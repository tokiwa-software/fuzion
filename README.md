# <img src="assets/logo.svg" alt="fuzion logo" width="25" /> Fuzion

[![OpenSSF
Scorecard](https://api.securityscorecards.dev/projects/github.com/tokiwa-software/fuzion/badge)](https://api.securityscorecards.dev/projects/github.com/tokiwa-software/fuzion)
[![run tests on linux](https://github.com/tokiwa-software/fuzion/actions/workflows/linux.yml/badge.svg)](https://github.com/tokiwa-software/fuzion/actions/workflows/linux.yml)
[![run tests on macOS](https://github.com/tokiwa-software/fuzion/actions/workflows/apple.yml/badge.svg)](https://github.com/tokiwa-software/fuzion/actions/workflows/apple.yml)
[![run tests on windows](https://github.com/tokiwa-software/fuzion/actions/workflows/windows.yml/badge.svg)](https://github.com/tokiwa-software/fuzion/actions/workflows/windows.yml)


## A language with a focus on simplicity, safety and correctness.

> Please note that this language is work in progress.

---

<!--ts-->
   * [Examples](#examples)
   * [Documentation](#documentation)
   * [Clone](#clone)
   * [Required packages](#required-packages)
     * [Linux](#linux)
     * [MacOS](#macos)
     * [Windows](#windows)
   * [Build](#build)
   * [Run](#run)
   * [Install prebuilt](#install-prebuilt)
<!--te-->

---

## Examples

```
hello_world is

  # first we define a custom mutate effect.
  # we will need this for buffered reading from stdin
  #
  lm : mutate is

  # calling `lm` creates an instance of our mutate effect,
  # `instate_self` is then used to instate this instance and
  # run code in the context of the instated effect.
  #
  lm.instate_self ()->

    # read someone's name from standard input
    #
    get_name =>
      match ((io.stdin lm).try String ()->
                io.buffered.read_line lm ? str String => str | io.end_of_file => "")
        name String => name
        e error => panic "Could not get your name!"

    # greet someone with the name given
    #
    greet(name String) is
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

```
ex_gcd is

  # return common divisors of a and b
  #
  common_divisors_of(a, b i32) =>
    max := max a.abs b.abs
    (1..max).flat_map i32 i->
      if (a % i = 0) && (b % i = 0)
        [-i, i]
      else
        []


  # find the greatest common divisor of a and b
  #
  gcd(a, b i32)
    pre
      safety: (a != 0 || b != 0)
    post
      safety: a % result = 0
      safety: b % result = 0
      pedantic: (common_divisors_of a b).reduce bool true (acc,cur -> acc && (result % cur = 0))
  =>
    if b = 0 then a else gcd b (a % b)


  say (gcd 8 12)
  say (gcd -8 12)
  say (gcd 28 0)
```

This example implements a simple variant of an algorithm that finds the greatest
common divisor of two numbers. However, it also demonstrates one of Fuzion's
notable features: design by contract. By specifying pre- and postconditions for
features, correctness checks are made possible.

```
generator_effect is
  # define a generator effect with a yield operation
  #
  gen(T type,
      yield T->unit    # yield is called by code to yield values
      ) : effect is

  # traverse a list and yield the elements
  #
  list.traverse unit =>
    match list.this
      c Cons => (generator_effect.gen A).env.yield c.head; c.tail.traverse
      nil =>

  # bind the yield operation dynamically
  #
  (gen i32 (i -> say "yielded $i")).instate_self ()->
    [0,8,15].as_list.traverse
```

Another major concept in Fuzion is that of the
*[algebraic effect](https://en.wikipedia.org/wiki/Effect_system)* - a new
approach to encapsulating code with side effects in a safe way.

In the example above, a custom *effect* has been used to implement a generator
with a `yield` operation. In some other languages, this requires a keyword
`yield` to be provided by the language, but in Fuzion this can be implemented
without language support.

If you want to play around with Fuzion, try the
[interactive tutorial](https://fuzion-lang.dev/tutorial/index).

## Documentation

Check [fuzion-lang.dev](https://fuzion-lang.dev) for language and implementation design.


## Clone

> Note that the current directory must not contain any spaces.

    git clone https://github.com/tokiwa-software/fuzion

## Required packages

### Linux

> For Debian based systems this command should install all requirements:
>
>     sudo apt-get install make clang libgc1 libgc-dev openjdk-21-jdk

- OpenJDK 21, e.g. [Adoptium](https://github.com/adoptium/temurin21-binaries/releases/)
- clang LLVM C compiler
- GNU make
- libgc

### MacOS

> This command should install all requirements:
>
>     brew install bdw-gc gnu-sed make temurin llvm
>
> Additionally you may need to update your PATH environment variable e.g.:
>
>     export PATH:"/usr/local/opt/gnu-sed/libexec/gnubin:/usr/local/opt/gnu-make/libexec/gnubin:$PATH"

- OpenJDK 21, e.g. [Adoptium](https://github.com/adoptium/temurin21-binaries/releases/)
- clang LLVM C compiler
- GNU make
- libgc


### Windows

> Note that building from powershell/cmd does not work yet.

1) Install chocolatey: [chocolatey.org](https://chocolatey.org/install)
2) In Powershell:
    1) choco install git openjdk make msys2 diffutils
    2) [Environment]::SetEnvironmentVariable("Path","c:\tools\msys64\ucrt64\bin;" + $env:Path , "User")
3) In file C:\tools\msys64\msys2_shell.cmd change line: 'rem set MSYS2_PATH_TYPE=inherit' to 'set MSYS2_PATH_TYPE=inherit'
4) In msys2 shell (execute C:\tools\msys64\msys2_shell.cmd):
    1) pacman -S mingw-w64-x86_64-clang
    2) make
5) execute ./bin/windows_install_boehm_gc.sh

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

## Install prebuilt

[![Packaging status](https://repology.org/badge/vertical-allrepos/fuzion.svg)](https://repology.org/project/fuzion/versions)
