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
 * Source of class Block
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.ListIterator;

import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Block represents a Block of statements
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Block extends AbstractBlock
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The soucecode position of this expression, used for error messages.
   */
  private final SourcePosition _pos;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Generic constructor
   *
   * @param pos the soucecode position of the start of this block, used for
   * error messages.
   *
   * @param closingBracePos the sourcecode position of this block's closing
   * brace. In case this block does not originate in source code, but was added
   * by AST manipulations, this might as well be equal to pos.
   *
   * @param s the list of statements
   *
   * @param newScope true iff this block opens a new scope, false if declaration
   * in this block should remain visibile after the block (which is usually the
   * case for artificially generated blocks)
   */
  private Block(SourcePosition pos,
                SourcePosition closingBracePos,
                List<Stmnt> s,
                boolean newScope)
  {
    super(closingBracePos, s, newScope);
    this._pos = pos;
  }


  /**
   * Generate a block of statements that define a new scope. This is generally
   * called from the Parser when the source contains a block.
   *
   * @param pos the soucecode position of the start of this block, used for
   * error messages.
   *
   * @param closingBracePos the sourcecode position of this block's closing
   * brace. In case this block does not originate in source code, but was added
   * by AST manipulations, this might as well be equal to pos.
   *
   * @param s the list of statements
   */
  public Block(SourcePosition pos,
               SourcePosition closingBracePos,
               List<Stmnt> s)
  {
    this(pos, closingBracePos, s, true);
  }


  /**
   * Generate a block of statements that do not define a new scope, i.e.,
   * declarations remain visible after this block.
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param s the list of statements
   */
  public Block(SourcePosition pos,
               List<Stmnt> s)
  {
    this(pos, pos, s, false);
  }


  /**
   * Generate a block of statements that do not define a new scope, i.e.,
   * declarations remain visible after this block.
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param s the list of statements
   *
   * @param hasImplicitResult true iff this block produces an implicit result
   * that can be ignored if assigned to unit type.
   */
  public Block(SourcePosition pos,
               List<Stmnt> s,
               boolean hasImplicitResult)
  {
    this(pos, s);
    this._hasImplicitResult = hasImplicitResult;
  }


  /*-------------------------  static methods  --------------------------*/


  /**
   * Create a block that consists only of one expression.  null if e == null.
   *
   * @param e an expression or null
   *
   * @return e if e is a Block, otherwise a new block that contains e or null if
   * e is null.
   */
  static Block fromExpr(Expr e)
  {
    Block result;
    if (e == null)
      {
        result = null;
      }
    else if (e instanceof Block)
      {
        result = (Block) e;
      }
    else
      {
        result = new Block(e.pos(), new List<Stmnt>(e));
      }
    return result;
  }


  /**
   * Create a block from one expression, or an empty block if expression is
   * null.
   *
   * @param e an expression or null
   *
   * @return e if e is a Block, otherwise a new block that is either empty or
   * contains e (if e not null).
   */
  static Block newIfNull(SourcePosition pos, Expr e)
  {
    var b = fromExpr(e);
    return b == null ? new Block(pos, new List<>()) : b;
  }

  /*-----------------------------  methods  -----------------------------*/


  /**
   * The soucecode position of this expression, used for error messages.
   */
  public SourcePosition pos()
  {
    return _pos;
  }

}

/* end of file */
