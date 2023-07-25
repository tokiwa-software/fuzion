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

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;


/**
 * Generic <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Generic extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The type parameter this generic corresponds to
   */
  private AbstractFeature _typeParameter;


  /**
   * Cached result of type().
   */
  private ResolvedParametricType _type;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a constrained formal generic parameter from a type
   * parameter feature.
   *
   * @param typeParameter the type parameter this is made from
   *
   * @param index the index in the formal generics declaration, starting at 0
   */
  public Generic(AbstractFeature typeParameter)
  {
    _typeParameter = typeParameter;
  }


  /*-----------------------------  methods  -----------------------------*/


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
    return feature().generics();
  }


  /**
   * feature
   *
   * @return
   */
  public AbstractFeature feature()
  {
    return typeParameter().outer();
  }


  /**
   * constraint returns the constraint type of this generic, ANY if no
   * constraint.
   *
   * @return the constraint.
   */
  public AbstractType constraint()
  {
    if (PRECONDITIONS) require
      (_typeParameter.state().atLeast(Feature.State.RESOLVED_TYPES));

    var result = _typeParameter.resultType();

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * constraint resolves the types of the type parameter and then returns the
   * resolved constraint using constraint():
   *
   * @param res the resolution instance.
   *
   * @return the resolved constraint.
   */
  public AbstractType constraint(Resolution res)
  {
    if (PRECONDITIONS) require
      (feature().state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

    res.resolveTypes(_typeParameter);
    return constraint();
  }


  /**
   * Return the name of this formal generic.
   */
  public String name()
  {
    return typeParameter().featureName().baseName();
  }


  /**
   * For a feature `f(A, B type)` the corresponding type feature has an implicit
   * THIS#TYPE type parameter: `f.type(THIS#TYPE, A, B type)`.
   *
   + This checks if this Generic is this implicit type parameter.
   */
  boolean isThisTypeInTypeFeature()
  {
    return typeParameter().state().atLeast(Feature.State.FINDING_DECLARATIONS) && typeParameter().outer().isTypeFeature() && index() == 0;
  }


  /**
   * Return the index of this formal generic within formalGenerics().list.
   *
   * @return the index such that formalGenerics.get(result)) this
   */
  public int index()
  {
    return typeParameter().typeParameterIndex();
  }


  /**
   * If this is a Generic in a type feature, return the original generic for the
   * type feature origin.
   *
   * e.g., for
   *
   *    stack(E type) is
   *
   *      type.empty => stack E
   *
   * the `stack.type.E` that is used in `type.empty` would be replaced by `stack.E`.
   *
   * @return the origin of `E` if it is in a type feature, `this` otherwise.
   */
  Generic typeFeatureOrigin()
  {
    var result = this;
    var o = typeParameter().outer();
    if (!isThisTypeInTypeFeature() && o.isTypeFeature())
      {
        result = o.typeFeatureOrigin().generics().list.get(index()-1);
      }
    return result;
  }


  /**
   * Replace this formal generic by the corresponding actual generic.
   *
   * @param actuals the actual generics that replace this.
   */
  public AbstractType replace(List<AbstractType> actuals)
  {
    if (PRECONDITIONS) require
      (Errors.count() > 0 || !isOpen(),
       Errors.count() > 0 || formalGenerics().sizeMatches(actuals));

    int i = index();
    if (CHECKS) check
      (Errors.count() > 0 || actuals.size() > i);
    return actuals.size() > i ? actuals.get(i) : Types.t_ERROR;
  }


  /**
   * Get the list of actual generics that are represented by this open formal
   * generic argument.
   *
   * If this is the generic C in the formal generics list <A,B,C...> and the
   * actual generics are <a,b,c,d>, then the actual generics for the open
   * argument C are c, d.
   *
   * @param actuals the actual generics list
   *
   * @return the part of actuals that this is replaced by, May be an
   * empty list or an arbitrarily long list.
   */
  public List<AbstractType> replaceOpen(List<AbstractType> actuals)
  {
    if (PRECONDITIONS) require
      (isOpen(),
      Errors.count() >= 0 || formalGenerics().sizeMatches(actuals));

    if (CHECKS) check
      (formalGenerics().list.getLast() == this);

    return formalGenerics().sizeMatches(actuals)
      ? new List<>(actuals.subList(formalGenerics().list.size()-1, actuals.size()).iterator())
      : new List<AbstractType>(Types.t_ERROR);
  }


  /**
   * The type parameter corresponding to this generic.
   */
  public AbstractFeature typeParameter()
  {
    return _typeParameter;
  }


  /**
   * Create a type from this Generic.
   */
  public ResolvedParametricType type()
  {
    if (_type == null)
      {
        _type = new ResolvedParametricType(this);
      }
    return _type;
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
