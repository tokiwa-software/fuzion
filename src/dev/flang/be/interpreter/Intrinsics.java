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
import java.io.RandomAccessFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;

import java.util.concurrent.TimeUnit;


/**
 * Intrinsics provides the implementation of Fuzion's intrinsic features.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Intrinsics extends ANY
{

  /*----------------------------  interfaces  ---------------------------*/


  interface IntrinsicCode
  {
    Callable get(Interpreter interpreter, Clazz innerClazz);
  }


  /*----------------------------  constants  ----------------------------*/


  /**
   * NYI: This will eventually be part of a Fuzion IR / BE Config class.
   */
  public static Boolean ENABLE_UNSAFE_INTRINSICS = null;


  static TreeMap<String, IntrinsicCode> _intrinsics_ = new TreeMap<>();


  /**
   * This will contain the current open streams
   * The key represents a file descriptor
   * The value represents the open stream
   */
  private static TreeMap<Long, RandomAccessFile> _openStreams_ = new TreeMap<Long, RandomAccessFile>();


  /*----------------------------  variables  ----------------------------*/


  /*------------------------  static variables  -------------------------*/


  /**
   * This will represent the current available file descriptor number to be used as a key for the openstreams maps
   * The value of this variable will be incremented each time a new stream is created
   */
  private static Stack<Long> _availableFileDescriptors_ = new Stack<Long>();


  /**
   * This will represent the current largest available file descriptor number
   * The value of this variable will be incremented when the current available file descriptors are not enough
   * and needs to be increased
   * This variable starts at 3 because 0, 1, 2 usually represents standard in, out and err
   */
  private static long _maxFileDescriptor_  = 3;


  /*-------------------------  static methods  --------------------------*/


  private static void put(String n, IntrinsicCode c) { _intrinsics_.put(n, c); }
  private static void put(String n1, String n2, IntrinsicCode c) { put(n1, c); put(n2, c); }
  private static void put(String n1, String n2, String n3, IntrinsicCode c) { put(n1, c); put(n2, c); put(n3, c); }
  private static void put(String n1, String n2, String n3, String n4, IntrinsicCode c) { put(n1, c); put(n2, c); put(n3, c); put(n4, c); }


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
  public static Callable call(Interpreter interpreter, Clazz innerClazz)
  {
    if (PRECONDITIONS) require
      (innerClazz.feature().isIntrinsic());

    Callable result;
    var f = innerClazz.feature();
    String in = f.qualifiedName();   // == _fuir.clazzIntrinsicName(cl);
    // NYI: We must check the argument count in addition to the name!
    var ca = _intrinsics_.get(in);
    if (ca != null)
      {
        result = ca.get(interpreter, innerClazz);
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

  /**
   * Checks the file descriptors stack and expands it as necessary.
   *
   * @return the next available file descriptor.
   */
  private static synchronized long allocFileDescriptor()
  {
    if (_availableFileDescriptors_.empty())
      {
        _maxFileDescriptor_++;
        return _maxFileDescriptor_-1;
      }
    return _availableFileDescriptors_.pop();
  }

  /**
   * Checks the file descriptors stack and expands it as necessary.
   *
   * @param fileDescriptor the file descriptor to release.
   */
  private static synchronized void releaseFileDescriptor(long fileDescriptor)
  {
    _availableFileDescriptors_.push(fileDescriptor);
  }

  static
  {
    put("Type.name"            , (interpreter, innerClazz) -> args -> Interpreter.value(innerClazz._outer.typeName()));
    put("fuzion.sys.args.count", (interpreter, innerClazz) -> args -> new i32Value(Interpreter._options_.getBackendArgs().size() + 1));
    put("fuzion.sys.args.get"  , (interpreter, innerClazz) -> args ->
        {
          var i = args.get(1).i32Value();
          var fuir = interpreter._fuir;
          if (i == 0)
            {
              return  Interpreter.value(fuir.clazzAsString(fuir.mainClazzId()));
            }
          else
            {
              return  Interpreter.value(Interpreter._options_.getBackendArgs().get(i - 1));
            }
        });
    put("fuzion.sys.out.write", (interpreter, innerClazz) ->
        {
          var s = System.out;
          return args ->
            {
              s.writeBytes((byte[])args.get(1).arrayData()._array);
              return Value.EMPTY_VALUE;
            };
        });
    put("fuzion.sys.fileio.read", (interpreter, innerClazz)-> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          var byteArr = (byte[])args.get(2).arrayData()._array;
          try
            {
              int bytesRead = _openStreams_.get(args.get(1).i64Value()).read(byteArr);

              if (args.get(3).i32Value() != bytesRead)
                {
                  if (bytesRead == -1)
                    {
                      // no more data to read due to end of file
                      return new i64Value(0);
                    }
                }

              return new i64Value(bytesRead);
            }
          catch (Exception e)
            {
              return new i64Value(-1);
            }
        });
    put("fuzion.sys.fileio.write", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          byte[] fileContent = (byte[])args.get(2).arrayData()._array;
          try
            {
              _openStreams_.get(args.get(1).i64Value()).write(fileContent);
              return new i8Value(0);
            }
          catch (Exception e)
            {
              return new i8Value(-1);
            }
        });
    put("fuzion.sys.fileio.delete", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          Path path = Path.of(utf8ByteArrayDataToString(args.get(1)));
          try
            {
              boolean b = Files.deleteIfExists(path);
              return new boolValue(b);
            }
          catch (Exception e)
            {
              return new boolValue(false);
            }
        });
    put("fuzion.sys.fileio.move", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          Path oldPath = Path.of(utf8ByteArrayDataToString(args.get(1)));
          Path newPath = Path.of(utf8ByteArrayDataToString(args.get(2)));
          try
            {
              Files.move(oldPath, newPath);
              return new boolValue(true);
            }
          catch (Exception e)
            {
              return new boolValue(false);
            }
        });
    put("fuzion.sys.fileio.create_dir", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          Path path = Path.of(utf8ByteArrayDataToString(args.get(1)));
          try
            {
              Files.createDirectory(path);
              return new boolValue(true);
            }
          catch (Exception e)
            {
              return new boolValue(false);
            }
        });
    put("fuzion.sys.fileio.open", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              System.err.println("*** error: unsafe feature "+innerClazz+" disabled");
              System.exit(1);
            }
          var open_results = (long[])args.get(2).arrayData()._array;
          long fd;
          try
            {
              switch (args.get(3).i8Value()) {
                case 0:
                  RandomAccessFile fis = new RandomAccessFile(utf8ByteArrayDataToString(args.get(1)), "r");
                  fd = allocFileDescriptor();
                  _openStreams_.put(fd, fis);
                  open_results[0] = fd;
                  break;
                case 1:
                  RandomAccessFile fos = new RandomAccessFile(utf8ByteArrayDataToString(args.get(1)), "rw");
                  fd = allocFileDescriptor();
                  _openStreams_.put(fd, fos);
                  open_results[0] = fd;
                  break;
                case 2:
                  RandomAccessFile fas = new RandomAccessFile(utf8ByteArrayDataToString(args.get(1)), "rw");
                  fas.seek(fas.length());
                  fd = allocFileDescriptor();
                  _openStreams_.put(fd, fas);
                  open_results[0] = fd;
                  break;
                default:
                  open_results[1] = -1;
                  System.err.println("*** Unsupported open flag. Please use: 0 for READ, 1 for WRITE, 2 for APPEND. ***");
                  System.exit(1);
              }
            }
          catch (Exception e)
            {
              open_results[1] = -1;
            }
          return Value.EMPTY_VALUE;
        });
    put("fuzion.sys.fileio.close", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              System.err.println("*** error: unsafe feature "+innerClazz+" disabled");
              System.exit(1);
            }
          long fd = args.get(1).i64Value();
          try
            {
              if (_openStreams_.containsKey(fd))
                {
                  _openStreams_.remove(fd).close();
                  releaseFileDescriptor(fd);
                  return new i8Value(0);
                }
              return new i8Value(-1);
            }
          catch (Exception e)
            {
              return new i8Value(-1);
            }
        });
    put("fuzion.sys.fileio.stats",
        "fuzion.sys.fileio.lstats", // NYI : should be altered in the future to not resolve symbolic links
        (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              System.err.println("*** error: unsafe feature "+innerClazz+" disabled");
              System.exit(1);
            }
          Path path = Path.of(utf8ByteArrayDataToString(args.get(1)));
          long[] stats = (long[])args.get(2).arrayData()._array;
          try
            {
              BasicFileAttributes metadata = Files.readAttributes(path, BasicFileAttributes.class);
              stats[0] = metadata.size();
              stats[1] = metadata.lastModifiedTime().to(TimeUnit.SECONDS);
              stats[2] = metadata.isRegularFile()? 1:0;
              stats[3] = metadata.isDirectory()? 1:0;
              return new boolValue(true);
            }
          catch (Exception e)
            {
              return new boolValue(false);
            }
        });
    put("fuzion.sys.fileio.seek", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              System.err.println("*** error: unsafe feature "+innerClazz+" disabled");
              System.exit(1);
            }
          long fd = args.get(1).i64Value();
          var seekResults = (long[])args.get(3).arrayData()._array;
          try
            {
              _openStreams_.get(fd).seek(args.get(2).i16Value());
              seekResults[0] = _openStreams_.get(fd).getFilePointer();
              return Value.EMPTY_VALUE;
            }
          catch (Exception e)
            {
              seekResults[1] = -1;
              return Value.EMPTY_VALUE;
            }
        });
    put("fuzion.sys.fileio.file_position", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              System.err.println("*** error: unsafe feature "+innerClazz+" disabled");
              System.exit(1);
            }
          long fd = args.get(1).i64Value();
          long[] arr = (long[])args.get(2).arrayData()._array;
          try
            {
              arr[0] = _openStreams_.get(fd).getFilePointer();
              return Value.EMPTY_VALUE;
            }
          catch (Exception e)
            {
              arr[1] = -1;
              return Value.EMPTY_VALUE;
            }
        });
    put("fuzion.sys.err.write", (interpreter, innerClazz) ->
        {
          var s = System.err;
          return args ->
            {
              s.writeBytes((byte[])args.get(1).arrayData()._array);
              return Value.EMPTY_VALUE;
            };
        });
    put("fuzion.sys.stdin.next_byte", (interpreter, innerClazz) -> args ->
        {
          try
            {
              var nextByte = System.in.readNBytes(1);
              return nextByte.length == 0 ? new i32Value(-1) : new i32Value(Byte.toUnsignedInt(nextByte[0]));
            }
            catch (IOException e)
              {
                return new i32Value(-2);
              }
        });
    put("fuzion.sys.out.flush", (interpreter, innerClazz) ->
        {
          var s = System.out;
          return args ->
            {
              s.flush();
              return Value.EMPTY_VALUE;
            };
        });
    put("fuzion.sys.err.flush", (interpreter, innerClazz) ->
        {
          var s = System.err;
          return args ->
            {
              s.flush();
              return Value.EMPTY_VALUE;
            };
        });
    put("fuzion.std.exit", (interpreter, innerClazz) -> args ->
        {
          int rc = args.get(1).i32Value();
          System.exit(rc);
          return Value.EMPTY_VALUE;
        });
    put("fuzion.java.JavaObject.isNull", (interpreter, innerClazz) -> args ->
        {
          Instance thizI = (Instance) args.get(0);
          Object thiz  =  JavaInterface.instanceToJavaObject(thizI);
          return new boolValue(thiz == null);
        });
    put("fuzion.java.getStaticField0",
        "fuzion.java.getField0"      , (interpreter, innerClazz) ->
        {
          String in = innerClazz.feature().qualifiedName();   // == _fuir.clazzIntrinsicName(cl);
          var statique = in.equals("fuzion.java.getStaticField0");
          var actualGenerics = innerClazz._type.generics();
          if ((actualGenerics == null) || (actualGenerics.size() != 1))
            {
              Errors.fatal("fuzion.java.getStaticField called with wrong number of actual generic arguments");
            }
          Clazz resultClazz = innerClazz.actualClazz(actualGenerics.getFirst());
          return args ->
            {
              if (!ENABLE_UNSAFE_INTRINSICS)
                {
                  Errors.fatal("*** error: unsafe feature "+in+" disabled");
                }
              Instance clazzOrThizI = (Instance) args.get(1);
              Instance fieldI = (Instance) args.get(2);
              String clazz = !statique ? null : (String) JavaInterface.instanceToJavaObject(clazzOrThizI);
              Object thiz  = statique  ? null :          JavaInterface.instanceToJavaObject(clazzOrThizI);
              String field = (String) JavaInterface.instanceToJavaObject(fieldI);
              return JavaInterface.getField(clazz, thiz, field, resultClazz);
            };
        });
    put("fuzion.java.callV0",
        "fuzion.java.callS0",
        "fuzion.java.callC0", (interpreter, innerClazz) ->
        {
          String in = innerClazz.feature().qualifiedName();   // == _fuir.clazzIntrinsicName(cl);
          var virtual     = in.equals("fuzion.java.callV0");
          var statique    = in.equals("fuzion.java.callS0");
          var constructor = in.equals("fuzion.java.callC0");
          var actualGenerics = innerClazz._type.generics();
          Clazz resultClazz = innerClazz.actualClazz(actualGenerics.getFirst());
          return args ->
            {
              if (!ENABLE_UNSAFE_INTRINSICS)
                {
                  Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
                }
              int a = 1;
              var clNameI =                      (Instance) args.get(a++);
              var nameI   = constructor ? null : (Instance) args.get(a++);
              var sigI    =                      (Instance) args.get(a++);
              var thizI   = !virtual    ? null : (Instance) args.get(a++);

              var argz = args.get(a); // of type fuzion.sys.internal_array<JavaObject>, we need to get field argz.data
              var argfields = innerClazz.argumentFields();
              var argsArray = argfields[argfields.length - 1];
              var sac = argsArray.resultClazz();
              var argzData = Interpreter.getField(Types.resolved.f_fuzion_sys_array_data, sac, argz, false);

              String clName =                          (String) JavaInterface.instanceToJavaObject(clNameI);
              String name   = nameI   == null ? null : (String) JavaInterface.instanceToJavaObject(nameI  );
              String sig    =                          (String) JavaInterface.instanceToJavaObject(sigI   );
              Object thiz   = thizI   == null ? null :          JavaInterface.instanceToJavaObject(thizI  );
              return JavaInterface.call(clName, name, sig, thiz, argzData, resultClazz);
            };
        });
    put("fuzion.java.arrayLength",  (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          var arr = JavaInterface.instanceToJavaObject(args.get(1).instance());
          return new i32Value(Array.getLength(arr));
        });
    put("fuzion.java.arrayGet", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          var arr = JavaInterface.instanceToJavaObject(args.get(1).instance());
          var ix  = args.get(2).i32Value();
          var res = Array.get(arr, ix);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(res, resultClazz);
        });
    put("fuzion.java.arrayToJavaObject0", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          var arrA = args.get(1).arrayData();
          var res = arrA._array;
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(res, resultClazz);
        });
    put("fuzion.java.stringToJavaObject0", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          var str = utf8ByteArrayDataToString(args.get(1));
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(str, resultClazz);
        });
    put("fuzion.java.javaStringToString", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          var javaString = (String) JavaInterface.instanceToJavaObject(args.get(1).instance());
          return Interpreter.value(javaString == null ? "--null--" : javaString);
        });
    put("fuzion.java.i8ToJavaObject", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          var b = args.get(1).i8Value();
          var jb = Byte.valueOf((byte) b);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(jb, resultClazz);
        });
    put("fuzion.java.u16ToJavaObject", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          var c = args.get(1).u16Value();
          var jc = Character.valueOf((char) c);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(jc, resultClazz);
        });
    put("fuzion.java.i16ToJavaObject", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          var s = args.get(1).i16Value();
          var js = Short.valueOf((short) s);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(js, resultClazz);
        });
    put("fuzion.java.i32ToJavaObject", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          var i = args.get(1).i32Value();
          var ji = Integer.valueOf(i);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(ji, resultClazz);
        });
    put("fuzion.java.i64ToJavaObject", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          var l = args.get(1).i64Value();
          var jl = Long.valueOf(l);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(jl, resultClazz);
        });
    put("fuzion.java.f32ToJavaObject", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          var f32 = args.get(1).f32Value();
          var jf = Float.valueOf(f32);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(jf, resultClazz);
        });
    put("fuzion.java.f64ToJavaObject", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          var d = args.get(1).f64Value();
          var jd = Double.valueOf(d);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(jd, resultClazz);
        });
    put("fuzion.java.boolToJavaObject", (interpreter, innerClazz) -> args ->
        {
          if (!ENABLE_UNSAFE_INTRINSICS)
            {
              Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
            }
          var b = args.get(1).boolValue();
          var jb = Boolean.valueOf(b);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(jb, resultClazz);
        });
    put("fuzion.sys.internal_array.alloc", (interpreter, innerClazz) -> args ->
        {
          return fuzionSysArrayAlloc(/* size */ args.get(1).i32Value(),
                                     /* type */ innerClazz._outer);
        });
    put("fuzion.sys.internal_array.get", (interpreter, innerClazz) -> args ->
        {
          return fuzionSysArrayGet(/* data  */ ((ArrayData)args.get(1)),
                                   /* index */ args.get(2).i32Value(),
                                   /* type  */ innerClazz._outer);
        });
    put("fuzion.sys.internal_array.setel", (interpreter, innerClazz) -> args ->
        {
          fuzionSysArraySetEl(/* data  */ ((ArrayData)args.get(1)),
                              /* index */ args.get(2).i32Value(),
                              /* value */ args.get(3),
                              /* type  */ innerClazz._outer);
          return Value.EMPTY_VALUE;
        });
    put("fuzion.sys.env_vars.has0", (interpreter, innerClazz) -> args -> new boolValue(System.getenv(utf8ByteArrayDataToString(args.get(1))) != null));
    put("fuzion.sys.env_vars.get0", (interpreter, innerClazz) -> args -> Interpreter.value(System.getenv(utf8ByteArrayDataToString(args.get(1)))));
    put("fuzion.sys.thread.spawn0", (interpreter, innerClazz) -> args ->
        {
          var call = Types.resolved.f_function_call;
          var oc = innerClazz.argumentFields()[0].resultClazz();
          var ic = oc.lookup(call);
          var al = new ArrayList<Value>();
          al.add(args.get(1));
          var t = new Thread(() -> interpreter.callOnInstance(ic.feature(), ic, new Instance(ic), al));
          t.setDaemon(true);
          t.start();
          return new Instance(Clazzes.c_unit.get());
        });
    put("safety"                , (interpreter, innerClazz) -> args -> new boolValue(Interpreter._options_.fuzionSafety()));
    put("debug"                 , (interpreter, innerClazz) -> args -> new boolValue(Interpreter._options_.fuzionDebug()));
    put("debugLevel"            , (interpreter, innerClazz) -> args -> new i32Value(Interpreter._options_.fuzionDebugLevel()));
    put("i8.as_i32"             , (interpreter, innerClazz) -> args -> new i32Value (              (                           args.get(0).i8Value() )));
    put("i8.castTo_u8"          , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & (                           args.get(0).i8Value() )));
    put("i8.prefix -°"          , (interpreter, innerClazz) -> args -> new i8Value  ((int) (byte)  (                       -   args.get(0).i8Value() )));
    put("i8.infix +°"           , (interpreter, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  +   args.get(1).i8Value() )));
    put("i8.infix -°"           , (interpreter, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  -   args.get(1).i8Value() )));
    put("i8.infix *°"           , (interpreter, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  *   args.get(1).i8Value() )));
    put("i8.div"                , (interpreter, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  /   args.get(1).i8Value() )));
    put("i8.mod"                , (interpreter, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  %   args.get(1).i8Value() )));
    put("i8.infix &"            , (interpreter, innerClazz) -> args -> new i8Value  (              (args.get(0).i8Value()  &   args.get(1).i8Value() )));
    put("i8.infix |"            , (interpreter, innerClazz) -> args -> new i8Value  (              (args.get(0).i8Value()  |   args.get(1).i8Value() )));
    put("i8.infix ^"            , (interpreter, innerClazz) -> args -> new i8Value  (              (args.get(0).i8Value()  ^   args.get(1).i8Value() )));
    put("i8.infix >>"           , (interpreter, innerClazz) -> args -> new i8Value  (              (args.get(0).i8Value()  >>  args.get(1).i8Value() )));
    put("i8.infix <<"           , (interpreter, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  <<  args.get(1).i8Value() )));
    put("i8.infix =="           , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i8Value()  ==  args.get(1).i8Value() )));
    put("i8.type.equality"      , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).i8Value()  ==  args.get(2).i8Value() )));
    put("i8.infix !="           , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i8Value()  !=  args.get(1).i8Value() )));
    put("i8.infix <"            , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i8Value()  <   args.get(1).i8Value() )));
    put("i8.infix >"            , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i8Value()  >   args.get(1).i8Value() )));
    put("i8.infix <="           , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i8Value()  <=  args.get(1).i8Value() )));
    put("i8.infix >="           , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i8Value()  >=  args.get(1).i8Value() )));
    put("i16.as_i32"            , (interpreter, innerClazz) -> args -> new i32Value (              (                           args.get(0).i16Value())));
    put("i16.castTo_u16"        , (interpreter, innerClazz) -> args -> new u16Value (     0xffff & (                           args.get(0).i16Value())));
    put("i16.prefix -°"         , (interpreter, innerClazz) -> args -> new i16Value ((int) (short) (                       -   args.get(0).i16Value())));
    put("i16.infix +°"          , (interpreter, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() +   args.get(1).i16Value())));
    put("i16.infix -°"          , (interpreter, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() -   args.get(1).i16Value())));
    put("i16.infix *°"          , (interpreter, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() *   args.get(1).i16Value())));
    put("i16.div"               , (interpreter, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() /   args.get(1).i16Value())));
    put("i16.mod"               , (interpreter, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() %   args.get(1).i16Value())));
    put("i16.infix &"           , (interpreter, innerClazz) -> args -> new i16Value (              (args.get(0).i16Value() &   args.get(1).i16Value())));
    put("i16.infix |"           , (interpreter, innerClazz) -> args -> new i16Value (              (args.get(0).i16Value() |   args.get(1).i16Value())));
    put("i16.infix ^"           , (interpreter, innerClazz) -> args -> new i16Value (              (args.get(0).i16Value() ^   args.get(1).i16Value())));
    put("i16.infix >>"          , (interpreter, innerClazz) -> args -> new i16Value (              (args.get(0).i16Value() >>  args.get(1).i16Value())));
    put("i16.infix <<"          , (interpreter, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() <<  args.get(1).i16Value())));
    put("i16.infix =="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i16Value() ==  args.get(1).i16Value())));
    put("i16.type.equality"     , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).i16Value() ==  args.get(2).i16Value())));
    put("i16.infix !="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i16Value() !=  args.get(1).i16Value())));
    put("i16.infix <"           , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i16Value() <   args.get(1).i16Value())));
    put("i16.infix >"           , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i16Value() >   args.get(1).i16Value())));
    put("i16.infix <="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i16Value() <=  args.get(1).i16Value())));
    put("i16.infix >="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i16Value() >=  args.get(1).i16Value())));
    put("i32.as_i64"            , (interpreter, innerClazz) -> args -> new i64Value ((long)        (                           args.get(0).i32Value())));
    put("i32.castTo_u32"        , (interpreter, innerClazz) -> args -> new u32Value (              (                           args.get(0).i32Value())));
    put("i32.as_f64"            , (interpreter, innerClazz) -> args -> new f64Value ((double)      (                           args.get(0).i32Value())));
    put("i32.prefix -°"         , (interpreter, innerClazz) -> args -> new i32Value (              (                       -   args.get(0).i32Value())));
    put("i32.infix +°"          , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() +   args.get(1).i32Value())));
    put("i32.infix -°"          , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() -   args.get(1).i32Value())));
    put("i32.infix *°"          , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() *   args.get(1).i32Value())));
    put("i32.div"               , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() /   args.get(1).i32Value())));
    put("i32.mod"               , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() %   args.get(1).i32Value())));
    put("i32.infix &"           , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() &   args.get(1).i32Value())));
    put("i32.infix |"           , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() |   args.get(1).i32Value())));
    put("i32.infix ^"           , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() ^   args.get(1).i32Value())));
    put("i32.infix >>"          , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() >>  args.get(1).i32Value())));
    put("i32.infix <<"          , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() <<  args.get(1).i32Value())));
    put("i32.infix =="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i32Value() ==  args.get(1).i32Value())));
    put("i32.type.equality"     , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).i32Value() ==  args.get(2).i32Value())));
    put("i32.infix !="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i32Value() !=  args.get(1).i32Value())));
    put("i32.infix <"           , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i32Value() <   args.get(1).i32Value())));
    put("i32.infix >"           , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i32Value() >   args.get(1).i32Value())));
    put("i32.infix <="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i32Value() <=  args.get(1).i32Value())));
    put("i32.infix >="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i32Value() >=  args.get(1).i32Value())));
    put("i64.castTo_u64"        , (interpreter, innerClazz) -> args -> new u64Value (              (                           args.get(0).i64Value())));
    put("i64.as_f64"            , (interpreter, innerClazz) -> args -> new f64Value ((double)      (                           args.get(0).i64Value())));
    put("i64.prefix -°"         , (interpreter, innerClazz) -> args -> new i64Value (              (                       -   args.get(0).u64Value())));
    put("i64.infix +°"          , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() +   args.get(1).i64Value())));
    put("i64.infix -°"          , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() -   args.get(1).i64Value())));
    put("i64.infix *°"          , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() *   args.get(1).i64Value())));
    put("i64.div"               , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() /   args.get(1).i64Value())));
    put("i64.mod"               , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() %   args.get(1).i64Value())));
    put("i64.infix &"           , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() &   args.get(1).i64Value())));
    put("i64.infix |"           , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() |   args.get(1).i64Value())));
    put("i64.infix ^"           , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() ^   args.get(1).i64Value())));
    put("i64.infix >>"          , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() >>  args.get(1).i64Value())));
    put("i64.infix <<"          , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() <<  args.get(1).i64Value())));
    put("i64.infix =="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i64Value() ==  args.get(1).i64Value())));
    put("i64.type.equality"     , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).i64Value() ==  args.get(2).i64Value())));
    put("i64.infix !="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i64Value() !=  args.get(1).i64Value())));
    put("i64.infix <"           , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i64Value() <   args.get(1).i64Value())));
    put("i64.infix >"           , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i64Value() >   args.get(1).i64Value())));
    put("i64.infix <="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i64Value() <=  args.get(1).i64Value())));
    put("i64.infix >="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).i64Value() >=  args.get(1).i64Value())));
    put("u8.as_i32"             , (interpreter, innerClazz) -> args -> new i32Value (              (                           args.get(0).u8Value() )));
    put("u8.castTo_i8"          , (interpreter, innerClazz) -> args -> new i8Value  ((int) (byte)  (                           args.get(0).u8Value() )));
    put("u8.prefix -°"          , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & (                       -   args.get(0).u8Value() )));
    put("u8.infix +°"           , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & (args.get(0).u8Value()  +   args.get(1).u8Value() )));
    put("u8.infix -°"           , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & (args.get(0).u8Value()  -   args.get(1).u8Value() )));
    put("u8.infix *°"           , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & (args.get(0).u8Value()  *   args.get(1).u8Value() )));
    put("u8.div"                , (interpreter, innerClazz) -> args -> new u8Value  (Integer.divideUnsigned   (args.get(0).u8Value(), args.get(1).u8Value())));
    put("u8.mod"                , (interpreter, innerClazz) -> args -> new u8Value  (Integer.remainderUnsigned(args.get(0).u8Value(), args.get(1).u8Value())));
    put("u8.infix &"            , (interpreter, innerClazz) -> args -> new u8Value  (              (args.get(0).u8Value()  &   args.get(1).u8Value() )));
    put("u8.infix |"            , (interpreter, innerClazz) -> args -> new u8Value  (              (args.get(0).u8Value()  |   args.get(1).u8Value() )));
    put("u8.infix ^"            , (interpreter, innerClazz) -> args -> new u8Value  (              (args.get(0).u8Value()  ^   args.get(1).u8Value() )));
    put("u8.infix >>"           , (interpreter, innerClazz) -> args -> new u8Value  (              (args.get(0).u8Value()  >>> args.get(1).u8Value() )));
    put("u8.infix <<"           , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & (args.get(0).u8Value()  <<  args.get(1).u8Value() )));
    put("u8.infix =="           , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).u8Value()  ==  args.get(1).u8Value() )));
    put("u8.type.equality"      , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).u8Value()  ==  args.get(2).u8Value() )));
    put("u8.infix !="           , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).u8Value()  !=  args.get(1).u8Value() )));
    put("u8.infix <"            , (interpreter, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(0).u8Value(), args.get(1).u8Value()) <  0));
    put("u8.infix >"            , (interpreter, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(0).u8Value(), args.get(1).u8Value()) >  0));
    put("u8.infix <="           , (interpreter, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(0).u8Value(), args.get(1).u8Value()) <= 0));
    put("u8.infix >="           , (interpreter, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(0).u8Value(), args.get(1).u8Value()) >= 0));
    put("u16.as_i32"            , (interpreter, innerClazz) -> args -> new i32Value (              (                           args.get(0).u16Value())));
    put("u16.low8bits"          , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & (                           args.get(0).u16Value())));
    put("u16.castTo_i16"        , (interpreter, innerClazz) -> args -> new i16Value ((short)       (                           args.get(0).u16Value())));
    put("u16.prefix -°"         , (interpreter, innerClazz) -> args -> new u16Value (     0xffff & (                       -   args.get(0).u16Value())));
    put("u16.infix +°"          , (interpreter, innerClazz) -> args -> new u16Value (     0xffff & (args.get(0).u16Value() +   args.get(1).u16Value())));
    put("u16.infix -°"          , (interpreter, innerClazz) -> args -> new u16Value (     0xffff & (args.get(0).u16Value() -   args.get(1).u16Value())));
    put("u16.infix *°"          , (interpreter, innerClazz) -> args -> new u16Value (     0xffff & (args.get(0).u16Value() *   args.get(1).u16Value())));
    put("u16.div"               , (interpreter, innerClazz) -> args -> new u16Value (Integer.divideUnsigned   (args.get(0).u16Value(), args.get(1).u16Value())));
    put("u16.mod"               , (interpreter, innerClazz) -> args -> new u16Value (Integer.remainderUnsigned(args.get(0).u16Value(), args.get(1).u16Value())));
    put("u16.infix &"           , (interpreter, innerClazz) -> args -> new u16Value (              (args.get(0).u16Value() &   args.get(1).u16Value())));
    put("u16.infix |"           , (interpreter, innerClazz) -> args -> new u16Value (              (args.get(0).u16Value() |   args.get(1).u16Value())));
    put("u16.infix ^"           , (interpreter, innerClazz) -> args -> new u16Value (              (args.get(0).u16Value() ^   args.get(1).u16Value())));
    put("u16.infix >>"          , (interpreter, innerClazz) -> args -> new u16Value (              (args.get(0).u16Value() >>> args.get(1).u16Value())));
    put("u16.infix <<"          , (interpreter, innerClazz) -> args -> new u16Value (     0xffff & (args.get(0).u16Value() <<  args.get(1).u16Value())));
    put("u16.infix =="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).u16Value() ==  args.get(1).u16Value())));
    put("u16.type.equality"     , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).u16Value() ==  args.get(2).u16Value())));
    put("u16.infix !="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).u16Value() !=  args.get(1).u16Value())));
    put("u16.infix <"           , (interpreter, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(0).u16Value(), args.get(1).u16Value()) <  0));
    put("u16.infix >"           , (interpreter, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(0).u16Value(), args.get(1).u16Value()) >  0));
    put("u16.infix <="          , (interpreter, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(0).u16Value(), args.get(1).u16Value()) <= 0));
    put("u16.infix >="          , (interpreter, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(0).u16Value(), args.get(1).u16Value()) >= 0));
    put("u32.as_i64"            , (interpreter, innerClazz) -> args -> new i64Value (Integer.toUnsignedLong(args.get(0).u32Value())));
    put("u32.low8bits"          , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & (                           args.get(0).u32Value())));
    put("u32.low16bits"         , (interpreter, innerClazz) -> args -> new u16Value (     0xffff & (                           args.get(0).u32Value())));
    put("u32.castTo_i32"        , (interpreter, innerClazz) -> args -> new i32Value (              (                           args.get(0).u32Value())));
    put("u32.as_f64"            , (interpreter, innerClazz) -> args -> new f64Value ((double)      Integer.toUnsignedLong(     args.get(0).u32Value())));
    put("u32.castTo_f32"        , (interpreter, innerClazz) -> args -> new f32Value (              Float.intBitsToFloat(       args.get(0).u32Value())));
    put("u32.prefix -°"         , (interpreter, innerClazz) -> args -> new u32Value (              (                       -   args.get(0).u32Value())));
    put("u32.infix +°"          , (interpreter, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() +   args.get(1).u32Value())));
    put("u32.infix -°"          , (interpreter, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() -   args.get(1).u32Value())));
    put("u32.infix *°"          , (interpreter, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() *   args.get(1).u32Value())));
    put("u32.div"               , (interpreter, innerClazz) -> args -> new u32Value (Integer.divideUnsigned   (args.get(0).u32Value(), args.get(1).u32Value())));
    put("u32.mod"               , (interpreter, innerClazz) -> args -> new u32Value (Integer.remainderUnsigned(args.get(0).u32Value(), args.get(1).u32Value())));
    put("u32.infix &"           , (interpreter, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() &   args.get(1).u32Value())));
    put("u32.infix |"           , (interpreter, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() |   args.get(1).u32Value())));
    put("u32.infix ^"           , (interpreter, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() ^   args.get(1).u32Value())));
    put("u32.infix >>"          , (interpreter, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() >>> args.get(1).u32Value())));
    put("u32.infix <<"          , (interpreter, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() <<  args.get(1).u32Value())));
    put("u32.infix =="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).u32Value() ==  args.get(1).u32Value())));
    put("u32.type.equality"     , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).u32Value() ==  args.get(2).u32Value())));
    put("u32.infix !="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).u32Value() !=  args.get(1).u32Value())));
    put("u32.infix <"           , (interpreter, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(0).u32Value(), args.get(1).u32Value()) <  0));
    put("u32.infix >"           , (interpreter, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(0).u32Value(), args.get(1).u32Value()) >  0));
    put("u32.infix <="          , (interpreter, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(0).u32Value(), args.get(1).u32Value()) <= 0));
    put("u32.infix >="          , (interpreter, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(0).u32Value(), args.get(1).u32Value()) >= 0));
    put("u64.low8bits"          , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & ((int)                      args.get(0).u64Value())));
    put("u64.low16bits"         , (interpreter, innerClazz) -> args -> new u16Value (     0xffff & ((int)                      args.get(0).u64Value())));
    put("u64.low32bits"         , (interpreter, innerClazz) -> args -> new u32Value ((int)         (                           args.get(0).u64Value())));
    put("u64.castTo_i64"        , (interpreter, innerClazz) -> args -> new i64Value (              (                           args.get(0).u64Value())));
    put("u64.as_f64"            , (interpreter, innerClazz) -> args -> new f64Value (Double.parseDouble(Long.toUnsignedString(args.get(0).u64Value()))));
    put("u64.castTo_f64"        , (interpreter, innerClazz) -> args -> new f64Value (              Double.longBitsToDouble(    args.get(0).u64Value())));
    put("u64.prefix -°"         , (interpreter, innerClazz) -> args -> new u64Value (              (                       -   args.get(0).u64Value())));
    put("u64.infix +°"          , (interpreter, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() +   args.get(1).u64Value())));
    put("u64.infix -°"          , (interpreter, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() -   args.get(1).u64Value())));
    put("u64.infix *°"          , (interpreter, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() *   args.get(1).u64Value())));
    put("u64.div"               , (interpreter, innerClazz) -> args -> new u64Value (Long.divideUnsigned   (args.get(0).u64Value(), args.get(1).u64Value())));
    put("u64.mod"               , (interpreter, innerClazz) -> args -> new u64Value (Long.remainderUnsigned(args.get(0).u64Value(), args.get(1).u64Value())));
    put("u64.infix &"           , (interpreter, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() &   args.get(1).u64Value())));
    put("u64.infix |"           , (interpreter, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() |   args.get(1).u64Value())));
    put("u64.infix ^"           , (interpreter, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() ^   args.get(1).u64Value())));
    put("u64.infix >>"          , (interpreter, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() >>> args.get(1).u64Value())));
    put("u64.infix <<"          , (interpreter, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() <<  args.get(1).u64Value())));
    put("u64.infix =="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).u64Value() ==  args.get(1).u64Value())));
    put("u64.type.equality"     , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).u64Value() ==  args.get(2).u64Value())));
    put("u64.infix !="          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(0).u64Value() !=  args.get(1).u64Value())));
    put("u64.infix <"           , (interpreter, innerClazz) -> args -> new boolValue(Long.compareUnsigned(args.get(0).u64Value(), args.get(1).u64Value()) <  0));
    put("u64.infix >"           , (interpreter, innerClazz) -> args -> new boolValue(Long.compareUnsigned(args.get(0).u64Value(), args.get(1).u64Value()) >  0));
    put("u64.infix <="          , (interpreter, innerClazz) -> args -> new boolValue(Long.compareUnsigned(args.get(0).u64Value(), args.get(1).u64Value()) <= 0));
    put("u64.infix >="          , (interpreter, innerClazz) -> args -> new boolValue(Long.compareUnsigned(args.get(0).u64Value(), args.get(1).u64Value()) >= 0));
    put("f32.prefix -"          , (interpreter, innerClazz) -> args -> new f32Value (                (                       -  args.get(0).f32Value())));
    put("f32.infix +"           , (interpreter, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() +  args.get(1).f32Value())));
    put("f32.infix -"           , (interpreter, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() -  args.get(1).f32Value())));
    put("f32.infix *"           , (interpreter, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() *  args.get(1).f32Value())));
    put("f32.infix /"           , (interpreter, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() /  args.get(1).f32Value())));
    put("f32.infix %"           , (interpreter, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() %  args.get(1).f32Value())));
    put("f32.infix **"          , (interpreter, innerClazz) -> args -> new f32Value ((float) Math.pow(args.get(0).f32Value(),   args.get(1).f32Value())));
    put("f32.infix =="          , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f32Value() == args.get(1).f32Value())));
    put("f32.type.equality"     , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(1).f32Value() == args.get(2).f32Value())));
    put("f32.infix !="          , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f32Value() != args.get(1).f32Value())));
    put("f32.infix <"           , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f32Value() <  args.get(1).f32Value())));
    put("f32.infix <="          , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f32Value() <= args.get(1).f32Value())));
    put("f32.infix >"           , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f32Value() >  args.get(1).f32Value())));
    put("f32.infix >="          , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f32Value() >= args.get(1).f32Value())));
    put("f32.as_f64"            , (interpreter, innerClazz) -> args -> new f64Value((double)                                    args.get(0).f32Value() ));
    put("f32.castTo_u32"        , (interpreter, innerClazz) -> args -> new u32Value (    Float.floatToIntBits(                  args.get(0).f32Value())));
    put("f32.as_string"         , (interpreter, innerClazz) -> args -> Interpreter.value(Float.toString      (                  args.get(0).f32Value())));
    put("f64.prefix -"          , (interpreter, innerClazz) -> args -> new f64Value (                (                       -  args.get(0).f64Value())));
    put("f64.infix +"           , (interpreter, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() +  args.get(1).f64Value())));
    put("f64.infix -"           , (interpreter, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() -  args.get(1).f64Value())));
    put("f64.infix *"           , (interpreter, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() *  args.get(1).f64Value())));
    put("f64.infix /"           , (interpreter, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() /  args.get(1).f64Value())));
    put("f64.infix %"           , (interpreter, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() %  args.get(1).f64Value())));
    put("f64.infix **"          , (interpreter, innerClazz) -> args -> new f64Value (        Math.pow(args.get(0).f64Value(),   args.get(1).f64Value())));
    put("f64.infix =="          , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f64Value() == args.get(1).f64Value())));
    put("f64.type.equality"     , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(1).f64Value() == args.get(2).f64Value())));
    put("f64.infix !="          , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f64Value() != args.get(1).f64Value())));
    put("f64.infix <"           , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f64Value() <  args.get(1).f64Value())));
    put("f64.infix <="          , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f64Value() <= args.get(1).f64Value())));
    put("f64.infix >"           , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f64Value() >  args.get(1).f64Value())));
    put("f64.infix >="          , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f64Value() >= args.get(1).f64Value())));
    put("f64.as_i64_lax"        , (interpreter, innerClazz) -> args -> new i64Value((long)                                      args.get(0).f64Value() ));
    put("f64.as_f32"            , (interpreter, innerClazz) -> args -> new f32Value((float)                                     args.get(0).f64Value() ));
    put("f64.castTo_u64"        , (interpreter, innerClazz) -> args -> new u64Value (    Double.doubleToLongBits(               args.get(0).f64Value())));
    put("f64.as_string"         , (interpreter, innerClazz) -> args -> Interpreter.value(Double.toString       (                args.get(0).f64Value())));
    put("f32s.isNaN"            , (interpreter, innerClazz) -> args -> new boolValue(                               Float.isNaN(args.get(1).f32Value())));
    put("f64s.isNaN"            , (interpreter, innerClazz) -> args -> new boolValue(                              Double.isNaN(args.get(1).f64Value())));
    put("f32s.acos"             , (interpreter, innerClazz) -> args -> new f32Value ((float)           Math.acos(               args.get(1).f32Value())));
    put("f32s.asin"             , (interpreter, innerClazz) -> args -> new f32Value ((float)           Math.asin(               args.get(1).f32Value())));
    put("f32s.atan"             , (interpreter, innerClazz) -> args -> new f32Value ((float)           Math.atan(               args.get(1).f32Value())));
    put("f32s.atan2"            , (interpreter, innerClazz) -> args -> new f32Value ((float)  Math.atan2(args.get(1).f32Value(),args.get(2).f32Value())));
    put("f32s.cos"              , (interpreter, innerClazz) -> args -> new f32Value ((float)           Math.cos(                args.get(1).f32Value())));
    put("f32s.cosh"             , (interpreter, innerClazz) -> args -> new f32Value ((float)           Math.cosh(               args.get(1).f32Value())));
    put("f32s.epsilon"          , (interpreter, innerClazz) -> args -> new f32Value (                  Math.ulp(                (float)1)));
    put("f32s.exp"              , (interpreter, innerClazz) -> args -> new f32Value ((float)           Math.exp(                args.get(1).f32Value())));
    put("f32s.log"              , (interpreter, innerClazz) -> args -> new f32Value ((float)           Math.log(                args.get(1).f32Value())));
    put("f32s.max"              , (interpreter, innerClazz) -> args -> new f32Value (                                           Float.MAX_VALUE));
    put("f32s.maxExp"           , (interpreter, innerClazz) -> args -> new i32Value (                                           Float.MAX_EXPONENT));
    put("f32s.minPositive"      , (interpreter, innerClazz) -> args -> new f32Value (                                           Float.MIN_NORMAL));
    put("f32s.minExp"           , (interpreter, innerClazz) -> args -> new i32Value (                                           Float.MIN_EXPONENT));
    put("f32s.sin"              , (interpreter, innerClazz) -> args -> new f32Value ((float)          Math.sin(                 args.get(1).f32Value())));
    put("f32s.sinh"             , (interpreter, innerClazz) -> args -> new f32Value ((float)          Math.sinh(                args.get(1).f32Value())));
    put("f32s.squareRoot"       , (interpreter, innerClazz) -> args -> new f32Value ((float)          Math.sqrt(        (double)args.get(1).f32Value())));
    put("f32s.tan"              , (interpreter, innerClazz) -> args -> new f32Value ((float)          Math.tan(                 args.get(1).f32Value())));
    put("f32s.tanh"             , (interpreter, innerClazz) -> args -> new f32Value ((float)          Math.tan(                 args.get(1).f32Value())));
    put("f64s.acos"             , (interpreter, innerClazz) -> args -> new f64Value (                 Math.acos(                args.get(1).f64Value())));
    put("f64s.asin"             , (interpreter, innerClazz) -> args -> new f64Value (                 Math.asin(                args.get(1).f64Value())));
    put("f64s.atan"             , (interpreter, innerClazz) -> args -> new f64Value (                 Math.atan(                args.get(1).f64Value())));
    put("f64s.atan2"            , (interpreter, innerClazz) -> args -> new f64Value (         Math.atan2(args.get(1).f64Value(),args.get(2).f64Value())));
    put("f64s.cos"              , (interpreter, innerClazz) -> args -> new f64Value (                 Math.cos(                 args.get(1).f64Value())));
    put("f64s.cosh"             , (interpreter, innerClazz) -> args -> new f64Value (                 Math.cosh(                args.get(1).f64Value())));
    put("f64s.epsilon"          , (interpreter, innerClazz) -> args -> new f64Value (                 Math.ulp(                 (double)1)));
    put("f64s.exp"              , (interpreter, innerClazz) -> args -> new f64Value (                 Math.exp(                 args.get(1).f64Value())));
    put("f64s.log"              , (interpreter, innerClazz) -> args -> new f64Value (                 Math.log(                 args.get(1).f64Value())));
    put("f64s.max"              , (interpreter, innerClazz) -> args -> new f64Value (                                               Double.MAX_VALUE));
    put("f64s.maxExp"           , (interpreter, innerClazz) -> args -> new i32Value (                                               Double.MAX_EXPONENT));
    put("f64s.minPositive"      , (interpreter, innerClazz) -> args -> new f64Value (                                               Double.MIN_NORMAL));
    put("f64s.minExp"           , (interpreter, innerClazz) -> args -> new i32Value (                                               Double.MIN_EXPONENT));
    put("f64s.sin"              , (interpreter, innerClazz) -> args -> new f64Value (                 Math.sin(                 args.get(1).f64Value())));
    put("f64s.sinh"             , (interpreter, innerClazz) -> args -> new f64Value (                 Math.sinh(                args.get(1).f64Value())));
    put("f64s.squareRoot"       , (interpreter, innerClazz) -> args -> new f64Value (                 Math.sqrt(                args.get(1).f64Value())));
    put("f64s.tan"              , (interpreter, innerClazz) -> args -> new f64Value (                 Math.tan(                 args.get(1).f64Value())));
    put("f64s.tanh"             , (interpreter, innerClazz) -> args -> new f64Value (                 Math.tan(                 args.get(1).f64Value())));
    put("Any.hashCode"          , (interpreter, innerClazz) -> args -> new i32Value (args.get(0).toString().hashCode()));
    put("Any.as_string"         , (interpreter, innerClazz) -> args -> Interpreter.value("instance[" + innerClazz._outer.toString() + "]"));
    put("fuzion.std.nano_time"  , (interpreter, innerClazz) -> args -> new u64Value (System.nanoTime()));
    put("fuzion.std.nano_sleep" , (interpreter, innerClazz) -> args ->
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
        });
    put("effect.replace"  ,
        "effect.default"  ,
        "effect.abortable",
        "effect.abort"    , (interpreter, innerClazz) -> effect(interpreter, innerClazz));
    put("effects.exists"  , (interpreter, innerClazz) -> args ->
        {
          var cl = innerClazz.actualGenerics()[0];
          return new boolValue(FuzionThread.current()._effects.get(cl) != null /* NOTE not containsKey since cl may map to null! */ );
        });
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
   * @param innerClazz the frame clazz of the called feature
   *
   * @return a Callable instance to execute the intrinsic call.
   */
  static Callable effect(Interpreter interpreter, Clazz innerClazz)
  {
    return (args) ->
      {
        var m = args.get(0);
        var cl = innerClazz._outer;
        String in = innerClazz.feature().qualifiedName();   // == _fuir.clazzIntrinsicName(cl);
        switch (in)
          {
          case "effect.replace": check(FuzionThread.current()._effects.get(cl) != null); FuzionThread.current()._effects.put(cl, m   );   break;
          case "effect.default": if (FuzionThread.current()._effects.get(cl) == null) {  FuzionThread.current()._effects.put(cl, m   ); } break;
          case "effect.abortable" :
            {
              var prev = FuzionThread.current()._effects.get(cl);
              FuzionThread.current()._effects.put(cl, m);
              var call = Types.resolved.f_function_call;
              var oc = innerClazz.actualGenerics()[0]; //innerClazz.argumentFields()[0].resultClazz();
              var ic = oc.lookup(call);
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
          default: throw new Error("unexected effect intrinsic '"+innerClazz+"'");
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
