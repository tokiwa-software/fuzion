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
 * Source of class StatementVisitor
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;


/**
 * This is used to perform some action on all Expr's within a Feature or other
 * structure of the AST.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public interface ExpressionVisitor
{


  /*-----------------------------  methods  -----------------------------*/


  /**
   * action is to be called an all Expr's encountered.
   */
  abstract void action (Expr e);

  /**
   * action is to be called an all AbstractCase's encountered.
   */
  default void action(AbstractCase c)
  {
  }

}

/* end of file */
