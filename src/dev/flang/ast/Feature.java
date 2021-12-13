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
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;


/**
 * Feature is part of the Fuzion abstract syntax tree and represents a single
 * feature declaration.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Feature extends AbstractFeature implements Stmnt
{


  /*----------------------------  constants  ----------------------------*/


  public enum State {
    LOADING,
    FINDING_DECLARATIONS,
    LOADED,
    RESOLVING,
    RESOLVING_INHERITANCE,
    RESOLVED_INHERITANCE,
    RESOLVING_DECLARATIONS,
    RESOLVED_DECLARATIONS,
    RESOLVING_TYPES,
    RESOLVED_TYPES,
    RESOLVING_SUGAR1,
    RESOLVED_SUGAR1,
    TYPES_INFERENCING,
    TYPES_INFERENCED,
    BOXING,
    BOXED,
    CHECKING_TYPES1,
    CHECKED_TYPES1,
    RESOLVING_SUGAR2,
    RESOLVED_SUGAR2,
    CHECKING_TYPES2,
    RESOLVED,
    ERROR;
    public boolean atLeast(State s)
    {
      return this.ordinal() >= s.ordinal();
    }
  };


  /*----------------------------  variables  ----------------------------*/


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
   * The soucecode position of this feature declaration, used for error
   * messages.
   */
  private final SourcePosition _pos;


  /**
   * The soucecode position of this feature's return type, if given explicitly.
   */
  private final SourcePosition _posOfReturnType;


  /**
   * the visibility of this feature
   */
  private Visi _visibility;


  /**
   * the modifiers of this feature
   */
  public final int _modifiers;


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
  private List<String> _qname;


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
   * The formal generic arguments of this feature
   */
  private FormalGenerics _generics;
  public FormalGenerics generics() { return _generics; }


  /**
   * The formal arguments of this feature
   */
  private List<Feature> _arguments;
  public List<AbstractFeature> arguments0;
  public List<AbstractFeature> arguments()
  {
    if (arguments0 == null)
      {
        arguments0 = new List<>();
        arguments0.addAll(_arguments);
      }
    return arguments0;
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
  private Feature resultField_ = null;

  /**
   * Flag set during resolveTypes if this feature's code has at least one
   * assignment to the result field.
   */
  private boolean hasAssignmentsToResult_ = false;


  /**
   * For Features with !returnType.isConstructorType(), this will be set to the
   * result type during resolveTypes.
   */
  private AbstractType resultType_ = null;


  /**
   * Actions collectected to be executed as soon as this feature has reached
   * State.RESOLVED_TYPES, see method whenResolvedTypes().
   */
  private List<Runnable> whenResolvedTypes = new List<>();


  /**
   * Field containing reference to outer feature, set after
   * RESOLVED_DECLARATIONS.
   */
  public Feature outerRef_ = null;


  /**
   * For a Feature that can be called and hasThisType() is true, this will be
   * set to the frame type during resolution.  This type uses the formal
   * generics as actual generics. For a generic feature, these must be replaced.
   */
  private AbstractType thisType_ = null;


  /**
   * For choice feature (i.e., isChoice() holds): The tag field that holds in
   * i32 that identifies the index of the actual generic argument to choice that
   * is represented.
   *
   * This might not become part of the runtime clazz if isChoiceOfOnlyRefs()
   * holds for that classs.
   */
  public Feature choiceTag_ = null;
  public AbstractFeature choiceTag() { return choiceTag_; }


  /**
   * Is this a loop's index variable that is automatically updated by the loops
   * for-clause.  If so, assignments outside the loop prolog or nextIteration
   * parts are not allowed.
   */
  boolean _isIndexVarUpdatedByLoop = false;
  public boolean isIndexVarUpdatedByLoop() { return _isIndexVarUpdatedByLoop; }


  /**
   * The source module this is defined in.  This is set when this is first
   * scheduled for resolution.
   */
  SrcModule _module = null;


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
         Consts.VISIBILITY_PUBLIC,
         0,
         ValueType.INSTANCE,
         new List<String>(FuzionConstants.UNIVERSE_NAME),
         FormalGenerics.NONE,
         new List<Feature>(),
         new List<>(),
         Contract.EMPTY_CONTRACT,
         new Impl(SourcePosition.builtIn,
                  new Block(SourcePosition.builtIn,
                            new List<Stmnt>()),
                  Impl.Kind.Routine));
  }


  /**
   * Class for the Universe Feature.
   */
  static class Universe extends Feature
  {
    Universe()
    {
    }
    public boolean isUniverse()
    {
      check
        (this.outer() == null);
      return true;
    }
  }

  /**
   * Constructor for universe
   */
  public static Feature createUniverse()
  {
    return new Universe();
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
  }


  /**
   * Create an anonymous feature
   *
   * @param pos the soucecode position, used for error messages.
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
                       Consts.VISIBILITY_INVISIBLE,
                       0,
                       r,
                       new List<String>(FuzionConstants.ANONYMOUS_FEATURE_PREFIX + (uniqueAnonymousFeatureId++)),
                       FormalGenerics.NONE,
                       new List<Feature>(),
                       i,
                       c,
                       new Impl(b.pos, b, Impl.Kind.Routine))
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
   * @param pos the soucecode position, used for error messages.
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
          Feature outer)
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
   * @param pos the soucecode position, used for error messages.
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
   * @param pos the soucecode position, used for error messages.
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
          Expr initialValue,
          Feature outerOfInitialValue)
  {
    this(pos,
         v,
         0,
         t == null ? NoType.INSTANCE : new FunctionReturnType(t), /* NYI: try to avoid creation of ReturnType here, set actualtype directly? */
         new List<String>(qname),
         FormalGenerics.NONE,
         new List<Feature>(),
         new List<>(),
         null,
         initialValue == null ? Impl.FIELD
                              : new Impl(pos, initialValue, outerOfInitialValue));

    if (PRECONDITIONS) require
                         ((t == null) == (initialValue != null));
  }


  /**
   * Constructor for argument features
   *
   * @param pos the soucecode position, used for error messages.
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
    this(pos,
         v,
         m,
         new FunctionReturnType(t), /* NYI: try to avoid creation of ReturnType here, set actualtype directly? */
         new List<String>(n),
         FormalGenerics.NONE,
         new List<Feature>(),
         new List<>(),
         c,
         Impl.FIELD);
  }


  /**
   * Quick-and-dirty way to generate unique names for anonymous inner features
   * declared for inline functions.
   */
  static long uniqueFunctionId = 0;

  /**
   * Constructor for function features
   *
   * @param pos the soucecode position, used for error messages.
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
          List<Feature> a,
          List<AbstractCall> i,
          Contract c,
          Impl     p)
  {
    this(pos,
         Consts.VISIBILITY_INVISIBLE,
         0,
         r,
         qname,
         FormalGenerics.NONE,
         a,
         i,
         c,
         p);
  }


  /**
   * Constructor
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param v the visibility
   *
   * @param m the modifiers
   *
   * @param r the result type
   *
   * @param qname the name of this feature
   *
   * @param g the generic parameters
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
                 FormalGenerics g,
                 List<Feature> a,
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
    this._posOfReturnType = r == NoType.INSTANCE || r.isConstructorType() ? pos : r.functionReturnType().pos();
    String n = qname.getLast();
    if (n.equals("_"))
      {
        // NYI: Check that this feature is allowed to have this name, i.e., it
        // is declared in a Destructure statement.
        n = "#_"+ underscoreId++;
      }
    this._qname     = qname;
    this._generics  = g;
    this._arguments = a;
    this._featureName = FeatureName.get(n, a.size());
    this._inherits   = (i.isEmpty() &&
                        (p.kind_ != Impl.Kind.FieldActual) &&
                        (p.kind_ != Impl.Kind.FieldDef   ) &&
                        (p.kind_ != Impl.Kind.FieldInit  ) &&
                        (p.kind_ != Impl.Kind.Field      ) &&
                        (qname.size() != 1 || (!qname.getFirst().equals(FuzionConstants.OBJECT_NAME  ) &&
                                               !qname.getFirst().equals(FuzionConstants.UNIVERSE_NAME))))
      ? new List<>(new Call(_pos, FuzionConstants.OBJECT_NAME, Expr.NO_EXPRS))
      : i;

    this._contract  = c;
    this._impl = p;

    g.setFeature(this);
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
      (newState.ordinal() == _state.ordinal() + 1);

    this._state = newState;
  }


  /**
   * NYI: HACK: universe is currently resolved twice, once as part of stdlib, and then as part of another module
   */
  public void resetState()
  {
    if (PRECONDITIONS) require
      (isUniverse());
    _state = Feature.State.LOADING;
  }


  /**
   * Add this feature to the given ource module.  This is called when this is
   * first scheduled for resolution.
   */
  void addTo(SrcModule m)
  {
    if (PRECONDITIONS) require
      (_module == null || _module == m || isUniverse());

    _module = m;
    m.add(this);
  }


  /**
   * The soucecode position of this statment, used for error messages.
   */
  public SourcePosition pos()
  {
    return _pos;
  }


  /**
   * Compare the fully qualified name of feature t (not of 'this'!) with the
   * names provided by the iterator, i.e., return true iff it returns "fuzion",
   * "std", "out", etc. and this feature is fuzion.std.out.
   *
   * @param t the feature to check.
   *
   * @param it an iterator producing the elements of a fully qualified name
   *
   * @return true if this features's fully qualified names is a prefix of the
   * names produced by it.
   */
  private boolean checkNames(AbstractFeature t, Iterator<String> it)
  {
    return
      t.isUniverse() ||
      checkNames(t.outer(), it) &&
      it.hasNext() &&
      it.next().equals(t.featureName().baseName());
  }


  /**
   * Check that the fully qualified name matches the _outer feature(s) using
   * checkNames(). If not, show a corresponding error.
   */
  public void checkName()
  {
    if (_qname.size() > 1)
      {
        Iterator<String> it = _qname.iterator();
        if (!checkNames(this, it) || it.hasNext())
          {
            AstErrors.declaredInWrongEnv(_pos, _qname, _outer);
          }
      }
    if (!isResultField() && _qname.getLast().equals(FuzionConstants.RESULT_NAME))
      {
        AstErrors.declarationOfResultFeature(_pos);
      }
  }


  /**
   * Get the outer feature of this feature, or null if this is the universe.
   *
   * The outer is set during FIND_DECLARATIONS, so this cannot be called before
   * the find declarations phase is done (i.e. we are in Satet.LOADED), or
   * before _outer was during the finding declarations phase.
   */
  public AbstractFeature outer()
  {
    if (PRECONDITIONS) require
      (state().atLeast(State.FINDING_DECLARATIONS));

    return _outer;
  }


  /**
   * Has the outer feature for this feature been set?  This is always the case
   * after phase LOADING, so this may only be called during phase LOADING.
   */
  public boolean outerSet()
  {
    if (PRECONDITIONS) require
      (state() == Feature.State.LOADING);

    return _outer != null;
  }

  /**
   * Set outer feature for this feature. Has to be done during phase LOADING.
   */
  public void setOuter(AbstractFeature outer)
  {
    if (PRECONDITIONS) require
      (state() == Feature.State.LOADING,
       !outerSet());

    this._outer = outer;
  }


  /**
   * get a reference to the outermost feature.
   */
  public AbstractFeature universe()
  {
    if (PRECONDITIONS) require
      (state().atLeast(State.LOADED));

    AbstractFeature r = this;
    while (!r.isUniverse())
      {
        r = r.outer();
      }
    return r;
  }


  /**
   * What is this Feature's kind?
   *
   * @return Routine, Field, Intrinsic, Abstract or Choice.
   */
  public Kind kind()
  {
    return state().atLeast(State.RESOLVING_TYPES) && isChoiceAfterTypesResolved()
      ? Kind.Choice
      : switch (_impl.kind_) {
          case FieldInit, FieldDef, FieldActual, FieldIter, Field -> Kind.Field;
          case Routine, RoutineDef                                -> Kind.Routine;
          case Abstract                                           -> Kind.Abstract;
          case Intrinsic                                          -> Kind.Intrinsic;
        };
  }


  /**
   * get the kind of this feature.
   */
  public Impl.Kind implKind()
  {
    return _impl.kind_;
  }

  /**
   * get the initial value of this feature.
   */
  public Expr initialValue()
  {
    // if (PRECONDITIONS) require
    //  (switch (implKind()) { case FieldInit, FieldDef, FieldActual, FieldIter -> true; default -> false; });

    return
      switch (implKind())
        {
        case FieldInit, FieldDef, FieldActual, FieldIter -> _impl._initialValue;
        default -> null;
        };
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
    return isField() && _featureName.baseName().startsWith("#");
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
        var t = _impl.kind_ == Impl.Kind.Routine
          ? _returnType.functionReturnType()
          : Types.t_UNDEFINED /* dummy type, will be replaced during TYPES_INFERENCING phase */;

        check
          (resultField_ == null);
        resultField_ = new Feature(res,
                                   _pos,
                                   Consts.VISIBILITY_PRIVATE,
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
   * not accessible by source code.  This is true for routines defined usings
   * '=>" (RoutineDef) and also for internally used routines created for loops.
   * In these cases, the result variable of the enclosing outer feature can be
   * accessed without qualification.
   */
  public boolean resultInternal()
  {
    return _impl.kind_ == Impl.Kind.RoutineDef;
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
       Errors.count() > 0);

    if (this == Types.resolved.f_choice)
      { // if this == choice, there are only formal generics, so nothing to erase
      }
    else
      {
        for (var p: _inherits)
          {
            check
              (Errors.count() > 0 || p.calledFeature() != null);

            if (p.calledFeature() == Types.resolved.f_choice)
              {
                if (p instanceof Call cp)
                  {
                    cp.generics = new List<AbstractType>(Types.t_ERROR);
                  }
              }
          }
      }
  }


  /**
   * Is this a choice-type, i.e., does it directly inherit from choice?
   */
  boolean isChoiceAfterTypesResolved()
  {
    return choiceGenerics() != null;
  }


  /**
   * Is this a tag field created for a choice-type?
   */
  boolean isChoiceTag()
  {
    return false;
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
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   */
  public void visit(FeatureVisitor v)
  {
    _generics.visit(v, this);
    for (var c: _inherits)
      {
        Expr nc = c.visit(v, this);
        check
          (c == nc); // NYI: This will fail when doing funny stuff like inherit from bool.infix &&, need to check and handle explicitly
      }
    if (_contract != null)
      {
        _contract.visit(v, this);
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
  boolean isLastArgType(Type t)
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
    String inh = "    inherits " + parent.qualifiedName() + " at " + p.pos.show() + "\n";
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
      { // mark all member of the cycl
        cyclicInhData.add(": feature " + qualifiedName()+" at " + _pos.show() + "\n" + inh);
        _detectedCyclicInheritance = true;
      }

    // try to fix recursive inheritance to keep compiler from crashing
    i.set(new Call(_pos, FuzionConstants.OBJECT_NAME, Expr.NO_EXPRS));
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

        check
          ((_outer == null) || _outer.state().atLeast(State.RESOLVING));

        var i = _inherits.listIterator();
        while (i.hasNext() && !_detectedCyclicInheritance)
          {
            var p = i.next();
            p.loadCalledFeature(res, this);
            if (p instanceof Call cp)
              {
                cp.isInheritanceCall_ = true;
              }
            var parent = p.calledFeature();
            check
              (Errors.count() > 0 || parent != null);
            if (parent instanceof Feature fp)
              {
                fp.resolveInheritance(res);
                if (fp.detectedCyclicInheritance())
                  {
                    cyclicInheritanceError(p, i);
                  }
              }
          }
        _state = State.RESOLVED_INHERITANCE;
        res.scheduleForDeclarations(this);
      }

    if (POSTCONDITIONS) ensure
      (_detectedCyclicInheritance || _state.atLeast(State.RESOLVED_INHERITANCE));
  }

  static FeatureVisitor findGenerics = new FeatureVisitor()
    {
      public Function     action(Function     f, Feature outer) { f.findGenerics(this, outer); return f; }
      public This         action(This         t, Feature outer) { t.findGenerics(      outer); return t; }
      public AbstractType action(AbstractType t, Feature outer) { t.findGenerics(      outer); return t; }
    };

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

        check
          (_state == State.RESOLVING_DECLARATIONS);

        this._returnType = _impl.checkReturnType(this);
        res._module.findDeclaredOrInheritedFeatures(this);

        check
          (_state.atLeast(State.RESOLVING_DECLARATIONS));

        if (_state == State.RESOLVING_DECLARATIONS)
          {
            /**
             * Find all the types used in this that refer to formal generic arguments of
             * this or any of this' outer classes.
             */
            visit(findGenerics);
          }

        _state = State.RESOLVED_DECLARATIONS;
        res.scheduleForTypeResolution(this);
      }

    if (POSTCONDITIONS) ensure
      (_state.atLeast(State.RESOLVED_DECLARATIONS));
  }


  static class ResolveTypes extends FeatureVisitor
  {
    Resolution res;
    ResolveTypes(Resolution r)
      {
        res = r;
      }
    public void         action(Assign       a, Feature outer) {        a.resolveTypes(res, outer); }
    public Call         action(Call         c, Feature outer) { return c.resolveTypes(res, outer); }
    public Stmnt        action(Destructure  d, Feature outer) { return d.resolveTypes(res, outer); }
    public Stmnt        action(Feature      f, Feature outer) { return f.resolveTypes(res, outer); }
    public Function     action(Function     f, Feature outer) {        f.resolveTypes(res, outer); return f; }
    public void         action(Generic      g, Feature outer) {        g.resolveTypes(res, outer); }
    public void         action(Match        m, Feature outer) {        m.resolveTypes(res, outer); }
    public Expr         action(This         t, Feature outer) { return t.resolveTypes(res, outer); }
    public AbstractType action(AbstractType t, Feature outer) { return t.resolve     (res, outer); }

    /**
     * visitActuals delays type resolution for actual arguments within a feature
     * until the feature's type was resolved.  The reason is that the feature's
     * type does not depend on the actual arguments, but the actual arguments
     * might depend directly or indirectly on the feature's type.
     */
    void visitActuals(Runnable r, Feature outer)
    {
      outer.whenResolvedTypes.add(r);
    }
  }


  /**
   * Type resolution for a feature f: For all expressions and statements in f's
   * inheritance clause, contract, and implementation, determine the static type
   * of the expression. Were needed, perform type inference. Schedule f for
   * syntactic sugar resolution.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   */
  void resolveTypes(Resolution res)
  {
    if (PRECONDITIONS) require
      (_state.atLeast(State.RESOLVED_DECLARATIONS));

    if (_state == State.RESOLVED_DECLARATIONS)
      {
        _state = State.RESOLVING_TYPES;

        visit(new ResolveTypes(res));

        if (hasThisType())
          {
            var tt = thisType();
            thisType_ = tt.resolve(res, this);
          }

        if ((_impl.kind_ == Impl.Kind.FieldActual) && (_impl._initialValue.typeOrNull() == null))
          {
            _impl._initialValue.visit(new ResolveTypes(res),
                                     true /* NYI: impl_outerOfInitialValue not set yet */
                                     ? (Feature) outer().outer()  /* NYI: Cast! */:
                                     _impl._outerOfInitialValue);
          }

        _state = State.RESOLVED_TYPES;
        while (!whenResolvedTypes.isEmpty())
          {
            whenResolvedTypes.removeFirst().run();
          }
        res.scheduleForSyntacticSugar1Resolution(this);
      }

    if (POSTCONDITIONS) ensure
      (_state.atLeast(State.RESOLVED_TYPES));
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
   * safety, debugLevel, debug.
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

        visit(new FeatureVisitor()
          {
            public Expr action(Call c, Feature outer) { return c.resolveSyntacticSugar(res, outer); }
          });

        _state = State.RESOLVED_SUGAR1;
        res.scheduleForTypeInteference(this);
      }

    if (POSTCONDITIONS) ensure
      (_state.atLeast(State.RESOLVED_SUGAR1));
  }


  /**
   * Find list of all accesses to this feature's closure by any of its inner
   * features.
   */
  private List<Call> closureAccesses(Resolution res)
  {
    List<Call> result = new List<>();
    for (AbstractFeature af : res._module.declaredOrInheritedFeatures(this).values())
      {
        var f = (Feature) af.astFeature();
        f.visit(new FeatureVisitor()
          {
            public Call action(Call c, Feature outer)
            {
              if (c.calledFeature() == outerRef())
                {
                  result.add(c);
                }
              return c;
            }
          });
      }
    return result;
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
    List<Call> closureAccesses = closureAccesses(res);
    if (!closureAccesses.isEmpty())
      {
        StringBuilder accesses = new StringBuilder();
        for (Call c: closureAccesses)
          {
            accesses.append(c.pos.show()).append("\n");
          }
        AstErrors.choiceMustNotAccessSurroundingScope(errorPos, accesses.toString());
      }
  }


  /**
   * Does this statement consist of nothing but declarations? I.e., it has no
   * code that actually would be executed at runtime.
   */
  public boolean containsOnlyDeclarations()
  {
    boolean result = true;
    switch (_impl.kind_)
      {
      case FieldInit:    // a field with initialization syntactic sugar
      case FieldDef:     // a field with implicit type
        result = false;
      case Field:        // a field
      case FieldActual:  // a field with implicit type taken from actual argument to call
      case RoutineDef:   // normal feature with code and implicit result type
      case Routine:      // normal feature with code
        result = true;
      }
    return result;
  };


  /**
   * For a choice feature, perform compile time checks for validity and add
   * internal fields for the type tag and the values.
   *
   * Due to bootstrapping, this cannot be performed during resolveTypes, so it
   * is part of the typeInference pass.
   *
   *
   * @param res this is called during type inteference, res gives the resolution
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
        if (p.isField() && !p.isOuterRef() && !p.isChoiceTag())
          {
            AstErrors.choiceMustNotContainFields(_pos,p);
          }
      }
    // choice type must not contain any code, but may contain inner features
    switch (_impl.kind_)
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
        if (!t.isRef())
          {
            if (t == thisType())
              {
                AstErrors.choiceMustNotReferToOwnValueType(_pos, t);
                thisType_ = Types.t_ERROR;
                eraseChoiceGenerics();
              }
            var o = outer();
            while (o != null)
              {
                if (t == o.thisType())
                  {
                    AstErrors.choiceMustNotReferToOuterValueType(_pos, t);
                    // o.thisType_ = Types.t_ERROR;  NYI: Do we need this?
                    eraseChoiceGenerics();
                  }
                o = o.outer();
              }
          }
      }

    thisType().checkChoice(_pos);

    checkNoClosureAccesses(res, _pos);
    for (var p : _inherits)
      {
        p.calledFeature().checkNoClosureAccesses(res, p.pos);
      }

    choiceTag_ = new Feature(res,
                             _pos,
                             Consts.VISIBILITY_PRIVATE,
                             Types.resolved.t_i32,
                             FuzionConstants.CHOICE_TAG_NAME,
                             this)
      {
        /**
         * Is this a tag field created for a choice-type?
         */
        boolean isChoiceTag()
        {
          return true;
        }
      };
    choiceTag_.scheduleForResolution(res);
  }


  /**
   * Choice related checks: Check if this inherits from a choice and flag an
   * error if this is the case.
   *
   * Check if this is a choice and if so, call checkChoiceAndAddInternalFields
   * for further checking and adding of fields.
   *
   * @param res this is called during type inteference, res gives the resolution
   * instance to schedule new fields for resolution.
   */
  void choiceTypeCheckAndInternalFields(Resolution res)
  {
    for (var p : _inherits)
      {
        // choice type is leaf
        var cf = p.calledFeature();
        check
          (Errors.count() > 0 || cf != null);

        if (cf != null && cf.isChoice() && !cf.sameAs(Types.resolved.f_choice))
          {
            AstErrors.cannotInheritFromChoice(p.pos);
          }
      }
    if (isChoice())
      {
        checkChoiceAndAddInternalFields(res);
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

        check
          (resultType_ == null
           || isUniverse() // NYI: HACK: universe is currently resolved twice, once as part of stdlib, and then as part of another module
           );

        if (outer() instanceof Feature o)
          {
            o.typeInference(res);
          }
        choiceTypeCheckAndInternalFields(res);

        resultType_ = resultType();
        if (resultType_ instanceof Type t)
          {
            t.checkChoice(_posOfReturnType);
          }

        /**
         * Perform type inference from outside to the inside, i.e., propage the
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
            public void  action(Assign   a, Feature outer) { a.propagateExpectedType(res, outer); }
            public Call  action(Call     c, Feature outer) { c.propagateExpectedType(res, outer); return c; }
            public void  action(Cond     c, Feature outer) { c.propagateExpectedType(res, outer); }
            public void  action(Impl     i, Feature outer) { i.propagateExpectedType(res, outer); }
            public void  action(If       i, Feature outer) { i.propagateExpectedType(res, outer); }
          });

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
            public void  action(Assign    a, Feature outer) { a.box(outer);           }
            public Call  action(Call      c, Feature outer) { c.box(outer); return c; }
            public Expr  action(InlineArray i, Feature outer) { i.box(outer); return i; }
          });

        _state = State.BOXED;
        res.scheduleForCheckTypes1(this);
      }

    if (POSTCONDITIONS) ensure
      (_state.atLeast(State.BOXED));
  }


  /**
   * Get the actual type from a type used in this feature after it was inherited
   * by heir.  During inheritance, formal generics may be replaced by actual
   * generics.
   *
   * @param t a type used in this feature, must not be an open generic type
   * (which can be replaced by several types during inheritance).
   *
   * @param heir a heir of this, might be equal to this.
   *
   * @return interned type that represents t seen as it is seen from heir.
   */
  public AbstractType handDownNonOpen(Resolution res, AbstractType t, AbstractFeature heir)
  {
    if (PRECONDITIONS) require
      (!t.isOpenGeneric(),
       heir != null,
       _state.atLeast(State.CHECKING_TYPES1));

    var a = handDown(res, new AbstractType[] { t }, heir);

    check
      (Errors.count() > 0 || a.length == 1);

    return a.length == 1 ? a[0] : Types.t_ERROR;
  }


  /**
   * Perform type checking, in particular, verify that all redefinitions of this
   * have the argument types.  Create compile time erros if this is not the
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
            public void  action(Assign    a, Feature outer) { a.checkTypes(res);             }
            public Call  action(Call      c, Feature outer) { c.checkTypes(outer); return c; }
            public void  action(If        i, Feature outer) { i.checkTypes();                }
            public Expr  action(InlineArray i, Feature outer) { i.checkTypes();      return i; }
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

    Feature result = resultField_;

    if (POSTCONDITIONS) ensure
      (Errors.count() > 0 || hasResultField() == (result != null));
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

    hasAssignmentsToResult_ = true;
  }


  /**
   * After type resolution, this checks if an assignment tot he result variable
   * has been found.
   */
  public boolean hasAssignmentsToResult()
  {
    if (PRECONDITIONS) require
      (_state.atLeast(State.RESOLVED_TYPES));

    return hasAssignmentsToResult_;
  }


  /**
   * Syntactic sugar resolution of a feature f: For all expressions and
   * statements in f's inheritance clause, contract, and implementation, resolve
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
            public Stmnt action(Feature   f, Feature outer) { return new Nop(_pos);                         }
            public Expr  action(Function  f, Feature outer) { return f.resolveSyntacticSugar2(res, outer); }
            public Expr  action(InlineArray i, Feature outer) { return i.resolveSyntacticSugar2(res, outer); }
            public void  action(Impl      i, Feature outer) {        i.resolveSyntacticSugar2(res, outer); }
          });

        _state = State.RESOLVED_SUGAR2;
        res.scheduleForCheckTypes2(this);
      }

    if (POSTCONDITIONS) ensure
      (_state.atLeast(State.RESOLVED_SUGAR2));
  }


  /**
   * Get inner feature with given name.
   *
   * @param name the name of the feature within this.
   *
   * @return the found feature or null in case of an error.
   */
  public AbstractFeature get(String name)
  {
    return get(name, -1);
  }


  /**
   * Get inner feature with given name and argCount.
   *
   * @param name the name of the feature within this.
   *
   * @param argcount the number of arguments, -1 if not specified.
   *
   * @return the found feature or Types.f_ERROR in case of an error.
   */
  public AbstractFeature get(String name, int argcount)
  {
    AbstractFeature result = Types.f_ERROR;
    var d = _module.declaredFeatures(this);
    var set = (argcount >= 0
               ? FeatureName.getAll(d, name, argcount)
               : FeatureName.getAll(d, name          )).values();
    if (set.size() == 1)
      {
        for (var f2 : set)
          {
            result = f2;
          }
      }
    else if (set.isEmpty())
      {
        AstErrors.internallyReferencedFeatureNotFound(_pos, name, this, name);
      }
    else
      { // NYI: This might happen if the user adds additional features
        // with different argCounts. name should contain argCount to
        // avoid this
        AstErrors.internallyReferencedFeatureNotUnique(_pos, name + (argcount >= 0 ? " (" + Errors.argumentsString(argcount) : ""), set);
      }
    return result;
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this.
   */
  public Stmnt visit(FeatureVisitor v, Feature outer)
  {
    check
      (!this._state.atLeast(State.LOADED) || this.outer() == outer);

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
   * @param outer the root feature that contains this statement.
   */
  public Stmnt resolveTypes(Resolution res, Feature outer)
  {
    Stmnt result = this;

    check
      (this.outer() == outer);

    if (_impl.kind_ == Impl.Kind.FieldDef    ||
        _impl.kind_ == Impl.Kind.FieldActual    )
      {
        if ((_returnType != NoType.INSTANCE))
          {
            AstErrors.fieldDefMustNotHaveType(_pos, this, _returnType, _impl._initialValue);
          }
      }
    if (_impl.kind_ == Impl.Kind.RoutineDef)
      {
        if ((_returnType != NoType.INSTANCE))
          {
            AstErrors.routineDefMustNotHaveType(_pos, this, _returnType, _impl._code);
          }
      }
    if (_impl._initialValue != null)
      {
        /* add assignment of initial value: */
        result = new Block
          (_pos, new List<>
           (this,
            new Assign(res, _pos, this, _impl._initialValue, outer)
            {
              public Assign visit(FeatureVisitor v, Feature outer)
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
   * @param call the call we are trying to resolve, or null when not resolving a
   * call.
   *
   * @param assign the assign we are trying to resolve, or null when not resolving an
   * assign
   *
   * @param destructure the destructure we are strying to resolve, or null when not
   * resolving a destructure.
   *
   * @param inner the inner feature that contains call or assign, null if
   * call/assign is part of current feature's code.
   *
   * @return in case we found a feature visible in the call's or assign's scope,
   * this is the feature.
   */
  public Feature findFieldDefInScope(String name, Call call, Assign assign, Destructure destructure, AbstractFeature inner)
  {
    if (PRECONDITIONS) require
      (name != null,
       call != null && assign == null && destructure == null ||
       call == null && assign != null && destructure == null ||
       call == null && assign == null && destructure != null,
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
            curres[0] = f;
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
            (curres[1] == null);

          curres[1] = curres[0];
        }

        public Call action(Call c, Feature outer)
        {
          if (c == call)
            { // Found the call, so we got the result!
              found();
            }
          else
            {
              // NYI: Special handling for anonymous inner features that currently do not appear as statements
              if (c.calledFeatureKnown() &&
                  c.calledFeature() instanceof Feature cf && cf.isAnonymousInnerFeature() &&
                  c.calledFeature() == inner)
                {
                  found();
                }
            }
          return c;
        }
        public void action(Assign a, Feature outer)
        {
          if (a == assign)
            { // Found the assign, so we got the result!
              found();
            }
        }
        public Stmnt action(Destructure d, Feature outer)
        {
          if (d == destructure)
            { // Found the assign, so we got the result!
              found();
            }
          return d;
        }
        public void actionBefore(Block b, Feature outer)
        {
          if (b._newScope)
            {
              stack.push(curres[0]);
            }
        }
        public void  actionAfter(Block b, Feature outer)
        {
          if (b._newScope)
            {
              curres[0] = stack.pop();
            }
        }
        public void actionBefore(Case c, Feature outer)
        {
          stack.push(curres[0]);
        }
        public void  actionAfter(Case c, Feature outer)
        {
          curres[0] = stack.pop();
        }
        public Stmnt action(Feature f, Feature outer)
        {
          if (f.isField() && f.featureName().baseName().equals(name))
            {
              curres[0] = f;
            }
          if (f == inner)
            {
              found();
            }
          return f;
        }
        public Expr action(Function  f, Feature outer)
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

    // then iterate the statements making fields visible as they are declared
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
            for (var s : ((Block)b).statements_)
              {
                if (s == this)
                  {
                    return true;
                  }
              }
          }
      }
    return false;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return
      _visibility+" "+
      Consts.modifierToString(_modifiers)+
      _returnType + " "+
      _featureName.baseName()+
      _generics+
      (_arguments.isEmpty() ? "" : "("+_arguments+")")+
      (_inherits.isEmpty() ? "" : " : "+_inherits)+
      _contract+
      _impl.toString();
  }


  /**
   * resultTypeRaw returns the result type of this feature using the
   * formal generic argument.
   *
   * @return this feature's result type using the formal generics, null in
   * case the type is currently unknown (in particular, in case of a type
   * inference from a field declared later).
   */
  private AbstractType resultTypeRaw()
  {
    AbstractType result;

    check
      (state().atLeast(State.RESOLVING_TYPES));

    if (resultType_ != null)
      {
        result = resultType_;
      }
    else if (outer() != null && this == outer().resultField())
      {
        result = (outer() instanceof Feature of) ? of.resultTypeRaw() : outer().resultType();
      }
    else if (_impl.kind_ == Impl.Kind.FieldDef ||
             _impl.kind_ == Impl.Kind.FieldActual)
      {
        check
          (!state().atLeast(State.TYPES_INFERENCED));
        result = _impl._initialValue.typeOrNull();
      }
    else if (_impl.kind_ == Impl.Kind.RoutineDef)
      {
        check
          (!state().atLeast(State.TYPES_INFERENCED));
        result = _impl._code.typeOrNull();
      }
    else if (_returnType.isConstructorType())
      {
        result = thisType();
      }
    else if (_returnType == NoType.INSTANCE)
      {
        result = Types.resolved.t_unit; // may be the result of intrinsic or abstract feature
      }
    else
      {
        result = _returnType.functionReturnType();
      }

    return result;
  }


  /**
   * resultTypeRaw returns the result type of this feature with given
   * actual generics applied.
   *
   * @param generics the actual generic arguments to create the type, or null if
   * generics should not be replaced.
   *
   * @return this feature's result type using the given actual generics, null in
   * case the type is currently unknown (in particular, in case of a type
   * inference to a field declared later).
   */
  private AbstractType resultTypeRaw(List<AbstractType> actualGenerics)
  {
    check
      (state().atLeast(State.RESOLVING_TYPES));

    var result = resultTypeRaw();
    if (result != null)
      {
        result = result.actualType(this, actualGenerics);
      }

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
   * @param generics the generics argument to be passed to resultTypeRaw
   *
   * @return the result type, Types.resulved.t_unit if none and null in case the
   * type must be inferenced and is not available yet.
   */
  AbstractType resultTypeIfPresent(Resolution res, List<AbstractType> generics)
  {
    if (!_state.atLeast(State.RESOLVING_TYPES))
      {
        res.resolveDeclarations(this);
        resolveTypes(res);
      }
    var result = resultTypeRaw(generics);
    if (result != null && result instanceof Type rt)
      {
        rt.findGenerics(outer());
      }
    return result;
  }


  /**
   * After type resolution, resultType returns the result type of this
   * feature using the formal generic argument.
   *
   * @return the result type, t_ERROR in case of an error. Never null.
   */
  public AbstractType resultType()
  {
    if (PRECONDITIONS) require
      (Errors.count() > 0 || _state.atLeast(State.RESOLVED_TYPES));

    var result = _state.atLeast(State.RESOLVED_TYPES) ? resultTypeRaw() : null;
    if (result == null)
      {
        check
          (Errors.count() > 0);

        result = Types.t_ERROR;
      }

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * In case this has not been resolved for types yet, do so. Next, try to
   * determine the result type of this feature. If the type is not explicit, but
   * needs to be inferred, but it could not be inferred, cause a runtime
   * error since we apparently have a cyclic dependencies for type inference.
   *
   * @param rpos the source code position to be used for error reporting
   *
   * @param res Resolution instance use to resolve this for types.
   *
   * @param generics the actual generic arguments to be applied to the type
   *
   * @return the result type, Types.resulved.t_unit if none and
   * Types.t_ERROR in case the type could not be inferenced and error
   * was reported.
   */
  AbstractType resultTypeForTypeInference(SourcePosition rpos, Resolution res, List<AbstractType> generics)
  {
    var result = resultTypeIfPresent(res, generics);
    if (result == null)
      {
        AstErrors.forwardTypeInference(rpos, this, _pos);
        result = Types.t_ERROR;
      }
    return result;
  }


  /**
   * thisType returns the type of this feature's frame object.  This can be
   * called even if !hasThisType() since thisClazz() is used also for abstract
   * or intrinsic feature to determine the resultClazz().
   *
   * @return this feature's frame object
   */
  public AbstractType thisType()
  {
    if (PRECONDITIONS) require
      (_state.atLeast(State.FINDING_DECLARATIONS));

    AbstractType result = thisType_;
    if (result == null)
      {
        result = this == Types.f_ERROR
          ? Types.t_ERROR
          : new Type(_pos, _featureName.baseName(), _generics.asActuals(), null, this, Type.RefOrVal.LikeUnderlyingFeature);
        thisType_ = result;
      }
    if (_state.atLeast(State.RESOLVED_TYPES))
      {
        result = Types.intern(result);
      }

    if (POSTCONDITIONS) ensure
      (result != null,
       Errors.count() > 0 || result.isRef() == isThisRef(),
       // does not hold if feature is declared repeatedly
       Errors.count() > 0 || result.featureOfType() == this,
       true || // this condition is very expensive to check and obviously true:
       !_state.atLeast(State.RESOLVED_TYPES) || result == Types.intern(result)
       );

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
    return _impl != Impl.INTRINSIC && _impl != Impl.ABSTRACT
      && !isField();
  }


  public FeatureName featureName()
  {
    check(_arguments.size() == _featureName.argCount());
    return _featureName;
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
      !state().atLeast(State.RESOLVING_DECLARATIONS) && _impl.kind_ == Impl.Kind.Routine && _returnType == NoType.INSTANCE;
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
      (this._outer != null,
       _state == State.FINDING_DECLARATIONS);

    var o = this._outer;
    if (_impl._code != null || _contract != null)
      {
        var outerRefType = isOuterRefAdrOfValue() ? Types.t_ADDRESS
                                                  : o.thisType();
        outerRef_ = new Feature(res,
                                _pos,
                                Consts.VISIBILITY_PRIVATE,
                                outerRefType,
                                outerRefName(),
                                this)
          {
            public boolean isOuterRef()
            {
              return true;
            }
          };
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
      (isUniverse() || (this == Types.f_ERROR) || outer() != null,
       (this == Types.f_ERROR) ||
       _state.atLeast(State.RESOLVED_DECLARATIONS) &&
       (!_state.atLeast(State.CHECKING_TYPES2) || outerRef_ != null || isField() || isUniverse()));

    Feature result = outerRef_;

    if (POSTCONDITIONS) ensure
      (isField() || isUniverse() || (this == Types.f_ERROR) || result != null);

    return result;
  }


  /**
   * Check if this is an outer ref field.
   */
  public boolean isOuterRef()
  {
    return false;
  }

}

/* end of file */
