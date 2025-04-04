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
 * Source of class CaseConverter
 *
 *---------------------------------------------------------------------*/

package dev.flang.shared;

import java.util.stream.Collectors;

public class CaseConverter
{

  /**
   * example: SomeFeature_Name => some_feature_name
   * @param oldName
   * @return
   */
  public static String ToSnakeCase(String oldName)
  {
    var result = oldName.codePoints().mapToObj(cp -> {
      if (Character.isUpperCase(cp))
        {
          return "_" + Character.toString(Character.toLowerCase(cp));
        }
      return Character.toString(cp);
    }).collect(Collectors.joining());
    if (!oldName.startsWith("_"))
      {
        // strip leading underscore
        result = result.replaceAll("^_", "");
      }
    // replace double underscore by one
    return result.replaceAll("_{2,}", "_");
  }


  /**
   * example: snakePascal_case => Snake_Pascal_Case
   * @param oldName
   * @return
   */
  public static String ToSnakePascalCase(String oldName)
  {
    return ToSnakeCase(oldName)
      .codePoints()
      .mapToObj(cp -> Character.toString(cp))
      .reduce("", (res, c) -> {
        if (res.isEmpty() || res.codePointAt(res.length() - 1) == CodepointOf('_'))
          {
            return res + c.toUpperCase();
          }
        return res + c;
      });
  }


  private static int CodepointOf(char c)
  {
    return String.valueOf(c).codePointAt(0);
  }

}
