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
 * Source of class Check
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.ANY;
import dev.flang.util.List;


/**
 * Check is the result of parsing a Fuzion `check` statement. A Check is very
 * short lived since it is only syntax sugar, fuzion code of the form
 *
 *   check
 *     debug: e1
 *     safety: e2
 *
 * will be turned into
 *
 *   if (debug: e1)
 *     fuzion.runtime.fault "debug: e1"
 *   if (safety: e2)
 *     fuzion.runtime.fault "safety: e2"
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Check extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  List<Cond> _conditions;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param c
   */
  public Check(List<Cond> c)
  {
    this._conditions = c;
  }



  /*-----------------------------  methods  -----------------------------*/


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this.
   */
  public Expr asIfs()
  {
    var l = new List<Expr>();
    for (var c : this._conditions)
      {
        var p = c.cond.sourceRange();
        var f = new Call(p, "fuzion");
        var r = new Call(p, f, "runtime");
        var e = new Call(p, r, "check_fault", new List<>(new StrConst(p, p.sourceText())));
        l.add(new If(p, c.cond, new Block(), e));
        System.out.println("SORUCE TEST '"+p.sourceText()+"' for "+p.show());
      }
    return new Block(l);
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return
      "check " + _conditions.toString() + "\n";
  }

}

/* end of file */
