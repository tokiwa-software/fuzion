/*

This file is part of the Fuzion language server protocol implementation.

The Fuzion language server protocol implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language server protocol implementation is distributed in the hope that it will be
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
 * Source of class ErrorHandling
 *
 *---------------------------------------------------------------------*/

package dev.flang.shared;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class ErrorHandling
{
  public static Throwable CurrentStacktrace()
  {
    var throwable = new Throwable();
    throwable.fillInStackTrace();
    return throwable;
  }

  public static String toString(Throwable th)
  {
    if (th == null)
      {
        return "";
      }
    var stackTraceString = toString(th.getStackTrace());
    return th.getMessage() + System.lineSeparator()
      + stackTraceString + System.lineSeparator()
      + toString(th.getCause());
  }

  private static String toString(StackTraceElement[] stackTrace)
  {
    var stackTraceString = Arrays.stream(stackTrace)
      .map(st -> st.toString())
      .collect(Collectors.joining(System.lineSeparator()));
    return stackTraceString;
  }

  public static String WriteStackTrace()
  {
    return WriteStackTrace(CurrentStacktrace());
  }

  public static String WriteStackTrace(Throwable e)
  {
    var stackTrace = toString(e) + System.lineSeparator()
      + "======" + System.lineSeparator()
      + Thread.getAllStackTraces()
        .entrySet()
        .stream()
        .map(entry -> "Thread: " + entry.getKey().getName() + System.lineSeparator() + toString(entry.getValue()))
        .collect(Collectors.joining(System.lineSeparator()));

    return IO
      .writeToTempFile(stackTrace + System.lineSeparator() + SourceText.allTexts(), "fuzion-lsp-crash", ".log", false)
      .getAbsolutePath();
  }

  /**
   * @param <T>
   * @param callable
   * @param defaultValue
   * @return result of callable or in the case of an exception a default value
   */
  public static <T> T ResultOrDefault(Callable<T> callable, T defaultValue)
  {
    try
      {
        return callable.call();
      }
    catch (Throwable e)
      {
        return defaultValue;
      }
  }

}
