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

JAVA = java --enable-native-access=ALL-UNNAMED
JAVA_VERSION = 25
# NYI: CLEANUP: remove some/all of the exclusions
LINT = -Xlint:all,-serial,-this-escape
JAVAC = javac $(LINT) -encoding UTF8 --release $(JAVA_VERSION)
FZ_SRC = $(patsubst %/,%,$(dir $(lastword $(MAKEFILE_LIST))))
SRC = $(FZ_SRC)/src
BUILD_DIR = ./build
FZ = $(BUILD_DIR)/bin/fz
FZJAVA = $(BUILD_DIR)/bin/fzjava
CLASSES_DIR = $(BUILD_DIR)/classes
CLASSES_DIR_LOGO = $(BUILD_DIR)/classes_logo

ifeq ($(OS),Windows_NT)
	SHELL := /bin/sh
endif

ifeq ($(FUZION_DEBUG_SYMBOLS),true)
	JAVAC += -g
endif

UNICODE_SOURCE = https://www.unicode.org/Public/UCD/latest/ucd/UnicodeData.txt

JAVA_FILE_UTIL_VERSION_IN                     = $(SRC)/dev/flang/util/Version.java.in
JAVA_FILE_UTIL_VERSION                        = $(BUILD_DIR)/generated/src/dev/flang/util/Version.java
JAVA_FILE_FUIR_ANALYSIS_ABSTRACT_INTERPRETER2 = $(BUILD_DIR)/generated/src/dev/flang/fuir/analysis/AbstractInterpreter2.java

JAVA_FILES_UTIL              = $(wildcard $(SRC)/dev/flang/util/*.java          ) $(JAVA_FILE_UTIL_VERSION)
JAVA_FILES_UTIL_UNICODE      = $(wildcard $(SRC)/dev/flang/util/unicode/*.java  )
JAVA_FILES_AST               = $(wildcard $(SRC)/dev/flang/ast/*.java           )
JAVA_FILES_PARSER            = $(wildcard $(SRC)/dev/flang/parser/*.java        )
JAVA_FILES_IR                = $(wildcard $(SRC)/dev/flang/ir/*.java            )
JAVA_FILES_MIR               = $(wildcard $(SRC)/dev/flang/mir/*.java           )
JAVA_FILES_FE                = $(wildcard $(SRC)/dev/flang/fe/*.java            )
JAVA_FILES_FUIR              = $(wildcard $(SRC)/dev/flang/fuir/*.java          )
JAVA_FILES_FUIR_ANALYSIS     = $(wildcard $(SRC)/dev/flang/fuir/analysis/*.java ) $(JAVA_FILE_FUIR_ANALYSIS_ABSTRACT_INTERPRETER2)
JAVA_FILES_FUIR_ANALYSIS_DFA = $(wildcard $(SRC)/dev/flang/fuir/analysis/dfa/*.java )
JAVA_FILES_FUIR_CFG          = $(wildcard $(SRC)/dev/flang/fuir/cfg/*.java      )
JAVA_FILES_OPT               = $(wildcard $(SRC)/dev/flang/opt/*.java           )
JAVA_FILES_BE_INTERPRETER    = $(wildcard $(SRC)/dev/flang/be/interpreter/*.java)
JAVA_FILES_BE_C              = $(wildcard $(SRC)/dev/flang/be/c/*.java          )
JAVA_FILES_BE_EFFECTS        = $(wildcard $(SRC)/dev/flang/be/effects/*.java    )
JAVA_FILES_BE_JVM            = $(wildcard $(SRC)/dev/flang/be/jvm/*.java        )
JAVA_FILES_BE_JVM_CLASSFILE  = $(wildcard $(SRC)/dev/flang/be/jvm/classfile/*.java)
JAVA_FILES_BE_JVM_RUNTIME    = $(wildcard $(SRC)/dev/flang/be/jvm/runtime/*.java)
JAVA_FILES_TOOLS             = $(wildcard $(SRC)/dev/flang/tools/*.java         ) $(SRC)/module-info.java
JAVA_FILES_TOOLS_FZJAVA      = $(wildcard $(SRC)/dev/flang/tools/fzjava/*.java  )
JAVA_FILES_TOOLS_DOCS        = $(wildcard $(SRC)/dev/flang/tools/docs/*.java    )
JAVA_FILES_MISC_LOGO         = $(wildcard $(SRC)/dev/flang/misc/logo/*.java     )

JAVA_FILES_FOR_JAVA_DOC = $(JAVA_FILES_UTIL) \
                          $(JAVA_FILES_AST) \
                          $(JAVA_FILES_PARSER) \
                          $(JAVA_FILES_IR) \
                          $(JAVA_FILES_MIR) \
                          $(JAVA_FILES_FE) \
                          $(JAVA_FILES_FUIR) \
                          $(JAVA_FILES_FUIR_ANALYSIS) \
                          $(JAVA_FILES_FUIR_ANALYSIS_DFA) \
                          $(JAVA_FILES_OPT) \
                          $(JAVA_FILES_BE_INTERPRETER) \
                          $(JAVA_FILES_BE_C) \
                          $(JAVA_FILES_BE_EFFECTS) \
                          $(JAVA_FILES_BE_JVM) \
                          $(JAVA_FILES_BE_JVM_CLASSFILE) \
                          $(JAVA_FILES_BE_JVM_RUNTIME) \
                          $(JAVA_FILE_UTIL_VERSION) \
                          $(JAVA_FILE_FUIR_ANALYSIS_ABSTRACT_INTERPRETER2)

CLASS_FILES_UTIL              = $(CLASSES_DIR)/dev/flang/util/__marker_for_make__
CLASS_FILES_UTIL_UNICODE      = $(CLASSES_DIR)/dev/flang/util/unicode/__marker_for_make__
CLASS_FILES_AST               = $(CLASSES_DIR)/dev/flang/ast/__marker_for_make__
CLASS_FILES_PARSER            = $(CLASSES_DIR)/dev/flang/parser/__marker_for_make__
CLASS_FILES_IR                = $(CLASSES_DIR)/dev/flang/ir/__marker_for_make__
CLASS_FILES_MIR               = $(CLASSES_DIR)/dev/flang/mir/__marker_for_make__
CLASS_FILES_FE                = $(CLASSES_DIR)/dev/flang/fe/__marker_for_make__
CLASS_FILES_FUIR              = $(CLASSES_DIR)/dev/flang/fuir/__marker_for_make__
CLASS_FILES_FUIR_ANALYSIS     = $(CLASSES_DIR)/dev/flang/fuir/analysis/__marker_for_make__
CLASS_FILES_FUIR_ANALYSIS_DFA = $(CLASSES_DIR)/dev/flang/fuir/analysis/dfa/__marker_for_make__
CLASS_FILES_FUIR_CFG          = $(CLASSES_DIR)/dev/flang/fuir/cfg/__marker_for_make__
CLASS_FILES_OPT               = $(CLASSES_DIR)/dev/flang/opt/__marker_for_make__
CLASS_FILES_BE_INTERPRETER    = $(CLASSES_DIR)/dev/flang/be/interpreter/__marker_for_make__
CLASS_FILES_BE_C              = $(CLASSES_DIR)/dev/flang/be/c/__marker_for_make__
CLASS_FILES_BE_EFFECTS        = $(CLASSES_DIR)/dev/flang/be/effects/__marker_for_make__
CLASS_FILES_BE_JVM            = $(CLASSES_DIR)/dev/flang/be/jvm/__marker_for_make__
CLASS_FILES_BE_JVM_CLASSFILE  = $(CLASSES_DIR)/dev/flang/be/jvm/classfile/__marker_for_make__
CLASS_FILES_BE_JVM_RUNTIME    = $(CLASSES_DIR)/dev/flang/be/jvm/runtime/__marker_for_make__
CLASS_FILES_TOOLS             = $(CLASSES_DIR)/dev/flang/tools/__marker_for_make__
CLASS_FILES_TOOLS_FZJAVA      = $(CLASSES_DIR)/dev/flang/tools/fzjava/__marker_for_make__
CLASS_FILES_TOOLS_DOCS        = $(CLASSES_DIR)/dev/flang/tools/docs/__marker_for_make__
CLASS_FILES_MISC_LOGO         = $(CLASSES_DIR_LOGO)/dev/flang/misc/logo/__marker_for_make__

JFREE_SVG_URL = https://repo1.maven.org/maven2/org/jfree/org.jfree.svg/5.0.1/org.jfree.svg-5.0.1.jar
JARS_JFREE_SVG_JAR = $(BUILD_DIR)/jars/org.jfree.svg-5.0.1.jar

FUZION_EBNF = $(BUILD_DIR)/fuzion.ebnf

FZ_SRC_TESTS          = $(FZ_SRC)/tests
FUZION_FILES_TESTS    = $(shell find $(FZ_SRC_TESTS))
FZ_SRC_INCLUDE        = $(FZ_SRC)/include
FUZION_FILES_RT       = $(shell find $(FZ_SRC_INCLUDE))
FZ_SRC_EXAMPLES       = $(FZ_SRC)/examples
FUZION_FILES_EXAMPLES = $(shell find $(FZ_SRC_EXAMPLES))

MOD_BASE              = $(BUILD_DIR)/modules/base.fum
MOD_TERMINAL          = $(BUILD_DIR)/modules/terminal.fum
MOD_LOCK_FREE         = $(BUILD_DIR)/modules/lock_free.fum
MOD_NOM               = $(BUILD_DIR)/modules/nom.fum
MOD_UUID              = $(BUILD_DIR)/modules/uuid.fum
MOD_HTTP              = $(BUILD_DIR)/modules/http.fum
MOD_CLANG             = $(BUILD_DIR)/modules/clang.fum
MOD_WOLFSSL           = $(BUILD_DIR)/modules/wolfssl.fum
MOD_JSON_ENCODE       = $(BUILD_DIR)/modules/json_encode.fum
MOD_DATABASE          = $(BUILD_DIR)/modules/database.fum
MOD_SQLITE            = $(BUILD_DIR)/modules/sqlite.fum
MOD_MAIL              = $(BUILD_DIR)/modules/mail.fum
MOD_WEB               = $(BUILD_DIR)/modules/web.fum
MOD_SODIUM            = $(BUILD_DIR)/modules/sodium.fum
MOD_CRYPTO            = $(BUILD_DIR)/modules/crypto.fum
MOD_WEBSERVER         = $(BUILD_DIR)/modules/webserver.fum

MOD_FZ_CMD_DIR = $(BUILD_DIR)/modules/fz_cmd
MOD_FZ_CMD_FZ_FILES = $(MOD_FZ_CMD_DIR)/__marker_for_make__
MOD_FZ_CMD = $(MOD_FZ_CMD_DIR).fum

ifeq ($(OS),Windows_NT)
	FUZION_RT = $(BUILD_DIR)/lib/fuzion_rt.dll
else ifeq ($(shell uname),Darwin)
	FUZION_RT = $(BUILD_DIR)/lib/libfuzion_rt.dylib
else
	FUZION_RT = $(BUILD_DIR)/lib/libfuzion_rt.so
endif

VERSION = $(shell cat $(FZ_SRC)/version.txt)

FUZION_BASE = \
			$(FZ) \
			$(FZJAVA) \
			$(FZ_MODULES) \
			$(FUZION_RT)

FUZION_FILES = \
			 $(BUILD_DIR)/tests \
			 $(BUILD_DIR)/examples \
			 $(BUILD_DIR)/include \
			 $(BUILD_DIR)/README.md \
			 $(BUILD_DIR)/release_notes.md \
			 $(FUZION_RT)

# files required for fz command with jvm backend
FZ_JVM = \
			 $(FZ) \
			 $(CLASS_FILES_BE_JVM_RUNTIME) \
			 $(MOD_BASE) \
			 $(FUZION_RT)

# files required for fz command with C backend
FZ_C = \
			 $(FZ) \
			 $(BUILD_DIR)/include \
			 $(MOD_BASE) \
			 $(FUZION_RT)

# files required for fz command with interpreter backends
FZ_INT = \
			 $(FZ) \
			 $(MOD_BASE) \
			 $(FUZION_RT)

SHELL_SCRIPTS = \
	bin/fz \
	bin/fzjava

FZ_MODULES = \
			$(MOD_BASE) \
			$(MOD_TERMINAL) \
			$(MOD_LOCK_FREE) \
			$(MOD_NOM) \
			$(MOD_UUID) \
			$(MOD_HTTP) \
			$(MOD_CLANG) \
			$(MOD_WOLFSSL) \
			$(MOD_JSON_ENCODE) \
			$(MOD_DATABASE) \
			$(MOD_SQLITE) \
			$(MOD_MAIL) \
			$(MOD_WEB) \
			$(MOD_SODIUM) \
			$(MOD_CRYPTO) \
			$(MOD_WEBSERVER)

C_FILES = $(shell find $(FZ_SRC) \( -path ./build -o -path ./.git \) -prune -o -name '*.c' -print)

# make sure that any rule failing will result in the created file being
# deleted. This helps in case a failing rule creates a broken result file, which
# would prevent a second run of `make` from re-applying the failing rule.
.DELETE_ON_ERROR:


# default make target
.PHONY: all
all: $(FUZION_BASE) $(FUZION_JAVA_MODULES) $(FUZION_FILES) $(MOD_FZ_CMD) $(FUZION_EBNF) $(BUILD_DIR)/fuzion.jar


# rules to build java modules
#
include $(FZ_SRC)/mod_java.mk


# everything but rarely used java modules
.PHONY: min-java
min-java: $(FUZION_BASE) $(MOD_JAVA_BASE) $(MOD_JAVA_XML) $(MOD_JAVA_DATATRANSFER) $(MOD_JAVA_DESKTOP) $(FUZION_FILES)

# everything but the java modules
.PHONY: no-java
no-java: $(FUZION_BASE) $(FUZION_FILES)

# only up to base module
.PHONY: base-only
base-only: $(FZ) $(MOD_BASE) $(FUZION_FILES)

# phony target to compile all java sources
.PHONY: javac
javac: $(CLASS_FILES_TOOLS) $(CLASS_FILES_TOOLS_FZJAVA) $(CLASS_FILES_TOOLS_DOCS)

$(BUILD_DIR)/%.md: $(FZ_SRC)/%.md
	cp $^ $@

$(FUZION_EBNF): $(FUZION_BASE) $(FZ_SRC)/bin/ebnf.fz
	mkdir -p $(@D)
	$(FZ) $(FZ_SRC)/bin/ebnf.fz $(JAVA_FILES_PARSER) > $@

$(JAVA_FILE_UTIL_VERSION): $(FZ_SRC)/version.txt $(JAVA_FILE_UTIL_VERSION_IN)
	mkdir -p $(@D)
	cat $(JAVA_FILE_UTIL_VERSION_IN) \
          | sed "s^@@VERSION@@^$(VERSION)^g" \
          | sed "s^@@JAVA_VERSION@@^$(JAVA_VERSION)^g" \
          | sed "s^@@REPO_PATH@@^$(dir $(abspath $(lastword $(MAKEFILE_LIST))))^g" \
          | sed "s^@@GIT_HASH@@^`cd $(FZ_SRC); printf \`git rev-parse HEAD\` \`git diff-index --quiet HEAD -- || echo with local changes\``^g" >$@
ifeq ($(FUZION_REPRODUCIBLE_BUILD),true)
	sed -i "s^@@DATE@@^^g;s^@@BUILTBY@@^^g" $@
else
	sed -i "s^@@DATE@@^`date +%Y-%m-%d\ %H:%M:%S`^g;s^@@BUILTBY@@^`printf $(USER)@; hostname`^g" $@
endif

$(CLASS_FILES_UTIL): $(JAVA_FILES_UTIL)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -d $(CLASSES_DIR) $(JAVA_FILES_UTIL)
	touch $@

$(CLASS_FILES_UTIL_UNICODE): $(JAVA_FILES_UTIL_UNICODE) $(CLASS_FILES_UTIL)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_UTIL_UNICODE)
	touch $@

$(CLASS_FILES_AST): $(JAVA_FILES_AST) $(CLASS_FILES_UTIL)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_AST)
	touch $@

$(CLASS_FILES_PARSER): $(JAVA_FILES_PARSER) $(CLASS_FILES_AST)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_PARSER)
	touch $@

$(CLASS_FILES_IR): $(JAVA_FILES_IR) $(CLASS_FILES_UTIL) $(CLASS_FILES_AST)  # NYI: remove dependency on $(CLASS_FILES_AST)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_IR)
	touch $@

$(CLASS_FILES_MIR): $(JAVA_FILES_MIR) $(CLASS_FILES_IR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_MIR)
	touch $@

$(CLASS_FILES_FE): $(JAVA_FILES_FE) $(CLASS_FILES_PARSER) $(CLASS_FILES_AST) $(CLASS_FILES_MIR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_FE)
	touch $@

$(CLASS_FILES_FUIR): $(JAVA_FILES_FUIR) $(CLASS_FILES_UTIL) $(CLASS_FILES_IR) $(CLASS_FILES_FE)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_FUIR)
	touch $@

$(JAVA_FILE_FUIR_ANALYSIS_ABSTRACT_INTERPRETER2): $(SRC)/dev/flang/fuir/analysis/AbstractInterpreter.java $(SRC)/dev/flang/fuir/analysis/AbstractInterpreter2.java.patch
	mkdir -p $(@D)
	patch --output $@ $^

# phony target to update the .patch files used to generate sources from modified
# version of those generated sources
.PHONY: update-java-patches
update-java-patches:
	diff $(SRC)/dev/flang/fuir/analysis/AbstractInterpreter.java $(JAVA_FILE_FUIR_ANALYSIS_ABSTRACT_INTERPRETER2) >$(SRC)/dev/flang/fuir/analysis/AbstractInterpreter2.java.patch || true

$(CLASS_FILES_FUIR_ANALYSIS): $(JAVA_FILES_FUIR_ANALYSIS) $(CLASS_FILES_UTIL) $(CLASS_FILES_FUIR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_FUIR_ANALYSIS)
	touch $@

$(CLASS_FILES_FUIR_ANALYSIS_DFA): $(JAVA_FILES_FUIR_ANALYSIS_DFA) $(CLASS_FILES_FUIR_ANALYSIS) $(CLASS_FILES_UTIL) $(CLASS_FILES_FUIR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_FUIR_ANALYSIS_DFA)
	touch $@

$(CLASS_FILES_FUIR_CFG): $(JAVA_FILES_FUIR_CFG) $(CLASS_FILES_UTIL) $(CLASS_FILES_FUIR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_FUIR_CFG)
	touch $@

$(CLASS_FILES_OPT): $(JAVA_FILES_OPT) $(CLASS_FILES_FE) $(CLASS_FILES_FUIR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_OPT)
	touch $@

$(CLASS_FILES_BE_INTERPRETER): $(JAVA_FILES_BE_INTERPRETER) $(CLASS_FILES_FUIR_ANALYSIS_DFA)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_BE_INTERPRETER)
	touch $@

$(CLASS_FILES_BE_C): $(JAVA_FILES_BE_C) $(CLASS_FILES_FUIR_ANALYSIS_DFA)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_BE_C)
	touch $@

$(CLASS_FILES_BE_EFFECTS): $(JAVA_FILES_BE_EFFECTS) $(CLASS_FILES_FUIR_ANALYSIS_DFA)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_BE_EFFECTS)
	touch $@

$(CLASS_FILES_BE_JVM): $(JAVA_FILES_BE_JVM) $(CLASS_FILES_FUIR_ANALYSIS_DFA) $(CLASS_FILES_BE_JVM_RUNTIME) $(CLASS_FILES_BE_JVM_CLASSFILE)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_BE_JVM)
	touch $@

$(CLASS_FILES_BE_JVM_CLASSFILE): $(JAVA_FILES_BE_JVM_CLASSFILE) $(CLASS_FILES_UTIL)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_BE_JVM_CLASSFILE)
	touch $@

$(CLASS_FILES_BE_JVM_RUNTIME): $(JAVA_FILES_BE_JVM_RUNTIME) $(CLASS_FILES_UTIL)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_BE_JVM_RUNTIME)
	touch $@

$(CLASS_FILES_TOOLS): $(JAVA_FILES_TOOLS) $(CLASS_FILES_FE) $(CLASS_FILES_OPT) $(CLASS_FILES_BE_C) $(CLASS_FILES_FUIR_ANALYSIS_DFA) $(CLASS_FILES_BE_EFFECTS) $(CLASS_FILES_BE_JVM) $(CLASS_FILES_BE_JVM_RUNTIME) $(CLASS_FILES_BE_INTERPRETER) $(CLASS_FILES_FUIR_CFG)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_TOOLS)
	touch $@

$(CLASS_FILES_TOOLS_FZJAVA): $(JAVA_FILES_TOOLS_FZJAVA) $(CLASS_FILES_TOOLS) $(CLASS_FILES_PARSER) $(CLASS_FILES_UTIL)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_TOOLS_FZJAVA)
	touch $@

$(CLASS_FILES_TOOLS_DOCS): $(JAVA_FILES_TOOLS_DOCS) $(CLASS_FILES_TOOLS) $(CLASS_FILES_PARSER) $(CLASS_FILES_UTIL)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_TOOLS_DOCS)
	touch $@

$(JARS_JFREE_SVG_JAR):
	mkdir -p $(@D)
	wget --output-document $@ $(JFREE_SVG_URL)

$(CLASS_FILES_MISC_LOGO): $(JAVA_FILES_MISC_LOGO) $(CLASS_FILES_UTIL_UNICODE) $(JARS_JFREE_SVG_JAR)
	mkdir -p $(CLASSES_DIR_LOGO)
	$(JAVAC) --class-path $(CLASSES_DIR):$(JARS_JFREE_SVG_JAR) -d $(CLASSES_DIR_LOGO) $(JAVA_FILES_MISC_LOGO)
	touch $@

$(BUILD_DIR)/assets/logo.svg: $(CLASS_FILES_MISC_LOGO)
	mkdir -p $(@D)
	$(JAVA) --class-path $(CLASSES_DIR):$(JARS_JFREE_SVG_JAR):$(CLASSES_DIR_LOGO) dev.flang.misc.logo.FuzionLogo $@
	inkscape $@ --export-filename $@.pdf
	touch $@

$(BUILD_DIR)/assets/logo_bleed.svg: $(CLASS_FILES_MISC_LOGO)
	mkdir -p $(@D)
	$(JAVA) --class-path $(CLASSES_DIR):$(JARS_JFREE_SVG_JAR):$(CLASSES_DIR_LOGO) dev.flang.misc.logo.FuzionLogo -b $@
	inkscape $@ --export-filename $@.tmp.pdf
	pdfjam --papersize '{46mm,46mm}' --outfile $@.pdf $@.tmp.pdf
	rm -f $@.tmp.pdf
	touch $@

$(BUILD_DIR)/assets/logo_bleed_cropmark.svg: $(CLASS_FILES_MISC_LOGO)
	mkdir -p $(@D)
	$(JAVA) --class-path $(CLASSES_DIR):$(JARS_JFREE_SVG_JAR):$(CLASSES_DIR_LOGO) dev.flang.misc.logo.FuzionLogo -c $@
	inkscape $@ --export-filename $@.tmp.pdf
	pdfjam --papersize '{46mm,46mm}' --outfile $@.pdf $@.tmp.pdf
	rm -f $@.tmp.pdf
	touch $@

$(FZ): $(FZ_SRC)/bin/fz | $(CLASS_FILES_TOOLS)
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/bin/fz $@
	chmod +x $@

$(MOD_BASE): $(FZ) $(shell find $(FZ_SRC)/modules/base/src -name "*.fz")
	rm -rf $(@D)/base
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/modules/base $(@D)
	$(FZ) -sourceDirs=$(BUILD_DIR)/modules/base/src -XloadBaseModule=off -saveModule=$@ -XenableSetKeyword
	$(FZ) -XXcheckIntrinsics

# keep make from deleting $(MOD_BASE) on ctrl-C:
.PRECIOUS: $(MOD_BASE)

$(MOD_TERMINAL): $(MOD_BASE) $(FZ) $(shell find $(FZ_SRC)/modules/terminal/src -name "*.fz")
	rm -rf $(@D)/terminal
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/modules/terminal $(@D)
	$(FZ) -sourceDirs=$(BUILD_DIR)/modules/terminal/src -saveModule=$@

$(MOD_LOCK_FREE): $(MOD_BASE) $(FZ) $(shell find $(FZ_SRC)/modules/lock_free/src -name "*.fz")
	rm -rf $(@D)/lock_free
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/modules/lock_free $(@D)
	$(FZ) -sourceDirs=$(BUILD_DIR)/modules/lock_free/src -saveModule=$@

$(MOD_NOM): $(MOD_BASE) $(FZ) $(shell find $(FZ_SRC)/modules/nom/src -name "*.fz")
	rm -rf $(@D)/nom
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/modules/nom $(@D)
	$(FZ) -sourceDirs=$(BUILD_DIR)/modules/nom/src -saveModule=$@

$(MOD_DATABASE): $(MOD_BASE) $(FZ) $(shell find $(FZ_SRC)/modules/database/src -name "*.fz")
	rm -rf $(@D)/database
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/modules/database $(@D)
	$(FZ) -sourceDirs=$(BUILD_DIR)/modules/database/src -saveModule=$@

$(MOD_SQLITE): $(MOD_DATABASE) $(FZ) $(shell find $(FZ_SRC)/modules/sqlite/src -name "*.fz")
	rm -rf $(@D)/sqlite
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/modules/sqlite $(@D)
	$(FZ) -modules=database -sourceDirs=$(BUILD_DIR)/modules/sqlite/src -saveModule=$@

$(MOD_MAIL): $(MOD_WOLFSSL) $(FZ) $(shell find $(FZ_SRC)/modules/mail/src -name "*.fz")
	rm -rf $(@D)/mail
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/modules/mail $(@D)
	$(FZ) -modules=wolfssl -sourceDirs=$(BUILD_DIR)/modules/mail/src -saveModule=$@

$(MOD_UUID): $(MOD_BASE) $(FZ) $(shell find $(FZ_SRC)/modules/uuid/src -name "*.fz")
	rm -rf $(@D)/uuid
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/modules/uuid $(@D)
	$(FZ) -sourceDirs=$(BUILD_DIR)/modules/uuid/src -saveModule=$@

$(MOD_HTTP): $(MOD_BASE) $(FZ) $(shell find $(FZ_SRC)/modules/http/src -name "*.fz")
	rm -rf $(@D)/http
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/modules/http $(@D)
	$(FZ) -sourceDirs=$(BUILD_DIR)/modules/http/src -saveModule=$@

$(MOD_CLANG): $(MOD_BASE) $(FZ) $(shell find $(FZ_SRC)/modules/clang/src -name "*.fz")
	rm -rf $(@D)/clang
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/modules/clang $(@D)
	$(FZ) -sourceDirs=$(BUILD_DIR)/modules/clang/src -saveModule=$@

$(MOD_WOLFSSL): $(MOD_BASE) $(FZ) $(shell find $(FZ_SRC)/modules/wolfssl/src -name "*.fz")
	rm -rf $(@D)/wolfssl
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/modules/wolfssl $(@D)
	$(FZ) -sourceDirs=$(BUILD_DIR)/modules/wolfssl/src -saveModule=$@

$(MOD_JSON_ENCODE): $(MOD_BASE) $(FZ) $(shell find $(FZ_SRC)/modules/json_encode/src -name "*.fz")
	rm -rf $(@D)/json_encode
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/modules/json_encode $(@D)
	$(FZ) -sourceDirs=$(BUILD_DIR)/modules/json_encode/src -saveModule=$@

$(MOD_WEB): $(MOD_HTTP) $(MOD_WOLFSSL) $(FZ) $(shell find $(FZ_SRC)/modules/web/src -name "*.fz")
	rm -rf $(@D)/web
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/modules/web $(@D)
	$(FZ) -modules=http,wolfssl -sourceDirs=$(BUILD_DIR)/modules/web/src -saveModule=$@

$(MOD_SODIUM): $(MOD_BASE) $(FZ) $(shell find $(FZ_SRC)/modules/sodium/src -name "*.fz")
	rm -rf $(@D)/sodium
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/modules/sodium $(@D)
	$(FZ) -sourceDirs=$(BUILD_DIR)/modules/sodium/src -saveModule=$@

$(MOD_CRYPTO): $(MOD_SODIUM) $(FZ) $(shell find $(FZ_SRC)/modules/crypto/src -name "*.fz")
	rm -rf $(@D)/crypto
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/modules/crypto $(@D)
	$(FZ) -modules=sodium -sourceDirs=$(BUILD_DIR)/modules/crypto/src -saveModule=$@

$(MOD_WEBSERVER): $(MOD_HTTP) $(FZ) $(shell find $(FZ_SRC)/modules/webserver/src -name "*.fz")
	rm -rf $(@D)/webserver
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/modules/webserver $(@D)
	$(FZ) -modules=http -sourceDirs=$(BUILD_DIR)/modules/webserver/src -saveModule=$@

$(FZJAVA): $(FZ_SRC)/bin/fzjava | $(CLASS_FILES_TOOLS_FZJAVA)
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/bin/fzjava $@
	chmod +x $@

$(BUILD_DIR)/bin/check_simple_example: $(FZ_SRC)/bin/check_simple_example.fz | $(FUZION_BASE) $(MOD_TERMINAL)
	$(FZ) -modules=terminal -c -o=$@ $(FZ_SRC)/bin/check_simple_example.fz
	@echo " + $@"

$(BUILD_DIR)/bin/record_simple_example: $(FZ_SRC)/bin/record_simple_example.fz | $(FUZION_BASE) $(MOD_TERMINAL)
	$(FZ) -modules=terminal -c -o=$@ $(FZ_SRC)/bin/record_simple_example.fz
	@echo " + $@"

$(BUILD_DIR)/tests: $(FUZION_FILES_TESTS) $(BUILD_DIR)/include $(BUILD_DIR)/bin/check_simple_example $(BUILD_DIR)/bin/record_simple_example
	rm -rf $@
	mkdir -p $(@D)
	cp -rf $(FZ_SRC_TESTS) $@

$(BUILD_DIR)/include: $(FUZION_FILES_RT)
	rm -rf $@
	mkdir -p $(@D)
	cp -rf $(FZ_SRC_INCLUDE) $@

$(BUILD_DIR)/examples: $(FUZION_FILES_EXAMPLES)
	rm -rf $@
	mkdir -p $(@D)
	cp -rf $(FZ_SRC_EXAMPLES) $@

$(BUILD_DIR)/UnicodeData.txt:
	cd $(BUILD_DIR) && wget $(UNICODE_SOURCE)

$(BUILD_DIR)/UnicodeData.java.generated: $(CLASS_FILES_UTIL_UNICODE) $(BUILD_DIR)/UnicodeData.txt
	$(JAVA) --class-path $(CLASSES_DIR) dev.flang.util.unicode.ParseUnicodeData $(BUILD_DIR)/UnicodeData.txt >$@

$(BUILD_DIR)/UnicodeData.java: $(BUILD_DIR)/UnicodeData.java.generated $(SRC)/dev/flang/util/UnicodeData.java.in
	sed -e '/@@@ generated code start @@@/r build/UnicodeData.java.generated' $(SRC)/dev/flang/util/UnicodeData.java.in >$@

# phony target to regenerate UnicodeData.java using the latest UnicodeData.txt.
# This must be phony since $(SRC)/dev/flang/util/UnicodeData.java would
# be a circular dependency
.phony: unicode
unicode: $(BUILD_DIR)/UnicodeData.java $(BUILD_DIR)/unicode_data.fz
	cp $(BUILD_DIR)/UnicodeData.java $(SRC)/dev/flang/util/UnicodeData.java
	cp $(BUILD_DIR)/unicode_data.fz $(FZ_SRC)/modules/base/src/encodings/unicode/data.fz

# generate $(BUILD_DIR)/unicode_data.fz using the latest UnicodeData.txt.
$(BUILD_DIR)/unicode_data.fz: $(CLASS_FILES_UTIL_UNICODE) $(BUILD_DIR)/UnicodeData.txt
	$(JAVA) --class-path $(CLASSES_DIR) dev.flang.util.unicode.ParseUnicodeData -fz $(BUILD_DIR)/UnicodeData.txt > $@

# phony target to regenerate Fuzion logo.
# This must be phony since $(SRC)/assets/logo.svg would be a circular dependency
.phony: logo
logo: $(BUILD_DIR)/assets/logo.svg $(BUILD_DIR)/assets/logo_bleed.svg $(BUILD_DIR)/assets/logo_bleed_cropmark.svg
	cp $^ $(FZ_SRC)/assets/

$(BUILD_DIR)/bin/run_tests: $(FZ) $(FZ_MODULES) $(FZ_SRC)/bin/run_tests.fz
	$(FZ) -modules=lock_free,web,http,wolfssl -c -CLink=wolfssl -CInclude="wolfssl/options.h wolfssl/ssl.h" $(FZ_SRC)/bin/run_tests.fz -o=$@

# phony target to run Fuzion tests and report number of failures
.PHONY: run_tests
run_tests: run_tests_fuir run_tests_jvm run_tests_c run_tests_effect run_tests_jar

TEST_DEPENDENCIES = $(FZ_MODULES) $(MOD_JAVA_BASE) $(MOD_FZ_CMD) $(BUILD_DIR)/tests $(BUILD_DIR)/bin/run_tests $(BUILD_DIR)/fuzion.jar

# phony target to run Fuzion tests using effects and report number of failures
.PHONY .SILENT: run_tests_effect
run_tests_effect: $(FZ) $(TEST_DEPENDENCIES)
	printf "testing effects: "
	$(BUILD_DIR)/bin/run_tests $(BUILD_DIR) effect

# phony target to run Fuzion tests using interpreter and report number of failures
.PHONY .SILENT: run_tests_int
run_tests_int: $(FZ_INT) $(TEST_DEPENDENCIES)
	printf "testing interpreter: "
	$(BUILD_DIR)/bin/run_tests $(BUILD_DIR) int

# phony target to run Fuzion tests using c backend and report number of failures
.PHONY .SILENT: run_tests_c
run_tests_c: $(FZ_C) $(TEST_DEPENDENCIES)
	printf "testing C backend: "; \
	$(BUILD_DIR)/bin/run_tests $(BUILD_DIR) c

# phony target to run Fuzion tests using jvm backend and report number of failures
.PHONY .SILENT: run_tests_jvm
run_tests_jvm: $(FZ_JVM) $(TEST_DEPENDENCIES)
	printf "testing JVM backend: "; \
	$(BUILD_DIR)/bin/run_tests $(BUILD_DIR) jvm

.PHONY .SILENT: run_tests_fuir
run_tests_fuir: $(TEST_DEPENDENCIES)
	printf "testing FUIR backend: "; \
	$(BUILD_DIR)/bin/run_tests $(BUILD_DIR) fuir

.PHONY .SILENT: run_tests_jar_build
run_tests_jar_build: $(FZ_JVM) $(BUILD_DIR)/tests
	$(FZ) -jar $(BUILD_DIR)/tests/hello/HelloWorld.fz
	LD_LIBRARY_PATH="$(LD_LIBRARY_PATH):$(BUILD_DIR)/lib" \
	PATH="$(PATH):$(BUILD_DIR)/lib" \
	DYLD_FALLBACK_LIBRARY_PATH="$(DYLD_FALLBACK_LIBRARY_PATH):$(BUILD_DIR)/lib" \
		$(JAVA) -jar HelloWorld.jar > /dev/null

.PHONY .SILENT: run_tests_jar
run_tests_jar: run_tests_jar_build
	output1="Hello World!"; \
	output2=$$(./HelloWorld); \
	if [ "$$output1" != "$$output2" ]; then \
		echo "Outputs are different $$output1, $$output2!"; \
		exit 1; \
	fi
	rm -f HelloWorld HelloWorld.jar libfuzion_rt.so libfuzion_rt.dylib fuzion_rt.dll

.PHONY: clean
clean:
	rm -rf $(BUILD_DIR)
	rm -rf fuzion_generated_clazzes
	find $(FZ_SRC) -name "*~" -type f -exec rm {} \;

.PHONY: release
release: clean all
	rm -f fuzion_$(VERSION).tar.gz
	tar cfz fuzion_$(VERSION).tar.gz --transform s/^build/fuzion_$(VERSION)/ build

SYNTAX_CHECK_MODULES = terminal,clang,lock_free,java.base,java.datatransfer,java.xml,java.desktop,web,http,wolfssl
# target to do a syntax check of fz files.
# currently only code in bin/ and examples/ are checked.
.PHONY: syntaxcheck
syntaxcheck: min-java
	find ./examples/ -name '*.fz' -print0 | xargs -0L1 $(FZ) -modules=$(SYNTAX_CHECK_MODULES) -noBackend
	find ./bin/ -name '*.fz' -print0 | xargs -0L1 $(FZ) -modules=$(SYNTAX_CHECK_MODULES) -noBackend

.PHONY: add_simple_test
add_simple_test: no-java
	(cd $(FZ_SRC); $(FZ) $(FZ_SRC)/bin/add_simple_test.fz; cd -)

.PHONY: rerecord_simple_tests
rerecord_simple_tests:
	echo "ATTENTION: This rerecording is naive. You will have to manually revert any inappropriate changes after recording session."
	for file in tests/*/ ; do if [ "$$(find "$$file" -maxdepth 1 -type f -name "*.expected_out" -print -quit)" ]; then make record -C build/"$$file"/; fi done
	rsync -a --include='*/' --include='*.expected_*' --exclude='*' build/tests/ tests/

.PHONY: rerecord_effects
rerecord_effects:
	for file in tests/*/ ; do if [ "$$(find "$$file" -maxdepth 1 -type f -name "*.effect" -print -quit)" ]; then make record_effect -C build/"$$file"/; fi done
	rsync -a --include='*/' --include='*.effect' --exclude='*' build/tests/ tests/

$(MOD_FZ_CMD_DIR).jmod: $(FUZION_BASE)
	rm -f $(MOD_FZ_CMD_DIR).jmod
	jmod create --class-path $(CLASSES_DIR) $(MOD_FZ_CMD_DIR).jmod
	@echo " + build/modules/fz_cmd.jmod"

$(MOD_FZ_CMD_FZ_FILES): $(MOD_FZ_CMD_DIR).jmod $(MOD_JAVA_BASE) $(MOD_JAVA_MANAGEMENT) $(MOD_JAVA_DESKTOP) $(MOD_JAVA_NET_HTTP)
	rm -rf $(MOD_FZ_CMD_DIR)
	$(FZJAVA) -to=$(MOD_FZ_CMD_DIR) -modules=java.base,java.management,java.desktop,java.net.http $(MOD_FZ_CMD_DIR)
	touch $@

$(MOD_FZ_CMD): $(MOD_FZ_CMD_FZ_FILES)
	$(FZ) -sourceDirs=$(MOD_FZ_CMD_DIR) -modules=java.base,java.management,java.desktop,java.net.http -saveModule=$@


$(FUZION_RT): $(BUILD_DIR)/include $(FUZION_FILES_RT)
# NYI: HACK: we just put them into /lib even though this src folder of base-lib currently
# NYI: a bit hacky to have so/dylib regardless of which OS.
# NYI: -DGC_THREADS -DGC_PTHREADS -DGC_WIN32_PTHREADS
	mkdir -p $(BUILD_DIR)/lib
ifeq ($(OS),Windows_NT)
	clang --target=x86_64-w64-windows-gnu -Wall -Werror -O3 -shared \
	-DPTW32_STATIC_LIB \
	-fno-trigraphs -fno-omit-frame-pointer -mno-omit-leaf-frame-pointer -std=c11 \
	$(BUILD_DIR)/include/win.c $(BUILD_DIR)/include/shared.c -o $@ \
	-lMswsock -lAdvApi32 -lWs2_32
else
	clang -Wall -Werror -O3 -shared -fPIC \
	-fno-trigraphs -fno-omit-frame-pointer -mno-omit-leaf-frame-pointer -std=c11 \
	$(BUILD_DIR)/include/posix.c $(BUILD_DIR)/include/shared.c -o $@
endif
# NYI: eventuall link libgc
# ifeq ($(OS),Windows_NT)
# 	clang --target=x86_64-w64-windows-gnu -Wall -Werror -O3 -shared \
# 	-DPTW32_STATIC_LIB -DGC_WIN32_PTHREADS \
# 	-fno-trigraphs -fno-omit-frame-pointer -mno-omit-leaf-frame-pointer -std=c11 \
# 	$(BUILD_DIR)/include/win.c $(BUILD_DIR)/include/shared.c -o $(BUILD_DIR)/lib/fuzion.dll \
# 	-lMswsock -lAdvApi32 -lWs2_32 /ucrt64/bin/libgc-1.dll -lgc
# else
# 	clang -Wall -Werror -O3 -shared \
# 	-fno-trigraphs -fno-omit-frame-pointer -mno-omit-leaf-frame-pointer -std=c11 \
# 	$(BUILD_DIR)/include/posix.c $(BUILD_DIR)/include/shared.c -o $(BUILD_DIR)/lib/libfuzion_rt.so \
# 	-lgc
# 	cp $(BUILD_DIR)/lib/libfuzion_rt.so $(BUILD_DIR)/lib/libfuzion_rt.dylib
# endif
	@echo " + "$@


# rules relevant for language server protocol
#
include $(FZ_SRC)/lsp.mk

# documentation
#
include $(FZ_SRC)/docs.mk

# tools to help with development
#
include $(FZ_SRC)/tools.mk

# NYI: CLEANUP: move included makefiles to subfolder

.PHONY: debian_package
debian_package:
	debuild -us -uc


# builds a *fat* jar containing all java classes
# including org.eclipse and gson, necessary for running the language server
#
$(BUILD_DIR)/fuzion.jar: $(CLASS_FILES_LSP) $(FUZION_BASE) $(FZ_SRC)/assets/Manifest.txt
# delete signatures otherwise we would get: "Invalid signature file digest for Manifest main attributes"
	rm -rf $(BUILD_DIR)/classes_lsp/META-INF
	rm -f $(BUILD_DIR)/classes_lsp/about.html
	jar cfm $@ $(FZ_SRC)/assets/Manifest.txt -C $(BUILD_DIR)/classes . -C $(BUILD_DIR)/classes_lsp .
