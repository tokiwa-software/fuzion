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

import dev.flang.util.SourcePosition;


/**
 * Actual represents an actual argument, i.e., a type or an expression.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Actual extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  private final SourcePosition _pos;


  /**
   * This actual parsed as a type, null if it can only be parsed as a value.
   */
  public final AbstractType _type;


  /**
   * This actual parsed as a value, Expr.NO_VALUE if it can only be parsed as a type.
   */
  Expr _expr;


  /*-------------------------- constructors ---------------------------*/


  /**
   * Constructor for an actual consisting of type t and expression e.
   *
   * t must be non-null or e must not be NO_VALUE.
   */
  public Actual(SourcePosition pos, AbstractType t, Expr e)
  {
    if (PRECONDITIONS) require
      (t != null || e != Expr.NO_VALUE,
       e != null);

    _pos = pos;
    _type = t;
    _expr = e;
  }


  /**
   * Constructor for an actual consisting of type t.
   *
   * t must be non-null or e must not be NO_VALUE.
   */
  public Actual(AbstractType t)
  {
    this(t.pos(), t, Expr.NO_VALUE);

    if (PRECONDITIONS) require
      (t != null);
  }


  /**
   * Constructor for an actual consisting of expression e.
   *
   */
  public Actual(Expr e)
  {
    this(e.pos(), null, e);

    if (PRECONDITIONS) require
      (e != Expr.NO_VALUE,
       e != null);
  }



  /*-----------------------------  methods  -----------------------------*/


  /**
   * The sourcecode position of this statment, used for error messages.
   */
  public SourcePosition pos()
  {
    return _pos;
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
    if (_expr != Expr.NO_VALUE)
      {
        _expr = _expr.visit(v, outer);
      }
    return this;
  }


  /**
   * Obtain this actual as a Expr of the actual value argument. Produce an error
   * if this does not contain an expression.
   *
   * @return an Expr that is not wrapped in an Actual,
   */
  public Expr expr(Call usedIn)
  {
    var e = _expr;
    if (e == Expr.NO_VALUE)
      {
        AstErrors.actualTypeParameterUsedAsExpression(this, usedIn);
        e =  Expr.ERROR_VALUE;
      }

    return e;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return (_expr == Expr.NO_VALUE ? _type.toString() : _expr.toString());
  }

}

/* end of file */
