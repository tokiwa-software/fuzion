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
#  Source code of Fuzion gtk example Makefile
#
# -----------------------------------------------------------------------

# To run this example, you must install gtk libs.
# e.g.: apt-get install libgtk-4-dev


.PHONY: jvm c compile test

# Test if gtk4 and pkg-config are installed
test:
	@command -v pkg-config >/dev/null 2>&1 || { echo >&2 "Error: pkg-config is not installed."; exit 1; }
	@pkg-config --exists gtk4 || { echo >&2 "Error: gtk4 is not found via pkg-config."; exit 1; }

jvm: test
	../../bin/fz -jvm \
		-JLibraries="$(shell pkg-config --libs gtk4 | sed 's/-l//g')" \
		./gtk.fz

c: compile
	./gtk_ex

compile: test
	../../bin/fz -c \
		-CFlags="$(shell pkg-config --cflags gtk4) -Wno-incompatible-function-pointer-types" \
		-CInclude=gtk/gtk.h \
		-CLink="$(shell pkg-config --libs gtk4 | sed 's/-l//g')" \
		-o=gtk_ex \
		./gtk.fz
