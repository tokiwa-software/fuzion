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
   * visibility not explicitly stated
   */
  UNSPECIFIED("unspecified"),


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
        ordinal < values().length,
        ordinal != UNSPECIFIED.ordinal());

    if (ANY.CHECKS) ANY.check
      (values()[ordinal].ordinal() == ordinal);

    return values()[ordinal];
  }


  /**
   * Return corresponding Visi instance for features, i.e., without type
   * visibility and with UNSPECIFIED replaced by PRIV.
   *
   * This is used for the visibility of pre / pre bool / pre and call / post
   * features to avoid errors due to type visibility.
   *
   * @return The visibility for features/calls encoded in this: PRIV, MOD or PUB.
   */
  public Visi eraseTypeVisibility()
  {
    return switch (this)
      {
      case UNSPECIFIED, PRIV, PRIVMOD, PRIVPUB -> PRIV;
      case MODPUB, MOD                         -> MOD;
      case PUB                                 -> PUB;
      };
  }


  /**
   * @return The visibility for types encoded in this.
   * PRIV => PRIV, PRIVMOD => MOD, PRIVPUB => PUB, etc.
   */
  public Visi typeVisibility()
  {
    return switch (this)
      {
        case UNSPECIFIED, PRIV    -> Visi.PRIV;
        case MOD, PRIVMOD         -> Visi.MOD;
        case PRIVPUB, MODPUB, PUB -> Visi.PUB;
        default -> throw new Error("unhandled case in Visi.typeVisibility");
      };
  }


  /**
   * Does this visibility explicitly specify a different visibility for the type?
   * @return
   */
  public boolean definesTypeVisibility()
  {
    return this == Visi.PRIVMOD || this == Visi.PRIVPUB || this == Visi.MODPUB;
  }


}

/* end of file */
