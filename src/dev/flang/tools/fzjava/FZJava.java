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

import dev.flang.ast.AbstractFeature;

import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;

import dev.flang.tools.FuzionHome;
import dev.flang.tools.Tool;

import dev.flang.util.Errors;
import dev.flang.util.List;

import java.io.IOException;

import java.lang.reflect.Modifier;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.TreeSet;
import java.util.TreeMap;

import java.util.zip.ZipFile;


/**
 * FZJava is the main class of the Fuzion fzjava tool that creates Fuzion
 * features to interface Java code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
@SuppressWarnings("rawtypes")
public class FZJava extends Tool
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Name of this tool, i.e., the command that typically starts this tool.
   */
  static final String FZJAVA_TOOL = "fzjava";


  /*----------------------------  variables  ----------------------------*/


  /**
   * Options specified by the user.
   */
  FZJavaOptions _options = new FZJavaOptions();


  /**
   * Set of outer package fuzion features created already using createOuter.
   */
  TreeSet<String> _pkgs = new TreeSet<String>();


  /**
   * Classes for which Fuzion wrappers are generated.  Super classes will be
   * generated before sub-classes.
   */
  TreeMap<String, ForClass> _classes = new TreeMap<>();


  /**
   * An instance of FrontEnd, which is required to load library modules.
   */
  private FrontEnd _fe;


  /**
   * The features that have already been defined in the loaded library modules.
   */
  TreeSet<String> _existingFeatures = new TreeSet<String>();

  /**
   * The name of the module that is currently being processed.
   */
  String _currentModule;


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
   * The usage, includes STANDARD_OPTIONS(xtra).
   *
   * @param xtra include extra options
   */
  protected String USAGE(boolean xtra)
  {
    return "Usage: " + _cmd + " [-h|--help|-version] " + STANDARD_OPTIONS(xtra) + "[-to=<dir>] {module}+\n";
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
                if (_options._dest != null)
                  {
                    fatal("several '-to' options provided");
                  }
                _options._dest = Path.of(a.substring("-to=".length()));
              }
            else if (a.startsWith("-p="))
              {
                _options._patterns.add(a.substring("-p=".length()));
              }
            else if (a.startsWith("-overwrite="))
              {
                _options._overwrite = parseOnOffArg(a);
              }
            else if (a.startsWith("-modules="))
              {
                _options._loadModules.addAll(parseStringListArg(a));
              }
            else if (a.startsWith("-moduleDirs="))
              {
                _options._moduleDirs.addAll(parseStringListArg(a));
              }
            else if (a.startsWith("-"))
              {
                unknownArg(a);
              }
            else
              {
                _options._modules.add(a);
              }
          }
      }
    if (_options._modules.isEmpty())
      {
        fatal("require at least one module given as a command line argument");
      }
    return () -> execute();
  }


  /**
   * Create Fuzion features to interface Java code.  Called after arguments have
   * been parsed successfully.
   */
  void execute()
  {
    if (createDestDir())
      {
        List<String> emptyList = new List<>();
        var feOptions = new FrontEndOptions(/* verbose */ _verbose,
                                            /* fuzionHome */ FuzionHome._fuzionHome,
                                            /* loadBaseMod */ true,
                                            /* eraseInternalNamesInMod */ true,
                                            /* modules */ _options._loadModules,
                                            /* moduleDirs */ _options._moduleDirs,
                                            /* dumpModules */ emptyList,
                                            /* fuzionDebugLevel */ 0,
                                            /* fuzionSafety */ true,
                                            /* enableUnsafeIntrinsics */ true,
                                            /* sourceDirs */ emptyList,
                                            /* readStdin */ false,
                                            /* executeCode */ null,
                                            /* main */ null,
                                            /* moduleName */ null,
                                            /* loadSources */ true,
                                            /* needsEscapeAnalysis */ false,
                                            /* serializeFuir */ false,
                                            /* timer */ s -> timer(s));
        _fe = new FrontEnd(feOptions);

        for (var m : _options._modules)
          {
            if (!m.endsWith(".jmod"))
              {
                m = m + ".jmod";
              }
            processModule(m);
            if (m.equals("java.base.jmod"))
              {
                addAsJavaObjectHelper();
              }
          }
        timer("all");
        feOptions.verbosePrintln(1, "Elapsed time for phases: " + _times);
      }
  }


  /*
   * Add helper feature Java.as_java_object that
   * allows easy java array creation of complex objects.
   */
  private void addAsJavaObjectHelper()
  {
    var fzp = _options._dest;
    fzp = fzp.resolve("ext.fz");
    try
      {
        var str = new StringBuilder(
                    "public Java.as_java_object(T type : Java.java.lang.Object, seq Sequence T) =>\n");
        str.append("  res := (Java.java.lang.reflect.Array.newInstance_Ljava_7_lang_7_Class_s_I T.get_java_class seq.count).val\n");
        str.append("  for idx := 0, idx+1\n");
        str.append("      el in seq\n");
        str.append("  do\n");
        str.append("    _ := Java.java.lang.reflect.Array.__k__set res idx el\n");
        str.append("  fuzion.java.Array T res.java_ref\n");
        str.append("\n");
        Files.write(fzp, str.toString().getBytes(StandardCharsets.UTF_8));
      }
    catch (IOException e)
      {
        Errors.fatal("could not write ext.fz");
      }
  }


  /**
   * Given a qualified name, add this qualified name and the qualified name of
   * its outer features declared by the given feature in any of the loaded
   * modules to _existingFeatures.
   *
   * This is usually called with the universe as given feature in the first
   * iteration. Then the qualified names of the given feature itself, and all of
   * its parents end up in _existingFeatures.
   *
   * The recursion here ends because no feature can be both an outer and an inner
   * feature of some other feature, i.e. the outer-inner relationship defines a
   * tree of features.
   */
  private void recurseDeclaredFeature(String name, AbstractFeature f)
  {
    for (var m : _fe.getModules())
      {
        var df = m.declaredFeatures(f);

        for (var fn : df.values())
          {
            if (name.startsWith(fn.qualifiedName()))
              {
                _existingFeatures.add(fn.qualifiedName());
                recurseDeclaredFeature(name, fn);
              }
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
    if (_options._dest == null)
      {
        _options._dest = Path.of("lib");
      }
    if (!Files.exists(_options._dest))
      {
        try
          {
            if (_verbose > 0)
              {
                say(" + " + _options._dest);
              }
            Files.createDirectory(_options._dest);
          }
        catch (IOException e)
          {
            Errors.error("failed to create directory: '" + _options._dest + "': " + e);
            return false;
          }
      }
    return true;
  }


  /**
   * Create Fuzion features to interface Java code for given module.
   *
   * @param m a module such as 'java.base.mod'
   */
  void processModule(String m)
  {
    // clean up in case a previous run of processModule filled this already:
    _currentModule = m.substring(0, m.length() - 5);
    _pkgs.clear();
    _classes.clear();

    var p = modulePath(m);
    if (_verbose > 0)
      {
        say("MODULE: " + m + " at " + p);
      }

    String url = "file:jar://" + p.toUri();
    try
      {
        var cl = new java.net.URLClassLoader(new URL[] { URI.create(url).toURL() });
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
            if (CHECKS) check
              (cn.equals(c.getName()));
            processClass(c);
          }
        catch (ClassNotFoundException e)
          {
            say_err("Failed to load class " + cn + ": " + e);
          }
      }
  }


  /**
   * Create Fuzion features to interface Java code for class with given name.
   *
   * @param cn the class name, e.g., "java.lang.Object".
   */
  boolean matchesClassPattern(String cn)
  {
    var ok = _options._patterns.isEmpty();
    for (var pa : _options._patterns)
      {
        ok = ok || cn.matches(pa);
      }
    return ok;
  }


  /**
   * Create Fuzion features to interface Java code for given class.
   *
   * @param c the Java class
   */
  ForClass forClass(Class c)
  {
    if (c != null && (c.getModifiers() & Modifier.PUBLIC) != 0)
      {
        var res = _classes.get(c.getName());
        if (res == null)
          {
            ForClass sfc = null;
            var sc = c.getSuperclass();
            sc = sc == null && c != java.lang.Object.class ? java.lang.Object.class : sc;
            if (sc != null)
              {
                sfc = forClass(sc);
              }
            res = new ForClass(c, sfc);
            _classes.put(c.getName(), res);
          }
        return res;
      }
    return null;
  }


  /**
   * Create Fuzion features to interface Java code for given class.
   *
   * @param c the Java class
   */
  void processClass(Class c)
  {
    var fc = forClass(c);
    if (fc != null)
      {
        fc.write(this);
      }
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
    recurseDeclaredFeature(jfn.replace("/", "."), _fe.baseModule().libraryUniverse());

    var pkg = jfn.substring(0, jfn.lastIndexOf("/"));
    if (pkg.indexOf("/") >= 0)
      {
        createOuter(pkg);
      }
    if (!_pkgs.contains(pkg))
      {
        _pkgs.add(pkg);
        // do not generate duplicate features
        if (!_existingFeatures.contains(pkg.replace("/", ".")))
          {
            FeatureWriter.write(this, pkg, "_pkg", "public " + FeatureWriter.mangle(pkg.replace("/",".")) + " is\n");
          }
      }
  }

}

/* end of file */
