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
 * Source of class MIR
 *
 *---------------------------------------------------------------------*/

package dev.flang.mir;

import dev.flang.ast.Feature;  // NYI: Remove dependency!

import dev.flang.util.ANY;


/**
 * The MIR contains the module-intermediate representation of Fuzion features.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class MIR extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The main feature
   */
  final Feature _main;


  /*--------------------------  constructors  ---------------------------*/


  public MIR(Feature main)
  {
    _main = main;
  }


  /*-----------------------------  methods  -----------------------------*/


  public Feature main()
  {
    return _main;
  }

}

/* end of file */
