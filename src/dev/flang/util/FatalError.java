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
 * Source of class FatalError
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

/**
 * This error is supposed to be thrown in case a
 * fatal error happens during compilation.
 */
public class FatalError extends Error{

  private int _status;

  /**
   * @return the status
   */
  public int getStatus()
  {
    return _status;
  }

  public FatalError(int status)
  {
    this._status = status;
  }

}
