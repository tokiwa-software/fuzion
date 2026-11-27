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
 * Source of class ReturnType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.ANY;
import dev.flang.util.SourcePosition;


/**
 * ReturnType represents the type of the value returned by a feature, which
 * could be either a normal type if the feature is a function, or
 * RefType/ValueType if the feature is a constructor
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class ReturnType extends ANY
{


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  ReturnType()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * If this has a source code position, return it
   *
   * @return the source position or null if there is none (e.g. for NoType).
   */
  SourcePosition posOrNull()
  {
    return null;
  }

  /**
   * true iff this is the return type of a constructor feature, i.e., "this" is
   * returned implicitly instead of "result".
   *
   * @return true for a constructor return type, false for a function.
   */
  abstract boolean isConstructorType();


  /**
   * For a function, the result type.
   *
   * @return the function result type.
   */
  AbstractType functionReturnType()
  {
    return functionReturnType(false);
  }


  /**
   * For a function, the result type.
   *
   * @param allowIncomplete allow returning types where generics are missing,
   * i.e. an incomplete type from a case field
   *
   * @return the function result type.
   */
  AbstractType functionReturnType(boolean allowIncomplete)
  {
    if (PRECONDITIONS) require
      (!isConstructorType());

    throw new Error(); // this is redefined for FunctionReturnType
  }


  /**
   * Used for checking an explicitly given return type for validity.
   *
   * @param context the source code context where this return type is used
   */
  void checkType(Context context) { }


  /**
   * used for resolving the (explicit) type of a return type
   *
   * @param res the resolution instance
   *
   * @param context the source code context where this return type is used
   */
  void resolveType(Resolution res, Context context) { }


}

/* end of file */
