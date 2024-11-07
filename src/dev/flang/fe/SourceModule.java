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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.SortedMap;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.ast.AbstractAssign;
import dev.flang.ast.AbstractBlock;
import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractCase;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.AstErrors;
import dev.flang.ast.Block;
import dev.flang.ast.Call;
import dev.flang.ast.Context;
import dev.flang.ast.Current;
import dev.flang.ast.Expr;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.FeatureAndOuter;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.Function;
import dev.flang.ast.Resolution;
import dev.flang.ast.SrcModule;
import dev.flang.ast.State;
import dev.flang.ast.Types;
import dev.flang.ast.Visi;

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
public class SourceModule extends Module implements SrcModule
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
   * The universe is the implicit root of all features that
   * themselves do not have their own root.
   */
  final Feature _universe;


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
  SourceModule(FrontEndOptions options, SourceDir[] sourceDirs, LibraryModule[] dependsOn, Feature universe)
  {
    super(dependsOn);

    _options = options;
    _sourceDirs = sourceDirs;
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
   * Get the path of main input file (when compiling from stdin or just one
   * single source file).
   */
  private Path inputFile()
  {
    return
      _options._readStdin           ? SourceFile.STDIN              :
      _options._inputFile != null   ? _options._inputFile           :
      _options._executeCode != null ? SourceFile.COMMAND_LINE_DUMMY : null;
  }


  /**
   * If source comes from stdin or an explicit input file, parse this and
   * extract the main feature.  Otherwise, return the default main.
   *
   * @return the main feature found or _defaultMain if none
   */
  String parseMain()
  {
    var res = _options._main;
    var p = inputFile();
    var ec = _options._executeCode;
    if (p != null || ec != null)
      {
        var expr = parseFile(p, ec);
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
  List<Expr> parseFile(Path p, byte[] ec)
  {
    if (ec != null)
      {
        _options.verbosePrintln(2, " - " + p);
      }
    return new Parser(p, ec).unit();
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
    var exprs = parseFile(p, null);
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
        new Types.Resolved(this, _universe, true);
      }

    _main = parseMain();
    findDeclarations(_universe, null);
    _universe.scheduleForResolution(_res);
    _res.resolve();
  }


  public void checkMain()
  {
    if (_main != null)
      {
        var main = this._universe.get(this, _main);
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
              case Routine  :
                if (!main.generics().list.isEmpty())
                  {
                    FeErrors.mainFeatureMustNotHaveTypeArguments(main);
                  }
                break;
              default       : FeErrors.mainFeatureMustNot(main, "be of kind " + main.kind() + ".");
              }
          }
      }
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
   * same as inputFile() (which will be read explicitly).
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
        var inputFile = inputFile();
        return p.getFileName().toString().endsWith(".fz") &&
          !Files.isDirectory(p) &&
          Files.isReadable(p) &&
          (inputFile == null || inputFile == SourceFile.STDIN || !Files.isSameFile(inputFile, p));
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
files matching rule xref:SRCF_DOTFZ[SRCF_DOTFZ] within that directory are parsed as if they were
part of the (((inner features))) declarations of the corresponding
((constructor)).
                    // end::fuzion_rule_SRCF_DIR[]
                    */

                    Files.list(d._dir)
                      .filter(p -> isValidSourceFile(p))
                      .sorted(Comparator.comparing(p -> p.toString()))
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
        // NYI inner.iscotype() does not work currently
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
    outer.whenResolvedDeclarations(()->
      {
        var q = inner._qname;
        var n = q.get(at);
        if (n == FuzionConstants.TYPE_NAME && outer.isUniverse())
          {
            AstErrors.mustNotDefineTypeFeatureInUniverse(inner);
          }
        else if (n == FuzionConstants.TYPE_NAME && !outer.definesType())
          {
            AstErrors.typeFeaturesMustOnlyBeDeclaredInFeaturesThatDefineType(inner);
          }
        var o =
          n != FuzionConstants.TYPE_NAME ? lookupType(inner.pos(), outer, n, at == 0,
                                                      false /* ignore ambiguous */,
                                                      false /* ignore not found */)._feature
                                        : outer.cotype(_res);
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
        private Stack<Expr> _scope = new Stack<>();
        @Override
        public void actionBefore(AbstractCase c)
        {
          _scope.push(c.code());
          super.actionBefore(c);
        }
        @Override
        public void actionAfter(AbstractCase c)
        {
          _scope.pop();
          super.actionAfter(c);
        }
        @Override
        public void actionBefore(Block b, AbstractFeature outer)
        {
          if (b._newScope) { _scope.push(b); }
          super.actionBefore(b, outer);
        }
        @Override
        public void actionAfter(Block b, AbstractFeature outer)
        {
          if (b._newScope) { _scope.pop(); }
          super.actionAfter(b, outer);
        }
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
        public Feature   action(Feature   f, AbstractFeature outer)
        {
          f._scoped = !_scope.isEmpty();
          findDeclarations(f, outer); return f;
        }
      });

    if (inner.impl().hasInitialValue() &&
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
   * This is somewhat ugly since it adds cotype to the declaredFeatures or
   * declaredOrInheritedFeatures of the outer types even after those had been
   * determined already.
   *
   * @param outerType the static outer type of universe.
   *
   * @param cotype the new type feature declared within outerType.
   */
  public void addCotype(AbstractFeature outerType,
                             Feature cotype)
  {
    findDeclarations(cotype, outerType);
    addDeclared(outerType, cotype);
    cotype.scheduleForResolution(_res);
    resolveDeclarations(cotype);
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
    var doi = d._declaredOrInheritedFeatures;
    if (doi != null)
      {
        if (CHECKS) check
          (Errors.any() || !doi.containsKey(fn) || doi.get(fn).size() == 1 && doi.get(fn).getFirst() == typeParameter);
        add(doi, fn, typeParameter);
      }
  }


  /**
   * Add inner feature to the set of declared (or inherited) features of outer.
   *
   * NYI: CLEANUP: This is a little ugly since it is used to add type features
   * while the sets of declared and inherited features had already been
   * determined.
   *
   * @param outer the outer feature
   *
   * @param inner the feature to be added.
   */
  private void addDeclared(AbstractFeature outer, AbstractFeature inner)
  {
    if (PRECONDITIONS)
      require(outer.isConstructor(), inner.isCotype());

    var d = data(outer);
    var fn = inner.featureName();
    var doi = d._declaredOrInheritedFeatures;
    if (doi != null)
      {
        if (CHECKS) check
          (!doi.containsKey(fn) || doi.get(fn).size() == 1 && doi.get(fn).getFirst() == inner);
        add(doi, fn, inner);
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
  // NYI: merge addToDeclaredOrInheritedFeatures and addDeclaredOrInherited
  private void addToDeclaredOrInheritedFeatures(AbstractFeature outer, AbstractFeature f)
  {
    var fn = f.featureName();
    var isInherited = outer != f.outer();
    var c = f.contract();
    for (var existing : declaredOrInheritedFeatures(outer, fn))
      {
        if ((
             // declarations do not have to satisfy visibility rules
             !isInherited ||
             // inherited features must be visible where we inherit them
             visibleFor(f, outer)
             )
            &&
            existing != f
            )
          {
            if ((f.modifiers() & FuzionConstants.MODIFIER_REDEFINE) == 0 &&
                /* previous duplicate feature declaration could result in this error for
                * type features, so suppress them in this case. See fuzion-lang.dev's
                * design/examples/typ_const2.fz as an example.
                */
                (!Errors.any() || !f.isCotype()))
              {
                /*
    // tag::fuzion_rule_PARS_REDEF[]
A feature that redefines at least one inherited feature must use the `redef` modifier.
    // end::fuzion_rule_PARS_REDEF[]
                */
                if (visibleFor(existing, f.outer()))
                  {
                    AstErrors.redefineModifierMissing(f.pos(), f, existing);
                  }
              }
            else if (c._hasPre != null && c._hasPreElse == null)
              {
                /*
    // tag::fuzion_rule_PARS_CONTR_PRE_ELSE[]
A pre-condition of a feature that redefines one or several inherited features must start with `pre else`, independent of whether the redefined, inherited features are `abstract` or not.
    // end::fuzion_rule_PARS_CONTR_PRE_ELSE[]
                */
                AstErrors.redefinePreconditionMustUseElse(c._hasPre, f);
              }
            else if (c._hasPost != null && c._hasPostThen == null)
              {
                /*
    // tag::fuzion_rule_PARS_CONTR_POST_THEN[]
A post-condition of a feature that redefines one or several inherited features must start with `post else`, independent of whether the redefined, inherited features are `abstract` or not.
    // end::fuzion_rule_PARS_CONTR_POST_THEN[]
                */
                AstErrors.redefinePostconditionMustUseThen(c._hasPost, f);
              }
            if (visibleFor(existing, f.outer()))
              {
                if (existing.isNonArgumentField() ||
                    // @fridis writes: "If we redefine an argument field by a field that is not an argument field,
                    //                  we run into strange situations where the same field is initialized twice,
                    //                  once via existing in the inheritance call and once va f by the field declaration."
                    existing.isArgument() && !f.isArgument())
                  {
                    AstErrors.redefiningFieldsIsForbidden(existing, f);
                  }
                f.redefines().add(existing);
                if (f instanceof Feature ff)
                  {
                    c.addInheritedContract(ff, existing);
                  }
              }
          }
      }

    if (f.redefines().isEmpty())
      {
        if ((f.modifiers() & FuzionConstants.MODIFIER_REDEFINE) != 0)
          {
            /*
    // tag::fuzion_rule_PARS_NO_REDEF[]
A feature that does not redefine an inherited feature must not use the `redef` modifier.
    // end::fuzion_rule_PARS_NO_REDEF[]
            */
            List<FeatureAndOuter> hiddenFeaturesSameSignature = lookup(outer, f.featureName().baseName(), null, true, true)
              .stream().filter(fo->fo._feature.featureName().equals(f.featureName())).collect(List.collector());
            AstErrors.redefineModifierDoesNotRedefine(f, hiddenFeaturesSameSignature);
          }
        else if (c._hasPreElse != null)
          {
            /*
    // tag::fuzion_rule_PARS_CONTR_PRE_NO_ELSE[]
A pre-condition of a feature that does not redefine an inherited feature must start with `pre`, not `pre else`.
    // end::fuzion_rule_PARS_CONTR_PRE_NO_ELSE[]
            */
            AstErrors.notRedefinedPreconditionMustNotUseElse(c._hasPreElse, f);
          }
        else if (c._hasPostThen != null)
          {
            /*
    // tag::fuzion_rule_PARS_CONTR_POST_NO_THEN[]
A post-condition of a feature that does not redefine an inherited feature must start with `post`, not `post then`.
    // end::fuzion_rule_PARS_CONTR_POST_NO_THEN[]
            */
            AstErrors.notRedefinedPostconditionMustNotUseThen(c._hasPostThen, f);
          }
      }

    // This is a fix for #978 but it might need to be removed when fixing #932.
    if (f instanceof Feature ff &&
        (outer.state().atLeast(State.RESOLVED_DECLARATIONS) ||
            ff.state().atLeast(State.RESOLVED_DECLARATIONS)))
      {
        ff._addedLate = true;
      }
    var doi = declaredOrInheritedFeatures(outer);
    doi.remove(fn);  // NYI: remove only those features that are redefined by f!
    add(doi, fn, f);
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
        // NYI: need to check that the scopes are disjunct
        if (existing instanceof Feature ef
            && (ef._scoped && f._scoped || visibilityPreventsConflict(f, ef)))
          {
            var existingFeatures = FeatureName.getAll(df, fn.baseName(), 0);
            fn = FeatureName.get(fn.baseName(), 0, existingFeatures.size());
            f.setFeatureName(fn);
          }
        else
          {
            if (existing instanceof Feature ef && ef.isArgument() && f.isArgument() && !f.isTypeParameter())
              {
                // NYI: CLEANUP: there should not be two places where
                // similar error is raised.
                // An error should have been raised already in feature constructor:
                // "Names of arguments used in this feature must be distinct"
                check(Errors.any());
              }
            else
              {
                AstErrors.duplicateFeatureDeclaration(f, existing);
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
   * Check if both features are fully private and
   * in different files and thus not conflicting
   * each other.
   */
  private boolean visibilityPreventsConflict(Feature f1, Feature f2)
  {
    return f2.visibility().typeVisibility() == Visi.PRIV
         && f1.visibility().typeVisibility() == Visi.PRIV
         && !f2.pos()._sourceFile._fileName.equals(f1.pos()._sourceFile._fileName);
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
    if (d != null && !f.isFixed())
      {
        for (var h : d._heirs)
          {
            addDeclaredOrInherited(data(outer)._declaredOrInheritedFeatures, h, fn, f);
            addToHeirs(h, fn, f);
          }
      }
  }





  /*--------------------------  feature lookup  -------------------------*/


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
    var result = new List<AbstractFeature>();
    forEachDeclaredOrInheritedFeature(outer,
                                      f ->
                                      {
                                        if (featureVisible(use.pos()._sourceFile, f) &&
                                            // NYI: UNDER DEVELOPMENT: this means selector .0 .1 can only be used for library features currently
                                            f instanceof LibraryFeature lf &&
                                            lf.resultType().isOpenGeneric() &&
                                            f.arguments().isEmpty())
                                          {
                                            result.add(f);
                                          }
                                      });
    return result.size() == 1 ? result.getFirst() : null;
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
   * @param hidden true to return features that are not visible, used for error
   * messages.
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
   * @param hidden true to return features that are not visible, used for error
   * messages.
   *
   * @return in case we found features visible in the call's scope, the features
   * together with the outer feature where they were found.
   */
  private List<FeatureAndOuter> lookup0(AbstractFeature outer, String name, Expr use, boolean traverseOuter, boolean hidden)
  {
    if (PRECONDITIONS) require
      (outer.state().atLeast(State.RESOLVED_INHERITANCE) || outer.isUniverse());

    List<FeatureAndOuter> result = new List<>();
    var curOuter = outer;
    AbstractFeature inner = null;
    do
      {
        if (!curOuter.state().atLeast(State.RESOLVING_DECLARATIONS))
          {
            _res.resolveDeclarations(curOuter);
          }
        var fs = FeatureName.getAll(declaredOrInheritedFeatures(curOuter), name);

        for (var e : fs.entrySet())
          {
            for (var v : e.getValue())
              {
                if ((use == null || (hidden != featureVisible(use.pos()._sourceFile, v))) &&
                    !(use instanceof Call c && !c._isInheritanceCall && v.isChoice()) &&
                    (use == null || /* NYI: do we have to evaluate inScope for all possible outers? */ inScope(use, v)))
                  {
                    result.add(new FeatureAndOuter(v, curOuter, inner));
                  }
              }
          }

        inner = curOuter;
        curOuter = curOuter.outer();
      }
    while (traverseOuter && curOuter != null);
    return result;
  }


  /**
   * true if `use` is happening in same or some
   * inner scope of where definition of `v` is.
   *
   * see also: tests/visibility_scoping
   * see also: tests/visibility_negative
   *
   * @param use
   * @param v
   * @return
   */
  @SuppressWarnings({
    "rawtypes", "unchecked"
  })
  private boolean inScope(Expr use, AbstractFeature v)
  {
    // we only need to do this evaluation for:
    // - scoped routines
    // - non argument fields
    if (v instanceof Feature f && (f._scoped || v.isField() && !f.isArgument()))
      {
        /* cases like the following are forbidden:
          * ```
          * a := b
          * b := 1
          * ```
          */
        var useIsBeforeDefinition = new Boolean[]{ false };
         /* while cases like these are allowed:
          * ```
          * a => b
          * b := 1
          * ```
          */
        var visitingInnerFeature = new Boolean[]{ false };
        var usage = new ArrayList<Stack<Expr>>();
        var definition = new ArrayList<Stack<Expr>>();
        var stacks = new ArrayList<Stack<Expr>>();
        stacks.add(new Stack<>());
        var visitor = new FeatureVisitor() {
          public void action(AbstractAssign a, AbstractFeature outer)
          {
            if (use == a)
              {
                useIsBeforeDefinition[0] = definition.isEmpty() && !visitingInnerFeature[0];
                usage.add((Stack)stacks.get(0).clone());
              }
            super.action(a, outer);
          }
          public void action(AbstractCall c) {
            if (use == c)
              {
                useIsBeforeDefinition[0] = definition.isEmpty() && !visitingInnerFeature[0];
                usage.add((Stack)stacks.get(0).clone());
              }
          };
          public Expr action(Function lambda, AbstractFeature outer) {
            if (usage.isEmpty() || definition.isEmpty())
              {
                stacks.get(0).push(lambda._expr);
                lambda._expr.visit(this, outer);
                stacks.get(0).pop();
              }
            return lambda;
          };
          public Expr action(Feature f2, AbstractFeature outer) {
            if (f == f2)
              {
                definition.add((Stack)stacks.get(0).clone());
              }
            // fields are visited by their outers
            if (usage.isEmpty() && f2.isRoutine())
              {
                stacks.get(0).push(f2);
                var old = visitingInnerFeature[0];
                visitingInnerFeature[0] = true;
                f2.impl().visit(this, outer);
                visitingInnerFeature[0] = old;
                stacks.get(0).pop();
              }
            return f2;
          };
          public void actionBefore(Block b, AbstractFeature outer)
          {
            if (b._newScope)
              {
                stacks.get(0).push(b);
              }
          }
          public void  actionAfter(Block b, AbstractFeature outer)
          {
            if (b._newScope)
              {
                stacks.get(0).pop();
              }
          }
          public void actionBefore(AbstractCase c)
          {
            stacks.get(0).push(c.code());
          }
          public void  actionAfter(AbstractCase c)
          {
            stacks.get(0).pop();
          }
        };
        f.outer().code().visit(visitor, null);

        if (f.isField())
          {
            /**
              * cases like this are okay:
              *   ring(r ring) is
              *      last ring := r.last
              */
            if (useIsBeforeDefinition[0] && !(use instanceof AbstractCall ac && ac.target() instanceof AbstractCall))
              {
                return false;
              }
            // the usage of this field is not in its containing features.
            if (usage.isEmpty())
              {
                return !f._scoped;
              }
          }

        var u = new ArrayList<>(usage.get(0));
        var d = new ArrayList<>(definition.get(0));

        if (d.size() > u.size())
          {
            return false;
          }
        else
          {
            for (int i = 0; i < d.size(); i++)
              {
                if (d.get(i) != u.get(i))
                  {
                    return false;
                  }
              }
          }
        return true;
      }
    // definition is neither field nor in an inner scope,
    // no need to do anything
    else
      {
        return true;
      }
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
   * @param ignoreAmbiguous If true, no errors are produced but null might be
   * returned if type is ambiguous.
   *
   * @param ignoreNotFound If true, no errors are produced but null might be
   * returned in case type was not found.
   *
   * @return FeatureAndOuter tuple of the found type's declaring feature,
   * FeatureAndOuter.ERROR in case of an error, null in case no type was found
   * and ignoreNotFound is true.
   */
  public FeatureAndOuter lookupType(SourcePosition pos,
                                    AbstractFeature outer,
                                    String name,
                                    boolean traverseOuter,
                                    boolean ignoreAmbiguous,
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
        var o = outer;
        while (traverseOuter && o != null)
          {
            if (o.isCotype())
              {
                lookup(o._cotypeOrigin, name, null, false, false)
                  .stream()
                  .filter(fo -> !fo._feature.isTypeParameter())  // type parameters are duplicated in type feature and taken from there
                  .forEach(fo -> fs.add(fo));
              }
            o = o.outer();
          }
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
        if (type_fs.size() > 1 && !ignoreAmbiguous)
          {
            AstErrors.ambiguousType(pos, name, type_fs);
            result = FeatureAndOuter.ERROR;
          }
        else if (type_fs.size() < 1 && !ignoreNotFound)
          {
            AstErrors.typeNotFound(pos, name, outer, nontype_fs);
          }
        else if (type_fs.size() != 1)
          {
            result = null;
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
   * @param context the source code context
   *
   * @return true if `to` may be replaced with `tr` or if `to` or `tr` contain
   * an error.
   */
  boolean isLegalCovariantThisType(AbstractFeature original,
                                   Feature redefinition,
                                   AbstractType to,
                                   AbstractType tr,
                                   boolean fixed,
                                   Context context)
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
      redefinition.outer().thisType(true).actualType(to, Context.NONE).compareTo(tr) == 0       ||

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
      original    .outer().isCotype() &&
      redefinition.outer().isCotype() &&
      to.replace_this_type_in_cotype(redefinition.outer())
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
   *
   * @param context the source code context
   */
  public void checkTypes(Feature f, Context context)
  {
    if (!f.isVisibilitySpecified() && !f.redefines().isEmpty())
      {
        f.setVisbility(f.redefines().stream().map(r -> r.visibility()).sorted().findAny().get());
      }

    f.impl().checkTypes(f);
    var args = f.arguments();
    var fixed = (f.modifiers() & FuzionConstants.MODIFIER_FIXED) != 0;
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
                var t1 = ta[i].applyTypePars(o, f.generics().asActuals());  /* replace o's type pars by f's */
                var t2 = ra[i];
                if (!isLegalCovariantThisType(o, f, t1, t2, fixed, context))
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
                                                                 isLegalCovariantThisType(o, f, t1, t2, true, context));
                  }
              }
          }

        var t1 = o.handDownNonOpen(_res, o.resultType(), f.outer())
                  .applyTypePars(o, f.generics().asActuals());    /* replace o's type pars by f's */
        var t2 = f.resultType();
        if (o.isCoTypesThisType() && f.isCoTypesThisType())
          { // NYI: CLEANUP: #706: allow redefinition of THIS_TYPE in type features for now, these are created internally.
          }
        else if (o.isConstructor() ||
                 switch (o.kind())
                 {
                   case Routine, Field, Intrinsic, Abstract, Native -> false; // ok
                   case TypeParameter, OpenTypeParameter, Choice    -> true;  // not ok
                 })
          {
            /*
    // tag::fuzion_rule_PARS_REDEF_KIND[]
A feature that is a constructor, choice or a type parameter may not be redefined.
    // end::fuzion_rule_PARS_REDEF_KIND[]
            */
            AstErrors.cannotRedefine(f, o);
          }
        else if (f.isConstructor() ||
                 switch (f.kind())
                 {
                   case Routine, Field, Intrinsic, Abstract, Native -> false; // ok
                   case TypeParameter, OpenTypeParameter, Choice    -> true;  // not ok
                 })
          {
            /*
    // tag::fuzion_rule_PARS_REDEF_AS_KIND[]
A feature that is a constructor, choice or a type parameter may not redefine an inherited feature.
    // end::fuzion_rule_PARS_REDEF_AS_KIND[]
            */
            AstErrors.cannotRedefine(f, o);
          }
        else if (!t1.isDirectlyAssignableFrom(t2, context) &&  // we (currently) do not tag the result in a redefined feature, see testRedefine
                 !t2.isVoid() &&
                 !isLegalCovariantThisType(o, f, t1, t2, fixed, context))
          {
            AstErrors.resultTypeMismatchInRedefinition(o, t1, f, isLegalCovariantThisType(o, f, t1, t2, true, context));
          }
      }

    if (f.isConstructor())
      {
        var cod = f.code();
        var rt = cod.type();
        if (!Types.resolved.t_unit.isAssignableFrom(rt, context))
          {
            AstErrors.constructorResultMustBeUnit(cod);
          }
      }

    if (f.isTypeParameter() &&
        !f.outer().isCotype()) // reg_issue1932 shows error twice without this)
      {
        if (f.resultType().isGenericArgument())
          {
            AstErrors.constraintMustNotBeGenericArgument(f);
          }
        if (  !f.isCoTypesThisType() // NYI: CLEANUP: #706: remove special handling for 'THIS_TYPE'
            && f.resultType().isChoice())
          {
            AstErrors.constraintMustNotBeChoice(f);
          }
      }
    checkLegalVisibility(f);
    checkRedefVisibility(f);
    checkLegalTypeVisibility(f);
    checkResultTypeVisibility(f);
    checkArgVisibility(f);
    checkArgTypesVisibility(f);
    checkPreconditionVisibility(f);
    checkAbstractVisibility(f);
    checkDuplicateFeatures(f);
    checkContractAccesses(f);
    checkLegalQualThisType(f);
    checkLegalDefinesType(f);
  }


  private void checkLegalDefinesType(Feature f)
  {
    if (f.definesUsableType() && inTypeFeature(f))
      {
        AstErrors.illegalFeatureDefiningType(f);
      }
  }


  private boolean inTypeFeature(AbstractFeature f)
  {
    return f.isTypeFeature() || (f.outer() != null && inTypeFeature(f.outer()));
  }


  /*
   * check that arguments of functions etc.
   * do not have visibility modifier.
   */
  private void checkArgVisibility(Feature f)
  {
    if (
        f.isArgument()
     && !f.outer().definesType()
     && !f.outer().isCotype()
     && f.visibility() != Visi.PRIV
    )
      {
        AstErrors.illegalVisibilityArgument(f);
      }
  }


  /**
   * check that all `.this` are legal,
   * i.e. that f or any outer of f are
   * what is supposed to be qualified
   *
   * @param f
   */
  private void checkLegalQualThisType(Feature f)
  {
    if (f.resultType().isThisType())
      {
        var subject = f.resultType().feature();
        var found = false;
        AbstractFeature o = f;
        while(o != null)
          {
            if (subject == o)
              {
                found = true;
                break;
              }
            o = o.outer();
          }
        if (!found &&
          // okay for post condition features result field
          !(f.isResultField() && f.outer().featureName().baseName().startsWith(FuzionConstants.POSTCONDITION_FEATURE_PREFIX)))
          {
            AstErrors.illegalResultTypeThisType(f);
          }
      }
  }


  private void checkLegalVisibility(Feature f)
  {
    if (f.isRoutine())
      {
        f.code().visit(new FeatureVisitor()
        {
          private Stack<Object> stack = new Stack<Object>();
          public void actionBefore(Block b, AbstractFeature outer) { if (b._newScope) { stack.push(b); } }
          public void actionBefore(AbstractCase c) { stack.push(c); }
          public void actionAfter(Block b, AbstractFeature outer)  { if (b._newScope) { stack.pop(); } }
          public void actionAfter (AbstractCase c) { stack.pop(); }
          public Expr action(Feature fd, AbstractFeature outer) {
            if (!stack.isEmpty() && !fd.visibility().equals(Visi.PRIV))
              {
                AstErrors.illegalVisibilityModifier(fd);
              }
            return super.action(fd, outer);
          }
        }, f);
      }
  }


  /**
   * Check that an abstract feature is at least as visible as the outer feature.
   */
  private void checkAbstractVisibility(Feature f) {
    if(f.isAbstract() &&
       f.visibility().eraseTypeVisibility().ordinal() < f.outer().visibility().eraseTypeVisibility().ordinal())
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
    if (!f.isCoTypesThisType()
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
      ._declared_preconditions
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
              && c.calledFeature().visibility().eraseTypeVisibility().ordinal() < f.visibility().eraseTypeVisibility().ordinal())
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
        AstErrors.illegalTypeVisibilityModifier(f);
      }
    else if(f.definesType() && f.outer() != null && f.outer().visibility().typeVisibility().ordinal() < f.visibility().typeVisibility().ordinal())
      {
        AstErrors.illegalTypeVisibility(f);
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
        if (!arg.isCoTypesThisType())
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
    var result = f.visibility().eraseTypeVisibility();
    var o = f.outer();
    while (o != null)
      {
        var ov = o.visibility().typeVisibility();
        result = ov.ordinal() < result.ordinal() ? ov : result;
        o = o.outer();
      }
    return result;
  }


  /**
   * Check f's declared or inherited features for duplicates and flag errors if
   * incompatible duplicates are encountered.
   *
   * @param f a feature
   */
  private void checkDuplicateFeatures(Feature f)
  {
    var doi = data(f)._declaredOrInheritedFeatures;
    if (doi != null)
      {
        for (var fn : doi.keySet())
          {
            checkDuplicateFeatures(f, fn, doi.get(fn));
          }
      }
  }

  /**
   * Check outer's declared or inherited features with effective name `fn` for duplicates and flag errors if
   * incompatible duplicates are encountered.
   *
   * @param outer a feature
   *
   * @param fn the effective feature name within outer, used for error messages only
   *
   * @param fl list of features declared or inherited by outer with effective name fn.
   */
  private void checkDuplicateFeatures(AbstractFeature outer, FeatureName fn, List<AbstractFeature> fl)
  {
    if (PRECONDITIONS)
      require(outer != null,
              fn != null,
              fl != null);

    for (var f1 : fl)
      {
        for (var f2 : fl)
          {
            if (f1 != f2)
              {
                var isInherited1 = outer != f1.outer();
                var isInherited2 = outer != f2.outer();

                // NYI: take visibility into account!!!
                if (isInherited1 && isInherited2)
                  { // NYI: Should be ok if existing or f is abstract.
                    AstErrors.repeatedInheritanceCannotBeResolved(outer.pos(), outer, fn, f1, f2);
                  }
                else
                  {
                    // NYI: if (!isInherited && !sameModule(f, outer))
                    AstErrors.duplicateFeatureDeclaration(f1, f2);
                  }
              }
          }
      }
  }


  /**
   * Check that code in contract does not access inner features apart from
   * arguments, result field, outer refs or case fields (in case condition uses
   * a `match`). Produce AstErrors if needed.
   *
   * @parm f the feature whose contract should be checked.
   */
  private void checkContractAccesses(AbstractFeature f)
  { //NYI: CLEANUP:  remove, has no effect.
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
