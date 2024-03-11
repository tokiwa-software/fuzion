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
 * Source of class Call
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;


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
   * The clazz this is calling.
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
  List<Val> _args;


  /**
   * 'this' instance created by this call.
   */
  Value _instance;


  /**
   * true means that the call may return, false means the call has not been
   * found to return, i.e., the result is null (aka void).
   */
  boolean _returns = false;


  /**
   * The environment, i.e., the effects installed when this call is made.
   */
  final Env _env;


  /**
   * For debugging: Reason that causes this call to be part of the analysis.
   */
  Context _context;


  /**
   * True if this instance escapes this call.
   */
  boolean _escapes = false;


  /**
   * If available, _codeblockId and _codeBlockIndex give an example call that
   * resulted in creation of this Call.  This can be used to show the reason why
   * a given call is present in showWhy().  Both are -1 if no call site is known.
   */
  int _codeblockId = -1;
  int _codeblockIndex = -1;


  /*---------------------------  constructors  ---------------------------*/


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
   * @param args are the actual arguments passed to the call
   *
   * @param context for debugging: Reason that causes this call to be part of
   * the analysis.
   */
  public Call(DFA dfa, int cc, boolean pre, Value target, List<Val> args, Env env, Context context)
  {
    _dfa = dfa;
    _cc = cc;
    _pre = pre;
    _target = target;
    _args = args;
    _env = env;
    _context = context;
    _instance = dfa.newInstance(cc, this);

    if (!pre && dfa._fuir.clazzResultField(cc)==-1) /* <==> _fuir.isConstructor(cl) */
      {
        /* a constructor call returns current as result, so it always escapes together with all outer references! */
        dfa.escapes(cc, pre);
        var or = dfa._fuir.clazzOuterRef(cc);
        while (or != -1)
          {
            var orr = dfa._fuir.clazzResultClazz(or);
            dfa.escapes(orr,false);
            or = dfa._fuir.clazzOuterRef(orr);
          }
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
        r = Value.compare(      _args.get(i).value(),
                          other._args.get(i).value());
      }
    if (r == 0)
      {
        r = Env.compare(_env, other._env);
      }
    return r;
  }


  /**
   * Record the fact that this call returns, i.e., it does not necessarily diverge.
   */
  void returns()
  {
    if (!_returns)
      {
        _returns = true;
        _dfa.wasChanged(() -> "Call.returns for " + this);
      }
  }


  /**
   * Return the result value returned by this call.  null in case this call
   * never returns.
   */
  public Val result()
  {
    Val result = null;
    if (_dfa._fuir.clazzKind(_cc) == IR.FeatureKind.Intrinsic)
      {
        var name = _dfa._fuir.clazzIntrinsicName(_cc);
        var idfa = DFA._intrinsics_.get(name);
        if (idfa != null)
          {
            result = DFA._intrinsics_.get(name).analyze(this);
          }
        else
          {
            var at = _dfa._fuir.clazzTypeParameterActualType(_cc);
            if (at >= 0)
              {
                var rc = _dfa._fuir.clazzResultClazz(_cc);
                var t = _dfa.newInstance(rc, this);
                // NYI: DFA missing support for Type instance, need to set field t.name to tname.
                result = t;
              }
            else
              {
                var msg = "DFA: code to handle intrinsic '" + name + "' is missing";
                Errors.warning(msg);
                var rc = _dfa._fuir.clazzResultClazz(_cc);
                result = switch (_dfa._fuir.getSpecialClazz(rc))
                  {
                  case c_i8, c_i16, c_i32, c_i64,
                       c_u8, c_u16, c_u32, c_u64,
                       c_f32, c_f64              -> new NumericValue(_dfa, rc);
                  case c_bool                    -> _dfa._bool;
                  case c_TRUE, c_FALSE           -> Value.UNIT;
                  case c_Const_String, c_String  -> _dfa.newConstString(null, this);
                  case c_unit                    -> Value.UNIT;
                  case c_sys_ptr                 -> new Value(_cc); // NYI: we might add a specific value for system pointers
                  case c_NOT_FOUND               -> null;
                  };
              }
          }
      }
    else if (_dfa._fuir.clazzKind(_cc) == IR.FeatureKind.Native)
      {
        var rc = _dfa._fuir.clazzResultClazz(_cc);
        result = switch (_dfa._fuir.getSpecialClazz(rc))
          {
            case c_i8, c_i16, c_i32, c_i64,
                 c_u8, c_u16, c_u32, c_u64,
                 c_f32, c_f64              -> new NumericValue(_dfa, rc);
            case c_Const_String, c_String  -> _dfa.newConstString(null, this);
            default                        -> { Errors.warning("DFA: cannot handle native feature " + _dfa._fuir.clazzIntrinsicName(_cc));
                                                yield null; }
          };
      }
    else if (_returns)
      {
        var rf = _dfa._fuir.clazzResultField(_cc);
        if (_pre)
          {
            result = Value.UNIT;
          }
        else if (rf == -1)
          {
            result = _instance;
          }
        else if (FUIR.SpecialClazzes.c_unit == _dfa._fuir.getSpecialClazz(_dfa._fuir.clazzResultClazz(rf)))
          {
            result = Value.UNIT;
          }
        else
          {
            // should not be possible to return void (_result should be null):
            if (CHECKS) check
              (!_dfa._fuir.clazzIsVoidType(_dfa._fuir.clazzResultClazz(_cc)));

            result = _instance.readField(_dfa, rf);
          }
      }
    return result;
  }


  /**
   * Create human-readable string from this call.
   */
  public String toString()
  {
    var sb = new StringBuilder();
    sb.append(_pre ? "precondition of " : "")
      .append(_dfa._fuir.clazzAsString(_cc));
    if (_target != Value.UNIT)
      {
        sb.append(" target=")
          .append(_target);
      }
    for (var i = 0; i < _args.size(); i++)
      {
        var a = _args.get(i);
        sb.append(" a")
          .append(i)
          .append("=")
          .append(a);
      }
    var r = result();
    sb.append(" => ")
      .append(r == null ? "*** VOID ***" : r);
    if (_env != null)
      {
        sb.append(_env.toString());
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
    if (_codeblockId != -1 && _codeblockIndex != -1)
      {
        System.out.println(_dfa._fuir.codeAtAsPos(_codeblockId,_codeblockIndex).show());
      }
    return indent + "  ";
  }


  /**
   * record code block id c and code block index i as a sample call site that
   * lead to the creation of this Call. Used by showWhy().
   */
  void addCallSiteLocation(int c, int i)
  {
    _codeblockId = c;
    _codeblockIndex = i;
  }


  /**
   * Get effect of given type in this call's environment or the default if none
   * found.
   *
   * @param ecl clazz defining the effect type.
   *
   * @return null in case no effect of type ecl was found
   */
  Value getEffect(int ecl)
  {
    return
      _env != null ? _env.getEffect(ecl)
                   : _dfa._defaultEffects.get(ecl);
  }


  /**
   * Replace effect of given type with a new value.
   *
   * NYI: This currently modifies the effect and hence the call. We should check
   * how this could be avoided or handled better.
   *
   * @param ecl clazz defining the effect type.
   *
   * @param e new instance of this effect
   */
  void replaceEffect(int ecl, Value e)
  {
    if (_env != null)
      {
        _env.replaceEffect(ecl, e);
      }
    else
      {
        _dfa.replaceDefaultEffect(ecl, e);
      }
  }


  /**
   * Record that the instance of this call escapes, i.e., it might be accessed
   * after the call returned and cannot be allocated on the stack.
   */
  void escapes()
  {
    if (PRECONDITIONS) require
      (_dfa._fuir.clazzKind(_cc) == FUIR.FeatureKind.Routine);

    if (!_escapes)
      {
        _escapes = true;
        // we currently store for _cc/_pre, so we accumulate different call
        // contexts to the same clazz. We might make this more detailed and
        // record this local to the call or use part of the call's context like
        // the target value to be more accurate.
        _dfa.escapes(_cc, _pre);
      }
  }

  boolean _outerEscapes = false;


  void outerDoesEscape()
  {
    if (!_outerEscapes)
      {
        _outerEscapes = true;
        _dfa.wasChanged(() -> "outerDoesEscape for " + this);
      }
  }

  boolean outerEscapes()
  {
    return _outerEscapes;
  }



  boolean outerRefValuesContain(Val v)
  {
    var or = _dfa._fuir.clazzOuterRef(_cc);
    if (or != -1 &&
        _dfa._fuir.clazzFieldIsAdrOfValue(or) &&     // outer ref is adr, otherwise target is passed by value (primitive type like u32)
        v.value()._clazz == _dfa._fuir.clazzResultClazz(or))
      {
        return true;
      }
    return false;
  }


}

/* end of file */
