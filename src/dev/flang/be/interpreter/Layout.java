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
 * Source of class Layout
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import java.util.Map;
import java.util.TreeMap;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Types;

import dev.flang.air.Clazz;

import dev.flang.util.ANY;


/**
 * Layout performs the instance layout for a given Clazz.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class Layout extends ANY
{


  /*-----------------------------  statics  -----------------------------*/


  /**
   * Determine the size of an instance of the given clazz.
   */
  static synchronized Layout get(Clazz c)
  {
    var l = (Layout) c._backendData; // _layouts_.get(c);
    if (l == null)
      {
        if (c.isRef())
          {
            l = get(c.asValue());
            c._backendData = l;
          }
        else
          {
            l = new Layout(c);
          }
      }
    return l;
  }


  /*----------------------------  variables  ----------------------------*/


  /**
   * The Clazz we are layouting
   */
  Clazz _clazz;


  /**
   * The size of the clazz, -1 if layout has not started yet, <-1 if layout is
   * in progress, Integer.MIN_VALUE if layout is done but clazz cannot be
   * instantiated.
   */
  int _size = -1;


  /**
   * The size of the choice values in case _clazz.isChoice(). -1 if layout has
   * not started yet.
   */
  int _choiceValsSize = -1;


  /**
   * Offsets of the fields in instances of this clazz.
   */
  Map<Clazz, Integer> _offsets = new TreeMap<>();


  /**
   * Offsets of the fields in instances of this clazz. This maps fields to
   * Integer offsets and open generic fields to int[] with offsets for all
   * select-variants.
   *
   * NYI: Remove, this should be replaced by _offsets.
   */
  Map<AbstractFeature, Object> _offsets0 = new TreeMap<>();


  /*---------------------------  consructors  ---------------------------*/


  Layout(Clazz cl)
  {
    if (PRECONDITIONS) require
      (cl != null);

    _clazz = cl;
    cl._backendData = this;

    _size = Integer.MIN_VALUE;
    if (_clazz.isChoice())
      {
        var tag = _clazz.choiceTag();
        if (tag != null)
          {
            _offsets.put(tag, _size - Integer.MIN_VALUE);
            _offsets0.put(tag.feature(), _size - Integer.MIN_VALUE);
            _size += get(tag.resultClazz()).size();
          }
        int maxSz = 0;
        for (var cg : _clazz.choiceGenerics())
          {
            var sz = cg.isRef() ? 1 : get(cg).size();
            if (sz > maxSz)
              {
                maxSz = sz;
              }
          }
        _choiceValsSize = maxSz;
        _size = _size + maxSz;
        _size -= Integer.MIN_VALUE;
      }
    else if (_clazz.isRoutine())
      {
        for (var f : _clazz.fields())
          {
            var ff = f.feature();
            // NYI: Ugly special handling, clean up:
            var fc =
              ff.isOuterRef() && ff.outer().isOuterRefAdrOfValue()  ? f.actualClazz(Types.t_ADDRESS) :
              ff.isOuterRef() && ff.outer().isOuterRefCopyOfValue() ? f._outer.actualClazz(ff.resultType(), f._select)
                                                                    : f.resultClazz();
            int fsz;
            if        (fc.isRef()) { fsz = 1;
            } else if (fc._type.compareTo(Types.resolved.t_i8    ) == 0) { fsz = 1;
            } else if (fc._type.compareTo(Types.resolved.t_i16   ) == 0) { fsz = 1;
            } else if (fc._type.compareTo(Types.resolved.t_i32   ) == 0) { fsz = 1;
            } else if (fc._type.compareTo(Types.resolved.t_i64   ) == 0) { fsz = 2;
            } else if (fc._type.compareTo(Types.resolved.t_u8    ) == 0) { fsz = 1;
            } else if (fc._type.compareTo(Types.resolved.t_u16   ) == 0) { fsz = 1;
            } else if (fc._type.compareTo(Types.resolved.t_u32   ) == 0) { fsz = 1;
            } else if (fc._type.compareTo(Types.resolved.t_u64   ) == 0) { fsz = 2;
            } else if (fc._type.compareTo(Types.resolved.t_f32   ) == 0) { fsz = 1;
            } else if (fc._type.compareTo(Types.resolved.t_f64   ) == 0) { fsz = 2;
            } else if (fc._type.compareTo(Types.resolved.t_void  ) == 0) { fsz = 0;
            } else {
              fsz = get(fc).size();
            }
            _offsets.put(f, _size - Integer.MIN_VALUE);
            if (f._select < 0)
              {
                _offsets0.put(f.feature(), _size - Integer.MIN_VALUE);
              }
            else
              {
                int[] a = (int[]) _offsets0.get(f.feature());
                if (a == null)
                  {
                    a = new int[_clazz.replaceOpen(f.feature().resultType()).size()];
                    _offsets0.put(f.feature(), a);
                  }
                a[f._select] = _size - Integer.MIN_VALUE;
              }
            _size += fsz;
          }
        _size -= Integer.MIN_VALUE;
      }

    if (POSTCONDITIONS) ensure
      (!_clazz.isChoice() && !_clazz.isRoutine() || sizeAvailable());
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Can size() be called?  Just for use in precondition.
   */
  boolean sizeAvailable()
  {
    if (PRECONDITIONS) require
      (_clazz.isChoice() || _clazz.isRoutine());

    return _size >= 0;
  }


  /**
   * The size of instances for _clazz.
   */
  int size()
  {
    if (PRECONDITIONS) require
      (sizeAvailable());

    return _size;
  }


  /**
   * Offset of field f within instances of _clazz.
   *
   * NYI: Remove, replace by offset(Clazz)
   */
  int offset0(AbstractFeature f, int select)
  {
    if (PRECONDITIONS) require
      (_clazz.isRoutine() || _clazz.isChoice(),
       f.resultType().isOpenGeneric() == (select >= 0),
       sizeAvailable());

    var o = _offsets0.get(f);
    var result = select < 0 ? ((Integer) o)
                            : ((int[]) o)[select];
    return result;
  }


  /**
   * Offset of field f within instances of _clazz.
   */
  int offset(Clazz f)
  {
    if (PRECONDITIONS) require
      (_clazz.isRoutine() || _clazz.isChoice(),
       sizeAvailable());

    return _offsets.get(f);
  }


  /**
   * The offset at which the are of choice values starts.
   */
  int choiceValsOffset()
  {
    if (PRECONDITIONS) require
      (_clazz.isChoice() && sizeAvailable());

    return _clazz.choiceTag() != null ? 1 : 0;
  }


  /**
   * The size of values part of a choice
   */
  int choiceValsSize()
  {
    if (PRECONDITIONS) require
      (_clazz.isChoice() && sizeAvailable());

    return _choiceValsSize;
  }


  /**
   * For choice clazz, this gives the offset of the memory reserved for choice
   * value with given id.
   *
   * Choice values might have different offsets depending on alignment
   * constraints or to avoid GC trouble by not mixing references with
   * non-references.
   *
   * @param id the slot id.
   */
  int choiceValOffset(int id)
  {
    if (PRECONDITIONS) require
      (_clazz.isChoice() && sizeAvailable(),
       id >= 0,
       id < _clazz.choiceGenerics().size());

    // id is ignored, all vals are currently stored at the same offset:
    return choiceValsOffset();
  }


  /**
   * For choice clazz, this gives the offset of the memory reserved for choice
   * vlues of reference type.  Unlike non-references, that might be layouted
   * differently according to alignment constraints or not mixing reference with
   * non-references to avoid GC trouble, values of reference type are always
   * using one single shared slot.
   */
  int choiceRefValOffset()
  {
    if (PRECONDITIONS) require
      (_clazz.isChoice() && sizeAvailable());

    return choiceValsOffset();
  }

}

/* end of file */
