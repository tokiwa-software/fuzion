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
 * Source of class Val
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;


import java.util.function.Function;

import dev.flang.util.ANY;


/**
 * Val represents an abstract value handled by the DFA.
 *
 * This is either an instance of Value, which itself represents a value, or an
 * EmbeddedValue, which is just a wrapper around a Value that is used to
 * determine lifetime information of the instance this value was taken from.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class Val extends ANY
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /*---------------------------  constructors  ---------------------------*/


  /*--------------------------  static methods  -------------------------*/


  /*-----------------------------  methods  -----------------------------*/


  /**
   * In case this value is wrapped in an instance that contains additional
   * information unrelated to the actual value (e.g. EmbeddedValue), get the
   * actual value.
   */
  abstract Value value();


  /**
   * apply f to the unwrapped value and re-wrap
   *
   * @param f function to apply to unwrapped value.
   */
  abstract Val rewrap(DFA dfa, Function<Value,Val> f);


  /**
   * Create the union of the values 'this' and 'v'.
   *
   * @param dfa the current analysis context.
   *
   * @param v the value this value should be joined with.
   *
   * @param clazz the clazz of the resulting value. This is usually the same as
   * the clazz of `this` or `v`, unless we are joining `ref` type values.
   */
  Val joinVal(DFA dfa, Val v, int clazz)
  {
    return rewrap(dfa, a ->
                  v.rewrap(dfa, b -> a.join(dfa, b, clazz)));
  }

}

/* end of file */
