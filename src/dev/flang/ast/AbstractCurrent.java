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
 * Source of class AbstractCurrent
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Iterator;

import dev.flang.util.SourcePosition;


/**
 * AbstractCurrent is an expression that returns the current object
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractCurrent extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The type of this, set during resolveTypes.
   */
  private AbstractType type_ = null;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param t the result type
   */
  public AbstractCurrent(AbstractType t)
  {
    if (PRECONDITIONS) require
      (t != null);

    this.type_ = t;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * type returns the type of this expression or Types.t_ERROR if the type is
   * still unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or t_ERROR in case it is not known yet.
   */
  public AbstractType type()
  {
    return type_;
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this.
   */
  public AbstractCurrent visit(FeatureVisitor v, AbstractFeature outer)
  {
    type_ = type_ instanceof Type tt ? tt.visit(v, outer) : type_;
    return this;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return type_.featureOfType().featureName().baseName() + ".this";
  }

}

/* end of file */
