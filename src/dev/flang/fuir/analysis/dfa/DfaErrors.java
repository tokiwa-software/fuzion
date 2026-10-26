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
 * Source of class DfaErrors
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionOptions;
import static dev.flang.util.Errors.*;
import dev.flang.util.HasSourcePosition;


/**
 * DfaErrors handles errors found during DFA phase
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class DfaErrors extends ANY
{


  /*-----------------------------  methods  -----------------------------*/


  public static void usedEffectNotInstalled(HasSourcePosition pos, String e, Context why)
  {
    Errors.error(pos.pos(),
                 "Failed to verify that effect " + st(e) + " is installed in current environment.",
                 "Callchain that lead to this point:\n\n" + why.contextStringForEnv() + "\n" +
                 "To fix this, you should make sure that an effect for type " + st(e) +" is instated "+
                 "along all call paths to this effect use." +
                 (DFA.TRACE_ALL_EFFECT_ENVS
                  ? ""
                  : ("\n\nAlternatively, you might want to try setting env variable "+code(FuzionOptions.envVarName(DFA.TRACE_ALL_EFFECT_ENVS_NAME)+"=true")+
                     ".  This will increase the analysis accuracy for effects, but may take a long time.")
                  ));
  }


  public static void readingUninitializedField(HasSourcePosition pos, String field, String clazz, Context why)
  {
    Errors.error(pos.pos(),
                 "reading uninitialized field " + sqn(field) + " from instance of " + code(clazz),
                 "Callchain that lead to this point:\n\n" + why.contextString());
  }


  /**
   * Report a fatal error for a recursion with ever-growing generic type
   * arguments.  Such a recursion would create an unbounded number of
   * monomorphized clazzes during DFA.
   *
   * @param e the newly created call that caused the growing instantiation
   *
   * @param ancestor an older call to the same original feature with a smaller
   * type instantiation
   */
  public static void dfaGrowingTypeRecursion(Call e, Call ancestor)
  {
    var fuir = e._dfa._fuir;
    var cl   = e.calledClazz();
    var anc  = ancestor.calledClazz();
    var msg  = "DFA detected unbounded recursion with ever-growing type arguments.";
    var detail =
      "Call to " + code(fuir.clazzNameHuman(cl)) +
      "\nrecurs from call to " + code(fuir.clazzNameHuman(anc)) +
      "\nwith a strictly larger type instantiation.\n\n" +
      "Callchain that lead to this point:\n\n" + e._context.contextString();
    var pos = e.callSitePos();
    if (pos == null)
      {
        Errors.fatal(msg, detail);
      }
    else
      {
        Errors.fatal(pos.pos(), msg, detail);
      }
  }


  public static void fatal(String msg)
  {
    Errors.fatal(msg);
  }

}

/* end of file */
