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
 * Source of class NoType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;


/**
 * NoType represents a feature return type in source code that is missing.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class NoType extends ReturnType
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * The singleton instance.
   */
  public static final NoType INSTANCE = new NoType();


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  private NoType()
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
    return false;
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
