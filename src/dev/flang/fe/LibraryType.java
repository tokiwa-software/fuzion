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
import dev.flang.ast.AbstractType;
import dev.flang.ast.Expr;
import dev.flang.ast.Feature;
import dev.flang.ast.Generic;
import dev.flang.ast.Type;

import dev.flang.util.List;

import dev.flang.util.SourcePosition;


/**
 * A LibraryType represents a Fuzion type loaded from a precompiled Fuzion
 * module file .fum.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class LibraryType extends AbstractType
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The library this come from.
   */
  public final LibraryModule _libModule;


  /**
   * Position in _libModule that declares this type. Maybe -1 for
   * _feature.thisType().
   */
  public final int _at;

  /**
   * The soucecode position of this type, used for error messages.
   */
  public final SourcePosition _pos;


  /**
   * NYI: For now, this is just a wrapper around an AST type. This should be
   * removed once all data is obtained from _libModule;
   */
  protected final AbstractType _from;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor to set common fields.
   */
  LibraryType(LibraryModule mod, int at, SourcePosition pos, AbstractType from /* NYI: to be removed */)
  {
    this._libModule = mod;
    this._at = at;
    this._pos = pos;
    this._from = from.astType();
  }


  /*-----------------------------  methods  -----------------------------*/


  public SourcePosition pos()
  {
    return _pos;
  }


  public AbstractType astType() { return _from; }

}

/* end of file */
