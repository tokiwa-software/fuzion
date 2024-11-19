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
#  Source code of Fuzion test Makefile
#
# -----------------------------------------------------------------------

override NAME = native

JAVA_VERSION = 21
JAVAC = javac -encoding UTF8 --release $(JAVA_VERSION)

JAVA_FILES_JEXTRACT = ../../generated/src/dev/flang/jextract/fz_h.java

A:=$(shell jextract --include-function fzE_nanotime --include-function fzE_nanosleep -l fuzion --output ../../generated/src/ -t dev.flang.jextract ../../../include/fz.h -I /usr/include/ && \
		$(JAVAC) --enable-preview -cp ../../classes -d ../../classes $(JAVA_FILES_JEXTRACT))

export LD_LIBRARY_PATH ?= ../../include
export PATH := $(PATH):../../include
export DYLD_FALLBACK_LIBRARY_PATH ?= ../../include

# NYI: does not seem to work? -Djava.library.path=../../include

FUZION_JAVA_OPTIONS = "--enable-preview --enable-native-access=ALL-UNNAMED"
include ../simple.mk
