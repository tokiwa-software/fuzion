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
 * Source of class ForClass
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools.fzjava;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;

import java.util.TreeMap;


/**
 * ForClass performs feature generation for one Java class.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class ForClass extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Some public members in a Java module may return a result type or expect an
   * argument that that is not public.  This flag enables warnings in this case.
   */
  static final boolean SHOW_WARNINGS_FOR_NON_PUBLIC_TYPES = false;


  /**
   * Feature suffix used for declaration of instance methods and fields
   */
  static String DYNAMIC_SUFFIX = "";


  /**
   * Feature suffix used for declaration of unit type containing static methods
   * and fields
   */
  static String STATIC_SUFFIX  = "_static";


  /**
   * File name suffix used for declaration of routine that returns instance of
   * unit type containing static methods and fields.
   */
  static String UNIT_SUFFIX    = "_unit";


  /*----------------------------  variables  ----------------------------*/


  /**
   * The original Java class
   */
  Class _class;


  /**
   * The super class if this exists as a fuzion wrapper. null if no super
   * class or no wrapper.
   */
  ForClass _superClass;


  /**
   * Map from parameters-signature to Method to find what method
   * to use if several methods differ only in their result type.
   */
  TreeMap<String, Method> _generateM = new TreeMap<>();


  /**
   * Set of overloaded methods, mapping name combined with number or
   * parameters (name + " " + n) to Methods.  In case of overloading, the
   * Method is always the preferred method to use a short name.
   */
  TreeMap<String, Method> _overloadedM = new TreeMap<>();


  /**
   * Set of constructors to generate code for
   */
  List<Constructor> _generateC = new List<>();


  /**
   * Set of overloaded constructors, mapping name combined with number or
   * parameters (name + " " + n) to Constructors.  In case of overloading, the
   * the mapped value is always the preferred constructor to use a short name.
   */
  TreeMap<Integer, Constructor> _overloadedC = new TreeMap<>();


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor to create ForClass instance for given class c.
   *
   * @param c the class.
   *
   * @param sc the super class or null.
   */
  ForClass(Class c, ForClass sc)
  {
    _class = c;
    _superClass = sc;
    for (var me : c.getMethods())
      {
        findMethod(me);
      }
    for (var co : c.getConstructors())
      {
        findConstructor(co);
      }
  }


  /*-----------------------------  methods  -----------------------------*/



  /**
   * Find Java methods to generate code for
   *
   * @param me the method to create fuzion code for
   */
  void findMethod(Method me)
  {
    if (me.getDeclaringClass() == _class &&
        (me.getModifiers() & Modifier.PUBLIC) != 0)
      {
        var pa = me.getParameters();
        var p = formalParameters(pa);
        var r = resultType(me);
        if ((me.getModifiers() & Modifier.STATIC) == 0 &&
            p != null &&
            r != null)
          {
            var jn = me.getName();
            var jp = signature(pa);
            var fn = fuzionName(jn, jp);
            var existing = _generateM.get(fn);
            if (existing == null || !preferred(existing, me))
              {
                _generateM.put(fn, me);
              }
            if (pa.length > 0)
              {
                String nn = jn + " " + pa.length;
                var existingOver = _overloadedM.get(nn);
                if (existingOver == null || !preferred(existingOver, me))
                  {
                    _overloadedM.put(nn, me);
                  }
              }
          }
        else
          {
            // NYI: instance methods, methods with non-empty parameter lists, methods with non-void result not supported
          }
      }
  }


  /**
   * Find Java constructors to generate code for
   *
   * @param c the constructor to create fuzion code for
   */
  void findConstructor(Constructor co)
  {
    if ((co.getModifiers() & Modifier.PUBLIC) != 0)
      {
        var pa = co.getParameters();
        var fp = formalParameters(pa);            // Fuzion parameters
        if (fp != null)
          {
            _generateC.add(co);
            if (pa.length > 0)
              {
                Integer nn = pa.length;
                var existingOver = _overloadedC.get(nn);
                if (existingOver == null || !preferred(existingOver, co))
                  {
                    _overloadedC.put(nn, co);
                  }
              }
          }
      }
  }


  /**
   * Check if the super class' wrapper feature already defines a short hand
   * 'name' with 'n' parameters.  If so, we do not add another short hand.
   */
  boolean inheritsShortHand(String name, int n)
  {
    var sc = _superClass;
    return sc != null && (sc._overloadedM.containsKey(name + " " + n) || sc.inheritsShortHand(name, n));
  }


  /**
   * Check if the super class' wrapper feature already defines a method with
   * given fuzionName 'fn'.  If so, we do not add another short hand.
   *
   * @param fn a name generated by a call to 'fuzionName()'
   */
  boolean inheritsMethod(String fn)
  {
    var sc = _superClass;
    return sc != null && (sc._generateM.containsKey(fn) || sc.inheritsMethod(fn));
  }


  /**
   * Write wrappers for this class.
   */
  void write(FZJava fzj)
  {
    var cn  = _class.getName();
    var ccn = FeatureWriter.cleanName(cn);
    var jfn = "Java/" + ccn.replace('.', '/');
    var jtn = typeName(_class);
    var n = jfn.substring(jfn.lastIndexOf("/") + 1);
    var fcn = FeatureWriter.mangle(n);
    var sc = _superClass == null ? null : _superClass._class;
    var inh = sc != null ? typeName(sc) + "(forbidden), " : "";
    var rf  = sc != null ? "redef " : "";
    StringBuilder data_dynamic = new StringBuilder(header(fzj, "Fuzion interface to instance members of Java instance class '" + cn + "'") +
                                                   jtn + "(" + rf + "forbidden void) ref : " + inh + "fuzion.java.JavaObject(forbidden) is\n");
    StringBuilder data_static  = new StringBuilder(header(fzj, "Fuzion interface to static members of Java class '" + cn + "'") +
                                                   jtn + STATIC_SUFFIX + " is\n");
    StringBuilder data_unit    = new StringBuilder(header(fzj, "Fuzion unit feature to call static members of Java class '" + cn + "'") +
                                                   jtn + " => " + jtn + STATIC_SUFFIX + "\n");
    for (var me : _generateM.values())
      {
        var pa = me.getParameters();
        var jn = me.getName();
        var jp = signature(pa);
        var fn = fuzionName(jn, jp);
        if (!inheritsMethod(fn))
          {
            processMethod(me, fcn, data_dynamic, data_static);
          }
      }
    for (var me : _overloadedM.values())
      {
        if (!inheritsShortHand(me.getName(), me.getParameterTypes().length))
          {
            shortHand(me, data_dynamic);
          }
      }
    for (var fi : _class.getFields())
      {
        if (fi.getDeclaringClass() == _class)
          {
            processField(fi, cn, n, data_dynamic, data_static);
          }
      }
    for (var co : _generateC)
      {
        processConstructor(co, fcn, data_static);
      }
    for (var co : _overloadedC.values())
      {
        shortHand(co, fcn, data_static);
      }

    fzj.createOuter(jfn);
    FeatureWriter.write(fzj, jfn, DYNAMIC_SUFFIX, data_dynamic.toString());
    FeatureWriter.write(fzj, jfn, STATIC_SUFFIX , data_static .toString());
    FeatureWriter.write(fzj, jfn, UNIT_SUFFIX   , data_unit   .toString());
  }


  /**
   * Create Fuzion header for a generated source file.
   *
   * @param main the main comment describing the generated file, must fit in a single line
   */
  String header(FZJava fzj, String main)
  {
    return
      "# " + main + "\n" +
      "#\n" +
      "# !!!!!!  DO NOT EDIT, GENERATED CODE !!!!!!\n" +
      "#\n" +
      "# This code was generated automatically using the " + FZJava.FZJAVA_TOOL + " tool V" + fzj.version() + " called \n" +
      "# as follows:\n" +
      "#\n" +
      "#   " + fzj.command() + "\n" +
      "#\n";
  }


  /**
   * Fuzion name for a Java method with Java name jn and parameters signature jp
   *
   * @param jn the Java name, e.g., "indexOf"
   *
   * @param jp the Java parameters signature, e.g., "Ljava/lang/String;I"
   *
   * @return a mangled fuzion name for this method, e.g.,
   * "indexOf_Ljava_7_lang_7_String_s_I"
   */
  String fuzionName(String jn,
                    String jp)
  {
    return FeatureWriter.mangledCleanName(jn) + (jp == null || jp.length() == 0 ? "" : "_" + FeatureWriter.mangle(jp));
  }


  /**
   * Create Fuzion feature for given method
   *
   * @param me the method to create fuzion code for
   *
   * @param fcn is the mangled base name of the Java class not
   * including the package name, e.g. "String"
   *
   * @param data_dynamic the fuzion feature containing the instance members of
   * the class
   *
   * @param data_static the fuzion feature containing the static members of
   * the class
   */
  void processMethod(Method me,
                     String fcn,
                     StringBuilder data_dynamic,
                     StringBuilder data_static)
  {
    var pa = me.getParameters();
    var rt = me.getReturnType();
    var jn = me.getName();                    // Java name
    var js = signature(pa, rt);               // Java signature
    var jp = signature(pa);                   // Java signature of parameters
    var fp = formalParameters(pa);            // Fuzion parameters
    var fr = resultType(me);                  // Fuzion result type
    var fn = fuzionName(jn, jp);
    data_dynamic.append("\n" +
                        "  # call Java instance method '" + me + "':\n" +
                        "  #\n" +
                        "  " + fn + fp + " " + fr + " is\n" +
                        "    " + ("fuzion.java.callVirtual<" + fr + "> " +
                                  fuzionString(jn) + " " +
                                  fuzionString(js) +
                                  " " + fcn + ".this "+
                                  parametersArray(pa) + "\n")
                        );
  }


  /**
   * Create Fuzion feature for given constructors
   *
   * @param c the constructor to create fuzion code for
   *
   * @param fcn is the mangled base name of the Java class not
   * including the package name, e.g., "String"
   *
   * @param data_static the fuzion feature containing the static members of
   * the class
   */
  void processConstructor(Constructor co,
                          String fcn,
                          StringBuilder data_static)
  {
    var pa = co.getParameters();
    var js = signature(pa, Void.TYPE);        // Java signature
    var jp = signature(pa);                   // Java signature of parameters
    var fp = formalParameters(pa);            // Fuzion parameters
    var fr = fcn;                             // Fuzion result type
    var fn = fuzionName("new", jp);
    data_static.append("\n" +
                       "  # call Java constructor '" + co + "':\n" +
                       "  #\n" +
                       "  " + fn + fp + " " + fr + " is\n" +
                       "    " + ("fuzion.java.callConstructor<" + fr + "> " +
                                 fuzionString(co.getDeclaringClass().getName()) +
                                 fuzionString(js) + " " +
                                 parametersArray(pa) + "\n")
                       );
  }


  /**
   * Create Fuzion shortHand for given method.  A shortHand is a feature that
   * does not have the mangled Java signature as part of its name.  In case of
   * overloading, only one method will be chosen as a shortHand.
   *
   * @param me the method to create short hand fuzion code for
   *
   * @param data_dynamic the fuzion feature containing the instance members of
   * the class
   */
  void shortHand(Method me,
                 StringBuilder data_dynamic)
  {
    var pa = me.getParameters();
    var fp = formalParameters(pa);
    var jn = me.getName();
    var fr = resultType(me);                  // Fuzion result type
    var jp = signature(pa);                   // Java signature of parameters
    var fn0= fuzionName(jn, null);
    var fn = fuzionName(jn, jp);
    data_dynamic.append("\n" +
                        "  # short-hand to call Java instance method '" + me + "':\n" +
                        "  #\n" +
                        "  " + fn0 + fp + " " + fr + " is\n" +
                        "    " + fn + parametersList(pa) + "\n");
  }


  /**
   * Create Fuzion shortHand for given constructor.  A shortHand is a feature that
   * does not have the mangled Java signature as part of its name.  In case of
   * overloading, only one constructor will be chosen as a shortHand.
   *
   * @param co the constructor to create short hand fuzion code for
   *
   * @param fcn is the mangled base name of the Java class not including the
   * package name, e.g., "String"
   *
   * @param data_static the fuzion feature containing the static members of
   * the class
   */
  void shortHand(Constructor co,
                 String fcn,
                 StringBuilder data_static)
  {
    var pa = co.getParameters();
    var fp = formalParameters(pa);
    var jp = signature(pa);                   // Java signature of parameters
    var fr = fcn;                             // Fuzion result type
    var fn0= "new";
    var fn = fuzionName(fn0, jp);
    data_static.append("\n" +
                       "  # short-hand to call Java consctructor '" + co + "':\n" +
                       "  #\n" +
                       "  " + fn0 + fp + " " + fr + " is\n" +
                       "    " + fn + parametersList(pa) + "\n");
  }


  /**
   * For two overloaded methods that only differ in the result type, choose
   * which one should be preferred for a short-hand call: the first one (r1, true),
   * or the second one (r2, false).
   *
   * @param r1 the first result type
   *
   * @param r2 the second result type
   *
   * @return true iff the first one with result r1 is preferred, false otherwise.
   */
  boolean preferredResult(Class r1, Class r2)
  {
    return r2.isAssignableFrom(r1);
  }


  /**
   * For two overloaded parameter lists pa1 and pa2, choose which one should be
   * preferred for a short-hand call: the first one (pa1, true), or the second
   * one (pa2, false) or none (null)
   *
   * @param pa1 paramaters of first cantidate
   *
   * @param pa2 paramaters of second cantidate
   *
   * @return true, false or null.
   */
  Boolean preferredParameters(Parameter[] pa1, Parameter[] pa2)
  {
    if (PRECONDITIONS) require
      (pa1.length == pa2.length);

    for (int i = 0; i < pa1.length; i++)
      {
        var t1 = pa1[i].getType();
        var t2 = pa2[i].getType();
        if (t1 != t2)
          {
            if (t1 == String.class)
              {
                return true;
              }
            else if (t2 == String.class)
              {
                return false;
              }
            else if (t1 == Object.class)
              {
                return true;
              }
            else if (t2 == Object.class)
              {
                return false;
              }
            else
              {
                return true; // no clear preference, so choose the first we found.
              }
          }
      }
    return null;
  }


  /**
   * For two overloaded methods m1 and m2, choose which one should be preferred
   * for a short-hand call: the first one (pa1, true), or the second one (pa2,
   * false).
   *
   * @param m1 first cantidate
   *
   * @param m2 second cantidate
   *
   * @return true iff m1 is preferred.
   */
  boolean preferred(Method m1, Method m2)
  {
    if (PRECONDITIONS) require
      (m1.getParameters().length == m2.getParameters().length);

    var result = preferredParameters(m1.getParameters(),
                                     m2.getParameters());
    if (result != null)
      {
        return result;
      }
    var r1 = m1.getReturnType();
    var r2 = m2.getReturnType();
    return preferredResult(r1, r2);
  }


  /**
   * For two overloaded constructors c1 and c2, choose which one should be
   * preferred for a short-hand call: the first one (c1, true), or the second
   * one (c2, false).
   *
   * @param c1 first cantidate
   *
   * @param c2 second cantidate
   *
   * @return true iff c1 is preferred.
   */
  boolean preferred(Constructor c1, Constructor c2)
  {
    if (PRECONDITIONS) require
      (c1.getParameters().length == c2.getParameters().length);

    var result = preferredParameters(c1.getParameters(),
                                     c2.getParameters());
    if (result != null)
      {
        return result;
      }
    return true;  // no preference, so chose the first.
  }


  /**
   * Declare the formal parameters for a Fuzion feature that provides a link to
   * a Java method with the given parameters.
   *
   * @param pa array of parameters
   *
   * @return the Fuzion parameters list, e.g., "(arg0 string) "
   */
  String formalParameters(Parameter[] pa)
  {
    StringBuilder res = new StringBuilder();
    for (var p : pa)
      {
        var t = p.getType();
        res.append(res.length() == 0 ? "(" : ", ");
        var mp = FeatureWriter.mangledCleanName(p.getName());
        String mt = null;
        if ((t.getModifiers() & Modifier.PUBLIC) == 0)
          {
            return null;
          }
        else if (t.isArray())
          { // NYI: handle array types
            return null;
          }
        else if (t == Byte     .TYPE) { mt = "i8";        }
        else if (t == Character.TYPE) { mt = "u16";       }
        else if (t == Short    .TYPE) { mt = "i16";       }
        else if (t == Integer  .TYPE) { mt = "i32";       }
        else if (t == Long     .TYPE) { mt = "i64";       }
        else if (t == Float    .TYPE) { mt = "i32";       }  // NYI: should be f32
        else if (t == Double   .TYPE) { mt = "i64";       }  // NYI: should be f64
        else if (t == Boolean  .TYPE) { mt = "bool";      }
        else if (t == String.class  ) { mt = "string";    }
        else                          { mt = typeName(t); }
        res.append(mp).append(" ").append(mt);
      }
    if (!res.isEmpty())
      {
        res.append(")");
      }
    return res.toString();
  }


  /**
   * Get the Java signature string for a method with the given parameters and
   * return type.
   *
   * @param pa array of parameters
   *
   * @param returnType the result type
   *
   * @return the signature, e.g., "(Ljava/lang/String;IJ)V"
   */
  String signature(Parameter[] pa, Class returnType)
  {
    return "(" + signature(pa) + ")" + signature(returnType);
  }


  /**
   * Get the Java signature string for a method with the given parameters.
   *
   * @param pa array of parameters
   *
   * @return the signature, e.g., "Ljava/lang/String;IJ"
   */
  String signature(Parameter[] pa)
  {
    StringBuilder res = new StringBuilder();
    for (var p : pa)
      {
        var t = p.getType();
        res.append(signature(t));
      }
    return res.toString();
  }


  /**
   * Get the Java signature string for a given type
   *
   * @param t the tye
   *
   * @return the signature, e.g., "V"
   */
  String signature(Class t)
  {
    String res;
    if (t.isArray())
      { // NYI: handle array types
        res = "NYI:array";
      }
    else if (t == Byte     .TYPE) { res = "B"; }
    else if (t == Character.TYPE) { res = "C"; }
    else if (t == Short    .TYPE) { res = "S"; }
    else if (t == Integer  .TYPE) { res = "I"; }
    else if (t == Long     .TYPE) { res = "J"; }
    else if (t == Float    .TYPE) { res = "F"; }
    else if (t == Double   .TYPE) { res = "D"; }
    else if (t == Boolean  .TYPE) { res = "Z"; }
    else if (t == Void     .TYPE) { res = "V"; }
    else
      {
        res = "L" + t.getName().replace(".","/") + ";";
      }
    return res;
  }


  /**
   * Get a string containing code to create a Fuzion array containting Java
   * objects corresponding to all the parameters.
   *
   * @param pa array of parameters
   *
   * @return a string declaring such an array, e.g.,
   * "[fuzion.java.stringToJavaObject arg0]".
   */
  String parametersArray(Parameter[] pa)
  {
    StringBuilder res = new StringBuilder("[");
    for (var p : pa)
      {
        var t = p.getType();
        res.append(res.length() == 1 ? "" : "; ");
        var mp = FeatureWriter.mangledCleanName(p.getName());
        if (t.isArray())
          { // NYI: handle array types
            return null;
          }
        else if (t == Byte     .TYPE) { res.append("fuzion.java.i8ToJavaObject "    ).append(mp); }
        else if (t == Character.TYPE) { res.append("fuzion.java.u16ToJavaObject "   ).append(mp); }
        else if (t == Short    .TYPE) { res.append("fuzion.java.i16ToJavaObject "   ).append(mp); }
        else if (t == Integer  .TYPE) { res.append("fuzion.java.i32ToJavaObject "   ).append(mp); }
        else if (t == Long     .TYPE) { res.append("fuzion.java.i64ToJavaObject "   ).append(mp); }
        else if (t == Float    .TYPE) { res.append("fuzion.java.f32ToJavaObject "   ).append(mp); }
        else if (t == Double   .TYPE) { res.append("fuzion.java.f64ToJavaObject "   ).append(mp); }
        else if (t == Boolean  .TYPE) { res.append("fuzion.java.boolToJavaObject "  ).append(mp); }
        else if (t == String.class  ) { res.append("fuzion.java.stringToJavaObject ").append(mp); }
        else
          {
            res.append(mp);
          }
      }
    res.append("]");
    return res.toString();
  }


  /**
   * Get a string containing all the mangled, cleaned names of the parameters.
   * This is used to pass parameters from short-hand features to those with
   * fully mangled signature in their name.
   *
   * @param pa array of parameters
   *
   * @return a string with space-separated parameter names, e.g., "arg0 arg1"
   */
  String parametersList(Parameter[] pa)
  {
    StringBuilder res = new StringBuilder("");
    for (var p : pa)
      {
        res.append(" ");
        var mp = FeatureWriter.mangledCleanName(p.getName());
        res.append(mp);
      }
    return res.toString();
  }


  /**
   * Get the Fuzion result type corresponding to the return type of a Method,
   * wrapping it onto 'outcome' in case exceptions are thrown.
   *
   * @param me the Java Method
   *
   * @return the corresponding Fuzion type, e.g., "i32", "outcome<string>",
   * "Java.java.util.Vector".
   */
  String resultType(Method me)
  {
    var res = plainResultType(me.getReturnType());
    if (res != null)
      {
        var e = me.getExceptionTypes();
        if (e != null)
          {
            res = "outcome<" + res + ">";
          }
      }
    return res;
  }


  /**
   * Get the Fuzion result type corresponding to the return type of a Method.
   *
   * @param t a Java type, e.g., Integer.TYPE, String.class,
   * java.util.Vector.class
   *
   * @return the corresponding Fuzion type, e.g., "i32", "string",
   * "Java.java.util.Vector", null if not supported.
   */
  String plainResultType(Class t)
  {
    if ((t.getModifiers() & Modifier.PUBLIC) == 0)
      {
        if (SHOW_WARNINGS_FOR_NON_PUBLIC_TYPES)
          {
            Errors.warning("Used type '" + t + "' is not public");
          }
        return null;
      }
    else if (t == Byte     .TYPE) { return "i8";        }
    else if (t == Character.TYPE) { return "u16";       }
    else if (t == Short    .TYPE) { return "i16";       }
    else if (t == Integer  .TYPE) { return "i32";       }
    else if (t == Long     .TYPE) { return "i64";       }
    else if (t == Float    .TYPE) { return "i32";       }  // NYI: should be f32
    else if (t == Double   .TYPE) { return "i64";       }  // NYI: should be f64
    else if (t == Boolean  .TYPE) { return "bool";      }
    else if (t == Void     .TYPE) { return "unit";      }
    else if (t.isArray()        ) { return null;        }  // NYI: Array not supported yet
    else if (t == String.class  ) { return "string";    }
    else                          { return typeName(t); }
  }

  /**
   * Get the mangled, clean name of a given type
   *
   * @param t a Java type
   *
   * @return the corresponding Fuzion type, e.g., "Java.java.lang.String".
   */
  String typeName(Class t)
  {
    return "Java." + FeatureWriter.mangledCleanName(t.getName());
  }


  /**
   * Create Fuzion feature for given field
   *
   * @param fi the field to create fuzion code for
   *
   * @param cn the name of the Java class, e.g. "java.lang.Object"
   *
   * @param classBaseName is the base name of the Java class not including the
   * package name, e.g. "Object"
   *
   * @param data_dynamic the fuzion feature containing the instance members of
   * the class
   *
   * @param data_static the fuzion feature containing the static members of
   * the class
   */
  void processField(Field fi,
                    String cn,
                    String classBaseName,
                    StringBuilder data_dynamic,
                    StringBuilder data_static)
  {
    if ((fi.getModifiers() & Modifier.STATIC) != 0 &&
        fi.getType() != Byte     .TYPE &&
        fi.getType() != Character.TYPE &&
        fi.getType() != Short    .TYPE &&
        fi.getType() != Integer  .TYPE &&
        fi.getType() != Long     .TYPE &&
        fi.getType() != Float    .TYPE &&
        fi.getType() != Double   .TYPE &&
        fi.getType() != Boolean  .TYPE    )
      {
        var t = fi.getType();
        if (!t.isArray())
          {
            var rt = typeName(t);
            var fin = FeatureWriter.mangledCleanName(fi.getName());
            data_static.append("\n" +
                               "  # read static Java field '" + fi + "':\n" +
                               "  #\n" +
                               "  " + fin + " " + rt + " is\n" +
                               "    " + ("fuzion.java.getStaticField<" + rt + "> " +
                                         fuzionString(cn) + " " +
                                         fuzionString(fin) + "\n"));
          }
      }
    else
      {
        // NYI: instance fields, fields with non-java.io.* type not supported
      }
  }


  /**
   * Create code for a constant fuzion String from the given Java string.
   * Enclose string in double quotes and Make sure that '$', '{', '{' are
   * escaped.
   *
   * @param s a string such as "java.io.ObjectInputFilter$Status"
   *
   * @return a string such as "\"java.io.ObjectInputFilter\\$Status\""
   */
  String fuzionString(String s)
  {
    StringBuilder res = new StringBuilder();
    res.append('\"');
    s.codePoints().forEach(i ->
      {
        switch (i)
          {
          case '$':
          case '{':
          case '}': res.append('\\').append((char) i); break;
          default: res.appendCodePoint(i); break;
          }
      });
    res.append('\"');
    return res.toString();
  }

}

/* end of file */
