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

import dev.flang.lsp.feature.Commands;
import dev.flang.lsp.feature.Completion;
import dev.flang.lsp.feature.SemanticToken;
import dev.flang.lsp.feature.SignatureHelper;
import dev.flang.lsp.shared.Concurrency;
import dev.flang.lsp.shared.Context;

/**
 * Implementation of the language server
 */
public class FuzionLanguageServer implements LanguageServer
{

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params)
  {
    Config.setClientCapabilities(params.getCapabilities());

    Context.logger.log("[client capabilites] " + Config.getClientCapabilities().toString());

    final InitializeResult res = new InitializeResult(getServerCapabilities());

    return CompletableFuture.supplyAsync(() -> res);
  }

  @Override
  public void cancelProgress(WorkDoneProgressCancelParams params)
  {
    // TODO Auto-generated method stub
    LanguageServer.super.cancelProgress(params);
  }

  private static ConfigurationParams configurationRequestParams()
  {
    var configItem = new ConfigurationItem();
    configItem.setSection("fuzion");
    var configParams = new ConfigurationParams(List.of(configItem));
    return configParams;
  }

  @Override
  public void initialized(InitializedParams params)
  {
    Context.logger.log("[Client] initialized");
    refetchClientConfig();
    registerChangeConfiguration();
  }

  private void registerChangeConfiguration()
  {
    Concurrency.MainExecutor.submit(() -> {
      if (!Config.getClientCapabilities().getWorkspace().getDidChangeConfiguration().getDynamicRegistration())
        {
          Context.logger.log("[Config] Client does not support dynamic registration of `did change configuration`.");
          return;
        }
      try
        {
          Config.languageClient()
            .registerCapability(new RegistrationParams(
              List.of(new Registration("698a8988-ecb4-46bc-a910-a78c60fdfadb", "workspace/didChangeConfiguration"))))
            .get(10, TimeUnit.SECONDS);
        }
      catch (Exception e)
        {
          Context.logger.error("[Config] failed registering workspace/didChangeConfiguration.");
        }
        Context.logger.log("[Config] registered workspace/didChangeConfiguration.");
    });
  }

  public static void refetchClientConfig()
  {
    Concurrency.MainExecutor.submit(() -> {
      try
        {
          if (Config.getClientCapabilities().getWorkspace().getConfiguration())
            {
              var config =
                Config.languageClient().configuration(configurationRequestParams()).get(10, TimeUnit.SECONDS);
              Config.setConfiguration(config);
            }
        }
      catch (Exception e)
        {
          Context.logger.warning("failed getting configuration from client");
        }
    });
  }

  private ServerCapabilities getServerCapabilities()
  {
    var capabilities = new ServerCapabilities();
    initializeInlayHints(capabilities);
    initializeCompletion(capabilities);
    initializeHover(capabilities);
    initializeDefinition(capabilities);
    initializeReferences(capabilities);
    initializeHighlights(capabilities);
    initializeRename(capabilities);
    initializeCodeActions(capabilities);
    initializeCommandExecutions(capabilities);
    initializeDocumentSymbol(capabilities);
    initializeCodeLens(capabilities);
    initializeSignatureHelp(capabilities);
    initializeSemanticTokens(capabilities);
    capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
    return capabilities;
  }

  private void initializeSemanticTokens(ServerCapabilities capabilities)
  {
    // NYI support delta
    // NYI support range
    capabilities.setSemanticTokensProvider(
      new SemanticTokensWithRegistrationOptions(SemanticToken.Legend, new SemanticTokensServerFull(false), false));
  }

  private void initializeCommandExecutions(ServerCapabilities capabilities)
  {
    var commands = Arrays.stream(Commands.values())
      .map(c -> c.name())
      .collect(Collectors.toList());
    capabilities.setExecuteCommandProvider(new ExecuteCommandOptions(commands));
  }

  private void initializeInlayHints(ServerCapabilities capabilities)
  {
    capabilities.setInlayHintProvider(false /* NYI: BUG inlay hints broken */);
  }

  private void initializeSignatureHelp(ServerCapabilities capabilities)
  {
    capabilities
      .setSignatureHelpProvider(new SignatureHelpOptions(Arrays.asList(SignatureHelper.TriggerCharacters.values())
        .stream()
        .map(x -> x.toString())
        .collect(Collectors.toList())));
  }

  private void initializeCodeLens(ServerCapabilities capabilities)
  {
    // NYI implement code lens resolve
    capabilities.setCodeLensProvider(new CodeLensOptions(false));
  }

  private void initializeDocumentSymbol(ServerCapabilities capabilities)
  {
    capabilities.setDocumentSymbolProvider(true);
  }

  private void initializeCodeActions(ServerCapabilities capabilities)
  {
    capabilities.setCodeActionProvider(true);
  }

  private void initializeRename(ServerCapabilities capabilities)
  {
    capabilities.setRenameProvider(new RenameOptions(true));
  }

  private void initializeReferences(ServerCapabilities capabilities)
  {
    capabilities.setReferencesProvider(true);
  }

  private void initializeHighlights(ServerCapabilities capabilities)
  {
    capabilities.setDocumentHighlightProvider(true);
  }

  private void initializeDefinition(ServerCapabilities serverCapabilities)
  {
    serverCapabilities.setDefinitionProvider(true);
  }

  private void initializeHover(ServerCapabilities serverCapabilities)
  {
    var hoverOptions = new HoverOptions();
    hoverOptions.setWorkDoneProgress(Boolean.FALSE);
    serverCapabilities.setHoverProvider(hoverOptions);
  }

  private void initializeCompletion(ServerCapabilities serverCapabilities)
  {
    CompletionOptions completionOptions = new CompletionOptions();
    completionOptions.setResolveProvider(Boolean.FALSE);
    completionOptions.setTriggerCharacters(
      Arrays.asList(Completion.TriggerCharacters.values())
        .stream()
        .map(x -> x.toString())
        .collect(Collectors.toList()));
    serverCapabilities.setCompletionProvider(completionOptions);
  }

  @Override
  public CompletableFuture<Object> shutdown()
  {
    return CompletableFuture.supplyAsync(() -> Boolean.TRUE);
  }

  @Override
  public void exit()
  {
    System.exit(0);
  }

  @Override
  public TextDocumentService getTextDocumentService()
  {
    return new FuzionTextDocumentService();
  }

  @Override
  public WorkspaceService getWorkspaceService()
  {
    return new FuzionWorkspaceService();
  }

  @Override
  public NotebookDocumentService getNotebookDocumentService()
  {
    return new FuzionNotebookService();
  }

  @Override
  public void setTrace(SetTraceParams params)
  {
    Config.setTrace(params.getValue());
  }
}
