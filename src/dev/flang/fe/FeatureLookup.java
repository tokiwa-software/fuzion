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
 * Source of interface FeatureLookup
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;


import dev.flang.ast.AbstractFeature;
import dev.flang.ast.FeatureName;


/**
 * Interface for looking up features that is used by middle end.
 */
public interface FeatureLookup {


  /**
   * Find feature with given name in outer.
   *
   * @param outer the declaring or inheriting feature
   *
   * @param name the feature name that we are searching for
   */
  public AbstractFeature lookupFeature(AbstractFeature outer, FeatureName name);


  /**
   * Find the redefinition of feature f within heir.
   *
   * @param heir a feature that inherits from f.outer(), directly or
   *             indirectly.
   *
   * @param f    a feature declared in or inherited by f.outer()
   *
   * @return the feature in heir that redefines f, or f itself if f is not
   *         redefined in heir.
   */
  public AbstractFeature findRedefinition(AbstractFeature heir, AbstractFeature f);


}
