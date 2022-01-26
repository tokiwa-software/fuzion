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
 * Source code of class ANY
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.util.HashMap;

/**
 * ANY implements static methods for pre- and post-conditions as in Eiffel.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Flags to globally enable / disable pre- and postconditions. Calls to
   * require/ensure should be put into an "if (PRECONDITIONS)" or "if
   * (POSTCONDITIONS)" to completely remove them by the JIT compiler if
   * disabled.
   */
  public static final boolean PRECONDITIONS  = System.getenv().getOrDefault("PRECONDITIONS" , "false").equals("true");
  public static final boolean POSTCONDITIONS = System.getenv().getOrDefault("POSTCONDITIONS", "false").equals("true");
  public static final boolean CHECKS = PRECONDITIONS | POSTCONDITIONS |
    System.getenv().getOrDefault("CHECKS", "false").equals("true");


  /*-----------------------------  methods  -----------------------------*/


  /**
   * require is a static method for pre-conditions as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void require(boolean cond1)
  {
    if (!cond1) throw new Error("require-condition1 failed!");
  }


  /**
   * require is a static method for pre-conditions as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void require(boolean cond1, boolean cond2)
  {
    if (!cond1) throw new Error("require-condition1 failed!");
    if (!cond2) throw new Error("require-condition2 failed!");
  }


  /**
   * require is a static method for pre-conditions as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @param cond3 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void require(boolean cond1, boolean cond2, boolean cond3)
  {
    if (!cond1) throw new Error("require-condition1 failed!");
    if (!cond2) throw new Error("require-condition2 failed!");
    if (!cond3) throw new Error("require-condition3 failed!");
  }


  /**
   * require is a static method for pre-conditions as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @param cond3 the condition that must hold
   *
   * @param cond4 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void require(boolean cond1, boolean cond2, boolean cond3, boolean cond4)
  {
    if (!cond1) throw new Error("require-condition1 failed!");
    if (!cond2) throw new Error("require-condition2 failed!");
    if (!cond3) throw new Error("require-condition3 failed!");
    if (!cond4) throw new Error("require-condition4 failed!");
  }


  /**
   * require is a static method for pre-conditions as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @param cond3 the condition that must hold
   *
   * @param cond4 the condition that must hold
   *
   * @param cond5 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void require(boolean cond1, boolean cond2, boolean cond3, boolean cond4, boolean cond5)
  {
    if (!cond1) throw new Error("require-condition1 failed!");
    if (!cond2) throw new Error("require-condition2 failed!");
    if (!cond3) throw new Error("require-condition3 failed!");
    if (!cond4) throw new Error("require-condition4 failed!");
    if (!cond5) throw new Error("require-condition5 failed!");
  }


  /**
   * require is a static method for pre-conditions as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @param cond3 the condition that must hold
   *
   * @param cond4 the condition that must hold
   *
   * @param cond5 the condition that must hold
   *
   * @param cond6 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void require(boolean cond1, boolean cond2, boolean cond3, boolean cond4, boolean cond5, boolean cond6)
  {
    if (!cond1) throw new Error("require-condition1 failed!");
    if (!cond2) throw new Error("require-condition2 failed!");
    if (!cond3) throw new Error("require-condition3 failed!");
    if (!cond4) throw new Error("require-condition4 failed!");
    if (!cond5) throw new Error("require-condition5 failed!");
    if (!cond6) throw new Error("require-condition6 failed!");
  }


  /**
   * require is a static method for pre-conditions as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @param cond3 the condition that must hold
   *
   * @param cond4 the condition that must hold
   *
   * @param cond5 the condition that must hold
   *
   * @param cond6 the condition that must hold
   *
   * @param cond7 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void require(boolean cond1, boolean cond2, boolean cond3, boolean cond4, boolean cond5, boolean cond6, boolean cond7)
  {
    if (!cond1) throw new Error("require-condition1 failed!");
    if (!cond2) throw new Error("require-condition2 failed!");
    if (!cond3) throw new Error("require-condition3 failed!");
    if (!cond4) throw new Error("require-condition4 failed!");
    if (!cond5) throw new Error("require-condition5 failed!");
    if (!cond6) throw new Error("require-condition6 failed!");
    if (!cond7) throw new Error("require-condition7 failed!");
  }


  /* ----------------------------------------------------------------------------- */


  /**
   * ensure is a static method for post-conditions as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void ensure(boolean cond1)
  {
    if (!cond1)
      throw new Error("ensure-condition failed!");
  }


  /**
   * ensure is a static method for post-conditions as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void ensure(boolean cond1, boolean cond2)
  {
    if (!cond1)
      throw new Error("ensure-condition1 failed!");
    if (!cond2)
      throw new Error("ensure-condition2 failed!");
  }


  /**
   * ensure is a static method for post-conditions as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @param cond3 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void ensure(boolean cond1, boolean cond2, boolean cond3)
  {
    if (!cond1)
      throw new Error("ensure-condition1 failed!");
    if (!cond2)
      throw new Error("ensure-condition2 failed!");
    if (!cond3)
      throw new Error("ensure-condition3 failed!");
  }


  /**
   * ensure is a static method for post-conditions as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @param cond3 the condition that must hold
   *
   * @param cond4 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void ensure(boolean cond1, boolean cond2, boolean cond3, boolean cond4)
  {
    if (!cond1)
      throw new Error("ensure-condition1 failed!");
    if (!cond2)
      throw new Error("ensure-condition2 failed!");
    if (!cond3)
      throw new Error("ensure-condition3 failed!");
    if (!cond4)
      throw new Error("ensure-condition4 failed!");
  }


  /**
   * ensure is a static method for post-conditions as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @param cond3 the condition that must hold
   *
   * @param cond4 the condition that must hold
   *
   * @param cond5 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void ensure(boolean cond1, boolean cond2, boolean cond3, boolean cond4, boolean cond5)
  {
    if (!cond1)
      throw new Error("ensure-condition1 failed!");
    if (!cond2)
      throw new Error("ensure-condition2 failed!");
    if (!cond3)
      throw new Error("ensure-condition3 failed!");
    if (!cond4)
      throw new Error("ensure-condition4 failed!");
    if (!cond5)
      throw new Error("ensure-condition5 failed!");
  }


  /**
   * ensure is a static method for post-conditions as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @param cond3 the condition that must hold
   *
   * @param cond4 the condition that must hold
   *
   * @param cond5 the condition that must hold
   *
   * @param cond6 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void ensure(boolean cond1, boolean cond2, boolean cond3, boolean cond4, boolean cond5, boolean cond6)
  {
    if (!cond1)
      throw new Error("ensure-condition1 failed!");
    if (!cond2)
      throw new Error("ensure-condition2 failed!");
    if (!cond3)
      throw new Error("ensure-condition3 failed!");
    if (!cond4)
      throw new Error("ensure-condition4 failed!");
    if (!cond5)
      throw new Error("ensure-condition5 failed!");
    if (!cond6)
      throw new Error("ensure-condition6 failed!");
  }


  /* ----------------------------------------------------------------------------- */


  /**
   * check is a static method for checks as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void check(boolean cond1)
  {
    if (!cond1)
      throw new Error("check-condition failed!");
  }


  /**
   * check is a static method for checks as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void check(boolean cond1, boolean cond2)
  {
    if (!cond1)
      throw new Error("check-condition1 failed!");
    if (!cond2)
      throw new Error("check-condition2 failed!");
  }


  /**
   * check is a static method for checks as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @param cond3 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void check(boolean cond1, boolean cond2, boolean cond3)
  {
    if (!cond1)
      throw new Error("check-condition1 failed!");
    if (!cond2)
      throw new Error("check-condition2 failed!");
    if (!cond3)
      throw new Error("check-condition3 failed!");
  }


  /**
   * check is a static method for checks as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @param cond3 the condition that must hold
   *
   * @param cond4 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void check(boolean cond1, boolean cond2, boolean cond3, boolean cond4)
  {
    if (!cond1)
      throw new Error("check-condition1 failed!");
    if (!cond2)
      throw new Error("check-condition2 failed!");
    if (!cond3)
      throw new Error("check-condition3 failed!");
    if (!cond4)
      throw new Error("check-condition4 failed!");
  }



  /**
   * check is a static method for checks as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @param cond3 the condition that must hold
   *
   * @param cond4 the condition that must hold
   *
   * @param cond5 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void check(boolean cond1, boolean cond2, boolean cond3, boolean cond4, boolean cond5)
  {
    if (!cond1)
      throw new Error("check-condition1 failed!");
    if (!cond2)
      throw new Error("check-condition2 failed!");
    if (!cond3)
      throw new Error("check-condition3 failed!");
    if (!cond4)
      throw new Error("check-condition4 failed!");
    if (!cond5)
      throw new Error("check-condition5 failed!");
  }


  /**
   * check is a static method for checks as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @param cond3 the condition that must hold
   *
   * @param cond4 the condition that must hold
   *
   * @param cond5 the condition that must hold
   *
   * @param cond6 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void check(boolean cond1, boolean cond2, boolean cond3, boolean cond4, boolean cond5, boolean cond6)
  {
    if (!cond1)
      throw new Error("check-condition1 failed!");
    if (!cond2)
      throw new Error("check-condition2 failed!");
    if (!cond3)
      throw new Error("check-condition3 failed!");
    if (!cond4)
      throw new Error("check-condition4 failed!");
    if (!cond5)
      throw new Error("check-condition5 failed!");
    if (!cond6)
      throw new Error("check-condition6 failed!");
  }


  /**
   * check is a static method for checks as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @param cond3 the condition that must hold
   *
   * @param cond4 the condition that must hold
   *
   * @param cond5 the condition that must hold
   *
   * @param cond6 the condition that must hold
   *
   * @param cond7 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void check(boolean cond1, boolean cond2, boolean cond3, boolean cond4, boolean cond5, boolean cond6, boolean cond7)
  {
    if (!cond1)
      throw new Error("check-condition1 failed!");
    if (!cond2)
      throw new Error("check-condition2 failed!");
    if (!cond3)
      throw new Error("check-condition3 failed!");
    if (!cond4)
      throw new Error("check-condition4 failed!");
    if (!cond5)
      throw new Error("check-condition5 failed!");
    if (!cond6)
      throw new Error("check-condition6 failed!");
    if (!cond7)
      throw new Error("check-condition7 failed!");
  }


  /**
   * check is a static method for checks as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @param cond3 the condition that must hold
   *
   * @param cond4 the condition that must hold
   *
   * @param cond5 the condition that must hold
   *
   * @param cond6 the condition that must hold
   *
   * @param cond7 the condition that must hold
   *
   * @param cond8 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void check(boolean cond1, boolean cond2, boolean cond3, boolean cond4, boolean cond5, boolean cond6, boolean cond7, boolean cond8)
  {
    if (!cond1)
      throw new Error("check-condition1 failed!");
    if (!cond2)
      throw new Error("check-condition2 failed!");
    if (!cond3)
      throw new Error("check-condition3 failed!");
    if (!cond4)
      throw new Error("check-condition4 failed!");
    if (!cond5)
      throw new Error("check-condition5 failed!");
    if (!cond6)
      throw new Error("check-condition6 failed!");
    if (!cond7)
      throw new Error("check-condition7 failed!");
    if (!cond8)
      throw new Error("check-condition8 failed!");
  }


  /**
   * check is a static method for checks as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @param cond3 the condition that must hold
   *
   * @param cond4 the condition that must hold
   *
   * @param cond5 the condition that must hold
   *
   * @param cond6 the condition that must hold
   *
   * @param cond7 the condition that must hold
   *
   * @param cond8 the condition that must hold
   *
   * @param cond9 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void check(boolean cond1, boolean cond2, boolean cond3, boolean cond4, boolean cond5, boolean cond6, boolean cond7, boolean cond8, boolean cond9)
  {
    if (!cond1)
      throw new Error("check-condition1 failed!");
    if (!cond2)
      throw new Error("check-condition2 failed!");
    if (!cond3)
      throw new Error("check-condition3 failed!");
    if (!cond4)
      throw new Error("check-condition4 failed!");
    if (!cond5)
      throw new Error("check-condition5 failed!");
    if (!cond6)
      throw new Error("check-condition6 failed!");
    if (!cond7)
      throw new Error("check-condition7 failed!");
    if (!cond8)
      throw new Error("check-condition8 failed!");
    if (!cond9)
      throw new Error("check-condition9 failed!");
  }


  /**
   * check is a static method for checks as in Eiffel
   *
   * @param cond1 the condition that must hold
   *
   * @param cond2 the condition that must hold
   *
   * @param cond3 the condition that must hold
   *
   * @param cond4 the condition that must hold
   *
   * @param cond5 the condition that must hold
   *
   * @param cond6 the condition that must hold
   *
   * @param cond7 the condition that must hold
   *
   * @param cond8 the condition that must hold
   *
   * @param cond9 the condition that must hold
   *
   * @param cond10 the condition that must hold
   *
   * @throws Error in case any argument is false
   */
  public static void check(boolean cond1, boolean cond2, boolean cond3, boolean cond4, boolean cond5, boolean cond6, boolean cond7, boolean cond8, boolean cond9, boolean cond10)
  {
    if (!cond1)
      throw new Error("check-condition1 failed!");
    if (!cond2)
      throw new Error("check-condition2 failed!");
    if (!cond3)
      throw new Error("check-condition3 failed!");
    if (!cond4)
      throw new Error("check-condition4 failed!");
    if (!cond5)
      throw new Error("check-condition5 failed!");
    if (!cond6)
      throw new Error("check-condition6 failed!");
    if (!cond7)
      throw new Error("check-condition7 failed!");
    if (!cond8)
      throw new Error("check-condition8 failed!");
    if (!cond9)
      throw new Error("check-condition9 failed!");
    if (!cond10)
      throw new Error("check-condition10 failed!");
  }


  /* ----------------------------------------------------------------------------- */


  /* uncomment to get simple allocation statistics *
  static int cnt;

  public ANY()
  {
    cnt++;
    if ((cnt&(cnt-1))==0)
      {
        Thread.dumpStack();
      }
  }
  /*  */


  /* uncomment to get allocation statistics *
  static HashMap<Class, Integer> _counts_ = new HashMap<>();

  public ANY()
  {
    Class cl = getClass();
    int n = _counts_.getOrDefault(cl, 0);
    _counts_.put(cl, n+1);
  }

  static
  {
    java.lang.Runtime.getRuntime().addShutdownHook
      (new Thread(() ->
       {
         for (var e : _counts_.entrySet())
           {
             System.out.println("ALLOCS: "+e.getValue()+"\t: "+e.getKey());
           }
       }
                  ));
  }
  /*  */

}

/* end of file */
