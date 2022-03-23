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
 * Source of class CTemp
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;


/**
 * CLocal is a CIdent that represents a local variable.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class CLocal extends CIdent
{


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create a C expression from a plain identifier
   *
   * @return the resulting expression
   */
  CLocal(String name)
  {
    super(name);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Is this a local variable?
   */
  boolean isLocalVar()
  {
    return true;
  }

}

/* end of file */
