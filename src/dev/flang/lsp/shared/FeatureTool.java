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
 * Source of class FeatureTool
 *
 *---------------------------------------------------------------------*/


package dev.flang.lsp.shared;

import java.net.URI;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Feature;
import dev.flang.ast.State;
import dev.flang.ast.Types;
import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.SourcePosition;

public class FeatureTool extends ANY
{
  /**
   * For feature a.b.c.d return the outer features a,b,c and universe
   * @param feature
   * @return
   */
  public static Stream<AbstractFeature> outerFeatures(AbstractFeature feature)
  {
    if (feature.outer() == null)
      {
        return Stream.empty();
      }
    return Stream.concat(Stream.of(feature.outer()), outerFeatures(feature.outer()));
  }

  /**
   * All inner features of given feature.
   * given feature `a` returns:
   * a.b
   * a.c
   * a.b.d
   * ...
   *
   * @param feature
   * @return
   */
  public static Stream<AbstractFeature> selfAndDescendants(AbstractFeature feature)
  {
    return Stream.concat(Stream.of(feature),
      ParserTool.declaredFeatures(feature).flatMap(f -> selfAndDescendants(f)));
  }

  /**
   * Extract the comment that belongs to some feature
   * from the corresponding source text file.
   * @param feature
   * @return
   */
  public static String commentOf(AbstractFeature feature)
  {
    var line = feature.pos().line() - 1;
    var commentLines = new ArrayList<String>();
    while (true)
      {
        var pos = SourcePositionTool.byLine(feature.pos()._sourceFile, line);
        if (line < 1 || !LexerTool.isCommentLine(pos))
          {
            break;
          }
        commentLines.add(SourceText.lineAt(pos));
        line = line - 1;
      }
    Collections.reverse(commentLines);

    var commentsOfRedefinedFeatures = feature
      .redefines()
      .stream()
      .map(f -> System.lineSeparator() + "redefines " + f.qualifiedName() + ":" + System.lineSeparator() + commentOf(f))
      .collect(Collectors.joining(System.lineSeparator()));

    return commentLines
      .stream()
      .map(l -> l.trim())
      .map(l -> l
        .replaceAll("^#", "")
        .trim())
      .collect(Collectors.joining(System.lineSeparator()))
      + commentsOfRedefinedFeatures;
  }

  /**
   * Text representation of an (incomplete) AST for uri.
   * @param uri
   * @return
   */
  public static String ast(URI uri)
  {
    var ast = ASTWalker.traverse(uri)
      .map(x -> x.getKey())
      .sorted(HasSourcePositionTool.compareByLineThenByColumn())
      .reduce("", (a, item) -> {
        var position = item.pos();
        // NYI
        var indent = 0;
        if (position.isBuiltIn())
          {
            return a;
          }
        return a + System.lineSeparator()
          + " ".repeat(indent * 2) + position.line() + ":" + position.column() + ":"
          + Util.shortName(item.getClass()) + ":" + HasSourcePositionTool.toLabel(item);
      }, String::concat);
    return ast;
  }

  /**
   * @param feature
   * @return the position of the name excluding prefix/infix/postfix etc.
   *
   * Examples:
   *
   * infix feature: ==
   * infix ==
   * ------^
   * formArgs feature x2:
   * feat(x1, x2 i32) is
   * ---------^
   * lambda arg i:
   * a := array<fraction<i32>> (n+1) (i -> 1 ⁄ 1)
   * ---------------------------------^
   */
  // NYI: UNDER DEVELOPMENT: we should probably extend the parser to have save these position during
  // parsing
  public static SourcePosition bareNamePosition(AbstractFeature feature)
  {
    require(!feature.featureName().baseName().equals(Errors.ERROR_STRING));

    if (feature.featureName().baseName().contains(" "))
      {
        var baseNameParts = feature.featureName().baseName().split(" ", 2);
        return LexerTool
          .tokensFrom(feature.pos())
          .dropWhile(tokenInfo -> !(baseNameParts[1].startsWith(tokenInfo.text())))
          .map(tokenInfo -> tokenInfo.start())
          .findFirst()
          .get();
      }
    var start = LexerTool
      .tokensFrom(feature.pos())
      .map(x -> x.text().equals("->") || x.text().equals(":="))
      .findFirst()
      .orElse(false) ?
    // NYI: UNDER DEVELOPMENT: HACK start lexing at start of line since
    // pos of lambda arg is the pos of the lambda arrow (->).
    // and destructed pos is pos of caching operator :=
                     SourcePositionTool.byLine(feature.pos()._sourceFile, feature.pos().line())
                     : feature.pos();

    return LexerTool
      .tokensFrom(start)
      .dropWhile(tokenInfo -> !tokenInfo.text().equals(feature.featureName().baseName()))
      .map(tokenInfo -> tokenInfo.start())
      .findFirst()
      .get();
  }

  /**
   * strips leading infix/prefix/postfix etc.
   * @param f
   * @return
   */
  public static String bareName(AbstractFeature f)
  {
    if (f.featureName().baseName().contains(" "))
      {
        return f.featureName().baseName().substring(f.featureName().baseName().indexOf(" ") + 1);
      }
    return f.featureName().baseName();
  }

  /**
   * Is the feature an argument of some feature?
   * @param feature
   * @return
   */
  public static boolean isArgument(AbstractFeature feature)
  {
    if (feature.outer() == null)
      {
        return false;
      }
    return feature.outer()
      .arguments()
      .stream()
      .anyMatch(f -> f.equals(feature));
  }

  /**
   * Is the feature an 'internal' feature?
   * @param af
   * @return
   */
  public static boolean isInternal(AbstractFeature af)
  {
    // NYI: UNDER DEVELOPMENT: this is a hack!
    return af.resultType().equals(Types.t_ADDRESS)
      || af.featureName().baseName().startsWith("@")
      || af.featureName().baseName().equals("result")
      || af.featureName()
        .baseName()
        .contains(FuzionConstants.INTERNAL_NAME_PREFIX) // Confusingly # is not
                                                        // just used as prefix
      || (af.featureName().baseName().equals("call") && isInternal(af.outer())) // lambda
      // NYI: UNDER DEVELOPMENT: hack
      || (af.featureName().baseName().matches("a\\d+")
        && af.outer().featureName().baseName().equals("call")) // autogenerated
                                                               // lambda args
      || af instanceof Feature f && f.state() == State.ERROR
      || af.featureName().baseName().equals(Errors.ERROR_STRING);
  }

  /**
   *
   * @param f
   * @return
   */
  static Optional<AbstractFeature> topLevelFeature(AbstractFeature f)
  {
    if (f.isUniverse() || f.outer() == Types.f_ERROR)
      {
        return Optional.empty();
      }
    if (f.outer().isUniverse() || !inSameSourceFile(f, f.outer()))
      {
        return Optional.of(f);
      }
    return topLevelFeature(f.outer());
  }

  private static boolean inSameSourceFile(AbstractFeature a, AbstractFeature b)
  {
    return a.pos()._sourceFile.toString().equals(b.pos()._sourceFile.toString());
  }

  /**
   * @param feature
   * @return example: array(T type, length i32, init Function array.T i32) => array array.T
   */
  public static String label(AbstractFeature feature, boolean useMarkup)
  {
    if (feature.isRoutine())
      {
        var arguments = (feature.arguments().isEmpty() ? "": "(")
          + feature.arguments()
            .stream()
            .map(a -> {
              var type = a.isTypeParameter() ? "type": TypeTool.label(a.resultType());
              type = useMarkup ? MarkdownTool.italic(type) : type;
              if (isInternal(a))
                {
                  return "_" + " " + type;
                }
              return a.featureName().baseName() + " " + type;
            })
            .collect(Collectors.joining(", "))
          + (feature.arguments().isEmpty() ? "" : ")");
        return feature.featureName().baseName() + arguments
          + (feature.isConstructor() ? "" : " " + (useMarkup ? MarkdownTool.italic(TypeTool.label(feature.resultType())) : TypeTool.label(feature.resultType())))
          + labelInherited(feature);
      }
    return feature.featureName().baseName() + " " + TypeTool.label(feature.resultType()) + labelInherited(feature);
  }


  /**
   * Text representation of the inherited features including
   * inheritance operator ':'
   *
   * @param feature
   * @return
   */
  public static String labelInherited(AbstractFeature feature)
  {
    if (feature.inherits().isEmpty())
      {
        return "";
      }
    return " : " + feature.inherits()
      .stream()
      .map(c -> c.calledFeature())
      .map(f -> f.featureName().baseName() + TypeTool.label(f.generics(), true))
      .collect(Collectors.joining(", "));
  }

  public static String commentOfInMarkdown(AbstractFeature f)
  {
    return MarkdownTool.italic(commentOf(f));
  }

  /**
   *
   * @param feature
   * @return true iff there are no other features at same or lesser level after given feature
   */
  static boolean isOfLastFeature(AbstractFeature feature)
  {
    return !isFunctionCall(feature) && selfAndDescendants(topLevelFeature(feature).get())
      .noneMatch(f -> f.pos().line() > feature.pos().line()
        && f.pos().column() <= feature.pos().column());
  }

  private static boolean isFunctionCall(AbstractFeature f)
  {
    return f.redefines().contains(Types.resolved.f_Function_call);
  }

  private static Set<AbstractFeature> callers(AbstractFeature f)
  {
    return callsTo(f).map(x -> x.getValue()).collect(Collectors.toSet());
  }

  private static Set<AbstractFeature> callees(AbstractFeature f)
  {
    return ASTWalker.traverseFeature(f, false)
      .map(e -> e.getKey())
      .filter(obj -> obj instanceof AbstractCall)
      .map(obj -> ((AbstractCall) obj).calledFeature())
      .collect(Collectors.toSet());
  }

  // NYI: UNDER DEVELOPMENT: use CFG
  public static String callGraph(AbstractFeature f)
  {
    var sb = new StringBuilder("digraph {" + System.lineSeparator());
    for(AbstractFeature caller : callers(f))
      {
        sb.append(
          "  " + quote(caller.qualifiedName()) + " -> " + quote(f.qualifiedName()) + ";" + System.lineSeparator());
      }
    for(AbstractFeature callee : callees(f))
      {
        sb.append(
          "  " + quote(f.qualifiedName()) + " -> " + quote(callee.qualifiedName()) + ";" + System.lineSeparator());
      }
    sb.append("}");
    return sb.toString();
  }

  private static String quote(String qualifiedName)
  {
    return "\"" + qualifiedName + "\"";
  }

  public static String uniqueIdentifier(AbstractFeature f)
  {
    return f.qualifiedName() + f.arguments().size();
  }

  /**
   * @param feature
   * @return all calls to this feature and the feature those calls are happening in
   */
  public static Stream<SimpleEntry<AbstractCall, AbstractFeature>> callsTo(AbstractFeature feature)
  {
    var universe = FeatureTool.universe(feature);
    return ASTWalker.calls(universe)
      .filter(entry -> entry.getKey().calledFeature() != null
        && entry.getKey().calledFeature().equals(feature));
  }

  static AbstractFeature universe(AbstractFeature feature)
  {
    var universe = feature;
    while (universe.outer() != null)
      {
        universe = universe.outer();
      }
    return universe;
  }

  public static boolean isUsedInChoice(AbstractFeature af)
  {
    var uri = ParserTool.getUri(af.pos());
    var result = ASTWalker
      .Features(uri)
      .anyMatch(f -> featureIsChoiceMember(f.selfType(), af)
        || f.hasResultField() && featureIsChoiceMember(f.resultType(), af));
    return result;
  }

  private static boolean featureIsChoiceMember(AbstractType at, AbstractFeature af)
  {
    return at.isChoice()
      && at.choiceGenerics()
        .stream()
        .map(t -> t.feature().selfType())
        .anyMatch(t -> t.equals(af.selfType()));
  }

  public static boolean doesInherit(AbstractFeature af)
  {
    return af.inherits()
      .stream()
      // filter implicit inheritance of `Object`
      .anyMatch(x -> x.calledFeature().inherits().size() != 0);
  }



}
