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


  /**
   * Name of abstract features for function types:
   */
  public static final String FUNCTION_NAME = "Function";

  /**
   * Name of abstract features for Nullary types:
   */
  public static final String NULLARY_NAME = "Nullary";

  /**
   * Name of abstract features for lazy types:
   */
  public static final String LAZY_NAME = "Lazy";

  /**
   * Name of abstract features for unary function types:
   */
  public static final String UNARY_NAME = "Unary";

  /**
   * Name of abstract features for binary function types:
   */
  public static final String BINARY_NAME = "Binary";

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
   * Dummy name used for type t_FORWARD_CYCLIC which is used to
   * indicate a forward or cylic type inference.
   */
  private static final String FORWARD_CYCLIC_NAME = "FORWARD_CYCLIC";


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

  /* artificial type for Expr with unknown type due to compilation error */
  public static final AbstractType t_FORWARD_CYCLIC = new ArtificialBuiltInType(FORWARD_CYCLIC_NAME);

  /* artificial feature used when feature is not known due to compilation error */
  public static final Feature f_ERROR = new Feature(true)
  {
    @Override public AbstractType selfType() { return t_ERROR; };
  };

  public static class Resolved
  {
    public final TreeSet<AbstractType> legalNativeResultTypes;
    public final TreeSet<AbstractType> legalNativeArgumentTypes;
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
    public final AbstractFeature f_Function;
    public final AbstractFeature f_Function_call;
    public final AbstractFeature f_safety;
    public final AbstractFeature f_array;
    public final AbstractFeature f_array_internal_array;
    public final AbstractFeature f_effect;
    public final AbstractFeature f_effect_finally;
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
    public final AbstractFeature f_Nullary;
    public final AbstractFeature f_Lazy;
    public final AbstractFeature f_Unary;
    public final AbstractFeature f_Binary;
    public final AbstractFeature f_auto_unwrap;
    public final Set<AbstractType> numericTypes;
    public Resolved(AbstractModule mod, AbstractFeature universe, boolean forFrontEnd)
    {
      this.universe = universe;
      t_i8                      = universe.get(mod, FuzionConstants.I8_NAME,  1).selfType();
      t_i16                     = universe.get(mod, FuzionConstants.I16_NAME, 1).selfType();
      t_i32                     = universe.get(mod, FuzionConstants.I32_NAME, 1).selfType();
      t_i64                     = universe.get(mod, FuzionConstants.I64_NAME, 1).selfType();
      t_u8                      = universe.get(mod, FuzionConstants.U8_NAME,  1).selfType();
      t_u16                     = universe.get(mod, FuzionConstants.U16_NAME, 1).selfType();
      t_u32                     = universe.get(mod, FuzionConstants.U32_NAME, 1).selfType();
      t_u64                     = universe.get(mod, FuzionConstants.U64_NAME, 1).selfType();
      t_f32                     = universe.get(mod, FuzionConstants.F32_NAME, 1).selfType();
      t_f64                     = universe.get(mod, FuzionConstants.F64_NAME, 1).selfType();
      t_bool                    = universe.get(mod, "bool", 0).selfType();
      t_fuzion                  = universe.get(mod, "fuzion", 0).selfType();
      t_String                  = universe.get(mod, FuzionConstants.STRING_NAME, 0).selfType();
      t_Any                     = universe.get(mod, FuzionConstants.ANY_NAME, 0).selfType();
      t_unit                    = universe.get(mod, FuzionConstants.UNIT_NAME, 0).selfType();
      t_void                    = universe.get(mod, "void", 0).selfType();
      t_codepoint               = universe.get(mod, "codepoint", 1).selfType();
      f_id                      = universe.get(mod, "id", 2);
      f_void                    = universe.get(mod, "void", 0);
      f_choice                  = universe.get(mod, "choice", 1);
      f_TRUE                    = universe.get(mod, "true_", 0);
      f_FALSE                   = universe.get(mod, "false_", 0);
      f_true                    = universe.get(mod, "true", 0);
      f_false                   = universe.get(mod, "false", 0);
      f_bool                    = universe.get(mod, "bool", 0);
      f_bool_NOT                = forFrontEnd ? f_bool.get(mod, FuzionConstants.PREFIX_OPERATOR_PREFIX + "!"   , 0) : null;
      f_bool_AND                = forFrontEnd ? f_bool.get(mod, FuzionConstants.INFIX_OPERATOR_PREFIX + "&&"   , 1) : null;
      f_bool_OR                 = forFrontEnd ? f_bool.get(mod, FuzionConstants.INFIX_OPERATOR_PREFIX + "||"   , 1) : null;
      f_bool_IMPLIES            = forFrontEnd ? f_bool.get(mod, FuzionConstants.INFIX_OPERATOR_PREFIX + ":"    , 1) : null;
      f_bool_TERNARY            = forFrontEnd ? f_bool.get(mod, FuzionConstants.TERNARY_OPERATOR_PREFIX + "? :", 3) : null;
      f_debug                   = universe.get(mod, "debug", 0);
      f_debug_level             = universe.get(mod, "debug_level", 0);
      f_Function                = universe.get(mod, FUNCTION_NAME, 2);
      f_Function_call           = f_Function.get(mod, FuzionConstants.OPERATION_CALL, 1);
      f_safety                  = universe.get(mod, "safety", 0);
      f_array                   = universe.get(mod, FuzionConstants.ARRAY_NAME, 5);
      f_array_internal_array    = f_array.get(mod, "internal_array", 0);
      f_effect                  = universe.get(mod, "effect", 0);
      f_effect_finally          = f_effect.get(mod, "finally", 0);
      f_effect_static_finally   = f_effect.get(mod, "static_finally", 0);
      f_error                   = universe.get(mod, "error", 1);
      f_error_msg               = f_error.get(mod, "msg", 0);
      f_fuzion                  = universe.get(mod, "fuzion", 0);
      f_fuzion_java             = f_fuzion.get(mod, "java", 0);
      f_fuzion_Java_Object      = f_fuzion_java.get(mod, "Java_Object", 1);
      f_fuzion_Java_Object_Ref  = f_fuzion_Java_Object.get(mod, "java_ref", 0);
      f_fuzion_sys              = f_fuzion.get(mod, "sys", 0);
      f_fuzion_sys_array        = f_fuzion_sys.get(mod, "internal_array", 3);
      f_fuzion_sys_array_data   = f_fuzion_sys_array.get(mod, "data", 0);
      f_fuzion_sys_array_length = f_fuzion_sys_array.get(mod, "length", 0);
      f_concur                  = universe.get(mod, "concur", 0);
      f_concur_atomic           = f_concur.get(mod, "atomic", 2);
      f_concur_atomic_v         = f_concur_atomic.get(mod, "v", 0);
      f_Type                    = universe.get(mod, FuzionConstants.TYPE_FEAT, 0);
      f_Type_infix_colon        = f_Type.get(mod, "infix :", 1);
      f_Type_infix_colon_true   = f_Type.get(mod, "infix_colon_true", 1);
      f_Type_infix_colon_false  = f_Type.get(mod, "infix_colon_false", 1);
      f_type_as_value           = universe.get(mod, "type_as_value", 1);
      f_Nullary                 = universe.get(mod, NULLARY_NAME, 1);
      f_Lazy                    = universe.get(mod, LAZY_NAME, 1);
      f_Unary                   = universe.get(mod, UNARY_NAME, 2);
      f_Binary                  = universe.get(mod, BINARY_NAME, 3);
      f_auto_unwrap             = universe.get(mod, "auto_unwrap", 2);
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

      legalNativeResultTypes = new TreeSet<AbstractType>();
      legalNativeArgumentTypes = new TreeSet<AbstractType>();

      resolved = this;
      ((ArtificialBuiltInType) t_ADDRESS  ).resolveArtificialType(universe.get(mod, FuzionConstants.ANY_NAME));
      ((ArtificialBuiltInType) t_UNDEFINED).resolveArtificialType(
        new Feature(true) {
          FeatureName fn = FeatureName.get(UNDEFINED_NAME, 0);
          @Override
          public FeatureName featureName()
          {
            return fn;
          }
          @Override
          public boolean isUniverse()
          {
            return true;
          }
        });
      ((ArtificialBuiltInType) t_ERROR    ).resolveArtificialType(f_ERROR);
    }
    Resolved(Resolution res, AbstractFeature universe)
    {
      this(res._module, universe, true);

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

    private Call _unitCall;
    public Call unitCall(Resolution res, Context context)
    {
      if (_unitCall == null)
        {
          _unitCall =  new Call(SourcePosition.builtIn, Universe.instance, Types.resolved.t_unit.feature()).resolveTypes(res, context);
        }
      return _unitCall;
    }

    private Call _fuzionSysCall;
    public Call fuzionSysCall(Resolution res, Context context)
    {
      if (_fuzionSysCall == null)
        {
          var fuzion       = new Call(SourcePosition.builtIn, null, "fuzion").resolveTypes(res, context);
          _fuzionSysCall   = new Call(SourcePosition.builtIn, fuzion, "sys" ).resolveTypes(res, context);
        }
      return _fuzionSysCall;
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
    _options    = options;
  }

}
