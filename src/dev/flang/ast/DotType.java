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

import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * DotType is expression of the form 'xyz.type' that evaluates to an instance of
 * 'Type'.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class DotType extends Expr
{

  /*----------------------------  variables  ----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  private final SourcePosition _pos;


  /**
   * actual generic arguments, set by parser
   */
  public AbstractType _lhs;


  /*-------------------------- constructors ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param n
   */
  public DotType(SourcePosition pos, AbstractType lhs)
  {
    _pos = pos;
    _lhs = lhs;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  public SourcePosition pos()
  {
    return _pos;
  }



  /**
   * visit all the features, expressions, statements within this feature.
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
    _lhs = _lhs.visit(v, outer);
    return v.action(this, outer);
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this statement.
   */
  public Call resolveTypes(Resolution res, AbstractFeature outer)
  {
    AbstractType t = _lhs;
    var tc = new Call(pos(), new Universe(), "Types", new List<>(),  new List<>(),  new List<>());
    tc.resolveTypes(res, outer);
    return (new Call(_pos, tc, "get", new List<>(_lhs), new List<>(), new List<>())).resolveTypes(res, outer);
  }


}

/* end of file */
