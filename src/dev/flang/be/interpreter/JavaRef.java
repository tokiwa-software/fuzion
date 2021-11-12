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
 * Source of class JavaRef
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import dev.flang.air.Clazz;
import dev.flang.air.Clazzes;

import dev.flang.util.Errors;


/**
 * JavaRef wraps a Java reference into a Value used by the interpreter
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class JavaRef extends Value
{

  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  final Object _javaRef;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param clazz
   *
   * @param outer
   */
  public JavaRef(Object javaRef)
  {
    this._javaRef = javaRef;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "javaRef[" + _javaRef + "]";
  }

}

/* end of file */
