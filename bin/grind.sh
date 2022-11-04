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
#  source code of bash script grind
#
#  Author: Michael Lill (michael.lill@tokiwa.software)
#
# -----------------------------------------------------------------------


set -euo pipefail

mkdir -p "$HOME/.cache"

TOOL=
if [ "$1" = "callgrind" ] || [ "$1" = "cachegrind" ]
then
  TOOL=$1
else
  echo "first param needs to be either: callgrind or cachegrind"
  exit 1
fi

# compile
fz -c -o=/home/sam/.cache/fz_c_out "${@:2}"

# grind
valgrind --tool="$TOOL" "--$TOOL-out-file=$HOME/.cache/fz_c_out.grind" "$HOME/.cache/fz_c_out"

# display
kcachegrind "$HOME/.cache/fz_c_out.grind"
