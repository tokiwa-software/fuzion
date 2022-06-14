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

import java.util.Iterator;
import java.util.ListIterator;
import java.util.TreeMap;
import java.util.TreeSet;

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
       c != null,
       !c.isEmpty());

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
   * visit all the features, expressions, statements within this feature.
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
        c.visit(v, outer);
      }
    return this;
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this statement.
   */
  public void resolveTypes(Resolution res, AbstractFeature outer)
  {
    var st = _subject.type();
    if (st.isGenericArgument())
      {
        AstErrors.matchSubjectMustNotBeTypeParameter(_subject.pos(), st);
      }
    res.resolveTypes(st.featureOfType());
    if (!st.isChoice())
      {
        AstErrors.matchSubjectMustBeChoice(_subject.pos(), st);
      }
    var cgs = st.choiceGenerics();
    if (CHECKS) check
      (cgs != null || Errors.count() > 0);
    if (cgs != null)
      {
        var i = cgs.listIterator();
        while (i.hasNext())
          {
            i.set(i.next().resolve(res, outer));
          }
        SourcePosition[] matched = new SourcePosition[cgs.size()];
        boolean ok = true;
        for (var c: cases())
          {
            ok &= ((Case) c).resolveType(res, cgs, outer, matched);
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
          }
      }
  }


  /**
   * Convert this Expression into an assignment to the given field.  In case
   * this is a statment with several branches such as an "if" or a "match"
   * statement, add corresponding assignments in each branch and convert this
   * into a statement that does not produce a value.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   *
   * @param r the field this should be assigned to.
   *
   * @return the Stmnt this Expr is to be replaced with, typically an Assign
   * that performs the assignment to r.
   */
  Match assignToField(Resolution res, AbstractFeature outer, Feature r)
  {
    for (var ac: cases())
      {
        var c = (Case) ac;
        c._code = c._code.assignToField(res, outer, r);
      }
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
   * @param outer the feature that contains this expression
   *
   * @param t the expected type.
   *
   * @return either this or a new Expr that replaces thiz and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the statement that reads the field.
   */
  public Expr propagateExpectedType(Resolution res, AbstractFeature outer, AbstractType t)
  {
    return addFieldForResult(res, outer, t);
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
