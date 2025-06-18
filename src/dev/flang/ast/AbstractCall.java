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

import java.io.ByteArrayOutputStream;

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


  public abstract List<AbstractType> actualTypeParameters();
  public abstract AbstractFeature calledFeature();
  public abstract Expr target();
  public abstract List<Expr> actuals();
  public abstract int select();
  public abstract boolean isInheritanceCall();


  /**
   * visit all the expressions within this Call.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited expressions
   */
  public void visitExpressions(ExpressionVisitor v)
  {
    for (var a : actuals())
      {
        a.visitExpressions(v);
      }
    if (target() != null)
      {
        target().visitExpressions(v);
      }
    super.visitExpressions(v);
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
        var git = cpc._generics.iterator();
        for (var ignore : cpc.calledFeature().typeArguments())
          {
            typeParameters.add(git.hasNext() ? git.next() : Types.t_UNDEFINED);
          }
        cpc.whenInferredTypeParameters(() ->
          {
            if (CHECKS) check
              (actualTypeParameters().stream().allMatch(atp -> !atp.containsUndefined(false)));
            int i = 0;
            for (var atp : cpc.actualTypeParameters())
              {
                if (typeParameters.isFrozen())
                  {
                    if (CHECKS) check
                      (Errors.any());
                  }
                else
                  {
                    typeParameters.set(i+1, that.rebaseTypeForCotype(atp));
                  }
                i++;
              }
          });
      }
    else
      {
        for (var atp : actualTypeParameters())
          {
            typeParameters.add(that.rebaseTypeForCotype(atp));
          }
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
   * Replace occurrences of this.type in formal arg type depending on
   * the target of the call.
   *
   * @param argType the formal type to be adjusted.
   *
   * @return a type derived from t where {@code this.type} is replaced by actual types
   * from the call's target where this is possible.
   */
  public AbstractType adjustArgTypeForTarget(AbstractType argType)
  {
    return adjustThisTypeForTarget(argType, true, null, Context.NONE);
  }


  /**
   * Replace occurrences of this.type in formal arg or result type depending on
   * the target of the call.
   *
   * @param t the formal type to be adjusted.
   *
   * @param arg true if {@code t} is the type of an argument, false if {@code t} is the result type
   *
   * @param calledOrArg the declared argument (if arg == true) or the called feature (otherwise).
   *
   * @param context the source code context where this Call is used
   *
   * @return a type derived from t where {@code this.type} is replaced by actual types
   * from the call's target where this is possible.
   */
  AbstractType adjustThisTypeForTarget(AbstractType t, boolean arg, AbstractFeature calledOrArg, Context context)
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
    if (target instanceof Call tc &&
        tc.calledFeature().isTypeParameter() &&
        !tt.isGenericArgument())
      {
        t = t.replace_type_parameter_used_for_this_type_in_cotype
          (tt.feature(),
           tc);
      }
    if (!calledFeature().isOuterRef())
      {
        var t0 = t;
        var declF = calledFeature().outer();
        if (!tt.isGenericArgument() && declF != tt.feature())
          {
            var heir = tt.feature();
            t = t.replace_inherited_this_type(declF, heir,
                                              (from,to) -> AstErrors.illegalOuterRefTypeInCall(this, arg, calledOrArg, t0, from, to));
          }
        var inner = ResolvedNormalType.newType(calledFeature().selfType(),
                                               target().type());
        t = t.replace_this_type_by_actual_outer(inner,
                                                (from,to) -> AstErrors.illegalOuterRefTypeInCall(this, arg, calledOrArg, t0, from, to),
                                                context);
      }
    return t;
  }


  /**
   * This call as a human readable string
   */
  public String toString()
  {
    return (target() == null ||
            (target() instanceof Universe) ||
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
