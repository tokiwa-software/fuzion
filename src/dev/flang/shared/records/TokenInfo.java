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

package dev.flang.shared.records;

import java.util.AbstractMap.SimpleEntry;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.State;
import dev.flang.ast.Feature;
import dev.flang.lsp.enums.TokenModifier; // NYI remove dependency
import dev.flang.lsp.enums.TokenType; // NYI remove dependency
import dev.flang.parser.Lexer.Token;
import dev.flang.shared.ASTWalker;
import dev.flang.shared.CallTool;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.LexerTool;
import dev.flang.shared.ParserTool;
import dev.flang.shared.SourceText;
import dev.flang.shared.Util;
import dev.flang.util.ANY;
import dev.flang.util.HasSourcePosition;
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
      .LineAt(_start)
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
    return Util.CharCount(text());
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
      var other = (EntryEqualByKey<T1, T2>) arg0;
      return (this.getKey() == null ? other.getKey() == null: this.getKey().equals(other.getKey()));
    }

    @Override
    public int hashCode()
    {
      return this.getKey().hashCode();
    }
  }

  /*
   * returns a map of: position -> call/feature
   * remark: position is encoded by integer index via function TokenInfo.KeyOf()
   * this is used to map an ident token to the appropriate feature/call
   */
  private static Map<Integer, HasSourcePosition> Pos2Items(SourcePosition pos)
  {
    return ASTWalker
      .Traverse(pos)
      .map(e -> e.getKey())
      .filter(x -> x instanceof AbstractFeature || x instanceof AbstractCall)
      // try to filter all generated features/calls
      .filter(x -> {
        if (x instanceof AbstractFeature af)
          {
            return LexerTool
              .TokensAt(FeatureTool.BareNamePosition(af))
              .right()
              .text()
              .equals(FeatureTool.BareName(af));
          }
        var c = (AbstractCall) x;
        return LexerTool
          .TokensAt(c.pos())
          .right()
          .text()
          .equals(FeatureTool.BareName(c.calledFeature()));
      })
      .map(item -> new EntryEqualByKey<Integer, HasSourcePosition>(
        TokenInfo.KeyOf(item instanceof AbstractFeature af ? FeatureTool.BareNamePosition(af): item.pos()), item))
      // NYI which are the duplicates here? Can we do better in selecting the
      // 'right' ones?
      .distinct()
      .collect(Collectors.toUnmodifiableMap(e -> e.getKey(), e -> e.getValue()));
  }


  private static final Map<String, Map<Integer, HasSourcePosition>> Pos2ItemsCache = Util.ThreadSafeLRUMap(1, null);


  private Map<Integer, HasSourcePosition> Pos2Items()
  {
    return Pos2ItemsCache.computeIfAbsent(SourceText.getText(_start), (key) -> Pos2Items(_start));
  }

  public Stream<Integer> SemanticTokenData(TokenInfo previousToken)
  {
    var tokenType = TokenType();
    int relativeLine = line() - previousToken.line();
    int relativeChar = startChar() - (IsSameLine(previousToken) ? previousToken.startChar(): 0);
    Integer tokenTypeNum = tokenType.get().num;

    if (ANY.CHECKS)
      ANY.check(relativeLine != 0 || relativeChar >= previousToken.charCount(),
        charCount() > 0 || (charCount() == 0 && relativeChar == 0));

    return Stream.of(
      relativeLine,
      relativeChar,
      charCount(),
      tokenTypeNum,
      Modifiers());
  }

  private boolean IsSameLine(TokenInfo previousToken)
  {
    return line().equals(previousToken.line());
  }

  // NYI
  private Integer Modifiers()
  {
    switch (_token)
      {
      case t_ident :
        var modifiers = new HashSet<TokenModifier>();
        GetItem()
          .ifPresent(item -> {
            if (item instanceof AbstractFeature af)
              {
                if (af.isAbstract())
                  {
                    modifiers.add(TokenModifier.Abstract);
                  }
                if (af.isField())
                  {
                    modifiers.add(TokenModifier.Readonly);
                  }
              }
          });
        return TokenModifier.dataOf(modifiers);
      default:
        return 0;
      }
  }

  private Optional<TokenType> TokenType()
  {
    if (_token.isKeyword())
      {
        switch (_token)
          {
          case t_const :
          case t_leaf :
          case t_infix :
          case t_prefix :
          case t_postfix :
          case t_private :
          case t_module :
          case t_public :
            return Optional.of(TokenType.Modifier);
          default:
            return Optional.of(TokenType.Keyword);
          }
      }
    switch (_token)
      {
      case t_comment :
        return Optional.of(TokenType.Comment);
      case t_numliteral :
        return Optional.of(TokenType.Number);
      case t_stringQQ :
      case t_StringDQ :
        return Optional.of(TokenType.String);
      case t_stringQD :
      case t_stringQB :
      case t_StringDD :
      case t_StringDB :
      case t_stringBQ :
      case t_stringBD :
      case t_stringBB :
        if (ANY.PRECONDITIONS)
          ANY.check(false);
        return Optional.empty();
      case t_question :
        return Optional.of(TokenType.Keyword);
      case t_op :
        if (_text.equals("=>")
          || _text.equals("->")
          || _text.equals(":=")
          || _text.equals("|"))
          {
            return Optional.of(TokenType.Keyword);
          }
        return Optional.of(TokenType.Operator);
      case t_ident :
        return GetItem()
          .map(TokenInfo::TokenType)
          // NYI check if all cases are considered
          .orElse(Optional.of(TokenType.Type));
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
      case t_indentationLimit :
      case t_lineLimit :
      case t_undefined :
        return Optional.empty();
      default:
        throw new RuntimeException("not implemented.");
      }
  }

  private static Optional<TokenType> TokenType(HasSourcePosition item)
  {
    if (item instanceof AbstractFeature af)
      {
        if (af instanceof Feature f && f.state().equals(State.ERROR))
          {
            return Optional.empty();
          }
        switch (af.kind())
          {
          case OpenTypeParameter :
          case TypeParameter :
            return Optional.of(TokenType.TypeParameter);
          case Field :
            if (FeatureTool.IsArgument(af))
              {
                return Optional.of(TokenType.Parameter);
              }
            return Optional.of(TokenType.Property);
          case Choice :
            return Optional.of(TokenType.Enum);
          case Intrinsic :
          case Abstract :
          case Routine :
            if (af.isConstructor()
              && af.valueArguments().size() == 0
              && af.code().containsOnlyDeclarations()
              && !FeatureTool.DoesInherit(af))
              {
                if (ParserTool.DeclaredFeatures(af).count() > 0)
                  {
                    return Optional.of(TokenType.Namespace);
                  }
                if (FeatureTool.IsUsedInChoice(af))
                  {
                    return Optional.of(TokenType.EnumMember);
                  }
                return Optional.of(TokenType.Type);
              }
            if (af.isConstructor())
              {
                return Optional.of(TokenType.Class);
              }
            if (FeatureTool.OuterFeatures(af).allMatch(x -> x.valueArguments().size() == 0))
              {
                return Optional.of(TokenType.Function);
              }
            return Optional.of(TokenType.Method);
          }
      }
    var ac = (AbstractCall) item;
    if (ac.isInheritanceCall())
      {
        return Optional.of(TokenType.Interface);
      }
    if (CallTool.IsFixLikeCall(ac))
      {
        return Optional.of(TokenType.Operator);
      }
    // "normal" call
    return TokenType(ac.calledFeature());
  }

  // NYI this should be done differently
  // how can we find the feature/call of an ident token more quickly?
  private Optional<HasSourcePosition> GetItem()
  {
    return Optional.empty();
    // NYI Too slow!
    // var key = KeyOf(start);
    // if (!Pos2Items().containsKey(key))
    //   {
    //     return Optional.empty();
    //   }
    // return Optional.of(Pos2Items()
    //   .get(key));
  }

  // NYI move this somewhere better
  public static Integer KeyOf(SourcePosition pos)
  {
    // NYI better key
    return pos.line() * 1000 + pos.column();
  }

  public boolean IsWhitespace()
  {
    return token() == Token.t_ws;
  }

  private final static Set<Token> leftBrackets =
    List.of(Token.t_lbrace, Token.t_lbracket, Token.t_lparen).stream().collect(Collectors.toUnmodifiableSet());
  private final static Set<Token> rightBrackets =
    List.of(Token.t_rbrace, Token.t_rbracket, Token.t_rparen).stream().collect(Collectors.toUnmodifiableSet());

  public boolean IsLeftBracket()
  {
    return leftBrackets.contains(token());
  }

  public boolean IsRightBracket()
  {
    return rightBrackets.contains(token());
  }

  @Override
  public String toString()
  {
    return "TokenInfo[start=" + start() + ", end=" + end() + ", text=" + text() + ", token=" + token() + "]";
  }

}
