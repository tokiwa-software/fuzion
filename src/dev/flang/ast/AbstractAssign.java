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
 * Source of class Assign
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.Errors;


/**
 * AbstractAssign represents an Assignment, created either from Source code or
 * loaded from a .fum library file.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractAssign extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The value that will be assigned to the field.
   */
  public Expr _value;


  /**
   * Field that is assigned by this assign expression. initialized
   * during init() phase.
   */
  public AbstractFeature _assignedField;



  /**
   * The target containing the field of the assignment.
   */
  public Expr _target;


  /**
   * Is this an allowed assignment to an index var, i.e., an assignment to an
   * index var that happens in the nextIteration part of a loop.
   */
  boolean _indexVarAllowed = false;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param v
   */
  public AbstractAssign(Expr v)
  {
    if (CHECKS) check
      (v != null);

    this._value = v;
  }


  /**
   * Constructor for Assign loaded from .fum/MIR module file be front end.
   *
   * @param f the feature we are assigning a value to
   *
   * @param t the target value containing f
   *
   * @param v the value to be assigned.
   */
  public AbstractAssign(AbstractFeature f, Expr t, Expr v)
  {
    this._assignedField = f;
    this._target = t;
    this._value = v;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this
   */
  public AbstractAssign visit(FeatureVisitor v, AbstractFeature outer)
  {
    _value = _value.visit(v, outer);
    if (_target != null)
      {
        _target = _target.visit(v, outer);
      }
    v.action(this, outer);
    return this;
  }


  /**
   * visit all the expressions within this Assign.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited expressions
   */
  public void visitExpressions(ExpressionVisitor v)
  {
    _value.visitExpressions(v);
    _target.visitExpressions(v);
    super.visitExpressions(v);
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this assignment is used
   */
  public void resolveTypes(Resolution res, Context context)
  {
    resolveTypes(res, context, null);
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this assignment is used
   *
   * @param destructure if this is called for an assignment that is created to
   * replace a Destructure, this refers to the Destructure expression.
   */
  public void resolveTypes(Resolution res, Context context, Destructure destructure)
  {
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
    if (CHECKS) check
      (_assignedField != Types.f_ERROR || Errors.any());

    if (resultTypeKnown(res))
      {
        _value = _value.propagateExpectedType(res, context, _assignedField.resultType());
      }
  }


  /**
   * During type inference: Wrap value that is assigned to lazy type variable
   * into Functions.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this assignment is used
   */
  public void wrapValueInLazy(Resolution res, Context context)
  {
    if (CHECKS) check
      (_assignedField != Types.f_ERROR || Errors.any());

    if (resultTypeKnown(res))
      {
        _value = _value.wrapInLazy(res, context, _assignedField.resultType());
      }
  }


  /**
   * During type inference: automatically unwrap values.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this assignment is used
   */
  public void unwrapValue(Resolution res, Context context)
  {
    if (CHECKS) check
      (_assignedField != Types.f_ERROR || Errors.any());

    if (resultTypeKnown(res))
      {
        _value = _value.unwrap(res, context, _assignedField.resultType());
      }
  }


  /**
   * @return Is the result type of this field already known?
   */
  private boolean resultTypeKnown(Resolution res)
  {
    return  _assignedField != Types.f_ERROR
         && res.state(_assignedField).atLeast(State.RESOLVED_TYPES)
         && _assignedField.resultTypeIfPresent(res) != null;
  }


  /**
   * Boxing for assigned value: Make sure a value type that is assigned to a ref
   * type will be boxed.
   *
   * @param context the source code context where this assignment is used
   */
  public void boxVal(Context context)
  {
    if (CHECKS) check
      (_assignedField != Types.f_ERROR || Errors.any());

    if (_assignedField != Types.f_ERROR)
      {
        _value = _value.box(_assignedField.resultType(), context);
      }
  }


  /**
   * check the types in this assignment
   *
   * @param res the Resolution that performs this checkTypes
   *
   * @param context the source code context where this assignment is used
   */
  public void checkTypes(Resolution res, Context context)
  {
    if (CHECKS) check
      (_assignedField != Types.f_ERROR || Errors.any());

    var f = _assignedField;
    if (f != Types.f_ERROR)
      {
        var frmlT = f.resultType();

        if (CHECKS) check
          (Errors.any() || frmlT != Types.t_ERROR,
           Errors.any() || _value.type() != Types.t_ERROR);

        if (_value.type() != Types.t_ERROR && !frmlT.isDirectlyAssignableFrom(_value.type(), context))
          {
            AstErrors.incompatibleTypeInAssignment(pos(), f, frmlT, _value, context);
          }

        if (CHECKS) check
          (Errors.any() || res._module.lookupFeature(this._target.type().feature(), f.featureName(), f) == f,
           Errors.any() || (_value.type().isVoid() || _value.needsBoxing(frmlT, context) == null || _value.isBoxed()));
      }
  }


  /**
   * Does this expression consist of nothing but declarations? I.e., it has no
   * code that actually would be executed at runtime.
   */
  public boolean containsOnlyDeclarations()
  {
    return false;
  }


  /**
   * Some Expressions do not produce a result, e.g., a Block that is empty or
   * whose last expression is not an expression that produces a result.
   */
  public boolean producesResult()
  {
    return false;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return toString(_assignedField.featureName().baseNameHuman());
  }


  /**
   * toString
   *
   * @return
   */
  protected String toString(String fieldName)
  {
    return "set " + fieldName + " := " + _value;
  }

}

/* end of file */
