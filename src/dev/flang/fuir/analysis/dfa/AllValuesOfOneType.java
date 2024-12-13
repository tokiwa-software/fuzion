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
 * Source of class AllValuesOfOneType
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;


import dev.flang.util.IntMap;
import dev.flang.util.List;


/**
 * AllValuesOfOneType represents all values used for a specific type
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class AllValuesOfOneType extends Value
{


  /*-----------------------------  classes  -----------------------------*/


  /**
   * Helper class to collet the values when creating a AllValuesOfOneType.
   *
   * This handles two cases: Usually, the values in the set will just be joined,
   * ordered by their _id fields.
   *
   * However, if the values are TaggedValues, then values with the same tag will
   * be merged in new TaggedValue instances with their original values merged.
   */
  class Collect
  {
    /**
     * the DFA instance
     */
    DFA _dfa;


    /**
     * In case the values are TaggedValue instances, this maps the tag values to
     * the original values.  Allocated on demand.
     */
    List<Value> _forTags;


    /**
     * In case the values are TaggedValue instances, this is their clazz.
     */
    int _taggedClazz = -1;


    /**
     * Consrtructor
     */
    Collect(DFA dfa)
    {
      _dfa = dfa;
    }

    /**
     * Convert the collected values to a components array to be used in this
     * ValueSet.
     */
    Value[] asComponents()
    {
      if (CHECKS) check
        ((_forTags == null) != (_components == null));

      var comp = _components;
      if (comp == null)
        {
          comp = new IntMap<Value>();
          for (var i = 0; _forTags != null && i < _forTags.size(); i++)
            {
              var v = _forTags.get(i);
              if (v != null)
                {
                  var tv = _dfa.newTaggedValue(_taggedClazz, v, i);
                  comp.put(tv._id, tv);
                }
            }
        }
      var componentsArray = new Value[comp.size()];
      var i = 0;
      for (var c : comp.keySet())
        {
          componentsArray[i++] = comp.get(c);
        }
      return componentsArray;
    }

  }


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * Map for value ids to values in this set. This is unused in case the
   * values are TaggedValue instances.  Allocated on demand.
   */
  IntMap<Value> _components = new IntMap<>();


  /**
   * The values as a List.
   */
  List<Value> _componentsList = new List<>();

  int _iterating = 0;

  final DFA _dfa;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create AllValuesOfOneType {v1, v2}
   *
   * @param dfa the dfa instance
   *
   * @param cl the clazz of the resulting value. This is usually the same as the
   * clazz of `this` or `v`, unless we are joining `ref` type values.
   */
  public AllValuesOfOneType(DFA dfa, int cl)
  {
    super(cl);
    _dfa = dfa;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Add given value, or --if it is a ValueSet-- all of its components.
   */
  void add(Value v)
  {
    v.forAll(this::add0);
  }


  /**
   * Add given value that must not be a ValueSet itself
   */
  private void add0(Value v)
  {
    if (PRECONDITIONS) require
      (!(v instanceof ValueSet),
       !(v instanceof AllValuesOfOneType));

      if (v instanceof TaggedValue tv)
        {
          System.out.println("tagged value for "+_dfa._fuir.clazzAsString(_clazz));
          check(false);
          /*
          if (_components != null)
            {
              System.out.println("ADDING TAGGED "+tv+" to "+_dfa._fuir.clazzAsString(_clazz));
              for (var c : _componentsArray)
                {
                  System.out.println("  has "+c);
                }
            }
          if (_forTags == null)
            {
              _forTags = new List<>();
              _taggedClazz = tv._clazz;
            }

          if (CHECKS) check
            (_taggedClazz == tv._clazz);

          var oo = _forTags.getIfExists(tv._tag);
          var no = tv._original;
          var o = oo == null ? no : _dfa.newValueSet(oo, no, _dfa._fuir.clazzChoice(v._clazz, tv._tag));
          _forTags.force(tv._tag, o);
          */
        }
      else if (_components.get(v._id) == null)
        {
          _components.put(v._id, v);
          if (_iterating > 0)
            {
              var cl = _componentsList;
              _componentsList = new List<>();
              _componentsList.addAll(cl);
            }
          _componentsList.add(v);
        }
    }




  /**
   * Compare this to another AllValuesOfOneType.
   *
   * @param other the other AllValuesOfOneType
   *
   * @return -1, 0, or +1 depending on whether {@code this < other}, {@code this == other} or
   * {@code this > other} by some order.
   */
  public int compareTo(AllValuesOfOneType other)
  {
    return Integer.compare(_clazz, other._clazz);
  }


  /**
   * Is this AllValuesOfOneType a superset of other?
   */
  @Override
  boolean contains(Value other)
  {
    return this == other || _components.get(other._id) != null;
  }


  /**
   * Compare this to another AllValuesOfOneType, both sets containing effect instances
   * that are used in Env[ironmnents].
   *
   * @param other the other AllValuesOfOneType
   *
   * @return -1, 0, or +1 depending on whether {@code this < other}, {@code this == other} or
   * {@code this > other} by some order.
   */
  public int envCompareTo(AllValuesOfOneType other)
  {
    return compareTo(other);
  }


  /**
   * Create human-readable string from this value.
   */
  public String toString()
  {
    var sb = new StringBuilder();
    forAll(x -> sb.append(sb.isEmpty() ? "ALL{" : ",").append(x));
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
    _iterating++;
    for (var v : _componentsList)
      {
        v.forAll(c);
      }
    _iterating--;
  }


  /**
   * Box this value. This works both for Instances as well as for value types
   * such as i32, bool, etc.
   */
  Value box(DFA dfa, int vc, int rc, Context context)
  {
    Value result = null;
    // NYI: performance in O(_components.size()²)
    _iterating++;
    for (var v : _componentsList)
      {
        var u = v.box(dfa, vc, rc, context);
        result = result == null ? u : dfa.newValueSet(result, u, rc);
      }
    _iterating--;
    return result;
  }


  /**
   * Unbox this value.
   */
  Value unbox(DFA dfa, int vc)
  {
    Value result = null;
    // NYI: performance in O(_components.size()²)
    _iterating++;
    for (var v : _componentsList)
      {
        var u = v.unbox(dfa, vc);
        result = result == null ? u : dfa.newValueSet(result, u, vc);
      }
    _iterating--;
    return result;
  }


}

/* end of file */
