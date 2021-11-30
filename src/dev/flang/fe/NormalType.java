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
 * Source of class NormalType
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import java.util.Set;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Feature;
import dev.flang.ast.Generic;

import dev.flang.util.List;

import dev.flang.util.SourcePosition;


/**
 * A NormalType is a LibraryType that is not a type parameter.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class NormalType extends LibraryType
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * For a type that is not a generic argument, this is the feature the type is
   * based on.
   */
  AbstractFeature _feature;


  /**
   * Is this explicitly a ref type even if _feature is a value type?
   */
  boolean _makeRef;


  /**
   * For a type that is not a generic argument, this is the list of actual
   * generics.
   */
  List<AbstractType> _generics;


  AbstractType _outer;


  /**
   * Cached result of asRef()
   */
  AbstractType _asRef = null;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a plain Type from a given feature that does not have any
   * actual generics.
   */
  NormalType(LibraryModule mod,
             int at,
             SourcePosition pos,
             AbstractFeature feature,
             boolean makeRef,
             List<AbstractType> generics,
             AbstractType outer,
             AbstractType from)
  {
    super(mod, at, pos, from);

    this._feature = feature;
    this._makeRef = makeRef;
    this._generics = generics;
    this._outer = outer;
  }


  /*-----------------------------  methods  -----------------------------*/


  public AbstractFeature featureOfType()
  {
    return _feature;
  }

  public boolean isGenericArgument()
  {
    return false;
  }

  public List<AbstractType> generics()
  {
    return _generics;
  }

  public Generic genericArgument()
  {
    throw new Error("genericArgument() is not defined for NormalType");
  }


  /**
   * A normal type may be an explicit ref type.
   */
  public boolean isRef()
  {
    return _makeRef || _feature.isThisRef();
  }

  public AbstractType outer()
  {
    return _outer;
  }


  public AbstractType asRef()
  {
    var result = _asRef;
    if (result == null)
      {
        result = isRef() ? this :  new NormalType(_libModule, _at, _pos, _feature, true, _generics, _outer, _from.asRef());
        _asRef = result;
      }
    return result;
  }

  public AbstractType asValue()
  {
    throw new Error("GenericType.asValue() not defined");
  }


}

/* end of file */
