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
 * Source of class IO
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp;

import java.io.InputStream;
import java.io.PrintStream;

public class IO
{
  // To be able to suppress the output of the
  // fuzion compiler we need to override
  // System.out etc.
  // In case of transport=stdio
  // will use these fields to write to stdout.
  //
  public static final PrintStream SYS_OUT = System.out;
  public static final PrintStream SYS_ERR = System.err;
  public static final InputStream SYS_IN  = System.in;

}
