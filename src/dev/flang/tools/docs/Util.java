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
   * Is this a value argument of a constructor, accessible like a field?
   */
  static boolean isConstructorArgument(AbstractFeature feature)
  {
    return isArgument(feature) && feature.isField() && feature.outer().isConstructor();
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


  /**
   * Is this declaration part of an internal or anonymous implementation?
   * Public members inside such scopes are internal too. Ordinary private
   * scopes do not affect this check. Cotypes represent their original feature's
   * type namespace and are checked through that origin.
   */
  static boolean isInternalFeature(AbstractFeature af)
  {
    for (var f = af; f != null && !f.isUniverse(); f = f.outer())
      {
        if (f.isCotype())
          {
            f = f.cotypeOrigin();
          }
        if (f.featureName().isInternal()
            || f.featureName().isNameless())
          {
            return true;
          }
      }
    return false;
  }

}
