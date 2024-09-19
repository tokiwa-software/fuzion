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
   * Names of internal types that are not backed by physical feature definitions.
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
    public final AbstractFeature f_true;
    public final AbstractFeature f_false;
    public final AbstractFeature f_bool;
    public final AbstractFeature f_bool_NOT;
    public final AbstractFeature f_bool_AND;
    public final AbstractFeature f_bool_OR;
    public final AbstractFeature f_bool_IMPLIES;
    public final AbstractFeature f_bool_TERNARY;
    public final AbstractFeature f_debug;
    public final AbstractFeature f_debug_level;
    public final AbstractFeature f_Const_String_utf8_data;
    public final AbstractFeature f_Function;
    public final AbstractFeature f_Function_call;
    public final AbstractFeature f_safety;
    public final AbstractFeature f_array;
    public final AbstractFeature f_array_internal_array;
    public final AbstractFeature f_effect;
    public final AbstractFeature f_effect_static_finally;
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
    public final AbstractFeature f_Type_infix_colon;
    public final AbstractFeature f_Type_infix_colon_true;
    public final AbstractFeature f_Type_infix_colon_false;
    public final AbstractFeature f_type_as_value;
    public final AbstractFeature f_Lazy;
    public final AbstractFeature f_Unary;
    public final AbstractFeature f_auto_unwrap;
    public final Set<AbstractType> numericTypes;
    public Resolved(SrcModule mod, AbstractFeature universe)
    {
      this((target, fn) -> target.get(mod, fn.baseName(), fn.argCount()), universe, true);
    }
    Resolved(Resolution res, AbstractFeature universe)
    {
      this(res._module, universe);

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
      AbstractFeature lookup(AbstractFeature target, FeatureName fn);
      default AbstractFeature lookup(AbstractFeature target, String name, int argCount)
      {
        var result = lookup(target, FeatureName.get(name,  argCount));
        check(result != null);
        return result;
      }
      default AbstractType lookupType(AbstractFeature target, String name, int argCount)
      {
        return lookup(target, name, argCount).selfType();
      }
    }
    public Resolved(LookupFeature lf, AbstractFeature universe, boolean forFrontEnd)
    {
      this.universe = universe;
      t_i8                      = lf.lookupType(universe, "i8", 1);
      t_i16                     = lf.lookupType(universe, "i16", 1);
      t_i32                     = lf.lookupType(universe, "i32", 1);
      t_i64                     = lf.lookupType(universe, "i64", 1);
      t_u8                      = lf.lookupType(universe, "u8", 1);
      t_u16                     = lf.lookupType(universe, "u16", 1);
      t_u32                     = lf.lookupType(universe, "u32", 1);
      t_u64                     = lf.lookupType(universe, "u64", 1);
      t_f32                     = lf.lookupType(universe, "f32", 1);
      t_f64                     = lf.lookupType(universe, "f64", 1);
      t_bool                    = lf.lookupType(universe, "bool", 0);
      t_fuzion                  = lf.lookupType(universe, "fuzion", 0);
      t_String                  = lf.lookupType(universe, FuzionConstants.STRING_NAME, 0);
      t_Const_String            = lf.lookupType(universe, "Const_String", 0);
      t_Any                     = lf.lookupType(universe, FuzionConstants.ANY_NAME, 0);
      t_unit                    = lf.lookupType(universe, FuzionConstants.UNIT_NAME, 0);
      t_void                    = lf.lookupType(universe, "void", 0);
      t_codepoint               = lf.lookupType(universe, "codepoint", 1);
      f_id                      = lf.lookup(universe, "id", 2);
      f_void                    = lf.lookup(universe, "void", 0);
      f_choice                  = lf.lookup(universe, "choice", 1);
      f_TRUE                    = lf.lookup(universe, "TRUE", 0);
      f_FALSE                   = lf.lookup(universe, "FALSE", 0);
      f_true                    = lf.lookup(universe, "true", 0);
      f_false                   = lf.lookup(universe, "false", 0);
      f_bool                    = lf.lookup(universe, "bool", 0);
      f_bool_NOT                = forFrontEnd ? lf.lookup(f_bool, FuzionConstants.PREFIX_OPERATOR_PREFIX + "!"   , 0) : null;
      f_bool_AND                = forFrontEnd ? lf.lookup(f_bool, FuzionConstants.INFIX_OPERATOR_PREFIX + "&&"   , 1) : null;
      f_bool_OR                 = forFrontEnd ? lf.lookup(f_bool, FuzionConstants.INFIX_OPERATOR_PREFIX + "||"   , 1) : null;
      f_bool_IMPLIES            = forFrontEnd ? lf.lookup(f_bool, FuzionConstants.INFIX_OPERATOR_PREFIX + ":"    , 1) : null;
      f_bool_TERNARY            = forFrontEnd ? lf.lookup(f_bool, FuzionConstants.TERNARY_OPERATOR_PREFIX + "? :", 3) : null;
      f_Const_String_utf8_data  = lf.lookup(lf.lookup(universe, "Const_String", 0), "utf8_data", 0);
      f_debug                   = lf.lookup(universe, "debug", 0);
      f_debug_level             = lf.lookup(universe, "debug_level", 0);
      f_Function                = lf.lookup(universe, FUNCTION_NAME, 2);
      f_Function_call           = lf.lookup(f_Function, "call", 1);
      f_safety                  = lf.lookup(universe, "safety", 0);
      f_array                   = lf.lookup(universe, "array", 5);
      f_array_internal_array    = lf.lookup(f_array, "internal_array", 0);
      f_effect                  = lf.lookup(universe, "effect", 0);
      f_effect_static_finally   = lf.lookup(f_effect, "static_finally", 0);
      f_error                   = lf.lookup(universe, "error", 1);
      f_error_msg               = lf.lookup(f_error, "msg", 0);
      f_fuzion                  = lf.lookup(universe, "fuzion", 0);
      f_fuzion_java             = lf.lookup(f_fuzion, "java", 0);
      f_fuzion_Java_Object      = lf.lookup(f_fuzion_java, "Java_Object", 1);
      f_fuzion_Java_Object_Ref  = lf.lookup(f_fuzion_Java_Object, "Java_Ref", 0);
      f_fuzion_sys              = lf.lookup(f_fuzion, "sys", 0);
      f_fuzion_sys_array        = lf.lookup(f_fuzion_sys, "internal_array", 3);
      f_fuzion_sys_array_data   = lf.lookup(f_fuzion_sys_array, "data", 0);
      f_fuzion_sys_array_length = lf.lookup(f_fuzion_sys_array, "length", 0);
      f_concur                  = lf.lookup(universe, "concur", 0);
      f_concur_atomic           = lf.lookup(f_concur, "atomic", 2);
      f_concur_atomic_v         = lf.lookup(f_concur_atomic, "v", 0);
      f_Type                    = lf.lookup(universe, "Type", 0);
      f_Type_infix_colon        = lf.lookup(f_Type, "infix :", 1);
      f_Type_infix_colon_true   = lf.lookup(f_Type, "infix_colon_true", 1);
      f_Type_infix_colon_false  = lf.lookup(f_Type, "infix_colon_false", 1);
      f_type_as_value           = lf.lookup(universe, "type_as_value", 1);
      f_Lazy                    = lf.lookup(universe, LAZY_NAME, 1);
      f_Unary                   = lf.lookup(universe, UNARY_NAME, 2);
      f_auto_unwrap             = lf.lookup(universe, "auto_unwrap", 2);
      resolved = this;
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
      ((ArtificialBuiltInType) t_ADDRESS  ).resolveArtificialType(lf.lookup(universe, FuzionConstants.ANY_NAME, 0));
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
