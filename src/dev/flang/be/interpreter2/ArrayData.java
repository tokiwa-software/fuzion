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

package dev.flang.be.interpreter2;

import dev.flang.air.Clazz;
import dev.flang.ast.AbstractType; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Types;        // NYI: remove dependency! Use dev.flang.fuir instead.
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
   *
   * @param clazz
   *
   * @param outer
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
        Errors.fatal("array index out of bounds: " + x + " not in 0.."+l+"\n" /* NYI  callStack() */);
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
    AbstractType elementType)
  {
    checkIndex(x);
    if      (elementType.compareTo(Types.resolved.t_i8  ) == 0 && _array instanceof byte   []) { ((byte   [])_array)[x] = (byte   ) v.i8Value();   }
    else if (elementType.compareTo(Types.resolved.t_i16 ) == 0 && _array instanceof short  []) { ((short  [])_array)[x] = (short  ) v.i16Value();  }
    else if (elementType.compareTo(Types.resolved.t_i32 ) == 0 && _array instanceof int    []) { ((int    [])_array)[x] =           v.i32Value();  }
    else if (elementType.compareTo(Types.resolved.t_i64 ) == 0 && _array instanceof long   []) { ((long   [])_array)[x] =           v.i64Value();  }
    else if (elementType.compareTo(Types.resolved.t_u8  ) == 0 && _array instanceof byte   []) { ((byte   [])_array)[x] = (byte   ) v.u8Value();   }
    else if (elementType.compareTo(Types.resolved.t_u16 ) == 0 && _array instanceof char   []) { ((char   [])_array)[x] = (char   ) v.u16Value();  }
    else if (elementType.compareTo(Types.resolved.t_u32 ) == 0 && _array instanceof int    []) { ((int    [])_array)[x] =           v.u32Value();  }
    else if (elementType.compareTo(Types.resolved.t_u64 ) == 0 && _array instanceof long   []) { ((long   [])_array)[x] =           v.u64Value();  }
    else if (elementType.compareTo(Types.resolved.t_f32 ) == 0 && _array instanceof float  []) { ((float  [])_array)[x] =           v.f32Value();  }
    else if (elementType.compareTo(Types.resolved.t_f64 ) == 0 && _array instanceof double []) { ((double [])_array)[x] =           v.f64Value();  }
    else if (elementType.compareTo(Types.resolved.t_bool) == 0 && _array instanceof boolean[]) { ((boolean[])_array)[x] =           v.boolValue(); }
    else                                                        { ((Value  [])_array)[x] =           v;             }
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
    AbstractType elementType)
  {
    checkIndex(x);
    if      (elementType.compareTo(Types.resolved.t_i8  ) == 0 && _array instanceof byte   []) { return new i8Value  (((byte   [])_array)[x]       ); }
    else if (elementType.compareTo(Types.resolved.t_i16 ) == 0 && _array instanceof short  []) { return new i16Value (((short  [])_array)[x]       ); }
    else if (elementType.compareTo(Types.resolved.t_i32 ) == 0 && _array instanceof int    []) { return new i32Value (((int    [])_array)[x]       ); }
    else if (elementType.compareTo(Types.resolved.t_i64 ) == 0 && _array instanceof long   []) { return new i64Value (((long   [])_array)[x]       ); }
    else if (elementType.compareTo(Types.resolved.t_u8  ) == 0 && _array instanceof byte   []) { return new u8Value  (((byte   [])_array)[x] & 0xff); }
    else if (elementType.compareTo(Types.resolved.t_u16 ) == 0 && _array instanceof char   []) { return new u16Value (((char   [])_array)[x]       ); }
    else if (elementType.compareTo(Types.resolved.t_u32 ) == 0 && _array instanceof int    []) { return new u32Value (((int    [])_array)[x]       ); }
    else if (elementType.compareTo(Types.resolved.t_u64 ) == 0 && _array instanceof long   []) { return new u64Value (((long   [])_array)[x]       ); }
    else if (elementType.compareTo(Types.resolved.t_f32 ) == 0 && _array instanceof float  []) { return new f32Value (((float  [])_array)[x]       ); }
    else if (elementType.compareTo(Types.resolved.t_f64 ) == 0 && _array instanceof double []) { return new f64Value (((double [])_array)[x]       ); }
    else if (elementType.compareTo(Types.resolved.t_bool) == 0 && _array instanceof boolean[]) { return new boolValue(((boolean[])_array)[x]       ); }
    else                                                        { return              ((Value   [])_array)[x]        ; }
  }


  /**
   * Allocate a new array
   *
   * @param sz size of the array
   * @param et the elements type
   * @return
   */
  public static ArrayData alloc(int sz, AbstractType elementType)
  {
    if      (elementType.compareTo(Types.resolved.t_i8  ) == 0) { return new ArrayData(new byte   [sz]); }
    else if (elementType.compareTo(Types.resolved.t_i16 ) == 0) { return new ArrayData(new short  [sz]); }
    else if (elementType.compareTo(Types.resolved.t_i32 ) == 0) { return new ArrayData(new int    [sz]); }
    else if (elementType.compareTo(Types.resolved.t_i64 ) == 0) { return new ArrayData(new long   [sz]); }
    else if (elementType.compareTo(Types.resolved.t_u8  ) == 0) { return new ArrayData(new byte   [sz]); }
    else if (elementType.compareTo(Types.resolved.t_u16 ) == 0) { return new ArrayData(new char   [sz]); }
    else if (elementType.compareTo(Types.resolved.t_u32 ) == 0) { return new ArrayData(new int    [sz]); }
    else if (elementType.compareTo(Types.resolved.t_u64 ) == 0) { return new ArrayData(new long   [sz]); }
    else if (elementType.compareTo(Types.resolved.t_bool) == 0) { return new ArrayData(new boolean[sz]); }
    else                                                        { return new ArrayData(new Value  [sz]); }
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

}

/* end of file */
