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

import dev.flang.ast.AbstractType; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Call; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Consts; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Impl; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Types; // NYI: remove dependency! Use dev.flang.fuir instead.

import dev.flang.air.Clazz;
import dev.flang.air.Clazzes;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;

import java.lang.reflect.Array;

import java.io.PrintStream;
import java.io.IOException;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.TreeMap;

import java.util.concurrent.TimeUnit;


/**
 * Intrinsics provides the implementation of Fuzion's intrinsic features.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Intrinsics extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * NYI: This will eventually be part of a Fuzion IR / BE Config class.
   */
  public static Boolean ENABLE_UNSAFE_INTRINSICS = null;


  /*----------------------------  variables  ----------------------------*/


  /*------------------------  static variables  -------------------------*/


  /*-------------------------  static methods  --------------------------*/


  /**
   * Get the proper output file handle 'stdout' or 'stderr' depending on the
   * prefix of the intrinsic feature name in.
   *
   * @param in name of an intrinsic feature in fuzion.std.out or fuzion.std.err.
   *
   * @return CIdent of 'stdout' or 'stderr'
   */
  private static PrintStream outOrErr(String in)
  {
    if      (in.startsWith("fuzion.std.out.")) { return System.out; }
    else if (in.startsWith("fuzion.std.err.")) { return System.err; }
    else                                       { throw new Error("outOrErr called on "+in); }
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
  public static Callable call(Interpreter interpreter, Clazz innerClazz)
  {
    if (PRECONDITIONS) require
      (innerClazz.feature().isIntrinsic());

    Callable result;
    var f = innerClazz.feature();
    String n = f.qualifiedName();
    // NYI: We must check the argument count in addition to the name!
    if      (n.equals("fuzion.std.args.count"))  { result = (args) -> new i32Value(1); /* NYI: args after cmd name not supported yet */  }
    else if (n.equals("fuzion.std.args.get"  ))
      {
        result = (args) ->
          {
            var i = args.get(1).i32Value();
            var fuir = interpreter._fuir;
            if (i == 0)
              {
                return  Interpreter.value(fuir.clazzAsString(fuir.mainClazzId()));
              }
            else
              {
                return  Interpreter.value("argument#"+i); /* NYI: args after cmd name not supported yet */
              }
          };
      }
    else if (n.equals("fuzion.std.out.write") ||
             n.equals("fuzion.std.err.write"))
      {
        var s = outOrErr(n);
        result = (args) ->
          {
            s.writeBytes((byte[])args.get(1).arrayData()._array);
            return Value.EMPTY_VALUE;
          };
      }
    else if (n.equals("fuzion.stdin.nextByte"))
    {
      result = (args) ->
        {
          try
            {
              var nextByte = System.in.readNBytes(1);
              return nextByte.length == 0 ? new i32Value(-1) : new i32Value(Byte.toUnsignedInt(nextByte[0]));
            }
            catch (IOException e)
            {
              return new i32Value(-1);
            }
        };
    }
    else if (n.equals("fuzion.std.out.flush") ||
             n.equals("fuzion.std.err.flush"))
      {
        var s = outOrErr(n);
        result = (args) ->
          {
            s.flush();
            return Value.EMPTY_VALUE;
          };
      }
    else if (n.equals("fuzion.std.exit"))
      {
        result = (args) ->
          {
            int rc = args.get(1).i32Value();
            System.exit(rc);
            return Value.EMPTY_VALUE;
          };
      }
    else if (n.equals("fuzion.java.JavaObject.isNull"))
      {
        result = (args) ->
          {
            Instance thizI = (Instance) args.get(0);
            Object thiz  =  JavaInterface.instanceToJavaObject(thizI);
            return new boolValue(thiz == null);
          };
      }
    else if (n.equals("fuzion.java.getStaticField0") ||
             n.equals("fuzion.java.getField0"      )    )
      {
        var statique = n.equals("fuzion.java.getStaticField0");
        var actualGenerics = innerClazz._type.generics();
        if ((actualGenerics == null) || (actualGenerics.size() != 1))
          {
            System.err.println("fuzion.java.getStaticField called with wrong number of actual generic arguments");
            System.exit(1);
          }
        Clazz resultClazz = innerClazz.actualClazz(actualGenerics.getFirst());
        result = (args) ->
          {
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                System.err.println("*** error: unsafe feature "+n+" disabled");
                System.exit(1);
              }
            Instance clazzOrThizI = (Instance) args.get(1);
            Instance fieldI = (Instance) args.get(2);
            String clazz = !statique ? null : (String) JavaInterface.instanceToJavaObject(clazzOrThizI);
            Object thiz  = statique  ? null :          JavaInterface.instanceToJavaObject(clazzOrThizI);
            String field = (String) JavaInterface.instanceToJavaObject(fieldI);
            return JavaInterface.getField(clazz, thiz, field, resultClazz);
          };
      }
    else if (n.equals("fuzion.java.callV0") ||
             n.equals("fuzion.java.callS0") ||
             n.equals("fuzion.java.callC0")    )
      {
        var virtual     = n.equals("fuzion.java.callV0");
        var statique    = n.equals("fuzion.java.callS0");
        var constructor = n.equals("fuzion.java.callC0");
        var actualGenerics = innerClazz._type.generics();
        Clazz resultClazz = innerClazz.actualClazz(actualGenerics.getFirst());
        result = (args) ->
          {
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                System.err.println("*** error: unsafe feature "+n+" disabled");
                System.exit(1);
              }
            int a = 1;
            var clNameI =                      (Instance) args.get(a++);
            var nameI   = constructor ? null : (Instance) args.get(a++);
            var sigI    =                      (Instance) args.get(a++);
            var thizI   = !virtual    ? null : (Instance) args.get(a++);

            var argz = args.get(a); // of type fuzion.sys.array<JavaObject>, we need to get field argz.data
            var argfields = innerClazz.argumentFields();
            var argsArray = argfields[argfields.length - 1];
            var sac = argsArray.resultClazz();
            var argzData = Interpreter.getField(Types.resolved.f_fuzion_sys_array_data, sac, argz);

            String clName =                          (String) JavaInterface.instanceToJavaObject(clNameI);
            String name   = nameI   == null ? null : (String) JavaInterface.instanceToJavaObject(nameI  );
            String sig    =                          (String) JavaInterface.instanceToJavaObject(sigI   );
            Object thiz   = thizI   == null ? null :          JavaInterface.instanceToJavaObject(thizI  );
            return JavaInterface.call(clName, name, sig, thiz, argzData, resultClazz);
          };
      }
    else if (n.equals("fuzion.java.arrayLength"))
      {
        result = (args) ->
          {
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                System.err.println("*** error: unsafe feature "+n+" disabled");
                System.exit(1);
              }
            var arr = JavaInterface.instanceToJavaObject(args.get(1).instance());
            return new i32Value(Array.getLength(arr));
          };
      }
    else if (n.equals("fuzion.java.arrayGet"))
      {
        result = (args) ->
          {
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                System.err.println("*** error: unsafe feature "+n+" disabled");
                System.exit(1);
              }
            var arr = JavaInterface.instanceToJavaObject(args.get(1).instance());
            var ix  = args.get(2).i32Value();
            var res = Array.get(arr, ix);
            Clazz resultClazz = innerClazz.resultClazz();
            return JavaInterface.javaObjectToInstance(res, resultClazz);
          };
      }
    else if (n.equals("fuzion.java.arrayToJavaObject0"))
      {
        result = (args) ->
          {
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                System.err.println("*** error: unsafe feature "+n+" disabled");
                System.exit(1);
              }
            var arrA = args.get(1).arrayData();
            var res = arrA._array;
            Clazz resultClazz = innerClazz.resultClazz();
            return JavaInterface.javaObjectToInstance(res, resultClazz);
          };
      }
    else if (n.equals("fuzion.java.stringToJavaObject0"))
      {
        result = (args) ->
          {
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                System.err.println("*** error: unsafe feature "+n+" disabled");
                System.exit(1);
              }
            var str = utf8ByteArrayDataToString(args.get(1));
            Clazz resultClazz = innerClazz.resultClazz();
            return JavaInterface.javaObjectToInstance(str, resultClazz);
          };
      }
    else if (n.equals("fuzion.java.javaStringToString"))
      {
        result = (args) ->
          {
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                System.err.println("*** error: unsafe feature "+n+" disabled");
                System.exit(1);
              }
            var javaString = (String) JavaInterface.instanceToJavaObject(args.get(1).instance());
            return Interpreter.value(javaString == null ? "--null--" : javaString);
          };
      }
    else if (n.equals("fuzion.java.i8ToJavaObject"))
      {
        result = (args) ->
          {
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                System.err.println("*** error: unsafe feature "+n+" disabled");
                System.exit(1);
              }
            var b = args.get(1).i8Value();
            var jb = Byte.valueOf((byte) b);
            Clazz resultClazz = innerClazz.resultClazz();
            return JavaInterface.javaObjectToInstance(jb, resultClazz);
          };
      }
    else if (n.equals("fuzion.java.u16ToJavaObject"))
      {
        result = (args) ->
          {
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                System.err.println("*** error: unsafe feature "+n+" disabled");
                System.exit(1);
              }
            var c = args.get(1).u16Value();
            var jc = Character.valueOf((char) c);
            Clazz resultClazz = innerClazz.resultClazz();
            return JavaInterface.javaObjectToInstance(jc, resultClazz);
          };
      }
    else if (n.equals("fuzion.java.i16ToJavaObject"))
      {
        result = (args) ->
          {
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                System.err.println("*** error: unsafe feature "+n+" disabled");
                System.exit(1);
              }
            var s = args.get(1).i16Value();
            var js = Short.valueOf((short) s);
            Clazz resultClazz = innerClazz.resultClazz();
            return JavaInterface.javaObjectToInstance(js, resultClazz);
          };
      }
    else if (n.equals("fuzion.java.i32ToJavaObject"))
      {
        result = (args) ->
          {
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                System.err.println("*** error: unsafe feature "+n+" disabled");
                System.exit(1);
              }
            var i = args.get(1).i32Value();
            var ji = Integer.valueOf(i);
            Clazz resultClazz = innerClazz.resultClazz();
            return JavaInterface.javaObjectToInstance(ji, resultClazz);
          };
      }
    else if (n.equals("fuzion.java.i64ToJavaObject"))
      {
        result = (args) ->
          {
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                System.err.println("*** error: unsafe feature "+n+" disabled");
                System.exit(1);
              }
            var l = args.get(1).i64Value();
            var jl = Long.valueOf(l);
            Clazz resultClazz = innerClazz.resultClazz();
            return JavaInterface.javaObjectToInstance(jl, resultClazz);
          };
      }
    else if (n.equals("fuzion.java.f32ToJavaObject"))
      {
        result = (args) ->
          {
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                System.err.println("*** error: unsafe feature "+n+" disabled");
                System.exit(1);
              }
            var f32 = args.get(1).f32Value();
            var jf = Float.valueOf(f32);
            Clazz resultClazz = innerClazz.resultClazz();
            return JavaInterface.javaObjectToInstance(jf, resultClazz);
          };
      }
    else if (n.equals("fuzion.java.f64ToJavaObject"))
      {
        result = (args) ->
          {
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                System.err.println("*** error: unsafe feature "+n+" disabled");
                System.exit(1);
              }
            var d = args.get(1).f64Value();
            var jd = Double.valueOf(d);
            Clazz resultClazz = innerClazz.resultClazz();
            return JavaInterface.javaObjectToInstance(jd, resultClazz);
          };
      }
    else if (n.equals("fuzion.java.boolToJavaObject"))
      {
        result = (args) ->
          {
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                System.err.println("*** error: unsafe feature "+n+" disabled");
                System.exit(1);
              }
            var b = args.get(1).boolValue();
            var jb = Boolean.valueOf(b);
            Clazz resultClazz = innerClazz.resultClazz();
            return JavaInterface.javaObjectToInstance(jb, resultClazz);
          };
      }
    else if (n.equals("fuzion.sys.array.alloc"))
      {
        result = (args) ->
          {
            return fuzionSysArrayAlloc(/* size */ args.get(1).i32Value(),
                                       /* type */ innerClazz._outer);
          };
      }
    else if (n.equals("fuzion.sys.array.get"))
      {
        result = (args) ->
          {
            return fuzionSysArrayGet(/* data  */ ((ArrayData)args.get(1)),
                                     /* index */ args.get(2).i32Value(),
                                     /* type  */ innerClazz._outer);
          };
      }
    else if (n.equals("fuzion.sys.array.setel"))
      {
        result = (args) ->
          {
            fuzionSysArraySetEl(/* data  */ ((ArrayData)args.get(1)),
                                /* index */ args.get(2).i32Value(),
                                /* value */ args.get(3),
                                /* type  */ innerClazz._outer);
            return Value.EMPTY_VALUE;
          };
      }
    else if (n.equals("fuzion.sys.env_vars.has0"))
      {
        result = (args) -> new boolValue(System.getenv(utf8ByteArrayDataToString(args.get(1))) != null);
      }
    else if (n.equals("fuzion.sys.env_vars.get0"))
      {
        result = (args) -> Interpreter.value(System.getenv(utf8ByteArrayDataToString(args.get(1))));
      }
    else if (n.equals("fuzion.sys.thread.spawn0"))
      {
        result = (args) ->
          {
            var call = Types.resolved.f_function_call;
            var oc = innerClazz.argumentFields()[0].resultClazz();
            var ic = oc.lookup(call, Call.NO_GENERICS, Clazzes.isUsedAt(call));
            var al = new ArrayList<Value>();
            al.add(args.get(1));
            new Thread(() -> interpreter.callOnInstance(ic.feature(), ic, new Instance(ic), al)).start();
            return new Instance(Clazzes.c_unit.get());
          };
      }
    else if (n.equals("safety"      ))
      {
        result = (args) -> new boolValue(Interpreter._options_.fuzionSafety());
      }
    else if (n.equals("debug"       ))
      {
        result = (args) -> new boolValue(Interpreter._options_.fuzionDebug());
      }
    else if (n.equals("debugLevel"  ))
      {
        result = (args) -> new i32Value(Interpreter._options_.fuzionDebugLevel());
      }
    else if (n.equals("bool.prefix !") ||
             n.equals("bool.infix ||") ||
             n.equals("bool.infix &&") ||
             n.equals("bool.infix :")   )
      {
        Errors.fatal(f.pos(), "intrinsic feature not supported by backend",
                     "intrinsic '"+n+"' should be handled by front end");
        result = (args) -> Value.NO_VALUE;
      }
    else if (n.equals("i8.as_i32"       )) { result = (args) -> new i32Value (              (                           args.get(0).i8Value() )); }
    else if (n.equals("i8.castTo_u8"    )) { result = (args) -> new u8Value  (       0xff & (                           args.get(0).i8Value() )); }
    else if (n.equals("i8.prefix -°"    )) { result = (args) -> new i8Value  ((int) (byte)  (                       -   args.get(0).i8Value() )); }
    else if (n.equals("i8.infix +°"     )) { result = (args) -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  +   args.get(1).i8Value() )); }
    else if (n.equals("i8.infix -°"     )) { result = (args) -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  -   args.get(1).i8Value() )); }
    else if (n.equals("i8.infix *°"     )) { result = (args) -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  *   args.get(1).i8Value() )); }
    else if (n.equals("i8.div"          )) { result = (args) -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  /   args.get(1).i8Value() )); }
    else if (n.equals("i8.mod"          )) { result = (args) -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  %   args.get(1).i8Value() )); }
    else if (n.equals("i8.infix &"      )) { result = (args) -> new i8Value  (              (args.get(0).i8Value()  &   args.get(1).i8Value() )); }
    else if (n.equals("i8.infix |"      )) { result = (args) -> new i8Value  (              (args.get(0).i8Value()  |   args.get(1).i8Value() )); }
    else if (n.equals("i8.infix ^"      )) { result = (args) -> new i8Value  (              (args.get(0).i8Value()  ^   args.get(1).i8Value() )); }
    else if (n.equals("i8.infix >>"     )) { result = (args) -> new i8Value  (              (args.get(0).i8Value()  >>  args.get(1).i8Value() )); }
    else if (n.equals("i8.infix <<"     )) { result = (args) -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  <<  args.get(1).i8Value() )); }
    else if (n.equals("i8.infix =="     )) { result = (args) -> new boolValue(              (args.get(0).i8Value()  ==  args.get(1).i8Value() )); }
    else if (n.equals("i8.infix !="     )) { result = (args) -> new boolValue(              (args.get(0).i8Value()  !=  args.get(1).i8Value() )); }
    else if (n.equals("i8.infix <"      )) { result = (args) -> new boolValue(              (args.get(0).i8Value()  <   args.get(1).i8Value() )); }
    else if (n.equals("i8.infix >"      )) { result = (args) -> new boolValue(              (args.get(0).i8Value()  >   args.get(1).i8Value() )); }
    else if (n.equals("i8.infix <="     )) { result = (args) -> new boolValue(              (args.get(0).i8Value()  <=  args.get(1).i8Value() )); }
    else if (n.equals("i8.infix >="     )) { result = (args) -> new boolValue(              (args.get(0).i8Value()  >=  args.get(1).i8Value() )); }
    else if (n.equals("i16.as_i32"      )) { result = (args) -> new i32Value (              (                           args.get(0).i16Value())); }
    else if (n.equals("i16.castTo_u16"  )) { result = (args) -> new u16Value (     0xffff & (                           args.get(0).i16Value())); }
    else if (n.equals("i16.prefix -°"   )) { result = (args) -> new i16Value ((int) (short) (                       -   args.get(0).i16Value())); }
    else if (n.equals("i16.infix +°"    )) { result = (args) -> new i16Value ((int) (short) (args.get(0).i16Value() +   args.get(1).i16Value())); }
    else if (n.equals("i16.infix -°"    )) { result = (args) -> new i16Value ((int) (short) (args.get(0).i16Value() -   args.get(1).i16Value())); }
    else if (n.equals("i16.infix *°"    )) { result = (args) -> new i16Value ((int) (short) (args.get(0).i16Value() *   args.get(1).i16Value())); }
    else if (n.equals("i16.div"         )) { result = (args) -> new i16Value ((int) (short) (args.get(0).i16Value() /   args.get(1).i16Value())); }
    else if (n.equals("i16.mod"         )) { result = (args) -> new i16Value ((int) (short) (args.get(0).i16Value() %   args.get(1).i16Value())); }
    else if (n.equals("i16.infix &"     )) { result = (args) -> new i16Value (              (args.get(0).i16Value() &   args.get(1).i16Value())); }
    else if (n.equals("i16.infix |"     )) { result = (args) -> new i16Value (              (args.get(0).i16Value() |   args.get(1).i16Value())); }
    else if (n.equals("i16.infix ^"     )) { result = (args) -> new i16Value (              (args.get(0).i16Value() ^   args.get(1).i16Value())); }
    else if (n.equals("i16.infix >>"    )) { result = (args) -> new i16Value (              (args.get(0).i16Value() >>  args.get(1).i16Value())); }
    else if (n.equals("i16.infix <<"    )) { result = (args) -> new i16Value ((int) (short) (args.get(0).i16Value() <<  args.get(1).i16Value())); }
    else if (n.equals("i16.infix =="    )) { result = (args) -> new boolValue(              (args.get(0).i16Value() ==  args.get(1).i16Value())); }
    else if (n.equals("i16.infix !="    )) { result = (args) -> new boolValue(              (args.get(0).i16Value() !=  args.get(1).i16Value())); }
    else if (n.equals("i16.infix <"     )) { result = (args) -> new boolValue(              (args.get(0).i16Value() <   args.get(1).i16Value())); }
    else if (n.equals("i16.infix >"     )) { result = (args) -> new boolValue(              (args.get(0).i16Value() >   args.get(1).i16Value())); }
    else if (n.equals("i16.infix <="    )) { result = (args) -> new boolValue(              (args.get(0).i16Value() <=  args.get(1).i16Value())); }
    else if (n.equals("i16.infix >="    )) { result = (args) -> new boolValue(              (args.get(0).i16Value() >=  args.get(1).i16Value())); }
    else if (n.equals("i32.as_i64"      )) { result = (args) -> new i64Value ((long)        (                           args.get(0).i32Value())); }
    else if (n.equals("i32.castTo_u32"  )) { result = (args) -> new u32Value (              (                           args.get(0).i32Value())); }
    else if (n.equals("i32.as_f64"      )) { result = (args) -> new f64Value ((double)      (                           args.get(0).i32Value())); }
    else if (n.equals("i32.prefix -°"   )) { result = (args) -> new i32Value (              (                       -   args.get(0).i32Value())); }
    else if (n.equals("i32.infix +°"    )) { result = (args) -> new i32Value (              (args.get(0).i32Value() +   args.get(1).i32Value())); }
    else if (n.equals("i32.infix -°"    )) { result = (args) -> new i32Value (              (args.get(0).i32Value() -   args.get(1).i32Value())); }
    else if (n.equals("i32.infix *°"    )) { result = (args) -> new i32Value (              (args.get(0).i32Value() *   args.get(1).i32Value())); }
    else if (n.equals("i32.div"         )) { result = (args) -> new i32Value (              (args.get(0).i32Value() /   args.get(1).i32Value())); }
    else if (n.equals("i32.mod"         )) { result = (args) -> new i32Value (              (args.get(0).i32Value() %   args.get(1).i32Value())); }
    else if (n.equals("i32.infix &"     )) { result = (args) -> new i32Value (              (args.get(0).i32Value() &   args.get(1).i32Value())); }
    else if (n.equals("i32.infix |"     )) { result = (args) -> new i32Value (              (args.get(0).i32Value() |   args.get(1).i32Value())); }
    else if (n.equals("i32.infix ^"     )) { result = (args) -> new i32Value (              (args.get(0).i32Value() ^   args.get(1).i32Value())); }
    else if (n.equals("i32.infix >>"    )) { result = (args) -> new i32Value (              (args.get(0).i32Value() >>  args.get(1).i32Value())); }
    else if (n.equals("i32.infix <<"    )) { result = (args) -> new i32Value (              (args.get(0).i32Value() <<  args.get(1).i32Value())); }
    else if (n.equals("i32.infix =="    )) { result = (args) -> new boolValue(              (args.get(0).i32Value() ==  args.get(1).i32Value())); }
    else if (n.equals("i32.infix !="    )) { result = (args) -> new boolValue(              (args.get(0).i32Value() !=  args.get(1).i32Value())); }
    else if (n.equals("i32.infix <"     )) { result = (args) -> new boolValue(              (args.get(0).i32Value() <   args.get(1).i32Value())); }
    else if (n.equals("i32.infix >"     )) { result = (args) -> new boolValue(              (args.get(0).i32Value() >   args.get(1).i32Value())); }
    else if (n.equals("i32.infix <="    )) { result = (args) -> new boolValue(              (args.get(0).i32Value() <=  args.get(1).i32Value())); }
    else if (n.equals("i32.infix >="    )) { result = (args) -> new boolValue(              (args.get(0).i32Value() >=  args.get(1).i32Value())); }
    else if (n.equals("i64.castTo_u64"  )) { result = (args) -> new u64Value (              (                           args.get(0).i64Value())); }
    else if (n.equals("i64.as_f64"      )) { result = (args) -> new f64Value ((double)      (                           args.get(0).i64Value())); }
    else if (n.equals("i64.prefix -°"   )) { result = (args) -> new i64Value (              (                       -   args.get(0).u64Value())); }
    else if (n.equals("i64.infix +°"    )) { result = (args) -> new i64Value (              (args.get(0).i64Value() +   args.get(1).i64Value())); }
    else if (n.equals("i64.infix -°"    )) { result = (args) -> new i64Value (              (args.get(0).i64Value() -   args.get(1).i64Value())); }
    else if (n.equals("i64.infix *°"    )) { result = (args) -> new i64Value (              (args.get(0).i64Value() *   args.get(1).i64Value())); }
    else if (n.equals("i64.div"         )) { result = (args) -> new i64Value (              (args.get(0).i64Value() /   args.get(1).i64Value())); }
    else if (n.equals("i64.mod"         )) { result = (args) -> new i64Value (              (args.get(0).i64Value() %   args.get(1).i64Value())); }
    else if (n.equals("i64.infix &"     )) { result = (args) -> new i64Value (              (args.get(0).i64Value() &   args.get(1).i64Value())); }
    else if (n.equals("i64.infix |"     )) { result = (args) -> new i64Value (              (args.get(0).i64Value() |   args.get(1).i64Value())); }
    else if (n.equals("i64.infix ^"     )) { result = (args) -> new i64Value (              (args.get(0).i64Value() ^   args.get(1).i64Value())); }
    else if (n.equals("i64.infix >>"    )) { result = (args) -> new i64Value (              (args.get(0).i64Value() >>  args.get(1).i64Value())); }
    else if (n.equals("i64.infix <<"    )) { result = (args) -> new i64Value (              (args.get(0).i64Value() <<  args.get(1).i64Value())); }
    else if (n.equals("i64.infix =="    )) { result = (args) -> new boolValue(              (args.get(0).i64Value() ==  args.get(1).i64Value())); }
    else if (n.equals("i64.infix !="    )) { result = (args) -> new boolValue(              (args.get(0).i64Value() !=  args.get(1).i64Value())); }
    else if (n.equals("i64.infix <"     )) { result = (args) -> new boolValue(              (args.get(0).i64Value() <   args.get(1).i64Value())); }
    else if (n.equals("i64.infix >"     )) { result = (args) -> new boolValue(              (args.get(0).i64Value() >   args.get(1).i64Value())); }
    else if (n.equals("i64.infix <="    )) { result = (args) -> new boolValue(              (args.get(0).i64Value() <=  args.get(1).i64Value())); }
    else if (n.equals("i64.infix >="    )) { result = (args) -> new boolValue(              (args.get(0).i64Value() >=  args.get(1).i64Value())); }
    else if (n.equals("u8.as_i32"       )) { result = (args) -> new i32Value (              (                           args.get(0).u8Value() )); }
    else if (n.equals("u8.castTo_i8"    )) { result = (args) -> new i8Value  ((int) (byte)  (                           args.get(0).u8Value() )); }
    else if (n.equals("u8.prefix -°"    )) { result = (args) -> new u8Value  (       0xff & (                       -   args.get(0).u8Value() )); }
    else if (n.equals("u8.infix +°"     )) { result = (args) -> new u8Value  (       0xff & (args.get(0).u8Value()  +   args.get(1).u8Value() )); }
    else if (n.equals("u8.infix -°"     )) { result = (args) -> new u8Value  (       0xff & (args.get(0).u8Value()  -   args.get(1).u8Value() )); }
    else if (n.equals("u8.infix *°"     )) { result = (args) -> new u8Value  (       0xff & (args.get(0).u8Value()  *   args.get(1).u8Value() )); }
    else if (n.equals("u8.div"          )) { result = (args) -> new u8Value  (Integer.divideUnsigned   (args.get(0).u8Value(), args.get(1).u8Value())); }
    else if (n.equals("u8.mod"          )) { result = (args) -> new u8Value  (Integer.remainderUnsigned(args.get(0).u8Value(), args.get(1).u8Value())); }
    else if (n.equals("u8.infix &"      )) { result = (args) -> new u8Value  (              (args.get(0).u8Value()  &   args.get(1).u8Value() )); }
    else if (n.equals("u8.infix |"      )) { result = (args) -> new u8Value  (              (args.get(0).u8Value()  |   args.get(1).u8Value() )); }
    else if (n.equals("u8.infix ^"      )) { result = (args) -> new u8Value  (              (args.get(0).u8Value()  ^   args.get(1).u8Value() )); }
    else if (n.equals("u8.infix >>"     )) { result = (args) -> new u8Value  (              (args.get(0).u8Value()  >>> args.get(1).u8Value() )); }
    else if (n.equals("u8.infix <<"     )) { result = (args) -> new u8Value  (       0xff & (args.get(0).u8Value()  <<  args.get(1).u8Value() )); }
    else if (n.equals("u8.infix =="     )) { result = (args) -> new boolValue(              (args.get(0).u8Value()  ==  args.get(1).u8Value() )); }
    else if (n.equals("u8.infix !="     )) { result = (args) -> new boolValue(              (args.get(0).u8Value()  !=  args.get(1).u8Value() )); }
    else if (n.equals("u8.infix <"      )) { result = (args) -> new boolValue(Integer.compareUnsigned(args.get(0).u8Value(), args.get(1).u8Value()) <  0); }
    else if (n.equals("u8.infix >"      )) { result = (args) -> new boolValue(Integer.compareUnsigned(args.get(0).u8Value(), args.get(1).u8Value()) >  0); }
    else if (n.equals("u8.infix <="     )) { result = (args) -> new boolValue(Integer.compareUnsigned(args.get(0).u8Value(), args.get(1).u8Value()) <= 0); }
    else if (n.equals("u8.infix >="     )) { result = (args) -> new boolValue(Integer.compareUnsigned(args.get(0).u8Value(), args.get(1).u8Value()) >= 0); }
    else if (n.equals("u16.as_i32"      )) { result = (args) -> new i32Value (              (                           args.get(0).u16Value())); }
    else if (n.equals("u16.low8bits"    )) { result = (args) -> new u8Value  (       0xff & (                           args.get(0).u16Value())); }
    else if (n.equals("u16.castTo_i16"  )) { result = (args) -> new i16Value ((short)       (                           args.get(0).u16Value())); }
    else if (n.equals("u16.prefix -°"   )) { result = (args) -> new u16Value (     0xffff & (                       -   args.get(0).u16Value())); }
    else if (n.equals("u16.infix +°"    )) { result = (args) -> new u16Value (     0xffff & (args.get(0).u16Value() +   args.get(1).u16Value())); }
    else if (n.equals("u16.infix -°"    )) { result = (args) -> new u16Value (     0xffff & (args.get(0).u16Value() -   args.get(1).u16Value())); }
    else if (n.equals("u16.infix *°"    )) { result = (args) -> new u16Value (     0xffff & (args.get(0).u16Value() *   args.get(1).u16Value())); }
    else if (n.equals("u16.div"         )) { result = (args) -> new u16Value (Integer.divideUnsigned   (args.get(0).u16Value(), args.get(1).u16Value())); }
    else if (n.equals("u16.mod"         )) { result = (args) -> new u16Value (Integer.remainderUnsigned(args.get(0).u16Value(), args.get(1).u16Value())); }
    else if (n.equals("u16.infix &"     )) { result = (args) -> new u16Value (              (args.get(0).u16Value() &   args.get(1).u16Value())); }
    else if (n.equals("u16.infix |"     )) { result = (args) -> new u16Value (              (args.get(0).u16Value() |   args.get(1).u16Value())); }
    else if (n.equals("u16.infix ^"     )) { result = (args) -> new u16Value (              (args.get(0).u16Value() ^   args.get(1).u16Value())); }
    else if (n.equals("u16.infix >>"    )) { result = (args) -> new u16Value (              (args.get(0).u16Value() >>> args.get(1).u16Value())); }
    else if (n.equals("u16.infix <<"    )) { result = (args) -> new u16Value (     0xffff & (args.get(0).u16Value() <<  args.get(1).u16Value())); }
    else if (n.equals("u16.infix =="    )) { result = (args) -> new boolValue(              (args.get(0).u16Value() ==  args.get(1).u16Value())); }
    else if (n.equals("u16.infix !="    )) { result = (args) -> new boolValue(              (args.get(0).u16Value() !=  args.get(1).u16Value())); }
    else if (n.equals("u16.infix <"     )) { result = (args) -> new boolValue(Integer.compareUnsigned(args.get(0).u16Value(), args.get(1).u16Value()) <  0); }
    else if (n.equals("u16.infix >"     )) { result = (args) -> new boolValue(Integer.compareUnsigned(args.get(0).u16Value(), args.get(1).u16Value()) >  0); }
    else if (n.equals("u16.infix <="    )) { result = (args) -> new boolValue(Integer.compareUnsigned(args.get(0).u16Value(), args.get(1).u16Value()) <= 0); }
    else if (n.equals("u16.infix >="    )) { result = (args) -> new boolValue(Integer.compareUnsigned(args.get(0).u16Value(), args.get(1).u16Value()) >= 0); }
    else if (n.equals("u32.as_i64"      )) { result = (args) -> new i64Value (Integer.toUnsignedLong(args.get(0).u32Value())); }
    else if (n.equals("u32.low8bits"    )) { result = (args) -> new u8Value  (       0xff & (                           args.get(0).u32Value())); }
    else if (n.equals("u32.low16bits"   )) { result = (args) -> new u16Value (     0xffff & (                           args.get(0).u32Value())); }
    else if (n.equals("u32.castTo_i32"  )) { result = (args) -> new i32Value (              (                           args.get(0).u32Value())); }
    else if (n.equals("u32.as_f64"      )) { result = (args) -> new f64Value ((double)      Integer.toUnsignedLong(     args.get(0).u32Value())); }
    else if (n.equals("u32.castTo_f32"  )) { result = (args) -> new f32Value (              Float.intBitsToFloat(       args.get(0).u32Value())); }
    else if (n.equals("u32.prefix -°"   )) { result = (args) -> new u32Value (              (                       -   args.get(0).u32Value())); }
    else if (n.equals("u32.infix +°"    )) { result = (args) -> new u32Value (              (args.get(0).u32Value() +   args.get(1).u32Value())); }
    else if (n.equals("u32.infix -°"    )) { result = (args) -> new u32Value (              (args.get(0).u32Value() -   args.get(1).u32Value())); }
    else if (n.equals("u32.infix *°"    )) { result = (args) -> new u32Value (              (args.get(0).u32Value() *   args.get(1).u32Value())); }
    else if (n.equals("u32.div"         )) { result = (args) -> new u32Value (Integer.divideUnsigned   (args.get(0).u32Value(), args.get(1).u32Value())); }
    else if (n.equals("u32.mod"         )) { result = (args) -> new u32Value (Integer.remainderUnsigned(args.get(0).u32Value(), args.get(1).u32Value())); }
    else if (n.equals("u32.infix &"     )) { result = (args) -> new u32Value (              (args.get(0).u32Value() &   args.get(1).u32Value())); }
    else if (n.equals("u32.infix |"     )) { result = (args) -> new u32Value (              (args.get(0).u32Value() |   args.get(1).u32Value())); }
    else if (n.equals("u32.infix ^"     )) { result = (args) -> new u32Value (              (args.get(0).u32Value() ^   args.get(1).u32Value())); }
    else if (n.equals("u32.infix >>"    )) { result = (args) -> new u32Value (              (args.get(0).u32Value() >>> args.get(1).u32Value())); }
    else if (n.equals("u32.infix <<"    )) { result = (args) -> new u32Value (              (args.get(0).u32Value() <<  args.get(1).u32Value())); }
    else if (n.equals("u32.infix =="    )) { result = (args) -> new boolValue(              (args.get(0).u32Value() ==  args.get(1).u32Value())); }
    else if (n.equals("u32.infix !="    )) { result = (args) -> new boolValue(              (args.get(0).u32Value() !=  args.get(1).u32Value())); }
    else if (n.equals("u32.infix <"     )) { result = (args) -> new boolValue(Integer.compareUnsigned(args.get(0).u32Value(), args.get(1).u32Value()) <  0); }
    else if (n.equals("u32.infix >"     )) { result = (args) -> new boolValue(Integer.compareUnsigned(args.get(0).u32Value(), args.get(1).u32Value()) >  0); }
    else if (n.equals("u32.infix <="    )) { result = (args) -> new boolValue(Integer.compareUnsigned(args.get(0).u32Value(), args.get(1).u32Value()) <= 0); }
    else if (n.equals("u32.infix >="    )) { result = (args) -> new boolValue(Integer.compareUnsigned(args.get(0).u32Value(), args.get(1).u32Value()) >= 0); }
    else if (n.equals("u64.low8bits"    )) { result = (args) -> new u8Value  (       0xff & ((int)                      args.get(0).u64Value())); }
    else if (n.equals("u64.low16bits"   )) { result = (args) -> new u16Value (     0xffff & ((int)                      args.get(0).u64Value())); }
    else if (n.equals("u64.low32bits"   )) { result = (args) -> new u32Value ((int)         (                           args.get(0).u64Value())); }
    else if (n.equals("u64.castTo_i64"  )) { result = (args) -> new i64Value (              (                           args.get(0).u64Value())); }
    else if (n.equals("u64.as_f64"      )) { result = (args) -> new f64Value (Double.parseDouble(Long.toUnsignedString(args.get(0).u64Value()))); }
    else if (n.equals("u64.castTo_f64"  )) { result = (args) -> new f64Value (              Double.longBitsToDouble(    args.get(0).u64Value())); }
    else if (n.equals("u64.prefix -°"   )) { result = (args) -> new u64Value (              (                       -   args.get(0).u64Value())); }
    else if (n.equals("u64.infix +°"    )) { result = (args) -> new u64Value (              (args.get(0).u64Value() +   args.get(1).u64Value())); }
    else if (n.equals("u64.infix -°"    )) { result = (args) -> new u64Value (              (args.get(0).u64Value() -   args.get(1).u64Value())); }
    else if (n.equals("u64.infix *°"    )) { result = (args) -> new u64Value (              (args.get(0).u64Value() *   args.get(1).u64Value())); }
    else if (n.equals("u64.div"         )) { result = (args) -> new u64Value (Long.divideUnsigned   (args.get(0).u64Value(), args.get(1).u64Value())); }
    else if (n.equals("u64.mod"         )) { result = (args) -> new u64Value (Long.remainderUnsigned(args.get(0).u64Value(), args.get(1).u64Value())); }
    else if (n.equals("u64.infix &"     )) { result = (args) -> new u64Value (              (args.get(0).u64Value() &   args.get(1).u64Value())); }
    else if (n.equals("u64.infix |"     )) { result = (args) -> new u64Value (              (args.get(0).u64Value() |   args.get(1).u64Value())); }
    else if (n.equals("u64.infix ^"     )) { result = (args) -> new u64Value (              (args.get(0).u64Value() ^   args.get(1).u64Value())); }
    else if (n.equals("u64.infix >>"    )) { result = (args) -> new u64Value (              (args.get(0).u64Value() >>> args.get(1).u64Value())); }
    else if (n.equals("u64.infix <<"    )) { result = (args) -> new u64Value (              (args.get(0).u64Value() <<  args.get(1).u64Value())); }
    else if (n.equals("u64.infix =="    )) { result = (args) -> new boolValue(              (args.get(0).u64Value() ==  args.get(1).u64Value())); }
    else if (n.equals("u64.infix !="    )) { result = (args) -> new boolValue(              (args.get(0).u64Value() !=  args.get(1).u64Value())); }
    else if (n.equals("u64.infix <"     )) { result = (args) -> new boolValue(Long.compareUnsigned(args.get(0).u64Value(), args.get(1).u64Value()) <  0); }
    else if (n.equals("u64.infix >"     )) { result = (args) -> new boolValue(Long.compareUnsigned(args.get(0).u64Value(), args.get(1).u64Value()) >  0); }
    else if (n.equals("u64.infix <="    )) { result = (args) -> new boolValue(Long.compareUnsigned(args.get(0).u64Value(), args.get(1).u64Value()) <= 0); }
    else if (n.equals("u64.infix >="    )) { result = (args) -> new boolValue(Long.compareUnsigned(args.get(0).u64Value(), args.get(1).u64Value()) >= 0); }
    else if (n.equals("f32.prefix -"    )) { result = (args) -> new f32Value (                (                       -  args.get(0).f32Value())); }
    else if (n.equals("f32.infix +"     )) { result = (args) -> new f32Value (                (args.get(0).f32Value() +  args.get(1).f32Value())); }
    else if (n.equals("f32.infix -"     )) { result = (args) -> new f32Value (                (args.get(0).f32Value() -  args.get(1).f32Value())); }
    else if (n.equals("f32.infix *"     )) { result = (args) -> new f32Value (                (args.get(0).f32Value() *  args.get(1).f32Value())); }
    else if (n.equals("f32.infix /"     )) { result = (args) -> new f32Value (                (args.get(0).f32Value() /  args.get(1).f32Value())); }
    else if (n.equals("f32.infix %"     )) { result = (args) -> new f32Value (                (args.get(0).f32Value() %  args.get(1).f32Value())); }
    else if (n.equals("f32.infix **"    )) { result = (args) -> new f32Value ((float) Math.pow(args.get(0).f32Value(),   args.get(1).f32Value())); }
    else if (n.equals("f32.infix =="    )) { result = (args) -> new boolValue(                (args.get(0).f32Value() == args.get(1).f32Value())); }
    else if (n.equals("f32.infix !="    )) { result = (args) -> new boolValue(                (args.get(0).f32Value() != args.get(1).f32Value())); }
    else if (n.equals("f32.infix <"     )) { result = (args) -> new boolValue(                (args.get(0).f32Value() <  args.get(1).f32Value())); }
    else if (n.equals("f32.infix <="    )) { result = (args) -> new boolValue(                (args.get(0).f32Value() <= args.get(1).f32Value())); }
    else if (n.equals("f32.infix >"     )) { result = (args) -> new boolValue(                (args.get(0).f32Value() >  args.get(1).f32Value())); }
    else if (n.equals("f32.infix >="    )) { result = (args) -> new boolValue(                (args.get(0).f32Value() >= args.get(1).f32Value())); }
    else if (n.equals("f32.as_f64"      )) { result = (args) -> new f64Value((double)                                    args.get(0).f32Value() ); }
    else if (n.equals("f32.castTo_u32"  )) { result = (args) -> new u32Value (    Float.floatToIntBits(                  args.get(0).f32Value())); }
    else if (n.equals("f32.asString"    )) { result = (args) -> Interpreter.value(Float.toString      (                  args.get(0).f32Value())); }
    else if (n.equals("f64.prefix -"    )) { result = (args) -> new f64Value (                (                       -  args.get(0).f64Value())); }
    else if (n.equals("f64.infix +"     )) { result = (args) -> new f64Value (                (args.get(0).f64Value() +  args.get(1).f64Value())); }
    else if (n.equals("f64.infix -"     )) { result = (args) -> new f64Value (                (args.get(0).f64Value() -  args.get(1).f64Value())); }
    else if (n.equals("f64.infix *"     )) { result = (args) -> new f64Value (                (args.get(0).f64Value() *  args.get(1).f64Value())); }
    else if (n.equals("f64.infix /"     )) { result = (args) -> new f64Value (                (args.get(0).f64Value() /  args.get(1).f64Value())); }
    else if (n.equals("f64.infix %"     )) { result = (args) -> new f64Value (                (args.get(0).f64Value() %  args.get(1).f64Value())); }
    else if (n.equals("f64.infix **"    )) { result = (args) -> new f64Value (        Math.pow(args.get(0).f64Value(),   args.get(1).f64Value())); }
    else if (n.equals("f64.infix =="    )) { result = (args) -> new boolValue(                (args.get(0).f64Value() == args.get(1).f64Value())); }
    else if (n.equals("f64.infix !="    )) { result = (args) -> new boolValue(                (args.get(0).f64Value() != args.get(1).f64Value())); }
    else if (n.equals("f64.infix <"     )) { result = (args) -> new boolValue(                (args.get(0).f64Value() <  args.get(1).f64Value())); }
    else if (n.equals("f64.infix <="    )) { result = (args) -> new boolValue(                (args.get(0).f64Value() <= args.get(1).f64Value())); }
    else if (n.equals("f64.infix >"     )) { result = (args) -> new boolValue(                (args.get(0).f64Value() >  args.get(1).f64Value())); }
    else if (n.equals("f64.infix >="    )) { result = (args) -> new boolValue(                (args.get(0).f64Value() >= args.get(1).f64Value())); }
    else if (n.equals("f64.as_i64_lax"  )) { result = (args) -> new i64Value((long)                                      args.get(0).f64Value() ); }
    else if (n.equals("f64.as_f32"      )) { result = (args) -> new f32Value((float)                                     args.get(0).f64Value() ); }
    else if (n.equals("f64.castTo_u64"  )) { result = (args) -> new u64Value (    Double.doubleToLongBits(               args.get(0).f64Value())); }
    else if (n.equals("f64.asString"    )) { result = (args) -> Interpreter.value(Double.toString       (                args.get(0).f64Value())); }
    else if (n.equals("f32s.isNaN"      )) { result = (args) -> new boolValue(                               Float.isNaN(args.get(1).f32Value())); }
    else if (n.equals("f64s.isNaN"      )) { result = (args) -> new boolValue(                              Double.isNaN(args.get(1).f64Value())); }
    else if (n.equals("f32s.acos"       )) { result = (args) -> new f32Value ((float)           Math.acos(               args.get(1).f32Value())); }
    else if (n.equals("f32s.asin"       )) { result = (args) -> new f32Value ((float)           Math.asin(               args.get(1).f32Value())); }
    else if (n.equals("f32s.atan"       )) { result = (args) -> new f32Value ((float)           Math.atan(               args.get(1).f32Value())); }
    else if (n.equals("f32s.atan2"      )) { result = (args) -> new f32Value ((float)  Math.atan2(args.get(1).f32Value(),args.get(2).f32Value())); }
    else if (n.equals("f32s.cos"        )) { result = (args) -> new f32Value ((float)           Math.cos(                args.get(1).f32Value())); }
    else if (n.equals("f32s.cosh"       )) { result = (args) -> new f32Value ((float)           Math.cosh(               args.get(1).f32Value())); }
    else if (n.equals("f32s.epsilon"    )) { result = (args) -> new f32Value (                  Math.ulp(                (float)1));               }
    else if (n.equals("f32s.exp"        )) { result = (args) -> new f32Value ((float)           Math.exp(                args.get(1).f32Value())); }
    else if (n.equals("f32s.log"        )) { result = (args) -> new f32Value ((float)           Math.log(                args.get(1).f32Value())); }
    else if (n.equals("f32s.max"        )) { result = (args) -> new f32Value (                                           Float.MAX_VALUE);         }
    else if (n.equals("f32s.maxExp"     )) { result = (args) -> new i32Value (                                           Float.MAX_EXPONENT);      }
    else if (n.equals("f32s.minPositive")) { result = (args) -> new f32Value (                                           Float.MIN_NORMAL);        }
    else if (n.equals("f32s.minExp"     )) { result = (args) -> new i32Value (                                           Float.MIN_EXPONENT);      }
    else if (n.equals("f32s.sin"        )) { result = (args) -> new f32Value ((float)          Math.sin(                 args.get(1).f32Value())); }
    else if (n.equals("f32s.sinh"       )) { result = (args) -> new f32Value ((float)          Math.sinh(                args.get(1).f32Value())); }
    else if (n.equals("f32s.squareRoot" )) { result = (args) -> new f32Value ((float)          Math.sqrt(        (double)args.get(1).f32Value())); }
    else if (n.equals("f32s.tan"        )) { result = (args) -> new f32Value ((float)          Math.tan(                 args.get(1).f32Value())); }
    else if (n.equals("f32s.tanh"       )) { result = (args) -> new f32Value ((float)          Math.tan(                 args.get(1).f32Value())); }
    else if (n.equals("f64s.acos"       )) { result = (args) -> new f64Value (                 Math.acos(                args.get(1).f64Value())); }
    else if (n.equals("f64s.asin"       )) { result = (args) -> new f64Value (                 Math.asin(                args.get(1).f64Value())); }
    else if (n.equals("f64s.atan"       )) { result = (args) -> new f64Value (                 Math.atan(                args.get(1).f64Value())); }
    else if (n.equals("f64s.atan2"      )) { result = (args) -> new f64Value (         Math.atan2(args.get(1).f64Value(),args.get(2).f64Value())); }
    else if (n.equals("f64s.cos"        )) { result = (args) -> new f64Value (                 Math.cos(                 args.get(1).f64Value())); }
    else if (n.equals("f64s.cosh"       )) { result = (args) -> new f64Value (                 Math.cosh(                args.get(1).f64Value())); }
    else if (n.equals("f64s.epsilon"    )) { result = (args) -> new f64Value (                 Math.ulp(                 (double)1));              }
    else if (n.equals("f64s.exp"        )) { result = (args) -> new f64Value (                 Math.exp(                 args.get(1).f64Value())); }
    else if (n.equals("f64s.log"        )) { result = (args) -> new f64Value (                 Math.log(                 args.get(1).f64Value())); }
    else if (n.equals("f64s.max"        )) { result = (args) -> new f64Value (                                               Double.MAX_VALUE);    }
    else if (n.equals("f64s.maxExp"     )) { result = (args) -> new i32Value (                                               Double.MAX_EXPONENT); }
    else if (n.equals("f64s.minPositive")) { result = (args) -> new f64Value (                                               Double.MIN_NORMAL);   }
    else if (n.equals("f64s.minExp"     )) { result = (args) -> new i32Value (                                               Double.MIN_EXPONENT); }
    else if (n.equals("f64s.sin"        )) { result = (args) -> new f64Value (                 Math.sin(                 args.get(1).f64Value())); }
    else if (n.equals("f64s.sinh"       )) { result = (args) -> new f64Value (                 Math.sinh(                args.get(1).f64Value())); }
    else if (n.equals("f64s.squareRoot" )) { result = (args) -> new f64Value (                 Math.sqrt(                args.get(1).f64Value())); }
    else if (n.equals("f64s.tan"        )) { result = (args) -> new f64Value (                 Math.tan(                 args.get(1).f64Value())); }
    else if (n.equals("f64s.tanh"       )) { result = (args) -> new f64Value (                 Math.tan(                 args.get(1).f64Value())); }
    else if (n.equals("Object.hashCode" )) { result = (args) -> new i32Value (args.get(0).toString().hashCode()); }
    else if (n.equals("Object.asString" )) { result = (args) -> Interpreter.value("instance[" + innerClazz._outer.toString() + "]"); }
    else if (n.equals("fuzion.std.nano_time"  )) { result = (args) -> new u64Value (System.nanoTime()); }
    else if (n.equals("fuzion.std.nano_sleep" )) {
      result = (args) ->
        {
          var d = args.get(1).u64Value();
          try
            {
              TimeUnit.NANOSECONDS.sleep(d < 0 ? Long.MAX_VALUE : d);
            }
          catch (InterruptedException ie)
            {
              throw new Error("unexpected interrupt", ie);
            }
          return new Instance(Clazzes.c_unit.get());
        };
    }
    else if (n.equals("effect.replace" ) ||
             n.equals("effect.default" ) ||
             n.equals("effect.abortable")||
             n.equals("effect.abort"   )    ) {  result = effect(interpreter, n, innerClazz);  }
    else if (n.equals("effects.exists"))
      {
        result = (args) ->
          {
            var cl = innerClazz.actualGenerics()[0];
            return new boolValue(FuzionThread.current()._effects.get(cl) != null /* NOTE not containsKey since cl may map to null! */ );
          };
      }
    else
      {
        Errors.fatal(f.pos(),
                     "Intrinsic feature not supported",
                     "Missing intrinsic feature: " + f.qualifiedName());
        result = (args) -> Value.NO_VALUE;
      }
    return result;
  }

  static class Abort extends Error
  {
    Clazz _effect;
    Abort(Clazz effect)
    {
      super();
      this._effect = effect;
    }
  }


  /**
   * Create code for one-way monad intrinsics.
   *
   * @param n qualified name of the intrinsic to be called.
   *
   * @param innerClazz the frame clazz of the called feature
   *
   * @return a Callable instance to execute the intrinsic call.
   */
  static Callable effect(Interpreter interpreter, String n, Clazz innerClazz)
  {
    return (args) ->
      {
        var m = args.get(0);
        var cl = innerClazz._outer;
        switch (n)
          {
          case "effect.replace": check(FuzionThread.current()._effects.get(cl) != null); FuzionThread.current()._effects.put(cl, m   );   break;
          case "effect.default": if (FuzionThread.current()._effects.get(cl) == null) {  FuzionThread.current()._effects.put(cl, m   ); } break;
          case "effect.abortable" :
            {
              var prev = FuzionThread.current()._effects.get(cl);
              FuzionThread.current()._effects.put(cl, m);
              var call = Types.resolved.f_function_call;
              var oc = innerClazz.actualGenerics()[0]; //innerClazz.argumentFields()[0].resultClazz();
              var ic = oc.lookup(call, Call.NO_GENERICS, Clazzes.isUsedAt(call));
              var al = new ArrayList<Value>();
              al.add(args.get(1));
              try {
                var ignore = interpreter.callOnInstance(ic.feature(), ic, new Instance(ic), al);
                return new boolValue(true);
              } catch (Abort a) {
                if (a._effect == cl)
                  {
                    return new boolValue(false);
                  }
                else
                  {
                    throw a;
                  }
              } finally {
                FuzionThread.current()._effects.put(cl, prev);
              }
            }
          case "effect.abort": throw new Abort(cl);
          default: throw new Error("unexected effect intrinsic '"+n+"'");
          }
        return Value.EMPTY_VALUE;
      };
  }


  static AbstractType elementType(Clazz arrayClazz)
  {
    // NYI: Properly determine generic argument type of array
    var arrayType = arrayClazz._type;
    if (arrayType.compareTo(Types.resolved.t_conststring) == 0 /* NYI: Hack */)
      {
        return Types.resolved.t_i32;
      }
    else
      {
        return arrayType.generics().getFirst();
      }
  }

  static ArrayData fuzionSysArrayAlloc(int sz,
                                       Clazz arrayClazz)
  {
    // NYI: Properly determine generic argument type of array
    var elementType = elementType(arrayClazz);
    if      (elementType.compareTo(Types.resolved.t_i8  ) == 0) { return new ArrayData(new byte   [sz]); }
    else if (elementType.compareTo(Types.resolved.t_i16 ) == 0) { return new ArrayData(new short  [sz]); }
    else if (elementType.compareTo(Types.resolved.t_i32 ) == 0) { return new ArrayData(new int    [sz]); }
    else if (elementType.compareTo(Types.resolved.t_i64 ) == 0) { return new ArrayData(new long   [sz]); }
    else if (elementType.compareTo(Types.resolved.t_u8  ) == 0) { return new ArrayData(new byte   [sz]); }
    else if (elementType.compareTo(Types.resolved.t_u16 ) == 0) { return new ArrayData(new char   [sz]); }
    else if (elementType.compareTo(Types.resolved.t_u32 ) == 0) { return new ArrayData(new int    [sz]); }
    else if (elementType.compareTo(Types.resolved.t_u64 ) == 0) { return new ArrayData(new long   [sz]); }
    else if (elementType.compareTo(Types.resolved.t_bool) == 0) { return new ArrayData(new boolean[sz]); }
    else                                                        { return new ArrayData(new Value  [sz]); }
  }

  static void fuzionSysArraySetEl(ArrayData ad,
                                  int x,
                                  Value v,
                                  Clazz arrayClazz)
  {
    // NYI: Properly determine generic argument type of array
    var elementType = elementType(arrayClazz);
    ad.checkIndex(x);
    if      (elementType.compareTo(Types.resolved.t_i8  ) == 0) { ((byte   [])ad._array)[x] = (byte   ) v.i8Value();   }
    else if (elementType.compareTo(Types.resolved.t_i16 ) == 0) { ((short  [])ad._array)[x] = (short  ) v.i16Value();  }
    else if (elementType.compareTo(Types.resolved.t_i32 ) == 0) { ((int    [])ad._array)[x] =           v.i32Value();  }
    else if (elementType.compareTo(Types.resolved.t_i64 ) == 0) { ((long   [])ad._array)[x] =           v.i64Value();  }
    else if (elementType.compareTo(Types.resolved.t_u8  ) == 0) { ((byte   [])ad._array)[x] = (byte   ) v.u8Value();   }
    else if (elementType.compareTo(Types.resolved.t_u16 ) == 0) { ((char   [])ad._array)[x] = (char   ) v.u16Value();  }
    else if (elementType.compareTo(Types.resolved.t_u32 ) == 0) { ((int    [])ad._array)[x] =           v.u32Value();  }
    else if (elementType.compareTo(Types.resolved.t_u64 ) == 0) { ((long   [])ad._array)[x] =           v.u64Value();  }
    else if (elementType.compareTo(Types.resolved.t_bool) == 0) { ((boolean[])ad._array)[x] =           v.boolValue(); }
    else                                                        { ((Value  [])ad._array)[x] =           v;             }
  }


  static Value fuzionSysArrayGet(ArrayData ad,
                                 int x,
                                 Clazz arrayClazz)
  {
    // NYI: Properly determine generic argument type of array
    var elementType = elementType(arrayClazz);
    ad.checkIndex(x);
    if      (elementType.compareTo(Types.resolved.t_i8  ) == 0) { return new i8Value  (((byte   [])ad._array)[x]       ); }
    else if (elementType.compareTo(Types.resolved.t_i16 ) == 0) { return new i16Value (((short  [])ad._array)[x]       ); }
    else if (elementType.compareTo(Types.resolved.t_i32 ) == 0) { return new i32Value (((int    [])ad._array)[x]       ); }
    else if (elementType.compareTo(Types.resolved.t_i64 ) == 0) { return new i64Value (((long   [])ad._array)[x]       ); }
    else if (elementType.compareTo(Types.resolved.t_u8  ) == 0) { return new u8Value  (((byte   [])ad._array)[x] & 0xff); }
    else if (elementType.compareTo(Types.resolved.t_u16 ) == 0) { return new u16Value (((char   [])ad._array)[x]       ); }
    else if (elementType.compareTo(Types.resolved.t_u32 ) == 0) { return new u32Value (((int    [])ad._array)[x]       ); }
    else if (elementType.compareTo(Types.resolved.t_u64 ) == 0) { return new u64Value (((long   [])ad._array)[x]       ); }
    else if (elementType.compareTo(Types.resolved.t_bool) == 0) { return new boolValue(((boolean[])ad._array)[x]       ); }
    else                                                        { return              ((Value   [])ad._array)[x]        ; }
  }

}

/* end of file */
