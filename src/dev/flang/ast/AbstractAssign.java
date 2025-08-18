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
      (v != null,
       // correct mechanism is Match.addFieldForResult
       !(v instanceof AbstractMatch));

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
    v.action(this);
    return this;
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this assignment is used
   */
  void resolveTypes(Resolution res, Context context)
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
  void propagateExpectedType(Resolution res, Context context)
  {
    if (CHECKS) check
      (_assignedField != Types.f_ERROR || Errors.any());

    if (resultTypeKnown(res))
      {
        var rt = _assignedField.resultType();
        _value = _value.propagateExpectedType(res, context, rt, null);
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
  void wrapValueInLazy(Resolution res, Context context)
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
  void unwrapValue(Resolution res, Context context)
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
    return _assignedField != Types.f_ERROR
        && _assignedField.resultTypeIfPresent(res) != null;
  }


  /**
   * check the types in this assignment
   *
   * @param res the Resolution that performs this checkTypes
   *
   * @param context the source code context where this assignment is used
   */
  void checkTypes(Resolution res, Context context)
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

        if (_value.type() != Types.t_ERROR && frmlT.isAssignableFrom(_value.type(), context).no())
          {
            AstErrors.incompatibleTypeInAssignment(pos(), f, frmlT, _value, context);
          }
        else
          {
            _value.checkAmbiguousAssignmentToChoice(frmlT);
          }


        if (CHECKS) check
          (Errors.any() || res._module.lookupFeature(this._target.type().feature(), f.featureName()) == f);
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
   * Some Expressions do not produce a result, e.g., a Block
   * whose last expression is not an expression that produces a result.
   */
  @Override public boolean producesResult()
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
