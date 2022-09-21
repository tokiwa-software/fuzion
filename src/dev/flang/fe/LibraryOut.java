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

import java.nio.ByteBuffer;

import java.util.TreeSet;
import java.util.TreeMap;

import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;

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

import dev.flang.util.ANY;
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
class LibraryOut extends ANY
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


  /**
   * Data created for this library module, to be saved as .fum file.
   */
  private FixUps _data = new FixUps();


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor to write library for given SourceModule.
   */
  LibraryOut(SourceModule sm, String name)
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
   *   |        | 1      | Name          | module name                                   |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | u128          | module version                                |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | int           | number modules this module depends on n       |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | n      | ModuleRef     | reference to another module                   |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | InnerFeatures | inner Features                                |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | SourceFiles   | source code files                             |
   *   +--------+--------+---------------+-----------------------------------------------+
   */

    // first, write features just to collect referenced modules
    innerFeatures(sm._universe);
    var rm = _data.referencedModules();
    _data = null;

    // now that we know the referenced modules, we start over:
    var v = version();
    if (v != null)
      {
        _data = new FixUps();
        _data.write(FuzionConstants.MIR_FILE_MAGIC);
        _data.writeName(name);
        _data.write(v);
        _data.writeInt(rm.size());
        for (var m : rm)
          {
            moduleRef(m);
          }
        innerFeatures(sm._universe);
        sourceFiles();
        _data.fixUps(this);
        sm._options.verbosePrintln(2, "" +
                                   _data.featureCount() + " features " +
                                   _data.typeCount() + " types and " +
                                   _sourceFiles.size() + " source files includes in fum file.");
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create a version number of this module file.  Currently, the version is
   * just a cryptographically strong random number.
   */
  byte[] version()
  {
    var alg = "DRBG"; // or "SHA1PRNG"? NYI: Choose best algorithm here!
    try
      {
        var result = new byte[16];
        var r = SecureRandom.getInstance(alg);
        r.nextBytes(result);
        return result;
      }
    catch (NoSuchAlgorithmException e)
      {
        Errors.error("failed to produce secure random using algorithm '" + alg + "': " + e);
        return null;
      }
  }


  /**
   * Create a ByteBuffer instance from the data of this library, null if not
   * data available (due to an error).
   */
  ByteBuffer buffer()
  {
    return _data != null ? _data.buffer() : null;
  }


  /**
   * Collect the binary data for a ModuleRef
   *
   * Data format for module references:
   *
   *   +---------------------------------------------------------------------------------+
   *   | ModuleRef                                                                       |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Name          | module name                                   |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | u128          | module version                                |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  void moduleRef(LibraryModule m)
  {
    _data.writeName(m.name());
    _data.write(m.version());
  }


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
        _data.writeInt(0);
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
               added.size() == f.typeArguments().size() // a choice has no arguments, no result, no outer ref, but type args
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

        var szPos = _data.offset();
        _data.writeInt(0);
        var innerPos = _data.offset();

        // write the actual data
        features(innerFeatures);
        _data.writeIntAt(szPos, _data.offset() - innerPos);
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
   *   | true   | 1      | byte          | 0YCYkkkk  k = kind                            |
   *   |        |        |               |           Y = has Type feature (i.e. 'f.type')|
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
   *   | Y=1    | 1      | int           | type feature index                            |
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
   */
  void feature(Feature f)
  {
    _data.add(f);
    var k =
      !f.isConstructor() ? f.kind().ordinal() :
      f.isThisRef()      ? FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_REF
                         : FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_VALUE;
    if (CHECKS) check
      (k >= 0,
       f.isConstructor() || k < FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_VALUE);
    if (CHECKS) check
      (Errors.count() > 0 || f.isRoutine() || f.isChoice() || f.isIntrinsic() || f.isAbstract() || f.generics() == FormalGenerics.NONE);
    if (f.isIntrinsicConstructor())
      {
        k = k | FuzionConstants.MIR_FILE_KIND_IS_INTRINSIC_CONSTRUCTOR;
      }
    if (f.hasTypeFeature())
      {
        k = k | FuzionConstants.MIR_FILE_KIND_HAS_TYPE_FEATURE;
      }
    var n = f.featureName();
    _data.write(k);
    var bn = n.baseName();
    if (_sourceModule._options._eraseInternalNamesInLib && bn.startsWith(FuzionConstants.INTERNAL_NAME_PREFIX))
      {
        bn = "";
      }
    _data.writeName(bn);
    _data.writeInt (n.argCount());  // NYI: use better integer encoding
    _data.writeInt (n._id);         // NYI: id /= 0 only if argCount = 0, so join these two values.
    pos(f.pos());
    if (!f.outer().isUniverse())
      {
        _data.writeOffset(f.outer());
      }
    else
      {
        _data.writeInt(0);
      }
    if ((k & FuzionConstants.MIR_FILE_KIND_HAS_TYPE_FEATURE) != 0)
      {
        _data.writeOffset(f.typeFeature());
      }
    if (CHECKS) check
      (f.arguments().size() == f.featureName().argCount());
    if (!f.isConstructor() && !f.isChoice())
      {
        type(f.resultType());
      }
    // NYI: Suppress output of inherits for fields, intrinsics, etc.?
    var i = f.inherits();
    _data.writeInt(i.size());
    for (var p : i)
      {
        code(p, false);
      }
    _data.writeInt(f.contract().req.size());
    for (var c : f.contract().req)
      {
        code(c.cond, false);
      }
    _data.writeInt(f.contract().ens.size());
    for (var c : f.contract().ens)
      {
        code(c.cond, false);
      }
    var r = f.redefines();
    _data.writeInt(r.size());
    for(var rf : r)
      {
        _data.writeOffset(rf);
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
   *   | tk==-1 | 1      | int           | index of type parameter feature               |
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
    var off = _data.offset(t);
    if (off >= 0)
      {
        _data.writeInt(-2);     // NYI: optimization: maybe write just one integer, e.g., -index-2
        _data.writeInt(off);
      }
    else if (t == Types.t_ADDRESS)
      {
        _data.writeInt(-4);
      }
    else if (t == Types.resolved.universe.thisType())
      {
        _data.writeInt(-3);
      }
    else
      {
        _data.addOffset(t, _data.offset());
        if (t.isGenericArgument())
          {
            if (CHECKS) check
              (!t.isRef());
            _data.writeInt(-1);
            _data.writeOffset(t.genericArgument().typeParameter());
          }
        else
          {
            boolean makeRef = t.isRef() && !t.featureOfType().isThisRef();
            // there is no explicit value type at this phase:
            if (CHECKS) check
              (makeRef || t.isRef() == t.featureOfType().isThisRef());
            _data.writeInt(t.generics().size());
            _data.writeOffset(t.featureOfType());
            _data.writeBool(makeRef);
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
    var szPos = _data.offset();
    _data.writeInt(0);
    var codePos = _data.offset();

    // write the actual code data
    expressions(code, dumpResult, null);
    _data.writeIntAt(szPos, _data.offset() - codePos);
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
        _data.writeOffset(a._assignedField);
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
        _data.write(u._needed ? 1 : 0);
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
            _data.write(IR.ExprKind.Unit.ordinal());
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
        _data.writeInt(d.length);
        _data.write(d);
      }
    else if (s instanceof AbstractCurrent)
      {
        lastPos = exprKindAndPos(IR.ExprKind.Current, lastPos, s.pos());
      }
    else if (s instanceof If i)
      {
        lastPos = expressions(i.cond, lastPos);
        lastPos = exprKindAndPos(IR.ExprKind.Match, lastPos, s.pos());
        _data.writeInt(2);
        _data.writeInt(1);
        type(Types.resolved.f_TRUE.resultType());
        code(i.block);
        _data.writeInt(1);
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
        _data.writeOffset(c.calledFeature());
        type(c.type());
        int n;
        var cf = c.calledFeature();
        if (cf.hasOpenGenericsArgList())
          {
            _data.writeInt(c._actuals.size());
          }
        if (cf.generics().isOpen())
          {
            n = c._generics.size();
            _data.writeInt(n);
          }
        else
          {
            n = cf.generics().list.size();
            if (CHECKS) check
              (c._generics.size() == n);
          }
        for (int i = 0; i < n; i++)
          {
            type(c._generics.get(i));
          }
        if (CHECKS) check
          (cf.resultType().isOpenGeneric() == (c.select() >= 0));
        if (cf.resultType().isOpenGeneric())
          {
            _data.writeInt(c.select());
          }
        if (dumpResult)
          {
            _data.write(IR.ExprKind.Pop.ordinal());
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
        _data.writeInt(cs.size());
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
                _data.writeInt(-1);
                _data.writeOffset(f);
              }
            else
              {
                var ts = c.types();
                if (CHECKS) check
                  (ts.size() > 0);
                _data.writeInt(ts.size());
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
   * This replaces absolute paths that start with sourcePath by a path relative
   * to $FUZION.
   */
  String fileName(SourceFile sf)
  {
    var sp = _sourceModule._options.sourcePaths();
    var sd = sp.length == 1 ? sp[0] : null;
    var sfp = sf._fileName;
    if (sd != null && sfp.startsWith(sd))
      {
        var sfr = sd.relativize(sfp);
        sfp = FuzionConstants.SYMBOLIC_FUZION_HOME_LIB_SOURCE.resolve(sfr);
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
        _data.write(k.ordinal() | 0x80);
        pos(newPos);
      }
    else
      {
        _data.write(k.ordinal());
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
        _data.addSourcePosition(pos);
        var sf = pos._sourceFile;
        _sourceFiles.put(fileName(sf), sf);
      }
    _data.writeInt(0);
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
    _data.writeInt(_sourceFiles.size());
    for (var e : _sourceFiles.entrySet())
      {
        var sf = e.getValue();
        var n = fileName(sf);
        _data.writeName(n);
        _data.writeInt(sf.byteLength());
        _data.addSourceFilePosition(n);
        _data.write(sf.bytes());
      }
  }


}

/* end of file */
