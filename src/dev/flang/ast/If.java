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
 * Source of class If
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Iterator;

import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * If <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class If extends ExprWithPos
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * the condition (must result in bool)
   */
  public Expr cond;

  /**
   * the if-block
   */
  public Block block;

  /**
   * the else-block (may be null)
   */
  public Block elseBlock;

  public AbstractType _type;

  private boolean _assignedToField = false;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param c the condition (must result in bool)
   *
   * @param b the if-block
   *
   * @param elseB the else-block (may be null)
   *
   */
  public If(SourcePosition pos, Expr c, Expr b, Expr elseB)
  {
    super(pos);

    if (PRECONDITIONS) require
                         (c != null,
                          b != null);

    this.cond = c;
    this.block = Block.fromExpr(b);
    this.block._newScope = true;

    var eb = Block.fromExpr(elseB);
    if (eb != null)
      {
        eb._newScope = true;
        elseBlock = eb;
      }

    /**
     * If there is no else / elseif, create a default else
     * branch returning unit.
     */
    if (elseBlock == null)
      {
        var unit = new Call(pos(), "unit");
        elseBlock = new Block(new List<>(unit));
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Is this a normal if (`false`) or one created to implement a contract such
   * as pre- or postconditions (`true`)?
   *
   * @return true iff this is an artificially generated if that originates in a
   * condition of a contract.
   */
  boolean fromContract()
  {
    return false;
  }


  /**
   * Create an Iterator over all branches in this if expression, including all
   * else-if branches.
   */
  Iterator<Expr> branches()
  {
    return new Iterator<Expr>()
    {
      If curIf = If.this;
      Expr lastBlock = null;
      public boolean hasNext()
      {
        return curIf != null || lastBlock != null;
      }
      public Expr next()
      {
        var result = curIf == null
          ? lastBlock
          : curIf.block;

        lastBlock = curIf != null ? curIf.elseBlock : null;
        curIf = curIf != null && curIf.elseBlock._expressions.size() == 1 && curIf.elseBlock._expressions.get(0) instanceof If i
          ? i
          : null;

        return result;
      }
    };
  }


  /**
   * Helper routine for typeForInferencing to determine the
   * type of this if expression on demand, i.e., as late as possible.
   *
   * @param context the source code context where this Expr is used
   */
  private AbstractType typeFromIfOrElse(Context context)
  {
    var result = Expr.union(new List<>(branches()), context);
    if (result==Types.t_ERROR)
      {
        new IncompatibleResultsOnBranches(pos(),
                                          "Incompatible types in branches of if expression",
                                          branches());
      }
    return result;
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
    if (_type == null)
      {
        _type = typeFromIfOrElse(Context.NONE);
      }
    return _type;
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
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    cond = cond.visit(v, outer);
    v.actionBeforeIfThen(this);
    block = block.visit(v, outer);
    v.actionBeforeIfElse(this);
    if (elseBlock != null)
      {
        elseBlock = elseBlock.visit(v, outer);
      }
    var res = v.action(this, outer);
    v.actionAfterIf(this);
    return res;
  }


  /**
   * visit all the expressions within this If.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited expressions
   */
  public void visitExpressions(ExpressionVisitor v)
  {
    super.visitExpressions(v);
    cond.visitExpressions(v);
    block.visitExpressions(v);
    if (elseBlock != null)
      {
        elseBlock.visitExpressions(v);
      }
  }


  /**
   * Convert this Expression into an assignment to the given field.  In case
   * this is a expression with several branches such as an "if" or a "match"
   * expression, add corresponding assignments in each branch and convert this
   * into a expression that does not produce a value.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @param r the field this should be assigned to.
   *
   * @return the Expr this Expr is to be replaced with, typically an Assign
   * that performs the assignment to r.
   */
  @Override
  If assignToField(Resolution res, Context context, Feature r)
  {
    block = block.assignToField(res, context, r);
    if (elseBlock != null)
      {
        elseBlock = elseBlock.assignToField(res, context, r);
      }
    _assignedToField = true;
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
   * @param context the source code context where this Expr is used
   */
  public void propagateExpectedType(Resolution res, Context context)
  {
    if (cond != null)
      {
        cond = cond.propagateExpectedType(res, context, Types.resolved.t_bool);
      }
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
   * @param context the source code context where this Expr is used
   *
   * @param t the expected type.
   *
   * @return either this or a new Expr that replaces thiz and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the expression that reads the field.
   */
  @Override
  public Expr propagateExpectedType(Resolution res, Context context, AbstractType t)
  {
    return addFieldForResult(res, context, t);
  }


  /**
   * Resolve syntactic sugar, e.g., by replacing anonymous inner functions by
   * declaration of corresponding inner features. Add (f,<>) to the list of
   * features to be searched for runtime types to be layouted.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   */
  public Expr resolveSyntacticSugar2(Resolution res)
  {
    return typeForInferencing() == Types.t_ERROR
      ? this  // no need to possible produce more errors
      : new AbstractMatch() {
          @Override
          public Expr subject()
          {
            return cond;
          }
          @Override
          public SourcePosition pos()
          {
            return If.this.pos();
          }
          @Override
          Kind kind()
          {
            return If.this.fromContract() ? Kind.Contract : Kind.If;
          }
          @Override
          public List<AbstractCase> cases()
          {
            return new List<AbstractCase>(
              new Case(block.pos(), new List<AbstractType>(Types.resolved.f_TRUE.selfType()), block),
              new Case(elseBlock.pos(), new List<AbstractType>(Types.resolved.f_FALSE.selfType()), elseBlock));
          }
        };
  }



  /**
   * Some Expressions do not produce a result, e.g., a Block that is empty or
   * whose last expression is not an expression that produces a result.
   */
  public boolean producesResult()
  {
    return !_assignedToField;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return
      "if "+cond+"\n"+block.toString("  ")+
      "else "+elseBlock.toString("  ");
  }


}

/* end of file */
