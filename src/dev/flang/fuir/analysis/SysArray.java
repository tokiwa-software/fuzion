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

package dev.flang.fuir.analysis;

import dev.flang.fuir.FUIR;


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


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create SysArray instance
   *
   * @param dfa the DFA analysis
   *
   * @param data the data stored in this array (in case this is a compile time constant).
   */
  public SysArray(DFA dfa, byte[] data)
  {
    if (PRECONDITIONS) require
      (data != null);

    _dfa = dfa;
    _data = data;
  }


  /*-----------------------------  methods  -----------------------------*/


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
   * Create the union of the values 'this' and 'v'.
   */
  public Value join(Value v)
  {
    return
      this == v ||
      v instanceof SysArray vs && compareTo(vs) == 0 ? this : super.join(v);
  }


}

/* end of file */
