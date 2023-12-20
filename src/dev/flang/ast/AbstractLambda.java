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
 * Source of class AbstractLambda
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.SourcePosition;


/**
 * AbstractLambda is the super class of lambda expressions and partially applied functions.q
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractLambda extends ExprWithPos
{


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the position
   */
  AbstractLambda(SourcePosition pos)
  {
    super(pos);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Try to infer the result of a lambda or partially applied function from a
   * partial expected type result type of a lambda.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   *
   * @param t the expected type, this might be a Function type with some type
   * parameters, particularly the result type, still undefined.
   *
   * @return the result type inferred from this lambda or Types.t_UNDEFINED if
   * not result type available.
   */
  public AbstractType inferLambdaResultType(Resolution res, AbstractFeature outer, AbstractType t)
  {
    return propagateTypeAndInferResult(res, outer, t, true);
  }


  /**
   * Special version of propagateExpectedType(res, outer, t) that tries to infer
   * the result type of a lambda or partially applied function.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   *
   * @param t the expected type.
   *
   * @param inferResultType true if the result type of this lambda should be
   * inferred.
   *
   * @return if inferResultType, the result type inferred from this lambda or
   * Types.t_UNDEFINED if not result type available.  if !inferResultType, t. In
   * case of error, return Types.t_ERROR.
   */
  protected abstract AbstractType propagateTypeAndInferResult(Resolution res,
                                                              AbstractFeature outer,
                                                              AbstractType t,
                                                              boolean inferResultType);

}

/* end of file */
