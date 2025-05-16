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
 * Source of class FunctionReturnType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.SourcePosition;

/**
 * FunctionReturnType represents the type of the value returned by a function.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FunctionReturnType extends ReturnType
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The parsed type.
   */
  private AbstractType _type;


  /**
   * When available, the source code position of the unresolved return
   * type. SourcePosition.builtIn if not available.
   */
  private final SourcePosition _pos;

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param t the parsed type
   */
  public FunctionReturnType(AbstractType t)
  {
    _type = t;
    _pos = t instanceof UnresolvedType ut ? ut.pos() : SourcePosition.builtIn;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * true iff this is the return type of a constructor feature, i.e., "this" is
   * returned implicitly instead of "result".
   *
   * @return true for a constructor return type, false for a function.
   */
  public boolean isConstructorType()
  {
    return false;
  }


  /**
   * For a function, the return type.
   *
   * @return the function return type.
   */
  public AbstractType functionReturnType()
  {
    return _type == Types.t_UNDEFINED
      ? null
      : _type;
  }


  /**
   * For a function, the source code position of the return type.
   *
   * @return the function return type source position.
   */
  public SourcePosition functionReturnTypePos()
  {
    if (PRECONDITIONS) require
      (!isConstructorType());

    return _pos;
  }


  /**
   * If this has a source code position, return it
   *
   * @return the source position or null if there is none (e.g. for NoType).
   */
  @Override
  public SourcePosition posOrNull()
  {
    return _pos;
  }


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   */
  public void visit(FeatureVisitor v, AbstractFeature outer)
  {
    _type = _type.visit(v, outer);
  }


  /**
   * Resolve the type this function returns. This is needed to resolve free
   * types used in an argument type, which change the number of type parameters
   * in a call.
   *
   * @param res the resolution instance
   *
   * @param outer the outer feature, which is the argument this is the result
   * type of.
   */
  void resolveArgumentType(Resolution res, Feature outer)
  {
    if (PRECONDITIONS) require
      (outer.isArgument(),
       this == outer.returnType());

    res.resolveDeclarations(outer);
    _type = _type.resolve(res, outer.context());
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return _type.toString(true);
  }


}

/* end of file */
