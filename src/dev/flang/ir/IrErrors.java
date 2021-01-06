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
 * Source of class IrErrors
 *
 *---------------------------------------------------------------------*/

package dev.flang.ir;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.SourcePosition;


/**
 * IrErrors handles errors in the IR
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class IrErrors extends ANY
{

  /*--------------------------  static fields  --------------------------*/

  /**
   * Error count of only those errors that occured in the IR.
   */
  static int count = 0;


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Record the given error found during compilation.
   */
  public static void error(SourcePosition pos, String msg, String detail)
  {
    if (PRECONDITIONS) require
      (pos != null,
       msg != null,
       detail != null);

    int old = Errors.count();
    Errors.error(pos, msg, detail);
    int delta = Errors.count() - old;  // Errors detects duplicates, so the count might not have changed.
    count += delta;
  }

}

/* end of file */
