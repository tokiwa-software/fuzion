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
 * Source of class Consts
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.ANY;


/**
 * Consts defines global constants used in the AST
 *
 * NYI: Consider moving these constants to def.flang.util.FuzionConstants.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Consts extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   *
   */
  public static final String[] MODIFIER_STRINGS = {"lazy", "redef", "fixed", "dyn"};


  /**
   *
   */
  public static final int MODIFIER_LAZY         = 0x01;
  static { if (CHECKS) check(modifierToString(MODIFIER_LAZY).trim().equals("lazy")); }

  /**
   *
   */
  public static final int MODIFIER_REDEFINE     = 0x02;
  static { if (CHECKS) check(modifierToString(MODIFIER_REDEFINE).trim().equals("redef")); }

  /**
   * 'fixed' modifier to force feature to be fixed, i.e., not inherited by
   * heirs.
   */
  public static final int MODIFIER_FIXED        = 0x04;
  static { if (CHECKS) check(modifierToString(MODIFIER_FIXED).trim().equals("fixed")); }

  /**
   * 'dyn' modifier to force feature within type feature to be dynamic.
   */
  public static final int MODIFIER_DYN          = 0x08;
  static { if (CHECKS) check(modifierToString(MODIFIER_DYN).trim().equals("dyn")); }


  /**
   * visibility for anonymous features
   */
  public static final Visi VISIBILITY_INVISIBLE  = new Visi("INVISIBLE");


  /**
   * default visibility: visible to all inner classes of outer class
   * of declaring class
   */
  public static final Visi VISIBILITY_LOCAL      = new Visi("LOCAL");


  /**
   * private visibility: visible to declaring class and all its inner
   * classes
   */
  public static final Visi VISIBILITY_PRIVATE    = new Visi("PRIVATE");


  /**
   * protected visibility: visibly to all heirs of declaring class
   */
  public static final Visi VISIBILITY_CHILDREN   = new Visi("CHILDREN");


  /**
   * public visibility: visible to all classes
   */
  public static final Visi VISIBILITY_PUBLIC     = new Visi("PUBLIC");


  /*-----------------------------  methods  -----------------------------*/


  /**
   * modifierToString
   *
   * @param m
   *
   * @return
   */
  public static String modifierToString(int m)
  {
    String result = "";
    for(int i=0; i<32; i++)
      {
        if ((m & (1<<i))!=0)
          {
            //        result = result + (result.length()!=0?" ":"") + MODIFIER_STRINGS[i];
            result = result + MODIFIER_STRINGS[i] + " ";
          }
      }
    return result;
  }

}

/* end of file */
