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
#  Source code of Makefile for the language server sources
#
# -----------------------------------------------------------------------


JAVA_FILES_LSP               = $(shell find $(SRC)/dev/flang/lsp -name '*.java' )
JAVA_FILES_LSP_SHARED        = $(shell find $(SRC)/dev/flang/lsp/shared -name '*.java' )
CLASSES_DIR_LSP              = ./build/classes_lsp
CLASS_FILES_LSP              = $(CLASSES_DIR_LSP)/dev/flang/lsp/__marker_for_make__

# Must update assets/Manifest.txt as well
LSP_LSP4J_URL            = https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j/0.23.1/org.eclipse.lsp4j-0.23.1.jar
LSP_LSP4J_GENERATOR_URL  = https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j.generator/0.23.1/org.eclipse.lsp4j.generator-0.23.1.jar
LSP_LSP4J_JSONRPC_URL    = https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j.jsonrpc/0.23.1/org.eclipse.lsp4j.jsonrpc-0.23.1.jar
LSP_GSON_URL             = https://repo1.maven.org/maven2/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar
JARS_LSP_LSP4J           = $(BUILD_DIR)/jars/org.eclipse.lsp4j-0.23.1.jar
JARS_LSP_LSP4J_GENERATOR = $(BUILD_DIR)/jars/org.eclipse.lsp4j.generator-0.23.1.jar
JARS_LSP_LSP4J_JSONRPC   = $(BUILD_DIR)/jars/org.eclipse.lsp4j.jsonrpc-0.23.1.jar
JARS_LSP_GSON            = $(BUILD_DIR)/jars/gson-2.11.0.jar

LSP_JAR = $(BUILD_DIR)/lsp.jar

$(JARS_LSP_LSP4J):
	mkdir -p $(@D)
	curl $(LSP_LSP4J_URL) --output $@

$(JARS_LSP_LSP4J_GENERATOR):
	mkdir -p $(@D)
	curl $(LSP_LSP4J_GENERATOR_URL) --output $@

$(JARS_LSP_LSP4J_JSONRPC):
	mkdir -p $(@D)
	curl $(LSP_LSP4J_JSONRPC_URL) --output $@

$(JARS_LSP_GSON):
	mkdir -p $(@D)
	curl $(LSP_GSON_URL) --output $@


$(BUILD_DIR)/jars/lsp.sha256: $(JARS_LSP_LSP4J) $(JARS_LSP_LSP4J_GENERATOR) $(JARS_LSP_LSP4J_JSONRPC) $(JARS_LSP_GSON)
	echo "b16bbc6232a3946e03d537bb9be74e18489dbc6a8b8c5ab6cb7980854df8440f $(BUILD_DIR)/jars/org.eclipse.lsp4j-0.23.1.jar" > $(BUILD_DIR)/jars/lsp.sha256
	echo "1adaeb34550ebec21636a45afe76ff8b60188a056966feb3c7e562450ba911be $(BUILD_DIR)/jars/org.eclipse.lsp4j.generator-0.23.1.jar" >> $(BUILD_DIR)/jars/lsp.sha256
	echo "4e1aa77474de1791d96dc55932fb46efdf53233548f38f62ba7376f8b0bc6650 $(BUILD_DIR)/jars/org.eclipse.lsp4j.jsonrpc-0.23.1.jar" >> $(BUILD_DIR)/jars/lsp.sha256
	echo "57928d6e5a6edeb2abd3770a8f95ba44dce45f3b23b7a9dc2b309c581552a78b $(BUILD_DIR)/jars/gson-2.11.0.jar" >> $(BUILD_DIR)/jars/lsp.sha256
	sha256sum --status -c $(BUILD_DIR)/jars/lsp.sha256


$(BUILD_DIR)/bin/fuzion_language_server: bin/fuzion_language_server
	cp bin/fuzion_language_server $@
	chmod +x $@


# NYI: CLEANUP: use just frontend not, CLASS_FILES_BE_JVM
$(CLASS_FILES_LSP): $(BUILD_DIR)/jars/lsp.sha256 $(BUILD_DIR)/bin/fuzion_language_server $(CLASS_FILES_BE_JVM) $(JAVA_FILES_LSP) $(JAVA_FILES_LSP_SHARED)
	mkdir -p $(CLASSES_DIR_LSP)
	$(JAVAC) -cp $(CLASSES_DIR):$(JARS_LSP_LSP4J):$(JARS_LSP_LSP4J_GENERATOR):$(JARS_LSP_LSP4J_JSONRPC):$(JARS_LSP_GSON) -d $(CLASSES_DIR_LSP) $(JAVA_FILES_LSP)
	$(JAVAC) -cp $(CLASSES_DIR):$(CLASSES_DIR_LSP):$(JARS_LSP_LSP4J):$(JARS_LSP_LSP4J_GENERATOR):$(JARS_LSP_LSP4J_JSONRPC):$(JARS_LSP_GSON) -d $(CLASSES_DIR_LSP) $(JAVA_FILES_LSP_SHARED)
	touch $@

.PHONY: lsp/compile
lsp/compile: $(FUZION_BASE) $(CLASS_FILES_LSP)

LSP_JAVA_STACKSIZE=16
LSP_DEBUGGER_SUSPENDED = -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:8000
LSP_JAVA_ARGS = -Dfuzion.home=$(BUILD_DIR) -Dfile.encoding=UTF-8 -Xss$(LSP_JAVA_STACKSIZE)m
.PHONY: lsp/debug/stdio
lsp/debug/stdio: lsp/compile
	$(JAVA) $(LSP_DEBUGGER_SUSPENDED) -cp  $(CLASSES_DIR):$(CLASSES_DIR_LSP):$(JARS_LSP_LSP4J):$(JARS_LSP_LSP4J_GENERATOR):$(JARS_LSP_LSP4J_JSONRPC):$(JARS_LSP_GSON) $(LSP_JAVA_ARGS) dev.flang.lsp.Main -stdio


# this is normally set by vscode-fuzion in debug mode
LANGUAGE_SERVER_PORT ?= 3000


.PHONY: lsp/debug/socket
lsp/debug/socket: NOOP = $(shell lsof -i:8000 | tail -n 1 | awk -F ' ' '{print $$2}' | xargs kill)
lsp/debug/socket: $(CLASS_FILES_LSP)
	mkdir -p runDir
	java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:8000 -cp $(CLASSES_DIR):$(CLASSES_DIR_LSP):$(JARS_LSP_LSP4J):$(JARS_LSP_LSP4J_GENERATOR):$(JARS_LSP_LSP4J_JSONRPC):$(JARS_LSP_GSON) $(LSP_JAVA_ARGS) dev.flang.lsp.Main -socket --port=$(LANGUAGE_SERVER_PORT)

$(LSP_JAR): $(CLASS_FILES_LSP)
	jar cfm $(LSP_JAR) assets/Manifest.txt -C $(BUILD_DIR)/classes . -C $(CLASSES_DIR_LSP) .
