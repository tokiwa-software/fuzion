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

  public static final Impl INTRINSIC_CONSTRUCTOR = new Impl(Kind.Intrinsic);

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
   *
   */
  Expr _initialValue;


  AbstractFeature _outerOfInitialValue = null;


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
    Of            // Syntacitic sugar 'enum : choice of red, green, blue is', exists only during parsing
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
   * @param isDefinition true if this implementation is a definition
   * using ":=", i.e., no type is needed.
   *
   * @param kind the kind, must not be FieldActual.
   */
  public Impl(SourcePosition pos, Expr e, Kind kind)
  {
    this(pos, e, null, kind);
  }


  /**
   * Implementation of a argument field feature whose type if inferred from the
   * actual argument.
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param e the actual argument to a call to a.outer()
   *
   * @param outerOfInitialValue the outer feature that contains e.
   */
  public Impl(SourcePosition pos, Expr e, AbstractFeature outerOfInitialValue)
  {
    this(pos, e, outerOfInitialValue, Kind.FieldActual);
  }


  /**
   * Implementation of a feature
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param e the code or initial value
   *
   * @param outerOfInitialValue for kind == FieldActual: the outer feature that contains e, null otherwise.
   *
   * @param kind the kind
   */
  private Impl(SourcePosition pos, Expr e, AbstractFeature outerOfInitialValue, Kind kind)
  {
    if (PRECONDITIONS) require
                         (true || (kind == Kind.FieldActual) == (outerOfInitialValue != null)); // NYI

    if (kind == Kind.FieldInit   ||
        kind == Kind.FieldDef    ||
        kind == Kind.FieldActual ||
        kind == Kind.FieldIter   )
      {
        this._code = null;
        this._initialValue = e;
        this._outerOfInitialValue = outerOfInitialValue;
      }
    else
      {
        if (CHECKS) check
          (kind == Kind.Routine    ||
           kind == Kind.RoutineDef ||
           kind == Kind.Of            );
        this._code = e;
        this._initialValue = null;
      }

    this.pos = pos;
    this._kind = kind;
  }


  /**
   * Implementation of a feature without an implementation (an abstract feature).
   */
  public Impl(Kind kind)
  {
    this._code = null;
    this._initialValue = null;
    this.pos = null;
    this._kind = kind;
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
        //   f => 0;
        //
        // requires no return type
        //
        if (rt != NoType.INSTANCE)
          {
            AstErrors.illegalResultTypeRoutineDef(f, rt);
            rt = NoType.INSTANCE;
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
        break;
      }
    return rt;
  }


  /**
   * visit all the features, expressions, statements within this feature.
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
   * @param t the expected type.
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
        this._code = new Block (this._code.pos(),
                                endPos,
                                new List<Stmnt>(ass));
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
        case FieldActual: result = " typefrom(" + _initialValue.pos() + ")";                break;
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
