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
 * Source of class ArtificialBuiltInType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;



/**
 * A BuiltInType is an unresolved type representing a built-in type that does
 * not appear explicitly in the source code but that is needed in the parsing
 * phase.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class ArtificialBuiltInType extends ResolvedNormalType
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * name of this type, like Types.ADDRESS_NAME.
   */
  final String _name;


  /**
   * Global unique ids for artificial built in types.
   */
  private static int ids = 1;


  /**
   * The unique id of this artificial built in type.
   */
  private int _id;


  /*--------------------------  constructors  ---------------------------*/


  public ArtificialBuiltInType(String name)
  {
    super();
    _name = name;
    _id = ids++;
  }


  /*-----------------------------  methods  -----------------------------*/


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
  }


  /**
   * outer feature, null.
   */
  public AbstractType outer()
  {
    return null;
  }


  /**
   * Id to differentiate artificial types.
   */
  @Override
  public int artificialBuiltInID()
  {
    return _id;
  }


  /**
   * toString is redefined here to avoid accessing _feature to create name.
   */
  @Override
  public String toString()
  {
    return _name;
  }

}

/* end of file */
