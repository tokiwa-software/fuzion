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
 * Source of class ParsedOperatorCall
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.FuzionConstants;
import dev.flang.util.List;


/**
 * Any call that was created by the parser from an infix, prefix or postfix
 * operator.  This is used to distinguish a call like
 *
 *   -a
 *
 * from
 *
 *   a.prefix -
 *
 * This is needed since the former could serve as a partial call and expand
 * to
 *
 *  x -> x-a
 *
 * while this should not be possible for the latter.
 */
public class ParsedOperatorCall extends ParsedCall
{

  /*-----------------------------  fields  ------------------------------*/


  /**
   * operator precedence, used to check the need to fix associativity once
   * the actual infix or infix_right operator is known.
   */
  public final int _precedence;


  /**
   * Has this been put into parentheses? If so, it may no longer be used as
   * chained boolean {@code (a < b) < c}, but it may still used as partial call
   * {@code l.map (+x)}.
   */
  boolean _inParentheses = false;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a prefix or postfix operator on target.
   *
   * @param target the target of the call.
   *
   * @param name the name of the called feature
   *
   * @param precedence the operator precedence
   */
  public ParsedOperatorCall(Expr target, ParsedName name, int precedence)
  {
    super(target, name);
    _precedence = precedence;
  }


  /**
   * Constructor for an infix operator with target as left hand
   * side and argument as right hand side.
   * arguments 'la'.
   *
   * @param target the left hand side.
   *
   * @param name the name of the called feature
   *
   * @param precedence the operator precedence
   *
   * @param rhs the right hand side
   */
  public ParsedOperatorCall(Expr target, ParsedName name, int precedence, Expr rhs)
  {
    super(target, name, new List<>(rhs));
    if (PRECONDITIONS) require
      (rhs != Universe.instance);

    _precedence = precedence;
  }


  /*-------------------------  static methods  --------------------------*/


  /**
   * For a given feature name, check if it is an operator call that has to be
   * looked up under different actual names. If so, return all the actual names.
   *
   * This is used for infix operators that might be `infix` or `infix_right` and
   * for unary operators that might be `prefix` or `postfix`.
   *
   * @return a non-empty list containing either just `name` or the list of
   * possible names that would match the given operator name.
   */
  public static List<String> lookupNames(String name)
  {
    var result = new List<String>();
    if (name.startsWith(FuzionConstants.UNARY_OPERATOR_PREFIX))
      {
        var op = name.substring(FuzionConstants.UNARY_OPERATOR_PREFIX.length());
        result.add(FuzionConstants.PREFIX_OPERATOR_PREFIX  + op);
        result.add(FuzionConstants.POSTFIX_OPERATOR_PREFIX + op);
      }
    else if (name.startsWith(FuzionConstants.INFIX_RIGHT_OR_LEFT_OPERATOR_PREFIX))
      {
        var op = name.substring(FuzionConstants.INFIX_RIGHT_OR_LEFT_OPERATOR_PREFIX.length());
        result.add(FuzionConstants.INFIX_OPERATOR_PREFIX + op);
        result.add(FuzionConstants.INFIX_RIGHT_OPERATOR_PREFIX + op);
      }
    else
      {
        result.add(name);
      }
    return result;
  }

  /*-----------------------------  methods  -----------------------------*/


  /**
   * Is this an operator call like {@code a+b} or {@code -x} in contrast to a named call {@code f}
   * or {@code t.g}?
   *
   * @param parenthesesAllowed true if an operator call in parentheses is still
   * ok.  {@code (+x)}.
   */
  boolean isOperatorCall(boolean parenthesesAllowed)
  {
    return parenthesesAllowed || !_inParentheses;
  }


  /**
   * Is this Expr put into parentheses {@code (}/{@code )}. If so, we no longer want to do
   * certain transformations like chained booleans {@code a < b < c} to {@code a < b && b < c}.
   */
  public void putInParentheses()
  {
    _inParentheses = true;
  }


  /**
   * In case this is a parsed infix operator call, fix the associativity: the
   * parser produces and AST assuming all infix operators are right associative
   * (which is wrong for most operators).  This call rotates the operators
   * accordingly if needed, i.e., changing
   *
   *    a - «b + c»
   *
   * into
   *
   *    «a - b» + c
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
   *
   * @return null in case nothing was done, otherwise the fully resolved new
   * call with fixed associativity.
   */
  @Override
  Call fixAssociativity(Resolution res, Context context)
  {
    Call result = null;
    if (isOperatorCall(true) && // outer may be in parentheses `(a - «b + c»)`
        _calledFeature.featureName().baseName().startsWith(FuzionConstants.INFIX_OPERATOR_PREFIX) &&
        (_actuals.size() == 1 || _actuals.size() == 2) &&
        _actuals.get(_actuals.size()-1) instanceof ParsedOperatorCall b_plus_c &&
        b_plus_c.isOperatorCall(false) && // next may not be in parentheses `a - (b + c)` should stay unchanged while `a - «b + c»` should become `(a - b) + c`.
        b_plus_c._precedence == _precedence)
      { // we need left associativity, so we swap this and b_plus_c:
        var b = b_plus_c._target;
        _actuals = _actuals.setOrClone(_actuals.size()-1, b);
        var a = _actuals   ; _actuals    = b_plus_c._actuals   ; b_plus_c._actuals    = a;
        var t = _target    ; _target     = b_plus_c            ; b_plus_c._target     = t;
        var p = _parsedName; _parsedName = b_plus_c._parsedName; b_plus_c._parsedName = p;
        var n = _name      ; _name       = b_plus_c._name      ; b_plus_c._name       = n;
        forceFreshResolve();
        b_plus_c.forceFreshResolve();
        result = resolveTypes(res, context);
      }
    return result;
  }


  /**
   * Force this call to be resolved again. This is required if _target or
   * _actuals have changed or the _name/_parsedName was changed such that a
   * different feature will be found.
   */
  void forceFreshResolve()
  {
    _resolvedFor = null;
    _actualsResolvedFor = null;
    _calledFeature = null;
    _resolvedFormalArgumentTypes = null;
    _type = null;
  }

}

/* end of file */
