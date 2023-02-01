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
 * Source of class ModuleRef
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import dev.flang.util.ANY;


/**
 * A ModuleRef represents a reference from a Fuzion module file .fum to another
 * module file.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ModuleRef extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * Start offsets of references pointing into this module.
   */
  final int _offset;


  /**
   * name of this module.
   */
  final String _name;


  /**
   * version of this module.
   */
  final byte[] _version;


  /**
   * this module as loaded.
   */
  final LibraryModule _module;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create Module for given offset, name and version
   */
  ModuleRef(int offset, String name, byte[] version, LibraryModule m)
  {
    _offset = offset;
    _name = name;
    _version = version;
    _module = m;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Size of the data written to this module's .fum files.
   */
  int size()
  {
    return _module != null
      ? _module._data.limit()
      : 0;
  }


}

/* end of file */
