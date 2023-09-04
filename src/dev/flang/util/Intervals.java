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
 * Source of class Intervals
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.util.TreeMap;

/**
 * Intervals manages properties over intervals of integers.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Intervals<PROPERTY> extends ANY
{

  /*----------------------------  variables  ----------------------------*/


  /**
   * Map of all positions where PROPERTY changes
   */
  private final TreeMap<Integer, PROPERTY> _map = new TreeMap<>();


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create a set of intervals with the given default property
   */
  public Intervals(PROPERTY p)
  {
    _map.put(Integer.MIN_VALUE, p);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Record property p for the interval min..max-1.
   *
   * @param min the first value for which p holds
   *
   * @param max the value after the last value for which p holds. May be <= min
   * to specify the empty interval.
   *
   * @param p tne new property to store for min..max-1
   */
  public void add(int min, int max, PROPERTY p)
  {
    if (min < max)
      {
        PROPERTY next = get(max);
        for (var v : _map.subMap(min, max).keySet())
          {
            _map.remove(v);
          }
        if (get(min) != p)
          {
            _map.put(min, p);
          }
        if (next != p)
          {
            _map.put(max, p);
          }
      }

    if (POSTCONDITIONS) ensure
                          (min >= max || get(min            ) == p,
                           min >= max || get(max - 1        ) == p,
                           min >= max || get((min + max) / 2) == p
                           // for all i: get(i) == min <= i < max ? p : old get(i)
                           );
  }


  /**
   * Find the property stored at the given position.
   *
   * @param at a position, any integer value
   */
  public PROPERTY get(int at)
  {
    return _map.subMap(Integer.MIN_VALUE, true, at, true).lastEntry().getValue();
  }

}

/* end of file */
