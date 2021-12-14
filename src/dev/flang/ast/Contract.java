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
 * Source of class Contract
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.List;


/**
 * Contract <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Contract
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Empty list of conditions.
   */
  static final List<Cond> NO_COND = new List<>();


  /**
   * Empty contract
   */
  static final Contract EMPTY_CONTRACT = new Contract(NO_COND, NO_COND, NO_COND);


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  public List<Cond> req;

  /**
   *
   */
  public List<Cond> ens;

  /**
   *
   */
  public List<Cond> inv;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param r
   *
   * @param e
   *
   * @param i
   */
  public Contract(List<Cond> r,
                  List<Cond> e,
                  List<Cond> i)
  {
    req = r == null || r.isEmpty() ? NO_COND : r;
    ens = e == null || e.isEmpty() ? NO_COND : e;
    inv = i == null || i.isEmpty() ? NO_COND : i;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   */
  public void visit(FeatureVisitor v, AbstractFeature outer)
  {
    if (req != null) { for (Cond c: req) { c.visit(v, outer); } }
    if (ens != null) { for (Cond c: ens) { c.visit(v, outer); } }
    if (inv != null) { for (Cond c: inv) { c.visit(v, outer); } }
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    StringBuffer res = new StringBuffer();
    if ((req != null) && (!req.isEmpty()))
      {
        res.append("\n  require ").append(req);
      }
    if ((ens != null) && (!ens.isEmpty()))
      {
        res.append("\n  ensure ").append(ens);
      }
    if ((inv != null) && (!inv.isEmpty()))
      {
        res.append("\n  invariant ").append(inv);
      }
    return res.toString();
  }

}

/* end of file */
