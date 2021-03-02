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

  static final String STANDRD_OPTIONS = "[-noANSI] (<main> | -) ";
  static final String USAGE =
    "Usage: " + CMD + " [-h|--help] [" + _allBackendArgs_ + "] " + STANDRD_OPTIONS + " --or--\n" +
    _allBackendExtraUsage_ +
    "       " + CMD + " -pretty [-noANSI] ({<file>} | -)\n";


  /*----------------------------  variables  ----------------------------*/


  /**
   * Level of verbosity of output
   */
  final int VERBOSE = Integer.getInteger("fuzion.verbose", 0);


  /**
   * Flag to enable intrinsic functions such as fuzion.java.callVirtual. These are
   * not allowed if run in a web playground.
   */
  final boolean ENABLE_UNSAFE_INTRINSICS = Boolean.getBoolean("fuzion.enableUnsafeIntrinsics");
  {
    Intrinsics.ENABLE_UNSAFE_INTRINSICS = ENABLE_UNSAFE_INTRINSICS;  // NYI: Add to Fuzion IR or BE Config
  }


  /**
   * Default result of debugLevel:
   */
  final int FUZION_DEBUG_LEVEL = Integer.getInteger("fuzion.debugLevel", 1);
  {
    Intrinsics.FUZION_DEBUG_LEVEL = FUZION_DEBUG_LEVEL;  // NYI: Add to Fuzion IR or BE Config
  }


  /**
   * Default result of safety:
   */
  final boolean FUZION_SAFETY = new Boolean(System.getProperty("fuzion.safety", "true"));
  {
    Intrinsics.FUZION_SAFETY = FUZION_SAFETY;  // NYI: Add to Fuzion IR or BE Config
  }


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
        var options = new FrontEndOptions(VERBOSE,
                                          FUZION_SAFETY,
                                          FUZION_DEBUG_LEVEL,
                                          _readStdin,
                                          _main);
        if (_backend == Backend.c)
          {
            options.setTailRec();
          }
        var mir = new FrontEnd(options).createMIR();
        var air = new MiddleEnd(options, mir).air();
        var fuir = new Optimizer(options, air).fuir();
        switch (_backend)
          {
          case interpreter: new Interpreter(fuir).run(); break;
          case c          : new C(new COptions(options, _binaryName_), fuir).compile(); break;
          default         : Errors.fatal("backend '" + _backend + "' not supported yet"); break;
          }
      };
  }

}

/* end of file */
