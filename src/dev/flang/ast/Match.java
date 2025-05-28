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
import java.util.Stack;
import java.util.function.Supplier;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
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


  /*-------------------------  static variables -------------------------*/


  /**
   * quick-and-dirty way to make unique names for match result vars
   */
  static private long _id_ = 0;


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
    super(pos);
    if (PRECONDITIONS) require
      (e != null,
       c != null);

    _subject = e;
    _cases = c;
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
  public Match visit(FeatureVisitor v, AbstractFeature outer)
  {
    var os = _subject;
    var ns = _subject.visit(v, outer);
    if (CHECKS) check
      (os == _subject);
    _subject = ns;
    v.action(this);
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
  void resolveTypes(Resolution res, Context context)
  {
    var st = _subject.typeForInferencing();
    if (st != null && st != Types.t_ERROR && !st.isGenericArgument())
      {
        res.resolveTypes(st.feature());
      }
    if (st != null && st.isChoice() && Types.resolved.t_void != st)
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
   * @param from for error output: if non-null, produces a String describing
   * where the expected type came from.
   *
   * @return either this or a new Expr that replaces thiz and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the expression that reads the field.
   */
  @Override
  Expr propagateExpectedType(Resolution res, Context context, AbstractType t, Supplier<String> from)
  {
    // NYI: CLEANUP: there should be another mechanism, for
    // adding missing result fields instead of misusing
    // `propagateExpectedType`.
    //
    return addFieldForResult(res, context, t);
  }


  /**
   * Add a field for the result of this match expression,
   * add an assign to this field of each cases result.
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this assignment is used
   *
   * @param t the type to use for the result field
   */
  private Expr addFieldForResult(Resolution res, Context context, AbstractType t)
  {
    Expr result = this;
    if (!t.isVoid())
      {
        var pos = pos();
        Feature r = new Feature(res,
                                pos,
                                Visi.PRIV,
                                t,
                                FuzionConstants.EXPRESSION_RESULT_PREFIX + (_id_++),
                                context.outerFeature());
        r.scheduleForResolution(res);
        res.resolveTypes();
        result = new Block(new List<>(assignToField(res, context, r),
                                      new Call(pos, new Current(pos, context.outerFeature()), r).resolveTypes(res, context)));
      }
    return result;
  }


  /**
   * This will trigger addFieldForResult in some cases, e.g.:
   * `match (if true then true else true) * =>`
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   */
  void addFieldsForSubject(Resolution res, Context context)
  {
    _subject = subject().propagateExpectedType(res, context, subject().type(), null);
  }


  /**
   * Some Expressions do not produce a result, e.g., a Block
   * whose last expression is not an expression that produces a result.
   */
  @Override public boolean producesResult()
  {
    return !_assignedToField;
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
        _type = typeFromCases(false);
      }
    return _type;
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
        _type = typeFromCases(false);
        if (_type == Types.t_ERROR)
          {
            new IncompatibleResultsOnBranches(
              pos(),
              "Incompatible types in " +
                (kind() == Kind.Plain ? "cases of match" : "branches of if") +
                " expression",
              casesForType());
          }
        else if (_type == null)
          {
            _type = typeFromCases(true);
          }
      }
    if (POSTCONDITIONS) ensure
      (_type != null,
       _type != Types.t_UNDEFINED);
    return _type;
  }


  /**
   * Helper routine for typeForInferencing to determine the
   * type of this if expression on demand, i.e., as late as possible.
   *
   * @param urgent true if we really need a type and an error should be produced
   * if we can't get one.
   */
  private AbstractType typeFromCases(boolean urgent)
  {
    return Expr.union(new List<>(casesForType()), Context.NONE, urgent);
  }


  /**
   * Iterator over all case blocks,
   * including nested match case blocks
   */
  private Iterator<Expr> casesForType()
  {
    return new Iterator<Expr>()
    {
      Stack<Iterator<AbstractCase>> stack = new Stack<Iterator<AbstractCase>>();
      {
        stack.push(cases().iterator());
      }
      public boolean hasNext()
      {
        while (!stack.peek().hasNext() && stack.size() > 1)
          {
            stack.pop();
          }
        return stack.peek().hasNext();
      }
      public Expr next()
      {
        var c = stack.peek().next().code();
        if (c instanceof Match m)
          {
            stack.push(m.cases().iterator());
            return next();
          }
        else if (c instanceof Block b && b.resultExpression() instanceof Match m)
          {
            stack.push(m.cases().iterator());
            return next();
          }
        else
          {
            return c;
          }
      }
    };
  }


  /**
   * create an if expr,
   *
   * @param pos the source position of the if
   *
   * @param c then condition
   *
   * @param b the "true"-block, must not be null
   *
   * @param elseB else block, may be null
   *
   * @param fromContract is this an if generated from a contract? (for error messages)
   */
  public static Match createIf(SourcePosition pos, Expr c, Expr b, Expr elseB, boolean fromContract)
  {
    if (PRECONDITIONS) require
      (c != null,
       b != null);

    /**
     * If there is no else / elseif, create a default else
     * branch returning unit.
     */
    if (elseB == null)
      {
        var unit = new Call(pos, FuzionConstants.UNIT_NAME);
        elseB = new Block(new List<>(unit));
      }

    // Types.resolved may still be null, so we have to
    // create these cases in a lazy fashion.
    var cases = new List<AbstractCase>(
          new Case(b.pos(), null, b)
          {
            @Override public List<AbstractType> types() { return Types.resolved == null ? null : new List<>(Types.resolved.f_TRUE.selfType()); }
            @Override boolean resolveType(Resolution res, List<AbstractType> cgs, Context context, SourcePosition[] matched)
            {
              matched[1] = SourcePosition.notAvailable;
              return true;
            }
          },
          new Case(elseB.pos(), null, elseB)
          {
            @Override public List<AbstractType> types() { return Types.resolved == null ? null : new List<>(Types.resolved.f_FALSE.selfType()); }
            @Override boolean resolveType(Resolution res, List<AbstractType> cgs, Context context, SourcePosition[] matched)
            {
              matched[0] = SourcePosition.notAvailable;
              return true;
            }
          });

    return new Match(pos, c, cases)
      {
        @Override Kind kind() { return fromContract ? Kind.Contract : Kind.If; }
      };
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    var sb = new StringBuilder((kind() == Kind.Plain ? "match " : "if ") + subject() + "\n");
    for (var c : cases())
      {
        sb.append(c.toString()).append("\n");
      }
    return sb.toString();
  }

}
