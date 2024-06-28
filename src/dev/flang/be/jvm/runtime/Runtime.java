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

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.JavaInterface;
import dev.flang.util.Pair;

import java.io.StringWriter;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;


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
  static class Abort extends Error
  {

    int _effect;

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

  static long _stdin  = _openStreams_.add(System.in );
  static long _stdout = _openStreams_.add(System.out);
  static long _stderr = _openStreams_.add(System.err);


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


  static long _next_unique_id = 0xf0015feedbadf00dL;

  static final long UNIQUE_ID_INCREMENT = 1000000000000223L; // large prime generated using https://www.browserling.com/tools/prime-numbers


  static final Object UNIQUE_ID_LOCK = new Object() {};


  public static final Object LOCK_FOR_ATOMIC = new Object();


  /**
   * The result of `envir.args[0]`
   */
  public static String _cmd_ =
    System.getProperties().computeIfAbsent(FUZION_COMMAND_PROPERTY,
                                           k -> ProcessHandle.current()
                                                             .info()
                                                             .command()
                                                             .orElse("")) instanceof String str ? str : "";


  /**
   * The results of `envir.args[1..n]`
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
   * Create the internal (Java) array for a `Const_String` from data in the
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
   * Create the internal (Java) array for an `array i8` or `array u8` from data
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
   * Create the internal (Java) array for an `array i16` from data in the chars
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
   * Create the internal (Java) array for an `array u16` from data in the chars
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
   * Create the internal (Java) array for an `array i32` or `array u32` from
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
   * Create the internal (Java) array for an `array i64` or `array u64` from
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
   * Create the internal (Java) array for an `array f32` from data in the chars
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
   * Create the internal (Java) array for an `array f64` from data in the chars
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


  public static void effect_default(int id, Any instance)
  {
    var t = currentThread();
    t.ensure_effect_capacity(id);
    if (t.effect_load(id) == null)
      {
        t.effect_store(id, instance);
      }
  }


  /**
   * Helper method to implement intrinsic effect.type.is_installed.
   *
   * @param id an effect id.
   *
   * @return true iff an effect with that id was installed.
   */
  public static boolean effect_is_installed(int id)
  {
    var t = currentThread();

    return t.effect_load(id) != null;
  }


  /**
   * Helper method to implement intrinsic effect.replace.
   *
   * @param id an effect id.
   *
   * @instance a new instance to replace the old one
   */
  public static void effect_replace(int id, Any instance)
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
   * Helper method to implement effect.abort.  Abort the currently installed
   * effect with given id.  Helper to implement intrinsic effect.abort.
   *
   * @param id the id of the effect type that is aborted.
   */
  public static void effect_abort(int id)
  {
    throw new Abort(id);
  }


  /**
   * Helper method to implement effect.abortable.  Install an instance of effect
   * type specified by id and run f.call while it is installed.  Helper to
   * implement intrinsic effect.abort.
   *
   * @param id the id of the effect that is installed
   *
   * @param instance the effect instance that is installed
   *
   * @param code the Unary instance to be executed
   *
   * @param call the Java clazz of the Unary instance to be executed.
   */
  public static void effect_abortable(int id, Any instance, Any code, Class call)
  {
    var t = currentThread();

    var old = t.effect_load(id);
    t.effect_store(id, instance);
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
        Errors.fatal("in effect.abortable, missing `" + ROUTINE_NAME + "` in class `" + call + "`");
      }
    else
      {
        try
          {
            r.invoke(null, code);
          }
        catch (IllegalAccessException e)
          {
            Errors.fatal("effect.abortable call caused `" + e + "` when calling `" + call + "`");
          }
        catch (InvocationTargetException e)
          {
            if (e.getCause() instanceof Abort a)
              {
                if (a._effect != id)
                  {
                    throw a;
                  }
              }
            else
              {
                handleInvocationTargetException(e);
              }
          }
        finally
          {
            t.effect_store(id, old);
          }
      }
  }

  /**
   * Helper method to implement `effect.env` expressions.  Returns the installed
   * effect with the given id.  Causes an error in case no such effect exists.
   *
   * @param id the id of the effect that should be loaded.
   *
   * @return the instance that was installed for this id
   *
   * @throws Error in case no instance was installed.
   */
  public static Any effect_get(int id)
  {
    var t = currentThread();

    var result = t.effect_load(id);
    if (result == null)
      {
        throw new Error("No effect of "+id+" installed");
      }
    return result;
  }


  /**
   * Called after a precondition/postcondition check failed
   *
   * @param msg a detail message explaining what failed
   */
  public static void contract_fail(String msg)
  {
    Errors.fatal("CONTRACT FAILED: " + msg, stackTrace());
  }


  /**
   * Get the message of last exception thrown in the current thread.
   */
  public static String getException()
  {
    return ((FuzionThread)Thread.currentThread())._thrownException.getMessage();
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
            var str = cl.substring(start);
            if (str.equals(last))
              {
                count++;
              }
            else
              {
                if (count > 1)
                  {
                    stacktrace.write("\n  " + Errors.repeated(count));
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
        stacktrace.write("\n  " + Errors.repeated(count));
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
    String clazz = null;

    try
      {
        Class cl = thiz.getClass();
        Field f = cl.getDeclaredField(field);
        result = f.get(thiz);
      }
    catch (IllegalAccessException | NoSuchFieldException e)
      {
        Errors.fatal(e.toString()+" when calling fuzion.java.get_static_field for field "+clazz+"."+field);
        result = null;
      }

    return result;
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
   * @param code the Unary instance to be executed
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


  static long unique_id()
  {
    long result;
    synchronized (UNIQUE_ID_LOCK)
      {
        result = _next_unique_id;
        _next_unique_id = result + UNIQUE_ID_INCREMENT;
      }
    return result;
  }


  public static byte[] args_get(int i)
  {
    return stringToUtf8ByteArray(i == 0 ? _cmd_
                                        : _args_[i-1]);
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


}

/* end of file */
