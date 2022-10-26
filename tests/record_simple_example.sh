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
#  Source code of record_simple_example.sh script, records expected output of
#  simple test using interpreter backend
#
#  Author: Fridtjof Siebert (siebert@tokiwa.software)
#
# -----------------------------------------------------------------------


# Run the fuzion example given as an argument $2 and store the stdout/stderr
# output to $2.expected_out and $2.expected_err.
#
# The fz command is given as argument $1
#
# In case file $2.skip exists, do not run the example
#

SCRIPTPATH="$(dirname "$(readlink -f "$0")")"
CURDIR=$("$SCRIPTPATH"/_cur_dir.sh)

if [ -f "$2".skip ]; then
    echo "SKIPPED $2"
else
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
    $1 "$2" >"$2".expected_out 2>"$2".expected_err0
    cat "$2".expected_err0 | sed "s|$CURDIR[\\\/]|--CURDIR--/|g" >"$2".expected_err
    rm -rf "$2".expected_err0
    echo "RECORDED $2"
fi
