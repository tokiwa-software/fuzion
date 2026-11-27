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

import java.util.function.Supplier;

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


  /*-----------------------------  classes  -----------------------------*/


  /**
   * Call used to access automatically generated arguments in partial call, i.e,
   * if you do
   *
   *   f (i32,i32)->i32 => (+)
   *
   * this will be converted into
   *
   *   f (i32,i32)->i32 => pa1,pa2 -> pa1 + pa2
   *
   * Then the two calls {@code pa1} and {@code pa2} on the right hand side will be done
   * using {@code PartialArg}. This is used in AstErrors to decide if a call is an
   * automatically generated partial call.
   */
  static class PartialArg extends ParsedCall
  {
    PartialArg(SourcePosition pos)
    {
      super(new ParsedName(pos, argName()));
    }

    @Override
    public AbstractType asType()
    {
      return null;
    }

    /**
     * The source text produced by Expr.sourceText would be the original call,
     * i.e., {@code +}, which is very confusing, so we create a verbose text with the
     * actual argument name here.
     */
    @Override
    public String sourceText()
    {
      return "partial application argument " + argName();
    }
  }


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
   * Constructor for a partially applied operator expression like {@code +} or
   * {@code **}. This can expand to a lambda of the form {@code x -> +x}, {@code x -> x+}, or
   * {@code x,y -> x+y}.
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
    return new PartialArg(pos);
  }


  /**
   * Create a partial call of the form {@code .f} that will be turned into a lambda {@code x -> x.f}.
   *
   * @param pos the source position of the call
   *
   * @param call a callback into the parser to create the call for the target
   * provided as an argument.  The target must be the cal to the bound variable
   * of the lambda expression.
   *
   * @return the corresponding lambda expression.
   */
  public static Function dotCall(SourcePosition pos, java.util.function.Function<Expr,Expr> call)
  {
    var a = argName(pos);
    var c = call.apply(a);
    return new Function(c.pos(),
                        new List<>(a),
                        c);
  }


  /*-----------------------------  methods  -----------------------------*/


  /** {@inheritDoc} */
  @Override
  Expr propagateExpectedType(Resolution res, Context context, AbstractType t, Supplier<String> from)
  {
    Expr result = this;
    t = t.functionTypeFromChoice(res, context);
    var type = propagateTypeAndInferResult(res, context, t, false, from);
    if (_function != null)
      {
        result = _function.propagateExpectedType(res, context, type, from);
      }
    return result;
  }


  /** {@inheritDoc} */
  @Override
  AbstractType propagateTypeAndInferResult(Resolution res, Context context, AbstractType t, boolean inferResultType, Supplier<String> from)
  {
    AbstractType result = inferResultType ? Types.t_UNDEFINED : t;
    if (_function == null && t.isFunctionLikeType() && (t.arity(res) == 1 || t.arity(res) == 2))
      {
        var a = argName(pos());
        List<Expr> args = new List<>(a);
        List<Expr> actuals = new List<>();
        String op = FuzionConstants.UNARY_OPERATOR_PREFIX + _op;
        if (t.arity(res) == 2)
          {
            var b = argName(pos());
            args.add(b);
            actuals.add(b);
            op = FuzionConstants.INFIX_RIGHT_OR_LEFT_OPERATOR_PREFIX + _op;
          }
        _function = new Function(pos(),
                                 args,
                                 new ParsedCall(a,
                                                new ParsedName(pos(), op),
                                                actuals));
      }
    if (_function != null)
      {
        result = _function.propagateTypeAndInferResult(res, context, t, inferResultType, from);
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
  @Override
  protected Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    return _function == null ? this
                             : _function.visit(v, outer);
  }


  /** {@inheritDoc} */
  @Override
  AbstractType typeForInferencing()
  {
    // unlike type(), we do not produce an error but just return null here since
    // everything might eventually turn out fine in this case.
    return _function == null ? null
                             : _function.typeForInferencing();
  }


  /** {@inheritDoc} */
  @Override
  public AbstractType type()
  {
    var result = typeForInferencing();
    if (result  == null)
      {
        AstErrors.noTypeInferenceFromLambda(_range);
      }
    return result == null
      ? Types.t_ERROR
      : result;
  }


  /**
   * toString
   *
   * @return
   */
  @Override
  public String toString()
  {
    return "(" + _op + ")";
  }

}

/* end of file */
