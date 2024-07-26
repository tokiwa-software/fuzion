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
 * Source of class RefValue
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;


/**
 * RefValue represents a Value other than Instance that was boxed, i.e., turned
 * into a ref.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class RefValue extends Value
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  DFA _dfa;


  /**
   * The original, non-boxed value.
   */
  Value _original;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create Boxed Value
   *
   * NYI: There are two cases that we need to distinguish: A boxed clone of a
   * value instance (so we need to clone original) or a reference to a value
   * instance (so original is the same instance).
   *
   * @param original the unboxed value
   */
  public RefValue(DFA dfa, Value original, int vc, int rc)
  {
    super(rc);
    if (PRECONDITIONS) require
      (!dfa._fuir.clazzIsRef(original._clazz),
       original._clazz == vc,
       dfa._fuir.clazzIsRef(rc));

    _dfa = dfa;
    _original = original;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Compare this to another RefValue.
   */
  public int compareTo(RefValue other)
  {
    return Integer.compare(_id, other._id);
  }


  /**
   * Compare this to another RefValue, used to compare effect instances in
   * Env[ironmnents].
   */
  public int envCompareTo(RefValue other)
  {
    return
      _clazz < other._clazz ? -1 :
      _clazz > other._clazz ? +1 :
      Value.envCompare(_original, other._original);
  }


  /**
   * Add v to the set of values of given field within this instance.
   */
  public void setField(DFA dfa, int field, Value v)
  {
    if (PRECONDITIONS) require
      (v != null);

    _original.setField(dfa, field, v);
  }



  /**
   * Get set of values of given field within this instance.
   */
  Val readFieldFromInstance(DFA dfa, int field, int site, Context why)
  {
    return _original.readFieldFromInstance(dfa, field, site, why);
  }


  /**
   * Unbox this value.
   */
  Value unbox(DFA dfa, int vc)
  {
    return _original;
  }


  /**
   * Create human-readable string from this value.
   */
  public String toString()
  {
    return "boxed("+_dfa._fuir.clazzAsString(_clazz)+"):" + _original;
  }

}

/* end of file */
