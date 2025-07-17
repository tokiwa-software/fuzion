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
 * Source of class Cond
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.List;


/**
 * Cond
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public record Cond(Expr cond)
{

  /*-------------------------  static methods  --------------------------*/


  /**
   * Wrap Expr instances from given list into new {@code Cond} instances
   *
   * @param b a list of {@code Expr} to be used as conditions
   *
   * @return a new list with each {@code Expr} form {@code l} wrapped into a {@code Cond}.
   */
  public static List<Cond> from(Block b)
  {
    return b._expressions.map2(e->new Cond(e));
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return cond.toString();
  }


}

/* end of file */
