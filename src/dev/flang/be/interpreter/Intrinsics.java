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

package dev.flang.be.interpreter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import dev.flang.fuir.FUIR;

import static dev.flang.ir.IR.NO_SITE;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;


/**
 * Intrinsics provides the implementation of Fuzion's intrinsic features.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Intrinsics extends ANY
{

  /*----------------------------  interfaces  ---------------------------*/


  @FunctionalInterface
  interface IntrinsicCode
  {
    Callable get(Executor executor, int innerClazz);
  }


  /*------------------------------  enums  ------------------------------*/


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
   * NYI: This will eventually be part of a Fuzion IR / BE Config class.
   */
  public static Boolean ENABLE_UNSAFE_INTRINSICS = null;


  static TreeMap<String, IntrinsicCode> _intrinsics_ = new TreeMap<>();

  /**
   * This contains all open files/streams.
   */
  private static OpenResources<AutoCloseable> _openStreams_ = new OpenResources<AutoCloseable>()
  {
    @Override
    protected boolean close(AutoCloseable f) {
      try
      {
        f.close();
        return true;
      }
      catch(Throwable e)
      {
        return false;
      }
    }
  };
  private static Value _stdin  = new i64Value(_openStreams_.add(System.in ));
  private static Value _stdout = new i64Value(_openStreams_.add(System.out));
  private static Value _stderr = new i64Value(_openStreams_.add(System.err));


  /**
   * This contains all open processes.
   */
  private static OpenResources<Process> _openProcesses_ = new OpenResources<Process>()
  {
    @Override
    protected boolean close(Process p) {
      if(PRECONDITIONS) require
        (p != null);
      return true;
    }
  };

  /**
   * This contains all started threads.
   */
  private static OpenResources<Thread> _startedThreads_ = new OpenResources<Thread>() {
     @Override
     protected boolean close(Thread f) {return true;};
   };


  /*----------------------------  variables  ----------------------------*/


  /*------------------------  static variables  -------------------------*/


  /**
   * the last unique identifier returned by `fuzion.sys.misc.unique_id`.
   */
  private static AtomicLong _last_unique_id_ = new AtomicLong();


  /*-------------------------  static methods  --------------------------*/


  private static void put(String n, IntrinsicCode c) { _intrinsics_.put(n, c); }
  private static void putUnsafe(String n, IntrinsicCode c) { _intrinsics_.put(n, (executor, innerClazz) -> args -> {
    if (!ENABLE_UNSAFE_INTRINSICS)
      {
        Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
      }
    return c.get(executor, innerClazz).call(args);
  }); }
  private static void put(String n1, String n2, IntrinsicCode c) { put(n1, c); put(n2, c); }
  private static void putUnsafe(String n1, String n2, IntrinsicCode c) { putUnsafe(n1, c); putUnsafe(n2, c); }
  private static void put(String n1, String n2, String n3, IntrinsicCode c) { put(n1, c); put(n2, c); put(n3, c); }
  private static void putUnsafe(String n1, String n2, String n3, IntrinsicCode c) { putUnsafe(n1, c); putUnsafe(n2, c); putUnsafe(n3, c); }
  private static void put(String n1, String n2, String n3, String n4, IntrinsicCode c) { put(n1, c); put(n2, c); put(n3, c); put(n4, c); }
  private static void putUnsafe(String n1, String n2, String n3, String n4, IntrinsicCode c) { putUnsafe(n1, c); putUnsafe(n2, c); putUnsafe(n3, c); putUnsafe(n4, c); }
  private static void put(String n1, String n2, String n3, String n4, String n5, IntrinsicCode c) { put(n1, c); put(n2, c); put(n3, c); put(n4, c); put(n5, c); }


  /**
   * Get the names of all intrinsics supported by this backend.
   */
  public static Set<String> supportedIntrinsics()
  {
    return _intrinsics_.keySet();
  }


  /**
   * Create a Java string from 0-terminated given byte array.
   */
  private static String utf8ByteArrayDataToString(Value internalArray)
  {
    var strA = internalArray.arrayData();
    var ba = (byte[]) strA._array;
    var l = 0;
    while (l < ba.length && ba[l] != 0)
      {
        l++;
      }
    return new String(ba, 0, l, StandardCharsets.UTF_8);
  }

  /**
   * Create a Callable to call an intrinsic feature.
   *
   * @param innerClazz the frame clazz of the called feature
   *
   * @return a Callable instance to execute the intrinsic call.
   */
  public static Callable call(Executor executor, int innerClazz)
  {
    Callable result;
    String in = executor.fuir().clazzOriginalName(innerClazz);
    // NYI: We must check the argument count in addition to the name!
    var ca = _intrinsics_.get(in);
    if (ca != null)
      {
        result = ca.get(executor, innerClazz);
      }
    else
      {
        Errors.fatal(executor.fuir().declarationPos(innerClazz),
                     "Intrinsic feature not supported",
                     "Missing intrinsic feature: " + in);
        result = (args) -> Value.NO_VALUE;
      }
    return result;
  }


  /**
   * Atomic intrinsics are made atomic using this lock.
   *
   * NYI: OPTIMIZATION: For atomic instances of types ref, i32, etc., we might
   * implement this using jdk.internal.misc.Unsafe or
   * java.util.concurrent.atomic.* to make these operations lock-free.
   */
  static final Object LOCK_FOR_ATOMIC = new Object();


  static
  {
    put("Type.name"            , (executor, innerClazz) -> args ->
      Interpreter.value(executor.fuir().clazzTypeName(executor.fuir().clazzOuterClazz(innerClazz))));

    put("concur.atomic.compare_and_swap0",  (executor, innerClazz) -> args ->
        {
          var a = executor.fuir().clazzOuterClazz(innerClazz);
          var f = executor.fuir().lookupAtomicValue(a);
          var thiz      = args.get(0);
          var expected  = args.get(1);
          var new_value = args.get(2);
          synchronized (LOCK_FOR_ATOMIC)
            {
              var res = Interpreter.getField(f, a, thiz, false); // NYI: HACK: We must clone this!
              if (Interpreter.compareField(f, a, thiz, expected))
                {
                  res = expected;   // NYI: HACK: workaround since res was not cloned
                  Interpreter.setField(f, a, thiz, new_value);
                }
              return res;
            }
        });
    put("concur.atomic.compare_and_set0",  (executor, innerClazz) -> args ->
        {
          var a = executor.fuir().clazzOuterClazz(innerClazz);
          var f = executor.fuir().lookupAtomicValue(a);
          var thiz      = args.get(0);
          var expected  = args.get(1);
          var new_value = args.get(2);
          synchronized (LOCK_FOR_ATOMIC)
            {
              if (Interpreter.compareField(f, a, thiz, expected))
                {
                  Interpreter.setField(f, a, thiz, new_value);
                  return new boolValue(true);
                }
              return new boolValue(false);
            }
        });
    put("concur.atomic.racy_accesses_supported",  (executor, innerClazz) -> args ->
        {
          var t = executor.fuir().clazzActualGeneric(executor.fuir().clazzOuterClazz(innerClazz), 0);
          return new boolValue
            (executor.fuir().clazzIsRef(t)                            ||
             (t == executor.fuir().clazz(FUIR.SpecialClazzes.c_i8  )) ||
             (t == executor.fuir().clazz(FUIR.SpecialClazzes.c_i16 )) ||
             (t == executor.fuir().clazz(FUIR.SpecialClazzes.c_i32 )) ||
             (t == executor.fuir().clazz(FUIR.SpecialClazzes.c_u8  )) ||
             (t == executor.fuir().clazz(FUIR.SpecialClazzes.c_u16 )) ||
             (t == executor.fuir().clazz(FUIR.SpecialClazzes.c_u32 )) ||
             (t == executor.fuir().clazz(FUIR.SpecialClazzes.c_f32 )) ||
             (t == executor.fuir().clazz(FUIR.SpecialClazzes.c_bool)));
        });
    put("concur.atomic.read0",  (executor, innerClazz) -> args ->
        {
          var a = executor.fuir().clazzOuterClazz(innerClazz);
          var f = executor.fuir().lookupAtomicValue(a);
          var thiz = args.get(0);
          synchronized (LOCK_FOR_ATOMIC)
            {
              return Interpreter.getField(f, a, thiz, false);
            }
        });
    put("concur.atomic.write0", (executor, innerClazz) -> args ->
        {
          var a = executor.fuir().clazzOuterClazz(innerClazz);
          var f = executor.fuir().lookupAtomicValue(a);
          var thiz = args.get(0);
          synchronized (LOCK_FOR_ATOMIC)
            {
              Interpreter.setField(f, a, thiz, args.get(1));
            }
          return new Instance(executor.fuir().clazz(FUIR.SpecialClazzes.c_unit));
        });

    put("concur.util.loadFence",   (executor, innerClazz) -> args ->
        {
          synchronized (LOCK_FOR_ATOMIC) { };
          return new Instance(executor.fuir().clazz(FUIR.SpecialClazzes.c_unit));
        });

    put("concur.util.storeFence",  (executor, innerClazz) -> args ->
        {
          synchronized (LOCK_FOR_ATOMIC) { };
          return new Instance(executor.fuir().clazz(FUIR.SpecialClazzes.c_unit));
        });

    put("fuzion.sys.args.count", (executor, innerClazz) -> args -> new i32Value(executor.options().getBackendArgs().size() + 1));
    put("fuzion.sys.args.get"  , (executor, innerClazz) -> args ->
        {
          var i = args.get(1).i32Value();
          var fuir = executor.fuir();
          if (i == 0)
            {
              return  Interpreter.value(fuir.clazzAsString(fuir.mainClazzId()));
            }
          else
            {
              return  Interpreter.value(executor.options().getBackendArgs().get(i - 1));
            }
        });
    put("fuzion.sys.fileio.flush"  , (executor, innerClazz) -> args ->
        {
          var s = _openStreams_.get(args.get(1).i64Value());
          if (s instanceof PrintStream ps)
            {
              ps.flush();
            }
          return new i32Value(0);
        });

    put("fuzion.sys.fatal_fault0", (executor, innerClazz) -> args ->
        {
          Errors.runTime(utf8ByteArrayDataToString(args.get(1)),
                         utf8ByteArrayDataToString(args.get(2)),
                         executor.callStack(executor.fuir()));
          return Value.EMPTY_VALUE;
        });
    put("fuzion.sys.stdin.stdin0"  , (executor, innerClazz) -> args ->
        {
          return _stdin;
        });
    put("fuzion.sys.out.stdout"    , (executor, innerClazz) -> args ->
        {
          return _stdout;
        });
    put("fuzion.sys.err.stderr"    , (executor, innerClazz) -> args ->
        {
          return _stderr;
        });
    put("fuzion.sys.fileio.read", (executor, innerClazz)-> args ->
        {
          var byteArr = (byte[])args.get(2).arrayData()._array;
          try
            {
              var s = _openStreams_.get(args.get(1).i64Value());
              int bytesRead = 0;
              if (s instanceof RandomAccessFile raf)
                {
                  if (!ENABLE_UNSAFE_INTRINSICS)
                    {
                      Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
                    }
                  bytesRead = raf.read(byteArr);
                }
              else
                {
                  bytesRead = ((InputStream) s).read(byteArr);
                }

              return new i32Value(bytesRead);
            }
          catch (Throwable e)
            {
              return new i32Value(-2);
            }
        });
    put("fuzion.sys.fileio.write", (executor, innerClazz) -> args ->
        {
          byte[] fileContent = (byte[])args.get(2).arrayData()._array;
          try
            {
              var s = _openStreams_.get(args.get(1).i64Value());
              if (s instanceof RandomAccessFile raf)
                {
                  if (!ENABLE_UNSAFE_INTRINSICS)
                    {
                      Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
                    }
                  raf.write(fileContent);
                }
              else
                {
                  ((OutputStream) s).write(fileContent);
                }
              return new i32Value(0);
            }
          catch (Throwable e)
            {
              return new i32Value(-1);
            }
        });
    putUnsafe("fuzion.sys.fileio.delete", (executor, innerClazz) -> args ->
        {
          Path path = Path.of(utf8ByteArrayDataToString(args.get(1)));
          try
            {
              boolean b = Files.deleteIfExists(path);
              return new boolValue(b);
            }
          catch (Throwable e)
            {
              return new boolValue(false);
            }
        });
    putUnsafe("fuzion.sys.fileio.move", (executor, innerClazz) -> args ->
        {
          Path oldPath = Path.of(utf8ByteArrayDataToString(args.get(1)));
          Path newPath = Path.of(utf8ByteArrayDataToString(args.get(2)));
          try
            {
              Files.move(oldPath, newPath);
              return new boolValue(true);
            }
          catch (Throwable e)
            {
              return new boolValue(false);
            }
        });
    putUnsafe("fuzion.sys.fileio.create_dir", (executor, innerClazz) -> args ->
        {
          Path path = Path.of(utf8ByteArrayDataToString(args.get(1)));
          try
            {
              Files.createDirectory(path);
              return new boolValue(true);
            }
          catch (Throwable e)
            {
              return new boolValue(false);
            }
        });
    putUnsafe("fuzion.sys.fileio.open", (executor, innerClazz) -> args ->
        {
          var open_results = (long[])args.get(2).arrayData()._array;
          open_results[1] = 0;
          try
            {
              switch (args.get(3).i8Value()) {
                case 0:
                  RandomAccessFile fis = new RandomAccessFile(utf8ByteArrayDataToString(args.get(1)), "r");
                  open_results[0] = _openStreams_.add(fis);
                  break;
                case 1:
                  RandomAccessFile fos = new RandomAccessFile(utf8ByteArrayDataToString(args.get(1)), "rw");
                  open_results[0] = _openStreams_.add(fos);
                  break;
                case 2:
                  RandomAccessFile fas = new RandomAccessFile(utf8ByteArrayDataToString(args.get(1)), "rw");
                  fas.seek(fas.length());
                  open_results[0] = _openStreams_.add(fas);
                  break;
                default:
                  open_results[1] = -1;
                  say_err("*** Unsupported open flag. Please use: 0 for READ, 1 for WRITE, 2 for APPEND. ***");
                  System.exit(1);
              }
            }
          catch (Throwable e)
            {
              open_results[1] = -1;
            }
          return Value.EMPTY_VALUE;
        });
    putUnsafe("fuzion.sys.fileio.close", (executor, innerClazz) -> args ->
        {
          long fd = args.get(1).i64Value();
          return _openStreams_.remove(fd)
            ? new i8Value(0)
            : new i8Value(-1);
        });
    putUnsafe("fuzion.sys.fileio.stats",
        "fuzion.sys.fileio.lstats", // NYI : should be altered in the future to not resolve symbolic links
        (executor, innerClazz) -> args ->
        {
          Path path = Path.of(utf8ByteArrayDataToString(args.get(1)));
          long[] stats = (long[])args.get(2).arrayData()._array;
          var err = SystemErrNo.UNSPECIFIED;
          try
            {
              BasicFileAttributes metadata = Files.readAttributes(path, BasicFileAttributes.class);
              stats[0] = metadata.size();
              stats[1] = metadata.lastModifiedTime().to(TimeUnit.SECONDS);
              stats[2] = metadata.isRegularFile()? 1:0;
              stats[3] = metadata.isDirectory()? 1:0;
              return new boolValue(true);
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
          catch (Throwable e)
            {
              err = SystemErrNo.UNSPECIFIED;
            }

          stats[0] = err.errno;
          stats[1] = 0;
          stats[2] = 0;
          stats[3] = 0;
          return new boolValue(false);
        });
    putUnsafe("fuzion.sys.fileio.seek", (executor, innerClazz) -> args ->
        {
          long fd = args.get(1).i64Value();
          var seekResults = (long[])args.get(3).arrayData()._array;
          try
            {
              var raf = (RandomAccessFile)_openStreams_.get(fd);
              raf.seek(args.get(2).i16Value());
              seekResults[0] = raf.getFilePointer();
              return Value.EMPTY_VALUE;
            }
          catch (Throwable e)
            {
              seekResults[1] = -1;
              return Value.EMPTY_VALUE;
            }
        });
    putUnsafe("fuzion.sys.fileio.file_position", (executor, innerClazz) -> args ->
        {
          long fd = args.get(1).i64Value();
          long[] arr = (long[])args.get(2).arrayData()._array;
          try
            {
              arr[0] = ((RandomAccessFile)_openStreams_.get(fd)).getFilePointer();
              return Value.EMPTY_VALUE;
            }
          catch (Throwable e)
            {
              arr[1] = -1;
              return Value.EMPTY_VALUE;
            }
        });
    putUnsafe("fuzion.sys.fileio.mmap", (executor, innerClazz) -> args ->
        {
          try
            {
              var raf = (RandomAccessFile)_openStreams_.get(args.get(1).i64Value());
              var offset = args.get(2).i64Value();
              var size = args.get(3).i64Value();

              // offset+size must not exceed file size, to match semantics of c-backend.
              if(raf.length() < (offset + size))
              {
                ((int[])args.get(4).arrayData()._array)[0] = -1;
                return new ArrayData(new byte[0]);
              }

              var mmap = raf.getChannel().map(MapMode.READ_WRITE, offset, size);

              // success, return an special implementation of ArrayData.
              ((int[])args.get(4).arrayData()._array)[0] = 0;
              return new ArrayData(new byte[0]){
                  @Override
                  void set(
                    int x,
                    Value v,
                    FUIR fuir,
                    int elementType)
                  {
                    checkIndex(x);
                    mmap.put(x, (byte)v.u8Value());
                  }

                  @Override
                  Value get(
                    int x,
                    FUIR fuir,
                    int elementType)
                  {
                    checkIndex(x);
                    return new u8Value(mmap.get(x));
                  }

                  @Override
                  int length()
                  {
                    return (int)size;
                  }
                };
            }
          catch (Throwable e)
            {
              ((int[])args.get(4).arrayData()._array)[0] = -1;
              return new ArrayData(new byte[0]);
            }
        });
    putUnsafe("fuzion.sys.fileio.munmap", (executor, innerClazz) -> args ->
        {
          return new i32Value(0);
        });
    putUnsafe("fuzion.sys.fileio.open_dir", (executor, innerClazz) -> args ->
        {
          var open_results = (long[])args.get(2).arrayData()._array;
          try
            {
              var i = Files.walk(Paths.get(utf8ByteArrayDataToString(args.get(1))), 1).iterator();
              // skip path itself
              i.next();
              interface CloseableIterator<T> extends Iterator<T>, AutoCloseable {};
              open_results[0] = _openStreams_.add(new CloseableIterator<Path>() {
                public void close() throws IOException
                {
                  // do nothing :)
                }

                public boolean hasNext() {
                  return i.hasNext();
                }

                public Path next() {
                  return i.next();
                }

                public void remove() {
                  i.remove();
                }
              });
            }
          catch (Throwable e)
            {
              open_results[1] = -1;
            }

          return Value.EMPTY_VALUE;
        });
    putUnsafe("fuzion.sys.fileio.read_dir", (executor, innerClazz) -> args ->
        {
          var i = getIterator(args.get(1).i64Value());
          try
            {
              return Interpreter.value(i.next().getFileName().toString());
            }
          catch (NoSuchElementException e)
            {
              return Interpreter.value("NoSuchElementException encountered!");
            }
        });
    putUnsafe("fuzion.sys.fileio.read_dir_has_next", (executor, innerClazz) -> args ->
        {
          var it = getIterator(args.get(1).i64Value());
          return new boolValue(it.hasNext());
        });
    putUnsafe("fuzion.sys.fileio.close_dir", (executor, innerClazz) -> args ->
        {
          _openStreams_.remove(args.get(1).i64Value());
          return new i64Value(0);
        });
    put("fuzion.sys.fileio.mapped_buffer_get", (executor, innerClazz) -> args ->
        {
          return ((ArrayData)args.get(1)).get(/* index */ (int) args.get(2).i64Value(),
                                              executor.fuir(),
                                              /* type  */ executor.fuir().clazz(FUIR.SpecialClazzes.c_u8));
        });
    put("fuzion.sys.fileio.mapped_buffer_set", (executor, innerClazz) -> args ->
        {
          ((ArrayData)args.get(1)).set(/* index */ (int) args.get(2).i64Value(),
                                       /* value */ args.get(3),
                                       executor.fuir(),
                                       /* type  */ executor.fuir().clazz(FUIR.SpecialClazzes.c_u8));
          return Value.EMPTY_VALUE;
        });

    put("fuzion.std.exit", (executor, innerClazz) -> args ->
        {
          int rc = args.get(1).i32Value();
          System.exit(rc);
          return Value.EMPTY_VALUE;
        });
    put("fuzion.java.Java_Object.is_null0", (executor, innerClazz) -> args ->
        {
          Instance thizI = (Instance) args.get(0);
          Object thiz = JavaInterface.instanceToJavaObject(thizI);
          return new boolValue(thiz == null);
        });
    putUnsafe("fuzion.java.get_static_field0",
        "fuzion.java.get_field0"      , (executor, innerClazz) ->
        {
          String in = executor.fuir().clazzOriginalName(innerClazz);
          var statique = in.equals("fuzion.java.get_static_field0");
          int resultClazz = executor.fuir().clazzActualGeneric(innerClazz, 0);
          return args ->
            {
              Instance clazzOrThizI = (Instance) args.get(1);
              Instance fieldI = (Instance) args.get(2);
              String clazz = !statique ? null : (String) JavaInterface.instanceToJavaObject(clazzOrThizI);
              Object thiz  = statique  ? null :          JavaInterface.instanceToJavaObject(clazzOrThizI);
              String field = (String) JavaInterface.instanceToJavaObject(fieldI);
              return JavaInterface.getField(clazz, thiz, field, resultClazz);
            };
        });
    putUnsafe("fuzion.java.set_static_field0",
        "fuzion.java.set_field0"      , (executor, innerClazz) ->
        {
          String in = executor.fuir().clazzOriginalName(innerClazz);
          var statique = in.equals("fuzion.java.set_static_field0");
          int resultClazz = executor.fuir().clazzActualGeneric(innerClazz, 0);
          return args ->
            {
              Instance clazzOrThizI = (Instance) args.get(1);
              Instance fieldI = (Instance) args.get(2);
              Instance value = (Instance) args.get(3);
              String clazz = !statique ? null : (String) JavaInterface.instanceToJavaObject(clazzOrThizI);
              Object thiz  = statique  ? null :          JavaInterface.instanceToJavaObject(clazzOrThizI);
              String field = (String) JavaInterface.instanceToJavaObject(fieldI);
              Object val  = JavaInterface.instanceToJavaObject(value);
              JavaInterface.setField(clazz, thiz, field, val);
              return Value.EMPTY_VALUE;
            };
        });
    putUnsafe("fuzion.java.call_v0",
        "fuzion.java.call_s0",
        "fuzion.java.call_c0", (executor, innerClazz) ->
        {
          String in = executor.fuir().clazzOriginalName(innerClazz);
          var virtual     = in.equals("fuzion.java.call_v0");
          var constructor = in.equals("fuzion.java.call_c0");
          var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
          return args ->
            {
              int a = 1;
              var clNameI =                      (Instance) args.get(a++);
              var nameI   = constructor ? null : (Instance) args.get(a++);
              var sigI    =                      (Instance) args.get(a++);
              var thizR   = !virtual    ? null :  (JavaRef) args.get(a++);

              var argz = args.get(a); // of type fuzion.sys.internal_array<JavaObject>, we need to get field argz.data
              var sac = executor.fuir().clazzArgClazz(innerClazz, executor.fuir().clazzArgCount(innerClazz) - 1);
              var argzData = Interpreter.getField(executor.fuir().clazz_fuzionSysArray_u8_data(), sac, argz, false);

              String clName =                          (String) JavaInterface.instanceToJavaObject(clNameI);
              String name   = nameI   == null ? null : (String) JavaInterface.instanceToJavaObject(nameI  );
              String sig    =                          (String) JavaInterface.instanceToJavaObject(sigI   );
              Object thiz   = thizR   == null ? null :          JavaInterface.javaRefToJavaObject (thizR  );
              return JavaInterface.call(clName, name, sig, thiz, argzData, resultClazz);
            };
        });
    putUnsafe("fuzion.java.cast0", (executor, innerClazz) -> args ->
        {
          var arg = JavaInterface.javaRefToJavaObject((JavaRef) args.get(1));
          var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
          return JavaInterface.javaObjectToInstance(arg, null, resultClazz);
        });
    putUnsafe("fuzion.java.array_length",  (executor, innerClazz) -> args ->
        {
          var arr = JavaInterface.instanceToJavaObject(args.get(1).instance());
          return new i32Value(Array.getLength(arr));
        });
    putUnsafe("fuzion.java.array_get", (executor, innerClazz) -> args ->
        {
          var arr = JavaInterface.instanceToJavaObject(args.get(1).instance());
          var ix  = args.get(2).i32Value();
          var res = Array.get(arr, ix);
          var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
          return JavaInterface.javaObjectToInstance(res, resultClazz);
        });
    putUnsafe("fuzion.java.array_to_java_object0", (executor, innerClazz) -> args ->
        {
          var argz = args.get(1);
          var sac = executor.fuir().clazzArgClazz(innerClazz, 0);
          var argzData = Interpreter.getField(executor.fuir().clazz_fuzionSysArray_u8_data(), sac, argz, false);
          var arrA = argzData.arrayData();
          var res = arrA._array;
          var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
          return JavaInterface.javaObjectToInstance(res, resultClazz);
        });
    putUnsafe("fuzion.java.create_jvm", (executor, innerClazz) -> args -> Value.EMPTY_VALUE);
    putUnsafe("fuzion.java.string_to_java_object0", (executor, innerClazz) -> args ->
        {
          var argz = args.get(1);
          var sac = executor.fuir().clazzArgClazz(innerClazz, 0);
          var argzData = Interpreter.getField(executor.fuir().clazz_fuzionSysArray_u8_data(), sac, argz, false);
          var str = utf8ByteArrayDataToString(argzData);
          var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
          return JavaInterface.javaObjectToInstance(str, resultClazz);
        });
    putUnsafe("fuzion.java.java_string_to_string", (executor, innerClazz) -> args ->
        {
          var javaString = (String) ((JavaRef)args.get(1))._javaRef;
          return Interpreter.value(javaString == null ? "--null--" : javaString);
        });
    putUnsafe("fuzion.java.i8_to_java_object", (executor, innerClazz) -> args ->
        {
          var b = args.get(1).i8Value();
          var jb = Byte.valueOf((byte) b);
          var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
          return JavaInterface.javaObjectToInstance(jb, resultClazz);
        });
    putUnsafe("fuzion.java.u16_to_java_object", (executor, innerClazz) -> args ->
        {
          var c = args.get(1).u16Value();
          var jc = Character.valueOf((char) c);
          var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
          return JavaInterface.javaObjectToInstance(jc, resultClazz);
        });
    putUnsafe("fuzion.java.i16_to_java_object", (executor, innerClazz) -> args ->
        {
          var s = args.get(1).i16Value();
          var js = Short.valueOf((short) s);
          var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
          return JavaInterface.javaObjectToInstance(js, resultClazz);
        });
    putUnsafe("fuzion.java.i32_to_java_object", (executor, innerClazz) -> args ->
        {
          var i = args.get(1).i32Value();
          var ji = Integer.valueOf(i);
          var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
          return JavaInterface.javaObjectToInstance(ji, resultClazz);
        });
    putUnsafe("fuzion.java.i64_to_java_object", (executor, innerClazz) -> args ->
        {
          var l = args.get(1).i64Value();
          var jl = Long.valueOf(l);
          var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
          return JavaInterface.javaObjectToInstance(jl, resultClazz);
        });
    putUnsafe("fuzion.java.f32_to_java_object", (executor, innerClazz) -> args ->
        {
          var f32 = args.get(1).f32Value();
          var jf = Float.valueOf(f32);
          var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
          return JavaInterface.javaObjectToInstance(jf, resultClazz);
        });
    putUnsafe("fuzion.java.f64_to_java_object", (executor, innerClazz) -> args ->
        {
          var d = args.get(1).f64Value();
          var jd = Double.valueOf(d);
          var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
          return JavaInterface.javaObjectToInstance(jd, resultClazz);
        });
    putUnsafe("fuzion.java.bool_to_java_object", (executor, innerClazz) -> args ->
        {
          var b = args.get(1).boolValue();
          var jb = Boolean.valueOf(b);
          var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
          return JavaInterface.javaObjectToInstance(jb, resultClazz);
        });
    put("fuzion.sys.internal_array_init.alloc", (executor, innerClazz) -> args ->
        {
          var at = executor.fuir().clazzOuterClazz(innerClazz); // array type
          var et = executor.fuir().clazzActualGeneric(at, 0); // element type
          return ArrayData.alloc(/* size */ args.get(1).i32Value(),
                                 executor.fuir(),
                                 /* type */ et);
        });
    put("fuzion.sys.internal_array.get", (executor, innerClazz) -> args ->
        {
          var at = executor.fuir().clazzOuterClazz(innerClazz); // array type
          var et = executor.fuir().clazzActualGeneric(at, 0); // element type
          return ((ArrayData)args.get(1)).get(
                                   /* index */ args.get(2).i32Value(),
                                   executor.fuir(),
                                   /* type  */ et);
        });
    put("fuzion.sys.internal_array.setel", (executor, innerClazz) -> args ->
        {
          var at = executor.fuir().clazzOuterClazz(innerClazz); // array type
          var et = executor.fuir().clazzActualGeneric(at, 0); // element type
          ((ArrayData)args.get(1)).set(
                              /* index */ args.get(2).i32Value(),
                              /* value */ args.get(3),
                              executor.fuir(),
                              /* type  */ et);
          return Value.EMPTY_VALUE;
        });
    put("fuzion.sys.internal_array.freeze", (executor, innerClazz) -> args ->
        {
          return Value.EMPTY_VALUE;
        });
    put("fuzion.sys.internal_array.ensure_not_frozen", (executor, innerClazz) -> args ->
        {
          return Value.EMPTY_VALUE;
        });
    put("fuzion.sys.env_vars.has0", (executor, innerClazz) -> args -> new boolValue(System.getenv(utf8ByteArrayDataToString(args.get(1))) != null));
    put("fuzion.sys.env_vars.get0", (executor, innerClazz) -> args -> Interpreter.value(System.getenv(utf8ByteArrayDataToString(args.get(1)))));
    // setting env variable not supported in java
    put("fuzion.sys.env_vars.set0"  , (executor, innerClazz) -> args -> new boolValue(false));
    // unsetting env variable not supported in java
    put("fuzion.sys.env_vars.unset0", (executor, innerClazz) -> args -> new boolValue(false));
    put("fuzion.sys.misc.unique_id",(executor, innerClazz) -> args -> new u64Value(_last_unique_id_.incrementAndGet()));
    put("fuzion.sys.thread.spawn0", (executor, innerClazz) -> args ->
        {
          var oc   = executor.fuir().clazzArgClazz(innerClazz, 0);
          var cc = executor.fuir().lookupCall(oc);
          var t = new Thread(() -> Errors.runAndExit
                             (() -> executor.callOnNewInstance(NO_SITE, cc, args.get(1), new List<>())));
          t.setDaemon(true);
          t.start();
          return new i64Value(_startedThreads_.add(t));
        });
    put("fuzion.sys.thread.join0", (executor, innerClazz) -> args ->
        {
          var thread = _startedThreads_.get(args.get(1).i64Value());
          var result = false;
          do
            {
              try
                {
                  thread.join();
                  result = true;
                }
              catch (InterruptedException e)
                {

                }
            }
          while (!result);

          // NYI: comment, fridi:
          // Furthermore, remove should probably not be called by join, but either by
          // the Thread itself or by some cleanup mechanism that removes terminated
          // threads, either when new threads are started or by a system thread that
          // joins and removes threads that are about to terminate.
          _startedThreads_.remove(args.get(1).i64Value());

          return Value.EMPTY_VALUE;
        });


    putUnsafe("fuzion.sys.net.bind0"    , (executor, innerClazz) -> args -> {
      var family = args.get(1).i32Value();
      var socketType = args.get(2).i32Value();
      var protocol = args.get(3).i32Value();
      var host = utf8ByteArrayDataToString(args.get(4));
      var port = utf8ByteArrayDataToString(args.get(5));
      var result = (long[])args.get(6).arrayData()._array;
      if (family != 2 && family != 10)
        {
          new RuntimeException("NYI: UNDER DEVELOPMENT: bind for family=" + family);
        }
      try
        {
          return switch (protocol)
            {
              case 6 ->
                {
                  var ss = ServerSocketChannel.open();
                  ss.bind(new InetSocketAddress(host, Integer.parseInt(port)));
                  result[0] = _openStreams_.add(ss);
                  yield new i32Value(0);
                }
              case 17 ->
                {
                  var ss = DatagramChannel.open();
                  ss.bind(new InetSocketAddress(host, Integer.parseInt(port)));
                  result[0] = _openStreams_.add(ss);
                  yield new i32Value(0);
                }
              default -> throw new Error("NYI: UNDER DEVELOPMENT: bind for protocol=" + protocol);
            };
        }
      catch(BindException e)
        {
          result[0] = SystemErrNo.EADDRINUSE.errno;
          return new i32Value(-1);
        }
      catch(Throwable e)
        {
          result[0] = -1;
          return new i32Value(-1);
        }
    });

    putUnsafe("fuzion.sys.net.listen"  , (executor, innerClazz) -> args -> {
      return new i32Value(0);
    });

    putUnsafe("fuzion.sys.net.accept"  , (executor, innerClazz) -> args -> {
      try
        {
          var asc = _openStreams_.get(args.get(1).i64Value());
          if(asc instanceof ServerSocketChannel ssc)
            {
              var socket = ssc.accept();
              ((long[])args.get(2).arrayData()._array)[0] = _openStreams_.add(socket);
              return new boolValue(true);
            }
          else if(asc instanceof DatagramChannel dc)
            {
              ((long[])args.get(2).arrayData()._array)[0] = args.get(1).i64Value();
              return new boolValue(true);
            }
          throw new Error("Misuse of intrinsic net.accept detected.");
        }
      catch(Throwable e)
        {
          return new boolValue(false);
        }
    });

    putUnsafe("fuzion.sys.net.connect0" , (executor, innerClazz) -> args -> {
      var family = args.get(1).i32Value();
      var socketType = args.get(2).i32Value();
      var protocol = args.get(3).i32Value();
      var host = utf8ByteArrayDataToString(args.get(4));
      var port = utf8ByteArrayDataToString(args.get(5));
      var result = (long[])args.get(6).arrayData()._array;
      if (family != 2 && family != 10)
        {
          new RuntimeException("NYI: UNDER DEVELOPMENT: connect for family=" + family);
        }
      try
        {
          return switch (protocol)
            {
              case 6 ->
                {
                  var socket = SocketChannel.open();
                  socket.connect(new InetSocketAddress(host, Integer.parseInt(port)));
                  result[0] = _openStreams_.add(socket);
                  yield new i32Value(0);
                }
              case 17 ->
                {
                  var ss = DatagramChannel.open();
                  ss.connect(new InetSocketAddress(host, Integer.parseInt(port)));
                  result[0] = _openStreams_.add(ss);
                  yield new i32Value(0);
                }
              default -> throw new Error("NYI: UNDER DEVELOPMENT: connect for protocol=" + protocol);
            };
        }
      catch(IOException e)
        {
          result[0] = SystemErrNo.ECONNREFUSED.errno;
          return new i32Value(-1);
        }
      catch(Throwable e)
        {
          result[0] = SystemErrNo.UNSPECIFIED.errno;
          return new i32Value(-1);
        }
    });

    putUnsafe("fuzion.sys.net.get_peer_address", (executor, innerClazz) -> args -> {
      try
        {
          if (_openStreams_.get(args.get(1).i64Value()) instanceof SocketChannel sockfd)
            {
              byte[] address = ((InetSocketAddress)sockfd.getRemoteAddress()).getAddress().getAddress();
              System.arraycopy(address, 0, args.get(2).arrayData()._array, 0, address.length);
              return new i32Value(address.length);
            }
          return new i32Value(-1);
        }
      catch (Throwable e)
        {
          return new i32Value(-1);
        }
    });

    putUnsafe("fuzion.sys.net.get_peer_port", (executor, innerClazz) -> args -> {
      try
        {
          if (_openStreams_.get(args.get(1).i64Value()) instanceof SocketChannel sockfd)
            {
              return new u16Value(((InetSocketAddress)sockfd.getRemoteAddress()).getPort());
            }
          return new u16Value(0);
        }
      catch (Throwable e)
        {
          return new u16Value(0);
        }
    });

    putUnsafe("fuzion.sys.net.read" , (executor, innerClazz) -> args -> {
      try
        {
          byte[] buff = (byte[])args.get(2).arrayData()._array;
          var desc = _openStreams_.get(args.get(1).i64Value());
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
              throw new Error("Misuse of intrinsic net.read detected.");
            }
          ((long[])args.get(4).arrayData()._array)[0] = bytesRead;
          return new boolValue(bytesRead != -1);
        }
      catch(Throwable e) //SocketTimeoutException and others
        {
          // unspecified error
          ((long[])args.get(4).arrayData()._array)[0] = -1;
          return new boolValue(false);
        }
    });

    putUnsafe("fuzion.sys.net.write" , (executor, innerClazz) -> args -> {
      try
        {
          var fileContent = (byte[])args.get(2).arrayData()._array;
          var sc = (ByteChannel)_openStreams_.get(args.get(1).i64Value());
          sc.write(ByteBuffer.wrap(fileContent));
          return new i32Value(0);
        }
      catch(Throwable e)
        {
          return new i32Value(-1);
        }
    });

    putUnsafe("fuzion.sys.net.close0" , (executor, innerClazz) -> args -> {
      long fd = args.get(1).i64Value();
      return _openStreams_.remove(fd)
        ? new i32Value(0)
        : new i32Value(-1);
    });

    putUnsafe("fuzion.sys.net.set_blocking0" , (executor, innerClazz) -> args -> {
      var asc = (AbstractSelectableChannel)_openStreams_.get(args.get(1).i64Value());
      var blocking = args.get(2).i32Value();
      try
        {
          asc.configureBlocking(blocking == 1);
          return new i32Value(0);
        }
      catch(Throwable e)
        {
          return new i32Value(-1);
        }
    });

    put("safety"                , (executor, innerClazz) -> args -> new boolValue(executor.options().fuzionSafety()));
    put("debug"                 , (executor, innerClazz) -> args -> new boolValue(executor.options().fuzionDebug()));
    put("debug_level"           , (executor, innerClazz) -> args -> new i32Value (executor.options().fuzionDebugLevel()));
    put("i8.as_i32"             , (executor, innerClazz) -> args -> new i32Value (              (                           args.get(0).i8Value() )));
    put("i8.cast_to_u8"         , (executor, innerClazz) -> args -> new u8Value  (       0xff & (                           args.get(0).i8Value() )));
    put("i8.prefix -°"          , (executor, innerClazz) -> args -> new i8Value  ((int) (byte)  (                       -   args.get(0).i8Value() )));
    put("i8.infix +°"           , (executor, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  +   args.get(1).i8Value() )));
    put("i8.infix -°"           , (executor, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  -   args.get(1).i8Value() )));
    put("i8.infix *°"           , (executor, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  *   args.get(1).i8Value() )));
    put("i8.div"                , (executor, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  /   args.get(1).i8Value() )));
    put("i8.mod"                , (executor, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  %   args.get(1).i8Value() )));
    put("i8.infix &"            , (executor, innerClazz) -> args -> new i8Value  (              (args.get(0).i8Value()  &   args.get(1).i8Value() )));
    put("i8.infix |"            , (executor, innerClazz) -> args -> new i8Value  (              (args.get(0).i8Value()  |   args.get(1).i8Value() )));
    put("i8.infix ^"            , (executor, innerClazz) -> args -> new i8Value  (              (args.get(0).i8Value()  ^   args.get(1).i8Value() )));
    put("i8.infix >>"           , (executor, innerClazz) -> args -> new i8Value  (              (args.get(0).i8Value()  >>  args.get(1).i8Value() )));
    put("i8.infix <<"           , (executor, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  <<  args.get(1).i8Value() )));
    put("i8.type.equality"      , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).i8Value()  ==  args.get(2).i8Value() )));
    put("i8.type.lteq"          , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).i8Value()  <=  args.get(2).i8Value() )));
    put("i16.as_i32"            , (executor, innerClazz) -> args -> new i32Value (              (                           args.get(0).i16Value())));
    put("i16.cast_to_u16"       , (executor, innerClazz) -> args -> new u16Value (     0xffff & (                           args.get(0).i16Value())));
    put("i16.prefix -°"         , (executor, innerClazz) -> args -> new i16Value ((int) (short) (                       -   args.get(0).i16Value())));
    put("i16.infix +°"          , (executor, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() +   args.get(1).i16Value())));
    put("i16.infix -°"          , (executor, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() -   args.get(1).i16Value())));
    put("i16.infix *°"          , (executor, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() *   args.get(1).i16Value())));
    put("i16.div"               , (executor, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() /   args.get(1).i16Value())));
    put("i16.mod"               , (executor, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() %   args.get(1).i16Value())));
    put("i16.infix &"           , (executor, innerClazz) -> args -> new i16Value (              (args.get(0).i16Value() &   args.get(1).i16Value())));
    put("i16.infix |"           , (executor, innerClazz) -> args -> new i16Value (              (args.get(0).i16Value() |   args.get(1).i16Value())));
    put("i16.infix ^"           , (executor, innerClazz) -> args -> new i16Value (              (args.get(0).i16Value() ^   args.get(1).i16Value())));
    put("i16.infix >>"          , (executor, innerClazz) -> args -> new i16Value (              (args.get(0).i16Value() >>  args.get(1).i16Value())));
    put("i16.infix <<"          , (executor, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() <<  args.get(1).i16Value())));
    put("i16.type.equality"     , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).i16Value() ==  args.get(2).i16Value())));
    put("i16.type.lteq"         , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).i16Value() <=  args.get(2).i16Value())));
    put("i32.as_i64"            , (executor, innerClazz) -> args -> new i64Value ((long)        (                           args.get(0).i32Value())));
    put("i32.cast_to_u32"       , (executor, innerClazz) -> args -> new u32Value (              (                           args.get(0).i32Value())));
    put("i32.as_f64"            , (executor, innerClazz) -> args -> new f64Value ((double)      (                           args.get(0).i32Value())));
    put("i32.prefix -°"         , (executor, innerClazz) -> args -> new i32Value (              (                       -   args.get(0).i32Value())));
    put("i32.infix +°"          , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() +   args.get(1).i32Value())));
    put("i32.infix -°"          , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() -   args.get(1).i32Value())));
    put("i32.infix *°"          , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() *   args.get(1).i32Value())));
    put("i32.div"               , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() /   args.get(1).i32Value())));
    put("i32.mod"               , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() %   args.get(1).i32Value())));
    put("i32.infix &"           , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() &   args.get(1).i32Value())));
    put("i32.infix |"           , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() |   args.get(1).i32Value())));
    put("i32.infix ^"           , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() ^   args.get(1).i32Value())));
    put("i32.infix >>"          , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() >>  args.get(1).i32Value())));
    put("i32.infix <<"          , (executor, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() <<  args.get(1).i32Value())));
    put("i32.type.equality"     , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).i32Value() ==  args.get(2).i32Value())));
    put("i32.type.lteq"         , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).i32Value() <=  args.get(2).i32Value())));
    put("i64.cast_to_u64"       , (executor, innerClazz) -> args -> new u64Value (              (                           args.get(0).i64Value())));
    put("i64.as_f64"            , (executor, innerClazz) -> args -> new f64Value ((double)      (                           args.get(0).i64Value())));
    put("i64.prefix -°"         , (executor, innerClazz) -> args -> new i64Value (              (                       -   args.get(0).i64Value())));
    put("i64.infix +°"          , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() +   args.get(1).i64Value())));
    put("i64.infix -°"          , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() -   args.get(1).i64Value())));
    put("i64.infix *°"          , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() *   args.get(1).i64Value())));
    put("i64.div"               , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() /   args.get(1).i64Value())));
    put("i64.mod"               , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() %   args.get(1).i64Value())));
    put("i64.infix &"           , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() &   args.get(1).i64Value())));
    put("i64.infix |"           , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() |   args.get(1).i64Value())));
    put("i64.infix ^"           , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() ^   args.get(1).i64Value())));
    put("i64.infix >>"          , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() >>  args.get(1).i64Value())));
    put("i64.infix <<"          , (executor, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() <<  args.get(1).i64Value())));
    put("i64.type.equality"     , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).i64Value() ==  args.get(2).i64Value())));
    put("i64.type.lteq"         , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).i64Value() <=  args.get(2).i64Value())));
    put("u8.as_i32"             , (executor, innerClazz) -> args -> new i32Value (              (                           args.get(0).u8Value() )));
    put("u8.cast_to_i8"         , (executor, innerClazz) -> args -> new i8Value  ((int) (byte)  (                           args.get(0).u8Value() )));
    put("u8.prefix -°"          , (executor, innerClazz) -> args -> new u8Value  (       0xff & (                       -   args.get(0).u8Value() )));
    put("u8.infix +°"           , (executor, innerClazz) -> args -> new u8Value  (       0xff & (args.get(0).u8Value()  +   args.get(1).u8Value() )));
    put("u8.infix -°"           , (executor, innerClazz) -> args -> new u8Value  (       0xff & (args.get(0).u8Value()  -   args.get(1).u8Value() )));
    put("u8.infix *°"           , (executor, innerClazz) -> args -> new u8Value  (       0xff & (args.get(0).u8Value()  *   args.get(1).u8Value() )));
    put("u8.div"                , (executor, innerClazz) -> args -> new u8Value  (Integer.divideUnsigned   (args.get(0).u8Value(), args.get(1).u8Value())));
    put("u8.mod"                , (executor, innerClazz) -> args -> new u8Value  (Integer.remainderUnsigned(args.get(0).u8Value(), args.get(1).u8Value())));
    put("u8.infix &"            , (executor, innerClazz) -> args -> new u8Value  (              (args.get(0).u8Value()  &   args.get(1).u8Value() )));
    put("u8.infix |"            , (executor, innerClazz) -> args -> new u8Value  (              (args.get(0).u8Value()  |   args.get(1).u8Value() )));
    put("u8.infix ^"            , (executor, innerClazz) -> args -> new u8Value  (              (args.get(0).u8Value()  ^   args.get(1).u8Value() )));
    put("u8.infix >>"           , (executor, innerClazz) -> args -> new u8Value  (              (args.get(0).u8Value()  >>> args.get(1).u8Value() )));
    put("u8.infix <<"           , (executor, innerClazz) -> args -> new u8Value  (       0xff & (args.get(0).u8Value()  <<  args.get(1).u8Value() )));
    put("u8.type.equality"      , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).u8Value()  ==  args.get(2).u8Value() )));
    put("u8.type.lteq"          , (executor, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(1).u8Value(), args.get(2).u8Value()) <= 0));
    put("u16.as_i32"            , (executor, innerClazz) -> args -> new i32Value (              (                           args.get(0).u16Value())));
    put("u16.low8bits"          , (executor, innerClazz) -> args -> new u8Value  (       0xff & (                           args.get(0).u16Value())));
    put("u16.cast_to_i16"       , (executor, innerClazz) -> args -> new i16Value ((short)       (                           args.get(0).u16Value())));
    put("u16.prefix -°"         , (executor, innerClazz) -> args -> new u16Value (     0xffff & (                       -   args.get(0).u16Value())));
    put("u16.infix +°"          , (executor, innerClazz) -> args -> new u16Value (     0xffff & (args.get(0).u16Value() +   args.get(1).u16Value())));
    put("u16.infix -°"          , (executor, innerClazz) -> args -> new u16Value (     0xffff & (args.get(0).u16Value() -   args.get(1).u16Value())));
    put("u16.infix *°"          , (executor, innerClazz) -> args -> new u16Value (     0xffff & (args.get(0).u16Value() *   args.get(1).u16Value())));
    put("u16.div"               , (executor, innerClazz) -> args -> new u16Value (Integer.divideUnsigned   (args.get(0).u16Value(), args.get(1).u16Value())));
    put("u16.mod"               , (executor, innerClazz) -> args -> new u16Value (Integer.remainderUnsigned(args.get(0).u16Value(), args.get(1).u16Value())));
    put("u16.infix &"           , (executor, innerClazz) -> args -> new u16Value (              (args.get(0).u16Value() &   args.get(1).u16Value())));
    put("u16.infix |"           , (executor, innerClazz) -> args -> new u16Value (              (args.get(0).u16Value() |   args.get(1).u16Value())));
    put("u16.infix ^"           , (executor, innerClazz) -> args -> new u16Value (              (args.get(0).u16Value() ^   args.get(1).u16Value())));
    put("u16.infix >>"          , (executor, innerClazz) -> args -> new u16Value (              (args.get(0).u16Value() >>> args.get(1).u16Value())));
    put("u16.infix <<"          , (executor, innerClazz) -> args -> new u16Value (     0xffff & (args.get(0).u16Value() <<  args.get(1).u16Value())));
    put("u16.type.equality"     , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).u16Value() ==  args.get(2).u16Value())));
    put("u16.type.lteq"         , (executor, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(1).u16Value(), args.get(2).u16Value()) <= 0));
    put("u32.as_i64"            , (executor, innerClazz) -> args -> new i64Value (Integer.toUnsignedLong(args.get(0).u32Value())));
    put("u32.low8bits"          , (executor, innerClazz) -> args -> new u8Value  (       0xff & (                           args.get(0).u32Value())));
    put("u32.low16bits"         , (executor, innerClazz) -> args -> new u16Value (     0xffff & (                           args.get(0).u32Value())));
    put("u32.cast_to_i32"       , (executor, innerClazz) -> args -> new i32Value (              (                           args.get(0).u32Value())));
    put("u32.as_f64"            , (executor, innerClazz) -> args -> new f64Value ((double)      Integer.toUnsignedLong(     args.get(0).u32Value())));
    put("u32.cast_to_f32"       , (executor, innerClazz) -> args -> new f32Value (              Float.intBitsToFloat(       args.get(0).u32Value())));
    put("u32.prefix -°"         , (executor, innerClazz) -> args -> new u32Value (              (                       -   args.get(0).u32Value())));
    put("u32.infix +°"          , (executor, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() +   args.get(1).u32Value())));
    put("u32.infix -°"          , (executor, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() -   args.get(1).u32Value())));
    put("u32.infix *°"          , (executor, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() *   args.get(1).u32Value())));
    put("u32.div"               , (executor, innerClazz) -> args -> new u32Value (Integer.divideUnsigned   (args.get(0).u32Value(), args.get(1).u32Value())));
    put("u32.mod"               , (executor, innerClazz) -> args -> new u32Value (Integer.remainderUnsigned(args.get(0).u32Value(), args.get(1).u32Value())));
    put("u32.infix &"           , (executor, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() &   args.get(1).u32Value())));
    put("u32.infix |"           , (executor, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() |   args.get(1).u32Value())));
    put("u32.infix ^"           , (executor, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() ^   args.get(1).u32Value())));
    put("u32.infix >>"          , (executor, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() >>> args.get(1).u32Value())));
    put("u32.infix <<"          , (executor, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() <<  args.get(1).u32Value())));
    put("u32.type.equality"     , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).u32Value() ==  args.get(2).u32Value())));
    put("u32.type.lteq"         , (executor, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(1).u32Value(), args.get(2).u32Value()) <= 0));
    put("u64.low8bits"          , (executor, innerClazz) -> args -> new u8Value  (       0xff & ((int)                      args.get(0).u64Value())));
    put("u64.low16bits"         , (executor, innerClazz) -> args -> new u16Value (     0xffff & ((int)                      args.get(0).u64Value())));
    put("u64.low32bits"         , (executor, innerClazz) -> args -> new u32Value ((int)         (                           args.get(0).u64Value())));
    put("u64.cast_to_i64"       , (executor, innerClazz) -> args -> new i64Value (              (                           args.get(0).u64Value())));
    put("u64.as_f64"            , (executor, innerClazz) -> args -> new f64Value (Double.parseDouble(Long.toUnsignedString(args.get(0).u64Value()))));
    put("u64.cast_to_f64"       , (executor, innerClazz) -> args -> new f64Value (              Double.longBitsToDouble(    args.get(0).u64Value())));
    put("u64.prefix -°"         , (executor, innerClazz) -> args -> new u64Value (              (                       -   args.get(0).u64Value())));
    put("u64.infix +°"          , (executor, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() +   args.get(1).u64Value())));
    put("u64.infix -°"          , (executor, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() -   args.get(1).u64Value())));
    put("u64.infix *°"          , (executor, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() *   args.get(1).u64Value())));
    put("u64.div"               , (executor, innerClazz) -> args -> new u64Value (Long.divideUnsigned   (args.get(0).u64Value(), args.get(1).u64Value())));
    put("u64.mod"               , (executor, innerClazz) -> args -> new u64Value (Long.remainderUnsigned(args.get(0).u64Value(), args.get(1).u64Value())));
    put("u64.infix &"           , (executor, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() &   args.get(1).u64Value())));
    put("u64.infix |"           , (executor, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() |   args.get(1).u64Value())));
    put("u64.infix ^"           , (executor, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() ^   args.get(1).u64Value())));
    put("u64.infix >>"          , (executor, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() >>> args.get(1).u64Value())));
    put("u64.infix <<"          , (executor, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() <<  args.get(1).u64Value())));
    put("u64.type.equality"     , (executor, innerClazz) -> args -> new boolValue(              (args.get(1).u64Value() ==  args.get(2).u64Value())));
    put("u64.type.lteq"         , (executor, innerClazz) -> args -> new boolValue(Long.compareUnsigned(args.get(1).u64Value(), args.get(2).u64Value()) <= 0));
    put("f32.prefix -"          , (executor, innerClazz) -> args -> new f32Value (                (                       -  args.get(0).f32Value())));
    put("f32.infix +"           , (executor, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() +  args.get(1).f32Value())));
    put("f32.infix -"           , (executor, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() -  args.get(1).f32Value())));
    put("f32.infix *"           , (executor, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() *  args.get(1).f32Value())));
    put("f32.infix /"           , (executor, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() /  args.get(1).f32Value())));
    put("f32.infix %"           , (executor, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() %  args.get(1).f32Value())));
    put("f32.infix **"          , (executor, innerClazz) -> args -> new f32Value ((float) Math.pow(args.get(0).f32Value(),   args.get(1).f32Value())));
    put("f32.infix ="           , (executor, innerClazz) -> args -> new boolValue(                (args.get(0).f32Value() == args.get(1).f32Value())));
    put("f32.infix <="          , (executor, innerClazz) -> args -> new boolValue(                (args.get(0).f32Value() <= args.get(1).f32Value())));
    put("f32.infix >="          , (executor, innerClazz) -> args -> new boolValue(                (args.get(0).f32Value() >= args.get(1).f32Value())));
    put("f32.infix <"           , (executor, innerClazz) -> args -> new boolValue(                (args.get(0).f32Value() <  args.get(1).f32Value())));
    put("f32.infix >"           , (executor, innerClazz) -> args -> new boolValue(                (args.get(0).f32Value() >  args.get(1).f32Value())));
    put("f32.as_f64"            , (executor, innerClazz) -> args -> new f64Value((double)                                    args.get(0).f32Value() ));
    put("f32.cast_to_u32"       , (executor, innerClazz) -> args -> new u32Value (    Float.floatToIntBits(                  args.get(0).f32Value())));
    put("f64.prefix -"          , (executor, innerClazz) -> args -> new f64Value (                (                       -  args.get(0).f64Value())));
    put("f64.infix +"           , (executor, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() +  args.get(1).f64Value())));
    put("f64.infix -"           , (executor, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() -  args.get(1).f64Value())));
    put("f64.infix *"           , (executor, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() *  args.get(1).f64Value())));
    put("f64.infix /"           , (executor, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() /  args.get(1).f64Value())));
    put("f64.infix %"           , (executor, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() %  args.get(1).f64Value())));
    put("f64.infix **"          , (executor, innerClazz) -> args -> new f64Value (        Math.pow(args.get(0).f64Value(),   args.get(1).f64Value())));
    put("f64.infix ="           , (executor, innerClazz) -> args -> new boolValue(                (args.get(0).f64Value() == args.get(1).f64Value())));
    put("f64.infix <="          , (executor, innerClazz) -> args -> new boolValue(                (args.get(0).f64Value() <= args.get(1).f64Value())));
    put("f64.infix >="          , (executor, innerClazz) -> args -> new boolValue(                (args.get(0).f64Value() >= args.get(1).f64Value())));
    put("f64.infix <"           , (executor, innerClazz) -> args -> new boolValue(                (args.get(0).f64Value() <  args.get(1).f64Value())));
    put("f64.infix >"           , (executor, innerClazz) -> args -> new boolValue(                (args.get(0).f64Value() >  args.get(1).f64Value())));
    put("f64.as_i64_lax"        , (executor, innerClazz) -> args -> new i64Value((long)                                      args.get(0).f64Value() ));
    put("f64.as_f32"            , (executor, innerClazz) -> args -> new f32Value((float)                                     args.get(0).f64Value() ));
    put("f64.cast_to_u64"       , (executor, innerClazz) -> args -> new u64Value (    Double.doubleToLongBits(               args.get(0).f64Value())));
    put("f32.is_NaN"            , (executor, innerClazz) -> args -> new boolValue(                               Float.isNaN(args.get(0).f32Value())));
    put("f64.is_NaN"            , (executor, innerClazz) -> args -> new boolValue(                              Double.isNaN(args.get(0).f64Value())));
    put("f32.acos"              , (executor, innerClazz) -> args -> new f32Value ((float)           Math.acos(               args.get(0).f32Value())));
    put("f32.asin"              , (executor, innerClazz) -> args -> new f32Value ((float)           Math.asin(               args.get(0).f32Value())));
    put("f32.atan"              , (executor, innerClazz) -> args -> new f32Value ((float)           Math.atan(               args.get(0).f32Value())));
    put("f32.cos"               , (executor, innerClazz) -> args -> new f32Value ((float)           Math.cos(                args.get(0).f32Value())));
    put("f32.cosh"              , (executor, innerClazz) -> args -> new f32Value ((float)           Math.cosh(               args.get(0).f32Value())));
    put("f32.exp"               , (executor, innerClazz) -> args -> new f32Value ((float)           Math.exp(                args.get(0).f32Value())));
    put("f32.log"               , (executor, innerClazz) -> args -> new f32Value ((float)           Math.log(                args.get(0).f32Value())));
    put("f32.sin"               , (executor, innerClazz) -> args -> new f32Value ((float)          Math.sin(                 args.get(0).f32Value())));
    put("f32.sinh"              , (executor, innerClazz) -> args -> new f32Value ((float)          Math.sinh(                args.get(0).f32Value())));
    put("f32.square_root"       , (executor, innerClazz) -> args -> new f32Value ((float)          Math.sqrt(        (double)args.get(0).f32Value())));
    put("f32.tan"               , (executor, innerClazz) -> args -> new f32Value ((float)          Math.tan(                 args.get(0).f32Value())));
    put("f32.tanh"              , (executor, innerClazz) -> args -> new f32Value ((float)          Math.tanh(                args.get(0).f32Value())));
    put("f64.acos"              , (executor, innerClazz) -> args -> new f64Value (                 Math.acos(                args.get(0).f64Value())));
    put("f64.asin"              , (executor, innerClazz) -> args -> new f64Value (                 Math.asin(                args.get(0).f64Value())));
    put("f64.atan"              , (executor, innerClazz) -> args -> new f64Value (                 Math.atan(                args.get(0).f64Value())));
    put("f64.cos"               , (executor, innerClazz) -> args -> new f64Value (                 Math.cos(                 args.get(0).f64Value())));
    put("f64.cosh"              , (executor, innerClazz) -> args -> new f64Value (                 Math.cosh(                args.get(0).f64Value())));
    put("f64.exp"               , (executor, innerClazz) -> args -> new f64Value (                 Math.exp(                 args.get(0).f64Value())));
    put("f64.log"               , (executor, innerClazz) -> args -> new f64Value (                 Math.log(                 args.get(0).f64Value())));
    put("f64.sin"               , (executor, innerClazz) -> args -> new f64Value (                 Math.sin(                 args.get(0).f64Value())));
    put("f64.sinh"              , (executor, innerClazz) -> args -> new f64Value (                 Math.sinh(                args.get(0).f64Value())));
    put("f64.square_root"       , (executor, innerClazz) -> args -> new f64Value (                 Math.sqrt(                args.get(0).f64Value())));
    put("f64.tan"               , (executor, innerClazz) -> args -> new f64Value (                 Math.tan(                 args.get(0).f64Value())));
    put("f64.tanh"              , (executor, innerClazz) -> args -> new f64Value (                 Math.tanh(                args.get(0).f64Value())));
    put("f32.type.epsilon"      , (executor, innerClazz) -> args -> new f32Value (                  Math.ulp(                (float)1)));
    put("f32.type.max"          , (executor, innerClazz) -> args -> new f32Value (                                           Float.MAX_VALUE));
    put("f32.type.max_exp"      , (executor, innerClazz) -> args -> new i32Value (                                           Float.MAX_EXPONENT));
    put("f32.type.min_positive" , (executor, innerClazz) -> args -> new f32Value (                                           Float.MIN_NORMAL));
    put("f32.type.min_exp"      , (executor, innerClazz) -> args -> new i32Value (                                           Float.MIN_EXPONENT));
    put("f64.type.epsilon"      , (executor, innerClazz) -> args -> new f64Value (                 Math.ulp(                 (double)1)));
    put("f64.type.max"          , (executor, innerClazz) -> args -> new f64Value (                                               Double.MAX_VALUE));
    put("f64.type.max_exp"      , (executor, innerClazz) -> args -> new i32Value (                                               Double.MAX_EXPONENT));
    put("f64.type.min_positive" , (executor, innerClazz) -> args -> new f64Value (                                               Double.MIN_NORMAL));
    put("f64.type.min_exp"      , (executor, innerClazz) -> args -> new i32Value (                                               Double.MIN_EXPONENT));
    put("fuzion.std.nano_time"  , (executor, innerClazz) -> args -> new u64Value (System.nanoTime()));
    put("fuzion.std.nano_sleep" , (executor, innerClazz) -> args ->
        {
          var d = args.get(1).u64Value();
          try
            {
              TimeUnit.NANOSECONDS.sleep(d < 0 ? Long.MAX_VALUE : d);
            }
          catch (InterruptedException ie)
            {
              throw new Error("unexpected interrupt", ie);
            }
          return new Instance(executor.fuir().clazz(FUIR.SpecialClazzes.c_unit));
        });
    put("fuzion.std.date_time", (executor, innerClazz) -> args ->
      {
        Date date = new Date();
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTime(date);
        var arg0 = (int[])args.get(1).arrayData()._array;
        arg0[0] = calendar.get(Calendar.YEAR);
        arg0[1] = calendar.get(Calendar.DAY_OF_YEAR);
        arg0[2] = calendar.get(Calendar.HOUR_OF_DAY);
        arg0[3] = calendar.get(Calendar.MINUTE);
        arg0[4] = calendar.get(Calendar.SECOND);
        arg0[5] = calendar.get(Calendar.MILLISECOND) * 1000;
        return new Instance(executor.fuir().clazz(FUIR.SpecialClazzes.c_unit));
      });
    put("effect.type.abort0"      ,
        "effect.type.default0"    ,
        "effect.type.instate0"    ,
        "effect.type.is_instated0",
        "effect.type.replace0"    , (executor, innerClazz) -> effect(executor, innerClazz));

    putUnsafe("fuzion.sys.process.create"  , (executor, innerClazz) -> args -> {
      var process_and_args = Arrays
        .stream(((Value[])args.get(1).arrayData()._array))
        .limit(args.get(2).i32Value()-1)
        .map(x -> utf8ByteArrayDataToString(x))
        .collect(Collectors.toList());

      var env_vars = Arrays
        .stream(((Value[])args.get(3).arrayData()._array))
        .limit(args.get(4).i32Value()-1)
        .map(x -> utf8ByteArrayDataToString(x))
        .collect(Collectors.toMap((x -> x.split("=")[0]), (x -> x.split("=")[1])));

      var result = (long[])args.get(5).arrayData()._array;
      try
        {
          var pb = new ProcessBuilder()
                              .command(process_and_args);

          pb.environment().putAll(env_vars);

          var process = pb.start();

          result[0] = _openProcesses_.add(process);
          result[1] = _openStreams_.add(process.getOutputStream());
          result[2] = _openStreams_.add(process.getInputStream());
          result[3] = _openStreams_.add(process.getErrorStream());
          return new i32Value(0);
        }
      catch (Throwable e)
        {
          return new i32Value(-1);
        }
    });

    put("fuzion.sys.process.wait"    , (executor, innerClazz) -> args -> {
      var desc = args.get(1).i64Value();
      var p = _openProcesses_.get(desc);
      try
        {
          var result = p.waitFor();
          _openProcesses_.remove(desc);
          return new i32Value(result);
        }
      catch(Throwable e)
        {
          return new i32Value(-1);
        }
    });

    put("fuzion.sys.pipe.read"       , (executor, innerClazz) -> args -> {
      var desc = args.get(1).i64Value();
      var buff = (byte[])args.get(2).arrayData()._array;
      var is = (InputStream) _openStreams_.get(desc);
      try
        {
          var readBytes = is.read(buff);

          return readBytes == -1
            ? new i32Value(0)
            : new i32Value(readBytes);
        }
      catch (Throwable e)
        {
          return new i32Value(-1);
        }
    });

    put("fuzion.sys.pipe.write"      , (executor, innerClazz) -> args -> {
      var desc = args.get(1).i64Value();
      var buff = (byte[])args.get(2).arrayData()._array;
      var os = (OutputStream) _openStreams_.get(desc);
      try
        {
          os.write(buff);
          return new i32Value(buff.length);
        }
      catch (Throwable e)
        {
          return new i32Value(-1);
        }
    });

    put("fuzion.sys.pipe.close"      , (executor, innerClazz) -> args -> {
      var desc = args.get(1).i64Value();
      return _openStreams_.remove(desc)
        ? new i32Value(0)
        : new i32Value(-1);
    });

    /* NYI: UNDER DEVELOPMENT: abusing javaObjectToPlainInstance in mtx_*, cnd_* intrinsics
      replace by returnOutcome like in jvm backend.
    */
    /* ReentrantLock */
    put("concur.sync.mtx_init", (executor, innerClazz) -> args -> {
      var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
      return JavaInterface.javaObjectToInstance(new ReentrantLock(), resultClazz);
    });
    put("concur.sync.mtx_lock", (executor, innerClazz) -> args -> {
      ((ReentrantLock) ((JavaRef) args.get(1))._javaRef).lock();
      return new boolValue(true);
    });
    put("concur.sync.mtx_trylock", (executor, innerClazz) -> args -> new boolValue(
      ((ReentrantLock) ((JavaRef) args.get(1))._javaRef).tryLock()));
    put("concur.sync.mtx_unlock", (executor, innerClazz) -> args -> {
      try
        {
          ((ReentrantLock) ((JavaRef) args.get(1))._javaRef).unlock();
          return new boolValue(true);
        }
      catch (IllegalMonitorStateException e)
        {
          return new boolValue(false);
        }
    });
    put("concur.sync.mtx_destroy", (executor, innerClazz) -> args -> executor.unitValue());

    /* Condition */
    put("concur.sync.cnd_init", (executor, innerClazz) -> args -> {
      var resultClazz = executor.fuir().clazzResultClazz(innerClazz);
      return JavaInterface.javaObjectToInstance(
        ((ReentrantLock) ((JavaRef) args.get(1))._javaRef).newCondition(), resultClazz);
    });
    put("concur.sync.cnd_signal", (executor, innerClazz) -> args -> {
      try
        {
          ((Condition) ((JavaRef) args.get(1))._javaRef).signal();
          return new boolValue(true);
        }
      catch (Exception e)
        {
          return new boolValue(false);
        }
    });
    put("concur.sync.cnd_broadcast", (executor, innerClazz) -> args -> {
      try
        {
          ((Condition) ((JavaRef) args.get(1))._javaRef).signalAll();
          return new boolValue(true);
        }
      catch (Exception e)
        {
          return new boolValue(false);
        }
    });
    put("concur.sync.cnd_wait", (executor, innerClazz) -> args -> {
      try
        {
          ((Condition) ((JavaRef) args.get(1))._javaRef).await();
          return new boolValue(true);
        }
      catch (Exception e)
        {
          return new boolValue(false);
        }
    });
    put("concur.sync.cnd_destroy", (executor, innerClazz) -> args -> executor.unitValue());
  }


  static class Abort extends Error
  {
    int _effect;
    Abort(int effect)
    {
      super();
      this._effect = effect;
    }
  }


  /**
   * Create code for one-way monad intrinsics.
   *
   * @param innerClazz the frame clazz of the called feature
   *
   * @return a Callable instance to execute the intrinsic call.
   */
  static Callable effect(Executor executor, int innerClazz)
  {
    return (args) ->
      {
        var fuir = executor.fuir();
        var m   = args.get(0);
        var in  = fuir.clazzOriginalName(innerClazz);
        int ecl = fuir.effectTypeFromInstrinsic(innerClazz);
        var ev  = args.size() > 1 ? args.get(1) : null;
        var effects = FuzionThread.current()._effects;
        switch (in)
          {
          case "effect.type.abort0"    : throw new Abort(ecl);
          case "effect.type.default0"  : if (effects.get(ecl) == null) { check(fuir.clazzIsUnitType(ecl) || ev != Value.EMPTY_VALUE); effects.put(ecl, ev); } break;
          case "effect.type.instate0"  :
            {
              // save old and instate new effect value ev:
              var prev = effects.get(ecl);
              effects.put(ecl, ev);

              // the callbacks to Fuzion for the code, fallback and finally:
              var call     = fuir.lookupCall(fuir.clazzActualGeneric(innerClazz, 0));
              var call_def = fuir.lookupCall(fuir.clazzActualGeneric(innerClazz, 1));
              var finallie = fuir.lookup_static_finally(ecl);

              Abort aborted = null;
              try
                { // run the code while effect is instated
                  var ignore = executor.callOnNewInstance(NO_SITE, call, args.get(2), new List<>());
                }
              catch (Abort a)
                {
                  aborted = a;
                }

              // in any case, restore old state and run finally on final effect value:
              var final_ev = effects.get(ecl);
              effects.put(ecl, prev);
              var ignore = executor.callOnNewInstance(NO_SITE, finallie, final_ev, new List<>());

              if (aborted != null)
                {
                  if (aborted._effect != ecl)
                    { // the abort came from another, surrounding effect, so pass it on
                      throw aborted;
                    }
                  // we got aborted, so we run `call_def` to produce default result of `instate`.
                  ignore = executor.callOnNewInstance(NO_SITE, call_def, args.get(3), new List<>(final_ev));
                }
            }
          case "effect.type.is_instated0": return new boolValue(effects.get(ecl) != null /* NOTE not containsKey since ecl may map to null! */ );
          case "effect.type.replace0"    : check(effects.get(ecl) != null, fuir.clazzIsUnitType(ecl) || ev != Value.EMPTY_VALUE); effects.put(ecl, ev);   break;
          default: throw new Error("unexpected effect intrinsic '"+in+"'");
          }
        return Value.EMPTY_VALUE;
      };
  }


  @SuppressWarnings("unchecked")
  private static Iterator<Path> getIterator(long v)
  {
    return (Iterator<Path>)_openStreams_.get(v);
  }


  /**
   * Get InetSocketAddress of TCP (SocketChannel) or UDP (DatagramChannel) channel.
   */
  static InetSocketAddress getRemoteAddress(AutoCloseable asc) throws IOException
  {
    if (asc instanceof DatagramChannel dc)
      {
        return (InetSocketAddress) dc.getRemoteAddress();
      }
    return (InetSocketAddress)((SocketChannel)asc).getRemoteAddress();
  }

}

/* end of file */
