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
 * Source of class Executor
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import dev.flang.fuir.FUIR;
import dev.flang.fuir.analysis.AbstractInterpreter;
import dev.flang.fuir.analysis.AbstractInterpreter.ProcessExpression;

import dev.flang.fuir.analysis.TailCall;
import dev.flang.util.Errors;
import dev.flang.util.FuzionOptions;
import dev.flang.util.List;
import dev.flang.util.Pair;
import dev.flang.util.StringHelpers;


/**
 * The class Executor implements ProcessExpression of the AbstractInterpreter.
 * It does the actual execution of the code.
 */
public class Executor extends ProcessExpression<Value, Object>
{


  /*-----------------------------  static fields  -----------------------------*/


  /**
   * Universe instance
   */
  private static Instance _universe;


  /**
   * The fuir to be used for executing the code.
   */
  private static FUIR _fuir;


  /**
   * The tail call analysis.
   */
  private static TailCall _tailCall;


  /**
   * The current options for compiling and running the application.
   */
  private static FuzionOptions _options_;



  /*-----------------------------  instance fields  -----------------------------*/



  /**
   * List that holds the args to be returned by args().
   */
  private final List<Value> _args;


  /**
   * The current instance to be returned by current().
   */
  private final Instance _cur;


  /**
   * The current outer to be returned by outer().
   */
  private final Value _outer;


  /*-----------------------------  constructors  -----------------------------*/


  /**
   * The constructor to initialize the Executor
   * at the start of the application.
   */
  public Executor(FUIR fuir, FuzionOptions opt)
  {
    _fuir = fuir;
    _options_ = opt;
    _universe = new Instance(_fuir.clazzUniverse());
    _tailCall = new TailCall(fuir);
    this._cur = _fuir.mainClazzId() == _fuir.clazzUniverse() ? _universe : new Instance(_fuir.mainClazzId());
    this._outer = _universe;
    this._args = new List<>();
  }


  /**
   * The constructor to initialize the executor
   * with a custom current, outer and args.
   *
   * @param fuir
   * @param cur
   * @param outer
   * @param args
   */
  public Executor(Instance cur, Value outer, List<Value> args)
  {
    this._cur = cur;
    this._outer = outer;
    this._args = args;
  }



  /*-----------------------------  methods  -----------------------------*/



  /*
   * For obtaining the current FUIR by
   * accessing the private static field _fuir.
   */
  public FUIR fuir()
  {
    if (PRECONDITIONS) require
    (_fuir != null);

    return _fuir;
  }


  /*
   * For obtaining the current FuzionOptions by
   */
  public FuzionOptions options()
  {
    if (PRECONDITIONS) require
      (_options_ != null);

    return _options_;
  }


  @Override
  public Object sequence(List<Object> l)
  {
    return null;
  }

  @Override
  public Value unitValue()
  {
    return Value.EMPTY_VALUE;
  }

  @Override
  public Object expressionHeader(int s)
  {
    return null;
  }

  @Override
  public Object comment(String s)
  {
    return null;
  }

  @Override
  public Object nop()
  {
    return null;
  }

  @Override
  public Object assignStatic(int s, int tc, int f, int rt, Value tvalue, Value val)
  {
    if (!(_fuir.clazzIsOuterRef(f) && _fuir.clazzIsUnitType(rt)))
      {
        Interpreter.setField(f, tc, tvalue, val);
      }
    return null;
  }

  @Override
  public Object assign(int s, Value tvalue, Value avalue)
  {
    // NYI: better check clazz containing field is universe
    if (tvalue == unitValue())
      {
        tvalue = _universe;
      }
    if (avalue != unitValue())
      {
        var ttcc = ttcc(s, tvalue);
        var tt = ttcc.v0();
        var cc = ttcc.v1();
        Interpreter.setField(cc, tt, tvalue, avalue);
      }
    return null;
  }

  @Override
  public Pair<Value, Object> call(int s, Value tvalue, List<Value> args)
  {
    var cc0 = _fuir.accessedClazz(s);
    var rt = _fuir.clazzResultClazz(cc0);
    var ttcc = ttcc(s, tvalue);
    var tt = ttcc.v0();
    var cc = ttcc.v1();

    // NYI: abstract interpreter should probably not give us boxed values
    // in this case
    if(_fuir.clazzIsBoxed(tt) && !_fuir.clazzIsRef(_fuir.clazzOuterClazz(cc /* NYI should this be cc0? */)))
      {
        tt = ((Boxed)tvalue)._valueClazz;
        tvalue = ((Boxed)tvalue)._contents;
      }

    var cl = _fuir.clazzAt(s);
    if (cc == cl // calling myself
        && _tailCall.callIsTailCall(cl, s))
      {
        throw new TailCallException(tvalue, args);
      }

    var result = switch (_fuir.clazzKind(cc))
      {
      case Routine :
        // NYI change call to pass in ai as in match expression?
        var cur = callOnNewInstance(s, cc, tvalue, args);

        Value rres = cur;
        if (!_fuir.isConstructor(cc))
          {
            var rfc = _fuir.clazzResultField(cc);
            if (!AbstractInterpreter.clazzHasUnitValue(_fuir, fuir().clazzResultClazz(rfc)))
              {
                rres = Interpreter.getField(rfc, cc, cur, false);
              }
          }
        yield pair(rres);
      case Field :
        var fc = cc;
        var fres = AbstractInterpreter.clazzHasUnitValue(_fuir, rt)
          ? pair(unitValue())
          : pair(Interpreter.getField(fc, tt, tt == _fuir.clazzUniverse() ? _universe : tvalue, false));

        if (CHECKS)
          check(fres != null, AbstractInterpreter.clazzHasUnitValue(_fuir, rt) || fres.v0() != unitValue());

        yield fres;
      case Intrinsic :
        yield _fuir.clazzTypeParameterActualType(cc) != -1  /* type parameter is also of Kind Intrinsic, NYI: CLEANUP: should better have its own kind?  */
          ? pair(unitValue())
          : pair(Intrinsics.call(this, cc).call(new List<>(tvalue, args)));
      case Abstract:
        throw new Error("Calling abstract not possible: " + _fuir.codeAtAsString(s));
      case Choice :
        throw new Error("Calling choice not possible: " + _fuir.codeAtAsString(s));
      case Native:
        throw new Error("NYI: UNDER DEVELOPMENT: Calling native not yet supported in interpreter.");
      };

    return result;
  }


  /**
   * Get the target clazz and the called clazz as a Pair of two Integers.
   *
   * @param tvalue the actual value of the target.
   */
  private Pair<Integer, Integer> ttcc(int s, Value tvalue)
  {
    int cc, tt;
    if (_fuir.accessIsDynamic(s))
      {
        cc = -1;
        tt = ((ValueWithClazz)tvalue)._clazz;
        var ccs = _fuir.accessedClazzes(s);
        for (var cci = 0; cci < ccs.length && cc==-1; cci += 2)
          {
            if (ccs[cci] == tt)
              {
                cc = ccs[cci+1];
              }
          }
      }
    else
      {
        cc = _fuir.accessedClazz(s);
        tt = _fuir.clazzOuterClazz(cc);
      }

    if (POSTCONDITIONS) ensure
      (cc != -1);

    return new Pair<>(tt, cc);
  }


  /**
   * wrap `v` into a `Pair<>(v, null)`
   *
   * @param v
   * @return
   */
  static Pair<Value, Object> pair(Value v)
  {
    return new Pair<Value,Object>(v, null);
  }


  @Override
  public Pair<Value, Object> box(int s, Value v, int vc, int rc)
  {
    return pair(new Boxed(rc, vc, v /* .cloneValue(vcc) */));
  }

  @Override
  public Pair<Value, Object> current(int s)
  {
    return pair(_cur);
  }

  @Override
  public Pair<Value, Object> outer(int s)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzResultClazz(_fuir.clazzOuterRef(_fuir.clazzAt(s))) == _fuir.clazzOuterClazz(_fuir.clazzAt(s)));

    return pair(_outer);
  }

  @Override
  public Value arg(int s, int i)
  {
    return _args.get(i);
  }

  @Override
  public Pair<Value, Object> constData(int s, int constCl, byte[] d)
  {
    // NYI: UNDERDEVELOPMENT: cache?
    var val = switch (_fuir.getSpecialClazz(constCl))
      {
      case c_Const_String, c_String -> Interpreter
        .value(new String(Arrays.copyOfRange(d, 4, ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getInt() + 4), StandardCharsets.UTF_8));
      case c_bool -> { check(d.length == 1, d[0] == 0 || d[0] == 1); yield new boolValue(d[0] == 1); }
      case c_f32 -> new f32Value(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getFloat());
      case c_f64 -> new f64Value(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getDouble());
      case c_i16 -> new i16Value(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getShort());
      case c_i32 -> new i32Value(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getInt());
      case c_i64 -> new i64Value(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getLong());
      case c_i8  -> new i8Value (ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).get());
      case c_u16 -> new u16Value(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getChar());
      case c_u32 -> new u32Value(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getInt());
      case c_u64 -> new u64Value(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getLong());
      case c_u8  -> new u8Value (ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).get() & 0xff);
      default -> {
        if (_fuir.clazzIsArray(constCl))
          {
            var elementType = _fuir.inlineArrayElementClazz(constCl);

            var bb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
            var elCount = bb.getInt();

            var arrayData = ArrayData.alloc(elCount, _fuir, elementType);

            for (int idx = 0; idx < elCount; idx++)
              {
                var b = _fuir.deseralizeConst(elementType, bb);
                var c = constData(s, elementType, b).v0();
                switch (_fuir.getSpecialClazz(elementType))
                  {
                    case c_i8   : ((byte[])   (arrayData._array))[idx] = (byte)c.i8Value(); break;
                    case c_i16  : ((short[])  (arrayData._array))[idx] = (short)c.i16Value(); break;
                    case c_i32  : ((int[])    (arrayData._array))[idx] = c.i32Value(); break;
                    case c_i64  : ((long[])   (arrayData._array))[idx] = c.i64Value(); break;
                    case c_u8   : ((byte[])   (arrayData._array))[idx] = (byte)c.u8Value(); break;
                    case c_u16  : ((char[])   (arrayData._array))[idx] = (char)c.u16Value(); break;
                    case c_u32  : ((int[])    (arrayData._array))[idx] = c.u32Value(); break;
                    case c_u64  : ((long[])   (arrayData._array))[idx] = c.u64Value(); break;
                    case c_f32  : ((float[])  (arrayData._array))[idx] = c.f32Value(); break;
                    case c_f64  : ((double[]) (arrayData._array))[idx] = c.f64Value(); break;
                    case c_bool : ((boolean[])(arrayData._array))[idx] = c.boolValue(); break;
                    default     : ((Value[])  (arrayData._array))[idx] = c;
                  }
              }

            Instance result = new Instance(constCl);
            var internalArray = _fuir.lookup_array_internal_array(constCl);
            var sysArray = _fuir.clazzResultClazz(internalArray);
            var saCl = sysArray;
            Instance sa = new Instance(saCl);
            Interpreter.setField(_fuir.lookup_fuzion_sys_internal_array_length(sysArray), saCl,                               sa,     new i32Value(elCount));
            Interpreter.setField(_fuir.lookup_fuzion_sys_internal_array_data(sysArray)  , saCl,                               sa,     arrayData);
            Interpreter.setField(internalArray                                          , constCl,                            result, sa);
            yield result;
          }
        else if (!_fuir.clazzIsChoice(constCl))
          {
            var b = ByteBuffer.wrap(d);
            var result = new Instance(constCl);
            for (int index = 0; index < _fuir.clazzArgCount(constCl); index++)
              {
                var fr = _fuir.clazzArgClazz(constCl, index);

                var bytes = _fuir.deseralizeConst(fr, b);
                var c = constData(s, fr, bytes).v0();
                var acl = _fuir.clazzArg(constCl, index);
                Interpreter.setField(acl, constCl, result, c);
              }

            yield result;
          }
        else
          {
            Errors.error("Unsupported constant.",
                         "Backend cannot handle constant of clazz '" + _fuir.clazzAsString(constCl) + "' ");
            yield null;
          }
      }
      };

    return pair(val);
  }

  @Override
  public Pair<Value, Object> match(int s, AbstractInterpreter<Value, Object> ai, Value subv)
  {
    var staticSubjectClazz = subv instanceof boolValue ? fuir().clazz(FUIR.SpecialClazzes.c_bool) : ((ValueWithClazz)subv)._clazz;

    if (CHECKS) check
      (fuir().clazzIsChoice(staticSubjectClazz));

    var tagAndChoiceElement = tagAndVal(staticSubjectClazz, subv);

    var cix = _fuir.matchCaseIndex(s, tagAndChoiceElement.v0());

    var field = _fuir.matchCaseField(s, cix);
    if (field != -1)
      {
        Interpreter.setField(
            field,
            _cur._clazz,
            _cur,
            tagAndChoiceElement.v1());
      }
    return ai.processCode(_fuir.matchCaseCode(s, cix));
  }


  /**
   * @param staticSubjectClazz the clazz of the subject, a choice
   *
   * @param sub the subjects current value
   *
   * @return pair where first value is the tag, the second value the extracted value.
   */
  private Pair<Integer, Value> tagAndVal(int staticSubjectClazz, Value sub)
  {
    if (PRECONDITIONS) require
      (fuir().clazzIsChoice(staticSubjectClazz));

    var tag = -1;
    Value val = null;
    if (fuir().clazzIsChoiceOfOnlyRefs(staticSubjectClazz))
      {
        val = Interpreter.getChoiceRefVal(staticSubjectClazz, staticSubjectClazz, sub);
        tag = ChoiceIdAsRef.tag(staticSubjectClazz, val);
      }
    else if (staticSubjectClazz == fuir().clazz(FUIR.SpecialClazzes.c_bool))
      {
        tag = sub.boolValue() ? 1 : 0;
        val = sub;
      }
    else
      {
        tag = sub.tag();
      }
    if (val == null)
      {
        val = Interpreter.getChoiceVal(staticSubjectClazz, staticSubjectClazz, sub, tag);
      }

    if (POSTCONDITIONS) ensure
      (tag != -1 && val != null);

    return new Pair<Integer, Value>(tag, val);
  }

  @Override
  public Pair<Value, Object> tag(int s, Value value, int newcl, int tagNum)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzIsChoice(newcl));

    var tc = _fuir.clazzChoice(newcl, tagNum);

    return pair(Interpreter.tag(newcl, tc, value, tagNum));
  }

  @Override
  public Pair<Value, Object> env(int s, int ecl)
  {
    var result = FuzionThread.current()._effects.get(ecl);
    if (result == null)
      {
        Errors.fatal("No effect installed: " + _fuir.clazzAsStringHuman(ecl));
      }

    if (POSTCONDITIONS) ensure
      (fuir().clazzIsUnitType(ecl) || result != unitValue());

    return pair(result);
  }


  /**
   * Generate code to terminate the execution immediately.
   *
   * @param msg a message explaining the illegal state
   */
  @Override
  public Object reportErrorInCode(String msg)
  {
    say_err(msg);
    System.exit(1);
    return null;
  }


  /**
   * callOnInstance assigns the arguments to the argument fields of a newly
   * created instance, calls the parents and then this feature.
   *
   * @param s site of the call or NO_SITE if unknown (e.g., form intrinsic)
   *
   * @param cc clazz id of the called clazz
   *
   * @param outer the target of the call
   *
   * @param args the arguments to be passed to this call.
   *
   * @return the (new) instance (might have been replaced due to tail call optimization).
   */
  Instance callOnNewInstance(int s, int cc, Value outer, List<Value> args)
  {
    FuzionThread.current()._callStackFrames.push(cc);
    FuzionThread.current()._callStack.push(s);

    var o = outer;
    var a = args;
    var cur = new Instance(cc);
    while (o != null)
      {
        try
          {
            new AbstractInterpreter<>(_fuir, new Executor(cur, o, a))
              .processClazz(cc);
            o = null;
          }
        catch(TailCallException tce)
          {
            cur = new Instance(cc);
            o = tce.tvalue;
            a = tce.args;
          }
      }

    FuzionThread.current()._callStack.pop();
    FuzionThread.current()._callStackFrames.pop();

    return cur;
  }


  /**
   * Helper for callStack() to show one single frame
   *
   * @param frame the clazz of the entry to show
   *
   * @param call the call of the entry to show
   */
  private static void showFrame(FUIR fuir, StringBuilder sb, int frame, int callSite)
  {
    if (frame != -1)
      {
        sb.append(_fuir.clazzAsStringHuman(frame)).append(": ");
      }
    sb.append(_fuir.sitePos(callSite).show()).append("\n");
  }


  /**
   * Helper for callStack() to show a repeated frame
   *
   * @param sb used to append the output
   *
   * @param repeat how often was the previous entry repeated? >= 0 where 0 means
   * it was not repeated, just appeared once, 1 means it was repeated once, so
   * appeared twice, etc.
   *
   * @param frame the clazz of the previous entry
   *
   * @param call the call of the previous entry
   */
  private static void showRepeat(FUIR fuir, StringBuilder sb, int repeat, int frame, int callSite)
  {
    if (repeat > 1)
      {
        sb.append(StringHelpers.repeated(repeat)).append("\n\n");
      }
    else if (repeat > 0)
      {
        showFrame(fuir, sb, frame, callSite);
      }
  }


  /**
   * Current call stack as a string for debugging output.
   */
  public static String callStack(FUIR fuir)
  {
    StringBuilder sb = new StringBuilder("Call stack:\n");
    int lastFrame = -1;
    int lastCall = -1;
    int repeat = 0;
    var s = FuzionThread.current()._callStack;
    var sf = FuzionThread.current()._callStackFrames;
    for (var i = s.size()-1; i >= 0; i--)
      {
        int frame = i<sf.size() ? sf.get(i) : null;
        var call = s.get(i);
        if (frame == lastFrame && call == lastCall)
          {
            repeat++;
          }
        else
          {
            showRepeat(fuir, sb, repeat, lastFrame, lastCall);
            repeat = 0;
            showFrame(fuir, sb, frame, call);
            lastFrame = frame;
            lastCall = call;
          }
      }
    showRepeat(fuir, sb, repeat, lastFrame, lastCall);
    return sb.toString();
  }


}
