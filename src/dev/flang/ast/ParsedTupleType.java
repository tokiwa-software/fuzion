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
 * Source of class ParsedTupleType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.FuzionConstants;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.List;


/**
 * A ParsedTupleType is a type created by the parser for parsing `pTypeList` or via a call to {@link
 * ParsedTuple#asParsedType}.  During resolution, this will be replaced by ResolvedParametricType or
 * NormalType.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ParsedTupleType extends ParsedType
{


  /*----------------------------  variables  ----------------------------*/


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a parsed tuple type
   *
   * @param pos the source code position of the type
   *
   * @param generics list of type parameters
   */
  public ParsedTupleType(HasSourcePosition pos, List<AbstractType> generics)
  {
    super(pos,
          // provide dummy name such that we see if this is used accidentally:
          "unresolved tuple type",
          generics);
  }


  /*-----------------------------  methods  -----------------------------*/


  /** {@inheritDoc} */
  @Override
  UnresolvedType clone(AbstractFeature originalOuterFeature)
  {
    return
      new ParsedTupleType(pos(), generics())
      {
        AbstractFeature originalOuterFeature(AbstractFeature currentOuter)
        {
          return originalOuterFeature;
        }
      };
  }


  /** {@inheritDoc} */
  @Override
  public boolean mayBeFreeType()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public FeatureAndOuter lookupType(Resolution res,
                                    AbstractFeature outer,
                                    boolean traverseOuter,
                                    boolean ignoreAmbiguous,
                                    boolean ignoreNotFound)
  {
    if (Types.resolved != null)
      {
        return new FeatureAndOuter(generics().isEmpty() ? Types.resolved.f_unit
                                                        : Types.resolved.f_tuple,
                                   Types.resolved.universe,
                                   null);
      }
    else
      { // for compliation of `base.fum` only: lookup by name:
        _name = generics().isEmpty() ? FuzionConstants.UNIT_NAME
                                     : FuzionConstants.TUPLE_NAME;
        return super.lookupType(res, outer, traverseOuter, ignoreAmbiguous, ignoreNotFound);
      }
  }

}

/* end of file */
