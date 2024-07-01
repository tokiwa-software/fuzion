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
#  Source code of _strip_err.sh
#
# -----------------------------------------------------------------------

# _strip_err expects fuzion error output from stdin and replaces common internal
# strings that change frequently, e.g., due to the compiler adding internal ids,
# by dummy strings that are all the same. Result is written to stdout.
#

set -euo pipefail

sed <&0 -E "s \.fz:[0-9]+:[0-9]+: \.fz:n:n: g" |                                                    # line numbers  \
    sed -E "s #fun[0-9]+ fun g"                | sed -E "s INTERN_fun[0-9]+ fun g "               | # lambdas       \
    sed -E "s #loop[0-9]+ loop g"              | sed -E "s INTERN_loop[0-9]+ loop g "             | # loops         \
    sed -E "s #pre[0-9]+ pre g"                | sed -E "s INTERN_pre[0-9]+ pre g "               | # preconditions \
    sed -E "s #prebool[0-9]+ prebool g"        | sed -E "s INTERN_prebool[0-9]+ prebool g "       | # pre bools     \
    sed -E "s #preandcall[0-9]+ preandcall g"  | sed -E "s INTERN_preandcall[0-9]+ preandcall g " | # pre bools     \
    sed -E "s #post[0-9]+ post g"              | sed -E "s INTERN_post[0-9]+ post g "               # postconditions
