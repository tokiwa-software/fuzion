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
#  Source code of check_simple_example.sh script, runs simple test using the
#  jvm backend
#
#  Author: Fridtjof Siebert (siebert@tokiwa.software)
#
# -----------------------------------------------------------------------

set -euo pipefail

# Run the fuzion example given as an argument $2 and compare the stdout/stderr
# output to $2.effect_out and $2.effect_err.
#
# The fz command is given as argument $1
#
# In case file $2.skip exists, do not run the example
#

if [ -f "$2".effect ]; then
    printf 'RUN %s ' "$2"
    unset OPT
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=.*$"     && export OPT=-Dfuzion.debugLevel=10
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=9( .*)$" && export OPT=-Dfuzion.debugLevel=9
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=8( .*)$" && export OPT=-Dfuzion.debugLevel=8
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=7( .*)$" && export OPT=-Dfuzion.debugLevel=7
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=6( .*)$" && export OPT=-Dfuzion.debugLevel=6
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=5( .*)$" && export OPT=-Dfuzion.debugLevel=5
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=4( .*)$" && export OPT=-Dfuzion.debugLevel=4
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=3( .*)$" && export OPT=-Dfuzion.debugLevel=3
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=2( .*)$" && export OPT=-Dfuzion.debugLevel=2
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=1( .*)$" && export OPT=-Dfuzion.debugLevel=1
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=0( .*)$" && export OPT=-Dfuzion.debugLevel=0

    # limit cpu time for executing test
    ulimit -S -t 120 || echo "failed setting limit via ulimit"

    EXIT_CODE=$(FUZION_DISABLE_ANSI_ESCAPES=true FUZION_JAVA_OPTIONS="${FUZION_JAVA_OPTIONS="-Xss${FUZION_JAVA_STACK_SIZE=5m}"} ${OPT:-}" $1 -XmaxErrors=-1 -effects "$2" >tmp_out.txt; echo $?)

    if [ "$EXIT_CODE" -ne 0 ] && [ "$EXIT_CODE" -ne 1 ]; then
        echo "unexpected exit code"
        exit "$EXIT_CODE"
    fi

    expout=$2.effect

    # NYI: workaround for #2586
    if [ "${OS-default}" = "Windows_NT" ]; then
        iconv --unicode-subst="?"  --byte-subst="?" --widechar-subst="?" -f utf-8 -t ascii "$expout" > tmp_conv.txt || false && cp tmp_conv.txt "$expout"
        iconv --unicode-subst="?"  --byte-subst="?" --widechar-subst="?" -f utf-8 -t ascii tmp_out.txt > tmp_conv.txt || false && cp tmp_conv.txt tmp_out.txt
    fi

    sort "$expout"   >tmp_expout_sorted.txt
    sort tmp_out.txt >tmp_out_sorted.txt
    FAILED="none" # "out" or "err" or "none"
    diff --strip-trailing-cr tmp_expout_sorted.txt tmp_out_sorted.txt || FAILED="out"; true
    rm tmp_expout_sorted.txt tmp_out_sorted.txt tmp_out.txt
    if [ "$FAILED" = "none" ]; then
        echo -e "\033[32;1mPASSED\033[0m."
    else
        echo -e "\033[31;1m*** FAILED\033[0m $FAILED on $2"
        exit 1
    fi
else
    echo "SKIP $2"
fi
