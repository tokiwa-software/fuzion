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

import dev.flang.ir.IR;

import dev.flang.util.FuzionConstants;
import dev.flang.util.SourcePosition;


/**
 * The FUIR contains the intermediate representation of fuzion applications.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class FUIR extends IR
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Enum of clazzes that require special handling in the backend
   */
  public enum SpecialClazzes
  {
    // dummy entry to report failure of getSpecialId()
    c_NOT_FOUND   (""                           , 0, null        ),

    c_universe    (FuzionConstants.UNIVERSE_NAME, 0, c_NOT_FOUND ),
    c_Any         ("Any"                        , 0, c_universe  ),
    c_i8          ("i8"                         , 1, c_universe  ),
    c_i16         ("i16"                        , 1, c_universe  ),
    c_i32         ("i32"                        , 1, c_universe  ),
    c_i64         ("i64"                        , 1, c_universe  ),
    c_u8          ("u8"                         , 1, c_universe  ),
    c_u16         ("u16"                        , 1, c_universe  ),
    c_u32         ("u32"                        , 1, c_universe  ),
    c_u64         ("u64"                        , 1, c_universe  ),
    c_f32         ("f32"                        , 1, c_universe  ),
    c_f64         ("f64"                        , 1, c_universe  ),
    c_unit        ("unit"                       , 0, c_universe  ),
    c_void        ("void"                       , 0, c_universe  ),
    c_bool        ("bool"                       , 0, c_universe  ),
    c_true_       ("true_"                      , 0, c_universe  ),
    c_false_      ("false_"                     , 0, c_universe  ),
    c_Const_String("Const_String"               , 0, c_universe  ),
    c_CS_utf8_data("utf8_data"                  , 0, c_Const_String),
    c_String      ("String"                     , 0, c_universe  ),
    c_error       ("error"                      , 1, c_universe  ),
    c_fuzion      ("fuzion"                     , 0, c_universe  ),
    c_java        ("java"                       , 0, c_fuzion    ),
    c_fuzion_sys  ("sys"                        , 0, c_fuzion    ),
    c_sys_ptr     ("Pointer"                    , 0, c_fuzion_sys),
    ;

    String _name;
    int _argCount;
    SpecialClazzes _outer;

    SpecialClazzes(String name, int argc, SpecialClazzes outer)
    {
      _name = name;
      _argCount = argc;
      _outer = outer;
    }
  }


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create FUIR from given Clazz instance.
   */
  public FUIR()
  {
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
  }


  /*-----------------------------  methods  -----------------------------*/


  /*------------------------  accessing classes  ------------------------*/


  /**
   * The clazz ids form a contiguous range of integers. This method gives the
   * smallest clazz id.  Together with `lastClazz`, this permits iteration.
   *
   * @return a valid clazz id such that for all clazz ids id: result <= id.
   */
  public abstract int firstClazz();


  /**
   * The clazz ids form a contiguous range of integers. This method gives the
   * largest clazz id.  Together with `firstClazz`, this permits iteration.
   *
   * @return a valid clazz id such that for all clazz ids id: result >= id.
   */
  public abstract int lastClazz();


  /**
   * id of the main clazz.
   *
   * @return a valid clazz id
   */
  public abstract int mainClazzId();


  /**
   * Convert a clazz id into a number 0, 1, 2, 3, ...
   *
   * The clazz id is intentionally large to detect accidental usage of a clazz
   * id in a wrong context.
   *
   * @param cl a clazz id
   *
   * @return a small positive integer corresponding to cl.
   */
  public int clazzId2num(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= firstClazz() && cl <= lastClazz());

    var result = cl - firstClazz();

    if (POSTCONDITIONS) ensure
      (result >= 0 && result <= lastClazz() - firstClazz());

    return result;
  }


  /**
   * Return the kind of this clazz ( Routine, Field, Intrinsic, Abstract, ...)
   */
  public abstract FeatureKind clazzKind(int cl);


  /**
   * Return the base name of this clazz, i.e., the name excluding the outer
   * clazz' name and excluding the actual type parameters
   *
   * @return String like `"Set"` if `cl` corresponds to `container.Set u32`.
   */
  public abstract String clazzBaseName(int cl);



  /**
   * Get the clazz of the result of calling a clazz
   *
   * @param cl a clazz id, must not be Choice
   *
   * @return clazz id of cl's result
   */
  public abstract int clazzResultClazz(int cl);


  /**
   * The original qualified name of the feature this clazz was
   * created from, ignoring any inheritance into new clazzes.
   *
   * @param cl a clazz
   *
   * @return its original name, e.g. 'Array.getel' instead of
   * 'Const_String.getel'
   */
  public abstract String clazzOriginalName(int cl);


  /**
   * String representation of clazz, for creation of unique type names.
   *
   * @param cl a clazz id.
   */
  public abstract String clazzAsString(int cl);


  /**
   * human readable String representation of clazz, for stack traces and debugging.
   *
   * @param cl a clazz id.
   */
  public abstract String clazzAsStringHuman(int cl);


  /**
   * Get a String representation of a given clazz including a list of arguments
   * and the result type. For debugging only, names might be ambiguous.
   *
   * @param cl a clazz id.
   */
  public abstract String clazzAsStringWithArgsAndResult(int cl);


  /**
   * Get the outer clazz of the given clazz.
   *
   * @param cl a clazz id
   *
   * @return clazz id of cl's outer clazz, NO_CLAZZ if cl is universe.
   */
  public abstract int clazzOuterClazz(int cl);


  /*------------------------  accessing fields  ------------------------*/


  /**
   * Number of value fields in clazz `cl`, including argument value fields,
   * inherited fields, artificial fields like outer refs.
   *
   * @param cl a clazz id
   *
   * @return number of value fields in `cl`
   */
  public abstract int clazzNumFields(int cl);


  /**
   * Return the field #i in the given clazz
   *
   * @param cl a clazz id
   *
   * @param i the field number
   *
   * @return the clazz id of the field
   */
  public abstract int clazzField(int cl, int i);


  /**
   * Is the given field clazz a reference to an outer feature?
   *
   * @param cl a clazz id of kind Field
   *
   * @return true for automatically generated references to outer instance
   */
  public abstract boolean clazzIsOuterRef(int cl);


  /**
   * Check if field does not store the value directly, but a pointer to the value.
   *
   * @param field a clazz id, not necessarily a field
   *
   * @return true iff the field is an outer ref field that holds an address of
   * an outer value, false for normal fields our outer ref fields that store the
   * outer ref or value directly.
   */
  public abstract boolean clazzFieldIsAdrOfValue(int fcl);


  /**
   * NYI: CLEANUP: Remove? This seems to be used only for naming fields, maybe we could use clazzId2num(field) instead?
   */
  public abstract int fieldIndex(int field);


  /*------------------------  accessing choices  -----------------------*/


  /**
   * is the given clazz a choice clazz
   *
   * @param cl a clazz id
   */
  public abstract boolean clazzIsChoice(int cl);


  /**
   * For a choice type, the number of entries to choose from.
   *
   * @param cl a clazz id
   *
   * @return -1 if cl is not a choice clazz, the number of choice entries
   * otherwise.  May be 0 for the void choice.
   */
  public abstract int clazzNumChoices(int cl);


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
  public abstract int clazzChoice(int cl, int i);


  /**
   * Is this a choice type with some elements of ref type?
   *
   * @param cl a clazz id
   *
   * @return true iff cl is a choice with at least one ref element
   */
  public abstract boolean clazzIsChoiceWithRefs(int cl);


  /**
   * Is this a choice type with all elements of ref type?
   *
   * @param cl a clazz id
   *
   * @return true iff cl is a choice with only ref or unit/void elements
   */
  public abstract boolean clazzIsChoiceOfOnlyRefs(int cl);



  /*------------------------  inheritance  -----------------------*/


  /**
   * Get all heirs of given clazz that are instantiated.
   *
   * @param cl a clazz id
   *
   * @return an array of the clazz id's of all heirs for cl that are
   * instantiated, including cl itself, provided that cl is instantiated.
   */
  public abstract int[] clazzInstantiatedHeirs(int cl);


  /*-------------------------  routines  -------------------------*/


  /**
   * Get the number of arguments required for a call to this clazz.
   *
   * @param cl clazz id
   *
   * @return number of arguments expected by cl, 0 if none or if clazz cl can
   * not be called (is a choice type)
   */
  public abstract int clazzArgCount(int cl);


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
  public abstract int clazzArgClazz(int cl, int arg);


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
  public abstract int clazzArg(int cl, int arg);


  /**
   * Get the id of the result field of a given clazz.
   *
   * @param cl a clazz id
   *
   * @return id of cl's result field or NO_CLAZZ if cl has no result field (NYI:
   * or a result field that contains no data)
   */
  public abstract int clazzResultField(int cl);


  /**
   * If a clazz's instance contains an outer ref field, return this field.
   *
   * @param cl a clazz id
   *
   * @return clazz id of cl's outer ref field or -1 if no such field exists.
   */
  public abstract int clazzOuterRef(int cl);


  /**
   * Get access to the code of a clazz of kind Routine
   *
   * @param cl a clazz id
   *
   * @return a site id referring to cl's code
   */
  public abstract int clazzCode(int cl);


  /**
   * Does the backend need to generate code for this clazz since it might be
   * called at runtime.  This is true for all features that are called directly
   * or dynamically in a 'normal' call, i.e., not in an inheritance call.
   *
   * An inheritance call is inlined since it works on a different instance, the
   * instance of the heir class.  Consequently, a clazz resulting from an
   * inheritance call does not need code for itself.
   */
  public abstract boolean clazzNeedsCode(int cl);


  /*-----------------------  constructors  -----------------------*/


  /**
   * Check if the given clazz is a constructor, i.e., a routine returning
   * its instance as a result?
   *
   * @param clazz a clazz id
   *
   * @return true if the clazz is a constructor, false otherwise
   */
  public abstract boolean isConstructor(int clazz);


  /**
   * Is the given clazz a ref clazz?
   *
   * @parm cl a constructor clazz id
   *
   * @return true for non-value-type clazzes
   */
  public abstract boolean clazzIsRef(int cl);


  /**
   * Is the given clazz a ref clazz that contains a boxed value type?
   *
   * @return true for boxed value-type clazz
   */
  public abstract boolean clazzIsBoxed(int cl);


  /**
   * For a reference clazz, obtain the corresponding value clazz.
   *
   * @param cl a clazz id
   *
   * @return clazz id of corresponding value clazz.
   */
  public abstract int clazzAsValue(int cl);


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
  public abstract byte[] clazzTypeName(int cl);


  /**
   * If cl is a type parameter, return the type parameter's actual type.
   *
   * @param cl a clazz id
   *
   * @return if cl is a type parameter, clazz id of cl's actual type or -1 if cl
   * is not a type parameter.
   */
  public abstract int clazzTypeParameterActualType(int cl);


  /*----------------------  special clazzes  ---------------------*/


  /**
   * Obtain SpecialClazz from a given clazz.
   *
   * @param cl a clazz id
   *
   * @return the corresponding SpecialClazz or c_NOT_FOUND if cl is not a
   * special clazz.
   */
  public abstract SpecialClazzes getSpecialClazz(int cl);


  /**
   * Check if a clazz is the special clazz c.
   *
   * @param cl a clazz id
   *
   * @param c one of the constants SpecialClazzes.c_i8,...
   *
   * @return true iff cl is the specified special clazz c
   */
  public abstract boolean clazzIs(int cl, SpecialClazzes c);


  /**
   * Get the id of the given special clazz.
   *
   * @param c the id of clazz c or -1 if that clazz was not created.
   */
  public abstract int clazz(SpecialClazzes c);


  /**
   * Get the id of clazz Any.
   *
   * @return clazz id of clazz Any
   */
  public abstract int clazzAny();


  /**
   * Get the id of clazz universe.
   *
   * @return clazz id of clazz universe
   */
  public abstract int clazzUniverse();


  /**
   * Get the id of clazz Const_String
   *
   * @return the id of Const_String or -1 if that clazz was not created.
   */
  public abstract int clazz_Const_String();


  /**
   * Get the id of clazz Const_String.utf8_data
   *
   * @return the id of Const_String.utf8_data or -1 if that clazz was not created.
   */
  public abstract int clazz_Const_String_utf8_data();


  /**
   * Get the id of clazz `array u8`
   *
   * @return the id of Const_String.array or -1 if that clazz was not created.
   */
  public abstract int clazz_array_u8();


  /**
   * Get the id of clazz fuzion.sys.array<u8>
   *
   * @return the id of fuzion.sys.array<u8> or -1 if that clazz was not created.
   */
  public abstract int clazz_fuzionSysArray_u8();


  /**
   * Get the id of clazz fuzion.sys.array<u8>.data
   *
   * @return the id of fuzion.sys.array<u8>.data or -1 if that clazz was not created.
   */
  public abstract int clazz_fuzionSysArray_u8_data();


  /**
   * Get the id of clazz fuzion.sys.array<u8>.length
   *
   * @return the id of fuzion.sys.array<u8>.length or -1 if that clazz was not created.
   */
  public abstract int clazz_fuzionSysArray_u8_length();


  /**
   * Get the id of clazz fuzion.java.Java_Object
   *
   * @return the id of fuzion.java.Java_Object or -1 if that clazz was not created.
   */
  public abstract int clazz_fuzionJavaObject();


  /**
   * Get the id of clazz fuzion.java.Java_Object.Java_Ref
   *
   * @return the id of fuzion.java.Java_Object.Java_Ref or -1 if that clazz was not created.
   */
  public abstract int clazz_fuzionJavaObject_Ref();


  /**
   * Get the id of clazz error
   *
   * @return the id of error or -1 if that clazz was not created.
   */
  public abstract int clazz_error();


  /**
   * On `cl` lookup field `Java_Ref`
   *
   * @param cl Java_Object or inheriting from Java_Object
   *
   */
  public abstract int lookupJavaRef(int cl);


  /**
   * Check if the given clazz is a --possibly inherited--
   * `fuzion.java.Java_Object.Java_Ref` field.
   *
   * NYI: CLEANUP: #3927: Remove once #3927 is fixed.
   *
   * @param cl a clazz id that should be checked, must not be NO_CLAZZ.
   */
  public abstract boolean isJavaRef(int cl);


  /**
   * For a clazz that is an heir of 'Function', find the corresponding inner
   * clazz for 'call'.  This is used for code generation of intrinsic
   * 'abortable' that has to create code to call 'call'.
   *
   * @param cl index of a clazz that is an heir of 'Function'.
   *
   * @return the index of the requested `Function.call` feature's clazz.
   */
  public abstract int lookupCall(int cl);


  /**
   * For a clazz that is an heir of 'effect', find the corresponding inner
   * clazz for 'finally'.  This is used for code generation of intrinsic
   * 'instate0' that has to create code to call 'effect.finally'.
   *
   * @param cl index of a clazz that is an heir of 'effect'.
   *
   * @return the index of the requested `effect.finally` feature's clazz.
   */
  public abstract int lookup_static_finally(int cl);


  /**
   * For a clazz of concur.atomic, lookup the inner clazz of the value field.
   *
   * @param cl index of a clazz representing cl's value field
   *
   * @return the index of the requested `concur.atomic.value` field's clazz.
   */
  public abstract int lookupAtomicValue(int cl);


  /**
   * For a clazz of array, lookup the inner clazz of the internal_array field.
   *
   * @param cl index of a clazz `array T` for some type parameter `T`
   *
   * @return the index of the requested `array.internal_array` field's clazz.
   */
  public abstract int lookup_array_internal_array(int cl);


  /**
   * For a clazz of fuzion.sys.internal_array, lookup the inner clazz of the
   * data field.
   *
   * @param cl index of a clazz `fuzion.sys.internal_array T` for some type parameter `T`
   *
   * @return the index of the requested `fuzion.sys.internal_array.data` field's clazz.
   */
  public abstract int lookup_fuzion_sys_internal_array_data(int cl);


  /**
   * For a clazz of fuzion.sys.internal_array, lookup the inner clazz of the
   * length field.
   *
   * @param cl index of a clazz `fuzion.sys.internal_array T` for some type parameter `T`
   *
   * @return the index of the requested `fuzion.sys.internal_array.length` field's clazz.
   */
  public abstract int lookup_fuzion_sys_internal_array_length(int cl);


  /**
   * For a clazz of error, lookup the inner clazz of the msg field.
   *
   * @param cl index of a clazz `error`
   *
   * @return the index of the requested `error.msg` field's clazz.
   */
  public abstract int lookup_error_msg(int cl);


  /*---------------------------  types  --------------------------*/


  /**
   * Is there just one single value of this class, so this type is essentially a
   * C/Java `void` type?
   *
   * NOTE: This is false for Fuzion's `void` type!
   */
  public abstract boolean clazzIsUnitType(int cl);


  /**
   * Is this a void type, i.e., values of this clazz do not exist.
   */
  public abstract boolean clazzIsVoidType(int cl);


  /**
   * Test is a given clazz is not -1 and stores data.
   *
   * @param cl the clazz defining a type, may be -1
   *
   * @return true if cl != -1 and not unit or void type.
   */
  public abstract boolean hasData(int cl);


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
  public abstract int clazzActualGeneric(int cl, int gix);


  /*---------------------  analysis results  ---------------------*/


  /**
   * For a call in cl in code block c at index i, does the result escape
   * the current clazz stack frame (such that it cannot be stored in a
   * local var in the stack frame of cl)
   *
   * @param s site of call
   *
   * @return true iff the result of the call must be cloned on the heap.
   */
  public boolean doesResultEscape(int s)
  {
    return true;
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
    Undefined;

    /**
     * May an instance with this LifeTime be accessible after the call to its
     * routine?
     */
    public boolean maySurviveCall()
    {
      require
        (this != Undefined);

      return this.ordinal() > Call.ordinal();
    }
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
   * @return A conservative estimate of the lifespan of cl's instance.
   * Undefined if a call to cl does not create an instance, Call if it is
   * guaranteed that the instance is inaccessible after the call returned.
   */
  public abstract LifeTime lifeTime(int cl);


  /*--------------------------  accessing code  -------------------------*/


  /**
   * Get the clazz id at the given site
   *
   * @param s a site, may be !withinCode(s), i.e., this may be used on
   * `clazzCode(cl)` if the code is empty.
   *
   * @return the clazz id that code at site s belongs to.
   */
  public abstract int clazzAt(int s);


  /**
   * Create a String representation of a site for debugging purposes:
   *
   * @param s a site or NO_SITE
   *
   * @return a String describing site
   */
  public abstract String siteAsString(int s);


  /**
   * Get the expr at the given site
   *
   * @param s site
   */
  public abstract ExprKind codeAt(int s);


  /**
   * For an instruction of type ExprKind.Tag at site s, return the type of the
   * original value that will be tagged.
   *
   * @param s a code site for an Env instruction.
   *
   * @return the original type, i.e., for `o option i32 := 42`, this is `i32`.
   */
  public abstract int tagValueClazz(int s);


  /**
   * For an instruction of type ExprKind.Tag at site s, return the type of the
   * original value that will be tagged.
   *
   * @param s a code site for an Env instruction.
   *
   * @return the new choice type, i.e., for `o option i32 := 42`, this is
   * `option i32`.
   */
  public abstract int tagNewClazz(int s);


  /**
   * For an instruction of type ExprKind.Tag at site s, return the number of the
   * choice. This will be the same number as the tag number used in a match.
   *
   * @param s a code site for an Env instruction.
   *
   * @return the tag number, i.e., for `o choice a b i32 c d := 42`, this is
   * `2`.
   */
  public abstract int tagTagNum(int s);


  /**
   * For an instruction of type ExprKind.Env at site s, return the type of the
   * env value.
   *
   * @param s a code site for an Env instruction.
   */
  public abstract int envClazz(int s);


  /**
   * For an instruction of type ExprKind.Box at site s, return the original type
   * of the value that is to be boxed.
   *
   * @param s a code site for a Box instruction.
   *
   * @return the original type of the value to be boxed.
   */
  public abstract int boxValueClazz(int s);


  /**
   * For an instruction of type ExprKind.Box at site s, return the new reference
   * type of the value that is to be boxed.
   *
   * @param s a code site for a Box instruction.
   *
   * @return the new reference type of the value to be boxed.
   */
  public abstract int boxResultClazz(int s);


  /**
   * Get the code for a comment expression.  This is used for debugging.
   *
   * @param s site of the comment
   */
  public abstract String comment(int s);


  /**
   * Get the inner clazz for a non dynamic access or the static clazz of a dynamic
   * access.
   *
   * @param s site of the access
   *
   * @return the clazz that has to be accessed or -1 if the access is an
   * assignment to a field that is unused, so the assignment is not needed.
   */
  public abstract int accessedClazz(int s);


  /**
   * Get the type of an assigned value. This returns the type even if the
   * assigned field has been removed and accessedClazz() returns -1.
   *
   * @param s site of the assignment
   *
   * @return the type of the assigned value.
   */
  public abstract int assignedType(int s);


  /**
   * Get the possible inner clazzes for a call or assignment to a field
   *
   * @param s site of the access
   *
   * @return an array with an even number of element pairs with accessed target
   * clazzes at even indices followed by the corresponding inner clazz of the
   * feature to be accessed for this target.
   */
  public abstract int[] accessedClazzes(int s);


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
  public abstract int lookup(int s, int tclazz);


  /**
   * Inform the FUIR instance that lookup for new clazzes is finished.  This
   * means that clazzIsUnitType will be able to produce correct results since no
   * more features will be added.
   */
  public void lookupDone()
  {
  }


  /**
   * Is an access to a feature (assignment, call) dynamic?
   *
   * @param s site of the access
   *
   * @return true iff the assignment or call requires dynamic binding depending
   * on the actual target type.
   */
  public abstract boolean accessIsDynamic(int s);


  /**
   * Get the target (outer) clazz of a feature access
   *
   * @param cl index of clazz containing the access
   *
   * @param s site of the access
   *
   * @return index of the static outer clazz of the accessed feature.
   */
  public abstract int accessTargetClazz(int s);


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
  public abstract int constClazz(int s);


  /**
   * For an intermediate command of type ExprKind.Const, return the constant
   * data using little endian encoding, i.e, 0x12345678 -> { 0x78, 0x56, 0x34, 0x12 }.
   */
  public abstract byte[] constData(int s);


  /**
   * For a match expression, get the static clazz of the subject.
   *
   * @param s site of the match
   *
   * @return clazz id of type of the subject
   */
  public abstract int matchStaticSubject(int s);


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
  public abstract int matchCaseField(int s, int cix);


  /**
   * For a given tag return the index of the corresponding case.
   *
   * @param s site of the match
   *
   * @param tag e.g. 0,1,2,...
   *
   * @return the index of the case for tag `tag`
   */
  public abstract int matchCaseIndex(int s, int tag);


  /**
   * For a match expression, get the tags matched by a given case
   *
   * @param s site of the match
   *
   * @paramc cix index of the case in the match
   *
   * @return array of tag numbers this case matches
   */
  public abstract int[] matchCaseTags(int s, int cix);


  /**
   * For a match expression, get the code associated with a given case
   *
   * @param s site of the match
   *
   * @paramc cix index of the case in the match
   *
   * @return code block for the case
   */
  public abstract int matchCaseCode(int s, int cix);


  @Override
  public boolean withinCode(int s)
  {
    return (s != NO_SITE) && super.withinCode(s);
  }


  /**
   * @return If the expression has only been found to result in void.
   */
  public abstract boolean alwaysResultsInVoid(int s);


  /**
   * Get a string representation of the expr at the given index in given code
   * block.  Useful for debugging.
   *
   * @param s site of an expression
   */
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
  public abstract SourcePosition sitePos(int s);


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
   * Print the contents of the given code block to System.out, for debugging.
   *
   * @param cl index of the clazz containing the code block.
   *
   * @param c the code block
   */
  public void dumpCode(int cl, int c)
  {
    String label = label(c) +  ":";
    for (var s = c; withinCode(s); s = s + codeSizeAt(s))
      {
        System.out.printf("%s\t%d: %s\n", label, s, codeAtAsString(s));
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
  public void dumpCode()
  {
    for (var cl = firstClazz(); cl <= lastClazz(); cl++)
      {
        switch (clazzKind(cl))
          {
          case Routine: if (clazzNeedsCode(cl)) { dumpCode(cl); } break;
          default: break;
          }
      };
  }


  /**
   * For a given site 's', go 'delta' expressions further or back (in case
   * 'delta < 0').
   *
   * @param s a site
   *
   * @param delta the number of instructions to go forward or back.
   */
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
              var acl = clazzArgClazz(cc, ac-1-i);
              if (clazzResultClazz(acl) != clazzUniverse())
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
  public abstract boolean isEffectIntrinsic(int cl);


  /**
   * For an intrinsic in effect that changes the effect in the
   * current environment, return the type of the environment.  This type is used
   * to distinguish different environments.
   *
   * @param cl the id of the intrinsic clazz
   *
   * @return the type of the outer feature of cl
   */
  public abstract int effectTypeFromInstrinsic(int cl);


  /*------------------------------  arrays  -----------------------------*/


  /**
   * the clazz of the elements of the array
   *
   * @param constCl, e.g. `array (tuple i32 codepoint)`
   *
   * @return e.g. `tuple i32 codepoint`
   */
  public abstract int inlineArrayElementClazz(int constCl);


  /**
   * Is `constCl` an array?
   */
  public abstract boolean clazzIsArray(int constCl);


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
  public abstract byte[] deseralizeConst(int cl, ByteBuffer bb);


  /*----------------------  accessing source code  ----------------------*/


  /**
   * Get the source file the clazz originates from.
   *
   * e.g. /fuzion/tests/hello/HelloWorld.fz, $FUZION/lib/panic.fz
   */
  public abstract String clazzSrcFile(int cl);


  /**
   * Get the position where the clazz is declared
   * in the source code.
   */
  public abstract SourcePosition declarationPos(int cl);


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
  public abstract void recordAbstractMissing(int cl, int f, int instantiationSite, String context, int callSite);


  /**
   * In case any errors were recorded via `recordAbstractMissing` this will
   * create the corresponding error messages.  The errors reported will be
   * cumulative, i.e., if a clazz is missing several implementations of abstract
   * features, there will be only one error for that clazz.
   */
  public abstract void reportAbstractMissing();


}

/* end of file */
