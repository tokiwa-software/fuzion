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
 * Assign represents an Assignment, created either from Source code or
 * loaded from a .fum library file.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Assign extends AbstractAssign
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The field name if it comes from parsing source code, null if this is
   * generated and assignedField is set directly.
   */
  final String _name;


  /**
   * The soucecode position of this assignment, used for error messages.
   */
  final SourcePosition _pos;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor used be the parser
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param n the name of the assigned field
   *
   * @param v the value assigned to field with name n
   */
  public Assign(SourcePosition pos, String n, Expr v)
  {
    super(v);

    if (CHECKS) check
      (pos != null,
       n != null,
       v != null);

    this._name = n;
    this._pos = pos;
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
  public Assign(SourcePosition pos, AbstractFeature f, Expr v, AbstractFeature outer)
  {
    super(f, new This(pos, outer, f.outer()), v);

    if (PRECONDITIONS) require
      (Errors.count() > 0 ||
       outer.state().atLeast(Feature.State.RESOLVED_TYPES),
       f != null);

    this._name = null;
    this._pos = pos;
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
    super(f, This.thiz(res, pos, outer, f.outer()), v);

    if (PRECONDITIONS) require
      (Errors.count() > 0 ||
       outer.state() == Feature.State.RESOLVING_TYPES   ||
       outer.state() == Feature.State.RESOLVED_TYPES    ||
       outer.state() == Feature.State.TYPES_INFERENCING ||
       outer.state() == Feature.State.RESOLVING_SUGAR2,
       f != null);

    this._name = null;
    this._pos = pos;
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
    return _pos;
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
        if (CHECKS) check
          (Errors.count() > 0 || fo.features.size() <= 1);
        f = fo.filter(pos(), FeatureName.get(_name, 0), __ -> false);
        if (f == null)
          {
            AstErrors.assignmentTargetNotFound(this, outer);
            f = Types.f_ERROR;
          }
        _assignedField = f;
        _target        = fo.target(pos(), res, outer);
      }
    if      (f == Types.f_ERROR          ) { if (CHECKS) check
                                               (Errors.count() > 0);
                                             /* ignore */
                                           }
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
   * toString
   *
   * @return
   */
  public String toString()
  {
    return _name == null ? super.toString() : toString(_name);
  }


}

/* end of file */
