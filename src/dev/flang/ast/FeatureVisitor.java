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
 * Source of class FeatureVisitor
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.TreeMap;
import java.util.Map;

import dev.flang.util.ANY;


/**
 * This is used to perform some action on a feature and all the statements,
 * expresions, types, etc. within this feature
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public abstract class FeatureVisitor extends ANY
{

  /*----------------------------  constants  ----------------------------*/

  /*----------------------------  variables  ----------------------------*/


  /*---------------------------  constructors  --------------------------*/


  public FeatureVisitor()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  public void    action      (Assign    a, Feature outer) { }
  public void    actionBefore(Block     b, Feature outer) { }
  public void    actionAfter (Block     b, Feature outer) { }
  public void    action      (Box       b, Feature outer) { }
  public Expr    action      (Call      c, Feature outer) { return c; }
  public void    actionBefore(Case      c, Feature outer) { }
  public void    actionAfter (Case      c, Feature outer) { }
  public Expr    action      (Current   c, Feature outer) { return c; }
  public Stmnt   action      (Decompose d, Feature outer) { return d; }
  public Stmnt   action      (Feature   f, Feature outer) { return f; }
  public Expr    action      (Function  f, Feature outer) { return f; }
  public void    action      (Generic   g, Feature outer) { }
  public void    action      (If        i, Feature outer) { }
  // NYI: remove: when loop has been replaced by tail recursion, this is no longer needed
  public Expr    action      (Loop      l, Feature outer) { return l; }
  // NYI: remove: when loop has been replaced by tail recursion, this is no longer needed
  public boolean actionBefore(Loop      l, Feature outer) { return true; }  // result implies visit loop contents
  public void    action      (Impl      i, Feature outer) { }
  public void    action      (Match     m, Feature outer) { }
  public Expr    action      (This      t, Feature outer) { return t; }
  public Type    action      (Type      t, Feature outer) { return t; }

  /**
   * Visotors that want a different treatment for visiting actual arguments of a
   * call can redefine this method.  This is used for type resolution to delay
   * resolution or actual arguments until the outer feature's type was resolved.
   */
  void visitActuals(Runnable r, Feature outer) { r.run(); }

}
