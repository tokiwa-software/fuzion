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
 * Source of class FuzionHome
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools;

import dev.flang.util.ANY;
import dev.flang.util.FuzionConstants;
import dev.flang.util.FuzionOptions;

import java.nio.file.Path;


/**
 * FuzionHome allows a Tool to retrieve the currently set value of the fuzion
 * home Java property.
 */
public class FuzionHome extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * Value of property with name FUZION_HOME_PROPERTY.  Used only to initialize
   * _fuzionHome.
   */
  private static final String _fuzionHomeProperty = FuzionOptions.propertyOrEnv(FuzionConstants.FUZION_HOME_PROPERTY);


  /**
   * Home directory of the Fuzion installation.
   */
  public static final Path _fuzionHome = _fuzionHomeProperty != null ? Path.of(_fuzionHomeProperty) : null;

}

/* end of file */
