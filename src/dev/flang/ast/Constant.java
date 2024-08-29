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
 * Source of class SourceConstant
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.SourcePosition;


/**
 * Constant represents a constant in the source code such as '3.14', 'true',
 * '"Hello"'.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class Constant extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  private final SourcePosition _pos;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a Constant loaded from library
   * with source position loaded on demand only.
   */
  public Constant()
  {
    _pos = SourcePosition.notAvailable;
  }


  /**
   * Constructor for a Constant at the given source code position.
   *
   * @param pos the sourcecode position, used for error messages.
   */
  public Constant(SourcePosition pos)
  {
    if (PRECONDITIONS) require
      (pos != null);

    this._pos = pos;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Serialized form of the data of this constant.
   */
  public abstract byte[] data();


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  public SourcePosition pos()
  {
    return _pos;
  }


  /**
   * The type of the constant.  This may be different to the the user-visible
   * `type()` of this constant, in particular, for a constant string, `type()`
   * returns `String`, while `typeOfConstant` is the actual child of `String`
   * used for constants: `Const_String`.
   *
   * @return the type to be used to create the constant value. Is assignment
   * compatible to `type()`.
   */
  public AbstractType typeOfConstant()
  {
    return type();
  }


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this or an alternative Expr if the action performed during the
   * visit replaces this by the alternative.
   */
  @Override
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    v.action(this);
    return this;
  }


  /**
   * Check that this constant is in the range allowed for its type().
   *
   * This is redefined by NumLiteral to check the range of the constant.
   */
  void checkRange()
  {
  }


  /**
   * This expression as a compile time constant.
   */
  @Override
  public Constant asCompileTimeConstant()
  {
    return this;
  }


  /**
   * Origin of this constant. This is either this constant itself for a
   * BoolConst, NumLiteral or StrConst, or the instance of AbstractCall or
   * InlineArray this was created from.
   */
  public Expr origin()
  {
    return this;
  }

}

/* end of file */
