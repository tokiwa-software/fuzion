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
 * Source of class ParsedName
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.SourcePosition;


/**
 * ParsedName represents a name created by the parser for the rule for `name`.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ParsedName extends ANY implements HasSourcePosition
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Name to be used in case we need a name, but there was an error parsing the
   * name.
   */
  public final static ParsedName ERROR_NAME = new ParsedName(SourcePosition.builtIn,
                                                             Errors.ERROR_STRING);


  /**
   * Name to be used in case the actual name is to be ignored.
   */
  public final static ParsedName DUMMY = new ParsedName(SourcePosition.builtIn,
                                                        FuzionConstants.DUMMY_NAME_STRING);


  /*----------------------------  variables  ----------------------------*/


  /**
   * The actual name.
   */
  public final String _name;


  /**
   * The sourcecode position of this name.
   */
  public final SourcePosition _pos;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for name at given pos.
   *
   * @param pos the position where name was found
   *
   * @param name the name.
   */
  public ParsedName(SourcePosition pos, String name)
  {
    if (PRECONDITIONS) require
      (pos != null,
       name != null);

    this._pos = pos;
    this._name = name;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The sourcecode position of this name.
   */
  public SourcePosition pos()
  {
    return _pos;
  }


  /**
   * toString creates a string representation for debug output.
   *
   * @retur just the name.
   */
  public String toString()
  {
    return _name;
  }

}

/* end of file */
