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
 * Source of class Config
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;

import com.google.gson.JsonObject;

import dev.flang.lsp.enums.Transport;
import dev.flang.lsp.feature.CodeLenses;
import dev.flang.lsp.shared.Context;
import dev.flang.lsp.shared.ErrorHandling;
import dev.flang.lsp.shared.ParserTool;
import dev.flang.lsp.shared.SourceText;
import dev.flang.lsp.shared.Util;
import dev.flang.util.FuzionOptions;

public class Config
{

  public static final boolean ComputeAsync = true;
  public static final long DIAGNOSTICS_DEBOUNCE_DELAY_MS = 1000;
  private static LanguageClient _languageClient;
  private static Transport _transport;
  private static ClientCapabilities _capabilities;

  // can be "messages", "off", "verbose"
  private static String _trace = "off";
  private static int serverPort;

  /**
   * @return the serverPort
   */
  public static int getServerPort()
  {
    return serverPort;
  }

  /**
   * @param serverPort the serverPort to set
   */
  public static void setServerPort(int serverPort)
  {
    Config.serverPort = serverPort;
  }

  public static LanguageClient languageClient()
  {
    return _languageClient;
  }

  public static void setLanguageClient(LanguageClient languageClient)
  {
    _languageClient = languageClient;
  }

  public static Transport transport()
  {
    return _transport;
  }

  public static void setTransport(Transport transport)
  {
    _transport = transport;
  }

  public static boolean DEBUG()
  {
    var debug = System.getenv("DEBUG");
    if (debug == null)
      {
        return false;
      }
    return debug.toLowerCase().equals("true");
  }

  public static void setConfiguration(List<Object> configuration)
  {
    var json = (JsonObject) configuration.get(0);

    Context.logger.info("[Config] received: " + json);

    setModules(json);
    setFuzionOptions(json);
    setCodeLensOptions(json);
    setFuirEnabled(json);
  }

  private static void setFuirEnabled(JsonObject json)
  {
    Context.middleEndEnabled = ErrorHandling.resultOrDefault(() -> json.get("middle_end_enabled").getAsBoolean(), false);
  }


  private static void setCodeLensOptions(JsonObject json)
  {
    try
      {
        var codeLens = json
          .getAsJsonObject("code_lens");

        CodeLenses.CallGraphEnabled =
          ErrorHandling.resultOrDefault(() -> codeLens.get("call_graph").getAsBoolean(), true);
        CodeLenses.SyntaxTreeEnabled =
          ErrorHandling.resultOrDefault(() -> codeLens.get("syntax_tree").getAsBoolean(), true);
        CodeLenses.RunEnabled = ErrorHandling.resultOrDefault(() -> codeLens.get("run").getAsBoolean(), true);

      }
    catch (Exception e)
      {
        Context.logger.error("[Config] parsing of code lens options failed.");
      }
  }

  private static void setFuzionOptions(JsonObject json)
  {
    try
      {
        var options = json
          .getAsJsonObject("options");

        Context.fuzionOptions = new FuzionOptions(
          ErrorHandling.resultOrDefault(() -> options.get("verbosity").getAsInt(), 0),
          ErrorHandling.resultOrDefault(() -> options.get("debugLevel").getAsInt(), 0),
          ErrorHandling.resultOrDefault(() -> options.get("safety").getAsBoolean(), true),
          ErrorHandling.resultOrDefault(() -> options.get("enableUnsafeIntrinsics").getAsBoolean(), true),
          SourceText.fuzionHome, null)
          {
            @Override
            public boolean isLanguageServer()
            {
              return true;
            }
          };

        Context.logger.log("[Config] FuzionOptions: verbosity(" + Context.fuzionOptions.verbose() + "), debugLevel("
          + Context.fuzionOptions.fuzionDebugLevel() + "), safety(" + Context.fuzionOptions.fuzionSafety() + ").");
      }
    catch (Exception e)
      {
        Context.logger.error("[Config] parsing of fuzion options failed.");
      }
  }

  private static void setJavaModules(JsonObject json)
  {
    try
      {
        var modules = json
          .getAsJsonObject("java")
          .getAsJsonArray("modules");

        var result = Util.streamOf(modules.iterator())
          .map(x -> x.getAsString())
          .collect(Collectors.toUnmodifiableList());

        Context.logger.log("[Config] Java modules: " + result.stream().collect(Collectors.joining(", ")));
        ParserTool.setModules(result);
      }
    catch (Exception e)
      {
        Context.logger.error("[Config] parsing of java modules failed.");
      }
  }

  public static void setTrace(String value)
  {
    _trace = value;
  }

  public static String getTrace()
  {
    return _trace;
  }

  public static void setClientCapabilities(ClientCapabilities capabilities)
  {
    _capabilities = capabilities;
  }

  public static ClientCapabilities getClientCapabilities()
  {
    return _capabilities;
  }

}
