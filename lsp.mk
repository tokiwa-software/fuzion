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
LSP_LSP4J_URL            = https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j/1.0.0/org.eclipse.lsp4j-1.0.0.jar
LSP_LSP4J_GENERATOR_URL  = https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j.generator/1.0.0/org.eclipse.lsp4j.generator-1.0.0.jar
LSP_LSP4J_JSONRPC_URL    = https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j.jsonrpc/1.0.0/org.eclipse.lsp4j.jsonrpc-1.0.0.jar
LSP_GSON_URL             = https://repo1.maven.org/maven2/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar
JARS_LSP_LSP4J           = $(BUILD_DIR)/jars/org.eclipse.lsp4j-1.0.0.jar
JARS_LSP_LSP4J_GENERATOR = $(BUILD_DIR)/jars/org.eclipse.lsp4j.generator-1.0.0.jar
JARS_LSP_LSP4J_JSONRPC   = $(BUILD_DIR)/jars/org.eclipse.lsp4j.jsonrpc-1.0.0.jar
JARS_LSP_GSON            = $(BUILD_DIR)/jars/gson-2.13.2.jar

LSP_CP = $(CLASSES_DIR):$(CLASSES_DIR_LSP):$(JARS_LSP_LSP4J):$(JARS_LSP_LSP4J_GENERATOR):$(JARS_LSP_LSP4J_JSONRPC):$(JARS_LSP_GSON)

ifeq ($(OS),Windows_NT)
	LSP_CP := $(CLASSES_DIR);$(CLASSES_DIR_LSP);$(JARS_LSP_LSP4J);$(JARS_LSP_LSP4J_GENERATOR);$(JARS_LSP_LSP4J_JSONRPC);$(JARS_LSP_GSON)
endif

$(JARS_LSP_LSP4J):
	mkdir -p $(@D)
	wget --output-document $@ $(LSP_LSP4J_URL)
	jar xf $@ -C $(CLASSES_DIR_LSP) || rm $@

$(JARS_LSP_LSP4J_GENERATOR):
	mkdir -p $(@D)
	wget --output-document $@ $(LSP_LSP4J_GENERATOR_URL)
	jar xf $@ -C $(CLASSES_DIR_LSP) || rm $@

$(JARS_LSP_LSP4J_JSONRPC):
	mkdir -p $(@D)
	wget --output-document $@ $(LSP_LSP4J_JSONRPC_URL)
	jar xf $@ -C $(CLASSES_DIR_LSP) || rm $@

$(JARS_LSP_GSON):
	mkdir -p $(@D)
	wget --output-document $@ $(LSP_GSON_URL)
	jar xf $@ -C $(CLASSES_DIR_LSP) || rm $@


$(BUILD_DIR)/jars/lsp.sha256: $(JARS_LSP_LSP4J) $(JARS_LSP_LSP4J_GENERATOR) $(JARS_LSP_LSP4J_JSONRPC) $(JARS_LSP_GSON)
	echo "ccd78893facc6bfcc359d56cba05d3d5b85eb41e4c40d4b4215ca45db5f416d9 $(BUILD_DIR)/jars/org.eclipse.lsp4j-1.0.0.jar" > $(BUILD_DIR)/jars/lsp.sha256
	echo "30cc6849e75eb92ec779593e6f80fb28650de2af135c879501472c2f50724a9c $(BUILD_DIR)/jars/org.eclipse.lsp4j.generator-1.0.0.jar" >> $(BUILD_DIR)/jars/lsp.sha256
	echo "9647feb0524bf763c878e12ab878a684102b81cccb3f77feecbec709d54f9bbb $(BUILD_DIR)/jars/org.eclipse.lsp4j.jsonrpc-1.0.0.jar" >> $(BUILD_DIR)/jars/lsp.sha256
	echo "dd0ce1b55a3ed2080cb70f9c655850cda86c206862310009dcb5e5c95265a5e0 $(BUILD_DIR)/jars/gson-2.13.2.jar" >> $(BUILD_DIR)/jars/lsp.sha256
	sha256sum --status -c $(BUILD_DIR)/jars/lsp.sha256


$(BUILD_DIR)/bin/fuzion_language_server: $(FZ_SRC)/bin/fuzion_language_server
	mkdir -p $(@D)
	cp $^ $@
	chmod +x $@


# NYI: CLEANUP: use just frontend not, CLASS_FILES_BE_JVM
$(CLASS_FILES_LSP): $(BUILD_DIR)/jars/lsp.sha256 $(BUILD_DIR)/bin/fuzion_language_server $(CLASS_FILES_BE_JVM) $(JAVA_FILES_LSP) $(JAVA_FILES_LSP_SHARED)
	mkdir -p $(CLASSES_DIR_LSP)
	$(JAVAC) --class-path "$(LSP_CP)" -d $(CLASSES_DIR_LSP) $(JAVA_FILES_LSP)
	$(JAVAC) --class-path "$(LSP_CP)" -d $(CLASSES_DIR_LSP) $(JAVA_FILES_LSP_SHARED)
	touch $@

.PHONY: lsp/compile
lsp/compile: $(FUZION_BASE) $(CLASS_FILES_LSP)

LSP_JAVA_STACKSIZE=16
LSP_DEBUGGER_SUSPENDED = -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:8000
LSP_JAVA_ARGS = -Dfuzion.home=$(BUILD_DIR) -Dfile.encoding=UTF-8 -Xss$(LSP_JAVA_STACKSIZE)m
.PHONY: lsp/debug/stdio
lsp/debug/stdio: lsp/compile
	$(JAVA) $(LSP_DEBUGGER_SUSPENDED) --class-path "$(LSP_CP)" $(LSP_JAVA_ARGS) dev.flang.lsp.Main -stdio


# this is normally set by vscode-fuzion in debug mode
LANGUAGE_SERVER_PORT ?= 3000


.PHONY: lsp/debug/socket
lsp/debug/socket: NOOP = $(shell lsof -i:8000 | tail -n 1 | awk -F ' ' '{print $$2}' | xargs kill)
lsp/debug/socket: $(CLASS_FILES_LSP)
	mkdir -p runDir
	java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:8000 --class-path "$(LSP_CP)" $(LSP_JAVA_ARGS) dev.flang.lsp.Main -socket --port=$(LANGUAGE_SERVER_PORT)
