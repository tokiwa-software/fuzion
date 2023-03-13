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
 * Source of class FeatureAndOuter
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;

import java.util.function.Predicate;

/**
 * FeatureAndOuter is a pair of an AbstractFeature f and an outer
 * Feature where it was found.  f might be inherited by outer, i.e.,
 * f.outer() == outer might not hold.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FeatureAndOuter extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * FeatureAndOuter instance returned in case of an error.
   */
  public static final FeatureAndOuter ERROR = new FeatureAndOuter(Types.f_ERROR,
                                                                  Types.f_ERROR,
                                                                  null);


  /*----------------------------  variables  ----------------------------*/


  /**
   * The feature contained in this instance.
   */
  public final AbstractFeature _feature;


  /**
   * The feature where _feature was found.  This might be _feature.outer(), but
   * it may as well be a heir of that.
   */
  public final AbstractFeature _outer;


  /**
   * if _outer is the outermost feature we where searching, _nextInner is
   * null. Otherwise, _nextInner is the next inner feature, e.g. in
   *
   *    a is
   *
   *      p is
   *
   *      b is
   *
   *        q is
   *
   *        say p      -- _nextInner for p will be b
   *        say q      -- _nextInner for q will be null
   *
   *        x is
   *          say p    -- _nextInner for p will be b
   *          say q    -- _nextInner for q will be x
   *
   *        fixed y is
   *          say p    -- _nextInner for p will be b
   *          say q    -- _nextInner for q will be y
   *
   * This is important since the type of `q` in `x` is
   * `a.this.type.b.this.type.q`, while `q` in `fixed y` is `a.this.type.b.q`.
   *
   */
  public final AbstractFeature _nextInner;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for given feature and outer.
   *
   * @param f the feature
   *
   * @param o the outer feature f was found in.
   */
  public FeatureAndOuter(AbstractFeature f,
                         AbstractFeature o,
                         AbstractFeature i)
  {
    this._feature = f;
    this._outer = o;
    this._nextInner = i;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * check if _nextInner exists and is fixed. If this is the case, we know that
   * the outer type is exact and cannot be a child (`.this.type`).
   */
  public boolean isNextInnerFixed()
  {
    return this._nextInner != null && this._nextInner.isFixed();
  }

  /**
   * For an access (call to or assignment to field), create an expression to
   * get the outer instance that contains the accessed feature.
   *
   * @param pos source code position of the access
   *
   * @param res Resolution instance
   *
   * @param cur the feature that contains the access.
   */
  Expr target(SourcePosition pos, Resolution res, AbstractFeature cur)
  {
    var t = new This(pos, cur, _outer);
    Expr result = t;
    if (cur.state() != Feature.State.RESOLVING_INHERITANCE)
      {
        var fcur = (Feature) cur; // NYI: cast to Feature!
        result = t.resolveTypes(res, fcur);
      }
    return result;
  }


  public static enum Operation {
      CALL("call"), ASSIGNMENT("assignment");

      final String opString;

      private Operation(final String opString)
      {
        this.opString = opString;
      }

      public String toString()
      {
        return this.opString;
      }
  }


  /**
   * Filter the features in given list to find an exact match for name or
   * a candidate.
   *
   * A predicate specifying what is an exact match is expected by this
   * function.
   *
   * If one feature f matches exactly or there is exactly one for which
   * isCandidate.test(f) holds, return that candidate. Otherwise, return null
   * if no candidate was found, or create an error and return Types.f_ERROR if
   * several candidates were found.
   *
   * @param l the list to filter
   *
   * @param isExact predicate to decide if a feature is an exact match.
   *
   * @param isCandidate predicate to decide if a feature is a candidate even
   * if its name is not an exact match.
   *
   * @return the list of found candidates, empty if no match was found, a list
   * of exactly one match, if an exact match was found, or, the list of all
   * candidates.
   */
  static List<FeatureAndOuter> findExactOrCandidate(List<FeatureAndOuter> l,
                                                    Predicate<FeatureName> isExact,
                                                    Predicate<AbstractFeature> isCandidate)
  {
    var match = false;
    var found = new List<FeatureAndOuter>();
    for (var fo : l)
      {
        var f = fo._feature;
        var fn = f.featureName();
        if (f.isChoice() && !f.isBaseChoice())
          {
            /* suppress call to choice type (e.g. bool : choice TRUE FALSE),
               except for (inheritance) calls to 'choice' */
          }
        else if (isExact.test(fn))  /* an exact match, so use it: */
          {
            if (CHECKS) check
              (Errors.count() > 0 ||
               !match ||
               fn.argCount() == 0 /* we might have several exact matches for fields */ ||
               found.get(0)._outer != fo._outer /* we might have several exact matches at different outer levels */
               );
            if (!match)
              {
                found = new List<>();
                match = true;
              }
            found.add(fo);
          }
        else if (!match && isCandidate.test(f))
          { /* no exact match, but we have a candidate to check later: */
            found.add(fo);
          }
      }

    return found;
  }


  /**
   * Filter the features in given list to find an exact match for name or
   * a candidate.
   *
   * If one feature f matches exactly or there is exactly one for which
   * isCandidate.test(f) holds, return that candidate. Otherwise, return null
   * if no candidate was found, or create an error and return Types.f_ERROR if
   * several candidates were found.
   *
   * @param l the list to filter
   *
   * @param pos source position of the access, for error reporting.
   *
   * @param operation "call" or "assignment", to be used in error messages
   *
   * @param name the name to search for an exact match
   *
   * @param isCandidate predicate to decide if a feature is a candidate even
   * if its name is not an exact match.
   *
   * @return if exactly one match was found, that match, null if no match was
   * found, an instance of FeatureAndOuter consisting of Types.f_ERROR for
   * feature and outer in case of several matches. An error is reported in this
   * case.
   */
  static FeatureAndOuter filter(List<FeatureAndOuter> l,
                                SourcePosition pos,
                                Operation operation,
                                FeatureName name,
                                Predicate<AbstractFeature> isCandidate)
  {
    var found = findExactOrCandidate(l,
                                     (FeatureName fn) -> fn.equalsExceptId(name),
                                     isCandidate);

    return switch (found.size())
      {
      case 0 -> null;
      case 1 -> found.get(0);
      default ->
        {
          AstErrors.ambiguousTargets(pos, operation, name, found);
          yield ERROR;
        }
      };
  }


  /**
   * human readable string, for debugging
   */
  public String toString()
  {
    return
      "[" + _feature.qualifiedName() +
      " found in " + _outer.qualifiedName() +
      (_nextInner == null ? " no next inner" : " next inner " + _nextInner.qualifiedName()) +
      (isNextInnerFixed() ? " fixed" : " not fixed") + "]";
  }


}

/* end of file */
