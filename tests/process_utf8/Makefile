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


override PATH := $(PATH):$(shell pwd)

all: jvm c int

compile_unicode_feature:
	../../bin/fz -c -o=smiley unicode_feature.fz
	mv -f smiley.exe 😀.exe 2> /dev/null || mv -f smiley 😀 2> /dev/null || true

jvm: compile_unicode_feature

c:   compile_unicode_feature

int: compile_unicode_feature

effect: compile_unicode_feature

record: compile_unicode_feature

record_int: compile_unicode_feature

record_jvm: compile_unicode_feature

record_c: compile_unicode_feature

record_effect: compile_unicode_feature


override NAME = process_utf8
include ../simple.mk
