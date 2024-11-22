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

# NYI:  this will be moved to main Makefile in the future
#       once we start replacing intrinsics by natives

BUILD_DIR = ../../

A:=$(shell clang -O3 -shared $(BUILD_DIR)/include/posix.c $(BUILD_DIR)/include/shared.c -o $(BUILD_DIR)/include/libfuzion.so && \
		clang -O3 -shared $(BUILD_DIR)/include/posix.c $(BUILD_DIR)/include/shared.c -o $(BUILD_DIR)/include/libfuzion.dylib && \
		clang -O3 -shared $(BUILD_DIR)/include/posix.c $(BUILD_DIR)/include/shared.c -o $(BUILD_DIR)/include/fuzion.dll)

export LD_LIBRARY_PATH ?= ../../include
export PATH := $(PATH):../../include
export DYLD_FALLBACK_LIBRARY_PATH ?= ../../include

include ../simple.mk
