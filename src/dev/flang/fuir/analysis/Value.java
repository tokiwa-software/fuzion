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

package dev.flang.fuir.analysis;

import dev.flang.util.ANY;


/**
 * Value represents an abstract value handled by the DFA.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Value extends ANY
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


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
    if      (a == b)                                                       { return 0;                    }
    else if (a == UNIT                    || b == UNIT                   ) { return a == UNIT  ? +1 : -1; }
    else if (a == TRUE                    || b == TRUE                   ) { return a == TRUE  ? +1 : -1; }
    else if (a == FALSE                   || b == FALSE                  ) { return a == FALSE ? +1 : -1; }
    else if (a instanceof Instance     ai && b instanceof Instance     bi) { return ai.compareTo(bi);     }
    else if (a instanceof NumericValue an && b instanceof NumericValue bn) { return an.compareTo(bn);     }
    else
      {
        System.err.println("Value.compareTo requires support for "+a.getClass()+" and "+b.getClass());
        return /* NYI: HACK! Works often, not always: */ (a.hashCode() & 0x7fffFFF) - (b.hashCode() & 0x7fffFFFF);
      }
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
    System.out.println("*** error: Value.readField called on class " + this + " (" + getClass() + "), expected " + Instance.class);
    return UNIT;
  }


  /**
   * Create the union of the values 'this' and 'v'.
   */
  public Value join(Value v)
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
    else if (this.isBool() && v.isBool())
      { // booleans that are not equal:
        return BOOL;
      }
    else
      {
        System.err.println("NYI: Value.join: "+this+" and "+v);
        return this;
      }
  }


}

/* end of file */
