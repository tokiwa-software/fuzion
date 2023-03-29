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
 * Source of class CString
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;

import dev.flang.util.ANY;
import dev.flang.util.List;


/**
 * CString provides a way to create C code as a Java String.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class CString extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * String representing one level of indentation.
   */
  private final String INDENTATION = "  ";


  /*----------------------------  variables  ----------------------------*/


  /**
   * Underlying StringBuilder.
   */
  private final StringBuilder _sb;


  /**
   * Indentation level, 0 means no indentation.
   */
  private final int _level;


  /**
   * CString for next indentation level
   */
  private CString _indent;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create new empty CString
   */
  public CString()
  {
    this(new StringBuilder(), 0);
  }


  /**
   * Create new empty CString for existing underlying StringBuilder and given
   * indentation level.
   */
  private CString(StringBuilder sb,
                  int level)
  {
    _sb = sb;
    _level = level;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Get the CString for the next indentation level.
   */
  CString indent()
  {
    if (_indent == null)
      {
        _indent = new CString(_sb, _level + 1);
      }
    return _indent;
  }


  /**
   * Check if indentation is needed when appending a non-LF character and append
   * the indentation to underlying StringBuilder if this is the case.
   */
  private void doIndent()
  {
    var l = _sb.length();
    if (l > 0 && _sb.charAt(l-1) == '\n')
      {
        for (int i = 0; i < _level; i++)
          {
            _sb.append(INDENTATION);
          }
      }
  }


  /**
   * Append the given string
   */
  CString append(String s)
  {
    for (int i = 0; i < s.length(); i++)
      {
        append(s.charAt(i));
      }
    return this;
  }


  /**
   * Append the given char
   */
  CString append(char c)
  {
    if (c != '\n')
      {
        doIndent();
      }
    _sb.append(c);
    return this;
  }


  /**
   * Append the given integer as decimal number
   */
  CString append(int i)
  {
    doIndent();
    _sb.append(i);
    return this;
  }


  /**
   * Append the given long as decimal number
   */
  CString append(long l)
  {
    doIndent();
    _sb.append(l);
    return this;
  }


  /**
   * Create Java String from this.
   */
  public String toString()
  {
    return _sb.toString();
  }

}

/* end of file */
