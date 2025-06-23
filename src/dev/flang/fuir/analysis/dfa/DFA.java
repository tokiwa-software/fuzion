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
 * Source of class DFA
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import java.util.function.Supplier;

import java.util.stream.Stream;

import dev.flang.fuir.DfaFUIR;
import dev.flang.fuir.FUIR;
import dev.flang.fuir.FUIR.LifeTime;
import dev.flang.fuir.GeneratingFUIR;
import dev.flang.fuir.SpecialClazzes;
import dev.flang.fuir.analysis.AbstractInterpreter2;
import dev.flang.ir.IR.ExprKind;
import dev.flang.ir.IR.FeatureKind;

import static dev.flang.ir.IR.NO_CLAZZ;
import static dev.flang.ir.IR.NO_SITE;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import static dev.flang.util.FuzionConstants.EFFECT_INSTATE_NAME;
import dev.flang.util.FuzionOptions;
import dev.flang.util.List;
import dev.flang.util.IntMap;
import dev.flang.util.LongMap;
import dev.flang.util.SourcePosition;


/**
 * DFA creates a data flow analysis based on the FUIR representation of a Fuzion
 * application.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class DFA extends ANY
{


  /*----------------------------  interfaces  ---------------------------*/


  /**
   * Functional interface to create intrinsics.
   */
  @FunctionalInterface
  interface IntrinsicDFA
  {
    Val analyze(Call c);
  }


  /*-----------------------------  classes  -----------------------------*/


  /**
   * Record match cases that were evaluated by the DFA.
   */
  public final Set<Long> _takenMatchCases = new TreeSet<>();


  /**
   * Statement processor used with AbstractInterpreter to perform DFA analysis
   */
  class Analyze extends AbstractInterpreter2.ProcessExpression<Val>
  {


    /**
     * The Call we are analyzing.
     */
    final Call _call;


    /**
     * Create processor for an abstract interpreter doing DFA analysis of the
     * given call's code.
     */
    Analyze(Call call)
    {
      _call = call;
    }



    /*
     * Produce the unit type value.  This is used as a placeholder
     * for the universe instance as well as for the instance 'unit'.
     */
    @Override
    public Val unitValue()
    {
      return Value.UNIT;
    }


    /**
     * Called before each statement is processed. May be used to, e.g., produce
     * tracing code for debugging or a comment.
     */
    @Override
    public void expressionHeader(int s)
    {
      if (_reportResults && _options.verbose(9))
        {
          say("DFA for "+_fuir.siteAsString(s)+" at "+s+": "+_fuir.codeAtAsString(s));
        }
    }


    /**
     * A comment, adds human readable information
     */
    @Override
    public void comment(String s)
    {
    }


    /**
     * no operation, like comment, but without giving any comment.
     */
    @Override
    public void nop()
    {
    }


    /**
     * Perform an assignment val to field f in instance rt
     *
     * @param s site of the expression causing this assignment
     *
     * @param f clazz id of the assigned field
     *
     * @param tvalue the target instance
     *
     * @param val the new value to be assigned to the field.
     */
    @Override
    public void assignStatic(int s, int f, Val tvalue, Val val)
    {
      tvalue.value().setField(DFA.this, f, val.value());
    }


    /**
     * Perform an assignment of avalue to a field in tvalue. The type of tvalue
     * might be dynamic (a reference). See FUIR.access*().
     *
     * @param s site of assignment
     *
     * @param tvalue the target instance
     *
     * @param avalue the new value to be assigned to the field.
     */
    @Override
    public void assign(int s, Val tvalue, Val avalue)
    {
      var res = access(s, tvalue, new List<>(avalue));
    }


    /**
     * In an access, check if the target of the access is a boxed value. If so,
     * unbox it.
     *
     * @param tvalue the target value of an access
     *
     * @param tt the type of the target value
     *
     * @param cc the called clazz
     *
     * @return tvalue in case tvalue does not need unboxing, or the unboxed
     * value if tt is boxed and the outer clazz of cc is a value type.
     */
    private Val unboxTarget(Val tvalue, int tt, int cc)
    {
      var cco = _fuir.clazzOuterClazz(cc);
      return _fuir.clazzIsRef(tt) && !_fuir.clazzIsRef(cco) ? tvalue.value().unbox(DFA.this, cco)
                                                            : tvalue;
    }


    /**
     * Perform a call of a feature with target instance tvalue with given
     * arguments.  The type of tvalue might be dynamic (a reference). See
     * FUIR.access*().
     *
     * Result.v0() may be null to indicate that code generation should stop here
     * (due to an error or tail recursion optimization).
     */
    @Override
    public Val call(int s, Val tvalue, List<Val> args)
    {
      var res = access(s, tvalue, args);
      DFA.this.site(s).recordResult(res == null);
      return res;
    }


    /**
     * Analyze an access (call or write) of a feature.
     *
     * @param s site of access, must be ExprKind.Assign or ExprKind.Call
     *
     * @param tvalue the target of this call, Value.UNIT if none.
     *
     * @param args the arguments of this call, or, in case of an assignment, a
     * list of one element containing value to be assigned.
     *
     * @return result value of the access
     */
    Val access(int s, Val tvalue, List<Val> args)
    {
      var resa = new Val[1];
      var tv = tvalue.value();
      tv.forAll(t -> resa[0] = accessSingleTarget(s, t, args, resa[0], tvalue));
      var res = resa[0];
      if (res != null &&
          tvalue instanceof EmbeddedValue &&
          !_fuir.clazzIsRef(_fuir.accessTargetClazz(s)) &&
          _fuir.clazzKind(_fuir.accessedClazz(s)) == FUIR.FeatureKind.Field)
        { // an embedded field in a value instance, so keep tvalue's
          // embedding. For chained embedded fields in value instances like
          // `t.f.g.h`, the embedding remains `t` for `f`, `g` and `h`.
          var resf = res;
          res = tvalue.rewrap(DFA.this, x -> resf.value());
        }
      return res;
    }


    /**
     * Analyze an access (call or write) of a feature for a target value that is
     * not a ValueSet.
     *
     * @param s site of access, must be ExprKind.Assign or ExprKind.Call
     *
     * @param tvalue the target of this call, Value.UNIT if none.  Must not be ValueSet.
     *
     * @param args the arguments of this call, or, in case of an assignment, a
     * list of one element containing value to be assigned.
     *
     * @param res in case an access is performed for multiple targets, this is
     * the result of the already processed targets, null otherwise.
     *
     * @param original_tvalue the original target of this call. This must be
     * unchanged, it is used for escape analysis to figure out if the call
     * causes an instance this is embedded in to escape.
     *
     * @return result value of the access joined with res in case res != null.
     */
    Val accessSingleTarget(int s, Value tvalue, List<Val> args, Val res, Val original_tvalue)
    {
      if (PRECONDITIONS) require
        (Errors.any() || tvalue != Value.UNIT || AbstractInterpreter2.clazzHasUnitValue(_fuir, _fuir.accessTargetClazz(s))
         // NYI: UNDER DEVELOPMENT: intrinsics create instances like
         // `fuzion.java.Array`. These intrinsics currently do not set the outer
         // refs correctly, so we handle them for now by just assuming they are
         // unit type values:
         || true
         ,

         !(tvalue instanceof ValueSet));
      var t_cl = tvalue == Value.UNIT ? _fuir.accessTargetClazz(s) : tvalue._clazz;
      var cc = _fuir.lookup(s, t_cl);
      if (cc != FUIR.NO_CLAZZ)
        {
          var r = access0(s, tvalue, args, cc, original_tvalue);
          if (r != null)
            {
              res = res == null ? r : res.joinVal(DFA.this, r, _fuir.clazzResultClazz(cc));
            }
        }
      else
        {
          var instantiatedAt = _calls.keySet().stream()
            .filter(c -> (c.calledClazz() == _fuir.clazzAsValue(t_cl) ||  // NYI: CLEANUP would be nice if c.calledClazz() would be a ref already, should have been boxed at some point
                          c.calledClazz() == t_cl                       ) && c.site() != NO_SITE)
            .map(c -> c.site())
            .findAny()
            .orElse(NO_SITE);
          _fuir.recordAbstractMissing(t_cl, _fuir.accessedClazz(s), instantiatedAt, _call.contextString(),
                                      s);
        }
      return res;
    }


    /**
     * Helper routine for access (above) to perform a static access (cal or write).
     */
    Val access0(int s, Val tvalue, List<Val> args, int cc, Val original_tvalue /* NYI: ugly */)
    {
      var cs = DFA.this.site(s);
      cs._accesses.add(cc);
      var isCall = _fuir.codeAt(s) == FUIR.ExprKind.Call;
      Val r;
      if (isCall)
        {
          r = call0(s, tvalue, args, cc, original_tvalue);
        }
      else
        {
          if (!_fuir.clazzIsUnitType(_fuir.clazzResultClazz(cc)))
            {
              if (_reportResults && _options.verbose(9))
                {
                  say("DFA for "+_fuir.siteAsString(s) + ": "+_fuir.codeAtAsString(s)+": " +
                                     tvalue + ".set("+_fuir.clazzAsString(cc)+") := " + args.get(0));
                }
              var v = args.get(0);
              tvalue.value().setField(DFA.this, cc, v.value());
              tempEscapes(s, v, cc);
            }
          r = Value.UNIT;
        }
      return r;
    }


    /**
     * Helper for call to handle non-dynamic call to cc
     *
     * @param s site of call
     *
     * @param tvalue
     *
     * @param args
     *
     * @param cc clazz that is called
     *
     * @return result values of the call
     */
    Val call0(int s, Val tvalue, List<Val> args, int cc, Val original_tvalue)
    {
      // in case we access the value in a boxed target, unbox it first:
      tvalue = unboxTarget(tvalue, _fuir.accessTargetClazz(s), cc);
      Val res = null;
      switch (_fuir.clazzKind(cc))
        {
        case Abstract :
          Errors.error("Call to abstract feature encountered.",
                       "Found call to  " + _fuir.clazzAsString(cc));
        case Routine  :
        case Intrinsic:
        case Native   :
          {
            if (_fuir.clazzNeedsCode(cc))
              {
                var ca = newCall(_call, cc, s, tvalue.value(), args, _call._env, _call);
                res = ca.result();
                if (_options.needsEscapeAnalysis() && res != null && res != Value.UNIT && !_fuir.clazzIsRef(_fuir.clazzResultClazz(cc)))
                  {
                    res = newEmbeddedValue(s, res.value());
                  }
                // check if target value of new call ca causes current _call's instance to escape.
                var or = _fuir.clazzOuterRef(cc);
                if (original_tvalue instanceof EmbeddedValue ev && ev._instance == _call._instance &&
                    _escapes.contains(ca.calledClazz()) &&
                    (or != NO_CLAZZ) &&
                    _fuir.clazzFieldIsAdrOfValue(or)    // outer ref is adr, otherwise target is passed by value (primitive type like u32)
                    )
                  {
                    _call.escapes();
                  }
                tempEscapes(s, original_tvalue, _fuir.clazzOuterRef(cc));
                if (_reportResults && _options.verbose(9))
                  {
                    say("DFA for " +_fuir.siteAsString(s) + ": "+_fuir.codeAtAsString(s)+": " + ca);
                  }
              }
            break;
          }
        case Field:
          {
            res = tvalue.value().callField(DFA.this, cc, s, _call);
            if (_reportResults && _options.verbose(9))
              {
                say("DFA for "+_fuir.siteAsString(s) + ": "+_fuir.codeAtAsString(s)+": " +
                                   tvalue + ".get(" + _fuir.clazzAsString(cc) + ") => " + res);
              }
            break;
          }
        default:       throw new Error("This should not happen: Unknown feature kind: " + _fuir.clazzKind(cc));
        }
      return res;
    }


    /**
     * For a given value v of value type vc create a boxed ref value of type rc.
     */
    @Override
    public Val box(int s, Val val, int vc, int rc)
    {
      var boxed = val.value().box(DFA.this, vc, rc, _call);
      return boxed;
    }


    /**
     * Get the current instance
     */
    @Override
    public Val current(int s)
    {
      return _call._instance;
    }


    /**
     * Get the outer instance
     */
    @Override
    public Val outer(int s)
    {
      return _call.target();
    }

    /**
     * Get the argument #i
     */
    @Override
    public Val arg(int s, int i)
    {
      return _call._args.get(i);
    }


    /**
     * Get a constant value of type constCl with given byte data d.
     */
    @Override
    public Val constData(int s, int constCl, byte[] d)
    {
      var r = switch (_fuir.getSpecialClazz(constCl))
        {
        case c_bool -> d[0] == 1 ? True() : False();
        case c_i8   ,
             c_i16  ,
             c_i32  ,
             c_i64  ,
             c_u8   ,
             c_u16  ,
             c_u32  ,
             c_u64  ,
             c_f32  ,
             c_f64  -> NumericValue.create(DFA.this, constCl, ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN));
        case c_String -> newConstString(Arrays.copyOfRange(d, 4, ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getInt()+4), _call);
        default ->
          {
            if (!_fuir.clazzIsChoice(constCl))
              {
                yield _fuir.clazzIsArray(constCl)
                  ? newArrayConst(s, constCl, _call, ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN))
                  : newValueConst(s, constCl, _call, ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN));
              }
            else
              {
                Errors.error("Unsupported constant in DFA analysis.",
                             "DFA cannot handle constant of clazz '" + _fuir.clazzAsString(constCl) + "' ");
                yield null;
              }
          }
        };
      return r;
    }


    /**
     * deserialize value constant of type {@code constCl} from {@code b}
     *
     * @param s the site of the constant
     *
     * @param constCl the constants clazz, e.g. {@code (tuple u32 codepoint)}
     *
     * @param context for debugging: Reason that causes this const string to be
     * part of the analysis.
     *
     * @param b the serialized data to be used when creating this constant
     *
     * @return an instance of {@code constCl} with fields initialized using the data from {@code b}.
     */
    private Value newValueConst(int s, int constCl, Context context, ByteBuffer b)
    {
      var result = newInstance(constCl, NO_SITE, context);
      var args = new List<Val>();
      for (int index = 0; index < _fuir.clazzArgCount(constCl); index++)
        {
          var f = _fuir.clazzArg(constCl, index);
          var fr = _fuir.clazzArgClazz(constCl, index);
          var bytes = _fuir.deserializeConst(fr, b);
          var arg = constData(s, fr, bytes).value();
          args.add(arg);
          result.setField(DFA.this, f, arg);
        }

      // register calls for constant creation even though
      // not every backend actually performs these calls.
      newCall(null,
              constCl,
              NO_SITE,
              Value.UNIT /* universe, but we do not use _universe as target */,
              args,
              null /* new environment */,
              context);

      return result;
    }


    /**
     * deserialize array constant of type {@code constCl} from {@code d}
     *
     * @param s the site of the constant
     *
     * @param constCl the constants clazz, e.g. {@code array (tuple i32 codepoint)}
     *
     * @param context for debugging: Reason that causes this const string to be
     * part of the analysis.
     *
     * @param d the serialized data to be used when creating this constant
     *
     * @return an instance of {@code constCl} with fields initialized using the data from {@code d}.
     */
    private Value newArrayConst(int s, int constCl, Context context, ByteBuffer d)
    {
      var result = newInstance(constCl, NO_SITE, context);
      var sa = _fuir.clazzField(constCl, 0);
      var sa0 = newInstance(_fuir.clazzResultClazz(sa), NO_SITE, context);

      var elementClazz = _fuir.inlineArrayElementClazz(constCl);
      var data = _fuir.clazzField(_fuir.clazzResultClazz(sa), 0);
      var lengthField = _fuir.clazzField(_fuir.clazzResultClazz(sa), 1);

      var elCount = d.getInt();

      // joining elements before instantiating sysarray
      // because setEl always triggers a DFA changed.
      Value elements = null;
      for (int idx = 0; idx < elCount; idx++)
        {
          var b = _fuir.deserializeConst(elementClazz, d);
          elements = elements == null
            ? constData(s, elementClazz, b).value()
            : elements.join(DFA.this, constData(s, elementClazz, b).value(), elementClazz);
        }
      SysArray sysArray = newSysArray(elements, elementClazz);

      sa0.setField(DFA.this, data, sysArray);
      sa0.setField(DFA.this, lengthField, NumericValue.create(DFA.this, _fuir.clazzResultClazz(lengthField), elCount));
      result.setField(DFA.this, sa, sa0);
      return result;
    }


    /**
     * Perform a match on value subv.
     */
    @Override
    public Val match(int s, AbstractInterpreter2<Val> ai, Val subv)
    {
      Val r = null; // result value null <=> does not return.  Will be set to Value.UNIT if returning case was found.
      for (var mc = 0; mc < _fuir.matchCaseCount(s); mc++)
        {
          // array to permit modification in lambda
          var taken = false;
          for (var t : _fuir.matchCaseTags(s, mc))
            {
              var sv = subv.value();
              if (sv instanceof ValueSet vs)
                {
                  for (var v : vs._componentsArray)
                    {
                      taken = matchSingleSubject(s, v, mc, t) || taken;
                    }
                }
              else
                {
                  taken = matchSingleSubject(s, sv, mc, t) || taken;
                }
              if (taken)
                {
                  DFA.this._takenMatchCases.add(((long)s<<32)|((long)mc));
                }
            }
          if (_reportResults && _options.verbose(9))
            {
              say("DFA for " + _fuir.siteAsString(s) + ": "+_fuir.codeAtAsString(s)+": "+subv+" case "+mc+": "+
                                 (taken ? "taken" : "not taken"));
            }

          if (taken)
            {
              var resv = ai.processCode(_fuir.matchCaseCode(s, mc));
              if (resv != null)
                { // if at least one case returns (i.e., result is not null), this match returns.
                  r = Value.UNIT;
                }
            }
        }
      DFA.this.site(s).recordResult(r == null);
      return r;
    }


    /**
     * Helper for match that matches only a single subject value.
     *
     * @param s site of the match
     *
     * @param v subject value of this match that is being tested.
     *
     * @param mc the match case to process
     *
     * @param t the tag the case matches
     *
     * @return true if this match case was taken by v
     */
    boolean matchSingleSubject(int s, Value v, int mc, int t)
    {
      var res = false;
      if (v instanceof TaggedValue tv)
        {
          if (tv._tag == t)
            {
              var field = _fuir.matchCaseField(s, mc);
              if (field != NO_CLAZZ)
                {
                  var untagged = tv._original;
                  _call._instance.setField(DFA.this, field, untagged);
                }
              res = true;
            }
        }
      else
        {
          throw new Error("DFA encountered Unexpected value in match: " + v.getClass() + " '" + v + "' " +
                          " for match of type " + _fuir.clazzAsString(_fuir.matchStaticSubject(s)));
        }
      return res;
    }


    /**
     * Create a tagged value of type newcl from an untagged value.
     */
    @Override
    public Val tag(int s, Val value, int newcl, int tagNum)
    {
      Val res = value.value().tag(_call._dfa, newcl, tagNum);
      return res;
    }


    /**
     * Generate code to terminate the execution immediately.
     *
     * @param msg a message explaining the illegal state
     */
    @Override
    public void reportErrorInCode(String msg)
    {
      // In the DFA, ignore these errors
    }

  }


  /*----------------------------  constants  ----------------------------*/


  /**
   * For debugging: dump stack when _changed is set, for debugging when fix point
   * is not reached.
   *
   * To enable, use fz with
   *
   *   FUZION_JAVA_OPTIONS=-Ddev.flang.fuir.analysis.dfa.DFA.SHOW_STACK_ON_CHANGE=true
   */
  static final boolean SHOW_STACK_ON_CHANGE =
    FuzionOptions.boolPropertyOrEnv("dev.flang.fuir.analysis.dfa.DFA.SHOW_STACK_ON_CHANGE");


  /**
   * Set this to show statistics on the clazzes that caused the most Calls that
   * need to be analysed.
   *
   * To enable, use fz with
   *
   *   dev_flang_fuir_analysis_dfa_DFA_SHOW_CALLS=on
   *
   * To show more details for feature {@code io.out.replace}
   *
   *   dev_flang_fuir_analysis_dfa_DFA_SHOW_CALLS=io.out.replace
   *
   */
  static final String SHOW_CALLS_ENV = FuzionOptions.propertyOrEnv("dev.flang.fuir.analysis.dfa.DFA.SHOW_CALLS", "off");
  static final String SHOW_CALLS = Stream.of("off", "").anyMatch(SHOW_CALLS_ENV::equals) ? null : SHOW_CALLS_ENV;


  /**
   * Set this to show statistics on the clazzes that caused the most value that
   * need to be analysed.
   *
   * To enable, use fz with
   *
   *   dev_flang_fuir_analysis_dfa_DFA_SHOW_VALUES=on
   *
   * To show more details for feature {@code u32}
   *
   *   dev_flang_fuir_analysis_dfa_DFA_SHOW_VALUES=u32
   *
   */
  static final String SHOW_VALUES_ENV = FuzionOptions.propertyOrEnv("dev.flang.fuir.analysis.dfa.DFA.SHOW_VALUES", "off");
  static final String SHOW_VALUES = Stream.of("off", "").anyMatch(SHOW_VALUES_ENV::equals) ? null : SHOW_VALUES_ENV;


  /**
   * Should the DFA analysis be call-site-sensitive? If set, this treats two
   * equals calls {@code t.f args} if they occur at different sites, i.e., location in
   * the source code.
   *
   * To enable, use fz with
   *
   *   dev_flang_fuir_analysis_dfa_DFA_SITE_SENSITIVE=true
   */
  static final boolean SITE_SENSITIVE = FuzionOptions.boolPropertyOrEnv("dev.flang.fuir.analysis.dfa.DFA.SITE_SENSITIVE", false);


  /**
   * Should the DFA analysis use embedded values?  This is required for proper
   * escape analysis of instances that contain value types.  Disabling this is
   * only for experimental purposes and will break the C backend since it relies
   * on escape analysis of embedded references.
   *
   * To disable, use fz with
   *
   *   dev_flang_fuir_analysis_dfa_DFA_USE_EMBEDDED_VALUES=false
   */
  static final boolean USE_EMBEDDED_VALUES = FuzionOptions.boolPropertyOrEnv("dev.flang.fuir.analysis.dfa.DFA.USE_EMBEDDED_VALUES", true);


  /**
   * DFA's intrinsics.
   */
  static TreeMap<String, IntrinsicDFA> _intrinsics_ = new TreeMap<>();


  /**
   * Set of intrinsics that are found to be used by the DFA.
   */
  static Set<String> _usedIntrinsics_ = new TreeSet<>();


  /**
   * Maximum recursive analysis of newly created Calls, see {@code analyzeNewCall} for
   * details.
   *
   * This was optimized using
   *
   *   > time ./build/bin/fz build/tests/catch_postcondition/catch_postcondition.fz
   *
   * which gave
   *
   *  time/sec   MAX_RECURSION
   *   9.434 for 160
   *   9.33  for  80
   *   9.38  for  50
   *   9.601 for  44
   *   9.135 for  40
   *   9.71  for  36
   *   9.4   for  30
   *   9.299 for  25
   *  10.693 for  20
   *  14.68  for  10
   *
   * So it seems as if the results are good starting at 25.
   */
  private static int MAX_NEW_CALL_RECURSION =
    FuzionOptions.intPropertyOrEnv("dev.flang.fuir.analysis.dfa.DFA.MAX_NEW_CALL_RECURSION", 40);


  /*-------------------------  static methods  --------------------------*/


  /**
   * Helper method to add intrinsic to _intrinsics_.
   */
  private static void put(String n, IntrinsicDFA c)
  {
    _intrinsics_.put(n, (call) -> {
      _usedIntrinsics_.add(n);
      return c.analyze(call);
    });
  }


  /**
   * Get the names of all intrinsics supported by this backend.
   */
  public static Set<String> supportedIntrinsics()
  {
    return _intrinsics_.keySet();
  }


  /*----------------------------  variables  ----------------------------*/


  /**
   * Options provided to the 'fz' command.
   */
  public final FuzionOptions _options;


  /**
   * The intermediate code we are analyzing.
   */
  public final GeneratingFUIR _fuir;


  /**
   * Special values of clazz 'bool' for 'true', 'false' and arbitrary bool
   * values.
   */
  Value _trueX, _falseX, _boolX;


  /**
   * Special value for universe.
   */
  final Value _universe;


  /**
   * Values created during DFA analysis that are cached via cache(Value).
   */
  TreeMap<Value, Value> _cachedValues = new TreeMap<>(Value.COMPARATOR);


  /**
   * Number of unique values that have been created.
   */
  int _numUniqueValues = 0;
  List<Value> _uniqueValues = new List<Value>();


  /**
   * Cache for results of newInstance.
   *
   * {@code site -> (long) clazz << 32 | call id -> Instance}
   */
  List<LongMap<Instance>> _instancesForSite = new List<>();


  /**
   * Cached results of newTaggedValue.
   *
   * Map key build from from clazz id, original value id and tag number to
   * TaggedValue instances.
   */
  LongMap<TaggedValue> _tagged = new LongMap<>();


  /**
   * Stored results of newValueSet.
   *
   * Map from key made from v._id and w._id to resulting value set.
   */
  LongMap<Value> _joined = new LongMap<>();


  /**
   * For different element types, pre-allocated SysArrays for uninitialized
   * arrays.  Used to store results of {@code newSysArray}.
   */
  IntMap<SysArray> _uninitializedSysArray = new IntMap<>();


  /**
   * Calls created during DFA analysis for instances that are still unit type
   * values.  This is done to avoid creating many calls and values that all end
   * up being unit types.
   */
  IntMap<Call> _unitCalls = new IntMap<>();


  /**
   * Calls created during DFA analysis.
   */
  TreeMap<Call, Call> _calls = new TreeMap<>();
  TreeMap<CallGroup, CallGroup> _callGroups = new TreeMap<>();


  /**
   * For those Calls whose key can be mapped to a long value, this gives a quick
   * way to lookup that key.
   */
  LongMap<Call> _callsQuick = new LongMap<>();
  LongMap<CallGroup> _callGroupsQuick = new LongMap<>();


  /**
   * All calls will receive a unique identifier.  This is the next unique
   * identifier to be used for the next new call.
   */
  int _callIds = 0;


  /**
   * Sites created during DFA analysis.
   */
  List<Site> _sites = new List<>();


  /**
   * Calls created during current sub-iteration of the DFA analysis.  These will
   * be analyzed at the end of the current iteration since they most likely add
   * new information.
   */
  List<Call> _hotCalls = new List<>();


  /**
   * Current number of recursive analysis of newly created Calls, see {@code analyzeNewCall} for
   * details.
   */
  private int _newCallRecursiveAnalyzeCalls = 0;


  /**
   * Clazz ids for clazzes for of newly created calls for which recursive analysis is performed,
   * see {@code analyzeNewCall} for details.
   *
   * This is set by method dfa() after the initial call was created to avoid the
   * initial cal to be treated outside the first iteration.
   */
  private int[] _newCallRecursiveAnalyzeClazzes = new int[0];


  /**
   * Envs created during DFA analysis.  The envs are compared insensitive to the
   * order in which they are installed.
   */
  TreeMap<Env, Env> _envs = new TreeMap<>();


  /**
   * Quick lookup for envs using a long key. This may contains several keys for
   * the same Env that differ only in the order the effects are installed.
   */
  LongMap<Env> _envsQuick = new LongMap<>();


  /**
   * Set of effect values used in Env-ironmnents. These values have their own
   * id, Value._envId, and they are compared differently using
   * Value.ENV_COMPARATOR to avoid env value explosion.
   */
  TreeMap<Value, Value> _envValues = new TreeMap<>(Value.ENV_COMPARATOR);


  /**
   * All fields that are ever written.  These will be needed even if they are
   * never read unless the assignments are actually removed (which is currently
   * not the case).
   */
  BitSet _writtenFields = new BitSet();


  /**
   * All fields that are ever read.
   */
  BitSet _readFields = new BitSet();


  /**
   * All clazzes that contain fields that are ever read.
   */
  BitSet _hasFields = new BitSet();


  /**
   * Map from type to corresponding default effects.
   *
   * NYI: this might need to be thread-local and not global!
   */
  public final IntMap<Value> _defaultEffects = new IntMap<>();


  /**
   * Map from effect-type to corresponding call that uses this effect.
   *
   * NYI: this might need to be thread-local and not global!
   */
  public final IntMap<Call> _defaultEffectContexts = new IntMap<>();


  /**
   * Flag to detect changes during current iteration of the fix-point algorithm.
   * If this remains false during one iteration we have reached a fix-point.
   */
  private boolean _changed = false;


  /**
   * For debugging: lazy creation of a message why _changed was set to true.
   */
  private Supplier<String> _changedSetBy;


  /**
   * Set of effects that are missing, excluding default effects.
   */
  IntMap<Integer> _missingEffects = new IntMap<>(); // NYI: CLEANUP: Remove? This is only written?


  /**
   * List of numeric values to avoid duplicates, values that are known
   */
  List<LongMap<NumericValue>> _numericValues = new List<>();


  /**
   * List of numeric values to avoid duplicates, values that are unknown, i.e,
   * that could have any value allowed by the numeric type.
   */
  List<NumericValue> _numericValuesAny = new List<>();


  List<Instance> _oneInstanceOfClazz = new List<>();


  /**
   * Flag to control output of errors.  This is set to true after a fix point
   * has been reached to report errors that should disappear when fix point is
   * reached (like vars are initialized).
   */
  boolean _reportResults = false;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create DFA for given intermediate code.
   *
   * @param options the options to specify values for intrinsics like 'debug',
   * 'safety'.
   *
   * @param fuir the intermediate code.
   */
  public DFA(FuzionOptions options, GeneratingFUIR fuir)
  {
    _options = options;
    _fuir = fuir;
    _universe = newInstance(_fuir.clazzUniverse(), NO_SITE, Context._MAIN_ENTRY_POINT_);
    Errors.showAndExit();
  }


  /*-----------------------------  methods  -----------------------------*/

  /**
   * get the FUIR from the call cl
   *
   * @param cl
   * @return
   */
  private static GeneratingFUIR fuir(Call cl)
  {
    return cl._dfa._fuir;
  }

  Value bool()
  {
    if (_boolX == null)
      {
        var bool = _fuir.clazz(SpecialClazzes.c_bool);
        if (bool != FUIR.NO_CLAZZ)
          {
            _trueX  = newTaggedValue(bool, Value.UNIT, 1);
            _falseX = newTaggedValue(bool, Value.UNIT, 0);
            _boolX  = _trueX.join(this, _falseX, bool);
          }
        else
          { // we have a very small application that does not even use `bool`
            _trueX  = Value.UNIT;
            _falseX = Value.UNIT;
            _boolX  = Value.UNIT;
          }
      }
    return _boolX;
  }
  Value True()
  {
    var ignore = bool();
    return _trueX;
  }
  Value False()
  {
    var ignore = bool();
    return _falseX;
  }


  /**
   * Create a new Instance of FUIR using the information collected during this
   * DFA analysis. In particular, Let 'clazzNeedsCode' return false for
   * routines that were found never to be called.
   */
  public DfaFUIR new_fuir()
  {
    dfa();
    _options.timer("dfa");
    var res = new DfaFUIR((GeneratingFUIR) _fuir)
      {
        /**
         * Determine the lifetime of the instance of a call to clazz cl.
         *
         * @param cl a clazz id of any kind
         *
         * @return A conservative estimate of the lifespan of cl's instance.
         * Undefined if a call to cl does not create an instance, Call if it is
         * guaranteed that the instance is inaccessible after the call returned.
         */
        public LifeTime lifeTime(int cl)
        {
          return
            (clazzKind(cl) != FeatureKind.Routine) ? super.lifeTime(cl) :
            !_options.needsEscapeAnalysis() ||
            currentEscapes(cl)                     ? LifeTime.Unknown
                                                   : LifeTime.Call;
        }


        /**
         * For a call to cl, does the instance of cl escape the call?
         *
         * @param cl a call's inner clazz
         *
         * @return true iff the instance of the call must be allocated on the
         * heap.
         */
        private boolean currentEscapes(int cl)
        {
          if (PRECONDITIONS) require
            (_options.needsEscapeAnalysis());

          return _escapes.contains(cl);
        }


        /**
         * For a call in cl in code block c at index i, does the result escape
         * the current clazz stack frame (such that it cannot be stored in a
         * local var in the stack frame of cl)
         *
         * @param s site of call
         *
         * @return true iff the result of the call must be cloned on the heap.
         */
        public boolean doesResultEscape(int s)
        {
          if (PRECONDITIONS) require
            (_options.needsEscapeAnalysis());

          return _escapesCode.contains(s);
        }


        @Override
        public boolean alwaysResultsInVoid(int s)
        {
          if (s < 0)
            {
              return false;
            }
          else
            {
              var code = _fuir.codeAt(s);
              return (code == ExprKind.Call || code == ExprKind.Match) && site(s).alwaysResultsInVoid() || super.alwaysResultsInVoid(s);
            }
        }


        @Override
        public synchronized int[] matchCaseTags(int s, int cix)
        {
          var key = ((long)s<<32)|((long)cix);
          return _takenMatchCases.contains(key) ? super.matchCaseTags(s, cix) : new int[0];
        };


        @Override
        public int matchCaseField(int s, int cix)
        {
          var key = ((long)s<<32)|((long)cix);
          return _takenMatchCases.contains(key) ?  super.matchCaseField(s, cix) : NO_CLAZZ;
        }


        @Override
        public boolean clazzIsUnitType(int cl)
        {
          return super.clazzIsUnitType(cl) || isUnitType(cl);
        }


        @Override
        public int clazzOuterRef(int cl)
        {
          var res = NO_CLAZZ;
          var or = super.clazzOuterRef(cl);
          if (or != NO_CLAZZ && !clazzIsUnitType(clazzResultClazz(or)))
            {
              res = or;
            }

          return res;
        }


        @Override
        public int accessedClazz(int s)
        {
          return codeAt(s) == ExprKind.Assign &&
            (clazzIsUnitType(assignedType(s)) || clazzIsUnitType(accessTargetClazz(s)))
            ? NO_CLAZZ
            : super.accessedClazz(s);
        }

      };

    return res;
  }


  /**
   * Perform DFA analysis
   */
  public void dfa()
  {
    _newCallRecursiveAnalyzeClazzes = new int[MAX_NEW_CALL_RECURSION];
    _real = false;
    findFixPoint();

    for (var k : _callGroupsQuick.keySet())
      {
        _callGroupsQuick.get(k).saveEffects();
      }
    for (var g : _callGroups.values())
      {
        g.saveEffects();
      }

    _cachedValues = new TreeMap<>(Value.COMPARATOR);
    // _numUniqueValues = 0;    -- NYI: Somewhere, the old unique ids are still in use, need to check why! See reg_issue2478 to check this.
    _uniqueValues = new List<Value>();
    _instancesForSite = new List<>();
    _tagged = new LongMap<>();
    _joined = new LongMap<>();
    _uninitializedSysArray = new IntMap<>();

    _callsQuick = new LongMap<>();
    _calls = new TreeMap<>();
    _callGroupsQuick = new LongMap<>();
    _callGroups = new TreeMap<>();

    _instancesForSite = new List<>();
    _unitCalls = new IntMap<>();

    _real = true;
    findFixPoint();

    _fuir.reportAbstractMissing();
    Errors.showAndExit();
  }


  /**
   * When using two-pased DFA, is this the first phase to find required effects,
   * or the real phase?
   */
  boolean _real;


  /**
   * Iteratively perform data flow analysis until a fix point is reached.
   */
  void findFixPoint()
  {
    var cnt = 0;
    do
      {
        cnt++;
        _changed = false;
        if (cnt == 1)
          {
            newCall(null,
                    _fuir.mainClazz(),
                    NO_SITE,
                    Value.UNIT,
                    new List<>(),
                    null /* env */,
                    Context._MAIN_ENTRY_POINT_);
          }
        _changedSetBy = () -> "*** change not set ***";
        iteration();
      }
    while (_changed && (true || cnt < 100));

    if (_options.verbose(4))
      {
        _options.verbosePrintln(4, "DFA done:");
        _options.verbosePrintln(4, "Values: " + _numUniqueValues);
        int i = 0;
        var valueStrings = new TreeSet<String>();
        for (var v : _uniqueValues)
          {
            valueStrings.add(v.toString());
          }
        for (var v : valueStrings)
          {
            i++;
            _options.verbosePrintln(5, "  value "+i+"/"+_numUniqueValues+": " + v);
          }
        _options.verbosePrintln(6, "Calls: ");
        for (var c : _calls.values())
          {
            _options.verbosePrintln(6, "  call: " + c);
          }
      }

    if (_real)
      {
        _reportResults = true;
        iteration();

        _fuir.lookupDone();  // once we are done, FUIR.clazzIsUnitType() will work since it can be sure nothing will be added.
      }

    if (CHECKS) check
      (!_changed);

    showCallStatistics();
  }


  /**
   * If call statistics are enabled (@see #SHOW_CALLS), show statistics on the
   * clazzes that caused the most Calls that need to be analysed.
   */
  void showCallStatistics()
  {
    for (var c : _calls.values())
      {
        analyzeShowDetails(c);
      }

    if (SHOW_CALLS != null)
      {
        var total = _calls.size();
        var counts = new IntMap<Integer>();
        for (var c : _calls.values())
          {
            var i = counts.getOrDefault(c.calledClazz(), 0);
            counts.put(c.calledClazz(), i+1);
          }
        counts
          .keySet()
          .stream()
          .sorted((a,b)->
                  { var ca = counts.get(a);
                    var cb = counts.get(b);
                    return ca != cb ? Integer.compare(counts.get(a), counts.get(b))
                      : Integer.compare(a, b);
                  })
          .filter(c -> counts.get(c) > total / 500)
          .forEach(c ->
                   {
                     System.out.println("Call count "+counts.get(c)+"/"+total+" for "+_fuir.clazzAsString(c)+" "+(_fuir.clazzIsUnitType(c)?"UNIT":""));
                     if (_fuir.clazzAsString(c).equals(SHOW_CALLS))
                       {
                         var i = 0;
                         Call prev = null;
                         for (var cc : _calls.values())
                           {
                             if (cc.calledClazz() == c)
                               {
                                 System.out.println(""+i+": "+cc);
                                 if (prev != null && cc.toString().equals(prev.toString()))
                                   {
                                     System.out.println("########## EQ, but compare results in "+prev.compareToWhy(cc));
                                   }
                                 i++;
                               }
                             prev = cc;
                           }
                       }
                   });
      }

    if (SHOW_VALUES != null)
      {
        var total = _uniqueValues.size();
        var counts = new IntMap<Integer>();
        for (var v : _uniqueValues)
          {
            var i = counts.getOrDefault(v._clazz, 0);
            counts.put(v._clazz, i+1);
            if (v._clazz == NO_CLAZZ && ((i&(i-1))==0)) System.out.println("clazz is null for "+v.getClass()+" "+v);
          }
        counts
          .keySet()
          .stream()
          .sorted((a,b)->
                  { var ca = counts.get(a);
                    var cb = counts.get(b);
                    return ca != cb ? Integer.compare(counts.get(a), counts.get(b))
                      : Integer.compare(a, b);
                  })
          .filter(c -> counts.get(c) > total / 5000)
          .forEach(c ->
                   {
                     System.out.println("Value count "+counts.get(c)+"/"+total+" for "+_fuir.clazzAsString(c));
                     if (_fuir.clazzAsString(c).equals(SHOW_VALUES))
                       {
                         var i = 0;
                         for (var v : _uniqueValues)
                           {
                             if (v._clazz == c)
                               {
                                 System.out.println(i + ": " + v);
                                 i++;
                               }
                           }
                       }
                   });
      }
  }


  /**
   * Set flag _changed to record the fact that the current iteration has not
   * reached a fix point yet.
   *
   * @param by in case _changed was not set yet, by is used to produce a message
   * why we have not reached a fix point yet.
   */
  void wasChanged(Supplier<String> by)
  {
    if (!_changed)
      {
        if (SHOW_STACK_ON_CHANGE)
          {
            var msg = by.get();
            say(msg);
            Thread.dumpStack();
          }
        _changedSetBy = by;
        _changed = true;
      }
  }


  /**
   * Perform one iteration of the analysis.
   */
  void iteration()
  {
    var s = new List<Call>();
    s.addAll(_calls.values());
    for (var c : s)
      {
        c._scheduledForAnalysis = true;
      }
    while (!s.isEmpty())
      {
        for (var c : s)
          {
            c._scheduledForAnalysis = false;
            analyze(c);
          }
        s = _hotCalls;
        _hotCalls = new List<>();
      }
  }


  /**
   * During analysis, mark the given call as {@code hot}, i.e., unless it is already
   * scheduled to be analyzed or re-analyzed in the current iteration, schedule
   * it to be.
   *
   * @param c a call that depends on some values that have changed in the
   * current iteration.
   */
  void hot(Call c)
  {
    if (!c._scheduledForAnalysis)
      {
        _hotCalls.add(c);
        c._scheduledForAnalysis = true;
      }
  }


  /**
   * Analyze code for given call
   *
   * @param c the call
   */
  void analyze(Call c)
  {
    if (_fuir.clazzKind(c.calledClazz()) == FUIR.FeatureKind.Routine)
      {
        var i = c._instance;
        check
          (c._args.size() == _fuir.clazzArgCount(c.calledClazz()));
        for (var a = 0; a < c._args.size(); a++)
          {
            var af = _fuir.clazzArg(c.calledClazz(), a);
            var aa = c._args.get(a);
            i.setField(this, af, aa.value());
          }

        // copy outer ref argument to outer ref field:
        var or = _fuir.clazzOuterRef(c.calledClazz());
        if (or != NO_CLAZZ)
          {
            i.setField(this, or, c.target());
          }

        var ai = new AbstractInterpreter2<Val>(_fuir, new Analyze(c));
        var r = ai.processClazz(c.calledClazz());
        if (r != null)
          {
            c.returns();
          }
      }
  }


  /**
   * Print out details on the analysis of call {@code c}
   */
  void analyzeShowDetails(Call c)
  {
    if (_reportResults && _options.verbose(6))
      {
        say(("----------------"+c+
             "----------------------------------------------------------------------------------------------------")
            .substring(0,100));

        var sb = new StringBuilder();
        var ignore = c.showWhy(sb);
        say(sb);
      }
  }


  /**
   * Flag to detect and stop (endless) recursion within NYIintrinsicMissing.
   */
  static boolean _recursion_in_NYIintrinsicMissing = false;


  /**
   * Report that intrinsic 'cl' is missing and return Value.UNDEFINED.
   */
  static Value NYIintrinsicMissing(Call cl)
  {
    if (true || cl._dfa._reportResults)
      {
        var name = fuir(cl).clazzOriginalName(cl.calledClazz());

        // NYI: Proper error handling.
        Errors.error("NYI: Support for intrinsic '" + name + "' missing");

        // cl.showWhy(sb) may try to print result values that depend on
        // intrinsics, so we risk running into an endless recursion here:
        if (!_recursion_in_NYIintrinsicMissing)
          {
            _recursion_in_NYIintrinsicMissing = true;
            var sb = new StringBuilder();
            var ignore = cl.showWhy(sb);
            say_err(sb);
            _recursion_in_NYIintrinsicMissing = false;
          }
      }
    return Value.UNDEFINED;
  }


  /**
   * Set of clazzes whose instance may escape the call to the clazz's routine.
   */
  TreeSet<Integer> _escapes = new TreeSet<>();


  /**
   * Set of sites of calls whose result value may escape the caller's
   * context (since a pointer to that value may be passed to a call).
   */
  TreeSet<Integer> _escapesCode = new TreeSet<>();


  /**
   * Record that the given clazz escape the call to the routine.  If it
   * does escape, the instance cannot be allocated on the stack.
   *
   * @param cc the clazz to check
   */
  void escapes(int cc)
  {
    if (_escapes.add(cc))
      {
        wasChanged(() -> "Escapes: " + _fuir.clazzAsString(cc));
      }
  }


  /**
   * Record that a temporary value whose address is taken may live longer than
   * than the current call, so we cannot store it in the current stack frame.
   *
   * @param s site of the call or assignment we are analyzing
   *
   * @param v value we are taking an address of
   *
   * @param adrField field the address of {@code v} is assigned to.
   *
   */
  void tempEscapes(int s, Val v, int adrField)
  {
    if (v instanceof EmbeddedValue ev &&
        adrField != NO_CLAZZ &&
        !_fuir.clazzIsRef(_fuir.clazzResultClazz(adrField)) &&
        _fuir.clazzFieldIsAdrOfValue(adrField)
        && ev._site != NO_SITE
        )
      {
        var cp = ev._site;
        if (!_escapesCode.contains(cp))
          {
            _escapesCode.add(cp);
            wasChanged(() -> "code escapes: "+_fuir.codeAtAsString(s));
          }
      }
  }


  /**
   * Helper routine for intrinsics that set the elements of an array given as an argument
   *
   * @param cl the intrinsic call
   *
   * @param argnum the argument that is an internal array
   *
   * @param intrinsicName name of the intrinsic, just of error handling
   *
   * @param elementClazz the element type of the array.
   */
  static void setArrayElementsToAnything(Call cl, int argnum, String intrinsicName, SpecialClazzes elementClazz)
  {
    var array = cl._args.get(argnum);
    if (array instanceof SysArray sa)
      {
        sa.setel(NumericValue.create(cl._dfa, fuir(cl).clazz(SpecialClazzes.c_i32)),
                 NumericValue.create(cl._dfa, fuir(cl).clazz(elementClazz)));
      }
    else
      {
        Errors.fatal("DFA internal error: intrinsic '" + intrinsicName+ ": Expected class SysArray, found " + array.getClass() + " " + array);
      }
  }


  /**
   * Helper routine to call setArrayElementsToAnything with specific clazz.
   *
   * @param cl the intrinsic call
   *
   * @param argnum the argument that is an internal array
   *
   * @param intrinsicName name of the intrinsic, just of error handling
   */
  static void setArrayU8ElementsToAnything (Call cl, int argnum, String intrinsicName) { setArrayElementsToAnything(cl, argnum, intrinsicName, SpecialClazzes.c_u8 ); }
  static void setArrayI32ElementsToAnything(Call cl, int argnum, String intrinsicName) { setArrayElementsToAnything(cl, argnum, intrinsicName, SpecialClazzes.c_i32); }
  static void setArrayI64ElementsToAnything(Call cl, int argnum, String intrinsicName) { setArrayElementsToAnything(cl, argnum, intrinsicName, SpecialClazzes.c_i64); }


  /**
   * Allocate the result instance of an intrinsic call returning
   * fuzion.java.Java_Object or children like fuzion.java.Array.
   *
   * @param cl the call to the intrinsic
   *
   * @return the result instance
   */
  static Value wrappedJavaObject(Call cl)
  {
    var rc   = fuir(cl).clazzResultClazz(cl.calledClazz());
    return cl._dfa.newInstance(rc, NO_SITE, cl._context);
  }

  static
  {
    put("Type.name"                      , cl -> cl._dfa.newConstString(fuir(cl).clazzTypeName(fuir(cl).clazzOuterClazz(cl.calledClazz())), cl) );

    put("concur.atomic.compare_and_swap0",  cl ->
        {
          var v = fuir(cl).lookupAtomicValue(fuir(cl).clazzOuterClazz(cl.calledClazz()));

          if (CHECKS) check
            (fuir(cl).clazzNeedsCode(v));

          var atomic    = cl.target();
          var expected  = cl._args.get(0);
          var new_value = cl._args.get(1).value();
          var res = atomic.callField(cl._dfa, v, cl.site(), cl);

          cl._dfa.markReadRecursively(v);

          // NYI: we could make compare_and_swap more accurate and call setField only if res contains expected, need bit-wise comparison
          atomic.setField(cl._dfa, v, new_value);
          return res;
        });

    put("concur.atomic.compare_and_set0",  cl ->
        {
          var v = fuir(cl).lookupAtomicValue(fuir(cl).clazzOuterClazz(cl.calledClazz()));

          if (CHECKS) check
            (fuir(cl).clazzNeedsCode(v));

          var atomic    = cl.target();
          var expected  = cl._args.get(0);
          var new_value = cl._args.get(1).value();
          var ignore = atomic.callField(cl._dfa, v, cl.site(), cl);

          cl._dfa.markReadRecursively(v);

          // NYI: we could make compare_and_set more accurate and call setField only if res contains expected, need bit-wise comparison
          atomic.setField(cl._dfa, v, new_value);
          return cl._dfa.bool();
        });

    put("concur.atomic.racy_accesses_supported",  cl ->
        {
          // NYI: racy_accesses_supported could return true or false depending on the backend's behavior.
          return cl._dfa.bool();
        });

    put("concur.atomic.read0",  cl ->
        {
          var v = fuir(cl).lookupAtomicValue(fuir(cl).clazzOuterClazz(cl.calledClazz()));

          if (CHECKS) check
            (fuir(cl).clazzNeedsCode(v));

          var atomic = cl.target();
          return atomic.callField(cl._dfa, v, cl.site(), cl);
        });

    put("concur.atomic.write0", cl ->
        {
          var v = fuir(cl).lookupAtomicValue(fuir(cl).clazzOuterClazz(cl.calledClazz()));

          if (CHECKS) check
            (fuir(cl).clazzNeedsCode(v));

          var atomic    = cl.target();
          var new_value = cl._args.get(0).value();
          atomic.setField(cl._dfa, v, new_value);
          return Value.UNIT;
        });

    put("concur.util.load_fence", cl ->
        {
          return Value.UNIT;
        });

    put("concur.util.store_fence", cl ->
        {
          return Value.UNIT;
        });

    put("safety"                         , cl -> cl._dfa._options.fuzionSafety() ? cl._dfa.True() : cl._dfa.False() );
    put("debug"                          , cl -> cl._dfa._options.fuzionDebug()  ? cl._dfa.True() : cl._dfa.False() );
    put("debug_level"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz()), cl._dfa._options.fuzionDebugLevel()) );

    put("fuzion.sys.args.count"          , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("fuzion.sys.args.get"            , cl -> cl._dfa.newConstString(null, cl) );
    put("fuzion.std.exit"                , cl -> null );

    put("fuzion.sys.fatal_fault0"        , cl-> null                                                              );

    put("i8.prefix -°"                   , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i16.prefix -°"                  , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i32.prefix -°"                  , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i64.prefix -°"                  , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i8.infix -°"                    , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i16.infix -°"                   , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i32.infix -°"                   , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i64.infix -°"                   , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i8.infix +°"                    , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i16.infix +°"                   , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i32.infix +°"                   , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i64.infix +°"                   , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i8.infix *°"                    , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i16.infix *°"                   , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i32.infix *°"                   , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i64.infix *°"                   , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i8.div"                         , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i16.div"                        , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i32.div"                        , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i64.div"                        , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i8.mod"                         , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i16.mod"                        , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i32.mod"                        , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i64.mod"                        , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i8.infix <<"                    , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i16.infix <<"                   , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i32.infix <<"                   , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i64.infix <<"                   , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i8.infix >>"                    , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i16.infix >>"                   , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i32.infix >>"                   , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i64.infix >>"                   , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i8.infix &"                     , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i16.infix &"                    , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i32.infix &"                    , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i64.infix &"                    , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i8.infix |"                     , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i16.infix |"                    , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i32.infix |"                    , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i64.infix |"                    , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i8.infix ^"                     , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i16.infix ^"                    , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i32.infix ^"                    , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );
    put("i64.infix ^"                    , cl -> { return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())); } );

    put("i8.type.equality"               , cl -> cl._dfa.bool() );
    put("i16.type.equality"              , cl -> cl._dfa.bool() );
    put("i32.type.equality"              , cl -> cl._dfa.bool() );
    put("i64.type.equality"              , cl -> cl._dfa.bool() );
    put("i8.type.lteq"                   , cl -> cl._dfa.bool() );
    put("i16.type.lteq"                  , cl -> cl._dfa.bool() );
    put("i32.type.lteq"                  , cl -> cl._dfa.bool() );
    put("i64.type.lteq"                  , cl -> cl._dfa.bool() );

    put("u8.prefix -°"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u16.prefix -°"                  , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u32.prefix -°"                  , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u64.prefix -°"                  , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u8.infix -°"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u16.infix -°"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u32.infix -°"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u64.infix -°"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u8.infix +°"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u16.infix +°"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u32.infix +°"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u64.infix +°"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u8.infix *°"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u16.infix *°"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u32.infix *°"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u64.infix *°"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u8.div"                         , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u16.div"                        , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u32.div"                        , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u64.div"                        , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u8.mod"                         , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u16.mod"                        , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u32.mod"                        , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u64.mod"                        , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u8.infix <<"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u16.infix <<"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u32.infix <<"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u64.infix <<"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u8.infix >>"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u16.infix >>"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u32.infix >>"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u64.infix >>"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u8.infix &"                     , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u16.infix &"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u32.infix &"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u64.infix &"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u8.infix |"                     , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u16.infix |"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u32.infix |"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u64.infix |"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u8.infix ^"                     , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u16.infix ^"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u32.infix ^"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u64.infix ^"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );

    put("u8.type.equality"               , cl -> cl._dfa.bool() );
    put("u16.type.equality"              , cl -> cl._dfa.bool() );
    put("u32.type.equality"              , cl -> cl._dfa.bool() );
    put("u64.type.equality"              , cl -> cl._dfa.bool() );
    put("u8.type.lteq"                   , cl -> cl._dfa.bool() );
    put("u16.type.lteq"                  , cl -> cl._dfa.bool() );
    put("u32.type.lteq"                  , cl -> cl._dfa.bool() );
    put("u64.type.lteq"                  , cl -> cl._dfa.bool() );

    put("i8.as_i32"                      , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("i16.as_i32"                     , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("i32.as_i64"                     , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("i32.as_f64"                     , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("i64.as_f64"                     , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u8.as_i32"                      , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u16.as_i32"                     , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u32.as_i64"                     , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u32.as_f64"                     , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u64.as_f64"                     , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("i8.cast_to_u8"                  , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("i16.cast_to_u16"                , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("i32.cast_to_u32"                , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("i64.cast_to_u64"                , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u8.cast_to_i8"                  , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u16.cast_to_i16"                , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u32.cast_to_i32"                , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u32.cast_to_f32"                , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u64.cast_to_i64"                , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u64.cast_to_f64"                , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u16.low8bits"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u32.low8bits"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u64.low8bits"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u32.low16bits"                  , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u64.low16bits"                  , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("u64.low32bits"                  , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );

    put("f32.prefix -"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.prefix -"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.infix +"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.infix +"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.infix -"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.infix -"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.infix *"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.infix *"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.infix /"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.infix /"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.infix %"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.infix %"                    , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.infix **"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.infix **"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.type.equal"                 , cl -> cl._dfa.bool() );
    put("f64.type.equal"                 , cl -> cl._dfa.bool() );
    put("f32.type.lower_than_or_equal"   , cl -> cl._dfa.bool() );
    put("f64.type.lower_than_or_equal"   , cl -> cl._dfa.bool() );
    put("f32.as_f64"                     , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.as_f32"                     , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.as_i64_lax"                 , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.cast_to_u32"                , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.cast_to_u64"                , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );

    put("f32.is_NaN"                     , cl -> cl._dfa.bool() );
    put("f64.is_NaN"                     , cl -> cl._dfa.bool() );
    put("f32.square_root"                , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.square_root"                , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.exp"                        , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.exp"                        , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.log"                        , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.log"                        , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.sin"                        , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.sin"                        , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.cos"                        , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.cos"                        , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.tan"                        , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.tan"                        , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.asin"                       , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.asin"                       , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.acos"                       , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.acos"                       , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.atan"                       , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.atan"                       , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.sinh"                       , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.sinh"                       , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.cosh"                       , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.cosh"                       , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.tanh"                       , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.tanh"                       , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.type.min_exp"               , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.type.max_exp"               , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.type.min_positive"          , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.type.max"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f32.type.epsilon"               , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.type.min_exp"               , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.type.max_exp"               , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.type.min_positive"          , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.type.max"                   , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("f64.type.epsilon"               , cl -> NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz())) );
    put("effect.type.from_env"           , cl ->
    {
      var ecl = fuir(cl).clazzResultClazz(cl.calledClazz());
      return cl.getEffectCheck(cl.site(), ecl);
    });
    put("effect.type.unsafe_from_env"    , cl ->
    {
      var ecl = fuir(cl).clazzResultClazz(cl.calledClazz());
      return cl.getEffectCheck(cl.site(), ecl);
    });


    put("fuzion.sys.type.alloc"          , cl ->
        {
          var ec = fuir(cl).clazzActualGeneric(cl.calledClazz(), 0);
          return cl._dfa.newSysArray(null, ec); // NYI: get length from args
        });
    put("fuzion.sys.type.setel"          , cl ->
        {
          var array = cl._args.get(0).value();
          var index = cl._args.get(1).value();
          var value = cl._args.get(2).value();
          if (array instanceof SysArray sa)
            {
              sa.setel(index, value);
              return Value.UNIT;
            }
          else
            {
              throw new Error("intrinsic fuzion.sys.setel: Expected class SysArray, found "+array.getClass()+" "+array);
            }
        });
    put("fuzion.sys.type.getel"          , cl ->
        {
          var array = cl._args.get(0).value();
          var index = cl._args.get(1).value();
          if (array instanceof SysArray sa)
            {
              return sa.get(index);
            }
          else
            {
              throw new Error("intrinsic fuzion.sys.internal_array.gel: Expected class SysArray, found "+array.getClass()+" "+array);
            }
        });
    put("fuzion.sys.internal_array.freeze"
                                         , cl -> Value.UNIT);
    put("fuzion.sys.internal_array.ensure_not_frozen"
                                         , cl -> Value.UNIT);
    put("fuzion.sys.env_vars.has0"       , cl -> cl._dfa.bool() );
    put("fuzion.sys.env_vars.get0"       , cl -> cl._dfa.newConstString(null, cl) );
    put("fuzion.sys.thread.spawn0"       , cl ->
        {
          var oc = fuir(cl).clazzActualGeneric(cl.calledClazz(), 0);
          var call = fuir(cl).lookupCall(oc);

          if (CHECKS) check
            (fuir(cl).clazzNeedsCode(call));

          // NYI: spawn0 needs to set up an environment representing the new
          // thread and perform thread-related checks (race-detection. etc.)!
          var ignore = cl._dfa.newCall(cl, call, NO_SITE, cl._args.get(0).value(), new List<>(), null /* new environment */, cl);
          return cl._dfa.newInstance(fuir(cl).clazzResultClazz(cl.calledClazz()), NO_SITE, cl._context);
        });
    put("fuzion.sys.thread.join0"        , cl -> Value.UNIT);

    put("effect.type.replace0"              , cl ->
        {
          var ecl = fuir(cl).effectTypeFromIntrinsic(cl.calledClazz());
          var new_e = cl._args.get(0).value();

          if (cl._dfa._real)
            {
              cl.replaceEffect(ecl, new_e);
            }
          else
            {
              cl.replaceEffect(ecl, new_e); // NYI: Why here as well? base16_test.fz needs this??!!??
              var oev = cl._dfa._preEffectValues.get(ecl);
              var ev = oev == null ? new_e : oev.join(cl._dfa, new_e, ecl);
              if (oev != ev)
                {
                  cl._dfa._preEffectValues.put(ecl, ev);
                  cl._dfa.wasChanged(() -> "effect.type.replace0 called: " + fuir(cl).clazzAsString(cl.calledClazz()));
                }
            }

          return Value.UNIT;
        });
    put("effect.type.default0"              , cl ->
        {
          var ecl = fuir(cl).effectTypeFromIntrinsic(cl.calledClazz());
          var new_e = cl._args.get(0).value();
          if (cl._dfa._real)
            {
              var old_e = cl._dfa._defaultEffects.get(ecl);
              if (old_e == null)
                {
                  cl._dfa._defaultEffects.put(ecl, new_e);
                  cl._dfa._defaultEffectContexts.put(ecl, cl);
                  cl._dfa.wasChanged(() -> "effect.default called: " + fuir(cl).clazzAsString(cl.calledClazz()));
                }
            }
          else
            {
              var oev = cl._dfa._preEffectValues.get(ecl);
              var ev = oev == null ? new_e : oev.join(cl._dfa, new_e, ecl);
              if (oev != ev)
                {
                  cl._dfa._preEffectValues.put(ecl, ev);
                  cl._dfa.wasChanged(() -> "effect.default called: " + fuir(cl).clazzAsString(cl.calledClazz()));
                }
            }
          return Value.UNIT;
        });
    put(EFFECT_INSTATE_NAME                 , cl ->
        {
          var fuir = fuir(cl);
          var ecl      = fuir.effectTypeFromIntrinsic(cl.calledClazz());

          var call     = fuir.lookupCall(fuir.clazzActualGeneric(cl.calledClazz(), 0));
          var finallie = fuir.lookup_static_finally(ecl);

          var a0 = cl._args.get(0).value();  // new effect value e
          var a1 = cl._args.get(1).value();  // code
          var a2 = cl._args.get(2).value();  // default code

          var newEnv = (cl._dfa._real) ? cl._dfa.newEnv(cl._env, ecl, a0) : null;
          var cll = cl._dfa.newCall(cl,
                                    call,
                                    NO_SITE,
                                    a1,
                                    new List<>(),
                                    newEnv,
                                    cl);
          cll._group.mayHaveEffect(ecl);

          // manually propagate effects from result to cl, except for the one we
          // have instated here:
          for (var recl : cll._group._usedEffects)
            {
              if (recl != ecl)
                {
                  cl._group.needsEffect(recl);   // NYI: needed? Done also in CallGroup.needsEffect!
                }
            }

          var result = cll.result();
          Value ev;
          if (cl._dfa._real)
            {
              ev = newEnv.getActualEffectValues(ecl);
            }
          else
            {
              var oev = cl._dfa._preEffectValues.get(ecl);
              ev = oev == null ? a0 : oev.join(cl._dfa, a0, ecl);
              if (oev != ev)
                {
                  cl._dfa._preEffectValues.put(ecl, ev);
                  cl._dfa.wasChanged(() -> EFFECT_INSTATE_NAME + " called: " + fuir(cl).clazzAsString(cl.calledClazz()));
                }
            }
          var aborted =
            // NYI: Why check both, newEnv.isAborted and _preEffectsAborted?
            newEnv != null && newEnv.isAborted(ecl) ||
            cl._dfa._preEffectsAborted.contains(ecl) ||
            (cl._dfa._real ? newEnv.isAborted(ecl) : cl._dfa._preEffectsAborted.contains(ecl));
          var call_def = fuir.lookupCall(fuir.clazzActualGeneric(cl.calledClazz(), 1), aborted);
          if (aborted)
            { // default result, only if abort is ever called
              var def = cl._dfa.newCall(cl, call_def, NO_SITE, a2, new List<>(ev), cl._env, cl);
              var res = def.result();
              result =
                result != null && res != null ? result.value().join(cl._dfa, res.value(), fuir(cl).clazzResultClazz(cl.calledClazz())) :
                result != null                ? result
                                              : res;
            }

          cl._dfa.newCall(cl, finallie, NO_SITE, ev, new List<>(), cl._env, cl);
          return result;
        });
    put("effect.type.abort0"                , cl ->
        {
          var ecl = fuir(cl).effectTypeFromIntrinsic(cl.calledClazz());
          var ev = cl.getEffectCheck(cl.site(), ecl);
          if (cl._dfa._real)
            {
              if (ev != null      /* NYI: needed? */ &&
                  cl._env != null /* NYI: needed? */ )
                {
                  cl._env.aborted(ecl);
                }
              cl._dfa._preEffectsAborted.add(ecl);  // NYI: why here as well ?
            }
          else
            {
              cl._dfa._preEffectsAborted.add(ecl);
            }
          return null;
        });
    put("effect.type.is_instated0"          , cl -> cl.getEffectCheck(cl.site(), fuir(cl).effectTypeFromIntrinsic(cl.calledClazz())) != null
        ? cl._dfa.True()
        : cl._dfa.bool()  /* NYI: currently, this is never FALSE since a default effect might get installed turning this into TRUE
                          * should reconsider if handling of default effects changes
                          */
        );

    put("fuzion.jvm.is_null0"  , cl ->
        {
          cl._dfa.readField(fuir(cl).clazzArg(cl.calledClazz(), 0));
          return cl._dfa.bool();
        });
    put("fuzion.jvm.array_get"             , cl ->
        {
          cl._dfa.readField(fuir(cl).clazzArg(cl.calledClazz(), 0));
          return wrappedJavaObject(cl);
        });
    put("fuzion.jvm.array_length"          , cl ->
      {
        cl._dfa.readField(fuir(cl).clazzArg(cl.calledClazz(), 0));
        return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz()));
      }
    );
    put("fuzion.jvm.array_to_java_object0" , cl ->
        {
          var data = fuir(cl).lookup_fuzion_sys_internal_array_data  (fuir(cl).clazzArgClazz(cl.calledClazz(),0));
          var len  = fuir(cl).lookup_fuzion_sys_internal_array_length(fuir(cl).clazzArgClazz(cl.calledClazz(),0));
          cl._dfa.readField(data);
          cl._dfa.readField(len);
          return Value.UNKNOWN_JAVA_REF;
        });
    put("fuzion.jvm.get_field0"            , cl ->
      {
        var rc = fuir(cl).clazzResultClazz(cl.calledClazz());
        var jobj = wrappedJavaObject(cl);
        // otherwise it is a primitive like int, boolean
        if (fuir(cl).clazzIsRef(rc))
          {
            var jref = fuir(cl).lookupJavaRef(rc);
            jobj.setField(cl._dfa, jref, Value.UNKNOWN_JAVA_REF);
          }
        return jobj;
      });
    put("fuzion.jvm.set_field0"            , cl ->
      {
        return Value.UNIT;
      });
    put("fuzion.jvm.java_string_to_string" , cl -> cl._dfa.newConstString(null, cl) );
    put("fuzion.jvm.create_jvm", cl -> Value.UNIT);
    put("fuzion.jvm.destroy_jvm", cl -> Value.UNIT);
    put("fuzion.jvm.string_to_java_object0", cl -> Value.UNKNOWN_JAVA_REF);
    put("fuzion.jvm.primitive_to_java_object", cl -> Value.UNKNOWN_JAVA_REF);
  }


  /**
   * create a generic fuzion java result
   * that is wrapped in an outcome
   *
   * @param cl the call that this is the result of
   */
  static Value outcomeJavaResult(Call cl)
  {
    var rc = fuir(cl).clazzResultClazz(cl.calledClazz());
    return outcome(cl._dfa, cl, rc, fuzionJavaResult(cl, fuir(cl).clazzChoice(rc, 0)));
  }


  /**
   * create a generic Java_Object or primitive
   * based on the type of result clazz rc.
   *
   * @param cl the call that this is the result of
   *
   * @param rc
   */
  private static Value fuzionJavaResult(Call cl, int rc)
  {
    return switch (fuir(cl).getSpecialClazz(rc))
      {
        case c_i8, c_u16, c_i16, c_i32, c_i64, c_f32, c_f64, c_bool ->
          cl._dfa.newInstance(rc, NO_SITE, cl._context);
        case c_unit -> Value.UNIT;
        default -> {
          var jref = fuir(cl).lookupJavaRef(rc);
          if (CHECKS) check
            (jref != NO_CLAZZ);
          var jobj = cl._dfa.newInstance(rc, NO_SITE, cl._context);
          jobj.setField(cl._dfa, jref, Value.UNKNOWN_JAVA_REF);
          yield jobj;
        }
      };
  }


  /**
   * Create a fuzion outcome of type {@code rc} with the value res.
   *
   * @param cl The call in which we are creating this outcome
   * @param rc the resulting outcome clazz.
   * @param res the success value
   */
  private static Value outcome(DFA dfa, Call cl, int rc, Value res)
  {
    var okay = dfa.newTaggedValue(rc, res, 0);
    var error_cl = dfa._fuir.clazzChoice(rc, 1);
    var error = dfa.newInstance(error_cl, NO_SITE, cl._context);
    var msg = dfa._fuir.lookup_error_msg(error_cl);
    error.setField(dfa, msg, dfa.newConstString(null, cl));
    var err = dfa.newTaggedValue(rc, error, 1);
    return okay.join(dfa, err, rc);
  }


  static {
    put("fuzion.jvm.call_c0"               , cl ->
      {
        var cc = cl.calledClazz();
        var fuir = fuir(cl);
        var data = fuir.lookup_fuzion_sys_internal_array_data(fuir.clazzArgClazz(cc, 2));
        cl._dfa.readField(data);
        return outcomeJavaResult(cl);
      });
    put("fuzion.jvm.call_s0"               , cl ->
      {
        var cc = cl.calledClazz();
        var fuir = fuir(cl);
        var data = fuir.lookup_fuzion_sys_internal_array_data(fuir.clazzArgClazz(cc, 3));
        cl._dfa.readField(data);
        return outcomeJavaResult(cl);
      });
    put("fuzion.jvm.call_v0"               , cl ->
      {
        var cc = cl.calledClazz();
        var fuir = fuir(cl);
        var data = fuir.lookup_fuzion_sys_internal_array_data(fuir.clazzArgClazz(cc, 4));
        cl._dfa.readField(data);
        return outcomeJavaResult(cl);
      });
    put("fuzion.jvm.cast0", cl -> outcomeJavaResult(cl));
    put("fuzion.jvm.get_static_field0"     , cl ->
      {
        var rc = fuir(cl).clazzResultClazz(cl.calledClazz());
        var jobj = wrappedJavaObject(cl);
        // otherwise it is a primitive like int, boolean
        if (fuir(cl).clazzIsRef(rc))
          {
            var jref = fuir(cl).lookupJavaRef(rc);
            jobj.setField(cl._dfa, jref, Value.UNKNOWN_JAVA_REF);
          }
        return jobj;
      });
    put("fuzion.jvm.set_static_field0"     , cl ->
      {

        return Value.UNIT;
      });

    put("concur.sync.mtx_init"              , cl ->
      {
        var rc = fuir(cl).clazzResultClazz(cl.calledClazz());
        var ag = fuir(cl).clazzActualGeneric(rc, 0);
        return outcome(cl._dfa,
                       cl,
                       rc,
                       cl._dfa.newInstance(ag, NO_SITE, cl._context));
      });
    put("concur.sync.mtx_lock"              , cl ->
      {
        cl._dfa.readField(fuir(cl).clazzArg(cl.calledClazz(), 0));
        return cl._dfa.bool();
      });
    put("concur.sync.mtx_trylock"           , cl ->
      {
        cl._dfa.readField(fuir(cl).clazzArg(cl.calledClazz(), 0));
        return cl._dfa.bool();
      });
    put("concur.sync.mtx_unlock"            , cl ->
      {
        cl._dfa.readField(fuir(cl).clazzArg(cl.calledClazz(), 0));
        return cl._dfa.bool();
      });
    put("concur.sync.mtx_destroy"           , cl ->
      {
        cl._dfa.readField(fuir(cl).clazzArg(cl.calledClazz(), 0));
        return Value.UNIT;
      });
    put("concur.sync.cnd_init"              , cl ->
      {
        cl._dfa.readField(fuir(cl).clazzArg(cl.calledClazz(), 0));
        var rc = fuir(cl).clazzResultClazz(cl.calledClazz());
        var ag = fuir(cl).clazzActualGeneric(rc, 0);
        return outcome(cl._dfa,
                       cl,
                       rc,
                       cl._dfa.newInstance(ag, NO_SITE, cl._context));
      });
    put("concur.sync.cnd_signal"            , cl ->
      {
        cl._dfa.readField(fuir(cl).clazzArg(cl.calledClazz(), 0));
        return cl._dfa.bool();
      });
    put("concur.sync.cnd_broadcast"         , cl ->
      {
        cl._dfa.readField(fuir(cl).clazzArg(cl.calledClazz(), 0));
        return cl._dfa.bool();
      });
    put("concur.sync.cnd_wait"              , cl ->
      {
        cl._dfa.readField(fuir(cl).clazzArg(cl.calledClazz(), 0));
        cl._dfa.readField(fuir(cl).clazzArg(cl.calledClazz(), 1));
        return cl._dfa.bool();
      });
    put("concur.sync.cnd_destroy"           , cl ->
      {
        cl._dfa.readField(fuir(cl).clazzArg(cl.calledClazz(), 0));
        return Value.UNIT;
      });
    put("native_string_length"              , cl ->
      {
        cl._dfa.readField(fuir(cl).clazzArg(cl.calledClazz(), 0));
        return NumericValue.create(cl._dfa, fuir(cl).clazzResultClazz(cl.calledClazz()));
      });
    put("native_array"                      , cl ->
      {
        cl._dfa.readField(fuir(cl).clazzArg(cl.calledClazz(), 0));
        cl._dfa.readField(fuir(cl).clazzArg(cl.calledClazz(), 1));
        return cl._dfa.newSysArray(null, cl._dfa._fuir.clazzActualGeneric(cl.calledClazz(), 0));
      });
  }


  /**
   * Add given value to the set of default effect values for effect type ecl.
   */
  void replaceDefaultEffect(int ecl, Value e)
  {
    if (_real)
      {
        var old_e = _defaultEffects.get(ecl);
        if (old_e != null)
          {
            var new_e = old_e == null ? e : old_e.join(this, e, ecl);
            if (old_e == null || Value.compare(old_e, new_e) != 0)
              {
                _defaultEffects.put(ecl, new_e);
                wasChanged(() -> "effect.replace called: " + _fuir.clazzAsString(ecl));
              }
          }
      }
  }


  /**
   * Check if given clazz is a built-in numeric clazz: i8..i64, u8..u64, f32 or f64.
   *
   * @param cl the clazz
   *
   * @return true iff cl is built-in numeric;
   */
  boolean isBuiltInNumeric(int cl)
  {
    return switch (_fuir.getSpecialClazz(cl))
      {
      case
        c_i8   ,
        c_i16  ,
        c_i32  ,
        c_i64  ,
        c_u8   ,
        c_u16  ,
        c_u32  ,
        c_u64  ,
        c_f32  ,
        c_f64  -> true;
      default -> false;
      };
  }


  TreeSet<Integer> _calledClazzesDuringPrePhase = new TreeSet<>();
  TreeMap<Integer, TreeSet<Integer>> _clazzesThatRequireEffect = new TreeMap<>();
  TreeMap<Integer, TreeSet<Integer>> _effectsRequiredByClazz = new TreeMap<>();

  TreeMap<Integer, Value>_preEffectValues = new TreeMap<>();
  TreeSet<Integer> _preEffectsAborted = new TreeSet<>();


  /**
   * Create instance of given clazz.
   *
   * @param cl the clazz
   *
   * @param site the site index where the new instances is creates, NO_SITE
   * if not within code (intrinsics etc.)
   *
   * @param context for debugging: Reason that causes this instance to be part
   * of the analysis.
   */
  Value newInstance(int cl, int site, Context context)
  {
    if (PRECONDITIONS) require
      (!_fuir.clazzIsChoice(cl) || _fuir.clazzIs(cl, SpecialClazzes.c_bool));

    Value r;
    if (isBuiltInNumeric(cl))
      {
        r = NumericValue.create(DFA.this, cl);
      }
    else if (_fuir.clazzIs(cl, SpecialClazzes.c_bool))
      {
        r = bool();
      }
    else
      {
        if (onlyOneInstance(cl))
          {
            var cnum = _fuir.clazzId2num(cl);
            var a = _oneInstanceOfClazz.getIfExists(cnum);
            if (a == null)
              {
                var ni = new Instance(this, cl, site, context);
                makeUnique(ni);
                _oneInstanceOfClazz.force(cnum, ni);
                a = ni;
              }
            r = a;
          }
        else if (_fuir.clazzIsRef(cl))
          {
            var vc = _fuir.clazzAsValue(cl);
            check(!_fuir.clazzIsRef(vc));
            r = newInstance(vc, site, context).box(this, vc, cl, context);
          }
        else
          {
            // Instances are cached using two maps with keys
            //
            //  - clazzAt(site)           and then
            //  - cl << 32 || env.id
            //
            var sc = site == FUIR.NO_SITE ? FUIR.NO_CLAZZ : _fuir.clazzAt(site);
            var sci = sc == FUIR.NO_CLAZZ ? 0 : 1 + _fuir.clazzId2num(sc);

            var clazzm = _instancesForSite.getIfExists(sci);
            if (clazzm == null)
              {
                clazzm = new LongMap<>();
                _instancesForSite.force(sci, clazzm);
              }
            var env = _real ? context.env() : null;
            var k1 = _fuir.clazzId2num(cl);
            var k2 = (env == null ? 0 : env._id + 1);
            var k = (long) k1 << 32 | k2 & 0xffffFFFFL;
            r = clazzm.get(k);
            if (r == null && env != null)
              { // check if instance is an effect that is already present in the
                // current environment. If so, we do not create a new instance
                // since this would end up creating an new environment with that
                // new instance added, which will in turn end up here again to
                // create another instance ... ad infinitum.
                r = context.findEffect(cl, site);
              }
            if (r == null)
              {
                var ni = new Instance(this, cl, site, context);
                wasChanged(() -> "DFA: new instance " + _fuir.clazzAsString(cl));
                clazzm.put(k, ni);
                makeUnique(ni);
                r = ni;
              }
          }
      }
    return r;
  }


  /**
   * Remember that the given field is read.  Fields that are never read will be
   * removed from the code.
   */
  void readField(int field)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzKind(field) == FUIR.FeatureKind.Field);

    var fnum = _fuir.clazzId2num(field);
    if (!_readFields.get(fnum))
      {
        _readFields.set(fnum);
        wasChanged(() -> "DFA: read field " + _fuir.clazzAsString(field));
      }
    var cl = _fuir.clazzAsValue(_fuir.clazzOuterClazz(field));
    var clnum = _fuir.clazzId2num(cl);
    _hasFields.set(clnum);
  }


  /**
   * mark a field and the fields it
   * contains as read.
   *
   * @param field
   */
  void markReadRecursively(int field)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzKind(field) == FUIR.FeatureKind.Field);

    readField(field);

    var rt = _fuir.clazzResultClazz(field);

    if (!_fuir.clazzIsRef(rt))
      {
        for (int i = 0; i < _fuir.clazzFieldCount(rt); i++)
          {
            var f = _fuir.clazzField(rt, i);
            // e.g. i32.val
            if (f != field)
              {
                markReadRecursively(f);
              }
          }
      }
  }


  /**
   * Is this field ever read?
   */
  boolean isRead(int field)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzKind(field) == FUIR.FeatureKind.Field);

    var fnum = _fuir.clazzId2num(field);
    return _readFields.get(fnum);
  }


  /**
   * To reduce number of calls created for unit type values, we originally
   * assume calls to an empty constructor with no arguments and not fields as
   * all the same.
   *
   * @oaran cl a clazz id, must not be NO_CLAZZ
   *
   * @return true if, as for what we now about used fields at this pointer, {@code cl}
   * defines a unit type.
   */
  boolean isUnitType(int cl)
  {
    var clnum = _fuir.clazzId2num(cl);
    return
      !_hasFields.get(clnum) &&
      _defaultEffects.get(cl) == null &&
      _fuir.isConstructor(cl) &&
      !_fuir.clazzIsRef(cl) &&
      _fuir.clazzArgCount(cl) == 0 &&
      !isBuiltInNumeric(cl);
  }


  /**
   * Check if value 'r' exists already. If so, return the existing
   * one. Otherwise, add 'r' to the set of existing values, set _changed since
   * the state has changed and return r.
   *
   * NYI: CLEANUP: The goal is to eventually remove this cache and instead use
   * maps like _instancesForSite instead.
   */
  Value cache(Value r)
  {
    var e = _cachedValues.get(r);
    if (e == null)
      {
        _cachedValues.put(r, r);
        e = r;
        makeUnique(e);
      }
    return e;
  }


  /**
   * Set unique id for a newly created value.
   */
  void makeUnique(Value v)
  {
    if (PRECONDITIONS) require
      (v._id < 0);

    v._id = _numUniqueValues++;
    _uniqueValues.add(v);
    wasChanged(() -> "DFA: new value " + v);
  }
  { makeUnique(Value.UNIT); }



  /**
   * Create Tagged Value
   *
   * @param nc the new clazz of this value after a tagging.
   *
   * @param original the untagged value
   *
   * @param tag the tag value.  Unlike some C backends, the tag is never left
   * out during analysis.
   */
  TaggedValue newTaggedValue(int nc, Value original, int tag)
  {
    TaggedValue r;
    if (nc == FUIR.NO_CLAZZ)
      {
        r = null;
      }
    else
      {
        var cid = _fuir.clazzId2num(nc);
        var vid = original._id;
        // try to fit clazz id, original value id and tag into one long as follows:
        //
        // Bit 6666555555555544444444443333333333222222222211111111110000000000
        //     3210987654321098765432109876543210987654321098765432109876543210
        //     <-------clazz id-----------><---original value id------><--tag->
        //     |          28 bits         ||          28 bits         ||8 bits|
        //
        if (cid >= 0 && cid <= 0xFFFffff &&
            vid >= 0 && vid <= 0xFFFffff &&
            tag >= 0 && tag <= 0xff)
          {
            var k =
              (long) cid << (28+8) |
              (long) vid <<     8  |
              (long) tag;
            if (CHECKS) check
              (((k >> (28+8)) & 0xFFFffff) == cid,
               ((k >>     8 ) & 0xFFFffff) == vid,
               ((k          ) &      0xff) == tag);
            r = _tagged.get(k);
            if (r == null)
              {
                r = new TaggedValue(this, nc, original, tag);
                if (CHECKS) check
                  (_cachedValues.get(r) == null);
                makeUnique(r);
                _tagged.put(k, r);
              }
          }
        else
          {
            r = new TaggedValue(this, nc, original, tag);
            r = (TaggedValue) cache(r);
          }
      }
    return r;
  }


  /**
   * Create a new or retrieve an existing value of the joined values v and w.
   */
  Value newValueSet(Value v, Value w, int clazz)
  {
    Value res = null;
    var vi = v._id;
    var wi = w._id;
    if (v == w)
      {
        res = v;
      }
    else
      {
        Long k = vi < wi ? (long) vi << 32 | wi & 0x7FFFFFFF
                         : (long) wi << 32 | vi & 0x7FFFFFFF;
        res = _joined.get(k);
        if (res == null)
          {
            if      (v.contains(w)) { res = v; }
            else if (w.contains(v)) { res = w; }
            else
              {
                res = new ValueSet(this, v, w, clazz);
                res = cache(res);
              }
            _joined.put(k, res);
          }
      }
    return res;
  }


  static boolean COMPARE_ONLY_ENV_EFFECTS_THAT_ARE_NEEDED = true;



  static boolean ONLY_ONE_INSTANCE  = true;

  List<Boolean> _onlyOneInstance = new List<>();


  boolean onlyOneInstance(int clazz)
  {
    var result = false;
    if (ONLY_ONE_INSTANCE)
      {
        var cnum = _fuir.clazzId2num(clazz);
        var b = _onlyOneInstance.getIfExists(cnum);
        if (b == null)
          {
            // NYI: UNDER DEVELOPMENT: This is currently a dumb list of features,
            // this should be something generic instead, e.g.
            //
            //   b := !_fuir.clazzIsChoice(clazz) && !_fuir.clazzIsRef(clazz);
            //
            b = switch (_fuir.clazzAsString(clazz))
              {
              case
              "list u8",
              "codepoint",
              "Sequence u8",
              "array u8",
              "fuzion.sys.internal_array u8" -> true;
              default -> false;
              };
            _onlyOneInstance.force(cnum, b);
          }
        result = b;
      }
    return result;
  }


  /**
   * Create new SysArray instance of the given element values and element clazz.
   *
   * NYI: We currently do not distinguish SysArrays by the array instance that
   * contains it.  We probably should do this to make sure that, e.g.,
   *
   *   a3 := array 10 i->3
   *   a4 := array 10 i->4
   *
   *   if (a3[3] != 3)
   *     panic "strange"
   *
   * will never panic.
   *
   * @param ne the element values, null if not initialized
   *
   * @param ec the element clazz.
   *
   * @return a new or existing instance of SysArray.
   */
  SysArray newSysArray(Value ne, int ec)
  {
    SysArray res;
    if (ne == null)
      {
        res = _uninitializedSysArray.get(ec);
        if (res == null)
          {
            res = new SysArray(this, ne, ec);
            _uninitializedSysArray.put(ec, res);
          }
      }
    else
      {
        res = ne._sysArrayOf;
        if (res == null)
          {
            res = new SysArray(this, ne, ec);
            ne._sysArrayOf = res;
          }
      }
    return res;
  }


  /**
   * Create EmbeddedValue for given call/code/index and value.
   *
   * @param site site of the call
   *
   * @param value the value of the embedded field
   */
  public Val newEmbeddedValue(int site,
                              Value value)
  {
    if (PRECONDITIONS) require
      (site != NO_SITE,
       value != null);

    Val r;
    if (!_options.needsEscapeAnalysis() || !USE_EMBEDDED_VALUES || value instanceof NumericValue)
      {
        r = value;
      }
    else
      {
        var e = value._embeddedAt;
        if (e == null)
          {
            e = new IntMap<>();
            value._embeddedAt = e;
          }
        var i = site;
        r = e.get(i);
        if (r == null)
          {
            var ev = new EmbeddedValue(null, site, value);
            e.put(i, ev);
            r = ev;
          }
      }
    return r;
  }



  /**
   * Create EmbeddedValue for given instance and value.
   *
   * @param instance the instance containing this embedded value
   *
   * @param value the value of the embedded field
   */
  public Val newEmbeddedValue(Instance instance,
                              Value value)
  {
    if (PRECONDITIONS) require
      (instance != null,
       value != null,
       value._clazz == NO_CLAZZ || !instance._dfa._fuir.clazzIsRef(value._clazz));

    if (CHECKS) check
      (instance._id >= 0);

    Val r;
    if (!_options.needsEscapeAnalysis() || !USE_EMBEDDED_VALUES || value instanceof NumericValue)
      {
        r = value;
      }
    else
      {
        var e = value._embeddedAt;
        if (e == null)
          {
            e = new IntMap<>();
            value._embeddedAt = e;
          }
        var i = FUIR.SITE_BASE - 1 - instance._id;
        r = e.get(i);
        if (r == null)
          {
            var ev = new EmbeddedValue(instance, NO_SITE, value);
            e.put(i, ev);
            r = ev;
          }
      }
    return r;
  }


  /**
   * Create constant string with given utf8 bytes.
   *
   * @param utf8Bytes the string contents or null if contents unknown
   *
   * @param context for debugging: Reason that causes this const string to be
   * part of the analysis.
   */
  Value newConstString(byte[] utf8Bytes, Context context)
  {
    var cs            = _fuir.clazz_const_string();
    var utf_data      = _fuir.clazz_const_string_utf8_data();
    var ar            = _fuir.clazz_array_u8();
    var internalArray = _fuir.lookup_array_internal_array(ar);
    var data          = _fuir.clazz_fuzionSysArray_u8_data();
    var length        = _fuir.clazz_fuzionSysArray_u8_length();
    var sysArray      = _fuir.clazzResultClazz(internalArray);
    var c_u8          = _fuir.clazz(SpecialClazzes.c_u8);
    var adata         = newSysArray(NumericValue.create(this, c_u8), c_u8);
    var r = newInstance(cs, NO_SITE, context);
    var arr = newInstance(ar, NO_SITE, context);
    var a = newInstance(sysArray, NO_SITE, context);
    a.setField(this,
               length,
                utf8Bytes != null ? NumericValue.create(this, _fuir.clazzResultClazz(length), utf8Bytes.length)
                                  : NumericValue.create(this, _fuir.clazzResultClazz(length)));
    a.setField(this, data  , adata);
    arr.setField(this, internalArray, a);
    r.setField(this, utf_data, arr);
    return r.box(this, cs, _fuir.clazz_ref_const_string(), context);
  }


  /**
   * For a call to cc, should we be site sensitive, i.e., distinguish calls
   * depending on their call site?
   *
   * Currently, we are site sensitive for all constructors or if SITE_SENSITIVE
   * is set via env var or property.
   *
   * @param cc a clazz that is called
   *
   * @return true iff the call site should be taken into account when comparing
   * calls to {@code cc}.
   */
  boolean siteSensitive(int cc)
  {
    return SITE_SENSITIVE || _fuir.isConstructor(cc);
  }


  long callQuickHash(int cl, int site, Value tvalue, Env env)
  {
    long k = -1;
    var k1 = _fuir.clazzId2num(cl);
    var k2 = tvalue._id;
    var k3 = siteSensitive(cl) ? siteIndex(site) : 0;
    var k4 = env == null ? 0 : env._id + 1;
    if (CHECKS) check
      (k1 >= 0,
       k2 >= 0,
       k3 >= 0,
       k4 >= 0);
    // We use a LongMap in case we manage to fiddle k1..k4 into a long
    //
    // try to fit clazz id, tvalue id, siteIndex and env id into long as follows
    //
    // Bit 6666555555555544444444443333333333222222222211111111110000000000
    //     3210987654321098765432109876543210987654321098765432109876543210
    //     <----clazz id----><---tvalue id----><---siteIndex----><-env-id->
    //     |     18 bits    ||     18 bits    ||     18 bits    ||10 bits |
    //
    if (k1 <= 0x3FFFE &&
        k2 <= 0x3FFFE &&
        k3 <= 0x3FFFE &&
        k4 <= 0x03FE)
      {
        k = ((k1 * 0x40000L + k2) * 0x40000L + k3) * 0x400L + k4;
        if (CHECKS) check
          (((k >> (18*2+10)) & 0x3FFFF) == k1,
           ((k >> (18  +10)) & 0x3FFFF) == k2,
           ((k >> (     10)) & 0x3FFFF) == k3,
           ((k               & 0x003FF) == k4));
      }
    return k;
  }


  static int _cntArrayCons = 0;

  /**
   * Create call to given clazz with given target and args.
   *
   * @param cl the called clazz
   *
   * @param site the call site, -1 if unknown (from intrinsic or program entry
   * point)
   *
   * @param tvalue the target value on which cl is called
   *
   * @param args the arguments passed to the call
   *
   * @param env the environment at the call or null if none.
   *
   * @param context for debugging: Reason that causes this call to be part of
   * the analysis.
   *
   * @return cl a new or existing call to cl with the given target, args and
   * environment.
   */
  Call newCall(Call from, int cl, int site, Value tvalue, List<Val> args, Env env, Context context)
  {
    var oenv = env;
    CallGroup g;
    var kg = CallGroup.quickHash(this, cl, site, tvalue);
    if (kg != -1)
      {
        g = _callGroupsQuick.get(kg);
        if (g == null)
          {
            g = new CallGroup(this, cl, site, tvalue);
            _callGroupsQuick.put(kg, g);
          }
      }
    else
      {
        var ng = new CallGroup(this, cl, site, tvalue);
        g = _callGroups.putIfAbsent(ng, ng);
        g = g != null ? g : ng;
      }

    var s = _fuir.clazzAsString(cl);
    if (!s.startsWith("instate_helper ") && !s.startsWith("(instate_helper "))
      {
        env = env == null ? null : env.filterCallGroup(g);
      }

    Call e, r;
    r = _unitCalls.get(cl);
    if (isUnitType(cl))
      { // as long as we see unit values only, we put them all together
        e = r;
        if (r == null)
          {
            r = new Call(g, args, env, context);
            _unitCalls.put(cl, r);
          }
      }
    else
      {
        if (r != null)
          {
            _unitCalls.put(cl, null);
            _calls.remove(r);
          }
        var k = COMPARE_ONLY_ENV_EFFECTS_THAT_ARE_NEEDED ? -1 : callQuickHash(cl, site, tvalue, env);
        if (k != -1)
          {
            r = _callsQuick.get(k);
            e = r;
            if (r == null)
              {
                r = new Call(g, args, env, context);
                _callsQuick.put(k, r);
              }
          }
        else
          {
            if (env != null && !true)
              {
                var fe = _effectsRequiredByClazz.get(cl);
                env = fe == null ? null : env.filterX(fe);
              }
            // TreeMap fallback in case we failed to pack the key into a long.
            //
            // NYI: OPTIMIZATION: We might find a more efficient way for this case,
            // maybe two nested LongMaps?
            r = new Call(g, args, env, context);
            e = _calls.get(r);
            if (env != null && e != null && e._env != null)
              {
                e._env.propagateAbort(env);
              }
          }
      }
    if (e == null)
      {
        r._uniqueCallId = _callIds++;
        if (_callIds < 0)
          {
            DfaErrors.fatal("DFA: Exceeded maximum number of calls " + Integer.MAX_VALUE);
          }
        _calls.put(r, r);
        r._instance = newInstance(cl, site, r);
        if (r._instance instanceof Instance riv)
          {
            riv._group = g;
          }
        else if (r._instance instanceof RefValue rv && rv._original instanceof Instance riv)
          {
            riv._group = g;
          }
        else
          {
            if (CHECKS) check
              (false);
          }
        e = r;
        var rf = r;
        wasChanged(() -> "DFA.newCall to " + rf);
        analyzeNewCall(r);
      }
    else
      {
        e.mergeWith(args);
      }
    if (from != null)
      {
        e._group.calledFrom(from._group);
      }
    return e;
  }


  /**
   * Helper for newCall to analyze a newly created call immediately. This helps
   * to avoid quadratic performance when analyzing a sequence of calls as in
   *
   *  a 1; a 2; a 3; a 4; a 5; ...
   *
   * Since a new call does not return, the analysis would stop for each iteration
   * after the fist new call.
   *
   * However, we cannot analyze all calls immediately since a recursive call
   * would result in an unbounded recursion during DFA.  So this analyzes the
   * call immediately unless it is part of a recursion or there are already
   * MAX_NEW_CALL_RECURSION new calls being analyzed right now.
   *
   * This might run into quadratic performance for code like the code above if
   * {@code a} would itself perform a new call to {@code b}, and {@code b} to {@code c}, etc. to a depth
   * that exceeds MAX_NEW_CALL_RECURSION.
   */
  private void analyzeNewCall(Call e)
  {
    var cnt = _newCallRecursiveAnalyzeCalls;
    var rec = true;
    if (cnt < _newCallRecursiveAnalyzeClazzes.length)
      {
        rec = false;
        for (var i = 0; i<cnt; i++)
          {
            rec = rec || _newCallRecursiveAnalyzeClazzes[i] == e.calledClazz();
          }
      }
    if (!rec)
      {
        _newCallRecursiveAnalyzeClazzes[cnt] = e.calledClazz();
        _newCallRecursiveAnalyzeCalls = cnt + 1;
        analyze(e);
        _newCallRecursiveAnalyzeCalls = cnt ;
      }
    hot(e);
  }


  /**
   * Turn a site id into an index value >= 0.
   *
   * @param s a site id or FUIR.NO_SITE
   *
   * @return a value >= 0 that tends to be relatively small.
   */
  static int siteIndex(int s)
  {
    if (PRECONDITIONS) require
      (s == FUIR.NO_SITE || s >= FUIR.SITE_BASE);

    return s == FUIR.NO_SITE ? 0 : s - FUIR.SITE_BASE + 1;
  }


  /**
   * Create instance of 'Site' for given site
   *
   * @param s a FUIR site
   *
   * @return corresponding Site instance
   */
  Site site(int s)
    {
      var i = siteIndex(s);
      var res = _sites.getIfExists(i);
      if (res == null)
        {
          res = new Site(s);
          _sites.force(i, res);
        }
      return res;
    }


  /**
   * Create new Env for given existing env and effect type  and value pair.
   *
   * @param env the previous environment.
   *
   * @param ecl the effect types
   *
   * @param ev the effect value
   *
   * @return new or existing Env instance created from env by adding ecl/ev.
   */
  Env newEnv(Env env, int ecl, Value ev)
  {
    Env e;
    var eid = env == null ? 0 : env._id;
    var vid = ev._envId;
    if (vid < 0)
      {
        var v = _envValues.get(ev);
        if (v == null)
          {
            _envValues.put(ev, ev);
            ev._envId = _envValues.size();
          }
        else
          {
            ev._envId = v._envId;
          }
        vid = ev._envId;
      }
    var cid = _fuir.clazzId2num(ecl);
    if (CHECKS) check
      (eid >= 0,
       vid >= 0,
       cid >= 0,
       eid <= 0x1fFFFF,
       vid <= 0x3fFFFF,
       cid <= 0x1fFFFF);
    if (eid >= 0 &&
        vid >= 0 &&
        cid >= 0 &&
        eid <= 0x1fFFFF &&
        vid <= 0x3fFFFF &&
        cid <= 0x1fFFFF)
      {
        var k =
          (long) eid << (26+25) |
          (long) vid << (   25) |
          (long) cid;
        e = _envsQuick.get(k);
        if (e == null)
          {
            e = newEnv2(env, ecl, ev);
            _envsQuick.put(k, e);
          }
      }
    else
      {
        e = newEnv2(env, ecl, ev);
      }
    return e;
  }

  SourcePosition effectTypePosition(int ecl)
  {
    // NYI: declarationPos may be equal for two effects declared in different modules, so we have to compare the modules as well!
    var res = _fuir.clazzDeclarationPos(ecl);
    if (res == null)   // NYI: Check if this can be replaced by check(res != null)!
      {
        var cd = _fuir.clazzCode(ecl);
        check
          (cd != FUIR.NO_SITE && _fuir.withinCode(cd));
        res = _fuir.sitePos(cd);
      }
    if (POSTCONDITIONS) ensure
      (res != null);
    return res;
  }


  /**
   * Helper for newEnv without quick caching.
   *
   * @param env the previous environment.
   *
   * @param ecl the effect types
   *
   * @param ev the effect value
   *
   * @return new or existing Env instance created from env by adding ecl/ev.
   */
  private Env newEnv2(Env env, int ecl, Value ev)
  {
    if (env != null)
      {
        env = env.filterPos(effectTypePosition(ecl));
      }
    var newEnv = new Env(this, env, ecl, ev);
    var e = _envs.get(newEnv);
    if (e == null)
      {
        _envs.put(newEnv, newEnv);
        e = newEnv;
        e._id = _envs.size()+1;
        wasChanged(() -> "DFA.newEnv for " + newEnv);
      }
    return e;
  }

}

/* end of file */
