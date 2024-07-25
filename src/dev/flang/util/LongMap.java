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
 * Source code of class ANY
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.util.HashMap;
import java.util.TreeMap;


/**
 * LongMap implements a Map from long to any type T.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class LongMap<T>
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Underlying implementation.
   */
  HashMap<Long, T> _m = new HashMap<>();


  /*-----------------------------  methods  -----------------------------*/


  public T get(long l)
  {
    return _m.get(Long.valueOf(l));
  }


  public T put(long l, T v)
  {
    return _m.put(Long.valueOf(l), v);
  }



}

/* end of file */
