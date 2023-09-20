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
 * Source of class CodeGen
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm;

import dev.flang.fuir.FUIR;

import dev.flang.fuir.analysis.AbstractInterpreter;

import dev.flang.be.jvm.classfile.Expr;
import dev.flang.be.jvm.classfile.ClassFileConstants;

import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.Pair;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;



/**
 * CodeGen is the statement processor for the JVM bytecode backend used with
 * AbstractInterpreter to generate bytecode.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class CodeGen
  extends AbstractInterpreter.ProcessStatement<Expr, Expr>  // both, values and statements are implemented by Expr
  implements ClassFileConstants
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The JVM backend
   */
  final JVM _jvm;


  /**
   * The IR, short for _jvm._fuir.
   */
  final FUIR _fuir;


  /**
   * Names used in the code, short for _jvm._names.
   */
  final Names _names;


  /**
   * Types used in the code, short for _jvm._types.
   */
  final Types _types;


  /**
   * Choices used in the code, short for _jvm._types._choices
   */
  final Choices _choices;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create CodeGen instance for JVM bytecode backend.
   *
   * @param jvm the backend instance.
   */
  public CodeGen(JVM jvm)
  {
    _jvm = jvm;
    _fuir = jvm._fuir;
    _names = jvm._names;
    _types = jvm._types;
    _choices = jvm._types._choices;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Join a List of RESULT from subsequent statements into a compound
   * statement.  For a code generator, this could, e.g., join statements "a :=
   * 3;" and "b(x);" into a block "{ a := 3; b(x); }".
   */
  public Expr sequence(List<Expr> l)
  {
    var res = Expr.UNIT;
    for (var s : l)
      {
        if (s == null)   // null is void, i.e., unreachable code
          {
            break;
          }
        res = res.andThen(s);
      }
    return res;
  }


  /*
   * Produce the unit type value.  This is used as a placeholder
   * for the universe instance as well as for the instance 'unit'.
   */
  public Expr unitValue()
  {
    return Expr.UNIT;
  }


  /**
   * Called before each statement is processed.  May be used to, e.g., produce
   * tracing code for debugging or a comment.
   *
   * @param cl the clazz we are compiling
   *
   * @param c the code block to compile
   *
   * @param i index of the access statement, must be ExprKind.Assign or ExprKind.Call
   */
  public Expr statementHeader(int cl, int c, int i)
  {
    return _jvm.trace(cl, c, i);
  }


  /**
   * A comment, adds human readable information
   */
  public Expr comment(String s)
  {
    return Expr.comment(s);
  }


  /**
   * no operation, like comment, but without giving any comment.
   */
  public Expr nop()
  {
    return Expr.UNIT;
  }


  /**
   * drop a value, but process its side-effect.
   *
   * @param v an expression that calculates a value that is not needed, but
   * where the calculation might have side-effects (like performing a call) that
   * we do need.
   *
   * @param type clazz id for the type of the value
   *
   * @return code to perform the side effects of v and ignoring the produced value.
   */
  public Expr drop(Expr v, int type)
  {
    // Check consistency between v.type() and type:
    if (CHECKS) check
      (v.type()                instanceof PrimitiveType pt && pt == _types.resultType(type) ||
       v.type()                instanceof ClassType     jt &&
       _types.resultType(type) instanceof ClassType     ct &&
       (_fuir.clazzIsRef(type) /* we do not check exact reference assignability here */ ||
        jt.sameAs(ct)          /* but value or choice types must be the same!        */ ));

    return v.andThen(v.type().pop());
  }


  /**
   * Determine the address of a given value.  This is used on a call to an
   * inner feature to pass a reference to the outer value type instance.
   */
  public Pair<Expr, Expr> adrOf(Expr v)
  {
    return new Pair<>(v, Expr.UNIT);
  }


  /**
   * Create code to assign value to a given field w/o dynamic binding.
   *
   * @param tc clazz id of the target instance
   *
   * @param f clazz id of the assigned field
   *
   * @param rt clazz of the field type
   *
   * @param tvalue the target instance
   *
   * @param val the new value to be assigned to the field.
   *
   * @return statement to perform the given assignment
   */
  public Expr assignStatic(int tc, int f, int rt, Expr tvalue, Expr val)
  {
    if (_fuir.clazzIsOuterRef(f) && _fuir.clazzIsUnitType(rt))
      {
        return val.drop().andThen(tvalue.drop());
      }
    else
      {
        return assignField(tvalue, f, val, rt);
      }
  }


  /**
   * Perform an assignment of a value to a field in tvalue. The type of tvalue
   * might be dynamic (a reference). See FUIR.access*().
   * local variable.
   *
   * @param cl the clazz we are compiling
   *
   * @param pre true iff we are compiling the precondition
   *
   * @param c the code block to compile
   *
   * @param i index of the access statement, must be ExprKind.Assign or ExprKind.Call
   *
   */
  public Expr assign(int cl, boolean pre, int c, int i, Expr tvalue, Expr avalue)
  {
    return access(cl, pre, c, i, tvalue, new List<>(avalue))._v1;
  }


  /**
   * If e might have some side effect, evaluate e and store the result in a new
   * local variable.
   *
   * This is used in case e's value is needed repeatedly as in passing it to a
   * precondition check before it is passed to the actual call.
   *
   * @param cl the clazz we are compiling
   *
   * @param pre true iff we are compiling the precondition
   *
   * @param e expression to evaluation and store in a local, null for void value
   *
   * @return a pair of a new expression that loads the value of e and an
   * expression that evaluates e and stores it in a local variable.
   */
  Pair<Expr,Expr> storeInLocal(int cl, boolean pre, Expr e)
  {
    if (e == null)
      {
        return new Pair<>(null, Expr.UNIT);
      }
    else
      {
        var t = e.type();
        var l = _jvm.allocLocal(cl, pre, t.stackSlots());
        return new Pair<>(t.load(l),
                          e.andThen(t.store(l)));
      }
  }


  /**
   * Perform a call of a feature with target instance tvalue with given
   * arguments.  The type of tvalue might be dynamic (a reference). See
   * FUIR.access*().
   *
   * Result._v0 may be null to indicate that code generation should stop here
   * (due to an error or tail recursion optimization).
   *
   * @param cl the clazz we are compiling
   *
   * @param pre true iff we are compiling the precondition
   *
   * @param c the code block to compile
   *
   * @param i index of the access statement, must be ExprKind.Assign or ExprKind.Call
   *
   * @param tvalue the target of this call, CExpr.UNIT if none.
   *
   * @param args the arguments of this call.
   */
  public Pair<Expr, Expr> call(int cl, boolean pre, int c, int i, Expr tvalue, List<Expr> args)
  {
    var ccP = _fuir.accessedPreconditionClazz(cl, c, i);
    var cc0 = _fuir.accessedClazz            (cl, c, i);
    var s = Expr.UNIT;
    if (ccP != -1)   // call precondition:
      {
        // evaluate target and args and copy to local vars to avoid evaluating them twice.
        var pt = storeInLocal(cl, pre, tvalue);
        tvalue = pt._v0;
        s = s.andThen(pt._v1);
        var nargs = new List<Expr>();
        for (var a : args)
          {
            var pa = storeInLocal(cl, pre, a);
            s = s.andThen(pa._v1);
            nargs.add(pa._v0);
          }
        args = nargs;
        s = s.andThen(staticCall(cl, pre, tvalue, args, ccP, true));
      }
    var res = Expr.UNIT;
    if (_fuir.callPreconditionOnly(cl, c, i))
      {
        // NYI: double side-effects for args in inherits call
        //
        // the following code currently does not work for the JVM backend:
        //
        //   test =>
        //     f(p String) => say "in f $p"; 42+p.byte_length
        //
        //     x(a i32)
        //     pre
        //       a >= 42
        //     is
        //       say "in x $a"
        //
        //     y : x (f "b") is
        //       say "in y"
        //
        //     say "calling x"
        //
        //     _ := x (f "aaa")
        //     say "calling y"
        //     a := y
        //     b := a
        //
        // This happens when the call is part of an inherits clause. In
        // this case, the actual code for the call is inlined. In this case,
        // saving the args in locals does not help against executing their
        // side-effects twice, so we must check if this works and fix it if not.
      }
    else
      {
        var r = access(cl, pre, c, i, tvalue, args);
        s = s.andThen(r._v1);
        res = r._v0;
      }
    return new Pair<>(res, s);
  }


  /**
   * Create code to access (call or write) a feature.
   *
   * @param cl the clazz we are compiling
   *
   * @param pre true iff we are compiling the precondition
   *
   * @param c the code block to compile
   *
   * @param i index of the access statement, must be ExprKind.Assign or ExprKind.Call
   *
   * @param tvalue the target of this call, CExpr.UNIT if none.
   *
   * @param args the arguments of this call, or, in case of an assignment, a
   * list of one element containing value to be assigned.
   *
   * @return pair of expression containing result value and statement to perform
   * the given access
   */
  Pair<Expr, Expr> access(int cl, boolean pre, int c, int i, Expr tvalue, List<Expr> args)
  {
    var res = Expr.UNIT;
    var s   = Expr.UNIT;
    var isCall = _fuir.codeAt(c, i) == FUIR.ExprKind.Call;
    var cc0 = _fuir.accessedClazz  (cl, c, i);
    var ccs = _fuir.accessedClazzes(cl, c, i);
    if (ccs.length == 0)
      {
        var rt = isCall ? _fuir.clazzResultClazz(cc0) : _fuir.clazz(FUIR.SpecialClazzes.c_unit);
        if (isCall && (_fuir.hasData(rt) || _fuir.clazzIsVoidType(rt)))
          {
            s = s.andThen(_jvm.reportErrorInCode("no targets for access of " + _fuir.clazzAsString(cc0) + " within " + _fuir.clazzAsString(cl)));
            res = null;
          }
        else
          {
            s = s.andThen(tvalue.drop());
            for (var a : args)
              {
                s = s.andThen(a.drop());
              }
            s = s.andThen(Expr.comment("access to " + _fuir.codeAtAsString(cl, c, i) + " eliminated"));
          }
      }
    else if (ccs.length > 2)
      {
        if (CHECKS) check
          (_fuir.hasData(_fuir.accessTargetClazz(cl, c, i)),  // would be strange if target is unit type
           _fuir.accessIsDynamic(cl, c, i));                  // or call is not dynamic

        var dynCall = args(true, tvalue, args, cc0, _fuir.clazzArgCount(cc0))
          .andThen(Expr.comment("Dynamic access of " + _fuir.clazzAsString(cc0)))
          .andThen(addDynamicFunctionAndStubs(cc0, ccs, isCall));
        if (AbstractInterpreter.clazzHasUniqueValue(_fuir, _fuir.clazzResultClazz(cc0)))
          {
            s = dynCall;  // make sure we do not throw away the code even if it is of unit type
          }
        else
          {
            res = dynCall;
          }
      }
    else
      {
        var tc = _fuir.accessTargetClazz(cl, c, i);
        var tt = ccs[0];                   // target clazz we match against
        var cc = ccs[1];                   // called clazz in case of match
        if (tc != tt)
          {
            tvalue = tvalue.andThen(Expr.checkcast(_types.javaType(tt)));
          }
        var calpair = staticAccess(cl, pre, tt, cc, tvalue, args, isCall);
        s = s.andThen(calpair._v1);
        res = calpair._v0;
        if (_fuir.clazzIsVoidType(_fuir.clazzResultClazz(cc)))
          {
            if (res != null)
              {
                s = s.andThen(res);
              }
            res = null;
          }
      }
    return new Pair<>(res, s);
  }


  /**
   * We have a dynamic access to feature cc0 with several targets, so we create an
   * interface with a dynamic function and add implementations to each actual
   * target.
   *
   * @param cc a feature whose outer feature is a ref that has several actual instances.
   *
   * @return the invokeinterface expression that performs the call
   */
  private Expr addDynamicFunctionAndStubs(int cc0, int[] ccs, boolean isCall)
  {
    var intfc = _types.interfaceFile(_fuir.clazzOuterClazz(cc0));
    var rc = _fuir.clazzResultClazz(cc0);
    var dn = _names.dynamicFunction(cc0);
    var ds = isCall ? _types.dynDescriptor(cc0, false)          : "(" + _types.javaType(rc).descriptor() + ")V";
    var dr = isCall ? _types.javaType(rc)                       : PrimitiveType.type_void;
    var da = isCall ? _types.dynDescriptorArgsCount(cc0, false) : 1;
    if (!intfc.hasMethod(dn))
      {
        intfc.method(ACC_PUBLIC | ACC_ABSTRACT, dn, ds, new List<>());
        check(intfc.hasMethod(dn));
      }
    for (var cci = 0; cci < ccs.length; cci += 2)
      {
        var tt = ccs[cci  ];    // target clazz we match against
        var cc = ccs[cci+1];    // called clazz
        _types.classFile(tt).addImplements(intfc._name);
        addStub(tt, cc, dn, ds, isCall);
      }
    return Expr.invokeInterface(intfc._name, dn, ds, dr, da);
  }


  /**
   * Create a stub method, i.e., an implementation of a dynamic function defined
   * in an interface that performs a static access for the given target type.
   *
   * @param tt the target clazz. Note that tt may be different to
   * _fuir.clazzOuterClazz(cc), e.g., if tt is some type defining abstract
   * feature and _fuir.clazzOuterClazz(cc) is the only possible heir at this
   * access. Or tt may be boxed, while actual outer clazz is unboxed.
   *
   * @param cc the called clazz
   *
   * @param dn the name of the stub to create
   *
   * @param ds the descriptor of the stub to create
   *
   * @param isCall true if the access is a call, false if it is an assignment to
   * a field.
   */
  private void addStub(int tt, int cc, String dn, String ds, boolean isCall)
  {
    var cf = _types.classFile(tt);
    if (!cf.hasMethod(dn))
      {
        var tv = _types.javaType(tt).load(0);
        var na = new List<Expr>();
        int slot = 1;
        Expr retoern = Expr.RETURN;
        if (isCall)
          {
            for (var ai = 0; ai < _fuir.clazzArgCount(cc); ai++)
              {
                var at = _fuir.clazzArgClazz(cc, ai);
                var t = _types.resultType(at);
                na.add(t.load(slot));
                slot = slot + t.stackSlots();
              }
            retoern = _types.javaType(_fuir.clazzResultClazz(cc)).return0();
          }
        else
          {
            var t = _types.javaType(_fuir.clazzResultClazz(cc));
            na.add(t.load(1));
          }
        var p = staticAccess(-1, false, tt, cc, tv, na, isCall);
        var code = p._v0 == null
          ? p._v1
          : (p._v1.andThen(p._v0)
                  .andThen(retoern));
        var ca = cf.codeAttribute(dn + "in class for " + _fuir.clazzAsString(tt),
                                  code, new List<>(), new List<>());
        cf.method(ACC_PUBLIC, dn, ds, new List<>(ca));
      }
  }


  /**
   * Create code for a static access (call or assignment).  This is used by
   * access to create code if there is only one possible target and by stubs to
   * perform the actual access.
   *
   * @param cl the clazz we are compiling or -1 if we are creating a stub
   *
   * @param pre true iff we are compiling the precondition
   *
   * @param tt the target clazz. Note that tt may be different to
   * _fuir.clazzOuterClazz(cc), e.g., if tt is some type defining abstract
   * feature and _fuir.clazzOuterClazz(cc) is the only possible heir at this
   * access. Or tt may be boxed, while actual outer clazz is unboxed.
   *
   * @param cc the accessed clazz.
   *
   * @param tv the target value.
   *
   * @param args the arguments
   *
   * @param isCall true if the access is a call, false if it is an assignment to
   * a field.
   *
   * @return the result and code to perform the access.
   */
  Pair<Expr, Expr> staticAccess(int cl, boolean pre, int tt, int cc, Expr tv, List<Expr> args, boolean isCall)
  {
    var cco = _fuir.clazzOuterClazz(cc);   // actual outer clazz of called clazz, more specific than tt
    if (_fuir.clazzIsBoxed(tt) &&
        !_fuir.clazzIsRef(cco)  // NYI: would be better if the AbstractInterpreter would
                                // not confront us with boxed referenced here, such that
                                // this second could be removed.
        )
      { // in case we access the value in a boxed target, unbox it first:
        tv = Expr.comment("UNBOXING , boxed type "+_fuir.clazzAsString(tt)+" desired type "+_fuir.clazzAsString(cco))
          .andThen(tv.getFieldOrUnit(_names.javaClass(tt),    // note that tv.getfield works vor unit type (resulting in tv.drop()).
                                     Names.BOXED_VALUE_FIELD_NAME,
                                     _types.javaType(cco)));
      }

    return isCall ? staticCall(cl, pre, tv, args, cc, false)
                  : new Pair<>(Expr.UNIT,
                               assignField(tv, cc, args.get(0), _fuir.clazzResultClazz(cc)));
  }


  /**
   * Create code to assign value to a field
   *
   * @param tc the static target clazz
   *
   * @param tt the actual target clazz in case the assignment is dynamic
   *
   * @param f the field
   */
  Expr assignField(Expr tvalue, int f, Expr value, int rt)
  {
    if (CHECKS) check
      (tvalue != null || !_fuir.hasData(rt) || _fuir.clazzOuterClazz(f) == _fuir.clazzUniverse());
    var occ   = _fuir.clazzOuterClazz(f);
    var vocc  = _fuir.clazzAsValue(occ);
    Expr res;
    if (_fuir.clazzIsVoidType(rt))
      {
        // NYI: this should IMHO not happen, where does value come from?
        //
        //   throw new Error("assignField called for void type");
        res = null;
      }
    else if (_jvm.fieldExists(f))
      {
        if (_fuir.clazzOuterClazz(f) == _fuir.clazzUniverse())
          {
            tvalue = tvalue
              .andThen(_jvm.LOAD_UNIVERSE);
          }
        res = tvalue
          .andThen(value)
          .andThen(_jvm.putfield(f));
      }
    else
      {
        res = Expr.comment("Not setting field `" + _fuir.clazzAsString(f) + "`: "+
                           (!_fuir.hasData(rt)       ? "type `" + _fuir.clazzAsString(rt) + "` is a unit type" :
                            _types.isScalar(occ) ? "target type is a scalar `" + _fuir.clazzAsString(occ) + "`"
                                                 : "FUIR.clazzNeedsCode() is false for this field"))
          // make sure we evaluate tvalue and value:
          .andThen(tvalue.drop())
          .andThen(value.drop());
      }
    return res;
  }


  /**
   * Create code to read value of a field
   *
   * @param tvalue the target instance to read the field from
   *
   * @param tc the static target clazz
   *
   * @param f the field
   */
  Expr readField(Expr tvalue, int tc, int f, int rt)
  {
    if (CHECKS) check
      (tvalue != null || !_fuir.hasData(rt));

    var occ = _fuir.clazzOuterClazz(f);
    if (occ == _fuir.clazzUniverse())
      {
        tvalue = tvalue
          .andThen(_jvm.LOAD_UNIVERSE);
      }
    return
      _types.isScalar(occ)      ? tvalue :   // reading, e.g., `val` field from `i32` is identity operation
      _fuir.clazzIsVoidType(rt) ? null       // NYI: this should not be possible, a field of type void is guaranteed to be uninitialized!
                                : tvalue.getFieldOrUnit(_names.javaClass(occ),
                                                        _names.field(f),
                                                        _types.resultType(rt));
  }


  /**
   * Create code for a statically bound call.
   *
   * @param cl the clazz we are compiling, -1 if this is a call in an interface method stub.
   *
   * @param pre true iff we are compiling the precondition
   *
   * @param tvalue the target value of the call
   *
   * @param args the arguments to the call
   *
   * @param cc clazz that is called
   *
   * @param preCalled true to call the precondition of cc instead of cc.
   *
   * @return the code to perform the call
   */
  Pair<Expr, Expr> staticCall(int cl, boolean pre, Expr tvalue, List<Expr> args, int cc, boolean preCalled)
  {
    var tc = _fuir.clazzOuterClazz(cc);
    Expr code = Expr.UNIT;
    var val = Expr.UNIT;
    var rt = _fuir.clazzResultClazz(cc);
    switch (preCalled ? FUIR.FeatureKind.Routine : _fuir.clazzKind(cc))
      {
      case Abstract :
        Errors.error("Call to abstract feature encountered.",
                     "Found call to  " + _fuir.clazzAsString(cc));
      case Intrinsic:
        {
          if (_fuir.clazzTypeParameterActualType(cc) != -1)  /* type parameter is also of Kind Intrinsic, NYI: should better have its own kind?  */
            {
              return new Pair<>(Expr.UNIT, tvalue.drop());
            }
          else if (!(preCalled || Intrinsix.inRuntime(_jvm, cc)))
            {
              return Intrinsix.inlineCode(_jvm, cc, tvalue, args);
            }
          // fall through!
        }
      case Routine  :
      case Native   :
        {
          if (_types.clazzNeedsCode(cc))
            {
              if (!preCalled                                             &&  // not calling pre-condition
                  cc == cl                                               &&  // calling myself
                  false && // NYI: _tailCall.callIsTailCall(cl, c, i)                     &&  // as a tail call
                  _fuir.lifeTime(cl, pre).ordinal() <=
                  FUIR.LifeTime.Call.ordinal()                               // and current instance did not escape
                )
                { // then we can do tail recursion optimization!
                  throw new Error("NYI: tailcall");
                  // code = tailRecursion(cl, c, i, tc, a);
                  // val = null;
                }
              else
                {
                  var oc = _fuir.clazzOuterClazz(cc);
                  if (_fuir.clazzIsRef(oc))
                    { // the type of tvalue is oc's interface, we need the actual class:
                      tvalue = tvalue.andThen(Expr.checkcast(_types.resultType(oc)));
                    }
                  var call = args(false, tvalue, args, cc, _fuir.clazzArgCount(cc))
                    .andThen(_types.invokeStatic(cc, preCalled));

                  if (!true)  // NYI: the following code does not work, seams like a unit-type val is sometimes thrown away instead of .drop()ped.
                    {
                      if (preCalled || _fuir.clazzIsVoidType(rt))
                        {
                          code = call;
                          val = preCalled ? Expr.UNIT : null;
                        }
                      else
                        {
                          val = call;
                          code = Expr.UNIT;
                        }
                    }
                  else
                    {
                      if (!preCalled && _fuir.hasData(rt))
                        {
                          val = call;
                        }
                      else
                        {
                          code = call;
                          if (_fuir.clazzIsVoidType(rt))
                            {
                              val = null;
                            }
                        }
                    }
                }
            }
          break;
        }
      case Field:
        {
          val = readField(tvalue, tc, cc, rt);
          break;
        }
      default:       throw new Error("This should not happen: Unknown feature kind: " + _fuir.clazzKind(cc));
      }
    return new Pair<>(val, code);
  }


  /**
   * Create C code to pass given number of arguments plus one implicit target
   * argument from the stack to a called feature.
   *
   * @param needTarget true if the target value must not be dropped even if it
   * is unused by the called feature cc. This is the case if we perform a
   * dynamic call, so the target ref is needed for dynamic dispatch.
   *
   * @param cc clazz that is called
   *
   * @param argCount the number of arguments.
   *
   * @return list of arguments to be passed to CExpr.call
   */
  Expr args(boolean needTarget, Expr tvalue, List<Expr> args, int cc, int argCount)
  {
    // first, recursively get args before argCount:
    var result = argCount == 0 ? Expr.UNIT
                               : args(needTarget, tvalue, args, cc, argCount-1);

    // then add tvalue/arg #argCount:
    var add = argCount > 0                                 ? args.get(argCount-1) :
              !needTarget && _fuir.clazzOuterRef(cc) == -1 ? tvalue.drop()
                                                           : tvalue;
    return result.andThen(add);
  }


  /**
   * For a given value v of value type vc create a boxed ref value of type rc.
   */
  public Pair<Expr, Expr> box(Expr val, int vc, int rc)
  {
    var res = val;
    if (!_fuir.clazzIsRef(vc) && _fuir.clazzIsRef(rc))  // NYI: would be good if the AbstractInterpreter would not call box() in this case
      {
        var n = _names.javaClass(rc);
        res = Expr.comment("box from "+_fuir.clazzAsString(vc)+" to "+_fuir.clazzAsString(rc))
          .andThen(val)
          .andThen(Expr.invokeStatic(n, Names.BOX_METHOD_NAME,
                                     _types.boxSignature(rc),
                                     _types.javaType(rc))
                   );
      }
    return new Pair<>(res, Expr.UNIT);
  }


  /**
   * Get the current instance
   */
  public Pair<Expr, Expr> current(int cl, boolean pre)
  {
    if (_types.isScalar(cl))
      {
        return new Pair<>(_types.javaType(cl).load(0), Expr.UNIT);
      }
    else
      {
        return new Pair<>(Expr.aload(_jvm.current_index(cl), _types.javaType(cl)), Expr.UNIT);
      }
  }


  /**
   * Get the outer instance the given clazz is called on.
   */
  public Pair<Expr, Expr> outer(int cl)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzResultClazz(_fuir.clazzOuterRef(cl)) == _fuir.clazzOuterClazz(cl));

    return new Pair<>(_types.javaType(_fuir.clazzOuterClazz(cl)).load(0),
                      Expr.UNIT);
  }


  /**
   * Get the argument #i
   */
  public Expr arg(int cl, int i)
  {
    var o = _fuir.clazzOuterRef(cl);
    var ot = o == -1 ? -1 : _fuir.clazzResultClazz(o);
    var l = ot == -1 ? 0 : _types.javaType(ot).stackSlots();
    for (var j = 0; j < i; j++)
      {
        var t = _fuir.clazzArgClazz(cl, j);
        l = l + _types.javaType(t).stackSlots();
      }
    var t = _fuir.clazzArgClazz(cl, i);
    var jt = _types.javaType(t);
    return jt.load(l);
  }


  /**
   * Get a constant value of type constCl with given byte data d.
   */
  public Pair<Expr, Expr> constData(int constCl, byte[] d)
  {
    var s = Expr.UNIT;
    var r = switch (_fuir.getSpecialId(constCl))
      {
      case c_bool -> Expr.iconst(d[0]);
      case c_i8   -> Expr.iconst(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).get     ());
      case c_i16  -> Expr.iconst(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getShort());
      case c_i32  -> Expr.iconst(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getInt  ());
      case c_i64  -> Expr.lconst(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getLong ());
      case c_u8   -> Expr.iconst(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).get     () & 0xff);
      case c_u16  -> Expr.iconst(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff);
      case c_u32  -> Expr.iconst(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getInt  ());
      case c_u64  -> Expr.lconst(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getLong ());
      case c_f32  -> Expr.fconst(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getInt  ());
      case c_f64  -> Expr.dconst(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getLong ());
      case c_Const_String ->
      {
        var p = _jvm.constString(d);
        s = p._v1;
        yield p._v0;
      }
      default ->
      {
        Errors.error("Unsupported constant in JVM backend.",
                     "Backend cannot handle constant of clazz '" + _fuir.clazzAsString(constCl) + "' ");
        yield null;
      }
      };
    return new Pair<>(r, s);
  }


  /**
   * Perform a match on value subv.
   *
   * @param ai the abstract interpreter instance
   *
   * @param cl the clazz we are compiling
   *
   * @param pre true iff we are compiling the precondition
   *
   * @param c the code block to compile
   *
   * @param i index of the access statement, must be ExprKind.Assign or ExprKind.Call
   *
   * @param sub code to produce the match subject value
   *
   * @return the code for the match, produces unit type result.
   */
  public Pair<Expr, Expr> match(AbstractInterpreter<Expr, Expr> ai, int cl, boolean pre, int c, int i, Expr sub)
  {
    var code = _choices.match(_jvm, ai, cl, pre, c, i, sub);
    return new Pair<>(Expr.UNIT, code);
  }


  /**
   * Create a tagged value of type newcl from an untagged value for type valuecl.
   *
   * @param cl the clazz we are compiling
   *
   * @param valuecl the original clazz of the value that is to be tagged. NYI: remove?
   *
   * @param value code to produce the value we are tagging
   *
   * @param newcl the choice type after tagging
   *
   * @param tagNum the tag number, corresponding to the choice type in
   * _fuir.clazzChoice(newcl, tagNum).
   *
   * @return code to produce the tagged value as a result.
   */
  public Pair<Expr, Expr> tag(int cl, int valuecl, Expr value, int newcl, int tagNum)
  {
    var res = _choices.tag(_jvm, cl, value, newcl, tagNum);
    return new Pair<>(res, Expr.UNIT);
  }


  /**
   * Access the effect of type ecl that is installed in the environment.
   */
  public Pair<Expr, Expr> env(int ecl)
  {
    var res =
      Expr.iconst(_fuir.clazzId2num(ecl))
      .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                 Names.RUNTIME_EFFECT_GET,
                                 Names.RUNTIME_EFFECT_GET_SIG,
                                 Names.ANY_TYPE))
      .andThen(Expr.checkcast(_types.javaType(ecl)));
    return new Pair<>(res, Expr.UNIT);
  }


  /**
   * Process a contract of kind ck of clazz cl that results in bool value cc
   * (i.e., the contract fails if !cc).
   */
  public Expr contract(int cl, FUIR.ContractKind ck, Expr cc)
  {
    return cc.andThen(Expr.branch(O_ifeq,
                                  Expr.stringconst("" + ck + " on call to '" + _fuir.clazzAsString(cl) + "'")
                                  .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                                             Names.RUNTIME_CONTRACT_FAIL,
                                                             Names.RUNTIME_CONTRACT_FAIL_SIG,
                                                             ClassFileConstants.PrimitiveType.type_void)),
                                  Expr.UNIT));
  }


}

/* end of file */
