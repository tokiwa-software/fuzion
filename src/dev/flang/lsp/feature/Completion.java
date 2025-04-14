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
 * Source of class Completion
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.feature;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.InsertTextMode;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.lsp.util.Bridge;
import dev.flang.parser.Lexer.Token;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.LexerTool;
import dev.flang.shared.ParserTool;
import dev.flang.shared.QueryAST;
import dev.flang.shared.TypeTool;
import dev.flang.shared.Util;

/**
 * tries offering code completions
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_completion
 */
public class Completion
{
  public enum TriggerCharacters
  {
    Dot("."), // calls
    Space(" "); // infix, postfix, types

    private final String triggerChar;

    private TriggerCharacters(String s)
    {
      triggerChar = s;
    }

    public String toString()
    {
      return this.triggerChar;
    }
  }

  private static CompletionItem buildCompletionItem(String label, String insertText,
    CompletionItemKind completionItemKind)
  {
    return buildCompletionItem(label, insertText, completionItemKind, null);
  }

  private static CompletionItem buildCompletionItem(String label, String insertText,
    CompletionItemKind completionItemKind, String sortText)
  {
    var item = new CompletionItem(label);
    item.setKind(completionItemKind);
    item.setInsertTextFormat(InsertTextFormat.Snippet);
    item.setInsertTextMode(InsertTextMode.AdjustIndentation);
    item.setInsertText(insertText);
    if (sortText != null)
      {
        item.setSortText(sortText);
      }
    return item;
  }

  public static Stream<CompletionItem> getCompletions(CompletionParams params)
  {
    var pos = Bridge.toSourcePosition(params);
    if (QueryAST.fnString(pos))
      {
        return Stream.empty();
      }

    var triggerCharacter = params.getContext().getTriggerCharacter();

    // dot-call
    if (".".equals(triggerCharacter))
      {
        var tokenBeforeDot = LexerTool
          .tokensAt(LexerTool.goLeft(pos))
          .left()
          .token();
        // do not offer completion for number
        if (tokenBeforeDot == Token.t_numliteral)
          {
            return Stream.empty();
          }
        // do not include `type` in completions
        if (tokenBeforeDot == Token.t_type)
          {
            return completions(QueryAST
              .dotCallCompletionsAt(pos));
          }
        return Stream.of(
            completions(QueryAST
              .dotCallCompletionsAt(pos)),
            completionItemThis(),
            completionItemType())
          .flatMap(x -> x);
      }
    if (" ".equals(triggerCharacter))
      {
        var tokenBeforeTriggerCharacter =
          LexerTool.tokensAt(LexerTool
            .goLeft(pos))
            .left()
            .token();
        if (tokenBeforeTriggerCharacter.equals(Token.t_for))
          {
            return forLoopCompletions();
          }


        var validTokens = new Token[]
          {
              Token.t_ident,
              Token.t_numliteral,
              Token.t_rbrace,
              Token.t_rbracket,
              Token.t_rparen,
              Token.t_stringQQ,
              Token.t_StringDQ,
              Token.t_stringBQ
          };
        var set = Util.arrayToSet(validTokens);
        if (set.contains(tokenBeforeTriggerCharacter))
          {
            // NYI better heuristic to check if we should offer infix/postfix
            // completion or types or keywords or nothing

            // no errors in line before pos?
            if (!ParserTool.errors(ParserTool.getUri(pos))
              .anyMatch(x -> x.pos.line() == pos.line() && x.pos.column() <= pos.column())
              && QueryAST.infixPostfixCompletionsAt(pos).count() > 0)
              {
                return completions(
                  QueryAST
                    .infixPostfixCompletionsAt(pos));
              }
            if (tokenBeforeTriggerCharacter.equals(Token.t_ident))
              {
                var types = QueryAST
                  .featuresInScope(pos)
                  .filter(af -> af.isConstructor() || af.isChoice())
                  .filter(af -> !af.featureName().baseName().contains(" "))
                  // NYI consider generics
                  .map(af -> TypeTool.baseName(af.selfType()))
                  .distinct()
                  .map(name -> buildCompletionItem(name, name, CompletionItemKind.TypeParameter));

                var keywords = Stream.of(buildCompletionItem("is", "is", CompletionItemKind.Keyword));

                return Stream.concat(keywords, types);
              }
          }
      }

    // NYI: UNDER DEVELOPMENT: // Invoked: ctrl+space
    // if
    // (params.getContext().getTriggerKind().equals(CompletionTriggerKind.Invoked)
    // && params.getContext().getTriggerCharacter() == null)
    // {
    // return completions(QueryAST.CompletionsAt(pos));
    // }
    return Stream.empty();
  }


  /**
   * completion item for keyword `type`
   * @return
   */
  private static Stream<CompletionItem> completionItemType()
  {
    return Stream.of(buildCompletionItem("type", "type", CompletionItemKind.Keyword));
  }

  /**
   * completion item for keyword `this`
   * @return
   */
  private static Stream<CompletionItem> completionItemThis()
  {
    return Stream.of(buildCompletionItem("this", "this", CompletionItemKind.Keyword));
  }


  private static Stream<CompletionItem> forLoopCompletions()
  {
    return Arrays.asList(
      buildCompletionItem("for in", "${1:i} in ${2:1}..${3:10} do", CompletionItemKind.Keyword),
      buildCompletionItem("for in while", "${1:i} in ${2:1}..${3:10} while ${4:} do",
        CompletionItemKind.Keyword),
      buildCompletionItem("for while", "i:=0, i+1 while ${4:} do", CompletionItemKind.Keyword),
      buildCompletionItem("for until else", "${1:i} in ${2:1}..${3:10} do"
        + System.lineSeparator() + "until ${4:}"
        + System.lineSeparator() + "else ${4:}",
        CompletionItemKind.Keyword),
      buildCompletionItem("for while until else","""
          for
            x1 := init1, next1
            x2 in set1
            x3 := init2, next2
            x4 in set2
            x5 := init3, next3
          while <whileCond>
            <body>
          until <untilCond>
            <success>
          else
            <failure>
          """,
        CompletionItemKind.Keyword)
      )
      .stream();
  }

  private static Stream<CompletionItem> completions(Stream<AbstractFeature> features)
  {
    var collectedFeatures = features
      .distinct()
      .collect(Collectors.toList());

    var completionItems = IntStream
      .range(0, collectedFeatures.size())
      .mapToObj(
        index -> {
          var feature = collectedFeatures.get(index);
          return buildCompletionItem(
            FeatureTool.label(feature, false),
            getInsertText(feature), CompletionItemKind.Function, String.format("%10d", index));
        });

    return completionItems;
  }

  /**
   * @param feature
   * @return example: psMap<${4:K -> ordered<psMap.K>}, ${5:V}> ${1:data} ${2:size} ${3:fill})
   */
  private static String getInsertText(AbstractFeature feature)
  {
    // NYI postfix return additional text edit
    var baseNameReduced = feature
      .featureName()
      .baseName()
      .replaceFirst("^.*\\s", "");
    if (!feature.isRoutine())
      {
        return baseNameReduced;
      }

    return baseNameReduced + getArguments(feature.valueArguments());
  }

  /**
   * @param arguments
   * @return ${1:data} ${2:size} ${3:fill}
   */
  private static String getArguments(List<AbstractFeature> arguments)
  {
    return IntStream
      .range(0, arguments.size())
      .<String>mapToObj(index -> {
        var argument = arguments.get(index);
        if (!argument.resultType().isFunctionType())
          {
            return " ${" + (index + 1) + ":" + argument.featureName().baseName() + "}";
          }
        return getFunArgument((index + 1) * 100, argument.resultType().generics());
      })
      .collect(Collectors.joining());
  }

  /**
   * @param offset
   * @param funArgs
   * @return example: (${101:H} -> ${102:B})
   */
  private static String getFunArgument(int offset, List<AbstractType> funArgs)
  {
    return " (" +
      IntStream.range(1, funArgs.size())
        .<String>mapToObj(
          x -> {
            return argPlaceholder(funArgs.get(x), x, offset);
          })
        .collect(Collectors.joining(", "))
      // result
      + " -> " + argPlaceholder(funArgs.get(0), funArgs.size(), offset) + ")";
  }

  /**
   * @param arg
   * @param x
   * @param offset
   * @return if arg not funType returns something like ${1:x}
   */
  private static String argPlaceholder(AbstractType arg, int x, int offset)
  {
    if (arg.isFunctionType())
      {
        return getFunArgument(offset * 100, arg.generics());
      }
    return "${" + (offset + x) + ":" + TypeTool.baseName(arg) + "}";
  }

}
