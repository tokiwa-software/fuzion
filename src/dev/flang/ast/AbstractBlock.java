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
 * Block represents a Block of expressions
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
   * @param s the list of expressions
   */
  public AbstractBlock(List<Expr> s)
  {
    this._expressions = s;
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
  public void visitExpressions(ExpressionVisitor v)
  {
    var s = _expressions;
    for (int i = 0; i < s.size(); i++)
      {
        s.get(i).visitExpressions(v);
      }
    super.visitExpressions(v);
  }


  /**
   * typeIfKnown returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  AbstractType typeIfKnown()
  {
    return Types.resolved.t_unit;
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
    return (i >= 0 && (_expressions.get(i).producesResult()))
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
      ? (Expr) _expressions.get(i)
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
