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

import dev.flang.fuir.GeneratingFUIR;
import dev.flang.fuir.OptimizedFUIR;

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

  private final OptimizedFUIR _fuir;


  /*--------------------------  constructors  ---------------------------*/


  public Optimizer(FuzionOptions options, GeneratingFUIR fuir)
  {
    _options = options;
    // NYI: UNDER DEVELOPMENT:
    _fuir = new OptimizedFUIR(fuir);
  }


  /*-----------------------------  methods  -----------------------------*/


  public OptimizedFUIR fuir()
  {
    return _fuir;
  }


}

/* end of file */
