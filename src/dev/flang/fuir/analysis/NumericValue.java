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
   * The value as a byte buffer.
   */
  ByteBuffer _data;


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create Instance of given clazz
   *
   * @param clazz the clazz this is an instance of.
   */
  public NumericValue(DFA dfa, int clazz, ByteBuffer data)
  {
    _dfa = dfa;
    _clazz = clazz;
    _data = data;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Compare this to another NumericValue.
   */
  public int compareTo(NumericValue other)
  {
    return
      _clazz < other._clazz ? -1 :
      _clazz > other._clazz ? +1 : _data.compareTo(other._data);
  }


  /**
   * Get this' value using the specified type.
   */
  long   i8 () { _data.rewind(); return _data.get      (); }
  long   i16() { _data.rewind(); return _data.getShort (); }
  long   i32() { _data.rewind(); return _data.getInt   (); }
  long   i64() { _data.rewind(); return _data.getLong  (); }
  long   u8 () { _data.rewind(); return _data.get      () & 0xff; }
  long   u16() { _data.rewind(); return _data.getChar  (); }
  long   u32() { _data.rewind(); return _data.getInt   (); }
  long   u64() { _data.rewind(); return _data.getLong  (); }
  float  f32() { _data.rewind(); return _data.getFloat (); }
  double f64() { _data.rewind(); return _data.getDouble(); }


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
