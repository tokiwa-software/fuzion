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
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.Generic;
import dev.flang.ast.UnresolvedType;
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
  RefOrVal _refOrVal;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a generic type that might be boxed.
   */
  GenericType(LibraryModule mod, int at, Generic generic, RefOrVal rov)
  {
    super(mod, at);

    this._generic = generic;
    this._refOrVal = rov;
  }


  /**
   * Constructor for a plain generic type.
   */
  GenericType(LibraryModule mod, int at, Generic generic)
  {
    this(mod, at, generic, RefOrVal.LikeUnderlyingFeature);
  }

  /*-----------------------------  methods  -----------------------------*/


  /**
   * The sourcecode position of the declaration point of this type, or, for
   * unresolved types, the source code position of its use.
   */
  public SourcePosition declarationPos() { return _generic.typeParameter().pos(); }


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
   * For a resolved normal type, return the underlying feature.
   *
   * @return the underlying feature.
   *
   * @throws Error if this is not resolved or isGenericArgument().
   */
  public AbstractFeature featureOfType()
  {
    if (CHECKS) check
      (Errors.any());

    return Types.f_ERROR;
  }

  public boolean isGenericArgument()
  {
    return true;
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
      case Boxed -> true;
      case Value -> false;
      case LikeUnderlyingFeature -> false;
      case ThisType -> throw new Error("dev.flang.fe.GenericType.isRef: unexpected ThisType for GenericType '"+this+"'");
      };
  }

  /**
   * isThisType
   */
  public boolean isThisType()
  {
    if (this._refOrVal == RefOrVal.ThisType)
      {
        throw new Error("Unexpected ThisType in GenericType");
      }
    return false;
  }

  public AbstractType outer()
  {
    if (CHECKS) check
      (Errors.any());
    return null;
  }

  public AbstractType asRef()
  {
    return switch (_refOrVal)
      {
      case Boxed -> this;
      default    -> new GenericType(_libModule, _at, _generic, RefOrVal.Boxed);
      };
  }
  public AbstractType asValue()
  {
    throw new Error("GenericType.asValue() not defined");
  }
  public AbstractType asThis()
  {
    throw new Error("GenericType.asThis() not defined");
  }

}

/* end of file */
