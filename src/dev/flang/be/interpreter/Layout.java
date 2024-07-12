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

import dev.flang.fuir.FUIR;


/**
 * Layout performs the instance layout for a given Clazz.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class Layout extends FUIRContext
{


  /*-----------------------------  statics  -----------------------------*/


  private static final TreeMap<Integer, Layout> _layouts_ = new TreeMap<>();


  /**
   * Determine the size of an instance of the given clazz.
   */
  static synchronized Layout get(int cl)
  {
    Layout result = _layouts_.get(cl);
    if (result == null)
      {
        if (fuir().clazzIsRef(cl))
          {
            result = get(fuir().clazzAsValue(cl));
          }
        else
          {
            result = new Layout(cl);
            _layouts_.put(cl, result);
          }
      }
    return result;
  }


  /*----------------------------  constants  ----------------------------*/


  /**
   * The Clazz we are layouting
   */
  private final int _clazz;


  /**
   * Offsets of the fields in instances of this clazz.
   */
  public final Map<Integer, Integer> _offsets = new TreeMap<>();


  /*----------------------------  variables  ----------------------------*/


  /**
   * The size of the clazz, -1 if layout has not started yet, <-1 if layout is
   * in progress, Integer.MIN_VALUE if layout is done but clazz cannot be
   * instantiated.
   */
  private final int _size;


  /**
   * The size of the choice values in case fuir.clazzIsChoice(_clazz). -1 if layout has
   * not started yet.
   */
  private int _choiceValsSize = -1;


  /*---------------------------  constructors  ---------------------------*/


  Layout(int cl)
  {
    _clazz = cl;

    var size = Integer.MIN_VALUE;
    if (fuir().clazzIsChoice(_clazz))
      {
        // reserved for tagging
        size += (fuir().clazzIsChoiceOfOnlyRefs(_clazz) ? 0 : 1);
        int maxSz = 0;
        for (int i = 0; i < fuir().clazzNumChoices(cl); i++)
          {
            var cg = fuir().clazzChoice(cl, i);
            var sz = fuir().clazzIsRef(cg) ? 1 : get(cg).size();
            if (sz > maxSz)
              {
                maxSz = sz;
              }
          }
        _choiceValsSize = maxSz;
        size = size + maxSz;
        size -= Integer.MIN_VALUE;
      }
    else if (fuir().clazzIsRoutine(_clazz))
      {
        for (int i = 0; i < fuir().clazzNumFields(cl); i++)
          {
            var f = fuir().clazzField(cl, i);
            // NYI: Ugly special handling, clean up:
            int fc = fuir().clazzFieldIsAdrOfValue(f)  ? fuir().clazzAddress()
                                                       : fuir().clazzResultClazz(f);
            int fsz;
            if        (fuir().clazzIsRef(fc)) {                         fsz = 1;
            } else if (fc == fuir().clazz(FUIR.SpecialClazzes.c_i8))  { fsz = 1;
            } else if (fc == fuir().clazz(FUIR.SpecialClazzes.c_i16)) { fsz = 1;
            } else if (fc == fuir().clazz(FUIR.SpecialClazzes.c_i32)) { fsz = 1;
            } else if (fc == fuir().clazz(FUIR.SpecialClazzes.c_i64)) { fsz = 2;
            } else if (fc == fuir().clazz(FUIR.SpecialClazzes.c_u8))  { fsz = 1;
            } else if (fc == fuir().clazz(FUIR.SpecialClazzes.c_u16)) { fsz = 1;
            } else if (fc == fuir().clazz(FUIR.SpecialClazzes.c_u32)) { fsz = 1;
            } else if (fc == fuir().clazz(FUIR.SpecialClazzes.c_u64)) { fsz = 2;
            } else if (fc == fuir().clazz(FUIR.SpecialClazzes.c_f32)) { fsz = 1;
            } else if (fc == fuir().clazz(FUIR.SpecialClazzes.c_f64)) { fsz = 2;
            } else if (fuir().clazzIsVoidType(cl))                    { fsz = 0;
            } else {                                                    fsz = get(fc).size(); }
            _offsets.put(i, size - Integer.MIN_VALUE);
            size += fsz;
          }
        size -= Integer.MIN_VALUE;
      }
    _size  = size;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The size of instances for _clazz.
   */
  int size()
  {
    return _size;
  }


  /**
   * Offset of field f within instances of _clazz.
   */
  int offset(int f)
  {
    if (PRECONDITIONS) require
      (fuir().clazzIsRoutine(_clazz) || fuir().clazzIsChoice(_clazz),
       _offsets.containsKey(fuir().fieldIndex(f))
       );

    return _offsets.get(fuir().fieldIndex(f));
  }


  /**
   * The offset at which the are of choice values starts.
   */
  int choiceValsOffset()
  {
    if (PRECONDITIONS) require
      (fuir().clazzIsChoice(_clazz));

    return fuir().clazzIsChoiceOfOnlyRefs(_clazz) ? 0 : 1;
  }


  /**
   * The size of values part of a choice
   */
  int choiceValsSize()
  {
    if (PRECONDITIONS) require
      (fuir().clazzIsChoice(_clazz));

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
      (fuir().clazzIsChoice(_clazz),
       id >= 0,
       id < fuir().clazzNumChoices(_clazz));

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
      (fuir().clazzIsChoice(_clazz));

    return choiceValsOffset();
  }


  @Override
  public String toString()
  {
    return fuir().clazzAsString(_clazz);
  }

}

/* end of file */
