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
 * @author Fridtjof Siebert (siebert@tokiwa.software)
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


  public void    action      (Unbox       u, Feature outer) { }
  public void    action      (Assign      a, Feature outer) { }
  public void    actionBefore(Block       b, Feature outer) { }
  public void    actionAfter (Block       b, Feature outer) { }
  public void    action      (Box         b, Feature outer) { }
  public Expr    action      (Call        c, Feature outer) { return c; }
  public void    actionBefore(Case        c, Feature outer) { }
  public void    actionAfter (Case        c, Feature outer) { }
  public void    action      (Cond        c, Feature outer) { }
  public Expr    action      (Current     c, Feature outer) { return c; }
  public Stmnt   action      (Destructure d, Feature outer) { return d; }
  public Stmnt   action      (Feature     f, Feature outer) { return f; }
  public Expr    action      (Function    f, Feature outer) { return f; }
  public void    action      (Generic     g, Feature outer) { }
  public void    action      (If          i, Feature outer) { }
  public void    action      (Impl        i, Feature outer) { }
  public Expr    action      (InlineArray   i, Feature outer) { return i; }
  public void    action      (Match       m, Feature outer) { }
  public void    action      (Tag         b, Feature outer) { }
  public Expr    action      (This        t, Feature outer) { return t; }
  public Type    action      (Type        t, Feature outer) { return t; }

  /**
   * Visotors that want a different treatment for visiting actual arguments of a
   * call can redefine this method.  This is used for type resolution to delay
   * resolution or actual arguments until the outer feature's type was resolved.
   */
  void visitActuals(Runnable r, Feature outer) { r.run(); }

  /**
   * This can be redefined to suppress visiting Assigns that were created for
   * assiging the initial values to fields. This is useful to avoid visiting the
   * initial value of the field twice as long as the field declaration is still
   * in the code.
   *
   * @return true iff Assigns creates for intial values of fields should be
   * visited.  The default implementation always returns true.
   */
  boolean visitAssignFromFieldImpl() { return true; }

}
