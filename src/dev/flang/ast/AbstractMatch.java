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

import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * AbstractMatch represents a complete match expression, e.g.,
 *
 *   x ? A,B => { a; },
 *       C c => { c.x; },
 *       *   => { q; }
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractMatch extends ExprWithPos
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
  public AbstractMatch(SourcePosition pos)
  {
    super(pos);
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
   * type returns the type of this expression or Types.t_ERROR if the type is
   * still unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or t_ERROR in case it is not known yet.
   * t_FORWARD_CYCLIC in case the type can not be inferred due to circular inference.
   */
  @Override
  public AbstractType type()
  {
    if (_type == null)
      {
        _type = cases()
          .map2(x -> x.code().type())
          .stream()
          .reduce(Types.resolved.t_void, (a,b) -> a.union(b, Context.NONE));
        if (CHECKS) require
          (_type.isVoid() || _type.compareTo(Types.resolved.t_unit) == 0);
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
        else if (Types.resolved.t_bool.asThis().isAssignableFromWithoutBoxing(st, context).no())
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
