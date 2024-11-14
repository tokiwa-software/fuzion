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



/**
 * Instance represents the result of fuzion.sys.array.alloc
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class SysArray extends Value
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/

  static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

  /*----------------------------  variables  ----------------------------*/


  /**
   * The DFA instance we are working with.
   */
  DFA _dfa;


  /**
   * The type of the array elements
   */
  int _elementClazz;


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
   * @param el the element values, null if not initialized
   *
   * @param ec the type of the array elements
   */
  public SysArray(DFA dfa, Value el, int ec)
  {
    super(dfa._fuir.clazzAny());

    _dfa = dfa;
    _elements = el;
    _elementClazz = ec;
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
        ne = _elements.join(_dfa, el, _elementClazz);
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
   * Create human-readable string from this instance.
   */
  public String toString()
  {
    return "--sys array of type " + _dfa._fuir.clazzAsString(_elementClazz) + "--";
  }


  /**
   * Create the union of the values 'this' and 'v'. This is called by join()
   * after common cases (same instance, UNDEFINED) have been handled.
   *
   * @param dfa the current analysis context.
   *
   * @param v the value this value should be joined with.
   *
   * @param clazz the clazz of the resulting value. This is usually the same as
   * the clazz of `this` or `v`, unless we are joining `ref` type values.
   */
  @Override
  public Value joinInstances(DFA dfa, Value v, int cl)
  {
    if (v instanceof SysArray sv)
      {
        Value ne =
          _elements == null ? sv._elements :
          sv._elements == null ? _elements : _elements.join(dfa, sv._elements, _elementClazz);
        return _dfa.newSysArray(ne, _elementClazz);
      }
    else
      {
        throw new Error("DFA: trying to join SysArray with " + v.getClass());
      }
  }


}

/* end of file */
