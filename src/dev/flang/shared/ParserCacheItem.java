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
 * Source of class ParserCacheItem
 *
 *---------------------------------------------------------------------*/

package dev.flang.shared;

import java.net.URI;
import java.util.TreeSet;
import java.util.stream.Stream;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Types.Resolved;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;
import dev.flang.util.Errors;
import dev.flang.util.FuzionOptions;

/**
 * holds all artifacts of parsing that we later need
 */
public class ParserCacheItem
{

  private final URI uri;
  private final FrontEndOptions frontEndOptions;
  private final FrontEnd frontEnd;
  private final TreeSet<Errors.Error> errors;
  private final TreeSet<Errors.Error> warnings;
  private final Resolved resolved;

  public ParserCacheItem(URI uri, FrontEndOptions frontEndOptions, FrontEnd frontEnd,
    TreeSet<Errors.Error> errors, TreeSet<Errors.Error> warnings, Resolved resolved)
  {
    this.uri = uri;
    this.frontEndOptions = frontEndOptions;
    this.frontEnd = frontEnd;
    this.errors = errors;
    this.warnings = warnings;
    this.resolved = resolved;
  }

  /**
   * @param uri
   * @return top level feature in source text
   */
  public Stream<AbstractFeature> TopLevelFeatures()
  {
    return ParserTool.DeclaredFeatures(resolved.universe)
      // feature is in file
      .filter(f -> ParserTool.getUri(f.pos()).equals(uri));
  }

  public TreeSet<Errors.Error> warnings()
  {
    return warnings;
  }

  public TreeSet<Errors.Error> errors()
  {
    return errors;
  }

  public FuzionOptions frontEndOptions()
  {
    return frontEndOptions;
  }

  public Resolved resolved()
  {
    return resolved;
  }

  public FrontEnd frontEnd()
  {
    return frontEnd;
  }

  public AbstractFeature universe()
  {
    return resolved.universe;
  }

}
