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
import dev.flang.ast.Consts;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.Visi;

import dev.flang.mir.MIR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.SourceFile;

import java.util.Set;
import java.util.SortedMap;
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
     * Cached result of SourceModule.allInnerAndInheritedFeatures().
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
   * NYI: cleanup: See #462: Remove once sub-directories are loaded
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
          (Errors.any() || cf != null);

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

                if ((f.modifiers() & Consts.MODIFIER_FIXED) == 0)
                  {
                    var newfn = cf.handDown(null, f, fn, p, outer);
                    addInheritedFeature(set, outer, p, newfn, f);
                  }
                else
                  {
                    for (var f2 : f.redefines())
                      {
                        var newfn = cf.handDown(null, f2, fn, p, outer);
                        addInheritedFeature(set, outer, p, newfn, f2);
                      }
                  }
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
        else if (existing == f && !existing.generics().equals(f.generics()) ||
                 existing != f && declaredFeatures(outer).get(fn) == null)
          { // NYI: Should be ok if existing or f is abstract.
            AstErrors.repeatedInheritanceCannotBeResolved(outer.pos(), outer, fn, existing, f);
          }
      }
    set.put(fn, f);
  }


  /**
   * Is type defined by feature `af` visible in file `usedIn`?
   * If `af` does not define a type, result is false.
   *
   * @param usedIn
   * @param af
   * @return
   */
  protected boolean typeVisible(SourceFile usedIn, AbstractFeature af)
  {
    return typeVisible(usedIn, af, false);
  }


  /**
   * Is type defined by feature `af` visible in file `usedIn`?
   * If `af` does not define a type, result is false.
   *
   * @param usedIn
   * @param af
   * @param ignoreDefinesType leave checking whether `af` defines a type to the caller
   * @return
   */
  protected boolean typeVisible(SourceFile usedIn, AbstractFeature af, boolean ignoreDefinesType)
  {
    var m = (af instanceof LibraryFeature lf) ? lf._libModule : this;
    var definedIn = af.pos()._sourceFile;
    var v = af.visibility();
    var definesType = af.definesType() || ignoreDefinesType;

    return definesType && (usedIn.sameAs(definedIn)
      || (v == Visi.PRIVMOD || v == Visi.MOD) && this == m
      || v == Visi.PRIVPUB || v == Visi.MODPUB ||  v == Visi.PUB);
  }


  /**
   * Is feature `af` visible in file `usedIn`?
   * @param usedIn
   * @param af
   * @return
   */
  protected boolean featureVisible(SourceFile usedIn, AbstractFeature af)
  {
    var m = (af instanceof LibraryFeature lf) ? lf._libModule : this;
    var definedIn = af.pos()._sourceFile;
    var v = af.visibility();

          // in same file
    return ((usedIn.sameAs(definedIn)
          // at least module visible and in same module
          || v.ordinal() >= Visi.MOD.ordinal() && this == m
          // publicly visible
          || v == Visi.PUB));
  }


  /**
   * Is `a` visible for feature `b`?
   *
   * @param a
   * @param b
   * @return
   */
  protected boolean visibleFor(AbstractFeature a, AbstractFeature b)
  {
    var usedIn = b.pos()._sourceFile;
    return featureVisible(usedIn, a) || typeVisible(usedIn, a);
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
            // NYI: cleanup: See #462: Remove once sub-directories are loaded
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
                    if (existing != null && f != existing)
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
