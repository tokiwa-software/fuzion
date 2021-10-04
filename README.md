
# <img src="assets/logo.svg" alt="fuzion logo" width="25" /> The Fuzion language implementation

This is work under development, documentation is mostly not existing.

Please check [https://flang.dev](https://flang.dev) for language and implementation design.

## Clone
> Note that the current directory must not contain any spaces.

    git clone https://github.com/fridis/fuzion

## Requirements

- OpenJDK 17, e.g., https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17%2B35/OpenJDK17-jdk_x64_linux_hotspot_17_35.tar.gz
- clang-10 LLVM C compiler (on ubuntu: sudo apt-get install clang-10)
- GNU make

## Windows
> Note that building from powershell/cmd does not work yet.

1) Install chocolatey: https://chocolatey.org/install
2) In Powershell:
    1) choco install git openjdk make msys2 diffutils
    2) [Environment]::SetEnvironmentVariable("Path","c:\tools\msys64\mingw64\bin;" + $env:Path , "User")
3) In file C:\tools\msys64\msys2_shell.cmd change line: 'rem set MSYS2_PATH_TYPE=inherit' to 'set MSYS2_PATH_TYPE=inherit'
4) In msys2 shell (execute C:\tools\msys64\msys2_shell.cmd):
    1) pacman -S mingw-w64-x86_64-clang
    2) make

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
