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
 * Source of class Feature
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import dev.flang.ast.Types;

import dev.flang.air.Clazz;
import dev.flang.air.Clazzes;

import dev.flang.util.ANY;


/**
 * JavaInterface <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class JavaInterface extends ANY
{


  /*-----------------------------  methods  -----------------------------*/


  static Value getField(String clazz,
                        Object thiz,
                        String field,
                        Clazz  resultClass)
  {
    Value result;
    try
      {
        Class cl = clazz != null ? Class.forName(clazz) : thiz.getClass();
        Field f = cl.getDeclaredField(field);
        Object value = f.get(thiz);
        result = javaObjectToInstance(value, resultClass);
      }
    catch (IllegalAccessException e)
      {
        System.err.println("IllegalAccessException when calling fuzion.java.getStaticField for field "+clazz+"."+field);
        System.exit(1);
        result = null;
      }
    catch (ClassNotFoundException e)
      {
        System.err.println("ClassNotFoundException when calling fuzion.java.getStaticField for field "+clazz+"."+field);
        System.exit(1);
        result = null;
      }
    catch (NoSuchFieldException e)
      {
        System.err.println("NoSuchFieldException when calling fuzion.java.getStaticField for field "+clazz+"."+field);
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


  /**
   * Extract Java object from an Instance of fuzion.java.JavaObject
   *
   * @param i an instance, must be of a clazz that inherits
   * fuzion.java.JavaObject
   */
  static Object instanceToJavaObject(Instance i)
  {
    var res = ((JavaRef)i.refs[0])._javaRef;
    if (res != null)
      {
        // convert Value[] containing Java instances into corresponding Java array
        if (res instanceof Value[] va)
          {
            var oa = new Object[va.length];
            for (var ix = 0; ix < va.length; ix++)
              {
                oa[ix] = instanceToJavaObject((Instance) va[ix]);
              }

            // find most general array element clazz ec
            Class ec = null;
            for (var ix = 0; ix < va.length; ix++)
              {
                if (oa[ix] != null)
                  {
                    var nc = oa[ix].getClass();
                    if (ec == null || nc.isAssignableFrom(ec))
                      {
                        ec = nc;
                      }
                  }
              }

            if (ec != null && ec != Object.class)
              {
                res = Array.newInstance(ec , va.length);
                System.arraycopy(oa, 0, res, 0, oa.length);
              }
            else
              {
                res = oa;
              }
          }
      }
    return res;
  }


  /**
   * Wrap Java object into an instance of resultClazz
   *
   * @param o a Java object
   *
   * @param resultClazz the clazz to wrap o into.  Must be either a heir of
   * 'fuzion.java.JavaObject' or 'outcome&lt;X&gt;' where 'X' is a heir of
   * 'fuzion.java.JavaObject'.
   *
   * @return a value of resultClazz that contains o.
   */
  static Value javaObjectToInstance(Object o, Clazz resultClazz)
  {
    return javaObjectToInstance(o, null, resultClazz);
  }


  /**
   * Wrap Java object or an exception into an instance of resultClazz.  In case
   * e!=null and resultClazz is not 'outcome', throws an error since we cannot
   * wrap the exception.
   *
   * @param o a Java object
   *
   * @param e a Java exception
   *
   * @param resultClazz the clazz to wrap o into.  Must be either a heir of
   * 'fuzion.java.JavaObject' or 'outcome&lt;X&gt;' where 'X' is a heir of
   * 'fuzion.java.JavaObject'.
   *
   * @return a value of resultClazz that contains o or, in case e!=null, e.
   */
  static Value javaObjectToInstance(Object o, Throwable e, Clazz resultClazz)
  {
    if (PRECONDITIONS) require
      (resultClazz != null);

    Value result;
    var ok = e == null;
    if (resultClazz.feature().qualifiedName().equals("outcome"))
      {
        var valClazz = resultClazz._choiceGenerics.get(ok ? 0 : 1);
        var res = ok ? javaObjectToPlainInstance(o, valClazz)
                     : javaThrowableToError     (e, valClazz);
        result = Interpreter.tag(resultClazz, valClazz, res);
      }
    else if (ok)
      {
        result = javaObjectToPlainInstance(o, resultClazz);
      }
    else
      { // NYI: Instead of throwing an exception, cause a panic and stop the
        // current thread in an orderly way.
        throw new Error("Java code returned with unexpected exception: " + e, e);
      }
    return result;
  }


  /**
   * Convert a Java object returned from a reflection call to the corresponding
   * Fuzion value.
   *
   * @param o a Java Object
   *
   * @param resultClazz a clazz like i32, i64, Java.java.lang.String, etc.
   *
   * @return a new value that represents o
   */
  static Value javaObjectToPlainInstance(Object o, Clazz resultClazz)
  {
    if (PRECONDITIONS) require
      (resultClazz != null);

    if      (resultClazz == Clazzes.i8 .getIfCreated() && o instanceof Byte      b) { return new i8Value(b); }
    else if (resultClazz == Clazzes.u16.getIfCreated() && o instanceof Character c) { return new u16Value(c); }
    else if (resultClazz == Clazzes.i16.getIfCreated() && o instanceof Short     s) { return new i16Value(s); }
    else if (resultClazz == Clazzes.i32.getIfCreated() && o instanceof Integer   i) { return new i32Value(i); }
    else if (resultClazz == Clazzes.i64.getIfCreated() && o instanceof Long      j) { return new i64Value(j); }
    else if (resultClazz == Clazzes.f32.getIfCreated() && o instanceof Float     f) { return new f32Value(f.floatValue()); }
    else if (resultClazz == Clazzes.f64.getIfCreated() && o instanceof Double    d) { return new f64Value(d.doubleValue()); }
    else if (resultClazz == Clazzes.bool  .getIfCreated() && o instanceof Boolean z) { return new boolValue(z); }
    else if (resultClazz == Clazzes.c_unit.getIfCreated() && o == null             ) { return new Instance(resultClazz); }
    else
      {
        var result = new Instance(resultClazz);
        for (var e : Layout.get(resultClazz)._offsets0.entrySet())
          {
            var f = e.getKey();
            var off = (Integer) e.getValue();
            var v = switch (f.featureName().baseName())
              {
              case "javaRef"   -> new JavaRef(o);
              case "forbidden" -> Value.NO_VALUE;
              default -> f.isOuterRef() ? new Instance(resultClazz._outer)
                                        : (Value) (Object) new Object() { { if (true) throw new Error("unexpected field in fuzion.java.Array: "+f.qualifiedName()); }};
              };
            if (v != Value.NO_VALUE)
              {
                result.refs[off] = v;
              }
          }
        return result;
      }
  }


  /**
   * Wrap a Java exception into an instance of 'error'.
   *
   * @param e the Java exception, must not be null,
   */
  static Value javaThrowableToError(Throwable e, Clazz resultClazz)
  {
    if (PRECONDITIONS) require
      (e != null,
       resultClazz != null);

    var result = new Instance(resultClazz);
    if (CHECKS) check
      (result.refs.length == 1);    // an 'error' has exactly one ref field of type string
    result.refs[0] = Interpreter.value(e.toString());

    return result;
  }


  /**
   * Convert an instance of 'fuzion.sys.array<Object>' to a Java Object[] with
   * the corresponding Java values.
   *
   * @param v a value of type ArrayData as it is stored in 'fuzion.sys.array.data'.
   *
   * @return corresponding Java array.
   */
  static Object[] instanceToJavaObjects(Value v)
  {
    var a = v.arrayData();
    var sz = a.length();
    var result = new Object[sz];
    for (var ix = 0; ix < sz; ix++)
      {
        result[ix] = instanceToJavaObject((Instance)(((Object[])a._array)[ix]));
      }
    return result;
  }


  /**
   * Call virtual or static Java method or constructor
   *
   * @param clName name of the class that declares the method or constructor.
   *
   * @param name name the method, null to call constructor
   *
   * @param sig Java signature of the method or constructor
   *
   * @param thiz target instance for a virtual call, null for static method or
   * constructor call
   *
   * @param args array of arguments to be passed to the method or constructor,
   * must be of type array data, i.e., the value in fuzion.sys.array<JavaObject>.data.
   *
   * @param resultClazz the result type of the constructed instance
   */
  static Value call(String clName, String name, String sig, Object thiz, Value args, Clazz resultClazz)
  {
    if (PRECONDITIONS) require
      (clName != null);

    Object res = null;
    Throwable err = null;
    Method m = null;
    Constructor co = null;
    var  p = getPars(sig);
    if (p == null)
      {
        System.err.println("could not parse signature >>"+sig+"<<");
        System.exit(1);
      }
    Class cl;
    try
      {
        cl = Class.forName(clName);
      }
    catch (ClassNotFoundException e)
      {
        System.err.println("ClassNotFoundException when calling fuzion.java.callStatic/callConstructor for class " +
                           clName + " calling " + (name == null ? "new " + clName : name ) + sig);
        System.exit(1);
        cl = Object.class; // not reached.
      }
    try
      {
        if (name == null)
          {
            co = cl.getConstructor(p);
          }
        else
          {
            m = cl.getMethod(name,p);
          }
      }
    catch (NoSuchMethodException e)
      {
        System.err.println("NoSuchMethodException when calling fuzion.java.callStatic/callVirtual/callConstructor calling " +
                           (name == null ? "new " + clName : (cl.getName() + "." + name)) + sig);
        System.exit(1);
      }
    Object[] argz = instanceToJavaObjects(args);
    try
      {
        for (var i = 0; i < argz.length; i++)
          {
            var pi = p[i];
            var ai = argz[i];
            // in case parameter type is some array and argument is empty array,
            // the type of the argument derived form the elements will be
            // Object[], so we create a more specific array:
            if (pi.isArray() && ai != null && Array.getLength(ai) == 0 && pi != ai.getClass())
              {
                argz[i] = Array.newInstance(pi.componentType(), 0);
              }
          }
        res = (name == null) ? co.newInstance(argz) : m.invoke(thiz, argz);
      }
    catch (InvocationTargetException e)
      {
        err = e.getCause();
      }
    catch (InstantiationException | IllegalAccessException e)
      {
        err = e;
      }
    return javaObjectToInstance(res, err, resultClazz);
  }


}

/* end of file */
