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

package dev.flang.ir;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Expr;
import dev.flang.ast.ExpressionVisitor;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.Types;
import dev.flang.util.Errors;
import dev.flang.util.SourcePosition;


/**
 * Tag is an expression that converts a value to a choice type, i.e., it adds a
 * tag to the value.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Tag extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * the original value instance.
   */
  public Expr _value;


  /**
   * The desired tagged type, set during creation.
   */
  public final AbstractType _taggedType;


  /**
   * The number to be used for tagging this value.
   */
  private final int _tagNum;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param value the value instance
   *
   * @param context the source code context where this Tag is to be used
   */
  Tag(Expr value, AbstractType taggedType)
  {
    if (PRECONDITIONS) require
      (value != null,
       taggedType.isChoice(),
       Errors.any()
        || taggedType
            .choiceGenerics()
            .stream()
            .filter(cg -> cg.isAssignableFromWithoutTagging(value.type()).yes())
            .count() == 1
        // NYI: UNDER DEVELOPMENT: why is value.type() sometimes unit
        // even though none of the choice elements is unit
        || value.type().compareTo(Types.resolved.t_unit) == 0
       );

    this._value = value;
    this._taggedType = taggedType;
    this._tagNum = (int)_taggedType
      .choiceGenerics()
      .stream()
      .takeWhile(cg -> cg.isAssignableFromWithoutTagging(value.type()).no())
      .count();
  }


  /*-----------------------------  methods  -----------------------------*/


  @Override
  public SourcePosition pos()
  {
    return _value.pos();
  }


  @Override
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'visit'");
  }


  /**
   * The number to be used for tagging this value.
   */
  public int tagNum()
  {
    return _tagNum;
  }


  @Override
  public AbstractType type()
  {
    return _taggedType;
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
