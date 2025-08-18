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

import java.util.stream.Collectors;

import dev.flang.ast.AbstractType;
import dev.flang.ast.Types;
import dev.flang.ast.UnresolvedType;
import dev.flang.util.ANY;

public class TypeTool extends ANY
{
  /**
   * human readable label for type
   * @param type
   * @return
   */
  public static String label(AbstractType type)
  {

    if (containsError(type)
      || type.containsUndefined(false))
      {
        return baseName(type);
      }
    if (!type.isGenericArgument() && type.generics() != UnresolvedType.NONE)
      {
        return labelNoErrorOrUndefined(type) + " "
          + type.generics().stream().map(g -> Util.addParens(label(g))).collect(Collectors.joining(" "));
      }
    return labelNoErrorOrUndefined(type);
  }

  // NYI: UNDER DEVELOPMENT: DUCKTAPE! ensure condition sometimes fails on containsError()
  // unable to reproduce unfortunately
  public static boolean containsError(AbstractType type)
  {
    return ErrorHandling.resultOrDefault(() -> type.containsError(), true);
  }


  private static String labelNoErrorOrUndefined(AbstractType type)
  {
    if (PRECONDITIONS)
      require(!containsError(type), !type.containsUndefined(false));

    if (type.isGenericArgument())
      {
        return baseName(type) + (type.isRef() ? " (boxed)": "");
      }
    else if (type.outer() != null)
      {
        return (type.isRef() && (type.feature() == null || !type.feature().isRef()) ? "ref "
                    : !type.isRef() && type.feature() != null && type.feature().isRef() ? "value "
                    : "")
          + (type.feature() == null
                                          ? baseName(type)
                                          : type.feature().featureName().baseName());
      }
    else if (type.feature() == null || type.feature() == Types.f_ERROR)
      {
        return baseName(type);
      }
    else
      {
        return type.feature().featureName().baseName();
      }
  }


  /*
   * the base name of the generic or feature describing the type.
   */
  public static String baseName(AbstractType t)
  {
    return (t.isGenericArgument()
              ? t.genericArgument()
              : t.feature())
      .featureName()
      .baseName();
  }
}
