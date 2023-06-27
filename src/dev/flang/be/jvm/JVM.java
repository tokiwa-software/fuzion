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

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.Pair;


/**
 * JVM provides a JVM bytecode backend converting FUIR data into classfile code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class JVM extends ANY
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

  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


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
  // final AbstractInterpreter<CExpr, CStmnt> _ai;


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
    _fuir = opt._Xdfa ?  new DFA(opt, fuir).new_fuir() : fuir;
    _tailCall = new TailCall(fuir);
    // _ai = new AbstractInterpreter<>(_fuir, new CodeGen());

    Errors.showAndExit();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create the C code from the intermediate code.
   */
  public void compile()
  {
    Errors.error("JVM backend still under development");
    Errors.showAndExit();
  }


}

/* end of file */
