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
import dev.flang.air.IClazzes;

import dev.flang.fe.FrontEnd;

import dev.flang.fuir.AirFUIR;
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


  public final AIR _air;


  private final IClazzes _clazzes;


  private final FrontEnd _fe;


  private final MIR _mir;


  /*--------------------------  constructors  ---------------------------*/


  public Optimizer(FuzionOptions options, AIR air, IClazzes clazzes)
  {
    _options = options;
    _air = air;
    _clazzes = clazzes;
    _fe = null;
    _mir = null;
  }


  public Optimizer(FuzionOptions options, FrontEnd fe, MIR mir)
  {
    _options = options;
    _air = null;
    _clazzes = null;
    _fe = fe;
    _mir = mir;
  }



  /*-----------------------------  methods  -----------------------------*/


  public FUIR fuir()
  {
    FUIR result;
    if (_fe != null)
      {
        result = new GeneratingFUIR(_fe, _mir);
      }
    else
      {
        result = new AirFUIR(_air.main(), _clazzes);
      }
    return result;
  }


}

/* end of file */
