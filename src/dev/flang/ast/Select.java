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
 * Source of class Select
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.List;
import dev.flang.util.SourcePosition;

public class Select extends Call {


  private Call _currentlyResolving;


  public Select(SourcePosition pos, Expr target, String name, int select)
  {
    super(pos, target, name, select, NO_GENERICS, Expr.NO_EXPRS, null, null);

    if (PRECONDITIONS) require
      (select >= 0,
       target != Call.ERROR);
  }


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this or an alternative Expr if the action performed during the
   * visit replaces this by the alternative.
   */
  @Override
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    if (_currentlyResolving != null)
      {
        _currentlyResolving.visit(v, outer);
        return this;
      }
    else
      {
        if (_target != null)
          {
            _target = _target.visit(v, outer);
          }
        v.action((AbstractCall) this);
        return v.action(this);
      }
  }


  @Override
  public AbstractFeature calledFeature()
  {
    return _calledFeature != null
      ? _calledFeature
      : Types.f_ERROR;
  }


  @Override
  public String toString()
  {
    return _target.toStringWrapped() + "." + (_name == null ? "<not yet known>" : _name) + "." + _select;
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
    throw new UnsupportedOperationException();
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
   */
  public Call resolveTypes(Resolution res, Context context)
  {
    var result = Call.ERROR;
    if (_name == null)
      {
        _currentlyResolving = resolveImplicit(res, context, _target.type());
      }
    else
      {
        loadCalledFeature(res, context);
        if (_calledFeature != null)
          {
            _currentlyResolving = _calledFeature.resultTypeIfPresentUrgent(res, true).isOpenGeneric()
              // explicit
              ? new Call(pos(), _target, _name, _select, Call.NO_GENERICS, NO_EXPRS, null, null)
              // implict
              : resolveImplicit(res, context, getActualResultType(res, context, true));
          }
      }
    if (_currentlyResolving != null)
      {
        result = _currentlyResolving.resolveTypes(res, context);
      }
    return result;
  }


  /**
   * Helper to try and implicitly resolve this select
   * in case explicit is not possible.
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this assignment is used
   *
   */
  private Call resolveImplicit(Resolution res, Context context, AbstractType at)
  {
    var result = Call.ERROR;

    var typeParameter = at.isGenericArgument() ? at.genericArgument().constraint(context).feature() : at.feature();
    var f = res._module.lookupOpenTypeParameterResult(typeParameter, this);

    if (f != null)
      {
        if (_name == null)
          {
            result = new Call(pos(), _target, f.featureName().baseName(), _select, Call.NO_GENERICS, NO_EXPRS, null, null);
          }
        else
          {
            var selectTarget = new Call(pos(), _target, _name, -1, Call.NO_GENERICS, NO_EXPRS, null, null);
            result = new Call(pos(), selectTarget, f.featureName().baseName(), _select, Call.NO_GENERICS, NO_EXPRS, null, null);
          }
      }
    else
      {
        AstErrors.useOfSelectorRequiresCallWithOpenGeneric(pos(), _calledFeature, _name, _select, at);
      }
    return result;
  }


  /**
   * perform static type checking, i.e., make sure that in all assignments from
   * actual to formal arguments, the types match.
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
   */
  @Override
  void checkTypes(Resolution res, Context context)
  {
    throw new UnsupportedOperationException("select should have been replaced in resolveTypes");
  }


  @Override
  public UnresolvedType asParsedType()
  {
    return null;
  }


  @Override
  public ParsedName asParsedName()
  {
    throw new UnsupportedOperationException("Select.asParsedName");
  }


  @Override
  public AbstractType asType()
  {
    AstErrors.selectIsNoType(_target != null ? _target.pos().rangeTo(pos().byteEndPos()) : pos());
    return Types.t_ERROR;
  }


  @Override
  public List<ParsedName> asQualifier()
  {
    throw new UnsupportedOperationException("Select.asQualifier");
  }

}
