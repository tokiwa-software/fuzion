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
 * Source of enum State
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

/**
 * Denotes in which step of the resolution
 * a feature is currently in.
 */
public enum State
{
  LOADING,
  FINDING_DECLARATIONS,
  LOADED,
  RESOLVING,
  RESOLVING_INHERITANCE,
  RESOLVED_INHERITANCE,
  RESOLVING_DECLARATIONS,
  RESOLVED_DECLARATIONS,
  RESOLVING_TYPES,
  RESOLVED_TYPES,
  RESOLVING_SUGAR1,
  RESOLVED_SUGAR1,
  TYPES_INFERENCING,
  TYPES_INFERENCED,
  RESOLVING_SUGAR2,
  RESOLVED_SUGAR2,
  BOXING,
  BOXED,
  CHECKING_TYPES,
  RESOLVED,
  ERROR;

  public boolean atLeast(State s)
  {
    return this.ordinal() >= s.ordinal();
  }

}
