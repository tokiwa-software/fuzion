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


  /**
   * The DFA instance we are working with.
   */
  final DFA _dfa;


  /**
   * CallGroup this call is part of, i.e., the set of calls with the same effect
   * environment abstraction during DFA analysis.
   */
  final CallGroup _group;


  /**
   * A example of a call site. This is more specific than _group._site since one
   * CallGroup may represent several Calls from different sites.
   */
  int _site;


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
   *
   * @param site the call site
   */
  public Call(CallGroup group,  List<Val> args, Env env, Context context, int site)
  {
    _group = group;
    _dfa = group._dfa;
    _args = args;
    _env = env;
    _context = context;
    _site = site;

    if (_dfa._fuir.isConstructor(calledClazz()))
      {
        /* a constructor call returns current as result, so it always escapes together with all outer references! */
        _dfa.escapes(calledClazz());
        var or = _dfa._fuir.clazzOuterRef(calledClazz());
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
   * Compare the environments of this Call with that of other, taking into
   * account only the actually required effects.
   *
   * @param other another call
   *
   * @return the result of the version of Env.compare() that was used, -1, 0, or
   * +1.
   */
  int envCompare(Call other)
  {
    return DFA.TRACE_ALL_EFFECT_ENVS
      ? Env.compare(env(), other.env())
      : Env.compare(_dfa._real
                    ? _dfa._effectsRequiredByClazz.get(calledClazz())
                    : _group._usedEffects,
                    env(), other.env());
  }


  /**
   * Compare this to another Call.
   */
  public int compareTo(Call other)
  {
    var res = _group.compareTo(other._group);
    if (res == 0 && _dfa._real)
      {
        res = envCompare(other);
      }
    return res;
  }


  /**
   * For debugging: Why did {@code compareTo(other)} return a value != 0?
   */
  String compareToWhy(Call other)
  {
    return
      calledClazz() != other.calledClazz() ? "cc different" :
      target()._id  != other.target()._id  ? "target different" :
      site()        != other.site()        ? "site different" :
      envCompare(other) != 0               ? "env different" + " env1: "+env()+ " env2: "+other.env() +
                                             " used " + _group.usedEffectsAsString() +
                                             " req " + _group.requiredEffectsAsString()
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
        var an = a0.joinVal(_dfa, a1, _dfa._fuir.clazzArgClazz(calledClazz(), i));
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
    if (_dfa._fuir.clazzKind(calledClazz()) == IR.FeatureKind.Intrinsic)
      {
        var name = _dfa._fuir.clazzOriginalName(calledClazz());
        var idfa = DFA._intrinsics_.get(name);
        if (idfa != null)
          {
            result = DFA._intrinsics_.get(name).analyze(this);
          }
        else
          {
            var msg = "DFA: code to handle intrinsic '" + name + "' is missing";
            Errors.warning(msg);
            result = genericResult();
          }
      }
    else if (_dfa._fuir.clazzKind(calledClazz()) == IR.FeatureKind.Native)
      {
        markSysArrayArgsAsInitialized();
        markFunctionArgsAsCalled();
        markArrayArgsAsRead();

        result = genericResult();
        if (result == null)
          {
            var rc = _dfa._fuir.clazzResultClazz(calledClazz());
            Errors.warning("DFA: cannot handle native feature result type: " + _dfa._fuir.clazzOriginalName(rc));
          }
      }
    else if (_returns)
      {
        var rf = _dfa._fuir.clazzResultField(calledClazz());
        if (_dfa._fuir.isConstructor(calledClazz()))
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
              (!_dfa._fuir.clazzIsVoidType(_dfa._fuir.clazzResultClazz(calledClazz())));

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
    for (int i = 0; i < _dfa._fuir.clazzArgCount(calledClazz()); i++)
      {
        _dfa.readField(_dfa._fuir.clazzArg(calledClazz(), i));

        var at = _dfa._fuir.clazzArgClazz(calledClazz(), i);
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
    for (int i = 0; i < _dfa._fuir.clazzArgCount(calledClazz()); i++)
      {
        _dfa.readField(_dfa._fuir.clazzArg(calledClazz(), i));

        var call = _dfa._fuir.lookupCall(_dfa._fuir.clazzArgClazz(calledClazz(), i));
        if (call != NO_CLAZZ)
          {
            var args = new List<Val>();
            for (int j = 0; j < _dfa._fuir.clazzArgCount(call); j++)
              {
                args.add(_dfa.newInstance(_dfa._fuir.clazzArgClazz(call, j), FUIR.NO_SITE, _context));
              }
            var ignore = _dfa
              .newCall(this, call, FUIR.NO_SITE, this._args.get(i).value(), args, _env /* NYI: UNDER DEVELOPMENT: assumption  here is that callback is not used after this call completes */, _context)
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
                     _dfa.newInstance(sa._elementClazz, site(), _context));
          }
      }
  }


  /**
   * create a generic result for this call.
   * used for results of native and not implemented intrinsics.
   */
  private Val genericResult()
  {
    var rc = _dfa._fuir.clazzResultClazz(calledClazz());
    return _dfa._fuir.clazzIsVoidType(rc)
      ? null
      : _dfa.newInstance(rc, site(), _context);
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
        sb.append(_dfa._fuir.clazzAsString(calledClazz()));
        if (target() != Value.UNIT)
          {
            sb.append(" target=")
              .append(target());
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
    var on = _dfa._fuir.clazzOriginalName(calledClazz());
    var pos = callSitePos();
    return
      (forEnv
       ? (on.equals(EFFECT_INSTATE_NAME)
          ? "install effect " + Errors.effe(_dfa._fuir.clazzAsStringHuman(_dfa._fuir.effectTypeFromIntrinsic(calledClazz()))) + ", old environment was "
          : "effect environment ") +
         Errors.effe(Env.envAsString(env())) +
         " for call to "
       : "call ")+
      Errors.sqn(_dfa._fuir.clazzAsStringHuman(calledClazz())) +
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
   * Target value of the call
   */
  Value target()
  {
    return _group._target;
  }


  /**
   * return the called clazz index.
   */
  int calledClazz()
  {
    return _group._cc;
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
   * Report an error if no effect found during last pass (i.e.,
   * _dfa._reportResults is set).
   *
   * @param s the site that requires this effect, for error message in case
   * _dfa._reportResults.
   *
   * @param ecl clazz defining the effect type.
   *
   * @param ignoreError true if error reporting for missing effect during
   * _dfa._real phase should be suppressed.
   *
   * @return the effect value or null in case no effect of type ecl was found (yet)
   */
  Value getEffect(int s, int ecl, boolean ignoreError)
  {
    Value result;
    if (_dfa._real)
      {
        result = _env != null ? _env.getActualEffectValues(ecl)
                              : _dfa._defaultEffects.get(ecl);
        if (result == null && DFA.DO_NOT_TRACE_ENVS)
          {
            result = _dfa._allValuesForEnv.get(ecl);
          }
        if (result == null && _dfa._reportResults && !ignoreError)
          {
            DfaErrors.usedEffectNotInstalled(_dfa._fuir.sitePos(s),
                                             _dfa._fuir.clazzAsString(ecl),
                                             this);
            _dfa._missingEffects.put(ecl, ecl);
          }
      }
    else
      {
        result = _dfa._preEffectValues.get(ecl);
      }
    return result;
  }


  /**
   * Mark effect of type `ecl` used and return its values via {@link getEffect}.
   *
   * @param s the site that requires this effect, for error message in case
   * _dfa._reportResults.
   *
   * @param ecl clazz defining the effect type.
   *
   * @param ignoreError true if error reporting for missing effect during
   * _dfa._real phase should be suppressed.
   *
   * @return the effect value or null in case no effect of type ecl was found (yet)
   */
  Value useAndGetEffect(int s, int ecl, boolean ignoreError)
  {
    _group.usesEffect(ecl);
    return getEffect(s, ecl, ignoreError);
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
    // make sure it is known that effect ecl is required here:
    _group.usesEffect(ecl);

    if ((_env == null || !_env.hasEffect(ecl)) && _dfa._defaultEffects.get(ecl) == null)
      {
        if (_dfa._reportResults && !DFA.DO_NOT_TRACE_ENVS)
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
      (_dfa._fuir.clazzKind(calledClazz()) == FUIR.FeatureKind.Routine);

    if (!_escapes)
      {
        _escapes = true;
        // we currently store for calledClazz(), so we accumulate different call
        // contexts to the same clazz. We might make this more detailed and
        // record this local to the call or use part of the call's context like
        // the target value to be more accurate.
        _dfa.escapes(calledClazz());
      }
  }


}

/* end of file */
