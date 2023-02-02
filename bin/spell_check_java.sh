#!/usr/bin/env bash

# This script checks the spelling of all html files in flang_dev.
# The aspell.dict - in the same directory as this script - contains
# all words that should not be reported as errors.

SCRIPTPATH="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 || exit ; pwd -P )"
cd "$SCRIPTPATH/.." || exit

shopt -s globstar dotglob
for f in **/*.java; do
  if [[ "$f" != *"content/docs/"* ]]; then
    # aspell mode is for checking C++ comments but seems to work for Java as well.
    UNKOWN_WORDS=$(aspell --lang=en_US -p "$PWD/bin/aspell.dict" --mode=ccpp list < "$f")

    if [ -n "${UNKOWN_WORDS##+([[:space:]])}" ];
    then
      echo "$f"
      echo "$UNKOWN_WORDS"
    fi
  fi
done
