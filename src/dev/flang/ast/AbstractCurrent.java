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
  private AbstractType _type = null;


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

    this._type = t;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * typeIfKnown returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  AbstractType typeIfKnown()
  {
    return _type;
  }


  /**
   * visit all the expressions within this feature.
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
    _type = _type.visit(v, outer);
    return this;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return _type.featureOfType().featureName().baseName() + ".this";
  }

}

/* end of file */
