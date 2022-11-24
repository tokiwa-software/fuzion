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
#  Source code of main Makefile
#
#  Author: Fridtjof Siebert (siebert@tokiwa.software)
#
# -----------------------------------------------------------------------

JAVA = java
JAVAC = javac -encoding UTF8 -source 17
FZ_SRC = $(patsubst %/,%,$(dir $(lastword $(MAKEFILE_LIST))))
SRC = $(FZ_SRC)/src
BUILD_DIR = ./build
CLASSES_DIR = $(BUILD_DIR)/classes

JAVA_FILE_TOOLS_VERSION_IN =  $(SRC)/dev/flang/tools/Version.java.in
JAVA_FILE_TOOLS_VERSION    =  $(BUILD_DIR)/generated/src/dev/flang/tools/Version.java

JAVA_FILES_UTIL              = $(wildcard $(SRC)/dev/flang/util/*.java          )
JAVA_FILES_UTIL_UNICODE      = $(wildcard $(SRC)/dev/flang/util/unicode/*.java  )
JAVA_FILES_AST               = $(wildcard $(SRC)/dev/flang/ast/*.java           )
JAVA_FILES_PARSER            = $(wildcard $(SRC)/dev/flang/parser/*.java        )
JAVA_FILES_IR                = $(wildcard $(SRC)/dev/flang/ir/*.java            )
JAVA_FILES_MIR               = $(wildcard $(SRC)/dev/flang/mir/*.java           )
JAVA_FILES_FE                = $(wildcard $(SRC)/dev/flang/fe/*.java            )
JAVA_FILES_AIR               = $(wildcard $(SRC)/dev/flang/air/*.java           )
JAVA_FILES_ME                = $(wildcard $(SRC)/dev/flang/me/*.java            )
JAVA_FILES_FUIR              = $(wildcard $(SRC)/dev/flang/fuir/*.java          )
JAVA_FILES_FUIR_ANALYSIS     = $(wildcard $(SRC)/dev/flang/fuir/analysis/*.java )
JAVA_FILES_FUIR_ANALYSIS_DFA = $(wildcard $(SRC)/dev/flang/fuir/analysis/dfa/*.java )
JAVA_FILES_FUIR_CFG          = $(wildcard $(SRC)/dev/flang/fuir/cfg/*.java      )
JAVA_FILES_OPT               = $(wildcard $(SRC)/dev/flang/opt/*.java           )
JAVA_FILES_BE_INTERPRETER    = $(wildcard $(SRC)/dev/flang/be/interpreter/*.java)
JAVA_FILES_BE_C              = $(wildcard $(SRC)/dev/flang/be/c/*.java          )
JAVA_FILES_BE_EFFECTS        = $(wildcard $(SRC)/dev/flang/be/effects/*.java    )
JAVA_FILES_TOOLS             = $(wildcard $(SRC)/dev/flang/tools/*.java         ) $(JAVA_FILE_TOOLS_VERSION)
JAVA_FILES_TOOLS_FZJAVA      = $(wildcard $(SRC)/dev/flang/tools/fzjava/*.java  )
JAVA_FILES_MISC_LOGO         = $(wildcard $(SRC)/dev/flang/misc/logo/*.java     )

CLASS_FILES_UTIL              = $(CLASSES_DIR)/dev/flang/util/__marker_for_make__
CLASS_FILES_UTIL_UNICODE      = $(CLASSES_DIR)/dev/flang/util/unicode/__marker_for_make__
CLASS_FILES_AST               = $(CLASSES_DIR)/dev/flang/ast/__marker_for_make__
CLASS_FILES_PARSER            = $(CLASSES_DIR)/dev/flang/parser/__marker_for_make__
CLASS_FILES_IR                = $(CLASSES_DIR)/dev/flang/ir/__marker_for_make__
CLASS_FILES_MIR               = $(CLASSES_DIR)/dev/flang/mir/__marker_for_make__
CLASS_FILES_FE                = $(CLASSES_DIR)/dev/flang/fe/__marker_for_make__
CLASS_FILES_AIR               = $(CLASSES_DIR)/dev/flang/air/__marker_for_make__
CLASS_FILES_ME                = $(CLASSES_DIR)/dev/flang/me/__marker_for_make__
CLASS_FILES_FUIR              = $(CLASSES_DIR)/dev/flang/fuir/__marker_for_make__
CLASS_FILES_FUIR_ANALYSIS     = $(CLASSES_DIR)/dev/flang/fuir/analysis/__marker_for_make__
CLASS_FILES_FUIR_ANALYSIS_DFA = $(CLASSES_DIR)/dev/flang/fuir/analysis/dfa/__marker_for_make__
CLASS_FILES_FUIR_CFG          = $(CLASSES_DIR)/dev/flang/fuir/cfg/__marker_for_make__
CLASS_FILES_OPT               = $(CLASSES_DIR)/dev/flang/opt/__marker_for_make__
CLASS_FILES_BE_INTERPRETER    = $(CLASSES_DIR)/dev/flang/be/interpreter/__marker_for_make__
CLASS_FILES_BE_C              = $(CLASSES_DIR)/dev/flang/be/c/__marker_for_make__
CLASS_FILES_BE_EFFECTS        = $(CLASSES_DIR)/dev/flang/be/effects/__marker_for_make__
CLASS_FILES_TOOLS             = $(CLASSES_DIR)/dev/flang/tools/__marker_for_make__
CLASS_FILES_TOOLS_FZJAVA      = $(CLASSES_DIR)/dev/flang/tools/fzjava/__marker_for_make__
CLASS_FILES_MISC_LOGO         = $(CLASSES_DIR)/dev/flang/misc/logo/__marker_for_make__

JFREE_SVG_URL = https://repo1.maven.org/maven2/org/jfree/org.jfree.svg/5.0.1/org.jfree.svg-5.0.1.jar
JARS_JFREE_SVG_JAR = $(BUILD_DIR)/jars/org.jfree.svg-5.0.1.jar

FUZION_EBNF = $(BUILD_DIR)/fuzion.ebnf

FZ_SRC_LIB = $(FZ_SRC)/lib
FUZION_FILES_LIB = $(shell find $(FZ_SRC_LIB) -name "*.fz")

MOD_BASE              = $(BUILD_DIR)/modules/base.fum
MOD_TERMINAL          = $(BUILD_DIR)/modules/terminal.fum
MOD_JAVA_BASE_DIR              = $(BUILD_DIR)/modules/java.base
MOD_JAVA_XML_DIR               = $(BUILD_DIR)/modules/java.xml
MOD_JAVA_DATATRANSFER_DIR      = $(BUILD_DIR)/modules/java.datatransfer
MOD_JAVA_DESKTOP_DIR           = $(BUILD_DIR)/modules/java.desktop
MOD_JAVA_BASE                  = $(MOD_JAVA_BASE_DIR).fum
MOD_JAVA_XML                   = $(MOD_JAVA_XML_DIR).fum
MOD_JAVA_DATATRANSFER          = $(MOD_JAVA_DATATRANSFER_DIR).fum
MOD_JAVA_DESKTOP               = $(MOD_JAVA_DESKTOP_DIR).fum
MOD_JAVA_BASE_FZ_FILES         = $(MOD_JAVA_BASE_DIR)/__marker_for_make__
MOD_JAVA_XML_FZ_FILES          = $(MOD_JAVA_XML_DIR)/__marker_for_make__
MOD_JAVA_DATATRANSFER_FZ_FILES = $(MOD_JAVA_DATATRANSFER_DIR)/__marker_for_make__
MOD_JAVA_DESKTOP_FZ_FILES      = $(MOD_JAVA_DESKTOP_DIR)/__marker_for_make__
MOD_JAVA_COMPILER_DIR = $(BUILD_DIR)/modules/java.compiler
MOD_JAVA_INSTRUMENT_DIR = $(BUILD_DIR)/modules/java.instrument
MOD_JAVA_LOGGING_DIR = $(BUILD_DIR)/modules/java.logging
MOD_JAVA_MANAGEMENT_DIR = $(BUILD_DIR)/modules/java.management
MOD_JAVA_MANAGEMENT_RMI_DIR = $(BUILD_DIR)/modules/java.management.rmi
MOD_JAVA_NAMING_DIR = $(BUILD_DIR)/modules/java.naming
MOD_JAVA_NET_HTTP_DIR = $(BUILD_DIR)/modules/java.net.http
MOD_JAVA_PREFS_DIR = $(BUILD_DIR)/modules/java.prefs
MOD_JAVA_RMI_DIR = $(BUILD_DIR)/modules/java.rmi
MOD_JAVA_SCRIPTING_DIR = $(BUILD_DIR)/modules/java.scripting
MOD_JAVA_SE_DIR = $(BUILD_DIR)/modules/java.se
MOD_JAVA_SECURITY_JGSS_DIR = $(BUILD_DIR)/modules/java.security.jgss
MOD_JAVA_SECURITY_SASL_DIR = $(BUILD_DIR)/modules/java.security.sasl
MOD_JAVA_SMARTCARDIO_DIR = $(BUILD_DIR)/modules/java.smartcardio
MOD_JAVA_SQL_DIR = $(BUILD_DIR)/modules/java.sql
MOD_JAVA_SQL_ROWSET_DIR = $(BUILD_DIR)/modules/java.sql.rowset
MOD_JAVA_TRANSACTION_XA_DIR = $(BUILD_DIR)/modules/java.transaction.xa
MOD_JAVA_XML_CRYPTO_DIR = $(BUILD_DIR)/modules/java.xml.crypto
MOD_JDK_ACCESSIBILITY_DIR = $(BUILD_DIR)/modules/jdk.accessibility
MOD_JDK_ATTACH_DIR = $(BUILD_DIR)/modules/jdk.attach
MOD_JDK_CHARSETS_DIR = $(BUILD_DIR)/modules/jdk.charsets
MOD_JDK_COMPILER_DIR = $(BUILD_DIR)/modules/jdk.compiler
MOD_JDK_CRYPTO_CRYPTOKI_DIR = $(BUILD_DIR)/modules/jdk.crypto.cryptoki
MOD_JDK_CRYPTO_EC_DIR = $(BUILD_DIR)/modules/jdk.crypto.ec
MOD_JDK_DYNALINK_DIR = $(BUILD_DIR)/modules/jdk.dynalink
MOD_JDK_EDITPAD_DIR = $(BUILD_DIR)/modules/jdk.editpad
MOD_JDK_HTTPSERVER_DIR = $(BUILD_DIR)/modules/jdk.httpserver
MOD_JDK_JARTOOL_DIR = $(BUILD_DIR)/modules/jdk.jartool
MOD_JDK_JAVADOC_DIR = $(BUILD_DIR)/modules/jdk.javadoc
MOD_JDK_JCONSOLE_DIR = $(BUILD_DIR)/modules/jdk.jconsole
MOD_JDK_JDEPS_DIR = $(BUILD_DIR)/modules/jdk.jdeps
MOD_JDK_JDI_DIR = $(BUILD_DIR)/modules/jdk.jdi
MOD_JDK_JDWP_AGENT_DIR = $(BUILD_DIR)/modules/jdk.jdwp.agent
MOD_JDK_JFR_DIR = $(BUILD_DIR)/modules/jdk.jfr
MOD_JDK_JLINK_DIR = $(BUILD_DIR)/modules/jdk.jlink
MOD_JDK_JPACKAGE_DIR = $(BUILD_DIR)/modules/jdk.jpackage
MOD_JDK_JSHELL_DIR = $(BUILD_DIR)/modules/jdk.jshell
MOD_JDK_JSOBJECT_DIR = $(BUILD_DIR)/modules/jdk.jsobject
MOD_JDK_JSTATD_DIR = $(BUILD_DIR)/modules/jdk.jstatd
MOD_JDK_LOCALEDATA_DIR = $(BUILD_DIR)/modules/jdk.localedata
MOD_JDK_MANAGEMENT_DIR = $(BUILD_DIR)/modules/jdk.management
MOD_JDK_MANAGEMENT_AGENT_DIR = $(BUILD_DIR)/modules/jdk.management.agent
MOD_JDK_MANAGEMENT_JFR_DIR = $(BUILD_DIR)/modules/jdk.management.jfr
MOD_JDK_NAMING_DNS_DIR = $(BUILD_DIR)/modules/jdk.naming.dns
MOD_JDK_NAMING_RMI_DIR = $(BUILD_DIR)/modules/jdk.naming.rmi
MOD_JDK_NET_DIR = $(BUILD_DIR)/modules/jdk.net
MOD_JDK_NIO_MAPMODE_DIR = $(BUILD_DIR)/modules/jdk.nio.mapmode
MOD_JDK_SCTP_DIR = $(BUILD_DIR)/modules/jdk.sctp
MOD_JDK_SECURITY_AUTH_DIR = $(BUILD_DIR)/modules/jdk.security.auth
MOD_JDK_SECURITY_JGSS_DIR = $(BUILD_DIR)/modules/jdk.security.jgss
MOD_JDK_XML_DOM_DIR = $(BUILD_DIR)/modules/jdk.xml.dom
MOD_JDK_ZIPFS_DIR = $(BUILD_DIR)/modules/jdk.zipfs
MOD_JAVA_COMPILER = $(MOD_JAVA_COMPILER_DIR).fum
MOD_JAVA_INSTRUMENT = $(MOD_JAVA_INSTRUMENT_DIR).fum
MOD_JAVA_LOGGING = $(MOD_JAVA_LOGGING_DIR).fum
MOD_JAVA_MANAGEMENT = $(MOD_JAVA_MANAGEMENT_DIR).fum
MOD_JAVA_MANAGEMENT_RMI = $(MOD_JAVA_MANAGEMENT_RMI_DIR).fum
MOD_JAVA_NAMING = $(MOD_JAVA_NAMING_DIR).fum
MOD_JAVA_NET_HTTP = $(MOD_JAVA_NET_HTTP_DIR).fum
MOD_JAVA_PREFS = $(MOD_JAVA_PREFS_DIR).fum
MOD_JAVA_RMI = $(MOD_JAVA_RMI_DIR).fum
MOD_JAVA_SCRIPTING = $(MOD_JAVA_SCRIPTING_DIR).fum
MOD_JAVA_SE = $(MOD_JAVA_SE_DIR).fum
MOD_JAVA_SECURITY_JGSS = $(MOD_JAVA_SECURITY_JGSS_DIR).fum
MOD_JAVA_SECURITY_SASL = $(MOD_JAVA_SECURITY_SASL_DIR).fum
MOD_JAVA_SMARTCARDIO = $(MOD_JAVA_SMARTCARDIO_DIR).fum
MOD_JAVA_SQL = $(MOD_JAVA_SQL_DIR).fum
MOD_JAVA_SQL_ROWSET = $(MOD_JAVA_SQL_ROWSET_DIR).fum
MOD_JAVA_TRANSACTION_XA = $(MOD_JAVA_TRANSACTION_XA_DIR).fum
MOD_JAVA_XML_CRYPTO = $(MOD_JAVA_XML_CRYPTO_DIR).fum
MOD_JDK_ACCESSIBILITY = $(MOD_JDK_ACCESSIBILITY_DIR).fum
MOD_JDK_ATTACH = $(MOD_JDK_ATTACH_DIR).fum
MOD_JDK_CHARSETS = $(MOD_JDK_CHARSETS_DIR).fum
MOD_JDK_COMPILER = $(MOD_JDK_COMPILER_DIR).fum
MOD_JDK_CRYPTO_CRYPTOKI = $(MOD_JDK_CRYPTO_CRYPTOKI_DIR).fum
MOD_JDK_CRYPTO_EC = $(MOD_JDK_CRYPTO_EC_DIR).fum
MOD_JDK_DYNALINK = $(MOD_JDK_DYNALINK_DIR).fum
MOD_JDK_EDITPAD = $(MOD_JDK_EDITPAD_DIR).fum
MOD_JDK_HTTPSERVER = $(MOD_JDK_HTTPSERVER_DIR).fum
MOD_JDK_JARTOOL = $(MOD_JDK_JARTOOL_DIR).fum
MOD_JDK_JAVADOC = $(MOD_JDK_JAVADOC_DIR).fum
MOD_JDK_JCONSOLE = $(MOD_JDK_JCONSOLE_DIR).fum
MOD_JDK_JDEPS = $(MOD_JDK_JDEPS_DIR).fum
MOD_JDK_JDI = $(MOD_JDK_JDI_DIR).fum
MOD_JDK_JDWP_AGENT = $(MOD_JDK_JDWP_AGENT_DIR).fum
MOD_JDK_JFR = $(MOD_JDK_JFR_DIR).fum
MOD_JDK_JLINK = $(MOD_JDK_JLINK_DIR).fum
MOD_JDK_JPACKAGE = $(MOD_JDK_JPACKAGE_DIR).fum
MOD_JDK_JSHELL = $(MOD_JDK_JSHELL_DIR).fum
MOD_JDK_JSOBJECT = $(MOD_JDK_JSOBJECT_DIR).fum
MOD_JDK_JSTATD = $(MOD_JDK_JSTATD_DIR).fum
MOD_JDK_LOCALEDATA = $(MOD_JDK_LOCALEDATA_DIR).fum
MOD_JDK_MANAGEMENT = $(MOD_JDK_MANAGEMENT_DIR).fum
MOD_JDK_MANAGEMENT_AGENT = $(MOD_JDK_MANAGEMENT_AGENT_DIR).fum
MOD_JDK_MANAGEMENT_JFR = $(MOD_JDK_MANAGEMENT_JFR_DIR).fum
MOD_JDK_NAMING_DNS = $(MOD_JDK_NAMING_DNS_DIR).fum
MOD_JDK_NAMING_RMI = $(MOD_JDK_NAMING_RMI_DIR).fum
MOD_JDK_NET = $(MOD_JDK_NET_DIR).fum
MOD_JDK_NIO_MAPMODE = $(MOD_JDK_NIO_MAPMODE_DIR).fum
MOD_JDK_SCTP = $(MOD_JDK_SCTP_DIR).fum
MOD_JDK_SECURITY_AUTH = $(MOD_JDK_SECURITY_AUTH_DIR).fum
MOD_JDK_SECURITY_JGSS = $(MOD_JDK_SECURITY_JGSS_DIR).fum
MOD_JDK_XML_DOM = $(MOD_JDK_XML_DOM_DIR).fum
MOD_JDK_ZIPFS = $(MOD_JDK_ZIPFS_DIR).fum
MOD_JAVA_COMPILER_FZ_FILES = $(MOD_JAVA_COMPILER_DIR)/__marker_for_make__
MOD_JAVA_INSTRUMENT_FZ_FILES = $(MOD_JAVA_INSTRUMENT_DIR)/__marker_for_make__
MOD_JAVA_LOGGING_FZ_FILES = $(MOD_JAVA_LOGGING_DIR)/__marker_for_make__
MOD_JAVA_MANAGEMENT_FZ_FILES = $(MOD_JAVA_MANAGEMENT_DIR)/__marker_for_make__
MOD_JAVA_MANAGEMENT_RMI_FZ_FILES = $(MOD_JAVA_MANAGEMENT_RMI_DIR)/__marker_for_make__
MOD_JAVA_NAMING_FZ_FILES = $(MOD_JAVA_NAMING_DIR)/__marker_for_make__
MOD_JAVA_NET_HTTP_FZ_FILES = $(MOD_JAVA_NET_HTTP_DIR)/__marker_for_make__
MOD_JAVA_PREFS_FZ_FILES = $(MOD_JAVA_PREFS_DIR)/__marker_for_make__
MOD_JAVA_RMI_FZ_FILES = $(MOD_JAVA_RMI_DIR)/__marker_for_make__
MOD_JAVA_SCRIPTING_FZ_FILES = $(MOD_JAVA_SCRIPTING_DIR)/__marker_for_make__
MOD_JAVA_SE_FZ_FILES = $(MOD_JAVA_SE_DIR)/__marker_for_make__
MOD_JAVA_SECURITY_JGSS_FZ_FILES = $(MOD_JAVA_SECURITY_JGSS_DIR)/__marker_for_make__
MOD_JAVA_SECURITY_SASL_FZ_FILES = $(MOD_JAVA_SECURITY_SASL_DIR)/__marker_for_make__
MOD_JAVA_SMARTCARDIO_FZ_FILES = $(MOD_JAVA_SMARTCARDIO_DIR)/__marker_for_make__
MOD_JAVA_SQL_FZ_FILES = $(MOD_JAVA_SQL_DIR)/__marker_for_make__
MOD_JAVA_SQL_ROWSET_FZ_FILES = $(MOD_JAVA_SQL_ROWSET_DIR)/__marker_for_make__
MOD_JAVA_TRANSACTION_XA_FZ_FILES = $(MOD_JAVA_TRANSACTION_XA_DIR)/__marker_for_make__
MOD_JAVA_XML_CRYPTO_FZ_FILES = $(MOD_JAVA_XML_CRYPTO_DIR)/__marker_for_make__
MOD_JDK_ACCESSIBILITY_FZ_FILES = $(MOD_JDK_ACCESSIBILITY_DIR)/__marker_for_make__
MOD_JDK_ATTACH_FZ_FILES = $(MOD_JDK_ATTACH_DIR)/__marker_for_make__
MOD_JDK_CHARSETS_FZ_FILES = $(MOD_JDK_CHARSETS_DIR)/__marker_for_make__
MOD_JDK_COMPILER_FZ_FILES = $(MOD_JDK_COMPILER_DIR)/__marker_for_make__
MOD_JDK_CRYPTO_CRYPTOKI_FZ_FILES = $(MOD_JDK_CRYPTO_CRYPTOKI_DIR)/__marker_for_make__
MOD_JDK_CRYPTO_EC_FZ_FILES = $(MOD_JDK_CRYPTO_EC_DIR)/__marker_for_make__
MOD_JDK_DYNALINK_FZ_FILES = $(MOD_JDK_DYNALINK_DIR)/__marker_for_make__
MOD_JDK_EDITPAD_FZ_FILES = $(MOD_JDK_EDITPAD_DIR)/__marker_for_make__
MOD_JDK_HTTPSERVER_FZ_FILES = $(MOD_JDK_HTTPSERVER_DIR)/__marker_for_make__
MOD_JDK_JARTOOL_FZ_FILES = $(MOD_JDK_JARTOOL_DIR)/__marker_for_make__
MOD_JDK_JAVADOC_FZ_FILES = $(MOD_JDK_JAVADOC_DIR)/__marker_for_make__
MOD_JDK_JCONSOLE_FZ_FILES = $(MOD_JDK_JCONSOLE_DIR)/__marker_for_make__
MOD_JDK_JDEPS_FZ_FILES = $(MOD_JDK_JDEPS_DIR)/__marker_for_make__
MOD_JDK_JDI_FZ_FILES = $(MOD_JDK_JDI_DIR)/__marker_for_make__
MOD_JDK_JDWP_AGENT_FZ_FILES = $(MOD_JDK_JDWP_AGENT_DIR)/__marker_for_make__
MOD_JDK_JFR_FZ_FILES = $(MOD_JDK_JFR_DIR)/__marker_for_make__
MOD_JDK_JLINK_FZ_FILES = $(MOD_JDK_JLINK_DIR)/__marker_for_make__
MOD_JDK_JPACKAGE_FZ_FILES = $(MOD_JDK_JPACKAGE_DIR)/__marker_for_make__
MOD_JDK_JSHELL_FZ_FILES = $(MOD_JDK_JSHELL_DIR)/__marker_for_make__
MOD_JDK_JSOBJECT_FZ_FILES = $(MOD_JDK_JSOBJECT_DIR)/__marker_for_make__
MOD_JDK_JSTATD_FZ_FILES = $(MOD_JDK_JSTATD_DIR)/__marker_for_make__
MOD_JDK_LOCALEDATA_FZ_FILES = $(MOD_JDK_LOCALEDATA_DIR)/__marker_for_make__
MOD_JDK_MANAGEMENT_FZ_FILES = $(MOD_JDK_MANAGEMENT_DIR)/__marker_for_make__
MOD_JDK_MANAGEMENT_AGENT_FZ_FILES = $(MOD_JDK_MANAGEMENT_AGENT_DIR)/__marker_for_make__
MOD_JDK_MANAGEMENT_JFR_FZ_FILES = $(MOD_JDK_MANAGEMENT_JFR_DIR)/__marker_for_make__
MOD_JDK_NAMING_DNS_FZ_FILES = $(MOD_JDK_NAMING_DNS_DIR)/__marker_for_make__
MOD_JDK_NAMING_RMI_FZ_FILES = $(MOD_JDK_NAMING_RMI_DIR)/__marker_for_make__
MOD_JDK_NET_FZ_FILES = $(MOD_JDK_NET_DIR)/__marker_for_make__
MOD_JDK_NIO_MAPMODE_FZ_FILES = $(MOD_JDK_NIO_MAPMODE_DIR)/__marker_for_make__
MOD_JDK_SCTP_FZ_FILES = $(MOD_JDK_SCTP_DIR)/__marker_for_make__
MOD_JDK_SECURITY_AUTH_FZ_FILES = $(MOD_JDK_SECURITY_AUTH_DIR)/__marker_for_make__
MOD_JDK_SECURITY_JGSS_FZ_FILES = $(MOD_JDK_SECURITY_JGSS_DIR)/__marker_for_make__
MOD_JDK_XML_DOM_FZ_FILES = $(MOD_JDK_XML_DOM_DIR)/__marker_for_make__
MOD_JDK_ZIPFS_FZ_FILES = $(MOD_JDK_ZIPFS_DIR)/__marker_for_make__

VERSION = $(shell cat $(FZ_SRC)/version.txt)

FUZION_BASE = \
			$(BUILD_DIR)/bin/fz \
			$(BUILD_DIR)/bin/fzjava \
			$(MOD_BASE) \
			$(MOD_TERMINAL)

# NYI: This is missing the following modules from JDK 17:
#
# - jdk.hotspot.agent
# - jdk.jcmd
# - jdk.incubator.concurrent
# - jdk.incubator.vector
#
# These cause odd ClassNotFound errors.
FUZION_JAVA_MODULES = \
					$(MOD_JAVA_BASE) \
					$(MOD_JAVA_XML) \
					$(MOD_JAVA_DATATRANSFER) \
					$(MOD_JAVA_DESKTOP) \
					$(MOD_JAVA_COMPILER) \
					$(MOD_JAVA_INSTRUMENT) \
					$(MOD_JAVA_LOGGING) \
					$(MOD_JAVA_MANAGEMENT) \
					$(MOD_JAVA_MANAGEMENT_RMI) \
					$(MOD_JAVA_NAMING) \
					$(MOD_JAVA_NET_HTTP) \
					$(MOD_JAVA_PREFS) \
					$(MOD_JAVA_RMI) \
					$(MOD_JAVA_SCRIPTING) \
					$(MOD_JAVA_SE) \
					$(MOD_JAVA_SECURITY_JGSS) \
					$(MOD_JAVA_SECURITY_SASL) \
					$(MOD_JAVA_SMARTCARDIO) \
					$(MOD_JAVA_SQL) \
					$(MOD_JAVA_SQL_ROWSET) \
					$(MOD_JAVA_TRANSACTION_XA) \
					$(MOD_JAVA_XML_CRYPTO) \
					$(MOD_JDK_ACCESSIBILITY) \
					$(MOD_JDK_ATTACH) \
					$(MOD_JDK_CHARSETS) \
					$(MOD_JDK_COMPILER) \
					$(MOD_JDK_CRYPTO_CRYPTOKI) \
					$(MOD_JDK_CRYPTO_EC) \
					$(MOD_JDK_DYNALINK) \
					$(MOD_JDK_EDITPAD) \
					$(MOD_JDK_HTTPSERVER) \
					$(MOD_JDK_JARTOOL) \
					$(MOD_JDK_JAVADOC) \
					$(MOD_JDK_JCONSOLE) \
					$(MOD_JDK_JDEPS) \
					$(MOD_JDK_JDI) \
					$(MOD_JDK_JDWP_AGENT) \
					$(MOD_JDK_JFR) \
					$(MOD_JDK_JLINK) \
					$(MOD_JDK_JPACKAGE) \
					$(MOD_JDK_JSHELL) \
					$(MOD_JDK_JSOBJECT) \
					$(MOD_JDK_JSTATD) \
					$(MOD_JDK_LOCALEDATA) \
					$(MOD_JDK_MANAGEMENT) \
					$(MOD_JDK_MANAGEMENT_AGENT) \
					$(MOD_JDK_MANAGEMENT_JFR) \
					$(MOD_JDK_NAMING_DNS) \
					$(MOD_JDK_NAMING_RMI) \
					$(MOD_JDK_NET) \
					$(MOD_JDK_NIO_MAPMODE) \
					$(MOD_JDK_SCTP) \
					$(MOD_JDK_SECURITY_AUTH) \
					$(MOD_JDK_SECURITY_JGSS) \
					$(MOD_JDK_XML_DOM) \
					$(MOD_JDK_ZIPFS)

FUZION_FILES = \
			 $(BUILD_DIR)/tests \
			 $(BUILD_DIR)/examples \
			 $(BUILD_DIR)/README.md \
			 $(BUILD_DIR)/release_notes.md

DOCUMENTATION = \
	$(BUILD_DIR)/doc/fumfile.html     # fum file format documentation created with asciidoc

SHELL_SCRIPTS = \
	bin/fz \
	bin/fzjava

.PHONY: all
all: $(FUZION_BASE) $(FUZION_JAVA_MODULES) $(FUZION_FILES)

.PHONY: without-java-modules
without-java-modules: $(FUZION_BASE) $(FUZION_FILES)

# phony target to compile all java sources
.PHONY: javac
javac: $(CLASS_FILES_TOOLS) $(CLASS_FILES_TOOLS_FZJAVA)

$(BUILD_DIR)/%.md: $(FZ_SRC)/%.md
	cp $^ $@

$(FUZION_EBNF): $(SRC)/dev/flang/parser/Parser.java
	mkdir -p $(@D)
	$(FZ_SRC)/bin/ebnf.sh > $@

$(JAVA_FILE_TOOLS_VERSION): $(FZ_SRC)/version.txt $(JAVA_FILE_TOOLS_VERSION_IN)
	mkdir -p $(@D)
	cat $(JAVA_FILE_TOOLS_VERSION_IN) \
          | sed "s^@@VERSION@@^$(VERSION)^g" \
          | sed "s^@@GIT_HASH@@^`cd $(FZ_SRC); echo -n \`git rev-parse HEAD\` \`git diff-index --quiet HEAD -- || echo with local changes\``^g" \
          | sed "s^@@DATE@@^`date +%Y-%m-%d\ %H:%M:%S`^g"  \
          | sed "s^@@BUILTBY@@^`echo -n $(USER)@; hostname`^g" >$@

$(CLASS_FILES_UTIL): $(JAVA_FILES_UTIL)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -d $(CLASSES_DIR) $(JAVA_FILES_UTIL)
	touch $@

$(CLASS_FILES_UTIL_UNICODE): $(JAVA_FILES_UTIL_UNICODE) $(CLASS_FILES_UTIL)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_UTIL_UNICODE)
	touch $@

$(CLASS_FILES_AST): $(JAVA_FILES_AST) $(CLASS_FILES_UTIL)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_AST)
	touch $@

$(CLASS_FILES_PARSER): $(JAVA_FILES_PARSER) $(CLASS_FILES_AST) $(FUZION_EBNF)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_PARSER)
	touch $@

$(CLASS_FILES_IR): $(JAVA_FILES_IR) $(CLASS_FILES_UTIL) $(CLASS_FILES_AST)  # NYI: remove dependency on $(CLASS_FILES_AST)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_IR)
	touch $@

$(CLASS_FILES_MIR): $(JAVA_FILES_MIR) $(CLASS_FILES_IR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_MIR)
	touch $@

$(CLASS_FILES_FE): $(JAVA_FILES_FE) $(CLASS_FILES_PARSER) $(CLASS_FILES_AST) $(CLASS_FILES_MIR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_FE)
	touch $@

$(CLASS_FILES_AIR): $(JAVA_FILES_AIR) $(CLASS_FILES_UTIL) $(CLASS_FILES_IR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_AIR)
	touch $@

$(CLASS_FILES_ME): $(JAVA_FILES_ME) $(CLASS_FILES_MIR) $(CLASS_FILES_AIR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_ME)
	touch $@

$(CLASS_FILES_FUIR): $(JAVA_FILES_FUIR) $(CLASS_FILES_UTIL) $(CLASS_FILES_IR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_FUIR)
	touch $@

$(CLASS_FILES_FUIR_ANALYSIS): $(JAVA_FILES_FUIR_ANALYSIS) $(CLASS_FILES_UTIL) $(CLASS_FILES_FUIR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_FUIR_ANALYSIS)
	touch $@

$(CLASS_FILES_FUIR_ANALYSIS_DFA): $(JAVA_FILES_FUIR_ANALYSIS_DFA) $(CLASS_FILES_FUIR_ANALYSIS) $(CLASS_FILES_UTIL) $(CLASS_FILES_FUIR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_FUIR_ANALYSIS_DFA)
	touch $@

$(CLASS_FILES_FUIR_CFG): $(JAVA_FILES_FUIR_CFG) $(CLASS_FILES_UTIL) $(CLASS_FILES_FUIR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_FUIR_CFG)
	touch $@

$(CLASS_FILES_OPT): $(JAVA_FILES_OPT) $(CLASS_FILES_AIR) $(CLASS_FILES_FUIR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_OPT)
	touch $@

$(CLASS_FILES_BE_INTERPRETER): $(JAVA_FILES_BE_INTERPRETER) $(CLASS_FILES_FUIR) $(CLASS_FILES_AIR) $(CLASS_FILES_AST)  # NYI: remove dependency on $(CLASS_FILES_AST), replace by $(CLASS_FILES_FUIR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_BE_INTERPRETER)
	touch $@

$(CLASS_FILES_BE_C): $(JAVA_FILES_BE_C) $(CLASS_FILES_FUIR) $(CLASS_FILES_FUIR_ANALYSIS_DFA)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_BE_C)
	touch $@

$(CLASS_FILES_BE_EFFECTS): $(JAVA_FILES_BE_EFFECTS) $(CLASS_FILES_FUIR_CFG)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_BE_EFFECTS)
	touch $@

$(CLASS_FILES_TOOLS): $(JAVA_FILES_TOOLS) $(CLASS_FILES_FE) $(CLASS_FILES_ME) $(CLASS_FILES_OPT) $(CLASS_FILES_BE_C) $(CLASS_FILES_FUIR_ANALYSIS_DFA) $(CLASS_FILES_BE_EFFECTS) $(CLASS_FILES_BE_INTERPRETER)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_TOOLS)
	touch $@

$(CLASS_FILES_TOOLS_FZJAVA): $(JAVA_FILES_TOOLS_FZJAVA) $(CLASS_FILES_TOOLS) $(CLASS_FILES_PARSER) $(CLASS_FILES_UTIL)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_TOOLS_FZJAVA)
	touch $@

$(JARS_JFREE_SVG_JAR):
	mkdir -p $(@D)
	curl $(JFREE_SVG_URL) --output $@

$(CLASS_FILES_MISC_LOGO): $(JAVA_FILES_MISC_LOGO) $(JARS_JFREE_SVG_JAR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR):$(JARS_JFREE_SVG_JAR) -d $(CLASSES_DIR) $(JAVA_FILES_MISC_LOGO)
	touch $@

$(BUILD_DIR)/assets/logo.svg: $(CLASS_FILES_MISC_LOGO)
	mkdir -p $(@D)
	$(JAVA) -cp $(CLASSES_DIR):$(JARS_JFREE_SVG_JAR) dev.flang.misc.logo.FuzionLogo $@
	inkscape $@ -o $@.pdf
	touch $@

$(BUILD_DIR)/assets/logo_bleed.svg: $(CLASS_FILES_MISC_LOGO)
	mkdir -p $(@D)
	$(JAVA) -cp $(CLASSES_DIR):$(JARS_JFREE_SVG_JAR) dev.flang.misc.logo.FuzionLogo -b $@
	inkscape $@ -o $@.tmp.pdf
	pdfjam --papersize '{46mm,46mm}' --outfile $@.pdf $@.tmp.pdf
	rm -f $@.tmp.pdf
	touch $@

$(BUILD_DIR)/assets/logo_bleed_cropmark.svg: $(CLASS_FILES_MISC_LOGO)
	mkdir -p $(@D)
	$(JAVA) -cp $(CLASSES_DIR):$(JARS_JFREE_SVG_JAR) dev.flang.misc.logo.FuzionLogo -c $@
	inkscape $@ -o $@.tmp.pdf
	pdfjam --papersize '{46mm,46mm}' --outfile $@.pdf $@.tmp.pdf
	rm -f $@.tmp.pdf
	touch $@

$(BUILD_DIR)/lib: $(FUZION_FILES_LIB)
	rm -rf $@
	mkdir -p $(@D)
	cp -rf $(FZ_SRC_LIB) $@

$(BUILD_DIR)/bin/fz: $(FZ_SRC)/bin/fz $(CLASS_FILES_TOOLS) $(BUILD_DIR)/lib
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/bin/fz $@
	chmod +x $@

$(MOD_BASE): $(BUILD_DIR)/lib $(BUILD_DIR)/bin/fz
	mkdir -p $(@D)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(BUILD_DIR)/lib -XloadBaseLib=off -saveLib=$@
	$(BUILD_DIR)/bin/fz -XXcheckIntrinsics

# keep make from deleting $(MOD_BASE) on ctrl-C:
.PRECIOUS: $(MOD_BASE)

$(MOD_TERMINAL): $(MOD_BASE) $(BUILD_DIR)/bin/fz $(FZ_SRC)/modules/terminal/src/terminal.fz
	mkdir -p $(@D)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(FZ_SRC)/modules/terminal/src -saveLib=$@

$(BUILD_DIR)/bin/fzjava: $(FZ_SRC)/bin/fzjava $(CLASS_FILES_TOOLS_FZJAVA)
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/bin/fzjava $@
	chmod +x $@

$(MOD_JAVA_BASE_FZ_FILES): $(MOD_BASE) $(BUILD_DIR)/bin/fzjava
	rm -rf $(@D)
	mkdir -p $(@D)
# wrapping in /bin/bash -c "..." is a workaround for building on windows, bash (mingw)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.base -to=$(@D) -verbose=0"
	touch $@

$(MOD_JAVA_XML_FZ_FILES): $(BUILD_DIR)/bin/fzjava
	rm -rf $(@D)
	mkdir -p $(@D)
# wrapping in /bin/bash -c "..." is a workaround for building on windows, bash (mingw)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.xml -to=$(@D) -modules=java.base -verbose=0"
	touch $@

$(MOD_JAVA_DATATRANSFER_FZ_FILES): $(BUILD_DIR)/bin/fzjava
	rm -rf $(@D)
	mkdir -p $(@D)
# wrapping in /bin/bash -c "..." is a workaround for building on windows, bash (mingw)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.datatransfer -to=$(@D) -modules=java.base,java.xml -verbose=0"
# NYI: cleanup: see #462: manually move these features to the main directory
# since otherwise they would not be found automatically.
	mv $(BUILD_DIR)/modules/java.datatransfer/Java/sun/datatransfer_pkg.fz $(BUILD_DIR)/modules/java.datatransfer/
	mv $(BUILD_DIR)/modules/java.datatransfer/Java/java/awt_pkg.fz  $(BUILD_DIR)/modules/java.datatransfer/
	touch $@

$(MOD_JAVA_DESKTOP_FZ_FILES): $(BUILD_DIR)/bin/fzjava
	rm -rf $(@D)
	mkdir -p $(@D)
# wrapping in /bin/bash -c "..." is a workaround for building on windows, bash (mingw)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.desktop -to=$(@D) -modules=java.base,java.xml,java.datatransfer -verbose=0"
# NYI: cleanup: see #462: manually move these features to the main directory
# since otherwise they would not be found automatically.
	mv $(BUILD_DIR)/modules/java.desktop/Java/com/sun/*.fz $(BUILD_DIR)/modules/java.desktop/
	mv $(BUILD_DIR)/modules/java.desktop/Java/java/*.fz $(BUILD_DIR)/modules/java.desktop/
	mv $(BUILD_DIR)/modules/java.desktop/Java/javax/*.fz $(BUILD_DIR)/modules/java.desktop/
	mv $(BUILD_DIR)/modules/java.desktop/Java/sun/swing_pkg.fz $(BUILD_DIR)/modules/java.desktop/sun_swing_pkg.fz
	mv $(BUILD_DIR)/modules/java.desktop/Java/sun/print_pkg.fz $(BUILD_DIR)/modules/java.desktop/sun_print_pkg.fz
	mv $(BUILD_DIR)/modules/java.desktop/Java/sun/*.fz $(BUILD_DIR)/modules/java.desktop/
	touch $@

$(MOD_JAVA_COMPILER_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.compiler -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_INSTRUMENT_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.instrument -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_LOGGING_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.logging -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_MANAGEMENT_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.management -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_MANAGEMENT_RMI_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE) $(MOD_JAVA_MANAGEMENT) $(MOD_JAVA_RMI)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.management.rmi -to=$(@D) -modules=java.base,java.management,java.rmi -verbose=0"
	touch $@
$(MOD_JAVA_NAMING_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.naming -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_NET_HTTP_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.net.http -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_PREFS_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.prefs -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_RMI_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.rmi -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_SCRIPTING_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.scripting -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_SE_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE) $(MOD_JAVA_SQL_ROWSET) $(MOD_JAVA_XML_CRYPTO) $(MOD_JAVA_MANAGEMENT_RMI) $(MOD_JAVA_SECURITY_JGSS) $(MOD_JAVA_SECURITY_SASL) $(MOD_JAVA_SCRIPTING) $(MOD_JAVA_DESKTOP) $(MOD_JAVA_COMPILER) $(MOD_JAVA_INSTRUMENT) $(MOD_JAVA_NET_HTTP) $(MOD_JAVA_PREFS)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.se -to=$(@D) -modules=java.base,java.naming,java.transaction.xa,java.logging,java.scripting,java.xml,java.datatransfer,java.prefs,java.sql,java.desktop,java.compiler,java.instrument,java.rmi,java.management,java.net.http,java.sql.rowset,java.xml.crypto,java.management.rmi,java.security.jgss,java.security.sasl -verbose=0"
	touch $@
$(MOD_JAVA_SECURITY_JGSS_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.security.jgss -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_SECURITY_SASL_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.security.sasl -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_SMARTCARDIO_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.smartcardio -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_SQL_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE) $(MOD_JAVA_LOGGING) $(MOD_JAVA_XML) $(MOD_JAVA_TRANSACTION_XA)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.sql -to=$(@D) -modules=java.base,java.logging,java.xml,java.transaction.xa -verbose=0"
	touch $@
$(MOD_JAVA_SQL_ROWSET_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE) $(MOD_JAVA_SQL) $(MOD_JAVA_NAMING) $(MOD_JAVA_LOGGING) $(MOD_JAVA_XML) $(MOD_JAVA_TRANSACTION_XA)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.sql.rowset -to=$(@D) -modules=java.base,java.logging,java.xml,java.transaction.xa,java.sql,java.naming -verbose=0"
	touch $@
$(MOD_JAVA_TRANSACTION_XA_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.transaction.xa -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_XML_CRYPTO_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE) $(MOD_JAVA_XML)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.xml.crypto -to=$(@D) -modules=java.xml,java.base -verbose=0"
	touch $@
$(MOD_JDK_ACCESSIBILITY_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE) $(MOD_JAVA_DESKTOP)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.accessibility -to=$(@D) -modules=java.base,java.xml,java.datatransfer,java.desktop -verbose=0"
	touch $@
$(MOD_JDK_ATTACH_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.attach -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_CHARSETS_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.charsets -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_COMPILER_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE) $(MOD_JAVA_COMPILER)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.compiler -to=$(@D) -modules=java.base,java.compiler -verbose=0"
	touch $@
$(MOD_JDK_CRYPTO_CRYPTOKI_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.crypto.cryptoki -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_CRYPTO_EC_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.crypto.ec -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_DYNALINK_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.dynalink -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_EDITPAD_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.editpad -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_HTTPSERVER_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.httpserver -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JARTOOL_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.jartool -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JAVADOC_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE) $(MOD_JDK_COMPILER)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.javadoc -to=$(@D) -modules=java.base,java.compiler,jdk.compiler -verbose=0"
	touch $@
$(MOD_JDK_JCONSOLE_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE) $(MOD_JAVA_DESKTOP) $(MOD_JAVA_MANAGEMENT)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.jconsole -to=$(@D) -modules=java.base,java.xml,java.datatransfer,java.desktop,java.management -verbose=0"
	touch $@
$(MOD_JDK_JDEPS_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.jdeps -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JDI_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.jdi -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JDWP_AGENT_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.jdwp.agent -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JFR_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.jfr -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JLINK_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.jlink -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JPACKAGE_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.jpackage -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JSHELL_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE) $(MOD_JAVA_COMPILER) $(MOD_JAVA_PREFS) $(MOD_JDK_JDI)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.jshell -to=$(@D) -modules=java.base,java.compiler,java.prefs,jdk.jdi -verbose=0"
	touch $@
$(MOD_JDK_JSOBJECT_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.jsobject -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JSTATD_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.jstatd -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_LOCALEDATA_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.localedata -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_MANAGEMENT_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE) $(MOD_JAVA_MANAGEMENT)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.management -to=$(@D) -modules=java.base,java.management -verbose=0"
	touch $@
$(MOD_JDK_MANAGEMENT_AGENT_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.management.agent -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_MANAGEMENT_JFR_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE) $(MOD_JAVA_MANAGEMENT) $(MOD_JDK_JFR)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.management.jfr -to=$(@D) -modules=java.base,java.management,jdk.jfr -verbose=0"
	touch $@
$(MOD_JDK_NAMING_DNS_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.naming.dns -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_NAMING_RMI_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.naming.rmi -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_NET_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.net -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_NIO_MAPMODE_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.nio.mapmode -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_SCTP_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.sctp -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_SECURITY_AUTH_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE) $(MOD_JAVA_NAMING)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.security.auth -to=$(@D) -modules=java.base,java.naming -verbose=0"
	touch $@
$(MOD_JDK_SECURITY_JGSS_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE) $(MOD_JAVA_SECURITY_JGSS)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.security.jgss -to=$(@D) -modules=java.base,java.security.jgss -verbose=0"
	touch $@
$(MOD_JDK_XML_DOM_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE) $(MOD_JAVA_XML)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.xml.dom -to=$(@D) -modules=java.base,java.xml -verbose=0"
	touch $@
$(MOD_JDK_ZIPFS_FZ_FILES): $(BUILD_DIR)/bin/fzjava $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava jdk.zipfs -to=$(@D) -modules=java.base -verbose=0"
	touch $@

$(MOD_JAVA_BASE): $(MOD_JAVA_BASE_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(^D) -saveLib=$@

$(MOD_JAVA_XML): $(MOD_JAVA_BASE) $(MOD_JAVA_XML_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_XML_DIR) -modules=java.base -saveLib=$@

$(MOD_JAVA_DATATRANSFER): $(MOD_JAVA_BASE) $(MOD_JAVA_XML) $(MOD_JAVA_DATATRANSFER_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_DATATRANSFER_DIR) -modules=java.base,java.xml -saveLib=$@

$(MOD_JAVA_DESKTOP): $(MOD_JAVA_BASE) $(MOD_JAVA_XML) $(MOD_JAVA_DATATRANSFER) $(MOD_JAVA_DESKTOP_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_DESKTOP_DIR) -modules=java.base,java.xml,java.datatransfer -saveLib=$@

$(MOD_JAVA_COMPILER): $(MOD_JAVA_BASE) $(MOD_JAVA_COMPILER_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_COMPILER_DIR) -modules=java.base -saveLib=$@
$(MOD_JAVA_INSTRUMENT): $(MOD_JAVA_BASE) $(MOD_JAVA_INSTRUMENT_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_INSTRUMENT_DIR) -modules=java.base -saveLib=$@
$(MOD_JAVA_LOGGING): $(MOD_JAVA_BASE) $(MOD_JAVA_LOGGING_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_LOGGING_DIR) -modules=java.base -saveLib=$@
$(MOD_JAVA_MANAGEMENT): $(MOD_JAVA_BASE) $(MOD_JAVA_MANAGEMENT_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_MANAGEMENT_DIR) -modules=java.base -saveLib=$@
$(MOD_JAVA_MANAGEMENT_RMI): $(MOD_JAVA_BASE) $(MOD_JAVA_MANAGEMENT) $(MOD_JAVA_RMI) $(MOD_JAVA_MANAGEMENT_RMI_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_MANAGEMENT_RMI_DIR) -modules=java.base,java.management,java.rmi -saveLib=$@
$(MOD_JAVA_NAMING): $(MOD_JAVA_BASE) $(MOD_JAVA_NAMING_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_NAMING_DIR) -modules=java.base -saveLib=$@
$(MOD_JAVA_NET_HTTP): $(MOD_JAVA_BASE) $(MOD_JAVA_NET_HTTP_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_NET_HTTP_DIR) -modules=java.base -saveLib=$@
$(MOD_JAVA_PREFS): $(MOD_JAVA_BASE) $(MOD_JAVA_PREFS_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_PREFS_DIR) -modules=java.base -saveLib=$@
$(MOD_JAVA_RMI): $(MOD_JAVA_BASE) $(MOD_JAVA_RMI_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_RMI_DIR) -modules=java.base -saveLib=$@
$(MOD_JAVA_SCRIPTING): $(MOD_JAVA_BASE) $(MOD_JAVA_SCRIPTING_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_SCRIPTING_DIR) -modules=java.base -saveLib=$@
$(MOD_JAVA_SE): $(MOD_JAVA_BASE) $(MOD_JAVA_SQL_ROWSET) $(MOD_JAVA_XML_CRYPTO) $(MOD_JAVA_MANAGEMENT_RMI) $(MOD_JAVA_SECURITY_JGSS) $(MOD_JAVA_SECURITY_SASL) $(MOD_JAVA_SCRIPTING) $(MOD_JAVA_DESKTOP) $(MOD_JAVA_COMPILER) $(MOD_JAVA_INSTRUMENT) $(MOD_JAVA_NET_HTTP) $(MOD_JAVA_PREFS) $(MOD_JAVA_SE_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_SE_DIR) -modules=java.base,java.naming,java.transaction.xa,java.logging,java.scripting,java.xml,java.datatransfer,java.prefs,java.sql,java.desktop,java.compiler,java.instrument,java.rmi,java.management,java.net.http,java.sql.rowset,java.xml.crypto,java.management.rmi,java.security.jgss,java.security.sasl -saveLib=$@
$(MOD_JAVA_SECURITY_JGSS): $(MOD_JAVA_BASE) $(MOD_JAVA_SECURITY_JGSS_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_SECURITY_JGSS_DIR) -modules=java.base -saveLib=$@
$(MOD_JAVA_SECURITY_SASL): $(MOD_JAVA_BASE) $(MOD_JAVA_SECURITY_SASL_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_SECURITY_SASL_DIR) -modules=java.base -saveLib=$@
$(MOD_JAVA_SMARTCARDIO): $(MOD_JAVA_BASE) $(MOD_JAVA_SMARTCARDIO_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_SMARTCARDIO_DIR) -modules=java.base -saveLib=$@
$(MOD_JAVA_SQL): $(MOD_JAVA_BASE) $(MOD_JAVA_LOGGING) $(MOD_JAVA_XML) $(MOD_JAVA_TRANSACTION_XA) $(MOD_JAVA_SQL_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_SQL_DIR) -modules=java.base,java.logging,java.xml,java.transaction.xa -saveLib=$@
$(MOD_JAVA_SQL_ROWSET): $(MOD_JAVA_BASE) $(MOD_JAVA_SQL) $(MOD_JAVA_NAMING) $(MOD_JAVA_LOGGING) $(MOD_JAVA_XML) $(MOD_JAVA_TRANSACTION_XA) $(MOD_JAVA_SQL_ROWSET_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_SQL_ROWSET_DIR) -modules=java.base,java.sql,java.naming,java.logging,java.xml,java.transaction.xa -saveLib=$@
$(MOD_JAVA_TRANSACTION_XA): $(MOD_JAVA_BASE) $(MOD_JAVA_TRANSACTION_XA_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_TRANSACTION_XA_DIR) -modules=java.base -saveLib=$@
$(MOD_JAVA_XML_CRYPTO): $(MOD_JAVA_BASE) $(MOD_JAVA_XML) $(MOD_JAVA_XML_CRYPTO_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JAVA_XML_CRYPTO_DIR) -modules=java.base,java.xml -saveLib=$@
$(MOD_JDK_ACCESSIBILITY): $(MOD_JAVA_BASE) $(MOD_JAVA_DESKTOP) $(MOD_JDK_ACCESSIBILITY_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_ACCESSIBILITY_DIR) -modules=java.base,java.xml,java.datatransfer,java.desktop -saveLib=$@
$(MOD_JDK_ATTACH): $(MOD_JAVA_BASE) $(MOD_JDK_ATTACH_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_ATTACH_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_CHARSETS): $(MOD_JAVA_BASE) $(MOD_JDK_CHARSETS_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_CHARSETS_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_COMPILER): $(MOD_JAVA_BASE) $(MOD_JAVA_COMPILER) $(MOD_JDK_COMPILER_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_COMPILER_DIR) -modules=java.base,java.compiler -saveLib=$@
$(MOD_JDK_CRYPTO_CRYPTOKI): $(MOD_JAVA_BASE) $(MOD_JDK_CRYPTO_CRYPTOKI_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_CRYPTO_CRYPTOKI_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_CRYPTO_EC): $(MOD_JAVA_BASE) $(MOD_JDK_CRYPTO_EC_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_CRYPTO_EC_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_DYNALINK): $(MOD_JAVA_BASE) $(MOD_JDK_DYNALINK_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_DYNALINK_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_EDITPAD): $(MOD_JAVA_BASE) $(MOD_JDK_EDITPAD_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_EDITPAD_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_HTTPSERVER): $(MOD_JAVA_BASE) $(MOD_JDK_HTTPSERVER_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_HTTPSERVER_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_JARTOOL): $(MOD_JAVA_BASE) $(MOD_JDK_JARTOOL_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_JARTOOL_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_JAVADOC): $(MOD_JAVA_BASE) $(MOD_JDK_COMPILER) $(MOD_JDK_JAVADOC_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_JAVADOC_DIR) -modules=java.base,java.compiler,jdk.compiler -saveLib=$@
$(MOD_JDK_JCONSOLE): $(MOD_JAVA_BASE) $(MOD_JAVA_DESKTOP) $(MOD_JAVA_MANAGEMENT) $(MOD_JDK_JCONSOLE_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_JCONSOLE_DIR) -modules=java.base,java.xml,java.datatransfer,java.desktop,java.management -saveLib=$@
$(MOD_JDK_JDEPS): $(MOD_JAVA_BASE) $(MOD_JDK_JDEPS_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_JDEPS_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_JDI): $(MOD_JAVA_BASE) $(MOD_JDK_JDI_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_JDI_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_JDWP_AGENT): $(MOD_JAVA_BASE) $(MOD_JDK_JDWP_AGENT_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_JDWP_AGENT_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_JFR): $(MOD_JAVA_BASE) $(MOD_JDK_JFR_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_JFR_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_JLINK): $(MOD_JAVA_BASE) $(MOD_JDK_JLINK_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_JLINK_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_JPACKAGE): $(MOD_JAVA_BASE) $(MOD_JDK_JPACKAGE_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_JPACKAGE_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_JSHELL): $(MOD_JAVA_BASE) $(MOD_JAVA_COMPILER) $(MOD_JAVA_PREFS) $(MOD_JDK_JDI) $(MOD_JDK_JSHELL_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_JSHELL_DIR) -modules=java.base,java.compiler,java.prefs,jdk.jdi -saveLib=$@
$(MOD_JDK_JSOBJECT): $(MOD_JAVA_BASE) $(MOD_JDK_JSOBJECT_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_JSOBJECT_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_JSTATD): $(MOD_JAVA_BASE) $(MOD_JDK_JSTATD_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_JSTATD_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_LOCALEDATA): $(MOD_JAVA_BASE) $(MOD_JDK_LOCALEDATA_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_LOCALEDATA_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_MANAGEMENT): $(MOD_JAVA_BASE) $(MOD_JAVA_MANAGEMENT) $(MOD_JDK_MANAGEMENT_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_MANAGEMENT_DIR) -modules=java.base,java.management -saveLib=$@
$(MOD_JDK_MANAGEMENT_AGENT): $(MOD_JAVA_BASE) $(MOD_JDK_MANAGEMENT_AGENT_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_MANAGEMENT_AGENT_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_MANAGEMENT_JFR): $(MOD_JAVA_BASE) $(MOD_JAVA_MANAGEMENT) $(MOD_JDK_JFR) $(MOD_JDK_MANAGEMENT_JFR_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_MANAGEMENT_JFR_DIR) -modules=java.base,java.management,jdk.jfr -saveLib=$@
$(MOD_JDK_NAMING_DNS): $(MOD_JAVA_BASE) $(MOD_JDK_NAMING_DNS_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_NAMING_DNS_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_NAMING_RMI): $(MOD_JAVA_BASE) $(MOD_JDK_NAMING_RMI_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_NAMING_RMI_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_NET): $(MOD_JAVA_BASE) $(MOD_JDK_NET_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_NET_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_NIO_MAPMODE): $(MOD_JAVA_BASE) $(MOD_JDK_NIO_MAPMODE_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_NIO_MAPMODE_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_SCTP): $(MOD_JAVA_BASE) $(MOD_JDK_SCTP_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_SCTP_DIR) -modules=java.base -saveLib=$@
$(MOD_JDK_SECURITY_AUTH): $(MOD_JAVA_BASE) $(MOD_JAVA_NAMING) $(MOD_JDK_SECURITY_AUTH_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_SECURITY_AUTH_DIR) -modules=java.base,java.naming -saveLib=$@
$(MOD_JDK_SECURITY_JGSS): $(MOD_JAVA_BASE) $(MOD_JAVA_SECURITY_JGSS) $(MOD_JDK_SECURITY_JGSS_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_SECURITY_JGSS_DIR) -modules=java.base,java.security.jgss -saveLib=$@
$(MOD_JDK_XML_DOM): $(MOD_JAVA_BASE) $(MOD_JAVA_XML) $(MOD_JDK_XML_DOM_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_XML_DOM_DIR) -modules=java.base,java.xml -saveLib=$@
$(MOD_JDK_ZIPFS): $(MOD_JAVA_BASE) $(MOD_JDK_ZIPFS_FZ_FILES)
	$(BUILD_DIR)/bin/fz -sourceDirs=$(MOD_JDK_ZIPFS_DIR) -modules=java.base -saveLib=$@

$(BUILD_DIR)/tests: $(FZ_SRC)/tests
	mkdir -p $(@D)
	cp -rf $^ $@
	chmod +x $@/*.sh

$(BUILD_DIR)/examples: $(FZ_SRC)/examples
	mkdir -p $(@D)
	cp -rf $^ $@

$(BUILD_DIR)/UnicodeData.txt:
	cd $(BUILD_DIR) && wget https://www.unicode.org/Public/UCD/latest/ucd/UnicodeData.txt

$(BUILD_DIR)/UnicodeData.java.generated: $(CLASS_FILES_UTIL_UNICODE) $(BUILD_DIR)/UnicodeData.txt
	$(JAVA) -cp $(CLASSES_DIR) dev.flang.util.unicode.ParseUnicodeData $(BUILD_DIR)/UnicodeData.txt >$@

$(BUILD_DIR)/UnicodeData.java: $(BUILD_DIR)/UnicodeData.java.generated $(SRC)/dev/flang/util/UnicodeData.java.in
	sed -e '/@@@ generated code start @@@/r build/UnicodeData.java.generated' $(SRC)/dev/flang/util/UnicodeData.java.in >$@

.phony: doc
doc: $(DOCUMENTATION)

$(BUILD_DIR)/doc/fumfile.html: $(SRC)/dev/flang/fe/LibraryModule.java
	mkdir -p $(@D)
	sed -n '/--asciidoc--/,/--asciidoc--/p' $^ | grep -v "\--asciidoc--" | asciidoc - >$@

# phony target to regenerate UnicodeData.java using the latest UnicodeData.txt.
# This must be phony since $(SRC)/dev/flang/util/UnicodeData.java would
# be a circular dependency
.phony: unicode
unicode: $(BUILD_DIR)/UnicodeData.java
	cp $^ $(SRC)/dev/flang/util/UnicodeData.java

# phony target to regenerate Fuzion logo.
# This must be phony since $(SRC)/assets/logo.svg would be a circular dependency
.phony: logo
logo: $(BUILD_DIR)/assets/logo.svg $(BUILD_DIR)/assets/logo_bleed.svg $(BUILD_DIR)/assets/logo_bleed_cropmark.svg
	cp $^ $(FZ_SRC)/assets/

# phony target to run Fuzion tests and report number of failures
.PHONY: run_tests
run_tests: run_tests_int run_tests_c

# phony target to run Fuzion tests using interpreter and report number of failures
.PHONY .SILENT: run_tests_int
run_tests_int: $(BUILD_DIR)/bin/fz $(MOD_BASE) $(MOD_TERMINAL) $(MOD_JAVA_BASE) $(BUILD_DIR)/tests
	echo -n "testing interpreter: "
	$(FZ_SRC)/bin/run_tests.sh $(BUILD_DIR) int

# phony target to run Fuzion tests using c backend and report number of failures
.PHONY .SILENT: run_tests_c
run_tests_c: $(BUILD_DIR)/bin/fz $(MOD_BASE) $(MOD_TERMINAL) $(MOD_JAVA_BASE) $(BUILD_DIR)/tests
	echo -n "testing C backend: "; \
	$(FZ_SRC)/bin/run_tests.sh $(BUILD_DIR) c

# phony target to run Fuzion tests and report number of failures
.PHONY: run_tests_parallel
run_tests_parallel: run_tests_int_parallel run_tests_c_parallel

# phony target to run Fuzion tests using interpreter and report number of failures
.PHONY .SILENT: run_tests_int_parallel
run_tests_int_parallel: $(BUILD_DIR)/bin/fz $(MOD_BASE) $(MOD_TERMINAL) $(MOD_JAVA_BASE) $(BUILD_DIR)/tests
	echo -n "testing interpreter: "
	$(FZ_SRC)/bin/run_tests_parallel.sh $(BUILD_DIR) int

# phony target to run Fuzion tests using c backend and report number of failures
.PHONY .SILENT: run_tests_c_parallel
run_tests_c_parallel: $(BUILD_DIR)/bin/fz $(MOD_BASE) $(MOD_TERMINAL) $(MOD_JAVA_BASE) $(BUILD_DIR)/tests
	echo -n "testing C backend: "; \
	$(FZ_SRC)/bin/run_tests_parallel.sh $(BUILD_DIR) c

.PHONY: clean
clean:
	rm -rf $(BUILD_DIR)
	find $(FZ_SRC) -name "*~" -type f -exec rm {} \;

.PHONY: release
release: clean all
	rm -f fuzion_$(VERSION).tar.gz
	tar cfz fuzion_$(VERSION).tar.gz --transform s/^build/fuzion_$(VERSION)/ build

# shellcheck (https://www.shellcheck.net) checks shell scripts for issues/bugs
.PHONY: shellcheck
shellcheck:
	shellcheck $(SHELL_SCRIPTS) $(shell find . -iname '*.sh' -not -path "./build/*")

# show readme in browser, requires 'sudo apt install grip'
.PHONY: show_readme
show_readme:
	grip -b README.md

# show release notes in browser, requires 'sudo apt install grip'
.PHONY: show_release_notes
show_release_notes:
	grip -b release_notes.md
