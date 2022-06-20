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
 * Source of class FuzionThread
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import java.util.Stack;
import java.util.TreeMap;

import dev.flang.util.ANY;

import dev.flang.air.Clazz;

import dev.flang.ast.AbstractCall; // NYI: remove dependency! Use dev.flang.fuir instead.


/**
 * FuzionThread contains thread-local data used by interpreted Fuzion code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FuzionThread extends ANY
{


  /*-----------------------------  statics  -----------------------------*/


  /**
   * ThreadLocal to hold current FuzionThread instance.
   */
  static ThreadLocal<FuzionThread> _current_ =
    new ThreadLocal<>()
    {
      protected FuzionThread initialValue() { return new FuzionThread(); }
    };


  /**
   * Get current thread.
   */
  static FuzionThread current()
  {
    return _current_.get();
  }


  /*----------------------------  variables  ----------------------------*/


  /**
   * Current call stack, for debugging output
   */
  Stack<AbstractCall> _callStack = new Stack<>();
  Stack<Clazz> _callStackFrames = new Stack<>();


  /**
   * Currently installed effects.
   */
  TreeMap<Clazz, Value> _effects = new TreeMap<>();

}

/* end of file */
