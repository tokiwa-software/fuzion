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
 * Source of class SemanticToken
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.feature;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.lsp.enums.TokenModifier;
import dev.flang.lsp.enums.TokenType;
import dev.flang.lsp.shared.LexerTool;
import dev.flang.lsp.shared.SourcePositionTool;
import dev.flang.lsp.shared.Util;
import dev.flang.lsp.shared.records.TokenInfo;
import dev.flang.lsp.util.Bridge;
import dev.flang.parser.Lexer.Token;
import dev.flang.util.ANY;
import dev.flang.util.SourcePosition;

public class SemanticToken extends ANY
{

  public static final SemanticTokensLegend Legend =
    new SemanticTokensLegend(TokenType.asList, TokenModifier.asList);

  public static SemanticTokens getSemanticTokens(SemanticTokensParams params)
  {
    return new SemanticTokens(semanticTokenData(lexerTokens(params)));
  }

  private static List<TokenInfo> lexerTokens(SemanticTokensParams params)
  {
    return LexerTool
      .tokensFrom(
        Bridge.toSourcePosition(
          new TextDocumentPositionParams(params.getTextDocument(), new Position(0, 0))))
      // - map all special strings to normal strings plus operator(s)
      .flatMap(t -> {
        switch (t.token())
          {
          case t_stringBQ :    // '}+-*"' in "abc{x}+-*"
            return Stream.of(
              new TokenInfo(t.start(),
                LexerTool.goRight(t.start()),
                t.text().substring(0, 1), Token.t_op),
              new TokenInfo(LexerTool.goRight(t.start()),
                t.end(),
                t.text().substring(1), Token.t_stringQQ));
          case t_stringQD :    // '"x is $' in "x is $x.".
          case t_StringDD :    // '+-*$' in "abc$x+-*$x.".
          case t_stringQB :    // '"a+b is {' in "a+b is {a+b}."
          case t_StringDB :    // '+-*{' in "abc$x+-*{a+b}."
            return Stream.of(
              new TokenInfo(t.start(),
                SourcePositionTool.byLineColumn(t.end()._sourceFile, t.end().line(),
                  t.end().column() - 1),
                t.text().substring(0, Util.charCount(t.text()) - 1), Token.t_stringQQ),
              new TokenInfo(
                SourcePositionTool.byLineColumn(t.end()._sourceFile, t.end().line(),
                  t.end().column() - 1),
                t.end(),
                t.text().substring(Util.charCount(t.text()) - 1, Util.charCount(t.text())), Token.t_op));
          case t_stringBD :    // '}+-*$' in "abc{x}+-*$x.".
          case t_stringBB :    // '}+-*{' in "abc{x}+-*{a+b}."
            return Stream.of(
              new TokenInfo(t.start(), LexerTool.goRight(t.start()), "}",
                Token.t_op),
              new TokenInfo(LexerTool.goRight(t.start()),
                SourcePositionTool.byLineColumn(t.start()._sourceFile, t.start().line(),
                  t.start().column() + Util.codepointCount(t.text()) - 1),
                t.text().substring(1, Util.charCount(t.text()) - 1), Token.t_stringQQ),
              new TokenInfo(
                SourcePositionTool.byLineColumn(t.start()._sourceFile, t.start().line(),
                  t.start().column() + Util.codepointCount(t.text()) - 1),
                t.end(),
                t.text().substring(Util.charCount(t.text()) - 1, Util.charCount(t.text())), Token.t_op));
          // discard these tokens
          case t_error :
          case t_ws :
          case t_comma :
          case t_lparen :
          case t_rparen :
          case t_lbrace :
          case t_rbrace :
          case t_lbracket :
          case t_rbracket :
          case t_semicolon :
          case t_eof :
          case t_barLimit :
          case t_colonLimit :
          case t_indentationLimit :
          case t_lineLimit :
          case t_undefined :
            return Stream.empty();
          default:
            return Stream.of(t);
          }
      })
      .flatMap(t -> {
        return t.start().line() == t.end().line()
          ? Stream.of(t)
          : splitToken(t);
      })
      .collect(Collectors.toList());
  }

  /**
   * tokens like comments, multiline strings can extend over more than one line.
   * split them up
   */
  private static Stream<? extends TokenInfo> splitToken(TokenInfo t)
  {
    var lines = t
      .text()
      .split("\\r?\\n");

    return IntStream.range(0, (int) lines.length)
      .filter(idx -> !lines[idx].isBlank())
      .mapToObj(
        idx -> {
          var col = idx == 0 ? t.start().column() : 1;
          return new TokenInfo(
            SourcePositionTool.byLineColumn(t.start()._sourceFile, t.start().line() + idx, col),
            SourcePositionTool.byLineColumn(t.start()._sourceFile, t.start().line() + idx, col - 1 + Util.charCount(lines[idx])),
            lines[idx],
            t.token());
      });
  }

  private static List<Integer> semanticTokenData(List<TokenInfo> lexerTokens)
  {
    return IntStream
      .range(0, lexerTokens.size())
      .mapToObj(x -> {
        var beginningOfFileToken =
          new TokenInfo(
            new SourcePosition(lexerTokens.get(x).start()._sourceFile, 0),
            new SourcePosition(lexerTokens.get(x).start()._sourceFile, 0),
            "",
            Token.t_undefined);
        var previousToken = x == 0 ? beginningOfFileToken: lexerTokens.get(x - 1);
        return lexerTokens.get(x).semanticTokenData(previousToken);
      })
      .flatMap(x -> x)
      .collect(Collectors.toList());
  }

}
