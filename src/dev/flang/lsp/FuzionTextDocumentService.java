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
 * Source of class FuzionTextDocumentService
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.services.TextDocumentService;

import dev.flang.lsp.feature.CodeActions;
import dev.flang.lsp.feature.CodeLenses;
import dev.flang.lsp.feature.Completion;
import dev.flang.lsp.feature.Definition;
import dev.flang.lsp.feature.Diagnostics;
import dev.flang.lsp.feature.DocumentHighlights;
import dev.flang.lsp.feature.DocumentSymbols;
import dev.flang.lsp.feature.Hovering;
import dev.flang.lsp.feature.InlayHints;
import dev.flang.lsp.feature.References;
import dev.flang.lsp.feature.Rename;
import dev.flang.lsp.feature.SemanticToken;
import dev.flang.lsp.feature.SignatureHelper;
import dev.flang.lsp.util.Computation;
import dev.flang.lsp.util.LSP4jUtils;
import dev.flang.shared.Debouncer;
import dev.flang.shared.SourceText;
import dev.flang.shared.Util;

public class FuzionTextDocumentService implements TextDocumentService
{
  private static final int MAX_COMPUTATION_TIME_MS = 5000;

  @Override
  public void didOpen(DidOpenTextDocumentParams params)
  {
    var textDocument = params.getTextDocument();
    var uri = Util.toURI(textDocument.getUri());
    var text = textDocument.getText();

    SourceText.setText(uri, text);
    afterSetText(uri);
  }

  final Debouncer debouncer = new Debouncer();

  private void afterSetText(URI uri)
  {
    debouncer.debounce(uri, new Runnable() {
      @Override
      public void run()
      {
        Diagnostics.publishDiagnostics(uri);
      }
    }, Config.DIAGNOSTICS_DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params)
  {
    var uri = LSP4jUtils.getUri(params.getTextDocument());
    var text = SyncKindFull(params);
    SourceText.setText(uri, text);
    afterSetText(uri);

  }

  private String SyncKindFull(DidChangeTextDocumentParams params)
  {
    var contentChanges = params.getContentChanges();
    var text = contentChanges.get(0).getText();
    return text;
  }


  @Override
  public void didClose(DidCloseTextDocumentParams params)
  {
    var textDocument = params.getTextDocument();
    var uri = Util.toURI(textDocument.getUri());
    SourceText.removeText(uri);
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params)
  {
    // NYI: UNDER DEVELOPMENT:
  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position)
  {
    return Computation.CancellableComputation(() -> Either.forLeft(Completion.getCompletions(position).collect(Collectors.toList())), "completion", 5000);
  }

  @Override
  public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved)
  {
    return Computation.CancellableComputation(() -> unresolved, "resolve completion", MAX_COMPUTATION_TIME_MS);
  }

  @Override
  public CompletableFuture<Hover> hover(HoverParams params)
  {
    return Computation.CancellableComputation(() -> Hovering.getHover(params), "hover", 5000);
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
    DefinitionParams params)
  {
    return Computation.CancellableComputation(() -> Definition.getDefinitionLocation(params), "definition", 5000);
  }

  @Override
  public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(DocumentHighlightParams params)
  {
    return Computation.CancellableComputation(() -> DocumentHighlights.getHightlights(params), "document highlight", 5000);
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params)
  {
    return Computation.CancellableComputation(() -> References.getReferences(params), "references", 5000);
  }

  @Override
  public CompletableFuture<WorkspaceEdit> rename(RenameParams params)
  {
    return Computation.CancellableComputation(() -> Rename.getWorkspaceEdit(params), "rename", 5000);
  }

  @Override
  public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(
    PrepareRenameParams params)
  {
    return Computation.CancellableComputation(() -> Either3.forSecond(Rename.getPrepareRenameResult(params)),
         "prepare rename", 5000);
  }

  @Override
  public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params)
  {
    return Computation.CancellableComputation(() -> CodeActions.getCodeActions(params), "code action", 5000);
  }

  @Override
  public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params)
  {
    return Computation.CancellableComputation(() -> DocumentSymbols.getDocumentSymbols(params), "document symbol",
      5000);
  }

  /* NYI
  @Override
  public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params)
  {
    return Computation.CancellableComputation(() -> InlayHints.getInlayHints(params), "inlay hint", 5000);
  }
  */

  @Override
  public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params)
  {
    return Computation.CancellableComputation(() -> CodeLenses.getCodeLenses(params), "code lens", 5000);
  }

  @Override
  public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params)
  {
    return Computation.CancellableComputation(() -> SignatureHelper.getSignatureHelp(params), "signature help", 5000);
  }

  @Override
  public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params)
  {
    return Computation.CancellableComputation(() -> SemanticToken.getSemanticTokens(params), "semantic tokens full",
      5000);
  }

}
