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

#  NAME -- the name of the main feature to be tested
#  FUZION -- the fz command
#  FUZION_OPTIONS -- options to be passed to $(FUZION)
override NAME = main
FUZION_OPTIONS = -modules=mod -moduleDirs=modules
FUZION ?= ../../bin/fz
FUZION_RUN = $(FUZION) $(FUZION_OPTIONS)
FILE = $(NAME).fz
ENV = \
  $(if $(FUZION_HOME)           , FUZION_HOME=$(FUZION_HOME)                      ,) \
  $(if $(FUZION_JAVA)           , FUZION_JAVA=$(FUZION_JAVA)                      ,) \
  $(if $(FUZION_JAVA_STACK_SIZE), FUZION_JAVA_STACK_SIZE=$(FUZION_JAVA_STACK_SIZE),) \
  $(if $(FUZION_JAVA_OPTIONS)   , FUZION_JAVA_OPTIONS=$(FUZION_JAVA_OPTIONS)      ,) \

CHECK = && echo $(OK) || (echo $(FAIL); exit 1)


int: clean modules/mod.fum
	$(ENV) ../check_simple_example_int.sh "$(FUZION_RUN)" $(FILE) || exit 1

jvm: clean modules/mod.fum
	$(ENV) ../check_simple_example_jvm.sh "$(FUZION_RUN)" $(FILE) || exit 1

c: clean modules/mod.fum
	$(ENV) ../check_simple_example_c.sh "$(FUZION_RUN)" $(FILE) || exit 1

record: clean modules/mod.fum
	$(ENV) ../record_simple_example.sh "$(FUZION_RUN)" $(FILE)

record_int: clean modules/mod.fum
	$(ENV) ../record_simple_example_int.sh "$(FUZION_RUN)" $(FILE)

record_jvm: clean modules/mod.fum
	$(ENV) ../record_simple_example_jvm.sh "$(FUZION_RUN)" $(FILE)

record_c: clean modules/mod.fum
	$(ENV) ../record_simple_example_c.sh "$(FUZION_RUN)" $(FILE)


# compile mod into mod.fum
modules/mod.fum:
	mkdir -p $(@D)
	$(FUZION) -sourceDirs=./mod -saveLib=$@  $(CHECK)

clean:
	rm -rf modules *~ */*~ */*/*~
