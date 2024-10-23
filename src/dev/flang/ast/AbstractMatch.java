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

import dev.flang.util.Errors;
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

  /**
   * where this match came from.
   * used only for better error messages.
   */
  enum Kind { Plain, If, Contract }


  /*----------------------------  variables  ----------------------------*/


  /**
   * Static type of this match or null if none. Set during resolveTypes().
   */
  AbstractType _type;


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
      (Errors.any() || subject() == ns);

    v.action(this);
    for (var c: cases())
      {
        c.visit(v, this, outer);
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
        c.get(i).visitExpressions(this, v);
      }
  }


  /**
   * Helper routine for typeForInferencing to determine the type of this match
   * expression on demand, i.e., as late as possible.
   */
  private AbstractType typeFromCases()
  {
    var result = Expr.union(cases().map2(x -> x.code()), Context.NONE);
    if (result == Types.t_ERROR)
      {
        new IncompatibleResultsOnBranches(pos(),
                                          "Incompatible types in cases of match expression",
                                          new Iterator<Expr>()
                                          {
                                            Iterator<AbstractCase> it = cases().iterator();
                                            public boolean hasNext() { return it.hasNext(); }
                                            public Expr next() { return it.next().code(); }
                                          });
      }
    return result;
  }


  /**
   * typeForInferencing returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  @Override
  AbstractType typeForInferencing()
  {
    if (_type == null)
      {
        _type = typeFromCases();
      }
    return _type;
  }


  /**
   * where this match came from.
   * used only for better error messages.
   */
  Kind kind()
  {
    return Kind.Plain;
  }


  /**
   * checks the subject type of this match.
   */
  void checkTypes(Context context)
  {
    var st = subject().type();
    if (st.isGenericArgument())
      {
        AstErrors.matchSubjectMustNotBeTypeParameter(subject().pos(), st);
      }

    if (CHECKS) check
      (Errors.any() || st != Types.t_ERROR);

    if (st != Types.t_ERROR)
      {
        if (kind() == Kind.Plain)
          {
            if (!st.isChoice())
              {
                AstErrors.matchSubjectMustBeChoice(subject().pos(), st);
              }
          }
        else if (!Types.resolved.t_bool.isDirectlyAssignableFrom(st, context))
          {
            if (kind() == Kind.Contract)
              {
                AstErrors.contractExpressionMustResultInBool(subject());
              }
            else
              {
                AstErrors.ifConditionMustBeBool(subject());
              }
          }
      }
  }


}

/* end of file */
