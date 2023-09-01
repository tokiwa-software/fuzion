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
 * Source of class BuiltInType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.SourcePosition;

/**
 * A BuiltInType is an unresolved type representing a built-in type that does
 * not appear explicitly in the source code but that is needed in the parsing
 * phase.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class BuiltInType extends ParsedType
{


  /*--------------------------  constructors  ---------------------------*/


  public BuiltInType(String name)
  {
    super(SourcePosition.builtIn, name, NONE, null);
  }


  /**
   * Constructor for built-in types
   *
   * @param ref true iff we create a ref type
   *
   * @param n the name, such as "int", "bool".
   */
  BuiltInType(boolean ref, String n)
  {
    super(SourcePosition.builtIn, n, NONE, null,
          ref ? RefOrVal.Boxed
              : RefOrVal.LikeUnderlyingFeature);
  }

}

/* end of file */
