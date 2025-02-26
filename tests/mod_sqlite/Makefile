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

CREATE_DB := $(shell echo "CREATE TABLE table_name (id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE); INSERT INTO table_name (name, email) VALUES ('Mario', 'admin@example.com');" | sqlite3 test.db > /dev/null 2> /dev/null || true)

override NAME = mod_sqlite
# NYI: FUZION_JVM_BACKEND_OPTIONS = -libraries=sqlite3
override FUZION_C_BACKEND_OPTIONS = "-CLink=sqlite3" "-CInclude=sqlite3.h"
include ../simple.mk
