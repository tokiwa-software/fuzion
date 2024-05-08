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

import dev.flang.util.List;
import dev.flang.util.SourceRange;


/**
 * Contract <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Contract
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Empty list of conditions.
   */
  static final List<Cond> NO_COND = new List<>();


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
  public List<Cond> ens;


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
    ens = e == null || e.isEmpty() ? NO_COND : e;
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
        for (Cond c: ens) { c.visit(v, outer); }
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
        for (Cond c: ens) { c.visitExpressions(v); }
      }
  }


  public void addInheritedContract(AbstractFeature from)
  {
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
    for (var e : from.contract().req)
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
    for (var e : from.contract().ens)
      {
        ens.add(e);
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
    if ((req != null) && (!req.isEmpty()))
      {
        res.append("\n  require ").append(req);
      }
    if ((ens != null) && (!ens.isEmpty()))
      {
        res.append("\n  ensure ").append(ens);
      }
    return res.toString();
  }

}

/* end of file */
