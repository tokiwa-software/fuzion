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

import dev.flang.air.Clazz; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.air.Clazzes; // NYI: remove dependency! Use dev.flang.fuir instead.

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


  /*----------------------------  constants  ----------------------------*/


  /**
   * The Clazz we are layouting
   */
  private final Clazz _clazz;


  /**
   * Offsets of the fields in instances of this clazz.
   */
  public final Map<Clazz, Integer> _offsets = new TreeMap<>((o1, o2) -> o1.compareToIgnoreOuter(o2));


  /*----------------------------  variables  ----------------------------*/


  /**
   * The size of the clazz, -1 if layout has not started yet, <-1 if layout is
   * in progress, Integer.MIN_VALUE if layout is done but clazz cannot be
   * instantiated.
   */
  private int _size = -1;


  /**
   * The size of the choice values in case _clazz.isChoice(). -1 if layout has
   * not started yet.
   */
  private int _choiceValsSize = -1;


  /*---------------------------  constructors  ---------------------------*/


  Layout(Clazz cl)
  {
    if (PRECONDITIONS) require
      (cl != null);

    _clazz = cl;
    cl._backendData = this;

    _size = Integer.MIN_VALUE;
    if (_clazz.isChoice())
      {
        // reserved for tagging
        _size += (_clazz.isChoiceOfOnlyRefs() ? 0 : 1);
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
              ff.isOuterRef() && ff.outer().isOuterRefAdrOfValue()  ? Clazzes.c_address
                                                                    : f.resultClazz();
            int fsz;
            if        (fc.isRef()) { fsz = 1;
            } else if (Clazzes.i8.getIfCreated()     != null && fc.compareTo(Clazzes.i8.get()      ) == 0) { fsz = 1;
            } else if (Clazzes.i16.getIfCreated()    != null && fc.compareTo(Clazzes.i16.get()     ) == 0) { fsz = 1;
            } else if (Clazzes.i32.getIfCreated()    != null && fc.compareTo(Clazzes.i32.get()     ) == 0) { fsz = 1;
            } else if (Clazzes.i64.getIfCreated()    != null && fc.compareTo(Clazzes.i64.get()     ) == 0) { fsz = 2;
            } else if (Clazzes.u8.getIfCreated()     != null && fc.compareTo(Clazzes.u8.get()      ) == 0) { fsz = 1;
            } else if (Clazzes.u16.getIfCreated()    != null && fc.compareTo(Clazzes.u16.get()     ) == 0) { fsz = 1;
            } else if (Clazzes.u32.getIfCreated()    != null && fc.compareTo(Clazzes.u32.get()     ) == 0) { fsz = 1;
            } else if (Clazzes.u64.getIfCreated()    != null && fc.compareTo(Clazzes.u64.get()     ) == 0) { fsz = 2;
            } else if (Clazzes.f32.getIfCreated()    != null && fc.compareTo(Clazzes.f32.get()     ) == 0) { fsz = 1;
            } else if (Clazzes.f64.getIfCreated()    != null && fc.compareTo(Clazzes.f64.get()     ) == 0) { fsz = 2;
            } else if (Clazzes.c_void.getIfCreated() != null && fc.compareTo(Clazzes.c_void.get()  ) == 0) { fsz = 0;
            } else {
              fsz = get(fc).size();
            }
            _offsets.put(f, _size - Integer.MIN_VALUE);
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
   */
  int offset(Clazz f)
  {
    if (PRECONDITIONS) require
      (_clazz.isRoutine() || _clazz.isChoice(),
       sizeAvailable(),
       _offsets.containsKey(f));

    return _offsets.get(f);
  }


  /**
   * The offset at which the are of choice values starts.
   */
  int choiceValsOffset()
  {
    if (PRECONDITIONS) require
      (_clazz.isChoice() && sizeAvailable());

    return _clazz.isChoiceOfOnlyRefs() ? 0 : 1;
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
   * values of reference type.  Unlike non-references, that might be layouted
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
