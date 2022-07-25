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

import java.util.TreeMap;

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
   * The call environment used to identify this environment.
   */
  Call _call;


  /**
   * The DFA instance we are working with.
   */
  DFA _dfa;


  /**
   * Sorted array of types that are present in this environment. This is
   * currenlty used to uniquely identify and Env instance, i.e., environments
   * that define the same effect types are joined into one environment.
   */
  int[] _types;


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
   * @param call a call environment used to distinguish this environment from
   * others.
   *
   * @param outer the surrounding effect environment, null if none.
   *
   * @param et the effect type to add to outer
   *
   * @param ev the effect value to add to outer.
   */
  public Env(Call call, Env outer, int et, Value ev)
  {
    _call = call;
    _dfa = call._dfa;

    if (outer == null)
      {
        _types = new int[] { et };
      }
    else if (outer.hasEffect(et))
      {
        _types = outer._types;
      }
    else
      {
        var ot = outer._types;
        var ol = ot.length;
        _types = new int[ol + 1];
        var left = true;
        for (int i = 0, j = 0; i < _types.length; i++)
          {
            var insert = j == ol || left && ot[j] > et;
            _types[i] = insert ? et : ot[j];
            j = j + (insert ? 0 : 1);
            left = insert ? left : false;
          }
      }
    _outer = outer;
    _effectType = et;
    _effectValue = ev;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Compare two Env instances that may be null.
   */
  static int compare(Env a, Env b)
  {
    return
      a == b    ?  0 :
      a == null ? -1 :
      b == null ? +1 : a.compareTo(b);
  }


  /**
   * Compare this env to another Env.
   */
  public int compareTo(Env other)
  {
    // NYI: The code to distinguish two environments is currently poor, we just
    // distinguish enviroments depending on the set types they set, so two
    // environments that set, e.g., io.out, to different effects will be treated
    // the same.  This must be improved in a way that gives more accuracy
    // without state explosion!
    var ta = _types;
    var oa = other._types;
    var res =
      ta.length < oa.length ? -1 :
      ta.length > oa.length ? +1 : 0;
    for (var i=0; res == 0 && i < ta.length; i++)
      {
        var tt = ta[i];
        var ot = oa[i];
        res =
          tt < ot ? -1 :
          tt > ot ? +1 : 0;
      }
    return res;
  }


  /**
   * Create human-readable string from this Env.
   */
  public String toString()
  {
    var sb = new StringBuilder();
    var sep = "";
    for (var et : _types)
      {
        sb.append(sep)
          .append(_dfa._fuir.clazzAsString(et))
          .append("->")
          .append(getEffect(et));
        sep = ", ";
      }
    return sb.toString();
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
   * Does this environment define the given effect (as non-default effect)?
   *
   * @param ecl clazz defining the effect type.
   *
   * @return true if ecl is defined.
   */
  boolean hasEffect(int ecl)
  {
    return _effectType == ecl || _outer != null && _outer.hasEffect(ecl);
  }


  /**
   * Replace effect of given type with a new value.
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
