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
 * Source of class MarkdownTool
 *
 *---------------------------------------------------------------------*/

package dev.flang.shared;

import java.util.Arrays;
import java.util.stream.Collectors;

public class MarkdownTool
{

  private static String Escape(String str)
  {
    return Arrays.stream(new String[]
      {
          "\\", "`", "*", "_", "{", "}", "[", "]", "(", ")", "#", "+", "-", ".", "!"
      })
      .reduce(str, (text, token) -> {
        return text.replaceAll("\\" + token, "\\" + token);
      });
  }

  public static String Italic(String str)
  {
    if(str.isBlank()){
      return "";
    }
    return Arrays.stream(Escape(str).split(System.lineSeparator()))
      .map(l -> "*" + l + "*")
      .collect(Collectors.joining(System.lineSeparator()));
  }

  public static String Blockquote(String str)
  {
    if(str.isBlank()){
      return "";
    }
    return Arrays.stream(Escape(str).split(System.lineSeparator()))
      .map(l -> "> " + l)
      .collect(Collectors.joining(System.lineSeparator()));
  }

  public static String Bold(String str)
  {
    if(str.isBlank()){
      return "";
    }
    return Arrays.stream(Escape(str).split(System.lineSeparator()))
      .map(l -> "**" + l + "**")
      .collect(Collectors.joining(System.lineSeparator()));
  }

}
