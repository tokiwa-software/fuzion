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
      (t != null && (Types.resolved == null || !t.isVoid()));

    this._type = t;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * typeForInferencing returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  @Override
  AbstractType typeForInferencing()
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
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    _type = _type.visit(v, outer);
    return v.action(this);
  }


  /**
   * In case this Current() was moved to a new feature, e.g., since it was used
   * in a lambda, a lazy argument or a partial call, the types will be resolved
   * again for a new outer feature that will be an artificial feature added as
   * an inner feature to the original outer feature.
   *
   * @param res the resolution instance
   *
   * @param context the source code context where this Call is used
   *
   * @return this in case outer is what it was when this was created, or a new
   * Expr {@code Current.outer_ref. .. .outer_ref} to access the same current instance
   * from within a new, nested outer feature.
   */
  Expr resolveTypes(Resolution res, Context context)
  {
    var of = _type.feature();
    return of == Types.f_ERROR || of == context.outerFeature()
      ? this
      : new This(pos(), context.outerFeature(), of).resolveTypes(res, context);
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return _type.feature().featureName().baseNameHuman() + ".this";
  }

}

/* end of file */
