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
   * The underlying generic:
   */
  AbstractFeature _generic;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a generic type that might be boxed.
   */
  ResolvedParametricType(AbstractFeature generic)
  {
    this._generic = generic;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * For a normal type, this is the list of actual type parameters given to the type.
   */
  public List<AbstractType> generics()
  {
    if (CHECKS) check
      (Errors.any());

    return UnresolvedType.NONE;
  }


  /**
   * genericArgument gives the Generic instance of a type defined by a generic
   * argument.
   *
   * @return the Generic instance, never null.
   */
  public AbstractFeature backingFeature()
  {
    return _generic;
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
    if (!genericArgument().isCoTypesThisType() &&
        /**
         * Must not be recursive definition as in:
         *
         * scenario1 =>
         *   fs(F type : F) =>
         * scenario1
         */
        this != genericArgument().resultType())
      {
        genericArgument().resultType().usedFeatures(s);
      }
  }


  /**
   * The mode of the type: ThisType, RefType or ValueType.
   */
  @Override
  public TypeKind kind()
  {
    return TypeKind.GenericArgument;
  }


}

/* end of file */
