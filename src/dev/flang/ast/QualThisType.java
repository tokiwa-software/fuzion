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

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Type created by parser for types like `a.b.this`.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class QualThisType extends UnresolvedType
{


  /*----------------------------  variables  ----------------------------*/


  final List<ParsedName> _qual;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create the type corresponding to "<qual>.this".
   *
   * @param pos the source position
   *
   * @param qual the qualifier
   */
  public QualThisType(List<ParsedName> qual)
  {
    super(SourcePosition.range(qual),
          qual.getLast()._name,
          Call.NO_GENERICS, null, UnresolvedType.RefOrVal.ThisType);

    this._qual = qual;
  }


}

/* end of file */
