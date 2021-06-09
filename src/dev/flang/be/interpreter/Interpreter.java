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
 * Tokiwa GmbH, Berlin
 *
 * Source of class Interpreter
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;

import dev.flang.fuir.FUIR;

import dev.flang.ir.Backend;
import dev.flang.ir.Clazz;
import dev.flang.ir.Clazzes;

import dev.flang.ast.AdrToValue; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Assign; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Block; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.BoolConst; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Box; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Call; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Case; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Check; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Current; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Expr; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Feature; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.If; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Impl; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.InitArray; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.IntConst; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Match; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Nop; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Old; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Stmnt; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.StrConst; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Tag; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Type; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Types; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Universe; // NYI: remove dependency! Use dev.flang.fuir instead.


/**
 * Interpreter contains interpreter for Fuzion application that is present as
 * intermediate code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class Interpreter extends Backend
{


  /*-----------------------------  statics  -----------------------------*/


  /**
   * Current call stack, for debugging output
   */
  public static Stack<Call> _callStack = new Stack<>();
  public static Stack<Clazz> _callStackFrames = new Stack<>();


  /**
   * Current call stack as a string for debugging output.
   */
  public static String callStack()
  {
    StringBuilder sb = new StringBuilder("Call stack:\n");
    Clazz lastFrame = null;
    Call lastCall = null;
    int repeat = 0;
    for (var i = _callStack.size()-1; i >= 0; i--)
      {
        Clazz frame = i<_callStackFrames.size() ? _callStackFrames.get(i) : null;
        Call call = _callStack.get(i);
        if (frame == lastFrame && call == lastCall)
          {
            repeat++;
          }
        else
          {
            if (repeat > 0)
              {
                sb.append("...  repeated ").append(repeat).append(" times  ...\n\n");
                repeat = 0;
              }
            if (frame != null)
              {
                sb.append(frame).append(": ");
              }
            sb.append(call.pos.show()).append("\n");
            lastFrame = frame;
            lastCall = call;
          }
      }
    if (repeat > 0)
      {
        sb.append("  ...  repeated ").append(repeat).append(" times  ...\n\n");
        repeat = 0;
      }
    return sb.toString();
  }

  static Map<String, Value> _cachedStrings_ = new TreeMap<>();


  /*----------------------------  variables  ----------------------------*/


  /**
   * The intermediate representation of the code we are interpreting.
   */
  final FUIR _fuir;


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create an interpreter to execute the given intermediate code.
   */
  public Interpreter(FUIR fuir)
  {
    _fuir = fuir;
    Clazzes.findAllClasses(this, _fuir.main());
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
    ArrayList<Value> mainargs = new ArrayList<>();
    mainargs.add(Instance.universe); // outer instance
    // mainargs.add(null); // NYI: args
    try
      {
        callable(false, _fuir.main(), Clazzes.universe.get()).call(mainargs);
      }
    catch (RuntimeException | Error e)
      {
        if (!(e instanceof StackOverflowError))
          {
            Errors.error("*** " + e + "\n" + callStack());
            throw e;
          }
        Errors.fatal("*** " + e + "\n" + callStack());
      }
    check
      (Errors.count() == 0);
  }


  /*----------------  methods to find execute statments  ----------------*/


  /**
   * Temporary interpreter, will be replaced by dynamic calls.
   *
   * This is fairly inefficient compared to dynamic
   * binding and it uses way too much stack since recursion keeps this giant
   * stack frame alive.
   */
  public static Value execute(Stmnt s, Clazz staticClazz, Value cur)
  {
    Value result;
    if (s instanceof Call)
      {
        var c = (Call) s;

        if (PRECONDITIONS) require
          (!c.isInheritanceCall_,  // inheritance calls are handled in Fature.callOnInstance
           c.sid_ >= 0);

        ArrayList<Value> args = executeArgs(c, staticClazz, cur);
        _callStack.push(c);

        var ca = (Callable) staticClazz.getRuntimeData(c.sid_);
        if (ca == null)
          {
            check
              (c.isDynamic());

            var cl = ((Instance) args.get(0)).clazz();
            ca = (Callable) cl._dynamicBinding.callable(c.calledFeature());
          }
        result = ca.call(args);
        _callStack.pop();
      }

    else if (s instanceof Current)
      {
        result = cur;
      }

    else if (s instanceof Assign)
      {
        var a = (Assign) s;
        Value v    = execute(a.value   , staticClazz, cur);
        Value thiz = execute(a.getOuter, staticClazz, cur);
        Clazz sClazz = staticClazz.getRuntimeClazz(a.tid_ + 0);
        setField(a.assignedField, sClazz, thiz, v);
        result = Value.NO_VALUE;
      }

    else if (s instanceof BoolConst)
      {
        var b = (BoolConst) s;
        result = new boolValue(b.b);
      }

    else if (s instanceof IntConst)
      {
        var i = (IntConst) s;
        var t = i.type();
        if      (t == Types.resolved.t_i32) { result = new i32Value((int ) i.l); }
        else if (t == Types.resolved.t_u32) { result = new u32Value((int ) i.l); }
        else if (t == Types.resolved.t_i64) { result = new i64Value((long) i.l); }
        else if (t == Types.resolved.t_u64) { result = new u64Value((long) i.l); }
        else                                { result = Value.NO_VALUE; check(false); }
      }

    else if (s instanceof Block)
      {
        var b = (Block) s;
        result = Value.NO_VALUE;
        for (Stmnt stmnt : b.statements_)
          {
            result = execute(stmnt, staticClazz, cur);
          }
      }

    else if (s instanceof If)
      {
        var i = (If) s;

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

    else if (s instanceof Match)
      {
        var m = (Match) s;
        result = null;
        Clazz staticSubjectClazz = staticClazz.getRuntimeClazz(m.runtimeClazzId_);
        Value sub = execute(m.subject, staticClazz, cur);
        Feature sf = staticSubjectClazz.feature();
        int tag;
        Value refVal = null;
        if (staticSubjectClazz.isChoiceOfOnlyRefs())
          {
            refVal = getChoiceRefVal(sf, staticSubjectClazz, sub);
            tag = ChoiceIdAsRef.get(staticSubjectClazz, refVal);
          }
        else
          {
            tag = getField(sf.choiceTag_, staticSubjectClazz, sub).i32Value();
          }
        Clazz subjectClazz = tag < 0
          ? ((Instance) refVal).clazz()
          : staticSubjectClazz.getChoiceClazz(tag);

        Iterator<Case> it = m.cases.iterator();
        boolean matches = false;
        do
          {
            Case c = it.next();

            if (c.field != null && Clazzes.isUsed(c.field, staticClazz))
              {
                Clazz fieldClazz = staticClazz.getRuntimeClazz(c.runtimeClazzId_).resultClazz();
                if (fieldClazz.isAssignableFrom(subjectClazz))
                  {
                    Value v = tag < 0 ? refVal
                                      : getChoiceVal(sf, staticSubjectClazz, sub, tag);
                    setField(c.field, staticClazz, cur, v);
                    matches = true;
                  }
              }
            else
              {
                var nt = c.field != null ? 1 : c.types.size();
                for (int i = 0; !matches && i < nt; i++)
                  {
                    Clazz caseClazz = staticClazz.getRuntimeClazz(c.runtimeClazzId_ + i);
                    matches = caseClazz.isAssignableFrom(subjectClazz);
                  }
              }
            if (matches)
              {
                result = execute(c.code, staticClazz, cur);
              }
          }
        while (!matches && it.hasNext());

        if (!matches)
          {
            var permitted = new List<Clazz>();
            for (var c : m.cases)
              {
                if (c.field != null)
                  {
                    permitted.add(staticClazz.getRuntimeClazz(c.runtimeClazzId_).resultClazz());
                  }
                else
                  {
                    for (int i = 0; i < c.types.size(); i++)
                      {
                        permitted.add(staticClazz.getRuntimeClazz(c.runtimeClazzId_ + i));
                      }
                  }
              }
            Errors.fatal(m.pos(), "no match found",
                         "For value of clazz: " + subjectClazz + "\n" +
                         "Permitted clazzes: " + permitted.toString("",", ","") + "\n" +
                         callStack());
          }
        check
          (matches);

      }

    else if (s instanceof AdrToValue)
      {
        var a = (AdrToValue) s;

        // This is a NOP here since values of reference type and value type are
        // treated the same way by the interpreter.
        result = execute(a.adr_, staticClazz, cur);
      }

    else if (s instanceof Universe)
     {
       result = Instance.universe;
     }

    else if (s instanceof Box)
      {
        var b = (Box) s;
        Value val = execute(b._value, staticClazz, cur);
        Clazz vc = (Clazz) staticClazz.getRuntimeData(b._valAndRefClazzId);
        Clazz rc = (Clazz) staticClazz.getRuntimeData(b._valAndRefClazzId + 1);
        if (vc.isRef())
          { // vc's type is a generic argument whose actual type does not need
            // boxing
            check
              (b._value.type().isGenericArgument() || b._value.isCallToOuterRef(),
               vc == rc);

            result = val;
          }
        else
          {
            // NYI: split this up into one statement that creates the new instance
            // followed by several instances of Assign that copy the fields.
            var ri = new Instance(rc);
            result = ri;
            for (Feature f : vc.clazzForField_.keySet())
              {
                // Fields select()ed from fields of open generic type have type t_unit
                // if the actual clazz does not have the number of actual open generic
                // parameters.
                if (vc.actualType(f.resultType()) != Types.resolved.t_unit)
                  {
                    Value v = getField(f, vc, val);
                    // NYI: Check that this works well for internal fields such as choice tags.
                    // System.out.println("Box "+vc+" => "+rc+" copying "+f.qualifiedName()+" "+v);
                    setField(f, rc, result, v);
                  }
              }
            if (vc.isChoice())
              {
                check
                  (rc.isChoice());

                var vl = Layout.get(vc);
                var rl = Layout.get(rc);
                var voff = 0;
                var roff = 0;
                var vsz  = vl.size();
                check
                  (rl.size() == vsz);
                if (val instanceof LValue)
                  {
                    voff += ((LValue) val).offset;
                    val   = ((LValue) val).container;
                  }
                if (val instanceof boolValue)
                  {
                    val.storeNonRef(new LValue(Clazzes.bool.get(), ri, roff), Layout.get(Clazzes.bool.get()).size());
                  }
                else
                  {
                    check
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

    else if (s instanceof StrConst)
      {
        var t = (StrConst) s;
        var str = t.str;
        result = _cachedStrings_.get(str);
        if (result == null)
          {
            result = value(t.str);
            _cachedStrings_.put(str, result);
          }
      }

    else if (s instanceof Tag)
      {
        var t = (Tag) s;
        Value v       = execute(t._value, staticClazz, cur);
        Clazz vClazz  = staticClazz.getRuntimeClazz(t._valAndTaggedClazzId + 0);
        Clazz tClazz  = staticClazz.getRuntimeClazz(t._valAndTaggedClazzId + 1);
        result        = new Instance(tClazz);
        LValue slot   = result.at(tClazz, 0); // NYI: needed? just result?
        setChoiceField(tClazz.feature(),
                       tClazz,
                       slot,
                       vClazz._type,
                       v);
      }

    else if (s instanceof Check)
      {
        var c = (Check) s;

        // NYI: check not supported yet
        // System.err.println("NYI: "+c);

        result = Value.NO_VALUE;
      }

    else if (s instanceof Nop)
      {
        result = Value.NO_VALUE;
      }

    else if (s instanceof Old)
      {
        throw new Error("NYI: Expr.execute() for " + s.getClass() + " " +s);
      }

    else if (s instanceof InitArray)
      {
        var i = (InitArray) s;
        Clazz ac  = staticClazz.getRuntimeClazz(i._arrayClazzId + 0);
        Clazz sac = staticClazz.getRuntimeClazz(i._arrayClazzId + 1);
        var sa = new Instance(sac);
        int l = i._elements.size();
        var arrayData = new Instance(l);
        setField(Types.resolved.f_sys_array_data  , sac, sa, arrayData);
        setField(Types.resolved.f_sys_array_length, sac, sa, new i32Value(l));
        for (int x = 0; x < l; x++)
          {
            var v = execute(i._elements.get(x), staticClazz, cur);
            Intrinsics.sysArraySetEl(arrayData, x, v, sac);
          }
        result = new Instance(ac);
        setField(Types.resolved.f_array_internalArray, ac, result, sa);
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
  public static ArrayList<Value> executeArgs(Call c,
                                             Clazz staticClazz,
                                             Value cur)
  {
    Value targt = execute(c.target, staticClazz, cur);
    ArrayList<Value> args = new ArrayList<>();
    args.add(targt);
    for (Expr e: c._actuals)
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
    result.string = str; // NYI: remove eventually, only for convenience in intrinsic features
    var saCl = Clazzes.constStringBytesArray;
    Instance sa = new Instance(saCl);
    byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
    setField(Types.resolved.f_sys_array_length, saCl, sa, new i32Value(bytes.length));
    Instance arrayData = new Instance(bytes.length);
    for (int i = 0; i<bytes.length; i++)
      {
        arrayData.nonrefs[i] = bytes[i];
      }
    setField(Types.resolved.f_sys_array_data, saCl, sa, arrayData);
    setField(Types.resolved.f_array_internalArray, cl, result, sa);

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

    if (dynamic)
      {
        return null; // in dynamic case, interpreter does not use this, but dynamic lookup only
      }
    else if (innerClazz == null)
      {
        check
          (Errors.count() > 0);
        result = (args) -> { Errors.fatal("null feature called"); return Value.NO_VALUE; };
      }
    else
      {
        var f = innerClazz.feature();
        if (f.impl == Impl.ABSTRACT)
          {
            result = (args) -> { Errors.fatal("abstract feature " + f.qualifiedName() + " called on " + args.get(0) + " of clazz "+outerClazz + "\n" + callStack()); return Value.NO_VALUE; };
          }
        else if (f.isField())
          {
            // result = (args) -> getField(f, outerClazz, args.get(0));
            //
            // specialize for i32.val and bool.tag
            var ocv = outerClazz.asValue();
            if (ocv == Clazzes.i32 .getIfCreated() ||
                ocv == Clazzes.i64 .getIfCreated() ||
                ocv == Clazzes.u32 .getIfCreated() ||
                ocv == Clazzes.u64 .getIfCreated() ||
                ocv == Clazzes.bool.getIfCreated()    )
              {
                check
                  (ocv != Clazzes.i32 .getIfCreated() || f.qualifiedName().equals("i32.val"),
                   ocv != Clazzes.i64 .getIfCreated() || f.qualifiedName().equals("i64.val"),
                   ocv != Clazzes.u32 .getIfCreated() || f.qualifiedName().equals("u32.val"),
                   ocv != Clazzes.u64 .getIfCreated() || f.qualifiedName().equals("u64.val"),
                   ocv != Clazzes.bool.getIfCreated() || f.qualifiedName().equals("bool." + FuzionConstants.CHOICE_TAG_NAME));
                result = (args) -> args.get(0);
              }
            else
              {
                Clazz fclazz = outerClazz.clazzForField(f);
                if (outerClazz.isRef())
                  {
                    result = (args) ->
                      {
                        LValue slot = fieldSlot(f, outerClazz, fclazz, args.get(0));
                        return loadField(f, fclazz, slot);
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
                              off = Layout.get(outerClazz).offset(f);
                            }
                          var slot = args.get(0).at(fclazz, off);
                          return loadField(f, fclazz, slot);
                        }
                      };
                  }
                else
                  {
                    var off = Layout.get(outerClazz).offset(f);
                    result = (args) ->
                      {
                        var slot = args.get(0).at(fclazz, off);
                        return loadField(f, fclazz, slot);
                      };
                  }
              }
          }
        else if (f.impl == Impl.INTRINSIC)
          {
            result = Intrinsics.call(innerClazz);
          }
        else if (innerClazz == Clazzes.universe.get())
          {
            result = (args) -> callOnInstance(f, innerClazz, Instance.universe, args);
          }
        else
          {
            result = (args) -> callOnInstance(f, innerClazz, new Instance(innerClazz), args);
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
  public static Value callOnInstance(Feature thiz, Clazz staticClazz, Instance cur, ArrayList<Value> args)
  {
    if (PRECONDITIONS) require
      (!thiz.isField(),
       thiz.impl != Impl.INTRINSIC,
       args.size() == thiz.arguments.size() + 1 || thiz.hasOpenGenericsArgList() /* e.g. in call tuple<i32>(42) */
       );

    cur.checkStaticClazz(staticClazz);
    _callStackFrames.push(staticClazz);

    check
      (thiz.isUsed());

    setOuter(thiz, staticClazz, cur, args.get(0));
    int aix = 1;
    for (Feature a : thiz.arguments)
      {
        if (a.isOpenGenericField())
          {
            int si = 0;
            while (aix < args.size())
              {
                setField(a.select(si),
                         staticClazz,
                         cur,
                         args    .get(aix));
                aix++;
                si++;
              }
          }
        else
          {
            setField(a,
                     staticClazz,
                     cur,
                     args    .get(aix));
            aix++;
          }
      }

    for (Call p: thiz.inherits)
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
    for (var c : thiz.contract.req)
      {
        var v = execute(c.cond, staticClazz, cur);
        if (!v.boolValue())
          {
            Errors.runTime(c.cond.pos,  // NYI: move to new class InterpreterErrors
                           "Precondition does not hold",
                           "For call to " + thiz.qualifiedName() + "\n" +
                           callStack());
          }
      }

    Impl i = thiz.impl;
    if (i == Impl.ABSTRACT)
      {
        Errors.fatal(thiz.pos,  // NYI: move to new class InterpreterErrors
                     "Abstract feature called",
                     "Feature called: " + thiz.qualifiedName() + "\n" +
                     "Target instance: " + cur);
      }
    if (i == Impl.INTRINSIC)
      {
        Errors.fatal(thiz.pos,  // NYI: move to new class InterpreterErrors
                     "Missing intrinsic feature called",
                     "Feature called: " + thiz.qualifiedName() + "\n" +
                     "Target instance: " + cur);
      }
    execute(i.code_, staticClazz, cur);
    for (var c : thiz.contract.ens)
      {
        var v = execute(c.cond, staticClazz, cur);
        if (!v.boolValue())
          {
            Errors.runTime(c.cond.pos,  // NYI: move to new class InterpreterErrors
                           "Postcondition does not hold",
                           "After call to " + thiz.qualifiedName() + "\n" +
                           callStack());
          }
      }
    // NYI: Also check postconditions for all features this redefines!
    _callStackFrames.pop();

    return thiz.returnType.isConstructorType() ? cur
                                               : getField(thiz.resultField(), staticClazz, cur);
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
  private static void setChoiceField(Feature thiz,
                                     Clazz choiceClazz,
                                     LValue choice,
                                     Type staticTypeOfValue,
                                     Value v)
  {
    if (PRECONDITIONS) require
      (choiceClazz.isChoice(),
       choiceClazz.feature() == thiz,
       choiceClazz._type != staticTypeOfValue);

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
        setField(thiz.choiceTag_, choiceClazz, choice, new i32Value(tag));
      }
    check
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
  public static Value getChoiceRefVal(Feature thiz, Clazz choiceClazz, Value choice)
  {
    if (PRECONDITIONS) require
      (choiceClazz != null,
       choiceClazz.feature() == thiz,
       choiceClazz.isChoiceWithRefs(),
       choice != null);

    int offset  = Layout.get(choiceClazz).choiceRefValOffset();
    LValue slot = choice.at(Clazzes.object.get(), offset);
    return loadRefField(thiz, slot);
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
  public static Value getChoiceVal(Feature thiz, Clazz choiceClazz, Value choice, int tag)
  {
    if (PRECONDITIONS) require
      (choiceClazz != null,
       choiceClazz.feature() == thiz,
       choice != null,
       tag >= 0);

    Clazz  vclazz = choiceClazz.getChoiceClazz(tag);
    LValue slot   = choice.at(vclazz, Layout.get(choiceClazz).choiceValOffset(tag));
    return loadField(thiz, vclazz, slot);
  }


  /**
   * Check if the given field could hold the given value.  Just for pre-/postconditions.q
   *
   * @param thiz a field
   *
   * @param v a value
   */
  private static boolean valueTypeMatches(Feature thiz, Value v)
  {
    return
      v instanceof Instance                                            /* a normal ref type     */ ||
      v instanceof LValue                                              /* ref type as LValue    */ ||
      v instanceof ChoiceIdAsRef && thiz.isChoice()                    /* a boxed choice tag    */ ||
      (v instanceof i32Value ||
       v instanceof i64Value ||
       v instanceof u32Value ||
       v instanceof u64Value   ) && thiz.isOuterRef()     /* e.g. outerref in integer.infix /-/ */ ||
      v == null                  && thiz.isChoice()                /* Nil/Null boxed choice tag */;
  }


  /**
   * load a field of reference type from memory
   *
   * @param slot reference to instance and offset of the field to be loaded
   *
   * @return the value that was loaded from the field, of type Instance for
   * normal refs, of type ChoiceIdAsRef, LValue or null for boxed choice tag or
   * ref to outer instance.
   */
  private static Value loadRefField(Feature thiz, LValue slot)
  {
    if (PRECONDITIONS) require
      (slot != null);

    Value result = slot.container.refs[slot.offset];

    if (POSTCONDITIONS) ensure
      (valueTypeMatches(thiz, result));

    return result;
  }


  /**
   * Store a reference in a given field of reference type.
   *
   * @param slot reference to instance and offset of the field to be set
   *
   * @param v the value to be stored in cur at offset
   */
  private static void setRefField(Feature thiz,
                                  LValue slot,
                                  Value v)
  {
    if (PRECONDITIONS) require
      (slot != null,
       valueTypeMatches(thiz, v)
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
  private static void setNonRefField(Feature thiz,
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
       v instanceof i32Value  ||
       v instanceof i64Value  ||
       v instanceof boolValue    );

    v.storeNonRef(slot, Layout.get(fclazz).size());
  }


  /**
   * Create an LValue that refers to the slot that contains this field.
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
  private static LValue fieldSlot(Feature thiz, Clazz staticClazz, Clazz fclazz, Value curValue)
  {
    int off;
    var clazz = staticClazz;
    if (staticClazz.isRef())
      {
        curValue = (curValue instanceof LValue) ? loadRefField(thiz, (LValue) curValue)
                                                : curValue;
        clazz = ((Instance) curValue).clazz();
      }
    off = Layout.get(clazz).offset(thiz);

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
   * @return the value that was loaded from the field, of type Instance for
   * normal refs, of type ChoiceIdAsRef, LValue for non-reference fields or ref
   * to outer instance, LValue or null for boxed choice tag.
   */
  private static Value loadField(Feature thiz, Clazz fclazz, LValue slot)
  {
    check
      (fclazz != null,
       slot != null);

    Value result = fclazz.isRef() ? loadRefField(thiz, slot)
                                  : slot;

    if (POSTCONDITIONS) ensure
      (valueTypeMatches(thiz, result));

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
   * @return the value that was loaded from the field, of type LValue for
   * non-refs, Instance for normal refs, of type ChoiceIdAsRef, LValue or null
   * for boxed choice tag or ref to outer instance.
   */
  public static Value getField(Feature thiz, Clazz staticClazz, Value curValue)
  {
    if (PRECONDITIONS) require
      (thiz.isField(),
       (curValue instanceof Instance) || (curValue instanceof LValue) ||
       curValue instanceof i32Value  && staticClazz == Clazzes.i32 .getIfCreated() ||
       curValue instanceof i64Value  && staticClazz == Clazzes.i64 .getIfCreated() ||
       curValue instanceof u32Value  && staticClazz == Clazzes.u32 .getIfCreated() ||
       curValue instanceof u64Value  && staticClazz == Clazzes.u64 .getIfCreated() ||
       curValue instanceof boolValue && staticClazz == Clazzes.bool.getIfCreated(),
       staticClazz != null);

    Value result;
    if (staticClazz == Clazzes.i32.getIfCreated() && curValue instanceof i32Value)
      {
        check
          (thiz.qualifiedName().equals("i32.val"));
        result = curValue;
      }
    else if (staticClazz == Clazzes.i64.getIfCreated() && curValue instanceof i64Value)
      {
        check
          (thiz.qualifiedName().equals("i64.val"));
        result = curValue;
      }
    else if (staticClazz == Clazzes.u32.getIfCreated() && curValue instanceof u32Value)
      {
        check
          (thiz.qualifiedName().equals("u32.val"));
        result = curValue;
      }
    else if (staticClazz == Clazzes.u64.getIfCreated() && curValue instanceof u64Value)
      {
        check
          (thiz.qualifiedName().equals("u64.val"));
        result = curValue;
      }
    else if (staticClazz == Clazzes.bool.getIfCreated() && curValue instanceof boolValue)
      {
        check
          (thiz.qualifiedName().equals("bool." + FuzionConstants.CHOICE_TAG_NAME));
        result = curValue;
      }
    else
      {
        Clazz  fclazz = staticClazz.clazzForField(thiz);
        LValue slot   = fieldSlot(thiz, staticClazz, fclazz, curValue);
        result = loadField(thiz, fclazz, slot);
      }

    if (POSTCONDITIONS) ensure
      (   thiz.isChoice()                         // null is used e.g. in Option<T>: choice<T,Null>.
       || result != null                          // otherwise, there must not be any null
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
  private static void setFieldSlot(Feature thiz, Clazz fclazz, LValue slot, Value v)
  {
    if (PRECONDITIONS) require
      (fclazz != null,
       slot != null,
       v != null || thiz.isChoice() || fclazz._type == Types.resolved.t_unit);

    if (fclazz.isRef())
      {
        setRefField   (thiz,        slot, v);
      }
    else if (fclazz._type != Types.resolved.t_unit)  // NYI: remove these assignments in earlier phase
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
   * @param curValue the Instance or LValue of that contains the written field
   *
   * @param v the value to be stored in the field
   */
  public static void setField(Feature thiz, Clazz staticClazz, Value curValue, Value v)
  {
    if (PRECONDITIONS) require
      (thiz.isField(),
       (curValue instanceof Instance) || (curValue instanceof LValue),
       staticClazz != null);

    if (Clazzes.isUsed(thiz, staticClazz))
      {
        Clazz  fclazz = staticClazz.clazzForField(thiz);
        LValue slot   = fieldSlot(thiz, staticClazz, fclazz, curValue);
        setFieldSlot(thiz, fclazz, slot, v);
      }
  }


  /**
   * If this has a reference to the frame of the outer feature and this
   * reference has been marked to be used,then set it to the given value.
   *
   * @param cur the newly created instance whose outer ref is to be set
   *
   * @param outer the address or value of the outer feature
   */
  public static void setOuter(Feature thiz, Clazz staticClazz, Instance cur, Value outer)
  {
    var or = thiz.outerRef_;
    if (or != null && or.isUsed())
      {
        setField(or, staticClazz, cur, outer);
      }
  }

}

/* end of file */
