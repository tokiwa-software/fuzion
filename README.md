# The Fuzion language implementation

This is work under development, documentation is mostly not existing.

Please check [https://flang.dev](https://flang.dev) for language and implementation design.

## Clone
> Note that the current directory must not contain any spaces.

    git clone https://github.com/fridis/fuzion

## Requirements

- OpenJDK 16, e.g., https://adoptopenjdk.net/releases.html?variant=openjdk16&jvmVariant=hotspot
- clang-10 LLVM C compiler (on ubuntu: sudo apt-get install clang-10)

## Build

> Make sure java/javac commands from OpenJDK 16 and clang 10 binary are in $PATH.

    cd fuzion
    make

alternatively, you can build in another directory and use

    make -f <fuzion-dir>/fuzion/Makefile

## Run

    cd build
    export PATH=$PWD/bin:$PATH
    cd tests/rosettacode_factors_of_an_integer
    fz factors

To compile the same example (requires clang C compiler):

    fz -c factors
    ./factors

Have fun!
