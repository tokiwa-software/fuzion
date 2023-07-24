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
 * Source of class BuiltInType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.SourcePosition;

/**
 * A BuiltInType is an unresolved type representing a built-in type that does
 * not appear explicity in the source code but that is needed in the parsing
 * phase.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ArtificialBuiltInType extends ResolvedNormalType
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * name of this type, like Types.ADDRESS_NAME.
   */
  final String _name;


  /*--------------------------  constructors  ---------------------------*/


  public ArtificialBuiltInType(String name)
  {
    super();
    _name = name;
  }


  /**
   * resolve artificial types t_ERROR, etc.
   *
   * @param feat a dummy feature like universe or Types.f_ERROR.
   */
  public void resolveArtificialType(AbstractFeature feat)
  {
    if (PRECONDITIONS) require
      (_feature == null,
       Types.INTERNAL_NAMES.contains(_name));

    _feature = feat;

    var interned = Types.intern(this);

    if (CHECKS) check
      (interned == this);
  }


  /**
   * outer feature, null unless this is Types.t_ADDRESS; where this is
   * universe.selfType().
   */
  public AbstractType outer()
  {
    return this == Types.t_ADDRESS ? Types.resolved.universe.selfType() : null;
  }


  /**
   * name is redefined here to avoid accessing _feature to create name.
   */
  public String name()
  {
    return _name;
  }

  /**
   * asString is redefined here to avoid accessing _feature to create name.
   */
  public String asString()
  {
    return _name;
  }

  /**
   * toString is redefined here to avoid accessing _feature to create name.
   */
  public String toString()
  {
    return _name;
  }

}

/* end of file */
