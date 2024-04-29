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

import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Partial represents partially applied operators and related syntax sugar and
 * provides methods for handling of partial evaluation.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Partial extends AbstractLambda
{


  /*----------------------------  constants  ----------------------------*/


  /*-------------------------  static variables -------------------------*/


  /**
   * We will need to generate names for input variables of lambda expressions,
   * so this counter is used to ensure these names are unique.
   */
  static int _partialFunctionArgumentId_ = 0;


  /*----------------------------  variables  ----------------------------*/


  /**
   * The operator this partial function implements.
   */
  final String _op;


  /**
   * Once we have received the target type through propagateExpectedType[2](),
   * this will be set to the Function instance that implements the lambda and
   * that will replace this instance.
   */
  private Function _function = null;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a partially applied operator expression like `+` or
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


  /**
   * Create a new, unique argument name for use in a automatically generated
   * lambda.
   */
  static String argName()
  {
    return FuzionConstants.PARTIAL_FUNCTION_ARGUMENT_PREFIX + (_partialFunctionArgumentId_++);
  }


  /**
   * Create a new, unique argument name for use in a automatically generated
   * lambda and return is as an instance of ParsedCall at the given position.
   */
  static ParsedCall argName(SourcePosition pos)
  {
    return new ParsedCall(new ParsedName(pos, argName()));
  }


  /**
   * Create a partial call of the form `.f` that will be turned into a lambda `x -> x.f`.
   *
   * @param pos the source position of the call
   *
   * @param call a callback into the parser to create the call for the target
   * provided as an argument.  The target must be the cal to the bound variable
   * of the lambda expression.
   *
   * @return the corresponding lambda expression.
   */
  public static Function dotCall(SourcePosition pos, java.util.function.Function<Expr,Call> call)
  {
    var a = argName(pos);
    var c = call.apply(a);
    return new Function(c.pos(),
                        new List<>(a),
                        c);
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
    Expr result = this;
    t = t.functionTypeFromChoice();
    var type = propagateTypeAndInferResult(res, outer, t, false);
    if (_function != null)
      {
        result = _function.propagateExpectedType(res, outer, type);
      }
    return result;
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
  public AbstractType propagateTypeAndInferResult(Resolution res, AbstractFeature outer, AbstractType t, boolean inferResultType)
  {
    AbstractType result = inferResultType ? Types.t_UNDEFINED : t;
    if (_function == null && t.isFunctionType() && (t.arity() == 1 || t.arity() == 2))
      {
        var a = argName(pos());
        List<Expr> args = new List<>(a);
        List<Expr> actuals = new List<>();
        String op = FuzionConstants.UNARY_OPERATOR_PREFIX + _op;
        if (t.arity() == 2)
          {
            var b = argName(pos());
            args.add(b);
            actuals.add(b);
            op = FuzionConstants.INFIX_OPERATOR_PREFIX + _op;
          }
        _function = new Function(pos(),
                                 args,
                                 new ParsedCall(a,
                                                new ParsedName(pos(), op),
                                                actuals));
      }
    if (_function != null)
      {
        result = _function.propagateTypeAndInferResult(res, outer, t, inferResultType);
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
                             : _function.typeForInferencing();
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
