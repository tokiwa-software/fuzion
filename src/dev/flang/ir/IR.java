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
public abstract class IR extends ANY
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
    Assign,
    Box,
    Call,
    Current,
    Comment,
    Const,
    Match,
    Tag,
    Env,
    Pop;
  }


  /**
   * All the code blocks in this IR. They are added via `addCode`.
   */
  final List<Object> _allCode;


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
   * This also sets _siteStart in case `b` was not already added.
   *
   * @param b a list of Expr statements to be added.
   *
   * @return the index of b
   */
  protected int addCode(List<Object> code)
  {
    var result = _allCode.size() + CODE_BASE;
    for (var c : code)
      {
        _allCode.add(c);
      }
    _allCode.add(null);
    return result;
  }
  protected Object getExpr(int s)
  {
    return _allCode.get(s - CODE_BASE);
  }
  public boolean withinCode(int s)
  {
    if (PRECONDITIONS) require
      (s >= CODE_BASE);

    return _allCode.get(s - CODE_BASE) != null;
  }
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
   * Get the expr at the given index in given code block
   *
   * @param c the code block id
   *
   * @param ix an index within the code block
   */
  public ExprKind codeAt(int s)
  {
    if (PRECONDITIONS) require
      (s >= CODE_BASE, withinCode(s));

    return exprKind(getExpr(s));
  }


  /**
   * Get the source code position of an expr at the given index if it is available.
   *
   * @param c the code block
   *
   * @param ix an index within the code block
   *
   * @return the source code position or null if not available.
   */
  public SourcePosition codeAtAsPos(int s)
  {
    if (PRECONDITIONS) require
      (s >= 0,
       withinCode(s));

    var e = getExpr(s);
    return (e instanceof Expr expr) ? expr.pos()
                                    : null;
  }


  /**
   * Get the size of the intermediate command at index ix in codeblock c.
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
   * @param c code block containing the match
   *
   * @param ix index of the match
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
    int result = 2; // two cases for If
    if (e instanceof AbstractMatch m)
      {
        result = m.cases().size();
      }
    return result;
  }


  // REMOVE:
  public int siteFromCI(int c, int i)
  {
    return c+i;
  }
  public int codeBlockStart(int site)
  {
    var c = site - CODE_BASE;
    var result = c;
    while (result > 0 && _allCode.get(result-1) != null)
      {
        result--;
      }
    return result + CODE_BASE;
  }
  public int exprIndexFromSite(int site)
  {
    return site - codeBlockStart(site);
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


}

/* end of file */
