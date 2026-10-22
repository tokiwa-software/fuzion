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
 * Source of class TokenInfo
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.shared.records;

import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.lsp.enums.TokenType; // NYI: UNDER DEVELOPMENT: remove dependency
import dev.flang.lsp.shared.SourceText;
import dev.flang.lsp.shared.Util;
import dev.flang.parser.Lexer.Token;
import dev.flang.util.ANY;
import dev.flang.util.SourcePosition;

/**
 * holds text of lexer token and the start position of the token
 */
public class TokenInfo extends ANY
{

  private SourcePosition _start;

  /**
   * @return the _start
   */
  public SourcePosition start()
  {
    return _start;
  }


  private SourcePosition _end;

  /**
   * @return the _end
   */
  public SourcePosition end()
  {
    return _end;
  }


  private String _text;

  /**
   * @return the _text
   */
  public String text()
  {
    return _text;
  }


  private Token _token;

  /**
   * @return the _token
   */
  public Token token()
  {
    return _token;
  }

  public TokenInfo(SourcePosition start, SourcePosition end, String text, Token token)
  {
    this._start = start;
    this._end = end;
    this._text = text;
    this._token = token;
    if (CHECKS)
      check(end.compareTo(start) >= 0);
  }

  /*
   * starting line of token, zero based
   */
  private Integer line()
  {
    return _start.line() == 0 ? 0: _start.line() - 1;
  }

  /*
  * startChar of token, zero based
  */
  private Integer startChar()
  {
    if (_start.column() == 0)
      {
        return 0;
      }
    return SourceText
      .lineAt(_start)
      .codePoints()
      .limit(_start.column() - 1)
      .map(cp -> Character.charCount(cp))
      .sum();
  }

  /**
   * Takes into account that supplementary characters like
   * 😀 need twice the horizontal space and are thus counted as
   * 2.
   */
  public Integer charCount()
  {
    return Util.charCount(text());
  }


  /**
   * A simple entry whose equality is decided by comparing its key only.
   */
  private static class EntryEqualByKey<T1, T2> extends SimpleEntry<T1, T2>
  {
    public EntryEqualByKey(T1 key, T2 value)
    {
      super(key, value);
    }

    @Override
    public boolean equals(Object arg0)
    {
      boolean result;

      if (this == arg0)
        {
          result = true;
        }
      else if (!(arg0 instanceof EntryEqualByKey<?, ?> other))
        {
          result = false;
        }
      else
        {
          var key = this.getKey();
          var otherKey = other.getKey();
          result = (key == null ? otherKey == null : key.equals(otherKey));
        }

      return result;
    }

    @Override
    public int hashCode()
    {
      return this.getKey().hashCode();
    }
  }


  public Stream<Integer> semanticTokenData(TokenInfo previousToken)
  {
    var tokenType = tokenType();
    int relativeLine = line() - previousToken.line();
    int relativeChar = startChar() - (isSameLine(previousToken) ? previousToken.startChar(): 0);
    Integer tokenTypeNum = tokenType.get().num;

    if (ANY.CHECKS)
      ANY.check(relativeLine != 0 || relativeChar >= previousToken.charCount(),
        charCount() > 0 || (charCount() == 0 && relativeChar == 0));

    return Stream.of(
      relativeLine,
      relativeChar,
      charCount(),
      tokenTypeNum,
      0);
  }

  private boolean isSameLine(TokenInfo previousToken)
  {
    return line().equals(previousToken.line());
  }

  private Optional<TokenType> tokenType()
  {
    return switch (_token)
      {
      case t_comment -> Optional.of(TokenType.Comment);
      case t_numliteral -> Optional.of(TokenType.Number);
      case t_stringQQ, t_stringDQ -> Optional.of(TokenType.String);
      case t_stringQD, t_stringQP, t_stringDD, t_stringDP, t_stringPQ, t_stringPD, t_stringPP -> Optional.empty();
      case t_question -> Optional.of(TokenType.Keyword);
      case t_op ->
           (_text.equals("=>")
             || _text.equals("->")
             || _text.equals(":=")
             || _text.equals("|"))
             ? Optional.of(TokenType.Keyword)
             : Optional.of(TokenType.Operator);
      case t_ident -> Optional.empty(); // NYI: UNDER DEVELOPMENT: how to distinguish code or type?
      case t_leaf, t_infix, t_infix_right, t_prefix, t_postfix, t_private, t_module, t_public -> Optional.of(TokenType.Modifier);
      case t_abstract,  t_check, t_do, t_else, t_env, t_fixed, t_for, t_if, t_in, t_index, t_intrinsic, t_invariant, t_is, t_loop, t_match, t_native, t_period, t_post, t_pre, t_redef, t_ref, t_set, t_ternary, t_then, t_this, t_type, t_universe, t_until, t_variant, t_while -> Optional.of(TokenType.Keyword);
      case t_ambiguousSemi, t_barLimit, t_colonLimit, t_comma, t_commaLimit, t_eof, t_error, t_indentationLimit, t_lbrace, t_lbracket, t_lineLimit, t_lparen, t_rbrace, t_rbracket, t_rparen, t_semicolon, t_spaceOrSemiLimit, t_undefined, t_ws -> Optional.empty();
      };
  }

  // NYI: UNDER DEVELOPMENT: move this somewhere better
  public static Integer keyOf(SourcePosition pos)
  {
    // NYI: UNDER DEVELOPMENT: better key
    return pos.line() * 1000 + pos.column();
  }

  private static final Set<Token> leftBrackets =
    List.of(Token.t_lbrace, Token.t_lbracket, Token.t_lparen).stream().collect(Collectors.toUnmodifiableSet());
  private static final Set<Token> rightBrackets =
    List.of(Token.t_rbrace, Token.t_rbracket, Token.t_rparen).stream().collect(Collectors.toUnmodifiableSet());

  public boolean isLeftBracket()
  {
    return leftBrackets.contains(token());
  }

  public boolean isRightBracket()
  {
    return rightBrackets.contains(token());
  }

  @Override
  public String toString()
  {
    return "TokenInfo[start=" + start() + ", end=" + end() + ", text=" + text() + ", token=" + token() + "]";
  }

}
