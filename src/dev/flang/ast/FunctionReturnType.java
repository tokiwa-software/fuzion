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
    if (PRECONDITIONS) require
      (t != null);

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
  @Override
  boolean isConstructorType()
  {
    return false;
  }


  /**
   * For a function, the return type.
   *
   * @return the function return type.
   */
  @Override
  AbstractType functionReturnType(boolean allowIncomplete)
  {
    return _type == Types.t_UNDEFINED || _type.isIncompleteType() && !allowIncomplete
      ? null
      : _type;
  }


  /**
   * If this has a source code position, return it
   *
   * @return the source position or null if there is none (e.g. for NoType).
   */
  @Override
  SourcePosition posOrNull()
  {
    return _pos;
  }


  /**
   * @see dev.flang.ast.ReturnType#resolveType(dev.flang.ast.Resolution, dev.flang.ast.Context)
   */
  @Override
  void resolveType(Resolution res, Context context)
  {
    _type = _type.resolve(res, context);
  }

  /**
   * @see dev.flang.ast.ReturnType#checkType(dev.flang.ast.Context)
   */
  @Override
  void checkType(Context context)
  {
    _type.selfOuterAndGenerics(t -> {
      switch (t.kind()) {
        case RefType, ValueType -> AbstractType.checkActualTypePars(context, t.feature(), t.generics(), t.originalGenericsPos(), _pos, x->x);
        case ThisType -> t.checkLegalThisType(_pos, context);
        case ParametricType -> {}
      }
    });
  }


  /**
   * toString
   *
   * @return
   */
  @Override
  public String toString()
  {
    return _type.toString(true);
  }


}

/* end of file */
