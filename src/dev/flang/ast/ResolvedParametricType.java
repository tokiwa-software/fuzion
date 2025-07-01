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
import dev.flang.util.SourcePosition;


/**
 * A ResolvedParametricType is a type for a type parameter found in source code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ResolvedParametricType extends ResolvedType
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
   * The sourcecode position of the declaration point of this type, or, for
   * unresolved types, the source code position of its use.
   */
  public SourcePosition declarationPos() { return _generic.pos(); }


  /**
   * For a resolved normal type, return the underlying feature.
   *
   * @return the underlying feature.
   *
   * @throws Error if this is not resolved or isGenericArgument().
   */
  public AbstractFeature feature()
  {
    if (CHECKS) check
      (Errors.any());

    return Types.f_ERROR;
  }


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
  public AbstractFeature genericArgument()
  {
    return _generic;
  }


  public AbstractType outer()
  {
    if (CHECKS) check
      (Errors.any());
    return null;
  }


  public AbstractType asRef()
  {
    throw new Error("ResolvedParametricType.asRef() not defined");
  }

  public AbstractType asValue()
  {
    throw new Error("ResolvedParametricType.asValue() not defined");
  }

  public AbstractType asThis()
  {
    throw new Error("ResolvedParametricType.asThis() not defined");
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
