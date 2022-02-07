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
 * Source of class Nop
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.ANY;
import dev.flang.util.SourcePosition;


/**
 * Nop performs no operation, it is just a placeholder for doing nothing.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Nop extends ANY implements Stmnt
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  public final SourcePosition _pos;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  public Nop(SourcePosition pos)
  {
    _pos = pos;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The sourcecode position of this statment, used for error messages.
   */
  public SourcePosition pos()
  {
    return _pos;
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this
   */
  public Nop visit(FeatureVisitor v, AbstractFeature outer)
  {
    return this;
  }


  /**
   * Does this statement consist of nothing but declarations? I.e., it has no
   * code that actually would be executed at runtime.
   */
  public boolean containsOnlyDeclarations()
  {
    return true;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "NOP";
  }

}

/* end of file */
