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
 * Source of class Highlight
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.feature;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;

import dev.flang.lsp.util.Bridge;
import dev.flang.lsp.util.LSP4jUtils;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.HasSourcePositionTool;
import dev.flang.shared.QueryAST;

/**
 * return document hightlights for calls to feature
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_documentHighlight
 */
public class DocumentHighlights
{
  public static List<? extends DocumentHighlight> getHightlights(DocumentHighlightParams params)
  {
    var pos = Bridge.ToSourcePosition(params);
    var feature = QueryAST.FeatureAt(pos);
    return feature
      .map(f -> {
        return Stream.concat(
          // the feature itself
          Stream.of(f)
            .filter(HasSourcePositionTool.IsItemInFile(LSP4jUtils.getUri(params)))
            .map(af -> Bridge.ToHighlight(af)),
          // the calls to the feature
          FeatureTool.CallsTo(f)
            .map(entry -> entry.getKey())
            .filter(HasSourcePositionTool.IsItemInFile(LSP4jUtils.getUri(params)))
            .map(c -> Bridge.ToHighlight(c)))
          .collect(Collectors.toList());
      })
      .orElse(List.of());
  }
}
