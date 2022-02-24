#!/bin/bash

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


# usage: run_tests.sh <build-dir> <target>
#

BUILD_DIR=$1
TARGET=$2
TESTS=$(echo "$BUILD_DIR"/tests/*/)
rm -rf "$BUILD_DIR"/run_tests.results
for test in $TESTS; do
  if test -n "$VERBOSE"; then
    echo -en "\nrun $test: "
  fi
  if test -e "$test"/skip -o -e "$test"/skip_"$TARGET"; then
    echo -n "_"
    echo "$test: skipped" >>"$BUILD_DIR"/run_tests.results
  else
      make "$TARGET" -e -C >"$test"/out.txt "$test" 2>/dev/null \
          && (echo -n "." && echo "$test: ok"     >>"$BUILD_DIR"/run_tests.results) \
          || (echo -n "#" && echo "$test: failed" >>"$BUILD_DIR"/run_tests.results)
fi
done

OK=$(     cat "$BUILD_DIR"/run_tests.results | grep --count ok$     )
SKIPPED=$(cat "$BUILD_DIR"/run_tests.results | grep --count skipped$)
FAILED=$( cat "$BUILD_DIR"/run_tests.results | grep --count failed$ )

echo -n " $OK/$(echo "$TESTS" | wc -w) tests passed,"
echo -n " $SKIPPED skipped,"
echo    " $FAILED failed."
cat "$BUILD_DIR"/run_tests.results | grep failed$ || echo -n

if [ "$FAILED" -ge 1 ]; then
  exit 1;
fi
