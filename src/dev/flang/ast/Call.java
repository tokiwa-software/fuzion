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

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;
import dev.flang.util.SourceRange;
import dev.flang.util.YesNo;
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
   * Dummy Call. Used to represent errors.
   */
  public static Call ERROR;


  /*----------------------------  variables  ----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  private final SourcePosition _pos;


  /**
   * name of called feature, set by parser
   */
  protected String _name;
  public String name() { return _name; }


  /**
   * For a call a.b.4 with a select clause ".4" to pick a variant from a field
   * of an open generic type, this is the chosen variant.
   */
  private final int _select;
  public int select() { return _select; }


  /**
   * actual generic arguments, set by parser
   */
  public /*final*/ List<AbstractType> _generics; // NYI: Make this final again when resolveTypes can replace a call
  public final List<AbstractType> _unresolvedGenerics;
  public List<AbstractType> actualTypeParameters()
  {
    var res = _generics;
    if (_generics == NO_GENERICS && needsToInferTypeParametersFromArgs())
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
  public List<Expr> _actuals;
  public List<Expr> actuals() { return _actuals; }


  /**
   * the target of the call, null for "this". Set by parser
   */
  protected Expr _target;
  public Expr target() { return _target; }
  private FeatureAndOuter _targetFrom = null;


  /**
   * Result of `targetType(Resolution, Context)` to be used after resolution.
   */
  protected AbstractType _targetType;

  /**
   * Type of the target of this call, set during type resolution. `null` if not
   * set yet.
   */
  AbstractType targetType() { return _targetType; }


  /**
   * Since _target will be replaced during phases RESOLVING_DECLARATIONS or
   * RESOLVING_TYPES we keep a copy of the original.  We will need the original
   * later to check if there is an ambiguity between the found called feature
   * and the partial application of another feature,
   * see @partiallyApplicableAlternative).
   */
  private final Expr _originalTarget;


  /**
   * The feature that is called by this call, resolved when
   * loadCalledFeature() is called.
   */
  protected AbstractFeature _calledFeature;


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
  private AbstractType _type;


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


  private boolean _recursiveResolveType = false;


  /**
   * Will be set to true for a call to a direct parent feature in an inheritance
   * call.
   */
  public boolean _isInheritanceCall = false;
  public boolean isInheritanceCall() { return _isInheritanceCall; }


  /**
   * Flag that is set be resolveImmediateFunctionCall if a call {@code f()} is
   * converted to {@code f.call} for nullary functions or lazy values.  This is needed
   * to avoid a possible error for a potential partial application ambiguity.
   */
  boolean _wasImplicitImmediateCall = false;
  int _originalArgCount;


  /**
   * A call that has been moved to a new instance of Call due to syntax sugar.
   * In particular, a call {@code a < b} on the right hand side of a chained boolean
   * call will be moved here while this will be replaced by a call to
   * {@code bool.infix &&}.  Also, an implicit call like {@code f()} that is turned into
   * {@code f.call}  will see the  new call moved to this.
   */
  Call _movedTo = null;


  /**
   * If this Call is the target of another call, this field will be set to that
   * other Call when this is created.
   *
   * This will be used to produce suggestions to fix errors reported for this
   * call.
   */
  public Call _targetOf_forErrorSolutions = null;


  /*-------------------------- constructors ---------------------------*/


  /**
   * Constructor to read a local field
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param n the name of the called feature
   */
  Call(SourcePosition pos, String n)
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
  Call(SourcePosition pos, Expr t, String n)
  {
    this(pos, t, n, Expr.NO_EXPRS);
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
  Call(SourcePosition pos, Expr t, String n, List<Expr> la)
  {
    this(pos, t, n, FuzionConstants.NO_SELECT, NO_GENERICS, la, null);

    if (PRECONDITIONS) require
      (la != null);
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
   */
  Call(SourcePosition pos, Expr t, AbstractFeature calledFeature)
  {
    this(pos, t, calledFeature.featureName().baseName(), FuzionConstants.NO_SELECT, NO_GENERICS, Expr.NO_EXPRS, calledFeature);
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
   */
  Call(SourcePosition pos,
       Expr target,
       List<AbstractType> generics,
       List<Expr> actuals,
       AbstractFeature calledFeature)
  {
    this(pos, target, calledFeature.featureName().baseName(), FuzionConstants.NO_SELECT, generics, actuals, calledFeature);
    if (PRECONDITIONS) check
      (calledFeature.generics().sizeMatches(generics) || generics.contains(Types.t_ERROR));
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
   * index '.0', '.1', etc. NO_SELECT for none.
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
       String name,
       int select,
       List<AbstractType> generics,
       List<Expr> actuals,
       AbstractFeature calledFeature)
  {
    if (PRECONDITIONS) require
      (Errors.any() || generics.stream().allMatch(g -> !g.containsError()),
       name != FuzionConstants.UNIVERSE_NAME,
       _actuals == null || _actuals.stream().allMatch(a -> a != Universe.instance));

    this._pos = pos;
    this._name = name;
    this._select = select;
    this._generics = generics;
    this._unresolvedGenerics = generics;
    this._actuals = actuals;
    this._target = target;
    if (target instanceof Call tc)
      {
        tc._targetOf_forErrorSolutions = this;
      }
    this._originalTarget = _target;
    this._calledFeature = calledFeature;
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
   * Get the type of the target.  In case the target's type is a generic type
   * parameter, return its constraint.
   *
   * @return the type of the target or null if unknown.
   */
  private AbstractType targetTypeOrConstraint(Resolution res, Context context)
  {
    if (PRECONDITIONS) require
      (_target != null);

    var result = _target.typeForInferencing();
    result = result == null
      ? null
      : result.selfOrConstraint(res, context);

    if (POSTCONDITIONS) ensure
      (result == null || !result.isGenericArgument());
    return result;
  }


  /**
   * Helper to check if the target of this call is undefined, i.e., it might
   * have a pending error.
   */
  private boolean targetTypeUndefined()
  {
    return _target != null && _target.typeForInferencing() == null;
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
   * @param context the source code context where this Call is used
   *
   * @return the actual type applying actual type parameters known from the
   * target of this call and actual type parameters given in this call. Result
   * is interned.
   */
  private AbstractType actualArgType(Resolution res, AbstractType frmlT, AbstractFeature arg, Context context)
  {
    if (PRECONDITIONS) require
      (!frmlT.isOpenGeneric());

    // NYI: CLEANUP: This is part of what is done in Call.adjustResultType, see comment there.
    AbstractType result = adjustThisTypeForTarget(frmlT, true, arg, context);
    result = targetTypeOrConstraint(res, context)
      .actualType(result, context)
      .applyTypePars(_calledFeature, _generics);

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }



  /**
   * Is the target of this call a type parameter?
   *
   * @return true for a call to {@code T.xyz}, {@code U.xyz} or {@code V.xyz} in a feature
   * {@code f(T,U,V type)}, false otherwise.
   */
  private boolean targetIsTypeParameter()
  {
    return _target instanceof Call tc && tc != ERROR && tc._calledFeature.isTypeParameter();
  }


  /**
   * Get the type of the target as seen by this call
   *
   * When calling {@code X.f} and {@code X} is a type parameter and {@code f} is a constructor,
   * then {@code X}'s type is the type {@code X}, while for a function {@code f} the type is {@code X}'s
   * constraint.
   *
   * @param context the source code context where this Call is used
   *
   * @return the type of the target.
   */
  private AbstractType targetType(Resolution res, Context context)
  {
    _target = res.resolveType(_target, context);
    _targetType =
      // NYI: CLEANUP: For a type parameter, the feature result type is abused
      // and holds the type parameter constraint.  As a consequence, we have to
      // fix this here and set the type of the target explicitly here.
      //
      // Would be better if AbstractFeature.resultType() would do this for us:
      _target instanceof Call tc &&
      targetIsTypeParameter()          ? tc.calledFeature().asGenericType() :
      calledFeature().isConstructor()  ? _target.type()
                                       : targetTypeOrConstraint(res, context);
    return _targetType;
  }


  /**
   * Get the feature of the target of this call.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Call is used. For a call
   * c in an inherits clause ("f : c { }"), context.outerFeature() is the outer
   * feature of f.
   *
   * @return the feature of the target of this call or null if lookup for the
   * target feature failed.
   */
  protected AbstractFeature targetFeature(Resolution res, Context context)
  {
    AbstractFeature result = null;

    // are we searching for features called via outer's inheritance calls?
    if (res.state(context.outerFeature()) == State.RESOLVING_INHERITANCE)
      {
        if (_target instanceof Universe)
          {
            result = _target.type().feature();
          }
        else if (_target instanceof Call tc)
          {
            _target.loadCalledFeature(res, context);
            result = tc.calledFeature();
          }
        else
          {
            result = context.outerFeature().outer();   // For an inheritance call, we do not permit call to outer' features,
                                                       // but only to the outer clazz' features:
          }
      }
    else if (_target != null)
      {
        _target.loadCalledFeature(res, context);
        _target = res.resolveType(_target, context);
        var tt = targetTypeOrConstraint(res, context);

        if (tt == null && _target instanceof Call c)
          {
            c._pendingError = ()->
              {
                if (c._calledFeature == null)
                  {
                    c.triggerFeatureNotFoundError(res, context);
                  }
                else
                  {
                    AstErrors.forwardTypeInference(c.pos(), c._calledFeature, c._calledFeature.pos());
                  }
                setToErrorState();
              };
          }
        else if (tt != null)
          {
            result = tt.feature();
          }
      }
    else
      { // search for feature in outer
        result = context.outerFeature();
      }

    return result;
  }


  /*-------------------------------------------------------------------*/


  /**
   * if loadCalledFeature is about to fail, try if we can convert this call into
   * a chain of boolean calls:
   *
   * check if we have a call of the form
   *
   * <pre>{@code
   *   a < b <= c
   * }</pre>
   *
   * and convert it to
   *
   * <pre>{@code
   *   a < {tmp := b; tmp} && tmp <= c
   * }</pre>
   *
   * @param res Resolution instance
   *
   * @param context the source code context where this Call is used
   */
  protected void findChainedBooleans(Resolution res, Context context)
  {
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
   * @param context the source code context where this Call is used. For a call
   * c in an inherits clause ("f : c { }"), context.outerFeature() is the outer
   * feature of f.
   */
  void loadCalledFeature(Resolution res, Context context)
  {
    if (PRECONDITIONS) require
      (context != null);
    var ignore = loadCalledFeatureUnlessTargetVoid(res, context);
  }


  /**
   * Load all features that are called by this expression.  This is called
   * during state RESOLVING_INHERITANCE for calls in the inherits clauses and
   * during state RESOLVING_TYPES for all other calls.
   *
   * @param res the resolution instance.
   * instance.
   *
   * @param context the source code context where this Call is used. For a call
   * c in an inherits clause ("f : c { }"), context.outerFeature() is the outer
   * feature of f.
   *
   * @return true if everything is fine, false in case the target results in
   * void and we hence can replace the call by _target.
   */
  private boolean loadCalledFeatureUnlessTargetVoid(Resolution res, Context context)
  {
    var outer = context.outerFeature();
    if (PRECONDITIONS) require
      (outer.isTypeParameter()   // NYI: type parameters apparently inherit ANY and are not resolved yet. Type parameters should not inherit anything and this special handling should go.
       ||
       (res.state(outer) == State.RESOLVING_INHERITANCE
       ? res.state(outer.outer()).atLeast(State.RESOLVING_DECLARATIONS)
       : res.state(outer)        .atLeast(State.RESOLVING_DECLARATIONS)));

    var targetVoid = false;
    AbstractFeature targetFeature = null;
    if (_calledFeature == null)
      {
        targetFeature = targetFeature(res, context);
        if (CHECKS) check
          (Errors.any() || targetFeature != Types.f_ERROR);
        targetVoid = Types.resolved != null && targetFeature == Types.resolved.f_void && targetFeature != outer;
        if (targetVoid || targetFeature == Types.f_ERROR)
          {
            _calledFeature = Types.f_ERROR;
          }
      }
    if (_calledFeature == null && targetFeature != null)
      {
        res.resolveDeclarations(targetFeature);
        var found = findOnTarget(res, targetFeature, true);
        var fos = found.v0();
        var fo  = found.v1();
        if (fo != null &&
            !isSpecialWrtArgs(fo._feature) &&
            fo._feature != Types.f_ERROR &&
            _generics.isEmpty() &&
            _actuals.size() != fo._feature.valueArguments().size() &&
            !fo._feature.hasOpenGenericsArgList(res))
          {
            splitOffTypeArgs(res, fo._feature, outer);
          }
        if (fo != null)
          {
            _calledFeature = fo._feature;
            if (_target == null)
              {
                _target = fo.target(pos(), res, context);
                _targetFrom = fo;
              }
          }

        if (_calledFeature == null &&                 // nothing found, so flag error
            (Types.resolved == null ||                // may happen when building bad base.fum
             targetFeature != Types.resolved.f_void)) // but allow to call anything on void
          {
            var tf = targetFeature;
            _pendingError = ()->
              {
                if (!fos.isEmpty() && _actuals.size() == 0 && fos.get(0)._feature.isChoice())
                  { // give a more specific error when trying to call a choice feature
                    AstErrors.cannotCallChoice(pos(), fos.get(0)._feature);
                  }
                else
                  {
                    triggerFeatureNotFoundError(res, fos, tf);
                  }
              };
          }

      }
    if (_calledFeature == null)
      { // nothing found, try if we can build a chained bool: `a < b < c` => `(a < b) && (a < c)`
        resolveTypesOfActuals(res, context);
        findChainedBooleans(res, context);
      }
    // !isInheritanceCall: see issue #2153
    if (_calledFeature == null && !isInheritanceCall())
      { // nothing found, try if we can build operator call: `a + b` => `x.y.z.this.infix + a b`
        findOperatorOnOuter(res, context);
      }
    if (_calledFeature == Types.f_ERROR)
      {
        _actuals = new List<>();
      }

    resolveTypesOfActuals(res, context);

    if (POSTCONDITIONS) ensure
      (Errors.any() || !calledFeatureKnown() || _calledFeature != Types.f_ERROR || targetVoid,
       Errors.any() || _target        != Call.ERROR,
       Errors.any() || _calledFeature != null || _pendingError != null || targetTypeUndefined(),
       Errors.any() || _target        != null || _pendingError != null);

    return !targetVoid;
  }


  /**
   * helper for triggering a feature not found error.
   */
  private void triggerFeatureNotFoundError(Resolution res, Context context)
  {
    var tf = targetFeature(res, context);
    triggerFeatureNotFoundError(res, findOnTarget(res, tf, true).v0(), tf);
  }


  /**
   * helper for triggering a feature not found error.
   */
  private void triggerFeatureNotFoundError(Resolution res, List<FeatureAndOuter> fos, AbstractFeature tf)
  {
    var calledName = FeatureName.get(_name, _actuals.size());
    AstErrors.calledFeatureNotFound(this,
                                    calledName,
                                    tf,
                                    _target,
                                    FeatureAndOuter.findExactOrCandidate(fos,
                                                                        (FeatureName fn) -> false,
                                                                        (AbstractFeature f) -> f.featureName().equalsBaseName(calledName)),
                                    hiddenCandidates(res, tf, calledName));
  }


  /**
   * Find the feature that may be called on the given target
   *
   * @param res the resolution instance
   *
   * @param target the - assumed - target of the call
   *
   * @return a pair of
   *          1) all found features matching the name
   *          2) the matching feature or null if none was found
   */
  private Pair<List<FeatureAndOuter>, FeatureAndOuter> findOnTarget(Resolution res, AbstractFeature target, boolean mayBeSpecialWrtArgs)
  {
    var calledName = FeatureName.get(_name, _actuals.size());
    var fos = res._module.lookup(target, _name, this, _target == null, false);
    for (var fo : fos)
      {
        if (fo._feature instanceof Feature ff && ff.state().atLeast(State.RESOLVED_DECLARATIONS))
          {
            ff.resolveArgumentTypes(res);
          }
      }
    var fo = FeatureAndOuter.filter(fos, pos(), FuzionConstants.OPERATION_CALL, calledName,
      ff -> mayMatchArgList(ff, false) || ff.hasOpenGenericsArgList(res));
    if (fo == null && mayBeSpecialWrtArgs)
      { // handle implicit calls `f()` that expand to `f.call()`:
        fo =
          FeatureAndOuter.filter(fos, pos(), FuzionConstants.OPERATION_CALL, calledName, ff -> isSpecialWrtArgs(ff));
      }
    return new Pair<>(fos, fo);
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

    setToErrorState0();
  }


  /**
   * same as setToErrorState but without
   * the requirement that there are any errors.
   */
  private void setToErrorState0()
  {
    if (!Types._options.isLanguageServer())
      {
        _calledFeature = Types.f_ERROR;
        _target = Call.ERROR;
        _actuals = new List<>();
        _generics = new List<>();
        _type = Types.t_ERROR;
        if (_movedTo != null)
          {
            _movedTo.setToErrorState();
          }
      }
  }


  /**
   * Try to find an alternative called feature by using partial application. If
   * a suitable alternative is found, return it.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Call is used
   *
   * @param expectedType the expected type.
   */
  FeatureAndOuter partiallyApplicableAlternative(Resolution res, Context context, AbstractType expectedType)
  {
    if (PRECONDITIONS) require
      (expectedType.isFunctionTypeExcludingLazy(),
       _name != null);

    FeatureAndOuter result = null;
    var n = expectedType.arity() + (_wasImplicitImmediateCall ? _originalArgCount : _actuals.size());

    // if loadCalledFeatureUnlessTargetVoid has found a suitable called
    // feature in an outer feature, it will have replaced a null _target, so
    // we check _originalTarget here to not check all outer features:
    var traverseOuter = _originalTarget == null;
    var targetFeature = traverseOuter ? context.outerFeature() : targetFeature(res, context);
    if (targetFeature != null)
      {
        var fos = res._module.lookup(targetFeature, _name, this, traverseOuter, false);
        var calledName = FeatureName.get(_name, n);
        result = FeatureAndOuter.filter(fos, pos(), FuzionConstants.OPERATION_CALL, calledName, ff -> ff.valueArguments().size() == n);
      }
    return result;
  }


  /**
   * Is this an operator call like {@code a+b} or {@code -x} in contrast to a named call {@code f}
   * or {@code t.g}?
   *
   * @param parenthesesAllowed true if an operator call in parentheses is still
   * ok.  {@code (+x)}.
   */
  boolean isOperatorCall(boolean parenthesesAllowed)
  {
    return false;
  }


  /**
   * @return list of features that would match called name and args but are not visible.
   */
  private List<FeatureAndOuter> hiddenCandidates(Resolution res, AbstractFeature targetFeature, FeatureName calledName)
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


  /**
   * Field used to detect and avoid repeated calls to resolveTypesOfActuals for
   * the same context.  Moving the call into a lambda or a lazy value will
   * change its context and resolution of actuals will have to be repeated.
   */
  protected Context _actualsResolvedFor;


  /**
   * Resolve types of actual arguments for given outer features.  This may be
   * called repeatedly with different outer arguments as a result of this call
   * being moved into a different feature (lambda, lazy, etc.).
   *
   * @param res the resolution instance
   *
   * @param context the source code context where this Call is used
   */
  private void resolveTypesOfActuals(Resolution res, Context context)
  {
    if (_actualsResolvedFor != context)
      {
        _actualsResolvedFor = context;

        context.outerFeature().whenResolvedTypes
          (() ->
           {
             var i = _actuals.listIterator();
             while (i.hasNext() &&
                    _actualsResolvedFor == context  && // Abandon resolution if context changed.
                    _calledFeature != null &&          // call itself is not resolved (due to partial application)
                    _calledFeature != Types.f_ERROR)   // or call itself could not be resolved
               {
                 var a = i.next();
                 var a1 = res.resolveType(a, context);
                 if (CHECKS) check
                   (a1 != null,
                    a1 != Universe.instance);
                 i.set(a1);
               }
           });
      }
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
   * @param res Resolution instance
   *
   * @param context the source code context where this Call is used
   */
  private void findOperatorOnOuter(Resolution res, Context context)
  {
    if (isOperatorCall())
      {
        var calledName = FeatureName.get(_name, _actuals.size()+1);
        var fo = res._module.lookup(context.outerFeature(), _name, this, true, false);
        var foa = FeatureAndOuter.filter(fo, pos(), FuzionConstants.OPERATION_CALL, calledName, ff -> mayMatchArgList(ff, true));
        if (foa != null && _target != Universe.instance)
          {
            _calledFeature = foa._feature;
            _resolvedFormalArgumentTypes = null;
            _pendingError = null;
            var newActuals = new List<>(_target);
            newActuals.addAll(_actuals);
            if (CHECKS) check
              (newActuals == null || newActuals.stream().allMatch(a -> a != Universe.instance));
            _actuals = newActuals;
            _target = foa.target(pos(), res, context);
          }
      }
  }


  /**
   * Is this an operator call?
   */
  private boolean isOperatorCall()
  {
    return
      _name.startsWith(FuzionConstants.INFIX_OPERATOR_PREFIX) ||
      _name.startsWith(FuzionConstants.PREFIX_OPERATOR_PREFIX) ||
      _name.startsWith(FuzionConstants.POSTFIX_OPERATOR_PREFIX);
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
  protected void splitOffTypeArgs(Resolution res, AbstractFeature calledFeature, AbstractFeature outer)
  {
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
    /* maybe an implicit call to a Function / Routine, see resolveImmediateFunctionCall() */
    return ff.arguments().size()==0;
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
   * typeForInferencing returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  @Override
  AbstractType typeForInferencing()
  {
    return (_calledFeature instanceof Feature f)
      && f.isAnonymousInnerFeature()
      && f.inherits().getFirst().typeForInferencing() != null
      && f.inherits().getFirst().typeForInferencing().isRef() == YesNo.yes
      ? f.inherits().getFirst().typeForInferencing()
      : _type;
  }


  /**
   * type returns the type of this expression or Types.t_ERROR if the type is
   * still unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or t_ERROR in case it is not known yet.
   * t_FORWARD_CYCLIC in case the type can not be inferred due to circular inference.
   */
  @Override
  public AbstractType type()
  {
    // type() will only be called when we really need the type, so we can report
    // an error in case there is one pending.
    var hasPendingError = _pendingError != null;
    reportPendingError();
    var result = typeForInferencing();
    if (result == null)
      {
        if (hasPendingError || _actuals.stream().anyMatch(a -> a.type() == Types.t_ERROR))
          {
            result = Types.t_ERROR;
          }
        else if (calledFeatureKnown() && calledFeature().state().atLeast(State.RESOLVED_TYPES))
          {
            AstErrors.failedToInferActualGeneric(_pos, _calledFeature, missingGenerics());
            result = Types.t_ERROR;
          }
        else
          {
            result = Types.t_FORWARD_CYCLIC;
          }
        setToErrorState0();
      }
    return result;
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
    v.actionBefore(this);
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
    var result = v.action(this);
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
            var na = a.visit(v, outer);
            if (CHECKS) check
              (na != Universe.instance);
            i.set(na);
          }
      }
  }


  /**
   * Helper function called during resolveTypes to resolve syntactic sugar that
   * allows directly calling a function returned by a call.
   *
   * If this is a normal call (e.g. {@code f.g}) whose result is a function type,
   * ({@code (i32,i32) -> f64}), and if {@code g} does not take any arguments, syntactic
   * sugar allows an implicit call to {@code Function.call}, i.e., {@code f.g 3 5} is
   * a short form of {@code f.g.call 3 5}.
   *
   * NYI: we could also permit {@code (f.g x y) 3 5} as a short form for {@code (f.g x
   * y).call 3 5{@code  in case }g} takes arguments.  But this might be too confusing
   * and it would require a change in the grammar.
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this assignment is used
   *
   * @return result this in case this was not an immediate call, otherwise the
   * resulting call to Function/Routine.call.
   */
  protected Call resolveImmediateFunctionCall(Resolution res, Context context)
  {
    return this;
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
   *
   * @param context the source code context where this Call is used
   */
  private void resolveFormalArg(Resolution res, int argnum, AbstractFeature frml, Context context)
  {
    int cnt = 1;
    var frmlT = frml.resultTypeIfPresentUrgent(res, true);

    var declF = _calledFeature.outer();
    var heir = _target.type();
    if (!heir.isGenericArgument() && declF != heir.feature())
      {
        var a = _calledFeature.handDown(res, new AbstractType[] { frmlT }, heir.feature());
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
                                           : heir.selfOrConstraint(res, context).generics()); // see for example #1919
                addToResolvedFormalArgumentTypes(res, argnum + i, frmlTs.toArray(new AbstractType[frmlTs.size()]), frml);
                i   = i   + frmlTs.size() - 1;
                cnt = cnt + frmlTs.size() - 1;
              }
            else
              {
                _resolvedFormalArgumentTypes[argnum + i] = actualArgType(res, frmlT, frml, context);
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
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
   */
  private void resolveFormalArgumentTypes(Resolution res, Context context)
  {
    if (_resolvedFormalArgumentTypes == null)
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
              (() -> resolveFormalArg(res, argnum, frml, context));
            count++;
          }
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
   * @param context the source code context where this Call is used
   *
   * @param urgent true if we should produce an error in case the formal result
   * type of the called feature is not available, false if this is still
   * acceptable and the result type can be set later.
   */
  protected AbstractType getActualResultType(Resolution res, Context context, boolean urgent)
  {
    AbstractType result;
    if (isTailRecursive(context.outerFeature()) || _recursiveResolveType)
      {
        result = Types.resolved.t_void; // a recursive call will not return and execute further
      }
    else if (!genericSizesMatch())
      {
        result = Types.t_ERROR;
      }
    else
      {
        _recursiveResolveType = true;
        result = _calledFeature.resultTypeIfPresentUrgent(res, urgent);
        _recursiveResolveType = false;

        if (result == Types.t_FORWARD_CYCLIC)
          {
            // Handling of cyclic type inference. It might be
            // better if this was done in `Feature.resultType`, but
            // there we do not have access to Call.this.pos(), so
            // we do it here.
            AstErrors.forwardTypeInference(pos(), _calledFeature, _calledFeature.pos());
            result = Types.t_ERROR;
            setToErrorState();
          }

        result = result == null
          ? result
          : adjustResultType(res, context, result);
      }
    return result;
  }


  /**
   * Adjust the _raw_ result type of the
   * called feature for the call.
   *
   * 1) resolve select
   * 2) apply type parameters of the target of the call
   * 3) apply type parameters of the called feature
   * 4) adjust this-types for the target of the call
   * 5) handle special cases: calling a type parameters, type_as_value, outer refs, constructors
   * 6) replace type parameters of cotype origin: e.g. equatable_sequence.T -> equatable_sequence.type.T
   *
   * @param rt the raw result type
   *
   * @return The actual result type of the call
   */
  private AbstractType adjustResultType(Resolution res, Context context, AbstractType rt)
  {
    var tt = targetIsTypeParameter() && rt.isThisTypeInCotype()
      ? // a call B.f for a type parameter target B. resultType() is the
      // constraint of B, so we create the corresponding type feature's
      // selfType:
      // NYI: CLEANUP: remove this special handling!
      _target.type().feature().selfType()
      : targetType(res, context);

    // NYI: CLEANUP: There is some overlap between Call.adjustResultType,
    // Call.actualArgType and AbstractType.genericsAssignable, might be nice to
    // consolidate this (i.e., bring the calls to applyTypePars / adjustThisType
    // / etc. in the same order and move them to a dedicated function).
    var t0 = tt == Types.t_ERROR ? tt : resolveSelect(rt, tt);
    var t1 = t0 == Types.t_ERROR ? t0 : t0.applyTypePars(tt);
    var t2 = t1 == Types.t_ERROR ? t1 : t1.applyTypePars(_calledFeature, _generics);
    var t3 = t2 == Types.t_ERROR ? t2 : tt.isGenericArgument() ? t2 : t2.resolve(res, tt.feature().context());
    var t4 = t3 == Types.t_ERROR ? t3 : adjustThisTypeForTarget(t3, false, calledFeature(), context);
    var t5 = t4 == Types.t_ERROR ? t4 : resolveForCalledFeature(res, t4, tt, context);
    var t6 = t5 == Types.t_ERROR ? t5 : calledFeature().isCotype() ? t5 : t5.replace_type_parameters_of_cotype_origin(context.outerFeature());
    return t6 == Types.t_UNDEFINED
      ? null
      : t6;
  }


  /**
   * Check if the generics of the called feature
   * and the calls generics may match in size.
   * Raise an error if they don't.
   */
  private boolean genericSizesMatch()
  {
    return _calledFeature
      .generics()
      .errorIfSizeDoesNotMatch(_generics,
                               pos(),
                               FuzionConstants.OPERATION_CALL,
                               "Called feature: "+_calledFeature.qualifiedName()+"\n");
  }


  /**
   * Helper for resolveType to process _select, i.e., check that _select is &lt; 0
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
    if (t.isOpenGeneric())
      {
        if (_select < 0)
          {
            AstErrors.cannotAccessValueOfOpenGeneric(pos(), _calledFeature, t);
            t = Types.t_ERROR;
          }
        else if(tt.generics().stream().anyMatch(g -> g.isOpenGeneric()))
          {
            var types = tt.generics().stream().filter(g -> !g.isOpenGeneric()).collect(List.collector());
            AstErrors.selectorRange(pos(), types.size(), _calledFeature, _name, _select, types);
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
                if (t.isOpenGeneric())
                  {
                    t = Types.t_ERROR;
                    AstErrors.cannotAccessValueOfOpenGeneric(pos(), _calledFeature, t);
                  }
              }
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
   * @param arg true if {@code t} is the type of an argument, false if {@code t} is the result type
   *
   * @param calledOrArg the declared argument (if arg == true) or the called feature (otherwise).
   *
   * @param context the source code context where this Call is used
   *
   * @return a type derived from t where {@code this.type} is replaced by actual types
   * from the call's target where this is possible.
   */
  private AbstractType adjustThisTypeForTarget(AbstractType t, boolean arg, AbstractFeature calledOrArg, Context context)
  {
    /**
     * For a call {@code T.f} on a type parameter whose result type contains
     * {@code this.type}, make sure we replace the implicit type parameter to
     * {@code this.type}.
     *
     * example:
     *
     *   equatable is
     *
     *     type.equality(a, b equatable.this.type) bool is abstract
     *
     *   equals(T type : equatable, x, y T) => T.equality x y
     *
     * For the call {@code T.equality x y}, we must replace the formal argument type
     * for {@code a} (and {@code b}) by {@code T}.
     */
    var target = target();
    var tt = target().type();
    if (target instanceof Call tc &&
        tc.calledFeature().isTypeParameter() &&
        !tt.isGenericArgument())
      {
        t = t.replace_type_parameter_used_for_this_type_in_cotype
          (tt.feature(),
           tc);
      }
    if (!calledFeature().isOuterRef())
      {
        var t0 = t;
        var declF = calledFeature().outer();
        if (!tt.isGenericArgument() && declF != tt.feature())
          {
            var heir = tt.feature();
            t = t.replace_inherited_this_type(declF, heir,
                                              (from,to) -> AstErrors.illegalOuterRefTypeInCall(this, arg, calledOrArg, t0, from, to));
          }
        var inner = ResolvedNormalType.newType(calledFeature().selfType(),
                                               _target.type());
        t = t.replace_this_type_by_actual_outer(inner,
                                                (from,to) -> AstErrors.illegalOuterRefTypeInCall(this, arg, calledOrArg, t0, from, to),
                                                context);
      }
    return t;
  }


  /**
   * Helper function for resolveType to adjust a result type depending on the
   * kind of feature that is called.
   *
   * In particular, this contains special handling for calling type parameters,
   * for type_as_value, for outer refs and for constructors.
   *
   * @param res the resolution instance.
   *
   * @param t the result type of the called feature, adjusts for select, this type, etc.
   *
   * @param tt target type or constraint.
   *
   * @param context the source code context where this Call is used
   */
  private AbstractType resolveForCalledFeature(Resolution res, AbstractType t, AbstractType tt, Context context)
  {
    if (_calledFeature.isTypeParameter())
      {
        if (!t.isGenericArgument())  // See AstErrors.constraintMustNotBeGenericArgument
          {
            // a type parameter's result type is the constraint's type as a type
            // feature with actual type parameters as given to the constraint.
            var tf = t.feature().cotype(res);
            var tg = new List<AbstractType>(t); // the constraint type itself
            tg.addAll(t.generics());            // followed by the generics
            t = tf.selfType().applyTypePars(tf, tg);
          }
      }
    else if (_calledFeature == Types.resolved.f_type_as_value)
      {
        t = _generics.get(0);
        // we are using `.this.type` inside a type feature, see #2295
        if (t.isThisTypeInCotype())
          {
            t = t.genericArgument().feature().thisType();
          }
        else if (!t.isGenericArgument())
          {
            t = t.typeType(res);
          }
        t = t.resolve(res, tt.feature().context());
      }
    else if (_calledFeature.isOuterRef())
      {
        var o = t.feature().outer();
        t = o == null || o.isUniverse() ? t : ResolvedNormalType.newType(t, o.thisType());
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
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
   */
  private Expr resolveTypeForNextActual(AbstractType formalTypeForPropagation,
                                        ListIterator<Expr> aargs,
                                        Resolution res,
                                        Context context)
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
                    actual = actual.propagateExpectedType(res, context, t);
                  }
              }
          }
      }
    if ((formalTypeForPropagation != null) || !actualWantsPropagation)
      {
        actual = res.resolveType(actual, context);
        if (CHECKS) check
          (actual != null,
           actual != Universe.instance);
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
   * @param context the source code context where this Call is used
   */
  private void inferGenericsFromArgs(Resolution res, Context context)
  {
    int sz = _calledFeature.generics().list.size();
    boolean[] conflict = new boolean[sz]; // The generics that had conflicting types
    var foundAt  = new List<List<Pair<SourcePosition, AbstractType>>>(); // generics that were found will get the type and pos found stored here, null while not found
    for (var i = 0; i<sz ; i++)
      {
        foundAt.add(null);
      }

    _generics = actualTypeParameters();
    var va = _calledFeature.valueArguments();
    var checked = new boolean[va.size()];
    int last, next = 0;
    do
      {
        last = next;
        inferGenericsFromArgs(res, context, checked, conflict, foundAt);
        next = 0;
        for (var b : foundAt)
          {
            next = next + (b != null ? 1 : 0);
          }
      }
    while (last < next);


    List<Generic> missing = missingGenerics();

    if (!missing.isEmpty())
      {
        triggerErrorsForActuals();
      }

    var rt = _calledFeature.resultTypeIfPresentUrgent(res, false);

    if (mustReportMissingImmediately(rt))
      {
        reportConflicts(conflict, foundAt);
        reportMissingInferred(missing);
      }
  }


  /**
   * Do we want to report missing generics now
   * or do we wait for result type propagation
   * which may allow inference later.
   */
  private boolean mustReportMissingImmediately(AbstractType rt)
  {
    return (rt == null ||
        !rt.isGenericArgument() ||
         rt.genericArgument().feature().outer() != _calledFeature.outer()) ||
         _actuals.stream().anyMatch(a -> a.typeForInferencing() == Types.t_ERROR);
  }


  /**
   * Trigger error reporting of actuals
   * by calling {@code type} for each actual.
   */
  private void triggerErrorsForActuals()
  {
    // we failed inferring all type parameters, so report errors
    for (var a : _actuals)
      {
        if (a instanceof Call)
          {
            var ignore = a.type();
          }
      }
  }


  /**
   * report any conflicts of inference
   *
   * @param conflict
   * @param foundAt
   */
  private void reportConflicts(boolean[] conflict, List<List<Pair<SourcePosition, AbstractType>>> foundAt)
  {
    // replace any missing type parameters or conflicting ones with t_ERROR,
    // report errors for conflicts
    for (Generic g : _calledFeature.generics().list)
      {
        int i = g.index();
        if (!g.isOpen() && (_generics.size() <= i || _generics.get(i) == Types.t_UNDEFINED) || conflict[i])
          {
            if (CHECKS) check
              (Errors.any() || i < _generics.size());
            if (conflict[i])
              {
                AstErrors.incompatibleTypesDuringTypeInference(pos(), g, foundAt.get(i));
                setToErrorState();
              }
            if (i < _generics.size())
              {
                _generics = _generics.setOrClone(i, Types.t_ERROR);
              }
          }
      }
  }


  /**
   * @return list of generic arguments
   *         which could not be inferred
   */
  private List<Generic> missingGenerics()
  {
    List<Generic> missing = new List<Generic>();
    for (Generic g : _calledFeature.generics().list)
      {
        int i = g.index();
        if (!g.isOpen() && _generics.get(i) == Types.t_UNDEFINED)
          {
            missing.add(g);
          }
      }
    return missing;
  }


  /**
   * report that generics in missing could not be inferred.
   *
   * @param missing the list of generics that could not be inferred
   */
  private void reportMissingInferred(List<Generic> missing)
  {
    // report missing inferred types only if there were no errors trying to find
    // the types of the actuals:
    if (!missing.isEmpty() &&
        !_calledFeature.isCotype() &&
        _calledFeature != Types.f_ERROR &&
        (!Errors.any() ||
         _actuals.stream().allMatch(x -> x.type() != Types.t_ERROR)))
      {
        AstErrors.failedToInferActualGeneric(pos(), _calledFeature, missing);
        setToErrorState();
      }
  }


  /**
   * Perform phase 1. of the calls to @see Expr.propagateExpectedTypeForPartial:
   * while the actual type parameters may not be known yet for this call, try to
   * create partial application lambdas for the actual arguments where needed.
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
   */
  void propagateForPartial(Resolution res, Context context)
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
            var t = frml.resultTypeIfPresent(res);
            if (t != null && t.isFunctionTypeExcludingLazy())
              {
                var a = resultExpression(actual);
                Expr l = a.propagateExpectedTypeForPartial(res, context, t);
                if (l != a)
                  {
                    if (CHECKS) check
                      (l != Universe.instance);
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
   * @param context the source code context where this Call is used
   *
   * @param checked boolean array for all cf.valuedArguments() that have been
   * checked already.
   *
   * @param conflict set of generics that caused conflicts
   *
   * @param foundAt the position of the expressions from which actual generics
   * were taken.
   */
  private void inferGenericsFromArgs(Resolution res,
                                     Context context,
                                     boolean[] checked,
                                     boolean[] conflict,
                                     List<List<Pair<SourcePosition, AbstractType>>> foundAt)
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
                var t = frml.resultTypeIfPresent(res);
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
                            var actual = resolveTypeForNextActual(Types.t_UNDEFINED, aargs, res, context);
                            var actualType = typeFromActual(actual, context);
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
                    var actual = resolveTypeForNextActual(pass == 0 ? null : t, aargs, res, context);
                    /*
                      without this if, type inference in this example would not work:
                      ```
                      feat(T type, u i64, str T) is
                      feat 0 "hello"
                      ```
                    */
                    if (t.dependsOnGenerics())
                      {
                        var actualType = typeFromActual(actual, context);
                        if (actualType != null)
                          {
                            /**
                             * infer via constraint of type parameter:
                             *
                             *     a(T type, S type : array T, s S) is
                             *     _ := a [32]
                             */
                            if (t.isGenericArgument())
                              {
                                var tp = t.genericArgument().typeParameter();
                                res.resolveTypes(tp);
                                inferGeneric(res, context, tp.resultType(), actualType, actual.pos(), conflict, foundAt, count-1);
                              }
                            inferGeneric(res, context, t, actualType, actual.pos(), conflict, foundAt, count-1);
                            checked[vai] = true;
                          }
                        else if (resultExpression(actual) instanceof AbstractLambda al)
                          {
                            checked[vai] = inferGenericLambdaResult(res, context, t, frml, al, actual.pos(), conflict, foundAt);
                          }
                      }
                    count++;
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
   */
  private void inferFormalArgTypesFromActualArgs()
  {
    for (var frml : _calledFeature.valueArguments())
      {
        if (frml instanceof Feature f)
          {
            f.impl().addInitialCall(this);
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
   * argument in the context of {@code outer}.
   *
   * In case {@code actual}'s type depends on a type parameter g of a feature f and
   * the context is the corresponding type feature ft, then g will be replaced
   * by the corresponding type parameter of ft.
   *
   * @param actual an actual argument or null if not known
   *
   * @param context the source code context where this Call is used
   *
   * @return the type of actual as seen within context, or null if not known.
   */
  AbstractType typeFromActual(Expr actual,
                              Context context)
  {
    var actualType = actual == null ? null : actual.typeForInferencing();
    if (actualType != null)
      {
        actualType = actualType.replace_type_parameters_of_cotype_origin(context.outerFeature());
        if (!actualType.isGenericArgument() && actualType.feature().isCotype())
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
   * @param context the source code context where this Call is used
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
                            Context context,
                            AbstractType formalType,
                            AbstractType actualType,
                            SourcePosition pos,
                            boolean[] conflict,
                            List<List<Pair<SourcePosition, AbstractType>>> foundAt)
  {
    inferGeneric(res, context, formalType, actualType, pos, conflict, foundAt, -1);
  }


  /**
   * Perform type inference for generics used in formalType that are instantiated by actualType.
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
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
   *
   * @param actualArgIndex the index of the actual or -1 if actualType not directly from actual
   */
  private void inferGeneric(Resolution res,
                            Context context,
                            AbstractType formalType,
                            AbstractType actualType,
                            SourcePosition pos,
                            boolean[] conflict,
                            List<List<Pair<SourcePosition, AbstractType>>> foundAt,
                            int actualArgIndex)
  {
    if (PRECONDITIONS) require
      (actualType.compareTo(actualType.replace_type_parameters_of_cotype_origin(context.outerFeature())) == 0);

    if (formalType.isLazyType() && !actualType.isLazyType())
      {
        inferGeneric(res, context, formalType.generics().get(0), actualType, pos, conflict, foundAt);
      }
    else if (formalType.isGenericArgument())
      {
        var g = formalType.genericArgument();
        if (g.feature() == _calledFeature)
          { // we found a use of a generic type, so record it:
            var i = g.index();
            if (!conflict[i])
              {
                var gt = _generics.get(i);
                var nt = gt == Types.t_UNDEFINED ? actualType
                                                 : gt.union(actualType, context);
                if (nt == Types.t_ERROR)
                  {
                    conflict[i] = true;
                  }
                _generics = _generics.setOrClone(i, nt == Types.t_ERROR ? Types.t_UNDEFINED : nt);
                addPair(foundAt, i, pos, actualType);
              }
          }
      }
    else
      {
        var fft = formalType.feature();
        res.resolveTypes(fft);
        var aft = actualType.isGenericArgument() ? null : actualType.feature();
        if (fft == aft)
          {
            for (int i=0; i < formalType.generics().size(); i++)
              {
                if (i < actualType.generics().size())
                  {
                    inferGeneric(res,
                                 context,
                                 formalType.generics().get(i),
                                 actualType.generics().get(i),
                                 pos, conflict, foundAt);
                  }
              }
          }
        else if (formalType.isChoice())
          {
            /**
             * example:
             *
             *   tree(T type) : choice nil T (Branch T) is
             *
             *   Branch(T type, left, right tree T) ref is
             *
             *   tree := (Branch
             *             (Branch
             *               (Branch $"A" "B")
             *               (Branch $"C" "D"))
             *             (Branch
             *               (Branch $"E" "F")
             *               (Branch $"G" nil)))
             */

            var directlyAssignable = formalType
              .choiceGenerics(context)
              .stream()
              .filter(x -> !x.dependsOnGenerics())
              .anyMatch(x -> x.isAssignableFrom(actualType, context));

            if (!directlyAssignable)
              {
                // if actualType is `Branch String`
                // we only consider `Branch T` and not `T`.
                var matchingFeature = formalType
                  .choiceGenerics(context)
                  .stream()
                  .filter(x -> x.dependsOnGenerics()
                           && !x.isGenericArgument()
                           && !actualType.isGenericArgument()
                           && x.feature() == actualType.feature())
                  .toList();
                for (var ct : matchingFeature)
                  {
                    inferGeneric(res, context, ct, actualType, pos, conflict, foundAt);
                  }
                if (matchingFeature.size() == 0)
                  {
                    for (var ct : formalType.choiceGenerics(context))
                      {
                        inferGeneric(res, context, ct, actualType, pos, conflict, foundAt);
                      }
                  }
              }
          }
        else if (actualArgIndex != -1 && aft != null && !aft.inheritsFrom(fft) && !fft.generics().list.isEmpty())
          {
            AstErrors.incompatibleArgumentTypeInCall(_calledFeature, actualArgIndex, formalType, _actuals.get(actualArgIndex), Context.NONE);
            setToErrorState();
          }
        else if (aft != null)
          {
            for (var p: aft.inherits())
              {
                if (p instanceof Call pc)
                  {
                    pc.resolveTypes(res, aft.context());
                  }
                var pt = p.type();
                if (pt != Types.t_ERROR)
                  {
                    var apt = actualType.actualType(pt, context);
                    if (apt.feature().inheritsFrom(formalType.feature()))
                      {
                        inferGeneric(res, context, formalType, apt, pos, conflict, foundAt);
                      }
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
   * @param context the source code context where this Call is used
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
                                           Context context,
                                           AbstractType formalType,
                                           AbstractFeature frml,
                                           AbstractLambda al,
                                           SourcePosition pos,
                                           boolean[] conflict,
                                           List<List<Pair<SourcePosition, AbstractType>>> foundAt)
  {
    var result = new boolean[] { false };
    if (formalType.isFunctionTypeExcludingLazy() || formalType.isLazyType())
      {
        var at = actualArgType(res, formalType, frml, context);
        if (!at.containsUndefined(true))
          {
            var lambdaResultType = formalType.generics().get(0);
            inferGenericLambdaResult(res, context, al, pos, conflict, foundAt, result, lambdaResultType, new List<>(lambdaResultType), at);
          }
      }
    return result[0];
  }


  /**
   * Perform type inference for result type of lambda
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
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
  private void inferGenericLambdaResult(Resolution res, Context context, AbstractLambda al, SourcePosition pos, boolean[] conflict,
    List<List<Pair<SourcePosition, AbstractType>>> foundAt, boolean[] result, AbstractType lambdaResultType, List<AbstractType> generics,
    AbstractType argumentType)
  {
    generics
      .stream()
      .forEach(g -> {
        if (!g.isGenericArgument())
          {
            inferGenericLambdaResult(res, context, al, pos, conflict, foundAt, result, lambdaResultType, g.generics(), argumentType);
          }
        else
          {
            var rg = g.genericArgument();
            var ri = rg.index();
            if (rg.feature() == _calledFeature && foundAt.get(ri) == null)
              {
                var rt = al.inferLambdaResultType(res, context, argumentType);
                if (rt != null)
                  {
                      inferGeneric(res, context, lambdaResultType, rt, pos, conflict, foundAt);
                      result[0] = true;
                  }
              }
          }
      });
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
    if (e instanceof Match m)
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
    return _calledFeature != null && (_generics == NO_GENERICS || _generics.stream().anyMatch(g -> g.containsUndefined(false))) && _calledFeature.generics() != FormalGenerics.NONE;
  }


  /**
   * Field used to detect and avoid repeated calls to resolveTypes for the same
   * context.  resolveTypes may be called repeatedly when types are determined
   * on demand for type inference for type parameters in a call. This field will
   * record that resolveTypes was called for a given context.  This is used to
   * not perform resolveTypes repeatedly.
   *
   * However, moving an expression into a lambda or a lazy value will change its
   * context and resolve will have to be repeated.
   */
  private Context _resolvedFor;


  /**
   * Has this call been resolved and if so, for which context?
   *
   * @return the context this has been resolved for or null if this has not been
   * resolved yet.  Note that the result may change due to repeated resolution
   * when this is moved to a different feature as a result of partial
   * application, lazy evaluation or is part of a lambda expression.
   */
  public Context resolvedFor()
  {
    return _resolvedFor;
  }


  /**
   * try resolving this call as dot-type-call
   *
   * On success _calledFeature and _target will be set.
   * No errors are raised if this is not successful
   * since then we are probably dealing with a normal call.
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
   */
  void tryResolveTypeCall(Resolution res, Context context)
  {
    var outer = context.outerFeature();
    if (_calledFeature == null && _target != null && outer.state().atLeast(State.RESOLVED_INHERITANCE))
    {
      AbstractType tt = _target.asParsedType();
      if (tt != null && tt instanceof UnresolvedType ut)
        {
          // check if this might be a
          // left hand side of dot-type-call
          tt = ut.resolve(res, context, true);
          tt = tt != null && tt != Types.t_ERROR ? tt.selfOrConstraint(res, context) : tt;
        }
      if (tt != null && tt != Types.t_ERROR)
        {
          var tf = tt.feature();
          var ttf = tf.isUniverse() ? tf : tf.cotype(res);
          res.resolveDeclarations(tf);
          var fo = findOnTarget(res, tf, false).v1();
          var tfo = findOnTarget(res, ttf, false).v1();
          var f = tfo == null ? null : tfo._feature;
          if (f != null
              && f.outer() != null
              /* omitting dot-type does not work when calling
               the inherited methods of `Type`. Otherwise we
               would always have an ambiguity when calling `as_string` */
              && f.outer().isCotype())
            {
              if (fo != null)
                {
                  AstErrors.ambiguousCall(this, fo._feature, tfo._feature);
                  setToErrorState();
                }
              else
                {
                  // we found a feature that fits a dot-type-call.
                  _calledFeature = f;
                  _pendingError = null;
                  _resolvedFormalArgumentTypes = null;
                  _target = new DotType(_pos, _target).resolveTypes(res, context);
                }
            }
          if (_calledFeature != null &&
              _generics.isEmpty() &&
              _actuals.size() != f.valueArguments().size() &&
              !f.hasOpenGenericsArgList(res))
            {
              splitOffTypeArgs(res, f, outer);
            }
        }
    }
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
   */
  Call resolveTypes(Resolution res, Context context)
  {
    if (_resolvedFor == context)
      {
        return this;
      }
    _resolvedFor = context;

    return loadCalledFeatureUnlessTargetVoid(res, context)
      ? resolveTypes0(res, context)
      // target of this call results in `void`, so we replace this call by the
      // target. However, we have to return a `Call` and `_target` is
      // `Expr`. Solution: we wrap `_target` into a call `universe.id void
      // _target`.
      : idVoidCall(res, context);
  }


  /**
   * resolve types of this call for non void target.
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
   */
  private Call resolveTypes0(Resolution res, Context context)
  {
    // Check that we either know _calledFeature, or there is an error pending
    // either for this Call, or we have a problem with the target:
    if (PRECONDITIONS) require
      (Errors.any() || res._options.isLanguageServer() || _calledFeature != null || _pendingError != null || targetTypeUndefined());

    if (_calledFeature == Types.f_ERROR)
      {
        _type = Types.t_ERROR;
      }
    else if (_calledFeature != null)
      {
        resolveGenerics(res, context);
        propagateForPartial(res, context);
        if (needsToInferTypeParametersFromArgs())
          {
            inferGenericsFromArgs(res, context);
            for (var r : _whenInferredTypeParameters)
              {
                r.run();
              }
          }
        inferFormalArgTypesFromActualArgs();
        setActualResultType(res, context);
        resolveFormalArgumentTypes(res, context);
      }
    resolveTypesOfActuals(res, context);

    return isErroneous(res)
      ? resolveTypesErrorResult()
      : resolveTypesSuccessResult(res, context);
  }


  /**
   * create a resolved Call {@code id void}
   */
  private Call idVoidCall(Resolution res, Context context)
  {
    return new Call(pos(),
                    Universe.instance,
                    new List<>(Types.resolved.t_void),
                    new List<>(_target),
                    Types.resolved.f_id)
             .resolveTypes(res, context);
  }


  /**
   * Try to resolve an immediate function call
   * or return the call itself.
   */
  private Call resolveTypesSuccessResult(Resolution res, Context context)
  {
    Call result = this;
    // NYI: Separate pass? This currently does not work if type was inferred
    if (_type != null && _type != Types.t_ERROR)
      {
        // Convert a call "f.g a b" into "f.g.call a b" in case f.g takes no
        // arguments and returns a Function or Routine
        result = resolveImmediateFunctionCall(res, context);
      }

    if (POSTCONDITIONS) ensure
      (targetTypeUndefined() || _pendingError != null || Errors.any() || result.typeForInferencing() != Types.t_ERROR || result == Call.ERROR);

    return  result;
  }


  /**
   * Is this call in an erroneous state?
   */
  private boolean isErroneous(Resolution res)
  {
    return !res._options.isLanguageServer() &&
      (targetTypeUndefined() || _pendingError == null && typeForInferencing() == Types.t_ERROR);
  }


  /**
   * Report errors of the target and return Call.ERROR
   */
  private Call resolveTypesErrorResult()
  {
    if (_target instanceof Call tc)
      {
        tc.reportPendingError();
      }
    else if (_target != null)
      {
        var ignore = _target.type();
      }
    return Call.ERROR; // short circuit this call
  }


  /**
   * Resolve the generics of this call.
   */
  private void resolveGenerics(Resolution res, Context context)
  {
    _generics = FormalGenerics.resolve(res, _generics, context.outerFeature());
    _generics = _generics.map(g -> g.resolve(res, _calledFeature.outer().context()));
  }


  /**
   * set the actual result type of this call
   */
  private void setActualResultType(Resolution res, Context context)
  {
    var t = getActualResultType(res, context, false);

    if (CHECKS) check
      (_type == null || t.compareTo(_type) == 0,
       Errors.any() || t != Types.t_ERROR);

    _type = t;

    if (_type == null || isTailRecursive(context.outerFeature()))
      {
        _calledFeature.whenResolvedTypes(() ->
          {
            var t2 = getActualResultType(res, context, true);
            if (CHECKS) check
              (_type == null || t2.compareTo(_type) == 0,
              Errors.any() || t2 != Types.t_ERROR);
            _type = t2;
          });
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
   * @param context the source code context where this Expr is used
   */
  void propagateExpectedType(Resolution res, Context context)
  {
    applyToActualsAndFormalTypes((actual, formalType) -> actual.propagateExpectedType(res, context, formalType));

    if (_target != null)
      {
        // This informs target that it is used which may
        // - e.g. for if- and match-expressions -
        // lead to these expressions adding a result field via
        // `addFieldForResult`.
        // This result field is then the target of the call.
        //
        // NYI: CLEANUP: there should be another mechanism, for
        // adding missing result fields instead of misusing
        // `propagateExpectedType`.
        //
        var t = _target.typeForInferencing();
        if (t != null)
          {
            _target = _target.propagateExpectedType(res, context, t);
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
   * @param context the source code context where this Expr is used
   *
   * @param t the expected type.
   *
   * @return either this or a new Expr that replaces this and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the expression that reads the field.
   */
  Expr propagateExpectedType(Resolution res, Context context, AbstractType t)
  {
    Expr r = this;
    if (t.isFunctionTypeExcludingLazy()         &&
        !_wasImplicitImmediateCall &&
        _type != Types.t_ERROR     &&
        (_type == null || !_type.isFunctionType()))
      {
        r = propagateExpectedTypeForPartial(res, context, t);
        if (r != this)
          {
            var r2 = r.propagateExpectedType(res, context, t);
            if (CHECKS) check
              (r == r2);
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
   * @param context the source code context where this Call is used
   */
  void wrapActualsInLazy(Resolution res, Context context)
  {
    applyToActualsAndFormalTypes((actual, formalType) -> actual.wrapInLazy(res, context, formalType));
  }


  /**
   * During type inference: automatically unwrap actuals.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Call is used
   */
  void unwrapActuals(Resolution res, Context context)
  {
    applyToActualsAndFormalTypes((actual, formalType) -> actual.unwrap(res, context, formalType));
  }


  /**
   * Helper for propagateExpectedType and wrapActualsInLazy to apply {@code f} to all
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
                  (a != null,
                   a != Universe.instance);
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
   * @param context the source code context where this Call is used
   */
  void boxArgs(Context context)
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
                    var a = actl.boxAndTag(rft, context);
                    if (CHECKS) check
                      (a != null,
                       a != Universe.instance);
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
   * @param context the source code context where this Call is used
   */
  void checkTypes(Resolution res, Context context)
  {
    reportPendingError();

    if (CHECKS) check
      (res._options.isLanguageServer() || Errors.any() || _type != null);

    if (_calledFeature != null &&
        context.outerFeature() != Types.resolved.f_effect_static_finally &&
        (_calledFeature == Types.resolved.f_effect_finally ||
         _calledFeature.redefinesFull().contains(Types.resolved.f_effect_finally))
       )
      {
        AstErrors.mustNotCallEffectFinally(this);
      }

    if (_type != null && _type != Types.t_ERROR)
      {
        var o = _type;
        while (o != null && !o.isGenericArgument())
          {
            o = o.outer();
            if (o != null && o.isRef() == YesNo.yes && !o.feature().isRef())
              {
                AstErrors.illegalCallResultType(this, _type, o);
                o = null;
              }
          }

        int fsz = _resolvedFormalArgumentTypes.length;
        if (_actuals.size() !=  fsz)
          {
            AstErrors.wrongNumberOfActualArguments(this);
            setToErrorState();
          }
        else
          {
            int count = 0;
            for (Expr actl : _actuals)
              {
                var frmlT = _resolvedFormalArgumentTypes[count];
                if (CHECKS) check
                  (Errors.any() || (actl != Call.ERROR && actl != Call.ERROR));
                if (frmlT != Types.t_ERROR && actl != Call.ERROR && actl != Call.ERROR && !frmlT.isAssignableFromWithoutTagging(actl.type(), context))
                  {
                    AstErrors.incompatibleArgumentTypeInCall(_calledFeature, count, frmlT, actl, context);
                  }

                if (CHECKS) check
                  (Errors.any() || actl.type().isVoid() || actl.needsBoxing(frmlT, context) == null || actl.isBoxed());

                count++;
              }
          }
        if (_calledFeature.isChoice())
          {
            boolean ok = false;
            var outer = context.outerFeature();
            if (outer.isChoice())
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
        if (_calledFeature.isOpenTypeParameter())
          {
            AstErrors.mustNotCallOpenTypeParameter(this);
          }

        if ( !(Errors.any() && _actuals.stream().anyMatch(a->a.typeForInferencing() == Types.t_ERROR)) )
          {
            // Check that generics match formal generic constraints
            AbstractType.checkActualTypePars(context, _calledFeature, _generics, _unresolvedGenerics, this);
          }
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
    return !(cc instanceof BoolConst bc)
      ? Match.createIf(pos(), cc, block, elseBlock, false)
      : bc.getCompileTimeConstBool()
      ? block
      : elseBlock;
  }


  /**
   * Syntactic sugar resolution: This does the following:<p>
   *
   *  - convert boolean operations {@code &&}, {@code ||} and {@code :} into if-expressions
   *  - convert repeated boolean operations ! into identity   // NYI
   *  - perform constant propagation for basic algebraic ops  // NYI
   *  - simplify boolean algebra via K-Map and/or Quine–McCluskey // NYI
   *  - replace calls to intrinsics that return compile time constants
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @return a new Expr to replace this call or this if it remains unchanged.
   */
  Expr resolveSyntacticSugar1(Resolution res, Context context)
  {
    Expr result = this;
    // must not be inheritance call since we do not want `: i32 2` turned into a numeric literal.
    // also we can not inherit from none constructor features like and/or etc.
    if (_pendingError == null && !isInheritanceCall())
      {
        // convert
        //   a && b into if a then b     else false
        //   a || b into if a then true  else b
        //   a: b   into if a then b     else true
        //   !a     into if a then false else true
        var cf = _calledFeature;
        // need to do a propagateExpectedType since this might add a result field
        // example where this results in an issue: `_ := [false: true]`
        if      (cf == Types.resolved.f_bool_AND    ) { result = newIf(_target, _actuals.get(0), BoolConst.FALSE).propagateExpectedType(res, context, Types.resolved.t_bool); }
        else if (cf == Types.resolved.f_bool_OR     ) { result = newIf(_target, BoolConst.TRUE , _actuals.get(0)).propagateExpectedType(res, context, Types.resolved.t_bool); }
        else if (cf == Types.resolved.f_bool_IMPLIES) { result = newIf(_target, _actuals.get(0), BoolConst.TRUE ).propagateExpectedType(res, context, Types.resolved.t_bool); }
        else if (cf == Types.resolved.f_bool_NOT    ) { result = newIf(_target, BoolConst.FALSE, BoolConst.TRUE ).propagateExpectedType(res, context, Types.resolved.t_bool); }
        else if (cf == Types.resolved.f_bool_TERNARY)
          {
            result = newIf(_target, _actuals.get(0), _actuals.get(1));
            if (!_generics.get(0).containsUndefined(false))
              {
                result = result.propagateExpectedType(res, context, _generics.get(0));
              }
          }

        // replace e.g. i16 7 by just the NumLiteral 7. This is necessary for syntaxSugar2 of InlineArray to work correctly.
        else if (cf == Types.resolved.t_i8 .feature()) { result = this._actuals.get(0).propagateExpectedType(res, context, Types.resolved.t_i8 ); }
        else if (cf == Types.resolved.t_i16.feature()) { result = this._actuals.get(0).propagateExpectedType(res, context, Types.resolved.t_i16); }
        else if (cf == Types.resolved.t_i32.feature()) { result = this._actuals.get(0).propagateExpectedType(res, context, Types.resolved.t_i32); }
        else if (cf == Types.resolved.t_i64.feature()) { result = this._actuals.get(0).propagateExpectedType(res, context, Types.resolved.t_i64); }
        else if (cf == Types.resolved.t_u8 .feature()) { result = this._actuals.get(0).propagateExpectedType(res, context, Types.resolved.t_u8 ); }
        else if (cf == Types.resolved.t_u16.feature()) { result = this._actuals.get(0).propagateExpectedType(res, context, Types.resolved.t_u16); }
        else if (cf == Types.resolved.t_u32.feature()) { result = this._actuals.get(0).propagateExpectedType(res, context, Types.resolved.t_u32); }
        else if (cf == Types.resolved.t_u64.feature()) { result = this._actuals.get(0).propagateExpectedType(res, context, Types.resolved.t_u64); }
        else if (cf == Types.resolved.t_f32.feature()) { result = this._actuals.get(0).propagateExpectedType(res, context, Types.resolved.t_f32); }
        else if (cf == Types.resolved.t_f64.feature()) { result = this._actuals.get(0).propagateExpectedType(res, context, Types.resolved.t_f64); }
        else if (cf != null && cf.preAndCallFeature() != null && !preChecked())
          {
            _calledFeature = cf.preAndCallFeature();
          }
      }
    return result;
  }


  /**
   * This is {@code true} if the precondition does not need to be checked before this
   * call is done.
   *
   * @return {@code true} for a call to a feature {@code f} in {@code f.preAndCallFeature()} since
   * this call does not require precondition checking and replacing it by a call
   * to {@code f.preAndCallFeature()} would result in endless recursion.
   */
  boolean preChecked()
  {
    return false;
  }


  /**
   * When wrapping an expression into a Lazy feature, we need to "tell it" that its
   * outer feature has changed. Otherwise, old information from previous results of
   * type resolution might remain there.
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
   */
  Call updateTarget(Resolution res, Context context)
  {
    if (_targetFrom != null)
      {
        _target = _targetFrom.target(pos(), res, context);
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
      {
        _calledFeature = Types.f_ERROR;
      }
      @Override AbstractType typeForInferencing() { return Types.t_ERROR; }
      @Override public AbstractType type() { return Types.t_ERROR; }
      @Override
      Expr boxAndTag(AbstractType frmlT, Context context)
      {
        return this;
      }
      public void setSourceRange(SourceRange r)
      { // do not change the source position if there was an error.
      }
      public Expr visit(FeatureVisitor v, AbstractFeature outer)
      {
        return this;
      }
    };
  }


  /**
   * Notify this call that it is fully inferred.
   */
  public void notifyInferred()
  {
    if (PRECONDITIONS) require
      (!actualTypeParameters().stream().anyMatch(atp -> atp.containsUndefined(false)));

    for (var r : _whenInferredTypeParameters)
      {
        r.run();
      }
  }

}

/* end of file */
