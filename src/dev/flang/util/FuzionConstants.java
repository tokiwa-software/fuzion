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
   * Artificial name of universe feature.
   */
  public static final String UNIVERSE_NAME    = "#universe";

  /**
   * Prefix of artifically generated name of outer refs.
   */
  public static final String OUTER_REF_PREFIX = "#^";

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
  public static final String INTERNAL_RESULT_NAME = "#result";


  /**
   * Artificial field added to instances of choice.fz if needed to
   * disambiguate different (value) types.
   */
  public static final String CHOICE_TAG_NAME = "#tag";


  /**
   * Prefix for names of anonymous inner features.
   */
  public static final String ANONYMOUS_FEATURE_PREFIX = "#anonymous";


  /*-----------------  special values used in MIR file  -----------------*/


  public static int MIR_FILE_MAGIC0 = 0xF710BEAD;  // FuZIOn BEAD, a module .fum
  public static byte[] MIR_FILE_MAGIC = int2Bytes(MIR_FILE_MAGIC0);


  public static int MIR_FILE_FIRST_FEATURE_OFFSET = 4;

  /**
   * feature kind value for constructor routines
   */
  public static int MIR_FILE_KIND_CONSTRUCTOR_VALUE = 5;
  public static int MIR_FILE_KIND_CONSTRUCTOR_REF   = 6;


  /*-----------------  special values used in AIR file  -----------------*/


  public static int AIR_FILE_MAGIC0 = 0xF710C0DE;  // FuZIOn CODE, application code .fapp
  public static byte[] AIR_FILE_MAGIC = int2Bytes(AIR_FILE_MAGIC0);


  /*-----------------  special values used in FUIR file  -----------------*/


  public static int FUIR_FILE_MAGIC0 = 0xF710DEED;  // FuZIOn DEED, fuzion IR, .fuir
  public static byte[] FUIR_FILE_MAGIC = int2Bytes(FUIR_FILE_MAGIC0);


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
