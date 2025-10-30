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
#  Source code of Makefile
#
# -----------------------------------------------------------------------


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


$(MOD_JAVA_BASE_FZ_FILES): $(MOD_BASE) $(FZJAVA)
	rm -rf $(@D)
	mkdir -p $(@D)
# wrapping in /bin/sh -c "..." is a workaround for building on windows (msys2)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.base -to=$(@D) -verbose=0"
	touch $@

$(MOD_JAVA_XML_FZ_FILES): $(FZJAVA)
	rm -rf $(@D)
	mkdir -p $(@D)
# wrapping in /bin/sh -c "..." is a workaround for building on windows (msys2)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.xml -to=$(@D) -modules=java.base -verbose=0"
	touch $@

$(MOD_JAVA_DATATRANSFER_FZ_FILES): $(FZJAVA)
	rm -rf $(@D)
	mkdir -p $(@D)
# wrapping in /bin/sh -c "..." is a workaround for building on windows (msys2)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.datatransfer -to=$(@D) -modules=java.base,java.xml -verbose=0"
# NYI: CLEANUP: see #462: manually move these features to the main directory
# since otherwise they would not be found automatically.
	mv $(BUILD_DIR)/modules/java.datatransfer/Java/sun/datatransfer_pkg.fz $(BUILD_DIR)/modules/java.datatransfer/
	mv $(BUILD_DIR)/modules/java.datatransfer/Java/java/awt_pkg.fz  $(BUILD_DIR)/modules/java.datatransfer/
	touch $@

$(MOD_JAVA_DESKTOP_FZ_FILES): $(FZJAVA)
	rm -rf $(@D)
	mkdir -p $(@D)
# wrapping in /bin/sh -c "..." is a workaround for building on windows (msys2)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.desktop -to=$(@D) -modules=java.base,java.xml,java.datatransfer -verbose=0"
# NYI: CLEANUP: see #462: manually move these features to the main directory
# since otherwise they would not be found automatically.
	mv $(BUILD_DIR)/modules/java.desktop/Java/com/sun/*.fz $(BUILD_DIR)/modules/java.desktop/
	mv $(BUILD_DIR)/modules/java.desktop/Java/java/*.fz $(BUILD_DIR)/modules/java.desktop/
	mv $(BUILD_DIR)/modules/java.desktop/Java/javax/*.fz $(BUILD_DIR)/modules/java.desktop/
	mv $(BUILD_DIR)/modules/java.desktop/Java/sun/swing_pkg.fz $(BUILD_DIR)/modules/java.desktop/sun_swing_pkg.fz
	mv $(BUILD_DIR)/modules/java.desktop/Java/sun/print_pkg.fz $(BUILD_DIR)/modules/java.desktop/sun_print_pkg.fz
	mv $(BUILD_DIR)/modules/java.desktop/Java/sun/*.fz $(BUILD_DIR)/modules/java.desktop/
	touch $@

$(MOD_JAVA_COMPILER_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.compiler -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_INSTRUMENT_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.instrument -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_LOGGING_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.logging -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_MANAGEMENT_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.management -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_MANAGEMENT_RMI_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE) $(MOD_JAVA_MANAGEMENT) $(MOD_JAVA_RMI)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.management.rmi -to=$(@D) -modules=java.base,java.management,java.rmi -verbose=0"
	touch $@
$(MOD_JAVA_NAMING_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.naming -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_NET_HTTP_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.net.http -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_PREFS_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.prefs -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_RMI_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.rmi -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_SCRIPTING_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.scripting -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_SE_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE) $(MOD_JAVA_SQL_ROWSET) $(MOD_JAVA_XML_CRYPTO) $(MOD_JAVA_MANAGEMENT_RMI) $(MOD_JAVA_SECURITY_JGSS) $(MOD_JAVA_SECURITY_SASL) $(MOD_JAVA_SCRIPTING) $(MOD_JAVA_DESKTOP) $(MOD_JAVA_COMPILER) $(MOD_JAVA_INSTRUMENT) $(MOD_JAVA_NET_HTTP) $(MOD_JAVA_PREFS)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.se -to=$(@D) -modules=java.base,java.naming,java.transaction.xa,java.logging,java.scripting,java.xml,java.datatransfer,java.prefs,java.sql,java.desktop,java.compiler,java.instrument,java.rmi,java.management,java.net.http,java.sql.rowset,java.xml.crypto,java.management.rmi,java.security.jgss,java.security.sasl -verbose=0"
	touch $@
$(MOD_JAVA_SECURITY_JGSS_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.security.jgss -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_SECURITY_SASL_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.security.sasl -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_SMARTCARDIO_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.smartcardio -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_SQL_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE) $(MOD_JAVA_LOGGING) $(MOD_JAVA_XML) $(MOD_JAVA_TRANSACTION_XA)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.sql -to=$(@D) -modules=java.base,java.logging,java.xml,java.transaction.xa -verbose=0"
	touch $@
$(MOD_JAVA_SQL_ROWSET_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE) $(MOD_JAVA_SQL) $(MOD_JAVA_NAMING) $(MOD_JAVA_LOGGING) $(MOD_JAVA_XML) $(MOD_JAVA_TRANSACTION_XA)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.sql.rowset -to=$(@D) -modules=java.base,java.logging,java.xml,java.transaction.xa,java.sql,java.naming -verbose=0"
	touch $@
$(MOD_JAVA_TRANSACTION_XA_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.transaction.xa -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JAVA_XML_CRYPTO_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE) $(MOD_JAVA_XML)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) java.xml.crypto -to=$(@D) -modules=java.xml,java.base -verbose=0"
	touch $@
$(MOD_JDK_ACCESSIBILITY_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE) $(MOD_JAVA_DESKTOP)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.accessibility -to=$(@D) -modules=java.base,java.xml,java.datatransfer,java.desktop -verbose=0"
	touch $@
$(MOD_JDK_ATTACH_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.attach -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_CHARSETS_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.charsets -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_COMPILER_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE) $(MOD_JAVA_COMPILER)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.compiler -to=$(@D) -modules=java.base,java.compiler -verbose=0"
	touch $@
$(MOD_JDK_CRYPTO_CRYPTOKI_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.crypto.cryptoki -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_CRYPTO_EC_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.crypto.ec -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_DYNALINK_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.dynalink -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_EDITPAD_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.editpad -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_HTTPSERVER_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.httpserver -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JARTOOL_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.jartool -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JAVADOC_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE) $(MOD_JDK_COMPILER)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.javadoc -to=$(@D) -modules=java.base,java.compiler,jdk.compiler -verbose=0"
	touch $@
$(MOD_JDK_JCONSOLE_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE) $(MOD_JAVA_DESKTOP) $(MOD_JAVA_MANAGEMENT)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.jconsole -to=$(@D) -modules=java.base,java.xml,java.datatransfer,java.desktop,java.management -verbose=0"
	touch $@
$(MOD_JDK_JDEPS_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.jdeps -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JDI_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.jdi -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JDWP_AGENT_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.jdwp.agent -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JFR_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.jfr -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JLINK_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.jlink -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JPACKAGE_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.jpackage -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JSHELL_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE) $(MOD_JAVA_COMPILER) $(MOD_JAVA_PREFS) $(MOD_JDK_JDI)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.jshell -to=$(@D) -modules=java.base,java.compiler,java.prefs,jdk.jdi -verbose=0"
	touch $@
$(MOD_JDK_JSOBJECT_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.jsobject -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_JSTATD_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.jstatd -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_LOCALEDATA_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.localedata -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_MANAGEMENT_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE) $(MOD_JAVA_MANAGEMENT)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.management -to=$(@D) -modules=java.base,java.management -verbose=0"
	touch $@
$(MOD_JDK_MANAGEMENT_AGENT_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.management.agent -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_MANAGEMENT_JFR_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE) $(MOD_JAVA_MANAGEMENT) $(MOD_JDK_JFR)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.management.jfr -to=$(@D) -modules=java.base,java.management,jdk.jfr -verbose=0"
	touch $@
$(MOD_JDK_NAMING_DNS_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.naming.dns -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_NAMING_RMI_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.naming.rmi -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_NET_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.net -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_NIO_MAPMODE_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.nio.mapmode -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_SCTP_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.sctp -to=$(@D) -modules=java.base -verbose=0"
	touch $@
$(MOD_JDK_SECURITY_AUTH_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE) $(MOD_JAVA_NAMING)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.security.auth -to=$(@D) -modules=java.base,java.naming -verbose=0"
	touch $@
$(MOD_JDK_SECURITY_JGSS_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE) $(MOD_JAVA_SECURITY_JGSS)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.security.jgss -to=$(@D) -modules=java.base,java.security.jgss -verbose=0"
	touch $@
$(MOD_JDK_XML_DOM_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE) $(MOD_JAVA_XML)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.xml.dom -to=$(@D) -modules=java.base,java.xml -verbose=0"
	touch $@
$(MOD_JDK_ZIPFS_FZ_FILES): $(FZJAVA) $(MOD_JAVA_BASE)
	rm -rf $(@D)
	mkdir -p $(@D)
	$(FUZION_BIN_SH) -c "$(FZJAVA) jdk.zipfs -to=$(@D) -modules=java.base -verbose=0"
	touch $@

$(MOD_JAVA_BASE): $(MOD_JAVA_BASE_FZ_FILES)
	$(FZ) -sourceDirs=$(^D) -saveModule=$@

$(MOD_JAVA_XML): $(MOD_JAVA_BASE) $(MOD_JAVA_XML_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_XML_DIR) -modules=java.base -saveModule=$@

$(MOD_JAVA_DATATRANSFER): $(MOD_JAVA_BASE) $(MOD_JAVA_XML) $(MOD_JAVA_DATATRANSFER_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_DATATRANSFER_DIR) -modules=java.base,java.xml -saveModule=$@

$(MOD_JAVA_DESKTOP): $(MOD_JAVA_BASE) $(MOD_JAVA_XML) $(MOD_JAVA_DATATRANSFER) $(MOD_JAVA_DESKTOP_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_DESKTOP_DIR) -modules=java.base,java.xml,java.datatransfer -saveModule=$@

$(MOD_JAVA_COMPILER): $(MOD_JAVA_BASE) $(MOD_JAVA_COMPILER_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_COMPILER_DIR) -modules=java.base -saveModule=$@
$(MOD_JAVA_INSTRUMENT): $(MOD_JAVA_BASE) $(MOD_JAVA_INSTRUMENT_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_INSTRUMENT_DIR) -modules=java.base -saveModule=$@
$(MOD_JAVA_LOGGING): $(MOD_JAVA_BASE) $(MOD_JAVA_LOGGING_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_LOGGING_DIR) -modules=java.base -saveModule=$@
$(MOD_JAVA_MANAGEMENT): $(MOD_JAVA_BASE) $(MOD_JAVA_MANAGEMENT_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_MANAGEMENT_DIR) -modules=java.base -saveModule=$@
$(MOD_JAVA_MANAGEMENT_RMI): $(MOD_JAVA_BASE) $(MOD_JAVA_MANAGEMENT) $(MOD_JAVA_RMI) $(MOD_JAVA_MANAGEMENT_RMI_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_MANAGEMENT_RMI_DIR) -modules=java.base,java.management,java.rmi -saveModule=$@
$(MOD_JAVA_NAMING): $(MOD_JAVA_BASE) $(MOD_JAVA_NAMING_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_NAMING_DIR) -modules=java.base -saveModule=$@
$(MOD_JAVA_NET_HTTP): $(MOD_JAVA_BASE) $(MOD_JAVA_NET_HTTP_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_NET_HTTP_DIR) -modules=java.base -saveModule=$@
$(MOD_JAVA_PREFS): $(MOD_JAVA_BASE) $(MOD_JAVA_PREFS_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_PREFS_DIR) -modules=java.base -saveModule=$@
$(MOD_JAVA_RMI): $(MOD_JAVA_BASE) $(MOD_JAVA_RMI_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_RMI_DIR) -modules=java.base -saveModule=$@
$(MOD_JAVA_SCRIPTING): $(MOD_JAVA_BASE) $(MOD_JAVA_SCRIPTING_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_SCRIPTING_DIR) -modules=java.base -saveModule=$@
$(MOD_JAVA_SE): $(MOD_JAVA_BASE) $(MOD_JAVA_SQL_ROWSET) $(MOD_JAVA_XML_CRYPTO) $(MOD_JAVA_MANAGEMENT_RMI) $(MOD_JAVA_SECURITY_JGSS) $(MOD_JAVA_SECURITY_SASL) $(MOD_JAVA_SCRIPTING) $(MOD_JAVA_DESKTOP) $(MOD_JAVA_COMPILER) $(MOD_JAVA_INSTRUMENT) $(MOD_JAVA_NET_HTTP) $(MOD_JAVA_PREFS) $(MOD_JAVA_SE_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_SE_DIR) -modules=java.base,java.naming,java.transaction.xa,java.logging,java.scripting,java.xml,java.datatransfer,java.prefs,java.sql,java.desktop,java.compiler,java.instrument,java.rmi,java.management,java.net.http,java.sql.rowset,java.xml.crypto,java.management.rmi,java.security.jgss,java.security.sasl -saveModule=$@
$(MOD_JAVA_SECURITY_JGSS): $(MOD_JAVA_BASE) $(MOD_JAVA_SECURITY_JGSS_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_SECURITY_JGSS_DIR) -modules=java.base -saveModule=$@
$(MOD_JAVA_SECURITY_SASL): $(MOD_JAVA_BASE) $(MOD_JAVA_SECURITY_SASL_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_SECURITY_SASL_DIR) -modules=java.base -saveModule=$@
$(MOD_JAVA_SMARTCARDIO): $(MOD_JAVA_BASE) $(MOD_JAVA_SMARTCARDIO_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_SMARTCARDIO_DIR) -modules=java.base -saveModule=$@
$(MOD_JAVA_SQL): $(MOD_JAVA_BASE) $(MOD_JAVA_LOGGING) $(MOD_JAVA_XML) $(MOD_JAVA_TRANSACTION_XA) $(MOD_JAVA_SQL_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_SQL_DIR) -modules=java.base,java.logging,java.xml,java.transaction.xa -saveModule=$@
$(MOD_JAVA_SQL_ROWSET): $(MOD_JAVA_BASE) $(MOD_JAVA_SQL) $(MOD_JAVA_NAMING) $(MOD_JAVA_LOGGING) $(MOD_JAVA_XML) $(MOD_JAVA_TRANSACTION_XA) $(MOD_JAVA_SQL_ROWSET_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_SQL_ROWSET_DIR) -modules=java.base,java.sql,java.naming,java.logging,java.xml,java.transaction.xa -saveModule=$@
$(MOD_JAVA_TRANSACTION_XA): $(MOD_JAVA_BASE) $(MOD_JAVA_TRANSACTION_XA_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_TRANSACTION_XA_DIR) -modules=java.base -saveModule=$@
$(MOD_JAVA_XML_CRYPTO): $(MOD_JAVA_BASE) $(MOD_JAVA_XML) $(MOD_JAVA_XML_CRYPTO_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JAVA_XML_CRYPTO_DIR) -modules=java.base,java.xml -saveModule=$@
$(MOD_JDK_ACCESSIBILITY): $(MOD_JAVA_BASE) $(MOD_JAVA_DESKTOP) $(MOD_JDK_ACCESSIBILITY_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_ACCESSIBILITY_DIR) -modules=java.base,java.xml,java.datatransfer,java.desktop -saveModule=$@
$(MOD_JDK_ATTACH): $(MOD_JAVA_BASE) $(MOD_JDK_ATTACH_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_ATTACH_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_CHARSETS): $(MOD_JAVA_BASE) $(MOD_JDK_CHARSETS_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_CHARSETS_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_COMPILER): $(MOD_JAVA_BASE) $(MOD_JAVA_COMPILER) $(MOD_JDK_COMPILER_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_COMPILER_DIR) -modules=java.base,java.compiler -saveModule=$@
$(MOD_JDK_CRYPTO_CRYPTOKI): $(MOD_JAVA_BASE) $(MOD_JDK_CRYPTO_CRYPTOKI_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_CRYPTO_CRYPTOKI_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_CRYPTO_EC): $(MOD_JAVA_BASE) $(MOD_JDK_CRYPTO_EC_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_CRYPTO_EC_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_DYNALINK): $(MOD_JAVA_BASE) $(MOD_JDK_DYNALINK_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_DYNALINK_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_EDITPAD): $(MOD_JAVA_BASE) $(MOD_JDK_EDITPAD_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_EDITPAD_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_HTTPSERVER): $(MOD_JAVA_BASE) $(MOD_JDK_HTTPSERVER_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_HTTPSERVER_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_JARTOOL): $(MOD_JAVA_BASE) $(MOD_JDK_JARTOOL_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_JARTOOL_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_JAVADOC): $(MOD_JAVA_BASE) $(MOD_JDK_COMPILER) $(MOD_JDK_JAVADOC_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_JAVADOC_DIR) -modules=java.base,java.compiler,jdk.compiler -saveModule=$@
$(MOD_JDK_JCONSOLE): $(MOD_JAVA_BASE) $(MOD_JAVA_DESKTOP) $(MOD_JAVA_MANAGEMENT) $(MOD_JDK_JCONSOLE_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_JCONSOLE_DIR) -modules=java.base,java.xml,java.datatransfer,java.desktop,java.management -saveModule=$@
$(MOD_JDK_JDEPS): $(MOD_JAVA_BASE) $(MOD_JDK_JDEPS_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_JDEPS_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_JDI): $(MOD_JAVA_BASE) $(MOD_JDK_JDI_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_JDI_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_JDWP_AGENT): $(MOD_JAVA_BASE) $(MOD_JDK_JDWP_AGENT_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_JDWP_AGENT_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_JFR): $(MOD_JAVA_BASE) $(MOD_JDK_JFR_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_JFR_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_JLINK): $(MOD_JAVA_BASE) $(MOD_JDK_JLINK_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_JLINK_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_JPACKAGE): $(MOD_JAVA_BASE) $(MOD_JDK_JPACKAGE_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_JPACKAGE_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_JSHELL): $(MOD_JAVA_BASE) $(MOD_JAVA_COMPILER) $(MOD_JAVA_PREFS) $(MOD_JDK_JDI) $(MOD_JDK_JSHELL_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_JSHELL_DIR) -modules=java.base,java.compiler,java.prefs,jdk.jdi -saveModule=$@
$(MOD_JDK_JSOBJECT): $(MOD_JAVA_BASE) $(MOD_JDK_JSOBJECT_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_JSOBJECT_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_JSTATD): $(MOD_JAVA_BASE) $(MOD_JDK_JSTATD_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_JSTATD_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_LOCALEDATA): $(MOD_JAVA_BASE) $(MOD_JDK_LOCALEDATA_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_LOCALEDATA_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_MANAGEMENT): $(MOD_JAVA_BASE) $(MOD_JAVA_MANAGEMENT) $(MOD_JDK_MANAGEMENT_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_MANAGEMENT_DIR) -modules=java.base,java.management -saveModule=$@
$(MOD_JDK_MANAGEMENT_AGENT): $(MOD_JAVA_BASE) $(MOD_JDK_MANAGEMENT_AGENT_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_MANAGEMENT_AGENT_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_MANAGEMENT_JFR): $(MOD_JAVA_BASE) $(MOD_JAVA_MANAGEMENT) $(MOD_JDK_JFR) $(MOD_JDK_MANAGEMENT_JFR_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_MANAGEMENT_JFR_DIR) -modules=java.base,java.management,jdk.jfr -saveModule=$@
$(MOD_JDK_NAMING_DNS): $(MOD_JAVA_BASE) $(MOD_JDK_NAMING_DNS_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_NAMING_DNS_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_NAMING_RMI): $(MOD_JAVA_BASE) $(MOD_JDK_NAMING_RMI_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_NAMING_RMI_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_NET): $(MOD_JAVA_BASE) $(MOD_JDK_NET_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_NET_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_NIO_MAPMODE): $(MOD_JAVA_BASE) $(MOD_JDK_NIO_MAPMODE_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_NIO_MAPMODE_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_SCTP): $(MOD_JAVA_BASE) $(MOD_JDK_SCTP_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_SCTP_DIR) -modules=java.base -saveModule=$@
$(MOD_JDK_SECURITY_AUTH): $(MOD_JAVA_BASE) $(MOD_JAVA_NAMING) $(MOD_JDK_SECURITY_AUTH_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_SECURITY_AUTH_DIR) -modules=java.base,java.naming -saveModule=$@
$(MOD_JDK_SECURITY_JGSS): $(MOD_JAVA_BASE) $(MOD_JAVA_SECURITY_JGSS) $(MOD_JDK_SECURITY_JGSS_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_SECURITY_JGSS_DIR) -modules=java.base,java.security.jgss -saveModule=$@
$(MOD_JDK_XML_DOM): $(MOD_JAVA_BASE) $(MOD_JAVA_XML) $(MOD_JDK_XML_DOM_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_XML_DOM_DIR) -modules=java.base,java.xml -saveModule=$@
$(MOD_JDK_ZIPFS): $(MOD_JAVA_BASE) $(MOD_JDK_ZIPFS_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_JDK_ZIPFS_DIR) -modules=java.base -saveModule=$@
