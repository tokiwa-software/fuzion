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
import java.util.TreeMap;

import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * If <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class If extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  public Expr cond;

  /**
   *
   */
  public Block block;

  /**
   *
   */
  public Block elseBlock;

  /**
   *
   */
  public If elseIf;

  public Type _type;


  /**
   * Id to store the if condition's clazz in the static outer clazz at runtime.
   * The clazz could be bool or ref bool.
   */
  public int runtimeClazzId_ = -1;  // NYI: Used by dev.flang.be.interpreter, REMOVE!



  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param c
   *
   * @param b
   */
  public If(SourcePosition pos, Expr c, Block b)
  {
    this(pos, c, b, null);
  }


  /**
   * Constructor
   *
   * @param c
   *
   * @param b
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
    this.elseBlock = Block.fromExpr(elseB);
    if (this.elseBlock != null)
      {
        this.elseBlock._newScope = true;
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * setElse
   *
   * @param b
   */
  public void setElse(Block b)
  {
    if (PRECONDITIONS) require
      (elseBlock == null,
       elseIf == null);

    elseBlock = b;
    if (this.elseBlock != null)
      {
        this.elseBlock._newScope = true;
      }
  }


  /**
   * setElse
   *
   * @param f
   */
  public void setElse(If f)
  {
    if (PRECONDITIONS) require
      (elseBlock == null,
       elseIf == null);

    elseIf = f;
  }


  /**
   * Create an Iterator over all branches in this if statement, including all
   * else-if branches.
   */
  private Iterator<Expr> branches()
  {
    return new Iterator<Expr>()
    {
      If curIf = If.this;
      boolean blockReturned = false;
      public boolean hasNext()
      {
        return curIf != null;
      }
      public Expr next()
      {
        Expr result   = !blockReturned ? curIf.block : curIf.elseBlock != null ? curIf.elseBlock : null;
        blockReturned = !blockReturned && curIf.elseBlock != null;
        curIf         = blockReturned ? curIf : curIf.elseIf;
        return result;
      }
    };
  }


  /**
   * true if this If or any nested else-if-branches end with a missing
   * else-branch. If so, there is an execution path that does not take any of
   * the branches, so the result type is void.
   */
  private boolean hasUntakenElseBranch()
  {
    return
      elseIf != null ? elseIf.hasUntakenElseBranch()
                     : elseBlock == null;
  }


  /**
   * Helper routine for typeOrNull to determine the type of this if statement on
   * demand, i.e., as late as possible.
   */
  private Type typeFromIfOrElse()
  {
    Type result;
    if (hasUntakenElseBranch())
      {
        result = Types.resolved.t_unit;
      }
    else
      {
        result = Types.resolved.t_void;
        Iterator<Expr> it = branches();
        while (it.hasNext())
          {
            var t = it.next().typeOrNull();
            result = result == null || t == null ? null : result.union(t);
          }
      }
    if (result == Types.t_UNDEFINED)
      {
        new IncompatibleResultsOnBranches(pos,
                                          "Incompatible types in branches of if statement",
                                          branches());
        result = Types.t_ERROR;
      }
    return result;
  }


  /**
   * typeOrNull returns the type of this expression or null if the type is still
   * unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  public Type typeOrNull()
  {
    if (_type == null)
      {
        _type = typeFromIfOrElse();
      }
    return _type;
  }


  /**
   * Some Expressions do not produce a result, e.g., a Block that is empty or
   * whose last statement is not an expression that produces a result or an if
   * with one branch not producing a result.
   */
  boolean producesResult()
  {
    boolean result = !hasUntakenElseBranch();
    if (result)
      {
        var it = branches();
        while (it.hasNext())
          {
            var e = it.next();
            result = result && e.producesResult();
          }
      }
    return result;
  }


  /**
   * check the types in this if, in particular, chack that the condition is of
   * type bool.
   *
   * @param outer the root feature that contains this statement.
   */
  public void checkTypes()
  {
    Type t = cond.type();
    if (!Types.resolved.t_bool.isAssignableFrom(t))
      {
        FeErrors.ifConditionMustBeBool(cond.pos, t);
      }
  }


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
  public If visit(FeatureVisitor v, Feature outer)
  {
    cond = cond.visit(v, outer);
    block = block.visit(v, outer);
    if (elseBlock != null)
      {
        elseBlock = elseBlock.visit(v, outer);
      }
    if (elseIf != null)
      {
        elseIf = elseIf.visit(v, outer);
      }
    v.action(this, outer);
    return this;
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
   *
   * @return the Stmnt this Expr is to be replaced with, typically an Assign
   * that performs the assignment to r.
   */
  If assignToField(Resolution res, Feature outer, Feature r)
  {
    block = block.assignToField(res, outer, r);
    if (elseBlock != null)
      {
        elseBlock = elseBlock.assignToField(res, outer, r);
      }
    if (elseIf != null)
      {
        elseIf = elseIf.assignToField(res, outer, r);
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
   */
  public void propagateExpectedType(Resolution res, Feature outer)
  {
    if (cond != null)
      {
        cond = cond.propagateExpectedType(res, outer, Types.resolved.t_bool);
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
   * @param outer the feature that contains this expression
   *
   * @param t the expected type.
   *
   * @return either this or a new Expr that replaces thiz and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the statement that reads the field.
   */
  public Expr propagateExpectedType(Resolution res, Feature outer, Type t)
  {
    return addFieldForResult(res, outer, t);
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
      (elseIf != null
       ? "else "+elseIf
       : (elseBlock != null
          ? "else\n"+elseBlock.toString("  ")
          : ""
          )
       );
  }


}

/* end of file */
