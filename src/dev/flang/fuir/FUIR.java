/*

This file is part of the Fuzion language implementation.

The Fuzion language implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language imbooplementation is distributed in the hope that it will be
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

import java.nio.charset.StandardCharsets;

import java.util.BitSet;
import java.util.TreeMap;

import dev.flang.air.Clazz;
import dev.flang.air.Clazzes;

import dev.flang.ast.AbstractAssign; // NYI: remove dependency
import dev.flang.ast.AbstractBlock; // NYI: remove dependency
import dev.flang.ast.AbstractCall; // NYI: remove dependency
import dev.flang.ast.AbstractConstant; // NYI: remove dependency
import dev.flang.ast.AbstractFeature; // NYI: remove dependency
import dev.flang.ast.AbstractMatch; // NYI: remove dependency
import dev.flang.ast.BoolConst; // NYI: remove dependency
import dev.flang.ast.Box; // NYI: remove dependency
import dev.flang.ast.Call; // NYI: remove dependency
import dev.flang.ast.Current; // NYI: remove dependency
import dev.flang.ast.Env; // NYI: remove dependency
import dev.flang.ast.Expr; // NYI: remove dependency
import dev.flang.ast.If; // NYI: remove dependency
import dev.flang.ast.InlineArray; // NYI: remove dependency
import dev.flang.ast.NumLiteral; // NYI: remove dependency
import dev.flang.ast.Stmnt; // NYI: remove dependency
import dev.flang.ast.Tag; // NYI: remove dependency
import dev.flang.ast.Types; // NYI: remove dependency
import dev.flang.ast.Unbox; // NYI: remove dependency

import dev.flang.ir.IR;

import dev.flang.util.ANY;
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
    c_conststring { Clazz getIfCreated() { return Clazzes.conststring.getIfCreated(); } },
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
    _clazzIds = new MapComparable2Int(CLAZZ_BASE);
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
              (cl._type != Types.t_ERROR);

            if (cl._type != Types.t_ADDRESS)     // NYI: would be better to not create this dummy clazz in the first place
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
    if (PRECONDITIONS)
      require
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
   * @return the clazz id of the choice type, or -1 if the clazz is never
   * instantiated and hence does not need to be taken care for.
   *
   * NYI: Instead of returning -1 for non-instantiated value clazzes, it would
   * be much nicer if those clazzes would be removed completely from the IR or
   * replaced by someting obvious like 'void'.
   */
  public int clazzChoice(int cl, int i)
  {
    if (PRECONDITIONS)
      require
        (i >= 0 && i < clazzNumChoices(cl));

    var cc = clazz(cl);
    var cg = cc.choiceGenerics().get(i);
    return cg.isRef() || cg.isInstantiated() ? id(cg) : -1;
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
    var f = fc.feature();
    return f.isOuterRef() &&
      !fc.resultClazz().isRef() &&
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
      default         -> throw new Error ("Unexpected feature kind: "+ff.kind());
      };
  }

  public String clazzBaseName(int cl)
  {
    var cc = clazz(cl);
    var res = cc.feature().featureName().baseName();
    if (!cc._type.generics().isEmpty())
      {
        res = res + cc._type.generics().toString("<",",",">");
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
      (clazzKind(cl) == FeatureKind.Intrinsic);

    var cc = clazz(cl);
    return cc.feature().qualifiedName();
  }

  public boolean clazzIsRef(int cl)
  {
    return clazz(cl).isRef();
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

    var c = clazz(cl);
    var rc = c.argumentFields()[arg].resultClazz(); // or .fieldClazz()?
    return id(rc);
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
   * Get the choice tag field of a choice clazz
   *
   * @param cl a clazz id
   *
   * @return clazz id of cl's choice tag or -1 if cl is not a choice or does not
   * need a choice tag.
   */
  public int clazzChoiceTag(int cl)
  {
    var cc = clazz(cl);
    var ct = cc.choiceTag();
    return ct == null ? -1 : id(ct);
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
   * Is a clazz cl's clazzOuterRef() a value type that survives the call to
   * cl?  If this is the case, we need to heap allocate the outer ref.
   *
   * @param cl a clazz id
   *
   * @return true if cl's may be kept alive longer than through its original
   * constructor call since inner instances stay alive.
   */
  public boolean clazzOuterRefEscapes(int cl)
  {
    var cc = clazz(cl);
    var or = cc.outerRef();
    var cco = cc._outer;
    if (or == null || cco.isUnitType())
      {
        return false;
      }
    else
      {
        var rc = or.resultClazz();
        return !rc.isRef() && !rc.feature().isBuiltInPrimitive();
      }
  }


  /**
   * Get the id of clazz Object.
   *
   * @return clazz id of clazz Object
   */
  public int clazzObject()
  {
    return id(Clazzes.object.get());
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
   * Are values of this clazz all the same, so they are essentially C/Java void
   * values?
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
     the precondition and once for the inlinded call! See this example:

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
        var or = (of == null) ? null : (Clazz) cc._inner.get(of);  // NYI: ugly cast
        toStack(code, p.target());
        if (or != null && !or.resultClazz().isUnitType())
          {
            if (!or.resultClazz().isRef() &&
                !or.resultClazz().feature().isBuiltInPrimitive())
              {
                code.add(ExprKind.AdrOf);
              }
            code.add(ExprKind.Dup);
            code.add(ExprKind.Current);
            code.add(or);  // field clazz means assignment to field
          }
        if (CHECKS) check
          (p.actuals().size() == p.calledFeature().valueArguments().size());
        for (var i = 0; i < p.actuals().size(); i++)
          {
            var a = p.actuals().get(i);
            var f = pf.arguments().get(i);
            toStack(code, a);
            code.add(ExprKind.Current);
            // Field clazz means assign value to that field
            code.add((Clazz) cc.getRuntimeData(p._parentCallArgFieldIds + i));
          }
        addCode(cc, code, p.calledFeature());
      }
    toStack(code, ff.code());
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
        addCode(cc, code, ff);
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
       clazzKind(cl) == FeatureKind.Abstract     ,
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
          case Intrinsic, Routine, Field ->
            (cc.isInstantiated() || cc.feature().isOuterRef())
            && cc != Clazzes.conststring.getIfCreated()
            && !cc.isAbsurd()
            // NYI: this should not depend on string comparison!
            && !(cc.feature().qualifiedName().equals("void.absurd"))
            ;
          };
        (result ? _needsCode : _doesNotNeedCode).set(cl - CLAZZ_BASE);
        return result;
      }
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
   * Get the id of clazz consstring
   *
   * @param the id of connststring or -1 if that clazz was not created.
   */
  public int clazz_conststring()
  {
    var cc = Clazzes.conststring.getIfCreated();
    return cc == null ? -1 : id(cc);
  }


  /**
   * Get the id of clazz consstring.internalArray
   *
   * @param the id of connststring.internalArray or -1 if that clazz was not created.
   */
  public int clazz_conststring_internalArray()
  {
    var cc = Clazzes.constStringInternalArray;
    return cc == null ? -1 : id(cc);
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>.data
   *
   * @param the id of fuzion.sys.array<u8>.data or -1 if that clazz was not created.
   */
  public int clazz_fuzionSysArray_u8_data()
  {
    var cc = Clazzes.fuzionSysArray_u8_data;
    return cc == null ? -1 : id(cc);
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>.length
   *
   * @param the id of fuzion.sys.array<u8>.length or -1 if that clazz was not created.
   */
  public int clazz_fuzionSysArray_u8_length()
  {
    var cc = Clazzes.fuzionSysArray_u8_length;
    return cc == null ? -1 : id(cc);
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
        Errors.fatal((e instanceof Stmnt s) ? s.pos() :
                     (e instanceof Clazz z) ? z._type.pos() : null,
                     "Stmnt not supported in FUIR.codeAt", "Statement class: " + e.getClass());
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
    var vcl = (Clazz) outerClazz.getRuntimeData(t._valAndTaggedClazzId + 0);
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
    var ncl = (Clazz) outerClazz.getRuntimeData(t._valAndTaggedClazzId + 1);
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
    var vcl = (Clazz) outerClazz.getRuntimeData(v._clazzId);
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
    Clazz vc = (Clazz) outerClazz.getRuntimeData(b._valAndRefClazzId);
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
    Clazz rc = (Clazz) outerClazz.getRuntimeData(b._valAndRefClazzId+1);
    return id(rc);
  }

  public int unboxOuterRefClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Unbox);

    var outerClazz = clazz(cl);
    var u = (Unbox) _codeIds.get(c).get(ix);
    Clazz orc = (Clazz) outerClazz.getRuntimeData(u._refAndValClazzId);
    return id(orc);
  }

  public int unboxResultClazz(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Unbox);

    var outerClazz = clazz(cl);
    var u = (Unbox) _codeIds.get(c).get(ix);
    Clazz vc = (Clazz) outerClazz.getRuntimeData(u._refAndValClazzId+1);
    return id(vc);
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

    return (String) _codeIds.get(c).get(ix);
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
      (s instanceof AbstractCall   call) ? (Clazz) outerClazz.getRuntimeData(call._sid + 0) :
      (s instanceof AbstractAssign a   ) ? (Clazz) outerClazz.getRuntimeData(a   ._tid + 1) :
      (s instanceof Clazz          fld ) ? fld :
      (Clazz) (Object) new Object() { { if (true) throw new Error("acccessedClazz found unexpected Stmnt."); } } /* Java is ugly... */;

    return innerClazz == null ? -1 : id(innerClazz);
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

    if (s instanceof AbstractCall call)
      {
        f = call.calledFeature();
        tclazz     = (Clazz) outerClazz.getRuntimeData(call._sid + 1);
      }
    else if (s instanceof AbstractAssign ass)
      {
        var assignedField = (Clazz) outerClazz.getRuntimeData(ass._tid+ 1);
        tclazz = (Clazz) outerClazz.getRuntimeData(ass._tid);  // NYI: This should be the same as assignedField._outer
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
    var innerClazzes = new List<Clazz>();
    for (var clz : tclazz.heirs())
      {
        if (CHECKS) check
          (clz.isRef() == tclazz.isRef());
        var in = (Clazz) clz._inner.get(f);  // NYI: cast would fail for open generic field
        if (in != null && clazzNeedsCode(id(in)))
          {
            innerClazzes.add(clz);
            innerClazzes.add(in);
          }
      }

    var innerClazzIds = new int[innerClazzes.size()];
    for (var i = 0; i < innerClazzes.size(); i++)
      {
        innerClazzIds[i] = id(innerClazzes.get(i));
        if (CHECKS) check
          (innerClazzIds[i] != -1);
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

    if (accessIsDynamic(cl, c, ix))
      {
        return accessedClazzesDynamic(cl, c, ix);
      }
    else
      {
        var innerClazz = accessedClazz(cl, c, ix);
        return clazzNeedsCode(innerClazz) ? new int[] { clazzOuterClazz(innerClazz), innerClazz }
                                          : new int[0];
      }
  }


  /**
   * Is an access to a feature (assignment, call) dynamic?
   *
   * @param cl index of clazz containing the access
   *
   * @param c code block containing the access
   *
   * @param ix index of the acces
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
      (s instanceof AbstractAssign ass ) ? ((Clazz) outerClazz.getRuntimeData(ass._tid)).isRef() : // NYI: This should be the same as assignedField._outer
      (s instanceof Clazz          arg ) ? outerClazz.isRef() && !arg.feature().isOuterRef() : // assignment to arg field in inherits call (dynamic if outerlClazz is ref)
                                                                                       // or to outer ref field (not dynamic)
      (s instanceof AbstractCall   call) ? call.isDynamic() && ((Clazz) outerClazz.getRuntimeData(call._sid + 1)).isRef() :
      new Object() { { if (true) throw new Error("acccessIsDynamic found unexpected Stmnt."); } } == null /* Java is ugly... */;

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

    var outerClazz = clazz(cl);
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
      (s instanceof AbstractAssign ass ) ? (Clazz) outerClazz.getRuntimeData(ass._tid) : // NYI: This should be the same as assignedField._outer
      (s instanceof Clazz          arg ) ? outerClazz : // assignment to arg field in inherits call, so outer clazz is current instance
      (s instanceof AbstractCall   call) ? (Clazz) outerClazz.getRuntimeData(call._sid + 1) :
      (Clazz) (Object) new Object() { { if (true) throw new Error("acccessTargetClazz found unexpected Stmnt."); } } /* Java is ugly... */;

    return id(tclazz);
  }


  public int fieldIndex(int field)
  {
    return clazz(field).fieldIndex();
  }

  /**
   * For an intermediate command of type ExprKind.Const, return its clazz.
   *
   * Currently, the clazz is one of bool, i32, u32, i64, u64 of conststring.
   * This will be extended by other basic types (f64, etc.), value instances
   * without refs, choice instances with tag, arrays, etc.
   */
  public int constClazz(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Const);

    Clazz clazz;
    var ic = _codeIds.get(c).get(ix);
    var t = ((Expr) ic).type();
    if      (t.compareTo(Types.resolved.t_bool  ) == 0) { clazz = Clazzes.bool       .getIfCreated(); }
    else if (t.compareTo(Types.resolved.t_i8    ) == 0) { clazz = Clazzes.i8         .getIfCreated(); }
    else if (t.compareTo(Types.resolved.t_i16   ) == 0) { clazz = Clazzes.i16        .getIfCreated(); }
    else if (t.compareTo(Types.resolved.t_i32   ) == 0) { clazz = Clazzes.i32        .getIfCreated(); }
    else if (t.compareTo(Types.resolved.t_i64   ) == 0) { clazz = Clazzes.i64        .getIfCreated(); }
    else if (t.compareTo(Types.resolved.t_u8    ) == 0) { clazz = Clazzes.u8         .getIfCreated(); }
    else if (t.compareTo(Types.resolved.t_u16   ) == 0) { clazz = Clazzes.u16        .getIfCreated(); }
    else if (t.compareTo(Types.resolved.t_u32   ) == 0) { clazz = Clazzes.u32        .getIfCreated(); }
    else if (t.compareTo(Types.resolved.t_u64   ) == 0) { clazz = Clazzes.u64        .getIfCreated(); }
    else if (t.compareTo(Types.resolved.t_f32   ) == 0) { clazz = Clazzes.f32        .getIfCreated(); }
    else if (t.compareTo(Types.resolved.t_f64   ) == 0) { clazz = Clazzes.f64        .getIfCreated(); }
    else if (t.compareTo(Types.resolved.t_string) == 0) { clazz = Clazzes.conststring.getIfCreated(); } // NYI: a slight inconsistency here, need to change AST
    else if (ic instanceof InlineArray)
      {
        throw new Error("NYI: FUIR support for InlineArray still missing");
      }
    else { throw new Error("Unexpected type for ExprKind.Const: " + t); }
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
    if      (ic instanceof AbstractConstant co) { return co.data(); }
    else if (ic instanceof InlineArray)
      {
        throw new Error("NYI: FUIR support for InlineArray still missing");
      }
    throw new Error("Unexpected constant type " + ((Expr) ic).type() + ", expected bool, i32, u32, i64, u64, or string");
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

    var cc = clazz(cl);
    var s = _codeIds.get(c).get(ix);
    Clazz ss = s instanceof If
      ? cc.getRuntimeClazz(((If)            s)._runtimeClazzId)
      : cc.getRuntimeClazz(((AbstractMatch) s)._runtimeClazzId);
    return id(ss);
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

    var cc = clazz(cl);
    var s = _codeIds.get(c).get(ix);
    int result = -1; // no field for If
    if (s instanceof AbstractMatch m)
      {
        var mc = m.cases().get(cix);
        var f = mc.field();
        var fc = f != null && Clazzes.isUsed(f, cc) ? cc.getRuntimeClazz(mc._runtimeClazzId) : null;
        result = fc != null ? id(fc) : -1;
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
   * For a match statement, get the code associated with a given case
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
   * For a clazz that is a heir of 'Function', find the corresponding inner
   * clazz for 'call'.  This is used for code generation of intrinsic
   * 'abortable' that has to create code to call 'call'.
   *
   * @param cl index of a clazz that is a heir of 'Function'.
   */
  public int lookupCall(int cl)
  {
    var cc = clazz(cl);
    var call = Types.resolved.f_function_call;
    var ic = cc.lookup(call, Call.NO_GENERICS, Clazzes.isUsedAt(call));
    return id(ic);
  }


  /**
   * For a given field cl whose outer instance is a value type, find the same
   * field in the corresponding outer ref type.
   *
   * @param cl index of a clazz that is a field.
   */
  public int correspondingFieldInRefInstance(int cl)
  {
    var cc = clazz(cl);
    var rf = cc.correspondingFieldInRefInstance(cc);
    return id(rf);
  }


  /**
   * For a given field cl whose outer instance is a ref type, find the same
   * field in the corresponding outer value type.
   *
   * @param cl index of a clazz that is a field.
   */
  public int correspondingFieldInValueInstance(int cl)
  {
    var cc = clazz(cl);
    var rf = cc.correspondingFieldInValueInstance(cc);
    return id(rf);
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
      case Assign  -> "Assign to " + clazzAsString(accessedClazz(cl, c, ix));
      case Box     -> "Box " + clazzAsString(boxValueClazz(cl, c, ix)) + " => " + clazzAsString(boxResultClazz(cl, c, ix));
      case Unbox   -> "Unbox";
      case Call    -> "Call to " + clazzAsString(accessedClazz(cl, c, ix));
      case Current -> "Current";
      case Comment -> "Comment";
      case Const   -> "Const";
      case Dup     -> "Dup";
      case Match   -> "Match";
      case Tag     -> "Tag";
      case Env     -> "Env";
      case Pop     -> "Pop";
      case Unit    -> "Unit";
      };
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
    for (var ix = 0; withinCode(c, ix); ix = ix + codeSizeAt(c, ix))
      {
        System.out.printf("%d.%4d: %s\n", c, ix, codeAtAsString(cl, c, ix));
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

    dumpCode(cl, clazzCode(cl));
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
   * 2)' is 0 (popping 'curent.n').
   *
   * 'skip(cl, c, 7)' will result in 7, while 'skip(cl, c, 8)' will result in an
   * error since there is no expression before 'mul 1 (sub curent.n (add
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
      case Unbox   -> skipBack(cl, c, codeIndex(c, ix, -1));
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
      case Dup     -> codeIndex(c, ix, -1);
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
      };
  }


  /*-----------------  convenience methods for effects  -----------------*/


  /**
   * Is cl one of the instrinsics in effect that changes the effect in
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
           "effect.abort" -> true;
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


}

/* end of file */
