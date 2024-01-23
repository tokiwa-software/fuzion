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
import java.util.Set;
import java.util.Stack;
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
    return
      // NYI anonymous feature should have correct visibility set.
      isAnonymousInnerFeature()
      ? outer().visibility()
      : _visibility == Visi.UNSPECIFIED
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
   * Flag set during resolveTypes if this feature's code has at least one
   * assignment to the result field.
   */
  private boolean _hasAssignmentsToResult = false;


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
   * All features that have been found to be directly redefined by this feature.
   * This does not include redefinitions of redefinitions.  Four Features loaded
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
   *
   * @param initialValue the initial value used for type inference in case t == null
   *
   * @param outerOfInitialValue the feature that contains the expression initialValue
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
         p);
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
  public Feature(Visi v,
                 int m,
                 ReturnType r,
                 List<ParsedName> qpname,
                 List<AbstractFeature> a,
                 List<AbstractCall> i,
                 Contract c,
                 Impl p)
  {
    this(qpname.getLast()._pos, v, m, r, qpname.map2(x -> x._name), a, i, c, p);

    if (PRECONDITIONS) require
      (qpname.size() >= 1,
       p != null);
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
                 Impl p)
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


  /**
   * What is this Feature's kind?
   *
   * @return Routine, Field, Intrinsic, Abstract or Choice.
   */
  public Kind kind()
  {
    return state().atLeast(State.RESOLVING_TYPES) && isChoiceAfterTypesResolved() ||
          !state().atLeast(State.RESOLVING_TYPES) && isChoiceBeforeTypesResolved()
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
  }


  /**
   * get the kind of this feature.
   */
  public Impl.Kind implKind()
  {
    return _impl._kind;
  }


  /**
   * Is this an intrinsic feature that creates an instance of its result ref
   * type?
   */
  public boolean isIntrinsicConstructor()
  {
    return _impl == Impl.INTRINSIC_CONSTRUCTOR;
  }


  /**
   * get the code of this feature.
   */
  public Expr code()
  {
    if (PRECONDITIONS) require
      (isRoutine());

    return _impl._code;
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
  boolean isResultField()
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
                                   resultInternal() ? FuzionConstants.INTERNAL_RESULT_NAME
                                                    : FuzionConstants.RESULT_NAME,
                                   this)
          {
            protected boolean isResultField() { return true; }
          };
      }
  }


  /**
   * Check if the result variable should be internal, i.e., have a name that is
   * not accessible by source code.  This is true for routines defined using
   * '=>" (RoutineDef) that are internally generated, e.g. for loops.
   * In these cases, the result variable of the enclosing outer feature can be
   * accessed without qualification.
   */
  public boolean resultInternal()
  {
    return _impl._kind == Impl.Kind.RoutineDef &&
      _featureName.isInternal();
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

    if (this == Types.resolved.f_choice)
      { // if this == choice, there are only formal generics, so nothing to erase
      }
    else
      {
        for (var p: _inherits)
          {
            if (CHECKS) check
              (Errors.any() || p.calledFeature() != null);

            if (p.calledFeature() == Types.resolved.f_choice)
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
                if (pf != null && pf.isBaseChoice())
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
    _contract.visit(v, this);
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
            p.loadCalledFeature(res, this);
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
            visit(res.resolveTypesOnly);
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


  static class ResolveTypes extends FeatureVisitor
  {
    Resolution res;
    ResolveTypes(Resolution r)
      {
        res = r;
      }
    public void         action      (AbstractAssign a, AbstractFeature outer) {        a.resolveTypes   (res,   outer); }
    public Call         action      (Call           c, AbstractFeature outer) { return c.resolveTypes   (res,   outer); }
    public Expr         action      (DotType        d, AbstractFeature outer) { return d.resolveTypes   (res,   outer); }
    public Expr         action      (Destructure    d, AbstractFeature outer) { return d.resolveTypes   (res,   outer); }
    public Expr         action      (Feature        f, AbstractFeature outer) { /* use f.outer() since qualified feature name may result in different outer! */
                                                                                return f.resolveTypes   (res, f.outer() ); }
    public Function     action      (Function       f, AbstractFeature outer) {        f.resolveTypes   (res,   outer); return f; }
    public void         action      (Match          m, AbstractFeature outer) {        m.resolveTypes   (res,   outer); }
    public Expr         action      (This           t, AbstractFeature outer) { return t.resolveTypes   (res,   outer); }
    public AbstractType action      (AbstractType   t, AbstractFeature outer) { return t.resolve        (res,   outer); }

    public boolean doVisitActuals() { return false; }
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
  void internalResolveTypes(Resolution res)
  {
    if (PRECONDITIONS) require
      (_state.atLeast(State.RESOLVED_DECLARATIONS));

    var old_state = _state;

    if (_state == State.RESOLVED_DECLARATIONS)
      {
        _state = State.RESOLVING_TYPES;

        resolveArgumentTypes(res);
        visit(res.resolveTypesFully);

        if (hasThisType())
          {
            var tt = selfType();
            _selfType = tt.resolve(res, this);
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

        if (definesType())
          {
            typeFeature(res);
          }
        visit(new FeatureVisitor()
          {
            public Expr action(Call c, AbstractFeature outer) { return c.resolveSyntacticSugar(res, outer); }
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
    for (AbstractFeature af : res._module.declaredOrInheritedFeatures(this).values())
      {
        af.visitExpressions(s -> {
            if (s instanceof AbstractCall c && dependsOnOuterRef(c))
              {
                result.add(c);
              }
          });
      }
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

    if (isThisRef())
      {
        AstErrors.choiceMustNotBeRef(_pos);
      }

    for (AbstractFeature p : res._module.declaredOrInheritedFeatures(this).values())
      {
        // choice type must not have any fields
        if (p.isField() && !p.isOuterRef())
          {
            AstErrors.mustNotContainFields(_pos, p, "Choice");
          }
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

    selfType().checkChoice(_pos);

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
    for (var p : _inherits)
      {
        // choice type is leaf
        var cf = p.calledFeature();
        if (CHECKS) check
          (Errors.any() || cf != null);

        if (cf != null && cf.isChoice() && cf != Types.resolved.f_choice)
          {
            AstErrors.cannotInheritFromChoice(p.pos());
          }
      }
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
    for (AbstractFeature p : res._module.declaredOrInheritedFeatures(this).values())
      {
        // primitives must not have any fields
        if (p.isField() && !p.isOuterRef() && !(p.featureName().baseName().equals("val") && p.resultType().equals(selfType())) )
          {
            AstErrors.mustNotContainFields(_pos, p, this.featureName().baseName());
          }
      }
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
        choiceTypeCheckAndInternalFields(res);

        _resultType = resultTypeIfPresent(res);
        if (_resultType == null)
          {
            AstErrors.failedToInferResultType(this);
            _resultType = Types.t_ERROR;
          }

        if (!isTypeParameter())
          {
            _resultType.checkChoice(_posOfReturnType);
          }

        if (_resultType.isThisType() && _resultType.featureOfType() == this)
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
        visit(new FeatureVisitor() {
            public void  action(AbstractAssign a, AbstractFeature outer) { a.propagateExpectedType(res, outer); }
            public Call  action(Call           c, AbstractFeature outer) { c.propagateExpectedType(res, outer); return c; }
            public void  action(Cond           c, AbstractFeature outer) { c.propagateExpectedType(res, outer); }
            public void  action(Impl           i, AbstractFeature outer) { i.propagateExpectedType(res, outer); }
            public void  action(If             i, AbstractFeature outer) { i.propagateExpectedType(res, outer); }
          });

        /* extra pass to automatically wrap values into 'Lazy' */
        visit(new FeatureVisitor() {
            // we must do this from the outside of calls towards the inside to
            // get the corrected nesting of Lazy features created during this
            // phase
            public boolean visitActualsLate() { return true; }
            public void  action(AbstractAssign a, AbstractFeature outer) { a.wrapValueInLazy  (res, outer); }
            public Expr  action(Call           c, AbstractFeature outer) { c.wrapActualsInLazy(res, outer); return c; }
          });

        if (isConstructor())
          {
            _impl._code = _impl._code.propagateExpectedType(res, this, Types.resolved.t_unit);
          }

        _state = State.TYPES_INFERENCED;
        res.scheduleForBoxing(this);
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
      (_state.atLeast(State.TYPES_INFERENCED));

    if (_state == State.TYPES_INFERENCED)
      {
        _state = State.BOXING;

        visit(new FeatureVisitor() {
            public void  action(AbstractAssign a, AbstractFeature outer) { a.box(outer);        }
            public Call  action(Call        c, AbstractFeature outer) { c.box(outer); return c; }
            public Expr  action(InlineArray i, AbstractFeature outer) { i.box(outer); return i; }
          });

        _state = State.BOXED;
        res.scheduleForCheckTypes1(this);
      }

    if (POSTCONDITIONS) ensure
      (_state.atLeast(State.BOXED));
  }


  /**
   * Perform type checking, in particular, verify that all redefinitions of this
   * have the argument types.  Create compile time errors if this is not the
   * case.
   */
  private void checkTypes(Resolution res)
  {
    if (PRECONDITIONS) require
      (_state.atLeast(State.CHECKING_TYPES1));

    res._module.checkTypes(this);
  }


  /**
   * Perform static type checking, i.e., make sure, that for all assignments from
   * actual to formal arguments or from values to fields, the types match.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   */
  void checkTypes1and2(Resolution res)
  {
    if (PRECONDITIONS) require
      (_state.atLeast(State.BOXED));

    _state =
      (_state == State.BOXED          ) ? State.CHECKING_TYPES1 :
      (_state == State.RESOLVED_SUGAR2) ? State.CHECKING_TYPES2 : _state;

    if ((_state == State.CHECKING_TYPES1) ||
        (_state == State.CHECKING_TYPES2)    )
      {
        visit(new FeatureVisitor() {

            /* if an error is reported in a call it might no longer make sense to check the actuals: */
            public boolean visitActualsLate() { return true; }

            public void         action(AbstractAssign a, AbstractFeature outer) { a.checkTypes(res);             }
            public Call         action(Call           c, AbstractFeature outer) { c.checkTypes(res, outer); return c; }
            public void         action(If             i, AbstractFeature outer) { i.checkTypes();                }
            public Expr         action(InlineArray    i, AbstractFeature outer) { i.checkTypes();      return i; }
            public AbstractType action(AbstractType   t, AbstractFeature outer) { return t.checkConstraints();   }
            public void         action(Cond           c, AbstractFeature outer) { c.checkTypes();                }
          });
        checkTypes(res);

        switch (_state)
          {
          case CHECKING_TYPES1: _state = State.CHECKED_TYPES1; res.scheduleForSyntacticSugar2Resolution(this); break;
          case CHECKING_TYPES2: _state = State.RESOLVED; /* end for front end! */                              break;
          }
      }

    if (POSTCONDITIONS) ensure
      (_state.atLeast(State.CHECKED_TYPES1));
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
      (Errors.any() || hasResultField() == (result != null));
    return result;
  }


  /**
   * During type resolution, record that we found an assignment to
   * resultField().
   */
  void foundAssignmentToResult()
  {
    if (PRECONDITIONS) require
      (_state == State.RESOLVING_TYPES ||
       _state == State.RESOLVED_TYPES);

    _hasAssignmentsToResult = true;
  }


  /**
   * After type resolution, this checks if an assignment tot he result variable
   * has been found.
   */
  public boolean hasAssignmentsToResult()
  {
    if (PRECONDITIONS) require
      (_state.atLeast(State.RESOLVED_TYPES));

    return _hasAssignmentsToResult;
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
      (_state.atLeast(State.CHECKED_TYPES1));

    if (_state == State.CHECKED_TYPES1)
      {
        _state = State.RESOLVING_SUGAR2;

        visit(new FeatureVisitor() {
            public Expr  action(Feature     f, AbstractFeature outer) { return new Nop(_pos);                        }
            public Expr  action(Function    f, AbstractFeature outer) { return f.resolveSyntacticSugar2(res, outer); }
            public Expr  action(InlineArray i, AbstractFeature outer) { return i.resolveSyntacticSugar2(res, outer); }
            public void  action(Impl        i, AbstractFeature outer) {        i.resolveSyntacticSugar2(res, outer); }
          });

        _state = State.RESOLVED_SUGAR2;
        res.scheduleForCheckTypes2(this);
      }

    if (POSTCONDITIONS) ensure
      (_state.atLeast(State.RESOLVED_SUGAR2));
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
    if (_impl._initialValue != null &&
        /* initial value has been replaced by explicit assignment during
         * RESOLVING_TYPES phase: */
        !outer.state().atLeast(State.RESOLVING_SUGAR1))
      {
        _impl._initialValue = _impl._initialValue.visit(v, outer);
      }
    return v.action(this, outer);
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param outer the root feature that contains this expression.
   */
  public Expr resolveTypes(Resolution res, AbstractFeature outer)
  {
    Expr result = this;

    if (CHECKS) check
      (this.outer() == outer,
        Errors.any() ||
        (_impl._kind != Impl.Kind.FieldDef    &&
         _impl._kind != Impl.Kind.FieldActual)
        || _returnType == NoType.INSTANCE);

    if (_impl._initialValue != null)
      {
        /* add assignment of initial value: */
        result = new Block
          (new List<>
           (this,
            new Assign(res, _pos, this, _impl._initialValue, outer)
            {
              public AbstractAssign visit(FeatureVisitor v, AbstractFeature outer)
              {
                /* During findFieldDefInScope, we check field uses in impl, but
                 * we have to avoid doing this again in this assignment since a declaration
                 *
                 *   x := 3
                 *   x := x + 1
                 *
                 * is converted into
                 *
                 *   Feature x with impl kind FieldDef, initialvalue 3
                 *   x := 3
                 *   Feature x with impl kind FieldDef, initialvalue x + 1
                 *   x := x + 1
                 *
                 * so the second assignment would find the second x, which is
                 * wrong.
                 *
                 * Alternatively, we could add this assignment in a later phase.
                 */
                return v.visitAssignFromFieldImpl()
                  ? super.visit(v, outer)
                  : this;
              }
            }
            ));
      }
    return result;
  }


  /**
   * Find the field whose scope includes the given call or assignment.
   *
   * @param name the name of the feature
   *
   * @param use the call, assign or destructure we are trying to resolve
   *
   * @param inner the inner feature that contains call or assign, null if
   * call/assign is part of current feature's code.
   *
   * @return in case we found a feature visible in the call's or assign's scope,
   * this is the feature.
   */
  public Feature findFieldDefInScope(String name, Expr use, AbstractFeature inner)
  {
    if (PRECONDITIONS) require
      (name != null,
       use instanceof Call ||
       use instanceof AbstractAssign ||
       use instanceof Destructure,
       inner == null || inner.outer() == this);

    // curres[0]: currently visible field with name name
    // curres[1]: result: will be set to currently visible field when call is found
    var curres = new Feature[2];
    var stack = new Stack<Feature>();

    // start by making the arguments visible:
    for (var f : _arguments)
      {
        if (f.featureName().baseName().equals(name))
          {
            curres[0] = (Feature) f;
          }
      }

    var fv = new FeatureVisitor()
      {

        /* we do not want to check assignments of initial values, see above in
         * resolveTypes() */
        boolean visitAssignFromFieldImpl() { return false; }

        void found()
        {
          if (PRECONDITIONS) require
            (curres[1] == null || curres[1] == Types.f_ERROR);

          curres[1] = curres[0];
        }

        public Call action(Call c, AbstractFeature outer)
        {
          if (c == use)
            { // Found the call, so we got the result!
              found();
            }
          else if (c.calledFeatureKnown() &&
                   c.calledFeature() instanceof Feature cf && cf.isAnonymousInnerFeature() &&
                   c.calledFeature() == inner)
            { // NYI: Special handling for anonymous inner features that currently do not appear as expressions
              found();
            }
          else if (c == Call.ERROR && curres[1] == null)
            {
              curres[1] = Types.f_ERROR;
            }
          return c;
        }
        public void action(AbstractAssign a, AbstractFeature outer)
        {
          if (a == use)
            { // Found the assign, so we got the result!
              found();
            }
        }
        public Expr action(Destructure d, AbstractFeature outer)
        {
          if (d == use)
            { // Found the assign, so we got the result!
              found();
            }
          return d;
        }
        public void actionBefore(Block b, AbstractFeature outer)
        {
          if (b._newScope)
            {
              stack.push(curres[0]);
            }
        }
        public void  actionAfter(Block b, AbstractFeature outer)
        {
          if (b._newScope)
            {
              curres[0] = stack.pop();
            }
        }
        public void actionBefore(AbstractCase c)
        {
          stack.push(curres[0]);
        }
        public void  actionAfter(AbstractCase c)
        {
          curres[0] = stack.pop();
        }
        public Expr action(Feature f, AbstractFeature outer)
        {
          if (f == inner)
            {
              found();
            }
          else
            {
              var iv = f._impl._initialValue;
              if (iv != null &&
                  outer.state().atLeast(State.RESOLVING_SUGAR1) /* iv otherwise already visited by Feature.visit(fv,outer) */)
                {
                  iv.visit(this, f);
                }
            }
          if (f.isField() && f.featureName().baseName().equals(name))
            {
              curres[0] = f;
            }
          return f;
        }
        public Expr action(Function  f, AbstractFeature outer)
        {
          if (inner != null && f._wrapper == inner)
            {
              found();
            }
          return f;
        }
      };

    for (var c : _contract.req)
      {
        c.cond.visit(fv, this);
      }

    for (var c : _contract.ens)
      {
        c.cond.visit(fv, this);
      }

    for (var p: _inherits)
      {
        p.visit(fv, this);
      }

    // then iterate the expressions making fields visible as they are declared
    // and checking which one is visible when we reach call:
    if (_impl._code != null)
      {
        _impl._code.visit(fv, this);
      }

    return curres[1];
  }


  /**
   * Is this feature an argument of its outer feature?
   */
  boolean isArgument()
  {
    if (_outer != null)
      {
        for (var a : _outer.arguments())
          {
            if (this == a)
              {
                return true;
              }
          }
      }
    return false;
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

    var g = ta.generic();
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
   * Is this feature declared in the main block of its outer feature?  Features
   * declared in inner blocks are not visible to the outside.
   */
  public boolean isDeclaredInMainBlock()
  {
    if (_outer != null)
      {
        var b = _outer.code();
        if (b instanceof Block)
          {
            for (var e : ((Block)b)._expressions)
              {
                if (e == this)
                  {
                    return true;
                  }
              }
          }
      }
    return false;
  }


  /**
   * resultTypeIfPresent returns the result type of this feature using the
   * formal generic argument.
   *
   * @return this feature's result type using the formal generics, null in
   * case the type is currently unknown (in particular, in case of a type
   * inference from a field declared later).
   */
  @Override
  AbstractType resultTypeIfPresent(Resolution res)
  {
    AbstractType result;

    if (CHECKS) check
      (state().atLeast(State.RESOLVING_TYPES));

    if (_resultType != null)
      {
        result = _resultType;
      }
    else if (outer() != null && this == outer().resultField())
      {
        result = outer().resultTypeIfPresent(res);
      }
    else if (_impl._kind == Impl.Kind.FieldDef    ||
             _impl._kind == Impl.Kind.FieldActual ||
             _impl._kind == Impl.Kind.RoutineDef)
      {
        if (CHECKS) check
          (!state().atLeast(State.TYPES_INFERENCED));
        result = _impl.inferredType(res, this);

        var from = _impl._kind == Impl.Kind.RoutineDef ? _impl._code
                                                       : _impl._initialValue;
        if (result != null &&
            !result.isGenericArgument() &&
            result.featureOfType().isTypeFeature() &&
            !(from instanceof Call c && c.calledFeature() == Types.resolved.f_Types_get))
          {
            result = Types.resolved.f_Type.selfType();
          }
      }
    else if (_returnType.isConstructorType())
      {
        result = selfType();
      }
    else if (_returnType == NoType.INSTANCE)
      {
        result = Types.resolved.t_unit; // may be the result of intrinsic or abstract feature
      }
    else
      {
        result = _returnType.functionReturnType();
      }
    if (isOuterRef())
      {
        result = result.asThis();
      }

    if (POSTCONDITIONS) ensure
      (isTypeFeaturesThisType() || selfType() == Types.resolved.t_Const_String || result != Types.resolved.t_Const_String);

    return result;
  }


  /**
   * In case this has not been resolved for types yet, do so. Next, try to
   * determine the result type of this feature. If the type is not explicit, but
   * needs to be inferenced, the result might still be null. Inferenced types
   * become available once this is in state RESOLVED_TYPES.
   *
   * @param res Resolution instance use to resolve this for types.
   *
   * @param generics the generic arguments to be applied to resultType.
   *
   * @return the result type, Types.resolved.t_void if none and null in case the
   * type must be inferenced and is not available yet.
   */
  @Override
  AbstractType resultTypeIfPresent(Resolution res, List<AbstractType> generics)
  {
    AbstractType result = Types.resolved == null ? null : Types.resolved.t_void;
    if (result != null && !_resultTypeIfPresentRecursion)
      {
        _resultTypeIfPresentRecursion = impl()._kind == Impl.Kind.FieldActual;
        if (!res.state(this).atLeast(State.RESOLVING_TYPES))
          {
            res.resolveTypes(this);
          }
        result = resultTypeIfPresent(res);
        result = result == null ? null : result.resolve(res, outer());
        result = result == null ? null : result.applyTypePars(this, generics);
        _resultTypeIfPresentRecursion = false;
      }
    return result;
  }
  boolean _resultTypeIfPresentRecursion = false;


  /**
   * After type resolution, resultType returns the result type of this
   * feature using the formal generic argument.
   *
   * @return the result type, t_ERROR in case of an error. Never null.
   */
  @Override
  public AbstractType resultType()
  {
    if (PRECONDITIONS) require
      (Errors.any() || _state.atLeast(State.RESOLVED_TYPES));

    var result = _state.atLeast(State.RESOLVED_TYPES) ? resultTypeIfPresent(null) : null;
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
  boolean hasThisType()
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
   *
   */
  private boolean definedInOwnFile = false;


  /**
   * definedInOwnFile
   *
   * @return
   */
  public boolean definedInOwnFile() {
    boolean result = definedInOwnFile;
    return result;
  }

  /**
   * setDefinedInOwnFile
   */
  public void setDefinedInOwnFile()
  {
    if (PRECONDITIONS) require
      (!definedInOwnFile);

    definedInOwnFile = true;

    if (POSTCONDITIONS) ensure
      (definedInOwnFile);
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
   * Has the frame object of this feature a ref type?
   */
  public boolean isThisRef()
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


}

/* end of file */
