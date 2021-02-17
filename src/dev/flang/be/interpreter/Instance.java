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
 * Tokiwa GmbH, Berlin
 *
 * Source of class Instance
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import dev.flang.ir.Clazz;
import dev.flang.ir.Clazzes;


/**
 * Instance <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class Instance extends Value
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Just for debugging: value used for uninitialzed non-ref data
   */
  static final int UNINITIALIZED_INT = -555555555;


  /**
   * Handy preallocated global instances to be used during execution:
   */
  public static final Instance universe = new Instance(Clazzes.universe.get());


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  private final Clazz clazz;


  /**
   *
   */
  public Value[] refs;

  /**
   *
   */
  public int[] nonrefs;


  /**
   * The string, if this is an instance of conststring. Only for convenience in
   * intrinsic features, should be removed.
   */
  public String string; // NYI: remove!


  /**
   * The corresponding Java object if this instance is JavaRef
   */
  public Object javaRef;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param clazz
   *
   * @param outer
   */
  public Instance(Clazz clazz)
  {
    if (PRECONDITIONS) require
      (clazz != null);

    this.clazz = clazz;
    int sz = clazz.size();
    this.refs = new Value[sz];
    this.nonrefs = new int[sz];
    for (int i = 0; i<sz; i++)
      {
        this.nonrefs[i] = UNINITIALIZED_INT;
      }
  }


  /**
   * Constructor for the data in an array
   *
   * @param l
   */
  public Instance(int l)
  {
    this.clazz = null;
    this.refs = new Value[l];
    this.nonrefs = new int[l];
    for (int i = 0; i<l; i++)
      {
        this.nonrefs[i] = UNINITIALIZED_INT;
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * clazz returns the clazz of a reference instance
   *
   * @return
   */
  public Clazz clazz()
  {
    Clazz result = clazz;
    if ((result != null) && !result.isRef() && !result.feature().isSingleton()) // NYI: ugly special handling of singleton
      {
        System.out.println("result.type is "+result._type+" "+result._type.isRef());
        System.out.println("result.feature is "+result.feature().qualifiedName()+(result.feature().isSingleton() ? " single" : " not single"));
        throw new Error("No clazz in a value instance: type "+result._type+" value "+this);
      }
    return result;
  }

  /**
   * For a value of type i32, return the value.
   *
   * @return the i32 value
   */
  public int i32Value()
  {
    if (PRECONDITIONS) require
      (clazz == Clazzes.i32    .getIfCreated() ||
       clazz == Clazzes.ref_i32.getIfCreated()   );

    return nonrefs[0];
  }


  /**
   * For a value of type i64, return the value.
   *
   * @return the i64 value
   */
  public long i64Value()
  {
    if (PRECONDITIONS) require
      (clazz == Clazzes.i64    .getIfCreated() ||
       clazz == Clazzes.ref_i64.getIfCreated()    );

    return
        nonrefs[0    ] & 0xFFFFffffL |
      ((nonrefs[0 + 1] & 0xFFFFffffL) << 32);
  }


  /**
   * For a value of type u32, return the value.
   *
   * @return the u32 value
   */
  public int u32Value()
  {
    if (PRECONDITIONS) require
      (clazz == Clazzes.u32    .getIfCreated() ||
       clazz == Clazzes.ref_u32.getIfCreated()    );

    return nonrefs[0];
  }


  /**
   * For a value of type u64, return the value.
   *
   * @return the u64 value
   */
  public long u64Value()
  {
    if (PRECONDITIONS) require
      (clazz == Clazzes.u64    .getIfCreated() ||
       clazz == Clazzes.ref_u64.getIfCreated()    );

    return
        nonrefs[0    ] & 0xFFFFffffL |
      ((nonrefs[0 + 1] & 0xFFFFffffL) << 32);
  }


  /**
   * For a value of type bool, return the value.
   *
   * @return the bool value
   */
  public boolean boolValue()
  {
    if (PRECONDITIONS) require
      (clazz == Clazzes.c_TRUE .getIfCreated() ||
       clazz == Clazzes.c_FALSE.getIfCreated()   );

    return clazz == Clazzes.c_TRUE.getIfCreated();
  }


  /**
   * Debugging only: Check that this value is valid as the current instance for
   * a feature with given static clazz.
   *
   * @param expected the static clazz of the feature this value is called on.
   *
   * @throws Error in case this does not match the expected clazz
   */
  public void checkStaticClazz(Clazz expected)
  {
    if (expected.isRef())
      {
        if (!expected.isAssignableFrom(clazz()))
          {
            throw new Error("Dynamic runtime clazz "+clazz()+" does not match static "+expected);
          }
      }
    else
      {
        if (expected != clazz)
          {
            throw new Error("Runtime clazz "+clazz+" does not equal static "+expected);
          }
      }
  }


  /**
   * Convert this value into an LValue with the given offset.
   *
   * @param c the clazz of the value, for debugging only
   *
   * @param off the offset of the value within this
   *
   * @return the LValue to rev
   */
  public LValue at(Clazz c, int off)
  {
    return new LValue(c, this, off);
  }


  /**
   * Store this value in a field
   *
   * @param slot the slot that addresses the field this should be stored in.
   *
   * @param size the size of the data to be stored
   */
  void storeNonRef(LValue slot, int size)
  {
    if (PRECONDITIONS)
      require(size == clazz.size());

    storeNonRef(slot, size, 0);
  }


  /**
   * Store this value in a field
   *
   * @param slot the slot that addresses the field this should be stored in.
   *
   * @param size the size of the data to be stored
   *
   * @param voffset the offset the value to be stored within this
   */
  void storeNonRef(LValue slot, int size, int voffset)
  {
    Instance cur    = slot.container;
    int      offset = slot.offset;

    for (int i=0; i < size; i++)
      {
        cur.refs   [offset + i] = refs   [voffset + i];
        cur.nonrefs[offset + i] = nonrefs[voffset + i];
      }
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "instance[" + clazz + "]" + this.hashCode();
  }


  /**
   * dump
   */
  public void dump()
  {
    System.out.println(toString());
    for (int i=0; i<nonrefs.length; i++)
      {
        System.out.println(" field["+i+"] ==\t int:"+nonrefs[i]+"\tref: "+refs[i]);
      }
    if (string != null)
      {
        System.out.println("  string == \""+string+"\"");
      }
    if (javaRef != null)
      {
        System.out.println("  javaRef == "+javaRef);
      }
  }

}

/* end of file */
