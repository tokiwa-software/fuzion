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
 * Source of class IO
 *
 *---------------------------------------------------------------------*/

package dev.flang.shared;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class IO
{
  public static final PrintStream SYS_OUT = System.out;
  public static final PrintStream SYS_ERR = System.err;
  public static final InputStream SYS_IN = System.in;
  private static PrintStream CLIENT_OUT = System.out;
  private static PrintStream CLIENT_ERR = System.err;
  // NYI "fuzion-lsp-server" should depend on usage
  private static File tempDir =
    ErrorHandling.ResultOrDefault(() -> Files.createTempDirectory("fuzion-lsp-server").toFile(), null);

  static byte[] getBytes(String text)
  {
    byte[] byteArray = new byte[0];
    try
      {
        byteArray = text.getBytes("UTF-8");
      }
    catch (UnsupportedEncodingException e)
      {
        ErrorHandling.WriteStackTrace();
      }
    return byteArray;
  }

  public static File writeToTempFile(String text)
  {
    return writeToTempFile(text, String.valueOf(System.currentTimeMillis()), ".fz");
  }

  public static File writeToTempFile(String text, String prefix, String extension)
  {
    return writeToTempFile(text, prefix, extension, true);
  }

  public static File writeToTempFile(String text, String prefix, String extension, boolean deleteOnExit)
  {
    try
      {
        File tempFile = File.createTempFile(prefix + String.valueOf(System.currentTimeMillis()), extension, tempDir);
        if (deleteOnExit)
          {
            tempFile.deleteOnExit();
          }

        FileWriter writer = new FileWriter(tempFile);
        writer.write(text);
        writer.close();
        return tempFile;
      }
    catch (IOException e)
      {
        ErrorHandling.WriteStackTrace();
        return null;
      }
  }

  /**
   * Feed text to stdin then execute the callable.
   * @param <T>
   * @param text
   * @param callable
   * @return
   */
  public synchronized static <T> T WithTextInputStream(String text, Callable<T> callable)
  {
    byte[] byteArray = getBytes(text);
    try
      {
        System.setIn(new ByteArrayInputStream(byteArray));
        return callable.call();
      }
    catch (Exception e)
      {
        ErrorHandling.WriteStackTrace(e);
        return null;
      } finally
      {
        IO.RedirectErrOutToClientLog();
      }
  }

  /**
   * @param runnable
   * @return callable to be run on an executor.
   * The result of the callable is everything that is written to stdout/stderr by the runnable.
   */
  public synchronized static Callable<String> WithCapturedStdOutErr(Runnable runnable)
  {
    return () -> {
      var inputStream = new PipedInputStream();
      var outputStream = new PrintStream(new PipedOutputStream(inputStream));
      try
        {
          System.setOut(outputStream);
          System.setErr(outputStream);
          runnable.run();
          // close outputstream so that reading of inputstream does not run
          // inifinitly.
          outputStream.close();
          return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } finally
        {
          outputStream.close();
          inputStream.close();
          IO.RedirectErrOutToClientLog();
        }
    };
  }

  public static void RedirectErrOutToClientLog()
  {
    System.setOut(CLIENT_OUT);
    System.setErr(CLIENT_ERR);
    System.setIn(new PipedInputStream());
  }

  public static PrintStream createCapturedStream(Consumer<String> callback)
  {
    try
      {
        var inputStream = new PipedInputStream();
        var reader = new BufferedReader(new InputStreamReader(inputStream));
        var result = new PrintStream(new PipedOutputStream(inputStream));
        Concurrency.MainExecutor.submit(
          () -> {
            try
              {
                while (true)
                  {
                    callback.accept(reader.readLine());
                  }
              }
            catch (IOException e)
              {
                System.exit(1);
              }
          });
        return result;
      }
    catch (IOException e)
      {
        System.exit(1);
        return null;
      }
  }

  public static void Init(Consumer<String> out, Consumer<String> err)
  {
    CLIENT_OUT = IO.createCapturedStream(out);
    CLIENT_ERR = IO.createCapturedStream(err);
    RedirectErrOutToClientLog();
  }

}
