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
#  source code of bash script profile_fz
#
#  Author: Michael Lill (michael.lill@tokiwa.software)
#
# -----------------------------------------------------------------------


set -euo pipefail

if test "$(cat /proc/sys/kernel/perf_event_paranoid)" -ge 2
then
  echo "please set kernel parameter: perf event paranoid"
  echo "example: sudo sysctl kernel.perf_event_paranoid=1"
  exit 1
fi

if [ -v XDG_CACHE_HOME ]; then
  mkdir -p "$XDG_CACHE_HOME"
else
  echo "XDG_CACHE_HOME is not set. please set it."
  exit 1
fi

SCRIPTPATH="$(dirname "$(readlink -f "$0")")"

if [[ -d "$XDG_CACHE_HOME/async-profiler" ]]
then
  git -C "$XDG_CACHE_HOME/async-profiler" fetch
  git -C "$XDG_CACHE_HOME/async-profiler" pull
else
  mkdir -p "$XDG_CACHE_HOME"
  git clone https://github.com/jvm-profiling-tools/async-profiler "$XDG_CACHE_HOME/async-profiler"
fi

make -C "$XDG_CACHE_HOME/async-profiler"

HTML_FILE=/tmp/$(date +%y%m%d-%H%M%S)_flamegraph.html

FUZION_JAVA_OPTIONS="-agentpath:$XDG_CACHE_HOME/async-profiler/build/libasyncProfiler.so=start,event=cpu,file=$HTML_FILE"
export FUZION_JAVA_OPTIONS

"$SCRIPTPATH/../build/bin/fz" "$@"

xdg-open "$HTML_FILE"
