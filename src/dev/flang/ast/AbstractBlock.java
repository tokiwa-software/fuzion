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

import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Block represents a Block of statements
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractBlock extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  public List<Stmnt> statements_;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Generic constructor
   *
   * @param s the list of statements
   */
  public AbstractBlock(List<Stmnt> s)
  {
    this.statements_ = s;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * visit all the features, expressions, statements within this feature.
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
    ListIterator<Stmnt> i = statements_.listIterator();
    while (i.hasNext())
      {
        Stmnt s = i.next();
        i.set(s.visit(v, outer));
      }
    return this;
  }


  /**
   * visit all the statements within this Block.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited statements
   */
  public void visitStatements(StatementVisitor v)
  {
    var s = statements_;
    for (int i = 0; i < s.size(); i++)
      {
        s.get(i).visitStatements(v);
      }
    super.visitStatements(v);
  }


  /**
   * typeOrNull returns the type of this expression or null if the type is still
   * unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  public AbstractType typeOrNull()
  {
    return Types.resolved.t_unit;
  }


  /**
   * resultExpressionIndex returns the index of the last non-NOP statement of
   * this block if it is an expression, -1 if the block is empty or the last
   * non-NOP statement is not an Expr.
   *
   * @return the index of the Expr that produces this Block's result, -1 if none
   * exists.
   */
  protected int resultExpressionIndex()
  {
    var i = statements_.size() - 1;
    while (i >= 0 && (statements_.get(i) instanceof Nop))
      {
        i--;
      }
    return (i >= 0 && (statements_.get(i) instanceof Expr))
      ? i
      : -1;
  }


  /**
   * resultExpression returns the last non-NOP statement of this block if it is
   * an expression, null if the block is empty or the last non-NOP statement is
   * not an Expr.
   *
   * @return the Expr that produces this Block's result, or null if none.
   */
  public Expr resultExpression()
  {
    var i = resultExpressionIndex();
    return i >= 0
      ? (Expr) statements_.get(i)
      : null;
  }


  /**
   * Does this statement consist of nothing but declarations? I.e., it has no
   * code that actually would be executed at runtime.
   */
  public boolean containsOnlyDeclarations()
  {
    boolean result = true;
    for (Stmnt s : statements_)
      {
        result = result && s.containsOnlyDeclarations();
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
    return new StringBuilder().append("{\n").append(toString("  ")).append("}").toString();
  }


  /**
   * toString
   *
   * @return
   */
  public String toString(String prefix)
  {
    String s = statements_.toString("\n");
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
