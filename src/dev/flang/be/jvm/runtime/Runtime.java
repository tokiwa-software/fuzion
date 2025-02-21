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
 * Source of class Runtime
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.ValueLayout.OfBoolean;
import java.lang.foreign.ValueLayout.OfByte;
import java.lang.foreign.ValueLayout.OfChar;
import java.lang.foreign.ValueLayout.OfDouble;
import java.lang.foreign.ValueLayout.OfFloat;
import java.lang.foreign.ValueLayout.OfInt;
import java.lang.foreign.ValueLayout.OfLong;
import java.lang.foreign.ValueLayout.OfShort;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.JavaInterface;
import dev.flang.util.Pair;
import dev.flang.util.StringHelpers;


/**
 * Runtime provides the runtime system for the JVM backend.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
@SuppressWarnings({"rawtypes"})
public class Runtime extends ANY
{

  /*-----------------------------  classes  -----------------------------*/


  /**
   * Exception that is thrown by effect.abort
   */
  public static class Abort extends Error
  {

    public final int _effect;

    /**
     * @param effect the id of the effect that is aborted.
     */
    Abort(int effect)
    {
      super();
      this._effect = effect;
    }

  }

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
   * Copy of dev.flang.be.jvm.Names.* without adding a dependency on
   * that package.  We do not want to bundle the backend classes with a
   * stand-alone application that needs the runtime classes, so this is copied.
   */
  public static final String ROUTINE_NAME      = "fzRoutine";
  public static final String CLASS_PREFIX      = "fzC_";
  public static final String CLASS_PREFIX_WITH_ID = "fzC__L";


  /**
   * Result value used when returning from a call into Java code in case an
   * exception was thrown by that code.
   */
  public static final JavaError _JAVA_ERROR_ = new JavaError();


  public static final String CLASS_NAME_TO_FUZION_CLAZZ_NAME = "CLASS_NAME_TO_FUZION_CLAZZ_NAME.txt";


  /**
   * Value used for {@code FuzionThread.effect_store} and {@code FuzionThread.effect_load}
   * to distinguish a unit value effect from a not existing effect
   */
  public static final AnyI _UNIT_TYPE_EFFECT_ = new AnyI() { };


  /*--------------------------  static fields  --------------------------*/


  /**
   * Flag to disallow intrinsics that would permit to take over the world via
   * file or network access, system function calls etc.
   */
  private static boolean _enable_unsafe_intrinsics_ = true;

  /**
   * Disable unsafe intrinsics.
   */
  public static void disableUnsafeIntrinsics()
  {
    _enable_unsafe_intrinsics_ = false;
  }


  /**
   * Check if unsafe intrinsics are enabled.  If not, terminate with a fatal
   * error.
   */
  public static void unsafeIntrinsic()
  {
    if (!_enable_unsafe_intrinsics_)
      {
        Errors.fatal("unsafe operation not permitted", stackTrace());
      }
  }


  /**
   * This contains all open files/streams.
   */
  static OpenResources<AutoCloseable> _openStreams_ = new OpenResources<AutoCloseable>()
  {
    @Override
    protected boolean close(AutoCloseable f) {
      try
      {
        f.close();
        return true;
      }
      catch(Exception e)
      {
        return false;
      }
    }
  };

  /**
   * This contains all open processes.
   */
  public static OpenResources<Process> _openProcesses_ = new OpenResources<Process>()
  {
    @Override
    protected boolean close(Process p) {
      if(PRECONDITIONS) require
        (p != null);

      return true;
    }
  };


  /**
   * This contains all started threads.
   */
  static OpenResources<Thread> _startedThreads_ = new OpenResources<Thread>() {
    @Override
    protected boolean close(Thread f)
    {
      return true;
    };
  };


  public static final Object LOCK_FOR_ATOMIC = new Object();


  /**
   * The result of {@code envir.args[0]}
   */
  public static String _cmd_ =
    System.getProperties().computeIfAbsent(FUZION_COMMAND_PROPERTY,
                                           k -> ProcessHandle.current()
                                                             .info()
                                                             .command()
                                                             .orElse("")) instanceof String str ? str : "";


  /**
   * The results of {@code envir.args[1..n]}
   */
  public static String[] _args_ = new String[] { "argument list not initialized", "this may indicate a severe bug" };


  /*-------------------------  static methods  --------------------------*/


  /**
   * Create a Java string from given byte array.
   */
  public static String utf8ByteArrayDataToString(byte[] ba)
  {
    var l = 0;
    while (l < ba.length && ba[l] != 0)
      {
        l++;
      }
    return new String(ba, 0, l, StandardCharsets.UTF_8);
  }


  /**
   * Create the internal (Java) byte array for given Java String.
   *
   * @param str the Java string
   *
   * @return the resulting array using utf_8 encoded bytes
   */
  public static byte[] stringToUtf8ByteArray(String str)
  {
    return str.getBytes(StandardCharsets.UTF_8);
  }


  /**
   * The main entry point: This is called from the main() function created for
   * the compiled application to start a FuzionThread that executed the Fuzion
   * application code given passed as argument f.
   *
   * @param f instance of an implementation of the Main interface that runs the
   * main feature's precondition followed by its code.
   */
  public static void run(Main f)
  {
    new FuzionThread(f);
  }


  /**
   * Get the current FuzionThread instance. In case the current thread is not a
   * thread attached to the Fuzion runtime, create a fatal error.
   *
   * @return the current thread, never null.
   */
  public static FuzionThread currentThread()
  {
    FuzionThread result = null;
    var ct = Thread.currentThread();
    if (ct instanceof FuzionThread ft)
      {
        result = ft;
      }
    else
      {
        Errors.fatal("Fuzion Runtime used from detached thread " + ct, stackTrace());
      }
    return result;
  }


  /**
   * Report a fatal error and exit.
   *
   * @param msg the error message
   */
  public static void fatal(String msg)
  {
    Errors.fatal(msg, stackTrace());
  }


  /**
   * Create the internal (Java) array for a {@code const_string} from data in the
   * chars of a Java String.
   *
   * @param str the Java string as unicodes.
   *
   * @return the resulting array using utf_8 encoded bytes
   */
  public static byte[] internalArrayForConstString(String str)
  {
    return stringToUtf8ByteArray(str);
  }


  /**
   * Create the internal (Java) array for an {@code array i8} or {@code array u8} from data
   * in the chars of a Java String.
   *
   * @param str the Java string, lower byte is the first, upper the second byte.
   *
   * @param len the length of the resulting byte[]
   *
   * @return the resulting array.
   */
  public static byte[] constArray8FromString(String str, int len)
  {
    var result = new byte[len];
    for (var i = 0; i < result.length; i++)
      {
        var c = str.charAt(i/2);
        result[i] = (byte) (i % 2 == 0 ? c : c >> 8);
      }
    if (CHECKS)
      freeze(result);
    return result;
  }


  /**
   * Create the internal (Java) array for an {@code array i16} from data in the chars
   * of a Java String.
   *
   * @param str the Java string, each char is one i16.
   *
   * @return the resulting array.
   */
  public static short[] constArrayI16FromString(String str)
  {
    var result = new short[str.length()];
    for (var i = 0; i < result.length; i++)
      {
        result[i] = (short) str.charAt(i);
      }
    if (CHECKS)
      freeze(result);
    return result;
  }


  /**
   * Create the internal (Java) array for an {@code array u16} from data in the chars
   * of a Java String.
   *
   * @param str the Java string, each char is one u16.
   *
   * @return the resulting array.
   */
  public static char[] constArrayU16FromString(String str)
  {
    var result = new char[str.length()];
    for (var i = 0; i < result.length; i++)
      {
        result[i] = str.charAt(i);
      }
    if (CHECKS)
      freeze(result);
    return result;
  }


  /**
   * Create the internal (Java) array for an {@code array i32} or {@code array u32} from
   * data in the chars of a Java String.
   *
   * @param str the Java string, two char form one i32 or u32 in little endian order.
   *
   * @return the resulting array.
   */
  public static int[] constArray32FromString(String str)
  {
    var result = new int[str.length() / 2];
    for (var i = 0; i < result.length; i++)
      {
        result[i] =
          ((str.charAt(2*i + 0) & 0xffff)      ) |
          ((str.charAt(2*i + 1) & 0xffff) << 16) ;
      }
    if (CHECKS)
      freeze(result);
    return result;
  }


  /**
   * Create the internal (Java) array for an {@code array i64} or {@code array u64} from
   * data in the chars of a Java String.
   *
   * @param str the Java string, four char form one i64 or u64 in little endian
   * order.
   *
   * @return the resulting array.
   */
  public static long[] constArray64FromString(String str)
  {
    var result = new long[str.length() / 4];
    for (var i = 0; i < result.length; i++)
      {
        result[i] =
          ((str.charAt(4*i + 0) & 0xffffL)      ) |
          ((str.charAt(4*i + 1) & 0xffffL) << 16) |
          ((str.charAt(4*i + 2) & 0xffffL) << 32) |
          ((str.charAt(4*i + 3) & 0xffffL) << 48) ;
      }
    if (CHECKS)
      freeze(result);
    return result;
  }


  /**
   * Create the internal (Java) array for an {@code array f32} from data in the chars
   * of a Java String.
   *
   * @param str the Java string, two chars form the bits of one f32 in little
   * endian order.
   *
   * @return the resulting array.
   */
  public static float[] constArrayF32FromString(String str)
  {
    var result = new float[str.length() / 2];
    for (var i = 0; i < result.length; i++)
      {
        result[i] = Float.intBitsToFloat(((str.charAt(2*i + 0) & 0xffff)      ) |
                                         ((str.charAt(2*i + 1) & 0xffff) << 16) );
      }
    if (CHECKS)
      freeze(result);
    return result;
  }


  /**
   * Create the internal (Java) array for an {@code array f64} from data in the chars
   * of a Java String.
   *
   * @param str the Java string, four chars form the bits of one f64 in little
   * endian order.
   *
   * @return the resulting array.
   */
  public static double[] constArrayF64FromString(String str)
  {
    var result = new double[str.length() / 4];
    for (var i = 0; i < result.length; i++)
      {
        result[i] = Double.longBitsToDouble(((str.charAt(4*i + 0) & 0xffffL)      ) |
                                            ((str.charAt(4*i + 1) & 0xffffL) << 16) |
                                            ((str.charAt(4*i + 2) & 0xffffL) << 32) |
                                            ((str.charAt(4*i + 3) & 0xffffL) << 48) );
      }
    if (CHECKS)
      freeze(result);
    return result;
  }


  /**
   * Create trace output by printing given msg.  This is called by the generated
   * code when JVM.TRACE is true to output the tracing information.
   *
   * @param msg the trace message.
   */
  public static void trace(String msg)
  {
    say(msg);
  }


  public static void effect_default(int id, AnyI instance)
  {
    var t = currentThread();
    t.ensure_effect_capacity(id);
    if (t.effect_load(id) == null)
      {
        t.effect_store(id, instance);
      }
  }


  /**
   * Helper method to implement intrinsic effect.type.is_instated.
   *
   * @param id an effect id.
   *
   * @return true iff an effect with that id was instated.
   */
  public static boolean effect_is_instated(int id)
  {
    var t = currentThread();

    return t.effect_load(id) != null;
  }


  /**
   * Helper method to implement intrinsic effect.replace0.
   *
   * @param id an effect type id.
   *
   * @param instance a new instance to replace the old one
   */
  public static void effect_replace(int id, AnyI instance)
  {
    var t = currentThread();

    t.effect_store(id, instance);
  }


  /**
   * Helper method to handle an InvocationTargetException caused by a call to
   * java.lang.reflect.Method.invoke.  This checks the causing exception, if
   * that is an unchecked RuntimeException or Error, it just re-throws it to be
   * handled by the caller.
   *
   * Otherwise, it causes a fatal error immediately.
   *
   * @param e the caught exception
   */
  public static void handleInvocationTargetException(InvocationTargetException e)
  {
    var o = e.getCause();
    if (o instanceof StackOverflowError so)
      {
        Errors.fatal("Stack overflow", stackTrace(so));
      }
    else if (o instanceof RuntimeException r) { throw r; }
    else if (o instanceof Error            r) { throw r; }
    else if (o != null)
      {
        Errors.fatal("Error while running JVM compiled code: " + o);
      }
    else
      {
        Errors.fatal("Error while running JVM compiled code: " + e);
      }
  }


  /**
   * Helper method to implement effect.abort0.  Abort the currently instated
   * effect with given id.  Helper to implement intrinsic effect.abort.
   *
   * @param id the id of the effect type that is aborted.
   */
  public static void effect_abort(int id)
  {
    throw new Abort(id);
  }


  /**
   * Helper method to implement effect.type.instante0. Instate a new instance for
   * effect type with given id.
   *
   * The existing value instated for the effect type identified by id will be
   * evacuated to FuzionThread._effectStack to be restored by effect_pop.
   *
   * @param id the id of the effect type that is instated
   *
   * @param instance the effect instance that is instated, NOTE: This is {@code _UNIT_TYPE_EFFECT_}
   * for a unit type effect.
   */
  public static void effect_push(int id, AnyI instance)
  {
    if (PRECONDITIONS) require
      (instance != null);

    var t = currentThread();

    var old = t.effect_load(id);
    t.effect_store(id, instance);
    t._effectStack.add(old);
  }


  /**
   * Helper method to implement effect.type.instante0. Un-instate an instance
   * instated by effect_push.
   *
   * The original instance that was saved to FuzionThread._effectStack will be
   * re-instated.
   *
   * @param id the id of the effect type that is popped.
   *
   * @return the instance that was previously instated or replaced for effect
   * type id.
   */
  public static AnyI effect_pop(int id)
  {
    var t = currentThread();

    var res = t.effect_load(id);
    var instance = t._effectStack.removeLast();
    t.effect_store(id, instance);
    return res;
  }


  /**
   * Get the message of last exception thrown in the current thread.
   */
  public static String getException()
  {
    var result = ((FuzionThread)Thread.currentThread())._thrownException.getMessage();
    if (result == null)
      {
        result = "";
      }
    return result;
  }


  /**
   * Get the current stack trace of Fuzion routines for printing error messages.
   *
   * @return the stack trace as a multi-line String.
   */
  public static String stackTrace()
  {
    return stackTrace(new Throwable());
  }



  /**
   * cached results of {@code classNameToFeatureName}.
   */
  static WeakHashMap<ClassLoader, Map<String, String>> _classNameToFeatureName = new WeakHashMap<>();


  /**
   * Obtain the contents of the resource CLASS_NAME_TO_FUZION_CLAZZ_NAME that
   * provides a mapping from Java class names to corresponding human readable
   * Fuzion feature names.
   */
  public static synchronized Map<String,String> classNameToFeatureName()
  {
    Map<String,String> result = null;
    var l = Thread.currentThread() instanceof FuzionThread ft ? ft._loader : null;
    if (l != null)
      {
        result = _classNameToFeatureName.get(l);
        if (result == null)
          {
            result = new TreeMap<>();
            var mcl = l.getResourceAsStream(CLASS_NAME_TO_FUZION_CLAZZ_NAME);
            if (mcl != null)
              {
                var reader = new BufferedReader(new InputStreamReader(mcl));
                try
                  {
                    var ln = reader.readLine();
                    while (ln != null)
                      {
                        var e = ln.split("\t");
                        if (e.length == 2)
                          {
                            result.put(e[0], e[1]);
                          }
                        ln = reader.readLine();
                      }
                  }
                catch (IOException x)
                  {
                    System.err.println("Failed to read resource `"+"`: " + x);
                  }
              }
            _classNameToFeatureName.put(l, result);
          }
      }
    return result;
  }


  /**
   * Get the stack trace of Fuzion routines of a given Throwable for printing
   * error messages.
   *
   * @param t the Throwable, must not be null,
   *
   * @return the stack trace as a multi-line String.
   */
  public static String stackTrace(Throwable t)
  {
    if (PRECONDITIONS) require
      (t != null);

    var stacktrace = new StringWriter();
    stacktrace.write("Call stack:\n");
    String last = "";
    int count = 0;
    for (var s : t.getStackTrace())
      {
        var m = s.getMethodName();
        var show = switch (m)
          {
          case "main",
               ROUTINE_NAME -> true;
          default           -> false;
          };
        var cl = s.getClassName();
        if (show && cl.startsWith(CLASS_PREFIX))
          {
            var mp = classNameToFeatureName();
            String str = mp != null ? mp.get(cl) : null;
            if (str == null)
              {
                int start;
                if (cl.startsWith(CLASS_PREFIX_WITH_ID))
                  {
                    var pre = CLASS_PREFIX_WITH_ID.length();
                    start = Math.max(cl.indexOf("_", pre)+1, pre);
                  }
                else
                  {
                    start = CLASS_PREFIX.length();
                  }
                str = cl.substring(start);
              }
            if (str.equals(last))
              {
                count++;
              }
            else
              {
                if (count > 1)
                  {
                    stacktrace.write("\n  " + StringHelpers.repeated(count));
                  }
                else if (count > 0)
                  {
                    stacktrace.write("\n");
                  }
                stacktrace.write(str);
                last = str;
                count = 1;
              }
          }
      }
    if (count > 1)
      {
        stacktrace.write("\n  " + StringHelpers.repeated(count));
      }

    return stacktrace.toString();
  }

  public static byte[] fuzion_sys_fileio_read_dir(long fd)
  {
    unsafeIntrinsic();

    var i = getIterator(fd);
    try
      {
        return stringToUtf8ByteArray(i.next().getFileName().toString());
      }
    catch (NoSuchElementException e)
      {
        return stringToUtf8ByteArray("NoSuchElementException encountered!");
      }
  }

  @SuppressWarnings("unchecked")
  public static Iterator<Path> getIterator(long fd)
  {
    return (Iterator<Path>)_openStreams_.get(fd);
  }


  public static byte[] fuzion_sys_env_vars_get0(Object d)
  {
    return stringToUtf8ByteArray(System.getenv(utf8ByteArrayDataToString((byte[]) d)));
  }


  /**
   * Helper method called by the fuzion.java.string_to_java_object0 intrinsic.
   *
   * Creates a new instance of String from the byte array passed as argument,
   * assuming the byte array contains an UTF-8 encoded string.
   *
   * @param b byte array consisting of a string encoded as UTF-8 bytes
   *
   * @return the string from the array, as an instance of Java's String
   */
  public static String fuzion_java_string_to_java_object0(byte[] b)
  {
    unsafeIntrinsic();

    return new String(b, StandardCharsets.UTF_8);
  }


  /**
   * Helper method to convert String to utf8-bytes.
   *
   * @param str the string
   *
   * @return the bytes, if str is null, str used is: "--null--"
   */
  public static byte[] fuzion_java_string_to_bytes_array(String str)
  {
    unsafeIntrinsic();

    if (str == null)
      {
        str = "--null--";
      }
    return str.getBytes(StandardCharsets.UTF_8);
  }


  /**
   * Helper method called by the fuzion.java.get_static_field0 intrinsic.
   *
   * Retrieves the content of a given static field.
   *
   * @param clazz name of the class of the field
   *
   * @param field name of the field
   *
   * @return whatever is stored in the specified static field
   */
  public static Object fuzion_java_get_static_field0(String clazz, String field)
  {
    unsafeIntrinsic();

    Object result;

    try
      {
        Class cl = Class.forName(clazz);
        Field f = cl.getDeclaredField(field);
        result = f.get(null);
      }
    catch (IllegalAccessException | ClassNotFoundException | NoSuchFieldException e)
      {
        Errors.fatal(e.toString()+" when calling fuzion.java.get_static_field for field "+clazz+"."+field);
        result = null;
      }

    return result;
  }


  /**
   * Helper method called by the fuzion.java.set_static_field0 intrinsic.
   *
   * Sets the static field to the given content
   *
   * @param clazz name of the class of the field
   *
   * @param field name of the field
   *
   * @param value the value to which the field should be set
   */
  public static void fuzion_java_set_static_field0(String clazz, String field, Object value)
  {
    unsafeIntrinsic();

    try
      {
        Class cl = Class.forName(clazz);
        Field f = cl.getDeclaredField(field);
        f.set(null, value);
      }
    catch (IllegalAccessException | ClassNotFoundException | NoSuchFieldException e)
      {
        Errors.fatal(e.toString()+" when calling fuzion.java.set_static_field for field "
                     +clazz+"."+field+" and value "+value);
      }
  }


  /**
   * Helper method called by the fuzion.java.get_field0 intrinsic.
   *
   * Given some instance of a Java class, retrieves the content of a given field in
   * this instance.
   *
   * @param thiz the Java instance
   *
   * @param field name of the field
   *
   * @return whatever is stored in the specified field of the instance
   */
  public static Object fuzion_java_get_field0(Object thiz, String field)
  {
    unsafeIntrinsic();

    Object result;
    Class clazz = null;

    try
      {
        clazz = thiz.getClass();
        Field f = clazz.getDeclaredField(field);
        result = f.get(thiz);
      }
    catch (IllegalAccessException | NoSuchFieldException e)
      {
        Errors.fatal(e.toString()
          + " when calling fuzion.java.get_field for field "
          + (clazz !=null ? clazz.getName() : "")+"."+field
        );
        result = null;
      }

    return result;
  }


  /**
   * Helper method called by the fuzion.java.set_field0 intrinsic.
   *
   * Given some instance of a Java class, set the given field in
   * this instance to the given content
   *
   * @param thiz the Java instance
   *
   * @param field name of the field
   *
   * @param value the value the field should be set to
   */
  public static void fuzion_java_set_field0(Object thiz, String field, Object value)
  {
    unsafeIntrinsic();

    Class clazz = null;

    try
      {
        clazz = thiz.getClass();
        Field f = clazz.getDeclaredField(field);
        f.set(thiz, value);
      }
    catch (IllegalAccessException | NoSuchFieldException e)
      {
        Errors.fatal(e.toString()
          + " when calling fuzion.java.set_field for field "
          + (clazz !=null ? clazz.getName() : "")+"."+field
          + "and value "+value
        );
      }
  }


  /**
   * Helper method for fuzion_java_call_v0, fuzion_java_call_s0, and fuzion_java_call_c0.
   *
   * Given the name of a class, the name of a method and the signature of the method or
   * (if no method is given), the classes' constructors' signature, parses the signature
   * that is given in string form into an array of instances of {@link java.lang.Class},
   * and parses the name of the class given as a string into an instance of
   * {@link java.lang.Class}.
   *
   * @param what what is calling this helper (used in the error message), should be one of
   * virtual, static, or constructor
   *
   * @param clName name of the class
   *
   * @param name name of the method
   *
   * @param sig signature of the method (or if method not given, the constructor)
   *
   * @return a {@link dev.flang.util.Pair} of the array of {@link java.lang.Class} instances
   * representing the given signature, and an instance of {@link java.lang.Class} representing
   * the given class.
   */
  private static Pair<Class[], Class> getParsAndClass(String what, String clName, String name, String sig)
  {
    var p = JavaInterface.getPars(sig);
    if (p == null)
      {
        Errors.fatal("could not parse signature >>"+sig+"<<");
      }
    Class cl;
    try
      {
        cl = Class.forName(clName);
      }
    catch (ClassNotFoundException e)
      {
        Errors.fatal("ClassNotFoundException when calling fuzion.java.call_"+what+" for class" +
                           clName + " calling " + ((name != null) ? name : ("new " + clName)) + sig);
        cl = Object.class; // not reached.
      }

    return new Pair<>(p, cl);
  }


  /**
   * Helper method called by the fuzion.java.call_v0 intrinsic.
   *
   * Calls a Java method on a specified instance.
   *
   * @param clName name of the class of the method to be called
   *
   * @param name name of the method to be called
   *
   * @param sig signature of the method to be called
   *
   * @param thiz instance of the class on which the method should be called
   *
   * @param args the arguments with which the method should be called
   *
   * @return whatever the method returns given the arguments
   */
  @SuppressWarnings("unchecked")
  public static Object fuzion_java_call_v0(String clName, String name, String sig, Object thiz, Object[] args)
  {
    if (PRECONDITIONS) require
      (clName != null);

    unsafeIntrinsic();

    Method m = null;
    var pcl = getParsAndClass("virtual", clName, name, sig);
    var p = pcl.v0();
    var cl = pcl.v1();
    try
      {
        m = cl.getMethod(name, p);
      }
    catch (NoSuchMethodException e)
      {
        Errors.fatal("NoSuchMethodException when calling fuzion.java.call_virtual calling " +
                           (cl.getName() + "." + name) + sig);
      }
    return invoke(m, thiz, args);
  }


  static interface ReflectionInvoker
  {
    Object invoke() throws InvocationTargetException, IllegalAccessException, InstantiationException;
  }


  /**
   * Helper to catch a possible {@link Exception} thrown by an invocation.
   *
   * @return the result of the invocation, or, if an error occurred, the global
   * instance of {@link JavaError}.
   */
  private static Object invokeAndWrapException(ReflectionInvoker invoke)
  {
    Object res;
    try
      {
        res = invoke.invoke();
      }
    catch (InvocationTargetException e)
      {
        ((FuzionThread)Thread.currentThread())._thrownException = e.getCause();
        res = _JAVA_ERROR_;
      }
    catch (Throwable e)
      {
        ((FuzionThread)Thread.currentThread())._thrownException = e;
        res = _JAVA_ERROR_;
      }
    return res;
  }


  /**
   * Invoke a method using {@link #invokeAndWrapException(ReflectionInvoker)}.
   *
   * @param m the {@link Method} to be invoked
   *
   * @param thiz the {@link Object instance} on which the {@link Method} shall be invoked
   *
   * @param args arguments to invoke this {@link Method} with
   *
   * @return the result of the invocation
   */
  private static Object invoke(Method m, Object thiz, Object[] args)
  {
    return invokeAndWrapException(()->m.invoke(thiz, args));
  }


  /**
   * Helper method called by the fuzion.java.call_s0 intrinsic.
   *
   * Calls a static Java method of a specified class.
   *
   * @param clName name of the class of the method to be called
   *
   * @param name name of the method to be called
   *
   * @param sig signature of the method to be called
   *
   * @param args the arguments with which the method should be called
   *
   * @return whatever the method returns given the arguments
   */
  @SuppressWarnings("unchecked")
  public static Object fuzion_java_call_s0(String clName, String name, String sig, Object[] args)
  {
    if (PRECONDITIONS) require
      (clName != null);

    unsafeIntrinsic();

    Method m = null;
    var pcl = getParsAndClass("static", clName, name, sig);
    var p = pcl.v0();
    var cl = pcl.v1();
    try
      {
        m = cl.getMethod(name,p);
      }
    catch (NoSuchMethodException e)
      {
        Errors.fatal("NoSuchMethodException when calling fuzion.java.call_static calling " +
                           (cl.getName() + "." + name) + sig);
      }
    return invoke(m, null, args);
  }


  /**
   * Helper method called by the fuzion.java.call_c0 intrinsic.
   *
   * Calls a Java constructor of a specified class.
   *
   * @param clName name of the class whose constructor should be called
   *
   * @param sig signature of the constructor to be called
   *
   * @param args the arguments with which the constructor should be called
   *
   * @return the instance of the class returned by the constructor
   */
  public static Object fuzion_java_call_c0(String clName, String sig, Object[] args)
  {
    if (PRECONDITIONS) require
      (clName != null);

    unsafeIntrinsic();

    var pcl = getParsAndClass("constructor", clName, null, sig);
    var p = pcl.v0();
    var cl = pcl.v1();
    try
      {
        @SuppressWarnings("unchecked")
        var co = cl.getConstructor(p);
        return invokeAndWrapException(()->co.newInstance(args));
      }
    catch (NoSuchMethodException e)
      {
        Errors.fatal("NoSuchMethodException when calling fuzion.java.call_constructor calling " +
                           ("new " + clName) + sig);
        return null; // not reached
      }
  }


  /**
   * @param code the Unary instance to be executed, i.e. the outer instance
   *
   * @param call the Java clazz of the Unary instance to be executed.
   */
  public static long thread_spawn(Any code, Class call)
  {
    long result = 0;
    Method r = null;
    for (var m : call.getDeclaredMethods())
      {
        if (m.getName().equals(ROUTINE_NAME))
          {
            r = m;
          }
      }
    if (r == null)
      {
        Errors.fatal("in " + Runtime.class.getName() + ".thread_spawn: missing `" + ROUTINE_NAME + "` in class `" + call + "`");
      }
    else
      {
        var t = new FuzionThread(r, code);
        result = _startedThreads_.add(t);
      }
    return result;
  }


  public static byte[] args_get(int i)
  {
    return stringToUtf8ByteArray(i == 0 ? _cmd_
                                        : _args_[i-1]);
  }


  public static Object mtx_init()
  {
    return new ReentrantLock();
  }

  public static boolean mtx_lock(Object rl)
  {
    try
      {
        ((ReentrantLock)rl).lockInterruptibly();
        return true;
      }
    catch(InterruptedException e)
      {
        return false;
      }
  }

  public static boolean mtx_trylock(Object rl)
  {
    return ((ReentrantLock)rl).tryLock();
  }

  public static boolean mtx_unlock(Object rl)
  {
    try
      {
        ((ReentrantLock)rl).unlock();
        return true;
      }
    catch(IllegalMonitorStateException e)
      {
        return false;
      }
  }

  public static void mtx_destroy(Object rl)
  {

  }


  public static Object cnd_init(Object rl)
  {
    return ((ReentrantLock)rl).newCondition();
  }

  public static boolean cnd_signal(Object cnd)
  {
    try
      {
        ((Condition)cnd).signal();
        return true;
      }
    catch(Exception e)
      {
        return false;
      }
  }

  public static boolean cnd_broadcast(Object cnd)
  {
    try
      {
        ((Condition)cnd).signalAll();
        return true;
      }
    catch(Exception e)
      {
        return false;
      }
  }

  public static boolean cnd_wait(Object cnd)
  {
    try
      {
        ((Condition)cnd).await();
        return true;
      }
    catch(Exception e)
      {
        return false;
      }
  }

  public static void cnd_destroy(Object cnd)
  {

  }


  /*---------------------------------------------------------------------*/


  /**
   * Weak map of frozen (immutable) arrays, used to debug accidental
   * modifications of frozen array.
   */
  static Map<Object, String> _frozenPointers_ = CHECKS ? Collections.synchronizedMap(new WeakHashMap<Object, String>()) : null;


  /**
   * If CHECKS are enabled, add the given pointer to the set of frozen (immutable) arrays.
   *
   * @param p a pointer to an array.
   */
  public static void freeze(Object p)
  {
    if (CHECKS)
      {
        // note that the internal array for `array unit` is null, we never
        // freeze this since modifications of unit-type arrays are pretty
        // harmless.
        if (p != null)
          {
            _frozenPointers_.put(p, "");
          }
      }
  }


  /**
   * If CHECKS are enabled, check that the given pointer was not added to set of
   * frozen (immutable) arrays using freeze(p).
   *
   * @param p a pointer to an array.
   */
  public static void ensure_not_frozen(Object p)
  {
    if (CHECKS)
      {
        // note that p may be null for `array unit`, and `null` is supported by
        // Java's WeakHashMap and will not be added by `freeze`, so we are fine.
        if (_frozenPointers_.containsKey(p))
          {
            Errors.fatal("Attempt to modify immutable array", stackTrace());
          }
      }
  }



  /**
   * Find the method handle of a native function
   *
   * @param str name of the function: e.g. sqlite3_exec
   *
   * @param desc the FunctionDescriptor of the function
   *
   * @return
   */
  public static MethodHandle get_method_handle(String str, FunctionDescriptor desc)
  {
    return Linker.nativeLinker()
      .downcallHandle(
        SymbolLookup.libraryLookup(System.mapLibraryName("fuzion" /* NYI */), Arena.ofAuto())
          .or(SymbolLookup.libraryLookup(System.mapLibraryName("sqlite3" /* NYI */), Arena.ofAuto()))
          .find(str)
          .orElseThrow(() -> new UnsatisfiedLinkError("unresolved symbol: " + str)),
        desc);
  }

  /**
   * copy the contents of memSeg to obj
   */
  public static void memorySegment2Obj(Object obj, MemorySegment memSeg)
  {
    if      (obj instanceof byte   [] arr) { MemorySegment.ofArray(arr).copyFrom(memSeg); }
    else if (obj instanceof short  [] arr) { MemorySegment.ofArray(arr).copyFrom(memSeg); }
    else if (obj instanceof char   [] arr) { MemorySegment.ofArray(arr).copyFrom(memSeg); }
    else if (obj instanceof int    [] arr) { MemorySegment.ofArray(arr).copyFrom(memSeg); }
    else if (obj instanceof long   [] arr) { MemorySegment.ofArray(arr).copyFrom(memSeg); }
    else if (obj instanceof float  [] arr) { MemorySegment.ofArray(arr).copyFrom(memSeg); }
    else if (obj instanceof double [] arr) { MemorySegment.ofArray(arr).copyFrom(memSeg); }
    else if (obj instanceof MemorySegment) {}
    else if (obj instanceof Object [] arr && arr.length > 0 && arr[0] instanceof MemorySegment)
      {
        for (int i = 0; i < arr.length; i++)
          {
            arr[i] = memSeg.getAtIndex(ValueLayout.ADDRESS, i * ValueLayout.ADDRESS.byteSize());
          }
      }
    else if (obj instanceof Object []    ) { /* NYI: UNDER DEVELOPMENT */ }
    else { throw new Error("NYI memorySegment2Obj: " + obj.getClass()); }
  }


  /**
   * creates a new MemorySegment and copies
   * content of object to the memory segment
   */
  public static MemorySegment obj2MemorySegment(Object obj)
  {
    if      (obj instanceof byte   [] arr) { return Arena.ofAuto().allocate(arr.length * 1).copyFrom(MemorySegment.ofArray(arr)); }
    else if (obj instanceof short  [] arr) { return Arena.ofAuto().allocate(arr.length * 2).copyFrom(MemorySegment.ofArray(arr)); }
    else if (obj instanceof char   [] arr) { return Arena.ofAuto().allocate(arr.length * 2).copyFrom(MemorySegment.ofArray(arr)); }
    else if (obj instanceof int    [] arr) { return Arena.ofAuto().allocate(arr.length * 4).copyFrom(MemorySegment.ofArray(arr)); }
    else if (obj instanceof long   [] arr) { return Arena.ofAuto().allocate(arr.length * 8).copyFrom(MemorySegment.ofArray(arr)); }
    else if (obj instanceof float  [] arr) { return Arena.ofAuto().allocate(arr.length * 4).copyFrom(MemorySegment.ofArray(arr)); }
    else if (obj instanceof double [] arr) { return Arena.ofAuto().allocate(arr.length * 8).copyFrom(MemorySegment.ofArray(arr)); }
    else if (obj instanceof MemorySegment memSeg) { return memSeg; }
    else if (obj instanceof Object [] arr)
      {
        var argsArray = Arena.ofAuto().allocate(arr.length * 8);
        for (int i = 0; i < arr.length; i++)
          {
            argsArray.set(ValueLayout.ADDRESS, i * 8, obj2MemorySegment(arr[i]));
          }
        return argsArray;
      }
    else { throw new Error("NYI obj2MemorySegment: " + obj.getClass()); }
  }


  public static Object c_array(MemoryLayout memLayout, Object obj, int length)
  {
    // NYI: remove this if else, once DFA knows hwo to trigger call of callback
    if (obj instanceof Object[])
      {
        return obj;
      }
    else
      {
        var memSeg = ((MemorySegment)obj).reinterpret(length * memLayout.byteSize());
        if (memLayout instanceof OfByte o)
          {
            var result = new byte[length];
            for (int i = 0; i < result.length; i++)
              {
                result[i] = memSeg.getAtIndex(o, i);
              }
            return result;
          }
        else if (memLayout instanceof OfBoolean ob)
          {
            var result = new boolean[length];
            for (int i = 0; i < result.length; i++)
              {
                result[i] = memSeg.getAtIndex(ob, i);
              }
            return result;
          }
        else if (memLayout instanceof OfChar ob)
          {
            var result = new char[length];
            for (int i = 0; i < result.length; i++)
              {
                result[i] = memSeg.getAtIndex(ob, i);
              }
            return result;
          }
        else if (memLayout instanceof OfDouble ob)
          {
            var result = new double[length];
            for (int i = 0; i < result.length; i++)
              {
                result[i] = memSeg.getAtIndex(ob, i);
              }
            return result;
          }
        else if (memLayout instanceof OfFloat ob)
          {
            var result = new float[length];
            for (int i = 0; i < result.length; i++)
              {
                result[i] = memSeg.getAtIndex(ob, i);
              }
            return result;
          }
        else if (memLayout instanceof OfInt ob)
          {
            var result = new int[length];
            for (int i = 0; i < result.length; i++)
              {
                result[i] = memSeg.getAtIndex(ob, i);
              }
            return result;
          }
        else if (memLayout instanceof OfLong ob)
          {
            var result = new long[length];
            for (int i = 0; i < result.length; i++)
              {
                result[i] = memSeg.getAtIndex(ob, i);
              }
            return result;
          }
        else if (memLayout instanceof OfShort ob)
          {
            var result = new short[length];
            for (int i = 0; i < result.length; i++)
              {
                result[i] = memSeg.getAtIndex(ob, i);
              }
            return result;
          }
        else
          {
            var ob = (AddressLayout)memLayout;
            var result = new Object[length];
            for (int i = 0; i < result.length; i++)
              {
                result[i] = memSeg.getAtIndex(ob, i);
              }
            return result;
          }
      }
  }


  public static MemorySegment upcall(Any code, Class call)
  {
    Method method = null;
    for (var m : call.getDeclaredMethods())
      {
        if (m.getName().equals(ROUTINE_NAME))
          {
            method = m;
          }
      }
    if (method == null)
      {
        Errors.fatal("Runtime.fatal");
      }

    MethodHandle handle;
    try
      {
        handle = MethodHandles.lookup().unreflect(method).bindTo(code);
        handle = MethodHandles
          .explicitCastArguments(handle, MethodType.methodType(int.class, new Class[]{ MemorySegment.class, int.class, MemorySegment.class, MemorySegment.class }));

        var desc = method.getReturnType() == void.class
          ? FunctionDescriptor.ofVoid(layout(handle.type().parameterArray()))
          : FunctionDescriptor.of(layout(method.getReturnType()), layout(handle.type().parameterArray()));

        return Linker
          .nativeLinker()
          .upcallStub(handle, desc, Arena.ofAuto());
      }
    catch (IllegalAccessException e)
      {
        Errors.fatal(e);
      }
    return null;
  }


  public static int c_string_len(MemorySegment segment)
  {
    int length = 0;
    segment = segment.reinterpret(10000);

    while (segment.get(ValueLayout.JAVA_BYTE, length) != 0)
      {
        length++;
      }

    return length;
  }


  public static MemoryLayout layout2(String str)
  {
    if (str.equals("I"))
      {
        return layout(int.class);
      }
    else if (str.equals("B"))
      {
        return layout(byte.class);
      }
    else if (str.equals("Z"))
      {
        return layout(boolean.class);
      }
    else if (str.equals("F"))
      {
        return layout(float.class);
      }
    else if (str.equals("D"))
      {
        return layout(double.class);
      }
    else if (str.equals("S"))
      {
        return layout(short.class);
      }
    else if (str.equals("J"))
      {
        return layout(long.class);
      }
    else if (str.equals("C"))
      {
        return layout(char.class);
      }
    return layout(Object.class);
  }

  public static MemoryLayout layout(Class returnType)
  {
    if (PRECONDITIONS) require
      (returnType != void.class);

    if (returnType == boolean.class)
      {
        return ValueLayout.JAVA_BOOLEAN;
      }
    if (returnType == byte.class)
      {
        return ValueLayout.JAVA_BYTE;
      }
    if (returnType == char.class)
      {
        return ValueLayout.JAVA_CHAR;
      }
    if (returnType == short.class)
      {
        return ValueLayout.JAVA_SHORT;
      }
    if (returnType == int.class)
      {
        return ValueLayout.JAVA_INT;
      }
    if (returnType == long.class)
      {
        return ValueLayout.JAVA_LONG;
      }
    if (returnType == float.class)
      {
        return ValueLayout.JAVA_FLOAT;
      }
    if (returnType == double.class)
      {
        return ValueLayout.JAVA_DOUBLE;
      }
    return ValueLayout.ADDRESS;
  }


  private static MemoryLayout[] layout(Class<?>[] parameterTypes)
  {
    var result = new MemoryLayout[parameterTypes.length];
    for (int i = 0; i < result.length; i++)
      {
        result[i] = layout(parameterTypes[i]);
      }
    return result;
  }


}

/* end of file */
