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
 * Source of class Boxed
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import dev.flang.air.Clazz;
import dev.flang.air.Clazzes;

import dev.flang.util.Errors;


/**
 * Boxed represents a value type instance that was boxed to create a ref type.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Boxed extends ValueWithClazz
{

  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  public Clazz _valueClazz;


  /**
   *
   */
  public Value _contents;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param clazz
   *
   * @param outer
   */
  public Boxed(Clazz clazz, Clazz valueClazz, Value contents)
  {
    super(clazz);

    if (PRECONDITIONS) require
      (clazz != null,
       clazz.isBoxed());

    this._contents = contents;
    this._valueClazz = valueClazz;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create a copy (clone) of this value.  Used for boxing values into
   * ref-types.
   */
  Instance cloneValue(Clazz cl)
  {
    if (PRECONDITIONS) require
      (false);

    throw new Error("cannot clone boxed value");
  }


  /**
   * For a value of type i8, return the value.
   *
   * @return the i8 value
   */
  public int i8Value()
  {
    if (PRECONDITIONS) require
      (_valueClazz == Clazzes.i8    .getIfCreated());

    return _contents.i8Value();
  }


  /**
   * For a value of type i16, return the value.
   *
   * @return the i16 value
   */
  public int i16Value()
  {
    if (PRECONDITIONS) require
      (_valueClazz == Clazzes.i16    .getIfCreated());

    return _contents.i16Value();
  }


  /**
   * For a value of type i32, return the value.
   *
   * @return the i32 value
   */
  public int i32Value()
  {
    if (PRECONDITIONS) require
      (_valueClazz == Clazzes.i32    .getIfCreated());

    return _contents.i32Value();
  }


  /**
   * For a value of type i64, return the value.
   *
   * @return the i64 value
   */
  public long i64Value()
  {
    if (PRECONDITIONS) require
      (_valueClazz == Clazzes.i64    .getIfCreated());

    return _contents.i64Value();
  }


  /**
   * For a value of type u8, return the value.
   *
   * @return the u8 value
   */
  public int u8Value()
  {
    if (PRECONDITIONS) require
      (_valueClazz == Clazzes.u8    .getIfCreated());

    return _contents.u8Value();
  }



  /**
   * For a value of type u16, return the value.
   *
   * @return the u16 value
   */
  public int u16Value()
  {
    if (PRECONDITIONS) require
      (_valueClazz == Clazzes.u16    .getIfCreated());

    return _contents.u16Value();
  }


  /**
   * For a value of type u32, return the value.
   *
   * @return the u32 value
   */
  public int u32Value()
  {
    if (PRECONDITIONS) require
      (_valueClazz == Clazzes.u32    .getIfCreated());

    return _contents.u32Value();
  }


  /**
   * For a value of type u64, return the value.
   *
   * @return the u64 value
   */
  public long u64Value()
  {
    if (PRECONDITIONS) require
      (_valueClazz == Clazzes.u64    .getIfCreated());

    return _contents.u64Value();
  }


  /**
   * For a value of type f32, return the value.
   *
   * @return the f32 value
   */
  public float f32Value()
  {
    if (PRECONDITIONS) require
      (_valueClazz == Clazzes.f32    .getIfCreated());

    return _contents.f32Value();
  }


  /**
   * For a value of type f64, return the value.
   *
   * @return the f64 value
   */
  public double f64Value()
  {
    if (PRECONDITIONS) require
      (_valueClazz == Clazzes.f64    .getIfCreated());

    return _contents.f64Value();
  }


  /**
   * For a value of type bool, return the value.
   *
   * @return the bool value
   */
  public boolean boolValue()
  {
    if (PRECONDITIONS) require
      (_valueClazz == Clazzes.c_TRUE .getIfCreated() ||
       _valueClazz == Clazzes.c_FALSE.getIfCreated() ||
       _valueClazz == Clazzes.bool   .getIfCreated());

    return _contents.boolValue();
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
    return _contents.at(c, off);
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "boxed[" + _clazz + "]" + this.hashCode();
  }


  /**
   * dump
   */
  public void dump()
  {
    System.out.print("BOXED: ");
    if (this._contents instanceof Instance i) i.dump();
  }

}

/* end of file */
