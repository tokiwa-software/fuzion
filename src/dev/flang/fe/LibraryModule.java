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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Expr;
import dev.flang.ast.FeatureName;
import dev.flang.ast.TypeKind;
import dev.flang.ast.UnresolvedType;
import dev.flang.ast.Types;
import dev.flang.ast.Visi;

import dev.flang.mir.MIR;
import dev.flang.mir.MirModule;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.FuzionConstants.MirExprKind;
import dev.flang.util.FuzionOptions;

import static dev.flang.util.FuzionConstants.MirExprKind;
import dev.flang.util.HexDump;
import dev.flang.util.List;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;
import dev.flang.util.SourceRange;
import dev.flang.util.Version;


/**
 * A LibraryModule represents a Fuzion module loaded from a precompiled Fuzion
 * module file .fum.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class LibraryModule extends Module implements MirModule
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * As long as source position is not part of the .fum/MIR file, use this
   * constant as a place holder.
   */
  static SourcePosition DUMMY_POS = SourcePosition.builtIn;


  /**
   * NYI: Instead of using env var, create a new tool "fzdump" or similar to
   * dump intermediate files.
   */
  static final boolean DUMP = FuzionOptions.boolPropertyOrEnv("FUZION_DUMP_MODULE_FILE");


  /**
   * Pre-allocated empty array
   */
  static byte[] NO_BYTES = new byte[0];


  /*----------------------------  variables  ----------------------------*/


  /**
   * The base index of this module. When converting local indices to global
   * indices, the _globalBase will be added.
   */
  private final int _globalBase;


  /**
   * The module binary data, contents of .mir file.
   */
  final ByteBuffer _data;


  /**
   * The module intermediate representation for this module.
   */
  private MIR _mir;


  /**
   * Map from offset in _data to LibraryFeatures for features in this module.
   */
  private final TreeMap<Integer, LibraryFeature> _libraryFeatures = new TreeMap<>();


  /**
   * Map from offset in _data to LibraryType for types in this module.
   */
  private final TreeMap<Integer, LibraryType> _libraryTypes = new TreeMap<>();


  /**
   * Cache for 'normal' code created from given index
   */
  Map<Integer, Expr> _code = new TreeMap<>();

  /**
   * Cache for inheritance call code created from given index
   */
  Map<Integer, Expr> _code1 = new TreeMap<>();

  /**
   * Cache for innerFeatures created from given index
   */
  private final Map<Integer, List<AbstractFeature>> _innerFeatures = new TreeMap<>();


  /**
   * Source code files, created on demand
   */
  private final ArrayList<SourceFile> _sourceFiles;


  /**
   * The universe
   */
  private final AbstractFeature _universe;


  /**
   * Modules referenced from this module
   */
  private final ModuleRef[] _modules;



  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create LibraryModule for given options and sourceDirs.
   */
  LibraryModule(int globalBase, FrontEnd fe, ByteBuffer data, Function<AbstractFeature, LibraryModule[]> loadDependsOn, AbstractFeature universe)
  {
    super(null /* set later, we need correct universe first */);

    _globalBase = globalBase;
    _mir = null;
    _data = data;
    _sourceFiles = new ArrayList<>(sourceFilesCount());
    var sfc = sourceFilesCount();
    for (int i = 0; i < sfc; i++)
      {
        _sourceFiles.add(null);
      }
    var mrc = moduleRefsCount();
    _modules = new ModuleRef[mrc];
    var p = moduleRefsPos();
    int moduleOffset = _data.limit();

    this._universe = universe == null ? libraryUniverse() : universe;
    if (CHECKS) check
      (_universe.isUniverse());

    _dependsOn = loadDependsOn.apply(universe());
    if (CHECKS)
      check(_dependsOn != null);

    for (int i = 0; i < mrc; i++)
      {
        var n = moduleRefName(p);
        var v = moduleRefHash(p);
        var m = fe.loadModule(n, universe());
        var mv = m.hash();
        if (!Arrays.equals(v, mv))
          {
            FeErrors.incompatibleModuleHash(this, m, v, mv);
          }
        var mr = new ModuleRef(moduleOffset, n, v, m);
        _modules[i] = mr;
        moduleOffset = moduleOffset + mr.size();
        p = moduleRefNextPos(p);
      }

    var dm = fe._options._dumpModules;
    if (DUMP ||
        dm != null && dm.contains(name()))
      {
        say(dump());
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Get the ModuleRef instance with given index.  ModuleRef instances refer to
   * other modules that this module depends on.
   */
  ModuleRef moduleRef(int offset)
  {
    ModuleRef result = null;
    for (int i = 0; i < _modules.length && offset >= _modules[i]._offset; i++)
      {
        result = _modules[i];
      }
    return result;
  }


  /**
   * Convert local index of this module into global index.
   */
  int globalIndex(int index)
  {
    if (PRECONDITIONS) require
      (0 < index,
       index < _data.limit());

    var result = _globalBase + index;

    if (POSTCONDITIONS) ensure
      (_globalBase - FrontEnd.GLOBAL_INDEX_OFFSET <  result - FrontEnd.GLOBAL_INDEX_OFFSET,
       result      - FrontEnd.GLOBAL_INDEX_OFFSET <= Integer.MAX_VALUE                    );

    return result;
  }


  /**
   * The universe
   */
  public AbstractFeature universe()
  {
    return _universe;
  }


  /**
   * The universes code as persisted in this fum-file
   */
  Expr moduleUniverseCode()
  {
    return innerFeatures(declFeaturesInnerPos(moduleDeclFeaturesPos())).get(0).code();
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
   *
   * @param main the main features name
   */
  public MIR createMIR(String main)
  {
    if (_mir == null)
      {
        var d = main == null
          ? universe()
          : lookupFeature(universe(), FeatureName.get(main, 0));

        if (CHECKS) check
          (d != null);

        _mir = createMIR(this, universe(), d);

        Errors.showAndExit();
      }
    return _mir;
  }


  /**
   * Get declared features for given outer Feature as seen by this module.
   * Result is null if outer has no declared features in this module.
   *
   * @param outer the declaring feature
   */
  public SortedMap<FeatureName, AbstractFeature>declaredFeatures(AbstractFeature outer)
  {
    var result = new TreeMap<FeatureName, AbstractFeature>();
    if (outer instanceof LibraryFeature lf)
      {
        for (var d : lf.declaredFeatures())
          {
            result.put(d.featureName(), d);  // NYI: handle equally named features from different modules
          }
      }
    for (var d : features(outer))
      {
        result.put(d.featureName(), d);  // NYI: handle equally named features from different modules
      }
    for (Module d : _dependsOn)
      {
        result.putAll(d.declaredFeatures(outer));  // NYI: handle equally named features from different modules
      }
    return result;
  }


  /**
   * Get or create LibraryFeature at given offset
   *
   * @param offset the offset in data()
   *
   * @return the feature declared at offset in this module.
   */
  AbstractFeature libraryFeature(int offset)
  {
    if (offset >= 0 && offset <= _data.limit())
      {
        var result = _libraryFeatures.get(offset);
        if (result == null)
          {
            result = new LibraryFeature(this, offset);
            _libraryFeatures.put(offset, result);
          }
        return result;
      }
    else
      {
        var mr = moduleRef(offset);
        if (CHECKS) check
          (mr != null);

        return mr._module != null
                ? mr._module.libraryFeature(offset - mr._offset)
                : Types.f_ERROR;
      }
  }


  /**
   * Get the feature corresponding to given offset
   *
   * @param offset the offset in data(), -1 for 'null', 0 for universe.
   *
   * @return the AbstractFeature corresponding to given offset.
   */
  AbstractFeature feature(int offset)
  {
    if (PRECONDITIONS) require
      (offset >= -1);

    return
      (offset == -1) ? null :
      (offset ==  0) ? universe()
                     : libraryFeature(offset);
  }


  /**
   * The features declared within outer by this module
   *
   * @param outer an outer feature
   *
   * @return list of inner features of outer that are declared by this library
   * module.
   */
  List<AbstractFeature> features(AbstractFeature outer)
  {
    if (outer.isUniverse())
      {
        return libraryUniverse()
          .innerFeatures();
      }
    else
      {
        var n = moduleNumDeclFeatures();
        var at = moduleDeclFeaturesPos();
        for (int i = 0; i < n; i++)
          {
            if (feature(declFeaturesOuter(at)) == outer)
              {
                return innerFeatures(declFeaturesInnerPos(at));
              }
            at = declFeaturesNextPos(at);
          }
        return new List<>();
      }
  }


  /**
   * get the universe as persisted in this fum-file
   */
  public LibraryFeature libraryUniverse()
  {
    return (LibraryFeature)innerFeatures(declFeaturesInnerPos(moduleDeclFeaturesPos())).get(0);
  }


  /**
   * The features declared at given InnerFeatures block.
   *
   * @param at the index of an InnerFeatures block.
   */
  List<AbstractFeature> innerFeatures(int at)
  {
    var result = _innerFeatures.get(at);
    if (result == null)
      {
        result = new List<AbstractFeature>();
        var is = innerFeaturesSize(at);
        var ip = innerFeaturesFeaturesPos(at);
        for (var i = ip; i < ip+is; i = featureNextPos(i))
          {
            result.add(libraryFeature(i));
          }
        _innerFeatures.put(at, result);
      }
    return result;
  }


  /**
   * Find the Generic instance defined at offset in this file.
   *
   * @param offset the offset of the Generic
   */
  AbstractFeature genericArgument(int offset)
  {
    var tp = feature(offset);
    var o = tp.outer();
    for (var g : o.typeArguments())
      {
        if (g == tp)
          {
            return g;
          }
      }
    check
      (false);
    throw new Error();
  }


  /**
   * Read Type at given position.
   */
  AbstractType type(int at)
  {
    var result = _libraryTypes.get(at);
    if (result == null)
      {
        var k = typeKind(at);
        if (k == -3)
          {
            return universe().selfType();
          }
        else if (k == -2)
          {
            var at2 = typeIndex(at);
            var k2 = typeKind(at2);
            if (CHECKS) check
              (k2 == -1 || k2 >= 0);
            return type(at2);
            // we do not cache references to types, so don't add this to _libraryTypes for at.
          }
        else if (k == -1)
          {
            result = new GenericType(this, at, genericArgument(typeTypeParameter(at)));
          }
        else
          {
            if (CHECKS) check
              (k >= 0);
            var feature = libraryFeature(cotype(at));
            var generics = UnresolvedType.NONE;
            if (k > 0)
              {
                var i = typeActualGenericsPos(at);
                generics = new List<AbstractType>();
                var gi = 0;
                while (gi < k)
                  {
                    generics.add(type(i));
                    i = typeNextPos(i);
                    gi++;
                  }
              }
            var outer = type(typeOuterPos(at));
            var tk = TypeKind.fromInt(typeValRefOrThis(at));
            result = tk == TypeKind.ThisType
              ? new ThisType(this, at, feature)
              : new NormalType(this, at, feature, tk, generics, outer);
          }
        _libraryTypes.put(at, result);
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
--asciidoc--

File Formats
------------

Module File Format
~~~~~~~~~~~~~~~~~~

Module File
^^^^^^^^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

.8+|true      | 1      | byte[4]       | MIR_FILE_MAGIC

              | 1      | Name          | module name

              | 1      | u128          | module hash

              | 1      | int           | number of modules this module depends on n

              | n      | ModuleRef     | reference to another module

              | 1      | int           | number of DeclFeatures entries m

              | m      | DeclFeatures  | features declared in this module

              | 1      | SourceFiles   | source code files
|====

--asciidoc--


   *   +---------------------------------------------------------------------------------+
   *   | Module File                                                                     |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | byte[4]       | MIR_FILE_MAGIC                                |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | Name          | module name                                   |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | u128          | module hash                                   |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | int           | number of modules this module depends on n    |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | n      | ModuleRef     | reference to another module                   |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | int           | number of DeclFeatures entries m              |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | m      | DeclFeatures  | features declared in this module              |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | SourceFiles   | source code files                             |
   *   +--------+--------+---------------+-----------------------------------------------+
   */

  int startPos()
  {
    return FuzionConstants.MIR_FILE_MAGIC.length;
  }
  int namePos()
  {
    return startPos();
  }
  public String name()
  {
    return name(namePos());
  }
  int nameNextPos()
  {
    return nameNextPos(namePos());
  }
  int hashPos()
  {
    return nameNextPos();
  }
  byte[] hash(int at)
  {
    var r = new byte[16];
    for (int i = 0; i<r.length; i++)
      {
        r[i] = _data.get(at);
        at++;
      }
    return r;
  }
  byte[] hash()
  {
    return hash(hashPos());
  }
  int hashNextPos()
  {
    return hashPos() + 16;
  }
  int moduleRefsCountPos()
  {
    return hashNextPos();
  }
  int moduleRefsCount()
  {
    return _data.getInt(moduleRefsCountPos());
  }
  int moduleRefsPos()
  {
    return moduleRefsCountPos() + 4;
  }
  int moduleRefsNextPos()
  {
    var at = moduleRefsPos();
    var n = moduleRefsCount();
    for (int i = 0; i < n; i++)
      {
        at = moduleRefNextPos(at);
      }
    return at;
  }
  int moduleNumDeclFeaturesPos()
  {
    return moduleRefsNextPos();
  }
  int moduleNumDeclFeatures()
  {
    return _data.getInt(moduleNumDeclFeaturesPos());
  }
  int moduleDeclFeaturesPos()
  {
    return moduleNumDeclFeaturesPos() + 4;
  }
  int moduleSourceFilesPos()
  {
    var n = moduleNumDeclFeatures();
    var at = moduleDeclFeaturesPos();
    while (n > 0)
      {
        n--;
        at = declFeaturesNextPos(at);
      }
    return at;
  }


  /*
--asciidoc--

ModuleRef
^^^^^^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

.2+| true     | 1      | Name          | module name

              | 1      | u128          | module hash
|====

--asciidoc--

   *   +---------------------------------------------------------------------------------+
   *   | ModuleRef                                                                       |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Name          | module name                                   |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | u128          | module hash                                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
   */

  int moduleRefNamePos(int at)
  {
    return at;
  }
  String moduleRefName(int at)
  {
    return name(moduleRefNamePos(at));
  }
  int moduleRefNameNextPos(int at)
  {
    return nameNextPos(at);
  }
  int moduleRefHashPos(int at)
  {
    return moduleRefNameNextPos(at);
  }
  byte[] moduleRefHash(int at)
  {
    return hash(moduleRefHashPos(at));
  }
  int moduleRefHashNextPos(int at)
  {
    return moduleRefHashPos(at) + 16;
  }
  int moduleRefNextPos(int at)
  {
    return moduleRefHashNextPos(at);
  }


  /*
--asciidoc--

DeclFeatures
^^^^^^^^^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

.2+|true      | 1      | int           | outer feature index, 0 for outer==universe

              | 1      | InnerFeatures | inner Features
|====

--asciidoc--

   *   +---------------------------------------------------------------------------------+
   *   | DeclFeatures                                                                    |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | outer feature index, 0 for outer()==null      |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | InnerFeatures | inner Features                                |
   *   +--------+--------+---------------+-----------------------------------------------+
   */

  int declFeaturesOuterPos(int at)
  {
    return at;
  }
  int declFeaturesOuter(int at)
  {
    return _data.getInt(declFeaturesOuterPos(at));
  }
  int declFeaturesInnerPos(int at)
  {
    return declFeaturesOuterPos(at) + 4;
  }
  int declFeaturesNextPos(int at)
  {
    return innerFeaturesNextPos(declFeaturesInnerPos(at));
  }


  /*
--asciidoc--

InnerFeatures
^^^^^^^^^^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

.2+| true     | 1      | int           | sizeof(inner Features)

              | 1      | Features      | inner Features
|====

Features
^^^^^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

   | true     | n      | Feature       | (inner) Features
|====

--asciidoc--

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
--asciidoc--

Feature
^^^^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what
.6+| true  .6+| 1      | short         | 000OREvvvFCYkkkk  k = kind, Y = has cotype (i.e., 'f.type'), C = is cotype, F = has 'fixed' modifier, v = visibility, R/E = has pre-/post-condition feature, O = hasValuesAsOpenTypeFeature
                       | Name          | name
                       | int           | arg count
                       | int           | name id
                       | Pos           | source code position
                       | int           | outer feature index, 0 for outer()==null
   | Y=1      | 1      | Feature       | the cotype
   | C=1      | 1      | Feature       | the cotype origin
   | hasRT    | 1      | Type          | optional result type,
                                         hasRT = !isConstructor && !isChoice && !isTypeParameter
   | O=1      | 1      | int           | open type Feature index,
   | isTypeParameter | 1 | Type        | constraint of (open) type parameters
.2+| true NYI! !isField? !isIntrinsc
              | 1      | int           | inherits count i
              | i      | Code          | inherits calls
.2+| R        | 1      | int           | feature offset of precondition feature
              | 1      | int           | feature offset of precondition bool feature
   | R && isConstructor | 1      | int           | feature offset of precondition and call feature
   | E        | 1      | int           | feature offset of postcondition feature
.2+| true     | 1      | int           | redefines count r
              | r      | int           | feature offset of redefined feature
   | isRoutine| 1      | Code          | Feature code
   | true     | 1      | InnerFeatures | inner features of this feature
|====

--asciidoc--

   *   +---------------------------------------------------------------------------------+
   *   | Feature                                                                         |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | short         | 000OREvvvFCYkkkk                              |
   *   |        |        |               |           k = kind                            |
   *   |        |        |               |           Y = has Type feature (i.e. 'f.type')|
   *   |        |        |               |           C = is cotype                       |
   *   |        |        |               |           F = has 'fixed' modifier            |
   *   |        |        |               |           v = visibility                      |
   *   |        |        |               |           R = has precondition feature        |
   *   |        |        |               |           E = has postcondition feature       |
   *   |        |        |               |           O = hasValuesAsOpenTypeFeature      |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | Name          | name                                          |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | arg count                                     |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | name id                                       |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | Pos           | source code position                          |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | outer feature index, 0 for outer()==null      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | Y=1    | 1      | int           | cotype index                                  |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | C=1    | 1      | int           | cotypeorigin index                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | hasRT  | 1      | Type          | optional result type,                         |
   *   |        |        |               | hasRT = !isConstructor && !isChoice           |
   *   |        |        |               |         && !isTypeParameter                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | O=1    | 1      | int           | open type Feature index                       |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | isType | 1      | Type          | constraint of (open) type parameters          |
   *   | Parame |        |               |                                               |
   *   | ter    |        |               |                                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | inherits count i                              |
   *   | NYI!   |        |               |                                               |
   *   | !isFiel+--------+---------------+-----------------------------------------------+
   *   | d? !isI| i      | Code          | inherits calls                                |
   *   | ntrinsc|        |               |                                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | R      | 1      | int           | feature offset of precondition feature        |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | feature offset of pre bool feature            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | R &&   | 1      | int           | feature offset of pre and call feature        |
   *   | isConst|        |               |                                               |
   *   | ructor |        |               |                                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | E      | 1      | int           | feature offset of postcondition feature       |
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
    var ko = data().getShort(featureKindPos(at));
    return ko;
  }
  AbstractFeature.Kind featureKindEnum(int at)
  {
    var k = featureKind(at) & FuzionConstants.MIR_FILE_KIND_MASK;
    return featureIsConstructor(at)
      ? AbstractFeature.Kind.Routine
      : AbstractFeature.Kind.from(k);
  }
  Visi featureVisibilityEnum(int at)
  {
    var k = (featureKind(at) & FuzionConstants.MIR_FILE_VISIBILITY_MASK) >> 7;
    return Visi.from(k);
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
  boolean featureIsRef(int at)
  {
    var k = featureKind(at) & FuzionConstants.MIR_FILE_KIND_MASK;
    return k == FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_REF;
  }
  boolean featureHasCotype(int at)
  {
    return ((featureKind(at) & FuzionConstants.MIR_FILE_KIND_HAS_COTYPE) != 0);
  }
  boolean featureIsCotype(int at)
  {
    return ((featureKind(at) & FuzionConstants.MIR_FILE_KIND_IS_COTYPE) != 0);
  }
  boolean featureIsFixed(int at)
  {
    return ((featureKind(at) & FuzionConstants.MIR_FILE_KIND_IS_FIXED) != 0);
  }
  boolean featureHasOpenTypeFeature(int at)
  {
    var res = ((featureKind(at) & FuzionConstants.MIR_FILE_KIND_HAS_VALUES_OF_OPEN_TYPE_FEATURE) != 0);
    if (CHECKS) check
      (true ||  // checking this would cause endless recursion
       res == (featureHasResultType(at) && libraryFeature(at).resultType().isOpenGeneric() ||
               (featureKind(at) & FuzionConstants.MIR_FILE_KIND_MASK) == AbstractFeature.Kind.OpenTypeParameter.ordinal()));
    return res;
  }
  int featureNamePos(int at)
  {
    var i = featureKindPos(at) + 2;
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
    var b = NO_BYTES;
    if (l > 0)
      {
        b = new byte[l];
        d.get(i, b);
      }
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
  int featurePositionPos(int at)
  {
    return featureIdNextPos(at);
  }
  int featurePosition(int at)
  {
    return data().getInt(featurePositionPos(at));
  }
  int featurePositionEnd(int at)
  {
    return data().getInt(featurePositionPos(at) + 4);
  }
  int featurePositionNextPos(int at)
  {
    return featurePositionPos(at) + 8;
  }
  int featureOuterPos(int at)
  {
    return featurePositionNextPos(at);
  }
  AbstractFeature featureOuter(int at)
  {
    return feature(data().getInt(featureOuterPos(at)));
  }
  int featureOuterNextPos(int at)
  {
    return featureOuterPos(at) + 4;
  }
  int featureCoTypeOrOriginPos(int at)
  {
    return featureOuterNextPos(at);
  }
  AbstractFeature featureCotype(int at)
  {
    if (PRECONDITIONS) require
      (featureHasCotype(at));
    return feature(data().getInt(featureCoTypeOrOriginPos(at)));
  }
  AbstractFeature featureCotypeOrigin(int at)
  {
    if (PRECONDITIONS) require
      (featureIsCotype(at));
    return feature(data().getInt(featureCoTypeOrOriginPos(at)));
  }
  int featureCotypeNextPos(int at)
  {
    return featureCoTypeOrOriginPos(at) + (featureHasCotype(at) || featureIsCotype(at) ? 4 : 0);
  }
  int featureResultTypePos(int at)
  {
    return featureCotypeNextPos(at);
  }
  boolean featureHasResultType(int at)
  {
    var k = featureKind(at) & FuzionConstants.MIR_FILE_KIND_MASK;
    return
      (k != FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_REF    &&
       k != FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_VALUE  &&
       k != AbstractFeature.Kind.Choice           .ordinal() &&
       k != AbstractFeature.Kind.TypeParameter    .ordinal() &&
       k != AbstractFeature.Kind.OpenTypeParameter.ordinal()
       );

  }
  int featureValuesAsOpenTypeFeaturePos(int at)
  {
    var i = featureResultTypePos(at);
    if (featureHasResultType(at))
      {
        i = typeNextPos(i);
      }
    return i;
  }
  AbstractFeature featureValuesAsOpenTypeFeature(int at)
  {
    return feature(data().getInt(featureValuesAsOpenTypeFeaturePos(at)));
  }
  int featureConstraintPos(int at)
  {
    return featureValuesAsOpenTypeFeaturePos(at) + (featureHasOpenTypeFeature(at) ? 4 : 0);
  }
  boolean featureHasConstraint(int at)
  {
    var k = featureKind(at) & FuzionConstants.MIR_FILE_KIND_MASK;
    return
      (k == AbstractFeature.Kind.TypeParameter    .ordinal() ||
       k == AbstractFeature.Kind.OpenTypeParameter.ordinal()    );
  }
  int featureInheritsCountPos(int at)
  {
    var i = featureConstraintPos(at);
    if (featureHasConstraint(at))
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

  int featurePreFeaturePos(int at)
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

  AbstractFeature featurePreFeature(int at)
  {
    AbstractFeature result = null;
    var k = featureKind(at);
    if ((k & FuzionConstants.MIR_FILE_KIND_HAS_PRE_CONDITION_FEATURE ) != 0)
      {
        result = feature(data().getInt(featurePreFeaturePos(at)));
      }
    return result;
  }

  AbstractFeature featurePreBoolFeature(int at)
  {
    AbstractFeature result = null;
    var k = featureKind(at);
    if ((k & FuzionConstants.MIR_FILE_KIND_HAS_PRE_CONDITION_FEATURE ) != 0)
      {
        result = feature(data().getInt(featurePreFeaturePos(at)+4));
      }
    return result;
  }

  AbstractFeature featurePreAndCallFeature(int at)
  {
    AbstractFeature result = null;
    var k = featureKind(at);
    if ((k & FuzionConstants.MIR_FILE_KIND_HAS_PRE_CONDITION_FEATURE ) != 0 &&
        !featureIsConstructor(at))
      {
        result = feature(data().getInt(featurePreFeaturePos(at)+8));
      }
    return result;
  }

  int featurePostFeaturePos(int at)
  {
    var i = featurePreFeaturePos(at);
    var k = featureKind(at);
    var sz =
      (k & FuzionConstants.MIR_FILE_KIND_HAS_PRE_CONDITION_FEATURE ) == 0 ? 0 :
      featureIsConstructor(at)                                            ? 8
                                                                          : 12;
    i = i + ((k & FuzionConstants.MIR_FILE_KIND_HAS_PRE_CONDITION_FEATURE ) != 0 ? sz : 0);
    return i;
  }

  AbstractFeature featurePostFeature(int at)
  {
    AbstractFeature result = null;
    var k = featureKind(at);
    if ((k & FuzionConstants.MIR_FILE_KIND_HAS_POST_CONDITION_FEATURE ) != 0)
      {
        result = feature(data().getInt(featurePostFeaturePos(at)));
      }
    return result;
  }

  int featureRedefinesCountPos(int at)
  {
    var i = featurePostFeaturePos(at);
    var k = featureKind(at);
    i = i + ((k & FuzionConstants.MIR_FILE_KIND_HAS_POST_CONDITION_FEATURE) != 0 ? 4 : 0);
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
  int featureNextPos(int at)
  {
    return innerFeaturesNextPos(featureInnerFeaturesPos(at));
  }


  /*
--asciidoc--

Name
^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

.2+| true     | 1      | int           | name length l
              | l      | byte          | name as utf8 bytes
|====

--asciidoc--
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
--asciidoc--

Type
^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

   | true     | 1      | int           | the kind of this type tk
   | tk==-3   | 1      | unit          | type of universe
   | tk==-2   | 1      | int           | index of type
   | tk==-1   | 1      | int           | index of type parameter feature
.4+| tk>=0    | 1      | int           | index of feature of type
              | 1      | byte          | 0: isValue, 1: isRef, 2: isThisType
              | tk     | Type          | actual generics
              | 1      | Type          | outer type
|====

--asciidoc--
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
   *   | tk==-1 | 1      | int           | index of type parameter feature               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | tk>=0  | 1      | int           | index of feature of type                      |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | byte          | 0: isValue, 1: isRef, 2: isThisType           |
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
  int typeTypeParameterPos(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) == -1);

    return at+4;
  }
  int typeTypeParameter(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) == -1);

    return data().getInt(typeTypeParameterPos(at));
  }
  int CotypePos(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) >= 0);

    return at+4;
  }
  int cotype(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) >= 0);

    return data().getInt(CotypePos(at));
  }
  int typeValRefOrThisPos(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) >= 0);

    return CotypePos(at) + 4;
  }
  int typeValRefOrThis(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) >= 0);

    return data().get(typeValRefOrThisPos(at));
  }
  int typeActualGenericsPos(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) >= 0);

    return typeValRefOrThisPos(at) + 1;
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
        return typeTypeParameterPos(at) + 4;
      }
    else
      {
        at = typeOuterPos(at);
        return typeNextPos(at);
      }
  }


  /*
--asciidoc--

Code
^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

.2+| true     | 1      | int           | sizeof(Expressions)
              | 1      | Expressions   | the actual code
|====


Expressions
^^^^^^^^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

   | true     | n      | Expression    | the single expressions
|====

--asciidoc--
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
--asciidoc--

Expression
^^^^^^^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

   | true     | 1      | byte          | ExprKind k in bits 0..6,  hasPos in bit 7
   | hasPos   | 1      | int           | source position: index in this file's SourceFiles section, 0 for builtIn pos
   | k==Ass   | 1      | Assign        | assignment
   | k==Con   | 1      | Constant      | constant
   | k==Cal   | 1      | Call          | feature call
   | k==Mat   | 1      | Match         | match expression
   | k==Tag   | 1      | Tag           | tag expression
|====

--asciidoc--
   *   +---------------------------------------------------------------------------------+
   *   | Expression                                                                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | byte          | ExprKind k in bits 0..6,  hasPos in bit 7     |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | hasPos | 1      | int           | source position: index in this file's         |
   *   |        |        |               | SourceFiles section, 0 for builtIn pos        |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Ass | 1      | Assign        | assignment                                    |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Con | 1      | Constant      | constant                                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Cal | 1      | Call          | feature call                                  |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Mat | 1      | Match         | match expression                              |
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
    return data().get(expressionKindPos(at)) & 0xff;
  }
  MirExprKind expressionKind(int at)
  {
    return MirExprKind.from(expressionKindRaw(at) & 0x7f);
  }
  boolean expressionHasPosition(int at)
  {
    return (expressionKindRaw(at) & 0x80) != 0;
  }
  int expressionPositionPos(int at)
  {
    return expressionKindPos(at) + 1;
  }
  int expressionPosition(int at)
  {
    if (PRECONDITIONS) require
      (expressionHasPosition(at));

    return data().getInt(expressionPositionPos(at));
  }
  int expressionPositionEnd(int at)
  {
    if (PRECONDITIONS) require
      (expressionHasPosition(at));

    return data().getInt(expressionPositionPos(at)+4);
  }
  int expressionExprPos(int at)
  {
    return expressionPositionPos(at) + (expressionHasPosition(at) ? 8 : 0);
  }

  /**
   * Expr expression at given position, return the position of the following
   * expression or Integer.MAX_VALUE if the expression is MirExprKind.Stop.
   */
  int expressionNextPos(int at)
  {
    var k = expressionKind(at);
    var eAt = expressionExprPos(at);
    return switch (k)
      {
      case Assign      -> assignNextPos(eAt);
      case Const       -> constNextPos(eAt);
      case Current     -> eAt;
      case Match       -> matchNextPos(eAt);
      case Call        -> callNextPos (eAt);
      case Pop         -> eAt;
      case Unit        -> eAt;
      case InlineArray -> inlineArrayNextPos(eAt);
      default          -> throw new Error("unexpected expression kind "+k+" at "+at+" in "+this);
      };
  }


  /*
--asciidoc--

Assign
^^^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

   | true     | 1      | int           | assigned field index
|====

--asciidoc--
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
      (expressionKindRaw(at-1) ==  MirExprKind.Assign.ordinal()         ||
       expressionKindRaw(at-9) == (MirExprKind.Assign.ordinal() | 0x80)    );

    return at;
  }
  int assignField(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Assign.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Assign.ordinal() | 0x80)     );

    return data().getInt(assignFieldPos(at));
  }
  int assignNextPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Assign.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Assign.ordinal() | 0x80)     );

    return assignFieldPos(at) + 4;
  }

  /*
--asciidoc--

Constant
^^^^^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

.3+| true  .2+| 1      | Type          | type of the constant
                       | length        | data length of the constant
              | length | byte          | data of the constant
|====

--asciidoc--
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
     (expressionKindRaw(at-1) ==  MirExprKind.Const.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Const.ordinal() | 0x80)     );

    return at;
  }
  AbstractType constType(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Const.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Const.ordinal() | 0x80)     );

    return type(constTypePos(at));
  }
  int constLengthPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Const.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Const.ordinal() | 0x80)     );

    return typeNextPos(constTypePos(at));
  }
  int constLength(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Const.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Const.ordinal() | 0x80)     );

    return data().getInt(constLengthPos(at));
  }
  int constDataPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Const.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Const.ordinal() | 0x80)     );

    return constLengthPos(at) + 4;
  }
  byte[] constData(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Const.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Const.ordinal() | 0x80)     );

    var l = constLength(at);
    var result = new byte[l];
    data().get(constDataPos(at), result);
    return result;
  }
  int constNextPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Const.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Const.ordinal() | 0x80)     );

    return constDataPos(at) + constLength(at);
  }


  /*
--asciidoc--

Call
^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

.2+| true     | 1      | int           | called feature f index
              | 1      | Type          | result type (NYI: remove, redundant!)s
   | hasOpenArgList
              | 1      | int           | num actual args (TBD: this is redundant,
                                         should be possible to determine)
   | f.generics.isOpen
              | 1      | int           | num actual generics n
   | true     | n      | Type          | actual generics. if !hasOpen, n is
                                         f.generics().list.size()
   | cf.resultType().isOpenGeneric()
              | 1      | int           | select
|====

--asciidoc--
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
   *   | f.gene | 1      | int           | num actual generics n                         |
   *   | rics.is|        |               |                                               |
   *   | Open   |        |               |                                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | n      | Type          | actual generics. if !hasOpen, n is            |
   *   |        |        |               | f.generics().list.size()                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cf.resu| 1      | int           | select                                        |
   *   | ltType(|        |               |                                               |
   *   | ).isOpe|        |               |                                               |
   *   | nGeneri|        |               |                                               |
   *   | c()    |        |               |                                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  int callCalledFeaturePos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Call.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Call.ordinal() | 0x80)     );

    return at;
  }
  int callCalledFeature(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Call.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Call.ordinal() | 0x80)     );

    return data().getInt(callCalledFeaturePos(at));
  }
  int callTypePos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Call.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Call.ordinal() | 0x80)     );

    return callCalledFeaturePos(at) + 4;
  }
  AbstractType callType(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Call.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Call.ordinal() | 0x80)     );

    return type(callTypePos(at));
  }
  int callNumArgsPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Call.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Call.ordinal() | 0x80)     );

    var p = callTypePos(at);
    return typeNextPos(p);
  }
  int callNumArgsRaw(int at)
  {
    if (PRECONDITIONS) require
      (expressionKindRaw(at-1) ==  MirExprKind.Call.ordinal()         ||
       expressionKindRaw(at-9) == (MirExprKind.Call.ordinal() | 0x80)       ,
       libraryFeature(callCalledFeature(at)).hasOpenGenericsArgList());

    return data().getInt(callNumArgsPos(at));
  }
  int callNumArgs(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Call.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Call.ordinal() | 0x80)     );

    var f = libraryFeature(callCalledFeature(at));
    return f.hasOpenGenericsArgList()
      ? callNumArgsRaw(at)
      : f.valueArguments().size();
  }
  int callNumTypeParametersPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Call.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Call.ordinal() | 0x80)     );

    return callNumArgsPos(at) +
      (libraryFeature(callCalledFeature(at)).hasOpenGenericsArgList() ? 4 : 0);
  }
  int callNumTypeParametersRaw(int at)
  {
    if (PRECONDITIONS) require
      (expressionKindRaw(at-1) ==  MirExprKind.Call.ordinal()         ||
       expressionKindRaw(at-9) == (MirExprKind.Call.ordinal() | 0x80)    ,
       libraryFeature(callCalledFeature(at)).generics().isOpen());

    return data().getInt(callNumTypeParametersPos(at));
  }
  int callNumTypeParameters(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Call.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Call.ordinal() | 0x80)     );

    var f = libraryFeature(callCalledFeature(at));
    return f.generics().isOpen()
      ? callNumTypeParametersRaw(at)
      : f.typeArguments().size();
  }
  int callTypeParametersPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Call.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Call.ordinal() | 0x80)     );

    return callNumTypeParametersPos(at) +
      (libraryFeature(callCalledFeature(at)).generics().isOpen() ? 4 : 0);
  }
  int callSelectPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Call.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Call.ordinal() | 0x80)     );

    var n = callNumTypeParameters(at);
    var tat = callTypeParametersPos(at);
    for (var i = 0; i < n; i++)
      {
        tat = typeNextPos(tat);
      }

    return tat;
  }
  int callSelect(int at)
  {
    if (PRECONDITIONS) require
      (expressionKindRaw(at-1) ==  MirExprKind.Call.ordinal()         ||
       expressionKindRaw(at-9) == (MirExprKind.Call.ordinal() | 0x80)    ,
       libraryFeature(callCalledFeature(at)).resultType().isOpenGeneric());

    return data().getInt(callSelectPos(at));
  }
  int callNextPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Call.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Call.ordinal() | 0x80)     );

    var sat = callSelectPos(at);
    var nat = sat + (libraryFeature(callCalledFeature(at)).resultType().isOpenGeneric() ? 4 : 0);

    return nat;
  }


  /*
--asciidoc--

Match
^^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

.2+| true     | 1      | int           | number of cases
              | n      | Case          | cases
|====

--asciidoc--
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
     (expressionKindRaw(at-1) ==  MirExprKind.Match.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Match.ordinal() | 0x80)     );

    return at;
  }
  int matchNumberOfCases(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Match.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Match.ordinal() | 0x80)     );

    return data().getInt(matchNumberOfCasesPos(at));
  }
  int matchCasesPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Match.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Match.ordinal() | 0x80)     );

    return matchNumberOfCasesPos(at) + 4;
  }
  int matchNextPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  MirExprKind.Match.ordinal()         ||
      expressionKindRaw(at-9) == (MirExprKind.Match.ordinal() | 0x80)     );

    var n = matchNumberOfCases(at);
    at = matchCasesPos(at);
    for (var i = 0; i < n; i++)
      {
        at = caseNextPos(at);
      }
    return at;
  }


  /*
--asciidoc--

Case
^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

   | true     | 1      | int           | num types n
   | n = -1   | 1      | int           | case field index
   | n >  0   | n      | Type          | case type
   | true     | 1      | Code          | code for case
|====

--asciidoc--
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
        if (CHECKS) check
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

--asciidoc--

InlineArray
^^^^^^^^^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

   | true     | 1      | int           | size in fum

   | true     | 1      | Code          | Code

   | true     | 1      | int           | element count

   | true     | n      | Code          | element
|====

--asciidoc--
   *   +---------------------------------------------------------------------------------+
   *   | InlineArray                                                                     |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | size in fum                                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Code          | Code                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | element count                                 |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | n      | Code          | element                                       |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  private int inlineArrayNextPos(int at)
  {
    var sz = data().getInt(at);
    return at + 4 /* size int */ + sz /* size of code and type */;
  }
  int inlineArrayCodePos(int at)
  {
    return at+4;
  }
  int inlineArrayCodeElementCountPos(int at)
  {
    return codeNextPos(inlineArrayCodePos(at));
  }
  int inlineArrayCodeElementCount(int at)
  {
    return data().getInt(inlineArrayCodeElementCountPos(at));
  }
  int inlineArrayCodeElementCodePos(int at)
  {
    return inlineArrayCodeElementCountPos(at)+4;
  }


  /*

--asciidoc--

SourceFiles
^^^^^^^^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

.2+| true     | 1      | int           | count n
              | n      | SourceFile    | source file
|====

--asciidoc--
   *   +---------------------------------------------------------------------------------+
   *   | SourceFiles                                                                     |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | count n                                       |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | n      | SourceFile    | source file                                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
   */
  int sourceFilesPos()
  {
    return moduleSourceFilesPos();
  }
  int sourceFilesCountPos()
  {
    return sourceFilesPos();
  }
  int sourceFilesCount()
  {
    return _data.getInt(sourceFilesCountPos());
  }
  int sourceFilesFirstSourceFilePos()
  {
    return sourceFilesCountPos() + 4;
  }

  /*

--asciidoc--

SourceFile
^^^^^^^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

.3+| true     | 1      | Name          | file name
              | 1      | int           | size s
              | s      | byte          | source file data
|====

--asciidoc--
   *   +---------------------------------------------------------------------------------+
   *   | SourceFile                                                                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Name          | file name                                     |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | int           | size s                                        |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | s      | byte          | source file data                              |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
   */
  int sourceFileNamePos(int at)
  {
    return at;
  }
  String sourceFileName(int at)
  {
    return name(sourceFileNamePos(at));
  }
  int sourceFileSizePos(int at)
  {
    return nameNextPos(sourceFileNamePos(at));
  }
  int sourceFileSize(int at)
  {
    return _data.getInt(sourceFileSizePos(at));
  }
  int sourceFileBytesPos(int at)
  {
    return sourceFileSizePos(at) + 4;
  }
  ByteBuffer sourceFileBytes(int at)
  {
    return _data.slice(sourceFileBytesPos(at), sourceFileSize(at));
  }
  int sourceFileNextPos(int at)
  {
    return sourceFileBytesPos(at) + sourceFileSize(at);
  }


  /*-------------------------------  misc  ------------------------------*/


  /**
   * Create a Source position instance for the given position in this library file.
   *
   * @param pos the position, may be -1 for undefined or 0 for
   * SourcePosition.builtIn, otherwise a valid index into a source file in this
   * module.
   */
  SourcePosition pos(int pos, int posEnd)
  {
    if (pos < 0)
      {
        return SourcePosition.notAvailable;
      }
    else if (pos == 0)
      {
        return SourcePosition.builtIn;
      }
    else
      {
        var at = sourceFilesFirstSourceFilePos();
        if (CHECKS) check
          (pos > at);
        var i = 0;
        while (pos > sourceFileNextPos(at))
          {
            at = sourceFileNextPos(at);
            i++;
            if (CHECKS) check
              (i < sourceFilesCount());
          }
        var sf = _sourceFiles.get(i);
        if (sf == null)
          {
            var bb = sourceFileBytes(at);
            var ba = new byte[bb.limit()]; // NYI: Would be better if SourceFile could use bb directly.
            bb.get(0, ba);
            sf = new SourceFile(Path.of("{" + name() + FuzionConstants.MODULE_FILE_SUFFIX + "}").resolve(Path.of(sourceFileName(at))), ba);
            _sourceFiles.set(i, sf);
          }
        return new SourceRange(sf, pos - sourceFileBytesPos(at), posEnd - sourceFileBytesPos(at));
      }
  }


  /**
   * Create annotated hex dump of this module file.
   */
  public String dump()
  {
    var hd = new HexDump(_data);
    hd.mark(0, FuzionConstants.MIR_FILE_MAGIC_EXPLANATION);
    hd.mark(namePos(), "module name");
    hd.mark(hashPos(), "module hash");
    hd.mark(moduleRefsCountPos(), "module refs count");
    hd.mark(moduleRefsPos(), "module refs");
    hd.mark(moduleNumDeclFeaturesPos(), "declFeatures count");
    var nd = moduleNumDeclFeatures();
    var at = moduleDeclFeaturesPos();
    while (nd > 0)
      {
        hd.mark(at, "DeclFeatures");
        hd.mark(declFeaturesInnerPos(at), "InnerFeatures");
        dump(hd, features(feature(declFeaturesOuter(at))));
        at = declFeaturesNextPos(at);
        nd--;
      }
    hd.mark(sourceFilesPos(), "SourceFiles");
    var n = sourceFilesCount();
    at = sourceFilesFirstSourceFilePos();
    while (n > 0)
      {
        hd.mark(at, "Source: " + sourceFileName(at));
        at = sourceFileNextPos(at);
        n--;
      }
    return hd.toString();
  }


  /**
   * Helper for dump to recursively annotate hex dump for features
   *
   * @param hd the hex dump instance
   *
   * @param fs the features to annotate.
   */
  private void dump(HexDump hd, List<AbstractFeature> fs)
  {
    for (var f: fs)
      {
        var lf = (LibraryFeature) f;
        var li = lf._index;
        hd.mark(li, featureKindEnum(li).toString());
        hd.mark(featureNamePos(li), f.qualifiedName());
        if (featureIsRoutine(li))
          {
            hd.mark(featureCodePos(li), "code");
          }
        dump(hd, innerFeatures(featureInnerFeaturesPos(li)));
      }
  }


  /**
   * Create String representation for debugging.
   */
  public String toString()
  {
    return "LibraryModule for '" + name() + "'";
  }


  /**
   * The path of the sources of this library module.
   */
  public String srcPath()
  {
    // NYI: CLEANUP: library-module should/could know its source dirs.
    return Path.of(Version.REPO_PATH)
      .resolve("modules").resolve(name()).resolve("src")
      .toString();
  }


  @Override
  public ByteBuffer data(String name)
  {
    throw new UnsupportedOperationException("Unimplemented method 'data'");
  }


  /**
   * Is this module the same as the provided one or does this module depend on the provided one?
   *
   * @param lm the LibraryModule against which this module should be checked
   * @return true iff they are the same or this module depends on the provided one
   */
  public boolean sameOrDependent(LibraryModule lm)
  {
    return lm == this || Arrays.asList(_modules).stream().map(r->r._module).anyMatch(x->x==lm);
  }

}

/* end of file */
