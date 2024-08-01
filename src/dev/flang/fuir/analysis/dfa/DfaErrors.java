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
import static dev.flang.util.Errors.*;
import dev.flang.util.HasSourcePosition;


/**
 * DfaErrors handles errors found during DFA phase
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class DfaErrors extends ANY
{


  /*-----------------------------  methods  -----------------------------*/


  public static void usedEffectNotInstalled(HasSourcePosition pos, String e, Context why)
  {
    Errors.error(pos.pos(),
                 "Failed to verify that effect " + st(e) + " is installed in current environment.",
                 "Callchain that lead to this point:\n\n" + why.contextStringForEnv());
  }


  public static void readingUninitializedField(HasSourcePosition pos, String field, String clazz, Context why)
  {
    Errors.error(pos.pos(),
                 "reading uninitialized field " + sqn(field) + " from instance of " + code(clazz),
                 "Callchain that lead to this point:\n\n" + why.contextString());
  }

  public static void anyAsUsedWithIncompatibleType(HasSourcePosition pos, String origType, String newType, Context why)
  {
    Errors.error(pos.pos(),
                 "Any.as is called with incompatible types " + st(origType) + " -> " + st(newType),
                 "Original type: " + st(origType) + "\n" +
                 "New type     : " + st(newType) + "\n" +
                 "Callchain that lead to this point:\n\n" + why.contextString());
  }

  public static void fatal(String msg)
  {
    Errors.fatal(msg);
  }

}

/* end of file */
