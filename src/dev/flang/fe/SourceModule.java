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
 * Source of class SourceModule
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import java.io.IOException;
import java.io.UncheckedIOException;

import java.nio.ByteBuffer;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.ast.AbstractAssign;
import dev.flang.ast.AbstractBlock;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.AstErrors;
import dev.flang.ast.Block;
import dev.flang.ast.Call;
import dev.flang.ast.Consts;
import dev.flang.ast.Destructure;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.FeatureAndOuter;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.FormalGenerics;
import dev.flang.ast.Impl;
import dev.flang.ast.Resolution;
import dev.flang.ast.SrcModule;
import dev.flang.ast.Stmnt;
import dev.flang.ast.Type;
import dev.flang.ast.Types;

import dev.flang.mir.MIR;
import dev.flang.mir.MirModule;

import dev.flang.parser.Parser;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourceDir;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;


/**
 * A SourceModule represents a Fuzion module created directly from source code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class SourceModule extends Module implements SrcModule, MirModule
{

  /*----------------------------  variables  ----------------------------*/


  /**
   * Configuration
   */
  public final FrontEndOptions _options;


  /**
   * All the directories we are reading Fuzion sources form.
   */
  private final SourceDir[] _sourceDirs;


  /**
   * If input comes from a specific file, this give the file.  May be
   * SourceFile.STDIN.
   */
  private final Path _inputFile;


  /**
   * The universe is the implicit root of all features that
   * themselves do not have their own root.
   */
  final Feature _universe;


  /**
   * If a main feature is defined for this module, this gives its name. Should
   * be null if a specific _inputFile defines the main feature.
   */
  private String _defaultMain;


  /**
   * Flag to forbid loading of source code for new features for this module once
   * MIR was created.
   */
  private boolean _closed = false;


  /**
   * In case this module defines a main feature, this is its fully qualified
   * name.
   */
  String _main;

  Resolution _res;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create SourceModule for given options and sourceDirs.
   */
  SourceModule(FrontEndOptions options, SourceDir[] sourceDirs, Path inputFile, String defaultMain, LibraryModule[] dependsOn, Feature universe)
  {
    super(dependsOn);

    _options = options;
    _sourceDirs = sourceDirs;
    _inputFile = inputFile;
    _defaultMain = defaultMain;
    _universe = universe;
  }


  /*-----------------------------  methods  -----------------------------*/


  /*---------------------------  main control  --------------------------*/


  /**
   * If source comes from stdin or an explicit input file, parse this and
   * extract the main feature.  Otherwise, return the default main.
   *
   * @return the main feature found or null if none
   */
  String parseMain()
  {
    var res = _defaultMain;
    var p = _inputFile;
    if (p != null)
      {
        var stmnts = parseFile(p);
        ((AbstractBlock) _universe.code())._statements.addAll(stmnts);
        for (var s : stmnts)
          {
            if (s instanceof Feature f)
              {
                f.legalPartOfUniverse();  // suppress FeErrors.initialValueNotAllowed
                if (stmnts.size() == 1)
                  {
                    res =  f.featureName().baseName();
                  }
              }
          }
      }
    return res;
  }


  /**
   * Load and parse the given Fuzion source file.
   *
   * @param p path of the file.
   *
   * @return the features found in source file p, may be empty, never null.
   */
  List<Stmnt> parseFile(Path p)
  {
    _options.verbosePrintln(2, " - " + p);
    return new Parser(p).unit();
  }


  /**
   * Load and parse the given Fuzion source file and return its features.
   *
   * @param p path of the file.
   *
   * @return the features found in source file p, may be empty, never null.
   */
  List<Feature> parseAndGetFeatures(Path p)
  {
    var stmnts = parseFile(p);
    var result = new List<Feature>();
    for (var s : stmnts)
      {
        if (s instanceof Feature f)
          {
            result.add(f);
          }
        else if (Errors.count() == 0)
          {
            AstErrors.statementNotAllowedOutsideOfFeatureDeclaration(s);
          }
      }
    return result;
  }


  /**
   * Create the abstract syntax tree and resolve all features.
   */
  void createASTandResolve()
  {
    if (CHECKS) check
      (_universe != null);

    _res = new Resolution(_options, _universe, this);
    if (_dependsOn.length > 0)
      {
        _universe.setState(Feature.State.RESOLVED);
        var stdlib = _dependsOn[0];
        new Types.Resolved(this,
                           (name, ref) ->
                             {
                               var f = lookupType(SourcePosition.builtIn, _universe, name, false);
                               return new NormalType(stdlib,
                                                     -1,
                                                     SourcePosition.builtIn,
                                                     f,
                                                     ref || f.isThisRef() ? FuzionConstants.MIR_FILE_TYPE_IS_REF : FuzionConstants.MIR_FILE_TYPE_IS_VALUE,
                                                     Type.NONE,
                                                     _universe.thisType());
                             },
                           _universe);
      }

    _main = parseMain();
    findDeclarations(_universe, null);
    _universe.scheduleForResolution(_res);
    _res.resolve();
  }


  /**
   * Create the module intermediate representation for this module.
   */
  public MIR createMIR()
  {
    var d = _main == null
      ? _universe
      : _universe.get(this, _main);

    if (false)  // NYI: Eventually, we might want to stop here in case of errors. This is disabled just to check the robustness of the next steps
      {
        Errors.showAndExit();
      }

    _closed = true;
    return createMIR(d);
  }



  /**
   * Create MIR based on given main feature.
   */
  MIR createMIR(AbstractFeature main)
  {
    if (main != null && Errors.count() == 0)
      {
        if (main.valueArguments().size() != 0)
          {
            FeErrors.mainFeatureMustNotHaveArguments(main);
          }
        switch (main.kind())
          {
          case Field    : FeErrors.mainFeatureMustNotBeField    (main); break;
          case Abstract : FeErrors.mainFeatureMustNotBeAbstract (main); break;
          case Intrinsic: FeErrors.mainFeatureMustNotBeIntrinsic(main); break;
          case Choice   : FeErrors.mainFeatureMustNotBeChoice   (main); break;
          case Routine:
            if (!main.generics().list.isEmpty())
              {
                FeErrors.mainFeatureMustNotHaveTypeArguments(main);
              }
          }
      }
    var result = new MIR(_universe, main, this);
    if (Errors.count() == 0)
      {
        new DFA(result).check();
      }

    return result;
  }



  /**
   * Check if a sub-directory corresponding to the given feature exists in the
   * source directory with the given root.
   *
   * @param root the top-level directory of the source directory
   *
   * @param f a feature
   *
   * @return a path from root, via the base names of f's outer features to a
   * directory with f's base name, null if this does not exist.
   */
  private SourceDir dirExists(SourceDir root, AbstractFeature f) throws IOException, UncheckedIOException
  {
    var o = f.outer();
    if (o == null)
      {
        return root;
      }
    else
      {
        var d = dirExists(root, o);
        return d == null ? null : d.dir(f.featureName().baseName());
      }
  }


  /**
   * Check if p denotes a file that should be read implicitly as source code,
   * i.e., its name ends with ".fz", it is a readable file and it is not the
   * same as _inputFile (which will be read explicitly).
   */
  boolean isValidSourceFile(Path p)
  {
    try
      {
        return p.getFileName().toString().endsWith(".fz") &&
          !Files.isDirectory(p) &&
          Files.isReadable(p) &&
          (_inputFile == null || _inputFile == SourceFile.STDIN || !Files.isSameFile(_inputFile, p));
      }
    catch (IOException e)
      {
        throw new UncheckedIOException(e);
      }
  }


  /**
   * During resolution, load all inner classes of this that are
   * defined in separate files.
   */
  void loadInnerFeatures(AbstractFeature f)
  {
    if (!f._loadedInner &&
        !_closed)  /* NYI: restrict this to f.isVisibleFrom(this) or similar */
      {
        f._loadedInner = true;
        for (var root : _sourceDirs)
          {
            try
              {
                var d = dirExists(root, f);
                if (d != null)
                  {
                    Files.list(d._dir)
                      .filter(p -> isValidSourceFile(p))
                      .sorted()
                      .forEach(p ->
                               {
                                 for (var inner : parseAndGetFeatures(p))
                                   {
                                     findDeclarations(inner, f);
                                     if (inner.state().atLeast(Feature.State.LOADED))
                                       {
                                         inner.scheduleForResolution(_res);
                                       }
                                   }
                               });
                  }
              }
            catch (IOException | UncheckedIOException e)
              {
                Errors.warning("Problem when listing source directory '" + root._dir + "': " + e);
              }
          }
      }
  }


  /**
   * For a SourceModule, resolve all declarations of inner features of f.
   *
   * @param f a feature.
   */
  void resolveDeclarations(AbstractFeature f)
  {
    _res.resolveDeclarations(f);
  }


  /*---------------------  collecting data from AST  --------------------*/


  /**
   * Find all the inner feature declarations within this feature and set
   * inner._outer and, recursively, the outer references of all inner features to
   * the corresponding outer declaring feature.
   *
   * @param inner the feature whose inner features should be found.
   *
   * @param outer the root feature that declares this feature.  For
   * all found feature declarations, the outer feature will be set to
   * this value.
   */
  public void findDeclarations(Feature inner, AbstractFeature outer)
  {
    if (PRECONDITIONS) require
      (inner.isUniverse() || inner.state() == Feature.State.LOADING,
       ((outer == null) == (inner.featureName().baseName().equals(FuzionConstants.UNIVERSE_NAME))),
       !inner.outerSet());

    if (inner._qname.size() > 1)
      {
        setOuterAndAddInnerForQualified(inner, outer);
      }
    else
      {
        setOuterAndAddInner(inner, outer);
      }
  }


  /**
   * For a feature declared with a qualified name, find the actual outer
   * feature, which might be different to the surrounding outer feature.
   *
   * Then, call setOuterAndAddInner with the outer that was found.  This call
   * might be delayed until outer's declarations have been resolved, i.e., after
   * the return of this call.
   *
   * @param inner the feature declared with qualified name.
   *
   * @param outer the surrounding feature
   */
  void setOuterAndAddInnerForQualified(Feature inner, AbstractFeature outer)
  {
    if (PRECONDITIONS) require
      (inner._qname.size() > 1);

    if (inner.isField())
      {
        AstErrors.qualifiedDeclarationNotAllowedForField(inner);
      }

    setOuterAndAddInnerForQualifiedRec(inner, 0, outer);
  }


  /**
   * Helper for setOuterAndAddInnerForQualified() above to iterate over outer features except the
   * outermost feature.
   *
   * This might register a callback in case the outer did not go through
   * findDeclarations yet. This might happen repeatedly for a chain of outer features.
   *
   * @param inner the feature declared with qualified name.
   *
   * @param outer the outer we search the current qualified name in
   *
   * @param at current index in inner._qname
   */
  private void setOuterAndAddInnerForQualifiedRec(Feature inner, int at, AbstractFeature outer)
  {
    outer.whenResolvedDeclarations
      (() ->
       {
         var q = inner._qname;
         var n = q.get(at);
         var o =
           n != FuzionConstants.TYPE_NAME ? lookupType(inner.pos(), outer, n, at == 0)
                                          : outer.typeFeature(_res);
         if (at < q.size()-2)
           {
             setOuterAndAddInnerForQualifiedRec(inner, at+1, o);
           }
         else
           {
             setOuterAndAddInner(inner, o);
             _res.resolveDeclarations(o);
             inner.scheduleForResolution(_res);
           }
       });
  }


  /**
   * Set of all features that are direct outer features of features declared by
   * sources in this source module and that themselves come from other modules.
   */
  TreeSet<LibraryFeature> _outerWithDeclarations = new TreeSet<>
    (new Comparator<LibraryFeature>()
     {
       public int compare(LibraryFeature f1, LibraryFeature f2)
       {
         var l1 = f1._libModule;
         var l2 = f2._libModule;
         return
           (l1 != l2) ? l1.name().compareTo(l2.name())
                      : Integer.signum(f1._index - f2._index);
       }
      });


  /**
   * set inner's outer feature, find its declared inner features and, recursively, do the
   * same for these inner features.
   *
   * @param inner the inner feature that has been loaded and found to be inner of outer.
   *
   * @param outer inner's outer feature.
   */
  void setOuterAndAddInner(Feature inner, AbstractFeature outer)
  {
    if (PRECONDITIONS) require
      (inner.isUniverse() || inner.state() == Feature.State.LOADING,
       inner.isUniverse() == (outer == null));

    inner.setOuter(outer);
    inner.setState(Feature.State.FINDING_DECLARATIONS);
    inner.checkName();
    inner.addOuterRef(_res);

    if (outer != null)
      {
        addDeclaredInnerFeature(outer, inner);
        if (outer instanceof LibraryFeature ol)
          {
            _outerWithDeclarations.add(ol);
          }
      }
    for (var a : inner.arguments())
      {
        findDeclarations((Feature) a, inner); // NYI: Cast!
      }
    inner.addResultField(_res);

    inner.visit(new FeatureVisitor()
      {
        public Call      action(Call      c, AbstractFeature outer) {
          if (c.name() == null)
            { /* this is an anonymous feature declaration */
              if (CHECKS) check
                (Errors.count() > 0  || c.calledFeature() != null);

              if (c.calledFeature() instanceof Feature cf)
                {
                  findDeclarations(cf, outer);
                }
            }
          return c;
        }
        public Feature   action(Feature   f, AbstractFeature outer) { findDeclarations(f, outer); return f; }
      });

    if (inner.initialValue() != null &&
        outer.pos()._sourceFile != inner.pos()._sourceFile &&
        (!outer.isUniverse() || !inner.isLegalPartOfUniverse()) &&
        !inner.isIndexVarUpdatedByLoop() /* required for loop in universe, e.g.
                                          *
                                          *   echo "for i in 1..10 do stdout.println(i)" | fz -
                                          */
        )
      { // declaring field with initial value in different file than outer
        // feature.  We would have to add this to the statements of the outer
        // feature.  But if there are several such fields, in what order?
        AstErrors.initialValueNotAllowed(inner);
      }

    inner.setState(Feature.State.LOADED);

    if (POSTCONDITIONS) ensure
      (inner.outer() == outer,
       inner.state() == Feature.State.LOADED);
  }


  /**
   * Add type new feature.
   *
   * This is somewhat ugly since it adds typeFeature to the declaredFeatures or
   * declaredOrInheritedFeatures of the outer types even after those had been
   * determined already.
   *
   * @param outerType the static outer type of universe.
   *
   * @param typeFeature the new type feature declared within outerType.
   */
  public void addTypeFeature(AbstractFeature outerType,
                             Feature typeFeature)
  {
    findDeclarations(typeFeature, outerType);
    addDeclared(true,  outerType, typeFeature);
    typeFeature.scheduleForResolution(_res);
    resolveDeclarations(typeFeature);
  }


  /**
   * Add inner feature to the set of declared (or inherited) features of outer.
   *
   * NYI: CLEANUP: This is a little ugly since it is used to add type features
   * while the sets of declared and inherited features had already been
   * determined.
   *
   * @param inherited true to add inner to declaredOrInherited, false to add it
   * to declare and declaredOrInherited.
   *
   * @param outer the outer feature
   *
   * @param inner the feature to be added.
   */
  private void addDeclared(boolean inherited, AbstractFeature outer, AbstractFeature inner)
  {
    var d = data(outer);
    var fn = inner.featureName();
    if (!inherited && d._declaredFeatures != null)
      {
        if (CHECKS) check
          (!d._declaredFeatures.containsKey(fn) || d._declaredFeatures.get(fn) == inner);
        d._declaredFeatures.put(fn, inner);
      }
    if (d._declaredOrInheritedFeatures != null)
      {
        if (CHECKS) check
          (!d._declaredOrInheritedFeatures.containsKey(fn) || d._declaredOrInheritedFeatures.get(fn) == inner);
        d._declaredOrInheritedFeatures.put(fn, inner);
      }
  }


  /*-----------------------  attaching data to AST  ----------------------*/


  /**
   * Get declared features for given outer Feature as seen by this module.
   * Result is never null.
   *
   * @param outer the declaring feature
   */
  public SortedMap<FeatureName, AbstractFeature>declaredFeatures(AbstractFeature outer)
  {
    var d = data(outer);
    var s = d._declaredFeatures;
    if (s == null)
      {
        s = new TreeMap<>();
        d._declaredFeatures = s;
        for (var m : _dependsOn)
          { // NYI: properly obtain set of declared features from m, do we need
            // to take care for the order and dependencies between modules?
            var md = m.declaredFeatures(outer);
            if (md != null)
              {
                for (var e : md.entrySet())
                  {
                    s.put(e.getKey(), e.getValue());
                  }
              }
          }

        // NYI: cleanup: See #462: Remove once sub-directories are loaded
        // directly, not implicitly when outer feature is found
        for (var inner : s.values())
          {
            loadInnerFeatures(inner);
          }

      }
    return s;
  }


  /**
   * During phase RESOLVING_DECLARATIONS, determine the set of declared or
   * inherited features for outer.
   *
   * @param outer the declaring feature
   */
  public void findDeclaredOrInheritedFeatures(Feature outer)
  {
    if (PRECONDITIONS) require
      (outer.state() == Feature.State.RESOLVING_DECLARATIONS);

    var d = data(outer);
    if (d._declaredOrInheritedFeatures == null)
      {
        // NYI: cleanup: See #479: there are two places that initialize
        // _declaredOrInheritedFeatures: this place and
        // Module.declaredOrInheritedFeatures(). There should be only one!
        d._declaredOrInheritedFeatures = new TreeMap<>();
      }
    findInheritedFeatures(d._declaredOrInheritedFeatures, outer);
    loadInnerFeatures(outer);
    findDeclaredFeatures(outer);
  }


  /**
   * Add all declared features to _declaredOrInheritedFeatures.  In case a
   * declared feature exists in _declaredOrInheritedFeatures (because it was
   * inherited), check if the declared feature redefines the inherited
   * feature. Otherwise, report an error message.
   *
   * @param outer the declaring feature
   */
  private void findDeclaredFeatures(AbstractFeature outer)
  {
    var s = declaredFeatures(outer);
    for (var e : s.entrySet())
      {
        var f = e.getValue();
        addToDeclaredOrInheritedFeatures(outer, f);
        if (f instanceof Feature ff)
          {
            ff.scheduleForResolution(_res);
          }
      }
  }


  /**
   * Add f with name fn to the declaredOrInherited features of outer.
   *
   * In case a declared feature exists in _declaredOrInheritedFeatures (because
   * it was inherited), check if the declared feature redefines the inherited
   * feature. Otherwise, report an error message.
   *
   * @param outer the declaring feature
   *
   * @param f the declared or inherited feature.
   */
  private void addToDeclaredOrInheritedFeatures(AbstractFeature outer, AbstractFeature f)
  {
    var fn = f.featureName();
    var doi = declaredOrInheritedFeatures(outer);
    var existing = doi.get(fn);
    if (existing == null)
      {
        if (f instanceof Feature ff && (ff._modifiers & Consts.MODIFIER_REDEFINE) != 0)
          {
            AstErrors.redefineModifierDoesNotRedefine(f);
          }
      }
    else if (existing == f)
      {
      }
    else if (existing.outer() == outer)
      {
        if (existing.isTypeFeature())
          {
            // NYI: see #461: type features may currently be declared repeatedly in different modules
          }
        else if (Errors.count() == 0)
          { // This can happen only as the result of previous errors since this
            // case was already handled in addDeclaredInnerFeature:
            throw new Error();
          }
      }
    else if (existing.generics() != FormalGenerics.NONE)
      {
        AstErrors.cannotRedefineGeneric(f.pos(), outer, existing);
      }
    else if (f instanceof Feature ff && (ff._modifiers & Consts.MODIFIER_REDEFINE) == 0 && !existing.isAbstract())
      {
        AstErrors.redefineModifierMissing(f.pos(), outer, existing);
      }
    else
      {
        f.redefines().add(existing);
      }
    if (f     instanceof Feature ff &&
        outer instanceof Feature of && of.state().atLeast(Feature.State.RESOLVED_DECLARATIONS))
      {
        ff._addedLate = true;
      }
    if (f instanceof Feature ff && ff.state().atLeast(Feature.State.RESOLVED_DECLARATIONS))
      {
        ff._addedLate = true;
      }
    doi.put(fn, f);
  }


  /**
   * Add inner to the set of declared inner features of outer.
   *
   * Note that inner must be declared in this module, but outer may be defined
   * in a different module.  E.g. #universe is declared in stdlib, while an
   * inner feature 'main' may be declared in the application's module.
   *
   * @param outer the declaring feature
   *
   * @param f the inner feature.
   */
  void addDeclaredInnerFeature(AbstractFeature outer, Feature f)
  {
    if (PRECONDITIONS) require
      (!(outer instanceof Feature of) || of.state().atLeast(Feature.State.LOADING));

    var fn = f.featureName();
    var df = declaredFeatures(outer);
    var existing = df.get(fn);
    if (existing != null)
      {
        if (f       .implKind() == Impl.Kind.FieldDef &&
            existing.implKind() == Impl.Kind.FieldDef    )
          {
            var existingFields = FeatureName.getAll(df, fn.baseName(), 0);
            fn = FeatureName.get(fn.baseName(), 0, existingFields.size());
            f.setFeatureName(fn);
          }
        else
          {
            boolean error = true;
            if (f.isField() && existing.isField())
              {
                error = false;
                var existingFields = FeatureName.getAll(df, fn.baseName(), 0);
                for (var e : existingFields.values())
                  {
                    // NYI: set error if e.declaredInBlock() == f.declaredInBlock()
                    if (((Feature)e).isDeclaredInMainBlock() && f.isDeclaredInMainBlock()) // NYI: Cast!
                      {
                        error = true;
                      }
                  }
                if (!error)
                  {
                    fn = FeatureName.get(fn.baseName(), 0, existingFields.size());
                    f.setFeatureName(fn);
                  }
              }
            if (error)
              {
                AstErrors.duplicateFeatureDeclaration(f.pos(), f, existing);
              }
          }
      }
    df.put(fn, f);
    if (!(outer instanceof Feature of) || of.state().atLeast(Feature.State.RESOLVED_DECLARATIONS))
      {
        addToDeclaredOrInheritedFeatures(outer, f);
        if (!outer.isChoice() || !f.isField())  // A choice does not inherit any fields
          {
            addToHeirs(outer, fn, f);
          }
      }
  }


  /**
   * Add feature under given name to _declaredOrInheritedFeatures of all direct
   * and indirect heirs of this feature.
   *
   * This is used in addDeclaredInnerFeature to add features during syntactic
   * sugar resolution after _declaredOrInheritedFeatures has already been set.
   *
   * @param fn the name of the feature, after possible renaming during inheritance
   *
   * @param f the feature to be added.
   */
  private void addToHeirs(AbstractFeature outer, FeatureName fn, Feature f)
  {
    var d = data(outer);
    if (d != null)
      {
        for (var h : d._heirs)
          {
            var pos = SourcePosition.builtIn; // NYI: Would be nicer to use Call.pos for the inheritance call in h.inherits
            addInheritedFeature(data(outer)._declaredOrInheritedFeatures, h, pos, fn, f);
            addToHeirs(h, fn, f);
          }
      }
  }


  /**
   * allInnerAndInheritedFeatures returns a complete set of inner features, used
   * by Clazz.layout and Clazz.hasState.
   *
   * @return
   */
  public Collection<AbstractFeature> allInnerAndInheritedFeatures(AbstractFeature f)
  {
    var d = data(f);
    var result = d._allInnerAndInheritedFeatures;
    if (result == null)
      {
        result = new TreeSet<>();

        result.addAll(declaredFeatures(f).values());
        for (var p : f.inherits())
          {
            var cf = p.calledFeature();
            if (CHECKS) check
              (Errors.count() > 0 || cf != null);

            if (cf != null)
              {
                result.addAll(allInnerAndInheritedFeatures(cf));
              }
          }
        d._allInnerAndInheritedFeatures = result;
      }
    return result;
  }


  /*--------------------------  feature lookup  -------------------------*/


  /**
   * Find feature with given name in outer.
   *
   * @param outer the declaring or inheriting feature
   */
  public AbstractFeature lookupFeature(AbstractFeature outer, FeatureName name, AbstractFeature original)
  {
    if (PRECONDITIONS) require
      (!(outer instanceof Feature of) || of.state().atLeast(Feature.State.LOADING));

    var result = declaredOrInheritedFeatures(outer).get(name);

    /* Was feature f added to the declared features of its outer features late,
     * i.e., after the RESOLVING_DECLARATIONS phase?  These late features are
     * currently not added to the sets of declared or inherited features by
     * children of their outer clazz.
     *
     * This is a fix for #978 but it might need to be removed when fixing #932.
     */
    return result == null && original instanceof Feature of && of._addedLate ? original
                                                                             : result;
  }


  /**
   * Find set of candidate features in an unqualified access (call or
   * assignment).  If several features match the name but have different
   * argument counts, return all of them.
   *
   * @param outer the declaring or inheriting feature
   *
   * @param name the name of the feature
   *
   * @param use the call, assign or destructure we are trying to resolve, used
   * to find field in scope, or null if fields should not be checked for scope
   *
   * @param traverseOuter true to collect all the features found in outer and
   * outer's outer (i.e., use is unqualified), false to search in outer only
   * (i.e., use is qualified with outer).
   *
   * @return in case we found features visible in the call's scope, the features
   * together with the outer feature where they were found.
   */
  public List<FeatureAndOuter> lookup(AbstractFeature outer, String name, Stmnt use, boolean traverseOuter)
  {
    if (PRECONDITIONS) require
      (!(outer instanceof Feature of) || of.state().atLeast(Feature.State.LOADING));

    List<FeatureAndOuter> result = new List<>();
    var curOuter = outer;
    AbstractFeature inner = null;
    var foundFieldInScope = false;
    do
      {
        var foundFieldInThisScope = foundFieldInScope;
        var fs = FeatureName.getAll(declaredOrInheritedFeatures(curOuter), name);
        if (fs.size() >= 1 && use != null && traverseOuter)
          { // try to disambiguate fields as in
            //
            //  x := a
            //  x := x + 1
            //  x := 2 * x
            List<FeatureName> fields = new List<>();
            for (var e : fs.entrySet())
              {
                var fn = e.getKey();
                var f = e.getValue();
                if (f.isField() && (f.outer()==null || f.outer().resultField() != f))
                  {
                    fields.add(fn);
                  }
              }
            if (!fields.isEmpty())
              {
                var f = curOuter instanceof Feature of /* NYI: AND cutOuter loaded by this module */
                  ? of.findFieldDefInScope(name, use, inner)
                  : null;
                fs = new TreeMap<>(fs);
                // if we found f in scope, remove all other entries, otherwise remove all entries within this since they are not in scope.
                for (var fn : fields)
                  {
                    var fi = fs.get(fn);
                    if (f != null || fi.outer() == outer && (!(fi instanceof Feature fif) || !fif.isArtificialField()))
                      {
                        fs.remove(fn);
                      }
                  }
                if (f != null)
                  {
                    fs.put(f.featureName(), f);
                    foundFieldInThisScope = true;
                  }
              }
          }

        for (var e : fs.entrySet())
          {
            var v = e.getValue();
            if (!v.isField() || !foundFieldInScope)
              {
                result.add(new FeatureAndOuter(v, curOuter));
                foundFieldInScope = foundFieldInScope || v.isField() && foundFieldInThisScope;
              }
          }

        inner = curOuter;
        curOuter = curOuter.outer();
      }
    while (traverseOuter && curOuter != null);
    return result;
  }


  /**
   * Lookup the feature that is referenced in a non-generic type.  There might
   * be several features with the given name and different argument counts.
   * Then, only the feature that is a constructor defines the type.
   *
   * If there are several such constructors, the type is ambiguous and an error
   * will be produced.
   *
   * Also, if there is no such type, an error will be produced.
   *
   * @param pos the position of the type.
   *
   * @param outer the outer feature of the type
   *
   * @param name the name of the type
   *
   * @param traverseOuter true to collect all the features found in outer and
   * outer's outer (i.e., use is unqualified), false to search in outer only
   * (i.e., use is qualified with outer).
   */
  public AbstractFeature lookupType(SourcePosition pos, AbstractFeature outer, String name, boolean traverseOuter)
  {
    if (PRECONDITIONS) require
      (Errors.count() > 0 || outer != Types.f_ERROR);

    AbstractFeature result = Types.f_ERROR;
    if (outer != Types.f_ERROR && name != Types.ERROR_NAME)
      {
        _res.resolveDeclarations(outer);
        var curOuter = outer;
        var type_fs = new List<AbstractFeature>();
        var nontype_fs = new List<AbstractFeature>();
        var fs = lookup(outer, name, null, traverseOuter);
        for (var fo : fs)
          {
            var f = fo._feature;
            (f.definesType() ? type_fs
                             : nontype_fs).add(f);
          }
        if (type_fs.size() > 1)
          {
            AstErrors.ambiguousType(pos, name, type_fs);
          }
        else if (type_fs.size() < 1)
          {
            AstErrors.typeNotFound(pos, name, outer, nontype_fs);
          }
        else
          {
            result = type_fs.get(0);
          }
      }
    return result;
  }


  /*--------------------------  type checking  --------------------------*/


  /**
   * Check if argument type 'tr' found in feature 'redefinition' may legally
   * redefine original argument type 'to' found in feature 'original'.
   *
   * This is the case if original is
   *
   *    a.b.c.f(... arg#i a.b.c.this ...)
   *
   * and redefinition is
   *
   *    x.y.z.f(... arg#i x.y.z.this ...)
   *
   * or redefinition is
   *
   *    fixed x.y.z.f(... arg#i x.y.z ...)
   *
   * @param original the parent feature.
   *
   * @param redefinition the heir feature.
   *
   * @param to original argument type in 'original'.
   *
   * @param tr new argument type in 'redefinition'.
   *
   * @return true if 'to' may be replaced with 'tr'.
   */
  boolean isLegalCovariantThisType(AbstractFeature original,
                                   Feature redefinition,
                                   AbstractType to,
                                   AbstractType tr,
                                   boolean ignoreFixedModifier)
  {
    return
      /* to is original    .this.type  and
       * tr is redefinition.this.type
       */
      ((to.isThisType()                                        ) &&
       (tr.isThisType()                                        ) &&
       (to.featureOfType() == original    .outer()             ) &&
       (tr.featureOfType() == redefinition.outer()             )   ) ||

      /* to is original.this.type  and
       * redefinition is fixed and tr is redefinition.thisType.
       */
      ((to.isThisType()                                        ) &&
       ((redefinition.modifiers() & Consts.MODIFIER_FIXED) != 0) &&
       (to.featureOfType() == original    .outer()             ) &&
       (tr.featureOfType() == redefinition.outer()             )   ) ||

      /* original and redefinition are inner features of type features, to is
       * THIS_TYPE and tr is the underlying non-type features thisType.
       *
       * E.g., i32.type.equality(a, b i32) redefines numeric.type.equality(a, b
       * numeric.this.type)
       */
      (original    .outer().isTypeFeature()                                                                                            &&
       redefinition.outer().isTypeFeature()                                                                                            &&
       to.isGenericArgument()                                                                                                          &&
       to.genericArgument()                   .typeParameter().featureName().baseName().equals(FuzionConstants.TYPE_FEATURE_THIS_TYPE) &&  /* NYI: ugly string comparison */
       original.outer().generics().list.get(0).typeParameter().featureName().baseName().equals(FuzionConstants.TYPE_FEATURE_THIS_TYPE) &&  /* NYI: ugly string comparison */
       !tr.isGenericArgument()                                                                                                         &&
       ((redefinition.modifiers() & Consts.MODIFIER_FIXED) != 0 || ignoreFixedModifier)                                                &&
       tr.compareTo(redefinition.outer().typeFeatureOrigin().thisTypeInTypeFeature()) == 0                                               );
  }


  /**
   * Check types of given Feature. This mainly checks that all redefinitions of
   * f are compatible with f.
   *
   * NYI: Better perform the check the other way around: check that f matches
   * the types of all features that f redefines.
   */
  public void checkTypes(Feature f)
  {
    var args = f.arguments();
    int ean = args.size();
    for (var o : f.redefines())
      {
        var ta = o.handDown(_res, o.argTypes(), f.outer());
        var ra = f.argTypes();
        if (ta.length != ra.length)
          {
            AstErrors.argumentLengthsMismatch(o, ta.length, f, ra.length);
          }
        else
          {
            for (int i = 0; i < ta.length; i++)
              {
                var t1 = ta[i];
                var t2 = ra[i];
                if (t1.compareTo(t2) != 0 &&
                    !isLegalCovariantThisType(o, f, t1, t2, false) &&
                    !t1.containsError() && !t2.containsError())
                  {
                    // original arg list may be shorter if last arg is open generic:
                    if (CHECKS) check
                      (Errors.count() > 0 ||
                       i < args.size() ||
                       args.get(args.size()-1).resultType().isOpenGeneric());
                    int ai = Math.min(args.size() - 1, i);

                    var originalArg = o.arguments().get(i);
                    var actualArg   =   args       .get(ai);
                    AstErrors.argumentTypeMismatchInRedefinition(o, originalArg, t1,
                                                                 f, actualArg,
                                                                 isLegalCovariantThisType(o, f, t1, t2, true));
                  }
              }
          }

        var t1 = o.handDownNonOpen(_res, o.resultType(), f.outer());
        var t2 = f.resultType();
        if (o.isTypeFeaturesThisType() && f.isTypeFeaturesThisType())
          { // NYI: CLEANUP: #706: allow redefinition of THIS_TYPE in type features for now, these are created internally.
          }
        else if ((t1.isChoice()
                  ? t1.compareTo(t2) != 0  // we (currently) do not tag the result in a redefined feature, see testRedefine
                  : !t1.isAssignableFrom(t2)) &&
                 t2 != Types.resolved.t_void &&
                 !isLegalCovariantThisType(o, f, t1, t2, false))
          {
            AstErrors.resultTypeMismatchInRedefinition(o, t1, f, isLegalCovariantThisType(o, f, t1, t2, true));
          }
      }

    if (f.isConstructor())
      {
        var cod = f.code();
        var rt = cod.type();
        if (!Types.resolved.t_unit.isAssignableFrom(rt))
          {
            AstErrors.constructorResultMustBeUnit(cod);
          }
      }

    if (f.isTypeParameter())
      {
        if (f.resultType().isGenericArgument())
          {
            AstErrors.constraintMustNotBeGenericArgument(f);
          }
      }
  }


  /*---------------------------  library file  --------------------------*/


  /**
   * Create a ByteBuffer containing the .mir file binary data for this module.
   */
  public ByteBuffer data(String name)
  {
    return new LibraryOut(this, name).buffer();
  }


  /*-------------------------------  misc  ------------------------------*/


  /**
   * Create String representation for debugging.
   */
  public String toString()
  {
    var r = new StringBuilder();
    r.append("SourceModule for paths: ");
    var comma = "";
    for (var s: _sourceDirs)
      {
        r.append(comma).append(s);
        comma = ", ";
      }
    return r.toString();
  }

}

/* end of file */
