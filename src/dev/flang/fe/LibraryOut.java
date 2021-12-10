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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.TreeMap;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Assign;
import dev.flang.ast.Block;
import dev.flang.ast.Box;
import dev.flang.ast.Call;
import dev.flang.ast.Check;
import dev.flang.ast.Constant;
import dev.flang.ast.Current;
import dev.flang.ast.Expr;
import dev.flang.ast.Feature;
import dev.flang.ast.FormalGenerics;
import dev.flang.ast.Generic;
import dev.flang.ast.If;
import dev.flang.ast.InlineArray;
import dev.flang.ast.Match;
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


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor to write library for given SourceModule.
   */
  LibraryOut(SourceModule sm)
  {
    super();

    _sourceModule = sm;
    write(FuzionConstants.MIR_FILE_MAGIC);
    innerFeatures(sm._universe);
    fixUps();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Collect the binary data for features declared within given feature.
   *
   * Data format for inner features:
   *
   *   +-------------------------------------------------------------------------------+
   *   | InnerFeatures                                                                 |
   *   +--------+--------+-------------+-----------------------------------------------+
   *   | cond.  | repeat | type        | what                                          |
   *   +--------+--------+-------------+-----------------------------------------------+
   *   | true   | 1      | int         | sizeof(inner Features)                        |
   *   +        +--------+-------------+-----------------------------------------------+
   *   |        | 1      | Features    | inner Features                                |
   *   +--------+--------+-------------+-----------------------------------------------+
   *
   * The count n is not stored explicitly, the list of inner Features ends after
   * isz bytes.
   */
  void innerFeatures(Feature f)
  {
    var m = _sourceModule.declaredFeaturesOrNull(f);
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
            check
              (Errors.count() > 0 || added.isEmpty()); // a choice has no arguments, no result and no outer ref.
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
   *   +-------------------------------------------------------------------------------+
   *   | Features                                                                      |
   *   +--------+--------+-------------+-----------------------------------------------+
   *   | cond.  | repeat | type        | what                                          |
   *   +--------+--------+-------------+-----------------------------------------------+
   *   | true   | n      | Feature     | (inner) Features                              |
   *   +--------+--------+-------------+-----------------------------------------------+
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
   *   | true   | 1      | byte          | 0000Tkkk  kkk = kind, T = has type parameters |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | Name          | name                                          |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | arg count                                     |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | name id                                       |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | T=1    | 1      | TypeArgs      | optional type arguments                       |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | hasRT  | 1      | Type          | optional result type,                         |
   *   |        |        |               | hasRT = !isConstructor && !isChoice           |
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
    check
      (k >= 0,
       f.isConstructor() || k < FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_VALUE);
    check
      (Errors.count() > 0 || f.isRoutine() || f.isChoice() || f.isIntrinsic() || f.isAbstract() || f.generics() == FormalGenerics.NONE);
    if (f.generics() != FormalGenerics.NONE)
      {
        k = k | FuzionConstants.MIR_FILE_KIND_HAS_TYPE_PAREMETERS;
      }
    var n = f.featureName();
    write(k);
    writeName(n.baseName());  // NYI: internal names (outer refs, statement results) are too long and waste memory
    writeInt (n.argCount());  // NYI: use better integer encoding
    writeInt (n._id);         // NYI: id /= 0 only if argCount = 0, so join these two values.
    if ((k & FuzionConstants.MIR_FILE_KIND_HAS_TYPE_PAREMETERS) != 0)
      {
        check
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
    check
      (f.arguments().size() == f.featureName().argCount());
    if (!f.isConstructor() && !f.isChoice())
      {
        type(f.resultType());
      }
    if (f.isRoutine())
      {
        code(f.code());
      }
    innerFeatures(f);
    _sourceModule.registerOffset(f, ix);
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
    else if (t == Types.resolved.universe.thisType())
      {
        writeInt(-3);
      }
    else
      {
        addOffset(t, offset());
        if (t.isGenericArgument())
          {
            check
              (!t.isRef());
            writeInt(-1);
            writeOffset(t.genericArgument());
          }
        else
          {
            boolean makeRef = t.isRef() && !t.featureOfType().isThisRef();
            check // there is no explicit value type at this phase:
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
   *   +---------------------------------------------------------------------------------+
   *   | Expressions                                                                     |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | n      | Expression    | the single expressions                        |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
   *   +---------------------------------------------------------------------------------+
   *   | Expression                                                                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | byte          | ExprKind k                                    |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Add | 1      | Assign        | assignment                                    |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Con | 1      | Constant      | constant                                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  void code(Expr code)
  {
    var szPos = offset();
    writeInt(0);
    var codePos = offset();

    // write the actual code data
    expressions(code, true);
    writeIntAt(szPos, offset() - codePos);
  }


  /**
   * Collect the binary data for given Expressions.
   *
   * @param s the statement to write
   */
  void expressions(Stmnt s)
  {
    expressions(s, false);
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
   *   +---------------------------------------------------------------------------------+
   *   | Expression                                                                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | byte          | ExprKind k                                    |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Add | 1      | Assign        | assignment                                    |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Con | 1      | Constant      | constant                                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *
   * @param s the statement to write
   *
   * @param dumpResult true to add a 'Pop' to ignore the result produced by s.
   */
  void expressions(Stmnt s, boolean dumpResult)
  {
    if (s instanceof Assign a)
      {
        expressions(a._value);
        expressions(a._target);
        write(IR.ExprKind.Assign.ordinal());
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
        expressions(u.adr_);
        if (u._needed)
          {
            write(IR.ExprKind.Unbox.ordinal());
          }
      }
    else if (s instanceof Box b)
      {
        expressions(b._value);
        write(IR.ExprKind.Box.ordinal());
      }
    else if (s instanceof Block b)
      {
        int i = 0;
        for (var st : b.statements_)
          {
            i++;
            if (i < b.statements_.size())
              {
                expressions(st, true);
              }
            else
              {
                expressions(st, dumpResult);
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
        write(IR.ExprKind.Const.ordinal());
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
    else if (s instanceof Current)
      {
        write(IR.ExprKind.Current.ordinal());
      }
    else if (s instanceof If i)
      {
        expressions(i.cond);
        write(IR.ExprKind.Match.ordinal());
        writeInt(2);
        code(i.block);
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
        expressions(c.target);
        for (var a : c._actuals)
          {
            expressions(a);
          }
        write(IR.ExprKind.Call.ordinal());
            if (offset() == 23941) Thread.dumpStack();
  /*
   *   +---------------------------------------------------------------------------------+
   *   | Call                                                                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | called feature f index                        |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | hasOpen| 1      | int           | num actual args (TBD: this is redundant,      |
   *   | ArgList|        |               | should be possible to determine)              |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cf.gene| 1      | int           | num actual generics n                         |
   *   | rics.is|        |               |                                               |
   *   | Open   |        |               |                                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   |        | n      | Type          | actual generics. if !hasOpen, n is            |
   *   |        |        |               | f.generics().list.size()                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
        writeOffset(c.calledFeature());
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
            check
              (c.generics.size() == n);
          }
        for (int i = 0; i < n; i++)
          {
            type(c.generics.get(i));
          }
        if (dumpResult)
          {
            write(IR.ExprKind.Pop.ordinal());
          }
      }
    else if (s instanceof Match m)
      {
        expressions(m.subject);
        write(IR.ExprKind.Match.ordinal());
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
   *
   *   +---------------------------------------------------------------------------------+
   *   | Case                                                                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Code          | code for case                                 |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
        writeInt(m.cases.size());
        for (var c : m.cases)
          {
            code(c.code);
          }
      }
    else if (s instanceof Tag t)
      {
        expressions(t._value);
        write(IR.ExprKind.Tag.ordinal());
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
    var o = _offsetsForFeature.get(f);
    var v = o == null ? -1 : (int) o;
    if (o == null)
      {
        _fixUpsF.add(f);
        _fixUpsFAt.add(offset());
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
        check
          (o != null);
        writeIntAt(at, o);
      }
    for (var i = 0; i<_fixUpsF.size(); i++)
      {
        var g  = _fixUpsF  .get(i);
        var at = _fixUpsFAt.get(i);
        var o = _offsetsForFeature.get(g);
        check
          (o != null);
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
