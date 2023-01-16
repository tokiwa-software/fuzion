/*

This file is part of the Fuzion language implementation.

The Fuzion docs generator implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion docs generator implementation is distributed in the hope that it will be
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
 * Source of class Util
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools.docs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

import dev.flang.ast.AbstractFeature;
import dev.flang.tools.FuzionHome;
import dev.flang.util.FuzionConstants;
import dev.flang.util.SourcePosition;

public class Util
{

  /**
   * Is the feature an argument?
   *
   * @param feature
   * @return
   */
  static boolean isArgument(AbstractFeature feature)
  {
    if (feature.outer() == null)
      {
        return false;
      }
    return feature.outer()
      .arguments()
      .stream()
      .anyMatch(f -> f.equals(feature));
  }

  /**
   * the comment belonging to this feature in HTML
   * @param af
   * @return
   */
  static String commentOf(AbstractFeature af)
  {
    if (af.isUniverse())
      {
        return "";
      }
    var line = af.pos()._line - 1;
    var commentLines = new ArrayList<String>();
    while (true)
      {
        var pos = new SourcePosition(af.pos()._sourceFile, line, 0);
        var strline = Util.lineAt(pos);
        if (line < 1 || !strline.matches("^\\s*#.*"))
          {
            break;
          }
        commentLines.add(strline);
        line = line - 1;
      }
    Collections.reverse(commentLines);

    var result = Html.processComment(af.qualifiedName(), commentLines
      .stream()
      .map(l -> l.trim())
      .map(l -> l
        .replaceAll("^#", "")
        .replaceAll("^ ", ""))
      .collect(Collectors.joining(System.lineSeparator())));
    return result;
  }


  /**
   * get line as string of source position pos
   */
  private static String lineAt(SourcePosition pos)
  {
    var uri = Path.of(pos._sourceFile._fileName.toString()
      .replace(FuzionConstants.SYMBOLIC_FUZION_HOME.toString(), (new FuzionHome())._fuzionHome.normalize().toAbsolutePath().toString())).toUri();
    try
      {
        return Files.readAllLines(Path.of(uri), StandardCharsets.UTF_8).get(pos._line - 1);
      }
    catch (IOException e)
      {
        System.err.println("Fatal error getting line for: " + pos);
        System.exit(1);
        return "";
      }
  }

}
