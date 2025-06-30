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

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.SourcePosition;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Env represents the set of effects installed in a given environment
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Env extends ANY implements Comparable<Env>
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  /**
   * String used in human readable output for an empty environment.
   */
  static final String EMPTY_ENV = "--empty--";


  /*----------------------------  variables  ----------------------------*/


  /**
   * Unique id to identify this Environment.
   */
  int _id = -1;


  /**
   * The DFA instance we are working with.
   */
  DFA _dfa;


  /**
   * Sorted array of types that are present in this environment. This is
   * currently used to uniquely identify Env instance, i.e., environments
   * that define the same effect types are joined into one environment.
   */
  int[] _types;


  /**
   * Initial values for the effect instances in this environment.
   */
  Value[] _initialEffectValues;


  /**
   * The surrounding environment, null if none.
   */
  Env _outer;


  /**
   * The type of the effect stored in this environment
   */
  int _effectType;


  /**
   * Is this effect ever aborted?
   */
  boolean _isAborted;


  /**
   * The initial value of the effect.  The initial values is part of the
   * identity of this effect, i.e., compareTo will take this value into account.
   */
  final Value _initialEffectValue;


  /**
   * The actual values of the effect.  This will include all the values added
   * via calls to {@code effect.replace}.
   */
  private Value _actualEffectValues;


  /*---------------------------  constructors  ---------------------------*/


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

    if (outer == null)
      {
        _types = new int[] { et };
        _initialEffectValues = new Value[] { ev };
      }
    else if (outer.hasEffect(et))
      {
        _types = outer._types;
        _initialEffectValues = new Value[_types.length];
        for (int i = 0; i < _types.length; i++)
          {
            _initialEffectValues[i] = _types[i] == et ? ev : outer._initialEffectValues[i];
          }
      }
    else
      {
        var ot = outer._types;
        var oi = outer._initialEffectValues;
        var ol = ot.length;
        _types = new int[ol + 1];
        _initialEffectValues = new Value[ol + 1];
        var left = true;
        for (int i = 0, j = 0; i < _types.length; i++)
          {
            var insert = j == ol || left && ot[j] > et;
            _types              [i] = insert ? et : ot[j];
            _initialEffectValues[i] = insert ? ev : oi[j];
            j = j + (insert ? 0 : 1);
            left = !insert && left;
          }
      }
    _outer = outer;
    _effectType = et;
    _initialEffectValue = ev;
    _actualEffectValues = ev;
  }


  /*-------------------------  static methods  --------------------------*/


  /**
   * Create a String for user output of the given environment
   *
   + @param env an environment or null.
   *
   * @return String for output in error messages etc.
   */
  public static String envAsString(Env env)
  {
    return env != null ? env.toStringShort()
                       : EMPTY_ENV;
  }


  /**
   * Compare two environoments by looking only at those effect values of effects
   * in the given set of effect types
   *
   * NOTE: iterating the elements of `which` must be deterministic!
   *
   * @param which set of clazz ids of effect types to compare
   *
   * @param a first environemnt
   *
   * @param b second environment
   *
   * @return -1/0/+1 defining a total order while ingoring effects not in
   * `which`.
   */
  static int compare(Set<Integer> which, Env a, Env b)
  {
    var res = 0;
    if (a != b && which != null)
      {
        for (var e : which)
          {
            var av = a != null ? a.get(e) : null;
            var bv = b != null ? b.get(e) : null;
            if (res == 0 && av != null && bv != null)
              {
                res = Value.envCompare(av, bv);
              }
            else if (av != bv && av == null)
              {
                res = -1;
              }
            else if (av != bv && bv == null)
              {
                res = +1;
              }

          }
      }
    return res;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Restrict this environment to an environment containing only the effects
   * that are required by given CallGroup.
   *
   * @param cg a CallGroup
   *
   * @return this Env stripped by any effects not required by cg.
   */
  Env filterCallGroup(CallGroup cg)
  {
    var o = _outer;
    var res = o == null ? null : o.filterCallGroup(cg);
    var ce = _dfa._effectsRequiredByClazz.get(cg._cc);
    if (ce != null && ce.contains(_effectType) ||
        cg._usedEffects.contains(_effectType) /* NYI: redundant with previous line? */ )
      {
        if (o != _outer)
          {
            res = _dfa.newEnv(res, _effectType, _actualEffectValues);
          }
        else
          {
            res = this;
          }
      }
    return res;
  }


  Env filterPos(SourcePosition pos, SourcePosition pos_new) // NYI: needed?
  {
    var res = _outer == null ? null : _outer.filterPos(pos, pos_new);
    var cd =_dfa._fuir.clazzCode(_effectType);
    var p = cd == FUIR.NO_SITE || !_dfa._fuir.withinCode(cd)
      ? _dfa._fuir.clazzDeclarationPos(_effectType)
      : _dfa._fuir.sitePos(cd);
    var p_new = _dfa.effectTypePosition(_effectType);
    var cmp2 = p_new.compareTo(pos_new) != 0;
    if (cmp2)
      {
        res = _dfa.newEnv(res, _effectType, _actualEffectValues);
      }
    return res;
  }
  Env filterPos(SourcePosition pos) // NYI: needed?
  {
    var res = _outer == null ? null : _outer.filterPos(pos);
    var p = _dfa.effectTypePosition(_effectType);
    var cmp = p.compareTo(pos) != 0;
    if (cmp)
      {
        res = _dfa.newEnv(res, _effectType, _actualEffectValues);
      }
    return res;
  }
  Env filterX(Set<Integer> which) // NYI: needed?
  {
    var res = _outer == null ? null : _outer.filterX(which);
    if (which.contains(_effectType))
      {
        res = _dfa.newEnv(res, _effectType, _actualEffectValues);
      }
    return res;
  }

  /**
   * Check the environment if it contains an effect of clazz `cl` instantiated at
   * `site`.
   *
   * @param cl a clazz
   *
   * @param site a site that contains a constructor call to `cl`
   *
   * @return in case the context contains an environment with an instance of
   * `cl` created at `site` instated, then return that existing instance.
   * Return null otherwise.
   */
  Instance find(int cl, int site)
  {
    if (cl == _effectType)
      {
        if (_actualEffectValues instanceof Instance ai && ai._site == site)
          {
            return ai;
          }
        else if (_actualEffectValues instanceof ValueSet avs)
          {
            for (var av : avs._componentsArray)
              {
                if (av instanceof Instance ai && ai._site == site)
                  {
                    return ai;
                  }
              }
          }
      }
    return null;
  }


  Value get(int ecl)
  {
    var i = Arrays.binarySearch(_types, ecl);
    return i >= 0 ? _initialEffectValues[i] : null;
  }


  /**
   * Compare two Env instances that may be null.
   */
  static int compare(Env a, Env b)
  {
    return
      a == null ? -1 :
      b == null ? +1 : Integer.compare(a._id, b._id);
  }


  /**
   * Compare this env to another Env.
   */
  public int compareTo(Env other)
  {
    // The _types are ordered
    var ta = this ._types;
    var oa = other._types;
    var tv = this ._initialEffectValues;
    var ov = other._initialEffectValues;
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
    for (var i=0; res == 0 && i < ta.length; i++)
      {
        var ti = tv[i];
        var oi = ov[i];
        res = Value.envCompare(ti, oi);
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
          .append(getActualEffectValues(et));
        sep = ", ";
      }
    return sb.toString();
  }


  /**
   * Create human-readable string from this Env.
   */
  public String toStringShort()
  {
    var sb = new StringBuilder();
    var sep = "";
    for (var et : _types)
      {
        sb.append(sep)
          .append(_dfa._fuir.clazzAsStringHuman(et));
        sep = ", ";
      }
    return sb.toString();
  }


  /**
   * Get all actual effect values of given type in this call's environment or
   * the default if none found.
   *
   * @param ecl clazz defining the effect type.
   *
   * @return null in case no effect of type ecl was found, not even in the set
   * of default effects.
   */
  Value getActualEffectValues(int ecl)
  {
    if (_dfa._real)
      return
        _effectType == ecl  ? _actualEffectValues :
        _outer      != null ? _outer.getActualEffectValues(ecl)
                            : _dfa._defaultEffects.get(ecl);
    else
      {
        var e1 = _dfa._preEffectValues.get(ecl);
        var e2 = _dfa._defaultEffects.get(ecl);
        return
          e1 == null ? e2 :
          e2 == null ? e1 : e1.join(_dfa,e2,ecl);
      }

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
        var oe = _actualEffectValues;
        var ne = e.join(_dfa, oe, ecl);
        if (Value.compare(oe, ne) != 0)
          {
            _actualEffectValues = ne;
            _dfa.wasChanged(() -> "effect.replace called: "+_dfa._fuir.clazzAsString(ecl));
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

  TreeSet<Env> _propagateAbort = new TreeSet<>(); // NYI: remove?


  void propagateAbort(Env e) // NYI: remove?
  {
    if (e != this)
      {
        _propagateAbort.add(e);
      }
  }


  /**
   * Is the effect just installed here ever aborted?
   *
   * @param ecl redundant with _effectType.  This is used only to check that this
   * is used only by intrinsic code for {@code effect.instate0} on the newly created
   * environment for the instated effect type.
   */
  boolean isAborted(int ecl)
  {
    boolean result = false;
    if (_effectType == ecl)
      {
        result = _isAborted;
      }
    else if (_outer != null)
      {
        result = _outer.isAborted(ecl);
      }
    else
      {
        check(false);
      }
    return result;
  }


  /**
   * Mark the environment that instates effect ecl as aborted.
   */
  void aborted(int ecl)
  {
    if (_effectType == ecl)
      {
        if (!_isAborted)
          {
            _isAborted = true;
            _dfa.wasChanged(() -> "effect.abort0 called: "+_dfa._fuir.clazzAsString(ecl));
          }
      }
    else if (_outer != null)
      {
        _outer.aborted(ecl);
      }
    else
      {
        throw new Error("DFA: Aborted effect `" + _dfa._fuir.clazzAsString(ecl) + "` not found in current environment");
      }
  }


}

/* end of file */
