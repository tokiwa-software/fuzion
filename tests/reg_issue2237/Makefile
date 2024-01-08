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

override NAME = issue_2237
FUZION = ../../bin/fz $(FUZION_OPTIONS)

all: jvm c int

int: mod
	$(FUZION) -modules=mod -moduleDirs=mod -interpreter $(NAME) 2>err.txt || (RC=$$? && cat err.txt && exit $$RC)

jvm: mod
	$(FUZION) -modules=mod -moduleDirs=mod -jvm $(NAME) 2>err.txt || (RC=$$? && cat err.txt && exit $$RC)

c: mod
	($(FUZION) -modules=mod -moduleDirs=mod -c -o=testbin $(NAME) && ./testbin) 2>err.txt || (export RC=$$? && cat err.txt && exit $$RC)

.PHONY: mod
mod:
	mkdir -p mod
	$(FUZION) -sourceDirs=mod -saveLib=mod/mod.fum
