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
 * Source of class FuzionConstants
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.nio.file.Path;


/**
 * FuzionConstants specify some global constants required by different modules
 * of the Fuzion language implementation.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FuzionConstants extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * String used in the dummy Expr Expr.NO_VALUE.
   */
  public static final String NO_VALUE_STRING = "**no value**";


  /**
   * String used in the dummy ParsedName.
   */
  public static final String DUMMY_NAME_STRING = "## dummy name ##";


  /**
   * Names of Java properties accepted by fz command:
   */
  public static final String FUZION_DEBUG_LEVEL_PROPERTY = "fuzion.debugLevel";
  public static final String FUZION_HOME_PROPERTY = "fuzion.home";
  public static final String FUZION_SAFETY_PROPERTY = "fuzion.safety";


  public static final String BASE_MODULE_NAME = "base";
  public static final String MAIN_MODULE_NAME = "main";


  /**
   * Special value, when not selecting field of
   * open type parameter.
   */
  public static final int NO_SELECT = -1;


  /*----------------------  special feature names  ----------------------*/


  /**
   * Prefix used for all internal names
   */
  public static final String INTERNAL_NAME_PREFIX = "#";

  /**
   * Artificial name of universe feature.
   */
  public static final String UNIVERSE_NAME    = "universe";

  /**
   * Prefix of artificially generated name of outer refs.
   */
  public static final String OUTER_REF_PREFIX = INTERNAL_NAME_PREFIX + "^<";

  /**
   * Suffix of artificially generated name of outer refs.
   */
  public static final String OUTER_REF_SUFFIX = ">";

  /**
   * Name of Any feature, i.e., the implicit parent feature of all other
   * features.
   */
  public static final String ANY_NAME    = "Any";


  /**
   * Name of unit feature. The feature is used throughout the base library
   * to indicate that no result is returned.
   */
  public static final String UNIT_NAME   = "unit";


  /**
   * Name of Type feature.
   */
  public static final String TYPE_FEAT   = "Type";


  /**
   * Name of String feature.
   */
  public static final String STRING_NAME = "String";


  /**
   * Name of i8 feature.
   */
  public static final String I8_NAME     = "i8";


  /**
   * Name of i16 feature.
   */
  public static final String I16_NAME    = "i16";


  /**
   * Name of i32 feature.
   */
  public static final String I32_NAME    = "i32";


  /**
   * Name of i64 feature.
   */
  public static final String I64_NAME    = "i64";


  /**
   * Name of u8 feature.
   */
  public static final String U8_NAME     = "u8";


  /**
   * Name of u16 feature.
   */
  public static final String U16_NAME    = "u16";


  /**
   * Name of u32 feature.
   */
  public static final String U32_NAME    = "u32";


  /**
   * Name of u64 feature.
   */
  public static final String U64_NAME    = "u64";


  /**
   * Name of f32 feature.
   */
  public static final String F32_NAME    = "f32";


  /**
   * Name of f64 feature.
   */
  public static final String F64_NAME    = "f64";


  /**
   * Name of the array feature
   */
  public static final String ARRAY_NAME        = "array";


  /**
   * Name of intrinsic {@code effect.type.instate0}.
   */
  public static final String EFFECT_INSTATE_NAME = "effect.type.instate0";


  /**
   * Name of feature {@code index []}.
   */
  public static final String FEATURE_NAME_INDEX = "index [ ]";


  /**
   * Name of feature {@code index [..]}.
   */
  public static final String FEATURE_NAME_INDEX_DOTDOT = "index [..]";


  /**
   * Name of feature {@code index [] :=}.
   */
  public static final String FEATURE_NAME_INDEX_ASSIGN = "index [ ] := ";



  /**
   * Name of implicitly declared result field in case this field is used as the
   * target of an assignment.
   */
  public static final String RESULT_NAME          = "result";


  /**
   * Operator prefixes used in feature names for prefix/infix/postfix operators:
   */
  public static final String PREFIX_OPERATOR_PREFIX = "prefix ";
  public static final String INFIX_OPERATOR_PREFIX = "infix ";
  public static final String INFIX_RIGHT_OPERATOR_PREFIX = "infix_right ";
  public static final String INFIX_RIGHT_OR_LEFT_OPERATOR_PREFIX = "infix/infix_right ";
  public static final String POSTFIX_OPERATOR_PREFIX = "postfix ";

  /**
   * Infix operator parsed for choice type syntax sugar `i32 | unit | bool`
   */
  public static final String INFIX_PIPE = INFIX_RIGHT_OR_LEFT_OPERATOR_PREFIX + "|";

  /**
   * Name of the feature that defines choice types.
   */
  public static final String CHOICE_NAME = "choice";

  /**
   * Infix operator parsed for function type `a.b->c`
   */
  public static final String INFIX_ARROW = INFIX_RIGHT_OR_LEFT_OPERATOR_PREFIX + "->";


  /**
   * An operator that should match both, prefix and postfix operators. This is
   * used for partial application where {@code -} applied used as a unary function may
   * result in {@code prefix -} or {@code postfix -}.
   */
  public static final String UNARY_OPERATOR_PREFIX = "unary ";
  public static final String TERNARY_OPERATOR_PREFIX = "ternary ";


  public static final String OPERATION_CALL = "call";
  public static final String OPERATION_ASSIGNMENT = "assignment";


  /**
   * Artificial name of implicitly declared result field in case the assignment
   * to result is implicitly from the last expression's value.
   */
  public static final String INTERNAL_RESULT_NAME = INTERNAL_NAME_PREFIX + "result";


  /**
   * Prefix for names of anonymous inner features.
   */
  public static final String ANONYMOUS_FEATURE_PREFIX = INTERNAL_NAME_PREFIX + "anonymous";


  /**
   * Name of type features.
   */
  public static final String TYPE_NAME = INTERNAL_NAME_PREFIX + "type";


  /**
   * Name of type parameter for type features.  This type parameter will be set
   * to the actual corresponding type, i.e., including the type's type
   * parameters.
   *
   * NOTE: Here, the INTERNAL_NAME_PREFIX is not used as a prefix since feature
   * names with this prefix will be removed from .fum files which results in
   * this not being found in redefinitions.
   */
  public static final String COTYPE_THIS_TYPE = "THIS" + INTERNAL_NAME_PREFIX + "TYPE";


  /**
   * Field introduced in, e.g.,
   *
   * <pre>{@code
   *    x := if a then 0 else 1
   * }</pre>
   *
   * converted to
   *
   * <pre>{@code
   *    if a then
   *     #exprResult123 := 0
   *   else
   *     #exprResult123 := 1
   *   x := #exprResult123
   * }</pre>
   */
  public static final String EXPRESSION_RESULT_PREFIX = INTERNAL_NAME_PREFIX + "exprResult";


  /**
   * Field introduced in, e.g.,
   *
   * <pre>{@code
   *    x := a < b < c
   * }</pre>
   *
   * converted to
   *
   * <pre>{@code
   *    #chainedBoolTemp123 = b
   *   x := a < #chainedBoolTemp123 && #chainedBoolTemp123 < c
   * }</pre>
   */
  public static final String CHAINED_BOOL_TMP_PREFIX = INTERNAL_NAME_PREFIX + "chainedBoolTemp";


  /**
   * Field introduced in, e.g.,
   *
   * <pre>{@code
   *    x := a,b -> a*b
   * }</pre>
   */
  public static final String LAMBDA_PREFIX = INTERNAL_NAME_PREFIX + "fun";


  /**
   * Field introduced in, e.g.,
   *
   * <pre>{@code
   *    x := [a, b, c]
   * }</pre>
   */
  public static final String INLINE_SYS_ARRAY_PREFIX = INTERNAL_NAME_PREFIX + "inlineSysArray";

  /**
   * Field introduced in, e.g.,
   *
   * <pre>{@code
   *    for x in set do
   *     say x
   * }</pre>
   */
  public static final String REC_LOOP_PREFIX = INTERNAL_NAME_PREFIX + "loop";


  /**
   * Fields introduced by Loop.java
   */
  public static final String ITER_ARG_PREFIX_INIT = INTERNAL_NAME_PREFIX + "iter_arg_init_";
  public static final String ITER_ARG_PREFIX_NEXT = INTERNAL_NAME_PREFIX + "iter_arg_next_";


  /**
   * Field introduced in, e.g.,
   *
   * <pre>{@code
   *    _ = f a
   * }</pre>
   */
  public static final String UNDERSCORE_PREFIX = INTERNAL_NAME_PREFIX + "_";


  /**
   * Field introduced in, e.g.,
   *
   * <pre>{@code
   *    (a,b) = f c
   * }</pre>
   */
  public static final String DESTRUCTURE_PREFIX = INTERNAL_NAME_PREFIX + "destructure";


  /**
   * Argument field in a partial function such as
   *
   * <pre>{@code
   *    m := s.map (f 42)
   * }</pre>
   *
   * that will be converted into
   *
   * <pre>{@code
   *    m := s.map (#partialFunctionArg123 -> f 42 #partialFunctionArg123)
   * }</pre>
   */
  public static final String PARTIAL_FUNCTION_ARGUMENT_PREFIX = INTERNAL_NAME_PREFIX + "partialFunctionArg";


  /**
   * Prefixes of pre, pre bool, pre and call and post features.
   */
  public static final String PRECONDITION_FEATURE_PREFIX        = INTERNAL_NAME_PREFIX + "pre";
  public static final String PREBOOLCONDITION_FEATURE_PREFIX    = INTERNAL_NAME_PREFIX + "prebool";
  public static final String PREANDCALLCONDITION_FEATURE_PREFIX = INTERNAL_NAME_PREFIX + "preandcall";
  public static final String POSTCONDITION_FEATURE_PREFIX       = INTERNAL_NAME_PREFIX + "post";

  /**
   * Internal name used for an outer type.
   */
  public static final String OUTER_TYPE_NAME = INTERNAL_NAME_PREFIX + "outer";


  /**
   * The qualified names of features fuzion.runtime.precondition_fault and
   * fuzion.runtime.postcondition_fault.
   */
  public static String[] FUZION_RUNTIME_PRECONDITION_FAULT  = "fuzion.runtime.precondition_fault" .split("\\.");
  public static String[] FUZION_RUNTIME_POSTCONDITION_FAULT = "fuzion.runtime.postcondition_fault".split("\\.");


  /**
   * Suffix added to module files.
   */
  public static final String MODULE_FILE_SUFFIX = ".fum";


  /*-----------------  special values used in MIR file  -----------------*/


  public static final int MIR_FILE_MAGIC0 = 0xF710BEAD;  // FuZIOn BEAD, a module .fum
  public static final byte[] MIR_FILE_MAGIC = int2Bytes(MIR_FILE_MAGIC0);
  public static final String MIR_FILE_MAGIC_EXPLANATION = "Module file magic: 'FuZIOn BEAD'";


  public static final int MIR_FILE_FIRST_FEATURE_OFFSET = 4;

  /**
   * feature kind value for constructor routines
   */
  public static final int MIR_FILE_KIND_CONSTRUCTOR_VALUE = 8;
  public static final int MIR_FILE_KIND_CONSTRUCTOR_REF   = 9;

  /**
   * The bits of feature kind that encode the kind.
   */
  public static final int MIR_FILE_KIND_MASK    = 0xf;


  /**
   * The bits of feature kind that encode the visibility.
   */
  public static final int MIR_FILE_VISIBILITY_MASK    = 0x7 << 7;


  /**
   * Flag OR'ed to kind, true if feature for type 'f.type' was added.
   */
  public static final int MIR_FILE_KIND_HAS_COTYPE = 0x10;


  /**
   * Flag OR'ed to kind, true if feature is cotype.
   */
  public static final int MIR_FILE_KIND_IS_COTYPE = 0x20;


  /**
   * Flag OR'ed to kind for features with modifier 'fixed'
   */
  public static final int MIR_FILE_KIND_IS_FIXED = 0x40;


  /**
   * Flag OR'ed to kind for features with precondition feature
   */
  public static final int MIR_FILE_KIND_HAS_PRE_CONDITION_FEATURE = 0x400;


  /**
   * Flag OR'ed to kind for features with postcondition feature
   */
  public static final int MIR_FILE_KIND_HAS_POST_CONDITION_FEATURE = 0x800;


  /**
   * Fuzion module directory as used in module files instead of absolute or
   * relative path of module directory.
   */
  public static final Path SYMBOLIC_FUZION_MODULE = Path.of("$MODULE");


  /**
   * Expression kind ids for use in FUM file are the ordinal numbers of these
   * constants.
   */
  public enum MirExprKind
  {
    Assign,
    Box,
    Call,
    Current,
    Comment,
    Const,
    Match,
    Tag,
    Pop,
    Unit,
    InlineArray;

    /**
     * get the Kind that corresponds to the given ordinal number.
     */
    public static MirExprKind from(int ordinal)
    {
      if (CHECKS) check
        (values()[ordinal].ordinal() == ordinal);

      return values()[ordinal];
    }

  }


  /*-----------------  special values used in AIR file  -----------------*/


  public static final int AIR_FILE_MAGIC0 = 0xF710C0DE;  // FuZIOn CODE, application code .fapp
  public static final byte[] AIR_FILE_MAGIC = int2Bytes(AIR_FILE_MAGIC0);


  /*-----------------  special values used in FUIR file  -----------------*/


  public static final int FUIR_FILE_MAGIC0 = 0xF710DEED;  // FuZIOn DEED, fuzion IR, .fuir
  public static final byte[] FUIR_FILE_MAGIC = int2Bytes(FUIR_FILE_MAGIC0);


  /*-----------------  special values for modifiers  -----------------*/


  /**
   *
   */
  public static final String[] MODIFIER_STRINGS = {"redef", "fixed"};


  /**
   *
   */
  public static final int MODIFIER_REDEFINE     = 0x01;
  static { if (CHECKS) check(modifierToString(MODIFIER_REDEFINE).trim().equals("redef")); }

  /**
   * 'fixed' modifier to force feature to be fixed, i.e., not inherited by
   * heirs.
   */
  public static final int MODIFIER_FIXED        = 0x02;
  static { if (CHECKS) check(modifierToString(MODIFIER_FIXED).trim().equals("fixed")); }



  /*-------------------------  static methods  --------------------------*/



  /**
   * modifierToString
   *
   * @return space separated modifiers string.
   */
  public static String modifierToString(int m)
  {
    String result = "";
    for(int i=0; i<32; i++)
      {
        if ((m & (1<<i))!=0)
          {
            result = result + MODIFIER_STRINGS[i] + " ";
          }
      }
    return result;
  }


  /**
   * Convert 32-bit integer to 4 bytes in big endian order.
   */
  private static byte[] int2Bytes(int i)
  {
    return new byte[]
      { (byte) (i >> 24),
        (byte) (i >> 16),
        (byte) (i >>  8),
        (byte) (i      )
      };
  }

}

/* end of file */
