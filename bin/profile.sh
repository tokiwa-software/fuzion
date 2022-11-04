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

if test "$(cat /proc/sys/kernel/perf_event_paranoid)" -eq 0
then
  echo "please set kernel parameter: perf event paranoid"
  echo "example: sudo sysctl kernel.perf_event_paranoid=1"
  exit 1
fi

SCRIPTPATH="$(dirname "$(readlink -f "$0")")"

if [[ -d "$HOME/.cache/async-profiler" ]]
then
  git -C "$HOME/.cache/async-profiler" fetch
  git -C "$HOME/.cache/async-profiler" pull
else
  mkdir -p "$HOME/.cache"
  git clone https://github.com/jvm-profiling-tools/async-profiler "$HOME/.cache/async-profiler"
fi

make -C "$HOME/.cache/async-profiler"

HTML_FILE=/tmp/$(date +%y%m%d-%H%M%S)_flamegraph.html

FUZION_JAVA_OPTIONS=-agentpath:"$HOME"/.cache/async-profiler/build/libasyncProfiler.so=start,event=cpu,file="$HTML_FILE"
export FUZION_JAVA_OPTIONS

"$SCRIPTPATH/../build/bin/fz" "$@"

x-www-browser "$HTML_FILE"
