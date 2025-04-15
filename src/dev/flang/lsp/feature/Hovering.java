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
 * Source of class Hovering
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.feature;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;

import dev.flang.lsp.util.Bridge;
import dev.flang.lsp.util.LSP4jUtils;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.LexerTool;
import dev.flang.shared.QueryAST;

/**
 * on hover returns signature of call
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_hover
 */
public class Hovering
{

  public static Hover getHover(HoverParams params)
  {
    var pos = Bridge.toSourcePosition(params);
    return LexerTool.identTokenAt(pos)
      .flatMap(identToken -> {
        var range = LSP4jUtils.range(identToken);
        return QueryAST
          .featureAt(pos)
          .map(f -> {
            var hoverInfo = FeatureTool.commentOfInMarkdown(f) + System.lineSeparator()
              + System.lineSeparator()
              + FeatureTool.label(f, true);
            var markupContent = new MarkupContent(MarkupKind.MARKDOWN, hoverInfo.trim());
            return new Hover(markupContent, range);
          });
      })
      .orElse(null);
  }

}
