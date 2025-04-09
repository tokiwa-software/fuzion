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
import dev.flang.util.List;


/**
 * ValueSet represents a set of reference Instance of RefValue values.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ValueSet extends Value
{


  /*-----------------------------  classes  -----------------------------*/


  /**
   * Helper class to collet the values when creating a ValueSet.
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
     * Map for value ids to values in this set. This is unused in case the
     * values are not TaggedValue instances.  Allocated on demand.
     */
    IntMap<Value> _components;


    /**
     * In case the values are TaggedValue instances, this maps the tag values to
     * the original values.  Allocated on demand.
     */
    List<Value> _forTags;

    IntMap<RefValue> _forRefs;


    /**
     * In case the values are TaggedValue instances, this is their clazz.
     */
    int _taggedClazz = -1;


    /**
     * Constructor
     */
    Collect(DFA dfa)
    {
      _dfa = dfa;
    }


    /**
     * Add given value, or --if it is a ValueSet-- all of its components.
     */
    void add(Value v)
    {
      if (v instanceof ValueSet vs)
        {
          for (var c : vs._componentsArray)
            {
              add0(c);
            }
        }
      else
        {
          add0(v);
        }
    }


    /**
     * Add given value that must not be a ValueSet itself
     */
    void add0(Value v)
    {
      if (PRECONDITIONS) require
        (!(v instanceof ValueSet));

      if (DFA.NO_SET_OF_REFS && v instanceof RefValue rv)
        {
          if (_forRefs == null)
            {
              _forRefs = new IntMap<>();
            }
          var r = _forRefs.get(rv._clazz);
          if (r != null)
            {
              var orig1 = r._original;
              var orig2 = rv._original;
              var ov = _dfa.newValueSet(orig1, orig2, orig1._clazz);
              rv = (RefValue) ov.box0(_dfa, orig1._clazz, rv._clazz, Context._MAIN_ENTRY_POINT_ /* NYI: why? */);
            }
          _forRefs.put(rv._clazz, rv);
        }
      else if (v instanceof TaggedValue tv)
        {
          if (false && _componentsArray != null)
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
        }
      else
        {
          if (_forTags != null)
            {
              System.out.println("ADDING UN-TAGGED "+v+" to "+_dfa._fuir.clazzAsString(_clazz));
              for (var c : _forTags)
                {
                  if (c != null)
                    System.out.println("  has "+c);
                }
            }
          if (_components == null)
            {
              _components = new IntMap<>();
            }
          _components.put(v._id, v);
        }
      if (false) if (((_forTags == null) == (_components == null)))
        {
          System.out.println("Added "+v+" ("+_dfa._fuir.clazzAsString(v._clazz)+") to "+_dfa._fuir.clazzAsString(_clazz)+": "+this);
        }

      if (false) if (CHECKS) check
        ((_forTags == null) != (_components == null));
    }


    /**
     * Convert the collected values to a components array to be used in this
     * ValueSet.
     */
    Value[] asComponents()
    {
      if (false) if (CHECKS) check
        ((_forTags == null) != (_components == null));

      if (_forRefs != null)
        {
          if (_components == null)
            {
              _components = new IntMap<>();
            }
          for (var k : _forRefs.keySet())
            {
              var r = _forRefs.get(k);
              _components.put(r._id, r);
            }
        }
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
   *
   * @param cl the clazz of the resulting value. This is usually the same as the
   * clazz of {@code this} or {@code v}, unless we are joining {@code ref} type values.
   */
  public ValueSet(DFA dfa, Value v1, Value v2, int cl)
  {
    super(cl);

    var coll = new Collect(dfa);
    coll.add(v1);
    coll.add(v2);
    _componentsArray = coll.asComponents();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Compare this to another ValueSet.
   *
   * @param other the other ValueSet
   *
   * @return -1, 0, or +1 depending on whether this &lt; other, this == other or
   * this &gt; other by some order.
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
        return Integer.compare(_clazz, other._clazz);
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
  @Override
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
            result = result || tc.contains(other);
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
   * @return -1, 0, or +1 depending on whether this &lt; other, this == other or
   * this &gt; other by some order.
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
    int[] i = new int[] { 0 };
    forAll(x -> sb.append(sb.isEmpty() ? "{\n§" : ",\n§").append(i[0]++).append(" #").append(x._id).append(" ").append(x.toString().replace("§","  §")));
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
        v.forAll(c);
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
        result = result == null ? u : dfa.newValueSet(result, u, rc);
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
        result = result == null ? u : dfa.newValueSet(result, u, vc);
      }
    return result;
  }



  /**
   * Get set of values of given field within this instance.
   */
  Val readFieldFromInstance(DFA dfa, int field, int site, Context why)
  {
    Val result = null;
    var rt = dfa._fuir.clazzResultClazz(field);
    // NYI: performance in O(_components.size()²)
    for (var v : _componentsArray)
      {
        var u = v.readFieldFromInstance(dfa, field, site, why);
        result = result == null ? u : dfa.newValueSet(result.value(), u.value(), rt);
      }
    return result;
  }


}

/* end of file */
