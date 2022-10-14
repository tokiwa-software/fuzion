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
 * Source of class Fuzion
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools;

import java.io.IOException;

import java.nio.channels.Channels;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.be.c.C;
import dev.flang.be.c.COptions;

import dev.flang.be.effects.Effects;

import dev.flang.be.interpreter.Interpreter;

import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;

import dev.flang.fuir.FUIR;

import dev.flang.fuir.analysis.dfa.DFA;

import dev.flang.me.MiddleEnd;

import dev.flang.opt.Optimizer;

import dev.flang.util.ANY;
import dev.flang.util.List;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.FuzionOptions;


/**
 * Fuzion is the main class of the Fuzion interpreter and compiler.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class Fuzion extends Tool
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Names of Java properties accepted by fz command:
   */
  static final String FUZION_HOME_PROPERTY = "fuzion.home";
  static final String FUZION_SAFETY_PROPERTY = "fuzion.safety";


  static String  _binaryName_ = null;
  static boolean _useBoehmGC_ = false;
  static boolean _xdfa_ = true;


  /**
   * Fuzion Backends:
   */
  static enum Backend
  {
    interpreter("-interpreter")
    {
      void process(FuzionOptions options, FUIR fuir)
      {
        new Interpreter(options, fuir).run();
      }
    },

    c          ("-c")
    {
      String usage()
      {
        return "[-o=<file>] [-useGC] [-Xdfa=(on|off)] ";
      }
      boolean handleOption(Fuzion f, String o)
      {
        boolean result = false;
        if (o.startsWith("-o="))
          {
            _binaryName_ = o.substring(3);
            result = true;
          }
        else if (o.equals("-useGC"))
          {
            _useBoehmGC_ = true;
            result = true;
          }
        else if (o.startsWith("-Xdfa="))
          {
            _xdfa_ = parseOnOffArg(o);
            result = true;
          }
        return result;
      }
      void process(FuzionOptions options, FUIR fuir)
      {
        new C(new COptions(options, _binaryName_, _useBoehmGC_, _xdfa_), fuir).compile();
      }
    },

    java       ("-java"),

    classes    ("-classes"),

    llvm       ("-llvm"),

    dfa        ("-dfa")
    {
      void process(FuzionOptions options, FUIR fuir)
      {
        new DFA(options, fuir).dfa();
      }
    },

    /**
     * backend to dump the IR of the main clazz to stdout
     *
     * NYI: make this dump all clazzes or give some way to control what clazzes should be dumped.
     */
    dumpFUIR   ("-XdumpFUIR")
    {
      boolean runsCode() { return false; }
      void process(FuzionOptions options, FUIR fuir)
      {
        fuir.dumpCode(fuir.mainClazzId());
      }
    },

    effects    ("-effects")
    {
      String usage()
      {
        return "";
      }
      void process(FuzionOptions options, FUIR fuir)
      {
        new Effects(fuir).find();
      }
    },

    checkIntrinsics("-XXcheckIntrinsics")
    {
      boolean runsCode() { return false; }
      boolean needsSources()
      {
        return false;
      }
      boolean needsMain()
      {
        return false;
      }
      void processFrontEnd(Fuzion f, FrontEnd fe)
      {
        new CheckIntrinsics(fe);
      }
    },

    saveLib("-saveLib=<file>")
    {
      boolean runsCode() { return false; }
      void parseBackendArg(Fuzion f, String a)
      {
        f._saveLib  = parsePath(a);
      }
      String usage()
      {
        return "[-XeraseInternalNamesInLib=(on|off)] ";
      }
      boolean handleOption(Fuzion f, String o)
      {
        boolean result = false;
        if (o.startsWith("-XeraseInternalNamesInLib"))
          {
            f._eraseInternalNamesInLib = parseOnOffArg(o);
            result = true;
          }
        return result;
      }
      boolean needsSources()
      {
        return true;
      }
      boolean needsMain()
      {
        return false;
      }
      void processFrontEnd(Fuzion f, FrontEnd fe)
      {
        /*
         * Save _module to a module file
         */
        if (Errors.count() == 0)
          {
            var p = f._saveLib;
            var n = p.getFileName().toString();
            var sfx = FuzionConstants.MODULE_FILE_SUFFIX;
            if (n.endsWith(sfx))
              {
                n = n.substring(0, n.length() - sfx.length());
              }
            var data = fe.module().data(n);
            if (data != null)
              {
                System.out.println(" + " + p);
                try (var os = Files.newOutputStream(p))
                  {
                    Channels.newChannel(os).write(data);
                  }
                catch (IOException io)
                  {
                    Errors.error("-saveLib: I/O error when writing module file",
                                 "While trying to write file '"+ p + "' received '" + io + "'");
                  }
              }
          }
      }
    },

    undefined;

    /**
     * the command line argument corresponding to this backend
     */
    private final String _arg;

    /**
     * Construct undefined backend
     */
    Backend()
    {
      _arg = null;
    }

    /**
     * Construct normal Backend option
     *
     * @param arg the command line arg to enable this backend
     */
    Backend(String arg)
    {
      if (PRECONDITIONS) require
        (arg != null && arg.startsWith("-"));

      _arg = arg;
      if (arg.indexOf("=") >= 0)
        {
          arg = arg.substring(0, arg.indexOf("=")+1);
        }
      _allBackends_.put(arg, this);
    }

    /**
     * parse the argument that activates this backend. This is not needed for
     * backends like '-c' or '-dfa', but for those that require additional
     * argument like '-saveLib=<path>'.
     */
    void parseBackendArg(Fuzion f, String a)
    {
    }

    /**
     * Does this backend handle a specific option? If so, must return true.
     */
    boolean handleOption(Fuzion f, String o)
    {
      return false;
    }

    /**
     * Does this backend run or abstractly interpret the code. If so, it
     * provides options stetting flags like -debug.
     */
    boolean runsCode()
    {
      return true;
    }

    /**
     * Usage string for the specific options handled by this backend. "" if
     * none.  Must end with " " otherwise.
     */
    String usage()
    {
      return "";
    }

    /**
     * Does this backend require the front end to load sources?
     */
    boolean needsSources()
    {
      return true;
    }

    /**
     * Does this backend require a main feature or main file or '-' for stdin?
     */
    boolean needsMain()
    {
      return true;
    }

    /**
     * If this backend processes the front end data directly, this method will
     * do that and return true.
     */
    void processFrontEnd(Fuzion f, FrontEnd fe)
    {
      var mir = fe.createMIR();                                                       f.timer("createeMIR");
      var air = new MiddleEnd(fe._options, mir, fe.module() /* NYI: remove */).air(); f.timer("me");
      var fuir = new Optimizer(fe._options, air).fuir();                              f.timer("ir");
      process(fe._options, fuir);
    }

    void process(FuzionOptions options, FUIR fuir)
    {
      Errors.fatal("backend '" + this + "' not supported yet");
    }
  }

  static TreeMap<String, Backend> _allBackends_ = new TreeMap<>();

  static { var __ = Backend.undefined; } /* make sure _allBackendArgs_ is initialized */


  /*----------------------------  variables  ----------------------------*/


  /**
   * Value of property with name FUZION_HOME_PROPERTY.  Used only to initialize
   * _fuzionHome.
   */
  private String _fuzionHomeProperty = System.getProperty(FUZION_HOME_PROPERTY);


  /**
   * Home directory of the Fuzion installation.
   */
  Path _fuzionHome = _fuzionHomeProperty != null ? Path.of(_fuzionHomeProperty) : null;


  /**
   * Should we save a library?
   */
  Path _saveLib = null;


  /**
   * Should we load the base library? We do not want to load if when using
   * -saveLib= backend to create the base library file.
   */
  boolean _loadBaseLib = true;


  /**
   * When saving a library, should we erase internal names?
   */
  boolean _eraseInternalNamesInLib = true;


  /**
   * Flag to enable intrinsic functions such as fuzion.java.callVirtual. These are
   * not allowed if run in a web playground.
   */
  boolean _enableUnsafeIntrinsics = Boolean.getBoolean("fuzion.enableUnsafeIntrinsics");


  /**
   * Default result of debugLevel:
   */
  int _debugLevel = Integer.getInteger("fuzion.debugLevel", 1);


  /**
   * List of modules added using '-modules'.
   */
  List<String> _modules = new List<>();


  /**
   * List of module directories added using '-moduleDirs'.
   */
  List<String> _moduleDirs = null;


  /**
   * List of modules added using '-XdumpModules'.
   */
  List<String> _dumpModules = new List<>();


  /**
   * List of source directories added using '-sourceDirs'.
   */
  List<String> _sourceDirs = null;


  /**
   * Default result of safety:
   */
  boolean _safety = Boolean.valueOf(System.getProperty(FUZION_SAFETY_PROPERTY, "true"));


  /**
   * Read input from stdin instead of file?
   */
  boolean _readStdin = false;


  /**
   * name of main features .
   */
  String  _main = null;


  /**
   * Desired backend.
   */
  Backend _backend = Backend.undefined;


  /*--------------------------  static methods  -------------------------*/


  /**
   * main the main method
   *
   * @param args the command line arguments.
   */
  public static void main(String[] args)
  {
    new Fuzion(args).run();
  }


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for the Fuzion class
   *
   * @param args the command line arguments.
   */
  private Fuzion(String[] args)
  {
    super("fz", args);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The standard options that come with every tool.  May be redefined to add
   * more standard options to be used in different configurations.
   *
   * @param xtra include extra options such as -Xhelp, -XjavaPof, etc.
   */
  protected String STANDARD_OPTIONS(boolean xtra)
  {
    return super.STANDARD_OPTIONS(xtra) +
      (xtra ? "[-XfuzionHome=<path>] [-XloadBaseLib=(on|off)] " : "");
  }


  /**
   * The usage, includes STANDARD_OPTIONS(xtra).
   *
   * @param xtra include extra options
   */
  protected String USAGE(boolean xtra)
  {
    var std = STANDARD_OPTIONS(xtra);
    var stdRun = "[-debug[=<n>]] [-safety=(on|off)] [-unsafeIntrinsics=(on|off)] ";
    var stdBe = "[-modules={<m>,..}] [-moduleDirs={<path>,..}] [-sourceDirs={<path>,..}] " +
      (xtra ? "[-XdumpModules={<name>,..}] " : "") +
      "(<main> | <srcfile>.fz | -) ";
    if (_backend == Backend.undefined)
      {
        var aba = new StringBuilder();
        var abe = new StringBuilder();
        for (var ab : _allBackends_.entrySet())
          {
            var b = ab.getValue();
            var ba = b._arg;
            var bu = b.usage();
            if (!ba.startsWith("-X") || xtra)
              {
                aba.append(aba.length() == 0 ? "" : "|").append(ba);
              }
          }
        return
          "Usage: " + _cmd + " [-h|--help|-version]  --or--\n" +
          "       " + _cmd + " [" + aba + "] [-h|--help|-version] [<backend specific options>]  --or--\n" +
          "       " + _cmd + " -pretty " + std + " ({<file>} | -)  --or--\n" +
          "       " + _cmd + " -latex " + std + "  --or--\n" +
          "       " + _cmd + " -acemode " + std + "  --or--\n";
      }
    else
      {
        var b = _backend;
        var ba = b._arg;
        var bu = b.usage();
        return "Usage: " + _cmd + " " + ba + " " + bu +
                           (b.runsCode() ? stdRun : "") +
                           stdBe + std;
      }
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
    if (args.length >= 1 && args[0].equals("-pretty"))
      {
        return parseArgsPretty(args);
      }
    else if (args.length >= 1 && args[0].equals("-latex"))
      {
        return parseArgsLatex(args);
      }
    else if (args.length >= 1 && args[0].equals("-acemode"))
      {
        return parseArgsAceMode(args);
      }
    else
      {
        return parseArgsForBackend(args);
      }
  }


  /**
   * Parse the given command line args for the pretty printer and create a
   * runnable that executes it.  System.exit() in case of error or -help.
   *
   * @param args the command line arguments
   *
   * @return a Runnable to run the pretty printer.
   */
  private Runnable parseArgsPretty(String[] args)
  {
    var sourceFiles = new List<String>();
    for (var a : args)
      {
        if (!parseGenericArg(a) &&
            !a.equals("-pretty")  // ignore, we know this already
            )
          {
            if (a.equals("-"))
              {
                _readStdin = true;
              }
            else if (a.startsWith("-"))
              {
                unknownArg(a);
              }
            else
              {
                sourceFiles.add(a);
              }
          }
      }
    if (sourceFiles.isEmpty() && !_readStdin)
      {
        fatal("no source files given");
      }
    else if (!sourceFiles.isEmpty() && _readStdin)
      {
        fatal("cannot process both, stdin input '-' and a list of source files");
      }
    return () ->
      {
        if (_readStdin)
          {
            new Pretty();
          }
        else
          {
            for (var s : sourceFiles)
              {
                new Pretty(s);
              }
          }
      };
  }


  /**
   * Parse the given command line args for the latex style output and create a
   * runnable that executes it.  System.exit() in case of error or -help.
   *
   * @param args the command line arguments
   *
   * @return a Runnable to run the latex styles output.
   */
  private Runnable parseArgsLatex(String[] args)
  {
    var sourceFiles = new List<String>();
    for (var a : args)
      {
        if (!parseGenericArg(a) &&
            !a.equals("-latex")   // ignore, we know this already
            )
          {
            unknownArg(a);
          }
      }
    return () ->
      {
        new Latex();
      };
  }


  /**
   * Parse the given command line args for the acemode generator and create a
   * runnable that executes it.  System.exit() in case of error or -help.
   * A mode provides syntax highlighting, code folding etc. for text editor ace.
   * For more information see: https://ace.c9.io/#nav=higlighter&api=tokenizer
   *
   * @param args the command line arguments
   *
   * @return a Runnable to run the acemode generator.
   */
  private Runnable parseArgsAceMode(String[] args)
  {
    for (var a : args)
      {
        if (!parseGenericArg(a) &&
            !a.equals("-acemode")   // ignore, we know this already
            )
          {
            unknownArg(a);
          }
      }
    return () ->
      {
        new AceMode();
      };
  }


  /**
   * Parse the given command line args to run Fuzion to create or execute code.
   * Return a runnable that runs fuzion.  System.exit() in case of error or
   * -help.
   *
   * @param args the command line arguments
   *
   * @return a Runnable to run fuzion.
   */
  private Runnable parseArgsForBackend(String[] args)
  {
    for (var a : args)
      {
        if (!parseGenericArg(a))
          {
            var arg = a;
            if (arg.indexOf("=") >= 0)
              {
                arg = arg.substring(0, arg.indexOf("=")+1);
              }
            if (a.equals("-"))
              {
                _readStdin = true;
              }
            else if (_allBackends_.containsKey(arg))
              {
                if (_backend != Backend.undefined)
                  {
                    fatal("arguments must specify at most one backend, found '" + _backend._arg + "' and '" + a + "'");
                  }
                _backend = _allBackends_.get(arg);
                _backend.parseBackendArg(this, a);
              }
            else if (a.startsWith("-XfuzionHome="            )) { _fuzionHome              = parsePath(a);              }
            else if (a.startsWith("-XloadBaseLib="           )) { _loadBaseLib             = parseOnOffArg(a);          }
            else if (a.startsWith("-modules="                )) { _modules.addAll(parseStringListArg(a));               }
            else if (a.startsWith("-XdumpModules="           )) { _dumpModules             = parseStringListArg(a);     }
            else if (a.startsWith("-sourceDirs="             )) { _sourceDirs = new List<>(); _sourceDirs.addAll(parseStringListArg(a)); }
            else if (a.startsWith("-moduleDirs="             )) { _moduleDirs = new List<>(); _moduleDirs.addAll(parseStringListArg(a)); }
            else if (_backend.runsCode() && a.matches("-debug(=\\d+|)"       )) { _debugLevel              = parsePositiveIntArg(a, 1); }
            else if (_backend.runsCode() && a.startsWith("-safety="          )) { _safety                  = parseOnOffArg(a);          }
            else if (_backend.runsCode() && a.startsWith("-unsafeIntrinsics=")) { _enableUnsafeIntrinsics  = parseOnOffArg(a);          }
            else if (_backend.handleOption(this, a))
              {
              }
            else if (a.startsWith("-"))
              {
                unknownArg(a);
              }
            else if (_main != null)
              {
                fatal("several main feature names provided: '" + _main + "', '" + a + "'");
              }
            else
              {
                _main = a;
              }
          }
      }
    if (_backend == Backend.undefined)
      {
        _backend = Backend.interpreter;
      }
    if (_main == null && !_readStdin && _backend.needsMain())
      {
        fatal("missing main feature name in command line args");
      }
    if (!_backend.needsMain() && _main != null)
      {
        fatal("no main feature '" + _main + "' may be given for backend '" + _backend + "'");
      }
    if (!_backend.needsMain() && _readStdin)
      {
        fatal("no '-' to read from stdin may be given for backend '" + _backend + "'");
      }
    if (_main != null && _readStdin)
      {
        fatal("cannot process main feature name together with stdin input");
      }
    if (_fuzionHome == null)
      {
        fatal("neither property '" + FUZION_HOME_PROPERTY + "' is set nor argument '-XfuzionHome=<path>' is given");
      }
    return () ->
      {
        var options = new FrontEndOptions(_verbose,
                                          _fuzionHome,
                                          _loadBaseLib,
                                          _eraseInternalNamesInLib,
                                          _modules,
                                          _moduleDirs,
                                          _dumpModules,
                                          _debugLevel,
                                          _safety,
                                          _enableUnsafeIntrinsics,
                                          _sourceDirs,
                                          _readStdin,
                                          _main,
                                          _backend.needsSources());
        if (_backend == Backend.c)
          {
            options.setTailRec();
          }
        timer("prep");
        var fe = new FrontEnd(options);
        timer("fe");
        _backend.processFrontEnd(this, fe);
        timer("be");
        options.verbosePrintln(1, "Elapsed time for phases: " + _times);
      };
  }


  /**
   * To be called whenever a major task was completed. Will record the time
   * since last call to timer together with name to be printed when verbose
   * output is activated.
   */
  void timer(String name)
  {
    var t = System.currentTimeMillis();
    var delta = t - _timer;
    _timer = t;
    _times.append(_times.length() == 0 ? "" : ", ").append(name).append(" ").append(delta).append("ms");
  }

  /**
   * Time required for phases recorded by timer().
   */
  StringBuilder _times = new StringBuilder();


  /**
   * Last time timer() was called, in System.currentTimeMillis();
   */
  long _timer = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();


}

/* end of file */
