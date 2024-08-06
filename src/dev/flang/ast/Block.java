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
  private Block(boolean newScope,
               List<Expr> s)
  {
    super(s);
    this._newScope = newScope;
  }


  /**
   * Generate an empty block of expressions. This is called from the Parser when
   * the body of a routine contains no code but just a `.`.
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
      // NYI hack, positions used for loops are not always in right order.
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
    v.actionBefore(this, outer);
    ListIterator<Expr> i = _expressions.listIterator();
    while (i.hasNext())
      {
        Expr e = i.next();
        i.set(e.visit(v, outer));
      }
    v.actionAfter(this, outer);
    return this;
  }


  /**
   * Load all features that are called by this expression.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param outer the class that contains this expression.
   */
  @Override
  void loadCalledFeature(Resolution res, AbstractFeature outer, List<AbstractCall> infix_colons)
  {
    Expr resExpr = resultExpression();
    if (resExpr != null)
      {
        resExpr.loadCalledFeature(res, outer, infix_colons);
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
   * Check if this value might need boxing and wrap this into Box() if this is
   * the case.
   *
   * @param frmlT the formal type this is assigned to.
   *
   * @return this or an instance of Box wrapping this.
   */
  @Override
  Expr box(AbstractType frmlT, AbstractFeature outer, List<AbstractCall> infix_colons)
  {
    var r = removeResultExpression();
    if (CHECKS) check
      (r != null || Types.resolved.t_unit.compareTo(frmlT) == 0);
    if (r != null)
      {
        _expressions.add(r.box(frmlT, outer, infix_colons));
      }
    return this;
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
   * @param outer the feature that contains this expression
   *
   * @param r the field this should be assigned to.
   */
  @Override
  Block assignToField(Resolution res, AbstractFeature outer, List<AbstractCall> infix_colons, Feature r)
  {
    Expr resExpr = removeResultExpression();
    if (resExpr != null)
      {
        _expressions.add(resExpr.assignToField(res, outer, infix_colons, r));
      }
    else if (!r.resultType().isAssignableFrom(Types.resolved.t_unit, outer, infix_colons))
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
   * @param outer the feature that contains this expression
   *
   * @param type the expected type.
   *
   * @return either this or a new Expr that replaces thiz and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the expression that reads the field.
   */
  public Expr propagateExpectedType(Resolution res, AbstractFeature outer, List<AbstractCall> infix_colons, AbstractType type)
  {
    if (type.compareTo(Types.resolved.t_unit) == 0 && hasImplicitResult())
      { // return unit if this is expected even if we would implicitly return
        // something else:
        _expressions.add(new Block(new List<>()));
      }

    // we must not remove result expression just yet.
    // we rely on it being present in SourceModule.inScope()
    var idx = resultExpressionIndex();
    Expr resExpr = resultExpression();

    if (resExpr != null)
      {
        var x = resExpr.propagateExpectedType(res, outer, infix_colons, type);
        _expressions.remove(idx);
        _expressions.add(x);
      }
    else if (Types.resolved.t_unit.compareTo(type) != 0)
      {
        _expressions.add(new Call(pos(), "unit").resolveTypes(res, outer));
      }
    return this;
  }


  /**
   * Some Expressions do not produce a result, e.g., a Block that is empty or
   * whose last expression is not an expression that produces a result.
   */
  public boolean producesResult()
  {
    var expr = resultExpression();
    return expr != null && expr.producesResult();
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
            e.typeForInferencing() != null &&
            e.typeForInferencing().compareTo(Types.resolved.t_unit) != 0 &&
            !e.typeForInferencing().isVoid() &&
            e.typeForInferencing() != Types.t_ERROR)
          {
            AstErrors.unusedResult(e);
          }
      });

  }


}

/* end of file */
