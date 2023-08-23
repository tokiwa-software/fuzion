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
 * Source of class Runner
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm;

import dev.flang.be.jvm.classfile.ClassFile;
import dev.flang.be.jvm.runtime.Runtime;

import dev.flang.util.Errors;

import java.util.ArrayList;
import java.util.TreeMap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Runnner is a ClassLoader that can be used to immediately execute bytecode created by the
 * JVM bytecode backend.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Runner extends ClassLoader
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * All the classfiles that can be created by this runner.
   */
  private TreeMap<String, ClassFile> _classFiles = new TreeMap<>();


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Constructor.  After call, requires classes to be added via Runner.add(),
   * then execution can start via Runner.runMain().
   */
  Runner()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Add a class file that can be loaded by this runner.
   */
  public void add(ClassFile cf)
  {
    _classFiles.put(cf._name, cf);
  }


  /**
   * JVM callback to load class with given name
   *
   * @param name the class name, e.g. "java/lang/Object".q
   */
  public Class findClass(String name)
  {
    Class result = null;
    var cf = _classFiles.get(name);
    if (cf != null)
      {
        var b =_classFiles.get(name).bytes();
        result = defineClass(name, b, 0, b.length);
      }
    return result;
  }


  /**
   * Run the fuzion code in the generated classes.
   *
   * This executes the main method defined in the universe.
   */
  public void runMain(ArrayList<String> applicationArgs)
  {
    Class<?> c = findClass("fzC_universe");
    Method m = null;
    try
      {
        m = c.getDeclaredMethod("main", (new String[0]).getClass());
      }
    catch (NoSuchMethodException e)
      {
        Errors.fatal("Error while launching JVM compiled code: " + e);
      }
    try
      {
        m.invoke(null, (Object) applicationArgs.toArray(String[]::new));
      }
    catch (IllegalAccessException e)
      {
        Errors.fatal("Error while launching JVM compiled code: " + e);
      }
    catch (InvocationTargetException e)
      {
        Runtime.handleInvocationTargetException(e);
      }
  }

}

/* end of file */
