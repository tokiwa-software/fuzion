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
 * Source of class LexerTool
 *
 *---------------------------------------------------------------------*/

package dev.flang.shared;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.parser.Lexer;
import dev.flang.parser.Lexer.Token;
import dev.flang.shared.records.TokenInfo;
import dev.flang.shared.records.Tokens;
import dev.flang.util.ANY;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;

public class LexerTool extends ANY
{

  /**
   * Is str a valid identifier?
   * @param str
   * @return
   */
  public static boolean IsValidIdentifier(String str)
  {
    var isIdentifier = IO.WithTextInputStream(str, () -> {
      var lexer = NewLexerStdIn();
      var startsWithIdent = lexer.current() == Token.t_ident;
      lexer.nextRaw();
      return startsWithIdent && lexer.current().equals(Token.t_eof);
    });
    return isIdentifier;
  }

  private static Map<String, List<TokenInfo>> tokenCache =
    Util.ThreadSafeLRUMap(1, (removed) -> {
    });

  private static Stream<TokenInfo> Tokenize(SourcePosition pos)
  {
    return IO.WithTextInputStream(SourceText.getText(pos), () -> {
      var lexer = NewLexerStdIn();
      return Stream.generate(() -> {
        // lexer has path stdin, so we pass sourcefile with original path
        // and the bytes of the current lexer.
        var result = tokenInfo(lexer, new SourceFile(pos._sourceFile._fileName, lexer.bytes()));
        advance(lexer);
        return result;
      }).takeWhile(tokenInfo -> tokenInfo.token() != Token.t_eof);
    });
  }

  /**
   * @param start
   * @return stream of tokens starting at start.
   * if start is in the middle of a token return this token as well.
   */
  public static Stream<TokenInfo> TokensFrom(SourcePosition start)
  {
    if (PRECONDITIONS)
      require(start.bytePos() <= SourceText.getText(start).getBytes().length);

    return tokenCache.computeIfAbsent(SourceText.getText(start),
      (k) -> Tokenize(start).collect(Collectors.toUnmodifiableList()))
      .stream()
      .dropWhile(x -> start.bytePos() >= x.end().bytePos());
  }

  private static void advance(Lexer lexer)
  {
    lexer.nextRaw();
    while (lexer.current() == Token.t_error ||
      lexer.current() == Token.t_undefined)
      {
        lexer.nextRaw();
      }
  }

  /**
   * Next token from start that matches one of tokens
   * or EOF.
   *
   * @param start
   * @param tokens
   * @return
   */
  public static Optional<TokenInfo> NextTokenOfType(SourcePosition start, Set<Token> tokens)
  {
    return TokensFrom(start)
      .filter(x -> tokens.contains(x.token()))
      .findFirst();
  }

  /**
   * @param params the position of the cursor
   * @return token left and right of cursor
   */
  public static Tokens TokensAt(SourcePosition params)
  {

    var tokens = TokensFrom(GoLeft(params))
      .limit(2)
      .collect(Collectors.toList());

    var eofPos = new SourcePosition(params._sourceFile,
      params._sourceFile.byteLength());

    var eof = new TokenInfo(eofPos, eofPos, "", Token.t_eof);

    var token1 = tokens.size() > 0 ? tokens.get(0): eof;
    var token2 = tokens.size() > 1 ? tokens.get(1): eof;

    // between two tokens
    if (token1.end().line() == params.line()
      && token1.end().column() == params.column())
      {
        return new Tokens(token1, token2);
      }
    return new Tokens(token1, token1);
  }

  /*
   * creates and initializes a lexer that reads from stdin
   */
  private static Lexer NewLexerStdIn()
  {
    var lexer = new Lexer(SourceFile.STDIN, null);
    // HACK the following is necessary because currently on instantiation
    // lexer calls next(), skipping any raw tokens at start
    lexer.setPos(0);
    advance(lexer);
    return lexer;
  }

  /**
   * creates a token info from the current lexer
   *
   * @param lexer
   * @param sf
   * @return
   *
   */
  private static TokenInfo tokenInfo(Lexer lexer, SourceFile sf)
  {
    var startPos = lexer.sourcePos(lexer.tokenPos());
    var start = new SourcePosition(sf, startPos.bytePos());
    var endPos = lexer.sourcePos(lexer.bytePos());
    var end = new SourcePosition(sf, endPos.bytePos());
    var tokenText = lexer.asString(lexer.tokenPos(), lexer.bytePos());
    var token = lexer.current();
    return new TokenInfo(start, end, tokenText, token);
  }

  /**
   * Is this line a comment?
   * @param params
   * @return
   */
  public static boolean isCommentLine(SourcePosition params)
  {
    return TokensFrom(params)
      .filter(x -> x.start().line() == params.line())
      .dropWhile(x -> x.token() == Token.t_ws)
      .findFirst()
      .map(x -> x.token() == Token.t_comment)
      .orElse(false);
  }

  /**
   * End of the token to the right of the given pos
   */
  public static SourcePosition EndOfToken(SourcePosition pos)
  {

    return pos.isBuiltIn()
                           ? pos
                           : TokensAt(pos)
                             .right()
                             .end();
  }

  /**
   * Move cursor one left. If at start of line same position.
   * @param p
   * @return
   */
  public static SourcePosition GoLeft(SourcePosition p)
  {
    if (p.column() == 1)
      {
        return p;
      }
    return SourcePositionTool.ByLineColumn(p._sourceFile, p.line(), p.column() - 1);
  }

  /**
   * Move cursor one right. If at end of line same position.
   * @param p
   * @return
   */
  public static SourcePosition GoRight(SourcePosition p)
  {
    return SourcePositionTool.ByLineColumn(p._sourceFile, p.line(), p.column() + 1);
  }

  /**
   * looks for an identifier token at position
   * if none found look for identifier token at position - 1
   * if none found look for operator   token at position
   * if none found look for operator   token at position - 1
   * @param pos
   * @return
   */
  public static Optional<TokenInfo> IdentOrOperatorTokenAt(SourcePosition pos)
  {
    return IdentTokenAt(pos)
      .or(() -> {
        var tokens = TokensAt(pos);
        if (tokens.right().token() == Token.t_op)
          {
            return Optional.of(tokens.right());
          }
        if (tokens.left().token() == Token.t_op)
          {
            return Optional.of(tokens.left());
          }
        return Optional.empty();
      });
  }

  /**
   * Ident token right or left of pos or empty.
   * @param pos
   * @return
   */
  public static Optional<TokenInfo> IdentTokenAt(SourcePosition pos)
  {
    var tokens = TokensAt(pos);
    if (tokens.right().token() == Token.t_ident)
      {
        return Optional.of(tokens.right());
      }
    if (tokens.left().token() == Token.t_ident)
      {
        return Optional.of(tokens.left());
      }
    return Optional.empty();
  }

}
