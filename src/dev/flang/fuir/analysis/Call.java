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

import dev.flang.ir.IR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;


/**
 * Call represents a call
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Call extends ANY implements Comparable<Call>, Context
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
   * Target value of the call
   */
  Value _target;


  /**
   * Arguments passed to the call.
   */
  List<Value> _args;



  /**
   * 'this' instance created by this call.
   */
  Instance _instance;


  /**
   * result value returned from this call.  A value of null means that this call
   * does not return at all, i.e., the call always diverges.
   *
   * A value of Value.UNDEFINED means that the call may return, but the value
   * still needs to be found.
   *
   * Any other value gives the result of the call.
   */
  Value _result;


  /**
   * For debugging: Reason that causes this call to be part of the analysis.
   */
  Context _context;


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create Call
   *
   * @param dfa the DFA instance we are analyzing with
   *
   * @param cc called clazz
   *
   * @param pre true if calling precondition
   *
   * @param target is the target value of the call
   *
   * @param args are the acutal arguments passed to the call
   *
   * @param context for debugging: Reason that causes this call to be part of
   * the analysis.
   */
  public Call(DFA dfa, int cc, boolean pre, Value target, List<Value> args, Context context)
  {
    _dfa = dfa;
    _cc = cc;
    _pre = pre;
    _target = target;
    _args = args;
    _context = context;
    _instance = dfa.newInstance(cc, this);
    if (_dfa._fuir.clazzKind(cc) == IR.FeatureKind.Intrinsic)
      {
        var name = _dfa._fuir.clazzIntrinsicName(_cc);
        _result = _dfa._intrinsics_.get(name).analyze(this);
      }
    else
      {
        _result = null;
      }
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
      !_pre &&  other._pre ? +1 : Value.compare(_target, other._target);
    for (var i = 0; r == 0 && i < _args.size(); i++)
      {
        r = Value.compare(_args.get(i), other._args.get(i));
      }
    return r;
  }


  /**
   * Record the fact that this call returns, i.e., it does not necessarily diverge.
   */
  void returns()
  {
    if (_result == null)
      {
        _result = Value.UNDEFINED;
        _dfa._changed = true;
      }
  }


  /**
   * Return the result value returned by this call.  null in case this call
   * never returns.
   */
  public Value result()
  {
    if (_result == Value.UNDEFINED)
      {
        var rf = _dfa._fuir.clazzResultField(_cc);
        if (_pre)
          {
            _result = Value.UNIT;
          }
        else if (rf == -1)
          {
            _result = _instance;
          }
        else
          {
            // should not be possible to return void (_result should be null):
            if (CHECKS) check
              (_dfa._fuir.clazzIsVoidType(_dfa._fuir.clazzResultClazz(_cc)));

            _result = _instance.readField(_dfa, _cc, rf);
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


  /**
   * Show the context that caused the inclusion of this call into the analysis.
   */
  public String showWhy()
  {
    var indent = _context.showWhy();
    System.out.println(indent + "  |");
    System.out.println(indent + "  +- performs call " + this);
    return indent + "  ";
  }


}

/* end of file */
