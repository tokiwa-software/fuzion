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
 * Source of class COptions
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm;

import dev.flang.util.FuzionOptions;

import java.util.ArrayList;


/**
 * JVMOptions specify the configuration of the JVM back end
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class JVMOptions extends FuzionOptions
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Should we use the DFA analysis to improve the generated code?
   */
  final boolean _Xdfa;


  /**
   * Should the generated JVM bytecode be run immediately?
   */
  final boolean _run;


  /**
   * Should the generated JVM bytecode be saved as class files?
   */
  final boolean _saveClasses;


  /**
   * List of arguments to pass to the program, if it is run immediately.
   */
  final ArrayList<String> _applicationArgs;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor initializing fields as given.
   */
  public JVMOptions(FuzionOptions fo,
                    boolean Xdfa,
                    boolean run,
                    boolean saveClasses)
  {
    super(fo);

    this._Xdfa        = Xdfa;
    this._run         = run;
    this._saveClasses = saveClasses;
    this._applicationArgs = fo.getBackendArgs();
  }


  /*-----------------------------  methods  -----------------------------*/

}

/* end of file */
