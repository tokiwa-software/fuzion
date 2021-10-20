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
 * Source of class MIR
 *
 *---------------------------------------------------------------------*/

package dev.flang.mir;

import dev.flang.ast.Feature;  // NYI: Remove dependency!

import dev.flang.ir.IR;

import dev.flang.util.ANY;
import dev.flang.util.Map2Int;
import dev.flang.util.MapComparable2Int;


/**
 * The MIR contains the module-intermediate representation of a Fuzion module.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class MIR extends IR
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The main feature
   */
  final Feature _main;


  /**
   * integer ids for features in this module
   */
  final Map2Int<Feature> _featureIds = new MapComparable2Int(FEATURE_BASE);


  /*--------------------------  constructors  ---------------------------*/


  public MIR(Feature main)
  {
    _main = main;
    addFeatures();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The main Feature.
   */
  public Feature main()
  {
    return _main;
  }


  /**
   * Create the mapping from Features to integers exists.
   */
  private void addFeatures()
  {
    if (_featureIds.size() == 0)
      {
        var u = main().universe();
        addFeatures(u);
      }
  }


  /**
   * Helper to addFeatures() to add feature f and all features declared within f.
   */
  private void addFeatures(Feature f)
  {
    _featureIds.add(f);
    for (var i : f.declaredFeatures().values())
      {
        addFeatures(i);
      }
  }


  /**
   * The first feature in this module.
   */
  public int firstFeature()
  {
    return FEATURE_BASE;
  }


  /**
   * The last feature in this module.
   */
  public int lastFeature()
  {
    return FEATURE_BASE + _featureIds.size() - 1;
  }

}

/* end of file */
