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
import dev.flang.util.SourcePosition;

/*---------------------------------------------------------------------*/


/**
 * Types manages the types used in the system.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Types extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  private static final Map<Type, Type> types = new TreeMap<>();

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
  static final String ERROR_NAME = Errors.ERROR_STRING;


  /**
   * Names if internal types that are not backed by physical feature defintions.
   */
  static Set<String> INTERNAL_NAMES = Collections.<String>unmodifiableSet
    (new TreeSet<>(Arrays.asList(ADDRESS_NAME,
                                 UNDEFINED_NAME,
                                 ERROR_NAME)));

  /* artificial type for the address of a value type, used for outer refs to value instances */
  public static Type t_ADDRESS = new Type(ADDRESS_NAME);

  /* artificial type for Expr that does not have a well defined type such as the
   * union of two distinct types */
  public static Type t_UNDEFINED = new Type(UNDEFINED_NAME);

  /* artificial type for Expr with unknown type due to compilation error */
  public static Type t_ERROR = new Type(ERROR_NAME);

  /* artificial feature used when feature is not known due to compilation error */
  public static Feature f_ERROR = new Feature(true);

  public static class Resolved
  {
    public final Feature universe;
    public final Type t_i8  ;
    public final Type t_i16 ;
    public final Type t_i32 ;
    public final Type t_i64 ;
    public final Type t_u8  ;
    public final Type t_u16 ;
    public final Type t_u32 ;
    public final Type t_u64 ;
    public final Type t_f32 ;
    public final Type t_f64 ;
    public final Type t_ref_i8  ;
    public final Type t_ref_i16 ;
    public final Type t_ref_i32 ;
    public final Type t_ref_i64 ;
    public final Type t_ref_u8  ;
    public final Type t_ref_u16 ;
    public final Type t_ref_u32 ;
    public final Type t_ref_u64 ;
    public final Type t_ref_f32 ;
    public final Type t_ref_f64 ;
    public final Type t_bool;
    public final Type t_object;
    public final Type t_sys;
    public final Type t_string;
    public final Type t_conststring;
    public final Type t_unit;

    /* void will be used as the initial result type of tail recursive calls of
     * the form
     *
     *    f => if c f else x
     *
     * since the union of void  with any other type is the other type.
     */
    public final Type t_void;
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
    public final AbstractFeature f_sys;
    public final AbstractFeature f_sys_array;
    public final AbstractFeature f_sys_array_length;
    public final AbstractFeature f_sys_array_data;
    Resolved(Resolution res, Feature universe)
    {
      this.universe = universe;
      t_i8            = Type.type(res,      "i8"          , universe);
      t_i16           = Type.type(res,      "i16"         , universe);
      t_i32           = Type.type(res,      "i32"         , universe);
      t_i64           = Type.type(res,      "i64"         , universe);
      t_u8            = Type.type(res,      "u8"          , universe);
      t_u16           = Type.type(res,      "u16"         , universe);
      t_u32           = Type.type(res,      "u32"         , universe);
      t_u64           = Type.type(res,      "u64"         , universe);
      t_f32           = Type.type(res,      "f32"         , universe);
      t_f64           = Type.type(res,      "f64"         , universe);
      t_ref_i8        = Type.type(res,true, "i8"          , universe);
      t_ref_i16       = Type.type(res,true, "i16"         , universe);
      t_ref_i32       = Type.type(res,true, "i32"         , universe);
      t_ref_i64       = Type.type(res,true, "i64"         , universe);
      t_ref_u8        = Type.type(res,true, "u8"          , universe);
      t_ref_u16       = Type.type(res,true, "u16"         , universe);
      t_ref_u32       = Type.type(res,true, "u32"         , universe);
      t_ref_u64       = Type.type(res,true, "u64"         , universe);
      t_ref_f32       = Type.type(res,true, "f32"         , universe);
      t_ref_f64       = Type.type(res,true, "f64"         , universe);
      t_bool          = Type.type(res,      "bool"        , universe);
      t_sys           = Type.type(res,      "sys"         , universe);
      t_string        = Type.type(res,      "string"      , universe);
      t_conststring   = Type.type(res,      "conststring" , universe);
      t_object        = Type.type(res,      "Object"      , universe);
      t_unit          = Type.type(res,      "unit"        , universe);
      t_void          = Type.type(res,      "void"        , universe);
      f_void          = universe.get(res, "void");
      f_choice        = universe.get(res, "choice");
      f_TRUE          = universe.get(res, "TRUE");
      f_FALSE         = universe.get(res, "FALSE");
      f_bool          = universe.get(res, "bool");
      f_bool_NOT      = f_bool.get(res, "prefix !");
      f_bool_AND      = f_bool.get(res, "infix &&");
      f_bool_OR       = f_bool.get(res, "infix ||");
      f_bool_IMPLIES  = f_bool.get(res, "infix :");
      f_debug         = universe.get(res, "debug", 0);
      f_debugLevel    = universe.get(res, "debugLevel");
      f_function      = universe.get(res, FUNCTION_NAME);
      f_function_call = f_function.get(res, "call");
      f_safety        = universe.get(res, "safety");
      f_array         = universe.get(res, "array", 1);
      f_array_internalArray = f_array.get(res, "internalArray");
      f_sys                 = universe.get(res, "sys");
      f_sys_array           = f_sys.get(res, "array");
      f_sys_array_data      = f_sys_array.get(res, "data");
      f_sys_array_length    = f_sys_array.get(res, "length");
      resolved = this;
      t_ADDRESS  .resolveArtificialType(universe.get(res, "Object"));
      t_UNDEFINED.resolveArtificialType(universe);
      t_ERROR    .resolveArtificialType(f_ERROR);
    }

  }

  /*----------------------------  variables  ----------------------------*/


  /*-----------------------------  methods  -----------------------------*/


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
            t.outerInterned();
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
