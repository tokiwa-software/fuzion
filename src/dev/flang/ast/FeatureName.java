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
import java.util.NavigableMap;
import java.util.SortedMap;

import dev.flang.util.ANY;
import dev.flang.util.Errors;


/**
 * FeatureName is a tuple consisting of the base name of a feature, which is a
 * string like "sort", "i32", "prefix -", "#result", etc., and the number of
 * formal arguments to that feature.
 *
 * The FeatureName may change when a feature is inherited by a heir class: If
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


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for universe
   */
  private FeatureName(String baseName, int argCount, int id)
  {
    _baseName = baseName;
    _argCount = argCount;
    _id    = id;
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
   * Get the unique element (baseName, argCount).
   */
  private static FeatureName get0(String baseName, int argCount, int id)
  {
    if (PRECONDITIONS) require
      (baseName != null,
       argCount >= 0);

    FeatureName n = new FeatureName(baseName, argCount, id);
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
    int result = _baseName.compareTo(o._baseName);
    return
        result != 0             ? result
      : _argCount < o._argCount ? -1 : _argCount > o._argCount ? +1
      : _id       < o._id       ? -1 : _id       > o._id       ? +1
                                : 0;
  }


  public boolean equals(FeatureName o)
  {
    return compareTo(o) == 0;
  }


  public String baseName()
  {
    return _baseName;
  }


  public int argCount()
  {
    return _argCount;
  }

  public String argCountAndIdString()
  {
    return " (" + Errors.argumentsString(_argCount) + (_id > 0 ? "," + _id : "") + ")";
  }

  public String toString()
  {
    return _baseName + argCountAndIdString();
  }

  public boolean equalsExceptId(FeatureName o)
  {
    return _baseName.equals(o._baseName) && _argCount == o._argCount;
  }

}

/* end of file */
