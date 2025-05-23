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
import java.util.function.Supplier;

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
  protected ParsedName _parsedName;


  /**
   * An implicit call to {@code Function.call} might be added during resolution of a
   * Function value like {@code Lazy}.  To prevent repeated resolution to do this
   * repeatedly, this flag records that a call {@code x} has been pushed down to be
   * the target of a call {@code x.call}.
   *
   * Without this, this might happen repeatedly.
   */
  private boolean _pushedImplicitImmediateCall = false;


  /**
   * quick-and-dirty way to get unique values for temp fields in
   * findChainedBooleans.
   */
  private static int _chainedBoolTempId_ = 0;


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


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Is this an operator expression of the form {@code expr1 | expr2}?  This is used
   * by {@code asParsedType} for choice-type syntax sugar.
   *
   * @param parenthesesAllowed if true, {@code (expr1 | expr2)} is accepted, with an
   * arbitrary number of parentheses, if false there must not be any surrounding
   * parentheses.
   *
   * @true iff this is a call to {@code infix |}, possibly with surrounding
   * parentheses depending on the argument's value.
   */
  boolean isInfixPipe(boolean parenthesesAllowed)
  {
    return isOperatorCall(parenthesesAllowed) && name().equals(FuzionConstants.INFIX_PIPE) && _actuals.size() == 1;
  }


  /**
   * Is this an operator expression of the form {@code expr1 -> expr2}?  This is used
   * by {@code asParsedType} for function-type syntax sugar.
   *
   * @true iff this is a call to {@code infix ->}.
   */
  boolean isInfixArrow()
  {
    return isOperatorCall(true) && name().equals(FuzionConstants.INFIX_ARROW) && _actuals.size() == 1;
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
                l = new List<AbstractType>();
                getInfixPipeArgs(l);
                name = FuzionConstants.CHOICE_NAME;
                tt = null;
              }
            result = new ParsedType(pos(), name, l, tt);
          }
      }
    return result;
  }


  /**
   * For a ParsedCall `x | y | z`, add the corresponding types of `x`, `y` and
   * `z` to `l`.
   *
   * Note that the AST produced by the parser uses right associative operators,
   * so this is `x | «y | z»`.
   *
   * @param l list of types.
   */
  private void getInfixPipeArgs(List<AbstractType> l)
  {
    var t = target();
    var tt = t.asParsedType();
    if (t instanceof ParsedCall pc && pc.isInfixPipe(false))
      {
        l.addAll(tt.generics());
      }
    else
      {
        l.add(tt);
      }
    var next = _actuals.get(0);
    if (next instanceof ParsedCall ac &&
        // cur is `y | z` in  '... | x | y | z',
        // but not `x | (y | z)`!
        ac.isInfixPipe(false))
      {
        ac.getInfixPipeArgs(l);
      }
    else
      {
        l.add(next.asParsedType());
      }
  }


  @Override
  public ParsedName asParsedName()
  {
    if (!_actuals.isEmpty())
      {
        return null;
      }
    return _parsedName;
  }


  @Override
  public List<ParsedName> asQualifier()
  {
    if (!_actuals.isEmpty())
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
   * <pre>{@code a < b <= c}</pre>
   *
   * and convert it to
   *
   * <pre>{@code a < {tmp := b; tmp} && tmp <= c}</pre>
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
            Expr t1 = new Call(pos(), new Current(pos(), outer), tmp);
            Expr t2 = new Call(pos(), new Current(pos(), outer), tmp);
            var c = _actuals;
            ParsedCall movedTo = new ParsedCall(t2, new ParsedName(pos(), name()), c)
              {
                boolean isChainedBoolRHS() { return true; }
              };
            this._movedTo = movedTo;
            Expr as = new Assign(res, pos(), tmp, b, context);
            t1         = res.resolveType(t1     , context);
            as         = res.resolveType(as     , context);
            while (c.getLast() instanceof ParsedCall lastArg &&
                   lastArg.isOperatorCall(false) &&
                   lastArg.isValidOperatorInChainedBoolean())
              {
                String tmpName2 = FuzionConstants.CHAINED_BOOL_TMP_PREFIX + (_chainedBoolTempId_++);
                var tmp2 = new Feature(res,
                                       pos(),
                                       Visi.PRIV,
                                       b.type(),
                                       tmpName2,
                                       outer);
                Expr t21 = new Call(pos(), new Current(pos(), outer), tmp2);
                Expr t22 = new Call(pos(), new Current(pos(), outer), tmp2);
                var c2 = lastArg._actuals.getLast();
                ParsedCall movedTo2 = new ParsedCall(t22, new ParsedName(pos(), lastArg.name()), new List<>(c2))
                  {
                    boolean isChainedBoolRHS() { return true; }
                  };
                lastArg._movedTo = movedTo2;
                var b2 = lastArg._actuals.size() == 1 ? lastArg._target : lastArg._actuals.get(0);
                var lhs = new Call(lastArg.pos(), _target, new List<>(), new List<>(movedTo), Types.resolved.f_bool_AND);
                Expr as2 = new Assign(res, pos(), tmp2, b2, context);
                t21         = res.resolveType(t21     , context);
                as2         = res.resolveType(as2     , context);
                movedTo._actuals.set(movedTo._actuals.size()-1,
                                     new Block(new List<Expr>(as2, t21)));

                c = lastArg._actuals;
                _target = lhs;
                movedTo = movedTo2;
              }
            cb._actuals.set(cb._actuals.size()-1,
                            new Block(new List<Expr>(as, t1)));
            _actuals = new List<Expr>(movedTo);
            _calledFeature = Types.resolved.f_bool_AND;
            _resolvedFormalArgumentTypes  = null;  // _calledFeature changed, so formal arg types must be resolved again
            _pendingError = null;
            _name = _calledFeature.featureName().baseName();
            var result = res.resolveType(movedTo, context);
            _actuals = new List<Expr>(result);
          }
      }
  }


  /**
   * Predicate that is true if this call is the result of pushArgToTemp in a
   * chain of boolean operators.  This is used for longer chains such as
   *
   * <pre>
   *   {@code a < b <= c < d }
   * </pre>
   *
   * which is first converted into
   *
   * <pre>
   *   {@code (a < {t1 := b; t1} && t1 <= c) < d}
   * </pre>
   *
   * where this returns {@code true} for the call {@code t1 <= c}, that in the next steps
   * needs to get {@code c} stored into a temporary variable as well.
   */
  boolean isChainedBoolRHS()
  {
    return false;
  }


  /**
   * Is this a call to an operator that may be
   * considered valid in a chained boolean?
   * I.e.: {@literal <,>,≤,≥,=,<=,>=,!=}
   */
  private boolean isValidOperatorInChainedBoolean()
  {
    return
      _name.equals(FuzionConstants.INFIX_RIGHT_OR_LEFT_OPERATOR_PREFIX + "<") ||
      _name.equals(FuzionConstants.INFIX_RIGHT_OR_LEFT_OPERATOR_PREFIX + ">") ||
      _name.equals(FuzionConstants.INFIX_RIGHT_OR_LEFT_OPERATOR_PREFIX + "≤") ||
      _name.equals(FuzionConstants.INFIX_RIGHT_OR_LEFT_OPERATOR_PREFIX + "≥") ||
      _name.equals(FuzionConstants.INFIX_RIGHT_OR_LEFT_OPERATOR_PREFIX + "<=") ||
      _name.equals(FuzionConstants.INFIX_RIGHT_OR_LEFT_OPERATOR_PREFIX + ">=") ||
      _name.equals(FuzionConstants.INFIX_RIGHT_OR_LEFT_OPERATOR_PREFIX + "=") ||
      _name.equals(FuzionConstants.INFIX_RIGHT_OR_LEFT_OPERATOR_PREFIX + "!=") ||
      // && is used to chain the calls together, needed for longer chains like `a < b < c < d`.
      _name.equals(FuzionConstants.INFIX_OPERATOR_PREFIX + "&&");
  }


  /**
   * Check if this call is a chained boolean call of the form
   *
   * <pre>{@code
   *   b <= c < d
   * }</pre>
   *
   * or, if the LHS is also a chained bool
   *
   * <pre>{@code
   *   (a < {t1 := b; t1} && t1 <= c) < d
   * }</pre>
   *
   * and return the part of the LHS that has the term that will need to be
   * stored in a temp variable, {@code c}, as an argument, i.e., {@code b <= c} or {@code t1 <=
   * c}, resp.
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
   * Check if partial application would change this pre-/postfix call into an
   * infix operator, e.g., `[1,2,3].map (*2)` ->  `[1,2,3].map (x->x*2)`
   *
   * @param expectedType the expected function type
   *
   * @return true if expectedType.arity() is 1, this is an operator call of a
   * pre- or postfix operator.
   */
  boolean isPartialInfix(AbstractType expectedType)
  {
    return
      expectedType.arity() == 1 &&
      isOperatorCall(true)      &&
      (_name.startsWith(FuzionConstants.PREFIX_OPERATOR_PREFIX ) ||
       _name.startsWith(FuzionConstants.POSTFIX_OPERATOR_PREFIX)    );
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
      (expectedType.isFunctionTypeExcludingLazy());

    var paa = partiallyApplicableAlternative(res, context, expectedType);
    Expr l = paa != null ? resolveTypes(res, context)  // this ensures _calledFeature is set such that possible ambiguity is reported
                         : this;
    if (l == this  /* resolution did not replace this call by sth different */ &&
        _calledFeature != Types.f_ERROR /* resolution did not cause an error */    )
      {
        checkPartialAmbiguity(res, context, expectedType);
        if (// try to solve error through partial application, e.g., for `[["a"]].map String.from_codepoints`
            _pendingError != null                       ||

            // convert pre/postfix to infix, e.g., `1-` -> `x->1-x` */
            isPartialInfix(expectedType)                ||

            // otherwise, try to solve inconsistent type
            paa != null                              &&
            (typeForInferencing() == null ||
             !typeForInferencing().isFunctionType())       )
          {
            l = applyPartially(res, context, expectedType);
          }
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
            fo._feature.preAndCallFeature() != _calledFeature)
          {
            AstErrors.partialApplicationAmbiguity(pos(), _calledFeature, fo._feature);
            setToErrorState();
          }
      }
  }


  /**
   * After propagateExpectedType: if type inference up until now has figured out
   * that a Lazy or Function feature is expected, but the current expression is
   * not a Lazy or Function feature, then wrap this expression in a Lazy or
   * Function feature.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @param t the type this expression is assigned to.
   *
   * @return the resulting Lazy of Function feature
   */
  Expr applyPartially(Resolution res, Context context, AbstractType t)
  {
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
        if (n == 1 && _name.startsWith(FuzionConstants.PREFIX_OPERATOR_PREFIX))
          { // -v ==> x->x-v   -- swap target and first actual:
            if (CHECKS) check
              (Errors.any() || _actuals.size() == 0,
               _target != Universe.instance);

            _actuals.add(_target);
            _target = pns.get(0);
          }
        else
          { // fill up actuals with arguments of the lambda:
            for (var i = 0; i < n; i++)
              {
                var c = pns.get(i);
                if (CHECKS) check
                  (c != Universe.instance);
                _actuals.add(c);
              }
          }
        if (isPartialInfix(t))
          {
            _name =
              _name.startsWith(FuzionConstants.PREFIX_OPERATOR_PREFIX)
              ? /* -v ==> x->x-v */ FuzionConstants.INFIX_RIGHT_OR_LEFT_OPERATOR_PREFIX + _name.substring(FuzionConstants.PREFIX_OPERATOR_PREFIX .length())
              : /* v- ==> x->v-x */ FuzionConstants.INFIX_RIGHT_OR_LEFT_OPERATOR_PREFIX + _name.substring(FuzionConstants.POSTFIX_OPERATOR_PREFIX.length());
          }
        _calledFeature = null;
        _resolvedFormalArgumentTypes  = null;
        _pendingError = null;
        var fn = new Function(pos(),
                              pns,
                              this)
          {
            @Override
            AbstractType propagateTypeAndInferResult(Resolution res, Context context, AbstractType t, boolean inferResultType, Supplier<String> from)
            {
              var rs = super.propagateTypeAndInferResult(res, context, t, inferResultType, from);
              if (rs != Types.t_ERROR)
                {
                  updateTarget(res);
                }
              return rs;
            }
          };
        result = fn;
        fn.resolveTypes(res, context);
      }
    else
      {
        result = ERROR;
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
   * This is used for implicit calls to Function and Lazy values where {@code f()} is
   * converted to {@code f.call()}.
   *
   * The actual arguments of this call are moved over to the new
   * call, this call's arguments are replaced by Expr.NO_EXPRS.
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
    var wasLazy = typeForInferencing() != null && typeForInferencing().isLazyType();

    if (CHECKS) check
      (select() == FuzionConstants.NO_SELECT);

    var result = new Call(pos(),   // NYI: ParsedCall?
                          this /* this becomes target of "call" */,
                          name,
                          _actuals)
      {
        @Override
        Expr originalLazyValue()
        {
          return wasLazy ? ParsedCall.this : super.originalLazyValue();
        }
        @Override
        Expr propagateExpectedType(Resolution res, Context context, AbstractType expectedType, Supplier<String> from)
        {
          if (expectedType.isFunctionTypeExcludingLazy())
            { // produce an error if the original call is ambiguous with partial application
              ParsedCall.this.checkPartialAmbiguity(res, context, expectedType);
            }
          return super.propagateExpectedType(res, context, expectedType, null);
        }
      };
    _movedTo = result;
    _wasImplicitImmediateCall = true;
    _originalArgCount = _actuals.size();
    _actuals = ParsedCall.NO_PARENTHESES;
    return result;
  }


  @Override
  protected Call resolveImmediateFunctionCall(Resolution res, Context context)
  {
    Call result = this;

    // replace Function or Lazy value `l` by `l.call`:
    if (isImmediateFunctionCall() && !_pushedImplicitImmediateCall)
      {
        _pushedImplicitImmediateCall = true;
        result = pushCall(res, context, FuzionConstants.OPERATION_CALL).resolveTypes(res, context);
      }
    return result;
  }


  /**
   * Is this call returning a Function/lambda that should
   * immediately be called?
   */
  private boolean isImmediateFunctionCall()
  {
    return typeForInferencing() != null &&
      typeForInferencing().isFunctionTypeExcludingLazy() &&
      _calledFeature != Types.resolved.f_Function && // exclude inherits call in function type
      _calledFeature.arguments().size() == 0      &&
      _actuals != NO_PARENTHESES
      ||
      typeForInferencing() != null &&
      typeForInferencing().isLazyType()           &&   // we are `Lazy T`
      _calledFeature != Types.resolved.f_Lazy     &&   // but not an explicit call to `Lazy` (e.g., in inherits clause)
      _calledFeature.arguments().size() == 0      &&   // no arguments (NYI: maybe allow args for `Lazy (Function R V)`, then `l a` could become `l.call.call a`
      _actuals.isEmpty();                              // dto.
  }

}

/* end of file */
