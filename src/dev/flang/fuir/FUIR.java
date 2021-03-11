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

import java.util.TreeSet;

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
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.Map2Int;
import dev.flang.util.MapComparable2Int;
import dev.flang.util.SourcePosition;


/**
 * The FUIR contains the intermediate representation of fuzion applications.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class FUIR extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  private static final int CLAZZ_BASE   = 0x10000000;

  private static final int CODE_BASE = 0x30000000;

  public enum ClazzKind
  {
    Routine,
    Field,
    Intrinsic,
    Abstract,
    Choice
  }

  public enum ExprKind
  {
    AdrToValue,
    Assign,
    Box,
    Call,
    Current,
    boolConst,
    i32Const,
    u32Const,
    i64Const,
    u64Const,
    strConst,
    Match,
    WipeStack,
  }


  /**
   * Dummy Expr for WipeStack.  This is needed only temporily as long as we use
   * the AST instances instead of proper bytecodes.
   *
   * NYI: remove once bytecode instructions are here.
   */
  static final Expr WIPE_STACK = new IntConst(42);

  /*----------------------------  variables  ----------------------------*/


  /**
   * The main clazz.
   */
  final Clazz _main;


  final Map2Int<Clazz> _clazzIds = new MapComparable2Int(CLAZZ_BASE);
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
            if (cl._type != Types.t_ADDRESS     // NYI: would be better to not create this dummy clazz in the first place
                )
              {
                _clazzIds.add(cl);
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

  public int clazzNumFields(int cl)
  {
    var f = _clazzIds.get(cl).fields();
    return f == null ? -1 : f.length;
  }


  /**
   * Return the field #i in the given clazz
   *
   * @param cl a clazz id
   *
   * @param i the field number
   *
   * @return the clazz id of the field
   */
  public int clazzField(int cl, int i)
  {
    if (PRECONDITIONS)
      require
        (i >= 0 && i < clazzNumFields(cl));

    var cc = _clazzIds.get(cl);
    var fc = cc.fields()[i];
    return _clazzIds.get(fc);
  }


  /**
   * For a choice type, the number of entries to choose from.
   *
   * @param cl a clazz id
   *
   * @return -1 if cl is not a choice clazz, the number of choice entries
   * otherwise.  May be 0 for the void choice.
   */
  public int clazzNumChoices(int cl)
  {
    var cgs = _clazzIds.get(cl).choiceGenerics();
    return cgs == null ? -1 : cgs.size();
  }


  /**
   * Return the choice #i in the given choice clazz
   *
   * @param cl a clazz id
   *
   * @param i the choice number
   *
   * @return the clazz id of the choice type
   */
  public int clazzChoice(int cl, int i)
  {
    if (PRECONDITIONS)
      require
        (i >= 0 && i < clazzNumChoices(cl));

    var cc = _clazzIds.get(cl);
    var cg = cc.choiceGenerics().get(i);
    return _clazzIds.get(cg);
  }


  /**
   * Is this a choice type with some elements of ref type?
   *
   * @param cl a clazz id
   *
   * @return true iff cl is a choice with at least one ref element
   */
  public boolean clazzIsChoiceWithRefs(int cl)
  {
    var cc = _clazzIds.get(cl);
    return cc.isChoiceWithRefs();
  }


  /**
   * Is this a choice type with all elements of ref type?
   *
   * @param cl a clazz id
   *
   * @return true iff cl is a choice with only ref or unit/void elements
   */
  public boolean clazzIsChoiceOfOnlyRefs(int cl)
  {
    var cc = _clazzIds.get(cl);
    return cc.isChoiceOfOnlyRefs();
  }


  /**
   * Check if is field does not store the value directly, but a pointer to the value.
   *
   * @param fcl a clazz id of the field
   *
   * @return true iff the field is an outer ref field that holds an address of
   * an outer value, false for normal fields our outer ref fields that store the
   * outer ref or value directly.
   */
  public boolean clazzFieldIsAdrOfValue(int fcl)
  {
    var fc = _clazzIds.get(fcl);
    var f = fc.feature();
    return f.isOuterRef() &&
      !fc._outer._outer.isRef() &&
      !fc._outer._outer.feature().isBuiltInPrimitive();
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
    return _clazzIds.get(cc.resultClazz());
  }


  public ClazzKind clazzKind(int cl)
  {
    var cc = _clazzIds.get(cl);
    if (cc.isChoice())
      {
        return ClazzKind.Choice;
      }
    else
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
  }

  public String clazzBaseName(int cl)
  {
    var cc = _clazzIds.get(cl);
    var res = cc.feature().featureName().baseName();
    if (!cc._type._generics.isEmpty())
      {
        res = res + cc._type._generics.toString("<",",",">");
      }
    return res;
  }


  /**
   * The intrinsic names is the original qualified name of the intrinsic feature
   * ignoring any inheritance into new clazzes.
   *
   * @param cl an intrinsic
   *
   * @return its intrinsic name, e.g. 'Array.getel' instead of
   * 'conststring.getel'
   */
  public String clazzIntrinsicName(int cl)
  {
    if (PRECONDITIONS) require
      (clazzKind(cl) == ClazzKind.Intrinsic);

    var cc = _clazzIds.get(cl);
    return cc.feature().qualifiedName();
  }

  public boolean clazzIsRef(int cl)
  {
    return _clazzIds.get(cl).isRef();
  }


  /**
   * For a reference clazz, obtain the corresponding value clazz.
   *
   * @param cl a clazz id
   *
   * @return clazz id  of corresponding value clazz.
   */
  public int clazzAsValue(int cl)
  {
    var cc = _clazzIds.get(cl);
    return _clazzIds.get(cc.asValue());
  }


  /**
   * Get the number of arguments required for a call to this clazz.
   *
   * @param cl clazz id
   *
   * @return number of arguments expected by cl, 0 if none or if clazz cl can
   * not be called (is a choice type)
   */
  public int clazzArgCount(int cl)
  {
    var cc = _clazzIds.get(cl);
    return cc.argumentFields().length;
  }


  /**
   * Get the clazz id of the type of the given argument of clazz cl
   *
   * @param cl clazz id
   *
   * @parem arg argument number 0, 1, .. clazzArgCount(cl)-1
   *
   * @return clazz id of the argument or -1 if no such feature exists (the
   * argument is unused).
   */
  public int clazzArgClazz(int cl, int arg)
  {
    if (PRECONDITIONS) require
      (arg >= 0,
       arg < clazzArgCount(cl));

    var c = _clazzIds.get(cl);
    var rc = c.argumentFields()[arg].resultClazz(); // or .fieldClazz()?
    return _clazzIds.get(rc);
  }


  /**
   * Get the clazz id of the given argument of clazz cl
   *
   * @param cl clazz id
   *
   * @parem arg argument number 0, 1, .. clazzArgCount(cl)-1
   *
   * @return clazz id of the argument or -1 if no such argument exists (the
   * argument is unused).
   */
  public int clazzArg(int cl, int arg)
  {
    var cc = _clazzIds.get(cl);
    var a = cc.argumentFields()[arg];
    return a == null ? -1 : _clazzIds.get(a);
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
   * Get the choice tag field of a choice clazz
   *
   * @param cl a clazz id
   *
   * @return clazz id of cl's choice tag or -1 if cl is not a choice or does not
   * need a choice tag.
   */
  public int clazzChoiceTag(int cl)
  {
    var cc = _clazzIds.get(cl);
    var ct = cc.choiceTag();
    return ct == null ? -1 : _clazzIds.get(ct);
  }


  /**
   * Get the choice tag id for a given value clazz in a choice clazz
   *
   * @param cl a clazz id of a choice clazz
   *
   * @param valuecl a clazz id of a static clazz of a value that is stored in an
   * instance of cl.
   *
   * @return id of the valuecl, correspods to the value to be stored in the tag.
   */
  public int clazzChoiceTag(int cl, int valuecl)
  {
    var cc = _clazzIds.get(cl);
    var vc = _clazzIds.get(valuecl);
    return cc.getChoiceTag(vc._type);
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
   * @return clazz id of cl's outer ref field or -1 if no such field exists.
   */
  public int clazzOuterRef(int cl)
  {
    var cc = _clazzIds.get(cl);
    var or = cc.outerRef();
    var cco = cc._outer;
    return
      or == null ||
      (cco.isUnitType()) ? -1
                         : _clazzIds.get(or);
  }


  /**
   * Get the id of clazz Object.
   *
   * @return clazz id of clazz Object
   */
  public int clazzObject()
  {
    return _clazzIds.get(Clazzes.object.get());
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
   * Check if a clazz is the standard lib i64.fz.
   *
   * @param cl a clazz id
   *
   * @return true iff cl is i64.fz.
   */
  public boolean clazzIsI64(int cl)
  {
    var cc = _clazzIds.get(cl);
    return cc == Clazzes.i64.getIfCreated();
  }


  /**
   * Check if a clazz is the standard lib u32.fz.
   *
   * @param cl a clazz id
   *
   * @return true iff cl is u32.fz.
   */
  public boolean clazzIsU32(int cl)
  {
    var cc = _clazzIds.get(cl);
    return cc == Clazzes.u32.getIfCreated();
  }


  /**
   * Check if a clazz is the standard lib u64.fz.
   *
   * @param cl a clazz id
   *
   * @return true iff cl is u64.fz.
   */
  public boolean clazzIsU64(int cl)
  {
    var cc = _clazzIds.get(cl);
    return cc == Clazzes.u64.getIfCreated();
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
   * Are values of this clazz all the same, so they are essentially C/Java void
   * values?
   */
  public boolean clazzIsUnitType(int cl)
  {
    var cc = _clazzIds.get(cl);
    return cc.isUnitType();
  }


  /**
   * Is this a void type, i.e., values of this clazz do not exist.
   */
  public boolean clazzIsVoidType(int cl)
  {
    var cc = _clazzIds.get(cl);
    return cc.isVoidType();
  }


  /**
   * Get the id of the result field of a given clazz.
   *
   * @param cl a clazz id
   *
   * @return id of cl's result field or -1 if f has no result field (NYI: or a
   * result field that contains no data)
   */
  public int clazzResultField(int cl)
  {
    var cc = _clazzIds.get(cl);
    var rf = cc.resultField();
    return rf == null ? -1 : _clazzIds.get(rf);
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


  /*--------------------------  stack handling  -------------------------*/


  List<Stmnt> toStack(Stmnt s)
  {
    List<Stmnt> result = new List<>();
    toStack(result, s);
    return result;
  }
  void toStack(List<Stmnt> l, Stmnt s) { toStack(l, s, false); }
  void toStack(List<Stmnt> l, Stmnt s, boolean dumpResult)
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
        // for (var st : b.statements_)
        for (int i=0; i<b.statements_.size(); i++)
          {
            var st = b.statements_.get(i);
            toStack(l, st, dumpResult || i < b.statements_.size()-1);
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
        if (dumpResult)
          {
            l.add(WIPE_STACK);
          }
      }
    else if (s instanceof Match)
      {
        var m = (Match) s;
        toStack(l, m.subject);
        l.add(m);
        for (var c : m.cases)
          {
            var caseCode = toStack(c.code);
            l.add(new IntConst(_codeIds.add(caseCode)));
          }
      }
    else if (s instanceof Nop)
      {
      }
    else if (s instanceof Singleton) // NYI: remove eventually
      {
        var si = (Singleton) s;
        // l.add(si);
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

    ExprKind result;
    var e = _codeIds.get(c).get(ix);
    if (e == WIPE_STACK) // Take care: must be first since WIPE_STACK is IntConst (for now)
      {
        result = ExprKind.WipeStack;
      }
    else if (e instanceof AdrToValue)
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
    else if (e instanceof If    ||
             e instanceof Match    )
      {
        result = ExprKind.Match;
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
    else
      {
        Errors.fatal(e.pos(), "Stmnt not supported in FUIR.codeAt", "Statement class: " + e.getClass());
        result = ExprKind.Current; // keep javac from complaining.
      }
    return result;
  }

  /**
   * Get the clazz field that is assigned to with the given assignment
   *
   * @parm cl a clazz id
   *
   * @param c code id
   *
   * @param ix index of assignment in code referred to by c
   *
   * @return the clazz id of the field that is assigned or -1 if that field
   * is unused, so the assignment is not needed.
   */
  public int assignedField(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Assign);

    var outerClazz = _clazzIds.get(cl);
    var a = (Assign) _codeIds.get(c).get(ix);
    var fc = (Clazz) outerClazz.getRuntimeData(a.tid_ + 2);
    return fc == null ? -1 : _clazzIds.get(fc);
  }

  public int assignOuterClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Assign);

    var outerClazz = _clazzIds.get(cl);
    var a = (Assign) _codeIds.get(c).get(ix);
    var ocl = (Clazz) outerClazz.getRuntimeData(a.tid_);
    return _clazzIds.get(ocl);
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
    return vcl == null ? -1 : _clazzIds.get(vcl);
  }

  public int boxValueClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Box);

    var outerClazz = _clazzIds.get(cl);
    var b = (Box) _codeIds.get(c).get(ix);
    Clazz vc = (Clazz) outerClazz.getRuntimeData(b._valAndRefClazzId);
    return _clazzIds.get(vc);
  }
  public int boxResultClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Box);

    var outerClazz = _clazzIds.get(cl);
    var b = (Box) _codeIds.get(c).get(ix);
    Clazz rc = (Clazz) outerClazz.getRuntimeData(b._valAndRefClazzId+1);
    return _clazzIds.get(rc);
  }


  /**
   * Get the inner clazz for a non dynamic call or the static clazz of a dynamic
   * call.
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
       codeAt(c, ix) == ExprKind.Call);

    var outerClazz = _clazzIds.get(cl);
    var call = (Call) _codeIds.get(c).get(ix);
    var innerClazz = ((dev.flang.ir.BackendCallable)outerClazz.getRuntimeData(call.sid_)).inner();
    return _clazzIds.get(innerClazz);
  }


  /**
   * Get the possible inner clazzes for a dynamic call
   *
   * NYI: This should not only return the list of inner clazzes, but a mapping
   * from outer clazzes to corresponding inner clazzes since different outer
   * clazzes could result in calls to the same inner clazz.
   *
   * @param outerClazz the caller
   *
   * @param call the call
   *
   * @return a list with an even number of element pairs with call target
   * clazzes at even indices followed by the corresponding inner clazz to be
   * called for this target.
   */
  private List<Clazz> callCalledClazzes(Clazz outerClazz, Call call)
  {
    if (PRECONDITIONS) require
      (call != null,
       outerClazz != null);

    var cf = call.calledFeature();
    var bc         = (dev.flang.ir.BackendCallable) outerClazz.getRuntimeData(call.sid_);
    var tclazz     = bc.outer();
    var innerClazz = bc.inner();
    var result = new List<Clazz>();
    for (var cl : Clazzes.all())  // NYI: Overkill, better check only sub-clazzes of tclazz
      {
        if (cl._type != Types.t_ADDRESS     // NYI: would be better to not create this dummy clazz in the first place
            )
          {
            if (cl._dynamicBinding != null)
              {
                var in = cl._dynamicBinding.inner(cf);
                if (in != null &&
                    in.feature().impl.kind_ != Impl.Kind.Abstract &&

                    // NYI: instead of just comparing the argument lenths, we
                    // should ensure to only return features from heir clazzes
                    // with equal generic parameters:
                    in.argumentFields().length == innerClazz.argumentFields().length)
                  {
                    result.add(cl);
                    result.add(in);
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
   * @return an array with an even number of element pairs with call target
   * clazzes at even indices followed by the corresponding inner clazz to be
   * called for this target.
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
        innerClazzIds[i] = _clazzIds.get(innerClazzes.get(i));
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


  public int fieldIndex(int field)
  {
    return _clazzIds.get(field).fieldIndex();
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


  /**
   * For a match statement, get the static clazz of the subject.
   *
   * @param cl index of clazz containing the match
   *
   * @param c code block containing the match
   *
   * @param ix index of the match
   *
   * @return clazz id of type of the subject
   */
  public int matchStaticSubject(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Match);

    var cc = _clazzIds.get(cl);
    var s = _codeIds.get(c).get(ix);
    Clazz ss = s instanceof If
      ? Clazzes.bool.get()
      : cc.getRuntimeClazz(((Match) s).runtimeClazzId_);
    return _clazzIds.get(ss);
  }


  /**
   * For a match statement, get the number of cases
   *
   * @param c code block containing the match
   *
   * @param ix index of the match
   *
   * @return the number of cases
   */
  public int matchCaseCount(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Match);

    var s = _codeIds.get(c).get(ix);
    int result = 2; // two cases for If
    if (s instanceof Match)
      {
        var match = (Match) s;
        result = match.cases.size();
      }
    return result;
  }


  /**
   * For a match statement, get the field of a given case
   *
   * @param cl index of clazz containing the match
   *
   * @param c code block containing the match
   *
   * @param ix index of the match
   *
   * @paramc cix index of the case in the match
   *
   * @return clazz id of field the value in this case is assigned to, -1 if this
   * case does not have a field or the field is unused.
   */
  public int matchCaseField(int cl, int c, int ix, int cix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Match,
       0 <= cix && cix <= matchCaseCount(c, ix));

    var cc = _clazzIds.get(cl);
    var s = _codeIds.get(c).get(ix);
    int result = -1; // no field for If
    if (s instanceof Match)
      {
        var match = (Match) s;
        var mc = match.cases.get(cix);
        var f = mc.field;
        var fc = f != null && Clazzes.isUsed(f, cc) ? cc.getRuntimeClazz(mc.runtimeClazzId_) : null;
        result = fc != null ? _clazzIds.get(fc) : -1;
      }
    return result;
  }



  /**
   * For a match statement, get the tags matched by a given case
   *
   * @param cl index of clazz containing the match
   *
   * @param c code block containing the match
   *
   * @param ix index of the match
   *
   * @paramc cix index of the case in the match
   *
   * @return array of tag numbers this case matches
   */
  public int[] matchCaseTags(int cl, int c, int ix, int cix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Match,
       0 <= cix && cix <= matchCaseCount(c, ix));

    var cc = _clazzIds.get(cl);
    var s = _codeIds.get(c).get(ix);
    int[] result;
    if (s instanceof If)
      {
        result = new int[] { cix == 0 ? 1 : 0 };
      }
    else
      {
        var match = (Match) s;
        var ss = cc.getRuntimeClazz(match.runtimeClazzId_);
        var mc = match.cases.get(cix);
        var f = mc.field;
        var fc = f != null && Clazzes.isUsed(f, cc) ? cc.getRuntimeClazz(mc.runtimeClazzId_) : null;
        int nt = f != null ? 1 : mc.types.size();
        var resultL = new List<Integer>();
        int tag = 0;
        for (var cg : ss.choiceGenerics())
          {
            for (int tix = 0; tix < nt; tix++)
              {
                var rc = fc != null ? fc.resultClazz() : cc.getRuntimeClazz(mc.runtimeClazzId_ + tix);
                if (rc.isAssignableFrom(cg))
                  {
                    resultL.add(tag);
                  }
              }
            tag++;
          }
        result = new int[resultL.size()];
        for (int i = 0; i < result.length; i++)
          {
            result[i] = resultL.get(i);
          }
      }
    return result;
  }

}

/* end of file */
