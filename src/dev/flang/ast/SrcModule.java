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
 * Source of interface SrcModule
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.SortedMap;


/**
 * SrcModule provides callbacks from the AST to data structures in the current
 * module, in particular to sets of features.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public interface SrcModule
{

  /**
   * Add inner to the set of declared inner features of outer using the given
   * feature name fn.
   *
   * Note that inner must be declared in this module, but outer may be defined
   * in a different module.  E.g. #universe is declared in stdlib, while an
   * inner feature 'main' may be declared in the application's module.
   *
   * @param outer the declaring feature
   *
   * @param fn the name of the declared feature
   *
   * @param inner the inner feature.
   */
  void addDeclaredInnerFeature(Feature outer, FeatureName fn, Feature inner);


  /**
   * Get declared features for given outer Feature as seen by this module.
   * Result is never null.
   */
  SortedMap<FeatureName, Feature>declaredFeatures(Feature outer);


  /**
   * During resolution, load all inner classes of this that are
   * defined in separate files.
   */
  void loadInnerFeatures(Feature f);

  /**
   * Find all the inner feature declarations within this feature and set
   * this.outer_ and, recursively, the outer_ references of all inner features to
   * the corresponding outer declaring feature.
   *
   * @param inner the feature whose inner features should be found.
   *
   * @param outer the root feature that declares this feature.  For
   * all found feature declarations, the outer feature will be set to
   * this value.
   */
  void findDeclarations(Feature inner, Feature outer);


}

/* end of file */
