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
 * Source of class Intrinsics
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm.runtime;

import dev.flang.be.interpreter.OpenResources; // NYI: remove dependency!

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.io.PrintStream;
import java.io.InputStream;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;


/**
 * Runtime provides the runtime system for the JVM backend.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Runtime extends ANY
{

  /*-----------------------------  classes  -----------------------------*/


  /**
   * Exception that is thrown by effect.abort
   */
  static class Abort extends Error
  {

    int _effect;

    /**
     * @param effect the id of the effect that is aborted.
     */
    Abort(int effect)
    {
      super();
      this._effect = effect;
    }

  }

  /**
   * Contains possible error numbers emitted by intrinsics when an error happens
   * on the system side. This attempts to match C's errno.h names and numbers.
   */
  enum SystemErrNo
  {
    UNSPECIFIED(0), EIO(5), EACCES(13), ENOTSUP(95), EADDRINUSE(98), ECONNREFUSED(111);

    final int errno;

    private SystemErrNo(final int errno)
    {
      this.errno = errno;
    }
  }


  /*----------------------------  constants  ----------------------------*/


  /**
   * Copy of dev.flang.be.jvm.Names.ROUTINE_NAME without adding a dependency on
   * that package.  We do not want to bundle the backend classes with a
   * stand-alone application that needs the runtime classes, so this is copied.
   */
  public static final String ROUTINE_NAME = "fzRoutine";


  /*--------------------------  static fields  --------------------------*/


  /**
   * Create a Java string from 0-terminated given byte array.
   */
  private static String utf8ByteArrayDataToString(byte[] ba)
  {
    var l = 0;
    while (l < ba.length && ba[l] != 0)
      {
        l++;
      }
    return new String(ba, 0, l, StandardCharsets.UTF_8);
  }


  /**
   * Currently installed effects.
   *
   * NYI: this should be thread local
   */
  static List<Any> _installedEffects_ = new List<>();


  /**
   * This contains all open files/streams.
   */
  static OpenResources<AutoCloseable> _openStreams_ = new OpenResources<AutoCloseable>()
  {
    @Override
    protected boolean close(AutoCloseable f) {
      try
      {
        f.close();
        return true;
      }
      catch(Exception e)
      {
        return false;
      }
    }
  };

  static long _stdin  = _openStreams_.add(System.in );
  static long _stdout = _openStreams_.add(System.out);
  static long _stderr = _openStreams_.add(System.err);


  /**
   * This contains all started threads.
   */
  private static OpenResources<Thread> _startedThreads_ = new OpenResources<Thread>() {
    @Override
    protected boolean close(Thread f)
    {
      return true;
    };
  };


  static long _next_unique_id = 0xf0015feedbadf00dL;

  static final long UNIQUE_ID_INCREMENT = 1000000000000223L; // large prime generated using https://www.browserling.com/tools/prime-numbers


  static final Object UNIQUE_ID_LOCK = new Object() {};


  public static final Object LOCK_FOR_ATOMIC = new Object();


  public static String[] args = new String[] { "argument list not initialized", "this may indicate a severe bug" };


  /*-------------------------  static methods  --------------------------*/


  /**
   * Report a fatal error and exit.
   *
   * @param msg the error message
   *
   * @return does not
   */
  public static void fatal(String msg)
  {
    Errors.fatal(msg);
  }


  /**
   * Create the internal (Java) array for a `Const_String` from data in the
   * chars of a Java String.
   *
   * @param str the Java string as unicodes.
   *
   * @return the resulting array using utf_8 encoded bytes
   */
  public static byte[] internalArrayForConstString(String str)
  {
    return str.getBytes(StandardCharsets.UTF_8);
  }


  /**
   * Create the internal (Java) array for an `array i8` or `array u8` from data
   * in the chars of a Java String.
   *
   * @param str the Java string, lower byte is the first, upper the second byte.
   *
   * @param len the length of the resulting byte[]
   *
   * @return the resulting array.
   */
  public static byte[] constArray8FromString(String str, int len)
  {
    var result = new byte[len];
    for (var i = 0; i < result.length; i++)
      {
        var c = str.charAt(i/2);
        result[i] = (byte) (i % 2 == 0 ? c : c >> 8);
      }
    return result;
  }


  /**
   * Create the internal (Java) array for an `array i16` from data in the chars
   * of a Java String.
   *
   * @param str the Java string, each char is one i16.
   *
   * @return the resulting array.
   */
  public static short[] constArrayI16FromString(String str)
  {
    var result = new short[str.length()];
    for (var i = 0; i < result.length; i++)
      {
        result[i] = (short) str.charAt(i);
      }
    return result;
  }


  /**
   * Create the internal (Java) array for an `array u16` from data in the chars
   * of a Java String.
   *
   * @param str the Java string, each char is one u16.
   *
   * @return the resulting array.
   */
  public static char[] constArrayU16FromString(String str)
  {
    var result = new char[str.length()];
    for (var i = 0; i < result.length; i++)
      {
        result[i] = str.charAt(i);
      }
    return result;
  }


  /**
   * Create the internal (Java) array for an `array i32` or `array u32` from
   * data in the chars of a Java String.
   *
   * @param str the Java string, two char form one i32 or u32 in little endian order.
   *
   * @return the resulting array.
   */
  public static int[] constArray32FromString(String str)
  {
    var result = new int[str.length() / 2];
    for (var i = 0; i < result.length; i++)
      {
        result[i] =
          ((str.charAt(2*i + 0) & 0xffff)      ) |
          ((str.charAt(2*i + 1) & 0xffff) << 16) ;
      }
    return result;
  }


  /**
   * Create the internal (Java) array for an `array i64` or `array u64` from
   * data in the chars of a Java String.
   *
   * @param str the Java string, four char form one i64 or u64 in little endian
   * order.
   *
   * @return the resulting array.
   */
  public static long[] constArray64FromString(String str)
  {
    var result = new long[str.length() / 4];
    for (var i = 0; i < result.length; i++)
      {
        result[i] =
          ((str.charAt(4*i + 0) & 0xffffL)      ) |
          ((str.charAt(4*i + 1) & 0xffffL) << 16) |
          ((str.charAt(4*i + 2) & 0xffffL) << 32) |
          ((str.charAt(4*i + 3) & 0xffffL) << 48) ;
      }
    return result;
  }


  /**
   * Create the internal (Java) array for an `array f32` from data in the chars
   * of a Java String.
   *
   * @param str the Java string, two chars form the bits of one f32 in little
   * endian order.
   *
   * @return the resulting array.
   */
  public static float[] constArrayF32FromString(String str)
  {
    var result = new float[str.length() / 2];
    for (var i = 0; i < result.length; i++)
      {
        result[i] = Float.intBitsToFloat(((str.charAt(2*i + 0) & 0xffff)      ) |
                                         ((str.charAt(2*i + 1) & 0xffff) << 16) );
      }
    return result;
  }


  /**
   * Create the internal (Java) array for an `array f64` from data in the chars
   * of a Java String.
   *
   * @param str the Java string, four chars form the bits of one f64 in little
   * endian order.
   *
   * @return the resulting array.
   */
  public static double[] constArrayF64FromString(String str)
  {
    var result = new double[str.length() / 4];
    for (var i = 0; i < result.length; i++)
      {
        result[i] = Double.longBitsToDouble(((str.charAt(4*i + 0) & 0xffffL)      ) |
                                            ((str.charAt(4*i + 1) & 0xffffL) << 16) |
                                            ((str.charAt(4*i + 2) & 0xffffL) << 32) |
                                            ((str.charAt(4*i + 3) & 0xffffL) << 48) );
      }
    return result;
  }


  /**
   * Create trace output by printing given msg.  This is called by the generated
   * code when JVM.TRACE is true to output the tracing information.
   *
   * @param msg the trace message.
   */
  public static void trace(String msg)
  {
    System.out.println(msg);
  }

  static Thread _nyi_remove_single_thread = Thread.currentThread();


  /**
   * Make sure _installedEffects_ is large enough to hold effect with given id.
   *
   * @param id an effect id.
   */
  private static void ensure_effect_capacity(int id)
  {
    while (_installedEffects_.size() < id+1)
      {
        _installedEffects_.add(null);
      }
  }


  /**
   * Internal helper to load an effect instance from the given id.
   *
   * @param id an effect id.
   */
  private static Any effect_load(int id)
  {
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    ensure_effect_capacity(id);
    return _installedEffects_.get(id);
  }

  /**
   * Internal helper to store an effect instance for the given id.
   *
   * @param id an effect id.
   */
  private static void effect_store(int id, Any instance)
  {
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    ensure_effect_capacity(id);
    _installedEffects_.set(id, instance);
  }


  public static void effect_default(int id, Any instance)
  {
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    _installedEffects_.ensureCapacity(id + 1);
    if (effect_load(id) == null)
      {
        effect_store(id, instance);
      }
  }


  /**
   * Helper method to implement intrinsic effect.type.is_installed.
   *
   * @param id an effect id.
   *
   * @return true iff an effect with that id was installed.
   */
  public static boolean effect_is_installed(int id)
  {
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    return effect_load(id) != null;
  }


  /**
   * Helper method to implement intrinsic effect.replace.
   *
   * @param id an effect id.
   *
   * @instance a new instance to replace the old one
   */
  public static void effect_replace(int id, Any instance)
  {
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    effect_store(id, instance);
  }


  /**
   * Helper method to handle an InvocationTargetException caused by a call to
   * java.lang.reflect.Method.invoke.  This checks the causing exception, if
   * that is an unchecked RuntimeException or Error, it just re-throws it to be
   * handled by the caller.
   *
   * Otherwise, it causes a fatal error immediately.
   *
   * @param e the caught exception
   *
   * @return does not.
   */
  public static void handleInvocationTargetException(InvocationTargetException e)
  {
    var o = e.getCause();
    if (o instanceof RuntimeException r) { throw r; }
    if (o instanceof Error            r) { throw r; }
    if (o != null)
      {
        Errors.fatal("Error while running JVM compiled code: " + o);
      }
    else
      {
        Errors.fatal("Error while running JVM compiled code: " + e);
      }
  }


  /**
   * Helper method to implement effect.abort.  Abort the currently installed
   * effect with given id.  Helper to implement intrinsic effect.abort.
   *
   * @param id the id of the effect type that is aborted.
   */
  public static void effect_abort(int id)
  {
    throw new Abort(id);
  }


  /**
   * Helper method to implement effect.abortable.  Install an instance of effect
   * type specified by id and run f.call while it is installed.  Helper to
   * implement intrinsic effect.abort.
   *
   * @param id the id of the effect that is installed
   *
   * @param instance the effect instance that is installed
   *
   * @param code the Unary instance to be executed
   *
   * @param call the Java clazz of the Unary instance to be executed.
   */
  public static void effect_abortable(int id, Any instance, Any code, Class call)
  {
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    var old = effect_load(id);
    effect_store(id, instance);
    Method r = null;
    for (var m : call.getDeclaredMethods())
      {
        if (m.getName().equals(ROUTINE_NAME))
          {
            r = m;
          }
      }
    if (r == null)
      {
        Errors.fatal("in effect.abortable, missing `" + ROUTINE_NAME + "` in class `" + call + "`");
      }
    else
      {
        try
          {
            r.invoke(null, code);
          }
        catch (IllegalAccessException e)
          {
            Errors.fatal("effect.abortable call caused `" + e + "` when calling `" + call + "`");
          }
        catch (InvocationTargetException e)
          {
            if (e.getCause() instanceof Abort a)
              {
                if (a._effect != id)
                  {
                    throw a;
                  }
              }
            else
              {
                handleInvocationTargetException(e);
              }
          }
        finally
          {
            effect_store(id, old);
          }
      }
  }

  /**
   * Helper method to implement `effect.env` expressions.  Returns the installed
   * effect with the given id.  Causes an error in case no such effect exists.
   *
   * @param id the id of the effect that should be loaded.
   *
   * @return the instance that was installed for this id
   *
   * @throws Error in case no instance was installed.
   */
  public static Any effect_get(int id)
  {
    if (CHECKS) check
      (_nyi_remove_single_thread == Thread.currentThread());

    var result = effect_load(id);
    if (result == null)
      {
        throw new Error("No effect of "+id+" installed");
      }
    return result;
  }


  /**
   * Called after a precondition/postcondition check failed
   *
   * @param msg a detail message explaining what failed
   *
   * @return does not.
   */
  public static void contract_fail(String msg)
  {
    var stacktrace = new StringWriter();
    new Throwable().printStackTrace(new PrintWriter(stacktrace));
    Errors.fatal("CONTRACT FAILED: " + msg, stacktrace.toString());
  }


  public static void fuzion_std_date_time(int[] arg0)
  {
    var date = new Date();
    var calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    calendar.setTime(date);
    arg0[0] = calendar.get(Calendar.YEAR);
    arg0[1] = calendar.get(Calendar.DAY_OF_YEAR);
    arg0[2] = calendar.get(Calendar.HOUR_OF_DAY);
    arg0[3] = calendar.get(Calendar.MINUTE);
    arg0[4] = calendar.get(Calendar.SECOND);
    arg0[5] = calendar.get(Calendar.MILLISECOND) * 1000;
  }


  public static int fuzion_sys_fileio_write(long f, byte[] fileContent, int l)
  {
    try
      {
        var s = Runtime._openStreams_.get(f);
        if (s instanceof RandomAccessFile raf)
          {
            /* NYI:
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
              }
            */
            raf.write(fileContent);
          }
        else if (s instanceof OutputStream os)
          {
            os.write(fileContent);
          }
        return 0;
      }
    catch (IOException e)
      {
        return -1;
      }
  }


  public static int fuzion_sys_net_bind0(int family, int socketType, int protocol, byte[] host0, byte[] port0, long[] result)
  {
    var host = utf8ByteArrayDataToString(host0);
    var port = utf8ByteArrayDataToString(port0);

    if (family != 2 && family != 10)
      {
        throw new RuntimeException("NYI");
      }
    try
      {
        return switch (protocol)
          {
            case 6 ->
              {
                var ss = ServerSocketChannel.open();
                ss.bind(new InetSocketAddress(host, Integer.parseInt(port)));
                result[0] = Runtime._openStreams_.add(ss);
                yield 0;
              }
            case 17 ->
              {
                var ss = DatagramChannel.open();
                ss.bind(new InetSocketAddress(host, Integer.parseInt(port)));
                result[0] = Runtime._openStreams_.add(ss);
                yield 0;
              }
            default -> throw new RuntimeException("NYI");
          };
      }
    catch(BindException e)
      {
        result[0] = SystemErrNo.EADDRINUSE.errno;
        return -1;
      }
    catch(IOException e)
      {
        result[0] = -1;
        return -1;
      }
  }

  public static int fuzion_sys_net_listen()
  {
    return 0;
  }

  public static boolean fuzion_sys_net_accept(long sockfd, long[] result)
  {
    try
      {
        var asc = Runtime._openStreams_.get(sockfd);
        if(asc instanceof ServerSocketChannel ssc)
          {
            var socket = ssc.accept();
            result[0] = Runtime._openStreams_.add(socket);
            return true;
          }
        else if(asc instanceof DatagramChannel dc)
          {
            result[0] = sockfd;
            return true;
          }
        throw new RuntimeException("NYI");
      }
    catch(IOException e)
      {
        return false;
      }
  }

  public static int fuzion_sys_net_connect0(int family, int socketType, int protocol, byte[] host0, byte[] port0, long[] result)
  {
    var host = utf8ByteArrayDataToString(host0);
    var port = utf8ByteArrayDataToString(port0);
    if (family != 2 && family != 10)
      {
        throw new RuntimeException("NYI");
      }
    try
      {
        return switch (protocol)
          {
            case 6 ->
              {
                var socket = SocketChannel.open();
                socket.connect(new InetSocketAddress(host, Integer.parseInt(port)));
                result[0] = Runtime._openStreams_.add(socket);
                yield 0;
              }
            case 17 ->
              {
                var ss = DatagramChannel.open();
                ss.connect(new InetSocketAddress(host, Integer.parseInt(port)));
                result[0] = Runtime._openStreams_.add(ss);
                yield 0;
              }
            default -> throw new RuntimeException("NYI");
          };
      }
    catch(IOException e)
      {
        result[0] = SystemErrNo.ECONNREFUSED.errno;
        return -1;
      }
  }

  public static int fuzion_sys_net_get_peer_address(long sockfd, byte[] data)
  {
    try
      {
        if (Runtime._openStreams_.get(sockfd) instanceof SocketChannel sc)
          {
            byte[] address = ((InetSocketAddress)sc.getRemoteAddress()).getAddress().getAddress();
            System.arraycopy(address, 0, data, 0, address.length);
            return address.length;
          }
        return -1;
      }
    catch (IOException e)
      {
        return -1;
      }
  }

  public static short fuzion_sys_net_get_peer_port(long sockfd)
  {
    try
      {
        if (Runtime._openStreams_.get(sockfd) instanceof SocketChannel sc)
          {
            return (short) ((InetSocketAddress)sc.getRemoteAddress()).getPort();
          }
        return 0;
      }
    catch (IOException e)
      {
        return 0;
      }
  }

  public static boolean fuzion_sys_net_read(long sockfd, byte[] buff, int length, long[] result)
  {
    try
      {
        var desc = Runtime._openStreams_.get(sockfd);
        // NYI blocking / none blocking read
        long bytesRead;
        if (desc instanceof DatagramChannel dc)
          {
            bytesRead = dc.receive(ByteBuffer.wrap(buff)) == null
              ? 0
              // NYI how to determine datagram length?
              : buff.length;
          }
        else if (desc instanceof ByteChannel sc)
          {
            bytesRead = sc.read(ByteBuffer.wrap(buff));
          }
        else
          {
            throw new RuntimeException("NYI");
          }
        result[0] = bytesRead;
        return bytesRead != -1;
      }
    catch(IOException e) //SocketTimeoutException and others
      {
        // unspecified error
        result[0] = -1;
        return false;
      }
  }

  public static int fuzion_sys_net_write(long sockfd, byte[] fileContent)
  {
    try
      {
        var sc = (ByteChannel)Runtime._openStreams_.get(sockfd);
        sc.write(ByteBuffer.wrap(fileContent));
        return 0;
      }
    catch(IOException e)
      {
        return -1;
      }
  }

  public static int fuzion_sys_net_close0(long sockfd)
  {
    return Runtime._openStreams_.remove(sockfd)
      ? 0
      : -1;
  }

  public static int fuzion_sys_net_set_blocking0(long sockfd, int blocking)
  {
    var asc = (AbstractSelectableChannel)Runtime._openStreams_.get(sockfd);
    try
      {
        asc.configureBlocking(blocking == 1);
        return 0;
      }
    catch(IOException e)
      {
        return -1;
      }
  }

  public static int fuzion_sys_fileio_flush(long fd)
  {
    var s = _openStreams_.get(fd);
    if (s instanceof PrintStream ps)
      {
        ps.flush();
      }
    return 0;
  }

  public static int fuzion_sys_fileio_read(long fd, byte[] byteArr)
  {
    try
      {
        var s = _openStreams_.get(fd);
        int bytesRead = 0;
        if (s instanceof RandomAccessFile raf)
          {
            bytesRead = raf.read(byteArr);
          }
        else
          {
            bytesRead = ((InputStream) s).read(byteArr);
          }

        return bytesRead;
      }
    catch (Exception e)
      {
        return -2;
      }
  }

  public static int fuzion_sys_fileio_write(long fd, byte[] fileContent)
  {
    try
      {
        var s = _openStreams_.get(fd);
        if (s instanceof RandomAccessFile raf)
          {
            raf.write(fileContent);
          }
        else
          {
            ((OutputStream) s).write(fileContent);
          }
        return 0;
      }
    catch (Exception e)
      {
        return -1;
      }
  }

  public static boolean fuzion_sys_fileio_delete(byte[] file)
  {
    Path path = Path.of(utf8ByteArrayDataToString(file));
    try
      {
        return Files.deleteIfExists(path);
      }
    catch (Exception e)
      {
        return false;
      }
  }

  public static boolean fuzion_sys_fileio_move(byte[] o, byte[] n)
  {
    Path oldPath = Path.of(utf8ByteArrayDataToString(o));
    Path newPath = Path.of(utf8ByteArrayDataToString(n));
    try
      {
        Files.move(oldPath, newPath);
        return true;
      }
    catch (Exception e)
      {
        return false;
      }
  }

  public static boolean fuzion_sys_fileio_create_dir(byte[] d)
  {
    Path path = Path.of(utf8ByteArrayDataToString(d));
    try
      {
        Files.createDirectory(path);
        return true;
      }
    catch (Exception e)
      {
        return false;
      }
  }

  public static void fuzion_sys_fileio_open(byte[] path, long[] open_results, short mode)
  {
    try
      {
        switch (mode)
          {
          case 0 :
            RandomAccessFile fis = new RandomAccessFile(utf8ByteArrayDataToString(path), "r");
            open_results[0] = _openStreams_.add(fis);
            break;
          case 1 :
            RandomAccessFile fos = new RandomAccessFile(utf8ByteArrayDataToString(path), "rw");
            open_results[0] = _openStreams_.add(fos);
            break;
          case 2 :
            RandomAccessFile fas = new RandomAccessFile(utf8ByteArrayDataToString(path), "rw");
            fas.seek(fas.length());
            open_results[0] = _openStreams_.add(fas);
            break;
          default:
            open_results[1] = -1;
            System.err.println("*** Unsupported open flag. Please use: 0 for READ, 1 for WRITE, 2 for APPEND. ***");
            System.exit(1);
          }
      }
    catch (Exception e)
      {
        open_results[1] = -1;
      }
    return;
  }

  public static int fuzion_sys_fileio_close(long fd)
  {
    return _openStreams_.remove(fd)
                                    ? 0
                                    : -1;
  }

  public static boolean fuzion_sys_fileio_lstats(byte[] d, long[] stats) // NYI : should be altered in the future to not resolve symbolic links
  {
    return fuzion_sys_fileio_stats(d, stats);
  }
  public static boolean fuzion_sys_fileio_stats(byte[] d, long[] stats)
  {
    Path path = Path.of(utf8ByteArrayDataToString(d));
    var err = SystemErrNo.UNSPECIFIED;
    try
      {
        BasicFileAttributes metadata = Files.readAttributes(path, BasicFileAttributes.class);
        stats[0] = metadata.size();
        stats[1] = metadata.lastModifiedTime().to(TimeUnit.SECONDS);
        stats[2] = metadata.isRegularFile() ? 1: 0;
        stats[3] = metadata.isDirectory() ? 1: 0;
        return true;
      }
    catch (UnsupportedOperationException e)
      {
        err = SystemErrNo.ENOTSUP;
      }
    catch (IOException e)
      {
        err = SystemErrNo.EIO;
      }
    catch (SecurityException e)
      {
        err = SystemErrNo.EACCES;
      }

    stats[0] = err.errno;
    stats[1] = 0;
    stats[2] = 0;
    stats[3] = 0;
    return false;
  }

  public static void fuzion_sys_fileio_seek(long fd, short s, long[] seekResults)
  {
    try
      {
        var raf = (RandomAccessFile) _openStreams_.get(fd);
        raf.seek(s);
        seekResults[0] = raf.getFilePointer();
        return;
      }
    catch (Exception e)
      {
        seekResults[1] = -1;
        return;
      }
  }

  public static void fuzion_sys_fileio_file_position(long fd, long[] arr)
  {
    try
      {
        arr[0] = ((RandomAccessFile) _openStreams_.get(fd)).getFilePointer();
        return;
      }
    catch (Exception e)
      {
        arr[1] = -1;
        return;
      }
  }

  public static byte[] fuzion_sys_fileio_mmap(long fd, long offset, long size, int[] result)
  {
    try
      {
        var raf = (RandomAccessFile) _openStreams_.get(fd);

        // offset+size must not exceed file size, to match semantics of
        // c-backend.
        if (raf.length() < (offset + size))
          {
            result[0] = -1;
            return new byte[0];
          }

        var mmap = raf.getChannel().map(MapMode.READ_WRITE, offset, size);

        // success
        result[0] = 0;
        return new byte[0];
        /* NYI
                  @Override
                  void set(
                    int x,
                    Value v,
                    AbstractType elementType)
                  {
                    checkIndex(x);
                    mmap.put(x, (byte)v.u8Value());
                  }

                  @Override
                  Value get(
                    int x,
                    AbstractType elementType)
                  {
                    checkIndex(x);
                    return new u8Value(mmap.get(x));
                  }

                  @Override
                  int length(){
                    return (int)size;
                  }
        */
      }
    catch (IOException e)
      {
        result[0] = -1;
        return new byte[0];
      }
  }

  public static int fuzion_sys_fileio_munmap()
  {
    return 0;
  }

  public static void fuzion_std_nano_sleep(long d)
  {
    try
      {
        TimeUnit.NANOSECONDS.sleep(d < 0 ? Long.MAX_VALUE: d);
      }
    catch (InterruptedException ie)
      {
        throw new Error("unexpected interrupt", ie);
      }
  };

  public static boolean fuzion_sys_env_vars_has0(byte[] b)
  {
    return System.getenv(utf8ByteArrayDataToString(b)) != null;
  }

  public static String fuzion_sys_env_vars_get0(byte[] d)
  {
    return System.getenv(utf8ByteArrayDataToString(d));
  }

  public static long fuzion_sys_thread_spawn0(/*args missing*/)
  {
    throw new RuntimeException("NYI");
    /* NYI
    var call = Types.resolved.f_function_call;
    var oc = innerClazz.argumentFields()[0].resultClazz();
    var ic = oc.lookup(call);
    var al = new ArrayList<Value>();
    al.add(args.get(1));
    var t = new Thread(() -> interpreter.callOnInstance(ic.feature(), ic, new Instance(ic), al));
    t.setDaemon(true);
    t.start();
    return _startedThreads_.add(t);
     */
  };

  public static void fuzion_sys_thread_join0(long threadId)
  {
    try
      {
        _startedThreads_.get(threadId).join();
        _startedThreads_.remove(threadId);
      }
    catch (InterruptedException e)
      {
        // NYI handle this exception
        System.err.println("Joining of threads was interrupted: " + e);
        System.exit(1);
      }
  };


  static long unique_id()
  {
    long result;
    synchronized (UNIQUE_ID_LOCK)
      {
        result = _next_unique_id;
        _next_unique_id = result + UNIQUE_ID_INCREMENT;
      }
    return result;
  }


  public static byte[] args_get(int i)
  {
    return args[i].getBytes(StandardCharsets.UTF_8);
  }


}

/* end of file */
