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
 * Source of class GenericType
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;


import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.TypeKind;
import dev.flang.ast.UnresolvedType;

import dev.flang.util.Errors;
import dev.flang.util.List;


/**
 * A GenericType is a LibraryType for a type parameter.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class GenericType extends LibraryType
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
  GenericType(LibraryModule mod, int at, AbstractFeature generic)
  {
    super(mod, at);

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
  @Override
  protected AbstractFeature backingFeature()
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
   * The mode of the type: ThisType, RefType or ValueType.
   */
  @Override
  public TypeKind kind()
  {
    return TypeKind.GenericArgument;
  }

}

/* end of file */
