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
 * Source of class JVM
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm;

import dev.flang.fuir.FUIR;

import dev.flang.fuir.analysis.AbstractInterpreter;
import dev.flang.fuir.analysis.dfa.DFA;
import dev.flang.fuir.analysis.TailCall;

import dev.flang.be.jvm.classfile.ClassFileConstants;
import dev.flang.be.jvm.classfile.Expr;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.Pair;

import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.stream.Stream;


/**
 * JVM provides a JVM bytecode backend converting FUIR data into classfile code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class JVM extends ANY implements ClassFileConstants
{

  /*
--asciidoc--

JVM backend design:
-------------------

Goals:
~~~~~~

The JVM backend is a backend for Fuzion that create Java bytecode to run on a
JVM.  There will be two major modes of operation:

* On-the-fly generation of bytecode that is added to a running JVM and
  generation (Using System.defineClass an similar APIs), and

* Static generation of Java .jar or .jmod files that contain the code for a
  Fuzion application.

The goal is to avoid overhead as much as possible and map Fuzion code to Java
instances as much as possible, to even create Java code that is better than
manually written Java code (e.g., by avoiding wrapping value types into heap
instances and hence avoiding allocation and increasing performance).

Object model:
~~~~~~~~~~~~~

The object model defines how Fuzion instances are represented in the target
code.

Primitive Features
^^^^^^^^^^^^^^^^^^

Where possible, Java primitive types (`boolean`, `byte`, `char`, `short`, `int`,
`long`, `float`, `double`) are used to hold Fuzion instances.  Note that some
Java primitives (`boolean`, `byte`, `char`, `short`) cannot be used to hold
values on the Java stack, so these will be `int` when on the stack.

[options="header",cols="1,1"]
|====
   |Fuzion feature | Java type

   |bool| boolean / int (on Java stack)
   |u8 | byte / int (on Java stack)
   |i8 | byte / int (on Java stack)
   |u16 | char / int (on Java stack)
   |i16 | short / int (on Java stack)
   |u32 | int
   |i32 | int
   |u64 | long
   |i64 | long
   |f32 | float
   |f64 | double
|====

Value Types
^^^^^^^^^^^

Value types will be split up into several primitive values, e.g., a Fuzion type

    point(x,y f64, col u32).


will be represented by three Java values

    double point_x
    double point_y
    int point_col

Whenever one value type is passed, as an argument or assigned to a field, this
will be performed as several assignments in the Java code.

Reference Features
^^^^^^^^^^^^^^^^^^

A reference feature corresponds to a Java class whose fields represent the
corresponding value feature.  There should be one Java class generated for every
Fuzion clazz, that class should inherit from an abstract class `FuzionInstance`
that may define methods required by the JVM backend.

There should be no need for additional type information since different
reference features will become different Java classes.

Boxed value types:
^^^^^^^^^^^^^^^^^^

Boxed value types could be represented just like reference types.

Choice Features
^^^^^^^^^^^^^^^

A choice feature in general would be a value type consisting of a `tag` of type
`int` in combination with all the possible variants of the choice type.

Several variants of reference type could be collapsed into a single Java field.

A choice consisting of only disjoint reference types and disjoint unit type
values could be collapsed into a single reference type with special values used
for the unit types, including the special value `null`.  Note that types like
`choice Any String`, which could be the result of type parameters in `choice T
U`, do not consist of disjoint reference types.


Arrays
^^^^^^

Arrays should be normal value types. `fuzion.sys.internal_array T`, however,
needs special treatment:

[options="header",cols="1,1"]
|====
   |Fuzion feature | Java type

   |`fuzion.sys.internal_array bool` | `boolean[]`
   |`fuzion.sys.internal_array u8`   | `byte[]`
   |`fuzion.sys.internal_array i8`   | `byte[]`
   |`fuzion.sys.internal_array u16`  | `char[]`
   |`fuzion.sys.internal_array i16`  | `short[]`
   |`fuzion.sys.internal_array u32`  | `int[]`
   |`fuzion.sys.internal_array i32`  | `int[]`
   |`fuzion.sys.internal_array u64`  | `long[]`
   |`fuzion.sys.internal_array i64`  | `long[]`
   |`fuzion.sys.internal_array f32`  | `float[]`
   |`fuzion.sys.internal_array f64`  | `double[]`
   |`fuzion.sys.internal_array T` where `T` is ref type | `FuzionInstance[]`
   |`fuzion.sys.internal_array T` where `T` is value type | One Java array for each primitive or ref part of `T`. Several such arrays may be combined if element type is the same, e.g. `float[32]` instead of `float[16]` and `float[16]`, where the `i`-th element will then be accessed at indices `i*2` and `i*2+1`.
|====

Mutable value Types
^^^^^^^^^^^^^^^^^^^

Value types that are immutable can be copied in a call.  Mutable ones, however,
must not be copied and must be made accessible from outside.

example:

    f is
      x := mut 0
      inc is x <- x.get + 1
      show is say x.get

    g is
      cnt := f
      cnt.inc     # g.cnt must be heap allocated to be accessible be inc.

Value types or primitive types that are passed to a call by reference since
their fields are mutable will need special handling.  Since Java does not permit
accesses to local variables from outside of a method, these will need to be
stored in a heap allocated object, even if their outer feature is not a ref
feature.

There are basically two approaches to pass such value fields

. passing an accessor-instance that contains a ref to the enclosing instance
  together with getter- and setter- methods to access the fields:

      abstract class Access_F
      {
        abstract int get_x_mutable_value(FuzionInstance cur);
        abstract void set_x_mutable_value(FuzionInstance cur, int v);
      }

      void f_inc(FuzionInstance cur, Access_f acc)
      {
        // using inlining here to make this example work:
        acc.set_mutable_value(cur, acc.get_mutable_value(acc) + 1);
      }

      class G extends FuzionInstance
      {
        int x_mutable_value;   // inlined field mutate.new.mutable_value
      }

      static Access_F access_G_x = new Access_f() {
                 void get_mutable_value(FuzionInstance cur)
                 {
                   return ((G) cur).x_mutable_value;
                 }
                 void set_mutable_value(FuzionInstance cur, int v)
                 {
                   ((G) cur).x_mutable_value = v;
                 }
               });

      static void g()
      {
         var cur = new G();
         cur.x_mutable_value = f();
         f_inc(cur, access_G_x);
      }
+
This might turn out difficult since if we would not inline the calls made in
`f.inc` we need to wrap the access wrapper into another access wrapper for
calling `mutate.new.get` and `mutate.new.infix <-`.

. Since mutating value fields should happen only for the target of a call, we
  could specialize the call for the location of the value in the surrounding
  instance

      class G extends FuzionInstance
      {
        int x_mutable_value;   // inlined field mutate.new.mutable_value
      }

      static void g()
      {
         var cur = new G();
         f_inc_for_G_cnt(cur);
      }

      static void f_inc_for_G_cnt(G cur)
      {
         mutate_new_i32_set_for_G_cnt_x(cur,
                                        mutate_new_i32_get_for_G_cnt_x(cur) + 1);
      }

      static void mutate_new_i32_get_for_G_cnt_x(G cur)
      {
        cur.x_mutable_value;
      }

      static void mutate_new_i32_set_for_G_cnt_x(G cur, int v)
      {
        cur.x_mutable_value = v;
      }
+
This seems simpler.

Function results
^^^^^^^^^^^^^^^^

Function result types that are ref types or primitive types in Java can be
returned like Java values.

To return other value types, we have to either

* wrap these into a heap allocated instance

* pass an accessor-instance that contains a ref to the enclosing instance
  that should receive the result

* use thread local vars to return the result

* specialize the call for where the result is stored


Calls:
~~~~~~

Statically Bound Calls:
^^^^^^^^^^^^^^^^^^^^^^^

Calls to features whose target is a value type or whose target is a single
Fuzion type do not need dynamic lookup.  The called features should be static
features.

Dynamically Bound Calls:
^^^^^^^^^^^^^^^^^^^^^^^^

Dynamically bounds calls are performed on a reference target, which means the
target instance has a unique corresponding Java class. This means we could
leverage the Java class to perform this call, either by

* adding an `id()` method to `FuzionInstance` that is redefined for each ref
  type to return the corresponding clazz id. A lookupswitch could then be used
  to perform the call (in O(log n)!)

* adding interface classes for fuzion features that contained interface methods
  for inner features that are implemented by all heir features implementing
  these.  Then, an `invokeinterface` could be used, which uses a cached search
  (also in O(log n)), but this is currently being improved
  (https://bugs.openjdk.org/browse/JDK-8180450[JEP-8180450]). A two-layered
  lookup could reduce the call overhead to O(1)
  https://www.usenix.org/legacy/publications/library/proceedings/jvm01/full_papers/siebert/siebert.pdf[JVM01/siebert.pdf].

Assignments:
~~~~~~~~~~~~

In Fuzion, an assignment may require dynamic binding if the assigned field is in
an outer instance that is a reference type.  So we must handle assignment like
dynamically bound calls in this case.

Java interface:
~~~~~~~~~~~~~~~

The JVM backend should be aware of the Java interfaces create by `fzjava` and
perform inline calls to the corresponding Java code.  Use of reflection and
should be avoided as much as possible.


--asciidoc--

   */


  /*----------------------------  constants  ----------------------------*/


  /**
   * property-controlled flag to enable trace debug output.
   *
   * To enable tracing, use fz with
   *
   *   FUZION_JAVA_OPTIONS=-Ddev.flang.be.jvm.JVM.TRACE=true
   */
  static final boolean TRACE =
    System.getProperty("dev.flang.be.jvm.JVM.TRACE",
                       "false").equals("true");


  /**
   * property-controlled flag to enable trace debug output when returning from a
   * feature.
   *
   * To enable tracing returns, use fz with
   *
   *   FUZION_JAVA_OPTIONS=-Ddev.flang.be.jvm.JVM.TRACE_RETURN=true
   */
  static final boolean TRACE_RETURN =
    System.getProperty("dev.flang.be.jvm.JVM.TRACE_RETURN",
                       "false").equals("true");


  /**
   * property-controlled flag to enable comments created in bytecode to improve
   * readability when disassembling (using javap or similar).  Comments are put
   * in the form of String constants that are loaed with O_ldc follwed by O_pop.
   *
   * To enable comments, use fz with
   *
   *   FUZION_JAVA_OPTIONS=-Ddev.flang.be.jvm.JVM.CODE_COMMENTS=true
   */
  static final boolean CODE_COMMENTS =
    System.getProperty("dev.flang.be.jvm.JVM.CODE_COMMENTS",
                       "false").equals("true");
  static
  {
    Expr.ENABLE_COMMENTS = CODE_COMMENTS;
  }


  static Path PATH_FOR_CLASSES = Path.of("fuzion_generated_clazzes");


  /**
   * JVM code generation phases
   */
  private enum CompilePhase
  {
    // create classes
    CLASSES
    {
      void compile(JVM jvm, int cl)
      {
        jvm._types.createClassFile(cl);
      }
    },

    // create fields
    FIELDS
    {
      void compile(JVM jvm, int cl)
      {
        if (jvm._fuir.clazzKind(cl) == FUIR.FeatureKind.Field && jvm.fieldExists(cl))
          {
            var o = jvm._fuir.clazzOuterClazz(cl);
            var cf = jvm._types.classFile(o);
            if (cf != null)
              {
                cf.field(ACC_PUBLIC,
                         jvm._names.field(cl),
                         jvm._types.resultType(jvm._fuir.clazzResultClazz(cl)).descriptor(),
                         new List<>());
              }
          }
      }
    },

    // compile
    CODE
    {
      void compile(JVM jvm, int cl)
      {
        var k = jvm._fuir.clazzKind(cl);
        switch (k)
          {
          case Intrinsic    :
          case Routine      : jvm.code(cl); break;
          case Choice       : jvm._types._choices.createCode(cl); break;
          case Field        : break;
          case Abstract     : break;
          case Native       : Errors.warning("JVM backend cannot compile native " + jvm._fuir.clazzAsString(cl)); break;
          default           : throw new Error ("Unexpected feature kind: " + k);
          };
      }
    },
    RUN {
      boolean condition(JVM jvm)
      {
        return jvm._options._run;
      }
      void prepare(JVM jvm)
      {
        Errors.showAndExit();
        jvm._runner = new Runner();
      }
      void compile(JVM jvm, int cl)
      {
        var cf = jvm._types.classFile(cl);
        if (cf != null)
          {
            jvm._runner.add(cf);
          }
        if (jvm._types.hasInterfaceFile(cl))
          {
            var ci = jvm._types.interfaceFile(cl);
            jvm._runner.add(ci);
          }
      }
      void finish(JVM jvm)
      {
        jvm._runner.runMain();
      }
    },
    SAVE {
      boolean condition(JVM jvm)
      {
        return jvm._options._saveClasses;
      }
      void prepare(JVM jvm)
      {
        if (!Files.exists(PATH_FOR_CLASSES))
          {
            try
              {
                Files.createDirectory(PATH_FOR_CLASSES);
              }
            catch (IOException io)
              {
                Errors.error("JVM backend I/O error",
                             "While creating directory '" + PATH_FOR_CLASSES + "', received I/O error '" + io + "'");
              }
          }
      }
      void compile(JVM jvm, int cl)
      {
        var cf = jvm._types.classFile(cl);
        if (cf != null)
          {
            try
              {
                cf.write(PATH_FOR_CLASSES);
              }
            catch (IOException io)
              {
                Errors.error("JVM backend I/O error",
                             "While creating class '" + cf.classFile() + "' in '" + PATH_FOR_CLASSES + "', received I/O error '" + io + "'");
              }
          }
        if (jvm._types.hasInterfaceFile(cl))
          {
            var ci = jvm._types.interfaceFile(cl);
            try
              {
                ci.write(PATH_FOR_CLASSES);
              }
            catch (IOException io)
              {
                Errors.error("JVM backend I/O error",
                             "While creating class '" + ci.classFile() + "' in '" + PATH_FOR_CLASSES + "', received I/O error '" + io + "'");
              }
          }
      }
    };

    /**
     * Perform this compilation phase on given clazz using given backend.
     *
     * @param jvm the backend
     *
     * @param cl the clazz.
     */
    abstract void compile(JVM jvm, int cl);

    void prepare(JVM jvm)
    {
    }
    boolean condition(JVM jvm)
    {
      return true;
    }
    void finish(JVM jvm)
    {
    }
  }


  /*----------------------------  variables  ----------------------------*/


  /**
   * The options set for the compilation.
   */
  final JVMOptions _options;


  /**
   * The intermediate code we are compiling.
   */
  final FUIR _fuir;


  /**
   * The tail call analysis.
   */
  final TailCall _tailCall;


  /**
   * Abstract interpreter framework used to walk through the code.
   */
  final AbstractInterpreter<Expr, Expr> _ai;


  /**
   * JVM identifier handling goes through _names:
   */
  final Names _names;


  /**
   * JVM types handling goes through _types:
   */
  final Types _types;


  final int[][] _numLocals;


  Runner _runner;

  Expr LOAD_UNIVERSE;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create JVM bytecode backend for given intermediate code.
   *
   * @param opt options to control compilation.
   *
   * @param fuir the intermediate code.
   */
  public JVM(JVMOptions opt,
             FUIR fuir)
  {
    _options = opt;
    fuir = opt._Xdfa ?  new DFA(opt, fuir).new_fuir() : fuir;
    _fuir = fuir;
    _names = new Names(fuir);
    _types = new Types(fuir, _names);
    _tailCall = new TailCall(fuir);
    _ai = new AbstractInterpreter<>(fuir, new CodeGen(this));
    _numLocals = new int[2][_fuir.clazzId2num(_fuir.lastClazz())+1];

    Errors.showAndExit();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create the C code from the intermediate code.
   */
  public void compile()
  {
    var cl = _fuir.mainClazzId();
    var name = _fuir.clazzBaseName(cl);

    var ucl = _names.javaClass(_fuir.clazzUniverse());
    _types.UNIVERSE_TYPE = new ClassType(ucl);
    LOAD_UNIVERSE = Expr.getstatic
      (ucl,
       _names.UNIVERSE_FIELD,
       _types.UNIVERSE_TYPE);

    createCode();
    Errors.showAndExit();
  }


  /**
   * create byte code
   *
   * @throws IOException
   */
  private void createCode()
  {
    var ordered = _types.inOrder();

    Stream.of(CompilePhase.values()).forEachOrdered
      ((p) ->
       {
         if (p.condition(this))
           {
             p.prepare(this);
             for (var c : ordered)
               {
                 p.compile(this, c);
               }
             p.finish(this);
           }
       });
  }



  /**
   * Create code for given clazz cl.
   *
   * @param cl id of clazz to compile
   *
   * @return C statements with the forward declarations required for cl.
   */
  public void code(int cl)
  {
    if (_types.clazzNeedsCode(cl))
      {
        var ck = _fuir.clazzKind(cl);
        switch (ck)
          {
          case Routine:
          case Intrinsic:
            {
              codeForRoutine(cl, false);
            }
          }
        if (_fuir.hasPrecondition(cl))
          {
            codeForRoutine(cl, true);
          }
      }
  }

  int current_index(int cl)
  {
    if (_types.isScalar(cl))
      {
        return 0;
      }
    var o = _fuir.clazzOuterClazz(cl);
    var l = _types.hasOuterRef(cl) ? (_types.isScalar(o) ? _types.javaType(o).stackSlots()  // outer of scalars like i64 are just copies of the value
                                                         : 1)
                                   : 0;
    for (var j = 0; j < _fuir.clazzArgCount(cl); j++)
      {
        var t = _fuir.clazzArgClazz(cl, j);
        var jt = _types.javaType(t);
        l = l + jt.stackSlots();
      }
    return l;
  }

  Expr new0(int cl)
  {
    var n = _names.javaClass(cl);
    return Expr.new0(n, _types.javaType(cl))
      .andThen(Expr.DUP)
      .andThen(Expr.invokeSpecial(n,"<init>","()V"));
  }

  Expr prolog(int cl, boolean pre)
  {
    if (false && pre)
      {
        return Expr.UNIT;
      }
    else if (_types.isScalar(cl))
      {
        // arg 0 is current
        return Expr.UNIT;
      }
    else
      {
        var x = Expr.UNIT;
        if (_fuir.clazzOuterRef(cl) != -1 && !_fuir.hasData(_fuir.clazzResultClazz
                                                                    (_fuir.clazzOuterRef(cl))))
          {
            x = Expr.aload(0, JAVA_LANG_OBJECT)
              .andThen(Expr.branch(O_ifnull,
                                   reportErrorInCode("outer in "+_fuir.clazzAsString(cl)+" "+pre+" of type " +
                                                     _fuir.clazzAsString
                                                     (_fuir.clazzResultClazz
                                                      (_fuir.clazzOuterRef(cl)))+" is null"),
                                   Expr.UNIT));
          }
        return x.andThen(new0(cl))
          .andThen(cl == _fuir.clazzUniverse()
                   ? Expr.DUP.andThen(Expr.putstatic(_names.javaClass(cl),
                                                     _names.UNIVERSE_FIELD,
                                                     _types.UNIVERSE_TYPE.descriptor()))
                   : Expr.UNIT)
          .andThen(Expr.astore(current_index(cl)));
      }
  }


  /**
   * Create code to print msg as trace output.
   *
   * @param msg the message to be shown
   */
  private Expr callRuntimeTrace(String msg)
  {
    return Expr.stringconst(msg)
      .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS, "trace", "(Ljava/lang/String;)V", PrimitiveType.type_void));
  }


  /**
   * If trace output is enabled, create bytecode for the given instruction
   *
   * @param cl the current clazz that is being compiled
   *
   * @param c the current code block
   *
   * @param i the index in the current code block
   *
   * @return code to output the trace or a NOP.
   */
  Expr trace(int cl, int c, int i)
  {
    if (TRACE)
      {
        var p = _fuir.codeAtAsPos(c,i);
        var msg = "IN " + _fuir.clazzAsString(cl) + ": " + _fuir.codeAtAsString(cl,c,i) +
          (p == null ? "" : " " + p.show());
        return callRuntimeTrace(msg);
      }
    else
      {
        return Expr.UNIT;
      }
  }
  Expr traceReturn(int cl, boolean pre)
  {
    if (TRACE_RETURN)
      {
        var msg = "return from "+_fuir.clazzAsString(cl)+(pre ? " PRECONDITION" : "");
        return callRuntimeTrace(msg);
      }
    else
      {
        return Expr.UNIT;
      }
  }


  Expr epilog(int cl, boolean pre)
  {
    var r = _fuir.clazzResultField(cl);
    var t = _fuir.clazzResultClazz(cl);
    if (pre || !_fuir.clazzIsRef(t) /* NYI: needed? */ && _fuir.clazzIsUnitType(t))
      {
        return traceReturn(cl, pre)
          .andThen(Expr.RETURN);
      }
    else if (r == -1)   // a constructor
      {
        var jt = _types.javaType(t);
        return
          traceReturn(cl, pre)
          .andThen(jt.load(current_index(cl)))
          .andThen(jt.return0());
      }
    else
      {
        /* NYI the following simple examples creates an reference to an undefined result field:

             a =>
               b (c i32) is
               b 0

        */

        var jt = _types.javaType(t);
        var ft = _types.resultType(t);
        var getf =
          ft != PrimitiveType.type_void ? (Expr.aload(current_index(cl), jt)
                                           .andThen(Expr.getfield(_names.javaClass(cl),
                                                                  _names.field(r),
                                                                  ft)))
                                        : Expr.UNIT;
        return
          traceReturn(cl, pre)
          .andThen(getf)
          .andThen(ft.return0());
      }
  }


  /**
   * In case of an unexpected situation such as code that should be unreachable,
   * this should be used to print a corresponding error and exit(1).
   *
   * @param msg the message to be shown, may include %-escapes for additional args
   *
   * @param args the additional args to be fprintf-ed into msg.
   *
   * @return the C statement to report the error and exit(1).
   */
  Expr reportErrorInCode(String msg)
  {
    return Expr.stringconst(msg)
      .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,"fatal","(Ljava/lang/String;)V", PrimitiveType.type_void))
      .andThen(Expr.endless_loop());
  }

  int allocLocal(int cl, boolean pre, int numSlots)
  {
    var res = _numLocals[pre ? 1 : 0][_fuir.clazzId2num(cl)];
    _numLocals[pre ? 1 : 0][_fuir.clazzId2num(cl)] = res + numSlots;
    return res;
  }

  /**
   * Create code for given clazz cl.
   *
   * @param cl id of clazz to generate code for
   *
   * @param pre true to create code for cl's precondition, false to create code
   * for cl itself.
   */
  void codeForRoutine(int cl, boolean pre)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzKind(cl) == FUIR.FeatureKind.Routine ||
       _fuir.clazzKind(cl) == FUIR.FeatureKind.Intrinsic || pre);

    var cf = _types.classFile(cl);
    if (cf == null) return;
    var prolog = Expr.UNIT;
    var epilog = Expr.UNIT;
    Expr code;
    var name = _names.function(cl, pre);

    // for an intrinsic that is not type type parameter, we do not generate code:
    if (pre ||
        _fuir.clazzKind(cl) == FUIR.FeatureKind.Routine ||
        _fuir.clazzTypeParameterActualType(cl) >= 0)
      {
        if (pre || _fuir.clazzKind(cl) == FUIR.FeatureKind.Routine)
          {
            _numLocals[pre ? 1 : 0][_fuir.clazzId2num(cl)] = current_index(cl) + Math.max(1, _types.javaType(cl).stackSlots());
            prolog = prolog(cl, pre);
            code = _ai.process(cl, pre)._v1;
            epilog = epilog(cl, pre);
          }
        else // intrinsic is a type parameter, type instances are unit types, so nothing to be done:
          {
            code = Expr.RETURN;
            name = "fzRoutine";
          }

        check
          (cf != null);

        var bc_cl = prolog
          .andThen(code)
          .andThen(epilog);
        var code_cl = cf.codeAttribute((pre ? "precondition of " : "") + _fuir.clazzAsString(cl),
                                       bc_cl.max_stack(),
                                       //(cl == _fuir.clazzUniverse() ? 1 : 0) +
                                       _numLocals[pre ? 1 : 0][_fuir.clazzId2num(cl)], bc_cl, new List<>(), new List<>());

        cf.method(cf.ACC_STATIC | cf.ACC_PUBLIC, name, _types.descriptor(cl, pre), new List<>(code_cl));
      }
  }


  /**
   * Create code to create a constant string.
   *
   * @param bytes the utf8 bytes of the string.
   */
  Pair<Expr, Expr> constString(byte[] bytes)
  {
    return constString(Expr.stringconst(bytes)
                       .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS,
                                                  Names.RUNTIME_INTERNAL_ARRAY_FOR_CONST_STRING,
                                                  Names.RUNTIME_INTERNAL_ARRAY_FOR_CONST_STRING_SIG,
                                                  PrimitiveType.type_byte.array())));

  }


  /**
   * Create code to create a constant string.
   *
   * @param bytes the utf8 bytes of the string as a Java string.
   */
  Pair<Expr, Expr> constString(Expr bytes)
  {
    var cs = _fuir.clazz_Const_String();
    var internalArray = _fuir.clazz_Const_String_internal_array();
    var data = _fuir.clazz_fuzionSysArray_u8_data();
    var length = _fuir.clazz_fuzionSysArray_u8_length();
    var fuzionSysArray = _fuir.clazzOuterClazz(data);
    var res = new0(cs)                                // stack: cs
      .andThen(Expr.DUP)                              //        cs, cs
      .andThen(new0(fuzionSysArray))                  //        cs, cs, fsa
      .andThen(Expr.DUP)                              //        cs, cs, fsa, fsa
      .andThen(bytes)                                 //        cs, cs, fsa, fsa, byt
      .andThen(Expr.DUP_X2)                           //        cs, cs, byt, fsa, fsa, byt
      .andThen(putfield(data))                        //        cs, cs, byt, fsa
      .andThen(Expr.DUP_X1)                           //        cs, cs, fsa, byt, fsa
      .andThen(Expr.SWAP)                             //        cs, cs, fsa, fsa, byt
      .andThen(Expr.ARRAYLENGTH)                      //        cs, cs, fsa, fsa, len
      .andThen(putfield(length))                      //        cs, cs, fsa
      .andThen(putfield(internalArray))               //        cs
      .is(_types.javaType(cs));                       //        -
    return new Pair<>(res, Expr.UNIT);
  }

  /**
   * Create code to create a constant string.
   *
   * @param str the string to create
   */
  Pair<Expr, Expr> constString(String str)
  {
    return constString(str.getBytes(StandardCharsets.UTF_8));
  }


  boolean fieldExists(int field)
  {
    var occ   = _fuir.clazzOuterClazz(field);
    var rt = _fuir.clazzResultClazz(field);

    return _fuir.hasData(rt)       &&
      !_types.isScalar(occ)        &&
      _types.clazzNeedsCode(field) &&
      _types.resultType(rt) != PrimitiveType.type_void;
  }


  Expr putfield(int field)
  {
    if (PRECONDITIONS) require
      (fieldExists(field));

    var cl = _fuir.clazzOuterClazz(field);
    var rt = _fuir.clazzResultClazz(field);
    return
      Expr.comment("Setting field `" + _fuir.clazzAsString(field) + "` in `" + _fuir.clazzAsString(cl) + "`")
      .andThen(Expr.putfield(_names.javaClass(cl),
                             _names.field(field),
                             _types.resultType(rt).descriptor()));
  }

}

/* end of file */
