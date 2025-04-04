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
 * Source of class InlayHints
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.feature;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.Constant;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Expr;
import dev.flang.lsp.util.Bridge;
import dev.flang.lsp.util.LSP4jUtils;
import dev.flang.parser.Lexer.Token;
import dev.flang.shared.ASTWalker;
import dev.flang.shared.CallTool;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.LexerTool;
import dev.flang.shared.TypeTool;
import dev.flang.shared.Util;
import dev.flang.util.ANY;
import dev.flang.util.SourcePosition;

/**
 * Provide inlay hints for actuals.
 * See: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlayHint
 */
public class InlayHints extends ANY
{
  private static final int MIN_PARAM_NAME_LENGTH = 2;
  private static boolean isEnabled = true;

  public static void Disable()
  {
    isEnabled = false;
  }

  public static void Enable()
  {
    isEnabled = true;
  }

  public static List<InlayHint> getInlayHints(InlayHintParams params)
  {
    if (!isEnabled)
      {
        return List.of();
      }
    var uri = LSP4jUtils.getUri(params.getTextDocument());

    var inlayHintsActuals = ASTWalker.Traverse(uri)
      .filter(e -> e.getKey() instanceof AbstractCall)
      .map(e -> (AbstractCall) e.getKey())
      .filter(c -> IsInRange(params.getRange(), c.pos()))
      .filter(c -> !CallTool.IsFixLikeCall(c))
      .filter(CallTool.CalledFeatureNotInternal)
      .flatMap(c -> {
        if (c.actuals().size() == c.calledFeature().valueArguments().size())
          {
            return IntStream.range(0, c.actuals().size())
              .filter(idx -> Util.CharCount(
                c.calledFeature().valueArguments().get(idx).featureName().baseName()) >= MIN_PARAM_NAME_LENGTH)
              // this is the case e.g. for _ args
              .filter(idx -> !FeatureTool.IsInternal(c.calledFeature().valueArguments().get(idx)))
              // omit inlay hint if actual is call of same name as arg
              .filter(idx -> !(c.actuals().get(idx) instanceof AbstractCall ac && ac.calledFeature()
                .featureName()
                .baseName()
                .equals(c.calledFeature().valueArguments().get(idx).featureName().baseName())))
              // for array initialization via [] syntax, don't show inlay hint
              .filter(idx -> !c.calledFeature().valueArguments().get(idx).qualifiedName().equals("array.internal_array"))
              .mapToObj(idx -> {
                var inlayHint = new InlayHint(Bridge.ToPosition(CallTool.StartOfExpr(c.actuals().get(idx))),
                  Either.forLeft(c.calledFeature().valueArguments().get(idx).featureName().baseName() + ":"));
                inlayHint.setKind(InlayHintKind.Parameter);
                inlayHint.setPaddingLeft(true);
                inlayHint.setPaddingRight(true);
                return inlayHint;
              });
          }
        // NYI when is actuals count != calledFeature valueArgs count?
        else
          {
            return Stream.empty();
          }
      });

    var inlayHintsResultTypes = ASTWalker
      .Features(LSP4jUtils.getUri(params.getTextDocument()))
      // NYI filter duplicate loop variable
      .filter(af -> !FeatureTool.IsInternal(af))
      .filter(af -> !FeatureTool.IsArgument(af))
      // NYI filter constants like numbers, strings etc.
      .filter(af -> !(af.isField() && TypeIsExplicitlyStated(af)))
      .flatMap(af -> PositionOfOperator(af)
        .map(pos -> {
          var ih = new InlayHint(pos, Either.forLeft(TypeTool.Label(af.resultType())));
          ih.setKind(InlayHintKind.Type);
          ih.setPaddingLeft(true);
          ih.setPaddingRight(true);
          return Stream.of(ih);
        })
        .orElse(Stream.empty()));

    // // NYI this is too slow since FUIR is needed for
    // // evaluating needed effects
    // var inlayHintsEffects = ParserTool
    //   .TopLevelFeatures(uri)
    //   .map(af -> {
    //     var effects = ParserTool.Effects(af);
    //     if (effects.isBlank())
    //       {
    //         return Optional.<InlayHint>empty();
    //       }
    //     var endOfLine = SourceText.LineAt(af.pos()).length();
    //     var ih = new InlayHint(new Position(af.pos().line() - 1, endOfLine), Either.forLeft("effects: " + effects));
    //     ih.setKind(InlayHintKind.Parameter);
    //     ih.setPaddingLeft(true);
    //     ih.setPaddingRight(true);
    //     return Optional.of(ih);
    //   })
    //   .flatMap(Optional::stream);

    return Util.ConcatStreams(inlayHintsActuals, inlayHintsResultTypes).collect(Collectors.toList());
  }

  private static boolean IsConstant(Expr code)
  {
    return code instanceof Constant;
  }

  static HashSet<Token> AllowedTokensBeforeOp = new HashSet<>(List.of(
    Token.t_ws,
    Token.t_op,
    Token.t_comma,
    Token.t_lparen,
    Token.t_rparen,
    Token.t_lbrace,
    Token.t_rbrace,
    Token.t_lbracket,
    Token.t_rbracket,
    Token.t_ident,
    Token.t_in,
    Token.t_ref,
    Token.t_redef,
    Token.t_const,
    Token.t_leaf,
    Token.t_infix,
    Token.t_prefix,
    Token.t_postfix,
    Token.t_ternary,
    Token.t_index,
    Token.t_set,
    Token.t_private,
    Token.t_module,
    Token.t_public,
    Token.t_type));


  private static boolean TypeIsExplicitlyStated(AbstractFeature af)
  {
    return LexerTool
      .TokensFrom(af.pos())
      .takeWhile(x -> AllowedTokensBeforeOp.contains(x.token()) && !x.text().equals(":="))
      .filter(x -> x.token().equals(Token.t_ident))
      .count() > 1;
  }

  /*
   * Position of `=>` or `:=` belonging to this feature
   */
  private static Optional<Position> PositionOfOperator(AbstractFeature af)
  {
    return LexerTool
      .TokensFrom(af.pos())
      .takeWhile(x -> AllowedTokensBeforeOp.contains(x.token()))
      .dropWhile(x -> !(x.text().equals("=>") || x.text().equals(":=")))
      .map(x -> Bridge.ToPosition(x.start()))
      .findFirst();
  }

  private static boolean IsInRange(Range range, SourcePosition pos)
  {
    var p = Bridge.ToPosition(pos);
    return range.getStart().getLine() <= p.getLine() && range.getEnd().getLine() >= p.getLine();
  }
}
