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
 * Source code of class ANY
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

}

/* end of file */
