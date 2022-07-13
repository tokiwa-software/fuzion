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
 * Source of class Instance
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis;

import java.nio.ByteBuffer;

import java.util.TreeSet;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;


/**
 * NumericValue represents a numeric value i8..i64, u8..u64, f32..f64.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class NumericValue extends Value implements Comparable<NumericValue>
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The DFA instance we are working with.
   */
  DFA _dfa;


  /**
   * The clazz of this numeric value.
   */
  int _clazz;


  /**
   * The value cast to long
   */
  long _value;


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create Instance of constant numeric value
   *
   * @param dfa the DFA analysis
   *
   * @param clazz the clazz this is an instance of.
   *
   * @param data serialized value
   */
  public NumericValue(DFA dfa, int clazz, ByteBuffer data)
  {
    _dfa = dfa;
    _clazz = clazz;

    _value = switch (_dfa._fuir.getSpecialId(_clazz))
      {
      case c_i8   -> data.get      ();
      case c_i16  -> data.getShort ();
      case c_i32  -> data.getInt   ();
      case c_i64  -> data.getLong  ();
      case c_u8   -> data.get      () & 0xff;
      case c_u16  -> data.getChar  ();
      case c_u32  -> data.getInt   ();
      case c_u64  -> data.getLong  ();
      case c_f32  -> Float.floatToIntBits   (data.getFloat ());
      case c_f64  -> Double.doubleToLongBits(data.getDouble());
      default     -> { check(false); yield 0; }
      };
  }


  /**
   * Create Instance of constant numeric value.
   *
   * @param dfa the DFA analysis
   *
   * @param clazz the clazz this is an instance of.
   *
   * @param v the value, cast to long.
   */
  public NumericValue(DFA dfa, int clazz, long v)
  {
    _dfa = dfa;
    _clazz = clazz;
    _value = v;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Compare this to another NumericValue.
   */
  public int compareTo(NumericValue other)
  {
    return
      _clazz < other._clazz ? -1 :
      _clazz > other._clazz ? +1 :
      _value < other._value ? -1 :
      _value > other._value ? -1 : 0;
  }


  /**
   * Get this' value using the specified type.
   */
  long   i8 () { return _value; }
  long   i16() { return _value; }
  long   i32() { return _value; }
  long   i64() { return _value; }
  long   u8 () { return _value; }
  long   u16() { return _value; }
  long   u32() { return _value; }
  long   u64() { return _value; }
  float  f32() { return Float .intBitsToFloat  ((int) _value); }
  double f64() { return Double.longBitsToDouble(      _value); }


  /**
   * Create a value set consisting of this value and 'v'.
   */
  public Value join(Value v)
  {
    if (v instanceof NumericValue nv)
      {
        if (CHECKS) check
          (_clazz == nv._clazz);

        var r = switch (_dfa._fuir.getSpecialId(_clazz))
          {
          case c_i8   -> i8 () == nv.i8 ();
          case c_i16  -> i16() == nv.i16();
          case c_i32  -> i32() == nv.i32();
          case c_i64  -> i64() == nv.i64();
          case c_u8   -> u8 () == nv.u8 ();
          case c_u16  -> u16() == nv.u16();
          case c_u32  -> u32() == nv.u32();
          case c_u64  -> u64() == nv.u64();
          case c_f32  -> f32() == nv.f32();
          case c_f64  -> f64() == nv.f64();
          default -> false;
          };
        if (!r)
          {
            System.err.println("Value.join could not join numerics "+this+" and "+nv+"!");
          }
        return this;
      }
    else
      {
        System.err.println("NYI: Value.join: "+this+" and "+v);
        return this;
      }
  }


  /**
   * Create human-readable string from this instance.
   */
  public String toString()
  {
    return _dfa._fuir.clazzAsString(_clazz) + ":" +
      switch (_dfa._fuir.getSpecialId(_clazz))
      {
      case c_i8   -> i8 ();
      case c_i16  -> i16();
      case c_i32  -> i32();
      case c_i64  -> i64();
      case c_u8   -> u8 ();
      case c_u16  -> u16();
      case c_u32  -> u32();
      case c_u64  -> u64();
      case c_f32  -> f32();
      case c_f64  -> f64();
      default -> "?!?";
      };
  }

}

/* end of file */
