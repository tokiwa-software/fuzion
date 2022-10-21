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
import dev.flang.util.Errors;
import dev.flang.util.HasSourcePosition;

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
  public abstract SortedMap<FeatureName, AbstractFeature>declaredFeatures(AbstractFeature outer);


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
   * During resolution, load all inner features of f that are defined in
   * separate files within this module.
   *
   * NYI: cleanup: See #462: Remove once sub-directries are loaded
   * directly, not implicitly when outer feature is found
   *
   * @param f the outer feature whose inner features should be loaded from
   * source files in sub directories.
   */
  void loadInnerFeatures(AbstractFeature f)
  { // this is a nop for all but SourceModules.
  }


  /**
   * For a SourceModule, resolve all declarations of inner features of f.
   *
   * @param f a feature.
   */
  void resolveDeclarations(AbstractFeature f)
  {
    // nothing to be done, code is only in SourceModule.resolveDeclarations.
  }


  /**
   * Find all inherited features and add them to given set.  In case an existing
   * feature was found, check if there is a conflict and if so, report an error
   * message (repeated inheritance).
   *
   * @param set the set to add inherited features to
   *
   * @param outer the inheriting feature
   */
  void findInheritedFeatures(SortedMap<FeatureName, AbstractFeature> set, AbstractFeature outer)
  {
    for (var p : outer.inherits())
      {
        var cf = p.calledFeature();
        if (CHECKS) check
          (Errors.count() > 0 || cf != null);

        if (cf != null && (cf.isConstructor() || cf.isChoice()))
          {
            data(cf)._heirs.add(outer);
            resolveDeclarations(cf);

            for (var fnf : declaredOrInheritedFeatures(cf).entrySet())
              {
                var fn = fnf.getKey();
                var f = fnf.getValue();
                if (CHECKS) check
                  (cf != outer);

                var newfn = cf.handDown(null, f, fn, p, outer);
                addInheritedFeature(set, outer, p, newfn, f);
              }
          }
      }
  }


  /**
   * Helper method for findInheritedFeatures and addToHeirs to add a feature
   * that this feature inherits.
   *
   * @param set the set to add inherited features to
   *
   * @param outer the outer feature that inherits f
   *
   * @param pos the source code position of the inherits call responsible for
   * the inheritance.
   *
   * @param fn the name of the feature, after possible renaming during inheritance
   *
   * @param f the feature to be added.
   */
  void addInheritedFeature(SortedMap<FeatureName, AbstractFeature> set,
                           AbstractFeature outer,
                           HasSourcePosition pos,
                           FeatureName fn,
                           AbstractFeature f)
  {
    var existing = set.get(fn);
    if (existing != null)
      {
        if (  this instanceof SourceModule  && f.redefines().contains(existing) ||
            !(this instanceof SourceModule) && f.outer().inheritsFrom(existing.outer()))  // NYI: cleanup: #478: better check f.redefines(existing)
          { // f redefined existing, so we are fine
          }
        else if (  this instanceof SourceModule  && existing.redefines().contains(f) ||
                 !(this instanceof SourceModule) && existing.outer().inheritsFrom(f.outer()))  // NYI: cleanup: #478: better check existing.redefines(f)
          { // existing redefines f, so use existing
            f = existing;
          }
        else if (existing == f && f.generics() != FormalGenerics.NONE ||
                 existing != f && declaredFeatures(outer).get(fn) == null)
          { // NYI: Should be ok if existing or f is abstract.
            AstErrors.repeatedInheritanceCannotBeResolved(outer.pos(), outer, fn, existing, f);
          }
      }
    set.put(fn, f);
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
            // NYI: cleanup: See #462: Remove once sub-directries are loaded
            // directly, not implicitly when outer feature is found
            loadInnerFeatures(outer);

            s = olf._libModule.declaredFeatures(olf);
            olf._libModule.findInheritedFeatures(s, olf);
            for (var e : declaredFeatures(outer).entrySet())
              {
                var f = e.getValue();
                if (!(f instanceof LibraryFeature flf && flf._libModule == olf._libModule))
                  { // f is a qualified feature that was added in a different module
                    var fn = f.featureName();
                    var existing = s.get(fn);
                    // NYI: We need proper visibility handling, e.g., it might
                    // be ok to have
                    //
                    // * modules 'A', 'B', 'C' where 'A' declares 'a' with a
                    //   private feature 'a.f' and 'B' adds its own 'a.f' used
                    //   by 'C' that depends on 'B'
                    //
                    // * modules 'A', 'B', 'C', 'D' where 'A' declares 'a' with
                    //   no inner feature 'a.f', but both B and C declare
                    //   different 'a.f' that are visible to but not used by 'D'
                    //
                    // * same as previous, but there is some syntax for 'D' to
                    //   chose 'a.[B].f' or 'a.[C].f'.
                    //
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
        else
          {
            s = declaredFeatures(outer);
          }
        // NYI: cleanup: See #479: there are two places that initialize
        // _declaredOrInheritedFeatures: this place and
        // SourceModule.findDeclaredOrInheritedFeatures(). There should be only one!
        d._declaredOrInheritedFeatures = s;
      }
    return s;
  }

}

/* end of file */
