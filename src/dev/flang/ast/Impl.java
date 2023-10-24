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

  public static final Impl INTRINSIC_CONSTRUCTOR = new Impl(Kind.Intrinsic);

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
   *
   */
  public Expr _code;


  /**
   * For a field declared using `:=` or a function declared using `=>`, this
   * gives the value of that field or function.
   */
  Expr _initialValue;
  public Expr initialValue()
  {
    return _initialValue;
  }


  /**
   * For FieldActual: All the actual values that were found for this argument
   * field.
   */
  final List<Expr> _initialValues;

  /**
   * For FieldActual: The outer features for all the actual values that were
   * found for this argument field.
   */
  final List<AbstractFeature> _outerOfInitialValues;


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

    public String toString(){
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
    this._code = switch (kind)
      {
      case Routine, RoutineDef, Of -> e;
      default -> null;
      };
    this._initialValue = switch (kind)
      {
      case FieldInit, FieldDef, FieldIter -> e;
      default -> null;
      };

    this.pos = pos;
    this._kind = kind;
    this._initialValues        = kind == Kind.FieldActual ? new List<>() : null;
    this._outerOfInitialValues = kind == Kind.FieldActual ? new List<>() : null;
  }


  /**
   * Implementation of a feature without an implementation (an abstract feature).
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
    if (this._code != null)
      {
        this._code = this._code.visit(v, outer);
      }
    else
      {
        // initialValue is code executed by outer.outer(), so this is visited by
        // Feature.visit for the outer feature and not here.
        //
        // this.initialValue.visit(v, outer.outer());
      }
    v.action(this, outer);
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
      (this._code != null) &&
      outer.hasResultField() &&
      outer instanceof Feature fouter && !fouter.hasAssignmentsToResult();
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
   * @param outer the feature that contains this implementation.
   *
   */
  public void propagateExpectedType(Resolution res, AbstractFeature outer)
  {
    if (needsImplicitAssignmentToResult(outer))
      {
        _code = _code.propagateExpectedType(res, outer, outer.resultType());
      }
  }


  /**
   * Does this feature implementation consist of nothing but declarations? I.e.,
   * it has no code that actually would be executed at runtime.
   */
  boolean containsOnlyDeclarations()
  {
    return _code == null || _code.containsOnlyDeclarations();
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
  public void resolveSyntacticSugar2(Resolution res, AbstractFeature outer)
  {
    if (needsImplicitAssignmentToResult(outer))
      {
        var resultField = outer.resultField();
        var endPos = (this._code instanceof Block) ? ((Block) this._code)._closingBracePos : this._code.pos();
        Assign ass = new Assign(res,
                                endPos,
                                resultField,
                                this._code,
                                outer);
        ass._value = this._code.box(ass._assignedField.resultType());  // NYI: move to constructor of Assign?
        this._code = new Block (new List<Expr>(ass));
      }
  }


  /**
   * For an actual value passed to an argument field with this Impl, record the
   * actual and its outer feature for type inference
   *
   * @param actl an actual argument expression
   *
   * @param outer the feature containing the actl expression
   */
  void addInitialValue(Expr actl, AbstractFeature outer)
  {
    if (_kind == Impl.Kind.FieldActual)
      {
        _initialValues.add(actl);
        _outerOfInitialValues.add(outer);
      }
  }


  /**
   * visit all the initial values recorded for an FieldActual using
   * addInitialValue.
   *
   * This is used to resolve types of the actual arguments when they are needed
   * for type inference.
   *
   * @param v the visitor to use
   */
  void visitInitialValues(FeatureVisitor v)
  {
    for (var i = 0; i < _initialValues.size(); i++)
      {
        var iv = _initialValues.get(i);
        var io = _outerOfInitialValues.get(i);
        iv.visit(v, io);
      }
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
  AbstractType typeFromInitialValues(Resolution res, AbstractFeature formalArg, boolean reportError)
  {
    AbstractType result = Types.resolved.t_void;
    for (var i = 0; i < _initialValues.size(); i++)
      {
        var iv = _initialValues.get(i);
        var io = _outerOfInitialValues.get(i);
        if (res != null)
          {
            iv.visit(new Feature.ResolveTypes(res),io);
          }
        var t = iv.typeIfKnown();
        if (t != null)
          {
            result = result.union(t);
          }
      }
    if (reportError)
      {
        if (_initialValues.size() == 0)
          {
            AstErrors.noActualCallFound(formalArg);
          }
        else if (result == Types.t_UNDEFINED)
          {
            var types = new List<AbstractType>();
            var positions = new TreeMap<AbstractType, List<SourcePosition>>();
            for (var i = 0; i < _initialValues.size(); i++)
              {
                var iv = _initialValues.get(i);
                var io = _outerOfInitialValues.get(i);
                var t = iv.typeIfKnown();
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

    if (result == Types.t_UNDEFINED)
      {
        result = Types.t_ERROR;
      }
    return result;
  }

  /**
   * For an Impl that uses type inference for the feature result type, this
   * determines the actual result type from the initial value, the code or the
   * actual arguments passed to a formal argument.
   *
   * @param res the resolution instance.
   *
   * @param f the feature this is the Impl of.
   */
  AbstractType inferredType(Resolution res, AbstractFeature f)
  {
    if (PRECONDITIONS) require
      (_kind == Kind.FieldDef    ||
       _kind == Kind.FieldActual ||
       _kind == Kind.RoutineDef     );

    return switch (_kind)
      {
      case FieldDef    -> _initialValue.typeIfKnown();
      case RoutineDef  -> _code.typeIfKnown();
      case FieldActual -> typeFromInitialValues(res, f, false);
      default -> throw new Error("missing case "+_kind);
      };
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
    if (_code != null) {
      result = _code.toString();
    } else {
      switch (_kind)
        {
        case FieldInit  : result = " = "  + _initialValue.getClass() + ": " +_initialValue; break;
        case FieldDef   : result = " := " + _initialValue.getClass() + ": " +_initialValue; break;
        case FieldActual: result = " type_inferred_from_actual";                            break;
        case Field      : result = "";                                                      break;
        case TypeParameter:     result = "type";                                            break;
        case TypeParameterOpen: result = "type...";                                         break;
        case RoutineDef : result = " => " + _code.toString();                               break;
        case Routine    : result = " is " + _code.toString();                               break;
        case Abstract   : result = "is abstract";                                           break;
        case Intrinsic  : result = "is intrinsic";                                          break;
        case Of         : result = "of " + _code.toString();                                break;
        default: throw new Error("Unexpected Kind: "+_kind);
        }
    }
    return result;
  }

}

/* end of file */
