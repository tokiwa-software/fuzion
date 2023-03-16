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
    FUZION_RANDOM_SEED=13 java -Xss16m -Xmx512m -cp "$FUZION_HOME"/classes -Dfuzion.home="$FUZION_HOME"/ $OPT dev.flang.tools.Fuzion - < "$1" >tmp_out.txt 2>tmp_err.txt
    if ! diff "$1".expected_out tmp_out.txt; then
      echo -e "\033[31;1m*** FAILED\033[0m out on $1"
      exit 1
    fi
    if ! diff "$1".expected_err tmp_err.txt; then
      echo -e "\033[31;1m*** FAILED\033[0m err on $1"
      exit 1
    fi
    diff "$1".expected_out tmp_out.txt >/dev/null && diff "$1".expected_err tmp_err.txt >/dev/null && echo -e "\033[32;1mPASSED\033[0m."
    rm tmp_out.txt tmp_err.txt
fi
