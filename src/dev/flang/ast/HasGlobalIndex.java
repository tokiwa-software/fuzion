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
 * Source of class HasGlobalIndex
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.ANY;
import dev.flang.util.Errors;


/**
 * HasGlobalIndex is parent of Fuzion AST elements that can map to a unique global
 * index.  This index is used during the middle end to replace types by actual
 * clazzes.
 *
 * NYI: CLEANUP #2411: Once the middle end works on .fum file data only, this
 * will no longer be needed.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class HasGlobalIndex extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /*
   * Range of global indices that can be used for AST elements created by the
   * parser.  These are initialized by dev.flang.fe.FrontEnd.
   */
  public static int FIRST_GLOBAL_INDEX = -1;
  public static int LAST_GLOBAL_INDEX = -1;


  /*--------------------------  static fields  --------------------------*/


  /**
   * Next global index to be used
   */
  private static int _curGix_ = 0;


  /*-----------------------------  fields  ------------------------------*/


  /**
   * Next global index to be used
   */
  private int _gix = _curGix_++;


  /*------------------------------  methods  ----------------------------*/


  /**
   * Unique global index of this element in the AST.
   */
  public int globalIndex()
  {
    var result = _gix + FIRST_GLOBAL_INDEX;
    if (result > LAST_GLOBAL_INDEX)
      {
        Errors.fatal("NYI: Implementation limitation: Max number of source code expressions reached: count is " + _gix +" max supported is "+(LAST_GLOBAL_INDEX - FIRST_GLOBAL_INDEX)+".");
      }
    return _gix + FIRST_GLOBAL_INDEX;
  }


  /**
   * Reset static fields
   */
  public static void reset()
  {
    _curGix_ = 0;
  }

}

/* end of file */
