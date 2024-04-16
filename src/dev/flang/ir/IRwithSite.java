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
 * IRwithSite is a variant IR that has new-style (site) code block handling used
 * by air and FUIR.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class IRwithSite extends IR
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * All the code blocks in this IR. They are added via `addCode`.
   */
  List<Object> _allCode = new List<>();


  /*--------------------------  constructors  ---------------------------*/


  public IRwithSite()
  {
    super();
  }


  /**
   * Clone this IR such that modifications can be made by optimizers.  A heir of
   * IR can use this to redefine some methods while reusing the data from
   * original for all the rest.
   *
   * @param original the original IR instance that we are cloning.
   */
  protected IRwithSite(IRwithSite original)
  {
    super(original);
    _allCode = original._allCode;
  }


  /*-----------------------  code block handling  -----------------------*/



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



  // REMOVE:
  public int siteFromCI(int c, int i)
  {
    return c+i;
  }
  public int codeIndexFromSite(int site)
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
    return site - codeIndexFromSite(site);
  }


}

/* end of file */
