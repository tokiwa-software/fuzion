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

import dev.flang.util.SourcePosition;


/**
 * Tag is an expression that converts a value to a choice type, i.e., it adds a
 * tag to the value.
 *
 * NYI: Tag should not be part of AST, but part of the IR.
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
  public AbstractType _taggedType;


  /**
   * Clazz index for value clazz that is being boxed and, at
   * _valAndRefClazzId+1, reference clazz that is the result clazz of the
   * boxing.
   */
  public int _valAndTaggedClazzId = -1;  // NYI: Used by dev.flang.be.interpreter, REMOVE!


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param value the value instance
   */
  public Tag(Expr value, AbstractType taggedType)
  {
    super();

    if (PRECONDITIONS) require
      (value != null,
       taggedType.isChoice(),
       taggedType
         .choiceGenerics()
         .stream()
         .filter(cg -> cg.isDirectlyAssignableFrom(value.type()))
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
    return _taggedType;
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
  public Tag visit(FeatureVisitor v, AbstractFeature outer)
  {
    _value = _value.visit(v, outer);
    v.action(this, outer);
    return this;
  }


  /**
   * visit all the statements within this Tag.
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
    return "tag(" + _value + ")";
  }

}

/* end of file */
