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
 * Source of class Optimizer
 *
 *---------------------------------------------------------------------*/

package dev.flang.opt;

import dev.flang.air.AIR;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.FuzionOptions;


/**
 * The Optimizer creates the intermediate code FUIR from the application IR (air)
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Optimizer extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Configuration
   */
  public final FuzionOptions _options;


  public final AIR _air;


  /*--------------------------  constructors  ---------------------------*/


  public Optimizer(FuzionOptions options, AIR air)
  {
    _options = options;
    _air = air;
  }


  /*-----------------------------  methods  -----------------------------*/


  public FUIR fuir()
  {
    return new FUIR(_air.main());
  }


}

/* end of file */
