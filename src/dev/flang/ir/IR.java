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

import dev.flang.ast.AbstractAssign; // NYI: remove dependency
import dev.flang.ast.AbstractBlock; // NYI: remove dependency
import dev.flang.ast.AbstractCall; // NYI: remove dependency
import dev.flang.ast.AbstractConstant; // NYI: remove dependency
import dev.flang.ast.AbstractCurrent; // NYI: remove dependency
import dev.flang.ast.AbstractMatch; // NYI: remove dependency
import dev.flang.ast.Box; // NYI: remove dependency
import dev.flang.ast.Check; // NYI: remove dependency
import dev.flang.ast.Env; // NYI: remove dependency
import dev.flang.ast.Expr; // NYI: remove dependency
import dev.flang.ast.If; // NYI: remove dependency
import dev.flang.ast.InlineArray; // NYI: remove dependency
import dev.flang.ast.NumLiteral; // NYI: remove dependency
import dev.flang.ast.Nop; // NYI: remove dependency
import dev.flang.ast.Tag; // NYI: remove dependency
import dev.flang.ast.Universe; // NYI: remove dependency

import dev.flang.util.ANY;
import dev.flang.util.List;
import dev.flang.util.Map2Int;
import dev.flang.util.SourcePosition;


/**
 * IR provides the common super class for the Fuzion intermediate representation.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class IR extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * For clazzes represented by integers, this gives the base added to the
   * integers to detect wrong values quickly.
   */
  protected static final int CLAZZ_BASE   = 0x10000000;

  /**
   * For FUIR code represented by integers, this gives the base added to the
   * integers to detect wrong values quickly.
   */
  protected static final int CODE_BASE    = 0x30000000;

  /**
   * For Features represented by integers, this gives the base added to the
   * integers to detect wrong values quickly.
   */
  protected static final int FEATURE_BASE = 0x50000000;

  /**
   * For sites represented by integers, this gives the base added to the
   * integers to detect wrong values quickly.
   */
  protected static final int SITE_BASE = 0x70000000;

  public static final int NO_SITE = SITE_BASE-1;

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
    Native
  }


  public enum ExprKind
  {
    AdrOf,
    Assign,
    Box,
    Call,
    Current,
    Comment,
    Const,
    Match,
    Tag,
    Env,
    Pop,
    Unit,
    // this is eliminated in FUIR
    InlineArray;

    /**
     * get the Kind that corresponds to the given ordinal number.
     */
    public static ExprKind from(int ordinal)
    {
      if (CHECKS) check
        (values()[ordinal].ordinal() == ordinal);

      return values()[ordinal];
    }

  }


  /**
   * All the code blocks in this IR. They are added via `addCode`.
   */
  private final Map2Int<List<Object>> _codeIds;


  /**
   * For every raw code block index in _codeIds, this gives the index of the
   * first site for the corresponding code block.
   */
  private final List<Integer> _siteStart = new List<>(0);


  /*--------------------------  constructors  ---------------------------*/


  public IR()
  {
    _codeIds = new Map2Int<>(CODE_BASE);
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
    _codeIds = original._codeIds;
  }

  /*-----------------------  code block handling  -----------------------*/


  /**
   * Add given code block and abtain a unique id for it.
   *
   * This also sets _siteStart in case `b` was not already added.
   *
   * NYI: UNDER DEVELOPMENT: The returned index should be replaced by a site
   * index, i.e., siteFromCI(result, 0).
   *
   * @param b a list of Expr statements to be added.
   *
   * @return the index of b
   */
  protected int addCode(List<Object> b)
  {
    b.freeze();
    var res = _codeIds.add(b);
    var index = res - CODE_BASE;
    if (index >= _siteStart.size()-1)
      {
        var nextSiteStart = _siteStart.getLast() + b.size() + 1; // b.size() might be 0 so we add 1 to have disjoint site indices
        _siteStart.add(nextSiteStart);
      }
    return res;
  }


  /**
   * Get the Expr #i in code block c
   *
   * NYI: UNDER DEVELOPMENT: This should be replaced by `getExpr(int site)`.
   *
   * @param c the code block index returned by `addCode`
   *
   * @param i an index in c
   */
  protected Object getExpr(int c, int i)
  {
    return _codeIds.get(c).get(i);
  }



  /**
   * Convert a code block index c and an Expr index in that code block to a site
   * index.
   *
   * NYI: UNDER DEVELOPMENT: This should be removed once `site` is used throughout.
   *
   * @param c the code block index returned by `addCode`
   *
   * @param i an index in c
   *
   * @return a site index corresponding to `c`/`i`.
   */
  public int siteFromCI(int c, int i)
  {
    if (PRECONDITIONS) require
      (0 <= i && i < _codeIds.get(c).size());

    var index = c - CODE_BASE;
    var result = _siteStart.get(index).intValue() + i + SITE_BASE;

    if (POSTCONDITIONS) ensure
      (c == codeIndexFromSite(result),
       i == exprIndexFromSite(result));

    return result;
  }


  /**
   * Extract code block index from a site.
   *
   * NYI: UNDER DEVELOPMENT: This should be removed once `site` is used throughout.
   *
   * @param site a code site
   *
   * @return the index of the code block containing the given site.
   */
  protected int codeIndexFromSite(int site)
  {
    var rawSite = site - SITE_BASE;
    // perform binary search in _siteStart
    int l = 0;
    int r = _siteStart.size()-1;
    int result_raw_c;
    do
      {
        int m = (l + r) / 2;
        var s = _siteStart.get(m).intValue();
        int cmp = Integer.compare(rawSite, s);
        result_raw_c = cmp < 0 ? m-1 : m;
        if (cmp <= 0) { r = m - 1; }
        if (cmp >= 0) { l = m + 1; }
      }
    while (l <= r);
    int result_c = result_raw_c + CODE_BASE;

    if (POSTCONDITIONS) ensure
      (site >= result_raw_c,
       _siteStart.get(result_raw_c) <= rawSite,
       result_raw_c == _siteStart.size()-1 || _siteStart.get(result_raw_c+1) > rawSite);

    return result_c;
  }


  /**
   * Extract expr index from a site.
   *
   * NYI: UNDER DEVELOPMENT: This should be removed once `site` is used throughout.
   *
   * @param site a code site
   *
   * @return the index of the Expr withing the code block containing the given site.
   */
  protected int exprIndexFromSite(int site)
  {
    var rawSite = site - SITE_BASE;
    var index = codeIndexFromSite(site) - CODE_BASE;
    return rawSite - _siteStart.get(index).intValue();
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
        toStack(l, a._value);
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
    else if (e instanceof AbstractConstant)
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
    else if (e instanceof If i)
      {
        // if is converted to If, blockId, elseBlockId
        toStack(l, i.cond);
        l.add(i);
        l.add(new NumLiteral(addCode(toStack(i.block      ))));
        l.add(new NumLiteral(addCode(toStack(i.elseBlock()))));
      }
    else if (e instanceof AbstractCall c)
      {
        toStack(l, c.target());
        for (var a : c.actuals())
          {
            toStack(l, a);
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
    else if (e instanceof Env v)
      {
        if (!dumpResult)
          {
            l.add(v);
          }
      }
    else if (e instanceof Nop)
      {
      }
    else if (e instanceof Universe)
      {
        var un = (Universe) e;
      }
    else if (e instanceof Check c)
      {
        // NYI: Check not supported yet
        //
        // l.add(s);
      }
    else
      {
        say_err("Missing handling of "+e.getClass()+" in IR.toStack");
      }
  }


  /**
   * Get size of given code
   *
   * @param c an index of a code block
   *
   * @return the size of code block c, i.e., withinCode(c, 0..result-1) <==> true.
   */
  public int codeSize(int c)
  {
    var code = _codeIds.get(c);
    return code.size();
  }


  /**
   * Check if index ix is within code block c.
   *
   * @param c an index of a code block
   *
   * @param ix any non-negative integer
   *
   * @return true iff ix is a valid index in code block c.
   */
  public boolean withinCode(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0);

    var code = _codeIds.get(c);
    return ix < code.size();
  }


  /**
   * Get the intermediate command at index ix in codeblock c.
   *
   * @param c an index of a code block
   *
   * @param ix an index within code block c.
   *
   * @return the intermediate command at that index.
   */
  public ExprKind codeAt(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0, withinCode(c, ix));

    ExprKind result;
    var e = _codeIds.get(c).get(ix);
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
    else if (e instanceof If            ||
             e instanceof AbstractMatch    )
      {
        result = ExprKind.Match;
      }
    else if (e instanceof Tag)
      {
        result = ExprKind.Tag;
      }
    else if (e instanceof Env)
      {
        result = ExprKind.Env;
      }
    else if (e instanceof AbstractConstant)
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
   * Get the source code position of expression #ix in given code block.
   *
   * @param c code block index
   *
   * @param ix index of expression within c
   *
   * @return the source code position of expression #ix in block c.
   */
  public SourcePosition codeAtPos(int c, int ix)
  {
    return ((Expr)_codeIds.get(c).get(ix)).pos();
  }


  /**
   * Get the size of the intermediate command at index ix in codeblock c.
   */
  public int codeSizeAt(int c, int ix)
  {
    int result = 1;
    var s = codeAt(c, ix);
    if (s == ExprKind.Match)
      {
        result = result + matchCaseCount(c, ix);
      }
    return result;
  }


  /**
   * For a match expression, get the number of cases
   *
   * @param c code block containing the match
   *
   * @param ix index of the match
   *
   * @return the number of cases
   */
  public int matchCaseCount(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Match);

    var s = _codeIds.get(c).get(ix);
    int result = 2; // two cases for If
    if (s instanceof AbstractMatch m)
      {
        result = m.cases().size();
      }
    return result;
  }


  /**
   * Get the code for a comment expression.  This is used for debugging.
   *
   * @param c code block containing the comment
   *
   * @param ix index of the comment
   */
  public String comment(int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Comment);

    return (String) _codeIds.get(c).get(ix);
  }

}

/* end of file */
