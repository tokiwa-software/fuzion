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

import dev.flang.util.Errors;

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

  private TreeMap<String, ClassFile> _classFiles = new TreeMap<>();


  public void add(ClassFile cf)
  {
    _classFiles.put(cf._name, cf);
  }


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



  public void runMain()
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
        m.invoke(null, (Object) new String[0]);
      }
    catch (IllegalAccessException e)
      {
        Errors.fatal("Error while launching JVM compiled code: " + e);
      }
    catch (InvocationTargetException e)
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
  }

}

/* end of file */
