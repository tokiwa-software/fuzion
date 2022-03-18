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

JAVA_FILES_UTIL           = $(wildcard $(SRC)/dev/flang/util/*.java          )
JAVA_FILES_UTIL_UNICODE   = $(wildcard $(SRC)/dev/flang/util/unicode/*.java  )
JAVA_FILES_AST            = $(wildcard $(SRC)/dev/flang/ast/*.java           )
JAVA_FILES_PARSER         = $(wildcard $(SRC)/dev/flang/parser/*.java        )
JAVA_FILES_IR             = $(wildcard $(SRC)/dev/flang/ir/*.java            )
JAVA_FILES_MIR            = $(wildcard $(SRC)/dev/flang/mir/*.java           )
JAVA_FILES_FE             = $(wildcard $(SRC)/dev/flang/fe/*.java            )
JAVA_FILES_AIR            = $(wildcard $(SRC)/dev/flang/air/*.java           )
JAVA_FILES_ME             = $(wildcard $(SRC)/dev/flang/me/*.java            )
JAVA_FILES_FUIR           = $(wildcard $(SRC)/dev/flang/fuir/*.java          )
JAVA_FILES_OPT            = $(wildcard $(SRC)/dev/flang/opt/*.java           )
JAVA_FILES_BE_INTERPRETER = $(wildcard $(SRC)/dev/flang/be/interpreter/*.java)
JAVA_FILES_BE_C           = $(wildcard $(SRC)/dev/flang/be/c/*.java          )
JAVA_FILES_TOOLS          = $(wildcard $(SRC)/dev/flang/tools/*.java         ) $(JAVA_FILE_TOOLS_VERSION)
JAVA_FILES_TOOLS_FZJAVA   = $(wildcard $(SRC)/dev/flang/tools/fzjava/*.java  )
JAVA_FILES_MISC_LOGO      = $(wildcard $(SRC)/dev/flang/misc/logo/*.java     )

CLASS_FILES_UTIL           = $(CLASSES_DIR)/dev/flang/util/__marker_for_make__
CLASS_FILES_UTIL_UNICODE   = $(CLASSES_DIR)/dev/flang/util/unicode/__marker_for_make__
CLASS_FILES_AST            = $(CLASSES_DIR)/dev/flang/ast/__marker_for_make__
CLASS_FILES_PARSER         = $(CLASSES_DIR)/dev/flang/parser/__marker_for_make__
CLASS_FILES_IR             = $(CLASSES_DIR)/dev/flang/ir/__marker_for_make__
CLASS_FILES_MIR            = $(CLASSES_DIR)/dev/flang/mir/__marker_for_make__
CLASS_FILES_FE             = $(CLASSES_DIR)/dev/flang/fe/__marker_for_make__
CLASS_FILES_AIR            = $(CLASSES_DIR)/dev/flang/air/__marker_for_make__
CLASS_FILES_ME             = $(CLASSES_DIR)/dev/flang/me/__marker_for_make__
CLASS_FILES_FUIR           = $(CLASSES_DIR)/dev/flang/fuir/__marker_for_make__
CLASS_FILES_OPT            = $(CLASSES_DIR)/dev/flang/opt/__marker_for_make__
CLASS_FILES_BE_INTERPRETER = $(CLASSES_DIR)/dev/flang/be/interpreter/__marker_for_make__
CLASS_FILES_BE_C           = $(CLASSES_DIR)/dev/flang/be/c/__marker_for_make__
CLASS_FILES_TOOLS          = $(CLASSES_DIR)/dev/flang/tools/__marker_for_make__
CLASS_FILES_TOOLS_FZJAVA   = $(CLASSES_DIR)/dev/flang/tools/fzjava/__marker_for_make__
CLASS_FILES_MISC_LOGO      = $(CLASSES_DIR)/dev/flang/misc/logo/__marker_for_make__

JFREE_SVG_URL = https://repo1.maven.org/maven2/org/jfree/org.jfree.svg/5.0.1/org.jfree.svg-5.0.1.jar
JARS_JFREE_SVG_JAR = $(BUILD_DIR)/jars/org.jfree.svg-5.0.1.jar

FUZION_EBNF = $(BUILD_DIR)/fuzion.ebnf

FZ_SRC_LIB = $(FZ_SRC)/lib
FUZION_FILES_LIB = $(shell find $(FZ_SRC_LIB) -name "*.fz")

MOD_BASE              = $(BUILD_DIR)/modules/base.fum
MOD_JAVA_BASE         = $(BUILD_DIR)/modules/java.base/__marker_for_make__
MOD_JAVA_XML          = $(BUILD_DIR)/modules/java.xml/__marker_for_make__
MOD_JAVA_DATATRANSFER = $(BUILD_DIR)/modules/java.datatransfer/__marker_for_make__
MOD_JAVA_DESKTOP      = $(BUILD_DIR)/modules/java.desktop/__marker_for_make__

VERSION = $(shell cat $(FZ_SRC)/version.txt)

ALL = \
	$(BUILD_DIR)/bin/fz \
	$(BUILD_DIR)/bin/fzjava \
	$(MOD_BASE) \
	$(MOD_JAVA_BASE) \
	$(MOD_JAVA_XML) \
	$(MOD_JAVA_DATATRANSFER) \
	$(MOD_JAVA_DESKTOP) \
	$(BUILD_DIR)/tests \
	$(BUILD_DIR)/examples \
        $(BUILD_DIR)/README.md \
        $(BUILD_DIR)/release_notes.md \

DOCUMENTATION = \
	$(BUILD_DIR)/doc/fumfile.html     # fum file format documentation created with asciidoc

SHELL_SCRIPTS = \
	bin/fz \
	bin/fzjava

.PHONY: all
all: $(ALL)

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

$(CLASS_FILES_OPT): $(JAVA_FILES_OPT) $(CLASS_FILES_AIR) $(CLASS_FILES_FUIR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_OPT)
	touch $@

$(CLASS_FILES_BE_INTERPRETER): $(JAVA_FILES_BE_INTERPRETER) $(CLASS_FILES_FUIR) $(CLASS_FILES_AIR) $(CLASS_FILES_AST)  # NYI: remove dependency on $(CLASS_FILES_AST), replace by $(CLASS_FILES_FUIR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_BE_INTERPRETER)
	touch $@

$(CLASS_FILES_BE_C): $(JAVA_FILES_BE_C) $(CLASS_FILES_FUIR)
	mkdir -p $(CLASSES_DIR)
	$(JAVAC) -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_BE_C)
	touch $@

$(CLASS_FILES_TOOLS): $(JAVA_FILES_TOOLS) $(CLASS_FILES_FE) $(CLASS_FILES_ME) $(CLASS_FILES_OPT) $(CLASS_FILES_BE_C) $(CLASS_FILES_BE_INTERPRETER)
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
	$(BUILD_DIR)/bin/fz -XsaveBaseLib=$@

$(BUILD_DIR)/bin/fzjava: $(FZ_SRC)/bin/fzjava $(CLASS_FILES_TOOLS_FZJAVA)
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/bin/fzjava $@
	chmod +x $@

$(MOD_JAVA_BASE): $(BUILD_DIR)/bin/fzjava
	rm -rf $(@D)
	mkdir -p $(@D)
# wrapping in /bin/bash -c "..." is a workaround for building on windows, bash (mingw)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.base -to=$(@D) -verbose=0"
	touch $@

$(MOD_JAVA_XML): $(BUILD_DIR)/bin/fzjava
	rm -rf $(@D)
	mkdir -p $(@D)
# wrapping in /bin/bash -c "..." is a workaround for building on windows, bash (mingw)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.xml -to=$(@D) -verbose=0"
	# NYI: manually delete redundant features
	rm -f $(BUILD_DIR)/modules/java.xml/Java_pkg.fz
	rm -f $(BUILD_DIR)/modules/java.xml/Java/jdk_pkg.fz
	rm -f $(BUILD_DIR)/modules/java.xml/Java/javax_pkg.fz
	rm -f $(BUILD_DIR)/modules/java.xml/Java/com_pkg.fz
	rm -f $(BUILD_DIR)/modules/java.xml/Java/com/sun_pkg.fz
	touch $@

$(MOD_JAVA_DATATRANSFER): $(BUILD_DIR)/bin/fzjava
	rm -rf $(@D)
	mkdir -p $(@D)
# wrapping in /bin/bash -c "..." is a workaround for building on windows, bash (mingw)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.datatransfer -to=$(@D) -verbose=0"
	# NYI: manually delete redundant features
	rm -f $(BUILD_DIR)/modules/java.datatransfer/Java_pkg.fz
	rm -f $(BUILD_DIR)/modules/java.datatransfer/Java/java_pkg.fz
	rm -f $(BUILD_DIR)/modules/java.datatransfer/Java/sun_pkg.fz
	rm -f $(BUILD_DIR)/modules/java.datatransfer/Java/java/awt_pkg.fz
	touch $@

$(MOD_JAVA_DESKTOP): $(BUILD_DIR)/bin/fzjava
	rm -rf $(@D)
	mkdir -p $(@D)
# wrapping in /bin/bash -c "..." is a workaround for building on windows, bash (mingw)
	/bin/bash -c "$(BUILD_DIR)/bin/fzjava java.desktop -to=$(@D) -verbose=0"
	# NYI: manually delete redundant features
	rm -f $(BUILD_DIR)/modules/java.desktop/Java_pkg.fz
	rm -f $(BUILD_DIR)/modules/java.desktop/Java/javax_pkg.fz
	rm -f $(BUILD_DIR)/modules/java.desktop/Java/com_pkg.fz
	rm -f $(BUILD_DIR)/modules/java.desktop/Java/sun_pkg.fz
	rm -f $(BUILD_DIR)/modules/java.desktop/Java/com/sun_pkg.fz
	rm -f $(BUILD_DIR)/modules/java.desktop/Java/java_pkg.fz
	touch $@

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
.PHONY .SILENT .IGNORE: run_tests_int
run_tests_int: $(BUILD_DIR)/bin/fz $(MOD_BASE) $(BUILD_DIR)/tests
	echo -n "testing interpreter: "
	$(FZ_SRC)/bin/run_tests.sh $(BUILD_DIR) int

# phony target to run Fuzion tests using c backend and report number of failures
.PHONY .SILENT .IGNORE: run_tests_c
run_tests_c: $(BUILD_DIR)/bin/fz $(MOD_BASE) $(BUILD_DIR)/tests
	echo -n "testing C backend: "; \
	$(FZ_SRC)/bin/run_tests.sh $(BUILD_DIR) c

# phony target to run Fuzion tests and report number of failures
.PHONY: run_tests_parallel
run_tests_parallel: run_tests_int_parallel run_tests_c_parallel

# phony target to run Fuzion tests using interpreter and report number of failures
.PHONY .SILENT: run_tests_int_parallel
run_tests_int_parallel: $(BUILD_DIR)/bin/fz $(MOD_BASE) $(BUILD_DIR)/tests
	echo -n "testing interpreter: "
	$(FZ_SRC)/bin/run_tests_parallel.sh $(BUILD_DIR) int

# phony target to run Fuzion tests using c backend and report number of failures
.PHONY .SILENT: run_tests_c_parallel
run_tests_c_parallel: $(BUILD_DIR)/bin/fz $(MOD_BASE) $(BUILD_DIR)/tests
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
