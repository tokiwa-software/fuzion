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
 * Source of class DocumentSymbols
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.feature;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.ast.AbstractFeature;
import dev.flang.lsp.util.Bridge;
import dev.flang.lsp.util.LSP4jUtils;
import dev.flang.shared.ParserTool;

public class DocumentSymbols
{
  public static List<Either<SymbolInformation, DocumentSymbol>> getDocumentSymbols(DocumentSymbolParams params)
  {
    return ParserTool.TopLevelFeatures(LSP4jUtils.getUri(params.getTextDocument()))
      .map(f -> DocumentSymbols.DocumentSymbolTree(f))
      .<Either<SymbolInformation, DocumentSymbol>>map(x -> Either.forRight(x))
      .collect(Collectors.toList());
  }

  public static DocumentSymbol DocumentSymbolTree(AbstractFeature feature)
  {
    var documentSymbol = Bridge.ToDocumentSymbol(feature);
    var children = ParserTool.DeclaredFeatures(feature)
      .map(f -> {
        return DocumentSymbolTree(f);
      })
      .collect(Collectors.toList());
    documentSymbol.setChildren(children);
    return documentSymbol;
  }

}
