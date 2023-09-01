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
 * Source of class OuterType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.SourcePosition;


/**
 * OuterType is a type created by the parser to represent the type of the
 * enclosing feature's outer feature.
 *
 * This will be replaced by that outer feature's thisType during resolve().
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class OuterType extends UnresolvedType
{

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  public OuterType(SourcePosition pos)
  {
    super(pos, "#outer", AbstractCall.NO_GENERICS, null);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * resolve this type
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param feat the outer feature this type is declared in, used
   * for resolution of generic parameters etc.
   */
  AbstractType resolve(Resolution res, AbstractFeature outerfeat)
  {
    if (PRECONDITIONS) require
      (outerfeat != null,
       outerfeat.state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

    return outerfeat.outer().thisType(false);
  }

}

/* end of file */
