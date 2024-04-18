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
 * Source code of class BoolArray
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.util.Arrays;


/**
 * BoolArray contains an expandable array of `boolean` values
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class BoolArray extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * pre-allocated empty array.
   */
  private static boolean[] _EMPTY_ = new boolean[0];


  /*----------------------------  variables  ----------------------------*/


  /**
   * Number of values stored in this array
   */
  private int _size = 0;


  /**
   * The actual array data
   */
  private boolean[] _data = _EMPTY_;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create an empty array;
   */
  public BoolArray()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * add a value as new last element. Increase size by 1.
   *
   * @param v the value to be added.
   */
  public void add(boolean v)
  {
    var s = _size;
    var d = _data;
    if (s >= d.length)
      {
        d = Arrays.copyOf(_data, Math.max(16, 2 * _size));
        _data = d;
      }
    d[s] = v;
    _size = s + 1;
  }


  /**
   * set a value at a given index.
   *
   * @param i the index
   *
   * @param v the value
   *
   * @return the old value at index i.
   */
  public boolean set(int i, boolean v)
  {
    if (PRECONDITIONS) require
      (i >= 0,
       i < size());

    var res = _data[i];
    _data[i] = v;
    return res;
  }


  /**
   * get a value at a given index.
   *
   * @param i the index
   *
   * @return the value at index i.
   */
  public boolean get(int i)
  {
    if (PRECONDITIONS) require
      (i >= 0,
       i < size());

    return _data[i];
  }


  /**
   * get the size of the array
   *
   * @return the size
   */
  public int size()
  {
    return _size;
  }

}

/* end of file */
