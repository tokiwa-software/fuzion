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
 * Source of class Pretty
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools;

import java.nio.file.Path;

import dev.flang.parser.Lexer;

import dev.flang.util.ANY;
import dev.flang.util.Intervals;
import dev.flang.util.SourceFile;
import dev.flang.util.Terminal;


/**
 * Pretty is a pretty printer for Fuzion source code
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
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
    var style = Terminal.RESET;
    for (int j = 0; j < f.byteLength(); j++)
      {
        var t = i.get(j);
        var newStyle = style(t);
        if (newStyle != style)
          {
            if (style != Terminal.RESET)
              {
                System.out.print(Terminal.RESET);
              }
            style = newStyle;
            System.out.print(style);
          }
        System.out.write(f.byteAt(j));
      }
    if (style != Terminal.RESET)
      {
        System.out.print(Terminal.RESET);
      }
  }



  /**
   * Get the style a given token is to be printed in.
   *
   * @param t a token
   *
   * @return the control sequence setting the style.
   */
  private String style(Lexer.Token t)
  {
    var result = Terminal.RESET;
    if (t.isKeyword())
      {
        result = Terminal.BOLD_PURPLE;
      }
    else
      {
        switch (t)
          {
          case t_comment: result = Terminal.RED; break;
          case t_ident  : result = Terminal.BLUE; break;
          case t_integer:
          case t_op     : result = Terminal.INTENSE_BLACK; break;
          default       :
            if (Lexer.isString(t))
              {
                result = Terminal.YELLOW; break;
              }
            break;
          }
      }
    return result;
  }


}

/* end of file */
