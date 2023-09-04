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
 * Source of class Intrinsics
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm.runtime;

import dev.flang.util.ANY;
import dev.flang.util.FuzionConstants;


/**
 * Intrinsics provides implementations of Fuzion's intrinsic features for use by
 * code generated for the JVM backend.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Intrinsics extends ANY
{


  /*-------------------------  static methods  --------------------------*/


  public static boolean safety      () { return Runtime._options != null ? Runtime._options.fuzionSafety() : Boolean.valueOf(System.getProperty(FuzionConstants.FUZION_SAFETY_PROPERTY, "true")); }
  public static boolean debug       () { return Runtime._options != null ? Runtime._options.fuzionDebug() : ((Integer.getInteger(FuzionConstants.FUZION_DEBUG_LEVEL_PROPERTY, 1)) > 0); }
  public static int     debug_level () { return Runtime._options != null ? Runtime._options.fuzionDebugLevel() : Integer.getInteger(FuzionConstants.FUZION_DEBUG_LEVEL_PROPERTY, 1); }

  public static long    fuzion_sys_stdin_stdin0 () { return Runtime._stdin;  }
  public static long    fuzion_sys_out_stdout   () { return Runtime._stdout; }
  public static long    fuzion_sys_err_stderr   () { return Runtime._stderr; }

  public static long    fuzion_sys_misc_unique_id() { return Runtime.unique_id(); }

  public static int     i8_as_i32                   (byte   a          ) { return                 (      a); }
  public static byte    i8_cast_to_u8               (byte   a          ) { return                 (      a); }
  public static byte    i8_prefix_minusDEGREE       (byte   a          ) { return (byte)          (  -   a); }
  public static byte    i8_infix_plusDEGREE         (byte   a, byte   b) { return (byte)          (a +   b); }
  public static byte    i8_infix_minusDEGREE        (byte   a, byte   b) { return (byte)          (a -   b); }
  public static byte    i8_infix_timesDEGREE        (byte   a, byte   b) { return (byte)          (a *   b); }
  public static byte    i8_div                      (byte   a, byte   b) { return (byte)          (a /   b); }
  public static byte    i8_mod                      (byte   a, byte   b) { return (byte)          (a %   b); }
  public static byte    i8_infix_AND                (byte   a, byte   b) { return (byte)          (a &   b); }
  public static byte    i8_infix_OR                 (byte   a, byte   b) { return (byte)          (a |   b); }
  public static byte    i8_infix_caret              (byte   a, byte   b) { return (byte)          (a ^   b); }
  public static byte    i8_infix_gtgt               (byte   a, byte   b) { return (byte)          (a >>  b); }
  public static byte    i8_infix_ltlt               (byte   a, byte   b) { return (byte)          (a <<  b); }
  public static boolean i8_type_equality             (byte   a, byte   b) { return                 ( a == b); }
  public static boolean i8_type_lteq                 (byte   a, byte   b) { return                 ( a <= b); }
  public static int     i16_as_i32                  (short  a          ) { return                 (      a); }
  public static char    i16_cast_to_u16             (short  a          ) { return (char)          (      a); }
  public static short   i16_prefix_minusDEGREE      (short  a          ) { return (short)         (  -   a); }
  public static short   i16_infix_plusDEGREE        (short  a, short  b) { return (short)         (a +   b); }
  public static short   i16_infix_minusDEGREE       (short  a, short  b) { return (short)         (a -   b); }
  public static short   i16_infix_timesDEGREE       (short  a, short  b) { return (short)         (a *   b); }
  public static short   i16_div                     (short  a, short  b) { return (short)         (a /   b); }
  public static short   i16_mod                     (short  a, short  b) { return (short)         (a %   b); }
  public static short   i16_infix_AND               (short  a, short  b) { return (short)         (a &   b); }
  public static short   i16_infix_OR                (short  a, short  b) { return (short)         (a |   b); }
  public static short   i16_infix_caret             (short  a, short  b) { return (short)         (a ^   b); }
  public static short   i16_infix_gtgt              (short  a, short  b) { return (short)         (a >>  b); }
  public static short   i16_infix_ltlt              (short  a, short  b) { return (short)         (a <<  b); }
  public static boolean i16_type_equality           (short  a, short  b) { return                 (a ==  b); }
  public static boolean i16_type_lteq               (short  a, short  b) { return                 (a <=  b); }
  public static long    i32_as_i64                  (int    a          ) { return (long)          (      a); }
  public static int     i32_cast_to_u32             (int    a          ) { return                 (      a); }
  public static double  i32_as_f64                  (int    a          ) { return (double)        (      a); }
  public static int     i32_prefix_minusDEGREE      (int    a          ) { return                 (  -   a); }
  public static int     i32_infix_plusDEGREE        (int    a, int    b) { return                 (a +   b); }
  public static int     i32_infix_minusDEGREE       (int    a, int    b) { return                 (a -   b); }
  public static int     i32_infix_timesDEGREE       (int    a, int    b) { return                 (a *   b); }
  public static int     i32_div                     (int    a, int    b) { return                 (a /   b); }
  public static int     i32_mod                     (int    a, int    b) { return                 (a %   b); }
  public static int     i32_infix_AND               (int    a, int    b) { return                 (a &   b); }
  public static int     i32_infix_OR                (int    a, int    b) { return                 (a |   b); }
  public static int     i32_infix_caret             (int    a, int    b) { return                 (a ^   b); }
  public static int     i32_infix_gtgt              (int    a, int    b) { return                 (a >>  b); }
  public static int     i32_infix_ltlt              (int    a, int    b) { return                 (a <<  b); }
  public static boolean i32_type_equality           (int    a, int    b) { return                 (a ==  b); }
  public static boolean i32_type_lteq               (int    a, int    b) { return                 (a <=  b); }
  public static long    i64_cast_to_u64             (long   a          ) { return                 (      a); }
  public static double  i64_as_f64                  (long   a          ) { return (double)        (      a); }
  public static long    i64_prefix_minusDEGREE      (long   a          ) { return                 (  -   a); }
  public static long    i64_infix_plusDEGREE        (long   a, long   b) { return                 (a +   b); }
  public static long    i64_infix_minusDEGREE       (long   a, long   b) { return                 (a -   b); }
  public static long    i64_infix_timesDEGREE       (long   a, long   b) { return                 (a *   b); }
  public static long    i64_div                     (long   a, long   b) { return                 (a /   b); }
  public static long    i64_mod                     (long   a, long   b) { return                 (a %   b); }
  public static long    i64_infix_AND               (long   a, long   b) { return                 (a &   b); }
  public static long    i64_infix_OR                (long   a, long   b) { return                 (a |   b); }
  public static long    i64_infix_caret             (long   a, long   b) { return                 (a ^   b); }
  public static long    i64_infix_gtgt              (long   a, long   b) { return                 (a >>  b); }
  public static long    i64_infix_ltlt              (long   a, long   b) { return                 (a <<  b); }
  public static boolean i64_type_equality           (long   a, long   b) { return                 (a ==  b); }
  public static boolean i64_type_lteq               (long   a, long   b) { return                 (a <=  b); }
  public static int     u8_as_i32                   (byte a            ) { return                  0xff & (      a) ; }
  public static byte    u8_cast_to_i8               (byte   a          ) { return (byte) ((int) (byte)    (      a)); }
  public static byte    u8_prefix_minusDEGREE       (byte   a          ) { return (byte)          (  -   (0xff & a)); }
  public static byte    u8_infix_plusDEGREE         (byte   a, byte   b) { return (byte)          ((0xff & a) +   (0xff & b)); }
  public static byte    u8_infix_minusDEGREE        (byte   a, byte   b) { return (byte)          ((0xff & a) -   (0xff & b)); }
  public static byte    u8_infix_timesDEGREE        (byte   a, byte   b) { return (byte)          ((0xff & a) *   (0xff & b)); }
  public static byte    u8_div                      (byte   a, byte   b) { return (byte) Integer.divideUnsigned   ((0xff & a), (0xff & b)); }
  public static byte    u8_mod                      (byte   a, byte   b) { return (byte) Integer.remainderUnsigned((0xff & a), (0xff & b)); }
  public static byte    u8_infix_AND                (byte   a, byte   b) { return (byte)          ((0xff & a) &   (0xff & b)); }
  public static byte    u8_infix_OR                 (byte   a, byte   b) { return (byte)          ((0xff & a) |   (0xff & b)); }
  public static byte    u8_infix_caret              (byte   a, byte   b) { return (byte)          ((0xff & a) ^   (0xff & b)); }
  public static byte    u8_infix_gtgt               (byte   a, byte   b) { return (byte)          ((0xff & a) >>> (0xff & b)); }
  public static byte    u8_infix_ltlt               (byte   a, byte   b) { return (byte)          ((0xff & a) <<  (0xff & b)); }
  public static boolean u8_type_equality             (byte   a, byte   b) { return                 (a ==  b) ; }
  public static boolean u8_type_lteq                 (byte   a, byte   b) { return Integer.compareUnsigned (a,    b) <= 0; }
  public static int     u16_as_i32                  (char   a          ) { return                 (      a); }
  public static byte    u16_low8bits                (char   a          ) { return (byte)          (      a); }
  public static short   u16_cast_to_i16             (char   a          ) { return (short)         (      a); }
  public static char    u16_prefix_minusDEGREE      (char   a, char   b) { return (char)          (  -   a); }
  public static char    u16_infix_plusDEGREE        (char   a, char   b) { return (char)          (a +   b); }
  public static char    u16_infix_minusDEGREE       (char   a, char   b) { return (char)          (a -   b); }
  public static char    u16_infix_timesDEGREE       (char   a, char   b) { return (char)          (a *   b); }
  public static char    u16_div                     (char   a, char   b) { return (char) Integer.divideUnsigned   (a, b); }
  public static char    u16_mod                     (char   a, char   b) { return (char) Integer.remainderUnsigned(a, b); }
  public static char    u16_infix_AND               (char   a, char   b) { return (char)          (a &   b); }
  public static char    u16_infix_OR                (char   a, char   b) { return (char)          (a |   b); }
  public static char    u16_infix_caret             (char   a, char   b) { return (char)          (a ^   b); }
  public static char    u16_infix_gtgt              (char   a, char   b) { return (char)          (a >>> b); }
  public static char    u16_infix_ltlt              (char   a, char   b) { return (char)          (a <<  b); }
  public static boolean u16_type_equality           (char   a, char   b) { return                 (a ==  b); }
  public static boolean u16_type_lteq               (char   a, char   b) { return Integer.compareUnsigned(a, b) <= 0; }
  public static long    u32_as_i64                  (int    a          ) { return Integer.toUnsignedLong(a); }
  public static byte    u32_low8bits                (int    a          ) { return (byte)          (      a); }
  public static char    u32_low16bits               (int    a          ) { return (char)          (      a); }
  public static int     u32_cast_to_i32             (int    a          ) { return                 (      a); }
  public static double  u32_as_f64                  (int    a          ) { return (double) Integer.toUnsignedLong(a); }
  public static float   u32_cast_to_f32             (int    a          ) { return Float.intBitsToFloat(       a); }
  public static int     u32_prefix_minusDEGREE      (int    a          ) { return                 (  -   a); }
  public static int     u32_infix_plusDEGREE        (int    a, int    b) { return                 (a +   b); }
  public static int     u32_infix_minusDEGREE       (int    a, int    b) { return                 (a -   b); }
  public static int     u32_infix_timesDEGREE       (int    a, int    b) { return                 (a *   b); }
  public static int     u32_div                     (int    a, int    b) { return Integer.divideUnsigned   (a, b); }
  public static int     u32_mod                     (int    a, int    b) { return Integer.remainderUnsigned(a, b); }
  public static int     u32_infix_AND               (int    a, int    b) { return                 (a &   b); }
  public static int     u32_infix_OR                (int    a, int    b) { return                 (a |   b); }
  public static int     u32_infix_caret             (int    a, int    b) { return                 (a ^   b); }
  public static int     u32_infix_gtgt              (int    a, int    b) { return                 (a >>> b); }
  public static int     u32_infix_ltlt              (int    a, int    b) { return                 (a <<  b); }
  public static boolean u32_type_equality           (int    a, int    b) { return                 (a ==  b); }
  public static boolean u32_type_lteq               (int    a, int    b) { return Integer.compareUnsigned(a, b) <= 0; }
  public static byte    u64_low8bits                (long   a          ) { return (byte)          (      a) ; }
  public static char    u64_low16bits               (long   a          ) { return (char)          (      a) ; }
  public static int     u64_low32bits               (long   a          ) { return (int)           (      a) ; }
  public static long    u64_cast_to_i64             (long   a          ) { return                 (      a) ; }
  public static double  u64_as_f64                  (long   a          ) { return Double.parseDouble(Long.toUnsignedString(a)); }  // NYI: why so complex?
  public static double  u64_cast_to_f64             (long   a          ) { return Double.longBitsToDouble(    a); }
  public static long    u64_prefix_minusDEGREE      (long   a          ) { return                 (  -   a); }
  public static long    u64_infix_plusDEGREE        (long   a, long   b) { return                 (a +   b); }
  public static long    u64_infix_minusDEGREE       (long   a, long   b) { return                 (a -   b); }
  public static long    u64_infix_timesDEGREE       (long   a, long   b) { return                 (a *   b); }
  public static long    u64_div                     (long   a, long   b) { return Long.divideUnsigned   (a, b); }
  public static long    u64_mod                     (long   a, long   b) { return Long.remainderUnsigned(a, b); }
  public static long    u64_infix_AND               (long   a, long   b) { return                 (a &   b); }
  public static long    u64_infix_OR                (long   a, long   b) { return                 (a |   b); }
  public static long    u64_infix_caret             (long   a, long   b) { return                 (a ^   b); }
  public static long    u64_infix_gtgt              (long   a, long   b) { return                 (a >>> b); }
  public static long    u64_infix_ltlt              (long   a, long   b) { return                 (a <<  b); }
  public static boolean u64_type_equality           (long   a, long   b) { return                 (a ==  b); }
  public static boolean u64_type_lteq               (long   a, long   b) { return Long.compareUnsigned(a,b) <= 0; }
  public static float   f32_prefix_minus            (float  a          ) { return                 (  -   a); }
  public static float   f32_infix_plus              (float  a, float  b) { return                 (a +   b); }
  public static float   f32_infix_minus             (float  a, float  b) { return                 (a -   b); }
  public static float   f32_infix_times             (float  a, float  b) { return                 (a *   b); }
  public static float   f32_infix_divide            (float  a, float  b) { return                 (a /   b); }
  public static float   f32_infix_PERCENT           (float  a, float  b) { return                 (a %   b); }
  public static float   f32_infix_timestimes        (float  a, float  b) { return (float) Math.pow(a,    b); }
  public static boolean f32_infix_eq                (float  a, float  b) { return                 (a ==  b); }
  public static boolean f32_infix_lteq              (float  a, float  b) { return                 (a <=  b); }
  public static boolean f32_infix_gteq              (float  a, float  b) { return                 (a >=  b); }
  public static boolean f32_infix_lt                (float  a, float  b) { return                 (a <   b); }
  public static boolean f32_infix_gt                (float  a, float  b) { return                 (a >   b); }
  public static double  f32_as_f64                  (float  a          ) { return (double)        (      a); }
  public static int     f32_cast_to_u32             (float  a          ) { return Float.floatToIntBits(  a); }
  public static double  f64_prefix_minus            (double a          ) { return                 (  -   a); }
  public static double  f64_infix_plus              (double a, double b) { return                 (a +   b); }
  public static double  f64_infix_minus             (double a, double b) { return                 (a -   b); }
  public static double  f64_infix_times             (double a, double b) { return                 (a *   b); }
  public static double  f64_infix_divide            (double a, double b) { return                 (a /   b); }
  public static double  f64_infix_PERCENT           (double a, double b) { return                 (a %   b); }
  public static double  f64_infix_timestimes        (double a, double b) { return         Math.pow(a,    b); }
  public static boolean f64_infix_eq                (double a, double b) { return                 (a ==  b); }
  public static boolean f64_infix_lteq              (double a, double b) { return                 (a <=  b); }
  public static boolean f64_infix_gteq              (double a, double b) { return                 (a >=  b); }
  public static boolean f64_infix_lt                (double a, double b) { return                 (a <   b); }
  public static boolean f64_infix_gt                (double a, double b) { return                 (a >   b); }
  public static long    f64_as_i64_lax              (double a          ) { return (long)          (      a); }
  public static float   f64_as_f32                  (double a          ) { return (float)         (      a); }
  public static long    f64_cast_to_u64             (double a          ) { return Double.doubleToLongBits(a); }
  public static boolean f32_type_is_NaN             (float  a          ) { return Float.isNaN       (         a); }
  public static boolean f64_type_is_NaN             (double a          ) { return Double.isNaN      (         a); }
  public static float   f32_type_acos               (float  a          ) { return (float) Math.acos (         a); }
  public static float   f32_type_asin               (float  a          ) { return (float) Math.asin (         a); }
  public static float   f32_type_atan               (float  a          ) { return (float) Math.atan (         a); }
  public static float   f32_type_cos                (float  a          ) { return (float) Math.cos  (         a); }
  public static float   f32_type_cosh               (float  a          ) { return (float) Math.cosh (         a); }
  public static float   f32_type_epsilon            (                  ) { return (float) Math.ulp  ((float)  1); }
  public static float   f32_type_exp                (float  a          ) { return (float) Math.exp  (         a); }
  public static float   f32_type_log                (float  a          ) { return (float) Math.log  (         a); }
  public static float   f32_type_max                (                  ) { return Float.MAX_VALUE;                }
  public static int     f32_type_max_exp            (                  ) { return Float.MAX_EXPONENT;             }
  public static float   f32_type_min_positive       (                  ) { return Float.MIN_NORMAL;               }
  public static int     f32_type_min_exp            (                  ) { return Float.MIN_EXPONENT;             }
  public static float   f32_type_sin                (float  a          ) { return (float) Math.sin  (         a); }
  public static float   f32_type_sinh               (float  a          ) { return (float) Math.sinh (         a); }
  public static float   f32_type_square_root        (float  a          ) { return (float) Math.sqrt ((double) a); }
  public static float   f32_type_tan                (float  a          ) { return (float) Math.tan  (         a); }
  public static float   f32_type_tanh               (float  a          ) { return (float) Math.tanh (         a); }
  public static double  f64_type_acos               (double a          ) { return         Math.acos (         a); }
  public static double  f64_type_asin               (double a          ) { return         Math.asin (         a); }
  public static double  f64_type_atan               (double a          ) { return         Math.atan (         a); }
  public static double  f64_type_cos                (double a          ) { return         Math.cos  (         a); }
  public static double  f64_type_cosh               (double a          ) { return         Math.cosh (         a); }
  public static double  f64_type_epsilon            (                  ) { return         Math.ulp  ((double) 1); }
  public static double  f64_type_exp                (double a          ) { return         Math.exp  (         a); }
  public static double  f64_type_log                (double a          ) { return         Math.log  (         a); }
  public static double  f64_type_max                (                  ) { return Double.MAX_VALUE;               }
  public static int     f64_type_max_exp            (                  ) { return Double.MAX_EXPONENT;            }
  public static double  f64_type_min_positive       (                  ) { return Double.MIN_NORMAL;              }
  public static int     f64_type_min_exp            (                  ) { return Double.MIN_EXPONENT;            }
  public static double  f64_type_sin                (double a          ) { return         Math.sin  (         a); }
  public static double  f64_type_sinh               (double a          ) { return         Math.sinh (         a); }
  public static double  f64_type_square_root        (double a          ) { return         Math.sqrt (         a); }
  public static double  f64_type_tan                (double a          ) { return         Math.tan  (         a); }
  public static double  f64_type_tanh               (double a          ) { return         Math.tanh (         a); }

  public static void fuzion_std_exit (int code) { System.exit(code); }

}

/* end of file */
