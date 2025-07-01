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
import dev.flang.util.SourcePosition;

/**
 * A this-type refers to the outer instance of its feature.
 */
public class ThisType extends ResolvedType {

  private final AbstractFeature _feature;

  public ThisType(AbstractFeature feature)
  {
    if (PRECONDITIONS) require
      (feature != null);
    _feature = feature;
  }

  /**
   * The sourcecode position of the declaration point of this type, or, for
   * unresolved types, the source code position of its use.
   */
  @Override
  public SourcePosition declarationPos() { return _feature == null ? SourcePosition.notAvailable : _feature.pos(); }


  @Override
  public AbstractFeature feature()
  {
    return _feature;
  }

  @Override
  public List<AbstractType> generics()
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'generics'");
  }

  @Override
  public AbstractType outer()
  {
    // NYI: UNDER DEVELOPMENT: better null or not legal to call?
    return Types.resolved.universe.selfType();
  }

  @Override
  public TypeKind kind()
  {
    return TypeKind.ThisType;
  }

  @Override
  public AbstractType asRef()
  {
    return ResolvedNormalType
      .create(
        _feature.generics().asActuals(),
        Call.NO_GENERICS,
        _feature.outer().selfType().asThis(),
        _feature,
        TypeKind.RefType);
  }

  @Override
  public AbstractType asValue()
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'asValue'");
  }

  @Override
  public AbstractType asThis()
  {
    return this;
  }

  @Override
  protected void usedFeatures(Set<AbstractFeature> s)
  {
    s.add(_feature);
  }

}
