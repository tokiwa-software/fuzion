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
 * Source of class LevelType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.List;

public class LevelType extends ResolvedType {

  private final AbstractFeature constraint;
  private final int level;

  public LevelType(AbstractFeature constraint, int level)
  {
    if (PRECONDITIONS) require
      (constraint.isTypeParameter());

    this.constraint = constraint;
    this.level = level;
  }

  @Override
  protected AbstractFeature backingFeature()
  {
    return constraint;
  }

  @Override
  public List<AbstractType> generics()
  {
    return AbstractCall.NO_GENERICS;
  }

  @Override
  public AbstractType outer()
  {
    return Types.resolved.universe.selfType();
  }

  @Override
  public TypeKind kind()
  {
    return TypeKind.LevelType;
  }

  @Override
  public int outerLevel()
  {
    return level;
  }

  /**
   * This returns feature() unless this is an LevelType
   * Then it returns the feature in the constraint that is referenced
   * by the LevelType.
   */
  @Override
  public AbstractFeature effectiveFeature()
  {
    var l = outerLevel();
    var t = feature().constraint();
    while (l>0)
      {
        t = t.outer();
        l--;
      }
    return t.feature();
  }

}
