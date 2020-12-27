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
 * Source of class Singleton
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Iterator;

import dev.flang.util.SourcePosition;


/**
 * Singleton is an expression that returns an existing singleton object
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class Singleton extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  public final Feature singleton_;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param f the singleton feature
   */
  public Singleton(SourcePosition pos, Feature f)
  {
    super(pos);

    if (PRECONDITIONS) require
      (f.isSingleton());

    this.singleton_ = f;
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
    return this.singleton_.thisType();
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
  public Singleton visit(FeatureVisitor v, Feature outer)
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
    return singleton_.qualifiedName() + ".singleton";
  }

}

/* end of file */
