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
 * Source of class Site
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;

import java.util.TreeSet;

import dev.flang.util.ANY;


/**
 * Site represents a code position that accesses a feature, i.e., it is
 * either a call site or the site of an assignment.  A Site is a
 * combination of a clazz, code block and instruction index.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Site extends ANY implements Comparable<Site>
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The site's clazz' id.
   */
  int _cl;


  /**
   * The site's code block.
   */
  int _c;


  /**
   * The index in _c.
   */
  int _i;


  /**
   * set of features accessed at this site.
   */
  TreeSet<Integer> _accesses = new TreeSet<>();


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create call site for given clazz, code block and index.
   */
  Site(int cl, int c, int i)
  {
    _cl = cl;
    _c = c;
    _i = i;
  }



  /**
   * Define total order over two call sites.
   */
  public int compareTo(Site cs)
  {
    return
      _cl < cs._cl ? -1 :
      _cl > cs._cl ? +1 :
      _c < cs._c ? -1 :
      _c > cs._c ? +1 :
      _i < cs._i ? -1 :
      _i > cs._i ? +1 : 0;
  }

}

/* end of file */
