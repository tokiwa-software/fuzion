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

import java.util.HashMap;

import dev.flang.ast.FeatureName;

import dev.flang.fe.FrontEnd;
import dev.flang.fe.LibraryFeature;
import dev.flang.fe.LibraryModule;

import dev.flang.mir.MIR;

import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * An implementation of FUIR that generates clazzes on demand from module files.
 * This is used to run DFA for monomorphization.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class GeneratingFUIR extends FUIR
{


  /*-----------------------------  classes  -----------------------------*/


  record Clazz(int _id, SpecialClazzes _s, int _gix)
  {
    @Override
    public boolean equals(Object other)
    {
      return _gix == ((Clazz)other)._gix;  // NYI: outer and type parameters!
    }
    @Override
    public int hashCode()
    {
      return _gix;  // NYI: outer and type parameters!
    }
  }


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  private final FrontEnd _fe;

  private final HashMap<Clazz, Clazz> _clazzesHM;


  private final LibraryModule _mainModule;


  private final int _mainClazz;
  private final int _universe;


  private final List<Clazz> _clazzes;


  private final int[] _specialClazzes;



  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create FUIR from given Clazz instance.
   */
  public GeneratingFUIR(FrontEnd fe, MIR mir)
  {
    _fe = fe;
    _clazzesHM = new HashMap<Clazz, Clazz>();
    _mainModule = fe.mainModule();
    _clazzes = new List<>();
    _mainClazz = newClazz(SpecialClazzes.c_NOT_FOUND, ((LibraryFeature)mir.main()    ).globalIndex());
    _universe  = newClazz(SpecialClazzes.c_NOT_FOUND, ((LibraryFeature)mir.universe()).globalIndex());
    _specialClazzes = new int[SpecialClazzes.values().length];
    for (var v : SpecialClazzes.values())
      {
        _specialClazzes[v.ordinal()] = newClazz(v, 0);
      }
  }



  /**
   * Clone this FUIR such that modifications can be made by optimizers.  A heir
   * of FUIR can use this to redefine methods.
   *
   * @param original the original FUIR instance that we are cloning.
   */
  public GeneratingFUIR(GeneratingFUIR original)
  {
    super(original);
    _fe = original._fe;
    _clazzesHM = original._clazzesHM;
    _mainModule = original._mainModule;
    _mainClazz = original._mainClazz;
    _universe = original._universe;
    _clazzes = original._clazzes;
    _specialClazzes = original._specialClazzes;
  }


  /*-----------------------------  methods  -----------------------------*/


  private int newClazz(SpecialClazzes s, int gix)
  {
    var cl = new Clazz(CLAZZ_BASE + _clazzes.size(), s, gix);
    var existing = _clazzesHM.get(cl);
    if (existing != null)
      {
        cl = existing;
      }
    else
      {
        _clazzes.add(cl);
        _clazzesHM.put(cl, cl);
      }
    return cl._id;
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
    var c = _clazzes.get(cl - CLAZZ_BASE);
    if (c._gix == 0) return FeatureKind.Routine;
    var m = _fe.module(c._gix);
    return switch (m.featureKindEnum(c._gix - m._globalBase))
      {
      case Routine           -> FeatureKind.Routine;
      case Field             -> FeatureKind.Field;
      case TypeParameter,
           OpenTypeParameter -> FeatureKind.Intrinsic; // NYI: strange
      case Intrinsic         -> FeatureKind.Intrinsic;
      case Abstract          -> FeatureKind.Abstract;
      case Choice            -> FeatureKind.Choice;
      case Native            -> FeatureKind.Native;
      };
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
    var c = _clazzes.get(cl - CLAZZ_BASE);
    if (c._gix == 0)
      {
        return "NYI_" + c._s;
    //        System.out.println("PRobme for "+c._s);
    //        throw new Error();
      }
    var m = _fe.module(c._gix);
    var ix = c._gix - m._globalBase;
    var bytes = m.featureName(ix);
    var ac = m.featureArgCount(ix);
    var id = m.featureId(ix);
    String result;
    if (bytes.length == 0)
      {
        result = FeatureName.get(ix, ac, id).baseName();
      }
    else
      {
        result = new String(bytes, StandardCharsets.UTF_8);
      }
    return result;
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
    if (true) return cl;
    throw new Error("NYI");
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
    throw new Error("NYI");
  }


  /**
   * String representation of clazz, for creation of unique type names.
   *
   * @param cl a clazz id.
   */
  @Override
  public String clazzAsString(int cl)
  {
    //System.out.println("NYI: clazzAsString "+cl);
    var c = _clazzes.get(cl - CLAZZ_BASE);
    return "CL: "+(c._id - CLAZZ_BASE)+" "+c._s.toString();
  }


  /**
   * human readable String representation of clazz, for stack traces and debugging.
   *
   * @param cl a clazz id.
   */
  @Override
  public String clazzAsStringHuman(int cl)
  {
    if (true) return clazzAsString(cl);
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    return -1;
    //     throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
  }


  /**
   * NYI: CLEANUP: Remove? This seems to be used only for naming fields, maybe we could use clazzId2num(field) instead?
   */
  @Override
  public int fieldIndex(int field)
  {
    throw new Error("NYI");
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
    if (true) return false;
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    var c = _clazzes.get(cl - CLAZZ_BASE);
    if (c._gix == 0) return 0;
    var m = _fe.module(c._gix);
    return m.featureArgCount(c._gix - m._globalBase);   // NYI: open generics?
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
    throw new Error("NYI");
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
    throw new Error("NYI");
  }


  /**
   * Get the id of the result field of a given clazz.
   *
   * @param cl a clazz id
   *
   * @return id of cl's result field or -1 if f has no result field (NYI: or a
   * result field that contains no data)
   */
  @Override
  public int clazzResultField(int cl)
  {
    if (true) return -1;
    throw new Error("NYI");
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
    if (true) return -1;
    throw new Error("NYI");
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
    if (true) return cl;
    throw new Error("NYI");
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
    var c = _clazzes.get(cl - CLAZZ_BASE);
    if (c._gix == 0) return false;
    if (true) return true;
    throw new Error("NYI");
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
    //System.out.println("NYI: isConstructor " + clazzAsString(cl));
    return false;
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
    //    System.out.println("NYI: clazzIsRef " + clazzAsString(cl));
    return false;
  }


  /**
   * Is the given clazz a ref clazz that contains a boxed value type?
   *
   * @return true for boxed value-type clazz
   */
  @Override
  public boolean clazzIsBoxed(int cl)
  {
    if (true) return false;
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    for (var v : SpecialClazzes.values())
      {
        if (cl == _specialClazzes[v.ordinal()])
          {
            return v;
          }
      }
    return SpecialClazzes.c_NOT_FOUND;
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
    return _clazzes.get(cl - CLAZZ_BASE)._s == c;
  }


  /**
   * Get the id of the given special clazz.
   *
   * @param c the id of clazz c or -1 if that clazz was not created.
   */
  @Override
  public int clazz(SpecialClazzes c)
  {
    //System.out.println("Special clazz "+c);
    return _specialClazzes[c.ordinal()];
  }


  /**
   * Get the id of clazz Any.
   *
   * @return clazz id of clazz Any
   */
  @Override
  public int clazzAny()
  {
    throw new Error("NYI");
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
    if (true) return -1;
    throw new Error("NYI");
  }


  /**
   * Get the id of clazz Const_String.utf8_data
   *
   * @return the id of Const_String.utf8_data or -1 if that clazz was not created.
   */
  @Override
  public int clazz_Const_String_utf8_data()
  {
    if (true) return -1;
    throw new Error("NYI");
  }


  /**
   * Get the id of clazz Const_String.array
   *
   * @return the id of Const_String.array or -1 if that clazz was not created.
   */
  @Override
  public int clazz_array_u8()
  {
    if (true) return -1;
    throw new Error("NYI");
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>
   *
   * @return the id of fuzion.sys.array<u8> or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8()
  {
    if (true) return -1;
    throw new Error("NYI");
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>.data
   *
   * @return the id of fuzion.sys.array<u8>.data or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8_data()
  {
    if (true) return -1;
    throw new Error("NYI");
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>.length
   *
   * @return the id of fuzion.sys.array<u8>.length or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8_length()
  {
    if (true) return -1;
    throw new Error("NYI");
  }


  /**
   * Get the id of clazz fuzion.java.Java_Object
   *
   * @return the id of fuzion.java.Java_Object or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionJavaObject()
  {
    if (true) return -1;
    throw new Error("NYI");
  }


  /**
   * Get the id of clazz fuzion.java.Java_Object.Java_Ref
   *
   * @return the id of fuzion.java.Java_Object.Java_Ref or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionJavaObject_Ref()
  {
    if (true) return -1;
    throw new Error("NYI");
  }


  /**
   * Get the id of clazz error
   *
   * @return the id of error or -1 if that clazz was not created.
   */
  @Override
  public int clazz_error()
  {
    if (true) return -1;
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    if (true) return true;
    throw new Error("NYI");
  }


  /**
   * Is this a void type, i.e., values of this clazz do not exist.
   */
  @Override
  public boolean clazzIsVoidType(int cl)
  {
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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


  @Override
  public boolean withinCode(int s)
  {
    return (s != NO_SITE) && false;
  }


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
    if (true) return s;
    throw new Error("NYI");
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
    throw new Error("NYI");
  }


  /**
   * Get the expr at the given site
   *
   * @param s site
   */
  @Override
  public ExprKind codeAt(int s)
  {
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
  }


  /**
   * For an intermediate command of type ExprKind.Const, return the constant
   * data using little endian encoding, i.e, 0x12345678 -> { 0x78, 0x56, 0x34, 0x12 }.
   */
  @Override
  public byte[] constData(int s)
  {
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
  }


  /**
   * @return If the expression has only been found to result in void.
   */
  @Override
  public boolean alwaysResultsInVoid(int s)
  {
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    throw new Error("NYI");
  }


  /**
   * Is `constCl` an array?
   */
  @Override
  public boolean clazzIsArray(int constCl)
  {
    throw new Error("NYI");
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
  @Override
  public byte[] deseralizeConst(int cl, ByteBuffer bb)
  {
    throw new Error("NYI");
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
    if (true) return "test.fz";
    throw new Error("NYI");
  }


  /**
   * Get the position where the clazz is declared
   * in the source code.
   */
  @Override
  public SourcePosition declarationPos(int cl)
  {
    throw new Error("NYI");
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
    throw new Error("NYI");
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
    if (false) throw new Error("NYI");
  }


}

/* end of file */
