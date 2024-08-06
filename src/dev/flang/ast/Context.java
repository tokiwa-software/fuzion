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
 * Source of class Context
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.ANY;

/**
 * Context represents the context of Expressions that affects how these Exprs
 * are interpreted. In particular, in Fuzion code like
 *
 *   f(v T) =>
 *      if      T : String then
 *        say "String of {v.codepoint_length} codepoints"
 *      else if T : integer then
 *        say "integer, neg is {-v}"
 *
 * The context of the calls to `say` would contain the constraints `T : String`
 * or `T : integer`.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class Context extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Pre-allocated instance of no context.
   */
  static final Context NONE = new Context(null)
    {
    };

  /*----------------------------  variables  ----------------------------*/


  private final Context _outer;
  Context outer() { return _outer; }


  /*---------------------------  constructors  --------------------------*/


  public Context(Context outer)
  {
    this._outer = outer;
  }


  /*-----------------------------  methods  -----------------------------*/


  public AbstractType constraintFor(AbstractFeature typeParameter)
  {
    return _outer != null ? _outer.constraintFor(typeParameter)
                          : null;
  }


}

/* end of file */
