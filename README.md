# <img src="assets/logo.svg" alt="fuzion logo" width="25" /> Fuzion

[![OpenSSF
Scorecard](https://api.securityscorecards.dev/projects/github.com/tokiwa-software/fuzion/badge)](https://api.securityscorecards.dev/projects/github.com/tokiwa-software/fuzion)

## A language with a focus on simplicity, safety and correctness.

> Please note that this language is work in progress.

---

<!--ts-->
   * [Examples](#examples)
   * [Documentation](#documentation)
   * [Clone](#clone)
   * [Requirements](#requirements)
     * [Windows](#windows)
   * [Build](#build)
   * [Run](#run)
<!--te-->

---

## Examples

```
hello_world is
  # read someone's name from standard input
  #
  get_name String
    post
      result != "Fuzion"
  is
    io.stdin.read_line ? name String => name
                       | e error => panic "Could not get your name!"

  # greet someone with the name given
  #
  greet(name String)
    pre
      name != "World"
  is
    say "Hello, {name}!"

  # greet the user
  #
  x := greet get_name

  # you can access any feature - even argument features of other features
  # from outside
  #
  say "How are you, {x.name}?"
```

This `hello_world` example demonstrates one important concept in Fuzion quite
well: Everything is a *feature*. *Features* are Fuzion's response to the mess
that is created by *classes*, *methods*, *interfaces*, and various other
concepts in other programming languages. Since everything is a feature, the
programmer does not need to care and the compiler will do this work. As you can
see, it is even possible to access the argument features of some feature from
outside.

The example also demonstrates pre- and postconditions in Fuzion. The result of
the `get_name` feature should not be `Fuzion`, and the name given to `greet`
should not be `World`. Pre- or postcondition failures will cause the program to
panic.

```
generator_effect is
  # define a generator effect with a yield operation
  #
  gen(T type,
      yield T->unit    # yield is called by code to yield values
      ) : simpleEffect is

  # traverse a list and yield the elements
  #
  list.traverse unit is
    match list.this
      c Cons => (example.gen A).env.yield c.head; c.tail.traverse
      nil =>

  # bind the yield operation dynamically
  #
  (gen i32 (i -> say "yielded $i")).use (()->
    [0,8,15].as_list.traverse)
```

Another major concept in Fuzion is that of the
*[algebraic effect](https://en.wikipedia.org/wiki/Effect_system)* - a new
approach to encapsulating code with side effects in a safe way.

In the example above, a custom *effect* has been used to implement a generator
with a `yield` operation. In some other languages, this requires a keyword
`yield` to be provided by the language, but in Fuzion this can be implemented
without language support.

If you want to play around with Fuzion, try the
[interactive tutorial](https://flang.dev/tutorial/index).

## Documentation

Check [flang.dev](https://flang.dev) for language and implementation design.


## Clone

> Note that the current directory must not contain any spaces.

    git clone https://github.com/tokiwa-software/fuzion

## Requirements

> For Debian based systems this command should install all requirements:
>
>     sudo apt-get install make clang libgc1 libgc-dev openjdk-17-jdk

- OpenJDK 17, e.g. [Adoptium](https://github.com/adoptium/temurin17-binaries/releases/)
- clang LLVM C compiler
- GNU make
- libgc

### Windows

> Note that building from powershell/cmd does not work yet.

1) Install chocolatey: [chocolatey.org](https://chocolatey.org/install)
2) In Powershell:
    1) choco install git openjdk make msys2 diffutils
    2) [Environment]::SetEnvironmentVariable("Path","c:\tools\msys64\mingw64\bin;" + $env:Path , "User")
3) In file C:\tools\msys64\msys2_shell.cmd change line: 'rem set MSYS2_PATH_TYPE=inherit' to 'set MSYS2_PATH_TYPE=inherit'
4) In msys2 shell (execute C:\tools\msys64\msys2_shell.cmd):
    1) pacman -S mingw-w64-x86_64-clang
    2) make

## Build

> Make sure java/javac and clang are in your $PATH.

    cd fuzion
    make

You should have a folder called **build** now.

## Run

    cd build
    export PATH=$PWD/bin:$PATH
    cd tests/rosettacode_factors_of_an_integer
    fz factors

To compile the same example (requires clang C compiler):

    fz -c factors
    ./factors

Have fun!
