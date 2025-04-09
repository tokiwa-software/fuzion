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
 * Source of class LSP4jLogger
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.util;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;

import dev.flang.lsp.Config;
import dev.flang.shared.Logger;
import dev.flang.util.ANY;

public class LSP4jLogger extends ANY implements Logger
{
  private void message(String str, MessageType messageType)
  {
    if (Config.languageClient() == null)
      {
        return;
      }
    Config.languageClient().logMessage(new MessageParams(messageType, str));
  }

  @Override
  public void error(String str)
  {
    message(str, MessageType.Error);
  }

  @Override
  public void warning(String str)
  {
    message(str, MessageType.Warning);
  }

  @Override
  public void info(String str)
  {
    message(str, MessageType.Info);
  }

  @Override
  public void log(String str)
  {
    message(str, MessageType.Log);
  }

}
