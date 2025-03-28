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
 * Source of class TaggedValue
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;


/**
 * TaggedValue represents a Value of a tagged choice type.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class TaggedValue extends Value implements Comparable<TaggedValue>
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The DFA instance we are working with.
   */
  DFA _dfa;


  /**
   * The original, un-tagged value.
   */
  final Value _original;


  /**
   * The value of the tag.
   */
  int _tag;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create Tagged Value
   *
   * @param dfa the DFA instance we are working with
   *
   * @param nc the new clazz of this value after a tagging.
   *
   * @param original the untagged value
   *
   * @param tag the tag value.  Unlike some C backends, the tag is never left
   * out during analysis.
   */
  public TaggedValue(DFA dfa, int nc, Value original, int tag)
  {
    super(nc);
    if (PRECONDITIONS) require
      (nc != original._clazz);

    _dfa = dfa;
    _original = original;
    _tag = tag;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Compare this to another TaggedValue.
   */
  public int compareTo(TaggedValue other)
  {
    return
      _clazz < other._clazz ? -1 :
      _clazz > other._clazz ? +1 :
      _tag   < other._tag   ? -1 :
      _tag   > other._tag   ? +1 : Value.compare(_original, other._original);
  }


  /**
   * Does this Value cover the values in other?
   */
  @Override
  boolean contains(Value other)
  {
    return other instanceof TaggedValue ot &&
      _clazz == ot._clazz &&
      _tag == ot._tag &&
      _original.contains(ot._original);
  }


  /**
   * Create the union of the values 'this' and 'v'. This is called by join()
   * after common cases (same instance, UNDEFINED) have been handled.
   *
   * @param dfa the current analysis context.
   *
   * @param v the value this value should be joined with.
   *
   * @param clazz the clazz of the resulting value. This is usually the same as
   * the clazz of {@code this} or {@code v}, unless we are joining {@code ref} type values.
   */
  @Override
  public Value joinInstances(DFA dfa, Value v, int clazz)
  {
    if (v instanceof TaggedValue tv && _tag == tv._tag)
      {
        if (CHECKS) check
          (tv._clazz == clazz);

        var oc = dfa._fuir.clazzChoice(tv._clazz, tv._tag);
        return _dfa.newTaggedValue(_clazz, _original.join(dfa, tv._original, oc), _tag);
      }
    else
      {
        if (v instanceof ValueSet vs)
          {
            for (var vv : vs._componentsArray)
              {
                if (vv instanceof TaggedValue tvv && _tag == tvv._tag)
                  {
                    if (tvv._original instanceof ValueSet tvvs && tvvs.contains(_original))
                      {
                        return v;
                      }
                    else
                      {
                        // NYI: OPTIMIZATION: Create new valueset with tvv replaced by tvv joined with this.
                      }
                  }
              }
          }
        return super.joinInstances(dfa, v, clazz);
      }
  }


  /**
   * Create human-readable string from this value.
   */
  public String toString()
  {
    return _dfa._fuir.clazzAsString(_clazz) + "[tag:" + _tag + ",val:" + _original + "]";
  }

}

/* end of file */
