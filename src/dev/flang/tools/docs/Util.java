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
import dev.flang.ast.Types;
import dev.flang.ast.Visi;
import dev.flang.tools.FuzionHome;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;

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
    return feature.outer() != null &&
     feature.outer()
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
        return Html.processComment(FuzionConstants.UNIVERSE_NAME, universeComment());
      }
    // arguments that are defined on same line as feature have no comments.
    if (af.isArgument() && af.pos().line() == af.outer().pos().line())
      {
        return "";
      }
    var line = af.pos().line() - 1;
    var commentLines = new ArrayList<String>();

    // NYI: OPTIMIZATION: use lexer to retrieve comments
    while (line > 0 && af.pos()._sourceFile.line(line).matches("(?s)^\\s*#.*"))
      {
        commentLines.add(af.pos()._sourceFile.line(line));
        line = line - 1;
      }

    Collections.reverse(commentLines);

    var result = Html.processComment(af.qualifiedName() + af.featureName().argCount() + "_", commentLines
      .stream()
      .map(l -> l.trim())
      .map(l -> l
        .replaceAll("^#", "")
        .replaceAll("^ ", ""))
      .collect(Collectors.joining(System.lineSeparator())));
    return result;
  }


  private static String universeComment()
  {
    var uri = FuzionHome._fuzionHome.normalize().toAbsolutePath().resolve("modules/base/src/universe.fz").toUri();
    try
      {
        return Files.readAllLines(Path.of(uri), StandardCharsets.UTF_8)
          .stream()
          .dropWhile(l -> !l.startsWith("# universe is the mother"))
          .map(l -> l.replaceAll("^#", "").trim())
          .collect(Collectors.joining(System.lineSeparator()))
          .trim();
      }
    catch (IOException e)
      {
        Errors.fatal("File universe.fz not found");
        return "";
      }
  }


  /**
   * Is the feature or the type it is defining visible outside of its module?
   * @param af
   */
  public static boolean isVisible(AbstractFeature af)
  {
    return af.visibility() == Visi.PRIVPUB
        || af.visibility() == Visi.MODPUB
        || af.visibility() == Visi.PUB;
  }


  static enum Kind {
    RefConstructor,
    ValConstructor,
    Type,
    Cotype,
    Other;

    static Kind classify(AbstractFeature af) {
      return
      // NYI: does not treat features that `Type` inherits but does not redefine as type features, see #3716
        (af.outer() != null && af.outer().isCotype() ||
        (af.outer().compareTo(Types.resolved.f_Type) == 0)                    ? Kind.Cotype
        : !af.definesType()                                                   ? Kind.Other
        : af.isChoice() || af.visibility().eraseTypeVisibility() != Visi.PUB  ? Kind.Type
        : af.isRef()                                                          ? Kind.RefConstructor
                                                                              : Kind.ValConstructor);
    }
  }

}
