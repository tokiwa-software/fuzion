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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;


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


  /**
   * @see java.util.Map.get
   */
  public T get(long i)
  {
    return _m.get(Long.valueOf(i));
  }


  /**
   * @see java.util.Map.getOrDefault
   */
  public T getOrDefault(long i, T def)
  {
    return _m.getOrDefault(Long.valueOf(i), def);
  }


  /**
   * @see java.util.Map.put
   */
  public T put(long i, T v)
  {
    return _m.put(Long.valueOf(i), v);
  }


  /**
   * All keys in this map.  This is sorted by the long values to ensure
   * repeatable behaviour when iterating.
   *
   * @see java.util.Map.keySet
   */
  public Set<Long> keySet()
  {
    var ts = new TreeSet<Long>();
    ts.addAll(_m.keySet());
    return ts;
  }



}

/* end of file */
