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
 * Source of class References
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.feature;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ReferenceParams;

import dev.flang.lsp.shared.FeatureTool;
import dev.flang.lsp.shared.QueryAST;
import dev.flang.lsp.util.Bridge;

/**
 * return list of references for feature at cursor position
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_references
 */
public class References
{

  public static List<? extends Location> getReferences(ReferenceParams params)
  {
    var feature = QueryAST.featureAt(Bridge.toSourcePosition(params));
    if (feature.isEmpty())
      {
        return List.of();
      }
    return FeatureTool.callsTo(feature.get())
      .map(entry -> Bridge.toLocation(entry.getKey()))
      .collect(Collectors.toList());
  }

}
