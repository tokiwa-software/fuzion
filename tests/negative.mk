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
#  Source code of Fuzion test Makefile to be included for negative tests
#
#  Author: Fridtjof Siebert (siebert@tokiwa.software)
#
# -----------------------------------------------------------------------

# expected variables
#
#  NAME -- the name of the main feature to be tested
#  FUZION -- the fz command

FUZION = ../../bin/fz -XmaxErrors=1000000
EXPECTED_ERRORS = `cat *.fz | grep "should.flag.an.error"  | sed "s ^.*//  g"| sort -n | uniq | wc -l`

int:
	$(FUZION) $(NAME) 2>err.txt || echo -n
	cat err.txt  | grep "should.flag.an.error" | sed "s ^.*//  g"| sort -n | uniq | wc -l | grep ^$(EXPECTED_ERRORS)$$ && echo "test passed." || exit 1

c:
	($(FUZION) -c $(NAME) -o=testbin && ./testbin) 2>err.txt || echo -n
	cat err.txt  | grep "should.flag.an.error" | sed "s ^.*//  g"| sort -n | uniq | wc -l | grep ^$(EXPECTED_ERRORS)$$ && echo "test passed." || exit 1

show:
	echo -n "Expected $(EXPECTED_ERRORS) errors, found " && cat err.txt  | grep "should.flag.an.error" | sed "s ^.*//  g"| sort -n | uniq | wc -l
	cat err.txt  | grep "should.flag.an.error" | sed "s ^.*//  g"| sort -n | uniq
