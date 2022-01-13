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
 * Source of class SourcePosition
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import java.nio.file.Path;


/**
 * SourcePosition represents a position in a source code file.
 *
 * NYI: SourcePosition is quite expensive and typically allocated often as part
 * of an parsed abstract syntax tree.  Instead, we could use an int or long id
 * consisting of a file id an a byte offset in that file.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class SourcePosition extends ANY implements Comparable<SourcePosition>, HasSourcePosition
{

  /*----------------------------  variables  ----------------------------*/


  /**
   * The source file this position refers to.
   */
  public final SourceFile _sourceFile;

  /**
   * character position in _sourceFile.
   */
  public final int _line;
  public final int _column;


  /**
   * SourcePosition instance for built-in types and features that do not have a
   * source code position.
   */
  public static final SourcePosition builtIn = new SourcePosition(new SourceFile(Path.of("--builtin--"), new byte[0]), 0, 0)
    {
      public boolean isBuiltIn()
      {
        return true;
      }

      String rawFileNameWithPosition()
      {
        return "<built-in>";
      }
    };


  /**
   * SourcePosition instance for source positions that are not available, e.g., for
   * precompiled modules that do not include source code..
   */
  public static final SourcePosition notAvailable = new SourcePosition(new SourceFile(Path.of("--not available--"), new byte[0]), 0, 0)
    {
      public boolean isBuiltIn()
      {
        return true;
      }

      String rawFileNameWithPosition()
      {
        return "<source position not available>";
      }
    };


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create source position for given source file, line and column.
   *
   * @param sourceFile the source file
   *
   * @param l the line number, starting at 1
   *
   * @param c the colun, starting at 1.
   */
  public SourcePosition(SourceFile sourceFile, int l, int c)
  {
    if (PRECONDITIONS) require
      (sourceFile != null);

    this._sourceFile = sourceFile;
    this._line = l;
    this._column = c;
  }


  /**
   * Create source position for given source file and byte position.
   *
   * @param sourceFile the source file
   *
   * @param bytePos the byte position within sourceFile
   */
  public SourcePosition(SourceFile sourceFile, int bytePos)
  {
    if (PRECONDITIONS) require
      (sourceFile != null);

    this._sourceFile = sourceFile;
    this._line = sourceFile.lineNum(bytePos);
    this._column = bytePos - sourceFile.lineStartPos(_line) + 1;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create and return the actual source code position held by this instance.
   */
  public SourcePosition pos()
  {
    /* this not only has a position, it _is_ a position! */
    return this;
  }


  /**
   * Is this a dummy-position for a built-in type?
   */
  public boolean isBuiltIn()
  {
    return false;
  }

  /**
   * Print the source code position to stderr.
   */
  public void show(String msg, String detail)
  {
    Errors.println(fileNameWithPosition() + " " + msg);
    if (!isBuiltIn())
      {
        Errors.println(showInSource());
      }
    if (detail != null && !detail.equals(""))
      {
        Errors.println(detail);
      }
    Errors.println("");
  }


  /**
   * Convert this to a string consisting of fileNameWithPosition() and
   * showInSource().
   */
  public String show()
  {
    return fileNameWithPosition() + "\n" + (isBuiltIn() ? "" : showInSource());
  }


  /**
   * Convert this into a two line string that shows the referenced source code
   * line followed by a line with caret (^) at the relevant position.  The
   * second line is not terminated by LF.
   */
  public String showInSource()
  {
    StringBuilder sb = new StringBuilder();
    sb.append(Terminal.BLUE);
    sb.append(_sourceFile.line(_line));

    // add LF in case this is the last line of a file that does not end in a line break
    if (sb.length() > 0 && sb.charAt(sb.length()-1) != '\n')
      {
        sb.append("\n");
      }
    sb.append(Terminal.YELLOW);
    for (int j=0; j < _column-1; j++)
      {
        sb.append('-');
      }
    sb.append('^');
    sb.append(Terminal.RESET);
    return sb.toString();
  }

  /**
   * Return the name of the file that this source position refers to.
   */
  String fileName()
  {
    return _sourceFile._fileName.toString();
  }

  /**
   * Convert this position to a string of the form "<filename>:<line>:<column>"
   * or "<built-in>" for builtIn position.
   */
  String rawFileNameWithPosition()
  {
    return fileName() + ":" + _line + ":" + _column;
  }

  /**
   * Convert this position to a string of the form "<filename>:<line>:<column>:"
   * or "<built-in>" for builtIn position.
   */
  public String fileNameWithPosition()
  {
    return Terminal.GREEN + rawFileNameWithPosition() + ":" + Terminal.REGULAR_COLOR;
  }


  /**
   * Byte position within _sourceFile
   */
  public int bytePos()
  {
    return _sourceFile.lineStartPos(_line) + _column - 1;
  }


  /**
   * Convert this position to a string of the form
   * "<filename>:<line>:<column>:".
   */
  public String toString()
  {
    return fileNameWithPosition();
  }


  public int compareTo(SourcePosition o)
  {
    int result = fileName().compareTo(o.fileName());
    if (result == 0)
      {
        result = _line < o._line ? -1 : _line > o._line ? +1 : 0;
      }
    if (result == 0)
      {
        result = _column < o._column ? -1 : _column > o._column ? +1 : 0;
      }
    return result;
  }

}

/* end of file */
