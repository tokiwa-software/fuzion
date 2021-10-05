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
public class Match extends Expr
{


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The subject under investigation here.
   */
  public Expr subject;

  /**
   * The list of cases in this match expression
   */
  public final List<Case> cases;


  /**
   * Static type of this match or null if none. Set during resolveTypes().
   */
  public Type type_;


  /**
   * Id to store the match's subject's clazz in the static outer clazz at
   * runtime.
   */
  public int runtimeClazzId_ = -1;  // NYI: Used by dev.flang.be.interpreter, REMOVE!


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a Match
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param e the expression that is checked by this match, must be of a choice
   * type
   *
   * @param c the match cases.
   */
  public Match(SourcePosition pos,
               Expr e,
               List<Case> c)
  {
    super(pos);

    if (PRECONDITIONS) require
      (e != null,
       c != null,
       !c.isEmpty());

    subject = e;
    cases = c;
  }


  /*-----------------------------  methods  -----------------------------*/


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
  public Match visit(FeatureVisitor v, Feature outer)
  {
    subject = subject.visit(v, outer);
    v.action(this, outer);
    for (Case c: cases)
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
  public void resolveTypes(Resolution res, Feature outer)
  {
    var st = subject.type();
    if (st.isGenericArgument())
      {
        FeErrors.matchSubjectMustNotBeTypeParameter(subject.pos(), st);
      }
    st.featureOfType().resolveTypes(res);
    if (!st.isChoice())
      {
        FeErrors.matchSubjectMustBeChoice(subject.pos(), st);
      }
    var cgs = st.choiceGenerics();
    check
      (cgs != null || Errors.count() > 0);
    if (cgs != null)
      {
        ListIterator<Type> i = cgs.listIterator();
        while (i.hasNext())
          {
            i.set(i.next().resolve(outer));
          }
        SourcePosition[] matched = new SourcePosition[cgs.size()];
        boolean ok = true;
        for (Case c: cases)
          {
            ok &= c.resolveType(cgs, outer, matched);
          }
        var missingMatches = new List<Type>();
        for (var ix = 0; ix < cgs.size(); ix++)
          {
            if (matched[ix] == null && cgs.get(ix) != Types.t_ERROR)
              {
                missingMatches.add(cgs.get(ix));
              }
          }
        if (!missingMatches.isEmpty() && ok)
          {
            FeErrors.missingMatches(pos, cgs, missingMatches);
          }
      }
  }


  /**
   * Helper routine for typeOrNull to determine the type of this match statement
   * on demand, i.e., as late as possible.
   */
  private Type typeFromCases()
  {
    Type result = Types.resolved.t_void;
    for (Case c: cases)
      {
        Type t = c.code.typeOrNull();
        result = result == null || t == null ? null : result.union(t);
      }
    if (result == Types.t_UNDEFINED)
      {
        new IncompatibleResultsOnBranches(pos,
                                          "Incompatible types in cases of match statement",
                                          new Iterator<Expr>()
                                          {
                                            Iterator<Case> it = cases.iterator();
                                            public boolean hasNext() { return it.hasNext(); }
                                            public Expr next() { return it.next().code; }
                                          });
        result = Types.t_ERROR;
      }
    return result;
  }


  /**
   * typeOrNull returns the type of this expression or null if the type is still
   * unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  public Type typeOrNull()
  {
    if (type_ == null)
      {
        type_ = typeFromCases();
      }
    return type_;
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
  Match assignToField(Resolution res, Feature outer, Feature r)
  {
    for (Case c: cases)
      {
        c.code = c.code.assignToField(res, outer, r);
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
  public Expr propagateExpectedType(Resolution res, Feature outer, Type t)
  {
    return addFieldForResult(res, outer, t);
  }


  /**
   * Find used features, i.e., mark all features that are found to be the target of a call as used.
   */
  public void findUsedFeatures(Resolution res)
  {
    Feature sf = subject.type().featureOfType();
    Feature ct = sf.choiceTag_;

    check
      (Errors.count() > 0 || ct != null);

    if (ct != null)
      {
        ct.markUsed(res, pos);
      }
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    var sb = new StringBuilder("match " + subject + "\n");
    for (var c : cases)
      {
        sb.append(c.toString()).append("\n");
      }
    return sb.toString();
  }

}
