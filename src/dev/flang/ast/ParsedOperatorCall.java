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
   * Has this been put into parentheses? If so, it may no longer be used as
   * chained boolean `(a < b) < c`, but it may still used as partial call `l.map
   * (+x)`.
   */
  private boolean _inParentheses = false;


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
   * @param rhs the right hand side
   */
  public ParsedOperatorCall(Expr target, ParsedName name, Expr rhs)
  {
    super(target, name, new List<>(rhs));
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Is this an operator call like `a+b` or `-x` in contrast to a named call `f`
   * or `t.g`?
   *
   * @param parenthesesAllowed true if an operator call in parentheses is still
   * ok.  (+x)`.
   */
  boolean isOperatorCall(boolean parenthesesAllowed)
  {
    return parenthesesAllowed || !_inParentheses;
  }


  /**
   * Is this Expr put into parentheses `(`/`)`. If so, we no longer want to do
   * certain transformations like chained booleans `a < b < c` to `a < b && b <
   * c`.
   */
  public void putInParentheses()
  {
    _inParentheses = true;
  }

}

/* end of file */
