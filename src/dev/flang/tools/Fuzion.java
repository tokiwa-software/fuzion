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

import java.nio.file.Path;

import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.be.c.C;
import dev.flang.be.c.COptions;

import dev.flang.be.effects.Effects;

import dev.flang.be.interpreter.Intrinsics;
import dev.flang.be.interpreter.Interpreter;

import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;

import dev.flang.me.MiddleEnd;

import dev.flang.opt.Optimizer;

import dev.flang.util.ANY;
import dev.flang.util.List;
import dev.flang.util.Errors;


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


  static String _binaryName_ = null;


  /**
   * Fuzion Backends:
   */
  static enum Backend
  {
    interpreter("-interpreter"),
    c          ("-c")
    {
      String usage()
      {
        return "[-o=<file>] ";
      }
      boolean handleOption(String o)
      {
        boolean result = false;
        if (o.startsWith("-o="))
          {
            _binaryName_ = o.substring(3);
            result = true;
          }
        return result;
      }
    }
    ,
    java       ("-java"),
    classes    ("-classes"),
    llvm       ("-llvm"),
    effects    ("-effects")
    {
      String usage()
      {
        return new String(""); /* tricky: empty string != "" */
      }
    },
    checkIntrinsics("-XXcheckIntrinsics")
    {
      boolean needsSources()
      {
        return false;
      }
      boolean needsMain()
      {
        return false;
      }
      boolean processFrontEnd(FrontEnd fe)
      {
        new CheckIntrinsics(fe);
        return true;
      }
    },
    saveBaseLib("-XsaveBaseLib")
    {
      boolean needsSources()
      {
        return true;
      }
      boolean needsMain()
      {
        return false;
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
      if (usage() == "")
        {
          _allBackendArgs_.append(_allBackendArgs_.length() == 0 ? "" : "|").append(arg);
        }
      else
        {
          _allBackendExtraUsage_.append("       @CMD@ " + _arg + " " + usage() + STD_OPTIONS + " --or--\n");
        }
      _allBackends_.put(arg, this);
    }


    /**
     * Does this backend handle a specific option? If so, must return true.
     */
    boolean handleOption(String o)
    {
      return false;
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
    boolean processFrontEnd(FrontEnd fe)
    {
      return false;
    }
  }

  static StringBuilder _allBackendArgs_ = new StringBuilder();
  static StringBuilder _allBackendExtraUsage_ = new StringBuilder();
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
   * Should we save the base library?
   */
  Path _saveBaseLib = null;


  /**
   * When saving a library, should we erase internal names?
   */
  Boolean _eraseInternalNamesInLib = null;


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
   * List of modules added using '-module'.
   */
  List<String> _modules = new List<>();


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
      (xtra ? "[-XfuzionHome=<path>] [-XsaveBaseLib=<path>] [-XeraseInternalNamesInLib=(on|off)] " : "");
  }


  /**
   * The basic usage, using STD_OPTIONS as a placeholder for standard
   * options.
   */
  protected String USAGE0()
  {
    return
      "Usage: " + _cmd + " [-h|--help|-version] [" + _allBackendArgs_ + "] " + STD_OPTIONS + "[-modules={<m>,..} [-debug[=<n>]] [-safety=(on|off)] [-unsafeIntrinsics=(on|off)] (<main> | <srcfile>.fz | -)  --or--\n" +
      _allBackendExtraUsage_.toString().replace("@CMD@", _cmd) +
      "       " + _cmd + " -pretty " + STD_OPTIONS + " ({<file>} | -)\n" +
      "       " + _cmd + " -latex " + STD_OPTIONS + "\n" +
      "       " + _cmd + " -acemode " + STD_OPTIONS + "\n";
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
            if (a.equals("-"))
              {
                _readStdin = true;
              }
            else if (_allBackends_.containsKey(a))
              {
                if (_backend != Backend.undefined)
                  {
                    fatal("arguments must specify at most one backend, found '" + _backend._arg + "' and '" + a + "'");
                  }
                _backend = _allBackends_.get(a);
              }
            else if (a.startsWith("-XfuzionHome="            )) { _fuzionHome              = parsePath(a);              }
            else if (a.startsWith("-XsaveBaseLib="           )) { _saveBaseLib             = parsePath(a);              }
            else if (a.startsWith("-XeraseInternalNamesInLib")) { _eraseInternalNamesInLib = parseOnOffArg(a);          }
            else if (a.startsWith("-modules="                )) { _modules.addAll(parseStringListArg(a));               }
            else if (a.matches("-debug(=\\d+|)"              )) { _debugLevel              = parsePositiveIntArg(a, 1); }
            else if (a.startsWith("-safety="                 )) { _safety                  = parseOnOffArg(a);          }
            else if (a.startsWith("-unsafeIntrinsics="       )) { _enableUnsafeIntrinsics  = parseOnOffArg(a);          }
            else if (_backend.handleOption(a))
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
    if (_saveBaseLib != null)
      {
        if (_backend == Backend.undefined)
          {
            _backend = Backend.saveBaseLib;
          }
        else
          {
            fatal("no backend may be specified in conjunction with -XsaveBaseLib");
          }
      }
    else if (_backend == Backend.saveBaseLib)
      {
        _saveBaseLib = Path.of("base.fum");
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
    if (_eraseInternalNamesInLib != null && _backend != Backend.saveBaseLib)
      {
        fatal("-XeraseInternalNamesInLib may only be specified when creating a library using -XsaveBaseLib");
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
                                          _saveBaseLib,
                                          _eraseInternalNamesInLib == null ? true : _eraseInternalNamesInLib,
                                          _modules,
                                          _debugLevel,
                                          _safety,
                                          _readStdin,
                                          _main,
                                          _backend.needsSources());
        if (_backend == Backend.c)
          {
            options.setTailRec();
          }
        long jvmStartTime = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
        long prepTime = System.currentTimeMillis();
        var fe = new FrontEnd(options);
        if (_saveBaseLib == null && !_backend.processFrontEnd(fe))
          {
            var mir = fe.createMIR();
            long feTime = System.currentTimeMillis();
            var air = new MiddleEnd(options, mir, fe.module() /* NYI: remove */).air();
            long meTime = System.currentTimeMillis();
            var fuir = new Optimizer(options, air).fuir();
            long irTime = System.currentTimeMillis();
            switch (_backend)
              {
              case interpreter:
                {
                  Intrinsics.ENABLE_UNSAFE_INTRINSICS = _enableUnsafeIntrinsics;  // NYI: Add to Fuzion IR or BE Config
                  var in = new Interpreter(options, fuir);
                  irTime = System.currentTimeMillis();
                  in.run(); break;
                }
              case c          : new C(new COptions(options, _binaryName_), fuir).compile(); break;
              case effects    : new Effects(fuir).find(); break;
              default         : Errors.fatal("backend '" + _backend + "' not supported yet"); break;
              }
            long beTime = System.currentTimeMillis();

            beTime -= irTime;
            irTime -= meTime;
            meTime -= feTime;
            feTime -= prepTime;
            prepTime -= jvmStartTime;
            options.verbosePrintln(1, "Elapsed time for phases: prep "+prepTime+"ms, fe "+feTime+"ms, me "+meTime+"ms, ir "+irTime+"ms, be "+beTime+"ms");
          }
        else
          {
            long feTime = System.currentTimeMillis();
            feTime -= prepTime;
            prepTime -= jvmStartTime;
            options.verbosePrintln(1, "Elapsed time for phases: prep "+prepTime+"ms, fe "+feTime+"ms");
          }
      };
  }

}

/* end of file */
