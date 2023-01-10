# <img src="assets/logo.svg" alt="fuzion logo" width="25" /> Fuzion

## A language with a focus on simplicity, safety and correctness.

> Please note that this language is work in progress.

---

<!--ts-->
   * [Documentation](#documentation)
   * [Clone](#clone)
   * [Requirements](#requirements)
     * [Windows](#windows)
   * [Build](#build)
   * [Run](#run)
<!--te-->

---

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
