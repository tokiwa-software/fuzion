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
 * Source of class FuzionLanguageClient
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp;

import java.util.UUID;

import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class FuzionLanguageClient
{
  public static String startProgress(String title, String message)
  {
    var token = UUID.randomUUID().toString();
    Config.languageClient().createProgress(new WorkDoneProgressCreateParams(Either.forLeft(token)));
    var progress = new WorkDoneProgressBegin();
    progress.setTitle(title);
    progress.setMessage(message);
    // NYI
    progress.setCancellable(false);
    progress.setPercentage(0);
    Config.languageClient().notifyProgress(new ProgressParams(Either.forLeft(token), Either.forLeft(progress)));
    return token;
  }

  public static void endProgress(String token)
  {
    Config.languageClient()
      .notifyProgress(new ProgressParams(Either.forLeft(token), Either.forLeft(new WorkDoneProgressEnd())));
  }
}
