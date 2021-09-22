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
 * Source of class Pretty
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import dev.flang.parser.Lexer;

import dev.flang.util.ANY;
import dev.flang.util.Intervals;
import dev.flang.util.SourceFile;
import dev.flang.util.Terminal;


/**
 * Pretty is a pretty printer for Fuzion source code
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Pretty extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for the pretty printer to read from stdin.
   */
  Pretty()
  {
    run(SourceFile.STDIN);
  }


  /**
   * Constructor for the pretty printer to read from given file
   */
  Pretty(String file)
  {
    run(Path.of(file));
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Run the pretty printer on the given file.
   */
  private void run(Path in)
  {
    var l = new Lexer(in)
      {
        public boolean ignore(Lexer.Token t) { return false; }
      };
    var i = intervals(l);
    print(l, i);
  }


  /**
   * Get all the intervals within l that are covered by given tokens.
   *
   * @param l a lexer
   *
   * @param a mapping from byte indices in l to tokens.
   */
  private Intervals<Lexer.Token> intervals(Lexer l)
  {
    var i = new Intervals<Lexer.Token>(null);
    var lastToken = l.current();
    var lastPos = l.pos();
    while (lastToken != Lexer.Token.t_eof)
      {
        var t = l.current();
        var p = l.pos();
        if (lastToken != t)
          {
            i.add(lastPos, p, lastToken);
            lastToken = t;
            lastPos = p;
          }
        l.next();
      }
    return i;
  }


  /**
   * pretty print the given source file using the tokens found
   *
   * @param f the source file
   *
   * @param i mapping from byte indices in f to tokens.
   */
  private void print(SourceFile f,
                     Intervals<Lexer.Token> i)
  {
    Styler s = new TerminalStyler();
    var style = s.plain();
    for (int j = 0; j < f.byteLength(); j = j + f.codePointSize(j))
      {
        var t = i.get(j);
        var newStyle = style(s, t);
        if (newStyle != style)
          {
            System.out.print(style.end());
            style = newStyle;
            System.out.print(style.start());
          }
        byte[] codePointBytes = f.bytesAt(j);
        String codePoint = new String(codePointBytes, StandardCharsets.UTF_8);
        System.out.print(codePoint);
      }
    System.out.print(style.end());
  }


  /**
   * Get the style a given token is to be printed in.
   *
   * @param t a token
   *
   * @return the control sequence setting the style.
   */
  private Style style(Styler s, Lexer.Token t)
  {
    var result = s.plain();
    if (t.isKeyword())
      {
        result = s.keyword();
      }
    else
      {
        switch (t)
          {
          case t_comment   : result = s.comment(); break;
          case t_ident     : result = s.ident();  break;
          case t_numliteral: result = s.integer();  break;
          case t_op        : result = s.op();  break;
          default          :
            if (Lexer.isString(t))
              {
                result = s.string(); break;
              }
            break;
          }
      }
    return result;
  }

  /**
   * Abstract class to generate different styles.
   */
  abstract class Styler
  {
    abstract Style plain();
    abstract Style keyword();
    abstract Style comment();
    abstract Style ident();
    abstract Style integer();
    abstract Style op();
    abstract Style string();
  }

  /**
   * Abstract class to generate start / end sequence for a style
   */
  abstract class Style
  {
    abstract String start();
    abstract String end();
  }

  /**
   * Styler using ANSI control sequences.
   */
  class TerminalStyler extends Styler
  {
    Style plain()
    {
      return new Style()
        {
          String start() { return ""; }
          String end() { return ""; }
        };
    }
    Style keyword()
    {
      return new Style()
        {
          String start() { return Terminal.BOLD_PURPLE; }
          String end() { return Terminal.RESET; }
        };
    }
    Style comment()
    {
      return new Style()
        {
          String start() { return Terminal.RED; }
          String end() { return Terminal.RESET; }
        };
    }
    Style ident()
    {
      return new Style()
        {
          String start() { return Terminal.BLUE; }
          String end() { return Terminal.RESET; }
        };
    }
    Style integer()
    {
      return new Style()
        {
          String start() { return Terminal.INTENSE_BLACK; }
          String end() { return Terminal.RESET; }
        };
    }
    Style op()
    {
      return new Style()
        {
          String start() { return Terminal.INTENSE_BLACK; }
          String end() { return Terminal.RESET; }
        };
    }
    Style string()
    {
      return new Style()
        {
          String start() { return Terminal.YELLOW; }
          String end() { return Terminal.RESET; }
        };
    }
  }


}

/* end of file */
