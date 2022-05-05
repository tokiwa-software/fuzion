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

import dev.flang.fuir.FUIR;

import dev.flang.fuir.cfg.CFG;

import dev.flang.util.Graph;


/**
 * Effects is an analysis backend that finds effects that are used by a Fuzion
 * program.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Effects extends CFG
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * Map from clazz cl to set of effects ecl that are required for a call to cl.
   */
  Graph<Integer> _effects = new Graph<>();


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create Effects code backend for given intermediate code.
   *
   * @param fuir the intermediate code.
   */
  public Effects(FUIR fuir)
  {
    super(fuir);
    createCallGraph();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Find default effects required by this program
   */
  public void find()
  {
    var cl = _fuir.mainClazzId();
    _effects.successors(cl)
      .stream()
      .forEach(x -> System.out.println(_fuir.clazzAsString(x)));
  }


  /**
   * Add connection from cl to ecl in _effects
   *
   * @param cl a clazz
   *
   * @param ecl an effect that is required by cl
   */
  public void addEffect(int cl, int ecl)
  {
    super.addEffect(cl, ecl);
    _effects.put(cl, ecl);
    propagateEffects(cl, ecl);
  }


  /**
   * Propagate an effect ecl that is required for a call to cl to all the
   * predecessors of cl unless cl itself is a call that installs an effectof
   * type ecl.
   *
   * @param cl a called clazz
   *
   * @param ecl an effect type
   */
  void propagateEffects(int cl, int ecl)
  {
    boolean ignore = false;
    if (_fuir.clazzKind(cl) == FUIR.FeatureKind.Intrinsic &&
        _fuir.clazzIntrinsicName(cl).equals("effect.abortable") &&
        _fuir.clazzOuterClazz(cl) == ecl)
      {
        // cl installs its outer clazz as an effect, so the caller no longer depends on ecl.
      }
    else if (_fuir.clazzKind(cl) == FUIR.FeatureKind.Intrinsic &&
             _fuir.clazzIntrinsicName(cl).equals("fuzion.sys.thread.spawn0"))
      {
        // cl spawns new thread, so caller does not depend on any effects
      }
    else
      {
        // propagate ecl to callers of cl:
        for (var p : _callGraph.predecessors(cl))
          {
            if (!_effects.contains(p, ecl))
              {
                _effects.put(p, ecl);
                propagateEffects(p, ecl);
              }
          }
      }
  }

}

/* end of file */
