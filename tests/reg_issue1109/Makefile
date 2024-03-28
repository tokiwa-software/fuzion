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

#
# The example caused an Error during DFA, when using module file and C backend.
#

FUZION_OPTIONS ?=
FUZION = ../../bin/fz $(FUZION_OPTIONS)
FUZION_HOME = $(dir $(FUZION))..

all: jvm c int

jvm: mod_jvm
	echo "m is _ := a" | $(FUZION) -jvm -sourceDirs=src -

c: mod_c
	echo "m is _ := a" | $(FUZION) -c -sourceDirs=src -

int: mod_int
	echo "m is _ := a" | $(FUZION) -interpreter -sourceDirs=src -

a.fum: $(FUZION_HOME)/modules/base.fum
	$(FUZION) -saveLib=a.fum -sourceDirs=src

mod_jvm: a.fum
	echo "m is _ := a" | $(FUZION) -jvm -modules=a -moduleDirs=. -

mod_c: a.fum
	echo "m is _ := a" | $(FUZION) -c -modules=a -moduleDirs=. -

mod_int: a.fum
	echo "m is _ := a" | $(FUZION) -interpreter -modules=a -moduleDirs=. -

clean:
	rm a.fum m *~ */*~
