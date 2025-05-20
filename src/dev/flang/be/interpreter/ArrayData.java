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
   *
   */
  public final Object _array;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  public ArrayData(Object array)
  {
    if (PRECONDITIONS) require
      (array != null,
       array.getClass().isArray(),
       array.getClass().componentType().isPrimitive() ||
       array.getClass().componentType() == Value.class );

    this._array = array;
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
    if      (_array instanceof byte   [] a) { return a.length; }
    else if (_array instanceof short  [] a) { return a.length; }
    else if (_array instanceof char   [] a) { return a.length; }
    else if (_array instanceof int    [] a) { return a.length; }
    else if (_array instanceof long   [] a) { return a.length; }
    else if (_array instanceof float  [] a) { return a.length; }
    else if (_array instanceof double [] a) { return a.length; }
    else if (_array instanceof boolean[] a) { return a.length; }
    else if (_array instanceof Object [] a) { return a.length; }
    else
      {
        throw new Error("Unexpected array type " + _array);
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
      case c_i8 ->          ((byte   [])_array)[x] = (byte   ) v.i8Value();
      case c_i16 ->         ((short  [])_array)[x] = (short  ) v.i16Value();
      case c_i32 ->         ((int    [])_array)[x] =           v.i32Value();
      case c_i64 ->         ((long   [])_array)[x] =           v.i64Value();
      case c_u8 ->          ((byte   [])_array)[x] = (byte   ) v.u8Value();
      case c_u16 ->         ((char   [])_array)[x] = (char   ) v.u16Value();
      case c_u32 ->         ((int    [])_array)[x] =           v.u32Value();
      case c_u64 ->         ((long   [])_array)[x] =           v.u64Value();
      case c_f32 ->         ((float  [])_array)[x] =           v.f32Value();
      case c_f64 ->         ((double [])_array)[x] =           v.f64Value();
      case c_bool ->        ((boolean[])_array)[x] =           v.boolValue();
      default ->            ((Value  [])_array)[x] =           v;
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
      case c_i8 ->          new i8Value  (((byte   [])_array)[x]       );
      case c_i16 ->         new i16Value (((short  [])_array)[x]       );
      case c_i32 ->         new i32Value (((int    [])_array)[x]       );
      case c_i64 ->         new i64Value (((long   [])_array)[x]       );
      case c_u8 ->          new u8Value  (((byte   [])_array)[x] & 0xff);
      case c_u16 ->         new u16Value (((char   [])_array)[x]       );
      case c_u32 ->         new u32Value (((int    [])_array)[x]       );
      case c_u64 ->         new u64Value (((long   [])_array)[x]       );
      case c_f32 ->         new f32Value (((float  [])_array)[x]       );
      case c_f64 ->         new f64Value (((double [])_array)[x]       );
      case c_bool ->        new boolValue(((boolean[])_array)[x]       );
      default ->            (((Value[])_array)[x])        ;
    };
  }


  /**
   * Allocate a new array
   *
   * @param sz size of the array
   * @param elementType the elements type
   * @return
   */
  public static ArrayData alloc(int sz, FUIR fuir, int elementType)
  {
    return switch (fuir.getSpecialClazz(elementType))
    {
      case c_i8 ->          new ArrayData(new byte   [sz]);
      case c_i16 ->         new ArrayData(new short  [sz]);
      case c_i32 ->         new ArrayData(new int    [sz]);
      case c_i64 ->         new ArrayData(new long   [sz]);
      case c_u8 ->          new ArrayData(new byte   [sz]);
      case c_u16 ->         new ArrayData(new char   [sz]);
      case c_u32 ->         new ArrayData(new int    [sz]);
      case c_u64 ->         new ArrayData(new long   [sz]);
      case c_f32 ->         new ArrayData(new float  [sz]);
      case c_f64 ->         new ArrayData(new double [sz]);
      case c_bool ->        new ArrayData(new boolean[sz]);
      default ->            new ArrayData(new Value  [sz]);
    };
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "data[" + length() + ", " + _array.getClass().componentType() + "]";
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
        if      (_array instanceof byte   [] arr) { memSegment.setAtIndex(ValueLayout.JAVA_BYTE,   i, arr[i]);}
        else if (_array instanceof short  [] arr) { memSegment.setAtIndex(ValueLayout.JAVA_SHORT,  i, arr[i]);}
        else if (_array instanceof char   [] arr) { memSegment.setAtIndex(ValueLayout.JAVA_CHAR,   i, arr[i]);}
        else if (_array instanceof int    [] arr) { memSegment.setAtIndex(ValueLayout.JAVA_INT,    i, arr[i]);}
        else if (_array instanceof long   [] arr) { memSegment.setAtIndex(ValueLayout.JAVA_LONG,   i, arr[i]);}
        else if (_array instanceof float  [] arr) { memSegment.setAtIndex(ValueLayout.JAVA_FLOAT,  i, arr[i]);}
        else if (_array instanceof double [] arr) { memSegment.setAtIndex(ValueLayout.JAVA_DOUBLE, i, arr[i]);}
        else if (_array instanceof boolean[] arr) { memSegment.setAtIndex(ValueLayout.JAVA_BOOLEAN,i, arr[i]);}
        else if (_array instanceof Value  [] arr)
        {
          for (int j = 0; j < arr.length; j++)
            {
              memSegment.set(ValueLayout.ADDRESS, j * 8, (MemorySegment)arr[j].toNative());
            }
        }
        else throw new Error("NYI: UNDER DEVELOPMENT: copyToMemSegment: " + _array.getClass());
      }
  }

  private int elementByteSize()
  {
    if      (_array instanceof byte   []) { return 1; }
    else if (_array instanceof short  []) { return 2; }
    else if (_array instanceof char   []) { return 2; }
    else if (_array instanceof int    []) { return 4; }
    else if (_array instanceof long   []) { return 8; }
    else if (_array instanceof float  []) { return 4; }
    else if (_array instanceof double []) { return 8; }
    else if (_array instanceof boolean[]) { return 4; }
    else if (_array instanceof Value  []) { return 8; }
    throw new Error("NYI: ArrayData.elementByteSize");
  }


  public void set(MemorySegment memSegment)
  {
    for (int i = 0; i < length(); i++)
      {
        if      (_array instanceof byte   [] arr) { arr[i] = memSegment.getAtIndex(ValueLayout.JAVA_BYTE, i); }
        else if (_array instanceof short  [] arr) { arr[i] = memSegment.getAtIndex(ValueLayout.JAVA_SHORT, i); }
        else if (_array instanceof char   [] arr) { arr[i] = memSegment.getAtIndex(ValueLayout.JAVA_CHAR, i); }
        else if (_array instanceof int    [] arr) { arr[i] = memSegment.getAtIndex(ValueLayout.JAVA_INT, i); }
        else if (_array instanceof long   [] arr) { arr[i] = memSegment.getAtIndex(ValueLayout.JAVA_LONG, i); }
        else if (_array instanceof float  [] arr) { arr[i] = memSegment.getAtIndex(ValueLayout.JAVA_FLOAT, i); }
        else if (_array instanceof double [] arr) { arr[i] = memSegment.getAtIndex(ValueLayout.JAVA_DOUBLE, i); }
        else if (_array instanceof boolean[] arr) { arr[i] = memSegment.getAtIndex(ValueLayout.JAVA_BOOLEAN, i); }
        else if (_array instanceof Value  [] arr) { /* NYI: UNDER DEVELOPMENT */ }
        else throw new Error("NYI: UNDER DEVELOPMENT: set: " + _array.getClass());
      }
  }
}

/* end of file */
