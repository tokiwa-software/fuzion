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
 * Source of class AbstractCase
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.ANY;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * AbstractCase represents one case in a match expression, e.g.,
 *
 *   A,B => { a; }
 *
 * or
 *
 *   C c => { c.x; },
 *
 * or
 *
 *   *   => { q; }
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractCase extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The soucecode position of this case, used for error messages.
   */
  final SourcePosition _pos;


  /**
   * Counter for a unique id for this case statement. This is used to store data
   * in the runtime clazz for this case.
   */
  public int runtimeClazzId_ = -1;  // NYI: Used by dev.flang.be.interpreter, REMOVE!


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for an AbstractCase that assigns
   *
   * @param pos the soucecode position, used for error messages.
   */
  public AbstractCase(SourcePosition pos)
  {
    _pos = pos;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The soucecode position of this case, used for error messages.
   */
  public SourcePosition pos()
  {
    return _pos;
  }


  /**
   * Field with type from this.type created in case fieldName != null.
   */
  public abstract AbstractFeature field();


  /**
   * List of types to be matched against. null if we match against type or match
   * everything.
   */
  public abstract List<AbstractType> types();


  /**
   * code to be executed in case of a match
   */
  public abstract Block code();

}

/* end of file */
