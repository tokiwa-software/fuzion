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
#  Author: Fridtjof Siebert (siebert@tokiwa.software)
#
# -----------------------------------------------------------------------

# this runs the tests twice, once as a negative test to make sure that all
# required errors are shown, and then as a simple test to make sure that the
# error output is correct.
#
# Even though the negative variante could not fail if the simple variant fails,
# running both ensures that an update of error output cannot accidentally
# inctroduce some missing errors.
#
all: jvm int c

jvm:
	NAME=$(NAME) make -f ../negative.mk jvm
	NAME=$(NAME) FUZION_OPTIONS=-XmaxErrors=-1 make -f ../simple.mk jvm
c:
	NAME=$(NAME) make -f ../negative.mk c
	NAME=$(NAME) FUZION_OPTIONS=-XmaxErrors=-1 make -f ../simple.mk c
int:
	NAME=$(NAME) make -f ../negative.mk int
	NAME=$(NAME) FUZION_OPTIONS=-XmaxErrors=-1 make -f ../simple.mk int

show:
	NAME=$(NAME) make -f ../negative.mk show

record:
	NAME=$(NAME) FUZION_OPTIONS=-XmaxErrors=-1 make -f ../simple.mk record

record_jvm:
	NAME=$(NAME) FUZION_OPTIONS=-XmaxErrors=-1 make -f ../simple.mk record_jvm

record_c:
	NAME=$(NAME) FUZION_OPTIONS=-XmaxErrors=-1 make -f ../simple.mk record_c

record_int:
	NAME=$(NAME) FUZION_OPTIONS=-XmaxErrors=-1 make -f ../simple.mk record_int
