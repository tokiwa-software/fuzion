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
 * Source of class Bridge
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.util;

import java.net.URI;
import java.nio.file.Path;

import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.ParserTool;
import dev.flang.shared.SourcePositionTool;
import dev.flang.shared.SourceText;
import dev.flang.shared.Util;
import dev.flang.util.ANY;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;

/**
 * provides bridge utility functions converting between lsp4j <-> fuzion
 */
public class Bridge extends ANY
{

  public static Position toPosition(SourcePosition sourcePosition)
  {
    if (PRECONDITIONS)
      require(!sourcePosition.isBuiltIn());
    return new Position(sourcePosition.line() - 1, sourcePosition.column() - 1);
  }

  public static Location toLocation(SourcePosition start, SourcePosition end)
  {
    if (PRECONDITIONS)
      require(!start.isBuiltIn());
    return new Location(ParserTool.getUri(start).toString(), new Range(toPosition(start), toPosition(end)));
  }

  public static Range toRange(AbstractFeature feature)
  {
    if (PRECONDITIONS)
      require(!feature.pos().isBuiltIn());

    return new Range(toPosition(feature.pos()), toPosition(ParserTool.endOfFeature(feature)));
  }

  public static Range toRange(SourcePosition pos)
  {
    return new Range(toPosition(pos), toPosition(new SourcePosition(pos._sourceFile, pos.byteEndPos())));
  }

  public static Range toRangeBaseName(AbstractFeature feature)
  {
    var bareNamePosition = FeatureTool.BareNamePosition(feature);
    return new Range(
      toPosition(bareNamePosition),
      toPosition(SourcePositionTool.ByLineColumn(bareNamePosition._sourceFile,
        bareNamePosition.line(),
        bareNamePosition.column() + Util.CharCount(FeatureTool.BareName(feature)))));
  }

  public static DocumentSymbol toDocumentSymbol(AbstractFeature feature)
  {
    return new DocumentSymbol(FeatureTool.Label(feature, false), symbolKind(feature), toRange(feature), toRange(feature));
  }

  private static SymbolKind symbolKind(AbstractFeature feature)
  {
    if (feature.isChoice())
      {
        return SymbolKind.Enum;
      }
    if (feature.isBuiltInPrimitive() && "bool".equals(feature.featureName().baseName()))
      {
        return SymbolKind.Boolean;
      }
    if (feature.isBuiltInPrimitive())
      {
        return SymbolKind.Number;
      }
    if (feature.isConstructor() || feature.isIntrinsic())
      {
        return SymbolKind.Constructor;
      }
    if (feature.isField())
      {
        return SymbolKind.Constant;
      }
    if (feature.isRoutine())
      {
        return SymbolKind.Function;
      }
    return SymbolKind.Class;
  }

  public static TextDocumentPositionParams toTextDocumentPosition(SourcePosition sourcePosition)
  {
    return LSP4jUtils.textDocumentPositionParams(ParserTool.getUri(sourcePosition), toPosition(sourcePosition));
  }

  public static SourcePosition toSourcePosition(TextDocumentPositionParams params)
  {
    return SourcePositionTool.ByLineColumn(toSourceFile(Util.toURI(params.getTextDocument().getUri())),
      params.getPosition().getLine() + 1, params.getPosition().getCharacter() + 1);
  }

  public static Location toLocation(AbstractCall call)
  {
    if (PRECONDITIONS)
      require(!call.pos().isBuiltIn());
    return new Location(ParserTool.getUri(call.pos()).toString(),
      toRange(call));
  }

  private static Range toRange(AbstractCall call)
  {
    var start = toPosition(call.pos());
    var nameLength = Util.CharCount(FeatureTool.BareName(call.calledFeature()));
    return new Range(start, new Position(start.getLine(), start.getCharacter() + nameLength));
  }

  public static Location toLocation(AbstractFeature af)
  {
    if (PRECONDITIONS)
      require(!af.pos().isBuiltIn());
    return new Location(ParserTool.getUri(af.pos()).toString(),
      new Range(toPosition(af.pos()), toPosition(ParserTool.endOfFeature(af))));
  }

  public static DocumentHighlight toHighlight(AbstractCall c)
  {
    return new DocumentHighlight(toRange(c), DocumentHighlightKind.Read);
  }

  public static DocumentHighlight toHighlight(AbstractFeature af)
  {
    return new DocumentHighlight(toRangeBaseName(af), DocumentHighlightKind.Text);
  }

  /**
   * The source file of an URI
   */
  private static SourceFile toSourceFile(URI uri)
  {
    if (PRECONDITIONS)
      require(!uri.equals(SourceFile.STDIN.toUri()));

    var filePath = Path.of(uri);
    if (filePath.equals(SourcePosition.builtIn._sourceFile._fileName))
      {
        return SourcePosition.builtIn._sourceFile;
      }
    return new SourceFile(filePath, SourceText.getText(uri).getBytes());
  }
}
