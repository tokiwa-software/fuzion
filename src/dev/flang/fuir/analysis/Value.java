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

package dev.flang.fuir.analysis;

import java.util.Comparator;

import java.util.function.Consumer;

import dev.flang.util.ANY;


/**
 * Value represents an abstract value handled by the DFA.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Value extends ANY
{


  /*-----------------------------  classes  -----------------------------*/


  static interface ValueConsumer extends Consumer<Value>
  {
  }


  /*----------------------------  constants  ----------------------------*/


  /**
   * Comparator instance to compare two Values of arbitrary tyes.
   */
  static Comparator COMPARATOR = new Comparator<Value>() {
      /**
       * compare two values.
       */
      public int compare(Value a, Value b)
      {
        if      (a == b)                                                       { return 0;                    }
        else if (a == UNIT                    || b == UNIT                   ) { return a == UNIT  ? +1 : -1; }
        else if (a == TRUE                    || b == TRUE                   ) { return a == TRUE  ? +1 : -1; }
        else if (a == FALSE                   || b == FALSE                  ) { return a == FALSE ? +1 : -1; }
        else if (a instanceof Instance     ai && b instanceof Instance     bi) { return ai.compareTo(bi);     }
        else if (a instanceof NumericValue an && b instanceof NumericValue bn) { return an.compareTo(bn);     }
        else if (a instanceof TaggedValue  at && b instanceof TaggedValue  bt) { return at.compareTo(bt);     }
        else if (a instanceof SysArray     aa && b instanceof SysArray     ba) { return aa.compareTo(ba);     }
        else if (a instanceof ValueSet     as && b instanceof ValueSet     bs) { return as.compareTo(bs);     }
        else if (a instanceof Instance    ) { return +1; } else if (b instanceof Instance       ) { return -1; }
        else if (a instanceof NumericValue) { return +1; } else if (b instanceof NumericValue   ) { return -1; }
        else if (a instanceof TaggedValue ) { return +1; } else if (b instanceof TaggedValue    ) { return -1; }
        else if (a instanceof SysArray    ) { return +1; } else if (b instanceof SysArray       ) { return -1; }
        else if (a instanceof ValueSet    ) { return +1; } else if (b instanceof ValueSet       ) { return -1; }
        else
          {
            System.err.println("Value.compareTo requires support for "+a.getClass()+" and "+b.getClass());
            return 0;
          }
      }
    };



  /**
   * The unit value 'unit', '{}'
   */
  static Value UNIT = new Value()
    {
      public String toString()
      {
        return "UNIT";
      }
    };


  /**
   * The true value of type 'bool'
   */
  static Value TRUE = new Value()
    {
      public String toString()
      {
        return "true";
      }
      boolean isBool()
      {
        return true;
      }
    };


  /**
   * The false value of type 'bool'
   */
  static Value FALSE = new Value()
    {
      public String toString()
      {
        return "false";
      }
      boolean isBool()
      {
        return true;
      }
    };


  /**
   * Any value of type 'bool'
   */
  static Value BOOL = new Value()
    {
      public String toString()
      {
        return "bool";
      }
      boolean isBool()
      {
        return true;
      }
    };



  /**
   * undefined value, used for not initialized fields.
   */
  static Value UNDEFINED = new Value()
    {
      public String toString()
      {
        return "UNDEFINED";
      }
    };


  /*----------------------------  variables  ----------------------------*/


  /**
   * Cached result of a call to box().
   */
  Value _boxed;


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create Value
   */
  public Value()
  {
  }


  /*--------------------------  static methods  -------------------------*/


  /**
   * compare two values.
   */
  public static int compare(Value a, Value b)
  {
    return COMPARATOR.compare(a,b);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * is this a boolean value, TRUE, FALSE, or BOOL?
   */
  boolean isBool()
  {
    return false;
  }


  /**
   * Get the address of a value.
   */
  public Value adrOf()
  {
    throw new Error("adrOf");
  }


  /**
   * Add v to the set of values of given field within this instance.
   */
  public void setField(int field, Value v)
  {
    throw new Error("Value.setField called on class " + this + " (" + getClass() + "), expected " + Instance.class);
  }


  /**
   * Get set of values of given field within this value.  This works for unit
   * type results even if this is not an instance (but a unit type itself).
   */
  public Value readField(DFA dfa, int target, int field)
  {
    var rt = dfa._fuir.clazzResultClazz(field);
    return dfa._fuir.clazzIsUnitType(rt)
      ? Value.UNIT
      : readFieldFromInstance(dfa, target, field);
  }


  /**
   * Get set of values of given field within this instance.
   */
  Value readFieldFromInstance(DFA dfa, int target, int field)
  {
    System.out.println("*** error: Value.readField '"+dfa._fuir.clazzAsString(field)+"' called on class " + this + " (" + getClass() + "), expected " + Instance.class);
    return UNIT;
  }


  /**
   * Create the union of the values 'this' and 'v'.
   */
  public Value join(Value v)
  {
    if (this == v || compare(this, v) == 0)
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
    else if (this.isBool() && (v   .isBool() || v    instanceof TaggedValue) ||
             v   .isBool() && (this.isBool() || this instanceof TaggedValue)    )
      { // booleans that are not equal:
        return BOOL;
      }
    else
      {
        return joinInstances(v);
      }
  }


  /**
   * Create the union of the values 'this' and 'v'. This is called by join()
   * after common cases (same instnace, UNDEFINED) have been handled.
   */
  public Value joinInstances(Value v)
  {
    System.err.println("NYI: Value.join: "+this+" and "+v);
    return this;
  }


  /**
   * Box this value. This works both for Instances as well as for value types
   * such as i32, bool, etc.
   */
  Value box(int vc, int rc)
  {
    if (_boxed == null)
      {
        _boxed = new BoxedValue(this, vc, rc);
      }
    return _boxed;
  }


  /**
   * Unbox this value.
   */
  Value unbox(int vc)
  {
    System.err.println("NYI: Unbox for "+getClass());
    return this;
  }


  /**
   * Wrapp this value into a tagged untion type.
   *
   * @param dfa the current analysis conext
   *
   * @param cl the current feature
   *
   * @tagNum the value of the tag. Unlike the backends, there is no optimization
   * made to try to not store the tagNum.
   */
  Value tag(DFA dfa, int cl, int tagNum)
  {
    return new TaggedValue(dfa, cl, this, tagNum);
  }


  /**
   * Perform c.accept on this and, if this is a set, o all values contained in
   * the set.
   *
   * @param c a consumer to apply to the values.
   */
  public void forAll(ValueConsumer c)
  {
    c.accept(this);
  }

}

/* end of file */
