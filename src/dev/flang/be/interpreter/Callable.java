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
 * Source of interface Callable
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import java.util.ArrayList;

import dev.flang.ast.Type; // NYI: remove dependency! Use dev.flang.fuir instead.


/**
 * Callable represents a call to a feature.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public interface Callable extends BackendCallable
{

  /**
   * Call this with given arguments
   *
   * @param args the arguments to be passed to the call
   *
   * @return the result returned by the call.
   */
  Value call(ArrayList<Value> args);

}

/* end of file */
