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
 * Source of class JVMOptions
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm;

import dev.flang.util.FuzionOptions;

import java.util.ArrayList;
import java.util.Optional;


/**
 * JVMOptions specify the configuration of the JVM back end
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class JVMOptions extends FuzionOptions
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Strategies for creation of instances for compile-time constants
   */
  public enum ConstantCreation
  {
    /**
     * Create a new instance of a constant every time it is used.  This is ideal
     * if the code using the constant is executed at most once, but fairly
     * inefficient in case it is use repeatedly.
     */
    onEveryUse,

    /**
     * Create instances when the universe is initialized.  This is good if a
     * constant is used repeatedly, but it adds startup overhead and wasted
     * resources for constants used in code that is never executed or executed
     * only very infrequently (such as error handling code after a fatal error).
     */
    onUniverseInitialization;

    /* There might be other alternatives, i.e., create constants on first use
     * and cache them, or create constants on first use of the surrounding
     * features, etc.
     */
  }



  /*----------------------------  variables  ----------------------------*/


  /**
   * Should the generated JVM bytecode be run immediately?
   */
  final boolean _run;


  /**
   * Should the generated JVM bytecode be saved as class files?
   */
  final boolean _saveClasses;


  /**
   * Should the generated JVM bytecode be saved as JAR file?
   */
  final boolean _saveJAR;


  /**
   * List of arguments to pass to the program, if it is run immediately.
   */
  final ArrayList<String> _applicationArgs;


  /**
   * Constant creation strategy to be used for non-primitive-type constants.
   */
  final ConstantCreation _constantCreationStrategy = ConstantCreation.onUniverseInitialization;


  /**
   * Custom output name when using option `-classes` or `-jvm`.
   */
  final Optional<String> _outputName;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor initializing fields as given.
   */
  public JVMOptions(FuzionOptions fo,
                    boolean run,
                    boolean saveClasses,
                    boolean saveJAR,
                    Optional<String> outputName)
  {
    super(fo);

    this._run         = run;
    this._saveClasses = saveClasses;
    this._saveJAR     = saveJAR;
    this._applicationArgs = fo.getBackendArgs();
    this._outputName = outputName;
  }


  /*-----------------------------  methods  -----------------------------*/

}

/* end of file */
