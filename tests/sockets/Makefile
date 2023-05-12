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


# A simple tests compares the actual output with the expected output
#
# expected variables
#
#  NAME -- the name of the main feature to be tested
#  FUZION -- the fz command
#  FUZION_OPTIONS -- options to be passed to $(FUZION)
override NAME = sockets_test_client
FUZION_OPTIONS = -unsafeIntrinsics=on
FUZION = ../../bin/fz
FUZION_RUN = $(FUZION) $(FUZION_OPTIONS)
FILE = $(NAME).fz
ENV = \
  $(if $(FUZION_HOME)           , FUZION_HOME=$(FUZION_HOME)                      ,) \
  $(if $(FUZION_JAVA)           , FUZION_JAVA=$(FUZION_JAVA)                      ,) \
  $(if $(FUZION_JAVA_STACK_SIZE), FUZION_JAVA_STACK_SIZE=$(FUZION_JAVA_STACK_SIZE),) \
  $(if $(FUZION_JAVA_OPTIONS)   , FUZION_JAVA_OPTIONS=$(FUZION_JAVA_OPTIONS)      ,) \

compile_server_c:
	rm -f server
	$(FUZION_RUN) -c -o=server sockets_test_server.fz


int: IPv4_TCP_PID = $(shell $(FUZION_RUN) sockets_test_server.fz ipv4 tcp > /dev/null & echo $$!)
int: IPv4_UDP_PID = $(shell $(FUZION_RUN) sockets_test_server.fz ipv4 udp > /dev/null & echo $$!)
int: IPv6_TCP_PID = $(shell $(FUZION_RUN) sockets_test_server.fz ipv6 tcp > /dev/null & echo $$!)
int: IPv6_UDP_PID = $(shell $(FUZION_RUN) sockets_test_server.fz ipv6 udp > /dev/null & echo $$!)
int:
	sleep 10s
	$(ENV) ../check_simple_example.sh "$(FUZION_RUN)" $(FILE) || exit 1
	kill $(IPv4_TCP_PID) 2> /dev/null || true
	kill $(IPv4_UDP_PID) 2> /dev/null || true
	kill $(IPv6_TCP_PID) 2> /dev/null || true
	kill $(IPv6_UDP_PID) 2> /dev/null || true


c: compile_server_c
c: IPv4_TCP_PID = $(shell ./server ipv4 tcp > /dev/null & echo $$!)
c: IPv4_UDP_PID = $(shell ./server ipv4 udp > /dev/null & echo $$!)
c: IPv6_TCP_PID = $(shell ./server ipv6 tcp > /dev/null & echo $$!)
c: IPv6_UDP_PID = $(shell ./server ipv6 udp > /dev/null & echo $$!)
c:
	sleep 1s
	$(ENV) ../check_simple_example_c.sh "$(FUZION_RUN)" $(FILE) || exit 1
	kill $(IPv4_TCP_PID) 2> /dev/null || true
	kill $(IPv4_UDP_PID) 2> /dev/null || true
	kill $(IPv6_TCP_PID) 2> /dev/null || true
	kill $(IPv6_UDP_PID) 2> /dev/null || true


record: IPv4_TCP_PID = $(shell $(FUZION_RUN) sockets_test_server.fz ipv4 tcp > /dev/null & echo $$!)
record: IPv4_UDP_PID = $(shell $(FUZION_RUN) sockets_test_server.fz ipv4 udp > /dev/null & echo $$!)
record: IPv6_TCP_PID = $(shell $(FUZION_RUN) sockets_test_server.fz ipv6 tcp > /dev/null & echo $$!)
record: IPv6_UDP_PID = $(shell $(FUZION_RUN) sockets_test_server.fz ipv6 udp > /dev/null & echo $$!)
record:
	sleep 10s
	$(ENV) ../record_simple_example.sh "$(FUZION_RUN)" $(FILE)
	kill $(IPv4_TCP_PID) 2> /dev/null || true
	kill $(IPv4_UDP_PID) 2> /dev/null || true
	kill $(IPv6_TCP_PID) 2> /dev/null || true
	kill $(IPv6_UDP_PID) 2> /dev/null || true


record_c: compile_server_c
record_c: IPv4_TCP_PID = $(shell ./server ipv4 tcp > /dev/null & echo $$!)
record_c: IPv4_UDP_PID = $(shell ./server ipv4 udp > /dev/null & echo $$!)
record_c: IPv6_TCP_PID = $(shell ./server ipv6 tcp > /dev/null & echo $$!)
record_c: IPv6_UDP_PID = $(shell ./server ipv6 udp > /dev/null & echo $$!)
record_c:
	sleep 1s
	$(ENV) ../record_simple_example_c.sh "$(FUZION_RUN)" $(FILE)
	kill $(IPv4_TCP_PID) 2> /dev/null || true
	kill $(IPv4_UDP_PID) 2> /dev/null || true
	kill $(IPv6_TCP_PID) 2> /dev/null || true
	kill $(IPv6_UDP_PID) 2> /dev/null || true
