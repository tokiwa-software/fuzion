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
#  FUZION_OPTIONS -- options to be passed to $(FUZION)

FUZION_OPTIONS ?=
FUZION_JVM_BACKEND_OPTIONS ?=
FUZION_C_BACKEND_OPTIONS ?=
FUZION_DEPENDENCIES ?=
FUZION ?= ../../bin/fz
FUZION_RUN = $(FUZION) $(FUZION_OPTIONS)
FILE = $(NAME).fz
ENV = \
  $(if $(FUZION_HOME)               , FUZION_HOME="$(FUZION_HOME)"                              ,) \
  $(if $(FUZION_JAVA)               , FUZION_JAVA="$(FUZION_JAVA)"                              ,) \
  $(if $(FUZION_JAVA_STACK_SIZE)    , FUZION_JAVA_STACK_SIZE="$(FUZION_JAVA_STACK_SIZE)"        ,) \
  $(if $(FUZION_JAVA_OPTIONS)       , FUZION_JAVA_OPTIONS="$(FUZION_JAVA_OPTIONS)"              ,) \
  $(if $(FUZION_OPTIONS)            , FUZION_OPTIONS="$(FUZION_OPTIONS)"                        ,) \
  $(if $(FUZION_JVM_BACKEND_OPTIONS), FUZION_JVM_BACKEND_OPTIONS="$(FUZION_JVM_BACKEND_OPTIONS)",) \
  $(if $(FUZION_C_BACKEND_OPTIONS)  , FUZION_C_BACKEND_OPTIONS="$(FUZION_C_BACKEND_OPTIONS)"    ,) \
  $(if $(FUZION_DEPENDENCIES)       , FUZION_OPTIONS="$(FUZION_DEPENDENCIES)"                   ,) \

# for libjvm.so
export LD_LIBRARY_PATH ?= $(JAVA_HOME)/lib/server
# on windows jvm.dll is in /bin/server
export PATH := $(PATH):$(JAVA_HOME)/bin/server
# fix libjvm.dylib not found: see also https://stackoverflow.com/a/3172515
export DYLD_FALLBACK_LIBRARY_PATH ?= $(JAVA_HOME)/lib/server


# needed for exception texts: see #3106
export LANGUAGE = en_US:en


all: jvm c int

../check_simple_example:
	$(FUZION) -modules=terminal -c -o=../check_simple_example ../check_simple_example.fz

int: $(FUZION_DEPENDENCIES) ../check_simple_example
	$(ENV) ../check_simple_example int "$(FUZION_RUN)" $(FILE) || exit 1

jvm: $(FUZION_DEPENDENCIES) ../check_simple_example
	$(ENV) ../check_simple_example jvm "$(FUZION_RUN)" $(FILE) || exit 1

c: $(FUZION_DEPENDENCIES) ../check_simple_example
	$(ENV) ../check_simple_example c "$(FUZION_RUN)" $(FILE) || exit 1

effect: $(FUZION_DEPENDENCIES) ../check_simple_example
	$(ENV) ../check_simple_example effect "$(FUZION_RUN)" $(FILE) || exit 1

record: $(FUZION_DEPENDENCIES)
	$(ENV) $(FUZION) ../record_simple_example.fz any "$(FUZION_RUN)" $(FILE)

record_int: $(FUZION_DEPENDENCIES)
	$(ENV) $(FUZION) ../record_simple_example.fz int "$(FUZION_RUN)" $(FILE)

record_jvm: $(FUZION_DEPENDENCIES)
	$(ENV) $(FUZION) ../record_simple_example.fz jvm "$(FUZION_RUN)" $(FILE)

record_c: $(FUZION_DEPENDENCIES)
	$(ENV) $(FUZION) ../record_simple_example.fz c "$(FUZION_RUN)" $(FILE)

record_effect: $(FUZION_DEPENDENCIES)
	$(ENV) $(FUZION) ../record_simple_example.fz effect "$(FUZION_RUN)" $(FILE)
