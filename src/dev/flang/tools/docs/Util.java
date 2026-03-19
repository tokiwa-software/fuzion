/*

This file is part of the Fuzion language implementation.

The Fuzion docs generator implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion docs generator implementation is distributed in the hope that it will be
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
 * Source of class Util
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools.docs;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Types;
import dev.flang.ast.Visi;
import dev.flang.util.ANY;

public class Util extends ANY
{

  /**
   * Is the feature an argument?
   *
   * @param feature
   * @return
   */
  static boolean isArgument(AbstractFeature feature)
  {
    return feature.outer() != null &&
     feature.outer()
      .arguments()
      .stream()
      .anyMatch(f -> f.equals(feature));
  }



  /**
   * Is the feature or the type it is defining visible outside of its module?
   * @param af
   */
  public static boolean isVisible(AbstractFeature af)
  {
    return af.visibility() == Visi.PRIVPUB
        || af.visibility() == Visi.MODPUB
        || af.visibility() == Visi.PUB;
  }


  static enum Kind {
    RefConstructor,
    ValConstructor,
    Type,
    Cotype,
    Other;

    static Kind classify(AbstractFeature af) {
      return
      // NYI: does not treat features that `Type` inherits but does not redefine as type features, see #3716
        (af.outer() != null && af.outer().isCotype() ||
        (af.outer().compareTo(Types.resolved.f_Type) == 0)                    ? Kind.Cotype
        : !af.definesType()                                                   ? Kind.Other
        : af.isChoice() || af.visibility().eraseTypeVisibility() != Visi.PUB  ? Kind.Type
        : af.isRef()                                                          ? Kind.RefConstructor
                                                                              : Kind.ValConstructor);
    }
  }

}
