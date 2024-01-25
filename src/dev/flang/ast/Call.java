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
 * Source of class Call
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Arrays;
import java.util.ListIterator;
import java.util.SortedMap;
import java.util.TreeMap;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;
import dev.flang.util.Pair;


/**
 * Call is an expression that is a call to a class and that results in
 * the result value of that class.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Call extends AbstractCall
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Special value for an empty actuals lists to distinguish a call without
   * parenthesis ("a.b") from a call with parenthesis and an empty actual
   * arguments list ("a.b()").
   */
  public static final List<Actual> NO_PARENTHESES = new List<>();


  /**
   * Empty map for general use.
   */
  public static final SortedMap<FeatureName, Feature> EMPTY_MAP = new TreeMap<>();


  /**
   * Dummy Call. Used to represent errors.
   */
  public static Call ERROR;


  /*------------------------  static variables  -------------------------*/

  /**
   * quick-and-dirty way to get unique values for temp fields in
   * findChainedBooleans.
   */
  static int _chainedBoolTempId_ = 0;


  /*----------------------------  variables  ----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  private final SourcePosition _pos;


  /**
   * name of called feature, set by parser
   */
  private String _name;
  public String name() { return _name; }


  /**
   * For a call a.b.4 with a select clause ".4" to pick a variant from a field
   * of an open generic type, this is the chosen variant.
   */
  private int _select;
  public int select() { return _select; }


  /**
   * actual generic arguments, set by parser
   */
  public /*final*/ List<AbstractType> _generics; // NYI: Make this final again when resolveTypes can replace a call
  public final List<AbstractType> _unresolvedGenerics;
  public List<AbstractType> actualTypeParameters()
  {
    var res = _generics;
    if (needsToInferTypeParametersFromArgs())
      {
        res = new List<>();
        for (Generic g : _calledFeature.generics().list)
          {
            if (!g.isOpen())
              {
                res.add(Types.t_UNDEFINED);
              }
          }
      }
    // res.freeze();  -- NYI: res.freeze not possible here since Function.propagateTypeAndInferResult performs gs.set
    return res;
  }


  /**
   * Actual arguments, set by parser
   */
  public List<Actual> _actualsNew;
  public List<Expr> _actuals;
  public List<Expr> actuals() { return _actuals; }


  /**
   * the target of the call, null for "this". Set by parser
   */
  private Expr _target;
  public Expr target() { return _target; }
  private FeatureAndOuter _targetFrom = null;


  /**
   * Since _target will be replaced during phases RESOLVING_DECLARATIONS or
   * RESOLVING_TYPES we keep a copy of the original.  We will need the original
   * later to check if there is an ambiguity between the found called feature
   * and the partial application of another feature,
   * see @partiallyApplicableAlternative).
   */
  private Expr _originalTarget = _target;


  /**
   * The feature that is called by this call, resolved when
   * loadCalledFeature() is called.
   */
  public AbstractFeature _calledFeature;


  /**
   * After an unsuccessful attempt was made to find the called feature, this
   * will be set to a Runnable that reports the corresponding error. The error
   * output is delayed because partial application may later fix this once we
   * know better what the expected target type is.
   */
  Runnable _pendingError = null;


  /**
   * Static type of this call. Set during resolveTypes().
   */
  AbstractType _type;


  /**
   * For static type analysis: This gives the resolved formal argument types for
   * the arguments of this call.  During type checking, it has to be checked
   * that the actual arguments can be assigned to these types.
   *
   * The number of resolved formal arguments might be different to the number of
   * formal arguments in case the last formal argument is of an open generic
   * type.
   */
  AbstractType[] _resolvedFormalArgumentTypes = null;


  /**
   * Will be set to true for a call to a direct parent feature in an inheritance
   * call.
   */
  public boolean _isInheritanceCall = false;
  public boolean isInheritanceCall() { return _isInheritanceCall; }


  /**
   * Flag that is set be resolveImmediateFunctionCall if a call `f()` is
   * converted to `f.call` for nullary functions or lazy values.  This is needed
   * to avoid a possible error for a potential partial application ambiguity.
   */
  boolean _wasImplicitImmediateCall = false;
  int _originalArgCount;


  /**
   * A call that has been moved to a new instance of Call due to syntax sugar.
   * In particular, a call "a < b" on the right hand side of a chained boolean
   * call will be moved here while this will be replaced by a call to
   * `bool.infix &&`.  Also, an implicit call like `f()` that is turned into
   * `f.call`  will see the  new call moved to this.
   */
  Call _movedTo = null;


  /*-------------------------- constructors ---------------------------*/


  /**
   * Constructor to read a local field
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param n the name of the called feature
   */
  public Call(SourcePosition pos, String n)
  {
    this(pos, null, n);
  }


  /**
   * Constructor to read a field in target t
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t the target of the call, null if none.
   *
   * @param n the name of the called feature
   */
  public Call(SourcePosition pos, Expr t, String n)
  {
    this(pos, t, n, -1, NO_PARENTHESES);
  }


  /**
   * Constructor to call feature with name 'n' on target 't' with actual
   * arguments 'la'.
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t the target of the call, null if none.
   *
   * @param n the name of the called feature
   *
   * @param la list of actual arguments
   */
  public Call(SourcePosition pos, Expr t, String n, List<Actual> la)
  {
    this(pos, t, n, -1, la);

    if (PRECONDITIONS) require
      (la != null);
  }


  /**
   * static helper for Call() constructor to create List<Expr> from List<Actual>
   * and directly pass it to this().
   */
  private static List<Expr> asExprList(List<Actual> la)
  {
    var res = new List<Expr>();
    for (var a : la)
      {
        res.add(a);
      }
    return res;
  }

  /**
   * Constructor to call feature with name 'n' on target 't' with actual
   * arguments 'la' with the ability to select from an open generic field.
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t the target of the call, null if none.
   *
   * @param n the name of the called feature
   *
   * @param select for selecting a open type parameter field, this gives the
   * index '.0', '.1', etc. -1 for none.
   *
   * @param la list of actual arguments
   */
  public Call(SourcePosition pos, Expr t, String n, int select, List<Actual> la)
  {
    this(pos, t, n, select, la, NO_GENERICS, asExprList(la), null, null);

    if (PRECONDITIONS) require
      (la != null,
       select >= -1);
  }


  /**
   * Constructor for a call whose called feature is already known, typically
   * because this call is created artificially for some syntactic sugar and not
   * by parsing source code.
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t the target of the call, null if none.
   *
   * @param calledFeature the called feature, must not be null
   *
   * @param select for selecting a open type parameter field, this gives the
   * index '.0', '.1', etc. -1 for none.
   */
  public Call(SourcePosition pos, Expr t, AbstractFeature calledFeature, int select)
  {
    this(pos, t, calledFeature.featureName().baseName(), select, NO_PARENTHESES);
    this._calledFeature = calledFeature;
  }


  /**
   * Constructor for a call to an anonymous feature declared in an expression.
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param anonymous the anonymous feature
   */
  public Call(SourcePosition pos,
              Feature anonymous)
  {
    this(pos, new This(pos), anonymous);
  }


  /**
   * A call to an anonymous feature declared using "fun a.b.c".
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param target the target of the call, null if none.
   *
   * @param anonymous the anonymous feature, which is the wrapper created around
   * the call to "c".
   */
  public Call(SourcePosition  pos,
              Expr            target,
              AbstractFeature anonymous)
  {
    this(pos, target, null, NO_PARENTHESES);
    this._calledFeature = anonymous;
  }


  /**
   * Constructor to low-level initialize all the fields directly.  This is
   * currently used to create calls to construct type features.
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param target the target of the call, null if none.
   *
   * @param generics
   *
   * @param actuals
   *
   * @param calledFeature
   *
   * @param type
   */
  Call(SourcePosition pos,
       Expr target,
       List<Actual> actualsNew,
       List<AbstractType> generics,
       List<Expr> actuals,
       AbstractFeature calledFeature,
       AbstractType type)
  {
    this(pos, target, calledFeature.featureName().baseName(), -1, actualsNew, generics, actuals, calledFeature, type);
  }


  /**
   * Constructor to low-level initialize all the fields directly.  This is
   * currently used to create immediate calls 'f.call a' from 'f a'.
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param target the target of the call, null if none.
   *
   * @param name the name of the called feature
   *
   * @param select for selecting a open type parameter field, this gives the
   * index '.0', '.1', etc. -1 for none.
   *
   * @param actualsNew
   *
   * @param generics
   *
   * @param actuals
   *
   * @param calledFeature
   *
   * @param type
   */
  private Call(SourcePosition pos,
               Expr target,
               String name,
               int select,
               List<Actual> actualsNew,
               List<AbstractType> generics,
               List<Expr> actuals,
               AbstractFeature calledFeature,
               AbstractType type)
  {
    if (PRECONDITIONS) require
      (Errors.any() || generics.stream().allMatch(g -> !g.containsError()));
    this._pos = pos;
    this._name = name;
    this._select = select;
    this._generics = generics;
    this._unresolvedGenerics = generics;
    this._actualsNew = actualsNew;
    this._actuals = actuals;
    this._target = target;
    this._calledFeature = calledFeature;
    this._type = type;
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
   * True iff this call was performed giving 0 or more actual arguments in
   * parentheses.  This allows a distinction between "a.b" and "a.b()" if b has
   * no formal arguments and is of a fun type. In this case, "a.b" calls only b,
   * while "a.b()" is syntactic sugar for "a.b.call".
   *
   * @return true if parentheses were present.
   */
  boolean hasParentheses()
  {
    return _actualsNew != NO_PARENTHESES;
  }


  /**
   * Get the type of the target.  In case the target's type is a generic type
   * parameter, return its constraint.
   *
   * @return the type of the target.
   */
  private AbstractType targetTypeOrConstraint(Resolution res)
  {
    if (PRECONDITIONS) require
      (_target != null);

    var result = _target.typeForCallTarget();
    if (result.isGenericArgument())
      {
        result = result.genericArgument().constraint(res);
      }

    if (POSTCONDITIONS) ensure
      (!result.isGenericArgument());
    return result;
  }


  /**
   * Convert a formal argument type in this call to the actual type defined by
   * the target of this call and the actual type parameters given in this call.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param frmlT the formal type. Might contain Types.t_UNDEFINED since this is
   * used during type resolution and type inference
   *
   * @return the actual type applying actual type parameters known from the
   * target of this call and actual type parameters given in this call. Result
   * is interned.
   */
  private AbstractType actualArgType(Resolution res, AbstractType frmlT)
  {
    if (PRECONDITIONS) require
      (!frmlT.isOpenGeneric());

    var result = adjustThisTypeForTarget(frmlT);
    result = targetTypeOrConstraint(res)
      .actualType(result)
      .applyTypePars(_calledFeature, _generics);

    if (POSTCONDITIONS) ensure
      (result != null && result != Types.resolved.t_Const_String);

    return result;
  }



  /**
   * Is the target of this call a type parameter?
   *
   * @return true for a call to `T.xyz`, `U.xyz` or `V.xyz` in a feature
   * `f(T,U,V type)`, false otherwise.
   */
  private boolean targetIsTypeParameter()
  {
    return _target instanceof Call tc && tc != ERROR && tc._calledFeature.isTypeParameter();
  }


  /**
   * Get the type of the target as seen by this call
   *
   * When calling `X.f` and `X` is a type parameter and `f` is a constructor,
   * then `X`'s type is the type `X`, while for a function `f` the type is `X`'s
   * constraint.
   *
   * @return the type of the target.
   */
  private AbstractType targetType(Resolution res)
  {
    return
      // NYI: CLEANUP: For a type parameter, the feature result type is abused
      // and holds the type parameter constraint.  As a consequence, we have to
      // fix this here and set the type of the target explicitly here.
      //
      // Would be better if AbstractFeature.resultType() would do this for us:
      _target instanceof Call tc &&
      targetIsTypeParameter()          ? tc.calledFeature().genericType() :
      calledFeature().isConstructor()  ? _target.typeForCallTarget()
                                       : targetTypeOrConstraint(res);
  }


  /**
   * Get the feature of the target of this call.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param thiz the surrounding feature. For a call c in an inherits clause
   * ("f : c is"), thiz is the outer feature of f.  For an expression in the
   * contracts or implementation of a feature f, thiz is f itself.
   *
   * @return the feature of the target of this call.
   */
  private AbstractFeature targetFeature(Resolution res, AbstractFeature thiz)
  {
    AbstractFeature result;

    // are we searching for features called via thiz' inheritance calls?
    if (res.state(thiz) == State.RESOLVING_INHERITANCE)
      {
        if (_target instanceof Call tc)
          {
            _target.loadCalledFeature(res, thiz);
            tc.reportPendingError();
            result = tc.calledFeature();
          }
        else
          {
            result = thiz.outer();   // For an inheritance call, we do not permit call to thiz' features,
                                     // but only to the outer clazz' features:
          }
      }
    else if (_target != null)
      {
        _target.loadCalledFeature(res, thiz);
        result = targetTypeOrConstraint(res).featureOfType();
      }
    else
      { // search for feature in thiz
        result = thiz;
      }

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
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
   */
  private void findChainedBooleans(Resolution res, AbstractFeature thiz)
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
            var movedTo = new Call(pos(), t2, _name, _actualsNew)
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
  boolean isChainedBoolRHS() { return false; }


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
        _target instanceof Call tc &&
        tc.isInfixOperator())
      {
        result = (tc._actuals.get(0) instanceof Call acc && acc.isChainedBoolRHS())
          ? acc
          : tc;
      }
    return result;
  }


  /*-------------------------------------------------------------------*/


  /**
   * Load all features that are called by this expression.  This is called
   * during state RESOLVING_INHERITANCE for calls in the inherits clauses and
   * during state RESOLVING_TYPES for all other calls.
   *
   * @param res the resolution instance.
   * instance.
   *
   * @param thiz the surrounding feature. For a call c in an inherits clause ("f
   * : c { }"), thiz is the outer feature of f.  For a expression in the
   * contracts or implementation of a feature f, thiz is f itself.
   *
   * NYI: Check if it might make more sense for thiz to be the declared feature
   * instead of the outer feature when processing an inherits clause.
   */
  void loadCalledFeature(Resolution res, AbstractFeature thiz)
  {
    var ignore = loadCalledFeatureUnlessTargetVoid(res, thiz);
  }


  /**
   * Load all features that are called by this expression.  This is called
   * during state RESOLVING_INHERITANCE for calls in the inherits clauses and
   * during state RESOLVING_TYPES for all other calls.
   *
   * @param res the resolution instance.
   * instance.
   *
   * @param thiz the surrounding feature. For a call c in an inherits clause ("f
   * : c { }"), thiz is the outer feature of f.  For a expression in the
   * contracts or implementation of a feature f, thiz is f itself.
   *
   * NYI: Check if it might make more sense for thiz to be the declared feature
   * instead of the outer feature when processing an inherits clause.
   *
   * @return true if everything is fine, false in case the target results in
   * void and we hence can replace the call by _target.
   */
  private boolean loadCalledFeatureUnlessTargetVoid(Resolution res, AbstractFeature thiz)
  {
    var targetVoid = false;

    if (PRECONDITIONS) require
      (thiz.isTypeParameter()   // NYI: type parameters apparently inherit ANY and are not resolved yet. Type parameters should not inherit anything and this special handling should go.
       ||
       (res.state(thiz) == State.RESOLVING_INHERITANCE
       ? res.state(thiz.outer()).atLeast(State.RESOLVING_DECLARATIONS)
       : res.state(thiz)        .atLeast(State.RESOLVING_DECLARATIONS)));

    var actualsResolved = true;
    if (_calledFeature == null)
      {
        if (CHECKS) check
          (Errors.any() || _name != Errors.ERROR_STRING);

        if (_name == Errors.ERROR_STRING)    // If call parsing failed, don't even try
          {
            setToErrorState();
          }
      }

    AbstractFeature targetFeature = null;
    if (_calledFeature == null)
      {
        actualsResolved = false;
        targetFeature = targetFeature(res, thiz);
        if (CHECKS) check
          (Errors.any() || targetFeature != null && targetFeature != Types.f_ERROR);
        targetVoid = Types.resolved != null && targetFeature == Types.resolved.f_void && targetFeature != thiz;
        if (targetVoid || targetFeature == Types.f_ERROR)
          {
            _calledFeature = Types.f_ERROR;
          }
      }
    if (_calledFeature == null)
      {
        res.resolveDeclarations(targetFeature);
        var fos = res._module.lookup(targetFeature, _name, this, _target == null, false);
        for (var fo : fos)
          {
            if (fo._feature instanceof Feature ff && ff.state().atLeast(State.RESOLVED_DECLARATIONS))
              {
                ff.resolveArgumentTypes(res);
              }
          }
        var calledName = FeatureName.get(_name, _actuals.size());
        var fo = FeatureAndOuter.filter(fos, pos(), FeatureAndOuter.Operation.CALL, calledName, ff -> mayMatchArgList(ff, false) || ff.hasOpenGenericsArgList(res));
        if (fo == null)
          { // handle implicit calls `f()` that expand to `f.call()`:
            fo = FeatureAndOuter.filter(fos, pos(), FeatureAndOuter.Operation.CALL, calledName, ff -> isSpecialWrtArgs(ff));
          }
        else if (// fo != null &&
                 fo._feature != Types.f_ERROR &&
                 _generics.isEmpty() &&
                 _actuals.size() != fo._feature.valueArguments().size() &&
                 !fo._feature.hasOpenGenericsArgList(res))
          {
            splitOffTypeArgs(res, fo._feature, thiz);
          }
        if (fo != null)
          {
            _calledFeature = fo._feature;
            if (_target == null)
              {
                _target = fo.target(pos(), res, thiz);
                _targetFrom = fo;
              }
          }
        else if (!fos.isEmpty() && _actuals.size() == 0 && fos.get(0)._feature.isChoice())
          { // give a more specific error when trying to call a choice feature
            AstErrors.cannotCallChoice(pos(), fos.get(0)._feature);
            setToErrorState();
          }

        if (_calledFeature == null &&                 // nothing found, so flag error
            (Types.resolved == null ||                // may happen when building bad base.fum
             targetFeature != Types.resolved.f_void)) // but allow to call anything on void
          {
            var tf = targetFeature;
            _pendingError = ()->
              {
                AstErrors.calledFeatureNotFound(this,
                                                calledName,
                                                tf,
                                                _target,
                                                FeatureAndOuter.findExactOrCandidate(fos,
                                                                                     (FeatureName fn) -> false,
                                                                                     (AbstractFeature f) -> f.featureName().equalsBaseName(calledName)),
                                                hiddenCandidates(res, thiz, tf, calledName));
              };
          }

      }
    if (_calledFeature == null)
      { // nothing found, try if we can build a chained bool: `a < b < c` => `(a < b) && (a < c)`
        resolveTypesOfActuals(res,thiz);
        actualsResolved = true;
        findChainedBooleans(res, thiz);
      }
    // !isInheritanceCall: see issue #2153
    if (_calledFeature == null && !isInheritanceCall())
      { // nothing found, try if we can build operator call: `a + b` => `x.y.z.this.infix + a b`
        findOperatorOnOuter(res, thiz);
      }
    if (_calledFeature == Types.f_ERROR)
      {
        _actuals = new List<>();
      }
    if (_calledFeature != null && !actualsResolved)
      {
        resolveTypesOfActuals(res,thiz);
      }

    if (POSTCONDITIONS) ensure
      (Errors.any() || !calledFeatureKnown() || calledFeature() != Types.f_ERROR || targetVoid,
       Errors.any() || _target        != Expr.ERROR_VALUE,
       Errors.any() || _calledFeature != null || _pendingError != null,
       Errors.any() || _target        != null || _pendingError != null);

    return !targetVoid;
  }


  /**
   * Check if there is a pending error from an unsuccessful attempt to find the
   * called feature.  If so, report the corresponding error and set this call
   * into an error state (with _calledFeature, _target, _actuals and _type set
   * to suitable error values).
   */
  void reportPendingError()
  {
    if (_pendingError != null)
      {
        _pendingError.run();
        _pendingError = null;
        setToErrorState();
      }
  }


  /**
   * After an error occurred for this call, set the called feature, target and
   * type all to corresponding error values to avoid further error
   * reporting. Also erase all actual arguments.
   */
  void setToErrorState()
  {
    if (PRECONDITIONS) require
      (Errors.any());

    if (!Types._options.isLanguageServer())
      {
        _calledFeature = Types.f_ERROR;
        _target = Expr.ERROR_VALUE;
        _actuals = new List<>();
        _actualsNew = new List<>();
        _generics = new List<>();
        _type = Types.t_ERROR;
        if (_movedTo != null)
          {
            _movedTo.setToErrorState();
          }
      }
  }


  /**
   * Perform partial application for a Call. In particular, this can make the
   * following changes:
   *
   *   f x y      ==>  a,b,c -> f x y a b c
   *   ++ x       ==>  a -> a ++ x
   *   x ++       ==>  a -> x ++ a
   *
   * @see Expr.propagateExpectedTypeForPartial for details.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   *
   * @param expectedType the expected type.
   */
  @Override
  Expr propagateExpectedTypeForPartial(Resolution res, AbstractFeature outer, AbstractType expectedType)
  {
    if (PRECONDITIONS) require
      (expectedType.isFunctionType());

    // NYI: CLEANUP: The logic in this method seems overly complex, there might be potential to simplify!
    Expr l = this;
    if (partiallyApplicableAlternative(res, outer, expectedType) != null)
      {
        if (_calledFeature != null)
          {
            res.resolveTypes(_calledFeature);
            var rt = _calledFeature.resultTypeIfPresent(res);
            if (rt != null && (!rt.isAnyFunctionType() || rt.arity() != expectedType.arity()))
              {
                l = applyPartially(res, outer, expectedType);
              }
          }
        else
          {
            if (_pendingError == null)
              {
                l = resolveTypes(res, outer);  // this ensures _calledFeature is set such that possible ambiguity is reported
              }
            if (l == this)
              {
                l = applyPartially(res, outer, expectedType);
              }
          }
      }
    else if (_pendingError != null                   || /* nothing found */
             newNameForPartial(expectedType) != null    /* search for a different name */)
      {
        l = applyPartially(res, outer, expectedType);
      }
    return l;
  }


  /**
   * Try to find an alternative called feature by using partial application. If
   * a suitable alternative is found, return it.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   *
   * @param expectedType the expected type.
   */
  FeatureAndOuter partiallyApplicableAlternative(Resolution res, AbstractFeature outer, AbstractType expectedType)
  {
    if (PRECONDITIONS) require
      (expectedType.isFunctionType());

    FeatureAndOuter result = null;
    if (_name != null)  // NYI: CLEANUP: _name is null for call to anonymous inner feature. Should better be the name of the called feature
      {
        var n = expectedType.arity() + (_wasImplicitImmediateCall ? _originalArgCount : _actuals.size());
        var newName = newNameForPartial(expectedType);
        var name = newName != null ? newName : _name;

        // if loadCalledFeatureUnlessTargetVoid has found a suitable called
        // feature in an outer feature, it will have replaced a null _target, so
        // we check _originalTarget here to not check all outer features:
        var traverseOuter = _originalTarget == null;
        var targetFeature = traverseOuter ? outer : targetFeature(res, outer);
        var fos = res._module.lookup(targetFeature, name, this, traverseOuter, false);
        var calledName = FeatureName.get(name, n);
        result = FeatureAndOuter.filter(fos, pos(), FeatureAndOuter.Operation.CALL, calledName, ff -> ff.valueArguments().size() == n);
      }
    return result;
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
   * @param outer the feature that contains this expression
   *
   * @param t the expected type.
   */
  void checkPartialAmbiguity(Resolution res, AbstractFeature outer, AbstractType expectedType)
  {
    if (_calledFeature != null && _calledFeature != Types.f_ERROR && this instanceof ParsedCall)
      {
        var fo = partiallyApplicableAlternative(res, outer, expectedType);
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
   * Is this an operator call like `a+b` or `-x` in contrast to a named call `f`
   * or `t.g`?
   */
  boolean isOperatorCall()
  {
    return false;
  }


  /**
   * Check if partial application would change the name of the called feature
   * for this call.
   *
   * @param expectedType the expected function type
   *
   * @return the new name or null in case the name stays unchanged.
   */
  String newNameForPartial(AbstractType expectedType)
  {
    String result = null;
    if (expectedType.arity() == 1 && isOperatorCall())
      {
        var name = _name;
        if (name.startsWith(FuzionConstants.PREFIX_OPERATOR_PREFIX))
          { // -v ==> x->x-v
            result = FuzionConstants.INFIX_OPERATOR_PREFIX + name.substring(FuzionConstants.PREFIX_OPERATOR_PREFIX.length());
          }
        else if (name.startsWith(FuzionConstants.POSTFIX_OPERATOR_PREFIX))
          { // -v ==> x->x-v
            result = FuzionConstants.INFIX_OPERATOR_PREFIX + name.substring(FuzionConstants.POSTFIX_OPERATOR_PREFIX.length());
          }
      }
    return result;
  }


  /**
   * After propagateExpectedType: if type inference up until now has figured
   * out that a Lazy feature is expected, but the current expression is not
   * a Lazy feature, then wrap this expression in a Lazy feature.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param  outer the feature that contains this expression
   *
   * @param t the type this expression is assigned to.
   */
  public Expr applyPartially(Resolution res, AbstractFeature outer, AbstractType t)
  {
    checkPartialAmbiguity(res, outer, t);
    Expr result;
    var n = t.arity();
    if (mustNotContainDeclarations("a partially applied function call", outer))
      {
        _pendingError = null;
        List<ParsedName> pns = new List<>();
        for (var i = 0; i < n; i++)
          {
            pns.add(Partial.argName(pos()));
          }
        _actuals    = _actuals   .clone();
        _actualsNew = _actualsNew.clone();
        if (_name.startsWith(FuzionConstants.PREFIX_OPERATOR_PREFIX))
          { // -v ==> x->x-v   -- swap target and first actual:
            if (CHECKS) check
              (Errors.any() || n == 1,
               Errors.any() || _actuals.size() == 0);
            _actuals   .add(           _target );
            _actualsNew.add(new Actual(_target));
            _target = new ParsedCall(null, pns.get(0));
          }
        else
          { // fill up actuals with arguments of the lambda:
            for (var i = 0; i < n; i++)
              {
                var c = new ParsedCall(null, pns.get(i));
                _actuals   .add(           c );
                _actualsNew.add(new Actual(c));
              }
          }
        var nn = newNameForPartial(t);
        if (nn != null)
          {
            _name = nn;
            _calledFeature = null;
            _pendingError = null;
          }
        var fn = new Function(pos(),
                              pns,
                              this)
          {
            public AbstractType propagateTypeAndInferResult(Resolution res, AbstractFeature outer, AbstractType t, boolean inferResultType)
            {
              var rs = super.propagateTypeAndInferResult(res, outer, t, inferResultType);
              updateTarget(res);
              return rs;
            }
          };
        result = fn;
        fn.resolveTypes(res, outer);
      }
    else
      {
        result = ERROR_VALUE;
      }
    return result;
  }


  /**
   * @return list of features that would match called name and args but are not visible.
   */
  private List<FeatureAndOuter> hiddenCandidates(Resolution res, AbstractFeature thiz, AbstractFeature targetFeature, FeatureName calledName)
  {
    var fos = res._module.lookup(targetFeature, _name, this, _target == null, true);
    for (var fo : fos)
      {
        if (fo._feature instanceof Feature ff && ff.state().atLeast(State.RESOLVED_DECLARATIONS))
          {
            ff.resolveArgumentTypes(res);
          }
      }
    return FeatureAndOuter
      .findExactOrCandidate(
        fos,
        (FeatureName fn) -> fn.equalsExceptId(calledName),
        ff -> mayMatchArgList(ff, false) || ff.hasOpenGenericsArgList(res)
    );
  }


  private void resolveTypesOfActuals(Resolution res, AbstractFeature outer)
  {
    // NYI: check why _actuals.listIterator cannot be done inside
    // whenResolvedTypes. If it could, the 'if calledFeature != null / Error
    // would not be needed.
    ListIterator<Expr> i = _actuals.listIterator(); // _actuals can change during resolveTypes, so create iterator early
    outer.whenResolvedTypes
      (() ->
       {
         while (i.hasNext())
           {
             var a = i.next();
             if (a != null) // splitOffTypeArgs might have set this to null
               {
                 if (_calledFeature != null && _calledFeature != Types.f_ERROR)
                   {
                     var a1 = res.resolveType(a, outer);
                     if (CHECKS) check
                       (a1 != null);
                     i.set(a1);
                   }
               }
           }
       });
  }


  /**
   * For an infix, prefix or postfix operator call of the form
   *
   *   a ⨁ b     -- or --
   *   ⨁ a       -- or --
   *   a ⨁
   *
   * that was not found within the target 'a', try to find this operator in 'thiz'
   * or any outer feature.  If found in X.Y.Z.this, then convert this call into
   *
   *   X.Y.Z.this.infix  ⨁ a b     -- or --
   *   X.Y.Z.this.prefix ⨁ a       -- or --
   *   X.Y.Z.this.postix ⨁ a       ,
   *
   * respectively.  This permits the introduction of binary or unary operators
   * within any feature, e.g., within unit type features that can be inherited
   * from or even in the universe.
   *
   * If successful, field _calledFeature will be set to the called feature and
   * fields _target and _actuals will be changed accordingly.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param thiz the surrounding feature
   */
  private void findOperatorOnOuter(Resolution res, AbstractFeature thiz)
  {
    if (_name.startsWith(FuzionConstants.INFIX_OPERATOR_PREFIX  ) ||
        _name.startsWith(FuzionConstants.PREFIX_OPERATOR_PREFIX ) ||
        _name.startsWith(FuzionConstants.POSTFIX_OPERATOR_PREFIX)    )
      {
        var calledName = FeatureName.get(_name, _actuals.size()+1);
        var fo = res._module.lookup(thiz, _name, this, true, false);
        var foa = FeatureAndOuter.filter(fo, pos(), FeatureAndOuter.Operation.CALL, calledName, ff -> mayMatchArgList(ff, true));
        if (foa != null)
          {
            _calledFeature = foa._feature;
            _pendingError = null;
            var newActuals = new List<>(_target);
            newActuals.addAll(_actuals);
            _actuals = newActuals;
            _target = foa.target(pos(), res, thiz);
          }
      }
  }


  /**
   * For a call of the form
   *
   *   array i32 10 i->i*i
   *
   * split the actuals list (i32, 10, i->i*i) into generics (i32) and actuals
   * (10, i->i*i).
   *
   * @param calledFeature the feature we are calling
   *
   * @param outer the feature surrounding this call
   */
  private void splitOffTypeArgs(Resolution res, AbstractFeature calledFeature, AbstractFeature outer)
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
            AbstractType t = _actualsNew.get(i)._type;
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


  /**
   * Check if this expression can also be parsed as a type and return that type. Otherwise,
   * report an error (AstErrors.expectedActualTypeInCall).
   *
   * @param outer the outer feature containing this expression
   *
   * @param tp the type parameter this expression is assigned to
   *
   * @return the Type corresponding to this, Type.t_ERROR in case of an error.
   */
  AbstractType asType(Resolution res, AbstractFeature outer, AbstractFeature tp)
  {
    var g = _generics;
    if (!_actuals.isEmpty())
      {
        g = new List<AbstractType>();
        g.addAll(_generics);
        for (var a : _actuals)
          {
            g.add(a.asType(res, outer, tp));
          }
      }
    AbstractType result = new ParsedType(pos(), _name, g,
                                         _target == null             ||
                                         _target instanceof Universe ||
                                         _target instanceof Current     ? null
                                                                        : _target.asType(res, outer, tp));
    return result.resolve(res, outer);
  }


  /**
   * Check if this call would need special handling of the argument count
   * in case the _calledFeature would be ff. This is the case for open generics,
   * "fun a.b.f" calls and implicit calls using f() for f returning Function value.
   *
   * @param ff the called feature candidate.
   *
   * @return true iff ff may be the called feature due to the special cases
   * listed above.
   */
  private boolean isSpecialWrtArgs(AbstractFeature ff)
  {
    return ff.arguments().size()==0; /* maybe an implicit call to a Function / Routine, see resolveImmediateFunctionCall() */
  }


  /**
   * Check if the actual arguments to this call may match the formal arguments
   * for calling ff.
   *
   * @param ff the candidate that might be called
   *
   * @param addOne true iff one actual argument will be added (used in
   * findOperatorOnOuter which will add the target to the actual arguments).
   *
   * @return true if ff is a valid candidate to be called.
   */
  private boolean mayMatchArgList(AbstractFeature ff, boolean addOne)
  {
    var asz = _actuals.size() + (addOne ? 1 : 0);
    var fvsz = ff.valueArguments().size();
    var ftsz = ff.typeArguments().size();

    var result = fvsz == asz ||
      _generics.isEmpty() && (fvsz + ftsz == asz) ||
      _generics.isEmpty() && asz >= fvsz + ftsz -1 &&
      ff.typeArguments().stream().anyMatch(ta -> ta.kind() == AbstractFeature.Kind.OpenTypeParameter);
    return result;
  }


  /**
   * After resolveTypes or if calledFeatureKnown(), this can be called to obtain
   * the feature that is called.
   */
  public AbstractFeature calledFeature()
  {
    if (PRECONDITIONS) require
      (Errors.any() || calledFeatureKnown() || _pendingError != null);

    reportPendingError();
    AbstractFeature result = _calledFeature != null ? _calledFeature : Types.f_ERROR;

    if (POSTCONDITIONS) ensure
      (result != null);
    return result;
  }


  /**
   * Is the called feature known? This is the case for calls to anonymous inner
   * features even before resolveTypes is executed. After resolveTypes, this is
   * the case unless there was an error finding the called feature.
   */
  boolean calledFeatureKnown()
  {
    return _calledFeature != null;
  }


  /**
   * Is this Expr a call to an outer ref?
   */
  public boolean isCallToOuterRef()
  {
    return calledFeature().isOuterRef();
  }


  /**
   * toString
   *
   * @return
   */
  // NYI move this to AbstractCall
  public String toString()
  {
    return (_target == null ||
            (_target instanceof Universe) ||
            (_target instanceof This t && t.toString().equals(FuzionConstants.UNIVERSE_NAME + ".this"))
            ? ""
            : _target.toString() + ".")
      + (_name          != null ? _name :
         _calledFeature != null ? _calledFeature.featureName().baseName()
                                : "--ANONYMOUS--" )
      + _generics.toString(" ", " ", "", t -> t.toStringWrapped())
      + _actuals .toString(" ", " ", "", e -> e.toStringWrapped())
      + (_select < 0        ? "" : " ." + _select);
  }


  /**
   * typeForInferencing returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  AbstractType typeForInferencing()
  {
    reportPendingError();
    return (_calledFeature instanceof Feature f) && f.isAnonymousInnerFeature() && f.inherits().getFirst().type().isRef()
      ? f.inherits().getFirst().type()
      : _type;
  }


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
    _generics = _generics.map(g -> g.visit(v, outer));
    if (v.doVisitActuals())
      {
        visitActuals(v, outer);
      }
    if (_target != null)
      {
        _target = _target.visit(v, outer);
      }
    v.action((AbstractCall) this);
    var result = v.action(this, outer);
    if (v.visitActualsLate())
      {
        visitActuals(v, outer);
      }
    return result;
  }


  /**
   * Helper for visit to visit all the actual value arguments of this call.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   */
  private void visitActuals(FeatureVisitor v, AbstractFeature outer)
  {
    ListIterator<Expr> i = _actuals.listIterator();
    while (i.hasNext())
      {
        var a = i.next();
        if (a != null)
          {
            i.set(a.visit(v, outer));
          }
      }
  }


  /**
   * Create a new call and push the current call to the target of that call.
   * This is used for implicit calls to Function and Lazy values where `f()` is
   * converted to `f.call()`, and for implicit fields in a select call such as,
   * e.g., a tuple access `t.3` that is converted to `t.values.3`.
   *
   * The actual arguments and _select of this call are moved over to the new
   * call, this calls arguments are replaced by NO_PARENTHESES/NO_EXPRS and this
   * calls _select is set to -1.
   *
   * @param res Resolution instance
   *
   * @param outer the feature surrounding this call
   *
   * @param name the name of the feature to be called.
   *
   * @return the newly created call
   */
  Call pushCall(Resolution res, AbstractFeature outer, String name)
  {
    var wasLazy = _type != null && _type.isLazyType();
    var result = new Call(pos(),
                      this /* this becomes target of "call" */,
                      name,
                      _select,
                      _actualsNew,
                      NO_GENERICS,
                      _actuals,
                      null,
                      null)
      {
        @Override
        Expr originalLazyValue()
        {
          return wasLazy ? Call.this : super.originalLazyValue();
        }
        @Override
        public Expr propagateExpectedType(Resolution res, AbstractFeature outer, AbstractType expectedType)
        {
          if (expectedType.isFunctionType())
            { // produce an error if the original call is ambiguous with partial application
              Call.this.checkPartialAmbiguity(res, outer, expectedType);
            }
          return super.propagateExpectedType(res, outer, expectedType);
        }
      };
    _movedTo = result;
    _wasImplicitImmediateCall = true;
    _originalArgCount = _actuals.size();
    _actualsNew = NO_PARENTHESES;
    _actuals = Expr.NO_EXPRS;
    _select = -1;
    return result;
  }


  /**
   * Helper function called during resolveTypes to implicitly call a feature
   * with an open type parameter result in case _select >= 0 and t is not a type
   * parameter.
   *
   * This converts, e.g., `t.3` for a tuple `t` to `t.values.3`.
   */
  Call resolveImplicitSelect(Resolution res, AbstractFeature outer, AbstractType t)
  {
    var result = this;
    if (_select >= 0 && !t.isGenericArgument())
      {
        var f = res._module.lookupOpenTypeParameterResult(t.featureOfType(), this);
        if (f != null)
          {
            // replace Function call `c.123` by `c.f.123`:
            result = pushCall(res, outer, f.featureName().baseName());
            setActualResultType(res, t); // setActualResultType will be done again by resolveTypes, but we need it now.
            result = result.resolveTypes(res, outer);
          }
      }
    return result;
  }


  /**
   * Helper function called during resolveTypes to resolve syntactic sugar that
   * allows directly calling a function returned by a call.
   *
   * If this is a normal call (e.g. `f.g`) whose result is a function type,
   * (`(i32,i32) -> f64`), and if `g` does not take any arguments, syntactic
   * sugar allows an implicit call to `Function.call`, i.e., `f.g 3 5` is
   * a short form of `f.g.call 3 5`.
   *
   * NYI: we could also permit `(f.g x y) 3 5` as a short form for `(f.g x
   * y).call 3 5` in case `g` takes arguments.  But this might be too confusing
   * and it would require a change in the grammar.
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this expression.
   *
   * @param result this in case this was not an immediate call, otherwise the
   * resulting call to Function/Routine.call.
   */
  private Call resolveImmediateFunctionCall(Resolution res, AbstractFeature outer)
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
      _type.isFunctionType() &&
      _calledFeature != Types.resolved.f_function && // exclude inherits call in function type
      _calledFeature.arguments().size() == 0      &&
      hasParentheses()
    ||
      _type.isLazyType()                          &&   // we are `Lazy T`
      _calledFeature != Types.resolved.f_Lazy     &&   // but not an explicit call to `Lazy` (e.g., in inherits clause)
      _calledFeature.arguments().size() == 0      &&   // no arguments (NYI: maybe allow args for `Lazy (Function R V)`, then `l a` could become `c.call.call a`
      _actualsNew.isEmpty()                       &&   // dto.
      originalLazyValue() == this;                     // prevent repeated `l.call.call` when resolving the newly created Call to `call`.
  }


  /**
   * Helper routine for resolveFormalArgumentTypes to determine the actual type
   * of a formal argument after inheritance and determination of actual type
   * from the target type and generics provided to the call.
   *
   * The result will be stored in _resolvedFormalArgumentTypes[argnum..].
   *
   * @param res Resolution instance
   *
   * @param argnum the number of this formal argument
   *
   * @param frml the formal argument
   */
  private void resolveFormalArg(Resolution res, int argnum, AbstractFeature frml)
  {
    int cnt = 1;
    var frmlT = frml.resultTypeIfPresent(res);

    var declF = _calledFeature.outer();
    var heir = _target.typeForCallTarget();
    if (!heir.isGenericArgument() && declF != heir.featureOfType())
      {
        var a = _calledFeature.handDown(res, new AbstractType[] { frmlT }, heir.featureOfType());
        if (a.length != 1)
          {
            // Check that the number or args can only change for the
            // last argument (when it is of an open generic type).  if
            // it would change for other arguments, changing the
            // _resolvedFormalArgumentTypes array would invalidate
            // argnum for following arguments.
            if (CHECKS) check
              (Errors.any() || argnum == _resolvedFormalArgumentTypes.length - 1);
            if (argnum != _resolvedFormalArgumentTypes.length -1)
              {
                a = new AbstractType[] { Types.t_ERROR }; /* do not change _resolvedFormalArgumentTypes array length */
              }
          }
        addToResolvedFormalArgumentTypes(res, argnum, a, frml);
        cnt = a.length;
      }
    else
      {
        _resolvedFormalArgumentTypes[argnum] = frmlT;
      }

    // next, replace generics given in the target type and in this call
    for (int i = 0; i < cnt; i++)
      {
        if (CHECKS) check
          (Errors.any() || argnum + i <= _resolvedFormalArgumentTypes.length);

        if (argnum + i < _resolvedFormalArgumentTypes.length)
          {
            frmlT = _resolvedFormalArgumentTypes[argnum + i];

            if (frmlT.isOpenGeneric())
              { // formal arg is open generic, i.e., this expands to 0 or more actual args depending on actual generics for target:
                Generic g = frmlT.genericArgument();
                var frmlTs = g.replaceOpen(g.feature() == _calledFeature
                                           ? _generics
                                           : _target.type().generics());
                addToResolvedFormalArgumentTypes(res, argnum + i, frmlTs.toArray(new AbstractType[frmlTs.size()]), frml);
                i   = i   + frmlTs.size() - 1;
                cnt = cnt + frmlTs.size() - 1;
              }
            else
              {
                _resolvedFormalArgumentTypes[argnum + i] = actualArgType(res, frmlT);
              }
          }
      }
  }


  /**
   * Helper routine for resolveFormalArg and replaceGenericsInFormalArg to
   * extend the _resolvedFormalArgumentTypes array.
   *
   * In case frml.resultType().isOpenGeneric(), this will call frml.select() for
   * all the actual types the open generic is replaced by to make sure the
   * corresponding features exist.
   *
   * @param res Resolution instance
   *
   * @param argnum index in _resolvedFormalArgumentTypes at which we add new
   * elements
   *
   * @param a the new elements to add to _resolvedFormalArgumentTypes
   *
   * @param frml the argument whose type we are resolving.
   */
  private void addToResolvedFormalArgumentTypes(Resolution res, int argnum, AbstractType[] a, AbstractFeature frml)
  {
    var na = new AbstractType[_resolvedFormalArgumentTypes.length - 1 + a.length];
    var j = 0;
    for (var i = 0; i < _resolvedFormalArgumentTypes.length; i++)
      {
        if (i == argnum)
          {
            for (var at : a)
              {
                if (CHECKS) check
                  (at != null);
                na[j] = at;
                j++;
              }
          }
        else
          {
            na[j] = _resolvedFormalArgumentTypes[i];
            j++;
          }
      }
    _resolvedFormalArgumentTypes = na;
  }


  /**
   * Helper routine for resolveTypes to resolve the formal argument types of the
   * arguments in this call. Results will be stored in
   * _resolvedFormalArgumentTypes array.
   */
  private void resolveFormalArgumentTypes(Resolution res)
  {
    var fargs = _calledFeature.valueArguments();
    _resolvedFormalArgumentTypes = fargs.size() == 0 ? UnresolvedType.NO_TYPES
                                                     : new AbstractType[fargs.size()];
    Arrays.fill(_resolvedFormalArgumentTypes, Types.t_UNDEFINED);
    int count = 0;
    for (var frml : fargs)
      {
        int argnum = count;  // effectively final copy of count
        frml.whenResolvedTypes
          (() -> resolveFormalArg(res, argnum, frml));
        count++;
      }
    if (POSTCONDITIONS) ensure
      (_resolvedFormalArgumentTypes != null);
  }


  /**
   * list filled by whenInferredTypeParameters.
   */
  private List<Runnable> _whenInferredTypeParameters = NO_RUNNABLE;


  /**
   * pre-allocated empty list for _whenInferredTypeParameters.
   */
  private static List<Runnable> NO_RUNNABLE = new List<>();


  /**
   * While type parameters are still unknown because they need to be inferred
   * from the actual arguments, this can be used to register actions to be
   * performed as soon as the type parameters are known.
   */
  void whenInferredTypeParameters(Runnable r)
  {
    if (PRECONDITIONS) require
      (needsToInferTypeParametersFromArgs());

    if (_whenInferredTypeParameters == NO_RUNNABLE)
      {
        _whenInferredTypeParameters = new List<>();
      }
    _whenInferredTypeParameters.add(r);
  }


  /**
   * Helper function for resolveTypes to determine the static result type of
   * this call.
   *
   * In particular, this replaces formal generic types by actual generics
   * provided to this call and it replaces select calls to fields of open
   * generic type by calls to the actual fields.
   *
   * @param res the resolution instance.
   *
   * @param frmlT the result type of the called feature, might be open generic.
   */
  private void setActualResultType(Resolution res, AbstractType frmlT)
  {
    var tt =
      targetIsTypeParameter() && frmlT.isThisTypeInTypeFeature()
      ? // a call B.f for a type parameter target B. resultType() is the
        // constraint of B, so we create the corresponding type feature's
        // selfType:
        // NYI: CLEANUP: remove this special handling!
        _target.typeForCallTarget().featureOfType().selfType()
      : targetType(res);

    var t1 = resolveSelect(frmlT, tt);
    var t2 = t1.applyTypePars(tt);
    var t3 = tt.isGenericArgument() ? t2 : t2.resolve(res, tt.featureOfType());
    var t4 = adjustThisTypeForTarget(t3);
    var t5 = resolveForCalledFeature(res, t4, tt);
    // call may be resolved repeatedly. In case of recursive use of FieldActual
    // (see #2182), we may see `void` as the result type of calls to argument
    // fields during recursion.  We use only the non-recursive (i.e., non-void)
    // ones:
    if (_type == null ||
        !t5.isVoid() ||
        !(_calledFeature instanceof Feature cf) ||
        cf.impl()._kind != Impl.Kind.FieldActual)
      {
        _type = t5;
      }
  }


  /**
   * Helper for resolveType to process _select, i.e., check that _select is < 0
   * and t is not open generic, or else _select chooses the actual open generic
   * type.
   *
   * @param t the result type of the called feature, might be open generic.
   *
   * @param tt target type or constraint.
   *
   * @return the actual, non open generic result type to Types.t_ERROR in case
   * of an error.
   */
  private AbstractType resolveSelect(AbstractType t, AbstractType tt)
  {
    if (_select < 0 && t.isOpenGeneric())
      {
        AstErrors.cannotAccessValueOfOpenGeneric(pos(), _calledFeature, t);
        t = Types.t_ERROR;
      }
    else if (_select >= 0 && !t.isOpenGeneric())
      {
        AstErrors.useOfSelectorRequiresCallWithOpenGeneric(pos(), _calledFeature, _name, _select, t);
        t = Types.t_ERROR;
      }
    else if (_select >= 0)
      {
        var types = t.genericArgument().replaceOpen(tt.generics());
        int sz = types.size();
        if (_select >= sz)
          {
            AstErrors.selectorRange(pos(), sz, _calledFeature, _name, _select, types);
            setToErrorState();
            t = Types.t_ERROR;
          }
        else
          {
            t = types.get(_select);
          }
      }
    return t;
  }


  /**
   * Replace occurrences of this.type in formal arg or result type depending on
   * the target of the call.
   *
   * @param t the formal type to be adjusted.
   *
   * @return a type derived from t where `this.type` is replaced by actual types
   * from the call's target where this is possible.
   */
  private AbstractType adjustThisTypeForTarget(AbstractType t)
  {
    /**
     * For a call `T.f` on a type parameter whose result type contains
     * `this.type`, make sure we replace the implicit type parameter to
     * `this.type`.
     *
     * example:
     *
     *   equatable is
     *
     *     type.equality(a, b equatable.this.type) bool is abstract
     *
     *   equals(T type : equatable, x, y T) => T.equality x y
     *
     * For the call `T.equality x y`, we must replace the the formal argument type
     * for `a` (and `b`) by `T`.
     */
    var target = target();
    var tt = target().type();
    if (target instanceof Call tc &&
        tc.calledFeature().isTypeParameter() &&
        !tt.isGenericArgument())
      {
        t = t.replace_type_parameter_used_for_this_type_in_type_feature
          (tt.featureOfType(),
           tc);
      }
    if (!calledFeature().isOuterRef())
      {
        var inner = ResolvedNormalType.newType(calledFeature().selfType(),
                                          _target.typeForCallTarget());
        var t0 = t;
        t = t.replace_this_type_by_actual_outer(inner,
                                                (from,to) -> AstErrors.illegalOuterRefTypeInCall(this, t0, from, to)
                                                );
      }
    return t;
  }


  /**
   * Helper function for resolveType to adjust a result type depending on the
   * kind of feature that is called.
   *
   * In particular, this contains special handling for calling type parameters,
   * for Types.get, for outer refs and for constructors.
   *
   * @param res the resolution instance.
   *
   * @param t the result type of the called feature, adjusts for select, this type, etc.
   *
   * @param tt target type or constraint.
   */
  private AbstractType resolveForCalledFeature(Resolution res, AbstractType t, AbstractType tt)
  {
    if (_calledFeature.isTypeParameter())
      {
        if (!t.isGenericArgument())  // See AstErrors.constraintMustNotBeGenericArgument
          {
            // a type parameter's result type is the constraint's type as a type
            // feature with actual type parameters as given to the constraint.
            var tf = t.featureOfType().typeFeature(res);
            var tg = new List<AbstractType>(t); // the constraint type itself
            tg.addAll(t.generics());            // followed by the generics
            t = tf.selfType().applyTypePars(tf, tg);
          }
      }
    else if (_calledFeature == Types.resolved.f_Types_get)
      {
        t = _generics.get(0);
        // we are using `.this.type` inside a type feature, see #2295
        if (t.isThisTypeInTypeFeature())
          {
            t = t.genericArgument().feature().thisType();
          }
        else if (!t.isGenericArgument())
          {
            t = t.typeType(res);
          }
        t = t.resolve(res, tt.featureOfType());
      }
    else if (_calledFeature.isOuterRef())
      {
        var o = t.featureOfType().outer();
        t = o == null || o.isUniverse() ? t : ResolvedNormalType.newType(t, o.thisType());
        t = t.asThis();
      }
    else if (_calledFeature.isConstructor())
      {  /* specialize t for the target type here */
        t = ResolvedNormalType.newType(t, tt);
      }
    else
      {
        t = t.applyTypePars(calledFeature(), _generics);
      }
    return t;
  }


  /**
   * Helper routine for inferGenericsFromArgs: Get the next element from aargs,
   * perform type resolution (which includes possibly replacing it by a
   * different Expr) and return it.
   *
   * This is called twice for two passes: First, with formalTypeForPropagation
   * == null, to find all the types of actuals that are happy to provide their
   * type.  Second, with formalTypeForPropagation != null, to first propagate a
   * type that was possibly found during the first pass before before resolving
   * the actual's type.
   *
   * @param formalTypeForPropagation  the formal argument type
   *
   * @param aargs iterator whose next value is the actual to process
   *
   * @param res the resolution instance
   *
   * @param outer the root feature that contains this expression
   */
  private Expr resolveTypeForNextActual(AbstractType formalTypeForPropagation,
                                        ListIterator<Expr> aargs,
                                        Resolution res,
                                        AbstractFeature outer)
  {
    Expr actual = aargs.next();
    var actualWantsPropagation = actual instanceof NumLiteral;
    if (formalTypeForPropagation != null && actualWantsPropagation)
      {
        if (formalTypeForPropagation.isGenericArgument())
          {
            var g = formalTypeForPropagation.genericArgument();
            if (g.feature() == _calledFeature)
              { // we found a use of a generic type, so record it:
                var t = _generics.get(g.index());
                if (t != Types.t_UNDEFINED)
                  {
                    actual = actual.propagateExpectedType(res, outer, t);
                  }
              }
          }
      }
    if ((formalTypeForPropagation != null) || !actualWantsPropagation)
      {
        actual = res.resolveType(actual, outer);
        if (CHECKS) check
          (actual != null);
        aargs.set(actual);
      }
    else
      {
        actual = null;
      }
    return actual;
  }


  /**
   * infer the missing generic arguments to this call by inspecting the types of
   * the actual arguments.
   *
   * This is called during resolveTypes, so we have to be careful since type
   * information is not generally available yet.
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this expression.
   */
  private void inferGenericsFromArgs(Resolution res, AbstractFeature outer)
  {
    var cf = _calledFeature;
    int sz = cf.generics().list.size();
    boolean[] conflict = new boolean[sz]; // The generics that had conflicting types
    var foundAt  = new List<List<Pair<SourcePosition, AbstractType>>>(); // generics that were found will get the type and pos found stored here, null while not found
    for (var i = 0; i<sz ; i++)
      {
        foundAt.add(null);
      }

    _generics = actualTypeParameters();
    var va = cf.valueArguments();
    var checked = new boolean[va.size()];
    int last, next = 0;
    do
      {
        last = next;
        inferGenericsFromArgs(res, outer, checked, conflict, foundAt);
        next = 0;
        for (var b : foundAt)
          {
            next = next + (b != null ? 1 : 0);
          }
      }
    while (last < next);

    List<Generic> missing = new List<Generic>();
    for (Generic g : cf.generics().list)
      {
        int i = g.index();
        if (!g.isOpen() && _generics.get(i) == Types.t_UNDEFINED)
          {
            missing.add(g);
            if (CHECKS) check
              (Errors.any() || g.isOpen() || i < _generics.size());
            if (i < _generics.size())
              {
                _generics = _generics.setOrClone(i, Types.t_ERROR);
              }
          }
        else if (conflict[i])
          {
            AstErrors.incompatibleTypesDuringTypeInference(pos(), g, foundAt.get(i));
            _generics = _generics.setOrClone(i, Types.t_ERROR);
          }
      }

    // report missing inferred types only if there were no errors trying to find
    // the types of the actuals:
    if (!missing.isEmpty() &&
        (!Errors.any() ||
         !_actuals.stream().anyMatch(x -> x.typeForInferencing() == Types.t_ERROR)))
      {
        AstErrors.failedToInferActualGeneric(pos(),cf, missing);
      }
  }


  /**
   * Perform phase 1. of the calls to @see Expr.propagateExpectedTypeForPartial:
   * while the actual type parameters may not be known yet for this call, try to
   * create partial application lambdas for the actual arguments where needed.
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this expression.
   */
  void propagateForPartial(Resolution res, AbstractFeature outer)
  {
    var cf = _calledFeature;

    ListIterator<Expr> aargs = _actuals.listIterator();
    var va = cf.valueArguments();
    var vai = 0;
    for (var frml : va)
      {
        if (aargs.hasNext())
          {
            var actual = aargs.next();
            var t = frml.resultTypeIfPresent(res, NO_GENERICS);
            if (t.isFunctionType())
              {
                var a = resultExpression(actual);
                Expr l = a.propagateExpectedTypeForPartial(res, outer, t);
                if (l != a)
                  {
                    _actuals = _actuals.setOrClone(vai, l);
                  }
              }
          }
        vai++;
      }
  }


  /**
   * infer the missing generic arguments to this call by inspecting the types of
   * the actual arguments.
   *
   * This is called during resolveTypes, so we have to be careful since type
   * information is not generally available yet.
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this expression.
   *
   * @param checked boolean array for all cf.valuedArguments() that have been
   * checked already.
   *
   * @param conflict set of generics that caused conflicts
   *
   * @param foundAt the position of the expressions from which actual generics
   * were taken.
   */
  private void inferGenericsFromArgs(Resolution res, AbstractFeature outer, boolean[] checked, boolean[] conflict, List<List<Pair<SourcePosition, AbstractType>>> foundAt)
  {
    var cf = _calledFeature;
    // run two passes: first, ignore numeric literals and open generics, do these in second pass
    for (var pass = 0; pass < 2; pass++)
      {
        int count = 1; // argument count, for error messages

        ListIterator<Expr> aargs = _actuals.listIterator();
        var va = cf.valueArguments();
        var vai = 0;
        for (var frml : va)
          {
            if (CHECKS) check
                          (Errors.any() || res.state(frml).atLeast(State.RESOLVED_DECLARATIONS));

            if (!checked[vai])
              {
                var t = frml.resultTypeIfPresent(res, NO_GENERICS);
                var g = t.isGenericArgument() ? t.genericArgument() : null;
                if (g != null && g.feature() == cf && g.isOpen())
                  {
                    if (pass == 1)
                      {
                        checked[vai] = true;
                        foundAt.set(g.index(), new List<>()); // set to something not null to avoid missing argument error below
                        while (aargs.hasNext())
                          {
                            count++;
                            var actual = resolveTypeForNextActual(Types.t_UNDEFINED, aargs, res, outer);
                            var actualType = typeFromActual(actual, outer);
                            if (actualType == null)
                              {
                                actualType = Types.t_ERROR;
                                AstErrors.failedToInferOpenGenericArg(pos(), count, actual);
                              }
                            _generics.add(actualType);
                          }
                      }
                  }
                else if (aargs.hasNext())
                  {
                    count++;
                    var actual = resolveTypeForNextActual(pass == 0 ? null : t, aargs, res, outer);
                    var actualType = typeFromActual(actual, outer);
                    if (actualType != null)
                      {
                        inferGeneric(res, outer, t, actualType, actual.pos(), conflict, foundAt);
                        checked[vai] = true;
                      }
                    else if (resultExpression(actual) instanceof AbstractLambda al)
                      {
                        checked[vai] = inferGenericLambdaResult(res, outer, t, al, actual.pos(), conflict, foundAt);
                      }
                  }
              }
            else if (aargs.hasNext())
              {
                aargs.next();
              }
            vai++;
          }
      }
  }


  /**
   * For a call to a feature whose formal arguments do not have an explicit
   * type, but one that is inferred from the actual argument, make sure that
   * this call's actual arg is taken into account.
   *
   * @param outer the root feature that contains this call.
   */
  private void inferFormalArgTypesFromActualArgs(AbstractFeature outer)
  {
    var aargs = _actuals.iterator();
    for (var frml : _calledFeature.valueArguments())
      {
        if (aargs.hasNext())
          {
            var actl = aargs.next();
            if (frml instanceof Feature f)
              {
                f.impl().addInitialCall(this, outer);
              }
          }
      }
  }


  /**
   * For a given Expr, check if this is a block. If so, return the block's
   * resultExpression, otherwise return actual.
   *
   * @param actual an Expr or null
   *
   * @return in case actual instanceof Block, the resultExpression of the
   * block's resultExpression (which might be null if the block result in
   * implicit unit type), otherwise actual
   */
  private Expr resultExpression(Expr actual)
  {
    return actual instanceof Block ab ? resultExpression(ab.resultExpression())
                                      : actual;
  }


  /**
   * During type inference for type parameters, determine the type of an actual
   * argument in the context of `outer`.
   *
   * In case `actual`'s type depends on a type parameter g of a feature f and
   * the context is the corresponding type feature ft, then g will be replaced
   * by the corresponding type parameter of ft.
   *
   * @param actual an actual argument or null if not known
   *
   * @param outer the root feature that contains this call.
   *
   * @return the type of actual as seen within outer, or null if not known.
   */
  AbstractType typeFromActual(Expr actual,
                              AbstractFeature outer)
  {
    var actualType = actual == null ? null : actual.typeForInferencing();
    if (actualType != null)
      {
        actualType = actualType.replace_type_parameters_of_type_feature_origin(outer);
        if (!actualType.isGenericArgument() && actualType.featureOfType().isTypeFeature())
          {
            actualType = Types.resolved.f_Type.selfType();
          }
      }
    return actualType;
  }


  /**
   * Helper for inferGeneric and inferGenericLambdaResult to add a type that was
   * found to the list of found positions and types.
   *
   * @param foundAt list with one entry for each generic that is either null or
   * a list of the position/type pairs found so far.  This list will be created
   * if it does not exist and the new pair will be added.
   *
   * @param i index of the generic in foundAt
   *
   * @param pos the position to add
   *
   * @param t the type to add.
   */
  private void addPair(List<List<Pair<SourcePosition, AbstractType>>> foundAt, int i, SourcePosition pos, AbstractType t)
  {
    if (foundAt.get(i) == null)
      {
        foundAt.set(i, new List<>());
      }
    foundAt.get(i).add(new Pair<SourcePosition, AbstractType>(pos, t));
  }


  /**
   * Perform type inference for generics used in formalType that are instantiated by actualType.
   *
   * @param res the resolution instance.
   *
   * @param formalType the (possibly generic) formal type
   *
   * @param actualType the actual type
   *
   * @param pos source code position of the expression actualType was derived from
   *
   * @param conflict set of generics that caused conflicts
   *
   * @param foundAt the position of the expressions from which actual generics
   * were taken.
   */
  private void inferGeneric(Resolution res,
                            AbstractFeature outer,
                            AbstractType formalType,
                            AbstractType actualType,
                            SourcePosition pos,
                            boolean[] conflict,
                            List<List<Pair<SourcePosition, AbstractType>>> foundAt)
  {
    if (PRECONDITIONS) require
      (actualType.compareTo(actualType.replace_type_parameters_of_type_feature_origin(outer)) == 0);

    if (formalType.isGenericArgument())
      {
        var g = formalType.genericArgument();
        if (g.feature() == _calledFeature)
          { // we found a use of a generic type, so record it:
            var i = g.index();
            var gt = _generics.get(i);
            var nt = gt == Types.t_UNDEFINED ? actualType
                                             : gt.union(actualType);
            if (nt == Types.t_ERROR)
              {
                conflict[i] = true;
              }
            _generics = _generics.setOrClone(i, nt);
            addPair(foundAt, i, pos, actualType);
          }
      }
    else
      {
        var fft = formalType.featureOfType();
        res.resolveTypes(fft);
        var aft = actualType.isGenericArgument() ? null : actualType.featureOfType();
        if (fft == aft)
          {
            for (int i=0; i < formalType.generics().size(); i++)
              {
                if (i < actualType.generics().size())
                  {
                    inferGeneric(res,
                                 outer,
                                 formalType.generics().get(i),
                                 actualType.generics().get(i),
                                 pos, conflict, foundAt);
                  }
              }
          }
        else if (formalType.isChoice())
          {
            for (var ct : formalType.choiceGenerics())
              {
                inferGeneric(res, outer, ct, actualType, pos, conflict, foundAt);
              }
          }
        else if (aft != null)
          {
            for (var p: aft.inherits())
              {
                var pt = p.type();
                if (pt != Types.t_ERROR)
                  {
                    var apt = pt.applyTypePars(actualType);
                    inferGeneric(res, outer, formalType, apt, pos, conflict, foundAt);
                  }
              }
          }
      }
  }


  /**
   * Perform type inference for result type of lambda
   *
   * @param res the resolution instance.
   *
   * @param outer the feature containing this call
   *
   * @param formalType the (possibly generic) formal type
   *
   * @param al the lambda-expression we try to get the result from
   *
   * @param pos source code position of the expression actualType was derived from
   *
   * @param conflict set of generics that caused conflicts
   *
   * @param foundAt the position of the expressions from which actual generics
   * were taken.
   */
  private boolean inferGenericLambdaResult(Resolution res,
                                           AbstractFeature outer,
                                           AbstractType formalType,
                                           AbstractLambda al,
                                           SourcePosition pos,
                                           boolean[] conflict,
                                           List<List<Pair<SourcePosition, AbstractType>>> foundAt)
  {
    var result = false;
    if ((formalType.isFunctionType() || formalType.isLazyType()) &&
        formalType.generics().get(0).isGenericArgument()
        )
      {
        var rg = formalType.generics().get(0).genericArgument();
        var ri = rg.index();
        if (rg.feature() == _calledFeature && foundAt.get(ri) == null)
          {
            var at = actualArgType(res, formalType);
            if (!at.containsUndefined(true))
              {
                var rt = al.inferLambdaResultType(res, outer, at);
                if (rt != null)
                  {
                    _generics = _generics.setOrClone(ri, rt);
                  }
                addPair(foundAt, ri, pos, rt);
                result = true;
              }
          }
      }
    return result;
  }


  /**
   * Is this a tail recursive call?
   *
   * A tail recursive call within 'outer' is a call to 'outer' whose result is
   * returned without any further modification.
   *
   * This means, any call
   *
   *    target.outer arg1 arg2 ...
   *
   * is a tail recursive call provided that the result returned is not
   * processed. The call may be dynamic, i.e., target may evaluate to something
   * different than outer.outer.
   *
   * This is used to allow cyclic type inferencing of the form
   *
   *   f =>
   *     if c
   *       x
   *     else
   *       f
   *
   * Which must return a value of x's type.
   */
  boolean isTailRecursive(AbstractFeature outer)
  {
    return
      calledFeature() == outer &&
      returnsThis(outer.code());
  }


  /**
   * Check if the result returns by the given expression is the result of this
   * call (i.e., this call is a tail call in e).
   *
   * @param e an expression.
   *
   * @return true iff this is a expression that can produce the result of e (but
   * not necessarily the only one).
   */
  private boolean returnsThis(Expr e)
  {
    if (e instanceof If i)
      {
        var it = i.branches();
        while (it.hasNext())
          {
            if (returnsThis(it.next()))
              {
                return true;
              }
          }
      }
    else if (e instanceof Match m)
      {
        for (var c : m.cases())
          {
            if (returnsThis(c.code()))
              {
                return true;
              }
          }
      }
    else if (e instanceof Block b)
      {
        var r = b.resultExpression();
        return r != null && returnsThis(r);
      }
    return e == this;
  }


  /**
   * true before types are resolved and typeParameters() is just a list of
   * Types.t_UNDEFINED since the actual types still need to be inferred from
   * actual arguments.
   */
  boolean needsToInferTypeParametersFromArgs()
  {
    return _calledFeature != null && _generics == NO_GENERICS && _calledFeature.generics() != FormalGenerics.NONE;
  }


  /**
   * Field used to detect and avoid repeated calls to resolveTypes for the same
   * outer feature.  resolveTypes may be called repeatedly when types are
   * determined on demand for type inference for type parameters in a call. This
   * field will record that resolveTypes was called for a given outer feature.
   * This is used to not perform resolveTypes repeatedly.
   *
   * However, moving an expression into a lambda or a lazy value will change its
   * outer feature and resolve will have to be repeated.
   */
  private AbstractFeature _resolvedFor;


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this expression.
   */
  public Call resolveTypes(Resolution res, AbstractFeature outer)
  {
    Call result = this;
    if (_resolvedFor == outer)
      {
        return this;
      }
    _resolvedFor = outer;
    if (!loadCalledFeatureUnlessTargetVoid(res, outer))
      { // target of this call results in `void`, so we replace this call by the
        // target. However, we have to return a `Call` and `_target` is
        // `Expr`. Solution: we wrap `_target` into a call `universe.id void
        // _target`.
        result = new Call(pos(),
                          new Universe(),
                          new List<>(new Actual(_target)),
                          new List<>(Types.resolved.t_void),
                          new List<>(_target),
                          Types.resolved.f_id,
                          Types.resolved.t_void);
        result.resolveTypes(res, outer);
      }

    if (CHECKS) check
      (res._options.isLanguageServer() || _calledFeature != null || _pendingError != null);

    if (_calledFeature == Types.f_ERROR)
      {
        _type = Types.t_ERROR;
      }
    else if (_calledFeature != null)
      {
        _generics = FormalGenerics.resolve(res, _generics, outer);
        _generics = _generics.map(g -> g.resolve(res, _calledFeature.outer()));

        ListIterator<Expr> i = _actuals.listIterator();
        while (i.hasNext())
          {
            Expr actl = i.next();
            if (actl instanceof Actual aa)
              {
                actl = aa.expr(this);
              }
            if (CHECKS) check
              (actl != null);
            i.set(actl);
          }

        propagateForPartial(res, outer);
        if (needsToInferTypeParametersFromArgs())
          {
            inferGenericsFromArgs(res, outer);
            for (var r : _whenInferredTypeParameters)
              {
                r.run();
              }
          }
        inferFormalArgTypesFromActualArgs(outer);
        if (_calledFeature.generics().errorIfSizeDoesNotMatch(_generics,
                                                              pos(),
                                                              "call",
                                                              "Called feature: "+_calledFeature.qualifiedName()+"\n"))
          {
            var cf = _calledFeature;
            var t = isTailRecursive(outer) ? Types.resolved.t_void // a tail recursive call will not return and execute further
                                           : cf.resultTypeIfPresent(res, _generics);

            if (t == Types.t_ERROR)
              {
                _type = Types.t_ERROR;
              }
            else if (t != null)
              {
                result = resolveImplicitSelect(res, outer, t);
                setActualResultType(res, t);
                // Convert a call "f.g a b" into "f.g.call a b" in case f.g takes no
                // arguments and returns a Function or Routine
                result = result.resolveImmediateFunctionCall(res, outer); // NYI: Separate pass? This currently does not work if type was inferred
              }
            if (t == null || isTailRecursive(outer))
              {
                cf.whenResolvedTypes
                  (() -> setActualResultType(res, cf.resultTypeForTypeInference(pos(), res, _generics)));
              }
          }
        else
          {
            _type = Types.t_ERROR;
          }
        resolveFormalArgumentTypes(res);
      }
    if (_type != null &&
        // exclude call to create type instance, it requires origin's type parameters:
        !calledFeature().isTypeFeature()
        )
      {
        _type = _type.replace_type_parameters_of_type_feature_origin(outer);
      }

    // make sure type features exist for all features used as actual type
    // parameters. This is required since the type parameter can be used to get
    // an instance of the type feature, which requires the presence of all type
    // features of all actual type parameters and outer types. See #2114 for an
    // example: There `array a` is used, which requires the presence of `a`'s
    // type feature.
    for (var t : actualTypeParameters())
      {
        if (!t.isGenericArgument())
          {
            t.applyToGenericsAndOuter(t2 ->
                                      {
                                        if (!t2.isGenericArgument())
                                          {
                                            var f2 = t2.featureOfType();
                                            if (!f2.isUniverse() && f2.state().atLeast(State.RESOLVED_INHERITANCE))
                                              {
                                                var t2f = f2.typeFeature(res);
                                              }
                                          }
                                        return t2;
                                      });
          }
      }

    if (POSTCONDITIONS) ensure
      (_pendingError != null || Errors.any() || result.typeForInferencing() != Types.t_ERROR);

    return _pendingError == null && result.typeForInferencing() == Types.t_ERROR && !res._options.isLanguageServer()
      ? Call.ERROR // short circuit this call
      : result;
  }


  /**
   * During type inference: Inform this expression that it is used in an
   * environment that expects the given type.  In particular, if this
   * expression's result is assigned to a field, this will be called with the
   * type of the field.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   */
  public void propagateExpectedType(Resolution res, AbstractFeature outer)
  {
    applyToActualsAndFormalTypes((actual, formalType) -> actual.propagateExpectedType(res, outer, formalType));

    if (_target != null)
      {
        // NYI: Need to check why this is needed, it does not make sense to
        // propagate the target's type to target. But if removed,
        // tests/reg_issue16_chainedBool/ fails with C backend:
        var t = _target.typeForInferencing();
        if (t != null)
          {
            _target = _target.propagateExpectedType(res, outer, _target.typeForInferencing());
          }
      }
  }


  /**
   * During type inference: Inform this expression that it is used in an
   * environment that expects the given type.  In particular, if this
   * expression's result is assigned to a field, this will be called with the
   * type of the field.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   *
   * @param t the expected type.
   *
   * @return either this or a new Expr that replaces thiz and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the expression that reads the field.
   */
  public Expr propagateExpectedType(Resolution res, AbstractFeature outer, AbstractType t)
  {
    Expr r = this;
    if (t.isFunctionType()         &&
        !_wasImplicitImmediateCall &&
        _type != Types.t_ERROR     &&
        (_type == null || !_type.isAnyFunctionType()))
      {
        r = propagateExpectedTypeForPartial(res, outer, t);
        if (r != this)
          {
            r.propagateExpectedType(res, outer, t);
          }
      }
    return r;
  }


  /**
   * During type inference: Wrap expressions that are assigned to lazy actuals
   * in functions.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   */
  public void wrapActualsInLazy(Resolution res, AbstractFeature outer)
  {
    applyToActualsAndFormalTypes((actual, formalType) -> actual.wrapInLazy(res, outer, formalType));
  }


  /**
   * Helper for propagateExpectedType and wrapActualsInLazy to apply `f` to all
   * actual value arguments and their formal types.
   *
   * @param f function to apply to all actuals
   */
  void applyToActualsAndFormalTypes(java.util.function.BiFunction<Expr, AbstractType, Expr> f)
  {
    if (_type != Types.t_ERROR &&
        _resolvedFormalArgumentTypes != null &&
        _actuals.size() == _resolvedFormalArgumentTypes.length /* this will cause an error in checkTypes() */ )
      {
        int count = 0;
        ListIterator<Expr> i = _actuals.listIterator();
        while (i.hasNext())
          {
            Expr actl = i.next();
            var frmlT = _resolvedFormalArgumentTypes[count];
            if (actl != null && frmlT != Types.t_ERROR)
              {
                var a = f.apply(actl, frmlT);
                if (CHECKS) check
                  (a != null);
                i.set(a);
              }
            count++;
          }
      }
  }


  /**
   * Boxing for actual arguments: Find actual arguments of value type that are
   * assigned to formal argument types that are references and box them.
   *
   * @param outer the feature that contains this expression
   */
  public void box(AbstractFeature outer)
  {
    if (_type != Types.t_ERROR && _resolvedFormalArgumentTypes != null)
      {
        int fsz = _resolvedFormalArgumentTypes.length;
        if (_actuals.size() ==  fsz)
          {
            int count = 0;
            ListIterator<Expr> i = _actuals.listIterator();
            while (i.hasNext())
              {
                Expr actl = i.next();
                var rft = _resolvedFormalArgumentTypes[count];
                if (actl != null && rft != Types.t_ERROR)
                  {
                    var a = actl.box(rft);
                    if (CHECKS) check
                      (a != null);
                    i.set(a);
                  }
                count++;
              }
          }
      }
  }


  /**
   * perform static type checking, i.e., make sure that in all assignments from
   * actual to formal arguments, the types match.
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this expression.
   */
  public void checkTypes(Resolution res, AbstractFeature outer)
  {
    reportPendingError();

    if (CHECKS) check
      (res._options.isLanguageServer() || _type != null);

    if (_type != null && _type != Types.t_ERROR)
      {
        var o = _type;
        while (o != null && !o.isGenericArgument())
          {
            o = o.outer();
            if (o != null && o.isRef() && !o.featureOfType().isThisRef())
              {
                AstErrors.illegalCallResultType(this, _type, o);
                o = null;
              }
          }

        int fsz = _resolvedFormalArgumentTypes.length;
        if (_actuals.size() !=  fsz)
          {
            AstErrors.wrongNumberOfActualArguments(this);
          }
        else
          {
            int count = 0;
            for (Expr actl : _actuals)
              {
                var frmlT = _resolvedFormalArgumentTypes[count];
                if (frmlT != Types.t_ERROR)
                  {
                    if (actl == Expr.NO_VALUE)
                      {
                        var pos = _actualsNew.get(_generics.size() + count).pos();
                        var typ = _actualsNew.get(_generics.size() + count)._type;
                        AstErrors.unexpectedTypeParameterInCall(pos,
                                                                _calledFeature,
                                                                count,
                                                                frmlT,
                                                                typ);
                      }
                    else if (actl != null && !frmlT.isAssignableFrom(actl.type()))
                      {
                        AstErrors.incompatibleArgumentTypeInCall(_calledFeature, count, frmlT, actl);
                      }
                  }
                count++;
              }
          }
        if (_calledFeature.isChoice())
          {
            boolean ok = false;
            if (outer != null && outer.isChoice())
              {
                for (var p : outer.inherits())
                  {
                    ok = ok || p == this;
                  }
              }
            if (!ok)
              {
                AstErrors.cannotCallChoice(pos(), _calledFeature);
              }
          }

        // Check that generics match formal generic constraints
        AbstractType.checkActualTypePars(_calledFeature, _generics, _unresolvedGenerics, pos());
      }
  }


  /**
   * Helper function for resolveSyntacticSugar: Create "if cc block else
   * elseBlock" and handle the case that cc is a compile time constant.
   *
   * NYI: move this to If.resolveSyntacticSugar!
   */
  private Expr newIf(Expr cc, Expr block, Expr elseBlock)
  {
    return
      !(cc instanceof BoolConst bc)   ? new If(pos(), cc, block, elseBlock) :
      bc.getCompileTimeConstBool() ? block : elseBlock;
  }


  /**
   * Syntactic sugar resolution: This does the following:
   *
   *  - convert boolean operations &&, || and : into if-expressions
   *  - convert repeated boolean operations ! into identity   // NYI
   *  - perform constant propagation for basic algebraic ops  // NYI
   *  - simplify boolean algebra via K-Map and/or Quine–McCluskey // NYI
   *  - replace calls to intrinsics that return compile time constants
   *
   * @return a new Expr to replace this call or this if it remains unchanged.
   */
  Expr resolveSyntacticSugar(Resolution res, AbstractFeature outer)
  {
    Expr result = this;
    // must not be inheritance call since we do not want `: i32 2` turned into a numeric literal.
    // also we can not inherit from none constructor features like and/or etc.
    if (_pendingError == null && !Errors.any() && !isInheritanceCall())
      {
        // convert
        //   a && b into if a b     else false
        //   a || b into if a true  else b
        //   a: b   into if a b     else true
        //   !a     into if a false else true
        var cf = _calledFeature;
        if      (cf == Types.resolved.f_bool_AND    ) { result = newIf(_target, _actuals.get(0), BoolConst.FALSE); }
        else if (cf == Types.resolved.f_bool_OR     ) { result = newIf(_target, BoolConst.TRUE , _actuals.get(0)); }
        else if (cf == Types.resolved.f_bool_IMPLIES) { result = newIf(_target, _actuals.get(0), BoolConst.TRUE ); }
        else if (cf == Types.resolved.f_bool_NOT    ) { result = newIf(_target, BoolConst.FALSE, BoolConst.TRUE ); }

        // replace e.g. i16 7 by just the NumLiteral 7. This is necessary for syntaxSugar2 of InlineArray to work correctly.
        else if (cf == Types.resolved.t_i8 .featureOfType()) { result = this._actuals.get(0).propagateExpectedType(res, outer, Types.resolved.t_i8 ); }
        else if (cf == Types.resolved.t_i16.featureOfType()) { result = this._actuals.get(0).propagateExpectedType(res, outer, Types.resolved.t_i16); }
        else if (cf == Types.resolved.t_i32.featureOfType()) { result = this._actuals.get(0).propagateExpectedType(res, outer, Types.resolved.t_i32); }
        else if (cf == Types.resolved.t_i64.featureOfType()) { result = this._actuals.get(0).propagateExpectedType(res, outer, Types.resolved.t_i64); }
        else if (cf == Types.resolved.t_u8 .featureOfType()) { result = this._actuals.get(0).propagateExpectedType(res, outer, Types.resolved.t_u8 ); }
        else if (cf == Types.resolved.t_u16.featureOfType()) { result = this._actuals.get(0).propagateExpectedType(res, outer, Types.resolved.t_u16); }
        else if (cf == Types.resolved.t_u32.featureOfType()) { result = this._actuals.get(0).propagateExpectedType(res, outer, Types.resolved.t_u32); }
        else if (cf == Types.resolved.t_u64.featureOfType()) { result = this._actuals.get(0).propagateExpectedType(res, outer, Types.resolved.t_u64); }
        else if (cf == Types.resolved.t_f32.featureOfType()) { result = this._actuals.get(0).propagateExpectedType(res, outer, Types.resolved.t_f32); }
        else if (cf == Types.resolved.t_f64.featureOfType()) { result = this._actuals.get(0).propagateExpectedType(res, outer, Types.resolved.t_f64); }

      }
    return result;
  }


  /**
   * When wrapping an expression into a Lazy feature, we need to "tell it" that its
   * outer feature has changed. Otherwise, old information from previous results of
   * type resolution might remain there.
   */
  public Call updateTarget(Resolution res, AbstractFeature outer)
  {
    if (_targetFrom != null)
      {
        _target = _targetFrom.target(pos(), res, outer);
      }
    return this;
  }


  /**
   * Reset static fields
   */
  public static void reset()
  {
    ERROR = new Call(SourcePosition.builtIn, Errors.ERROR_STRING)
    {
      { _type = Types.t_ERROR; }
      @Override
      Expr box(AbstractType frmlT)
      {
        return this;
      }
    };
  }

}

/* end of file */
