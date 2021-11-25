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


  /**
   * This is used only during early phases of the front end before types where
   * checked if they are or contains generics.
   */
  boolean checkedForGeneric()
  {
    return true;
  }


  /**
   * is this a formal generic argument that is open, i.e., the last argument in
   * a formal generic arguments list and followed by ... as A in
   * Funtion<R,A...>.
   *
   * This type needs very special treatment, it is allowed only as an argument
   * type of the last argument in an abstract feature declaration.  When
   * replacing generics by actual generics arguments, this gets replaced by a
   * (possibly empty) list of actual types.
   *
   * @return true iff this is an open generic
   */
  public boolean isOpenGeneric()
  {
    if (PRECONDITIONS) require
      (checkedForGeneric());

    return isGenericArgument() && genericArgument().isOpen();
  }


  /**
   * Check if this.isOpenGeneric(). If so, create a compile-time error.
   *
   * @return true iff !isOpenGeneric()
   */
  public boolean ensureNotOpen()
  {
    boolean result = true;

    if (PRECONDITIONS) require
      (checkedForGeneric());

    if (isOpenGeneric())
      {
        AstErrors.illegalUseOfOpenFormalGeneric(pos(), generic());
        result = false;
      }
    return result;
  }



  /**
   * Check if this is a choice type.
   */
  public boolean isChoice()
  {
    return !isGenericArgument() && featureOfType().isChoice();
  }


  /**
   * Check if this or any of its generic arguments is Types.t_ERROR.
   */
  public boolean containsError()
  {
    return false;
  }


  /**
   * Check if a value of static type actual can be assigned to a field of static
   * type this.  This performs static type checking, i.e., the types may still
   * be or depend on generic parameters.
   *
   * @param actual the actual type.
   */
  public boolean isAssignableFrom(AbstractType actual)
  {
    return isAssignableFrom(actual, null);
  }


  /**
   * Check if a value of static type actual can be assigned to a field of static
   * type this.  This performs static type checking, i.e., the types may still
   * be or depend on generic parameters.
   *
   * In case any of the types involved are or contain t_ERROR, this returns
   * true. This is convenient to avoid the creation of follow-up errors in this
   * case.
   *
   * @param actual the actual type.
   */
  public boolean isAssignableFromOrContainsError(AbstractType actual)
  {
    return
      containsError() || actual.containsError() || isAssignableFrom(actual);
  }


  /**
   * Check if given value can be assigned to this static type.  In addition to
   * isAssignableFromOrContainsError, this checks if 'expr' is not '<xyz>.this'
   * (Current or an outer ref) that might be a value type that is a heir of this
   * type.
   *
   * @param expr the expression to be assigned to a variable of this type.
   *
   * @return true iff the assignment is ok.
   */
  public boolean isAssignableFrom(Expr expr)
  {
    var actlT = expr.type();

    check
      (actlT == Types.intern(actlT));

    return isAssignableFromOrContainsError(actlT) &&
      (!expr.isCallToOuterRef() && !(expr instanceof Current) || actlT.isRef() || actlT.isChoice());
  }


  public abstract AbstractFeature featureOfType();
  public abstract AbstractType actualType(AbstractType t);
  public abstract AbstractType actualType(AbstractFeature f, List<AbstractType> actualGenerics);
  public abstract AbstractType asRef();
  public abstract AbstractType asValue();
  public abstract boolean isRef();
  public abstract List<AbstractType> replaceGenerics(List<AbstractType> generics);
  public abstract SourcePosition pos();
  public abstract List<AbstractType> generics();
  public abstract boolean isAssignableFrom(AbstractType actual, Set<String> assignableTo);
  public abstract int compareToIgnoreOuter(Type other);
  public abstract boolean isFreeFromFormalGenerics();
  public abstract boolean isGenericArgument();
  public abstract AbstractType outer();
  public abstract boolean outerMostInSource();
  public abstract boolean dependsOnGenerics();
  public abstract Generic generic();
  public abstract Generic genericArgument();
  public abstract List<AbstractType> choiceGenerics();
  public abstract boolean constraintAssignableFrom(AbstractType actual);

  public Type astType() { return (Type) this; }
}

/* end of file */
