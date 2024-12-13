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
 * Source of class Effects
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.effects;


import dev.flang.fuir.analysis.dfa.DFA;

import dev.flang.util.ANY;
import dev.flang.util.FuzionOptions;


/**
 * Effects is an analysis backend that finds effects that are used by a Fuzion
 * program.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Effects extends ANY
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The options provided to the fz command.
   */
  final FuzionOptions _options;


  /**
   * The dfa
   */
  final DFA _dfa;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create Effects code backend for given intermediate code.
   *
   * @param options arguments provided to {@code fz -effects} command.
   *
   * @param dfa the DFA.
   */
  public Effects(FuzionOptions options, DFA dfa)
  {
    _dfa = dfa;
    this._options = options;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Find default effects required by this program
   */
  public void find()
  {
    var _fuir = _dfa.new_fuir();
    _dfa._defaultEffects
      .keySet()
      .stream()
      .forEach(t ->
               {
                 if (_options.verbose(1))
                   {
                     say("EFFECT type "+_fuir.clazzAsString(t)+" default used is "+_dfa._defaultEffects.get(t));
                     var sb = new StringBuilder();
                     var ignore = _dfa._defaultEffectContexts.get(t).showWhy(sb);
                     say(sb);
                   }
                 else
                   {
                     say(_fuir.clazzAsString(t));
                   }
               });
  }


}

/* end of file */
