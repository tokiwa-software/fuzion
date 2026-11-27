/*

This file is part of the Fuzion language server protocol implementation.

The Fuzion language server protocol implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language server protocol implementation is distributed in the hope that it will be
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
 * Source of class ExprTool
 *
 *---------------------------------------------------------------------*/


package dev.flang.lsp.shared;

import java.util.Comparator;

import dev.flang.ast.Expr;
import dev.flang.util.ANY;
import dev.flang.util.SourcePosition;

public class ExprTool extends ANY
{

  /**
   * A heuristic to figure out where the given expr ends.
   * NYI: UNDER DEVELOPMENT: use Parser to figure out the end of expression.
   * @param expr
   * @return
   */
  public static SourcePosition endOfExpr(Expr expr)
  {
      return expr.sourceRange().bytePos() == expr.sourceRange().byteEndPos()
        ? LexerTool.endOfToken(expr.pos())
        : new SourcePosition(expr.sourceRange()._sourceFile, expr.sourceRange().byteEndPos());
  }


  /**
   * Compare expressions by evaluating their end positions and comparing those.
   */
  public static final Comparator<? super Expr> compareByEndOfExpr =
    Comparator.comparing(expr -> endOfExpr(expr), (sourcePosition1, sourcePosition2) -> {
      return SourcePositionTool.compare(sourcePosition1, sourcePosition2);
    });

}
