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
 * Source of class PreallocatedConstant
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm;

import dev.flang.util.ANY;


/**
 * PreallocatedConstant is a pair of a clazz that defines a type of a constant
 * and the serialized value of the constant.  It is used to find duplicates
 * in constants.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class PreallocatedConstant extends ANY implements Comparable<PreallocatedConstant>
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The clazz of the constant
   */
  final int _constClazz;


  /**
   * The serialized byte data of the constnat
   */
  final byte[] _data;


  /*---------------------------  constructors  --------------------------*/


  /**
   * Create instance of preallocated constant
   *
   * @param constCl the type of the constant
   *
   * @param data the serialized byte data
   */
  PreallocatedConstant(int constCl, byte[] data)
  {
    if (PRECONDITIONS) require
      (data != null);

    this._constClazz = constCl;
    this._data = data;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Compare operation.
   */
  public int compareTo(PreallocatedConstant other)
  {
    int result =
      _constClazz  < other._constClazz  ? -1 :
      _constClazz  > other._constClazz  ? +1 :
      _data.length < other._data.length ? -1 :
      _data.length > other._data.length ? -1 : 0;

    for (int i = 0; result == 0 && i < _data.length; i++)
      {
        result =
          _data[i] < other._data[i] ? -1 :
          _data[i] > other._data[i] ? +1 : 0;
      }

    return result;
  }


}

/* end of file */
