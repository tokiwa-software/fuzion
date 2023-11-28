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
 * Source of class Partial
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.parser.Operator;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Partial represents partially applied functions and related syntax sugar and provides mathos for handling of partial evalution.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Partial extends ExprWithPos
{


  /*----------------------------  constants  ----------------------------*/


  /*-------------------------  static variables -------------------------*/


  static int _partialFunctionArgumentId_ = 0;


  /*----------------------------  variables  ----------------------------*/


  final String _op;


  private Function _function = null;

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a partially applied operator expresion like `+` or
   * `**`. This can expand to a lambda of the form `x -> +x`, `x -> x+`, or `x,y
   * -> x+y`.
   *
   * @param pos the source code position of the operator
   *
   * @param op the operator text, e.g. "+".
   */
  public Partial(SourcePosition pos, String op)
  {
    super(pos);

    _op = op;
  }


  /*--------------------------  static methods  -------------------------*/



  static String argName()
  {
    return FuzionConstants.PARTIAL_FUNCTION_ARGUMENT_PREFIX + (_partialFunctionArgumentId_++);
  }
  static ParsedName argName(SourcePosition pos)
  {
    return new ParsedName(pos, argName());
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * During type inference: Inform this expression that it is used in an
   * environment that expects the given type.  In particular, if this
   * expression's result is assigned to a field, this will be called with the
   * type of the field.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   *
   * @param t the expected type.
   *
   * @return either this or a new Expr that replaces thiz and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the expression that reads the field.
   */
  public Expr propagateExpectedType(Resolution res, AbstractFeature outer, AbstractType t)
  {
    t = t.functionTypeFromChoice();
    var type = propagateExpectedType2(res, outer, t, false);
    return this;
  }



  /**
   * Special version of propagateExpectedType(res, outer, t) tries to infer the
   * result type of a lambda.
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
  public AbstractType propagateExpectedType2(Resolution res, AbstractFeature outer, AbstractType t, boolean inferResultType)
  {
    AbstractType result = inferResultType ? Types.t_UNDEFINED : t;
    if (t.isFunctionType() && t.arity() == 1)
      {
        var a = argName(pos());
        var op = FuzionConstants.UNARY_OPERATOR_PREFIX + _op;
        _function = new Function(pos(),
                                 new List<>(a),
                                 new ParsedCall(new ParsedCall(null, a),
                                                new ParsedName(pos(), op),
                                                new List<>()));
        result = _function.propagateExpectedType2(res, outer, t, inferResultType);
      }
    else if (t.isFunctionType() && t.arity() == 2)
      {
        var a = argName(pos());
        var b = argName(pos());
        var op = FuzionConstants.INFIX_OPERATOR_PREFIX + _op;
        _function = new Function(pos(),
                                 new List<>(a, b),
                                 new ParsedCall(new ParsedCall(null, a),
                                                new ParsedName(pos(), op),
                                                new List<>(new Actual(new ParsedCall(null, b)))));
        result = _function.propagateExpectedType2(res, outer, t, inferResultType);
      }
    return result;
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
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    return _function == null ? this
                             : _function.visit(v, outer);
  }


  /**
   * type returns the type of this expression or Types.t_ERROR if the type is
   * still unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or t_ERROR in case it is not known yet.
   */
  public AbstractType type()
  {
    return _function == null ? Types.t_UNDEFINED
                             : _function.type();
  }


  /**
   * typeForInferencing returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  AbstractType typeForInferencing()
  {
    // unlike type(), we do not produce an error but just return null here since
    // everything might eventually turn out fine in this case.
    return _function == null ? null
                             : _function.type();
  }


  /**
   * Resolve syntactic sugar, e.g., by replacing anonymous inner functions by
   * declaration of corresponding inner features. Add (f,<>) to the list of
   * features to be searched for runtime types to be layouted.
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this expression.
   */
  public Expr resolveSyntacticSugar2(Resolution res, AbstractFeature outer)
  {
    return _function == null ? this
                             : _function.resolveSyntacticSugar2(res, outer);
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "(" + _op + ")";
  }

}

/* end of file */
