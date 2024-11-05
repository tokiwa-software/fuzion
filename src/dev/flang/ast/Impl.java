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
 * Source of class Impl
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.TreeMap;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Impl <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Impl extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  public static final Impl FIELD = new Impl(Kind.Field);

  public static final Impl TYPE_PARAMETER = new Impl(Kind.TypeParameter);
  public static final Impl TYPE_PARAMETER_OPEN = new Impl(Kind.TypeParameterOpen);

  public static final Impl ABSTRACT = new Impl(Kind.Abstract);

  public static final Impl INTRINSIC = new Impl(Kind.Intrinsic);

  public static final Impl NATIVE = new Impl(Kind.Native);

  /**
   * A dummy Impl instance used in case of parsing error.
   */
  public static final Impl ERROR = new Impl(Kind.Intrinsic);


  /*----------------------------  variables  ----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  public final SourcePosition pos;


  /**
   * For a field declared using `:=` this
   * gives the initial value of that field or function.
   * For a function declared using `=>` this
   * gives the code of that function.
   */
  private Expr _expr;
  public Expr expr()
  {
    return _expr;
  }


  /**
   * For FieldActual: All the actual calls that were found for the outer feature
   * of this argument field.
   */
  final List<Call> _initialCalls;


  public enum Kind
  {
    FieldInit,    // a field with initialization syntactic sugar
    FieldDef,     // a field with implicit type
    FieldActual,  // an argument field with type defined by actual argument
    FieldIter,    // a field f declared as an iterator index in a loop (eg., for f in myset { print(f); } )
    Field,        // a field
    TypeParameter,// a type parameter Field
    TypeParameterOpen,// an open (list) type parameter Field
    RoutineDef,   // normal feature with code and implicit result type
    Routine,      // normal feature with code
    Abstract,     // an abstract feature
    Intrinsic,    // an intrinsic feature
    Of,           // Syntactic sugar 'enum : choice of red, green, blue is', exists only during parsing
    Native;       // a native feature

    public String toString()
    {
      return switch(this)
        {
          case FieldInit         : yield "field initialization";
          case FieldDef          : yield "field definition";
          case FieldActual       : yield "actual argument";
          case FieldIter         : yield "iterator";
          case Field             : yield "field";
          case TypeParameter     : yield "type parameter";
          case TypeParameterOpen : yield "open type parameter";
          case RoutineDef        : yield "routine definition";
          case Routine           : yield "routine";
          case Abstract          : yield "abstract";
          case Intrinsic         : yield "intrinsic";
          case Native            : yield "native";
          case Of                : yield "choice of";
        };
    }
  };

  /**
   *
   */
  public final Kind _kind;


  /**
   * Flag to detect infinite recursion when resolving types of initial
   * values. Used by @see initialValueFromCall.
   */
  private boolean _infiniteRecursionInResolveTypes = false;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Implementation of a feature
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param e the code or initial value
   *
   * @param kind the kind, must not be FieldActual.
   */
  public Impl(SourcePosition pos, Expr e, Kind kind)
  {
    this._expr = e;
    this.pos = pos;
    this._kind = kind;
    this._initialCalls = kind == Kind.FieldActual ? new List<>() : null;
  }


  /**
   * Implementation of a feature without an implementation.
   * e.g. for an abstract, intrinsic or type parameter feature.
   */
  public Impl(Kind kind)
  {
    this(null, null, kind);
  }

  /*-----------------------------  methods  -----------------------------*/


  /**
   * Check if the return type of a feature f.returnType() is allowed in
   * conjunction with this feature implementation. Cause a compiler Error and
   * return a value return type if this is not the case.
   *
   * @param f a feature
   */
  public ReturnType checkReturnType(Feature f)
  {
    if (PRECONDITIONS) require
      (f.impl() == this);

    ReturnType rt = f.returnType();

    switch (_kind)
      {
      case FieldInit:
        // Field initialization of the form
        //
        //   i int := 0;
        //
        // needs a normal function return type:
        //
        if (rt == NoType.INSTANCE)
          {
            AstErrors.missingResultTypeForField(f);
            rt = new FunctionReturnType(Types.t_ERROR);
          }
        else if (!(rt instanceof FunctionReturnType))
          {
            AstErrors.illegalResultType(f, rt);
            rt = new FunctionReturnType(Types.t_ERROR);
          }
        break;

      case FieldDef:
      case FieldActual:
        // Field definition of the form
        //
        //   i := 0;
        //
        // requires no return type
        //
        if (rt != NoType.INSTANCE)
          {
            AstErrors.illegalResultTypeDef(f, rt);
            rt = NoType.INSTANCE;
          }
        break;

      case Field:
        // A field declaration of the form
        //
        //   f type := ?;
        //
        // requires a type
        if (rt == NoType.INSTANCE)
          {
            AstErrors.missingResultTypeForField(f);
            rt = new FunctionReturnType(Types.t_ERROR);
          }
        else if (!(rt instanceof FunctionReturnType))
          {
            AstErrors.illegalResultTypeNoInit(f, rt);
            rt = new FunctionReturnType(Types.t_ERROR);
          }
        break;

      case RoutineDef:
        // Function definition of the form
        //
        //   f => 0
        //   f i32 => 0
        //
        // may or may not have a return type but must not be
        // `ref` type:
        //
        //   f ref => x
        //
        if (rt == RefType.INSTANCE)
          {
            AstErrors.illegalResultTypeRefTypeRoutineDef(f);
          }
        break;

      case Routine:
        // Feature definition of the form
        //
        //   f type { .. }
        //
        // may or may not have a return type
        //
        if (rt == NoType.INSTANCE)
          {
            rt = ValueType.INSTANCE;
          }
        else if (rt instanceof FunctionReturnType)
          {
            // NYI: function declaration using `is` should be forbidden
            //
            //   f type is ...
            //   f type { ... }
            //
            // AstErrors.illegalResultTypeInConstructorDeclaration(f);  -- NYI
          }
        break;
      }
    return rt;
  }


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   */
  public void visit(FeatureVisitor v, AbstractFeature outer)
  {
    if (isRoutineLike())
      {
        this._expr = this._expr.visit(v, outer);
      }
    else
      {
        // In case this is a field:
        // _code is code executed by outer.outer(), so this is visited by
        // Feature.visit for the outer feature and not here.
        //
        // this.visitCode(v, outer.outer());
      }
    v.action(this, outer);
  }


  /**
   * Visit the expression of this implementation.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   */
  public void visitExpr(FeatureVisitor v, AbstractFeature outer)
  {
    this._expr = this._expr.visit(v, outer);
  }


  /**
   * Is this an implementation of a routine?
   */
  private boolean isRoutineLike()
  {
    return _kind == Kind.Routine || _kind == Kind.RoutineDef;
  }


  /*
   * Does this implementation have an initial value?
   * I.e. is it executed by the outer feature of its feature?
   */
  public boolean hasInitialValue()
  {
    return _kind == Kind.FieldInit || _kind == Kind.FieldDef || _kind == Kind.FieldIter;
  }


  /**
   * Do we need to add implicit assignments to the result field? This is the
   * case for routines that do not have an explicit assignment to the result
   * field.
   *
   * @param outer the feature that contains this implementation.
   */
  private boolean needsImplicitAssignmentToResult(AbstractFeature outer)
  {
    return
      isRoutineLike() && outer.hasResultField();
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
   * @param context the source code context where this Expr is used
   */
  public void propagateExpectedType(Resolution res, Context context)
  {
    if (needsImplicitAssignmentToResult(context.outerFeature()))
      {
        _expr = _expr.propagateExpectedType(res, context, context.outerFeature().resultType());
      }
  }


  /**
   * Inform the expression of this implementation that its expected type is `t`.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @param t the expected type.
   */
  public void propagateExpectedType(Resolution res, Context context, AbstractType t)
  {
    _expr = _expr.propagateExpectedType(res, context, t);
  }


  /**
   * Does this feature implementation consist of nothing but declarations? I.e.,
   * it has no code that actually would be executed at runtime.
   */
  boolean containsOnlyDeclarations()
  {
    return _expr == null || _expr.containsOnlyDeclarations();
  }


  /**
   * Resolve syntactic sugar, e.g., by replacing anonymous inner functions by
   * declaration of corresponding inner features. Add (f,<>) to the list of
   * features to be searched for runtime types to be layouted.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this implementation.
   */
  public void resolveSyntacticSugar2(Resolution res, Context context)
  {
    var outer = context.outerFeature();
    if (outer.isConstructor() && outer.preFeature() != null)
      { // For constructors, the constructor itself checks the precondition (while
        // for functions, this is done by the caller):
        var c = outer.contract().callPreCondition(res, outer, context);
        _expr = new Block(new List<>(c, _expr));
      }
    if (needsImplicitAssignmentToResult(outer))
      {
        var resultField = outer.resultField();
        Assign ass = new Assign(res,
                                this._expr.pos(),
                                resultField,
                                this._expr,
                                context);
        ass._value = this._expr;
        this._expr = ass;
      }

    // Add call to post condition feature:
    var pF = outer.postFeature();
    if (pF != null)
      {
        switch (outer.kind())
          {
          case Field             -> {} // Errors.fatal("NYI: UNDER DEVELOPMENT #3092 postcondition for field not supported yet");
          case TypeParameter     ,
               OpenTypeParameter -> { if (!Errors.any()) { Errors.fatal("postcondition for type parameter should not exist for " + outer.pos().show()); } }
          case Routine           ->
            {
              var callPostCondition = Contract.callPostCondition(res, context);
              this._expr = new Block(new List<>(this._expr, callPostCondition));
            }
          case Abstract          -> {} // ok, must be checked by redefinitions
          case Intrinsic         -> {} // Errors.fatal("NYI: UNDER DEVELOPMENT #3105 postcondition for intrinsic");
          case Native            -> {} // Errors.fatal("NYI: UNDER DEVELOPMENT #3105 postcondition for native");
          }
      }
  }


  /**
   * For an actual value passed to an argument field with this Impl, record the
   * actual call for type inference for argument types. This is used for code like
   *
   *   f(a,b) => a+b
   *
   *   x := f 3 4
   *
   * where the argument types for `a` and `b` are inferred from the actual
   * arguments `3` and `4`.
   *
   * @param call an actual argument expression
   */
  void addInitialCall(Call call)
  {
    if (_kind == Impl.Kind.FieldActual)
      {
        _initialCalls.add(call);
      }
  }


  /**
   * Get the initial value from actual argument in a call.
   *
   * @param i the index in _initialCalls
   *
   * @param res the resolution. If not null, the actuals' types will be
   * resolved.
   *
   * @return the Expr that is assigned to this in call #i.
   */
  private Expr initialValueFromCall(int i, Resolution res)
  {
    Expr result = null;
    var ic = _initialCalls.get(i);
    var aargs = ic._actuals.listIterator();
    for (var frml : ic.calledFeature().valueArguments())
      {
        if (aargs.hasNext())
          {
            var actl = aargs.next();
            if (frml instanceof Feature f && f.impl() == this)
              {
                if (res != null && !_infiniteRecursionInResolveTypes)
                  {
                    _infiniteRecursionInResolveTypes = true;
                    actl = res.resolveType(actl, ic.resolvedFor());
                    aargs.set(actl);
                    _infiniteRecursionInResolveTypes = false;
                  }
                if (CHECKS) check
                  (result == null);
                result = actl;
              }
          }
      }

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * Determine the type of a FieldActual by forming the union of the types of
   * all actual values added by addInitialValue.
   *
   * @param res The resolution instance.  NOTE: res may be null, e.g., when this
   * is called during a later phase.
   *
   * @param formalArg the features whose Impl this is.
   *
   * @param reportError true to produce an error message, false to suppress
   * this. Error messages are first suppressed until all initial values were
   * found such that we can report all occurrences of actuals and all actual
   * types that were found.
   */
  private AbstractType typeFromInitialValues(Resolution res, AbstractFeature formalArg, boolean reportError)
  {
    var exprs = new List<Expr>();
    for (var i = 0; i < _initialCalls.size(); i++)
      {
        var iv = initialValueFromCall(i, res);
        exprs.add(iv);
      }
    var result = Expr.union(exprs, Context.NONE);
    // the following line is currently necessary
    // to enable cyclic type inference e.g. in reg_issue2182
    result = result == null ? Types.resolved.t_void : result;
    if (reportError)
      {
        if (_initialCalls.size() == 0)
          {
            AstErrors.noActualCallFound(formalArg);
          }
        else if (result == Types.t_ERROR)
          {
            var types = new List<AbstractType>();
            var positions = new TreeMap<AbstractType, List<SourcePosition>>();
            for (var i = 0; i < _initialCalls.size(); i++)
              {
                var iv = initialValueFromCall(i, null);
                var t = iv.typeForInferencing();
                if (t != null)
                  {
                    var l = positions.get(t);
                    if (l == null)
                      {
                        l = new List<>();
                        positions.put(t, l);
                        types.add(t);
                      }
                    l.add(iv.pos());
                  }
              }
            AstErrors.incompatibleTypesOfActualArguments(formalArg, types, positions);
          }
      }

    return result;
  }


  /*
   * Is the type of this implementation possibly inferable?
   */
  public boolean typeInferable()
  {
    return _kind == Kind.RoutineDef || _kind == Kind.FieldDef || _kind == Kind.FieldActual;
  }


  /**
   * For an Impl that uses type inference for the feature result type, this
   * determines the actual result type from the initial value, the code or the
   * actual arguments passed to a formal argument.
   *
   * @param res the resolution instance.
   *
   * @param f the feature this is the Impl of.
   *
   * @param urgent if true and the result type is inferred and inference would
   * currently not succeed, then enforce it even if that would produce an error.
   *
   */
  AbstractType inferredType(Resolution res, AbstractFeature f, boolean urgent)
  {
    var result = switch (_kind)
      {
      case RoutineDef ->
        {
          var t = _expr.typeForInferencing();
          if (t == null && urgent)
            {
              t = _expr.type();  // produce _expr's error if we really need the type and can't get it
            }
          yield t;
        }
      case FieldDef ->
        {
          var t = _expr.typeForInferencing();
          // second try, the feature containing the field
          // may not be resolved yet.
          // see #348 for an example.
          var fo = f.outer();
          if (res != null && t == null && (fo.isUniverse() || !fo.state().atLeast(State.RESOLVING_TYPES)))
            {
              f.visit(res.resolveTypesFully(fo), fo);
              t  = _expr.typeForInferencing();
            }
          if (t == null && urgent)
            {
              t = _expr.type();  // produce _expr's error if we really need the type and can't get it
            }
          yield t;
        }
      case FieldActual -> typeFromInitialValues(res, f, false);
      default -> null;
      };

    return result != null &&
           result.isTypeType() &&
           !(_expr instanceof Call c && c.calledFeature() == Types.resolved.f_type_as_value)
      ? Types.resolved.f_Type.selfType()
      : result;
  }


  /**
   * Perform type checking and produce errors. In particular, this reports
   * errors when type inference for FieldActual fails due to incompatible types
   * or no actuals found.
   */
  public void checkTypes(AbstractFeature f)
  {
    if (_kind == Kind.FieldActual)
      {
        var ignore = typeFromInitialValues(null, f, true);
      }
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    String result;
    if (_expr != null) {
      result = _expr.toString();
    } else {
      switch (_kind)
        {
        case FieldInit  : result = " = "  + _expr.getClass() + ": " +_expr; break;
        case FieldDef   : result = " := " + _expr.getClass() + ": " +_expr; break;
        case FieldActual: result = " type_inferred_from_actual";                            break;
        case Field      : result = "";                                                      break;
        case TypeParameter:     result = "type";                                            break;
        case TypeParameterOpen: result = "type...";                                         break;
        case RoutineDef : result = " => " + _expr.toString();                               break;
        case Routine    : result = " is " + _expr.toString();                               break;
        case Abstract   : result = "is abstract";                                           break;
        case Intrinsic  : result = "is intrinsic";                                          break;
        case Of         : result = "of " + _expr.toString();                                break;
        default: throw new Error("Unexpected Kind: "+_kind);
        }
    }
    return result;
  }

}

/* end of file */
