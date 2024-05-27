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
 * Source of enum RefOrVal
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

/**
 * Is this type explicitly a reference or a value type, or whatever the
 * underlying feature is?
 */
enum RefOrVal
  {
    Boxed,                  // this is boxed value type or an explicit reference type
    Value,                  // this is an explicit value type
    LikeUnderlyingFeature,  // this is ref or value as declared for the underlying feature
    ThisType,               // this is the type of feature().this.type, i.e., it may be an heir type
  }
