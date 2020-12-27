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
 * Tokiwa GmbH, Berlin
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
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class Impl extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  public static final Impl FIELD = new Impl(Kind.Field);

  public static final Impl ABSTRACT = new Impl(Kind.Abstract);

  public static final Impl INTRINSIC = new Impl(Kind.Intrinsic);


  /*----------------------------  variables  ----------------------------*/


  /**
   * The soucecode position of this expression, used for error messages.
   */
  public final SourcePosition pos;


  /**
   *
   */
  public Expr code_;


  /**
   *
   */
  Expr initialValue;


  public enum Kind
  {
    FieldInit,    // a field with initialization syntactic sugar
    FieldDef,     // a field with implicit type
    FieldIter,    // a field f declared as an iterator index in a loop (eg., for f in myset { print(f); } )
    Field,        // a field
    RoutineDef,   // normal feature with code and implicit result type
    Routine,      // normal feature with code
    Abstract,     // an abstract feature
    Intrinsic     // an intrinsic feature
  };

  /**
   *
   */
  public final Kind kind_;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Implementation of a feature
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param e the code or initial value
   *
   * @param isDefinition true if this implementation is a definition
   * using ":=", i.e., no type is needed.
   */
  public Impl(SourcePosition pos, Expr e, Kind kind)
  {
    if (kind == Kind.FieldInit ||
        kind == Kind.FieldDef  ||
        kind == Kind.FieldIter   )
      {
        this.code_ = null;
        this.initialValue = e;
      }
    else
      {
        check
          (kind == Kind.Routine    ||
           kind == Kind.RoutineDef    );
        this.code_ = e;
        this.initialValue = null;
      }

    this.pos = pos;
    this.kind_ = kind;
  }


  /**
   * Implementation of a feature without an implementation (an abstract feature).
   */
  public Impl(Kind kind)
  {
    this.code_ = null;
    this.initialValue = null;
    this.pos = null;
    this.kind_ = kind;
  }

  /*-----------------------------  methods  -----------------------------*/


  /**
   * Check if the return type of a feature f.returnType is allowed in
   * conjunction with this feature implementation. Cause a compiler Error and
   * return a value return type if this is not the case.
   *
   * @param f a feature
   */
  public ReturnType checkReturnType(Feature f)
  {
    if (PRECONDITIONS) require
      (f.impl == this);

    ReturnType rt = f.returnType;

    switch (kind_)
      {
      case FieldInit:
        // Field initialization of the form
        //
        //   i int = 0;
        //
        // needs a normal function return type:
        //
        if (rt == NoType.INSTANCE)
          {
            FeErrors.missingResultTypeForField(f);
            rt = new FunctionReturnType(Types.t_ERROR);
          }
        else if (!(rt instanceof FunctionReturnType))
          {
            Errors.error(f.pos,
                         "Illegal result type >>" + rt + "<< in field declaration with initializaton using \"=\"",
                         "Field declared: " + f.qualifiedName());
            rt = new FunctionReturnType(Types.t_ERROR);
          }
        break;

      case FieldDef:
        // Field definition of the form
        //
        //   i := 0;
        //
        // requires no return type
        //
        if (rt != NoType.INSTANCE)
          {
            Errors.error(f.pos,
                         "Illegal result type >>" + rt + "<< in field definition using \":=\"",
                         "For field definition using \":=\", the type is determined automatically, " +
                         "it must not be given explicitly.\n" +
                         "Field declared: " + f.qualifiedName());
            rt = NoType.INSTANCE;
          }
        break;

      case Field:
        // A field declaration of the form
        //
        //   f type;
        //
        // requires a type
        if (rt == NoType.INSTANCE)
          {
            FeErrors.missingResultTypeForField(f);
            rt = new FunctionReturnType(Types.t_ERROR);
          }
        else if (!(rt instanceof FunctionReturnType))
          {
            Errors.error(f.pos,
                         "Illegal result type >>" + rt + "<< in field declaration",
                         "Field declared: " + f.qualifiedName());
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
            Errors.error(f.pos,
                         "Illegal result type >>" + rt + "<< in feature definition using \"=>\"",
                         "For function definition using \"=>\", the type is determined automatically, " +
                         "it must not be given explicitly.\n" +
                         "Feature declared: " + f.qualifiedName());
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
  public void visit(FeatureVisitor v, Feature outer)
  {
    if (this.code_ != null)
      {
        this.code_ = this.code_.visit(v, outer);
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
   */
  public void propagateExpectedType(Resolution res, Feature outer)
  {
    if (this.code_ != null)
      {
        Feature resultField = outer.resultField();
        if ((resultField != null) && !outer.hasAssignmentsToResult())
          {
            code_ = code_.propagateExpectedType(res, outer, outer.resultType());
          }
      }
  }


  /**
   * Does this feature implementation consist of nothing but declarations? I.e.,
   * it has no code that actually would be executed at runtime.
   */
  boolean containsOnlyDeclarations()
  {
    return code_ == null || code_.containsOnlyDeclarations();
  }


  /**
   * Resolve syntactic sugar, e.g., by replacing anonymous inner functions by
   * declaration of corresponding inner features. Add (f,<>) to the list of
   * features to be searched for runtime types to be layouted.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param outer the root feature that contains this statement.
   */
  public void resolveSyntacticSugar2(Resolution res, Feature outer)
  {
    if (this.code_ != null)
      {
        Feature resultField = outer.resultField();
        if ((resultField != null) && !outer.hasAssignmentsToResult())
          {
            var endPos = (this.code_ instanceof Block) ? ((Block) this.code_).closingBracePos_ : this.code_.pos;
            Assign ass = new Assign(res,
                                    endPos,
                                    resultField,
                                    this.code_,
                                    outer);
            this.code_ = new Block (this.code_.pos,
                                    endPos,
                                    new List<Stmnt>(ass));
          }
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
    if (code_ != null) {
      result = code_.toString();
    } else {
      switch (kind_)
        {
        case FieldInit : result = " = "  + initialValue.getClass() + ": " +initialValue; break;
        case FieldDef  : result = " := " + initialValue.getClass() + ": " +initialValue; break;
        case Field     : result = "";                                                    break;
        case RoutineDef: result = " => " + code_.toString();                             break;
        case Routine   : result =          code_.toString();                             break;
        case Abstract  : result = "is abstract";                                         break;
        case Intrinsic : result = "is intrinsic";                                        break;
        default: throw new Error("Unexpected Kind: "+kind_);
        }
    }
    return result;
  }

}

/* end of file */
