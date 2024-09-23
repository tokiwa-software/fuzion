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
 * Source of interface AbstractModule
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.SortedMap;


/**
 * AbstractModule provides feature lookup needed for Types.Resolved.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public interface AbstractModule
{


  /**
   * Get declared features for given outer Feature as seen by this module.
   * Result is never null.
   */
  SortedMap<FeatureName, AbstractFeature>declaredFeatures(AbstractFeature outer);

}

/* end of file */
