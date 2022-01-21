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
 * Source of class Module
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AstErrors;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.FormalGenerics;

import dev.flang.mir.MIR;

import dev.flang.util.ANY;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;


/**
 * A Module represents a Fuzion module independently of whether this is loaded
 * from source code, library from a .mir file or downloaded from the web.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class Module extends ANY
{



  /*-----------------------------  classes  -----------------------------*/


  /**
   * Data stored locally to a Feature.
   */
  static class FData
  {

    /**
     * Features declared inside a feature. The inner features are mapped from
     * their FeatureName.
     */
    SortedMap<FeatureName, AbstractFeature> _declaredFeatures;

    /**
     * Features declared inside a feature or inherited from its parents.
     */
    SortedMap<FeatureName, AbstractFeature> _declaredOrInheritedFeatures;

    /**
     * All features that have been found to inherit from this feature.  This set
     * is collected during RESOLVING_DECLARATIONS.
     */
    Set<AbstractFeature> _heirs = new TreeSet<>();


    /**
     * All features that have been found to directly redefine this feature. This
     * does not include redefintions of redefinitions.  This set is collected
     * during RESOLVING_DECLARATIONS.
     */
    Set<AbstractFeature> _redefinitions = null;

    /**
     * Cached result of SourceModule.allInnerandinheritedfeatures().
     */
    Set<AbstractFeature> _allInnerAndInheritedFeatures = null;

    /**
     * offset of this feature's data in .mir file.
     */
    int _mirOffset = -1;

  }


  /*----------------------------  variables  ----------------------------*/


  /**
   * What modules does this module depend on?
   */
  LibraryModule[] _dependsOn;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create SourceModule for given options and sourceDirs.
   */
  Module(LibraryModule[] dependsOn)
  {
    _dependsOn = dependsOn;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create the module intermediate representation for this module.
   */
  public abstract MIR createMIR();


  /**
   * Get declared features for given outer Feature as seen by this module.
   * Result is null if outer has no declared features in this module.
   *
   * @param outer the declaring feature
   */
  abstract SortedMap<FeatureName, AbstractFeature>declaredFeatures(AbstractFeature outer);


  /**
   * Get declared and inherited features for given outer Feature as seen by this
   * module.  Result may be null if this module does not contribute anything to
   * outer.
   *
   * @param outer the declaring feature
   */
  abstract SortedMap<FeatureName, AbstractFeature>declaredOrInheritedFeaturesOrNull(AbstractFeature outer);


  /**
   * Get or create the data record for given outer feature.
   *
   * @param outer the feature we need to get the data record from.
   */
  FData data(AbstractFeature outer)
  {
    var d = (FData) outer._frontEndData;
    if (d == null)
      {
        d = new FData();
        outer._frontEndData = d;
      }
    return d;
  }


  /**
   * Get declared and inherited features for given outer Feature as seen by this
   * module.  Result is never null.
   *
   * @param outer the declaring feature
   */
  public SortedMap<FeatureName, AbstractFeature> declaredOrInheritedFeatures(AbstractFeature outer)
  {
    if (PRECONDITIONS) require
      (!(outer instanceof Feature of) || of.state().atLeast(Feature.State.RESOLVING_DECLARATIONS) || of.isUniverse());

    var d = data(outer);
    var s = d._declaredOrInheritedFeatures;
    if (s == null)
      {
        if (outer instanceof LibraryFeature olf)
          {
            s = olf._libModule.declaredOrInheritedFeaturesOrNull(outer);
            if (s == null)
              {
                s = new TreeMap<>();
              }
            for (var e : declaredFeatures(outer).entrySet())
              {
                if (e.getValue() instanceof Feature f)
                  { // f is a qualified feature that was added as source code
                    var fn = f.featureName();
                    var existing = s.get(fn);
                    if (existing != null)
                      {
                        AstErrors.duplicateFeatureDeclaration(f.pos(), outer, s.get(fn));
                      }
                    else
                      {
                        s.put(f.featureName(), f);
                      }
                  }
              }
          }
        else if (outer.isUniverse())
          {
            s = declaredFeatures(outer);
          }
        else
          {
            s = new TreeMap<>();
            for (Module m : _dependsOn)
              { // NYI: properly obtain set of declared features from m, do we need
                // to take care for the order and dependencies between modules?
                var md = m.declaredOrInheritedFeaturesOrNull(outer);
                if (md != null)
                  {
                    for (var e : md.entrySet())
                      {
                        s.put(e.getKey(), e.getValue());
                      }
                  }
              }
          }
        d._declaredOrInheritedFeatures = s;
      }
    return s;
  }

}

/* end of file */
