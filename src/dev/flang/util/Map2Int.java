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
 * Source of class Map2Int
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import java.util.stream.IntStream;


/**
 * Map2Int gives an efficient mapping from an (possibly comparable) instance to
 * int.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Map2Int<T> extends ANY
{

  /*----------------------------  variables  ----------------------------*/


  /**
   * The underlying map from T to int
   */
  private Map<T, Integer> _mapT2Int = isComparable()
    ? new TreeMap<>()
    : new HashMap<>();

  /**
   * The underlying map from int to T
   */
  private ArrayList<T> _mapInt2T = new ArrayList<>();


  /**
   * The base, i.e, an offset added to the integers T is mapped to.
   */
  public final int _base;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create map with 0 as base offset.
   */
  public Map2Int()
  {
    this(0);
  }


  /**
   * Create map with given base offset.
   */
  public Map2Int(int base)
  {
    _base = base;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * redefined in MapComparable2Int.
   */
  protected boolean isComparable()
  {
    return false;
  }


  /**
   * check if t has already been added to map. If not, add it. In any case,
   * return its index.
   *
   * @param t a new element to add
   *
   * @return the index of t
   */
  public int add(T t)
  {
    var res = _mapT2Int.get(t);
    if (res == null)
      {
        res = _mapT2Int.size();
        _mapT2Int.put(t, res);
        _mapInt2T.add(t);
      }
    return res + _base;
  }


  /**
   * get the index of an element
   *
   * @param t an element
   *
   * @return t's index or _base-1 if t was not added.
   */
  public int get(T t)
  {
    var res = _mapT2Int.get(t);
    return _base + (res == null ? - 1 : res);
  }


  /**
   * get the index of an element
   *
   * @return t's index or _base-1 if t was not added.
   */
  public T get(int i)
  {
    if (PRECONDITIONS) require
      (i - _base >= 0,
       i - _base < size());

    return _mapInt2T.get(i - _base);
  }


  /**
   * Number of entries in the map
   */
  public int size()
  {
    return _mapT2Int.size();
  }


  /**
   * Get a stream of all the integers this map maps to.
   */
  public IntStream ints()
  {
    return IntStream.range(_base, _base + size() - 1);
  }

}

/* end of file */
