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
 * Source of class Fuzion
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools;

import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.be.c.C;
import dev.flang.be.c.COptions;

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
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
class Fuzion extends ANY
{

  /*----------------------------  constants  ----------------------------*/

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
          _allBackendExtraUsage_.append("       " + CMD + " " + _arg + " " + usage() + STANDRD_OPTIONS + " --or--\n");
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
  }

  static StringBuilder _allBackendArgs_ = new StringBuilder();
  static StringBuilder _allBackendExtraUsage_ = new StringBuilder();
  static TreeMap<String, Backend> _allBackends_ = new TreeMap<>();

  static final String CMD = System.getProperty("fuzion.command", "fz");

  static { var __ = Backend.undefined; } /* make sure _allBackendArgs_ is initialized */

  static final String STANDRD_OPTIONS = "[-noANSI] [-debug[=<n>]] [-safety=(on|off)] [-enableUnsafeIntrinsics=(on|off)] [-verbose[=<n>]] (<main> | <srcfile>.fz | -) ";
  static final String USAGE =
    "Usage: " + CMD + " [-h|--help] [" + _allBackendArgs_ + "] " + STANDRD_OPTIONS + " --or--\n" +
    _allBackendExtraUsage_ +
    "       " + CMD + " -pretty [-noANSI] ({<file>} | -)\n" +
    "       " + CMD + " -latex\n";


  /*----------------------------  variables  ----------------------------*/


  /**
   * Level of verbosity of output
   */
  int _verbose = Integer.getInteger("fuzion.verbose", 0);


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
   * Default result of safety:
   */
  boolean _safety = Boolean.getBoolean("fuzion.safety");


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
   * @param args the command line arguments.  One argument is
   * currently supported: the main feature name.
   */
  public static void main(String[] args)
  {
    new Fuzion(args);
  }


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for the Fuzion class
   *
   * @param args the command line arguments.  One argument is
   * currently supported: the main feature name.
   */
  private Fuzion(String[] args)
  {
    try
      {
        parseArgs(args).run();
        Errors.showAndExit(true);
      }
    catch(Throwable e)
      {
        e.printStackTrace();
        System.exit(1);
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Parse the given command line args and create a runnable to run the
   * corresponding tool.  System.exit() in case of error or -help.
   *
   * @param args the command line arguments
   *
   * @return a Runnable to run the selected tool.
   */
  private Runnable parseArgs(String[] args)
  {
    if (args.length >= 1 && args[0].equals("-pretty"))
      {
        return parseArgsPretty(args);
      }
    else if (args.length >= 1 && args[0].equals("-latex"))
      {
        return parseArgsLatex(args);
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
    var duplicates = new TreeSet<String>();
    var sourceFiles = new List<String>();
    for (var a : args)
      {
        if (duplicates.contains(a))
          {
            Errors.fatal("duplicate argument: '" + a + "'", USAGE);
          }
        duplicates.add(a);
        if (a.equals("-pretty"))
          { // ignore, we know this already
          }
        else if (a.equals("-h"    ) ||
                 a.equals("-help" ) ||
                 a.equals("--help")    )
          {
            System.out.println(USAGE);
            System.exit(0);
          }
        else if (a.equals("-noANSI"))
          {
            System.setProperty("FUZION_DISABLE_ANSI_ESCAPES","true");
          }
        else if (a.equals("-"))
          {
            _readStdin = true;
          }
        else if (a.startsWith("-"))
          {
            Errors.fatal("unknown argument: '" + a + "'", USAGE);
          }
        else
          {
            sourceFiles.add(a);
          }
      }
    if (sourceFiles.isEmpty() && !_readStdin)
      {
        Errors.fatal("no source files given", USAGE);
      }
    else if (!sourceFiles.isEmpty() && _readStdin)
      {
        Errors.fatal("cannot process both, stdin input '-' and a list of source files", USAGE);
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
   * Parse the given command line args for the pretty printer and create a
   * runnable that executes it.  System.exit() in case of error or -help.
   *
   * @param args the command line arguments
   *
   * @return a Runnable to run the pretty printer.
   */
  private Runnable parseArgsLatex(String[] args)
  {
    var duplicates = new TreeSet<String>();
    var sourceFiles = new List<String>();
    for (var a : args)
      {
        if (duplicates.contains(a))
          {
            Errors.fatal("duplicate argument: '" + a + "'", USAGE);
          }
        duplicates.add(a);
        if (a.equals("-latex"))
          { // ignore, we know this already
          }
        else if (a.equals("-h"    ) ||
                 a.equals("-help" ) ||
                 a.equals("--help")    )
          {
            System.out.println(USAGE);
            System.exit(0);
          }
        else if (a.startsWith("-"))
          {
            Errors.fatal("unknown argument: '" + a + "'", USAGE);
          }
        else
          {
            Errors.fatal("unknown argument: '" + a + "'", USAGE);
          }
      }
    return () ->
      {
        new Latex();
      };
  }


  /**
   * Parse argument of the form "-xyz" or "-xyz=123".
   *
   * @param a the argument
   *
   * @param defawlt value to be returned in case a does not specify an explicit
   * value.
   *
   * @return defawlt or the values specifed in a after '='.
   */
  int parsePositiveIntArg(String a, int defawlt)
  {
    if (PRECONDITIONS) require
      (a.split("=").length == 2);

    int result = defawlt;
    var s = a.split("=");
    if (s.length > 1)
      {
        try
          {
            result = Integer.parseInt(s[1]);
          }
        catch (NumberFormatException e)
          {
            Errors.fatal("failed to parse number",
                         "While analysing command line argument '" + a + "', encountered: '" + e + "'");
          }
      }
    return result;
  }


  /**
   * Parse argument of the form "-xyz=on" or "-xyz=off".
   *
   * @param a the argument
   *
   * @return true iff a is set to 'on' or an error was reported.
   */
  boolean parseOnOffArg(String a)
  {
    if (PRECONDITIONS) require
      (a.indexOf("=") >= 0);

    var s = a.split("=");
    return
      switch (s.length == 2 ? s[1] : "**fail**")
        {
        case "on" -> true;
        case "off" -> false;
        default ->
        {
          Errors.fatal("Unsupported parameter to command line option '" + s[0] + "'",
                       "While analysing command line argument '" + a + "'.  Paramter must be 'on' or 'off'");
          yield true;
        }
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
    var duplicates = new TreeSet<String>();
    for (var a : args)
      {
        if (duplicates.contains(a))
          {
            Errors.fatal("duplicate argument: '" + a + "'", USAGE);
          }
        duplicates.add(a);
        if (a.equals("-"))
          {
            _readStdin = true;
          }
        else if (_allBackends_.containsKey(a))
        {
          if (_backend != Backend.undefined)
            {
              Errors.fatal("arguments must specify at most one backend, found '" + _backend._arg + "' and '" + a + "'", USAGE);
            }
          _backend = _allBackends_.get(a);
        }
        else if (a.equals("-h"    ) ||
                 a.equals("-help" ) ||
                 a.equals("--help")    )
          {
            System.out.println(USAGE);
            System.exit(0);
          }
        else if (a.equals("-noANSI"))
          {
            System.setProperty("FUZION_DISABLE_ANSI_ESCAPES","true");
          }
        else if (a.matches("-verbose(=\\d+|)"     )) { _verbose                = parsePositiveIntArg(a, 1); }
        else if (a.matches("-debug(=\\d+|)"       )) { _debugLevel             = parsePositiveIntArg(a, 1); }
        else if (a.startsWith("-safety="          )) { _safety                 = parseOnOffArg(a);          }
        else if (a.startsWith("-unsafeIntrinsics=")) { _enableUnsafeIntrinsics = parseOnOffArg(a);          }
        else if (_backend.handleOption(a))
          {
          }
        else if (a.startsWith("-"))
          {
            Errors.fatal("unknown argument: '" + a + "'", USAGE);
          }
        else if (_main != null)
          {
            Errors.fatal("several main feature names provided: '" + _main + "', '" + a + "'", USAGE);
          }
        else
          {
            _main = a;
          }
      }
    if (_main == null && !_readStdin)
      {
        Errors.fatal("missing main feature name in command line args", USAGE);
      }
    if (_main != null && _readStdin)
      {
        Errors.fatal("cannot process main feature name together with stdin input", USAGE);
      }
    if (_backend == Backend.undefined)
      {
        _backend = Backend.interpreter;
      }
    return () ->
      {
        var options = new FrontEndOptions(_verbose,
                                          _safety,
                                          _debugLevel,
                                          _readStdin,
                                          _main);
        if (_backend == Backend.c)
          {
            options.setTailRec();
          }
        var mir = new FrontEnd(options).createMIR();
        var air = new MiddleEnd(options, mir).air();
        var fuir = new Optimizer(options, air).fuir(_backend != Backend.interpreter);
        switch (_backend)
          {
          case interpreter:
            {
              Intrinsics.ENABLE_UNSAFE_INTRINSICS = _enableUnsafeIntrinsics;  // NYI: Add to Fuzion IR or BE Config
              Intrinsics.FUZION_DEBUG_LEVEL       = _debugLevel;              // NYI: Add to Fuzion IR or BE Config
              Intrinsics.FUZION_SAFETY            = _safety;                  // NYI: Add to Fuzion IR or BE Config
              new Interpreter(fuir).run(); break;
            }
          case c          : new C(new COptions(options, _binaryName_), fuir).compile(); break;
          default         : Errors.fatal("backend '" + _backend + "' not supported yet"); break;
          }
      };
  }

}

/* end of file */
