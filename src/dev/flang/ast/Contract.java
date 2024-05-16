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
 * Source of class Contract
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.ANY;
import dev.flang.util.List;
import dev.flang.util.SourceRange;


/**
 * Contract <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Contract extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Empty list of conditions.
   */
  static final List<Cond> NO_COND = new List<>();
  static { NO_COND.freeze(); }


  /**
   * Empty contract
   */
  public static final Contract EMPTY_CONTRACT = new Contract(NO_COND, null, null,
                                                             NO_COND, null, null);


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  public List<Cond> req;

  /**
   *
   */
  public List<Cond> _declared_postconditions;

  /**
   * post-conditions inherited from redefined features.
   */
  public List<Cond> _inherited_postconditions = NO_COND;


  /**
   * Did the parser find `pre` / `post` or even `pre else` / `post then` ? These
   * might be present even if the condition list is NO_COND.
   */
  public final SourceRange _hasPre,     _hasPost;
  public final SourceRange _hasPreElse, _hasPostThen;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  public Contract(List<Cond> r, SourceRange hasPre,  SourceRange hasElse,
                  List<Cond> e, SourceRange hasPost, SourceRange hasThen)
  {
    _hasPre  = hasPre;
    _hasPost = hasPost;
    _hasPreElse  = hasElse;
    _hasPostThen = hasThen;
    req = r == null || r.isEmpty() ? NO_COND : r;
    _declared_postconditions = e == null || e.isEmpty() ? NO_COND : e;
  }


  /**
   * Constructor
   */
  public Contract(List<Cond> r, List<Cond> e)
  {
    this(r, null, null, e, null, null);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   */
  public void visit(FeatureVisitor v, AbstractFeature outer)
  {
    if (this != EMPTY_CONTRACT)
      {
        for (Cond c: req) { c.visit(v, outer); }
        for (Cond c: _inherited_postconditions) { c.visit(v, outer); }
        for (Cond c: _declared_postconditions) { c.visit(v, outer); }
      }
  }


  /**
   * visit all the expressions within this Contract.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited expressions
   */
  public void visitExpressions(ExpressionVisitor v)
  {
    if (this != EMPTY_CONTRACT)
      {
        for (Cond c: req) { c.visitExpressions(v); }
        for (Cond c: _inherited_postconditions) { c.visitExpressions(v); }
        for (Cond c: _declared_postconditions) { c.visitExpressions(v); }
      }
  }


  /**
   * Get a List of all post-conditions in order, i.e., inherited first in order
   * defined by order of the inherit-clauses, then declared.
   */
  public List<Cond> all_postconditions()
  {
    List<Cond> result;
    if (_inherited_postconditions.isEmpty())
      {
        result = _declared_postconditions;
      }
    else if (_declared_postconditions.isEmpty())
      {
        result = _inherited_postconditions;
      }
    else
      {

        /*
    // tag::fuzion_rule_SEMANTIC_CONTRACT_POST_ORDER[]
The conditions of a post-condition are checked at run-time in sequential source-code order after any inherited post-conditions have been checked. Inherited post-conditions of redefined inherited features are checked at runtime in the source code order of the `inherit` clause of the corresponding outer features.
    // end::fuzion_rule_SEMANTIC_CONTRACT_POST_ORDER[]
        */
        result = new List<>();
        result.addAll(_inherited_postconditions);
        result.addAll(_declared_postconditions);
      }
    return result;
  }


  /**
   * When redefining a feature, the original contract is inherited with
   * preconditions ORed and postconditions ANDed.  This feature performs this
   * condition inheritance.
   *
   * @param to the redefining feature that inherits a contract
   *
   * @param from the redefined feature this contract should inherit from.
   */
  public void addInheritedContract(AbstractFeature to, AbstractFeature from)
  {
    var c = from.contract();

    // precondition inheritance is the disjunction with the conjunction of all inherited conditions, i.e, in
    //
    //   a is
    //     f pre a; b; c => ...
    //   b : a is
    //     redef f pre else d; e; f =>
    //
    // b.f becomes
    //
    //   b : a is
    //     redef f pre (a && b && c) || (d && e && f) =>
    //
    for (var e : c.req)
      {
        // NYI: missing support precondition inheritance!
      }

    // postcondition inheritance is just the conjunction of all inherited conditions
    //
    //   a is
    //     f post a; b; c => ...
    //   b : a is
    //     redef f post then d; e; f =>
    //
    // b.f becomes
    //
    //     redef f post a && b && c && d && e && f =>
    //
    for (var e : c.all_postconditions())
      {
        var ne = e.clonePostCondition(to, from);
        _inherited_postconditions = _inherited_postconditions.addAfterUnfreeze(ne);
      }
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    StringBuffer res = new StringBuffer();
    if (_hasPre != null)
      {
        res
          .append("\n  pre ")
          .append(_hasPreElse != null ? "else " : "")
          .append(req);
      }
    if (_hasPost != null)
      {
        res
          .append("\n  post ")
          .append(_hasPostThen != null ? "then " : "")
          .append(_declared_postconditions);
      }
    return res.toString();
  }

}

/* end of file */
