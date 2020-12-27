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
#  Source code of class ANY
#
# -----------------------------------------------------------------------

# must be at least java 11
JAVA = java
FUSION_HOME = $(shell pwd)

JAVA_FILES_UTIL = \
          src/dev/flang/util/ANY.java \
	  src/dev/flang/util/Errors.java \
	  src/dev/flang/util/FusionOptions.java \
	  src/dev/flang/util/List.java \
	  src/dev/flang/util/Map2Int.java \
	  src/dev/flang/util/MapComparable2Int.java \
	  src/dev/flang/util/SourceFile.java \
	  src/dev/flang/util/SourcePosition.java \

CLASS_FILES_UTIL           = classes/dev/flang/util/__marker_for_make__

$(CLASS_FILES_UTIL): $(JAVA_FILES_UTIL)
	mkdir -p classes
	javac -d classes $(JAVA_FILES_UTIL)
	touch $@

clean:
	rm -rf classes
	find . -name "*~" -exec rm {} \;
