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

package dev.flang.be.interpreter;

import dev.flang.fuir.FUIR;

/**
 * Instance <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Instance extends ValueWithClazz
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Just for debugging: value used for uninitialized non-ref data
   */
  static final int UNINITIALIZED_INT = -555555555;


  /*----------------------------  variables  ----------------------------*/



  /**
   *
   */
  public Value[] refs;

  /**
   *
   */
  public int[] nonrefs;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param clazz
   */
  public Instance(int clazz)
  {
    super(clazz);

    if (PRECONDITIONS) require
      (clazz > 0);

    int sz = Layout.get(clazz).size();
    this.refs = new Value[sz];
    this.nonrefs = new int[sz];
    for (int i = 0; i<sz; i++)
      {
        this.nonrefs[i] = UNINITIALIZED_INT;
      }
  }


  /**
   * Constructor used by LValue.cloneValue() to create a clone of a value
   * embedded in another Instance.
   *
   * @param clazz the new, unboxed clazz of the result
   *
   * @param from the original Instance.
   *
   * @param offset the position of the value within from.
   */
  Instance(int clazz, Instance from, int offset)
  {
    this(clazz);
    int sz = Layout.get(clazz).size();
    for (int i = 0; i<sz; i++)
      {
        this.nonrefs[i] = from.nonrefs[offset + i];
        this.refs   [i] = from.refs   [offset + i];
      }
  }


  /**
   * Constructor used by cloneValue() to create a clone of this Instance.
   *
   * @param clazz the new, unboxed clazz of the result
   *
   * @param from the original Instance.
   */
  private Instance(int clazz, Instance from)
  {
    this(clazz, from, 0);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create a copy (clone) of this value.  Used for boxing values into
   * ref-types.
   */
  Instance cloneValue(int cl)
  {
    if (PRECONDITIONS) require
      (_clazz == cl,
       !fuir().clazzIsRef(cl));

    return new Instance(cl, this);
  }


  /**
   * For a value of type i8, return the value.
   *
   * @return the i8 value
   */
  public int i8Value()
  {
    if (PRECONDITIONS) require
      (_clazz == fuir().clazz(FUIR.SpecialClazzes.c_i8) ||
       fuir().clazzAsValue(_clazz) == fuir().clazz(FUIR.SpecialClazzes.c_i8));

    return nonrefs[0];
  }


  /**
   * For a value of type i16, return the value.
   *
   * @return the i16 value
   */
  public int i16Value()
  {
    if (PRECONDITIONS) require
      (_clazz == fuir().clazz(FUIR.SpecialClazzes.c_i16) ||
       fuir().clazzAsValue(_clazz) == fuir().clazz(FUIR.SpecialClazzes.c_i16));

    return nonrefs[0];
  }


  /**
   * For a value of type i32, return the value.
   *
   * @return the i32 value
   */
  public int i32Value()
  {
    if (PRECONDITIONS) require
      (_clazz == fuir().clazz(FUIR.SpecialClazzes.c_i32) ||
       fuir().clazzAsValue(_clazz) == fuir().clazz(FUIR.SpecialClazzes.c_i32));

    return nonrefs[0];
  }


  /**
   * For a value of type i64, return the value.
   *
   * @return the i64 value
   */
  public long i64Value()
  {
    if (PRECONDITIONS) require
      (_clazz == fuir().clazz(FUIR.SpecialClazzes.c_i64) ||
       fuir().clazzAsValue(_clazz) == fuir().clazz(FUIR.SpecialClazzes.c_i64));

    return
        nonrefs[0    ] & 0xFFFFffffL |
      ((nonrefs[0 + 1] & 0xFFFFffffL) << 32);
  }


  /**
   * For a value of type u8, return the value.
   *
   * @return the u8 value
   */
  public int u8Value()
  {
    if (PRECONDITIONS) require
      (_clazz == fuir().clazz(FUIR.SpecialClazzes.c_u8) ||
       fuir().clazzAsValue(_clazz) == fuir().clazz(FUIR.SpecialClazzes.c_u8));

    return nonrefs[0];
  }



  /**
   * For a value of type u16, return the value.
   *
   * @return the u16 value
   */
  public int u16Value()
  {
    if (PRECONDITIONS) require
      (_clazz == fuir().clazz(FUIR.SpecialClazzes.c_u16) ||
       fuir().clazzAsValue(_clazz) == fuir().clazz(FUIR.SpecialClazzes.c_u16));

    return nonrefs[0];
  }


  /**
   * For a value of type u32, return the value.
   *
   * @return the u32 value
   */
  public int u32Value()
  {
    if (PRECONDITIONS) require
      (_clazz == fuir().clazz(FUIR.SpecialClazzes.c_u32) ||
       fuir().clazzAsValue(_clazz) == fuir().clazz(FUIR.SpecialClazzes.c_u32));

    return nonrefs[0];
  }


  /**
   * For a value of type u64, return the value.
   *
   * @return the u64 value
   */
  public long u64Value()
  {
    if (PRECONDITIONS) require
      (_clazz == fuir().clazz(FUIR.SpecialClazzes.c_u64) ||
       fuir().clazzAsValue(_clazz) == fuir().clazz(FUIR.SpecialClazzes.c_u64));

    return
        nonrefs[0    ] & 0xFFFFffffL |
      ((nonrefs[0 + 1] & 0xFFFFffffL) << 32);
  }


  /**
   * For a value of type f32, return the value.
   *
   * @return the f32 value
   */
  public float f32Value()
  {
    if (PRECONDITIONS) require
      (_clazz == fuir().clazz(FUIR.SpecialClazzes.c_f32) ||
       fuir().clazzAsValue(_clazz) == fuir().clazz(FUIR.SpecialClazzes.c_f32));

    return Float.intBitsToFloat(nonrefs[0]);
  }


  /**
   * For a value of type f64, return the value.
   *
   * @return the f64 value
   */
  public double f64Value()
  {
    if (PRECONDITIONS) require
      (_clazz == fuir().clazz(FUIR.SpecialClazzes.c_f64) ||
       fuir().clazzAsValue(_clazz) == fuir().clazz(FUIR.SpecialClazzes.c_f64));

    var l =
        nonrefs[0    ] & 0xFFFFffffL |
      ((nonrefs[0 + 1] & 0xFFFFffffL) << 32);
    return Double.longBitsToDouble(l);
  }


  /**
   * For a value of type bool, return the value.
   *
   * @return the bool value
   */
  public boolean boolValue()
  {
    if (PRECONDITIONS) require
      (_clazz == fuir().clazz(FUIR.SpecialClazzes.c_true_) ||
       _clazz == fuir().clazz(FUIR.SpecialClazzes.c_false_) ||
       _clazz == fuir().clazz(FUIR.SpecialClazzes.c_bool));

    return
      _clazz == fuir().clazz(FUIR.SpecialClazzes.c_true_)  ? true  :
      _clazz == fuir().clazz(FUIR.SpecialClazzes.c_false_) ? false
                                               : nonrefs[0] == 1;
  }


  /**
   * Debugging only: Check that this value is valid as the current instance for
   * a feature with given static clazz.
   *
   * @param expected the static clazz of the feature this value is called on.
   *
   * @throws Error in case this does not match the expected clazz
   */
  public void checkStaticClazz(int expected)
  {
    if (fuir().clazzIsRef(expected))
      {
        // NYI:
        // if (!expected.isDirectlyAssignableFrom(clazz()))
        //   {
        //     throw new Error("Dynamic runtime clazz "+clazz()+" does not match static "+expected);
        //   }
      }
    else
      {
        if (expected != _clazz)
          {
            throw new Error("Runtime clazz "+_clazz+" does not equal static "+expected);
          }
      }
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
    return new LValue(c, this, off);
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
    if (PRECONDITIONS) require
      (size == Layout.get(_clazz).size());

    storeNonRef(slot, size, 0);
  }


  /**
   * Store this value in a field
   *
   * @param slot the slot that addresses the field this should be stored in.
   *
   * @param size the size of the data to be stored
   *
   * @param voffset the offset the value to be stored within this
   */
  void storeNonRef(LValue slot, int size, int voffset)
  {
    Instance cur    = slot.container;
    int      offset = slot.offset;

    for (int i=0; i < size; i++)
      {
        cur.refs   [offset + i] = refs   [voffset + i];
        cur.nonrefs[offset + i] = nonrefs[voffset + i];
      }
  }


  /**
   * Does this value equal the value in slot of given size on a low-level
   * bit-wise comparison?
   *
   * @param slot the slot that addresses the field this should be compared
   * against.
   *
   * @param size the size of the data to be compared.
   */
  boolean equalsBitWise(LValue slot, int size)
  {
    if (PRECONDITIONS) require
      (size == Layout.get(_clazz).size());

    return equalsBitWise(slot, size, 0);
  }


  /**
   * Does a value of given size stored at voffset in this instance equal the
   * value in slot on a low-level bit-wise comparison?
   *
   * @param slot the slot that addresses the field this should be compared
   * against.
   *
   * @param size the size of the data to be compared.
   *
   * @param voffset the offset of the value in this instance.
   */
  boolean equalsBitWise(LValue slot, int size, int voffset)
  {
    var result = true;
    Instance cur    = slot.container;
    int      offset = slot.offset;

    for (int i=0; result && i < size; i++)
      {
        result = cur.refs   [offset + i] == refs   [voffset + i] &&
                 cur.nonrefs[offset + i] == nonrefs[voffset + i];
      }
    return result;
  }


  /**
   * Return the instance this value contains.  If this is an Instance, return
   * this, if this is an LValue containing an instance, get that instance.
   */
  Instance instance()
  {
    return this;
  }


  /**
   * Return the tag of this choice.
   */
  public int tag()
  {
    if (PRECONDITIONS) require
      (fuir().clazzIsChoice(_clazz) & !fuir().clazzIsChoiceOfOnlyRefs(_clazz));

    var tag = nonrefs[0];
    if (POSTCONDITIONS) ensure
      (tag >= 0);

    return tag;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    var result = "instance[" + fuir().clazzAsString(_clazz) + "]" + this.hashCode() +
      (_clazz == fuir().clazz(FUIR.SpecialClazzes.c_i32)  ? " (" + i32Value() + ")" :
       _clazz == fuir().clazz(FUIR.SpecialClazzes.c_u32)  ? " (" + u32Value() + ")" :
       _clazz == fuir().clazz(FUIR.SpecialClazzes.c_u8)   ? " (" + u8Value() + ")" :
       _clazz == fuir().clazz(FUIR.SpecialClazzes.c_i64)  ? " (" + i64Value() + ")" :
       _clazz == fuir().clazz(FUIR.SpecialClazzes.c_u64)  ? " (" + u64Value() + ")" :
       _clazz == fuir().clazz(FUIR.SpecialClazzes.c_bool) ? " (" + boolValue() + ")" : "");
    return result;
  }

}

/* end of file */
