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
 * Source of class AbstractBlock
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.ListIterator;

import dev.flang.util.List;


/**
 * AbstractBlock represents a list of expressions
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractBlock extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  public List<Expr> _expressions;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Generic constructor
   *
   * @param e the list of expressions
   */
  public AbstractBlock(List<Expr> e)
  {
    this._expressions = e;
  }


  /*-----------------------------  methods  -----------------------------*/


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
  public AbstractBlock visit(FeatureVisitor v, AbstractFeature outer)
  {
    ListIterator<Expr> i = _expressions.listIterator();
    while (i.hasNext())
      {
        Expr e = i.next();
        i.set(e.visit(v, outer));
      }
    return this;
  }


  /**
   * visit all the expressions within this Block.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited expressions
   */
  void visitExpressions(ExpressionVisitor v)
  {
    var e = _expressions;
    for (int i = 0; i < e.size(); i++)
      {
        e.get(i).visitExpressions(v);
      }
    super.visitExpressions(v);
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
    Expr resExpr = resultExpression();
    return resExpr == null
      ? Types.resolved.t_unit
      : resExpr.typeForInferencing();
  }


  /**
   * type returns the type of this expression or Types.t_ERROR if the type is
   * still unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or t_ERROR in case it is not known yet.
   * t_FORWARD_CYCLIC in case the type can not be inferred due to circular inference.
   */
  @Override
  public AbstractType type()
  {
    Expr resExpr = resultExpression();
    return resExpr == null
      ? Types.resolved.t_unit
      : resExpr.type();
  }


  /**
   * resultExpressionIndex returns the index of the last non-NOP expression of
   * this block if it is an expression, -1 if the block is empty or the last
   * non-NOP expression is not an Expr.
   *
   * @return the index of the Expr that produces this Block's result, -1 if none
   * exists.
   */
  protected int resultExpressionIndex()
  {
    var i = _expressions.size() - 1;
    while (i >= 0 && (_expressions.get(i) instanceof Nop))
      {
        i--;
      }
    return i >= 0 && (_expressions.get(i).producesResult())
      ? i
      : -1;
  }


  /**
   * Try to perform partial application such that this expression matches
   * {@code expectedType}.  Note that this may happen twice:
   *
   * 1. during RESOLVING_DECLARATIONS phase of outer when resolving arguments to
   *    a call such as {@code l.map +1}. In this case, expectedType may be a function
   *    type {@code Function R A} with generic arguments not yet replaced by actual
   *    arguments, in particular the result type {@code R} is unknown since it is the
   *    result type of this expression.
   *
   * 2. during TYPES_INFERENCING phase when the target variable's type is fully
   *    resolved and this gets propagated to this expression.
   *
   * Note that this does not perform resolveTypes on the results since that
   * would be too early during 1. but it is required in 2.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @param expectedType the expected type.
   */
  @Override
  Expr propagateExpectedTypeForPartial(Resolution res, Context context, AbstractType expectedType)
  {
    var a = resultExpression();
    return a == null
      ? super.propagateExpectedTypeForPartial(res, context, expectedType)
      : replacedResultExpression(a.propagateExpectedTypeForPartial(res, context, expectedType));
  }


  /**
   * create a new block just like this but with a
   * replaced result expression.
   *
   * @param newResultExpression
   * @return
   */
  private Expr replacedResultExpression(Expr newResultExpression)
  {
    if (PRECONDITIONS) require
      (this.resultExpressionIndex() >= 0);

    var l = _expressions
      .take(resultExpressionIndex());
    l.add(newResultExpression);
    return new Block(l);

  }


  /**
   * resultExpression returns the last non-NOP expression of this block if it is
   * an expression, null if the block is empty or the last non-NOP expression is
   * not an Expr.
   *
   * @return the Expr that produces this Block's result, or null if none.
   */
  public Expr resultExpression()
  {
    var i = resultExpressionIndex();
    return i >= 0
      ? _expressions.get(i)
      : null;
  }


  /**
   * Does this expression consist of nothing but declarations? I.e., it has no
   * code that actually would be executed at runtime.
   */
  public boolean containsOnlyDeclarations()
  {
    boolean result = true;
    for (Expr e : _expressions)
      {
        result = result && e.containsOnlyDeclarations();
      }
    return result;
  }


  /**
   * Is this Expr a call to an outer ref?
   */
  public boolean isCallToOuterRef()
  {
    return resultExpression() != null && resultExpression().isCallToOuterRef();
  }


  /**
   * This expression as a compile time constant.
   */
  @Override
  public Constant asCompileTimeConstant()
  {
    return resultExpression().asCompileTimeConstant();
  }


  /**
   * During type inference: Inform this expression that it is
   * expected to result in the given type.
   *
   * @param t the expected type.
   */
  @Override
  protected void propagateExpectedType(AbstractType t)
  {
    var resExpr = resultExpression();
    if (resExpr != null)
      {
        resExpr.propagateExpectedType(t);
      }
  }


  /**
   * typeForUnion returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  @Override
  AbstractType typeForUnion()
  {
    Expr resExpr = resultExpression();
    return resExpr == null
      ? Types.resolved.t_unit
      : resExpr.typeForUnion();
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return new StringBuilder().append("{\n").append(toString("  ")).append("}").toString();
  }


  /**
   * toString
   *
   * @return
   */
  public String toString(String prefix)
  {
    String s = _expressions.toString("\n");
    StringBuilder sb = new StringBuilder();
    if (s.length() > 0)
      {
        sb.append(prefix);
      }
    for (int i=0; i<s.length(); i++)
      {
        var c = s.charAt(i);
        sb.append(c);
        if (c == '\n' && i < s.length()-1)
          {
            sb.append(prefix);
          }
      }
    return sb.toString();
  }

}

/* end of file */
