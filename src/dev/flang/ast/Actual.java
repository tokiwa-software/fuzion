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
 * Source of class Actual
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.ANY;


/**
 * Actual represents an actual argument, i.e., a type or an expression.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Actual extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * This actual parsed as a type, null if it can only be parsed as a value.
   */
  public final AbstractType _type;


  /**
   * This actual parsed as a value, null if it can only be parsed as a type.
   */
  public final Expr _expr;


  /*-------------------------- constructors ---------------------------*/


  /**
   * Constructor for an actual consisting of type t and expression e.
   *
   * At least one of t and e must be non-null.
   */
  public Actual(AbstractType t, Expr e)
  {
    if (PRECONDITIONS) require
      (t != null || e != null);

    _type = t;
    _expr = e;
  }

}

/* end of file */
