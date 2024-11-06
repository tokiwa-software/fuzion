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
import dev.flang.util.SourcePosition;


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
   * generics ("a.b(x,y)") from a call with an empty actual generics list
   * ("a.b<>(x,y)").
   */
  public static final List<AbstractType> NO_GENERICS = new List<>();


  /*-------------------------- constructors ---------------------------*/


  /**
   * Constructor
   */
  public AbstractCall()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  public abstract List<AbstractType> actualTypeParameters();
  public abstract AbstractFeature calledFeature();
  public abstract Expr target();
  public abstract List<Expr> actuals();
  public abstract int select();
  public abstract boolean isInheritanceCall();
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    say_err("Called "+this.getClass()+".visit");
    return this;
  }


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
   * typeForInferencing returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  @Override
  AbstractType typeForInferencing()
  {
    return type();
  }


  /**
   * This call serialized as a constant.
   */
  public Constant asCompileTimeConstant()
  {
    var result = new Constant(AbstractCall.this.pos()) {

      /**
       * actuals are serialized in order. example
       * `tuple (u8 5) (codepoint u32 72)` results in
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
   * For a type feature, create the inheritance call for a parent type feature.
   *
   * @param p the source position
   *
   * @param typeParameters the type parameters passed to the call
   *
   * @param res Resolution instance used to resolve types in this call.
   *
   * @param that the original feature that is used to lookup types.
   *
   * @return instance of Call to be used for the parent call in cotype().
   */
  Call typeCall(SourcePosition p, Resolution res, AbstractFeature that)
  {
    var selfType = new ParsedType(pos(),
                                  FuzionConstants.COTYPE_THIS_TYPE,
                                  new List<>(),
                                  null);
    var typeParameters = new List<AbstractType>(selfType);
    if (this instanceof Call cpc && cpc.needsToInferTypeParametersFromArgs())
      {
        var git = cpc._generics.iterator();
        for (var atp : cpc.calledFeature().typeArguments())
          {
            typeParameters.add(git.hasNext() ? git.next() : Types.t_UNDEFINED);
          }
        cpc.whenInferredTypeParameters(() ->
          {
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

    var o = calledFeature().outer();
    Expr oc = o == null || o.isUniverse()
      ? new Universe()
      : (target() instanceof AbstractCall ac && !ac.isCallToOuterRef())
      ? ac.typeCall(p, res, that)
      : o.typeCall(p, new List<>(o.selfType()), res, that);

    var tf = calledFeature().cotype(res);

    return new Call(p,
                    oc,
                    typeParameters,
                    Expr.NO_EXPRS,
                    tf,
                    tf.selfType());
  }


}

/* end of file */
