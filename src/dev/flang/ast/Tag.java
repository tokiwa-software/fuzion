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
 * Source of class Tag
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.Errors;
import dev.flang.util.List;


/**
 * Tag is an expression that converts a value to a choice type, i.e., it adds a
 * tag to the value.
 *
 * NYI: Tag should not be part of AST, but part of the IR.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Tag extends ExprWithPos
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * the original value instance.
   */
  public Expr _value;


  /**
   * The desired tagged type, set during creation.
   */
  public AbstractType _taggedType;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param value the value instance
   */
  public Tag(Expr value, AbstractType taggedType, AbstractFeature outer, Context context)
  {
    super(value.pos());

    // NYI: Move to check types phase
    taggedType.checkChoice(value.pos(), outer, context);

    if (PRECONDITIONS) require
      (value != null,
       taggedType.isChoice(),
       Errors.any()
        || taggedType
            .choiceGenerics(outer, context)
            .stream()
            .filter(cg -> cg.isDirectlyAssignableFrom(value.type(), outer, context))
            .count() == 1
        // NYI why is value.type() sometimes unit
        // even though none of the choice elements is unit
        || value.type().compareTo(Types.resolved.t_unit) == 0
       );
    this._value = value;
    this._taggedType = taggedType;
  }


  /*-----------------------------  methods  -----------------------------*/


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
    return _taggedType;
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
  public Tag visit(FeatureVisitor v, AbstractFeature outer)
  {
    var o = _value;
    _value = _value.visit(v, outer);
    if (CHECKS) check
      (o.type().compareTo(_value.type()) == 0);
    v.action(this, outer);
    return this;
  }


  /**
   * visit all the expressions within this Tag.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited expressions
   */
  public void visitExpressions(ExpressionVisitor v)
  {
    super.visitExpressions(v);
    _value.visitExpressions(v);
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "tag(" + _value + ")";
  }

}

/* end of file */
