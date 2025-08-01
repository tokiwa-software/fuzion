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

import static dev.flang.util.FuzionConstants.NO_SELECT;

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
  private YesNo _dependsOnGenerics = YesNo.dontKnow;


  // flag to disable applyTypePar caching, for debugging only
  private static boolean typeParCachingEnabled = true;
  /**
   * Cached results for {@code applyTypePars(t)} and {@code applyTypePars(f, List<AbstractType>)};
   */
  private AbstractType _appliedTypeParsCachedFor1;
  private AbstractType _appliedTypeParsCache;
  private AbstractFeature _appliedTypePars2CachedFor1;
  private List<AbstractType> _appliedTypePars2CachedFor2;
  private AbstractType _appliedTypePars2Cache;


  /**
   * Cached result of calling usedFeatures(_usedFeatures).
   */
  private Set<AbstractFeature> _usedFeatures = null;



  /*---------------------------  static methods  ---------------------------*/




  /*--------------------------  abstract methods  --------------------------*/


  /**
   * The feature backing the type.
   */
  protected abstract AbstractFeature backingFeature();


  /**
   * For a normal type, this is the list of actual type parameters given to the type.
   *
   * Requires that this is resolved and !isGenericArgument().
   */
  public abstract List<AbstractType> generics();


  /**
   * The outer of this type. May be null.
   *
   * Requires that this is resolved and !isGenericArgument().
   */
  public abstract AbstractType outer();


  /**
   * The mode of the type: ThisType, RefType or ValueType.
   */
  public abstract TypeKind kind();



  /*-----------------------------  methods  -----------------------------*/


  /**
   * @return the generics of this type.
   * In case this is a this-type return the features generics.
   */
  public List<AbstractType> actualGenerics()
  {
    return isThisType()
      ? feature().generics().asActuals()
      : generics();
  }


  /**
   * `this` as a value.
   *
   * Requires that at isNormalType().
   */
  public AbstractType asValue()
  {
    if (PRECONDITIONS) require
      (isNormalType());

    throw new Error("asValue() not supported for "+getClass());
  }


  /**
   * This type as a reference.
   *
   * Requires that this is resolved, !isGenericArgument().
   */
  public AbstractType asRef()
  {
    return asRef(false);
  }


  /**
   * This type as a reference.
   *
   * @param allowForThisType allow this-types to be turned in to a ref-type
   *
   * Requires that this is resolved, !isGenericArgument().
   */
  public AbstractType asRef(boolean allowForThisType)
  {
    if (PRECONDITIONS) require
      (!(this instanceof UnresolvedType),
       !isGenericArgument(),
       allowForThisType || !isThisType());

    return switch (kind()) {
      case GenericArgument -> throw new Error("asValue not legal for genericArgument");
      case ThisType -> allowForThisType
        ? ResolvedNormalType.create(
            feature().generics().asActuals(),
            Call.NO_GENERICS,
            feature().outer().selfType().asThis(),
            feature(),
            TypeKind.RefType)
        : Types.t_ERROR;
      case RefType -> this;
      case ValueType ->
        ResolvedNormalType.create(generics(), generics(), outer(), feature(), TypeKind.RefType);
    };
  }


  /**
   * Return this type as a this-type, a type denoting the
   * instance of this type in the current context.
   *
   * Requires that this is resolved and !isGenericArgument().
   */
  public AbstractType asThis()
  {
    if (PRECONDITIONS) require
      (!(this instanceof UnresolvedType),
       !isGenericArgument());

    return switch (kind()) {
      case GenericArgument -> throw new Error("asThis not legal for genericArgument");
      case ThisType -> this;
      case RefType, ValueType ->
        feature().isUniverse()
          ? this
          : new ThisType(feature());
    };
  }


  /**
   * The sourcecode position of the declaration point of this type, or, for
   * unresolved types, the source code position of its use.
   */
  public SourcePosition declarationPos()
  {
    return backingFeature().pos();
  }


  /**
   * For a resolved normal type, return the underlying feature.
   *
   * Requires that this is resolved and !isGenericArgument().
   *
   * @return the underlying feature.
   */
  public AbstractFeature feature()
  {
    if (PRECONDITIONS) require
      (!(this instanceof UnresolvedType),
       !isGenericArgument());

    var result = backingFeature();

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * For a resolved parametric type return the generic.
   *
   * Requires that this is resolved and isGenericArgument().
   */
  public AbstractFeature genericArgument()
  {
    if (PRECONDITIONS) require
      (!(this instanceof UnresolvedType),
       isGenericArgument());

    var result = backingFeature();

    if (POSTCONDITIONS) ensure
      (result.isTypeParameter());

    return result;
  }



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
   * is this a formal generic argument that is open, i.e., the last argument in
   * a formal generic arguments list and followed by ... as A in
   * {@code Function<R,A...>}.
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
    return isGenericArgument() && genericArgument().isOpenTypeParameter();
  }


  /**
   * Check if this is a choice type.
   */
  public boolean isChoice()
  {
    return !isGenericArgument() && feature().isChoice();
  }


  /**
   * Is this type a generic argument (true) or false backed by a feature (false)?
   */
  public boolean isGenericArgument()
  {
    return kind() == TypeKind.GenericArgument;
  }


  /**
   * Is this a ref-type?
   */
  public boolean isRef()
  {
    return kind() == TypeKind.RefType;
  }


  /**
   * Is this a value-type?
   */
  public boolean isValue()
  {
    return kind() == TypeKind.ValueType;
  }

  /**
   * Is this a this-type?
   */
  public boolean isThisType()
  {
    return kind() == TypeKind.ThisType;
  }


  /**
   * For a resolved type, check if it is a choice type and if so, return the
   * list of choices.
   *
   * @param context the source code context where this Type is used
   */
  List<AbstractType> choiceGenerics(Context context)
  {
    if (PRECONDITIONS) require
      (this instanceof ResolvedType,
       isChoice());

    var g = feature().choiceGenerics();
           // NYI: UNDER DEVELOPMENT: a bit weird, choice this types.
    return isThisType() ? g : replaceGenerics(g)
      .map(t -> t.replace_this_type_by_actual_outer(this, context));
  }


  /**
   * For a resolved type, check if it is a choice type and if so, return the
   * list of choices.
   */
  public List<AbstractType> choiceGenerics()
  {
    return choiceGenerics(Context.NONE);
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
    else if (isNormalType())
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
   * Is the type a _normal_ type, i.e. value or ref-type with generics and outer?
   */
  public boolean isNormalType()
  {
    return switch (kind())
      {
      case RefType, ValueType -> true;
      default -> false;
      };
  }


  /**
   * Check if this or any of its generic arguments is {@code Types.t_UNDEFINED}.
   *
   * @param exceptFirst if true, the first generic argument may be
   * {@code Types.t_UNDEFINED}.  This is used in a lambda {@code x -> f x} of type
   * {@code Function<R,X>} when {@code R} is unknown and to be inferred.
   */
  public boolean containsUndefined(boolean exceptFirst)
  {
    boolean result = false;
    if (this == Types.t_UNDEFINED)
      {
        result = true;
      }
    else if (isNormalType())
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
   * Is actual assignable to this?
   *
   * @param actual the actual type.
   */
  public YesNo isAssignableFrom(AbstractType actual)
  {
    return isAssignableFrom(actual, Context.NONE, true, true, null);
  }


  /**
   * Is actual assignable to this?
   *
   * @param actual the actual type.
   */
  YesNo isAssignableFrom(AbstractType actual, Context context)
  {
    return isAssignableFrom(actual, context, true, true, null);
  }


  /**
   * Is actual assignable to this without the need for tagging?
   *
   * @param actual the actual type.
   */
  YesNo isAssignableFromWithoutTagging(AbstractType actual, Context context)
  {
    return isAssignableFrom(actual, context, true, false, null);
  }


  /**
   * Check if a value of static type actual can be assigned to a field of static
   * type this.  This performs static type checking, i.e., the types may still
   * be or depend on generic parameters.
   *
   * @param actual the actual type.
   */
  public YesNo isAssignableFromWithoutBoxing(AbstractType actual)
  {
    return isAssignableFrom(actual, Context.NONE, false, true, null);
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
  YesNo isAssignableFromWithoutBoxing(AbstractType actual, Context context)
  {
    return isAssignableFrom(actual, context, false, true, null);
  }


  /**
   * Is actual assignable to this without the need for tagging?
   *
   * @param actual the actual type.
   */
  public YesNo isAssignableFromWithoutTagging(AbstractType actual)
  {
    return isAssignableFromWithoutTagging(actual, Context.NONE);
  }


  /**
   * Is actual assignable to this without the need for tagging/boxing?
   *
   * @param actual the actual type.
   */
  public YesNo isAssignableFromDirectly(AbstractType actual)
  {
    return isAssignableFrom(actual, Context.NONE, false, false, null);
  }


  /**
   * Check if a value of static type actual can be assigned to a field of static
   * type this.  This performs static type checking, i.e., the types may still
   * be or depend on generic parameters.
   *
   * @param actual the actual type.
   *
   * @param context the source code context where this Type is used
   *
   * @param assignableTo in case we want to show all types actual is assignable
   * to in an error message, this collects the types converted to strings.
   *
   * @return
   *  - yes      if assignable
   *  - no       if not assignable
   *  - dontKnow if contains error
   */
  YesNo isAssignableFrom(AbstractType actual, Context context, boolean allowBoxing, boolean allowTagging, Set<String> assignableTo)
  {
    if (PRECONDITIONS) require
      (this  .isGenericArgument() || this  .feature() != null || Errors.any(),
       actual.isGenericArgument() || actual.feature() != null || Errors.any());

    if (assignableTo != null)
      {
        assignableTo.add(actual.toString(true));
      }
    var target_type = this  .remove_type_parameter_used_for_this_type_in_cotype();
    var actual_type = actual.remove_type_parameter_used_for_this_type_in_cotype();
    var result = AbstractType.isArtificialType(this) || AbstractType.isArtificialType(actual)
        ? YesNo.dontKnow
        : YesNo.fromBool(target_type.compareTo(actual_type) == 0 || actual_type.isVoid());
    if (result.no() && !target_type.isGenericArgument() && isRef() && actual_type.isRef())
      {
        if (actual_type.isGenericArgument())
          {
            result = isAssignableFrom(actual_type.genericArgument().constraint(context).asRef(true), context, allowBoxing, allowTagging, assignableTo);
          }
        else
          {
            for (var p: actual_type.feature().inherits())
              {
                var pt = actual_type.actualType(p.type(), context).asRef(true);
                result = isAssignableFrom(pt, context, allowBoxing, allowTagging, assignableTo);
                // until result != no
                if (!result.no()) { break; }
              }
          }
      }
    if (result.no() && allowTagging && target_type.isChoice() && !isThisTypeInCotype())
      {
        result = YesNo.fromBool(target_type.isChoiceMatch(actual_type, context));
      }
    // NYI: UNDER DEVELOPMENT: probably unsound! see also isAssignableFrom
    if (result.no() && isChoice() && actual.isChoice() && (isThisType() || actual.isThisType()))
      {
        result = YesNo.fromBool(asThis().compareTo(actual.asThis()) == 0);
      }
    if (result.no() && allowBoxing)
      {
        if (actual.isGenericArgument())
          {
            result = isAssignableFrom(actual.genericArgument().constraint(context).asRef(true), context, allowBoxing, allowTagging, assignableTo);
          }
        else if (!actual.isRef())
          {
            result = isAssignableFrom(actual.asRef(true), context, false, allowTagging, assignableTo);
          }
      }
    return result;
  }


  /**
   * check if t is an artificial type like t_ERROR
   *
   * @param t
   * @return
   */
  private static boolean isArtificialType(AbstractType t)
  {
    return t instanceof ArtificialBuiltInType;
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
      (isChoice());

    boolean result = false;
    for (var t : choiceGenerics(context))
      {
        if (CHECKS) check
          (Errors.any() || t != null);
        result = result || t != null && t.isAssignableFromWithoutBoxing(actual, context).yes();
      }
    return result;
  }


  /**
   * Check if a type parameter actual can be assigned to a type parameter with
   * constraint this.
   *
   * @param context the source code context where this Type is used
   *
   * @param call if this is a formal type in a call, this is the call, otherwise
   * null. This call is used to replace type parameters that depend on the
   * call's target or actual type parameters. Also this is used to for error
   * messages that require the source position of the call.
   *
   * @param actual the actual type.
   */
  boolean constraintAssignableFrom(Context context, Call call, AbstractType actual)
  {
    if (PRECONDITIONS) require
      (this  .isGenericArgument() || this  .feature() != null || Errors.any(),
       actual.isGenericArgument() || actual.feature() != null || Errors.any(),
       Errors.any() || this != Types.t_ERROR && actual != Types.t_ERROR);

    var result = containsError()                   ||
      actual.containsError()                       ||
      this  .compareTo(actual               ) == 0 ||
      this  .compareTo(Types.resolved.t_Any ) == 0;

    if (!result && !isGenericArgument())
      {
        if (actual.isGenericArgument())
          {
            result = constraintAssignableFrom(context, call, actual.genericArgument().constraint(context));
          }
        else
          {
            if (CHECKS) check
              (actual.feature() != null || Errors.any());
            if (actual.feature() != null)
              {
                result = actual.feature() == feature() && (actual.isThisType() ||
                  genericsAssignable(actual, context, call)); // NYI: Check: What about open generics?
                for (var p: actual.feature().inherits())
                  {
                    result |= !p.calledFeature().isChoice() &&
                      constraintAssignableFrom(context, call, p.type().applyTypePars(actual));
                  }
              }
          }
      }
    return result;
  }


  /**
   * Check if a type parameter actual can be assigned to a type parameter with
   * constraint this.
   *
   * @param actual the actual type.
   */
  public boolean constraintAssignableFrom(AbstractType actual)
  {
    return constraintAssignableFrom(Context.NONE, null, actual);
  }


  /**
   * Check if generics of type parameter {@code actual} are assignable to
   * generics of type parameter with constraint {@code this}.
   *
   * @param actual the actual type to be checked
   *
   * @param context the source code context where this Type is used
   *
   * @param call if this is a formal type in a call, this is the call, otherwise
   * null. This call is used to replace type parameters that depend on the
   * call's target or actual type parameters. Also this is used to for error
   * messages that require the source position of the call.
   */
  private boolean genericsAssignable(AbstractType actual, Context context, Call call)
  {
    if (PRECONDITIONS) require
      (!this.isGenericArgument(),
       !actual.isGenericArgument());

    var ogs = actual.generics();
    var i1 = actualGenerics().iterator();
    var i2 = ogs.iterator();
    AbstractType go = null;
    while ((go != null || i1.hasNext()) && i2.hasNext())
      {
        var g = go != null ? go : i1.next();
        go = g.isOpenGeneric() ? g : null;
        var og = i2.next();
        if (call != null)
          {
            var tt = call.targetType(context);
            if (CHECKS) check
              (tt != null);

            if (g.isOpenGeneric())
              {
                // this happens only for inherits-calls of cotypes if the
                // original type has open type parameters.  We just ignore
                // these for now since these constructors of cotypes are
                // never executed anyway.
                //
                // Examples are base.fum features such as `Unary` and
                // `Binary`.
                //
                // NYI: CLEANUP: Would be nicer to have the cotype inherits
                // calls use the correct actual type parameters such that
                // this does not happen.
                if (CHECKS) check
                  (call != null && call.calledFeature().isCotype());
              }
            else // adjust type depending on all target, required to fix #5001:
              {
                // NYI: CLEANUP: This code is part of what is done in Call.adjustResultType, see comment there.
                g = tt.actualType(g, context);
                g = g.applyTypePars(call._calledFeature, call._generics);
              }
          }
        var gt = g.selfOrConstraint(context);

        if (
          // NYI: BUG: #5002: check recursive type, e.g.:
          // this  = monad monad.A monad.MA
          // other = monad option.T (option option.T)
          // for now just prevent infinite recursion
            gt.compareTo(this) != 0 &&

            !gt.constraintAssignableFrom(context, call, og))
          {
            return false;
          }
      }
    return !i1.hasNext() && !i2.hasNext();
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
        result = genericsToReplace.flatMap
          (t -> t.isOpenGeneric() && t.genericArgument().outer().generics() == f.generics()
                ? t.genericArgument().replaceOpen(actualGenerics)
                : new List<>(t.applyTypePars(f, actualGenerics)));
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
      (isNormalType(),
       Errors.any() ||
       feature().generics().sizeMatches(generics()));

    return applyTypePars(feature(), genericsToReplace, generics());
  }


  /**
   * Does this type depend on generics?
   * The outer of this type may still depend on generics
   */
  public boolean dependsOnGenericsNoOuter()
  {
    boolean result = false;
    if (isGenericArgument())
      {
        result = true;
      }
    else
      {
        for (var t: generics())
          {
            if (CHECKS) check
              (Errors.any() || t != null);
            if (t != null &&
                t.dependsOnGenerics())
              {
                result = true;
              }
          }
      }
    return result;
  }


  /**
   * Does this type (or its outer type) depend on generics. If not, applyTypePars()
   * will not need to do anything on this.
   */
  public boolean dependsOnGenerics()
  {
    YesNo result = _dependsOnGenerics;
    if (result == YesNo.dontKnow)
      {
        if (isGenericArgument())
          {
            result = YesNo.yes;
          }
        else if (isThisType())
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
      (target != null,
       Errors.any() || !isOpenGeneric(),
       Errors.any() || target.isGenericArgument() || target.isThisType() || target.feature().generics().sizeMatches(target.generics()));

    AbstractType result;
    if (typeParCachingEnabled && _appliedTypeParsCachedFor1 == target)
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
        result = result.applyTypePars(target.feature(), target.actualGenerics());
        if (target.isThisType())
          {
            // see #659 for when this is relevant
            result = result.applyTypePars(target.feature().outer().thisType());
          }
        else
          {
            if (target.outer() != null)
              {
                result = result.applyTypePars(target.outer());
              }
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
      (Errors.any() ||
       f.generics().sizeMatches(actualGenerics),
       Errors.any() || !isOpenGeneric() || genericArgument().outer().generics() != f.generics());

    AbstractType result;
    if (typeParCachingEnabled &&
        _appliedTypePars2CachedFor1 == f &&
        _appliedTypePars2CachedFor2 == actualGenerics)
      {
        result = _appliedTypePars2Cache;
      }
    else if (actualGenerics.contains(Types.t_UNDEFINED))
      {
        result = applyTypePars_(f, actualGenerics);
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
   * Is this the type of a type feature, e.g., the type of {@code (list
   * i32).type}. Will return false for an instance of Type for which this is
   * still unknown since {@code Type.resolve()} was not called yet.
   */
  boolean isCotypeType()
  {
    return !isGenericArgument() && feature().isCotype();
  }


  /**
   * A cotype has the actual underlying type as its first type parameter
   * {@code THIS_TYPE} in addition to the type parameters of the original type.
   *
   * In case this is a cotype, determine the actual types for generics
   * by apply the actual type parameters passed to {@code THIS_TYPE}.
   *
   * @return the actual generics after {@code THIS_TYPE.actualType} was applied.
   */
  public List<AbstractType> cotypeActualGenerics()
  {
    return cotypeActualGenerics(generics());
  }


  /**
   * A cotype has the actual underlying type as its first type parameter
   * {@code THIS_TYPE} in addition to the type parameters of the original type.
   *
   * In case this is a cotype, determine the actual types for the types in {@code g}
   * by apply the actual type parameters passed to {@code THIS_TYPE}.
   *
   * @param g list of generics, must be derived from {@code generics()}
   *
   * @return the actual generics after {@code THIS_TYPE.actualType} was applied.
   */
  private List<AbstractType> cotypeActualGenerics(List<AbstractType> g)
  {
    /* types of type features require special handling since the type
     * feature has one additional first type parameter --the underlying
     * type: this_type--, and all other type parameters need to be converted
     * to the actual type relative to that.
     */
    if (isCotypeType())
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
        result = result
          .applyTypePars(i.calledFeature(),
                         i.actualTypeParameters());
      }
    return result
      .applyTypeParsLocally(f, actualGenerics, NO_SELECT);
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
  public AbstractType applyTypeParsLocally(AbstractFeature f,
                                           List<AbstractType> actualGenerics,
                                           int select)
  {
    return applyTypeParsLocally(f, actualGenerics, select, null);
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
   * @param forOuter in case we replace an outer type that is a type parameter
   * as in `T.i`, forOuter gives the original outer feature  of `i` such that `T`
   * can be replaced with the corresponding actual type that inherits from that
   * outer type. `null` in case we are not handling an outer type.
   *
   * @return t iff t does not depend on a formal generic parameter of this,
   * otherwise the type that results by replacing all formal generic parameters
   * of this in t by the corresponding type from actualGenerics.
   */
  private AbstractType applyTypeParsLocally(AbstractFeature f,
                                            List<AbstractType> actualGenerics,
                                            int select,
                                            AbstractFeature forOuter)
  {
    if (PRECONDITIONS) require
      (f != null,
       actualGenerics != null);

    return switch(kind())
      {
        case GenericArgument ->
          {
            var result = this;
            AbstractFeature g = result.genericArgument();
            if (g.outer().generics() != f.generics())  // if g is not formal generic of f, and g is a type feature generic, try g's origin:
              {
                g = g.cotypeOriginGeneric();
              }
            if (g.outer().generics() == f.generics()) // if g is a formal generic defined by f, then replace it by the actual generic:
              {
                if (g.isOpenTypeParameter())
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
                while (forOuter != null && !result.isGenericArgument() && !result.feature().inheritsFrom(forOuter))
                  {
                    result = result.outer();
                    if (CHECKS) check
                      (Errors.any() || result != null);
                    if (result == null)
                      {
                        result = Types.t_ERROR;
                      }
                  }
              }
            yield result;
          }
        case RefType, ValueType ->
          {
            var result = this;
            var generics = result.generics();
            var g2 = generics instanceof FormalGenerics.AsActuals aa && aa.actualsOf(f)
              ? actualGenerics
              : generics.map(t -> t.applyTypeParsLocally(f, actualGenerics, FuzionConstants.NO_SELECT));
            var ro = result.outer();
            var o2 = ro != null ? ro.applyTypeParsLocally(f, actualGenerics, select, feature().outer())
                                : null;

            g2 = cotypeActualGenerics(g2);

            if (g2 != result.generics() ||
                o2 != result.outer()       )
              {
                var hasError = o2 == Types.t_ERROR;
                for (var t : g2)
                  {
                    hasError = hasError || (t == Types.t_ERROR);
                  }
                result = hasError ? Types.t_ERROR : result.replaceGenericsAndOuter(g2, o2);
              }
            yield result;
          }
        case ThisType -> this;
      };
  }


  /**
   * For a type that is not a type parameter, create a new variant using given
   * actual generics and outer type.
   *
   * @param g the new actual generics to be used
   *
   * @param o the new outer type to be used (which may also differ in its
   * actual generics).
   *
   * @return a new type with same feature(), but using g/o as generics
   * and outer type.
   */
  public AbstractType replaceGenericsAndOuter(List<AbstractType> g, AbstractType o)
  {
    if (PRECONDITIONS) require
      (isNormalType(),
       this instanceof ResolvedType,
       feature().generics().sizeMatches(g));

    throw new Error("replaceGenericsAndOuter not supported for "+getClass());
  }


  /**
   * Use this type as a target type for a call using type {@code t} and determine the
   * actual type corresponding to {@code t}. For this, type parameters used in {@code t} are
   * replaced by the actual type parameters in {@code this} and {@code this.type} within {@code t}
   * will be replaced by the actual type in {@code this}.
   *
   * @param t a type, must not be an open generic.
   *
   * @param context the source code context where this Type is used
   *
   * @return t with type parameters replaced by the corresponding actual type
   * parameters in {@code this} and {@code this.type}s replaced by the corresponding actual
   * type in {@code this}.
   */
  AbstractType actualType(AbstractType t, Context context)
  {
    if (PRECONDITIONS) require
      (!isGenericArgument(),
       !t.isOpenGeneric());

    return t.applyTypePars(this)
      .replace_this_type_by_actual_outer(this, context);
  }


  /**
   * Use this type as a target type for a call using type {@code t} and determine the
   * actual type corresponding to {@code t}. For this, type parameters used in {@code t} are
   * replaced by the actual type parameters in {@code this} and {@code this.type} within {@code t}
   * will be replaced by the actual type in {@code this}.
   *
   * @param t a type, must not be an open generic.
   *
   * @return t with type parameters replaced by the corresponding actual type
   * parameters in {@code this} and {@code this.type}s replaced by the corresponding actual
   * type in {@code this}.
   */
  public AbstractType actualType(AbstractType t)
  {
    return actualType(t, Context.NONE);
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
  void checkChoice(SourcePosition pos, Context context)
  {
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
                      }
                  }
                i2++;
              }
            i1++;
          }
      }
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
      ||    this .isAssignableFrom(other, context, false, false, null).no()
         && other.isAssignableFrom(this , context, false, false, null).no();
  }


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outerfeat the feature surrounding this expression.
   */
  public AbstractType visit(FeatureVisitor v, AbstractFeature outerfeat)
  {
    return v.action(this);
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
   * isFunctionTypeExcludingLazy checks if this is a function type used for lambda expressions,
   * e.g., "(i32, i32) -> String".
   *
   * @return true iff this is a function type but not a {@code Lazy}.
   */
  public boolean isFunctionTypeExcludingLazy()
  {
    return
      this != Types.t_ERROR &&
      isFunctionType() &&
      feature() != Types.resolved.f_Lazy;
  }


  /**
   * Check if this any function type, i.e., inherits directly or indirectly from
   * {@code Function}.
   *
   * @return true if this is a type based on a feature that is or inherits from {@code Function}.
   */
  public boolean isFunctionType()
  {
    return
      !isGenericArgument() &&
      (feature() == Types.resolved.f_Function ||
       feature().inherits().stream().anyMatch(c -> c.calledFeature().selfType().isFunctionType()));
  }


  /**
   * If this is a choice type, extract function type that might be one of the
   * choices.
   *
   * @param context the source code context where this Type is used
   *
   * @return if this is a choice and there is exactly one choice for which
   * isFunctionTypeExcludingLazy() holds, return that type, otherwise return this.
   */
  AbstractType functionTypeFromChoice(Context context)
  {
    return findInChoice(cg -> cg.isFunctionType(), context);
  }


  /**
   * For a function type (see isFunctionType()), return the arity of the
   * function.
   *
   * @return the number of arguments to be passed to this function type.
   */
  int arity()
  {
    if (PRECONDITIONS) require
      (isFunctionType());

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
   * Recursive helper for {@code arity} to determine the arity by inspecting the
   * parents of {@code f}.
   *
   * @param f a feature
   *
   * @return the arity in case f inherits from {@code Function}, -1 otherwise.
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
      this.isAssignableFrom(that, context).yes()   ? this :
      that.isAssignableFrom(this, context).yes()   ? that : Types.t_ERROR;

    if (POSTCONDITIONS) ensure
      (result == Types.t_ERROR     ||
       this.isVoid() && result == that ||
       that.isVoid() && result == this ||
       result.isAssignableFrom(this, context).yes() &&
       result.isAssignableFrom(that, context).yes());

    return result;
  }


  /**
   * Is this the type denoting {@code void}?
   */
  public boolean isVoid()
  {
    return Types.resolved != null && compareTo(Types.resolved.t_void) == 0;
  }


  /**
   * Compare this to other for creating unique types.
   */
  public int compareTo(AbstractType other)
  {
    if (PRECONDITIONS) require
      (other != null,
       (this  instanceof ResolvedType),
       (other instanceof ResolvedType));

    int result = compareToIgnoreOuter(other);
    if (result == 0 && isNormalType())
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
      (other != null,
       (this  instanceof ResolvedType),
       (other instanceof ResolvedType));

    int result = 0;

    if (this != other)
      {
        result =
          isGenericArgument() &&  other.isGenericArgument() ?  0 :
          isGenericArgument() && !other.isGenericArgument() ? -1 :
          !isGenericArgument() && other.isGenericArgument() ? +1 : feature().compareTo(other.feature());
        if (result == 0 && isNormalType() && other.isNormalType())
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

    if (POSTCONDITIONS) ensure
      (result != 0 || kind() == other.kind());

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
   * This must be called on a call result type to replace {@code this.type} used in
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
   *   say (type_of a.l)    # should print {@code list a}
   *   say (type_of b.l)    # should print {@code list b}
   *
   * @param tt the type feature we are calling ({@code list a.this.type} in the example)
   * above).
   *
   * @param foundRef a consumer that will be called for all the this-types found
   * together with the ref type they are replaced with.  May be null.  This will
   * be used to check for AstErrors.illegalOuterRefTypeInCall.
   *
   * @param context the source code context where this Type is used
   *
   * @return the actual type, i.e.{@code list a} or {@code list b} in the example above.
   */
  AbstractType replace_this_type_by_actual_outer(AbstractType tt,
                                                 BiConsumer<AbstractType, AbstractType> foundRef,
                                                 Context context)
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
   * Convenience version of replace_this_type_by_actual_outer with {@code null} as
   * argument to {@code foundRef}.
   *
   * @param tt the type feature we are calling ({@code equatable.type} in the example)
   * above).
   *
   * @param context the source code context where this Type is used
   */
  AbstractType replace_this_type_by_actual_outer(AbstractType tt, Context context)
  {
    return replace_this_type_by_actual_outer(tt, null, context);
  }


  /**
   * For checking if a type constraint or any of the outer types of the
   * constraint corresponds to a `this` type.
   *
   * This is used in a call `E.x` were `E` has a constraint `E : a.b` and `x`
   * has a result type `a.this.p` or `a.b.this.q` to replace the `this` type by
   * the constraint to bet `E.p` (alternatively `E.outer(1).p`,
   * see @replace_this_type_by_actual_outer2) or `E.q`, respectively.
   *
   * @param f the feature this might be inheriting from (or from f.outer()...).
   *
   * @return the outer level that inherits from f, i.e.,
   *         <ul>
   *           <li>-1 if no inheritance from `f` was found,</li>
   *           <li> 0 if this type's feature inherits from f,</li>
   *           <li> 1 if the next outer feature inherits from `f , etc.</li>
   *         </ul>
   */
  int whichOuterInheritsFrom(AbstractFeature f)
  {
    if (PRECONDITIONS) require
      (!isGenericArgument());

    var result = 0;
    var tf = feature();
    while (tf != null && !tf.inheritsFrom(f))
      {
        tf = tf.outer();
        result++;
      }

    return tf != null ? result : -1;
  }


  /**
   * Helper for replace_this_type_by_actual_outer to replace {@code this.type} for
   * exactly tt, ignoring tt.outer().
   *
   * @param tt the type feature we are calling
   *
   * @param foundRef a consumer that will be called for all the this-types found
   * together with the ref type they are replaced with.  May be null.
   *
   * @param context the source code context where this Type is used
   */
  AbstractType replace_this_type_by_actual_outer2(AbstractType tt, BiConsumer<AbstractType, AbstractType> foundRef, Context context)
  {
    AbstractType result;
    if (replacesThisType(tt, context))
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
   * Is this a `.this` type that should be replaced by `tt`?
   *
   * @param tt the type feature we are calling
   *
   * @param context the source code context where this Type is used
   */
  private boolean replacesThisType(AbstractType tt, Context context)
  {
    return
      isThisTypeInCotype() && tt.isGenericArgument()   // we have a type parameter TT.THIS#TYPE, which is equal to TT
      ||
      isThisType() && (!tt.isGenericArgument() && tt.feature().inheritsFrom(feature())  // we have abc.this.type with tt inheriting from abc, so use tt
                       ||
                       // we have a,b,c.this.type and tt is type parameter with constraing x.y.z: So replace it if
                       // any of `a.b.c`, `a.b`, or `a` inherits from this. During monomorphization, when the type
                       // parameter will be replaced, we will find that actual outer type that fits here.
                       //
                       // NYI: CLEANUP: instead of returning `tt` here, we might create a new type that refers to the n`th outer type
                       // of the actual type parameter, i.e., `Outer(1,tt)` in case `a.b` inherits from this, and `Outer(2,tt)` and in
                       // case `a` inherits from this.
                       tt.isGenericArgument() && tt.genericArgument().constraint(context).whichOuterInheritsFrom(feature()) >= 0
                       );
  }


  /**
   * Helper for replace_this_type_by_actual_outer to replace {@code this.type} for
   * exactly tt, ignoring tt.outer().
   *
   * @param tt the type feature we are calling
   *
   * @param foundRef a consumer that will be called for all the this-types found
   * together with the ref type they are replaced with.  May be null.
   */
  public AbstractType replace_this_type_by_actual_outer_locally(AbstractType tt,
                                                        BiConsumer<AbstractType, AbstractType> foundRef)
  {
    return replace_this_type_by_actual_outer_locally(tt, foundRef, Context.NONE);
  }


  /**
   * Helper for replace_this_type_by_actual_outer to replace {@code this.type} for
   * exactly tt, ignoring tt.outer().
   *
   * @param tt the type feature we are calling
   *
   * @param foundRef a consumer that will be called for all the this-types found
   * together with the ref type they are replaced with.  May be null.
   */
  private AbstractType replace_this_type_by_actual_outer_locally(AbstractType tt,
                                                         BiConsumer<AbstractType, AbstractType> foundRef,
                                                         Context context)
  {
    var result = this;
    var att = tt.selfOrConstraint(context);
    if (isThisTypeInCotype() && tt.isGenericArgument()   // we have a type parameter TT.THIS#TYPE, which is equal to TT
        ||
        isThisType() && att.feature() == feature()  // we have abc.this.type with att == abc, so use tt
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
        result = applyToGenericsAndOuter(g -> g.replace_this_type_by_actual_outer_locally(tt, foundRef, context));
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
   * when called on the result type of {@code f.type.x} argument {@code f.type}, this will
   * result in {@code option f}.
   *
   * @param cotype the type feature whose this.type we are replacing
   */
  public AbstractType replace_this_type_in_cotype(AbstractFeature cotype)
  {
    return isThisTypeInCotype() && cotype  == genericArgument().outer()
      ? cotype.cotypeOrigin().selfTypeInCoType()
      : applyToGenericsAndOuter(g -> g.replace_this_type_in_cotype(cotype));
  }


  /**
   * Check this and, recursively, all types contained in this' type parameters
   * and outer types if {@code this.isThisType && this.feature() == parent} is
   * true.  Replace all matches by the {@code heir.thisType()}.
   *
   * As an examples, in the code
   *
   *   f is
   *     x option f.this.type is ...
   *
   *   g : f is
   *     redef x option g.this.type is ...
   *
   * the result type of the inherited {@code f.x} is converted from {@code f.this.type} to
   * {@code g.this.type?} when checking types for the redefinition {@code g.x}.
   *
   * @param parent the parent feature we are inheriting {@code this} type from.
   *
   * @param heir the redefining feature
   *
   * @param foundRef a consumer that will be called for all the this-types found
   * together with the ref type they are replaced with.  May be null.  This will
   * be used to check for AstErrors.illegalOuterRefTypeInCall.
   */
  public AbstractType replace_this_type(AbstractFeature parent, AbstractFeature heir, BiConsumer<AbstractType, AbstractType> foundRef)
  {
    if (PRECONDITIONS) require
      (parent == Types.f_ERROR || heir == Types.f_ERROR || heir.inheritsFrom(parent));

    if (isThisType() && feature() == parent)
      {
        var tt = heir.thisType();
        if (foundRef != null && tt.feature().isRef())
          {
            foundRef.accept(this, tt);
          }
        return tt;
      }
    else
      {
        return applyToGenericsAndOuter(g -> g.replace_this_type(parent, heir, foundRef));
      }
  }


  /**
   * replace {@code x.this} types along inheritance chain from {@code declF} to {@code heir}.
   *
   * find outer that inherits this clazz, e.g.
   *
   *   x.me x.this => ...
   *   y : x is
   *     _ := y.me
   *
   * a the call {@code y.me}, type {@code x.this} is declared in {@code x} ({@code declF}) and used in
   * {@code y} ({@code heir}), the type will be replaced by {@code y.this}.
   *
   * @param declF the parent feature that contains the inherited feature where
   * this type is used
   *
   * @param heir the child feature that inherits from {@code declF} and uses this type
   * in the new context
   *
   * @param foundRef a consumer that will be called for all the this-types found
   * together with the ref type they are replaced with.  May be null.  This will
   * be used to check for AstErrors.illegalOuterRefTypeInCall.
   *
   * @return the inherited type.
   */
  public AbstractType replace_inherited_this_type(AbstractFeature declF, AbstractFeature heir, BiConsumer<AbstractType, AbstractType> foundRef)
  {
    if (PRECONDITIONS) require
      (declF == Types.f_ERROR || heir ==Types.f_ERROR || heir.inheritsFrom(declF));

    var t = this;
    var inh = heir.tryFindInheritanceChain(declF);
    if (CHECKS) check
      (Errors.any() || inh != null);
    if (inh != null)
      {
        for (AbstractCall c : inh)
          {
            var parent = c.calledFeature();
            t = t.replace_this_type(parent, heir, foundRef);
          }
      }
    return t;
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
       res != null || feature().state().atLeast(State.RESOLVED),
       !feature().isCotype());

    AbstractType result = null;
    var fot = feature();
    if (fot.isUniverse() || this == Types.t_ERROR || fot.isCotype())
      {
        result = this;
      }
    else
      {
        var g = new List<AbstractType>(
            // THIS#TYPE, the _payload_ of this typetype
            this,
            // all other generics
            actualGenerics()
          );

        var tf = res != null ? res.cotype(fot) : fot.cotype();
        if (CHECKS) check
          (tf != null);
        result = ResolvedNormalType.create(g, Types.resolved.universe.selfType(), tf);
      }
    return result;
  }


  /**
   * This should be called on a formal argument in call with given {@code target}.  If
   * {@code target} is a type parameter and the formal argument type {@code this} depends on
   * a type features {@code this.type}, then replace {@code this.type} by the type parameter
   * {@code target}.
   *
   * example:
   *
   *   equatable is
   *
   *     type.equality(a, b equatable.this.type) bool is abstract
   *
   *   equals(T type : equatable, x, y T) => T.equality x y
   *
   * For the call {@code T.equality x y} this will be called on the formal argument
   * type for {@code a} (and {@code b}).
   *
   * The type of the formal arguments {@code a} and {@code b} is {@code equatable.this.type},
   * which was replaced by the implicit first generic argument of
   * {@code equatable.type}.  This method will replaced it by {@code T} in the call
   * {@code T.equality x y}, such that actual arguments of the same type are
   * assignment compatible to it.
   *
   * @param tf the type feature we are calling ({@code equatable.type} in the example
   * above).
   *
   * @param tpt the type parameters type that is the target of the call ({@code T} in the example above).
   */
  AbstractType replace_type_parameter_used_for_this_type_in_cotype(AbstractFeature tf, AbstractType tpt)
  {
    if (PRECONDITIONS) require
      (tpt.isGenericArgument());

    var result = this;
    if (isGenericArgument())
      {
        if (genericArgument() == tf.arguments().get(0))
          { // a call of the form `T.f x` where `f` is declared as
            // `abc.type.f(arg abc.this.type)`, so replace
            // `abc.this.type` by `T`.
            result = tpt;
          }
      }
    else
      {
        result = applyToGenericsAndOuter(g -> g.replace_type_parameter_used_for_this_type_in_cotype(tf, tpt));
      }
    return result;
  }


  /**
   * Replace implicit generic type used for {@code abc.this.type} in {@code abc.type} by
   * {@code abc.this.type}.
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
   * here, when passing {@code b} to {@code plus}, {@code b} is of type {@code num.this.type}, which was
   * replaced by the implicit first generic argument of {@code num.type}, but it needs
   * to be changed back to {@code num.this.type}.
   */
  AbstractType remove_type_parameter_used_for_this_type_in_cotype()
  {
    var result = this;
    if (isGenericArgument())
      {
        var tp = genericArgument();
        var tf = tp.outer();
        if (tf.isCotype() && tp == tf.arguments().get(0))
          { // generic used for `abc.this.type` in `abc.type` by `abc.this.type`.
            result = result.isRef()
              ? tf.cotypeOrigin().selfType().asThis().asRef()
              : tf.cotypeOrigin().selfType().asThis();
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
        if (genericArgument().outer() == outerCotype.cotypeOrigin())
          {
            result = outerCotype.typeArguments().get(genericArgument().typeParameterIndex() + 1).asGenericType();
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
    if (isNormalType())
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
   * For a feature {@code f(A, B type)} the corresponding type feature has an implicit
   * THIS#TYPE type parameter: {@code f.type(THIS#TYPE, A, B type)}.
   *
   + This checks if this type is this implicit type parameter.
   */
  public boolean isThisTypeInCotype()
  {
    return isGenericArgument()
      && genericArgument().state().atLeast(State.FINDING_DECLARATIONS)
      && genericArgument().outer().isCotype()
      && genericArgument().typeParameterIndex() == 0;
  }


  /**
   * Check if for this or any type parameters of this, isThisType is true.  This
   * must not be the case for any clazzes in FUIR since clazzes require concrete
   * types.
   *
   * @return true if an {@code this.type} where found
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
  List<AbstractType> unresolvedGenerics() { return generics(); }


  /**
   * Get a String representation of this Type.
   *
   * Note that this does not work for instances of Type before they were
   * resolved.  Use toString() for creating strings early in the front end
   * phase.
   */
  public String toString(boolean humanReadable) { return toString(humanReadable, null); }


  /**
   * Get a String representation of this Type.
   *
   * Note that this does not work for instances of Type before they were
   * resolved.  Use toString() for creating strings early in the front end
   * phase.
   *
   * @param context the feature to which the name should be relative to
   */
  public String toString(boolean humanReadable, AbstractFeature context)
  {
    String result;

    if (isGenericArgument())
      {
        var ga = genericArgument();
        result = ga.qualifiedName(context) + (isRef() ? " (boxed)" : "");
      }
    else
      {
        var f = feature();
        var typeType = f.isCotype();
        if (typeType)
          {
            f = f.cotypeOrigin();
          }
        var fn = f.featureName();
        // for a feature that does not define a type itself, the name is not
        // unique due to overloading with different argument counts. So we add
        // the argument count to get a unique name.
        var fname = (humanReadable ? fn.baseNameHuman() : fn.baseName())
          +  (f.definesType() || fn.argCount() == 0 || fn.isInternal() || humanReadable
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

        result = outerToString(humanReadable)
              + (isNormalType() && isRef() != feature().isRef() ? (isRef() ? "ref " : "value ") : "" )
              + fname;
        if (isThisType())
          {
            result = result + ".this";
          }
        if (typeType)
          {
            result = result + ".type";
          }
        if (isNormalType())
          {
            var skip = typeType;
            for (var g : generics())
              {
                if (!skip) // skip first generic 'THIS#TYPE' for types of type features.
                  {
                    result = result + " " + g.toStringWrapped(humanReadable, context);
                  }
                skip = false;
              }
          }
      }
    return result;
  }


  /**
   * create String representation of the outer of this type
   */
  private String outerToString(boolean humanReadable)
  {
    var o = outer();
    return isThisType()
        ? (feature().outer().isUniverse() ? "" : feature().outer().qualifiedName() + ".")
        : o != null && (o.isGenericArgument() || !o.feature().isUniverse())
        ? o.toStringWrapped(humanReadable) + "."
        : "";
  }


  /**
   * Get a String representation of this Type.
   */
  public String toString()
  {
    return toString(false, null);
  }


  /**
   * wrap the result of toString in parentheses if necessary
   */
  public String toStringWrapped()
  {
    return StringHelpers.wrapInParentheses(toString());
  }


  /**
   * wrap the result of toString in parentheses if necessary
   */
  public String toStringWrapped(boolean humanReadable)
  {
    return StringHelpers.wrapInParentheses(toString(humanReadable));
  }


  /**
   * wrap the result of toString in parentheses if necessary
   *
   * @param context the feature to which the path should be relative to, universe if null
   */
  public String toStringWrapped(boolean humanReadable, AbstractFeature context)
  {
    return StringHelpers.wrapInParentheses(toString(humanReadable, context));
  }


  /**
   * Check if constraints on type parameters of this type are satisfied.
   *
   * @return itself on success or t_ERROR if constraints are not met and an
   * error was produced
   *
   * @param context the source code context where this Type is used
   */
  AbstractType checkConstraints(Context context)
  {
    var result = this;
    if (result != Types.t_ERROR && isNormalType())
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
   * @param call if this is a formal type in a call, this is the call, otherwise
   * null. This call is used to replace type parameters that depend on the
   * call's target or actual type parameters. Also this is used to for error
   * messages that require the source position of the call.
   *
   * @return true iff check was ok, false iff an error was found and reported
   */
  static boolean checkActualTypePars(Context context, AbstractFeature called, List<AbstractType> actuals, List<AbstractType> unresolvedActuals, Call call)
  {
    var result = true;
    var fi = called.typeArguments().iterator();
    var ai = actuals.iterator();
    var ui = unresolvedActuals.iterator();
    while (fi.hasNext() &&
           ai.hasNext()    ) // NYI: handling of open generic arguments
      {
        var f = fi.next();
        var a = ai.next();
        var u = ui.hasNext() ? ui.next() : null;
        var c = f.constraint(context).applyTypePars(called, actuals);
        if (CHECKS) check
          (Errors.any() || f != null && a != null);

        var pos = u instanceof UnresolvedType ut ? ut.pos() :
                  call != null                   ? call.pos()
                                                 : called.pos();

        if (a == Types.t_UNDEFINED)
          {
            AstErrors.failedToInferActualGeneric(pos, called, new List<>(f));
          }
        else
          {
            a.checkLegalThisType(pos, context);
            a.checkChoice(pos, context);
            if (!c.isGenericArgument() && // See AstErrors.constraintMustNotBeGenericArgument,
                                          // will be checked in SourceModule.checkTypes(Feature)
                !c.constraintAssignableFrom(context, call, a))
              {
                if (!f.isCoTypesThisType())
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
      }
    return result;
  }


  /**
   * If the type is a this-type, check if it is legal.
   */
  public void checkLegalThisType(SourcePosition pos, AbstractFeature outer)
  {
    checkLegalThisType(pos, outer.context());
  }


  /**
   * If the type is a this-type, check if it is legal.
   */
  private void checkLegalThisType(SourcePosition pos, Context context)
  {
    if (isThisType() && !isCotypeType())
      {
        var subject = feature();
        var found = false;
        AbstractFeature o = context.outerFeature();
        while (o != null)
          {
            o = o.isCotype() ? o.cotypeOrigin() : o;
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
   * @return this type and any of its generics that have more restrictive visibility than {@code v}.
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
  Stream<AbstractType> choices(Context context)
  {
    return isChoice()
      ? choiceGenerics(context)
        .stream()
        .flatMap(cg -> cg.choices(context))
      : Stream.of(this);
  }


  /**
   * Return constraint if type is a generic, unmodified type otherwise
   *
   * @return constraint for generics, unmodified type otherwise
   */
  public AbstractType selfOrConstraint()
  {
    return (isGenericArgument() ? genericArgument().constraint(Context.NONE) : this);
  }


  /**
   * Return constraint if type is a generic, unmodified type otherwise
   * @param context the context
   * @return constraint for generics, unmodified type otherwise
   */
  AbstractType selfOrConstraint(Context context)
  {
    return (isGenericArgument() ? genericArgument().constraint(context) : this);
  }


  /**
   * Return constraint if type is a generic, unmodified type otherwise
   * @param res the resolution
   * @param context the context
   * @return constraint for generics, unmodified type otherwise
   */
  AbstractType selfOrConstraint(Resolution res, Context context)
  {
    return (isGenericArgument() ? genericArgument().constraint(res, context) : this);
  }


  /**
   * traverse a resolved type collecting all features this type uses.
   *
   * @param s the features that have already been found
   */
  void usedFeatures(Set<AbstractFeature> s)
  {

  }


  /**
   * Is this is an incomplete type,
   * a resolve type where generics sizes do not match, yet.
   */
  public boolean isIncompleteType()
  {
    return this instanceof IncompleteType;
  }

}

/* end of file */
