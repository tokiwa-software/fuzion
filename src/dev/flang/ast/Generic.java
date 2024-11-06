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
public class Generic extends ANY implements Comparable<Generic>
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The type parameter this generic corresponds to
   */
  private final AbstractFeature _typeParameter;


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
   */
  public Generic(AbstractFeature typeParameter)
  {
    if (PRECONDITIONS) require
      (typeParameter.isTypeParameter());

    _typeParameter = typeParameter;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * true for a formal generic that can be repeated zero or more times, i.e.,
   * the last formal generic in an open formal generics list.
   */
  public boolean isOpen()
  {
    return _typeParameter.isOpenTypeParameter();
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
   * @param context the source code context where this Generic is used, may be
   * Context.NONE to get the constraint declared for this generic.
   *
   * @return the constraint.
   */
  public AbstractType constraint(Context context)
  {
    if (PRECONDITIONS) require
      (_typeParameter.state().atLeast(State.RESOLVED_TYPES));

    var result = context.constraintFor(_typeParameter);
    if (result == null)
      {
        result = _typeParameter.resultType();
      }

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
   * @param context the source code context where this Generic is used
   *
   * @return the resolved constraint(context).
   */
  public AbstractType constraint(Resolution res, Context context)
  {
    if (PRECONDITIONS) require
      (res.state(feature()).atLeast(State.RESOLVING_DECLARATIONS));

    res.resolveTypes(_typeParameter);
    return constraint(context);
  }


  /**
   * For a feature `f(A, B type)` the corresponding type feature has an implicit
   * THIS#TYPE type parameter: `f.type(THIS#TYPE, A, B type)`.
   *
   + This checks if this Generic is this implicit type parameter.
   */
  boolean isThisTypeInCotype()
  {
    return typeParameter().state().atLeast(State.FINDING_DECLARATIONS) && typeParameter().outer().isCotype() && index() == 0;
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
  Generic cotypeOrigin()
  {
    var result = this;
    var o = typeParameter().outer();
    if (!isThisTypeInCotype() && o.isCotype())
      {
        result = o.cotypeOrigin().generics().list.get(index()-1);
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
      (Errors.any() || !isOpen(),
       Errors.any() || formalGenerics().sizeMatches(actuals));

    int i = index();
    if (CHECKS) check
      (Errors.any() || actuals.size() > i);
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
      formalGenerics().sizeMatches(actuals));

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
   * short string of this Generic, e.g. `T`
   *
   * @return
   */
  public String toString()
  {
    return _typeParameter.featureName().baseNameHuman();
  }


  /**
   * long string of this Generic, e.g. `Sequence.map.B`
   *
   * @return
   */
  public String toLongString()
  {
    return _typeParameter.qualifiedName();
  }

  /**
   * long string of this Generic relative to feature context, e.g. `map.B` if context is `Sequence`
   *
   * @return
   */
  public String toLongString(AbstractFeature context)
  {
    return _typeParameter.qualifiedName(context);
  }


  /**
   * Compare this Generic to other
   */
  public int compareTo(Generic other)
  {
    return _typeParameter.compareTo(other._typeParameter);
  }


}

/* end of file */
