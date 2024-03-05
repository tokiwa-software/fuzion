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
 * Source of class ParsedOperatorCall
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.List;


/**
 * Any call that was created by the parser from an infix, prefix or postfix
 * operator.  This is used to distinguish a call like
 *
 *   -a
 *
 * from
 *
 *   a.prefix -
 *
 * This is needed since the former could serve as a partial call an expand
 * to
 *
 *  x -> x-a
 *
 * while this should not be possible for the latter.
 */
public class ParsedOperatorCall extends ParsedCall
{

  /*-----------------------------  fields  ------------------------------*/


  /**
   * Is this really an operator call? This in invalidated if placed inside
   * parentheses `(-a)` or `(a+b)`.
   */
  private boolean _isOperatorCall = true;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a prefix or postfix operator on target.
   *
   * @param target the target of the call.
   *
   * @param name the name of the called feature
   */
  public ParsedOperatorCall(Expr target, ParsedName name)
  {
    super(target, name);
  }


  /**
   * Constructor for an infix operator with target as left hand
   * side and argument as right hand side.
   * arguments 'la'.
   *
   * @param target the left hand side.
   *
   * @param name the name of the called feature
   *
   * @param argument the right hand side
   */
  public ParsedOperatorCall(Expr target, ParsedName name, Expr rhs)
  {
    super(target, name, new List<>(new Actual(rhs)));
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Is this an operator call like `a+b` or `-x` in contrast to a named call `f`
   * or `t.g`?
   */
  boolean isOperatorCall()
  {
    return _isOperatorCall;
  }


  /**
   * Is this Expr put into parentheses `(`/`)`. If so, we no longer want to do
   * certain transformations like chained booleans `a < b < c` to `a < b && b <
   * c`.
   */
  public void putInParentheses()
  {
    _isOperatorCall = false;
  }

}

/* end of file */
