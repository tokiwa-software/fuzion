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
 * Source of class Old
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;


/**
 * Old <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Old extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  public Expr e;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param e
   */
  public Old(Expr e)
  {
    super(e.pos);
    this.e = e;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Load all features that are called by this expression.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param thiz the class that contains this expression.
   */
  void loadCalledFeature(Resolution res, Feature thiz)
  {
    e.loadCalledFeature(res, thiz);
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
  public Old visit(FeatureVisitor v, Feature outer)
  {
    e = e.visit(v, outer);
    return this;
  }


  /**
   * typeOrNull returns the type of this expression or null if the type is still
   * unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  public AbstractType typeOrNull()
  {
    return e.typeOrNull();
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "old "+e;
  }

}

/* end of file */
