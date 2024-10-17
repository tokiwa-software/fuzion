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

import dev.flang.fe.FrontEnd;

import dev.flang.fuir.FUIR;
import dev.flang.fuir.GeneratingFUIR;

import dev.flang.mir.MIR;

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

  private final FrontEnd _fe;

  private final MIR _mir;


  /*--------------------------  constructors  ---------------------------*/


  public Optimizer(FuzionOptions options, FrontEnd fe, MIR mir)
  {
    _options = options;
    _fe = fe;
    _mir = mir;
  }


  /*-----------------------------  methods  -----------------------------*/


  public FUIR fuir()
  {
    return new GeneratingFUIR(_fe, _mir);
  }


}

/* end of file */
