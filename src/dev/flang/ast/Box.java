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

package dev.flang.ast;

import dev.flang.util.SourcePosition;


/**
 * Box is an expression that copies a value instance into a newly created
 * instance and returns a reference to the new copy.
 *
 * NYI: Box should not be part of AST, but part of the IR.
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
  public AbstractType _type;


  /**
   * Clazz index for value clazz that is being boxed and, at
   * _valAndRefClazzId+1, reference clazz that is the result clazz of the
   * boxing.
   */
  public int _valAndRefClazzId = -1;  // NYI: Used by dev.flang.be.interpreter, REMOVE!


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
       frmlT.isGenericArgument() || !value.type().isRef() || value.isCallToOuterRef());

    this._value = value;
    var t = value.type();
    this._type = frmlT.isGenericArgument() ? t : t.asRef();
  }


  /**
   * Constructor for Box loaded from .fum/MIR module file be front end.
   *
   * @param value the value to be boxed.
   */
  public Box(Expr value)
  {
    if (PRECONDITIONS) require
      (value != null,
       true /* NYI */ || !value.type().isRef() || value.isCallToOuterRef());

    this._value = value;
    var t = value.type();
    this._type = t.asRef();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  public SourcePosition pos()
  {
    return _value.pos();
  }


  /**
   * type returns the type of this expression or Types.t_ERROR if the type is
   * still unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or t_ERROR in case it is not known yet.
   */
  public AbstractType type()
  {
    return _type;
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
  public Box visit(FeatureVisitor v, AbstractFeature outer)
  {
    _value = _value.visit(v, outer);
    return this;
  }


  /**
   * visit all the statements within this Box.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited statements
   */
  public void visitStatements(StatementVisitor v)
  {
    super.visitStatements(v);
    _value.visitStatements(v);
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
