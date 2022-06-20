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
 * Source of class Constant
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.SourcePosition;


/**
 * AbstractConstant represents a constant in the source code such as '3.14', 'true',
 * '"Hello"'.  This class might be loaded from a library or parsed in sources.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractConstant extends Expr
{


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a Constant at the given source code postition.
   */
  public AbstractConstant()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Serialized form of the data of this constant.
   */
  public abstract byte[] data();


}

/* end of file */
