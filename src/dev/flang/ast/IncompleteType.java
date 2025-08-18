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
 * Source of class IncompleteType
 *
 *---------------------------------------------------------------------*/


package dev.flang.ast;

import dev.flang.util.Errors;
import dev.flang.util.List;

/**
 * An incomplete type is a type whose generics are missing.
 * I.e. a type from a case field where it is allowed to omit the generics.
 */
public class IncompleteType extends ResolvedType {
  private final AbstractFeature _feature;
  private final TypeKind _typeKind;
  public IncompleteType(AbstractFeature f, TypeKind typeKind)
  {
    _feature = f;
    _typeKind = typeKind;
  }
  @Override protected AbstractFeature backingFeature() { return _feature; }
  @Override public List<AbstractType> generics() { return AbstractCall.NO_GENERICS; }
  @Override public AbstractType outer() { if (CHECKS) check(Errors.any()); return null; }
  @Override public TypeKind kind() { return _typeKind; }
}
