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
 * Tokiwa GmbH, Berlin
 *
 * Source of class Feature
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import dev.flang.ir.Clazz;


/**
 * JavaInterface <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class JavaInterface
{


  /*-----------------------------  methods  -----------------------------*/


  static Value getStaticField(String clazz,
                              String field,
                              Clazz  resultClass)
  {
    Value result;
    try
      {
        Class cl = Class.forName(clazz);
        Field f = cl.getDeclaredField(field);
        if (!Modifier.isStatic(f.getModifiers()))
          {
            System.err.println("fusion.java.getStaticField called for field "+f+" which is not static");
            System.exit(1);
          }
        Class t = f.getType();
        if (t.isPrimitive())
          {
            System.err.println("fusion.java.getStaticField called for field of primitive type, which is not yet supported");
            System.exit(1);
            result = null;
          }
        else
          {
            Object value = f.get(null);
            result = javaObjectToInstance(value,resultClass);
          }
      }
    catch (IllegalAccessException e)
      {
        System.err.println("IllegalAccessException when calling fusion.java.getStaticField for field "+clazz+"."+field);
        System.exit(1);
        result = null;
      }
    catch (ClassNotFoundException e)
      {
        System.err.println("ClassNotFoundException when calling fusion.java.getStaticField for field "+clazz+"."+field);
        System.exit(1);
        result = null;
      }
    catch (NoSuchFieldException e)
      {
        System.err.println("NoSuchFieldException when calling fusion.java.getStaticField for field "+clazz+"."+field);
        System.exit(1);
        result = null;
      }
    return result;
  }


  /**
   * forName load a class with a given name using this accessible
   * object's class loader.
   *
   * @param name the class name using "." as separator between package
   * and class name.
   *
   * @return the loaded class.
   */
  static Class forName(String name)
  {
    Class result;
    try
      {
        result = Class.forName(name);
      }
    catch (ClassNotFoundException e)
      {
        result = null;
      }
    return result;
  }



  /**
   * str2type converts a type descriptor of a field into the correpsonding type.
   *
   * @param str a type descriptor, e.g. "Z", "Ljava/lang/String;".
   *
   * @return the type, e.g. Boolean.TYPE, String.class, etc.
   */
  static Class str2type(String str) {
    switch (str.charAt(0)) {
    case 'Z': return Boolean.TYPE;
    case 'B': return Byte.TYPE;
    case 'C': return Character.TYPE;
    case 'S': return Short.TYPE;
    case 'I': return Integer.TYPE;
    case 'J': return Long.TYPE;
    case 'F': return Float.TYPE;
    case 'D': return Double.TYPE;
    case 'V': return Void.TYPE;
    case '[': return forName(str                            .replace('/','.'));
    case 'L': return forName(str.substring(1,str.length()-1).replace('/','.'));
    }
    return null;
  }


  /**
   * getEnd find the end of a type string starting at index i of d.
   *
   * @param d the descriptor string
   *
   * @param i the current index
   *
   * @return the index after the last index of the subtype.
   */
  static int getEnd(String d, int i) { // end of a sub-type in descriptor
    while (d.charAt(i) == '[') {
      i++;
    }
    if (d.charAt(i) == 'L') {
      return d.indexOf(';',i)+1;
    } else {
      return i+1;
    }
  }


  /**
   * getPars determines an array of parameter types of a given
   * signature.
   *
   * @return a new parameter type array.
   */
  static Class[] getPars(String d)
  {
    Class[] result;

    /* count parameters: */
    int cnt = 0;
    int i = 1;
    while (d.charAt(i)!=')')
      {
        int e = getEnd(d,i);
        if (e <= i)
          {
            return null;
          }
        cnt++;
        i = e;
      }

    result = new Class[cnt];

    /* get parameters: */
    cnt = 0;
    i = 1;
    while (d.charAt(i)!=')')
      {
        int e = getEnd(d,i);
        result[cnt] = str2type(d.substring(i,e));
        cnt++;
        i = e;
      }
    return result;
  }

  static Object instanceToJavaObject(Instance i)
  {
    Object result;
    if (i == null)
      {
        result = i;
      }
    else if (i.string != null)
      {
        result = i.string;
      }
    else if (i.javaRef != null)
      {
        result = i.javaRef;
      }
    else
      {
        System.err.println("JavaInterface.instanceToJavaObject: cannot convert instance to Java: "+i);
        System.exit(1);
        result = null;
      }
    return result;
  }

  static Instance javaObjectToInstance(Object o, Clazz resultClass)
  {
    Instance result;
    if (o == null)
      {
        result = null;
      }
    else if (o instanceof String)
      {
        System.err.println("javaObjectToInstance: String conversion NYI");
        System.exit(1);
        result = null;
      }
    else
      {
        Instance inst = new Instance(resultClass);
        inst.javaRef = o;
        result = inst;
        var resultFeat = resultClass._type.featureOfType();
        var resultOuter = resultFeat.outer();
        if (resultOuter != null)
          {
            Interpreter.setOuter(resultFeat, resultClass, inst, Interpreter.getSingletonInstance(resultOuter));
          }
      }
    return result;
  }


  static Instance callVirtual(String name, String sig, Object thiz, Instance argI)
  {
    Instance result;
    Object res;
    Method m;
    Class[] p = getPars(sig);
    if (p == null)
      {
        System.err.println("could not parse signature >>"+sig+"<<");
        System.exit(1);
      }
    try
      {
        m = thiz.getClass().getMethod(name,p);
      }
    catch (NoSuchMethodException e)
      {
        System.err.println("NoSuchMethodException when calling fusion.java.callVirtual for field "+thiz.getClass()+"."+name+sig);
        System.exit(1);
        m = null;
      }
    if (m == null)
      {
        System.err.println("fusion.java.callVirtual: method "+name+sig+" not found in target "+thiz.getClass());
        System.exit(1);
      }
    Object arg = instanceToJavaObject(argI);
    try
      {
        res = m.invoke(thiz,new Object[] { arg });
      }
    catch (IllegalAccessException e)
      {
        System.err.println("fusion.java.callVirtual: method "+name+sig+" causes IllegalAccessException");
        System.exit(1);
        res = null;
      }
    catch (InvocationTargetException e)
      {
        System.err.println("fusion.java.callVirtual: method "+name+sig+" causes InvocationTargetException");
        System.exit(1);
        res = null;
      }
    result = javaObjectToInstance(res,null);
    return result;
  }


}

/* end of file */
