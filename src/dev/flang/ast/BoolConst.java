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
 * Source of class BoolConst
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.SourcePosition;


/**
 * BoolConst
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class BoolConst extends Constant
{


  /*------------------------  static variables  -------------------------*/


  public static final BoolConst TRUE = new BoolConst(true);
  public static final BoolConst FALSE = new BoolConst(false);


  /**
   * Serialized forms of this constants:
   */
  static final byte[] DATA_TRUE  = new byte[] { 1 };
  static final byte[] DATA_FALSE = new byte[] { 0 };


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  public boolean b;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  private BoolConst(boolean value)
  {
    super(SourcePosition.builtIn);
    this.b = value;
  }


  /*--------------------------  static methods  -------------------------*/


  /**
   * Get either TRUE of FALSE
   */
  static BoolConst get(boolean b)
  {
    return b ? TRUE : FALSE;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * typeForInferencing returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  @Override
  AbstractType typeForInferencing()
  {
    return Types.resolved.t_bool;
  }


  boolean getCompileTimeConstBool()
  {
    return b;
  }


  /**
   * Serialized form of the data of this constant.
   */
  public byte[] data()
  {
    return b ? DATA_TRUE : DATA_FALSE;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return b ? "true" : "false";
  }

}

/* end of file */
