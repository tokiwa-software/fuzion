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

import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.Generic;
import dev.flang.ast.Type;
import dev.flang.ast.Types;

import dev.flang.ir.IR;

import dev.flang.mir.MIR;

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
  static boolean USE_FUM = "true".equals(System.getenv("USE_FUM"));


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
  }


  /*-----------------------------  methods  -----------------------------*/


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
    var sdf = _srcModule.declaredFeaturesOrNull(outer.astFeature());
    return sdf == null ? null : libraryFeatures(sdf);
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
   * Get declared and inherited features for given outer Feature as seen by this
   * module.  Result may be null if this module does not contribute anything to
   * outer.
   *
   * @param outer the declaring feature
   */
  SortedMap<FeatureName, AbstractFeature>declaredOrInheritedFeaturesOrNull(AbstractFeature outer)
  {
    var sdif = _srcModule.declaredOrInheritedFeaturesOrNull(outer.astFeature());
    return sdif == null ? null : libraryFeatures(sdif);
  }


  /**
   * Get direct redefininitions of given Feature as seen by this module.
   * Result is null if f has no redefinitions in this module.
   *
   * @param f the original feature
   */
  Set<AbstractFeature>redefinitionsOrNull(AbstractFeature f)
  {
    Set<AbstractFeature> result = null;
    var rfs = _srcModule.redefinitionsOrNull(f.astFeature());
    if (rfs != null)
      {
        result = new TreeSet<>();
        for (var e : rfs)
          {
            result.add(libraryFeature(e));
          }
      }
    return result;
  }


  /**
   * Find the Generic instance defined at offset in this file.
   *
   * @param offset the offset of the Generic
   */
  Generic genericArgument(int offset)
  {
    return findGenericArgument(offset, _mir.universe(), FuzionConstants.MIR_FILE_FIRST_FEATURE_OFFSET);
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
            var o = libraryFeature(i, _srcModule.featureFromOffset(i));
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
                var inner = featureInnerSizePos(i);
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
        if (k == -3)
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
                res = new NormalType(this, at, pos, feature, makeRef, generics, outer, from);
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
  int featureCodePos(int at)
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
  int featureInnerSizePos(int at)
  {
    var i = featureCodePos(at);
    if (featureIsRoutine(at))
      {
        i = codeNextPos(i);
      }
    return i;
  }
  int featureInnerSize(int at)
  {
    return data().getInt(featureInnerSizePos(at));
  }
  int featureInnerPos(int at)
  {
    var i = featureInnerSizePos(at);
    return i + 4;
  }
  int featureNextPos(int at)
  {
    return featureInnerPos(at) + featureInnerSize(at);
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

    if (true) return false;
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
    if (k == -3)
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
   *   | k==Con | 1      | Constant      | constant                                      |
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
      case Unbox   -> eAt;
      case Box     -> eAt;
      case Const   -> constNextPos(eAt);
      case Current -> eAt;
      case Match   -> matchNextPos(eAt);
      case Call    -> callNextPos (eAt);
      case Tag     -> eAt;
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
   *
   *   +---------------------------------------------------------------------------------+
   *   | Case                                                                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Code          | code for case                                 |
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
  int matchCaseNextPos(int at)
  {
    return codeNextPos(at);
  }
  int matchNextPos(int at)
  {
    if (PRECONDITIONS) require
      (expressionKind(at-1) == IR.ExprKind.Match);

    var n = matchNumberOfCases(at);
    at = matchCasesPos(at);
    for (var i = 0; i < n; i++)
      {
        at = matchCaseNextPos(at);
      }
    return at;
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
