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

import java.util.Collection;
import java.util.function.Consumer;

import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * SrcModule provides callbacks from the AST to data structures in the current
 * module, in particular to sets of features.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public interface SrcModule extends AbstractModule
{


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


  List<AbstractFeature> declaredOrInheritedFeatures(AbstractFeature outer, FeatureName fn);
  void forEachDeclaredOrInheritedFeature(AbstractFeature af, Consumer<AbstractFeature> f);
  AbstractFeature lookupFeature(AbstractFeature outer, FeatureName name, AbstractFeature original);
  void findDeclaredOrInheritedFeatures(Feature outer);
  List<FeatureAndOuter> lookup(AbstractFeature thiz, String name, Expr use, boolean traverseOuter, boolean hidden);
  AbstractFeature lookupOpenTypeParameterResult(AbstractFeature outer, Expr use);
  void checkTypes(Feature f, Context context);
  FeatureAndOuter lookupType(SourcePosition pos,
                             AbstractFeature outer,
                             String name,
                             boolean traverseOuter,
                             boolean ignoreAmbiguous,
                             boolean ignoreNotFound);

  void addCotype(AbstractFeature outerType,
                      Feature         innerType);
  void addTypeParameter(AbstractFeature outerType,
                      Feature         innerType);

  /*----------------------  methods needed by AIR  ----------------------*/

  /* NYI: cleanup: methods for AIR phase should not be defined in package ast! */

  /**
   * allInnerAndInheritedFeatures returns a complete set of inner features, used
   * by Clazz.layout and Clazz.hasState.
   *
   * @return
   */
  Collection<AbstractFeature> allInnerAndInheritedFeatures(AbstractFeature f);

}

/* end of file */
