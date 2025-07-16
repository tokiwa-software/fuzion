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
 * Source of class Function
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.function.Supplier;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Function represents a lambda expression {@code (x,y) -> f x y} in Fuzion.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Function extends AbstractLambda
{


  /*----------------------------  constants  ----------------------------*/


  static final List<AbstractCall> NO_CALLS = new List<>();


  /*-------------------------  static variables -------------------------*/

  /**
   * quick-and-dirty way to make unique names for function wrappers
   */
  static private long id = 0;


  /*----------------------------  variables  ----------------------------*/


  /**
   * For a function that declares a new anonymous feature, this will be the
   * resulting call that creates an instance of a subclass for Function/Routine
   * whose call() function implements the function.
   */
  Call _call;


  /**
   * The implementation of {@code Function.call} that contains the code of this lambda.
   */
  Feature _feature;


  /**
   * The inferred type
   */
  AbstractType _type;


  /**
   * In case `type()` is called and we have not received a type via
   * `propagateExpectedType`, this will be called. This is currently set by
   * `resolveTypes` to handle cases like `()->true`.
   */
  Runnable _resultTypeLastResort = ()->{};


  /**
   * For a function that declares a new anonymous feature, these are the generic
   * arguments to Function/Routine the anonymous feature inherits from. This
   * will be used put the correct return type in case of a fun declaration using
   * => that requires type inference.
   *
   * I.e. a call to ({@code Function}/{@code Unary}/{@code Binary}/{@code Nullary}/{@code Lazy <generics>})
   */
  Call _inheritsCall;


  /**
   * The feature that inherits from {@code Function/Unary/Binary/Nullary/Lazy/...} that implements this lambda in
   * its {@code call} feature.
   */
  Feature _wrapper;


  /**
   * Names of argument fields {@code x, y} of a lambda {@code x, y -> f x y}
   */
  final List<Expr> _namesAsExprs;
  final List<ParsedName> _names;


  /**
   * the right hand side of the '->', as produced by the parser
   */
  final Expr _originalExpr;


  /**
   * the right hand side of the '->', replaced by Expr.NO_VALUE in case of error.
   */
  private Expr _expr;
  public Expr expr() { return _expr; }


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a lambda of the form
   *
   *   (x,y) -> x*y
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param names the names of the arguments, "x", "y". The are parsed as
   * expressions and these might end up being turned into types by asParsedType
   * if this lambda ends up used as an actual type argument.
   *
   * @param e the code on the right hand side of '->'.
   */
  public Function(SourcePosition pos,
                  List<Expr> names,
                  Expr e)
  {
    super(pos);

    _namesAsExprs = names;
    _names = names
      .map2(n ->
            {
              var pn = n.asParsedName();
              if (pn == null)
                {
                  AstErrors.argNameExpectedInLambda(n);
                }
              return pn;
            })
      .filter(n -> n != null);
    _originalExpr = e;
    _expr = e;
  }


  @Override
  public ParsedType asParsedType()
  {
    var resType = _expr.asParsedType();
    ParsedType result = null;
    if (resType != null)
      {
        List<AbstractType> argTypes = _namesAsExprs != null
          ? _namesAsExprs.map2(e -> e.asParsedType())
          : _names.map2(n -> new ParsedType(n.pos(),
                                            n._name,
                                            new List<>(),
                                            null)
                        );
        if (argTypes.stream().allMatch(t -> t != null))
          {
            result = UnresolvedType.funType(pos(),
                                            resType,
                                            argTypes);
          }
      }
    return result;
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
   * @param context the source code context where this Expr is used
   *
   * @param t the expected type.
   *
   * @param from for error output: if non-null, produces a String describing
   * where the expected type came from.
   *
   * @return either this or a new Expr that replaces thiz and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the expression that reads the field.
   */
  Expr propagateExpectedType(Resolution res, Context context, AbstractType t, Supplier<String> from)
  {
    _type = propagateTypeAndInferResult(res, context, t.functionTypeFromChoice(context), false, from);
    return this;
  }


  /**
   * In case of partial application and lazy values, it might happen that the
   * target of calls might need to get resolved again since the original target
   * was relative to the feature the expression was used in, while now it must
   * be resolved relative to the feature declared implicitly by this lambda,
   * which add one level of nesting.
   *
   * This must be called after this Function's resolveTypes was resolved since
   * this creates the new feature surrounding the expression.
   *
   * What this does is for all calls {@code c} in the expression that is wrapped in
   * this lambda, call {@code c.updateTarget} with this lambda's feature as {@code outer}
   * argument.
   *
   * @param res the resolution instance.
   */
  void updateTarget(Resolution res)
  {
    var e = _expr.visit(new FeatureVisitor()
      {
        @Override
        public Expr action(Call c)
        {
          return c.updateTarget(res, _feature.context());
        }
      },
      _feature);
    // since `_expr` is used/visited by `SourceModule.inScope`
    // ensure that it is not being modified.
    if (CHECKS) check
      (e == _expr);
  }


  /**
   * Special version of propagateExpectedType(res, outer, t) tries to infer the
   * result type of a lambda.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @param t the expected type.
   *
   * @param inferResultType true if the result type of this lambda should be
   * inferred.
   *
   * @param from for error output: if non-null, produces a String describing
   * where the expected type came from.
   *
   * @return if inferResultType, the result type inferred from this lambda or
   * Types.t_UNDEFINED if no result type available.  if !inferResultType, t. In
   * case of error, return Types.t_ERROR.
   */
  @Override
  AbstractType propagateTypeAndInferResult(Resolution res, Context context, AbstractType t, boolean inferResultType, Supplier<String> from)
  {
    AbstractType result = inferResultType ? Types.t_UNDEFINED : t;
    if (_call == null)
      {
        if (!t.isFunctionType())
          {
            // suppress error for t_UNDEFINED, but only if other error was already reported
            if (t != Types.t_UNDEFINED || !Errors.any())
              {
                AstErrors.expectedFunctionTypeForLambda(pos(), t, from);
              }
            t = Types.t_ERROR;
          }

        /* We have an expression of the form
         *
         *   (o, i) -> o.hash_code + i
         *
         * so we replace it by
         *
         * --Fun<id>-- : Function<R,A1,A2,...>
         * {
         *   public redef R call(A1 a1, A2 a2, ...)
         *   {
         *     result = o.hash_code + i;
         *   }
         * }
         * [..]
         *         --Fun<id>--()
         * [..]
         */
        var a = new List<AbstractFeature>();
        var gs = t.generics();
        int i = 1;
        for (var n : _names)
          {
            if (i < gs.size() && gs.get(i) == Types.t_UNDEFINED)
              {
                t = Types.t_ERROR;
              }
            else
              {
                var arg = new Feature(n._pos,
                                      Visi.PRIV,
                                      0,
                                      i < gs.size() ? gs.get(i) : Types.t_ERROR,
                                      n._name,
                                      Contract.EMPTY_CONTRACT);
                a.add(arg);
                i++;
              }
          }
        if (i != gs.size())
          {
            if (t != Types.t_ERROR)
              {
                AstErrors.wrongNumberOfArgumentsInLambda(pos(), _names, t);
              }
            t = Types.t_ERROR;
          }
        if (t != Types.t_ERROR)
          {
            var rt0 = gs.get(0);
            var rt = inferResultType ? NoType.INSTANCE      : new FunctionReturnType(rt0);
            var im = inferResultType ? Impl.Kind.RoutineDef : Impl.Kind.Routine;
            var feature = new Feature(pos(), Visi.PRIV, FuzionConstants.MODIFIER_REDEFINE, rt, new List<String>(FuzionConstants.OPERATION_CALL), a, NO_CALLS, Contract.EMPTY_CONTRACT, new Impl(_expr.pos(), _expr, im))
              {
                @Override
                public boolean isLambdaCall()
                {
                  return true;
                }
              };
            _feature = feature;
            feature._sourceCodeContext = context;

            var inheritsName =
              (t.feature() == Types.resolved.f_Unary   && gs.size() == 2) ? Types.UNARY_NAME   :
              (t.feature() == Types.resolved.f_Binary  && gs.size() == 3) ? Types.BINARY_NAME  :
              (t.feature() == Types.resolved.f_Nullary && gs.size() == 1) ? Types.NULLARY_NAME :
              (t.feature() == Types.resolved.f_Lazy    && gs.size() == 1) ? Types.LAZY_NAME
                                                                          : Types.FUNCTION_NAME;

            // inherits clause for wrapper feature: Function<R,A,B,C,...>
            _inheritsCall = new Call(pos(), null, inheritsName);
            _inheritsCall._generics = gs;
            List<Expr> expressions = new List<Expr>(feature);
            String wrapperName = FuzionConstants.LAMBDA_PREFIX + id++;
            _wrapper = new Feature(pos(),
                                   Visi.PRIV,
                                   0,
                                   RefType.INSTANCE,
                                   new List<String>(wrapperName),
                                   AbstractFeature._NO_FEATURES_,
                                   new List<>(_inheritsCall),
                                   Contract.EMPTY_CONTRACT,
                                   new Impl(pos(), new Block(expressions), Impl.Kind.Routine));
            res._module.findDeclarations(_wrapper, context.outerFeature());
            res.resolveDeclarations(_wrapper);
            res.resolveTypes(_feature);
            if (inferResultType)
              {
                result = refineResultType(res, context, rt0, _feature.resultType());
                _inheritsCall._generics = gs.setOrClone(0, result);
                _inheritsCall.notifyInferred();
              }

            _call = new Call(pos(), new Current(pos(), context.outerFeature()), _wrapper).resolveTypes(res, context);
          }
        else
          {
            _expr = Expr.NO_VALUE;
            result = Types.t_ERROR;
          }
      }
    return result;
  }


  /**
   * Refine the result type based on the inferred lmbdRt
   * and the expected type frmlRt.
   *
   * This enables type inference for lambdas in e.g.:
   *
   * result of lambda may need tagging:
   *     bind(B type, f T -> outcome B) outcome B => ...
   *
   * result of lambda may need boxing:
   *     flat_map(B type, f T -> Sequence B) Sequence B => ...
   */
  private AbstractType refineResultType(Resolution res, Context context, AbstractType frmlRt, AbstractType lmbdRt)
  {
    var result = lmbdRt;
    if (!frmlRt.isGenericArgument())
      {
        if (frmlRt.isChoice()
            // NYI: UNDER DEVELOPMENT: We may want to go further here and support more than
            // one missing undefined
            && frmlRt.choiceGenerics().stream().filter(x -> x == Types.t_UNDEFINED).count() == 1)
          {
            if (frmlRt.feature() != lmbdRt.selfOrConstraint(res, context).feature())
              {
                result = frmlRt.applyToGenericsAndOuter(x -> x == Types.t_UNDEFINED ? lmbdRt: x);
                if (result.isChoice() && result.choiceGenerics().stream().filter(x -> x == Types.t_UNDEFINED).count() == 0)
                  {
                    _feature.setRefinedResultType(res, context, result);
                  }
              }
          }
        else if (!lmbdRt.isGenericArgument()
                 && lmbdRt.feature() != frmlRt.feature()
                 && lmbdRt.feature().inheritsFrom(frmlRt.feature()))
          {
            result = ResolvedNormalType.create(lmbdRt.generics(), frmlRt.outer(), frmlRt.feature());
            _feature.setRefinedResultType(res, context, result);
          }
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
    if (this._call != null)
      {
        var e = this._call.visit(v, outer);
        if (CHECKS) check
          (e == this._call); // NYI: This will fail e.g. if _call is a call to bool.infix &&, need to handle explicitly
        this._call = (Call) e;
      }
    return v.action(this);
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
   */
  void resolveTypes(Resolution res, Context context)
  {
    if (CHECKS) check
      (this._call == null || this._feature != null);

    if (this._call == null)
      {
        // do not do anything yet, we are waiting for propagateExpectedType to
        // tell us what we are.
        if (_names.isEmpty())
          {
            // However, if we do not need the propagated argument types because
            // there are no arguments, we can resolve the type.
            //
            // But we cannot do this immediately, since we might still gain
            // information from propagateExpectedType such as the numeric result
            // type in `()->42` or the actual type for tagging the result of
            // `()->nil` if this is supposed to result in, e.g., option.
            //
            // So we create a Runnable to do this that is to be called when the
            // going get's tough during a call to `type()` if we still have
            // nothing to offer.
            _resultTypeLastResort = ()->
              {
                var f = Types.resolved.f_Nullary;
                var t_undef = ResolvedNormalType.create(f, new List<>(Types.t_UNDEFINED));
                var t_res = propagateTypeAndInferResult(res,
                                                        context,
                                                        t_undef,
                                                        true,
                                                        ()->"type from nullary lambda `()->expr`");
                _type = ResolvedNormalType.create(f, new List<>(t_res));
              };
          }
      }
    else
      {
        List<AbstractType> generics = new List<>();

        var f = this._feature;
        if (CHECKS) check
          (Errors.any() || f != null);

        if (f != null)
          {
            res.resolveTypes(f);
            generics.add(f.resultType());
            for (var a : f.arguments())
              {
                res.resolveTypes(a);
                generics.add(a.resultType());
              }
          }

        _inheritsCall._generics = generics;
        Call inheritsCall2 = _inheritsCall.resolveTypes(res, context);
        // Call.resolveType returns something different than this only for an
        // immediate function call, which is never the case in an inherits
        // clause.
        if (CHECKS) check
          (Errors.any() || _inheritsCall == inheritsCall2,
           _type == null || _type.isAssignableFromDirectly(_call.type()).yes());
      }
  }


  /**
   * type returns the type of this expression or Types.t_ERROR if the type is
   * still unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or t_ERROR in case it is not known yet.
   */
  public AbstractType type()
  {
    if (CHECKS) check
      (_type != Types.t_UNDEFINED);

    if (_type == null)
      {
        _resultTypeLastResort.run();
      }
    if (_type == null)
      {
        if (_expr.type() != Types.t_ERROR || !Errors.any())
          {
            AstErrors.noTypeInferenceFromLambda(pos());
          }
        _type = Types.t_ERROR;
      }
    if (POSTCONDITIONS) ensure
      (_type != null,
       _type != Types.t_UNDEFINED);
    return _type;
  }


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
    // unlike type(), we do not produce an error but just return null here since
    // everything might eventually turn out fine in this case.
    // NYI: UNDER DEVELOPMENT: ugly in case result type is error
    // we should probably have replaced Function already...
    return _feature != null && _feature.resultTypeIfPresent(null) == Types.t_ERROR
      ? Types.t_ERROR
      : _type;
  }


  /**
   * Resolve syntactic sugar, e.g., by replacing anonymous inner functions by
   * declaration of corresponding inner features. Add (f,{@literal <>}) to the list of
   * features to be searched for runtime types to be layouted.
   *
   * @param res the resolution instance.
   */
  public Expr resolveSyntacticSugar2(Resolution res)
  {
    return _call != null
      ? _call
      : this;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return _names + " -> " + _originalExpr;
  }


  /**
   * Check whether this Function has a valid type, i.e. is not an error
   * @return this Function
   */
  public Expr checkTypes()
  {
    type(); // just for triggering error messages
    return this;
  }

}

/* end of file */
