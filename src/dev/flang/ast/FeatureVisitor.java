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
 * Source of class FeatureVisitor
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.ANY;

/**
 * This is used to perform some action on a feature and all the expressions,
 * expressions, types, etc. within this feature
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class FeatureVisitor extends ANY
{

  /*----------------------------  constants  ----------------------------*/

  /*----------------------------  variables  ----------------------------*/


  /*---------------------------  constructors  --------------------------*/


  public FeatureVisitor()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  public void         action      (AbstractAssign   a, AbstractFeature outer) { }
  public void         actionBefore(Block            b, AbstractFeature outer) { }
  public void         actionAfter (Block            b, AbstractFeature outer) { }
  public void         action      (AbstractCall     c                       ) { }
  public void         action      (AbstractConstant c                       ) { }
  // this is used for resolving dot-type-calls that omit the .type
  public void         actionBefore(Call             c, AbstractFeature outer) { }
  public Expr         action      (Call             c, AbstractFeature outer) { return c; }
  public Expr         action      (DotType          d, AbstractFeature outer) { return d; }
  public void         actionBefore(AbstractCase     c                       ) { }
  public void         actionAfter (AbstractCase     c                       ) { }
  public void         action      (Cond             c, AbstractFeature outer) { }
  public Expr         action      (Destructure      d, AbstractFeature outer) { return d; }
  public Expr         action      (Feature          f, AbstractFeature outer) { return f; }
  public Expr         action      (Function         f, AbstractFeature outer) { return f; }
  public Expr         action      (If               i, AbstractFeature outer) { return i; }
  public void         action      (Impl             i, AbstractFeature outer) { }
  public Expr         action      (InlineArray      i, AbstractFeature outer) { return i; }
  public void         action      (AbstractMatch    m                       ) { }
  public void         action      (Match            m, AbstractFeature outer) { }
  public void         action      (Tag              b, AbstractFeature outer) { }
  public Expr         action      (This             t, AbstractFeature outer) { return t; }
  public AbstractType action      (AbstractType     t, AbstractFeature outer) { return t; }

  /**
   * Visitors that want a different treatment for visiting actual arguments of a
   * call can redefine this method to return false when visiting actuals is not
   * desired, but, e.g., done later manually.
   */
  public boolean doVisitActuals() { return !visitActualsLate(); }

  /**
   * When visiting a Call, the actuals value arguments are visited before the
   * target and before the call itself.
   *
   * If this is redefined to return true, the order will be changed to target,
   * call itself and actuals.  The reason is that nested lazy values must be
   * processed from outside to the inside to ensure that the outer feature of
   * the inner actual is set correctly (i.e., set to the Lazy instance created
   * for the outer lazy value).
   */
  public boolean visitActualsLate() { return false; }

}

/* end of file */
