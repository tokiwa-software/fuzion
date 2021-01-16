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
 * Tokiwa GmbH, Berlin
 *
 * Source of class FUIR
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir;

import dev.flang.ast.AdrToValue; // NYI: remove dependency
import dev.flang.ast.Assign; // NYI: remove dependency
import dev.flang.ast.Block; // NYI: remove dependency
import dev.flang.ast.BoolConst; // NYI: remove dependency
import dev.flang.ast.Box; // NYI: remove dependency
import dev.flang.ast.Call; // NYI: remove dependency
import dev.flang.ast.Current; // NYI: remove dependency
import dev.flang.ast.Expr; // NYI: remove dependency
import dev.flang.ast.Feature; // NYI: remove dependency
import dev.flang.ast.If; // NYI: remove dependency
import dev.flang.ast.IntConst; // NYI: remove dependency
import dev.flang.ast.Loop; // NYI: remove dependency
import dev.flang.ast.Match; // NYI: remove dependency
import dev.flang.ast.Nop; // NYI: remove dependency
import dev.flang.ast.Singleton; // NYI: remove dependency
import dev.flang.ast.Stmnt; // NYI: remove dependency
import dev.flang.ast.StrConst; // NYI: remove dependency
import dev.flang.ast.Types; // NYI: remove dependency

import dev.flang.ir.Clazz;
import dev.flang.ir.Clazzes;

import dev.flang.util.ANY;
import dev.flang.util.List;
import dev.flang.util.Map2Int;
import dev.flang.util.MapComparable2Int;


/**
 * The FUIR contains the intermediate representation of fusion applications.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class FUIR extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  private static final int CLAZZ_BASE   = 0x10000000;

  private static final int FEATURE_BASE = 0x20000000;

  private static final int CODE_BASE = 0x30000000;

  public enum FeatureKind
  {
    Routine,
    Field,
    Intrinsic,
    Abstract,
  }

  public enum ExprKind
  {
    NOP,
    AdrToValue,
    Assign,
    Box,
    Call,
    Current,
    If,
    boolConst,
    i32Const,
    u32Const,
    i64Const,
    u64Const,
    strConst,
    Match,
    Unknown,
  }

  /*----------------------------  variables  ----------------------------*/


  /**
   * The main feature
   */
  final Clazz _main;


  final Map2Int<Clazz> _clazzIds = new MapComparable2Int(CLAZZ_BASE);
  final Map2Int<Feature> _featureIds = new MapComparable2Int(FEATURE_BASE);
  final Map2Int<List<Stmnt>> _codeIds = new Map2Int(CODE_BASE);


  /*--------------------------  constructors  ---------------------------*/


  public FUIR(Clazz main)
  {
    _main = main;
  }


  /*-----------------------------  methods  -----------------------------*/


  public Clazz main()
  {
    return _main;
  }


  /*------------------------  accessing classes  ------------------------*/


  private void addClasses()
  {
    if (_clazzIds.size() == 0)
      {
        for (var cl : Clazzes.all())
          {
            if (cl._type != Types.t_VOID)  // NYI: would be better to not create this dummy clazz in the first place
              {
                _clazzIds.add(cl);
                _featureIds.add(cl.feature());
              }
          }
      }
  }

  public int firstClazz()
  {
    addClasses();
    return CLAZZ_BASE;
  }

  public int lastClazz()
  {
    addClasses();
    return CLAZZ_BASE + _clazzIds.size() - 1;
  }

  public int mainClazzId()
  {
    addClasses();
    return _clazzIds.get(_main);
  }

  public int clazzSize(int cl)
  {
    return _clazzIds.get(cl).size();
  }

  public int clazz2FeatureId(int cl)
  {
    return _featureIds.get(_clazzIds.get(cl).feature());
  }

  public boolean clazzIsRef(int cl)
  {
    return _clazzIds.get(cl).isRef();
  }

  public int clazzArgCount(int cl)
  {
    // NYI: This does not handle open generic args such as in Function.call yet.
    return _clazzIds.get(cl).feature().arguments.size();
  }


  /*------------------------  accessing features  -----------------------*/


  public String featureBaseName(int f)
  {
    return _featureIds.get(f).featureName().baseName();
  }

  public boolean featureIsUniverse(int f)
  {
    return _featureIds.get(f).isUniverse();
  }

  public int featureOuter(int f)
  {
    if (PRECONDITIONS) require
      (!featureIsUniverse(f));

    return _featureIds.add(_featureIds.get(f).outer());
  }

  public FeatureKind featureKind(int f)
  {
    var ff = _featureIds.get(f);
    switch (ff.impl.kind_)
      {
      case Routine:
      case RoutineDef: return FeatureKind.Routine;
      case Field:
      case FieldDef:
      case FieldInit: return FeatureKind.Field;
      case Intrinsic: return FeatureKind.Intrinsic;
      case Abstract: return FeatureKind.Abstract;
      default: throw new Error ("Unexpected feature impl kind: "+ff.impl.kind_);
      }
  }

  List<Stmnt> toStack(Stmnt s)
  {
    List<Stmnt> result = new List<>();
    toStack(result, s);
    return result;
  }
  void toStack(List<Stmnt> l, Stmnt s)
  {
    if (PRECONDITIONS) require
      (l != null,
       s != null);

    if (s instanceof Assign)
      {
        var a = (Assign) s;
        toStack(l, a.value);
        toStack(l, a.getOuter);
        l.add(a);
      }
    else if (s instanceof AdrToValue)
      {
        var a = (AdrToValue) s;
        toStack(l, a.adr_);
        l.add(a);
      }
    else if (s instanceof Box)
      {
        Box b = (Box) s;
        toStack(l, b._value);
        l.add(b);
      }
    else if (s instanceof Block)
      {
        Block b = (Block) s;
        for (var st : b.statements_)
          {
            toStack(l, st);
            if (st instanceof Expr)
              {
                // NYI: insert pop!
              }
          }
      }
    else if (s instanceof BoolConst)
      {
        l.add(s);
      }
    else if (s instanceof Current)
      {
        l.add(s);
      }
    else if (s instanceof If)
      {
        // if is converted to If, blockId, elseBlockId
        var i = (If) s;
        toStack(l, i.cond);
        l.add(i);
        List<Stmnt> block = toStack(i.block);
        l.add(new IntConst(_codeIds.add(block)));
        Stmnt elseBlock;
        if (i.elseBlock != null)
          {
            elseBlock = i.elseBlock;
          }
        else if (i.elseIf != null)
          {
            elseBlock = i.elseIf;
          }
        else
          {
            elseBlock = new Block(i.pos(), new List<>());
          }
        List<Stmnt> elseBlockCode = toStack(elseBlock);
        l.add(new IntConst(_codeIds.add(elseBlockCode)));
      }
    else if (s instanceof IntConst)
      {
        l.add(s);
      }
    else if (s instanceof Call)
      {
        var c = (Call) s;
        toStack(l, c.target);
        for (var a : c._actuals)
          {
            toStack(l, a);
          }
        l.add(c);
      }
    else if (s instanceof Loop)
      {
        var lo = (Loop) s;
        if (lo._prolog != null)
          {
            toStack(l, lo._prolog);
          }
        // NYI: rest...
      }
    else if (s instanceof Match)
      {
        var m = (Match) s;
        toStack(l, m.subject);
        for (var c : m.cases)
          {
            var caseCode = toStack(c.code);
            l.add(new IntConst(_codeIds.add(caseCode)));
          }
      }
    else if (s instanceof Nop)
      {
      }
    else if (s instanceof Singleton)
      {
        var si = (Singleton) s;
        l.add(si);
      }
    else if (s instanceof StrConst)
      {
        l.add(s);
      }
    else
      {
        System.err.println("Missing handling of "+s.getClass()+" in FUIR.toStack");
      }
  }

  public int featureCode(int f)
  {
    if (PRECONDITIONS) require
      (featureKind(f) == FeatureKind.Routine);

    var ff = _featureIds.get(f);
    var cod = ff.impl.code_;
    List<Stmnt> code = toStack(cod);
    return _codeIds.add(code);
  }


  /*--------------------------  accessing code  -------------------------*/


  public boolean withinCode(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0);

    var code = _codeIds.get(c);
    return ix < code.size();
  }

  public ExprKind codeAt(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0, withinCode(c, ix));

    ExprKind result = ExprKind.Unknown;
    var e = _codeIds.get(c).get(ix);
    if (e instanceof AdrToValue)
      {
        result = ExprKind.AdrToValue;
      }
    else if (e instanceof Assign)
      {
        result = ExprKind.Assign;
      }
    else if (e instanceof Box)
      {
        result = ExprKind.Box;
      }
    else if (e instanceof Call)
      {
        result = ExprKind.Call;
      }
    else if (e instanceof Current)
      {
        result = ExprKind.Current;
      }
    else if (e instanceof If)
      {
        result = ExprKind.If;
      }
    else if (e instanceof BoolConst)
      {
        result = ExprKind.boolConst;
      }
    else if (e instanceof IntConst)
      {
        var i = (IntConst) e;
        var t = i.type();
        if      (t == Types.resolved.t_i32) { result = ExprKind.i32Const; }
        else if (t == Types.resolved.t_u32) { result = ExprKind.u32Const; }
        else if (t == Types.resolved.t_i64) { result = ExprKind.i64Const; }
        else if (t == Types.resolved.t_u64) { result = ExprKind.u64Const; }
        else { throw new Error("Unexpected type for IntConst: " + t); }
      }
    else if (e instanceof StrConst)
      {
        result = ExprKind.strConst;
      }
    else if (e instanceof Match)
      {
        result = ExprKind.Match;
      }
    else if (e instanceof Singleton)
      {
        var s = (Singleton) e;
        result = ExprKind.NOP;
      }
    else
      {
        System.err.println("Stmnt not supported in FUIR.codeAt: "+e.getClass());
      }
    return result;
  }

  public int callCalledFeature(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call);

    var cl = (Call) _codeIds.get(c).get(ix);
    return _featureIds.add(cl.calledFeature());
  }

  public int callArgCount(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call);

    var cl = (Call) _codeIds.get(c).get(ix);
    return cl._actuals.size();
  }

  public boolean callIsDynamic(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call);

    var call = (Call) _codeIds.get(c).get(ix);
    return call.isDynamic();
  }

  public int callTargetClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call);

    var call = (Call) _codeIds.get(c).get(ix);
    var outerClazz = _clazzIds.get(cl);
    Clazz tclazz = Clazzes.clazz(call.target, outerClazz);
    return _clazzIds.add(tclazz);
  }

  public int callFieldOffset(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call,
       callIsDynamic(c, ix));

    var call = (Call) _codeIds.get(c).get(ix);
    var outerClazz = _clazzIds.get(cl);
    var f = call.calledFeature();
    var off = outerClazz.offsetForField_.get(f);
    if (off == null)
      {
        System.err.println("for call "+call+" at "+call.pos.show());
        System.err.println("*** could not find offset of field "+f.qualifiedName()+" within "+outerClazz);
      }
    return off;
  }

  public boolean boolConst(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.boolConst);

    var ic = (BoolConst) _codeIds.get(c).get(ix);
    return ic.b;
  }

  public int i32Const(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.i32Const);

    var ic = (IntConst) _codeIds.get(c).get(ix);
    return (int) ic.l;
  }

  public int u32Const(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.u32Const);

    var ic = (IntConst) _codeIds.get(c).get(ix);
    return (int) ic.l;
  }

  public long i64Const(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.i64Const);

    var ic = (IntConst) _codeIds.get(c).get(ix);
    return ic.l;
  }

  public long u64Const(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.u64Const);

    var ic = (IntConst) _codeIds.get(c).get(ix);
    return ic.l;
  }

}

/* end of file */
