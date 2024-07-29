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
 * Source code of class IntMap
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.util.HashMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;


/**
 * IntMap implements a Map from int to any type T.
 *
 * This permits exchanging the internal implementation easily without changing
 * all the places that use this map. This intentionally does not inherit from
 * `java.util.Map` to provide only the essential operations.
 *
 * Even though this might internally be use hashing, all operations should be
 * implemented in a way that provides reproducible execution, i.e, iteration do
 * not depend on memory layout, order of addition of entries, etc.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class IntMap<T>
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Underlying implementation.
   *
   * This currently uses a @see java.util.HashMap.
   *
   * NYI: OPTIMIZATION: We might use our own sparse array implementation here to
   * improve performance by avoiding boxing into Integer, allocation of
   * HashMap.Node elements, calling of Integer.hashCode(), etc.
   */
  HashMap<Integer, T> _m = new HashMap<>();


  /*-----------------------------  methods  -----------------------------*/


  /**
   * @see java.util.Map.size
   */
  public int size()
  {
    return _m.size();
  }


  /**
   * @see java.util.Map.get
   */
  public T get(int i)
  {
    return _m.get(Integer.valueOf(i));
  }


  /**
   * @see java.util.Map.getOrDefault
   */
  public T getOrDefault(int i, T def)
  {
    return _m.getOrDefault(Integer.valueOf(i), def);
  }


  /**
   * @see java.util.Map.put
   */
  public T put(int i, T v)
  {
    return _m.put(Integer.valueOf(i), v);
  }


  /**
   * All keys in this map.  This is sorted by the integer values to ensure
   * repeatable behaviour when iterating.
   *
   * @see java.util.Map.keySet
   */
  public Set<Integer> keySet()
  {
    var ts = new TreeSet<Integer>();
    ts.addAll(_m.keySet());
    return ts;
  }


}

/* end of file */
