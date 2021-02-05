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
 * Tokiwa GmbH, Berlin
 *
 * Source of class DynamicBinding
 *
 *---------------------------------------------------------------------*/

package dev.flang.ir;

import java.util.Map;
import java.util.TreeMap;

import dev.flang.ast.Feature; // NYI: remove dependency!
import dev.flang.ast.Types; // NYI: remove dependency!

import dev.flang.util.ANY;


/**
 * DynamicBinding provides a means to lookup a dynamically bound feature in a
 * call or the offset of a field that is accessed.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class DynamicBinding extends ANY
{

  /*----------------------------  variables  ----------------------------*/


  /**
   * The surrounding clazz.
   */
  final Clazz _clazz;


  /**
   * Offsets of fields in instances of _clazz.
   *
   * NYI: For now, this uses a simple TreeMap. For efficiency, this should use
   * an efficient table, e.g. coloring as described in
   * https://onlinelibrary.wiley.com/doi/abs/10.1002/spe.1022
   */
  Map<Feature, Integer> _offsets = new TreeMap<>();


  /**
   * Actual callables when calling a dynamically bound feature on _clazz.
   *
   * NYI: like _offsets, this should use a more efficient lookup table.
   */
  Map<Feature, BackendCallable> _callables = new TreeMap<>();


  /**
   * Actual inner clazzes when calling a dynamically bound feature on _clazz.
   *
   * NYI: like _offsets, this should use a more efficient lookup table.
   */
  Map<Feature, Clazz> _inner = new TreeMap<>();


  /**
   * Actual outer clazzes when calling a dynamically bound feature on _clazz.
   *
   * NYI: like _offsets, this should use a more efficient lookup table.
   */
  Map<Feature, Clazz> _outer = new TreeMap<>();


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param type
   *
   * @param outer
   */
  public DynamicBinding(Clazz cl)
  {
    if (PRECONDITIONS) require
      (cl != null);

    this._clazz = cl;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Record the offset of the given field in the corresponding clazz.
   *
   * @param f a inherited or defined field in _clazz.
   *
   * @param offset the offset of this field in an instance of _clazz.
   */
  public void addFieldOffset(Feature f, int offset)
  {
    if (PRECONDITIONS) require
      (f != null);

    _offsets.put(f, offset);

    if (POSTCONDITIONS) ensure
      (fieldOffset(f) == offset);
  }


  /**
   * Record a callable corresponding the the given feature in the corresponding
   * clazz.
   *
   * @param f a feature inherited or defined in _clazz.
   *
   * @param callable backend data needed to perform a call to the actual
   * implementation of f in _clazz.
   */
  public void addCallable(Feature f, BackendCallable callable)
  {
    if (PRECONDITIONS) require
      (f != null);

    _callables.put(f, callable);

    if (POSTCONDITIONS) ensure
      (callable(f) == callable);
  }


  /**
   * Record inner and outer clazz for a call corresponding the the given feature
   * in the corresponding clazz.
   *
   * @param f a feature inherited or defined in _clazz.
   *
   * @param callable backend data needed to perform a call to the actual
   * implementation of f in _clazz.
   */
  public void add(Feature f, Clazz inner, Clazz outer)
  {
    if (PRECONDITIONS) require
      (f != null);

    _inner.put(f, inner);
    _outer.put(f, outer);

    if (POSTCONDITIONS) ensure
      (inner(f) == inner,
       outer(f) == outer);
  }


  /**
   * Look up the offset storef for field f using addFieldOffset.
   *
   * @return the offset of f stored using addFieldOffset.
   */
  public int fieldOffset(Feature f)
  {
    return _offsets.get(f);
  }


  /**
   * Look up the value stored for calledFeature using addCallable.
   *
   * @param calledFeature the static feature that is called.
   *
   * @return the backend data identifying the actual callable feature.
   */
  public BackendCallable callable(Feature calledFeature)
  {
    return _callables.get(calledFeature);
  }


  /**
   * Look up the value stored for inner using add
   *
   * @param calledFeature the static feature that is called.
   *
   * @return the innr class stored for calledFeature using add, null if none
   */
  public Clazz inner(Feature calledFeature)
  {
    return _inner.get(calledFeature);
  }


  /**
   * Look up the value stored for outner using add
   *
   * @param calledFeature the static feature that is called.
   *
   * @return the outer class stored for calledFeature using add, null if none
   */
  public Clazz outer(Feature calledFeature)
  {
    return _outer.get(calledFeature);
  }

}

/* end of file */
