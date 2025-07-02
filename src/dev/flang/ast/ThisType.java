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
 * Source of class ThisType
 *
 *---------------------------------------------------------------------*/


package dev.flang.ast;

import java.util.Set;

import dev.flang.util.List;

/**
 * A this-type refers to the outer instance of its feature.
 */
class ThisType extends ResolvedType {


  /*----------------------------  fields  ----------------------------*/


  /**
   * The feature describing the this-type
   */
  private final AbstractFeature _feature;


  /*----------------------------  constructors  ----------------------------*/


  /**
   * @param feature the feature describing the this-type
   */
  public ThisType(AbstractFeature feature)
  {
    if (PRECONDITIONS) require
      (!feature.isUniverse(),
       feature != Types.f_ERROR);
    _feature = feature;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The feature backing the type.
   */
  @Override
  protected AbstractFeature backingFeature()
  {
    return _feature;
  }


  /**
   * For a normal type, this is the list of actual type parameters given to the type.
   *
   * Requires that this is resolved and !isGenericArgument().
   */
  @Override
  public List<AbstractType> generics()
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'generics'");
  }


  /**
   * The outer of this type. May be null.
   *
   * Requires that this is resolved and !isGenericArgument().
   */
  @Override
  public AbstractType outer()
  {
    // NYI: UNDER DEVELOPMENT: better null or not legal to call?
    return Types.resolved.universe.selfType();
  }


  /**
   * The mode of the type: ThisType, RefType or ValueType.
   */
  @Override
  public TypeKind kind()
  {
    return TypeKind.ThisType;
  }


  /**
   * This type as a reference.
   *
   * Requires !isGenericArgument().
   */
  @Override
  public AbstractType asRef()
  {
    // NYI: CLEANUP: isAssignableFrom should create the ref-type itself
    if (PRECONDITIONS) require
      (Thread.currentThread().getStackTrace()[2].getMethodName().equals("isAssignableFrom"));

    return ResolvedNormalType
      .create(
        _feature.generics().asActuals(),
        Call.NO_GENERICS,
        _feature.outer().selfType().asThis(),
        _feature,
        TypeKind.RefType);
  }


  /**
   * This type as a value.
   *
   * Requires !isGenericArgument().
   */
  @Override
  public AbstractType asValue()
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'asValue'");
  }


  /**
   * Return this type as a this-type, a type denoting the
   * instance of this type in the current context.
   *
   * Requires that this is resolved and !isGenericArgument().
   */
  @Override
  public AbstractType asThis()
  {
    return this;
  }


  /**
   * traverse a resolved type collecting all features this type uses.
   *
   * @param s the features that have already been found
   */
  @Override
  protected void usedFeatures(Set<AbstractFeature> s)
  {
    s.add(_feature);
  }

}
