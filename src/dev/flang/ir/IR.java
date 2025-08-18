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
 * Source of class IR
 *
 *---------------------------------------------------------------------*/

package dev.flang.ir;

import java.util.stream.Collectors;

import dev.flang.ast.AbstractAssign; // NYI: remove dependency
import dev.flang.ast.AbstractBlock; // NYI: remove dependency
import dev.flang.ast.AbstractCall; // NYI: remove dependency
import dev.flang.ast.Constant; // NYI: remove dependency
import dev.flang.ast.AbstractCurrent; // NYI: remove dependency
import dev.flang.ast.AbstractMatch; // NYI: remove dependency
import dev.flang.ast.AbstractType;
import dev.flang.ast.Expr; // NYI: remove dependency
import dev.flang.ast.InlineArray; // NYI: remove dependency
import dev.flang.ast.NumLiteral; // NYI: remove dependency
import dev.flang.ast.Nop; // NYI: remove dependency
import dev.flang.ast.Universe; // NYI: remove dependency

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * IR provides the common super class for the Fuzion intermediate representation.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class IR extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * For clazzes represented by integers, this gives the base added to the
   * integers to detect wrong values quickly.
   */
  protected static final int CLAZZ_BASE   = 0x10000000;
  protected static final int CLAZZ_END    = CLAZZ_BASE + 0x1FFFFFFF; // 0x2fffffff


  /**
   * For FUIR code represented by integers, this gives the base added to the
   * integers to detect wrong values quickly.
   */
  public static final int SITE_BASE    = CLAZZ_END + 1; // 0x30000000;
  public static final int SITE_END     = SITE_BASE + 0x1FFFFFFF; // 0x4fffffff


  /**
   * Special site index value for unknown site location (i.e, a site coming from
   * an intrinsic or the program entry point).
   */
  public static final int NO_SITE = SITE_BASE-1;


  /**
   * Special clazz index value for not-existing clazz.
   *
   * NYI: CLEANUP: This should better be {@code CLAZZ_BASE-1} and it should be used
   * instead of {@code -1} in FUIR.java.
   */
  public static final int NO_CLAZZ = -1;


  /**
   * For Features represented by integers, this gives the base added to the
   * integers to detect wrong values quickly.
   */
  protected static final int FEATURE_BASE = SITE_END + 1; // 0x50000000


  /**
   * The basic types of features in Fuzion:
   */
  public enum FeatureKind
  {
    Routine,
    Field,
    Intrinsic,
    Abstract,
    Choice,
    Native,
    TypeParameter;


    /**
     * Can a feature of this kind have an outer ref?
     */
    public boolean mayHaveOuterRef()
    {
      return this == Routine || this == Intrinsic;
    }
  }


  public enum ExprKind
  {
    Assign,
    Box,
    Call,
    Current,
    Comment,
    Const,
    Match,
    Tag,
    Pop;
  }


  /**
   * All the code blocks in this IR. They are added via {@code addCode}.
   */
  protected final List<Object> _allCode;


  /*--------------------------  constructors  ---------------------------*/


  public IR()
  {
    _allCode = new List<>();
  }


  /**
   * Clone this IR such that modifications can be made by optimizers.  A heir of
   * IR can use this to redefine some methods while reusing the data from
   * original for all the rest.
   *
   * @param original the original IR instance that we are cloning.
   */
  protected IR(IR original)
  {
    _allCode = original._allCode;
  }


  /*-----------------------  code block handling  -----------------------*/


  /**
   * Add given code block and obtain a unique id for it.
   *
   * This also sets _siteStart in case {@code b} was not already added.
   *
   * @param code a list of Exprs, might contain non-Expr values for special cases.
   *
   * @return the index of code
   */
  protected int addCode(List<Object> code)
  {
    var result = _allCode.size() + SITE_BASE;
    for (var c : code)
      {
        _allCode.add(c);
      }
    _allCode.add(null);
    return result;
  }


  /**
   * Get the expression at the given site
   *
   * @param s a site
   *
   * @return the expression found at site s.
   */
  protected Object getExpr(int s)
  {
    return _allCode.get(s - SITE_BASE);
  }


  /*--------------------------  stack handling  -------------------------*/


  /**
   * Create list of ExprKind from the given expression (and its nested
   * expressions).
   *
   * @param e a expression.
   *
   * @return list of ExprKind created from s.
   */
  private List<Object> toStack(Expr e)
  {
    List<Object> result = new List<>();
    toStack(result, e);
    return result;
  }


  /**
   * Add entries of type ExprKind created from the given expression (and its
   * nested expressions) to list l.
   *
   * @param l list of ExprKind that should be extended by s's expressions
   *
   * @param e a expression.
   */
  protected void toStack(List<Object> l, Expr e)
  {
    toStack(l, e, false);
  }


  /**
   * Add entries of type ExprKind created from the given expression (and its
   * nested expressions) to list l.  pop the result in case dumpResult==true.
   *
   * @param l list of ExprKind that should be extended by s's expressions
   *
   * @param e a expression.
   *
   * @param dumpResult flag indicating that we are not interested in the result.
   */
  protected void toStack(List<Object> l, Expr e, boolean dumpResult)
  {
    if (PRECONDITIONS) require
      (l != null,
       e != null);

    if (e instanceof AbstractAssign a)
      {
        toStack(l, boxAndTag(a._value, a._assignedField.resultType()));
        toStack(l, a._target);
        l.add(a);
      }
    else if (e instanceof Box b)
      {
        toStack(l, b._value, dumpResult);
        if (!dumpResult)
          {
            l.add(b);
          }
      }
    else if (e instanceof AbstractBlock b)
      {
        // for (var expr : b.expressions_)  -- not possible since we need index i
        for (int i=0; i<b._expressions.size(); i++)
          {
            var expr = b._expressions.get(i);
            toStack(l, expr, dumpResult || i < b._expressions.size()-1);
          }
      }
    else if (e instanceof Constant)
      {
        if (!dumpResult)
          {
            l.add(e);
          }
      }
    else if (e instanceof InlineArray ia)
      {
        // in FUIR this inline array might be added
        //  to stack as a compile time constant.
        if (!dumpResult)
        {
          toStack(l, ia.code(), dumpResult);
        }
      }
    else if (e instanceof AbstractCurrent)
      {
        if (!dumpResult)
          {
            l.add(ExprKind.Current);
          }
      }
    else if (e instanceof AbstractCall c)
      {
        toStack(l, c.target());
        var fat = c.formalArgumentTypes();
        for (int i = 0; i < c.actuals().size(); i++)
          {
            toStack(l, boxAndTag(c.actuals().get(i), fat[i]));
          }
        l.add(c);
        if (dumpResult)
          {
            l.add(ExprKind.Pop);
          }
      }
    else if (e instanceof AbstractMatch m)
      {
        toStack(l, m.subject());
        l.add(m);
        for (var c : m.cases())
          {
            var caseCode = toStack(c.code());
            l.add(new NumLiteral(addCode(caseCode)));
          }
      }
    else if (e instanceof Tag t)
      {
        toStack(l, t._value, dumpResult);
        if (!dumpResult)
          {
            l.add(t);
          }
      }
    else if (e instanceof Nop)
      {
      }
    else if (e instanceof Universe)
      {
      }
    else
      {
        say_err("Missing handling of "+e.getClass()+" in IR.toStack");
      }
  }


  /**
   * Check if expr might need boxing or tagging and wrap this
   * into Box()/Tag()/Tag(Box()) if this is the case.
   *
   * @param expr the expr to be boxed/tagged
   *
   * @param frmlT the formal type this value is assigned to
   *
   * @return this or an instance of Box/Tag wrapping this.
   */
  protected static Expr boxAndTag(Expr expr, AbstractType frmlT)
  {
    if (PRECONDITIONS) require
      (frmlT != null);

    var result = expr;
    var t = expr.type();

    if (!t.isVoid() && frmlT.isAssignableFrom(t).yes())
      {
        var rt = expr.needsBoxing(frmlT);
        if (rt != null)
          {
            result = new Box(result, rt);
          }
        if (frmlT.isChoice() && frmlT.isAssignableFrom(result.type()).yes())
          {
            result = tag(result, frmlT);
            if (CHECKS) check
              (result.needsBoxing(frmlT) == null);
          }
      }
    /**
     * A ref is
     * B ref is
     *
     * ab  : A, B is
     *
     * take_B(v B) => say "take_B: ok: {type_of v} dynamic {v.dynamic_type}"
     *
     * y1(v T : A) =>
     *   y2
     *     pre T : B
     *   =>
     *     take_B v
     *
     * y1 ab
     */
    // NYI: ugly special case: currently needed for code like
    // because isAssignableFrom does not return yes without correct Context...
    else if (t.isGenericArgument() && frmlT.isRef())
      {
        var rt = expr.needsBoxing(frmlT);
        if (rt != null)
          {
            result = new Box(result, rt);
          }
      }

    if (POSTCONDITIONS) ensure
      (Errors.any()
        || t.isVoid()
        || frmlT.isGenericArgument()
        || frmlT.isThisType()
        || result.needsBoxing(frmlT) == null
        || frmlT.isAssignableFrom(t).no());

    return result;
  }


  /**
   * handle tagging when assigning value to choice frmlT
   *
   * @param expr
   *
   * @param frmlT
   *
   * @return
   */
  private static Expr tag(Expr expr, AbstractType frmlT)
  {
    if(PRECONDITIONS) require
      (frmlT.isChoice());

    // Case 1: types are equal, no tagging necessary
    if (frmlT.compareTo(expr.type()) == 0)
      {
        return expr;
      }
    // Case 1.1: types are equal, no tagging necessary
    // NYI: BUG: soundness issue? see also isAssignableFrom
    else if(expr.type().isChoice() && (frmlT.isThisType() || expr.type().isThisType()) && frmlT.asThis().compareTo(expr.type().asThis()) == 0)
      {
        return expr;
      }
    // Case 2.1: ambiguous assignment via subtype
    //
    // example:
    //
    //  A ref is
    //  B ref is
    //  C ref : B, A is
    //  t choice A B := C
    //
    else if (frmlT
             .choiceGenerics()
             .stream()
             .filter(cg -> cg.isAssignableFromWithoutTagging(expr.type()).yes())
             .count() > 1)
      {
        Errors.fatal("Ambiguous assignment to choice, should have been caught in frontend.");
        return expr;
      }
    // Case 2.2: no nested tagging necessary:
    // there is a choice generic in this choice
    // that this value is "directly" assignable to
    else if (frmlT
             .choiceGenerics()
             .stream()
             .anyMatch(cg -> cg.isAssignableFromWithoutTagging(expr.type()).yes()))
      {
        return new Tag(expr, frmlT);
      }
    // Case 3: nested tagging necessary
    // value is only assignable to choice element
    // that itself is a choice
    else
      {
        // we assign to the choice generic
        // that expr is assignable to
        var cgs = frmlT
          .choiceGenerics()
          .stream()
          .filter(cg -> cg.isChoice() && cg.isAssignableFromWithoutBoxing(expr.type()).yes())
          .collect(Collectors.toList());

        if (cgs.size() > 1)
          {
            Errors.fatal("Ambiguous assignment to choice, should have been caught in frontend.");
          }

        if (CHECKS) check
          (Errors.any() || cgs.size() == 1);

        return tag(tag(expr, cgs.get(0)), frmlT);
      }
  }


  /**
   * Get size of the code starting at given site
   *
   * @param s a site
   *
   * @return the size of code block c, i.e. {@code withinCode(s+0..s+result-1) <==> true}.
   */
  public int codeSize(int s)
  {
    var result = 0;
    while (withinCode(s + result))
      {
        result++;
      }
    return result;
  }


  /**
   * Check if site s is still a valid site. For every valid site {@code s} with {@code withinCode(s)},
   * it is legal to call {@code withinCode(s+codeSizeAt(s))} to check if the code continues.
   *
   * @param s a value site or the successor of a valid site
   *
   * @return true iff s is a valid valid site that contains an expression
   */
  public boolean withinCode(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE);

    return _allCode.get(s - SITE_BASE) != null;
  }


  /**
   * Get the expr at the given site
   *
   * @param s a site
   *
   * @return the ExprKind of the expression at site, null if undefined.
   */
  public ExprKind codeAt(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       withinCode(s));

    return exprKind(getExpr(s));
  }


  /**
   * Helper for {@code codeAt} to determine the ExprKind for an Object that is either
   * an ast Expr or String.
   *
   * @param e an expression as stored in _allCode
   *
   * @return the corresponding ExprKind
   */
  protected ExprKind exprKind(Object e)
  {
    ExprKind result;
    if (e instanceof ExprKind ek)
      {
        result = ek;
      }
    else if (e instanceof String)
      {
        result = ExprKind.Comment;
      }
    else if (e instanceof AbstractAssign)
      {
        result = ExprKind.Assign;
      }
    else if (e instanceof Box)
      {
        result = ExprKind.Box;
      }
    else if (e instanceof AbstractCall)
      {
        result = ExprKind.Call;
      }
    else if (e instanceof AbstractMatch)
      {
        result = ExprKind.Match;
      }
    else if (e instanceof Tag)
      {
        result = ExprKind.Tag;
      }
    else if (e instanceof Constant)
      {
        result = ExprKind.Const;
      }
    else if (e instanceof InlineArray)
      {
        check(false);
        result = null;
      }
    else
      {
        result = null;
      }
    return result;
  }


  /**
   * Get the source code position of an expr at the given site if it is available.
   *
   * @param s a site
   *
   * @return the source code position or null if not available.
   */
  public SourcePosition sitePos(int s)
  {
    if (PRECONDITIONS) require
      (s >= 0,
       withinCode(s));

    var e = getExpr(s);
    return (e instanceof Expr expr) ? expr.pos()
                                    : null;
  }


  /**
   * Get the size of the intermediate command at given site
   *
   * @param s a site
   *
   * @return the offset of the next expression relative to {@code s}.
   */
  public int codeSizeAt(int s)
  {
    int result = 1;
    var e = codeAt(s);
    if (e == ExprKind.Match)
      {
        result = result + matchCaseCount(s);
      }
    return result;
  }


  /**
   * For a match expression, get the number of cases
   *
   * @param s site of the match
   *
   * @return the number of cases
   */
  public int matchCaseCount(int s)
  {
    if (PRECONDITIONS) require
      (s >= 0,
       withinCode(s),
       codeAt(s) == ExprKind.Match);

    var e = getExpr(s);
    return ((AbstractMatch) e).cases().size();
  }


  /**
   * From a given site, determine the site of the start of the code block that
   * contains the given site.
   *
   * @param site any site
   *
   * @return the site of the first Expr in the code block containing {@code site}
   */
  public int codeBlockStart(int site)
  {
    var c = site - SITE_BASE;
    var result = c;
    while (result > 0 && _allCode.get(result-1) != null)
      {
        result--;
      }
    return result + SITE_BASE;
  }


  /**
   * From a given site, determine the site of the last Expr in the code block
   * that contains the given site.
   *
   * @param site any site
   *
   * @return the site of the last Expr in the code block containing {@code site}
   */
  public int codeBlockEnd(int site)
  {
    var s0 = codeBlockStart(site);
    while (withinCode(s0 + codeSizeAt(s0)))
      {
        s0 = s0 + codeSizeAt(s0);
      }
    return s0;
  }


}

/* end of file */
