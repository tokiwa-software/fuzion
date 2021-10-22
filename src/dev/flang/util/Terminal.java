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
 * Source of class Terminal
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;


/**
 * Terminal provides constants to modify the text output style and color.
 *
 * Thanks to Bruno Penteado who collected these codes at
 *
 *   https://gist.github.com/bcap/5682077#file-terminal-control-sh
 *
 * but this also includes input from
 *
 *   https://en.wikipedia.org/wiki/ANSI_escape_code
 *
 * and
 *
 *   man console_codes
 *
 * Why does this have to be so messy?
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Terminal extends ANY
{

  /**
   * Are ANSI escapes enabled.
   *
   * Would be nice to check this via isattay(stdout), but this does not work in
   * Java. Alternatives such as System.console() != null do not work, since
   * System.console() is null if stdin is a file. System.getenv("TERM") also
   * does not work, this remains set if stdout/stderr is piped into a file.
   */
  public static final boolean ENABLED =
    !"true".equals(System.getProperty("FUZION_DISABLE_ANSI_ESCAPES")) &&
    !"true".equals(System.getenv     ("FUZION_DISABLE_ANSI_ESCAPES"));

  public static final String RESET                     = ENABLED ? "\033[0m" : "";
  public static final String BOLD                      = ENABLED ? "\033[1m" : "";
  public static final String BOLD_OFF                  = ENABLED ? "\033[22m" : "";  // yes, 22, not 21! 21 is underline
  public static final String ITALICS                   = ENABLED ? "\033[3m" : "";
  public static final String ITALICS_OFF               = ENABLED ? "\033[23m" : "";
  public static final String UNDERLINE                 = ENABLED ? "\033[4m" : "";   // also called underscore
  public static final String UNDERLINE_OFF             = ENABLED ? "\033[24m" : "";
  public static final String BLINK                     = ENABLED ? "\033[5m" : "";
  public static final String BLINK_OFF                 = ENABLED ? "\033[25m" : "";
  public static final String INVERSE                   = ENABLED ? "\033[7m" : "";
  public static final String INVERSE_OFF               = ENABLED ? "\033[27m" : "";
  public static final String HIDDEN                    = ENABLED ? "\033[8m" : "";
  public static final String HIDDEN_OFF                = ENABLED ? "\033[28m" : "";
  public static final String RESET_STYLE               = ENABLED ? "\033[22;23;24;25;27;28m" : "";

  public static final String BLACK                     = ENABLED ? "\033[30m" : "";
  public static final String RED                       = ENABLED ? "\033[31m" : "";
  public static final String GREEN                     = ENABLED ? "\033[32m" : "";
  public static final String YELLOW                    = ENABLED ? "\033[33m" : "";
  public static final String BLUE                      = ENABLED ? "\033[34m" : "";
  public static final String PURPLE                    = ENABLED ? "\033[35m" : "";
  public static final String CYAN                      = ENABLED ? "\033[36m" : "";
  public static final String WHITE                     = ENABLED ? "\033[37m" : "";
  public static final String REGULAR_COLOR             = ENABLED ? "\033[39m" : "";

  public static final String PLAIN_BLACK               = ENABLED ? "\033[0;30m" : "";
  public static final String PLAIN_RED                 = ENABLED ? "\033[0;31m" : "";
  public static final String PLAIN_GREEN               = ENABLED ? "\033[0;32m" : "";
  public static final String PLAIN_YELLOW              = ENABLED ? "\033[0;33m" : "";
  public static final String PLAIN_BLUE                = ENABLED ? "\033[0;34m" : "";
  public static final String PLAIN_PURPLE              = ENABLED ? "\033[0;35m" : "";
  public static final String PLAIN_CYAN                = ENABLED ? "\033[0;36m" : "";
  public static final String PLAIN_WHITE               = ENABLED ? "\033[0;37m" : "";
  public static final String PLAIN_REGULAR_COLOR       = ENABLED ? "\033[0;39m" : "";

  public static final String INTENSE_BLACK             = ENABLED ? "\033[0;90m" : "";
  public static final String INTENSE_RED               = ENABLED ? "\033[0;91m" : "";
  public static final String INTENSE_GREEN             = ENABLED ? "\033[0;92m" : "";
  public static final String INTENSE_YELLOW            = ENABLED ? "\033[0;93m" : "";
  public static final String INTENSE_BLUE              = ENABLED ? "\033[0;94m" : "";
  public static final String INTENSE_PURPLE            = ENABLED ? "\033[0;95m" : "";
  public static final String INTENSE_CYAN              = ENABLED ? "\033[0;96m" : "";
  public static final String INTENSE_WHITE             = ENABLED ? "\033[0;97m" : "";

  public static final String BOLD_BLACK                = ENABLED ? "\033[1;30m" : "";
  public static final String BOLD_RED                  = ENABLED ? "\033[1;31m" : "";
  public static final String BOLD_GREEN                = ENABLED ? "\033[1;32m" : "";
  public static final String BOLD_YELLOW               = ENABLED ? "\033[1;33m" : "";
  public static final String BOLD_BLUE                 = ENABLED ? "\033[1;34m" : "";
  public static final String BOLD_PURPLE               = ENABLED ? "\033[1;35m" : "";
  public static final String BOLD_CYAN                 = ENABLED ? "\033[1;36m" : "";
  public static final String BOLD_WHITE                = ENABLED ? "\033[1;37m" : "";

  public static final String UNDERLINE_BLACK           = ENABLED ? "\033[4;30m" : "";
  public static final String UNDERLINE_RED             = ENABLED ? "\033[4;31m" : "";
  public static final String UNDERLINE_GREEN           = ENABLED ? "\033[4;32m" : "";
  public static final String UNDERLINE_YELLOW          = ENABLED ? "\033[4;33m" : "";
  public static final String UNDERLINE_BLUE            = ENABLED ? "\033[4;34m" : "";
  public static final String UNDERLINE_PURPLE          = ENABLED ? "\033[4;35m" : "";
  public static final String UNDERLINE_CYAN            = ENABLED ? "\033[4;36m" : "";
  public static final String UNDERLINE_WHITE           = ENABLED ? "\033[4;37m" : "";

  public static final String INTENSE_BOLD_BLACK        = ENABLED ? "\033[1;90m" : "";
  public static final String INTENSE_BOLD_RED          = ENABLED ? "\033[1;91m" : "";
  public static final String INTENSE_BOLD_GREEN        = ENABLED ? "\033[1;92m" : "";
  public static final String INTENSE_BOLD_YELLOW       = ENABLED ? "\033[1;93m" : "";
  public static final String INTENSE_BOLD_BLUE         = ENABLED ? "\033[1;94m" : "";
  public static final String INTENSE_BOLD_PURPLE       = ENABLED ? "\033[1;95m" : "";
  public static final String INTENSE_BOLD_CYAN         = ENABLED ? "\033[1;96m" : "";
  public static final String INTENSE_BOLD_WHITE        = ENABLED ? "\033[1;97m" : "";

  public static final String BACKGROUND_BLACK          = ENABLED ? "\033[40m" : "";
  public static final String BACKGROUND_RED            = ENABLED ? "\033[41m" : "";
  public static final String BACKGROUND_GREEN          = ENABLED ? "\033[42m" : "";
  public static final String BACKGROUND_YELLOW         = ENABLED ? "\033[43m" : "";
  public static final String BACKGROUND_BLUE           = ENABLED ? "\033[44m" : "";
  public static final String BACKGROUND_PURPLE         = ENABLED ? "\033[45m" : "";
  public static final String BACKGROUND_CYAN           = ENABLED ? "\033[46m" : "";
  public static final String BACKGROUND_WHITE          = ENABLED ? "\033[47m" : "";
  public static final String BACKGROUND_REGULAR_COLOR  = ENABLED ? "\033[49m" : "";

  public static final String INTENSE_BACKGROUND_BLACK  = ENABLED ? "\033[0;100m" : "";
  public static final String INTENSE_BACKGROUND_RED    = ENABLED ? "\033[0;101m" : "";
  public static final String INTENSE_BACKGROUND_GREEN  = ENABLED ? "\033[0;102m" : "";
  public static final String INTENSE_BACKGROUND_YELLOW = ENABLED ? "\033[0;103m" : "";
  public static final String INTENSE_BACKGROUND_BLUE   = ENABLED ? "\033[0;104m" : "";
  public static final String INTENSE_BACKGROUND_PURPLE = ENABLED ? "\033[0;105m" : "";
  public static final String INTENSE_BACKGROUND_CYAN   = ENABLED ? "\033[0;106m" : "";
  public static final String INTENSE_BACKGROUND_WHITE  = ENABLED ? "\033[0;107m" : "";

}

/* end of file */
