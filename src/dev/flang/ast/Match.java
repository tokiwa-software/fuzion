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
 * Source of class Match
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Match represents a complete match expression, e.g.,
 *
 *   x ? A,B => { a; },
 *       C c => { c.x; },
 *       *   => { q; }
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Match extends AbstractMatch
{


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The subject under investigation here.
   */
  Expr _subject;
  public Expr subject() { return _subject; }


  /**
   * The list of cases in this match expression
   */
  final List<AbstractCase> _cases;
  public List<AbstractCase> cases() { return _cases; }


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  private final SourcePosition _pos;


  private boolean _assignedToField = false;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a Match
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param e the expression that is checked by this match, must be of a choice
   * type
   *
   * @param c the match cases.
   */
  public Match(SourcePosition pos,
               Expr e,
               List<AbstractCase> c)
  {
    if (PRECONDITIONS) require
      (e != null,
       c != null);

    _subject = e;
    _cases = c;
    _pos = pos;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  public SourcePosition pos()
  {
    return _pos;
  }


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
  public Match visit(FeatureVisitor v, AbstractFeature outer)
  {
    _subject = _subject.visit(v, outer);
    v.action(this);
    v.action(this, outer);
    for (var c: cases())
      {
        c.visit(v, this, outer);
      }
    return this;
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
   */
  public void resolveTypes(Resolution res, Context context)
  {
    var st = _subject.type();
    if (CHECKS) check
      (Errors.any() || st != Types.t_ERROR);
    if (st != Types.t_ERROR && !st.isGenericArgument())
      {
        res.resolveTypes(st.feature());
      }
    if (st.isChoice() && Types.resolved.t_void != st)
      {
        var cgs = st.choiceGenerics(context);
        for (var i = 0; i < cgs.size(); i++)
          {
            var n = cgs.get(i);
            if (CHECKS) check
              (Errors.any() || n != null);
            if (n != null)
              {
                cgs = cgs.setOrClone(i, n.resolve(res, context));
              }
          }
        SourcePosition[] matched = new SourcePosition[cgs.size()];
        boolean ok = true;
        for (var c: cases())
          {
            ok &= ((Case) c).resolveType(res, cgs, context, matched);
          }
        var missingMatches = new List<AbstractType>();
        for (var ix = 0; ix < cgs.size(); ix++)
          {
            if (matched[ix] == null && cgs.get(ix) != Types.t_ERROR)
              {
                missingMatches.add(cgs.get(ix));
              }
          }
        if (!missingMatches.isEmpty() && ok)
          {
            AstErrors.missingMatches(pos(), cgs, missingMatches);
            _type = Types.t_ERROR;
          }
      }
  }


  /**
   * Convert this Expression into an assignment to the given field.  In case
   * this is a expression with several branches such as an "if" or a "match"
   * expression, add corresponding assignments in each branch and convert this
   * into a expression that does not produce a value.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @param r the field this should be assigned to.
   *
   * @return the Expr this Expr is to be replaced with, typically an Assign
   * that performs the assignment to r.
   */
  @Override
  Match assignToField(Resolution res, Context context, Feature r)
  {
    for (var ac: cases())
      {
        var c = (Case) ac;
        c._code = c._code.assignToField(res, context, r);
      }
    _assignedToField = true;
    return this;
  }


  /**
   * During type inference: Inform this expression that it is used in an
   * environment that expects the given type.  In particular, if this
   * expression's result is assigned to a field, this will be called with the
   * type of the field.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @param t the expected type.
   *
   * @return either this or a new Expr that replaces thiz and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the expression that reads the field.
   */
  @Override
  public Expr propagateExpectedType(Resolution res, Context context, AbstractType t)
  {
    // NYI: CLEANUP: there should be another mechanism, for
    // adding missing result fields instead of misusing
    // `propagateExpectedType`.
    //

    // This will trigger addFieldForResult in some cases, e.g.:
    // `match (if true then true else true) * =>`
    _subject = subject().propagateExpectedType(res, context, subject().type());

    return addFieldForResult(res, context, t);
  }


  /**
   * Some Expressions do not produce a result, e.g., a Block that is empty or
   * whose last expression is not an expression that produces a result.
   */
  public boolean producesResult()
  {
    return !_assignedToField;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    var sb = new StringBuilder("match " + subject() + "\n");
    for (var c : cases())
      {
        sb.append(c.toString()).append("\n");
      }
    return sb.toString();
  }

}
