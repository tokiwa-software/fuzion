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
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;
import dev.flang.util.YesNo;


/**
 * AbstractType represents a Fuzion Type in the front end.  This type might
 * either be part of the abstract syntax tree or part of a binary module file.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractType extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Cached result of dependsOnGenerics().
   */
  public YesNo _dependsOnGenerics = YesNo.dontKnow;


  /*-----------------------------  methods  -----------------------------*/


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
        AstErrors.illegalUseOfOpenFormalGeneric(pos(), genericArgument());
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
   * For a resolved type, check if it is a choice type and if so, return the
   * list of choices. Otherwise, return null.
   */
  public List<AbstractType> choiceGenerics()
  {
    if (PRECONDITIONS) require
      (isGenericArgument() || !(this instanceof Type tt) || tt.feature != null);  // type must be resolved

    if (!isGenericArgument())
      {
        var g = featureOfType().choiceGenerics();
        if (g != null)
          {
            return replaceGenerics(g);
          }
      }
    return null;
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


  /**
   * Check if a type parameter actual can be assigned to a type parameter with
   * constraint this.
   *
   * @param actual the actual type.
   */
  public boolean constraintAssignableFrom(AbstractType actual)
  {
    if (PRECONDITIONS) require
      (Types.intern(this  ) == this,
       Types.intern(actual) == actual,
       this  .isGenericArgument() || this  .featureOfType() != null || Errors.count() > 0,
       actual.isGenericArgument() || actual.featureOfType() != null || Errors.count() > 0,
       Errors.count() > 0 || this != Types.t_ERROR && actual != Types.t_ERROR);

    var result = containsError() ||
      actual.containsError()     ||
      this   == actual           ||
      actual == Types.resolved.t_void;
    if (!result && !isGenericArgument())
      {
        if (actual.isGenericArgument())
          {
            result = constraintAssignableFrom(actual.genericArgument().constraint());
          }
        else
          {
            check
              (actual.featureOfType() != null || Errors.count() > 0);
            if (actual.featureOfType() != null)
              {
                if (actual.featureOfType() == featureOfType())
                  {
                    if (actual.generics().size() == generics().size()) // NYI: Check: What aboout open generics?
                      {
                        result = true;
                        // NYI: Should we check if the generics are assignable as well?
                        //
                        //  for (int i = 0; i < _generics.size(); i++)
                        //    {
                        //      var g0 = _generics.get(i);
                        //      var g = _generics.get(i);
                        //      if (g.isGenericArgument())
                        //        {
                        //          g = g.generic.constraint();
                        //        }
                        //      result = result && g0.constraintAssignableFrom(actual._generics.get(i));
                        //    }
                      }
                  }
                if (!result)
                  {
                    for (Call p: actual.featureOfType().inherits())
                      {
                        var pt = Types.intern(actual.actualType(p.type()));
                        if (constraintAssignableFrom(pt))
                          {
                            result = true;
                          }
                      }
                  }
              }
          }
      }
    return result;
  }


  /**
   * Replace generic types used in given List of types by the actual generic arguments
   * given as actualGenerics.
   *
   * @param f the feature the generics belong to.
   *
   * @param genericsToReplace a list of possibly generic types
   *
   * @param actualGenerics the actual generics to feat that shold replace the
   * formal generics found in genericsToReplace.
   *
   * @return a new list of types with all formal generic arguments from this
   * replaced by the corresponding actualGenerics entry.
   */
  static List<AbstractType> actualTypes(AbstractFeature f, List<AbstractType> genericsToReplace, List<AbstractType> actualGenerics)
  {
    if (PRECONDITIONS) require
      (Errors.count() > 0 ||
       f.generics().sizeMatches(actualGenerics));

    var result = genericsToReplace;
    if (f != null && !genericsToReplace.isEmpty())
      {
        if (genericsToReplace == f.generics().asActuals())  /* shortcut for properly handling open generics list */
          {
            result = actualGenerics;
          }
        else
          {
            boolean changes = false;
            for (var t: genericsToReplace)
              {
                changes = changes || t.actualType(f, actualGenerics) != t;
              }
            if (changes)
              {
                result = new List<>();
                for (var t: genericsToReplace)
                  {
                    result.add(t.actualType(f, actualGenerics));
                  }
              }
          }
      }
    return result;
  }


  /**
   * Replace formal generics from this type's feature in given list by the
   * actual generic arguments of this type.
   *
   * @param genericsToReplace a list of possibly generic types
   *
   * @return a new list of types with all formal generic arguments from
   * featureOfType() replaced by the corresponding generics entry of this type.
   */
  public List<AbstractType> replaceGenerics(List<AbstractType> genericsToReplace)
  {
    if (PRECONDITIONS) require
      (featureOfType().generics().sizeMatches(generics()));

    return actualTypes(featureOfType(), genericsToReplace, generics());
  }


  /**
   * Does this type (or its outer type) depend on generics. If not, actualType()
   * will not need to do anything on this.
   */
  public boolean dependsOnGenerics()
  {

    if (PRECONDITIONS) require
      (checkedForGeneric());

    YesNo result = _dependsOnGenerics;
    if (result == YesNo.dontKnow)
      {
        if (isGenericArgument())
          {
            result = YesNo.yes;
          }
        else
          {
            result = YesNo.no;
            if (generics() != Type.NONE)
              {
                for (var t: generics())
                  {
                    if (t.dependsOnGenerics())
                      {
                        result = YesNo.yes;
                      }
                  }
              }
            if (outer() != null && outer().dependsOnGenerics())
              {
                result = YesNo.yes;
              }
          }
        _dependsOnGenerics = result;
      }
    return result == YesNo.yes;
  }


  /**
   * Replace generic types used in given type t by the actual generic arguments
   * given in this.
   *
   * @param t a possibly generic type, must not be an open generic.
   *
   * @return t with all generic arguments from this.featureOfType._generics
   * replaced by this._generics.
   */
  public AbstractType actualType(AbstractType t)
  {
    if (PRECONDITIONS) require
      (checkedForGeneric(),
       t != null,
       t.checkedForGeneric(),
       Errors.count() > 0 || !t.isOpenGeneric(),
       featureOfType().generics().sizeMatches(generics()));

    var result = t;
    if (result.dependsOnGenerics())
      {
        result = result.actualType(featureOfType(), generics());
        if (outer() != null)
          {
            result = outer().actualType(result);
          }
      }

    if (POSTCONDITIONS) ensure
      (result != null);
    return result;
  }


  /**
   * Check if type t depends on a formal generic parameter of this. If so,
   * replace t by the corresponding actual generic parameter from the list
   * provided.
   *
   * @param f the feature actualGenerics belong to.
   *
   * @param actualGenerics the actual generic parameters
   *
   * @return t iff t does not depend on a formal generic parameter of this,
   * otherwise the type that results by replacing all formal generic parameters
   * of this in t by the corresponding type from actualGenerics.
   */
  public AbstractType actualType(AbstractFeature f, List<AbstractType> actualGenerics)
  {
    if (PRECONDITIONS) require
      (checkedForGeneric(),
       Errors.count() > 0 ||
       f.generics().sizeMatches(actualGenerics),
       Errors.count() > 0 || !isOpenGeneric() || genericArgument().formalGenerics() != f.generics());

    f = f.astFeature();
    AbstractType result = this;
    if (f != null)
      {
        for (Call i : f.inherits())
          {
            result = result.actualType(i.calledFeature(),
                                       i.generics);
          }
      }
    if (result.isGenericArgument())
      {
        Generic g = result.genericArgument();
        if (f != null && g.formalGenerics() == f.generics())  // t is replaced by corresponding actualGenerics entry
          {
            result = result.ensureNotOpen() ? g.replace(actualGenerics)
                                            : Types.t_ERROR;
          }
      }
    else
      {
        var g2 = actualTypes(f, result.generics(), actualGenerics);
        var o2 = (result.outer() == null) ? null : result.outer().actualType(f, actualGenerics);
        if (g2 != result.generics() ||
            o2 != result.outer()    ||
            o2 != null && o2.astType() != result.outer().astType() /* NYI: remove this line as soon as astType is gone */ )
          {
            var hasError = o2 == Types.t_ERROR;
            for (var t : g2)
              {
                hasError = hasError || (t == Types.t_ERROR);
              }
            result = hasError ? Types.t_ERROR : new Type(result.astType(), g2, o2 == null ? null : o2.astType());
          }
      }
    return result.astType(); // NYI: remove .astType(), needed only because isAssignableFrom is not correct yet.
  }


  public abstract AbstractFeature featureOfType();
  public abstract AbstractType asRef();
  public abstract AbstractType asValue();
  public abstract boolean isRef();
  public abstract SourcePosition pos();
  public abstract List<AbstractType> generics();
  public abstract boolean isAssignableFrom(AbstractType actual, Set<String> assignableTo);
  public abstract int compareToIgnoreOuter(Type other);
  public abstract boolean isGenericArgument();
  public abstract AbstractType outer();
  public abstract Generic genericArgument();

  public Type astType() { return (Type) this; }
}

/* end of file */
