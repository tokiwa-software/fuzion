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
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.ast.AbstractBlock;
import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.AstErrors;
import dev.flang.ast.Call;
import dev.flang.ast.Consts;
import dev.flang.ast.Current;
import dev.flang.ast.Expr;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.FeatureAndOuter;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.Impl;
import dev.flang.ast.Resolution;
import dev.flang.ast.SrcModule;
import dev.flang.ast.State;
import dev.flang.ast.Types;
import dev.flang.ast.Visi;
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


  /**
   * Resolution instance
   */
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


  @Override
  String name()
  {
    return "*** source module ***";
  }


  /*---------------------------  main control  --------------------------*/


  /**
   * If source comes from stdin or an explicit input file, parse this and
   * extract the main feature.  Otherwise, return the default main.
   *
   * @return the main feature found or _defaultMain if none
   */
  String parseMain()
  {
    var res = _defaultMain;
    var p = _inputFile;
    if (p != null)
      {
        var expr = parseFile(p);
        ((AbstractBlock) _universe.code())._expressions.addAll(expr);
        for (var s : expr)
          {
            if (s instanceof Feature f)
              {
                f.legalPartOfUniverse();  // suppress FeErrors.initialValueNotAllowed
                if (expr.size() == 1 && !f.isField())
                  {
                    res = f.featureName().baseName();
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
  List<Expr> parseFile(Path p)
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
    var exprs = parseFile(p);
    var result = new List<Feature>();
    for (var s : exprs)
      {
        if (s instanceof Feature f)
          {
            result.add(f);
          }
        else if (!Errors.any())
          {
            AstErrors.expressionNotAllowedOutsideOfFeatureDeclaration(s);
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
        _universe.setState(State.RESOLVED);
        new Types.Resolved(this,
                           (name) ->
                             {
                               return lookupType(SourcePosition.builtIn, _universe, name, false, false)
                                ._feature
                                .selfType();
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
    if (main != null && !Errors.any())
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
    if (!Errors.any())
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
    /*
    // tag::fuzion_rule_SRCF_DOTFZ[]
Fuzion source files may have an arbitrary file name ending with the file name extension `.fz`.
    // end::fuzion_rule_SRCF_DOTFZ[]
    */
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
                    /*
                    // tag::fuzion_rule_SRCF_DIR[]
Files in a sub-directories within a directory are considered as input only if
the directory name equals the (((base name))) of a (((constructor))).  Then, the
files matching rule xref:SRCF_DOTFZ[SRCF_DOTFZ] within that diretory are parsed as if they were
part of the (((inner features))) declarations of the correpsonding
((construtor)).
                    // end::fuzion_rule_SRCF_DIR[]
                    */

                    Files.list(d._dir)
                      .filter(p -> isValidSourceFile(p))
                      .sorted()
                      .forEach(p ->
                               {
                                 for (var inner : parseAndGetFeatures(p))
                                   {
                                     findDeclarations(inner, f);
                                     if (inner.state().atLeast(State.LOADED))
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
      (inner.isUniverse() || inner.state() == State.LOADING,
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
        // NYI inner.isTypeFeature() does not work currently
        if (inner._qname.getFirst().equals(FuzionConstants.TYPE_NAME))
          {
            AstErrors.typeFeaturesMustNotBeFields(inner);
          }
        else
          {
            AstErrors.qualifiedDeclarationNotAllowedForField(inner);
          }
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
           n != FuzionConstants.TYPE_NAME ? lookupType(inner.pos(), outer, n, at == 0, false)._feature
                                          : outer.typeFeature(_res);
         if (at < q.size()-2)
           {
             setOuterAndAddInnerForQualifiedRec(inner, at+1, o);
           }
         else if (o != Types.f_ERROR)
           {
             setOuterAndAddInner(inner, o);
             _res.resolveDeclarations(o);
             inner.scheduleForResolution(_res);
           }
          else
           {
             if (CHECKS) check
               (Errors.any());
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
      (inner.isUniverse() || inner.state() == State.LOADING,
       inner.isUniverse() == (outer == null));

    inner.setOuter(outer);
    inner.setState(State.FINDING_DECLARATIONS);
    inner.checkName();

    if (outer == null)
      {
        inner.addOuterRef(_res);
      }
    else
      {
        // fixes issue #1787
        // We need to wait until `inner` has its final type parameters.
        // This may include type parameters received via free types.
        // (Creating outer ref uses `createThisType()` which calls `generics()`.)
        outer.whenResolvedDeclarations(() -> {
          inner.addOuterRef(_res);
        });
      }

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
                (Errors.any()  || c.calledFeature() != null);

              if (c.calledFeature() instanceof Feature cf
                  // fixes issue #1358
                  && cf.state() == State.LOADING)
                {
                  findDeclarations(cf, outer);
                }
            }
          return c;
        }
        public Feature   action(Feature   f, AbstractFeature outer) { findDeclarations(f, outer); return f; }
      });

    if (inner.impl().initialValue() != null &&
        !outer.pos()._sourceFile.sameAs(inner.pos()._sourceFile) &&
        (!outer.isUniverse() || !inner.isLegalPartOfUniverse()) &&
        (outer.isUniverse() || !outer.pos().isBuiltIn()) && // some generated features in loops do not have source position
        !inner.isIndexVarUpdatedByLoop() /* required for loop in universe, e.g.
                                          *
                                          *   echo "for i in 1..10 do stdout.println(i)" | fz -
                                          */
        )
      { // declaring field with initial value in different file than outer
        // feature.  We would have to add this to the expressions of the outer
        // feature.  But if there are several such fields, in what order?
        AstErrors.initialValueNotAllowed(inner);
      }

    inner.setState(State.LOADED);

    if (POSTCONDITIONS) ensure
      (inner.outer() == outer,
       inner.state() == State.LOADED);
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
  public void addTypeParameter(AbstractFeature outer,
                               Feature typeParameter)
  {
    var d = data(outer);
    var fn = typeParameter.featureName();
    if (d._declaredFeatures != null)
      {
        if (CHECKS) check
          (!d._declaredFeatures.containsKey(fn) || d._declaredFeatures.get(fn) == typeParameter);
        d._declaredFeatures.put(fn, typeParameter);
      }
    if (d._declaredOrInheritedFeatures != null)
      {
        if (CHECKS) check
          (!d._declaredOrInheritedFeatures.containsKey(fn) || d._declaredOrInheritedFeatures.get(fn) == typeParameter);
        d._declaredOrInheritedFeatures.put(fn, typeParameter);
      }
  }


  /**
   * Add inner feature to the set of declared (or inherited) features of outer.
   *
   * NYI: CLEANUP: This is a little ugly since it is used to add type features
   * while the sets of declared and inherited features had already been
   * determined.
   *
   * @param inherited true to add inner to declaredOrInherited, false to add it
   * to declaredFeatures and declaredOrInherited.
   *
   * @param outer the outer feature
   *
   * @param inner the feature to be added.
   */
  private void addDeclared(boolean inherited, AbstractFeature outer, AbstractFeature inner)
  {
    if (PRECONDITIONS)
      require(outer.isConstructor(), inner.isTypeFeature());

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
      (outer.state() == State.RESOLVING_DECLARATIONS);

    var d = data(outer);
    if (d._declaredOrInheritedFeatures == null)
      {
        // NYI: cleanup: See #479: there are two places that initialize
        // _declaredOrInheritedFeatures: this place and
        // Module.declaredOrInheritedFeatures(). There should be only one!
        d._declaredOrInheritedFeatures = new TreeMap<>();
      }
    findInheritedFeatures(d._declaredOrInheritedFeatures, outer, _dependsOn);
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
    else if (f instanceof Feature ff && (ff._modifiers & Consts.MODIFIER_REDEFINE) == 0 && !existing.isAbstract())
      { /* previous duplicate feature declaration could result in this error for
         * type features, so suppress them in this case. See fuzion-lang.dev's
         * design/examples/typ_const2.fz as an example.
         */
        if ((!Errors.any() || !f.isTypeFeature()) && visibleFor(existing, f))
          {
            AstErrors.redefineModifierMissing(f.pos(), f, existing);
          }
      }
    else
      {
        f.redefines().add(existing);
      }
    if (f     instanceof Feature ff &&
        outer.state().atLeast(State.RESOLVED_DECLARATIONS))
      {
        ff._addedLate = true;
      }
    if (f instanceof Feature ff && ff.state().atLeast(State.RESOLVED_DECLARATIONS))
      {
        ff._addedLate = true;
      }
    doi.put(fn, f);
  }


  /**
   * Add inner to the set of declared inner features of outer.
   *
   * Note that inner must be declared in this module, but outer may be defined
   * in a different module.  E.g. universe is declared in stdlib, while an
   * inner feature 'main' may be declared in the application's module.
   *
   * @param outer the declaring feature
   *
   * @param f the inner feature.
   */
  void addDeclaredInnerFeature(AbstractFeature outer, Feature f)
  {
    if (PRECONDITIONS) require
      (outer.state().atLeast(State.LOADING));

    var fn = f.featureName();
    var df = declaredFeatures(outer);
    var existing = df.get(fn);
    if (existing != null)
      {
        if (existing instanceof Feature ef &&
            f .implKind() == Impl.Kind.FieldDef &&
            ef.implKind() == Impl.Kind.FieldDef    )
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
    if (outer.state().atLeast(State.RESOLVED_DECLARATIONS))
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
              (Errors.any() || cf != null);

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


  @Override
  public SortedMap<FeatureName, AbstractFeature> declaredOrInheritedFeatures(AbstractFeature outer)
  {
    return this.declaredOrInheritedFeatures(outer, _dependsOn);
  }


  /**
   * Find feature with given name in outer.
   *
   * @param outer the declaring or inheriting feature
   */
  public AbstractFeature lookupFeature(AbstractFeature outer, FeatureName name, AbstractFeature original)
  {
    if (PRECONDITIONS) require
      (outer.state().atLeast(State.LOADING));

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
   * Check if outer defines or inherits exactly one feature with no arguments
   * and an open type parameter as its result type. If such a feature exists and
   * is visible by `use`, it will be returned.
   *
   * @param outer the declaring or inheriting feature
   *
   * @param use the expression that uses the feature, so it must be visible to
   * this.
   *
   * @return the unique feature that was found, null if none or several were
   * found.
   */
  public AbstractFeature lookupOpenTypeParameterResult(AbstractFeature outer, Expr use)
  {
    if (!outer.state().atLeast(State.RESOLVING_DECLARATIONS))
      {
        _res.resolveDeclarations(outer);
      }
    var count = 0;
    AbstractFeature found = null;
    for (var f : declaredOrInheritedFeatures(outer).values())
      {
        if (featureVisible(use.pos()._sourceFile, f) &&
            f instanceof LibraryFeature lf &&
            lf.resultType().isOpenGeneric() &&
            f.arguments().isEmpty())
          {
            found = f;
            count++;
          }
      }
    return count == 1 ? found : null;
  }


  /**
   * Find set of candidate features in an unqualified access (call or
   * assignment).  If several features match the name but have different
   * argument counts, return all of them.
   *
   * @param outer the declaring or inheriting feature
   *
   * @param name the name of the feature, may starts with
   * FuzionConstants.UNARY_OPERATOR_PREFIX, which means that both prefix and
   * postfix variants should match.
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
  public List<FeatureAndOuter> lookup(AbstractFeature outer, String name, Expr use, boolean traverseOuter, boolean hidden)
  {
    List<FeatureAndOuter> result;
    if (name.startsWith(FuzionConstants.UNARY_OPERATOR_PREFIX))
      {
        var op = name.substring(FuzionConstants.UNARY_OPERATOR_PREFIX.length());
        var prefixName = FuzionConstants.PREFIX_OPERATOR_PREFIX + op;
        var postfxName = FuzionConstants.POSTFIX_OPERATOR_PREFIX + op;
        result = new List<>();
        result.addAll(lookup0(outer, prefixName, use, traverseOuter, hidden));
        result.addAll(lookup0(outer, postfxName, use, traverseOuter, hidden));
      }
    else
      {
        result = lookup0(outer, name, use, traverseOuter, hidden);
      }
    return result;
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
  private List<FeatureAndOuter> lookup0(AbstractFeature outer, String name, Expr use, boolean traverseOuter, boolean hidden)
  {
    if (PRECONDITIONS) require
      (outer.state().atLeast(State.RESOLVING_DECLARATIONS) || outer.isUniverse());

    List<FeatureAndOuter> result = new List<>();
    var curOuter = outer;
    AbstractFeature inner = null;
    var foundFieldInScope = false;
    do
      {
        if (!curOuter.state().atLeast(State.RESOLVING_DECLARATIONS))
          {
            _res.resolveDeclarations(curOuter);
          }
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
                var f = curOuter instanceof Feature of
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
            if ((use == null || (hidden != featureVisible(use.pos()._sourceFile, v))) &&
                (!v.isField() || !foundFieldInScope))
              {
                result.add(new FeatureAndOuter(v, curOuter, inner));
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
   *
   * @return FeatureAndOuter tuple of the found type's declaring feature,
   * FeatureAndOuter.ERROR in case of an error, null in case no type was found
   * and ignoreNotFound is true.
   */
  public FeatureAndOuter lookupType(SourcePosition pos,
                                    AbstractFeature outer,
                                    String name,
                                    boolean traverseOuter,
                                    boolean ignoreNotFound)
  {
    if (PRECONDITIONS) require
      (Errors.any() || outer != Types.f_ERROR);

    FeatureAndOuter result = FeatureAndOuter.ERROR;
    if (outer != Types.f_ERROR && name != Types.ERROR_NAME)
      {
        _res.resolveDeclarations(outer);
        var type_fs = new List<AbstractFeature>();
        var nontype_fs = new List<AbstractFeature>();
        var fs = lookup(outer, name, null, traverseOuter, false);
        for (var fo : fs)
          {
            var f = fo._feature;
            if (typeVisible(pos._sourceFile, f, true))
              {
                if (f.definesType() || f.isTypeParameter())
                  {
                    type_fs.add(f);
                    result = fo;
                  }
                else
                  {
                    nontype_fs.add(f);
                  }
              }
          }
        if (type_fs.size() > 1)
          {
            AstErrors.ambiguousType(pos, name, type_fs);
          }
        else if (type_fs.size() < 1)
          {
            if (ignoreNotFound)
              {
                result = null;
              }
            else
              {
                AstErrors.typeNotFound(pos, name, outer, nontype_fs);
              }
          }
      }

    if (POSTCONDITIONS) ensure
      (ignoreNotFound || result != null);

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
   * @param to original argument type in `original`.
   *
   * @param tr new argument type in `redefinition`.
   *
   * @param fixed true to perform the test as if `redefinition` is `fixed`. This
   * is used in two ways: first, to check if `redefinition` is fixed, and then,
   * when an error is reported, to suggest adding `fixed` if that would solve
   * the error.
   *
   * @return true if `to` may be replaced with `tr` or if `to` or `tr` contain
   * an error.
   */
  boolean isLegalCovariantThisType(AbstractFeature original,
                                   Feature redefinition,
                                   AbstractType to,
                                   AbstractType tr,
                                   boolean fixed)
  {
    return
      /* to contains original    .this.type and
       * tr contains redefinition.this.type
       */
      to.replace_this_type(original.outer(), redefinition.outer())
        .compareTo(tr) == 0                                                       ||

      /* to depends on original.this.type, redefinition is fixed and tr is
       * equals the actual type of to as seen by redefinition.outer.
       *
       * Ex.
       *
       *   p is
       *     maybe option p.this.type
       *     is abstract
       *
       *   h : p is
       *     fixed redef maybe option h
       *     is
       *       if random.next_bool then
       *         nil
       *       else
       *         h
       *
       * here, the result type of inherited `p.maybe` is `option p.this.type`,
       * which gets turned into `option h.this.type` when inherited. However,
       * since `h.maybe` is fixed, we can use the actual type in the outer
       * feature `h`, i.e., `option h`, which is equal to the result type of the
       * redefinition `h.maybe`.
       */
      fixed &&
      redefinition.outer().thisType(true).actualType(to).compareTo(tr) == 0       ||

      /* original and redefinition are inner features of type features, `to` is
       * `this.type` and `tr` is the underlying non-type feature's selfType.
       *
       * E.g.,
       *
       *   fixed i32.type.equality(a, b i32) bool is ...
       *
       * redefines
       *
       *   equatable.type.equality(a, b equatable.this.type) bool is abstract
       *
       * so we allow `equatable.this.type` to become `i32`.
       */
      fixed                                &&
      original    .outer().isTypeFeature() &&
      redefinition.outer().isTypeFeature() &&
      to.replace_this_type_in_type_feature(redefinition.outer())
        .compareTo(tr) == 0                                                       ||

      /* avoid reporting errors in case of previous errors
       */
      to.containsError() ||
      tr.containsError();
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
    if (!f.isVisibilitySpecified() && !f.redefines().isEmpty())
      {
        f.setVisbility(f.redefines().stream().map(r -> r.visibility()).sorted().findAny().get());
      }

    f.impl().checkTypes(f);
    var args = f.arguments();
    var fixed = (f.modifiers() & Consts.MODIFIER_FIXED) != 0;
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
                if (!isLegalCovariantThisType(o, f, t1, t2, fixed))
                  {
                    // original arg list may be shorter if last arg is open generic:
                    if (CHECKS) check
                      (Errors.any() ||
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
        else if (o.isChoice())
          {
            AstErrors.cannotRedefineChoice(f, o);
          }
        else if (!t1.isDirectlyAssignableFrom(t2) &&  // we (currently) do not tag the result in a redefined feature, see testRedefine
                 !t2.isVoid() &&
                 !isLegalCovariantThisType(o, f, t1, t2, fixed))
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
    checkRedefVisibility(f);
    checkLegalTypeVisibility(f);
    checkResultTypeVisibility(f);
    checkArgTypesVisibility(f);
    checkPreconditionVisibility(f);
    checkAbstractVisibility(f);
  }


  /**
   * Check that an abstract feature is at least as visible as the outer feature.
   */
  private void checkAbstractVisibility(Feature f) {
    if(f.isAbstract() &&
       f.visibility().featureVisibility().ordinal() < f.outer().visibility().featureVisibility().ordinal())
      {
        AstErrors.abstractFeaturesVisibilityMoreRestrictiveThanOuter(f);
      }
  }


  /**
   * Check that `f` does not have more restrictive
   * visibility than every feature it redefines.
   */
  private void checkRedefVisibility(Feature f)
  {
    if (!f.isTypeFeaturesThisType()
    // Function.call is public while actual lambdas-impl are not.
    // If lambda-impl were public then result-type and all arg-types
    // would have to be public as well. Hence this exception.
    && !f.isLambdaCall())
    {
      for (var redefined : f.redefines()) {
        if (redefined.visibility().ordinal() > f.visibility().ordinal())
          {
            AstErrors.redefMoreRestrictiveVisibility(f, redefined);
          }
      }
    }
  }


  /**
   * Are all used features in precondition at least as visible as feature?
   * @param f
   */
  private void checkPreconditionVisibility(Feature f)
  {
    f
      .contract()
      .req
      .forEach(r -> r.visit(new FeatureVisitor() {
        @Override
        public void action(AbstractCall c)
        {
          super.action(c);
          if (// visibility of arg is allowed to be more restrictive
              // since it is always known by caller
              !f.arguments().contains(c.calledFeature())
              // do not check exprResult generated by label
              && !(c.target() instanceof Current)
              // type param is known by caller
              && !c.calledFeature().isTypeParameter()
              // the called feature must be at least as visible as the feature.
              && c.calledFeature().visibility().featureVisibility().ordinal() < f.visibility().featureVisibility().ordinal())
            {
              AstErrors.calledFeatureInPreconditionHasMoreRestrictiveVisibilityThanFeature(f, c);
            }
        }
      }, f));
  }


  /**
   * Check that `f` defines type if type visibility is explicitly specified.
   *
   * @param f
   */
  private void checkLegalTypeVisibility(Feature f)
  {
    if (!f.definesType() && f.visibility().definesTypeVisibility())
      {
        AstErrors.illegalVisibilityModifier(f);
      }
  }


  /**
   * Check that the types of the arguments are at least as visible as `f`.
   *
   * @param f
   */
  private void checkArgTypesVisibility(Feature f)
  {
    for (AbstractFeature arg : f.arguments())
      {
        if (!arg.isTypeFeaturesThisType())
          {
            var s = arg.resultType().moreRestrictiveVisibility(effectiveFeatureVisibility(f));
            if (!s.isEmpty())
              {
                AstErrors.argTypeMoreRestrictiveVisbility(f, arg, s);
              }
          }
      }
  }


  /**
   * Check that result type is at least as visible as feature `f`.
   *
   * @param f
   */
  private void checkResultTypeVisibility(Feature f)
  {
    var s = f.resultType().moreRestrictiveVisibility(effectiveFeatureVisibility(f));
    if (!s.isEmpty())
      {
        AstErrors.resultTypeMoreRestrictiveVisibility(f, s);
      }
  }


  /**
   * Returns the effective visibility of a feature.
   *
   * Example:
   * A feature might be marked as public but one its
   * outer features type visibility is private.
   * Then the features effective visibility is also private.
   */
  private Visi effectiveFeatureVisibility(Feature f)
  {
    var result = f.visibility().featureVisibility();
    var o = f.outer();
    while (o != null)
      {
        var ov = o.visibility().typeVisibility();
        result = ov.ordinal() < result.ordinal() ? ov : result;
        o = o.outer();
      }
    return result;
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
