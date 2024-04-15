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
 * IRwithCode is a variant IR that has old-style (code/index) code block
 * handling used by fe.DFA and air, while FUIR uses site-base code handling.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class IRwithCode extends IR
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * All the code blocks in this IR. They are added via `addCode`.
   */
  private final Map2Int<List<Object>> _codeIds;


  /*--------------------------  constructors  ---------------------------*/


  public IRwithCode()
  {
    super();
    _codeIds = new Map2Int<>(CODE_BASE);
  }


  /**
   * Clone this IR such that modifications can be made by optimizers.  A heir of
   * IR can use this to redefine some methods while reusing the data from
   * original for all the rest.
   *
   * @param original the original IR instance that we are cloning.
   */
  protected IRwithCode(IRwithCode original)
  {
    super(original);
    _codeIds = original._codeIds;
  }


  /*-----------------------  code block handling  -----------------------*/


  /**
   * Add given code block and obtain a unique id for it.
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
    return _codeIds.add(b);
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

    var e = getExpr(c, ix);
    return exprKind(e);
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
    return ((Expr)getExpr(c, ix)).pos();
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

    var s = getExpr(c, ix);
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

    return (String) getExpr(c, ix);
  }

}

/* end of file */
