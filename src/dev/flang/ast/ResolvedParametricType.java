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
 * Source of class ResolvedParametricType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Set;

import dev.flang.util.Errors;
import dev.flang.util.List;


/**
 * A ResolvedParametricType is a type for a type parameter found in source code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class ResolvedParametricType extends ResolvedType
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The underlying typeParameter:
   */
  AbstractFeature _typeParameter;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a parametric type that might be boxed.
   */
  ResolvedParametricType(AbstractFeature typeParameter)
  {
    this._typeParameter = typeParameter;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * For a normal type, this is the list of actual type parameters given to the type.
   */
  public List<AbstractType> typeArguments()
  {
    if (CHECKS) check
      (Errors.any());

    return UnresolvedType.NONE;
  }


  /**
   * The feature backing the type.
   *
   * @return the type parameter, never null.
   */
  public AbstractFeature backingFeature()
  {
    return _typeParameter;
  }


  public AbstractType outer()
  {
    if (CHECKS) check
      (Errors.any());
    return null;
  }


  /**
   * traverse a type collecting all features this type uses.
   *
   * @param s the features that have already been found
   */
  @Override
  void usedFeatures(Set<AbstractFeature> s)
  {
    if (!typeParameter().isCoTypesRelayTypeParameter() &&
        /**
         * Must not be recursive definition as in:
         *
         * scenario1 =>
         *   fs(F type : F) =>
         * scenario1
         */
        this != typeParameter().resultType())
      {
        typeParameter().resultType().usedFeatures(s);
      }
  }


  /**
   * The mode of the type: ParametricType, ThisType, RefType or ValueType.
   */
  @Override
  public TypeKind kind()
  {
    return TypeKind.ParametricType;
  }


}

/* end of file */
