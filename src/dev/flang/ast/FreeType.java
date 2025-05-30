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
 * Source of class FreeType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Optional;

import dev.flang.util.SourcePosition;


/**
 * Type created by parser for types like {@code A : numeric}.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FreeType extends UnresolvedType
{

  /*----------------------------  variables  ----------------------------*/


  final UnresolvedType _constraint;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create the type corresponding to {@code <name> : <constraint>}
   *
   * @param pos the source position
   *
   * @param constraint the constraint.
   */
  public FreeType(SourcePosition pos, String name, UnresolvedType constraint)
  {
    super(pos,
          name,
          Call.NO_GENERICS, null, Optional.empty());

    this._constraint = constraint;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * May this unresolved type be a free type. This is the case for explicit free
   * types such as {@code X : Any}, and for all normal types like {@code XYZ} that are not
   * qualified by an outer type {@code outer.XYZ} and that do not have actual type
   * parameters {@code XYZ T1 T2} and that are not boxed.
   */
  public boolean mayBeFreeType()
  {
    return true;
  }


  /**
   * Is this type a free type?
   */
  public boolean isFreeType()
  {
    return true;
  }


  /**
   * For an unresolved type with mayBeFreeType() == true, this gives the
   * constraint to be used with that free type.
   */
  UnresolvedType freeTypeConstraint()
  {
    return _constraint;
  }


}

/* end of file */
