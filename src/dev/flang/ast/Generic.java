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
 * Source of class Generic
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Iterator;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Generic <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Generic extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The soucecode position of this feature declaration, used for error
   * messages.
   */
  public final SourcePosition _pos;


  /**
   * The index in the formal generics declaration, starting at 0
   */
  final int _index;


  /**
   * the name of this formal generic parameter
   */
  final String _name;


  /**
   * the constraint on this generic paremter, null for the implicit Object
   * constraint.
   */
  private AbstractType _constraint;


  /**
   * The formal generics declaration that contains this generic.
   */
  private FormalGenerics _formalGenerics;


  /**
   * true for a formal generic that can be repeated zero or more times, i.e.,
   * the last formal generic in an open formal generics list.
   */
  private boolean _isOpen;


  /**
   * In case this is a generic that selects a particular argument of an open
   * generic, this gives the index of that argument in the actual generics for
   * the open generic.
   *
   * Otherwise, this is -1.
   */
  private final int _select;


  /**
   * In case this is a generic that selects a particular argument of an open
   * generic, this gives the original open generic.
   *
   * Otherwise, this is null.
   */
  private final Generic _selectFrom;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for an unconstraint formal generic parameter.
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param index the index in the formal generics declaration, starting at 0
   *
   * @param name the name of the generic parameter
   */
  public Generic(SourcePosition pos, int index, String name)
  {
    this(pos, index, name, null);

    if (PRECONDITIONS) require
      (pos != null,
       index >= 0,
       name != null);
  }


  /**
   * Constructor for a constrainted formal generic parameter.
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param index the index in the formal generics declaration, starting at 0
   *
   * @param name the name of the generic parameter
   *
   * @param constraint the constraint on the generic paremter, null for the
   * implicit Object constraint.
   */
  public Generic(SourcePosition pos, int index, String name, AbstractType constraint)
  {
    if (PRECONDITIONS) require
      (pos != null,
       index >= 0,
       name != null);

    _pos = pos;
    _index = index;
    _name = name;
    _constraint = constraint;
    _select = -1;
    _selectFrom = null;
  }


  /**
   * Constructor used by select() to create a Generic that selects on particular
   * actual generic passed to an open generic argument.
   *
   * @param original the original open generic argument
   *
   * @param select the index of the actual argument that is selected, 0 for the
   * first actual argument.
   */
  private Generic(Generic original, int select)
  {
    if (PRECONDITIONS) require
      (original.isOpen(),
       select >= 0);

    _pos = original._pos;
    _index = original._index + select;
    _name = original._name + "." + select;
    _constraint = original._constraint;
    _formalGenerics = original._formalGenerics;
    _select = select;
    _selectFrom = original;
  }


  /*-----------------------------  methods  -----------------------------*/


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
    if (_constraint != null)
      {
        _constraint = _constraint.visit(v, outer);
      }
    v.action(this, outer);
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param outer the root feature that contains this statement.
   */
  public void resolveTypes(Resolution res, AbstractFeature outer)
  {
    if (PRECONDITIONS) require
      (outer == feature());

    if (_constraint != null)
      {
        _constraint = _constraint.resolve(res, outer);
        if (_constraint.isGenericArgument())
          {
            AstErrors.constraintMustNotBeGenericArgument(this);
          }
      }
  }


  /**
   * setFormalGenerics
   *
   * @param f
   *
   * @param open true for a generic that can be repeated 0 or more times.
   */
  public void setFormalGenerics(FormalGenerics f, boolean open)
  {
    if (PRECONDITIONS) require
      (_formalGenerics == null);

    _formalGenerics = f;
    _isOpen = open;

    if (POSTCONDITIONS) ensure
      (_formalGenerics == f,
       _isOpen == open);
  }


  /**
   * true for a formal generic that can be repeated zero or more times, i.e.,
   * the last formal generic in an open formal generics list.
   */
  public boolean isOpen()
  {
    return _isOpen;
  }


  /**
   * formalGeneric give the FormalGenerics instance this is a part of.
   *
   * @return
   */
  public FormalGenerics formalGenerics()
  {
    if (PRECONDITIONS) require
      (_formalGenerics != null);

    return _formalGenerics;
  }


  /**
   * feature
   *
   * @return
   */
  public AbstractFeature feature()
  {
    if (PRECONDITIONS) require
      (_formalGenerics != null);

    return _formalGenerics.feature();
  }


  /**
   * constraint
   *
   * @return
   */
  public AbstractType constraint()
  {
    if (PRECONDITIONS) require
      (feature().state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

    var result = (_constraint == null) ? Types.resolved.t_object
                                       : _constraint;

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * Return the name of this formal generic.
   */
  public String name()
  {
    return _name;
  }


  /**
   * Return the index of this formal generic within formalGenerics().list.
   *
   * @return the index such that formalGenerics.get(result)) this
   */
  public int index()
  {
    return _index;
  }


  /**
   * Replace this formal generic by the corresponding actual generic.
   *
   * @param actualGenerics the actual generics that replace this.
   */
  public AbstractType replace(List<AbstractType> actualGenerics)
  {
    if (PRECONDITIONS) require
      (!isOpen(),
       Errors.count() > 0 || _formalGenerics.sizeMatches(actualGenerics));

    AbstractType result;
    if (_select >= 0)
      {
        var openTypes = _selectFrom.replaceOpen(actualGenerics);
        result = _select < openTypes.size()
          ? openTypes.get(_select)
          : // This is not an error, we can run into this situation, e.g., for
            // the values of a tuple for all the actual clazzes of tuple with
            // fewer actual generic arguments than the max size of a tuple. In
            // this case, the types of all the unused select fields will be
            // t_unit.
            Types.resolved.t_unit;
      }
    else
      {
        result = null;
        int i = index();
        var actuals = actualGenerics.iterator();
        while (i > 0 && actuals.hasNext())
          {
            actuals.next();
            i--;
          }
        check
          (Errors.count() > 0 || actuals.hasNext());
        result = actuals.hasNext() ? actuals.next() : Types.t_ERROR;
      }
    return result;
  }


  /**
   * Get the list of actual generics that are represented by this open formal
   * generic argument.
   *
   * If this is the generic C in the formal generics list <A,B,C...> and the
   * actual generics are <a,b,c,d>, then the actual generics for the open
   * argument C are c, d.
   *
   * @param actualGenerics the actual generics list
   *
   * @return the part of actualGenerics that this is replaced by, May be an
   * empty list or an arbitrarily long list.
   */
  public List<AbstractType> replaceOpen(List<AbstractType> actualGenerics)
  {
    if (PRECONDITIONS) require
      (isOpen(),
       _formalGenerics.sizeMatches(actualGenerics));

    var formals = _formalGenerics.list.iterator();
    var actuals = actualGenerics.iterator();

    // fist, skip all formal/actual generics until we reached the last formal:
    Generic formal = formals.next();
    while (formals.hasNext())
      {
        check
          (formal != this);
        actuals.next();
        formal = formals.next();
      }
    check
      (formal == this);

    // Now, return the tail of actuals:
    return actuals.hasNext() ? new List<>(actuals)
                             : Type.NONE;
  }


  /**
   * For an open generic, create a Type that selects one given actual generic
   * argument.
   *
   * @param i the index of the actual generic argument, must be >= 0.
   *
   * @return a Type that represents the given actual generic argument or t_unit
   * if the index is >= the number of actual generic arguments.
   */
  public Type select(int i)
  {
    if (PRECONDITIONS) require
      (isOpen(),
       i >= 0);

    return new Type(_pos, new Generic(this, i));
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return _name+(_constraint == null ? "" : " -> "+_constraint);
  }


}

/* end of file */
