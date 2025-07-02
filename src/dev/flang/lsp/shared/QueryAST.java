/*

This file is part of the Fuzion language server protocol implementation.

The Fuzion language server protocol implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language server protocol implementation is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License along with The
Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.

*/

/*-----------------------------------------------------------------------
 *
 * Tokiwa Software GmbH, Germany
 *
 * Source of class QueryAST
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.shared;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Call;
import dev.flang.ast.Constant;
import dev.flang.ast.StrConst;
import dev.flang.ast.Types;
import dev.flang.parser.Lexer.Token;
import dev.flang.util.ANY;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.SourcePosition;

public class QueryAST extends ANY
{
  /**
   * try find the target feature for a dot-, infix- or postfix-call
   * at source position
   *
   * @param params
   * @return
   */
  public static Optional<AbstractFeature> targetFeature(SourcePosition params)
  {
    return findTargetFeatureInAST(params)
      .or(() -> constant(params));
  }

  // NYI: UNDER DEVELOPMENT: motivate/explain this heuristic
  private static Optional<AbstractFeature> findTargetFeatureInAST(SourcePosition params)
  {
    var leftToken = LexerTool.tokensAt(LexerTool.goLeft(params)).left();
    return ASTWalker
      .traverse(params)
      .filter(entry -> entry.getKey() instanceof AbstractCall)
      .filter(entry -> !entry.getValue().pos().isBuiltIn()
        && SourcePositionTool.positionIsAfterOrAtCursor(params, ParserTool.endOfFeature(entry.getValue())))
      .filter(entry -> SourcePositionTool.positionIsBeforeCursor(params, ((AbstractCall) entry.getKey()).pos()))
      .map(entry -> (AbstractCall) entry.getKey())
      .filter(ac -> SourcePositionTool.compare(ExprTool.endOfExpr(ac), params) <= 0)
      .sorted(ExprTool.compareByEndOfExpr.reversed())
      .filter(ac -> ac.calledFeature() != null)
      .filter(CallTool.calledFeatureNotInternal)
      // if left token is identifier, filter none matching calls by name
      .filter(ac -> !leftToken.token().equals(Token.t_ident) || !(ac instanceof Call)
        || leftToken.text().equals(((Call) ac).name()))
      .map(ac -> {
        // try use inferred type
        if (!TypeTool.containsError(ac.type()))
          {
            return ac.type();
          }
        // fall back to result type
        return ac
          .calledFeature()
          .resultType();
      })
      // NYI:
      // .map(at -> at.selfOrConstraint())
      .map(at -> at.feature())
      .filter(f -> !FeatureTool.isInternal(f) || f.featureName().baseName().endsWith("#type"))
      .findFirst();
  }

  // NYI: UNDER DEVELOPMENT: motivate/explain this heuristic
  private static Optional<? extends AbstractFeature> constant(SourcePosition params)
  {
    return ASTWalker.traverse(params)
      .filter(entry -> entry.getKey() instanceof Constant)
      .filter(entry -> !entry.getValue().pos().isBuiltIn()
        && SourcePositionTool.positionIsAfterOrAtCursor(params, ParserTool.endOfFeature(entry.getValue())))
      .filter(entry -> SourcePositionTool.positionIsBeforeCursor(params, ((Constant) entry.getKey()).pos()))
      .map(entry -> ((Constant) entry.getKey()))
      .filter(HasSourcePositionTool.isItemOnSameLineAsCursor(params))
      .sorted(HasSourcePositionTool.compareBySourcePosition.reversed())
      .map(x -> x.type().feature())
      .findFirst();
  }


  /**
   * get stream of possible features for dot-call at source position
   */
  public static Stream<AbstractFeature> dotCallCompletionsAt(SourcePosition params)
  {
    var tokenBeforeDot = LexerTool
      .tokensAt(LexerTool.goLeft(params))
      .left()
      .token();
    return targetFeature(params)
      // NYI: UNDER DEVELOPMENT: this should be simplified
      .map(tf -> tokenBeforeDot == Token.t_type && !tf.isCotype() ? tf.cotype() : tf)
      .map(tf -> candidates(tf)
        // filter infix, prefix, postfix features
        .filter(x -> !x.featureName().baseName().contains(" ")))
      .orElse(Stream.empty());
  }


  /**
   * find possible candidates for call on target feature
   * @param targetFeature
   * @return
   */
  private static Stream<AbstractFeature> candidates(AbstractFeature targetFeature)
  {
    var declaredFeaturesOfInheritedFeatures =
      inheritedRecursive(targetFeature).flatMap(af -> ParserTool.declaredFeatures(af));

    var declaredFeatures = Stream.concat(ParserTool
      .declaredFeatures(targetFeature), declaredFeaturesOfInheritedFeatures)
      .collect(Collectors.toList());

    var redefinedFeatures =
      declaredFeatures.stream().flatMap(x -> x.redefines().stream()).collect(Collectors.toSet());

    return declaredFeatures
      .stream()
      // subtract redefined features from result
      .filter(x -> !redefinedFeatures.contains(x))
      .filter(x -> !x.isTypeParameter());
  }


  /**
   * possibly called features for infix/postfix call
   * @param params
   * @return
   */
  public static Stream<AbstractFeature> infixPostfixCompletionsAt(SourcePosition params)
  {
    return targetFeature(params)
      .map(feature -> {
        var declaredFeaturesOfInheritedFeatures =
          inheritedRecursive(feature).flatMap(af -> ParserTool.declaredFeatures(af));

        var declaredFeatures = Stream.concat(ParserTool
          .declaredFeatures(feature), declaredFeaturesOfInheritedFeatures)
          .collect(Collectors.toList());

        var redefinedFeatures =
          declaredFeatures.stream().flatMap(x -> x.redefines().stream()).collect(Collectors.toSet());

        // subtract redefined features from result
        return declaredFeatures
          .stream()
          .filter(x -> x.featureName().baseName().startsWith("infix")
            || x.featureName().baseName().startsWith("postfix"))
          .filter(x -> !redefinedFeatures.contains(x));
      })
      .orElse(Stream.empty());
  }


  /**
   * returns all directly and indirectly inherited features of af
   * @param af
   * @return
   */
  private static Stream<AbstractFeature> inheritedRecursive(AbstractFeature af)
  {
    return Stream.concat(af.inherits().stream().map(ac -> ac.calledFeature()),
      af.inherits().stream().flatMap(c -> inheritedRecursive(c.calledFeature())));
  }


  /**
   * @param params
   * @return
   * NYI: UNDER DEVELOPMENT: currently ununsed.
   * Can we use this without beeing annoying?
   */
  public static Stream<AbstractFeature> completionsAt(SourcePosition params)
  {
    var tokens = LexerTool.tokensAt(params);
    if (tokens.left().token().equals(Token.t_ws))
      {
        return QueryAST.featuresInScope(params);
      }
    else if (tokens.left().token().equals(Token.t_ident))
      {
        return QueryAST.featuresInScope(params)
          .filter(f -> {
            return f.featureName().baseName().startsWith(tokens.left().text());
          });
      }
    return Stream.empty();
  }


  /**
   * given a TextDocumentPosition return all matching ASTItems
   * in the given file on the given line.
   * sorted by position descending.
   * @param params
   * @return
   */
  private static Stream<HasSourcePosition> astItemsBeforeOrAtCursor(SourcePosition params)
  {
    return ASTWalker.traverse(params)
      .filter(HasSourcePositionTool.isItemInScope(params))
      .map(entry -> entry.getKey())
      .filter(HasSourcePositionTool.isItemNotBuiltIn(params))
      .filter(HasSourcePositionTool.isItemOnSameLineAsCursor(params))
      .sorted(HasSourcePositionTool.compareBySourcePosition.reversed());
  }

  /**
   * returns all features declared in uri
   * @param uri
   * @return
   */
  public static Stream<AbstractFeature> selfAndDescendants(URI uri)
  {
    return ParserTool
      .topLevelFeatures(uri)
      .flatMap(f -> FeatureTool.selfAndDescendants(f));
  }

  public static Optional<AbstractCall> callAt(SourcePosition params)
  {
    Optional<AbstractCall> call = astItemsBeforeOrAtCursor(params)
      .filter(item -> item instanceof AbstractCall)
      .map(c -> (AbstractCall) c)
      .findFirst();
    return call;
  }


  /**
   * tries to find the closest feature at given
   * position that is declared, called or used by a type
   * @param params
   */
  public static Optional<AbstractFeature> featureAt(SourcePosition params)
  {
    return featureDefinedAt(params)
      .or(() -> findFeatureInAST(params))
      // NYI: UNDER DEVELOPMENT: workaround for not having positions of all types in
      // the AST currently
      .or(() -> featureAtFuzzy(params))
      .or(() -> {
        dev.flang.lsp.shared.Context.logger.warning("No feature found at: " + params);
        return Optional.empty();
      });
  }

  private static Optional<? extends AbstractFeature> findFeatureInAST(SourcePosition params)
  {
    var token = LexerTool.identOrOperatorTokenAt(params);
    return astItemsBeforeOrAtCursor(params)
      .map(astItem -> {
        if (astItem instanceof AbstractFeature f
          && token.<Boolean>map(x -> x.text().equals(f.featureName().baseName())).orElse(false))
          {
            return f;
          }
        if (astItem instanceof AbstractCall c)
          {
            return ErrorHandling.resultOrDefault(() -> {
              if (token.map(t -> c.pos().column() + Util.codepointCount(t.text()) >= params.column())
                .orElse(false)
                && !c.calledFeature().equals(Types.f_ERROR))
                {
                  return c.calledFeature();
                }
              return null;
            }, null);
          }
        return null;
      })
      .filter(f -> f != null)
      .findFirst();
  }

  /**
   * if we are somewhere here:
   *
   * infix * ...
   * ^^^^^^^
   *
   * or somewhere here:
   *
   * some_feat ... is
   * ^^^^^^^^^
   *
   * return the matching feature
   */
  private static Optional<AbstractFeature> featureDefinedAt(SourcePosition params)
  {
    return ASTWalker.Features(SourceText.uriOf(params))
      .filter(af -> !FeatureTool.isArgument(af))
      // line
      .filter(x -> params.line() == x.pos().line())
      .filter(x -> {
        var start = x.pos().column();
        // NYI: UNDER DEVELOPMENT: should work most of the time but there might be additional
        // whitespace?
        var end = start + Util.codepointCount(x.featureName().baseName());
        return start <= params.column() && params.column() <= end;
      })
      .findAny();
  }

  /**
   * Try to find feature by matching the ident token text at given position.
   * @param params
   * @return
   */
  private static Optional<AbstractFeature> featureAtFuzzy(SourcePosition params)
  {
    return LexerTool.identOrOperatorTokenAt(params)
      .flatMap(token -> QueryAST.featuresInScope(params)
        .filter(f -> f.featureName().baseName().equals(token.text()))
        // NYI: UNDER DEVELOPMENT: we could be better here if we considered approximate
        // argcount
        .findFirst());
  }

  /**
   * @param params
   * @return the most inner feature at the cursor position
   */
  public static Optional<AbstractFeature> inFeature(SourcePosition params)
  {
    return selfAndDescendants(SourceText.uriOf(params))
      .filter(f -> {
        var startOfFeature = f.pos();
        var endOfFeature = ParserTool.endOfFeature(f);
        return SourcePositionTool.compare(params, endOfFeature) <= 0 &&
          SourcePositionTool.compare(params, startOfFeature) > 0;
      })
      .filter(f -> f.pos().column() < params.column())
      .sorted(HasSourcePositionTool.compareBySourcePosition.reversed())
      .findFirst();
  }

  /**
   * @param params
   * @return if text document position is inside of string
   */
  public static boolean fnString(SourcePosition params)
  {
    return ASTWalker.traverse(params)
      .filter(x -> x.getKey() instanceof StrConst)
      .map(x -> (StrConst) x.getKey())
      .anyMatch(x -> {
        if (x.pos().line() != params.line())
          {
            return false;
          }
        var start = x.pos().column();
        var d = x.data();
        var end = x.pos().column() + Util.charCount(new String(Arrays.copyOfRange(d, 4, ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getInt() + 4), StandardCharsets.UTF_8));
        if (SourceText.lineAt(x.pos()).charAt(x.pos().column() - 1) == '\"')
          {
            return start < params.column()
              && params.column() - 1 <= end;
          }
        return start < params.column()
          && params.column() <= end;
      });
  }

  /**
   * @param feature
   * @return all features which are accessible (callable) at pos
   */
  public static Stream<AbstractFeature> featuresInScope(SourcePosition pos)
  {
    return inFeature(pos)
      .map(feature -> {
        return Util.concatStreams(
          Stream.of(feature),
          FeatureTool.outerFeatures(feature),
          feature.inherits().stream().map(c -> c.calledFeature()))
          .filter(f -> !TypeTool.containsError(f.selfType()))
          .flatMap(f -> {
            return ParserTool.declaredFeatures(f);
          })
          .distinct();
      })
      .orElse(Stream.empty());
  }

}
