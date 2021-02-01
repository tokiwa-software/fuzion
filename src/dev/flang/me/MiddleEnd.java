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
 * Source of class MiddleEnd
 *
 *---------------------------------------------------------------------*/

package dev.flang.me;

import dev.flang.air.AIR;

import dev.flang.mir.MIR;

import dev.flang.ir.Clazz;
import dev.flang.ir.Clazzes;

import dev.flang.util.ANY;
import dev.flang.util.FuzionOptions;


/**
 * The MiddleEnd creates application IR (air) from the the module IRs (mir)
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class MiddleEnd extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Configuration
   */
  public final FuzionOptions _options;


  public final MIR _mir;


  /*--------------------------  constructors  ---------------------------*/


  public MiddleEnd(FuzionOptions options, MIR mir)
  {
    _options = options;
    _mir = mir;
    Clazzes.init(options);
  }


  /*-----------------------------  methods  -----------------------------*/


  public AIR air()
  {
    return new AIR(main());
  }


  private Clazz main()
  {
    var main = _mir.main();
    Clazz cl = main != null ? Clazzes.clazz(main.thisType()) : null;
    return cl;
  }


}

/* end of file */
