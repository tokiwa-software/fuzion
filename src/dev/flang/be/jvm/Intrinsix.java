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


  // NYI: Move to class InlineIntrinsics or similar.
  interface IntrinsicCode
  {
    Pair<Expr,Expr> get(JVM jvm, int cc, Expr tvalue, List<Expr> args);
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
        _availableIntrinsics.add(m.getName());
      }
  }


  /**
   * Set of code generators for intrinsics that produce inline code
   */
  static final TreeMap<String, IntrinsicCode> _compiled_ = new TreeMap<>();
  static
  {
    put("Any.as_string",
        (jvm, cc, tvalue, args) ->
        {
          var clname = jvm._fuir.clazzAsString(jvm._fuir.clazzOuterClazz(cc));
          return jvm.constString("instance["+clname+"]");
        });

    put("Type.name",
        (jvm, cc, tvalue, args) ->
        {
          var str = jvm._fuir.clazzTypeName(jvm._fuir.clazzOuterClazz(cc));
          return new Pair<>(tvalue.drop().andThen(jvm.constString(str)), Expr.UNIT);
        });

    put("concur.atomic.racy_accesses_supported",
        (jvm, cc, tvalue, args) ->
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
        (jvm, cc, tvalue, args) ->
        {
          var val = Expr.getstatic(Names.RUNTIME_CLASS,
                                   "LOCK_FOR_ATOMIC",
                                   JAVA_LANG_OBJECT)
            .andThen(Expr.MONITORENTER)
            .andThen(Expr.getstatic(Names.RUNTIME_CLASS,
                                    "LOCK_FOR_ATOMIC",
                                    JAVA_LANG_OBJECT))
            .andThen(Expr.MONITOREXIT);
          return new Pair<>(Expr.UNIT, val);
        });

    put("debug",
        (jvm, cc, tvalue, args) ->
        {
          return new Pair<>(Expr.UNIT, Expr.iconst(jvm._options.fuzionDebug() ? 1 : 0));
        });

    put("debug_level",
        (jvm, cc, tvalue, args) ->
        {
          return new Pair<>(Expr.UNIT, Expr.iconst(jvm._options.fuzionDebugLevel()));
        });

    put("fuzion.std.date_time",
        (jvm, cc, tvalue, args) ->
        {
          var res =
            args.get(0)
            .andThen(Expr.checkcast(PrimitiveType.type_int.array()))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_std_date_time",
                                       "([I)V",
                                       PrimitiveType.type_void));
            ;
          return new Pair<>(Expr.UNIT, res);
        });

    put("fuzion.sys.args.count",
        (jvm, cc, tvalue, args) ->
        {
          var val = Expr.getstatic(Names.RUNTIME_CLASS,
                                   Names.RUNTIME_ARGS,
                                   JAVA_LANG_STRING.array())
            .andThen(Expr.ARRAYLENGTH);
          return new Pair<>(val, Expr.UNIT);
        });

    put("fuzion.sys.args.get",
        (jvm, cc, tvalue, args) ->
        {
          return jvm.constString(args.get(0)
                                 .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                                            Names.RUNTIME_ARGS_GET,
                                                            Names.RUNTIME_ARGS_GET_SIG,
                                                            PrimitiveType.type_byte.array())));
        });

    put("fuzion.sys.fileio.write", (jvm, cc, tvalue, args) ->
        {
          var res =
            tvalue.drop()
            .andThen(args.get(0))
            .andThen(args.get(1))
            .andThen(Expr.checkcast(PrimitiveType.type_byte.array()))
            .andThen(args.get(2))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_sys_fileio_write",
                                       "(J[BI)I",
                                       PrimitiveType.type_int));
          return new Pair<>(res, Expr.UNIT);
        });

    put("fuzion.sys.internal_array.get",
        "fuzion.sys.internal_array.setel",
        "fuzion.sys.internal_array_init.alloc",

        (jvm, cc, tvalue, args) ->
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
              var arrAndIndex = args.get(0)
                .andThen(Expr.checkcast(jt.array()))
                .andThen(args.get(1));
              if (in.equals("fuzion.sys.internal_array.get"))
                {
                  val = arrAndIndex
                    .andThen(jt.xaload());
                }
              else if (in.equals("fuzion.sys.internal_array.setel"))
                {
                  code = arrAndIndex
                    .andThen(args.get(2))
                    .andThen(jt.xastore());
                }
            }
          return new Pair<>(val, code);
        });

    put("effect.abort",
        (jvm, cc, tvalue, args) ->
        {
          var ecl = jvm._fuir.effectType(cc);
          var code = Expr.iconst(jvm._fuir.clazzId2num(ecl))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "effect_abort",
                                       "(I)V",
                                       ClassFileConstants.PrimitiveType.type_void));
          return new Pair<>(Expr.UNIT, code);
        });

    put("effect.abortable",
        (jvm, cc, tvalue, args) ->
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
                                           ClassFileConstants.PrimitiveType.type_void));
              return new Pair<>(Expr.UNIT, result);
            }
          else
            { // unreachable, call type cannot be primitive type
              throw new Error("unexpected type " + call_t + " for " + jvm._fuir.clazzAsString(call));
            }
        });

    put("effect.default",
        (jvm, cc, tvalue, args) ->
        {
          var ecl = jvm._fuir.effectType(cc);
          var result = Expr.iconst(jvm._fuir.clazzId2num(ecl))
            .andThen(tvalue)
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "effect_default",
                                       "(" + ("I" +
                                              Names.ANY_DESCR) +
                                       ")V",
                                       ClassFileConstants.PrimitiveType.type_void));
          return new Pair<>(Expr.UNIT, result);
        });
    put("effect.replace",
        (jvm, cc, tvalue, args) ->
        {
          var ecl = jvm._fuir.effectType(cc);
          var result = Expr.iconst(jvm._fuir.clazzId2num(ecl))
            .andThen(tvalue)
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "effect_replace",
                                       "(" + ("I" +
                                              Names.ANY_DESCR) +
                                       ")V",
                                       ClassFileConstants.PrimitiveType.type_void));
          return new Pair<>(Expr.UNIT, result);
        });
    put("effect.type.is_installed",
        (jvm, cc, tvalue, args) ->
        {
          var ecl = jvm._fuir.clazzActualGeneric(cc, 0);
          var val = Expr.iconst(jvm._fuir.clazzId2num(ecl))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "effect_is_installed",
                                       "(I)Z",
                                       ClassFileConstants.PrimitiveType.type_boolean));
          return new Pair<>(val, Expr.UNIT);
        });

    put("safety",
        (jvm, cc, tvalue, args) ->
        {
          return new Pair<>(Expr.UNIT, Expr.iconst(jvm._options.fuzionSafety() ? 1 : 0));
        });


    put("fuzion.sys.net.accept",(jvm, cc, tvalue, args) ->
    {
          var res =
            tvalue.drop()
            .andThen(args.get(0))
            .andThen(args.get(1))
            .andThen(Expr.checkcast(PrimitiveType.type_long.array()))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_sys_net_accept",
                                       methodDescriptor(Runtime.class, "fuzion_sys_net_accept"),
                                       PrimitiveType.type_boolean));
          return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.net.bind0",(jvm, cc, tvalue, args) ->
    {
          var res =
            tvalue.drop()
            .andThen(args.get(0))
            .andThen(args.get(1))
            .andThen(args.get(2))
            .andThen(args.get(3))
            .andThen(Expr.checkcast(PrimitiveType.type_byte.array()))
            .andThen(args.get(4))
            .andThen(Expr.checkcast(PrimitiveType.type_byte.array()))
            .andThen(args.get(5))
            .andThen(Expr.checkcast(PrimitiveType.type_long.array()))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_sys_net_bind0",
                                       methodDescriptor(Runtime.class, "fuzion_sys_net_bind0"),
                                       PrimitiveType.type_int));
          return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.net.close0",(jvm, cc, tvalue, args) ->
    {
          var res =
            tvalue.drop()
            .andThen(args.get(0))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_sys_net_close0",
                                        methodDescriptor(Runtime.class, "fuzion_sys_net_close0"),
                                       PrimitiveType.type_int));
          return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.net.connect0",(jvm, cc, tvalue, args) ->
    {
          var res =
            tvalue.drop()
            .andThen(args.get(0))
            .andThen(args.get(1))
            .andThen(args.get(2))
            .andThen(args.get(3))
            .andThen(Expr.checkcast(PrimitiveType.type_byte.array()))
            .andThen(args.get(4))
            .andThen(Expr.checkcast(PrimitiveType.type_byte.array()))
            .andThen(args.get(5))
            .andThen(Expr.checkcast(PrimitiveType.type_long.array()))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_sys_net_connect0",
                                       methodDescriptor(Runtime.class, "fuzion_sys_net_connect0"),
                                       PrimitiveType.type_int));
          return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.net.get_peer_address",(jvm, cc, tvalue, args) ->
    {
          var res =
            tvalue.drop()
            .andThen(args.get(0))
            .andThen(args.get(1))
            .andThen(Expr.checkcast(PrimitiveType.type_byte.array()))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_sys_net_get_peer_address",
                                       methodDescriptor(Runtime.class, "fuzion_sys_net_get_peer_address"),
                                       PrimitiveType.type_int));
          return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.net.get_peer_port",(jvm, cc, tvalue, args) ->
    {
          var res =
            tvalue.drop()
            .andThen(args.get(0))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_sys_net_get_peer_port",
                                       methodDescriptor(Runtime.class, "fuzion_sys_net_get_peer_port"),
                                       PrimitiveType.type_short));
          return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.net.listen",(jvm, cc, tvalue, args) ->
    {
          var res =
            tvalue.drop()
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_sys_net_listen",
                                       methodDescriptor(Runtime.class, "fuzion_sys_net_listen"),
                                       PrimitiveType.type_int));
          return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.net.read",(jvm, cc, tvalue, args) ->
    {
          var res =
            tvalue.drop()
            .andThen(args.get(0))
            .andThen(args.get(1))
            .andThen(Expr.checkcast(PrimitiveType.type_byte.array()))
            .andThen(args.get(2))
            .andThen(args.get(3))
            .andThen(Expr.checkcast(PrimitiveType.type_long.array()))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_sys_net_read",
                                       methodDescriptor(Runtime.class, "fuzion_sys_net_read"),
                                       PrimitiveType.type_boolean));
          return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.net.set_blocking0",(jvm, cc, tvalue, args) ->
    {
          var res =
            tvalue.drop()
            .andThen(args.get(0))
            .andThen(args.get(1))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_sys_net_set_blocking0",
                                       methodDescriptor(Runtime.class, "fuzion_sys_net_set_blocking0"),
                                       PrimitiveType.type_int));
          return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.net.write",(jvm, cc, tvalue, args) ->
    {
          var res =
            tvalue.drop()
            .andThen(args.get(0))
            .andThen(args.get(1))
            .andThen(Expr.checkcast(PrimitiveType.type_byte.array()))
            .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                       "fuzion_sys_net_write",
                                       methodDescriptor(Runtime.class, "fuzion_sys_net_write"),
                                       PrimitiveType.type_int));
          return new Pair<>(res, Expr.UNIT);
    });


    put("fuzion.sys.fileio.close", (jvm, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(args.get(0))
          .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
            "fuzion_sys_fileio_close",
            methodDescriptor(Runtime.class, "fuzion_sys_fileio_close"),
            PrimitiveType.type_int));
      return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.fileio.create_dir", (jvm, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(args.get(0))
          .andThen(Expr.checkcast(PrimitiveType.type_byte.array()))
          .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
            "fuzion_sys_fileio_create_dir",
            methodDescriptor(Runtime.class, "fuzion_sys_fileio_create_dir"),
            PrimitiveType.type_boolean));
      return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.fileio.delete", (jvm, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(args.get(0))
          .andThen(Expr.checkcast(PrimitiveType.type_byte.array()))
          .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
            "fuzion_sys_fileio_delete",
            methodDescriptor(Runtime.class, "fuzion_sys_fileio_delete"),
            PrimitiveType.type_boolean));
      return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.fileio.file_position", (jvm, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(args.get(0))
          .andThen(args.get(1))
          .andThen(Expr.checkcast(PrimitiveType.type_long.array()))
          .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
            "fuzion_sys_fileio_file_position",
            methodDescriptor(Runtime.class, "fuzion_sys_fileio_file_position"),
            PrimitiveType.type_void));
      return new Pair<>(Expr.UNIT, res);
    });
    put("fuzion.sys.fileio.flush", (jvm, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(args.get(0))
          .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
            "fuzion_sys_fileio_flush",
            methodDescriptor(Runtime.class, "fuzion_sys_fileio_flush"),
            PrimitiveType.type_int));
      return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.fileio.lstats", (jvm, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(args.get(0))
          .andThen(Expr.checkcast(PrimitiveType.type_byte.array()))
          .andThen(args.get(1))
          .andThen(Expr.checkcast(PrimitiveType.type_long.array()))
          .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
            "fuzion_sys_fileio_lstats",
            methodDescriptor(Runtime.class, "fuzion_sys_fileio_lstats"),
            PrimitiveType.type_boolean));
      return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.fileio.stats", (jvm, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(args.get(0))
          .andThen(Expr.checkcast(PrimitiveType.type_byte.array()))
          .andThen(args.get(1))
          .andThen(Expr.checkcast(PrimitiveType.type_long.array()))
          .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
            "fuzion_sys_fileio_stats",
            methodDescriptor(Runtime.class, "fuzion_sys_fileio_stats"),
            PrimitiveType.type_boolean));
      return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.fileio.mmap", (jvm, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(args.get(0))
          .andThen(args.get(1))
          .andThen(args.get(2))
          .andThen(args.get(3))
          .andThen(Expr.checkcast(PrimitiveType.type_int.array()))
          .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
            "fuzion_sys_fileio_mmap",
            methodDescriptor(Runtime.class, "fuzion_sys_fileio_mmap"),
            PrimitiveType.type_byte.array()));
      return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.fileio.move", (jvm, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(args.get(0))
          .andThen(Expr.checkcast(PrimitiveType.type_byte.array()))
          .andThen(args.get(1))
          .andThen(Expr.checkcast(PrimitiveType.type_byte.array()))
          .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
            "fuzion_sys_fileio_move",
            methodDescriptor(Runtime.class, "fuzion_sys_fileio_move"),
            PrimitiveType.type_boolean));
      return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.fileio.munmap", (jvm, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
            "fuzion_sys_fileio_munmap",
            methodDescriptor(Runtime.class, "fuzion_sys_fileio_munmap"),
            PrimitiveType.type_int));
      return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.fileio.open", (jvm, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(args.get(0))
          .andThen(Expr.checkcast(PrimitiveType.type_byte.array()))
          .andThen(args.get(1))
          .andThen(Expr.checkcast(PrimitiveType.type_long.array()))
          .andThen(args.get(2))
          .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
            "fuzion_sys_fileio_open",
            methodDescriptor(Runtime.class, "fuzion_sys_fileio_open"),
            PrimitiveType.type_void));
      return new Pair<>(Expr.UNIT, res);
    });
    put("fuzion.sys.fileio.read", (jvm, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(args.get(0))
          .andThen(args.get(1))
          .andThen(Expr.checkcast(PrimitiveType.type_byte.array()))
          .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
            "fuzion_sys_fileio_read",
            methodDescriptor(Runtime.class, "fuzion_sys_fileio_read"),
            PrimitiveType.type_int));
      return new Pair<>(res, Expr.UNIT);
    });
    put("fuzion.sys.fileio.seek", (jvm, cc, tvalue, args) -> {
      var res =
        tvalue.drop()
          .andThen(args.get(0))
          .andThen(args.get(1))
          .andThen(args.get(2))
          .andThen(Expr.checkcast(PrimitiveType.type_long.array()))
          .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
            "fuzion_sys_fileio_seek",
            methodDescriptor(Runtime.class, "fuzion_sys_fileio_seek"),
            PrimitiveType.type_void));
      return new Pair<>(Expr.UNIT, res);
    });

    put(new String[]
      { "concur.atomic.compare_and_set0",
        "concur.atomic.compare_and_swap0",
        "concur.atomic.read0",
        "concur.atomic.write0",
        "fuzion.java.Java_Object.is_null",
        "fuzion.java.array_get",
        "fuzion.java.array_length",
        "fuzion.java.array_to_java_object0",
        "fuzion.java.bool_to_java_object",
        "fuzion.java.call_c0",
        "fuzion.java.call_s0",
        "fuzion.java.call_v0",
        "fuzion.java.f32_to_java_object",
        "fuzion.java.f64_to_java_object",
        "fuzion.java.get_field0",
        "fuzion.java.get_static_field0",
        "fuzion.java.i16_to_java_object",
        "fuzion.java.i32_to_java_object",
        "fuzion.java.i64_to_java_object",
        "fuzion.java.i8_to_java_object",
        "fuzion.java.java_string_to_string",
        "fuzion.java.string_to_java_object0",
        "fuzion.java.u16_to_java_object",
        "fuzion.std.nano_sleep",
        "fuzion.std.nano_time",
        "fuzion.sys.env_vars.get0",
        "fuzion.sys.env_vars.has0",
        "fuzion.sys.env_vars.set0",
        "fuzion.sys.env_vars.unset0",
        "fuzion.sys.thread.join0",
        "fuzion.sys.thread.spawn0"
      },
        (jvm, cc, tvalue, args) ->
        {
          var name = jvm._names.function(cc, false);
          var in = jvm._fuir.clazzIntrinsicName(cc);
          var msg = "NYI: missing implementation of JVM backend intrinsic '" +
            in + "', need '" + Intrinsics.class + "." + name + "' or inline code in " +
            Intrinsics.class + ".";
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
  static Pair<Expr, Expr> inlineCode(JVM jvm, int cc, Expr tvalue, List<Expr> args)
  {
    Pair<Expr, Expr> result = null;
    var name = jvm._names.function(cc, false);
    if (!_availableIntrinsics.contains(name))
      {
        var in = jvm._fuir.clazzIntrinsicName(cc);
        var g = Intrinsix._compiled_.get(in);
        if (g != null)
          {
            result = g.get(jvm, cc, tvalue, args);
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
