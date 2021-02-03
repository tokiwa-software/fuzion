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
 * Source of class Pretty
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools;

import java.nio.file.Path;

import dev.flang.util.ANY;
import dev.flang.util.Errors;


/**
 * Pretty is a pretty printer for Fuzion source code
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class Pretty extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for the pretty printer to read from stdin.
   */
  Pretty()
  {
    Errors.fatal("pretty printer not yet supported");
  }


  /**
   * Constructor for the pretty printer to read from given file
   */
  Pretty(String file)
  {
    Errors.fatal("pretty printer not yet supported");
  }

}

/* end of file */
