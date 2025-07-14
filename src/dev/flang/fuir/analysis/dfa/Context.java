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
    /**
     * Return a unique id for the call or main entry point context.
     */
    @Override
    public int uniqueCallId()
    {
      return -1;
    }

    /**
     * Effect-environment in this context, null if none.
     */
    @Override
    public Env env()
    {
      return null;
    }

    public String showWhy(StringBuilder sb)
    {
      sb.append("program entry point")
        .append("\n");
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
   * Return a unique id for the call or main entry point context.
   */
  abstract int uniqueCallId();


  /**
   * Effect-environment in this context, null if none.
   */
  abstract Env env();

  /**
   * Show the context that caused the inclusion of this instance into the
   * analysis.
   *
   * @param sb the context information will be appended to this StringBuilder.
   *
   * @return a string providing the indentation level for the caller in case of
   * nested contexts.  "  " is to be added to the result on each recursive call.
   */
  String showWhy(StringBuilder sb);


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
   * Convenience function for {@code contextString(true)}
   */
  default String contextStringForEnv() { return contextString(true); }


  /**
   * Convenience function for {@code contextString(false)}
   */
  default String contextString()       { return contextString(false); }



  /**
   * Check the context if it contains an effect of clazz `cl` instantiated at
   * `site`.
   *
   * @param cl a clazz
   *
   * @param site a site that contains a constructor call to `cl`
   *
   * @return in case the context contains an environment with an instance of
   * `cl` created at `site` instated, then return that existing instance.
   * Return null otherwise.
   */
  default Instance findEffect(int cl, int site)
  {
    Instance result = null;
    var e = env();
    if (e != null)
      {
        result = e.find(cl, site);
      }
    if (result == null && this instanceof Call cc)
      {
        result = cc._context.findEffect(cl, site);
      }
    return result;
  }


}

/* end of file */
