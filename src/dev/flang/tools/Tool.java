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
 * Source of class Tool
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools;

import java.nio.file.Path;

import java.util.TreeSet;
import java.util.Set;

import dev.flang.be.c.C;
import dev.flang.be.c.COptions;

import dev.flang.be.interpreter.Intrinsics;
import dev.flang.be.interpreter.Interpreter;

import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;

import dev.flang.me.MiddleEnd;

import dev.flang.opt.Optimizer;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.Profiler;


/**
 * Tool provides features like argument parsing used in different Fuzion tools.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class Tool extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Placeholder within USAGE0() result to hold standard options.
   */
  public static final String STD_OPTIONS = "@STANDARD_OPTIONS@";

  private static final String XTRA_OPTIONS = "[-X|--Xhelp] [-XjavaProf] " +
    "[" + Errors.MAX_ERROR_MESSAGES_OPTION   + "=<n>] " +
    "[" + Errors.MAX_WARNING_MESSAGES_OPTION + "=<n>] ";


  /*----------------------------  variables  ----------------------------*/


  /**
   * This command.  This might be different to the command entered by the user,
   * e.g., if the user executes a shell script that starts the JVM.
   */
  protected final String _rawCmd;


  /**
   * The actual command entered by the user.  This is typically a command that
   * executes a shell script that starts the JVM.
   */
  protected final String _cmd;


  /**
   * Level of verbosity of output
   */
  public int _verbose = Integer.getInteger("fuzion.verbose", 0);


  /**
   * Set of arguments parseGenericArg was already called with, used to determine
   * duplicates.
   */
  private Set<String> _duplicates = new TreeSet<>();


  /**
   * The arguments passed to this command.
   */
  private String[] _args;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for the Fuzion class
   *
   * @param name the default tool command name
   *
   * @param args the command line arguments.
   */
  protected Tool(String name, String[] args)
  {
    _rawCmd = name;
    _cmd = System.getProperty("fuzion.command", name);
    _args = args;
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
    return "[-noANSI] [-verbose[=<n>]] " + (xtra ? XTRA_OPTIONS : "");
  }


  /**
   * The basic usage, using STD_OPTIONS as a placeholder for standard
   * options.
   */
  protected abstract String USAGE0();


  /**
   * The usage, created from USAGE0() by adding STANDARD_OPTIONS().
   *
   * @param xtra include extra options
   */
  protected final String USAGE(boolean xtra)
  {
    return USAGE0().replace(STD_OPTIONS, STANDARD_OPTIONS(xtra));
  }


  /**
   * parse args and run this tool
   */
  protected void run()
  {
    try
      {
        parseArgs(_args).run();
        Errors.showAndExit(true);
      }
    catch(Throwable e)
      {
        e.printStackTrace();
        System.exit(1);
      }
  }


  /**
   * The command string including all arguments.
   */
  public String command()
  {
    StringBuilder result = new StringBuilder();
    result.append(_cmd);
    for (var a : _args)
      {
        if (a.indexOf(" ") >= 0)
          {
            a = "'" + a + "'";
          }
        result.append(" ").append(a);
      }
    return result.toString();
  }


  /**
   * Return the version number of this tool
   */
  public String version()
  {
    return Version.VERSION;
  }


  /**
   * Return the full version information of this tool, including build date, git
   * hash, built by.
   */
  public String fullVersion()
  {
    return Version.VERSION + " (" + Version.DATE + " GIT hash " + Version.GIT_HASH + " built by " + Version.BUILTBY + ")";
  }


  /**
   * Parse the given command line args and create a runnable to run the
   * corresponding tool.  System.exit() in case of error or -help.
   *
   * @param args the command line arguments
   *
   * @return a Runnable to run the selected tool.
   */
  public abstract Runnable parseArgs(String[] args);


  /**
   * Parse the given argument against generic arguments (those that can always
   * be applied).
   *
   * @param a the command line argument
   *
   * @return true iff a was a generic argument and was parsed, false if a still
   * needs to be handled.
   */
  protected boolean parseGenericArg(String a)
  {
    if (_duplicates.contains(a))
      {
        fatal("duplicate argument: '" + a + "'");
      }
    _duplicates.add(a);
    if (a.equals("-h"    ) ||
        a.equals("-help" ) ||
        a.equals("--help")    )
      {
        System.out.println(USAGE(false));
        System.exit(0);
      }
    else if (a.equals("-X"     ) ||
             a.equals("-Xhelp" ) ||
             a.equals("--Xhelp")    )
      {
        System.out.println(USAGE(true));
        System.exit(0);
      }
    else if (a.equals("-version"))
      {
        System.out.println(_rawCmd + " V" + fullVersion()); ;
        System.exit(0);
      }
    else if (a.equals("-XjavaProf"))
      {
        Profiler.start();
      }
    else if (a.startsWith(Errors.MAX_ERROR_MESSAGES_OPTION) && a.startsWith(Errors.MAX_ERROR_MESSAGES_OPTION + "="))
      {
        Errors.MAX_ERROR_MESSAGES = Integer.parseInt(a.substring(a.indexOf("=")+1));
      }
    else if (a.startsWith(Errors.MAX_WARNING_MESSAGES_OPTION) && a.startsWith(Errors.MAX_WARNING_MESSAGES_OPTION + "="))
      {
        Errors.MAX_WARNING_MESSAGES = Integer.parseInt(a.substring(a.indexOf("=")+1));
      }
    else if (a.equals("-noANSI"))
      {
        System.setProperty("FUZION_DISABLE_ANSI_ESCAPES","true");
      }
    else if (a.matches("-verbose(=\\d+|)"))
      {
        _verbose = parsePositiveIntArg(a, 1);
      }
    else
      {
        return false;
      }
    return true;
  }


  /**
   * Create a fatal error for a problem related to argument parsing or
   * processing.  Show 'msg' together with the usage.
   *
   * @param msg the message.
   */
  protected void fatal(String msg)
  {
    Errors.fatal(msg, USAGE(false));
  }


  /**
   * Create a fatal error for an unknown argument
   *
   * @param a the argument that is unknown.
   */
  protected void unknownArg(String a)
  {
    if (a.startsWith("-"))
      {
        fatal("unknown argument: '" + a + "'");
      }
    else
      {
        fatal("unknown argument: '" + a + "'");
      }
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
  protected int parsePositiveIntArg(String a, int defawlt)
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
   * Parse argument of the form "-xyz=<string>"
   *
   * @param a the argument
   *
   * @return the string after the first "=" in a, may be empty, may not be null
   */
  protected static String parseString(String a)
  {
    if (PRECONDITIONS) require
      (a.indexOf("=") >= 0);

    return a.substring(a.indexOf("=")+1);
  }


  /**
   * Parse argument of the form "-xyz=<path>"
   *
   * @param a the argument
   *
   * @return the path after the first "=" in a.
   */
  protected static Path parsePath(String a)
  {
    if (PRECONDITIONS) require
      (a.indexOf("=") >= 0);

    return Path.of(parseString(a));
  }


  /**
   * Parse argument of the form "-xyz=on" or "-xyz=off".
   *
   * @param a the argument
   *
   * @return true iff a is set to 'on' or an error was reported.
   */
  protected static boolean parseOnOffArg(String a)
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
                       "While analysing command line argument '" + a + "'.  Parameter must be 'on' or 'off'");
          yield true;
        }
        };
  }


  /**
   * Parse argument of the form "-xyz=abc,def,ghi".
   *
   * @param a the argument
   *
   * @return the list containing the single elements, e.g. ["abc","def","ghi"]
   */
  protected static List<String> parseStringListArg(String a)
  {
    if (PRECONDITIONS) require
      (a.indexOf("=") >= 0);

    List<String> result = new List<>();
    for (var s : a.substring(a.indexOf("=")+1).split(","))
      {
        result.add(s);
      }
    return result;
  }


}

/* end of file */
