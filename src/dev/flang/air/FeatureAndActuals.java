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

package dev.flang.air;

import dev.flang.ast.AbstractCall; // NYI: remove dependency!
import dev.flang.ast.AbstractFeature; // NYI: remove dependency!
import dev.flang.ast.AbstractType; // NYI: remove dependency!

import dev.flang.util.ANY;
import dev.flang.util.List;


/**
 * FeatureAndActuals represents a triplet consisting of an AbstractFeature
 * combined with a list of actual type parameters for that feature and a flag
 * selecting between the feature itself of the precondition of this feature.
 *
 * Instances of this are used as the key the the set of inner clazzes in a Clazz
 * since for this inner clazz to exist, there must be a call iwth actual type
 * parameters and this call may be either to the feature itself or to its
 * precondition.
 *
 * Note that a feature that is abstract may still see calls to its precondition.
 * Similarly, a call to a precondition may be performed on a boxed target
 * instance, while the actual inner clazz will end up calling the feature on the
 * unboxed value.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FeatureAndActuals extends ANY implements Comparable
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
   * The precondition flag of this triplet.
   */
  public final boolean _preconditionClazz;

  /**
   * in case _tp is null, this selects if this instance should be less (false)
   * or larger (true) than all instances of the same feature.
   */
  public final boolean _max;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create a tuple for given feature, type parameters and precondition flag.
   *
   * @param f the underlying feature, must not be null
   *
   * @param tp the actual type parameters, never null.
   *
   * @param preconditionClazz true iff precondition of f is to be called
   */
  public FeatureAndActuals(AbstractFeature f, List<AbstractType> tp, boolean preconditionClazz)
  {
    if (PRECONDITIONS) require
      (f != null,
       tp != null);

    _f = f;
    _tp = tp;
    _preconditionClazz = preconditionClazz;
    _max = false; /* unused */
    }


  /**
   * Convenience constructor for empty type parameters and not precondition clazz.
   *
   * @param f the underlying feature, must not be null
   */
  public FeatureAndActuals(AbstractFeature f)
  {
    this(f, AbstractCall.NO_GENERICS, false);
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
    _preconditionClazz = false; /* unused */
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
  public int compareTo(Object oo)
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
        r = _preconditionClazz == o._preconditionClazz ?  0 :
            _preconditionClazz                         ? -1
                                                       : +1;
        if (r == 0)
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
      }
    return r;
  }

}

/* end of file */
