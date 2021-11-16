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
 * Source of class AbstractType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Set;

import dev.flang.util.ANY;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * AbstractType represents a Fuzion Type in the front end.  This type might
 * either be part of the abstract syntax tree or part of a binary module file.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractType extends ANY
{

  public abstract AbstractFeature featureOfType();
  public abstract boolean isOpenGeneric();
  public abstract AbstractType actualType(AbstractType t);
  public abstract AbstractType actualType(AbstractFeature f, List<AbstractType> actualGenerics);
  public abstract AbstractType asRef();
  public abstract AbstractType asValue();
  public abstract boolean isRef();
  public abstract boolean isChoice();
  public abstract List<AbstractType> replaceGenerics(List<AbstractType> genenrics);
  public abstract SourcePosition pos();
  public abstract List<AbstractType> generics();
  public abstract boolean isAssignableFrom(AbstractType t);
  public abstract boolean isAssignableFromOrContainsError(AbstractType t);
  public abstract boolean isAssignableFrom(Expr expr);
  abstract boolean isAssignableFrom(AbstractType actual, Set<String> assignableTo);
  public abstract int compareToIgnoreOuter(Type other);
  public abstract boolean isFreeFromFormalGenerics();
  public abstract boolean isFreeFromFormalGenericsInSource();
  public abstract boolean isGenericArgument();
  public abstract AbstractType outer();
  public abstract boolean outerMostInSource();
  abstract boolean dependsOnGenerics();
  abstract boolean containsError();
  abstract Generic generic();
  public abstract Generic genericArgument();
  abstract List<AbstractType> choiceGenerics();
  abstract boolean constraintAssignableFrom(AbstractType actual);
  abstract boolean ensureNotOpen();

  public Type astType() { return (Type) this; }
}

/* end of file */
