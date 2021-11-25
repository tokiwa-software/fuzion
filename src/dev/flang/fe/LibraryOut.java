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
 * Source of class LibraryOut
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.TreeMap;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Feature;
import dev.flang.ast.FormalGenerics;
import dev.flang.ast.Generic;

import dev.flang.util.DataOut;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;


/**
 * LibraryOut creates data for a .fum/MIR file from a SourceModule
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class LibraryOut extends DataOut
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The underlying module we are saving as a library.
   */
  private final SourceModule _sourceModule;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor to write library for given SourceModule.
   */
  LibraryOut(SourceModule sm)
  {
    super();

    _sourceModule = sm;
    write(FuzionConstants.MIR_FILE_MAGIC);
    innerFeatures(sm._universe);
    fixUps();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Collect the binary data for features declared within given feature.
   *
   * Data format for inner features:
   *
   *   +-------------------------------------------------------------------------------+
   *   | InnerFeatures                                                                 |
   *   +--------+--------+-------------+-----------------------------------------------+
   *   | cond.  | repeat | type        | what                                          |
   *   +--------+--------+-------------+-----------------------------------------------+
   *   | true   | 1      | int         | sizeof(inner Features) isz                    |
   *   +        +--------+-------------+-----------------------------------------------+
   *   |        | 1      | Features    | inner Features                                |
   *   +--------+--------+-------------+-----------------------------------------------+
   *
   * The count n is not stored explicitly, the list of inner Features ends after
   * isz bytes.
   */
  void innerFeatures(Feature f)
  {
    var m = _sourceModule.declaredFeaturesOrNull(f);
    if (m == null)
      {
        writeInt(0);
      }
    else
      {
        // the first inner features written out will be the formal arguments,
        // followed by the result field (iff f.hasResultField()), followed by
        // all other inner features in (alphabetical?) order.
        var innerFeatures = new List<AbstractFeature>();
        var added = new TreeSet<AbstractFeature>();
        for (var a : f.arguments())
          {
            innerFeatures.add(a);
            added.add(a);
          }
        if (f.hasResultField())
          {
            var r = f.resultField();
            innerFeatures.add(r);
            added.add(r);
          }
        if (f.hasOuterRef())
          {
            var or = f.outerRef();
            innerFeatures.add(or);
            added.add(or);
          }
        if (f.isChoice())
          {
            check
              (Errors.count() > 0 || added.isEmpty()); // a choice has no arguments, no result and no outer ref.
            var ct = f.choiceTag();
            innerFeatures.add(ct);
            added.add(ct);
          }
        for (var i : m.values())
          {
            if (!added.contains(i))
              {
                innerFeatures.add(i);
              }
          }

        var szPos = offset();
        writeInt(0);
        var innerPos = offset();

        // write the actual data
        features(innerFeatures);
        writeIntAt(szPos, offset() - innerPos);
      }
  }


  /**
   * Collect the binary data for a list of features.
   *
   *   +-------------------------------------------------------------------------------+
   *   | Features                                                                      |
   *   +--------+--------+-------------+-----------------------------------------------+
   *   | cond.  | repeat | type        | what                                          |
   *   +--------+--------+-------------+-----------------------------------------------+
   *   | true   | n      | Feature     | (inner) Features                              |
   *   +--------+--------+-------------+-----------------------------------------------+
   *
   */
  void features(List<AbstractFeature> fs)
  {
    for (var df : fs)
      {
        if (df instanceof Feature dff)
          {
            feature(dff);
          }
      }
  }


  /**
   * Collect the binary data for given feature.
   *
   * Data format for a feature:
   *
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
   *   |        | 1      | InnerFeatures | inner features of this feature                |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
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
   *
   *   +---------------------------------------------------------------------------------+
   *   | TypeArg                                                                         |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Name          | type arg name                                 |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  void feature(Feature f)
  {
    _offsetsForFeature.put(f, offset());
    var ix = offset();
    var k =
      !f.isConstructor() ? f.kind().ordinal() :
      f.isThisRef()      ? FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_REF
                         : FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_VALUE;
    check
      (k >= 0,
       f.isConstructor() || k < FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_VALUE);
    check
      (Errors.count() > 0 || f.isRoutine() || f.isChoice() || f.isIntrinsic() || f.isAbstract() || f.generics() == FormalGenerics.NONE);
    if (f.generics() != FormalGenerics.NONE)
      {
        k = k | FuzionConstants.MIR_FILE_KIND_HAS_TYPE_PAREMETERS;
      }
    var n = f.featureName();
    write(k);
    writeName(n.baseName());  // NYI: internal names (outer refs, statement results) are too long and waste memory
    writeInt (n.argCount());  // NYI: use better integer encoding
    writeInt (n._id);         // NYI: id /= 0 only if argCount = 0, so join these two values.
    if ((k & FuzionConstants.MIR_FILE_KIND_HAS_TYPE_PAREMETERS) != 0)
      {
        check
          (f.generics().list.size() > 0);
        writeInt(f.generics().list.size());
        write(f.generics().isOpen() ? 1 : 0);
        for (var g : f.generics().list)
          {
            _offsetsForGeneric.put(g, offset());
            writeName(g.name());
          }
      }
    check
      (f.arguments().size() == f.featureName().argCount());
    if (!f.isConstructor() && !f.isChoice())
      {
        type(f.resultType());
      }
    innerFeatures(f);
    _sourceModule.registerOffset(f, ix);
  }


  /**
   * Collect the binary data for given type.
   *
   * Data format for a type:
   *
   *   +---------------------------------------------------------------------------------+
   *   | Type                                                                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | the kind of this type tk                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | tk==-1 | 1      | int           | index of generic argument                     |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | tk>=0  | 1      | int           | index of feature of type                      |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | tk     | Type          | actual generics                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  void type(AbstractType t)
  {
    if (t.isGenericArgument())
      {
        writeInt(-1);
        writeOffset(t.generic());
      }
    else
      {
        writeInt(t.generics().size());
        writeOffset(t.featureOfType());
        for (var gt : t.generics())
          {
            type(gt);
          }
      }
  }


  /*--------------------------  fixup handling  -------------------------*/


  /**
   * Generics that are referenced before being defined and hence need a fixup:
   */
  ArrayList<Generic> _fixUpsG = new ArrayList<>();


  /**
   * Positions of fixups for generics
   */
  ArrayList<Integer> _fixUpsGAt = new ArrayList<>();


  /**
   * Generic offsets in this file
   */
  Map<Generic, Integer> _offsetsForGeneric = new HashMap<>();


  /**
   * Features that are referenced before being defined and hence need a fixup:
   */
  ArrayList<AbstractFeature> _fixUpsF = new ArrayList<>();


  /**
   * Positions of fixups for features
   */
  ArrayList<Integer> _fixUpsFAt = new ArrayList<>();


  /**
   * Feature offsets in this file
   */
  Map<AbstractFeature, Integer> _offsetsForFeature = new TreeMap<>();


  /**
   * Write offset of given generic, create fixup if not known yet.
   */
  void writeOffset(Generic g)
  {
    var o = _offsetsForGeneric.get(g);
    var v = o == null ? -1 : (int) o;
    if (o == null)
      {
        _fixUpsG.add(g);
        _fixUpsGAt.add(offset());
      }
    writeInt(v);
  }

  /**
   * Write offset of given feature, create fixup if not known yet.
   */
  void writeOffset(AbstractFeature f)
  {
    var o = _offsetsForFeature.get(f);
    var v = o == null ? -1 : (int) o;
    if (o == null)
      {
        _fixUpsF.add(f);
        _fixUpsFAt.add(offset());
      }
    writeInt(v);
  }


  /**
   * Perform fixups
   */
  private void fixUps()
  {
    for (var i = 0; i<_fixUpsG.size(); i++)
      {
        var g  = _fixUpsG  .get(i);
        var at = _fixUpsGAt.get(i);
        var o = _offsetsForGeneric.get(g);
        check
          (o != null);
        writeIntAt(at, o);
      }
    for (var i = 0; i<_fixUpsF.size(); i++)
      {
        var g  = _fixUpsF  .get(i);
        var at = _fixUpsFAt.get(i);
        var o = _offsetsForFeature.get(g);
        check
          (o != null);
        writeIntAt(at, o);
      }
  }

}

/* end of file */
