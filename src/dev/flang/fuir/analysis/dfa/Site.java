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
   * The site's site.
   */
  final int _s;


  /**
   * set of features accessed at this site.
   */
  final TreeSet<Integer> _accesses = new TreeSet<>();


  /**
   * Results of analyzing this Site
   */
  private boolean _mayReturn = false;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create call site for site s.
   *
   * @param s a FUIR site
   */
  Site(int s)
  {
    _s = s;
  }


  /**
   * This site is found to always only result in void.
   */
  public boolean alwaysResultsInVoid()
  {
    return !_mayReturn;
  }


  /**
   * Record result of an analysis of this site.
   */
  public void recordResult(boolean isVoid)
  {
    if (!isVoid)
      {
        _mayReturn = true;
      }
  }


  /**
   * Define total order over two call sites.
   */
  public int compareTo(Site cs)
  {
    return Integer.compare(_s, cs._s);
  }

}

/* end of file */
