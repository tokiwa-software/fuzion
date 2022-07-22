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

import java.util.TreeMap;


/**
 * ValueSet represents a set of reference Instance of BoxedValue values.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ValueSet extends Value
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * the values this consists of
   */
  TreeMap<Value, Value> _components;


  /**
   * The values as an array.
   */
  Value[] _componentsArray;


  /*---------------------------  consructors  ---------------------------*/


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

    _components = new TreeMap<>(Value.COMPARATOR);
    v1.forAll(x -> _components.put(x,x));
    v2.forAll(x -> _components.put(x,x));
    _componentsArray = _components.values().toArray(new Value[_components.size()]);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Compare this to another ValueSet.
   *
   * @param other the other ValueSet
   *
   * @return -1, 0, or +1 depending on wether this < other, this == other or
   * this > other by some order.
   */
  public int compareTo(ValueSet other)
  {
    var s1 = _components.size();
    var s2 = other._components.size();
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
    _components.values().forEach(c);
  }


  /**
   * Create the union of the values 'this' and 'v'. This is called by join()
   * after common cases (same instnace, UNDEFINED) have been handled.
   */
  public Value joinInstances(Value v)
  {
    return new ValueSet(this, v);
  }


  /**
   * Unbox this value.
   */
  Value unbox(int vc)
  {
    Value result = null;
    for (var v : _components.values())
      {
        var u = v.unbox(vc);
        result = result == null ? u : new ValueSet(result, u);
      }
    return result;
  }


}

/* end of file */
