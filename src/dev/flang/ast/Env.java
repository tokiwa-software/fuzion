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
 * Source of class Env
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Iterator;

import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Env is an expression of the form 'a.b.c.env' that permits accessing a one-way
 * monad in the current environment.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Env extends ExprWithPos
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * the type of this Env entry.
   */
  AbstractType _type;


  // id of the clazz of the monad as stored in outer clazz.
  //
  // NYI: remove, used in interpreter.
  public int _clazzId = -1;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param qual
   *
   * @param a
   */
  public Env(SourcePosition pos, AbstractType t)
  {
    super(pos);

    this._type = t;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * typeForFeatureResultTypeInferencing returns the type of this expression or
   * null if the type is still unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  AbstractType typeForFeatureResultTypeInferencing()
  {
    return _type;
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this or an alternative Expr if the action performed during the
   * visit replaces this by the alternative.
   */
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    _type = _type.visit(v, outer);
    return this;
  }


  /**
   * Find all the types used in this that refer to formal generic arguments of
   * this or any of this' outer classes.
   *
   * @param outer the root feature that contains this statement.
   */
  public void findGenerics(AbstractFeature outer)
  {
    // NYI: Check if qual starts with the name of a formal generic in outer or
    // outer.outer..., flag an error if this is the case.
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param outer the root feature that contains this statement.
   *
   * @return a call to the outer references to access the value represented by
   * this.
   */
  public Expr resolveTypes(Resolution res, AbstractFeature outer)
  {
    _type = _type.resolve(res, outer);
    return this;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return _type + ".env";
  }


}

/* end of file */
