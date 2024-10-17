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
 * Source of class FeatureAndActuals
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir;

import dev.flang.ast.AbstractCall; // NYI: remove dependency!
import dev.flang.ast.AbstractFeature; // NYI: remove dependency!
import dev.flang.ast.AbstractType; // NYI: remove dependency!

import dev.flang.util.ANY;
import dev.flang.util.List;


/**
 * FeatureAndActuals represents a tuple consisting of an AbstractFeature
 * combined with a list of actual type parameters for that feature.
 *
 * Instances of this are used as the key for the set of inner clazzes in a Clazz
 * since for this inner clazz to exist, there must be a call with actual type
 * parameters.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FeatureAndActuals extends ANY implements Comparable<FeatureAndActuals>
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The feature part of this triplet.
   */
  public final AbstractFeature _f;


  /**
   * The type parameters of this triplet.
   *
   * This may be null to create a dummy instance that is less (or more) than all
   * instances for the same feature. This is useful for creating sub-sets.
   */
  public final List<AbstractType> _tp;


  /**
   * in case _tp is null, this selects if this instance should be less (false)
   * or more (true) than all instances of the same feature.
   * This is used to extract a subset - containing all FeatureAndActuals having
   * the same feature - from a Set of FeatureAndActuals.
   */
  public final boolean _max;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create a tuple for given feature and type parameters.
   *
   * @param f the underlying feature, must not be null
   *
   * @param tp the actual type parameters, never null.
   */
  public FeatureAndActuals(AbstractFeature f, List<AbstractType> tp)
  {
    if (PRECONDITIONS) require
      (f != null,
       tp != null,
       f.generics().sizeMatches(tp));

    _f = f;
    _tp = tp;
    _max = false; /* unused */
  }


  /**
   * Convenience constructor for empty type parameters.
   *
   * @param f the underlying feature, must not be null
   */
  public FeatureAndActuals(AbstractFeature f)
  {
    this(f, AbstractCall.NO_GENERICS);
  }


  /**
   * Create a dummy tuple for given feature that is less or larger than the given feature.
   *
   * @param f the underlying feature, must not be null
   *
   * @param max false iff this should be less than instances of the same
   * feature, false for this to be larger.
   */
  public FeatureAndActuals(AbstractFeature f, boolean max)
  {
    _f = f;
    _tp = null;
    _max = max;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Compare this with another instance.
   *
   * @param oo other instance
   *
   * @return -1, 0, or +1
   */
  public int compareTo(FeatureAndActuals oo)
  {
    var o = (FeatureAndActuals) oo;
    var r = _f.compareTo(o._f);
    if (r == 0 && (_tp == null || o._tp == null))
      { // special handling for min / max:
        r = _tp == null && o._tp == null && _max == o._max ? 0  :
            _tp == null ? (  _max ? +1 : -1)
                        : (o._max ? -1 : +1);
      }
    else if (r == 0)
      {
        var sz1 = _tp.size();
        var sz2 = o._tp.size();
        if (sz1 < sz2)
          {
            r = -1;
          }
        else if (sz1 > sz2)
          {
            r = +1;
          }
        else
          {
            for (int i = 0; r == 0 && i < sz1; i++)
              {
                r = _tp.get(i).compareTo(o._tp.get(i));
              }
          }
      }
    return r;
  }


  /**
   * Convert this to a string for debugging:
   */
  public String toString()
  {
    return
      (_f == null ? "--" : _f.qualifiedName()) +
      (_tp != null ? _tp.toString(t -> " " + t.asStringWrapped(true))
                   : (_max ? " MAX" : " MIN"));
  }

}

/* end of file */
