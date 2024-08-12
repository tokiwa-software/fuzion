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
 * Source of class FuzionOptions
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.nio.file.Path;

import java.util.ArrayList;

import java.util.function.Consumer;


/**
 * FuzionOptions specify shared configuration of front- and back-end
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FuzionOptions extends ANY
{


  /*-------------------------  static methods  --------------------------*/


  /**
   * Get the value of a Java property (set via -D<name>=...)  or env variable.
   *
   * @param name property or env variable name.  Should usually be a fully
   * qualified class name such as "dev.flang.optimizer.Warp.enable".  Since `.`
   * is not permitted in env var, `.` will be replaced by `_` when checking env
   * variables.
   *
   * @return the value of the property, if found. Otherwise, the value of the
   * env var if found. null otherwise.
   */
  public static String propertyOrEnv(String name)
  {
    return propertyOrEnv(name, null);
  }


  /**
   * Get the value of a Java property (set via -D<name>=...)  or env variable.
   *
   * @param name property or env variable name.  Should usually be a fully
   * qualified class name such as "dev.flang.optimizer.Warp.enable".  Since `.`
   * is not permitted in env var, `.` will be replaced by `_` when checking env
   * variables.
   *
   * @param defawlt value to return in case propery / env var is not set.
   *
   * @return the value of the property, if found. Otherwise, the value of the
   * env var if found. defawlt otherwise.
   */
  public static String propertyOrEnv(String name, String defawlt)
  {
    var result = System.getProperty(name);
    if (result == null)
      {
        result = System.getenv(envVarName(name));
      }
    return result != null ? result : defawlt;
  }


  /**
   * Helper to convert property name to anv var name. Replaces `.` by `_` since
   * `.` is not permitted in an env var.
   */
  private static String envVarName(String propertyName)
  {
    return propertyName.replace(".","_");
  }


  /**
   * Get the value of boolean a Java property (set via -D<name>=...)  or env
   * variable.
   *
   * @param name property or env variable name.  Should usually be a fully
   * qualified class name such as "dev.flang.optimizer.Warp.enable".  Since `.`
   * is not permitted in env var, `.` will be replaced by `_` when checking env
   * variables.
   *
   * @return true iff the property is set and equals to "true", false otherwise.
   */
  public static boolean boolPropertyOrEnv(String name)
  {
    return propertyOrEnv(name, "false").equals("true");
  }


  /**
   * Get the value of boolean a Java property (set via -D<name>=...) or env
   * variable.
   *
   * @param name property or env variable name.  Should usually be a fully
   * qualified class name such as "dev.flang.optimizer.Warp.enable".  Since `.`
   * is not permitted in env var, `.` will be replaced by `_` when checking env
   * variables.
   *
   * @param defawlt give the default value to be used if property / env var is
   * not present.
   *
   * @return true iff the property is set and equals to "true", false if it is
   * set to something different and `defawlt` otherwise.
   */
  public static boolean boolPropertyOrEnv(String name, boolean defawlt)
  {
    return propertyOrEnv(name, defawlt ? "true" : "false").equals("true");
  }


  /**
   * Get the value of int a Java property (set via -D<name>=...) or env
   * variable.
   *
   * @param name property or env variable name.  Should usually be a fully
   * qualified class name such as "dev.flang.optimizer.Warp.enable".  Since `.`
   * is not permitted in env var, `.` will be replaced by `_` when checking env
   * variables.
   *
   * @param defawlt give the default value to be used if property / env var is
   * not present.
   *
   * @return if the property is set, its value parsed as an integer. Othewise,
   * if the env var is set, its value parsed as an integer. Otherwise, defawlt.
   */
  public static int intPropertyOrEnv(String name, int defawlt)
  {
    var res = defawlt;
    var p = propertyOrEnv(name);
    if (p != null)
      {
        try
          {
            res = Integer.parseInt(p);
          }
        catch (NumberFormatException e)
          {
            var pe = System.getProperty(name) != null
              ? "property `" + name + "`"
              : "env var `" + envVarName(name) + "`";
            Errors.fatal("Failed to parse value " + p + " of " + pe + ": "+e);
            res = -1;
          }
      }
    return res;
  }


  /*----------------------------  variables  ----------------------------*/


  /**
   * Level of verbosity of output, 0 for no output
   */
  final int _verbose;


  /**
   * runtime safety-checks enabled?
   */
  final boolean _fuzionSafety;


  /**
   * level of runtime debug checks
   */
  final int _fuzionDebugLevel;


  /**
   * Flag to enable intrinsic functions such as fuzion.java.call_virtual. These are
   * not allowed if run in a web playground.
   */
  final boolean _enableUnsafeIntrinsics;


  /**
   * Path to the Fuzion home directory, never null.
   */
  final Path _fuzionHome;


  /*
   * Array that can be set to pass arbitrary arguments to the backend. Currently used
   * for passing arguments given to the interpreter to the fuzion.std.args intrinsics.
   */
  private ArrayList<String> _backendArgs;
  public void setBackendArgs(ArrayList<String> args) { _backendArgs = args; }
  public ArrayList<String> getBackendArgs() { return _backendArgs; }


  /**
   * `_timer.accept("phase")` can be called with a phase name to measure the
   * time spent in this "phase", printed if `-verbose` level is sufficiently
   * high.
   */
  final Consumer<String> _timer;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor initializing fields as given.
   *
   * @param timer can be called with a phase name to measure the time spent in
   * this phase, printed if `-verbose` level is sufficiently high.
   */
  public FuzionOptions(int verbose, int fuzionDebugLevel, boolean fuzionSafety, boolean enableUnsafeIntrinsics, Path fuzionHome, Consumer<String> timer)
  {
    if (PRECONDITIONS) require
      (verbose >= 0,
       fuzionHome != null);

    _verbose = verbose;
    _fuzionDebugLevel = fuzionDebugLevel;
    _fuzionSafety = fuzionSafety;
    _enableUnsafeIntrinsics = enableUnsafeIntrinsics;
    _fuzionHome = fuzionHome;
    _timer = timer;
  }


  /**
   * Constructor initializing fields from existing FuzionOptions instance
   */
  public FuzionOptions(FuzionOptions fo)
  {
    this(fo.verbose(),
         fo.fuzionDebugLevel(),
         fo.fuzionSafety(),
         fo.enableUnsafeIntrinsics(),
         fo.fuzionHome(),
         fo._timer);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Check if verbose output at the given level is enabled.
   */
  public boolean verbose(int level)
  {
    if (PRECONDITIONS) require
      (level > 0);

    return level <= _verbose;
  }


  /**
   * Level of verbosity of output, 0 for no output
   */
  public int verbose()
  {
    return _verbose;
  }


  /**
   * Print given string if running at given verbosity level.
   */
  public void verbosePrintln(int level, String s)
  {
    if (PRECONDITIONS) require
      (level > 0);

    if (verbose(level))
      {
        say(s);
      }
  }


  /**
   * Print given string if running at verbosity level 1 or higher.
   */
  public void verbosePrintln(String s)
  {
    verbosePrintln(1, s);
  }


  public boolean fuzionSafety()
  {
    return _fuzionSafety;
  }

  public int fuzionDebugLevel()
  {
    return _fuzionDebugLevel;
  }

  public boolean fuzionDebug()
  {
    return fuzionDebugLevel() > 0;
  }

  public boolean enableUnsafeIntrinsics()
  {
    return _enableUnsafeIntrinsics;
  }

  public Path fuzionHome()
  {
    return _fuzionHome;
  }

  public boolean isLanguageServer()
  {
    return false;
  }


  /**
   * Measure the time spent in this phase `name`, the result is printed at the
   * end if `-verbose` level is sufficiently high.
   */
  public void timer(String name)
  {
    _timer.accept(name);
  }


}

/* end of file */
