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
 * Source of class Context
 *
 *---------------------------------------------------------------------*/


package dev.flang.shared;

import dev.flang.util.FuzionOptions;

public class Context
{
  public static FuzionOptions FuzionOptions = new FuzionOptions(0, 0, false, true, SourceText.FuzionHome, null){
    public boolean isLanguageServer() { return true; };
  };
  public static Logger Logger = new DummyLogger();
  public static boolean MiddleEndEnabled = false;
}
