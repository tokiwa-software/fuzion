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
 * Source of class SingleType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;


/**
 * SingleType <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class SingleType extends ReturnType
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * The singleton instance.
   */
  public static final SingleType INSTANCE = new SingleType();


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  private SingleType()
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
   * true iff this is the return type of a constructor feature that returns a
   * reference, i.e., it is marked "ref" or "single".
   *
   * @return true for a refr return type, false for a function.
   */
  public boolean isRef()
  {
    return true;
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   */
  public void visit(FeatureVisitor v, Feature outer)
  {
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "single";
  }

}

/* end of file */
