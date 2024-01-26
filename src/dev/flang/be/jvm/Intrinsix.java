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
 * Source of class Intrinsics
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm;

import dev.flang.be.jvm.classfile.ClassFileConstants;
import dev.flang.be.jvm.classfile.Expr;

import dev.flang.be.jvm.runtime.Intrinsics;
import dev.flang.be.jvm.runtime.Runtime;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.Pair;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import java.util.stream.Collectors;


/**
 * Intrinsix provides code for compilation of intrinsics for the JVM backend.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Intrinsix extends ANY implements ClassFileConstants
{

  /*----------------------------  interfaces  ---------------------------*/


  /**
   * Functional interface for inline intrinsic.
   */
  interface IntrinsicCode
  {
    Pair<Expr,Expr> get(JVM jvm, int cl, boolean pr, int cc, Expr tvalue, List<Expr> args);
  }


  /*----------------------------  constants  ----------------------------*/


  /* Set of names of intrinsics defined in runtime.Intrinsics.  No inline code
   * is created for these, but they are called directly:
   */
  static TreeSet<String> _availableIntrinsics = new TreeSet<>();
  static
  {
    for (var m : Intrinsics.class.getDeclaredMethods())
      {
        if (!m.isSynthetic())
          {
            _availableIntrinsics.add(m.getName());
          }
      }
  }


  /**
   * Enclose the given code by monitorenter/monitorexit for
   * Runtime.LOCK_FOR_ATOMIC.
   *
   * @param e the code that needs atomicity
   *
   * @return e surrounded by monitorenter/monitorexit for
   * Runtime.LOCK_FOR_ATOMIC.
   */
  private static Expr locked(Expr e)
  {
    return Expr.getstatic(Names.RUNTIME_CLASS,
                          Names.RUNTIME_LOCK_FOR_ATOMIC,
                          JAVA_LANG_OBJECT)
      .andThen(Expr.MONITORENTER)
      .andThen(e)
      .andThen(Expr.getstatic(Names.RUNTIME_CLASS,
                              Names.RUNTIME_LOCK_FOR_ATOMIC,
                              JAVA_LANG_OBJECT))
      .andThen(Expr.MONITOREXIT);
  }


  /**
   * Set of code generators for intrinsics that produce inline code
   */
  static final TreeMap<String, IntrinsicCode> _compiled_ = new TreeMap<>();
  static
  {
    put("Any.as_string",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var clname = jvm._fuir.clazzAsString(jvm._fuir.clazzOuterClazz(cc));
          return jvm.constString("instance["+clname+"]");
        });

    put("Type.name",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var str = jvm._fuir.clazzTypeName(jvm._fuir.clazzOuterClazz(cc));
          return new Pair<>(tvalue.drop().andThen(jvm.constString(str)), Expr.UNIT);
        });

    put("concur.atomic.racy_accesses_supported",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var v = jvm._fuir.lookupAtomicValue(jvm._fuir.clazzOuterClazz(cc));
          var rc  = jvm._fuir.clazzResultClazz(v);
          var r =
            jvm._fuir.clazzIsRef(rc) ||
            jvm._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i8  ) ||
            jvm._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i16 ) ||
            jvm._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i32 ) ||
            jvm._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i64 ) ||
            jvm._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u8  ) ||
            jvm._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u16 ) ||
            jvm._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u32 ) ||
            jvm._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u64 ) ||
            jvm._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_f32 ) ||
            jvm._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_bool) ||
            jvm._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_unit);
          return new Pair<>(Expr.UNIT, Expr.iconst(r ? 1 : 0));
        });

    put("concur.util.loadFence",
        "concur.util.storeFence",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          return new Pair<>(Expr.UNIT,
                            locked(Expr.UNIT));
        });

    put("concur.atomic.read0",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var ac = jvm._fuir.clazzOuterClazz(cc);
          var v = jvm._fuir.lookupAtomicValue(ac);
          var val = locked(tvalue
                           .andThen(jvm.getfield(v)));
          return new Pair<>(val, Expr.UNIT);
        });

    put("concur.atomic.write0",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var ac = jvm._fuir.clazzOuterClazz(cc);
          var v = jvm._fuir.lookupAtomicValue(ac);
          var code = locked(tvalue
                            .andThen(args.get(0))
                            .andThen(jvm.putfield(v)));
          return new Pair<>(Expr.UNIT, code);
        });

    put("concur.atomic.compare_and_set0",
        "concur.atomic.compare_and_swap0",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var cf = jvm._types.classFile(cl);
          var ac = jvm._fuir.clazzOuterClazz(cc);
          var v = jvm._fuir.lookupAtomicValue(ac);
          var rc  = jvm._fuir.clazzResultClazz(v);
          var tt = tvalue.type();
          var jt = jvm._types.javaType(rc);
          int tslot  = jvm.allocLocal(cl, pre, 1);                  // local var slot for target
          int nvslot = jvm.allocLocal(cl, pre, jt.stackSlots());    // local var slot for arg(1), new value, not casted
          int vslot  = jvm.allocLocal(cl, pre, jt.stackSlots());    // local var slot for old value, not casted.

          Expr pos, neg, oldv;
          if (jvm._fuir.clazzIntrinsicName(cc).equals("concur.atomic.compare_and_set0"))
            { // compare_and_set: return true or false
              pos = Expr.iconst(1);            // 1
              neg = Expr.iconst(0);            // 0
              oldv = Expr.UNIT;
            }
          else
            { // compare_and_swap: return old value
              pos = Expr.UNIT;
              neg = Expr.UNIT;
              oldv = jt.load(vslot, cf);
            }

          Expr val =
            locked(
                   // preparation: store target in tslot, arg1 in nvslot and value field in vslot
                   tvalue                                       // target       -> tslot
                   .andThen(Expr.astore(tslot, tt.vti(cf)))     //
                   .andThen(args.get(1))                        // new value    -> nslot
                   .andThen(jt.store(nvslot, cf))                   //
                   .andThen(tt.load(tslot, cf))                 // target.value -> vslot
                   .andThen(jvm.getfield(v))                    //
                   .andThen(jt.store(vslot, cf))                    //
                   // actual comparison:
                   .andThen(jvm.compareValues(cl,
                                              pre,
                                              args.get(0),
                                              jt.load(vslot, cf),
                                              rc))              // cmp_result
                   // conditional assignment code and result
                   .andThen(Expr.branch(O_ifne,                         // -
                                        tt.load(tslot, cf)              // tv
                                        .andThen(jt.load(nvslot, cf))   // tv nv
                                        .andThen(jvm.putfield(v))       // -
                                        .andThen(pos),                  // - --or-- 1
                                        neg))                           // - --or-- 0
                   .andThen(oldv));                                     // v --or-- 0/1
          return new Pair<>(val, Expr.UNIT);
        });

    put("debug",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          return new Pair<>(Expr.UNIT, Expr.iconst(jvm._options.fuzionDebug() ? 1 : 0));
        });

    put("debug_level",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          return new Pair<>(Expr.UNIT, Expr.iconst(jvm._options.fuzionDebugLevel()));
        });

    put("fuzion.java.Java_Object.is_null",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var jref = jvm._fuir.clazz_fuzionJavaObject_Ref();
          var res = tvalue
            .andThen(jvm.getfield(jref))
            .andThen(Expr.branch(O_ifnull,
                                 Expr.iconst(1),
                                 Expr.iconst(0)))
            ;
          return new Pair<>(res, Expr.UNIT);
        });

    put("fuzion.java.i8_to_java_object",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var rc = jvm._fuir.clazz_fuzionJavaObject();
          var jref = jvm._fuir.clazz_fuzionJavaObject_Ref();
          var res = jvm.new0(rc)
            .andThen(Expr.DUP)
            .andThen(args.get(0))
            .andThen(Expr.invokeStatic("java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", Names.JAVA_LANG_OBJECT, 1))
            .andThen(jvm.putfield(jref))
            .is(jvm._types.javaType(rc));
          return new Pair<>(res, Expr.UNIT);
        });

    put("fuzion.java.i16_to_java_object",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var rc = jvm._fuir.clazz_fuzionJavaObject();
          var jref = jvm._fuir.clazz_fuzionJavaObject_Ref();
          var res = jvm.new0(rc)
            .andThen(Expr.DUP)
            .andThen(args.get(0))
            .andThen(Expr.invokeStatic("java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", Names.JAVA_LANG_OBJECT, 1))
            .andThen(jvm.putfield(jref))
            .is(jvm._types.javaType(rc));
          return new Pair<>(res, Expr.UNIT);
        });

    put("fuzion.java.i32_to_java_object",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var rc = jvm._fuir.clazz_fuzionJavaObject();
          var jref = jvm._fuir.clazz_fuzionJavaObject_Ref();
          var res = jvm.new0(rc)
            .andThen(Expr.DUP)
            .andThen(args.get(0))
            .andThen(Expr.invokeStatic("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", Names.JAVA_LANG_OBJECT, 1))
            .andThen(jvm.putfield(jref))
            .is(jvm._types.javaType(rc));
          return new Pair<>(res, Expr.UNIT);
        });

    put("fuzion.java.i64_to_java_object",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var rc = jvm._fuir.clazz_fuzionJavaObject();
          var jref = jvm._fuir.clazz_fuzionJavaObject_Ref();
          var res = jvm.new0(rc)
            .andThen(Expr.DUP)
            .andThen(args.get(0))
            .andThen(Expr.invokeStatic("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", Names.JAVA_LANG_OBJECT, 1))
            .andThen(jvm.putfield(jref))
            .is(jvm._types.javaType(rc));
          return new Pair<>(res, Expr.UNIT);
        });

    put("fuzion.java.u16_to_java_object",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var rc = jvm._fuir.clazz_fuzionJavaObject();
          var jref = jvm._fuir.clazz_fuzionJavaObject_Ref();
          var res = jvm.new0(rc)
            .andThen(Expr.DUP)
            .andThen(args.get(0))
            .andThen(Expr.invokeStatic("java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", Names.JAVA_LANG_OBJECT, 1))
            .andThen(jvm.putfield(jref))
            .is(jvm._types.javaType(rc));
          return new Pair<>(res, Expr.UNIT);
        });

    put("fuzion.java.f32_to_java_object",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var rc = jvm._fuir.clazz_fuzionJavaObject();
          var jref = jvm._fuir.clazz_fuzionJavaObject_Ref();
          var res = jvm.new0(rc)
            .andThen(Expr.DUP)
            .andThen(args.get(0))
            .andThen(Expr.invokeStatic("java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", Names.JAVA_LANG_OBJECT, 1))
            .andThen(jvm.putfield(jref))
            .is(jvm._types.javaType(rc));
          return new Pair<>(res, Expr.UNIT);
        });

    put("fuzion.java.f64_to_java_object",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var rc = jvm._fuir.clazz_fuzionJavaObject();
          var jref = jvm._fuir.clazz_fuzionJavaObject_Ref();
          var res = jvm.new0(rc)
            .andThen(Expr.DUP)
            .andThen(args.get(0))
            .andThen(Expr.invokeStatic("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", Names.JAVA_LANG_OBJECT, 1))
            .andThen(jvm.putfield(jref))
            .is(jvm._types.javaType(rc));
          return new Pair<>(res, Expr.UNIT);
        });

    put("fuzion.java.bool_to_java_object",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var rc = jvm._fuir.clazz_fuzionJavaObject();
          var jref = jvm._fuir.clazz_fuzionJavaObject_Ref();
          var res = jvm.new0(rc)
            .andThen(Expr.DUP)
            .andThen(args.get(0))
            .andThen(Expr.invokeStatic("java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", Names.JAVA_LANG_OBJECT, 1))
            .andThen(jvm.putfield(jref))
            .is(jvm._types.javaType(rc));
          return new Pair<>(res, Expr.UNIT);
        });

    put("fuzion.sys.args.count",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var val = Expr.getstatic(Names.RUNTIME_CLASS,
                                   Names.RUNTIME_ARGS,
                                   JAVA_LANG_STRING.array())
            .andThen(Expr.ARRAYLENGTH);
          return new Pair<>(val, Expr.UNIT);
        });

    put("fuzion.sys.args.get",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          return jvm.constString(args.get(0)
                                 .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                                            Names.RUNTIME_ARGS_GET,
                                                            Names.RUNTIME_ARGS_GET_SIG,
                                                            PrimitiveType.type_byte.array(),
                                                            1)));
        });

    put("fuzion.sys.internal_array.get",
        "fuzion.sys.internal_array.setel",
        "fuzion.sys.internal_array_init.alloc",

        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var in = jvm._fuir.clazzIntrinsicName(cc);
          var at = jvm._fuir.clazzOuterClazz(cc); // array type
          var et = jvm._fuir.clazzActualGeneric(at, 0); // element type
          var jt = jvm._types.resultType(et);
          var val = Expr.UNIT;
          var code = Expr.UNIT;
          if (in.equals("fuzion.sys.internal_array_init.alloc"))
            {
              val = args.get(0)
                .andThen(jt.newArray());
            }
          else
            {
              if (in.equals("fuzion.sys.internal_array.get"))
                {
                  val = args.get(0)
                    .andThen(Expr.checkcast(jt.array()))
                    .andThen(args.get(1))
                    .andThen(jt.xaload());
                }
              else if (in.equals("fuzion.sys.internal_array.setel"))
                {
                  var check_frozen = Expr.UNIT;
                  if (CHECKS)
                    {
                      check_frozen = Expr.DUP
                        .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                                   "ensure_not_frozen",
                                                   "(" + (JAVA_LANG_OBJECT.descriptor()) + ")V",
                                                   ClassFileConstants.PrimitiveType.type_void,
                                                   1));
                    }
                  code = args.get(0)
                    .andThen(Expr.checkcast(jt.array()))
                    .andThen(check_frozen)
                    .andThen(args.get(1))
                    .andThen(args.get(2))
                    .andThen(jt.xastore());
                }
            }
          return new Pair<>(val, code);
        });

    put("fuzion.sys.internal_array.freeze",
        "fuzion.sys.internal_array.ensure_not_frozen",

        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var in = jvm._fuir.clazzIntrinsicName(cc);
          var at = jvm._fuir.clazzOuterClazz(cc);       // array type
          var et = jvm._fuir.clazzActualGeneric(at, 0); // element type
          var val = Expr.UNIT;
          var code = Expr.UNIT;
          if (CHECKS)
            {
              var data = jvm._fuir.lookup_fuzion_sys_internal_array_data(at);
              code = tvalue
                .andThen(jvm.getfield(data))
                .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                           in.replace("fuzion.sys.internal_array.",""),
                                           "(" + (JAVA_LANG_OBJECT.descriptor()) + ")V",
                                           ClassFileConstants.PrimitiveType.type_void,
                                           1));
            }
          return new Pair<>(val, code);
        });

    put("effect.abort0",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var ecl = jvm._fuir.effectType(cc);
          var code = Expr.iconst(jvm._fuir.clazzId2num(ecl))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "effect_abort",
                                       "(I)V",
                                       ClassFileConstants.PrimitiveType.type_void,
                                       1));
          return new Pair<>(Expr.UNIT, code);
        });

    put("effect.abortable",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var ecl = jvm._fuir.effectType(cc);
          var oc = jvm._fuir.clazzActualGeneric(cc, 0);
          var call = jvm._fuir.lookupCall(oc);
          var call_t = jvm._types.javaType(call);
          if (call_t instanceof ClassType call_ct)
            {
              var result = Expr.iconst(jvm._fuir.clazzId2num(ecl))
                .andThen(tvalue)
                .andThen(args.get(0))
                .andThen(Expr.classconst(call_ct))
                .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                           "effect_abortable",
                                           "(" + ("I" +
                                                  Names.ANY_DESCR +
                                                  Names.ANY_DESCR +
                                                  JAVA_LANG_CLASS.descriptor()) +
                                           ")V",
                                           ClassFileConstants.PrimitiveType.type_void,
                                           4));
              return new Pair<>(Expr.UNIT, result);
            }
          else
            { // unreachable, call type cannot be primitive type
              throw new Error("unexpected type " + call_t + " for " + jvm._fuir.clazzAsString(call));
            }
        });

    put("effect.default",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var ecl = jvm._fuir.effectType(cc);
          var result = Expr.iconst(jvm._fuir.clazzId2num(ecl))
            .andThen(tvalue)
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "effect_default",
                                       "(" + ("I" +
                                              Names.ANY_DESCR) +
                                       ")V",
                                       ClassFileConstants.PrimitiveType.type_void,
                                       2));
          return new Pair<>(Expr.UNIT, result);
        });
    put("effect.replace",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var ecl = jvm._fuir.effectType(cc);
          var result = Expr.iconst(jvm._fuir.clazzId2num(ecl))
            .andThen(tvalue)
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "effect_replace",
                                       "(" + ("I" +
                                              Names.ANY_DESCR) +
                                       ")V",
                                       ClassFileConstants.PrimitiveType.type_void,
                                       2));
          return new Pair<>(Expr.UNIT, result);
        });
    put("effect.type.is_installed",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var ecl = jvm._fuir.clazzActualGeneric(cc, 0);
          var val = Expr.iconst(jvm._fuir.clazzId2num(ecl))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "effect_is_installed",
                                       "(I)Z",
                                       ClassFileConstants.PrimitiveType.type_boolean,
                                       1));
          return new Pair<>(val, Expr.UNIT);
        });

    put("safety",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          return new Pair<>(Expr.UNIT, Expr.iconst(jvm._options.fuzionSafety() ? 1 : 0));
        });

    put("fuzion.std.nano_time", (jvm, cl, pre, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(Expr.invokeStatic(System.class.getName().replace(".", "/"),
            "nanoTime",
            methodDescriptor(System.class, "nanoTime"),
            PrimitiveType.type_long,
            0));
      return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.env_vars.get0", (jvm, cl, pre, cc, tvalue, args) -> {
      return jvm.constString(
        tvalue.drop()
          .andThen(args.get(0))
          .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
            "fuzion_sys_env_vars_get0",
            methodDescriptor(Runtime.class, "fuzion_sys_env_vars_get0"),
            PrimitiveType.type_byte.array(),
            1)));
    });
    put("fuzion.sys.env_vars.set0", (jvm, cl, pre, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(Expr.iconst(0)); // false
      return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.env_vars.unset0", (jvm, cl, pre, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(Expr.iconst(0)); // false
      return new Pair<>(res, Expr.UNIT);
    });

    put("fuzion.sys.thread.spawn0",
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var oc = jvm._fuir.clazzActualGeneric(cc, 0);
          var call = jvm._fuir.lookupCall(oc);
          var call_t = jvm._types.javaType(call);
          if (call_t instanceof ClassType call_ct)
            {
              var result =
                tvalue
                .andThen(args.get(0))
                .andThen(Expr.classconst(call_ct))
                .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                           "thread_spawn",
                                           "(" + (// Names.ANY_DESCR +
                                                  Names.ANY_DESCR +
                                                  JAVA_LANG_CLASS.descriptor()) +
                                           ")J",
                                           ClassFileConstants.PrimitiveType.type_long,
                                           2));
              return new Pair<>(result, Expr.UNIT);
            }
          else
            { // unreachable, call type cannot be primitive type
              throw new Error("unexpected type " + call_t + " for " + jvm._fuir.clazzAsString(call));
            }
        });

    put(new String[]
      { "fuzion.java.array_get",
        "fuzion.java.array_length",
        "fuzion.java.array_to_java_object0",
        "fuzion.java.call_c0",
        "fuzion.java.call_s0",
        "fuzion.java.call_v0",
        "fuzion.java.get_field0",
        "fuzion.java.get_static_field0",
        "fuzion.java.java_string_to_string",
        "fuzion.java.string_to_java_object0"
      },
        (jvm, cl, pre, cc, tvalue, args) ->
        {
          var name = jvm._names.function(cc, false);
          var in = jvm._fuir.clazzIntrinsicName(cc);
          var msg = "NYI: missing implementation of JVM backend intrinsic '" +
            in + "', need '" + Intrinsics.class.getName() + "." + name + "' or inline code in " +
            Intrinsix.class.getName() + ".";
          return new Pair<>(null,
                            jvm.reportErrorInCode(msg));
        });
  }

  // helper to add one element to _compiled_
  private static void put(String n1, IntrinsicCode gen)
  {
    if (CHECKS) check
      (!_compiled_.containsKey(n1));

    _compiled_.put(n1, gen);
  }

  // helper to add one element under two names to _compiled_
  private static void put(String n1,
                          String n2, IntrinsicCode gen) { put(n1, gen);
                                                          put(n2, gen); }

  // helper to add one element under three names to _compiled_
  private static void put(String n1,
                          String n2,
                          String n3, IntrinsicCode gen) { put(n1, gen);
                                                          put(n2, gen);
                                                          put(n3, gen); }

  // helper to add one element under many names to to _compiled_
  private static void put(String[] names,       IntrinsicCode gen)
  {
    for (var n : names)
      {
        put(n, gen);
      }
  }


  /*-------------------------  static methods  --------------------------*/


  /**
   * Get the names of all intrinsics supported by this backend.
   */
  public static Set<String> supportedIntrinsics()
  {
    var result = new TreeSet<String>();
    result.addAll(_compiled_.keySet().stream().map(Intrinsix::backendName).collect(Collectors.toSet()));
    for (var n : _availableIntrinsics)
      {
        if (result.contains(n))
          {
            Errors.fatal("JVM backend intrinsic `" + n + "` exists twice: in " + Intrinsix.class + "._compiled_ " +
                         "and " + Intrinsics.class);
          }
      }
    result.addAll(_availableIntrinsics);
    return result;
  }


  /**
   * Convert a given intrinsic name into the name used by the JVM backend in the
   * result of supportedIntrinsics.
   *
   * @param s _fuir.intrinsicName of a feature
   *
   * @return the mangled name used by the JVM backend
   */
  public static String backendName(String s)
  {
    return Names.mangle(s);
  }


  /**
   * Check if given intrinsic cc is implemented as inline code.  Create inline
   * code if that is the case or code to produce an error if that is not the
   * case and there is no implementation in runtime.Instrinsics either.
   *
   * @param jvm the backend
   *
   * @param cc the intrinsic to be called
   *
   * @param tvalue the target value
   *
   * @param args the actual arguments in the call
   *
   * @return null in case intrinsic is implemented in runtime.Intrinsics and can
   * be called normally, otherwise the code created for the intrinsic or to
   * produce an error message since the intrinsic is missing.
   */
  static Pair<Expr, Expr> inlineCode(JVM jvm, int cl, boolean pre, int cc, Expr tvalue, List<Expr> args)
  {
    Pair<Expr, Expr> result = null;
    var name = jvm._names.function(cc, false);
    if (!_availableIntrinsics.contains(name))
      {
        var in = jvm._fuir.clazzIntrinsicName(cc);
        var g = Intrinsix._compiled_.get(in);
        if (g != null)
          {
            result = g.get(jvm, cl, pre, cc, tvalue, args);
          }
        else
          {
            var msg = "missing implementation of JVM backend intrinsic '"+in+"', need '" + Intrinsics.class + "." + name + "' ";
            Errors.warning(msg);
            result = new Pair<>(null,
                                jvm.reportErrorInCode(msg));
          }
      }
    return result;
  }


  /**
   * Check if given intrinsic cc has an implementation in runtime.Instrinsics.
   *
   * @param jvm the backend
   *
   * @param cc the intrinsic to be called
   *
   * @return true iff cc is implemented in runtime.Intrinsics.
   */
  static boolean inRuntime(JVM jvm, int cc)
  {
    return _availableIntrinsics.contains( jvm._names.function(cc, false));
  }


  /**
   * @param c
   * @return a descriptor for the class, e.g. String => Ljava.lang.String;
   */
  private static String descriptor(Class c)
  {
    if(c==byte.class)
        return "B";
    if(c==char.class)
        return "C";
    if(c==double.class)
        return "D";
    if(c==float.class)
        return "F";
    if(c==int.class)
        return "I";
    if(c==long.class)
        return "J";
    if(c==short.class)
        return "S";
    if(c==boolean.class)
        return "Z";
    if(c==void.class)
        return "V";

    var n = c.getName().replace('.', '/');
    return c.isArray()
      ? n
      : ('L' + n + ';');
  }


  /**
   * get the the method descriptor for a method in class c.
   * e.g. for args: Runtime.class, "fuzion_sys_net_listen"
   *      and `int fuzion_sys_net_listen(){}`
   *      it returns: ()I
   */
  private static String methodDescriptor(Class c, String m)
  {
    return Arrays
      .stream(c.getMethods())
      .filter(x -> x.getName().equals(m))
      .findAny()
      .map(x ->
        {
          return "("
                  + Arrays.stream(x.getParameterTypes()).map(pt -> descriptor(pt)).collect(Collectors.joining())
                + ")"
            + descriptor(x.getReturnType());
        }
      )
      .get();
  }


}

/* end of file */
