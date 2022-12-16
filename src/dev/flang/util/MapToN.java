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
 * Source of class MapToN
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;


/**
 * MapToN mapes comparable objects to a set of comparable objects
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class MapToN<A, B>
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The underlying map.
   */
  private TreeMap<A, TreeSet<B>> _map = new TreeMap<>();


  /*--------------------------  constructors  ---------------------------*/



  /*-----------------------------  methods  -----------------------------*/


  /**
   * Add mapping from 'a' to 'b' to this map.
   *
   * @param a an instance of A, not null
   *
   * @param b an instance of B, not null
   */
  public boolean put(A a, B b)
  {
    var s = _map.get(a);
    if (s == null)
      {
        s = new TreeSet<B>();
        _map.put(a, s);
      }
    return s.add(b);
  }


  /**
   * Check if mapping from 'a' to 'b' is part of this map.
   *
   * @param a an instance of A, not null
   *
   * @param b an instance of B, not null
   */
  public boolean contains(A a, B b)
  {
    var s = _map.get(a);
    return s != null && s.contains(b);
  }


  /**
   * Get the set of successors of a, return empty set if there are none.
   *
   * @param an instance of A, not null.
   */
  public Set<B> successors(A a)
  {
    Set<B> result = _map.get(a);
    if (result == null)
      {
        result = new TreeSet<>();
      }
    return result;
  }

}

/* end of file */
