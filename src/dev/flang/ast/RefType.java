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
 * Source of class RefType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;


/**
 * RefType
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class RefType extends ReturnType
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * The singleton instance.
   */
  public static final RefType INSTANCE = new RefType();


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  private RefType()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * true iff this is the return type of a constructor feature, i.e., "this" is
   * returned implicitly instead of "result".
   *
   * @return true for a constructor return type.
   */
  @Override
  boolean isConstructorType()
  {
    return true;
  }


  /**
   * toString
   *
   * @return
   */
  @Override
  public String toString()
  {
    return "";
  }
}

/* end of file */
