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
#  Source code of run_tests.sh bash script
#
#  Author: Fridtjof Siebert (siebert@tokiwa.software)
#
# -----------------------------------------------------------------------

set -eu

# usage: run_tests.sh <build-dir> <target>
#

BUILD_DIR=$1
TARGET=$2
TESTS=$(find "$BUILD_DIR"/tests -name Makefile -print0 | xargs --null --max-args=1 dirname | sort)
VERBOSE="${VERBOSE:-""}"

rm -rf "$BUILD_DIR"/run_tests.results
rm -rf "$BUILD_DIR"/run_tests.failures

# print collected results up until interruption
trap "echo """"; cat ""$BUILD_DIR""/run_tests.results ""$BUILD_DIR""/run_tests.failures; exit 130;" INT

echo "$(echo "$TESTS" | wc -l) tests."


# get nanoseconds, with workaround for macOS
nanosec () {
  if date --help 2> /dev/null | grep nanoseconds > /dev/null; then
    date +%s%N | cut -b1-13
  else
    date +%s000000000 | cut -b1-13
  fi
}


START_TIME_TOTAL="$(nanosec)"
for test in $TESTS; do
  if test -n "$VERBOSE"; then
    printf '\nrun %s: ' "$test"
  fi
  if test -e "$test"/skip -o -e "$test"/skip_"$TARGET"; then
    printf "_"
    echo "$test: skipped" >>"$BUILD_DIR"/run_tests.results
  else
    START_TIME="$(nanosec)"
    if timeout --kill-after=600s 600s make "$TARGET" --environment-overrides --directory="$test" >"$test"/out.txt 2>"$test"/stderr.txt; then
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
done
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

  echo "To rerun all failed tests, use this command:"
  grep failed$ "$BUILD_DIR"/run_tests.results | cut -d' ' -f1 | sed 's/^/make -C /' | sed -z 's/\n/ \&\& /g'

  exit 1;
fi
