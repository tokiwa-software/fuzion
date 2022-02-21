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
 * Source of class ValueWithClazz
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import dev.flang.air.Clazz;
import dev.flang.air.Clazzes;

import dev.flang.util.Errors;


/**
 * ValueWithClazz represents a value that is equipped with an instance of
 * clazz that  describes its type.
 *
 * This instance is used for debugging and, if it is an isRef() or
 * isDynamicOuterRef(), for dynamic binding.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class ValueWithClazz extends Value
{

  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The clazz of this value.
   */
  protected final Clazz _clazz;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a value with the given clazz.
   *
   * @param clazz
   *
   * @param outer
   */
  ValueWithClazz(Clazz clazz)
  {
    if (PRECONDITIONS) require
      (clazz != null);

    this._clazz = clazz;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * clazz returns the clazz of a dynamic instance
   *
   * @return the clazz
   */
  public Clazz clazz()
  {
    Clazz result = _clazz;
    if (CHECKS) check
      ((result == null) || result.isRef() || result.isDynamicOuterRef());
    return result;
  }

}

/* end of file */
