#!/bin/bash
set -euo pipefail

# @returns:
#   - unix: /my/current/path
#   - windows: C:\\\my\\\current\\\path

CURDIR="$PWD"
if [ -x "$(command -v cygpath)" ]
then
    CURDIR=$(cygpath -w $PWD | sed 's/\\/\\\\/g')
fi

echo $CURDIR
