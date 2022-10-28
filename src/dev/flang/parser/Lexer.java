/*

This file is part of the Fuzion language implementation.

The Fuzion language implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language implementation is distributed in the hope that it will be
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
 * Source of class Lexer
 *
 *---------------------------------------------------------------------*/

package dev.flang.parser;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.stream.Stream;

import dev.flang.util.Callable;
import dev.flang.util.Errors;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;
import dev.flang.util.UnicodeData;


/**
 * Lexer performs the lexical analysis of Fuzion source code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Lexer extends SourceFile
{


  /*-----------------------------  classes  -----------------------------*/


  /**
   * Class representing tokens like t_lparen or specific operators like '<', '<'.
   */
  static class TokenOrOp
  {
    /**
     * token
     */
    Token _token;

    /**
     * string in token is Token.t_op.
     */
    String _detail;


    /**
     * Create Parens for given field values.
     */
    private TokenOrOp(Token t, String d)
    {
      _token = t;
      _detail = d;
    }

    public String toString()
    {
      return
        _token != Token.t_op ? _token.toString()
                             : "operator '" + _detail + "'";
    }
  }


  /**
   * Class representing parentheses like '(', ')' or '<', '<'.
   */
  static class Parens
  {

    /**
     * left and right tokens,
     */
    TokenOrOp _left, _right;


    /**
     * Create Parens for non-operators like t_lparen
     */
    Parens(Token l, Token r)
    {
      _left  = new TokenOrOp(l, null);
      _right = new TokenOrOp(r, null);
    }


    /**
     * Create Parens for operators like '<', '>'
     */
    Parens(String l, String r)
    {
      _left  = new TokenOrOp(Token.t_op, l);
      _right = new TokenOrOp(Token.t_op, r);
    }
  }

  /*----------------------------  constants  ----------------------------*/


  /**
   * Tokens produced by the lexer
   */
  public static enum Token
  {
    t_error,       // erroneous input
    t_ws,          // whitespace
    t_comment,     // comment
    t_op,          // operators +, -, *, /, ., |, etc.
    t_comma,       // ,
    t_lparen,      // (
    t_rparen,      // )
    t_lbrace,      // {
    t_rbrace,      // }
    t_lcrochet,    // [
    t_rcrochet,    // ]
    t_semicolon,   // ;
    t_question,    // ?
    t_numliteral,  // 123
    t_ident,       // abc
    t_stringQQ,    // "abc"
    t_stringQD,    // '"x is $'   in "x is $x.".
    t_stringQB,    // '"a+b is {' in "a+b is {a+b}."
    t_StringDQ,    // '+-*"'      in "abc$x+-*"
    t_StringDD,    // '+-*$'      in "abc$x+-*$x.".
    t_StringDB,    // '+-*{'      in "abc$x+-*{a+b}."
    t_stringBQ,    // '}+-*"'     in "abc{x}+-*"
    t_stringBD,    // '}+-*$'     in "abc{x}+-*$x.".
    t_stringBB,    // '}+-*{'     in "abc{x}+-*{a+b}."
    t_this("this"),
    t_env("env"),
    t_check("check"),
    t_else("else"),
    t_if("if"),
    t_then("then"),
    t_is("is"),
    t_abstract("abstract"),
    t_intrinsic("intrinsic"),
    t_intrinsic_constructor("intrinsic_constructor"),
    t_for("for"),
    t_in("in"),
    t_do("do"),
    t_loop("loop"),
    t_while("while"),
    t_until("until"),
    t_variant("variant"),
    t_pre("pre"),
    t_post("post"),
    t_inv("inv"),
    t_var("var"),
    t_match("match"),
    t_fun("fun"),
    t_value("value"),
    t_ref("ref"),
    t_lazy("lazy"),
    t_synchronized("synchronized"),
    t_redef("redef"),
    t_redefine("redefine"),
    t_const("const"),
    t_leaf("leaf"),
    t_infix("infix"),
    t_prefix("prefix"),
    t_postfix("postfix"),
    t_ternary("ternary"),
    t_index("index"),
    t_set("set"),
    t_export("export"),
    t_private("private"),
    t_protected("protected"),
    t_public("public"),
    t_of("of"),
    t_type("type"),
    t_eof,               // end of file
    t_indentationLimit,  // token's indentation is not sufficient
    t_lineLimit,         // token is in next line while sameLine() parsing is enabled
    t_spaceLimit,        // token follows white space while endAtSpace is enabled
    t_undefined;         // current token before first call to next()

    /**
     * In case this Token is a keyword, this is the keyword.
     */
    private final String _keyword;

    /**
     * Construct non-keyword token
     */
    Token()
    {
      _keyword = null;

      if (POSTCONDITIONS) ensure
        (!isKeyword());
    }

    /**
     * Construct keyword token
     *
     * @param keyword the keyword.
     */
    Token(String keyword)
    {
      _keyword = keyword;

      if (POSTCONDITIONS) ensure
        (isKeyword(),
         keyword() == keyword);
    }

    /**
     * Is this a keyword token?
     */
    public boolean isKeyword()
    {
      return _keyword != null;
    }

    /**
     * For a keyword token, return the keyword.
     */
    String keyword()
    {
      if (PRECONDITIONS) require
        (isKeyword());

      return _keyword;
    }

    /**
     * Sorted array of all the keyword tokens
     */
    public static Token[] _keywords = Stream.of(Token.class.getEnumConstants())
      .filter((t) -> t.isKeyword())
      .sorted((t1, t2) -> t1.keyword().compareTo(t2.keyword()))
      .toArray(Token[]::new);


    /**
     * String representation for debugging.
     */
    public String toString()
    {
      String result;
      if (isKeyword())
        {
          result = keyword();
        }
      else
        {
          switch (this)
            {
            case t_op                : result = "operator"                                   ; break;
            case t_comma             : result = "comma ','"                                  ; break;
            case t_lparen            : result = "left parenthesis '('"                       ; break;
            case t_rparen            : result = "right parenthesis ')'"                      ; break;
            case t_lbrace            : result = "left curly brace '{'"                       ; break;
            case t_rbrace            : result = "right curly brace '}'"                      ; break;
            case t_lcrochet          : result = "left crochet '['"                           ; break;
            case t_rcrochet          : result = "right crochet ']'"                          ; break;
            case t_semicolon         : result = "semicolon ';'"                              ; break;
            case t_question          : result = "question mark '?'"                          ; break;
            case t_numliteral        : result = "numeric literal"                            ; break;
            case t_ident             : result = "identifier"                                 ; break;
            case t_stringQQ          : result = "string constant"                            ; break;
            case t_stringQD          : result = "string constant ending in $"                ; break;
            case t_stringQB          : result = "string constant ending in {"                ; break;
            case t_StringDQ          : result = "string constant after $<id>"                ; break;
            case t_StringDD          : result = "string constant after $<id> ending in $"    ; break;
            case t_StringDB          : result = "string constant after $<id> ending in {"    ; break;
            case t_stringBQ          : result = "string constant after {<expr>}"             ; break;
            case t_stringBD          : result = "string constant after {<expr>} ending in $" ; break;
            case t_stringBB          : result = "string constant after {<expr>} ending in {" ; break;
            case t_eof               : result = "end-of-file"                                ; break;
            default                  : result = super.toString()                             ; break;
            }
        }
      return result;
    }
  }


  /**
   * Private code point classes
   */
  private static final byte K_UNKNOWN =  0;
  private static final byte K_OP      =  1;  // '+'|'-'|'*'|'%'|'|'|'~'|'#'|'!'|'$'|'&'|'@'|':'|'<'|'>'|'='|'^'|'.')+;
  private static final byte K_WS      =  2;  // spaces, tabs, lf, cr, ...
  private static final byte K_SLASH   =  3;  // '/', introducing a comment or an operator.
  private static final byte K_SHARP   =  4;  // '/', introducing a comment or an operator.
  private static final byte K_COMMA   =  5;  // ','
  private static final byte K_LPAREN  =  6;  // '('  round brackets or parentheses
  private static final byte K_RPAREN  =  7;  // ')'
  private static final byte K_LBRACE  =  8;  // '{'  curly brackets or braces
  private static final byte K_RBRACE  =  9;  // '}'
  private static final byte K_LCROCH  = 10;  // '['  square brackets or crochets
  private static final byte K_RCROCH  = 11;  // ']'
  private static final byte K_SEMI    = 12;  // ';'
  private static final byte K_DIGIT   = 13;  // '0'..'9'
  private static final byte K_LETTER  = 14;  // 'A'..'Z', 'a'..'z', mathematical letter
  private static final byte K_GRAVE   = 15;  // '`'  backtick
  private static final byte K_DQUOTE  = 16;  // '"'
  private static final byte K_SQUOTE  = 17;  // '''
  private static final byte K_BACKSL  = 18;  // '\\'
  private static final byte K_NUMERIC = 19;  // mathematical digit
  private static final byte K_EOF     = 20;  // end-of-file
  private static final byte K_ERROR   = 21;  // an error occurred


  /**
   * Code point classes for ASCII codepoints
   */
  private static byte[] _asciiKind = new byte[]
  {
    // 0…
    K_UNKNOWN /* NUL */, K_UNKNOWN /* SOH */, K_UNKNOWN /* STX */, K_UNKNOWN /* ETX */,
    K_UNKNOWN /* EOT */, K_UNKNOWN /* ENQ */, K_UNKNOWN /* ACK */, K_UNKNOWN /* BEL */,
    K_UNKNOWN /* BS  */, K_WS      /* HT  */, K_WS      /* LF  */, K_WS      /* VT  */,
    K_WS      /* FF  */, K_WS      /* CR  */, K_UNKNOWN /* SO  */, K_UNKNOWN /* SI  */,
    // 1…
    K_UNKNOWN /* DLE */, K_UNKNOWN /* DC1 */, K_UNKNOWN /* DC2 */, K_UNKNOWN /* DC3 */,
    K_UNKNOWN /* DC4 */, K_UNKNOWN /* NAK */, K_UNKNOWN /* SYN */, K_UNKNOWN /* ETB */,
    K_UNKNOWN /* CAN */, K_UNKNOWN /* EM  */, K_UNKNOWN /* SUB */, K_UNKNOWN /* ESC */,
    K_UNKNOWN /* FS  */, K_UNKNOWN /* GS  */, K_UNKNOWN /* RS  */, K_UNKNOWN /* US  */,
    // 2…
    K_WS      /* SP  */, K_OP      /* !   */, K_DQUOTE  /* "   */, K_SHARP   /* #   */,
    K_OP      /* $   */, K_OP      /* %   */, K_OP      /* &   */, K_SQUOTE  /* '   */,
    K_LPAREN  /* (   */, K_RPAREN  /* )   */, K_OP      /* *   */, K_OP      /* +   */,
    K_COMMA   /* ,   */, K_OP      /* -   */, K_OP      /* .   */, K_SLASH   /* /   */,
    // 3…
    K_DIGIT   /* 0   */, K_DIGIT   /* 1   */, K_DIGIT   /* 2   */, K_DIGIT   /* 3   */,
    K_DIGIT   /* 4   */, K_DIGIT   /* 5   */, K_DIGIT   /* 6   */, K_DIGIT   /* 7   */,
    K_DIGIT   /* 8   */, K_DIGIT   /* 9   */, K_OP      /* :   */, K_SEMI    /* ;   */,
    K_OP      /* <   */, K_OP      /* =   */, K_OP      /* >   */, K_OP      /* ?   */,
    // 4…
    K_OP      /* @   */, K_LETTER  /* A   */, K_LETTER  /* B   */, K_LETTER  /* C   */,
    K_LETTER  /* D   */, K_LETTER  /* E   */, K_LETTER  /* F   */, K_LETTER  /* G   */,
    K_LETTER  /* H   */, K_LETTER  /* I   */, K_LETTER  /* J   */, K_LETTER  /* K   */,
    K_LETTER  /* L   */, K_LETTER  /* M   */, K_LETTER  /* N   */, K_LETTER  /* O   */,
    // 5…
    K_LETTER  /* P   */, K_LETTER  /* Q   */, K_LETTER  /* R   */, K_LETTER  /* S   */,
    K_LETTER  /* T   */, K_LETTER  /* U   */, K_LETTER  /* V   */, K_LETTER  /* W   */,
    K_LETTER  /* X   */, K_LETTER  /* Y   */, K_LETTER  /* Z   */, K_LCROCH  /* [   */,
    K_BACKSL  /* \   */, K_RCROCH  /* ]   */, K_OP      /* ^   */, K_LETTER  /* _   */,
    // 6…
    K_GRAVE   /* `   */, K_LETTER  /* a   */, K_LETTER  /* b   */, K_LETTER  /* c   */,
    K_LETTER  /* d   */, K_LETTER  /* e   */, K_LETTER  /* f   */, K_LETTER  /* g   */,
    K_LETTER  /* h   */, K_LETTER  /* i   */, K_LETTER  /* j   */, K_LETTER  /* k   */,
    K_LETTER  /* l   */, K_LETTER  /* m   */, K_LETTER  /* n   */, K_LETTER  /* o   */,
    // 7…
    K_LETTER  /* p   */, K_LETTER  /* q   */, K_LETTER  /* r   */, K_LETTER  /* s   */,
    K_LETTER  /* t   */, K_LETTER  /* u   */, K_LETTER  /* v   */, K_LETTER  /* w   */,
    K_LETTER  /* x   */, K_LETTER  /* y   */, K_LETTER  /* z   */, K_LBRACE  /* {   */,
    K_OP      /* |   */, K_RBRACE  /* }   */, K_OP      /* ~   */, K_UNKNOWN /* DEL */
  };


  /**
   * ASCII control sequence names or null if normal ASCII char.
   */
  private static String[] _asciiControlName = new String[]
  {
    // 0…
    "NUL", "SOH", "STX", "ETX", "EOT", "ENQ", "ACK", "BEL", "BS ", "HT ", "LF ", "VT ", "FF ", "CR ", "SO ", "SI ",
    // 1…
    "DLE", "DC1", "DC2", "DC3", "DC4", "NAK", "SYN", "ETB", "CAN", "EM ", "SUB", "ESC", "FS" , "GS" , "RS" , "US ",
    // 2…
    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
    // 3…
    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
    // 4…
    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
    // 5…
    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
    // 6…
    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
    // 7…
    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, "DEL"
  };


  /*----------------------------  variables  ----------------------------*/


  /**
   * Stack of String lexers for escape sequences, identifiers and expressions
   * embedded in strings.
   */
  private StringLexer _stringLexer = null;


  /**
   * The current token
   */
  private Token _curToken = Token.t_undefined;


  /**
   * For _curToken == Token.t_numliteral, this gives the details:
   */
  private Literal _curLiteral = null;


  /**
   * Position of the current token
   */
  private int _curPos = -1;


  /**
   * Line number of the current token
   */
  private int _curLine = 1;


  /**
   * Position of the previous token, -1 if none
   */
  private int _lastPos = -1;


  /**
   * Minimum indentation required for the current token: current() must have
   * indentation > _minIndent, while currentAtMinIndent() must have indentation
   * >= _minIndent.
   */
  private int _minIndent = -1;


  /**
   * Token at this pos will be returned by current() even if its indentaion is
   * at <= _minIndent. If set to the first token of a statement that sets
   * _minIntend, this ensures that we can still parse the first token of this
   * statement.
   */
  private int _minIndentStartPos = -1;


  /**
   * Line restriction for current()/currentAtMinIndent(): Symbols not in this
   * line will be replaced by t_lineLimit.
   */
  private int _sameLine = -1;


  /**
   * White space restriction for current()/currentAtMinIndent(): Symbols after
   * this position that are preceded by white space will be replaced by
   * t_spaceLimit.
   */
  private int _endAtSpace = Integer.MAX_VALUE;


  /**
   * Has the raw token before current() been skipped because ignore(t) resulted
   * in true?
   */
  private boolean _ignoredTokenBefore = false;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create a lexer for the given file
   */
  public Lexer(Path fileName)
  {
    super(fileName);

    if (PRECONDITIONS) require
      (fileName != null);

    next();
  }


  /**
   * Fork this lexer.
   */
  Lexer(Lexer original)
  {
    super(original);

    _curToken = original._curToken;
    _curPos = original._curPos;
    _curLine = original._curLine;
    _lastPos = original._lastPos;
    _minIndent = original._minIndent;
    _minIndentStartPos = original._minIndentStartPos;
    _sameLine = original._sameLine;
    _endAtSpace = original._endAtSpace;
    _ignoredTokenBefore = original._ignoredTokenBefore;
    _stringLexer = original._stringLexer == null ? null : new StringLexer(original._stringLexer);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Is the given token ignored?  This is usually the case for t_erro, t_ws and
   * t_comment, but it might be different for tools like the pretty printer that
   * want to know about white space and comments.
   *
   * We should never ignore t_eof since this would lead to an endless loop in
   * next().
   *
   * @param t a token
   *
   * @return true iff t is to be ignored
   */
  public boolean ignore(Token t)
  {
    return
      t == Token.t_error   ||
      t == Token.t_ws      ||
      t == Token.t_comment;
  }


  /**
   * Has there been an ignore()d token before current()?  With the default
   * implementation of ignore(), this checks if there was whitespace or a
   * comment before this token.
   *
   * @return true if the previous raw token was skipped
   */
  public boolean ignoredTokenBefore()
  {
    return _ignoredTokenBefore;
  }


  /**
   * Is the next token after current() an ignore()d token?  With the default
   * implementation of ignore(), this checks if there follows whitespace or
   * a comment after this token.
   *
   * @return true if the next raw token would be skipped
   */
  public boolean ignoredTokenAfter()
  {
    var f = new Lexer(this);
    f.nextRaw();
    return ignore(f.currentNoLimit());
  }


  /**
   * Set the minimum indentation to the position of startPos.  The token at
   * startPos is excluded from the limit, so it will be returned by current(),
   * while later tokens at the same indentation level will be replaced by
   * t_indentationLimit.
   *
   * @param startPos defines the indentation limit, -1 for none.
   *
   * @return the previous min indentation, -1 if none.
   */
  int minIndent(int startPos)
  {
    int result = _minIndentStartPos;
    _minIndent = startPos >= 0 ? codePointInLine(startPos) : -1;
    _minIndentStartPos = startPos;

    return result;
  }


  /**
   * Get the current minimun indentation.
   *
   * @return the min indentation, -1 if none.
   */
  int minIndent()
  {
    return _minIndent;
  }


  /**
   * Restrict parsing to the given line.  Any tokens that are not in this line
   * will be replaced by t_lineLimit.
   *
   * @param line the line restriction, -1 to remove the restriction.
   *
   * @return the previous line restriction, -1 if none.
   */
  int sameLine(int line)
  {
    int result = _sameLine;
    _sameLine = line;

    return result;
  }


  /**
   * Is parsing restrict to one line?  This is enabled by a call to sameLine()
   * with a positive argument.
   *
   * @return true iff parsing is restricted to current line
   */
  boolean isRestrictedToLine()
  {
    return _sameLine >= 0;
  }


  /**
   * Restrict parsing until the next occurence of white space.  Symbols after
   * fromPos that are preceded by white space will be replaced by t_spaceLimit.
   *
   * @param fromPos the position of the last token that is permitted to be
   * preceded by white space.
   *
   * @return the previous endAtSpace-restriction, Integer.MAX_VALUE if none.
   */
  int endAtSpace(int fromPos)
  {
    if (PRECONDITIONS) require
      (fromPos >= 0);

    int result = _endAtSpace;
    _endAtSpace = fromPos;

    return result;
  }


  /**
   * Convenience method to temporarily reset limits set via sameLine() or
   * endAtSpace() while parsing.
   *
   * @param c functional interface for the parser code that should be executed
   * without sameLine/endAtSpace limits.
   *
   * @return c.call()'s result.
   */
  <V> V relaxLineAndSpaceLimit(Callable<V> c)
  {
    int oldLine = sameLine(-1);
    int oldEAS = endAtSpace(Integer.MAX_VALUE);
    V result = c.call();
    sameLine(oldLine);
    endAtSpace(oldEAS);
    return result;
  }


  /**
   * short-hand for bracketTermWithNLs with atMinIndent==false and c==def.
   */
  <V> V bracketTermWithNLs(Parens brackets, String rule, Callable<V> c)
  {
    return bracketTermWithNLs(brackets, rule, c, c);
  }


  /**
   * Parse a term in brackets that may extend over several lines. In case this appears in an
   * expression that must be in the same line, e.g.,
   *
   *   n := a * (b + c) - d
   *
   * continue the same line after the closing bracket, e.g.
   *
   *   n := a * (b
   *         + c) - d
   *
   * @param brackets the opening / closing bracket to use
   *
   * @param rule the parser rule we are processing, used in syntax error messages.
   *
   * @param c code to parse the inside of the brackets
   *
   * @param def code to produce a default result in case closing bracket follows
   * immediately after opening bracket.
   *
   * @return value returned by c or def, resp.
   */
  <V> V bracketTermWithNLs(Parens brackets, String rule, Callable<V> c, Callable<V> def)
  {
    var start = brackets._left;
    var end   = brackets._right;
    var ol = line();
    var startsIndent = pos() == _minIndentStartPos;
    match(true, start, rule);
    V result = relaxLineAndSpaceLimit(!currentMatches(true, end) ? c : def);
    var nl = line();
    relaxLineAndSpaceLimit(() ->
                           {
                             match(true, end, rule);
                             return Void.TYPE; // is there a better unit type in Java?
                           });
    var sl = sameLine(-1);
    if (sl == ol)
      {
        sl = nl;
      }
    sameLine(sl);
    return result;
  }


  /**
   * check if we can parse a bracket term and skip it if so.
   *
   * @param brackets the opening / closing bracket to use
   *
   * @param c code to parse the inside of the brackets
   *
   * @return true if both brackets are present and c returned true, Otherwise no
   * bracket term could be parsed and the parser/lexer is at an undefined
   * position.
   */
  boolean skipBracketTermWithNLs(Parens brackets, Callable<Boolean> c)
  {
    var start = brackets._left;
    var end   = brackets._right;
    var ol = line();
    var startsIndent = pos() == _minIndentStartPos;
    var result = skip(false, start) && relaxLineAndSpaceLimit(c);
    if (result)
      {
        var nl = line();
        result = relaxLineAndSpaceLimit(() -> {
            return skip(startsIndent , end);
          });
        var sl = sameLine(-1);
        if (sl == ol)
          {
            sl = nl;
          }
        sameLine(sl);
      }
    return result;
  }


  /**
   * Advance to the next token that is not ignore()d.
   */
  public void next()
  {
    _lastPos = _curPos;
    _ignoredTokenBefore = false;
    nextRaw();
    while (ignore(currentNoLimit()))
      {
        _ignoredTokenBefore = true;
        nextRaw();
      }
  }


  /**
   * The current token.  If minIndent >= 0 and the current token is not indented
   * deeper than this limit, return Token.t_indentationLimit.
   *
   * @param minIndent the minimum indentation (-1 if none) for the next token,
   * return t_indentationLimit if not met.
   *
   * @param sameLine the line number (-1 if any line) for the next token, return
   * t_lineLimit if next token is in a different line.
   *
   * @param spaceLimit the white space restriction (Integer.MAX_VALUE if none):
   * Any token after this position will be replaced by t_spaceLimit.
   */
  Token current(int minIndent, int sameLine, int endAtSpace)
  {
    var t = _curToken;
    int l = _curLine;
    int p = _curPos;
    return
      t == Token.t_eof                                     ? t                        :
      sameLine  >= 0 && l != sameLine                      ? Token.t_lineLimit        :
      p > endAtSpace && ignoredTokenBefore()               ? Token.t_spaceLimit       :
      p == _minIndentStartPos                              ? t                        :
      minIndent >= 0 && codePointInLine(p, l) <= minIndent ? Token.t_indentationLimit
                                                           : _curToken;
  }


  /**
   * The current token.  If indentation limit was set and the current token is
   * not indented deeper than this limit, return Token.t_indentationLimit.
   */
  public Token current()
  {
    return current(_minIndent, _sameLine, _endAtSpace);
  }


  /**
   * The current token.  If indentation limit was set and the current token is
   * indented less than this limit minus 1, return Token.t_indentationLimit.
   */
  Token currentAtMinIndent()
  {
    return current(_minIndent - 1, _sameLine, _endAtSpace);
  }


  /**
   * The current token.  If indentation limit was set and the current token is
   * indented less than (atMinIndent==true) or not deeper then
   * (atMinIndent==false) this limit, return Token.t_indentationLimit.
   */
  Token current(boolean atMinIndent)
  {
    return atMinIndent ? currentAtMinIndent() : current();
  }


  /**
   * The current token.
   */
  Token currentNoLimit()
  {
    return current(-1, -1, Integer.MAX_VALUE);
  }


  /**
   * The byte position in the source file.
   */
  public int pos()
  {
    return _curPos;
  }


  /**
   * The byte position of the previous non-skip token in the source file.  -1 if
   * this does not exist.
   */
  int lastPos()
  {
    return _lastPos;
  }


  /**
   * The current position as a SourcePosition instance
   */
  SourcePosition posObject()
  {
    return posObject(pos());
  }


  /**
   * The given position as a SourcePosition instance
   */
  SourcePosition posObject(int pos)
  {
    return sourcePos(pos);
  }


  /**
   * Position of the first byte in source file after the current token.
   */
  private int endPos()
  {
    return bytePos();
  }


  /**
   * The line number of the current token.
   */
  int line()
  {
    return _curLine;
  }


  /**
   * Advance to the next token. The next token might be an ignored token, i.e,
   * white space or a comment.
   */
  public void nextRaw()
  {
    _curPos = bytePos();
    int p = curCodePoint();
    var token = Token.t_undefined;
    if (p == SourceFile.END_OF_FILE)
      {
        token = Token.t_eof;
      }
    else if (_stringLexer != null)
      {
        token = _stringLexer.nextRaw();
      }
    if (token == Token.t_undefined)
      {
        nextCodePoint();
        switch (kind(p))
          {
          case K_UNKNOWN :
          case K_GRAVE   :    // '`'  backtick
          case K_BACKSL  :    // '\\'
          case K_SQUOTE  :    // '''
            {
              Errors.error(sourcePos(),
                           "Unknown code point in source file",
                           "Unknown code point " + Integer.toHexString(p) + " is not permitted by Fuzion's grammar.");
              token = Token.t_error;
              break;
            }
          case K_SHARP   :   // '#'
            {
              boolean SHARP_COMMENT_ONLY_IF_IN_COL_1 = false;
              token =
                !SHARP_COMMENT_ONLY_IF_IN_COL_1 ||
                codePointInLine(_curPos) == 1      ? skipUntilEOL() // comment until end of line
                                                   : skipOp(Token.t_op);
              break;
            }
          /**
OPERATOR  : ( '!'
            | '$'
            | '%'
            | '&'
            | '*'
            | '+'
            | '-'
            | '.'
            | ':'
            | '<'
            | '='
            | '>'
            | '?'
            | '^'
            | '|'
            | '~'
            )+
          ;
          */
          case K_OP      :   // '+'|'-'|'*'|'%'|'|'|'~'|'!'|'$'|'&'|'@'|':'|'<'|'>'|'='|'^'|'.')+;
            {
              token = skipOp(p == '?' ? Token.t_question : Token.t_op);
              break;
            }
          /**
LF          : ( '\r'? '\n'
                | '\r'
                | '\f'
              )
            ;
          */
          case K_WS      :   // spaces, tabs, lf, cr, ...
            {
              int last = p;
              p = curCodePoint();
              token = checkWhiteSpace(last, p);
              while (kind(p) == K_WS)
                {
                  nextCodePoint();
                  last = p;
                  p = curCodePoint();
                  if (token != Token.t_error)
                    {
                      token = checkWhiteSpace(last,p);
                    }
                }
              break;
            }
          case K_SLASH   :   // '/', introducing a comment or an operator.
            {
              p = curCodePoint();
              token = kind(p) == K_SLASH ? skipUntilEOL() : // comment until end of line
                      p == '*'           ? skipComment()
                                         : skipOp(Token.t_op);
              break;
            }
          /**
COMMA       : ','
            ;
          */
          case K_COMMA   :   // ','
            {
              token = Token.t_comma;
              break;
            }
          /**
LPAREN      : '('
            ;
          */
          case K_LPAREN  :    // '('  round brackets or parentheses
            {
              token = Token.t_lparen;
              break;
            }
          /**
RPAREN      : ')'
            ;
          */
          case K_RPAREN  :    // ')'
            {
              token = Token.t_rparen;
              break;
            }
          /**
BRACEL      : '{'
            ;
          */
          case K_LBRACE  :    // '{'  curly brackets or braces
            {
              token = Token.t_lbrace;
              break;
            }
          /**
BRACER      : '}'
            ;
          */
          case K_RBRACE  :    // '}'
            {
              token = Token.t_rbrace;
              break;
            }
          /**
LBRACKET    : '['
            ;
          */
          case K_LCROCH  :    // '['  square brackets or crochets
            {
              token = Token.t_lcrochet;
              break;
            }
          /**
RBRACKET    : ']'
            ;
          */
          case K_RCROCH  :    // ']'
            {
              token = Token.t_rcrochet;
              break;
            }
          /**
SEMI        : ';'
            ;
          */
          case K_SEMI    :    // ';'
            {
              token = Token.t_semicolon;
              break;
            }
          /**
NUM_LITERAL : [0-9]+
            ;
          */
          case K_DIGIT   :    // '0'..'9'
            {
              _curLiteral = literal(p);
              token = Token.t_numliteral;
              break;
            }
          /**
IDENT     : ( 'a'..'z'
            | 'A'..'Z'
            )
            ( 'a'..'z'
            | 'A'..'Z'
            | '0'..'9'
            | '_'
            )*
          ;
          */
          case K_LETTER  :    // 'A'..'Z', 'a'..'z'
            {
              while (partOfIdentifier(curCodePoint()))
                {
                  nextCodePoint();
                }
              token = findKeyword();
              break;
            }
          case K_DQUOTE  :    // '"'
            {
              token = new StringLexer().finish();
              break;
            }
          case K_ERROR   :    // an error occurred
            {
              token = Token.t_error;
              break;
            }
          default:
            {
              Errors.error(sourcePos(),
                           "Unexpected unicode character \\u" + Integer.toHexString(0x1000000+p).substring(1).toUpperCase() + " found",
                           null);
              token = Token.t_error;
            }
          }
      }
    _curToken = token;
  }


  /**
   * Check if given consecutive white space code points are acceptable,
   * increment _curLine if last/p start a new line.
   *
   * @param p1 the first code point
   *
   * @param p2 the second code point
   *
   * @return Token.t_ws or Token.t_error in case of illegal white space.
   */
  Token checkWhiteSpace(int p1, int p2)
  {
    var result = Token.t_ws;
    if (isNewLine(p1, p2))
      {
        _curLine++;
      }
    else if (p1 != ' ')
      {
        Errors.error(sourcePos(),
                     "Unexpected white space character \\u" + Integer.toHexString(0x1000000+p1).substring(1).toUpperCase() + " found",
                     null);
        result = Token.t_error;
      }
    return result;
  }


  /**
   * Check if the given code point may be part of an identifier.  This is true
   * for letters, digits and numeric code points.
   *
   * @param cp a code point
   *
   * @return true iff cp may be part of an identifier, e.g., 'i', '3', '²', etc.
   */
  private boolean partOfIdentifier(int cp)
  {
    return switch (kind(cp))
      {
      case K_LETTER, K_DIGIT, K_NUMERIC -> true;
      default -> false;
      };
  }


  /**
   * Check if the current token in _sourceFile at pos()..endPos() is a keyword.
   *
   * @return the corresponding keyword token such as Token.t_public if this is
   * the case, Token.t_ident otherwise.
   */
  private Token findKeyword()
  {
    Token result = Token.t_ident;
    // perform binary search in Token.keywords array:
    int l = 0;
    int r = Token._keywords.length-1;
    while (l <= r)
      {
        int m = (l + r) / 2;
        Token t = Token._keywords[m];
        int c = compareToString(pos(), endPos(), t._keyword);
        if (c == 0)
          {
            result = t;
          }
        if (c <= 0)
          {
            r = m - 1;
          }
        if (c >= 0)
          {
            l = m + 1;
          }
      }
    return result;
  }


  /**
   * Check if the given string is a keyword.
   *
   * @return true iff s is a Fuzion keyword.
   */
  public static boolean isKeyword(String s)
  {
    // perform binary search in Token.keywords array:
    int l = 0;
    int r = Token._keywords.length-1;
    while (l <= r)
      {
        int m = (l + r) / 2;
        Token t = Token._keywords[m];
        int c = s.compareTo(t._keyword);
        if (c == 0)
          {
            return true;
          }
        if (c <= 0)
          {
            r = m - 1;
          }
        if (c >= 0)
          {
            l = m + 1;
          }
      }
    return false;
  }


  /**
   * Determine the kind (K_*) for a given codepoint.
   */
  private int kind(int p)
  {
    int kind;
    if (p <= 0x7f)
      {
        kind = _asciiKind[p];
      }
    else if (p == SourceFile.END_OF_FILE)
      {
        kind = K_EOF;
      }
    else if (p == SourceFile.BAD_CODEPOINT)
      {
        kind = K_ERROR;
      }
    else
      {
        kind = switch (UnicodeData.category(p))
          {
          case "Cc" -> K_UNKNOWN;  // 	Other, Control
          case "Cf" -> K_UNKNOWN;  // 	Other, Format
          case "Cn" -> K_UNKNOWN;  // 	Other, Not Assigned (no characters in the file have this property)
          case "Co" -> K_UNKNOWN;  // 	Other, Private Use
          case "Cs" -> K_UNKNOWN;  // 	Other, Surrogate
          case "LC" -> K_LETTER;   // 	Letter, Cased
          case "Ll" -> K_LETTER;   // 	Letter, Lowercase
          case "Lm" -> K_LETTER;   // 	Letter, Modifier
          case "Lo" -> K_LETTER;   // 	Letter, Other
          case "Lt" -> K_LETTER;   // 	Letter, Titlecase
          case "Lu" -> K_LETTER;   // 	Letter, Uppercase
          case "Mc" -> K_UNKNOWN;  // 	Mark, Spacing Combining
          case "Me" -> K_UNKNOWN;  // 	Mark, Enclosing
          case "Mn" -> K_UNKNOWN;  // 	Mark, Nonspacing
          case "Nd" -> K_NUMERIC;  // 	Number, Decimal Digit
          case "Nl" -> K_NUMERIC;  // 	Number, Letter
          case "No" -> K_NUMERIC;  // 	Number, Other
          case "Pc" -> K_OP;       // 	Punctuation, Connector
          case "Pd" -> K_OP;       // 	Punctuation, Dash
          case "Pe" -> K_OP;       // 	Punctuation, Close
          case "Pf" -> K_OP;       // 	Punctuation, Final quote (may behave like Ps or Pe depending on usage)
          case "Pi" -> K_OP;       // 	Punctuation, Initial quote (may behave like Ps or Pe depending on usage)
          case "Po" -> K_OP;       // 	Punctuation, Other
          case "Ps" -> K_OP;       // 	Punctuation, Open
          case "Sc" -> K_OP;       // 	Symbol, Currency
          case "Sk" -> K_OP;       // 	Symbol, Modifier
          case "Sm" -> K_OP;       // 	Symbol, Math
          case "So" -> K_OP;       // 	Symbol, Other
          case "Zl" -> K_UNKNOWN;  // 	Separator, Line
          case "Zp" -> K_UNKNOWN;  // 	Separator, Paragraph
          case "Zs" -> K_UNKNOWN;  // 	Separator, Space
          default   -> K_UNKNOWN;
          };
      }
    return kind;
  }


  /**
   * skip a numeric literal.
   *
LITERAL     : DIGITS_W_DOT EXPONENT
            ;
fragment
EXPONENT    : "E" PLUSMINUS DIGITS
            | "P" PLUSMINUS DIGITS
            |
            ;
fragment
PLUSMINUS   : "+"
            | "-"
            |
            ;
   *
   * @return the corresponding Token, currently always Token.t_numliteral.
   */
  Literal literal(int firstDigit)
  {
    var m = new Digits(firstDigit, true, false);
    var p = curCodePoint();
    return switch (p)
      {
      case 'P', 'E' ->
      {
        nextCodePoint();
        var neg = switch (curCodePoint())
          {
          case '+' -> { nextCodePoint(); yield false; }
          case '-' -> { nextCodePoint(); yield true; }
          default  -> false;
          };
        var fd = curCodePoint();
        if (kind(fd) == K_DIGIT)
          {
            nextCodePoint();
            var e = new Digits(fd, false, neg);
            yield new Literal(m, e, p == 'P');
          }
        else
          {
            Errors.error(sourcePos(),
                         "Broken numeric literal, expected exponent's digits after 'P' or 'E'",
                         null);
            yield new Literal();
          }
      }
      default -> new Literal(m);
      };
  }


  /**
   * Class holding the details for a numeric literal found be the parser.
   */
  class Literal
  {
    /**
     * Was there an error when scanning this literal?  If so, all the rest is
     * undefined.
     */
    public final boolean _hasError;


    /**
     * The main digits parts, e.g., for "0x_de_ad.c0deP0o123", this will have
     * _digits == "deadc0de", _base == hex, _dotAt == 4.
     */
    public final Digits _mantissa;

    /**
     * The exponents part or null if none. E.g., for "0x_de_ad.c0deP0o123", this
     * will have _digits == "123", _base == oct, _dotAt == 0.
     */
    public final Digits _exponent;

    /**
     * Is the exponent given with 'P' (and not with 'E').  E.g., for
     * "0x_de_ad.c0deP0o123", this will be true.
     */
    public final boolean _binaryExponent;


    /**
     * The original string of this literal, E.g., for
     * "0x_de_ad.c0deP0o123", this will be "0x_de_ad.c0deP0o123"
     */
    public final String _originalString;


    /**
     * Create literal with given mantissa, exponent and binaryExponent.
     */
    Literal(Digits m, Digits e, boolean binaryExponent)
    {
      _mantissa = m;
      _exponent = e;
      _binaryExponent = binaryExponent;
      _hasError = m == null || m._hasError || e != null && e._hasError;
      _originalString = tokenAsString();
    }

    /**
     * Create literal with given mantissa and no exponent.
     */
    Literal(Digits m)
    {
      this(m, null, false);
    }

    /**
     * Create literal with _hasError set.
     */
    Literal()
    {
      this(null);
    }


    /**
     * Check that this is a plain base-10 integer without dot and without exponent and return its digits.
     */
    String plainInteger()
    {
      if (_hasError)
        {
          return null;
        }
      else if (_mantissa._base != Digits.Base.dec || _exponent != null)
        {
          Errors.error(sourcePos(),
                       "Plain integer literal expected, not binary, octal, hex or float",
                       "Literal is '"+_originalString+"'");
          return null;
        }
      else
        {
          return _mantissa._digits;
        }
    }


    /**
     * The value of the mantissa, ignoring decimal '.' position (i.e., value of
     * '123.456' is 123456).
     */
    BigInteger mantissaValue()
    {
      if (_hasError)
        {
          return BigInteger.valueOf(0);
        }
      else
        {
          return _mantissa.absValue();
        }
    }

    /**
     * The base of the mantissa
     */
    int mantissaBase()
    {
      if (_hasError)
        {
          return 10;
        }
      else
        {
          return _mantissa._base._base;
        }
    }
    int mantissaDotAt()
    {
      return _hasError ? 0 : _mantissa._dotAt;
    }
    BigInteger exponent()
    {
      return _hasError || _exponent == null ? BigInteger.valueOf(0) : _exponent.signedValue();
    }
    int exponentBase()
    {
      return _binaryExponent ? 2 : 10;
    }
  }


  /**
   * class holding the digits of the mantissa or the exponent in a Literal.
   */
  public class Digits
  {

    /**
     * The base.
     */
    public enum Base {
      bin(2 , "binary" ),
      oct(8 , "octal"  ),
      dec(10, "decimal"),
      hex(16, "hex"    );
      final int _base;
      final String _name;
      Base(int base, String name)
      {
        _base = base;
        _name = name;
      }
    };


    /**
     * The base as indicated by prefix '0b', '0o', '0d', '0x'. E.g., for
     * "0x_de_ad.c0de", this will be hex.
     */
    public final Base _base;


    /**
     * The digits, without base prefix and without '_' separators.  E.g., for
     * "0x_de_ad.c0de", this will be "deadc0de"
     */
    public final String _digits;

    /**
     * Position of the decimal dot.  E.g., for "0x_de_ad.c0de", this will be
     * 4.
     */
    public int _dotAt = 0;

    /**
     * Was there a '-' preceding these digits?
     */
    public final boolean _negative;

    /**
     * Did an error occure?  E.g., for "0x_de_ad.c0de", this will be
     * false.
     */
    public boolean _hasError = false;

    /**
     * Helper routine to check if codepoint p is a digit for base. This is
     * generous, i.e., it will consider any digits '0'..'9' a digit even for
     * bases bin and oct and it will consider any letter a digit for base hex.
     */
    boolean isDigit(int p)
    {
      return kind(p) == K_DIGIT || (_base == Base.hex && kind(p) == K_LETTER && p != 'P');
    }

    /**
     * Scan digits of the form
     *
     * @param firstDigit the first digit that was skipped already
     *
     * @param allowDot true to parse DIGITS_W_DOT, false to parse DIGITS
     *
     * @param negative true if a '-' was encountered before firstDigit.
     *
DIGITS      :         DEC_DIGIT_ DEC_DIGITS_
            | "0" "b" BIN_DIGIT_ BIN_DIGITS_
            | "0" "o" OCT_DIGIT_ OCT_DIGITS_
            | "0" "d" DEC_DIGIT_ DEC_DIGITS_
            | "0" "x" HEX_DIGIT_ HEX_DIGITS_
            ;
DIGITS_W_DOT: DIGITS
            |         DEC_DIGIT_ DEC_DIGITS_ DEC_TAIL
            | "0" "b" BIN_DIGIT_ BIN_DIGITS_ BIN_TAIL
            | "0" "o" OCT_DIGIT_ OCT_DIGITS_ OCT_TAIL
            | "0" "d" DEC_DIGIT_ DEC_DIGITS_ DEC_TAIL
            | "0" "x" HEX_DIGIT_ HEX_DIGITS_ HEX_TAIL
            ;
fragment
UNDERSCORE  : "_"
            |
            ;
BIN_DIGIT   : "0" | "1"
            ;
BIN_DIGIT_  : UNDERSCORE BIN_DIGIT
            ;
fragment
BIN_DIGITS_ : BIN_DIGIT_ BIN_DIGITS_
            |
            ;
fragment
BIN_DIGITS  : BIN_DIGIT BIN_DIGITS
            |
            ;
BIN_TAIL    : "." BIN_DIGITS
            ;
OCT_DIGIT   : "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7"
            ;
OCT_DIGIT_  : UNDERSCORE OCT_DIGIT
            ;
fragment
OCT_DIGITS_ : OCT_DIGIT_ OCT_DIGITS_
            |
            ;
fragment
OCT_DIGITS  : OCT_DIGIT OCT_DIGITS
            |
            ;
OCT_TAIL    : "." OCT_DIGITS
            ;
DEC_DIGIT   : "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"
            ;
DEC_DIGIT_  : UNDERSCORE DEC_DIGIT
            ;
fragment
DEC_DIGITS_ : DEC_DIGIT_ DEC_DIGITS_
            |
            ;
fragment
DEC_DIGITS  : DEC_DIGIT DEC_DIGITS
            |
            ;
DEC_TAIL    : "." DEC_DIGITS
            ;
HEX_DIGIT   : "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"
            | "a" | "b" | "c" | "d" | "e" | "f" | "g" | "h" | "i" | "j" | "k" | "l" | "m" | "n" | "o" | "p" | "q" | "r" | "s" | "t" | "u" | "v" | "w" | "x" | "y" | "z"
            | "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H" | "I" | "J" | "K" | "L" | "M" | "N" | "O" | "P" | "Q" | "R" | "S" | "T" | "U" | "V" | "W" | "X" | "Y" | "Z"
            ;
HEX_DIGIT_  : UNDERSCORE HEX_DIGIT
            ;
fragment
HEX_DIGITS_ : HEX_DIGIT_ HEX_DIGITS_
            |
            ;
fragment
HEX_DIGITS  : HEX_DIGIT HEX_DIGITS
            |
            ;
HEX_TAIL    : "." HEX_DIGITS
            ;
     */
    Digits(int firstDigit, boolean allowDot, boolean negative)
    {
      _negative = negative;
      var digits = new StringBuilder();
      int c1 = curCodePoint();
      int firstGroupSize = -1;
      int groupSize = -1;
      int currentGroupSize = 0;
      var b =  firstDigit != '0' ? null :
        switch (c1)
          {
          case 'b' -> Base.bin;
          case 'o' -> Base.oct;
          case 'd' -> Base.dec;
          case 'x' -> Base.hex;
          default  -> null;
          };
      if (b == null)
        {
          _base = Base.dec;
          checkAndAppendDigit(digits, firstDigit);
        }
      else
        {
          _base = b;
          nextCodePoint();
          if (curCodePoint() == '_')
            {
              firstGroupSize = 0;
              nextCodePoint();
            }
        }
      var d = curCodePoint();
      var start = sourcePos();
      var end = false;
      while (isDigit(d) || d == '_' || !end)
        {
          if (isDigit(d) && d != '_')
            {
              currentGroupSize = currentGroupSize + 1;
              checkAndAppendDigit(digits, d);
            }
          else
            {
              end = d != '_';
              if (firstGroupSize < 0)
                {
                  firstGroupSize = currentGroupSize;
                }
              else if (currentGroupSize == 0 && !_hasError)
                {
                  Errors.error(sourcePos(),
                               "Broken numeric literal, repeated '_' are not allowed",
                               null);
                  _hasError = true;
                }
              else if (groupSize < 0)
                {
                  if (currentGroupSize < 2 && !_hasError)
                    {
                      Errors.error(sourcePos(),
                                   "Broken numeric literal, grouping fewer than two digits is not allowed",
                                   null);
                      _hasError = true;
                    }
                  groupSize = currentGroupSize;
                  if (firstGroupSize > groupSize && !_hasError)
                    {
                      Errors.error(sourcePos(),
                                   "Broken numeric literal, inconsistent grouping of digits.",
                                   "First group has " + firstGroupSize + " digits, while next group has " + groupSize + " digits.");
                      _hasError = true;
                    }
                }
              else if (groupSize != currentGroupSize && !_hasError)
                {
                  Errors.error(sourcePos(),
                               "Broken numeric literal, inconsistent grouping of digits.",
                               "Previous group has " + groupSize + " digits, while later group has "+currentGroupSize+" digits.");
                  _hasError = true;
                }
              currentGroupSize = 0;
            }
          if (!end)
            {
              nextCodePoint();
              d = curCodePoint();
            }
        }
      if (allowDot && curCodePoint() == '.')
        {
          var f = new Lexer(Lexer.this);
          f.nextCodePoint();
          var fd = f.curCodePoint();
          if (isDigit(fd))
            {
              nextCodePoint();
              d = curCodePoint();
              while (isDigit(d))
                {
                  checkAndAppendDigit(digits, d);
                  _dotAt++;
                  nextCodePoint();
                  d = curCodePoint();
                }
            }
        }

      if (!_hasError && digits.isEmpty())
        {
          Errors.error(sourcePos(),
                       "Broken " + _base._name + "literal, expected digits after '0" + (char) c1 + "'.",
                       null);
          _hasError = true;
        }
      _digits = digits.toString();
    }

    void checkAndAppendDigit(StringBuilder digits, int d)
    {
      var v =
        ('0' <= d && d <= '9') ? d - (int) '0' :
        ('a' <= d && d <= 'z') ? d - (int) 'a' + 10 :
        ('A' <= d && d <= 'Z') ? d - (int) 'A' + 10 : Integer.MAX_VALUE;
      if (v >= _base._base)
        {
          Errors.error(sourcePos(),
                       "Invalid digit '" + Character.toString(d) + "' for base " + _base._base + ".",
                       null);
          d = '0';
        }
      digits.appendCodePoint(d);
    }


    /**
     * The value, ignoring '-' and ingoring decimal '.' position (i.e., value of '123.456' is
     * 123456).
     */
    BigInteger absValue()
    {
      return new BigInteger(_digits, _base._base);
    }

    /**
     * The value, ignoring decimal '.' position (i.e., value of '123.456' is
     * 123456).
     */
    BigInteger signedValue()
    {
      var res = absValue();
      return _negative ? res.negate() : res;
    }

  }


  /**
   * Skip comments of the form '/''*' .. '*''/', skip them recursively if nested
   * comments are found.  Called with the current code point at the first '*'.
   */
  private Token skipComment()
  {
    int nestedStartPos = -1, nestedEndPos = -1;
    int startPos = _curPos;
    int p = curCodePoint();

    if (CHECKS) check
      (p == '*');

    nextCodePoint();
    boolean gotStar = false;
    boolean done = false;
    do
      {
        int last = p;
        p = curCodePoint();
        if (isNewLine(last, p))
          {
            _curLine++;
          }
        if (p == SourceFile.END_OF_FILE)
          {
            Errors.error(sourcePos(startPos),
                         "Unterminated comment",
                         "Multi-line comment started with '/*' was not properly terminated with '*/'.\n" +
                         (nestedStartPos >= 0
                          ? ("Comment contains nested comment starting at " + sourcePos(nestedStartPos).show() + "\n" +
                             "ended at " + sourcePos(nestedEndPos).show())
                          : ""));
            done = true;
          }
        else
          {
            nextCodePoint();
            if (p == '/')
              {
                if (gotStar)
                  {
                    done = true;
                  }
                else if (curCodePoint() == '*')
                  {
                    if (nestedStartPos < 0)
                      {
                        nestedStartPos = bytePos() - 1;
                      }
                    skipComment();
                    if (nestedEndPos < 0)
                      {
                        nestedEndPos = bytePos() - 1;
                      }
                  }
              }
            gotStar = (p == '*');
          }
      }
    while (!done);
    return Token.t_comment;
  }


  /**
   * Advance to the next line.
   *
   * @return Token.t_comment
   */
  private Token skipUntilEOL()
  {
    int p = curCodePoint();
    if (p != SourceFile.END_OF_FILE)
      {
        int l;
        do
          {
            nextCodePoint();
            l = p;
            p = curCodePoint();
          }
        while (p != SourceFile.END_OF_FILE && !isNewLine(l, p));
        _curLine++;
      }
    return Token.t_comment;
  }


  /**
   * Advance to the first codePoint after an Operator.  An Operator consists of
   * codePoints of kind K_OP, K_SHARP or K_SLASH.
   *
   * @return Token.t_op
   */
  private Token skipOp(Token res)
  {
    int p = curCodePoint();
    while (kind(p) == K_OP || kind(p) == K_SHARP || kind(p) == K_SLASH)
      {
        res = Token.t_op;
        nextCodePoint();
        p = curCodePoint();
      }
    return res;
  }


  /**
   * Produce a syntax error at the given position.
   *
   * @param pos the byte offset of the error
   *
   * @param expected the expected tokens
   *
   * @param currentRule the current rule we are trying to parse
   */
  void syntaxError(int pos, String expected, String currentRule)
  {
    String detail = Parser.parserDetail(currentRule);
    if (current() == Token.t_indentationLimit)
      {
        Errors.indentationProblemEncountered(sourcePos(pos),
                                             sourcePos(_minIndentStartPos),
                                             detail);
      }
    else if (current() == Token.t_lineLimit)
      {
        Errors.lineBreakNotAllowedHere(sourcePos(lineEndPos(_sameLine)), detail);
      }
    else if (current() == Token.t_spaceLimit)
      {
        Errors.whiteSpaceNotAllowedHere(sourcePos(pos()), detail);
      }
    else
      {
        Errors.syntax(sourcePos(pos), expected, currentAsString(), detail);
      }
  }


  /**
   * Produce a syntax error at the current token's position.
   *
   * @param pos the byte offset of the error
   *
   * @param expected the expected tokens
   *
   * @param currentRule the current rule we are trying to parse
   */
  void syntaxError(String expected, String currentRule)
  {
    syntaxError(pos(), expected, currentRule);
  }


  /**
   * Match the current token with the given token. If the token matches and t !=
   * Token.t_eof, advance to the next token using next(). Otherwise, cause a
   * syntax error.
   *
   * @param t the token we want to see
   *
   * @param currentRule the current rule we are trying to parse
   */
  void match(Token t, String currentRule)
  {
    match(false, t, currentRule);
  }


  /**
   * Match the current token, which might be at the indentation limit, with the
   * given token. If the token matches and t != Token.t_eof, advance to the next
   * token using next(). Otherwise, cause a syntax error.
   *
   * @param t the token we want to see
   *
   * @param currentRule the current rule we are trying to parse
   */
  void matchAtMinIndent(Token t, String currentRule)
  {
    match(true, t, currentRule);
  }


  /**
   * Match the current token, obtained via currentAtMinIndent() or
   * current() depending on atMinIndent, with the given token. If the
   * token matches and t != Token.t_eof, advance to the next token using
   * next(). Otherwise, cause a syntax error.
   *
   * @param t the token we want to see
   *
   * @param currentRule the current rule we are trying to parse
   */
  void match(boolean atMinIndent, Token t, String currentRule)
  {
    if (current(atMinIndent) == t)
      {
        if (t != Token.t_eof)
          {
            next();
          }
      }
    else
      {
        syntaxError(t.toString(), currentRule);
      }
  }


  /**
   * Match the current token, obtained via currentAtMinIndent() or
   * current() depending on atMinIndent, with the given token.
   *
   * @param t the token we want to see
   *
   * @return true iff curent token matches
   */
  boolean currentMatches(boolean atMinIndent, TokenOrOp to)
  {
    return current(atMinIndent) == to._token &&
      (to._token != Token.t_op || tokenAsString().equals(to._detail));
  }


  /**
   * Match the current token, obtained via currentAtMinIndent() or
   * current() depending on atMinIndent, with the given token. If the
   * token matches and t != Token.t_eof, advance to the next token using
   * next(). Otherwise, cause a syntax error.
   *
   * @param t the token we want to see
   *
   * @param currentRule the current rule we are trying to parse
   */
  void match(boolean atMinIndent, TokenOrOp to, String currentRule)
  {
    if (currentMatches(atMinIndent, to))
      {
        if (to._token != Token.t_eof)
          {
            next();
          }
      }
    else
      {
        syntaxError(to.toString(), currentRule);
      }
  }


  /**
   * Match the current token with the given operator, i.e, check that current()
   * is Token.t_op and the operator is op.  If so, advance to the next token
   * using next(). Otherwise, cause a syntax error.
   *
COLON       : ":"
            ;

ARROW       : "=>"
            ;

PIPE        : "|"
            ;
   *
   * @param op the operator we want to see
   *
   * @param currentRule the current rule we are trying to parse
   */
  void matchOperator(String op, String currentRule)
  {
    if (isOperator(op))
      {
        next();
      }
    else
      {
        syntaxError("operator '" + op + "'", currentRule);
      }
  }


  /**
   * Check if the current token is the given single-code point operator, i.e,
   * check that current() is Token.t_op and the operator is op.
   *
   * @param op the operator we want to see
   *
   * @return true iff the current token is the given operator.
   */
  boolean isOperator(int codePoint)
  {
    return
      current() == Token.t_op &&
      codePointAt(_curPos) == codePoint &&
      endPos() - pos() == 1;
  }


  /**
   * Check if the current token is the given operator, i.e, check that current()
   * is Token.t_op and the operator is op.
   *
   * @param op the operator we want to see
   *
   * @return true iff the current token is the given operator.
   */
  boolean isOperator(String op)
  {
    return
      current() == Token.t_op &&
      operator().equals(op);
  }


  /**
   * Check if the current operator is an operator that starts with the given
   * string.  If so, split the current token into an operator op and the
   * remaining string.
   *
   * This is useful, e.g., to parse nested generics lists such as
   * Stack<List<i32>>, where the last token is seen by the lexer as a single
   * operator >>, while the parser prefers to see two consecutive operators >.
   */
  void splitOperator(String op)
  {
    if (current() == Token.t_op && operator().startsWith(op))
      {
        setPos(_curPos);
        for (int i = 0; i < op.length(); i++)
          {
            if (CHECKS) check
              (op.charAt(i) == curCodePoint());
            nextCodePoint();
          }
        if (CHECKS) check
          (isOperator(op));
      }
  }


  /**
   * Return the actual operator of the current t_op token as a string.
   */
  String operator()
  {
    if (PRECONDITIONS) require
      (current() == Token.t_op);

    return tokenAsString();
  }


  /**
   * In case the current token is Token.t_op, return the operator, otherwise
   * return Errors.ERROR_STRING.
   */
  String operatorOrError()
  {
    String result = Errors.ERROR_STRING;

    if (current() == Token.t_op)
      {
        result = operator();
      }

    return result;
  }


  /**
   * Return the actual identifier of the current t_ident token as a string.
   */
  String identifier(boolean mayBeAtMinIndent)
  {
    if (PRECONDITIONS) require
      (current(mayBeAtMinIndent) == Token.t_ident);

    return tokenAsString();
  }


  /**
   * Return the actual identifier of the current t_ident token as a string.
   */
  String identifier()
  {
    if (PRECONDITIONS) require
      (current() == Token.t_ident);

    return identifier(false);
  }


  /**
   * In case the current token is Token.t_ident, return the identifier, otherwise
   * return Errors.ERROR_STRING.
   */
  String identifierOrError()
  {
    String result = Errors.ERROR_STRING;

    if (current() == Token.t_ident)
      {
        result = identifier();
      }

    return result;
  }


  /**
   * Return an object with the details of the current t_numliteral token.
   */
  Literal curLiteral()
  {
    if (PRECONDITIONS) require
      (current() == Token.t_numliteral);

    return _curLiteral;
  }


  /**
   * Parse state to decide between normal parsing and $<id> and {<expr>} within
   * strings.
   */
  private enum StringState
  {
    IDENT_EXPECTED,   // parsing an identifier in a string as in "xyz is $xyz."
    EXPR_EXPECTED,    // parsing an expression in a string as in "expr is {q.f(fun f(x i32) -> { x * 5 })}."
    CONTINUED,        // parsing the string following an identifier in a string as in "abc+$x-def" when parsing -def"
  }


  /**
   * For a partial string, the class of the beginning or the end of the string.
   */
  public enum StringEnd
  {
    QUOTE,   // A normal string starting with '"' as in "normal string...
    DOLLAR,  // Following '$<id>' as " dollar string..." in "previous $ident dollar string...
    BRACE;   // Following '{<expr>}' as " rbrace string..." in "previous {a+b} rbrace string...

    /**
     * Get the partial string token for a string starting with this and ending with end.
     */
    Token token(StringEnd end)
    {
      if      (this == StringEnd.QUOTE  && end == StringEnd.QUOTE ) { return Token.t_stringQQ; }
      else if (this == StringEnd.QUOTE  && end == StringEnd.DOLLAR) { return Token.t_stringQD; }
      else if (this == StringEnd.QUOTE  && end == StringEnd.BRACE ) { return Token.t_stringQB; }
      else if (this == StringEnd.DOLLAR && end == StringEnd.QUOTE ) { return Token.t_StringDQ; }
      else if (this == StringEnd.DOLLAR && end == StringEnd.DOLLAR) { return Token.t_StringDD; }
      else if (this == StringEnd.DOLLAR && end == StringEnd.BRACE ) { return Token.t_StringDB; }
      else if (this == StringEnd.BRACE  && end == StringEnd.QUOTE ) { return Token.t_stringBQ; }
      else if (this == StringEnd.BRACE  && end == StringEnd.DOLLAR) { return Token.t_stringBD; }
      else if (this == StringEnd.BRACE  && end == StringEnd.BRACE ) { return Token.t_stringBB; }
      throw new Error("impossible StringEnd.token combination "+this+" and "+end);
    }
  }


  /**
   * For a given string token, return if that string starts with '"' or follows
   * an embedded '$<id>' or '{<expr>}'.
   */
  StringEnd beginning(Token t)
  {
    if (PRECONDITIONS) require
      (isString(t));

    switch (t)
      {
      case t_stringQQ:
      case t_stringQD:
      case t_stringQB: return StringEnd.QUOTE;
      case t_StringDQ:
      case t_StringDD:
      case t_StringDB: return StringEnd.DOLLAR;
      case t_stringBQ:
      case t_stringBD:
      case t_stringBB: return StringEnd.BRACE;
      default        : throw new Error();

      }
  }


  /**
   * For a given string token, return if that string ends with '"' or with an
   * embedded '$<id>' or '{<expr>}'.
   */
  StringEnd end(Token t)
  {
    if (PRECONDITIONS) require
      (isString(t));

    switch (t)
      {
      case t_stringQQ:
      case t_StringDQ:
      case t_stringBQ: return StringEnd.QUOTE;
      case t_stringQD:
      case t_StringDD:
      case t_stringBD: return StringEnd.DOLLAR;
      case t_stringQB:
      case t_StringDB:
      case t_stringBB: return StringEnd.BRACE;
      default        : throw new Error();

      }
  }


  /**
   * Small state-machine parser for constant strings.
   */
  private class StringLexer
  {
    /**
     * The original string that started with '"', i.e., disregarding any partial
     * strings following '$<id>' or '{<expr>}'.  Used for proper error messages.
     */
    final int _stringStart;


    /**
     * -1 if this string is being read from the underlying SourceFile directly.
     * Otherwise, the character start position in the underlying source file.
     */
    final int _pos;

    int _braceCount;

    /**
     * In case of nested string lexers, this is the outer lexer.
     */
    StringLexer _outer;

    /**
     * One of t_stringQQ. t_stringQD or t_stringQB to identify the
     * beginning of this partial string.
     */
    StringEnd _beginning;

    /**
     * Parsing state for identifiers and expressions embedded in strings.
     */
    private StringState _state;


    char[][] escapeChars = new char[][] {
        { 'b', '\b'  },  // BS 0x08
        { 't', '\t'  },  // HT 0x09
        { 'n', '\n'  },  // LF 0x0a
        { 'f', '\f'  },  // FF 0x0c
        { 'r', '\r'  },  // CR 0x0d
        { '\"', '\"' },  // "  0x22
        { '$',  '$'  },  // $  0x24
        { '\'', '\'' },  // '  0x27
        { '\\', '\\' },  // \  0x5c
        { '{',  '{'  },  // {  0x7b
        { '}',  '}'  },  // }  0x7d
      };


    /**
     * Create a string lexer at the start of lexing a string.  The first token
     * will be returned through this.finish().  Lexer.this._stringLexer will be
     * set to the new StringLexer instance if more tokens related to this string
     * will come.
     */
    StringLexer()
    {
      _stringStart = Lexer.this.pos();
      _pos = -1;
      _beginning = StringEnd.QUOTE;
    }


    /**
     * Create a StringLexer at the position of the current string token.  This
     * is used by string() to retrieve the actual string contents after the
     * lexing step has finished.
     *
     * @param sb a StringBuilder to receive the code points of this string.
     */
    StringLexer(StringBuilder sb)
    {
      if (PRECONDITIONS) require
        (isString(current()));

      _stringStart = Lexer.this.pos();
      _pos =  Lexer.this.pos() + (beginning(current()) == StringEnd.DOLLAR ? 0 : 1);
      _beginning = StringEnd.QUOTE;
      iterateCodePoints(sb);
    }


    /**
     * Create a clone of this StringLexer, used for cloning the surrounding Lexer.
     */
    StringLexer(StringLexer original)
    {
      if (PRECONDITIONS) require
        (original._pos == -1  /* should happen only during lexing, not when retrieving via string() */);

      this._stringStart = original._stringStart;
      this._pos = original._pos;
      this._braceCount = original._braceCount;
      this._beginning = original._beginning;
      this._state = original._state;
      this._outer = original._outer == null ? null : new StringLexer(original._outer);
    }


    /**
     * Return the current raw code point, not processing any escapes.
     */
    private int raw(int pos)
    {
      return pos < 0 ? curCodePoint() : codePointAt(pos);
    }


    /**
     * Iterate over code points and append them to sb
     */
    private Token iterateCodePoints(StringBuilder sb)
    {
      var t = Token.t_undefined;
      var pos = _pos;

      var escaped = false;
      while (t == Token.t_undefined)
        {
          var p = raw(pos);
          var c = -1;
          if (p == END_OF_FILE)
            {
              Errors.unterminatedString(sourcePos(), Lexer.this.sourcePos(_stringStart));
              t = Token.t_error;
            }
          else if (p < _asciiControlName.length && _asciiControlName[p] != null)
            {
              Errors.unexpectedControlCodeInString(sourcePos(), _asciiControlName[p], p, sourcePos(_stringStart));
              t = Token.t_error;
            }
          else
            {
              if (escaped)
                {
                  for (var i = 0; i < escapeChars.length && c < 0; i++)
                    {
                      if (p == (int) escapeChars[i][0])
                        {
                          c = (int) escapeChars[i][1];
                        }
                    }
                  if (c < 0)
                    {
                      Errors.unknownEscapedChar(sourcePos(), p, escapeChars);
                    }
                  escaped = false;
                }
              else if (p == '\\')
                {
                  escaped = true;
                }
              else if (p == '"') { t = _beginning.token(StringEnd.QUOTE);  }
              else if (p == '$') { t = _beginning.token(StringEnd.DOLLAR); }
              else if (p == '{') { t = _beginning.token(StringEnd.BRACE);  }
              else
                {
                  c = p;
                }
              var l = p;
              if (pos < 0)
                {
                  nextCodePoint();
                }
              else
                {
                  pos = pos + codePointSize(pos);
                }
              p = raw(pos);
              if (isNewLine(l, p))
                {
                  Errors.unexpectedEndOfLineInString(sourcePos(bytePos()-1), sourcePos(_stringStart));
                  t = Token.t_error;
                }
            }
          if (c >= 0 && sb != null)
            {
              sb.appendCodePoint(c);
            }
        }
      return t;
    }


    /**
     * This StringLexer's implementation of Lexer.nextRaw() to get the next raw token.
     *
     * @return the next raw token or Token.t_undefined to delegate scanning the
     * next token to Lexer.nextRaw().
     */
    Token nextRaw()
    {
      if (CHECKS) check
        (_stringLexer == this,
         _pos == -1);

      int p = curCodePoint();
      switch (_state)
        {
        case EXPR_EXPECTED:
          switch (kind(p))
            {
            case K_LBRACE:
              _braceCount++;
              return Token.t_lbrace;
            case K_RBRACE:
              _braceCount--;
              if (_stringLexer._braceCount > 0)
                {
                  return Token.t_rbrace;
                }
              _beginning = StringEnd.BRACE;
              break;
            default:
              return Token.t_undefined;
            }
          break;
        case IDENT_EXPECTED:
          {
            if (kind(p) != K_LETTER && kind(p) != K_DIGIT)
              {
                Errors.identifierInStringExpected(sourcePos(), sourcePos(_stringLexer._stringStart));
              }
            _state = StringState.CONTINUED;
            return Token.t_undefined;
          }
        case CONTINUED:
          {
            _beginning = StringEnd.DOLLAR;
          }
        }
      // pop this from the stack of string lexers:
      _stringLexer = _outer;
      return finish();
    }


    /**
     * iterate over the chars of this (partial) string and determine its token
     * type.  In case the string is not finished, update this string lexer's
     * state and push it to the stack of string lexers.
     *
     * @return the string token of the parsed (partial) string.
     */
    Token finish()
    {
      var t = iterateCodePoints(null);
      if (isPartialString(t))
        {
          // push this onto the stack of string lexers:
          _outer = _stringLexer;
          _stringLexer = this;
          switch (end(t))
            {
            case DOLLAR: _state = StringState.IDENT_EXPECTED; break;
            case BRACE : _state = StringState.EXPR_EXPECTED; break;
            default    : throw new Error("default:");
            }
        }
      return t;
    }

  }


  /**
   * Is the given token a constant string, i.e., any of the t_string* variants
   * of constant strings.
   *
   * @param t a token
   *
   * @return true iff t is a string
   */
  public static boolean isString(Token t)
  {
    switch (t)
      {
      case t_stringQQ:
      case t_stringQD:
      case t_stringQB:
      case t_StringDQ:
      case t_StringDD:
      case t_StringDB:
      case t_stringBQ:
      case t_stringBD:
      case t_stringBB: return true;
      default        : return false;
      }
  }


  /**
   * Is the given token a constant string that is started, i.e., it is not
   * preceded by an embedded identifier '$id' or expression '{expr}.
   *
   * @param t a token
   *
   * @return true iff t is a started string
   */
  boolean isStartedString(Token t)
  {
    return isString(t) && beginning(t) == StringEnd.QUOTE;
  }


  /**
   * Is the given token a constant string that is completed, i.e., it is not
   * followed by an embedded identifier '$id' or expression '{expr}.
   *
   * @param t a token
   *
   * @return true iff t is a completed string
   */
  boolean isCompletedString(Token t)
  {
    return isString(t) && end(t) == StringEnd.QUOTE;
  }


  /**
   * Is the given token a constant string that is followed by an embedded
   * identifier or expression, i.e., any of the t_string*D or
   * t_string*B variants of constant strings.
   *
   * @param t a token
   *
   * @return true iff t is a partial string
   */
  boolean isPartialString(Token t)
  {
    return isString(t) && !isCompletedString(t);
  }


  /**
   * Is the given token a constant string that is following an embedded
   * identifier or expression, i.e., any of the t_stringD* or t_stringB*
   * variants of constant strings.
   *
   * @param t a token
   *
   * @return true iff t is continuing a partial string
   */
  boolean isContinuedString(Token t)
  {
    return isString(t) && beginning(t) != StringEnd.QUOTE;
  }


  /**
   * Return the actual string constant of the current t_string* token as a
   * string.
   */
  String string()
  {
    if (PRECONDITIONS) require
      (isString(current()));

    var sb = new StringBuilder();
    var s = new StringLexer(sb);
    return sb.toString();
  }


  /**
   * Return the current token as a string as it appeared in the source code.
   */
  private String tokenAsString()
  {
    if (PRECONDITIONS) require
      (current() != Token.t_eof);

    return asString(pos(), endPos());
  }


  /**
   * The current token as a string for debugging purposes.
   */
  public String currentAsString()
  {
    var t = current();
    var result = t.toString();
    switch (t)
      {
      case t_op        :
      case t_numliteral:
      case t_ident     : result = result + " '" + tokenAsString() + "'"; break;
      default          :
        if (isString(t))
          {
            result = result + " '" + tokenAsString() + "'";
          }
        else if (t.isKeyword())
          {
            result = "keyword '" + result + "'";
          }
        break;
      }
    return result;
  }


  /**
   * Parse "(" if it is found
   *
   * @return true iff a "(" was found and skipped.
   */
  boolean skipLParen()
  {
    return skip(Token.t_lparen);
  }


  /**
   * Parse given token and skip it. if it was found.
   *
   * @param t a token.
   *
   * @return true iff the current token was t and was skipped, otherwise no
   * change is made.
   */
  boolean skip(Token t)
  {
    boolean result = false;
    if (current() == t)
      {
        next();
        result = true;
      }
    return result;
  }


  /**
   * Parse given token and skip it. if it was found.
   *
   * @param t a token.
   *
   * @return true iff the current token was t and was skipped, otherwise no
   * change is made.
   */
  boolean skip(boolean atMinIndent, Token t)
  {
    boolean result = false;
    if (current(atMinIndent) == t)
      {
        next();
        result = true;
      }
    return result;
  }


  /**
   * Parse given token and skip it. if it was found.
   *
   * @param t a token.
   *
   * @return true iff the current token was t and was skipped, otherwise no
   * change is made.
   */
  boolean skip(boolean atMinIndent, TokenOrOp t)
  {
    boolean result = false;
    if (currentMatches(atMinIndent, t))
      {
        next();
        result = true;
      }
    return result;
  }


  /**
   * Parse singe-char t_op.
   *
STAR        : "*"
            ;
QUESTION    : "?"
            ;
   *
   * @param op the single-char operator
   *
   * @return true iff an t_op op was found and skipped.
   */
  boolean skip(char op)
  {
    boolean result = false;
    if (isOperator(op))
      {
        next();
        result = true;
      }
    return result;
  }


  /**
   * Parse specific t_op.
   *
   * @param op the operator
   *
   * @return true iff an t_op op was found and skipped.
   */
  boolean skip(String op)
  {
    boolean result = false;
    if (isOperator(op))
      {
        next();
        result = true;
      }
    return result;
  }


  /**
   * Parse specific t_op after splitting it off from the current op.
   *
   * @param op the operator
   *
   * @return true iff an t_op op was found and skipped.
   */
  boolean splitSkip(String op)
  {
    boolean result = false;
    splitOperator(op);
    if (isOperator(op))
      {
        next();
        result = true;
      }
    return result;
  }


  /**
   * Return the details of the numeric literal of the current t_numliteral token.
   */
  Literal skipNumLiteral()
  {
    if (PRECONDITIONS) require
      (current() == Token.t_numliteral);

    var result = curLiteral();
    next();
    return result;
  }


}

/* end of file */
