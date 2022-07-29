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


/**
 * FrontEndOptions specify the configuration of the front end
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


  private boolean _tailRecursionInsteadOfLoops; // NYI: move to FrontendOptions
  public void setTailRec() { _tailRecursionInsteadOfLoops = true; }
  public boolean tailRecursionInsteadOfLoops() { return _tailRecursionInsteadOfLoops; }


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Costructor initializing fields as given.
   */
  public FuzionOptions(int verbose, int fuzionDebugLevel, boolean fuzionSafety)
  {
    if (PRECONDITIONS) require
      (verbose >= 0);

    _verbose = verbose;
    _fuzionDebugLevel = fuzionDebugLevel;
    _fuzionSafety = fuzionSafety;

  }


  /**
   * Costructor initializing fields from existing FuzionOptions instance
   */
  public FuzionOptions(FuzionOptions fo)
  {
    this(fo.verbose(),
         fo.fuzionDebugLevel(),
         fo.fuzionSafety());
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Check if verbose output at the given leven is enabled.
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
        System.out.println(s);
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


}

/* end of file */
