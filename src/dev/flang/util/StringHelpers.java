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
 * Source code of class StringHelpers
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

/**
 * StringHelpers contains String related methods that turned out useful in the
 * Fuzion implementation.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class StringHelpers extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /*--------------------------  static methods  -------------------------*/


  /**
   * wrap given string in parentheses iff it contains white space (@see
   * isWhiteSpace) that is not already wrapped in parentheses.
   *
   * @param s a string like "a b" or "(a b).c"
   *
   * @return a string without any white space that is not wrapped in
   * parentheses, e.g., "(a b)" or "(a b).c" (for the examples above).
   */
  public static String wrapInParentheses(String s)
  {
    return containsWhiteSpaceOutsideParentheses(s) ? "(" + s + ")"
                                                   : s;
  }


  /**
   * Is the given character a white space character. white space is SPACE, TAB,
   * LF, CR, or FF.
   *
   * @param c a character
   *
   + @return true iff c is white space.
   */
  public static boolean isWhiteSpace(char c)
  {
    return switch (c)
      {
      case  '\t', '\n', '\f', '\r', ' ' -> true;
      default                           -> false;
      };
  }


  /**
   * Does the given String contain white space (@sww isWhiteSpace) that is not
   * wrapped in parentheses?
   *
   * @param s a String like "a b" or "(a b).c"
   *
   * @return true iff s contains unwrapped white space as in "a b", false if it
   * does not as in "(a b).c".
   */
  public static boolean containsWhiteSpaceOutsideParentheses(String s)
  {
    var result = false;
    int level = 0;
    for(var i = 0; i < s.length(); i++)
      {
        var c = s.charAt(i);
        switch (s.charAt(i))
          {
          case '(' : level ++; break;
          case ')' : level --; break;
          default  : result = result || level == 0 && isWhiteSpace(c); break;
          }
      }
    return result;
  }


  /**
   * Create a string representation for an argument count.
   * @param count the number of arguments
   * @return a string like "no arguments", "one argument", "2 arguments"
   */
  public static String argumentsString(int count)
  {
    return singularOrPlural(count, "argument");
  }

  public static String singularOrPlural(int count, String what)
  {
    return
      count == 0 ? "no " + plural(what) :
      count == 1 ? "one " + what
                 : "" + count + " " + plural(what);
  }


  /**
   * Build plural of a noun iff count is not 1.
   *
   * @param count a counter
   *
   * @param what a noun like "car", "baby", etc.
   *
   * @return what iff count==1, otherwise the plural form "cars", "babies", etc.
   */
  public static String plural(int count, String what)
  {
    if (PRECONDITIONS) require
      (count >= 0);

    return count == 1 ? what : plural(what);
  }


  /**
   * Build plural of a noun
   *
   * @param what a noun like "car", "baby", etc.
   *
   * @return the plural form "cars", "babies", etc.
   */
  public static String plural(String what)
  {
    return what.endsWith("y") ? what.substring(0, what.length()-1) + "ies"
                              : what + "s";
  }


  /**
   * Create a string like "never", "once", "twice", "3 times, "4 times", "-1 times".
   *
   * @param count the number of times something happened.
   */
  public static String times(int count)
  {
    return
      count == 0 ? "never"  :
      count == 1 ? "once "  :
      count == 2 ? "twice " : "" + count + " times";
  }


  /**
   * Create a string like "... repeated 4 times ...", "... repeated twice ..."
   *
   * @param count the number of times something was repeated
   */
  public static String repeated(int count)
  {
    if (PRECONDITIONS) require
      (count > 0);

    return "... repeated " + StringHelpers.times(count) + " ...";
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
}

/* end of file */
