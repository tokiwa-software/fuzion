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
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.Generic;
import dev.flang.ast.Type;

import dev.flang.util.List;
import dev.flang.util.FuzionConstants;
import dev.flang.util.HasSourcePosition;


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
   * Is this a value, ref or this type?  This is one of the Constants
   * FuzionConstants.MIR_FILE_TYPE_IS_VALUE,
   * FuzionConstants.MIR_FILE_TYPE_IS_REF, or
   * FuzionConstants.MIR_FILE_TYPE_IS_THIS.
   */
  int _valRefOrThis;


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
             HasSourcePosition pos,
             AbstractFeature feature,
             int valRefOrThis,
             List<AbstractType> generics,
             AbstractType outer)
  {
    super(mod, at, pos);

    this._feature = feature;
    this._valRefOrThis = valRefOrThis;
    this._generics = generics;
    this._outer = outer;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Dummy visit() for types.
   *
   * NYI: This is called during me.MiddleEnd.findUsedFeatures(). It should be
   * replaced by a different mechanism not using FaetureVisitor.
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
   * @return a new type with same featureOfType(), but using g2/o2 as generics
   * and outer type.
   */
  public AbstractType actualType(List<AbstractType> g2, AbstractType o2)
  {
    if (PRECONDITIONS) require
      (!isGenericArgument());

    return new NormalType(_libModule, _at, _pos, _feature, _valRefOrThis, g2, o2);
  }


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
    return switch (_valRefOrThis)
      {
      case FuzionConstants.MIR_FILE_TYPE_IS_VALUE -> false;
      case FuzionConstants.MIR_FILE_TYPE_IS_REF   -> true;
      case FuzionConstants.MIR_FILE_TYPE_IS_THIS  -> false;
      default -> throw new Error("unexpected NormalType._valRefOrThis: "+_valRefOrThis);
      };
  }

  /**
   * isThisType
   */
  public boolean isThisType()
  {
    return switch (_valRefOrThis)
      {
      case FuzionConstants.MIR_FILE_TYPE_IS_VALUE -> false;
      case FuzionConstants.MIR_FILE_TYPE_IS_REF   -> false;
      case FuzionConstants.MIR_FILE_TYPE_IS_THIS  -> true;
      default -> throw new Error("unexpected NormalType._valRefOrThis: "+_valRefOrThis);
      };
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
        result = isRef() ? this :  new NormalType(_libModule, _at, _pos, _feature, FuzionConstants.MIR_FILE_TYPE_IS_REF, _generics, _outer);
        _asRef = result;
      }
    return result;
  }

  public AbstractType asValue()
  {
    var result = _asValue;
    if (result == null)
      {
        result = !isRef() && !isThisType() ? this :  new NormalType(_libModule, _at, _pos, _feature, FuzionConstants.MIR_FILE_TYPE_IS_VALUE, _generics, _outer);
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
            result = new NormalType(_libModule, _at, _pos, _feature, FuzionConstants.MIR_FILE_TYPE_IS_THIS, _generics, _outer);
          }
        _asThis = result;
      }
    return result;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    String result = "";

    if (outer() != null && !outer().isGenericArgument() && !outer().featureOfType().isUniverse())
      {
        result = outer() + ".";
      }
    if (isRef() != featureOfType().isThisRef())
      {
        result = result + (isRef() ? "ref " : "value ");
      }
    result = result + (featureOfType().featureName().baseName());
    if (generics() != Type.NONE)
      {
        result = result + "<" + generics() + ">";
      }
    return result;
  }


}

/* end of file */
