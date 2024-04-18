/*

This file is part of the Fuzion language implementation.

The Fuzion language implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language implementation is distributed in the hope that it will be
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
 * Source of class FUIRContext
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import dev.flang.fuir.FUIR;
import dev.flang.util.ANY;

public class FUIRContext extends ANY {

  private static FUIR _fuir;

  /**
   * @return the fuir
   */
  public static FUIR fuir()
  {
    if (PRECONDITIONS) require
      (_fuir != null);

    return _fuir;
  }

  /**
   * @param fuir the fuir to set
   */
  public static void set_fuir(FUIR fuir)
  {
    if (PRECONDITIONS) require
      (_fuir == null);

    _fuir = fuir;
  }

}
