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
import dev.flang.ast.Block; // NYI: remove dependency
import dev.flang.ast.Box; // NYI: remove dependency
import dev.flang.ast.Check; // NYI: remove dependency
import dev.flang.ast.Env; // NYI: remove dependency
import dev.flang.ast.Expr; // NYI: remove dependency
import dev.flang.ast.Feature; // NYI: remove dependency
import dev.flang.ast.If; // NYI: remove dependency
import dev.flang.ast.Impl; // NYI: remove dependency
import dev.flang.ast.InlineArray; // NYI: remove dependency
import dev.flang.ast.NumLiteral; // NYI: remove dependency
import dev.flang.ast.Nop; // NYI: remove dependency
import dev.flang.ast.Stmnt; // NYI: remove dependency
import dev.flang.ast.Tag; // NYI: remove dependency
import dev.flang.ast.Types; // NYI: remove dependency
import dev.flang.ast.Unbox; // NYI: remove dependency
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
   * The basic types of features in Fuzion:
   */
  public enum FeatureKind
  {
    Routine,
    Field,
    Intrinsic,
    Abstract,
    Choice
  }


  public enum ExprKind
  {
    AdrOf,
    Assign,
    Box,
    Unbox,
    Call,
    Current,
    Comment,
    Const,
    Dup,
    Match,
    Tag,
    Env,
    Pop,
    Unit;

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


  protected final Map2Int<List<Object>> _codeIds;


  /*--------------------------  constructors  ---------------------------*/


  public IR()
  {
    _codeIds = new Map2Int<>(CODE_BASE);
  }

  /**
   * Clone this IR such that modifications can be made by optimizers.  A heir of
   * IR can use this to redefine some methods while reusing the data from
   * orignal for all the rest.
   *
   * @param original the original IR instance that we are cloning.
   */
  protected IR(IR original)
  {
    _codeIds = original._codeIds;
  }


  /*--------------------------  stack handling  -------------------------*/


  /**
   * Create list of ExprKind from the given statment (and its nested
   * statements).
   *
   * @param s a statement.
   *
   * @return list of ExprKind created from s.
   */
  private List<Object> toStack(Stmnt s)
  {
    List<Object> result = new List<>();
    toStack(result, s);
    return result;
  }


  /**
   * Add entries of type ExprKind created from the given statement (and its
   * nested statements) to list l.
   *
   * @param l list of ExprKind that should be extended by s's statements
   *
   * @param s a statement.
   */
  protected void toStack(List<Object> l, Stmnt s)
  {
    toStack(l, s, false);
  }


  /**
   * Add entries of type ExprKind created from the given statement (and its
   * nested statements) to list l.  pop the result in case dumpResult==true.
   *
   * @param l list of ExprKind that should be extended by s's statements
   *
   * @param s a statement.
   *
   * @param dumpResult flag indicating that we are not interested in the result.
   */
  private void toStack(List<Object> l, Stmnt s, boolean dumpResult)
  {
    if (PRECONDITIONS) require
      (l != null,
       s != null);

    if (s instanceof AbstractAssign a)
      {
        toStack(l, a._value);
        toStack(l, a._target);
        l.add(a);
      }
    else if (s instanceof Unbox u)
      {
        toStack(l, u._adr);
        if (u._needed)
          {
            l.add(u);
          }
      }
    else if (s instanceof Box b)
      {
        toStack(l, b._value);
        l.add(b);
      }
    else if (s instanceof AbstractBlock b)
      {
        // for (var st : b.statements_)  -- not possible since we need index i
        for (int i=0; i<b._statements.size(); i++)
          {
            var st = b._statements.get(i);
            toStack(l, st, dumpResult || i < b._statements.size()-1);
          }
      }
    else if (s instanceof AbstractConstant)
      {
        l.add(s);
      }
    else if (s instanceof AbstractCurrent)
      {
        l.add(ExprKind.Current);
      }
    else if (s instanceof If i)
      {
        // if is converted to If, blockId, elseBlockId
        toStack(l, i.cond);
        l.add(i);
        l.add(new NumLiteral(_codeIds.add(toStack(i.block      ))));
        l.add(new NumLiteral(_codeIds.add(toStack(i.elseBlock()))));
      }
    else if (s instanceof AbstractCall c)
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
    else if (s instanceof AbstractMatch m)
      {
        toStack(l, m.subject());
        l.add(m);
        for (var c : m.cases())
          {
            var caseCode = toStack(c.code());
            l.add(new NumLiteral(_codeIds.add(caseCode)));
          }
      }
    else if (s instanceof Tag t)
      {
        toStack(l, t._value);
        l.add(t);
      }
    else if (s instanceof Env v)
      {
        l.add(v);
      }
    else if (s instanceof Nop)
      {
      }
    else if (s instanceof Universe)
      {
        var un = (Universe) s;
      }
    else if (s instanceof InlineArray)
      {
        l.add(s);
      }
    else if (s instanceof Check c)
      {
        // NYI: Check not supported yet
        //
        // l.add(s);
      }
    else
      {
        System.err.println("Missing handling of "+s.getClass()+" in FUIR.toStack");
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
    else if (e instanceof Unbox)
      {
        result = ExprKind.Unbox;
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
    else if (e instanceof AbstractConstant ||
             e instanceof InlineArray         )
      {
        result = ExprKind.Const;
      }
    else
      {
        result = null;
      }
    return result;
  }


  /**
   * Get the source code position of statement #ix in given code block.
   *
   * @param c code block index
   *
   * @param ix index of statement within c
   *
   * @return the source code position of statement #ix in block c.
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
   * For a match statement, get the number of cases
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

}

/* end of file */
