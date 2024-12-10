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
 * Source of record SiteRecord
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir;

import java.io.Serializable;

import dev.flang.ir.IR.ExprKind;

public record SiteRecord(
  int clazzAt,
  boolean alwaysResultsInVoid,
  ExprKind codeAt,
  int constClazz,
  byte[] constData,
  int accessedClazz,
  int[] accessedClazzes,
  int accessTargetClazz,
  int tagValueClazz,
  int assignedType,
  int boxValueClazz,
  int boxResultClazz,
  int matchStaticSubject,
  int matchCaseCount,
  int[][] matchCaseTags,
  int[] matchCaseCode,
  int tagNewClazz,
  int tagTagNum,
  int[] matchCaseField,
  boolean accessIsDynamic
)implements Serializable
{
  private static final long serialVersionUID = 1L;
}
