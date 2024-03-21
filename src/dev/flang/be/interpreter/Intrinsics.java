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
import java.util.stream.Collectors;

import dev.flang.air.Clazz;
import dev.flang.air.Clazzes;
import dev.flang.ast.AbstractType; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Types; // NYI: remove dependency! Use dev.flang.fuir instead.
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
    Callable get(Interpreter interpreter, Clazz innerClazz);
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
      catch(Exception e)
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
  private static void putUnsafe(String n, IntrinsicCode c) { _intrinsics_.put(n, (interpreter, innerClazz) -> args -> {
    if (!ENABLE_UNSAFE_INTRINSICS)
      {
        Errors.fatal("*** error: unsafe feature "+innerClazz+" disabled");
      }
    return c.get(interpreter, innerClazz).call(args);
  }); }
  private static void put(String n1, String n2, IntrinsicCode c) { put(n1, c); put(n2, c); }
  private static void putUnsafe(String n1, String n2, IntrinsicCode c) { putUnsafe(n1, c); putUnsafe(n2, c); }
  private static void put(String n1, String n2, String n3, IntrinsicCode c) { put(n1, c); put(n2, c); put(n3, c); }
  private static void putUnsafe(String n1, String n2, String n3, IntrinsicCode c) { putUnsafe(n1, c); putUnsafe(n2, c); putUnsafe(n3, c); }
  private static void put(String n1, String n2, String n3, String n4, IntrinsicCode c) { put(n1, c); put(n2, c); put(n3, c); put(n4, c); }
  private static void putUnsafe(String n1, String n2, String n3, String n4, IntrinsicCode c) { putUnsafe(n1, c); putUnsafe(n2, c); putUnsafe(n3, c); putUnsafe(n4, c); }


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
  public static Callable call(Interpreter interpreter, Clazz innerClazz)
  {
    if (PRECONDITIONS) require
      (innerClazz.feature().isIntrinsic());

    Callable result;
    var f = innerClazz.feature();
    String in = f.qualifiedName();   // == _fuir.clazzOriginalName(cl);
    // NYI: We must check the argument count in addition to the name!
    var ca = _intrinsics_.get(in);
    if (ca != null)
      {
        result = ca.get(interpreter, innerClazz);
      }
    else
      {
        Errors.fatal(f.pos(),
                     "Intrinsic feature not supported",
                     "Missing intrinsic feature: " + f.qualifiedName());
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
    put("Type.name"            , (interpreter, innerClazz) -> args -> Interpreter.value(innerClazz._outer.typeName()));

    put("concur.atomic.compare_and_swap0",  (interpreter, innerClazz) -> args ->
        {
          var a = innerClazz._outer;
          var f = Types.resolved.f_concur_atomic_v;
          var thiz      = args.get(0);
          var expected  = args.get(1);
          var new_value = args.get(2);
          synchronized (LOCK_FOR_ATOMIC)
            {
              var res = Interpreter.getField(f, -1, a, thiz, false); // NYI: HACK: We must clone this!
              if (Interpreter.compareField(f, -1, a, thiz, expected))
                {
                  res = expected;   // NYI: HACK: workaround since res was not cloned
                  Interpreter.setField(f, -1, a, thiz, new_value);
                }
              return res;
            }
        });
    put("concur.atomic.compare_and_set0",  (interpreter, innerClazz) -> args ->
        {
          var a = innerClazz._outer;
          var f = Types.resolved.f_concur_atomic_v;
          var thiz      = args.get(0);
          var expected  = args.get(1);
          var new_value = args.get(2);
          synchronized (LOCK_FOR_ATOMIC)
            {
              if (Interpreter.compareField(f, -1, a, thiz, expected))
                {
                  Interpreter.setField(f, -1, a, thiz, new_value);
                  return new boolValue(true);
                }
              return new boolValue(false);
            }
        });
    put("concur.atomic.racy_accesses_supported",  (interpreter, innerClazz) -> args ->
        {
          var t = innerClazz._outer._type.generics().get(0);
          return new boolValue
            (t.isRef()                                 ||
             (t.compareTo(Types.resolved.t_i8  ) == 0) ||
             (t.compareTo(Types.resolved.t_i16 ) == 0) ||
             (t.compareTo(Types.resolved.t_i32 ) == 0) ||
             (t.compareTo(Types.resolved.t_u8  ) == 0) ||
             (t.compareTo(Types.resolved.t_u16 ) == 0) ||
             (t.compareTo(Types.resolved.t_u32 ) == 0) ||
             (t.compareTo(Types.resolved.t_f32 ) == 0) ||
             (t.compareTo(Types.resolved.t_bool) == 0)    );
        });
    put("concur.atomic.read0",  (interpreter, innerClazz) -> args ->
        {
          var a = innerClazz._outer;
          var f = Types.resolved.f_concur_atomic_v;
          var thiz = args.get(0);
          synchronized (LOCK_FOR_ATOMIC)
            {
              return Interpreter.getField(f, -1, a, thiz, false);
            }
        });
    put("concur.atomic.write0", (interpreter, innerClazz) -> args ->
        {
          var a = innerClazz._outer;
          var f = Types.resolved.f_concur_atomic_v;
          var thiz = args.get(0);
          synchronized (LOCK_FOR_ATOMIC)
            {
              Interpreter.setField(f, -1, a, thiz, args.get(1));
            }
          return new Instance(Clazzes.c_unit.get());
        });

    put("concur.util.loadFence",   (interpreter, innerClazz) -> args ->
        {
          synchronized (LOCK_FOR_ATOMIC) { };
          return new Instance(Clazzes.c_unit.get());
        });

    put("concur.util.storeFence",  (interpreter, innerClazz) -> args ->
        {
          synchronized (LOCK_FOR_ATOMIC) { };
          return new Instance(Clazzes.c_unit.get());
        });

    put("fuzion.sys.args.count", (interpreter, innerClazz) -> args -> new i32Value(interpreter._options_.getBackendArgs().size() + 1));
    put("fuzion.sys.args.get"  , (interpreter, innerClazz) -> args ->
        {
          var i = args.get(1).i32Value();
          var fuir = interpreter._fuir;
          if (i == 0)
            {
              return  Interpreter.value(fuir.clazzAsString(fuir.mainClazzId()));
            }
          else
            {
              return  Interpreter.value(interpreter._options_.getBackendArgs().get(i - 1));
            }
        });
    put("fuzion.sys.fileio.flush"  , (interpreter, innerClazz) -> args ->
        {
          var s = _openStreams_.get(args.get(1).i64Value());
          if (s instanceof PrintStream ps)
            {
              ps.flush();
            }
          return new i32Value(0);
        });

    put("fuzion.sys.stdin.stdin0"  , (interpreter, innerClazz) -> args ->
        {
          return _stdin;
        });
    put("fuzion.sys.out.stdout"    , (interpreter, innerClazz) -> args ->
        {
          return _stdout;
        });
    put("fuzion.sys.err.stderr"    , (interpreter, innerClazz) -> args ->
        {
          return _stderr;
        });
    put("fuzion.sys.fileio.read", (interpreter, innerClazz)-> args ->
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
          catch (Exception e)
            {
              return new i32Value(-2);
            }
        });
    put("fuzion.sys.fileio.write", (interpreter, innerClazz) -> args ->
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
          catch (Exception e)
            {
              return new i32Value(-1);
            }
        });
    putUnsafe("fuzion.sys.fileio.delete", (interpreter, innerClazz) -> args ->
        {
          Path path = Path.of(utf8ByteArrayDataToString(args.get(1)));
          try
            {
              boolean b = Files.deleteIfExists(path);
              return new boolValue(b);
            }
          catch (Exception e)
            {
              return new boolValue(false);
            }
        });
    putUnsafe("fuzion.sys.fileio.move", (interpreter, innerClazz) -> args ->
        {
          Path oldPath = Path.of(utf8ByteArrayDataToString(args.get(1)));
          Path newPath = Path.of(utf8ByteArrayDataToString(args.get(2)));
          try
            {
              Files.move(oldPath, newPath);
              return new boolValue(true);
            }
          catch (Exception e)
            {
              return new boolValue(false);
            }
        });
    putUnsafe("fuzion.sys.fileio.create_dir", (interpreter, innerClazz) -> args ->
        {
          Path path = Path.of(utf8ByteArrayDataToString(args.get(1)));
          try
            {
              Files.createDirectory(path);
              return new boolValue(true);
            }
          catch (Exception e)
            {
              return new boolValue(false);
            }
        });
    putUnsafe("fuzion.sys.fileio.open", (interpreter, innerClazz) -> args ->
        {
          var open_results = (long[])args.get(2).arrayData()._array;
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
          catch (Exception e)
            {
              open_results[1] = -1;
            }
          return Value.EMPTY_VALUE;
        });
    putUnsafe("fuzion.sys.fileio.close", (interpreter, innerClazz) -> args ->
        {
          long fd = args.get(1).i64Value();
          return _openStreams_.remove(fd)
            ? new i8Value(0)
            : new i8Value(-1);
        });
    putUnsafe("fuzion.sys.fileio.stats",
        "fuzion.sys.fileio.lstats", // NYI : should be altered in the future to not resolve symbolic links
        (interpreter, innerClazz) -> args ->
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

          stats[0] = err.errno;
          stats[1] = 0;
          stats[2] = 0;
          stats[3] = 0;
          return new boolValue(false);
        });
    putUnsafe("fuzion.sys.fileio.seek", (interpreter, innerClazz) -> args ->
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
          catch (Exception e)
            {
              seekResults[1] = -1;
              return Value.EMPTY_VALUE;
            }
        });
    putUnsafe("fuzion.sys.fileio.file_position", (interpreter, innerClazz) -> args ->
        {
          long fd = args.get(1).i64Value();
          long[] arr = (long[])args.get(2).arrayData()._array;
          try
            {
              arr[0] = ((RandomAccessFile)_openStreams_.get(fd)).getFilePointer();
              return Value.EMPTY_VALUE;
            }
          catch (Exception e)
            {
              arr[1] = -1;
              return Value.EMPTY_VALUE;
            }
        });
    putUnsafe("fuzion.sys.fileio.mmap", (interpreter, innerClazz) -> args ->
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
                };
            }
          catch (IOException e)
            {
              ((int[])args.get(4).arrayData()._array)[0] = -1;
              return new ArrayData(new byte[0]);
            }
        });
    putUnsafe("fuzion.sys.fileio.munmap", (interpreter, innerClazz) -> args ->
        {
          return new i32Value(0);
        });
    putUnsafe("fuzion.sys.fileio.open_dir", (interpreter, innerClazz) -> args ->
        {
          var open_results = (long[])args.get(2).arrayData()._array;
          try
            {
              var i = Files.walk(Paths.get(utf8ByteArrayDataToString(args.get(1))), 1).iterator();
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
          catch (IOException e)
            {
              open_results[1] = -1;
            }

          return Value.EMPTY_VALUE;
        });
    putUnsafe("fuzion.sys.fileio.read_dir", (interpreter, innerClazz) -> args ->
        {
          var i = (Iterator<Path>)_openStreams_.get(args.get(1).i64Value());
          try
            {
              return Interpreter.value(i.next().getFileName().toString());
            }
          catch (NoSuchElementException e)
            {
              return Interpreter.value("NoSuchElementException encountered!");
            }
        });
    putUnsafe("fuzion.sys.fileio.read_dir_has_next", (interpreter, innerClazz) -> args ->
        {
          return new boolValue(((Iterator<Path>)_openStreams_.get(args.get(1).i64Value())).hasNext());
        });
    putUnsafe("fuzion.sys.fileio.close_dir", (interpreter, innerClazz) -> args ->
        {
          _openStreams_.remove(args.get(1).i64Value());
          return new i64Value(0);
        });
    put("fuzion.sys.fileio.mapped_buffer_get", (interpreter, innerClazz) -> args ->
        {
          return ((ArrayData)args.get(1)).get(/* index */ (int) args.get(2).i64Value(),
                                              /* type  */ Types.resolved.t_u8);
        });
    put("fuzion.sys.fileio.mapped_buffer_set", (interpreter, innerClazz) -> args ->
        {
          ((ArrayData)args.get(1)).set(/* index */ (int) args.get(2).i64Value(),
                                       /* value */ args.get(3),
                                       /* type  */ Types.resolved.t_u8);
          return Value.EMPTY_VALUE;
        });

    put("fuzion.std.exit", (interpreter, innerClazz) -> args ->
        {
          int rc = args.get(1).i32Value();
          System.exit(rc);
          return Value.EMPTY_VALUE;
        });
    put("fuzion.java.Java_Object.is_null0", (interpreter, innerClazz) -> args ->
        {
          Instance thizI = (Instance) args.get(0);
          Object thiz = JavaInterface.instanceToJavaObject(thizI);
          return new boolValue(thiz == null);
        });
    putUnsafe("fuzion.java.get_static_field0",
        "fuzion.java.get_field0"      , (interpreter, innerClazz) ->
        {
          String in = innerClazz.feature().qualifiedName();   // == _fuir.clazzOriginalName(cl);
          var statique = in.equals("fuzion.java.get_static_field0");
          Clazz resultClazz = innerClazz.actualGenerics()[0];
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
    putUnsafe("fuzion.java.call_v0",
        "fuzion.java.call_s0",
        "fuzion.java.call_c0", (interpreter, innerClazz) ->
        {
          String in = innerClazz.feature().qualifiedName();   // == _fuir.clazzOriginalName(cl);
          var virtual     = in.equals("fuzion.java.call_v0");
          var constructor = in.equals("fuzion.java.call_c0");
          Clazz resultClazz = innerClazz.actualGenerics()[0];
          return args ->
            {
              int a = 1;
              var clNameI =                      (Instance) args.get(a++);
              var nameI   = constructor ? null : (Instance) args.get(a++);
              var sigI    =                      (Instance) args.get(a++);
              var thizR   = !virtual    ? null :  (JavaRef) args.get(a++);

              var argz = args.get(a); // of type fuzion.sys.internal_array<JavaObject>, we need to get field argz.data
              var argfields = innerClazz.argumentFields();
              var argsArray = argfields[argfields.length - 1];
              var sac = argsArray.resultClazz();
              var argzData = Interpreter.getField(Types.resolved.f_fuzion_sys_array_data, -1, sac, argz, false);

              String clName =                          (String) JavaInterface.instanceToJavaObject(clNameI);
              String name   = nameI   == null ? null : (String) JavaInterface.instanceToJavaObject(nameI  );
              String sig    =                          (String) JavaInterface.instanceToJavaObject(sigI   );
              Object thiz   = thizR   == null ? null :          JavaInterface.javaRefToJavaObject (thizR  );
              return JavaInterface.call(clName, name, sig, thiz, argzData, resultClazz);
            };
        });
    putUnsafe("fuzion.java.array_length",  (interpreter, innerClazz) -> args ->
        {
          var arr = JavaInterface.instanceToJavaObject(args.get(1).instance());
          return new i32Value(Array.getLength(arr));
        });
    putUnsafe("fuzion.java.array_get", (interpreter, innerClazz) -> args ->
        {
          var arr = JavaInterface.instanceToJavaObject(args.get(1).instance());
          var ix  = args.get(2).i32Value();
          var res = Array.get(arr, ix);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(res, resultClazz);
        });
    putUnsafe("fuzion.java.array_to_java_object0", (interpreter, innerClazz) -> args ->
        {
          var argz = args.get(1);
          var argfields = innerClazz.argumentFields();
          var argsArray = argfields[argfields.length - 1];
          var sac = argsArray.resultClazz();
          var argzData = Interpreter.getField(Types.resolved.f_fuzion_sys_array_data, -1, sac, argz, false);
          var arrA = argzData.arrayData();
          var res = arrA._array;
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(res, resultClazz);
        });
    putUnsafe("fuzion.java.string_to_java_object0", (interpreter, innerClazz) -> args ->
        {
          var argz = args.get(1);
          var argfields = innerClazz.argumentFields();
          var argsArray = argfields[argfields.length - 1];
          var sac = argsArray.resultClazz();
          var argzData = Interpreter.getField(Types.resolved.f_fuzion_sys_array_data, -1, sac, argz, false);
          var str = utf8ByteArrayDataToString(argzData);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(str, resultClazz);
        });
    putUnsafe("fuzion.java.java_string_to_string", (interpreter, innerClazz) -> args ->
        {
          var javaString = (String) JavaInterface.instanceToJavaObject(args.get(1).instance());
          return Interpreter.value(javaString == null ? "--null--" : javaString);
        });
    putUnsafe("fuzion.java.i8_to_java_object", (interpreter, innerClazz) -> args ->
        {
          var b = args.get(1).i8Value();
          var jb = Byte.valueOf((byte) b);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(jb, resultClazz);
        });
    putUnsafe("fuzion.java.u16_to_java_object", (interpreter, innerClazz) -> args ->
        {
          var c = args.get(1).u16Value();
          var jc = Character.valueOf((char) c);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(jc, resultClazz);
        });
    putUnsafe("fuzion.java.i16_to_java_object", (interpreter, innerClazz) -> args ->
        {
          var s = args.get(1).i16Value();
          var js = Short.valueOf((short) s);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(js, resultClazz);
        });
    putUnsafe("fuzion.java.i32_to_java_object", (interpreter, innerClazz) -> args ->
        {
          var i = args.get(1).i32Value();
          var ji = Integer.valueOf(i);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(ji, resultClazz);
        });
    putUnsafe("fuzion.java.i64_to_java_object", (interpreter, innerClazz) -> args ->
        {
          var l = args.get(1).i64Value();
          var jl = Long.valueOf(l);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(jl, resultClazz);
        });
    putUnsafe("fuzion.java.f32_to_java_object", (interpreter, innerClazz) -> args ->
        {
          var f32 = args.get(1).f32Value();
          var jf = Float.valueOf(f32);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(jf, resultClazz);
        });
    putUnsafe("fuzion.java.f64_to_java_object", (interpreter, innerClazz) -> args ->
        {
          var d = args.get(1).f64Value();
          var jd = Double.valueOf(d);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(jd, resultClazz);
        });
    putUnsafe("fuzion.java.bool_to_java_object", (interpreter, innerClazz) -> args ->
        {
          var b = args.get(1).boolValue();
          var jb = Boolean.valueOf(b);
          Clazz resultClazz = innerClazz.resultClazz();
          return JavaInterface.javaObjectToInstance(jb, resultClazz);
        });
    put("fuzion.sys.internal_array_init.alloc", (interpreter, innerClazz) -> args ->
        {
          var at = interpreter._fuir.clazzOuterClazz(innerClazz._idInFUIR); // array type
          var et = interpreter._fuir.clazzActualGeneric(at, 0); // element type
          return ArrayData.alloc(/* size */ args.get(1).i32Value(),
                                 /* type */ interpreter._fuir.clazzForInterpreter(et)._type);
        });
    put("fuzion.sys.internal_array.get", (interpreter, innerClazz) -> args ->
        {
          var at = interpreter._fuir.clazzOuterClazz(innerClazz._idInFUIR); // array type
          var et = interpreter._fuir.clazzActualGeneric(at, 0); // element type
          return ((ArrayData)args.get(1)).get(
                                   /* index */ args.get(2).i32Value(),
                                   /* type  */ interpreter._fuir.clazzForInterpreter(et)._type);
        });
    put("fuzion.sys.internal_array.setel", (interpreter, innerClazz) -> args ->
        {
          var at = interpreter._fuir.clazzOuterClazz(innerClazz._idInFUIR); // array type
          var et = interpreter._fuir.clazzActualGeneric(at, 0); // element type
          ((ArrayData)args.get(1)).set(
                              /* index */ args.get(2).i32Value(),
                              /* value */ args.get(3),
                              /* type  */ interpreter._fuir.clazzForInterpreter(et)._type);
          return Value.EMPTY_VALUE;
        });
    put("fuzion.sys.internal_array.freeze", (interpreter, innerClazz) -> args ->
        {
          return Value.EMPTY_VALUE;
        });
    put("fuzion.sys.internal_array.ensure_not_frozen", (interpreter, innerClazz) -> args ->
        {
          return Value.EMPTY_VALUE;
        });
    put("fuzion.sys.env_vars.has0", (interpreter, innerClazz) -> args -> new boolValue(System.getenv(utf8ByteArrayDataToString(args.get(1))) != null));
    put("fuzion.sys.env_vars.get0", (interpreter, innerClazz) -> args -> Interpreter.value(System.getenv(utf8ByteArrayDataToString(args.get(1)))));
    // setting env variable not supported in java
    put("fuzion.sys.env_vars.set0"  , (interpreter, innerClazz) -> args -> new boolValue(false));
    // unsetting env variable not supported in java
    put("fuzion.sys.env_vars.unset0", (interpreter, innerClazz) -> args -> new boolValue(false));
    put("fuzion.sys.misc.unique_id",(interpreter, innerClazz) -> args -> new u64Value(_last_unique_id_.incrementAndGet()));
    put("fuzion.sys.thread.spawn0", (interpreter, innerClazz) -> args ->
        {
          var call = Types.resolved.f_Function_call;
          var oc = innerClazz.argumentFields()[0].resultClazz();
          var ic = oc.lookup(call);
          var t = new Thread(() -> interpreter.callOnInstance(ic._idInFUIR, new Instance(ic), args.get(1), new List<>(), false));
          t.setDaemon(true);
          t.start();
          return new i64Value(_startedThreads_.add(t));
        });
    put("fuzion.sys.thread.join0", (interpreter, innerClazz) -> args ->
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


    putUnsafe("fuzion.sys.net.bind0"    , (interpreter, innerClazz) -> args -> {
      var family = args.get(1).i32Value();
      var socketType = args.get(2).i32Value();
      var protocol = args.get(3).i32Value();
      var host = utf8ByteArrayDataToString(args.get(4));
      var port = utf8ByteArrayDataToString(args.get(5));
      var result = (long[])args.get(6).arrayData()._array;
      if (family != 2 && family != 10)
        {
          throw new Error("NYI");
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
              default -> throw new Error("NYI");
            };
        }
      catch(BindException e)
        {
          result[0] = SystemErrNo.EADDRINUSE.errno;
          return new i32Value(-1);
        }
      catch(IOException e)
        {
          result[0] = -1;
          return new i32Value(-1);
        }
    });

    putUnsafe("fuzion.sys.net.listen"  , (interpreter, innerClazz) -> args -> {
      return new i32Value(0);
    });

    putUnsafe("fuzion.sys.net.accept"  , (interpreter, innerClazz) -> args -> {
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
          throw new Error("NYI");
        }
      catch(IOException e)
        {
          return new boolValue(false);
        }
    });

    putUnsafe("fuzion.sys.net.connect0" , (interpreter, innerClazz) -> args -> {
      var family = args.get(1).i32Value();
      var socketType = args.get(2).i32Value();
      var protocol = args.get(3).i32Value();
      var host = utf8ByteArrayDataToString(args.get(4));
      var port = utf8ByteArrayDataToString(args.get(5));
      var result = (long[])args.get(6).arrayData()._array;
      if (family != 2 && family != 10)
        {
          throw new Error("NYI");
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
              default -> throw new Error("NYI");
            };
        }
      catch(IOException e)
        {
          result[0] = SystemErrNo.ECONNREFUSED.errno;
          return new i32Value(-1);
        }
    });

    putUnsafe("fuzion.sys.net.get_peer_address", (interpreter, innerClazz) -> args -> {
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
      catch (IOException e)
        {
          return new i32Value(-1);
        }
    });

    putUnsafe("fuzion.sys.net.get_peer_port", (interpreter, innerClazz) -> args -> {
      try
        {
          if (_openStreams_.get(args.get(1).i64Value()) instanceof SocketChannel sockfd)
            {
              return new u16Value(((InetSocketAddress)sockfd.getRemoteAddress()).getPort());
            }
          return new u16Value(0);
        }
      catch (IOException e)
        {
          return new u16Value(0);
        }
    });

    putUnsafe("fuzion.sys.net.read" , (interpreter, innerClazz) -> args -> {
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
              throw new Error("NYI");
            }
          ((long[])args.get(4).arrayData()._array)[0] = bytesRead;
          return new boolValue(bytesRead != -1);
        }
      catch(IOException e) //SocketTimeoutException and others
        {
          // unspecified error
          ((long[])args.get(4).arrayData()._array)[0] = -1;
          return new boolValue(false);
        }
    });

    putUnsafe("fuzion.sys.net.write" , (interpreter, innerClazz) -> args -> {
      try
        {
          var fileContent = (byte[])args.get(2).arrayData()._array;
          var sc = (ByteChannel)_openStreams_.get(args.get(1).i64Value());
          sc.write(ByteBuffer.wrap(fileContent));
          return new i32Value(0);
        }
      catch(IOException e)
        {
          return new i32Value(-1);
        }
    });

    putUnsafe("fuzion.sys.net.close0" , (interpreter, innerClazz) -> args -> {
      long fd = args.get(1).i64Value();
      return _openStreams_.remove(fd)
        ? new i32Value(0)
        : new i32Value(-1);
    });

    putUnsafe("fuzion.sys.net.set_blocking0" , (interpreter, innerClazz) -> args -> {
      var asc = (AbstractSelectableChannel)_openStreams_.get(args.get(1).i64Value());
      var blocking = args.get(2).i32Value();
      try
        {
          asc.configureBlocking(blocking == 1);
          return new i32Value(0);
        }
      catch(IOException e)
        {
          return new i32Value(-1);
        }
    });

    put("safety"                , (interpreter, innerClazz) -> args -> new boolValue(interpreter._options_.fuzionSafety()));
    put("debug"                 , (interpreter, innerClazz) -> args -> new boolValue(interpreter._options_.fuzionDebug()));
    put("debug_level"           , (interpreter, innerClazz) -> args -> new i32Value (interpreter._options_.fuzionDebugLevel()));
    put("i8.as_i32"             , (interpreter, innerClazz) -> args -> new i32Value (              (                           args.get(0).i8Value() )));
    put("i8.cast_to_u8"         , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & (                           args.get(0).i8Value() )));
    put("i8.prefix -°"          , (interpreter, innerClazz) -> args -> new i8Value  ((int) (byte)  (                       -   args.get(0).i8Value() )));
    put("i8.infix +°"           , (interpreter, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  +   args.get(1).i8Value() )));
    put("i8.infix -°"           , (interpreter, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  -   args.get(1).i8Value() )));
    put("i8.infix *°"           , (interpreter, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  *   args.get(1).i8Value() )));
    put("i8.div"                , (interpreter, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  /   args.get(1).i8Value() )));
    put("i8.mod"                , (interpreter, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  %   args.get(1).i8Value() )));
    put("i8.infix &"            , (interpreter, innerClazz) -> args -> new i8Value  (              (args.get(0).i8Value()  &   args.get(1).i8Value() )));
    put("i8.infix |"            , (interpreter, innerClazz) -> args -> new i8Value  (              (args.get(0).i8Value()  |   args.get(1).i8Value() )));
    put("i8.infix ^"            , (interpreter, innerClazz) -> args -> new i8Value  (              (args.get(0).i8Value()  ^   args.get(1).i8Value() )));
    put("i8.infix >>"           , (interpreter, innerClazz) -> args -> new i8Value  (              (args.get(0).i8Value()  >>  args.get(1).i8Value() )));
    put("i8.infix <<"           , (interpreter, innerClazz) -> args -> new i8Value  ((int) (byte)  (args.get(0).i8Value()  <<  args.get(1).i8Value() )));
    put("i8.type.equality"      , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).i8Value()  ==  args.get(2).i8Value() )));
    put("i8.type.lteq"          , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).i8Value()  <=  args.get(2).i8Value() )));
    put("i16.as_i32"            , (interpreter, innerClazz) -> args -> new i32Value (              (                           args.get(0).i16Value())));
    put("i16.cast_to_u16"       , (interpreter, innerClazz) -> args -> new u16Value (     0xffff & (                           args.get(0).i16Value())));
    put("i16.prefix -°"         , (interpreter, innerClazz) -> args -> new i16Value ((int) (short) (                       -   args.get(0).i16Value())));
    put("i16.infix +°"          , (interpreter, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() +   args.get(1).i16Value())));
    put("i16.infix -°"          , (interpreter, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() -   args.get(1).i16Value())));
    put("i16.infix *°"          , (interpreter, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() *   args.get(1).i16Value())));
    put("i16.div"               , (interpreter, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() /   args.get(1).i16Value())));
    put("i16.mod"               , (interpreter, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() %   args.get(1).i16Value())));
    put("i16.infix &"           , (interpreter, innerClazz) -> args -> new i16Value (              (args.get(0).i16Value() &   args.get(1).i16Value())));
    put("i16.infix |"           , (interpreter, innerClazz) -> args -> new i16Value (              (args.get(0).i16Value() |   args.get(1).i16Value())));
    put("i16.infix ^"           , (interpreter, innerClazz) -> args -> new i16Value (              (args.get(0).i16Value() ^   args.get(1).i16Value())));
    put("i16.infix >>"          , (interpreter, innerClazz) -> args -> new i16Value (              (args.get(0).i16Value() >>  args.get(1).i16Value())));
    put("i16.infix <<"          , (interpreter, innerClazz) -> args -> new i16Value ((int) (short) (args.get(0).i16Value() <<  args.get(1).i16Value())));
    put("i16.type.equality"     , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).i16Value() ==  args.get(2).i16Value())));
    put("i16.type.lteq"         , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).i16Value() <=  args.get(2).i16Value())));
    put("i32.as_i64"            , (interpreter, innerClazz) -> args -> new i64Value ((long)        (                           args.get(0).i32Value())));
    put("i32.cast_to_u32"       , (interpreter, innerClazz) -> args -> new u32Value (              (                           args.get(0).i32Value())));
    put("i32.as_f64"            , (interpreter, innerClazz) -> args -> new f64Value ((double)      (                           args.get(0).i32Value())));
    put("i32.prefix -°"         , (interpreter, innerClazz) -> args -> new i32Value (              (                       -   args.get(0).i32Value())));
    put("i32.infix +°"          , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() +   args.get(1).i32Value())));
    put("i32.infix -°"          , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() -   args.get(1).i32Value())));
    put("i32.infix *°"          , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() *   args.get(1).i32Value())));
    put("i32.div"               , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() /   args.get(1).i32Value())));
    put("i32.mod"               , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() %   args.get(1).i32Value())));
    put("i32.infix &"           , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() &   args.get(1).i32Value())));
    put("i32.infix |"           , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() |   args.get(1).i32Value())));
    put("i32.infix ^"           , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() ^   args.get(1).i32Value())));
    put("i32.infix >>"          , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() >>  args.get(1).i32Value())));
    put("i32.infix <<"          , (interpreter, innerClazz) -> args -> new i32Value (              (args.get(0).i32Value() <<  args.get(1).i32Value())));
    put("i32.type.equality"     , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).i32Value() ==  args.get(2).i32Value())));
    put("i32.type.lteq"         , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).i32Value() <=  args.get(2).i32Value())));
    put("i64.cast_to_u64"       , (interpreter, innerClazz) -> args -> new u64Value (              (                           args.get(0).i64Value())));
    put("i64.as_f64"            , (interpreter, innerClazz) -> args -> new f64Value ((double)      (                           args.get(0).i64Value())));
    put("i64.prefix -°"         , (interpreter, innerClazz) -> args -> new i64Value (              (                       -   args.get(0).i64Value())));
    put("i64.infix +°"          , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() +   args.get(1).i64Value())));
    put("i64.infix -°"          , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() -   args.get(1).i64Value())));
    put("i64.infix *°"          , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() *   args.get(1).i64Value())));
    put("i64.div"               , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() /   args.get(1).i64Value())));
    put("i64.mod"               , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() %   args.get(1).i64Value())));
    put("i64.infix &"           , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() &   args.get(1).i64Value())));
    put("i64.infix |"           , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() |   args.get(1).i64Value())));
    put("i64.infix ^"           , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() ^   args.get(1).i64Value())));
    put("i64.infix >>"          , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() >>  args.get(1).i64Value())));
    put("i64.infix <<"          , (interpreter, innerClazz) -> args -> new i64Value (              (args.get(0).i64Value() <<  args.get(1).i64Value())));
    put("i64.type.equality"     , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).i64Value() ==  args.get(2).i64Value())));
    put("i64.type.lteq"         , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).i64Value() <=  args.get(2).i64Value())));
    put("u8.as_i32"             , (interpreter, innerClazz) -> args -> new i32Value (              (                           args.get(0).u8Value() )));
    put("u8.cast_to_i8"         , (interpreter, innerClazz) -> args -> new i8Value  ((int) (byte)  (                           args.get(0).u8Value() )));
    put("u8.prefix -°"          , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & (                       -   args.get(0).u8Value() )));
    put("u8.infix +°"           , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & (args.get(0).u8Value()  +   args.get(1).u8Value() )));
    put("u8.infix -°"           , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & (args.get(0).u8Value()  -   args.get(1).u8Value() )));
    put("u8.infix *°"           , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & (args.get(0).u8Value()  *   args.get(1).u8Value() )));
    put("u8.div"                , (interpreter, innerClazz) -> args -> new u8Value  (Integer.divideUnsigned   (args.get(0).u8Value(), args.get(1).u8Value())));
    put("u8.mod"                , (interpreter, innerClazz) -> args -> new u8Value  (Integer.remainderUnsigned(args.get(0).u8Value(), args.get(1).u8Value())));
    put("u8.infix &"            , (interpreter, innerClazz) -> args -> new u8Value  (              (args.get(0).u8Value()  &   args.get(1).u8Value() )));
    put("u8.infix |"            , (interpreter, innerClazz) -> args -> new u8Value  (              (args.get(0).u8Value()  |   args.get(1).u8Value() )));
    put("u8.infix ^"            , (interpreter, innerClazz) -> args -> new u8Value  (              (args.get(0).u8Value()  ^   args.get(1).u8Value() )));
    put("u8.infix >>"           , (interpreter, innerClazz) -> args -> new u8Value  (              (args.get(0).u8Value()  >>> args.get(1).u8Value() )));
    put("u8.infix <<"           , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & (args.get(0).u8Value()  <<  args.get(1).u8Value() )));
    put("u8.type.equality"      , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).u8Value()  ==  args.get(2).u8Value() )));
    put("u8.type.lteq"          , (interpreter, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(1).u8Value(), args.get(2).u8Value()) <= 0));
    put("u16.as_i32"            , (interpreter, innerClazz) -> args -> new i32Value (              (                           args.get(0).u16Value())));
    put("u16.low8bits"          , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & (                           args.get(0).u16Value())));
    put("u16.cast_to_i16"       , (interpreter, innerClazz) -> args -> new i16Value ((short)       (                           args.get(0).u16Value())));
    put("u16.prefix -°"         , (interpreter, innerClazz) -> args -> new u16Value (     0xffff & (                       -   args.get(0).u16Value())));
    put("u16.infix +°"          , (interpreter, innerClazz) -> args -> new u16Value (     0xffff & (args.get(0).u16Value() +   args.get(1).u16Value())));
    put("u16.infix -°"          , (interpreter, innerClazz) -> args -> new u16Value (     0xffff & (args.get(0).u16Value() -   args.get(1).u16Value())));
    put("u16.infix *°"          , (interpreter, innerClazz) -> args -> new u16Value (     0xffff & (args.get(0).u16Value() *   args.get(1).u16Value())));
    put("u16.div"               , (interpreter, innerClazz) -> args -> new u16Value (Integer.divideUnsigned   (args.get(0).u16Value(), args.get(1).u16Value())));
    put("u16.mod"               , (interpreter, innerClazz) -> args -> new u16Value (Integer.remainderUnsigned(args.get(0).u16Value(), args.get(1).u16Value())));
    put("u16.infix &"           , (interpreter, innerClazz) -> args -> new u16Value (              (args.get(0).u16Value() &   args.get(1).u16Value())));
    put("u16.infix |"           , (interpreter, innerClazz) -> args -> new u16Value (              (args.get(0).u16Value() |   args.get(1).u16Value())));
    put("u16.infix ^"           , (interpreter, innerClazz) -> args -> new u16Value (              (args.get(0).u16Value() ^   args.get(1).u16Value())));
    put("u16.infix >>"          , (interpreter, innerClazz) -> args -> new u16Value (              (args.get(0).u16Value() >>> args.get(1).u16Value())));
    put("u16.infix <<"          , (interpreter, innerClazz) -> args -> new u16Value (     0xffff & (args.get(0).u16Value() <<  args.get(1).u16Value())));
    put("u16.type.equality"     , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).u16Value() ==  args.get(2).u16Value())));
    put("u16.type.lteq"         , (interpreter, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(1).u16Value(), args.get(2).u16Value()) <= 0));
    put("u32.as_i64"            , (interpreter, innerClazz) -> args -> new i64Value (Integer.toUnsignedLong(args.get(0).u32Value())));
    put("u32.low8bits"          , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & (                           args.get(0).u32Value())));
    put("u32.low16bits"         , (interpreter, innerClazz) -> args -> new u16Value (     0xffff & (                           args.get(0).u32Value())));
    put("u32.cast_to_i32"       , (interpreter, innerClazz) -> args -> new i32Value (              (                           args.get(0).u32Value())));
    put("u32.as_f64"            , (interpreter, innerClazz) -> args -> new f64Value ((double)      Integer.toUnsignedLong(     args.get(0).u32Value())));
    put("u32.cast_to_f32"       , (interpreter, innerClazz) -> args -> new f32Value (              Float.intBitsToFloat(       args.get(0).u32Value())));
    put("u32.prefix -°"         , (interpreter, innerClazz) -> args -> new u32Value (              (                       -   args.get(0).u32Value())));
    put("u32.infix +°"          , (interpreter, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() +   args.get(1).u32Value())));
    put("u32.infix -°"          , (interpreter, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() -   args.get(1).u32Value())));
    put("u32.infix *°"          , (interpreter, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() *   args.get(1).u32Value())));
    put("u32.div"               , (interpreter, innerClazz) -> args -> new u32Value (Integer.divideUnsigned   (args.get(0).u32Value(), args.get(1).u32Value())));
    put("u32.mod"               , (interpreter, innerClazz) -> args -> new u32Value (Integer.remainderUnsigned(args.get(0).u32Value(), args.get(1).u32Value())));
    put("u32.infix &"           , (interpreter, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() &   args.get(1).u32Value())));
    put("u32.infix |"           , (interpreter, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() |   args.get(1).u32Value())));
    put("u32.infix ^"           , (interpreter, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() ^   args.get(1).u32Value())));
    put("u32.infix >>"          , (interpreter, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() >>> args.get(1).u32Value())));
    put("u32.infix <<"          , (interpreter, innerClazz) -> args -> new u32Value (              (args.get(0).u32Value() <<  args.get(1).u32Value())));
    put("u32.type.equality"     , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).u32Value() ==  args.get(2).u32Value())));
    put("u32.type.lteq"         , (interpreter, innerClazz) -> args -> new boolValue(Integer.compareUnsigned(args.get(1).u32Value(), args.get(2).u32Value()) <= 0));
    put("u64.low8bits"          , (interpreter, innerClazz) -> args -> new u8Value  (       0xff & ((int)                      args.get(0).u64Value())));
    put("u64.low16bits"         , (interpreter, innerClazz) -> args -> new u16Value (     0xffff & ((int)                      args.get(0).u64Value())));
    put("u64.low32bits"         , (interpreter, innerClazz) -> args -> new u32Value ((int)         (                           args.get(0).u64Value())));
    put("u64.cast_to_i64"       , (interpreter, innerClazz) -> args -> new i64Value (              (                           args.get(0).u64Value())));
    put("u64.as_f64"            , (interpreter, innerClazz) -> args -> new f64Value (Double.parseDouble(Long.toUnsignedString(args.get(0).u64Value()))));
    put("u64.cast_to_f64"       , (interpreter, innerClazz) -> args -> new f64Value (              Double.longBitsToDouble(    args.get(0).u64Value())));
    put("u64.prefix -°"         , (interpreter, innerClazz) -> args -> new u64Value (              (                       -   args.get(0).u64Value())));
    put("u64.infix +°"          , (interpreter, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() +   args.get(1).u64Value())));
    put("u64.infix -°"          , (interpreter, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() -   args.get(1).u64Value())));
    put("u64.infix *°"          , (interpreter, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() *   args.get(1).u64Value())));
    put("u64.div"               , (interpreter, innerClazz) -> args -> new u64Value (Long.divideUnsigned   (args.get(0).u64Value(), args.get(1).u64Value())));
    put("u64.mod"               , (interpreter, innerClazz) -> args -> new u64Value (Long.remainderUnsigned(args.get(0).u64Value(), args.get(1).u64Value())));
    put("u64.infix &"           , (interpreter, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() &   args.get(1).u64Value())));
    put("u64.infix |"           , (interpreter, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() |   args.get(1).u64Value())));
    put("u64.infix ^"           , (interpreter, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() ^   args.get(1).u64Value())));
    put("u64.infix >>"          , (interpreter, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() >>> args.get(1).u64Value())));
    put("u64.infix <<"          , (interpreter, innerClazz) -> args -> new u64Value (              (args.get(0).u64Value() <<  args.get(1).u64Value())));
    put("u64.type.equality"     , (interpreter, innerClazz) -> args -> new boolValue(              (args.get(1).u64Value() ==  args.get(2).u64Value())));
    put("u64.type.lteq"         , (interpreter, innerClazz) -> args -> new boolValue(Long.compareUnsigned(args.get(1).u64Value(), args.get(2).u64Value()) <= 0));
    put("f32.prefix -"          , (interpreter, innerClazz) -> args -> new f32Value (                (                       -  args.get(0).f32Value())));
    put("f32.infix +"           , (interpreter, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() +  args.get(1).f32Value())));
    put("f32.infix -"           , (interpreter, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() -  args.get(1).f32Value())));
    put("f32.infix *"           , (interpreter, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() *  args.get(1).f32Value())));
    put("f32.infix /"           , (interpreter, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() /  args.get(1).f32Value())));
    put("f32.infix %"           , (interpreter, innerClazz) -> args -> new f32Value (                (args.get(0).f32Value() %  args.get(1).f32Value())));
    put("f32.infix **"          , (interpreter, innerClazz) -> args -> new f32Value ((float) Math.pow(args.get(0).f32Value(),   args.get(1).f32Value())));
    put("f32.infix ="           , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f32Value() == args.get(1).f32Value())));
    put("f32.infix <="          , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f32Value() <= args.get(1).f32Value())));
    put("f32.infix >="          , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f32Value() >= args.get(1).f32Value())));
    put("f32.infix <"           , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f32Value() <  args.get(1).f32Value())));
    put("f32.infix >"           , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f32Value() >  args.get(1).f32Value())));
    put("f32.as_f64"            , (interpreter, innerClazz) -> args -> new f64Value((double)                                    args.get(0).f32Value() ));
    put("f32.cast_to_u32"       , (interpreter, innerClazz) -> args -> new u32Value (    Float.floatToIntBits(                  args.get(0).f32Value())));
    put("f64.prefix -"          , (interpreter, innerClazz) -> args -> new f64Value (                (                       -  args.get(0).f64Value())));
    put("f64.infix +"           , (interpreter, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() +  args.get(1).f64Value())));
    put("f64.infix -"           , (interpreter, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() -  args.get(1).f64Value())));
    put("f64.infix *"           , (interpreter, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() *  args.get(1).f64Value())));
    put("f64.infix /"           , (interpreter, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() /  args.get(1).f64Value())));
    put("f64.infix %"           , (interpreter, innerClazz) -> args -> new f64Value (                (args.get(0).f64Value() %  args.get(1).f64Value())));
    put("f64.infix **"          , (interpreter, innerClazz) -> args -> new f64Value (        Math.pow(args.get(0).f64Value(),   args.get(1).f64Value())));
    put("f64.infix ="           , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f64Value() == args.get(1).f64Value())));
    put("f64.infix <="          , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f64Value() <= args.get(1).f64Value())));
    put("f64.infix >="          , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f64Value() >= args.get(1).f64Value())));
    put("f64.infix <"           , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f64Value() <  args.get(1).f64Value())));
    put("f64.infix >"           , (interpreter, innerClazz) -> args -> new boolValue(                (args.get(0).f64Value() >  args.get(1).f64Value())));
    put("f64.as_i64_lax"        , (interpreter, innerClazz) -> args -> new i64Value((long)                                      args.get(0).f64Value() ));
    put("f64.as_f32"            , (interpreter, innerClazz) -> args -> new f32Value((float)                                     args.get(0).f64Value() ));
    put("f64.cast_to_u64"       , (interpreter, innerClazz) -> args -> new u64Value (    Double.doubleToLongBits(               args.get(0).f64Value())));
    put("f32.type.is_NaN"       , (interpreter, innerClazz) -> args -> new boolValue(                               Float.isNaN(args.get(1).f32Value())));
    put("f64.type.is_NaN"       , (interpreter, innerClazz) -> args -> new boolValue(                              Double.isNaN(args.get(1).f64Value())));
    put("f32.type.acos"         , (interpreter, innerClazz) -> args -> new f32Value ((float)           Math.acos(               args.get(1).f32Value())));
    put("f32.type.asin"         , (interpreter, innerClazz) -> args -> new f32Value ((float)           Math.asin(               args.get(1).f32Value())));
    put("f32.type.atan"         , (interpreter, innerClazz) -> args -> new f32Value ((float)           Math.atan(               args.get(1).f32Value())));
    put("f32.type.cos"          , (interpreter, innerClazz) -> args -> new f32Value ((float)           Math.cos(                args.get(1).f32Value())));
    put("f32.type.cosh"         , (interpreter, innerClazz) -> args -> new f32Value ((float)           Math.cosh(               args.get(1).f32Value())));
    put("f32.type.epsilon"      , (interpreter, innerClazz) -> args -> new f32Value (                  Math.ulp(                (float)1)));
    put("f32.type.exp"          , (interpreter, innerClazz) -> args -> new f32Value ((float)           Math.exp(                args.get(1).f32Value())));
    put("f32.type.log"          , (interpreter, innerClazz) -> args -> new f32Value ((float)           Math.log(                args.get(1).f32Value())));
    put("f32.type.max"          , (interpreter, innerClazz) -> args -> new f32Value (                                           Float.MAX_VALUE));
    put("f32.type.max_exp"      , (interpreter, innerClazz) -> args -> new i32Value (                                           Float.MAX_EXPONENT));
    put("f32.type.min_positive" , (interpreter, innerClazz) -> args -> new f32Value (                                           Float.MIN_NORMAL));
    put("f32.type.min_exp"      , (interpreter, innerClazz) -> args -> new i32Value (                                           Float.MIN_EXPONENT));
    put("f32.type.sin"          , (interpreter, innerClazz) -> args -> new f32Value ((float)          Math.sin(                 args.get(1).f32Value())));
    put("f32.type.sinh"         , (interpreter, innerClazz) -> args -> new f32Value ((float)          Math.sinh(                args.get(1).f32Value())));
    put("f32.type.square_root"  , (interpreter, innerClazz) -> args -> new f32Value ((float)          Math.sqrt(        (double)args.get(1).f32Value())));
    put("f32.type.tan"          , (interpreter, innerClazz) -> args -> new f32Value ((float)          Math.tan(                 args.get(1).f32Value())));
    put("f32.type.tanh"         , (interpreter, innerClazz) -> args -> new f32Value ((float)          Math.tanh(                args.get(1).f32Value())));
    put("f64.type.acos"         , (interpreter, innerClazz) -> args -> new f64Value (                 Math.acos(                args.get(1).f64Value())));
    put("f64.type.asin"         , (interpreter, innerClazz) -> args -> new f64Value (                 Math.asin(                args.get(1).f64Value())));
    put("f64.type.atan"         , (interpreter, innerClazz) -> args -> new f64Value (                 Math.atan(                args.get(1).f64Value())));
    put("f64.type.cos"          , (interpreter, innerClazz) -> args -> new f64Value (                 Math.cos(                 args.get(1).f64Value())));
    put("f64.type.cosh"         , (interpreter, innerClazz) -> args -> new f64Value (                 Math.cosh(                args.get(1).f64Value())));
    put("f64.type.epsilon"      , (interpreter, innerClazz) -> args -> new f64Value (                 Math.ulp(                 (double)1)));
    put("f64.type.exp"          , (interpreter, innerClazz) -> args -> new f64Value (                 Math.exp(                 args.get(1).f64Value())));
    put("f64.type.log"          , (interpreter, innerClazz) -> args -> new f64Value (                 Math.log(                 args.get(1).f64Value())));
    put("f64.type.max"          , (interpreter, innerClazz) -> args -> new f64Value (                                               Double.MAX_VALUE));
    put("f64.type.max_exp"      , (interpreter, innerClazz) -> args -> new i32Value (                                               Double.MAX_EXPONENT));
    put("f64.type.min_positive" , (interpreter, innerClazz) -> args -> new f64Value (                                               Double.MIN_NORMAL));
    put("f64.type.min_exp"      , (interpreter, innerClazz) -> args -> new i32Value (                                               Double.MIN_EXPONENT));
    put("f64.type.sin"          , (interpreter, innerClazz) -> args -> new f64Value (                 Math.sin(                 args.get(1).f64Value())));
    put("f64.type.sinh"         , (interpreter, innerClazz) -> args -> new f64Value (                 Math.sinh(                args.get(1).f64Value())));
    put("f64.type.square_root"  , (interpreter, innerClazz) -> args -> new f64Value (                 Math.sqrt(                args.get(1).f64Value())));
    put("f64.type.tan"          , (interpreter, innerClazz) -> args -> new f64Value (                 Math.tan(                 args.get(1).f64Value())));
    put("f64.type.tanh"         , (interpreter, innerClazz) -> args -> new f64Value (                 Math.tanh(                args.get(1).f64Value())));
    put("Any.as_string"         , (interpreter, innerClazz) -> args -> Interpreter.value("instance[" + innerClazz._outer.toString() + "]"));
    put("fuzion.std.nano_time"  , (interpreter, innerClazz) -> args -> new u64Value (System.nanoTime()));
    put("fuzion.std.nano_sleep" , (interpreter, innerClazz) -> args ->
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
          return new Instance(Clazzes.c_unit.get());
        });
    put("fuzion.std.date_time", (interpreter, innerClazz) -> args ->
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
        return new Instance(Clazzes.c_unit.get());
      });
    put("effect.replace"  ,
        "effect.default"  ,
        "effect.abortable",
        "effect.abort0"   , (interpreter, innerClazz) -> effect(interpreter, innerClazz));
    put("effect.type.is_installed", (interpreter, innerClazz) -> args ->
        {
          var cl = innerClazz.actualGenerics()[0];
          return new boolValue(FuzionThread.current()._effects.get(cl) != null /* NOTE not containsKey since cl may map to null! */ );
        });

    putUnsafe("fuzion.sys.process.create"  , (interpreter, innerClazz) -> args -> {
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
      catch (IOException e)
        {
          return new i32Value(-1);
        }
    });

    put("fuzion.sys.process.wait"    , (interpreter, innerClazz) -> args -> {
      var desc = args.get(1).i64Value();
      var p = _openProcesses_.get(desc);
      try
        {
          var result = p.waitFor();
          _openProcesses_.remove(desc);
          return new i32Value(result);
        }
      catch(InterruptedException e)
        {
          return new i32Value(-1);
        }
    });

    put("fuzion.sys.pipe.read"       , (interpreter, innerClazz) -> args -> {
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
      catch (IOException e)
        {
          return new i32Value(-1);
        }
    });

    put("fuzion.sys.pipe.write"      , (interpreter, innerClazz) -> args -> {
      var desc = args.get(1).i64Value();
      var buff = (byte[])args.get(2).arrayData()._array;
      var os = (OutputStream) _openStreams_.get(desc);
      try
        {
          os.write(buff);
          return new i32Value(buff.length);
        }
      catch (IOException e)
        {
          return new i32Value(-1);
        }
    });

    put("fuzion.sys.pipe.close"      , (interpreter, innerClazz) -> args -> {
      var desc = args.get(1).i64Value();
      return _openStreams_.remove(desc)
        ? new i32Value(0)
        : new i32Value(-1);
    });
  }


  static class Abort extends Error
  {
    Clazz _effect;
    Abort(Clazz effect)
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
  static Callable effect(Interpreter interpreter, Clazz innerClazz)
  {
    return (args) ->
      {
        var m = args.get(0);
        var cl = innerClazz._outer;
        String in = innerClazz.feature().qualifiedName();   // == _fuir.clazzOriginalName(cl);
        switch (in)
          {
          case "effect.replace": check(FuzionThread.current()._effects.get(cl) != null, m != Value.EMPTY_VALUE); FuzionThread.current()._effects.put(cl, m   );   break;
          case "effect.default": if (FuzionThread.current()._effects.get(cl) == null) { check(m != Value.EMPTY_VALUE); FuzionThread.current()._effects.put(cl, m   ); } break;
          case "effect.abortable" :
            {
              var prev = FuzionThread.current()._effects.get(cl);
              FuzionThread.current()._effects.put(cl, m);
              var call = Types.resolved.f_Function_call;
              var oc = innerClazz.actualGenerics()[0]; //innerClazz.argumentFields()[0].resultClazz();
              var ic = oc.lookup(call);
              try {
                var ignore = interpreter.callOnInstance(ic._idInFUIR, new Instance(ic), args.get(1), new List<>(), false);
                return new boolValue(true);
              } catch (Abort a) {
                if (a._effect == cl)
                  {
                    return new boolValue(false);
                  }
                else
                  {
                    throw a;
                  }
              } finally {
                FuzionThread.current()._effects.put(cl, prev);
              }
            }
          case "effect.abort0": throw new Abort(cl);
          default: throw new Error("unexpected effect intrinsic '"+innerClazz+"'");
          }
        return Value.EMPTY_VALUE;
      };
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
