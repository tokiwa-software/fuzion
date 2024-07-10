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
 * Source of class FuzionLanguageServer
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.HoverOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.SemanticTokensServerFull;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.NotebookDocumentService;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * Implementation of the language server
 */
public class FuzionLanguageServer implements LanguageServer
{
  @Override
  public WorkspaceService getWorkspaceService()
  {
    return new FuzionWorkspaceService();
  }

  @Override
  public TextDocumentService getTextDocumentService()
  {
    return new FuzionTextDocumentService();
  }

  @Override
  public void exit()
  {
    System.exit(0);
  }

  @Override
  public CompletableFuture<Object> shutdown()
  {
    return CompletableFuture.supplyAsync(() -> Boolean.TRUE);
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params)
  {
    var capabilities = new ServerCapabilities();
    var res = new InitializeResult(capabilities);
    return CompletableFuture.supplyAsync(() -> res);
  }


}
