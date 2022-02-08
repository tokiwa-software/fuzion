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
#  Source code of _cur_dir.sh command, echos the current directory in a platform
#  independent way using $PWD or cygpath
#
#  Author: Michael Lill (michael.lill@tokiwa.software)
#
# -----------------------------------------------------------------------


set -euo pipefail

# @returns:
#   - unix: /my/current/path
#   - windows: C:\\\my\\\current\\\path

CURDIR="$PWD"
if [ -x "$(command -v cygpath)" ]
then
    CURDIR=$(cygpath -w "$PWD" | sed 's/\\/\\\\/g')
fi

echo "$CURDIR"
