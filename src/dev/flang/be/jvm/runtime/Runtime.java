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

package dev.flang.be.jvm.runtime;

import dev.flang.be.interpreter.OpenResources; // NYI: remove dependency!

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.OutputStream;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.nio.charset.StandardCharsets;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;


/**
 * Runtime provides the runtime system for the JVM backend.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
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


  /*----------------------------  constants  ----------------------------*/


  /**
   * Copy of dev.flang.be.jvm.Names.ROUTINE_NAME without adding a dependency on
   * that package.  We do not want to bundle the backend classes with a
   * stand-alone application that needs the runtime classes, so this is copied.
   */
  public static final String ROUTINE_NAME = "fzRoutine";


  /*--------------------------  static fields  --------------------------*/


  /**
   * Currently installed effects.
   *
   * NYI: this should be thread local
   */
  static List<Any> _installedEffects_ = new List<>();


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


  static long _next_unique_id = 0xf0015feedbadf00dL;

  static final long UNIQUE_ID_INCREMENT = 1000000000000223L; // large prime generated using https://www.browserling.com/tools/prime-numbers


  static final Object UNIQUE_ID_LOCK = new Object() {};


  public static String[] args = new String[] { "argument list not initialized", "this may indicate a severe bug" };


  /*-------------------------  static methods  --------------------------*/


  /**
   * Report a fatal error and exit.
   *
   * @param msg the error message
   *
   * @return does not
   */
  public static void fatal(String msg)
  {
    Errors.fatal(msg);
  }


  public static byte[] internalArrayForConstString(String str)
  {
    return str.getBytes(StandardCharsets.UTF_8);
  }


  /**
   * Create trace output by printing given msg.  This is called by the generated
   * code when JVM.TRACE is true to output the tracing information.
   *
   * @param msg the trace message.
   */
  public static void trace(String msg)
  {
    System.out.println(msg);
  }

  static Thread _nyi_remove_single_thread = Thread.currentThread();


  /**
   * Make sure _installedEffects_ is large enough to hold effect with given id.
   *
   * @param id an effect id.
   */
  private static void ensure_effect_capacity(int id)
  {
    while (_installedEffects_.size() < id+1)
      {
        _installedEffects_.add(null);
      }
  }


  /**
   * Internal helper to load an effect instance from the given id.
   *
   * @param id an effect id.
   */
  private static Any effect_load(int id)
  {
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    ensure_effect_capacity(id);
    return _installedEffects_.get(id);
  }

  /**
   * Internal helper to store an effect instance for the given id.
   *
   * @param id an effect id.
   */
  private static void effect_store(int id, Any instance)
  {
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    ensure_effect_capacity(id);
    _installedEffects_.set(id, instance);
  }


  public static void effect_default(int id, Any instance)
  {
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    _installedEffects_.ensureCapacity(id + 1);
    if (effect_load(id) == null)
      {
        effect_store(id, instance);
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
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    return effect_load(id) != null;
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
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    effect_store(id, instance);
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
   *
   * @return does not.
   */
  public static void handleInvocationTargetException(InvocationTargetException e)
  {
    var o = e.getCause();
    if (o instanceof RuntimeException r) { throw r; }
    if (o instanceof Error            r) { throw r; }
    if (o != null)
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
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    var old = effect_load(id);
    effect_store(id, instance);
    Method r = null;
    for (var m : call.getDeclaredMethods())
      {
        if (m.getName().equals("fzRoutine"))
          {
            r = m;
          }
      }
    if (r == null)
      {
        Errors.fatal("in effect.abortable, missing `fzRoutine` in class `" + call + "`");
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
            effect_store(id, old);
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
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    var result = effect_load(id);
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
   *
   * @return does not.
   */
  public static void contract_fail(String msg)
  {
    Errors.fatal(new Error("CONTRACT FAILED: " + msg));
  }


  public static void fuzion_std_date_time(int[] arg0)
  {
    var date = new Date();
    var calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    calendar.setTime(date);
    arg0[0] = calendar.get(Calendar.YEAR);
    arg0[1] = calendar.get(Calendar.DAY_OF_YEAR);
    arg0[2] = calendar.get(Calendar.HOUR_OF_DAY);
    arg0[3] = calendar.get(Calendar.MINUTE);
    arg0[4] = calendar.get(Calendar.SECOND);
    arg0[5] = calendar.get(Calendar.MILLISECOND) * 1000;
  }


  public static int fuzion_sys_fileio_write(long f, byte[] fileContent, int l)
  {
    try
      {
        var s = Runtime._openStreams_.get(f);
        if (s instanceof RandomAccessFile raf)
          {
            /* NYI:
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
              }
            */
            raf.write(fileContent);
          }
        else if (s instanceof OutputStream os)
          {
            os.write(fileContent);
          }
        return 0;
      }
    catch (IOException e)
      {
        return -1;
      }
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
    return args[i].getBytes(StandardCharsets.UTF_8);
  }


}

/* end of file */
