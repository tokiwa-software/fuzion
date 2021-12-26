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
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Block extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  public List<Stmnt> statements_;

  SourcePosition closingBracePos_;

  boolean _newScope;


  /**
   * true iff this block produces an implicit result that can be ignored if
   * assigned to unit type.
   */
  private boolean _hasImplicitResult;


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


  /**
   * Generate a block of statements that do not define a new scope, i.e.,
   * declarations remain visible after this block.
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param s the list of statements
   *
   * @param hasImplicitResult true iff this block produces an implicit result
   * that can be ignored if assigned to unit type.
   */
  public Block(SourcePosition pos,
               List<Stmnt> s,
               boolean hasImplicitResult)
  {
    this(pos, s);
    this._hasImplicitResult = hasImplicitResult;
  }


  /*-------------------------  static methods  --------------------------*/


  /**
   * Create a block that consists only of one expression.  null if e == null.
   *
   * @param e an expression or null
   *
   * @return e if e is a Block, otherwise a new block that contains e or null if
   * e is null.
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


  /**
   * Create a block from one expression, or an empty block if expression is
   * null.
   *
   * @param e an expression or null
   *
   * @return e if e is a Block, otherwise a new block that is either empty or
   * contains e (if e not null).
   */
  static Block newIfNull(SourcePosition pos, Expr e)
  {
    var b = fromExpr(e);
    return b == null ? new Block(pos, new List<>()) : b;
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
  public Block visit(FeatureVisitor v, AbstractFeature outer)
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
   * visit all the statements within this Block.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited statements
   */
  public void visitStatements(StatementVisitor v)
  {
    super.visitStatements(v);
    for (var s : statements_)
      {
        s.visitStatements(v);
      }
  }


  /**
   * resultExpressionIndex returns the index of the last non-NOP statement of
   * this block if it is an expression, -1 if the block is empty or the last
   * non-NOP statement is not an Expr.
   *
   * @return the index of the Expr that produces this Block's result, -1 if none
   * exists.
   */
  private int resultExpressionIndex()
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
   * removeResultExpression removes and returns the last non-NOP statement of
   * this block if it is an expression.  Does nothing an returns null if the
   * block is empty or the last non-NOP statement is not an Expr.
   *
   * @return the Expr that produces this Block's result
   */
  private Expr removeResultExpression()
  {
    var i = resultExpressionIndex();
    return i >= 0
      ? (Expr) statements_.remove(i)
      : null;
  }


  /**
   * Check if this value might need boxing and wrap this into Box() if this is
   * the case.
   *
   * @param frmlT the formal type this is assigned to.
   *
   * @return this or an instance of Box wrapping this.
   */
  Expr box(Stmnt s, int arg)
  {
    var r = removeResultExpression();
    if (r != null)
      {
        statements_.add(r.box(s, arg));
      }
    return this;
  }


  /**
   * typeOrNull returns the type of this expression or null if the type is still
   * unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  public AbstractType typeOrNull()
  {
    AbstractType result = Types.resolved.t_unit;
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
  void loadCalledFeature(Resolution res, AbstractFeature outer)
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
   * Does this block produce a result that does not explicitly appear in source
   * code? This is the case, e.g., for loops that implicitly return the last
   * value of the index variable for true/false to indicate success or failure.
   *
   * In this case, the implicit result can safely be replace by unit if it is
   * used as a unit type.
   */
  private boolean hasImplicitResult()
  {
    return _hasImplicitResult ||
      resultExpression() instanceof Block b && b.hasImplicitResult();
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
  Block assignToField(Resolution res, AbstractFeature outer, Feature r)
  {
    Expr resExpr = removeResultExpression();
    if (resExpr != null)
      {
        statements_.add(resExpr.assignToField(res, outer, r));
      }
    else if (r.resultType() != Types.resolved.t_unit)
      {
        AstErrors.blockMustEndWithExpression(closingBracePos_, r.resultType());
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
  public Expr propagateExpectedType(Resolution res, AbstractFeature outer, AbstractType type)
  {
    if (type == Types.resolved.t_unit && hasImplicitResult())
      { // return unit if this is expected even if we would implicitly return
        // something else:
        statements_.add(new Block(pos, new List<>()));
      }
    Expr resExpr = removeResultExpression();
    if (resExpr != null)
      {
        statements_.add(resExpr.propagateExpectedType(res, outer, type));
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
   * Some Expressions do not produce a result, e.g., a Block that is empty or
   * whose last statement is not an expression that produces a result or an if
   * with one branch not producing a result.
   */
  boolean producesResult()
  {
    var expr = resultExpression();
    return expr != null && expr.producesResult();
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
