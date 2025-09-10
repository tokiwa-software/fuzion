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
 * Source of class AbstractCall
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import static dev.flang.util.FuzionConstants.NO_SELECT;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.function.BiConsumer;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.StringHelpers;


/**
 * AbstractCall is an expression that is a call to a class and that results in
 * the result value of that class.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractCall extends Expr
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Special value for an empty generics list to distinguish a call without
   * generics ({@code a.b(x,y)}) from a call with an empty actual generics list
   * ({@code a.b<>(x,y)}).
   */
  public static final List<AbstractType> NO_GENERICS = new List<>();
  { NO_GENERICS.freeze(); }


  /*-----------------------------  methods  -----------------------------*/

  /**
   * The type parameters used for calling {@code calledFeature}, never null.
   *
   * The default implementations returns an empty list.
   */
  public List<AbstractType> actualTypeParameters()
  {
    return NO_GENERICS;
  }


  /**
   * The feature we are calling, never null.
   */
  public abstract AbstractFeature calledFeature();


  /**
   * The target of the call, never null.
   */
  public abstract Expr target();


  /**
   * The actual arguments of the call, never null.
   *
   * The default implementations returns an empty list.
   */
  public List<Expr> actuals()
  {
    return NO_EXPRS;
  }


  /**
   * For a call a.b.4 with a select clause ".4" to pick a variant from a field
   * of an open generic type, this is the chosen variant.
   *
   * The default implementations returns -1.
   */
  public int select()
  {
    return NO_SELECT;
  }


  /**
   * True iff this a call to a direct parent feature in an inheritance call.
   *
   * e.g.:
   *
   *     a : b.c.d is
   *     # ------^  for call d isInheritanceCall is true
   *
   * The default implementations returns false.
   */
  public boolean isInheritanceCall()
  {
    return false;
  }


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this.
   */
  @Override
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    // visit should be only required by the `Call` or `ParsedCall`, not by `LibraryCall`
    throw new Error("AbstractCall.visit on implemented by " + getClass());
  }



  /**
   * Does this call use dynamic binding.  Dynamic binding is used if the target
   * is not Current (either explicitly or in an inheritance call).  In case the
   * target is current, this call will be specialized to avoid dynamic binding.
   */
  public boolean isDynamic()
  {
    return
      !(target() instanceof AbstractCurrent) &&
      !isInheritanceCall();
  }


  /**
   * This call serialized as a constant.
   */
  public Constant asCompileTimeConstant()
  {
    var result = new Constant(AbstractCall.this.pos()) {

      /**
       * actuals are serialized in order. example
       * {@code tuple (u8 5) (codepoint u32 72)} results in
       * the following data:
       *        b b b b b
       * u8 ----^ ^^^^^^^--- codepoint u32 (both little endian)
       */
      @Override
      public byte[] data()
      {
        var b = AbstractCall.this
          .actuals()
          .map2(x -> x.asCompileTimeConstant().data());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (var d : b)
          {
            out.write(d, 0, d.length);
          }

        return out.toByteArray();
      }

      @Override
      AbstractType typeForInferencing()
      {
        return AbstractCall.this.type();
      }

      @Override
      public Expr visit(FeatureVisitor v, AbstractFeature outer)
      {
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
      }

      @Override
      public Expr origin() { return AbstractCall.this; }

    };
    return result;
  }


  /**
   * For a type feature, create the inheritance call for a parent type feature
   * from this original inheritance call.
   *
   * @param res Resolution instance used to resolve types in this call.
   *
   * @param that the original feature that is used to lookup types.
   *
   * @return instance of Call to be used for the parent call in cotype().
   */
  Call cotypeInheritanceCall(Resolution res, AbstractFeature that)
  {
    var selfType = new ParsedType(pos(),
                                  FuzionConstants.COTYPE_THIS_TYPE,
                                  new List<>(),
                                  null);
    var typeParameters = new List<AbstractType>(selfType);
    if (this instanceof Call cpc && cpc.needsToInferTypeParametersFromArgs())
      {
        typeParameters.addAll(actualTypeParameters());
        cpc.whenInferredTypeParameters(() ->
          {
            if (CHECKS) check
              (actualTypeParameters().stream().allMatch(atp -> !atp.containsUndefined(false)));
            if (CHECKS) check
              (Errors.any() || !typeParameters.isFrozen());
            if (!typeParameters.isFrozen())
              {
                typeParameters.removeTail(1);
                typeParameters.addAll(actualTypeParameters().map(that::rebaseTypeForCotype));
              }
          });
      }
    else
      {
        typeParameters.addAll(actualTypeParameters().map(that::rebaseTypeForCotype));
      }

    return calledFeature().cotypeInheritanceCall(pos(), typeParameters, res, that, target());
  }


  /**
   * Is this expression a call to `type_as_value`?
   */
  @Override
  boolean isTypeAsValueCall()
  {
    return calledFeature() == Types.resolved.f_type_as_value;
  }


  /**
   * Convert a formal argument type in this call to the actual type defined by
   * the target of this call and the actual type parameters given in this call.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Call is used
   *
   * @param frmlT the formal type. Might contain Types.t_UNDEFINED since this is
   * used during type resolution and type inference
   *
   * @return the actual type applying actual type parameters known from the
   * target of this call and actual type parameters given in this call. Result
   * is interned.
   */
  AbstractType actualArgType(Resolution res, Context context, AbstractType frmlT, AbstractFeature arg)
  {
    if (PRECONDITIONS) require
      (!frmlT.isOpenGeneric());

    return adjustResultType(res, context, target().type(), frmlT,
                                                (from,to) -> AstErrors.illegalOuterRefTypeInCall(this, true, arg, frmlT, from, to), true);
  }


  /**
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Call is used
   *
   * @param tt the target type to use when adjusting t.
   *
   * @param rt the result type to adjust
   *
   * @param foundRef a consumer that will be called for all the this-types found
   * together with the ref type they are replaced with.  May be null.  This will
   * be used to check for AstErrors.illegalOuterRefTypeInCall.
   *
   */
  protected AbstractType adjustResultType(Resolution res, Context context, AbstractType tt, AbstractType rt, BiConsumer<AbstractType, AbstractType> foundRef, boolean forArg /* NYI: UNDER DEVELOPMENT: try to remove this parameter */)
  {
    var t1 = rt == Types.t_ERROR ? rt : adjustThisTypeForTarget(context, rt, foundRef);
    var t2 = t1 == Types.t_ERROR ? t1 : t1.applyTypePars(tt);
    var t3 = t2 == Types.t_ERROR ? t2 : t2.applyTypePars(calledFeature(), actualTypeParameters(res, context));
    var t4 = t3 == Types.t_ERROR ? t3 : tt.isGenericArgument() ? t3 : t3.resolve(res, tt.feature().context());
    var t5 = t4 == Types.t_ERROR || forArg ? t4 : adjustThisTypeForTarget(context, t4, foundRef);

    if (POSTCONDITIONS) ensure
      (t5 != null);

    return t5;
  }


  /**
   * get actual type parameters during resolution
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
   *
   */
  protected List<AbstractType> actualTypeParameters(Resolution res, Context context)
  {
    return actualTypeParameters();
  }


  /**
   * Replace occurrences of this.type in formal arg or result type depending on
   * the target of the call.
   *
   * @param context the source code context where this Call is used
   *
   * @param t the formal type to be adjusted.
   *
   * @param foundRef a consumer that will be called for all the this-types found
   * together with the ref type they are replaced with.  May be null.  This will
   * be used to check for AstErrors.illegalOuterRefTypeInCall.
   *
   * @return a type derived from t where {@code this.type} is replaced by actual types
   * from the call's target where this is possible.
   */
  AbstractType adjustThisTypeForTarget(Context context, AbstractType t, BiConsumer<AbstractType, AbstractType> foundRef)
  {
    /**
     * For a call {@code T.f} on a type parameter whose result type contains
     * {@code this.type}, make sure we replace the implicit type parameter to
     * {@code this.type}.
     *
     * example:
     *
     *   equatable is
     *
     *     type.equality(a, b equatable.this.type) bool is abstract
     *
     *   equals(T type : equatable, x, y T) => T.equality x y
     *
     * For the call {@code T.equality x y}, we must replace the formal argument type
     * for {@code a} (and {@code b}) by {@code T}.
     */
    var target = target();
    var tt = target().type();
    var tpt = target.asTypeParameterType();
    if (tpt != null)
      {
        t = t.replace_type_parameter_used_for_this_type_in_cotype
          (tt.feature(),
           tpt);
      }
    if (!calledFeature().isOuterRef())
      {
        var declF = calledFeature().outer();
        if (!tt.isGenericArgument() && declF != tt.feature())
          {
            var heir = tt.feature();
            t = t.replace_inherited_this_type(declF, heir, foundRef);
          }
        var inner = ResolvedNormalType.newType(calledFeature().selfType(),
                                               target().type());
        t = t.replace_this_type_by_actual_outer(inner, foundRef, context);
      }
    return t;
  }


  /**
   * If this expression is a Call to a type parameter,
   * return the type parameters type, otherwise null.
   */
  @Override
  AbstractType asTypeParameterType()
  {
    return calledFeature().isTypeParameter()
      ? calledFeature().asGenericType()
      : null;
  }


  /**
   * Helper routine for resolveFormalArgumentTypes to determine the actual type
   * of a formal argument after inheritance and determination of actual type
   * from the target type and generics provided to the call.
   *
   * The result will be stored in _resolvedFormalArgumentTypes[argnum..].
   *
   * @param res Resolution instance
   *
   * @param context the source code context where this Call is used
   *
   * @param argnum the number of this formal argument
   *
   * @param frml the formal argument
   */
  AbstractType[] resolveFormalArg(Resolution res, Context context, AbstractType[] rfat, int argnum, AbstractFeature frml)
  {
    int cnt = 1;
    var frmlT = frml.resultTypeIfPresentUrgent(res, true);

    var declF = calledFeature().outer();
    var tt = target().type();
    if (!tt.isGenericArgument() && declF != tt.feature())
      {
        var a = calledFeature().handDown(res, new AbstractType[] { frmlT }, tt.feature());
        if (a.length != 1)
          {
            // Check that the number or args can only change for the
            // last argument (when it is of an open generic type).  if
            // it would change for other arguments, changing the
            // _resolvedFormalArgumentTypes array would invalidate
            // argnum for following arguments.
            if (CHECKS) check
              (Errors.any() || argnum == rfat.length - 1);
            if (argnum != rfat.length -1)
              {
                a = new AbstractType[] { Types.t_ERROR }; /* do not change _resolvedFormalArgumentTypes array length */
              }
          }
        rfat = addToResolvedFormalArgumentTypes(rfat, a, argnum);
        cnt = a.length;
      }
    else
      {
        rfat[argnum] = frmlT;
      }

    // next, replace generics given in the target type and in this call
    for (int i = 0; i < cnt; i++)
      {
        if (CHECKS) check
          (Errors.any() || argnum + i <= rfat.length);

        if (argnum + i < rfat.length)
          {
            frmlT = rfat[argnum + i];

            if (frmlT.isOpenGeneric())
              { // formal arg is open generic, i.e., this expands to 0 or more actual args depending on actual generics for target:
                var g = frmlT.genericArgument();
                var frmlTs = g.replaceOpen(openGenericsFor(res, context, g.outer()));
                rfat = addToResolvedFormalArgumentTypes(rfat, frmlTs.toArray(new AbstractType[frmlTs.size()]), argnum + i);
                i   = i   + frmlTs.size() - 1;
                cnt = cnt + frmlTs.size() - 1;
              }
            else
              {
                rfat[argnum + i] = actualArgType(res, context, frmlT, frml);
              }
          }
      }
    return rfat;
  }


  /**
   * Find the actual generics of the open generic argument in f.
   *
   * @param f the feature having the open type parameter
   */
  private List<AbstractType> openGenericsFor(Resolution res, Context context, AbstractFeature f)
  {
    return calledFeature().inheritsFrom(f)
      ? actualTypeParameters()
      : openGenericsFor(res, context, f, target().type());
  }


  /**
   * In the target type of this call,
   * find the actual generics of the open generic argument in f .
   *
   * @param f the feature having the open type parameter
   */
  private List<AbstractType> openGenericsFor(Resolution res, Context context, AbstractFeature f, AbstractType tt)
  {
    if (PRECONDITIONS) require
      (tt != null);

    var x = res == null ? tt.selfOrConstraint(context) : tt.selfOrConstraint(res, context);
    return x.feature().inheritsFrom(f)
      ? x.generics()
      : openGenericsFor(res, context, f, tt.outer());
  }


  /**
   * Helper routine for resolveFormalArg and replaceGenericsInFormalArg to
   * extend the _resolvedFormalArgumentTypes array.
   *
   * In case frml.resultType().isOpenGeneric(), this will call frml.select() for
   * all the actual types the open generic is replaced by to make sure the
   * corresponding features exist.
   *
   * @param a the new elements to add to _resolvedFormalArgumentTypes
   *
   * @param argnum index in _resolvedFormalArgumentTypes at which we add new
   * elements
   */
  private AbstractType[] addToResolvedFormalArgumentTypes(AbstractType[] rfat, AbstractType[] a, int argnum)
  {
    var na = new AbstractType[rfat.length - 1 + a.length];
    var j = 0;
    for (var i = 0; i < rfat.length; i++)
      {
        if (i == argnum)
          {
            for (var at : a)
              {
                if (CHECKS) check
                  (at != null);
                na[j] = at;
                j++;
              }
          }
        else
          {
            na[j] = rfat[i];
            j++;
          }
      }
    return na;
  }


  /**
   * For static type analysis: This gives the resolved formal argument types for
   * the arguments of this call.  During type checking, it has to be checked
   * that the actual arguments can be assigned to these types.
   *
   * The number of resolved formal arguments might be different to the number of
   * formal arguments in case the last formal argument is of an open generic
   * type.
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
   */
  AbstractType[] resolvedFormalArgumentTypes(Resolution res, Context context)
  {
    // NYI: UNDER DEVELOPMENT: cache this? cache key: calledFeature/target

    var result = new AbstractType[0];

    if (!(this instanceof Call c) || c.calledFeatureKnown())
      {
        var fargs = calledFeature().valueArguments();
        result = fargs.size() == 0
          ? UnresolvedType.NO_TYPES
          : new AbstractType[fargs.size()];
        Arrays.fill(result, Types.t_UNDEFINED);

        int count = 0;
        for (var frml : fargs)
          {
            int argnum = count;  // effectively final copy of count
            if (CHECKS)
              check(frml.state().atLeast(State.RESOLVED_TYPES));
            result = resolveFormalArg(res, context, result, argnum, frml);
            count++;
          }
      }

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * For static type analysis: This gives the resolved formal argument types for
   * the arguments of this call.  During type checking, it has to be checked
   * that the actual arguments can be assigned to these types.
   *
   * The number of resolved formal arguments might be different to the number of
   * formal arguments in case the last formal argument is of an open generic
   * type.
   *
   */
  public AbstractType[] formalArgumentTypes()
  {
    return resolvedFormalArgumentTypes(null, Context.NONE);
  }


  /**
   * Is this Expr a call to an outer ref?
   */
  @Override
  boolean isCallToOuterRef()
  {
    return calledFeature().isOuterRef();
  }


  /**
   * This call as a human readable string
   */
  public String toString()
  {
    return ((target() instanceof Universe) ||
            (target() instanceof This t && t.toString().equals(FuzionConstants.UNIVERSE_NAME + ".this"))
            ? ""
            : StringHelpers.wrapInParentheses(target().toString()) + ".")
      + (this instanceof Call c && !c.calledFeatureKnown() ? c._name : calledFeature().featureName().baseNameHuman())
      + actualTypeParameters().toString(" ", " ", "", t -> (t == null ? "--null--" : t.toStringWrapped(true)))
      + actuals()             .toString(" ", " ", "", e -> (e == null ? "--null--" : e.toStringWrapped()))
      + (select() < 0        ? "" : " ." + select());
  }


}

/* end of file */
