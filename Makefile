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
FZ_SRC = $(patsubst %/,%,$(dir $(abspath $(lastword $(MAKEFILE_LIST)))))
SRC = $(FZ_SRC)/src
BUILD_DIR = $(CURDIR)/build
CLASSES_DIR = $(BUILD_DIR)/classes
FUZIONX = $(JAVA) \$$(JAVA_OPTS) -cp $(CLASSES_DIR) dev.flang.tools.Fuzion

JAVA_FILES_UTIL = \
          $(SRC)/dev/flang/util/ANY.java \
          $(SRC)/dev/flang/util/Errors.java \
          $(SRC)/dev/flang/util/FuzionOptions.java \
          $(SRC)/dev/flang/util/FuzionConstants.java \
          $(SRC)/dev/flang/util/Intervals.java \
          $(SRC)/dev/flang/util/List.java \
          $(SRC)/dev/flang/util/Map2Int.java \
          $(SRC)/dev/flang/util/MapComparable2Int.java \
          $(SRC)/dev/flang/util/SourceFile.java \
          $(SRC)/dev/flang/util/SourcePosition.java \
          $(SRC)/dev/flang/util/Terminal.java \

JAVA_FILES_AST = \
          $(SRC)/dev/flang/ast/AdrToValue.java \
          $(SRC)/dev/flang/ast/Assign.java \
          $(SRC)/dev/flang/ast/Block.java \
          $(SRC)/dev/flang/ast/BoolConst.java \
          $(SRC)/dev/flang/ast/Box.java \
          $(SRC)/dev/flang/ast/Call.java \
          $(SRC)/dev/flang/ast/Case.java \
          $(SRC)/dev/flang/ast/Check.java \
          $(SRC)/dev/flang/ast/Cond.java \
          $(SRC)/dev/flang/ast/Consts.java \
          $(SRC)/dev/flang/ast/Contract.java \
          $(SRC)/dev/flang/ast/Current.java \
          $(SRC)/dev/flang/ast/Decompose.java \
          $(SRC)/dev/flang/ast/Expr.java \
          $(SRC)/dev/flang/ast/FeErrors.java \
          $(SRC)/dev/flang/ast/Feature.java \
          $(SRC)/dev/flang/ast/FeatureName.java \
          $(SRC)/dev/flang/ast/FeatureVisitor.java \
          $(SRC)/dev/flang/ast/FormalGenerics.java \
          $(SRC)/dev/flang/ast/Function.java \
          $(SRC)/dev/flang/ast/FunctionReturnType.java \
          $(SRC)/dev/flang/ast/Generic.java \
          $(SRC)/dev/flang/ast/If.java \
          $(SRC)/dev/flang/ast/IntConst.java \
          $(SRC)/dev/flang/ast/Impl.java \
          $(SRC)/dev/flang/ast/IncompatibleResultsOnBranches.java \
          $(SRC)/dev/flang/ast/Loop.java \
          $(SRC)/dev/flang/ast/Match.java \
          $(SRC)/dev/flang/ast/Nop.java \
          $(SRC)/dev/flang/ast/NoType.java \
          $(SRC)/dev/flang/ast/Old.java \
          $(SRC)/dev/flang/ast/RefType.java \
          $(SRC)/dev/flang/ast/Resolution.java \
          $(SRC)/dev/flang/ast/ReturnType.java \
          $(SRC)/dev/flang/ast/Singleton.java \
          $(SRC)/dev/flang/ast/SingleType.java \
          $(SRC)/dev/flang/ast/Stmnt.java \
          $(SRC)/dev/flang/ast/StrConst.java \
          $(SRC)/dev/flang/ast/Tag.java \
          $(SRC)/dev/flang/ast/This.java \
          $(SRC)/dev/flang/ast/Type.java \
          $(SRC)/dev/flang/ast/Types.java \
          $(SRC)/dev/flang/ast/ValueType.java \
          $(SRC)/dev/flang/ast/Visi.java \

JAVA_FILES_PARSER = \
          $(SRC)/dev/flang/parser/FList.java \
          $(SRC)/dev/flang/parser/Lexer.java \
          $(SRC)/dev/flang/parser/Operator.java \
          $(SRC)/dev/flang/parser/OpExpr.java \
          $(SRC)/dev/flang/parser/Parser.java \

JAVA_FILES_IR = \
          $(SRC)/dev/flang/ir/Backend.java \
          $(SRC)/dev/flang/ir/BackendCallable.java \
          $(SRC)/dev/flang/ir/Clazz.java \
          $(SRC)/dev/flang/ir/Clazzes.java \
          $(SRC)/dev/flang/ir/DynamicBinding.java \
          $(SRC)/dev/flang/ir/IrErrors.java \

JAVA_FILES_MIR = \
          $(SRC)/dev/flang/mir/MIR.java \

JAVA_FILES_FE = \
          $(SRC)/dev/flang/fe/FrontEnd.java \
          $(SRC)/dev/flang/fe/FrontEndOptions.java \

JAVA_FILES_AIR = \
          $(SRC)/dev/flang/air/AIR.java \

JAVA_FILES_ME = \
          $(SRC)/dev/flang/me/MiddleEnd.java \

JAVA_FILES_FUIR = \
          $(SRC)/dev/flang/fuir/FUIR.java \

JAVA_FILES_OPT = \
          $(SRC)/dev/flang/opt/Optimizer.java \

JAVA_FILES_BE_INTERPRETER = \
          $(SRC)/dev/flang/be/interpreter/Callable.java \
          $(SRC)/dev/flang/be/interpreter/ChoiceIdAsRef.java \
          $(SRC)/dev/flang/be/interpreter/Instance.java \
          $(SRC)/dev/flang/be/interpreter/Interpreter.java \
          $(SRC)/dev/flang/be/interpreter/JavaInterface.java \
          $(SRC)/dev/flang/be/interpreter/Layout.java \
          $(SRC)/dev/flang/be/interpreter/LValue.java \
          $(SRC)/dev/flang/be/interpreter/Intrinsics.java \
          $(SRC)/dev/flang/be/interpreter/Value.java \
          $(SRC)/dev/flang/be/interpreter/boolValue.java \
          $(SRC)/dev/flang/be/interpreter/i32Value.java \
          $(SRC)/dev/flang/be/interpreter/i64Value.java \
          $(SRC)/dev/flang/be/interpreter/u32Value.java \
          $(SRC)/dev/flang/be/interpreter/u64Value.java \

JAVA_FILES_BE_C = \
          $(SRC)/dev/flang/be/c/C.java \
          $(SRC)/dev/flang/be/c/CConstants.java \
          $(SRC)/dev/flang/be/c/CExpr.java \
          $(SRC)/dev/flang/be/c/CFile.java \
          $(SRC)/dev/flang/be/c/CIdent.java \
          $(SRC)/dev/flang/be/c/CNames.java \
          $(SRC)/dev/flang/be/c/COptions.java \
          $(SRC)/dev/flang/be/c/CStmnt.java \
          $(SRC)/dev/flang/be/c/CTypes.java \
          $(SRC)/dev/flang/be/c/Intrinsics.java \

JAVA_FILES_TOOLS = \
          $(SRC)/dev/flang/tools/Fuzion.java \
          $(SRC)/dev/flang/tools/Pretty.java \

CLASS_FILES_UTIL           = $(CLASSES_DIR)/dev/flang/util/__marker_for_make__
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

FUZION_EBNF = $(BUILD_DIR)/fuzion.ebnf

.PHONY: all
all: $(BUILD_DIR)/bin/fz $(BUILD_DIR)/tests

# phony target to compile all java sources
.PHONY: javac
javac: $(CLASS_FILES_TOOLS)

$(FUZION_EBNF): $(SRC)/dev/flang/parser/Parser.java
	mkdir -p $(@D)
	which pcregrep && pcregrep -M "^[a-zA-Z0-9]+[ ]*:(\n|.)*?;" $^ >$@ || echo "*** need pcregrep tool installed" >$@

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

$(BUILD_DIR)/lib: $(FZ_SRC)/lib
	mkdir -p $(@D)
	cp -rf $^ $@

$(BUILD_DIR)/bin/fz: $(FZ_SRC)/bin/fz $(CLASS_FILES_TOOLS) $(BUILD_DIR)/lib
	mkdir -p $(@D)
	cp -rf $(FZ_SRC)/bin/fz $@
	chmod +x $@

$(BUILD_DIR)/tests: $(FZ_SRC)/tests
	mkdir -p $(@D)
	cp -rf $^ $@

# phony target to run Fuzion tests and report number of failures
.PHONY: run_tests
run_tests: $(BUILD_DIR)/bin/fz $(BUILD_DIR)/tests
	rm -rf $(BUILD_DIR)/run_tests.results
	for test in $(shell echo $(BUILD_DIR)/tests/*); do \
	  FUZION="$(FUZIONX)" make -e -C >$$test/out.txt $$test 2>/dev/null && (echo -n "." && echo "$$test: ok" >>$(BUILD_DIR)/run_tests.results) || (echo -n "#"; echo "$$test: failed" >>$(BUILD_DIR)/run_tests.results); \
	done
	echo `cat $(BUILD_DIR)/run_tests.results | grep ok$$ | wc -l`/`ls $(BUILD_DIR)/tests | wc -l` tests passed, `cat $(BUILD_DIR)/run_tests.results | grep failed$$ | wc -l` tests failed; \
	cat $(BUILD_DIR)/run_tests.results | grep failed$$

.PHONY: clean
clean:
	rm -rf $(BUILD_DIR)
	find $(FZ_SRC) -name "*~" -exec rm {} \;
