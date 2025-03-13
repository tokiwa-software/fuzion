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
 * Source of class FuirErrors
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir;

import java.util.Map;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AstErrors;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.SourcePosition;


/**
 * FuirErrors handles errors in the Application IR
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FuirErrors extends AstErrors
{

  /*--------------------------  static fields  --------------------------*/

  /**
   * Error count of only those errors that occurred in the IR.
   */
  static int count = 0;
  public static int count() { return count; }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Record the given error found during compilation.
   */
  public static void error(SourcePosition pos, String msg, String detail)
  {
    if (PRECONDITIONS) require
      (pos != null,
       msg != null,
       detail != null);

    int old = Errors.count();
    Errors.error(pos, msg, detail);
    int delta = Errors.count() - old;  // Errors detects duplicates, so the count might not have changed.
    count += delta;
  }

  public static void abstractFeatureNotImplemented(AbstractFeature featureThatDoesNotImplementAbstract,
                                                   Map<AbstractFeature, String> abstractFeature,
                                                   HasSourcePosition instantiatedAt,
                                                   String context)
  {
    if (PRECONDITIONS) require
      (!abstractFeature.isEmpty());

    var abs = new StringBuilder();
    var abstracts = new StringBuilder();
    var foundAbstract = false;
    var foundFixed = false;
    for (var af : abstractFeature.keySet())
      {
        foundAbstract |= af.isAbstract();
        foundFixed    |= (af.modifiers() & FuzionConstants.MODIFIER_FIXED) != 0;
      }
    if (CHECKS) check
      (foundAbstract || foundFixed);
    var kind =
      foundAbstract && foundFixed ? "abstract or fixed" :
      foundAbstract               ? "abstract"          :
      foundFixed                  ? "fixed"             : Errors.ERROR_STRING;
    for (var af : abstractFeature.keySet())
      {
        var calledAt = abstractFeature.get(af);
        abs.append(abs.length() == 0 ? "" : ", ").append(s(af));
        var afKind = af.isAbstract() ? "abstract" : "fixed";
        abstracts.append((abstracts.length() == 0 ? "inherits or declares" : "and") + " " + afKind + " feature " +
                         s(af) + " declared at " + af.pos().show() + "\n" +
                         "which is called at " + calledAt + "\n");
      }
    abstracts.append("without providing an implementation\n");
    error(featureThatDoesNotImplementAbstract.pos(),
          "Used " + kind + " " + (abstractFeature.size() > 1 ? "features " + abs + " are" : "feature " + abs + " is") + " not implemented by "+s(featureThatDoesNotImplementAbstract),
          "Feature " + s(featureThatDoesNotImplementAbstract) + " " +
          "instantiated at " + instantiatedAt.pos().show() + "\n" +
          abstracts + "\n" +
          "Callchain that lead to this point:\n\n" + context);
  }

}

/* end of file */
