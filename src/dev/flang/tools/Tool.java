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

import dev.flang.parser.Parser;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionOptions;
import dev.flang.util.List;
import dev.flang.util.Profiler;
import dev.flang.util.Version;


/**
 * Tool provides features like argument parsing used in different Fuzion tools.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class Tool extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  private static final String XTRA_OPTIONS = "[-X|--Xhelp] [-XjavaProf] [-XjavaProf=<graph.svg>] " +
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
    _cmd = FuzionOptions.propertyOrEnv(FUZION_COMMAND_PROPERTY, name);
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
   * The usage, must include STANDARD_OPTIONS(xtra).
   *
   * @param xtra include extra options
   */
  protected abstract String USAGE(boolean xtra);


  /**
   * parse args and run this tool
   */
  protected void run()
  {
    Errors.runAndExit(() -> parseArgs(_args).run());
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
  public static String fullVersion()
  {
    var result = Version.VERSION + " (";

    if (!Version.DATE.isEmpty())
      {
        result = result + Version.DATE + " ";
      }

    result = result + "GIT hash " + Version.GIT_HASH;

    if (!Version.BUILTBY.isEmpty())
      {
        result = result + " built by " + Version.BUILTBY;
      }

    result = result + ")";

    return result;
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
    if (_duplicates.contains(stripValue(a)))
      {
        fatal("duplicate argument: '" + stripValue(a) + "'");
      }
    _duplicates.add(stripValue(a));
    if (a.equals("-h"    ) ||
        a.equals("-help" ) ||
        a.equals("--help")    )
      {
        say(USAGE(false));
        System.exit(0);
      }
    else if (a.equals("-X"     ) ||
             a.equals("-Xhelp" ) ||
             a.equals("--Xhelp")    )
      {
        say(USAGE(true));
        System.exit(0);
      }
    else if (a.equals("-version"))
      {
        say(_rawCmd + " V" + fullVersion()); ;
        System.exit(0);
      }
    else if (a.equals("-XjavaProf"))
      {
        Profiler.start();
      }
    else if (a.startsWith("-XjavaProf="))
      {
        var file = a.substring(a.indexOf("=")+1);
        if (file.equals(""))
          {
            fatal("Please provide a file name to option '-XjavaProf=<file>'.");
          }
        else
          {
            Profiler.start(file);
          }
      }
    else if (a.equals(Errors.MAX_ERROR_MESSAGES_OPTION) || a.startsWith(Errors.MAX_ERROR_MESSAGES_OPTION + "="))
      {
        Errors.MAX_ERROR_MESSAGES = parseIntArg(a, -1);
      }
    else if (a.equals(Errors.MAX_WARNING_MESSAGES_OPTION) || a.startsWith(Errors.MAX_WARNING_MESSAGES_OPTION + "="))
      {
        Errors.MAX_WARNING_MESSAGES = parseIntArg(a, -1);;
      }
    else if (a.equals("-noANSI"))
      {
        System.setProperty("FUZION_DISABLE_ANSI_ESCAPES","true");
      }
    else if (a.matches("-verbose(=\\d+|)"))
      {
        _verbose = parseIntArg(a, 1);
      }
    else if (a.equals("-XenableSetKeyword"))
      {
        Parser.ENABLE_SET_KEYWORD = true;
      }
    else
      {
        return false;
      }
    return true;
  }

  private String stripValue(String option)
  {
    return option.contains("=") ? option.substring(0, option.indexOf("=")) : option;
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
   * Parse argument of the form {@code -xyz} or {@code -xyz=123}.
   *
   * @param a the argument
   *
   * @param defawlt value to be returned in case a does not specify an explicit
   * value.
   *
   * @return defawlt or the values specified in a after '='.
   */
  protected int parseIntArg(String a, int defawlt)
  {
    if (PRECONDITIONS) require
      (a.split("=").length == 1 || a.split("=").length == 2);

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
                         "While analyzing command line argument '" + a + "', encountered: '" + e + "'");
          }
      }
    return result;
  }


  /**
   * Parse argument of the form {@code -xyz=<string>}
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
   * Parse argument of the form {@code -xyz=<path>}
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
   * Parse argument of the form {@code -xyz=on} or {@code -xyz=off}.
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
                       "While analyzing command line argument '" + a + "'.  Parameter must be 'on' or 'off'");
          yield true;
        }
        };
  }


  /**
   * Parse argument of the form {@code -xyz=abc,def,ghi}.
   *
   * @param a the argument
   *
   * @return the list containing the single elements, e.g. {@code ["abc","def","ghi"]}
   */
  protected static List<String> parseStringListArg(String a)
  {
    if (PRECONDITIONS) require
      (a.indexOf("=") >= 0);

    List<String> result = new List<>();
    var strings = a.substring(a.indexOf("=")+1);
    if (!strings.equals(""))
      {
        for (var s : strings.split(","))
          {
            result.add(s);
          }
      }
    return result;
  }


  /**
   * To be called whenever a major task was completed. Will record the time
   * since last call to timer together with name to be printed when verbose
   * output is activated.
   */
  public void timer(String name)
  {
    var t = System.currentTimeMillis();
    var delta = t - _timer;
    _timer = t;
    _times.append(_times.length() == 0 ? "" : ", ").append(name).append(" ").append(delta).append("ms");
  }


  /**
   * Last time timer() was called, in System.currentTimeMillis();
   */
  long _timer = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();


  /**
   * Time required for phases recorded by timer().
   */
  public final StringBuilder _times = new StringBuilder();


}

/* end of file */
