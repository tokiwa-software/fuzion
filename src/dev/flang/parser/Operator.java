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
 * Source of class Operator
 *
 *---------------------------------------------------------------------*/

package dev.flang.parser;

import dev.flang.util.ANY;
import dev.flang.util.SourcePosition;


/**
 * Operator represents an infix, prefix or postfix opeartor encountered while
 * parsing.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class Operator extends ANY
{

  /*----------------------------  variables  ----------------------------*/


  public final SourcePosition pos;

  public final String text;


  /*--------------------------  constructors  ---------------------------*/


  public Operator(SourcePosition pos, String text)
  {
    if (PRECONDITIONS) require
      (pos != null,
       text != null);

    this.pos = pos;
    this.text = text;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Convert this to String for debugging.
   */
  public String toString()
  {
    return "OP:"+text;
  }

}

/* end of file */
