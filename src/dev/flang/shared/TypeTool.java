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


package dev.flang.shared;

import java.util.stream.Collectors;

import dev.flang.ast.AbstractType;
import dev.flang.ast.FormalGenerics;
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
  public static String Label(AbstractType type)
  {

    if (ContainsError(type)
      || type.containsUndefined(false))
      {
        return baseName(type);
      }
    if (!type.isGenericArgument() && type.generics() != UnresolvedType.NONE)
      {
        return LabelNoErrorOrUndefined(type) + " "
          + type.generics().stream().map(g -> Util.AddParens(Label(g))).collect(Collectors.joining(" "));
      }
    return LabelNoErrorOrUndefined(type);
  }

  // NYI DUCKTAPE! ensure condition sometimes fails on containsError()
  // unable to reproduce unfortunately
  public static boolean ContainsError(AbstractType type)
  {
    return ErrorHandling.ResultOrDefault(() -> type.containsError(), true);
  }

  /**
   * human readable label for formal generics.
   * @param generics
   * @param brief
   * @return
   */
  public static String Label(FormalGenerics generics, boolean brief)
  {
    if (!generics.isOpen() && generics.list.isEmpty() || brief)
      {
        return "";
      }
    return " " + generics.list + (generics.isOpen() ? "... ": "");
  }

  private static String LabelNoErrorOrUndefined(AbstractType type)
  {
    if (PRECONDITIONS)
      require(!ContainsError(type), !type.containsUndefined(false));

    if (type.isGenericArgument())
      {
        return baseName(type) + (type.isRef().yes() ? " (boxed)": "");
      }
    else if (type.outer() != null)
      {
        return (type.isRef().yes() && (type.feature() == null || !type.feature().isRef()) ? "ref "
                    : !type.isRef().yes() && type.feature() != null && type.feature().isRef() ? "value "
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
    var f = t.isGenericArgument()
                                  ? t.genericArgument().typeParameter()
                                  : t.feature();
    return f
      .featureName()
      .baseName();
  }
}
