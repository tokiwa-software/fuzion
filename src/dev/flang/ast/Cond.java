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
public class Cond
{


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  public Expr cond;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param c
   */
  public Cond(Expr c)
  {
    this.cond = c;
  }


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
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   */
  public void visit(FeatureVisitor v, AbstractFeature outer)
  {
    cond = cond.visit(v, outer);
    v.action(this, outer);
  }


  /**
   * visit all the expressions within this Cond.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited expressions
   */
  public void visitExpressions(ExpressionVisitor v)
  {
    cond.visitExpressions(v);
  }


  /**
   * check the type of the expression of this condition.
   */
  public void checkTypes()
  {
    var t = cond.type();
    if (t != Types.t_ERROR && Types.resolved.t_bool.compareTo(t) != 0)
      {
        AstErrors.contractExpressionMustResultInBool(cond);
      }
  }


  /**
   * During type inference: Inform the condition that it is used in an
   * environment that expects a bool type.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Cond is used
   */
  public void propagateExpectedType(Resolution res, Context context)
  {
    cond = cond.propagateExpectedType(res, context, Types.resolved.t_bool);
  }


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
