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
#  Source code of Fuzion test Makefile to be included for simple tests
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

FUZION = ../../bin/fz
FILE = $(NAME).fz

all:
	../check_simple_example.sh $(FUZION) $(FILE) || exit 1

c:
	../check_simple_example_c.sh $(FUZION) $(FILE) || exit 1

record:
	../record_simple_example.sh $(FUZION) $(FILE)

record_c:
	../record_simple_example_c.sh $(FUZION) $(FILE)
