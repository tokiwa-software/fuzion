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
#  Tokiwa GmbH, Berlin
#
#  Source code of main Makefile
#
#  Author: Fridtjof Siebert (siebert@tokiwa.eu)
#
# -----------------------------------------------------------------------

# must be at least java 11
JAVA = java
BUILD_DIR = $(CURDIR)/build
CLASSES_DIR = $(BUILD_DIR)/classes
FUSION_HOME = $(BUILD_DIR)
FUSIONX = $(JAVA) \$$(JAVA_OPTS) -cp $(FUSION_HOME)/classes dev.flang.tools.Fusion

JAVA_FILES_UTIL = \
          src/dev/flang/util/ANY.java \
	  src/dev/flang/util/Errors.java \
	  src/dev/flang/util/FusionOptions.java \
	  src/dev/flang/util/List.java \
	  src/dev/flang/util/Map2Int.java \
	  src/dev/flang/util/MapComparable2Int.java \
	  src/dev/flang/util/SourceFile.java \
	  src/dev/flang/util/SourcePosition.java \

JAVA_FILES_AST = \
          src/dev/flang/ast/AdrToValue.java \
          src/dev/flang/ast/Assign.java \
          src/dev/flang/ast/Block.java \
          src/dev/flang/ast/BoolConst.java \
          src/dev/flang/ast/Box.java \
          src/dev/flang/ast/Call.java \
	  src/dev/flang/ast/Case.java \
          src/dev/flang/ast/Check.java \
	  src/dev/flang/ast/Cond.java \
          src/dev/flang/ast/Consts.java \
          src/dev/flang/ast/Contract.java \
	  src/dev/flang/ast/Current.java \
	  src/dev/flang/ast/Decompose.java \
          src/dev/flang/ast/Expr.java \
          src/dev/flang/ast/FeErrors.java \
          src/dev/flang/ast/Feature.java \
          src/dev/flang/ast/FeatureName.java \
	  src/dev/flang/ast/FeatureVisitor.java \
	  src/dev/flang/ast/FormalGenerics.java \
          src/dev/flang/ast/Function.java \
	  src/dev/flang/ast/FunctionReturnType.java \
	  src/dev/flang/ast/Generic.java \
          src/dev/flang/ast/If.java \
          src/dev/flang/ast/IntConst.java \
          src/dev/flang/ast/Impl.java \
	  src/dev/flang/ast/IncompatibleResultsOnBranches.java \
          src/dev/flang/ast/Loop.java \
	  src/dev/flang/ast/Match.java \
	  src/dev/flang/ast/Nop.java \
	  src/dev/flang/ast/NoType.java \
          src/dev/flang/ast/Old.java \
          src/dev/flang/ast/RefType.java \
	  src/dev/flang/ast/Resolution.java \
	  src/dev/flang/ast/ReturnType.java \
	  src/dev/flang/ast/Singleton.java \
          src/dev/flang/ast/SingleType.java \
          src/dev/flang/ast/Stmnt.java \
          src/dev/flang/ast/StrConst.java \
          src/dev/flang/ast/This.java \
	  src/dev/flang/ast/Type.java \
	  src/dev/flang/ast/Types.java \
          src/dev/flang/ast/ValueType.java \
          src/dev/flang/ast/Visi.java \

JAVA_FILES_PARSER = \
          src/dev/flang/parser/FList.java \
          src/dev/flang/parser/Lexer.java \
          src/dev/flang/parser/Operator.java \
          src/dev/flang/parser/OpExpr.java \
          src/dev/flang/parser/Parser.java \

JAVA_FILES_IR = \
          src/dev/flang/ir/Backend.java \
          src/dev/flang/ir/BackendCallable.java \
          src/dev/flang/ir/Clazz.java \
          src/dev/flang/ir/Clazzes.java \
          src/dev/flang/ir/DynamicBinding.java \
          src/dev/flang/ir/IrErrors.java \

JAVA_FILES_MIR = \
          src/dev/flang/mir/MIR.java \

JAVA_FILES_FE = \
          src/dev/flang/fe/FrontEnd.java \
          src/dev/flang/fe/FrontEndOptions.java \

JAVA_FILES_AIR = \
          src/dev/flang/air/AIR.java \

JAVA_FILES_ME = \
          src/dev/flang/me/MiddleEnd.java \

JAVA_FILES_FUIR = \
          src/dev/flang/fuir/FUIR.java \

JAVA_FILES_OPT = \
          src/dev/flang/opt/Optimizer.java \

JAVA_FILES_BE_INTERPRETER = \
          src/dev/flang/be/interpreter/Callable.java \
          src/dev/flang/be/interpreter/ChoiceIdAsRef.java \
          src/dev/flang/be/interpreter/Instance.java \
          src/dev/flang/be/interpreter/Interpreter.java \
          src/dev/flang/be/interpreter/JavaInterface.java \
          src/dev/flang/be/interpreter/LValue.java \
          src/dev/flang/be/interpreter/NativeFeature.java \
          src/dev/flang/be/interpreter/Value.java \
          src/dev/flang/be/interpreter/boolValue.java \
          src/dev/flang/be/interpreter/i32Value.java \
          src/dev/flang/be/interpreter/i64Value.java \
          src/dev/flang/be/interpreter/u32Value.java \
          src/dev/flang/be/interpreter/u64Value.java \

JAVA_FILES_BE_C = \
          src/dev/flang/be/c/C.java \

JAVA_FILES_TOOLS = \
          src/dev/flang/tools/Fusion.java \

CLASS_FILES_UTIL           = $(CLASSES_DIR)/dev/flang/util/__marker_for_make__
CLASS_FILES_AST            = $(CLASSES_DIR)/dev/flang/__marker_for_make__
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

FUZION_EBNF = $(BUILD_DIR)/fuzion.ebnf

.PHONY: all
all: $(CLASS_FILES_TOOLS) $(FUZION_EBNF) $(BUILD_DIR)/lib $(BUILD_DIR)/bin/fz $(BUILD_DIR)/tests

# phony target to compile all java sources
.PHONY: javac
javac: $(CLASS_FILES_TOOLS)

$(FUZION_EBNF): src/dev/flang/parser/Parser.java
	mkdir -p $(@D)
	which pcregrep && pcregrep -M "^[a-zA-Z].*:(\n|.)*?;" $^ >$@ || echo "*** need pcregrep tool installed" >$@

$(CLASS_FILES_UTIL): $(JAVA_FILES_UTIL)
	mkdir -p $(CLASSES_DIR)
	javac -d $(CLASSES_DIR) $(JAVA_FILES_UTIL)
	touch $@

$(CLASS_FILES_AST): $(JAVA_FILES_AST) $(CLASS_FILES_UTIL)
	mkdir -p $(CLASSES_DIR)
	javac -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_AST)
	touch $@

$(CLASS_FILES_PARSER): $(JAVA_FILES_PARSER) $(CLASS_FILES_AST) $(FUZION_EBNF)
	mkdir -p $(CLASSES_DIR)
	javac -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_PARSER)
	touch $@

$(CLASS_FILES_IR): $(JAVA_FILES_IR) $(CLASS_FILES_UTIL) $(CLASS_FILES_AST)  # NYI: remove dependency on $(CLASS_FILES_AST)
	mkdir -p $(CLASSES_DIR)
	javac -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_IR)
	touch $@

$(CLASS_FILES_MIR): $(JAVA_FILES_MIR) $(CLASS_FILES_IR)
	mkdir -p $(CLASSES_DIR)
	javac -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_MIR)
	touch $@

$(CLASS_FILES_FE): $(JAVA_FILES_FE) $(CLASS_FILES_PARSER) $(CLASS_FILES_AST) $(CLASS_FILES_MIR)
	mkdir -p $(CLASSES_DIR)
	javac -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_FE)
	touch $@

$(CLASS_FILES_AIR): $(JAVA_FILES_AIR) $(CLASS_FILES_UTIL) $(CLASS_FILES_IR)
	mkdir -p $(CLASSES_DIR)
	javac -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_AIR)
	touch $@

$(CLASS_FILES_ME): $(JAVA_FILES_ME) $(CLASS_FILES_MIR) $(CLASS_FILES_AIR)
	mkdir -p $(CLASSES_DIR)
	javac -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_ME)
	touch $@

$(CLASS_FILES_FUIR): $(JAVA_FILES_FUIR) $(CLASS_FILES_UTIL) $(CLASS_FILES_IR)
	mkdir -p $(CLASSES_DIR)
	javac -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_FUIR)
	touch $@

$(CLASS_FILES_OPT): $(JAVA_FILES_OPT) $(CLASS_FILES_AIR) $(CLASS_FILES_FUIR)
	mkdir -p $(CLASSES_DIR)
	javac -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_OPT)
	touch $@

$(CLASS_FILES_BE_INTERPRETER): $(JAVA_FILES_BE_INTERPRETER) $(CLASS_FILES_FUIR) $(CLASS_FILES_AST)  # NYI: remove dependency on $(CLASS_FILES_AST), replace by $(CLASS_FILES_FUIR)
	mkdir -p $(CLASSES_DIR)
	javac -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_BE_INTERPRETER)
	touch $@

$(CLASS_FILES_BE_C): $(JAVA_FILES_BE_C) $(CLASS_FILES_FUIR)
	mkdir -p $(CLASSES_DIR)
	javac -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_BE_C)
	touch $@

$(CLASS_FILES_TOOLS): $(JAVA_FILES_TOOLS) $(CLASS_FILES_FE) $(CLASS_FILES_ME) $(CLASS_FILES_OPT) $(CLASS_FILES_BE_C) $(CLASS_FILES_BE_INTERPRETER)
	mkdir -p $(CLASSES_DIR)
	javac -cp $(CLASSES_DIR) -d $(CLASSES_DIR) $(JAVA_FILES_TOOLS)
	touch $@

$(BUILD_DIR)/lib: lib
	mkdir -p $(@D)
	cp -rf $^ $@

$(BUILD_DIR)/bin/fz: bin/fz
	mkdir -p $(@D)
	cp -rf $^ $@
	chmod +x $@

$(BUILD_DIR)/tests: tests
	mkdir -p $(@D)
	cp -rf $^ $@

# phony target to run Fuzion tests and report number of failures
.PHONY: run_tests
run_tests: $(CLASS_FILES_TOOLS) $(BUILD_DIR)/lib $(BUILD_DIR)/tests
	rm -rf $(BUILD_DIR)/run_tests.results
	for test in $(shell echo $(BUILD_DIR)/tests/*); do \
	  FUSION="$(FUSIONX)" make -e -C >$$test/out.txt $$test 2>/dev/null && (echo -n "." && echo "$$test: ok" >>$(BUILD_DIR)/run_tests.results) || (echo -n "#"; echo "$$test: failed" >>$(BUILD_DIR)/run_tests.results); \
	done
	echo `cat $(BUILD_DIR)/run_tests.results | grep ok$$ | wc -l`/`ls tests | wc -l` tests passed, `cat $(BUILD_DIR)/run_tests.results | grep failed$$ | wc -l` tests failed; \
	cat $(BUILD_DIR)/run_tests.results | grep failed$$

clean:
	rm -rf $(BUILD_DIR)
	find . -name "*~" -exec rm {} \;
