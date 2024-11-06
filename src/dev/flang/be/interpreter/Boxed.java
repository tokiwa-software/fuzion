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

import dev.flang.fuir.FUIR;

/**
 * Boxed represents a value type instance that was boxed to create a ref type.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Boxed extends ValueWithClazz
{

  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  public int _valueClazz;


  /**
   *
   */
  public Value _contents;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  public Boxed(int clazz, int valueClazz, Value contents)
  {
    super(clazz);

    if (PRECONDITIONS) require
      (clazz > 0,
       fuir().clazzIsBoxed(clazz));

    this._contents = contents;
    this._valueClazz = valueClazz;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create a copy (clone) of this value.  Used for boxing values into
   * ref-types.
   */
  Instance cloneValue(int cl)
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
      (_valueClazz == fuir().clazz(FUIR.SpecialClazzes.c_i8));

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
      (_valueClazz == fuir().clazz(FUIR.SpecialClazzes.c_i16));

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
      (_valueClazz == fuir().clazz(FUIR.SpecialClazzes.c_i32));

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
      (_valueClazz == fuir().clazz(FUIR.SpecialClazzes.c_i64));

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
      (_valueClazz == fuir().clazz(FUIR.SpecialClazzes.c_u8));

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
      (_valueClazz == fuir().clazz(FUIR.SpecialClazzes.c_u16));

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
      (_valueClazz == fuir().clazz(FUIR.SpecialClazzes.c_u32));

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
      (_valueClazz == fuir().clazz(FUIR.SpecialClazzes.c_u64));

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
      (_valueClazz == fuir().clazz(FUIR.SpecialClazzes.c_f32));

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
      (_valueClazz == fuir().clazz(FUIR.SpecialClazzes.c_f64));

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
      (_valueClazz == fuir().clazz(FUIR.SpecialClazzes.c_true_) ||
       _valueClazz == fuir().clazz(FUIR.SpecialClazzes.c_false_) ||
       _valueClazz == fuir().clazz(FUIR.SpecialClazzes.c_bool));

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
  public LValue at(int c, int off)
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
    return "boxed[" + fuir().clazzAsStringHuman(_clazz) + "]" + this.hashCode();
  }

}

/* end of file */
