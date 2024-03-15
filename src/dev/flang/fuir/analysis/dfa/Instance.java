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
import java.util.stream.Collectors;

import dev.flang.util.Errors;


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
  public Instance(DFA dfa, int clazz, Context context, int site)
  {
    super(clazz);

    if (PRECONDITIONS) require
      (!dfa._fuir.clazzIsRef(clazz));

    _dfa = dfa;
    _context = context;
    _fields = new TreeMap<>();
    _isBoxed = false;
    _site = site;
  }


  /*-----------------------------  methods  -----------------------------*/


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
    var e1 = i1._context instanceof Call ca1 ? ca1._env : null;
    var e2 = i2._context instanceof Call ca2 ? ca2._env : null;
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
  Val readFieldFromInstance(DFA dfa, int field)
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
            Errors.error("reading uninitialized field " + dfa._fuir.clazzAsString(field) + " from instance of " + dfa._fuir.clazzAsString(_clazz) +
                         (_isBoxed ? " Boxed!" : "") +
                         "\n" +
                         "fields available:\n  " + _fields.keySet().stream().map(x -> ""+x+":"+dfa._fuir.clazzAsString(x)).collect(Collectors.joining(",\n  ")));

            for (var f : _fields.keySet())
              {
                if (dfa._fuir.clazzAsString(f).equals(dfa._fuir.clazzAsString(field).replace("ref ","")))
                  {
                    say("NYI: HACK: Using value version instead: "+v);
                  }
              }
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
    return _dfa._fuir.clazzAsString(_clazz);
  }

}

/* end of file */
