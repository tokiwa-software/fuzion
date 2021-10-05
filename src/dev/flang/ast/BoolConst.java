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
 * Source of class NumLiteral
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.SourcePosition;


/**
 * BoolConst <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class BoolConst extends Expr
{


  /*------------------------  static variables  -------------------------*/


  public static final BoolConst TRUE = new BoolConst(true);
  public static final BoolConst FALSE = new BoolConst(false);


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  public boolean b;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param s
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
   * typeOrNull returns the type of this expression or null if the type is still
   * unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  public Type typeOrNull()
  {
    return Types.resolved.t_bool;
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this.
   */
  public BoolConst visit(FeatureVisitor v, Feature outer)
  {
    // nothing to be done for a constant
    return this;
  }


  boolean isCompileTimeConst()
  {
    return true;
  }

  boolean getCompileTimeConstBool()
  {
    return b;
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
