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
 * Source of class Errors
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;


/**
 * Errors handles compilation error messages for Fuzion
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Errors extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * String used for identifier, operators, etc. if their name is unknown due to
   * an error.
   */
  public static final String ERROR_STRING = "**error**";


  /*------------------------  static variables  -------------------------*/


  /**
   * Set of errors that have been shown so far. This is used to avoid presenting
   * error repeatedly.
   */
  private static final TreeSet<Error> _errors_ = new TreeSet<>();


  /**
   * Positions that produced a syntax error. If a syntax error occured, all
   * other errors at this position will be supressed.
   */
  private static final TreeSet<SourcePosition> _syntaxErrorPositions_ = new TreeSet<>();


  /**
   * Set of warnings that have been shown so far. This is used to avoid presenting
   * error repeatedly.
   */
  private static final TreeSet<Error> _warnings_ = new TreeSet<>();


  /**
   * If two errors at the same posisition with the same message only differ in
   * the detail message, chances are high that the detail message differs only
   * due to AST conversions done meanwhile, so we produce only one error in this
   * case.
   */
  private static final boolean DISTINGUISH_BY_DETAILMESSAGE = false;


  /**
   * Maximum number of error messages that are displayed. If this limit is
   * reached, we terminate with return code 1.
   */
  public static String MAX_ERROR_MESSAGES_PROPERTY = "fuzion.maxErrorCount";
  public static String MAX_ERROR_MESSAGES_OPTION = "-XmaxErrors";
  public static int MAX_ERROR_MESSAGES = Integer.getInteger(MAX_ERROR_MESSAGES_PROPERTY, 20);


  /**
   * Maximum number of warning messages that are displayed. If this limit is
   * reached, we stop printing further warnings.
   */
  public static String MAX_WARNING_MESSAGES_PROPERTY = "fuzion.maxWarningCount";
  public static String MAX_WARNING_MESSAGES_OPTION = "-XmaxWarnings";
  public static int MAX_WARNING_MESSAGES = Integer.getInteger(MAX_WARNING_MESSAGES_PROPERTY, Integer.MAX_VALUE);


  /*-----------------------------  classes  -----------------------------*/


  static class Error implements Comparable<Error>
  {
    SourcePosition pos;
    String msg, detail;

    Error(SourcePosition pos, String msg, String detail)
    {
      if (PRECONDITIONS) require
        (pos != null,
         msg != null);

      this.pos    = pos;
      this.msg    = msg == null ? "*** unknown error ***" : msg;  // just in case, should not be needed
      this.detail = detail == null ? "" : detail;
    }

    /**
     * Compare two errors. Compares first by the source code position, such that
     * we can easily print them in order if we wish to.
     */
    public int compareTo(Error o)
    {
      int result = ((pos == o.pos)
                    ? 0
                    : ((pos == null)
                       ? +1
                       : pos.compareTo(o.pos)));
      if (result == 0)
        {
          result = msg.compareTo(o.msg);
        }
      if (result == 0)
        {
          if (DISTINGUISH_BY_DETAILMESSAGE)
            {
              result = detail.compareTo(o.detail);
            }
        }
      return result;
    }
  }

  /*--------------------------  constructors  ---------------------------*/



  /*-----------------------------  methods  -----------------------------*/


  /**
   * Total number of errors encountered so far
   */
  public static int count()
  {
    return _errors_.size();
  }


  /**
   * Total number of warnings encountered so far
   */
  public static int warningCount()
  {
    return _warnings_.size();
  }


  /**
   * Convert given message into an error message preceded by "error <count>: "
   * and increment the count.
   *
   * @param s a message, e.g., "undefined variable".
   *
   * @return a message including error count, e..g, "error 23: undefined variable".
   */
  static String errorMessage(String s)
  {
    return Terminal.BOLD_RED + "error " + count() + Terminal.RESET + Terminal.BOLD + ": " + s + Terminal.RESET;
  }


  /**
   * Convert given message into a warning message preceded by "warning: ".
   *
   * @param s a message, e.g., "battery low".
   *
   * @return a message including error count, e..g, "warning: battery low".
   */
  static String warningMessage(String s)
  {
    return Terminal.BOLD_YELLOW + "warning " + warningCount() + Terminal.RESET + Terminal.BOLD + ": " + s + Terminal.RESET;
  }


  /**
   * Print the given string to System.err, add a LF in case s does not end with
   * a LF.
   *
   * @param s the string to print.
   */
  public static void println(String s)
  {
    if (s.endsWith("\n"))
      {
        System.err.print(s);
      }
    else
      {
        System.err.println(s);
      }
  }

  /**
   * Record the given error found during compilation.
   *
   * @param s the error message, should not contain any LF or any case specific details
   *
   * @param detail details for this error, may contain LFs and case specific details, may be null
   */
  public static void error(String s, String detail)
  {
    error(null, s, detail);
  }


  /**
   * Record the given error found during compilation.
   */
  public static void error(String s)
  {
    error(s, null);
  }


  /**
   * Record the given error found during compilation.
   *
   * @param pos source code position where this error occured, may be null
   *
   * @param msg the error message, should not contain any LF or any case specific details
   *
   * @param detail details for this error, may contain LFs and case specific details, may be null
   */
  public static void error(SourcePosition pos, String msg, String detail)
  {
    if (PRECONDITIONS) require
      (msg != null);

    Error e = new Error(pos == null ? SourcePosition.builtIn : pos, msg, detail);
    if (!_errors_.contains(e) && (pos == null || !_syntaxErrorPositions_.contains(pos)))
      {
        _errors_.add(e);
        print(pos, errorMessage(msg), detail);
        if (count() >= MAX_ERROR_MESSAGES)
          {
            showAndExit();
          }
        //Thread.dumpStack();
      }
  }


  /**
   * Record the given syntax error found during compilation.  A syntax error
   * will automatically suppress all other errors at pos.
   *
   * @param pos source code position where this error occured, may be null
   *
   * @param msg the error message, should not contain any LF or any case specific details
   *
   * @param detail details for this error, may contain LFs and case specific details, may be null
   */
  public static void syntaxError(SourcePosition pos, String msg, String detail)
  {
    if (PRECONDITIONS) require
      (msg != null);

    error(pos, msg, detail);
    if (pos != null)
      {
        _syntaxErrorPositions_.add(pos);
      }
  }


  /**
   * print the given error or warning found during compilation.
   *
   * @param pos source code position where this error occured, may be null
   *
   * @param msg the error or warning message created by errorMessage() or
   * warningMessage().
   *
   * @param detail details for this error, may contain LFs and case specific details, may be null
   */
  private static void print(SourcePosition pos, String msg, String detail)
  {
    if (true)  // true: a blank line before errors, false: separation line between errors
      {
        System.err.println();
      }
    else
      {
        System.err.println("------------");
      }
    if (pos == null)
      {
        println(msg);
        if (detail != null && !detail.equals(""))
          {
            println(detail);
          }
        println("");
      }
    else
      {
        pos.show(msg, detail);
      }
  }


  /**
   * Record the given error found during compilation and exit immediately with
   * exit code 1.
   *
   * @param s the error message, should not contain any LF or any case specific details
   */
  public static void fatal(String s)
  {
    fatal(s, null);
  }


  /**
   * Record the given error found during compilation and exit immediately with
   * exit code 1.
   *
   * @param s the error message, should not contain any LF or any case specific details
   *
   * @param detail details for this error, may contain LFs and case specific details, may be null
   */
  public static void fatal(String s, String detail)
  {
    error(s, detail);
    System.err.println("*** fatal errors encountered, stopping.");
    System.exit(1);
  }


  /**
   * Record the given error found during compilation and exit immediately with
   * exit code 1.
   *
   * @param pos source code position where this error occured, may be null
   *
   * @param s the error message, should not contain any LF or any case specific details
   *
   * @param detail details for this error, may contain LFs and case specific details, may be null
   */
  public static void fatal(SourcePosition pos, String s, String detail)
  {
    error(pos, s, detail);
    System.err.println("*** fatal errors encountered, stopping.");
    System.exit(1);
  }


  /**
   * Record the given runtime error found and exit immediately with exit code 1.
   *
   * @param s the error message, should not contain any LF or any case specific details
   */
  public static void runTime(String s)
  {
    runTime(s, null);
  }


  /**
   * Record the given runtime error found and exit immediately with exit code 1.
   *
   * @param s the error message, should not contain any LF or any case specific details
   *
   * @param detail details for this error, may contain LFs and case specific details, may be null
   */
  public static void runTime(String s, String detail)
  {
    runTime(null, s, detail);
  }


  /**
   * Record the given runtime error found and exit immediately with exit code 1.
   *
   * @param pos source code position where this error occured, may be null
   *
   * @param s the error message, should not contain any LF or any case specific details
   *
   * @param detail details for this error, may contain LFs and case specific details, may be null
   */
  public static void runTime(SourcePosition pos, String s, String detail)
  {
    error(pos, s, detail);
    System.err.println("*** fatal errors encountered, stopping.");
    System.exit(1);
  }


  /**
   * Check if any errors found.  If so, show the number of errors and
   * System.exit(1).
   *
   * Otherwise, if warningStatistics is true, report the number of warnings
   * encountered.  Return normally in this case.
   *
   * @param warningStatistics true iff warning count should be printed.
   */
  public static void showAndExit(boolean warningStatistics)
  {
    if (count() > 0)
      {
        if (count() >= MAX_ERROR_MESSAGES)
          {
            warning(SourcePosition.builtIn,
                    "Maximum error count reached, terminating.",
                    "Maximum error count is " + MAX_ERROR_MESSAGES + ".\n" +
                    "Change this via property '" + MAX_ERROR_MESSAGES_PROPERTY + "' or command line option '" + MAX_ERROR_MESSAGES_OPTION + "'.");
          }
        println(singularOrPlural(count(), "error") +
                (warningCount() > 0 ? " and " + singularOrPlural(warningCount(), "warning")
                                    : "") +
                ".");
        System.exit(1);
      }
    else if (warningStatistics && warningCount() > 0)
      {
        println(singularOrPlural(warningCount(), "warning") + ".");
      }
  }


  /**
   * Check if any errors found.  If so, show the number of errors and
   * System.exit(1).
   */
  public static void showAndExit()
  {
    showAndExit(false);
  }

  public static String argumentsString(int count)
  {
    return singularOrPlural(count, "argument");
  }

  public static String singularOrPlural(int count, String what)
  {
    return
      count == 0 ? "no " + what + "s" :
      count == 1 ? "one " + what
                 : "" + count + " " + what + "s";
  }

  /**
   * Record the given error found during compilation.
   */
  public static void warning(String msg)
  {
    warning(null, msg, null);
  }


  /**
   * Record the given warning found during compilation.
   *
   * @param pos source code position where this warning occured, may be null
   *
   * @param msg the warning message, should not contain any LF or any case specific details
   *
   * @param detail details for this warning, may contain LFs and case specific details, may be null
   */
  public static void warning(SourcePosition pos, String msg, String detail)
  {
    if (PRECONDITIONS) require
      (msg != null);

    if (warningCount() < MAX_WARNING_MESSAGES)
      {
        if (warningCount()+1 == MAX_WARNING_MESSAGES)
          {
            pos = SourcePosition.builtIn;
            msg = "Maximum warning count reached, suppressing further warnings";
            detail = "Maximum warning count is " + MAX_WARNING_MESSAGES + ".\n" +
              "Change this via property '" + MAX_WARNING_MESSAGES_PROPERTY + "' or command line option '" + MAX_WARNING_MESSAGES_OPTION + "'.";
          }
        Error e = new Error(pos == null ? SourcePosition.builtIn : pos, msg, detail);
        if (!_warnings_.contains(e))
          {
            _warnings_.add(e);
            print(pos, warningMessage(msg), detail);
          }
      }
  }


  /**
   * Convert a positive integer to an ordinal number "first", "4th", "12th",
   * "51st", etc.
   */
  public static String ordinal(int i)
  {
    if (PRECONDITIONS) require
      (i > 0);

    return
      i == 1 ? "first"  :
      i == 2 ? "second" :
      i == 3 ? "third"  :
      i % 10 == 1 && i % 100 != 11 ? "" + i + "st" :
      i % 10 == 2 && i % 100 != 12 ? "" + i + "nd" :
      i % 10 == 3 && i % 100 != 13 ? "" + i + "rd" :
      i + "th";
  }


  public static void indentationProblemEncountered(SourcePosition pos,
                                                   SourcePosition firstPos,
                                                   String detail)
  {
    syntaxError(pos,
                "Inconsistent indentation",
                "Indentation reference point is " + firstPos.show() + "\n" +
                detail);
  }

  public static void syntax(SourcePosition pos, String expected, String found, String detail)
  {
    syntaxError(pos,
                "Syntax error: expected " + expected + ", found " + found,
                detail);
  }

  private static String legalEscapes(char[][] escapes)
  {
    var legal = new StringBuilder();
    var comma = "";
    for (var c : escapes)
      {
        legal.append(comma).append("'\\" + c[0] + "'");
        comma = ", ";
      }
    return legal.toString();
  }

  public static void unknownEscapedChar(SourcePosition pos, int found, char[][] escapes)
  {
    syntaxError(pos,
                "Unknown escaped character found in constant string.",
                "Escaped character found: '" + new StringBuilder().appendCodePoint(found) +
                "', legal escaped characters are " + legalEscapes(escapes) + ".");
  }

  public static void unterminatedString(SourcePosition pos, SourcePosition start)
  {
    syntaxError(pos,
                "Unterminated constant string.",
                "Expected double quotes '\"' to mark end of constant string starting at " + start.show());
  }

  public static void unexpectedControlCodeInString(SourcePosition pos, String controlSeq, int codePoint, SourcePosition start)
  {
    syntaxError(pos,
                "Unexpected control sequence in constant string.",
                "Found unexpected control sequence '" + controlSeq + "' (0x" + Integer.toHexString(codePoint) + ") in constant string starting at " + start.show());
  }

  public static void unexpectedEndOfLineInString(SourcePosition pos, SourcePosition start)
  {
    syntaxError(pos,
                "Unexpected end-of-line in constant string.",
                "Found unexpected end-of-line in constant string starting at " + start.show());
  }

  public static void identifierInStringExpected(SourcePosition pos, SourcePosition start)
  {
    syntaxError(pos,
                "Expected identifier immediately following '$' in constant string",
                "For string starting at " + start.show());
  }

  public static void lineBreakNotAllowedHere(SourcePosition pos, String detail)
  {
    syntaxError(pos,
                "No line break may occur at this position",
                "This code is part of an expression that must reside within a single line.\n" +
                detail + "\n" +
                "To solve this, enclose the expression in parentheses '(' and ')'.");
  }

  public static void whiteSpaceNotAllowedHere(SourcePosition pos, String detail)
  {
    syntaxError(pos,
                "No white space may occur before this position",
                "This code is part of an actual argument that must not contain white space.\n" +
                detail + "\n" +
                "To solve this, enclose the expression in parentheses '(' and ')'.");
  }

  public static void expectedStringContinuation(SourcePosition pos, String token)
  {
    syntaxError(pos,
                "Expected constant string continuation starting with closing bracket, e.g., '} done.\"'.",
                "Found " + token + " instead.");
  }

}

/* end of file */
