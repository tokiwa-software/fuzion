## 2022-02-25: V0.071dev

## 2022-02-25: V0.070

- fzjava
  - fixed name conflicts between Java's `String.isBlank`/`String.split` and
    Fuzion's `string.isBlank`/`String.split`.

## 2022-02-25: V0.069

- flang.dev
  - design
    - added example for [Automatic Monadic
      Lifting](https://flang.dev/design/monadic_lifting) using Knuth's
      man-or-boy example and a new feature _handles_ that provides a state monad
      similar to Haskell's _IORef_.

- C backend
  - heap allocate value instances that are kept alive through inner instances,
    fixes segfault in #168.

- IR
  - added new category of clazz: dynamic outer ref. A value clazz _c_ is a
    dynamic outer ref if it is referenced from inner clazzes _i_ and there
    exists heirs of _c_ that may redefine features such that the accesses from
    _i_ require dynamic binding.

- Fuzion language
  - added keyword *intrinsic_constructor* to mark intrinsics that create an
    instance of their result type.  This simplifies static analysis for
    frequence cases such as *fuzion.java.getField0*.
  - inline arrays now may end with a comma as in
    ```
      a := [ 1,
             2,
             3,
           ]
    ```

- stdlib
  - added *Sequence.reduce*, *string.split*, *string.isBlank*, *handles*, ...

- fuzion.ebnf
  - several fixes, cleanup

- fz, fzjava
  - minor fixes in scripts for issues reported by _shellcheck_.

## 2022-02-04: V0.068

- fzjava

  - fixed generated code to use 'Sequence' instead of 'List', which was renamed
    a while ago.

- stdlib

  - lib: Minor improvements used in tutoral at flang.dev:

    - added 'list head tail' as a counterpart to Haskell's 'head : tail' for
      list creation.

    - added u128.name

    - added strings.fromCodepoints

## 2022-01-30: V0.067

- front end

  - speed up by

    - caching results for AbstractType.actualType

    - replaced string comparison in FeatureName.compareTo by int comparison.

    - removed usage of strings in AbstractFeature.isOuterRef

    - removed feature names for internally generated features from .fum file

    - caching for LibraryFeature.innerFeatures()

    - disabled check()s unless env var CHECKS=true

    - removed outer ref field from choice features and from any feature whose
      outer feature is the universe.

- stdlib

  - added 'elementOf'/'infix ∈' to 'string'

  - added 'add' to 'Set'

  - added explanation of the origin of the term 'yak' to the same named feature.


## 2022-01-24: V0.066

- front end:

  - speeup up by

    - creating source positions for code loaded from .fum file on demand only.

    - caching of code, result, outerRef withn LibraryFeature

    - using StatementVisitor instead of FeatureVisitor. The former does not
      support code modification, so it might be faster.


## 2022-01-21: V0.065

- flang.dev

    - added .fum file documentation to https://flang.dev/design/fum_file

    - added browsable fuzion API documentation to https://flang.dev/docs/index

- parser

    - single-line operator expressions may now continue after a multi-line
      klammer-expression, e.g.,
      ```
        a := 1 * (2
                  + 3
                  + 4) - 5

      ```
      is allowed now.  The same holds for expressions using [] or {}:
      ```
        b := 2 * { say "hi"
                   + 4
                 } - 1

        c := 3 * [ "A",
                   "B",
                   "C" ].fold strings.concat
                        .byteLength - 5
      ```
    - improve handling of '.'-chained calls over several lines. It is now
      possible to write
      ```
        s := myseq.drop n
                  .take m
                  .asString
      ```
      before, '.take' was applied to 'n' instead of the result of '.drop'.  Now,
      a space separated argument list is terminated by a new line followed by
      '.', while a call in a line is permitted to continue in the next line if
      followed by an indented '.'.

- front end

  - suppoert for qualified features, even to add inner features to library code
    such as
    ```
      i32.factorial => if val <= 1 then 1 else val * (val-1).factorial
    ```
    or even

    ```
      i32.postfix ! => if val <= 1 then 1 else val * (val-1).factorial
    ```
    such that you can write
    ```
      say 6.factorial
      say 4!
    ```
  - parser now uses the same rule when parsing a .fz file read form a source
    directory as when reading stdin or an explicit file given on the command
    line.  Before, .fz files read from a source directory were not permitted to
    contain more than one feature declaration.

  - cleanup in module creation and loading code

- stdlib

  - renamed 'List' as 'Sequence'. A 'Sequence' is a finite or infinite data
    structure containing elements of one type that can be iterated as a
    (Lisp-style) 'list' or a (Java-style) 'stream'

  - cleanup: joined related source files, e.g. i32.fz now contains the code of
    i32s.fz and i32_.fz as well.  This is possible since .fz files read from a
    source directory now may contain several feature declarations.

## 2022-01-06: V0.064

- front end:

  - several optimizations like caching to improve performance of using module
    file.

  - save source code positions of feature declarations and statments to module
    file for more detailed error messages.


## 2022-01-03: V0.063

- front end:

  - support precompilation of base library code into a module file
    build/modules/base.fum.

    This file is mapped into memory instead of parsing the source files in
    build/lib resulting in faster startup up.

    For the front end and the interpreter, the AST is re-created from this file
    as needed.

    Currently, the AST is also re-created to produce the FUIR representation
    used by the C backend, but this should eventually not be needed any longer.

## 2021-10-29: V0.062

- flang.dev website

  - added more idioms, e.g. #182 (quine), 242..258, etc.

- IR

  - major code restructuring and cleanup moving logic away from the AST and into
    the Front End, Middle End, Application IR, etc.  The goal is to be able to
    create "module IR" files as a faster replacement of source code.


  - added very basic data-flow-analysis phase that finds some very obvious cases
    of uses of uninitialized fields.


## 2021-10-15: V0.061

- flang.dev website

  - added many idioms, updated syntax of existing idioms

- library

  - added matrix, matrices

  - lot's of minor improvements

- Frontend

  - improve type inference for type parameters

- general

  - switched to OpenJDK 17


## 2021-09-30: V0.060

- flang.dev website

  - new design for code input / execution output text areas

  - tuturial section on string literals.

  - examples and idioms updated for new syntax of lambda expressions and
    function types.

  - new idioms 141, 142, 150

- fuzion.ebnf

  - grammar is now checked using antlr, bugs and inconsitencies have been fixed.

- Frontend/Parser:

  - new syntax for function types: '(i32, string) -> bool' or 'u8 -> string'

  - new syntax and type inference for lambdas: 'x -> 2*x', 'x,y -> x+y',
    '(a,b,c) -> a < b < c', '() -> 42', etc.

  - last expression in a constructor now must result in type 'unit'. This is to find
    problems earlier when constructors are used instead of routines.

- IR

  - removed callbacks from IR to the backend to have a clearer separation of phases.

- FUIR

  - Moved code to set outer references from backend to FUIR to simplify backend.

- C backend

  - fix wrap-around overflow handling in created code for signed integer types.

  - several code generation fixes

  - code cleanup

- tests

  - analysed root cause of failing tests, added issues to github for tests that
    could not be fixed, added mechanism to skip these tests, such that a normal
    build does not report any errors for known problems.

- fz

  - limit number of errors and warnings printed, new options '-XmaxErrors=<n>'
    and '-XmaxWarnings=<n>'.

  - many bug fixes


## 2021-09-02: V0.059

- FrontEnd / lib / backends

  - support for types i8, i16, u8, u16, f32, f64.

- flang.dev website

  - added sections on integer and float constants to tutorial.

- Java interface:

  - added java.desktop module that contains java.awt graphics

  - support for primitive types and Java arrays

- examples

  - added example javaGraphics that uses java.desktop module to draw into a
    window.


## 2021-08-23: V0.058

- FrontEnd

  - fix running examples such as tutorial/overflow* on flang.dev (see git commit
    for details).

## 2021-08-20: V0.057

- Java interface

  - new tool fzjava to create Fuzion wrappers for Java code

  - 'fz -modules=java.base <x>' will run '<x>' with JDK's java.base module
    accessible, e.g., via

      serversocket := Java.java.net.ServerSocket.new 8080

- examples

  - added example 'callingJava' to show how to call Java code from Fuzion and
    'webserver' that shows how to use Java APIs to write a minimalistic web
    server in Fuzion.

- fz

  - now supports modules via '-modules' options. Currently, these are just
    additional source code directories in 'build/modules', but these should be
    packed into fuzion module files eventualls.

- Parser

  - precedence of fraction bar ⁄ is higher than basic arithmetic operations.

- library

  - support for types i8, i16, u8, u16

  - string.utf8 now returns an List<u8>.

- flang.dev website

  - now supportes ACE editor for source code

  - use server-side events to fix many issues in the communication.

  - several new idioms implemented and old ones improved

- General

  - Fuzion logo is now generated


## 2021-07-30: V0.056

- Parser

  - Changed parsing of expressions using []: 'a[i]' is an call to 'a.index
    [](i)' as before, but 'a [i]' now is a call to 'a' with an array as
    argument, i.e., 'a([i])'.  This avoids extra parentheses when passing an
    array as an argument.

- tests

  - changed syntax in tests to not use { }, ( ), commas, semicolons, when
    possible

- fz

  - now accepts '.fuzion' as extension for source file given as command line
    argument: 'fz ./helloworld.fuzion' now works instead of printing a confusing
    message.

  - error messages now enclose code snippets in '' and highlight feature names, types
    and expressions in different colors.

## 2021-07-21: V0.055

- C backend

  - several bugs fixed: bugs related to (normlized) outer refs, calls to
    not-instantiated clazzes, code after a statement that produced a void
    result, array of unit type, etc.

- IR

  - several fixes in normlization of outer refs, i.e., in the way clazzes
    instantiated with different outer contexts are distinguished.  This fixed
    several tests that failed when using the C backend.


## 2021-07-08: V0.054

- flang.dev

  - changed path to load 'main.js' to be absolute '/main.js'.  Before, main.js
    could not be loaded if the first page access was in a sub directory such as
    'flang.dev/tutorial/index' and anything requiring java script, like
    executing examples, did not work.

- Front End

  - fixed issue#8: returing 'false' as 'option<V>' n logner allowed


## 2021-07-06: V0.053

- Parser

  - More haskell-style call syntax
    ```
      f(a,b)   calls f with arguments 'a', 'b'
      f (a,b)  calls f with argument '(a,b)', a tuple

      f fun () => x    no longer works
      f (fun () => x)  must be used instead
    ```

- Front End

  - Now enforce feature with 'unit' result to result in unit type value.

- lib

  - faction now uses unicode fraction slash '⁄' to create fraction, e.g., '1⁄2'

## 2021-06-30: V0.052

- Parser

  - Haskell-style call syntax with space separated arguments and left binding,
    so the call
    ```
      f a b (g c d)
    ```
    is equivalent to
    ```
      f(a,b,g(c,d))
    ```
    Additionally, infix, prefix and postfix operators now bind stronger than a call
    if there is no white space (or comment) between the operator and its operand,
    and weaker than a call if there is white space.  This means
    ```
      f x+1
    ```
    is equivalent to
    ```
      f(x+1)
    ```
    while
    ```
      f x + 1
    ```
    is equivalent to
    ```
      f(x)+1
    ```
    This makes the semantics depend on white space in a way that might be
    surprising.  This change is therefore considered for experiments only, if this
    turns out to be too confusing, the grammar should go back to not make operator
    precedence depend on spaces around operators.

- lib, tests

  - libs and tests were changed to support new call syntax, in particular the
    left binding of call and the weaker precedence of operators that are
    separated by white space.


## 2021-06-29: V0.051

- flang.dev

  - design: added flang.dev/design/calls.html for thoughts on Fuzion's call
    syntax.

  - added idioms 134, 135, 136

- front end

  - allow chained boolean expressions with >3 terms, e.g., 'a < b < c < d'

  - support for '? |' instead of match, allow '|' as case separator
    ```
      a ? x X => x.foo
        | y Y => y.bar
    ```
    as short-hand for
    ```
     match a
       x X => x.foo
       y Y => y.bar
    ```
    also allowed is
    ```
     match a
       | x X => x.foo
       | y Y => y.bar
    ```
  - A loop's 'until' clause now supports 'then' to separate the condition from
    the code executed for successful loop termination.  The code is now
    summetric to 'if cond then'.

- lib

  - 'o??' and 'o!!' are new postfix operator synonymes for 'exists' and 'isNil'
    in 'option', 'numOption' and 'outcome'.

- fz

  - several bug fixes in FE, IR, FUIR; interpreter and C backend.


## 2021-06-23: V0.050

- Fuzion front-end/middle-end

  - Fixed major performance bottleneck, performance improved by about 40-60%.

- fz tool

  - new option -XjavaProf (for profiling fz's own Java code) and -X/--xhelp).

- flang.dev

  - added idiom 76

## 2021-06-22: V0.049

- lib: added

  - bitsets
  - sum
  - List.tails, takeWhile, dropWhile
  - searchableList.countMatches, countMatchesOverlapping
  - string.count, contains

- FUIR

  - suppress code generation for pre- and post-conditions that are constant and
    'true'.  Reduces binary size when using C backend on HelloWorld with
    '-debug=0 -safety=off' by 3.5%.

- flang.dev

  - added idioms 39, 40, 49, 68, 76, 82, 116

## 2021-06-18: V0.048

- IR: Added mechanism to 'normalize' clazzes to reduce the explosion in the total
  number of clazzes generated: Clazzes that only differ in their outer clazzes
  are now fused if the outer clazzes are references.

- lib

  - replaced 'streamable' by 'List', i.e., the preferred way to iterate is by
    using lisp-syle lists consisting of a head and a cons-cell.

  - added u128 for unsigned 128-bit integers

- flang.dev

  - added idioms 62, 63

## 2021-05-31: V0.047

- tools:

  - fz tool now prints execution time for phases prep, front end, middle end,
    ir, and back end.

- front end:

  - improved performance by caching result of Type.outer()

- lib

  - fix exponential performance when printing string built from s+(s+(s+(...)))


## 2021-05-30: V0.046

- lib

  - string: added startsWith, endsWith


- flang.dev

  - added idioms 84, 85, 86, 87, 88, 89, 90, 93, 96, 97, 100, 108, 110, 112,
    113, 114, 117, 118, 119, 122, 124, 127, 227, 231


## 2021-05-24: V0.045

- lib:

  - integer.bin/cot/hex to create binary, octal and     hex strings

  - fix quadratic runtime of printing string consisting of many sub-strings
    concatenated using 'infix +'.

  - i32/u32/i64/u64: added onesCount (population count)

  - stream/streamable: added asList, planning to work more with (lazy,
    immutable) lists than with streams that are inherently mutable. Also added
    'take', 'drop', 'inifx ++' and 'asArray' to stream/streamable.

  - added feature 'some' as standard wrapper. This can be used, e.g., to make an
    'option<some<nil>>' without getting complaints about overlapping values, the
    possible values of typ 'option<some<nil>>' are 'nil' and 'some nil'


- flang.dev

  - added idioms # 38, 44, 46, 47, 48, 53, 54, 55, 57

## 2021-05-24: V0.044

- lib

  - new unit types strings, i32s, i64s, u32s, u64s that contain monoids and
    other features related to string, i32, i64, u32, u64 but not requiring an
    instance.  Also added new routines i32, i64, u32, u64, all without arguments
    to distinguish them from the integer types that have one argument, that
    result in the corresponding unit type i32s, i64s, u32s, and u64s,
    respectively.

    Now, we can write, e.g., '[1,2,3].fold i32.sum' to sum the elements of an
    array.

  - add new feature 'Set' as abstract parent for sets, and operations such as
    'infix ∈', so we can write '2 ∈ [1,2,3]' or '7 ∉ 10..300' (both result in
    bool value true).

- flang.dev

  - added idioms # 51, 52

## 2021-05-21: V0.043

- lib

  - added codepoint.fz and string.codepoints/codepointsArray to create stream
    and array of codepoints from a string.

  - added 'infix **' for integer exponentiation.

- parser/lexer:

  - support for binary, octal, and hex numbers using prefix 0b, 0o, and 0x.
    Support for grouping of digits using '_' as in 'ichiOku := 1_0000_0000',
    groups must be equal in size and at least two digits long.

- flang.dev

  - added idioms # 32, 41

  - added design page on integer literals / constants.

## 2021-05-20: V0.042

- lib

  - add string.parseI32*, parseU32*, etc to parse integers from decimal,
    binary, octal or hex numbers.  Very nice generic implementation for
    all integers in one feature!

  - added error.fz for an error condition

  - added outcome.fz similar to Rust's result: a choice between a value and an
    error.

- flang.dev

  - added idioms # 18, 22, 26, 27, 34, 42

## 2021-05-19: V0.041

- flang.dev

  - added idioms # 8, 9, 13, 16, 19

- lib

  - added array.reverse, list.reverse

  - added fuzion.std.panic

  - added map as parent of hashMap and orderedMap, defines keys, values, items

  - added Option/array/list.asString

- front end

  - fix#9: AdrToValue.type() was not intern()ed.

## 2021-05-18: V0.040

-lib

  - added features partiallyOrdered and ordered and implemented ordered by
    numeric and string.

  - added feature hasHash and implemented it for numeric and string.

  - added hashmap and orderedMap.

  - added psMap and psSet to have well behaved persistent sets and maps for
    ordered values.

  - added highestOneBit and trailingZeros to i32/i64/u32/u64

- C backend

  - add support for indentation in generated code.

## 2021-04-20: V0.039

- C backend

  - now works with array initialization using '[a, b, c]'.

- flang.dev

  - updated examples to use array initialization instead of InitArray.fz

  - new design of main page using CSS magic.


## 2021-04-20: V0.038

- Fuzion language

  - Support for array initialization using '[a, b, c]' or '[e; f; g]' where the
    array elements are expressions.  The array type is either inferred from the
    union of the types of the element expressions or propagated back from the
    target the array is assigned to.  Boxing and Tagging for array elements is
    automatic.  Support in FUIR/C backend missing, only interpreter for now.


## 2021-04-14: V0.037

- Fuzion language

  - match statement:

   - now infers actual generics if this does not lead to ambiguity. So one can,
     e.g., match a 'list<string>' against 'Cons' instead of 'Cons<string,
     list<string>>'.

   - Errors are created if a case does not match anything, matches several types
     or if some cases are missing

- lib

  - bitset.fz provides persistent bitsets

  - array.fz is a persistent array now, i.e., array.put(index, value) creates a
    new array.

  - stream.map, stream.fold added

  - numeric.min, numeric.max added


## 2021-04-08: V0.036

- lib

  - array.fz is immutable now, new feature marray.fz provides mutable arrays.

- Fuzion Language:

  - Field initialization 'f type := value', instance decomposition
    '(a i32, b bool) := obj' and array writes 'a[i] := v' now use ':='
    instead of '='.

## 2021-04-07: V0.035

- Fuzion Language:

  - Assignments are no longer accepted in the format 'a = b'. Instead, an assignment
    must be of the form

      set a := b

    to make sure that mutations are clearly visible in the source code.

## 2021-04-06: V0.034

- Fuzion Language

  - identifiers now may consist of non-ASCII code points of Unicode categories
    LC, Ll, Lm, Lo, Lt, Lu, Nd, Nl, and No, while the first character may not be
    of one of the numeric types No, Nl, No.

  - operartors now may consist of non-ASCII code points of Unicode categories
    Sc, Sk, Sm, So.

- tools:

  - fz tool now supports command line options -verbose, -debug, -safety,
    -unsafeIntrinsics

  - 'fz' now can be used giving a source file name as in 'fz Hello.fz'.

  - 'fz -latex' creates a LaTeX style for Fuzion source code.

## 2021-03-29: V0.033

- lib

  - added list.fold, list.take, added predefined monoids to numeric ands string.

  - renamed FALSE.fz and TRUE.fz to avoid name clashes on case-insensitive file
    systems.

## 2021-03-25: V0.032

- Fuzion Language:

  - Constant string now allow embedded identifiers using $id and embedded
    expressions using {expr}.

  - Constant strings now support escape sequences:
    | Esc sequence |  result | unicode |
    |------|-------|---------|
    | '\b' |  BS   | 0x08    |
    | '\t' |  HT   | 0x09    |
    | '\n' |  LF   | 0x0a    |
    | '\f' |  FF   | 0x0c    |
    | '\r' |  CR   | 0x0d    |
    | '\"' |  "    | 0x22    |
    | '$'  |  $    | 0x24    |
    | '\'' |  '    | 0x27    |
    | '\\' |  \    | 0x5c    |
    | '\{' |  {    | 0x7b    |
    | '\}' |  }    | 0x7d    |

## 2021-03-19: V0.031

- C backend

  - function to compile one whole feature is pure now

- tests

  - Makefiles now run tests using c backend in addition to interpreter

## 2021-03-18: V0.030

- C backend

  - support for postconditions

## 2021-03-17: V0.029

- C backend

  - support for choice types of unit types and one reference type, (e.g.,
    Option<T>). These do not require a tag since the unit types can be encoded
    as a special pointer value.

  - support for preconditions

## 2021-03-16: V0.028

- AST/IR/BE:

  - Tagging a value when assigned to a choice type is now done explicitly by an
    IR command Tag.  This no longer happens implicitly as part of an assignment,
    simplifying the IR and backends.

- C backend

  - fix handling of boxed unit

- Parser

  - a field with formal generics or formal arguments is now disallowed as early
    as during the parsing step.

## 2021-03-15: V0.027

- IR

  - now distinguishes instantiated from called clazzes resulting in less code
    generated by C backend

- C backend

  - support for arrays of arbitrary types

  - support for fields declared in universe

## 2021-03-11: V0.026

- C backend:

  - loads of clean-up and simplifications

  - bug fixes for dynamic calls and write accesses to outer fields

## 2021-03-10: V0.025

- C backend:

  - implemented match for non-ref values (i.e, values that distinguished by tag
    and not by a type check).

- Benchmarks:

  - added c_man_or_boy, a benchmark that compiles a the man_or_boy example to C

## 2021-03-09: V0.024

- Desgn:

  - Added section "Automatic global data" in the file
    design/monadic_lifting.html to propose monadic parameters together with
    automatic monadic lifting of callers of features with these monadic
    parameters as a solution for global state (and much more).

- C backend:

  - Choice types make progress: C structs defined, assignment to choice type is
    implemented, match statement is still missing.

  - fixed dynamic calls that did not work to call inherited feature.

## 2021-03-05: V0.023

- Design

  - added 'Feature Kinds' section with very detailed comparison of the
    capabilities of constructors, routines, fields, intrinsics, abstracts and
    choices.  Includes example code for uses of each feature kind in different
    scenarios.

- C backend:

  - several bug fixes and improvements.

## 2021-03-02: V0.022

- C backend

  - Compiles and runs HelloWorld without any warnings during Fuzion to C
    compilation and during C to machine code compilation.

  - added intrinsics for i32, i64, u32, u64.

- IR/FUIR/C backend:

  - code cleanup


## 2021-02-24: V0.021

- IR:

  - no longer creates 'impossible' clazzes such as integer<i32>.infix + when there is no
    instance of integer<i32> created anywhere. So C backend does not need to filter these
    out.

  - some normalization of clazzes to reduce total number of clazzes and amount of code.

- lib:

  - monad.fz added for experiments.

## 2021-02-22: V0.020

- lib:

  - choice.fz now comes with detailed explanation of sum types

  - Tuple.fz now comes with a detailed explanation of product types.

  - new list.fz and Cons.fz provide abstract list framework

- ir:

  - removed several ways that clazzes that cannot exists at runtime such as
    could be created, e.g., 'integer<i32>.ref decimalStr', which should be
    'i32.ref deciamStr'.

## 2021-02-18: V0.019

- lib:

  - added unit.fz as a standard unit type and changed void.fz into a 'real' void
    type with no instances.

  - added monoid.fz and quantors.fz

  - added monadic 'bind' operators to Option and numOption, re-implemented numOption
    using monads.

- front end:

  - added boxing of values of generic type and for implicit assignments to function result
    field.

  - internal cleanup with respect to types, removed internally used type t_ANY, replaced it
    by void.

## 2021-02-12: V0.018

- C backend:

  - The C backend successfully compiles a "HelloWorld" example now using the
    call

      fz -c HelloWorld

    which generates a HelloWorld.c file and then invokes clang -O3 to create a
    HelloWorld binary.

Benchmarks:

  - added c_hello, a benchmark that compiles a HelloWorld example to C


## 2021-02-07: V0.017

- Frotend:

  - Inspired by FOSDEM talks on Raku, I have decided to experiment with making
    the parentheses on calls optional. This means that instead of

      f(a, g(h(-b, c + d)))

    you can now write

      f a, g h -b, c + d

    To avoid ambiguity, a new keyword 'then' separates the condition in a
    single-line 'if'-statement from the following block and uninitialized fields
    can no longer be declared as

       f type

    but usng

       f type = ?

    An operator '<op>' in an expression like 'x <op> y' is now parsed as a
    prefix operator 'x(<op>y)' if there is white space or a comment between 'x'
    and '<op>' and no white space nor a comment between '<op>' and 'y', i.e,
    'a-b', 'a- b' and 'a - b' are all calling an 'infix -' on 'a' with argument
    'b', while 'a -b' calls 'prefix -' on 'b' and passes the result as first
    argument to 'a'.

- stdlib: new features

    'say' as shorthand for 'stdout.println'

    'integer.infix %%' to check if an integer is divisibly by another integer

- Tools

  - added simple syntax highlighting tool via 'fz -pretty'

- FUIR and C backend

  - lots of minor improvements


## 2021-02-01: V0.016

- Frontend:

  - all loops are now implemented using tail recursive features.  IR and backend
    support for loops has been removed.

  - fixed several problems with field visibility and masking in nested scopes.

- Interpreter

  - stack trace printing now compresses simple recursion.

- C backend

  - can compile simple tests including loops now.  Main missing features is
    references and dynamic binding

  - made code more readable by using identifiers closer to the original Fuzion
    feature names.

- General

  - error printing now uses ANSI escapes to produce colourful output.  Can be
    disabled via env var FUZION_DISABLE_ANSI_ESCAPES=true.

- Design

  - added page on static vs. dynamic typing


## 2021-01-12: V0.015

- Fuzion IR

  - extracted dynamic binding related code from Clazz.java to
    DynamicBinging.java.

- flang.dev

  - Removed access restrictions, made website and tutorial accessible without
    login.

  - Replaced Fusion by Fuzion at most locations.

- Design

  - Updated module dependencies with explicit backends, added dependency from
    optimizer to interpreter backend since the optimizer will need an engine to
    evaluate compile time constants and the interpreter should do a perfect job
    here.

- Fuzion Front End

  - Removed Loop from AST, a loop is now directly replaced by a tail recurive
    call.

  - Source file name extension is .fz now.

  - one line comments introduced with '#' can start in any column now.  Replaced
    '//' by '#' in the standard lib and examples.

- C backend

  - Compiles and runs first micro-HelloWorld, still many limitations.

      hw is
        fusion.std.out.write(72)
        fusion.std.out.write(87)
        fusion.std.out.write(10)

- Fusion tools

  - added proper command line handling to fusion command line tool, in
    particular -interpreter, -c, -llvm, etc. to chose backend.

## 2020-11-22: V0.014

- Fusion Middle End

  - improved smart linking: Do not include features that could be called
    dynamically but there is no corresponding dynamic call.

- Fusion Front End

  - replaced boolean prefix !, infix &&, infix || and infix : by corresponding
    if-statements to support lazy evaluation.

  - implemented simple constant propagation for bool constants.

  - Expressions of the form 'a <op1> b <op2> c' where 'a <op1> b' yields a bool
    result and '<op2>' is not defined as an infix operator on bool are now
    automatically converted to '(a <op1> { tmp := b; tmp }) && (tmp <op2> c'.
    This means that expressions such as '0 <= index < length' are now possible.

  - Renamed bool operator '==' as '<=>' to avoid confusion when comparing 3 bool
    variables using 'a == b == c' (which holds, e.g., for 'true == false ==
    false').  Maybe the front end should require parentheses as in '(a <=> b)
    <=> c' or '(a == b) == c' when 'a' is boolean to avoid confusion.  How is
    this done in other languages?

- Fusion tools

  - changed fusion.verbose property from boolean to integer, 0 is non-verbose,
    levels 1..10 show different levels of detail.

  - new properties fusion.debugLevel=<int> and fusion.safety=<bool> can be used
    to set the default values for debugLevel and safety (to enable or disable
    pre- and postcondition checks).

- tests

  - added test case redef_args to redefine argument fields.  Not working yet.

## 2020-11-16: V0.013

- Fusion Interpreter: No longer uses virtual call for a dynamic feature called
  on a value instance.

- middle end: Split up finding runtime clazzes and building virtual call tables
  into two phases.  No longer builds virtual call tables for value clazzes.

- Design

  - added page "Matches in Expressions" describing possible syntax of
    expressions using '?' to match the type of a value of choice type.

- removed "throws" keyword and its uses in feature declarations and
  pre/postconditions.

## 2020-11-13: V0.012

- Fusion Interpreter

  - fixed boxing of choice types (only the tag was copied, so values remained
    uninitialized).

- standard lib

  - added support for 2- and 3-dimensional arrays through features

          array<T>>(l1, l2,     fun (i32, i32, i32) T)
          array<T>>(l1, l2, l3, fun (i32, i32, i32) T)

    these inherit from the 1-dimensional array feature, so it maps the
    multi-dimensional array to a flat unidimensional array.

  - added feature numOption<T> as a pseudo-numeric result type of numeric's +?,
    -? and *? operations to allow expressions such as 'a *? b +? c'

  - moved code to create interval from i32/i64/u32/u64 into separate generic
    feature hasInterval to avoid code duplication.

- Fusion middle end: Major cleanup and some bugfixes in handling of outer
  features that showed up when writing hasInterval.

## 2020-11-10: V0.011

- website

  - added tutorial pages on integer type, overflows, fractions.

- Fusion Interpreter

  - now catches StackOverflowError and shows fusion stack trace instead of Java
    stack trace

- Added prefix $ as an alternative to .asString.  Now, you can write '$length +
  "cm"' to create a string like "23cm".

- added overflowing, optional, saturating and wrapping basic operations to
  integer types:

   +  , -  , *   cause runtime error (if debug enabled) on overflow / underflow
   +? , -? , *?  produce an Option that is nil in case of overflow / underflow
   +^ , -^ , *^  are saturating, result is max / min on overflow / underflow
   +° , -° , *°  are wrapping, like in Java

- Nice exercise in generic programming: Added asString feature to
  integer to replace specific implementation in heirs i32, u32, i64 and u64.

- operator precedence: Now, prefix operators always take precedence over infix
  operators, i.e., 'a + + b' is parsed as 'a + (+ b)'.  Precedence is taken into
  account, i.e. 'a * + * b' is parsed as '(a *) + (* b)', while 'a + * + b' is
  parsed as 'a + (* (+ b))'.  Infix operators are parsed left-to-right except for
  operators with right associativity (currently only '^').  Postfix operators
  have precedence over prefix operators with equal precedence value, i.e, '+a-'
  is parsed as '+(a-)'.

  The precedence of multi-character operators such as '|-*-|' is determined by
  the first character, '|' in this case.  Precedence levels currently are

   15: @
   14: ^
   13: !$ (only prefix and postfix)
   12: ~
   11: */%⊛⊗⊘⦸⊝⊚⊙⦾⦿⦸⨸⨁⨂⨷
   10: +-⊕⊖
    9: .
    8: #
    7: $ (only infix)
    6:
    5: <>=⧁⧀⊜⩹⩺⩹⩺⩻⩼⩽⩾⩿⪀⪁⪂⪃⪄⪅⪆⪇⪈⪉⪊⪋⪌⪍⪎⪏⪐⪑⪒⪓⪔⪕⪖⪗⪘⪙⪚⪛⪜⪝⪞⪟⪠⪡⪢⪤⪥⪦⪧⪨⪩⪪⪫⪬⪭⪮⪯⪰⪱⪲⪴⪵⪶⪷⪸⪹⪺⪻⪼⫷⫸⫹⫺
       ! (only infix)
    4: &
    3: |⦶⦷
    2: ∀
    1: ∃
    0: all other operators
   -1: :

  this list is a little arbitrary, is there some mathematical precedence
  definition related to the mathematical unicode chars?

- added new keyword "redef" as alternative to "redefine" (will eventually dump
  'redefine'.

- Fusion std library: Added abstract features numeric and integer as parents for
  integer types. For integer types i32, u32, i64, u64 added preconditions to
  catch overflows.  Added fraction type: 'a /-/ b' for a, b of any (but the
  same) integer type defines a fraction that can be used with all the standard
  operations defined for numeric types.

- Enabled support for generic constraints. A generic feature declared as "name<T
  : c>(args ..)" puts the constraint c on the generic argument T. NYI: Still
  need to check that the actual generic arg is assignable to the constraint.

- '°' is now a legal character in an operator. New opeartors +°, -° and *°
  implement wrap-around operations, while +, - and * have preconditions that
  ensure there is no overflow.

- Created Slack channel #fusion-language:
  https://join.slack.com/t/fusion-zentrale/shared_invite/zt-j9ab7feo-3fl2oZLBfnKdyfOaDgvnjg

- Added types i64, u32, u64 and numeric, made i32, i64, u32 and u64 inherit
  from numeric.

- Removed "throws" clause from the syntax of feature and function declaration, I
  do not intend to support exception: An exception must either be part of the
  function result, so it should go there, or it is a fatal error that kills (at
  least) the current thread.

- Fusion Interpreter

  - added specialization for reading fields in value type to improve performance

## 2020-11-06: V0.010

- benchmarks: Added first simple performance benchmark that runs the man_or_boy
  example repeatedly.

- replaced abstract and native modifiers by specific routine bodies "is
  abstract" and "is intrinsic". This avoids the problem of marking a field
  abstract or intrinsic.

- website

  - update tools module design (dependency graph)

- Fusion Interpreter

  - split code into two packages dev.flang.ast and dev.flang.be.interpreter.
    Removed dependencies from ast to be.interpreter, the dependencies from
    be.interpreter to ast will have to be changed to refer to fuir (once it
    exists.

## 2020-11-03: V0.009

- website

  - added Fusion grammer fusion.ebnf linked from the main page.

  - Fusion Tutorial

    - added sections on Design by Contract, developing with pre- and
      postconditions, predefined pre- and postcondition qualifiers.

- pre- and postconditions for single features are supported now.  A failing
  condition causes termination of the Fusion interpreter with an error message
  and a (Fusion!) call stack.

- Condition qualifiers have been added to control execution of pre- and
  postconditions: safety, debug, debug(i32), pedantic, analysis.

- new boolean operator "infix :", which implements "implies" ((a : b) <==> !a ||
  b). ':' has lowest precedence now, such that preconditions of the form

    pre
      safety: index < length

  are possible now.

- new keywords "pre", "post", and "inv" as alternatives for "require", "ensure",
  and "invariant". The latter might get retired soon.

- i32.infix / and i32.infix % now check that other != 0 via a pre-condition
  qualified with safety.

- new fields i32.max and i32.min to get the max/min value an i32 can hold.  A
  bit strange: Since i32 is a value type, we need an i32 to get the constant
  maximum value, e.g., 0.max == i32(-123).max == array.length.max.

- inline functions (lambdas declared with "fun (args) ...") now use the same
  syntax for their implementation as routine definititions: "is" block, "=>"
  block or a block enclosed in { }.

## 2020-11-01: V0.008

- website

  - Added "How to Support Fusion" page.

  - Added Rosetta Code Fibonacci example, updated other Rosetta Code examples to
    use indentation based blocks.

  - updated the idioms to use indentation instead of { }.

- Fusion language

  - ifstmnt and loop are expr now, not stmnt. This means they can be used
    directly as expressions, it is no longer needed to put them into { } to use
    them as a value.

  - added else and until as block ending tokens such that single line blocks
    that end with else or until no longer need to be enclosed in { }.

- Fusion Front End

  - Removed redundent directory listing in source dirs to improve speed.

## 2020-10-29: V0.007

- scopes for match cases: features declared in match cases are now visible only
  in the match case they were declared in.  In particular, the same variable
  name can be reused for the value we are matching.

- Fusion Tutorial

  - updated the examples in the tutorial to use indentation instead of { }
    whenever possible.  Added a copy of the tutorial with the original examples
    as "Fusion Tutorial using { }", linked from the main page.

- blocks no longer need to be enclosed by { }.  Routine declarations without { }
  require the new keyword 'is' before their code block.

- match statement no longer needs to be enclosed by { }. The right side of a
  case in a match statement is no longer an expr, but a block.  Cases are no
  longer separated by comma, but by semicolons or flat line breaks. This means
  that { } are no longer needed if line breaks and proper indentation is used.
  Single-line match clauses are a bit awkward, though, requiring { } and ';':

    v := match x a => { p; q }; b => { r; s }

  is equivalent to

    v := match x a => p; q
                 b => r; s

  is equivalent to

    v := match x a =>
                   p
                   q
                 b =>
                   r
                   s

- expressions

  - expressions are now restricted to a single line with the following
    exceptions:

    - an expression within parentheses ( ) or crochets [ ] may extend over
      several lines

    - a statement sequence within braces { } used as an expression may extend
      over several lines

    - a dot expression abc.def can have a line break before the dot.

- semicolons

  - semicolons between statements are now required for subsequent statements in
    the same block if they are in the same source line, semicolons are optional
    if statements are separated by a flat line break.

- Fusion design pages

  - Added much more detail to Blocks and Indentation page

  - Added page to Fusion toolchain modules

- Fusion Interpreter

  - improved performance by

    - caching interned types

    - checking and caching which types do not depend on generics,
      such that their actual type is always the same.

    - disabling pre- and postconditions in production (enabled by setting env
      variables PRECONDTIONS=true and POSCONDITIONS=true)

## 2020-10-22: V0.006

- feature declarations and visibility

  - field declarations using := can now mask previously declared fields with the
    same name.

  - features in unqualified accesses are now searched taking the scope
    visibility into account.

- a.b.this expression now produces a proper error message if a.b is not found.

- Fusion Interpreter

  - When processing input from stdin (which is used when clicking "run" on the
    website examplex), the interpreter now parses stmnts and not feature.  If
    input is a single statement that is a feature declaration, that feature will
    be executed as before. Otherwise, these statements will be added to the
    internal feature --universe-- and this will be executed.  This has the
    effect that many small examples no longer need a surrounding feature, e.g.,
    'stdout.println("Hello, World!")' can be executed directly now and Fusion
    scripts with shebang are possible.

  - uninitialized non-ref values are not initialzed with -555555555 to be more
    obvious when used.  DFA to detect possible uninitialized accesses is still
    not supported.

- Fusion design pages

  - Added page on visibility.

  - Extended page on strings with section on text blocks and converted it from
    wiki to html.

  - added sections on coroutines, light-weight threading, SIMD and fuom, the
    Fusion Optimizer.

- Loops

  Slight change in semantics: 'for i := <expr> do .. ' is now interpreted as
  "for i := <expr>, <expr> do .. ' and it is not allowed to assign a value to i
  anywhere else. This allows the following pattern

    for data := read()
    while data >= 0
      {
        process(data)
      }

- comments

  - New type of comments enabled: '#' as first codePoint in a line starts a
    comment that extends to the end of this line.  Now we can run fusion codes
    like scripts by starting the file with shebang, e.g.,

       #!/bin/fusion.sh

       stdout.println("Hello, World!");

    if fusion.sh starts the fusion interpreter.

  - Fixed bug in Lexer that disallowed '/' in operators. Now, operators such as
    infix -/*^-^*/- are allowed, this does not contain a comment.

- Errors

  - In case of compilation errors, suppress certain subsequent errors:

     - wrong type if if/while/until expression if type is t_ERROR,

     - missing result type in field declaration if field name is
       Lexer.ERROR_STRING (indicating a parsing error),

     - expected EOF in case of previous errors

## 2020-10-14: V0.005

- Benchmarks

  - added simple infrastructure for daily run benchmarks, accessible through the
    website.  There is currently no dedicated server to run the benchmarks, so
    there will be jitter, but this should do for now.

- Fusion Interpreter

  - Added checks that the main feature is not a field, is not abstract, not
    native and not generic such that this produces a proper error message
    instead of a Java exception.

- Fusion frontend

  - overloading for features:

    A feature may now be overloaded in the number (not the type!) of its
    arguments.  In particular, there are two features stdout.println now, one
    with no argument and one that expects a ref Object argument.

    This change also added infrastructure that will be needed for Eiffel-style
    renaming of inherited features.

  - initial assignments to fields are no longer allowed unless the field is
    declared within the code of an outer routine.  In particular, x := <expr>
    does not work. This code was never executed anyway and I do not want to
    leave the execution order as a arbitray decision of the implementation.

- Fusion library

  - renamed 'fusion.std.out.printline' as 'println', overloading the existing
    'println(ref Object)'.

  - In 'i32.infix..': Added feature 'lower' and renamed 'other' as 'upper' for
    convenience.  Extended 'infix :' to support negative steps to iterate from
    'lower' down to 'upper' (i.e, it requires 'lower >= upper'), e.g.,
    '10..0 : -1' counts from 10 down to 0.

  - added 'string.infix *' to repeat a string a given number of times.

- Fusion design pages

  - added page on blocks using braces vs. indentation

  - added page on semicolon and how and when it could be made optional

  - changed overloading page from wiki markup to html and added section on
    argument count overloading.

  - changed inheritance page from wiki markup to html and added section on
    multiple inheritance, conflicts during inheritance and possible solutions to
    resolve these conflicts.

  - added page on assignment syntax

  - added page on file name extensions

- webserver

  - improved logging

## 2020-10-05: V0.004

- lexer

  - fixed crash when comment following >>//<< ends with end-of-file instead of
    new line.

- webserver

  - added more examples to idioms 1, 12, 17, 20, 21, changed getRed into just red
    in the tutorial's match examples (Java's getter/setter frenzy apparently
    found its way deep into my unconsciousness).

  - removed user ids for logged in users, they are not needed.

- lib

  - removed Null.fu

## 2020-10-02: V0.003

- webserver

  - added pages to implement the idioms from programming-idioms.org

  - index.html template: Added 3sec timeout to reconnect EventSource in case no
    server response was encountered.  This seamms to work better than the
    previous solution of reconnecting if more than 3 requests were left without
    reply, but I am still unhappy about this manual reconnect.

- loops

  - if a loop that may succeed or fail has a VOID result type is used as an
    expression and type propagation asks it to be of type boolean, then the loop
    will result in true on successful termination (until condition is true) or
    false (otherwise: iteration was exhaustive or while condition is false).

  - index Vars now may be updated in the loop body if their declaration in the
    for-clause does not define a value for the next iteration.

  - added compiler checks that while- and until-conditions are actually boolean,
    so we now get a compile-time error instead of a runtime crash

  - added type propagation: The while- and until-conditions are expected to be
    of type bool.

- if statement

  - added compiler checks that if-conditions are actually boolean, so we now get
    a compile-time error instead of a runtime crash

  - added type propagation: The condition of an if-statement is expected to be
    of type bool.

- compile time errors

  - the front end no longer stops after a phase has shown errors but tries to
    continue until it would execute the application.  This might cause
    subsequent errors or even crashes, but it will help to make the front end
    more robust.

- abstract features

  - added runtime error that checks that with one or several abstract inner
    feature are called unless there are no calls to the abstract feature. No
    fancy data-flow analysis is done yet, so the following code

      f {
       x { abstract y }
       z : x { redefine y { } }
       x;
       r ref x = z;
       r.y;
      }

    does not compile even though the only call to y is in an instance of z.

  - added compile-time errors for the case that a feature with an implementation
    is marked as abstract

## 2020-09-30: V0.002

- renamed intrinsic features for reference comparison as >>infix ===<< and
  >>infix !==<< to avoid type errors when redefined in i32 and bool.

- Added checks that redefined features match the argument and result types of
  the original features. No variance for argument types allowed (so far),
  covariance allowed for ref result type.

- removed types int and String and last uses of int in testsuite, i32/string are
  used instead.


## 2020-09-29: V0.001

- First version to be presented to very few selected users via the flang.dev website.
