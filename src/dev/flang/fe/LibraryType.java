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
 * Source of class LibraryType
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import java.util.Set;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.ResolvedType;


/**
 * A LibraryType represents a Fuzion type loaded from a precompiled Fuzion
 * module file .fum.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class LibraryType extends ResolvedType
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The library this come from.
   */
  public final LibraryModule _libModule;


  /**
   * Position in _libModule that declares this type. Maybe -1 for
   * _feature.selfType().
   */
  public final int _at;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor to set common fields.
   */
  LibraryType(LibraryModule mod, int at)
  {
    this._libModule = mod;
    this._at = at;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * traverse a type collecting all features this type uses.
   *
   * @param s the features that have already been found
   */
  protected void usedFeatures(Set<AbstractFeature> s)
  {
    // a library type has already been checked. nothing to be done.
  }



}

/* end of file */
