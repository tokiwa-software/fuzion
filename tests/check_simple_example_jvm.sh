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
# output to $2.expected_out and $2.expected_err.
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
    (FUZION_DISABLE_ANSI_ESCAPES=true FUZION_JAVA_OPTIONS="${FUZION_JAVA_OPTIONS="-Xss${FUZION_JAVA_STACK_SIZE=5m}"} ${OPT:-}" $1 -jvm "$2" >tmp_out.txt 2>tmp_err.txt) || true
    sed -i "s|${CURDIR//\\//}/|--CURDIR--/|g" tmp_err.txt
    expout=$2.expected_out
    experr=$2.expected_err
    if [ -f "$2".expected_out_jvm ]; then
        expout=$2.expected_out_jvm
    fi
    if [ -f "$2".expected_err_jvm ]; then
        experr=$2.expected_err_jvm
    fi
    # show diff in stdout unless an unexpected output occured to stderr:
    (diff "$experr" tmp_err.txt && diff "$expout" tmp_out.txt) || echo -e "\033[31;1m*** FAILED\033[0m out on $2"
    diff "$expout" tmp_out.txt >/dev/null && diff "$experr" tmp_err.txt >/dev/null && echo -e "\033[32;1mPASSED\033[0m."
    RC=$?
    rm tmp_out.txt tmp_err.txt
fi
exit $RC
