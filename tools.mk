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


# shellcheck (https://www.shellcheck.net) checks shell scripts for issues/bugs
.PHONY: shellcheck
shellcheck:
	shellcheck $(SHELL_SCRIPTS) $(shell find . -iname '*.sh' -not -path "./build/*" -not -path "./debian/*")

# show readme in browser, requires 'sudo apt install grip'
.PHONY: show_readme
show_readme:
	grip -b README.md

# show release notes in browser, requires 'sudo apt install grip'
.PHONY: show_release_notes
show_release_notes:
	grip -b release_notes.md

# do spell checking of comments and strings in java source code.
.PHONY: spellcheck
spellcheck:
	bin/spell_check_java.sh

.PHONY: lint-java
lint-java:
	$(JAVAC) -Xlint --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) \
		$(JAVA_FILES_UTIL) \
		$(JAVA_FILES_UTIL_UNICODE) \
		$(JAVA_FILES_AST) \
		$(JAVA_FILES_PARSER) \
		$(JAVA_FILES_IR) \
		$(JAVA_FILES_MIR) \
		$(JAVA_FILES_FE) \
		$(JAVA_FILES_FUIR) \
		$(JAVA_FILES_FUIR_ANALYSIS) \
		$(JAVA_FILES_FUIR_ANALYSIS_DFA) \
		$(JAVA_FILES_FUIR_CFG) \
		$(JAVA_FILES_OPT) \
		$(JAVA_FILES_BE_INTERPRETER) \
		$(JAVA_FILES_BE_C) \
		$(JAVA_FILES_BE_EFFECTS) \
		$(JAVA_FILES_BE_JVM) \
		$(JAVA_FILES_BE_JVM_CLASSFILE) \
		$(JAVA_FILES_BE_JVM_RUNTIME) \
		$(JAVA_FILES_TOOLS) \
		$(JAVA_FILES_TOOLS_FZJAVA) \
		$(JAVA_FILES_TOOLS_DOCS)

.PHONY: lint-javadoc
lint-javadoc:
	$(JAVAC) -Xdoclint:all,-missing --class-path $(CLASSES_DIR) -d $(CLASSES_DIR) \
		$(JAVA_FILES_UTIL) \
		$(JAVA_FILES_UTIL_UNICODE) \
		$(JAVA_FILES_AST) \
		$(JAVA_FILES_PARSER) \
		$(JAVA_FILES_IR) \
		$(JAVA_FILES_MIR) \
		$(JAVA_FILES_FE) \
		$(JAVA_FILES_FUIR) \
		$(JAVA_FILES_FUIR_ANALYSIS) \
		$(JAVA_FILES_FUIR_ANALYSIS_DFA) \
		$(JAVA_FILES_FUIR_CFG) \
		$(JAVA_FILES_OPT) \
		$(JAVA_FILES_BE_INTERPRETER) \
		$(JAVA_FILES_BE_C) \
		$(JAVA_FILES_BE_EFFECTS) \
		$(JAVA_FILES_BE_JVM) \
		$(JAVA_FILES_BE_JVM_CLASSFILE) \
		$(JAVA_FILES_BE_JVM_RUNTIME) \
		$(JAVA_FILES_TOOLS) \
		$(JAVA_FILES_TOOLS_FZJAVA) \
		$(JAVA_FILES_TOOLS_DOCS)

.PHONY: remove_unused_imports
remove_unused_imports:
	wget --output-document /tmp/google-java-format-1.21.0-all-deps.jar https://github.com/google/google-java-format/releases/download/v1.21.0/google-java-format-1.21.0-all-deps.jar
	$(JAVA) -jar /tmp/google-java-format-1.21.0-all-deps.jar -r --fix-imports-only  --skip-sorting-imports `find src/`


$(BUILD_DIR)/pmd.zip:
	mkdir -p $(@D)
	wget --output-document $@ https://github.com/pmd/pmd/releases/download/pmd_releases%2F7.3.0/pmd-dist-7.3.0-bin.zip

$(BUILD_DIR)/pmd: $(BUILD_DIR)/pmd.zip
	echo "7e56043b5db83b288804c97d48a46db37bba22861b63eadd8e69f72c74bfb0a8 $(BUILD_DIR)/pmd.zip" > $(BUILD_DIR)/pmd.zip.sha256
	sha256sum --check $(BUILD_DIR)/pmd.zip.sha256
	unzip $(BUILD_DIR)/pmd.zip -d $@

# this linter detects different things than standard java linter
# but gives a lot of suggestions.
# use grep, e.g.: make lint-pmd | grep 'UnusedLocalVariable'
#
.PHONY: lint-pmd
lint-pmd: $(BUILD_DIR)/pmd
	$(BUILD_DIR)/pmd/pmd-bin-7.3.0/bin/pmd check -d src -R rulesets/java/quickstart.xml -f text

.PHONY: lint-c
lint-c:
	clang-tidy $(C_FILES) -- -std=c11
