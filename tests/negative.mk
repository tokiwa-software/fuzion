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

FUZION_OPTIONS ?=
FUZION = FUZION_DISABLE_ANSI_ESCAPES=true ../../bin/fz -XmaxErrors=-1 $(FUZION_OPTIONS)
EXPECTED_ERRORS = `cat *.fz | grep "should.flag.an.error"  | sed "s ^.*//  g"| sort -n | uniq | wc -l | tr -d ' '`

all: jvm c int

int:
	$(FUZION) -interpreter $(NAME) 2>err.txt || true
# check if for every unique comment containing "should flag an error" an error is reported for a line with that comment
	printf "RUN negative test $(NAME).fz using interpreter backend "; \
	cat err.txt  | grep "should.flag.an.error" | sed "s ^.*//  g" | sort -n | uniq | wc -l | tr -d ' ' | grep ^$(EXPECTED_ERRORS)$$ > /dev/null && \
		printf "\033[32;1mPASSED\033[0m.\n" || (printf "\033[31;1m*** FAILED ***\033[0m\n" && exit 1)

jvm:
	$(FUZION) -jvm $(NAME) 2>err.txt || true
# check if for every unique comment containing "should flag an error" an error is reported for a line with that comment
	printf "RUN negative test $(NAME).fz using jvm backend "; \
	cat err.txt  | grep "should.flag.an.error" | sed "s ^.*//  g" | sort -n | uniq | wc -l | tr -d ' ' | grep ^$(EXPECTED_ERRORS)$$ > /dev/null && \
		printf "\033[32;1mPASSED\033[0m.\n" || (printf "\033[31;1m*** FAILED ***\033[0m\n" && exit 1)

c:
	($(FUZION) -c -o=testbin $(NAME) && ./testbin) 2>err.txt || true
# check if for every unique comment containing "should flag an error" an error is reported for a line with that comment
	printf "RUN negative test $(NAME).fz using c backend "; \
	cat err.txt  | grep "should.flag.an.error" | sed "s ^.*//  g" | sort -n | uniq | wc -l | tr -d ' ' | grep ^$(EXPECTED_ERRORS)$$ > /dev/null && \
		printf "\033[32;1mPASSED\033[0m.\n" || (printf "\033[31;1m*** FAILED ***\033[0m\n" && exit 1)

show:
	printf "Expected $(EXPECTED_ERRORS) errors, found " && cat err.txt  | grep "should.flag.an.error" | sed "s ^.*//  g"| sort -n | uniq | wc -l | tr -d ' '
	cat err.txt  | grep "should.flag.an.error" | sed "s ^.*//  g"| sort -n | uniq
