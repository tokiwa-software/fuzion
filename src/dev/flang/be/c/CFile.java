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
 * Source of class CFile
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.flang.util.ANY;


/**
 * CFile handles writing a C source file created by the C backend
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class CFile extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Writer to create the C code to.
   */
  private PrintWriter _cout;


  /**
   * Indentation counter for generated code, just for code readability.
   */
  private int _c_indentation = 0;


  /**
   * Column within current line of last character written to C code.  0 for
   * beginning of the line.
   */
  private int _c_col = 0;


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create C code backend for given intermediate code.
   *
   * @param fuir the intermediate code.
   *
   * @param opt options to control compilation.
   */
  public CFile(String cname) throws IOException
  {
    _cout = new PrintWriter(Files.newBufferedWriter(Path.of(cname), StandardCharsets.UTF_8));
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Close the C file created by this.
   */
  public void close()
  {
    _cout.close();
    _cout = null;
  }


  /**
   * helper for c_print: Print a string that does not contain any LF.  If at
   * beginning of line, indent _c_indentation times.
   *
   * @param s the string
   */
  private void printSimpleString(String s)
  {
    if (PRECONDITIONS) require
      (s.indexOf("\n") < 0);

    var l = s.length();
    if (l > 0)
      {
        if (_c_col == 0)
          {
            for (int i = 0; i < _c_indentation; i++)
              {
                _cout.print(" ");
              }
          }
        _cout.print(s);
        _c_col += l;
      }
  }


  /**
   * Print the given C string, indenting new lines _c_indentation times.
   *
   * @param s the string to print, may contain '\n'.
   */
  public void print(String s)
  {
    int start = 0;
    do
      {
        int end = s.indexOf('\n', start);
        if (end >= 0)
          {
            printSimpleString(s.substring(start, end));
            _cout.print("\n");
            _c_col = 0;
            start = end + 1;
          }
        else
          {
            printSimpleString(s.substring(start));
            start = s.length();
          }
      }
    while (start < s.length());
  }


  /**
   * Print the given CStmnt followed by a semicolon and LF.
   *
   * @param s the statement to print;
   */
  public void print(CStmnt s)
  {
    var sb = new CString();
    s.code(sb);
    if (s.needsSemi())
      {
        sb.append(";\n");
      }
    print(sb.toString());
  }


  /**
   * Print the given CExpr.
   *
   * @param e the expression to print;
   */
  public void printExpr(CExpr e)
  {
    print(e.code());
  }


  /**
   * Print the given C string, indenting new lines _c_indentation times, follwed
   * by LF.
   *
   * @param s the string to print, may contain '\n'.
   */
  public void println(String s)
  {
    print(s + "\n");
  }


  /**
   * Increase indentation level by 1
   */
  public void indent()
  {
    _c_indentation ++;
  }


  /**
   * Decrease indentation level by 1
   */
  public void unindent()
  {
    if (PRECONDITIONS)
      require
        (_c_indentation > 0);

    _c_indentation --;
  }

}

/* end of file */
