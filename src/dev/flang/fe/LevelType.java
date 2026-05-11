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

package dev.flang.fe;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.TypeKind;
import dev.flang.util.List;

public class LevelType extends LibraryType {

  private final AbstractFeature constraint;
  private final int lvl;

  public LevelType(LibraryModule libraryModule, int at, AbstractFeature constraint, int lvl)
  {
    super(libraryModule, at);
    this.constraint = constraint;
    this.lvl = lvl;
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
    var universe = constraint;
    while (!universe.isUniverse())
      {
        universe = universe.outer();
      }
    return universe.selfType();
  }

  @Override
  public TypeKind kind()
  {
    return TypeKind.LevelType;
  }

  @Override
  public int outerLevel()
  {
    return lvl;
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
