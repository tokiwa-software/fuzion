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
 * Source of class Visi
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

/**
 * Visi store the visibility of a Feature
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public enum Visi
{

  /**
   * visibility for anonymous features
   */
  INVISIBLE("invisible"),


  /**
   * default visibility: visible to all inner classes of outer class
   * of declaring class
   */
  LOCAL("local"),


  /**
   * private visibility: visible to declaring class and all its inner
   * classes
   */
  PRIVATE("private"),


  /**
   * protected visibility: visible to all heirs of declaring class
   */
  CHILDREN("children"),


  /**
   * public visibility: visible to all classes
   */
  PUBLIC("public");



  private final String _kind;

  private Visi(String s)
  {
    _kind = s;
  }

  public String toString()
  {
    return this._kind;
  }

}

/* end of file */
