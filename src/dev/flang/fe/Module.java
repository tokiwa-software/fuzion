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
import dev.flang.ast.FeatureName;
import dev.flang.ast.Types;
import dev.flang.ast.Visi;

import dev.flang.mir.MIR;
import dev.flang.mir.MirModule;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourceFile;

import java.util.Arrays;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Stream;


/**
 * A Module represents a Fuzion module independently of whether this is loaded
 * from source code, library from a .fum file or downloaded from the web.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class Module extends ANY implements FeatureLookup
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
    SortedMap<FeatureName, List<AbstractFeature>> _declaredOrInheritedFeatures;

    /**
     * All features that have been found to inherit from this feature.  This set
     * is collected during RESOLVING_DECLARATIONS.
     */
    Set<AbstractFeature> _heirs = new TreeSet<>();

  }


  /*----------------------------  variables  ----------------------------*/


  /**
   * What modules does this module depend on?
   */
  LibraryModule[] _dependsOn;


  /*--------------------------  static methods  -------------------------*/


  /**
   * From the given map s, get the list of entries for given FeatureName. Will
   * return an empty list if no mapping was found.
   *
   * @param s a set of features
   *
   * @param fn a name we are looking for
   *
   * @return the list of features store for {@code fn}, never null.
   */
  protected static List<AbstractFeature> get(SortedMap<FeatureName, List<AbstractFeature>> s, FeatureName fn)
  {
    var result = s.get(fn);
    return result == null ? AbstractFeature._NO_FEATURES_ : result;
  }


  /**
   * Add feature {@code f} for name {@code fn} to the map {@code s}. If a mapping exists that does
   * not contain {@code f}, add {@code f} to the existing mapping.  Otherwise, create a new
   * mapping that only contains {@code f}.
   *
   * @param s a set of features we are modifying.
   *
   * @param fn a name we want to map to {@code f}. Note that {@code fn} might be different
   * to {@code f.featureName()}.
   *
   * @param f a feature.
   */
  protected static void add(SortedMap<FeatureName, List<AbstractFeature>> s, FeatureName fn, AbstractFeature f)
  {
    var l = s.get(fn);
    if (l == null)
      {
        l = new List<>();
        s.put(fn, l);
      }
    if (!l.stream().anyMatch(x->x==f))
      {
        l.add(f);
      }
  }


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create Module for given options and sourceDirs.
   */
  Module(LibraryModule[] dependsOn)
  {
    _dependsOn = dependsOn;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Get declared features for given outer Feature as seen by this module.
   * Result is null if outer has no declared features in this module.
   *
   * @param outer the declaring feature
   */
  public abstract SortedMap<FeatureName, AbstractFeature>declaredFeatures(AbstractFeature outer);


  /**
   * The name of this module, e.g. base
   */
  abstract String name();


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
   * NYI: CLEANUP: See #462: Remove once sub-directories are loaded
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
   *
   * @param modules the additional modules where we should look for declared or inherited features
   */
  void findInheritedFeatures(SortedMap<FeatureName, List<AbstractFeature>> set, AbstractFeature outer, Module[] modules)
  {
    for (var p : outer.inherits())
      {
        var cf = p.calledFeature();
        if (CHECKS) check
          (Errors.any() || (cf != null && cf != Types.f_ERROR));

        if (cf != null && cf != Types.f_ERROR && (cf.isConstructor() || cf.isChoice()))
          {
            data(cf)._heirs.add(outer);
            resolveDeclarations(cf);

            for (var fnf : declaredOrInheritedFeatures(cf, modules).entrySet())
              {
                var fn = fnf.getKey();
                for (var f : fnf.getValue())
                  {
                    if (CHECKS) check
                      (cf != outer);

                    var res = this instanceof SourceModule sm ? sm._res : null;
                    if (!f.isFixed())
                      {
                        var newfn = cf.handDown(res, f, fn, p, outer);
                        addDeclaredOrInherited(set, outer, newfn, f);
                      }
                    else
                      {
                        for (var f2 : f.redefines())
                          {
                            var newfn = cf.handDown(res, f2, fn, p, outer);
                            addDeclaredOrInherited(set, outer, newfn, f2);
                          }
                      }
                  }
              }
          }
      }
  }


  /**
   * Helper method to add a feature that this feature declares or inherits.
   *
   * @param set set of declared or inherited features.
   *
   * @param outer the declaring feature or the outer feature that inherits f
   *
   * @param fn the name of the feature, after possible renaming during inheritance
   *
   * @param f the feature to be added.
   */
  protected void addDeclaredOrInherited(SortedMap<FeatureName, List<AbstractFeature>> set, AbstractFeature outer, FeatureName fn, AbstractFeature f)
  {
    if (PRECONDITIONS)
      require(Errors.any() || !f.isFixed() || outer == f.outer());

    var it = get(set, fn).listIterator();
    while (f != null && it.hasNext())
      {
        var existing = it.next();
        if (f != existing)
          {
            if (redefines(f, existing))
              {
                it.remove();
              }
            else if (redefines(existing, f))
              {
                f = null;
              }
          }
      }
    if (f != null)
      {
        add(set, fn, f);
      }
  }


  /**
   * Does f1 redefine f2?
   */
  private boolean redefines(AbstractFeature f1, AbstractFeature f2)
  {
    return this instanceof SourceModule  && f1.redefines().contains(f2) ||
        !(this instanceof SourceModule) && f1.outer().inheritsFrom(f2.outer()); // NYI: CLEANUP: #478: better check f1.redefines(f2)
  }


  /**
   * Is type defined by feature {@code af} visible in file {@code usedIn}?
   * If {@code af} does not define a type, result is false.
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
   * Does this qualify for compiler generated code, e.g. array initialization?
   *
   * @param usedIn
   * @param m
   * @param v
   * @return
   */
  private boolean isCompilerGeneratedCode(SourceFile usedIn, Module m, Visi v)
  {
    return SourceFile._builtIn_ == usedIn
      && v.ordinal() >= Visi.MOD.ordinal()
      && m.name().equals(FuzionConstants.BASE_MODULE_NAME);
  }


  /**
   * Is type defined by feature {@code af} visible in file {@code usedIn}?
   * If {@code af} does not define a type, result is false.
   *
   * @param usedIn
   * @param af
   * @param ignoreDefinesType leave checking whether {@code af} defines a type to the caller
   * @return
   */
  protected boolean typeVisible(SourceFile usedIn, AbstractFeature af, boolean ignoreDefinesType)
  {
    var m = (af instanceof LibraryFeature lf) ? lf._libModule : this;
    var definedIn = af.pos()._sourceFile;
    var v = af.visibility();
    var tv = af.visibility().typeVisibility();

    return isCompilerGeneratedCode(usedIn, m, v)
      || (usedIn.sameAs(definedIn) || tv == Visi.MOD && this == m || tv == Visi.PUB)
        && (af.definesType() || ignoreDefinesType);
  }


  /**
   * Is feature {@code af} visible in file {@code usedIn}?
   * @param usedIn
   * @param af
   * @return
   */
  protected boolean featureVisible(SourceFile usedIn, AbstractFeature af)
  {
    var m = (af instanceof LibraryFeature lf) ? lf._libModule : this;
    var definedIn = af.pos()._sourceFile;
    var v = af.visibility();

    return isCompilerGeneratedCode(usedIn, m, v)
            // built-in or generated features like #loop0
            || af.pos().isBuiltIn()
            // in same file
            || ((usedIn.sameAs(definedIn)
            // at least module visible and in same module
            || v.ordinal() >= Visi.MOD.ordinal() && this == m
            // publicly visible
            || v == Visi.PUB));
  }


  /**
   * Is {@code a} visible for feature {@code b}?
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
   *
   * @param modules the additional modules where we should look for declared or inherited features
   *
   * @return the map of names within outer and corresponding features. Never null.
   */
  private SortedMap<FeatureName, List<AbstractFeature>> declaredOrInheritedFeatures(AbstractFeature outer, Module[] modules)
  {
    var d = data(outer);
    var s = d._declaredOrInheritedFeatures;
    if (s == null)
      {
        s = new TreeMap<>();

        if (outer instanceof LibraryFeature olf)
          {
            // NYI: CLEANUP: See #462: Remove once sub-directories are loaded
            // directly, not implicitly when outer feature is found
            loadInnerFeatures(outer);

            for (var f : olf.declaredFeatures())
              {
                add(s, f.featureName(), f);
              }
          }

        // This is need for "extension" features
        // that are declared in different modules
        // than the outer feature:
        // e.g. `Any.dt => Any.this.type`
        var selfAndModules = Stream
          .concat(Arrays.stream(modules), Stream.of(this))
          .toArray(Module[]::new);

        // first we search in additional modules
        if (outer instanceof LibraryFeature)
          {
            for (Module libraryModule : modules)
              {
                for (var e : libraryModule.declaredFeatures(outer).entrySet())
                  {
                    addDeclaredOrInherited(s, outer, e.getKey(), e.getValue());
                  }
                libraryModule.findInheritedFeatures(s, outer, selfAndModules);
              }
          }

        // then we search in this module
        for (var e : declaredFeatures(outer).entrySet())
          {
            addDeclaredOrInherited(s, outer, e.getKey(), e.getValue());
          }

        d._declaredOrInheritedFeatures = s;
      }
    return s;
  }


  /**
   * Get declared and inherited features for given outer Feature as seen by this
   * module.  Result is never null.
   *
   * @param outer the declaring feature
   *
   * @return the map of names within outer and corresponding features. Never null.
   */
  SortedMap<FeatureName, List<AbstractFeature>> declaredOrInheritedFeatures(AbstractFeature outer)
  {
    return this.declaredOrInheritedFeatures(outer, _dependsOn);
  }


  /**
   * Get declared and inherited features with given effective name for given
   * outer Feature as seen by this module.  Result is never null.
   *
   * @param outer the declaring feature
   *
   * @param fn the effective name in outer.
   *
   * @return the list of features in outer for name fn, never null.
   */
  public List<AbstractFeature> declaredOrInheritedFeatures(AbstractFeature outer, FeatureName fn)
  {
    var s = declaredOrInheritedFeatures(outer);
    var l = s.get(fn);
    return l == null ? AbstractFeature._NO_FEATURES_ : l;
  }


  /**
   * Helper to apply given function to all declared or inherited features of this feature.
   *
   * @param af a feature as seen from this module
   *
   * @param fun operation to apply to all declared or inherited features of af.
   */
  public void forEachDeclaredOrInheritedFeature(AbstractFeature af, Consumer<AbstractFeature> fun)
  {
    for (var l: new List<List<AbstractFeature>>(declaredOrInheritedFeatures(af).values().iterator()))
      {
        l.forEach(fun);
      }
  }


  /**
   * Find feature with given name in outer.
   *
   * @param outer the declaring or inheriting feature
   */
  @Override
  public AbstractFeature lookupFeature(AbstractFeature outer, FeatureName name)
  {
    return declaredOrInheritedFeatures(outer, name).getFirstOrNull();
  }


  /**
   * Create MIR based on given main feature.
   */
  static MIR createMIR(MirModule mirMod, AbstractFeature universe, AbstractFeature main)
  {
    var result = new MIR(universe, main, mirMod);
    if (!Errors.any())
      {
        new DFA(result).check();
      }

    return result;
  }


}

/* end of file */
