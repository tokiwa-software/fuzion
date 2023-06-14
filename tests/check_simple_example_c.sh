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
#  Source code of check_simple_example_c.sh script, runs simple test using C
#  backend
#
#  Author: Fridtjof Siebert (siebert@tokiwa.software)
#
# -----------------------------------------------------------------------

set -euo pipefail

# Run the fuzion example given as an argument $2 using the C backend and compare
# the stdout/stderr output to $2.expected_out and $2.expected_err.
#
# The fz command is given as argument $1
#
# In case file $2.skip exists, do not run the example
#

SCRIPTPATH="$(dirname "$(readlink -f "$0")")"
CURDIR=$("$SCRIPTPATH"/_cur_dir.sh)

RC=0
if [ -f "$2".skip ]; then
    echo "SKIP $2"
else
    echo -n "RUN $2 "
    unset OPT
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=.*$"      && export OPT=-Dfuzion.debugLevel=10
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=9( .*|)$" && export OPT=-Dfuzion.debugLevel=9
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=8( .*|)$" && export OPT=-Dfuzion.debugLevel=8
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=7( .*|)$" && export OPT=-Dfuzion.debugLevel=7
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=6( .*|)$" && export OPT=-Dfuzion.debugLevel=6
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=5( .*|)$" && export OPT=-Dfuzion.debugLevel=5
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=4( .*|)$" && export OPT=-Dfuzion.debugLevel=4
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=3( .*|)$" && export OPT=-Dfuzion.debugLevel=3
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=2( .*|)$" && export OPT=-Dfuzion.debugLevel=2
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=1( .*|)$" && export OPT=-Dfuzion.debugLevel=1
    head -n 1 "$2" | grep -q -E "# fuzion.debugLevel=0( .*|)$" && export OPT=-Dfuzion.debugLevel=0

    rm -f testbin

    ( (FUZION_JAVA_OPTIONS="${FUZION_JAVA_OPTIONS="-Xss${FUZION_JAVA_STACK_SIZE=5m}"} ${OPT:-}" $1 -c "$FUZION_BACKEND_C_ARGS" "$2" -o=testbin                && ./testbin) 2>tmp_err.txt | head -n 100) >tmp_out.txt || true # tail my result in 141

    # This version dumps stderr output if fz was successful, which essentially ignores C compiler warnings:
    # (($1 -c "$FUZION_BACKEND_C_ARGS" $2 -o=testbin 2>tmp_err0.txt && ./testbin  2>tmp_err0.txt | head -n 100) >tmp_out.txt || true # tail my result in 141

    sed -i "s|${CURDIR//\\//}/|--CURDIR--/|g" tmp_err.txt

    expout=$2.expected_out
    experr=$2.expected_err
    if [ -f "$2".expected_out_c ]; then
        expout=$2.expected_out_c
    fi
    if [ -f "$2".expected_err_c ]; then
        experr=$2.expected_err_c
    fi
    head -n 100 "$expout" >tmp_exp_out.txt
    if diff tmp_exp_out.txt tmp_out.txt; then
        if diff "$experr" tmp_err.txt >/dev/null; then
            echo -ne "\033[32;1mPASSED\033[0m."
        else
            if [ -s "$experr" ] && [ -s tmp_err.txt ]; then
                echo -ne "\033[33;1mDIFF IN STDERR\033[0m."
            else
                diff "$experr" tmp_err.txt
                echo -e "\033[31;1m*** FAILED\033[0m err on $2"
                RC=1
            fi
        fi
    else
        diff "$experr" tmp_err.txt
        echo -e "\033[31;1m*** FAILED\033[0m out on $2"
        RC=1
    fi
    if [ -f testbin ]; then
        echo " (binary)"
    else
        echo " (no binary)"
    fi
    rm -f tmp_out.txt tmp_err.txt tmp_exp_out.txt testbin testbin.c
fi
exit $RC
