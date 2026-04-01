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
 * Source of class ArrayData
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import dev.flang.fuir.FUIR;
import dev.flang.util.Errors;



/**
 * ArrayData represents memory allocated by fuzion.sys.array.alloc
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ArrayData extends Value
{

  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The actual data
   */
  public final Object _data;


  public final int _clazz;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  public ArrayData(Object d, int cl)
  {
    if (PRECONDITIONS) require
      (d != null,
       d.getClass().isArray(),
       d.getClass().componentType().isPrimitive() ||
       d.getClass().componentType() == Value.class );

    this._data = d;
    this._clazz = cl;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * check if x is a valid index in this array. Errors.fatal if not.
   *
   * @param x the index to check.
   */
  void checkIndex(int x)
  {
    var l = length();
    if (x < 0 || x >= l)
      {
        Errors.fatal("array index out of bounds: " + x + " not in 0.."+l+"\n" /* NYI: need fuir + Executor.callStack(fuir) */);
      }
  }


  /**
   * Determine the length of this array.
   */
  int length()
  {
    if      (_data instanceof byte   [] a) { return a.length; }
    else if (_data instanceof short  [] a) { return a.length; }
    else if (_data instanceof char   [] a) { return a.length; }
    else if (_data instanceof int    [] a) { return a.length; }
    else if (_data instanceof long   [] a) { return a.length; }
    else if (_data instanceof float  [] a) { return a.length; }
    else if (_data instanceof double [] a) { return a.length; }
    else if (_data instanceof boolean[] a) { return a.length; }
    else if (_data instanceof Object [] a) { return a.length; }
    else
      {
        throw new Error("Unexpected array type " + _data);
      }
  }


  /**
   * Return the ArrayData this value contains.  If this is an ArrayData, return
   * this, if this is an LValue containing an ArrayData, get that ArrayData.
   */
  ArrayData arrayData()
  {
    return this;
  }


  /**
   * set array element at index x.
   *
   * @param x index
   * @param v value
   * @param elementType the values type
   */
  void set(
    int x,
    Value v,
    FUIR fuir,
    int elementType)
  {
    checkIndex(x);
    switch (fuir.getSpecialClazz(elementType))
    {
      case c_i8 ->          ((byte   [])_data)[x] = (byte   ) v.i8Value();
      case c_i16 ->         ((short  [])_data)[x] = (short  ) v.i16Value();
      case c_i32 ->         ((int    [])_data)[x] =           v.i32Value();
      case c_i64 ->         ((long   [])_data)[x] =           v.i64Value();
      case c_u8 ->          ((byte   [])_data)[x] = (byte   ) v.u8Value();
      case c_u16 ->         ((char   [])_data)[x] = (char   ) v.u16Value();
      case c_u32 ->         ((int    [])_data)[x] =           v.u32Value();
      case c_u64 ->         ((long   [])_data)[x] =           v.u64Value();
      case c_f32 ->         ((float  [])_data)[x] =           v.f32Value();
      case c_f64 ->         ((double [])_data)[x] =           v.f64Value();
      case c_bool ->        ((boolean[])_data)[x] =           v.boolValue();
      default ->            ((Value  [])_data)[x] =           v;
    }
  }


  /**
   * get array element at index x.
   *
   * @param x index
   * @param elementType the values type
   * @return
   */
  Value get(
    int x,
    FUIR fuir,
    int elementType)
  {
    checkIndex(x);
    return switch (fuir.getSpecialClazz(elementType))
    {
      case c_i8 ->          new i8Value  (((byte   [])_data)[x]       );
      case c_i16 ->         new i16Value (((short  [])_data)[x]       );
      case c_i32 ->         new i32Value (((int    [])_data)[x]       );
      case c_i64 ->         new i64Value (((long   [])_data)[x]       );
      case c_u8 ->          new u8Value  (((byte   [])_data)[x] & 0xff);
      case c_u16 ->         new u16Value (((char   [])_data)[x]       );
      case c_u32 ->         new u32Value (((int    [])_data)[x]       );
      case c_u64 ->         new u64Value (((long   [])_data)[x]       );
      case c_f32 ->         new f32Value (((float  [])_data)[x]       );
      case c_f64 ->         new f64Value (((double [])_data)[x]       );
      case c_bool ->        new boolValue(((boolean[])_data)[x]       );
      default ->            (((Value[])_data)[x])        ;
    };
  }


  /**
   * Allocate a new array
   *
   * @param sz size of the array
   * @param elementType the elements type
   * @return
   */
  public static ArrayData alloc(int cl, int sz, FUIR fuir, int elementType)
  {
    return switch (fuir.getSpecialClazz(elementType))
    {
      case c_i8 ->          new ArrayData(new byte   [sz], cl);
      case c_i16 ->         new ArrayData(new short  [sz], cl);
      case c_i32 ->         new ArrayData(new int    [sz], cl);
      case c_i64 ->         new ArrayData(new long   [sz], cl);
      case c_u8 ->          new ArrayData(new byte   [sz], cl);
      case c_u16 ->         new ArrayData(new char   [sz], cl);
      case c_u32 ->         new ArrayData(new int    [sz], cl);
      case c_u64 ->         new ArrayData(new long   [sz], cl);
      case c_f32 ->         new ArrayData(new float  [sz], cl);
      case c_f64 ->         new ArrayData(new double [sz], cl);
      case c_bool ->        new ArrayData(new boolean[sz], cl);
      default ->            new ArrayData(new Value  [sz], cl);
    };
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "data[" + length() + ", " + _data.getClass().componentType() + "]";
  }


  @Override
  protected Object toNative()
  {
    var memSegment = Arena.ofAuto().allocate(length() * elementByteSize());

    copyToMemSegment(memSegment);

    return memSegment;
  }


  private void copyToMemSegment(MemorySegment memSegment)
  {
    for (int i = 0; i < length(); i++)
      {
        if      (_data instanceof byte   [] arr) { memSegment.setAtIndex(ValueLayout.JAVA_BYTE,   i, arr[i]);}
        else if (_data instanceof short  [] arr) { memSegment.setAtIndex(ValueLayout.JAVA_SHORT,  i, arr[i]);}
        else if (_data instanceof char   [] arr) { memSegment.setAtIndex(ValueLayout.JAVA_CHAR,   i, arr[i]);}
        else if (_data instanceof int    [] arr) { memSegment.setAtIndex(ValueLayout.JAVA_INT,    i, arr[i]);}
        else if (_data instanceof long   [] arr) { memSegment.setAtIndex(ValueLayout.JAVA_LONG,   i, arr[i]);}
        else if (_data instanceof float  [] arr) { memSegment.setAtIndex(ValueLayout.JAVA_FLOAT,  i, arr[i]);}
        else if (_data instanceof double [] arr) { memSegment.setAtIndex(ValueLayout.JAVA_DOUBLE, i, arr[i]);}
        else if (_data instanceof boolean[] arr) { memSegment.setAtIndex(ValueLayout.JAVA_BOOLEAN,i, arr[i]);}
        else if (_data instanceof Value  [] arr)
        {
          for (int j = 0; j < arr.length; j++)
            {
              memSegment.set(ValueLayout.ADDRESS, j * 8, (MemorySegment)arr[j].toNative());
            }
        }
        else throw new Error("NYI: UNDER DEVELOPMENT: copyToMemSegment: " + _data.getClass());
      }
  }

  private int elementByteSize()
  {
    if      (_data instanceof byte   []) { return 1; }
    else if (_data instanceof short  []) { return 2; }
    else if (_data instanceof char   []) { return 2; }
    else if (_data instanceof int    []) { return 4; }
    else if (_data instanceof long   []) { return 8; }
    else if (_data instanceof float  []) { return 4; }
    else if (_data instanceof double []) { return 8; }
    else if (_data instanceof boolean[]) { return 4; }
    else if (_data instanceof Value  []) { return 8; }
    throw new Error("NYI: ArrayData.elementByteSize");
  }


  public void set(MemorySegment memSegment)
  {
    for (int i = 0; i < length(); i++)
      {
        if      (_data instanceof byte   [] arr) { arr[i] = memSegment.getAtIndex(ValueLayout.JAVA_BYTE, i); }
        else if (_data instanceof short  [] arr) { arr[i] = memSegment.getAtIndex(ValueLayout.JAVA_SHORT, i); }
        else if (_data instanceof char   [] arr) { arr[i] = memSegment.getAtIndex(ValueLayout.JAVA_CHAR, i); }
        else if (_data instanceof int    [] arr) { arr[i] = memSegment.getAtIndex(ValueLayout.JAVA_INT, i); }
        else if (_data instanceof long   [] arr) { arr[i] = memSegment.getAtIndex(ValueLayout.JAVA_LONG, i); }
        else if (_data instanceof float  [] arr) { arr[i] = memSegment.getAtIndex(ValueLayout.JAVA_FLOAT, i); }
        else if (_data instanceof double [] arr) { arr[i] = memSegment.getAtIndex(ValueLayout.JAVA_DOUBLE, i); }
        else if (_data instanceof boolean[] arr) { arr[i] = memSegment.getAtIndex(ValueLayout.JAVA_BOOLEAN, i); }
        else if (_data instanceof Value  [] arr) { /* NYI: UNDER DEVELOPMENT */ }
        else throw new Error("NYI: UNDER DEVELOPMENT: set: " + _data.getClass());
      }
  }
}

/* end of file */
