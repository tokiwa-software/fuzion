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
 * Source of class Instance
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis;

import java.util.TreeSet;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;


/**
 * Instance represents an abstract instance of a feature handled by the DFA
 * Analysis. An Abstract instance may consist of abstract values as well as
 * context information, taint information, etc.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Instance extends ANY
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The clazz this is an instance of.
   */
  int _clazz;


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create Instance of given clazz
   *
   * @param clazz the clazz this is an instance of.
   */
  public Instance(int clazz)
  {
    _clazz = clazz;
  }


}

/* end of file */
