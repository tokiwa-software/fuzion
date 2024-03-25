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
 * Source of class Instance
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;

import java.util.TreeMap;


/**
 * Instance represents an abstract instance of a feature handled by the DFA
 * Analysis. An Abstract instance may consist of abstract values as well as
 * context information, taint information, etc.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Instance extends Value implements Comparable<Instance>
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The DFA instance we are working with.
   */
  DFA _dfa;


  /**
   * Map from fields to the values that have been assigned to the fields.
   */
  private final TreeMap<Integer, Value> _fields;


  /**
   * For debugging: Reason that causes this instance to be part of the analysis.
   */
  Context _context;


  /**
   * Is this instance the result of the IR command 'Box'.  If so, there is a
   * terrible hack to find the field values.
   */
  final boolean _isBoxed;


  /**
   * Site of the call that created this instance, -1 if the call site is not
   * known, i.e., the call is coming from intrinsic call or the main entry
   * point.
   *
   * Instances created at different sites will be considered as different
   * instances.
   */
  final int _site;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create Instance of given clazz
   *
   * @param dfa the DFA instance we are analyzing with
   *
   * @param clazz the clazz this is an instance of.
   *
   * @param context for debugging: Reason that causes this instance to be part
   * of the analysis.
   */
  public Instance(DFA dfa, int clazz, int site, Context context)
  {
    super(clazz);

    if (PRECONDITIONS) require
      (!dfa._fuir.clazzIsRef(clazz));

    _dfa = dfa;
    _site = site;
    _context = context;
    _fields = new TreeMap<>();
    _isBoxed = false;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Get the environment this instance was created in, or null if none.
   *
   * This environment is taken into account when comparing instances.
   */
  Env env()
  {
    return _context instanceof Call ca1 ? ca1._env : null;
  }


  /**
   * Compare this to another instance.
   */
  public int compareTo(Instance other)
  {
    var i1 = this;
    var i2 = other;
    var c1 = i1._clazz;
    var c2 = i2._clazz;
    var s1 = i1._site;
    var s2 = i2._site;
    var e1 = i1.env();
    var e2 = i2.env();
    return
      c1 < c2    ? -1 :
      c1 > c2    ? +1 :
      s1 < s2    ? -1 :
      s1 > s2    ? +1 :
      e1 == e2   ?  0 :
      e1 == null ? -1 :
      e2 == null ? +1
                 : e1.compareTo(e2);
  }

  /**
   * Compare this to another instance, used to compare effect instances in
   * Env[ironmnents].  The main different to `compareTo` is that the effect
   * environment is ignored since that would lead to an explosion of
   * Environments.
   */
  public int envCompareTo(Instance other)
  {
    var i1 = this;
    var i2 = other;
    var c1 = i1._clazz;
    var c2 = i2._clazz;
    var s1 = i1._site;
    var s2 = i2._site;
    return
      c1 < c2    ? -1 :
      c1 > c2    ? +1 :
      s1 < s2    ? -1 :
      s1 > s2    ? +1
                 :  0;
  }


  /**
   * Add v to the set of values of given field within this instance.
   */
  public void setField(DFA dfa, int field, Value v)
  {
    if (PRECONDITIONS) require
      (v != null);

    var oldv = _fields.get(field);
    if (oldv != null)
      {
        v = oldv.join(v);
      }
    if (oldv == null || Value.COMPARATOR.compare(oldv, v) != 0)
      {
        var fv = v;
        _dfa.wasChanged(() -> "setField: new values " + fv + " (was " + oldv + ") for " + this);
      }
    dfa._writtenFields.add(field);
    _fields.put(field, v);
  }


  /**
   * Get set of values of given field within this instance.
   */
  Val readFieldFromInstance(DFA dfa, int field, int site, Context why)
  {
    if (PRECONDITIONS) require
      (_clazz == dfa._fuir.clazzAsValue(dfa._fuir.clazzOuterClazz(field)));

    dfa._readFields.add(field);
    var v = _fields.get(field);
    Val res = v;
    if (v == null)
      {
        if (dfa._reportResults)
          {
            DfaErrors.readingUninitializedField(dfa._fuir.siteAsPos(site),
                                                dfa._fuir.clazzAsString(field),
                                                dfa._fuir.clazzAsString(_clazz) + (_isBoxed ? " Boxed!" : ""),
                                                why);
          }
      }
    else if (!dfa._fuir.clazzIsRef(dfa._fuir.clazzResultClazz(field)))
      {
        res = new EmbeddedValue(this, v);
      }

    return res;
  }


  /**
   * Create the union of the values 'this' and 'v'. This is called by join()
   * after common cases (same instance, UNDEFINED) have been handled.
   */
  public Value joinInstances(Value v)
  {
    return new ValueSet(this, v);
  }


  /**
   * Create human-readable string from this instance.
   */
  public String toString()
  {
    return _dfa._fuir.clazzAsString(_clazz) + "@" + _dfa._fuir.siteAsPos(_site);
  }

}

/* end of file */
