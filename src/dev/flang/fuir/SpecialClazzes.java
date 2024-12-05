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
  c_Any         ("Any"                        , 0, c_universe  ),
  c_i8          ("i8"                         , 1, c_universe  ),
  c_i16         ("i16"                        , 1, c_universe  ),
  c_i32         ("i32"                        , 1, c_universe  ),
  c_i64         ("i64"                        , 1, c_universe  ),
  c_u8          ("u8"                         , 1, c_universe  ),
  c_u16         ("u16"                        , 1, c_universe  ),
  c_u32         ("u32"                        , 1, c_universe  ),
  c_u64         ("u64"                        , 1, c_universe  ),
  c_f32         ("f32"                        , 1, c_universe  ),
  c_f64         ("f64"                        , 1, c_universe  ),
  c_unit        ("unit"                       , 0, c_universe  ),
  c_void        ("void"                       , 0, c_universe  ),
  c_bool        ("bool"                       , 0, c_universe  ),
  c_true_       ("true_"                      , 0, c_universe  ),
  c_false_      ("false_"                     , 0, c_universe  ),
  c_Const_String("Const_String"               , 0, c_universe  ),
  c_CS_utf8_data("utf8_data"                  , 0, c_Const_String),
  c_String      ("String"                     , 0, c_universe  ),
  c_error       ("error"                      , 1, c_universe  ),
  c_fuzion      ("fuzion"                     , 0, c_universe  ),
  c_java        ("java"                       , 0, c_fuzion    ),
  c_fuzion_sys  ("sys"                        , 0, c_fuzion    ),
  c_sys_ptr     ("Pointer"                    , 0, c_fuzion_sys),
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
