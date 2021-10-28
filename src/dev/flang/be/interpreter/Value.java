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
 * Source of class Value
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import dev.flang.air.Clazz;
import dev.flang.air.Clazzes;

import dev.flang.util.ANY;


/**
 * Value <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class Value extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Dummy value to be returned by Expr.execute for the case that the
   * exression does not produce a value
   */
  public static Value NO_VALUE = new Value() { };


  /**
   * Dummy value to be returned by intrinsic features that return an empty self.
   */
  public static Value EMPTY_VALUE = new Value()
    {
      void storeNonRef(LValue slot, int size)
      {
        // treat as NOP.
      }
    };


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  public Value()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "UNKNOWN VALUE";
  }


  /**
   * For a value of type i8, return the value.
   *
   * @return the i8 value
   */
  public int i8Value()
  {
    throw new Error("this is not of type i8Value, but " + getClass());
  }


  /**
   * For a value of type i16, return the value.
   *
   * @return the i16 value
   */
  public int i16Value()
  {
    throw new Error("this is not of type i16Value, but " + getClass());
  }


  /**
   * For a value of type i32, return the value.
   *
   * @return the i32 value
   */
  public int i32Value()
  {
    throw new Error("this is not of type i32Value, but " + getClass());
  }


  /**
   * For a value of type i64, return the value.
   *
   * @return the i64 value
   */
  public long i64Value()
  {
    throw new Error("this is not of type i64Value, but " + getClass());
  }


  /**
   * For a value of type u8, return the value.
   *
   * @return the u8 value
   */
  public int u8Value()
  {
    throw new Error("this is not of type u8Value, but " + getClass());
  }


  /**
   * For a value of type u16, return the value.
   *
   * @return the u16 value
   */
  public int u16Value()
  {
    throw new Error("this is not of type u16Value, but " + getClass());
  }


  /**
   * For a value of type u32, return the value.
   *
   * @return the u32 value
   */
  public int u32Value()
  {
    throw new Error("this is not of type u32Value, but " + getClass());
  }


  /**
   * For a value of type u64, return the value.
   *
   * @return the u64 value
   */
  public long u64Value()
  {
    throw new Error("this is not of type u64Value, but " + getClass());
  }


  /**
   * For a value of type f32, return the value.
   *
   * @return the f32 value
   */
  public float f32Value()
  {
    throw new Error("this is not of type f32Value, but " + getClass());
  }


  /**
   * For a value of type f64, return the value.
   *
   * @return the f64 value
   */
  public double f64Value()
  {
    throw new Error("this is not of type f64Value, but " + getClass());
  }


  /**
   * For a value of type bool, return the value.
   *
   * @return the bool value
   */
  public boolean boolValue()
  {
    throw new Error("this is not of type boolValue, but " + getClass());
  }


  /**
   * Convert this value into an LValue with the given offset.
   *
   * @param c the clazz of the value, for debugging only
   *
   * @param off the offset of the value within this
   *
   * @return the LValue to rev
   */
  public LValue at(Clazz c, int off)
  {
    throw new Error("Cannot create LValue from " + getClass());
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
    throw new Error("Cannot store " + getClass() + " as non-ref");
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
    throw new Error("value " + this + " not allowed for clazz "+ expected);
  }


  /**
   * Return the instance this value contains.  If this is an Instance, return
   * this, if this is an LValue containing an instance, get that instance.
   */
  Instance instance()
  {
    throw new Error("value "+ this + " of class " + getClass() + " is not an instance");
  }


  /**
   * Return the ArrayData this value contains.  If this is an ArrayData, return
   * this, if this is an LValue containing an ArrayData, get that ArrayData.
   */
  ArrayData arrayData()
  {
    throw new Error("value "+ this + " of class " + getClass() + " is not an ArrayData");
  }


}

/* end of file */
