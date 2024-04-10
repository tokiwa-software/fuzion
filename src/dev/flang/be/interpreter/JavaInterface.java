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
 * Source of class JavaInterface
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import dev.flang.air.Clazz; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.air.Clazzes; // NYI: remove dependency! Use dev.flang.fuir instead.

import dev.flang.util.ANY;
import dev.flang.util.Errors;


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
        Errors.fatal("IllegalAccessException when calling fuzion.java.get_static_field for field "+clazz+"."+field);
        result = null;
      }
    catch (ClassNotFoundException e)
      {
        Errors.fatal("ClassNotFoundException when calling fuzion.java.get_static_field for field "+clazz+"."+field);
        result = null;
      }
    catch (NoSuchFieldException e)
      {
        Errors.fatal("NoSuchFieldException when calling fuzion.java.get_static_field for field "+clazz+"."+field);
        result = null;
      }
    return result;
  }


  /**
   * Extract Java object from an Instance of fuzion.java.Java_Object
   *
   * @param i an instance, must be of a clazz that inherits
   * fuzion.java.Java_Object
   */
  static Object instanceToJavaObject(Instance i)
  {
    var res = ((JavaRef)i.refs[0])._javaRef;
    if (res != null)
      {
        // convert Value[] containing Java instances into corresponding Java array
        if (res instanceof ValueWithClazz[] va)
          {
            var oa = new Object[va.length];
            for (var ix = 0; ix < va.length; ix++)
              {
                if (va[ix].clazz() == Clazzes.i8.get())
                  {
                    oa[ix] = va[ix].i8Value();
                  }
                else if (va[ix].clazz() == Clazzes.i16.get())
                  {
                    oa[ix] = va[ix].i16Value();
                  }
                else if (va[ix].clazz() == Clazzes.i32.get())
                  {
                    oa[ix] = va[ix].i32Value();
                  }
                else if (va[ix].clazz() == Clazzes.i64.get())
                  {
                    oa[ix] = va[ix].i64Value();
                  }
                else if (va[ix].clazz() == Clazzes.u8.get())
                  {
                    oa[ix] = va[ix].u8Value();
                  }
                else if (va[ix].clazz() == Clazzes.u16.get())
                  {
                    oa[ix] = va[ix].u16Value();
                  }
                else if (va[ix].clazz() == Clazzes.u32.get())
                  {
                    oa[ix] = va[ix].u32Value();
                  }
                else if (va[ix].clazz() == Clazzes.u64.get())
                  {
                    oa[ix] = va[ix].u64Value();
                  }
                else if (va[ix].clazz() == Clazzes.f32.get())
                  {
                    oa[ix] = va[ix].f32Value();
                  }
                else if (va[ix].clazz() == Clazzes.f64.get())
                  {
                    oa[ix] = va[ix].f64Value();
                  }
                else if (va[ix].clazz() == Clazzes.bool.get())
                  {
                    oa[ix] = va[ix].boolValue();
                  }
                else
                  {
                    oa[ix] = instanceToJavaObject(va[ix].instance());
                  }
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
   * Extract Java object from a fuzion.sys.Pointer stored by a JavaRef
   *
   * @param r a JavaRef, must be a fuzion.sys.Pointer
   */
  static Object javaRefToJavaObject(JavaRef r)
  {
    var res = r._javaRef;
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
                    else if (!ec.isAssignableFrom(nc))
                      {
                        ec = Object.class;
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
   * @param resultClazz the clazz to wrap o into.  Must be either an heir of
   * 'fuzion.java.Java_Object' or 'outcome&lt;X&gt;' where 'X' is an heir of
   * 'fuzion.java.Java_Object'.
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
   * @param resultClazz the clazz to wrap o into.  Must be either an heir of
   * 'fuzion.java.Java_Object' or 'outcome&lt;X&gt;' where 'X' is an heir of
   * 'fuzion.java.Java_Object'.
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

    if (resultClazz == Clazzes.i8.getIfCreated())
      {
        return o instanceof Byte b ? new i8Value(b): new i8Value(((Value) o).i8Value());
      }
    else if (resultClazz == Clazzes.u16.getIfCreated())
      {
        return o instanceof Character c ? new u16Value(c): new u16Value(((Value) o).u16Value());
      }
    else if (resultClazz == Clazzes.i16.getIfCreated())
      {
        return o instanceof Short s ? new i16Value(s): new i16Value(((Value) o).i16Value());
      }
    else if (resultClazz == Clazzes.i32.getIfCreated())
      {
        return o instanceof Integer i ? new i32Value(i): new i32Value(((Value) o).i32Value());
      }
    else if (resultClazz == Clazzes.i64.getIfCreated())
      {
        return o instanceof Long j ? new i64Value(j): new i64Value(((Value) o).i64Value());
      }
    else if (resultClazz == Clazzes.f32.getIfCreated())
      {
        return o instanceof Float f ? new f32Value(f.floatValue()): new f32Value(((Value) o).f32Value());
      }
    else if (resultClazz == Clazzes.f64.getIfCreated())
      {
        return o instanceof Double d ? new f64Value(d.doubleValue()): new f64Value(((Value) o).f64Value());
      }
    else if (resultClazz == Clazzes.bool.getIfCreated())
      {
        return o instanceof Boolean z ? new boolValue(z): new boolValue(((Value) o).boolValue());
      }
    else if (resultClazz == Clazzes.c_unit.getIfCreated() && o == null             ) { return new Instance(resultClazz); }
    else
      {
        var result = new Instance(resultClazz);
        for (var e : Layout.get(resultClazz)._offsets.entrySet())
          {
            var f = e.getKey().feature();
            var off = (Integer) e.getValue();
            var v = switch (f.featureName().baseName())
              {
              case "Java_Ref"   -> new JavaRef(o);
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
    result.refs[0] = Interpreter.value(e.getMessage().toString());

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
   * Convert an instance of 'fuzion.sys.array<fuzion.sys.Pointer>' to a
   * Java Object[] with the corresponding Java values.
   *
   * @param v a value of type ArrayData as it is stored in 'fuzion.sys.array.data'.
   *
   * @return corresponding Java array.
   */
  static Object[] javaRefToJavaObjects(Value v)
  {
    var a = v.arrayData();
    var sz = a.length();
    var result = new Object[sz];
    for (var ix = 0; ix < sz; ix++)
      {
        result[ix] = javaRefToJavaObject((JavaRef)(((Object[])a._array)[ix]));
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
    var  p = dev.flang.util.JavaInterface.getPars(sig);
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
        Errors.fatal("ClassNotFoundException when calling fuzion.java.call_static/call_constructor for class " +
                           clName + " calling " + (name == null ? "new " + clName : name ) + sig);
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
        Errors.fatal("NoSuchMethodException when calling fuzion.java.call_static/call_virtual/call_constructor calling " +
                           (name == null ? "new " + clName : (cl.getName() + "." + name)) + sig);
      }
    Object[] argz = javaRefToJavaObjects(args);
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
