/*

This file is part of the Fuzion language server protocol implementation.

The Fuzion language server protocol implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language server protocol implementation is distributed in the hope that it will be
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
 * Source of class TypeTool
 *
 *---------------------------------------------------------------------*/


package dev.flang.lsp.shared;

import dev.flang.ast.AbstractType;
import dev.flang.util.ANY;

public class TypeTool extends ANY
{

  // NYI: UNDER DEVELOPMENT: DUCKTAPE! ensure condition sometimes fails on containsError()
  // unable to reproduce unfortunately
  public static boolean containsError(AbstractType type)
  {
    return ErrorHandling.resultOrDefault(() -> type.containsError(), true);
  }


  /*
   * the base name of the generic or feature describing the type.
   */
  public static String baseName(AbstractType t)
  {
    return (t.isParametricType()
              ? t.typeParameter()
              : t.feature())
      .featureName()
      .baseName();
  }
}
