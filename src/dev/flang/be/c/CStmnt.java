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
 * Source of class CStmnt
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;

import dev.flang.util.ANY;

/**
 * CSmnt provides infrastructure to generate C statements
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
abstract class CStmnt extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  producers  ----------------------------*/


  /*-------------------------  static methods  --------------------------*/


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create the C code corresponding to this statement
   *
   * @param sb will be used to append the code to
   */
  abstract void code(StringBuilder sb);


  /**
   * Convenience function to create the C code as a string. Try to avoid since
   * this causes additional allocation and copying.  Prefer to use
   * code(StringBuilder).
   *
   * @return the C code of this CExpr
   */
  String code()
  {
    var sb = new StringBuilder();
    code(sb);
    return sb.toString();
  }


  /**
   * Create String for debugging-output.
   */
  public String toString()
  {
    return "C-Statement '" + code() + "'";
  }

}

/* end of file */
