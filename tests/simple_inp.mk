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
#  Source code of Fuzion test Makefile to be included for simple tests which require stdin
#
#  Author: Fridtjof Siebert (siebert@tokiwa.software)
#
# -----------------------------------------------------------------------

# A simple tests compares the actual output with the expected output
#
# expected variables
#
#  NAME -- the name of the main feature to be tested
#  FUZION -- the fz command
#  FUZION_OPTIONS -- options to be passed to $(FUZION)

FUZION_OPTIONS ?=
FUZION ?= ../../bin/fz
FILE = $(NAME).fz
STDIN = $(NAME).fz.stdin
FUZION_RUN = $(FUZION) $(FUZION_OPTIONS)

all: jvm c int


int:
	cat $(STDIN) | ../../bin/check_simple_example int "$(FUZION_RUN)" $(FILE) || exit 1

c:
	cat $(STDIN) | ../../bin/check_simple_example c "$(FUZION_RUN)" $(FILE) || exit 1

jvm:
	cat $(STDIN) | ../../bin/check_simple_example jvm "$(FUZION_RUN)" $(FILE) || exit 1

record:
	cat $(STDIN) | $(FUZION) ../../bin/record_simple_example any "$(FUZION_RUN)" $(FILE)

record_int:
	cat $(STDIN) | $(FUZION) ../../bin/record_simple_example int "$(FUZION_RUN)" $(FILE)

record_c:
	cat $(STDIN) | $(FUZION) ../../bin/record_simple_example c "$(FUZION_RUN)" $(FILE)

record_jvm:
	cat $(STDIN) | $(FUZION) ../../bin/record_simple_example jvm "$(FUZION_RUN)" $(FILE)

effect:
	$(ENV) ../../bin/check_simple_example effect "$(FUZION_RUN)" $(FILE) || exit 1

record_effect: $(FUZION_DEPENDENCIES)
	$(ENV) $(FUZION) ../../bin/record_simple_example effect "$(FUZION_RUN)" $(FILE)
