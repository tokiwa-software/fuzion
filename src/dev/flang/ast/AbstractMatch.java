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
 * Source of class AbstractMatch
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * AbstractMatch represents a complete match expression, e.g.,
 *
 *   x ? A,B => { a; },
 *       C c => { c.x; },
 *       *   => { q; }
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractMatch extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Id to store the match's subject's clazz in the static outer clazz at
   * runtime.
   */
  public int runtimeClazzId_ = -1;  // NYI: Used by dev.flang.be.interpreter, REMOVE!


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a AbstractMatch
   *
   * @param pos the soucecode position, used for error messages.
   */
  public AbstractMatch(SourcePosition pos)
  {
    super(pos);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The subject under investigation here.
   */
  public abstract Expr subject();


  /**
   * The list of cases in this match expression
   */
  public abstract List<Case> cases();


}

/* end of file */
