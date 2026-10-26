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
 * Source of class CallGroup
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;


import dev.flang.ir.IR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;

import static dev.flang.ir.IR.NO_CLAZZ;
import static dev.flang.util.FuzionConstants.EFFECT_INSTATE_NAME;

import java.util.TreeSet;
import java.util.stream.Collectors;


/**
 * CallGroup represents all calls that differ only by their environment, i.e.,
 * by the effects installed when this call is made.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class CallGroup extends ANY implements Comparable<CallGroup>
{


  /*-------------------------  static methods  --------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The DFA instance we are working with.
   */
  final DFA _dfa;


  /**
   * The clazz this is calling.
   */
  final int _cc;


  /**
   * If available, _site gives the call site of this Call as used in the IR.
   * Calls with different call sites are analysed separately, even if the
   * context and environment of the call is the same.
   *
   * IR.NO_SITE if the call site is not known, i.e., the call is coming from
   * intrinsic call or the main entry point.
   */
  final int _site;


  /**
   * Target value of the call
   */
  Value _target;


  /**
   * Set of CallGroups this may be called from.
   */
  TreeSet<CallGroup> _from = new TreeSet<>();


  /**
   * Set of clazz ids for effects this CallGroup uses.
   */
  TreeSet<Integer> _usedEffects = new TreeSet<>();


  /**
   * The quick hash of this call group or -1 if none.
   */
  private final long _quickHash;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create CallGroup
   *
   * @param dfa the DFA instance we are analyzing with
   *
   * @param cc called clazz
   *
   * @param site the call site, -1 if unknown (from intrinsic or program entry
   * point)
   *
   * @param target is the target value of the call
   */
  public CallGroup(DFA dfa, int cc, int site, Value target)
  {
    if (PRECONDITIONS) require
      (Errors.any() ||
       dfa._fuir.clazzOuterClazz(cc) == target._clazz ||
       dfa._fuir.clazzOuterClazz(cc) == dfa._fuir.clazzUniverse() && target._clazz == NO_CLAZZ ||
       dfa._fuir.clazzIsUnitType(dfa._fuir.clazzOuterClazz(cc)) ||
       dfa._fuir.clazzIsRef(dfa._fuir.clazzOuterClazz(cc)) &&  dfa._fuir.clazzIsRef(target._clazz));

    _dfa = dfa;
    _cc = cc;
    _site = site;
    _target = target;
    _quickHash = dfa.callQuickHash(cc, site, target, null);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Compare this to another Call.
   */
  public int compareTo(CallGroup other)
  {
    return _quickHash != -1 ? Long.compare(_quickHash, other._quickHash) :
      _cc         != other._cc         ? Integer.compare(_cc        , other._cc        ) :
      _target._id != other._target._id ? Integer.compare(_target._id, other._target._id) :
      _dfa.siteSensitive(_cc)          ? Integer.compare(_site      , other._site      ) : 0;
  }


  /**
   * For debugging: Why did {@code compareTo(other)} return a value != 0?
   */
  String compareToWhy(CallGroup other)
  {
    return
      _cc                              != other._cc         ? "cc different"     :
      _target._id                      != other._target._id ? "target different" :
      _dfa.siteSensitive(_cc) && _site != other._site       ? "site different"   : null;
  }


  /**
   * For all effects {@code e} that this needs and that this may have, record that
   * {@code _cc} requires {@code e} in {@code _dfa._clazzesThatRequireEffect} and
   * {@code _dfa._effectsRequiredByClazz}.
   */
  void saveEffects()
  {
    if (PRECONDITIONS) require
      (!_dfa._real);

    for (var e : _usedEffects)
      {
        _dfa._effectsRequiredByClazz  .computeIfAbsent(_cc, k->new TreeSet<>()).add(e  );
      }
  }


  /**
   * Is this ca call to {@code effect.instate0} that instates effect with clazz id {@code ecl}?
   *
   * @param ecl clazz id for an effect
   *
   * @return true iff this is {@code effect.instate0} and this instates an effect of
   * type {@code ecl}.
   */
  private boolean instates(int ecl)
  {
    return
      _dfa._fuir.clazzKind(_cc) == IR.FeatureKind.Intrinsic &&
      _dfa._fuir.clazzOriginalName(_cc).equals(EFFECT_INSTATE_NAME) &&
      _dfa._fuir.effectTypeFromIntrinsic(_cc) == ecl;
  }


  /**
   * Record the fact that this CallGroup (and all its targets and callers)
   * uses the instated effect type {@code ecl}.
   *
   * @param ecl clazz id for an effect.
   */
  void usesEffect(int ecl)
  {
    if (!instates(ecl) && _usedEffects.add(ecl))
      {
        _target.forAll(v ->
                       {
                         if (v instanceof RefValue rv)
                           {
                             v = rv._original;
                           }
                       });

        _dfa.wasChanged(() -> "needs effect "+_dfa._fuir.clazzName(ecl)+" for "+this);
        for (var f : _from)
          {
            f.usesEffect(ecl);
          }
      }
  }


  /**
   * Record the fact that this CallGroup is called from {@code from}.  Propagate all
   * used effects to {@code from} and may have effects from {@code from}.
   *
   * @param from CallGroup of a caller to this.
   */
  void calledFrom(CallGroup from)
  {
    if (_from.add(from))
      {
        for (var ecl : _usedEffects)
          {
            from.usesEffect(ecl);
          }
      }
  }


  /**
   * Helper for toString() to show a set of clazzes given by their ids.
   */
  String clazzesAsString(java.util.Set<Integer> s)
  {
    return s == null ? "{}" :
      s.stream().map(_dfa._fuir::clazzName).collect(Collectors.joining(","));
  }


  /**
   * For debugging output: the used effects.
   */
  String usedEffectsAsString()
  {
    return clazzesAsString(_usedEffects);
  }


  /**
   * For debugging output: the required effects.
   */
  String requiredEffectsAsString()
  {
    return clazzesAsString(_dfa._effectsRequiredByClazz.get(_cc));
  }


  /**
   * String representation, for debugging.
   */
  @Override
  public String toString()
  {
    return "CALLGROUP to "+_dfa._fuir.clazzName(_cc)+" at "+_dfa._fuir.siteAsString(_site)+" effects: "+
      (_dfa._real
       ? usedEffectsAsString()
       : requiredEffectsAsString());
  }


}

/* end of file */
