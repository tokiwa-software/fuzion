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

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.nio.charset.StandardCharsets;

import java.nio.channels.Channels;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.TreeMap;

import dev.flang.be.c.C;
import dev.flang.be.c.COptions;

import dev.flang.be.effects.Effects;

import dev.flang.be.interpreter.Interpreter;

import dev.flang.be.jvm.JVM;
import dev.flang.be.jvm.JVMOptions;

import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;

import dev.flang.fuir.FUIR;
import dev.flang.fuir.LibraryFuir;
import dev.flang.fuir.analysis.dfa.DFA;

import dev.flang.opt.Optimizer;

import dev.flang.util.List;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.FuzionOptions;
import dev.flang.util.SourceFile;
import dev.flang.util.QuietThreadTermination;


/**
 * Fuzion is the main class of the Fuzion interpreter and compiler.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Fuzion extends Tool
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Time at application start in System.currentTimeMillis();
   */
  protected static final long _timerStart = System.currentTimeMillis();


  static String  _binaryName_ = null;
  static boolean _useBoehmGC_ = true;
  static String _cCompiler_ = null;
  static String _cFlags_ = null;
  static String _cTarget_ = null;
  static String _cInclude_ = null;
  static String _cLink_ = null;
  static boolean _keepGeneratedCode_ = false;
  static String  _jvmOutName_ = null;
  static String  _jvmLib_ = null;


  /**
   * Fuzion Backends:
   */
  static enum Backend
  {
    interpreter("-interpreter")
    {
      boolean takesApplicationArgs()
      {
        return true;
      }
      void process(FuzionOptions options, FUIR fuir)
      {
        new Interpreter(options, fuir).run();
      }
      boolean serializeFuir()
      {
        // NYI: UNDER DEVELOPMENT: use sth. like -XdisableSerializeFUIR
        return FuzionOptions.boolPropertyOrEnv("dev.flang.tools.serializeFUIR");
      }
    },

    c          ("-c")
    {
      String usage()
      {
        return "[-o=<file>] [-Xgc=(on|off)] [-XkeepGeneratedCode=(on|off)] [-CC=<c compiler>] [-CFlags=\"list of c compiler flags\"] [-CTarget=\"e.g. x86_64-pc-linux-gnu\"] [-CInclude=\"list of header files to include\"] [-CLink=\"list libraries to link\"] ";
      }
      boolean handleOption(Fuzion f, String o)
      {
        boolean result = false;
        if (o.startsWith("-o="))
          {
            _binaryName_ = o.substring(3);
            result = true;
          }
        else if (o.startsWith("-Xgc="))
          {
            _useBoehmGC_ = parseOnOffArg(o);
            result = true;
          }
        else if (o.startsWith("-CC="))
          {
            _cCompiler_ = o.substring(4);
            result = true;
          }
        else if (o.startsWith("-CFlags="))
          {
            _cFlags_ = o.substring(8);
            result = true;
          }
        else if (o.startsWith("-CTarget="))
          {
            _cTarget_ = o.substring(9);
            result = true;
          }
        else if (o.startsWith("-CInclude="))
          {
            _cInclude_ = o.substring(10);
            result = true;
          }
        else if (o.startsWith("-CLink="))
          {
            _cLink_ = o.substring(7);
            result = true;
          }
        else if (o.startsWith("-XkeepGeneratedCode="))
          {
            _keepGeneratedCode_ = parseOnOffArg(o);
            result = true;
          }
        return result;
      }
      @Override
      public boolean needsEscapeAnalysis()
      {
        return true;
      }
      void process(FuzionOptions options, FUIR fuir)
      {
        new C(new COptions(options, _binaryName_, _useBoehmGC_, _cCompiler_, _cFlags_, _cTarget_, _cInclude_, _cLink_, _keepGeneratedCode_), fuir).compile();
      }
      boolean serializeFuir()
      {
        // NYI: UNDER DEVELOPMENT: use sth. like -XdisableSerializeFUIR
        return FuzionOptions.boolPropertyOrEnv("dev.flang.tools.serializeFUIR");
      }
    },

    java       ("-java"),

    jvm        ("-jvm")
    {
      String usage()
      {
        return "";
      }
      boolean handleOption(Fuzion f, String o)
      {
        boolean result = false;
        if (o.startsWith("-JLibraries="))
          {
            _jvmLib_ = o.substring(12);
            result = true;
          }
        return result;
      }
      void process(FuzionOptions options, FUIR fuir)
      {
        try
          {
            new JVM(new JVMOptions(options, /* run */ true, /* save classes */ false, /* save JAR */ false, Optional.empty(), Optional.ofNullable(_jvmLib_)), fuir).compile();
          }
        catch (QuietThreadTermination e)
          {
          }
      }
      boolean takesApplicationArgs()
      {
        return true;
      }
      boolean serializeFuir()
      {
        // NYI: UNDER DEVELOPMENT: use sth. like -XdisableSerializeFUIR
        return FuzionOptions.boolPropertyOrEnv("dev.flang.tools.serializeFUIR");
      }
    },

    classes    ("-classes")
    {
      String usage()
      {
        return "[-o=<outputName>] ";
      }
      boolean handleOption(Fuzion f, String o)
      {
        boolean result = false;
        if (o.startsWith("-o="))
          {
            _jvmOutName_ = o.substring(3);
            result = true;
          }
        else if (o.startsWith("-JLibraries="))
          {
            _jvmLib_ = o.substring(12);
            result = true;
          }
        return result;
      }
      void process(FuzionOptions options, FUIR fuir)
      {
        new JVM(new JVMOptions(options, /* run */ false, /* save classes */ true, /* save JAR */ false, Optional.ofNullable(_jvmOutName_), Optional.ofNullable(_jvmLib_)), fuir).compile();
      }
      boolean serializeFuir()
      {
        // NYI: UNDER DEVELOPMENT: use sth. like -XdisableSerializeFUIR
        return FuzionOptions.boolPropertyOrEnv("dev.flang.tools.serializeFUIR");
      }
    },

    jar        ("-jar")
    {
      String usage()
      {
        return "[-o=<outputName>] ";
      }
      boolean handleOption(Fuzion f, String o)
      {
        boolean result = false;
        if (o.startsWith("-o="))
          {
            _jvmOutName_ = o.substring(3);
            result = true;
          }
        else if (o.startsWith("-JLibraries="))
          {
            _jvmLib_ = o.substring(12);
            result = true;
          }
        return result;
      }
      void process(FuzionOptions options, FUIR fuir)
      {
        new JVM(new JVMOptions(options, /* run */ false, /* save classes */ false, /* save JAR */ true, Optional.ofNullable(_jvmOutName_), Optional.ofNullable(_jvmLib_)), fuir).compile();
      }
      boolean serializeFuir()
      {
        // NYI: UNDER DEVELOPMENT: use sth. like -XdisableSerializeFUIR
        return FuzionOptions.boolPropertyOrEnv("dev.flang.tools.serializeFUIR");
      }
    },

    llvm       ("-llvm"),

    dfa        ("-dfa")
    {
      void process(FuzionOptions options, FUIR fuir)
      {
        // nothing to be done, DFA was already run by processFrontEnd which calls us.
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
        fuir.dumpCode();
      }
    },

    effects    ("-effects")
    {
      String usage()
      {
        return "";
      }
      @Override
      void processFrontEnd(Fuzion f, FrontEnd fe)
      {
        var o    = fe._options;
        var mir  = fe.createMIR();                             f.timer("createMIR");
        var fuir = new Optimizer(o, fe, mir).fuir();           f.timer("ir");
        new Effects(o, new DFA(o, fuir)).find();
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

    saveMod("-saveModule=<file>")
    {
      boolean runsCode() { return false; }
      void parseBackendArg(Fuzion f, String a)
      {
        f._saveMod  = parsePath(a);
      }
      String usage()
      {
        return "[-XeraseInternalNamesInMod=(on|off)] ";
      }
      boolean handleOption(Fuzion f, String o)
      {
        boolean result = false;
        if (o.startsWith("-XeraseInternalNamesInMod"))
          {
            f._eraseInternalNamesInMod = parseOnOffArg(o);
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
         * Save module to a fum-file
         */
        if (!Errors.any())
          {
            var data = fe.sourceModule().data();
            if (data != null)
              {
                try (var os = Files.newOutputStream(f._saveMod))
                  {
                    Channels.newChannel(os).write(data);
                    say(" + " + f._saveMod + " in " + (System.currentTimeMillis() - _timerStart) + "ms");
                  }
                catch (IOException io)
                  {
                    Errors.error("-saveModule: I/O error when writing module file",
                                 "While trying to write file '"+ f._saveMod + "' received '" + io + "'");
                  }
              }
          }
      }
    },

    /**
     * This backend does nothing except showing
     * any errors that happened in the frontend.
     * Can be used for syntax checking of fz files.
     */
    frontEndOnly("-frontendOnly")
    {
      void processFrontEnd(Fuzion f, FrontEnd fe)
      {
        Errors.showAndExit();
      }
    },

    /**
     * This backend does nothing except showing
     * any errors that happened in the stages up to
     * and including the DFA.
     */
    noBackend("-noBackend")
    {
      void process(FuzionOptions options, FUIR fuir)
      {
        Errors.showAndExit();
      }
      boolean serializeFuir()
      {
        // NYI: UNDER DEVELOPMENT: use sth. like -XdisableSerializeFUIR
        return FuzionOptions.boolPropertyOrEnv("dev.flang.tools.serializeFUIR");
      }
    },

    undefined
    {
      // unless another backend will be set, undefined will be replaced by jvm
      // backend, which takes application args:
      boolean takesApplicationArgs()
      {
        return true;
      }
    };

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
     * backends like {@code -c} or {@code -dfa}, but for those that require additional
     * argument like {@code -saveModule=<path>}.
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
     * Do we need to perform escape analysis during DFA phase since the backend needs that?
     *
     * This currently has a significant impact on the DFA performance, so we try to
     * avoid this for backends that do not need it (JVM and interpreter).
     *
     * @return true if escape analysis has to be performed.
     */
    public boolean needsEscapeAnalysis()
    {
      return false;
    }

    /**
     * Does this backend require a main feature or main file or {@code -} for stdin?
     */
    boolean needsMain()
    {
      return true;
    }

    /**
     * Does this backend process arguments that are passed to the Fuzion application?
     */
    boolean takesApplicationArgs()
    {
      return false;
    }

    /**
     * If this backend processes the front end data directly, this method will
     * do that and return true.
     */
    void processFrontEnd(Fuzion f, FrontEnd fe)
    {
      var o    = fe._options;
      var mir  = fe.createMIR();                             f.timer("createMIR");
      var fuir = new Optimizer(o, fe, mir).fuir();           f.timer("ir");
      process(o, new DFA(o, fuir).new_fuir());
    }

    /**
     * Process backend
     */
    void process(FuzionOptions options, FUIR fuir)
    {
      Errors.fatal("backend '" + this + "' not supported yet");
    }

    /**
     * Should the FUIR be serialized?
     */
    boolean serializeFuir()
    {
      return false;
    }

  }

  static TreeMap<String, Backend> _allBackends_ = new TreeMap<>();

  static { var __ = Backend.undefined; } /* make sure _allBackendArgs_ is initialized */


  /*----------------------------  variables  ----------------------------*/


  /**
   * Home directory of the Fuzion installation.
   */
  Path _fuzionHome = FuzionHome._fuzionHome;


  /**
   * Should we save a module?
   */
  Path _saveMod = null;


  /**
   * Should we load the base module? We do not want to load if when using
   * -saveModule= backend to create the base module file.
   */
  boolean _loadBaseMod = true;


  /**
   * When saving a module, should we erase internal names?
   */
  boolean _eraseInternalNamesInMod = false;


  /**
   * Flag to enable intrinsic functions such as fuzion.jvm.env.call_virtual. These are
   * not allowed if run in a web playground.
   */
  boolean _enableUnsafeIntrinsics = true;


  /**
   * Default result of debugLevel:
   */
  int _debugLevel = Integer.getInteger(FuzionConstants.FUZION_DEBUG_LEVEL_PROPERTY, 1);


  /**
   * List of modules added using {@code -modules}.
   */
  List<String> _modules = new List<>();


  /**
   * List of module directories added using {@code -moduleDirs}.
   */
  List<String> _moduleDirs = new List<>();


  /**
   * List of modules added using {@code -XdumpModules}.
   */
  List<String> _dumpModules = new List<>();


  /**
   * List of source directories added using {@code -sourceDirs}.
   */
  List<String> _sourceDirs = null;


  /**
   * Default result of safety:
   */
  boolean _safety = FuzionOptions.boolPropertyOrEnv(FuzionConstants.FUZION_SAFETY_PROPERTY, true);


  /**
   * Read input from stdin instead of file?
   */
  boolean _readStdin = false;


  /**
   * Code provided via comment line argument {@code -e} or {@code -exec}, null if none.
   */
  byte[] _executeCode = null;


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
   * @param xtra include extra options such as -Xhelp, -XjavaProf, etc.
   */
  protected String STANDARD_OPTIONS(boolean xtra)
  {
    return super.STANDARD_OPTIONS(xtra) +
      (xtra ? "[-XfuzionHome=<path>] [-XloadBaseModule=(on|off)] " : "");
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
      (xtra ? "[-XdumpModules={<name>,..}] " : "");
    if (_backend == Backend.undefined)
      {
        var aba = new StringBuilder();
        for (var ab : _allBackends_.entrySet())
          {
            var b = ab.getValue();
            var ba = b._arg;
            if (!ba.startsWith("-X") || xtra)
              {
                aba.append(aba.length() == 0 ? "" : "|").append(ba);
              }
          }
        return
          "Usage: " + _cmd + " [-h|--help|-version]  --or--\n" +
          "       " + _cmd + " [" + aba + "] [-h|--help|-version] [<backend specific options>]  --or--\n" +
          "       " + _cmd + " -pretty " + std + " ({<file>} | - | -e <code> | -execute <code>)  --or--\n" +
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
                           stdBe + std +
                           (b.takesApplicationArgs() ? "[--] " : "") +
                           "(<main> | <srcfile>.fz | - | (-e|-execute) <code>) " +
                           (b.takesApplicationArgs() ? "[<list of arbitrary arguments for envir.args effect>] " : "");
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
   * Check that there is exactly one of these three input source set:
   * _readStdin, _executeCode != null or commandLineSomethings.
   *
   * @param commandLineSomethings true iff input source is given via command line
   * argument or arguments
   *
   * @param nameOfSomething How to call the command line sources in an error
   * message, differs for the Pretty printer tool that may take several source files.
   *
   */
  private void checkExactlyOneInputSource(boolean commandLineSomethings, String nameOfSomething)
  {
    var sources = new List<String>();
    if (_readStdin            ) { sources.add("stdin input '-'"                             ); }
    if (_executeCode != null  ) { sources.add("option '-e/-execute <code>'"                 ); }
    if (commandLineSomethings ) { sources.add(nameOfSomething + " given on the command line"); }
    if (sources.size() == 0)
      {
        fatal("no " + nameOfSomething + ", no '-' to read stdin, nor '-e/-execute <code>' argument given");
      }
    else if (sources.size() > 1)
      {
        fatal(sources.toString("cannot process multiple input sources: "," and ","."));
      }
  }


  /**
   * Check if {@code a} is {@code -e} or {@code -execute}.
   *
   * Cause an error in case of repeated {@code -e} or {@code -execute} arguments.
   *
   * @return true if that is that case and the next argument gives the code.
   */
  private boolean parseExecute(String a)
  {
    var result = a.equals("-e") || a.equals("-execute");
    if (result && _executeCode != null)
      {
        fatal("repeated argument '-e' or '-execute'");
      }
    return result;
  }


  /**
   * Must be called with the argument following an argument for which
   * parseExecute returned true.  Will store the code in _executeCode.
   *
   * @param a the code argument.
   */
  private void executeCode(String a)
  {
    _executeCode = (a + "\n").getBytes(StandardCharsets.UTF_8);
  }


  /**
   * This must be called after a argument parsing loop that contains
   * parseExecute() to check that code was actually given following {@code -e} or
   * {@code -execute}.
   *
   * @param nextIsCode did the call to {@code parseExecute} return true for the last
   * argument?
   */
  private void checkMissingCode(boolean nextIsCode)
  {
    if (nextIsCode)
      {
        fatal("missing code following argument '-e' or '-execute'");
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
    boolean nextIsCode = false;
    var sourceFiles = new List<String>();
    for (var a : args)
      {
        if (nextIsCode)
          {
            executeCode(a);
            nextIsCode = false;
          }
        else if (!parseGenericArg(a) &&
                 !a.equals("-pretty")  // ignore, we know this already
                 )
          {
            if (a.equals("-"))
              {
                _readStdin = true;
              }
            else if (parseExecute(a))
              {
                nextIsCode = true;
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
    checkMissingCode(nextIsCode);
    checkExactlyOneInputSource(!sourceFiles.isEmpty(), "source file(s)");
    return () ->
      {
        if (_readStdin)
          {
            new Pretty(SourceFile.STDIN);
          }
        else if (_executeCode != null)
          {
            new Pretty(SourceFile.COMMAND_LINE_DUMMY, _executeCode);
          }
        else
          {
            for (var s : sourceFiles)
              {
                new Pretty(Path.of(s));
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
   * For more information see: {@see https://ace.c9.io/#nav=higlighter&api=tokenizer}
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
    boolean nextIsCode = false;
    ArrayList<String> applicationArgs = new ArrayList<>();
    boolean getApplicationArgs = false;

    for (var a : args)
      {
        if (getApplicationArgs || _backend.takesApplicationArgs() && (_readStdin || _main != null || _executeCode != null))
          {
            applicationArgs.add(a);
          }
        else if (nextIsCode)
          {
            executeCode(a);
            nextIsCode = false;
          }
        else if (!parseGenericArg(a))
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
            else if (parseExecute(a))
              {
                nextIsCode = true;
              }
            else if (_backend.takesApplicationArgs() && a.equals("--"))
              {
                /* stop argument parsing */
                getApplicationArgs = true;
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
            else if (a.startsWith("-XloadBaseModule="        )) { _loadBaseMod             = parseOnOffArg(a);          }
            else if (a.startsWith("-modules="                )) { _modules.addAll(parseStringListArg(a));               }
            else if (a.startsWith("-XdumpModules="           )) { _dumpModules             = parseStringListArg(a);     }
            else if (a.startsWith("-sourceDirs="             )) { _sourceDirs = new List<>(); _sourceDirs.addAll(parseStringListArg(a)); }
            else if (a.startsWith("-moduleDirs="             )) {                             _moduleDirs.addAll(parseStringListArg(a)); }
            else if (_backend.runsCode() && a.matches("-debug(=\\d+|)"       )) { _debugLevel              = parseIntArg(a, 1); }
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
    checkMissingCode(nextIsCode);
    if (_backend == Backend.undefined && args.length > 0)
      {
        _backend = Backend.jvm;
      }
    if (_backend.needsMain() && _main == null && !_readStdin && _executeCode == null)
      {
        if (applicationArgs.size() >= 1)
          {
            String mainOrStdin = applicationArgs.remove(0);
            _readStdin = mainOrStdin.equals("-");
            _main = _readStdin ? null : mainOrStdin;
          }
        else
          {
            fatal("missing main feature name in command line args");
          }
      }
    if (_backend.needsMain())
      {
        checkExactlyOneInputSource(_main != null, "main feature name or source file");
      }
    else
      {
        if (_main != null)
          {
            fatal("no main feature '" + _main + "' may be given for backend '" + _backend + "'");
          }
        if (_readStdin)
          {
            fatal("no '-' to read from stdin may be given for backend '" + _backend + "'");
          }
        if (_executeCode != null)
          {
            fatal("no '-e/-execute <code>' argument may be given for backend '" + _backend + "'");
          }
      }
    if (_fuzionHome == null)
      {
        fatal("neither property '" + FuzionConstants.FUZION_HOME_PROPERTY + "' is set nor argument '-XfuzionHome=<path>' is given");
      }
    return () ->
      {
        var options = new FrontEndOptions(_verbose,
                                          _fuzionHome,
                                          _loadBaseMod,
                                          _eraseInternalNamesInMod,
                                          _modules,
                                          _moduleDirs,
                                          _dumpModules,
                                          _debugLevel,
                                          _safety,
                                          _enableUnsafeIntrinsics,
                                          _sourceDirs,
                                          _readStdin,
                                          _executeCode,
                                          _main,
                                          moduleName(),
                                          _backend.needsSources(),
                                          _backend.needsEscapeAnalysis(),
                                          _backend.serializeFuir(),
                                          s -> timer(s));
        options.setBackendArgs(applicationArgs);
        timer("prep");
        if (!options.serializeFuir())
          {
            var fe = new FrontEnd(options);
            timer("fe");
            Errors.showAndExit();
            _backend.processFrontEnd(this, fe);
            timer("be");
          }
        else
          {
            if(CHECKS) check
              (options.needsEscapeAnalysis() == true, _backend != Backend.effects);

            Path fuirFile = fuirFile(options);
            if (!Files.exists(fuirFile))
              {
                var fe = new FrontEnd(options);                       timer("fe");
                Errors.showAndExit();
                var mir  = fe.createMIR();                            timer("createMIR");
                var fuir = new Optimizer(options, fe, mir).fuir();    timer("ir");
                var dfuir = new DFA(options, fuir).new_fuir();        timer("dfa");
                var data = dfuir.serialize();                         timer("serializeFUIR");

                try (FileOutputStream stream = new FileOutputStream(fuirFile.toFile()))
                  {
                    stream.write(data);
                  }
                catch (IOException e)
                  {
                    Errors.fatal(e);
                  }
              }
            try
              {
                var fuir = new LibraryFuir(Files.readAllBytes(fuirFile));
                timer("loadFUIR");
                _backend.process(options, fuir);
                timer("be");
              }
            catch (IOException e)
              {
                Errors.fatal(e);
              }
          }
        options.verbosePrintln(1, "Elapsed time for phases: " + _times);
      };
  }


  /**
   * determine the path of the .fuir-file
   *
   * The filename that is automatically chosen is currently
   * Arrays.hashCode() over the (first/main) code
   * and the file extension fuir.
   */
  private Path fuirFile(FrontEndOptions options)
  {
    long hashCode = -1;
    try
      {
        var bytes = _readStdin
          ? System.in.readAllBytes()
          : options.inputFile() != null
            ? Files.readAllBytes(options.inputFile())
            : _executeCode;

        hashCode = Arrays.hashCode(bytes) + Integer.MAX_VALUE;

        if (_readStdin)
          {
            System.setIn(new ByteArrayInputStream(bytes));
          }
      }
    catch (IOException e)
      {
        Errors.fatal("I/O Error: " + e.getMessage());
      }

    return Path.of(hashCode + ".fuir");
  }


  /**
   * The name of the module we are compiling.
   */
  private String moduleName()
  {
    var n = FuzionConstants.MAIN_MODULE_NAME;
    if (_saveMod != null)
       {
         var p = _saveMod;
         n = p.getFileName().toString();
         var sfx = FuzionConstants.MODULE_FILE_SUFFIX;
         if (n.endsWith(sfx))
           {
             n = n.substring(0, n.length() - sfx.length());
           }
       }
    return n;
  }


}

/* end of file */
