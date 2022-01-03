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

JAVA_FILES_UTIL = \
          $(SRC)/dev/flang/util/ANY.java \
          $(SRC)/dev/flang/util/Callable.java \
          $(SRC)/dev/flang/util/DataOut.java \
          $(SRC)/dev/flang/util/Errors.java \
          $(SRC)/dev/flang/util/FuzionOptions.java \
          $(SRC)/dev/flang/util/FuzionConstants.java \
          $(SRC)/dev/flang/util/Intervals.java \
          $(SRC)/dev/flang/util/List.java \
          $(SRC)/dev/flang/util/Map2Int.java \
          $(SRC)/dev/flang/util/MapComparable2Int.java \
          $(SRC)/dev/flang/util/Profiler.java \
          $(SRC)/dev/flang/util/SourceDir.java \
          $(SRC)/dev/flang/util/SourceFile.java \
          $(SRC)/dev/flang/util/SourcePosition.java \
          $(SRC)/dev/flang/util/Terminal.java \
          $(SRC)/dev/flang/util/UnicodeData.java \
          $(SRC)/dev/flang/util/YesNo.java \

JAVA_FILES_UTIL_UNICODE = \
          $(SRC)/dev/flang/util/unicode/ParseUnicodeData.java \

JAVA_FILES_AST = \
          $(SRC)/dev/flang/ast/AbstractCall.java \
          $(SRC)/dev/flang/ast/AbstractCase.java \
          $(SRC)/dev/flang/ast/AbstractFeature.java \
          $(SRC)/dev/flang/ast/AbstractMatch.java \
          $(SRC)/dev/flang/ast/AbstractType.java \
          $(SRC)/dev/flang/ast/Assign.java \
          $(SRC)/dev/flang/ast/AstErrors.java \
          $(SRC)/dev/flang/ast/Block.java \
          $(SRC)/dev/flang/ast/BoolConst.java \
          $(SRC)/dev/flang/ast/Box.java \
          $(SRC)/dev/flang/ast/Call.java \
          $(SRC)/dev/flang/ast/Case.java \
          $(SRC)/dev/flang/ast/Check.java \
          $(SRC)/dev/flang/ast/Cond.java \
          $(SRC)/dev/flang/ast/Consts.java \
          $(SRC)/dev/flang/ast/Constant.java \
          $(SRC)/dev/flang/ast/Contract.java \
          $(SRC)/dev/flang/ast/Current.java \
          $(SRC)/dev/flang/ast/Destructure.java \
          $(SRC)/dev/flang/ast/Expr.java \
          $(SRC)/dev/flang/ast/Feature.java \
          $(SRC)/dev/flang/ast/FeatureName.java \
          $(SRC)/dev/flang/ast/FeatureVisitor.java \
          $(SRC)/dev/flang/ast/FeaturesAndOuter.java \
          $(SRC)/dev/flang/ast/FormalGenerics.java \
          $(SRC)/dev/flang/ast/Function.java \
          $(SRC)/dev/flang/ast/FunctionReturnType.java \
          $(SRC)/dev/flang/ast/Generic.java \
          $(SRC)/dev/flang/ast/If.java \
          $(SRC)/dev/flang/ast/InlineArray.java \
          $(SRC)/dev/flang/ast/Impl.java \
          $(SRC)/dev/flang/ast/IncompatibleResultsOnBranches.java \
          $(SRC)/dev/flang/ast/Loop.java \
          $(SRC)/dev/flang/ast/Match.java \
          $(SRC)/dev/flang/ast/Nop.java \
          $(SRC)/dev/flang/ast/NoType.java \
          $(SRC)/dev/flang/ast/NumLiteral.java \
          $(SRC)/dev/flang/ast/Old.java \
          $(SRC)/dev/flang/ast/RefType.java \
          $(SRC)/dev/flang/ast/Resolution.java \
          $(SRC)/dev/flang/ast/ReturnType.java \
          $(SRC)/dev/flang/ast/SrcModule.java \
          $(SRC)/dev/flang/ast/StatementVisitor.java \
          $(SRC)/dev/flang/ast/Stmnt.java \
          $(SRC)/dev/flang/ast/StrConst.java \
          $(SRC)/dev/flang/ast/Tag.java \
          $(SRC)/dev/flang/ast/This.java \
          $(SRC)/dev/flang/ast/Type.java \
          $(SRC)/dev/flang/ast/Types.java \
          $(SRC)/dev/flang/ast/Unbox.java \
          $(SRC)/dev/flang/ast/Universe.java \
          $(SRC)/dev/flang/ast/ValueType.java \
          $(SRC)/dev/flang/ast/Visi.java \

JAVA_FILES_PARSER = \
          $(SRC)/dev/flang/parser/FList.java \
          $(SRC)/dev/flang/parser/Lexer.java \
          $(SRC)/dev/flang/parser/Operator.java \
          $(SRC)/dev/flang/parser/OpExpr.java \
          $(SRC)/dev/flang/parser/Parser.java \

JAVA_FILES_IR = \
          $(SRC)/dev/flang/ir/IR.java \

JAVA_FILES_MIR = \
          $(SRC)/dev/flang/mir/MIR.java \
          $(SRC)/dev/flang/mir/MirModule.java \

JAVA_FILES_FE = \
          $(SRC)/dev/flang/fe/DFA.java \
          $(SRC)/dev/flang/fe/FeErrors.java \
          $(SRC)/dev/flang/fe/FrontEnd.java \
          $(SRC)/dev/flang/fe/FrontEndOptions.java \
          $(SRC)/dev/flang/fe/GenericType.java \
          $(SRC)/dev/flang/fe/LibraryCall.java \
          $(SRC)/dev/flang/fe/LibraryFeature.java \
          $(SRC)/dev/flang/fe/LibraryModule.java \
          $(SRC)/dev/flang/fe/LibraryOut.java \
          $(SRC)/dev/flang/fe/LibraryType.java \
          $(SRC)/dev/flang/fe/Module.java \
          $(SRC)/dev/flang/fe/NormalType.java \
          $(SRC)/dev/flang/fe/SourceModule.java \

JAVA_FILES_AIR = \
          $(SRC)/dev/flang/air/AIR.java \
          $(SRC)/dev/flang/air/AirErrors.java \
          $(SRC)/dev/flang/air/Clazz.java \
          $(SRC)/dev/flang/air/Clazzes.java \

JAVA_FILES_ME = \
          $(SRC)/dev/flang/me/MiddleEnd.java \

JAVA_FILES_FUIR = \
          $(SRC)/dev/flang/fuir/FUIR.java \

JAVA_FILES_OPT = \
          $(SRC)/dev/flang/opt/Optimizer.java \

JAVA_FILES_BE_INTERPRETER = \
          $(SRC)/dev/flang/be/interpreter/ArrayData.java \
          $(SRC)/dev/flang/be/interpreter/BackendCallable.java \
          $(SRC)/dev/flang/be/interpreter/Callable.java \
          $(SRC)/dev/flang/be/interpreter/ChoiceIdAsRef.java \
          $(SRC)/dev/flang/be/interpreter/DynamicBinding.java \
          $(SRC)/dev/flang/be/interpreter/Instance.java \
          $(SRC)/dev/flang/be/interpreter/Interpreter.java \
          $(SRC)/dev/flang/be/interpreter/JavaInterface.java \
          $(SRC)/dev/flang/be/interpreter/JavaRef.java \
          $(SRC)/dev/flang/be/interpreter/Layout.java \
          $(SRC)/dev/flang/be/interpreter/LValue.java \
          $(SRC)/dev/flang/be/interpreter/Intrinsics.java \
          $(SRC)/dev/flang/be/interpreter/Value.java \
          $(SRC)/dev/flang/be/interpreter/boolValue.java \
          $(SRC)/dev/flang/be/interpreter/i8Value.java \
          $(SRC)/dev/flang/be/interpreter/i16Value.java \
          $(SRC)/dev/flang/be/interpreter/i32Value.java \
          $(SRC)/dev/flang/be/interpreter/i64Value.java \
          $(SRC)/dev/flang/be/interpreter/u8Value.java \
          $(SRC)/dev/flang/be/interpreter/u16Value.java \
          $(SRC)/dev/flang/be/interpreter/u32Value.java \
          $(SRC)/dev/flang/be/interpreter/u64Value.java \
          $(SRC)/dev/flang/be/interpreter/f32Value.java \
          $(SRC)/dev/flang/be/interpreter/f64Value.java \

JAVA_FILES_BE_C = \
          $(SRC)/dev/flang/be/c/C.java \
          $(SRC)/dev/flang/be/c/CConstants.java \
          $(SRC)/dev/flang/be/c/CExpr.java \
          $(SRC)/dev/flang/be/c/CFile.java \
          $(SRC)/dev/flang/be/c/CIdent.java \
          $(SRC)/dev/flang/be/c/CNames.java \
          $(SRC)/dev/flang/be/c/COptions.java \
          $(SRC)/dev/flang/be/c/CStmnt.java \
          $(SRC)/dev/flang/be/c/CString.java \
          $(SRC)/dev/flang/be/c/CTypes.java \
          $(SRC)/dev/flang/be/c/Intrinsics.java \

JAVA_FILE_TOOLS_VERSION_IN = \
	  $(SRC)/dev/flang/tools/Version.java.in

JAVA_FILE_TOOLS_VERSION = \
	  $(BUILD_DIR)/generated/src/dev/flang/tools/Version.java

JAVA_FILES_TOOLS = \
          $(SRC)/dev/flang/tools/Fuzion.java \
          $(SRC)/dev/flang/tools/Latex.java \
          $(SRC)/dev/flang/tools/AceMode.java \
          $(SRC)/dev/flang/tools/Pretty.java \
          $(SRC)/dev/flang/tools/Tool.java \
	  $(JAVA_FILE_TOOLS_VERSION)

JAVA_FILES_TOOLS_FZJAVA = \
          $(SRC)/dev/flang/tools/fzjava/ForClass.java \
          $(SRC)/dev/flang/tools/fzjava/FZJava.java \
          $(SRC)/dev/flang/tools/fzjava/FZJavaOptions.java \
          $(SRC)/dev/flang/tools/fzjava/FeatureWriter.java \

JAVA_FILES_MISC_LOGO =\
          $(SRC)/dev/flang/misc/logo/FuzionLogo.java

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
	$(BUILD_DIR)/examples

DOCUMENTATION = \
	$(BUILD_DIR)/doc/fumfile.html     # fum file format documentation created with asciidoc

.PHONY: all
all: $(ALL)

# phony target to compile all java sources
.PHONY: javac
javac: $(CLASS_FILES_TOOLS) $(CLASS_FILES_TOOLS_FZJAVA)

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

$(BUILD_DIR)/lib: $(FZ_SRC)/lib
	mkdir -p $(@D)
	cp -rf $^ $@

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
.PHONY .SILENT: run_tests_int
run_tests_int: $(BUILD_DIR)/bin/fz $(BUILD_DIR)/tests
	echo -n "testing interpreter: "
	$(FZ_SRC)/bin/run_tests.sh $(BUILD_DIR) int

# phony target to run Fuzion tests using c backend and report number of failures
.PHONY .SILENT: run_tests_c
run_tests_c: $(BUILD_DIR)/bin/fz $(BUILD_DIR)/tests
	echo -n "testing C backend: "; \
	$(FZ_SRC)/bin/run_tests.sh $(BUILD_DIR) c

.PHONY: clean
clean:
	rm -rf $(BUILD_DIR)
	find $(FZ_SRC) -name "*~" -exec rm {} \;

.PHONY: release
release: clean all
	rm -f fuzion_$(VERSION).tar.gz
	tar cfz fuzion_$(VERSION).tar.gz -C build .
