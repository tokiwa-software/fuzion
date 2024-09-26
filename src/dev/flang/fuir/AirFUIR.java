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
import java.util.BitSet;
import java.util.TreeMap;
import java.util.TreeSet;


import dev.flang.air.AirErrors;
import dev.flang.air.Clazz;
import dev.flang.air.IClazzes;
import dev.flang.air.FeatureAndActuals;

import dev.flang.ast.AbstractAssign; // NYI: remove dependency
import dev.flang.ast.AbstractBlock; // NYI: remove dependency
import dev.flang.ast.AbstractCall; // NYI: remove dependency
import dev.flang.ast.AbstractFeature; // NYI: remove dependency
import dev.flang.ast.AbstractMatch; // NYI: remove dependency
import dev.flang.ast.Constant; // NYI: remove dependency
import dev.flang.ast.Context; // NYI: remove dependency
import dev.flang.ast.Box; // NYI: remove dependency
import dev.flang.ast.Env; // NYI: remove dependency
import dev.flang.ast.Expr; // NYI: remove dependency
import dev.flang.ast.InlineArray; // NYI: remove dependency
import dev.flang.ast.NumLiteral; // NYI: remove dependency
import dev.flang.ast.Tag; // NYI: remove dependency
import dev.flang.ast.Types; // NYI: remove dependency


import dev.flang.util.Errors;
import dev.flang.util.IntArray;
import dev.flang.util.List;
import dev.flang.util.Map2Int;
import dev.flang.util.MapComparable2Int;
import dev.flang.util.SourcePosition;


/**
 * The AirFUIR contains the intermediate representation of fuzion applications
 * based on the air package.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class AirFUIR extends FUIR
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Map used by getSpecialId() to quickly find the SpecialClazz corresponding
   * to a Clazz.
   */
  private TreeMap<Clazz, SpecialClazzes> SPECIAL_ID = new TreeMap<>();


  private int[] _specialClazzes = new int[SpecialClazzes.values().length];
  { Arrays.fill(_specialClazzes, -1);  }




  /**
   * For each site, this gives the clazz id of the clazz that contains the code at that site.
   */
  private final IntArray _siteClazzes;


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
   * Cached 'true' results of 'clazzNeedsCode'
   */
  BitSet _needsCode = new BitSet();


  /**
   * Cached 'false' results of 'clazzNeedsCode'
   */
  BitSet _doesNotNeedCode = new BitSet();


  /**
   * Cached results of accessedClazzesDynamic.
   */
  TreeMap<Integer,int[]> _accessedClazzesDynamicCache = new TreeMap<>();


  /**
   * the current instance of clazzes.
   */
  private IClazzes _clazzes;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create FUIR from given Clazz instance.
   *
   * @param main the main clazz.
   */
  public AirFUIR(Clazz main, IClazzes clazzes)
  {
    if (PRECONDITIONS) require
      (main != null,
       clazzes != null);

    _main = main;
    _clazzes = clazzes;
    _clazzIds = new MapComparable2Int<>(CLAZZ_BASE);
    _clazzCode = new TreeMap<>();
    _siteClazzes = new IntArray();
    _clazzes.findAllClasses(main());
    addClasses();

    SpecialClazzes c; Clazz cc;
    c = SpecialClazzes.c_i8;           cc = clazzes.i8();           if (cc != null) { _specialClazzes[c.ordinal()] = id(cc); SPECIAL_ID.put(cc, c); }
    c = SpecialClazzes.c_i16;          cc = clazzes.i16();          if (cc != null) { _specialClazzes[c.ordinal()] = id(cc); SPECIAL_ID.put(cc, c); }
    c = SpecialClazzes.c_i32;          cc = clazzes.i32();          if (cc != null) { _specialClazzes[c.ordinal()] = id(cc); SPECIAL_ID.put(cc, c); }
    c = SpecialClazzes.c_i64;          cc = clazzes.i64();          if (cc != null) { _specialClazzes[c.ordinal()] = id(cc); SPECIAL_ID.put(cc, c); }
    c = SpecialClazzes.c_u8;           cc = clazzes.u8();           if (cc != null) { _specialClazzes[c.ordinal()] = id(cc); SPECIAL_ID.put(cc, c); }
    c = SpecialClazzes.c_u16;          cc = clazzes.u16();          if (cc != null) { _specialClazzes[c.ordinal()] = id(cc); SPECIAL_ID.put(cc, c); }
    c = SpecialClazzes.c_u32;          cc = clazzes.u32();          if (cc != null) { _specialClazzes[c.ordinal()] = id(cc); SPECIAL_ID.put(cc, c); }
    c = SpecialClazzes.c_u64;          cc = clazzes.u64();          if (cc != null) { _specialClazzes[c.ordinal()] = id(cc); SPECIAL_ID.put(cc, c); }
    c = SpecialClazzes.c_f32;          cc = clazzes.f32();          if (cc != null) { _specialClazzes[c.ordinal()] = id(cc); SPECIAL_ID.put(cc, c); }
    c = SpecialClazzes.c_f64;          cc = clazzes.f64();          if (cc != null) { _specialClazzes[c.ordinal()] = id(cc); SPECIAL_ID.put(cc, c); }
    c = SpecialClazzes.c_bool;         cc = clazzes.bool();         if (cc != null) { _specialClazzes[c.ordinal()] = id(cc); SPECIAL_ID.put(cc, c); }
    c = SpecialClazzes.c_TRUE;         cc = clazzes.c_TRUE();       if (cc != null) { _specialClazzes[c.ordinal()] = id(cc); SPECIAL_ID.put(cc, c); }
    c = SpecialClazzes.c_FALSE;        cc = clazzes.c_FALSE();      if (cc != null) { _specialClazzes[c.ordinal()] = id(cc); SPECIAL_ID.put(cc, c); }
    c = SpecialClazzes.c_Const_String; cc = clazzes.Const_String(); if (cc != null) { _specialClazzes[c.ordinal()] = id(cc); SPECIAL_ID.put(cc, c); }
    c = SpecialClazzes.c_String;       cc = clazzes.String();       if (cc != null) { _specialClazzes[c.ordinal()] = id(cc); SPECIAL_ID.put(cc, c); }
    c = SpecialClazzes.c_sys_ptr;      cc = clazzes.fuzionSysPtr(); if (cc != null) { _specialClazzes[c.ordinal()] = id(cc); SPECIAL_ID.put(cc, c); }
    c = SpecialClazzes.c_unit;         cc = clazzes.c_unit();       if (cc != null) { _specialClazzes[c.ordinal()] = id(cc); SPECIAL_ID.put(cc, c); }
  }


  /**
   * Clone this FUIR such that modifications can be made by optimizers.  A heir
   * of FUIR can use this to redefine methods.
   *
   * @param original the original FUIR instance that we are cloning.
   */
  public AirFUIR(AirFUIR original)
  {
    super(original);
    _main = original._main;
    _clazzes = original._clazzes;
    _clazzIds = original._clazzIds;
    _clazzCode = original._clazzCode;
    _siteClazzes = original._siteClazzes;
    _specialClazzes = original._specialClazzes;
    SPECIAL_ID = original.SPECIAL_ID;
  }


  /*-----------------------------  methods  -----------------------------*/


  private Clazz main()
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
        for (var cl : _clazzes.all())
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

  @Override
  public int firstClazz()
  {
    return CLAZZ_BASE;
  }

  @Override
  public int lastClazz()
  {
    return CLAZZ_BASE + _clazzIds.size() - 1;
  }

  @Override
  public int mainClazzId()
  {
    return id(_main);
  }


  @Override
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
  @Override
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
  @Override
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
  @Override
  public int clazzChoice(int cl, int i)
  {
    if (PRECONDITIONS) require
      (i >= 0 && i < clazzNumChoices(cl));

    var cc = clazz(cl);
    var cg = cc.choiceGenerics().get(i);
    var res = cg.isRef()          ||
              cg.isInstantiated()    ? cg
                                     : _clazzes.c_void();
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
  @Override
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
  @Override
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
  @Override
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
  @Override
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
   * @return clazz id of cl's result
   */
  @Override
  public int clazzResultClazz(int cl)
  {
    if (PRECONDITIONS) require
      (clazzKind(cl) != FeatureKind.Choice);

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
  @Override
  public byte[] clazzTypeName(int cl)
  {
    var cc = clazz(cl);
    return cc.typeName().getBytes(StandardCharsets.UTF_8);
  }


  @Override
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

  @Override
  public String clazzBaseName(int cl)
  {
    var cc = clazz(cl);
    var res = cc.feature().featureName().baseName();
    res = res + cc._type.generics()
      .toString(" ", " ", "", t -> t.asStringWrapped(false));
    return res;
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
    var cc = clazz(cl);
    return cc.feature().qualifiedName();
  }


  /**
   * Is the given clazz a ref clazz?
   *
   * @return true for non-value-type clazzes
   */
  @Override
  public boolean clazzIsRef(int cl)
  {
    return clazz(cl).isRef();
  }


  /**
   * Is the given clazz a ref clazz that contains a boxed value type?
   *
   * @return true for boxed value-type clazz
   */
  @Override
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
  @Override
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
  @Override
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
  @Override
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
  @Override
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
  @Override
  public boolean clazzIsChoice(int cl)
  {
    return clazz(cl).isChoice();
  }


  /**
   * Get the outer clazz of the given clazz.
   *
   * @param cl a clazz id
   *
   * @return clazz id of cl's outer clazz, NO_CLAZZ if cl is universe.
   */
  @Override
  public int clazzOuterClazz(int cl)
  {
    var o = clazz(cl)._outer;
    return o == null ? NO_CLAZZ : id(o);
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
  @Override
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
  @Override
  public int clazzAny()
  {
    return id(_clazzes.Any());
  }


  /**
   * Get the id of clazz universe.
   *
   * @return clazz id of clazz universe
   */
  @Override
  public int clazzUniverse()
  {
    return id(_clazzes.universe());
  }



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
    var cc = clazz(cl);
    var result = SPECIAL_ID.get(cc);
    return result == null ? SpecialClazzes.c_NOT_FOUND : result;
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
    return cl == clazz(c);
  }


  /**
   * String representation of clazz, for creation of unique type names.
   *
   * @param cl a clazz id.
   */
  @Override
  public String clazzAsString(int cl)
  {
    return cl == -1 || cl == NO_CLAZZ
      ? "-- no clazz --"
      : clazz(cl)._type.asString();
  }

  /**
   * human readable String representation of clazz, for stack traces and debugging.
   *
   * @param cl a clazz id.
   */
  @Override
  public String clazzAsStringHuman(int cl)
  {
    return cl == -1
      ? "-- no clazz --"
      : clazz(cl)._type.asString(true);
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
   * Is there just one single value of this class, so this type is essentially a
   * C/Java `void` type?
   *
   * NOTE: This is false for Fuzion's `void` type!
   */
  @Override
  public boolean clazzIsUnitType(int cl)
  {
    var cc = clazz(cl);
    return cc.isUnitType();
  }


  /**
   * Is this a void type, i.e., values of this clazz do not exist.
   */
  @Override
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
   * @return id of cl's result field or NO_CLAZZ if cl has no result field (NYI:
   * or a result field that contains no data)
   */
  @Override
  public int clazzResultField(int cl)
  {
    var cc = clazz(cl);
    var rf = cc.resultField();
    return rf == null ? NO_CLAZZ : id(rf);
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
  @Override
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
        var pf = p.calledFeature();
        var of = pf.outerRef();
        var or = (of == null) ? null : (Clazz) cc._inner.get(new FeatureAndActuals(of, new List<>()));  // NYI: ugly cast
        var needsOuterRef = (or != null && !or.resultClazz().isUnitType());
        toStack(code, p.target(), !needsOuterRef /* dump result if not needed */);
        if (needsOuterRef)
          {
            code.add(ExprKind.Current);
            code.add(or);  // field clazz means assignment to field
          }
        if (CHECKS) check
          (p.actuals().size() == p.calledFeature().valueArguments().size());
        var argFields = cc._parentCallArgFields.get(p.globalIndex());
        for (var i = 0; i < p.actuals().size(); i++)
          {
            var a = p.actuals().get(i);
            toStack(code, a);
            code.add(ExprKind.Current);
            // Field clazz means assign value to that field
            code.add(argFields[i]);
          }
        addCode(cc, code, p.calledFeature());
      }
    toStack(code, ff.code());
  }


  /**
   * This has to be called after `super.addCode(List)` was called to record the
   * clazz for all sites of the newly added code.
   *
   * @param cl the clazz id of the clazz that contains the new code
   */
  private void recordClazzForSitesOfRecentlyAddedCode(int cl)
  {
    if (PRECONDITIONS) require
      (_siteClazzes.size() < _allCode.size());

    while (_siteClazzes.size() < _allCode.size())
      {
        _siteClazzes.add(cl);
      }
  }


  /**
   * Get access to the code of a clazz of kind Routine
   *
   * @param cl a clazz id
   *
   * @return a code id referring to cl's code
   */
  @Override
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
        res = addCode(code);
        recordClazzForSitesOfRecentlyAddedCode(cl);
        _clazzCode.put(cl, res);
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
  @Override
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
          case Abstract -> false;
          case Choice -> false;
          case Intrinsic, Routine, Field, Native ->
            (cc.isInstantiated() || cc.feature().isOuterRef())
            && cc != _clazzes.Const_String()
            && !cc.isAbsurd()
            && !cc.isBoxed();
          };
        (result ? _needsCode : _doesNotNeedCode).set(cl - CLAZZ_BASE);
        return result;
      }
  }


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
    return switch (clazzKind(cl))
      {
      case Abstract  -> LifeTime.Undefined;
      case Choice    -> LifeTime.Undefined;
      case Intrinsic -> LifeTime.Undefined;
      case Field     -> LifeTime.Call;
      case Routine   -> LifeTime.Unknown;
      case Native    -> LifeTime.Undefined;
      };
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
    return clazz(cl).feature().isOuterRef();
  }


  /**
   * Get the id of clazz Const_String
   *
   * @return the id of Const_String or -1 if that clazz was not created.
   */
  @Override
  public int clazz_Const_String()
  {
    var cc = _clazzes.Const_String();
    return cc == null ? -1 : id(cc);
  }


  /**
   * Get the id of clazz Const_String.utf8_data
   *
   * @return the id of Const_String.utf8_data or -1 if that clazz was not created.
   */
  @Override
  public int clazz_Const_String_utf8_data()
  {
    var cc = _clazzes.Const_String_utf8_data();
    return cc == null ? -1 : id(cc);
  }


  /**
   * Get the id of clazz `array u8`
   *
   * @return the id of Const_String.array or -1 if that clazz was not created.
   */
  @Override
  public int clazz_array_u8()
  {
    return clazzResultClazz(clazz_Const_String_utf8_data());
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>
   *
   * @return the id of fuzion.sys.array<u8> or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8()
  {
    var cc = _clazzes.fuzionSysArray_u8();
    return cc == null ? -1 : id(cc);
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>.data
   *
   * @return the id of fuzion.sys.array<u8>.data or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8_data()
  {
    var cc = _clazzes.fuzionSysArray_u8_data();
    return cc == null ? -1 : id(cc);
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>.length
   *
   * @return the id of fuzion.sys.array<u8>.length or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8_length()
  {
    var cc = _clazzes.fuzionSysArray_u8_length();
    return cc == null ? -1 : id(cc);
  }


  /**
   * Get the id of clazz fuzion.java.Java_Object
   *
   * @return the id of fuzion.java.Java_Object or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionJavaObject()
  {
    var cc = _clazzes.fuzionJavaObject();
    return cc == null ? -1 : id(cc);
  }


  /**
   * Get the id of clazz fuzion.java.Java_Object.Java_Ref
   *
   * @return the id of fuzion.java.Java_Object.Java_Ref or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionJavaObject_Ref()
  {
    var cc = _clazzes.fuzionJavaObject_Ref();
    return cc == null ? -1 : id(cc);
  }


  /**
   * Get the id of clazz error
   *
   * @return the id of error or -1 if that clazz was not created.
   */
  @Override
  public int clazz_error()
  {
    var cc = _clazzes.c_error();
    return cc == null ? -1 : id(cc);
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
    return lookup(cl, Types.resolved.f_fuzion_Java_Object_Ref);
  }


  /**
   * Get the id of the given special clazz.
   *
   * @param c the id of clazz c or -1 if that clazz was not created.
   */
  @Override
  public int clazz(SpecialClazzes c)
  {
    return _specialClazzes[c.ordinal()];
  }


  /*--------------------------  accessing code  -------------------------*/


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
      (s >= SITE_BASE);

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
        res = clazzAsString(cl) + "(" + clazzArgCount(cl) + " args) at " + s;
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
       withinCode(s));

    ExprKind result;
    var e = getExpr(s);
    if (e instanceof Clazz    )  /* Clazz represents the field we assign a value to */
      {
        result = ExprKind.Assign;
      }
    else
      {
        result = exprKind(e);
      }
    if (result == null)
      {
        Errors.fatal(sitePos(s),
                     "Expr `" + e.getClass() + "` not supported in FUIR.codeAt", "Expression class: " + e.getClass());
        result = ExprKind.Current; // keep javac from complaining.
      }
    return result;
  }


  @Override
  public int tagValueClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       withinCode(s),
       codeAt(s) == ExprKind.Tag);

    var cl = clazzAt(s);
    var outerClazz = clazz(cl);
    var t = (Tag) getExpr(s);
    var vcl = outerClazz.actualClazzes(t, null)[0];
    return vcl == null ? -1 : id(vcl);
  }

  @Override
  public int tagNewClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       withinCode(s),
       codeAt(s) == ExprKind.Tag);

    var cl = clazzAt(s);
    var outerClazz = clazz(cl);
    var t = (Tag) getExpr(s);
    var ncl = outerClazz.actualClazzes(t, null)[1];
    return ncl == null ? -1 : id(ncl);
  }

  @Override
  public int tagTagNum(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
      withinCode(s),
      codeAt(s) == ExprKind.Tag);

    return ((Tag) getExpr(s)).tagNum();
  }

  /**
   * For outer clazz cl with an Env instruction at site s, return the type of
   * the env value.
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
    var vcl = outerClazz.actualClazzes(v, null)[0];
    return vcl == null ? -1 : id(vcl);
  }

  @Override
  public int boxValueClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       withinCode(s),
       codeAt(s) == ExprKind.Box);

    var cl = clazzAt(s);
    var outerClazz = clazz(cl);
    var b = (Box) getExpr(s);
    Clazz vc = outerClazz.actualClazzes(b, null)[0];
    return id(vc);
  }

  @Override
  public int boxResultClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       withinCode(s),
       codeAt(s) == ExprKind.Box);

    var cl = clazzAt(s);
    var outerClazz = clazz(cl);
    var b = (Box) getExpr(s);
    Clazz rc = outerClazz.actualClazzes(b, null)[1];
    return id(rc);
  }


  /**
   * Get the code for a comment expression.  This is used for debugging.
   *
   * @param s site of the comment
   */
  @Override
  public String comment(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       withinCode(s),
       codeAt(s) == ExprKind.Comment);

    return (String) getExpr(s);
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
  public int accessedClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       withinCode(s),
       codeAt(s) == ExprKind.Call   ||
       codeAt(s) == ExprKind.Assign    );

    var cl = clazzAt(s);
    var outerClazz = clazz(cl);
    var e = getExpr(s);
    Clazz innerClazz =
      (e instanceof AbstractCall   call) ? outerClazz.actualClazzes(call, null)[0] :
      (e instanceof AbstractAssign a   ) ? outerClazz.actualClazzes(a   , null)[1] :
      (e instanceof Clazz          fld ) ? fld :
      (Clazz) (Object) new Object() { { if (true) throw new Error("accessedClazz found unexpected Expr " + (e == null ? e : e.getClass()) + "."); } } /* Java is ugly... */;

    return innerClazz == null ? -1 : id(innerClazz);
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
       withinCode(s),
       codeAt(s) == ExprKind.Assign    );

    var cl = clazzAt(s);
    var outerClazz = clazz(cl);
    var e = getExpr(s);
    var t =
      (e instanceof AbstractAssign a   ) ? outerClazz.actualClazzes(a, null)[2] :
      (e instanceof Clazz          fld ) ? fld.resultClazz() :
      (Clazz) (Object) new Object() { { if (true) throw new Error("assignedType found unexpected Expr " + (e == null ? e : e.getClass()) + "."); } } /* Java is ugly... */;

    return id(t);
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
       withinCode(s),
       codeAt(s) == ExprKind.Call   ||
       codeAt(s) == ExprKind.Assign    ,
       accessIsDynamic(s));

    var key = Integer.valueOf(s);
    var res = _accessedClazzesDynamicCache.get(key);
    if (res == null)
      {
        var cl = clazzAt(s);
        var outerClazz = clazz(cl);
        var e = getExpr(s);
        Clazz tclazz;
        AbstractFeature f;
        var typePars = AbstractCall.NO_GENERICS;

        if (e instanceof AbstractCall call)
          {
            f = call.calledFeature();
            tclazz   = outerClazz.actualClazzes(call, null)[1];
            typePars = outerClazz.actualGenerics(call.actualTypeParameters());
          }
        else if (e instanceof AbstractAssign ass)
          {
            var acl = outerClazz.actualClazzes(ass, null);
            var assignedField = acl[1];
            tclazz = acl[0];  // NYI: This should be the same as assignedField._outer
            f = assignedField.feature();
          }
        else if (e instanceof Clazz fld)
          {
            tclazz = (Clazz) fld._outer;
            f = fld.feature();
          }
        else
          {
            throw new Error("Unexpected expression in accessedClazzesDynamic, must be ExprKind.Call or ExprKind.Assign, is " +
                            codeAt(s) + " " + e.getClass() + " at " + sitePos(s).show());
          }
        var found = new TreeSet<Integer>();
        var result = new List<Integer>();
        var fa = new FeatureAndActuals(f, typePars);
        for (var clz : tclazz.heirs())
          {
            if (CHECKS) check
              (clz.isRef() == tclazz.isRef());

            var in = (Clazz) clz._inner.get(fa);
            if (in != null && clazzNeedsCode(id(in)))
              {
                var in_id  = id(in);
                var clz_id = id(clz);
                if (CHECKS) check
                  (in_id  != -1 &&
                   clz_id != -1    );
                if (found.add(clz_id))
                  {
                    result.add(clz_id);
                    result.add(in_id);
                  }
              }
          }

        res = new int[result.size()];
        for (int i = 0; i < res.length; i++)
          {
            res[i] = result.get(i);
          }
        _accessedClazzesDynamicCache.put(key,res);
      }
    return res;
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
       withinCode(s),
       codeAt(s) == ExprKind.Call   ||
       codeAt(s) == ExprKind.Assign    ,
       tclazz >= CLAZZ_BASE &&
       tclazz < CLAZZ_BASE  + _clazzes.all().size());

    int innerClazz;
    if (accessIsDynamic(s))
      {
        innerClazz = NO_CLAZZ;
        var ccs = accessedClazzes(s);
        for (var i = 0; i < ccs.length; i += 2)
          {
            var tt = ccs[i+0];
            var cc = ccs[i+1];
            if (tt == tclazz)
              {
                innerClazz = cc;
              }
          }
      }
    else
      {
        innerClazz = accessedClazz(s);
        if (CHECKS) check
          (tclazz == clazzOuterClazz(innerClazz));
        innerClazz = switch (clazzKind(innerClazz))
          {
          case Routine, Intrinsic, Native, Field -> innerClazz;
          case Abstract, Choice -> NO_CLAZZ;
          };
      }
    return innerClazz;
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
       withinCode(s),
       codeAt(s) == ExprKind.Assign ||
       codeAt(s) == ExprKind.Call  );

    var cl = clazzAt(s);
    var outerClazz = clazz(cl);
    var e = getExpr(s);
    var res =
      (e instanceof AbstractAssign ass ) ? outerClazz.actualClazzes(ass, null)[0].isRef() : // NYI: This should be the same as assignedField._outer
      (e instanceof Clazz          arg ) ? outerClazz.isRef() && !arg.feature().isOuterRef() : // assignment to arg field in inherits call (dynamic if outerClazz is ref)
                                                                                       // or to outer ref field (not dynamic)
      (e instanceof AbstractCall   call) ? outerClazz.actualClazzes(call,null)[1].isRef()  :
      new Object() { { if (true) throw new Error("accessIsDynamic found unexpected Expr " + (e == null ? e : e.getClass()) + "."); } } == null /* Java is ugly... */;

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
       withinCode(s),
       codeAt(s) == ExprKind.Assign ||
       codeAt(s) == ExprKind.Call  );

    var cl = clazzAt(s);
    var outerClazz = clazz(cl);
    var e = getExpr(s);
    var tclazz =
      (e instanceof AbstractAssign ass ) ? outerClazz.actualClazzes(ass, null)[0] : // NYI: This should be the same as assignedField._outer
      (e instanceof Clazz          arg ) ? outerClazz : // assignment to arg field in inherits call, so outer clazz is current instance
      (e instanceof AbstractCall   call) ? outerClazz.actualClazzes(call, null)[1] :
      (Clazz) (Object) new Object() { { if (true) throw new Error("accessTargetClazz found unexpected Expr " + (e == null ? e : e.getClass()) + "."); } } /* Java is ugly... */;

    return id(tclazz);
  }


  @Override
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
   * @param s site of the constant
   */
  @Override
  public int constClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       withinCode(s),
       codeAt(s) == ExprKind.Const);

    var cl = clazzAt(s);
    var cc = clazz(cl);
    var ac = (Constant) getExpr(s);
    var acl = cc.actualClazzes(ac.origin(), null);
    // origin might be Constant, AbstractCall or InlineArray.  In all
    // cases, the clazz of the result is the first actual clazz:
    var clazz = acl[0];
    return id(clazz);
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
  public int matchStaticSubject(int s)
  {
    if (PRECONDITIONS) require
      (s >= 0,
       withinCode(s),
       codeAt(s) == ExprKind.Match);

    var cl = clazzAt(s);
    var cc = clazz(cl);
    var e = getExpr(s);
    Clazz ss = cc.actualClazzes((Expr) e, null)[0];
    return id(ss);
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
       withinCode(s),
       codeAt(s) == ExprKind.Match,
       0 <= cix && cix <= matchCaseCount(s));

    var cl = clazzAt(s);
    var cc = clazz(cl);
    var e = getExpr(s);
    int result = -1; // no field for If
    if (e instanceof AbstractMatch m)
      {
        var mc = m.cases().get(cix);
        var f = mc.field();
        var fc = f != null && _clazzes.isUsed(f) ? cc.actualClazzes(mc, null)[0] : null;
        result = fc != null ? id(fc) : -1;
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
       withinCode(s),
       codeAt(s) == ExprKind.Match,
       0 <= cix && cix <= matchCaseCount(s));

    var e = getExpr(s);
    int[] result;
    if (e instanceof AbstractMatch match)
      {
        var mc = match.cases().get(cix);
        var ts = mc.types();
        var f = mc.field();
        int nt = f != null ? 1 : ts.size();
        var resultL = new List<Integer>();
        int tag = 0;
        for (var cg : match.subject().type().choiceGenerics(Context.NONE /* NYI: CLEANUP: Context should no longer be needed during FUIR */))
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
        result = new int[resultL.size()];
        for (int i = 0; i < result.length; i++)
          {
            result[i] = resultL.get(i);
          }
      }
    else
      {
        throw new Error("match expr has wrong class " + (e == null ? e : e.getClass()));
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
       withinCode(s),
       codeAt(s) == ExprKind.Match,
       0 <= cix && cix <= matchCaseCount(s));

    var me = getExpr(s);
    var e = getExpr(s + 1 + cix);

    if (me instanceof AbstractMatch m &&
        m.subject() instanceof AbstractCall sc)
      {
        var c = m.cases().get(cix);
        if (sc.calledFeature() == Types.resolved.f_Type_infix_colon_true  && !c.types().stream().anyMatch(x->x.compareTo(Types.resolved.f_TRUE .selfType())==0) ||
            sc.calledFeature() == Types.resolved.f_Type_infix_colon_false && !c.types().stream().anyMatch(x->x.compareTo(Types.resolved.f_FALSE.selfType())==0)    )
          {
            return NO_SITE;
          }
        else if (sc.calledFeature() == Types.resolved.f_Type_infix_colon)
          {
            var innerClazz = clazz(clazzAt(s)).actualClazzes(sc, null)[0];
            var tclazz = innerClazz._outer;
            var T = innerClazz.actualGenerics()[0];
            var pos = T._type.constraintAssignableFrom(Context.NONE /* NYI: CLEANUP: Context should no longer be needed during FUIR */, tclazz._type.generics().get(0));
            if (pos  && !c.types().stream().anyMatch(x->x.compareTo(Types.resolved.f_TRUE .selfType())==0) ||
                !pos && !c.types().stream().anyMatch(x->x.compareTo(Types.resolved.f_FALSE.selfType())==0)    )
              {
                return NO_SITE;
              }
          }
      }

    return ((NumLiteral) e).intValue().intValueExact();
  }

  @Override
  public boolean withinCode(int s)
  {
    return (s != NO_SITE) && super.withinCode(s);
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
    return lookup(cl, Types.resolved.f_Function_call);
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
    return lookup(cl, Types.resolved.f_effect_static_finally);
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
    return lookup(cl, Types.resolved.f_concur_atomic_v);
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
  @Override
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
  @Override
  public int lookup_fuzion_sys_internal_array_length(int cl)
  {
    return lookup(cl, Types.resolved.f_fuzion_sys_array_length);
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
    return lookup(cl, Types.resolved.f_error_msg);
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
   * Helper for dumpCode and sitePos to create a label for given code block.
   *
   * @param c a code block;
   *
   * @return a String that can be used as a unique label for code block `c`.
   */
  private String label(int c)
  {
    return "l" + (c-SITE_BASE);
  }


  /**
   * Get a string representation of the expr at the given index in given code
   * block.  Useful for debugging.
   *
   * @param s site of an expression
   */
  @Override
  public String codeAtAsString(int s)
  {
    return switch (codeAt(s))
      {
      case Assign  -> "Assign " + clazzAsString(assignedType(s)) + " to " + clazzAsString(accessedClazz(s));
      case Box     -> "Box "       + clazzAsString(boxValueClazz(s)) + " => " + clazzAsString(boxResultClazz  (s));
      case Call    -> {
                        var sb = new StringBuilder("Call ");
                        var cc = accessedClazz(s);
                        sb.append(clazzAsStringWithArgsAndResult(cc));
                        yield sb.toString();
                       }
      case Current -> "Current";
      case Comment -> "Comment: " + comment(s);
      case Const   -> {
                        var data = constData(s);
                        var sb = new StringBuilder("Const of type ");
                        sb.append(clazzAsString(constClazz(s)));
                        for (var i = 0; i < Math.min(8, data.length); i++)
                          {
                            sb.append(String.format(" %02x", data[i] & 0xff));
                          }
                        yield sb.toString();
                      }
      case Match   -> {
                        var sb = new StringBuilder("Match");
                        for (var cix = 0; cix < matchCaseCount(s); cix++)
                          {
                            var f = matchCaseField(s, cix);
                            sb.append(" " + cix + (f == -1 ? "" : "("+clazzAsString(clazzResultClazz(f))+")") + "=>" + label(matchCaseCode(s, cix)));
                          }
                        yield sb.toString();
                      }
      case Tag     -> "Tag";
      case Env     -> "Env";
      case Pop     -> "Pop";
      };
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



  /**
   * Print the contents of the given code block to System.out, for debugging.
   *
   * @param cl index of the clazz containing the code block.
   *
   * @param c the code block
   */
  @Override
  public void dumpCode(int cl, int c)
  {
    String label = label(c) +  ":";
    for (var s = c; withinCode(s); s = s + codeSizeAt(s))
      {
        label = "";
        switch (codeAt(s))
          {
          case Match:
            var l = label(c) + "_" + (s-c);
            for (var cix = 0; cix < matchCaseCount(s); cix++)
              {
                var mc = matchCaseCode(s, cix);

                dumpCode(cl, mc);
                say("\tgoto " + l);
              }
            label = l + ":";
            break;
          default: break;
          }
      }
    if (label != "")
      {
        say(label);
      }
  }


  /**
   * Print the code of the given routine.
   *
   * @param cl index of the clazz.
   */
  @Override
  public void dumpCode(int cl)
  {
    if (PRECONDITIONS) require
      (clazzKind(cl) == FeatureKind.Routine);

    say("Code for " + clazzAsStringWithArgsAndResult(cl) + (cl == mainClazzId() ? " *** main *** " : ""));
    dumpCode(cl, clazzCode(cl));
  }


  /**
   * Print the code of all routines
   */
  @Override
  public void dumpCode()
  {
    _clazzIds.ints().forEach(cl ->
      {
        switch (clazzKind(cl))
          {
          case Routine: if (clazzNeedsCode(cl)) { dumpCode(cl); } break;
          default: break;
          }
      });
  }


  /**
   * For a given site 's', go 'delta' expressions further or back (in case
   * 'delta < 0').
   *
   * @param s a site
   *
   * @param delta the number of instructions to go forward or back.
   */
  @Override
  public int codeIndex(int s, int delta)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       withinCode(s));

    while (delta > 0)
      {
        s = s + codeSizeAt(s);
        delta--;
      }
    if (delta < 0)
      {
        s = codeIndex2(codeBlockStart(s), s, delta);
      }
    return s;
  }


  /**
   * Helper routine for codeIndex to recursively find the index of expression
   * 'n' before expression at 'ix' where 'n == -delta' and 'delta < 0'.
   *
   * NYI: Performance: This requires time 'O(codeSize(c))', so using this
   * quickly results in quadratic performance!
   *
   * @param si a site, our current position we are checking
   *
   * @param s a site we are looking for
   *
   * @param delta the negative number of instructions to go back.
   *
   * @return the site of the expression 'delta' expressions before 's', or a
   * negative value '-m' if that instruction can be found 'm' recursive calls up.
   */
  private int codeIndex2(int si, int s, int delta)
  {
    check
      (si >= SITE_BASE && s >= SITE_BASE); // this code uses negative results if site was not found yet, so better make sure a site is never negative!

    if (si == s)
      {
        return delta;  // found s, so result is -delta calls up
      }
    else
      {
        var r = codeIndex2(si + codeSizeAt(si), s, delta);
        return r <  -1 ? r + 1  // found s, position of s + delta is at least one call up
             : r == -1 ? si     // found s, position of s + delta is here!
             :           r;     // found s, pass on result
      }
  }


  /**
   * Helper routine to go back in the code jumping over the whole previous
   * expression. Say you have the code  -- NYI: This example is confusing and probably wrong --
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
   * Then 'skip(cl, 6)' is 2 (popping 'add current.m 2'), while 'skip(cl, 2)' is
   * 0 (popping 'current.n').
   *
   * 'skip(cl, 7)' will result in 7, while 'skip(cl, 8)' will result in an
   * error since there is no expression before 'mul 1 (sub current.n (add
   * current.m 2))'.
   *
   * @param s site to start skipping backwards from
   */
  @Override
  public int skipBack(int s)
  {
    return switch (codeAt(s))
      {
      case Assign  ->
        {
          var tc = accessTargetClazz(s);
          s = skipBack(codeIndex(s, -1));
          if (tc != clazzUniverse())
            {
              s = skipBack(s);
            }
          yield s;
        }
      case Box     -> skipBack(codeIndex(s, -1));
      case Call    ->
        {
          var tc = accessTargetClazz(s);
          var cc = accessedClazz(s);
          var ac = clazzArgCount(cc);
          s = codeIndex(s, -1);
          for (var i = 0; i < ac; i++)
            {
              if (clazzArgClazz(cc, ac-1-i) != clazzUniverse())
                {
                  s = skipBack(s);
                }
            }
          if (tc != clazzUniverse())
            {
              s = skipBack(s);
            }
          yield s;
        }
      case Current -> codeIndex(s, -1);
      case Comment -> skipBack(codeIndex(s, -1));
      case Const   -> codeIndex(s, -1);
      case Match   ->
        {
          s = codeIndex(s, -1);
          s = skipBack(s);
          yield s;
        }
      case Tag     -> skipBack(codeIndex(s, -1));
      case Env     -> codeIndex(s, -1);
      case Pop     -> skipBack(codeIndex(s, -1));
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
    return cl != -1 &&
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

    var result = id(_clazzes.clazz(clazz(constCl)._type.generics().get(0)));

    if (POSTCONDITIONS) ensure
      (result >= 0);

    return result;
  }

  /**
   * Check if the given clazz is a constructor.
   *
   * @param clazz a clazz id
   *
   * @return true if the clazz is a constructor, false otherwise
   */
  @Override
  public boolean isConstructor(int clazz)
  {
    // Intrinsic functions are not constructors even though they have no result field.
    return clazzResultField(clazz) == NO_CLAZZ && clazzKind(clazz) != FeatureKind.Intrinsic;
  }


  /**
   * Is `constCl` an array?
   */
  @Override
  public boolean clazzIsArray(int constCl)
  {
    return clazz(constCl)._type.feature() == Types.resolved.f_array;
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


  /**
   * Get the source file the clazz originates from.
   *
   * e.g. /fuzion/tests/hello/HelloWorld.fz, $FUZION/lib/panic.fz
   */
  @Override
  public String clazzSrcFile(int cl)
  {
    return this.clazz(cl)._type.declarationPos()._sourceFile._fileName.toString();
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
   * Get the position where the clazz is declared
   * in the source code.
   */
  @Override
  public SourcePosition declarationPos(int cl)
  {
    return clazz(cl)._type.declarationPos();
  }


  /*---------------------------------------------------------------------
   *
   * handling of abstract missing errors.
   *
   * NYI: This still uses AirErrors.abstractFeatureNotImplemented, which should
   * eventually be moved to DFA or somewhere else when DFA is joined with AIR
   * phase.
   */


  /**
   * tuple of clazz, called abstract features and location where the clazz was
   * instantiated.
   */
  record AbsMissing(Clazz clazz,
                    TreeSet<AbstractFeature> called,
                    SourcePosition instantiationPos,
                    String context)
  {
  };


  /**
   * Set of missing implementations of abstract features
   */
  TreeMap<Clazz, AbsMissing> _abstractMissing = new TreeMap<>();


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
  public void recordAbstractMissing(int cl, int f, int instantiationSite, String context)
  {
    var cc = clazz(cl);
    var cf = clazz(f);
    var r = _abstractMissing.computeIfAbsent(cc, ccc -> new AbsMissing(ccc, new TreeSet<>(), sitePos(instantiationSite), context));
    r.called.add(cf.feature());
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
      .forEach(r -> AirErrors.abstractFeatureNotImplemented(r.clazz.feature(),
                                                            r.called,
                                                            r.instantiationPos,
                                                            r.context,
                                                            _clazzes));
  }


}

/* end of file */
