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
 * Source of class f64Value
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import dev.flang.air.Clazz;
import dev.flang.air.Clazzes;

import dev.flang.util.ANY;


/**
 * f64Value is a value of type f64
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class f64Value extends Value
{


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  private double _val;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param i
   */
  public f64Value(double val)
  {
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
    return Double.toString(_val);
  }


  /**
   * For a value of type f64, return the value.
   *
   * @return the f64 value
   */
  public double f64Value()
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
    if (PRECONDITIONS)
      require(size == 2);

    var l = Double.doubleToRawLongBits(_val);
    slot.container.nonrefs[slot.offset    ] = (int) l;
    slot.container.nonrefs[slot.offset + 1] = (int) (l >> 32);
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
    if (expected != Clazzes.f64.getIfCreated())
      {
        throw new Error("f64 value not allowed for clazz " + expected);
      }
  }

}

/* end of file */
