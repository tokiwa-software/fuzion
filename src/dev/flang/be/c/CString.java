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
 * Source of class CString
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;

import dev.flang.util.ANY;
import dev.flang.util.List;


/**
 * CString provides a way to create C code as a Java String.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
class CString extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  private final StringBuilder _sb = new StringBuilder();



  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create new empty CString
   */
  public CString()
  {
  }



  /*-----------------------------  methods  -----------------------------*/


  /**
   * Append the given string
   */
  CString append(String s)
  {
    _sb.append(s);
    return this;
  }


  /**
   * Append the given char
   */
  CString append(char c)
  {
    _sb.append(c);
    return this;
  }


  /**
   * Append the given integer as decimal number
   */
  CString append(int i)
  {
    _sb.append(i);
    return this;
  }


  /**
   * Append the given long as decimal number
   */
  CString append(long l)
  {
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
