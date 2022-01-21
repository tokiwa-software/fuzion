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

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.HasSourcePosition;


/**
 * AbstractAssign represents an Assignment, created either from Source code or
 * loaded from a .fum library file.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractAssign extends ANY implements Stmnt, HasSourcePosition
{


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  public Expr _value;


  /**
   * Field that is assigned by this assign statement. initialized
   * during init() phase.
   */
  public AbstractFeature _assignedField;


  public Expr _target;


  /**
   * Is this an allowed assignment to an index var, i.e., an assignment to an
   * index var that happens in the nextIteration part of a loop.
   */
  boolean _indexVarAllowed = false;


  public int tid_ = -1;  // NYI: Used by dev.flang.be.interpreter, REMOVE!


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param v
   */
  public AbstractAssign(Expr v)
  {
    check
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
   * visit all the features, expressions, statements within this feature.
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
   * visit all the statements within this Assign.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited statements
   */
  public void visitStatements(StatementVisitor v)
  {
    Stmnt.super.visitStatements(v);
    _value.visitStatements(v);
    _target.visitStatements(v);
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
    resolveTypes(res, outer, null);
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this statement.
   *
   * @param destructure if this is called for an assignment that is created to
   * replace a Destructure, this refers to the Destructure statement.
   */
  void resolveTypes(Resolution res, AbstractFeature outer, Destructure destructure)
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
   * @param outer the feature that contains this expression
   *
   * @param t the expected type.
   */
  public void propagateExpectedType(Resolution res, AbstractFeature outer)
  {
    check
      (_assignedField != Types.f_ERROR || Errors.count() > 0);

    if (_assignedField != Types.f_ERROR)
      {
        _value = _value.propagateExpectedType(res, outer, _assignedField.resultType());
      }
  }


  /**
   * Boxing for actual arguments: Find actual arguments of value type that are
   * assigned to formal argument types that are references and box them.
   *
   * @param outer the feature that contains this expression
   */
  public void box(AbstractFeature outer)
  {
    check
      (_assignedField != Types.f_ERROR || Errors.count() > 0);

    if (_assignedField != Types.f_ERROR)
      {
        _value = _value.box(_assignedField.resultType());
      }
  }


  /**
   * check the types in this assignment
   *
   * @param outer the root feature that contains this statement.
   */
  public void checkTypes(Resolution res)
  {
    check
      (_assignedField != Types.f_ERROR || Errors.count() > 0);

    var f = _assignedField;
    if (f != Types.f_ERROR)
      {
        var frmlT = f.resultType();

        check
          (Errors.count() > 0 || frmlT != Types.t_ERROR);

        if (!frmlT.isAssignableFrom(_value))
          {
            AstErrors.incompatibleTypeInAssignment(pos(), f, frmlT, _value);
          }

        check
          (res._module.lookupFeature(this._target.type().featureOfType(), f.featureName()) == f || Errors.count() > 0);
      }
  }


  /**
   * Does this statement consist of nothing but declarations? I.e., it has no
   * code that actually would be executed at runtime.
   */
  public boolean containsOnlyDeclarations()
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
    return toString(_assignedField.featureName().baseName());
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
