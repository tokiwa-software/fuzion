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
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.Generic;

import dev.flang.mir.MIR;

import dev.flang.util.FuzionConstants;
import dev.flang.util.SourceDir;


/**
 * A LibraryModule represents a Fuzion module loaded from a precompiled Fuzion
 * module file .fum.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class LibraryModule extends Module
{


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


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create SourceModule for given options and sourceDirs.
   */
  LibraryModule(FrontEndOptions options, SourceDir[] sourceDirs, Path inputFile, String defaultMain, Module[] dependsOn, Feature universe)
  {
    super(dependsOn);

    _options = options;
    _srcModule = new SourceModule(options, sourceDirs, inputFile, defaultMain, dependsOn, universe);
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
                var n = featureTypeArgsCount(i);
                var j = featureTypeArgsListPos(i);
                var ix = 0;
                while (result == null && ix < n)
                  {
                    if (j == offset)
                      {
                        result = o.generics().list.get(ix);
                      }
                    j = nextTypeArg(j);
                    ix++;
                  }
              }
            else
              {
                check
                  (o != null);
                var inner = featureInnerSizePos(i);
                if (inner <= offset && offset <= nextFeaturePos(i))
                  {
                    result = findGenericArgument(offset, o, inner);
                  }
              }
            i = nextFeaturePos(i);
            check(result != null || i <= offset);
          }
      }
    return result;
  }


  /*---------------------  accessing mir file data  ---------------------*/


  int featureKindPos(int at)
  {
    return at;
  }
  int featureKind(int at)
  {
    var ko = data().get(featureKindPos(at));
    return ko;
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
  int featureIdPosAfter(int at)
  {
    return featureIdPos(at) + 4;
  }
  int featureId(int at)
  {
    return data().getInt(featureIdPos(at));
  }
  int featureTypeArgsCountPos(int at)
  {
    if (PRECONDITIONS) require
      ((featureKind(at) & FuzionConstants.MIR_FILE_KIND_HAS_TYPE_PAREMETERS) != 0);

    return featureIdPosAfter(at);
  }
  int featureTypeArgsCount(int at)
  {
    if (PRECONDITIONS) require
      ((featureKind(at) & FuzionConstants.MIR_FILE_KIND_HAS_TYPE_PAREMETERS) != 0);

    return data().getInt(featureTypeArgsCountPos(at));
  }
  int featureTypeArgsOpenPos(int at)
  {
    if (PRECONDITIONS) require
      ((featureKind(at) & FuzionConstants.MIR_FILE_KIND_HAS_TYPE_PAREMETERS) != 0);

    return featureTypeArgsCountPos(at) + 4;
  }
  boolean featureTypeArgsOpen(int at)
  {
    if (PRECONDITIONS) require
      ((featureKind(at) & FuzionConstants.MIR_FILE_KIND_HAS_TYPE_PAREMETERS) != 0);

    return data().get(featureTypeArgsOpenPos(at)) != 0;
  }
  int featureTypeArgsListPos(int at)   // works also if list is empty
  {
    return featureTypeArgsOpenPos(at) + 1;
  }
  int featureResultTypePos(int at)
  {
    var i = featureIdPosAfter(at);
    if ((featureKind(at) & FuzionConstants.MIR_FILE_KIND_HAS_TYPE_PAREMETERS) != 0)
      {
        var d = data();
        var n = featureTypeArgsCount(at);
        i = featureTypeArgsListPos(at);
        while (n > 0)
          {
            i = nextTypeArg(i);
            n--;
          }
      }
    return i;
  }
  boolean featureHasResultType(int at)
  {
    var k = featureKind(at) & FuzionConstants.MIR_FILE_KIND_MASK;
    return
      (k != FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_REF   &&
       k != FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_VALUE &&
       k != AbstractFeature.Kind.Choice.ordinal());
  }
  int featureInnerSizePos(int at)
  {
    var i = featureResultTypePos(at);
    if (featureHasResultType(at))
      {
        i = nextTypePos(i);
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
  int nextFeaturePos(int at)
  {
    var next = featureInnerPos(at) + featureInnerSize(at);
    return next;
  }

  String typeArgName(int at)
  {
    return name(at);
  }
  int nextTypeArg(int at)
  {
    return nextName(at);
  }

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
  int nextName(int at)
  {
    var i = at;
    var d = data();
    var l = d.getInt(i);
    i = i + 4 + l;
    return i;
  }


  int nextTypePos(int at)
  {
    var k = data().getInt(at);
    at = at + 4;
    if (k == -1)
      {
        return at + 4;
      }
    else
      {
        int f = data().getInt(at);
        at = at + 4;
        int n = k;
        for (var i = 0; i<n; i++)
          {
            at = nextTypePos(at);
          }
        return at;
      }
  }


  int typeKind(int at)
  {
    return data().getInt(at);
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
  int typeActualGenericsPos(int at)
  {
    if (PRECONDITIONS) require
      (typeKind(at) >= 0);

    return typeFeaturePos(at) + 4;
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
