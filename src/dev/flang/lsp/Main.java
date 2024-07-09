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
 * Source of class Main
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

import dev.flang.lsp.enums.Transport;
import dev.flang.util.ANY;
import dev.flang.util.Errors;

/**
 * Main class of Fuzion LSP responsible for starting the language server.
 */
public class Main extends ANY
{


  /**
   * In case of wrong arguments this is called to
   * print usage information and exit with a none zero
   * exit code.
   */
  private static void printUsageAndExit()
  {
    IO.SYS_ERR.println("usage: [-stdio | -socket=<port>]");
    System.exit(1);
  }


  /**
   * Is the str contained in args?
   */
  private static boolean hasArg(String[] args, String str)
  {
    return Arrays.stream(args).map(arg -> arg.trim()).anyMatch(arg -> arg.startsWith(str));
  }


  /**
   * For an arg like -socket=8080 extract the value (8080)
   */
  private static Optional<String> getArg(String[] args, String str)
  {
    return Arrays.stream(args)
      .map(arg -> arg.trim())
      .filter(arg -> arg.startsWith(str))
      .findAny()
      .map(x -> x.split("=")[1]);
  }

  /**
   * get launcher for language server for given parameters
   */
  private static Launcher<LanguageClient> launcher(Transport t, int port) throws InterruptedException, ExecutionException, IOException
  {
    var server = new FuzionLanguageServer();
    switch (t)
      {
      case stdio :
        return buildLauncher(server, IO.SYS_IN, IO.SYS_OUT);
      case socket :
        try (var serverSocket = new ServerSocket(port))
          {
            IO.SYS_OUT.println("Property os.name: " + System.getProperty("os.name"));
            IO.SYS_OUT.println("socket opened on port: " + serverSocket.getLocalPort());
            var socket = serverSocket.accept();
            return buildLauncher(server, socket.getInputStream(), socket.getOutputStream());
          }
      default:
        Errors.fatal("Language server transport not yet implemented.");
        return null;
      }
  }

  /**
   * build launcher for language server for given parameters
   */
  private static Launcher<LanguageClient> buildLauncher(FuzionLanguageServer server, InputStream in, OutputStream out)
    throws IOException
  {
    return new Launcher.Builder<LanguageClient>()
      .setLocalService(server)
      .setRemoteInterface(LanguageClient.class)
      .setInput(in)
      .setOutput(out)
      // NYI: .setExecutorService(Concurrency.MainExecutor)
      .create();
  }


  public static void main(String[] args) throws Exception
  {
    System.setProperty("FUZION_DISABLE_ANSI_ESCAPES", "true");
    Errors.MAX_ERROR_MESSAGES = Integer.MAX_VALUE;

    /*
    Servers usually support different communication channels (e.g. stdio, pipes, …).
    To ease the usage of servers in different clients it is highly recommended that a server implementation
    supports the following command line arguments to pick the communication channel:

    stdio: uses stdio as the communication channel.
    pipe: use pipes (Windows) or socket files (Linux, Mac) as the communication channel.
          The pipe / socket file name is passed as the next arg or with --pipe=.
    socket: uses a socket as the communication channel. The port is passed as next arg or with --port=.
    node-ipc: use node IPC communication between the client and the server. This is only support if both client and server run under node.

    https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#implementationConsiderations
     */

    Transport transport = Transport.stdio;
    int port = -1;

    if (hasArg(args, "-stdio"))
      {
        transport = Transport.stdio;
      }
    else if (hasArg(args, "-pipe"))
      {
        // NYI: UNDER DEVELOPMENT:
        printUsageAndExit();
      }
    else if (hasArg(args, "-socket="))
      {
        transport = Transport.socket;
        var p = getArg(args, "-socket");
        if (p.isEmpty())
          {
            printUsageAndExit();
            return;
          }
        port = Integer.parseInt(p.get());
      }
    else
      {
        printUsageAndExit();
      }

    var launcher = launcher(transport, port);
    launcher.startListening();
    var languageClient = launcher.getRemoteProxy();
  }


}
