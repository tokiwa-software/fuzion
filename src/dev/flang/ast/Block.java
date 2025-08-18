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
import java.util.function.Supplier;

import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;
import dev.flang.util.SourceRange;


/**
 * Block represents a Block of expressions
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Block extends AbstractBlock
{


  /*----------------------------  variables  ----------------------------*/


  public boolean _newScope;


  /**
   * true iff this block produces an implicit result that can be ignored if
   * assigned to unit type.
   */
  private boolean _hasImplicitResult;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Generic constructor
   *
   * @param newScope true iff this block opens a new scope, false if declaration
   * in this block should remain visible after the block (which is usually the
   * case for artificially generated blocks)
   *
   * @param s the list of expressions
   *
   */
  public Block(boolean newScope,
               List<Expr> s)
  {
    super(s);
    this._newScope = newScope;
  }


  /**
   * Generate an empty block of expressions. This is called from the Parser when
   * the body of a routine contains no code but just a {@code .}.
   */
  public Block()
  {
    this(true, new List<>());
  }


  /**
   * Generate a block of expressions that do not define a new scope, i.e.,
   * declarations remain visible after this block.
   *
   * @param s the list of expressions
   */
  public Block(List<Expr> s)
  {
    this(false, s);
  }


  /**
   * Generate a block of expressions that do not define a new scope, i.e.,
   * declarations remain visible after this block.
   *
   * @param s the list of expressions
   *
   * @param hasImplicitResult true iff this block produces an implicit result
   * that can be ignored if assigned to unit type.
   */
  public Block(List<Expr> s,
               boolean hasImplicitResult)
  {
    this(s);
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
        result = new Block(new List<Expr>(e));
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
  static Block newIfNull(Expr e)
  {
    var b = fromExpr(e);
    return b == null ? new Block(new List<>()) : b;
  }


  /*-----------------------------  methods  -----------------------------*/


  @Override
  public UnresolvedType asParsedType()
  {
    return _expressions.size() == 1 ? _expressions.getFirst().asParsedType() : null;
  }

  /**
   * The sourcecode position of this expression, used for error messages.
   */
  public SourcePosition pos()
  {
    return _range != null
      ? _range
      : _expressions.isEmpty()
      || _expressions.getFirst().pos().isBuiltIn()
      || _expressions.getLast().pos().isBuiltIn()
      ? SourcePosition.notAvailable
      // NYI: UNDER DEVELOPMENT: hack, positions used for loops are not always in ascending order.
      : _expressions.getFirst().pos().bytePos() > _expressions.getLast().pos().byteEndPos()
      ? SourcePosition.notAvailable
      : new SourceRange(
          _expressions.getFirst().pos()._sourceFile,
          _expressions.getFirst().pos().bytePos(),
          _expressions.getLast().pos().byteEndPos());
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
  public Block visit(FeatureVisitor v, AbstractFeature outer)
  {
    v.actionBefore(this);
    ListIterator<Expr> i = _expressions.listIterator();
    while (i.hasNext())
      {
        Expr e = i.next();
        i.set(e.visit(v, outer));
      }
    v.actionAfter(this);
    return this;
  }


  /**
   * Load all features that are called by this expression.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   */
  @Override
  void loadCalledFeature(Resolution res, Context context)
  {
    Expr resExpr = resultExpression();
    if (resExpr != null)
      {
        resExpr.loadCalledFeature(res, context);
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
    Expr resExpr = resultExpression();
    return resExpr != null ? resExpr.pos()
                           : pos();
  }


  /**
   * removeResultExpression removes and returns the last non-NOP expression of
   * this block if it is an expression.  Does nothing and returns null if the
   * block is empty or the last non-NOP expression is not an Expr.
   *
   * @return the Expr that produces this Block's result
   */
  private Expr removeResultExpression()
  {
    var i = resultExpressionIndex();
    return i >= 0
      ? _expressions.remove(i)
      : null;
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
   */
  @Override
  Block assignToField(Resolution res, Context context, Feature r)
  {
    Expr resExpr = removeResultExpression();
    if (resExpr == null && r.resultType().isAssignableFromWithoutBoxing(Types.resolved.t_unit, context).yes())
      {
        resExpr = new Call(pos(), FuzionConstants.UNIT_NAME)
          .resolveTypes(res, context);
      }
    if (resExpr != null)
      {
        _expressions.add(resExpr.assignToField(res, context, r));
      }
    else
      {
        AstErrors.blockMustEndWithExpression(pos(), r.resultType());
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
   * @param context the source code context where this Expr is used
   *
   * @param type the expected type.
   *
   * @param from for error output: if non-null, produces a String describing
   * where the expected type came from.
   *
   * @return either this or a new Expr that replaces thiz and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the expression that reads the field.
   */
  Expr propagateExpectedType(Resolution res, Context context, AbstractType type, Supplier<String> from)
  {
    Expr result = this;
    Expr resExpr = resultExpression();
    if (type.compareTo(Types.resolved.t_unit) == 0 && hasImplicitResult() ||
        resExpr == null && Types.resolved.t_unit.compareTo(type) != 0)
      {
        _expressions.add(new Call(pos(), FuzionConstants.UNIT_NAME).resolveTypes(res, context));
      }
    else if (resExpr != null)
      {
        // this may do partial application for the whole block
        result = super.propagateExpectedType(res, context, type, from);
        if (result == this)
          {
            // we must not remove result expression just yet.
            // we rely on it being present in SourceModule.inScope()
            var idx = resultExpressionIndex();
            var x = resExpr.propagateExpectedType(res, context, type, from);
            _expressions.remove(idx);
            _expressions.add(x);
          }
      }
    return result;
  }


  /**
   * check that each expression in this block
   * results in either unit or void.
   */
  public void checkTypes()
  {
    _expressions
      .stream()
      .limit(_expressions.isEmpty() ? 0 : _expressions.size() - 1)
      .forEach(e -> {
        if (e.producesResult() &&
            e.type().compareTo(Types.resolved.t_unit) != 0 &&
            !e.type().isVoid() &&
            e.type() != Types.t_ERROR)
          {
            AstErrors.unusedResult(e);
          }
      });

  }


}

/* end of file */
