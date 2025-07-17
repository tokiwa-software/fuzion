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
 * Source of class Computation
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.util;

import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;

import dev.flang.lsp.Config;
import dev.flang.lsp.shared.Concurrency;
import dev.flang.lsp.shared.Context;
import dev.flang.lsp.shared.ErrorHandling;
import dev.flang.lsp.shared.concurrent.MaxExecutionTimeExceededException;

public class Computation
{
  private static final int INTERVALL_CHECK_CANCELLED_MS = 250;
  private static LocalDateTime lastErrorMessageSent = LocalDateTime.MIN;

  public static <T> CompletableFuture<T> cancellableComputation(Callable<T> callable, String callee, int maxTimeInMs)
  {
    Context.logger.log("[" + callee + "] started computing.");

    var result = new CompletableFuture<T>();
    return result.completeAsync(() -> {
      try
        {

          var res = Concurrency.runWithPeriodicCancelCheck(callable, () -> {
            if (result.isCancelled())
              {
                throw new CancellationException();
              }
          }, INTERVALL_CHECK_CANCELLED_MS,
            maxTimeInMs);

          var ms = res.nanoSeconds() / 1_000_000;
          Context.logger.log("[" + callee + "] finished in " + ms + "ms");

          return res.result();
        }
      // this exception is an error response in LSP, so we rethrow
      catch (ResponseErrorException e)
        {
          throw e;
        }
      catch (MaxExecutionTimeExceededException e)
        {
          Context.logger.warning("[" + callee + "] Max execution time exceeded: " + e);
        }
      catch (CancellationException e)
        {
          Context.logger.info("[" + callee + "] was cancelled.");
        }
      catch (Throwable th)
        {
          Context.logger.error("[" + callee + "] An unexpected error occurred: " + th + ":");
          Context.logger.error(ErrorHandling.toString(th));
          notifyUser();
        }
      return null;
    });
  }

  private static void notifyUser()
  {
    if (lastErrorMessageSent.plusMinutes(1).isBefore(LocalDateTime.now()))
      {
        lastErrorMessageSent = LocalDateTime.now();
        Config.languageClient().showMessage(new MessageParams(MessageType.Error, "An error occurred. :-( See logs."));
      }
  }
}
