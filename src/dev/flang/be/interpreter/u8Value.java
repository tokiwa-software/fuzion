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
 * Source of class u8Value
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import dev.flang.air.Clazz;
import dev.flang.air.Clazzes;



/**
 * u8Value is a value of type u8
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class u8Value extends Value
{


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  private int _val;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param i
   */
  public u8Value(int val)
  {
    if (PRECONDITIONS) require
      (0 <= val && val <= 0xff);

    _val = val;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return Integer.toUnsignedString(_val);
  }


  /**
   * For a value of type u8, return the value.
   *
   * @return the u8 value
   */
  public int u8Value()
  {
    return _val;
  }


  /**
   * Store this value in a field
   *
   * @param slot the slot that addresses the field this should be stored in.
   *
   * @param size the size of the data to be stored
   */
  void storeNonRef(LValue slot, int size)
  {
    if (size != 1) System.out.println("Assigning "+this);
    if (PRECONDITIONS) require
      (size == 1);

    slot.container.nonrefs[slot.offset] = _val;
  }


  /**
   * Debugging only: Check that this value is valid as the current instance for
   * a feature with given static clazz.
   *
   * @param expected the static clazz of the feature this value is called on.
   *
   * @throws Error in case this does not match the expected clazz
   */
  void checkStaticClazz(Clazz expected)
  {
    if (expected != Clazzes.u8.getIfCreated())
      {
        throw new Error("u8 value not allowed for clazz " + expected);
      }
  }

}

/* end of file */
