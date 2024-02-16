#!/usr/bin/env bash

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

set -euo pipefail

mkdir -p build
cd build
wget https://www.hboehm.info/gc/gc_source/gc-8.2.4.tar.gz
echo "3d0d3cdbe077403d3106bb40f0cbb563413d6efdbb2a7e1cd6886595dec48fc2 gc-8.2.4.tar.gz" | sha256sum --check --status
tar xf gc-8.2.4.tar.gz
cd gc-8.2.4
./configure --prefix=/ucrt64/ --enable-threads=pthreads
make
make install
