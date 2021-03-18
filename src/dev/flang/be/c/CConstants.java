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
 * Source of class CConstants
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;

import dev.flang.util.ANY;


/**
 * CConstants provides constants used by the backend, e.g., limitations.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class CConstants extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * The code generator assumes that the first page is reserved by the system,
   * no legal memory address could end up in this first page.
   */
  static final long PAGE_SIZE = 4096;

}

/* end of file */
