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


import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.TypeKind;

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
   * Is this a value, ref or this type?
   */
  TypeKind _typeKind;


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
  AbstractType _asValue = null;
  AbstractType _asThis = null;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a plain Type from a given feature that does not have any
   * actual generics.
   */
  NormalType(LibraryModule mod,
             int at,
             AbstractFeature feature,
             TypeKind typeKind,
             List<AbstractType> generics,
             AbstractType outer)
  {
    super(mod, at);

    this._feature = feature;
    this._typeKind = typeKind;
    this._generics = generics;
    this._generics.freeze();
    this._outer = outer;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The sourcecode position of the declaration point of this type, or, for
   * unresolved types, the source code position of its use.
   */
  @Override
  public SourcePosition declarationPos() { return feature().pos(); }


  /**
   * For a type that is not a type parameter, create a new variant using given
   * actual generics and outer type.
   *
   * @param g2 the new actual generics to be used
   *
   * @param o2 the new outer type to be used (which may also differ in its
   * actual generics).
   *
   * @return a new type with same feature(), but using g2/o2 as generics
   * and outer type.
   */
  @Override
  public AbstractType applyTypePars(List<AbstractType> g2, AbstractType o2)
  {
    if (PRECONDITIONS) require
      (!isGenericArgument(),
       !isThisType());

    return new NormalType(_libModule, _at, _feature, _typeKind, g2, o2);
  }


  /**
   * For a resolved normal type, return the underlying feature.
   *
   * @return the underlying feature.
   *
   * @throws Error if this is not resolved or isGenericArgument().
   */
  @Override
  public AbstractFeature feature()
  {
    return _feature;
  }


  /**
   * For a normal type, this is the list of actual type parameters given to the type.
   */
  @Override
  public List<AbstractType> generics()
  {
    if (PRECONDITIONS) require
      (!isThisType());
    return _generics;
  }

  @Override
  public AbstractFeature genericArgument()
  {
    throw new Error("genericArgument() is not defined for NormalType");
  }

  /**
   * The mode of the type: ThisType, RefType or ValueType.
   */
  @Override
  public TypeKind kind()
  {
    return _typeKind;
  }

  @Override
  public AbstractType outer()
  {
    // NYI: UNDER DEVELOPMENT:
    // if (PRECONDITIONS)
    //   require(!isThisType());
    return _outer;
  }

  @Override
  public AbstractType asRef()
  {
    var result = _asRef;
    if (result == null)
      {
        result = isRef()
          ? this
          : isThisType()
          ? new NormalType(_libModule, _at, _feature, TypeKind.RefType, _feature.generics().asActuals(), _feature.outer().selfType().asThis())
          : new NormalType(_libModule, _at, _feature, TypeKind.RefType, _generics, _outer);
        _asRef = result;
      }
    return result;
  }

  @Override
  public AbstractType asValue()
  {
    if (PRECONDITIONS) require
      (!isThisType());
    var result = _asValue;
    if (result == null)
      {
        result = isValue() ? this :  new NormalType(_libModule, _at, _feature, TypeKind.ValueType, _generics, _outer);
        _asValue = result;
      }
    return result;
  }

  @Override
  public AbstractType asThis()
  {
    var result = _asThis;
    if (result == null)
      {
        if (isThisType())
          {
            result = this;
          }
        else
          {
            result = new NormalType(_libModule, _at, _feature, TypeKind.ThisType, _generics, _outer);
          }
        _asThis = result;
      }
    return result;
  }


}

/* end of file */
