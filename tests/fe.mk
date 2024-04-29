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
#  Source code of Fuzion test Makefile to be included for fe tests
#  This is used e.g. for tests that do not fully work yet.
#
# -----------------------------------------------------------------------

# expected variables
#
#  NAME -- the name of the main feature to be tested
#  FUZION -- the fz command

FUZION_OPTIONS ?=
FUZION = ../../bin/fz $(FUZION_OPTIONS)

all: jvm c int

int:
	$(FUZION) -frontend-only $(NAME) 2>err.txt || (RC=$$? && cat err.txt && exit $$RC)

jvm:
	$(FUZION) -frontend-only $(NAME) 2>err.txt || (RC=$$? && cat err.txt && exit $$RC)

c:
	$(FUZION) -frontend-only $(NAME) 2>err.txt || (RC=$$? && cat err.txt && exit $$RC)
