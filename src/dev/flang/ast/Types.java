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
import java.util.Set;
import java.util.TreeSet;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.FuzionOptions;
import dev.flang.util.List;

/*---------------------------------------------------------------------*/


/**
 * Types manages the types used in the system.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Types extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Name of abstract features for function types:
   */
  public static final String FUNCTION_NAME = "Function";

  /**
   * Name of abstract features for lazy types:
   */
  public static final String LAZY_NAME = "Lazy";

  /**
   * Name of abstract features for unary function types:
   */
  public static final String UNARY_NAME = "Unary";

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
   * branches of an if or match, or the type of the result var before type
   * inference has determined the result type.
   */
  static final String UNDEFINED_NAME = "--UNDEFINED--";


  /**
   * Dummy name used for error type t_ERROR which is used in case of compilation
   * time errors.
   */
  public static final String ERROR_NAME = Errors.ERROR_STRING;


  /**
   * Names if internal types that are not backed by physical feature definitions.
   */
  static Set<String> INTERNAL_NAMES = Collections.<String>unmodifiableSet
    (new TreeSet<>(Arrays.asList(ADDRESS_NAME,
                                 UNDEFINED_NAME,
                                 ERROR_NAME)));

  /* artificial type for the address of a value type, used for outer refs to value instances */
  public static AbstractType t_ADDRESS;

  /* artificial type for Expr that does not have a well defined type such as the
   * union of two distinct types */
  public static AbstractType t_UNDEFINED;

  /* artificial type for Expr with unknown type due to compilation error */
  public static ResolvedType t_ERROR;

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
    public final AbstractType t_bool;
    public final AbstractType t_Any;
    private final AbstractType t_fuzion;
    public final AbstractType t_String;
    public final AbstractType t_Const_String;
    public final AbstractType t_unit;

    /* void will be used as the initial result type of tail recursive calls of
     * the form
     *
     *    f => if c f else x
     *
     * since the union of void  with any other type is the other type.
     */
    public final AbstractType t_void;
    public final AbstractType t_codepoint;
    public final AbstractFeature f_id;
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
    public final AbstractFeature f_debug_level;
    public final AbstractFeature f_Function;
    public final AbstractFeature f_Function_call;
    public final AbstractFeature f_safety;
    public final AbstractFeature f_array;
    public final AbstractFeature f_array_internal_array;
    public final AbstractFeature f_error;
    public final AbstractFeature f_error_msg;
    public final AbstractFeature f_fuzion;
    public final AbstractFeature f_fuzion_java;
    public final AbstractFeature f_fuzion_Java_Object;
    public final AbstractFeature f_fuzion_Java_Object_Ref;
    public final AbstractFeature f_fuzion_sys;
    public final AbstractFeature f_fuzion_sys_array;
    public final AbstractFeature f_fuzion_sys_array_length;
    public final AbstractFeature f_fuzion_sys_array_data;
    public final AbstractFeature f_concur;
    public final AbstractFeature f_concur_atomic;
    public final AbstractFeature f_concur_atomic_v;
    public final AbstractFeature f_Type;
    public final AbstractFeature f_Types;
    public final AbstractFeature f_Types_get;
    public final AbstractFeature f_Lazy;
    public final AbstractFeature f_Unary;
    public final AbstractFeature f_auto_unwrap;
    public final Set<AbstractType> numericTypes;
    public Resolved(Resolution res)
    {
      this((outer, fn) -> res._module.lookupFeature(outer, fn, null), res.universe);

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
        t_String     ,
        t_Const_String,
        t_Any        ,
        t_unit       ,
        t_void       ,
        t_codepoint
      };

      for (var t : internalTypes)
        {
          res.resolveTypes(t.feature());
        }
    }
    public static interface LookupFeature
    {
      AbstractFeature lookupFeature(AbstractFeature outer, FeatureName fn);
    }
    public Resolved(LookupFeature luf, AbstractFeature _universe)
    {
      this.universe = _universe;
      resolved = this;
      f_id                         = luf.lookupFeature(universe, FeatureName.get("id"    , 2));
      f_void                       = luf.lookupFeature(universe, FeatureName.get("void"  , 0));
      t_void                       = f_void.selfType();
      f_choice                     = luf.lookupFeature(universe, FeatureName.get("choice", 1));
      f_TRUE                       = luf.lookupFeature(universe, FeatureName.get("TRUE"  , 0));
      f_FALSE                      = luf.lookupFeature(universe, FeatureName.get("FALSE" , 0));
      f_bool                       = luf.lookupFeature(universe, FeatureName.get("bool"  , 0));
      t_bool                       = f_bool.selfType();
      f_bool_NOT                   = luf.lookupFeature(f_bool, FeatureName.get(FuzionConstants.PREFIX_OPERATOR_PREFIX + "!", 0));
      f_bool_AND                   = luf.lookupFeature(f_bool, FeatureName.get(FuzionConstants.INFIX_OPERATOR_PREFIX + "&&", 1));
      f_bool_OR                    = luf.lookupFeature(f_bool, FeatureName.get(FuzionConstants.INFIX_OPERATOR_PREFIX + "||", 1));
      f_bool_IMPLIES               = luf.lookupFeature(f_bool, FeatureName.get(FuzionConstants.INFIX_OPERATOR_PREFIX + ":" , 1));
      f_debug                      = luf.lookupFeature(universe, FeatureName.get("debug", 0));
      f_debug_level                = luf.lookupFeature(universe, FeatureName.get("debug_level", 0));
      f_Function                   = luf.lookupFeature(universe, FeatureName.get(FUNCTION_NAME, 2));
      f_Function_call              = luf.lookupFeature(f_Function, FeatureName.get("call", 1));
      f_safety                     = luf.lookupFeature(universe, FeatureName.get("safety", 0));
      f_array                      = luf.lookupFeature(universe, FeatureName.get("array", 5));
      f_array_internal_array       = luf.lookupFeature(f_array, FeatureName.get("internal_array", 0));
      f_error                      = luf.lookupFeature(universe, FeatureName.get("error", 1));
      f_error_msg                  = luf.lookupFeature(f_error, FeatureName.get("msg", 0));
      f_fuzion                     = luf.lookupFeature(universe, FeatureName.get("fuzion", 0));
      f_fuzion_java                = luf.lookupFeature(f_fuzion, FeatureName.get("java", 0));
      f_fuzion_Java_Object         = luf.lookupFeature(f_fuzion_java, FeatureName.get("Java_Object", 1));
      f_fuzion_Java_Object_Ref     = luf.lookupFeature(f_fuzion_Java_Object, FeatureName.get("Java_Ref", 0));
      f_fuzion_sys                 = luf.lookupFeature(f_fuzion, FeatureName.get("sys", 0));
      f_fuzion_sys_array           = luf.lookupFeature(f_fuzion_sys, FeatureName.get("internal_array", 3));
      f_fuzion_sys_array_data      = luf.lookupFeature(f_fuzion_sys_array, FeatureName.get("data", 0));
      f_fuzion_sys_array_length    = luf.lookupFeature(f_fuzion_sys_array, FeatureName.get("length", 0));
      f_concur                     = luf.lookupFeature(universe, FeatureName.get("concur", 0));
      f_concur_atomic              = luf.lookupFeature(f_concur, FeatureName.get("atomic", 2));
      f_concur_atomic_v            = luf.lookupFeature(f_concur_atomic, FeatureName.get("v", 0));
      f_Type                       = luf.lookupFeature(universe, FeatureName.get("Type", 0));
      f_Types                      = luf.lookupFeature(universe, FeatureName.get("Types", 0));
      f_Types_get                  = luf.lookupFeature(f_Types, FeatureName.get("get", 1));
      f_Lazy                       = luf.lookupFeature(universe, FeatureName.get(LAZY_NAME , 1));
      f_Unary                      = luf.lookupFeature(universe, FeatureName.get(UNARY_NAME, 2));
      f_auto_unwrap                = luf.lookupFeature(universe, FeatureName.get("auto_unwrap", 2));
      t_i8                         = luf.lookupFeature(universe, FeatureName.get("i8" , 1)).selfType();
      t_i16                        = luf.lookupFeature(universe, FeatureName.get("i16", 1)).selfType();
      t_i32                        = luf.lookupFeature(universe, FeatureName.get("i32", 1)).selfType();
      t_i64                        = luf.lookupFeature(universe, FeatureName.get("i64", 1)).selfType();
      t_u8                         = luf.lookupFeature(universe, FeatureName.get("u8" , 1)).selfType();
      t_u16                        = luf.lookupFeature(universe, FeatureName.get("u16", 1)).selfType();
      t_u32                        = luf.lookupFeature(universe, FeatureName.get("u32", 1)).selfType();
      t_u64                        = luf.lookupFeature(universe, FeatureName.get("u64", 1)).selfType();
      t_f32                        = luf.lookupFeature(universe, FeatureName.get("f32", 1)).selfType();
      t_f64                        = luf.lookupFeature(universe, FeatureName.get("f64", 1)).selfType();
      t_fuzion                     = f_fuzion.selfType();
      t_String                     = luf.lookupFeature(universe, FeatureName.get(FuzionConstants.STRING_NAME, 0)).selfType();
      t_Const_String               = luf.lookupFeature(universe, FeatureName.get("Const_String",0)).selfType();
      t_Any                        = luf.lookupFeature(universe, FeatureName.get(FuzionConstants.ANY_NAME,0)).selfType();
      t_unit                       = luf.lookupFeature(universe, FeatureName.get(FuzionConstants.UNIT_NAME,0)).selfType();
      t_codepoint                  = luf.lookupFeature(universe, FeatureName.get("codepoint", 1)).selfType();
      numericTypes = new TreeSet<AbstractType>(new List<>(
        t_i8,
        t_i16,
        t_i32,
        t_i64,
        t_u8,
        t_u16,
        t_u32,
        t_u64,
        t_f32,
        t_f64));
      ((ArtificialBuiltInType) t_ADDRESS  ).resolveArtificialType(t_Any.feature());
      ((ArtificialBuiltInType) t_UNDEFINED).resolveArtificialType(universe);
      ((ArtificialBuiltInType) t_ERROR    ).resolveArtificialType(f_ERROR);
    }
  }


  /**
   * The current options as a static field.
   */
  // NYI remove this when we have a better way of accessing current Resolution.
  static FuzionOptions _options;


  /*----------------------------  variables  ----------------------------*/


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Reset static fields such as the intern()ed types.
   */
  public static void reset(FuzionOptions options)
  {
    resolved = null;
    t_ADDRESS   = new ArtificialBuiltInType(ADDRESS_NAME  );
    t_UNDEFINED = new ArtificialBuiltInType(UNDEFINED_NAME);
    t_ERROR     = new ArtificialBuiltInType(ERROR_NAME    );
    f_ERROR     = new Feature(true);
    _options    = options;
  }

}
