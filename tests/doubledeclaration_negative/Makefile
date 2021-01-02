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
#  Tokiwa GmbH, Berlin
#
#  Source code of Fuzion test Makefile
#
#  Author: Fridtjof Siebert (siebert@tokiwa.eu)
#
# -----------------------------------------------------------------------

NAME = doubledeclaration_negative
EXPECTED_ERRORS = `cat *.fu | grep "should.flag.an.error"  | sed "s ^.*//  g"| sort -n | uniq | wc -l`

all:
	$(FUSION) $(NAME) 2>err.txt || echo -n
	cat err.txt  | grep "should.flag.an.error" | sed "s ^.*//  g"| sort -n | uniq | wc -l | grep ^$(EXPECTED_ERRORS)$$ && echo "test passed." || exit 1

show:
	echo -n "Expected $(EXPECTED_ERRORS) errors, found " && cat err.txt  | grep "should.flag.an.error" | sed "s ^.*//  g"| sort -n | uniq | wc -l
	cat err.txt  | grep "should.flag.an.error" | sed "s ^.*//  g"| sort -n | uniq
