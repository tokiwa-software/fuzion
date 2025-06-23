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
import dev.flang.ast.AbstractCall;
import dev.flang.ast.Constant;
import dev.flang.ast.AbstractCurrent;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractMatch;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Box;
import dev.flang.ast.Expr;
import dev.flang.ast.Feature;
import dev.flang.ast.FormalGenerics;
import dev.flang.ast.InlineArray;
import dev.flang.ast.Nop;
import dev.flang.ast.ResolvedType;
import dev.flang.ast.State;
import dev.flang.ast.Tag;
import dev.flang.ast.Types;
import dev.flang.ast.Universe;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import static dev.flang.util.FuzionConstants.MirExprKind;
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
   *   |        | 1      | int           | number of modules this module depends on n    |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | n      | ModuleRef     | reference to another module                   |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | int           | number of DeclFeatures entries m              |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | m      | DeclFeatures  | features declared in this module              |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | SourceFiles   | source code files                             |
   *   +--------+--------+---------------+-----------------------------------------------+
   */

    // first, write features just to collect referenced modules
    allDeclFeatures(sm);
    var rm = _data.referencedModules();
    _data = null;

    // now that we know the referenced modules, we start over:
    var v = version();
    if (v != null)
      {
        _data = new FixUps();
        _data.writeBytes(FuzionConstants.MIR_FILE_MAGIC);
        _data.writeString(name);
        _data.writeBytes(v);
        _data.writeInt(rm.size());
        for (var m : rm)
          {
            moduleRef(m);
          }
        allDeclFeatures(sm);
        sourceFiles();
        _data.fixUps(this);
        sm._options.verbosePrintln(2, "" +
                                   _data.featureCount() + " features " +
                                   _data.typeCount() + " types and " +
                                   _sourceFiles.size() + " source files included in fum file.");
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
   * <pre>
   *   +---------------------------------------------------------------------------------+
   *   | ModuleRef                                                                       |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Name          | module name                                   |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | u128          | module hash                                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   * </pre>
   */
  void moduleRef(LibraryModule m)
  {
    _data.writeString(m.name());
    _data.writeBytes(m.hash());
  }


  /**
   * Write all features declared in given source module into DeclFeatures
   * sections of a library file.  This includes the features declared in the
   * 'universe' as well as all features declared within outer features that come
   * from other module files.
   *
   * @param sm the source module we are compiling
   */
  void allDeclFeatures(SourceModule sm)
  {
    _data.writeInt(1 + sm._outerWithDeclarations.size());
    declFeatures(null);
    for (var o : sm._outerWithDeclarations)
      {
        declFeatures(o);
      }
  }


  /**
   * Collect the binary data for features declared within given outer feature.
   *
   * Data format for declFeatures:
   *
   * <pre>
   *   +---------------------------------------------------------------------------------+
   *   | DeclFeatures                                                                    |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | outer feature index, 0 for outer()==null      |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | InnerFeatures | inner Features                                |
   *   +--------+--------+---------------+-----------------------------------------------+
   * </pre>
   */
  void declFeatures(AbstractFeature outer)
  {
    featureIndexOrZeroForUniverse(outer);
    innerFeatures(outer);
  }


  /**
   * Write index of given feature f or '0' if 'f' is the universe.
   *
   * @param f a feature whose index is to be written.
   */
  void featureIndexOrZeroForUniverse(AbstractFeature f)
  {
    if (f == null)
      {
        _data.writeInt(0);
      }
    else
      {
        _data.writeOffset(f);
      }
  }


  /**
   * Collect the binary data for features declared within given feature.
   *
   * Data format for inner features:
   *
   * <pre>
   *   +---------------------------------------------------------------------------------+
   *   | InnerFeatures                                                                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | sizeof(inner Features) == size                |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | Features      | inner Features                                |
   *   +--------+--------+---------------+-----------------------------------------------+
   * </pre>
   * The count n is not stored explicitly, the list of inner Features ends after
   * size bytes.
   */
  void innerFeatures(AbstractFeature f)
  {
    if (f == null)
      {
        var szPos = _data.offset();
        _data.writeInt(0);
        var innerPos = _data.offset();

        // write the actual data
        feature(_sourceModule._universe);
        _data.writeIntAt(szPos, _data.offset() - innerPos);
      }
    else
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
  }


  /**
   * Collect the binary data for a list of features.
   *
   * <pre>
   *   +---------------------------------------------------------------------------------+
   *   | Features                                                                        |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | n      | Feature       | (inner) Features                              |
   *   +--------+--------+---------------+-----------------------------------------------+
   * </pre>
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
   * <pre>{@literal
   *   +---------------------------------------------------------------------------------+
   *   | Feature                                                                         |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | short         | 0000REvvvFCYkkkk                              |
   *   |        |        |               |           k = kind                            |
   *   |        |        |               |           Y = has Type feature (i.e. 'f.type')|
   *   |        |        |               |           C = is cotype                       |
   *   |        |        |               |           F = has 'fixed' modifier            |
   *   |        |        |               |           v = visibility                      |
   *   |        |        |               |           R = has precondition feature        |
   *   |        |        |               |           E = has postcondition feature       |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | Name          | name                                          |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | arg count                                     |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | name id                                       |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | Pos           | source code position                          |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | outer feature index, 0 for outer()==null      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | Y=1    | 1      | int           | cotype index                                  |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | C=1    | 1      | int           | cotypeorigin index                            |
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
   *   | R      | 1      | int           | feature offset of precondition feature        |
   *   |        |        +---------------+-----------------------------------------------+
   *   |        |        | int           | feature offset of pre bool feature            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | R &&   | 1      | int           | feature offset of pre and call feature        |
   *   | isConst|        |               |                                               |
   *   | ructor |        |               |                                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | E      | 1      | int           | feature offset of postcondition feature       |
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
   * }</pre>
   */
  void feature(Feature f)
  {
    if (PRECONDITIONS) require
      (f.state().atLeast(State.RESOLVED));

    _data.add(f);
    int k = f.visibility().ordinal() << 7;
    k = k | (!f.isConstructor() ? f.kind().ordinal() :
              f.isRef()         ? FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_REF
                                : FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_VALUE);
    if (CHECKS) check
      (k >= 0,
       Errors.any() || f.isRoutine() || f.isChoice() || f.isIntrinsic() || f.isAbstract() || f.isNative() || f.generics().list().isEmpty());
    if (f.hasCotype())
      {
        k = k | FuzionConstants.MIR_FILE_KIND_HAS_COTYPE;
      }
    if (f.isCotype())
      {
        k = k | FuzionConstants.MIR_FILE_KIND_IS_COTYPE;
      }
    if ((f.modifiers() & FuzionConstants.MODIFIER_FIXED) != 0)
      {
        k = k | FuzionConstants.MIR_FILE_KIND_IS_FIXED;
      }
    var preF   = f.preFeature();
    var preBF  = f.preBoolFeature();
    var preACF = f.preAndCallFeature();
    if (CHECKS) check
      ((preF == null) == (preBF == null),
        f.isConstructor() || (preF == null) == (preACF == null),
       !f.isConstructor() ||                    preACF == null );
    if (preF != null)
      {
        k = k | FuzionConstants.MIR_FILE_KIND_HAS_PRE_CONDITION_FEATURE;
      }
    var postF = f.postFeature();
    if (postF != null)
      {
        k = k | FuzionConstants.MIR_FILE_KIND_HAS_POST_CONDITION_FEATURE;
      }
    var n = f.featureName();
    _data.writeShort(k);
    var bn = n.baseName();
    if (_sourceModule._options._eraseInternalNamesInMod && n.isInternal())
      {
        bn = "";
      }
    _data.writeString(bn);
    var argCount = n.argCount() + f.freeTypesCount();
    _data.writeInt (argCount);      // NYI: use better integer encoding
    _data.writeInt (n._id);         // NYI: id /= 0 only if argCount = 0, so join these two values.
    pos(f.pos());
    featureIndexOrZeroForUniverse(f.outer());
    if (CHECKS) check
      (!(f.isCotype() && f.hasCotype()));
    if ((k & FuzionConstants.MIR_FILE_KIND_HAS_COTYPE) != 0)
      {
        _data.writeOffset(f.cotype());
      }
    if ((k & FuzionConstants.MIR_FILE_KIND_IS_COTYPE) != 0)
      {
        _data.writeOffset(f.cotypeOrigin());
      }
    if (CHECKS) check
      (f.arguments().size() == argCount);
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
    if (preF   != null) { _data.writeOffset(preF  ); }
    if (preBF  != null) { _data.writeOffset(preBF ); }
    if (preACF != null) { _data.writeOffset(preACF); }
    if (postF  != null) { _data.writeOffset(postF ); }

    var redefines = f.redefines();
    _data.writeInt(redefines.size());
    for(var rf : redefines)
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
   * <pre>
   *   +---------------------------------------------------------------------------------+
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
   *   |        | 1      | byte          | 0: isValue, 1: isRef, 2: isThisType           |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | tk     | Type          | actual generics                               |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | Type          | outer type                                    |
   *   +--------+--------+---------------+-----------------------------------------------+
   * </pre>
   */
  void type(AbstractType t)
  {
    if (PRECONDITIONS) require
      (t != null, t != Types.t_ERROR, t != Types.t_UNDEFINED, t instanceof ResolvedType);

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
    else if (!t.isGenericArgument() && t.feature().isUniverse())
      {
        _data.writeInt(-3);
      }
    else
      {
        _data.addOffset(t, _data.offset());
        if (t.isGenericArgument())
          {
            if (CHECKS) check
              (t.isValue());
            _data.writeInt(-1);
            _data.writeOffset(t.genericArgument());
          }
        else
          {
            _data.writeInt(t.generics().size());
            _data.writeOffset(t.feature());
            _data.writeByte(t.mode().num);
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
   * <pre>
   *   +---------------------------------------------------------------------------------+
   *   | Code                                                                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | sizeof(Expressions)                           |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | 1      | Expressions   | the actual code                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   * </pre>
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
   * <pre>
   *   +---------------------------------------------------------------------------------+
   *   | Expressions                                                                     |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | n      | Expression    | the single expressions                        |
   *   +--------+--------+---------------+-----------------------------------------------+
   * </pre>
   *
   * @param e the expression to write
   */
  SourcePosition expressions(Expr e, SourcePosition lastPos)
  {
    return expressions(e, false, lastPos);
  }

  /**
   * Collect the binary data for given Expressions.
   *
   * Data format for Expression:
   *
   * <pre>
   *   +---------------------------------------------------------------------------------+
   *   | Expression                                                                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | byte          | MirExprKind k in bits 0..6,  hasPos in bit 7  |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | hasPos | 1      | int           | source position: index in this file's         |
   *   |        |        |               | SourceFiles section, 0 for builtIn pos        |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Ass | 1      | Assign        | assignment                                    |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Con | 1      | Constant      | constant                                      |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Cal | 1      | Call          | feature call                                  |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Mat | 1      | Match         | match expression                              |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | k==Tag | 1      | Tag           | tag expression                                |
   *   +--------+--------+---------------+-----------------------------------------------+
   * </pre>
   *
   * @param e the expression to write
   *
   * @param dumpResult true to add a 'Pop' to ignore the result produced by s.
   */
  SourcePosition expressions(Expr e, boolean dumpResult, SourcePosition lastPos)
  {
    if (e instanceof AbstractAssign a)
      {
        var v = a._value;
        lastPos = expressions(v, lastPos);
        lastPos = expressions(a._target, lastPos);
        lastPos = exprKindAndPos(MirExprKind.Assign, lastPos, e.sourceRange());
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
    else if (e instanceof Box b)
      {
  /*
   *   +---------------------------------------------------------------------------------+
   *   | Box                                                                             |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Type          | box result type                               |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
        lastPos = expressions(b._value, lastPos);
        lastPos = exprKindAndPos(MirExprKind.Box, lastPos, e.sourceRange());
        type(e.type());
      }
    else if (e instanceof AbstractBlock b)
      {
        int i = 0;
        for (var expr : b._expressions)
          {
            i++;
            if (i < b._expressions.size())
              {
                lastPos = expressions(expr, true, lastPos);
              }
            else
              {
                lastPos = expressions(expr, dumpResult, lastPos);
                dumpResult = dumpResult || expr instanceof AbstractBlock || expr.producesResult();
              }
          }
        if (!dumpResult)
          {
            _data.writeByte(MirExprKind.Unit.ordinal());
          }
      }
    else if (e instanceof Constant c)
      {
        lastPos = exprKindAndPos(MirExprKind.Const, lastPos, e.sourceRange());
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
        _data.writeBytes(d);
      }
    else if (e instanceof AbstractCurrent ac)
      {
        if (!ac.type().feature().isUniverse())
          {
            lastPos = exprKindAndPos(MirExprKind.Current, lastPos, e.sourceRange());
          }
      }
    else if (e instanceof AbstractCall c)
      {
        var t = c.target();
        if (CHECKS)
          check(!t.type().isVoid());
        lastPos = expressions(t, lastPos);
        for (var a : c.actuals())
          {
            lastPos = expressions(a, lastPos);
          }
        lastPos = exprKindAndPos(MirExprKind.Call, lastPos, e.sourceRange());
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
            _data.writeInt(c.actuals().size());
          }
        if (cf.generics().isOpen())
          {
            n = c.actualTypeParameters().size();
            _data.writeInt(n);
          }
        else
          {
            n = cf.generics().list().size();
            if (CHECKS) check
              (c.actualTypeParameters().size() == n);
          }
        for (int i = 0; i < n; i++)
          {
            type(c.actualTypeParameters().get(i));
          }
        if (CHECKS) check
          (cf.resultType().isOpenGeneric() == (c.select() >= 0));
        if (cf.resultType().isOpenGeneric())
          {
            _data.writeInt(c.select());
          }
        if (dumpResult)
          {
            _data.writeByte(MirExprKind.Pop.ordinal());
          }
      }
    else if (e instanceof AbstractMatch m)
      {
        lastPos = expressions(m.subject(), lastPos);
        lastPos = exprKindAndPos(MirExprKind.Match, lastPos, e.sourceRange());
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
            var cc = c.code();
            code(cc);
          }
      }
    else if (e instanceof Tag t)
      {
        lastPos = expressions(t._value, lastPos);
        lastPos = exprKindAndPos(MirExprKind.Tag, lastPos, e.sourceRange());
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
    else if (e instanceof Nop)
      {
      }
    else if (e instanceof Universe)
      {
        // Universe is ignored, a call with target clazz universe will get its
        // target implicitly.
        //
        // write(MirExprKind.Universe.ordinal());
      }
    else if (e instanceof InlineArray ia)
      {
        lastPos = exprKindAndPos(MirExprKind.InlineArray, lastPos, e.sourceRange());
  /*
   *   +---------------------------------------------------------------------------------+
   *   | InlineArray                                                                     |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | size in fum                                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | Code          | Code                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | element count                                 |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | n      | Code          | element                                       |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
        var szPos = _data.offset();
        _data.writeInt(0);
        var codePos = _data.offset();

        // the code
        code(ia.code());
        // element count
        _data.writeInt(ia._elements.size());
        // each elements code
        ia._elements.forEach(x -> code(x));

        _data.writeIntAt(szPos, _data.offset() - codePos);
      }
    else
      {
        say_err("Missing handling of "+e.getClass()+" "+e.getClass().getSuperclass()+" in LibraryOut.expressions");
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
        sfp = sd.relativize(sfp);
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
  SourcePosition exprKindAndPos(MirExprKind k, SourcePosition lastPos, SourcePosition newPos)
  {
    if (lastPos == null || lastPos.compareTo(newPos) != 0)
      {
        _data.writeByte(k.ordinal() | 0x80);
        pos(newPos);
      }
    else
      {
        _data.writeByte(k.ordinal());
      }
    return newPos;
  }


  /**
   * Write source code position
   *
   * @param pos the position
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
    // start
    _data.writeInt(0);
    // end
    _data.writeInt(0);
  }


  /**
   * Collect the binary data for source files used in this module
   *
   * <pre>
   *   +---------------------------------------------------------------------------------+
   *   | SourceFiles                                                                     |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | count n                                       |
   *   +        +--------+---------------+-----------------------------------------------+
   *   |        | n      | SourceFile    | source file                                   |
   *   +--------+--------+---------------+-----------------------------------------------+
   * </pre>
   *
   * <pre>
   *   +---------------------------------------------------------------------------------+
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
   * </pre>
   *
   */
  void sourceFiles()
  {
    _data.writeInt(_sourceFiles.size());
    for (var e : _sourceFiles.entrySet())
      {
        var sf = e.getValue();
        var n = fileName(sf);
        _data.writeString(n);
        _data.writeInt(sf.byteLength());
        _data.addSourceFilePosition(n);
        _data.writeBytes(sf.bytes());
      }
  }


}

/* end of file */
