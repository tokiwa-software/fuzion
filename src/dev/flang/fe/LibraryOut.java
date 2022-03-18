/*

This file is part of the Fuzion language implementation.

The Fuzion language implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language implementation is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License along with The
Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.

*/

/*-----------------------------------------------------------------------
 *
 * Tokiwa Software GmbH, Germany
 *
 * Source of class LibraryOut
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import java.nio.file.Path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.TreeMap;

import dev.flang.ast.AbstractAssign;
import dev.flang.ast.AbstractBlock;
import dev.flang.ast.AbstractCurrent;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractMatch;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Block;
import dev.flang.ast.Box;
import dev.flang.ast.Call;
import dev.flang.ast.Check;
import dev.flang.ast.Constant;
import dev.flang.ast.Env;
import dev.flang.ast.Expr;
import dev.flang.ast.Feature;
import dev.flang.ast.FormalGenerics;
import dev.flang.ast.Generic;
import dev.flang.ast.If;
import dev.flang.ast.InlineArray;
import dev.flang.ast.Nop;
import dev.flang.ast.Stmnt;
import dev.flang.ast.Tag;
import dev.flang.ast.Types;
import dev.flang.ast.Unbox;
import dev.flang.ast.Universe;

import dev.flang.ir.IR;

import dev.flang.util.DataOut;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;


/**
 * LibraryOut creates data for a .fum/MIR file from a SourceModule
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class LibraryOut extends DataOut
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The underlying module we are saving as a library.
   */
  private final SourceModule _sourceModule;


  /**
   * The source code files in this module, indexed by their position.
   */
  private TreeMap<String, SourceFile> _sourceFiles = new TreeMap<>();


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor to write library for given SourceModule.
   */
  LibraryOut(SourceModule sm)
  {
    super();

    _sourceModule = sm;

  /*
   *   +---------------------------------------------------------------------------------+
   *   | Module File s                                                                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | byte[]        | MIR_FILE_MAGIC                                |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | InnerFeatures | inner Features                                |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | SourceFiles   | source code files                             |
   *   +--------+--------+---------------+-----------------------------------------------+
   */

    write(FuzionConstants.MIR_FILE_MAGIC);
    innerFeatures(sm._universe);
    sourceFiles();
    fixUps();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Collect the binary data for features declared within given feature.
   *
   * Data format for inner features:
   *
   *   +---------------------------------------------------------------------------------+
   *   | InnerFeatures                                                                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | sizeof(inner Features) == size                |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | Features      | inner Features                                |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
   * The count n is not stored explicitly, the list of inner Features ends after
   * size bytes.
   */
  void innerFeatures(AbstractFeature f)
  {
    var m = _sourceModule.declaredFeatures(f);
    if (m == null)
      {
        writeInt(0);
      }
    else
      {
        // the first inner features written out will be the formal arguments,
        // followed by the result field (iff f.hasResultField()), followed by
        // all other inner features in (alphabetical?) order.
        var innerFeatures = new List<AbstractFeature>();
        var added = new TreeSet<AbstractFeature>();
        for (var a : f.arguments())
          {
            innerFeatures.add(a);
            added.add(a);
          }
        if (f.hasResultField())
          {
            var r = f.resultField();
            innerFeatures.add(r);
            added.add(r);
          }
        if (f.hasOuterRef())
          {
            var or = f.outerRef();
            innerFeatures.add(or);
            added.add(or);
          }
        if (f.isChoice())
          {
            if (CHECKS) check
              (Errors.count() > 0 ||
               added.size() == 0 // a choice has no arguments, no result, no outer ref
               );
            var ct = f.choiceTag();
            innerFeatures.add(ct);
            added.add(ct);
          }
        for (var i : m.values())
          {
            if (!added.contains(i))
              {
                innerFeatures.add(i);
              }
          }

        var szPos = offset();
        writeInt(0);
        var innerPos = offset();

        // write the actual data
        features(innerFeatures);
        writeIntAt(szPos, offset() - innerPos);
      }
  }


  /**
   * Collect the binary data for a list of features.
   *
   *   +---------------------------------------------------------------------------------+
   *   | Features                                                                        |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | n      | Feature       | (inner) Features                              |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
   */
  void features(List<AbstractFeature> fs)
  {
    for (var df : fs)
      {
        if (df instanceof Feature dff)
          {
            feature(dff);
          }
      }
  }


  /**
   * Collect the binary data for given feature.
   *
   * Data format for a feature:
   *
   *   +---------------------------------------------------------------------------------+
   *   | Feature                                                                         |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | byte          | 000CTkkk  kkk = kind, T = has type parameters |
   *   |        |        |               |           C = is intrinsic constructor        |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | Name          | name                                          |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | arg count                                     |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | name id                                       |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | Pos           | source code position                          |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | outer feature index, 0 for outer()==universe  |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | T=1    | 1      | TypeArgs      | optional type arguments                       |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | hasRT  | 1      | Type          | optional result type,                         |
   *   |        |        |               | hasRT = !isConstructor && !isChoice           |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | inherits count i                              |
   *   | NYI!   |        |               |                                               |
   *   | !isFiel+--------+---------------+-----------------------------------------------+
   *   | d? !isI| i      | Code          | inherits calls                                |
   *   | ntrinsc|        |               |                                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | precondition count pre_n                      |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | pre_n  | Code          | precondition code                             |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | int           | postcondition count post_n                    |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | post_n | Code          | postcondition code                            |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | int           | invariant count inv_n                         |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | inv_n  | Code          | invariant code                                |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | redefines count r                             |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | r      | int           | feature offset of redefined feature           |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | isRou- | 1      | Code          | Feature code                                  |
   *   | tine   |        |               |                                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   |        | 1      | InnerFeatures | inner features of this feature                |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
   *   +---------------------------------------------------------------------------------+
   *   | TypeArgs                                                                        |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | num type ags n                                |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | bool          | isOpen                                        |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | n      | TypeArg       | type arguments                                |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
   *   +---------------------------------------------------------------------------------+
   *   | TypeArg                                                                         |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Name          | type arg name                                 |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | Type          | constraint                                    |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  void feature(Feature f)
  {
    _offsetsForFeature.put(f, offset());
    var ix = offset();
    var k =
      !f.isConstructor() ? f.kind().ordinal() :
      f.isThisRef()      ? FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_REF
                         : FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_VALUE;
    if (CHECKS) check
      (k >= 0,
       f.isConstructor() || k < FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_VALUE);
    if (CHECKS) check
      (Errors.count() > 0 || f.isRoutine() || f.isChoice() || f.isIntrinsic() || f.isAbstract() || f.generics() == FormalGenerics.NONE);
    if (f.generics() != FormalGenerics.NONE)
      {
        k = k | FuzionConstants.MIR_FILE_KIND_HAS_TYPE_PAREMETERS;
      }
    if (f.isIntrinsicConstructor())
      {
        k = k | FuzionConstants.MIR_FILE_KIND_IS_INTRINSIC_CONSTRUCTOR;
      }
    var n = f.featureName();
    write(k);
    var bn = n.baseName();
    if (_sourceModule._options._eraseInternalNamesInLib && bn.startsWith(FuzionConstants.INTERNAL_NAME_PREFIX))
      {
        bn = "";
      }
    writeName(bn);
    writeInt (n.argCount());  // NYI: use better integer encoding
    writeInt (n._id);         // NYI: id /= 0 only if argCount = 0, so join these two values.
    pos(f.pos());
    if (!f.outer().isUniverse())
      {
        writeOffset(f.outer());
      }
    else
      {
        writeInt(0);
      }
    if ((k & FuzionConstants.MIR_FILE_KIND_HAS_TYPE_PAREMETERS) != 0)
      {
        if (CHECKS) check
          (f.generics().list.size() > 0);
        writeInt(f.generics().list.size());
        writeBool(f.generics().isOpen());
        for (var g : f.generics().list)
          {
            _offsetsForGeneric.put(g, offset());
            writeName(g.name());
            type(g.constraint());
          }
      }
    if (CHECKS) check
      (f.arguments().size() == f.featureName().argCount());
    if (!f.isConstructor() && !f.isChoice())
      {
        type(f.resultType());
      }
    // NYI: Suppress output of inherits for fields, intrinsics, etc.?
    var i = f.inherits();
    writeInt(i.size());
    for (var p : i)
      {
        code(p, false);
      }
    writeInt(f.contract().req.size());
    for (var c : f.contract().req)
      {
        code(c.cond, false);
      }
    writeInt(f.contract().ens.size());
    for (var c : f.contract().ens)
      {
        code(c.cond, false);
      }
    writeInt(f.contract().inv.size());
    for (var c : f.contract().inv)
      {
        code(c.cond, false);
      }
    var r = f.redefines();
    writeInt(r.size());
    for(var rf : r)
      {
        writeOffset(rf);
      }
    if (f.isRoutine())
      {
        code(f.code());
      }
    innerFeatures(f);
  }


  /**
   * Collect the binary data for given type.
   *
   * Data format for a type:
   *
   *   +---------------------------------------------------------------------------------+
   *   | Type                                                                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | the kind of this type tk                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | tk==-4 | 1      | unit          | ADDRESS                                       |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | tk==-3 | 1      | unit          | type of universe                              |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | tk==-2 | 1      | int           | index of type                                 |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | tk==-1 | 1      | int           | index of generic argument                     |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | tk>=0  | 1      | int           | index of feature of type                      |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | bool          | isRef                                         |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | tk     | Type          | actual generics                               |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | Type          | outer type                                    |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  void type(AbstractType t)
  {
    var off = offset(t);
    if (off >= 0)
      {
        writeInt(-2);     // NYI: optimization: maybe write just one integer, e.g., -index-2
        writeInt(off);
      }
    else if (t == Types.t_ADDRESS)
      {
        writeInt(-4);
      }
    else if (t == Types.resolved.universe.thisType())
      {
        writeInt(-3);
      }
    else
      {
        addOffset(t, offset());
        if (t.isGenericArgument())
          {
            if (CHECKS) check
              (!t.isRef());
            writeInt(-1);
            writeOffset(t.genericArgument());
          }
        else
          {
            boolean makeRef = t.isRef() && !t.featureOfType().isThisRef();
            // there is no explicit value type at this phase:
            if (CHECKS) check
              (makeRef || t.isRef() == t.featureOfType().isThisRef());
            writeInt(t.generics().size());
            writeOffset(t.featureOfType());
            writeBool(makeRef);
            for (var gt : t.generics())
              {
                type(gt);
              }
            type(t.outer());
          }
      }
  }


  /**
   * Collect the binary data for given Code.
   *
   * Data format for a Code:
   *
   *   +---------------------------------------------------------------------------------+
   *   | Code                                                                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | sizeof(Expressions)                           |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | Expressions   | the actual code                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
   */
  void code(Expr code)
  {
    code(code, true);
  }
  void code(Expr code, boolean dumpResult)
  {
    var szPos = offset();
    writeInt(0);
    var codePos = offset();

    // write the actual code data
    expressions(code, dumpResult, null);
    writeIntAt(szPos, offset() - codePos);
  }


  /**
   * Collect the binary data for given Expressions.
   *
   * Data format for Expressions:
   *
   *   +---------------------------------------------------------------------------------+
   *   | Expressions                                                                     |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | n      | Expression    | the single expressions                        |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
   * @param s the statement to write
   */
  SourcePosition expressions(Stmnt s, SourcePosition lastPos)
  {
    return expressions(s, false, lastPos);
  }

  /**
   * Collect the binary data for given Expressions.
   *
   * Data format for Expression:
   *
   *   +---------------------------------------------------------------------------------+
   *   | Expression                                                                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | byte          | ExprKind k in bits 0..6,  hasPos in bit 7     |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | hasPos | 1      | int           | source position: index in this file's         |
   *   |        |        |               | SourceFiles section, 0 for builtIn pos        |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Add | 1      | Assign        | assignment                                    |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Unb | 1      | Unbox         | unbox expression                              |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Con | 1      | Constant      | constant                                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Cal | 1      | Call          | feature call                                  |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Mat | 1      | Match         | match statement                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Tag | 1      | Tag           | tag expression                                |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
   * @param s the statement to write
   *
   * @param dumpResult true to add a 'Pop' to ignore the result produced by s.
   */
  SourcePosition expressions(Stmnt s, boolean dumpResult, SourcePosition lastPos)
  {
    if (s instanceof AbstractAssign a)
      {
        lastPos = expressions(a._value, lastPos);
        lastPos = expressions(a._target, lastPos);
        lastPos = exprKindAndPos(IR.ExprKind.Assign, lastPos, s.pos());
  /*
   *   +---------------------------------------------------------------------------------+
   *   | Assign                                                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | assigned field index                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
        writeOffset(a._assignedField);
      }
    else if (s instanceof Unbox u)
      {
        lastPos = expressions(u.adr_, lastPos);
        lastPos = exprKindAndPos(IR.ExprKind.Unbox, lastPos, s.pos());
  /*
   *   +---------------------------------------------------------------------------------+
   *   | Unbox                                                                           |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Type          | result type                                   |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | bool          | needed flag (NYI: What is this? remove?)      |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
        type(u.type());
        write(u._needed ? 1 : 0);
      }
    else if (s instanceof Box b)
      {
        lastPos = expressions(b._value, lastPos);
        lastPos = exprKindAndPos(IR.ExprKind.Box, lastPos, s.pos());
      }
    else if (s instanceof AbstractBlock b)
      {
        int i = 0;
        for (var st : b.statements_)
          {
            i++;
            if (i < b.statements_.size())
              {
                lastPos = expressions(st, true, lastPos);
              }
            else
              {
                lastPos = expressions(st, dumpResult, lastPos);
                dumpResult = dumpResult || st instanceof Expr;
              }
          }
        if (!dumpResult)
          {
            write(IR.ExprKind.Unit.ordinal());
          }
      }
    else if (s instanceof Constant c)
      {
        lastPos = exprKindAndPos(IR.ExprKind.Const, lastPos, s.pos());
  /*
   *   +---------------------------------------------------------------------------------+
   *   | Constant                                                                        |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Type          | type of the constant                          |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | length        | data length of the constant                   |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | length | byte          | data of the constant                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
        type(c.type());
        var d = c.data();
        writeInt(d.length);
        write(d);
      }
    else if (s instanceof AbstractCurrent)
      {
        lastPos = exprKindAndPos(IR.ExprKind.Current, lastPos, s.pos());
      }
    else if (s instanceof If i)
      {
        lastPos = expressions(i.cond, lastPos);
        lastPos = exprKindAndPos(IR.ExprKind.Match, lastPos, s.pos());
        writeInt(2);
        writeInt(1);
        type(Types.resolved.f_TRUE.resultType());
        code(i.block);
        writeInt(1);
        type(Types.resolved.f_FALSE.resultType());
        if (i.elseBlock != null)
          {
            code(i.elseBlock);
          }
        else if (i.elseIf != null)
          {
            code(i.elseIf);
          }
        else
          {
            code(new Block(i.pos(), new List<>()));
          }
      }
    else if (s instanceof Call c)
      {
        lastPos = expressions(c.target, lastPos);
        for (var a : c._actuals)
          {
            lastPos = expressions(a, lastPos);
          }
        lastPos = exprKindAndPos(IR.ExprKind.Call, lastPos, s.pos());
  /*
   *   +---------------------------------------------------------------------------------+
   *   | Call                                                                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | called feature f index                        |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | Type          | result type (NYI: remove, redundant!)s        |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | hasOpen| 1      | int           | num actual args (TBD: this is redundant,      |
   *   | ArgList|        |               | should be possible to determine)              |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cf.gene| 1      | int           | num actual generics n                         |
   *   | rics.is|        |               |                                               |
   *   | Open   |        |               |                                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | n      | Type          | actual generics. if !hasOpen, n is            |
   *   |        |        |               | f.generics().list.size()                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cf.resu| 1      | int           | select                                        |
   *   | ltType(|        |               |                                               |
   *   | ).isOpe|        |               |                                               |
   *   | nGeneri|        |               |                                               |
   *   | c()    |        |               |                                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
        writeOffset(c.calledFeature());
        type(c.type());
        int n;
        var cf = c.calledFeature();
        if (cf.hasOpenGenericsArgList())
          {
            writeInt(c._actuals.size());
          }
        if (cf.generics().isOpen())
          {
            n = c.generics.size();
            writeInt(n);
          }
        else
          {
            n = cf.generics().list.size();
            if (CHECKS) check
              (c.generics.size() == n);
          }
        for (int i = 0; i < n; i++)
          {
            type(c.generics.get(i));
          }
        if (CHECKS) check
          (cf.resultType().isOpenGeneric() == (c.select() >= 0));
        if (cf.resultType().isOpenGeneric())
          {
            writeInt(c.select());
          }
        if (dumpResult)
          {
            write(IR.ExprKind.Pop.ordinal());
          }
      }
    else if (s instanceof AbstractMatch m)
      {
        lastPos = expressions(m.subject(), lastPos);
        lastPos = exprKindAndPos(IR.ExprKind.Match, lastPos, s.pos());
  /*
   *   +---------------------------------------------------------------------------------+
   *   | Match                                                                           |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | number of cases                               |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | n      | Case          | cases                                         |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
        var cs = m.cases();
        writeInt(cs.size());
        for (var c : cs)
          {
  /*
   *   +---------------------------------------------------------------------------------+
   *   | Case                                                                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | num types n                                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | n = -1 | 1      | int           | case field index                              |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | n >  0 | n      | Type          | case type                                     |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Code          | code for case                                 |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
            var f = c.field();
            if (f != null)
              {
                writeInt(-1);
                writeOffset(f);
              }
            else
              {
                var ts = c.types();
                if (CHECKS) check
                  (ts.size() > 0);
                writeInt(ts.size());
                for (var t : ts)
                  {
                    type(t);
                  }
              }
            code(c.code());
          }
      }
    else if (s instanceof Tag t)
      {
        lastPos = expressions(t._value, lastPos);
        lastPos = exprKindAndPos(IR.ExprKind.Tag, lastPos, s.pos());
  /*
   *   +---------------------------------------------------------------------------------+
   *   | Tag                                                                             |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Type          | resulting tagged union type                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
        type(t.type());
      }
    else if (s instanceof Env e)
      {
        lastPos = exprKindAndPos(IR.ExprKind.Env, lastPos, s.pos());
  /*
   *   +---------------------------------------------------------------------------------+
   *   | Env                                                                             |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Type          | The type of this env                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
        type(e.type());
      }
    else if (s instanceof Nop)
      {
      }
    else if (s instanceof Universe)
      {
        // Universe is ignored, a call with target clazz universe will get its
        // target implicitly.
        //
        // write(IR.ExprKind.Universe.ordinal());
      }
    else if (s instanceof InlineArray)
      {
        throw new Error("Cannot write Library code for "+s.getClass());
      }
    else if (s instanceof Check c)
      {
        // NYI: Check not supported yet
        //
        // l.add(s);
      }
    else
      {
        System.err.println("Missing handling of "+s.getClass()+" in LibraryOut.expressions");
      }
    return lastPos;
  }


  /**
   * Determine the filename from a source file.
   *
   * This replaced absolute paths that start with fuzionHome by a path relative
   * to $FUZION.
   */
  private String fileName(SourceFile sf)
  {
    var fhp = _sourceModule._options._fuzionHome;
    var sfp = sf._fileName;
    if (sfp.startsWith(fhp))
      {
        var sfr = fhp.relativize(sfp);
        sfp = FuzionConstants.SYMBOLIC_FUZION_HOME.resolve(sfr);
      }
    return sfp.toString();
  }


  /**
   * Write expression kind and (optional) source code position if it changed.
   *
   * @param k the expression kind
   *
   * @param lastPos the previous position that was written already
   *
   * @param newPos the new position that is to be written if it differs from
   * lastPos
   */
  SourcePosition exprKindAndPos(IR.ExprKind k, SourcePosition lastPos, SourcePosition newPos)
  {
    if (lastPos == null || lastPos.compareTo(newPos) != 0)
      {
        write(k.ordinal() | 0x80);
        pos(newPos);
      }
    else
      {
        write(k.ordinal());
      }
    return newPos;
  }


  /**
   * Write source code position
   *
   * @param post the position
   */
  void pos(SourcePosition pos)
  {
  /*
   *   +---------------------------------------------------------------------------------+
   *   | Pos                                                                             |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | position                                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
    if (!pos.isBuiltIn())
      {
        _fixUpsSourcePositions.add(pos);
        _fixUpsSourcePositionsAt.add(offset());
        var sf = pos._sourceFile;
        _sourceFiles.put(fileName(sf), sf);
      }
    writeInt(0);
  }


  /**
   * Collect the binary data for source files used in this module
   *
   *   +---------------------------------------------------------------------------------+
   *   | SourceFiles                                                                     |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | count n                                       |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | n      | SourceFile    | source file                                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
   *   +---------------------------------------------------------------------------------+
   *   | SourceFile                                                                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Name          | file name                                     |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | int           | size s                                        |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | s      | byte          | source file data                              |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
   */
  void sourceFiles()
  {
    writeInt(_sourceFiles.size());
    for (var e : _sourceFiles.entrySet())
      {
        var sf = e.getValue();
        var n = fileName(sf);
        writeName(n);
        writeInt(sf.byteLength());
        _sourceFilePositions.put(n, offset());
        write(sf.bytes());
      }
  }


  /*--------------------------  fixup handling  -------------------------*/


  /**
   * Generics that are referenced before being defined and hence need a fixup:
   */
  ArrayList<Generic> _fixUpsG = new ArrayList<>();


  /**
   * Positions of fixups for generics
   */
  ArrayList<Integer> _fixUpsGAt = new ArrayList<>();


  /**
   * Generic offsets in this file
   */
  Map<Generic, Integer> _offsetsForGeneric = new HashMap<>();


  /**
   * Features that are referenced before being defined and hence need a fixup:
   */
  ArrayList<AbstractFeature> _fixUpsF = new ArrayList<>();


  /**
   * Positions of fixups for features
   */
  ArrayList<Integer> _fixUpsFAt = new ArrayList<>();


  /**
   * Feature offsets in this file
   */
  Map<AbstractFeature, Integer> _offsetsForFeature = new TreeMap<>();


  /**
   * Type offsets in this file
   */
  Map<AbstractType, Integer> _offsetsForType = new TreeMap<>();


  /**
   * SourcePositions that need fixup.
   */
  ArrayList<SourcePosition> _fixUpsSourcePositions = new ArrayList<>();


  /**
   * offsets of SourcePositions that need fixup.
   */
  ArrayList<Integer> _fixUpsSourcePositionsAt = new ArrayList<>();


  /**
   * source file position offsets in this file.
   */
  Map<String, Integer> _sourceFilePositions = new TreeMap<>();


  /**
   * Write offset of given generic, create fixup if not known yet.
   */
  void writeOffset(Generic g)
  {
    var o = _offsetsForGeneric.get(g);
    var v = o == null ? -1 : (int) o;
    if (o == null)
      {
        _fixUpsG.add(g);
        _fixUpsGAt.add(offset());
      }
    writeInt(v);
  }


  /**
   * Write offset of given feature, create fixup if not known yet.
   */
  void writeOffset(AbstractFeature f)
  {
    int v;
    if (f.isUniverse())
      {
        v = 0;
      }
    else if (f == null)
      {
        v = -1;
      }
    else
      {
        var o = _offsetsForFeature.get(f);
        if (o == null)
          {
            _fixUpsF.add(f);
            _fixUpsFAt.add(offset());
            v = -1;
          }
        else
          {
            v = (int) o;
          }
      }
    writeInt(v);
  }


  /**
   * Perform fixups
   */
  private void fixUps()
  {
    for (var i = 0; i<_fixUpsG.size(); i++)
      {
        var g  = _fixUpsG  .get(i);
        var at = _fixUpsGAt.get(i);
        var o = _offsetsForGeneric.get(g);
        if (CHECKS) check
          (o != null);
        writeIntAt(at, o);
      }
    for (var i = 0; i<_fixUpsF.size(); i++)
      {
        var g  = _fixUpsF  .get(i);
        var at = _fixUpsFAt.get(i);
        var o = _offsetsForFeature.get(g);
        if (CHECKS) check
          (o != null);
        writeIntAt(at, o);
      }
    for (var i = 0; i<_fixUpsSourcePositions.size(); i++)
      {
        var p  = _fixUpsSourcePositions  .get(i);
        var at = _fixUpsSourcePositionsAt.get(i);
        var sf = p._sourceFile;
        var n = fileName(sf);
        var o = _sourceFilePositions.get(n) + p.bytePos();
        if (CHECKS) check
          (o > 0);
        writeIntAt(at, o);
      }
  }


  /**
   * Record offset as the offset of type t.
   *
   * @param t a type that was or will be written out
   *
   * @param offset of t in the offset in the .fum/MIR file
   */
  void addOffset(AbstractType t, int offset)
  {
    if (PRECONDITIONS) require
      (offset(t) == -1);

    _offsetsForType.put(t, offset);
  }


  /**
   * Get the offset that was previously recored for type t, or -1 if no offset
   * was record (i.e., t has not been written yet).
   */
  int offset(AbstractType t)
  {
    return _offsetsForType.getOrDefault(t, -1);
  }

}

/* end of file */
