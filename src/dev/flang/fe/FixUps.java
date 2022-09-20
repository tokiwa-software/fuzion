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
 * Source of class FixUps
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Feature;

import dev.flang.util.DataOut;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * FixUps is a helper class for LibraryOut that extends DataOut with methods to
 * fix up indices and offsets that are known only after feature, types,
 * etc. were written.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class FixUps extends DataOut
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Set of modules referenced by features written out.
   */
  private Map<LibraryModule, Integer> _referencedModules = new TreeMap<LibraryModule, Integer>((x,y) -> x.name().compareTo(y.name()));


  /**
   * Features that are referenced before being defined and hence need a fixup:
   */
  private ArrayList<AbstractFeature> _fixUpsF = new ArrayList<>();


  /**
   * Positions of fixups for features
   */
  private ArrayList<Integer> _fixUpsFAt = new ArrayList<>();


  /**
   * Feature offsets in this file
   */
  private Map<AbstractFeature, Integer> _offsetsForFeature = new TreeMap<>();


  /**
   * Type offsets in this file
   */
  private Map<AbstractType, Integer> _offsetsForType = new TreeMap<>();


  /**
   * SourcePositions that need fixup.
   */
  private ArrayList<SourcePosition> _fixUpsSourcePositions = new ArrayList<>();


  /**
   * offsets of SourcePositions that need fixup.
   */
  private ArrayList<Integer> _fixUpsSourcePositionsAt = new ArrayList<>();


  /**
   * source file position offsets in this file.
   */
  private Map<String, Integer> _sourceFilePositions = new TreeMap<>();


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor to write library for given SourceModule.
   */
  FixUps()
  {
    super();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Add current offset as the offset for feature f.
   *
   * @param f the feature that will be written immediately after this call.
   */
  void add(Feature f)
  {
    _offsetsForFeature.put(f, offset());
  }


  /**
   * get the number of features written.
   */
  int featureCount()
  {
    return _offsetsForFeature.size();
  }


  /**
   * Write offset of given feature, create fixup if not known yet.
   */
  void writeOffset(AbstractFeature f)
  {
    int v;
    if (f.isUniverse())
      {
        v = 0;
      }
    else if (f == null)
      {
        v = -1;
      }
    else
      {
        var o = _offsetsForFeature.get(f);
        if (o == null)
          {
            _fixUpsF.add(f);
            _fixUpsFAt.add(offset());
            if (f instanceof LibraryFeature lf)
              {
                _referencedModules.put(lf._libModule, -1);
              }
            v = -1;
          }
        else
          {
            v = (int) o;
          }
      }
    writeInt(v);
  }


  /*
   * Add fixup for source code position 'pos' at current offset.
   */
  void addSourcePosition(SourcePosition pos)
  {
    _fixUpsSourcePositions.add(pos);
    _fixUpsSourcePositionsAt.add(offset());
  }


  /*
   * Add current offset as position for given source file
   */
  void addSourceFilePosition(String n)
  {
    _sourceFilePositions.put(n, offset());
  }


  /**
   * Perform fixups
   */
  void fixUps(LibraryOut lo)
  {
    for (var i = 0; i<_fixUpsF.size(); i++)
      {
        var g  = _fixUpsF  .get(i);
        var at = _fixUpsFAt.get(i);
        writeIntAt(at, offsetOfFeature(g));
      }
    for (var i = 0; i<_fixUpsSourcePositions.size(); i++)
      {
        var p  = _fixUpsSourcePositions  .get(i);
        var at = _fixUpsSourcePositionsAt.get(i);
        var sf = p._sourceFile;
        var n = lo.fileName(sf);
        var o = _sourceFilePositions.get(n) + p.bytePos();
        if (CHECKS) check
          (o > 0);
        writeIntAt(at, o);
      }
  }


  /**
   * Get the offset to be written for a given Feature.
   *
   * If g is an instance of Feature, i.e., it is part of the AST created from
   * source code, the offset will be the offset in the currently written module
   * file.
   *
   * Otherwise, g is an instance of LibraryFeature and and the result is the
   * offset of g in its module file plus that module's base.
   *
   * @param g a feature referenced within the written module.
   */
  int offsetOfFeature(AbstractFeature g)
  {
    if (g instanceof LibraryFeature gl)
      {
        var bi = moduleBase(gl._libModule);
        return bi + gl._index;
      }
    else
      {
        var o = _offsetsForFeature.get(g);
        if (CHECKS) check
          (o != null);
        return o;
      }
  }


  /**
   * Get the base offset of a given module.
   *
   * The base offset of the first module is the size of this module file.  The
   * base offset of every subsequence module is that previous module's base
   * offset plus the previous' modules size.
   *
   * @param m a module referenced within the written module.
   */
  int moduleBase(LibraryModule m)
  {
    var i = _referencedModules.get(m);
    if (i == -1)
      {
        var o = offset();
        for (var rm : _referencedModules.keySet())
          {
            _referencedModules.put(rm, o);
            int size = rm.data().limit();
            o = o + size;
          }
        i = _referencedModules.get(m);
      }
    return i;
  }


  /**
   * Record offset as the offset of type t.
   *
   * @param t a type that was or will be written out
   *
   * @param offset of t in the offset in the .fum/MIR file
   */
  void addOffset(AbstractType t, int offset)
  {
    if (PRECONDITIONS) require
      (offset(t) == -1);

    _offsetsForType.put(t, offset);
  }


  /**
   * Get the offset that was previously recored for type t, or -1 if no offset
   * was record (i.e., t has not been written yet).
   */
  int offset(AbstractType t)
  {
    return _offsetsForType.getOrDefault(t, -1);
  }


  /**
   * get the number of types added
   */
  int typeCount()
  {
    return _offsetsForType.size();
  }


  /**
   * Get sorted list of all the library modules that that are referenced by any
   * features written for this module.
   */
  List<LibraryModule> referencedModules()
  {
    var l = new List<LibraryModule>();
    l.addAll(_referencedModules.keySet());
    return l;
  }

}

/* end of file */
