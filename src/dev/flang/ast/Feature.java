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
 * Source of class Feature
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Feature is part of the Fuzion abstract syntax tree and represents a single
 * feature declaration.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Feature extends AbstractFeature
{


  /*------------------------  static variables  -------------------------*/


  /**
   * static counter used to generate unique _id values.
   */
  static int _ids_ = 0;


  /*----------------------------  variables  ----------------------------*/


  /**
   * Unique identifier to define a total ordered over Features (used in
   * compareTo)
   */
  int _id = _ids_++;


  /**
   * The state of this feature.
   */
  private State _state = State.LOADING;


  /**
   * Set during RESOLVING_INHERITANCE in case this is part of a cyclic
   * inheritance.
   */
  private boolean _detectedCyclicInheritance = false;
  boolean detectedCyclicInheritance() { return _detectedCyclicInheritance; }


  /**
   * The sourcecode position of this feature declaration, used for error
   * messages.
   */
  private final SourcePosition _pos;


  /**
   * The sourcecode position of this feature's return type, if given explicitly.
   */
  private final SourcePosition _posOfReturnType;


  /**
   * The visibility of this feature
   */
  private Visi _visibility;
  public Visi visibility()
  {
    return _visibility == Visi.UNSPECIFIED
      ? Visi.PRIV
      : _visibility;
  }


  /**
   * Is visiblity explicitly specified in source code (or already set)?
   */
  public boolean isVisibilitySpecified()
  {
    return _visibility != Visi.UNSPECIFIED;
  }


  /**
   * This is used for feature defined using `choice of`
   * to set same visibility for choice elements as for choice in Parser.
   *
   * @param v
   */
  public void setVisbility(Visi v)
  {
    if (PRECONDITIONS) require
      (_visibility == Visi.UNSPECIFIED);

    _visibility = v;
  }


  /**
   * the modifiers of this feature
   */
  public final int _modifiers;
  public int modifiers() { return _modifiers; }


  /**
   * the result type of this feature.  Special values this might have are
   *
   * NoType: for no result type (as for an abstract or intrinsic feature)
   *
   * RefType: for constructors
   *
   * ValueType: for constructors of value types
   */
  ReturnType _returnType;
  public ReturnType returnType() { return _returnType; }


  /**
   * The qualified name of this feature as given at its declaration. This
   * usually has just one entry equal to name. If it has several entries, this
   * gives the fully qualified name of this feature.
   */
  public final List<String> _qname;


  /**
   * The FeatureName of this feature, i.e., the combination of its name and the
   * number of arguments.
   *
   * NOTE that during findDeclarations phase, this field is overwritten for
   * fields such as
   *
   *    x := 42
   *    x := x + 1
   *
   * to have FeatureNames with different ids for these two x's.
   */
  private FeatureName _featureName;


  /**
   * The formal arguments of this feature
   */
  private List<AbstractFeature> _arguments;
  public List<AbstractFeature> arguments()
  {
    return _arguments;
  }


  /**
   * The parents of this feature
   */
  private final List<AbstractCall> _inherits;
  public final List<AbstractCall> inherits() { return _inherits; }


  /**
   * The contract of this feature
   */
  private final Contract _contract;
  public Contract contract() { return _contract; }


  /**
   * Lists of features we redefine and hence from which we inherit pre or post
   * conditions.  Used during front end only to create calls to redefined
   * features post conditions when generating post condition feature for this
   * contract.
   */
  List<AbstractFeature> _inheritedPre  = new List<>();
  List<AbstractFeature> _inheritedPost = new List<>();


  /**
   * precondition feature, added during syntax sugar phase.
   */
  Feature _preFeature = null;
  @Override
  public AbstractFeature preFeature()
  {
    return _preFeature;
  }

  /**
   * pre bool feature, added during syntax sugar phase.
   */
  Feature _preBoolFeature = null;
  @Override
  public AbstractFeature preBoolFeature()
  {
    return _preBoolFeature;
  }

  /**
   * pre and call feature, added during syntax sugar phase.
   */
  Feature _preAndCallFeature = null;
  @Override
  public AbstractFeature preAndCallFeature()
  {
    return _preAndCallFeature;
  }

  /**
   * post feature, added during syntax sugar phase.
   */
  Feature _postFeature = null;
  @Override
  public AbstractFeature postFeature()
  {
    return _postFeature;
  }


  /**
   * The implementation of this feature
   */
  private Impl _impl;
  public Impl impl() { return _impl; }

  /**
   * Update the implementation of this feature, used in Loop.
   */
  void setImpl(Impl newImpl)
  {
    _impl = newImpl;
  }


  /**
   * Reference to this feature's root, i.e., its outer feature.
   */
  private AbstractFeature _outer = null;


  /**
   * In case this is a function returning a result different than self or single
   * and not implemented as a field, this is the result variable. Created during
   * LOADING.
   */
  private Feature _resultField = null;


  /**
   * For Features with !returnType.isConstructorType(), this will be set to the
   * result type during resolveTypes.
   */
  private AbstractType _resultType = null;


  /**
   * Actions collected to be executed as soon as this feature has reached
   * State.RESOLVED_DECLARATIONS, see method whenResolvedDeclarations().
   */
  private LinkedList<Runnable> whenResolvedDeclarations = new LinkedList<>();


  /**
   * Actions collected to be executed as soon as this feature has reached
   * State.RESOLVED_TYPES, see method whenResolvedTypes().
   */
  private LinkedList<Runnable> whenResolvedTypes = new LinkedList<>();


  /**
   * Field containing reference to outer feature, set after
   * RESOLVED_DECLARATIONS.
   */
  private Feature _outerRef = null;


  /**
   * Is this a loop's index variable that is automatically updated by the loops
   * for-clause.  If so, assignments outside the loop prolog or nextIteration
   * parts are not allowed.
   */
  boolean _isIndexVarUpdatedByLoop = false;
  public boolean isIndexVarUpdatedByLoop() { return _isIndexVarUpdatedByLoop; }


  /**
   * Is this a loop's variable that is being iterated over using the `in` keyword?
   * If so, also store the internal list name.
   */
  boolean _isLoopIterator = false;
  String _loopIteratorListName;


  /**
   * All features that have been found to be directly redefined by this feature.
   * This does not include redefinitions of redefinitions.  For Features loaded
   * from source code, this set is collected during RESOLVING_DECLARATIONS.  For
   * LibraryFeature, this will be loaded from the library module file.
   */
  private Set<AbstractFeature> _redefines = null;
  public Set<AbstractFeature> redefines()
  {
    if (_redefines == null)
      {
        _redefines = new TreeSet<>();
      }
    return _redefines;
  }


  /**
   * Flag used by dev.flang.fe.SourceModule to mark Features that were added to
   * their outer feature late.  Features that were added late will not be seen
   * via heirs.
   *
   * This is used for adding internal features like wrappers for lambdas.
   *
   * This is a fix for #978 but it might need to be removed when fixing #932.
   */
  public boolean _addedLate = false;


  /*
   * true if this feature is found to be
   * declared in a block with
   * _newscope=true (e.g. if/else, loop)
   * or in a case-block
   *
   * example:
   * ```
   * f0 =>
   *   if cc1 then
   *      f1 =>
   *        f2 =>
   *        if cc2 then
   *          f3 =>
   *      {
   *        f4 =>
   *      }
   * ```
   * f1, f3 and f4 are _scoped in this example.
   * f2 is not _scoped, i.e. does not need to be checked if in scope.
   * This is because if f1 is accessible then f2 is also always accessible.
   *
   */
  public boolean _scoped = false;

  private List<AbstractType> _effects;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Quick-and-dirty way to generate unique names for anonymous features.
   */
  static long uniqueAnonymousFeatureId = 0;

  /**
   * Quick-and-dirty way to generate unique names for underscore fields.
   */
  static long underscoreId = 0;


  /**
   * Constructor for universe
   */
  public Feature()
  {
    this(SourcePosition.builtIn,
         Visi.PUB,
         0,
         ValueType.INSTANCE,
         new List<String>(FuzionConstants.UNIVERSE_NAME),
         new List<>(),
         new List<>(),
         Contract.EMPTY_CONTRACT,
         new Impl(SourcePosition.builtIn,
                  new Block(new List<Expr>()),
                  Impl.Kind.Routine));
  }


  /**
   * Constructor for Types.f_ERROR, dummy feature for error handling.
   *
   * @param b ignored, just to have a different signature
   */
  Feature(boolean b)
  {
    this();
    _state = State.ERROR;
    _featureName = FeatureName.get(Types.ERROR_NAME, arguments().size());
  }


  /**
   * Create an anonymous feature
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param r the result type
   *
   * @param i the inherits calls
   *
   * @param c the contract
   *
   * @param b the implementation block
   */
  public static Feature anonymous(SourcePosition pos,
                                  ReturnType r,
                                  List<AbstractCall> i,
                                  Contract c,
                                  Block b)
  {
    return new Feature(pos,
                       Visi.UNSPECIFIED,
                       0,
                       r,
                       new List<String>(FuzionConstants.ANONYMOUS_FEATURE_PREFIX + (uniqueAnonymousFeatureId++)),
                       new List<>(),
                       i,
                       c,
                       new Impl(b.pos(), b, Impl.Kind.Routine))
      {
        boolean isAnonymousInnerFeature()
        {
          return true;
        }
      };
  }


  /**
   * Constructor for automatically generated variables (result,
   * outer).
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param v the visibility
   *
   * @param t the result type
   *
   * @param qname the name of this feature
   *
   * @param outer the declaring feature that will be set as an outer feature of
   * the newly created feature via a call to findDeclarations.
   */
  Feature(Resolution res,
          SourcePosition pos,
          Visi v,
          AbstractType t,
          String qname,
          AbstractFeature outer)
  {
    this(pos,
         v,
         0,
         t,
         qname,
         null);
    res._module.findDeclarations(this, outer);
  }


  /**
   * Constructor for field within a case of a match, e.g. the field "a" in "x ?
   * A a => ".
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param v the visibility
   *
   * @param t the result type
   *
   * @param qname the name of this feature
   */
  Feature(SourcePosition pos,
          Visi v,
          AbstractType t,
          String qname)
  {
    this(pos,
         v,
         0,
         t,
         qname,
         null);
  }


  /**
   * Loop index variable field
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param v the visibility
   *
   * @param t the result type, null in case it is inferred from initialValue
   *
   * @param qname the name of this feature
   */
  Feature(SourcePosition pos,
          Visi v,
          AbstractType t,
          String qname,
          Impl impl)
  {
    this(pos,
         v,
         0,
         t == null ? NoType.INSTANCE : new FunctionReturnType(t), /* NYI: try to avoid creation of ReturnType here, set actualtype directly? */
         new List<String>(qname),
         new List<>(),
         new List<>(),
         null,
         impl);
  }


  /**
   * Constructor for argument features
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param v the visibility
   *
   * @param m the modifiers
   *
   * @param t the result type
   *
   * @param n the name of this argument, never qualified
   *
   * @param c the contract
   *
   * @param i Impl.FIELD or Impl.TYPE_PARAMETER
   */
  public Feature(SourcePosition pos,
                 Visi v,
                 int m,
                 AbstractType t,
                 String n,
                 Contract c,
                 Impl i)
  {
    this(pos,
         v,
         m,
         t == null ? NoType.INSTANCE: new FunctionReturnType(t), /* NYI: try to avoid creation of ReturnType here, set actualtype directly? */
         new List<String>(n),
         new List<>(),
         new List<>(),
         c,
         i);
  }


  /**
   * Constructor for internally generated fields
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param v the visibility
   *
   * @param m the modifiers
   *
   * @param t the result type
   *
   * @param n the name of this argument, never qualified
   *
   * @param c the contract
   */
  public Feature(SourcePosition pos,
                 Visi v,
                 int m,
                 AbstractType t,
                 String n,
                 Contract c)
  {
    this(pos, v, m, t, n, c, Impl.FIELD);
  }


  /**
   * Quick-and-dirty way to generate unique names for anonymous inner features
   * declared for inline functions.
   */
  static long uniqueFunctionId = 0;

  /**
   * Constructor for function features
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param r the result type
   *
   * @param qname the name of this feature
   *
   * @param a the arguments
   *
   * @param i the inherits calls
   *
   * @param c the contract
   *
   * @param p the implementation
   */
  Feature(SourcePosition pos,
          ReturnType r,
          List<String> qname,
          List<AbstractFeature> a,
          List<AbstractCall> i,
          Contract c,
          Impl     p)
  {
    this(pos,
         Visi.PRIV,
         0,
         r,
         qname,
         a,
         i,
         c,
         p,
         null);
  }


  /**
   * Constructor used by parser
   *
   * @param v the visibility
   *
   * @param m the modifiers
   *
   * @param r the result type
   *
   * @param qpname the name of this feature
   *
   * @param a the arguments
   *
   * @param i the inherits calls
   *
   * @param c the contract
   *
   * @param p the implementation (feature body etc).
   */
  public Feature(Visi v,
                 int m,
                 ReturnType r,
                 List<ParsedName> qpname,
                 List<AbstractFeature> a,
                 List<AbstractCall> i,
                 Contract c,
                 Impl p,
                 List<AbstractType> effects)
  {
    this(qpname.getLast()._pos, v, m, r, qpname.map2(x -> x._name), a, i, c, p);

    _effects = effects;

    if (PRECONDITIONS) require
      (qpname.size() >= 1,
       p != null);
  }


  /**
   * Constructor without effects
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param v the visibility
   *
   * @param m the modifiers
   *
   * @param r the result type
   *
   * @param qname the name of this feature
   *
   * @param a the arguments
   *
   * @param i the inherits calls
   *
   * @param c the contract
   *
   * @param p the implementation (feature body etc).
   */
  public Feature(SourcePosition pos,
                 Visi v,
                 int m,
                 ReturnType r,
                 List<String> qname,
                 List<AbstractFeature> a,
                 List<AbstractCall> i,
                 Contract c,
                 Impl p)
  {
    this(pos,v,m,r,qname,a,i,c,p,null);
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param v the visibility
   *
   * @param m the modifiers
   *
   * @param r the result type
   *
   * @param qname the name of this feature
   *
   * @param a the arguments
   *
   * @param i the inherits calls
   *
   * @param c the contract
   *
   * @param p the implementation (feature body etc).
   */
  public Feature(SourcePosition pos,
                 Visi v,
                 int m,
                 ReturnType r,
                 List<String> qname,
                 List<AbstractFeature> a,
                 List<AbstractCall> i,
                 Contract c,
                 Impl p,
                 List<AbstractType> effects)
  {
    if (PRECONDITIONS) require
      (pos != null,
       qname.size() >= 1,
       p != null);

    this._pos        = pos;
    this._visibility = v;
    this._modifiers  = m;
    this._returnType = r;
    this._posOfReturnType = r == NoType.INSTANCE || r.isConstructorType() ? pos : r.functionReturnTypePos();
    String n = qname.getLast();
    if (n.equals("_"))
      {
        // NYI: Check that this feature is allowed to have this name, i.e., it
        // is declared in a Destructure expression.
        n = FuzionConstants.UNDERSCORE_PREFIX + underscoreId++;
      }
    this._qname     = qname;
    this._arguments = a;
    this._featureName = FeatureName.get(n, arguments().size());
    this._inherits   = (i.isEmpty() &&
                        (p._kind != Impl.Kind.FieldActual) &&
                        (p._kind != Impl.Kind.FieldDef   ) &&
                        (p._kind != Impl.Kind.FieldInit  ) &&
                        (p._kind != Impl.Kind.Field      ) &&
                        (qname.size() != 1 || (!qname.getFirst().equals(FuzionConstants.ANY_NAME  ) &&
                                               !qname.getFirst().equals(FuzionConstants.UNIVERSE_NAME))))
      ? new List<>(new Call(_pos, FuzionConstants.ANY_NAME))
      : i;

    this._contract = c == null ? Contract.EMPTY_CONTRACT : c;
    this._impl = p;

    // check args for duplicate names
    if (!a.stream()
          .map(arg -> arg.featureName().baseName())
          .filter(argName -> !argName.equals("_"))
          .allMatch(new HashSet<>()::add))
      {
        var usedNames = new HashSet<>();
        var duplicateNames = a.stream()
              .map(arg -> arg.featureName().baseName())
              .filter(argName -> !argName.equals("_"))
              .filter(argName -> !usedNames.add(argName))
              .collect(Collectors.toSet());
        // NYI report pos of arguments not pos of feature
        AstErrors.argumentNamesNotDistinct(this, duplicateNames);
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Return the state of this feature.
   */
  public State state()
  {
    return _state;
  }


  /**
   * set the state to a new value
   */
  public void setState(State newState)
  {
    if (PRECONDITIONS) require
     (newState.ordinal() == _state.ordinal() + 1 || newState == State.RESOLVED || isUniverse());

    this._state = newState;
  }


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  public SourcePosition pos()
  {
    return _pos;
  }


  /**
   * The sourcecode position of this feature declaration's result type, null if
   * not available.
   */
  public SourcePosition resultTypePos()
  {
    return _returnType.posOrNull();
  }


  /**
   * Check for possible errors related to the feature name. Currently, this only
   * checks that no feature uses FuzionConstants.RESULT_NAME as its base name
   * since this is reserved for the implicit result field.
   */
  public void checkName()
  {
    if (!isResultField() && _qname.getLast().equals(FuzionConstants.RESULT_NAME))
      {
        AstErrors.declarationOfResultFeature(_pos);
      }
  }


  /**
   * Get the outer feature of this feature, or null if this is the universe.
   *
   * The outer is set during FIND_DECLARATIONS, so this cannot be called before
   * the find declarations phase is done (i.e. we are in State.LOADED), or
   * before _outer was during the finding declarations phase.
   */
  public AbstractFeature outer()
  {
    if (PRECONDITIONS) require
      (Errors.any() || isUniverse() || state().atLeast(State.FINDING_DECLARATIONS),
      !isFreeType() || _outer.arguments().contains(this));

    return _outer;
  }


  /**
   * Has the outer feature for this feature been set?  This is always the case
   * after phase LOADING, so this may only be called during phase LOADING.
   */
  public boolean outerSet()
  {
    if (PRECONDITIONS) require
      (isUniverse() || state() == State.LOADING);

    return _outer != null;
  }

  /**
   * Set outer feature for this feature. Has to be done during phase LOADING.
   */
  public void setOuter(AbstractFeature outer)
  {
    if (PRECONDITIONS) require
      (isUniverse() || state() == State.LOADING,
       !outerSet());

    this._outer = outer;
  }


  // this caching reduces build time of base.fum by ~50%
  private Optional<Kind> _kind = Optional.empty();
  /**
   * What is this Feature's kind?
   *
   * @return Routine, Field, Intrinsic, Abstract or Choice.
   */
  public Kind kind()
  {
    var result = _kind;
    if (result.isEmpty())
      {
        var kind = state().atLeast(State.RESOLVING_TYPES) && Types.resolved != null && isChoiceAfterTypesResolved()
                     || isChoiceBeforeTypesResolved()
          ? Kind.Choice
          : switch (implKind()) {
              case FieldInit, FieldDef, FieldActual, FieldIter, Field -> Kind.Field;
              case TypeParameter                                      -> Kind.TypeParameter;
              case TypeParameterOpen                                  -> Kind.OpenTypeParameter;
              case Routine, RoutineDef, Of                            -> Kind.Routine;
              case Abstract                                           -> Kind.Abstract;
              case Intrinsic                                          -> Kind.Intrinsic;
              case Native                                             -> Kind.Native;
            };
        // cache only when we have resolved types.
        if (state().atLeast(State.RESOLVING_TYPES) && Types.resolved != null)
          {
            _kind = Optional.of(kind);
          }
         result = Optional.of(kind);
      }
    return result.get();
  }


  /**
   * get the kind of this feature.
   */
  public Impl.Kind implKind()
  {
    return _impl._kind;
  }


  /**
   * get the code of this feature.
   */
  public Expr code()
  {
    if (PRECONDITIONS) require
      (isRoutine());

    return _impl.expr();
  }


  /**
   * Is this an anonymous feature, i.e., a feature declared within an expression
   * and without giving a name, in contrast to an normal feature defined by a
   * feature declaration?
   *
   * @return true iff this feature is anonymous.
   */
  boolean isAnonymousInnerFeature()
  {
    return false;
  }


  /**
   * Is this a field that was added by fz, not by the user. If so, we do not
   * enforce visibility only after its declaration.
   *
   * @return true iff this feature is anonymous.
   */
  public boolean isArtificialField()
  {
    return isField() && _featureName.isInternal();
  }


  /**
   * true iff this is the automatically generated field RESULT_NAME or
   * INTERNAL_RESULT_NAME.
   *
   * @return true iff this is a result field.
   */
  public boolean isResultField()
  {
    return false;
  }


  /**
   * if hasResultField(), add a corresponding field to hold the result.
   */
  public void addResultField(Resolution res)
  {
    if (PRECONDITIONS) require
      (_state == State.FINDING_DECLARATIONS);

    if (hasResultField())
      {
        var t = _impl._kind == Impl.Kind.Routine
          ? _returnType.functionReturnType()
          : Types.t_UNDEFINED /* dummy type, will be replaced during TYPES_INFERENCING phase */;

        if (CHECKS) check
          (_resultField == null);
        _resultField = new Feature(res,
                                   _pos,
                                   Visi.PRIV,
                                   t,
                                   FuzionConstants.INTERNAL_RESULT_NAME,
                                   this)
          {
            public boolean isResultField() { return true; }
          };
      }
  }


  /**
   * Is this a case-field declared in a match-clause?
   */
  public boolean isCaseField()
  {
    return false;
  }


  /**
   * true iff this feature is a function or field that returns a result, but not
   * a constructor that returns its frame object.
   *
   * @return true iff this has a function result
   */
  boolean hasResult()
  {
    return isField() || hasResultField();
  }


  /**
   * In case a cycle in choice generic arguments is detected, this function is
   * used to erase the generics altogether to avoid later problems when
   * traversing types.
   */
  private void eraseChoiceGenerics()
  {
    if (PRECONDITIONS) require
      (_state.atLeast(State.RESOLVING_TYPES),
       Errors.any());

    if (this.isBaseChoice())
      { // if this == choice, there are only formal generics, so nothing to erase
      }
    else
      {
        for (var p: _inherits)
          {
            if (CHECKS) check
              (Errors.any() || p.calledFeature() != null);

            if (p.calledFeature().isBaseChoice())
              {
                if (p instanceof Call cp)
                  {
                    cp._generics = new List<AbstractType>(Types.t_ERROR);
                  }
              }
          }
      }
  }


  /**
   * Is this a choice-type, i.e., is it 'choice' or does it directly inherit from 'choice'?
   */
  boolean isChoiceBeforeTypesResolved()
  {
    if (state().atLeast(State.RESOLVED_DECLARATIONS))
      {
        if (isBaseChoice())
          {
            return true;
          }
        else
          {
            for (var p: inherits())
              {
                if (CHECKS) check
                  (Errors.any() || p.calledFeature() != null);

                var pf = p.calledFeature();
                if (pf != null && pf.isChoice())
                  {
                    return true;
                  }
              }
          }
      }
    return false;
  }


  /**
   * Is this a choice-type, i.e., does it directly inherit from choice?
   */
  boolean isChoiceAfterTypesResolved()
  {
    return choiceGenerics() != null;
  }


  /*
   * Inheritance resolution for a feature f: recursively, perform inheritance
   * resolution for the outer feature of f and for all direct ancestors of a f,
   * then perform inheritance resolution of the feature f itself.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   */
  public void scheduleForResolution(Resolution res)
  {
    if (PRECONDITIONS) require
      (state().atLeast(State.LOADED));

    if (_state == State.LOADED)
      {
        _state = State.RESOLVING;
        res.add(this);
      }
  }


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   */
  public void visit(FeatureVisitor v)
  {
    for (var c: _inherits)
      {
        Expr nc = c.visit(v, this);
        if (CHECKS) check
          (Errors.any() || c == nc); // NYI: This will fail when doing funny stuff like inherit from bool.infix &&, need to check and handle explicitly
      }
    _impl.visit(v, this);
    _returnType.visit(v, this);
  }


  /**
   * May this be a field declared directly in universe?
   */
  private boolean _legalPartOfUniverse = false;


  /**
   * Application code that is read from stdin or directly from a file is allowed
   * to declare and initialize fields directly in the universe.  This is called
   * for features that are declared in stdin or directly read file.
   */
  public void legalPartOfUniverse()
  {
    this._legalPartOfUniverse = true;
  }


  /**
   * May this be a field declared directly in universe?
   */
  public boolean isLegalPartOfUniverse()
  {
    return _legalPartOfUniverse;
  }


  /**
   * Check if this is the last argument of a feature and t is its return type.
   * This is needed during type resolution since this is the only place where an
   * open formal generic may be used.
   *
   * @return true iff this is the last argument of a feature and t is its return
   * type.
   */
  boolean isLastArgType(AbstractType t)
  {
    return
      outer() != null &&
      !outer().arguments().isEmpty() &&
      outer().arguments().getLast() == this &&
      t == _returnType.functionReturnType();
  }


  /**
   * buffer to collect cycle part of error message shown by
   * cyclicInheritanceError().
   */
  private static ArrayList<String> cyclicInhData = new ArrayList<>();


  /**
   * Helper function for resolveInheritance: In case a cycle was detected, this
   * is used to collect information on the cycle when returning from recursive
   * inheritance resolution.  When returning from the last element in the cycle,
   * create an error message from cyclicInhData.
   *
   * @param p the inherits call from this that is part of a cycle
   *
   * @param i the iterator over this.inherits() that has produced p. This will be
   * used to replace this entry to break the cycle (and hopefully avoid other
   * problems during compilation).
   */
  private void cyclicInheritanceError(AbstractCall p, ListIterator<AbstractCall> i)
  {
    if (PRECONDITIONS) require
      (p != null,
       p.calledFeature() instanceof Feature fp && fp.detectedCyclicInheritance(),
       i != null);

    var parent = p.calledFeature();
    String inh = "    inherits " + parent.qualifiedName() + " at " + p.pos().show() + "\n";
    if (_detectedCyclicInheritance)
      { // the cycle closes while returning from recursion in resolveInheritance, so show the error:
        StringBuilder cycle = new StringBuilder(inh);
        for (int c = 1; c <= cyclicInhData.size(); c++)
          {
            cycle.append(( c + 1 < 10 ? " " : "") + (c + 1) + cyclicInhData.get(cyclicInhData.size() - c));
          }
        AstErrors.recursiveInheritance(_pos, this, cycle.toString());
        cyclicInhData.clear();
      }
    else
      { // mark all member of the cycle
        cyclicInhData.add(": feature " + qualifiedName()+" at " + _pos.show() + "\n" + inh);
        _detectedCyclicInheritance = true;
      }

    // try to fix recursive inheritance to keep compiler from crashing
    i.set(new Call(_pos, FuzionConstants.ANY_NAME));
  }


  /**
   * Inheritance resolution for a feature f: recursively, perform inheritance
   * resolution for the outer feature of f and for all direct ancestors of a f,
   * then perform inheritance resolution of the feature f itself.
   *
   * After inheritance resolution for a feature f, add it to the set of features
   * to be type resolved.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   */
  void resolveInheritance(Resolution res)
  {
    if (PRECONDITIONS) require
      (_state.atLeast(State.LOADED));

    if (_state == State.RESOLVING_INHERITANCE)
      {
        _detectedCyclicInheritance = true;
      }
    else if (_state == State.RESOLVING)
      {
        _state = State.RESOLVING_INHERITANCE;

        if (CHECKS) check
          ((_outer == null) || res.state(_outer).atLeast(State.RESOLVING));

        var i = _inherits.listIterator();
        while (i.hasNext() && !_detectedCyclicInheritance)
          {
            var p = i.next();
            if (p instanceof Call cp)
              {
                cp._isInheritanceCall = true;
              }
            p.loadCalledFeature(res, context());
            var parent = p.calledFeature();
            if (CHECKS) check
              (Errors.any() || parent != null);
            if (parent instanceof Feature fp)
              {
                fp.resolveInheritance(res);
                if (fp.detectedCyclicInheritance())
                  {
                    cyclicInheritanceError(p, i);
                  }
              }
            if (!parent.isConstructor() && !parent.isChoice() /* choice is handled in choiceTypeCheckAndInternalFields */)
              {
                AstErrors.parentMustBeConstructor(p.pos(), this, parent);
              }
          }
        _state = State.RESOLVED_INHERITANCE;
        res.scheduleForDeclarations(this);
      }

    if (POSTCONDITIONS) ensure
      (_detectedCyclicInheritance || _state.atLeast(State.RESOLVED_INHERITANCE));
  }


  /**
   * For every feature 'f', this produces the corresponding type feature
   * 'f.type'.  This feature inherits from the abstract type features of all
   * direct ancestors of this, and, if there are no direct ancestors (for
   * Object), this inherits from 'Type'.
   *
   * @param res Resolution instance used to resolve this for types.
   *
   * @return The feature that should be the direct ancestor of this feature's
   * type feature.
   */
  @Override
  public AbstractFeature cotype(Resolution res)
  {
    resolveInheritance(res);
    return super.cotype(res);
  }


  /*
   * Declaration resolution for a feature f: For all declarations of features in
   * f (formal arguments, local features, implicit result field), add these
   * features to the set of features to be resolved for inheritance. Schedule f
   * for type resolution.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   */
  void resolveDeclarations(Resolution res)
  {
    if (PRECONDITIONS) require
      (_state.atLeast(State.RESOLVED_INHERITANCE));

    if (_state == State.RESOLVED_INHERITANCE)
      {
        _state = State.RESOLVING_DECLARATIONS;

        if (CHECKS) check
          (_state == State.RESOLVING_DECLARATIONS);

        this._returnType = _impl.checkReturnType(this);
        res._module.findDeclaredOrInheritedFeatures(this);

        if (CHECKS) check
          (_state.atLeast(State.RESOLVING_DECLARATIONS));

        if (_state == State.RESOLVING_DECLARATIONS)
          {
            /**
             * Find all the types used in this that refer to formal generic arguments of
             * this or any of this' outer classes.
             */
            resolveArgumentTypes(res);
            visit(res.resolveTypesOnly(this));
          }

        _state = State.RESOLVED_DECLARATIONS;
        while (!whenResolvedDeclarations.isEmpty())
          {
            whenResolvedDeclarations.removeFirst().run();
          }
        res.scheduleForTypeResolution(this);
      }

    if (POSTCONDITIONS) ensure
      (_state.atLeast(State.RESOLVING_DECLARATIONS));
  }


  /**
   * Perform an action as soon as this feature has reached
   * State.atLeast(State.RESOLVED_DECLARATIONS).  Perform the action immediately
   * if this is already the case, otherwise record the action to perform it as
   * soon as this is the case.
   *
   * @param r the action
   */
  public void whenResolvedDeclarations(Runnable r)
  {
    if (_state.atLeast(State.RESOLVED_DECLARATIONS))
      {
        r.run();
      }
    else
      {
        whenResolvedDeclarations.add(r);
      }
  }


  static class ResolveTypes extends ContextVisitor
  {
    Resolution res;
    ResolveTypes(Resolution r, Context context)
    {
      super(context);
      res = r;
    }
    @Override public void         action      (AbstractAssign  a, AbstractFeature outer) {        a.resolveTypes      (res,   _context); }
    @Override public void         actionBefore(Call            c, AbstractFeature outer) {        c.tryResolveTypeCall(res,   _context); }
    @Override public Call         action      (Call            c, AbstractFeature outer) { return c.resolveTypes      (res,   _context); }
    @Override public Expr         action      (DotType         d, AbstractFeature outer) { return d.resolveTypes      (res,   _context); }
    @Override public Expr         action      (Destructure     d, AbstractFeature outer) { return d.resolveTypes      (res,   _context); }
    @Override public Expr         action      (Feature         f, AbstractFeature outer)
    {
      if (f._sourceCodeContext == Context.NONE)  // for a lambda, this is already set.
        {
          f._sourceCodeContext = _context;
        }
      return f;
    }
    @Override public Function     action      (Function        f, AbstractFeature outer) {        f.resolveTypes      (res,   _context); return f; }
    @Override public void         action      (Match           m, AbstractFeature outer) {        m.resolveTypes      (res,   _context); }

    @Override public Expr         action      (This            t, AbstractFeature outer) { return t.resolveTypes      (res,   _context); }
    @Override public AbstractType action      (AbstractType    t, AbstractFeature outer) { return t.resolve           (res,   _context); }
    @Override public Expr         action      (AbstractCurrent c, AbstractFeature outer) { return c.resolveTypes      (res,   _context); }

    @Override public boolean doVisitActuals() { return false; }
  }


  /**
   * Resolve argument types such that type parameters for free types will be
   * added.
   *
   * @param res the resolution instance.
   */
  void resolveArgumentTypes(Resolution res)
  {
    valueArguments().stream().forEach(a -> { if (a instanceof Feature af) af.returnType().resolveArgumentType(res, af); } );
  }


  /**
   * Type resolution for a feature f: For all expressions and expressions in f's
   * inheritance clause, contract, and implementation, determine the static type
   * of the expression. Were needed, perform type inference. Schedule f for
   * syntactic sugar resolution.
   *
   * NOTE: This is called by Resolution.java. To force a feature is in state
   * RESOLVED_TYPES, use Resolution.resolveTypes(f).
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   */
  Context _sourceCodeContext = Context.NONE;
  void internalResolveTypes(Resolution res)
  {
    if (PRECONDITIONS) require
      (_state.atLeast(State.RESOLVED_DECLARATIONS));

    var old_state = _state;

    if (_state == State.RESOLVED_DECLARATIONS)
      {
        _state = State.RESOLVING_TYPES;

        if (Contract.requiresPreConditionsFeature(this) && preFeature() == null)
          {
            Contract.addPreFeature(res, this, context(), false);
          }

        resolveArgumentTypes(res);
        visit(res.resolveTypesFully(this));

        if (hasThisType())
          {
            var tt = selfType();
            _selfType = tt.resolve(res, context());
          }

        if (_effects != null)
        {
          for (var e : _effects)
            {
              var t = e.resolve(res, context());

              if (t != Types.t_ERROR && (!(t.selfOrConstraint(res, context()))
                                            .feature().inheritsFrom(Types.resolved.f_effect)))
                {
                  AstErrors.notAnEffect(t, ((UnresolvedType) e).pos());
                }
            }
        }

        _state = State.RESOLVED_TYPES;
        while (!whenResolvedTypes.isEmpty())
          {
            whenResolvedTypes.removeFirst().run();
          }
        res.scheduleForSyntacticSugar1Resolution(this);
      }

    if (POSTCONDITIONS) ensure
      (old_state == State.RESOLVING_TYPES && _state == old_state /* recursive attempt to resolve types */ ||
       _state.atLeast(State.RESOLVING_TYPES));
  }


  /**
   * Perform an action as soon as this feature has reached
   * State.atLeast(State.RESOLVED_TYPES).  Perform the action immediately if
   * this is already the case, otherwise record the action to perform it as soon
   * as this is the case.
   *
   * This is used to solve cyclic dependencies in case features A and B use one
   * another.
   *
   * @param r the action
   */
  void whenResolvedTypes(Runnable r)
  {
    if (_state.atLeast(State.RESOLVED_TYPES))
      {
        r.run();
      }
    else
      {
        whenResolvedTypes.add(r);
      }
  }


  /**
   * Syntactic sugar resolution of a feature f after type resolution. Currently
   * used for lazy boolean operations like &&, || and for compile-time constants
   * safety, debug_level, debug.
   *
   * @param res the resolution instance.
   */
  void resolveSyntacticSugar1(Resolution res)
  {
    if (PRECONDITIONS) require
      (_state.atLeast(State.RESOLVED_TYPES));

    if (_state == State.RESOLVED_TYPES)
      {
        _state = State.RESOLVING_SUGAR1;

        Contract.addContractFeatures(res, this, context());
        if (!isUniverse() && !isCotype()
            && !isField() /* NYI: UNDER DEVELOPMENT: does not work yet for fields */
            && !isTypeParameter())
          {
            cotype(res);
          }
        visit(new ContextVisitor(context())
          {
            public Expr action(Feature f, AbstractFeature outer) { return f.resolveSyntacticSugar1(res, _context, this); }
            public Expr action(Call    c, AbstractFeature outer) { return c.resolveSyntacticSugar1(res, _context      ); }
          });


        _state = State.RESOLVED_SUGAR1;
        res.scheduleForTypeInference(this);
      }

    if (POSTCONDITIONS) ensure
      (_state.atLeast(State.RESOLVED_SUGAR1));
  }


  /**
   * Find list of all accesses to this feature's closure by any of its inner
   * features.
   */
  private List<AbstractCall> closureAccesses(Resolution res)
  {
    List<AbstractCall> result = new List<>();
    res._module.forEachDeclaredOrInheritedFeature(this,
                                                  af -> af.visitExpressions(s -> {
          if (s instanceof AbstractCall c && dependsOnOuterRef(c))
            {
              result.add(c);
            }
        })
      );
    return result;
  }


  /**
   * Returns true if the call depends on an outer reference.
   * @param c
   * @return
   */
  private boolean dependsOnOuterRef(AbstractCall c)
  {
    return c.calledFeature() == outerRef() ||
    // see issue #698 for an example where this applies.
      c.calledFeature().inherits().stream().anyMatch(ihc -> ihc.target().isCallToOuterRef());
  }


  /**
   * Check that this feature and all its declared or inherited features does not
   * contain code that would access the outer features of this feature.  If such
   * accesses exists, report an error that this not allowed.
   *
   * @param errorPos the position this error should be reported at, this should
   * be the definition of the choice type.
   */
  void checkNoClosureAccesses(Resolution res, SourcePosition errorPos)
  {
    var closureAccesses = closureAccesses(res);
    if (!closureAccesses.isEmpty())
      {
        StringBuilder accesses = new StringBuilder();
        for (var c: closureAccesses)
          {
            accesses.append(c.pos().show()).append("\n");
          }
        AstErrors.choiceMustNotAccessSurroundingScope(errorPos, accesses.toString());
      }
  }


  /**
   * Does this expression consist of nothing but declarations? I.e., it has no
   * code that actually would be executed at runtime.
   */
  public boolean containsOnlyDeclarations()
  {
    return switch (_impl._kind)
      {
      case FieldInit,    // a field with initialization syntactic sugar
           FieldDef      // a field with implicit type
        -> false;
      case Field,        // a field
           FieldActual,  // a field with implicit type taken from actual argument to call
           RoutineDef,   // normal feature with code and implicit result type
           Routine,      // normal feature with code
           Intrinsic,    // intrinsic feature
           Abstract      // abstract feature
        -> true;
      default -> throw new Error("missing case "+_impl._kind);
      };
  }


  /**
   * For a choice feature, perform compile time checks for validity and add
   * internal fields for the type tag and the values.
   *
   * Due to bootstrapping, this cannot be performed during resolveTypes, so it
   * is part of the typeInference pass.
   *
   *
   * @param res this is called during type interference, res gives the resolution
   * instance to schedule new fields for resolution.
   */
  private void checkChoiceAndAddInternalFields(Resolution res)
  {
    if (PRECONDITIONS) require
      (isChoice());

    if (isRef())
      {
        AstErrors.choiceMustNotBeRef(_pos);
      }

    // choice type must not contain any code, but may contain inner features
    switch (_impl._kind)
      {
      case FieldInit:    // a field with initialization syntactic sugar
      case FieldDef:     // a field with implicit type
      case FieldActual:  // a field with implicit type taken from actual argument to call
      case Field:        // a field
        {
          AstErrors.choiceMustNotBeField(_pos);
          break;
        }
      case RoutineDef:  // normal feature with code and implicit result type
        {
          AstErrors.choiceMustNotBeRoutine(_pos);
          break;
        }
      case Routine:      // normal feature with code
        {
          if (!_impl.containsOnlyDeclarations())
            {
              AstErrors.choiceMustNotContainCode(_pos);
            }
          break;
        }
      case Abstract:
        { // not ok
          AstErrors.choiceMustNotBeAbstract(_pos);
          break;
        }
      case Intrinsic:
        {
          AstErrors.choiceMustNotBeIntrinsic(_pos);
          break;
        }
      }

    res._module.forEachDeclaredOrInheritedFeature(this,
                                                  p ->
      {
        if (_returnType != NoType.INSTANCE &&
            _returnType != ValueType.INSTANCE)
          { // choice type must not have a result type
            if (!(Errors.any() && _returnType == RefType.INSTANCE))  // this was covered by AstErrors.choiceMustNotBeRef
              {
                /*
    // tag::fuzion_rule_CHOICE_RESULT[]
A ((Choice)) declaration must not contain a result type.
    // end::fuzion_rule_CHOICE_RESULT[]
                */
                AstErrors.choiceMustNotHaveResultType(_pos, _returnType);
              }
          }
        else if (p.isField() && !p.isOuterRef() &&
                 !(Errors.any() && (p instanceof Feature pf && (pf.isArtificialField() || /* do not report auto-generated fields like `result` in choice if there are other problems */
                                                                pf.isResultField()
                                                                )
                                    )
                   )
                 )
          { // choice type must not have any fields
            AstErrors.mustNotContainFields(_pos, p, "Choice");
          }
      });

    for (var t : choiceGenerics())
      {
        if (CHECKS) check
          (Errors.any() || t != null);
        if (t != null && !t.isRef())
          {
            if (t.compareTo(thisType()) == 0)
              {
                AstErrors.choiceMustNotReferToOwnValueType(_pos, t);
                _selfType = Types.t_ERROR;
                eraseChoiceGenerics();
              }
            var o = outer();
            while (o != null)
              {
                if (t.compareTo(o.thisType()) == 0)
                  {
                    AstErrors.choiceMustNotReferToOuterValueType(_pos, t);
                    eraseChoiceGenerics();
                  }
                o = o.outer();
              }
          }
      }

    checkNoClosureAccesses(res, _pos);
    for (var p : _inherits)
      {
        p.calledFeature().checkNoClosureAccesses(res, p.pos());
      }
  }


  /**
   * Choice related checks: Check if this inherits from a choice and flag an
   * error if this is the case.
   *
   * Check if this is a choice and if so, call checkChoiceAndAddInternalFields
   * for further checking and adding of fields.
   *
   * @param res this is called during type interference, res gives the resolution
   * instance to schedule new fields for resolution.
   */
  void choiceTypeCheckAndInternalFields(Resolution res)
  {
    if (isChoice())
      {
        checkChoiceAndAddInternalFields(res);
      }
    if (isBuiltInPrimitive())
      {
        checkBuiltInPrimitive(res);
      }
  }


  /**
   * check that primitives do not contain fields
   *
   * @param res
   */
  private void checkBuiltInPrimitive(Resolution res)
  {
    res._module.forEachDeclaredOrInheritedFeature(this,
                                                  p ->
      {
        // primitives must not have any fields
        if (p.isField() && !p.isOuterRef() && !(p.featureName().baseName().equals("val") && p.resultType().compareTo(selfType())==0) )
          {
            AstErrors.mustNotContainFields(_pos, p, this.featureName().baseName());
          }
      });
  }


  /**
   * Type inference determines static types that are not given explicitly in the
   * source code.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance to schedule new features for resolution.
   */
  void typeInference(Resolution res)
  {
    if (PRECONDITIONS) require
      (_state.atLeast(State.RESOLVED_TYPES));

    if (_state == State.RESOLVED_SUGAR1)
      {
        _state = State.TYPES_INFERENCING;

        if (CHECKS) check
          (_resultType == null
           || isUniverse() // NYI: HACK: universe is currently resolved twice, once as part of stdlib, and then as part of another module
           );

        if (outer() instanceof Feature o)
          {
            o.typeInference(res);
          }

        _resultType = resultTypeIfPresentUrgent(res, true);
        if (_resultType == null)
          {
            AstErrors.failedToInferResultType(this);
            _resultType = Types.t_ERROR;
          }

        if (_resultType.isThisType() && _resultType.feature() == this)
          { // we are in the case of issue #1186: A routine returns itself:
            //
            //  a => a.this
            AstErrors.routineCannotReturnItself(this);
            _resultType = Types.t_ERROR;
          }

        /**
         * Perform type inference from outside to the inside, i.e., propagate the
         * expected type as in
         *
         *   f (b bool) i64              { if (b) { 23 } else { -17 } }
         *   g (b bool) choice<A, f32> } { b ? 3.4 : A }
         *   abstract myfun { abstract x(a i32) i32 }
         *   h myfun { fun (a) => a*a }
         *
         * Here, i64 will be propagated to be used as the type of "23" and
         * "-17", choice<A, f32> will be used as the type of "3.4" and "A", and
         * myfun will be used as the type of "fun (a) => a*a", which implies
         * that i32 will be the type for "a".
         */
        visit(new ContextVisitor(context()) {
            public void  action(AbstractAssign a, AbstractFeature outer) { a.propagateExpectedType(res, _context); }
            public Call  action(Call           c, AbstractFeature outer) { c.propagateExpectedType(res, _context); return c; }
            public void  action(Cond           c, AbstractFeature outer) { c.propagateExpectedType(res, _context); }
            public void  action(Impl           i, AbstractFeature outer) { i.propagateExpectedType(res, _context); }
            public Expr  action(If             i, AbstractFeature outer) { i.propagateExpectedType(res, _context); return i; }
          });

        /*
         * extra pass to automatically wrap values into 'Lazy'
         * or unwrap values inheriting `unwrap`
         */
        visit(new ContextVisitor(context()) {
            // we must do this from the outside of calls towards the inside to
            // get the corrected nesting of Lazy features created during this
            // phase
            public boolean visitActualsLate() { return true; }
            public void  action(AbstractAssign a, AbstractFeature outer) { a.wrapValueInLazy  (res, _context); a.unwrapValue  (res, _context); }
            public Expr  action(Call           c, AbstractFeature outer) { c.wrapActualsInLazy(res, _context); c.unwrapActuals(res, _context); return c; }
          });

        if (isConstructor())
          {
            _impl.propagateExpectedType(res, context(), Types.resolved.t_unit);
          }

        _state = State.TYPES_INFERENCED;
        res.scheduleForSyntacticSugar2Resolution(this);
      }

    if (POSTCONDITIONS) ensure
      (_state.atLeast(State.TYPES_INFERENCED));
  }


  /**
   * Perform boxing, i.e., wrap value instances into ref instances if they are
   * assigned to a ref.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   */
  void box(Resolution res)
  {
    if (PRECONDITIONS) require
      (_state.atLeast(State.RESOLVED_SUGAR2));

    if (_state == State.RESOLVED_SUGAR2)
      {
        _state = State.BOXING;

        visit(new ContextVisitor(context()) {
            public void  action(AbstractAssign a, AbstractFeature outer) { a.boxVal     (_context);           }
            public Call  action(Call           c, AbstractFeature outer) { c.boxArgs    (_context); return c; }
            public Expr  action(InlineArray    i, AbstractFeature outer) { i.boxElements(_context); return i; }
          });

        _state = State.BOXED;
        res.scheduleForCheckTypes(this);
      }

    if (POSTCONDITIONS) ensure
      (_state.atLeast(State.BOXED));
  }


  /**
   * Perform type checking, in particular, verify that all redefinitions of this
   * have the argument types.  Create compile time errors if this is not the
   * case.
   */
  private void checkTypes(Resolution res, Context context)
  {
    if (PRECONDITIONS) require
      (_state.atLeast(State.CHECKING_TYPES));

    res._module.checkTypes(this, context);
  }


  /**
   * Perform static type checking, i.e., make sure, that for all assignments from
   * actual to formal arguments or from values to fields, the types match.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   */
  void checkTypes(Resolution res)
  {
    if (PRECONDITIONS) require
      (_state == State.BOXED);

    _state = State.CHECKING_TYPES;

    choiceTypeCheckAndInternalFields(res);

    _selfType   = selfType() .checkChoice(_pos,             context());
    _resultType = _resultType.checkChoice(_posOfReturnType == SourcePosition.builtIn ? _pos : _posOfReturnType, context());
    visit(new ContextVisitor(context()) {
        /* if an error is reported in a call it might no longer make sense to check the actuals: */
        @Override public boolean visitActualsLate() { return true; }
        @Override public void         action(AbstractAssign a, AbstractFeature outer) {        a.checkTypes(res,  _context);           }
        @Override public Call         action(Call           c, AbstractFeature outer) {        c.checkTypes(res,  _context); return c; }
        @Override public void         action(Constant       c                       ) {        c.checkRange();                         }
        @Override public void         action(AbstractMatch  m                       ) {        m.checkTypes(_context);                 }
        @Override public Expr         action(InlineArray    i, AbstractFeature outer) {        i.checkTypes(      _context); return i; }
        @Override public AbstractType action(AbstractType   t, AbstractFeature outer) { return t.checkConstraints(_context);           }
        @Override public void         action(Cond           c, AbstractFeature outer) {        c.checkTypes();                         }
        @Override public void         actionBefore(Block    b, AbstractFeature outer) {        b.checkTypes();                         }
      });
    checkTypes(res, context());
    visit(new ContextVisitor(context()) {
      @Override public Expr action(Feature f, AbstractFeature outer) { return new Nop(_pos);}
    });

    _state = State.RESOLVED;
  }


  /**
   * The result field declared automatically in case hasResultField().
   *
   * @return the result or null if this does not have a result field.
   */
  public Feature resultField()
  {
    if (PRECONDITIONS) require
      (_state.atLeast(State.LOADED));

    Feature result = _resultField;

    if (POSTCONDITIONS) ensure
      (Errors.any() ||
       hasResultField() == (result != null) ||

       // the following will later be checked by checkChoiceAndAddInternalFields() and
       // reported as an error (fuzion rule CHOICE_RESULT):
       isChoice() && (result != null)
       );
    return result;
  }


  /**
   * Syntactic sugar resolution of a feature f: For all expressions and
   * expressions in f's inheritance clause, contract, and implementation, resolve
   * syntactic sugar, e.g., by replacing anonymous inner functions by
   * declaration of corresponding inner features. Add (f,<>) to the list of
   * features to be searched for runtime types to be layouted.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   */
  void resolveSyntacticSugar2(Resolution res)
  {
    if (PRECONDITIONS) require
      (_state == State.TYPES_INFERENCED);

    _state = State.RESOLVING_SUGAR2;

    visit(new ContextVisitor(context()) {
        @Override public Expr  action(Function    f, AbstractFeature outer) { return f.resolveSyntacticSugar2(res); }
        @Override public Expr  action(InlineArray i, AbstractFeature outer) { return i.resolveSyntacticSugar2(res, _context); }
        @Override public void  action(Impl        i, AbstractFeature outer) {        i.resolveSyntacticSugar2(res, _context); }
        @Override public Expr  action(If          i, AbstractFeature outer) { return i.resolveSyntacticSugar2(res); }
      });

    _state = State.RESOLVED_SUGAR2;
    res.scheduleForBoxing(this);
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
    // impl.initialValue is code executed by outer, not by this. So we visit it
    // here, while impl.code is visited when impl.visit is called with this as
    // outer argument.
    //
    if (_impl.hasInitialValue() &&
        /* initial value has been replaced by explicit assignment during
         * RESOLVING_TYPES phase: */
        (outer == null || !outer.state().atLeast(State.RESOLVING_SUGAR1)))
      {
        _impl.visitExpr(v, outer);
      }
    return v.action(this, outer);
  }


  /**
   * resolve syntactic sugar of feature declaration, i.e., add assignment for the
   * initial value of fields.
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this feature declaration is done
   *
   * @param rss1 the visitor to resolve syntax sugar 1, used to visit recursively.
   */
  public Expr resolveSyntacticSugar1(Resolution res, Context context, ContextVisitor rss1)
  {
    var outer = context.outerFeature();

    if (PRECONDITIONS) require
      (res != null,
       outer.state() == State.RESOLVING_SUGAR1,
       isUniverse() || outer != null || Errors.any());

    Expr result = this;

    if (CHECKS) check
      (Errors.any() ||
       (_impl._kind != Impl.Kind.FieldDef    &&
        _impl._kind != Impl.Kind.FieldActual)
       || _returnType == NoType.INSTANCE);

    if (_impl.hasInitialValue())
      {
        // outer() != outer may be the case for fields declared in types
        //
        //   type.f := x
        //
        // or for qualified declarations
        //
        //   String.new_field := 3
        //
        // which should have caused errors already.
        if (CHECKS) check
          (Errors.any() || this.outer() == outer);

        if (this.outer() == outer)
          {
            /* add assignment of initial value: */
            AbstractAssign ass = new Assign(res, _pos, this, _impl.expr(), context);
            ass = ass.visit(rss1, outer);
            result = new Block(new List<>(this, ass));
          }
      }
    return result;
  }


  /**
   * During type resolution, add a type parameter created for a free type like
   * `T` in `f(x T) is ...`.
   *
   * @param res the resolution instance.
   *
   * @param ta the newly created type parameter feature.
   *
   * @return the generic instance for ta
   */
  Generic addTypeParameter(Resolution res, Feature ta)
  {
    if (PRECONDITIONS) require
      (ta.isFreeType());

    // A call to generics() has the side effects of setting _generics,
    // _arguments and _typeArguments
    var unused = generics();

    // Now we patch the new type parameter ta into _arguments, _typeArguments
    // and _generics:
    var a = _arguments;
    _arguments = new List<>(a);
    var tas = typeArguments();
    _arguments.add(tas.size(), ta);
    tas.add(ta);

    // NYI: For now, we keep the original FeatureName since changing it would
    // require updating res._module.declaredFeatures /
    // declaredOrInheritedFeatures. This means free types do not increase the
    // arg count in feature name. This does not seem to cause problems when
    // looking up features, but we may miss to report errors for duplicate
    // features.  Note that when saved to a module file, this feature's name
    // will have the actual argument count, so this inconsistency is restricted
    // to the current source module.
    //
    //    _featureName = FeatureName.get(_featureName.baseName(), _arguments.size());
    res._module.findDeclarations(ta, this);

    var g = ta.asGeneric();
    _generics = _generics.addTypeParameter(g);
    res._module.addTypeParameter(this, ta);
    this.whenResolvedTypes(()->res.resolveTypes(ta));

    return g;
  }


  /**
   * Is this a type parameter created for a free type?
   */
  boolean isFreeType()
  {
    return false;
  }


  /**
   * resultTypeIfPresentUrgent returns the result type of this feature using the
   * formal generic argument.
   *
   * @param urgent if true and the result type is inferred and inference would
   * currently not succeed, then enforce it even if that would produce an error.
   *
   * @return this feature's result type, null in case the type is currently
   * unknown since the type inference is incomplete.
   */
  @Override
  AbstractType resultTypeIfPresentUrgent(Resolution res, boolean urgent)
  {
    AbstractType result;

    if (res != null && !res.state(this).atLeast(State.RESOLVING_TYPES))
      {
        res.resolveTypes(this);
      }

    if (_resultType != null)
      {
        result = _resultType;
      }
    else if (outer() != null && this == outer().resultField())
      {
        result = outer().resultTypeIfPresent(res);
      }
    else if (_impl.typeInferable())
      {
        if (CHECKS) check
          (!state().atLeast(State.TYPES_INFERENCED));
        result = _impl.inferredType(res, this, urgent);
      }
    else if (_returnType.isConstructorType())
      {
        result = selfType();
      }
    else if (_returnType == NoType.INSTANCE)
      {
        result = null;
      }
    else
      {
        result = _returnType.functionReturnType();
      }
    if (isOuterRef() && !outer().isFixed())
      {
        result = result.asThis();
      }
    if (res != null && result != null && outer() != null)
      {
        result = result.resolve(res, outer().context());
      }

    if (POSTCONDITIONS) ensure
      (isCoTypesThisType() || Types.resolved == null || selfType() == Types.resolved.t_Const_String || result != Types.resolved.t_Const_String);

    return result;
  }


  /**
   * After type resolution, resultType returns the result type of this
   * feature using the formal generic argument.
   *
   * @return the result type, t_ERROR in case of an error.  Never
   * null. Types.t_UNDEFINED in case type inference for this type is cyclic and
   * hence impossible.
   */
  @Override
  public AbstractType resultType()
  {
    if (PRECONDITIONS) require
      (Errors.any() || _state.atLeast(State.RESOLVED_TYPES));

    var result = _state.atLeast(State.RESOLVED_TYPES) ? resultTypeIfPresentUrgent(null, true) : null;
    if (result == null)
      {
        if (CHECKS) check
          (Errors.any());

        result = Types.t_ERROR;
      }

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * determine if this feature can either be called in a way that requires the
   * creation of a frame object or any heir features of this might do so.
   *
   * @return true iff this has or any heir of this might have a frame object on
   * a call.
   */
  private boolean hasThisType()
  {
    return
      _impl._kind != Impl.Kind.Intrinsic &&
      _impl._kind != Impl.Kind.Abstract  &&
      !isField();
  }


  public FeatureName featureName()
  {
    if (CHECKS) check
      (// the feature name is currently not changed in addTypeParameter, so
       // we add freeTypesCount() here.
       arguments().size() == _featureName.argCount() + freeTypesCount());

    return _featureName;
  }


  /**
   * Number of free types among the type parameters.
   */
  public int freeTypesCount()
  {
    var result = 0;
    for (var tp : _arguments)
      {
        result += ((Feature) tp).isFreeType() ? 1 : 0;
      }
    return result;
  }


  /**
   * Set the feature's name to a new value.  This can only be used to modify the
   * feature name's id field, which is used to distinguish several fields with
   * equal name as in
   *
   *   x := 42
   *   x := x + 1
   */
  public void setFeatureName(FeatureName newFeatureName)
  {
    if (PRECONDITIONS) require
      (_featureName.baseName() == newFeatureName.baseName(),
       _featureName.argCount() == 0,
       newFeatureName.argCount() == 0);

    _featureName = newFeatureName;
  }


  /**
   * outerRefName
   *
   * @return
   */
  private String outerRefName()
  {
    if (PRECONDITIONS) require
      (_outer != null);

    return FuzionConstants.OUTER_REF_PREFIX + qualifiedName();
  }


  /**
   * Is this a routine that returns the current instance as its result?
   */
  public boolean isConstructor()
  {
    return isRoutine() && _returnType.isConstructorType() ||
      // special handling if this is called before resolveDeclarations:
      !state().atLeast(State.RESOLVING_DECLARATIONS) && _impl._kind == Impl.Kind.Routine && _returnType == NoType.INSTANCE;
  }


  /**
   * Is this a constructor returning a reference result?
   */
  public boolean isRef()
  {
    return _returnType == RefType.INSTANCE;
  }


  /**
   * Add implicit field to the outer feature of this.
   */
  public void addOuterRef(Resolution res)
  {
    if (PRECONDITIONS) require
      (_state.atLeast(State.FINDING_DECLARATIONS));

    if (hasOuterRef())
      {
        var outerRefType = isOuterRefAdrOfValue() ? Types.t_ADDRESS
                                                  : this._outer.selfType();
        _outerRef = new Feature(res,
                                _pos,
                                Visi.PRIV,
                                outerRefType,
                                outerRefName(),
                                this);

        whenResolvedTypes(()->_outerRef.scheduleForResolution(res));
      }
  }


  /**
   * outerRef returns the field of this feature that refers to the
   * outer field.
   *
   * @return the outer ref if it exists, null otherwise.
   */
  public AbstractFeature outerRef()
  {
    if (PRECONDITIONS) require
      (isUniverse() || _state.atLeast(State.RESOLVING_DECLARATIONS));

    Feature result = _outerRef;

    if (POSTCONDITIONS) ensure
      (!hasOuterRef() || result != null);

    return result;
  }


  /**
   * Check if this is an outer ref field.
   */
  public boolean isOuterRef()
  {
    var o = outer();
    return o != null && (o instanceof Feature of ? of._outerRef : o.outerRef()) == this;
  }


  /**
   * Compare this to other for sorting Feature
   */
  public int compareTo(AbstractFeature other)
  {
    return (other instanceof Feature of)
      ? _id - of._id
      : +1;
  }


  /**
   * Is this the `call` implementation of a lambda?
   */
  public boolean isLambdaCall()
  {
    return false;
  }


  /**
   * Is this the 'THIS_TYPE' type parameter in a type feature?
   *
   * Overriding since AbstractFeature.isCoTypesThisType needs outer to be
   * in state of at least FINDING_DECLARATIONS which is not always the case
   * when isCoTypesThisType is called.
   */
  @Override
  public boolean isCoTypesThisType()
  {
    return false;
  }


  /**
   * Is this base-lib's choice-feature?
   */
  @Override
  boolean isBaseChoice()
  {
    if (PRECONDITIONS) require
      (state().atLeast(State.RESOLVED_DECLARATIONS));

    return Types.resolved != null
      ? this == Types.resolved.f_choice
      : (featureName().baseName().equals("choice") && featureName().argCount() == 1 && outer().isUniverse());
  }


  /**
   * Does this feature define a type that is
   * (potentially) qualifiable in sourcecode?
   */
  public boolean definesUsableType()
  {
    return definesType() && !featureName().isInternal();
  }


}

/* end of file */
