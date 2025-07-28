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
 * Source of class DotType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * DotType is expression of the form 'xyz.type' that evaluates to an instance of
 * 'Type'.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class DotType extends ExprWithPos
{

  /*----------------------------  variables  ----------------------------*/


  /**
   * actual generic arguments, set by parser
   */
  private final UnresolvedType _lhs;


  /*-------------------------- constructors ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param lhs the left hand side of the dot-type.
   */
  public DotType(SourcePosition pos, Expr lhs)
  {
    super(pos);

    if (CHECKS) check
      (lhs != null, lhs.asParsedType() != null);

    _lhs = lhs.asParsedType();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this.
   */
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    return v.action(this);
  }


  /**
   * typeForInferencing returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  @Override
  AbstractType typeForInferencing()
  {
    // could be _lhs.typeType(); but
    // we can't be sure if feature of type
    // is already resolved, which would lead
    // to precondition failures.
    return null;
  }


  /**
   * Return this expression as an (unresolved) type.
   * This is null by default except for calls/this-expressions
   * that can be used as the left hand side in a dot-type-call.
   *
   * The type is returned as produced by the parser and needs
   * to be resolved with the context it is used in to be of
   * any use.
   */
  @Override
  public UnresolvedType asParsedType()
  {
    return _lhs;
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
   */
  Expr resolveTypes(Resolution res, Context context)
  {
    return new Call(pos(),
                    Universe.instance,
                    "type_as_value",
                    FuzionConstants.NO_SELECT,
                    new List<>(_lhs),
                    new List<>(),
                    null).resolveTypes(res, context);
  }


}

/* end of file */
