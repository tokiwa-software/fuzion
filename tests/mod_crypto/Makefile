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

export DYLD_FALLBACK_LIBRARY_PATH ?= /opt/homebrew/lib/:/Library/Developer/CommandLineTools/usr/lib/

override NAME = mod_crypto
override FUZION_OPTIONS = -modules=crypto,sodium
override FUZION_JVM_BACKEND_OPTIONS = -JLibraries=sodium
override FUZION_C_BACKEND_OPTIONS = -CLink=sodium|-CInclude=sodium.h
include ../simple.mk
