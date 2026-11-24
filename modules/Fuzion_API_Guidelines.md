# Fuzion API Guidelines

## General

### Purpose

This document gives some general guidelines on Fuzion API design helping with general rules for a
consistent API.

### Motivation

A good API design is intuitive and has few surprises.  This means that similar services offered by
different APIs should use similar names and a similar structure.  This common structure is not
always obvious, so the purpose of this document is to collect aspects of this common structure.

### Guiding Principle

For every API, the guiding principle must be the clarity and ease of use of the API for the development of high quality code.

This implies

- Simplicity of API implementation is only a minor concern in API design.

- APIs should avoid requiring redundant code, e.g, explicit types that could be inferred or redundant error handling, to be entered from the user.

- Performance and deterministic execution time, however, is an important concern.  This can anticipate future changes like compiler optimizations.

- Backward-compatibility is not a concern at this point there is no installed code base, while

- Extensibility to meet likely future needs in a backward-compatible way is.

- Safety and security are of major importance, in particulr for effects that interact with state or control flow outside of the code using it.

- Non-functional properties that are important for developed code and the API implementation that must be considered:

  - *reliability*, the code must always work correctly, even in case of errors such as missing resources

  - *maintainability*, changes to code should be simple, dependencies should be obvious

  - *computational complexity*, inefficient algorithms should be avoided

  - *determinism* in the sense that results are repeatable and timing is deterministic, e.g, the use of hash tables or unbalanced trees typically results in very nondeterministic timing with linear worst-case scenarios

  - *modularity*, APIs should be grouped in a logical way with (in the hierarchy of outer features as well as Fuzion modules) with only essential dependencies

  - *composability*, the ability to combine APIs in new ways instead of hard-coded dependencies

## Naming

### Capitalization and Underscores

Feature names should follow the suggestions for [Identifiers in the Fuzion Design
Documentation](https://fuzion-lang.dev/design/identifiers). In particular

* type parameters should be in all *UPPERCASE*, usually short `T`, `FUN`, `ELEMENT`

* features and value types should use *snake_case* such as `to_string`

* `ref` types and their constructors should use *Snake_Pascal_Case*, e.g., `Mutable_Map`

### Name prefixes or patterns

Certain categories of operations should use common names with a common style. These include

- *Value conversion*: Pattern `as_<type>` for features that provide a typically lossless conversion
  to another type. The result type of these features must be `<type>`. If a conversion is not always
  possible, a precondition with `debug:` prefix should ensure that conversion is possible.

  The resulting value should logically represent the same value as the original value (in contrast
  to the result of a cast that may result in logically different values).

  An example is `integer.as_u8` with precondition `debug: fits_in_u8`.

- *Casts*: Pattern `cast_to_<type>` implements a cast to another type that does not preserve the
  logical value, but a different value derived from the original, typically by re-interpreting the
  internal representation differently.

  A cast typically makes sense only if the underlying representation has the same bit-size before
  and after the cast.

  An example is `i8.cast_to_u8` that with `(i8 -3).cast_to_u8 = u8 253`

- *property checks*: Pattern *is_<property>*: a feature that is a predicate on the value
  tested. Result type should be `bool` or similar like `trit` to cover uncertain or other special
  case cases.

  Example: `f64.is_sign_bit_set`

- Size, length and count: A value that contains a possible fixed and finite number of other elements
  may provide these features.

  `length` is to be used for structures that are long or essentially one-dimensional line string,
  array. `size` is preferred for something that usually would not be considered one-dimensional like
  sets, trees, etc.

  `count` indicates that determining the number of elements may be expensive and require courting. It
  may even diverge if the number is infinite. In contrast to that, `size` or `length` are usually
  cheap operations.

  In case there are several interpretations of what elements a structure contains, these should be
  prefixed with the type of these elements, e.g., `String.codepoint_count`.

## Type Parameters

### Allow inference of actual type parameters

API should be designed such that type parameters need to be specified only if needed.

Example: The API to create an option from a value `67` of type `i64` and store it in a field, one would need to write

    f option i64 := 67

where `i64` is redundant.  To help with this, the API provides a helper function

    option(T : Any, o option T) option T => o

such that one can write

    f := option 67

avoiding the redundant type parameter.

<!--
LocalWords:  Fuzion APIs bool trit codepoint modularity composability
 -->
