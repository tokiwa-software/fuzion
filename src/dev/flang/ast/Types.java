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
 * Source of class Types
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;

/*---------------------------------------------------------------------*/


/**
 * Types manages the types used in the system.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Types extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  private static Map<Type, Type> types;

  /**
   * Name of abstract features for function types:
   */
  public static final String FUNCTION_NAME = "Function";

  public static Resolved resolved = null;

  /**
   * Dummy name used for address type Types.t_ADDRESS which is used for
   * references to value types.
   */
  static final String ADDRESS_NAME = "--ADDRESS--";


  /**
   * Dummy name used for undefined type t_UNDEFINED which is used for undefined
   * types that are expected to be replaced by the correct type during type
   * inference.  Examples are the result of union of distinct types on different
   * branches of an if or match, or the type of the result var befure type
   * inference has determined the result type.
   */
  static final String UNDEFINED_NAME = "--UNDEFINED--";


  /**
   * Dummy name used for error type t_ERROR which is used in case of compilation
   * time errors.
   */
  public static final String ERROR_NAME = Errors.ERROR_STRING;


  /**
   * Names if internal types that are not backed by physical feature defintions.
   */
  static Set<String> INTERNAL_NAMES = Collections.<String>unmodifiableSet
    (new TreeSet<>(Arrays.asList(ADDRESS_NAME,
                                 UNDEFINED_NAME,
                                 ERROR_NAME)));

  /* artificial type for the address of a value type, used for outer refs to value instances */
  public static Type t_ADDRESS;

  /* artificial type for Expr that does not have a well defined type such as the
   * union of two distinct types */
  public static Type t_UNDEFINED;

  /* artificial type for Expr with unknown type due to compilation error */
  public static Type t_ERROR;

  /* artificial feature used when feature is not known due to compilation error */
  public static Feature f_ERROR = new Feature(true);

  public static class Resolved
  {
    public final AbstractFeature universe;
    public final AbstractType t_i8  ;
    public final AbstractType t_i16 ;
    public final AbstractType t_i32 ;
    public final AbstractType t_i64 ;
    public final AbstractType t_u8  ;
    public final AbstractType t_u16 ;
    public final AbstractType t_u32 ;
    public final AbstractType t_u64 ;
    public final AbstractType t_f32 ;
    public final AbstractType t_f64 ;
    public final AbstractType t_ref_i8  ;
    public final AbstractType t_ref_i16 ;
    public final AbstractType t_ref_i32 ;
    public final AbstractType t_ref_i64 ;
    public final AbstractType t_ref_u8  ;
    public final AbstractType t_ref_u16 ;
    public final AbstractType t_ref_u32 ;
    public final AbstractType t_ref_u64 ;
    public final AbstractType t_ref_f32 ;
    public final AbstractType t_ref_f64 ;
    public final AbstractType t_bool;
    public final AbstractType t_object;
    private final AbstractType t_fuzion;
    public final AbstractType t_string;
    public final AbstractType t_conststring;
    public final AbstractType t_unit;

    /* void will be used as the initial result type of tail recursive calls of
     * the form
     *
     *    f => if c f else x
     *
     * since the union of void  with any other type is the other type.
     */
    public final AbstractType t_void;
    public final AbstractFeature f_void;
    public final AbstractFeature f_choice;
    public final AbstractFeature f_TRUE;
    public final AbstractFeature f_FALSE;
    public final AbstractFeature f_bool;
    public final AbstractFeature f_bool_NOT;
    public final AbstractFeature f_bool_AND;
    public final AbstractFeature f_bool_OR;
    public final AbstractFeature f_bool_IMPLIES;
    public final AbstractFeature f_debug;
    public final AbstractFeature f_debugLevel;
    public final AbstractFeature f_function;
    public final AbstractFeature f_function_call;
    public final AbstractFeature f_safety;
    public final AbstractFeature f_array;
    public final AbstractFeature f_array_internalArray;
    public final AbstractFeature f_fuzion;
    public final AbstractFeature f_fuzion_sys;
    public final AbstractFeature f_fuzion_sys_array;
    public final AbstractFeature f_fuzion_sys_array_length;
    public final AbstractFeature f_fuzion_sys_array_data;
    public final AbstractFeature f_Type;
    public final AbstractFeature f_Types;
    public final AbstractFeature f_Types_get;
    public static interface CreateType
    {
      AbstractType type(String name, boolean isRef);
    }
    public Resolved(SrcModule mod, CreateType ct, AbstractFeature universe)
    {
      this.universe = universe;
      t_i8            = ct.type("i8"          , false);
      t_i16           = ct.type("i16"         , false);
      t_i32           = ct.type("i32"         , false);
      t_i64           = ct.type("i64"         , false);
      t_u8            = ct.type("u8"          , false);
      t_u16           = ct.type("u16"         , false);
      t_u32           = ct.type("u32"         , false);
      t_u64           = ct.type("u64"         , false);
      t_f32           = ct.type("f32"         , false);
      t_f64           = ct.type("f64"         , false);
      t_ref_i8        = ct.type("i8"          , true );
      t_ref_i16       = ct.type("i16"         , true );
      t_ref_i32       = ct.type("i32"         , true );
      t_ref_i64       = ct.type("i64"         , true );
      t_ref_u8        = ct.type("u8"          , true );
      t_ref_u16       = ct.type("u16"         , true );
      t_ref_u32       = ct.type("u32"         , true );
      t_ref_u64       = ct.type("u64"         , true );
      t_ref_f32       = ct.type("f32"         , true );
      t_ref_f64       = ct.type("f64"         , true );
      t_bool          = ct.type("bool"        , false);
      t_fuzion        = ct.type("fuzion"      , false);
      t_string        = ct.type(FuzionConstants.STRING_NAME, false);
      t_conststring   = ct.type("conststring" , false);
      t_object        = ct.type(FuzionConstants.OBJECT_NAME, false);
      t_unit          = ct.type("unit"        , false);
      t_void          = ct.type("void"        , false);
      f_void          = universe.get(mod, "void");
      f_choice        = universe.get(mod, "choice");
      f_TRUE          = universe.get(mod, "TRUE");
      f_FALSE         = universe.get(mod, "FALSE");
      f_bool          = universe.get(mod, "bool");
      f_bool_NOT      = f_bool.get(mod, "prefix !");
      f_bool_AND      = f_bool.get(mod, "infix &&");
      f_bool_OR       = f_bool.get(mod, "infix ||");
      f_bool_IMPLIES  = f_bool.get(mod, "infix :");
      f_debug         = universe.get(mod, "debug", 0);
      f_debugLevel    = universe.get(mod, "debugLevel");
      f_function      = universe.get(mod, FUNCTION_NAME);
      f_function_call = f_function.get(mod, "call");
      f_safety        = universe.get(mod, "safety");
      f_array         = universe.get(mod, "array", 5);
      f_array_internalArray = f_array.get(mod, "internalArray");
      f_fuzion                     = universe.get(mod, "fuzion");
      f_fuzion_sys                 = f_fuzion.get(mod, "sys");
      f_fuzion_sys_array           = f_fuzion_sys.get(mod, "internal_array");
      f_fuzion_sys_array_data      = f_fuzion_sys_array.get(mod, "data");
      f_fuzion_sys_array_length    = f_fuzion_sys_array.get(mod, "length");
      f_Type                       = universe.get(mod, "Type");
      f_Types                      = universe.get(mod, "Types");
      f_Types_get                  = f_Types.get(mod, "get");
      resolved = this;
      t_ADDRESS  .resolveArtificialType(universe.get(mod, FuzionConstants.OBJECT_NAME));
      t_UNDEFINED.resolveArtificialType(universe);
      t_ERROR    .resolveArtificialType(f_ERROR);
    }
    Resolved(Resolution res, AbstractFeature universe)
    {
      this(res._module, (name, ref) -> Type.type(res, ref, name, universe), universe);

      var internalTypes = new AbstractType[] {
        t_i8         ,
        t_i16        ,
        t_i32        ,
        t_i64        ,
        t_u8         ,
        t_u16        ,
        t_u32        ,
        t_u64        ,
        t_f32        ,
        t_f64        ,
        t_bool       ,
        t_fuzion     ,
        t_string     ,
        t_conststring,
        t_object     ,
        t_unit       ,
        t_void       };

      for (var t : internalTypes)
        {
          res.resolveTypes(t.featureOfType());
        }
    }
  }

  /*----------------------------  variables  ----------------------------*/


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Reset static fields such as the intern()ed types.
   */
  public static void reset()
  {
    types = new TreeMap<>();
    resolved = null;
    t_ADDRESS   = new Type(ADDRESS_NAME  );
    t_UNDEFINED = new Type(UNDEFINED_NAME);
    t_ERROR     = new Type(ERROR_NAME    );
    f_ERROR     = new Feature(true);
  }


  /**
   * Find the unique instance of t
   */
  public static AbstractType intern(AbstractType at)
  {
    if (PRECONDITIONS) require
      ((!(at instanceof Type t)) || t.isGenericArgument() || t.feature != null || Errors.count() > 0);

    if (at instanceof Type t)
      {
        if (!t.isGenericArgument())
          {
            Types.intern(t.outer());
          }
        var tg = t._generics.listIterator();
        while (tg.hasNext())
          {
            tg.set(intern(tg.next()));
          }
        Type existing = t._interned;
        if (existing == null)
          {
            existing = types.get(t);
            if (existing == null)
              {
                types.put(t,t);
                existing = t;
              }
            t._interned = existing;
          }
        at = existing;
      }
    return at;
  }


  /**
   * Return the total number of unique types stored globally.
   */
  public static int num()
  {
    return types.size();
  }

}
