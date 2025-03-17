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
 * Source of class FeatureName
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.TreeMap;
import java.util.Map;
import java.util.SortedMap;

import dev.flang.util.ANY;
import dev.flang.util.FuzionConstants;
import dev.flang.util.StringHelpers;


/**
 * FeatureName is a tuple consisting of the base name of a feature, which is a
 * string like "sort", "i32", "prefix -", "#result", etc., and the number of
 * formal arguments to that feature.
 *
 * The FeatureName may change when a feature is inherited by an heir class: If
 * the feature has an argument of an open generic type, the actual number of
 * arguments may change by replacing that argument by the actual generic
 * arguments.
 *
 * Also, renaming during inheritance might be requested explicitly.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FeatureName extends ANY implements Comparable<FeatureName>
{


  /*----------------------------  constants  ----------------------------*/


  /*------------------------  static variables  -------------------------*/


  /**
   * Global map of all base names to one FeatureName instance.  This is used to
   * set _baseNameId to avoid string comparison.
   */
  private static final Map<String, FeatureName> _allBaseNames_ = new TreeMap<>();


  /**
   * Global map of all FeatureName instances
   */
  private static final Map<FeatureName, FeatureName> _all_ = new TreeMap<>();


  /*----------------------------  variables  ----------------------------*/


  /**
   * The base name of this feature name.
   */
  private String _baseName;


  /**
   * The argument count of this feature name.
   */
  private int _argCount;


  /**
   * To distinguish several fields that mask one another, this gives an id for
   * fields with the same name.
   */
  public final int _id;


  /**
   * A unique id for each _baseName to avoid string comparison and use int
   * comparison instead.
   */
  private int _baseNameId = 0;

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for universe
   */
  private FeatureName(String baseName, int argCount, int id)
  {
    if (PRECONDITIONS) require
      (argCount == 0 || id == 0 || id == Integer.MAX_VALUE);

    _baseName = baseName;
    _argCount = argCount;
    _id       = id;
  }


  /*-------------------------  static methods  --------------------------*/


  /**
   * Get the unique element (baseName, argCount).
   */
  public static FeatureName get(String baseName, int argCount)
  {
    if (PRECONDITIONS) require
      (baseName != null,
       argCount >= 0,
       argCount < Integer.MAX_VALUE);  // not <= to allow MAX_VALUE to be used in getAll

    return get0(baseName, argCount, 0);
  }


  /**
   * Get the unique element (baseName, argCount, id).
   */
  public static FeatureName get(String baseName, int argCount, int id)
  {
    if (PRECONDITIONS) require
      (baseName != null,
       argCount >= 0,
       argCount < Integer.MAX_VALUE,  // not <= to allow MAX_VALUE to be used in getAll
       id >= 0,
       id < Integer.MAX_VALUE); // not <= to allow MAX_VALUE to be used in getAll

    return get0(baseName, argCount, id);
  }


  /**
   * Get the unique element (globalIndex, argCount, id) for a feature without a
   * name (such as outer refs, choice tags, loops).
   *
   * @param globalIndex a global, unique index into a module file
   *
   * @param argCount the argument count
   *
   * @param id the additional id
   */
  public static FeatureName get(int globalIndex, int argCount, int id)
  {
    if (PRECONDITIONS) require
      (globalIndex > 0,
       argCount >= 0,
       argCount < Integer.MAX_VALUE,  // not <= to allow MAX_VALUE to be used in getAll
       id >= 0,
       id < Integer.MAX_VALUE); // not <= to allow MAX_VALUE to be used in getAll

    var result = new FeatureName("", argCount, id);
    result._baseNameId = -globalIndex;
    return result;
  }


  /**
   * Get the unique element (baseName, argCount).
   */
  private static FeatureName get0(String baseName, int argCount, int id)
  {
    if (PRECONDITIONS) require
      (baseName != null,
       argCount >= 0);

    FeatureName n = new FeatureName(baseName, argCount, id);
    var bn = _allBaseNames_.get(baseName);
    if (bn == null)
      {
        n._baseNameId = _allBaseNames_.size() + 100;
        _allBaseNames_.put(baseName, n);
      }
    else
      {
        n._baseNameId = bn._baseNameId;
      }
    FeatureName result = _all_.get(n);
    if (result == null)
      {
        _all_.put(n, n);
        result = n;
      }
    return result;
  }


  /**
   * From a sorted map of FeatureName to some type T, get the submap of all the
   * FeatureNames with the given baseName.
   */
  public static <T> SortedMap<FeatureName, T> getAll(SortedMap<FeatureName, T> map, String baseName)
  {
    return map.subMap(get0(baseName, 0, 0),
                      get0(baseName, Integer.MAX_VALUE, 0) /* exclusive */
                      );
  }


  /**
   * From a sorted map of FeatureName to some type T, get the submap of all the
   * FeatureNames with the given baseName/argCount.
   */
  public static <T> SortedMap<FeatureName, T> getAll(SortedMap<FeatureName, T> map, String baseName, int argCount)
  {
    return map.subMap(get0(baseName, argCount, 0),
                      get0(baseName, argCount, Integer.MAX_VALUE) /* exclusive */
                      );
  }


  /*-----------------------------  methods  -----------------------------*/


  public int compareTo(FeatureName o)
  {
    if (PRECONDITIONS) require
      (_baseNameId != 0,
       o._baseNameId != 0);

    int result =_baseNameId - o._baseNameId;
    return
        result != 0              ? result
      : _argCount != o._argCount ? _argCount - o._argCount
                                 : _id - o._id;
  }


  public boolean equals(FeatureName o)
  {
    return compareTo(o) == 0;
  }


  public String baseName()
  {
    return isNameless() ? "@"+(-_baseNameId) : _baseName;
  }


  public String baseNameHuman()
  {
    var n = baseName();
    return
      n.startsWith(FuzionConstants.UNDERSCORE_PREFIX)                  ? "_"          :
      n.startsWith(FuzionConstants.LAMBDA_PREFIX)                      ? "λ"        :
      n.startsWith(FuzionConstants.ANONYMOUS_FEATURE_PREFIX)           ? "anonymous"  :
      n.startsWith(FuzionConstants.REC_LOOP_PREFIX)                    ? (n.contains("else") ? "else" : "loop") :
      n.startsWith(FuzionConstants.EXPRESSION_RESULT_PREFIX) ||
      n.startsWith(FuzionConstants.INTERNAL_RESULT_NAME)               ? "result"     :
      n.startsWith(FuzionConstants.PREBOOLCONDITION_FEATURE_PREFIX   ) ? n.replaceFirst(FuzionConstants.PREBOOLCONDITION_FEATURE_PREFIX    + ".*#", "pre "    ) :
      n.startsWith(FuzionConstants.PREANDCALLCONDITION_FEATURE_PREFIX) ? n.replaceFirst(FuzionConstants.PREANDCALLCONDITION_FEATURE_PREFIX + ".*#", "precall ") :
      n.startsWith(FuzionConstants.PRECONDITION_FEATURE_PREFIX       ) ? n.replaceFirst(FuzionConstants.PRECONDITION_FEATURE_PREFIX        + ".*#", "pre "    ) :
      n.startsWith(FuzionConstants.POSTCONDITION_FEATURE_PREFIX      ) ? n.replaceFirst(FuzionConstants.POSTCONDITION_FEATURE_PREFIX       + ".*#", "post "   ) :
      n.endsWith(FuzionConstants.TYPE_NAME)                            ? n.replace("." + FuzionConstants.TYPE_NAME, "") :
      n.startsWith(FuzionConstants.ITER_ARG_PREFIX_INIT)               ? n.replace(FuzionConstants.ITER_ARG_PREFIX_INIT, "") :
      n.startsWith(FuzionConstants.ITER_ARG_PREFIX_NEXT)               ? n.replace(FuzionConstants.ITER_ARG_PREFIX_NEXT, "") :
      n;
  }


  public int argCount()
  {
    return _argCount;
  }


  public String argCountAndIdString()
  {
    return " (" + StringHelpers.argumentsString(_argCount) + (_id > 0 ? "," + _id : "") + ")";
  }


  public String toString()
  {
    return baseNameHuman() + argCountAndIdString();
  }


  public boolean equalsExceptId(FeatureName o)
  {
    return _baseName.equals(o._baseName) && _argCount == o._argCount;
  }


  /**
   * Compare just the baseName of the given FeatureNames.
   *
   * This is used in AstErrors.solutionWrongArgumentNumber.
   */
  public boolean equalsBaseName(FeatureName o)
  {
    return _baseName.equals(o._baseName);
  }


  /**
   * Reset static fields
   */
  public static void reset()
  {
    _all_.clear();
    _allBaseNames_.clear();
  }


  /**
   * Returns true iff the name of the feature is an internal name, i.e. it starts with the
   * internal name prefix.
   */
  public boolean isInternal()
  {
    return baseName().startsWith(FuzionConstants.INTERNAL_NAME_PREFIX);
  }


  /**
   * Returns true if this FeatureName is unspecified, i.e. an "@"-name would be generated
   * by baseName above.
   */
  public boolean isNameless()
  {
    return _baseNameId < 0;
  }

}

/* end of file */
