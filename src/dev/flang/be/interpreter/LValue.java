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
 * Source of class LValue
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import dev.flang.fuir.SpecialClazzes;


/**
 * LValue represents an address of a modifyable value type, which is the result
 * of reading a value field.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class LValue extends ValueWithClazz
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The instance (stack of heap) containing this LValue
   */
  public final Instance container;


  /**
   * The offset of this Value within container
   */
  public int offset;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  public LValue(int c, Instance cont, int off)
  {
    super(c);

    if (PRECONDITIONS) require
      (cont != null,
       fuir().clazzIsUnitType(c) || off >= 0,
       fuir().clazzIsUnitType(c) || off < Layout.get(cont._clazz).size(),
       fuir().clazzIsUnitType(c) || off < cont.refs.length);

    this.container = cont;
    this.offset = off;
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

    return new Instance(cl, container, offset);
  }


  /**
   * For a value of type i8, return the value.
   *
   * @return the integer value
   */
  public int i8Value()
  {
    return container.nonrefs[offset];
  }


  /**
   * For a value of type i16, return the value.
   *
   * @return the integer value
   */
  public int i16Value()
  {
    return container.nonrefs[offset];
  }


  /**
   * For a value of type i32, return the value.
   *
   * @return the integer value
   */
  public int i32Value()
  {
    return container.nonrefs[offset];
  }


  /**
   * For a value of type i64, return the value.
   *
   * @return the i64 value
   */
  public long i64Value()
  {
    return
        container.nonrefs[offset    ] & 0xFFFFffffL |
      ((container.nonrefs[offset + 1] & 0xFFFFffffL) << 32);
  }


  /**
   * For a value of type u8, return the value.
   *
   * @return the integer value
   */
  public int u8Value()
  {
    return container.nonrefs[offset];
  }


  /**
   * For a value of type u16, return the value.
   *
   * @return the integer value
   */
  public int u16Value()
  {
    return container.nonrefs[offset];
  }


  /**
   * For a value of type u32, return the value.
   *
   * @return the integer value
   */
  public int u32Value()
  {
    return container.nonrefs[offset];
  }


  /**
   * For a value of type u64, return the value.
   *
   * @return the u64 value
   */
  public long u64Value()
  {
    return
        container.nonrefs[offset    ] & 0xFFFFffffL |
      ((container.nonrefs[offset + 1] & 0xFFFFffffL) << 32);
  }


  /**
   * For a value of type f32, return the value.
   *
   * @return the f32 value
   */
  public float f32Value()
  {
    return Float.intBitsToFloat(container.nonrefs[offset]);
  }


  /**
   * For a value of type f64, return the value.
   *
   * @return the f64 value
   */
  public double f64Value()
  {
    return Double.longBitsToDouble(  container.nonrefs[offset    ] & 0xFFFFffffL |
                                   ((container.nonrefs[offset + 1] & 0xFFFFffffL) << 32));
  }


  /**
   * For a value of type bool, return the value.
   *
   * @return the bool value
   */
  public boolean boolValue()
  {
    return container.nonrefs[offset] != 0;
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
    return new LValue(c,
                      container,
                      offset + off);
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

    container.storeNonRef(slot, size, offset);
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
    return container.equalsBitWise(slot, size, offset);
  }


  /**
   * Debugging only: Check that this value is valid as the current instance for
   * a feature with given static clazz.
   *
   * @param expected the static clazz of the feature this value is called on.
   *
   * @throws Error in case this does not match the expected clazz
   */
  void checkStaticClazz(int expected)
  {
    if (fuir().clazzIsRef(expected))
      {
        throw new Error("LValue (" + _clazz + " not allowed for dynamic clazz " + expected);
      }
    if (expected != _clazz)
      {
        throw new Error("Runtime clazz "+_clazz+" does not equal static "+expected);
      }
  }


  /**
   * Return the instance this value contains.  If this is an Instance, return
   * this, if this is an LValue containing an instance, get that instance.
   */
  Instance instance()
  {
    if (PRECONDITIONS) require
      (fuir().clazzIsRef(_clazz));

    return (Instance) container.refs[offset];
  }


  /**
   * Return the ArrayData this value contains.  If this is an ArrayData, return
   * this, if this is an LValue containing an ArrayData, get that ArrayData.
   */
  ArrayData arrayData()
  {
    return (ArrayData) container.refs[offset];
  }


  /**
   * Return the tag of this choice.
   */
  public int tag()
  {
    if (PRECONDITIONS) require
      (fuir().clazzIsChoice(_clazz) && !fuir().clazzIsChoiceOfOnlyRefs(_clazz));

    var tag = container.nonrefs[offset];
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
    return "lvalue[" + container + "@" + offset + "(" + fuir().clazzAsString(_clazz) + ")]" +
      (_clazz == fuir().clazz(SpecialClazzes.c_i8  ) ? " (" + i8Value()   + ")" :
       _clazz == fuir().clazz(SpecialClazzes.c_u8  ) ? " (" + u8Value()   + ")" :
       _clazz == fuir().clazz(SpecialClazzes.c_i16 ) ? " (" + i16Value()  + ")" :
       _clazz == fuir().clazz(SpecialClazzes.c_u16 ) ? " (" + u16Value()  + ")" :
       _clazz == fuir().clazz(SpecialClazzes.c_i32 ) ? " (" + i32Value()  + ")" :
       _clazz == fuir().clazz(SpecialClazzes.c_u32 ) ? " (" + u32Value()  + ")" :
       _clazz == fuir().clazz(SpecialClazzes.c_i64 ) ? " (" + i64Value()  + ")" :
       _clazz == fuir().clazz(SpecialClazzes.c_u64 ) ? " (" + u64Value()  + ")" :
       _clazz == fuir().clazz(SpecialClazzes.c_f32 ) ? " (" + f32Value()  + ")" :
       _clazz == fuir().clazz(SpecialClazzes.c_f64 ) ? " (" + f64Value()  + ")" :
       _clazz == fuir().clazz(SpecialClazzes.c_bool) ? " (" + boolValue() + ")" : "");
  }

}

/* end of file */
