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
 * Source of class Env
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;

import dev.flang.util.ANY;


/**
 * Env represents the set of effects installed in a given environment
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Env extends ANY implements Comparable<Env>
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The DFA instance we are working with.
   */
  DFA _dfa;


  /**
   * The surrounding environment, null if none.
   */
  Env _outer;


  /**
   * The type of the effect stored in this environment
   */
  int _effectType;


  /**
   * The value of the effect.
   */
  Value _effectValue;


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create Env from given outer adding mapping from effect type et to effect
   * value ev.
   *
   * @param outer the surrounding effect environment, null if none.
   *
   * @param et the effect type to add to outer
   *
   * @param ev the effect value to add to outer.
   */
  public Env(DFA dfa, Env outer, int et, Value ev)
  {
    _dfa = dfa;
    _outer = outer;
    _effectType = et;
    _effectValue = ev;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Compare this env to another Env.
   */
  public int compareTo(Env other)
  {
    // NYI: need to be able to compare environemnts. CAVEAT: Currently, Env is
    // mutable due to replaceEffect changing the value!
    return 0;
  }


  /**
   * Create human-readable string from this Env.
   */
  public String toString()
  {
    return "ENV: "+_dfa._fuir.clazzAsString(_effectType)+": "+_effectValue+"\n"+
      (_outer != null ? _outer.toString() : "");
  }


  /**
   * Get effect of given type in this call's environment or the default if none
   * found.
   *
   * @param ecl clazz defining the effect type.
   *
   * @return null in case no effect of type ecl was found
   */
  Value getEffect(int ecl)
  {
    return
      _effectType == ecl  ? _effectValue          :
      _outer      != null ? _outer.getEffect(ecl)
                          : _dfa._defaultEffects.get(ecl);
  }


  /**
   * Replace effect of given type with a new value.
   *
   * NYI: This currently modifies the effect and hence the call. We should check
   * how this could be avoided or handled better.
   *
   * @param ecl clazz defining the effect type.
   *
   * @param e new instance of this effect
   */
  void replaceEffect(int ecl, Value e)
  {
    if (_effectType == ecl)
      {
        var oe = _effectValue;
        var ne = e.join(oe);
        if (Value.compare(oe, ne) != 0)
          {
            _effectValue = ne;
            if (!_dfa._changed)
              {
                _dfa._changedSetBy = "effect.replace called: "+_dfa._fuir.clazzAsString(ecl);
              }
            _dfa._changed = true;
          }
      }
    else if (_outer != null)
      {
        _outer.replaceEffect(ecl, e);
      }
    else
      {
        _dfa.replaceDefaultEffect(ecl, e);
      }
  }

}

/* end of file */
