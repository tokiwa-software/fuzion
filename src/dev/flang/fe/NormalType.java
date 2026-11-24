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
import dev.flang.ast.ResolvedType;
import dev.flang.ast.TypeKind;

import dev.flang.util.List;


/**
 * A NormalType is a LibraryType that is not a type parameter.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class NormalType extends LibraryType
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

    if (PRECONDITIONS) require
      (typeKind == TypeKind.RefType || typeKind == TypeKind.ValueType,
       generics.stream().allMatch(g -> g instanceof ResolvedType),
       feature.outer() == null || outer.isParametricType() || outer.feature().inheritsFrom(feature.outer()));

    this._feature = feature;
    this._typeKind = typeKind;
    this._generics = generics.freeze();
    this._outer = outer;
  }


  /*-----------------------------  methods  -----------------------------*/


  /** {@inheritDoc} */
  @Override
  public AbstractType asValue()
  {
    return switch (kind())
      {
      case ValueType -> this;
      case RefType   -> new NormalType(_libModule, _at, _feature, TypeKind.ValueType, generics(), outer());
      default        -> throw new Error("unexpected kind "+kind()+" for NormalType");
      };
  }



  /** {@inheritDoc} */
  @Override
  public AbstractType replaceGenericsAndOuter0(List<AbstractType> g2, AbstractType o2)
  {
    return new NormalType(_libModule, _at, _feature, _typeKind, g2, o2);
  }


  /** {@inheritDoc} */
  @Override
  protected AbstractFeature backingFeature()
  {
    return _feature;
  }


  /** {@inheritDoc} */
  @Override
  public List<AbstractType> generics()
  {
    return _generics;
  }


  /** {@inheritDoc} */
  @Override
  public TypeKind kind()
  {
    return _typeKind;
  }

  @Override
  public AbstractType outer()
  {
    return _outer;
  }


}

/* end of file */
