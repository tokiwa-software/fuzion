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
import dev.flang.util.FuzionOptions;

/*---------------------------------------------------------------------*/


/**
 * Types manages the types used in the system.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Types extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  private static Map<ResolvedNormalType, ResolvedNormalType> types;

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
    public final AbstractType t_any;
    private final AbstractType t_fuzion;
    public final AbstractType t_string;
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
    public final AbstractFeature f_function;
    public final AbstractFeature f_function_call;
    public final AbstractFeature f_safety;
    public final AbstractFeature f_array;
    public final AbstractFeature f_array_internal_array;
    public final AbstractFeature f_fuzion;
    public final AbstractFeature f_fuzion_java;
    public final AbstractFeature f_fuzion_java_object;
    public final AbstractFeature f_fuzion_java_object_ref;
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
    public static interface CreateType
    {
      AbstractType type(String name);
    }
    public Resolved(SrcModule mod, CreateType ct, AbstractFeature universe)
    {
      this.universe = universe;
      t_i8            = ct.type("i8");
      t_i16           = ct.type("i16");
      t_i32           = ct.type("i32");
      t_i64           = ct.type("i64");
      t_u8            = ct.type("u8");
      t_u16           = ct.type("u16");
      t_u32           = ct.type("u32");
      t_u64           = ct.type("u64");
      t_f32           = ct.type("f32");
      t_f64           = ct.type("f64");
      t_bool          = ct.type("bool");
      t_fuzion        = ct.type("fuzion");
      t_string        = ct.type(FuzionConstants.STRING_NAME);
      t_Const_String  = ct.type("Const_String");
      t_any           = ct.type(FuzionConstants.ANY_NAME);
      t_unit          = ct.type(FuzionConstants.UNIT_NAME);
      t_void          = ct.type("void");
      f_id            = universe.get(mod, "id", 2);
      f_void          = universe.get(mod, "void");
      f_choice        = universe.get(mod, "choice");
      f_TRUE          = universe.get(mod, "TRUE");
      f_FALSE         = universe.get(mod, "FALSE");
      f_bool          = universe.get(mod, "bool");
      f_bool_NOT      = f_bool.get(mod, FuzionConstants.PREFIX_OPERATOR_PREFIX + "!");
      f_bool_AND      = f_bool.get(mod, FuzionConstants.INFIX_OPERATOR_PREFIX + "&&");
      f_bool_OR       = f_bool.get(mod, FuzionConstants.INFIX_OPERATOR_PREFIX + "||");
      f_bool_IMPLIES  = f_bool.get(mod, FuzionConstants.INFIX_OPERATOR_PREFIX + ":");
      f_debug         = universe.get(mod, "debug", 0);
      f_debug_level   = universe.get(mod, "debug_level");
      f_function      = universe.get(mod, FUNCTION_NAME);
      f_function_call = f_function.get(mod, "call");
      f_safety        = universe.get(mod, "safety");
      f_array         = universe.get(mod, "array", 5);
      f_array_internal_array = f_array.get(mod, "internal_array");
      f_fuzion                     = universe.get(mod, "fuzion");
      f_fuzion_java                = f_fuzion.get(mod, "java");
      f_fuzion_java_object         = f_fuzion_java.get(mod, "Java_Object");
      f_fuzion_java_object_ref     = f_fuzion_java_object.get(mod, "Java_Ref");
      f_fuzion_sys                 = f_fuzion.get(mod, "sys");
      f_fuzion_sys_array           = f_fuzion_sys.get(mod, "internal_array");
      f_fuzion_sys_array_data      = f_fuzion_sys_array.get(mod, "data");
      f_fuzion_sys_array_length    = f_fuzion_sys_array.get(mod, "length");
      f_concur                     = universe.get(mod, "concur");
      f_concur_atomic              = f_concur.get(mod, "atomic");
      f_concur_atomic_v            = f_concur_atomic.get(mod, "v");
      f_Type                       = universe.get(mod, "Type");
      f_Types                      = universe.get(mod, "Types");
      f_Types_get                  = f_Types.get(mod, "get");
      f_Lazy                       = universe.get(mod, LAZY_NAME);
      f_Unary                      = universe.get(mod, UNARY_NAME);
      resolved = this;
      ((ArtificialBuiltInType) t_ADDRESS  ).resolveArtificialType(universe.get(mod, FuzionConstants.ANY_NAME));
      ((ArtificialBuiltInType) t_UNDEFINED).resolveArtificialType(universe);
      ((ArtificialBuiltInType) t_ERROR    ).resolveArtificialType(f_ERROR);
    }
    Resolved(Resolution res, AbstractFeature universe)
    {
      this(res._module, (name) -> UnresolvedType.type(res, false, name, universe), universe);

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
        t_Const_String,
        t_any        ,
        t_unit       ,
        t_void
      };

      for (var t : internalTypes)
        {
          res.resolveTypes(t.featureOfType());
        }
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
    types = new TreeMap<>();
    resolved = null;
    t_ADDRESS   = new ArtificialBuiltInType(ADDRESS_NAME  );
    t_UNDEFINED = new ArtificialBuiltInType(UNDEFINED_NAME);
    t_ERROR     = new ArtificialBuiltInType(ERROR_NAME    );
    f_ERROR     = new Feature(true);
    _options    = options;
  }


  /**
   * Find the unique instance of t
   */
  public static AbstractType intern(AbstractType at)
  {
    if (PRECONDITIONS) require
      (!(at instanceof UnresolvedType t) || Errors.any());

    if (at instanceof ResolvedNormalType t && t.unresolvedGenerics().isEmpty())
      {
        var existing = types.get(t);
        if (existing == null)
          {
            types.put(t,t);
            existing = t;
          }
        t._generics.freeze();
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
