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
 * Source of class ParsedType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * A type that was created by the parser and
 * thus has a concrete position in the source code.
 */
public class ParsedType extends Type
{

  /**
   * The position in source code this type was
   * being parsed at.
   */
  private SourcePosition _pos;


  /**
   * Constructor
   *
   * @param pos
   *
   * @param name name of the parsed type
   *
   * @param generics the actual generic arguments
   *
   * @param outer outer type
   */
  public ParsedType(SourcePosition pos, String name, List<AbstractType> generics, AbstractType outer)
  {
    super(name, generics, outer);

    if (CHECKS) check
      (pos != null);

    _pos = pos;
    if (outer != null && outer.isRef())
      {
        AstErrors.outerTypeMayNotBeRefType(this);
        this.name = Errors.ERROR_STRING;
      }
  }


  /**
   * Constructor
   *
   * @param pos
   *
   * @param name name of the ParsedType
   *
   * @param generics the actual generic arguments
   *
   * @param outer outer type
   *
   * @param featureOfType if this type corresponds to a feature, then this is the
   * feature, else null.
   *
   * @param ref true iff this type should be a ref type, otherwise it will be a
   * value type.
   */
  public ParsedType(SourcePosition pos, String name, List<AbstractType> generics, AbstractType outer,
    AbstractFeature featureOfType, RefOrVal refOrVal)
  {
    super(name, generics, outer, featureOfType, refOrVal);

    if (CHECKS) check
      (pos != null);

    _pos = pos;
  }


  /**
   * Call Constructor for a function type that returns a result
   *
   * @param returnType the result type.
   *
   * @param arguments the arguments list
   *
   * @return a Type instance that represents this function
   */
  public static AbstractType funType(SourcePosition pos, AbstractType returnType, List<AbstractType> arguments)
  {
    if (PRECONDITIONS) require
      (returnType != null,
       pos != null,
       arguments != null);

    // This is called during parsing,
    // so Types.resolved.f_function is not set yet.
    return new ParsedType(pos, arguments.size() == 1 ? Types.UNARY_NAME: Types.FUNCTION_NAME,
      new List<AbstractType>(returnType, arguments),
      null);
  }


  public SourcePosition pos()
  {
    return _pos;
  }


  /**
   * Check if this.isOpenGeneric(). If so, create a compile-time error.
   *
   * @return true iff !isOpenGeneric()
   */
  @Override
  public boolean ensureNotOpen()
  {
    if (PRECONDITIONS)
      require(checkedForGeneric());

    if (isOpenGeneric())
      {
        AstErrors.illegalUseOfOpenFormalGeneric(pos(), genericArgument());
      }
    return !isOpenGeneric();
  }

}
