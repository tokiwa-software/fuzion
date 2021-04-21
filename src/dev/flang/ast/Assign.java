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
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class Assign extends ANY implements Stmnt
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The soucecode position of this assignment, used for error messages.
   */
  final SourcePosition pos;


  /**
   * The field name if it comes from parsing source code, null if this is
   * generated and assignedField is set directly.
   */
  final String name;


  /**
   *
   */
  public Expr value;


  /**
   * Field that is assigned by this assign statement. initialized
   * during init() phase.
   */
  public Feature assignedField;


  public Expr getOuter;


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
    this.pos = pos;
    this.name = n;
    this.value = v;
  }


  /**
   * Constructor to create an assignment to a given field.
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param f
   *
   * @param v
   */
  public Assign(SourcePosition pos, Feature f, Expr v, boolean indexVarAllowed)
  {
    if (PRECONDITIONS) require
      (f != null);

    this.pos = pos;
    this.name = null;
    this.assignedField = f;
    this.value = v;
    this._indexVarAllowed = indexVarAllowed;
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
  public Assign(SourcePosition pos, Feature f, Expr v, Feature outer)
  {
    if (PRECONDITIONS) require
      (outer.state().atLeast(Feature.State.RESOLVED_TYPES),
       f != null);

    this.pos = pos;
    this.name = null;
    this.assignedField = f;
    this.value = v;
    this.getOuter = new This(pos, outer, assignedField.outer());
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
  public Assign(Resolution res, SourcePosition pos, Feature f, Expr v, Feature outer)
  {
    if (PRECONDITIONS) require
      (outer.state() == Feature.State.RESOLVING_TYPES   ||
       outer.state() == Feature.State.RESOLVED_TYPES    ||
       outer.state() == Feature.State.TYPES_INFERENCING ||
       outer.state() == Feature.State.RESOLVING_SUGAR2);

    this.pos = pos;
    this.name = null;
    this.assignedField = f;
    this.value = v;
    this.getOuter = This.thiz(res, pos, outer, assignedField.outer());
    if (outer.state().atLeast(Feature.State.TYPES_INFERENCING))
      {
        propagateExpectedType(res, outer);
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The soucecode position of this statment, used for error messages.
   */
  public SourcePosition pos()
  {
    return pos;
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
  public Assign visit(FeatureVisitor v, Feature outer)
  {
    value = value.visit(v, outer);
    if (getOuter != null)
      {
        getOuter = getOuter.visit(v, outer);
      }
    v.action(this, outer);
    return this;
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this statement.
   */
  public void resolveTypes(Resolution res, Feature outer)
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
   * @param decompose if this is called for an assignment that is created to
   * replace a Decompose, this refers to the Decompose statement.
   */
  void resolveTypes(Resolution res, Feature outer, Decompose decompose)
  {
    Feature f = assignedField;
    if (f == null)
      {
        var fo = outer.findDeclaredInheritedOrOuterFeatures(name, null, decompose == null ? this : null, decompose);
        check
          (Errors.count() > 0 || fo == null || fo.features.size() == 1);
        f = (fo == null || fo.features.size() == 0) ? null : fo.features.values().iterator().next();
      }
    if      (f == null                 ) { FeErrors.assignmentTargetNotFound(this,    outer); }
    else if (!f.isField()              ) { FeErrors.assignmentToNonField    (this, f, outer); }
    else if (!_indexVarAllowed &&
             f._isIndexVarUpdatedByLoop) { FeErrors.assignmentToIndexVar    (this, f, outer); }
    else
      {
        assignedField = f;
        if (f == f.outer().resultField())
          {
            f.outer().foundAssignmentToResult();
          }
        f.scheduleForResolution(res);
        if (getOuter == null)
          {
            this.getOuter = This.thiz(res, pos, outer, assignedField.outer());
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
  public void propagateExpectedType(Resolution res, Feature outer)
  {
    check
      (assignedField != null || Errors.count() > 0);

    if (assignedField != null)
      {
        value = value.propagateExpectedType(res, outer, assignedField.resultType());
      }
  }


  /**
   * Boxing for actual arguments: Find actual arguments of value type that are
   * assigned to formal argument types that are references and box them.
   *
   * @param outer the feature that contains this expression
   */
  public void box(Feature outer)
  {
    check
      (assignedField != null || Errors.count() > 0);

    if (assignedField != null)
      {
        Type frmlT = assignedField.resultType();
        value = value.box(frmlT);
      }
  }


  /**
   * check the types in this assignment
   *
   * @param outer the root feature that contains this statement.
   */
  public void checkTypes()
  {
    check
      (assignedField != null || Errors.count() > 0);

    if (assignedField != null)
      {
        Type frmlT = assignedField.resultType();

        Type actlT = value.type();

        check
          (actlT == Types.intern(actlT));

        check
          (Errors.count() > 0 || (frmlT != Types.t_ERROR &&
                                actlT != Types.t_ERROR    ));

        if (!frmlT.isAssignableFromOrContainsError(actlT))
          {
            FeErrors.incompatibleTypeInAssignment(pos, assignedField, frmlT, actlT, value);
          }

        check
          (this.getOuter.type().featureOfType() == assignedField.outer() || Errors.count() > 0);
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
    return (name == null ? assignedField._featureName.baseName() : name) + " = " + value;
  }

}

/* end of file */
