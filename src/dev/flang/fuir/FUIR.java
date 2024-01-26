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
import java.nio.charset.StandardCharsets;

import java.util.BitSet;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.air.Clazz;
import dev.flang.air.Clazzes;
import dev.flang.air.FeatureAndActuals;

import dev.flang.ast.AbstractAssign; // NYI: remove dependency
import dev.flang.ast.AbstractBlock; // NYI: remove dependency
import dev.flang.ast.AbstractCall; // NYI: remove dependency
import dev.flang.ast.AbstractConstant; // NYI: remove dependency
import dev.flang.ast.AbstractFeature; // NYI: remove dependency
import dev.flang.ast.AbstractMatch; // NYI: remove dependency
import dev.flang.ast.BoolConst; // NYI: remove dependency
import dev.flang.ast.Box; // NYI: remove dependency
import dev.flang.ast.Env; // NYI: remove dependency
import dev.flang.ast.Expr; // NYI: remove dependency
import dev.flang.ast.If; // NYI: remove dependency
import dev.flang.ast.InlineArray; // NYI: remove dependency
import dev.flang.ast.NumLiteral; // NYI: remove dependency
import dev.flang.ast.Tag; // NYI: remove dependency
import dev.flang.ast.Types; // NYI: remove dependency

import dev.flang.ir.IR;

import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.Map2Int;
import dev.flang.util.MapComparable2Int;
import dev.flang.util.SourcePosition;


/**
 * The FUIR contains the intermediate representation of fuzion applications.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FUIR extends IR
{


  /*----------------------------  constants  ----------------------------*/


  public enum ContractKind
  {
    Pre,
    Post;

    /**
     * String representation for debugging.
     */
    public String toString()
    {
      switch (this)
        {
        case Pre : return "pre-condition";
        case Post: return "post-condition";
        default: throw new Error("unhandled switch case");
        }
    }
  }


  /**
   * Map used by getSpecialId() to quickly find the SpecialClazz corresponding
   * to a Clazz.
   */
  private static TreeMap<Clazz, SpecialClazzes> SPECIAL_ID = new TreeMap<>();


  /**
   * Enum of clazzes that require special handling in the backend
   */
  public enum SpecialClazzes
  {
    c_i8          { Clazz getIfCreated() { return Clazzes.i8         .getIfCreated(); } },
    c_i16         { Clazz getIfCreated() { return Clazzes.i16        .getIfCreated(); } },
    c_i32         { Clazz getIfCreated() { return Clazzes.i32        .getIfCreated(); } },
    c_i64         { Clazz getIfCreated() { return Clazzes.i64        .getIfCreated(); } },
    c_u8          { Clazz getIfCreated() { return Clazzes.u8         .getIfCreated(); } },
    c_u16         { Clazz getIfCreated() { return Clazzes.u16        .getIfCreated(); } },
    c_u32         { Clazz getIfCreated() { return Clazzes.u32        .getIfCreated(); } },
    c_u64         { Clazz getIfCreated() { return Clazzes.u64        .getIfCreated(); } },
    c_f32         { Clazz getIfCreated() { return Clazzes.f32        .getIfCreated(); } },
    c_f64         { Clazz getIfCreated() { return Clazzes.f64        .getIfCreated(); } },
    c_bool        { Clazz getIfCreated() { return Clazzes.bool       .getIfCreated(); } },
    c_TRUE        { Clazz getIfCreated() { return Clazzes.c_TRUE     .getIfCreated(); } },
    c_FALSE       { Clazz getIfCreated() { return Clazzes.c_FALSE    .getIfCreated(); } },
    c_Const_String{ Clazz getIfCreated() { return Clazzes.Const_String.getIfCreated(); } },
    c_String      { Clazz getIfCreated() { return Clazzes.String     .getIfCreated(); } },
    c_sys_ptr     { Clazz getIfCreated() { return Clazzes.fuzionSysPtr;               } },
    c_unit        { Clazz getIfCreated() { return Clazzes.c_unit     .getIfCreated(); } },

    // dummy entry to report failure of getSpecialId()
    c_NOT_FOUND   { Clazz getIfCreated() { return null;                               } };

    abstract Clazz getIfCreated();

    SpecialClazzes()
    {
      var c = getIfCreated();
      if (c != null)
        {
          SPECIAL_ID.put(c, this);
        }
    }
  }


  /*----------------------------  variables  ----------------------------*/


  /**
   * The main clazz.
   */
  final Clazz _main;


  /**
   * Mapping from Clazz instances to int ids.
   */
  final Map2Int<Clazz> _clazzIds;


  /**
   * Cached results for clazzCode(), required to ensure that code indices are
   * unique, i.e., comparing the code index is equivalent to comparing the clazz
   * ids.
   */
  private final TreeMap<Integer, Integer> _clazzCode;


  /**
   * Cached results for clazzContract(), required to ensure that code indices are
   * unique, i.e., comparing the code index is equivalent to comparing the clazz
   * ids.
   */
  private final TreeMap<Long, Integer> _clazzContract;


  /**
   * Cached 'true' results of 'clazzNeedsCode'
   */
  BitSet _needsCode = new BitSet();


  /**
   * Cached 'false' results of 'clazzNeedsCode'
   */
  BitSet _doesNotNeedCode = new BitSet();


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create FUIR from given Clazz instance.
   *
   * @param main the main clazz.
   */
  public FUIR(Clazz main)
  {
    _main = main;
    _clazzIds = new MapComparable2Int<>(CLAZZ_BASE);
    _clazzCode = new TreeMap<>();
    _clazzContract = new TreeMap<>();
    Clazzes.findAllClasses(main());
  }


  /**
   * Clone this FUIR such that modifications can be made by optimizers.  A heir
   * of FUIR can use this to redefine methods.
   *
   * @param original the original FUIR instance that we are cloning.
   */
  public FUIR(FUIR original)
  {
    super(original);
    _main = original._main;
    _clazzIds = original._clazzIds;
    _clazzCode = original._clazzCode;
    _clazzContract = original._clazzContract;
  }


  /*-----------------------------  methods  -----------------------------*/


  public Clazz main()
  {
    return _main;
  }


  /*------------------------  accessing classes  ------------------------*/


  /**
   * Get Clazz that given id maps to
   */
  private Clazz clazz(int id)
  {
    return _clazzIds.get(id);
  }


  /**
   * Get id of given clazz.
   */
  private int id(Clazz cc)
  {
    return cc._idInFUIR;
  }


  /**
   * Add cl to the set of clazzes in this FUIR and assign an id to cl.
   */
  private void add(Clazz cl)
  {
    var id = _clazzIds.add(cl);

    if (CHECKS) check
      (cl._idInFUIR == -1 || cl._idInFUIR == id);

    cl._idInFUIR = id;
  }


  private void addClasses()
  {
    if (_clazzIds.size() == 0)
      {
        for (var cl : Clazzes.all())
          {
            if (CHECKS) check
              (Errors.any() || cl._type != Types.t_ERROR);

            if (cl._type == Types.t_ERROR)
              {
                if (CHECKS) check
                  (Errors.any());

                if (!Errors.any())
                  {
                    Errors.error("Found error clazz in set of clazzes in the IR even though no earlier errors " +
                                 "were reported.  This can only be the result of a severe bug.");
                  }
              }
            else if (cl._type != Types.t_ADDRESS)     // NYI: would be better to not create this dummy clazz in the first place
              {
                add(cl);
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
    return id(_main);
  }


  /**
   * Convert a clazz id into a number 0, 1, 2, 3, ...
   *
   * The clazz id is intentionally large to detect accidental usage of a clazz
   * id in a wrong context.
   *
   * @param cl a clazz id
   */
  public int clazzId2num(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= firstClazz() && cl <= lastClazz());

    var result = cl - FUIR.CLAZZ_BASE;

    if (POSTCONDITIONS) ensure
      (result >= 0 && result <= lastClazz() - firstClazz());

    return result;
  }


  public int clazzNumFields(int cl)
  {
    return clazz(cl).fields().length;
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
    if (PRECONDITIONS) require
      (i >= 0 && i < clazzNumFields(cl));

    var cc = clazz(cl);
    var fc = cc.fields()[i];
    return id(fc);
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
    var cc = clazz(cl);
    return cc.isChoice() ? cc.choiceGenerics().size() : -1;
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
  public int clazzChoice(int cl, int i)
  {
    if (PRECONDITIONS) require
      (i >= 0 && i < clazzNumChoices(cl));

    var cc = clazz(cl);
    var cg = cc.choiceGenerics().get(i);
    var res = cg.isRef()          ||
              cg.isInstantiated()    ? cg
                                     : Clazzes.c_void.get();
    return id(res);
  }


  /**
   * Get all heirs of given clazz that are instantiated.
   *
   * @param cl a clazz id
   *
   * @return an array of the clazz id's of all heirs for cl that are
   * instantiated, including cl itself, provided that cl is instantiated.
   */
  public int[] clazzInstantiatedHeirs(int cl)
  {
    var cc = clazz(cl);
    var result = new List<Clazz>();
    for (var h : cc.heirs())
      {
        if (h.isInstantiated())
          {
            result.add(h);
          }
      }
    var res = new int[result.size()];
    for (var i = 0; i < result.size(); i++)
      {
        res[i] = id(result.get(i));
        if (CHECKS) check
          (res[i] != -1);
      }
    return res;
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
    var cc = clazz(cl);
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
    var cc = clazz(cl);
    return cc.isChoiceOfOnlyRefs();
  }


  /**
   * Check if field does not store the value directly, but a pointer to the value.
   *
   * @param fcl a clazz id of the field
   *
   * @return true iff the field is an outer ref field that holds an address of
   * an outer value, false for normal fields our outer ref fields that store the
   * outer ref or value directly.
   */
  public boolean clazzFieldIsAdrOfValue(int fcl)
  {
    var fc = clazz(fcl);
    return clazzFieldIsAdrOfValue(fc);
  }


  /**
   * Check if field does not store the value directly, but a pointer to the value.
   *
   * @param fc a clazz of the field
   *
   * @return true iff the field is an outer ref field that holds an address of
   * an outer value, false for normal fields our outer ref fields that store the
   * outer ref or value directly.
   */
  private boolean clazzFieldIsAdrOfValue(Clazz fc)
  {
    var f = fc.feature();
    return f.isOuterRef() &&
      !fc.resultClazz().isRef() &&
      !fc.resultClazz().isUnitType() &&
      !fc.resultClazz().feature().isBuiltInPrimitive();
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
    var cc = clazz(cl);
    return id(cc.resultClazz());
  }


  /**
   * For a clazz that represents a Fuzion type such as 'i32.type', return the
   * corresponding name of the type such as 'i32'.
   *
   * @param cl a clazz id of a type clazz
   *
   * @return the name of the type represented by instances of cl, using UTF8 encoding.
   */
  public byte[] clazzTypeName(int cl)
  {
    var cc = clazz(cl);
    return cc.typeName().getBytes(StandardCharsets.UTF_8);
  }


  public FeatureKind clazzKind(int cl)
  {
    return clazzKind(clazz(cl));
  }


  FeatureKind clazzKind(Clazz cc)
  {
    var ff = cc.feature();
    return switch (ff.kind())
      {
      case Routine    -> FeatureKind.Routine;
      case Field      -> FeatureKind.Field;
      case Intrinsic  -> FeatureKind.Intrinsic;
      case Abstract   -> FeatureKind.Abstract;
      case Choice     -> FeatureKind.Choice;
      case TypeParameter -> FeatureKind.Intrinsic;
      case Native     -> FeatureKind.Native;
      default         -> throw new Error ("Unexpected feature kind: "+ff.kind());
      };
  }

  public String clazzBaseName(int cl)
  {
    var cc = clazz(cl);
    var res = cc.feature().featureName().baseName();
    res = res + cc._type.generics()
      .toString(" ", " ", "", t -> t.asStringWrapped());
    return res;
  }


  /**
   * The intrinsic names is the original qualified name of the intrinsic feature
   * ignoring any inheritance into new clazzes.
   *
   * @param cl an intrinsic
   *
   * @return its intrinsic name, e.g. 'Array.getel' instead of
   * 'Const_String.getel'
   */
  public String clazzIntrinsicName(int cl)
  {
    if (PRECONDITIONS) require
      (clazzKind(cl) == FeatureKind.Intrinsic ||
       clazzKind(cl) == FeatureKind.Native);

    var cc = clazz(cl);
    return cc.feature().qualifiedName();
  }


  /**
   * Is the given clazz a ref clazz?
   *
   * @return true for non-value-type clazzes
   */
  public boolean clazzIsRef(int cl)
  {
    return clazz(cl).isRef();
  }


  /**
   * Is the given clazz a ref clazz that contains a boxed value type?
   *
   * @return true for boxed value-type clazz
   */
  public boolean clazzIsBoxed(int cl)
  {
    var result = clazz(cl).isBoxed();

    if (POSTCONDITIONS) ensure
      (!result || clazzIsRef(cl));  // result implies clazzIsRef(cl)

    return result;
  }


  /**
   * For a reference clazz, obtain the corresponding value clazz.
   *
   * @param cl a clazz id
   *
   * @return clazz id of corresponding value clazz.
   */
  public int clazzAsValue(int cl)
  {
    var cc = clazz(cl);
    return id(cc.asValue());
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
    var cc = clazz(cl);
    return cc.argumentFields().length;
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
  public int clazzArgClazz(int cl, int arg)
  {
    if (PRECONDITIONS) require
      (arg >= 0,
       arg < clazzArgCount(cl));

    var c = clazz(cl);
    var rc = c.argumentFields()[arg].resultClazz(); // or .fieldClazz()?
    return id(rc);
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
  public int clazzArg(int cl, int arg)
  {
    var cc = clazz(cl);
    var a = cc.argumentFields()[arg];
    return a == null ? -1 : id(a);
  }


  /**
   * is the given clazz a choice clazz
   *
   * @param cl a clazz id
   */
  public boolean clazzIsChoice(int cl)
  {
    return clazz(cl).isChoice();
  }


  /**
   * Get the choice tag id for a given value clazz in a choice clazz
   *
   * @param cl a clazz id of a choice clazz
   *
   * @param valuecl a clazz id of a static clazz of a value that is stored in an
   * instance of cl.
   *
   * @return id of the valuecl, corresponds to the value to be stored in the tag.
   */
  public int clazzChoiceTag(int cl, int valuecl)
  {
    if (PRECONDITIONS) require
      (!clazzIsVoidType(valuecl));

    var cc = clazz(cl);
    var vc = clazz(valuecl);
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
    var o = clazz(cl)._outer;
    return o == null ? -1 : id(o);
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
    var cc = clazz(cl);
    var or = cc.outerRef();
    var cco = cc._outer;
    return
      or == null ||
      (cco.isUnitType()) ? -1
                         : id(or);
  }


  /**
   * If cl is a type parameter, return the type parameter's actual type.
   *
   * @param cl a clazz id
   *
   * @return if cl is a type parameter, clazz id of cl's actual type or -1 if cl
   * is not a type parameter.
   */
  public int clazzTypeParameterActualType(int cl)
  {
    var cc = clazz(cl);
    var at = -1;
    if (cc.feature().isTypeParameter())
      {
        var atc = cc.typeParameterActualType();
        at = id(atc);
      }
    return at;
  }


  /**
   * Get the id of clazz Any.
   *
   * @return clazz id of clazz Any
   */
  public int clazzAny()
  {
    return id(Clazzes.any.get());
  }


  /**
   * Get the id of clazz universe.
   *
   * @return clazz id of clazz universe
   */
  public int clazzUniverse()
  {
    return id(Clazzes.universe.get());
  }



  /**
   * Obtain SpecialClazzes id from a given clazz.
   *
   * @param cl a clazz id
   *
   * @return the corresponding SpecialClazzes id or c_NOT_FOUND if cl is not a
   * special clazz.
   */
  public SpecialClazzes getSpecialId(int cl)
  {
    var cc = clazz(cl);
    var result = SPECIAL_ID.get(cc);
    return result == null ? SpecialClazzes.c_NOT_FOUND : result;
  }


  /**
   * Check if a clazz is the special clazz c.
   *
   * @param cl a clazz id
   *
   * @param One of the constants SpecialClazzes.c_i8,...
   *
   * @return true iff cl is the specified special clazz c
   */
  public boolean clazzIs(int cl, SpecialClazzes c)
  {
    var cc = clazz(cl);
    return cc == c.getIfCreated();
  }


  // String representation of clazz, for debugging only
  public String clazzAsString(int cl)
  {
    return cl == -1
      ? "-- no clazz --"
      : clazz(cl).toString();
  }


  // String representation of clazz, for debugging and type names
  //
  // NYI: This should eventually replace clazzAsString.
  public String clazzAsStringNew(int cl)
  {
    return cl == -1
      ? "-- no clazz --"
      : clazz(cl)._type.asString();
  }


  /**
   * Is there just one single value of this class, so this type is essentially a
   * C/Java `void` type?
   *
   * NOTE: This is false for Fuzion's `void` type!
   */
  public boolean clazzIsUnitType(int cl)
  {
    var cc = clazz(cl);
    return cc.isUnitType();
  }


  /**
   * Is this a void type, i.e., values of this clazz do not exist.
   */
  public boolean clazzIsVoidType(int cl)
  {
    var cc = clazz(cl);
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
    var cc = clazz(cl);
    var rf = cc.resultField();
    return rf == null ? -1 : id(rf);
  }


  /**
   * Get the id of an actual generic parameter of a given clazz.
   *
   * @param cl a clazz id
   *
   * @param gix indec of the generic parameter
   *
   * @return id of cl's actual generic parameter #gix
   */
  public int clazzActualGeneric(int cl, int gix)
  {
    var cc = clazz(cl);
    return id(cc.actualGenerics()[gix]);
  }


  /**
   * add the code of feature ff to code.  In case ff has inherits calls, also
   * include the code of the inherited features.
   *
   * @param code a list that code should be added to.
   *
   * @param ff a routine or constructor feature.
   */
  private void addCode(Clazz cc, List<Object> code, AbstractFeature ff)
  {
    for (var p: ff.inherits())
      {
        /*
NYI: Any side-effects in p.target() or p.actuals() will be executed twice, once for
     the precondition and once for the inlined call! See this example:

hw25 is
  A (a i32)
    pre
      a < 100
  is
    say "in A: $a"

  B : A x is

  count := 0

  x =>
    set count := count + 1
    count

  B; B; B
  if (count == 3) say "PASS" else say "FAIL"
        */

        var pf = p.calledFeature();
        var of = pf.outerRef();
        var or = (of == null) ? null : (Clazz) cc._inner.get(new FeatureAndActuals(of, new List<>(), false));  // NYI: ugly cast
        var needsOuterRef = (or != null && !or.resultClazz().isUnitType());
        toStack(code, p.target(), !needsOuterRef /* dump result if not needed */);
        if (needsOuterRef)
          {
            if (clazzFieldIsAdrOfValue(or))
              {
                code.add(ExprKind.AdrOf);
              }
            code.add(ExprKind.Current);
            code.add(or);  // field clazz means assignment to field
          }
        if (CHECKS) check
          (p.actuals().size() == p.calledFeature().valueArguments().size());
        var argFields = cc._parentCallArgFields.get(p.globalIndex());
        for (var i = 0; i < p.actuals().size(); i++)
          {
            var a = p.actuals().get(i);
            var f = pf.arguments().get(i);
            toStack(code, a);
            code.add(ExprKind.Current);
            // Field clazz means assign value to that field
            code.add(argFields[i]);
          }
        addCode(cc, code, p.calledFeature());
      }
    toStack(code, ff.code());
  }


  public boolean doesResultEscape(int cl, int c, int i)
  {
    return true;
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
      (clazzKind(cl) == FeatureKind.Routine);

    var res = _clazzCode.get(cl);
    if (res == null)
      {
        var cc = clazz(cl);
        var ff = cc.feature();
        var code = new List<Object>();
        if (!clazzIsVoidType(cl))
          {
            addCode(cc, code, ff);
          }
        res = _codeIds.add(code);
        _clazzCode.put(cl, res);
      }
    return res;
  }


  /**
   * Get access to the code of the contract of a clazz of kind Routine,
   * Intrinsic, Abstract or Field.
   *
   * @param cl a clazz id
   *
   * @param ck the part of the contract to be accessed, ContractKind.Pre or
   * ContractKind.Post for pre- and post-conditions, respectively.
   *
   * @param ix the index of the pre- or post-condition, 0 for the first
   * condition
   *
   * @return a code id referring to cl's pre- or post-condition, -1 if cl does
   * not have a pre- or post-condition with the given index
   */
  public int clazzContract(int cl, ContractKind ck, int ix)
  {
    if (PRECONDITIONS) require
      (clazzKind(cl) == FeatureKind.Routine   ||
       clazzKind(cl) == FeatureKind.Field     ||
       clazzKind(cl) == FeatureKind.Intrinsic ||
       clazzKind(cl) == FeatureKind.Abstract  ||
       clazzKind(cl) == FeatureKind.Native       ,
       ix >= 0);

    var cc = clazz(cl);
    var ff = cc.feature();
    var ccontract = ff.contract();
    var cond = (ccontract != null && ck == ContractKind.Pre  ? ccontract.req :
                ccontract != null && ck == ContractKind.Post ? ccontract.ens : null);
    // NYI: PERFORMANCE: Always iterating the conditions results in performance
    // quadratic in the number of conditions.  This could be improved by
    // filtering BoolConst.TRUE once and reusing the resulting cond.
    var i = 0;
    while (cond != null && i < cond.size() &&
           (cond.get(i).cond == BoolConst.TRUE || ix > 0))
      {
        if (cond.get(i).cond != BoolConst.TRUE)
          {
            ix--;
          }
        i++;
      }
    var res = -1;
    if (cond != null && i < cond.size())
      {
        // create 64-bit key from cl, ck and ix as follows:
        //
        //  key = cl (32 bits) : -ix    for ck == Pre
        //  key = cl (32 bits) : +ix    for ck == Post
        //
        var key = ((long) cl << 32) | ((ck.ordinal()*2-1) * (i+1)) & 0xffffffffL;

        // lets verify we did not lose any information, i.e, we can extract cl, ix and ck:
        if (CHECKS) check
          (cl == key >> 32,
           ck == ((key << 32 < 0) ? ContractKind.Pre : ContractKind.Post),
           i == (int) (key & 0xffffffff) * ((ck.ordinal()*2-1))-1);

        var resBoxed = _clazzContract.get(key);
        if (resBoxed == null)
          {
            var code = new List<Object>();
            toStack(code, cond.get(i).cond);
            resBoxed = _codeIds.add(code);
            _clazzContract.put(key, resBoxed);
          }
        res = resBoxed;
      }
    return res;
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
  public boolean clazzNeedsCode(int cl)
  {
    if (_needsCode.get(cl - CLAZZ_BASE))
      {
        return true;
      }
    else if (_doesNotNeedCode.get(cl - CLAZZ_BASE))
      {
        return false;
      }
    else
      {
        var cc = clazz(cl);
        var result = switch (clazzKind(cc))
          {
          case Abstract, Choice -> false;
          case Intrinsic, Routine, Field, Native ->
            (cc.isInstantiated() || cc.feature().isOuterRef() || cc.feature().isTypeFeature())
            && cc != Clazzes.Const_String.getIfCreated()
            && !cc.isAbsurd()
            && !cc.isBoxed()
            // NYI: this should not depend on string comparison!
            && !(cc.feature().qualifiedName().equals("void.absurd"))
            ;
          };
        (result ? _needsCode : _doesNotNeedCode).set(cl - CLAZZ_BASE);
        return result;
      }
  }


  /**
   * Enum of possible life times of instances created when a clazz is called.
   *
   * Ordinal numbers are sorted by lifetime length, i.e., smallest ordinal is
   * shortest lifetime.
   */
  public enum LifeTime
  {
    /* the instance is no longer accessible after the call returns, so it can
     * safely be allocated on a runtime stack and freed when the call returns
     */
    Call,

    /* The instance has an unknown lifetime, so it should be heap allocated and
     * freed by GC
     */
    Unknown,

    /* The called clazz does not have an instance value, so there is no lifetime
     * associated to it
     */
    Undefined
  }

  static
  {
    check(LifeTime.Call.ordinal() < LifeTime.Unknown.ordinal());
  }


  /**
   * Determine the lifetime of the instance of a call to clazz cl.
   *
   * @param cl a clazz id of any kind
   *
   * @param pre true to analyse the instance created for cl's precondition,
   * false to analyse the instance created for a call to cl
   *
   * @return A conservative estimate of the lifespan of cl's instance.
   * Undefined if a call to cl does not create an instance, Call if it is
   * guaranteed that the instance is inaccessible after the call returned.
   */
  public LifeTime lifeTime(int cl, boolean pre)
  {
    var result =
      pre ? (switch (clazzKind(cl))
               {
               case Abstract  -> LifeTime.Unknown;
               case Choice    -> LifeTime.Undefined;
               case Intrinsic -> LifeTime.Unknown;
               case Field     -> LifeTime.Unknown;
               case Routine   -> LifeTime.Unknown;
               case Native    -> LifeTime.Unknown;
               })
          : (switch (clazzKind(cl))
               {
               case Abstract  -> LifeTime.Undefined;
               case Choice    -> LifeTime.Undefined;
               case Intrinsic -> LifeTime.Undefined;
               case Field     -> LifeTime.Call;
               case Routine   -> LifeTime.Unknown;
               case Native    -> LifeTime.Unknown;
               });

      return result;
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
    return clazz(cl).feature().isOuterRef();
  }


  /**
   * Get the id of clazz Const_String
   *
   * @return the id of Const_String or -1 if that clazz was not created.
   */
  public int clazz_Const_String()
  {
    var cc = Clazzes.Const_String.getIfCreated();
    return cc == null ? -1 : id(cc);
  }


  /**
   * Get the id of clazz Const_String.internal_array
   *
   * @return the id of Const_String.internal_array or -1 if that clazz was not created.
   */
  public int clazz_Const_String_internal_array()
  {
    var cc = Clazzes.constStringInternalArray;
    return cc == null ? -1 : id(cc);
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>
   *
   * @return the id of fuzion.sys.array<u8> or -1 if that clazz was not created.
   */
  public int clazz_fuzionSysArray_u8()
  {
    var cc = Clazzes.fuzionSysArray_u8;
    return cc == null ? -1 : id(cc);
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>.data
   *
   * @return the id of fuzion.sys.array<u8>.data or -1 if that clazz was not created.
   */
  public int clazz_fuzionSysArray_u8_data()
  {
    var cc = Clazzes.fuzionSysArray_u8_data;
    return cc == null ? -1 : id(cc);
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>.length
   *
   * @return the id of fuzion.sys.array<u8>.length or -1 if that clazz was not created.
   */
  public int clazz_fuzionSysArray_u8_length()
  {
    var cc = Clazzes.fuzionSysArray_u8_length;
    return cc == null ? -1 : id(cc);
  }


  /**
   * Get the id of clazz fuzion.java.Java_Object
   *
   * @return the id of fuzion.java.Java_Object or -1 if that clazz was not created.
   */
  public int clazz_fuzionJavaObject()
  {
    var cc = Clazzes.fuzionJavaObject;
    return cc == null ? -1 : id(cc);
  }


  /**
   * Get the id of clazz fuzion.java.Java_Object.Java_Ref
   *
   * @return the id of fuzion.java.Java_Object.Java_Ref or -1 if that clazz was not created.
   */
  public int clazz_fuzionJavaObject_Ref()
  {
    var cc = Clazzes.fuzionJavaObject_Ref;
    return cc == null ? -1 : id(cc);
  }


  /**
   * On `cl` lookup field `Java_Ref`
   *
   * @param cl Java_Object or inheriting from Java_Object
   *
   */
  public int lookupJavaRef(int cl)
  {
    return lookup(cl, Types.resolved.f_fuzion_java_object_ref);
  }


  /**
   * Get the id of the given special clazz.
   *
   * @param the id of clazz c or -1 if that clazz was not created.
   */
  public int clazz(SpecialClazzes c)
  {
    addClasses();
    var cc = c.getIfCreated();
    if (cc != null)
      {
        add(cc);
      }
    return cc == null ? -1 : id(cc);
  }


  /**
   * Get the id of clazz u8
   *
   * @param the id of u8 or -1 if that clazz was not created.
   */
  public int clazz_u8()
  {
    return clazz(SpecialClazzes.c_u8);
  }


  /*--------------------------  accessing code  -------------------------*/



  /**
   * Get the expr at the given index in given code block
   *
   * @param c the code block id
   *
   * @param ix an index within the code block
   */
  public ExprKind codeAt(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0, withinCode(c, ix));

    ExprKind result;
    var e = _codeIds.get(c).get(ix);
    if (e instanceof Clazz    )  /* Clazz represents the field we assign a value to */
      {
        result = ExprKind.Assign;
      }
    else
      {
        result = super.codeAt(c, ix);
      }
    if (result == null)
      {
        Errors.fatal(codeAtAsPos(c, ix),
                     "Expr not supported in FUIR.codeAt", "Statement class: " + e.getClass());
        result = ExprKind.Current; // keep javac from complaining.
      }
    return result;
  }


  public int tagValueClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Tag);

    var outerClazz = clazz(cl);
    var t = (Tag) _codeIds.get(c).get(ix);
    var vcl = outerClazz.actualClazzes(t, null)[0];
    return vcl == null ? -1 : id(vcl);
  }

  public int tagNewClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Tag);

    var outerClazz = clazz(cl);
    var t = (Tag) _codeIds.get(c).get(ix);
    var ncl = outerClazz.actualClazzes(t, null)[1];
    return ncl == null ? -1 : id(ncl);
  }

  /**
   * For outer clazz cl with an Env instruction in code c at ix, return the type
   * of the env value.
   */
  public int envClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Env);

    var outerClazz = clazz(cl);
    var v = (Env) _codeIds.get(c).get(ix);
    var vcl = outerClazz.actualClazzes(v, null)[0];
    return vcl == null ? -1 : id(vcl);
  }

  public int boxValueClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Box);

    var outerClazz = clazz(cl);
    var b = (Box) _codeIds.get(c).get(ix);
    Clazz vc = outerClazz.actualClazzes(b, null)[0];
    return id(vc);
  }

  public int boxResultClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Box);

    var outerClazz = clazz(cl);
    var b = (Box) _codeIds.get(c).get(ix);
    Clazz rc = outerClazz.actualClazzes(b, null)[1];
    return id(rc);
  }


  /**
   * Get the code for a comment expression.  This is used for debugging.
   *
   * @param cl index of clazz containing the comment
   *
   * @param c code block containing the comment
   *
   * @param ix index of the comment
   */
  public String comment(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Comment);

    return comment(c, ix);
  }


  /**
   * Get the inner clazz of the precondition of a call, -1 if no precondition.
   *
   * The precondition clazz may be different to the accessedClazz in case of
   * `ref` types: in the following code
   *
   *    x is
   *      f t
   *      pre condition
   *      is expr
   *    r ref x := x
   *    r.f
   *
   * the precondition clazz for the call `r.f` is `(ref x).f`, while the
   * accessedClazz may be `x.f` (NYI: check!).
   *
   * @param cl index of clazz containing the access
   *
   * @param c code block containing the access
   *
   * @param ix index of the access
   *
   * @return the clazz whose precondition has to be checked or -1 if there is no
   * precondition to be checked.
   */
  public int accessedPreconditionClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call   ||
       codeAt(c, ix) == ExprKind.Assign    );

    var outerClazz = clazz(cl);
    var s = _codeIds.get(c).get(ix);
    Clazz innerClazz =
      (s instanceof AbstractCall   call) ? outerClazz.actualClazzes(call, null)[2] :
      (Clazz) (Object) new Object() { { if (true) throw new Error("accessedClazz found unexpected Expr."); } } /* Java is ugly... */;

    var res = innerClazz == null ? -1 : id(innerClazz);
    return res != -1 && hasPrecondition(res) ? res : -1;
  }


  /**
   * Get the inner clazz for a non dynamic access or the static clazz of a dynamic
   * access.
   *
   * @param cl index of clazz containing the access
   *
   * @param c code block containing the access
   *
   * @param ix index of the access
   *
   * @return the clazz that has to be accessed or -1 if the access is an
   * assignment to a field that is unused, so the assignment is not needed.
   */
  public int accessedClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call   ||
       codeAt(c, ix) == ExprKind.Assign    );

    var outerClazz = clazz(cl);
    var s = _codeIds.get(c).get(ix);
    Clazz innerClazz =
      (s instanceof AbstractCall   call) ? outerClazz.actualClazzes(call, null)[0] :
      (s instanceof AbstractAssign a   ) ? outerClazz.actualClazzes(a   , null)[1] :
      (s instanceof Clazz          fld ) ? fld :
      (Clazz) (Object) new Object() { { if (true) throw new Error("accessedClazz found unexpected Expr."); } } /* Java is ugly... */;

    return innerClazz == null ? -1 : id(innerClazz);
  }


  /**
   * Get the type of an assigned value. This returns the type even if the
   * assigned field has been removed and accessedClazz() returns -1.
   *
   * @param cl index of clazz containing the assignment
   *
   * @param c code block containing the assignment
   *
   * @param ix index of the assignment
   *
   * @return the type of the assigned value.
   */
  public int assignedType(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Assign    );

    var outerClazz = clazz(cl);
    var s = _codeIds.get(c).get(ix);
    var t =
      (s instanceof AbstractAssign a   ) ? outerClazz.actualClazzes(a, null)[2] :
      (s instanceof Clazz          fld ) ? fld.resultClazz() :
      (Clazz) (Object) new Object() { { if (true) throw new Error("assignedType found unexpected Expr."); } } /* Java is ugly... */;

    return id(t);
  }


  /**
   * Get the possible inner clazzes for a dynamic call or assignment to a field
   *
   * @param cl index of clazz containing the access
   *
   * @param c code block containing the access
   *
   * @param ix index of the call
   *
   * @return an array with an even number of element pairs with accessed target
   * clazzes at even indices followed by the corresponding inner clazz of the
   * feature to be accessed for this target.
   */
  private int[] accessedClazzesDynamic(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call   ||
       codeAt(c, ix) == ExprKind.Assign    ,
       accessIsDynamic(cl, c, ix));

    var outerClazz = clazz(cl);
    var s =  _codeIds.get(c).get(ix);
    Clazz tclazz;
    AbstractFeature f;
    var typePars = AbstractCall.NO_GENERICS;

    if (s instanceof AbstractCall call)
      {
        f = call.calledFeature();
        tclazz   = outerClazz.actualClazzes(call, null)[1];
        typePars = outerClazz.actualGenerics(call.actualTypeParameters());
      }
    else if (s instanceof AbstractAssign ass)
      {
        var acl = outerClazz.actualClazzes(ass, null);
        var assignedField = acl[1];
        tclazz = acl[0];  // NYI: This should be the same as assignedField._outer
        f = assignedField.feature();
      }
    else if (s instanceof Clazz fld)
      {
        tclazz = (Clazz) fld._outer;
        f = fld.feature();
      }
    else
      {
        throw new Error();
      }
    var innerClazzes1 = new List<Clazz>();
    var innerClazzes2 = new List<Clazz>();
    for (var clz : tclazz.heirs())
      {
        if (CHECKS) check
          (clz.isRef() == tclazz.isRef());

        var in = (Clazz) clz._inner.get(new FeatureAndActuals(f, typePars, false));
        if (in != null && clazzNeedsCode(id(in)) && !innerClazzes1.contains(clz))
          {
            innerClazzes1.add(clz);
            innerClazzes2.add(in);
          }
      }

    var innerClazzIds = new int[2*innerClazzes1.size()];
    for (var i = 0; i < innerClazzes1.size(); i++)
      {
        innerClazzIds[2*i  ] = id(innerClazzes1.get(i));
        innerClazzIds[2*i+1] = id(innerClazzes2.get(i));
        if (CHECKS) check
          (innerClazzIds[2*i  ] != -1,
           innerClazzIds[2*i+1] != -1);
      }
    return innerClazzIds;
  }


  /**
   * Get the possible inner clazzes for a call or assignment to a field
   *
   * @param cl index of clazz containing the access
   *
   * @param c code block containing the access
   *
   * @param ix index of the call
   *
   * @return an array with an even number of element pairs with accessed target
   * clazzes at even indices followed by the corresponding inner clazz of the
   * feature to be accessed for this target.
   */
  public int[] accessedClazzes(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call   ||
       codeAt(c, ix) == ExprKind.Assign    );

    int[] result;
    if (accessIsDynamic(cl, c, ix))
      {
        result = accessedClazzesDynamic(cl, c, ix);
      }
    else
      {
        var innerClazz = accessedClazz(cl, c, ix);
        var tt = clazzOuterClazz(innerClazz);
        result = clazzNeedsCode(innerClazz) ? new int[] { tt, innerClazz }
                                            : new int[0];
      }
    return result;
  }


  /**
   * Is an access to a feature (assignment, call) dynamic?
   *
   * @param cl index of clazz containing the access
   *
   * @param c code block containing the access
   *
   * @param ix index of the access
   *
   * @return true iff the assignment or call requires dynamic binding depending
   * on the actual target type.
   */
  public boolean accessIsDynamic(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Assign ||
       codeAt(c, ix) == ExprKind.Call  );

    var outerClazz = clazz(cl);
    var s = _codeIds.get(c).get(ix);
    var res =
      (s instanceof AbstractAssign ass ) ? outerClazz.actualClazzes(ass, null)[0].isRef() : // NYI: This should be the same as assignedField._outer
      (s instanceof Clazz          arg ) ? outerClazz.isRef() && !arg.feature().isOuterRef() : // assignment to arg field in inherits call (dynamic if outerClazz is ref)
                                                                                       // or to outer ref field (not dynamic)
      (s instanceof AbstractCall   call) ? outerClazz.actualClazzes(call,null)[1].isRef()  :
      new Object() { { if (true) throw new Error("accessIsDynamic found unexpected Expr."); } } == null /* Java is ugly... */;

    return res;
  }


  /**
   * Is this call only to check preconditions.
   *
   * This is a bit of a hack for calls to parent features in inherits clauses:
   * These calls are inlined, so the backend does not need to take care.  Only
   * the precondition must be executed explicitly, so there remains a call to
   * the parent feature with callPreconditionOnly() returning true.
   *
   * The result of a the call in this case is unit.
   *
   * @param cl index of clazz containing the call
   *
   * @param c code block containing the call
   *
   * @param ix index of the call
   *
   * @return true if only the precondition should be executed.
   */
  public boolean callPreconditionOnly(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call);

    var call = (AbstractCall) _codeIds.get(c).get(ix);
    return call.isInheritanceCall();
  }


  /**
   * Get the target (outer) clazz of a feature access
   *
   * @param cl index of clazz containing the access
   *
   * @param c code block containing the access
   *
   * @param ix index of the access
   *
   * @return index of the static outer clazz of the accessed feature.
   */
  public int accessTargetClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Assign ||
       codeAt(c, ix) == ExprKind.Call  );

    var outerClazz = clazz(cl);
    var s = _codeIds.get(c).get(ix);
    var tclazz =
      (s instanceof AbstractAssign ass ) ? outerClazz.actualClazzes(ass, null)[0] : // NYI: This should be the same as assignedField._outer
      (s instanceof Clazz          arg ) ? outerClazz : // assignment to arg field in inherits call, so outer clazz is current instance
      (s instanceof AbstractCall   call) ? outerClazz.actualClazzes(call, null)[1] :
      (Clazz) (Object) new Object() { { if (true) throw new Error("accessTargetClazz found unexpected Expr."); } } /* Java is ugly... */;

    return id(tclazz);
  }


  public int fieldIndex(int field)
  {
    return clazz(field).fieldIndex();
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
   * @param c code block containing the constant
   *
   * @param ix index of the constant
   */
  public int constClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Const);

    var cc = clazz(cl);
    var ac = (AbstractConstant) _codeIds.get(c).get(ix);
    var acl = cc.actualClazzes(ac.origin(), null);
    // origin might be AbstractConstant, AbstractCall or InlineArray.  In all
    // cases, the clazz of the result is the first actual clazz:
    var clazz = acl[0];
    return id(clazz);
  }


  /**
   * For an intermediate command of type ExprKind.Const, return the constant
   * data using little endian encoding, i.e, 0x12345678 -> { 0x78, 0x56, 0x34, 0x12 }.
   */
  public byte[] constData(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Const);

    var ic = _codeIds.get(c).get(ix);
    return ((AbstractConstant) ic).data();
  }


  /**
   * For a match expression, get the static clazz of the subject.
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

    var cc = clazz(cl);
    var s = _codeIds.get(c).get(ix);
    Clazz ss = cc.actualClazzes((Expr) s, null)[0];
    return id(ss);
  }


  /**
   * For a match expression, get the field of a given case
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

    var cc = clazz(cl);
    var s = _codeIds.get(c).get(ix);
    int result = -1; // no field for If
    if (s instanceof AbstractMatch m)
      {
        var mc = m.cases().get(cix);
        var f = mc.field();
        var fc = f != null && Clazzes.isUsed(f) ? cc.actualClazzes(mc, null)[0] : null;
        result = fc != null ? id(fc) : -1;
      }
    return result;
  }


  /**
   * For a match expression, get the tags matched by a given case
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

    var cc = clazz(cl);
    var s = _codeIds.get(c).get(ix);
    int[] result;
    if (s instanceof If)
      {
        result = new int[] { cix == 0 ? 1 : 0 };
      }
    else
      {
        var match = (AbstractMatch) s;
        var mc = match.cases().get(cix);
        var ts = mc.types();
        var f = mc.field();
        int nt = f != null ? 1 : ts.size();
        var resultL = new List<Integer>();
        int tag = 0;
        for (var cg : match.subject().type().choiceGenerics())
          {
            for (int tix = 0; tix < nt; tix++)
              {
                var t = f != null ? f.resultType() : ts.get(tix);
                if (t.isDirectlyAssignableFrom(cg))
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
    if(POSTCONDITIONS) ensure
      (result.length > 0);
    return result;
  }


  /**
   * For a match expression, get the code associated with a given case
   *
   * @param c code block containing the match
   *
   * @param ix index of the match
   *
   * @paramc cix index of the case in the match
   *
   * @return code block for the case
   */
  public int matchCaseCode(int c, int ix, int cix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Match,
       0 <= cix && cix <= matchCaseCount(c, ix));

    var s = _codeIds.get(c).get(ix+1+cix);
    return ((NumLiteral)s).intValue().intValueExact();
  }


  /**
   * For a clazz that is an heir of 'Function', find the corresponding inner
   * clazz for 'call'.  This is used for code generation of intrinsic
   * 'abortable' that has to create code to call 'call'.
   *
   * @param cl index of a clazz that is an heir of 'Function'.
   *
   * @return the index of the requested `Function.call` method's clazz.
   */
  public int lookupCall(int cl)
  {
    return lookup(cl, Types.resolved.f_function_call);
  }


  /**
   * For a clazz of concur.atomic, lookup the inner clazz of the value field.
   *
   * @param cl index of a clazz representing cl's value field
   *
   * @return the index of the requested `concur.atomic.value` field's clazz.
   */
  public int lookupAtomicValue(int cl)
  {
    return lookup(cl, Types.resolved.f_concur_atomic_v);
  }


  /**
   * For a clazz of array, lookup the inner clazz of the internal_array field.
   *
   * @param cl index of a clazz `array T` for some type parameter `T`
   *
   * @return the index of the requested `array.internal_array` field's clazz.
   */
  public int lookup_array_internal_array(int cl)
  {
    return lookup(cl, Types.resolved.f_array_internal_array);
  }


  /**
   * For a clazz of fuzion.sys.internal_array, lookup the inner clazz of the
   * data field.
   *
   * @param cl index of a clazz `fuzion.sys.internal_array T` for some type parameter `T`
   *
   * @return the index of the requested `fuzion.sys.internal_array.data` field's clazz.
   */
  public int lookup_fuzion_sys_internal_array_data(int cl)
  {
    return lookup(cl, Types.resolved.f_fuzion_sys_array_data);
  }


  /**
   * For a clazz of fuzion.sys.internal_array, lookup the inner clazz of the
   * length field.
   *
   * @param cl index of a clazz `fuzion.sys.internal_array T` for some type parameter `T`
   *
   * @return the index of the requested `fuzion.sys.internal_array.length` field's clazz.
   */
  public int lookup_fuzion_sys_internal_array_length(int cl)
  {
    return lookup(cl, Types.resolved.f_fuzion_sys_array_length);
  }


  /**
   * Internal helper for lookup_* methods.
   *
   * @param cl index of the outer clazz for the lookup
   *
   * @param f the feature we look for
   *
   * @return the index of the requested inner clazz.
   */
  private int lookup(int cl, AbstractFeature f)
  {
    var cc = clazz(cl);
    var ic = cc.lookup(f);
    return id(ic);
  }


  /**
   * Get a string representation of the expr at the given index in given code
   * block.  Useful for debugging.
   *
   * @param cl index of the clazz containing the code block.
   *
   * @param c the code block
   *
   * @param ix an index within the code block
   */
  public String codeAtAsString(int cl, int c, int ix)
  {
    return switch (codeAt(c,ix))
      {
      case AdrOf   -> "AdrOf";
      case Assign  -> "Assign to " + clazzAsString(accessedClazz     (cl, c, ix));
      case Box     -> "Box "       + clazzAsString(boxValueClazz     (cl, c, ix)) + " => " + clazzAsString(boxResultClazz  (cl, c, ix));
      case Call    -> "Call to "   + clazzAsString(accessedClazz     (cl, c, ix));
      case Current -> "Current";
      case Comment -> "Comment: " + comment(c, ix);
      case Const   -> "Const of type " + clazzAsString(constClazz(cl, c, ix));
      case Match   -> {
                        var sb = new StringBuilder("Match");
                        for (var cix = 0; cix < matchCaseCount(c, ix); cix++)
                          {
                            var f = matchCaseField(cl, c, ix, cix);
                            sb.append(" " + cix + (f == -1 ? "" : "("+clazzAsString(clazzResultClazz(f))+")") + "=>" + matchCaseCode(c, ix, cix));
                          }
                        yield sb.toString();
                      }
      case Tag     -> "Tag";
      case Env     -> "Env";
      case Pop     -> "Pop";
      case Unit    -> "Unit";
      case InlineArray -> "InlineArray";
      };
  }



  /**
   * Get the source code position of an expr at the given index if it is available.
   *
   * @param c the code block
   *
   * @param ix an index within the code block
   *
   * @return the source code position or null if not available.
   */
  public SourcePosition codeAtAsPos(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0, withinCode(c, ix));

    var e = _codeIds.get(c).get(ix);
    return (e instanceof Expr expr) ? expr.pos() :
           (e instanceof Clazz z) ? z._type.declarationPos()  /* implicit assignment to argument field */
                                  : null;
  }


  /**
   * Print the contents of the given code block to System.out, for debugging.
   *
   * @param cl index of the clazz containing the code block.
   *
   * @param c the code block
   */
  public void dumpCode(int cl, int c)
  {
    dumpCode(cl, c, null);
  }


  /**
   * Print the contents of the given code block to System.out, for debugging.
   *
   * In case printed != null, recursively print all successor code blocks for
   * Match expressions and add their ids to printed, unless they has been added
   * already.
   *
   * @param cl index of the clazz containing the code block.
   *
   * @param c the code block
   *
   * @param printed set of code blocks that had already been printed.
   */
  private void dumpCode(int cl, int c, TreeSet<Integer> printed)
  {
    if (printed != null)
      {
        printed.add(c);
      }
    for (var ix = 0; withinCode(c, ix); ix = ix + codeSizeAt(c, ix))
      {
        System.out.printf("%d.%4d: %s\n", c, ix, codeAtAsString(cl, c, ix));
        if (printed != null)
          {
            switch (codeAt(c,ix))
              {
              case Match:
                for (var cix = 0; cix < matchCaseCount(c, ix); cix++)
                  {
                    var mc = matchCaseCode(c, ix, cix);
                    if (!printed.contains(mc))
                      {
                        dumpCode(cl, mc, printed);
                      }
                  }
                break;
              default: break;
              }
          }
      }
  }


  /**
   * Print the code of the given routine.
   *
   * @param cl index of the clazz.
   */
  public void dumpCode(int cl)
  {
    if (PRECONDITIONS) require
      (clazzKind(cl) == FeatureKind.Routine);

    System.out.println("Code for " + clazzAsString(cl) + (cl == mainClazzId() ? " *** main *** " : ""));
    dumpCode(cl, clazzCode(cl), new TreeSet<>());
  }


  /**
   * Print the code of all routines
   */
  public void dumpCode()
  {
    addClasses();
    _clazzIds.ints().forEach(cl ->
      {
        switch (clazzKind(cl))
          {
          case Routine: dumpCode(cl); break;
          default: break;
          }
      });
  }


  /**
   * For a given index 'ix' into the code block 'c', go 'delta' expressions
   * further or back (in case 'delta < 0').
   *
   * @param c the code block
   *
   * @param ix an index in c
   *
   * @param delta the number of instructions to go forward or back.
   */
  public int codeIndex(int c, int ix, int delta)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix));

    while (delta > 0)
      {
        ix = ix + codeSizeAt(c, ix);
        delta--;
      }
    if (delta < 0)
      {
        ix = codeIndex2(c, 0, ix, delta);
      }
    return ix;
  }


  /**
   * Helper routine for codeIndex to recursively find the index of expression
   * 'n' before expression at 'ix' where 'n == -delta' and 'delta < 0'.
   *
   * NYI: Performance: This requires time 'O(codeSize(c))', so using this
   * quickly results in quadratic performance!
   *
   * @param c the code block
   *
   * @param i current index in c, starting at 0.
   *
   * @param ix an index in c
   *
   * @param delta the negative number of instructions to go back.
   *
   * @return the index of the expression 'n' expressions before 'ix', or a
   * negative value '-m' if that instruction can be found 'm' recursive calls up.
   */
  private int codeIndex2(int c, int i, int ix, int delta)
  {
    if (i == ix)
      {
        return delta;
      }
    else
      {
        var r = codeIndex2(c, i + codeSizeAt(c, i), ix, delta);
        if (r < -1)
          {
            return r + 1;
          }
        else if (r == -1)
          {
            return i;
          }
        else
          {
            return r;
          }
      }
  }


  /**
   * Helper routine to go back in the code jumping over the whole previous
   * expression. Say you have the code
   *
   *   0: const 1
   *   1: current
   *   2: call field 'n'
   *   3: current
   *   4: call field 'm'
   *   5: const 2
   *   6: call add
   *   7: sub
   *   8: mul
   *
   * Then 'skip(cl, c, 6)' is 2 (popping 'add current.m 2'), while 'skip(cl, c,
   * 2)' is 0 (popping 'current.n').
   *
   * 'skip(cl, c, 7)' will result in 7, while 'skip(cl, c, 8)' will result in an
   * error since there is no expression before 'mul 1 (sub current.n (add
   * current.m 2))'.
   *
   * @param cl index of the clazz containing the code block.
   *
   * @param c the code block
   *
   * @param ix an index in c
   */
  public int skipBack(int cl, int c, int ix)
  {
    return switch (codeAt(c, ix))
      {
      case AdrOf   -> skipBack(cl, c, codeIndex(c, ix, -1));
      case Assign  ->
        {
          var tc = accessTargetClazz(cl, c, ix);
          ix = skipBack(cl, c, codeIndex(c, ix, -1));
          if (tc != clazzUniverse())
            {
              ix = skipBack(cl, c, ix);
            }
          yield ix;
        }
      case Box     -> skipBack(cl, c, codeIndex(c, ix, -1));
      case Call    ->
        {
          var tc = accessTargetClazz(cl, c, ix);
          var cc = accessedClazz(cl, c, ix);
          var ac = clazzArgCount(cc);
          ix = codeIndex(c, ix, -1);
          for (var i = 0; i < ac; i++)
            {
              var acl = clazzArgClazz(cc, ac-1-i);
              if (clazzResultClazz(acl) != clazzUniverse())
                {
                  ix = skipBack(cl, c, ix);
                }
            }
          if (tc != clazzUniverse())
            {
              ix = skipBack(cl, c, ix);
            }
          yield ix;
        }
      case Current -> codeIndex(c, ix, -1);
      case Comment -> skipBack(cl, c, codeIndex(c, ix, -1));
      case Const   -> codeIndex(c, ix, -1);
      case Match   ->
        {
          ix = codeIndex(c, ix, -1);
          ix = skipBack(cl, c, ix);
          yield ix;
        }
      case Tag     -> skipBack(cl, c, codeIndex(c, ix, -1));
      case Env     -> codeIndex(c, ix, -1);
      case Pop     -> skipBack(cl, c, codeIndex(c, ix, -1));
      case Unit    -> codeIndex(c, ix, -1);
      case InlineArray -> { check(false); yield codeIndex(c, ix, -1); }
      };
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
  public boolean isEffect(int cl)
  {
    if (PRECONDITIONS) require
      (clazzKind(cl) == FeatureKind.Intrinsic);

    return switch(clazzIntrinsicName(cl))
      {
      case "effect.replace",
           "effect.default",
           "effect.abortable",
           "effect.abort0" -> true;
      default -> false;
      };
  }



  /**
   * For an intrinstic in effect that changes the effect in the
   * current environment, return the type of the environment.  This type is used
   * to distinguish different environments.
   *
   * @param cl the id of the intrinsic clazz
   *
   * @return the type of the outer feature of cl
   */
  public int effectType(int cl)
  {
    if (PRECONDITIONS) require
      (isEffect(cl));

    var or = clazzOuterRef(cl);
    return clazzResultClazz(or);
  }


  /**
   * Test is a given clazz is not -1 and stores data.
   *
   * @param cl the clazz defining a type, may be -1
   *
   * @return true if cl != -1 and not unit or void type.
   */
  public boolean hasData(int cl)
  {
    return cl != -1 &&
      !clazzIsUnitType(cl) &&
      !clazzIsVoidType(cl) &&
      cl != clazzUniverse();
  }


  /**
   * Does this clazzes contract include any preconditions?
   */
  public boolean hasPrecondition(int cl)
  {
    return clazzContract(cl, FUIR.ContractKind.Pre, 0) != -1;
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
        || o instanceof AbstractConstant
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
      ia.elementType().compareTo(Types.resolved.t_void) != 0 &&
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


  /**
   * the clazz of the elements of the array
   *
   * @param constCl, e.g. `array (tuple i32 codepoint)`
   *
   * @return e.g. `tuple i32 codepoint`
   */
  public int inlineArrayElementClazz(int constCl)
  {
    if (PRECONDITIONS) require
      (clazzIsArray(constCl));

    var result = Clazzes.clazz(clazz(constCl)._type.generics().get(0))._idInFUIR;

    if (POSTCONDITIONS) ensure
      (result >= 0);

    return result;
  }


  /**
   * Is `constCl` an array?
   */
  public boolean clazzIsArray(int constCl)
  {
    return clazz(constCl)._type.featureOfType() == Types.resolved.f_array;
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
  private ByteBuffer deserializeClazz(int cl, ByteBuffer bb)
  {
    return switch (getSpecialId(cl))
      {
      case c_Const_String, c_String :
        var len = bb.duplicate().getInt();
        yield bb.slice(bb.position(), 4+len);
      case c_bool :
        yield bb.slice(bb.position(), 1);
      case c_i8, c_i16, c_i32, c_i64, c_u8, c_u16, c_u32, c_u64, c_f32, c_f64 :
        var bytes = bb.duplicate().getInt();
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
    var bbb = bb.duplicate();
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
  public byte[] deseralizeConst(int cl, ByteBuffer bb)
  {
    var elBytes = deserializeClazz(cl, bb.duplicate());
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
    var bbb = bb.duplicate();
    var elCount = bbb.getInt();
    var elBytes = 0;
    for (int i = 0; i < elCount; i++)
      {
        elBytes += deseralizeConst(elementClazz, bbb).length;
      }
    return bb.slice(bb.position(), 4+elBytes);
  }


  public int clazzOuterRefResultClazz(int cl)
  {
    var or = clazzOuterRef(cl);
    return or == -1
      ? -1
      : clazzResultClazz(or);
  }

}

/* end of file */
