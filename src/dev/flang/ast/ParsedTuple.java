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
 * Source of class ParsedTuple
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.SourcePosition;
import dev.flang.util.List;


/**
 * A tuple value created by the parser.
 */
public class ParsedTuple extends ParsedCall
{


  /*---------------------------  constructors  --------------------------*/


  /**
   * Constructor for a {@code tuple} value with given arguments.  If {@code arguemnts.isEmpty()},
   * this will be a {@code unit}.
   *
   * @param arguments list of actual arguments to build a the {@code tuple}.
   */
  public ParsedTuple(SourcePosition pos,
                     List<Expr> arguments)
  {
    super(Universe.instance,
          // provide dummy name such that we see if this is used accidentally:
          new ParsedName(pos, "unresolved tuple"),
          arguments);
  }


  /*-----------------------------  methods  -----------------------------*/


  /** {@inheritDoc}
   *
   * Instead of lookup by name, set the `_calledFeature` directly.
   */
  @Override
  void loadCalledFeature(Resolution res, Context context)
  {
    if (PRECONDITIONS) require
      (context != null);

    _calledFeature = actuals().isEmpty() ? Types.resolved.f_unit
                                         : Types.resolved.f_tuple;

    super.loadCalledFeature(res, context);
  }


  @Override
  public ParsedType asParsedType()
  {
    var t0 = super.asParsedType();
    return t0 == null ? null
                      : new ParsedTupleType(pos(), t0.generics());
  }


}

/* end of file */
