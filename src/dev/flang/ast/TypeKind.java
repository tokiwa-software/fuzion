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
 * Source of enum TypeKind
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;


/**
 * for specifying the kind of a type.
 * May be one of "ThisType", "RefType" or "ValueType".
 */
public enum TypeKind {

  GenericArgument(0x00),
  ValueType(0x01),
  RefType(0x02),
  ThisType(0x03);

  public int num;

  TypeKind(int num) {
      this.num = num;
  }

  public static TypeKind fromInt(int num)
  {
    return switch (num) {
      case 0x00 -> TypeKind.GenericArgument;
      case 0x01 -> TypeKind.ValueType;
      case 0x02 -> TypeKind.RefType;
      case 0x03 -> TypeKind.ThisType;
      default   -> throw new Error("Illegal TypeKind: " + num);
    };
  }

}
