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
 * Source of class ValueSet
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;

import dev.flang.util.IntMap;


/**
 * ValueSet represents a set of reference Instance of RefValue values.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ValueSet extends Value
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The values as an array.
   */
  Value[] _componentsArray;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create ValueSet {v1, v2}
   *
   * @param v1 some value
   *
   * @param v2 some value
   */
  public ValueSet(Value v1, Value v2)
  {
    super(-1);

    IntMap<Value> components = new IntMap<>();
    if (v1 instanceof ValueSet v1s)
      {
        for (var c : v1s._componentsArray)
          {
            components.put(c._id, c);
          }
      }
    else
      {
        components.put(v1._id, v1);
      }
    if (v2 instanceof ValueSet v2s)
      {
        for (var c : v2s._componentsArray)
          {
            components.put(c._id, c);
          }
      }
    else
      {
        components.put(v2._id, v2);
      }
    _componentsArray = new Value[components.size()];
    var i = 0;
    for (var c : components.keySet())
      {
        _componentsArray[i++] = components.get(c);
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Compare this to another ValueSet.
   *
   * @param other the other ValueSet
   *
   * @return -1, 0, or +1 depending on whether this < other, this == other or
   * this > other by some order.
   */
  public int compareTo(ValueSet other)
  {
    var s1 =       _componentsArray.length;
    var s2 = other._componentsArray.length;
    if (s1 == s2)
      {
        for (int i = 0; i < _componentsArray.length; i++)
          {
            var x1 = _componentsArray[i];
            var x2 = other._componentsArray[i];
            var res = Value.compare(x1, x2);
            if (res != 0)
              {
                return res;
              }
          }
        return 0;
      }
    else if (s1 < s2)
      {
        return -1;
      }
    else
      {
        return +1;
      }
  }

  /**
   * Is this ValueSet a superset of other?
   */
  boolean contains(Value other)
  {
    boolean result;
    if (other instanceof ValueSet os)
      {
        result = true;
        for (var oc : os._componentsArray)
          {
            result = result && contains(oc);
          }
      }
    else
      {
        result = false;
        for (var tc : _componentsArray)
          {
            result = result || tc == other;
          }
      }
    return result;
  }


  /**
   * Compare this to another ValueSet, both sets containing effect instances
   * that are used in Env[ironmnents].
   *
   * @param other the other ValueSet
   *
   * @return -1, 0, or +1 depending on whether this < other, this == other or
   * this > other by some order.
   */
  public int envCompareTo(ValueSet other)
  {
    var s1 =       _componentsArray.length;
    var s2 = other._componentsArray.length;
    if (s1 == s2)
      {
        for (int i = 0; i < _componentsArray.length; i++)
          {
            var x1 = _componentsArray[i];
            var x2 = other._componentsArray[i];
            var res = Value.envCompare(x1, x2);
            if (res != 0)
              {
                return res;
              }
          }
        return 0;
      }
    else if (s1 < s2)
      {
        return -1;
      }
    else
      {
        return +1;
      }
  }


  /**
   * Create human-readable string from this value.
   */
  public String toString()
  {
    var sb = new StringBuilder();
    forAll(x -> sb.append(sb.isEmpty() ? "{" : ",").append(x));
    sb.append("}");
    return sb.toString();
  }


  /**
   * Call c.accept on all values in this set.
   *
   * @param c a consumer to apply to the values.
   */
  public void forAll(ValueConsumer c)
  {
    for (var v : _componentsArray)
      {
        c.accept(v);
      }
  }


  /**
   * Box this value. This works both for Instances as well as for value types
   * such as i32, bool, etc.
   */
  Value box(DFA dfa, int vc, int rc, Context context)
  {
    Value result = null;
    // NYI: performance in O(_components.size()²)
    for (var v : _componentsArray)
      {
        var u = v.box(dfa, vc, rc, context);
        result = result == null ? u : dfa.newValueSet(result, u);
      }
    return result;
  }


  /**
   * Unbox this value.
   */
  Value unbox(DFA dfa, int vc)
  {
    Value result = null;
    // NYI: performance in O(_components.size()²)
    for (var v : _componentsArray)
      {
        var u = v.unbox(dfa, vc);
        result = result == null ? u : dfa.newValueSet(result, u);
      }
    return result;
  }


}

/* end of file */
