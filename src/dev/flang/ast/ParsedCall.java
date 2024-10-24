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

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;


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


  /*----------------------------  variables  ----------------------------*/


  /**
   * The name of the called feature as produced by the parser.
   */
  private final ParsedName _parsedName;


  /*---------------------------  constructors  --------------------------*/


  /**
   * Constructor to call feature without any arguments in the default target
   *
   * @param name the name of the called feature
   */
  public ParsedCall(ParsedName name)
  {
    this(null, name);
  }


  /**
   * Constructor to call feature without any arguments in target t
   *
   * @param target the target of the call, null if none.
   *
   * @param name the name of the called feature
   */
  public ParsedCall(Expr target, ParsedName name)
  {
    this(target, name, NO_PARENTHESES);
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
    super(name._pos, target, name._name, arguments);

    _parsedName = name;
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

    _parsedName = name;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Is this an operator expression of the form `expr1 | expr2`?  This is used
   * by `asParsedType` for choice-type syntax sugar.
   *
   * @param parenthesesAllowed if true, `(expr1 | expr2)` is accepted, with an
   * arbitrary number of parentheses, if false there must not be any surrounding
   * parentheses.
   *
   * @true iff this is a call to `infix |`, possibly with surrounding
   * parentheses depending on the argument's value.
   */
  boolean isInfixPipe(boolean parenthesesAllowed)
  {
    return isOperatorCall(parenthesesAllowed) && name().equals("infix |") && _actuals.size() == 1;
  }


  /**
   * Is this an operator expression of the form `expr1 -> expr2`?  This is used
   * by `asParsedType` for function-type syntax sugar.
   *
   * @true iff this is a call to `infix ->`.
   */
  boolean isInfixArrow()
  {
    return isOperatorCall(true) && name().equals("infix ->") && _actuals.size() == 1;
  }


  /*
   * Returns either result of asParsedType()
   * or Types.t_UNDEFINED in case types name is '_'.
   */
  @Override
  public AbstractType asType()
  {
    return name().equals("_")
      ? Types.t_UNDEFINED
      : asParsedType();
  }


  @Override
  public ParsedType asParsedType()
  {
    ParsedType result = null;
    var target = target();
    var tt = target == null ? null : target().asParsedType();
    var ok = target == null || tt != null;
    var name = name();
    var l = new List<AbstractType>();
    for (var a : _actuals)
      {
        var at = a.asParsedType();
        ok = ok && at != null;
        l.add(at);
      }
    if (ok)
      {
        if (tt != null && isInfixArrow())
          {
            // NYI: a function type like `a.b->c` is currently parsed as a call
            // to `infix ->`. Would be better if the parser generated the
            // function type directly, for new we do it here:
            var a = new List<AbstractType>(tt);
            // NYI: if tt is of the form `()` or `(a,b,c)`, we need to create an
            // empty list or a list of types `a`, `b` and `c`.
            result =  UnresolvedType.funType(pos(), l.getFirst(), a);
          }
        else
          {
            if (tt != null && isInfixPipe(true))   // choice type syntax sugar: 'tt | arg'
              {
                var l2 = new List<AbstractType>();
                if (target instanceof ParsedCall tc && tc.isInfixPipe(false))
                  { // tt is `x | y` in  'x | y | arg',
                    // but not `(x | y)`!
                    l2.addAll(tt.generics());
                  }
                else
                  { // `tt | arg` where `tt` is not itself `x | y`
                    l2.add(tt);
                  }
                l2.addAll(l);
                l = l2;
                name = "choice";
                tt = null;
              }
            result = new ParsedType(pos(), name, l, tt);
          }
      }
    return result;
  }


  @Override
  public ParsedName asParsedName()
  {
    if (!_actuals.isEmpty() || _select != -1)
      {
        return null;
      }
    return _parsedName;
  }


  @Override
  public List<ParsedName> asQualifier()
  {
    if (!_actuals.isEmpty() || _select != -1)
      {
        return null;
      }
    var t = target();
    var l = t == null ? new List<ParsedName>() : t.asQualifier();
    if (l != null)
      {
        l.add(_parsedName);
      }
    return l;
  }


  /*-------------------------------------------------------------------*/


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
   *
   * @param res Resolution instance
   *
   * @param context the source code context where this Call is used
   */
  @Override
  protected void findChainedBooleans(Resolution res, Context context)
  {
    var cb = chainedBoolTarget(res, context);
    if (cb != null && _actuals.size() == 1)
      {
        var b = res.resolveType(cb._actuals.getLast(), context);
        if (b.typeForInferencing() != Types.t_ERROR)
          {
            var outer = context.outerFeature();
            String tmpName = FuzionConstants.CHAINED_BOOL_TMP_PREFIX + (_chainedBoolTempId_++);
            var tmp = new Feature(res,
                                  pos(),
                                  Visi.PRIV,
                                  b.type(),
                                  tmpName,
                                  outer);
            Expr t1 = new Call(pos(), new Current(pos(), outer), tmp, -1);
            Expr t2 = new Call(pos(), new Current(pos(), outer), tmp, -1);
            var movedTo = new ParsedCall(t2, new ParsedName(pos(), name()), _actuals)
              {
                boolean isChainedBoolRHS() { return true; }
              };
            this._movedTo = movedTo;
            Expr as = new Assign(res, pos(), tmp, b, context);
            t1         = res.resolveType(t1     , context);
            as         = res.resolveType(as     , context);
            var result = res.resolveType(movedTo, context);
            cb._actuals.set(cb._actuals.size()-1,
                            new Block(new List<Expr>(as, t1)));
            _actuals = new List<Expr>(result);
            _calledFeature = Types.resolved.f_bool_AND;
            _resolvedFormalArgumentTypes  = null;  // _calledFeature changed, so formal arg types must be resolved again
            _pendingError = null;
            _name = _calledFeature.featureName().baseName();
          }
      }
  }


  /**
   * Predicate that is true if this call is the result of pushArgToTemp in a
   * chain of boolean operators.  This is used for longer chains such as
   *
   *   a < b <= c < d
   *
   * which is first converted into
   *
   *   (a < {t1 := b; t1} && t1 <= c) < d
   *
   * where this returns 'true' for the call 't1 <= c', that in the next steps
   * needs to get 'c' stored into a temporary variable as well.
   */
  boolean isChainedBoolRHS()
  {
    return false;
  }


  /**
   * Is this a call to an operator that may be
   * considered valid in a chained boolean?
   * I.e.: <,>,≤,≥,=,<=,>=,!=
   */
  private boolean isValidOperatorInChainedBoolean()
  {
    return
      _name.equals("infix <") ||
      _name.equals("infix >") ||
      _name.equals("infix ≤") ||
      _name.equals("infix ≥") ||
      _name.equals("infix <=") ||
      _name.equals("infix >=") ||
      _name.equals("infix =") ||
      _name.equals("infix !=") ||
      // && is used to chain the calls together.
      _name.equals("infix &&");
  }


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
   * @param res Resolution instance
   *
   * @param context the source code context where this Call is used
   *
   * @return the term whose RHS would have to be stored in a temp variable for a
   * chained boolean call.
   */
  private Call chainedBoolTarget(Resolution res, Context context)
  {
    Call result = null;
    if (Types.resolved != null &&
        targetFeature(res, context) == Types.resolved.f_bool &&
        isValidOperatorInChainedBoolean() &&
        target() instanceof ParsedCall pc &&
        pc.isValidOperatorInChainedBoolean() &&
        pc.isOperatorCall(false))
      {
        result = (pc._actuals.get(0) instanceof ParsedCall acc && acc.isChainedBoolRHS())
          ? acc
          : pc;
      }
    return result;
  }


  /**
   * Perform partial application for a Call. In particular, this can make the
   * following changes:
   *
   *   f x y      ==>  a,b,c -> f x y a b c
   *   ++ x       ==>  a -> a ++ x
   *   x ++       ==>  a -> x ++ a
   *
   * @see Expr#propagateExpectedTypeForPartial for details.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @param expectedType the expected type.
   */
  @Override
  Expr propagateExpectedTypeForPartial(Resolution res, Context context, AbstractType expectedType)
  {
    if (PRECONDITIONS) require
      (expectedType.isFunctionType());

    // NYI: CLEANUP: The logic in this method seems overly complex, there might be potential to simplify!
    Expr l = this;
    if (partiallyApplicableAlternative(res, context, expectedType) != null)
      {
        if (_calledFeature != null)
          {
            res.resolveTypes(_calledFeature);
            var rt = _calledFeature.resultTypeIfPresent(res);
            if (rt != null && (!rt.isAnyFunctionType() || rt.arity() != expectedType.arity()))
              {
                l = applyPartially(res, context, expectedType);
              }
          }
        else
          {
            if (_pendingError == null)
              {
                l = resolveTypes(res, context);  // this ensures _calledFeature is set such that possible ambiguity is reported
              }
            if (l == this)
              {
                l = applyPartially(res, context, expectedType);
              }
          }
      }
    else if (_pendingError != null                   || /* nothing found */
             newNameForPartial(expectedType) != null    /* search for a different name */)
      {
        l = applyPartially(res, context, expectedType);
      }
    return l;
  }


  /**
   * check that partial application would not lead to to ambiguity. See
   * tests/partial_application_negative for examples: In case a call can be made
   * directly and partial application would find another possible target that
   * would also be valid, we flag an error.
   *
   * This prevents the situation where a library API change that adds a new
   * feature f' with fewer arguments than an existing feature f would result in
   * code that used partial application in a call to f to suddenly call f'
   * without notice.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @param expectedType the expected type.
   */
  void checkPartialAmbiguity(Resolution res, Context context, AbstractType expectedType)
  {
    if (_calledFeature != null && _calledFeature != Types.f_ERROR && this instanceof ParsedCall)
      {
        var fo = partiallyApplicableAlternative(res, context, expectedType);
        if (fo != null &&
            fo._feature != _calledFeature &&
            newNameForPartial(expectedType) == null)
          {
            AstErrors.partialApplicationAmbiguity(pos(), _calledFeature, fo._feature);
            setToErrorState();
          }
      }
  }


  /**
   * After propagateExpectedType: if type inference up until now has figured
   * out that a Lazy feature is expected, but the current expression is not
   * a Lazy feature, then wrap this expression in a Lazy feature.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @param t the type this expression is assigned to.
   */
  public Expr applyPartially(Resolution res, Context context, AbstractType t)
  {
    checkPartialAmbiguity(res, context, t);
    Expr result;
    var n = t.arity();
    if (mustNotContainDeclarations("a partially applied function call", context.outerFeature()))
      {
        _pendingError = null;
        List<Expr> pns = new List<>();
        for (var i = 0; i < n; i++)
          {
            pns.add(Partial.argName(pos()));
          }
        _actuals = _actuals.size() == 0 ? new List<>() : _actuals;
        if (_name.startsWith(FuzionConstants.PREFIX_OPERATOR_PREFIX))
          { // -v ==> x->x-v   -- swap target and first actual:
            if (CHECKS) check
              (Errors.any() || n == 1,
               Errors.any() || _actuals.size() == 0);
            _actuals.add(_target);
            _target = pns.get(0);
          }
        else
          { // fill up actuals with arguments of the lambda:
            for (var i = 0; i < n; i++)
              {
                var c = pns.get(i);
                _actuals.add(c);
              }
          }
        var nn = newNameForPartial(t);
        if (nn != null)
          {
            _name = nn;
          }
        _calledFeature = null;
        _resolvedFormalArgumentTypes  = null;
        _pendingError = null;
        var fn = new Function(pos(),
                              pns,
                              this)
          {
            @Override
            public AbstractType propagateTypeAndInferResult(Resolution res, Context context, AbstractType t, boolean inferResultType)
            {
              var rs = super.propagateTypeAndInferResult(res, context, t, inferResultType);
              updateTarget(res);
              return rs;
            }
          };
        result = fn;
        fn.resolveTypes(res, context);
      }
    else
      {
        result = ERROR_VALUE;
      }
    return result;
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
            AbstractType t = _actuals.get(i).asType();
            if (t != null)
              {
                g.add(t);
              }
            ai.set(Expr.NO_VALUE);  // make sure visit() no longer visits this
            if (ti > ts.size() && ts.get(ti).kind() != AbstractFeature.Kind.OpenTypeParameter)
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


  /**
   * Create a new call and push the current call to the target of that call.
   * This is used for implicit calls to Function and Lazy values where `f()` is
   * converted to `f.call()`, and for implicit fields in a select call such as,
   * e.g., a tuple access `t.3` that is converted to `t.values.3`.
   *
   * The actual arguments and _select of this call are moved over to the new
   * call, this call's arguments are replaced by Expr.NO_EXPRS and this calls
   * _select is set to -1.
   *
   * @param res Resolution instance
   *
   * @param context the source code context where this Call is used
   *
   * @param name the name of the feature to be called.
   *
   * @return the newly created call
   */
  Call pushCall(Resolution res, Context context, String name)
  {
    var wasLazy = _type != null && _type.isLazyType();
    var result = new Call(pos(),   // NYI: ParsedCall?
                          this /* this becomes target of "call" */,
                          name,
                          select(),
                          NO_GENERICS,
                          _actuals,
                          null,
                          null)
      {
        @Override
        Expr originalLazyValue()
        {
          return wasLazy ? ParsedCall.this : super.originalLazyValue();
        }
        @Override
        public Expr propagateExpectedType(Resolution res, Context context, AbstractType expectedType)
        {
          if (expectedType.isFunctionType())
            { // produce an error if the original call is ambiguous with partial application
              ParsedCall.this.checkPartialAmbiguity(res, context, expectedType);
            }
          return super.propagateExpectedType(res, context, expectedType);
        }
      };
    _movedTo = result;
    _wasImplicitImmediateCall = true;
    _originalArgCount = _actuals.size();
    _actuals = ParsedCall.NO_PARENTHESES;
    _select = -1;
    return result;
  }


  @Override
  Call resolveImplicitSelect(Resolution res, Context context, AbstractType t)
  {
    Call result = this;
    if (_select >= 0 && !t.isGenericArgument())
      {
        var f = res._module.lookupOpenTypeParameterResult(t.feature(), this);
        if (f != null)
          {
            // replace Function call `c.123` by `c.f.123`:
            result = pushCall(res, context, f.featureName().baseName());
            setActualResultType(res, context, t); // setActualResultType will be done again by resolveTypes, but we need it now.
            result = result.resolveTypes(res, context);
          }
      }
    return result;
  }


  @Override
  protected Call resolveImmediateFunctionCall(Resolution res, Context context)
  {
    Call result = this;

    // replace Function or Lazy value `l` by `l.call`:
    if (isImmediateFunctionCall())
      {
        result = pushCall(res, context, "call").resolveTypes(res, context);
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
      _actuals != NO_PARENTHESES
      ||
      _type.isLazyType()                          &&   // we are `Lazy T`
      _calledFeature != Types.resolved.f_Lazy     &&   // but not an explicit call to `Lazy` (e.g., in inherits clause)
      _calledFeature.arguments().size() == 0      &&   // no arguments (NYI: maybe allow args for `Lazy (Function R V)`, then `l a` could become `c.call.call a`
      _actuals.isEmpty()                          &&   // dto.
      originalLazyValue() == this;                     // prevent repeated `l.call.call` when resolving the newly created Call to `call`.
  }

}

/* end of file */
