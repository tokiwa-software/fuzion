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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.OutputStream;

import java.nio.charset.StandardCharsets;


/**
 * Runtime provides the runtime system for the JVM backend.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Runtime extends ANY
{


  /*----------------------------  constants  ----------------------------*/

  static final int NYI_MAX_EFFECT_ID = 10000; // NYI: remove!


  /*--------------------------  static fields  --------------------------*/


  /**
   * Currently installed effects.
   *
   * NYI: this should be thread local
   *
   * NYI: this should not have a static size but allocated to hold the # of
   * effects in use
   */
  static Any[] _installedEffects_ = new Any[NYI_MAX_EFFECT_ID];


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


  public static final String[] args = new String[] { "<unknown command>" };


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


  public static void effect_default(int id, Any instance)
  {
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    if (_installedEffects_[id] == null)
      {
        _installedEffects_[id] = instance;
      }
  }

  public static boolean effect_is_installed(int id)
  {
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    return _installedEffects_[id] != null;
  }

  public static void effect_replace(int id, Any instance)
  {
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    _installedEffects_[id] = instance;
  }

  public static Any effect_get(int id)
  {
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    if (_installedEffects_[id] == null)
      {
        throw new Error("No effect of "+id+" installed");
      }
    return _installedEffects_[id];
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
    Errors.fatal("CONTRACT FAILED: " + msg);
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
