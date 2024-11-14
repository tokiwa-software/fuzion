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
 * Source of class Value
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;

import static dev.flang.ir.IR.NO_SITE;

import dev.flang.fuir.FUIR;

import dev.flang.util.Errors;
import dev.flang.util.IntMap;

import java.util.Comparator;

import java.util.function.Consumer;
import java.util.function.Function;



/**
 * Value represents an abstract value handled by the DFA.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Value extends Val
{


  /*-----------------------------  classes  -----------------------------*/


  static interface ValueConsumer extends Consumer<Value>
  {
  }


  /**
   * Comparator instance to compare two Values of arbitrary types.
   */
  static class ValueComparator implements Comparator<Value> {
      /**
       * compare two values.
       */
      public int compare(Value a, Value b)
      {

        if      (a == b)                                                       { return 0;                    }
        else if (a == UNIT                    || b == UNIT                   ) { return a == UNIT  ? +1 : -1; }
        else if (a instanceof TaggedValue  at && b instanceof TaggedValue  bt) { return at.compareTo(bt);     }
        else if (a instanceof ValueSet     as && b instanceof ValueSet     bs) { return as.compareTo(bs);     }
        else if (a instanceof TaggedValue ) { return +1; } else if (b instanceof TaggedValue    ) { return -1; }
        else if (a instanceof ValueSet    ) { return +1; } else if (b instanceof ValueSet       ) { return -1; }
        else if (a._id >= 0 && b._id >= 0) { return Integer.compare(a._id, b._id); }
        else
          {
            throw new Error(getClass().toString()+"compareTo requires support for "+a.getClass()+" and "+b.getClass());
          }
      }
  }


  /**
   * Comparator instance to compare two Values of effect instances that are used in
   * Env[ironmnents].
   */
  static class ValueEnvComparator implements Comparator<Value> {
      /**
       * compare two values.
       */
      public int compare(Value a, Value b)
      {
        if      (a == b)                                                       { return 0;                    }
     // else if (a == UNIT                    || b == UNIT                   ) { return a == UNIT  ? +1 : -1; }
        else if (a instanceof Instance     ai && b instanceof Instance     bi) { return ai.envCompareTo(bi);     }
     // else if (a instanceof NumericValue an && b instanceof NumericValue bn) { return an.envCompareTo(bn);     }
        else if (a instanceof RefValue     ab && b instanceof RefValue     bb) { return ab.envCompareTo(bb);     }
     // else if (a instanceof TaggedValue  at && b instanceof TaggedValue  bt) { return at.envCompareTo(bt);     }
     // else if (a instanceof SysArray     aa && b instanceof SysArray     ba) { return aa.envCompareTo(ba);     }
        else if (a instanceof ValueSet     as && b instanceof ValueSet     bs) { return as.envCompareTo(bs);     }
        else if (a instanceof Instance    ) { return +1; } else if (b instanceof Instance       ) { return -1; }
     // else if (a instanceof NumericValue) { return +1; } else if (b instanceof NumericValue   ) { return -1; }
        else if (a instanceof RefValue    ) { return +1; } else if (b instanceof RefValue       ) { return -1; }
     // else if (a instanceof TaggedValue ) { return +1; } else if (b instanceof TaggedValue    ) { return -1; }
     // else if (a instanceof SysArray    ) { return +1; } else if (b instanceof SysArray       ) { return -1; }
        else if (a instanceof ValueSet    ) { return +1; } else if (b instanceof ValueSet       ) { return -1; }
        else
          {
            throw new Error(getClass().toString() + "envCompareTo requires support for " + a.getClass() + " and " + b.getClass());
          }
      }
  }


  /*----------------------------  constants  ----------------------------*/


  /**
   * Comparator instance to compare two Values of arbitrary types.
   */
  static Comparator<Value> COMPARATOR = new ValueComparator();


  /**
   * Comparator instance to compare two Values of effect instances that are used in
   * Env[ironmnents].
   */
  static Comparator<Value> ENV_COMPARATOR = new ValueEnvComparator();


  /**
   * The unit value 'unit', '{}'
   */
  static Value UNIT = new Value(-1)
    {
      /**
       * Add v to the set of values of given field within this instance.
       */
      public void setField(DFA dfa, int field, Value v)
      {
        if (dfa._fuir.clazzUniverse() == dfa._fuir.clazzOuterClazz(field))
          {
            dfa._universe.setField(dfa, field, v);
          }
        else
          {
            super.setField(dfa, field, v);
          }
      }


      /**
       * Get set of values of given field within this value.  This works for unit
       * type results even if this is not an instance (but a unit type itself).
       */
      public Val readField(DFA dfa, int field, int site, Context why)
      {
        if (dfa._fuir.clazzUniverse() == dfa._fuir.clazzOuterClazz(field))
          {
            return dfa._universe.readField(dfa, field, site, why);
          }
        else
          {
            return super.readField(dfa, field, site, why);
          }
      }


      public String toString()
      {
        return "UNIT";
      }
    };


  /**
   * undefined value, used for not initialized fields.
   */
  static Value UNDEFINED = new Value(-1)
    {
      public String toString()
      {
        return "UNDEFINED";
      }
    };


  /**
   * undefined value, used for not initialized fields.
   */
  static Value UNKNOWN_JAVA_REF = new Value(-1)
    {
      public String toString()
      {
        return "UNKNOWN_JAVA_REF";
      }
    };


  /*----------------------------  variables  ----------------------------*/


  /**
   * The clazz this is an instance of.
   */
  int _clazz;


  /**
   * Cached result of a call to box().
   */
  Value _boxed;


  /**
   * Unique id for this value, set by DFA.makeUnique(Value).
   */
  int _id = -1;


  /**
   * Uniquew id for this value when used as an effect instance in an environment.
   */
  int _envId = -1;


  /**
   * If this was embedded, this gives a map from the site to the embedded value.
   */
  IntMap<EmbeddedValue> _embeddedAt = null;


  /**
   * If a SysArray of this was created, this is the SysArray value.
   */
  SysArray _sysArrayOf = null;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create Value
   */
  public Value(int cl)
  {
    _clazz = cl;
  }


  /*--------------------------  static methods  -------------------------*/


  /**
   * compare two Values.
   */
  public static int compare(Value a, Value b)
  {
    return COMPARATOR.compare(a,b);
  }


  /**
   * compare two Values of effect instances that are used in Env[ironmnents].
   */
  public static int envCompare(Value a, Value b)
  {
    return ENV_COMPARATOR.compare(a,b);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Does this Value cover the values in other?
   */
  boolean contains(Value other)
  {
    return this == other;
  }


  /**
   * Add v to the set of values of given field within this instance.
   */
  public void setField(DFA dfa, int field, Value v)
  {
    var rt = dfa._fuir.clazzResultClazz(field);
    if (!dfa._fuir.clazzIsUnitType(rt) && !Errors.any())
      {
        throw new Error("Value.setField for '"+dfa._fuir.clazzAsString(field)+"' called on class " +
                        this + " (" + getClass() + "), expected " + Instance.class);
      }
  }


  /**
   * Get set of values of given field within this value.  This works for unit
   * type results even if this is not an instance (but a unit type itself).
   */
  public Val readField(DFA dfa, int field, int site, Context why)
  {
    var rt = dfa._fuir.clazzResultClazz(field);
    var res =
      dfa._fuir.clazzIsUnitType(rt)                                                                 ||
      // NYI: UNDER DEVELOPMENT: intrinsics create instances like
      // `fuzion.java.Array`. These intrinsics currently do not set the outer
      // refs correctly, so we handle them here for now by just assuming they
      // are unit type values:
      dfa._fuir.clazzIsOuterRef(field) && (rt == dfa._fuir.clazz(FUIR.SpecialClazzes.c_java  ) ||
                                           rt == dfa._fuir.clazz(FUIR.SpecialClazzes.c_fuzion)    )
      ? Value.UNIT
      : readFieldFromInstance(dfa, field, site, why);
    return res;
  }


  /**
   * Create a call to a field
   *
   * @param cc the inner value of the field that is called.
   */
  Val callField(DFA dfa, int cc, int site, Context why)
  {
    var resa = new Val[] { null };
    forAll(t ->
           {
             var r = t.readField(dfa, cc, site, why);
             if (resa[0] == null)
               {
                 resa[0] = r;
               }
             else
               {
                 resa[0] = resa[0].joinVal(dfa, r, dfa._fuir.clazzResultClazz(cc));
               }
           });
    return resa[0];
  }


  /**
   * Get set of values of given field within this instance.
   */
  Val readFieldFromInstance(DFA dfa, int field, int site, Context why)
  {
    var rt = dfa._fuir.clazzResultClazz(field);
    if (!dfa._fuir.clazzIsUnitType(rt) && !Errors.any())
      {
        throw new Error("Value.readField '"+dfa._fuir.clazzAsString(field)+"' called on class " + this + " (" + getClass() + "), expected " + Instance.class +
                        " field type " + dfa._fuir.clazzAsString(rt) + " in "+this);
      }
    return Value.UNIT;
  }


  /**
   * Create the union of the values 'this' and 'v'.
   *
   * @param dfa the current analysis context.
   *
   * @param v the value this value should be joined with.
   *
   * @param clazz the clazz of the resulting value. This is usually the same as
   * the clazz of `this` or `v`, unless we are joining `ref` type values.
   */
  public Value join(DFA dfa, Value v, int clazz)
  {
    if (this == v)
      {
        return this;
      }
    else if (this == UNDEFINED)
      {
        return v;
      }
    else if (v == UNDEFINED)
      {
        return this;
      }
    else
      {
        return joinInstances(dfa, v, clazz);
      }
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
   * the clazz of `this` or `v`, unless we are joining `ref` type values.
   */
  public Value joinInstances(DFA dfa, Value v, int clazz)
  {
    return dfa.newValueSet(this, v, clazz);
  }


  /**
   * Box this value. This works both for Instances as well as for value types
   * such as i32, bool, etc.
   */
  Value box(DFA dfa, int vc, int rc, Context context)
  {
    Value result;
    if (this == UNIT)
      {
        result = dfa.newInstance(rc, NO_SITE, context);
      }
    else
      {
        result = _boxed;
        if (result == null)
          {
            result = new RefValue(dfa, this, vc, rc);
            dfa.makeUnique(result);
            _boxed = result;
          }
      }
    return result;
  }


  /**
   * Unbox this value.
   */
  Value unbox(DFA dfa, int vc)
  {
    return this;
  }


  /**
   * Wrapp this value into a tagged union type.
   *
   * @param dfa the current analysis context
   *
   * @param cl the current feature
   *
   * @tagNum the value of the tag. Unlike the backends, there is no optimization
   * made to try to not store the tagNum.
   */
  Value tag(DFA dfa, int cl, int tagNum)
  {
    return dfa.newTaggedValue(cl, this, tagNum);
  }


  /**
   * Perform c.accept on this and, if this is a set, on all values contained in
   * the set.
   *
   * @param c a consumer to apply to the values.
   */
  public void forAll(ValueConsumer c)
  {
    c.accept(this);
  }


  /**
   * In case this value is wrapped in an instance that contains additional
   * information unrelated to the actual value (e.g. EmbeddedValue), get the
   * actual value.
   */
  Value value()
  {
    return this;
  }


  /**
   * apply f to the unwrapped value and re-wrap
   *
   * @param f function to apply to unwrapped value.
   */
  public Val rewrap(DFA dfa, Function<Value,Val> f)
  {
    return f.apply(this);
  }

}

/* end of file */
