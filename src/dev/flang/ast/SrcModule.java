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

import java.util.Set;
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
   * Get declared features for given outer Feature as seen by this module.
   * Result is never null.
   */
  SortedMap<FeatureName, AbstractFeature>declaredFeatures(AbstractFeature outer);


  /**
   * During resolution, load all inner classes of this that are
   * defined in separate files.
   */
  void loadInnerFeatures(Feature f);

  /**
   * Find all the inner feature declarations within this feature and set
   * this._outer and, recursively, the outer references of all inner features to
   * the corresponding outer declaring feature.
   *
   * @param inner the feature whose inner features should be found.
   *
   * @param outer the root feature that declares this feature.  For
   * all found feature declarations, the outer feature will be set to
   * this value.
   */
  void findDeclarations(Feature inner, AbstractFeature outer);


  SortedMap<FeatureName, AbstractFeature> declaredOrInheritedFeatures(AbstractFeature outer);
  AbstractFeature lookupFeature(AbstractFeature outer, FeatureName name);
  void findDeclaredOrInheritedFeatures(Feature outer);
  SortedMap<FeatureName, AbstractFeature> lookupFeatures(AbstractFeature outer, String name);
  FeaturesAndOuter lookupNoTarget(AbstractFeature thiz, String name, Call call, Assign assign, Destructure destructure);
  void checkTypes(Feature f);


  /**
   * Add given feature to the features declared in this SrcModule.
   */
  void add(Feature f);

}

/* end of file */
