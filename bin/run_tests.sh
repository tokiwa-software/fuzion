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
#  Source code of run_tests.sh bash script
#
#  Author: Fridtjof Siebert (siebert@tokiwa.software)
#
# -----------------------------------------------------------------------

set -euo pipefail

# usage: run_tests.sh <build-dir> <target>
#

BUILD_DIR=$1
TARGET=$2
TESTS=$(echo "$BUILD_DIR"/tests/*/)
VERBOSE="${VERBOSE:-""}"

rm -rf "$BUILD_DIR"/run_tests.results
rm -rf "$BUILD_DIR"/run_tests.failures

# print collected results up until interruption
trap "echo """"; cat ""$BUILD_DIR""/run_tests.results ""$BUILD_DIR""/run_tests.failures; exit 130;" INT

for test in $TESTS; do
  if test -n "$VERBOSE"; then
    echo -en "\nrun $test: "
  fi
  if test -e "$test"/skip -o -e "$test"/skip_"$TARGET"; then
    echo -n "_"
    echo "$test: skipped" >>"$BUILD_DIR"/run_tests.results
  else
    START_TIME=$(date +%s%N | cut -b1-13)
    if make "$TARGET" -e -C "$test" >"$test"/out.txt 2>"$test"/stderr.txt; then
        echo -n "."
        echo "$test: ok"     >>"$BUILD_DIR"/run_tests.results
    else
        echo -n "#"
        echo "$test: failed" >>"$BUILD_DIR"/run_tests.results
        cat "$test"/out.txt "$test"/stderr.txt >>"$BUILD_DIR"/run_tests.failures
    fi
    END_TIME=$(date +%s%N | cut -b1-13)
    if test -n "$VERBOSE"; then
      echo -en " time: $((END_TIME-START_TIME))ms"
    fi
  fi
done

OK=$(     grep --count ok$      "$BUILD_DIR"/run_tests.results || true)
SKIPPED=$(grep --count skipped$ "$BUILD_DIR"/run_tests.results || true)
FAILED=$( grep --count failed$  "$BUILD_DIR"/run_tests.results || true)

echo -n " $OK/$(echo "$TESTS" | wc -w) tests passed,"
echo -n " $SKIPPED skipped,"
echo    " $FAILED failed."
grep failed$ "$BUILD_DIR"/run_tests.results || echo -n

if [ "$FAILED" -ge 1 ]; then
  cat "$BUILD_DIR"/run_tests.failures
  exit 1;
fi
