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
import dev.flang.be.jvm.classfile.ClassFile;
import dev.flang.be.jvm.classfile.ClassFile.Attribute;
import dev.flang.be.jvm.classfile.ClassFileConstants;

import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.Pair;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;



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
        jt.sameAs(ct)          /* but value or choice types must be the same!        */ ||
        jt == NULL_TYPE));

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
   * @param cl id of clazz we are interpreting
   *
   * @param pre true iff interpreting cl's precondition, false for cl itself.
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
  public Expr assignStatic(int cl, boolean pre, int tc, int f, int rt, Expr tvalue, Expr val)
  {
    if (_fuir.clazzIsOuterRef(f) && _fuir.clazzIsUnitType(rt))
      {
        return val.drop().andThen(tvalue.drop());
      }
    else
      {
        return _jvm.assignField(cl, pre, tvalue, f, val, rt);
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
   * @param tvalue the target instance
   *
   * @param avalue the new value to be assigned to the field.
   */
  public Expr assign(int cl, boolean pre, int c, int i, Expr tvalue, Expr avalue)
  {
    var p = access(cl, pre, c, i, tvalue, new List<>(avalue));

    if (CHECKS) check
      (p._v0 == Expr.UNIT);

    return p._v1;
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
        var cf = _types.classFile(cl);
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
    var p = combinePreconditionAndCall(cl, pre, c, i, tvalue, args);
    if (p == null)
      {
        var ccP = _fuir.accessedPreconditionClazz(cl, c, i);
        var cc0 = _fuir.accessedClazz            (cl, c, i);
        var s = Expr.UNIT;
        var res = Expr.UNIT;
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
            s = s.andThen(staticCall(cl, pre, tvalue, args, ccP, true, c, i));
          }
        if (!_fuir.callPreconditionOnly(cl, c, i))
          {
            var r = access(cl, pre, c, i, tvalue, args);
            s = s.andThen(r._v1);
            res = r._v0;
          }
        p = new Pair<>(res, s);
      }
    return p;
  }


  /**
   * Optimization for call for the common case that a precondition is checked
   * followed by a call to a statically bound routine of the same class. This
   * call is then replaced by a call to a combined method that checks the
   * precondition and then calls the routine.
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
   *
   * @return Result value and code generated for this combined call, null if
   * combined call is not possible.
   */
  Pair<Expr, Expr> combinePreconditionAndCall(int cl, boolean pre, int c, int i, Expr tvalue, List<Expr> args)
  {
    Pair<Expr, Expr> res = null;
    var ccP = _fuir.accessedPreconditionClazz(cl, c, i);
    var ccs = _fuir.accessedClazzes(cl, c, i);
    if (ccs.length == 2)
      {
        var tt = ccs[0];                   // target clazz we match against
        var cc = ccs[1];                   // called clazz in case of match
        if (cc == ccP &&
            _fuir.clazzKind(ccP) == FUIR.FeatureKind.Routine &&
            !_fuir.clazzIsBoxed(tt) &&
            cc != cl /* not a call to current clazz that might need tail-call optimization */ &&
            _types.clazzNeedsCode(cc)
            )
          {
            var tc = _fuir.accessTargetClazz(cl, c, i);
            if (tc != tt || _types.hasInterfaceFile(tc))
              {
                tvalue = tvalue.andThen(Expr.checkcast(_types.javaType(tc)));
              }
            var call = args(false, tvalue, args, cc, _fuir.clazzArgCount(cc))
              .andThen(_types.invokeStaticCombindedPreAndCall(cc));

            var rt = _fuir.clazzResultClazz(cc);
            res = makePair(call, rt);
          }
      }
    return res;
  }


  /**
   * Create a value / code pair from code that produces a value of the given
   * Fuzion type.
   *
   * In case of a 'normal' type, this just creates a Pair with _v0 set to value
   * and _v1 to Expr.UNIT.
   *
   * For a type that is compiled to a unit type (Java's void type), execution of
   * the code in value is still require even though the value will be unused, so
   * we return Pair(Expr.UNIT, value).
   *
   * Finally, if rt is Fuzion's void type, i.e., the execution will never
   * return, this creates Pair(null, value).
   *
   * @param value code to calculate a value of type rt
   *
   * @param rt the type, may be unit or void
   *
   * @return a Pair that produces the value and the desired side effects.
   */
  Pair<Expr, Expr> makePair(Expr value, int rt)
  {
    var code = Expr.UNIT;
    if (_types.resultType(rt) == PrimitiveType.type_void)
      { // there is no Java value, so treat as code:
        code = value;
        value = _fuir.clazzIsVoidType(rt) ? null : Expr.UNIT;
      }
    return new Pair(value, code);
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
    var isCall = _fuir.codeAt(c, i) == FUIR.ExprKind.Call;  // call or assignment?
    var cc0 = _fuir.accessedClazz  (cl, c, i);
    var ccs = _fuir.accessedClazzes(cl, c, i);
    var rt = isCall ? _fuir.clazzResultClazz(cc0) : _fuir.clazz(FUIR.SpecialClazzes.c_unit);
    if (ccs.length == 0)
      {
        s = s.andThen(tvalue.drop());
        for (var a : args)
          {
            s = s.andThen(a.drop());
          }
        if (isCall && (_fuir.hasData(rt) || _fuir.clazzIsVoidType(rt)))  // we need a non-unit result and do not know what to do with this call, so flag an error
          {
            s = s.andThen(_jvm.reportErrorInCode("no targets for access of " + _fuir.clazzAsString(cc0) + " within " + _fuir.clazzAsString(cl)));
            res = null;
          }
        else  // an assignment to an unused field or unit-type call, that is fine to remove, just add a comment
          {
            s = s.andThen(Expr.comment("access to " + _fuir.codeAtAsString(cl, c, i) + " eliminated"));
          }
      }
    else if (ccs.length > 2)
      {
        if (CHECKS) check
          (_fuir.hasData(_fuir.accessTargetClazz(cl, c, i)),  // would be strange if target is unit type
           _fuir.accessIsDynamic(cl, c, i));                  // or call is not dynamic

        var dynCall = args(true, tvalue, args, cc0, isCall ? _fuir.clazzArgCount(cc0) : 1)
          .andThen(Expr.comment("Dynamic access of " + _fuir.clazzAsString(cc0)))
          .andThen(addDynamicFunctionAndStubs(cc0, ccs, isCall));
        if (AbstractInterpreter.clazzHasUniqueValue(_fuir, rt))
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
        if (tc != tt || _types.hasInterfaceFile(tc))
          {
            tvalue = tvalue.andThen(Expr.checkcast(_types.javaType(tt)));
          }
        var calpair = staticAccess(cl, pre, tt, cc, tvalue, args, isCall, c, i);
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
    var ds = isCall ? _types.dynDescriptor(cc0, false)          : "(" + _types.javaType(rc).argDescriptor() + ")V";
    var dr = isCall ? _types.resultType(rc)                       : PrimitiveType.type_void;
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
    return Expr.invokeInterface(intfc._name, dn, ds, dr);
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
        var jtt = _types.javaType(tt);
        var tv = jtt.load(0);
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
            retoern = _types.resultType(_fuir.clazzResultClazz(cc)).return0();
          }
        else
          {
            var t = _types.javaType(_fuir.clazzResultClazz(cc));
            na.add(t.load(1));
          }
        var p = staticAccess(-1, false, tt, cc, tv, na, isCall, -1, -1);
        var code = p._v1
          .andThen(p._v0 == null ? Expr.UNIT : p._v0)
          .andThen(retoern);
        var ca = cf.codeAttribute(dn + "in class for " + _fuir.clazzAsString(tt),
                                  code, new List<>(), new List<Attribute>(ClassFile.StackMapTable.empty(cf)));
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
  Pair<Expr, Expr> staticAccess(int cl, boolean pre, int tt, int cc, Expr tv, List<Expr> args, boolean isCall, int c, int i)
  {
    var cco = _fuir.clazzOuterClazz(cc);   // actual outer clazz of called clazz, more specific than tt
    if (_fuir.clazzIsBoxed(tt) &&
        !_fuir.clazzIsRef(cco)  // NYI: CLEANUP: would be better if the AbstractInterpreter would
                                // not confront us with boxed references here, such that
                                // this special handling could be removed.
        )
      { // in case we access the value in a boxed target, unbox it first:
        tv = Expr.comment("UNBOXING , boxed type "+_fuir.clazzAsString(tt)+" desired type "+_fuir.clazzAsString(cco))
          .andThen(tv.getFieldOrUnit(_names.javaClass(tt),    // note that tv.getfield works vor unit type (resulting in tv.drop()).
                                     Names.BOXED_VALUE_FIELD_NAME,
                                     _types.javaType(cco)));
      }

    return isCall ? staticCall(cl, pre, tv, args, cc, false, c, i)
                  : new Pair<>(Expr.UNIT,
                               _jvm.assignField(cl, pre, tv, cc, args.get(0), _fuir.clazzResultClazz(cc)));
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
  Pair<Expr, Expr> staticCall(int cl, boolean pre, Expr tvalue, List<Expr> args, int cc, boolean preCalled, int c, int i)
  {
    Pair<Expr, Expr> res;
    var oc = _fuir.clazzOuterClazz(cc);
    var rt = preCalled ? _fuir.clazz(FUIR.SpecialClazzes.c_unit) : _fuir.clazzResultClazz(cc);
    var cf = _types.classFile(cl);
    switch (preCalled ? FUIR.FeatureKind.Routine : _fuir.clazzKind(cc))
      {
      case Abstract :
        Errors.error("Call to abstract feature encountered.",
                     "Found call to  " + _fuir.clazzAsString(cc));
      case Intrinsic:
        {
          if (_fuir.clazzTypeParameterActualType(cc) != -1)  /* type parameter is also of Kind Intrinsic, NYI: CLEANUP: should better have its own kind?  */
            {
              return new Pair<>(Expr.UNIT, tvalue.drop());
            }
          else if (!(preCalled || Intrinsix.inRuntime(_jvm, cc)))
            {
              return Intrinsix.inlineCode(_jvm, cl, pre, cc, tvalue, args);
            }
          // fall through!
        }
      case Routine  :
      case Native   :
        {
          if (_types.clazzNeedsCode(cc))
            {
              if (!preCalled                                                             // not calling pre-condition
                  && cc == cl                                                            // calling myself
                  && c != -1 && i != -1 && _jvm._tailCall.callIsTailCall(cl, c, i)       // as a tail call
                  && _fuir.lifeTime(cl, pre).ordinal() <= FUIR.LifeTime.Call.ordinal())
                { // then we can do tail recursion optimization!

                  // if present, store target to local #0
                  var ot = _jvm.javaTypeOfTarget(cl);
                  var code = ot == PrimitiveType.type_void ? tvalue.drop()
                                                           : tvalue.andThen(ot.store(0));

                  // store arguments to local vars
                  for (int ai = 0; ai < _fuir.clazzArgCount(cl); ai++)
                    {
                      code = code.andThen(setArg(cl, ai, args.get(ai)));
                    }

                  // perform tail call by goto startLabel
                  code = code.andThen(Expr.goBacktoLabel(_jvm.startLabel(cl)));

                  res = new Pair(null,  // result is void, we do not return from this path.
                                 code);
                }
              else
                {
                  if (_fuir.clazzIsRef(oc))
                    { // the type of tvalue is oc's interface, we need the actual class:
                      tvalue = tvalue.andThen(Expr.checkcast(_types.resultType(oc)));
                    }
                  var call = args(false, tvalue, args, cc, _fuir.clazzArgCount(cc))
                    .andThen(_types.invokeStatic(cc, preCalled));

                  res = makePair(call, rt);
                }
            }
          else
            {
              res = new Pair(Expr.UNIT, Expr.UNIT);
            }
          break;
        }
      case Field:
        {
          res = makePair(_jvm.readField(tvalue, oc, cc, rt), rt);
          break;
        }
      default:       throw new Error("This should not happen: Unknown feature kind: " + _fuir.clazzKind(cc));
      }
    return res;
  }


  /**
   * Create code to pass given number of arguments plus one implicit target
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
    if (!_fuir.clazzIsRef(vc) && _fuir.clazzIsRef(rc))  // NYI: CLEANUP: would be good if the AbstractInterpreter would not call box() in this case
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
        return new Pair<>(Expr.aload(_jvm.current_index(cl), _types.resultType(cl)), Expr.UNIT);
      }
  }


  /**
   * Get the outer instance the given clazz is called on.
   */
  public Pair<Expr, Expr> outer(int cl)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzResultClazz(_fuir.clazzOuterRef(cl)) == _fuir.clazzOuterClazz(cl));

    var cf = _types.classFile(cl);

    return new Pair<>(_types.javaType(_fuir.clazzOuterClazz(cl)).load(0),
                      Expr.UNIT);
  }


  /**
   * Get the value argument #i from the slot that contains the argument at the
   * beginning of a call to the Java code of cl.
   *
   * @param cl the clazz we are compiling.
   *
   * @param i index the local variable we want to get
   *
   * @return code to read arg #i from its slot.
   */
  public Expr arg(int cl, int i)
  {
    if (PRECONDITIONS) require
      (0 <= i,
       i < _fuir.clazzArgCount(cl));

    var cf = _types.classFile(cl);
    var l = _jvm.argSlot(cl, i);
    var t = _fuir.clazzArgClazz(cl, i);
    var jt = _types.resultType(t);
    return jt.load(l);
  }


  /**
   * Set the argument #i to the given value.
   *
   * This is used for tail-call optimization to store a new value for an
   * argument in the local variable slot(s) for that argument.
   *
   * @param cl the clazz we are compiling
   *
   * @param i the number of the argument
   *
   * @param val the value of the argument.
   *
   * @return code that stores `val` into the slot of arg #i.
   */
  Expr setArg(int cl, int i, Expr val)
  {
    if (PRECONDITIONS) require
      (0 <= i,
       i < _fuir.clazzArgCount(cl));

    var l = _jvm.argSlot(cl, i);
    var t = _fuir.clazzArgClazz(cl, i);
    var jt = _types.resultType(t);
    var cf = _types.classFile(cl);
    return val.andThen(jt.store(l));
  }


  /**
   * Get a constant value of type constCl with given byte data d.
   */
  public Pair<Expr, Expr> constData(int constCl, byte[] d)
  {
    var c = createConstant(constCl, d);
    return switch (constantCreationStrategy(constCl))
      {
      case onEveryUse               -> c;  // create constant inline
      case onUniverseInitialization ->     // or create constant in universe' static initializer:
        {
          var ucl = _types.classFile(_fuir.clazzUniverse());
          var f = _names.preallocatedConstantField(constCl, d);
          var jt = _types.javaType(constCl);
          if (!ucl.hasField(f))
            {
              ucl.field(ACC_STATIC | ACC_PUBLIC,
                        f,
                        jt.descriptor(),
                        new List<>());
              ucl.addToClInit(c._v1);
              ucl.addToClInit(c._v0.andThen(Expr.putstatic(ucl._name, f, jt)));
            }
          yield new Pair<Expr, Expr>(Expr.getstatic(ucl._name, f, jt), Expr.UNIT);
        }
      };
  }


  /**
   * For a constant of given type, determine the creation strategy. In
   * particular, for primitive type constants, do not create them early and
   * cache them, while for more complex constants, it might be better to create
   * them early on and reuse the instance.
   *
   * @param constCl the clazz of the type of the constant.
   *
   * @return the creation strategy to use.
   */
  JVMOptions.ConstantCreation constantCreationStrategy(int constCl)
  {
    return switch (_fuir.getSpecialClazz(constCl))
      {
      case c_bool, c_i8 , c_i16, c_i32,
           c_i64 , c_u8 , c_u16, c_u32,
           c_u64 , c_f32, c_f64         -> JVMOptions.ConstantCreation.onEveryUse;
      default                           -> _jvm._options._constantCreationStrategy;
      };
  }


  /**
   * Create an instance of a constant value of type constCl with given byte data
   * d.
   *
   * @param constCl the type of the constant
   *
   * @param d the constant in serialized form
   *
   * @return the value and code to produce the constant
   */
  Pair<Expr, Expr> createConstant(int constCl, byte[] d)
  {
    return switch (_fuir.getSpecialClazz(constCl))
      {
      case c_bool         -> new Pair<>(Expr.iconst(d[0]                                                                 ), Expr.UNIT);
      case c_i8           -> new Pair<>(Expr.iconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).get     ()         ), Expr.UNIT);
      case c_i16          -> new Pair<>(Expr.iconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getShort()         ), Expr.UNIT);
      case c_i32          -> new Pair<>(Expr.iconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getInt  ()         ), Expr.UNIT);
      case c_i64          -> new Pair<>(Expr.lconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getLong ()         ), Expr.UNIT);
      case c_u8           -> new Pair<>(Expr.iconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).get     () &   0xff), Expr.UNIT);
      case c_u16          -> new Pair<>(Expr.iconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff), Expr.UNIT);
      case c_u32          -> new Pair<>(Expr.iconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getInt  ()         ), Expr.UNIT);
      case c_u64          -> new Pair<>(Expr.lconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getLong ()         ), Expr.UNIT);
      case c_f32          -> new Pair<>(Expr.fconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getInt  ()         ), Expr.UNIT);
      case c_f64          -> new Pair<>(Expr.dconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getLong ()         ), Expr.UNIT);
      case c_Const_String, c_String
                          -> _jvm.constString(Arrays.copyOfRange(d, 4, ByteBuffer.wrap(d).getInt()+4));
      default             ->
        {
          if (_fuir.clazzIsArray(constCl))
            {
              var elementType = this._fuir.inlineArrayElementClazz(constCl);

              var bb = ByteBuffer.wrap(d);
              var elCount = bb.getInt();
              var jt = this._types.resultType(elementType);
              var aLen = Expr
                .iconst(elCount);

              var result =  aLen
                .andThen(jt.newArray());

              for (int idx = 0; idx < elCount; idx++)
                {
                  var b = _fuir.deseralizeConst(elementType, bb);
                  var c = createConstant(elementType, b);
                  result = result
                    .andThen(Expr.DUP)                             // T[], T[]
                    .andThen(Expr.checkcast(jt.array()))
                    .andThen(Expr.iconst(idx))                     // T[], T[], idx
                    .andThen(c._v1)                                // T[], T[], idx, const-data-code
                    .andThen(c._v0)                                // T[], T[], idx, const-data-code
                    .andThen(jt.xastore());                        // T[]
                }
              yield _jvm.const_array(constCl, result);
            }
          else if (!_fuir.clazzIsChoice(constCl))
            {
              var b = ByteBuffer.wrap(d);
              var result = Expr.UNIT;
              for (int index = 0; index < _fuir.clazzArgCount(constCl); index++)
                {
                  var fr = _fuir.clazzArgClazz(constCl, index);
                  var bytes = _fuir.deseralizeConst(fr, b);
                  var c = createConstant(fr, bytes);
                  result = result
                    .andThen(c._v1)
                    .andThen(c._v0);
                }
              result = result
                .andThen(_types.invokeStaticCombindedPreAndCall(constCl));

              yield new Pair<>(result, Expr.UNIT);
            }
          else
            {
              Errors.error("Unsupported constant in JVM backend.",
                           "Backend cannot handle constant of clazz '" + _fuir.clazzAsString(constCl) + "' ");
              yield null;
            }
        }
      };
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
   * @param valuecl the original clazz of the value that is to be tagged. NYI: CLEANUP: remove?
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
                                                             ClassFileConstants.PrimitiveType.type_void))));
  }


}

/* end of file */
