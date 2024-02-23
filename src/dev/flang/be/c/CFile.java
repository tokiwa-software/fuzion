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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import dev.flang.util.ANY;
import dev.flang.util.Errors;


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


  /**
   * The temporary file to write the generated C-code to.
   */
  private File tempFile;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * constructor to instantiate a CFile
   *
   * @param name the name of the binary
   */
  public CFile(String name)
  {
    try
      {
        tempFile = File.createTempFile("fuzion_"+ name + "_", ".c");
        _cout = new PrintWriter(Files.newBufferedWriter(tempFile.toPath(), StandardCharsets.UTF_8));
      }
    catch (IOException io)
      {
        Errors.fatal("C backend I/O error",
                     "While creating temporary file, received I/O error '" + io + "'");
      }
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
    s.codeSemi(sb);
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
   * Print the given C string, indenting new lines _c_indentation times, followed
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
    if (PRECONDITIONS) require
      (_c_indentation > 0);

    _c_indentation --;
  }


  /**
   * @return the absolute path of the file
   * the c-code is written to.
   */
  public String fileName()
  {
    return tempFile.getAbsolutePath();
  }

}

/* end of file */
