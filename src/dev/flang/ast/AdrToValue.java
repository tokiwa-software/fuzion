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
 * Source of class AdrToValue
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Iterator;

import dev.flang.util.Errors;
import dev.flang.util.SourcePosition;

/**
 * AdrToValue is an expression that dereferences an address of a value type to
 * the value type.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class AdrToValue extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The address of the value type
   */
  public Expr adr_;


  /**
   * The type of this, set during creation.
   */
  private Type type_;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param t the result type
   */
  public AdrToValue(SourcePosition pos, Expr adr, Type type, Feature outer)
  {
    super(pos);

    if (PRECONDITIONS) require
      (pos != null,
       adr != null,
       adr.type().isRef(),
       Errors.count() > 0 || type.featureOfType() == outer,
       !type.featureOfType().isThisRef()
       );

    this.adr_ = adr;
    this.type_ = type; // outer.thisType().resolve(outer);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * typeOrNull returns the type of this expression or Null if the type is still
   * unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  public Type typeOrNull()
  {
    return type_;
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
  public AdrToValue visit(FeatureVisitor v, Feature outer)
  {
    adr_ = adr_.visit(v, outer);
    return this;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "deref(" + adr_ + ")";
  }

}

/* end of file */
