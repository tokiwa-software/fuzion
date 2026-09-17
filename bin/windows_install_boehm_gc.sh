#!/usr/bin/env sh

# This file is part of the Fuzion language implementation.
#
# The Fuzion language implementation is free software: you can redistribute it
# and/or modify it under the terms of the GNU General Public License as published
# by the Free Software Foundation, version 3 of the License.
#
# The Fuzion language implementation is distributed in the hope that it will be
# useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
# License for more details.
#
# You should have received a copy of the GNU General Public License along with The
# Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.


# -----------------------------------------------------------------------
#
#  Tokiwa Software GmbH, Germany
#
#  Source code of windows_install_boehm_gc.sh bash script
#
# -----------------------------------------------------------------------

set -eu

VERSION=8.2.4
TAR_BALL_HASH=3d0d3cdbe077403d3106bb40f0cbb563413d6efdbb2a7e1cd6886595dec48fc2

mkdir -p build
cd build
wget "https://www.hboehm.info/gc/gc_source/gc-$VERSION.tar.gz"
echo "$TAR_BALL_HASH gc-$VERSION.tar.gz" | sha256sum --check --status
tar xf "gc-$VERSION.tar.gz"
cd "gc-$VERSION"
# MINGW_PREFIX/MINGW_CHOST are set by the MSYS2 shell (/ucrt64, /clangarm64, ...)
./configure --prefix="${MINGW_PREFIX:-/ucrt64}/" \
            --host="${MINGW_CHOST:-x86_64-w64-mingw32}" \
            CC="${CC:-clang}" \
            --enable-threads=win32
make
make install
