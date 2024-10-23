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

import dev.flang.util.FuzionConstants;
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
    super(pos, FuzionConstants.OUTER_TYPE_NAME, AbstractCall.NO_GENERICS, null);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * resolve this type
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this type is used, used for
   * resolution of generic parameters etc.
   */
  @Override
  AbstractType resolve(Resolution res, Context context)
  {
    if (PRECONDITIONS) require
      (context != null,
       res.state(context.outerFeature()).atLeast(State.RESOLVING_DECLARATIONS));

    return context.outerFeature().outer().thisType(false);
  }

}

/* end of file */
