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

package dev.flang.be.interpreter;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import dev.flang.fuir.SpecialClazzes;

import static dev.flang.ir.IR.NO_SITE;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;


/**
 * Intrinsics provides the implementation of Fuzion's intrinsic features.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Intrinsics extends ANY
{

  /*----------------------------  interfaces  ---------------------------*/


  @FunctionalInterface
  interface IntrinsicCode
  {
    Callable get(Executor executor, int innerClazz);
  }


  /*------------------------------  enums  ------------------------------*/


  /**
   * Contains possible error numbers emitted by intrinsics when an error happens
   * on the system side. This attempts to match C's errno.h names and numbers.
   */
  enum SystemErrNo
  {
    UNSPECIFIED(0), EIO(5), EACCES(13), ENOTSUP(95), EADDRINUSE(98), ECONNREFUSED(111);

    final int errno;

    private SystemErrNo(final int errno)
    {
      this.errno = errno;
    }
  }


  /*----------------------------  constants  ----------------------------*/


  /**
   * NYI: This will eventually be part of a Fuzion IR / BE Config class.
   */
  public static Boolean ENABLE_UNSAFE_INTRINSICS = null;


  static TreeMap<String, IntrinsicCode> _intrinsics_ = new TreeMap<>();

  /*----------------------------  variables  ----------------------------*/


  /*------------------------  static variables  -------------------------*/


  /*-------------------------  static methods  --------------------------*/


  private static void put(String n, IntrinsicCode c) { _intrinsics_.put(n, c); }
  private static void putUnsafe(String n, IntrinsicCode c) { _intrinsics_.put(n, (executor, innerClazz) -> args -> {
    if (!ENABLE_UNSAFE_INTRINSICS)
      {
        Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
      }
    return c.get(executor, innerClazz).call(args);
  }); }
  private static void put(String n1, String n2, IntrinsicCode c) { put(n1, c); put(n2, c); }
  private static void putUnsafe(String n1, String n2, IntrinsicCode c) { putUnsafe(n1, c); putUnsafe(n2, c); }
  private static void put(String n1, String n2, String n3, IntrinsicCode c) { put(n1, c); put(n2, c); put(n3, c); }
  private static void putUnsafe(String n1, String n2, String n3, IntrinsicCode c) { putUnsafe(n1, c); putUnsafe(n2, c); putUnsafe(n3, c); }
  private static void put(String n1, String n2, String n3, String n4, IntrinsicCode c) { put(n1, c); put(n2, c); put(n3, c); put(n4, c); }
  private static void putUnsafe(String n1, String n2, String n3, String n4, IntrinsicCode c) { putUnsafe(n1, c); putUnsafe(n2, c); putUnsafe(n3, c); putUnsafe(n4, c); }
  private static void put(String n1, String n2, String n3, String n4, String n5, IntrinsicCode c) { put(n1, c); put(n2, c); put(n3, c); put(n4, c); put(n5, c); }


  /**
   * Get the names of all intrinsics supported by this backend.
   */
  public static Set<String> supportedIntrinsics()
  {
    return _intrinsics_.keySet();
  }


  /**
   * Create a Java string from 0-terminated given byte array.
   */
  private static String utf8ByteArrayDataToString(Value internalArray)
  {
    var strA = internalArray.arrayData();
    var ba = (byte[]) strA._array;
    var l = 0;
    while (l < ba.length && ba[l] != 0)
      {
        l++;
      }
    return new String(ba, 0, l, StandardCharsets.UTF_8);
  }

  /**
   * Create a Callable to call an intrinsic feature.
   *
   * @param innerClazz the frame clazz of the called feature
   *
   * @return a Callable instance to execute the intrinsic call.
   */
  public static Callable call(Executor executor, int site, int innerClazz)
  {
    Callable result;
    String in = executor.fuir().clazzOriginalName(innerClazz);
    // NYI: We must check the argument count in addition to the name!
    var ca = _intrinsics_.get(in);
    if (ca != null)
      {
        result = ca.get(executor, innerClazz);
      }
    else
      {
        Errors.fatal(executor.fuir().sitePos(site),
                     "Intrinsic feature not supported",
                     "Missing intrinsic feature: " + in);
        result = (args) -> Value.NO_VALUE;
      }
    return result;
  }


  /**
   * Atomic intrinsics are made atomic using this lock.
   *
   * NYI: OPTIMIZATION: For atomic instances of types ref, i32, etc., we might
   * implement this using jdk.internal.misc.Unsafe or
   * java.util.concurrent.atomic.* to make these operations lock-free.
   */
  static final Object LOCK_FOR_ATOMIC = new Object();


  static
  {
    put("Type.name"            , (executor, innerClazz) -> args ->
      Interpreter.boxedConstString(executor.fuir().clazzTypeName(executor.fuir().clazzOuterClazz(innerClazz))));

    put("concur.atomic.compare_and_swap0",  (executor, innerClazz) -> args ->
        {
          var a = executor.fuir().clazzOuterClazz(innerClazz);
          var f = executor.fuir().lookupAtomicValue(a);
          var thiz      = args.get(0);
          var expected  = args.get(1);
          var new_value = args.get(2);
          synchronized (LOCK_FOR_ATOMIC)
            {
              var res = Interpreter.getField(f, a, thiz, false); // NYI: HACK: We must clone this!
              if (Interpreter.compareField(f, a, thiz, expected))
                {
                  res = expected;   // NYI: HACK: workaround since res was not cloned
                  Interpreter.setField(f, a, thiz, new_value);
                }
              return res;
            }
        });
    put("concur.atomic.compare_and_set0",  (executor, innerClazz) -> args ->
        {
          var a = executor.fuir().clazzOuterClazz(innerClazz);
          var f = executor.fuir().lookupAtomicValue(a);
          var thiz      = args.get(0);
          var expected  = args.get(1);
          var new_value = args.get(2);
          synchronized (LOCK_FOR_ATOMIC)
            {
              if (Interpreter.compareField(f, a, thiz, expected))
                {
                  Interpreter.setField(f, a, thiz, new_value);
                  return new boolValue(true);
                }
              return new boolValue(false);
            }
        });
    put("concur.atomic.racy_accesses_supported",  (executor, innerClazz) -> args ->
        {
          var t = executor.fuir().clazzActualGeneric(executor.fuir().clazzOuterClazz(innerClazz), 0);
          return new boolValue
            (executor.fuir().clazzIsRef(t)                            ||
             (t == executor.fuir().clazz(SpecialClazzes.c_i8  )) ||
             (t == executor.fuir().clazz(SpecialClazzes.c_i16 )) ||
             (t == executor.fuir().clazz(SpecialClazzes.c_i32 )) ||
             (t == executor.fuir().clazz(SpecialClazzes.c_u8  )) ||
             (t == executor.fuir().clazz(SpecialClazzes.c_u16 )) ||
             (t == executor.fuir().clazz(SpecialClazzes.c_u32 )) ||
             (t == executor.fuir().clazz(SpecialClazzes.c_f32 )) ||
             (t == executor.fuir().clazz(SpecialClazzes.c_bool)));
        });
    put("concur.atomic.read0",  (executor, innerClazz) -> args ->
        {
          var a = executor.fuir().clazzOuterClazz(innerClazz);
          var f = executor.fuir().lookupAtomicValue(a);
          var thiz = args.get(0);
          synchronized (LOCK_FOR_ATOMIC)
            {
              return Interpreter.getField(f, a, thiz, false);
            }
        });
    put("concur.atomic.write0", (executor, innerClazz) -> args ->
        {
          var a = executor.fuir().clazzOuterClazz(innerClazz);
          var f = executor.fuir().lookupAtomicValue(a);
          var thiz = args.get(0);
          synchronized (LOCK_FOR_ATOMIC)
            {
              Interpreter.setField(f, a, thiz, args.get(1));
            }
          return new Instance(executor.fuir().clazz(SpecialClazzes.c_unit));
        });

    put("concur.util.load_fence",   (executor, innerClazz) -> args ->
        {
          synchronized (LOCK_FOR_ATOMIC) { };
          return new Instance(executor.fuir().clazz(SpecialClazzes.c_unit));
        });

    put("concur.util.store_fence",  (executor, innerClazz) -> args ->
        {
          synchronized (LOCK_FOR_ATOMIC) { };
          return new Instance(executor.fuir().clazz(SpecialClazzes.c_unit));
        });

    put("fuzion.sys.args.count", (executor, innerClazz) -> args -> new i32Value(executor.options().getBackendArgs().size() + 1));
    put("fuzion.sys.args.get"  , (executor, innerClazz) -> args ->
        {
          var i = args.get(1).i32Value();
          var fuir = executor.fuir();
          if (i == 0)
            {
              return  Interpreter.boxedConstString(fuir.clazzAsString(fuir.mainClazz()));
            }
          else
            {
              return  Interpreter.boxedConstString(executor.options().getBackendArgs().get(i - 1));
            }
        });

    put("fuzion.sys.fatal_fault0", (executor, innerClazz) -> args ->
        {
          Errors.runTime(utf8ByteArrayDataToString(args.get(1)),
                         utf8ByteArrayDataToString(args.get(2)),
                         Executor.callStack(executor.fuir()));
          return Value.EMPTY_VALUE;
        });

    put("fuzion.std.exit", (executor, innerClazz) -> args ->
        {
          int rc = args.get(1).i32Value();
          System.exit(rc);
          return Value.EMPTY_VALUE;
        });
    put("fuzion.jvm.is_null0", (executor, innerClazz) -> args ->
        {
          Object thiz = ((JavaRef)args.get(1))._javaRef;
          return new boolValue(thiz == null);
        });
    putUnsafe("fuzion.jvm.get_static_field0",
        "fuzion.jvm.get_field0"      , (executor, innerClazz) ->
        {
          String in = executor.fuir().clazzOriginalName(innerClazz);
          var statique = in.equals("fuzion.jvm.get_static_field0");
          int resultClazz = executor.fuir().clazzActualGeneric(innerClazz, 0);
          return args ->
            {
              String clazz = !statique ? null : (String) ((JavaRef) args.get(1))._javaRef;
              Object thiz  = statique  ? null :          ((JavaRef) args.get(1))._javaRef;
              String field = (String) (((JavaRef) args.get(2))._javaRef);
              return JavaInterface.getField(clazz, thiz, field, resultClazz);
            };
        });
    putUnsafe("fuzion.jvm.set_static_field0",
        "fuzion.jvm.set_field0"      , (executor, innerClazz) ->
        {
          String in = executor.fuir().clazzOriginalName(innerClazz);
          var statique = in.equals("fuzion.jvm.set_static_field0");
          return args ->
            {
              String clazz = !statique ? null : (String) ((JavaRef) args.get(1))._javaRef;
              Object thiz  = statique  ? null :          ((JavaRef) args.get(1))._javaRef;
              String field = (String) ((JavaRef) args.get(2))._javaRef;
              Object val  = ((JavaRef) args.get(3))._javaRef;
              JavaInterface.setField(clazz, thiz, field, val);
              return Value.EMPTY_VALUE;
            };
        });
    putUnsafe("fuzion.jvm.call_v0",
        "fuzion.jvm.call_s0",
        "fuzion.jvm.call_c0", (executor, innerClazz) ->
        {
          String in = executor.fuir().clazzOriginalName(innerClazz);
          var virtual     = in.equals("fuzion.jvm.call_v0");
          var constructor = in.equals("fuzion.jvm.call_c0");
          var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
          return args ->
            {
              int a = 1;
              var clNameI =                       (JavaRef) args.get(a++);
              var nameI   = constructor ? null :  (JavaRef) args.get(a++);
              var sigI    =                       (JavaRef) args.get(a++);
              var thizR   = !virtual    ? null :  (JavaRef) args.get(a++);

              var argz = args.get(a); // of type fuzion.sys.internal_array<JavaObject>, we need to get field argz.data
              var sac = executor.fuir().clazzArgClazz(innerClazz, executor.fuir().clazzArgCount(innerClazz) - 1);
              var argzData = Interpreter.getField(executor.fuir().lookup_fuzion_sys_internal_array_data(sac), sac, argz, false);

              String clName =                          (String) clNameI._javaRef;
              String name   = nameI   == null ? null : (String) nameI._javaRef;
              String sig    =                          (String) sigI._javaRef;
              Object thiz   = thizR   == null ? null :          thizR._javaRef;
              return JavaInterface.call(clName, name, sig, thiz, argzData, resultClazz);
            };
        });
    putUnsafe("fuzion.jvm.cast0", (executor, innerClazz) -> args ->
        {
          var arg = ((JavaRef) args.get(1))._javaRef;
          var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
          return JavaInterface.javaObjectToInstance(arg, null, resultClazz);
        });
    putUnsafe("fuzion.jvm.array_length",  (executor, innerClazz) -> args ->
        {
          return new i32Value(Array.getLength(((JavaRef) args.get(1))._javaRef));
        });
    putUnsafe("fuzion.jvm.array_get", (executor, innerClazz) -> args ->
        {
          var arr = ((JavaRef) args.get(1))._javaRef;
          var ix  = args.get(2).i32Value();
          var res = Array.get(arr, ix);
          var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
          return JavaInterface.javaObjectToInstance(res, resultClazz);
        });
    putUnsafe("fuzion.jvm.array_to_java_object0", (executor, innerClazz) -> args ->
        {
          var argz = args.get(1);
          var sac = executor.fuir().clazzArgClazz(innerClazz, 0);
          var res = Interpreter
            .getField(executor.fuir().lookup_fuzion_sys_internal_array_data(sac), sac, argz, false)
            .arrayData()
            ._array;
          return new JavaRef(res);
        });
    putUnsafe("fuzion.jvm.create_jvm", (executor, innerClazz) -> args -> new i32Value(0));
    putUnsafe("fuzion.jvm.destroy_jvm", (executor, innerClazz) -> args -> Value.EMPTY_VALUE);
    putUnsafe("fuzion.jvm.string_to_java_object0", (executor, innerClazz) -> args ->
        {
          var argz = args.get(1);
          var sac = executor.fuir().clazzArgClazz(innerClazz, 0);
          var argzData = Interpreter.getField(executor.fuir().lookup_fuzion_sys_internal_array_data(sac), sac, argz, false);
          return new JavaRef(utf8ByteArrayDataToString(argzData));
        });
    putUnsafe("fuzion.jvm.java_string_to_string", (executor, innerClazz) -> args ->
        {
          var javaString = (String) ((JavaRef)args.get(1))._javaRef;
          return Interpreter.boxedConstString(javaString == null ? "--null--" : javaString);
        });
    putUnsafe("fuzion.jvm.primitive_to_java_object", (executor, innerClazz) -> args ->
        {
          var res =  switch (executor.fuir().getSpecialClazz(executor.fuir().clazzActualGeneric(innerClazz, 0)))
          {
            case c_bool -> Boolean  .valueOf(args.get(1).boolValue());
            case c_f32  -> Float    .valueOf(args.get(1).f32Value());
            case c_f64  -> Double   .valueOf(args.get(1).f64Value());
            case c_i16  -> Short    .valueOf((short)args.get(1).i16Value());
            case c_i32  -> Integer  .valueOf(args.get(1).i32Value());
            case c_i64  -> Long     .valueOf(args.get(1).i64Value());
            case c_i8   -> Byte     .valueOf((byte)args.get(1).i8Value());
            case c_u16  -> Character.valueOf((char)args.get(1).u16Value());
            default -> throw new Error("NYI: BUG: primitive_to_java_object not implemented for " + executor.fuir().clazzAsString(executor.fuir().clazzActualGeneric(innerClazz, 0)));
          };
          return new JavaRef(res);
        });
    put("fuzion.sys.type.alloc", (executor, innerClazz) -> args ->
        {
          var et = executor.fuir().clazzActualGeneric(innerClazz, 0); // element type
          return ArrayData.alloc(/* size */ args.get(1).i32Value(),
                                 executor.fuir(),
                                 /* type */ et);
        });
    put("fuzion.sys.type.getel", (executor, innerClazz) -> args ->
        {
          var et = executor.fuir().clazzActualGeneric(innerClazz, 0); // element type
          return ((ArrayData)args.get(1)).get(
                                   /* index */ args.get(2).i32Value(),
                                   executor.fuir(),
                                   /* type  */ et);
        });
    put("fuzion.sys.type.setel", (executor, innerClazz) -> args ->
        {
          var et = executor.fuir().clazzActualGeneric(innerClazz, 0); // element type
          ((ArrayData)args.get(1)).set(
                              /* index */ args.get(2).i32Value(),
                              /* value */ args.get(3),
                              executor.fuir(),
                              /* type  */ et);
          return Value.EMPTY_VALUE;
        });
    put("fuzion.sys.internal_array.freeze", (executor, innerClazz) -> args ->
        {
          return Value.EMPTY_VALUE;
        });
    put("fuzion.sys.internal_array.ensure_not_frozen", (executor, innerClazz) -> args ->
        {
          return Value.EMPTY_VALUE;
        });
    put("fuzion.sys.env_vars.has0", (executor, innerClazz) -> args -> new boolValue(System.getenv(utf8ByteArrayDataToString(args.get(1))) != null));
    put("fuzion.sys.env_vars.get0", (executor, innerClazz) -> args -> Interpreter.boxedConstString(System.getenv(utf8ByteArrayDataToString(args.get(1)))));
    put("fuzion.sys.thread.spawn0", (executor, innerClazz) -> args ->
        {
          var oc   = executor.fuir().clazzArgClazz(innerClazz, 0);
          var cc = executor.fuir().lookupCall(oc);
          var t = new Thread(() -> Errors.runAndExit
                             (() -> executor.callOnNewInstance(NO_SITE, cc, args.get(1), new List<>())));
          t.setDaemon(true);
          t.start();
          // NYI: CLEANUP: should not use javaObjectToInstance
          var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
          return JavaInterface.javaObjectToInstance(t, resultClazz);
        });
    put("fuzion.sys.thread.join0", (executor, innerClazz) -> args ->
        {
          var thread = ((Thread) ((JavaRef) args.get(1))._javaRef);
          var result = false;
          do
            {
              try
                {
                  thread.join();
                  result = true;
                }
              catch (InterruptedException e)
                {

                }
            }
          while (!result);
          return Value.EMPTY_VALUE;
        });

    put("safety"                , (executor, innerClazz) -> args -> new boolValue(executor.options().fuzionSafety()));
    put("debug"                 , (executor, innerClazz) -> args -> new boolValue(executor.options().fuzionDebug()));
    put("debug_level"           , (executor, innerClazz) -> args -> new i32Value (executor.options().fuzionDebugLevel()));
    put("i8.as_i32"             , (executor, innerClazz) -> args -> new i32Value (              (                           args.get(0).i8Value() )));
    put("i8.cast_to_u8"         , (executor, innerClazz) -> args -> new u8Value  (       0xff & (                           args.get(0).i8Value() )));
    put("i8.prefix -°"          , (executor, innerClazz) -> args -> new i8Value  ((int) (byte)  (                       -   args.get(0).i8Value() )));
    put("i8.infix +°"           , (executor, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  +   args.get(1).i8Value() )));
    put("i8.infix -°"           , (executor, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  -   args.get(1).i8Value() )));
    put("i8.infix *°"           , (executor, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  *   args.get(1).i8Value() )));
    put("i8.div"                , (executor, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  /   args.get(1).i8Value() )));
    put("i8.mod"                , (executor, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  %   args.get(1).i8Value() )));
    put("i8.infix &"            , (executor, innerClazz) -> args -> new i8Value  (              (args.get(0).i8Value()  &   args.get(1).i8Value() )));
    put("i8.infix |"            , (executor, innerClazz) -> args -> new i8Value  (              (args.get(0).i8Value()  |   args.get(1).i8Value() )));
    put("i8.infix ^"            , (executor, innerClazz) -> args -> new i8Value  (              (args.get(0).i8Value()  ^   args.get(1).i8Value() )));
    put("i8.infix >>"           , (executor, innerClazz) -> args -> new i8Value  (              (args.get(0).i8Value()  >>  args.get(1).i8Value() )));
    put("i8.infix <<"           , (executor, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  <<  args.get(1).i8Value() )));
    put("i8.type.equality"      , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).i8Value()  ==  args.get(2).i8Value() )));
    put("i8.type.lteq"          , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).i8Value()  <=  args.get(2).i8Value() )));
    put("i16.as_i32"            , (executor, innerClazz) -> args -> new i32Value (              (                           args.get(0).i16Value())));
    put("i16.cast_to_u16"       , (executor, innerClazz) -> args -> new u16Value (     0xffff & (                           args.get(0).i16Value())));
    put("i16.prefix -°"         , (executor, innerClazz) -> args -> new i16Value ((int) (short) (                       -   args.get(0).i16Value())));
    put("i16.infix +°"          , (executor, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() +   args.get(1).i16Value())));
    put("i16.infix -°"          , (executor, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() -   args.get(1).i16Value())));
    put("i16.infix *°"          , (executor, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() *   args.get(1).i16Value())));
    put("i16.div"               , (executor, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() /   args.get(1).i16Value())));
    put("i16.mod"               , (executor, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() %   args.get(1).i16Value())));
    put("i16.infix &"           , (executor, innerClazz) -> args -> new i16Value (              (args.get(0).i16Value() &   args.get(1).i16Value())));
    put("i16.infix |"           , (executor, innerClazz) -> args -> new i16Value (              (args.get(0).i16Value() |   args.get(1).i16Value())));
    put("i16.infix ^"           , (executor, innerClazz) -> args -> new i16Value (              (args.get(0).i16Value() ^   args.get(1).i16Value())));
    put("i16.infix >>"          , (executor, innerClazz) -> args -> new i16Value (              (args.get(0).i16Value() >>  args.get(1).i16Value())));
    put("i16.infix <<"          , (executor, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() <<  args.get(1).i16Value())));
    put("i16.type.equality"     , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).i16Value() ==  args.get(2).i16Value())));
    put("i16.type.lteq"         , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).i16Value() <=  args.get(2).i16Value())));
    put("i32.as_i64"            , (executor, innerClazz) -> args -> new i64Value ((long)        (                           args.get(0).i32Value())));
    put("i32.cast_to_u32"       , (executor, innerClazz) -> args -> new u32Value (              (                           args.get(0).i32Value())));
    put("i32.as_f64"            , (executor, innerClazz) -> args -> new f64Value ((double)      (                           args.get(0).i32Value())));
    put("i32.prefix -°"         , (executor, innerClazz) -> args -> new i32Value (              (                       -   args.get(0).i32Value())));
    put("i32.infix +°"          , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() +   args.get(1).i32Value())));
    put("i32.infix -°"          , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() -   args.get(1).i32Value())));
    put("i32.infix *°"          , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() *   args.get(1).i32Value())));
    put("i32.div"               , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() /   args.get(1).i32Value())));
    put("i32.mod"               , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() %   args.get(1).i32Value())));
    put("i32.infix &"           , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() &   args.get(1).i32Value())));
    put("i32.infix |"           , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() |   args.get(1).i32Value())));
    put("i32.infix ^"           , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() ^   args.get(1).i32Value())));
    put("i32.infix >>"          , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() >>  args.get(1).i32Value())));
    put("i32.infix <<"          , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() <<  args.get(1).i32Value())));
    put("i32.type.equality"     , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).i32Value() ==  args.get(2).i32Value())));
    put("i32.type.lteq"         , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).i32Value() <=  args.get(2).i32Value())));
    put("i64.cast_to_u64"       , (executor, innerClazz) -> args -> new u64Value (              (                           args.get(0).i64Value())));
    put("i64.as_f64"            , (executor, innerClazz) -> args -> new f64Value ((double)      (                           args.get(0).i64Value())));
    put("i64.prefix -°"         , (executor, innerClazz) -> args -> new i64Value (              (                       -   args.get(0).i64Value())));
    put("i64.infix +°"          , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() +   args.get(1).i64Value())));
    put("i64.infix -°"          , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() -   args.get(1).i64Value())));
    put("i64.infix *°"          , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() *   args.get(1).i64Value())));
    put("i64.div"               , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() /   args.get(1).i64Value())));
    put("i64.mod"               , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() %   args.get(1).i64Value())));
    put("i64.infix &"           , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() &   args.get(1).i64Value())));
    put("i64.infix |"           , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() |   args.get(1).i64Value())));
    put("i64.infix ^"           , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() ^   args.get(1).i64Value())));
    put("i64.infix >>"          , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() >>  args.get(1).i64Value())));
    put("i64.infix <<"          , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() <<  args.get(1).i64Value())));
    put("i64.type.equality"     , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).i64Value() ==  args.get(2).i64Value())));
    put("i64.type.lteq"         , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).i64Value() <=  args.get(2).i64Value())));
    put("u8.as_i32"             , (executor, innerClazz) -> args -> new i32Value (              (                           args.get(0).u8Value() )));
    put("u8.cast_to_i8"         , (executor, innerClazz) -> args -> new i8Value  ((int) (byte)  (                           args.get(0).u8Value() )));
    put("u8.prefix -°"          , (executor, innerClazz) -> args -> new u8Value  (       0xff & (                       -   args.get(0).u8Value() )));
    put("u8.infix +°"           , (executor, innerClazz) -> args -> new u8Value  (       0xff & (args.get(0).u8Value()  +   args.get(1).u8Value() )));
    put("u8.infix -°"           , (executor, innerClazz) -> args -> new u8Value  (       0xff & (args.get(0).u8Value()  -   args.get(1).u8Value() )));
    put("u8.infix *°"           , (executor, innerClazz) -> args -> new u8Value  (       0xff & (args.get(0).u8Value()  *   args.get(1).u8Value() )));
    put("u8.div"                , (executor, innerClazz) -> args -> new u8Value  (Integer.divideUnsigned   (args.get(0).u8Value(), args.get(1).u8Value())));
    put("u8.mod"                , (executor, innerClazz) -> args -> new u8Value  (Integer.remainderUnsigned(args.get(0).u8Value(), args.get(1).u8Value())));
    put("u8.infix &"            , (executor, innerClazz) -> args -> new u8Value  (              (args.get(0).u8Value()  &   args.get(1).u8Value() )));
    put("u8.infix |"            , (executor, innerClazz) -> args -> new u8Value  (              (args.get(0).u8Value()  |   args.get(1).u8Value() )));
    put("u8.infix ^"            , (executor, innerClazz) -> args -> new u8Value  (              (args.get(0).u8Value()  ^   args.get(1).u8Value() )));
    put("u8.infix >>"           , (executor, innerClazz) -> args -> new u8Value  (              (args.get(0).u8Value()  >>> args.get(1).u8Value() )));
    put("u8.infix <<"           , (executor, innerClazz) -> args -> new u8Value  (       0xff & (args.get(0).u8Value()  <<  args.get(1).u8Value() )));
    put("u8.type.equality"      , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).u8Value()  ==  args.get(2).u8Value() )));
    put("u8.type.lteq"          , (executor, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(1).u8Value(), args.get(2).u8Value()) <= 0));
    put("u16.as_i32"            , (executor, innerClazz) -> args -> new i32Value (              (                           args.get(0).u16Value())));
    put("u16.low8bits"          , (executor, innerClazz) -> args -> new u8Value  (       0xff & (                           args.get(0).u16Value())));
    put("u16.cast_to_i16"       , (executor, innerClazz) -> args -> new i16Value ((short)       (                           args.get(0).u16Value())));
    put("u16.prefix -°"         , (executor, innerClazz) -> args -> new u16Value (     0xffff & (                       -   args.get(0).u16Value())));
    put("u16.infix +°"          , (executor, innerClazz) -> args -> new u16Value (     0xffff & (args.get(0).u16Value() +   args.get(1).u16Value())));
    put("u16.infix -°"          , (executor, innerClazz) -> args -> new u16Value (     0xffff & (args.get(0).u16Value() -   args.get(1).u16Value())));
    put("u16.infix *°"          , (executor, innerClazz) -> args -> new u16Value (     0xffff & (args.get(0).u16Value() *   args.get(1).u16Value())));
    put("u16.div"               , (executor, innerClazz) -> args -> new u16Value (Integer.divideUnsigned   (args.get(0).u16Value(), args.get(1).u16Value())));
    put("u16.mod"               , (executor, innerClazz) -> args -> new u16Value (Integer.remainderUnsigned(args.get(0).u16Value(), args.get(1).u16Value())));
    put("u16.infix &"           , (executor, innerClazz) -> args -> new u16Value (              (args.get(0).u16Value() &   args.get(1).u16Value())));
    put("u16.infix |"           , (executor, innerClazz) -> args -> new u16Value (              (args.get(0).u16Value() |   args.get(1).u16Value())));
    put("u16.infix ^"           , (executor, innerClazz) -> args -> new u16Value (              (args.get(0).u16Value() ^   args.get(1).u16Value())));
    put("u16.infix >>"          , (executor, innerClazz) -> args -> new u16Value (              (args.get(0).u16Value() >>> args.get(1).u16Value())));
    put("u16.infix <<"          , (executor, innerClazz) -> args -> new u16Value (     0xffff & (args.get(0).u16Value() <<  args.get(1).u16Value())));
    put("u16.type.equality"     , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).u16Value() ==  args.get(2).u16Value())));
    put("u16.type.lteq"         , (executor, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(1).u16Value(), args.get(2).u16Value()) <= 0));
    put("u32.as_i64"            , (executor, innerClazz) -> args -> new i64Value (Integer.toUnsignedLong(args.get(0).u32Value())));
    put("u32.low8bits"          , (executor, innerClazz) -> args -> new u8Value  (       0xff & (                           args.get(0).u32Value())));
    put("u32.low16bits"         , (executor, innerClazz) -> args -> new u16Value (     0xffff & (                           args.get(0).u32Value())));
    put("u32.cast_to_i32"       , (executor, innerClazz) -> args -> new i32Value (              (                           args.get(0).u32Value())));
    put("u32.as_f64"            , (executor, innerClazz) -> args -> new f64Value ((double)      Integer.toUnsignedLong(     args.get(0).u32Value())));
    put("u32.cast_to_f32"       , (executor, innerClazz) -> args -> new f32Value (              Float.intBitsToFloat(       args.get(0).u32Value())));
    put("u32.prefix -°"         , (executor, innerClazz) -> args -> new u32Value (              (                       -   args.get(0).u32Value())));
    put("u32.infix +°"          , (executor, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() +   args.get(1).u32Value())));
    put("u32.infix -°"          , (executor, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() -   args.get(1).u32Value())));
    put("u32.infix *°"          , (executor, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() *   args.get(1).u32Value())));
    put("u32.div"               , (executor, innerClazz) -> args -> new u32Value (Integer.divideUnsigned   (args.get(0).u32Value(), args.get(1).u32Value())));
    put("u32.mod"               , (executor, innerClazz) -> args -> new u32Value (Integer.remainderUnsigned(args.get(0).u32Value(), args.get(1).u32Value())));
    put("u32.infix &"           , (executor, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() &   args.get(1).u32Value())));
    put("u32.infix |"           , (executor, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() |   args.get(1).u32Value())));
    put("u32.infix ^"           , (executor, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() ^   args.get(1).u32Value())));
    put("u32.infix >>"          , (executor, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() >>> args.get(1).u32Value())));
    put("u32.infix <<"          , (executor, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() <<  args.get(1).u32Value())));
    put("u32.type.equality"     , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).u32Value() ==  args.get(2).u32Value())));
    put("u32.type.lteq"         , (executor, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(1).u32Value(), args.get(2).u32Value()) <= 0));
    put("u64.low8bits"          , (executor, innerClazz) -> args -> new u8Value  (       0xff & ((int)                      args.get(0).u64Value())));
    put("u64.low16bits"         , (executor, innerClazz) -> args -> new u16Value (     0xffff & ((int)                      args.get(0).u64Value())));
    put("u64.low32bits"         , (executor, innerClazz) -> args -> new u32Value ((int)         (                           args.get(0).u64Value())));
    put("u64.cast_to_i64"       , (executor, innerClazz) -> args -> new i64Value (              (                           args.get(0).u64Value())));
    put("u64.as_f64"            , (executor, innerClazz) -> args -> new f64Value (Double.parseDouble(Long.toUnsignedString(args.get(0).u64Value()))));
    put("u64.cast_to_f64"       , (executor, innerClazz) -> args -> new f64Value (              Double.longBitsToDouble(    args.get(0).u64Value())));
    put("u64.prefix -°"         , (executor, innerClazz) -> args -> new u64Value (              (                       -   args.get(0).u64Value())));
    put("u64.infix +°"          , (executor, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() +   args.get(1).u64Value())));
    put("u64.infix -°"          , (executor, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() -   args.get(1).u64Value())));
    put("u64.infix *°"          , (executor, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() *   args.get(1).u64Value())));
    put("u64.div"               , (executor, innerClazz) -> args -> new u64Value (Long.divideUnsigned   (args.get(0).u64Value(), args.get(1).u64Value())));
    put("u64.mod"               , (executor, innerClazz) -> args -> new u64Value (Long.remainderUnsigned(args.get(0).u64Value(), args.get(1).u64Value())));
    put("u64.infix &"           , (executor, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() &   args.get(1).u64Value())));
    put("u64.infix |"           , (executor, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() |   args.get(1).u64Value())));
    put("u64.infix ^"           , (executor, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() ^   args.get(1).u64Value())));
    put("u64.infix >>"          , (executor, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() >>> args.get(1).u64Value())));
    put("u64.infix <<"          , (executor, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() <<  args.get(1).u64Value())));
    put("u64.type.equality"     , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).u64Value() ==  args.get(2).u64Value())));
    put("u64.type.lteq"         , (executor, innerClazz) -> args -> new boolValue(Long.compareUnsigned(args.get(1).u64Value(), args.get(2).u64Value()) <= 0));
    put("f32.prefix -"          , (executor, innerClazz) -> args -> new f32Value (                (                       -  args.get(0).f32Value())));
    put("f32.infix +"           , (executor, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() +  args.get(1).f32Value())));
    put("f32.infix -"           , (executor, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() -  args.get(1).f32Value())));
    put("f32.infix *"           , (executor, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() *  args.get(1).f32Value())));
    put("f32.infix /"           , (executor, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() /  args.get(1).f32Value())));
    put("f32.infix %"           , (executor, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() %  args.get(1).f32Value())));
    put("f32.infix **"          , (executor, innerClazz) -> args -> new f32Value ((float) Math.pow(args.get(0).f32Value(),   args.get(1).f32Value())));
    put("f32.type.equal"        , (executor, innerClazz) -> args -> new boolValue(                (args.get(1).f32Value() == args.get(2).f32Value())));
    put("f32.type.lower_than_or_equal"
                                , (executor, innerClazz) -> args -> new boolValue(                (args.get(1).f32Value() <= args.get(2).f32Value())));
    put("f32.as_f64"            , (executor, innerClazz) -> args -> new f64Value((double)                                    args.get(0).f32Value() ));
    put("f32.cast_to_u32"       , (executor, innerClazz) -> args -> new u32Value (    Float.floatToIntBits(                  args.get(0).f32Value())));
    put("f64.prefix -"          , (executor, innerClazz) -> args -> new f64Value (                (                       -  args.get(0).f64Value())));
    put("f64.infix +"           , (executor, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() +  args.get(1).f64Value())));
    put("f64.infix -"           , (executor, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() -  args.get(1).f64Value())));
    put("f64.infix *"           , (executor, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() *  args.get(1).f64Value())));
    put("f64.infix /"           , (executor, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() /  args.get(1).f64Value())));
    put("f64.infix %"           , (executor, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() %  args.get(1).f64Value())));
    put("f64.infix **"          , (executor, innerClazz) -> args -> new f64Value (        Math.pow(args.get(0).f64Value(),   args.get(1).f64Value())));
    put("f64.type.equal"        , (executor, innerClazz) -> args -> new boolValue(                (args.get(1).f64Value() == args.get(2).f64Value())));
    put("f64.type.lower_than_or_equal"
                                , (executor, innerClazz) -> args -> new boolValue(                (args.get(1).f64Value() <= args.get(2).f64Value())));
    put("f64.as_i64_lax"        , (executor, innerClazz) -> args -> new i64Value((long)                                      args.get(0).f64Value() ));
    put("f64.as_f32"            , (executor, innerClazz) -> args -> new f32Value((float)                                     args.get(0).f64Value() ));
    put("f64.cast_to_u64"       , (executor, innerClazz) -> args -> new u64Value (    Double.doubleToLongBits(               args.get(0).f64Value())));
    put("f32.is_NaN"            , (executor, innerClazz) -> args -> new boolValue(                               Float.isNaN(args.get(0).f32Value())));
    put("f64.is_NaN"            , (executor, innerClazz) -> args -> new boolValue(                              Double.isNaN(args.get(0).f64Value())));
    put("f32.acos"              , (executor, innerClazz) -> args -> new f32Value ((float)           Math.acos(               args.get(0).f32Value())));
    put("f32.asin"              , (executor, innerClazz) -> args -> new f32Value ((float)           Math.asin(               args.get(0).f32Value())));
    put("f32.atan"              , (executor, innerClazz) -> args -> new f32Value ((float)           Math.atan(               args.get(0).f32Value())));
    put("f32.cos"               , (executor, innerClazz) -> args -> new f32Value ((float)           Math.cos(                args.get(0).f32Value())));
    put("f32.cosh"              , (executor, innerClazz) -> args -> new f32Value ((float)           Math.cosh(               args.get(0).f32Value())));
    put("f32.exp"               , (executor, innerClazz) -> args -> new f32Value ((float)           Math.exp(                args.get(0).f32Value())));
    put("f32.log"               , (executor, innerClazz) -> args -> new f32Value ((float)           Math.log(                args.get(0).f32Value())));
    put("f32.sin"               , (executor, innerClazz) -> args -> new f32Value ((float)          Math.sin(                 args.get(0).f32Value())));
    put("f32.sinh"              , (executor, innerClazz) -> args -> new f32Value ((float)          Math.sinh(                args.get(0).f32Value())));
    put("f32.square_root"       , (executor, innerClazz) -> args -> new f32Value ((float)          Math.sqrt(        (double)args.get(0).f32Value())));
    put("f32.tan"               , (executor, innerClazz) -> args -> new f32Value ((float)          Math.tan(                 args.get(0).f32Value())));
    put("f32.tanh"              , (executor, innerClazz) -> args -> new f32Value ((float)          Math.tanh(                args.get(0).f32Value())));
    put("f64.acos"              , (executor, innerClazz) -> args -> new f64Value (                 Math.acos(                args.get(0).f64Value())));
    put("f64.asin"              , (executor, innerClazz) -> args -> new f64Value (                 Math.asin(                args.get(0).f64Value())));
    put("f64.atan"              , (executor, innerClazz) -> args -> new f64Value (                 Math.atan(                args.get(0).f64Value())));
    put("f64.cos"               , (executor, innerClazz) -> args -> new f64Value (                 Math.cos(                 args.get(0).f64Value())));
    put("f64.cosh"              , (executor, innerClazz) -> args -> new f64Value (                 Math.cosh(                args.get(0).f64Value())));
    put("f64.exp"               , (executor, innerClazz) -> args -> new f64Value (                 Math.exp(                 args.get(0).f64Value())));
    put("f64.log"               , (executor, innerClazz) -> args -> new f64Value (                 Math.log(                 args.get(0).f64Value())));
    put("f64.sin"               , (executor, innerClazz) -> args -> new f64Value (                 Math.sin(                 args.get(0).f64Value())));
    put("f64.sinh"              , (executor, innerClazz) -> args -> new f64Value (                 Math.sinh(                args.get(0).f64Value())));
    put("f64.square_root"       , (executor, innerClazz) -> args -> new f64Value (                 Math.sqrt(                args.get(0).f64Value())));
    put("f64.tan"               , (executor, innerClazz) -> args -> new f64Value (                 Math.tan(                 args.get(0).f64Value())));
    put("f64.tanh"              , (executor, innerClazz) -> args -> new f64Value (                 Math.tanh(                args.get(0).f64Value())));
    put("f32.type.epsilon"      , (executor, innerClazz) -> args -> new f32Value (                  Math.ulp(                (float)1)));
    put("f32.type.max"          , (executor, innerClazz) -> args -> new f32Value (                                           Float.MAX_VALUE));
    put("f32.type.max_exp"      , (executor, innerClazz) -> args -> new i32Value (                                           Float.MAX_EXPONENT));
    put("f32.type.min_positive" , (executor, innerClazz) -> args -> new f32Value (                                           Float.MIN_NORMAL));
    put("f32.type.min_exp"      , (executor, innerClazz) -> args -> new i32Value (                                           Float.MIN_EXPONENT));
    put("f64.type.epsilon"      , (executor, innerClazz) -> args -> new f64Value (                 Math.ulp(                 (double)1)));
    put("f64.type.max"          , (executor, innerClazz) -> args -> new f64Value (                                               Double.MAX_VALUE));
    put("f64.type.max_exp"      , (executor, innerClazz) -> args -> new i32Value (                                               Double.MAX_EXPONENT));
    put("f64.type.min_positive" , (executor, innerClazz) -> args -> new f64Value (                                               Double.MIN_NORMAL));
    put("f64.type.min_exp"      , (executor, innerClazz) -> args -> new i32Value (                                               Double.MIN_EXPONENT));
    put("effect.type.abort0"      ,
        "effect.type.default0"    ,
        FuzionConstants.EFFECT_INSTATE_NAME,
        "effect.type.is_instated0",
        "effect.type.replace0"    , (executor, innerClazz) -> effect(executor, innerClazz));

    put("effect.type.from_env",
        "effect.type.unsafe_from_env",
        (executor, innerClazz) -> args ->
        {
          var ecl = executor.fuir().clazzResultClazz(innerClazz); // type
          var result = FuzionThread.current()._effects.get(ecl);
          if (result == null)
            {
              Errors.fatal("No effect installed: " + executor.fuir().clazzAsStringHuman(ecl));
            }

          if (POSTCONDITIONS) ensure
            (executor.fuir().clazzIsUnitType(ecl) || result != Value.NO_VALUE);

          return result;
        });

    /* NYI: UNDER DEVELOPMENT: abusing javaObjectToPlainInstance in mtx_*, cnd_* intrinsics
      replace by returnOutcome like in jvm backend.
    */
    /* ReentrantLock */
    put("concur.sync.mtx_init", (executor, innerClazz) -> args -> {
      var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
      return JavaInterface.javaObjectToInstance(new ReentrantLock(), resultClazz);
    });
    put("concur.sync.mtx_lock", (executor, innerClazz) -> args -> {
      ((ReentrantLock) ((JavaRef) args.get(1))._javaRef).lock();
      return new boolValue(true);
    });
    put("concur.sync.mtx_trylock", (executor, innerClazz) -> args -> new boolValue(
      ((ReentrantLock) ((JavaRef) args.get(1))._javaRef).tryLock()));
    put("concur.sync.mtx_unlock", (executor, innerClazz) -> args -> {
      try
        {
          ((ReentrantLock) ((JavaRef) args.get(1))._javaRef).unlock();
          return new boolValue(true);
        }
      catch (IllegalMonitorStateException e)
        {
          return new boolValue(false);
        }
    });
    put("concur.sync.mtx_destroy", (executor, innerClazz) -> args -> executor.unitValue());

    /* Condition */
    put("concur.sync.cnd_init", (executor, innerClazz) -> args -> {
      var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
      return JavaInterface.javaObjectToInstance(
        ((ReentrantLock) ((JavaRef) args.get(1))._javaRef).newCondition(), resultClazz);
    });
    put("concur.sync.cnd_signal", (executor, innerClazz) -> args -> {
      try
        {
          ((Condition) ((JavaRef) args.get(1))._javaRef).signal();
          return new boolValue(true);
        }
      catch (Exception e)
        {
          return new boolValue(false);
        }
    });
    put("concur.sync.cnd_broadcast", (executor, innerClazz) -> args -> {
      try
        {
          ((Condition) ((JavaRef) args.get(1))._javaRef).signalAll();
          return new boolValue(true);
        }
      catch (Exception e)
        {
          return new boolValue(false);
        }
    });
    put("concur.sync.cnd_wait", (executor, innerClazz) -> args -> {
      try
        {
          ((Condition) ((JavaRef) args.get(1))._javaRef).await();
          return new boolValue(true);
        }
      catch (Exception e)
        {
          return new boolValue(false);
        }
    });
    put("concur.sync.cnd_destroy", (executor, innerClazz) -> args -> executor.unitValue());
    put("native_string_length", (executor, innerClazz) -> args -> {
      throw new UnsupportedOperationException("NYI: UNDER DEVELOPMENT: native_string_length");
    });
    put("native_array", (executor, innerClazz) -> args -> {
      throw new UnsupportedOperationException("NYI: UNDER DEVELOPMENT: native_array");
    });
  }


  static class Abort extends Error
  {
    int _effect;
    Abort(int effect)
    {
      super();
      this._effect = effect;
    }
  }


  /**
   * Create code for one-way monad intrinsics.
   *
   * @param innerClazz the frame clazz of the called feature
   *
   * @return a Callable instance to execute the intrinsic call.
   */
  static Callable effect(Executor executor, int innerClazz)
  {
    return (args) ->
      {
        var fuir = executor.fuir();
        var in  = fuir.clazzOriginalName(innerClazz);
        int ecl = fuir.effectTypeFromIntrinsic(innerClazz);
        var ev  = args.size() > 1 ? args.get(1) : null;
        var effects = FuzionThread.current()._effects;
        switch (in)
          {
          case "effect.type.abort0"    : throw new Abort(ecl);
          case "effect.type.default0"  : if (effects.get(ecl) == null) { check(fuir.clazzIsUnitType(ecl) || ev != Value.EMPTY_VALUE); effects.put(ecl, ev); } break;
          case FuzionConstants.EFFECT_INSTATE_NAME :
            {
              // save old and instate new effect value ev:
              var prev = effects.get(ecl);
              effects.put(ecl, ev);

              // the callbacks to Fuzion for the code, fallback and finally:
              var call     = fuir.lookupCall(fuir.clazzActualGeneric(innerClazz, 0));
              var call_def = fuir.lookupCall(fuir.clazzActualGeneric(innerClazz, 1));
              var finallie = fuir.lookup_static_finally(ecl);

              Abort aborted = null;
              try
                { // run the code while effect is instated
                  var ignore = executor.callOnNewInstance(NO_SITE, call, args.get(2), new List<>());
                }
              catch (Abort a)
                {
                  aborted = a;
                }

              // in any case, restore old state and run finally on final effect value:
              var final_ev = effects.get(ecl);
              effects.put(ecl, prev);
              var ignore = executor.callOnNewInstance(NO_SITE, finallie, final_ev, new List<>());

              if (aborted != null)
                {
                  if (aborted._effect != ecl)
                    { // the abort came from another, surrounding effect, so pass it on
                      throw aborted;
                    }
                  // we got aborted, so we run `call_def` to produce default result of `instate`.
                  ignore = executor.callOnNewInstance(NO_SITE, call_def, args.get(3), new List<>(final_ev));
                }
            }
            break;
          case "effect.type.is_instated0": return new boolValue(effects.get(ecl) != null /* NOTE not containsKey since ecl may map to null! */ );
          case "effect.type.replace0"    : check(effects.get(ecl) != null, fuir.clazzIsUnitType(ecl) || ev != Value.EMPTY_VALUE); effects.put(ecl, ev);   break;
          default: throw new Error("unexpected effect intrinsic '"+in+"'");
          }
        return Value.EMPTY_VALUE;
      };
  }

  /**
   * Get InetSocketAddress of TCP (SocketChannel) or UDP (DatagramChannel) channel.
   */
  static InetSocketAddress getRemoteAddress(AutoCloseable asc) throws IOException
  {
    if (asc instanceof DatagramChannel dc)
      {
        return (InetSocketAddress) dc.getRemoteAddress();
      }
    return (InetSocketAddress)((SocketChannel)asc).getRemoteAddress();
  }

}

/* end of file */
