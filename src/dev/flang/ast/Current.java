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
 * Source of class Current
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Iterator;

import dev.flang.util.SourcePosition;


/**
 * Current is an expression that returns the current object
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Current extends AbstractCurrent
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  private final SourcePosition _pos;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t the result type
   */
  public Current(SourcePosition pos, AbstractType t)
  {
    super(Types.intern(t).asThis());

    if (PRECONDITIONS) require
      (pos != null,
       t != null);

    this._pos = pos;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  public SourcePosition pos()
  {
    return _pos;
  }

}

/* end of file */
