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
 * Source of class SourceText
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.shared;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import java.util.stream.Collectors;

import dev.flang.util.ANY;
import dev.flang.util.FuzionConstants;
import dev.flang.util.SourcePosition;

public class SourceText extends ANY
{
  /**
   * currently open text documents and their contents
   */
  private static final TreeMap<URI, String> textDocuments = new TreeMap<URI, String>();

  public static final Path fuzionHome = Path.of(System.getProperty("fuzion.home"));

  public static void setText(URI uri, String text)
  {
    if (PRECONDITIONS)
      require(text != null);

    textDocuments.put(uri, text);
  }

  public static String getText(URI uri)
  {
    return textDocuments.computeIfAbsent(uri, u -> readFromDisk(u));
  }

  public static void removeText(URI uri)
  {
    textDocuments.remove(uri);
  }

  /**
   * convenience method to get source text by source position.
   */
  public static String getText(SourcePosition params)
  {
    return getText(uriOf(params));
  }

  /**
   * For debugging!
   * All fuzion source text documents that are currently int the cache.
   * @return
   */
  public static String allTexts()
  {
    return textDocuments
      .entrySet()
      .stream()
      .map(e -> e.getKey().toString() + System.lineSeparator() + e.getValue())
      .collect(Collectors.joining(System.lineSeparator()));
  }

  /**
   * Read UTF-8 encoded file at uri to string.
   * @param uri
   * @return
   */
  private static String readFromDisk(URI uri)
  {
    try
      {
        return Files.readString(Path.of(uri), StandardCharsets.UTF_8);
      }
    catch (Exception e)
      {
        ErrorHandling.writeStackTrace(e);
        return null;
      }
  }

  /**
   * the complete line of given source position
   * @param pos
   * @return
   */
  public static String lineAt(SourcePosition pos)
  {
    return pos.line() == 0
      ? ""
      : pos._sourceFile.line(pos.line());
  }

  /**
   * utility function to get the uri of a sourceposition
   *
   * @param sourcePosition
   * @return
   */
  public static URI uriOf(SourcePosition sourcePosition)
  {
    return Path.of(
      sourcePosition._sourceFile._fileName.toString()
        .replace(FuzionConstants.SYMBOLIC_FUZION_MODULE.toString(), fuzionHome.toString() + "/lib/"))
      .toUri();
  }

}
