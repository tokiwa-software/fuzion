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


/**
 * BiGraph contains a directed bi-partite graph with edges from A to B and
 * efficient lookup for backwards edges from B to A.
 *
 * For the special case that A == B, this Bi-Graph degenerats to a plain graph.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class BiGraph<A, B>
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The edges from A to B
   */
  private MapToN<A,B> _to   = new MapToN<>();


  /**
   * The edges from B to A
   */
  private MapToN<B,A> _back = new MapToN<>();


  /*--------------------------  constructors  ---------------------------*/



  /*-----------------------------  methods  -----------------------------*/


  /**
   * Add edge from 'a' to 'b' to this graph.
   *
   * @param a an instance of A, not null
   *
   * @param b an instance of B, not null
   */
  public void put(A a, B b)
  {
    _to  .put(a, b);
    _back.put(b, a);
  }


  /**
   * Check if edge from 'a' to 'b' is part of this graph.
   *
   * @param a an instance of A, not null
   *
   * @param b an instance of B, not null
   */
  public boolean contains(A a, B b)
  {
    return _to.contains(a,b);
  }

  /**
   * Get the set of successors of a, return empty set if there are none.
   *
   * @param an instance of A, not null.
   */
  public Set<B> successors(A a)
  {
    return _to.successors(a);
  }

  /**
   * Get the set of predecessors of a, return empty set if there are none.
   *
   * @param an instance of A, not null.
   */
  public Set<A> predecessors(B b)
  {
    return _back.successors(b);
  }


}

/* end of file */
