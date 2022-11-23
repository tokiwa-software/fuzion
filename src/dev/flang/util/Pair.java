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
 * Source code of class Pair
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;


/**
 * Pair contains two values.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Pair<A,B>
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The first value.
   */
  public final A _v0;


  /**
   * The second value.
   */
  public final B _v1;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create a Pair of v0 and v1.
   */
  public Pair(A v0, B v1)
  {
    _v0 = v0;
    _v1 = v1;
  }

}

/* end of file */
