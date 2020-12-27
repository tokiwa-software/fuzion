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
 * Source of class FusionOptions
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;


/**
 * FrontEndOptions specify the configuration of the front end
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class FusionOptions extends ANY
{



  /*----------------------------  variables  ----------------------------*/


  /**
   * Level of verbosity of output, 0 for no output
   */
  final int _verbose;


  /**
   * runtime safety-checks enabled?
   */
  final boolean _fusionSafety;


  /**
   * level of runtime debug checks
   */
  final int _fusionDebugLevel;


  private boolean _tailRecursionInsteadOfLoops; // NYI: move to FrontendOptions
  public void setTailRec() { _tailRecursionInsteadOfLoops = true; }
  public boolean tailRecursionInsteadOfLoops() { return _tailRecursionInsteadOfLoops; }

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Costructor initializing fields as given.
   */
  public FusionOptions(int verbose, boolean fusionSafety, int fusionDebugLevel)
  {
    if (PRECONDITIONS) require
                         (verbose >= 0);

    _verbose = verbose;
    _fusionSafety = fusionSafety;
    _fusionDebugLevel = fusionDebugLevel;

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


  public boolean fusionSafety()
  {
    return _fusionSafety;
  }

  public int fusionDebugLevel()
  {
    return _fusionDebugLevel;
  }

  public boolean fusionDebug()
  {
    return fusionDebugLevel() > 0;
  }


}

/* end of file */
