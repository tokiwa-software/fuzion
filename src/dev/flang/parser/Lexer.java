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
 * Tokiwa GmbH, Berlin
 *
 * Source of class Lexer
 *
 *---------------------------------------------------------------------*/

package dev.flang.parser;

import java.nio.file.Path;
import java.util.stream.Stream;

import dev.flang.util.Errors;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;


/**
 * Lexer performs the lexical analysis of Fusion source code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class Lexer extends SourceFile
{


  /*----------------------------  constants  ----------------------------*/

  /**
   * Tokens produced by the lexer
   */
  static enum Token
  {
    t_skip,       // whitespace or comment
    t_op,         // operators +, -, *, /, ., |, etc.
    t_comma,      // ,
    t_lparen,     // (
    t_rparen,     // )
    t_lbrace,     // {
    t_rbrace,     // }
    t_lcrochet,   // [
    t_rcrochet,   // ]
    t_semicolon,  // ;
    t_integer,    // 123
    t_ident,      // abc
    t_string,     // "abc"
    t_this("this"),
    t_check("check"),
    t_else("else"),
    t_if("if"),
    t_is("is"),
    t_abstract("abstract"),
    t_intrinsic("intrinsic"),
    t_for("for"),
    t_in("in"),
    t_do("do"),
    t_loop("loop"),
    t_while("while"),
    t_until("until"),
    t_require("require"),
    t_ensure("ensure"),
    t_invariant("invariant"),
    t_variant("variant"),
    t_pre("pre"),
    t_post("post"),
    t_inv("inv"),
    t_var("var"),
    t_old("old"),
    t_match("match"),
    t_fun("fun"),
    t_single("single"),
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
    t_eof,               // end of file
    t_indentationLimit,  // token's indentation is not sufficient
    t_lineLimit,         // token is in next line while sameLine() parsing is enabled
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
    boolean isKeyword()
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
    static Token[] _keywords = Stream.of(Token.class.getEnumConstants())
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
            case t_op       : result = "operator"             ; break;
            case t_comma    : result = "comma ','"            ; break;
            case t_lparen   : result = "left parenthesis '('" ; break;
            case t_rparen   : result = "right parenthesis ')'"; break;
            case t_lbrace   : result = "left curly brace '{'" ; break;
            case t_rbrace   : result = "right curly brace '}'"; break;
            case t_lcrochet : result = "left crochet '['"     ; break;
            case t_rcrochet : result = "right crochet ']'"    ; break;
            case t_semicolon: result = "semicolon ';'"        ; break;
            case t_integer  : result = "integer constant"     ; break;
            case t_ident    : result = "identifier"           ; break;
            case t_string   : result = "string constant"      ; break;
            case t_eof      : result = "end-of-file"          ; break;
            default         : result = super.toString()       ; break;
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


  /*----------------------------  variables  ----------------------------*/


  /**
   * The current token
   */
  private Token _curToken = Token.t_undefined;


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
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Set the minimun indentation to the position of startPos.  The token at
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
   * Advance to the next token that is not Token.t_skip.
   */
  void next()
  {
    _lastPos = _curPos;
    do
      {
        nextRaw();
      }
    while (currentNoLimit() == Token.t_skip);
  }


  /**
   * The current token.  If minIndent >= 0 and the current token is not indented
   * deeper than this limit, return Token.t_indentationLimit.
   */
  Token current(int minIndent, int sameLine)
  {
    var t = _curToken;
    int l = _curLine;
    int p = _curPos;
    return
      t == Token.t_eof                                     ? t                        :
      sameLine  >= 0 && l != sameLine                      ? Token.t_lineLimit        :
      p == _minIndentStartPos                              ? t                        :
      minIndent >= 0 && codePointInLine(p, l) <= minIndent ? Token.t_indentationLimit
                                                           : _curToken;
  }


  /**
   * The current token.  If indentation limit was set and the current token is
   * not indented deeper than this limit, return Token.t_indentationLimit.
   */
  Token current()
  {
    return current(_minIndent, _sameLine);
  }


  /**
   * The current token.  If indentation limit was set and the current token is
   * indented less than this limit, return Token.t_indentationLimit.
   */
  Token currentAtMinIndent()
  {
    return current(_minIndent - 1, _sameLine);
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
    return current(-1, -1);
  }


  /**
   * The byte position in the source file.
   */
  int pos()
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
   * Advance to the next token. The next token might be Token.t_skip, i.e, white
   * space or a comment.
   */
  void nextRaw()
  {
    _curPos = bytePos();
    int p = curCodePoint();
    Token token;
    if (p == SourceFile.END_OF_FILE)
      {
        token = Token.t_eof;
      }
    else
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
                           "Unknown code point " + Integer.toHexString(p) + " is not permitted by Fusion's grammar.");
              token = Token.t_skip;
              break;
            }
          case K_SHARP   :   // '#'
            {
              token = codePointInLine(_curPos) == 1 ? skipUntilEOL() // comment until end of line
                                                    : skipOp();
              break;
            }
          case K_OP      :   // '+'|'-'|'*'|'%'|'|'|'~'|'#'|'!'|'$'|'&'|'@'|':'|'<'|'>'|'='|'^'|'.')+;
            {
              token = skipOp();
              break;
            }
          case K_WS      :   // spaces, tabs, lf, cr, ...
            {
              int last = p;
              p = curCodePoint();
              if (isNewLine(last,p))
                {
                  _curLine++;
                }
              while (kind(p) == K_WS)
                {
                  nextCodePoint();
                  last = p;
                  p = curCodePoint();
                  if (isNewLine(last,p))
                    {
                      _curLine++;
                    }
                }
              token = Token.t_skip;
              break;
            }
          case K_SLASH   :   // '/', introducing a comment or an operator.
            {
              p = curCodePoint();
              token = kind(p) == K_SLASH ? skipUntilEOL() : // comment until end of line
                      p == '*'           ? skipComment()
                                         : skipOp();
              break;
            }
          case K_COMMA   :   // ','
            {
              token = Token.t_comma;
              break;
            }
          case K_LPAREN  :    // '('  round brackets or parentheses
            {
              token = Token.t_lparen;
              break;
            }
          case K_RPAREN  :    // ')'
            {
              token = Token.t_rparen;
              break;
            }
          case K_LBRACE  :    // '{'  curly brackets or braces
            {
              token = Token.t_lbrace;
              break;
            }
          case K_RBRACE  :    // '}'
            {
              token = Token.t_rbrace;
              break;
            }
          case K_LCROCH  :    // '['  square brackets or crochets
            {
              token = Token.t_lcrochet;
              break;
            }
          case K_RCROCH  :    // ']'
            {
              token = Token.t_rcrochet;
              break;
            }
          case K_SEMI    :    // ';'
            {
              token = Token.t_semicolon;
              break;
            }
          case K_DIGIT   :    // '0'..'9'
            {
              while (kind(curCodePoint()) == K_DIGIT)
                {
                  nextCodePoint();
                }
              token = Token.t_integer;
              break;
            }
          case K_LETTER  :    // 'A'..'Z', 'a'..'z'
            {
              while (kind(curCodePoint()) == K_LETTER  ||
                     kind(curCodePoint()) == K_DIGIT   ||
                     kind(curCodePoint()) == K_NUMERIC   )
                {
                  nextCodePoint();
                }
              token = findKeyword();
              break;
            }
          case K_DQUOTE  :    // '"'
            {
              boolean end = false;
              while (!end && kind(curCodePoint()) != K_EOF)
                {
                  end = kind(curCodePoint()) == K_DQUOTE;
                  nextCodePoint();
                }
              token = Token.t_string;
              break;
            }
          case K_ERROR   :    // an error occurred
            {
              token = Token.t_skip;
              break;
            }
          default:
            {
              throw new Error("unexpected character kind: "+kind(p));
            }
          }
      }
    _curToken = token;
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
        if (p == '°' ||
            p >= 0x02190 && p <= 0x021ff ||  // Arrows https://www.unicode.org/charts/PDF/U2190.pdf
            p >= 0x02200 && p <= 0x022ff ||  // Mathematical Operators, see https://www.unicode.org/charts/PDF/U2200.pdf
            p >= 0x027f0 && p <= 0x027ff ||  // Supplemental Arrows-A https://www.unicode.org/charts/PDF/U27F0.pdf
            p >= 0x02900 && p <= 0x0297f ||  // Supplemental Arrows-B https://www.unicode.org/charts/PDF/U2900.pdf
            p >= 0x02b00 && p <= 0x02bff &&  // Miscellaneous Symbols and Arrows https://www.unicode.org/charts/PDF/U2B00.pdf
            p != 0x02b74 &&                     // Miscellaneous Symbols and Arrows, reserved
            p != 0x02b75 &&                     // Miscellaneous Symbols and Arrows, reserved
            p != 0x02b96 ||                     // Miscellaneous Symbols and Arrows, undefined
            // 0x1f800 .. 0x1f8ff, many holes   // Supplemental Arrows-C https://www.unicode.org/charts/PDF/U1F800.pdf
            false
            )
          { // Maybe symbol kinds Sm and So would be good, see https://www.unicode.org/Public/UCD/latest/ucd/UnicodeData.txt
            kind = K_OP;
          }
        else if (p >= 0x1d400 && p <= 0x1d7ff && // Mathematical Alphanumeric Symbols https://www.unicode.org/charts/PDF/U1D400.pdf
                 p != 0x1d455 &&
                 p != 0x1d49d &&
                 p != 0x1d4a0 &&
                 p != 0x1d4a1 &&
                 p != 0x1d4a3 &&
                 p != 0x1d4a4 &&
                 p != 0x1d4a7 &&
                 p != 0x1d4a8 &&
                 p != 0x1d4ad &&
                 p != 0x1d4ba &&
                 p != 0x1d4bc &&
                 p != 0x1d4c4 &&
                 p != 0x1d506 &&
                 p != 0x1d50b &&
                 p != 0x1d50c &&
                 p != 0x1d515 &&
                 p != 0x1d51d &&
                 p != 0x1d53a &&
                 p != 0x1d53f &&
                 p != 0x1d545 &&
                 p != 0x1d547 &&
                 p != 0x1d548 &&
                 p != 0x1d549 &&
                 p != 0x1d551 &&
                 p != 0x1d6a6 &&
                 p != 0x1d6a7 &&
                 p != 0x1d7cc &&
                 p != 0x1d7cd ||
                 false)
          {
            kind = p < 0x1d7ce ? K_LETTER    // mathematical letters
                               : K_NUMERIC;  // special digits
          }
        else if (p >= 0x02100 && p <= 0x0214f || // Mathematical Letterlike Symbols https://www.unicode.org/charts/PDF/U2100.pdf
                 false)
          { // Maybe symbol kinds Lu, Ll, Lt and Lo would be good, see https://www.unicode.org/Public/UCD/latest/ucd/UnicodeData.txt
            kind = K_LETTER;
          }
        else
          {
            kind = K_UNKNOWN;
          }
      }
    return kind;
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

    check
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
    return Token.t_skip;
  }


  /**
   * Advance to the next line.
   *
   * @return Token.t_op
   */
  private Token skipUntilEOL()
  {
    int p = curCodePoint();
    int l;
    do
      {
        nextCodePoint();
        l = p;
        p = curCodePoint();
      }
    while (p != SourceFile.END_OF_FILE && !isNewLine(l, p));
    _curLine++;
    return Token.t_skip;
  }


  /**
   * Advance to the first codePoint after an Operator.  An Operator consists of
   * codePoints of kind K_OP, K_SHARP or K_SLASH.
   *
   * @return Token.t_op
   */
  private Token skipOp()
  {
    int p = curCodePoint();
    while (kind(p) == K_OP || kind(p) == K_SHARP || kind(p) == K_SLASH)
      {
        nextCodePoint();
        p = curCodePoint();
      }
    return Token.t_op;
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
        Errors.lineBreakNotAllowedHere(posObject(lineEndPos(_sameLine)), detail);
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
   * Match the current token with the given operator, i.e, check that current()
   * is Token.t_op and the operator is op.  If so, advance to the next token
   * using next(). Otherwise, cause a syntax error.
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
            check
              (op.charAt(i) == curCodePoint());
            nextCodePoint();
          }
        check
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
  String identifier()
  {
    if (PRECONDITIONS) require
      (current() == Token.t_ident);

    return tokenAsString();
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
   * Return the actual integer constant of the current t_integer token as a
   * string.
   */
  String integer()
  {
    if (PRECONDITIONS) require
      (current() == Token.t_integer);

    return tokenAsString();
  }


  /**
   * Return the actual string constant of the current t_string token as a
   * string.
   */
  String string()
  {
    if (PRECONDITIONS) require
      (current() == Token.t_string);

    return getAsString(pos() + 1, endPos() - 1);
  }


  /**
   * Return the current token as a string as it appeared in the source code.
   */
  private String tokenAsString()
  {
    if (PRECONDITIONS) require
      (current() != Token.t_eof);

    return getAsString(pos(), endPos());
  }


  /**
   * The current token as a string for debugging purposes.
   */
  String currentAsString()
  {
    var t = current();
    var result = t.toString();
    switch (t)
      {
      case t_op       :
      case t_integer  :
      case t_ident    :
      case t_string   : result = result + " '" + tokenAsString() + "'"; break;
      default         :
        if (t.isKeyword())
          {
            result = "'" + result + "'";
          }
        break;
      }
    return result;
  }

}

/* end of file */
