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
 * Source of record ClazzRecord
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir;

import java.io.Serializable;

import dev.flang.fuir.FUIR.LifeTime;
import dev.flang.ir.IR.FeatureKind;

public record ClazzRecord(
  String clazzBaseName,
  int clazzOuterClazz,
  boolean clazzIsBoxed,
  int[] clazzArgs,
  FeatureKind clazzKind,
  int clazzOuterRef,
  int clazzResultClazz,
  boolean clazzIsRef,
  boolean clazzIsUnitType,
  boolean clazzIsChoice,
  int clazzAsValue,
  int[] clazzChoices,
  int[] clazzInstantiatedHeirs,
  boolean hasData,
  boolean clazzNeedsCode,
  int[] clazzFields,
  int clazzCode,
  int clazzResultField,
  boolean clazzFieldIsAdrOfValue,
  int clazzTypeParameterActualType,
  String clazzOriginalName,
  int[] clazzActualGenerics,
  int lookupCall,
  int lookup_static_finally,
  LifeTime lifeTime,
  byte[] clazzTypeName,
  int inlineArrayElementClazz,
  String clazzAsStringHuman
  ) implements Serializable
{
  private static final long serialVersionUID = 1L;
}
