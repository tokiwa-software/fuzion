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
 * Source of class AbstractMatch
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Iterator;

import dev.flang.util.List;


/**
 * AbstractMatch represents a complete match expression, e.g.,
 *
 *   x ? A,B => { a; },
 *       C c => { c.x; },
 *       *   => { q; }
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractMatch extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Static type of this match or null if none. Set during resolveTypes().
   */
  AbstractType _type;


  /**
   * Id to store the match's subject's clazz in the static outer clazz at
   * runtime.
   */
  public int _runtimeClazzId = -1;  // NYI: Used by dev.flang.be.interpreter, REMOVE!


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a AbstractMatch
   */
  public AbstractMatch()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The subject under investigation here.
   */
  public abstract Expr subject();


  /**
   * The list of cases in this match expression
   */
  public abstract List<AbstractCase> cases();


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
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    var ns = subject().visit(v, outer);
    if (CHECKS) check
      (subject() == ns);

    v.action(this);
    for (var c: cases())
      {
        c.visit(v, outer);
      }
    return this;
  }


  /**
   * visit all the expressions within this Match.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited expressions
   */
  public void visitExpressions(ExpressionVisitor v)
  {
    subject().visitExpressions(v);
    super.visitExpressions(v);
    var c = cases();
    for (int i = 0; i < c.size(); i++)
      {
        c.get(i).visitExpressions(v);
      }
  }


  /**
   * Helper routine for typeIfKnown to determine the type of this match
   * expression on demand, i.e., as late as possible.
   */
  private AbstractType typeFromCases()
  {
    AbstractType result = Types.resolved.t_void;
    for (var c: cases())
      {
        var t = c.code().typeIfKnown();
        result = result == null || t == null ? null : result.union(t);
      }
    if (result == Types.t_UNDEFINED)
      {
        new IncompatibleResultsOnBranches(pos(),
                                          "Incompatible types in cases of match expression",
                                          new Iterator<Expr>()
                                          {
                                            Iterator<AbstractCase> it = cases().iterator();
                                            public boolean hasNext() { return it.hasNext(); }
                                            public Expr next() { return it.next().code(); }
                                          });
        result = Types.t_ERROR;
      }
    return result;
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
    if (_type == null)
      {
        _type = typeFromCases();
      }
    return _type;
  }


}

/* end of file */
