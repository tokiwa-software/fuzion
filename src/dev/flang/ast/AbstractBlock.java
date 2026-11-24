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


  /** {@inheritDoc} */
  @Override
  AbstractType typeForInferencing()
  {
    Expr resExpr = resultExpression();
    return resExpr == null
      ? Types.resolved.t_unit
      : resExpr.typeForInferencing();
  }


  @Override
  AbstractType typeForInferencing(Context context)
  {
    Expr resExpr = resultExpression();
    return resExpr == null
      ? Types.resolved.t_unit
      : resExpr.typeForInferencing(context);
  }


  /** {@inheritDoc} */
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
    return i >= 0 && (_expressions.get(i).producesResult())
      ? i
      : -1;
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


  /** {@inheritDoc} */
  @Override
  boolean isCallToOuterRef()
  {
    return resultExpression() != null && resultExpression().isCallToOuterRef();
  }


  /** {@inheritDoc} */
  @Override
  public Constant asCompileTimeConstant()
  {
    return resultExpression().asCompileTimeConstant();
  }


  /** {@inheritDoc} */
  @Override
  protected void propagateExpectedType(AbstractType t)
  {
    var resExpr = resultExpression();
    if (resExpr != null)
      {
        resExpr.propagateExpectedType(t);
      }
  }


  /** {@inheritDoc} */
  @Override
  AbstractType typeForUnion()
  {
    Expr resExpr = resultExpression();
    return resExpr == null
      ? Types.resolved.t_unit
      : resExpr.typeForUnion();
  }


  @Override
  public boolean isEmpty()
  {
    return _expressions
      .stream()
      // declarations are NOPs, hence allowed
      .allMatch(x -> x instanceof AbstractFeature);
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
