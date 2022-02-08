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
#  source code of bash script ebnf.sh
#
#  Author: Michael Lill (michael.lill@tokiwa.software)
#
# -----------------------------------------------------------------------

# echos ebnf grammar and tests the resulting grammar with antlr
# 1) Extract ebnf grammar from Lexer/Parser.java
# 2) Test if grammar can be parsed with antlr4

set -euo pipefail

if [ ! -x "$(command -v pcregrep)" ]
then
  echo "*** no ebnf generated, missing pcregrep tool"
  echo "*** no ebnf generated, missing pcregrep tool" 1>&2
  exit 0
fi

if [ ! -x "$(command -v antlr4)" ]
then
  echo "*** no ebnf generated, missing antlr4 tool"
  echo "*** no ebnf generated, missing antlr4 tool" 1>&2
  exit 0
fi

NEW_LINE=$'\n'
SCRIPTPATH="$(dirname "$(readlink -f "$0")")"
cd "$SCRIPTPATH"/..

#
EBNF_LEXER=$(pcregrep -M "^[a-zA-Z0-9_]+[ ]*:(\n|.)*?( ;)" ./src/dev/flang/parser/Lexer.java)
EBNF_PARSER=$(pcregrep -M "^[a-zA-Z0-9_]+[ ]*:(\n|.)*?( ;)" ./src/dev/flang/parser/Parser.java)

# header
EBNF="grammar Fuzion;${NEW_LINE}${NEW_LINE}"
# combine parser and lexer
EBNF="${EBNF}${EBNF_LEXER}${NEW_LINE}${EBNF_PARSER}"
# replace " by '
EBNF=$(sed 's/"/\x27/g' <<< "$EBNF")

echo "$EBNF"

# test grammar with antlr4
TMP=$(mktemp -d)
mkdir -p "$TMP"/fuzion_grammar
echo "$EBNF" > "$TMP"/fuzion_grammar/Fuzion.g4
# NYI add option -Werror
antlr4 -long-messages -o "$TMP"/fuzion_grammar "$TMP"/fuzion_grammar/Fuzion.g4
antlr4_rc=$?
rm -rf "$TMP"

if [ ! $antlr4_rc -eq 0 ]; then
  echo "antlr4 failed parsing grammar"
  exit 1
fi
