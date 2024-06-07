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
#  Source code of _diff_err.sh
#
# -----------------------------------------------------------------------

# _diff_err expects two file names as arguments and calls `diff` on these files
# after first replacing common internal strings that change frequently, e.g., due to
# the compiler adding internal ids, by dummy strings that are all the same.
#

SCRIPTPATH="$(dirname "$(readlink -f "$0")")"
STRIP_ERR="$SCRIPTPATH"/_strip_err.sh

set -euo pipefail

tmp1="$(mktemp)"
tmp2="$(mktemp)"

"$STRIP_ERR" <"$1" >"$tmp1"
"$STRIP_ERR" <"$2" >"$tmp2"

diff "$tmp1" "$tmp2"; rc=$?

rm "$tmp1" "$tmp2"
exit $rc
