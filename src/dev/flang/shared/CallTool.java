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
 * Source of class CallTool
 *
 *---------------------------------------------------------------------*/


package dev.flang.shared;

import java.util.function.Predicate;

import dev.flang.ast.AbstractBlock;
import dev.flang.ast.AbstractCall;
import dev.flang.ast.Constant;
import dev.flang.ast.AbstractCurrent;
import dev.flang.ast.Box;
import dev.flang.ast.Expr;
import dev.flang.parser.Lexer.Token;
import dev.flang.util.ANY;
import dev.flang.util.FuzionConstants;
import dev.flang.util.SourcePosition;

public class CallTool extends ANY
{

  // NYI this info should be part of an AbstractCall
  public static final Predicate<? super AbstractCall> CalledFeatureNotInternal = (c) -> {
    return !c.calledFeature().isUniverse()
      && !c.calledFeature().isBuiltInPrimitive()
      && !c.calledFeature().isTypeParameter()
      && !c.calledFeature().qualifiedName().equals("fuzion.sys")
      && !c.calledFeature().qualifiedName().equals("fuzion.sys.internal_array_init")
      && !c.calledFeature().qualifiedName().equals("fuzion.sys.internal_array." + FuzionConstants.FEATURE_NAME_INDEX_ASSIGN)
      && !c.calledFeature().qualifiedName().equals("unit")
      && !FeatureTool.IsInternal(c.calledFeature());
  };

  /**
   * Is prefix/infix/postfix call
   * @param c
   * @return
   */
  public static boolean IsFixLikeCall(AbstractCall c)
  {
    return c.calledFeature().featureName().baseName().contains(" ");
  }

  /**
  * for call of c in  a.b.c return pos of:
  * ------------------^
  * @param expr
  * @return
  */
  public static SourcePosition StartOfExpr(Expr expr)
  {
    if (ExprTool.IsLambdaCall(expr))
      {
        return ExprTool.LambdaOpeningBracePosition(expr)
          .orElse(expr.pos());
      }
    return AdjustForOpeningParens(TraverseChainedCalls(expr).pos());
  }

  /*
   * if pos is at opening parens, braces, brackets,
   * return the start of the parens, brackets
   */
  private static SourcePosition AdjustForOpeningParens(SourcePosition pos)
  {
    var leftToken = LexerTool.TokensAt(pos).left().token();
    if (leftToken == Token.t_lparen
      || leftToken == Token.t_lbrace
      || leftToken == Token.t_lbracket)
      {
        return AdjustForOpeningParens(LexerTool.GoLeft(pos));
      }
    return pos;
  }

  /*
   * If we have something like
   * a.b.c and expr is to Call to c this should return a
   * as the origin of the chained calls
   */
  private static Expr TraverseChainedCalls(Expr expr)
  {
    if (expr instanceof Box b)
      {
        return TraverseChainedCalls(b._value);
      }
    if (expr instanceof AbstractCall ac
      && (ac.target() instanceof AbstractBlock
        || ac.target() instanceof AbstractCurrent
        || ac.target() instanceof Constant
        || ac.target() instanceof AbstractCall))
      {
        return TraverseChainedCalls(ac.target());
      }
    return expr;
  }

}
