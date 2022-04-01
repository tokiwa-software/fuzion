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

import dev.flang.util.Errors;

import java.util.Arrays;


/**
 * ArayData represents memory allocated by fuzion.sys.array.alloc
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
        Errors.fatal("array index out of bounds: " + x + " not in 0.."+l+"\n"+Interpreter.callStack());
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
