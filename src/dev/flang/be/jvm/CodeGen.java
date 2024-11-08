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

import static dev.flang.ir.IR.NO_SITE;

import dev.flang.be.jvm.classfile.Expr;
import dev.flang.be.jvm.classfile.VerificationType;
import dev.flang.be.jvm.classfile.ClassFile;
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
  extends AbstractInterpreter.ProcessExpression<Expr, Expr>  // both, values and statements are implemented by Expr
  implements ClassFileConstants
{


  /*----------------------------  constants  ----------------------------*/


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
  @Override
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
  @Override
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
   * @param s site of the next expression
   */
  @Override
  public Expr expressionHeader(int s)
  {
    return _jvm.trace(s);
  }


  /**
   * A comment, adds human readable information
   */
  @Override
  public Expr comment(String s)
  {
    return Expr.comment(s);
  }


  /**
   * no operation, like comment, but without giving any comment.
   */
  @Override
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
  @Override
  public Expr drop(Expr v, int type)
  {
    if (CHECKS) check
      (primitiveTypeMatches(v.type(), type) || classTypeMatches(v.type(), type));

    return v.andThen(v.type().pop());
  }


  /**
   * Create code to assign value to a given field w/o dynamic binding.
   *
   * @param s cl id of clazz we are interpreting
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
  @Override
  public Expr assignStatic(int s, int tc, int f, int rt, Expr tvalue, Expr val)
  {
    if (_fuir.clazzIsOuterRef(f) && _fuir.clazzIsUnitType(rt))
      {
        return val.drop().andThen(tvalue.drop());
      }
    else
      {
        return _jvm.assignField(s, tvalue, f, val, rt);
      }
  }


  /**
   * Perform an assignment of a value to a field in tvalue. The type of tvalue
   * might be dynamic (a reference). See FUIR.access*().
   * local variable.
   *
   * @param s site of the assignment
   *
   * @param tvalue the target instance
   *
   * @param avalue the new value to be assigned to the field.
   */
  @Override
  public Expr assign(int s, Expr tvalue, Expr avalue)
  {
    var p = access(s, tvalue, new List<>(avalue));

    if (CHECKS) check
      (p.v0() == Expr.UNIT);

    return p.v1();
  }


  /**
   * Perform a call of a feature with target instance tvalue with given
   * arguments.  The type of tvalue might be dynamic (a reference). See
   * FUIR.access*().
   *
   * Result.v0() may be null to indicate that code generation should stop here
   * (due to an error or tail recursion optimization).
   *
   * @param si site of the call
   *
   * @param tvalue the target of this call, CExpr.UNIT if none.
   *
   * @param args the arguments of this call.
   */
  @Override
  public Pair<Expr, Expr> call(int si, Expr tvalue, List<Expr> args)
  {
    return access(si, tvalue, args);
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
    return new Pair<>(value, code);
  }


  /**
   * Create code to access (call or write) a feature.
   *
   * @param si site of the access expression, must be ExprKind.Assign or ExprKind.Call
   *
   * @param tvalue the target of this call, CExpr.UNIT if none.
   *
   * @param args the arguments of this call, or, in case of an assignment, a
   * list of one element containing value to be assigned.
   *
   * @return pair of expression containing result value and statement to perform
   * the given access
   */
  Pair<Expr, Expr> access(int si, Expr tvalue, List<Expr> args)
  {
    var res = Expr.UNIT;
    var s   = Expr.UNIT;
    var isCall = _fuir.codeAt(si) == FUIR.ExprKind.Call;  // call or assignment?
    var cc0 = _fuir.accessedClazz  (si);
    var ccs = _fuir.accessedClazzes(si);
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
            s = s.andThen(_jvm.reportErrorInCode("no targets for access of " + clazzInQuotes(cc0) + " within `" + _fuir.siteAsString(si) + "`"));
            res = null;
          }
        else  // an assignment to an unused field or unit-type call, that is fine to remove, just add a comment
          {
            s = s.andThen(Expr.comment("access to " + _fuir.codeAtAsString(si) + " eliminated"));
          }
      }
    else if (ccs.length > 2)
      {
        if (CHECKS) check
          (_fuir.hasData(_fuir.accessTargetClazz(si)),  // would be strange if target is unit type
           _fuir.accessIsDynamic(si));                  // or call is not dynamic

        var dynCall = args(true, tvalue, args, cc0, isCall ? _fuir.clazzArgCount(cc0) : 1)
          .andThen(Expr.comment("Dynamic access of " + clazzInQuotes(cc0)))
          .andThen(addDynamicFunctionAndStubs(si, cc0, ccs, isCall));
        if (AbstractInterpreter.clazzHasUnitValue(_fuir, rt))
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
        var tc = _fuir.accessTargetClazz(si);
        var tt = ccs[0];                   // target clazz we match against
        var cc = ccs[1];                   // called clazz in case of match
        if (tc != tt || _types.hasInterfaceFile(tc))
          {
            tvalue = tvalue.andThen(Expr.checkcast(_types.javaType(tt)));
          }
        var calpair = staticAccess(si, tt, cc, tvalue, args, isCall);
        s = s.andThen(calpair.v1());
        res = calpair.v0();
      }
    if (_fuir.clazzIsVoidType(_fuir.clazzResultClazz(cc0)))
      {
        if (res != null)
          {
            s = s.andThen(res);
          }
        res = null;
      }
    return new Pair<>(res, s);
  }


  /**
   * We have a dynamic access to feature cc0 with several targets, so we create an
   * interface with a dynamic function and add implementations to each actual
   * target.
   *
   * @param si site of the access expression, must be ExprKind.Assign or ExprKind.Call
   *
   * @param cc0 a feature whose outer feature is a ref that has several actual instances.
   *
   * @return the invokeinterface expression that performs the call
   */
  private Expr addDynamicFunctionAndStubs(int si, int cc0, int[] ccs, boolean isCall)
  {
    var intfc = _types.interfaceFile(_fuir.clazzOuterClazz(cc0));
    var rc = _fuir.clazzResultClazz(cc0);
    var dn = _names.dynamicFunction(cc0);
    var ds = isCall ? _types.dynDescriptor(cc0) : "(" + _types.javaType(rc).argDescriptor() + ")V";
    var dr = isCall ? _types.resultType(rc)     : PrimitiveType.type_void;
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
        var initLocals = new List<>(VerificationType.UninitializedThis);
        if (isCall)
          {
            initLocals.addAll(_jvm.initialLocals(cc0));
          }
        else
          {
            initLocals = Types.addToLocals(initLocals, _types.javaType(rc));
          }
        addStub(tt, cc, dn, ds, isCall, initLocals);
      }
    return Expr.invokeInterface(intfc._name, dn, ds, dr, _fuir.sitePos(si).line());
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
  private void addStub(int tt, int cc, String dn, String ds, boolean isCall, List<VerificationType> initLocals)
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
        var p = staticAccess(/* *** NOTE ***: The site must be NO_SITE since we are not generating
                              * code for `_fuir.clazzAt(si)`, but for the stub. If we would pass the
                              * site here, the access might otherwise be optimized as a tail call!
                              */
                             FUIR.NO_SITE,
                             tt, cc, tv, na, isCall);
        var code = p.v1()
          .andThen(p.v0() == null ? Expr.UNIT : p.v0())
          .andThen(retoern);
        var ca = cf.codeAttribute(dn + "in class for " + clazzInQuotes(tt),
                                  code, new List<>(), ClassFile.StackMapTable.empty(cf, initLocals, code));
        cf.method(ACC_PUBLIC, dn, ds, new List<>(ca));
      }
  }


  /**
   * Create code for a static access (call or assignment).  This is used by
   * access to create code if there is only one possible target and by stubs to
   * perform the actual access.
   *
   * @param si site of the access or NO_SITE if this is a call in an interface method stub.
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
   * @param si site of the access
   *
   * @return the result and code to perform the access.
   */
  Pair<Expr, Expr> staticAccess(int si, int tt, int cc, Expr tv, List<Expr> args, boolean isCall)
  {
    var cco = _fuir.clazzOuterClazz(cc);   // actual outer clazz of called clazz, more specific than tt
    if (_fuir.clazzIsBoxed(tt) &&
        !_fuir.clazzIsRef(cco)  // NYI: CLEANUP: would be better if the AbstractInterpreter would
                                // not confront us with boxed references here, such that
                                // this special handling could be removed.
        )
      { // in case we access the value in a boxed target, unbox it first:
        tv = Expr.comment("UNBOXING , boxed type " + clazzInQuotes(tt) + " desired type " + clazzInQuotes(cco))
          .andThen(tv.getFieldOrUnit(_names.javaClass(tt),    // note that tv.getfield works vor unit type (resulting in tv.drop()).
                                     Names.BOXED_VALUE_FIELD_NAME,
                                     _types.javaType(cco)));
      }

    return isCall ? staticCall(si, tv, args, cc)
                  : new Pair<>(Expr.UNIT,
                               _jvm.assignField(si, tv, cc, args.get(0), _fuir.clazzResultClazz(cc)));
  }


  /**
   * Create code for a statically bound call.
   *
   * @param si site of the access or NO_SITE if this is a call in an interface method stub.
   *
   * @param tvalue the target value of the call
   *
   * @param args the arguments to the call
   *
   * @param cc clazz that is called
   *
   * @return the code to perform the call
   *
   * @param si site of the call
   */
  Pair<Expr, Expr> staticCall(int si, Expr tvalue, List<Expr> args, int cc)
  {
    Pair<Expr, Expr> res;
    var oc = _fuir.clazzOuterClazz(cc);
    var rt = _fuir.clazzResultClazz(cc);

    switch (_fuir.clazzKind(cc))
      {
      case Abstract :
        res = new Pair<>(null,  // result is void, we do not return from this path.
                         Expr.UNIT);
        Errors.error("Call to abstract feature encountered.",
                     "Found call to " + clazzInQuotes(cc));
        break;
      case Intrinsic:
        {
          if (_fuir.clazzTypeParameterActualType(cc) != -1)  /* type parameter is also of Kind Intrinsic, NYI: CLEANUP: should better have its own kind?  */
            {
              return new Pair<>(Expr.UNIT, tvalue.drop());
            }
          else if (!Intrinsix.inRuntime(_jvm, cc))
            {
              return Intrinsix.inlineCode(_jvm, si, cc, tvalue, args);
            }
          // fall through!
        }
      case Routine  :
      case Native   :
        {
          if (_types.clazzNeedsCode(cc))
            {
              var cl = si == NO_SITE ? FUIR.NO_CLAZZ
                                     : _fuir.clazzAt(si);

              if (cc == cl && // calling myself
                  _jvm._tailCall.callIsTailCall(cl, si))
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
                  code = code
                    .andThen(_fuir.lifeTime(cl).maySurviveCall()
                                ? Expr.goBacktoLabel(_jvm.startLabel(cl, true))
                                : Expr.goBacktoLabel(_jvm.startLabel(cl, false)));

                  res = new Pair<>(null,  // result is void, we do not return from this path.
                                 code);
                }
              else
                {
                  if (_fuir.clazzIsRef(oc))
                    { // the type of tvalue is oc's interface, we need the actual class:
                      tvalue = tvalue.andThen(Expr.checkcast(_types.resultType(oc)));
                    }
                  var call = args(false, tvalue, args, cc, _fuir.clazzArgCount(cc))
                    .andThen(_types.invokeStatic(cc, _fuir.sitePos(si).line()));

                  res = makePair(call, rt);
                }
            }
          else
            {
              res = new Pair<>(Expr.UNIT, Expr.UNIT);
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
  @Override
  public Pair<Expr, Expr> box(int s, Expr val, int vc, int rc)
  {
    var res = val;
    if (!_fuir.clazzIsRef(vc) && _fuir.clazzIsRef(rc))  // NYI: CLEANUP: would be good if the AbstractInterpreter would not call box() in this case
      {
        var n = _names.javaClass(rc);
        res = Expr.comment("box from " + clazzInQuotes(vc) + " to " + clazzInQuotes(rc))
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
  @Override
  public Pair<Expr, Expr> current(int s)
  {
    var cl = _fuir.clazzAt(s);
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
  @Override
  public Pair<Expr, Expr> outer(int s)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzResultClazz(_fuir.clazzOuterRef(_fuir.clazzAt(s))) == _fuir.clazzOuterClazz(_fuir.clazzAt(s)));

    var cl = _fuir.clazzAt(s);
    return new Pair<>(_types.javaType(_fuir.clazzOuterClazz(cl)).load(0),
                      Expr.UNIT);
  }


  /**
   * Get the value argument #i from the slot that contains the argument at the
   * beginning of a call to the Java code of cl.
   *
   * @param s site of the current expression
   *
   * @param i index the local variable we want to get
   *
   * @return code to read arg #i from its slot.
   */
  @Override
  public Expr arg(int s, int i)
  {
    if (PRECONDITIONS) require
      (0 <= i,
       i < _fuir.clazzArgCount(_fuir.clazzAt(s)));

    var cl = _fuir.clazzAt(s);
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

    return val.andThen(jt.store(l));
  }


  /**
   * Get a constant value of type constCl with given byte data d.
   */
  @Override
  public Pair<Expr, Expr> constData(int si, int constCl, byte[] d)
  {
    var c = createConstant(si, constCl, d);
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
              ucl.addToClInit(c.v1());
              ucl.addToClInit(c.v0().andThen(Expr.putstatic(ucl._name, f, jt)));
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
  Pair<Expr, Expr> createConstant(int si, int constCl, byte[] d)
  {
    return switch (_fuir.getSpecialClazz(constCl))
      {
      case c_bool         -> new Pair<>(Expr.iconst(d[0]                                                                             , _types.javaType(constCl)), Expr.UNIT);
      case c_i8           -> new Pair<>(Expr.iconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).get     ()         , _types.javaType(constCl)), Expr.UNIT);
      case c_i16          -> new Pair<>(Expr.iconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getShort()         , _types.javaType(constCl)), Expr.UNIT);
      case c_i32          -> new Pair<>(Expr.iconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getInt  ()         , _types.javaType(constCl)), Expr.UNIT);
      case c_i64          -> new Pair<>(Expr.lconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getLong ())                                   , Expr.UNIT);
      case c_u8           -> new Pair<>(Expr.iconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).get     () &   0xff, _types.javaType(constCl)), Expr.UNIT);
      case c_u16          -> new Pair<>(Expr.iconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff, _types.javaType(constCl)), Expr.UNIT);
      case c_u32          -> new Pair<>(Expr.iconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getInt  ()         , _types.javaType(constCl)), Expr.UNIT);
      case c_u64          -> new Pair<>(Expr.lconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getLong ())                                   , Expr.UNIT);
      case c_f32          -> new Pair<>(Expr.fconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getInt  ())                                   , Expr.UNIT);
      case c_f64          -> new Pair<>(Expr.dconst(ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN).getLong ())                                   , Expr.UNIT);
      case c_Const_String, c_String
                          -> _jvm.constString(Arrays.copyOfRange(d, 4, ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getInt()+4));
      default             ->
        {
          if (_fuir.clazzIsArray(constCl))
            {
              var elementType = this._fuir.inlineArrayElementClazz(constCl);

              var bb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
              var elCount = bb.getInt();
              var jt = this._types.resultType(elementType);
              var aLen = Expr
                .iconst(elCount);

              var result =  aLen
                .andThen(jt.newArray());

              for (int idx = 0; idx < elCount; idx++)
                {
                  var b = _fuir.deseralizeConst(elementType, bb);
                  var c = createConstant(si, elementType, b);
                  result = result
                    .andThen(Expr.DUP)                             // T[], T[]
                    .andThen(Expr.checkcast(jt.array()))
                    .andThen(Expr.iconst(idx))                     // T[], T[], idx
                    .andThen(c.v1())                               // T[], T[], idx, const-data-code
                    .andThen(c.v0())                               // T[], T[], idx, const-data-code
                    .andThen(jt.xastore());                        // T[]
                }
              yield _jvm.const_array(constCl, result, elCount);
            }
          else if (!_fuir.clazzIsChoice(constCl))
            {
              var b = ByteBuffer.wrap(d);
              var result = Expr.UNIT;
              for (int index = 0; index < _fuir.clazzArgCount(constCl); index++)
                {
                  var fr = _fuir.clazzArgClazz(constCl, index);
                  var bytes = _fuir.deseralizeConst(fr, b);
                  var c = createConstant(si, fr, bytes);
                  result = result
                    .andThen(c.v1())
                    .andThen(c.v0());
                }
              result = result
                .andThen(_types.invokeStatic(constCl, _fuir.sitePos(si).line()));

              yield new Pair<>(result, Expr.UNIT);
            }
          else
            {
              Errors.error("Unsupported constant in JVM backend.",
                           "Backend cannot handle constant of clazz " + clazzInQuotes(constCl));
              yield null;
            }
        }
      };
  }


  /**
   * Perform a match on value subv.
   *
   * @param s site of the match
   *
   * @param ai the abstract interpreter instance
   *
   * @param sub code to produce the match subject value
   *
   * @return the code for the match, produces unit type result.
   */
  @Override
  public Pair<Expr, Expr> match(int s, AbstractInterpreter<Expr, Expr> ai, Expr sub)
  {
    var code = _choices.match(_jvm, ai, s, sub);
    return new Pair<>(Expr.UNIT, code);
  }


  /**
   * Create a tagged value of type newcl from an untagged value for type valuecl.
   *
   * @param cl the clazz we are compiling
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
  @Override
  public Pair<Expr, Expr> tag(int s, Expr value, int newcl, int tagNum)
  {
    var res = _choices.tag(_jvm, s, value, newcl, tagNum);
    return new Pair<>(res, Expr.UNIT);
  }


  /**
   * Access the effect of type ecl that is installed in the environment.
   */
  @Override
  public Pair<Expr, Expr> env(int s, int ecl)
  {
    var rt = _types.resultType(ecl);
    var res =
      Expr.iconst(_jvm.effectId(ecl))
      .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                 Names.RUNTIME_EFFECT_GET,
                                 Names.RUNTIME_EFFECT_GET_SIG,
                                 Names.ANY_TYPE))
      .andThen(rt == PrimitiveType.type_void ? Expr.UNIT : Expr.checkcast(rt));
    return new Pair<>(res, Expr.UNIT);
  }


  /**
   * Are jt and resultType(type) a primitive type and do they match?
   */
  private boolean primitiveTypeMatches(JavaType jt, int type)
  {
    return jt instanceof PrimitiveType pt && pt == _types.resultType(type);
  }

  /**
   * Are jt and resultType(type) a class type and do they match?
   */
  private boolean classTypeMatches(JavaType jt, int type)
  {
    return
      jt                      instanceof ClassType et &&
      _types.resultType(type) instanceof ClassType ct &&
      ( et == NULL_TYPE                                                                 ||
        _fuir.clazzIsRef(type) /* we do not check exact reference assignability here */ ||
        et.sameAs(ct)          /* but value or choice types must be the same!        */ );
  }


  /**
   * For debugging output
   *
   * @return "`<clazz c>`".
   */
  private String clazzInQuotes(int c)
  {
    return "`" + _fuir.clazzAsString(c) + "`";
  }


  /**
   * Generate code to terminate the execution immediately.
   *
   * @param msg a message explaining the illegal state
   */
  // NYI: BUG: #3178 reportErrorInCode may currently not be called repeatedly
  //           triggers error: Expecting a stack map frame
  // @Override
  // public Expr reportErrorInCode(String msg)
  // {
  //   return this._jvm.reportErrorInCode(msg);
  // }

}

/* end of file */
