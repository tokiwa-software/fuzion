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
 * Source of class FeaturesAndOuter
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.SortedMap;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;


/**
 * FeaturesAndOuter is a set of features combined with an outer feature that
 * were found in outer and are candidates for the target of a call.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FeaturesAndOuter extends ANY
{

  public SortedMap<FeatureName, AbstractFeature> features;

  public AbstractFeature outer;


  /**
   * For an access (call to or assignment to field), create an expression to
   * get the outer instance that contains the accessed feature(s).
   *
   * @param pos source code position of the access
   *
   * @param res Resolution instance
   *
   * @param cur the feature that contains the access.
   */
  Expr target(SourcePosition pos, Resolution res, AbstractFeature cur)
  {
    var t = new This(pos, cur, outer);
    Expr result = t;
    if (cur.state() != Feature.State.RESOLVING_INHERITANCE)
      {
        var fcur = (Feature) cur; // NYI: cast to Feature!
        result = t.resolveTypes(res, fcur);
      }
    return result;
  }


  /**
   * Filter the features to find an exact match for name or a candidate.
   *
   * If one feature f matches exactly or there is exactly one for which
   * isCandidate.test(f) holds, return that candidate. Otherwise, return null
   * if no candidate was found, or create an error and return Types.f_ERROR if
   * several candidates were found.
   *
   * @param pos source position of the access, for error reporting.
   *
   * @param name the name to search for an exact match
   *
   * @param isCandidate predicate to decide if a feature is a candidate even
   * if its name is not an exact match.
   */
  AbstractFeature filter(SourcePosition pos, FeatureName name, java.util.function.Predicate<AbstractFeature> isCandidate)
  {
    var match = false;
    var found = new List<AbstractFeature>();
    for (var f : features.entrySet())
      {
        var ff = f.getValue();
        var fn = f.getKey();
        if (fn.equalsExceptId(name))  /* an exact match, so use it: */
          {
            check
              (Errors.count() > 0 || !match || fn.argCount() == 0);
            if (!match)
              {
                found = new List<>();
                match = true;
              }
            found.add(ff);
          }
        else if (!match && isCandidate.test(ff))
          { /* no exact match, but we have a candidate to check later: */
            found.add(ff);
          }
      }
    return switch (found.size())
      {
      case 0 -> null;
      case 1 -> found.get(0);
      default ->
      {
        AstErrors.ambiguousCallTargets(pos, name, found);
        yield Types.f_ERROR;
      }
      };
  }

}

/* end of file */
