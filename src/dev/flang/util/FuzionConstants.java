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


  /*----------------------  special feature names  ----------------------*/


  /**
   * Prefix used for all internal names
   */
  public static final String INTERNAL_NAME_PREFIX = "#";

  /**
   * Artificial name of universe feature.
   */
  public static final String UNIVERSE_NAME    = INTERNAL_NAME_PREFIX + "universe";

  /**
   * Prefix of artifically generated name of outer refs.
   */
  public static final String OUTER_REF_PREFIX = INTERNAL_NAME_PREFIX + "^";

  /**
   * Name of Object feature, i.e., the implicit parent feature of all other
   * features.
   */
  public static final String OBJECT_NAME          = "Object";


  /**
   * Name of implicitly declared result field in case this field is used as the
   * target of an assignment.
   */
  public static final String RESULT_NAME          = "result";


  /**
   * Artificial name of implicitly declared result field in case the assignment
   * to result is implicitly from the last statement's value.
   */
  public static final String INTERNAL_RESULT_NAME = INTERNAL_NAME_PREFIX + "result";


  /**
   * Artificial field added to instances of choice.fz if needed to
   * disambiguate different (value) types.
   */
  public static final String CHOICE_TAG_NAME = INTERNAL_NAME_PREFIX + "tag";


  /**
   * Prefix for names of anonymous inner features.
   */
  public static final String ANONYMOUS_FEATURE_PREFIX = INTERNAL_NAME_PREFIX + "anonymous";


  /**
   * Name of type features.
   */
  public static final String TYPE_NAME = INTERNAL_NAME_PREFIX + "type";


  /**
   * Field introduced in, e.g.,
   *
   *   x := if a then 0 else 1
   *
   * converted to
   *
   *   if a then
   *     #stmtResult123 := 0
   *   else
   *     #stmtResult123 := 1
   *   x := #stmtResult123
   */
  public static final String STATEMENT_RESULT_PREFIX = INTERNAL_NAME_PREFIX + "stmtResult";


  /**
   * Field introduced in, e.g.,
   *
   *   x := a < b < c
   *
   * converted to
   *
   *   #chainedBoolTemp123 = b
   *   x := a < #chainedBoolTemp123 && #chainedBoolTemp123 < c
   */
  public static final String CHAINED_BOOL_TMP_PREFIX = INTERNAL_NAME_PREFIX + "chainedBoolTemp";


  /**
   * Field introduced in, e.g.,
   *
   *   x := a,b -> a*b
   */
  public static final String LAMBDA_PREFIX = INTERNAL_NAME_PREFIX + "fun";


  /**
   * Field introduced in, e.g.,
   *
   *   x := [a, b, c]
   */
  public static final String INLINE_SYS_ARRAY_PREFIX = INTERNAL_NAME_PREFIX + "inlineSysArray";

  /**
   * Field introduced in, e.g.,
   *
   *   for x in set do
   *     say x
   */
  public static final String REC_LOOP_PREFIX = INTERNAL_NAME_PREFIX + "loop";


  /**
   * Field introduced in, e.g.,
   *
   *   _ = f a
   */
  public static final String UNDERSCORE_PREFIX = INTERNAL_NAME_PREFIX + "_";


  /**
   * Field introduced in, e.g.,
   *
   *   (a,b) = f c
   */
  public static final String DESTRUCTURE_PREFIX = INTERNAL_NAME_PREFIX + "destructure";


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
  public static final int MIR_FILE_KIND_CONSTRUCTOR_VALUE = 7;
  public static final int MIR_FILE_KIND_CONSTRUCTOR_REF   = 8;

  /**
   * The bits of feature kind that are not flags
   */
  public static final int MIR_FILE_KIND_MASK    = 0xf;


  /**
   * Flag OR'ed to kind, true if feature for type 'f.type' was added.
   */
  public static final int MIR_FILE_KIND_HAS_TYPE_FEATURE = 0x10;


  /**
   * Flag OR'ed to kind for intrinsics that create an instance of their result ref type.
   */
  public static final int MIR_FILE_KIND_IS_INTRINSIC_CONSTRUCTOR = 0x20;


  /**
   * Fuzion home directory as used in module files instead of absolute or
   * relative path of build directory.
   */
  public static final Path SYMBOLIC_FUZION_HOME = Path.of("$FUZION");


  /**
   * Directory to be used for sources in module files
   */
  public static final Path SYMBOLIC_FUZION_HOME_LIB_SOURCE = SYMBOLIC_FUZION_HOME.resolve("lib");


  /*-----------------  special values used in AIR file  -----------------*/


  public static final int AIR_FILE_MAGIC0 = 0xF710C0DE;  // FuZIOn CODE, application code .fapp
  public static final byte[] AIR_FILE_MAGIC = int2Bytes(AIR_FILE_MAGIC0);


  /*-----------------  special values used in FUIR file  -----------------*/


  public static final int FUIR_FILE_MAGIC0 = 0xF710DEED;  // FuZIOn DEED, fuzion IR, .fuir
  public static final byte[] FUIR_FILE_MAGIC = int2Bytes(FUIR_FILE_MAGIC0);


  /*-------------------------  static methods  --------------------------*/


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
