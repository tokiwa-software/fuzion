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
 * Source of interface IClazzes
 *
 *---------------------------------------------------------------------*/


package dev.flang.air;

import java.util.Set;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.util.HasSourcePosition;

public interface IClazzes {

  void findAllClasses(Clazz main);

  Set<Clazz> all();

  HasSourcePosition isUsedAt(AbstractFeature af);

  /**
   * Has this feature been found to be used?
   */
  default boolean isUsed(AbstractFeature f)
  {
    return isUsedAt(f) != null;
  }

  Clazz i8();
  Clazz i16();
  Clazz i32();
  Clazz i64();
  Clazz u8();
  Clazz u16();
  Clazz u32();
  Clazz u64();
  Clazz f32();
  Clazz f64();
  Clazz bool();
  Clazz c_TRUE();
  Clazz c_FALSE();
  Clazz Const_String();
  Clazz String();
  Clazz c_unit();
  Clazz Const_String_utf8_data();
  Clazz c_void();
  Clazz universe();
  Clazz Any();
  Clazz c_address();
  Clazz fuzionSysPtr();
  Clazz fuzionSysArray_u8();
  Clazz fuzionSysArray_u8_data();
  Clazz fuzionSysArray_u8_length();
  Clazz fuzionJavaObject();
  Clazz fuzionJavaObject_Ref();
  Clazz c_error();

  Clazz clazz(AbstractType abstractType);

}
