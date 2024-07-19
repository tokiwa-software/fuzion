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
 * Source of class SysArray
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;

import dev.flang.fuir.FUIR.SpecialClazzes;
import dev.flang.util.Errors;


/**
 * Instance represents the result of fuzion.sys.array.alloc
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class SysArray extends Value implements Comparable<SysArray>
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The DFA instance we are working with.
   */
  DFA _dfa;


  /**
   * The data stored in the array (in case this is a compile time constant).
   */
  byte[] _data;


  /**
   * Value of the array elements
   */
  Value _elements = null;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create SysArray instance
   *
   * @param dfa the DFA analysis
   *
   * @param data the data stored in this array (in case this is a compile time constant).
   *
   * @param elementClazz clazz of the array elements, may be -1 if data[] is empty
   */
  public SysArray(DFA dfa, byte[] data, int elementClazz)
  {
    super(dfa._fuir.clazzAny());

    if (PRECONDITIONS) require
      (data != null);

    _dfa = dfa;
    _data = data;
    if (true)
      {
        if (data.length > 0)
          {
            _elements = switch (dfa._fuir.getSpecialClazz(elementClazz))
              {
              case
                c_i8   , c_i16  ,
                c_i32  , c_i64  ,
                c_u8   , c_u16  ,
                c_u32  , c_u64  ,
                c_f32  , c_f64  -> NumericValue.create(dfa, elementClazz); // NYI: any value, even if we could know the exact values from data
              default           -> {
                                     Errors.fatal("Constant array of element type "+dfa._fuir.clazzAsString(elementClazz)+" not supported yet");
                                     yield null;
                                   }
              };
          }
      }
    else
      { // NYI: accurate sys array element tracking does not work yet.  This
        // needs to be specialized for elementClazz, the following code is just
        // for u8 and probably does not work:
        for (var i = 0; i < data.length; i++)
          {
            setel(null, NumericValue.create(dfa, dfa._fuir.clazz(SpecialClazzes.c_u8), data[i] & 0xff));
          }
      }
  }

  /**
   * Create SysArray instance
   *
   * @param dfa the DFA analysis
   *
   * @param el the element values.
   */
  public SysArray(DFA dfa, Value el)
  {
    super(dfa._fuir.clazzAny());

    _dfa = dfa;
    _data = new byte[0];
    _elements = el;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Add el to the set of values of elements at index.
   */
  void setel(Value index, Value el)
  {
    Value ne;
    if (_elements == null)
      {
        ne = el;
      }
    else
      {
        ne = _elements.join(el);
      }
    if (_elements == null || Value.compare(_elements, ne) != 0)
      {
        _dfa.wasChanged(() -> "elements of SysArray changed: " + _elements + " =>" + ne);
        _elements = ne;
      }
  }


  /**
   * Get set of values of elements at index index.
   */
  Value get(Value index)
  {
    return _elements;
  }


  /**
   * Compare this to another instance.
   */
  public int compareTo(SysArray other)
  {
    var r =
      _data.length < other._data.length ? -1 :
      _data.length > other._data.length ? +1 : 0;
    for (var i = 0; r == 0 && i < _data.length; i++)
      {
        r =
          _data[i] < other._data[i] ? -1 :
          _data[i] > other._data[i] ? +1 : 0;
      }
    if (r == 0)
      {
        r = (_elements == null && other._elements == null) ?  0 :
            (_elements == null && other._elements != null) ? -1 :
            (_elements != null && other._elements == null) ? +1
                                                           : Value.compare(_elements, other._elements);
      }
    return r;
  }


  /**
   * Create human-readable string from this instance.
   */
  public String toString()
  {
    return "--sys array of length " + _data.length + "--";
  }


  /**
   * Create the union of the values 'this' and 'v'. This is called by join()
   * after common cases (same instance, UNDEFINED) have been handled.
   */
  public Value joinInstances(Value v)
  {
    if (v instanceof SysArray sv)
      {
        Value ne =
          _elements == null ? sv._elements :
          sv._elements == null ? _elements : _elements.join(sv._elements);
        return new SysArray(_dfa, ne);
      }
    else
      {
        return new ValueSet(this, v);
      }
  }


}

/* end of file */
