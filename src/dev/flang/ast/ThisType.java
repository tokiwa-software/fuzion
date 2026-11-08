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
 * Source of class ThisType
 *
 *---------------------------------------------------------------------*/


package dev.flang.ast;

import java.util.Set;

import dev.flang.util.HasSourcePosition;
import dev.flang.util.List;

/**
 * A this-type refers to the outer instance of its feature.
 */
class ThisType extends ResolvedType {


  /*----------------------------  fields  ----------------------------*/


  /**
   * The feature describing the this-type
   */
  private final AbstractFeature _feature;


  /*----------------------------  constructors  ----------------------------*/


  /**
   * @param feature the feature describing the this-type
   */
  public ThisType(AbstractFeature feature)
  {
    if (PRECONDITIONS) require
      (!feature.isUniverse(),
       feature != Types.f_ERROR);
    _feature = feature;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create a `.this`-type and check it for validity in the given context.
   *
   * @param context the context this type is used in
   *
   * @param at source position for error reporting
   *
   * @param f the feature the this type refers to.
   *
   * @return the `ThisType` instance of `Types.t_ERROR` is `f` is not an outer feature or cotype
   * origin in `context`.
   */
  public static AbstractType create(Context context,
                                    HasSourcePosition at,
                                    AbstractFeature f)
  {
    AbstractType result = new ThisType(f);
    var n = context.outerFeature();
    AbstractFeature o;
    do
      {
        o = n.isCotype() ? n.cotypeOrigin() : n;
        n = o.outer();
      }
    while (o != f && n != null);
    if (o != f)
      {
        AstErrors.illegalThisType(at.pos(), result);
        result = Types.t_ERROR;
      }
    return result;
  }


  /**
   * The feature backing the type.
   */
  @Override
  protected AbstractFeature backingFeature()
  {
    return _feature;
  }


  /**
   * For a normal type, this is the list of actual type parameters given to the type.
   *
   * Requires that this is resolved and isNormalType().
   */
  @Override
  public List<AbstractType> generics()
  {
    throw new UnsupportedOperationException("Unimplemented method 'generics'");
  }


  /**
   * The outer of this type. May be null.
   *
   * Requires that this is resolved and isNormalType().
   */
  @Override
  public AbstractType outer()
  {
    throw new UnsupportedOperationException("Unimplemented method 'outer'");
  }


  /**
   * The mode of the type: ParametricType, ThisType, RefType or ValueType.
   */
  @Override
  public TypeKind kind()
  {
    return TypeKind.ThisType;
  }


  /**
   * traverse a resolved type collecting all features this type uses.
   *
   * @param s the features that have already been found
   */
  @Override
  protected void usedFeatures(Set<AbstractFeature> s)
  {
    s.add(_feature);
  }

}
