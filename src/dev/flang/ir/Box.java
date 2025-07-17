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
 * Source of class Box
 *
 *---------------------------------------------------------------------*/

package dev.flang.ir;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Expr;
import dev.flang.ast.ExpressionVisitor;
import dev.flang.ast.FeatureVisitor;
import dev.flang.util.SourcePosition;


/**
 * Box is an expression that copies a value instance into a newly created
 * instance and returns a reference to the new copy.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Box extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * the original value instance.
   */
  public Expr _value;


  /**
   * The type of this, set during creation.
   */
  private final AbstractType _type;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param value the value instance
   *
   * @param frmlT formal type the Expr should result in
   */
  public Box(Expr value, AbstractType frmlT)
  {
    if (PRECONDITIONS) require
      (value != null,
       !frmlT.containsUndefined(false),
       frmlT.isGenericArgument() || frmlT.isThisType() || !value.type().isRef() || value.isCallToOuterRef(),
       !(value instanceof Box));

    this._value = value;
    this._type = frmlT;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  public SourcePosition pos()
  {
    return _value.pos();
  }


  @Override
  public AbstractType type()
  {
    return _type;
  }


  @Override
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'visit'");
  }


  /**
   * visit all the expressions within this Box.
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
    return "box(" + _value + ")";
  }

}

/* end of file */
