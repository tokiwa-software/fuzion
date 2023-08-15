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
 * Source of class Check
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.SourcePosition;


/**
 * Check <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Check extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  Cond cond;


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  public final SourcePosition _pos;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param c
   */
  public Check(SourcePosition pos, Cond c)
  {
    this._pos = pos;
    this.cond = c;
  }



  /*-----------------------------  methods  -----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  public SourcePosition pos()
  {
    return _pos;
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
  public Check visit(FeatureVisitor v, AbstractFeature outer)
  {
    cond.visit(v, outer);
    return this;
  }


  /**
   * visit all the expressions within this Check.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited expressions
   */
  public void visitExpressions(ExpressionVisitor v)
  {
    super.visitExpressions(v);
    cond.visitStatements(v);
  }


  /**
   * Does this expression consist of nothing but declarations? I.e., it has no
   * code that actually would be executed at runtime.
   */
  public boolean containsOnlyDeclarations()
  {
    return false;
  };


  /**
   * Some Expressions do not produce a result, e.g., a Block that is empty or
   * whose last expression is not an expression that produces a result.
   */
  public boolean producesResult()
  {
    return false;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return
      "check "+cond+"\n";
  }

}

/* end of file */
