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
 * Source of class FuzionThread
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm.runtime;

import dev.flang.util.Errors;
import dev.flang.util.List;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


/**
 * FuzionThread is a thread spawned for code running in the JVM backend.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FuzionThread extends Thread
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Currently installed effects for this thread.
   */
  List<AnyI> _installedEffects = new List<>();


  List<AnyI> _effectStack = new List<>();


  /**
   * Field to store the exception in case a call into Java code resulted in
   * throwing an exception.
   */
  public Throwable _thrownException = null;


  /**
   * Class loader used to load resources related to compiled fuzion code.
   */
  final ClassLoader _loader;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create and start a new thread for the given code.
   *
   * @param r the static Java method that is the main fuzion function for this
   * thread.
   *
   * @param code the argument to pass to r.
   */
  FuzionThread(Method r, Any code)
  {
    this((Runnable) () ->
         {
           try
             {
               r.invoke(null, code);
             }
           catch (IllegalAccessException e)
             {
               Errors.fatal("thread_spawn call caused `" + e + "` when calling `" + r + "`");
             }
           catch (InvocationTargetException e)
             {
               Runtime.handleInvocationTargetException(e);
             }
         },
         r.getDeclaringClass().getClassLoader());
  }


  /**
   * Create a main FuzionThread and run code.
   */
  FuzionThread(Main main)
  {
    this((Runnable) ()->main.fz_run(), main.getClass().getClassLoader());
  }


  /**
   * Create a main FuzionThread and run code.
   *
   * @oaran loader class loader used to load resources related to compiled
   * fuzion code.
   */
  private FuzionThread(Runnable r, ClassLoader l)
  {
    super(()->Errors.runAndExit(r), "Fuzion thread");
    _loader = l;
    start();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Make sure _installedEffects is large enough to hold effect with given id.
   *
   * @param id an effect id.
   */
  void ensure_effect_capacity(int id)
  {
    while (_installedEffects.size() < id+1)
      {
        _installedEffects.add(null);
      }
  }


  /**
   * Internal helper to load an effect instance from the given id.
   *
   * @param id an effect id.
   */
  AnyI effect_load(int id)
  {
    ensure_effect_capacity(id);
    return _installedEffects.get(id);
  }


  /**
   * Internal helper to store an effect instance for the given id.
   *
   * @param id an effect id.
   */
  void effect_store(int id, AnyI instance)
  {
    ensure_effect_capacity(id);
    _installedEffects.set(id, instance);
  }


}

/* end of file */
