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

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Function <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Function extends ExprWithPos
{


  /*----------------------------  constants  ----------------------------*/


  static final List<Feature> NO_FEATURES = new List<Feature>();
  static final List<AbstractCall> NO_CALLS = new List<>();


  /*-------------------------  static variables -------------------------*/

  /**
   * quick-and-dirty way to make unique names for function wrappers
   */
  static private long id = 0;


  /*----------------------------  variables  ----------------------------*/


  /**
   * For function declaration of the kind "fun a.b.f", this is the call to f
   *
   * For a function that declares a new anonymous feature, this will be the
   * resulting call that creates an instance of a subclass for Function/Routine
   * whose call() function implements the function.
   */
  Call _call;


  /**
   * For function declaration of the kind
   *
   *  fun int (Object o, int i) { result =* o.hashCode + i; }
   *
   * this is the declared feature
   *
   *  int (Object o, int i) { result =* o.hashCode + i; }
   *
   */
  AbstractFeature _feature;


  AbstractType _type;


  /**
   * For a function that declares a new anonymous feature, these are the generic
   * arguments to Function/Routine the anonymous feature inherits from. This
   * will be used put the correct return type in case of a fun declaration using
   * => that requires type inference.
   */
  Call _inheritsCall;


  /**
   * For a function defined inline, this is the wrapper that calls the inline
   * feature.
   */
  Feature _wrapper;


  /**
   * Fields for a lambda of the form
   *
   *   (x,y) pre y != 0 -> x*y
   *
   * In this case, _wrapper and _call will be created during propagateExpectedType().
   */
  List<String> _names;  // names of the arguments: "x", "y"
  List<AbstractCall> _inherits; // inherits calls, currently always empty
  Contract _contract;   // contract of the lambda
  Expr _expr;           // the right hand side of the '->'


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor defining a function by referring to a feature, e.g.,
   *
   *   x(fun a.b.f);
   *
   * where f is defined in a.b as
   *
   *   f(o Object, i int) int { result = o.hashCode + i; };
   *
   * then the "fun f" is syntactic surgar for
   *
   *  a.b._anonymous<#>_f_: Function<int,Object,int>
   *   {
   *     redefine call(o Object, i int) int
   *     {
   *       result = f(o,i);
   *     };
   *   }
   *  x(a.b._anonymous<#>_f_());
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param c the call after the fun keyword
   */
  public Function(SourcePosition pos,
                  Call c)
  {
    super(pos);

    if (PRECONDITIONS) require
      (c._actuals.size() == 0);

    this._call = c;
    c.forFun = true;
    this._wrapper = null;
  }


  /**
   * Constructor for a lambda of the form
   *
   *   (x,y) pre y != 0 -> x*y
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param names the names of the arguments, "x", "y"
   *
   * @param i the inheritance clause, currently alway empty list
   *
   * @param c the contract
   *
   * @param e the code on the right hand side of '->'.
   */
  public Function(SourcePosition pos,
                  List<String> names,
                  List<AbstractCall> i,
                  Contract c,
                  Expr e)
  {
    super(pos);

    _names = names;
    _inherits = i;
    _contract = c;
    _expr = e;
    _wrapper = null;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Load all features that are called by this expression.
   *
   * @param res the resolution instance.
   *
   * @param thiz the class that contains this expression.
   */
  void loadCalledFeature(Resolution res, AbstractFeature thiz)
  {
    if (this._call != null)
      {
        this._call.loadCalledFeature(res, thiz);
        if (this._feature == null)
          {
            var f = this._call.calledFeature();
            if (CHECKS) check
              (Errors.count() > 0 || f != null);

            if (f != null)
              {
                if (f.isConstructor())
                  {
                    System.err.println("NYI: fun for constructor type not allowed");
                    System.exit(1);
                  }
              }
          }
      }
  }


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
   * will be replaced by the statement that reads the field.
   */
  public Expr propagateExpectedType(Resolution res, AbstractFeature outer, AbstractType t)
  {
    propagateExpectedType2(res, outer, t, false);
    return this;
  }


  /**
   * Special version of propagateExpetedType(res, outer, t) tries to infer the
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
   * @return the result type provided that inferResultType and the result type
   * could be inferred, null otherwise.
   */
  public AbstractType propagateExpectedType2(Resolution res, AbstractFeature outer, AbstractType t, boolean inferResultType)
  {
    AbstractType result = null;
    if (_call == null)
      {
        if (t != Types.t_ERROR && t.featureOfType() != Types.resolved.f_function)
          {
            AstErrors.expectedFunctionTypeForLambda(pos(), t);
            t = Types.t_ERROR;
          }

        /* We have an expression of the form
         *
         *   (o, i) -> o.hashCode + i
         *
         * so we replace it by
         *
         * --Fun<id>-- : Function<R,A1,A2,...>
         * {
         *   public redefine R call(A1 a1, A2 a2, ...)
         *   {
         *     result = o.hashCode + i;
         *   }
         * }
         * [..]
         *         --Fun<id>--()
         * [..]
         */
        List<Feature> a = new List<>();
        var gs = t.generics();
        int i = 1;
        for (var n : _names)
          {
            var arg = new Feature(pos() /* better n.pos() */,
                                  Consts.VISIBILITY_LOCAL,
                                  0,
                                  i < gs.size() ? gs.get(i) : Types.t_ERROR,
                                  n,
                                  Contract.EMPTY_CONTRACT);
            a.add(arg);
            i++;
          }
        if (t != Types.t_ERROR && i != gs.size())
          {
            AstErrors.wrongNumberOfArgumentsInLambda(pos(), _names, t);
            t = Types.t_ERROR;
          }
        if (t != Types.t_ERROR)
          {
            var rt = inferResultType ? NoType.INSTANCE      : new FunctionReturnType(gs.get(0));
            var im = inferResultType ? Impl.Kind.RoutineDef : Impl.Kind.Routine;
            var f = new Feature(pos(), rt, new List<String>("call"), a, _inherits, _contract, new Impl(_expr.pos(), _expr, im));
            this._feature = f;

            // inherits clause for wrapper feature: Function<R,A,B,C,...>
            _inheritsCall = new Call(pos(), Types.FUNCTION_NAME, gs, Expr.NO_EXPRS);
            List<Stmnt> statements = new List<Stmnt>(f);
            String wrapperName = FuzionConstants.LAMBDA_PREFIX + id++;
            _wrapper = new Feature(pos(),
                                   Consts.VISIBILITY_INVISIBLE,
                                   Consts.MODIFIER_FINAL,
                                   RefType.INSTANCE,
                                   new List<String>(wrapperName),
                                   NO_FEATURES,
                                   new List<>(_inheritsCall),
                                   Contract.EMPTY_CONTRACT,
                                   new Impl(pos(), new Block(pos(), statements), Impl.Kind.Routine));
            res._module.findDeclarations(_wrapper, outer);
            if (inferResultType)
              {
                res.resolveDeclarations(_wrapper);
                res.resolveTypes(f);
                result = f.resultType();
                gs.set(0, result);
              }

            _call = new Call(pos(), new Current(pos(), outer.thisType()), _wrapper).resolveTypes(res, outer);
          }
        _type = t;
      }
    return result;
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
    if (this._call != null)
      {
        var e = this._call.visit(v, outer);
        if (CHECKS) check
          (e == this._call); // NYI: This will fail e.g. if _call is a call to bool.infix &&, need to handle explicitly
        this._call = (Call) e;
      }
    return v.action(this, outer);
  }


  /**
   * Find all the types used in this that refer to formal generic arguments of
   * this or any of this' outer classes.
   *
   * @param outer the root feature that contains this statement.
   */
  public void findGenerics(FeatureVisitor v, AbstractFeature outer)
  {
    if (this._feature != null)
      { /* NYI: Neeed? The following comment seems wrong: */
        // directly process generics in _feature's arguments and return type,
        // while visit() skips the _feature.
        var f = this._feature;
        for (var a : f.arguments())
          {
            var rt = ((Feature)a).returnType(); // NYI: Cast!
            rt.visit(v, outer);
          }
        var rt = ((Feature)f).returnType(); // NYI: Cast!
        rt.visit(v, outer);
      }
  }


  private AbstractFeature functionOrRoutine()
  {
    var f = this._feature == null ? this._call.calledFeature()
                                  : this._feature;
    if (CHECKS) check
      (Errors.count() > 0 || f != null);

    if (f != null)
      {
        f = Types.resolved.f_function;
      }
    return f;
  }

  /**
   * Produce the list of actual generic arguments to be passed to
   * functionOrRoutine.
   */
  List<AbstractType> generics(Resolution res)
  {
    List<AbstractType> generics = new List<>();

    var f = this._feature == null ? this._call.calledFeature()
                                  : this._feature;
    if (CHECKS) check
      (Errors.count() > 0 || f != null);

    if (f != null)
      {
        generics.add(f instanceof Feature ff && ff.hasResult()  // NYI: Cast!
                     ? ff.resultTypeForTypeInference(pos(), res, Type.NONE)
                     : new Type("unit"));
        for (var a : f.arguments())
          {
            res.resolveTypes(a);
            generics.add(a.resultType());
          }
      }
    return generics;
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this statement.
   */
  public void resolveTypes(Resolution res, AbstractFeature outer)
  {
    if (this._call == null)
      {
        // do not do anything yet, we are waiting for propagateExpectedType to
        // tell us what we are.
      }
    else if (this._feature == null)
      {
        var fr = functionOrRoutine();
        var generics = generics(res);
        FormalGenerics.resolve(res, generics, outer);
        _type = fr != null ? new Type(pos(), fr.featureName().baseName(), generics, null, fr, Type.RefOrVal.LikeUnderlyingFeature).resolve(res, outer)
                           : Types.t_ERROR;
      }
    else
      {
        _inheritsCall._generics = generics(res);
        Call inheritsCall2 = _inheritsCall.resolveTypes(res, outer);
        // Call.resolveType returns something differnt than this only for an
        // immediate function call, which is never the case in an inherits
        // clause.
        if (CHECKS) check
          (_inheritsCall == inheritsCall2);
        _type = _call.type();
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
    var result = _type;
    if (result == null)
      {
        AstErrors.noTypeInferenceFromLambda(pos());
        _type = Types.t_ERROR;
      }
    return _type;
  }


  /**
   * typeForGenericsTypeInfereing returns the type of this expression or null if
   * the type is still unknown, i.e., before or during type resolution for
   * generic type arguments.
   *
   * @return this Expr's type or null if not known.
   */
  public AbstractType typeForGenericsTypeInfereing()
  {
    // unlike type(), we do not produce an error but just return null here since
    // everything might eventually turn out fine in this case.
    return _type;
  }


  /**
   * Resolve syntactic sugar, e.g., by replacing anonymous inner functions by
   * declaration of corresponding inner features. Add (f,<>) to the list of
   * features to be searched for runtime types to be layouted.
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this statement.
   */
  public Expr resolveSyntacticSugar2(Resolution res, AbstractFeature outer)
  {
    Expr result = this;
    var ignore = type(); // just for the side-effect of producing an error if there was no type-propagation.
    if (Errors.count() == 0)  // avoid null pointer hdlg in case calledFeature not found etc.
      {
        if (this._feature == null)
          { /* We have an expression of the form
             *
             *   fun a.b.f
             *
             * so we replace it by
             *
             * --Fun<id>-- : Function<R,A1,A2,...>
             * {
             *   public redefine R call(A1 a1, A2 a2, ...)
             *   {
             *     result = a.b.f(a1, a2, ...);
             *   }
             * }
             * [..]
             *         --Fun<id>--()
             * [..]
             */
            Call call = this._call;
            call.forFun = false;  // the call is no longer for fun (i.e., ignored in Call.resolveTypes)
            var calledFeature = call.calledFeature();
            /* NYI: "fun a.b" special cases: check what can go wrong with
             * calledTarget and flag an error. Possible errors aor special case
             * are
             *
             *  - calling an abstract feature
             *  - calling a redefined feature
             *  - calling a single feature
             *  - calling a feature in a different module
             */
            List<Expr> actual_args = new List<Expr>();
            List<Feature> formal_args = new List<Feature>();
            int argnum = 1;
            for (var f : calledFeature.arguments())
              {
                String name = "a"+argnum;
                actual_args.add(new Call(pos(), null, name));
                formal_args.add(new Feature(pos(), Consts.VISIBILITY_LOCAL, 0, f.resultType(), name, Contract.EMPTY_CONTRACT));
                argnum++;
              }
            Call callWithArgs = new Call(pos(), null, call.name, actual_args);
            Feature fcall = new Feature(pos(), Consts.VISIBILITY_PUBLIC,
                                        Consts.MODIFIER_REDEFINE,
                                        NoType.INSTANCE, // calledFeature.returnType,
                                        new List<String>("call"),
                                        formal_args,
                                        NO_CALLS,
                                        Contract.EMPTY_CONTRACT,
                                        new Impl(pos(), callWithArgs, Impl.Kind.RoutineDef));

            // inherits clause for wrapper feature: Function<R,A,B,C,...>
            var fr = functionOrRoutine();
            List<AbstractCall> inherits = new List<>(new Call(pos(), fr.featureName().baseName(), _type.generics(), Expr.NO_EXPRS));

            List<Stmnt> statements = new List<Stmnt>(fcall);

            String wrapperName = FuzionConstants.LAMBDA_PREFIX + id++;
            Feature function = new Feature(pos(),
                                           Consts.VISIBILITY_INVISIBLE,
                                           Consts.MODIFIER_FINAL,
                                           RefType.INSTANCE,
                                           new List<String>(wrapperName),
                                           NO_FEATURES,
                                           inherits,
                                           Contract.EMPTY_CONTRACT,
                                           new Impl(pos(), new Block(pos(), statements), Impl.Kind.Routine));
            res._module.findDeclarations(function, call.target.type().featureOfType());
            result = new Call(pos(),
                              call.target,
                              function)
              .resolveTypes(res, outer);
          }
        else
          {
            result = _call;
          }
      }
    return result;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return (_feature == null)
      ? "function CALL: " + _call
      : "function FEATURE: " + _feature;
  }

}

/* end of file */
