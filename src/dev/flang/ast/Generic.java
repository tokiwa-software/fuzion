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
   * The index in the formal generics declaration, starting at 0
   */
  final int _index;


  /**
   * The formal generics declaration that contains this generic.
   */
  private FormalGenerics _formalGenerics;


  /**
   * The type parameter this generic corresponds to
   */
  private AbstractFeature _typeParameter;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a constrainted formal generic parameter from a type
   * parameter feature.
   *
   * @param typeParameter the type parameter this is made from
   *
   * @param index the index in the formal generics declaration, starting at 0
   */
  public Generic(AbstractFeature typeParameter, int index)
  {
    _index = index;
    _typeParameter = typeParameter;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * setFormalGenerics
   *
   * @param f
   *
   * @param open true for a generic that can be repeated 0 or more times.
   */
  public void setFormalGenerics(FormalGenerics f)
  {
    if (PRECONDITIONS) require
      (_formalGenerics == null);

    _formalGenerics = f;

    if (POSTCONDITIONS) ensure
      (_formalGenerics == f);
  }


  /**
   * true for a formal generic that can be repeated zero or more times, i.e.,
   * the last formal generic in an open formal generics list.
   */
  public boolean isOpen()
  {
    return typeParameter().isOpenTypeParameter();
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

    AbstractType result =_typeParameter.state().atLeast(Feature.State.RESOLVED_TYPES)
      ? _typeParameter.resultType()
      : ((Feature) _typeParameter).returnType().functionReturnType();

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * Return the name of this formal generic.
   */
  public String name()
  {
    return typeParameter().featureName().baseName();
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

    int i = index();
    var actuals = actualGenerics.iterator();
    while (i > 0 && actuals.hasNext())
      {
        actuals.next();
        i--;
      }
    if (CHECKS) check
      (Errors.count() > 0 || actuals.hasNext());
    return actuals.hasNext() ? actuals.next() : Types.t_ERROR;
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
        if (CHECKS) check
          (formal != this);
        actuals.next();
        formal = formals.next();
      }
    if (CHECKS) check
      (formal == this);

    // Now, return the tail of actuals:
    return actuals.hasNext() ? new List<>(actuals)
                             : Type.NONE;
  }


  /**
   * The type parameter corresponding to this generic.
   */
  public AbstractFeature typeParameter()
  {
    return _typeParameter;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return name();
  }


}

/* end of file */
