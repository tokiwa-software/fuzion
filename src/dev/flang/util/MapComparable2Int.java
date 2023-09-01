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
 * Source of class MapComparable2Int
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;



/**
 * Map2Int gives an efficient mapping from a comparable instance to int
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class MapComparable2Int<T extends Comparable> extends Map2Int<T>
{


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create map with 0 as base offset.
   */
  public MapComparable2Int()
  {
    this(0);
  }


  /**
   * Create map with given base offset.
   */
  public MapComparable2Int(int base)
  {
    super(base);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * redefined to indicate that T is Comparable.
   */
  protected boolean isComparable()
  {
    return true;
  }

}

/* end of file */
