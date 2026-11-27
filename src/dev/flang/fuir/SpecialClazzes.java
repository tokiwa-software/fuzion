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
 * Source of enum SpecialClazzes
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir;

import java.util.function.Supplier;

import dev.flang.ast.Types;

/**
 * Enum of clazzes that require special handling in the backend
 */
public enum SpecialClazzes
{
  // dummy entry to report failure of getSpecialId()
  c_NOT_FOUND           (null          , ()->null),

  c_universe            (c_NOT_FOUND   , ()->null),
  c_Any                 (c_universe    , ()->Types.resolved.f_Any),
  c_i8                  (c_universe    , ()->Types.resolved.f_i8),
  c_i16                 (c_universe    , ()->Types.resolved.f_i16),
  c_i32                 (c_universe    , ()->Types.resolved.f_i32),
  c_i64                 (c_universe    , ()->Types.resolved.f_i64),
  c_u8                  (c_universe    , ()->Types.resolved.f_u8),
  c_u16                 (c_universe    , ()->Types.resolved.f_u16),
  c_u32                 (c_universe    , ()->Types.resolved.f_u32),
  c_u64                 (c_universe    , ()->Types.resolved.f_u64),
  c_f32                 (c_universe    , ()->Types.resolved.f_f32),
  c_f64                 (c_universe    , ()->Types.resolved.f_f64),
  c_void                (c_universe    , ()->Types.resolved.f_void),
  c_bool                (c_universe    , ()->Types.resolved.f_bool),
  c_const_string        (c_universe    , ()->Types.resolved.f_const_string),
  c_CS_utf8_data        (c_const_string, ()->Types.resolved.f_const_string_utf8_data),
  c_String              (c_universe    , ()->Types.resolved.f_String),
  c_Mutex               (c_universe    , ()->Types.resolved.f_Mutex),
  c_Condition           (c_universe    , ()->Types.resolved.f_Condition),
  c_File_Descriptor     (c_universe    , ()->Types.resolved.f_File_Descriptor),
  c_Directory_Descriptor(c_universe    , ()->Types.resolved.f_Directory_Descriptor),
  c_Java_Ref            (c_universe    , ()->Types.resolved.f_Java_Ref),
  c_Mapped_Memory       (c_universe    , ()->Types.resolved.f_Mapped_Memory),
  c_Native_Ref          (c_universe    , ()->Types.resolved.f_Native_Ref),
  c_Internal_Thread     (c_universe    , ()->Types.resolved.f_Internal_Thread),
  c_stackoverflow_cause (c_universe    , ()->Types.resolved.f_stackoverflow_cause)
  ;

  final SpecialClazzes _outer;
  final Supplier<dev.flang.ast.AbstractFeature> _getFeature;

  SpecialClazzes(SpecialClazzes outer,
                 Supplier<dev.flang.ast.AbstractFeature> getFeature)
  {
    _outer = outer;
    _getFeature = getFeature;
  }

}
