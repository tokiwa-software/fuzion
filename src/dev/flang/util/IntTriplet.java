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
 * Source code of class IntTriplet
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.util.Comparator;


/**
 * IntTriplet contains three int values.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public record IntTriplet(int v0, int v1, int v2)
{

  /**
   * Comparator that defines a total order for IntTriplet.
   */
  public static Comparator<IntTriplet> _comparator_ = new Comparator<IntTriplet>()
    {
      public int compare(IntTriplet a, IntTriplet b)
      {
        return a.v0 != b.v0 ? Integer.compare(a.v0, b.v0) :
               a.v1 != b.v1 ? Integer.compare(a.v1, b.v1)
                            : Integer.compare(a.v2, b.v2);
      }
    };

}
/* end of file */
