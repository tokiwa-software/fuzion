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
 * Source of class ResolvedType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;



/**
 * A ResolvedType is a type after type resolution, i.e., it is know if this is a
 * parametric type or a type derived from a feature.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class ResolvedType extends AbstractType
{


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a resolved type
   */
  public ResolvedType()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


}

/* end of file */
