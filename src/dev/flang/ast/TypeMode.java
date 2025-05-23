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
 * Source of enum TypeMode
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;


/**
 * for specifying the mode of a type.
 * May be one of "ThisType", "RefType" or "ValueType".
 */
public enum TypeMode {

  ThisType(0x00),
  RefType(0x01),
  ValueType(0x02);

  public int num;

  TypeMode(int num) {
      this.num = num;
  }

  public static TypeMode fromInt(int num)
  {
    return switch (num) {
      case 0x00 -> TypeMode.ThisType;
      case 0x01 -> TypeMode.RefType;
      case 0x02 -> TypeMode.ValueType;
      default   -> throw new Error("Illegal TypeMode: " + num);
    };
  }

}
