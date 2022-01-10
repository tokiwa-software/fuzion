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
 * Source of class AbstractCall
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * AbstractCall is an expression that is a call to a class and that results in
 * the result value of that class.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractCall extends Expr
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Special value for an empty generics list to distinguish a call without
   * generics ("a.b(x,y)") from a call with an empty actual generics list
   * ("a.b<>(x,y)").
   */
  public static final List<AbstractType> NO_GENERICS = new List<>();


  /*----------------------------  variables  ----------------------------*/


  // NYI: Move sid_ to target?
  public int sid_ = -1;  // NYI: Used by dev.flang.be.interpreter, REMOVE!


  // For a call to parent in an inherits clause, these are the ids of the
  // argument fields for the parent feature.
  //
  // NYI: remove, used in FUIR.  This should be replaced by explicit assignments to fields
  public int parentCallArgFieldIds_ = -1;


  /*-------------------------- constructors ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   */
  public AbstractCall(SourcePosition pos)
  {
    super(pos);
  }


  /*-----------------------------  methods  -----------------------------*/


  public abstract List<AbstractType> generics();
  public abstract AbstractFeature calledFeature();
  public abstract Expr target();
  public abstract List<Expr> actuals();
  public abstract int select();
  public abstract boolean isDynamic();
  public abstract boolean isInheritanceCall();
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    System.err.println("Called "+this.getClass()+".visit");
    return this;
  }


  /**
   * visit all the statements within this Call.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited statements
   */
  public void visitStatements(StatementVisitor v)
  {
    super.visitStatements(v);
    for (var a : actuals())
      {
        a.visitStatements(v);
      }
    if (target() != null)
      {
        target().visitStatements(v);
      }
  }

}

/* end of file */
