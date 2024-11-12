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
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;
import dev.flang.util.StringHelpers;
import dev.flang.util.YesNo;


/**
 * AbstractType represents a Fuzion Type in the front end.  This type might
 * either be part of the abstract syntax tree or part of a binary module file.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractType extends ANY implements Comparable<AbstractType>
{

  /*----------------------------  variables  ----------------------------*/


  /**
   * Cached result of dependsOnGenerics().
   */
  public YesNo _dependsOnGenerics = YesNo.dontKnow;


  /**
   * Cached results for applyTypePars(t) and applyTypePars(f, List<AbstractType>);
   */
  private AbstractType _appliedTypeParsCachedFor1;
  private AbstractType _appliedTypeParsCache;
  private AbstractFeature _appliedTypePars2CachedFor1;
  private List<AbstractType> _appliedTypePars2CachedFor2;
  private AbstractType _appliedTypePars2Cache;


  /**
   * Cached result of calling usedFeatures(_usedFeatures).
   */
  Set<AbstractFeature> _usedFeatures = null;



  /*---------------------------  static methods  ---------------------------*/




  /*--------------------------  abstract methods  --------------------------*/



  /**
   * The sourcecode position of the declaration point of this type, or, for
   * unresolved types, the source code position of its use.
   */
  public abstract SourcePosition declarationPos();


  /**
   * Is this type a generic argument (true) or false backed by a feature (false)?
   */
  public abstract boolean isGenericArgument();


  /**
   * For a resolved normal type, return the underlying feature.
   *
   * Requires that this is resolved and !isGenericArgument().
   *
   * @return the underlying feature.
   */
  public abstract AbstractFeature feature();


  /**
   * For a normal type, this is the list of actual type parameters given to the type.
   *
   * Requires that this is resolved and !isGenericArgument().
   */
  public abstract List<AbstractType> generics();


  /**
   * For a resolved parametric type return the generic.
   *
   * Requires that this is resolved and isGenericArgument().
   */
  public abstract Generic genericArgument();


  /**
   * The outer of this type. May be null.
   *
   * Requires that this is resolved and !isGenericArgument().
   */
  public abstract AbstractType outer();


  /**
   * Is this type denoting a reference type?
   */
  public abstract boolean isRef();


  /**
   * Is this a this-type?
   */
  public abstract boolean isThisType();


  /**
   * This type as a reference.
   */
  public abstract AbstractType asRef();


  /**
   * This type as a value.
   */
  public abstract AbstractType asValue();


  /**
   * Return this type as a this-type, a type denoting the
   * instance of this type in the current context.
   *
   * Requires that this is resolved and !isGenericArgument().
   */
  public abstract AbstractType asThis();


  /**
   * traverse a resolved type collecting all features this type uses.
   *
   * @param s the features that have already been found
   */
  protected abstract void usedFeatures(Set<AbstractFeature> s);



  /*-----------------------------  methods  -----------------------------*/



  /**
   * resolve this type. This is only needed for ast.Type, for fe.LibraryType
   * this is a NOP.
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this type is used
   */
  AbstractType resolve(Resolution res, Context context)
  {
    return this;
  }


  /**
   * For a normal type, resolve the actual type parameters.
   *
   * @param pos source code position of the unresolved types whose generics we
   * are resolving.
   *
   * @param res the resolution instance
   *
   * @param outerfeat the outer feature this type is declared in.
   */
  AbstractType resolveGenerics(HasSourcePosition pos, Resolution res, AbstractFeature outerfeat)
  {
    return this;
  }


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
   * Function<R,A...>.
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
   * Check if this is a choice type.
   */
  public boolean isChoice()
  {
    return !isGenericArgument() && feature().isChoice();
  }


  /**
   * For a resolved type, check if it is a choice type and if so, return the
   * list of choices.
   *
   * @param context the source code context where this Type is used
   */
  public List<AbstractType> choiceGenerics(Context context)
  {
    if (PRECONDITIONS) require
      (this instanceof ResolvedType,
       isChoice());

    var g = feature().choiceGenerics();
    return replaceGenerics(g)
      .map(t -> t.replace_this_type_by_actual_outer(this, context));
  }


  /**
   * Check if this or any of its generic arguments is Types.t_ERROR.
   */
  public boolean containsError()
  {
    boolean result = false;
    if (this == Types.t_ERROR)
      {
        result = true;
      }
    else if (!isGenericArgument())
      {
        for (var t: generics())
          {
            if (CHECKS) check
              (Errors.any() || t != null);
            result = result || t == null || t.containsError();
          }
      }

    if (POSTCONDITIONS) ensure
      (!result || Errors.any());

    return result;
  }


  /**
   * Check if this or any of its generic arguments is Types.t_UNDEFINED.
   *
   * @param exceptFirst if true, the first generic argument may be
   * Types.t_UNDEFINED.  This is used in a lambda 'x -> f x' of type
   * 'Function<R,X>' when 'R' is unknown and to be inferred.
   */
  public boolean containsUndefined(boolean exceptFirst)
  {
    boolean result = false;
    if (this == Types.t_UNDEFINED)
      {
        result = true;
      }
    else if (!isGenericArgument())
      {
        for (var t: generics())
          {
            if (CHECKS) check
              (Errors.any() || t != null);
            result = result || !exceptFirst && t != null && t.containsUndefined(false);
            exceptFirst = false;
          }
      }

    return result;
  }


  /**
   * Check if a value of static type actual can be assigned to a field of static
   * type this.  This performs static type checking, i.e., the types may still
   * be or depend on generic parameters.
   *
   * @param actual the actual type.
   *
   * @param context the source code context where this Type is used
   */
  public boolean isAssignableFrom(AbstractType actual, Context context)
  {
    return isAssignableFrom(actual, null, context);
  }


  /**
   * Check if a value of static type actual can be assigned to a field of static
   * type this without tagging. This performs static type checking, i.e.,
   * the types may still be or depend on generic parameters.
   *
   * @param actual the actual type.
   *
   * @param context the source code context where this Type is used
   */
  public boolean isDirectlyAssignableFrom(AbstractType actual, Context context)
  {
    return actual.isVoid()
         || (!isChoice() && isAssignableFrom(actual, context))
         || (isChoice() && compareTo(actual) == 0);
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
   *
   * @param context the source code context where this Type is used
   */
  public boolean isAssignableFromOrContainsError(AbstractType actual, Context context)
  {
    return
      containsError() || actual.containsError() || isAssignableFrom(actual, context);
  }


  /**
   * Check if a value of static type actual can be assigned to a field of static
   * type this.  This performs static type checking, i.e., the types may still
   * be or depend on generic parameters.
   *
   * @param actual the actual type.
   *
   * @param assignableTo in case we want to show all types actual is assignable
   * to in an error message, this collects the types converted to strings.
   *
   * @param context the source code context where this Type is used
   */
  public boolean isAssignableFrom(AbstractType actual, Set<String> assignableTo, Context context)
  {
    if (PRECONDITIONS) require
      (this  .isGenericArgument() || this  .feature() != null || Errors.any(),
       actual.isGenericArgument() || actual.feature() != null || Errors.any(),
       Errors.any() || this != Types.t_ERROR && actual != Types.t_ERROR);

    if (assignableTo != null)
      {
        assignableTo.add(actual.toString());
      }
    var target_type = this  .remove_type_parameter_used_for_this_type_in_cotype();
    var actual_type = actual.remove_type_parameter_used_for_this_type_in_cotype();
    var result =
      target_type.compareTo(actual_type          ) == 0 ||
      actual_type.isVoid() ||
      target_type == Types.t_ERROR                      ||
      actual_type == Types.t_ERROR;
    if (!result && !target_type.isGenericArgument() && isRef() && actual_type.isRef())
      {
        if (actual_type.isGenericArgument())
          {
            result = isAssignableFrom(actual_type.genericArgument().constraint(context).asRef(), context);
          }
        else
          {
            if (CHECKS) check
              (actual_type.feature() != null || Errors.any());
            if (actual_type.feature() != null)
              {
                for (var p: actual_type.feature().inherits())
                  {
                    var pt = actual_type.actualType(p.type(), context);
                    if (actual_type.isRef())
                      {
                        pt = pt.asRef();
                      }
                    if (isAssignableFrom(pt, assignableTo, context))
                      {
                        result = true;
                      }
                  }
              }
          }
      }
    if (!result && target_type.isChoice() && !isThisTypeInCotype())
      {
        result = target_type.isChoiceMatch(actual_type, context);
      }
    return result;
  }


  /**
   * Helper for isAssignableFrom: check if this is a choice type and actual is
   * assignable to one of the generic arguments to this choice.
   *
   * @return true iff this is a choice and actual is assignable to one of the
   * generic arguments of this choice.
   *
   * @param context the source code context where this Type is used
   */
  private boolean isChoiceMatch(AbstractType actual, Context context)
  {
    if (PRECONDITIONS) require
      (!isGenericArgument() && feature() != null || Errors.any());

    boolean result = false;
    if (!isGenericArgument() && !isRef() && feature().isChoice())
      {
        for (var t : choiceGenerics(context))
          {
            if (CHECKS) check
              (Errors.any() || t != null);
            result = result || t != null && t.isAssignableFrom(actual, context);
          }
      }
    return result;
  }


  /**
   * Check if a type parameter actual can be assigned to a type parameter with
   * constraint this.
   *
   * @param context the source code context where this Type is used
   *
   * @param actual the actual type.
   */
  public boolean constraintAssignableFrom(Context context, AbstractType actual)
  {
    if (PRECONDITIONS) require
      (this  .isGenericArgument() || this  .feature() != null || Errors.any(),
       actual.isGenericArgument() || actual.feature() != null || Errors.any(),
       Errors.any() || this != Types.t_ERROR && actual != Types.t_ERROR);

    var result = containsError()                   ||
      actual.containsError()                       ||
      this  .compareTo(actual               ) == 0 ||
      this  .compareTo(Types.resolved.t_Any ) == 0 ||
      actual.isVoid();
    if (!result && !isGenericArgument())
      {
        if (actual.isGenericArgument())
          {
            result = constraintAssignableFrom(context, actual.genericArgument().constraint(context));
          }
        else
          {
            if (CHECKS) check
              (actual.feature() != null || Errors.any());
            if (actual.feature() != null)
              {
                result = actual.feature() == feature() &&
                  genericsAssignable(actual, context); // NYI: Check: What about open generics?
                for (var p: actual.feature().inherits())
                  {
                    result |= !p.calledFeature().isChoice() &&
                      constraintAssignableFrom(context, p.type().applyTypePars(actual));
                  }
              }
          }
      }
    return result;
  }


  /**
   * Check if generics of type parameter `actual` are assignable to
   * generics of type parameter with constraint `this`.
   *
   * @param context the source code context where this Type is used
   */
  private boolean genericsAssignable(AbstractType actual, Context context)
  {
    if (PRECONDITIONS) require
      (!this.isGenericArgument(),
       !actual.isGenericArgument());

    var ogs = actual.generics();
    if (ogs.size() != generics().size())
      {
        return false;
      }
    var i1 = generics().iterator();
    var i2 = ogs.iterator();
    while(i1.hasNext())
      {
        var g = i1.next();
        var og = i2.next();
        if (
          // NYI check recursive type, e.g.:
          // this  = monad monad.A monad.MA
          // other = monad option.T (option option.T)
          // for now just prevent infinite recursion
            !(g.isGenericArgument() && (g.genericArgument().constraint(context) == this ||
                                        g.genericArgument().constraint(context).constraintAssignableFrom(context, og))))
          // NYI what if g is not a generic argument?
          {
            return false;
          }
      }
    return true;
  }


  /**
   * Replace generic types used in given List of types by the actual generic arguments
   * given as actualGenerics.
   *
   * @param f the feature the generics belong to.
   *
   * @param genericsToReplace a list of possibly generic types
   *
   * @param actualGenerics the actual generics to feat that should replace the
   * formal generics found in genericsToReplace.
   *
   * @return a new list of types with all formal generic arguments from this
   * replaced by the corresponding actualGenerics entry.
   */
  private static List<AbstractType> applyTypePars(AbstractFeature f, List<AbstractType> genericsToReplace, List<AbstractType> actualGenerics)
  {
    if (PRECONDITIONS) require
      (Errors.any() ||
       f.generics().sizeMatches(actualGenerics));

    List<AbstractType> result;
    if (genericsToReplace instanceof FormalGenerics.AsActuals aa && aa.actualsOf(f))  /* shortcut for properly handling open generics list */
      {
        result = actualGenerics;
      }
    else
      {
        result = genericsToReplace.map(t -> t.applyTypePars(f, actualGenerics));
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
   * feature() replaced by the corresponding generics entry of this type.
   */
  public List<AbstractType> replaceGenerics(List<AbstractType> genericsToReplace)
  {
    if (PRECONDITIONS) require
      (Errors.any() ||
       feature().generics().sizeMatches(generics()));

    return applyTypePars(feature(), genericsToReplace, generics());
  }


  /**
   * Does this type (or its outer type) depend on generics. If not, applyTypePars()
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
            if (generics() != UnresolvedType.NONE)
              {
                for (var t: generics())
                  {
                    if (CHECKS) check
                      (Errors.any() || t != null);
                    if (t != null &&
                        t.dependsOnGenerics())
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
   * Does this type (or its outer type) depend on generics except the implicit
   * THIS_TYPE used for type features?
   *
   * NYI: HACK: This is a workaround for #3160 that is used in the air package
   * instead of dependsOnGenerics() temporarily until we have a proper fix.
   */
  public boolean dependsOnGenericsExceptTHIS_TYPE()
  {
    var res =
      dependsOnGenerics()  &&
      (    isGenericArgument() && !isThisTypeInCotype()
       || !isGenericArgument() && (generics().stream().anyMatch(t -> t.dependsOnGenericsExceptTHIS_TYPE()) ||
                                   outer() != null && outer().dependsOnGenericsExceptTHIS_TYPE()));
    return res;
  }


  /**
   * Replace generic types used by this type by the actual types given in
   * target.
   *
   * @param target a target type this is used in
   *
   * @return this with all generic arguments from target.feature._generics
   * replaced by target._generics.
   */
  public AbstractType applyTypePars(AbstractType target)
  {
    if (PRECONDITIONS) require
      (checkedForGeneric(),
       target != null,
       target.checkedForGeneric(),
       Errors.any() || !isOpenGeneric(),
       Errors.any() || target.isGenericArgument() || target.feature().generics().sizeMatches(target.generics()));

    AbstractType result;
    if (_appliedTypeParsCachedFor1 == target)
      {
        result = _appliedTypeParsCache;
      }
    else
      {
        result = applyTypePars_(target);
        _appliedTypeParsCachedFor1 = target;
        _appliedTypeParsCache = result;
      }

    if (POSTCONDITIONS) ensure
      (result != null);
    return result;
  }


  /**
   * Replace generic types used by this type by the actual types given in
   * target.
   *
   * Internal version of applyTypePars(target) that does not perform caching.
   *
   * @param target a target type this is used in
   *
   * @return this with all generic arguments from target.feature._generics
   * replaced by target._generics.
   */
  private AbstractType applyTypePars_(AbstractType target)
  {
    /* NYI: Performance: This requires time in O(this.depth *
     * feature.inheritanceDepth * t.depth), i.e. it is in O(n³)! Caching
     * is used to alleviate this a bit, but this is probably not sufficient!
     */
    var result = this;
    if (dependsOnGenerics())
      {
        target = target.selfOrConstraint(Context.NONE);
        result = result.applyTypePars(target.feature(), target.generics());
        if (target.outer() != null)
          {
            result = result.applyTypePars(target.outer());
          }
      }
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
  public AbstractType applyTypePars(AbstractFeature f, List<AbstractType> actualGenerics)
  {
    if (PRECONDITIONS) require
      (checkedForGeneric(),
       Errors.any() ||
       f.generics().sizeMatches(actualGenerics),
       Errors.any() || !isOpenGeneric() || genericArgument().formalGenerics() != f.generics());

    AbstractType result;
    if (_appliedTypePars2CachedFor1 == f &&
        _appliedTypePars2CachedFor2 == actualGenerics)
      {
        result = _appliedTypePars2Cache;
      }
    else
      {
        result = applyTypePars_(f, actualGenerics);
        _appliedTypePars2CachedFor1 = f;
        _appliedTypePars2CachedFor2 = actualGenerics;
        actualGenerics.freeze();
        _appliedTypePars2Cache = result;
      }

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * Is this the type of a type feature, e.g., the type of `(list
   * i32).type`. Will return false for an instance of Type for which this is
   * still unknown since Type.resolve() was not called yet.
   */
  boolean isTypeType()
  {
    return !isGenericArgument() && feature().isCotype();
  }


  /**
   * A cotype has the actual underlying type as its first type parameter
   * `THIS_TYPE` in addition to the type parameters of the original type.
   *
   * In case this is a cotype, determine the actual types for generics
   * by apply the actual type parameters passed to `THIS_TYPE`.
   *
   * @return the actual generics after `THIS_TYPE.actualType` was applied.
   */
  public List<AbstractType> cotypeActualGenerics()
  {
    return cotypeActualGenerics(generics());
  }


  /**
   * A cotype has the actual underlying type as its first type parameter
   * `THIS_TYPE` in addition to the type parameters of the original type.
   *
   * In case this is a cotype, determine the actual types for the types in `g`
   * by apply the actual type parameters passed to `THIS_TYPE`.
   *
   * @param `g` list of generics, must be derived from `generics()`
   *
   * @return the actual generics after `THIS_TYPE.actualType` was applied.
   */
  private List<AbstractType> cotypeActualGenerics(List<AbstractType> g)
  {
    /* types of type features require special handling since the type
     * feature has one additional first type parameter --the underlying
     * type: this_type--, and all other type parameters need to be converted
     * to the actual type relative to that.
     */
    if (isTypeType())
      {
        var this_type = g.get(0);
        g = g.map(x -> x == this_type                     ||        // leave first type parameter unchanged
                            this_type.isGenericArgument()    ? x    // no actuals to apply in a generic arg
                                                             : this_type.actualType(x, Context.NONE));
      }
    return g;
  }


  /**
   * Check if this type depends on a formal generic parameter of f. If so,
   * replace t by the corresponding actual generic parameter from the list
   * provided.
   *
   * Internal version of applyTypePars(f, actualGenerics) that does not perform
   * caching.
   *
   * @param f the feature actualGenerics belong to.
   *
   * @param actualGenerics the actual generic parameters
   *
   * @return t iff t does not depend on a formal generic parameter of this,
   * otherwise the type that results by replacing all formal generic parameters
   * of this in t by the corresponding type from actualGenerics.
   */
  private AbstractType applyTypePars_(AbstractFeature f, List<AbstractType> actualGenerics)
  {
    if (PRECONDITIONS) require
      (f != null);

    /* NYI: Performance: This requires time in O(this.depth *
     * f.inheritanceDepth), i.e. it is in O(n²)!  Caching is used to alleviate
     * this a bit, but this is probably not sufficient!
     */
    var result = this;
    for (var i : f.inherits())
      {
        result = result.applyTypePars(i.calledFeature(),
                                      i.actualTypeParameters());
      }
    if (result.isGenericArgument())
      {
        Generic g = result.genericArgument();
        if (g.formalGenerics() != f.generics())  // if g is not formal generic of f, and g is a type feature generic, try g's origin:
          {
            g = g.cotypeOrigin();
          }
        if (g.formalGenerics() == f.generics()) // if g is a formal generic defined by f, then replace it by the actual generic:
          {
            result = g.replace(actualGenerics);
          }
      }
    else
      {
        var g2 = applyTypePars(f, result.generics(), actualGenerics);
        var o2 = (result.outer() == null) ? null : result.outer().applyTypePars(f, actualGenerics);

        g2 = cotypeActualGenerics(g2);

        if (g2 != result.generics() ||
            o2 != result.outer()       )
          {
            var hasError = o2 == Types.t_ERROR;
            for (var t : g2)
              {
                hasError = hasError || (t == Types.t_ERROR);
              }
            result = hasError ? Types.t_ERROR : result.applyTypePars(g2, o2);
          }
      }
    return result;
  }


  /**
   * Check if type t depends on a formal generic parameter of this. If so,
   * replace t by the corresponding actual generic parameter from the list
   * provided.
   *
   * Unlike applyTypePars(), this does not traverse outer types.
   *
   * @param target the target whose actuals type parameters should be applied to
   * this.
   *
   * @param select true iff this is an open generic type and we select a given
   * actual generic.
   *
   * @return t iff t does not depend on a formal generic parameter of this,
   * otherwise the type that results by replacing all formal generic parameters
   * of this in t by the corresponding type from actualGenerics.
   */
  public AbstractType applyTypeParsLocally(AbstractType target, int select)
  {
    if (PRECONDITIONS) require
      (target != null,
       Errors.any() || !isOpenGeneric() || (select >= 0));

    var result = this;
    if (dependsOnGenerics())
      {
        result = result.applyTypeParsLocally(target.feature(), target.generics(), select);
      }
    return result;
  }


  /**
   * Check if type t depends on a formal generic parameter of this. If so,
   * replace t by the corresponding actual generic parameter from the list
   * provided.
   *
   * Unlike applyTypePars(), this does not traverse outer types.
   *
   * @param f the feature actualGenerics belong to.
   *
   * @param actualGenerics the actual generic parameters
   *
   * @param select true iff this is an open generic type and we select a given
   * actual generic.
   *
   * @return t iff t does not depend on a formal generic parameter of this,
   * otherwise the type that results by replacing all formal generic parameters
   * of this in t by the corresponding type from actualGenerics.
   */
  public AbstractType applyTypeParsLocally(AbstractFeature f, List<AbstractType> actualGenerics, int select)
  {
    if (PRECONDITIONS) require
      (f != null,
       actualGenerics != null,
       Errors.any() || !isOpenGeneric() || (select >= 0));

    var result = this;
    if (result.isGenericArgument())
      {
        Generic g = result.genericArgument();
        if (g.formalGenerics() != f.generics())  // if g is not formal generic of f, and g is a type feature generic, try g's origin:
          {
            g = g.cotypeOrigin();
          }
        if (g.formalGenerics() == f.generics()) // if g is a formal generic defined by f, then replace it by the actual generic:
          {
            if (g.isOpen())
              {
                var tl = g.replaceOpen(actualGenerics);
                if (CHECKS) check
                  (Errors.any() || select >= 0 && select <= tl.size());
                if (select >= 0 && select <= tl.size())
                  {
                    result = tl.get(select);
                  }
                else
                  {
                    result = Types.t_ERROR;
                  }
              }
            else
              {
                result = g.replace(actualGenerics);
              }
          }
      }
    else
      {
        var generics = result.generics();
        var g2 = generics instanceof FormalGenerics.AsActuals aa && aa.actualsOf(f)
          ? actualGenerics
          : generics.map(t -> t.applyTypeParsLocally(f, actualGenerics, -1));
        var o2 = (result.outer() == null) ? null : result.outer().applyTypePars(f, actualGenerics);

        g2 = cotypeActualGenerics(g2);

        if (g2 != result.generics() ||
            o2 != result.outer()       )
          {
            var hasError = o2 == Types.t_ERROR;
            for (var t : g2)
              {
                hasError = hasError || (t == Types.t_ERROR);
              }
            result = hasError ? Types.t_ERROR : result.applyTypePars(g2, o2);
          }
      }
    return result;
  }


  /**
   * For a type that is not a type parameter, create a new variant using given
   * actual generics and outer type.
   *
   * @param g2 the new actual generics to be used
   *
   * @param o2 the new outer type to be used (which may also differ in its
   * actual generics).
   *
   * @return a new type with same feature(), but using g2/o2 as generics
   * and outer type.
   */
  public AbstractType applyTypePars(List<AbstractType> g2, AbstractType o2)
  {
    if (PRECONDITIONS) require
      (!isGenericArgument());

    throw new Error("actualType not supported for "+getClass());
  }


  /**
   * Use this type as a target type for a call using type `t` and determine the
   * actual type corresponding to `t`. For this, type parameters used in `t` are
   * replaced by the actual type parameters in `this` and `this.type` within `t`
   * will be replaced by the actual type in `this`.
   *
   * @param t a type, must not be an open generic.
   *
   * @param context the source code context where this Type is used
   *
   * @return t with type parameters replaced by the corresponding actual type
   * parameters in `this` and `this.type`s replaced by the corresponding actual
   * type in `this`.
   */
  public AbstractType actualType(AbstractType t, Context context)
  {
    if (PRECONDITIONS) require
      (!isGenericArgument(),
       !t.isOpenGeneric());

    return t.applyTypePars(this)
      .replace_this_type_by_actual_outer(this, context);
  }


  /**
   * Check that in case this is a choice type, it is valid, i.e., it is a value
   * type and the generic arguments to the choice are different.  Create compile
   * time error in case this is not the case.
   *
   * @param pos source position to report as part of the error message
   *
   * @param context the source code context where this Type is used
   *
   * @return this or Types.t_ERROR in case an error was reported.
   */
  AbstractType checkChoice(SourcePosition pos, Context context)
  {
    var result = this;
    if (isChoice())
      {
        var g = choiceGenerics(context);
        if (CHECKS) check
          (Errors.any() || !isRef());

        int i1 = 0;
        for (var t1 : g)
          {
            int i2 = 0;
            for (var t2 : g)
              {
                if (i1 < i2)
                  {
                    if (!t1.disjoint(t2, context) &&
                         t1 != Types.t_ERROR &&
                         t2 != Types.t_ERROR)
                      {
                        AstErrors.genericsMustBeDisjoint(pos, t1, t2);
                        result = Types.t_ERROR;
                      }
                  }
                i2++;
              }
            i1++;
          }
      }
    return result;
  }


  /**
   * Are this and other disjoint?
   * In other words:
   * Do the sets these types represent not have any overlapping values?
   *
   * @param context the source code context where this Type is used
   */
  private boolean disjoint(AbstractType other, Context context)
  {
    return this.isVoid()
      || other.isVoid()
      ||    !this .isDirectlyAssignableFrom(other, context)
         && !other.isDirectlyAssignableFrom(this , context);
  }


  public AbstractType visit(FeatureVisitor v, AbstractFeature outerfeat)
  {
    throw new Error("AbstractType.visit not implemented by "+getClass());
  }


  /**
   * Check if this is a choice and exactly one variant of the choice matches the
   * given predicate. If so, return that variant.
   *
   * @param p a predicate over AbstractType
   *
   * @param context the source code context where this Type is used
   *
   * @return the single choice type for which p holds, this if this is not a
   * choice or the number of matches is not 1.
   */
  AbstractType findInChoice(Predicate<AbstractType> p, Context context)
  {
    return choices(context)
      .filter(p)
      .collect(Collectors.reducing((a, b) -> null))  // get single element or null if multiple
      .orElse(this);
  }


  /**
   * isFunctionType checks if this is a function type used for lambda expressions,
   * e.g., "(i32, i32) -> String".
   *
   * @return true iff this is a function type but not a `Lazy`.
   */
  public boolean isFunctionType()
  {
    return
      this != Types.t_ERROR &&
      isAnyFunctionType() &&
      feature() != Types.resolved.f_Lazy;
  }


  /**
   * Check if this any function type, i.e., inherits directly or indirectly from
   * `Function`.
   *
   * @return true if this is a type based on a feature that is or inherits from `Function`.
   */
  public boolean isAnyFunctionType()
  {
    return
      !isGenericArgument() &&
      (feature() == Types.resolved.f_Function ||
       feature().inherits().stream().anyMatch(c -> c.calledFeature().selfType().isAnyFunctionType()));
  }


  /**
   * If this is a choice type, extract function type that might be one of the
   * choices.
   *
   * @param context the source code context where this Type is used
   *
   * @return if this is a choice and there is exactly one choice for which
   * isFunctionType() holds, return that type, otherwise return this.
   */
  AbstractType functionTypeFromChoice(Context context)
  {
    return findInChoice(cg -> cg.isFunctionType(), context);
  }


  /**
   * For a function type (see isAnyFunctionType()), return the arity of the
   * function.
   *
   * @return the number of arguments to be passed to this function type.
   */
  int arity()
  {
    if (PRECONDITIONS) require
      (isAnyFunctionType());

    var f = feature();
    if (f == Types.resolved.f_Function)
      {
        return generics().size() - 1;
      }
    else
      {
        var result = arityFromParents(f);
        if (result >= 0)
          {
            return result;
          }
        throw new Error("AbstractType.arity failed to find arity of " + this);
      }
  }


  /**
   * Recursive helper for `arity` to determine the arity by inspecting the
   * parents of `f`.
   *
   * @param f a feature
   *
   * @return the arity in case f inherits from `Function`, -1 otherwise.
   */
  private int arityFromParents(AbstractFeature f)
  {
    for (var p : f.inherits())
      {
        var pf = p.calledFeature();
        var result = pf.equals(Types.resolved.f_Function)
          ? p.actualTypeParameters().size() - 1
          : arityFromParents(pf);
        if (result >= 0)
          {
            return result;
          }
      }
    return -1;
  }


  /**
   * isLazyType checks if this is a lazy function type.
   *
   * @return true iff this is a lazy type
   */
  public boolean isLazyType()
  {
    return
      this != Types.t_ERROR &&
      !isGenericArgument() &&
      feature() == Types.resolved.f_Lazy;
  }


  /**
   * Find a type that is assignable from values of two types, this and t. If no
   * such type exists, return Types.t_ERROR.
   *
   * @param that another type or null
   *
   * @param context the source code context where this Type is used
   *
   * @return a type that is assignable both from this and that, or Types.t_ERROR if none
   * exists.
   */
  AbstractType union(AbstractType that, Context context)
  {
    AbstractType result =
      this == Types.t_ERROR                        ? Types.t_ERROR     :
      that == Types.t_ERROR                        ? Types.t_ERROR     :
      that == null                                 ? Types.t_ERROR     :
      this.isVoid()                                ? that              :
      that.isVoid()                                ? this              :
      this.isAssignableFrom(that        , context) ? this :
      that.isAssignableFrom(this        , context) ? that :
      this.isAssignableFrom(that.asRef(), context) ? this :
      that.isAssignableFrom(this.asRef(), context) ? that : Types.t_ERROR;

    if (POSTCONDITIONS) ensure
      (result == Types.t_ERROR     ||
       this.isVoid() && result == that ||
       that.isVoid() && result == this ||
       (result.isAssignableFrom(this, context) || result.isAssignableFrom(this.asRef(), context) &&
        result.isAssignableFrom(that, context) || result.isAssignableFrom(that.asRef(), context)    ));

    return result;
  }


  /**
   * Is this the type denoting `void`?
   */
  public boolean isVoid()
  {
    return compareTo(Types.resolved.t_void) == 0;
  }


  /**
   * Compare this to other for creating unique types.
   */
  public int compareTo(AbstractType other)
  {
    if (PRECONDITIONS) require
      (checkedForGeneric(),
       other != null,
       other.checkedForGeneric(),
       (this  instanceof ResolvedType),
       (other instanceof ResolvedType));

    int result = compareToIgnoreOuter(other);
    if (result == 0 && !isGenericArgument())
      {
        var to = this .outer();
        var oo = other.outer();
        result =
          (to == null && oo == null) ?  0 :
          (to == null && oo != null) ? -1 :
          (to != null && oo == null) ? +1 : to.compareTo(oo);
      }
    return result;
  }


  /**
   * Compare this to other ignoring the outer type. This is used for created in
   * clazzes when the outer clazz is known.
   */
  public int compareToIgnoreOuter(AbstractType other)
  {
    if (PRECONDITIONS) require
      (checkedForGeneric(),
       other != null,
       other.checkedForGeneric(),
       (this  instanceof ResolvedType),
       (other instanceof ResolvedType));

    int result = 0;

    if (this != other)
      {
        result =
          isGenericArgument() &&  other.isGenericArgument() ?  0 :
          isGenericArgument() && !other.isGenericArgument() ? -1 :
          !isGenericArgument() && other.isGenericArgument() ? +1 : feature().compareTo(other.feature());
        if (result == 0 && !isGenericArgument())
          {
            if (generics().size() != other.generics().size())  // this may happen for open generics lists
              {
                result = generics().size() < other.generics().size() ? -1 : +1;
              }
            else
              {
                var tg = generics().iterator();
                var og = other.generics().iterator();
                while (tg.hasNext() && result == 0)
                  {
                    var tgt = tg.next();
                    var ogt = og.next();

                    if (CHECKS) check
                      (Errors.any() || tgt != null && ogt != null);

                    if (tgt != null && ogt != null)
                      {
                        result = tgt.compareTo(ogt);
                      }
                  }
              }
          }
        if (result == 0)
          {
            result = artificialBuiltInID() - other.artificialBuiltInID();
          }
        if (result == 0 && isRef() ^ other.isRef())
          {
            result = isRef() ? -1 : 1;
          }
        if (result == 0 && isThisType() ^ other.isThisType())
          {
            result = isThisType() ? -1 : 1;
          }
        if (result == 0 && isGenericArgument())
          {
            result = genericArgument().compareTo(other.genericArgument());
          }
      }
    return result;
  }


  /**
   * Id to differentiate artificial types.
   */
  public int artificialBuiltInID()
  {
    return 0;
  }


  /**
   * This must be called on a call result type to replace `this.type` used in
   * the result type by the actual type dictated by the target of the call
   *
   * example:
   *
   *   a is
   *
   *     l list a.this.type is [a.this].as_list
   *
   *   b : a is
   *
   *   say (type_of a.l)    # should print `list a`
   *   say (type_of b.l)    # should print `list b`
   *
   * @param tt the type feature we are calling (`list a.this.type` in the example)
   * above).
   *
   * @param foundRef a consumer that will be called for all the this-types found
   * together with the ref type they are replaced with.  May be null.  This will
   * be used to check for AstErrors.illegalOuterRefTypeInCall.
   *
   * @param context the source code context where this Type is used
   *
   * @return the actual type, i.e.`list a` or `list b` in the example above.
   */
  public AbstractType replace_this_type_by_actual_outer(AbstractType tt,
                                                        BiConsumer<AbstractType, AbstractType> foundRef, Context context)
  {
    var result = this;
    do
      {
        result = result.replace_this_type_by_actual_outer2(tt, foundRef, context);
        tt = tt.isGenericArgument() ? null : tt.outer();
      }
    while (tt != null);
    return result;
  }


  /**
   * Convenience version of replace_this_type_by_actual_outer with `null` as
   * argument to `foundRef`.
   *
   * @param tt the type feature we are calling (`equatable.type` in the example)
   * above).
   *
   * @param context the source code context where this Type is used
   */
  public AbstractType replace_this_type_by_actual_outer(AbstractType tt, Context context)
  {
    return replace_this_type_by_actual_outer(tt, null, context);
  }


  /**
   * Helper for replace_this_type_by_actual_outer to replace `this.type` for
   * exactly tt, ignoring tt.outer().
   *
   * @param tt the type feature we are calling
   *
   * @param foundRef a consumer that will be called for all the this-types found
   * together with the ref type they are replaced with.  May be null.
   *
   * @param context the source code context where this Type is used
   */
  private AbstractType replace_this_type_by_actual_outer2(AbstractType tt, BiConsumer<AbstractType, AbstractType> foundRef, Context context)
  {
    var result = this;
    var att = tt.selfOrConstraint(context);
    if (isThisTypeInCotype() && tt.isGenericArgument()   // we have a type parameter TT.THIS#TYPE, which is equal to TT
        ||
        isThisType() && att.feature().inheritsFrom(feature())  // we have abc.this.type with att inheriting from abc, so use tt
        )
      {
        if (foundRef != null && tt.isRef())
          {
            foundRef.accept(this, tt);
          }
        result = tt;
      }
    else
      {
        result = applyToGenericsAndOuter(g -> g.replace_this_type_by_actual_outer2(tt, foundRef, context));
      }
    return result;
  }


  /**
   * Check this and, recursively, all types contained in this' type parameters
   * and outer types if isThisTypeInCotype() is true and the surrounding
   * type feature equals cotype.  Replace all matches by cotype's self
   * type.
   *
   * As an examples, in the code
   *
   *   f is
   *     fixed type.x option f.this.type is abstract
   *
   * when called on the result type of `f.type.x` argument `f.type`, this will
   * result in `option f`.
   *
   * @param cotype the type feature whose this.type we are replacing
   */
  public AbstractType replace_this_type_in_cotype(AbstractFeature cotype)
  {
    return isThisTypeInCotype() && cotype  == genericArgument().typeParameter().outer()
      ? cotype.cotypeOrigin().selfTypeInCoType()
      : applyToGenericsAndOuter(g -> g.replace_this_type_in_cotype(cotype));
  }


  /**
   * Check this and, recursively, all types contained in this' type parameters
   * and outer types if `this.isThisType && this.feature() == parent` is
   * true.  Replace all matches by the `heir.thisType()`.
   *
   * As an examples, in the code
   *
   *   f is
   *     x option f.this.type is ...
   *
   *   g : f is
   *     redef x option g.this.type is ...
   *
   * the result type of the inherited `f.x` is converted from `f.this.type` to
   * `g.this.type?` when checking types for the redefinition `g.x`.
   *
   * @param parent the parent feature we are inheriting `this` type from.
   *
   * @param heir the redefining feature
   */
  public AbstractType replace_this_type(AbstractFeature parent, AbstractFeature heir)
  {
    return isThisType() && feature() == parent
      ? heir.thisType()
      : applyToGenericsAndOuter(g -> g.replace_this_type(parent, heir));
  }


  /**
   * For a given type t, get the type of t's type feature. E.g., for t==string,
   * this will return the type of string.type.
   *
   * @return the type of t's type.
   */
  public AbstractType typeType()
  {
    if (PRECONDITIONS) require
      (!isGenericArgument(),
       feature().state().atLeast(State.RESOLVED));

    return typeType(null);
  }


  /**
   * For a given type t, get the type of t's type feature. E.g., for t==string,
   * this will return the type of string.type, which is 'string.#type string'
   *
   * @param res Resolution instance used to resolve the type feature that might
   * need to be created.
   *
   * @return the type of t's type.
   */
  AbstractType typeType(Resolution res)
  {
    if (PRECONDITIONS) require
      (!isGenericArgument(),
       res != null || feature().state().atLeast(State.RESOLVED));

    AbstractType result = null;
    var fot = feature();
    if (fot.isUniverse() || this == Types.t_ERROR || fot.isCotype())
      {
        result = this;
      }
    else
      {
        var g = new List<AbstractType>(this);
        g.addAll(generics());

        var tf = res != null ? fot.cotype(res) : fot.cotype();
        if (CHECKS) check
          (tf != null);
        result = ResolvedNormalType.create(g,
                                           g,
                                           outer().typeType(res),
                                           tf);
      }
    return result;
  }


  /**
   * This should be called on a formal argument in call with given `target`.  If
   * `target` is a type parameter and the formal argument type `this` depends on
   * a type features `this.type`, then replace `this.type` by the type parameter
   * `target`.
   *
   * example:
   *
   *   equatable is
   *
   *     type.equality(a, b equatable.this.type) bool is abstract
   *
   *   equals(T type : equatable, x, y T) => T.equality x y
   *
   * For the call `T.equality x y` this will be called on the formal argument
   * type for `a` (and `b`).
   *
   * The type of the formal arguments `a` and `b` is `equatable.this.type`,
   * which was replaced by the implicit first generic argument of
   * `equatable.type`.  This method will replaced it by `T` in the call
   * `T.equality x y`, such that actual arguments of the same type are
   * assignment compatible to it.
   *
   * @param tf the type feature we are calling (`equatable.type` in the example
   * above).
   *
   * @param tc the target call (`T` in the example above).
   */
  AbstractType replace_type_parameter_used_for_this_type_in_cotype(AbstractFeature tf, AbstractCall tc)
  {
    var result = this;
    if (isGenericArgument())
      {
        if (genericArgument().typeParameter() == tf.arguments().get(0))
          { // a call of the form `T.f x` where `f` is declared as
            // `abc.type.f(arg abc.this.type)`, so replace
            // `abc.this.type` by `T`.
            result = tc.calledFeature().asGenericType();
          }
      }
    else
      {
        result = applyToGenericsAndOuter(g -> g.replace_type_parameter_used_for_this_type_in_cotype(tf, tc));
      }
    return result;
  }


  /**
   * Replace implicit generic type used for `abc.this.type` in `abc.type` by
   * `abc.this.type`.
   *
   * example:
   *
   *   num is
   *
   *     type.zero num.this.type is abstract
   *
   *     plus (other num.this.type) num.this.type is abstract
   *
   *     type.sum is
   *       infix ∙ (a, b num.this.type) num.this.type is a.plus b
   *
   * here, when passing `b` to `plus`, `b` is of type `num.this.type`, which was
   * replaced by the implicit first generic argument of `num.type`, but it needs
   * to be changed back to `num.this.type`.
   */
  AbstractType remove_type_parameter_used_for_this_type_in_cotype()
  {
    var result = this;
    if (isGenericArgument())
      {
        var tp = genericArgument().typeParameter();
        var tf = tp.outer();
        if (tf.isCotype() && tp == tf.arguments().get(0))
          { // generic used for `abc.this.type` in `abc.type` by `abc.this.type`.
            result = tf.cotypeOrigin().selfType().asThis();
          }
      }
    else
      {
        result = applyToGenericsAndOuter(g -> g.remove_type_parameter_used_for_this_type_in_cotype());
      }
    return result;
  }


  /**
   * For any type parameter g used in this, in cases these are type parameters
   * of the origin of a type feature o, where o is f or an outer feature of f,
   * replace g by the corresponding type parameter of o.
   *
   * This is used to infer type parameter for a call to a feature declared in a
   * type feature where the actual arguments are instances of the original
   * (non-type) feature.
   *
   * @param f the outer feature this type is used in.
   */
  public AbstractType replace_type_parameters_of_cotype_origin(AbstractFeature f)
  {
    var t = this;
    if (!f.isUniverse() && f != Types.f_ERROR)
      {
        t = t.replace_type_parameters_of_cotype_origin(f.outer());
        if (f.isCotype())
          {
            t = t.replace_type_parameter_of_type_origin(f);
          }
      }
    return t;
  }


  /**
   * Helper for replace_type_parameters_of_cotype_origin working on a
   * given outer type feature.
   *
   * @param outerCotype one outer type feature this is used in.
   */
  private AbstractType replace_type_parameter_of_type_origin(AbstractFeature outerCotype)
  {
    if (PRECONDITIONS) require
      (outerCotype.isCotype());

    AbstractType result;
    if (isGenericArgument())
      {
        if (genericArgument().feature() == outerCotype.cotypeOrigin())
          {
            result = outerCotype.generics().list.get(genericArgument().index() + 1).type();
          }
        else
          {
            result = this;
          }
      }
    else
      {
        result = applyToGenericsAndOuter(g -> g.replace_type_parameter_of_type_origin(outerCotype));
      }
    return result;
  }


  /**
   * Apply given function to generics and outer types in this type
   * to create a new type.
   *
   * @param f function to apply to generics and outer types
   *
   * @return in case f resulted in any changes, a new type with generics and
   * outer types replaced by the corresponding results of f.apply.  this in case
   * the were no changes.
   */
  public AbstractType applyToGenericsAndOuter(java.util.function.Function<AbstractType, AbstractType> f)
  {
    var result = this;
    if (!isGenericArgument())
      {
        var g = generics();
        var ng = g.map(f);
        var o = outer();
        var no = o != null ? f.apply(o) : null;
        if (ng != g || no != o)
          {
            result = ResolvedNormalType.create(this, ng, unresolvedGenerics(), no);
          }
      }
    return result;
  }


  /**
   * For a feature `f(A, B type)` the corresponding type feature has an implicit
   * THIS#TYPE type parameter: `f.type(THIS#TYPE, A, B type)`.
   *
   + This checks if this type is this implicit type parameter.
   */
  public boolean isThisTypeInCotype()
  {
    return isGenericArgument() && genericArgument().isThisTypeInCotype();
  }


  /**
   * Check if for this or any type parameters of this, isThisType is true.  This
   * must not be the case for any clazzes in FUIR since clazzes require concrete
   * types.
   *
   * @return true if an `this.type` where found
   */
  public boolean containsThisType()
  {
    return
      isThisType() ||
      !isGenericArgument() && (generics().stream().anyMatch(g -> g.containsThisType()) ||
                               outer() != null && outer().containsThisType());
  }




  /**
   * For a normal type, this is the list of the unresolved version of actual
   * type parameters given to the type, as far as they are available. They are
   * not available, e.g., when the type was inferred or was loaded from a module
   * file.  The list might be shorter than generics().
   */
  public List<AbstractType> unresolvedGenerics() { return generics(); }



  /**
   * Get a String representation of this Type.
   *
   * Note that this does not work for instances of Type before they were
   * resolved.  Use toString() for creating strings early in the front end
   * phase.
   */
  public String asString() { return asString(false, null); }

  /**
   * Get a String representation of this Type.
   *
   * Note that this does not work for instances of Type before they were
   * resolved.  Use toString() for creating strings early in the front end
   * phase.
   */
  public String asString(boolean humanReadable) { return asString(humanReadable, null); }

  /**
   * Get a String representation of this Type.
   *
   * Note that this does not work for instances of Type before they were
   * resolved.  Use toString() for creating strings early in the front end
   * phase.
   *
   * @param context the feature to which the name should be relative to
   */
  public String asString(boolean humanReadable, AbstractFeature context)
  {
    if (PRECONDITIONS) require
      (checkedForGeneric());

    String result;

    if (isGenericArgument())
      {
        var ga = genericArgument();
        result = ga.toLongString(context) + (this.isRef() ? " (boxed)" : "");
      }
    else
      {
        var o = outer();
        String outer = o != null && (o.isGenericArgument() || !o.feature().isUniverse()) ? o.asStringWrapped(humanReadable) + "." : "";
        var f = feature();
        var typeType = f.isCotype();
        if (typeType)
          {
            f = f._cotypeOrigin;
          }
        var fn = f.featureName();
        // for a feature that does not define a type itself, the name is not
        // unique due to overloading with different argument counts. So we add
        // the argument count to get a unique name.
        var fname = (humanReadable ? fn.baseNameHuman() : fn.baseName())
          +  (f.definesType() || fn.argCount() == 0 || fn.isInternal()
                ? ""
                : FuzionConstants.INTERNAL_NAME_PREFIX + fn.argCount());

        // NYI: would be good if postFeatures could be identified not be string comparison, but with something like
        // `f.isPostFeature()`. Note that this would need to be saved in .fum file as well!
        ///
        if (fname.startsWith(FuzionConstants.POSTCONDITION_FEATURE_PREFIX))
          {
            fname = fname.substring(FuzionConstants.POSTCONDITION_FEATURE_PREFIX.length(),
                                    fname.lastIndexOf("_")) +
              ".postcondition";
          }

        result = outer
              + (isRef() != feature().isRef() ? (isRef() ? "ref " : "value ") : "" )
              + fname;
        if (isThisType())
          {
            result = result + ".this";
          }
        if (typeType)
          {
            result = result + ".type";
          }
        var skip = typeType;
        for (var g : generics())
          {
            if (!skip) // skip first generic 'THIS#TYPE' for types of type features.
              {
                result = result + " " + g.asStringWrapped(humanReadable, context);
              }
            skip = false;
          }
      }
    return result;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return asString();
  }


  /**
   * wrap the result of toString in parentheses if necessary
   */
  public String toStringWrapped()
  {
    return StringHelpers.wrapInParentheses(toString());
  }


  /**
   * wrap the result of asString in parentheses if necessary
   */
  public String asStringWrapped(boolean humanReadable)
  {
    return StringHelpers.wrapInParentheses(asString(humanReadable));
  }


  /**
   * wrap the result of asString in parentheses if necessary
   * @param context the feature to which the path should be relative to, universe if null
   */
  public String asStringWrapped(boolean humanReadable, AbstractFeature context)
  {
    return StringHelpers.wrapInParentheses(asString(humanReadable, context));
  }


  /**
   * Check if constraints on type parameters of this type are satisfied.
   *
   * @return itself on success or t_ERROR if constraints are not met and an
   * error was produced
   *
   * @param context the source code context where this Type is used
   */
  public AbstractType checkConstraints(Context context)
  {
    var result = this;
    if (result != Types.t_ERROR && !isGenericArgument())
      {
        if (!checkActualTypePars(context, feature(), generics(), unresolvedGenerics(), null))
          {
            result = Types.t_ERROR;
          }
      }
    return result;
  }


  /**
   * Check that given actuals match formal type parameter constraints of given
   * feature.
   *
   * @param context the source code context where this Type is used
   *
   * @param called the feature that has formal type parameters
   *
   * @param actuals the actual type parameters
   *
   * @param unresolvedActuals when available, the list of unresolved actuals
   * such that source code positions can be shown.
   *
   * @param callPos in case this is a call un unresolvedActuals is empty since
   * the actual type parameters are inferred, this gives the position of the
   * call, null if not a call.
   *
   * @return true iff check was ok, false iff an error was found and reported
   */
  static boolean checkActualTypePars(Context context, AbstractFeature called, List<AbstractType> actuals, List<AbstractType> unresolvedActuals, SourcePosition callPos)
  {
    var result = true;
    var fi = called.generics().list.iterator();
    var ai = actuals.iterator();
    var ui = unresolvedActuals.iterator();
    while (fi.hasNext() &&
           ai.hasNext()    ) // NYI: handling of open generic arguments
      {
        var f = fi.next();
        var a = ai.next();
        var u = ui.hasNext() ? ui.next() : null;
        var c = f.constraint(Context.NONE);
        if (CHECKS) check
          (Errors.any() || f != null && a != null);

        var pos = u instanceof UnresolvedType ut ? ut.pos() :
                  callPos != null                ? callPos
                                                 : called.pos();

        a.checkLegalQualThisType(callPos, context);
        a.checkChoice(pos, context);
        if (!c.isGenericArgument() && // See AstErrors.constraintMustNotBeGenericArgument,
                                      // will be checked in SourceModule.checkTypes(Feature)
            !c.constraintAssignableFrom(context, a))
          {
            if (!f.typeParameter().isCoTypesThisType())  // NYI: CLEANUP: #706: remove special handling for 'THIS_TYPE'
              {
                // In case of choice, error will be shown
                // by SourceModule.checkTypes(): AstErrors.constraintMustNotBeChoice
                if (!c.isChoice())
                  {
                    AstErrors.incompatibleActualGeneric(pos, f, c, a);
                  }

                result = false;
              }
          }
      }
    return result;
  }


  /**
   * If the type is a this-type, check if it is legal.
   *
   * @param pos
   *
   * @param context
   */
  private void checkLegalQualThisType(SourcePosition pos, Context context)
  {
    if (isThisType())
      {
        var subject = feature();
        var found = false;
        AbstractFeature o = context.outerFeature();
        o = o.isCotype() ? o.cotypeOrigin() : o;
        while(o != null)
          {
            if (subject == o)
              {
                found = true;
                break;
              }
            o = o.outer();
          }
        if (!found)
          {
            AstErrors.illegalThisType(pos, this);
          }
      }
  }


  /**
   * Create a clone of this Type that uses originalOuterFeature as context to
   * look up features the type is built from.  Generics will be looked up in the
   * current context.
   *
   * This is used for type features that use types from the original feature,
   * but needs to replace generics by the type feature's generics.
   *
   * @param originalOuterFeature the original feature, which is not a type
   * feature.
   */
  AbstractType clone(AbstractFeature originalOuterFeature)
  {
    return this;
  }


  /**
   * @param v
   *
   * @return this type and any of its generics that have more restrictive visibility than `v`.
   */
  public Set<AbstractFeature> moreRestrictiveVisibility(Visi v)
  {
    if (PRECONDITIONS) require
      (!v.definesTypeVisibility());

    if (_usedFeatures == null)
      {
        _usedFeatures = new TreeSet<AbstractFeature>();
        usedFeatures(_usedFeatures);
      }

    return _usedFeatures
      .stream()
      .filter(af -> af.visibility().typeVisibility().ordinal() < v.ordinal())
      .collect(Collectors.toSet());
  }


  /**
   * Flatten this type.
   *
   * If this is a - possibly nested - choice return
   *   all choice generics
   *
   * else this returns a Stream of itself.
   */
  public Stream<AbstractType> choices(Context context)
  {
    return isChoice()
      ? choiceGenerics(context)
        .stream()
        .flatMap(cg -> cg.choices(context))
      : Stream.of(this);
  }


  /**
   * Return constraint if type is a generic, unmodified type otherwise
   * @param tt the type
   * @param context the context
   * @return constraint for generics, unmodified type otherwise
   */
  AbstractType selfOrConstraint(Context context)
  {
    return (isGenericArgument() ? genericArgument().constraint(context) : this);
  }


  /**
   * Return constraint if type is a generic, unmodified type otherwise
   * @param tt the type
   * @param res the resolution
   * @param context the context
   * @return constraint for generics, unmodified type otherwise
   */
  AbstractType selfOrConstraint(Resolution res, Context context)
  {
    return (isGenericArgument() ? genericArgument().constraint(res, context) : this);
  }


}

/* end of file */
