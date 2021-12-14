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


/**
 * Cond <description>
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
   *
   * @param t
   */
  public Cond(Expr c)
  {
    this.cond = c;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * visit all the features, expressions, statements within this feature.
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
   * During type inference: Inform the condition that it is used in an
   * environment that expects a bool type.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   */
  public void propagateExpectedType(Resolution res, AbstractFeature outer)
  {
    cond = cond.propagateExpectedType(res, outer, Types.resolved.t_bool);
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
