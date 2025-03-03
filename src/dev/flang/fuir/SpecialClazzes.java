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
 * Source of enum SpezialClazzes
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir;

import dev.flang.util.FuzionConstants;

/**
 * Enum of clazzes that require special handling in the backend
 */
public enum SpecialClazzes
{
  // dummy entry to report failure of getSpecialId()
  c_NOT_FOUND   (""                           , 0, null        ),

  c_universe    (FuzionConstants.UNIVERSE_NAME, 0, c_NOT_FOUND ),
  c_Any         (FuzionConstants.ANY_NAME     , 0, c_universe  ),
  c_i8          (FuzionConstants.I8_NAME      , 1, c_universe  ),
  c_i16         (FuzionConstants.I16_NAME     , 1, c_universe  ),
  c_i32         (FuzionConstants.I32_NAME     , 1, c_universe  ),
  c_i64         (FuzionConstants.I64_NAME     , 1, c_universe  ),
  c_u8          (FuzionConstants.U8_NAME      , 1, c_universe  ),
  c_u16         (FuzionConstants.U16_NAME     , 1, c_universe  ),
  c_u32         (FuzionConstants.U32_NAME     , 1, c_universe  ),
  c_u64         (FuzionConstants.U64_NAME     , 1, c_universe  ),
  c_f32         (FuzionConstants.F32_NAME     , 1, c_universe  ),
  c_f64         (FuzionConstants.F64_NAME     , 1, c_universe  ),
  c_unit        (FuzionConstants.UNIT_NAME    , 0, c_universe  ),
  c_void        ("void"                       , 0, c_universe  ),
  c_bool        ("bool"                       , 0, c_universe  ),
  c_true_       ("true_"                      , 0, c_universe  ),
  c_false_      ("false_"                     , 0, c_universe  ),
  c_const_string("const_string"               , 0, c_universe  ),
  c_CS_utf8_data("utf8_data"                  , 0, c_const_string),
  c_String      (FuzionConstants.STRING_NAME  , 0, c_universe  ),
  c_error       ("error"                      , 1, c_universe  ),
  c_fuzion      ("fuzion"                     , 0, c_universe  ),
  c_java        ("java"                       , 0, c_fuzion    ),
  c_fuzion_sys  ("sys"                        , 0, c_fuzion    ),
  c_sys_ptr     ("Pointer"                    , 0, c_fuzion_sys),
  c_Mutex       ("Mutex"                      , 0, c_universe  ),
  c_Condition   ("Condition"                  , 0, c_universe  ),
  c_File_Descriptor("File_Descriptor"         , 0, c_universe  ),
  c_Directory_Descriptor("Directory_Descriptor", 0, c_universe  ),
  c_Java_Ref    ("Java_Ref"                   , 0, c_universe  ),
  ;

  final String _name;
  final int _argCount;
  final SpecialClazzes _outer;

  SpecialClazzes(String name, int argc, SpecialClazzes outer)
  {
    _name = name;
    _argCount = argc;
    _outer = outer;
  }
}
