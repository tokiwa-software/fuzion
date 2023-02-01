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
 * Source of class Interpreter
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FatalError;
import dev.flang.util.FuzionOptions;
import dev.flang.util.List;

import dev.flang.fuir.FUIR;

import dev.flang.air.Clazz;
import dev.flang.air.Clazzes;

import dev.flang.ast.AbstractAssign; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.AbstractBlock; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.AbstractCall; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.AbstractConstant; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.AbstractCurrent; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.AbstractFeature; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.AbstractMatch; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.AbstractType; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Box; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Check; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Env; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Expr; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.If; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.InlineArray; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Nop; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Stmnt; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Tag; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Types; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Unbox; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Universe; // NYI: remove dependency! Use dev.flang.fuir instead.


/**
 * Interpreter contains interpreter for Fuzion application that is present as
 * intermediate code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Interpreter extends ANY
{


  /*-----------------------------  statics  -----------------------------*/


  /**
   * User specified options. In particular, debugLevel and safety is needed by
   * the backend.
   */
  static FuzionOptions _options_;


  /**
   * Helper for callStack() to show one single frame
   *
   * @param frame the clazz of the entry to show
   *
   * @param call the call of the entry to show
   */
  private static void showFrame(StringBuilder sb, Clazz frame, AbstractCall call)
  {
    if (frame != null)
      {
        sb.append(frame).append(": ");
      }
    sb.append(call.pos().show()).append("\n");
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
   * @param call the call of the pevious entry
   */
  private static void showRepeat(StringBuilder sb, int repeat, Clazz frame, AbstractCall call)
  {
    if (repeat > 1)
      {
        sb.append("...  repeated ").append(repeat).append(" times  ...\n\n");
      }
    else if (repeat > 0)
      {
        showFrame(sb, frame, call);
      }
  }


  /**
   * Current call stack as a string for debugging output.
   */
  public static String callStack()
  {
    StringBuilder sb = new StringBuilder("Call stack:\n");
    Clazz lastFrame = null;
    AbstractCall lastCall = null;
    int repeat = 0;
    var s = FuzionThread.current()._callStack;
    var sf = FuzionThread.current()._callStackFrames;
    for (var i = s.size()-1; i >= 0; i--)
      {
        Clazz frame = i<sf.size() ? sf.get(i) : null;
        var call = s.get(i);
        if (frame == lastFrame && call == lastCall)
          {
            repeat++;
          }
        else
          {
            showRepeat(sb, repeat, lastFrame, lastCall);
            repeat = 0;
            showFrame(sb, frame, call);
            lastFrame = frame;
            lastCall = call;
          }
      }
    showRepeat(sb, repeat, lastFrame, lastCall);
    return sb.toString();
  }

  static Map<AbstractConstant, Value> _cachedConsts_ = new HashMap<>();


  /*----------------------------  variables  ----------------------------*/


  /**
   * The intermediate representation of the code we are interpreting.
   */
  public final FUIR _fuir;


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create an interpreter to execute the given intermediate code.
   */
  public Interpreter(FuzionOptions options, FUIR fuir)
  {
    Intrinsics.ENABLE_UNSAFE_INTRINSICS = options.enableUnsafeIntrinsics();  // NYI: Add to Fuzion IR or BE Config
    _options_ = options;
    _fuir = fuir;
    Errors.showAndExit();
    Clazzes.showStatistics();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Run the application with the given args
   *
   * @param args -- NYI: command line args not supported yet
   */
  public void run()
  {
    for (var cl : Clazzes.all())
      {
        DynamicBinding db = null;
        for (var e : cl._inner.entrySet())
          {
            if (db == null)
              {
                db = new DynamicBinding(cl);
                cl._dynamicBinding = db;
              }
            if (e.getValue() instanceof Clazz innerClazz)
              {
                db.add(this, e.getKey(), innerClazz, cl);
              }
          }
      }

    if (Errors.count() == 0)
      {
        ArrayList<Value> mainargs = new ArrayList<>();
        mainargs.add(Instance.universe); // outer instance
        // mainargs.add(null); // NYI: args
        try
          {
            callable(false, _fuir.main(), Clazzes.universe.get()).call(mainargs);
          }
        catch (FatalError e)
          {
            throw e;
          }
        catch (StackOverflowError e)
          {
            Errors.fatal("*** " + e + "\n" + callStack());
          }
        catch (RuntimeException | Error e)
          {
            Errors.error("*** " + e + "\n" + callStack());
            throw e;
          }
        if (CHECKS) check
          (Errors.count() == 0);
      }
  }


  /*----------------  methods to execute statments  ----------------*/


  /**
   * From a value val of type valueClazz which is in
   * choiceClazz.choiceGenerics_, create a new value of type choiceClazz
   * consisting of val and the choice tag.
   *
   * @param choiceClazz the choice clazz the result should have
   *
   * @param valueClazz the static type of val
   *
   * @param val the value
   *
   * @return a new value of type choiceClazz containing val.
   */
  static Value tag(Clazz choiceClazz, Clazz valueClazz, Value val)
  {
    if (PRECONDITIONS) require
      (choiceClazz.isChoice());

    var result  = new Instance(choiceClazz);
    LValue slot = result.at(choiceClazz, 0); // NYI: needed? just result?
    setChoiceField(choiceClazz.feature(),
                   choiceClazz,
                   slot,
                   valueClazz._type,
                   val);
    return result;
  }


  /**
   * Temporary interpreter, will be replaced by dynamic calls.
   *
   * This is fairly inefficient compared to dynamic
   * binding and it uses way too much stack since recursion keeps this giant
   * stack frame alive.
   */
  public Value execute(Stmnt s, Clazz staticClazz, Value cur)
  {
    Value result;
    if (s instanceof AbstractCall c)
      {
        if (PRECONDITIONS) require
          (!c.isInheritanceCall(),  // inheritance calls are handled in Fature.callOnInstance
           c._sid >= 0);

        ArrayList<Value> args = executeArgs(c, staticClazz, cur);
        FuzionThread.current()._callStack.push(c);

        var d = staticClazz.getRuntimeData(c._sid + 0);
        if (d instanceof Clazz innerClazz)
          {
            var tclazz = (Clazz) staticClazz.getRuntimeData(c._sid + 1);
            var dyn = (tclazz.isRef() || c.target().isCallToOuterRef() && tclazz.isUsedAsDynamicOuterRef()) && c.isDynamic();
            d = callable(dyn, innerClazz, tclazz);
            if (d == null)
              {
                d = "dyn"; // anything else, null would also do, but could be confused with 'not initialized'
              }
            staticClazz.setRuntimeData(c._sid + 0, d);  // cache callable
          }
        Callable ca;
        if (d instanceof Callable dca)
          {
            ca = dca;
          }
        else // if (d == "dyn")
          {
            var v = (ValueWithClazz) args.get(0);
            Clazz cl = v.clazz();
            var db = (DynamicBinding) cl._dynamicBinding;
            ca = (Callable) db.callable(c.calledFeature());
          }
        result = ca.call(args);
        FuzionThread.current()._callStack.pop();
      }

    else if (s instanceof AbstractCurrent)
      {
        result = cur;
      }

    else if (s instanceof AbstractAssign a)
      {
        Value v    = execute(a._value , staticClazz, cur);
        Value thiz = execute(a._target, staticClazz, cur);
        Clazz sClazz = staticClazz.getRuntimeClazz(a._tid + 0);
        setField(a._assignedField, -1, sClazz, thiz, v);
        result = Value.NO_VALUE;
      }

    else if (s instanceof AbstractConstant i)
      {
        result = _cachedConsts_.get(i);
        if (result == null)
          {
            var t = i.type();
            var d = i.data();
            if      (t.compareTo(Types.resolved.t_bool  ) == 0) { result = new boolValue(d[0] != 0                                                           ); }
            else if (t.compareTo(Types.resolved.t_i8    ) == 0) { result = new i8Value  (ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).get      ()       ); }
            else if (t.compareTo(Types.resolved.t_i16   ) == 0) { result = new i16Value (ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getShort ()       ); }
            else if (t.compareTo(Types.resolved.t_i32   ) == 0) { result = new i32Value (ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getInt   ()       ); }
            else if (t.compareTo(Types.resolved.t_i64   ) == 0) { result = new i64Value (ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getLong  ()       ); }
            else if (t.compareTo(Types.resolved.t_u8    ) == 0) { result = new u8Value  (ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).get      () & 0xff); }
            else if (t.compareTo(Types.resolved.t_u16   ) == 0) { result = new u16Value (ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getChar  ()       ); }
            else if (t.compareTo(Types.resolved.t_u32   ) == 0) { result = new u32Value (ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getInt   ()       ); }
            else if (t.compareTo(Types.resolved.t_u64   ) == 0) { result = new u64Value (ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getLong  ()       ); }
            else if (t.compareTo(Types.resolved.t_f32   ) == 0) { result = new f32Value (ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getFloat ()       ); }
            else if (t.compareTo(Types.resolved.t_f64   ) == 0) { result = new f64Value (ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getDouble()       ); }
            else if (t.compareTo(Types.resolved.t_string) == 0) { result = value(new String(d, StandardCharsets.UTF_8));                                        }
            else                                                { result = Value.NO_VALUE; check(false); }
            _cachedConsts_.put(i, result);
          }
      }

    else if (s instanceof AbstractBlock b)
      {
        result = Value.NO_VALUE;
        for (Stmnt stmnt : b._statements)
          {
            result = execute(stmnt, staticClazz, cur);
          }
      }

    else if (s instanceof If i)
      {
        Value c = execute(i.cond, staticClazz, cur);
        if (c.boolValue())
          {
            result = execute(i.block, staticClazz,cur);
          }
        else if (i.elseBlock != null)
          {
            result = execute(i.elseBlock, staticClazz,cur);
          }
        else if (i.elseIf != null)
          {
            result = execute(i.elseIf, staticClazz,cur);
          }
        else
          {
            result = Value.NO_VALUE;
          }
      }

    else if (s instanceof AbstractMatch m)
      {
        result = null;
        Clazz staticSubjectClazz = staticClazz.getRuntimeClazz(m._runtimeClazzId);
        staticSubjectClazz = staticSubjectClazz.asValue(); /* asValue since subject in , e.g., 'match (bool.this)' may be 'ref bool'
                                                            * NYI: might be better to store asValue directly at getRuntimeClazz(m.runtimeClazzId_)
                                                            */
        Value sub = execute(m.subject(), staticClazz, cur);
        var sf = staticSubjectClazz.feature();
        int tag;
        Value refVal = null;
        if (staticSubjectClazz.isChoiceOfOnlyRefs())
          {
            refVal = getChoiceRefVal(sf, staticSubjectClazz, sub);
            tag = ChoiceIdAsRef.get(staticSubjectClazz, refVal);
          }
        else if (staticSubjectClazz == Clazzes.bool.get())
          {
            tag = sub.boolValue() ? 1 : 0;
          }
        else
          {
            tag = getField(sf.choiceTag(), staticSubjectClazz, sub, false).i32Value();
          }
        Clazz subjectClazz = tag < 0
          ? ((ValueWithClazz) refVal).clazz()
          : staticSubjectClazz.getChoiceClazz(tag);

        var it = m.cases().iterator();
        boolean matches = false;
        do
          {
            var c = it.next();

            if (c.field() != null && Clazzes.isUsed(c.field(), staticClazz))
              {
                Clazz fieldClazz = staticClazz.getRuntimeClazz(c._runtimeClazzId).resultClazz();
                if (fieldClazz.isDirectlyAssignableFrom(subjectClazz))
                  {
                    Value v = tag < 0 ? refVal
                                      : getChoiceVal(sf, staticSubjectClazz, sub, tag);
                    setField(c.field(), -1, staticClazz, cur, v);
                    matches = true;
                  }
              }
            else
              {
                var nt = c.field() != null ? 1 : c.types().size();
                for (int i = 0; !matches && i < nt; i++)
                  {
                    Clazz caseClazz = staticClazz.getRuntimeClazz(c._runtimeClazzId + i);
                    matches = caseClazz.isDirectlyAssignableFrom(subjectClazz);
                  }
              }
            if (matches)
              {
                result = execute(c.code(), staticClazz, cur);
              }
          }
        while (!matches && it.hasNext());

        if (!matches)
          {
            var permitted = new List<Clazz>();
            for (var c : m.cases())
              {
                if (c.field() != null)
                  {
                    permitted.add(staticClazz.getRuntimeClazz(c._runtimeClazzId).resultClazz());
                  }
                else
                  {
                    for (int i = 0; i < c.types().size(); i++)
                      {
                        permitted.add(staticClazz.getRuntimeClazz(c._runtimeClazzId + i));
                      }
                  }
              }
            Errors.fatal(m.pos(), "no match found",
                         "For value of clazz: " + subjectClazz + "\n" +
                         "Permitted clazzes: " + permitted.toString("",", ","") + "\n" +
                         callStack());
          }
        if (CHECKS) check
          (matches);

      }

    else if (s instanceof Unbox u)
      {
        // This is a NOP here since values of reference type and value type are
        // treated the same way by the interpreter.
        result = execute(u._adr, staticClazz, cur);
      }

    else if (s instanceof Universe)
     {
       result = Instance.universe;
     }

    else if (s instanceof Box b)
      {
        Value val = execute(b._value, staticClazz, cur);
        var id = b._valAndRefClazzId;
        Clazz vc = id < 0 ? null : (Clazz) staticClazz.getRuntimeData(id);
        Clazz rc = id < 0 ? null : (Clazz) staticClazz.getRuntimeData(id + 1);
        if (id < 0 || vc.isRef() || !rc.isRef())
          { // vc's type is a generic argument or outer type whose actual type
            // does not need boxing
            if (CHECKS) check
              (vc == rc);

            result = val;
          }
        else
          {
            // NYI: split this up into one statement that creates the new instance
            // followed by several instances of Assign that copy the fields.
            var ri = new Instance(rc);
            result = ri;
            for (var f : vc._clazzForField.keySet())
              {
                // Fields select()ed from fields of open generic type have type t_unit
                // if the actual clazz does not have the number of actual open generic
                // parameters.
                if (vc.actualType(f.resultType()).compareTo(Types.resolved.t_unit) != 0)
                  {
                    // see tests/redef_args and issue #86 for a case where this lookup is needed:
                    f = vc.lookup(f, b).feature();
                    if (Clazzes.isUsed(f, vc))
                      {
                        Value v = getField(f, vc, val, true /* allow for uninitialized ref field */);
                        // NYI: Check that this works well for internal fields such as choice tags.
                        if (v != null)
                          {
                            setField(f, -1, rc, result, v);
                          }
                      }
                  }
              }
            if (vc.isChoice())
              {
                if (CHECKS) check
                  (rc.isChoice());

                var vl = Layout.get(vc);
                var rl = Layout.get(rc);
                var voff = 0;
                var roff = 0;
                var vsz  = vl.size();
                if (CHECKS) check
                  (rl.size() == vsz);
                if (val instanceof LValue lv)
                  {
                    voff += lv.offset;
                    val   = lv.container;
                  }
                if (val instanceof boolValue)
                  {
                    val.storeNonRef(new LValue(Clazzes.bool.get(), ri, roff), Layout.get(Clazzes.bool.get()).size());
                  }
                else
                  {
                    if (CHECKS) check
                      (!rc.isChoiceOfOnlyRefs() || vsz == 1);
                    var vi = (Instance) val;
                    for (int i = 0; i<vsz; i++)
                      {
                        ri.refs   [roff+i] = vi.refs   [voff+i];
                        ri.nonrefs[roff+i] = vi.nonrefs[voff+i];
                      }
                  }
              }
          }
      }

    else if (s instanceof Tag t)
      {
        Value v      = execute(t._value, staticClazz, cur);
        Clazz vClazz = staticClazz.getRuntimeClazz(t._valAndTaggedClazzId + 0);
        Clazz tClazz = staticClazz.getRuntimeClazz(t._valAndTaggedClazzId + 1);
        result = tag(tClazz, vClazz, v);
      }

    else if (s instanceof Check c)
      {
        // NYI: check not supported yet
        // System.err.println("NYI: "+c);

        result = Value.NO_VALUE;
      }

    else if (s instanceof Nop)
      {
        result = Value.NO_VALUE;
      }

    else if (s instanceof InlineArray i)
      {
        Clazz ac  = staticClazz.getRuntimeClazz(i._arrayClazzId + 0);
        Clazz sac = staticClazz.getRuntimeClazz(i._arrayClazzId + 1);
        var sa = new Instance(sac);
        int l = i._elements.size();
        var arrayData = Intrinsics.fuzionSysArrayAlloc(l, sac);
        setField(Types.resolved.f_fuzion_sys_array_data  , -1, sac, sa, arrayData);
        setField(Types.resolved.f_fuzion_sys_array_length, -1, sac, sa, new i32Value(l));
        for (int x = 0; x < l; x++)
          {
            var v = execute(i._elements.get(x), staticClazz, cur);
            Intrinsics.fuzionSysArraySetEl(arrayData, x, v, sac);
          }
        result = new Instance(ac);
        setField(Types.resolved.f_array_internalArray, -1, ac, result, sa);
      }

    else if (s instanceof Env v)
      {
        Clazz vClazz = staticClazz.getRuntimeClazz(v._clazzId);
        result = FuzionThread.current()._effects.get(vClazz);
        if (result == null)
          {
            Errors.fatal("*** effect for " + vClazz + " not present in current environment\n" +
                         "    available are " + FuzionThread.current()._effects.keySet() + "\n" +
                         callStack());
          }
      }

    else
      {
        throw new Error("Execution of " + s.getClass() + " not implemented");
      }
    return result;
  }


  /**
   * Execute the code to evaluate the arguments to this call and return the argument values.
   *
   * @param staticClazz the static clazz of the current object of the call
   *
   * @param target_cur the current object to evaluate the target of the call.
   *
   * @return the evaluated arguments
   */
  public ArrayList<Value> executeArgs(AbstractCall c,
                                      Clazz staticClazz,
                                      Value cur)
  {
    Value targt = execute(c.target(), staticClazz, cur);
    ArrayList<Value> args = new ArrayList<>();
    args.add(targt);
    for (Expr e: c.actuals())
      {
        args.add(execute(e, staticClazz, cur));
      }
    return args;
  }


  /*----------------  methods to create constant values  ----------------*/

  /**
   * Create runtime value of given String constant.
   *
   * @str the string in UTF-16
   */
  public static Value value(String str)
  {
    Clazz cl = Clazzes.conststring.get();
    Instance result = new Instance(cl);
    var saCl = Clazzes.fuzionSysArray_u8;
    Instance sa = new Instance(saCl);
    byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
    setField(Types.resolved.f_fuzion_sys_array_length, -1, saCl, sa, new i32Value(bytes.length));
    var arrayData = new ArrayData(bytes);
    setField(Types.resolved.f_fuzion_sys_array_data, -1, saCl, sa, arrayData);
    setField(Types.resolved.f_array_internalArray, -1, cl, result, sa);

    return result;
  }


  /*--------------------  methods to call a feature  --------------------*/


  /**
   * Obtain backend information required for dynamic binding lookup to perform a
   * call.
   *
   * @param dynamic true if this sets the static inner / outer clazz of a
   * dynamic call, false if this is a static call
   *
   * @param innerClazz the frame clazz of the called feature
   *
   * @param outerClazz the static clazz of the target instance of this call
   *
   * @return an instance of Callable that performs the call
   */
  public Callable callable(boolean dynamic, Clazz innerClazz, Clazz outerClazz)
  {
    Callable result;

    // Special handling for reading 'val' field from ref version of built in integer types
    var builtInVal =
      innerClazz.feature().isField() &&
      (outerClazz == Clazzes.ref_i8 .getIfCreated() ||
       outerClazz == Clazzes.ref_i16.getIfCreated() ||
       outerClazz == Clazzes.ref_i32.getIfCreated() ||
       outerClazz == Clazzes.ref_i64.getIfCreated() ||
       outerClazz == Clazzes.ref_u8 .getIfCreated() ||
       outerClazz == Clazzes.ref_u16.getIfCreated() ||
       outerClazz == Clazzes.ref_u32.getIfCreated() ||
       outerClazz == Clazzes.ref_u64.getIfCreated() ||
       outerClazz == Clazzes.ref_f32.getIfCreated() ||
       outerClazz == Clazzes.ref_f64.getIfCreated()) &&
      /* NYI: somewhat ugly way to access "val" field, should better have Clazzes.ref_i32_val.getIfCreate() etc. */
      innerClazz.feature() == outerClazz.feature().get(Clazz._module, "val");

    if (dynamic && !builtInVal)
      {
        return null; // in dynamic case, interpreter does not use this, but dynamic lookup only
      }
    else if (innerClazz == null)
      {
        if (CHECKS) check
          (Errors.count() > 0);
        result = (args) -> { Errors.fatal("null feature called"); return Value.NO_VALUE; };
      }
    else
      {
        var f = innerClazz.feature();
        switch (f.kind())
          {
          case Abstract:
            result = (args) -> { Errors.fatal("abstract feature " + f.qualifiedName() + " called on " + args.get(0) + " of clazz "+outerClazz + "\n" + callStack()); return Value.NO_VALUE; };
            break;
          case Field:
            {
              // result = (args) -> getField(f, outerClazz, args.get(0));
              //
              // specialize for i32.val and bool.tag
              var ocv = outerClazz.asValue();
              if (builtInVal || ocv == Clazzes.bool.getIfCreated())
                {
                  if (CHECKS) check
                    (ocv != Clazzes.i8  .getIfCreated() || f.qualifiedName().equals("i8.val"),
                     ocv != Clazzes.i16 .getIfCreated() || f.qualifiedName().equals("i16.val"),
                     ocv != Clazzes.i32 .getIfCreated() || f.qualifiedName().equals("i32.val"),
                     ocv != Clazzes.i64 .getIfCreated() || f.qualifiedName().equals("i64.val"),
                     ocv != Clazzes.u8  .getIfCreated() || f.qualifiedName().equals("u8.val"),
                     ocv != Clazzes.u16 .getIfCreated() || f.qualifiedName().equals("u16.val"),
                     ocv != Clazzes.u32 .getIfCreated() || f.qualifiedName().equals("u32.val"),
                     ocv != Clazzes.u64 .getIfCreated() || f.qualifiedName().equals("u64.val"),
                     ocv != Clazzes.f32 .getIfCreated() || f.qualifiedName().equals("f32.val") &&
                     ocv != Clazzes.f64 .getIfCreated() || f.qualifiedName().equals("f64.val"),
                     ocv != Clazzes.bool.getIfCreated() || f == Types.resolved.f_bool.choiceTag());
                  result = (args) -> args.get(0);
                }
              else
                {
                  Clazz fclazz = outerClazz.clazzForFieldX(f, innerClazz._select);
                  if (outerClazz.isRef())
                    {
                      result = (args) ->
                        {
                          LValue slot = fieldSlot(f, -1, outerClazz, fclazz, args.get(0));
                          return loadField(f, fclazz, slot, false);
                        };
                    }
                  else if (true) // NYI: offset might not have been set yet, so for now, cache it dynamically at runtime
                    {
                      result = new Callable()
                        {
                          int off = -1;
                          public Value call(ArrayList<Value> args)
                          {
                            if (off < 0)
                              {
                                off = Layout.get(outerClazz).offset(innerClazz);
                              }
                            var slot = args.get(0).at(fclazz, off);
                            return loadField(f, fclazz, slot, false);
                          }
                        };
                    }
                  else
                    {
                      var off = Layout.get(outerClazz).offset(innerClazz);
                      result = (args) ->
                        {
                          var slot = args.get(0).at(fclazz, off);
                          return loadField(f, fclazz, slot, false);
                        };
                    }
                }
              break;
            }
          case Intrinsic:
            result = Intrinsics.call(this, innerClazz);
            break;
          case Choice: // NYI: why choice here?
          case Routine:
            if (innerClazz == Clazzes.universe.get())
              {
                result = (args) -> callOnInstance(f, innerClazz, Instance.universe, args);
              }
            else
              {
                result = (args) -> callOnInstance(f, innerClazz, new Instance(innerClazz), args);
              }
            break;
          case TypeParameter:
            {
              result = (args) -> {
                var rc = innerClazz.resultClazz();
                if (CHECKS) check  // check that outer ref, if exists, is unused:
                  (true || // NYI: This check is currently disabled, outer ref of types are not properly removed yet and not properly initialized here
                   rc.feature().outerRef() == null || !Clazzes.isUsedAtAll(rc.feature().outerRef()));
                return new Instance(rc);
              };
              break;
            }
          default:
            throw new Error("unhandled switch case: "+f.kind());
          }
      }
    return result;
  }


  /**
   * callOnInstance assigns the arguments to the argument fields of a newly
   * created instance, calls the parents and then this feature.
   *
   * @param cur the newly created instance
   *
   * @param args the arguments to be passed to this call.
   *
   * @return
   */
  public Value callOnInstance(AbstractFeature thiz, Clazz staticClazz, Instance cur, ArrayList<Value> args)
  {
    if (PRECONDITIONS) require
      (thiz.isRoutine(),
       args.size() == thiz.valueArguments().size() + 1 || thiz.hasOpenGenericsArgList() /* e.g. in call tuple<i32>(42) */
       );

    cur.checkStaticClazz(staticClazz);
    FuzionThread.current()._callStackFrames.push(staticClazz);

    if (CHECKS) check
      (Clazzes.isUsedAtAll(thiz));

    setOuter(thiz, staticClazz, cur, args.get(0));
    int aix = 1;
    for (var a : thiz.valueArguments())
      {
        if (a.isOpenGenericField())
          {
            int si = 0;
            while (aix < args.size())
              {
                setField(a,
                         si,
                         staticClazz,
                         cur,
                         args    .get(aix));
                aix++;
                si++;
              }
          }
        else
          {
            // field might have been redefined, see https://github.com/tokiwa-software/fuzion/issues/165
            a = staticClazz.lookup(a).feature();
            setField(a,
                     -1,
                     staticClazz,
                     cur,
                     args    .get(aix));
            aix++;
          }
      }

    for (var p: thiz.inherits())
      {
        // The new instance passed to the parent p is the same (==cur) as that
        // for this feature since the this inherits from p.
        ArrayList<Value> pargs = executeArgs(p, staticClazz, cur);
        callOnInstance(p.calledFeature(),
                       staticClazz,
                       cur,
                       pargs);
      }

    // NYI: Precondition should be checked by the caller _before_ dynamic
    // binding, not here after dynamic binding.  Also, preconditions should be
    // taken from the static feature called ORed with the preconditions of all
    // features that feature redefines.
    for (var c : thiz.contract().req)
      {
        var v = execute(c.cond, staticClazz, cur);
        if (!v.boolValue())
          {
            Errors.runTime(c.cond.pos(),  // NYI: move to new class InterpreterErrors
                           "Precondition does not hold",
                           "For call to " + thiz.qualifiedName() + "\n" +
                           callStack());
          }
      }

    switch (thiz.kind())
      {
      case Abstract:
        Errors.fatal(thiz.pos(),  // NYI: move to new class InterpreterErrors
                     "Abstract feature called",
                     "Feature called: " + thiz.qualifiedName() + "\n" +
                     "Target instance: " + cur);
        break;
      case Intrinsic:
        Errors.fatal(thiz.pos(),  // NYI: move to new class InterpreterErrors
                     "Missing intrinsic feature called",
                     "Feature called: " + thiz.qualifiedName() + "\n" +
                     "Target instance: " + cur);
        break;
      case Routine:
        execute(thiz.code(), staticClazz, cur);
        break;
      case Field:
      case Choice:
      default:
        throw new Error("Call to unsupported Feature kind: "+thiz.kind());
      }

    for (var c : thiz.contract().ens)
      {
        var v = execute(c.cond, staticClazz, cur);
        if (!v.boolValue())
          {
            Errors.runTime(c.cond.pos(),  // NYI: move to new class InterpreterErrors
                           "Postcondition does not hold",
                           "After call to " + thiz.qualifiedName() + "\n" +
                           callStack());
          }
      }
    // NYI: Also check postconditions for all features this redefines!
    FuzionThread.current()._callStackFrames.pop();

    return thiz.isConstructor() ? cur
                                : getField(thiz.resultField(), staticClazz, cur, false);
  }


  /*---------------------  methods to access fields  --------------------*/


  /**
   * Store a value in this choice type and set the proper tag. This is not
   * intended only for assigning a value of one specific generic parameter type
   * of the choice type to a choice field, not for assigning a choice value to a
   * field of the same choice type.
   *
   * @param choiceClazz the runtime clazz of this choice type
   *
   * @param choice the LValue referring to the choice field to be set
   *
   * @param staticTypeOfValue the static type of the value to be assigned to
   * choice.
   *
   * @param v the value to be stored in choice.
   */
  private static void setChoiceField(AbstractFeature thiz,
                                     Clazz choiceClazz,
                                     LValue choice,
                                     AbstractType staticTypeOfValue,
                                     Value v)
  {
    if (PRECONDITIONS) require
      (choiceClazz.isChoice(),
       choiceClazz.feature() == thiz,
       choiceClazz._type.compareTo(staticTypeOfValue) != 0);

    int tag = choiceClazz.getChoiceTag(staticTypeOfValue);
    Clazz  vclazz  = choiceClazz.getChoiceClazz(tag);
    LValue valSlot = choice.at(vclazz, Layout.get(choiceClazz).choiceValOffset(tag));
    if (choiceClazz.isChoiceOfOnlyRefs())
      { // store reference only
        if (!staticTypeOfValue.isRef())
          { // the value is a stateless value type, so we store the tag as a reference.
            v = ChoiceIdAsRef.get(choiceClazz, tag);
            vclazz = Clazzes.object.get();
            staticTypeOfValue = vclazz._type;
            valSlot = choice.at(vclazz, Layout.get(choiceClazz).choiceRefValOffset());
          }
      }
    else
      { // store tag and value separately
        setField(thiz.choiceTag(), -1, choiceClazz, choice, new i32Value(tag));
      }
    if (CHECKS) check
      (vclazz._type.isAssignableFrom(staticTypeOfValue));
    setFieldSlot(thiz, vclazz, valSlot, v);
  }


  /**
   * Read the value slot for refs within a choice clazz.
   *
   * @param choiceClazz the runtime clazz corresponding to this
   *
   * @param choice the value containing the choice.
   */
  public static Value getChoiceRefVal(AbstractFeature thiz, Clazz choiceClazz, Value choice)
  {
    if (PRECONDITIONS) require
      (choiceClazz != null,
       choiceClazz.feature() == thiz,
       choiceClazz.isChoiceWithRefs(),
       choice != null);

    int offset  = Layout.get(choiceClazz).choiceRefValOffset();
    LValue slot = choice.at(Clazzes.object.get(), offset);
    return loadRefField(thiz, slot, false);
  }


  /**
   * Read a value slot within a choice clazz.
   *
   * @param choiceClazz the runtime clazz corresponding to this
   *
   * @param choice the value containing the choice.
   *
   * @param tag the tag value identifying the slot to be read.
   */
  public static Value getChoiceVal(AbstractFeature thiz, Clazz choiceClazz, Value choice, int tag)
  {
    if (PRECONDITIONS) require
      (choiceClazz != null,
       choiceClazz.feature() == thiz,
       choice != null,
       tag >= 0);

    Clazz  vclazz = choiceClazz.getChoiceClazz(tag);
    LValue slot   = choice.at(vclazz, Layout.get(choiceClazz).choiceValOffset(tag));
    return loadField(thiz, vclazz, slot, false);
  }


  /**
   * Check if the given field could hold the given value.  Just for pre-/postconditions.q
   *
   * @param thiz a field
   *
   * @param v a value
   *
   * @param allowUninitializedRefField true if a ref field may be not
   * initialized (e.g., when boxing this).
   */
  private static boolean valueTypeMatches(AbstractFeature thiz, Value v, boolean allowUninitializedRefField)
  {
    return
      v instanceof Instance                                            /* a normal ref type     */ ||
      v instanceof ArrayData                                           /* fuzion.sys.array.data */ ||
      v instanceof LValue                                              /* ref type as LValue    */ ||
      v instanceof ChoiceIdAsRef && thiz.isChoice()                    /* a boxed choice tag    */ ||
      (v instanceof i8Value ||
       v instanceof i16Value ||
       v instanceof i32Value ||
       v instanceof i64Value ||
       v instanceof u8Value ||
       v instanceof u16Value ||
       v instanceof u32Value ||
       v instanceof u64Value ||
       v instanceof f32Value ||
       v instanceof f64Value   ) && thiz.isOuterRef()     /* e.g. outerref in integer.infix /-/ */ ||
      v == null                  && thiz.isChoice()                /* Nil/Null boxed choice tag */ ||
      v == null                  && allowUninitializedRefField;
  }


  /**
   * load a field of reference type from memory
   *
   * @param slot reference to instance and offset of the field to be loaded
   *
   * @return the value that was loaded from the field, of type Instance for
   * normal refs, of type ChoiceIdAsRef, LValue or null for boxed choice tag or
   * ref to outer instance.
   *
   * @param allowUninitializedRefField true if a ref field may be not
   * initialized (e.g., when boxing this).
   */
  private static Value loadRefField(AbstractFeature thiz, LValue slot, boolean allowUninitializedRefField)
  {
    if (PRECONDITIONS) require
      (slot != null);

    Value result = slot.container.refs[slot.offset];

    if (POSTCONDITIONS) ensure
      (valueTypeMatches(thiz, result, allowUninitializedRefField));

    return result;
  }


  /**
   * Store a reference in a given field of reference type.
   *
   * @param slot reference to instance and offset of the field to be set
   *
   * @param v the value to be stored in cur at offset
   */
  private static void setRefField(AbstractFeature thiz,
                                  LValue slot,
                                  Value v)
  {
    if (PRECONDITIONS) require
      (slot != null,
       valueTypeMatches(thiz, v, false)
       );

    slot.container.refs[slot.offset] = v;
  }


  /**
   * Store a value in a given field of value type.
   *
   * @param fclazz the runtime clazz of the value type
   *
   * @param slot reference to instance and offset of the field to be set
   *
   * @param v the value to be stored in cur at offset
   */
  private static void setNonRefField(AbstractFeature thiz,
                                     Clazz fclazz,
                                     LValue slot,
                                     Value v)
  {
    if (PRECONDITIONS) require
      (!fclazz.isRef(),
       slot != null,
       v != null || thiz.isChoice() ||
       v instanceof LValue    ||
       v instanceof Instance  ||
       v instanceof i8Value   ||  // NYI: what about u8/u16/..
       v instanceof i16Value  ||
       v instanceof i32Value  ||
       v instanceof i64Value  ||
       v instanceof boolValue    );

    v.storeNonRef(slot, Layout.get(fclazz).size());
  }


  /**
   * Create an LValue that refers to the slot that contains this field.
   *
   * @param thiz the field to access.
   *
   * @param select in case thiz is a field of open generic type, this selects
   * the actual field. -1 otherwise.
   *
   * @param staticClazz is the static type of the clazz that contains the
   * this field
   *
   * @param fclazz is the static type of the field
   *
   * @param curValue the Instance or LValue of the object that contains the
   * loaded field
   *
   * @return an LValue that refers directly to the memory for the field.
   */
  private static LValue fieldSlot(AbstractFeature thiz, int select, Clazz staticClazz, Clazz fclazz, Value curValue)
  {
    int off;
    var clazz = staticClazz;
    if (staticClazz.isRef())
      {
        curValue = (curValue instanceof LValue lv) ? loadRefField(thiz, lv, false)
                                                   : curValue;
        clazz = ((ValueWithClazz) curValue).clazz();
      }
    off = Layout.get(clazz).offset0(thiz, select);

    // NYI: check if this is a can be enabled or removed:
    //
    //  check
    //    (staticClazz.isAssignableFrom(clazz));
    return curValue.at(fclazz, off);
  }


  /**
   * load a field from memory
   *
   * @param slot reference to instance and offset of the field to be loaded
   *
   * @param allowUninitializedRefField true if a ref field may be not
   * initialized (e.g., when boxing this).
   *
   * @return the value that was loaded from the field, of type Instance for
   * normal refs, of type ChoiceIdAsRef, LValue for non-reference fields or ref
   * to outer instance, LValue or null for boxed choice tag.
   */
  private static Value loadField(AbstractFeature thiz, Clazz fclazz, LValue slot, boolean allowUninitializedRefField)
  {
    if (CHECKS) check
      (fclazz != null,
       slot != null);

    Value result = fclazz.isRef() ? loadRefField(thiz, slot, allowUninitializedRefField)
                                  : slot;

    if (POSTCONDITIONS) ensure
      (valueTypeMatches(thiz, result, allowUninitializedRefField));

    return result;
  }


  /**
   * getField loads the value of a field.
   *
   * @param staticClazz is the static type of the clazz that contains the
   * loaded field
   *
   * @param curValue the Instance or LValue of the object that contains the
   * loaded field
   *
   * @param allowUninitializedRefField When boxing a partially initialized value
   * (this), some fields may not be initialized yet.
   *
   * NYI: Once static analysis detects use of uninitialized data, boxing this
   * data should be disallowed.
   *
   * @return the value that was loaded from the field, of type LValue for
   * non-refs, Instance for normal refs, of type ChoiceIdAsRef, LValue or null
   * for boxed choice tag or ref to outer instance.
   */
  public static Value getField(AbstractFeature thiz, Clazz staticClazz, Value curValue, boolean allowUninitializedRefField)
  {
    if (PRECONDITIONS) require
      (thiz.isField(),
       (curValue instanceof Instance) || (curValue instanceof LValue) ||
       curValue instanceof i8Value   && staticClazz == Clazzes.i8  .getIfCreated() ||
       curValue instanceof i16Value  && staticClazz == Clazzes.i16 .getIfCreated() ||
       curValue instanceof i32Value  && staticClazz == Clazzes.i32 .getIfCreated() ||
       curValue instanceof i64Value  && staticClazz == Clazzes.i64 .getIfCreated() ||
       curValue instanceof u8Value   && staticClazz == Clazzes.u8  .getIfCreated() ||
       curValue instanceof u16Value  && staticClazz == Clazzes.u16 .getIfCreated() ||
       curValue instanceof u32Value  && staticClazz == Clazzes.u32 .getIfCreated() ||
       curValue instanceof u64Value  && staticClazz == Clazzes.u64 .getIfCreated() ||
       curValue instanceof f32Value  && staticClazz == Clazzes.f32 .getIfCreated() ||
       curValue instanceof f64Value  && staticClazz == Clazzes.f64 .getIfCreated() ||
       curValue instanceof boolValue && staticClazz == Clazzes.bool.getIfCreated(),
       staticClazz != null);

    Value result;
    if (staticClazz == Clazzes.i8.getIfCreated() && curValue instanceof i8Value)
      {
        if (CHECKS) check
          (thiz.qualifiedName().equals("i8.val"));
        result = curValue;
      }
    else if (staticClazz == Clazzes.i16.getIfCreated() && curValue instanceof i16Value)
      {
        if (CHECKS) check
          (thiz.qualifiedName().equals("i16.val"));
        result = curValue;
      }
    else if (staticClazz == Clazzes.i32.getIfCreated() && curValue instanceof i32Value)
      {
        if (CHECKS) check
          (thiz.qualifiedName().equals("i32.val"));
        result = curValue;
      }
    else if (staticClazz == Clazzes.i64.getIfCreated() && curValue instanceof i64Value)
      {
        if (CHECKS) check
          (thiz.qualifiedName().equals("i64.val"));
        result = curValue;
      }
    else if (staticClazz == Clazzes.u8.getIfCreated() && curValue instanceof u8Value)
      {
        if (CHECKS) check
          (thiz.qualifiedName().equals("u8.val"));
        result = curValue;
      }
    else if (staticClazz == Clazzes.u16.getIfCreated() && curValue instanceof u16Value)
      {
        if (CHECKS) check
          (thiz.qualifiedName().equals("u16.val"));
        result = curValue;
      }
    else if (staticClazz == Clazzes.u32.getIfCreated() && curValue instanceof u32Value)
      {
        if (CHECKS) check
          (thiz.qualifiedName().equals("u32.val"));
        result = curValue;
      }
    else if (staticClazz == Clazzes.u64.getIfCreated() && curValue instanceof u64Value)
      {
        if (CHECKS) check
          (thiz.qualifiedName().equals("u64.val"));
        result = curValue;
      }
    else if (staticClazz == Clazzes.f32.getIfCreated() && curValue instanceof f32Value)
      {
        if (CHECKS) check
          (thiz.qualifiedName().equals("f32.val"));
        result = curValue;
      }
    else if (staticClazz == Clazzes.f64.getIfCreated() && curValue instanceof f64Value)
      {
        if (CHECKS) check
          (thiz.qualifiedName().equals("f64.val"));
        result = curValue;
      }
    else if (staticClazz == Clazzes.bool.getIfCreated() && curValue instanceof boolValue)
      {
        if (CHECKS) check
          (thiz == Types.resolved.f_bool.choiceTag());
        result = curValue;
      }
    else
      {
        Clazz  fclazz = staticClazz.clazzForFieldX(thiz, -1);
        LValue slot   = fieldSlot(thiz, -1, staticClazz, fclazz, curValue);
        result = loadField(thiz, fclazz, slot, allowUninitializedRefField);
      }

    if (POSTCONDITIONS) ensure
      (   result != null                          // there must not be any null
       || allowUninitializedRefField              // unless we explicitly allowed uninitialized data
      );

    return result;
  }


  /**
   * setFieldSlot stores a value into a field.
   *
   * @param fclazz is the static type of the field to be written to
   *
   * @param slot is the address of the field to be written
   *
   * @param v the value to be stored in slot
   */
  private static void setFieldSlot(AbstractFeature thiz, Clazz fclazz, LValue slot, Value v)
  {
    if (PRECONDITIONS) require
      (fclazz != null,
       slot != null,
       v != null || thiz.isChoice() || fclazz._type.compareTo(Types.resolved.t_unit) == 0);

    if (fclazz.isRef())
      {
        setRefField   (thiz,        slot, v);
      }
    else if (fclazz._type.compareTo(Types.resolved.t_unit) != 0)  // NYI: remove these assignments in earlier phase
      {
        setNonRefField(thiz, fclazz, slot, v);
      }
  }


  /**
   * setField stores a value into a field
   *
   * @param staticClazz is the static type of the clazz that contains the
   * written field
   *
   * @param select in case thiz is a field of open generic type, this selects
   * the actual field. -1 otherwise.
   *
   * @param curValue the Instance or LValue of that contains the written field
   *
   * @param v the value to be stored in the field
   */
  public static void setField(AbstractFeature thiz, int select, Clazz staticClazz, Value curValue, Value v)
  {
    if (PRECONDITIONS) require
      (thiz.isField(),
       (curValue instanceof Instance) || (curValue instanceof LValue),
       staticClazz != null);

    if (Clazzes.isUsed(thiz, staticClazz))
      {
        Clazz  fclazz = staticClazz.clazzForFieldX(thiz, select);
        LValue slot   = fieldSlot(thiz, select, staticClazz, fclazz, curValue);
        setFieldSlot(thiz, fclazz, slot, v);
      }
  }


  /**
   * If this has a reference to the frame of the outer feature and this
   * reference has been marked to be used, then set it to the given value.
   *
   * @param cur the newly created instance whose outer ref is to be set
   *
   * @param outer the address or value of the outer feature
   */
  public static void setOuter(AbstractFeature thiz, Clazz staticClazz, Instance cur, Value outer)
  {
    var or = thiz.outerRef();
    if (or != null && Clazzes.isUsedAtAll(or))
      {
        setField(or, -1, staticClazz, cur, outer);
      }
  }

}

/* end of file */
