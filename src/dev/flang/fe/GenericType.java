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
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.Generic;
import dev.flang.ast.Type;
import dev.flang.ast.Types;

import dev.flang.util.Errors;
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


  /**
   * Is this an explicit reference or value type?  Ref/Value to make this a
   * reference/value type independent of the type of the underlying feature
   * defining a ref type or not, false to keep the underlying feature's
   * ref/value status.
   */
  Type.RefOrVal _refOrVal;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a plain Type from a given feature that does not have any
   * actual generics.
   */
  GenericType(LibraryModule mod, int at, SourcePosition pos, Generic generic, AbstractType from, Type.RefOrVal rov)
  {
    super(mod, at, pos, from);

    this._generic = generic;
    this._refOrVal = Type.RefOrVal.LikeUnderlyingFeature;
    this._refOrVal = rov;
  }


  /**
   * Constructor for a plain Type from a given feature that does not have any
   * actual generics.
   */
  GenericType(LibraryModule mod, int at, SourcePosition pos, Generic generic, AbstractType from)
  {
    this(mod, at, pos, generic, from, Type.RefOrVal.LikeUnderlyingFeature);
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


  public AbstractFeature featureOfType()
  {
    check
      (Errors.count() > 0);

    return Types.f_ERROR;
  }

  public boolean isGenericArgument()
  {
    return true;
  }

  public List<AbstractType> generics()
  {
    check
      (Errors.count() > 0);

    return Type.NONE;
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
    return switch (_refOrVal)
      {
      case Ref -> true;
      case Value -> false;
      case LikeUnderlyingFeature -> false;
      };
  }

  public AbstractType outer()
  {
    check(Errors.count() > 0);
    return null;
  }

  public AbstractType asRef()
  {
    return switch (_refOrVal)
      {
      case Ref -> this;
      default -> new GenericType(_libModule, _at, _pos, _generic, _from, Type.RefOrVal.Ref);
      };
  }
  public AbstractType asValue()
  {
    throw new Error("GenericType.asValue() not defined");
  }

  public String toString()
  {
    return genericArgument().feature().qualifiedName() + "." + genericArgument().name() + (this.isRef() ? " (boxed)" : "");
  }

}

/* end of file */
