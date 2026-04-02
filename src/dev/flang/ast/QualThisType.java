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
 * Source of class QualThisType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Optional;
import java.util.stream.Collectors;

import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Type created by parser for types like {@code a.b.this}.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class QualThisType extends UnresolvedType
{

  /*--------------------------  constructors  ---------------------------*/


  private final List<ParsedName> _qual;


  /**
   * Create the type corresponding to {@code <qual>.this}.
   *
   * @param qual the qualifier
   */
  public QualThisType(List<ParsedName> qual)
  {
    _qual = qual;
    super(SourcePosition.range(qual),
          qual.getLast()._name,
          Call.NO_GENERICS, null, Optional.of(TypeKind.ThisType));
  }



  /**
   * resolve this type, i.e., find or create the corresponding instance of
   * ResolvedType of this and all outer types and type arguments this depends on.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param context the outer feature this type is declared in. Lookup of
   * unqualified types will happen in this feature.
   *
   * @param tolerant behavior if resolution is not possible
   *                 if true return null, if false flag error
   */
  @Override
  AbstractType resolve(Resolution res, Context context, boolean tolerant)
  {
    if (PRECONDITIONS) require
      (res != null,
       context != null);

    var outer = context.outerFeature();
    res.resolveDeclarations(outer);

    // The following code is
    // for resolving fully qualified this-types.
    if (!tolerant && _qual.size() > 1)
      {
        var found = new List<FeatureAndOuter>();
        var cur = context.outerFeature();
        do
          {
            var fo = res._module.lookupType(pos(), cur, _name, false, false, true);
            if (fo != null &&
                fo._feature
                  .qualifiedName()
                  .endsWith(_qual.stream().filter(x -> !x._name.equals(FuzionConstants.UNIVERSE_NAME))
                  .map(x -> x._name)
                  .collect(Collectors.joining("."))))
              {
                found.add(fo);
              }
            cur = cur.outer();
          }
        while (cur != null);

        if (found.size() == 1 || found.size() > 1 && _qual.getFirst()._name.equals(FuzionConstants.UNIVERSE_NAME))
          {
            _resolved = found.getLast()._feature.thisType();
          }
        else if (!found.isEmpty())
          {
            AstErrors.ambiguousType(pos(), _name, found.map2(x -> x._feature));
            _resolved = Types.t_ERROR;
          }
      }

    return super.resolve(res, context, tolerant);
  }


}

/* end of file */
