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
import dev.flang.util.SourcePosition;


/**
 * Assign <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Assign extends ANY implements Stmnt
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The soucecode position of this assignment, used for error messages.
   */
  final SourcePosition _pos;


  /**
   * The field name if it comes from parsing source code, null if this is
   * generated and assignedField is set directly.
   */
  final String _name;


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
   * @param pos the soucecode position, used for error messages.
   *
   * @param n
   *
   * @param v
   */
  public Assign(SourcePosition pos, String n, Expr v)
  {
    check
      (pos != null,
       n != null,
       v != null);
    this._pos = pos;
    this._name = n;
    this._value = v;
  }


  /**
   * Constructor to create an assignment to a given field.  This is used to
   * create an implicit assignment to result if the code does not do this
   * explicitly.
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param f
   *
   * @param v
   *
   * @param outer the root feature that contains this statement.
   */
  public Assign(SourcePosition pos, Feature f, Expr v, AbstractFeature outer)
  {
    if (PRECONDITIONS) require
      (outer.state().atLeast(Feature.State.RESOLVED_TYPES),
       f != null);

    this._pos = pos;
    this._name = null;
    this._assignedField = f;
    this._value = v;
    this._target = new This(pos, outer, f.outer());
  }


  /**
   * Constructor to create an assignment to a given field.  This is used to
   * create an implicit assignment to result if the code does not do this
   * explicitly.
   *
   * @param res the resolution instance.
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param f
   *
   * @param v
   *
   * @param outer the root feature that contains this statement.
   */
  public Assign(Resolution res, SourcePosition pos, AbstractFeature f, Expr v, AbstractFeature outer)
  {
    if (PRECONDITIONS) require
      (outer.state() == Feature.State.RESOLVING_TYPES   ||
       outer.state() == Feature.State.RESOLVED_TYPES    ||
       outer.state() == Feature.State.TYPES_INFERENCING ||
       outer.state() == Feature.State.RESOLVING_SUGAR2);

    this._pos = pos;
    this._name = null;
    this._assignedField = f;
    this._value = v;
    this._target = This.thiz(res, pos, outer, f.outer());
    if (outer.state().atLeast(Feature.State.TYPES_INFERENCING))
      {
        propagateExpectedType(res, outer);
      }
  }


  /**
   * Constructor for Assign loaded from .fum/MIR module file be front end.
   *
   * @param pos the source position
   *
   * @param f the feature we are assigning a value to
   *
   * @param t the target value containing f
   *
   * @param v the value to be assigned.
   */
  public Assign(SourcePosition pos, AbstractFeature f, Expr t, Expr v)
  {
    this._pos = pos;
    this._name = f.featureName().baseName();
    this._assignedField = f;
    this._target = t;
    this._value = v;
  }



  /*-----------------------------  methods  -----------------------------*/


  /**
   * The soucecode position of this statment, used for error messages.
   */
  public SourcePosition pos()
  {
    return _pos;
  }


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
  public Assign visit(FeatureVisitor v, AbstractFeature outer)
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
    var f = _assignedField;
    if (f == null)
      {
        var fo = res._module.lookupNoTarget(outer, _name, null, destructure == null ? this : null, destructure);
        check
          (Errors.count() > 0 || fo.features.size() <= 1);
        f = fo.filter(_pos, FeatureName.get(_name, 0), __ -> false);
        if (f == null)
          {
            AstErrors.assignmentTargetNotFound(this, outer);
            f = Types.f_ERROR;
          }
        _assignedField = f;
        _target        = fo.target(_pos, res, outer);
      }
    if      (f == Types.f_ERROR          ) { check(Errors.count() > 0); /* ignore */ }
    else if (!f.isField()                ) { AstErrors.assignmentToNonField    (this, f, outer); }
    else if (!_indexVarAllowed       &&
             f instanceof Feature ff &&
             ff.isIndexVarUpdatedByLoop()) { AstErrors.assignmentToIndexVar    (this, f, outer); }
    else if (f == f.outer().resultField())
      {
        if (f.outer() instanceof Feature fo)
          {
            fo.foundAssignmentToResult();
          }
        else
          {
            throw new Error("NYI: Assignement to result defined in library feature not handled well yet!");
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
            AstErrors.incompatibleTypeInAssignment(_pos, f, frmlT, _value);
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
    return "set " + (_name == null ? _assignedField.featureName().baseName() : _name) + " := " + _value;
  }

}

/* end of file */
