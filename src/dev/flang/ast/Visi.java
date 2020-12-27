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
 * Source of class Visi
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.ANY;
import dev.flang.util.List;


/**
 * Visi store the visibility of a Feature
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class Visi extends ANY
{

  final List<List<String>> _exportTo;
  final String _kind;

  Visi(String kind)
  {
    this._kind = kind;
    this._exportTo = null;
  }

  Visi(List<List<String>> l)
  {
    this._kind = "export";
    this._exportTo = l;
  }

  public String toString()
  {
    return _kind + (_exportTo != null ? " "+_exportTo : "");
  }

}

/* end of file */
