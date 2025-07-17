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
#  Author: Fridtjof Siebert (siebert@tokiwa.software)
#
# -----------------------------------------------------------------------

# this runs the tests twice, once as a negative test to make sure that all
# required errors are shown, and then as a simple test to make sure that the
# error output is correct.
#
# Even though the negative variant could not fail if the simple variant fails,
# running both ensures that an update of error output cannot accidentally
# introduce some missing errors.
#

FUZION_OPTIONS ?=
FILE ?= $(NAME).fz
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

all: jvm int c

jvm:
	FILE=$(FILE) NAME=$(NAME) $(ENV) make -f ../negative.mk jvm
	FILE=$(FILE) NAME=$(NAME) $(ENV) make -f ../simple.mk jvm
c:
	FILE=$(FILE) NAME=$(NAME) $(ENV) make -f ../negative.mk c
	FILE=$(FILE) NAME=$(NAME) $(ENV) make -f ../simple.mk c
int:
	FILE=$(FILE) NAME=$(NAME) $(ENV) make -f ../negative.mk int
	FILE=$(FILE) NAME=$(NAME) $(ENV) make -f ../simple.mk int

show:
	FILE=$(FILE) NAME=$(NAME) $(ENV) make -f ../negative.mk show

record:
	FILE=$(FILE) NAME=$(NAME) $(ENV) make -f ../simple.mk record

record_jvm:
	FILE=$(FILE) NAME=$(NAME) $(ENV) make -f ../simple.mk record_jvm

record_c:
	FILE=$(FILE) NAME=$(NAME) $(ENV) make -f ../simple.mk record_c

record_int:
	FILE=$(FILE) NAME=$(NAME) $(ENV) make -f ../simple.mk record_int

effect:
