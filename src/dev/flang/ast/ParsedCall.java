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
 * Source of class ParsedCall
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.ListIterator;

import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Any call that was created by the parser.
 */
public class ParsedCall extends Call
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Special value for an empty actuals lists to distinguish a call without
   * parenthesis ("a.b") from a call with parenthesis and an empty actual
   * arguments list ("a.b()").
   */
  public static final List<Expr> NO_PARENTHESES = new List<>();



  final List<Expr> _parsedActuals;
  boolean _appliedPartially = false;

  /**
   * Constructor to read a field in target t
   *
   * @param target the target of the call, null if none.
   *
   * @param name the name of the called feature
   */
  public ParsedCall(Expr target, ParsedName name)
  {
    super(name._pos, target, name._name);

    _parsedActuals = NO_PARENTHESES;// Expr.NO_EXPRS;
  }


  /**
   * Constructor to call feature with name 'n' on target 't' with actual
   * arguments 'la'.
   *
   * @param target the target of the call, null if none.
   *
   * @param name the name of the called feature
   *
   * @param arguments list of actual arguments
   */
  public ParsedCall(Expr target, ParsedName name, List<Expr> arguments)
  {
    this(name._pos, target, name, arguments);
  }


  /**
   * Constructor to call feature with name 'n' on target 't' with actual
   * arguments 'la'.
   *
   * @param target the target of the call, null if none.
   *
   * @param name the name of the called feature
   *
   * @param arguments list of actual arguments
   */
  public ParsedCall(SourcePosition pos, Expr target, ParsedName name, List<Expr> arguments)
  {
    super(pos, target, name._name, arguments);

    _parsedActuals = arguments;
  }


  /**
   * Constructor to call field 'n' on target 't' and select an open generic
   * variant.
   *
   * @param target the target of the call, null if none.
   *
   * @param name the name of the called feature
   *
   * @param select for selecting a open type parameter field, this gives the
   * index '.0', '.1', etc. -1 for none.
   */
  public ParsedCall(Expr target, ParsedName name, int select)
  {
    super(name._pos, target, name._name, select, NO_PARENTHESES);

    _parsedActuals = NO_PARENTHESES;
  }


  boolean isInfixPipe(boolean parenthesesAllowed)
  {
    return isOperatorCall(parenthesesAllowed) && name().equals("infix |") && _parsedActuals.size() == 1;
  }


  /**
   * True iff this call was performed giving 0 or more actual arguments in
   * parentheses.  This allows a distinction between "a.b" and "a.b()" if b has
   * no formal arguments and is of a fun type. In this case, "a.b" calls only b,
   * while "a.b()" is syntactic sugar for "a.b.call".
   *
   * @return true if parentheses were present.
   */
  public boolean hasParentheses()
  {
    return _appliedPartially || _parsedActuals != NO_PARENTHESES;
  }


  @Override
  public ParsedType asParsedType()
  {
    var target = target();
    var tt = target == null ? null : target().asParsedType();
    var ok = target == null || tt != null;
    var name = name();
    var l = new List<AbstractType>();
    if (ok)
      {
        if (tt != null && isInfixPipe(true))   // choice type syntax sugar: 'tt | arg'
          {
            if (target instanceof ParsedCall tc && tc.isInfixPipe(false))
              { // tt is `x | y` in  'x | y | arg',
                // but not `(x | y)`!
                l.addAll(tt.generics());
              }
            else
              { // `tt | arg` where `tt` is not itself `x | y`
                l.add(tt);
              }
            name = "choice";
            tt = null;
          }
        for (var a : _parsedActuals)
          {
            var at = a.asParsedType();
            ok = ok && at != null;
            l.add(at);
          }
      }
    return ok ? new ParsedType(pos(), name, l, tt)
              : null;
  }


  /*-------------------------------------------------------------------*/


  /**
   * Check if this call is a chained boolean call of the form
   *
   *   b <= c < d
   *
   * or, if the LHS is also a chained bool
   *
   *   (a < {t1 := b; t1} && t1 <= c) < d
   *
   * and return the part of the LHS that has the term that will need to be
   * stored in a temp variable, 'c', as an argument, i.e., 'b <= c' or 't1 <=
   * c', resp.
   *
   * @return the term whose RHS would have to be stored in a temp variable for a
   * chained boolean call.
   */
  private Call chainedBoolTarget(Resolution res, AbstractFeature thiz)
  {
    Call result = null;
    if (Types.resolved != null &&
        targetFeature(res, thiz) == Types.resolved.f_bool &&
        isInfixOperator() &&
        target() instanceof ParsedCall pc &&
        pc.isInfixOperator() &&
        pc.isOperatorCall(false))
      {
        result = (pc._actuals.get(0) instanceof Call acc && acc.isChainedBoolRHS())
          ? acc
          : pc;
      }
    return result;
  }


  /**
   * if loadCalledFeature is about to fail, try if we can convert this call into
   * a chain of boolean calls:
   *
   * check if we have a call of the form
   *
   *   a < b <= c
   *
   * and convert it to
   *
   *   a < {tmp := b; tmp} && tmp <= c
   */
  @Override
  protected void findChainedBooleans(Resolution res, AbstractFeature thiz)
  {
    var cb = chainedBoolTarget(res, thiz);
    if (cb != null && _actuals.size() == 1)
      {
        var b = res.resolveType(cb._actuals.getLast(), thiz);
        if (b.typeForInferencing() != Types.t_ERROR)
          {
            String tmpName = FuzionConstants.CHAINED_BOOL_TMP_PREFIX + (_chainedBoolTempId_++);
            var tmp = new Feature(res,
                                  pos(),
                                  Visi.PRIV,
                                  b.type(),
                                  tmpName,
                                  thiz);
            Expr t1 = new Call(pos(), new Current(pos(), thiz), tmp, -1);
            Expr t2 = new Call(pos(), new Current(pos(), thiz), tmp, -1);
            var movedTo = new Call(pos(), t2, name(), _parsedActuals)
              {
                boolean isChainedBoolRHS() { return true; }
              };
            this._movedTo = movedTo;
            Expr as = new Assign(res, pos(), tmp, b, thiz);
            t1 = res.resolveType(t1    , thiz);
            as = res.resolveType(as    , thiz);
            var result = res.resolveType(movedTo, thiz);
            cb._actuals.set(cb._actuals.size()-1,
                            new Block(new List<Expr>(as, t1)));
            _actuals = new List<Expr>(result);
            _calledFeature = Types.resolved.f_bool_AND;
            _pendingError = null;
            _name = _calledFeature.featureName().baseName();
          }
      }
  }


  /**
   * Does this call a non-generic infix operator?
   */
  private boolean isInfixOperator()
  {
    return
      _name.startsWith("infix ") &&
      (_actuals.size() == 1 /* normal infix operator 'a.infix + b' */                ||
       _actuals.size() == 2 /* infix on different target 'X.Y.Z.this.infix + a b' */    ) &&
      true; /* no check for _generics.size(), we allow infix operator to infer arbitrary number of type parameters */
  }


  @Override
  protected void splitOffTypeArgs(Resolution res, AbstractFeature calledFeature, AbstractFeature outer)
  {
    var g = new List<AbstractType>();
    var a = new List<Expr>();
    var ts = calledFeature.typeArguments();
    var tn = ts.size();
    var ti = 0;
    var vs = calledFeature.valueArguments();
    var vn = vs.size();
    var i = 0;
    ListIterator<Expr> ai = _actuals.listIterator();
    while (ai.hasNext())
      {
        var aa = ai.next();

        // check that ts[ti] is open type parameter only iff ti == tn-1, ie.,
        // only the last type parameter may be open
        if (CHECKS) check
          (ti >= tn-1 ||
           ts.get(ti).kind() == AbstractFeature.Kind.TypeParameter    ,
           ti != tn-1 ||
           ts.get(ti).kind() == AbstractFeature.Kind.TypeParameter     ||
           ts.get(ti).kind() == AbstractFeature.Kind.OpenTypeParameter);

        if (_actuals.size() - i > vn)
          {
            AbstractType t = _parsedActuals.get(i).asParsedType();
            if (t != null)
              {
                g.add(t);
              }
            ai.set(Expr.NO_VALUE);  // make sure visit() no longer visits this
            if (ts.get(ti).kind() != AbstractFeature.Kind.OpenTypeParameter)
              {
                ti++;
              }
          }
        else
          {
            a.add(aa);
          }
        i++;
      }
    _generics = g;
    _actuals = a;
  }


  @Override
  protected Call resolveImmediateFunctionCall(Resolution res, AbstractFeature outer)
  {
    Call result = this;

    // replace Function or Lazy value `l` by `l.call`:
    if (isImmediateFunctionCall())
      {
        result = pushCall(res, outer, "call").resolveTypes(res, outer);
      }
    return result;
  }


  /**
   * Is this call returning a Function/lambda that should
   * immediately be called?
   */
  private boolean isImmediateFunctionCall()
  {
    return
      _type.isFunctionType()                      &&
      _calledFeature != Types.resolved.f_Function && // exclude inherits call in function type
      _calledFeature.arguments().size() == 0      &&
      hasParentheses()
      ||
      _type.isLazyType()                          &&   // we are `Lazy T`
      _calledFeature != Types.resolved.f_Lazy     &&   // but not an explicit call to `Lazy` (e.g., in inherits clause)
      _calledFeature.arguments().size() == 0      &&   // no arguments (NYI: maybe allow args for `Lazy (Function R V)`, then `l a` could become `c.call.call a`
      _parsedActuals.isEmpty()                    &&   // dto.
      originalLazyValue() == this;                     // prevent repeated `l.call.call` when resolving the newly created Call to `call`.
  }

}

/* end of file */
