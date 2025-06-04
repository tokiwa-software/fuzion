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
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import dev.flang.util.Errors;
import dev.flang.util.Pair;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;
import dev.flang.util.SourceRange;
import dev.flang.util.StringHelpers;
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
   * Class representing tokens like t_lparen or specific operators like {@code <}, {@code >}.
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
   * Class representing parentheses like {@code (}, {@code )} or {@code <}, {@code >}.
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
     * Create Parens for operators like {@code <}, {@code >}
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
    t_op,          // operators +, -, *, /, .., |, etc.
    t_comma,       // ,
    t_lparen,      // (
    t_rparen,      // )
    t_lbrace,      // {
    t_rbrace,      // }
    t_lbracket,    // [
    t_rbracket,    // ]
    t_period,      // .
    t_semicolon,   // ;
    t_question,    // ?
    t_numliteral,  // 123
    t_ident,       // abc
    t_stringQQ,    // "abc"
                   // OR multiline string
                   // """
                   //   abc"""
    t_stringQD,    // '"x is $'   in "x is $x.".
                   // OR multiline string
                   // '"""
                   //   x is $'   in
                   // '"""
                   //   x is $x."""'
    t_stringQB,    // '"a+b is {' in "a+b is {a+b}."
                   // OR multiline string
                   // '"""
                   //   a+b is {' in
                   // '"""
                   //   a+b is {a+b}."""'
    t_StringDQ,    // '+-*"'      in "abc$x+-*"
                   //     ^--- fat quotations (""") instead of single
                   //          quotation if part of multiline string
    t_StringDD,    // '+-*$'      in "abc$x+-*$x.".
    t_StringDB,    // '+-*{'      in "abc$x+-*{a+b}."
    t_stringBQ,    // '}+-*"'     in "abc{x}+-*"
                   //      ^--- fat quotations (""") instead of single
                   //           quotation if part of multiline string
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
    t_native("native"),
    t_for("for"),
    t_in("in"),
    t_do("do"),
    t_fixed("fixed"),
    t_loop("loop"),
    t_while("while"),
    t_until("until"),
    t_variant("variant"),
    t_pre("pre"),
    t_post("post"),
    t_inv("inv"),
    t_var("var"),
    t_match("match"),
    t_value("value"),
    t_ref("ref"),
    t_redef("redef"),
    t_const("const"),                 // unused
    t_leaf("leaf"),                   // unused
    t_infix("infix"),
    t_infix_right("infix_right"),
    t_prefix("prefix"),
    t_postfix("postfix"),
    t_ternary("ternary"),
    t_index("index"),
    t_set("set"),
    t_private("private"),
    t_module("module"),
    t_public("public"),
    t_type("type"),
    t_universe("universe"),
    t_eof,               // end of file
    t_indentationLimit,  // token's indentation is not sufficient
    t_lineLimit,         // token is in next line while sameLine() parsing is enabled
    t_spaceOrSemiLimit,  // token follows white space or semicolon while endAtSpace is enabled
    t_commaLimit,        // token is operator "," while endAtComma is enabled
    t_colonLimit,        // token is operator ":" while endAtColon is enabled
    t_barLimit,          // token is operator "|" while endAtBar is enabled
    t_ambiguousSemi,     // it is unclear whether the semicolon ends the inner or the outer block, will always cause a syntax error
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
     * maximum length of the keywords.
     */
    public static int _maxKeywordLength = Stream.of(_keywords)
      .mapToInt(k -> k.keyword().length())
      .max()
      .orElseThrow();

    /**
     * Array of sorted arrays of keywords of equal length.  Used to reduce
     * effort to find keyword by checking only those with a correct length.
     */
    public static Token[][] _keywordsOfLength = IntStream.range(0, _maxKeywordLength+1)
    .mapToObj(i -> Stream.of(_keywords)
              .filter(k -> k.keyword().length() == i)
              .toArray(Token[]::new))
      .toArray(Token[][]::new);

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
            case t_period            : result = "period '.'"                                 ; break;
            case t_lparen            : result = "left parenthesis '('"                       ; break;
            case t_rparen            : result = "right parenthesis ')'"                      ; break;
            case t_lbrace            : result = "left curly brace '{'"                       ; break;
            case t_rbrace            : result = "right curly brace '}'"                      ; break;
            case t_lbracket          : result = "left bracket '['"                           ; break;
            case t_rbracket          : result = "right bracket ']'"                          ; break;
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

  static enum SemiState {
    CONTINUE, // continue parsing current rule at semicolon
    END,      // end block at semicolon
    ERROR     // ambiguous semicolon in nested blocks
  }


  /**
   * Private code point classes
   */
  private static final byte K_UNKNOWN =  0;
  private static final byte K_OP      =  1;  // '+'|'-'|'*'|'%'|'|'|'~'|'#'|'!'|'$'|'&'|'@'|':'|'<'|'>'|'='|'^';
  private static final byte K_WS      =  2;  // spaces, tabs, lf, cr, ...
  private static final byte K_SLASH   =  3;  // '/', introducing a comment or an operator.
  private static final byte K_SHARP   =  4;  // '#', introducing a comment or an operator.
  private static final byte K_COMMA   =  5;  // ','
  private static final byte K_PERIOD  =  6;  // ','
  private static final byte K_LPAREN  =  7;  // '('  round brackets or parentheses
  private static final byte K_RPAREN  =  8;  // ')'
  private static final byte K_LBRACE  =  9;  // '{'  curly brackets or braces
  private static final byte K_RBRACE  = 10;  // '}'
  private static final byte K_LBRACK  = 11;  // '['  square brackets
  private static final byte K_RBRACK  = 12;  // ']'
  private static final byte K_SEMI    = 13;  // ';'
  private static final byte K_DIGIT   = 14;  // '0'..'9'
  private static final byte K_QUESTN  = 15;  // '?'
  private static final byte K_LETTER  = 16;  // 'A'..'Z', 'a'..'z', mathematical letter
  private static final byte K_GRAVE   = 17;  // '`'  backtick
  private static final byte K_DQUOTE  = 18;  // '"'
  private static final byte K_SQUOTE  = 19;  // '''
  private static final byte K_BACKSL  = 20;  // '\\'
  private static final byte K_NUMERIC = 21;  // mathematical digit
  private static final byte K_EOF     = 22;  // end-of-file
  private static final byte K_ERROR   = 23;  // an error occurred


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
    K_COMMA   /* ,   */, K_OP      /* -   */, K_PERIOD  /* .   */, K_SLASH   /* /   */,
    // 3…
    K_DIGIT   /* 0   */, K_DIGIT   /* 1   */, K_DIGIT   /* 2   */, K_DIGIT   /* 3   */,
    K_DIGIT   /* 4   */, K_DIGIT   /* 5   */, K_DIGIT   /* 6   */, K_DIGIT   /* 7   */,
    K_DIGIT   /* 8   */, K_DIGIT   /* 9   */, K_OP      /* :   */, K_SEMI    /* ;   */,
    K_OP      /* <   */, K_OP      /* =   */, K_OP      /* >   */, K_QUESTN  /* ?   */,
    // 4…
    K_OP      /* @   */, K_LETTER  /* A   */, K_LETTER  /* B   */, K_LETTER  /* C   */,
    K_LETTER  /* D   */, K_LETTER  /* E   */, K_LETTER  /* F   */, K_LETTER  /* G   */,
    K_LETTER  /* H   */, K_LETTER  /* I   */, K_LETTER  /* J   */, K_LETTER  /* K   */,
    K_LETTER  /* L   */, K_LETTER  /* M   */, K_LETTER  /* N   */, K_LETTER  /* O   */,
    // 5…
    K_LETTER  /* P   */, K_LETTER  /* Q   */, K_LETTER  /* R   */, K_LETTER  /* S   */,
    K_LETTER  /* T   */, K_LETTER  /* U   */, K_LETTER  /* V   */, K_LETTER  /* W   */,
    K_LETTER  /* X   */, K_LETTER  /* Y   */, K_LETTER  /* Z   */, K_LBRACK  /* [   */,
    K_BACKSL  /* \   */, K_RBRACK  /* ]   */, K_OP      /* ^   */, K_LETTER  /* _   */,
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


  /**
   * Convert a code point into a human readable string to be used in the Fuzion reference manual.
   *
   * For non-ASCII, this currently only prints the unicode value as 0xNNNN.
   *
   * @param cp a codepoint
   *
   * @return a human-readable String describing cp.
   */
  static String codePointAsString(int cp)
  {
    var n = _asciiControlName[cp];
    return String.format("0x%04x %s", cp,
                         n != null ? "`" + n.strip() + "`":
                         cp == ' ' ? "SPACE" :
                         cp < 0x7f ? "`'" + (char) cp + "'`"
                                   : "");
  }



  /**
   * Helper for main() to create asciidoc source for a codepoint ranges that
   * correspond to given kind (K_*).  Result is printed to System.out.
   *
   * @param kind one of the kinds defined as constants K_LETTER, K_DIGIT, etc.
   */
  static void showChars(byte kind)
  {
    var got = new TreeSet<String>();
    for (int cp = 0; cp < 0xffff; cp++)
      {
        var k = kind(cp);
        if (k == kind)
          {
            if (cp <= 0x7f)
              {
                var cp2 = cp;
                while (cp2 < 0x7f && kind(cp2+1) == k)
                  {
                    cp2++;
                  }
                if (cp2 > cp+1)
                  {
                    say("* " + codePointAsString(cp) + " .. " + codePointAsString(cp2));
                    cp = cp2;
                  }
                else
                  {
                    say("* " + codePointAsString(cp));
                  }
              }
            else
              {
                got.add(UnicodeData.category(cp));
              }
          }
      }
    if (!got.isEmpty())
      {
        say("* Unicode " + StringHelpers.plural(got.size(), "category") + " " +
                           got.stream().map(x -> "`" + x + "`").collect(Collectors.joining (", ")));
      }
  }


  /**
   * main method used to create asciidoc text to be used in the Fuzion reference
   * manual.
   *
   * @param args command line arguments. If an argument is
   *
   *   "-whiteSpace", then output list of white space code points
   *
   *   "-illegal", then output list of illegal code points and categories.
   *
   *   "-keywords", then output list of keywords.
   */
  public static void main(String[] args)
  {
    Stream.of(args).forEach
      (x ->
       {
         if (x.equals("-whiteSpace"))
           {
             for (var cp = 0; cp < _asciiKind.length; cp++)
               {
                 if (kind(cp) == K_WS)
                   {
                     say(codePointAsString(cp));
                   }
               }
           }
         else if (x.equals("-illegal" )) { showChars(K_UNKNOWN); }
         else if (x.equals("-letter"  )) { showChars(K_LETTER ); }
         else if (x.equals("-digit"   )) { showChars(K_DIGIT  ); }
         else if (x.equals("-numeric" )) { showChars(K_NUMERIC); }
         else if (x.equals("-op"      )) { showChars(K_OP     ); }
         else if (x.equals("-keywords"))
           {
             for (var k : Token._keywords)
               {
                 System.out.print("`"+k+"` ");
               }
             say();
           }
         else if (x.equals("-stringLiteralEscapes"))
           {
             say("""
[options=\"header\",cols=\"1,1\"]
|====
   | escape sequence | resulting code point
                                """);
             for (int i = 0; i < StringLexer.escapeChars.length; i++)
               {
                 var c      = StringLexer.escapeChars[i][0];
                 var result = StringLexer.escapeChars[i][1];
                 if (c != '\n'  && c != '\r')
                   {
                     say("  | `\\" + (char) c + "` | " + codePointAsString(result));
                   }
               }
             say("  | `\\` + " + codePointAsString('\n') + " | _nothing_");
             say("  | `\\` + " + codePointAsString('\r') + " + " + codePointAsString('\n') + " | _nothing_");
             say("|====");
           }

       });
  }


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
  private Literal _curNumLiteral = null;


  /**
   * Position of the current token
   */
  private int _tokenPos = -1;


  /**
   * Position of the previous token, -1 if none
   */
  private int _lastTokenPos = -1;


  /**
   * End position of the previous token, -1 if none
   */
  private int _lastTokenEndPos = -1;


  /**
   * Minimum indentation required for the current token: {@code current()} must have
   * {@code indentation > _minIndent}, while {@code currentAtMinIndent()} must have {@code indentation
   * >= _minIndent}.
   */
  private int _minIndent = -1;


  /**
   * Token at this pos will be returned by {@code current()} even if its indentation is
   * at {@code <= _}minIndent. If set to the first token of a expression that sets
   * {@code _minIndent}, this ensures that we can still parse the first token of this
   * expression.
   */
  private int _minIndentStartPos = -1;


  /**
   * Line restriction for {@code current()}/{@code currentAtMinIndent()}: Symbols not in this
   * line will be replaced by t_lineLimit.
   */
  private int _sameLine = -1;


  /**
   * White space and semicolon restriction for {@code current()}/{@code currentAtMinIndent()}: Symbols after
   * this position that are preceded by white space or semicolon will be replaced by
   * {@code t_spaceLimit}.
   */
  private int _endAtSpace = Integer.MAX_VALUE;


  /**
   * {@code :} operator restriction for {@code current()}/{@code currentAtMinIndent()}: if set,
   * "," will be replaced by {@code t_commaLimit}.
   */
  private boolean _endAtComma = false;


  /**
   * {@code :} operator restriction for {@code current()}/{@code currentAtMinIndent()}: if set,
   * operator ":" will be replaced by {@code t_colonLimit}.
   */
  private boolean _endAtColon = false;


  /**
   * {@code |} operator restriction for {@code current()}/{@code currentAtMinIndent()}: if set,
   * operator "|" will be replaced by t_barLimit.
   */
  private boolean _endAtBar = false;

  private SemiState _atSemicolon = SemiState.CONTINUE;


  /**
   * Has the raw token before {@code current()} been skipped because {@code ignore(t)} resulted
   * in {@code true}?
   */
  private boolean _ignoredTokenBefore = false;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create a lexer for the given file or byte array data.
   */
  public Lexer(Path fileName, byte[] sf)
  {
    super(fileName, sf);

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
    _curNumLiteral = original._curNumLiteral;
    _tokenPos = original._tokenPos;
    _lastTokenPos = original._lastTokenPos;
    _lastTokenEndPos = original._lastTokenEndPos;
    _minIndent = original._minIndent;
    _minIndentStartPos = original._minIndentStartPos;
    _sameLine = original._sameLine;
    _endAtSpace = original._endAtSpace;
    _endAtComma = original._endAtComma;
    _endAtColon = original._endAtColon;
    _endAtBar = original._endAtBar;
    _atSemicolon = original._atSemicolon;
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
    // this is performance-relevant, so we provide a fast path if this can be
    // decided from the ASCII value of the current code point:
    var p = curCodePoint();
    if (p <= 0x7f)
      {
        var k = _asciiKind[p];
        switch (k)
          {
          case K_OP      :
          case K_COMMA   :
          case K_PERIOD  :
          case K_QUESTN  :
          case K_LPAREN  :
          case K_RPAREN  :
          case K_LBRACE  :
          case K_RBRACE  :
          case K_LBRACK  :
          case K_RBRACK  :
          case K_SEMI    :
          case K_DIGIT   :
          case K_LETTER  :
          case K_GRAVE   :
          case K_DQUOTE  :
          case K_SQUOTE  :
          case K_BACKSL  :
          case K_NUMERIC : return false;   // fast-path for common positive cases
          case K_WS      : return true;    // fast path for common negative cases
          case K_UNKNOWN :
          case K_SLASH   :
          case K_SHARP   :
          case K_EOF     :
          case K_ERROR   : break;  // comments and special cases, use slow path
          default        : throw new Error("unknown case in Lexer.ignoredTokenAfter for " + k + "!");
          }
      }
    // slow path: fork lexer and check using `ignore()`:
    var f = new Lexer(this);
    f.nextRaw();
    return ignore(f._curToken);
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
  int setMinIndent(int startPos)
  {
    int result = _minIndentStartPos;
    _minIndent = startPos >= 0 ? codePointIndentation(startPos) : -1;
    _minIndentStartPos = startPos;

    return result;
  }


  /**
   * Get the current minimum indentation.
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
   * Restrict parsing until the next occurrence of white space or semicolon.  Symbols after
   * fromPos that are preceded by white space or semicolon will be replaced by t_spaceLimit.
   *
   * @param fromPos the position of the last token that is permitted to be
   * preceded by white space.
   *
   * @return the previous endAtSpace-restriction, Integer.MAX_VALUE if none.
   */
  int endAtSpaceOrSemi(int fromPos)
  {
    if (PRECONDITIONS) require
      (fromPos >= 0);

    int result = _endAtSpace;
    _endAtSpace = fromPos;

    return result;
  }


  /**
   * Restrict parsing until the next occurrence of ",". "," will be replaced by
   * t_commaLimit.
   *
   * @param endAtComma true to enable, false to disable
   *
   * @return the previous endAtComma-restriction.
   */
  boolean endAtComma(boolean endAtComma)
  {
    var result = _endAtComma;
    _endAtComma = endAtComma;

    return result;
  }


  /**
   * Restrict parsing until the next occurrence of operator ":".  Operator ":"
   * will be replaced by t_colonLimit.
   *
   * @param endAtColon true to enable, false to disable
   *
   * @return the previous endAtColon-restriction.
   */
  boolean endAtColon(boolean endAtColon)
  {
    var result = _endAtColon;
    _endAtColon = endAtColon;

    return result;
  }


  /**
   * Restrict parsing until the next occurrence of operator "|".  Operator "|"
   * will be replaced by t_barLimit.
   *
   * @param endAtBar true to enable, false to disable
   *
   * @return the previous endAtBar-restriction
   */
  boolean endAtBar(boolean endAtBar)
  {
    var result = _endAtBar;
    _endAtBar = endAtBar;

    return result;
  }

  /**
   * Increases the semicolon state, which is used to detect ambiguous semicolons
   * e.g. when blocks are nested in one line.
   */
  void incrSemiState()
  {
    _atSemicolon = _atSemicolon == SemiState.CONTINUE ? SemiState.END
                                                      : SemiState.ERROR;
  }

  /**
   * Decreases the semicolon state, which is used to detect ambiguous semicolons
   * e.g. when blocks are nested in one line.
   */
  void decrSemiState()
  {
    _atSemicolon = _atSemicolon == SemiState.ERROR ? SemiState.END
                                                   : SemiState.CONTINUE;
  }

  /**
   * Set a new semicolon state,  which is used to detect ambiguous semicolons
   * e.g. when blocks are nested in one line.
   *
   * @param newVal the new semicolon state
   * @return the previous semicolon state
   */
  SemiState semiState(SemiState newVal)
  {
    SemiState old = _atSemicolon;
    _atSemicolon = newVal;
    return old;
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
  <V> V relaxLineAndSpaceLimit(Supplier<V> c)
  {
    int oldLine = sameLine(-1);
    int oldEAS = endAtSpaceOrSemi(Integer.MAX_VALUE);
    var oldEAc = endAtComma(false);
    var oldEAC = endAtColon(false);
    var oldEAB = endAtBar(false);
    var oldSemiSt = semiState(SemiState.CONTINUE);
    V result = c.get();
    sameLine(oldLine);
    endAtSpaceOrSemi(oldEAS);
    endAtComma(oldEAc);
    endAtColon(oldEAC);
    endAtBar(oldEAB);
    semiState(oldSemiSt);
    return result;
  }


  /**
   * short-hand for bracketTermWithNLs with c==def.
   */
  <V> V optionalBrackets(Parens brackets, String rule, Supplier<V> c)
  {
    return currentMatches(true, brackets._left)
      ? bracketTermWithNLs(brackets, rule, c, c)
      : c.get();
  }

  /**
   * short-hand for bracketTermWithNLs with c==def.
   */
  <V> V bracketTermWithNLs(Parens brackets, String rule, Supplier<V> c)
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
  <V> V bracketTermWithNLs(Parens brackets, String rule, Supplier<V> c, Supplier<V> def)
  {
    var oldSemiSt = semiState(SemiState.CONTINUE);
    var start = brackets._left;
    var end   = brackets._right;
    var ol = line();
    match(true, start, rule);
    var indentRef = tokenPos();
    V result = relaxLineAndSpaceLimit(!currentMatches(true, end) ? c : def);
    var nl = line();
    relaxLineAndSpaceLimit(() ->
                           {
                            if (current(true) != end._token)
                              {
                                // if indentation decreases before closing bracket, discard everything until closing bracket
                                Errors.indentationProblemEncountered(tokenSourcePos(), sourcePos(indentRef), Parser.parserDetail(rule));
                                while (current(true) != end._token && current(true) != Token.t_eof)
                                  {
                                      next();
                                  }
                              }
                            match(true, end, rule);
                            return Void.TYPE; // is there a better unit type in Java?
                           });
    var sl = sameLine(-1);
    if (sl == ol)
      {
        sl = nl;
      }
    sameLine(sl);
    semiState(oldSemiSt);
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
  boolean skipBracketTermWithNLs(Parens brackets, Supplier<Boolean> c)
  {
    var start = brackets._left;
    var end   = brackets._right;
    var ol = line();
    var result = skip(false, start) && relaxLineAndSpaceLimit(c);
    if (result)
      {
        var nl = line();
        result = relaxLineAndSpaceLimit(() -> {
            return skip(true , end);
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
    _lastTokenPos = _tokenPos;
    _lastTokenEndPos = tokenEndPos();
    _ignoredTokenBefore = false;
    nextRaw();
    while (ignore(_curToken))
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
   * @param endAtSpaceOrSemi the white space and semicolon restriction (Integer.MAX_VALUE if none):
   * Any token after this position will be replaced by t_spaceLimit.
   *
   * @param endAtColon true to replace operator ":" by t_colonLimit.
   *
   * @param endAtBar true to replace operator "|" by t_barLimit.
   */
  Token current(int minIndent, int sameLine, int endAtSpaceOrSemi, boolean endAtComma, boolean endAtColon, boolean endAtBar)
  {
    var t = _curToken;
    int l = line();
    int p = _tokenPos;
    return
      t == Token.t_eof                                       ? t                              :
      sameLine  >= 0 && l != sameLine                        ? Token.t_lineLimit              :
      p > endAtSpaceOrSemi && ignoredTokenBefore()           ? Token.t_spaceOrSemiLimit       :
      p > endAtSpaceOrSemi && _curToken == Token.t_semicolon ? Token.t_spaceOrSemiLimit       :
      p == _minIndentStartPos                                ? t                              :
      minIndent >= 0 && codePointIndentation(p) <= minIndent ? Token.t_indentationLimit       :
      endAtComma                  &&
      _curToken == Token.t_comma                             ? Token.t_commaLimit             :
      endAtColon                  &&
      _curToken == Token.t_op     &&
      tokenAsString().equals(":")                            ? Token.t_colonLimit             :
      endAtBar                    &&
      _curToken == Token.t_op     &&
      tokenAsString().equals("|")                            ? Token.t_barLimit               :
      ambiguousSemi()                                        ? Token.t_ambiguousSemi
                                                             : _curToken;
  }

  private boolean ambiguousSemi()
  {
    return _atSemicolon == SemiState.ERROR &&
           _curToken == Token.t_semicolon && semiNotAtEnd();
  }

  private boolean semiNotAtEnd()
  {
    var lookAhead = new Lexer(this);
    lookAhead.next();
    int nextTokLine = lookAhead.line();
    // semicolon does not end line if the next (non ignored) token is in the same line
    return line() == nextTokLine;
  }


  /**
   * The current token.  If indentation limit was set and the current token is
   * not indented deeper than this limit, return Token.t_indentationLimit.
   */
  public Token current()
  {
    return current(_minIndent, _sameLine, _endAtSpace, _endAtComma, _endAtColon, _endAtBar);
  }


  /**
   * The current token.  If indentation limit was set and the current token is
   * indented less than this limit minus 1, return Token.t_indentationLimit.
   */
  Token currentAtMinIndent()
  {
    return current(_minIndent - 1, _sameLine, _endAtSpace, _endAtComma, _endAtColon, _endAtBar);
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
    return _curToken;
  }


  /**
   * The byte position in the source file.
   */
  public int tokenPos()
  {
    return _tokenPos;
  }


  /**
   * The byte position of the previous non-skip token in the source file.  -1 if
   * this does not exist.
   */
  int lastTokenPos()
  {
    return _lastTokenPos;
  }


  /**
   * The byte end position of the previous non-skip token in the source file.  -1 if
   * this does not exist.
   */
  int lastTokenEndPos()
  {
    return _lastTokenEndPos;
  }


  /**
   * The current lexer position as a SourcePosition instance
   */
  SourcePosition tokenSourcePos()
  {
    return sourcePos(tokenPos());
  }


  /**
   * The start and end position of the current token as a SourceRange instance
   */
  SourceRange tokenSourceRange()
  {
    return sourceRange(tokenPos(), tokenEndPos());
  }


  /**
   * The start and end position of the previous token as a SourceRange instance
   */
  SourceRange lastTokenSourceRange()
  {
    return sourceRange(lastTokenPos(), lastTokenEndPos());
  }


  /**
   * Position of the first byte in source file after the current token.
   */
  int tokenEndPos()
  {
    return bytePos();
  }


  /**
   * Obtain the given range pos.bytePos()..lastTokenEndPos() as a SourceRange object.
   *
   * @param pos a source position within this file, must be before lastTokenEndPos().
   */
  public SourceRange sourceRange(SourcePosition pos)
  {
    if (PRECONDITIONS) require
      (pos != null,
       Errors.any() || pos.bytePos() < lastTokenEndPos());

    return sourceRange(pos.bytePos());
  }


  /**
   * Obtain the given range pos..lastTokenEndPos() as a SourceRange object.
   *
   * @param pos a byte position within this file, must be smaller than
   * lastTokenEndPos(), unless there were previous errors
   */
  public SourceRange sourceRange(int pos)
  {
    if (PRECONDITIONS) require
      (0 <= pos,
       Errors.any() || pos < lastTokenEndPos());
    // in error case lastTokenEnd() < pos is possible
    var endPos = Math.max(Math.min(pos+1, byteLength()), lastTokenEndPos());
    return sourceRange(pos, endPos);
  }


  /**
   * Obtain the given range pos..endPos as a SourceRange object.  In case this
   * range is empty, get the range prevPos..prevPos+1
   *
   * This is useful in case the range could be empty as for a code block as
   * follows:
   *
   * The non-empty case
   *
   *    x is something
   *        ^^        ^
   *        ||        +-- endPos
   *        |+----------- pos
   *        |
   *        +------------ prevPos
   *    y is block
   *
   * results in pos..endPos:
   *
   *    x is something
   *    -----^^^^^^^^^
   *
   * while the empty case
   *
   *    x is
   *        ^
   *        +------------ endPos
   *        +------------ prevPos
   *    y is block
   *    ^
   *    |
   *    +---------------- pos
   *
   * results in
   *
   *    x is
   *    ----^
   *    y is block
   *
   * @param prevPos the last position of the previous token
   *
   * @param pos a byte position within this file.
   *
   * @param endPos a byte position, usually after pos within this file.
   */
  public SourceRange sourceRange(int prevPos, int pos, int endPos)
  {
    if (PRECONDITIONS) require
      (0 <= prevPos,
       0 <= pos,
       prevPos <= byteLength(),
       pos     <= byteLength(),
       endPos  <= byteLength());

    return pos < endPos ? sourceRange(pos, endPos)
                        : sourceRange(prevPos, Math.min(byteLength(), prevPos+1));
  }


  /**
   * The line number of the current token.
   */
  int line()
  {
    return this.lineNum(_tokenPos);
  }


  /**
   * Advance to the next token. The next token might be an ignored token, i.e,
   * white space or a comment.
   */
  public void nextRaw()
  {
    _tokenPos = bytePos();
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
    /*
    // tag::fuzion_rule_LEXR_LEGALCP[]
Fuzion source code may not contain any
xref:unsupported_code_points[unsupported code points].
    // end::fuzion_rule_LEXR_LEGALCP[]
    */
    /*
    // tag::fuzion_rule_LEXR_UNUSEDCP[]
Unless part of another token, Fuzion source code may not contain unsupported code points backtick `\``, backslash `\\`
or single quote `'`.  This might, however, be used in the future.
    // end::fuzion_rule_LEXR_UNUSEDCP[]
    */
              Errors.error(sourcePos(),
                           "Unknown code point in source file",
                           "Unknown code point " + Integer.toHexString(p) + " is not permitted by Fuzion's grammar.");
              token = Token.t_error;
              break;
            }
          case K_SHARP   :   // '#'
            {
              /*
    // tag::fuzion_rule_LEXR_COMMENT2[]
A code point sharp 0x023 `#` that is not part of an operator starts a comment that extends until the end of the current line.
    // end::fuzion_rule_LEXR_COMMENT2[]
              */
              boolean SHARP_COMMENT_ONLY_IF_IN_COL_1 = false;
              token =
                !SHARP_COMMENT_ONLY_IF_IN_COL_1 ||
                codePointIndentation(_tokenPos) == 1 ? skipUntilEOL() // comment until end of line
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
          case K_OP      :   // '+'|'-'|'*'|'%'|'|'|'~'|'!'|'$'|'&'|'@'|':'|'<'|'>'|'='|'^';
            {
    /*
    // tag::fuzion_rule_LEXR_OPER1[]
A Fuzion operator code point starts with a xref:fuzion_op[Fuzion operator code point].
    // end::fuzion_rule_LEXR_OPER1[]
    */
              token = skipOp(Token.t_op);
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
    /*
    // tag::fuzion_rule_LEXR_WHITESPACE[]
xref:whitespace_code_points[White space] separates tokens but does not appear as a token itself.
    // end::fuzion_rule_LEXR_WHITESPACE[]
    */
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
              /*
    // tag::fuzion_rule_LEXR_OPER4[]
A Fuzion operator may start with a single slash 0x002F '/'.
    // end::fuzion_rule_LEXR_OPER4[]
              */
              p = curCodePoint();
              /*
    // tag::fuzion_rule_LEXR_COMMENT1[]
A sequence of code points slash 0x02f `/` followed by asterisk 0x002a `\*` starts a comment that extends until a corresponding sequence of `*` `/` is encountered.  These comments may be nested and each opening `/` `\*` must be matched by a corresponding `\*` `/`.
    // end::fuzion_rule_LEXR_COMMENT1[]
              */
              token = p == '*' ? skipComment()
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
    /*
    // tag::fuzion_rule_LEXR_OPER2[]
A single code point 0x002E '.' or 0x003F '?' is not an operator.
    // end::fuzion_rule_LEXR_OPER2[]
    */
          /**
PERIOD      : '.'
            ;
          */
          case K_PERIOD  :   // '.'
            {
              token = skipOp(Token.t_period);
              break;
            }
          /**
QUESTION  : '?'
          ;
          */
          case K_QUESTN  :   // '?'
            {
              token = skipOp(Token.t_question);
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
          case K_LBRACK  :    // '['  square brackets
            {
              token = Token.t_lbracket;
              break;
            }
          /**
RBRACKET    : ']'
            ;
          */
          case K_RBRACK  :    // ']'
            {
              token = Token.t_rbracket;
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
          /* NUM_LITERAL: */
          case K_DIGIT   :    // '0'..'9'
            {
              _curNumLiteral = numLiteral(p);
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
    /*
    // tag::fuzion_rule_LEXR_IDENT1[]
A Fuzion identifier starts with a codepoint that is a xref:fuzion_letter[Fuzion letter] followed by one or several codepoints of type xref:fuzion_letter[Fuzion letter], xref:fuzion_digit[Fuzion digit] or xref:fuzion_numeric[Fuzion numeric].
    // end::fuzion_rule_LEXR_IDENT1[]
    */
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
    if (p1 != ' ' && !isNewLine(p1) && !(p1 == CR && p2 == LF))
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
   * @return true iff cp may be part of an identifier, e.g., {@code i}, {@code 3}, {@code ²}, etc.
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
    /*
    // tag::fuzion_rule_LEXR_IDENT2[]
A xref:fuzion_keyword[Fuzion keyword] cannot be used as a Fuzion identifier.
    // end::fuzion_rule_LEXR_IDENT2[]
    */

    Token result = Token.t_ident;
    var s = tokenPos();
    var e = tokenEndPos();
    var len = e - s;
    if (len <= Token._maxKeywordLength)
      {
        var a = Token._keywordsOfLength[len];
        // perform binary search in Token.keywords array:
        int l = 0;
        int r = a.length-1;
        while (l <= r)
          {
            int m = (l + r) / 2;
            Token t = a[m];
            int c = compareToString(s, e, t._keyword);
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
  private static int kind(int p)
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
NUM_LITERAL : DIGITS_W_DOT EXPONENT
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
  Literal numLiteral(int firstDigit)
  {
    var m = new Digits(firstDigit, true, false);
    var p = curCodePoint();
    return switch (p)
      {
      case 'P', 'E', 'p', 'e' ->
      {
        if (p == 'p' || p == 'e')
          {
            Errors.error(sourcePos(),
              "Broken numeric literal, exponent indicator must be in upper case.",
              "To fix this change '" + (char) p + "' to '" + (p == 'e' ? 'E' : 'P') + "'.");
            p = p == 'e' ? 'E' : 'P';
          }

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
      default ->
      {
        if (kind(p) == K_LETTER)
        {
          var c = curCodePoint();
          var possiblePrefixError = (c == 'b' || c == 'o' || c == 'd' || c == 'x')
                                    && codePointAt(bytePos()-1) == '0'
                                    && codePointAt(bytePos()-2) == '.';
          Errors.error(sourcePos(),
                       "Broken numeric literal, expected anything but a letter following a numeric literal.",
                       possiblePrefixError
                       ? "Fractional part must not have base prefix '0" + (char) c + "' if integer part has none."
                       : null);
          // skip parts of broken num literal to avoid subsequent errors
          while (kind(curCodePoint()) == K_LETTER || kind(curCodePoint()) == K_DIGIT)
            {
              nextCodePoint();
            }
        }
        yield new Literal(m);
      }};
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
     * Is the exponent given with {@code P} (and not with {@code E}).  E.g., for
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
     * The value of the mantissa, ignoring decimal {@code .} position (i.e., value of
     * {@code 123.456} is 123456).
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
     * The base as indicated by prefix {@code 0b}, {@code 0o}, {@code 0d}, {@code 0x}. E.g., for
     * "0x_de_ad.c0de", this will be hex.
     */
    public final Base _base;


    /**
     * The digits, without base prefix and without {@code _} separators.  E.g., for
     * "0x_de_ad.c0de", this will be "deadc0de"
     */
    public final String _digits;

    /**
     * Position of the decimal dot.  E.g., for "0x_de_ad.c0de", this will be
     * 4.
     */
    public int _dotAt = 0;

    /**
     * Was there a {@code -} preceding these digits?
     */
    public final boolean _negative;

    /**
     * Did an error occur?  E.g., for "0x_de_ad.c0de", this will be
     * false.
     */
    public boolean _hasError = false;

    /**
     * Helper routine to check if codepoint p is a digit for base. This is
     * generous, i.e., it will consider any digits {@code 0}..{@code 9} a digit even for
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
     * @param negative true if a {@code -} was encountered before firstDigit.
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
BIN_TAIL    : ".0b" BIN_DIGITS
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
OCT_TAIL    : ".0o" OCT_DIGITS
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
DEC_TAIL    : "."   DEC_DIGITS
            | ".0d" DEC_DIGITS
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
HEX_TAIL    : ".0x" HEX_DIGITS
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
          if (kind(fd) == K_DIGIT)
            {
              // match base prefix (base prefix must be repeated after dot in floating point literal)
              if (b != null)
                {
                  nextCodePoint();
                  var ok = matchBasePrefix('0', b);
                  nextCodePoint();
                  if (ok)
                    {
                      switch (b)
                        {
                          case Base.bin -> matchBasePrefix('b', b);
                          case Base.oct -> matchBasePrefix('o', b);
                          case Base.dec -> matchBasePrefix('d', b);
                          case Base.hex -> matchBasePrefix('x', b);
                        };
                    }
                }

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

    /**
     * Match the current code point against the expected base prefix character
     * and create an error if the don't match
     *
     * @param c the expected base prefix character
     * @param b the expected base of the num literal
     * @return true iff c matched current code point and no error was created
     */
    boolean matchBasePrefix(Character c, Base b)
    {
      var result = true;
      if (curCodePoint() != c)
        {
          result = false;
          Errors.error(null, sourcePos(bytePos()), "Base prefix must be repeated after dot in floating point literal",
                       "Expected '" + c + "' but found '"
                       + new String(Character.toChars(curCodePoint()))
                       + "' in " + b._name + " floating point number. Base prefixes in integer and fractional part must be the same.");
        }
      return result;
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
     * The value, ignoring {@code -} and ignoring decimal {@code .} position (i.e., value of {@code 123.456} is
     * 123456).
     */
    BigInteger absValue()
    {
      return new BigInteger(_digits, _base._base);
    }

    /**
     * The value, ignoring decimal {@code .} position (i.e., value of {@code 123.456} is
     * 123456).
     */
    BigInteger signedValue()
    {
      var res = absValue();
      return _negative ? res.negate() : res;
    }

  }


  /**
   * Skip comments of the form {@code /}{@code *} .. {@code *}{@code /}, skip them recursively if nested
   * comments are found.  Called with the current code point at the first {@code *}.
   */
  private Token skipComment()
  {
    int nestedStartPos = -1, nestedEndPos = -1;
    int startPos = _tokenPos;
    int p = curCodePoint();

    if (CHECKS) check
      (p == '*');

    nextCodePoint();
    boolean gotStar = false;
    boolean done = false;
    do
      {
        p = curCodePoint();
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
        while (p != SourceFile.END_OF_FILE && !isNewLine(l));
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
    /*
    // tag::fuzion_rule_LEXR_OPER3[]
A Fuzion operator may contain one or several codepoints that are xref:fuzion_op[Fuzion operator code points], sharp 0x0023 '#' or slash 0x002F '/'.
    // end::fuzion_rule_LEXR_OPER3[]
    */
    int p = curCodePoint();
    while ((((1 << K_OP     |
              1 << K_SHARP  |
              1 << K_SLASH  |
              1 << K_PERIOD |
              1 << K_QUESTN   ) >> kind(p)) & 1) != 0)
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
    /*
    // tag::fuzion_rule_PARS_SYNTAX[]
Fuzion xref:input_source[input sources] must match the Fuzion grammar defined in <<_bnf_grammar>>.
    // end::fuzion_rule_PARS_SYNTAX[]
    */

    String detail = Parser.parserDetail(currentRule);
    switch (current())
      {
      case t_indentationLimit -> Errors.indentationProblemEncountered(sourcePos(pos),
                                                                      sourcePos(_minIndentStartPos),
                                                                      detail);
      case t_lineLimit        -> Errors.lineBreakNotAllowedHere (sourcePos(lineEndPos(_sameLine)), detail);
      case t_spaceOrSemiLimit -> Errors.whiteSpaceNotAllowedHere(sourcePos(tokenPos()), detail);
      case t_commaLimit       -> Errors.commaNotAllowedHere     (sourcePos(tokenPos()), detail);
      case t_colonLimit       -> Errors.colonPartOfTernary      (sourcePos(tokenPos()), detail);
      case t_barLimit         -> Errors.barPartOfCase           (sourcePos(tokenPos()), detail);
      case t_ambiguousSemi    -> Errors.ambiguousSemicolon(sourcePos(pos));
      default                 -> Errors.syntax(sourcePos(pos), expected, currentAsString(), detail);
      }
  }


  /**
   * Produce a syntax error at the current token's position.
   *
   * @param expected the expected tokens
   *
   * @param currentRule the current rule we are trying to parse
   */
  void syntaxError(String expected, String currentRule)
  {
    syntaxError(tokenPos(), expected, currentRule);
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
   * @param to the token we want to see
   *
   * @return true iff current token matches
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
   * @param to the token we want to see
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
   * @param codePoint the operator we want to see
   *
   * @return true iff the current token is the given operator.
   */
  boolean isOperator(int codePoint)
  {
    return isOperator(false, codePoint);
  }


  /**
   * Check if the current token is the given single-code point operator, i.e,
   * check that current() is Token.t_op and the operator is op.
   *
   * @param atMinIndent
   *
   * @param codePoint the operator we want to see
   *
   * @return true iff the current token is the given operator.
   */
  boolean isOperator(boolean atMinIndent, int codePoint)
  {
    return
      current(atMinIndent) == Token.t_op &&
      codePointAt(_tokenPos) == codePoint &&
      tokenEndPos() - tokenPos() == 1;
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
    return isOperator(false, op);
  }


  /**
   * Check if the current token is the given operator, i.e, check that current()
   * is Token.t_op and the operator is op.
   *
   * @param atMinIndent
   *
   * @param op the operator we want to see
   *
   * @return true iff the current token is the given operator.
   */
  boolean isOperator(boolean atMinIndent, String op)
  {
    return
      current(atMinIndent) == Token.t_op &&
      operator(atMinIndent).equals(op);
  }


  /**
   * Check if the current operator is an operator that starts with the given
   * string.  If so, split the current token into an operator op and the
   * remaining string.
   *
   * This is useful, e.g., to parse nested generics lists such as
   * {@code Stack<List<i32>>}, where the last token is seen by the lexer as a single
   * operator {@code >>}, while the parser prefers to see two consecutive operators {@code >}.
   */
  void splitOperator(String op)
  {
    if (current() == Token.t_op && operator().startsWith(op))
      {
        setPos(_tokenPos);
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
    return operator(false);
  }


  /**
   * @param atMinIndent
   *
   * Return the actual operator of the current t_op token as a string.
   */
  String operator(boolean atMinIndent)
  {
    if (PRECONDITIONS) require
      (current(atMinIndent) == Token.t_op);

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
   * Return an object with the details of the current t_numliteral token.
   */
  Literal curNumLiteral()
  {
    if (PRECONDITIONS) require
      (current() == Token.t_numliteral);

    return _curNumLiteral;
  }


  /**
   * Parse state to decide between normal parsing and {@code $<id>} and {@code {<expr>}} within
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
    QUOTE,   // A string starting with '"' or '"""' as in "normal string...
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
   * For a given string token, return if that string starts with {@code "} or follows
   * an embedded {@code $<id>} or {@code {<expr>}}.
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
   * For a given string token, return if that string ends with {@code "} or with an
   * embedded {@code $<id>} or {@code {<expr>}}.
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
     * The original string that started with {@code "}, i.e., disregarding any partial
     * strings following {@code $<id>} or {@code {<expr>}}.  Used for proper error messages.
     */
    final int _stringStart;


    /**
     * Empty if this string is being read from the underlying SourceFile directly.
     * Otherwise, the character start position in the underlying source file.
     */
    final Optional<Integer> _pos;

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


    /**
     * If this is changed, https://fuzion-lang.dev/tutorial/string_constants
     * must be changed as well.
     */
    static int[][] escapeChars = new int[][] {
        { 'b', '\b'  },  // BS 0x08
        { 't', '\t'  },  // HT 0x09
        { 'n', '\n'  },  // LF 0x0a
        { 'f', '\f'  },  // FF 0x0c
        { 'r', '\r'  },  // CR 0x0d
        { 's', ' '   },  // SP 0x20
        { '\"', '\"' },  // "  0x22
        { '$',  '$'  },  // $  0x24
        { '\\', '\\' },  // \  0x5c
        { '{',  '{'  },  // {  0x7b
        { '}',  '}'  },  // }  0x7d
        { '\n', -1   },
        { '\r', -1   },
      };


    /**
     * Store the indentation of multiline strings.
     * Empty if single line string.
     */
    private Optional<Integer> _multiLineIndentation; // NYI: CLEANUP: mark as final?


    /**
     * Create a string lexer at the start of lexing a string.  The first token
     * will be returned through this.finish().  Lexer.this._stringLexer will be
     * set to the new StringLexer instance if more tokens related to this string
     * will come.
     */
    StringLexer()
    {
      _stringStart = Lexer.this.tokenPos();
      _pos = Optional.empty();
      _beginning = StringEnd.QUOTE;
      _multiLineIndentation = Optional.empty();
    }


    /**
     * Create a StringLexer at the position of the current string token.  This
     * is used by string() to retrieve the actual string contents after the
     * lexing step has finished.
     *
     * @param sb a StringBuilder to receive the code points of this string.
     * @param multiLineIndentation if in multiline string, contains the indentation
     */
    StringLexer(StringBuilder sb, Optional<Integer> multiLineIndentation)
    {
      if (PRECONDITIONS) require
        (isString(current()));

      _stringStart = Lexer.this.tokenPos();
      _pos = Optional.of(Lexer.this.tokenPos() + (beginning(current()) == StringEnd.DOLLAR ? 0 : 1));
      _beginning = StringEnd.QUOTE;
      _multiLineIndentation = multiLineIndentation;
      iterateCodePoints(sb);
    }


    /**
     * Create a clone of this StringLexer, used for cloning the surrounding Lexer.
     */
    StringLexer(StringLexer original)
    {
      if (PRECONDITIONS) require
        (original._pos.isEmpty()  /* should happen only during lexing, not when retrieving via string() */);

      this._stringStart = original._stringStart;
      this._pos = original._pos;
      this._braceCount = original._braceCount;
      this._beginning = original._beginning;
      this._state = original._state;
      this._outer = original._outer == null ? null : new StringLexer(original._outer);
      this._multiLineIndentation = original._multiLineIndentation;
    }


    /**
     * Return the current raw code point, not processing any escapes.
     */
    private int raw(Optional<Integer> pos)
    {
      return pos
        .map(p -> codePointAt(p))
        .orElse(curCodePoint());
    }


    /**
     * Iterate over code points and append them to sb
     */
    private Token iterateCodePoints(StringBuilder sb)
    {
      var t = Token.t_undefined;
      var pos = startOfStringContent();

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
          else if (
                p < _asciiControlName.length
            && _asciiControlName[p] != null
            && !(_multiLineIndentation.isPresent() && isCRorLF(p))
          )
            {
              Errors.unexpectedControlCodeInString(sourcePos(), _asciiControlName[p], p, sourcePos(_stringStart));
              t = Token.t_error;
            }
          else
            {
              checkIndentation(pos);
              if (escaped)
                {
                  var i = 0;
                  while (i < escapeChars.length && p != (int) escapeChars[i][0])
                    {
                      i++;
                    }
                  if (i < escapeChars.length)
                    {
                      c = (int) escapeChars[i][1];
                    }
                  else
                    {
                      Errors.unknownEscapedChar(sourcePos(), p, escapeChars);
                    }
                  if (p != (int) '\r')
                    {
                      // if carriage return is encountered, wait for line feed
                      escaped = false;
                    }
                }
              else if (p == '\\')
                {
                  escaped = true;
                }
              else if (_multiLineIndentation.isPresent() && atMultiLineStringDelimiter(getPos(pos)))
                {
                  pos = advance(pos, 2); // skip fat quotation
                  t = _beginning.token(StringEnd.QUOTE);
                }
              // single or double '"' are allowed in multiline strings
              else if (p == '"' && _multiLineIndentation.isEmpty())
                {
                  t = _beginning.token(StringEnd.QUOTE);
                }
              else if (p == '$') { t = _beginning.token(StringEnd.DOLLAR); }
              else if (p == '{') { t = _beginning.token(StringEnd.BRACE);  }
              else if (!skipped(pos))
                {
                  c = p;
                }
              else
                {
                  // codepoint is skipped
                }
              if (isNewLine(p) && _multiLineIndentation.isEmpty())
                {
                  Errors.unexpectedEndOfLineInString(sourcePos(bytePos()-1), sourcePos(_stringStart));
                  t = Token.t_error;
                }
              pos = advance(pos, 1);
              p = raw(pos);
            }
          if (c >= 0 && sb != null)
            {
              if (c == LF && !sb.isEmpty())
              {
                var posBeforeLineBreak = (byteAt(getPos(pos)-2) & 0xff )== CR
                                            ? getPos(pos) - 3
                                            : getPos(pos) - 2;
                var previousCodepoint = byteAt(posBeforeLineBreak) & 0xff;
                if (kind(previousCodepoint) == K_WS && !isCRorLF(previousCodepoint))
                  {
                    Errors.trailingWhiteSpaceInMultiLineString(sourcePos(posBeforeLineBreak));
                  }
              }
              sb.appendCodePoint(c);
            }
        }
      return t;
    }


    /**
     * In multiline strings check if indentation is at least
     * as much as reference the indentation of the first line.
     *
     * @param curPos
     */
    private void checkIndentation(Optional<Integer> curPos)
    {
      _multiLineIndentation.ifPresent(indentation ->
        {
          var codePoint = raw(curPos);
          if (   !atMultiLineStringDelimiter(getPos(curPos))
              && codePoint != SP
              // empty lines are allowed
              && codePoint != CR
              && codePoint != LF
              && column(curPos) < indentation
            )
            {
              Errors.notEnoughIndentationInMultiLineString(sourcePos(getPos(curPos)), indentation-1);
            }
        });
    }


    /**
     * Is this codepoint a carriage return or line feed?
     * @param cp
     * @return
     */
    private boolean isCRorLF(int cp)
    {
      return cp == CR || cp == LF;
    }


    /**
     * get the start of this strings content.
     * if in single line string this returns _pos,
     * in multi line string the first character belonging
     * to the multiline string.
     *
     * NYI: CLEANUP: don't set multiLineIndentation here... but in constructor
     * @return
     */
    private Optional<Integer> startOfStringContent()
    {
      var pos = _pos;
      if (atMultiLineStringDelimiter(getPos(pos) - 1) && _multiLineIndentation.isEmpty())
        {
          pos = advance(pos, 2); // skip fat quotation
          while(raw(pos) != END_OF_FILE && (isCRorLF(raw(pos)) || raw(pos) == SP))
            {
              pos = advance(pos, 1);
            }
          if (raw(pos) == END_OF_FILE)
            {
              Errors.unterminatedString(sourcePos(), Lexer.this.sourcePos(_stringStart));
            }
          if (lineNum(getPos(pos)) != lineNum(_stringStart) + 1)
            {
              Errors.expectedIndentedStringInFirstLineAfterFatQuotation(sourcePos(_stringStart), sourcePos(getPos(pos)));
            }
          _multiLineIndentation = Optional.of(column(pos));
        }
      return pos;
    }


    /**
     * convenience method to get the byte position of
     * this string lexer as an int.
     *
     * @param pos
     * @return the byte position
     */
    private int getPos(Optional<Integer> pos)
    {
      return pos.orElse(bytePos());
    }


    /**
     * Advance the StringLexer by n codepoints.
     *
     * @param pos
     * @param n
     * @return
     */
    private Optional<Integer> advance(Optional<Integer> pos, int n)
    {
      for (int i = 0; i < n; i++)
        {
          if (pos.isEmpty())
          {
            nextCodePoint();
          }
          pos = pos.map(p -> p + codePointSize(p));
        }
      return pos;
    }


    /**
     * get the column of this pos.
     */
    int column(Optional<Integer> pos)
    {
      return codePointIndentation(getPos(pos));
    }


    /**
     * In multiline strings any space before multiLineIndentation is ignored.
     *
     * @param pos
     * @return
     */
    private boolean skipped(Optional<Integer> pos)
    {
      return _multiLineIndentation
        .map(indentation ->
          {
            var codepoint = raw(pos);
            var isIgnoredSpace = (codepoint == SP && column(pos) < indentation);
            return
              isIgnoredSpace
              // newlines are normalized to LF in multiline strings.
              || codepoint == CR;
          }
        ).orElse(false);
    }


    /**
     * At start or end of a multiline string?
     *
     * @param pos
     * @return
     */
    private boolean atMultiLineStringDelimiter(int pos)
    {
      return !(pos < 0 || pos+2 >= byteLength())
        && byteAt(pos) == '"'
        && byteAt(pos+1) == '"'
        && byteAt(pos+2) == '"';
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
         _pos.isEmpty());

      int p = curCodePoint();
      switch (_state)
        {
        case EXPR_EXPECTED:
          switch (kind(p))
            {
            case K_LBRACE:
              _braceCount++;
              return Token.t_undefined;
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
   * preceded by an embedded identifier {@code $id} or expression {@code {expr}}.
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
   * followed by an embedded identifier {@code $id} or expression {@code {expr}}.
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
   *
   * @param multiLineIndentation
   */
  Pair<String, Optional<Integer>> string(Optional<Integer> multiLineIndentation)
  {
    if (PRECONDITIONS) require
      (isString(current()));

    var sb = new StringBuilder();
    var s = new StringLexer(sb, multiLineIndentation);
    return new Pair<>(sb.toString(), s._multiLineIndentation);
  }


  /**
   * Return the current token as a string as it appeared in the source code.
   */
  private String tokenAsString()
  {
    if (PRECONDITIONS) require
      (currentNoLimit() != Token.t_eof);

    return asString(tokenPos(), tokenEndPos());
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
    return skip(false, op);
  }


  /**
   * Parse specific t_op.
   *
   * @param atMinIndent
   *
   * @param op the operator
   *
   * @return true iff an t_op op was found and skipped.
   */
  boolean skip(boolean atMinIndent, String op)
  {
    boolean result = false;
    if (isOperator(atMinIndent, op))
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

    var result = curNumLiteral();
    next();
    return result;
  }


}

/* end of file */
