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
 * Source of class QualThisType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Optional;

import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Type created by parser for types like {@code a.b.this}.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class QualThisType extends UnresolvedType
{

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create the type corresponding to {@code <qual>.this}.
   *
   * @param qual the qualifier
   */
  public QualThisType(List<ParsedName> qual)
  {
    // NYI: BUG: need to take complete qualifier into account!
    super(SourcePosition.range(qual),
          qual.getLast()._name,
          Call.NO_GENERICS, null, Optional.of(TypeMode.ThisType));
  }


}

/* end of file */
