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
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.TypeMode;

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
  TypeMode _typeMode;


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
             TypeMode typeMode,
             List<AbstractType> generics,
             AbstractType outer)
  {
    super(mod, at);

    this._feature = feature;
    this._typeMode = typeMode;
    this._generics = generics;
    this._generics.freeze();
    this._outer = outer;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The sourcecode position of the declaration point of this type, or, for
   * unresolved types, the source code position of its use.
   */
  public SourcePosition declarationPos() { return feature().pos(); }


  /**
   * Dummy visit() for types.
   *
   * NYI: This is called during me.MiddleEnd.findUsedFeatures(). It should be
   * replaced by a different mechanism not using FeatureVisitor.
   */
  public AbstractType visit(FeatureVisitor v, AbstractFeature outerfeat)
  {
    return this;
  }


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
  public AbstractType applyTypePars(List<AbstractType> g2, AbstractType o2)
  {
    if (PRECONDITIONS) require
      (!isGenericArgument());

    return new NormalType(_libModule, _at, _feature, _typeMode, g2, o2);
  }


  /**
   * For a resolved normal type, return the underlying feature.
   *
   * @return the underlying feature.
   *
   * @throws Error if this is not resolved or isGenericArgument().
   */
  public AbstractFeature feature()
  {
    return _feature;
  }

  public boolean isGenericArgument()
  {
    return false;
  }

  /**
   * For a normal type, this is the list of actual type parameters given to the type.
   */
  public List<AbstractType> generics()
  {
    return _generics;
  }

  public AbstractFeature genericArgument()
  {
    throw new Error("genericArgument() is not defined for NormalType");
  }

  /**
   * The mode of the type: ThisType, RefType or ValueType.
   */
  @Override
  public TypeMode mode()
  {
    return _typeMode;
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
        result = isRef() ? this :  new NormalType(_libModule, _at, _feature, TypeMode.RefType, _generics, _outer);
        _asRef = result;
      }
    return result;
  }

  public AbstractType asValue()
  {
    var result = _asValue;
    if (result == null)
      {
        result = isValue() ? this :  new NormalType(_libModule, _at, _feature, TypeMode.ValueType, _generics, _outer);
        _asValue = result;
      }
    return result;
  }

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
            result = new NormalType(_libModule, _at, _feature, TypeMode.ThisType, _generics, _outer);
          }
        _asThis = result;
      }
    return result;
  }


}

/* end of file */
