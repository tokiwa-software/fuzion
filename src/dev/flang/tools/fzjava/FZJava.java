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
 * Source of class FZJava
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools.fzjava;

import dev.flang.parser.Lexer;

import dev.flang.tools.Tool;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;

import java.io.IOException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;

import java.net.MalformedURLException;
import java.net.URL;

import java.nio.file.Files;
import java.nio.file.Path;

import java.nio.charset.StandardCharsets;

import java.util.TreeSet;
import java.util.TreeMap;

import java.util.zip.ZipFile;


/**
 * FZJava is the main class of the Fuzion fzjava tool that creates Fuzion
 * features to interface Java code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class FZJava extends Tool
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Name of this tool, i.e., the command that typically starts this tool.
   */
  static final String FZJAVA_TOOL = "fzjava";


  /*----------------------------  variables  ----------------------------*/


  /**
   * The set of Java modules specified on the command line, e.g., ["java.base",
   * "java.desktop"].
   */
  List<String> _modules = new List<String>();


  /**
   * The set of regex patterns to use to filter java classes, e.g., ["java..*",
   * "javax..*"]
   */
  List<String> _patterns = new List<String>();


  /**
   * The module to create, .e.g. "build/modules/java.base"
   */
  Path _dest;


  /**
   * Should existing files be overwritten?
   */
  boolean _overwrite = false;


  /**
   * Set of outer package fuzion features created already using createOuter.
   */
  TreeSet<String> _pkgs = new TreeSet<String>();


  /*--------------------------  static methods  -------------------------*/


  /**
   * main the main method
   *
   * @param args the command line arguments.  One argument is
   * currently supported: the main feature name.
   */
  public static void main(String[] args)
  {
    new FZJava(args).run();
  }


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for the FZJava class
   *
   * @param args the command line arguments.
   */
  private FZJava(String[] args)
  {
    super(FZJAVA_TOOL, args);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The basic usage, using STD_OPTIONS as a placeholder for standard
   * options.
   */
  protected String USAGE0()
  {
    return "Usage: " + _cmd + " [-h|--help|-version] " + STD_OPTIONS + "[-to=<dir>] {module}+\n";
  }


  /**
   * Parse the given command line args and create a runnable to run the
   * corresponding tool.  System.exit() in case of error or -help.
   *
   * @param args the command line arguments
   *
   * @return a Runnable to run the selected tool.
   */
  public Runnable parseArgs(String[] args)
  {
    for (var a : args)
      {
        if (!parseGenericArg(a))
          {
            if (a.startsWith("-to="))
              {
                if (_dest != null)
                  {
                    fatal("several '-to' options provided");
                  }
                _dest = Path.of(a.substring("-to=".length()));
              }
            else if (a.startsWith("-p="))
              {
                _patterns.add(a.substring("-p=".length()));
              }
            else if (a.startsWith("-overwrite="))
              {
                _overwrite = parseOnOffArg(a);
              }
            else if (a.startsWith("-"))
              {
                unknownArg(a);
              }
            else
              {
                _modules.add(a);
              }
          }
      }
    if (_modules.isEmpty())
      {
        fatal("require at least one module given as a command line argument");
      }
    return () -> execute();
  }


  /**
   * Create Fuzion features to interface Java code.  Called after arguments have
   * been parsted successfully.
   */
  void execute()
  {
    if (createDestDir())
      {
        for (var m : _modules)
          {
            if (!m.endsWith(".jmod"))
              {
                m = m + ".jmod";
              }
            processModule(m);
          }
      }
  }


  /**
   * Create destination directory _dest if it does not exist.
   *
   * @return true iff everything went fine and _dest exists.
   */
  boolean createDestDir()
  {
    if (_dest == null)
      {
        _dest = Path.of("lib");
      }
    if (!Files.exists(_dest))
      {
        try
          {
            if (_verbose > 0)
              {
                System.out.println(" + " + _dest);
              }
            Files.createDirectory(_dest);
          }
        catch (IOException e)
          {
            Errors.error("failed to create directory: '" + _dest + "': " + e);
            return false;
          }
      }
    return true;
  }


  /**
   * Create Fuzion features to interface Java code for given module.
   *
   * @param a module such as 'java.base.mod'
   */
  void processModule(String m)
  {
    var p = modulePath(m);
    if (_verbose > 0)
      {
        System.out.println("MODULE: " + m + " at " + p);
      }

    String url = "file:jar://" + p.toUri().getPath();
    try
      {
        var cl = new java.net.URLClassLoader​(new URL[] { new URL(url) });
        try
          {
            var zip = new ZipFile(p.toFile());
            try
              {
                zip.stream().forEach(e ->
                  {
                    var n = e.getName();
                    if (n.startsWith("classes/") && n.endsWith(".class"))
                      {
                        var fn = n.substring("classes/".length(),n.lastIndexOf(".class"));
                        var cn = fn.replace('/','.');
                        if (!cn.equals("module-info"))
                          {
                            processClass(cl, cn);
                          }
                      }
                  });
              }
            finally
              {
                zip.close();
              }
          }
        catch (IOException x)
          {
            Errors.error("error opening jmod file: '" + p + "' for module '" + m + "': " + x);
          }
      }
    catch (MalformedURLException e)
      {
        Errors.error("failed to load module '" + m + "' from URL '" + url + "': " + e);
      }
  }


  /**
   * Get the path to module m.
   *
   * @param m a module name, may include a path, e.g., "java.base.mod",
   * "/usr/local/lib/jdk-16+36/jmods/java.base.mod".
   *
   * @return the path to m, using $JAVA_HOME/mods in case m does not provide a
   * path to a module file.
   */
  Path modulePath(String m)
  {
    var p = Path.of(m);
    if (!Files.exists(p))
      {
        var jp = javaHome().resolve("jmods").resolve(p);
        if (Files.exists(jp))
          {
            return jp;
          }
      }
    return p;
  }


  /**
   * Get the path of the Java installation directory.
   */
  Path javaHome()
  {
    return Path.of(System.getProperty("java.home"));
  }


  /**
   * Create Fuzion features to interface Java code for class with given name.
   *
   * @param cl ClassLoader to load class from
   *
   * @param cn the class name, e.g., "java.lang.Object".
   *
   */
  void processClass(ClassLoader cl, String cn)
  {
    if (matchesClassPattern(cn))
      {
        try
          {
            var c = cl.loadClass(cn);
            check
              (cn.equals(c.getName()));
            processClass(c);
          }
        catch (ClassNotFoundException e)
          {
            System.err.println("Failed to load class " + cn + ": " + e);
          }
      }
  }


  /**
   * Create Fuzion features to interface Java code for class with given name.
   *
   * @param cl ClassLoader to load class from
   *
   * @param cn the class name, e.g., "java.lang.Object".
   *
   */
  boolean matchesClassPattern(String cn)
  {
    var ok = _patterns.isEmpty();
    for (var pa : _patterns)
      {
        ok = ok || cn.matches(pa);
      }
    return ok;
  }


  static String DYNAMIC_SUFFIX = "";
  static String STATIC_SUFFIX  = "_static";
  static String UNIT_SUFFIX    = "_unit";


  /**
   * Create Fuzion features to interface Java code for given class.
   *
   * @param c the Java class
   */
  void processClass(Class c)
  {
    var cn  = c.getName();
    var ccn = cleanName(cn);
    var jfn = "Java/" + ccn.replace('.', '/');
    var jtn = typeName(c);
    if (c != null && (c.getModifiers() & Modifier.PUBLIC) != 0)
      {
        var n = jfn.substring(jfn.lastIndexOf("/") + 1);
        StringBuilder data_dynamic = new StringBuilder(header("Fuzion interface to instance members of Java instance class '" + cn + "'") +
                                                       jtn + "(forbidden void) ref : fuzion.java.JavaObject(forbidden) is\n");
        StringBuilder data_static  = new StringBuilder(header("Fuzion interface to static members of Java class '" + cn + "'") +
                                                       jtn + STATIC_SUFFIX + " is\n");
        StringBuilder data_unit    = new StringBuilder(header("Fuzion unit feature to call static members of Java class '" + cn + "'") +
                                                       jtn + " => " + jtn + STATIC_SUFFIX + "\n");
        TreeMap<String,Method> overloaded = new TreeMap<>();
        for (var me : c.getMethods())
          {
            processMethod(me, n, data_dynamic, data_static, overloaded);
          }
        for (var me : overloaded.values())
          {
            shortHand(me, n, data_dynamic, data_static);
          }
        for (var fi : c.getFields())
          {
            if (fi.getDeclaringClass() == c)
              {
                processField(fi, cn, n, data_dynamic, data_static);
              }
          }
        // NYI: Constructors not supported

        createOuter(jfn);
        writeFeature(jfn, DYNAMIC_SUFFIX, data_dynamic.toString());
        writeFeature(jfn, STATIC_SUFFIX , data_static .toString());
        writeFeature(jfn, UNIT_SUFFIX   , data_unit   .toString());
      }
  }


  /**
   * Create Fuzion header for a generated source file.
   *
   * @param main the main comment describing the generated file, must fit in a single line
   */
  String header(String main)
  {
    return
      "# " + main + "\n" +
      "#\n" +
      "# !!!!!!  DO NOT EDIT, GENERATED CODE !!!!!!\n" +
      "#\n" +
      "# This code was generated automatically using the " + FZJAVA_TOOL + " tool V" + version() + " called \n" +
      "# as follows:\n" +
      "#\n" +
      "#   " + command() + "\n" +
      "#\n";
  }


  /**
   * Create Fuzion feature for given method
   *
   * @param me the method to create fuzion code for
   *
   * @param classBaseName is the base name of the Java class not including the
   * package name, e.g. "Object"
   *
   * @param data_dynamic the fuzion feature containing the instance members of
   * the class
   *
   * @param data_static the fuzion feature containing the static members of
   * the class
   *
   * @param overloaded Set of overloaded methods, mapping name comdined with
   * number or parameters (name + " " + n) to Methods.  In case of overloading,
   * the Method is always the preferred method to use a short name.
   */
  void processMethod(Method me,
                     String classBaseName,
                     StringBuilder data_dynamic,
                     StringBuilder data_static,
                     TreeMap<String, Method> overloaded)
  {
    var pa = me.getParameters();
    var p = formalParameters(pa);
    if ((me.getModifiers() & Modifier.STATIC) == 0 &&
        p != null &&
        me.getReturnType() == Void.TYPE)
      {
        var n = me.getName();
        var sp = signature(pa);
        var s = signature(pa, me.getReturnType());
        data_dynamic.append("\n" +
                            "  # call Java instance method '" + me + "':\n" +
                            "  #\n" +
                            "  " + mangle(cleanName(n)) + (sp.length() == 0 ? "" : "_" + mangle(sp)) + p + " is\n" +
                            "    " + ("fuzion.java.callVirtual<fuzion.java.JavaVoid> " +
                                      fuzionString(n) + " " +
                                      fuzionString(s) +
                                      " " + mangle(classBaseName) + ".this "+
                                      parametersArray(pa) + "\n")
                            );
        String nn = n + " " + pa.length;
        if (sp.length() > 0)
          {
            var existing = overloaded.get(nn);
            if (existing == null || !preferred(existing.getParameters(), pa))
              {
                overloaded.put(nn, me);
              }
          }
      }
    else
      {
        // NYI: instance methods, methods with non-empty parameter lists, methods with non-void result not supported
      }
  }


  /**
   * Create Fuzion shortHand for given method.  A shortHand is a feature that
   * does not have the mangled Java signature as part of its name. In case of
   * overloading, only one method will be choses as a shortHand.
   *
   * @param me the method to create fuzion code for
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
  void shortHand(Method me,
                 String classBaseName,
                 StringBuilder data_dynamic,
                 StringBuilder data_static)
  {
    var pa = me.getParameters();
    var p = formalParameters(pa);
    var n = me.getName();
    var mcn = mangle(cleanName(n));
    var sp = signature(pa);
    data_dynamic.append("\n" +
                        "  # short-hand to call Java instance method '" + me + "':\n" +
                        "  #\n" +
                        "  " + mcn + p + " is\n" +
                        "    " + mcn + "_" + mangle(sp) + parametersList(pa) + "\n");
  }



  /**
   * For two overloaded methods with parameter lists pa1 and pa2, choose which
   * one should be preferred for a short-hand call: the first one (pa1, true),
   * or the second one (pa2, false).
   *
   * @param pa1 paramaters of first cantidate
   *
   * @param pa2 paramaters of second cantidate
   */
  boolean preferred(Parameter[] pa1, Parameter[] pa2)
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
                return false; // no clear preference, so choose the first we found.
              }
          }
      }

    check
      (false);     // all parameters are equal, this should be unreachable

    return false;
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
        var mp = mangle(cleanName(p.getName()));
        String mt = null;
        if (t.isArray())
          { // NYI: handle array types
            return null;
          }
        else if (t == Byte     .TYPE) { mt = "i32";       }  // NYI: should be i8
        else if (t == Character.TYPE) { mt = "i32";       }  // NYI: should be u16
        else if (t == Short    .TYPE) { mt = "i32";       }  // NYI: should be i16
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
        var mp = mangle(cleanName(p.getName()));
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
        var mp = mangle(cleanName(p.getName()));
        res.append(mp);
      }
    return res.toString();
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
    return mangle("Java." + cleanName(t.getName()));
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
            var fin = mangle(cleanName(fi.getName()));
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


  /**
   * For a name that is separated by '.', find all parts that are Fuzion
   * keywords and replace them by "_k_" + <keyword>
   *
   * @param n a string, e.g., "java/lang/ref/PhantomReference"
   *
   * @return a string without any keywords, e.g., "java/lang/_k_ref/PhantomReference"
   */
  String cleanName(String n)
  {
    StringBuilder res = new StringBuilder();
    for (var s : n.split("\\."))
      {
        if (Lexer.isKeyword(s))
          {
            s = "_k_" + s;
          }
        else if (s.equals("Object"))
          {
            // NYI: Due to #40, we cannot declare an inner feature with name 'Object',
            // so we replace it by '_jObject'.
            s = "_jObject";
          }
        else if (s.equals("string"))
          {
            // NYI: this is just a precaution to avoid confusion with Fuzion
            // strings. Needs to test if this is really necessary.
            s = "_jstring";
          }

        if (res.length() > 0)
          {
            res.append(".");
          }
        res.append(s);
      }
    return res.toString();
  }


  /**
   * For given java class, create fuzion features for all its outer
   * packages. E.g., for "java/lang/Object", this will create
   *
   * Java.fz:              Java is
   * Java/java.fz:         Java.java is
   * Java/java/lang.fz:    Java.java.lang is
   *
   * @param jfn a fuzion clazz such as "Java/java/lang/Object"
   */
  void createOuter(String jfn)
  {
    var pkg = jfn.substring(0, jfn.lastIndexOf("/"));
    if (pkg.indexOf("/") >= 0)
      {
        createOuter(pkg);
      }
    if (!_pkgs.contains(pkg))
      {
        _pkgs.add(pkg);
        writeFeature(pkg, "", mangle(pkg.replace("/",".")) + " is\n");
      }
  }


  /**
   * Write a feature's code to a file fn + suffix + ".fz".
   *
   * @param fn a feature name, e.g., "Java/java/lang/Object"
   *
   * @param suffix a suffix, e.g., "" or "_static"
   *
   * @param data the data to write to the file.
   */
  void writeFeature(String fn, String suffix, String data)
  {
    var fzp = _dest;
    while (fn.indexOf("/") >= 0)
      {
        var d = mangle(fn.substring(0, fn.indexOf("/")));
        fzp = fzp.resolve(d);
        fn = fn.substring(fn.indexOf("/")+1);
      }
    fzp = fzp.resolve(mangle(fn) + suffix + ".fz");
    try
      {
        var fzd = fzp.getParent();
        if (fzd != null)
          {
            Files.createDirectories(fzd);
          }
        if (_overwrite || !Files.exists(fzp))
          {
            if (_verbose > 0)
              {
                System.out.println(" + " + fzp);
              }
            Files.write(fzp, data.getBytes(StandardCharsets.UTF_8));
          }
      }
    catch (IOException ioe)
      {
        Errors.error("failed to write file '" + fzp + "': " + ioe);
      }
  }


  /**
   * Perform name mangling to create a fuzion name
   *
   * @param n a name such as "java/security/Policy$Parameters" or
   * "java.security.Policy$Parameters"
   *
   * @return a mangled name such as "java.security.Policy_u000024_Parameters"
   */
  String mangle(String n)
  {
    StringBuilder res = new StringBuilder();
    n.codePoints().forEach(i ->
      {
        if (i >= 'a' && i <= 'z' ||
            i >= 'A' && i <= 'Z' ||
            i >= '0' && i <= '9' ||
            i == '.')
          {
            res.appendCodePoint(i);
          }
        else if (i == '_')
          {
            res.append("__");
          }
        else if (i == '$')
          {
            res.append("_S_");
          }
        else if (i == '/')
          {
            res.append("_7_");
          }
        else if (i == ';')
          {
            res.append("_s_");
          }
        else
          {
            res.append("_u").append(Integer.toHexString(0x1000000 + i).substring(1)).append("_");
          }
      });
    return res.toString();
  }

}

/* end of file */
