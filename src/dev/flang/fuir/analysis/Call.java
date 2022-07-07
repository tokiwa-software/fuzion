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
 * Source of class Instance
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis;

import java.util.TreeSet;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;


/**
 * Call represents a call
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Call implements Comparable<Call>
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The DFA instance we are working with.
   */
  DFA _dfa;


  /**
   * The clazz this is an instance of.
   */
  int _cc;

  /**
   * Is this a call to _cc's precondition?
   */
  boolean _pre;


  /**
   * Arguments passed to the call.
   */
  List<Value> _args;


  /**
   * 'this' instance created by this call.
   */
  Instance _instance;


  /**
   * result value returned from this call.
   */
  Value _result;


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create Call
   *
   * @param cc called clazz
   *
   * @param pre true if calling precondition
   */
  public Call(DFA dfa, int cc, boolean pre, List<Value> args)
  {
    _dfa = dfa;
    _cc = cc;
    _pre = pre;
    _args = args;
    _instance = dfa.newInstance(cc);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Compare this to another Call.
   */
  public int compareTo(Call other)
  {
    var r =
      _cc   <   other._cc  ? -1 :
      _cc   >   other._cc  ? +1 :
      _pre  && !other._pre ? -1 :
      !_pre &&  other._pre ? +1 : 0;
    for (var i = 0; r == 0 && i < _args.size(); i++)
      {
        r = Value.compare(_args.get(i), other._args.get(i));
      }
    return r;
  }


  /**
   * Return the result value returned by this call.
   */
  public Value result()
  {
    if (_result == null)
      {
        var rf = _dfa._fuir.clazzResultField(_cc);
        if (_pre)
          {
            _result = Value.UNIT;
          }
        else if (rf == -1)
          {
            _result = _dfa.newInstance(_cc);
          }
        else
          {
            var rt = _dfa._fuir.clazzResultClazz(_cc);
            _result = _dfa._fuir.clazzIsVoidType(rt) ? null : _instance.readField(_cc, rf);
          }
      }
    return _result;
  }


  /**
   * Create human-readable string from this instance.
   */
  public String toString()
  {
    var sb = new StringBuilder();
    sb.append(_pre ? "precondition of " : "").append(_dfa._fuir.clazzAsString(_cc));
    for (var a : _args)
      {
        sb.append(" ").append(a);
      }
    return sb.toString();
  }

}

/* end of file */
