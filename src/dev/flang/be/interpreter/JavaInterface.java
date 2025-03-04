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

import dev.flang.fuir.SpecialClazzes;
import dev.flang.util.Errors;


/**
 * JavaInterface
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class JavaInterface extends FUIRContext
{


  /*-----------------------------  methods  -----------------------------*/


  static Value getField(String clazz,
                        Object thiz,
                        String field,
                        int  resultClass)
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


  static void setField(String clazz,
                        Object thiz,
                        String field,
                        Object value)
  {
    try
      {
        Class cl = clazz != null ? Class.forName(clazz) : thiz.getClass();
        Field f = cl.getDeclaredField(field);
        f.set(cl, value);
      }
    catch (IllegalAccessException e)
      {
        Errors.fatal("IllegalAccessException when calling fuzion.java.get_static_field for field "+clazz+"."+field);
      }
    catch (ClassNotFoundException e)
      {
        Errors.fatal("ClassNotFoundException when calling fuzion.java.get_static_field for field "+clazz+"."+field);
      }
    catch (NoSuchFieldException e)
      {
        Errors.fatal("NoSuchFieldException when calling fuzion.java.get_static_field for field "+clazz+"."+field);
      }
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
  static Value javaObjectToInstance(Object o, int resultClazz)
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
  static Value javaObjectToInstance(Object o, Throwable e, int resultClazz)
  {
    if (PRECONDITIONS) require
      (resultClazz > 0);

    Value result;
    var ok = e == null;
    // NYI: HACK:
    if (fuir().clazzAsString(resultClazz).startsWith("outcome"))
      {
        var valClazz = fuir().clazzChoice(resultClazz, ok ? 0 : 1);
        var res = ok ? javaObjectToPlainInstance(o, valClazz)
                     : javaThrowableToError     (e, valClazz);
        result = Interpreter.tag(resultClazz, valClazz, res, ok ? 0 : 1);
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
  static Value javaObjectToPlainInstance(Object o, int resultClazz)
  {
    if (PRECONDITIONS) require
      (resultClazz > 0);

    if (resultClazz == fuir().clazz(SpecialClazzes.c_i8))
      {
        return o instanceof Byte b ? new i8Value(b): new i8Value(((Value) o).i8Value());
      }
    else if (resultClazz == fuir().clazz(SpecialClazzes.c_u8))
      {
        return o instanceof Byte b ? new u8Value(b): new u8Value(((Value) o).u8Value());
      }
    else if (resultClazz == fuir().clazz(SpecialClazzes.c_u16))
      {
        return o instanceof Character c ? new u16Value(c): new u16Value(((Value) o).u16Value());
      }
    else if (resultClazz == fuir().clazz(SpecialClazzes.c_i16))
      {
        return o instanceof Short s ? new i16Value(s): new i16Value(((Value) o).i16Value());
      }
    else if (resultClazz == fuir().clazz(SpecialClazzes.c_u32))
      {
        return o instanceof Integer i ? new u32Value(i): new u32Value(((Value) o).u32Value());
      }
    else if (resultClazz == fuir().clazz(SpecialClazzes.c_i32))
      {
        return o instanceof Integer i ? new i32Value(i): new i32Value(((Value) o).i32Value());
      }
    else if (resultClazz == fuir().clazz(SpecialClazzes.c_i64))
      {
        return o instanceof Long j ? new i64Value(j): new i64Value(((Value) o).i64Value());
      }
    else if (resultClazz == fuir().clazz(SpecialClazzes.c_u64))
      {
        return o instanceof Long j ? new u64Value(j): new u64Value(((Value) o).u64Value());
      }
    else if (resultClazz == fuir().clazz(SpecialClazzes.c_f32))
      {
        return o instanceof Float f ? new f32Value(f.floatValue()): new f32Value(((Value) o).f32Value());
      }
    else if (resultClazz == fuir().clazz(SpecialClazzes.c_f64))
      {
        return o instanceof Double d ? new f64Value(d.doubleValue()): new f64Value(((Value) o).f64Value());
      }
    else if (resultClazz == fuir().clazz(SpecialClazzes.c_bool))
      {
        return o instanceof Boolean z ? new boolValue(z): new boolValue(((Value) o).boolValue());
      }
    else if (resultClazz == fuir().clazz(SpecialClazzes.c_unit) && o == null             ) { return new Instance(resultClazz); }
    // NYI: UNDER DEVELOPMENT: remove this, abusing javaObjectToPlainInstance in mtx_*, cnd_* intrinsics
    else if (resultClazz == fuir().clazz(SpecialClazzes.c_Array)) { return new JavaRef(o); }
    else if (resultClazz == fuir().clazz(SpecialClazzes.c_Mutex)) { return new JavaRef(o); }
    else if (resultClazz == fuir().clazz(SpecialClazzes.c_Condition)) { return new JavaRef(o); }
    else if (resultClazz == fuir().clazz(SpecialClazzes.c_File_Descriptor)) { return new JavaRef(o); }
    else if (resultClazz == fuir().clazz(SpecialClazzes.c_Directory_Descriptor)) { return new JavaRef(o); }
    else if (resultClazz == fuir().clazz(SpecialClazzes.c_Mapped_Memory)) { return new JavaRef(o); }
    else
      {
        var result = new Instance(resultClazz);
        for (var e : Layout.get(resultClazz)._offsets.entrySet())
          {
            var f = e.getKey();
            var off = (Integer) e.getValue();
            var v = switch (fuir().clazzBaseName(f))
              {
              case "java_ref"   -> new JavaRef(o);
              case "forbidden" -> Value.NO_VALUE;
              default -> fuir().clazzIsOuterRef(f) ? new Instance(fuir().clazzOuterClazz(resultClazz))
                                        : (Value) (Object) new Object() { { if (true) throw new Error("unexpected field in fuzion.java.Array: "+fuir().clazzAsString(f)); }};
              };
            if (v != Value.NO_VALUE && /* NYI: HACK: */ result.refs.length > off)
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
  static Value javaThrowableToError(Throwable e, int resultClazz)
  {
    if (PRECONDITIONS) require
      (e != null,
       resultClazz > 0);

    var result = new Instance(resultClazz);
    if (CHECKS) check
      (result.refs.length == 1);    // an 'error' has exactly one ref field of type string
    result.refs[0] = Interpreter.boxedConstString(e.getMessage().toString());

    return result;
  }


  /**
   * Convert an instance of {@code fuzion.sys.array<Array>} to a
   * Java {@code Object[]} with the corresponding Java values.
   *
   * @param v a value of type ArrayData as it is stored in {@code fuzion.sys.array.data}.
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
        result[ix] = ((JavaRef)(((Object[])a._array)[ix]))._javaRef;
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
   * must be of type array data, i.e., the value in {@code fuzion.sys.array<JavaObject>.data}.
   *
   * @param resultClazz the result type of the constructed instance
   */
  static Value call(String clName, String name, String sig, Object thiz, Value args, int resultClazz)
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
    catch (Throwable e)
      {
        err = e;
      }
    return javaObjectToInstance(res, err, resultClazz);
  }


}

/* end of file */
