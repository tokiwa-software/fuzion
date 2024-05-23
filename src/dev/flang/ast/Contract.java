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
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
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
                                                             NO_COND, null, null,
                                                             NO_COND, null,
                                                             NO_COND, null);


  /*--------------------------  static fields  --------------------------*/


  /**
   * Id used to generate unique names for pre- and postcondution features.
   */
  public static int _id_ = 0;


  /**
   * Reset static fields
   */
  public static void reset()
  {
    _id_ = 0;
  }


  /*----------------------------  variables  ----------------------------*/


  private List<AbstractFeature> _inherited = new List<>();


  /**
   *
   */
  public List<Cond> req;
  public List<Cond>            _declared_preconditions_as_feature;
  public List<AbstractFeature> _declared_preconditions_as_feature_args;
  Feature _preFeature;

  /**
   *
   */
  public List<Cond>            _declared_postconditions;
  public List<Cond>            _declared_postconditions_as_feature;
  public List<AbstractFeature> _declared_postconditions_as_feature_args;
  public Feature _postFeature;

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
                  List<Cond> e, SourceRange hasPost, SourceRange hasThen,
                  List<Cond> preAsFeature, List<AbstractFeature> preArgs,
                  List<Cond> postAsFeature, List<AbstractFeature> postArgs)
  {
    _hasPre  = hasPre;
    _hasPost = hasPost;
    _hasPreElse  = hasElse;
    _hasPostThen = hasThen;
    req = r == null || r.isEmpty() ? NO_COND : r;
    _declared_preconditions_as_feature = preAsFeature == null || preAsFeature.isEmpty() ? NO_COND : preAsFeature;
    _declared_preconditions_as_feature_args = preArgs;
    _declared_postconditions = e == null || e.isEmpty() ? NO_COND : e;
    _declared_postconditions_as_feature = postAsFeature == null || postAsFeature.isEmpty() ? NO_COND: postAsFeature;
    _declared_postconditions_as_feature_args = postArgs;
  }


  /**
   * Constructor use for contract loaded from fum file
   */
  public Contract(List<Cond> r, List<Cond> e, List<Cond> e2)
  {
    this(r, null, null, e, null, null, null, null, e2, null);
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
      }
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

    if (c != EMPTY_CONTRACT)
      {
        _inherited.add(from);
      }
  }


  public boolean hasPostConditionsFeature()
  {
    return !_declared_postconditions_as_feature.isEmpty();
  }


  private String _postConditionFeatureName = null;
  String postConditionsFeatureName(AbstractFeature f)
  {
    if (PRECONDITIONS) require
      (hasPostConditionsFeature());

    if (_postConditionFeatureName == null)
      {
        _postConditionFeatureName = FuzionConstants.POSTCONDITION_FEATURE_PREFIX + f.featureName().baseName() +  "_" + (_id_++);
      }
    return _postConditionFeatureName;
  }

  Call callPostCondition(Resolution res, Feature outer)
  {
    var p = _hasPost;
    List<Expr> args = new List<>();
    for (var a : outer.valueArguments())
      {
        var ca = new Call(p,
                          new Current(p, outer),
                          a,
                          -1);
        ca = ca.resolveTypes(res, outer);
        args.add(ca);
      }
    if (outer.hasResultField())
      {
        var c2 = new Call(p,
                          new Current(p, outer),
                          outer.resultField(),
                          -1);
        c2 = c2.resolveTypes(res, outer);
        args.add(c2);
      }
    return callPostCondition(res, outer, outer, outer, args);
  }
  Call callPostCondition(Resolution res, AbstractFeature outer, Feature actualOuterAfterInheritance, Feature in, List<Expr> args)
  {
    var p = _hasPost != null ? _hasPost : outer.pos();
    Expr t = null;
    var or = in.outerRef();
    if (or != null)
      {
        t = new This(p, in, in.outer()).resolveTypes(res, in);
      }
    else
      {
        t = new Universe();
      }
    AbstractFeature pF = null;
    if (outer instanceof Feature of)
      {
        addContractFeatures(of, res);
        pF = _postFeature;
      }
    else
      {
        pF = outer.postFeature();
      }
    var callPostCondition = new Call(p,
                                     t,
                                     in.generics().asActuals(),
                                     args,
                                     pF,
                                     Types.resolved.t_unit);
    callPostCondition = callPostCondition.resolveTypes(res, in);
    return callPostCondition;
  }

  /**
   * Part of the syntax sugar phase: For all contracts, create artificial
   * features that check that contract.
   */
  void addContractFeatures(Feature f, Resolution res)
  {
    if (PRECONDITIONS) require
      (f != null,
       res != null,
       //       res.state(f) == State.RESOLVING_SUGAR1,
       Errors.any() || !f.isUniverse() || (_declared_preconditions_as_feature.isEmpty() &&
                                           _declared_postconditions_as_feature.isEmpty()));

    // NYI: code to add precondition feature missing

    // add postcondition feature
    if (hasPostConditionsFeature() && _postFeature == null)
      {
        var name = postConditionsFeatureName(f);
        var args = new List<AbstractFeature>(_declared_postconditions_as_feature_args);
        var resultField = new Feature(_hasPost,
                                      Visi.PRIV,
                                      f.resultType(), // NYI: replace type parameter of f by type parameters of _postFeature!
                                      FuzionConstants.RESULT_NAME)
          {
            public boolean isResultField() { return true; }
          };
        args.add(resultField);
        var l = new List<Expr>();
        for (var c : _declared_postconditions_as_feature)
          {
            var p = c.cond.pos();
            l.add(new If(p,
                         c.cond,
                         new Block(),
                         new ParsedCall(new ParsedCall(new ParsedCall(new ParsedName(p, "fuzion")), new ParsedName(p, "runtime")), new ParsedName(p, "postcondition_fault"),
                                        new List<>(new StrConst(p, p.sourceText()))
                                        )
                         )
                  );
          }
        var code = new Block(l);
        _postFeature = new Feature(_hasPost,
                                   f.visibility(),
                                   f.modifiers() & FuzionConstants.MODIFIER_FIXED, // modifiers
                                   NoType.INSTANCE,
                                   new List<>(name),
                                   args,
                                   new List<>(), // inheritance
                                   Contract.EMPTY_CONTRACT,
                                   new Impl(_hasPost, code, Impl.Kind.RoutineDef));
        res._module.findDeclarations(_postFeature, f.outer());
        res.resolveDeclarations(_postFeature);
        res.resolveTypes(_postFeature);

        // We add calls to postconditions of redefined features after creating _postFeature since
        // this enables us to access _postFeaturealready:
        List<Expr> l2 = null;
        for (var inh : _inherited)
          {
            var ic = inh.contract();
            if (ic.hasPostConditionsFeature())
              {
                List<Expr> args2 = new List<>();
                for (var a : args)
                  {
                    var p = _hasPost;
                    var ca = new Call(p,
                                      new Current(p, _postFeature),
                                      a,
                                      -1);
                    ca = ca.resolveTypes(res, _postFeature);
                    args2.add(ca);
                  }
                var inhpost = ic.callPostCondition(res, inh, f, _postFeature, args2);
                inhpost = inhpost.resolveTypes(res, _postFeature);
                if (l2 == null)
                  {
                    l2 = new List<>();
                  }
                l2.add(inhpost);
              }
          }
        if (l2 != null)
          {
            l2.addAll(code._expressions);
            code._expressions = l2;
          }
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
