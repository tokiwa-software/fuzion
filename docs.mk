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


DOC_FILES_FUMFILE = $(BUILD_DIR)/doc/files/fumfile.html     # fum file format documentation created with asciidoc
DOC_DESIGN_JVM    = $(BUILD_DIR)/doc/design/jvm.html
DOC_JAVA          = $(BUILD_DIR)/doc/java/index.html

REF_MANUAL_SOURCE  = $(FZ_SRC)/doc/ref_manual/fuzion_reference_manual.adoc
REF_MANUAL_SOURCES = $(wildcard $(FZ_SRC)/doc/ref_manual/*.adoc) \
                     $(wildcard $(FZ_SRC)/doc/ref_manual/*.txt) \
                     $(BUILD_DIR)/generated/doc/unicode_version.adoc \
                     $(BUILD_DIR)/generated/doc/codepoints_white_space.adoc \
                     $(BUILD_DIR)/generated/doc/codepoints_illegal.adoc \
                     $(BUILD_DIR)/generated/doc/codepoints_letter.adoc \
                     $(BUILD_DIR)/generated/doc/codepoints_digit.adoc \
                     $(BUILD_DIR)/generated/doc/codepoints_numeric.adoc \
                     $(BUILD_DIR)/generated/doc/codepoints_op.adoc \
                     $(BUILD_DIR)/generated/doc/keywords.adoc \
                     $(BUILD_DIR)/generated/doc/stringEscapes.adoc \
                     $(JAVA_FILES_UTIL) \
                     $(JAVA_FILES_PARSER) \
                     $(JAVA_FILES_FE)
REF_MANUAL_PDF     = $(BUILD_DIR)/doc/reference_manual/fuzion_reference_manual.pdf
REF_MANUAL_HTML    = $(BUILD_DIR)/doc/reference_manual/html/index.html

DOCUMENTATION = \
	$(DOC_FILES_FUMFILE) \
	$(DOC_DESIGN_JVM)    \
	$(REF_MANUAL_PDF)    \
	$(REF_MANUAL_HTML)   \
	$(DOC_JAVA)          \
	$(BUILD_DIR)/apidocs/index.html

$(REF_MANUAL_PDF): $(REF_MANUAL_SOURCES) $(BUILD_DIR)/generated/doc/fum_file.adoc $(FUZION_EBNF)
	mkdir -p $(@D)
	asciidoctor-pdf --failure-level=WARN $(REF_MANUAL_ATTRIBUTES) --out-file $@ $(REF_MANUAL_SOURCE)

$(REF_MANUAL_HTML): $(REF_MANUAL_SOURCES) $(BUILD_DIR)/generated/doc/fum_file.adoc $(FUZION_EBNF)
	mkdir -p $(@D)
	asciidoctor --failure-level=WARN $(REF_MANUAL_ATTRIBUTES) --out-file=$@ $(REF_MANUAL_SOURCE)

.phony: doc
doc: $(DOCUMENTATION)

$(BUILD_DIR)/generated/doc/fum_file.adoc: $(SRC)/dev/flang/fe/LibraryModule.java
	mkdir -p $(@D)
	sed -n '/--asciidoc--/,/--asciidoc--/p' $^ | grep --invert-match "\--asciidoc--" >$@

$(DOC_FILES_FUMFILE): $(BUILD_DIR)/generated/doc/fum_file.adoc
	mkdir -p $(@D)
	asciidoc - <$^ >$@

$(DOC_DESIGN_JVM): $(SRC)/dev/flang/be/jvm/JVM.java
	mkdir -p $(@D)
	sed -n '/--asciidoc--/,/--asciidoc--/p' $^ | grep --invert-match "\--asciidoc--" | asciidoc - >$@

REF_MANUAL_ATTRIBUTES = \
  --attribute FZ_SRC=$(realpath $(FZ_SRC)) \
  --attribute GENERATED=$(realpath $(BUILD_DIR)/generated) \
  --attribute FUZION_EBNF=$(realpath $(FUZION_EBNF)) \
  --attribute UNICODE_SOURCE=$(UNICODE_SOURCE)

$(DOC_JAVA): $(JAVA_FILE_UTIL_VERSION) $(JAVA_FILE_FUIR_ANALYSIS_ABSTRACT_INTERPRETER2)
	javadoc --release $(JAVA_VERSION) --enable-preview -d $(dir $(DOC_JAVA)) $(JAVA_FILES_FOR_JAVA_DOC)

$(BUILD_DIR)/generated/doc/unicode_version.adoc:
	mkdir -p $(@D)
	cd $(FZ_SRC) && git log modules/base/src/encodings/unicode/data.fz  | grep --extended-regexp "^Date:" | head | sed "s-Date:   -:UNICODE_VERSION: -g" | head -n1 > $(realpath $(@D))/unicode_version.adoc

$(BUILD_DIR)/generated/doc/codepoints_white_space.adoc: $(CLASS_FILES_PARSER)
	mkdir -p $(@D)
	$(JAVA) --class-path $(CLASSES_DIR) dev.flang.parser.Lexer -whiteSpace >$@

$(BUILD_DIR)/generated/doc/codepoints_illegal.adoc: $(CLASS_FILES_PARSER)
	mkdir -p $(@D)
	$(JAVA) --class-path $(CLASSES_DIR) dev.flang.parser.Lexer -illegal >$@

$(BUILD_DIR)/generated/doc/codepoints_letter.adoc: $(CLASS_FILES_PARSER)
	mkdir -p $(@D)
	$(JAVA) --class-path $(CLASSES_DIR) dev.flang.parser.Lexer -letter >$@

$(BUILD_DIR)/generated/doc/codepoints_digit.adoc: $(CLASS_FILES_PARSER)
	mkdir -p $(@D)
	$(JAVA) --class-path $(CLASSES_DIR) dev.flang.parser.Lexer -digit >$@

$(BUILD_DIR)/generated/doc/codepoints_numeric.adoc: $(CLASS_FILES_PARSER)
	mkdir -p $(@D)
	$(JAVA) --class-path $(CLASSES_DIR) dev.flang.parser.Lexer -numeric >$@

$(BUILD_DIR)/generated/doc/codepoints_op.adoc: $(CLASS_FILES_PARSER)
	mkdir -p $(@D)
	$(JAVA) --class-path $(CLASSES_DIR) dev.flang.parser.Lexer -op >$@

$(BUILD_DIR)/generated/doc/keywords.adoc: $(CLASS_FILES_PARSER)
	mkdir -p $(@D)
	$(JAVA) --class-path $(CLASSES_DIR) dev.flang.parser.Lexer -keywords >$@

$(BUILD_DIR)/generated/doc/stringEscapes.adoc: $(CLASS_FILES_PARSER)
	mkdir -p $(@D)
	$(JAVA) --class-path $(CLASSES_DIR) dev.flang.parser.Lexer -stringLiteralEscapes >$@

# NYI: UNDER DEVELOPMENT: integrate into fz: fz -docs
$(BUILD_DIR)/apidocs/index.html: $(FUZION_BASE) $(CLASS_FILES_TOOLS_DOCS) $(FUZION_FILES) $(MOD_FZ_CMD)
	$(JAVA) --class-path $(CLASSES_DIR) -Xss64m -Dfuzion.home=$(BUILD_DIR) dev.flang.tools.docs.Docs -bare -api-src=/api $(@D)

# NYI: UNDER DEVELOPMENT: integrate into fz: fz -docs
$(BUILD_DIR)/apidocs_git/index.html: $(FUZION_BASE) $(CLASS_FILES_TOOLS_DOCS) $(FUZION_FILES) $(MOD_FZ_CMD)
	$(JAVA) --class-path $(CLASSES_DIR) -Xss64m -Dfuzion.home=$(BUILD_DIR) dev.flang.tools.docs.Docs -bare -docs-root=/docs_git -api-src=/api_git $(@D)

# NYI: UNDER DEVELOPMENT: integrate into fz: fz -docs
.phony: debug_api_docs
debug_api_docs: $(FUZION_BASE) $(CLASS_FILES_TOOLS_DOCS)
	mkdir -p $(BUILD_DIR)/debugdocs
	cp assets/docs/style.css $(BUILD_DIR)/debugdocs/
	cp assets/docs/32.png $(BUILD_DIR)/debugdocs/
	$(JAVA) --class-path $(CLASSES_DIR) -Xss64m -Dfuzion.home=$(BUILD_DIR) dev.flang.tools.docs.Docs $(BUILD_DIR)/debugdocs
	jwebserver --port 15306 --directory $$(realpath $(BUILD_DIR)/debugdocs)

.phony: serve_docs
serve_docs: $(DOCUMENTATION)
	jwebserver --port 15307 --directory $$(realpath $(BUILD_DIR)/doc)

.phony: serve_java_docs
serve_java_docs: $(DOC_JAVA)
	jwebserver --port 15308 --directory $$(realpath $(BUILD_DIR)/doc/java)
