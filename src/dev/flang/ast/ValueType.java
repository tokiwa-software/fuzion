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
 * Source of class ValueType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;


/**
 * ValueType <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ValueType extends ReturnType
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * The singleton instance.
   */
  public static final ValueType INSTANCE = new ValueType();


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  private ValueType()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * true iff this is the return type of a constructor feature, i.e., "this" is
   * returned implicitly instead of "result".
   *
   * @return true for a constructor return type.
   */
  public boolean isConstructorType()
  {
    return true;
  }


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   */
  public void visit(FeatureVisitor v, AbstractFeature outer)
  {
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "value";
  }

}

/* end of file */
