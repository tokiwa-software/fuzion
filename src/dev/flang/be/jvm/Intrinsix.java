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
 * Source of class Intrinsix
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import dev.flang.be.jvm.classfile.ClassFileConstants;
import dev.flang.be.jvm.classfile.Expr;
import dev.flang.be.jvm.classfile.Label;

import dev.flang.be.jvm.runtime.Intrinsics;
import dev.flang.be.jvm.runtime.Runtime;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.Pair;


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
    Pair<Expr,Expr> get(JVM jvm, int si, int cc, Expr tvalue, List<Expr> args);
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
    put("Type.name",
        (jvm, si, cc, tvalue, args) ->
        {
          var str = jvm._fuir.clazzTypeName(jvm._fuir.clazzOuterClazz(cc));
          return new Pair<>(tvalue.drop().andThen(jvm.constString(str)), Expr.UNIT);
        });

    put("concur.atomic.racy_accesses_supported",
        (jvm, si, cc, tvalue, args) ->
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
          return new Pair<>(Expr.iconst(r ? 1 : 0), Expr.UNIT);
        });

    put("concur.util.loadFence",
        "concur.util.storeFence",
        (jvm, si, cc, tvalue, args) ->
        {
          return new Pair<>(Expr.UNIT,
                            locked(Expr.UNIT));
        });

    put("concur.atomic.read0",
        (jvm, si, cc, tvalue, args) ->
        {
          var ac = jvm._fuir.clazzOuterClazz(cc);
          var v = jvm._fuir.lookupAtomicValue(ac);
          var val = locked(tvalue
                           .andThen(jvm.getfield(v)));
          return new Pair<>(val, Expr.UNIT);
        });

    put("concur.atomic.write0",
        (jvm, si, cc, tvalue, args) ->
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
        (jvm, si, cc, tvalue, args) ->
        {
          var ac = jvm._fuir.clazzOuterClazz(cc);
          var v = jvm._fuir.lookupAtomicValue(ac);
          var rc  = jvm._fuir.clazzResultClazz(v);
          var tt = tvalue.type();
          var jt = jvm._types.resultType(rc);
          int tslot  = jvm.allocLocal(si, 1);                  // local var slot for target
          int nvslot = jvm.allocLocal(si, jt.stackSlots());    // local var slot for arg(1), new value, not casted
          int vslot  = jvm.allocLocal(si, jt.stackSlots());    // local var slot for old value, not casted.

          Expr pos, neg, oldv;
          if (jvm._fuir.clazzOriginalName(cc).equals("concur.atomic.compare_and_set0"))
            { // compare_and_set: return true or false
              pos = Expr.iconst(1);            // 1
              neg = Expr.iconst(0);            // 0
              oldv = Expr.UNIT;
            }
          else
            { // compare_and_swap: return old value
              pos = Expr.UNIT;
              neg = Expr.UNIT;
              oldv = jt.load(vslot);
            }

          Expr val =
            locked(
                   // preparation: store target in tslot, arg1 in nvslot and value field in vslot
                   tvalue                                   // target       -> tslot
                   .andThen(Expr.astore(tslot, tt.vti()))   //
                   .andThen(args.get(1))                    // new value    -> nslot
                   .andThen(jt.store(nvslot))               //
                   .andThen(tt.load(tslot))                 // target.value -> vslot
                   .andThen(jvm.getfield(v))                //
                   .andThen(jt.store(vslot))                //
                   // actual comparison:
                   .andThen(jvm.compareValues(si,
                                              args.get(0),
                                              jt.load(vslot),
                                              rc))              // cmp_result
                   // conditional assignment code and result
                   .andThen(Expr.branch(O_ifne,                         // -
                                        tt.load(tslot)                  // tv
                                        .andThen(jt.load(nvslot))       // tv nv
                                        .andThen(jvm.putfield(v))       // -
                                        .andThen(pos),                  // - --or-- 1
                                        neg))                           // - --or-- 0
                   .andThen(oldv));                                     // v --or-- 0/1
          return new Pair<>(val, Expr.UNIT);
        });

    put("debug",
        (jvm, si, cc, tvalue, args) ->
        {
          return new Pair<>(Expr.iconst(jvm._options.fuzionDebug() ? 1 : 0), Expr.UNIT);
        });

    put("debug_level",
        (jvm, si, cc, tvalue, args) ->
        {
          return new Pair<>(Expr.iconst(jvm._options.fuzionDebugLevel()), Expr.UNIT);
        });

    put("fuzion.java.Java_Object.is_null0",
        (jvm, si, cc, tvalue, args) ->
        {
          var res = args.get(0)
            .andThen(Expr.checkcast(JAVA_LANG_OBJECT))
            .andThen(Expr.branch(O_ifnull,
                                 Expr.iconst(1),
                                 Expr.iconst(0)));
          return new Pair<>(res, Expr.UNIT);
        });

    // arrays of fuzion-type, java reference type and java primitive type id
    String[][] primitive_to_java_object =  {
      {"i8"  , "Byte"     , "B" },
      {"i16" , "Short"    , "S" },
      {"i32" , "Integer"  , "I" },
      {"i64" , "Long"     , "J" },
      {"u16" , "Character", "C" },
      {"f32" , "Float"    , "F" },
      {"f64" , "Double"   , "D" },
      {"bool", "Boolean"  , "Z" },
    };

    for (var a : primitive_to_java_object)
      {
        var fz_type = a[0];
        var java_Type = a[1];
        var java_type = a[2];

        put("fuzion.java." + fz_type + "_to_java_object",
            (jvm, si, cc, tvalue, args) ->
            {
              var rc = jvm._fuir.clazz_fuzionJavaObject();
              var jref = jvm._fuir.lookupJavaRef(rc);
              var res = jvm.new0(rc)
                .andThen(Expr.DUP)
                .andThen(args.get(0))
                .andThen(Expr.invokeStatic("java/lang/" + java_Type, "valueOf", "(" + java_type + ")Ljava/lang/" + java_Type + ";", Names.JAVA_LANG_OBJECT))
                .andThen(jvm.putfield(jref))
                .is(jvm._types.resultType(rc));
              return new Pair<>(res, Expr.UNIT);
            });
      }

    put("fuzion.java.create_jvm",
        (jvm, si, cc, tvalue, args) -> new Pair<>(Expr.UNIT, Expr.UNIT));

    put("fuzion.java.string_to_java_object0",
        (jvm, si, cc, tvalue, args) ->
        {
          var rc = jvm._fuir.clazz_fuzionJavaObject();
          var jref = jvm._fuir.lookupJavaRef(rc);
          var data = jvm._fuir.lookup_fuzion_sys_internal_array_data(jvm._fuir.clazzArgClazz(cc,0));
          var res = jvm.new0(rc)
            .andThen(Expr.DUP)
            .andThen(args.get(0))
            .andThen(jvm.getfield(data))
            .andThen(Expr.checkcast(PrimitiveType.type_byte.array()))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS, "fuzion_java_string_to_java_object0", "([B)Ljava/lang/String;", Names.JAVA_LANG_OBJECT))
            .andThen(jvm.putfield(jref))
            .is(jvm._types.resultType(rc));
          return new Pair<>(res, Expr.UNIT);
        });

    put("fuzion.java.java_string_to_string",
        (jvm, si, cc, tvalue, args) ->
        {
          return jvm.constString(args.get(0)
                                 .andThen(Expr.checkcast(JAVA_LANG_STRING))
                                 .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS, "fuzion_java_string_to_bytes_array", "(Ljava/lang/String;)[B", PrimitiveType.type_byte.array())));
        });

    put("fuzion.java.array_to_java_object0",
        (jvm, si, cc, tvalue, args) ->
        {
          var rc = jvm._fuir.clazzResultClazz(cc);
          var jref = jvm._fuir.lookupJavaRef(rc);
          var et = jvm._types.javaType(jvm._fuir.clazzActualGeneric(cc, 0)); // possibly resultType
          var data = jvm._fuir.lookup_fuzion_sys_internal_array_data(jvm._fuir.clazzArgClazz(cc,0));
          var res = jvm.new0(rc)
            .andThen(Expr.DUP)
            .andThen(args.get(0))
            .andThen(jvm.getfield(data))
            .andThen(Expr.checkcast(et.array()))
            .andThen(jvm.putfield(jref))
            .is(jvm._types.resultType(rc));
          return new Pair<>(res, Expr.UNIT);
        });

    put("fuzion.java.array_length",
        (jvm, si, cc, tvalue, args) ->
        {
          var jref = jvm._fuir.lookupJavaRef(jvm._fuir.clazzArgClazz(cc,0));
          var et = jvm._types.javaType(jvm._fuir.clazzActualGeneric(cc, 0)); // possibly resultType
          var res = args.get(0)
            .andThen(jvm.getfield(jref))
            .andThen(Expr.checkcast(et.isPrimitive() ? et.array() : ClassFileConstants.JAVA_LANG_OBJECT.array()))
            .andThen(Expr.ARRAYLENGTH);
          return new Pair<>(res, Expr.UNIT);
        });

    put("fuzion.java.array_get",
        (jvm, si, cc, tvalue, args) ->
        {
          var jref = jvm._fuir.lookupJavaRef(jvm._fuir.clazzArgClazz(cc,0));
          var et = jvm._types.javaType(jvm._fuir.clazzActualGeneric(cc, 0)); // possibly resultType
          var res = args.get(0)
            .andThen(jvm.getfield(jref))
            .andThen(Expr.checkcast(et.isPrimitive() ? et.array() : ClassFileConstants.JAVA_LANG_OBJECT.array()))
            .andThen(args.get(1))
            .andThen((et.isPrimitive() ? et : ClassFileConstants.JAVA_LANG_OBJECT).xaload());
          if (!et.isPrimitive())
            {
              res = res
                .andThen(jvm.new0(jvm._fuir.clazzActualGeneric(cc, 0)))                               // result, rc0
                .andThen(Expr.DUP_X1)                                                                 // rc0, result, rc0
                .andThen(Expr.SWAP)                                                                   // rc0, rc0, result
                .andThen(jvm.putfield(jvm._fuir.lookupJavaRef(jvm._fuir.clazzActualGeneric(cc, 0)))); // rc0
            }
          return new Pair<>(res, Expr.UNIT);
        });

    put("fuzion.java.get_static_field0",
        (jvm, si, cc, tvalue, args) ->
        {
          var rc = jvm._fuir.clazzResultClazz(cc);
          var jt = jvm._types.javaType(jvm._fuir.clazz_fuzionJavaObject());
          var sref0 = jvm._fuir.lookupJavaRef(jvm._fuir.clazzArgClazz(cc, 0));
          var sref1 = jvm._fuir.lookupJavaRef(jvm._fuir.clazzArgClazz(cc, 1));
          var res = args.get(0)
            .andThen(Expr.checkcast(jt))
            .andThen(jvm.getfield(sref0)) // class name as String
            .andThen(Expr.checkcast(JAVA_LANG_STRING))
            .andThen(args.get(1))
            .andThen(Expr.checkcast(jt))
            .andThen(jvm.getfield(sref1)) // class name as String, field name as String
            .andThen(Expr.checkcast(JAVA_LANG_STRING))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                        "fuzion_java_get_static_field0",
                                        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",
                                        Names.JAVA_LANG_OBJECT));
          return new Pair<>(res.andThen(returnNewJavaObject(jvm, rc)), Expr.UNIT);
        });

    put("fuzion.java.set_static_field0",
        (jvm, si, cc, tvalue, args) ->
        {
          var jt = jvm._types.javaType(jvm._fuir.clazz_fuzionJavaObject());
          var sref0 = jvm._fuir.lookupJavaRef(jvm._fuir.clazzArgClazz(cc, 0));
          var sref1 = jvm._fuir.lookupJavaRef(jvm._fuir.clazzArgClazz(cc, 1));
          var res = args.get(0)
            .andThen(Expr.checkcast(jt))
            .andThen(jvm.getfield(sref0)) // class name as String
            .andThen(Expr.checkcast(JAVA_LANG_STRING))
            .andThen(args.get(1))
            .andThen(Expr.checkcast(jt))
            .andThen(jvm.getfield(sref1)) // class name as String, field name as String
            .andThen(Expr.checkcast(JAVA_LANG_STRING))
            .andThen(args.get(2))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                        "fuzion_java_set_static_field0",
                                        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
                                        ClassFileConstants.PrimitiveType.type_void));
          return new Pair<>(Expr.UNIT, res);
        });

    put("fuzion.java.get_field0",
        (jvm, si, cc, tvalue, args) ->
        {
          var rc = jvm._fuir.clazzResultClazz(cc);
          var rt = jvm._types.javaType(rc);
          var jO = jvm._fuir.clazz_fuzionJavaObject_Ref();
          var javaRefFieldName = jvm._names.field(jO);
          var res = args
            .get(0)
            .andThen(Expr.stringconst(javaRefFieldName))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_java_get_field0",
                                       "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                                       Names.JAVA_LANG_OBJECT))
            .andThen(args.get(1))
            .andThen(Expr.stringconst(javaRefFieldName))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_java_get_field0",
                                       "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                                       Names.JAVA_LANG_OBJECT))
            .andThen(Expr.checkcast(JAVA_LANG_STRING))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_java_get_field0",
                                       "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                                       Names.JAVA_LANG_OBJECT))
            .is(rt);

          return new Pair<>(res.andThen(returnNewJavaObject(jvm, rc)), Expr.UNIT);
        });

    put("fuzion.java.set_field0",
        (jvm, si, cc, tvalue, args) ->
        {
          var jO = jvm._fuir.clazz_fuzionJavaObject_Ref();
          var javaRefFieldName = jvm._names.field(jO);
          var res = args
            .get(0)
            .andThen(Expr.stringconst(javaRefFieldName))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_java_get_field0",
                                       "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                                       Names.JAVA_LANG_OBJECT))
            .andThen(args.get(1))
            .andThen(Expr.stringconst(javaRefFieldName))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_java_get_field0",
                                       "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                                       Names.JAVA_LANG_OBJECT))
            .andThen(Expr.checkcast(JAVA_LANG_STRING))
            .andThen(args.get(2))
            .andThen(Expr.stringconst(javaRefFieldName))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_java_get_field0",
                                       "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                                       Names.JAVA_LANG_OBJECT))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_java_set_field0",
                                       "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V",
                                       ClassFileConstants.PrimitiveType.type_void));

          return new Pair<>(Expr.UNIT, res);
        });
  }


  /**
   * Helper to convert String arguments passed to call_* into Java Strings.
   *
   * NYI: CLEANUP: This might profit from some cleanup: These arguments are
   * currently always instances of fuzion.java.Java_Object created by
   * string_to_java_object, and this fact is used here to access the
   * javaRef-field using static binding.
   *
   * It would be easier if string_to_java_object would just return the
   * java.lang.String as a fuzion.sys.Pointer, then we could avoid the first
   * checkcast and the getfield here.
   */
  static Expr call_jlStringArg(JVM jvm, int cc, List<Expr> args, int argi)
  {
    var at = jvm._fuir.clazzArgClazz(cc, argi);
    var jt = jvm._types.javaType(at);
    var sref = jvm._fuir.lookupJavaRef(at);
    return args.get(argi)
      .andThen(Expr.checkcast(jt)) /* ugly: String is passed as fuzion.Java_Object, not fuzion.Java_String or Java.java.lang._jString */
      .andThen(jvm.getfield(sref)) // class_name
      .andThen(Expr.checkcast(JAVA_LANG_STRING));
  }


  /*
   * creates byte code to convert to
   * object on top of the stack to a fuzion object.
   */
  static Expr returnNewJavaObject(JVM jvm, int rc)
  {
    return switch (jvm._fuir.getSpecialClazz(rc))
      {
      case c_unit -> Expr.ACONST_NULL;
      case c_i8 -> {
        yield Expr.checkcast(new ClassType("java/lang/Byte"))
          .andThen(Expr.invokeVirtual("java/lang/Byte", "byteValue", "()B", PrimitiveType.type_byte));
      }
      case c_u16 -> {
        yield Expr.checkcast(new ClassType("java/lang/Character"))
          .andThen(Expr.invokeVirtual("java/lang/Character", "charValue", "()C", PrimitiveType.type_char));
      }
      case c_i16 -> {
        yield Expr.checkcast(new ClassType("java/lang/Short"))
          .andThen(Expr.invokeVirtual("java/lang/Short", "shortValue", "()S", PrimitiveType.type_short));
      }
      case c_i32 -> {
        yield Expr.checkcast(new ClassType("java/lang/Integer"))
          .andThen(Expr.invokeVirtual("java/lang/Integer", "intValue", "()I", PrimitiveType.type_int));
      }
      case c_i64 -> {
        yield Expr.checkcast(new ClassType("java/lang/Long"))
          .andThen(Expr.invokeVirtual("java/lang/Long", "longValue", "()L", PrimitiveType.type_long));
      }
      case c_f32 -> {
        yield Expr.checkcast(new ClassType("java/lang/Float"))
          .andThen(Expr.invokeVirtual("java/lang/Float", "floatValue", "()F", PrimitiveType.type_float));
      }
      case c_f64 -> {
        yield Expr.checkcast(new ClassType("java/lang/Double"))
          .andThen(Expr.invokeVirtual("java/lang/Double", "doubleValue", "()D", PrimitiveType.type_double));
      }
      case c_bool -> {
        yield Expr.checkcast(new ClassType("java/lang/Boolean"))
          .andThen(Expr.invokeVirtual("java/lang/Boolean", "booleanValue", "()Z", PrimitiveType.type_boolean));
      }
      case c_sys_ptr -> {
        check(false);
        yield Expr.UNIT;
      }
      default -> {
        var rt = jvm._types.javaType(rc);
        var jref = jvm._fuir.lookupJavaRef(rc);

        yield jvm.new0(rc)                                            // result, rc0
          .andThen(Expr.DUP_X1)                                       // rc0, result, rc0
          .andThen(Expr.SWAP)                                         // rc0, rc0, result
          .andThen(jvm.putfield(jref))                                // rc0
          .is(rt);
      }
      };
  }


  /**
   * Return result evaluated by `exec`.
   *
   * @param jvm the jvm instance
   * @param rc  outcome ...
   * @param exec the code we are executing
   * @return
   */
  static Pair<Expr, Expr> returnResult(JVM jvm, int si, int rc, Expr exec)
  {
    if (PRECONDITIONS) require
      (jvm._fuir.clazzBaseName(rc).startsWith("outcome"));

    var rc0 = jvm._fuir.clazzChoice(rc, 0);
    // for storing the result of exec
    int slot  = jvm.allocLocal(si, JAVA_LANG_OBJECT.stackSlots());      // local var slot.
    var cl_err = jvm._fuir.clazz_error();

    var pos = switch (jvm._fuir.getSpecialClazz(rc0))
      {
      case c_unit -> Expr.ACONST_NULL;
      case c_i8 -> {
        yield Expr.aload(slot, JAVA_LANG_OBJECT)
          .andThen(
            Expr.checkcast(new ClassType("java/lang/Byte"))
              .andThen(Expr.invokeVirtual("java/lang/Byte", "byteValue", "()B", PrimitiveType.type_byte)));
      }
      case c_u16 -> {
        yield Expr.aload(slot, JAVA_LANG_OBJECT)
          .andThen(
            Expr.checkcast(new ClassType("java/lang/Character"))
              .andThen(Expr.invokeVirtual("java/lang/Character", "charValue", "()C", PrimitiveType.type_char)));
      }
      case c_i16 -> {
        yield Expr.aload(slot, JAVA_LANG_OBJECT)
          .andThen(
            Expr.checkcast(new ClassType("java/lang/Short"))
              .andThen(Expr.invokeVirtual("java/lang/Short", "shortValue", "()S", PrimitiveType.type_short)));
      }
      case c_i32 -> {
        yield Expr.aload(slot, JAVA_LANG_OBJECT)
          .andThen(
            Expr.checkcast(new ClassType("java/lang/Integer"))
              .andThen(Expr.invokeVirtual("java/lang/Integer", "intValue", "()I", PrimitiveType.type_int)));
      }
      case c_i64 -> {
        yield Expr.aload(slot, JAVA_LANG_OBJECT)
          .andThen(
            Expr.checkcast(new ClassType("java/lang/Long"))
              .andThen(Expr.invokeVirtual("java/lang/Long", "longValue", "()L", PrimitiveType.type_long)));
      }
      case c_f32 -> {
        yield Expr.aload(slot, JAVA_LANG_OBJECT)
          .andThen(
            Expr.checkcast(new ClassType("java/lang/Float"))
              .andThen(Expr.invokeVirtual("java/lang/Float", "floatValue", "()F", PrimitiveType.type_float)));
      }
      case c_f64 -> {
        yield Expr.aload(slot, JAVA_LANG_OBJECT)
          .andThen(
            Expr.checkcast(new ClassType("java/lang/Double"))
              .andThen(Expr.invokeVirtual("java/lang/Double", "doubleValue", "()D", PrimitiveType.type_double)));
      }
      case c_bool -> {
        yield Expr.aload(slot, JAVA_LANG_OBJECT)
          .andThen(
            Expr.checkcast(new ClassType("java/lang/Boolean"))
              .andThen(Expr.invokeVirtual("java/lang/Boolean", "booleanValue", "()Z", PrimitiveType.type_boolean)));
      }
      case c_sys_ptr -> {
        yield Expr.aload(slot, JAVA_LANG_OBJECT);
      }
      default -> {
        var rt = jvm._types.javaType(rc0);
        var jref = jvm._fuir.lookupJavaRef(rc0);

        yield Expr.aload(slot, JAVA_LANG_OBJECT)
          .andThen(jvm.new0(rc0))                                            // result, rc0
          .andThen(Expr.DUP_X1)                                              // rc0, result, rc0
          .andThen(Expr.SWAP)                                                // rc0, rc0, result
          .andThen(jvm.putfield(jref))                                       // rc0
          .is(rt);
      }};

    var neg = jvm.new0(cl_err)                                                               // error
      .andThen(Expr.DUP)                                                                     // error, error
      .andThen(jvm.constString(
        Expr.invokeStatic(Names.RUNTIME_CLASS, "getException", "()Ljava/lang/String;", JAVA_LANG_STRING)
          .andThen(Expr.getstatic("java/nio/charset/StandardCharsets", "UTF_8", new ClassType("java/nio/charset/Charset")))
          .andThen(Expr.invokeVirtual("java/lang/String", "getBytes", "(Ljava/nio/charset/Charset;)[B", ClassFileConstants.PrimitiveType.type_byte.array()))
      ))                                                                                     // error, error, string
      .andThen(jvm.putfield(jvm._fuir.clazzArg(jvm._fuir.clazz_error(), 0)))                 // error
      .andThen(Expr.checkcast(jvm._types.javaType(cl_err)));

    var res = exec
      .andThen(Expr.DUP)                                                               // result, result
      .andThen(Expr.astore(slot, JAVA_LANG_OBJECT.vti()))                              // result
      .andThen(Expr.instanceOf(ERROR_TYPE))                                            // boolean
      .andThen(Expr.branch(O_ifeq, jvm._types._choices.tag(jvm, si, pos, rc, 0),
                                   jvm._types._choices.tag(jvm, si, neg, rc, 1)));     // tagged_result

    return new Pair<>(res, Expr.UNIT);
  }


  static
  {
    put("fuzion.java.call_v0",
        (jvm, si, cc, tvalue, args) ->
        {
          var rc = jvm._fuir.clazzResultClazz(cc);
          var data = jvm._fuir.clazzArg(jvm._fuir.clazzArgClazz(cc, 4), 0);
          var exec = call_jlStringArg(jvm, cc, args, 0)
            .andThen(call_jlStringArg(jvm, cc, args, 1))
            .andThen(call_jlStringArg(jvm, cc, args, 2))
            .andThen(args.get(3))
            .andThen(args.get(4))
            .andThen(jvm.getfield(data)) // args
            .andThen(Expr.checkcast(JAVA_LANG_OBJECT.array()))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                    "fuzion_java_call_v0",
                                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                                    Names.JAVA_LANG_OBJECT));
          return returnResult(jvm, si, rc, exec);
        });

    put("fuzion.java.call_s0",
        (jvm, si, cc, tvalue, args) ->
        {
          var rc = jvm._fuir.clazzResultClazz(cc);
          var data = jvm._fuir.clazzArg(jvm._fuir.clazzArgClazz(cc, 3), 0);
          var exec = call_jlStringArg(jvm, cc, args, 0)
            .andThen(call_jlStringArg(jvm, cc, args, 1))
            .andThen(call_jlStringArg(jvm, cc, args, 2))
            .andThen(args.get(3))
            .andThen(jvm.getfield(data)) // args
            .andThen(Expr.checkcast(JAVA_LANG_OBJECT.array()))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_java_call_s0",
                                       "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
                                       Names.JAVA_LANG_OBJECT));
          return returnResult(jvm, si, rc, exec);
        });

    put("fuzion.java.call_c0",
        (jvm, si, cc, tvalue, args) ->
        {
          var rc = jvm._fuir.clazzResultClazz(cc);
          var data = jvm._fuir.lookup_fuzion_sys_internal_array_data(jvm._fuir.clazzArgClazz(cc, 2));
          var exec = call_jlStringArg(jvm, cc, args, 0)
            .andThen(call_jlStringArg(jvm, cc, args, 1))
            .andThen(args.get(2))
            .andThen(jvm.getfield(data)) // args
            .andThen(Expr.checkcast(JAVA_LANG_OBJECT.array()))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_java_call_c0",
                                       "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
                                       Names.JAVA_LANG_OBJECT));
          return returnResult(jvm, si, rc, exec);
        });

    put("fuzion.java.cast0",
        (jvm, si, cc, tvalue, args) ->
        {
          var rc = jvm._fuir.clazzResultClazz(cc);
          return returnResult(jvm, si, rc, args.get(0));
        });

    put("fuzion.sys.args.count",
        (jvm, si, cc, tvalue, args) ->
        {
          var val = Expr.getstatic(Names.RUNTIME_CLASS,
                                   Names.RUNTIME_ARGS,
                                   JAVA_LANG_STRING.array())
            .andThen(Expr.ARRAYLENGTH)
            .andThen(Expr.iconst(1))
            .andThen(Expr.IADD);
          return new Pair<>(val, Expr.UNIT);
        });

    put("fuzion.sys.args.get",
        (jvm, si, cc, tvalue, args) ->
        {
          return jvm.constString(args.get(0)
                                 .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                                            Names.RUNTIME_ARGS_GET,
                                                            Names.RUNTIME_ARGS_GET_SIG,
                                                            PrimitiveType.type_byte.array())));
        });

    put("fuzion.sys.internal_array.get",
        "fuzion.sys.internal_array.setel",
        "fuzion.sys.internal_array_init.alloc",

        (jvm, si, cc, tvalue, args) ->
        {
          var in = jvm._fuir.clazzOriginalName(cc);
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
                                                   ClassFileConstants.PrimitiveType.type_void));
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

        (jvm, si, cc, tvalue, args) ->
        {
          var in = jvm._fuir.clazzOriginalName(cc);
          var at = jvm._fuir.clazzOuterClazz(cc);       // array type
          var val = Expr.UNIT;
          var code = Expr.UNIT;
          if (CHECKS)
            {
              var data = jvm._fuir.lookup_fuzion_sys_internal_array_data(at);
              if (jvm.fieldExists(data))
                {
                  code = tvalue
                    .andThen(jvm.getfield(data))
                    .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                               in.replace("fuzion.sys.internal_array.",""),
                                               "(" + (JAVA_LANG_OBJECT.descriptor()) + ")V",
                                               ClassFileConstants.PrimitiveType.type_void));
                }
            }
          return new Pair<>(val, code);
        });

    put("effect.type.abort0",
        (jvm, si, cc, tvalue, args) ->
        {
          var ecl = jvm._fuir.effectTypeFromInstrinsic(cc);
          var eid = jvm.effectId(ecl);
          var code = Expr.iconst(eid)
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "effect_abort",
                                       "(I)V",
                                       ClassFileConstants.PrimitiveType.type_void));
          return new Pair<>(Expr.UNIT, code);
        });

    put("effect.type.instate0",
        (jvm, si, cc, tvalue, args) ->
        {
          var ecl = jvm._fuir.effectTypeFromInstrinsic(cc);
          var eid = jvm.effectId(ecl);
          var call     = jvm._fuir.lookupCall(jvm._fuir.clazzActualGeneric(cc, 0));
          var call_def = jvm._fuir.lookupCall(jvm._fuir.clazzActualGeneric(cc, 1));
          var finallie = jvm._fuir.lookup_static_finally(ecl);
          var ejt = jvm._types.resultType(ecl);
          var unit_effect = ejt == ClassFileConstants.PrimitiveType.type_void;
          var try_end   = new Label();
          var try_catch = new Label();
          var try_after = new Label();
          var try_start = Expr.tryCatch(try_end,
                                        try_catch,
                                        Names.ABORT_TYPE);

          // code-snippet to call effect_pop and leave the effect instance on the Java stack
          var pop_effect = Expr.iconst(eid)
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "effect_pop",
                                       "(I)"+Names.ANYI_DESCR,
                                       Names.ANYI_TYPE)
                     )
            .andThen(ejt == ClassFileConstants.PrimitiveType.type_void ? Expr.POP   // cast AnyI to void by dumping the value
                                                                       : Expr.checkcast(ejt));

          var call_finally      = jvm._types.invokeStatic(finallie, jvm._fuir.sitePos(si).line());
          var pop_and_finally   = pop_effect.andThen(call_finally);     // pop effect and call finally
          var pop_fin_and_throw = pop_and_finally.andThen(Expr.THROW);  // pop effect, call finally and throw abort exception from stack

          var result = Expr.iconst(eid)
            .andThen(unit_effect ? args.get(0).drop()
                                    .andThen(Expr.getstatic(Names.RUNTIME_CLASS,
                                                            "_UNIT_TYPE_EFFECT_",
                                                            Names.ANYI_TYPE))
                                 : args.get(0))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "effect_push",
                                       "(" + ("I" +
                                              Names.ANYI_DESCR) +
                                       ")V",
                                       ClassFileConstants.PrimitiveType.type_void)
                     )
            .andThen(try_start)
            .andThen(args.get(1))
            .andThen(jvm._types.invokeStatic(call, jvm._fuir.sitePos(si).line()))
            .andThen(try_end)
            .andThen(pop_and_finally)
            .andThen(Expr.gotoLabel(try_after))
            .andThen(try_catch)
            .andThen(!jvm._fuir.clazzNeedsCode(call_def)
                     ? pop_fin_and_throw // in case call_def was detected by DFA to not be called, we can pass on the abort directly
                     : Expr.DUP  // duplicate abort exception
                       .andThen(Expr.getfield(Names.ABORT_CLASS, Names.ABORT_EFFECT, PrimitiveType.type_int))
                       .andThen(Expr.iconst(eid))
                       .andThen(Expr.branch(O_if_icmpne,
                                            // not for us, so pop effect and re-throw
                                            pop_fin_and_throw,
                                            // for us, so run `finally` and `call_def`
                                            Expr.POP // drop abort exception
                                            .andThen(args.get(2))
                                            .andThen(pop_effect)
                                            .andThen(unit_effect ? Expr.UNIT : Expr.DUP)
                                            .andThen(call_finally)
                                            .andThen(jvm._types.invokeStatic(call_def, jvm._fuir.sitePos(si).line())))
                                )
                     )
            .andThen(try_after);
          return new Pair<>(Expr.UNIT, result);
        });

    put("effect.type.default0",
        (jvm, si, cc, tvalue, args) ->
        {
          var ecl = jvm._fuir.effectTypeFromInstrinsic(cc);
          var eid = jvm.effectId(ecl);
          var arg = args.get(0);
          if (jvm._types.resultType(ecl) == ClassFileConstants.PrimitiveType.type_void)
            {
              arg = arg.drop().andThen(Expr.ACONST_NULL);
            }
          var result = Expr.iconst(eid)
            .andThen(arg)
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "effect_default",
                                       "(" + ("I" +
                                              Names.ANYI_DESCR) +
                                       ")V",
                                       ClassFileConstants.PrimitiveType.type_void));
          return new Pair<>(Expr.UNIT, result);
        });

   put("effect.type.replace0",
        (jvm, si, cc, tvalue, args) ->
        {
          var ecl = jvm._fuir.effectTypeFromInstrinsic(cc);
          var eid = jvm.effectId(ecl);
          var arg = args.get(0);
          if (jvm._types.resultType(ecl) == ClassFileConstants.PrimitiveType.type_void)
            {
              arg = arg.drop().andThen(Expr.ACONST_NULL);
            }
          var result = Expr.iconst(eid)
            .andThen(arg)
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "effect_replace",
                                       "(" + ("I" +
                                              Names.ANY_DESCR) +
                                       ")V",
                                       ClassFileConstants.PrimitiveType.type_void));
          return new Pair<>(Expr.UNIT, result);
        });

    put("effect.type.is_instated0",
        (jvm, si, cc, tvalue, args) ->
        {
          var ecl = jvm._fuir.effectTypeFromInstrinsic(cc);
          var eid = jvm.effectId(ecl);
          var val = Expr.iconst(eid)
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "effect_is_instated",
                                       "(I)Z",
                                       ClassFileConstants.PrimitiveType.type_boolean));
          return new Pair<>(val, Expr.UNIT);
        });

    put("safety",
        (jvm, si, cc, tvalue, args) ->
        {
          return new Pair<>(Expr.iconst(jvm._options.fuzionSafety() ? 1 : 0), Expr.UNIT);
        });

    put("fuzion.sys.fileio.read_dir", (jvm, si, cc, tvalue, args) -> {
      return jvm.constString(
        args.get(0)
          .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                     "fuzion_sys_fileio_read_dir",
                                     methodDescriptor(Runtime.class, "fuzion_sys_fileio_read_dir"),
                                     PrimitiveType.type_byte.array())));
    });

    put("fuzion.std.nano_time", (jvm, si, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(Expr.invokeStatic(System.class.getName().replace(".", "/"),
            "nanoTime",
            methodDescriptor(System.class, "nanoTime"),
            PrimitiveType.type_long));
      return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.env_vars.get0", (jvm, si, cc, tvalue, args) -> {
      return jvm.constString(
        tvalue.drop()
          .andThen(args.get(0))
          .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
            "fuzion_sys_env_vars_get0",
            methodDescriptor(Runtime.class, "fuzion_sys_env_vars_get0"),
            PrimitiveType.type_byte.array())));
    });
    put("fuzion.sys.env_vars.set0", (jvm, si, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(Expr.iconst(0)); // false
      return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.env_vars.unset0", (jvm, si, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(Expr.iconst(0)); // false
      return new Pair<>(res, Expr.UNIT);
    });

    put("fuzion.sys.thread.spawn0",
        (jvm, si, cc, tvalue, args) ->
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
                                           ClassFileConstants.PrimitiveType.type_long));
              return new Pair<>(result, Expr.UNIT);
            }
          else
            { // unreachable, call type cannot be primitive type
              throw new Error("unexpected type " + call_t + " for " + jvm._fuir.clazzAsString(call));
            }
        });

    /* ReentrantLock */
    put("concur.sync.mtx_init",  (jvm, si, cc, tvalue, args) ->
      returnResult(
        jvm,
        si,
        jvm._fuir.clazzResultClazz(cc),
        Expr.invokeStatic(Names.RUNTIME_CLASS,
                          "mtx_init",
                          "()Ljava/lang/Object;",
                          JAVA_LANG_OBJECT)));
    put("concur.sync.mtx_lock",  (jvm, si, cc, tvalue, args) ->
      new Pair<>(args.get(0).andThen(
        Expr.invokeStatic(Names.RUNTIME_CLASS,
                          "mtx_lock",
                          "(Ljava/lang/Object;)Z",
                          ClassFileConstants.PrimitiveType.type_boolean)), Expr.UNIT)
      );
    put("concur.sync.mtx_trylock",  (jvm, si, cc, tvalue, args) ->
      new Pair<>(args.get(0).andThen(
        Expr.invokeStatic(Names.RUNTIME_CLASS,
                          "mtx_trylock",
                          "(Ljava/lang/Object;)Z",
                          ClassFileConstants.PrimitiveType.type_boolean)), Expr.UNIT)
      );
    put("concur.sync.mtx_unlock",  (jvm, si, cc, tvalue, args) ->
      new Pair<>(args.get(0).andThen(
        Expr.invokeStatic(Names.RUNTIME_CLASS,
                          "mtx_unlock",
                          "(Ljava/lang/Object;)Z",
                          ClassFileConstants.PrimitiveType.type_boolean)), Expr.UNIT)
      );
    put("concur.sync.mtx_destroy",  (jvm, si, cc, tvalue, args) ->
      new Pair<>(args.get(0).andThen(
        Expr.invokeStatic(Names.RUNTIME_CLASS,
                          "mtx_destroy",
                          "(Ljava/lang/Object;)V",
                          ClassFileConstants.PrimitiveType.type_void)), Expr.UNIT)
      );

    /* Condition */
    put("concur.sync.cnd_init",  (jvm, si, cc, tvalue, args) ->
      returnResult(jvm, si, jvm._fuir.clazzResultClazz(cc),
          args.get(0).andThen(Expr.invokeStatic(
                          Names.RUNTIME_CLASS,
                          "cnd_init",
                          "(Ljava/lang/Object;)Ljava/lang/Object;",
                          JAVA_LANG_OBJECT))));
    put("concur.sync.cnd_signal",  (jvm, si, cc, tvalue, args) ->
      new Pair<>(args.get(0).andThen(
        Expr.invokeStatic(Names.RUNTIME_CLASS,
                          "cnd_signal",
                          "(Ljava/lang/Object;)Z",
                          ClassFileConstants.PrimitiveType.type_boolean)), Expr.UNIT)
      );
    put("concur.sync.cnd_broadcast",  (jvm, si, cc, tvalue, args) ->
      new Pair<>(args.get(0).andThen(
        Expr.invokeStatic(Names.RUNTIME_CLASS,
                          "cnd_broadcast",
                          "(Ljava/lang/Object;)Z",
                          ClassFileConstants.PrimitiveType.type_boolean)), Expr.UNIT)
      );
    put("concur.sync.cnd_wait",  (jvm, si, cc, tvalue, args) ->
      new Pair<>(args.get(0).andThen(
        Expr.invokeStatic(Names.RUNTIME_CLASS,
                          "cnd_wait",
                          "(Ljava/lang/Object;)Z",
                          ClassFileConstants.PrimitiveType.type_boolean)), Expr.UNIT)
      );
    put("concur.sync.cnd_destroy",  (jvm, si, cc, tvalue, args) ->
      new Pair<>(args.get(0).andThen(
        Expr.invokeStatic(Names.RUNTIME_CLASS,
                          "cnd_destroy",
                          "(Ljava/lang/Object;)V",
                          ClassFileConstants.PrimitiveType.type_void)), Expr.UNIT)
      );

    put(new String[]
      {
        // the names of intrinsics that are not implemented in the JVM backend should go here
      },
        (jvm, si, cc, tvalue, args) ->
        {
          var name = jvm._names.function(cc);
          var in = jvm._fuir.clazzOriginalName(cc);
          var msg = "missing implementation of JVM backend intrinsic '" +
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
   * case and there is no implementation in runtime.Intrinsics either.
   *
   * @param jvm the backend
   *
   * @param si site of the call
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
  static Pair<Expr, Expr> inlineCode(JVM jvm, int si, int cc, Expr tvalue, List<Expr> args)
  {
    Pair<Expr, Expr> result = null;
    var name = jvm._names.function(cc);
    if (!_availableIntrinsics.contains(name))
      {
        var in = jvm._fuir.clazzOriginalName(cc);
        var g = Intrinsix._compiled_.get(in);
        if (g != null)
          {
            result = g.get(jvm, si, cc, tvalue, args);
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
   * Check if given intrinsic cc has an implementation in runtime.Intrinsics.
   *
   * @param jvm the backend
   *
   * @param cc the intrinsic to be called
   *
   * @return true iff cc is implemented in runtime.Intrinsics.
   */
  static boolean inRuntime(JVM jvm, int cc)
  {
    return _availableIntrinsics.contains( jvm._names.function(cc));
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
