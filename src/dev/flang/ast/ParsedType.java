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

import java.util.Optional;

import dev.flang.util.HasSourcePosition;
import dev.flang.util.List;


/**
 * A ParsedType is a type as it was created by the parser. During resolution,
 * this will be replaced by ResolvedParametricType or NormalType.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ParsedType extends UnresolvedType
{


  /*----------------------------  variables  ----------------------------*/


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a parsed type
   *
   * @param pos the source code position of the type
   *
   * @param name the name of the type
   *
   * @param generics list of type parameters
   *
   * @param outer outer type or null if unqualified.
   */
  public ParsedType(HasSourcePosition pos, String name, List<AbstractType> generics, AbstractType outer)
  {
    super(pos, name, generics, outer);
  }


  /**
   * Constructor for a parsed type to be called by BuiltInType's constructor.
   *
   * @param pos the source code position of the type
   *
   * @param name the name of the type
   *
   * @param generics list of type parameters
   *
   * @param outer outer type or null if unqualified.
   */
  ParsedType(HasSourcePosition pos, String name, List<AbstractType> generics, AbstractType outer, Optional<TypeKind> rov)
  {
    super(pos, name, generics, outer, rov);
  }


  /**
   * Create a clone of original that uses originalOuterFeature as context to
   * look up features the type is built from.
   *
   * @param original the original value type
   *
   * @param originalOuterFeature the original feature, which is not a type
   * feature.
   */
  ParsedType(UnresolvedType original, AbstractFeature originalOuterFeature)
  {
    super(original, originalOuterFeature);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create a clone of this Type that uses originalOuterFeature as context to
   * look up features the type is built from.  Generics will be looked up in the
   * current context.
   *
   * This is used for type features that use types from the original feature,
   * but needs to replace generics by the type feature's generics.
   *
   * @param originalOuterFeature the original feature, which is not a type
   * feature.
   */
  UnresolvedType clone(AbstractFeature originalOuterFeature)
  {
    return
      new ParsedType(this, originalOuterFeature)
      {
        AbstractFeature originalOuterFeature(AbstractFeature currentOuter)
        {
          return originalOuterFeature;
        }
      };
  }


  /**
   * May this unresolved type be a free type. This is the case for explicit free
   * types such as {@code X : Any}, and for all normal types like {@code XYZ} that are not
   * qualified by an outer type {@code outer.XYZ} and that do not have actual type
   * parameters {@code XYZ T1 T2} and that are not boxed.
   */
  public boolean mayBeFreeType()
  {
    return
      outer() == null      &&
      generics().isEmpty() &&
      _typeKind.isEmpty();
  }


  /**
   * For a type {@code XYZ} with mayBeFreeType() returning true, this gives the name
   * of the free type, which would be {@code "XYZ"} in this example.
   *
   * @return the name of the free type, which becomes the name of the type
   * parameter created for it.
   */
  public String freeTypeName()
  {
    return name();
  }


}

/* end of file */
