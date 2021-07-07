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
#  Source code of fz command, the main Fuzion tools entry point
#
#  Author: Fridtjof Siebert (siebert@tokiwa.software)
#
# -----------------------------------------------------------------------

#!/bin/bash
#
# Run the fuzion example given as an argument $2 using the C backend and store
# the stdout/stderr output to $2.expected_out_c and $2.expected_err_c.
#
# The fz command is given as argument $1
#
# In case file $2.skip exists, do not run the example
#

if [ -f $2.skip ]; then
    echo "SKIPPED $2"
else
    (($1 $2 -c -o=testbin && ./testbin) 2>$2.expected_err_c | head -n 100) >$2.expected_out_c
    echo "RECORDED $2"
fi
