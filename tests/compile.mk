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
#  Source code of Fuzion test Makefile to be included for compile only tests
#  This is used e.g. for tests that result in infinite recursion
#  but we still want to test that compilation succeeds.
#
# -----------------------------------------------------------------------

# expected variables
#
#  NAME -- the name of the main feature to be tested
#  FUZION -- the fz command

FUZION_OPTIONS ?=
FUZION = ../../bin/fz $(FUZION_OPTIONS)
FILE = $(NAME).fz

all: jvm c int

../check_simple_example:
	$(FUZION) -modules=terminal -c -o=../check_simple_example ../check_simple_example.fz

int:
	printf 'COMPILE %s ' "$(NAME)" && $(FUZION) -noBackend $(NAME) 2>err.txt && echo "\033[32;1mPASSED\033[0m." || echo "\033[31;1m*** FAILED\033[0m." && (RC=$$? && cat err.txt && exit $$RC)

jvm:
	printf 'COMPILE %s ' "$(NAME)" && $(FUZION) -classes $(NAME) 2>err.txt && echo "\033[32;1mPASSED\033[0m." || echo "\033[31;1m*** FAILED\033[0m." && (RC=$$? && cat err.txt && exit $$RC)

c:
	printf 'COMPILE %s ' "$(NAME)" && $(FUZION) -c $(NAME) 2>err.txt && echo "\033[32;1mPASSED\033[0m." || echo "\033[31;1m*** FAILED\033[0m." && (RC=$$? && cat err.txt && exit $$RC)

effect: ../check_simple_example
	$(ENV) ../check_simple_example effect "$(FUZION_RUN)" $(FILE) || exit 1

record_effect: ../check_simple_example
	$(ENV) ../record_simple_example effect "$(FUZION_RUN)" $(FILE)
