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
  Generic _generic;


  /**
   * Is this an explicit reference or value type?  Ref/Value to make this a
   * reference/value type independent of the type of the underlying feature
   * defining a ref type or not, false to keep the underlying feature's
   * ref/value status.
   */
  UnresolvedType.RefOrVal _refOrVal;


  /**
   * Cached result of asRef(), null if not used yet.
   */
  private ResolvedParametricType _asRef;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a generic type that might be boxed.
   */
  ResolvedParametricType(Generic generic, UnresolvedType.RefOrVal rov)
  {
    if (PRECONDITIONS) require
      (switch (rov) { case Boxed, LikeUnderlyingFeature -> true;
                      case Value, ThisType -> false; });

    this._generic = generic;
    this._refOrVal = rov;
  }


  /**
   * Constructor for a plain generic type.
   */
  ResolvedParametricType(Generic generic)
  {
    this(generic, UnresolvedType.RefOrVal.LikeUnderlyingFeature);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The sourcecode position of the declaration point of this type, or, for
   * unresolved types, the source code position of its use.
   */
  public SourcePosition declarationPos() { return _generic.typeParameter().pos(); }


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outerfeat the feature surrounding this expression.
   */
  public AbstractType visit(FeatureVisitor v, AbstractFeature outerfeat)
  {
    return v.action(this, outerfeat);
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
      case ThisType -> throw new Error("dev.flang.fe.ResolvedParametricType.isRef: unexpected ThisType for ResolvedParametricType '"+this+"'");
      };
  }



  public AbstractType outer()
  {
    if (CHECKS) check
      (Errors.any());
    return null;
  }


  public AbstractType asRef()
  {
    if (_asRef == null)
      {
        _asRef = switch (_refOrVal)
          {
          case Boxed -> this;
          default    -> new ResolvedParametricType(_generic, UnresolvedType.RefOrVal.Boxed);
          };
      }
    return _asRef;
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
  protected void usedFeatures(Set<AbstractFeature> s)
  {
    if (!genericArgument().typeParameter().isTypeFeaturesThisType())
      {
        genericArgument().typeParameter().resultType().usedFeatures(s);
      }
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    String n;
    if (_generic.isThisTypeInTypeFeature())
      {
        var qn = _generic.feature().qualifiedName();
        qn = qn.substring(0, qn.lastIndexOf(".type"));
        n = qn + ".this.type (in type feature)";
      }
    else
      {
        n = _generic.typeParameter().qualifiedName();
      }
    return n + (this.isRef() ? " (boxed)" : "");
  }


}

/* end of file */
