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
 * Source of class FUIR
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import dev.flang.ast.AbstractAssign;
import dev.flang.ast.AbstractBlock;
import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractCurrent;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractMatch;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Box;
import dev.flang.ast.Constant;
import dev.flang.ast.Context;
import dev.flang.ast.Env;
import dev.flang.ast.Expr;
import dev.flang.ast.InlineArray;
import dev.flang.ast.NumLiteral;
import dev.flang.ast.Tag;
import dev.flang.ast.Types;
import dev.flang.ast.Universe;

import dev.flang.fe.FrontEnd;
import dev.flang.fe.LibraryFeature;
import dev.flang.fe.LibraryModule;

import dev.flang.mir.MIR;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.FuzionOptions;
import dev.flang.util.IntArray;
import dev.flang.util.IntMap;
import dev.flang.util.List;
import dev.flang.util.Pair;
import dev.flang.util.SourcePosition;


/**
 * An implementation of FUIR that generates clazzes on demand from module files.
 * This is used to run DFA for monomorphization.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class GeneratingFUIR extends FUIR
{


  /*----------------------------  constants  ----------------------------*/


  static final int[] NO_CLAZZ_IDS = new int[0];


  /*----------------------------  constants  ----------------------------*/


  /**
   * property- or env-var-controlled flag to enable debug output whenever a
   * new clazz is created.
   *
   * To enable this, use fz with
   *
   *   dev_flang_fuir_GeneratingFUIR_SHOW_NEW_CLAZZES=true
   */
  static final boolean SHOW_NEW_CLAZZES = FuzionOptions.boolPropertyOrEnv("dev.flang.fuir.GeneratingFUIR.SHOW_NEW_CLAZZES");



  /*----------------------------  variables  ----------------------------*/


  private final FrontEnd _fe;

  private final TreeMap<Clazz, Clazz> _clazzesTM;


  /**
   * For each site, this gives the clazz id of the clazz that contains the code at that site.
   */
  private final IntArray _siteClazzes;


  /**
   * For each site s, the cached result of accessedClazz(s), box*Clazz(s), matchStaticSubject(), etc.
   */
  private final IntMap<Object> _siteClazzCache;


  /**
   * For each site s, the cached result of accessTargetClazz(s)
   */
  private final IntMap<Clazz> _accessedTarget;


  /**
   * For each site s, the actual results of lookup called for a dynamic site.
   * This is returned as the result of accessedClazzes().
   */
  final IntMap<int[]> _accessedClazzes;


  final LibraryModule _mainModule;


  private final int _mainClazz;
  private final int _universe;
  Clazz universe() { return id2clazz(_universe); }


  private final List<Clazz> _clazzes;

  private final List<List<AbstractCall>> _inh;


  private final Clazz[] _specialClazzes;

  private final Map<AbstractType, Clazz> _clazzesForTypes;

  boolean _lookupDone;

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create FUIR from given Clazz instance.
   */
  public GeneratingFUIR(FrontEnd fe, MIR mir)
  {
    _fe = fe;
    _lookupDone = false;
    _clazzesTM = new TreeMap<Clazz, Clazz>();
    _siteClazzes = new IntArray();
    _siteClazzCache = new IntMap<>();
    _accessedClazzes = new IntMap<>();
    _accessedTarget = new IntMap<>();
    _mainModule = fe.mainModule();
    _clazzes = new List<>();
    _specialClazzes = new Clazz[SpecialClazzes.values().length];
    _universe  = newClazz(null, mir.universe().selfType(), -1)._id;
    doesNeedCode(_universe);
    _mainClazz = newClazz(mir.main().selfType())._id;
    doesNeedCode(_mainClazz);
    _inh = new List<>();
    _clazzesForTypes = new TreeMap<>();
  }



  /**
   * Clone this FUIR such that modifications can be made by optimizers.  An heir
   * of FUIR can use this to redefine methods.
   *
   * @param original the original FUIR instance that we are cloning.
   */
  public GeneratingFUIR(GeneratingFUIR original)
  {
    super(original);
    _fe = original._fe;
    original._lookupDone = true;
    _lookupDone = true;
    _clazzesTM = original._clazzesTM;
    _siteClazzes = original._siteClazzes;
    _siteClazzCache = original._siteClazzCache;
    _accessedClazzes = original._accessedClazzes;
    _accessedTarget = original._accessedTarget;
    _mainModule = original._mainModule;
    _mainClazz = original._mainClazz;
    _universe = original._universe;
    _clazzes = original._clazzes;
    _specialClazzes = original._specialClazzes;
    _inh = original._inh;
    _clazzesForTypes = original._clazzesForTypes;
  }


  /*-----------------------------  methods  -----------------------------*/



  Clazz newClazz(AbstractType t)
  {
    var o = t.outer();
    return newClazz(o == null ? null : newClazz(o), t, -1);
  }
  Clazz newClazz(Clazz outerR, AbstractType actualType, int select)
  {
    Clazz result;

    var outer = outerR;
    Clazz o = outerR;
    var ao = actualType.feature().outer();
    while (o != null)
      {
        if (actualType.isRef() && ao != null && ao.inheritsFrom(o.feature()) && !outer.isRef())
          {
            outer = o;  // short-circuit outer relation if suitable outer was found
          }

        if (o._type.compareTo(actualType) == 0 &&
            // example where the following logic is relevant:
            // `((Unary i32 i32).compose i32).#fun`
            // here `compose i32` is not a constructor but a normal routine.
            // `compose i32` does not define a type. Thus it will not lead
            // to a recursive value type.
            actualType.feature().definesType() &&
            actualType != Types.t_ERROR &&
            // a recursive outer-relation

            // This is a little ugly: we do not want outer to be a value
            // type in the source code (see tests/inheritance_negative for
            // reasons why), but we are fine if outer is an 'artificial'
            // value type that is created by Clazz.asValue(), since these
            // will never be instantiated at runtime but are here only for
            // the convenience of the backend.
            //
            // So instead of testing !o.isRef() we use
            // !o._type.feature().isThisRef().
            !o._type.feature().isRef() &&
            !o._type.feature().isIntrinsic())
          {  // but a recursive chain of value types is not permitted

            // NYI: recursive chain of value types should be detected during
            // types checking phase!
            StringBuilder chain = new StringBuilder();
            chain.append("1: "+actualType+" at "+actualType.declarationPos().show()+"\n");
            int i = 2;
            Clazz c = outer;
            while (c._type.compareTo(actualType) != 0)
              {
                chain.append(""+i+": "+c._type+" at "+c._type.declarationPos().show()+"\n");
                c = c._outer;
                i++;
              }
            chain.append(""+i+": "+c._type+" at "+c._type.declarationPos().show()+"\n");
            Errors.error(actualType.declarationPos(),
                         "Recursive value type is not allowed",
                         "Value type " + actualType + " equals type of outer feature.\n"+
                         "The chain of outer types that lead to this recursion is:\n"+
                         chain + "\n" +
                         "To solve this, you could add a 'ref' after the arguments list at "+o._type.feature().pos().show());
          }
        o = o._outer;
      }

    var t = actualType;

    var cl = new Clazz(this, outerR, t, select, CLAZZ_BASE + _clazzes.size());
    var existing = _clazzesTM.get(cl);
    if (existing != null)
      {
        result = existing;
      }
    else
      {
        result = cl;
        _clazzes.add(cl);
        _clazzesTM.put(cl, cl);

        if (outerR != null)
          {
            outerR.addInner(result);
          }

        var s = SpecialClazzes.c_NOT_FOUND;
        if (cl.isRef() == cl.feature().isRef())  // not an boxed or explicit value clazz
          {
            // NYI: OPTIMIZATION: Avoid creating all feature qualified names!
            s = switch (cl.feature().qualifiedName())
              {
              case "Any"               -> SpecialClazzes.c_Any         ;
              case "i8"                -> SpecialClazzes.c_i8          ;
              case "i16"               -> SpecialClazzes.c_i16         ;
              case "i32"               -> SpecialClazzes.c_i32         ;
              case "i64"               -> SpecialClazzes.c_i64         ;
              case "u8"                -> SpecialClazzes.c_u8          ;
              case "u16"               -> SpecialClazzes.c_u16         ;
              case "u32"               -> SpecialClazzes.c_u32         ;
              case "u64"               -> SpecialClazzes.c_u64         ;
              case "f32"               -> SpecialClazzes.c_f32         ;
              case "f64"               -> SpecialClazzes.c_f64         ;
              case "unit"              -> SpecialClazzes.c_unit        ;
              case "void"              -> SpecialClazzes.c_void        ;
              case "bool"              -> SpecialClazzes.c_bool        ;
              case "true_"             -> SpecialClazzes.c_true_       ;
              case "false_"            -> SpecialClazzes.c_false_      ;
              case "Const_String"      -> SpecialClazzes.c_Const_String;
              case "String"            -> SpecialClazzes.c_String      ;
              case "error"             -> SpecialClazzes.c_error       ;
              case "fuzion"            -> SpecialClazzes.c_fuzion      ;
              case "fuzion.sys"        -> SpecialClazzes.c_fuzion_sys  ;
              case "fuzion.sys.Pointer"-> SpecialClazzes.c_sys_ptr     ;
              default                  -> SpecialClazzes.c_NOT_FOUND   ;
              };
            if (s != SpecialClazzes.c_NOT_FOUND)
              {
                _specialClazzes[s.ordinal()] = result;
              }
          }
        cl._specialClazzId = s;
        if (SHOW_NEW_CLAZZES) System.out.println("NEW CLAZZ "+cl);
        cl.init();

        if (cl.feature().isField() && outerR.isRef() && !outerR.isBoxed())
          { // NYI: CLEANUP: Duplicate clazzes for fields in corresponding value
            // instance.  This is currently needed for the C backend only since
            // that backend creates ref clazzes by embedding the underlying
            // value instance in the ref clazz' struct:
            var ignore = newClazz(outerR.asValue(), actualType, select);
          }

        result.registerAsHeir();
      }
    return result;
  }


  private Clazz id2clazz(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return _clazzes.get(cl - CLAZZ_BASE);
  }
  private Clazz clazz(int cl)
  {
    if (PRECONDITIONS) require
      (cl == NO_CLAZZ || cl >= CLAZZ_BASE,
       cl == NO_CLAZZ || cl < CLAZZ_BASE + _clazzes.size());

    return cl == NO_CLAZZ ? null : _clazzes.get(cl - CLAZZ_BASE);
  }


  private static List<AbstractCall> NO_INH = new List<>();
  static { NO_INH.freeze(); }


  /**
   * Determine the result clazz of an Expr.
   *
   * @param inh the inheritance chain that brought the code here (in case it is
   * an inlined inherits call).
   */
  Clazz clazz(Expr e, Clazz outerClazz, List<AbstractCall> inh)
  {
    Clazz result;
    if (e instanceof AbstractBlock b)
      {
        Expr resExpr = b.resultExpression();
        result = resExpr != null ? clazz(resExpr, outerClazz, inh)
                                 : id2clazz(clazz(SpecialClazzes.c_unit));
      }

    else if (e instanceof Box b)
      {
        result = outerClazz.handDown(b.type(), inh);
      }

    else if (e instanceof AbstractCall c)
      {
        var tclazz = clazz(c.target(), outerClazz, inh);
        if (!tclazz.isVoidType())
          {
            var at = outerClazz.handDownThroughInheritsCalls(c.actualTypeParameters(), inh);
            var typePars = outerClazz.actualGenerics(at);
            result = tclazz.lookupCall(c, typePars).resultClazz();
          }
        else
          {
            result = tclazz;
          }
      }

    else if (e instanceof AbstractCurrent c)
      {
        result = outerClazz;
      }

    else if (e instanceof AbstractMatch m)
      {
        result = outerClazz.handDown(m.type(), inh);
      }

    else if (e instanceof Universe)
      {
        result = id2clazz(_universe);
      }

    else if (e instanceof Constant c)
      {
        result = outerClazz.handDown(c.typeOfConstant(), inh);
      }

    else if (e instanceof Tag tg)
      {
        result = outerClazz.handDown(tg._taggedType, inh);
      }

    else if (e instanceof InlineArray ia)
      {
        result = outerClazz.handDown(ia.type(), inh);
      }

    else if (e instanceof Env v)
      {
        result = outerClazz.handDown(v.type(), inh);
      }

    else
      {
        if (!Errors.any())
          {
            throw new Error("" + e + " "+ e.getClass() + " should no longer exist at runtime");
          }

        result = error();
      }

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /*----------------  get clazz to be used in case of an error  -----------------*/


  Clazz error()
  {
    if (PRECONDITIONS) require
      (Errors.any());

    return _clazzes.get(clazz(SpecialClazzes.c_void));  // NYI: UNDER DEVELOPMENT: have a dedicated clazz for this?
  }


  /*----------------  methods to convert type to clazz  -----------------*/


  /**
   * clazz
   *
   * @return
   */
  Clazz type2clazz(AbstractType thiz)
  {
    if (PRECONDITIONS) require
      (Errors.any() || !thiz.dependsOnGenerics(),
       !thiz.isThisType());

    var result = _clazzesForTypes.get(thiz);
    if (result == null)
      {
        var ot = thiz.outer();
        var oc = ot != null ? type2clazz(ot) : null;
        result = newClazz(oc, thiz, -1);
        _clazzesForTypes.put(thiz, result);
      }

    if (POSTCONDITIONS) ensure
      (Errors.any() || thiz.isRef() == result._type.isRef());

    return result;
  }


  /*------------------------  accessing classes  ------------------------*/


  /**
   * The clazz ids form a contiguous range of integers. This method gives the
   * smallest clazz id.  Together with `lastClazz`, this permits iteration.
   *
   * @return a valid clazz id such that for all clazz ids id: result <= id.
   */
  @Override
  public int firstClazz()
  {
    return CLAZZ_BASE;
  }


  /**
   * The clazz ids form a contiguous range of integers. This method gives the
   * largest clazz id.  Together with `firstClazz`, this permits iteration.
   *
   * @return a valid clazz id such that for all clazz ids id: result >= id.
   */
  @Override
  public int lastClazz()
  {
    return CLAZZ_BASE + _clazzes.size() - 1;
  }


  /**
   * id of the main clazz.
   *
   * @return a valid clazz id
   */
  @Override
  public int mainClazzId()
  {
    return _mainClazz;
  }


  /**
   * Return the kind of this clazz ( Routine, Field, Intrinsic, Abstract, ...)
   */
  @Override
  public FeatureKind clazzKind(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return c.clazzKind();
  }


  /**
   * Return the base name of this clazz, i.e., the name excluding the outer
   * clazz' name and excluding the actual type parameters
   *
   * @return String like `"Set"` if `cl` corresponds to `container.Set u32`.
   */
  @Override
  public String clazzBaseName(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    var res = c.feature().featureName().baseName();
    res = res + c._type.generics()
      .toString(" ", " ", "", t -> t.asStringWrapped(false));
    return res;
  }



  /**
   * Get the clazz of the result of calling a clazz
   *
   * @param cl a clazz id, must not be Choice
   *
   * @return clazz id of cl's result
   */
  @Override
  public int clazzResultClazz(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).resultClazz()._id;
  }


  /**
   * The original qualified name of the feature this clazz was
   * created from, ignoring any inheritance into new clazzes.
   *
   * @param cl a clazz
   *
   * @return its original name, e.g. 'Array.getel' instead of
   * 'Const_String.getel'
   */
  @Override
  public String clazzOriginalName(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var cc = id2clazz(cl);
    return cc.feature().qualifiedName();
  }


  /**
   * String representation of clazz, for creation of unique type names.
   *
   * @param cl a clazz id.
   */
  @Override
  public String clazzAsString(int cl)
  {
    if (PRECONDITIONS) require
      (cl == NO_CLAZZ || cl >= CLAZZ_BASE,
       cl == NO_CLAZZ || cl < CLAZZ_BASE + _clazzes.size());

    return cl == NO_CLAZZ
      ? "-- no clazz --"
      : id2clazz(cl).asString(false);
  }


  /**
   * human readable String representation of clazz, for stack traces and debugging.
   *
   * @param cl a clazz id.
   */
  @Override
  public String clazzAsStringHuman(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return c.asString(true);
  }


  /**
   * Get a String representation of a given clazz including a list of arguments
   * and the result type. For debugging only, names might be ambiguous.
   *
   * @param cl a clazz id.
   */
  @Override
  public String clazzAsStringWithArgsAndResult(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var sb = new StringBuilder();
    sb.append(clazzAsString(cl))
      .append("(");
    var o = clazzOuterClazz(cl);
    if (o != -1)
      {
        sb.append("outer ")
          .append(clazzAsString(o));
      }
    for (var i = 0; i < clazzArgCount(cl); i++)
      {
        var ai = clazzArg(cl,i);
        sb.append(o != -1 || i > 0 ? ", " : "")
          .append(clazzBaseName(ai))
          .append(" ")
          .append(clazzAsString(clazzResultClazz(ai)));
      }
    sb.append(") ")
      .append(clazzAsString(clazzResultClazz(cl)));
    return sb.toString();
  }


  /**
   * Get the outer clazz of the given clazz.
   *
   * @param cl a clazz id
   *
   * @return clazz id of cl's outer clazz, -1 if cl is universe or a value-less
   * type.
   */
  @Override
  public int clazzOuterClazz(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    var o = c._outer;
    return o == null ? NO_CLAZZ : o._id;
  }


  /*------------------------  accessing fields  ------------------------*/


  /**
   * Number of value fields in clazz `cl`, including argument value fields,
   * inherited fields, artificial fields like outer refs.
   *
   * @param cl a clazz id
   *
   * @return number of value fields in `cl`
   */
  @Override
  public int clazzNumFields(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).fields().length;
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
  @Override
  public int clazzField(int cl, int i)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size(),
       0 <= i,
       i < clazzNumFields(cl));

    return id2clazz(cl).fields()[i]._id;
  }


  /**
   * Is the given field clazz a reference to an outer feature?
   *
   * @param cl a clazz id of kind Field
   *
   * @return true for automatically generated references to outer instance
   */
  @Override
  public boolean clazzIsOuterRef(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).feature().isOuterRef();
  }


  /**
   * Check if field does not store the value directly, but a pointer to the value.
   *
   * @param field a clazz id, not necessarily a field
   *
   * @return true iff the field is an outer ref field that holds an address of
   * an outer value, false for normal fields our outer ref fields that store the
   * outer ref or value directly.
   */
  @Override
  public boolean clazzFieldIsAdrOfValue(int field)
  {
    if (PRECONDITIONS) require
      (field >= CLAZZ_BASE,
       field < CLAZZ_BASE + _clazzes.size());

    var fc = id2clazz(field);
    var f = fc.feature();
    return f.isOuterRef() &&
      !fc.resultClazz().isRef() &&
      !fc.resultClazz().isUnitType() &&
      !fc.resultClazz().feature().isBuiltInPrimitive();
  }


  /**
   * NYI: CLEANUP: Remove? This seems to be used only for naming fields, maybe we could use clazzId2num(field) instead?
   */
  @Override
  public int fieldIndex(int field)
  {
    if (PRECONDITIONS) require
      (field >= CLAZZ_BASE,
       field < CLAZZ_BASE + _clazzes.size(),
       clazzKind(field) == FeatureKind.Field);

    return id2clazz(field).fieldIndex();
  }


  /*------------------------  accessing choices  -----------------------*/


  /**
   * is the given clazz a choice clazz
   *
   * @param cl a clazz id
   */
  @Override
  public boolean clazzIsChoice(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return c.isChoice();
  }


  /**
   * For a choice type, the number of entries to choose from.
   *
   * @param cl a clazz id
   *
   * @return -1 if cl is not a choice clazz, the number of choice entries
   * otherwise.  May be 0 for the void choice.
   */
  @Override
  public int clazzNumChoices(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return switch (c.feature().kind())
      {
      case Choice -> c.choiceGenerics().size();
      default     -> -1;
      };
  }


  /**
   * Return the choice #i in the given choice clazz
   *
   * @param cl a clazz id
   *
   * @param i the choice number
   *
   * @return the clazz id of the choice type, or void clazz if the clazz is
   * never instantiated and hence does not need to be taken care for.
   */
  @Override
  public int clazzChoice(int cl, int i)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size(),
       i >= 0 && i < clazzNumChoices(cl));

    var cc = id2clazz(cl);
    var cg = cc.choiceGenerics().get(i);
    var res = cg.isRef()          ||
              cg.isInstantiatedChoice() ? cg
                                        : id2clazz(clazz(SpecialClazzes.c_void));
    return res._id;
  }


  /**
   * Is this a choice type with some elements of ref type?
   *
   * NYI: CLEANUP: Used by C and interpreter backends only. Remove?
   *
   * @param cl a clazz id
   *
   * @return true iff cl is a choice with at least one ref element
   */
  @Override
  public boolean clazzIsChoiceWithRefs(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var cc = id2clazz(cl);
    return cc.isChoiceWithRefs();
  }


  /**
   * Is this a choice type with all elements of ref type?
   *
   * @param cl a clazz id
   *
   * @return true iff cl is a choice with only ref or unit/void elements
   */
  @Override
  public boolean clazzIsChoiceOfOnlyRefs(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var cc = id2clazz(cl);
    return cc.isChoiceOfOnlyRefs();
  }



  /*------------------------  inheritance  -----------------------*/


  /**
   * Get all heirs of given clazz that are instantiated.
   *
   * @param cl a clazz id
   *
   * @return an array of the clazz id's of all heirs for cl that are
   * instantiated, including cl itself, provided that cl is instantiated.
   */
  @Override
  public int[] clazzInstantiatedHeirs(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    if (!clazzIsRef(cl))
      {
        // NYI: this is sometimes (e.g. in tests/inheritance) called for non-ref
        // clazzes. Check what this is needed for, seems not to make not so much
        // sense.
      }

    var c = id2clazz(cl);
    var result = new List<Clazz>();
    for (var h : c.heirs())
      {
        if (h.isInstantiatedChoice())
          {
            result.add(h);
          }
      }
    var res = new int[result.size()];
    for (var i = 0; i < result.size(); i++)
      {
        res[i] = result.get(i)._id;
        if (CHECKS) check
          (res[i] != -1);
      }
    return res;
  }


  /*-------------------------  routines  -------------------------*/


  /**
   * Get the number of arguments required for a call to this clazz.
   *
   * @param cl clazz id
   *
   * @return number of arguments expected by cl, 0 if none or if clazz cl can
   * not be called (is a choice type)
   */
  @Override
  public int clazzArgCount(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return
      switch (clazzKind(c._id))
        {
        case Routine,
             Intrinsic,
             Abstract,
             Native -> c.argumentFields().length;
        case Field,
             Choice -> 0;
        };
  }


  /**
   * Get the clazz id of the type of the given argument of clazz cl
   *
   * @param cl clazz id
   *
   * @param arg argument number 0, 1, .. clazzArgCount(cl)-1
   *
   * @return clazz id of the argument or -1 if no such feature exists (the
   * argument is unused).
   */
  @Override
  public int clazzArgClazz(int cl, int arg)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size(),
       arg >= 0,
       arg < clazzArgCount(cl));

    var c = id2clazz(cl);
    var rc = c.argumentFields()[arg].resultClazz();
    return rc._id;
  }


  /**
   * Get the clazz id of the given argument of clazz cl
   *
   * @param cl clazz id
   *
   * @param arg argument number 0, 1, .. clazzArgCount(cl)-1
   *
   * @return clazz id of the argument or -1 if no such argument exists (the
   * argument is unused).
   */
  @Override
  public int clazzArg(int cl, int arg)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size(),
       arg >= 0,
       arg < clazzArgCount(cl));

    var c = id2clazz(cl);
    var af = c.argumentFields()[arg];
    return af._id;
  }


  /**
   * Get the id of the result field of a given clazz.
   *
   * @param cl a clazz id
   *
   * @return id of cl's result field or NO_CLAZZ if f has no result field (NYI: or a
   * result field that contains no data)
   */
  @Override
  public int clazzResultField(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    var r = c.resultField();
    return r == null ? NO_CLAZZ : r._id;
  }


  /**
   * If a clazz's instance contains an outer ref field, return this field.
   *
   * @param cl a clazz id
   *
   * @return clazz id of cl's outer ref field or -1 if no such field exists.
   */
  @Override
  public int clazzOuterRef(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    var or = c.outerRef();
    return or == null || c._outer.isUnitType() ? NO_CLAZZ : or._id;
  }


  /**
   * Get access to the code of a clazz of kind Routine
   *
   * @param cl a clazz id
   *
   * @return a site id referring to cl's code
   */
  @Override
  public int clazzCode(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size(),
       Errors.any() ||
       !_lookupDone ||
       clazzNeedsCode(cl) ||
       cl == clazz_Const_String() ||
       cl == clazz_Const_String_utf8_data() ||
       cl == clazz_array_u8() ||
       cl == clazz_fuzionSysArray_u8() ||
       cl == clazz_fuzionSysArray_u8_data() ||
       cl == clazz_fuzionSysArray_u8_length()
       );

    var c = id2clazz(cl);
    var result = c._code;
    if (result == NO_SITE)
      {
        if (!_lookupDone)
          {
            c.doesNeedCode();
          }
        result = addCode(cl, c);
        c._code = result;
      }
    return result;
  }


  int addCode(int cl, Clazz c)
  {
    var code = new List<Object>();
    var inhe = new List<List<AbstractCall>>();
    addCode(cl, c, code, inhe, c.feature(), NO_INH);
    check
      (code.size() == inhe.size(),
       _allCode.size() == _inh.size());
    var result = addCode(code);
    _inh.addAll(inhe);
    _inh.add(null);
    check
      (_allCode.size() == _inh.size());
    while (_siteClazzes.size() < _allCode.size())
      {
        _siteClazzes.add(cl);
      }
    check
      (_allCode.size() == _siteClazzes.size());
    return result;
  }

  void addCode(int cl, Clazz c, List<Object> code, List<List<AbstractCall>> inhe, LibraryFeature ff, List<AbstractCall> inh)
  {
    if (!clazzIsVoidType(cl))
      {
        for (var p: ff.inherits())
          {
            var pf = (LibraryFeature) p.calledFeature();
            var of = pf.outerRef();
            Clazz or = (of == null) ? null : c.lookup(of);
            var needsOuterRef = (or != null && (!or.resultClazz().isUnitType()));
            toStack(code, p.target(), !needsOuterRef /* dump result if not needed */);
            while (inhe.size() < code.size()) { inhe.add(inh); }
            while (_inh.size() < _allCode.size()) { _inh.add(inh); }
            if (needsOuterRef)
              {
                code.add(ExprKind.Current);
                code.add(or);  // field clazz means assignment to field
              }
            if (CHECKS) check
              (p.actuals().size() == p.calledFeature().valueArguments().size());

            AbstractFeature cf = pf;
            var n = p.actuals().size();
            var argFields = new Clazz[n];
            for (var i = 0; i < n; i++)
              {
                if (i >= cf.valueArguments().size())
                  {
                    if (CHECKS) check
                      (Errors.any());
                  }
                else
                  {
                    var cfa = cf.valueArguments().get(i);
                    argFields[i] = c.lookupNeeded(cfa);
                  }
              }
            for (var i = 0; i < p.actuals().size(); i++)
              {
                var a = p.actuals().get(i);
                toStack(code, a);
                while (inhe.size() < code.size()) { inhe.add(inh); }
                while (_inh.size() < _allCode.size()) { _inh.add(inh); }
                code.add(ExprKind.Current);
                // Field clazz means assign value to that field
                code.add(argFields[i]);
              }

            var inh1 = new List<AbstractCall>();
            inh1.add(p);
            inh1.addAll(inh);
            addCode(cl, c, code, inhe, pf, inh1);
          }
        toStack(code, ff.code());
        while (inhe.size() < code.size()) { inhe.add(inh); }
        while (_inh.size() < _allCode.size()) { _inh.add(inh); }
      }
  }


  /**
   * Does the backend need to generate code for this clazz since it might be
   * called at runtime.  This is true for all features that are called directly
   * or dynamically in a 'normal' call, i.e., not in an inheritance call.
   *
   * An inheritance call is inlined since it works on a different instance, the
   * instance of the heir class.  Consequently, a clazz resulting from an
   * inheritance call does not need code for itself.
   */
  @Override
  public boolean clazzNeedsCode(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return c.needsCode();
  }


  void doesNeedCode(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    c.doesNeedCode();
  }


  /*-----------------------  constructors  -----------------------*/


  /**
   * Check if the given clazz is a constructor, i.e., a routine returning
   * its instance as a result?
   *
   * @param cl a clazz id
   *
   * @return true if the clazz is a constructor, false otherwise
   */
  @Override
  public boolean isConstructor(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return switch (c.feature().kind())
      {
      case Routine -> c.feature().isConstructor();
      default -> false;
      };
  }


  /**
   * Is the given clazz a ref clazz?
   *
   * @parm cl a constructor clazz id
   *
   * @return true for non-value-type clazzes
   */
  @Override
  public boolean clazzIsRef(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return c.isRef();
  }


  /**
   * Is the given clazz a ref clazz that contains a boxed value type?
   *
   * @return true for boxed value-type clazz
   */
  @Override
  public boolean clazzIsBoxed(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return c.isRef() && !c.feature().isRef();
  }


  /**
   * For a reference clazz, obtain the corresponding value clazz.
   *
   * @param cl a clazz id
   *
   * @return clazz id of corresponding value clazz.
   */
  @Override
  public int clazzAsValue(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var cc = id2clazz(cl);
    var vcc = id2clazz(cl).asValue();
    if (vcc.isRef())
      {
        throw new Error("vcc.isRef in clazzAsValue for "+clazzAsString(cl)+" is "+vcc);
      }
    var vc0 = id2clazz(cl).asValue()._id;
    var vc = vcc._id;

    if (POSTCONDITIONS) ensure
      (!clazzIsRef(vc));

    return vc;
  }


  /*--------------------------  cotypes  -------------------------*/


  /**
   * For a clazz that represents a Fuzion type such as 'i32.type', return the
   * corresponding name of the type such as 'i32'.  This value is returned by
   * intrinsic `Type.name`.
   *
   * @param cl a clazz id of a cotype
   *
   * @return the name of the type represented by instances of cl, using UTF8 encoding.
   */
  @Override
  public byte[] clazzTypeName(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return c.typeName().getBytes(StandardCharsets.UTF_8);
  }


  /**
   * If cl is a type parameter, return the type parameter's actual type.
   *
   * @param cl a clazz id
   *
   * @return if cl is a type parameter, clazz id of cl's actual type or -1 if cl
   * is not a type parameter.
   */
  @Override
  public int clazzTypeParameterActualType(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var cc = id2clazz(cl);
    return cc.feature().isTypeParameter() ? cc.typeParameterActualType()._id
                                          : NO_CLAZZ;
  }


  /*----------------------  special clazzes  ---------------------*/


  /**
   * Obtain SpecialClazz from a given clazz.
   *
   * @param cl a clazz id
   *
   * @return the corresponding SpecialClazz or c_NOT_FOUND if cl is not a
   * special clazz.
   */
  @Override
  public SpecialClazzes getSpecialClazz(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return c._specialClazzId;
  }


  /**
   * Check if a clazz is the special clazz c.
   *
   * @param cl a clazz id
   *
   * @param c one of the constants SpecialClazzes.c_i8,...
   *
   * @return true iff cl is the specified special clazz c
   */
  @Override
  public boolean clazzIs(int cl, SpecialClazzes c)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl)._specialClazzId == c;
  }


  /**
   * Get the id of the given special clazz.
   *
   * @param s the special clazz we are looking for
   */
  Clazz specialClazz(SpecialClazzes s)
  {
    if (PRECONDITIONS) require
      (s != SpecialClazzes.c_NOT_FOUND);

    var result = _specialClazzes[s.ordinal()];
    if (result == null)
      {
        if (s == SpecialClazzes.c_universe)
          {
            result = id2clazz(_universe);
          }
        else
          {
            var o = clazz(s._outer);
            var oc = id2clazz(o);
            var of = oc.feature();
            var f = (LibraryFeature) of.get(of._libModule, s._name, s._argCount);
            result = newClazz(oc, f.selfType(), -1);
            if (CHECKS) check
              (f.isRef() == result.isRef());
          }
        _specialClazzes[s.ordinal()] = result;
      }
    return result;
  }


  /**
   * Get the id of the given special clazz.
   *
   * @param s the special clazz we are looking for
   */
  @Override
  public int clazz(SpecialClazzes s)
  {
    if (PRECONDITIONS) require
      (s != SpecialClazzes.c_NOT_FOUND);

    return specialClazz(s)._id;
  }


  /**
   * Get the id of clazz Any.
   *
   * @return clazz id of clazz Any
   */
  @Override
  public int clazzAny()
  {
    return clazz(SpecialClazzes.c_Const_String);
  }


  /**
   * Get the id of clazz universe.
   *
   * @return clazz id of clazz universe
   */
  @Override
  public int clazzUniverse()
  {
    return _universe;
  }


  /**
   * Get the id of clazz Const_String
   *
   * @return the id of Const_String or -1 if that clazz was not created.
   */
  @Override
  public int clazz_Const_String()
  {
    return clazz(SpecialClazzes.c_Const_String);
  }


  /**
   * Get the id of clazz Const_String.utf8_data
   *
   * @return the id of Const_String.utf8_data or -1 if that clazz was not created.
   */
  @Override
  public int clazz_Const_String_utf8_data()
  {
    return clazz(SpecialClazzes.c_CS_utf8_data);
  }


  /**
   * Get the id of clazz `array u8`
   *
   * @return the id of Const_String.array or -1 if that clazz was not created.
   */
  @Override
  public int clazz_array_u8()
  {
    var utf8_data = clazz_Const_String_utf8_data();
    return clazzResultClazz(utf8_data);
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>
   *
   * @return the id of fuzion.sys.array<u8> or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8()
  {
    var a8 = clazz_array_u8();
    var ia = lookup_array_internal_array(a8);
    var res = clazzResultClazz(ia);
    return res;
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>.data
   *
   * @return the id of fuzion.sys.array<u8>.data or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8_data()
  {
    var sa8 = clazz_fuzionSysArray_u8();
    return lookup_fuzion_sys_internal_array_data(sa8);
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>.length
   *
   * @return the id of fuzion.sys.array<u8>.length or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8_length()
  {
    var sa8 = clazz_fuzionSysArray_u8();
    return lookup_fuzion_sys_internal_array_length(sa8);
  }


  /**
   * Get the id of clazz fuzion.java.Java_Object
   *
   * @return the id of fuzion.java.Java_Object or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionJavaObject()
  {
    return newClazz(Types.resolved.f_fuzion_Java_Object.selfType())._id;
  }


  /**
   * Get the id of clazz fuzion.java.Java_Object.Java_Ref
   *
   * @return the id of fuzion.java.Java_Object.Java_Ref or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionJavaObject_Ref()
  {
    return newClazz(Types.resolved.f_fuzion_Java_Object_Ref.selfType())._id;
  }


  /**
   * Get the id of clazz error
   *
   * @return the id of error or -1 if that clazz was not created.
   */
  @Override
  public int clazz_error()
  {
    return clazz(SpecialClazzes.c_error);
  }


  /**
   * On `cl` lookup field `Java_Ref`
   *
   * @param cl Java_Object or inheriting from Java_Object
   *
   */
  @Override
  public int lookupJavaRef(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).lookupNeeded(Types.resolved.f_fuzion_Java_Object_Ref)._id;
  }


  /**
   * Check if the given clazz is a --possibly inherited--
   * `fuzion.java.Java_Object.Java_Ref` field.
   *
   * NYI: CLEANUP: #3927: Remove once #3927 is fixed.
   *
   * @param cl a clazz id that should be checked, must not be NO_CLAZZ.
   */
  @Override
  public boolean isJavaRef(int cl)
  {
    var f = id2clazz(cl).feature();
    return isJavaRef(f);
  }


  /**
   * Helper for isJavaRef to check is this is or redefineds
   * `fuzion.java.Java_Object.Java_Ref` field.
   *
   * NYI: CLEANUP: #3927: Remove once #3927 is fixed.
   */
  boolean isJavaRef(AbstractFeature f)
  {
    var result = f == Types.resolved.f_fuzion_Java_Object_Ref;
    if (!result)
      {
        for (var rf : f.redefines())
          {
            result = result || isJavaRef(rf);
          }
      }
    return result;
  }


  /**
   * For a clazz that is an heir of 'Function', find the corresponding inner
   * clazz for 'call'.  This is used for code generation of intrinsic
   * 'abortable' that has to create code to call 'call'.
   *
   * @param cl index of a clazz that is an heir of 'Function'.
   *
   * @return the index of the requested `Function.call` feature's clazz.
   */
  @Override
  public int lookupCall(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).lookupNeeded(Types.resolved.f_Function_call)._id;
  }


  /**
   * For a clazz that is an heir of 'effect', find the corresponding inner
   * clazz for 'finally'.  This is used for code generation of intrinsic
   * 'instate0' that has to create code to call 'effect.finally'.
   *
   * @param cl index of a clazz that is an heir of 'effect'.
   *
   * @return the index of the requested `effect.finally` feature's clazz.
   */
  @Override
  public int lookup_static_finally(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).lookupNeeded(Types.resolved.f_effect_static_finally)._id;
  }


  /**
   * For a clazz of concur.atomic, lookup the inner clazz of the value field.
   *
   * @param cl index of a clazz representing cl's value field
   *
   * @return the index of the requested `concur.atomic.value` field's clazz.
   */
  @Override
  public int lookupAtomicValue(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).lookupNeeded(Types.resolved.f_concur_atomic_v)._id;
  }


  /**
   * For a clazz of array, lookup the inner clazz of the internal_array field.
   *
   * @param cl index of a clazz `array T` for some type parameter `T`
   *
   * @return the index of the requested `array.internal_array` field's clazz.
   */
  @Override
  public int lookup_array_internal_array(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size(),
       id2clazz(cl).feature() == Types.resolved.f_array);

    return id2clazz(cl).lookupNeeded(Types.resolved.f_array_internal_array)._id;
  }


  /**
   * For a clazz of fuzion.sys.internal_array, lookup the inner clazz of the
   * data field.
   *
   * @param cl index of a clazz `fuzion.sys.internal_array T` for some type parameter `T`
   *
   * @return the index of the requested `fuzion.sys.internal_array.data` field's clazz.
   */
  @Override
  public int lookup_fuzion_sys_internal_array_data(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size(),
       id2clazz(cl).feature() == Types.resolved.f_fuzion_sys_array);

    return id2clazz(cl).lookupNeeded(Types.resolved.f_fuzion_sys_array_data)._id;
  }


  /**
   * For a clazz of fuzion.sys.internal_array, lookup the inner clazz of the
   * length field.
   *
   * @param cl index of a clazz `fuzion.sys.internal_array T` for some type parameter `T`
   *
   * @return the index of the requested `fuzion.sys.internal_array.length` field's clazz.
   */
  @Override
  public int lookup_fuzion_sys_internal_array_length(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size(),
       id2clazz(cl).feature() == Types.resolved.f_fuzion_sys_array);

    return id2clazz(cl).lookupNeeded(Types.resolved.f_fuzion_sys_array_length)._id;
  }


  /**
   * For a clazz of error, lookup the inner clazz of the msg field.
   *
   * @param cl index of a clazz `error`
   *
   * @return the index of the requested `error.msg` field's clazz.
   */
  @Override
  public int lookup_error_msg(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).lookupNeeded(Types.resolved.f_error_msg)._id;
  }


  /*---------------------------  types  --------------------------*/


  /**
   * Is there just one single value of this class, so this type is essentially a
   * C/Java `void` type?
   *
   * NOTE: This is false for Fuzion's `void` type!
   */
  @Override
  public boolean clazzIsUnitType(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).isUnitType();
  }


  /**
   * Is this a void type, i.e., values of this clazz do not exist.
   */
  @Override
  public boolean clazzIsVoidType(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).isVoidType();
  }


  /**
   * Test is a given clazz is not -1 and stores data.
   *
   * @param cl the clazz defining a type, may be -1
   *
   * @return true if cl != -1 and not unit or void type.
   */
  @Override
  public boolean hasData(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return
      !clazzIsUnitType(cl) &&
      !clazzIsVoidType(cl) &&
      cl != clazzUniverse();
  }


  /**
   * Add entries of type ExprKind created from the given expression (and its
   * nested expressions) to list l. pop the result in case dumpResult==true.
   *
   * @param l list of ExprKind that should be extended by s's expressions
   *
   * @param e a expression.
   *
   * @param dumpResult flag indicating that we are not interested in the result.
   */
  @Override
  protected void toStack(List<Object> l, Expr e, boolean dumpResult)
  {
    if ((e instanceof AbstractCall ||
         e instanceof InlineArray    ) && isConst(e))
      {
        if (!dumpResult)
          {
            l.add(e.asCompileTimeConstant());
          }
      }
    else
      {
        super.toStack(l, e, dumpResult);
      }
  }


  /**
   * Is this a compile-time constant?
   *
   * @param o an Object from the IR-Stack.
   *
   * @return true iff `o` is an Expr and can be turned into a compile-time constant.
   */
  private boolean isConst(Object o)
  {
    if (PRECONDITIONS) require
      (o instanceof Expr || o instanceof ExprKind);

    return o instanceof InlineArray iai && isConst(iai)
        || o instanceof Constant
        || o instanceof AbstractCall ac && isConst(ac)
        || o instanceof AbstractBlock ab && ab._expressions.size() == 1 && isConst(ab.resultExpression());
  }


  /**
   * Can this array be turned into a compile-time constant?
   */
  private boolean isConst(InlineArray ia)
  {
    return
      !ia.type().dependsOnGenerics() &&
      !ia.type().containsThisType() &&
      // some backends have special handling for array void.
      !ia.elementType().isVoid() &&
      ia._elements
        .stream()
        .allMatch(el -> {
          var s = new List<>();
          super.toStack(s, el);
          return s
            .stream()
            .allMatch(x -> isConst(x));
        });
  }


  /**
   * Can this call be turned into a constant?
   *
   * @param ac the call to be analyzed.
   *
   * @return true iff the call is suitable to be turned into
   * a compile time constant.
   */
  private boolean isConst(AbstractCall ac)
  {
    var result =
      !ac.isInheritanceCall() &&
      ac.calledFeature().isConstructor() &&
      // contains no fields
      ac.calledFeature().code().containsOnlyDeclarations() &&
      // we are calling a value type feature
      !ac.calledFeature().selfType().isRef() &&
      // only features without args and no fields may be inherited
      ac.calledFeature().inherits().stream().allMatch(c -> c.calledFeature().arguments().isEmpty() && c.calledFeature().code().containsOnlyDeclarations()) &&
      // no unit   // NYI we could allow units that does not contain declarations
      ac.actuals().size() > 0 &&
      ac.actuals().stream().allMatch(x -> isConst(x));

    if (result)
      {
        var s = new List<>();
        super.toStack(s, ac, false);
        result = s
          .stream()
          .allMatch(x -> x == ac || isConst(x));
      }
    return result;
  }




  /*----------------------  type parameters  ---------------------*/


  /**
   * Get the id of an actual generic parameter of a given clazz.
   *
   * @param cl a clazz id
   *
   * @param gix indec of the generic parameter
   *
   * @return id of cl's actual generic parameter #gix
   */
  @Override
  public int clazzActualGeneric(int cl, int gix)
  {
    var cc = id2clazz(cl);
    return cc.actualTypeParameters()[gix]._id;
  }


  /*---------------------  analysis results  ---------------------*/


  /**
   * Determine the lifetime of the instance of a call to clazz cl.
   *
   * @param cl a clazz id of any kind
   *
   * @return A conservative estimate of the lifespan of cl's instance.
   * Undefined if a call to cl does not create an instance, Call if it is
   * guaranteed that the instance is inaccessible after the call returned.
   */
  @Override
  public LifeTime lifeTime(int cl)
  {
    throw new Error("NYI");
  }


  /*--------------------------  accessing code  -------------------------*/


  /*
  @Override
  public boolean withinCode(int s)
  {
    return (s != NO_SITE) && false;
  }
  */

  /**
   * Get the clazz id at the given site
   *
   * @param s a site, may be !withinCode(s), i.e., this may be used on
   * `clazzCode(cl)` if the code is empty.
   *
   * @return the clazz id that code at site s belongs to.
   */
  @Override
  public int clazzAt(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size());

    return _siteClazzes.get(s - SITE_BASE);
  }


  /**
   * Create a String representation of a site for debugging purposes:
   *
   * @param s a site or NO_SITE
   *
   * @return a String describing site
   */
  @Override
  public String siteAsString(int s)
  {
    String res;
    if (s == NO_SITE)
      {
        res = "** NO_SITE **";
      }
    else if (s >= SITE_BASE && (s - SITE_BASE < _allCode.size()))
      {
        var cl = clazzAt(s);
        var p = sitePos(s);
        res = clazzAsString(cl) + "(" + clazzArgCount(cl) + " args)" + (p == null ? "" : " at " + sitePos(s).show());
      }
    else
      {
        res = "ILLEGAL site " + s;
      }
    return res;

  }


  /**
   * Get the expr at the given site
   *
   * @param s site
   */
  @Override
  public ExprKind codeAt(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s));

    var e = getExpr(s);
    var result = e instanceof Clazz  ? ExprKind.Assign /* Clazz represents the field we assign a value to */
                                     : exprKind(e);
    if (result == null)
      {
        Errors.fatal(sitePos(s),
                     "Expr `" + e.getClass() + "` not supported in FUIR.codeAt", "Expression class: " + e.getClass());
        result = ExprKind.Current; // keep javac from complaining.
      }
    return result;
  }


  /**
   * For an instruction of type ExprKind.Tag at site s, return a pair of the type of the
   * original value and the new type of the tagged value.
   *
   * @param s a code site for an Env instruction.
   *
   * @return pair of untagged and tagged types.
   */
  private
  synchronized /* NYI: remove once it is ensured that _siteClazzCache is no longer modified when _lookupDone */
  Pair<Clazz,Clazz> tagValueAndReusltClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Tag);

    var res = (Pair<Clazz,Clazz>) _siteClazzCache.get(s);
    if (res == null)
      {
        if (CHECKS) check
          (!_lookupDone);
        var cl = clazzAt(s);
        var outerClazz = clazz(cl);
        var t = (Tag) getExpr(s);
        Clazz vc = clazz(t._value, outerClazz, _inh.get(s - SITE_BASE));
        var tc = outerClazz.handDown(t._taggedType, _inh.get(s - SITE_BASE));
        tc.instantiatedChoice(t);
        res = new Pair<>(vc, tc);
        _siteClazzCache.put(s, res);
      }
    return res;
  }



  /**
   * For an instruction of type ExprKind.Tag at site s, return the type of the
   * original value that will be tagged.
   *
   * @param s a code site for an Env instruction.
   *
   * @return the original type, i.e., for `o option i32 := 42`, this is `i32`.
   */
  @Override
  public int tagValueClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Tag);

    return tagValueAndReusltClazz(s).v0()._id;
  }


  /**
   * For an instruction of type ExprKind.Tag at site s, return the type of the
   * original value that will be tagged.
   *
   * @param s a code site for an Env instruction.
   *
   * @return the new choice type, i.e., for `o option i32 := 42`, this is
   * `option i32`.
   */
  @Override
  public int tagNewClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Tag);

    return tagValueAndReusltClazz(s).v1()._id;
  }


  /**
   * For an instruction of type ExprKind.Tag at site s, return the number of the
   * choice. This will be the same number as the tag number used in a match.
   *
   * @param s a code site for an Env instruction.
   *
   * @return the tag number, i.e., for `o choice a b i32 c d := 42`, this is
   * `2`.
   */
  @Override
  public int tagTagNum(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Tag);

    var t = (Tag) getExpr(s);
    return t.tagNum();
  }


  /**
   * For an instruction of type ExprKind.Env at site s, return the type of the
   * env value.
   *
   * @param s a code site for an Env instruction.
   */
  @Override
  public int envClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       withinCode(s),
       codeAt(s) == ExprKind.Env);

    var cl = clazzAt(s);
    var outerClazz = clazz(cl);
    var v = (Env) getExpr(s);
    Clazz vcl = clazz(v, outerClazz, _inh.get(s - SITE_BASE));
    return vcl == null ? -1 : vcl._id;
  }


  /**
   * For an instruction of type ExprKind.Box at site s, return the original type
   * of the value that is to be boxed and the new reference type.
   *
   * @param s a code site for a Box instruction.
   *
   * @return a pair consisting of the original type and the new boxed type
   */
  private
  synchronized /* NYI: remove once it is ensured that _siteClazzCache is no longer modified when _lookupDone */
  Pair<Clazz,Clazz> boxValueAndResultClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Box);

    var res = (Pair<Clazz,Clazz>) _siteClazzCache.get(s);
    if (res == null)
      {
        if (CHECKS) check
          (!_lookupDone || true); // NYI, why doesn't this hold?
        var cl = clazzAt(s);
        var outerClazz = id2clazz(cl);
        var b = (Box) getExpr(s);
        Clazz vc = clazz(b._value, outerClazz, _inh.get(s - SITE_BASE));
        var rc = outerClazz.handDown(b.type(), -1, _inh.get(s - SITE_BASE));
        if (rc.isRef() &&
            outerClazz.feature() != Types.resolved.f_type_as_value) // NYI: ugly special case
          {
            rc = vc.asRef();
          }
        else
          {
            rc = vc;
          }
        res = new Pair<>(vc, rc);
        _siteClazzCache.put(s, res);
      }
    return res;
  }


  /**
   * For an instruction of type ExprKind.Box at site s, return the original type
   * of the value that is to be boxed.
   *
   * @param s a code site for a Box instruction.
   *
   * @return the original type of the value to be boxed.
   */
  @Override
  public int boxValueClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Box);

    return boxValueAndResultClazz(s).v0()._id;
  }


  /**
   * For an instruction of type ExprKind.Box at site s, return the new reference
   * type of the value that is to be boxed.
   *
   * @param s a code site for a Box instruction.
   *
   * @return the new reference type of the value to be boxed.
   */
  @Override
  public int boxResultClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Box);

    return boxValueAndResultClazz(s).v1()._id;
  }


  /**
   * Get the code for a comment expression.  This is used for debugging.
   *
   * @param s site of the comment
   */
  @Override
  public String comment(int s)
  {
    throw new Error("NYI");
  }


  /*
   * Is vc.asRef assignable to ec?
   */
  private boolean asRefAssignable(Clazz ec, Clazz vc)
  {
    return asRefDirectlyAssignable(ec, vc) || asRefAssignableToChoice(ec, vc);
  }


  /*
   * Is vc.asRef directly assignable to ec, i.e. without the need for tagging?
   */
  private boolean asRefDirectlyAssignable(Clazz ec, Clazz vc)
  {
    return ec.isRef() && ec._type.isAssignableFrom(vc.asRef()._type, Context.NONE);
  }


  /*
   * Is ec a choice and vc.asRef assignable to ec?
   */
  private boolean asRefAssignableToChoice(Clazz ec, Clazz vc)
  {
    return ec._type.isChoice() &&
      !ec._type.isAssignableFrom(vc._type, Context.NONE) &&
      ec._type.isAssignableFrom(vc._type.asRef(), Context.NONE);
  }


  /**
   * Get the inner clazz for a non dynamic access or the static clazz of a dynamic
   * access.
   *
   * @param s site of the access
   *
   * @return the clazz that has to be accessed or -1 if the access is an
   * assignment to a field that is unused, so the assignment is not needed.
   */
  @Override
  public
  synchronized /* NYI: remove once it is ensured that _siteClazzCache is no longer modified when _lookupDone */
  int accessedClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Call   ||
       codeAt(s) == ExprKind.Assign    );

    var res = _siteClazzCache.get(s);
    if (res == null)
      {
        if (CHECKS) check
          (!_lookupDone || true); // NYI, why doesn't this hold?
        res = accessedClazz(s, null);
        if (res == null)
          {
            res = this;  // using `this` for `null`.
          }
        _siteClazzCache.put(s, res);
      }
    return res instanceof Clazz rc ? rc._id : NO_CLAZZ;
  }


  private Clazz accessedClazz(int s, Clazz tclazz)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Call   ||
       codeAt(s) == ExprKind.Assign    );

    var cl = clazzAt(s);
    var outerClazz = id2clazz(cl);
    var e = getExpr(s);

    var innerClazz = switch (e)
      {
      case AbstractCall   call -> calledInner(call, outerClazz, tclazz, _inh.get(s - SITE_BASE));
      case AbstractAssign a    -> assignedField(outerClazz, tclazz, a, _inh.get(s - SITE_BASE));
      case Clazz          fld  -> fld;
      default                  -> { throw new Error("accessedClazz found unexpected Expr " + (e == null ? e : e.getClass()) + "."); }
      };
    return innerClazz == null ? null : innerClazz;
  }


  public Clazz calledTarget(AbstractCall c, Clazz outerClazz, List<AbstractCall> inh)
  {
    if (PRECONDITIONS) require
      (Errors.any() || c.calledFeature() != null && c.target() != null);

    if (c.calledFeature() == null  || c.target() == null)
      {
        return error();  // previous errors, give up
      }

    return clazz(c.target(), outerClazz, inh);
  }


  public Clazz calledInner(AbstractCall c, Clazz outerClazz, Clazz explicitTarget, List<AbstractCall> inh)
  {
    if (PRECONDITIONS) require
      (Errors.any() || c.calledFeature() != null && c.target() != null);

    var outer = outerClazz.feature();
    if (c.calledFeature() == null  || c.target() == null)
      {
        return error();  // previous errors, give up
      }

    var tclazz = explicitTarget == null
      ? calledTarget(c, outerClazz, inh)
      : explicitTarget;

    Clazz innerClazz = null;
    var cf      = c.calledFeature();
    var callToOuterRef = c.target().isCallToOuterRef();
    var dynamic = c.isDynamic() && (tclazz.isRef() || callToOuterRef);
    var needsCode = !dynamic || explicitTarget != null;
    var typePars = outerClazz.actualGenerics(c.actualTypeParameters());
    if (!tclazz.isVoidType())
      {
        innerClazz = tclazz.lookup(new FeatureAndActuals(cf, typePars), c.select(), c.isInheritanceCall());
        if (c.calledFeature() == Types.resolved.f_Type_infix_colon)
          {
            var T = innerClazz.actualTypeParameters()[0];
            cf = T._type.constraintAssignableFrom(Context.NONE, tclazz._type.generics().get(0))
              ? Types.resolved.f_Type_infix_colon_true
              : Types.resolved.f_Type_infix_colon_false;
            innerClazz = tclazz.lookup(new FeatureAndActuals(cf, typePars), -1, c.isInheritanceCall());
          }
        if (needsCode)
          {
            innerClazz.doesNeedCode();
          }
      }
    return innerClazz == null ? error() : innerClazz;
  }



  private Clazz assignedField(Clazz outerClazz, Clazz tclazz, AbstractAssign a, List<AbstractCall> inh)
  {
    if (tclazz == null)
      {
        tclazz = clazz(a._target, outerClazz, inh);
        // target clazz of an assignment is always the value counterpart, even
        // if `tclazz` is a `ref` that itself is never instantiated.
        //
        // NYI: CLEANUP: We might reconsider this and permit `ref` here the same
        // way that `ref` can be the target to a call that reads a
        // field. Currently, back-ends (JVM) rely on this being a value, though.
        tclazz = tclazz.asValue();
      }
    var fc = tclazz.lookup(a._assignedField);
    if (fc.resultClazz().isUnitType())
      {
        fc = null;
      }
    return fc;
  }


  /**
   * Get the type of an assigned value. This returns the type even if the
   * assigned field has been removed and accessedClazz() returns -1.
   *
   * @param s site of the assignment
   *
   * @return the type of the assigned value.
   */
  @Override
  public int assignedType(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Assign    );

    var cl = clazzAt(s);
    var outerClazz = id2clazz(cl);
    var e = getExpr(s);
    var field = switch (e)
      {
      case AbstractAssign a   ->
      {
        Clazz sClazz = clazz(a._target, outerClazz, _inh.get(s - SITE_BASE));
        var vc = sClazz.asValue();
        yield vc.lookup(a._assignedField);
      }
      case Clazz          fld -> fld;
      default                 -> { throw new Error("assignedType found unexpected Expr " + (e == null ? e : e.getClass()) + "."); }
      };

    return field.resultClazz()._id;
  }


  private void addToAccessedClazzes(int s, int tclazz, int innerClazz)
  {
    var a = _accessedClazzes.get(s);
    if (a == null)
      {
        _accessedClazzes.put(s, new int[] { tclazz, innerClazz});
      }
    else
      {
        var found = false;
        for (var i=0; i < a.length && !found; i+=2)
          {
            if (a[i] == tclazz)
              {
                if (CHECKS) check
                  (a[i+1] == innerClazz);
                found = true;
              }
          }
        if (!found)
          {
            if (CHECKS) check
              (!_lookupDone);

            var n = new int[a.length+2];
            System.arraycopy(a, 0, n, 0, a.length);
            n[a.length  ] = tclazz;
            n[a.length+1] = innerClazz;
            _accessedClazzes.put(s, n);
          }
      }
  }


  /**
   * Get the possible inner clazzes for a dynamic call or assignment to a field
   *
   * @param s site of the access
   *
   * @return an array with an even number of element pairs with accessed target
   * clazzes at even indices followed by the corresponding inner clazz of the
   * feature to be accessed for this target.
   */
  private int[] accessedClazzesDynamic(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Call   ||
       codeAt(s) == ExprKind.Assign    ,
       accessIsDynamic(s));

    var result = _accessedClazzes.get(s);
    if (result == null)
      {
        result = NO_CLAZZ_IDS;
      }
    return result;
  }

  /**
   * Get the possible inner clazzes for a call or assignment to a field
   *
   * @param s site of the access
   *
   * @return an array with an even number of element pairs with accessed target
   * clazzes at even indices followed by the corresponding inner clazz of the
   * feature to be accessed for this target.
   */
  @Override
  public int[] accessedClazzes(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Call   ||
       codeAt(s) == ExprKind.Assign    );

    int[] result;
    if (accessIsDynamic(s))
      {
        result = accessedClazzesDynamic(s);
      }
    else
      {
        var innerClazz = accessedClazz(s);
        var tt = clazzOuterClazz(innerClazz);
        result = clazzNeedsCode(innerClazz) ? new int[] { tt, innerClazz }
                                            : new int[0];
      }
    return result;
  }


  /**
   * Get the possible inner clazz for a call or assignment to a field with given
   * target clazz.
   *
   * This is used to feed information back from static analysis tools like DFA
   * to the GeneratingFUIR such that the given target will be added to the
   * targets / inner clazzes tuples returned by accesedClazzes.
   *
   * @param s site of the access
   *
   * @param tclazz the target clazz of the acces.
   *
   * @return the accessed inner clazz or NO_CLAZZ in case that does not exist,
   * i.e., an abstract feature is missing.
   */
  @Override
  public int lookup(int s, int tclazz)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Call   ||
       codeAt(s) == ExprKind.Assign    ,
       tclazz >= CLAZZ_BASE &&
       tclazz < CLAZZ_BASE  + _clazzes.size());

    int innerClazz;
    if (accessIsDynamic(s))
      {
        var inner = accessedClazz(s, id2clazz(tclazz));
        innerClazz = inner == null ? NO_CLAZZ : inner._id;
        if (inner != null)
          {
            addToAccessedClazzes(s, tclazz, innerClazz);
          }
      }
    else
      {
        innerClazz = accessedClazz(s);
        if (CHECKS) check
          (tclazz == clazzOuterClazz(innerClazz));
      }
    if (innerClazz != NO_CLAZZ)
      {
        innerClazz = switch (clazzKind(innerClazz))
          {
          case Routine, Intrinsic, Native, Field -> innerClazz;
          case Abstract, Choice -> NO_CLAZZ;
          };
      }
    if (innerClazz != NO_CLAZZ && codeAt(s) == ExprKind.Call)
      {
        doesNeedCode(innerClazz);
      }

    return innerClazz;
  }


  /**
   * Inform the FUIR instance that lookup for new clazzes is finished.  This
   * means that clazzIsUnitType will be able to produce correct results since no
   * more features will be added.
   */
  @Override
  public void lookupDone()
  {
    if (CHECKS) check
      (!_lookupDone);

    _lookupDone = true;

    // NYI: UNDER DEVELOPMENT: layout phase creates new clazzes, which is why we cannot iterate like this. Need to check why and remove this!
    //
    // for(var c : _clazzes)
    for (var i = 0; i < _clazzes.size(); i++)
      {
        var c = _clazzes.get(i);
        c.layoutAndHandleCycle();
      }
  }


  /**
   * Is an access to a feature (assignment, call) dynamic?
   *
   * @param s site of the access
   *
   * @return true iff the assignment or call requires dynamic binding depending
   * on the actual target type.
   */
  @Override
  public boolean accessIsDynamic(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Assign ||
       codeAt(s) == ExprKind.Call  );

    var cl = clazzAt(s);
    var outerClazz = id2clazz(cl);
    var e = getExpr(s);
    var res = switch (e)
      {
      case AbstractAssign ass  -> id2clazz(accessTargetClazz(s)).isRef();
      case Clazz          arg  -> outerClazz.isRef() && !arg.feature().isOuterRef(); // assignment to arg field in inherits call (dynamic if outerClazz is ref)
                                                                                    // or to outer ref field (not dynamic)
      case AbstractCall   call -> id2clazz(accessTargetClazz(s)).isRef();
      default                  -> { throw new Error("accessIsDynamic found unexpected Expr " + (e == null ? e : e.getClass()) + "."); }
      };
    return res;
  }


  /**
   * Get the target (outer) clazz of a feature access
   *
   * @param cl index of clazz containing the access
   *
   * @param s site of the access
   *
   * @return index of the static outer clazz of the accessed feature.
   */
  @Override
  public int accessTargetClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Assign ||
       codeAt(s) == ExprKind.Call  );

    var tclazz = _accessedTarget.get(s);
    if (tclazz == null)
      {
        var cl = clazzAt(s);
        var outerClazz = id2clazz(cl);
        var e = getExpr(s);
        tclazz = switch (e)
          {
          case AbstractAssign ass  -> clazz(ass._target, outerClazz, _inh.get(s - SITE_BASE)); // NYI: This should be the same as assignedField._outer
          case Clazz          arg  -> outerClazz; // assignment to arg field in inherits call, so outer clazz is current instance
          case AbstractCall   call -> calledTarget(call, outerClazz, _inh.get(s - SITE_BASE));
          default                  -> { throw new Error("accessTargetClazz found unexpected Expr " + (e == null ? e : e.getClass()) + "."); }
          };
        _accessedTarget.put(s, tclazz);
      }

    return tclazz._id;
  }


  /**
   * For an intermediate command of type ExprKind.Const, return its clazz.
   *
   * Currently, the clazz is one of bool, i8, i16, i32, i64, u8, u16, u32, u64,
   * f32, f64, or Const_String. This will be extended by value instances without
   * refs, choice instances with tag, arrays, etc.
   *
   * @param cl index of clazz containing the constant
   *
   * @param s site of the constant
   */
  @Override
  public int constClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Const);

    var res = (Clazz) _siteClazzCache.get(s);
    if (res == null)
      {
        var cl = clazzAt(s);
        var cc = id2clazz(cl);
        var outerClazz = cc;
        var ac = (Constant) getExpr(s);
        res = switch (ac.origin())
          {
          case Constant     c -> clazz(c, outerClazz, _inh.get(s - SITE_BASE));
          case AbstractCall c -> calledInner(c, outerClazz, null, _inh.get(s - SITE_BASE));
          case InlineArray  ia -> outerClazz.handDown(ia.type(),  _inh.get(s - SITE_BASE));
          default -> throw new Error("constClazz origin of unknown class " + ac.origin().getClass());
          };
        _siteClazzCache.put(s, res);
      }

    return res._id;
  }


  /**
   * For an intermediate command of type ExprKind.Const, return the constant
   * data using little endian encoding, i.e, 0x12345678 -> { 0x78, 0x56, 0x34, 0x12 }.
   */
  @Override
  public byte[] constData(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Const);

    var ic = getExpr(s);
    return ((Constant) ic).data();
  }


  /**
   * For a match expression, get the static clazz of the subject.
   *
   * @param s site of the match
   *
   * @return clazz id of type of the subject
   */
  @Override
  public
  synchronized /* NYI: remove once it is ensured that _siteClazzCache is no longer modified when _lookupDone */
  int matchStaticSubject(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Match);

    var rc = (Clazz) _siteClazzCache.get(s);
    if (rc == null)
      {
        if (CHECKS) check
          (!_lookupDone);
        var cl = clazzAt(s);
        var cc = id2clazz(cl);
        var outerClazz = cc;
        var m = (AbstractMatch) getExpr(s);
        rc = clazz(m.subject(), outerClazz, _inh.get(s - SITE_BASE));
        _siteClazzCache.put(s, rc);
      }
    return rc._id;
  }


  /**
   * For a match expression, get the field of a given case
   *
   * @param s site of the match
   *
   * @paramc cix index of the case in the match
   *
   * @return clazz id of field the value in this case is assigned to, -1 if this
   * case does not have a field or the field is unused.
   */
  @Override
  public int matchCaseField(int s, int cix)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Match);

    var cl = clazzAt(s);
    var cc = id2clazz(cl);
    var outerClazz = cc;
    var m = (AbstractMatch) getExpr(s);
    var mc = m.cases().get(cix);
    var f = mc.field();
    var result = NO_CLAZZ;
    if (f != null)
      {
        // NYI: Check if this works for a case that is part of an inherits clause, do
        // we need to store in outerClazz.outer?
        result = outerClazz.lookup(f)._id;
      }
    return result;
  }


  /**
   * For a given tag return the index of the corresponding case.
   *
   * @param s site of the match
   *
   * @param tag e.g. 0,1,2,...
   *
   * @return the index of the case for tag `tag`
   */
  @Override
  public int matchCaseIndex(int s, int tag)
  {
    var result = -1;
    for (var j = 0; result < 0 && j <  matchCaseCount(s); j++)
      {
        var mct = matchCaseTags(s, j);
        if (Arrays.stream(mct).anyMatch(t -> t == tag))
          {
            result = j;
          }
      }
    if (CHECKS) check
      (result != -1);
    return result;
  }


  /**
   * For a match expression, get the tags matched by a given case
   *
   * @param s site of the match
   *
   * @paramc cix index of the case in the match
   *
   * @return array of tag numbers this case matches
   */
  @Override
  public int[] matchCaseTags(int s, int cix)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Match);

    var m = (AbstractMatch) getExpr(s);
    var mc = m.cases().get(cix);
    var ts = mc.types();
    var f = mc.field();
    int nt = f != null ? 1 : ts.size();
    var resultL = new List<Integer>();
    int tag = 0;
    for (var cg : m.subject().type().choiceGenerics(Context.NONE /* NYI: CLEANUP: Context should no longer be needed during FUIR */))
      {
        for (int tix = 0; tix < nt; tix++)
          {
            var t = f != null ? f.resultType() : ts.get(tix);
            if (t.isDirectlyAssignableFrom(cg, Context.NONE /* NYI: CLEANUP: Context should no longer be needed during FUIR */))
              {
                resultL.add(tag);
              }
          }
        tag++;
      }
    var result = new int[resultL.size()];
    for (int i = 0; i < result.length; i++)
      {
        result[i] = resultL.get(i);
      }

    if(POSTCONDITIONS) ensure
      (result.length > 0);

    return result;
  }


  /**
   * For a match expression, get the code associated with a given case
   *
   * @param s site of the match
   *
   * @paramc cix index of the case in the match
   *
   * @return code block for the case
   */
  @Override
  public int matchCaseCode(int s, int cix)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Match);

    var me = getExpr(s);
    var e = getExpr(s + 1 + cix);

    if (me instanceof AbstractMatch m &&
        m.subject() instanceof AbstractCall sc)
      {
        var c = m.cases().get(cix);
        var cf = sc.calledFeature();
        if (cf == Types.resolved.f_Type_infix_colon_true  ||
            cf == Types.resolved.f_Type_infix_colon_false ||
            cf == Types.resolved.f_Type_infix_colon          )
          {
            var outer = id2clazz(clazzAt(s));
            var innerClazz = calledInner(sc, outer, null, _inh.get(s - SITE_BASE));
            var tclazz = innerClazz._outer;
            var T = innerClazz.actualTypeParameters()[0];
            var pos = cf == Types.resolved.f_Type_infix_colon_true ||
              cf == Types.resolved.f_Type_infix_colon  &&
              T._type.constraintAssignableFrom(Context.NONE /* NYI: CLEANUP: Context should no longer be needed during FUIR */, tclazz._type.generics().get(0));
            var tf = pos ? Types.resolved.f_TRUE : Types.resolved.f_FALSE;
            if (!c.types().stream().anyMatch(x->x.compareTo(tf.selfType())==0))
              {
                return NO_SITE;
              }
          }
      }

    return ((NumLiteral) e).intValue().intValueExact();
  }


  /**
   * @return If the expression has only been found to result in void.
   */
  @Override
  public boolean alwaysResultsInVoid(int s)
  {
    return false;
  }


  /**
   * Get the source code position of an expr at the given index if it is available.
   *
   * @param s site of an expression
   *
   * @return the source code position or null if not available.
   */
  @Override
  public SourcePosition sitePos(int s)
  {
    if (PRECONDITIONS) require
      (s == NO_SITE || s >= SITE_BASE,
       s == NO_SITE || withinCode(s));

    SourcePosition result = SourcePosition.notAvailable;
    if (s != NO_SITE)
      {
        var e = getExpr(s);
        result = (e instanceof Expr expr) ? expr.pos() :
                 (e instanceof Clazz z)   ? z._type.declarationPos()  /* implicit assignment to argument field */
                                          : null;
      }
    return result;
  }


  /*-----------------  convenience methods for effects  -----------------*/


  /**
   * Is cl one of the intrinsics in effect that changes the effect in
   * the current environment?
   *
   * @param cl the id of the intrinsic clazz
   *
   * @return true for effect.install and similar features.
   */
  @Override
  public boolean isEffectIntrinsic(int cl)
  {
    if (PRECONDITIONS) require
      (cl != NO_CLAZZ);

    return
      (clazzKind(cl) == FeatureKind.Intrinsic) &&
      switch(clazzOriginalName(cl))
      {
      case "effect.type.abort0"  ,
           "effect.type.default0",
           "effect.type.instate0",
           "effect.type.is_instated0",
           "effect.type.replace0" -> true;
      default -> false;
      };
  }


  /**
   * For an intrinsic in effect that changes the effect in the
   * current environment, return the type of the environment.  This type is used
   * to distinguish different environments.
   *
   * @param cl the id of the intrinsic clazz
   *
   * @return the type of the outer feature of cl
   */
  @Override
  public int effectTypeFromInstrinsic(int cl)
  {
    if (PRECONDITIONS) require
      (isEffectIntrinsic(cl));

    return clazzActualGeneric(clazzOuterClazz(cl), 0);
  }


  /*------------------------------  arrays  -----------------------------*/


  /**
   * the clazz of the elements of the array
   *
   * @param constCl, e.g. `array (tuple i32 codepoint)`
   *
   * @return e.g. `tuple i32 codepoint`
   */
  @Override
  public int inlineArrayElementClazz(int constCl)
  {
    if (PRECONDITIONS) require
      (clazzIsArray(constCl));

    var result = type2clazz(id2clazz(constCl)._type.generics().get(0))._id;

    if (POSTCONDITIONS) ensure
      (result >= 0);

    return result;
  }


  /**
   * Is `cl` an array?
   */
  @Override
  public boolean clazzIsArray(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return clazz(cl).feature() == Types.resolved.f_array;
  }


  /*----------------------------  constants  ----------------------------*/




  /**
   * Extract bytes from `bb` that should be used when deserializing for `cl`.
   *
   * @param cl the constants clazz
   *
   * @param bb the bytes to be used when deserializing this constant.
   *           May be more than necessary for variable length constants
   *           like strings, arrays, etc.
   */
  private ByteBuffer deserializeClazz(int cl, ByteBuffer bb)
  {
    return switch (getSpecialClazz(cl))
      {
      case c_Const_String, c_String :
        var len = bb.duplicate().order(ByteOrder.LITTLE_ENDIAN).getInt();
        yield bb.slice(bb.position(), 4+len);
      case c_bool :
        yield bb.slice(bb.position(), 1);
      case c_i8, c_i16, c_i32, c_i64, c_u8, c_u16, c_u32, c_u64, c_f32, c_f64 :
        var bytes = bb.duplicate().order(ByteOrder.LITTLE_ENDIAN).getInt();
        yield bb.slice(bb.position(), 4+bytes);
      default:
        yield this.clazzIsArray(cl)
          ? deserializeArray(this.inlineArrayElementClazz(cl), bb)
          : deserializeValueConst(cl, bb);
      };
  }


  /**
   * bytes used when serializing call that results in this type.
   */
  private ByteBuffer deserializeValueConst(int cl, ByteBuffer bb)
  {
    var args = clazzArgCount(cl);
    var bbb = bb.duplicate().order(ByteOrder.LITTLE_ENDIAN);
    var argBytes = 0;
    for (int i = 0; i < args; i++)
      {
        var rt = clazzArgClazz(cl, i);
        argBytes += deseralizeConst(rt, bbb).length;
      }
    return bb.slice(bb.position(), argBytes);
  }


  /**
   * Extract bytes from `bb` that should be used when deserializing for `cl`.
   *
   * @param cl the constants clazz
   *
   * @param bb the bytes to be used when deserializing this constant.
   *           May be more than necessary for variable length constants
   *           like strings, arrays, etc.
   */
  @Override
  public byte[] deseralizeConst(int cl, ByteBuffer bb)
  {
    var elBytes = deserializeClazz(cl, bb.duplicate()).order(ByteOrder.LITTLE_ENDIAN);
    bb.position(bb.position()+elBytes.remaining());
    var b = new byte[elBytes.remaining()];
    elBytes.get(b);
    return b;
  }



  /**
   * Extract bytes from `bb` that should be used when deserializing this inline array.
   *
   * @param elementClazz the elements clazz
   *
   * @elementCount the count of elements in this array.
   *
   * @param bb the bytes to be used when deserializing this constant.
   *           May be more than necessary for variable length constants
   *           like strings, arrays, etc.
   */
  private ByteBuffer deserializeArray(int elementClazz, ByteBuffer bb)
  {
    var bbb = bb.duplicate().order(ByteOrder.LITTLE_ENDIAN);
    var elCount = bbb.getInt();
    var elBytes = 0;
    for (int i = 0; i < elCount; i++)
      {
        elBytes += deseralizeConst(elementClazz, bbb).length;
      }
    return bb.slice(bb.position(), 4+elBytes);
  }


  /*----------------------  accessing source code  ----------------------*/


  /**
   * Get the source file the clazz originates from.
   *
   * e.g. /fuzion/tests/hello/HelloWorld.fz, $FUZION/lib/panic.fz
   */
  @Override
  public String clazzSrcFile(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return c.feature().pos()._sourceFile._fileName.toString();
  }


  /**
   * Get the position where the clazz is declared
   * in the source code.
   *
   * NYI: CLEANUP: This is currently used only by the interpreter backend. Maybe we should remove this?
   */
  @Override
  public SourcePosition declarationPos(int cl)
  {
    var c = id2clazz(cl);
    return c._type.declarationPos();
  }


  /*---------------------------------------------------------------------
   *
   * handling of abstract missing errors.
   *
   */


  /**
   * tuple of clazz, called abstract features and location where the clazz was
   * instantiated.
   */
  record AbsMissing(Clazz clazz,
                    TreeMap<AbstractFeature, String> called,
                    SourcePosition instantiationPos,
                    String context)
  {
  };


  /**
   * Set of missing implementations of abstract features
   */
  TreeMap<Clazz, AbsMissing> _abstractMissing = new TreeMap<>((a,b)->Integer.compare(a._id,b._id));


  /**
   * If a called to an abstract feature was found, the DFA will use this to
   * record the missing implementation of an abstract features.
   *
   * Later, this will be reported as an error via `reportAbstractMissing()`.
   *
   * @param cl clazz is of the clazz that is missing an implementation of an
   * abstract features.
   *
   * @param f the inner clazz that is called and that is missing an implementation
   *
   * @param instantiationPos if known, the site where `cl` was instantiated,
   * `NO_SITE` if unknown.
   */
  @Override
  public void recordAbstractMissing(int cl, int f, int instantiationSite, String context, int callSite)
  {
    // we might have an assignment to a field that was removed:
    if (codeAt(callSite) == FUIR.ExprKind.Call)
      {
        var cc = id2clazz(cl);
        var cf = id2clazz(f);
        var r = _abstractMissing.computeIfAbsent(cc, ccc ->
          new AbsMissing(ccc,
                         new TreeMap<>(),
                         instantiationSite == NO_SITE ? SourcePosition.notAvailable : sitePos(instantiationSite),
                         context));
        r.called.put(cf.feature(), sitePos(callSite).show());
        if (CHECKS) check
          (cf.feature().isAbstract() ||
           (cf.feature().modifiers() & FuzionConstants.MODIFIER_FIXED) != 0);
      }
  }


  /**
   * In case any errors were recorded via `recordAbstractMissing` this will
   * create the corresponding error messages.  The errors reported will be
   * cumulative, i.e., if a clazz is missing several implementations of abstract
   * features, there will be only one error for that clazz.
   */
  @Override
  public void reportAbstractMissing()
  {
    _abstractMissing.values()
      .stream()
      .forEach(r -> FuirErrors.abstractFeatureNotImplemented(r.clazz.feature(),
                                                             r.called,
                                                             r.instantiationPos,
                                                             r.context));
  }


}

/* end of file */
