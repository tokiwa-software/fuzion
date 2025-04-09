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
import dev.flang.fuir.SpecialClazzes;
import dev.flang.ir.IR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;

import static dev.flang.ir.IR.NO_CLAZZ;
import static dev.flang.util.FuzionConstants.EFFECT_INSTATE_NAME;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.List;

import java.util.TreeSet;


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
   * Unique id to identify this Call. This is used to avoid expensive comparison
   * for calls.
   */
  int _uniqueCallId = -1;

  final boolean _real;


  /**
   * The DFA instance we are working with.
   */
  final DFA _dfa;


  final CallGroup _group;

  /**
   * The clazz this is calling.
   */
  final int _cc;


  /**
   * If available, _site gives the call site of this Call as used in the IR.
   * Calls with different call sites are analysed separately, even if the
   * context and environment of the call is the same.
   *
   * IR.NO_SITE if the call site is not known, i.e., the call is coming from
   * intrinsic call or the main entry point.
   */
  final int _site;


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
   * Is this call scheduled to be analysed during the current DFA iteration?
   * This is used to re-schedule hot calls (those that are likely to change the
   * DFA state) if are not already scheduled to be analyzed.
   */
  boolean _scheduledForAnalysis = false;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create Call
   *
   * @param group the call group containing the clazz, site and target
   *
   * @param args are the actual arguments passed to the call
   *
   * @param context for debugging: Reason that causes this call to be part of
   * the analysis.
   */
  public Call(CallGroup group,  List<Val> args, Env env, Context context)
  {
    _real = group._dfa._real;
    _group = group;
    _dfa = group._dfa;
    _cc = group._cc;
    _site = group._site;
    _target = group._target;
    _args = args;
    _env = env;
    _context = context;

    if (_dfa._fuir.isConstructor(_cc))
      {
        /* a constructor call returns current as result, so it always escapes together with all outer references! */
        _dfa.escapes(_cc);
        var or = _dfa._fuir.clazzOuterRef(_cc);
        while (or != NO_CLAZZ)
          {
            var orr = _dfa._fuir.clazzResultClazz(or);
            _dfa.escapes(orr);
            or = _dfa._fuir.clazzOuterRef(orr);
          }
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Return a unique id for the call or main entry point context.
   */
  @Override
  public int uniqueCallId()
  {
    return _uniqueCallId;
  }


  /**
   * Compare this to another Call.
   */
  public int compareTo(Call other)
  {
    var res = _group.compareTo(other._group);
    if (res == 0 && _dfa._real)
      {
        if (DFA.COMPARE_ONLY_ENV_EFFECTS_THAT_ARE_NEEDED)
          {
            if (false)
              {
                var which = new TreeSet<Integer>();
                which.addAll(_group._usedEffects);
                which.addAll(other._group._usedEffects);
                res = Env.compare(which, env(), other.env());
              }
            else
              {
                res = Env.compare(_real
                                  ? _dfa._effectsRequiredByClazz.get(_cc)
                                  : _group._usedEffects,
                                  env(), other.env());
              }
          }
        else
          {
            res = Env.compare(env(), other.env());
          }
      }
    return res;
  }


  /**
   * For debugging: Why did {@code compareTo(other)} return a value != 0?
   */
  String compareToWhy(Call other)
  {
    return
      _cc         != other._cc            ? "cc different" :
      _target._id != other._target._id    ? "target different" :
      _site       != other._site          ? "site different" :
      Env.compare(_real
                  ? _dfa._effectsRequiredByClazz.get(_cc)
                  : _group._usedEffects,
                  env(), other.env())!= 0 ? "env different" + " env1: "+env()+ " env2: "+other.env() + " used "+_group.usedEffectsAsString() +" req "+_group.requiredEffectsAsString()
                                          : "not different";
  }


  /**
   * Merge argument values args into this call's argument values.
   *
   * In case this resulted in any change, mark this as hot to make sure it will
   * be (re-) analyzed in the current iteration.
   *
   * @param args the values to be merged into this Call's arguments
   */
  void mergeWith(List<Val> args)
  {
    for (var i = 0; i < _args.size(); i++)
      {
        var a0 = _args.get(i);
        var a1 =  args.get(i);
        var an = a0.joinVal(_dfa, a1, _dfa._fuir.clazzArgClazz(_cc, i));
        if (an.value() != a0.value())
          {
            _args.set(i, an);
            _dfa.hot(this);
          }
      }
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
        var name = _dfa._fuir.clazzOriginalName(_cc);
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
                var t = _dfa.newInstance(rc, _site, this);
                // NYI: DFA missing support for Type instance, need to set field t.name to tname.
                result = t;
              }
            else
              {
                var msg = "DFA: code to handle intrinsic '" + name + "' is missing";
                Errors.warning(msg);
                result = genericResult();
              }
          }
      }
    else if (_dfa._fuir.clazzKind(_cc) == IR.FeatureKind.Native)
      {
        markSysArrayArgsAsInitialized();
        markFunctionArgsAsCalled();
        markArrayArgsAsRead();

        result = genericResult();
        if (result == null)
          {
            var rc = _dfa._fuir.clazzResultClazz(_cc);
            Errors.warning("DFA: cannot handle native feature result type: " + _dfa._fuir.clazzOriginalName(rc));
          }
      }
    else if (_returns)
      {
        var rf = _dfa._fuir.clazzResultField(_cc);
        if (_dfa._fuir.isConstructor(_cc))
          {
            result = _instance;
          }
        else if (SpecialClazzes.c_unit == _dfa._fuir.getSpecialClazz(_dfa._fuir.clazzResultClazz(rf)))
          {
            result = Value.UNIT;
          }
        else
          {
            // should not be possible to return void (_result should be null):
            if (CHECKS) check
              (!_dfa._fuir.clazzIsVoidType(_dfa._fuir.clazzResultClazz(_cc)));

            result = _instance.readField(_dfa, rf, -1, this);
          }
      }
    return result;
  }


  /**
   * call all args that are Function
   */
  private void markArrayArgsAsRead()
  {
    for (int i = 0; i < _dfa._fuir.clazzArgCount(_cc); i++)
      {
        _dfa.readField(_dfa._fuir.clazzArg(_cc, i));

        var at = _dfa._fuir.clazzArgClazz(_cc, i);
        if (_dfa._fuir.clazzIsArray(at))
          {
            var ia = _dfa._fuir.lookup_array_internal_array(at);
            _dfa.readField(ia);
            _dfa.readField(_dfa._fuir.lookup_fuzion_sys_internal_array_data(_dfa._fuir.clazzResultClazz(ia)));
          }
      }
  }


  /**
   * call all args that are Function
   */
  private void markFunctionArgsAsCalled()
  {
    for (int i = 0; i < _dfa._fuir.clazzArgCount(_cc); i++)
      {
        _dfa.readField(_dfa._fuir.clazzArg(_cc, i));

        var call = _dfa._fuir.lookupCall(_dfa._fuir.clazzArgClazz(_cc, i));
        if (call != NO_CLAZZ)
          {
            var args = new List<Val>();
            for (int j = 0; j < _dfa._fuir.clazzArgCount(call); j++)
              {
                args.add(_dfa.newInstance(_dfa._fuir.clazzArgClazz(call, j), FUIR.NO_SITE, _context));
              }
            var ignore = _dfa
              .newCall(this, call, FUIR.NO_SITE, this._args.get(i).value(), args, null /* env */, _context)
              .result();
          }
      }
  }


  /*
   * sys array arguments might be written
   * to in the native features
   * so we fake that here
   */
  private void markSysArrayArgsAsInitialized()
  {
    for (var arg : _args)
      {
        if (arg.value() instanceof SysArray sa && sa._elements == null)
          {
            sa.setel(NumericValue.create(_dfa, _dfa._fuir.clazz(SpecialClazzes.c_i32)),
                     _dfa.newInstance(sa._elementClazz, _site, _context));
          }
      }
  }


  /**
   * create a generic result for this call.
   * used for results of native and not implemented intrinsics.
   */
  private Val genericResult()
  {
    var rc = _dfa._fuir.clazzResultClazz(_cc);
    return _dfa._fuir.clazzIsVoidType(rc)
      ? null
      : _dfa.newInstance(rc, _site, _context);
  }


  /**
   * toString() might end up in a complex recursion if it is used for careless
   * debug output, so we try to catch recursion and stop it.
   */
  static TreeSet<Call> _toStringRecursion_ = new TreeSet<>();


  /**
   * Create human-readable string from this call.
   */
  public String toString()
  {
    if (_toStringRecursion_.contains(this))
      {
        return "*** recursive Call.toString() ***";
      }
    else
      {
        _toStringRecursion_.add(this);
        var sb = new StringBuilder();
        sb.append(_dfa._fuir.clazzAsString(_cc));
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
        sb.append(" => ")
          .append(_returns ? "returns" : "*** VOID ***")
          .append(" ENV: ")
          .append(Errors.effe(Env.envAsString(env())));
        _toStringRecursion_.remove(this);
        return sb.toString();
      }
  }


  /**
   * Create human-readable string from this call with effect environment information
   */
  public String toString(boolean forEnv)
  {
    var on = _dfa._fuir.clazzOriginalName(_cc);
    var pos = callSitePos();
    return
      (forEnv
       ? (on.equals(EFFECT_INSTATE_NAME)
          ? "install effect " + Errors.effe(_dfa._fuir.clazzAsStringHuman(_dfa._fuir.effectTypeFromIntrinsic(_cc))) + ", old environment was "
          : "effect environment ") +
         Errors.effe(Env.envAsString(env())) +
       // " required " + _group._usedEffects.stream().map(e -> Errors.effe(_dfa._fuir.clazzAsStringHuman(e))).collect(java.util.stream.Collectors.joining(", ")) +
         " for call to "
       : "call ")+
      // "[" + Errors.sqn(_dfa._fuir.clazzAsString(_cc)) + "]" +
      Errors.sqn(_dfa._fuir.clazzAsStringHuman(_cc)) +
      // " used " + _group.usedEffectsAsString() +
      // " may have " + _group.mayHaveEffectsAsString() +
      (pos != null ? " at " + pos.pos().show() : "");
  }


  /**
   * Show the context that caused the inclusion of this call into the analysis.
   *
   * @param sb the context information will be appended to this StringBuilder.
   *
   * @return a string providing the indentation level for the caller in case of
   * nested contexts.  "  " is to be added to the result on each recursive call.
   */
  public String showWhy(StringBuilder sb)
  {
    var indent = _context.showWhy(sb);
    var pos = callSitePos();
    sb.append(indent).append("  |\n")
      .append(indent).append("  +- performs call ").append(this).append("\n")
      .append(pos != null ? pos.pos().show() + "\n"
                          : "");
    return indent + "  ";
  }


  /**
   * return the call site index, -1 if unknown.
   */
  int site()
  {
    return _site;
  }


  /**
   * If available, get a source code position of a call site that results in this call.
   *
   * @return The SourcePosition or null if not available
   */
  HasSourcePosition callSitePos()
  {
    var s = site();
    return s == IR.NO_SITE ? null
                           : _dfa._fuir.sitePos(s);
  }


  /**
   * Effect-environment in this call, null if none.
   */
  @Override
  public Env env()
  {
    return _env;
  }


  /**
   * Get effect of given type in this call's environment or the default if none
   * found or null if no effect in environment and also no default available.
   *
   * @param ecl clazz defining the effect type.
   *
   * @return null in case no effect of type ecl was found
   */
  Value getEffectCheck(int ecl)
  {
    _group.needsEffect(ecl);
    return
      _env != null ? _env.getActualEffectValues(ecl)
                   : _dfa._defaultEffects.get(ecl);
  }
  Value getEffectCheck2(int ecl)
  {
    Value result;
    if (_dfa._real)
      {
        result = getEffectCheck(ecl);
        if (result == null && _dfa._reportResults)
          {
            DfaErrors.usedEffectNotInstalled(_dfa._fuir.sitePos(_site),
                                             _dfa._fuir.clazzAsString(ecl),
                                             this);
            _dfa._missingEffects.put(ecl, ecl);
          }
      }
    else
      {
        result = getEffectCheck(ecl); // NYI: needed only for the side-effect
        result = _dfa._preEffectValues.get(ecl);
      }
    return result;
  }



  /**
   * Get effect of given type in this call's environment or the default if none
   * found or null if no effect in environment and also no default available.
   *
   * Report an error if no effect found during last pass (i.e.,
   * _dfa._reportResults is set).
   *
   * @param s site of the code requiring the effect
   *
   * @param ecl clazz defining the effect type.
   *
   * @return null in case no effect of type ecl was found
   */
  Value getEffectForce(int s, int ecl)
  {
    _group.needsEffect(ecl);
    var result = getEffectCheck(ecl);
    if (result == null && _dfa._reportResults && !_dfa._fuir.clazzOriginalName(_cc).equals("effect.type.unsafe_from_env"))
      {
        DfaErrors.usedEffectNotInstalled(_dfa._fuir.sitePos(s),
                                         _dfa._fuir.clazzAsString(ecl),
                                         this);
        _dfa._missingEffects.put(ecl, ecl);
      }
    return result;
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
    if (!_dfa._reportResults)
      {
        // make sure it is known that effect ecl is required here, but do not
        // report an error if it is not since we have our own error below:
        var ignore = getEffectCheck2(ecl);
      }

    if ((_env == null || !_env.hasEffect(ecl)) && _dfa._defaultEffects.get(ecl) == null)
      {
        if (_dfa._reportResults)
          {
            // NYI: Make this a normal error similar to DfaErrors.usedEffectnotinstalled:
            Errors.fatal("Trying to replace effect " + Errors.code(_dfa._fuir.clazzAsString(ecl))
                         + " that is not yet installed: \n" + toString(false) + "\n" + toString(true));
          }
      }
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
        // we currently store for _cc, so we accumulate different call
        // contexts to the same clazz. We might make this more detailed and
        // record this local to the call or use part of the call's context like
        // the target value to be more accurate.
        _dfa.escapes(_cc);
      }
  }


}

/* end of file */
