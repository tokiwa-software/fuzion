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
 * Source of class NumericValue
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;

import java.nio.ByteBuffer;

import dev.flang.util.LongMap;

/**
 * NumericValue represents a numeric value i8..i64, u8..u64, f32..f64.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class NumericValue extends Value
{


  /*-----------------------------  classes  -----------------------------*/


  /*-------------------------  static methods  --------------------------*/


  /**
   * Create Instance of constant numeric value
   *
   * @param dfa the DFA analysis
   *
   * @param clazz the clazz this is an instance of.
   *
   * @param data serialized value
   */
  public static NumericValue create(DFA dfa, int clazz, ByteBuffer data)
  {
    var v = switch (dfa._fuir.getSpecialClazz(clazz))
      {
      case c_i8   -> (long) data.get      ();
      case c_i16  -> (long) data.getShort ();
      case c_i32  -> (long) data.getInt   ();
      case c_i64  -> (long) data.getLong  ();
      case c_u8   -> (long) data.get      () & 0xff;
      case c_u16  -> (long) data.getChar  ();
      case c_u32  -> (long) data.getInt   ();
      case c_u64  ->        data.getLong  ();
      case c_f32  -> (long) Float .floatToIntBits  (data.getFloat ());
      case c_f64  ->        Double.doubleToLongBits(data.getDouble());
      default     -> { check(false); yield null; }
      };

    return create(dfa, clazz, v);
  }


  /**
   * Create Instance of constant numeric value
   *
   * @param dfa the DFA analysis
   *
   * @param clazz the clazz this is an instance of.
   *
   * @param vlong the value, cast to long.
   */
  public static NumericValue create(DFA dfa, int clazz, long vlong)
  {
    Long vLong = vlong;
    var clazz_id = dfa._fuir.getSpecialClazz(clazz).ordinal();
    var values = dfa._numericValues.getIfExists(clazz_id);
    if (values == null)
      {
        values = new LongMap<>();
        dfa._numericValues.force(clazz_id, values);
      }
    NumericValue res;
    res = values.get(vLong);
    if (res == null)
      {
        res = new NumericValue(dfa, clazz, vLong);
        values.put(vLong, res);
      }
    return res;
  }


  /**
   * Create Instance of unknown numeric value
   *
   * @param dfa the DFA analysis
   *
   * @param clazz the clazz this is an instance of.
   */
  public static NumericValue create(DFA dfa, int clazz)
  {
    var clazz_id = dfa._fuir.getSpecialClazz(clazz).ordinal();
    var res = dfa._numericValuesAny.getIfExists(clazz_id);
    if (res == null)
      {
        res = new NumericValue(dfa, clazz, null);
        dfa._numericValuesAny.force(clazz_id, res);
      }
    return res;
  }


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The DFA instance we are working with.
   */
  DFA _dfa;


  /**
   * The value cast to long, null if not known.
   */
  Long _value;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create Instance of constant numeric value.
   *
   * @param dfa the DFA analysis
   *
   * @param clazz the clazz this is an instance of.
   *
   * @param v the value, wrapped in Long or null if unknown.
   */
  private NumericValue(DFA dfa, int clazz, Long v)
  {
    super(clazz);

    _dfa = dfa;
    _value = v;
    dfa.makeUnique(this);
  }


  /*-----------------------------  methods  -----------------------------*/


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
  float  f32() { return Float .intBitsToFloat  ((int) _value.longValue()); }
  double f64() { return Double.longBitsToDouble(      _value); }


  /**
   * Get set of values of given field within this instance.
   */
  Value readFieldFromInstance(DFA dfa, int field, int site, Context why)
  {
    /* for a numeric value, this can only read the 'val' field, e.g., 'i32.val',
     * which (recursively) contains the numeric value, so we can just return
     * this value: */
    return this;
  }


  /**
   * Create the union of the values 'this' and 'v'. This is called by join()
   * after common cases (same instance, UNDEFINED) have been handled.
   *
   * @param dfa the current analysis context.
   *
   * @param v the value this value should be joined with.
   *
   * @param clazz the clazz of the resulting value. This is usually the same as
   * the clazz of `this` or `v`, unless we are joining `ref` type values.
   */
  @Override
  public Value joinInstances(DFA dfa, Value v, int clazz)
  {
    if (v instanceof NumericValue nv)
      {
        if (CHECKS) check
          (_clazz == nv._clazz);

        var r =
          _value == null || nv._value == null ||
          switch (_dfa._fuir.getSpecialClazz(_clazz))
          {
          case c_i8   -> i8 () == nv.i8 ();
          case c_i16  -> i16() == nv.i16();
          case c_i32  -> i32() == nv.i32();
          case c_i64  -> i64() == nv.i64();
          case c_u8   -> u8 () == nv.u8 ();
          case c_u16  -> u16() == nv.u16();
          case c_u32  -> u32() == nv.u32();
          case c_u64  -> u64() == nv.u64();
          case c_f32  -> f32() == nv.f32();  // NYI: check if this is correct for NaN etc.
          case c_f64  -> f64() == nv.f64();  // NYI: check if this is correct for NaN etc.
          default -> false;
          };
        if (r)
          {
            return this;
          }
        else if (_clazz == nv._clazz)
          {
            return create(_dfa, _clazz);
          }
        else
          {
            return super.joinInstances(dfa, v, clazz);
          }
      }
    else if (v instanceof ValueSet)
      {
        return v.join(dfa, this, clazz);
      }
    else
      {
        return super.joinInstances(dfa, v, clazz);
      }
  }


  /**
   * Add v to the set of values of given field within this instance.
   */
  public void setField(DFA dfa, int field, Value v)
  {
    if (_dfa._fuir.clazzOuterClazz(field) == _clazz)
      {
        // ok, we are setting 'val' field in numeric type
      }
    else
      {
        throw new Error("Value.setField called on class " + this + " (" + getClass() + ") to set "+_dfa._fuir.clazzAsString(field)+" expected " + Instance.class);
      }
  }


  /**
   * Create human-readable string from this instance.
   */
  public String toString()
  {
    return _dfa._fuir.clazzAsString(_clazz) + ":" +
      (_value == null
       ? "--any value--"
       : switch (_dfa._fuir.getSpecialClazz(_clazz))
          {
          case c_i8   -> "" + i8 ();
          case c_i16  -> "" + i16();
          case c_i32  -> "" + i32();
          case c_i64  -> "" + i64();
          case c_u8   -> "" + u8 ();
          case c_u16  -> "" + u16();
          case c_u32  -> "" + u32();
          case c_u64  -> "" + u64();
          case c_f32  -> "" + f32();
          case c_f64  -> "" + f64();
          default -> throw new Error("unexpected case");
          });
  }

}

/* end of file */
