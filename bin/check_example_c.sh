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
#  shell script to check a code example.
#
# -----------------------------------------------------------------------


#
# Run the fuzion example given as an argument $1 and compare the stdout/stderr
# output to $1.expected_out and $1.expected_err.
#
# In case file $1.skip exists, do not run the example
#

RECORD_BIN=$(dirname "$0")

: "${FUZION_HOME=$(readlink -f "$RECORD_BIN/../build")}"

if ! [[ -f "$FUZION_HOME/bin/fz" ]]; then
    echo "fz not found! Searched in: $FUZION_HOME/bin"
    exit 1;
fi

if [ -z ${1+x} ]; then
    echo "args wrong"
    exit 1
fi

if [ -f "$1".skip ]; then
    echo "SKIP $1"
else
    echo -n "RUN $1 "
    unset OPT
    head -n 1 "$1" | grep -q -E "# fuzion.debugLevel=.*$"      && export OPT=-Dfuzion.debugLevel=10
    head -n 1 "$1" | grep -q -E "# fuzion.debugLevel=9( .*|)$" && export OPT=-Dfuzion.debugLevel=9
    head -n 1 "$1" | grep -q -E "# fuzion.debugLevel=8( .*|)$" && export OPT=-Dfuzion.debugLevel=8
    head -n 1 "$1" | grep -q -E "# fuzion.debugLevel=7( .*|)$" && export OPT=-Dfuzion.debugLevel=7
    head -n 1 "$1" | grep -q -E "# fuzion.debugLevel=6( .*|)$" && export OPT=-Dfuzion.debugLevel=6
    head -n 1 "$1" | grep -q -E "# fuzion.debugLevel=5( .*|)$" && export OPT=-Dfuzion.debugLevel=5
    head -n 1 "$1" | grep -q -E "# fuzion.debugLevel=4( .*|)$" && export OPT=-Dfuzion.debugLevel=4
    head -n 1 "$1" | grep -q -E "# fuzion.debugLevel=3( .*|)$" && export OPT=-Dfuzion.debugLevel=3
    head -n 1 "$1" | grep -q -E "# fuzion.debugLevel=2( .*|)$" && export OPT=-Dfuzion.debugLevel=2
    head -n 1 "$1" | grep -q -E "# fuzion.debugLevel=1( .*|)$" && export OPT=-Dfuzion.debugLevel=1
    head -n 1 "$1" | grep -q -E "# fuzion.debugLevel=0( .*|)$" && export OPT=-Dfuzion.debugLevel=0

    rm -f testbin

    # NYI: Use this version to check there are no warnings produced by C compiler:
    ( (java -Xss16m -Xmx512m -cp "$FUZION_HOME"/classes -Dfuzion.home="$FUZION_HOME"/ $OPT dev.flang.tools.Fuzion -c -o=testbin - && FUZION_RANDOM_SEED=13 ./testbin < "$1") 2>tmp_err.txt | head -n 1000) >tmp_out.txt;

    # This version dumps stderr output if fz was successful, which essentially ignores C compiler warnings:
    # (cat $1 | java -Xss16m -Xmx512m -cp build/classes $OPT dev.flang.tools.Fuzion -c -o=testbin - 2>tmp_err.txt && ./testbin 2>tmp_err.txt | head -n 100) >tmp_out.txt;

    expout=$1.expected_out
    experr=$1.expected_err
    if [ -f "$1".expected_out_c ]; then
        expout=$1.expected_out_c
    fi
    if [ -f "$1".expected_err_c ]; then
        experr=$1.expected_err_c
    fi
    RESULT="FAIL"
    if [ -f "$expout" ]; then
        head -n 1000 "$expout" >tmp_exp_out.txt
        if diff tmp_exp_out.txt tmp_out.txt; then
            if diff "$experr" tmp_err.txt >/dev/null; then
                echo -ne "\033[32;1mPASSED\033[0m."
                RESULT="PASSED"
            else
                if [ -s "$experr" ] && [ -s tmp_err.txt ]; then
                    echo -ne "\033[33;1mDIFF IN STDERR\033[0m."
                else
                    diff "$experr" tmp_err.txt
                    echo -e "\033[31;1m*** FAILED\033[0m err on $1"
                fi
            fi
        else
            diff "$experr" tmp_err.txt
            echo -e "\033[31;1m*** FAILED\033[0m out on $1"
        fi
    else
        echo -e "\033[31;1m*** FAILED\033[0m missing out on $1"
    fi
    if [ -f testbin ]; then
        echo " (binary)"
    else
        echo " (no binary)"
    fi
    rm -f tmp_out.txt tmp_err.txt tmp_exp_out.txt
    if [ "$RESULT" = "FAIL" ]; then
        exit 1
    fi
fi
