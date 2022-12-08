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

# lower priority to prevent system getting unresponsive
renice -n 19 $$ > /dev/null

BUILD_DIR=$1
TARGET=$2
TESTS=$(echo "$BUILD_DIR"/tests/*/)
VERBOSE="${VERBOSE:-""}"

rm -rf "$BUILD_DIR"/run_tests.results

# print collected results up until interruption
trap "echo """"; cat ""$BUILD_DIR""/run_tests.results ""$BUILD_DIR""/run_tests.failures; exit 130;" INT

N=$(nproc --all || echo 1)
open_sem "$N"

for test in $TESTS; do
  task(){
    if test -n "$VERBOSE"; then
      echo -en "\nrun $test: "
    fi
    if test -e "$test"/skip -o -e "$test"/skip_"$TARGET"; then
      echo -n "_"
      echo "$test: skipped" >>"$BUILD_DIR"/run_tests.results
    else
      if make "$TARGET" -e -C "$test" >"$test"/out.txt 2>"$test"/stderr.txt; then
        echo -n "."
        echo "$test: ok"     >>"$BUILD_DIR"/run_tests.results
      else
        echo -n "#"
        echo "$test: failed" >>"$BUILD_DIR"/run_tests.results
        cat "$test"/out.txt "$test"/stderr.txt >>"$BUILD_DIR"/run_tests.failures
      fi
    fi
  }
  run_with_lock task
done
wait

echo -n " $(grep --count ok$ "$BUILD_DIR"/run_tests.results || true)/$(echo "$TESTS" | wc -w) tests passed,"
echo -n " $(grep --count skipped$ "$BUILD_DIR"/run_tests.results || true) skipped,"
echo    " $(grep --count failed$ "$BUILD_DIR"/run_tests.results || true) failed."
if grep failed$ "$BUILD_DIR"/run_tests.results; then
  cat "$BUILD_DIR"/run_tests.failures
fi
