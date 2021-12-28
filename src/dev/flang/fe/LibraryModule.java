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
 * Source of class LibraryModule
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.ByteBuffer;

import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.AstErrors;
import dev.flang.ast.Expr;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.FormalGenerics;
import dev.flang.ast.Generic;
import dev.flang.ast.Type;
import dev.flang.ast.Types;

import dev.flang.ir.IR;

import dev.flang.mir.MIR;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourceDir;
import dev.flang.util.SourcePosition;


/**
 * A LibraryModule represents a Fuzion module loaded from a precompiled Fuzion
 * module file .fum.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class LibraryModule extends Module
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Temporary solution to switch to .fum file data completely.
   *
   * NYI: Remove when we have switched to .fum file!
   */
  public static boolean USE_FUM = "true".equals(System.getenv("USE_FUM"));


  /**
   * As long as source position is not part of the .fum/MIR file, use this
   * constant as a place holder.
   */
  static SourcePosition DUMMY_POS = SourcePosition.builtIn;


  /*----------------------------  variables  ----------------------------*/


  /**
   * Configuration
   */
  public final FrontEndOptions _options;


  /**
   * NYI: For now, a LibraryModule is just a wrapper around a SourceModule.
   * This will change once the source module can actually be saved to a file.
   */
  public final SourceModule _srcModule;


  /**
   * The module name, used for debug output.
   */
  final String _name;


  /**
   * The module binary data, contents of .mir file.
   */
  final ByteBuffer _data;


  /**
   * The module intermediate representation for this module.
   */
  final MIR _mir;


  /**
   * Map from offset in _data to LibraryFeatures for features in this module.
   */
  TreeMap<Integer, LibraryFeature> _libraryFeatures = new TreeMap<>();


  /**
   * Map from offset in _data to LibraryType for types in this module.
   */
  TreeMap<Integer, LibraryType> _libraryTypes = new TreeMap<>();


  /**
   * Cache for 'normal' code created from given index
   */
  Map<Integer, Expr> _code = new TreeMap<>();

  /**
   * Cache for inheritance call code created from given index
   */
  Map<Integer, Expr> _code1 = new TreeMap<>();


  /**
   * The universe
   */
  final Feature _universe;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create LibraryModule for given options and sourceDirs.
   */
  LibraryModule(FrontEndOptions options, String name, SourceDir[] sourceDirs, Path inputFile, String defaultMain, Module[] dependsOn, Feature universe)
  {
    super(dependsOn);

    _options = options;
    _srcModule = new SourceModule(options, sourceDirs, inputFile, defaultMain, dependsOn, universe);
    _name = name;
    _mir = _srcModule.createMIR();
    _data = _mir._module.data();
    _universe = universe;
  }


  /**
   * Create LibraryModule for given options and sourceDirs.
   */
  LibraryModule(FrontEndOptions options, String name, ByteBuffer data, Module[] dependsOn, Feature universe)
  {
    super(dependsOn);

    _options = options;
    _srcModule = null;
    _name = name;
    _mir = null;
    _data = data;
    _universe = universe;
  }

  /*-----------------------------  methods  -----------------------------*/


  /**
   * The universe
   */
  Feature universe()
  {
    return _universe;
  }


  /**
   * The module binary data, contents of .mir file.
   */
  public ByteBuffer data()
  {
    return _data;
  }


  /**
   * Create the module intermediate representation for this module.
   */
  public MIR createMIR()
  {
    return _mir;
  }


  /**
   * Get declared features for given outer Feature as seen by this module.
   * Result is null if outer has no declared features in this module.
   *
   * @param outer the declaring feature
   */
  SortedMap<FeatureName, AbstractFeature>declaredFeaturesOrNull(AbstractFeature outer)
  {
    if (USE_FUM)
      {
        return declaredFeatures(outer);
      }
    else
      {
        var sdf = _srcModule.declaredFeaturesOrNull(outer.astFeature());
        return sdf == null ? null : libraryFeatures(sdf);
      }
  }
  SortedMap<FeatureName, AbstractFeature>declaredFeatures(AbstractFeature outer)
  {
    if (USE_FUM)
      {
        var result = new TreeMap<FeatureName, AbstractFeature>();
        if (outer instanceof LibraryFeature lf && lf._libModule == this)
          {
            var l = lf.declaredFeatures();
            for (var d : l)
              {
                result.put(d.featureName(), d);
              }
          }
        return result;
      }
    else
      {
        var sdf = _srcModule.declaredFeaturesOrNull(outer.astFeature());
        return sdf == null ? new TreeMap<>() : libraryFeatures(sdf);
      }
  }


  /**
   * Helper method for declaredFeaturesOrNull and
   * declaredOrInheritedFeaturesOrNull: Wrap ast.Feature into LibraryFeature.
   */
  private SortedMap<FeatureName, AbstractFeature> libraryFeatures(SortedMap<FeatureName, AbstractFeature> from)
  {
    SortedMap<FeatureName, AbstractFeature> result = new TreeMap<>();
    for (var e : from.entrySet())
      {
        result.put(e.getKey(), libraryFeature(e.getValue()));
      }
    return result;
  }


  /**
   * Wrap given Feature into a LibraryFeature unless it is a LibraryFeature
   * already.  In case f was wrapped before, returns the original wrapper.
   */
  LibraryFeature libraryFeature(AbstractFeature f)
  {
    return (LibraryFeature) (
      f instanceof LibraryFeature                               ? f                    :
      f instanceof Feature astF && astF._libraryFeature != null ? astF._libraryFeature
                                                                : libraryFeature(_srcModule.data(f)._mirOffset, (Feature) f));
  }


  /**
   * Get or create LibraryFeature at given offset
   *
   * @param offset the offset in data()
   *
   * @param from the original AST Feature. NYI: Remove!
   *
   * @return the LibraryFeature declared at offset in this module.
   */
  LibraryFeature libraryFeature(int offset, Feature from)
  {
    var result = _libraryFeatures.get(offset);
    if (result == null)
      {
        result = new LibraryFeature(this, offset, from);
        _libraryFeatures.put(offset, result);
      }
    return result;
  }


  /**
   * The features declared within universe by this module
   */
  List<AbstractFeature> features()
  {
    return innerFeatures(startPos());
  }


  /**
   * The features declared within universe by this module
   */
  SortedMap<FeatureName, AbstractFeature> featuresMap()
  {
    var res = new TreeMap<FeatureName, AbstractFeature>();
    for (var f : features())
      {
        res.put(f.featureName(), f);
      }
    return res;
  }


  /**
   * The features declared at given InnerFeatures block.
   *
   * @param at the index of an InnerFeatures block.
   */
  List<AbstractFeature> innerFeatures(int at)
  {
    var result = new List<AbstractFeature>();
    var is = innerFeaturesSize(at);
    var ip = innerFeaturesFeaturesPos(at);
    for (var i = ip; i < ip+is; i = featureNextPos(i))
      {
        result.add(libraryFeature(i, null));
      }
    return result;
  }


  /**
   * Get declared and inherited features for given outer Feature as seen by this
   * module.  Result may be null if this module does not contribute anything to
   * outer.
   *
   * @param outer the declaring feature
   */
  SortedMap<FeatureName, AbstractFeature>declaredOrInheritedFeaturesOrNull(AbstractFeature outer)
  {
    if (USE_FUM)
      {
        var res = new TreeMap<FeatureName, AbstractFeature>();
        if (outer instanceof LibraryFeature olf)
          {
            var declared = olf.declaredFeatures();
            for (var d : declared)
              {
                res.put(d.featureName(), d);
              }
            findInheritedFeatures(res, outer);
          }
        else if (outer.isUniverse())
          {
            var declared = features();
            for (var d : declared)
              {
                res.put(d.featureName(), d);
              }
          }
        return res;
      }
    else
      {
        var sdif = _srcModule.declaredOrInheritedFeaturesOrNull(outer.astFeature());
        return sdif == null ? null : libraryFeatures(sdif);
      }
  }

  /**
   * Find all inherited features and add them to declaredOrInheritedFeatures_.
   * In case an existing feature was found, check if there is a conflict and if
   * so, report an error message (repeated inheritance).
   *
   * NYI: This is somewhat redundant with SourceModule.findInheritedFeatures,
   * maybe join these two as Module.findInheritedFeatures?
   *
   * @param outer the declaring feature
   */
  private void findInheritedFeatures(SortedMap<FeatureName, AbstractFeature> set, AbstractFeature outer)
  {
    for (var p : outer.inherits())
      {
        var cf = p.calledFeature().libraryFeature();
        check
          (Errors.count() > 0 || cf != null);

        if (cf != null)
          {
            //data(cf)._heirs.add(outer);
            //_res.resolveDeclarations(cf);
            if (cf instanceof LibraryFeature clf)
              {
                var s = clf._libModule.declaredOrInheritedFeaturesOrNull(cf);
                if (s != null)
                  {
                    for (var fnf : s.entrySet())
                      {
                        var fn = fnf.getKey();
                        var f = fnf.getValue();
                        check
                          (cf != outer);

                        var newfn = cf.handDown(null /*this*/, f, fn, p, outer);
                        addInheritedFeature(set, outer, p.pos(), newfn, f);
                      }
                  }
              }
            else
              {
                for (var fnf : declaredOrInheritedFeatures(cf).entrySet())
                  {
                    var fn = fnf.getKey();
                    var f = fnf.getValue();
                    check
                      (cf != outer);

                    var newfn = cf.handDown(null /*this*/, f, fn, p, outer);
                    addInheritedFeature(set, outer, p.pos(), newfn, f);
                  }
              }
          }
      }
  }



  /**
   * Helper method for findInheritedFeatures and addToHeirs to add a feature
   * that this feature inherits.
   *
   * NYI: This is somewhat redundant with SourceModule.addInheritedFeature,
   * maybe join these two as Module.addInheritedFeature?
   *
   * @param pos the source code position of the inherits call responsible for
   * the inheritance.
   *
   * @param fn the name of the feature, after possible renaming during inheritance
   *
   * @param f the feature to be added.
   */
  private void addInheritedFeature(SortedMap<FeatureName, AbstractFeature> set, AbstractFeature outer, SourcePosition pos, FeatureName fn, AbstractFeature f)
  {
    var s = set;
    var existing = s == null ? null : s.get(fn);
    if (existing != null)
      {
        if (existing.outer().inheritsFrom(f.outer()))  // NYI: better check existing.redefines(f)
          {
            f = existing;
          }
        else if (f.outer().inheritsFrom(existing.outer()))  // NYI: better check f.redefines(existing)
          {
          }
        else if (existing == f && f.generics() != FormalGenerics.NONE ||
                 existing != f && declaredFeatures(outer).get(fn) == null)
          {
            AstErrors.repeatedInheritanceCannotBeResolved(outer.pos(), outer, fn, existing, f);
          }
      }
    s.put(fn, f);
  }


  /**
   * Find the Generic instance defined at offset in this file.
   *
   * @param offset the offset of the Generic
   */
  Generic genericArgument(int offset)
  {
    return findGenericArgument(offset, universe(), FuzionConstants.MIR_FILE_FIRST_FEATURE_OFFSET);
  }


  /**
   * Helper for genericArgument to find the Generic instance defined at offset
   * in the InnerFeatures starting at position 'at' in this file.
   *
   * @param offset the offset of the Generic
   *
   * @param f the outer feature
   *
   * @param at the start of the InnerFeatures to search
   */
  private Generic findGenericArgument(int offset, AbstractFeature f, int at)
  {
    if (PRECONDITIONS) require
      (f != null,
       at <= offset,
       offset < data().limit(),
       at >= 0,
       at < data().limit());

    Generic result = null;
    var sz = data().getInt(at);
    check
      (at+4+sz <= data().limit());
    var i = at+4;
    if (i <= offset && offset < i+sz)
      {
        while (result == null)
          {
            var o = libraryFeature(i, USE_FUM ? null : _srcModule.featureFromOffset(i));
            if (((featureKind(i) & FuzionConstants.MIR_FILE_KIND_HAS_TYPE_PAREMETERS) != 0) &&
                offset > i &&
                offset <= featureResultTypePos(i))
              {
                var tai = featureTypeArgsPos(i);
                var n = typeArgsCount(tai);
                var j = typeArgsListPos(tai);
                var ix = 0;
                while (result == null && ix < n)
                  {
                    if (j == offset)
                      {
                        result = o.generics().list.get(ix);
                      }
                    j = typeArgNextPos(j);
                    ix++;
                  }
              }
            else
              {
                check
                  (o != null);
                var inner = featureInnerFeaturesPos(i);
                if (inner <= offset && offset <= featureNextPos(i))
                  {
                    result = findGenericArgument(offset, o, inner);
                  }
              }
            i = featureNextPos(i);
            check(result != null || i <= offset);
          }
      }
    return result;
  }



  /**
   * Read Type at given position.
   */
  AbstractType type(int at, SourcePosition pos, AbstractType from)
  {
    AbstractType result = _libraryTypes.get(at);
    if (result == null)
      {
        var k = typeKind(at);
        if (k == -4)
          {
            return Types.t_ADDRESS;
          }
        else if (k == -3)
          {
            return Types.resolved.universe.thisType();
          }
        else if (k == -2)
          {
            var at2 = typeIndex(at);
            var k2 = typeKind(at2);
            check
              (k2 == -1 || k2 >= 0);
            result = type(at2, pos, from);
            // we do not cache references to types, so don't add this to _libraryTypes for at.
          }
        else
          {
            LibraryType res;
            if (k < 0)
              {
                res = new GenericType(this, at, pos, genericArgument(typeGeneric(at)), from);
              }
            else
              {
                var feature = libraryFeature(typeFeature(at), from == null ? null : (Feature) from.featureOfType().astFeature());
                var makeRef = typeIsRef(at);
                var generics = Type.NONE;
                if (k > 0)
                  {
                    var i = typeActualGenericsPos(at);
                    generics = new List<AbstractType>();
                    var gi = 0;
                    while (gi < k)
                      {
                        generics.add(type(i, pos, from == null ? null : from.generics().get(gi)));
                        i = typeNextPos(i);
                        gi++;
                      }
                  }
                else
                  {
                    generics = Type.NONE;
                  }
                var outer = type(typeOuterPos(at), from == null ? DUMMY_POS : from.outer().pos(), from == null ? null : from.outer());
                res = new NormalType(this, at, pos, feature, makeRef ? Type.RefOrVal.Ref : Type.RefOrVal.LikeUnderlyingFeature, generics, outer, from);
              }
            _libraryTypes.put(at, res);
            result = res;
          }
      }
    return result;
  }


  /*---------------------  accessing mir file data  ---------------------*/


  /*
   * For each section 'sec' in the data file, where the section defines one or
   * several 'field's, there will be the following methods:
   *
   * secFieldPos(int at)     Position of 'field' in 'sec' starting 'at'
   *
   * secField(int at)        Contents of 'field' in 'sec' starting 'at'
   *
   * secFieldNextPos(int at) Position behind 'field' in 'sec' starting 'at' (optional)
   *
   * secNextPos(int at)      Position right after 'sec' starting 'at'
   */

  /*
   *   +---------------------------------------------------------------------------------+
   *   | Module File s                                                                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | byte[]        | MIR_FILE_MAGIC                                |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | InnerFeatures | inner Features                                |
   *   +--------+--------+---------------+-----------------------------------------------+
   */

  int startPos()
  {
    return FuzionConstants.MIR_FILE_MAGIC.length;
  }

  /*
   *   +---------------------------------------------------------------------------------+
   *   | InnerFeatures                                                                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | sizeof(inner Features)                        |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | Features      | inner Features                                |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
   *   +---------------------------------------------------------------------------------+
   *   | Features                                                                        |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | n      | Feature       | (inner) Features                              |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
   */

  int innerFeaturesSizePos(int at)
  {
    return at;
  }
  int innerFeaturesSize(int at)
  {
    return data().getInt(innerFeaturesSizePos(at));
  }
  int innerFeaturesFeaturesPos(int at)
  {
    return at + 4;
  }
  int innerFeaturesNextPos(int at)
  {
    return innerFeaturesFeaturesPos(at) + innerFeaturesSize(at);
  }


  /*
   *   +---------------------------------------------------------------------------------+
   *   | Feature                                                                         |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | byte          | 0000Tkkk  kkk = kind, T = has type parameters |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | Name          | name                                          |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | arg count                                     |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | name id                                       |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | T=1    | 1      | TypeArgs      | optional type arguments                       |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | hasRT  | 1      | Type          | optional result type,                         |
   *   |        |        |               | hasRT = !isConstructor && !isChoice           |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | inherits count i                              |
   *   | NYI!   |        |               |                                               |
   *   | !isFiel+--------+---------------+-----------------------------------------------+
   *   | d? !isI| i      | Code          | inherits calls                                |
   *   | ntrinsc|        |               |                                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | redefines count r                             |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | r      | int           | feature offset of redefined feature           |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | isRou- | 1      | Code          | Feature code                                  |
   *   | tine   |        |               |                                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   |        | 1      | InnerFeatures | inner features of this feature                |
   *   +--------+--------+---------------+-----------------------------------------------+
   */

  int featureKindPos(int at)
  {
    return at;
  }
  int featureKind(int at)
  {
    var ko = data().get(featureKindPos(at));
    return ko;
  }
  AbstractFeature.Kind featureKindEnum(int at)
  {
    var k = featureKind(at) & FuzionConstants.MIR_FILE_KIND_MASK;
    return featureIsConstructor(at)
      ? AbstractFeature.Kind.Routine
      : AbstractFeature.Kind.from(k);
  }
  boolean featureIsConstructor(int at)
  {
    var k = featureKind(at) & FuzionConstants.MIR_FILE_KIND_MASK;
    return switch (k)
      {
        case FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_VALUE,
             FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_REF -> true;
        default                                            -> false;
      };
  }
  boolean featureIsRoutine(int at)
  {
    return featureKindEnum(at) == AbstractFeature.Kind.Routine;
  }
  boolean featureIsThisRef(int at)
  {
    var k = featureKind(at) & FuzionConstants.MIR_FILE_KIND_MASK;
    return k == FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_REF;
  }
  int featureNamePos(int at)
  {
    var i = featureKindPos(at) + 1;
    return i;
  }
  int featureNameLength(int at)
  {
    var i = featureNamePos(at);
    var l = data().getInt(i);
    return l;
  }
  byte[] featureName(int at)
  {
    var i = featureNamePos(at);
    var d = data();
    var l = d.getInt(i); i = i + 4;
    var b = new byte[l];
    d.get(i, b);
    return b;
  }
  int featureArgCountPos(int at)
  {
    var i = featureNamePos(at);
    var l = featureNameLength(at);
    return i + 4 + l;
  }
  int featureArgCount(int at)
  {
    return data().getInt(featureArgCountPos(at));
  }
  int featureIdPos(int at)
  {
    var i = featureArgCountPos(at);
    return i + 4;
  }
  int featureIdNextPos(int at)
  {
    return featureIdPos(at) + 4;
  }
  int featureId(int at)
  {
    return data().getInt(featureIdPos(at));
  }
  int featureTypeArgsPos(int at)
  {
    if (PRECONDITIONS) require
      ((featureKind(at) & FuzionConstants.MIR_FILE_KIND_HAS_TYPE_PAREMETERS) != 0);

    return featureIdNextPos(at);
  }
  int featureResultTypePos(int at)
  {
    if ((featureKind(at) & FuzionConstants.MIR_FILE_KIND_HAS_TYPE_PAREMETERS) != 0)
      {
        return typeArgsNextPos(featureTypeArgsPos(at));
      }
    else
      {
        return featureIdNextPos(at);
      }
  }
  boolean featureHasResultType(int at)
  {
    var k = featureKind(at) & FuzionConstants.MIR_FILE_KIND_MASK;
    return
      (k != FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_REF   &&
       k != FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_VALUE &&
       k != AbstractFeature.Kind.Choice.ordinal());
  }
  int featureInheritsCountPos(int at)
  {
    var i = featureResultTypePos(at);
    if (featureHasResultType(at))
      {
        i = typeNextPos(i);
      }
    return i;
  }
  int featureInheritsCount(int at)
  {
    return data().getInt(featureInheritsCountPos(at));
  }
  int featureInheritsPos(int at)
  {
    return featureInheritsCountPos(at) + 4;
  }
  int featureRedefinesCountPos(int at)
  {
    var i = featureInheritsPos(at);
    var ic = featureInheritsCount(at);
    while (ic > 0)
      {
        i = codeNextPos(i);
        ic--;
      }
    return i;
  }
  int featureRedefinesCount(int at)
  {
    return data().getInt(featureRedefinesCountPos(at));
  }
  int featureRedefinesPos(int at)
  {
    return featureRedefinesCountPos(at) + 4;
  }
  int featureRedefine(int at, int i)
  {
    if (PRECONDITIONS) require
      (i >= 0,
       i < featureRedefinesPos(at));

    return data().getInt(featureRedefinesPos(at) + 4 * i);
  }
  int featureCodePos(int at)
  {
    return featureRedefinesPos(at) + 4 * featureRedefinesCount(at);
  }
  int featureInnerFeaturesPos(int at)
  {
    var i = featureCodePos(at);
    if (featureIsRoutine(at))
      {
        i = codeNextPos(i);
      }
    return i;
  }
  int featureInnerSizePos(int at)
  {
    if (USE_FUM) throw new Error("NYI: REMOVE!");
    return innerFeaturesSizePos(featureInnerFeaturesPos(at));
  }
  int featureInnerSize(int at)
  {
    if (USE_FUM) throw new Error("NYI: REMOVE!");
    return innerFeaturesSize(featureInnerFeaturesPos(at));
  }
  int featureInnerPos(int at)
  {
    if (USE_FUM) throw new Error("NYI: REMOVE!");
    return innerFeaturesFeaturesPos(featureInnerFeaturesPos(at));
  }
  int featureNextPos(int at)
  {
    return innerFeaturesNextPos(featureInnerFeaturesPos(at));
  }


  /*
   *   +---------------------------------------------------------------------------------+
   *   | TypeArgs                                                                        |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | num type ags n                                |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | bool          | isOpen                                        |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | n      | TypeArg       | type arguments                                |
   *   +--------+--------+---------------+-----------------------------------------------+
   */

  int typeArgsCountPos(int at)
  {
    return at;
  }
  int typeArgsCount(int at)
  {
    return data().getInt(typeArgsCountPos(at));
  }
  int typeArgsOpenPos(int at)
  {
    return typeArgsCountPos(at) + 4;
  }
  boolean typeArgsOpen(int at)
  {
    return data().get(typeArgsOpenPos(at)) != 0;
  }
  int typeArgsListPos(int at)   // works also if list is empty
  {
    return typeArgsOpenPos(at) + 1;
  }
  int typeArgsNextPos(int at)
  {
    var d = data();
    var n = typeArgsCount(at);
    var i = typeArgsListPos(at);
    while (n > 0)
      {
        i = typeArgNextPos(i);
        n--;
      }
    return i;
  }


  /*
   *   +---------------------------------------------------------------------------------+
   *   | TypeArg                                                                         |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Name          | type arg name                                 |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | Type          | constraint                                    |
   *   +--------+--------+---------------+-----------------------------------------------+
   */

  int typeArgNamePos(int at)
  {
    return at;
  }
  String typeArgName(int at)
  {
    return name(typeArgNamePos(at));
  }
  int typeArgConstraintPos(int at)
  {
    return nameNextPos(typeArgNamePos(at));
  }
  AbstractType typeArgConstraint(int at, SourcePosition pos, AbstractType from)
  {
    return type(typeArgConstraintPos(at), pos, from);
  }
  int typeArgNextPos(int at)
  {
    return typeNextPos(typeArgConstraintPos(at));
  }


  /*
   *   +---------------------------------------------------------------------------------+
   *   | Name                                                                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | name length l                                 |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | l      | byte          | name as utf8 bytes                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   */

  String name(int at)
  {
    var i = at;
    var d = data();
    var l = d.getInt(i);
    i = i + 4;
    var b = new byte[l];
    d.get(i, b);
    return new String(b, StandardCharsets.UTF_8);
  }
  int nameNextPos(int at)
  {
    var i = at;
    var d = data();
    var l = d.getInt(i);
    i = i + 4 + l;
    return i;
  }


  /*
   *   +---------------------------------------------------------------------------------+
   *   | Type                                                                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | the kind of this type tk                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | tk==-4 | 1      | unit          | ADDRESS                                       |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | tk==-3 | 1      | unit          | type of universe                              |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | tk==-2 | 1      | int           | index of type                                 |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | tk==-1 | 1      | int           | index of generic argument                     |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | tk>=0  | 1      | int           | index of feature of type                      |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | bool          | isRef                                         |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | tk     | Type          | actual generics                               |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | Type          | outer type                                    |
   *   +--------+--------+---------------+-----------------------------------------------+
   */

  int typeKind(int at)
  {
    return data().getInt(at);
  }
  int typeAddressPos(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) == -4);

    return at+4;
  }
  int typeUniversePos(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) == -3);

    return at+4;
  }
  int typeIndexPos(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) == -2);

    return at+4;
  }
  int typeIndex(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) == -2);

    return data().getInt(typeIndexPos(at));
  }
  int typeGenericPos(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) == -1);

    return at+4;
  }
  int typeGeneric(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) == -1);

    return data().getInt(typeGenericPos(at));
  }
  int typeFeaturePos(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) >= 0);

    return at+4;
  }
  int typeFeature(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) >= 0);

    return data().getInt(typeFeaturePos(at));
  }
  int typeIsRefPos(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) >= 0);

    return typeFeaturePos(at) + 4;
  }
  boolean typeIsRef(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) >= 0);

    if (true) return false;  // NYI: WHy?
    return data().get(typeIsRefPos(at)) != 0;
  }
  int typeActualGenericsPos(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) >= 0);

    return typeIsRefPos(at) + 1;
  }
  int typeOuterPos(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) >= 0);

    var k = typeKind(at);
    at = typeActualGenericsPos(at);
    int n = k;
    for (var i = 0; i<n; i++)
      {
        at = typeNextPos(at);
      }
    return at;
  }
  int typeNextPos(int at)
  {
    var k = typeKind(at);
    if (k == -4)
      {
        return typeAddressPos(at) + 0;
      }
    else if (k == -3)
      {
        return typeUniversePos(at) + 0;
      }
    else if (k == -2)
      {
        return typeIndexPos(at) + 4;
      }
    else if (k == -1)
      {
        return typeGenericPos(at) + 4;
      }
    else
      {
        at = typeOuterPos(at);
        return typeNextPos(at);
      }
  }


  /*
   *   +---------------------------------------------------------------------------------+
   *   | Code                                                                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | sizeof(Expressions)                           |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | Expressions   | the actual code                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
   *   +---------------------------------------------------------------------------------+
   *   | Expressions                                                                     |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | n      | Expression    | the single expressions                        |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  int codeSizePos(int at)
  {
    return at;
  }
  int codeSize(int at)
  {
    return data().getInt(codeSizePos(at));
  }
  int codeExpressionsPos(int at)
  {
    return at + 4;
  }
  int codeNextPos(int at)
  {
    var sz = data().getInt(at);
    return at + 4 + sz;
  }

  /*
   *   +---------------------------------------------------------------------------------+
   *   | Expression                                                                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | byte          | ExprKind k                                    |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Add | 1      | Assign        | assignment                                    |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Unb | 1      | Unbox         | unbox expression                              |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Con | 1      | Constant      | constant                                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Cal | 1      | Call          | feature call                                  |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Mat | 1      | Match         | match statement                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Tag | 1      | Tag           | tag expression                                |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  int expressionKindPos(int at)
  {
    return at;
  }
  int expressionKindRaw(int at)
  {
    return data().get(expressionKindPos(at));
  }
  IR.ExprKind expressionKind(int at)
  {
    return IR.ExprKind.from(expressionKindRaw(at));
  }
  int expressionNextPos(int at)
  {
    var k = expressionKind(at);
    var eAt = at + 1;
    return switch (k)
      {
      case Assign  -> assignNextPos(eAt);
      case Unbox   -> unboxNextPos (eAt);
      case Box     -> eAt;
      case Const   -> constNextPos(eAt);
      case Current -> eAt;
      case Match   -> matchNextPos(eAt);
      case Call    -> callNextPos (eAt);
      case Tag     -> tagNextPos  (eAt);
      case Pop     -> eAt;
      case Unit    -> eAt;
      default      -> throw new Error("unexpected expression kind "+k+" at "+at+" in "+this);
      };
  }


  /*
   *   +---------------------------------------------------------------------------------+
   *   | Assign                                                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | assigned field index                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  int assignFieldPos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Assign);

    return at;
  }
  int assignField(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Assign);

    return data().getInt(assignFieldPos(at));
  }
  int assignNextPos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Assign);

    return assignFieldPos(at) + 4;
  }


  /*
   *   +---------------------------------------------------------------------------------+
   *   | Unbox                                                                           |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Type          | result type                                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  int unboxTypePos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Unbox);

    return at;
  }
  AbstractType unboxType(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Unbox);

    return type(unboxTypePos(at), DUMMY_POS, null);
  }
  int unboxNextPos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Unbox);

    return typeNextPos(unboxTypePos(at));
  }


  /*
   *   +---------------------------------------------------------------------------------+
   *   | Constant                                                                        |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Type          | type of the constant                          |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | length        | data length of the constant                   |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | length | byte          | data of the constant                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  int constTypePos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Const);

    return at;
  }
  AbstractType constType(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Const);

    return type(constTypePos(at), DUMMY_POS, null);
  }
  int constLengthPos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Const);

    return typeNextPos(constTypePos(at));
  }
  int constLength(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Const);

    return data().getInt(constLengthPos(at));
  }
  int constDataPos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Const);

    return constLengthPos(at) + 4;
  }
  byte[] constData(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Const);

    var l = constLength(at);
    var result = new byte[l];
    data().get(constDataPos(at), result);
    return result;
  }
  int constNextPos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Const);

    return constDataPos(at) + constLength(at);
  }


  /*
   *   +---------------------------------------------------------------------------------+
   *   | Call                                                                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | called feature f index                        |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | Type          | result type (NYI: remove, redundant!)s        |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | hasOpen| 1      | int           | num actual args (TBD: this is redundant,      |
   *   | ArgList|        |               | should be possible to determine)              |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cf.gene| 1      | int           | num actual generics n                         |
   *   | rics.is|        |               |                                               |
   *   | Open   |        |               |                                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   |        | n      | Type          | type parameters. if !hasOpen, n is            |
   *   |        |        |               | f.generics().list.size()                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  int callCalledFeaturePos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Call);

    return at;
  }
  int callCalledFeature(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Call);

    return data().getInt(callCalledFeaturePos(at));
  }
  int callTypePos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Call);

    return callCalledFeaturePos(at) + 4;
  }
  AbstractType callType(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Call);

    return type(callTypePos(at), DUMMY_POS, null);
  }
  int callNumArgsPos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Call);

    var p = callTypePos(at);
    return typeNextPos(p);
  }
  int callNumArgsRaw(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Call,
       libraryFeature(callCalledFeature(at), null).hasOpenGenericsArgList());

    return data().getInt(callNumArgsPos(at));
  }
  int callNumArgs(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Call);

    var f = libraryFeature(callCalledFeature(at), null);
    return f.hasOpenGenericsArgList()
      ? callNumArgsRaw(at)
      : f.arguments().size();
  }
  int callNumTypeParametersPos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Call);

    return callNumArgsPos(at) +
      (libraryFeature(callCalledFeature(at), null).hasOpenGenericsArgList() ? 4 : 0);
  }
  int callNumTypeParametersRaw(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Call,
       libraryFeature(callCalledFeature(at), null).generics().isOpen());

    return data().getInt(callNumTypeParametersPos(at));
  }
  int callNumTypeParameters(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Call);

    var f = libraryFeature(callCalledFeature(at), null);
    return f.generics().isOpen()
      ? callNumTypeParametersRaw(at)
      : f.generics().list.size();
  }
  int callTypeParametersPos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Call);

    return callNumTypeParametersPos(at) +
      (libraryFeature(callCalledFeature(at), null).generics().isOpen() ? 4 : 0);
  }
  int callNextPos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Call);

    var n = callNumTypeParameters(at);
    var tat = callTypeParametersPos(at);
    for (var i = 0; i < n; i++)
      {
        tat = typeNextPos(tat);
      }

    return tat;
  }


  /*
   *   +---------------------------------------------------------------------------------+
   *   | Match                                                                           |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | number of cases                               |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | n      | Case          | cases                                         |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  int matchNumberOfCasesPos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Match);

    return at;
  }
  int matchNumberOfCases(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Match);

    return data().getInt(matchNumberOfCasesPos(at));
  }
  int matchCasesPos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Match);

    return matchNumberOfCasesPos(at) + 4;
  }
  int matchNextPos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Match);

    var n = matchNumberOfCases(at);
    at = matchCasesPos(at);
    for (var i = 0; i < n; i++)
      {
        at = caseNextPos(at);
      }
    return at;
  }


  /*
   *   +---------------------------------------------------------------------------------+
   *   | Case                                                                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | num types n                                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | n = -1 | 1      | int           | case field index                              |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | n >  0 | n      | Type          | case type                                     |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Code          | code for case                                 |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  int caseNumTypesPos(int at)
  {
    return at;
  }
  int caseNumTypes(int at)
  {
    return data().getInt(caseNumTypesPos(at));
  }
  int caseFieldPos(int at)
  {
    return at + 4;
  }
  int caseField(int at)
  {
    if (PRECONDITIONS) require
      (caseNumTypes(at) == -1);

    return data().getInt(caseFieldPos(at));
  }
  int caseTypePos(int at)
  {
    return at + 4;
  }
  int caseCodePos(int at)
  {
    int result;
    var n = caseNumTypes(at);
    if (n == -1)
      {
        result = caseFieldPos(at) + 4;
      }
    else
      {
        check
          (n > 0);
        result = caseTypePos(at);
        while (n > 0)
          {
            result = typeNextPos(result);
            n--;
          }
      }
    return result;
  }
  int caseNextPos(int at)
  {
    return codeNextPos(caseCodePos(at));
  }


  /*
   *   +---------------------------------------------------------------------------------+
   *   | Tag                                                                             |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Type          | resulting tagged union type                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  int tagTypePos(int at)
  {
    return at;
  }
  AbstractType tagType(int at)
  {
    return type(tagTypePos(at), DUMMY_POS, null);
  }
  int tagNextPos(int at)
  {
    return typeNextPos(tagTypePos(at));
  }


  /*-------------------------------  misc  ------------------------------*/


  /**
   * Create String representation for debugging.
   */
  public String toString()
  {
    return "LibraryModule for '" + _srcModule + "'";
  }

}

/* end of file */
