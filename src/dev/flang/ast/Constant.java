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
 * Source of class Constant
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.function.Supplier;

import dev.flang.util.SourcePosition;


/**
 * Constant represents a constant in the source code such as '3.14', 'true',
 * '"Hello"'.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class Constant extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  private final SourcePosition _pos;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a Constant loaded from library
   * with source position loaded on demand only.
   */
  public Constant()
  {
    _pos = SourcePosition.notAvailable;
  }


  /**
   * Constructor for a Constant at the given source code position.
   *
   * @param pos the sourcecode position, used for error messages.
   */
  public Constant(SourcePosition pos)
  {
    if (PRECONDITIONS) require
      (pos != null);

    this._pos = pos;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Serialized form of the data of this constant.
   */
  public abstract byte[] data();


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  public SourcePosition pos()
  {
    return _pos;
  }


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this or an alternative Expr if the action performed during the
   * visit replaces this by the alternative.
   */
  @Override
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    return v.action(this);
  }


  /**
   * Check that this constant is in the range allowed for its type().
   *
   * This is redefined by NumLiteral to check the range of the constant.
   */
  void checkRange()
  {
  }


  /**
   * This expression as a compile time constant.
   */
  @Override
  public Constant asCompileTimeConstant()
  {
    return this;
  }


  /**
   * Origin of this constant. This is either this constant itself for a
   * BoolConst, NumLiteral or StrConst, or the instance of AbstractCall or
   * InlineArray this was created from.
   */
  public Expr origin()
  {
    return this;
  }


  /**
   * Try to perform partial application such that this expression matches
   * {@code expectedType}.  Note that this may happen twice:
   *
   * 1. during RESOLVING_DECLARATIONS phase of outer when resolving arguments to
   *    a call such as {@code l.map +1}. In this case, expectedType may be a function
   *    type {@code Function R A} with generic arguments not yet replaced by actual
   *    arguments, in particular the result type {@code R} is unknown since it is the
   *    result type of this expression.
   *
   * 2. during TYPES_INFERENCING phase when the target variable's type is fully
   *    resolved and this gets propagated to this expression.
   *
   * Note that this does not perform resolveTypes on the results since that
   * would be too early during 1. but it is required in 2.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @param expectedType the expected type.
   */
  @Override
  Expr propagateExpectedType(Resolution res, Context context, AbstractType t, Supplier<String> from)
  {
    var result = t.isFunctionType() && t.arity() == 0
      ? new Function(_pos, NO_EXPRS, this)
      : this;
    if (result != this)
      {
        result = result.propagateExpectedType(res, context, t, from);
      }
    return result;
  }
  @Override
  Expr propagateExpectedTypeForPartial(Resolution res, Context context, AbstractType expectedType)
  {
    return expectedType.isFunctionType() && expectedType.arity() == 0
       ? new Function(_pos, NO_EXPRS, this)
       : this;
  }


  /**
   * Resolve syntactic sugar, e.g., by replacing anonymous inner functions by
   * declaration of corresponding inner features. Add (f,{@literal <>}) to the list of
   * features to be searched for runtime types to be layouted.
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Expr is used
   */
  protected Expr resolveSyntacticSugar2(Resolution res, Context context)
  {
     return this;
  }

}

/* end of file */
