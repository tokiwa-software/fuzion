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
 * Source of class Context
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;

import dev.flang.util.ANY;
import dev.flang.util.Errors;


/**
 * Context to show why something is found to be used.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public interface Context
{


  /*-----------------------------  classes  -----------------------------*/


  /**
   * The main application entry point as a Context.
   */
  static class MainEntryPoint extends ANY implements Context
  {
    public String showWhy()
    {
      say("program entry point");
      return "  ";
    }
    public String toString(boolean forEnv)
    {
      return forEnv ? "effect environment " + Errors.effe(Env.envAsString(null)) + " at program entry"
                    : "program entry point";
    }
  };


  /*----------------------------  constants  ----------------------------*/


  /**
   * Singleton instance of MainEntryPoint.
   */
  static final Context _MAIN_ENTRY_POINT_ = new MainEntryPoint();


  /*----------------------------  variables  ----------------------------*/


  /*---------------------------  constructors  ---------------------------*/


  /*--------------------------  static methods  -------------------------*/


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Show the context that caused the inclusion of this instance into the
   * analysis.
   *
   * @return a string providing the indentation level for the caller in case of
   * nested contexts.  "  " is to be added to the result on each recursive call.
   */
  String showWhy();


  /**
   * Show the context that caused the inclusion of this instance into the
   * analysis in a way that is useful for error related to effects.
   */
  String toString(boolean forEnv);


  /**
   * Create a multi-line String describing this context to be used in error
   * messages.
   *
   * @param forEnv true if effect environments should be included in the
   * resulting string.
   *
   * @return A LF-terminated String of the call context starting with the
   * innermost context going towards older and older contexts until we reach the
   * program entry point.
   */
  default String contextString(boolean forEnv)
  {
    var result = new StringBuilder();
    Context co = this;
    while (co != null)
      {
        result.append(co.toString(forEnv) + "\n");
        co = co instanceof Call cc ? cc._context : null;
      }
    return result.toString();
  }


  /**
   * Convenience function for `conextString(true)`
   */
  default String contextStringForEnv() { return contextString(true); }


  /**
   * Convenience function for `conextString(false)`
   */
  default String contextString()       { return contextString(false); }


}

/* end of file */
