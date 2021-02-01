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
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
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
   * Ser of errors that have been shown so far. This is used to avoid presenting
   * error repeatedly.
   */
  private static final TreeSet<Error> _errors_ = new TreeSet<>();


  /**
   * If two errors at the same posisition with the same message only differ in
   * the detail message, chances are high that the detail message differs only
   * due to AST conversions done meanwhile, so we produce only one error in this
   * case.
   */
  private static final boolean DISTINGUISH_BY_DETAILMESSAGE = false;


  /**
   * Total number of errors encountered so far
   */
  private static int _count_ = 0;


  /**
   * Maximum number of error messages that are displayed. If this limit is
   * reached, we terminate with return code 1.
   */
  public static int MAX_ERROR_MESSAGES = Integer.getInteger("fuzion.maxErrorCount", Integer.MAX_VALUE);


  /*-----------------------------  classes  -----------------------------*/


  static class Error implements Comparable
  {
    SourcePosition pos;
    String msg, detail;

    Error(SourcePosition pos, String msg, String detail)
    {
      if (PRECONDITIONS) require
        (pos != null,
         msg != null,
         detail != null);

      this.pos    = pos;
      this.msg    = msg == null ? "*** unknown error ***" : msg;  // just in case, should not be needed
      this.detail = detail == null ? "" : detail;
    }

    /**
     * Compare two errors. Compares first by the source code position, such that
     * we can easily print them in order if we wish to.
     */
    public int compareTo(Object other)
    {
      Error o = (Error) other;
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
    return _count_;
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
    _count_++;
    return Terminal.BOLD_RED + "error " + _count_ + Terminal.RESET + ": " + s;
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
    return Terminal.BOLD_YELLOW + "warning" + Terminal.RESET + ": " + s;
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
   */
  public static void error(String s, String detail)
  {
    println(errorMessage(s));
    if (detail != null && !detail.equals(""))
      {
        println(detail);
      }
    println("");
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
   */
  public static void error(SourcePosition pos, String msg, String detail)
  {
    if (PRECONDITIONS) require
      (pos != null,
       msg != null,
       detail != null);

    Error e = new Error(pos, msg, detail);
    if (!_errors_.contains(e))
      {
        _errors_.add(e);
        if (true)  // true: a blank line before errors, false: sepration line between errors
          {
            System.err.println();
          }
        else
          {
            if (_count_ > 0)
              {
                System.err.println("------------");
              }
          }
        pos.show(errorMessage(msg), detail);
        if (_count_ >= MAX_ERROR_MESSAGES)
          {
            showStatistics();
            System.exit(1);
          }
      }
  }


  /**
   * Record the given error found during compilation and exit immediately with
   * exit code 1.
   */
  public static void fatal(String s)
  {
    error(s);
    System.err.println("*** fatal errors encountered, stopping.");
    System.exit(1);
  }


  /**
   * Record the given runtime error found and exit immediately with exit code 1.
   */
  public static void runTime(String s)
  {
    error(s);
    System.err.println("*** fatal errors encountered, stopping.");
    System.exit(1);
  }


  /**
   * Show number of errors encountered during compilation.
   */
  public static void showStatistics()
  {
    println(singularOrPlural(_count_, "error"));
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
    System.err.println(warningMessage(msg));
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
    error(pos,
          "Inconsistent indentation",
          "Indentation reference point is " + firstPos.show() + "\n" +
          detail);
  }

  public static void syntax(SourcePosition pos, String expected, String found, String detail)
  {
    error(pos,
          "Syntax error: expected " + expected + ", found " + found + "\n",
          detail);
  }

  public static void lineBreakNotAllowedHere(SourcePosition pos, String detail)
  {
    error(pos,
          "No line break may occur at this position",
          "This code is part of an expression that must reside within a single line.\n" +
          detail + "\n" +
          "To solve this, enclose the expression in parentheses '(' and ')'.");
  }

}

/* end of file */
