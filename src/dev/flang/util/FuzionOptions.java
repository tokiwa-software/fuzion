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


/**
 * FuzionOptions specify shared configuration of front- and back-end
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FuzionOptions extends ANY
{


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


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor initializing fields as given.
   */
  public FuzionOptions(int verbose, int fuzionDebugLevel, boolean fuzionSafety, boolean enableUnsafeIntrinsics, Path fuzionHome)
  {
    if (PRECONDITIONS) require
      (verbose >= 0,
       fuzionHome != null);

    _verbose = verbose;
    _fuzionDebugLevel = fuzionDebugLevel;
    _fuzionSafety = fuzionSafety;
    _enableUnsafeIntrinsics = enableUnsafeIntrinsics;
    _fuzionHome = fuzionHome;
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
         fo.fuzionHome());
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


}

/* end of file */
