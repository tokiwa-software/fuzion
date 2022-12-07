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
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Env;
import dev.flang.ast.Expr;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.FormalGenerics;
import dev.flang.ast.Generic;
import dev.flang.ast.Type;
import dev.flang.ast.Types;

import dev.flang.ir.IR;

import dev.flang.mir.MIR;

import dev.flang.util.FuzionConstants;
import dev.flang.util.HexDump;
import dev.flang.util.List;
import dev.flang.util.SourceDir;
import dev.flang.util.SourceFile;
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
   * As long as source position is not part of the .fum/MIR file, use this
   * constant as a place holder.
   */
  static SourcePosition DUMMY_POS = SourcePosition.builtIn;


  /**
   * NYI: Instead of using env var, create a new tool "fzdump" or similar to
   * dump intermediate files.
   */
  static final boolean DUMP = "true".equals(System.getenv("FUZION_DUMP_MODULE_FILE"));


  /**
   * Pre-allocated empty array
   */
  static byte[] NO_BYTES = new byte[0];


  /*----------------------------  variables  ----------------------------*/


  /**
   * The base index of this module. When converting local indices to global
   * indices, the _globalBase will be added.
   */
  final int _globalBase;


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
   * Cache for innerFeatures created from given index
   */
  Map<Integer, List<AbstractFeature>> _innerFeatures = new TreeMap<>();


  /**
   * Source code files, created on demand
   */
  private final ArrayList<SourceFile> _sourceFiles;


  /**
   * The universe
   */
  final AbstractFeature _universe;


  /**
   * Modules referenced from this module
   */
  final ModuleRef[] _modules;


  /**
   * The front end that loaded this module.
   */
  private final FrontEnd _fe;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create LibraryModule for given options and sourceDirs.
   */
  LibraryModule(int globalBase, FrontEnd fe, ByteBuffer data, LibraryModule[] dependsOn, AbstractFeature universe)
  {
    super(dependsOn);

    _globalBase = globalBase;
    _fe = fe;
    _mir = null;
    _data = data;
    _universe = universe;
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
    for (int i = 0; i < mrc; i++)
      {
        var n = moduleRefName(p);
        var v = moduleRefVersion(p);
        var mr = new ModuleRef(moduleOffset, n, v, fe.loadModule(n));
        _modules[i] = mr;
        moduleOffset = moduleOffset + mr.size();
        p = moduleRefNextPos(p);
      }

    var dm = fe._options._dumpModules;
    if (DUMP ||
        dm != null && dm.contains(name()))
      {
        System.out.println(dump());
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Get the ModuleRef instance with given index.  ModuleRef instances refer to
   * other modules that this module depennds on.
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
  AbstractFeature universe()
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
  public SortedMap<FeatureName, AbstractFeature>declaredFeatures(AbstractFeature outer)
  {
    var result = new TreeMap<FeatureName, AbstractFeature>();
    var l = (outer instanceof LibraryFeature lf && lf._libModule == this)
      ? lf.declaredFeatures() // the declared features are declared in this module
      : features(outer);      // the declared features are declared in another module
    for (var d : l)
      {
        result.put(d.featureName(), d);  // NYI: handle equally named features from different modules
      }
    return result;
  }


  /**
   * Get or create LibraryFeature at given offset
   *
   * @param offset the offset in data()
   *
   * @return the LibraryFeature declared at offset in this module.
   */
  LibraryFeature libraryFeature(int offset)
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

        return mr._module.libraryFeature(offset - mr._offset);
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
  Generic genericArgument(int offset)
  {
    var tp = feature(offset);
    var o = tp.outer();
    for (var g : o.generics().list)
      {
        if (g.typeParameter() == tp)
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
        if (k == -4)
          {
            return Types.t_ADDRESS;
          }
        else if (k == -3)
          {
            return _fe._universe.thisType();
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
            result = new GenericType(this, at, DUMMY_POS, genericArgument(typeTypeParameter(at)));
          }
        else
          {
            if (CHECKS) check
              (k >= 0);
            var feature = libraryFeature(typeFeature(at));
            var makeThisType = typeIsThisType(at);
            var makeRef = typeIsRef(at);
            var generics = Type.NONE;
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
            result = new NormalType(this, at, DUMMY_POS, feature,
                                    makeThisType ? Type.RefOrVal.ThisType :
                                    makeRef      ? Type.RefOrVal.Ref
                                                 : Type.RefOrVal.LikeUnderlyingFeature,
                                    generics, outer);
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

.6+|true      | 1      | byte[]        | MIR_FILE_MAGIC

              | 1      | Name          | module name

              | 1      | u128          | module version

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
   *   | true   | 1      | byte[]        | MIR_FILE_MAGIC                                |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | Name          | module name                                   |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | u128          | module version                                |
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
  String name()
  {
    return name(namePos());
  }
  int nameNextPos()
  {
    return nameNextPos(namePos());
  }
  int versionPos()
  {
    return nameNextPos();
  }
  byte[] version(int at)
  {
    var r = new byte[16];
    for (int i = 0; i<r.length; i++)
      {
        r[i] = _data.get(at);
        at++;
      }
    return r;
  }
  byte[] version()
  {
    return version(versionPos());
  }
  int versionNextPos()
  {
    return versionPos() + 16;
  }
  int moduleRefsCountPos()
  {
    return versionNextPos();
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

              | 1      | u128          | module version
|====

--asciidoc--

   *   +---------------------------------------------------------------------------------+
   *   | ModuleRef                                                                       |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Name          | module name                                   |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | u128          | module version                                |
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
  int moduleRefVersionPos(int at)
  {
    return moduleRefNameNextPos(at);
  }
  byte[] moduleRefVersion(int at)
  {
    return version(moduleRefVersionPos(at));
  }
  int moduleRefVersionNextPos(int at)
  {
    return moduleRefVersionPos(at) + 16;
  }
  int moduleRefNextPos(int at)
  {
    return moduleRefVersionNextPos(at);
  }


  /*
--asciidoc--

DeclFeatures
^^^^^^^^^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

.6+|true      | 1      | int           | outer feature index, 0 for outer==universe

              | 1      | InnerFeatures | inner Features
|====

--asciidoc--

   *   +---------------------------------------------------------------------------------+
   *   | DeclFeatures                                                                    |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | outer feature index, 0 for outer()==universe  |
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

   |          | 1      | Features      | inner Features
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
.6+| true  .6+| 1      | byte          | 00CYkkkk  k = kind, Y = has Type feature (i.e., 'f.type'), C = is intrinsic constructor
                       | Name          | name
                       | int           | arg count
                       | int           | name id
                       | Pos           | source code position
                       | int           | outer feature index, 0 for outer()==universe
   | hasRT    | 1      | Type          | optional result type,
                                       hasRT = !isConstructor && !isChoice
.2+| true NYI! !isField? !isIntrinsc
              | 1      | int           | inherits count i
              | i      | Code          | inherits calls
.4+| true     | 1      | int           | precondition count pre_n
              | pre_n  | Code          | precondition code
              | 1      | int           | postcondition count post_n
              | post_n | Code          | postcondition code
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
   *   | true   | 1      | byte          | 00CYkkkk  k = kind                            |
   *   |        |        |               |           Y = has Type feature (i.e. 'f.type')|
   *   |        |        |               |           C = is intrinsic constructor        |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | Name          | name                                          |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | arg count                                     |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | name id                                       |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | Pos           | source code position                          |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | outer feature index, 0 for outer()==universe  |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | Y=1    | 1      | int           | type feature index                            |
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
   *   | true   | 1      | int           | precondition count pre_n                      |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | pre_n  | Code          | precondition code                             |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | int           | postcondition count post_n                    |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | post_n | Code          | postcondition code                            |
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
  boolean featureIsIntrinsicConstructor(int at)
  {
    return ((featureKind(at) & FuzionConstants.MIR_FILE_KIND_IS_INTRINSIC_CONSTRUCTOR) != 0);
  }
  boolean featureHasTypeFeature(int at)
  {
    return ((featureKind(at) & FuzionConstants.MIR_FILE_KIND_HAS_TYPE_FEATURE) != 0);
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
  int featurePositionNextPos(int at)
  {
    return featurePositionPos(at) + 4;
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
  int featureTypeFeaturePos(int at)
  {
    return featureOuterNextPos(at);
  }
  AbstractFeature featureTypeFeature(int at)
  {
    if (PRECONDITIONS) require
      (featureHasTypeFeature(at));
    return feature(data().getInt(featureTypeFeaturePos(at)));
  }
  int featureTypeFeatureNextPos(int at)
  {
    return featureTypeFeaturePos(at) + (featureHasTypeFeature(at) ? 4 : 0);
  }
  int featureResultTypePos(int at)
  {
    return featureTypeFeatureNextPos(at);
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

  int featurePreCondCountPos(int at)
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
  int featurePreCondCount(int at)
  {
    return data().getInt(featurePreCondCountPos(at));
  }
  int featurePreCondPos(int at)
  {
    return featurePreCondCountPos(at) + 4;
  }

  int featurePostCondCountPos(int at)
  {
    var i = featurePreCondPos(at);
    var ic = featurePreCondCount(at);
    while (ic > 0)
      {
        i = codeNextPos(i);
        ic--;
      }
    return i;
  }
  int featurePostCondCount(int at)
  {
    return data().getInt(featurePostCondCountPos(at));
  }
  int featurePostCondPos(int at)
  {
    return featurePostCondCountPos(at) + 4;
  }

  int featureRedefinesCountPos(int at)
  {
    var i = featurePostCondPos(at);
    var ic = featurePostCondCount(at);
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
   | tk==-4   | 1      | unit          | ADDRESS
   | tk==-3   | 1      | unit          | type of universe
   | tk==-2   | 1      | int           | index of type
   | tk==-1   | 1      | int           | index of type parameter feature
.4+| tk>=0    | 1      | int           | index of feature of type
              | 1      | byte          | 0: default, 1: isRef, 2: isThisType
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
   *   | tk==-4 | 1      | unit          | ADDRESS                                       |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | tk==-3 | 1      | unit          | type of universe                              |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | tk==-2 | 1      | int           | index of type                                 |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | tk==-1 | 1      | int           | index of type parameter feature               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | tk>=0  | 1      | int           | index of feature of type                      |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | byte          | 0: default, 1: isRef, 2: isThisType           |
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

    return data().get(typeIsRefPos(at)) == 1;
  }
  boolean typeIsThisType(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) >= 0);

    return data().get(typeIsRefPos(at)) == 2;
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
   | k==Add   | 1      | Assign        | assignment
   | k==Unb   | 1      | Unbox         | unbox expression
   | k==Con   | 1      | Constant      | constant
   | k==Cal   | 1      | Call          | feature call
   | k==Mat   | 1      | Match         | match statement
   | k==Tag   | 1      | Tag           | tag expression
   | k==Env   | 1      | Env           | env expression
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
   *   | k==Env | 1      | Env           | env expression                                |
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
  IR.ExprKind expressionKind(int at)
  {
    return IR.ExprKind.from(expressionKindRaw(at) & 0x7f);
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
  int expressionExprPos(int at)
  {
    return expressionPositionPos(at) + (expressionHasPosition(at) ? 4 : 0);
  }
  int expressionNextPos(int at)
  {
    var k = expressionKind(at);
    var eAt = expressionExprPos(at);
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
      case Env     -> envNextPos  (eAt);
      case Pop     -> eAt;
      case Unit    -> eAt;
      default      -> throw new Error("unexpected expression kind "+k+" at "+at+" in "+this);
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
      (expressionKindRaw(at-1) ==  IR.ExprKind.Assign.ordinal()         ||
       expressionKindRaw(at-5) == (IR.ExprKind.Assign.ordinal() | 0x80)    );

    return at;
  }
  int assignField(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Assign.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Assign.ordinal() | 0x80)     );

    return data().getInt(assignFieldPos(at));
  }
  int assignNextPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Assign.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Assign.ordinal() | 0x80)     );

    return assignFieldPos(at) + 4;
  }


  /*
--asciidoc--

Unbox
^^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

.2+| true     | 1      | Type          | result type
              | 1      | bool          | needed flag (NYI: What is this? remove?)
|====

--asciidoc--
   *   +---------------------------------------------------------------------------------+
   *   | Unbox                                                                           |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Type          | result type                                   |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | bool          | needed flag (NYI: What is this? remove?)      |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  int unboxTypePos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Unbox.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Unbox.ordinal() | 0x80)     );

    return at;
  }
  AbstractType unboxType(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Unbox.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Unbox.ordinal() | 0x80)     );

    return type(unboxTypePos(at));
  }
  int unboxNeededPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Unbox.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Unbox.ordinal() | 0x80)     );

    return typeNextPos(unboxTypePos(at));
  }
  boolean unboxNeeded(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Unbox.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Unbox.ordinal() | 0x80)     );

    return data().get(unboxNeededPos(at)) != 0;
  }
  int unboxNextPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Unbox.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Unbox.ordinal() | 0x80)     );

    return unboxNeededPos(at) + 1;
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
     (expressionKindRaw(at-1) ==  IR.ExprKind.Const.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Const.ordinal() | 0x80)     );

    return at;
  }
  AbstractType constType(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Const.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Const.ordinal() | 0x80)     );

    return type(constTypePos(at));
  }
  int constLengthPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Const.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Const.ordinal() | 0x80)     );

    return typeNextPos(constTypePos(at));
  }
  int constLength(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Const.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Const.ordinal() | 0x80)     );

    return data().getInt(constLengthPos(at));
  }
  int constDataPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Const.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Const.ordinal() | 0x80)     );

    return constLengthPos(at) + 4;
  }
  byte[] constData(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Const.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Const.ordinal() | 0x80)     );

    var l = constLength(at);
    var result = new byte[l];
    data().get(constDataPos(at), result);
    return result;
  }
  int constNextPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Const.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Const.ordinal() | 0x80)     );

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
     (expressionKindRaw(at-1) ==  IR.ExprKind.Call.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Call.ordinal() | 0x80)     );

    return at;
  }
  int callCalledFeature(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Call.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Call.ordinal() | 0x80)     );

    return data().getInt(callCalledFeaturePos(at));
  }
  int callTypePos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Call.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Call.ordinal() | 0x80)     );

    return callCalledFeaturePos(at) + 4;
  }
  AbstractType callType(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Call.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Call.ordinal() | 0x80)     );

    return type(callTypePos(at));
  }
  int callNumArgsPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Call.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Call.ordinal() | 0x80)     );

    var p = callTypePos(at);
    return typeNextPos(p);
  }
  int callNumArgsRaw(int at)
  {
    if (PRECONDITIONS) require
      (expressionKindRaw(at-1) ==  IR.ExprKind.Call.ordinal()         ||
       expressionKindRaw(at-5) == (IR.ExprKind.Call.ordinal() | 0x80)       ,
       libraryFeature(callCalledFeature(at)).hasOpenGenericsArgList());

    return data().getInt(callNumArgsPos(at));
  }
  int callNumArgs(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Call.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Call.ordinal() | 0x80)     );

    var f = libraryFeature(callCalledFeature(at));
    return f.hasOpenGenericsArgList()
      ? callNumArgsRaw(at)
      : f.valueArguments().size();
  }
  int callNumTypeParametersPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Call.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Call.ordinal() | 0x80)     );

    return callNumArgsPos(at) +
      (libraryFeature(callCalledFeature(at)).hasOpenGenericsArgList() ? 4 : 0);
  }
  int callNumTypeParametersRaw(int at)
  {
    if (PRECONDITIONS) require
      (expressionKindRaw(at-1) ==  IR.ExprKind.Call.ordinal()         ||
       expressionKindRaw(at-5) == (IR.ExprKind.Call.ordinal() | 0x80)    ,
       libraryFeature(callCalledFeature(at)).generics().isOpen());

    return data().getInt(callNumTypeParametersPos(at));
  }
  int callNumTypeParameters(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Call.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Call.ordinal() | 0x80)     );

    var f = libraryFeature(callCalledFeature(at));
    return f.generics().isOpen()
      ? callNumTypeParametersRaw(at)
      : f.typeArguments().size();
  }
  int callTypeParametersPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Call.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Call.ordinal() | 0x80)     );

    return callNumTypeParametersPos(at) +
      (libraryFeature(callCalledFeature(at)).generics().isOpen() ? 4 : 0);
  }
  int callSelectPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Call.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Call.ordinal() | 0x80)     );

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
      (expressionKindRaw(at-1) ==  IR.ExprKind.Call.ordinal()         ||
       expressionKindRaw(at-5) == (IR.ExprKind.Call.ordinal() | 0x80)    ,
       libraryFeature(callCalledFeature(at)).resultType().isOpenGeneric());

    return data().getInt(callSelectPos(at));
  }
  int callNextPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Call.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Call.ordinal() | 0x80)     );

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
   |          | n      | Case          | cases
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
     (expressionKindRaw(at-1) ==  IR.ExprKind.Match.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Match.ordinal() | 0x80)     );

    return at;
  }
  int matchNumberOfCases(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Match.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Match.ordinal() | 0x80)     );

    return data().getInt(matchNumberOfCasesPos(at));
  }
  int matchCasesPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Match.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Match.ordinal() | 0x80)     );

    return matchNumberOfCasesPos(at) + 4;
  }
  int matchNextPos(int at)
  {
    if (PRECONDITIONS) require
     (expressionKindRaw(at-1) ==  IR.ExprKind.Match.ordinal()         ||
      expressionKindRaw(at-5) == (IR.ExprKind.Match.ordinal() | 0x80)     );

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

Tag
^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

   | true     | 1      | Type          | resulting tagged union type
|====

--asciidoc--
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
    return type(tagTypePos(at));
  }
  int tagNextPos(int at)
  {
    return typeNextPos(tagTypePos(at));
  }



  /*

--asciidoc--

Env
^^^^

[options="header",cols="1,1,2,5"]
|====
   |cond.     | repeat | type          | what

   | true     | 1      | Type          | type of resulting env value
|====

--asciidoc--
   *   +---------------------------------------------------------------------------------+
   *   | Env                                                                             |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Type          | type of resulting env value                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  int envTypePos(int at)
  {
    return at;
  }
  AbstractType envType(int at)
  {
    return type(envTypePos(at));
  }
  int envNextPos(int at)
  {
    return typeNextPos(envTypePos(at));
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

.2+| true     | 1      | Name          | file name
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
  SourcePosition pos(int pos)
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
            var ba = new byte[bb.limit()]; // NYI: Would be better if SoureFile could use bb directly.
            bb.get(0, ba);
            sf = new SourceFile(Path.of(sourceFileName(at)), ba);
            _sourceFiles.set(i, sf);
          }
        return new SourcePosition(sf, pos - sourceFileBytesPos(at));
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
    hd.mark(versionPos(), "module version");
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
   * Helper for dump to recursivle annotate hex dump for features
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

}

/* end of file */
