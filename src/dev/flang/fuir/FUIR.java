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

import java.nio.charset.StandardCharsets;

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
import dev.flang.ast.Impl; // NYI: remove dependency
import dev.flang.ast.IntConst; // NYI: remove dependency
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
 * The FUIR contains the intermediate representation of fuzion applications.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class FUIR extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  private static final int CLAZZ_BASE   = 0x10000000;

  private static final int FEATURE_BASE = 0x20000000;

  private static final int CODE_BASE = 0x30000000;

  public enum ClazzKind
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
    Singleton,
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


  private int addClazz(Clazz cl)
  {
    if (PRECONDITIONS) require
                         (cl._type != Types.t_VOID,  // NYI: would be better to not create these dummy clazzes in the first place
                          cl._type != Types.t_ADDRESS);

    int result = _clazzIds.add(cl);
    _featureIds.add(cl.feature());
    return result;
  }


  int addClazzIfNotVOID(Clazz cl)
  {
    if (PRECONDITIONS) require
                         (cl._type != Types.t_ADDRESS);

    if (cl._type != Types.t_VOID)  // NYI: would be better to not create this dummy clazz in the first place
      {
        // NYI: Check Clazzes.isUsed(thizFeature, ocl)
        return addClazz(cl);
      }
    else
      {
        return -1;
      }
  }


  private void addClasses()
  {
    if (_clazzIds.size() == 0)
      {
        for (var cl : Clazzes.all())
          {
            if (cl._type != Types.t_VOID    &&  // NYI: would be better to not create this dummy clazz in the first place
                cl._type != Types.t_ADDRESS     // NYI: would be better to not create this dummy clazz in the first place
                )
              {
                int res = addClazz(cl);
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


  /**
   * Return the clazz of the field at given slot
   *
   * NYI: slots are an artifact from the interpreter, should not appear in the IR
   *
   * @param cl a clazz id
   *
   * @param i a slot index in cl
   *
   * @return the clazz id or -1 if no field at this slot or -2 if field type is
   * VOID, so no field is needed. -3 if field type is ADDRESS
   */
  public int clazzFieldSlotClazz(int cl, int i)
  {
    if (PRECONDITIONS)
      require
        (i >= 0 && i < clazzSize(cl));

    var cc = _clazzIds.get(cl);
    for (var e : cc.offsetForField_.entrySet())
      {
        if (e.getValue() == i)
          {
            var f = e.getKey();
            Clazz fcl = cc.actualResultClazz(f);
            if (fcl._type == Types.t_VOID)  // NYI: would be better to not create this dummy clazz in the first place
              {
                return -2;
              }
            else if (fcl._type == Types.t_ADDRESS)  // NYI: would be better to not create this dummy clazz in the first place
              {
                return -3;
              }
            else
              {
                return addClazz(fcl);
              }
          }
      }
    return -1;
  }


  /**
   * Return the field at slot i in the given clazz
   *
   * NYI: slots are an artifact from the interpreter, should not appear in the IR
   *
   * @param cl a clazz id
   *
   * @param i a slot index in cl
   *
   * @return the feature id or -1 if no field at this slot
   */
  public int clazzField(int cl, int i)
  {
    if (PRECONDITIONS)
      require
        (i >= 0 && i < clazzSize(cl));

    var cc = _clazzIds.get(cl);
    for (var e : cc.offsetForField_.entrySet())
      {
        if (e.getValue() == i)
          {
            var f = e.getKey();
            return _featureIds.add(f);
          }
      }
    return -1;
  }


  /**
   * Get the clazz of the result of calling a clazz
   *
   * @param cl a clazz id
   *
   * @return clazz id of cl's result or -1 if the result is a stateless value
   */
  public int clazzResultClazz(int cl)
  {
    var cc = _clazzIds.get(cl);
    var cf = cc.feature();
    var rcl =
      cf.returnType.isConstructorType() ? cc :
      cf.isField() && cf.isOuterRef()   ? cc._outer._outer : cc.actualClazz(cf.resultType());
    return addClazzIfNotVOID(rcl);
  }


  public ClazzKind clazzKind(int cl)
  {
    var ff = _clazzIds.get(cl).feature();
    switch (ff.impl.kind_)
      {
      case Routine    :
      case RoutineDef : return ClazzKind.Routine;
      case Field      :
      case FieldDef   :
      case FieldActual:
      case FieldInit  : return ClazzKind.Field;
      case Intrinsic  : return ClazzKind.Intrinsic;
      case Abstract   : return ClazzKind.Abstract;
      default: throw new Error ("Unexpected feature impl kind: "+ff.impl.kind_);
      }
  }

  public String clazzBaseName(int cl)
  {
    return _clazzIds.get(cl).feature().featureName().baseName();
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


  /**
   * Get the clazz id of the type of the given argument of clazz cl
   *
   * @param cl clazz id
   *
   * @parem arg argument number 0, 1, .. clazzArgCount(cl)-1
   *
   * @return feature id of the argument or -1 if no such feature exists (the
   * argument is unused).
   */
  public int clazzArgClazz(int cl, int arg)
  {
    // NYI: This does not handle open generic args such as in Function.call yet.
    var c = _clazzIds.get(cl);
    var a = c.feature().arguments.get(arg);
    var rc = c.actualClazz(a.resultType());
    return addClazzIfNotVOID(rc);
  }

  /**
   * Get the feature id of the given argument of clazz cl
   *
   * @param cl clazz id
   *
   * @parem arg argument number 0, 1, .. clazzArgCount(cl)-1
   *
   * @return feature id of the argument or -1 if no such feature exists (the
   * argument is unused).
   */
  public int clazzArg(int cl, int arg)
  {
    // NYI: This does not handle open generic args such as in Function.call yet.
    var c = _clazzIds.get(cl);
    var a = c.feature().arguments.get(arg);
    int f = _featureIds.get(a);
    if (f < FEATURE_BASE)
      {
        f = -1;  // NYI: Would be nicer to either include unused feature as well or to remove the argument altogether
      }
    return f;
  }


  /**
   * Get the offset of a field in an instance of given clazz.
   *
   * @param cl a clazz id
   *
   * @param f a feature id of a field in cl
   *
   * @return the offset of f in an instance of cl
   */
  public int clazzFieldOffset(int cl, int f)
  {
    var c = _clazzIds.get(cl);
    var ff = _featureIds.get(f);
    var off = c.offsetForField_.get(ff);
    return off;  // implicit unboxing, NullPointer in case field not found!
  }


  /**
   * is the given clazz a choice clazz
   *
   * @param cl a clazz id
   */
  public boolean clazzIsChoice(int cl)
  {
    return _clazzIds.get(cl).isChoice();
  }


  /**
   * Get the outer clazz of the given clazz.
   *
   * @param cl a clazz id
   *
   * @return clazz id of cl's outer clazz, -1 if cl is universe or a value-less
   * type.
   */
  public int clazzOuterClazz(int cl)
  {
    var o = _clazzIds.get(cl)._outer;
    return o == null ? -1 : _clazzIds.get(o);
  }


  /**
   * If a clazz's instance contains an outer ref field, return this field.
   *
   * @param cl a clazz id
   *
   * @return feature id of cl's outer ref field or -1 if no such field exists.
   */
  public int clazzOuterRef(int cl)
  {
    var cc = _clazzIds.get(cl);
    var ff = cc.feature();
    var or = ff.outerRefOrNull();
    return
      or == null                         ? -1 :
      cc.offsetForField_.get(or) == null ? -1 : _featureIds.add(or);
  }


  /**
   * Check if the outer ref within instance of cl is really a ref (true) or a
   * copy of the outer (immutable) value instance
   *
   * @param cl a clazz id
   *
   * @return true iff cl's outer ref is an address
   */
  public boolean clazzIsOuterRefAdrOfValue(int cl)
  {
    if (PRECONDITIONS) require
      (clazzOuterRef(cl) != -1);

    var cc = _clazzIds.get(cl);
    var ff = cc.feature();
    return ff.isOuterRefAdrOfValue();
  }


  /**
   * Check if a clazz is the standard lib i32.fz.
   *
   * @param cl a clazz id
   *
   * @return true iff cl is i32.fz.
   */
  public boolean clazzIsI32(int cl)
  {
    var cc = _clazzIds.get(cl);
    return cc == Clazzes.i32.getIfCreated();
  }


  /**
   * Check if a clazz is the standard lib bool.fz.
   *
   * @param cl a clazz id
   *
   * @return true iff cl is bool.fz.
   */
  public boolean clazzIsBool(int cl)
  {
    var cc = _clazzIds.get(cl);
    return cc == Clazzes.bool.getIfCreated();
  }


  /**
   * Check if a clazz is the standard lib TRUE.fz.
   *
   * @param cl a clazz id
   *
   * @return true iff cl is TRUE.fz.
   */
  public boolean clazzIsTRUE(int cl)
  {
    var cc = _clazzIds.get(cl);
    return cc == Clazzes.c_TRUE.getIfCreated();
  }


  /**
   * Check if a clazz is the standard lib FALSE.fz.
   *
   * @param cl a clazz id
   *
   * @return true iff cl is FALSE.fz.
   */
  public boolean clazzIsFALSE(int cl)
  {
    var cc = _clazzIds.get(cl);
    return cc == Clazzes.c_FALSE.getIfCreated();
  }


  // String representation of clazz, for debugging only
  public String clazzAsString(int cl)
  {
    return cl == -1
      ? "-- no clazz --"
      : _clazzIds.get(cl).toString();
  }


  /**
   * Are values of this clazz essentially void values?
   */
  public boolean clazzIsValueLess(int cl)
  {
    var cc = _clazzIds.get(cl);
    return cc.size() == 0 && !cc.isRef();
  }


  /**
   * Get the id of the result field of a given feature.
   *
   * @param cl a clazz id
   *
   * @return feature id of cl's result field or -1 if f has no result field or a
   * result field that contains no data
   */
  public int clazzResultField(int cl)
  {
    var ff = _clazzIds.get(cl).feature();
    var r = ff.resultField();
    return r == null // NYI: should also check if result type if void or empty
      ? -1
      : _featureIds.add(r);
  }


  /**
   * Get access to the code of a clazz of kind Routine
   *
   * @param cl a clazz id
   *
   * @return a code id referring to cl's code
   */
  public int clazzCode(int cl)
  {
    if (PRECONDITIONS) require
      (clazzKind(cl) == ClazzKind.Routine);

    var ff = _clazzIds.get(cl).feature();
    var cod = ff.impl.code_;
    List<Stmnt> code = toStack(cod);
    return _codeIds.add(code);
  }


  /**
   * Is the given field clazz a reference to an outer feature?
   *
   * @param cl a clazz id of kind Field
   *
   * @return true for automatically generated references to outer instance
   */
  public boolean clazzIsOuterRef(int cl)
  {
    return _clazzIds.get(cl).feature().isOuterRef();
  }


  /**
   * Get the id of clazz consstring
   *
   * @param the id of connststring or -1 if that clazz was not created.
   */
  public int clazz_conststring()
  {
    var cc = Clazzes.conststring.getIfCreated();
    return cc == null ? -1 : _clazzIds.get(cc);
  }


  /*------------------------  accessing fields  -----------------------*/


  public String fieldName(int f)
  {
    return _featureIds.get(f).featureName().baseName();
  }


  /*--------------------------  stack handling  -------------------------*/


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
        result = ExprKind.Singleton;
      }
    else
      {
        System.err.println("Stmnt not supported in FUIR.codeAt: "+e.getClass());
      }
    return result;
  }

  public int assignedField(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Assign);

    var a = (Assign) _codeIds.get(c).get(ix);
    return _featureIds.add(a.assignedField);
  }

  public int assignOuterClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Assign);

    var outerClazz = _clazzIds.get(cl);
    var a = (Assign) _codeIds.get(c).get(ix);
    var ocl = Clazzes.clazz(a.getOuter, outerClazz);
    return addClazzIfNotVOID(ocl);
  }

  public int assignValueClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Assign);

    var outerClazz = _clazzIds.get(cl);
    var a = (Assign) _codeIds.get(c).get(ix);
    var vcl = (Clazz) outerClazz.getRuntimeData(a.tid_ + 1);
    return _clazzIds.get(vcl);
  }

  public int assignClazzForField(int outerClazz, int field)
  {
    var ocl = _clazzIds.get(outerClazz);
    var f = _featureIds.get(field);
    var fclazz = ocl.clazzForField(f);
    check
      (fclazz._type != Types.t_VOID);  // VOID would result in two universes. NYI: Better do not create this clazz in the first place
    return addClazz(fclazz);
  }


  public String callDebugString(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call);

    var call = (Call) _codeIds.get(c).get(ix);
    return call.calledFeature().qualifiedName();
  }


  /**
   * Get the inner clazz for a non dynamic call
   *
   * @param cl index of clazz containing the call
   *
   * @param c code block containing the call
   *
   * @param ix index of the call
   *
   * @return the clazz that has to be called
   */
  public int callCalledClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call,
       !callIsDynamic(cl, c, ix));

    var outerClazz = _clazzIds.get(cl);
    var call = (Call) _codeIds.get(c).get(ix);
    var innerClazz = ((dev.flang.ir.BackendCallable)outerClazz.getRuntimeData(call.sid_)).inner();
    check
      (innerClazz._type != Types.t_VOID);  // VOID would result in two universes. NYI: Better do not create this clazz in the first place
    return addClazz(innerClazz);
  }


  /**
   * Get the possible inner clazzes for a dynamic call
   *
   * @param outerClazz the caller
   *
   * @param call the call
   *
   * @return the clazz that has to be called
   */
  private List<Clazz> callCalledClazzes(Clazz outerClazz, Call call)
  {
    if (PRECONDITIONS) require
      (call != null,
       outerClazz != null);

    var cf = call.calledFeature();
    var tclazz = ((dev.flang.ir.BackendCallable)outerClazz.getRuntimeData(call.sid_)).outer();
    var result = new List<Clazz>();
    for (var cl : Clazzes.all())  // NYI: Overkill, better check only sub-clazzes of tclazz
      {
        if (cl._type != Types.t_VOID    &&  // NYI: would be better to not create this dummy clazz in the first place
            cl._type != Types.t_ADDRESS     // NYI: would be better to not create this dummy clazz in the first place
            )
          {
            if (cl._dynamicBinding != null)
              {
                var in = cl._dynamicBinding.inner(cf);
                if (in != null && in.feature().impl.kind_ != Impl.Kind.Abstract)
                  {
                    if (in.toString().startsWith("hasInterval<i32>"))  // NYI: Remove, hasInterval<i32> should not be instantiated at all so it should not appear here...
                      {
                        System.err.println("***** ignoring target "+in);
                      }
                    else
                      {
                        result.add(in);
                      }
                  }
              }
          }
      }
    return result;
  }


  /**
   * Get the possible inner clazzes for a dynamic call
   *
   * @param cl index of clazz containing the call
   *
   * @param c code block containing the call
   *
   * @param ix index of the call
   *
   * @return the clazz that has to be called
   */
  public int[] callCalledClazzes(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call);

    var outerClazz = _clazzIds.get(cl);
    var call = (Call) _codeIds.get(c).get(ix);
    var innerClazzes = callCalledClazzes(outerClazz, call);
    var innerClazzIds = new int[innerClazzes.size()];
    for (var i = 0; i < innerClazzes.size(); i++)
      {
        check
          (innerClazzes.get(i)._type != Types.t_VOID);  // VOID would result in two universes. NYI: Better do not create this clazz in the first place

        innerClazzIds[i] = addClazz(innerClazzes.get(i));
        check(innerClazzIds[i] != -1);
      }
    return innerClazzIds;
  }


  public int callArgCount(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call);

    var call = (Call) _codeIds.get(c).get(ix);
    return call._actuals.size();
  }

  public boolean callIsDynamic(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call);

    var outerClazz = _clazzIds.get(cl);
    var call = (Call) _codeIds.get(c).get(ix);
    var outer = ((dev.flang.ir.BackendCallable)outerClazz.getRuntimeData(call.sid_)).outer();
    return call.isDynamic() && outer.isRef();
  }

  public int callTargetClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call);

    var call = (Call) _codeIds.get(c).get(ix);
    var outerClazz = _clazzIds.get(cl);
    var tclazz = ((dev.flang.ir.BackendCallable)outerClazz.getRuntimeData(call.sid_)).outer();
    return _clazzIds.get(tclazz);
  }


  public int callFieldOffset(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call);

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

  public byte[] strConst(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.strConst);

    var ic = (StrConst) _codeIds.get(c).get(ix);
    return ic.str.getBytes(StandardCharsets.UTF_8);
  }

}

/* end of file */
