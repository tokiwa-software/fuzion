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
    throw new UnsupportedOperationException("Unimplemented method 'generics'");
  }


  /** {@inheritDoc} */
  @Override
  public AbstractType outer()
  {
    throw new UnsupportedOperationException("Unimplemented method 'outer'");
  }


  /** {@inheritDoc} */
  @Override
  public TypeKind kind()
  {
    return TypeKind.ThisType;
  }


  /** {@inheritDoc} */
  @Override
  protected void usedFeatures(Set<AbstractFeature> s)
  {
    s.add(_feature);
  }


  /**
   * A `ThisType` is fixed, i.e., equivalent to `_feature.thisType(true)` if used in the context of
   * `f` and in the chain of `f`, `f.outer()`, `f.outer().outer()`, ... there is a feature `g` such
   * that `g.isFixed()` and `g.outer()==_feature`.
   *
   * If so, `this` is equivalent to `_feature.thisType(true)`.
   *
   * @param context the context this is used in
   */
  boolean isFixedFor(Context context)
  {
    var result = _feature.kind() == Feature.Kind.Function;  // a function can not have children, so all is fixed
    var cf = context.outerFeature();
    var cfo = cf.outer();
    while (cfo != null && !result)
      {
        result = cf.isFixed() && cfo == _feature;  // context is fixed and outer of context is _feature
        cf = cfo;
        cfo = cf.outer();
      }
    return result;
  }


}
