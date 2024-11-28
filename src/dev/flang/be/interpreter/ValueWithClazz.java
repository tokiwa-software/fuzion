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
 * Source of class ValueWithClazz
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;


/**
 * ValueWithClazz represents a value that is equipped with an instance of
 * clazz that  describes its type.
 *
 * This instance is used for debugging and, if it is an isRef() or
 * isDynamicOuterRef(), for dynamic binding.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class ValueWithClazz extends Value
{

  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The clazz of this value.
   */
  public final int _clazz;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a value with the given clazz.
   */
  ValueWithClazz(int clazz)
  {
    if (PRECONDITIONS) require
      (clazz > 0);
    this._clazz = clazz;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * clazz returns the clazz of a dynamic instance
   *
   * @return the clazz
   */
  public int clazz()
  {
    return _clazz;
  }


  @Override
  public String toString()
  {
    return fuir().clazzAsString(_clazz);
  }


  @Override
  protected Object toNative()
  {
    return switch (fuir().getSpecialClazz(_clazz))
      {
      case c_i8 -> (byte)i8Value();
      case c_i16 -> (short)i16Value();
      case c_i32 -> i32Value();
      case c_i64 -> i64Value();
      case c_u8 -> (byte)u8Value();
      case c_u16 -> (char)u16Value();
      case c_u32 -> u32Value();
      case c_u64 -> u64Value();
      case c_f32 -> f32Value();
      case c_f64 -> f64Value();
      case c_bool -> boolValue();
      default -> super.toNative();
      };
  }


}

/* end of file */
