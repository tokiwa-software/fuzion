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

import java.util.Set;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Generic;

import dev.flang.util.List;

import dev.flang.util.SourcePosition;


/**
 * A GenericType is a LibraryType for a type parameter.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class GenericType extends LibraryType
{


  /*----------------------------  variables  ----------------------------*/

  /**
   * The underlying generic:
   */
  Generic _generic;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a plain Type from a given feature that does not have any
   * actual generics.
   */
  GenericType(LibraryModule mod, int at, SourcePosition pos, Generic generic, AbstractType from)
  {
    super(mod, at, pos, from);

    this._generic = generic;
  }

  /*-----------------------------  methods  -----------------------------*/


  public AbstractFeature featureOfType()
  {
    throw new Error("GenericType.featureOfType() not defined");
  }

  public boolean isGenericArgument()
  {
    return true;
  }

  public List<AbstractType> generics()
  {
    throw new Error("GenericType.generics() not defined");
  }


  /**
   * genericArgument gives the Generic instance of a type defined by a generic
   * argument.
   *
   * @return the Generic instance, never null.
   */
  public Generic genericArgument()
  {
    return _generic;
  }

  /**
   * A parametric type is not considered a ref type even it the actual type
   * might very well be a ref.
   */
  public boolean isRef()
  {
    return false;
  }

  public AbstractType outer()
  {
    throw new Error("GenericType.outer() not defined");
  }

  public AbstractType asRef()
  {
    throw new Error("GenericType.asRef() not defined");
  }
  public AbstractType asValue()
  {
    throw new Error("GenericType.asValue() not defined");
  }


}

/* end of file */
