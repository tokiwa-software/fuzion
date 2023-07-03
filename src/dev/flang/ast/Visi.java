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

import dev.flang.util.ANY;

/**
 * Visi store the visibility of a Feature and the type it is defining
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public enum Visi
{

  /*
   * visible only in the current file
   */
  PRIV("private"),


  /*
   * callable only in the current file, type visible in module
   */
  PRIVMOD("private:module"),


  /*
   * callable only in the current file, type publicly visible
   */
  PRIVPUB("private:public"),


  /*
   * callable only in the module, type visible in module
   */
  MOD("module"),


  /*
   * callable only within the module, type publicly visible
   */
  MODPUB("module:public"),


  /*
   * visible everywhere
   */
  PUB("public");



  private final String _kind;

  private Visi(String s)
  {
    _kind = s;
  }

  public String toString()
  {
    return this._kind;
  }


  /**
   * get the Visibility that corresponds to the given ordinal number.
   */
  public static Visi from(int ordinal)
  {
    if (ANY.PRECONDITIONS) ANY.require
      (0 <= ordinal,
        ordinal < values().length);

    if (ANY.CHECKS) ANY.check
      (values()[ordinal].ordinal() == ordinal);

    return values()[ordinal];
  }

}

/* end of file */
