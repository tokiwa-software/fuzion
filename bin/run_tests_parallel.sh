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
#  Source code of run_tests_parallel.sh bash script
#
#  Author: Fridtjof Siebert (siebert@tokiwa.software)
#
# -----------------------------------------------------------------------

set -euo pipefail

# usage: run_tests_parallel.sh <build-dir> <target>
#

# source: https://newbedev.com/parallelize-a-bash-for-loop
# initialize a semaphore with a given number of tokens
open_sem(){
    mkfifo pipe-$$
    exec 3<>pipe-$$
    rm pipe-$$
    local i=$1
    for((;i>0;i--)); do
        printf %s 000 >&3
    done
}

# source: https://newbedev.com/parallelize-a-bash-for-loop
# run the given command asynchronously and pop/push tokens
run_with_lock(){
    local x
    # this read waits until there is something to read
    read -r -u 3 -n 3 x && ((0==x)) || exit "$x"
    (
     ( "$@"; )
    # push the return code of the command to the semaphore
    printf '%.3d' $? >&3
    )&
}

# source: https://stackoverflow.com/questions/45181115/portable-way-to-find-the-number-of-processors-cpus-in-a-shell-script
portable_nproc() {
    OS="$(uname -s)"
    if [ "$OS" = "Linux" ]; then
        NPROCS="$(nproc --all)"
    elif [ "$OS" = "Darwin" ] || \
         [ "$(echo "$OS" | grep -q BSD)" = "BSD" ]; then
        NPROCS="$(sysctl -n hw.ncpu)"
    else
        NPROCS="$(getconf _NPROCESSORS_ONLN || echo 4)"  # glibc/coreutils fallback
    fi
    echo "$NPROCS"
}

# get nanoseconds, with workaround for macOS
nanosec () {
  if date --help 2> /dev/null | grep nanoseconds > /dev/null; then
    date +%s%N | cut -b1-13
  else
    date +%s000000000 | cut -b1-13
  fi
}


# lower priority to prevent system getting unresponsive
renice -n 19 $$ > /dev/null

BUILD_DIR=$1
TARGET=$2
TESTS=$(find "$BUILD_DIR"/tests -name Makefile -print0 | xargs -0 -n1 dirname | sort)
VERBOSE="${VERBOSE:-""}"

rm -rf "$BUILD_DIR"/run_tests.results
rm -rf "$BUILD_DIR"/run_tests.failures

# print collected results up until interruption
trap "echo """"; cat ""$BUILD_DIR""/run_tests.results ""$BUILD_DIR""/run_tests.failures; exit 130;" INT

N=$(($(portable_nproc) > 6 ? 6 : $(portable_nproc)))

echo "$(echo "$TESTS" | wc -l) tests, running $N tests in parallel."

open_sem "$N"

START_TIME_TOTAL="$(nanosec)"
for test in $TESTS; do
  task(){
    if test -n "$VERBOSE"; then
      printf '\nrun %s: ' "$test"
    fi
    if test -e "$test"/skip -o -e "$test"/skip_"$TARGET"; then
      printf "_"
      echo "$test: skipped" >>"$BUILD_DIR"/run_tests.results
    else
      START_TIME="$(nanosec)"
      if make "$TARGET" -e -C "$test" >"$test"/out.txt 2>"$test"/stderr.txt; then
         TEST_RESULT=true
      else
         TEST_RESULT=false
      fi
      END_TIME="$(nanosec)"
      if $TEST_RESULT; then
        printf "."
        echo "$test in $((END_TIME-START_TIME))ms: ok"     >>"$BUILD_DIR"/run_tests.results
      else
        printf "#"
        echo "$test in $((END_TIME-START_TIME))ms: failed" >>"$BUILD_DIR"/run_tests.results
        cat "$test"/out.txt "$test"/stderr.txt >>"$BUILD_DIR"/run_tests.failures
      fi
    fi
  }
  run_with_lock task
done
wait
END_TIME_TOTAL="$(nanosec)"

OK=$(     grep --count ok$      "$BUILD_DIR"/run_tests.results || true)
SKIPPED=$(grep --count skipped$ "$BUILD_DIR"/run_tests.results || true)
FAILED=$( grep --count failed$  "$BUILD_DIR"/run_tests.results || true)

RESULT=" $OK/$(echo "$TESTS" | wc -w) tests passed,"
printf '%s' "$RESULT"
printf ' %s skipped,' "$SKIPPED"
echo    " $FAILED failed in $((END_TIME_TOTAL-START_TIME_TOTAL))ms."
grep failed$ "$BUILD_DIR"/run_tests.results || true

if [ "$FAILED" -ge 1 ]; then
  cat "$BUILD_DIR"/run_tests.failures
  exit 1;
fi
