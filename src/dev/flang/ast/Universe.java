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
 * Source of class Universe
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Iterator;

import dev.flang.util.FuzionConstants;
import dev.flang.util.SourcePosition;


/**
 * Universe is an expression that returns the universe instance.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Universe extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  private final SourcePosition _pos;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  public Universe()
  {
    this(SourcePosition.builtIn);
  }


  /**
   * Constructor for a universeCall at the given position.
   */
  public Universe(SourcePosition pos)
  {
    this._pos = pos;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  public SourcePosition pos()
  {
    return _pos;
  }


  /**
   * typeIfKnown returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  AbstractType typeIfKnown()
  {
    return Types.resolved.universe.thisType();
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this.
   */
  public Universe visit(FeatureVisitor v, AbstractFeature outer)
  {
    return this;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return FuzionConstants.UNIVERSE_NAME;
  }

}

/* end of file */
