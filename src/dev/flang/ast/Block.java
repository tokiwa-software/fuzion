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
 * Tokiwa GmbH, Berlin
 *
 * Source of class Block
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.ListIterator;

import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Block <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class Block extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  public List<Stmnt> statements_;

  SourcePosition closingBracePos_;

  boolean _newScope;

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Generic constructor
   *
   * @param pos the soucecode position of the start of this block, used for
   * error messages.
   *
   * @param closingBracePos the sourcecode position of this block's closing
   * brace. In case this block does not originate in source code, but was added
   * by AST manipulations, this might as well be equal to pos.
   *
   * @param s the list of statements
   *
   * @param newScope true iff this block opens a new scope, false if declaration
   * in this block should remain visibile after the block (which is usually the
   * case for artificially generated blocks)
   */
  private Block(SourcePosition pos,
                SourcePosition closingBracePos,
                List<Stmnt> s,
                boolean newScope)
  {
    super(pos);
    this.statements_ = s;
    this.closingBracePos_ = closingBracePos;
    this._newScope = newScope;
  }


  /**
   * Generate a block of statements that define a new scope. This is generally
   * called from the Parser when the source contains a block.
   *
   * @param pos the soucecode position of the start of this block, used for
   * error messages.
   *
   * @param closingBracePos the sourcecode position of this block's closing
   * brace. In case this block does not originate in source code, but was added
   * by AST manipulations, this might as well be equal to pos.
   *
   * @param s the list of statements
   */
  public Block(SourcePosition pos,
               SourcePosition closingBracePos,
               List<Stmnt> s)
  {
    this(pos, closingBracePos, s, true);
  }


  /**
   * Generate a block of statements that do not define a new scope, i.e.,
   * declarations remain visible after this block.
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param s the list of statements
   */
  public Block(SourcePosition pos,
               List<Stmnt> s)
  {
    this(pos, pos, s, false);
  }


  /*-------------------------  static methods  --------------------------*/


  /**
   * Create a block that consists only of one expression.
   */
  static Block fromExpr(Expr e)
  {
    Block result;
    if (e == null)
      {
        result = null;
      }
    else if (e instanceof Block)
      {
        result = (Block) e;
      }
    else
      {
        result = new Block(e.pos(), new List<Stmnt>(e));
      }
    return result;
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
  public Block visit(FeatureVisitor v, Feature outer)
  {
    v.actionBefore(this, outer);
    ListIterator<Stmnt> i = statements_.listIterator();
    while (i.hasNext())
      {
        Stmnt s = i.next();
        i.set(s.visit(v, outer));
      }
    v.actionAfter(this, outer);
    return this;
  }


  /**
   * resultExpression returns the last statement of this block if it is an
   * expression, null if the blcok is empty or the last expression is not an
   * Expr.
   *
   * @return the Expr that produces this Block's result
   */
  public Expr resultExpression()
  {
    Expr result = null;
    if (!statements_.isEmpty())
      {
        Stmnt s = statements_.getLast();
        if (s instanceof Expr)
          {
            result = ((Expr)s);
          }
      }
    return result;
  }


  /**
   * typeOrNull returns the type of this expression or Null if the type is still
   * unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  public Type typeOrNull()
  {
    Type result = Types.t_VOID;
    Expr resExpr = resultExpression();
    if (resExpr != null)
      {
        result = resExpr.typeOrNull();
      }
    return result;
  }


  /**
   * Load all features that are called by this expression.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param outer the class that contains this expression.
   */
  void loadCalledFeature(Resolution res, Feature outer)
  {
    Expr resExpr = resultExpression();
    if (resExpr != null)
      {
        resExpr.loadCalledFeature(res, outer);
      }
  }


  /**
   * The source code position of this expression that produces the result value
   * of this Expression. This is usually equal to this Expression's position,
   * unless we have a block of the form
   *
   *   {
   *     x;
   *     y
   *   }
   *
   * where this is the position of y.
   */
  SourcePosition posOfLast()
  {
    SourcePosition result = closingBracePos_;
    Expr resExpr = resultExpression();
    if (resExpr != null)
      {
        result = resExpr.pos;
      }
    return result;
  }


  /**
   * Convert this Expression into an assignment to the given field.  In case
   * this is a statment with several branches such as an "if" or a "match"
   * statement, add corresponding assignments in each branch and convert this
   * into a statement that does not produce a value.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   *
   * @param r the field this should be assigned to.
   */
  Block assignToField(Resolution res, Feature outer, Feature r)
  {
    Expr resExpr = resultExpression();
    if (resExpr != null)
      {
        statements_.removeLast();
        statements_.add(resExpr.assignToField(res, outer, r));
      }
    else
      {
        FeErrors.blockMustEndWithExpression(closingBracePos_, r.resultType());
      }
    return this;
  }


  /**
   * During type inference: Inform this expression that it is used in an
   * environment that expects the given type.  In particular, if this
   * expression's result is assigned to a field, this will be called with the
   * type of the field.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   *
   * @param t the expected type.
   *
   * @return either this or a new Expr that replaces thiz and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the statement that reads the field.
   */
  public Expr propagateExpectedType(Resolution res, Feature outer, Type type)
  {
    Expr resExpr = resultExpression();
    if (resExpr != null)
      {
        resExpr = resExpr.propagateExpectedType(res, outer, type);
        statements_.set(statements_.size() - 1, resExpr);
      }
    return this;
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
    String s = statements_.toString("", "\n");
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
