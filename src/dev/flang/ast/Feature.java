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
import java.util.Arrays;
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
 * Feature <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Feature extends ANY implements Stmnt, Comparable<Feature>
{


  /*----------------------------  constants  ----------------------------*/


  static final String UNIVERSE_NAME        = "#universe";
  static final String OBJECT_NAME          = "Object";
  static final String RESULT_NAME          = "result";
  static final String INTERNAL_RESULT_NAME = "#result";


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
    CHECKED_TYPES2,
    FINDING_USED_FEATURES,
    FOUND_USED_FEATURES,
    RESOLVING_FEATURE_INDEX,
    RESOLVED_FEATURE_INDEX,
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
  private State state_ = State.LOADING;


  /**
   * Set during RESOLVING_INHERITANCE in case this is part of a cyclic
   * inheritance.
   */
  private boolean detectedCyclicInheritance = false;


  /**
   * The soucecode position of this feature declaration, used for error
   * messages.
   */
  public final SourcePosition pos;


  /**
   * The soucecode position of this feature's return type, if given explicitly.
   */
  public final SourcePosition posOfReturnType_;


  /**
   * the visibility of this feature
   */
  Visi visibility;


  /**
   * the modifiers of this feature
   */
  public int modifiers;


  /**
   * the result type of this feature.  Special values this might have are
   *
   * NoType: for no result type (as for an abstract or intrinsic feature)
   *
   * RefType: for constructors
   *
   * ValueType: for constructors of value types
   */
  public ReturnType returnType; // NYI: public field should not be written to


  /**
   * The qualified name of this feature as given at its declaration. This
   * usually has just one entry equal to name. If it has several entries, this
   * gives the fully qualified name of this feature.
   */
  List<String> qname;


  /**
   * The FeatureName of this feature, i.e., the combination of its name and the
   * number of arguments.
   */
  FeatureName _featureName;


  /**
   * The formal generic arguments of this feature
   */
  public FormalGenerics generics;


  /**
   * The formal arguments of this feature
   */
  public List<Feature> arguments;


  /**
   * The parents of this feature
   */
  public final List<Call> inherits;


  /**
   * The contract of this feature
   */
  public Contract contract;


  /**
   * The implementation of this feature
   */
  public Impl impl;


  /**
   * Reference to this feature's root, i.e., its outer feature.
   */
  protected Feature outer_ = null;


  /**
   * Check if this feature is used in a call.
   */
  private boolean isUsed_ = false;

  /**
   * In case isUsed_ is true, this gives the source code position of the first
   * use.
   */
  private SourcePosition isUsedAt_ = null;

  /**
   * Has this feature been found to be called dynamically?
   */
  private boolean isCalledDynamically_ = false;

  /**
   * Index for dynamically bound calls to this feature.
   */
  private int featureIndex = -1;


  /**
   * Number of anonymous inner classes
   */
  private int numAnonymousInnerClasses_ = 0;


  /**
   *
   */
  private SortedMap<FeatureName, Feature> declaredFeatures_ = new TreeMap<>();


  /**
   *
   */
  private SortedMap<FeatureName, Feature> declaredOrInheritedFeatures_ = new TreeMap<>();


  /**
   * All features that have been found to directly redefine this feature. This
   * does not include redifintions of redefinitions.  This set is collected
   * during RESOLVING_DECLARATIONS.
   */
  private Set<Feature> redefinitions_ = new TreeSet<>();


  /**
   * All features that have been found to inherit from this feature.  This set
   * is collected during RESOLVING_DECLARATIONS.
   */
  private Set<Feature> _heirs = new TreeSet<>();


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
  private Type resultType_ = null;


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
  private Type thisType_ = null;


  /**
   * # if ids created by getRuntimeClazzId[s].
   *
   * NYI! This is static to create unique ids. It is sufficient to have unique ids for sets of clazzes used by the same statement.
   */
  private static int runtimeClazzIdCount_ = 0;  // NYI: Used by dev.flang.be.interpreter, REMOVE!


  /**
   * For choice feature (i.e., isChoice() holds): The tag field that holds in
   * i32 that identifies the index of the actual generic argument to choice that
   * is represented.
   *
   * This might not become part of the runtime clazz if isChoiceOfOnlyRefs()
   * holds for that classs.
   */
  public Feature choiceTag_ = null;


  /**
   * For fields of open generic type: The results of select(res,i) and
   * select(i).
   */
  ArrayList<Feature> _selectOpen;


  /**
   * Is this a loop's index variable that is automatically updated by the loops
   * for-clause.  If so, assignments outside the loop prolog or nextIteration
   * parts are not allowed.
   */
  boolean _isIndexVarUpdatedByLoop = false;


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
         new List<String>(UNIVERSE_NAME),
         FormalGenerics.NONE,
         new List<Feature>(),
         new List<Call>(),
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
        (outer_ == null);
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
    state_ = State.ERROR;
  }


  /**
   * Constructor for an anonymous feature
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param t the result type
   *
   * @param s the signature, containin g the generic parameters, the
   * arguments, the throws clause, the inherits list and the contract.
   *
   * @param p the implementation (feature body etc).
   */
  public Feature(SourcePosition pos,
                 ReturnType r,
                 List<Call> i,
                 Contract c,
                 Block b)
  {
    this(pos,
         Consts.VISIBILITY_INVISIBLE,
         0,
         r,
         new List<String>("#anonymous"+uniqueAnonymousFeatureId),
         FormalGenerics.NONE,
         new List<Feature>(),
         i,
         c,
         new Impl(b.pos, b, Impl.Kind.Routine));
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
   * @param c the contract
   *
   * @param outer the declaring feature that will be set as an outer feature of
   * the newly created feature via a call to findDeclarations.
   */
  Feature(SourcePosition pos,
          Visi v,
          Type t,
          String qname,
          Feature outer)
  {
    this(pos,
         v,
         0,
         t,
         qname,
         null);
    findDeclarations(outer);
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
          Type t,
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
          Type t,
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
         new List<Call>(),
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
                 Type t,
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
         new List<Call>(),
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
   * @param t the result type
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
          List<Call> i,
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
   * @param t the result type
   *
   * @param qname the name of this feature
   *
   * @param g the generic parameters
   *
   * @param s the signature, containin g the generic parameters, the
   * arguments, the throws clause, the inherits list and the contract.
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
                 List<Call> i,
                 Contract c,
                 Impl p)
  {
    if (PRECONDITIONS) require
      (pos != null,
       qname.size() >= 1,
       p != null);

    this.pos        = pos;
    this.visibility = v;
    this.modifiers  = m;
    this.returnType = r;
    this.posOfReturnType_ = r == NoType.INSTANCE || r.isConstructorType() ? pos : r.functionReturnType().pos;
    String n = qname.getLast();
    if (n.equals("_"))
      {
        // NYI: Check that this feature is allowed to have this name, i.e., it
        // is declared in a Destructure statement.
        n = "#_"+ underscoreId++;
      }
    this.qname      = qname;
    this.generics   = g;
    this.arguments  = a;
    this._featureName = FeatureName.get(n, a.size());
    this.inherits   = (i.isEmpty() &&
                       (p.kind_ != Impl.Kind.FieldActual) &&
                       (p.kind_ != Impl.Kind.FieldDef   ) &&
                       (p.kind_ != Impl.Kind.FieldInit  ) &&
                       (p.kind_ != Impl.Kind.Field      ) &&
                       (qname.size() != 1 || (!qname.getFirst().equals(OBJECT_NAME  ) &&
                                              !qname.getFirst().equals(UNIVERSE_NAME))))
      ? new List<Call>(new Call(pos, OBJECT_NAME, Expr.NO_EXPRS))
      : i;

    this.contract  = c;
    this.impl = p;

    generics.setFeature(this);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Return the state of this class.
   */
  public State state()
  {
    return state_;
  }


  /**
   * The soucecode position of this statment, used for error messages.
   */
  public SourcePosition pos()
  {
    return pos;
  }


  /**
   * Compare the fully qualified name of this feature with the names provided by
   * the iterator, i.e., return true iff it returns "fuzion", "std", "out",
   * etc. and this feature is fuzion.std.out.
   *
   * @param it an iterator producing the elements of a fully qualified name
   *
   * @return true if this features's fully qualified names is a prefix of the
   * names produced by it.
   */
  private boolean checkNames(Iterator<String> it)
  {
    return
      (outer_ == null) || (outer_.checkNames(it) &&
                           it.hasNext() &&
                           it.next().equals(_featureName.baseName()));
  }


  /**
   * Check that the fully qualified name matches the outer_ feature(s) using
   * checkNames(). If not, show a corresponding error.
   */
  private void checkName()
  {
    if (qname.size() > 1)
      {
        Iterator<String> it = qname.iterator();
        if (!checkNames(it) || it.hasNext())
          {
            Errors.error(pos,
                         "Feature is declared in wrong environment",
                         "Feature " + qname + " is declared in wrong environment " + outer_.qualifiedName());
          }
      }
    if (!isResultField() && qname.getLast().equals(RESULT_NAME))
      {
        FeErrors.declarationOfResultFeature(pos);
      }
  }


  public Feature outer()
  {
    if (PRECONDITIONS) require
      (state().atLeast(State.LOADED));

    return outer_;
  }


  public Feature universe()
  {
    if (PRECONDITIONS) require
      (state().atLeast(State.LOADED));

    return (outer_ == null) ? this : outer_.universe();
  }


  public boolean isUniverse()
  {
    return false;
  }


  private void addDeclaredInnerFeature(Feature f)
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.LOADING));

    var fn = f.featureName();
    var existing = declaredFeatures_.get(fn);
    if (existing != null)
      {
        if (f       .impl.kind_ == Impl.Kind.FieldDef &&
            existing.impl.kind_ == Impl.Kind.FieldDef    )
          {
            var existingFields = FeatureName.getAll(declaredFeatures_, fn.baseName(), 0);
            fn = FeatureName.get(fn.baseName(), 0, existingFields.size());
            f._featureName = fn;
          }
        else
          {
            boolean error = true;
            if (f.isField() && existing.isField())
              {
                error = false;
                var existingFields = FeatureName.getAll(declaredFeatures_, fn.baseName(), 0);
                for (var e : existingFields.values())
                  {
                    // NYI: set error if e.declaredInBlock() == f.declaredInBlock()
                    if (e.isDeclaredInMainBlock() && f.isDeclaredInMainBlock())
                      {
                        error = true;
                      }
                  }
                if (!error)
                  {
                    fn = FeatureName.get(fn.baseName(), 0, existingFields.size());
                    f._featureName = fn;
                  }
              }
            if (error)
              {
                FeErrors.duplicateFeatureDeclaration(f.pos, f, existing);
              }
          }
      }
    this.declaredFeatures_.put(fn, f);
    if (this.state().atLeast(State.RESOLVED_DECLARATIONS))
      {
        check(Errors.count() > 0 || f.isAnonymousInnerFeature());
        check(Errors.count() > 0 || !this.declaredOrInheritedFeatures_.containsKey(fn));
        this.declaredOrInheritedFeatures_.put(fn, f);
        if (!f.isChoiceTag())  // NYI: somewhat ugly special handling of choice tags should not be needed
          {
            addToHeirs(fn, f);
          }
      }
  }


  /**
   * Add feature under given name to declaredOrInheritedFeatures_ of all direct
   * and indirect heirs of this feature.
   *
   * This is used in addDeclaredInnerFeature to add features during syntactic
   * sugar resolution after declaredOrInheritedFeatures_ has already been set.
   *
   * @param fn the name of the feature, after possible renaming during inheritance
   *
   * @param f the feature to be added.
   */
  private void addToHeirs(FeatureName fn, Feature f)
  {
    for (var h : _heirs)
      {
        var pos = SourcePosition.builtIn; // NYI: Would be nicer to use Call.pos for the inheritance call in h.inhertis
        h.addInheritedFeature(pos, fn, f);
        h.addToHeirs(fn, f);
      }
  }


  /**
   * Is this an anonymous feature, i.e., a feature declared within an expression
   * and without giving a name, in contrast to an normal feature defined by a
   * feature declaration?
   *
   * @return true iff this feature is anonymous.
   */
  private boolean isAnonymousInnerFeature()
  {
    // NYI: better have a flag for this
    return _featureName.baseName().startsWith("#");
  }


  /**
   * Is this a field that was added by fz, not by the user. If so, we do not
   * enforce visibility only after its declaration.
   *
   * @return true iff this feature is anonymous.
   */
  private boolean isArtificialField()
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
   * true iff this feature as a result field. This is the case if the returnType
   * is not a constructortype (self, value, single) and this is not a field.
   *
   * @return true iff this has a result field.
   */
  boolean hasResultField()
  {
    return
      (impl.kind_ == Impl.Kind.RoutineDef) ||
      (impl.kind_ == Impl.Kind.Routine &&
       !returnType.isConstructorType() &&
       returnType != NoType.INSTANCE);
  }


  /**
   * if hasResultField(), add a corresponding field to hold the result.
   */
  private void addResultField()
  {
    if (PRECONDITIONS) require
      (state_ == State.FINDING_DECLARATIONS);

    if (hasResultField())
      {
        Type t = impl.kind_ == Impl.Kind.Routine
          ? returnType.functionReturnType()
          : Types.t_UNDEFINED /* dummy type, will be replaced during TYPES_INFERENCING phase */;

        check
          (resultField_ == null);
        resultField_ = new Feature(pos,
                                   Consts.VISIBILITY_PRIVATE,
                                   t,
                                   resultInternal() ? INTERNAL_RESULT_NAME
                                                    : RESULT_NAME,
                                   this)
          {
            boolean isResultField() { return true; }
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
    return impl.kind_ == Impl.Kind.RoutineDef;
  }


  /**
   * true iff this feature is a function or field that returns a result, but not
   * a constructor that returns its frame object.
   *
   * @return true iff this has a function result
   */
  public boolean hasResult()
  {
    return isField() || hasResultField();
  }


  /**
   * Is this a choice feature, i.e., does it directly inherit from choice? If
   * so, return the actual generic parameters passed to the choice.
   *
   * @return null if this is not a choice feature, the actual generic
   * parameters, i.e, the actual choice types, otherwise.
   */
  public List<Type> choiceGenerics()
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.RESOLVING_TYPES));

    List<Type> result;

    if (this == Types.f_ERROR)
      {
        result = null;
      }
    else if (this == Types.resolved.f_choice)
      {
        result = generics.asActuals();
      }
    else
      {
        result = null;
        Call lastP = null;
        for (Call p: inherits)
          {
            check
              (Errors.count() > 0 || p.calledFeature() != null);

            if (p.calledFeature() == Types.resolved.f_choice)
              {
                if (lastP != null)
                  {
                    Errors.error(p.pos,
                                 "Repeated inheritance of choice is not permitted",
                                 "A choice feature must inherit directly from choice exactly one time.\n" +
                                 "Previous inheritance from choice at " + lastP.pos);
                  }
                lastP = p;
                result = p.generics;
              }
          }
      }
    return result;
  }


  /**
   * In case a cycle in choice generic arguments is detected, this function is
   * used to erase the generics altogether to avoid later problems when
   * traversing types.
   */
  private void eraseChoiceGenerics()
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.RESOLVING_TYPES),
       Errors.count() > 0);

    if (this == Types.resolved.f_choice)
      { // if this == choice, there are only formal generics, so nothing to erase
      }
    else
      {
        for (Call p: inherits)
          {
            check
              (Errors.count() > 0 || p.calledFeature() != null);

            if (p.calledFeature() == Types.resolved.f_choice)
              {
                p.generics = new List<Type>(Types.t_ERROR);
              }
          }
      }
  }


  /**
   * Is this a choice-type, i.e., does it directly inherit from choice?
   */
  public boolean isChoice()
  {
    return choiceGenerics() != null;
  }


  /**
   * Is this a tag field created for a choice-type?
   */
  public boolean isChoiceTag()
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

    if (state_ == State.LOADED)
      {
        state_ = State.RESOLVING;
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
    generics.visit(v, this);
    for (Call c: inherits)
      {
        Expr nc = c.visit(v, this);
        check
          (c == nc); // NYI: This will fail when doing funny stuff like inherit from bool.infix &&, need to check and handle explicitly
      }
    if (contract != null)
      {
        contract.visit(v, this);
      }
    impl.visit(v, this);
    returnType.visit(v, this);
  }


  /**
   * Find all the inner feature declarations within this feature and set
   * this.outer_ and, recursively, the outer_ references of all inner features to
   * the corresponding outer declaring feature.
   *
   * @param outer the root feature that declares this feature.  For
   * all found feature declarations, the outer feature will be set to
   * this value.
   */
  public void findDeclarations(Feature outer)
  {
    if (PRECONDITIONS) require
      (state_ == State.LOADING,
       ((outer == null) == (_featureName.baseName().equals(UNIVERSE_NAME))),
       this.outer_ == null);

    this.state_ = State.FINDING_DECLARATIONS;

    this.outer_ = outer;
    checkName();

    if (outer != null)
      {
        outer.addDeclaredInnerFeature(this);
        addOuterRef();
      }
    for (Feature a : arguments)
      {
        a.findDeclarations(this);
      }
    addResultField();

    visit(new FeatureVisitor()
      {
        public Call      action(Call      c, Feature outer) { c.findDeclarations(outer); return c; }
        public Feature   action(Feature   f, Feature outer) { f.findDeclarations(outer); return f; }
      });

    if (impl._initialValue != null &&
        outer.pos._sourceFile != pos._sourceFile &&
        (!outer.isUniverse() || !_legalPartOfUniverse) &&
        !_isIndexVarUpdatedByLoop  /* required for loop in universe, e.g.
                                    *
                                    *   echo "for i in 1..10 do stdout.println(i)" | fz -
                                    */
        )
      { // declaring field with initial value in different file than outer
        // feature.  We would have to add this to the statements of the outer
        // feature.  But if there are several such fields, in what order?
        FeErrors.initialValueNotAllowed(this);
      }

    this.state_ = State.LOADED;

    if (POSTCONDITIONS) ensure
      (outer_ == outer,
       state_ == State.LOADED);
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
      !outer().arguments.isEmpty() &&
      outer().arguments.getLast() == this &&
      t == returnType.functionReturnType();
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
   * @param i the iterator over this.inherits that has produced p. This will be
   * used to replace this entry to break the cycle (and hopefully avoid other
   * problems during compilation).
   */
  private void cyclicInheritanceError(Call p, ListIterator<Call> i)
  {
    if (PRECONDITIONS) require
      (p != null,
       p.calledFeature().detectedCyclicInheritance,
       i != null);

    Feature parent = p.calledFeature();
    String inh = "    inherits " + parent.qualifiedName() + " at " + p.pos.show() + "\n";
    if (detectedCyclicInheritance)
      { // the cycle closes while returning from recursion in resolveInheritance, so show the error:
        StringBuilder cycle = new StringBuilder(inh);
        for (int c = 1; c <= cyclicInhData.size(); c++)
          {
            cycle.append(( c + 1 < 10 ? " " : "") + (c + 1) + cyclicInhData.get(cyclicInhData.size() - c));
          }
        Errors.error(pos,
                     "Recursive inheritance in feature " + qualifiedName(),
                     cycle.toString());
        cyclicInhData.clear();
      }
    else
      { // mark all member of the cycl
        cyclicInhData.add(": feature " + qualifiedName()+" at " + pos.show() + "\n" + inh);
        detectedCyclicInheritance = true;
      }

    // try to fix recursive inheritance to keep compiler from crashing
    i.set(new Call(pos, OBJECT_NAME, Expr.NO_EXPRS));
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
      (state_.atLeast(State.LOADED));

    if (state_ == State.RESOLVING_INHERITANCE)
      {
        detectedCyclicInheritance = true;
      }
    else if (state_ == State.RESOLVING)
      {
        state_ = State.RESOLVING_INHERITANCE;

        check
          ((outer_ == null) || outer_.state().atLeast(State.RESOLVING));

        ListIterator<Call> i = inherits.listIterator();
        while (i.hasNext() && !detectedCyclicInheritance)
          {
            Call p = i.next();
            p.loadCalledFeature(res, this);
            p.isInheritanceCall_ = true;
            Feature parent = p.calledFeature();
            check
              (Errors.count() > 0 || parent != null);
            if (parent != null)
              {
                parent.resolveInheritance(res);
                if (parent.detectedCyclicInheritance)
                  {
                    cyclicInheritanceError(p, i);
                  }
              }
          }
        state_ = State.RESOLVED_INHERITANCE;
        res.scheduleForDeclarations(this);
      }

    if (POSTCONDITIONS) ensure
      (detectedCyclicInheritance || state_.atLeast(State.RESOLVED_INHERITANCE));
  }

  static FeatureVisitor findGenerics = new FeatureVisitor()
    {
      public Function action(Function f, Feature outer) { f.findGenerics(this, outer); return f; }
      public This     action(This     t, Feature outer) { t.findGenerics(      outer); return t; }
      public Type     action(Type     t, Feature outer) { t.findGenerics(      outer); return t; }
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
      (state_.atLeast(State.RESOLVED_INHERITANCE));

    if (state_ == State.RESOLVED_INHERITANCE)
      {
        state_ = State.RESOLVING_DECLARATIONS;

        check
          (state_ == State.RESOLVING_DECLARATIONS);

        this.returnType = impl.checkReturnType(this);
        findDeclaredOrInheritedFeatures(res);

        check
          (state_.atLeast(State.RESOLVING_DECLARATIONS));

        if (state_ == State.RESOLVING_DECLARATIONS)
          {
            /**
             * Find all the types used in this that refer to formal generic arguments of
             * this or any of this' outer classes.
             */
            visit(findGenerics);
          }

        state_ = State.RESOLVED_DECLARATIONS;
        res.scheduleForTypeResolution(this);
      }

    if (POSTCONDITIONS) ensure
      (state_.atLeast(State.RESOLVED_DECLARATIONS));
  }


  static class ResolveTypes extends FeatureVisitor
  {
    Resolution res;
    ResolveTypes(Resolution r)
      {
        res = r;
      }
    public void     action(Assign      a, Feature outer) {        a.resolveTypes(res, outer); }
    public Call     action(Call        c, Feature outer) { return c.resolveTypes(res, outer); }
    public Stmnt    action(Destructure d, Feature outer) { return d.resolveTypes(res, outer); }
    public Stmnt    action(Feature     f, Feature outer) { return f.resolveTypes(res, outer); }
    public Function action(Function    f, Feature outer) {        f.resolveTypes(res, outer); return f; }
    public void     action(Generic     g, Feature outer) {        g.resolveTypes(res, outer); }
    public void     action(Match       m, Feature outer) {        m.resolveTypes(res, outer); }
    public Expr     action(This        t, Feature outer) { return t.resolveTypes(res, outer); }
    public Type     action(Type        t, Feature outer) { return t.resolve(outer); }

    /**
     * visitActuals delays type resolution for actual arguments within a feature
     * until the feature's type was resolved.  The reason is that the feature's
     * type does not depend on the actual arguments, but the actual arguments
     * might depend directly or indirectly on the feature's type.
     */
    void visitActuals(Runnable r, Feature outer)
    {
      if (outer.state_.atLeast(State.RESOLVED_TYPES))
        {
          r.run();
        }
      else
        {
          outer.whenResolvedTypes.add(r);
        }
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
      (state_.atLeast(State.RESOLVED_DECLARATIONS));

    if (state_ == State.RESOLVED_DECLARATIONS)
      {
        state_ = State.RESOLVING_TYPES;

        visit(new ResolveTypes(res));

        if (hasThisType())
          {
            thisType_ = thisType().resolve(this);
          }

        if ((impl.kind_ == Impl.Kind.FieldActual) && (impl._initialValue.typeOrNull() == null))
          {
            impl._initialValue.visit(new ResolveTypes(res),
                                     true /* NYI: impl_outerOfInitialValue not set yet */
                                     ? outer().outer() :
                                     impl._outerOfInitialValue);
          }

        state_ = State.RESOLVED_TYPES;
        while (!whenResolvedTypes.isEmpty())
          {
            whenResolvedTypes.removeFirst().run();
          }
        res.scheduleForSyntacticSugar1Resolution(this);
      }

    if (POSTCONDITIONS) ensure
      (state_.atLeast(State.RESOLVED_TYPES));
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
    if (state_.atLeast(State.RESOLVED_TYPES))
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
      (state_.atLeast(State.RESOLVED_TYPES));

    if (state_ == State.RESOLVED_TYPES)
      {
        state_ = State.RESOLVING_SUGAR1;

        visit(new FeatureVisitor()
          {
            public Expr action(Call c, Feature outer) { return c.resolveSyntacticSugar(res, outer); }
          });

        state_ = State.RESOLVED_SUGAR1;
        res.scheduleForTypeInteference(this);
      }

    if (POSTCONDITIONS) ensure
      (state_.atLeast(State.RESOLVED_SUGAR1));
  }


  /**
   * Find list of all accesses to this feature's closure by any of its inner
   * features.
   */
  private List<Call> closureAccesses()
  {
    List<Call> result = new List<>();
    for (Feature f : declaredOrInheritedFeatures().values())
      {
        f.visit(new FeatureVisitor()
          {
            public Call action(Call c, Feature outer)
            {
              if (c.calledFeature() == outerRefOrNull())
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
  private void checkNoClosureAccesses(SourcePosition errorPos)
  {
    List<Call> closureAccesses = closureAccesses();
    if (!closureAccesses.isEmpty())
      {
        StringBuilder accesses = new StringBuilder();
        for (Call c: closureAccesses)
          {
            accesses.append(c.pos.show()).append("\n");
          }
        Errors.error(errorPos,
                     "Choice type must not access fields of surrounding scope.",
                     "A closure cannot be built for a choice type. Forbidden accesses occur at \n" +
                     accesses);
      }
  }


  /**
   * Does this statement consist of nothing but declarations? I.e., it has no
   * code that actually would be executed at runtime.
   */
  public boolean containsOnlyDeclarations()
  {
    boolean result = true;
    switch (impl.kind_)
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
        Errors.error(pos,
                     "choice feature must not be ref",
                     "A choice feature must be a value type since it is not constructed ");
      }

    for (Feature p : declaredOrInheritedFeatures_.values())
      {
        // choice type must not have any fields
        if (p.isField() && !p.isOuterRef())
          {
            Errors.error(pos,
                         "Choice must not contain any fields",
                         "Field >>" + p.qualifiedName() + "<< is not permitted in choice.\n" +
                         "Field declared at "+ p.pos.show());
          }
      }
    // choice type must not contain any code, but may contain inner features
    switch (impl.kind_)
      {
      case FieldInit:    // a field with initialization syntactic sugar
      case FieldDef:     // a field with implicit type
      case FieldActual:  // a field with implicit type taken from actual argument to call
      case Field:        // a field
        {
          Errors.error(pos,
                       "Choice feature must not be a field",
                       "A choice feature must be a normal feature with empty code section");
          break;
        }
      case RoutineDef:  // normal feature with code and implicit result type
        {
          Errors.error(pos,
                       "Choice feature must not be defined as a function",
                       "A choice feature must be a normal feature with empty code section");
          break;
        }
      case Routine:      // normal feature with code
        {
          if (!impl.containsOnlyDeclarations())
            {
              Errors.error(pos,
                           "Choice feature must not contain any code",
                           "A choice feature must be a normal feature with empty code section");
            }
          break;
        }
      case Abstract:
        { // not ok
          Errors.error(pos,
                       "Choice feature must not be abstract",
                       "A choice feature must be a normal feature with empty code section");
          break;
        }
      case Intrinsic:
        {
          Errors.error(pos,
                       "Choice feature must not be intrinsic",
                       "A choice feature must be a normal feature with empty code section");
          break;
        }
      }

    for (Type t : choiceGenerics())
      {
        if (!t.isRef())
          {
            if (t == thisType())
              {
                Errors.error(pos,
                             "Choice cannot refer to its own value type as one of the choice alternatives",
                             "Embedding a choice type in itself would result in an infinitely large type.\n" +
                             "Fauly generic argument: "+t+" at "+t.pos.show());
                thisType_ = Types.t_ERROR;
                eraseChoiceGenerics();
              }
            Feature o = outer();
            while (o != null)
              {
                if (t == o.thisType())
                  {
                    Errors.error(pos,
                                 "Choice cannot refer to an outer value type as one of the choice alternatives",
                                 "Embedding an outer value in a choice type would result in infinitely large type.\n" +
                                 "Fauly generic argument: "+t+" at "+t.pos.show());
                    o.thisType_ = Types.t_ERROR;
                    eraseChoiceGenerics();
                  }
                o = o.outer();
              }
          }
      }

    thisType().checkChoice(this.pos);

    checkNoClosureAccesses(pos);
    for (Call p : inherits)
      {
        p.calledFeature().checkNoClosureAccesses(p.pos);
      }

    choiceTag_ = new Feature(pos,
                             Consts.VISIBILITY_PRIVATE,
                             Types.resolved.t_i32,
                             FuzionConstants.CHOICE_TAG_NAME,
                             this)
      {
        /**
         * Is this a tag field created for a choice-type?
         */
        public boolean isChoiceTag()
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
    for (Call p : inherits)
      {
        // choice type is leaf
        Feature cf = p.calledFeature();
        check
          (Errors.count() > 0 || cf != null);

        if (cf != null && cf.isChoice() && cf != Types.resolved.f_choice)
          {
            Errors.error(p.pos,
                         "Cannot inherit from choice feature",
                         "Choice must be leaf.");
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
      (state_.atLeast(State.RESOLVED_TYPES));

    if (state_ == State.RESOLVED_SUGAR1)
      {
        state_ = State.TYPES_INFERENCING;

        check
          (resultType_ == null);

        if (outer() != null)
          {
            outer().typeInference(res);
          }
        choiceTypeCheckAndInternalFields(res);

        resultType_ = resultType();
        resultType_.checkChoice(posOfReturnType_);

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

        state_ = State.TYPES_INFERENCED;
        res.scheduleForBoxing(this);
      }

    if (POSTCONDITIONS) ensure
      (state_.atLeast(State.TYPES_INFERENCED));
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
      (state_.atLeast(State.TYPES_INFERENCED));

    if (state_ == State.TYPES_INFERENCED)
      {
        state_ = State.BOXING;

        visit(new FeatureVisitor() {
            public void  action(Assign    a, Feature outer) { a.box(outer);           }
            public Call  action(Call      c, Feature outer) { c.box(outer); return c; }
            public Expr  action(InitArray i, Feature outer) { i.box(outer); return i; }
          });

        state_ = State.BOXED;
        res.scheduleForCheckTypes1(this);
      }

    if (POSTCONDITIONS) ensure
      (state_.atLeast(State.BOXED));
  }


  /**
   * Determine the form argument types of this feature.
   *
   * @return a new array containing this feature's formal argument types.
   */
  Type[] argTypes()
  {
    int argnum = 0;
    var result = new Type[arguments.size()];
    for (Feature frml : arguments)
      {
        check
          (Errors.count() > 0 || frml.state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

        Type frmlT = frml.resultType();
        check(frmlT == Types.intern(frmlT));
        result[argnum] = frmlT;
        argnum++;
      }

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * Find the chain of inheritance calls from this to its parent f.
   *
   * NYI: Repeated inheritance handling is still missing, there might be several
   * different inheritance chains, need to check if they lead to the same result
   * (wrt generic arguments) or renaminging/selection of the preferred
   * implementation.
   *
   * @param ancestor the ancestor feature this inherits from
   *
   * @return The inheritance chain from the inheritance call to ancestor at the
   * first index down to the last inheritance call within this.  Empty list in
   * case this == ancestor, null in case this does not inherit from ancestor.
   */
  private List<Call> tryFindInheritanceChain(Feature ancestor)
  {
    List<Call> result;
    if (this == ancestor)
      {
        result = new List<Call>();
      }
    else
      {
        result = null;
        for (Call c : inherits)
          {
            result = c.calledFeature().tryFindInheritanceChain(ancestor);
            if (result != null)
              {
                result.add(c);
                break;
              }
          }
      }
    return result;
  }


  /**
   * Find the chain of inheritance calls from this to its parent f.
   *
   * NYI: Repeated inheritance handling is still missing, there might be several
   * different inheritance chains, need to check if they lead to the same result
   * (wrt generic arguments) or renaminging/selection of the preferred
   * implementation.
   *
   * @param ancestor the ancestor feature this inherits from
   *
   * @return The inheritance chain from the inheritance call to ancestor at the
   * first index down to the last inheritance call within this.  Empty list in
   * case this == ancestor, never null.
   */
  public List<Call> findInheritanceChain(Feature ancestor)
  {
    if (PRECONDITIONS) require
      (ancestor != null);

    List<Call> result = tryFindInheritanceChain(ancestor);

    if (POSTCONDITIONS) ensure
      (this == Types.f_ERROR || ancestor == Types.f_ERROR || Errors.count() > 0 || result != null);

    return result;
  }


  /**
   * Check if this features argument list contains arguments of open generic
   * type. If this is the case, then the argCount of the feature name may change
   * when inherited.
   */
  public boolean hasOpenGenericsArgList()
  {
    boolean result = false;
    Feature o = this;
    while (o != null && !result)
      {
        for (var g : o.generics.list)
          {
            if (g.isOpen())
              {
                for (Feature a : arguments)
                  {
                    Type t = a.returnType.functionReturnType();
                    if (!t.checkedForGeneric)
                      {
                        a.visit(findGenerics);
                      }
                    result = result || t.isGenericArgument() && t.genericArgument() == g;
                  }
              }
          }
        o = o.outer();
      }
    return result;
  }


  /**
   * Obtain the effective name of this feature when actualGenerics are the
   * actual generics of its outer() feature.
   */
  FeatureName effectiveName(List<Type> actualGenerics)
  {
    if (PRECONDITIONS) require
      (outer().generics.sizeMatches(actualGenerics));

    var result = _featureName;
    if (hasOpenGenericsArgList())
      {
        var argCount = arguments.size() + actualGenerics.size() - outer().generics.list.size();
        check
          (argCount >= 0);
        result =  FeatureName.get(result.baseName(),
                                  argCount);
      }
    return result;
  }


  /**
   * For a feature with given FeatureName fn that is directly inherited from
   * this through inheritance call p to heir, this determines the actual
   * FeatureName as seen in the heir feature.
   *
   * The reasons for a feature name to change during inheritance are
   *
   * - actual generic arguments to open generic parameters change the argument
   *   count.
   *
   * - explicit renaming during inheritance
   *
   * @param f a feature that is declared in or inherted by this feature
   *
   * @param fn a feature name within this feature
   *
   * @param p an inheritance call in heir inheriting from this
   *
   * @param the heir that contains the inheritance call p
   *
   * @return the new feature name as seen within heir.
   */
  public FeatureName handDown(Feature f, FeatureName fn, Call p, Feature heir)
  {
    if (PRECONDITIONS) require
      (this.declaredOrInheritedFeatures_.get(fn) == f,
       this != heir);

    if (f.outer() == p.calledFeature()) // NYI: currently does not support inheriting open generic over several levels
      {
        fn = f.effectiveName(p.generics);
      }

    return fn;
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
  Type handDownNonOpen(Type t, Feature heir)
  {
    if (PRECONDITIONS) require
      (!t.isOpenGeneric(),
       heir != null,
       state_.atLeast(State.CHECKING_TYPES1));

    var a = handDown(new Type[] { t }, heir);

    check
      (Errors.count() > 0 || a.length == 1);

    return a.length == 1 ? a[0] : Types.t_ERROR;
  }


  /**
   * Determine the actual types of an array of types in this feature after it
   * was inherited by heir. The types may change on the way due to formal
   * generics being replaced by actual generic arguments on the way.
   *
   * Due to open generics, even the number of types may change through
   * inheritance.
   *
   * @param a an array of types to be handed down
   *
   * @param heir a feature that inhertis from outer()
   *
   * @return the types from the argument array a has seen this within
   * heir. Their number might have changed due to open generics.
   */
  Type[] handDown(Type[] a, Feature heir)  // NYI: This does not distinguish different inheritance chains yet
  {
    if (PRECONDITIONS) require
      (heir != null,
       state_.atLeast(State.RESOLVED_TYPES));

    if (heir != Types.f_ERROR)
      {
        for (Call c : heir.findInheritanceChain(outer()))
          {
            for (int i = 0; i < a.length; i++)
              {
                Type ti = a[i];
                if (ti.isOpenGeneric())
                  {
                    List<Type> frmlTs = ti.genericArgument().replaceOpen(c.generics);
                    a = Arrays.copyOf(a, a.length - 1 + frmlTs.size());
                    for (Type tg : frmlTs)
                      {
                        check
                          (tg == Types.intern(tg));
                        a[i] = tg;
                        i++;
                      }
                    i = i - 1;
                  }
                else
                  {
                    FormalGenerics.resolve(c.generics, heir);
                    ti = ti.actualType(c.calledFeature(), c.generics);
                    a[i] = Types.intern(ti);
                  }
              }
          }
      }
    return a;
  }


  /**
   * Perform type checking, in particular, verify that all redefinitions of this
   * have the argument types.  Create compile time erros if this is not the
   * case.
   */
  private void checkTypes()
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.CHECKING_TYPES1));

    int ean = arguments.size();
    for (Feature r : redefinitions_)
      {
        Type[] ta = handDown(argTypes(), r.outer());
        Type[] ra = r.argTypes();
        if (ta.length != ra.length)
          {
            FeErrors.argumentLengthsMismatch(this, ta.length, r, ra.length);
          }
        else
          {
            for (int i = 0; i < ta.length; i++)
              {
                Type t1 = ta[i];
                Type t2 = ra[i];
                if (t1 != t2 && !t1.containsError() && !t2.containsError())
                  {
                    // original arg list may be shorter if last arg is open generic:
                    check
                      (Errors.count() > 0 ||
                       i < arguments.size() ||
                       arguments.get(arguments.size()-1).resultType().isOpenGeneric());
                    int ai = Math.min(arguments.size() - 1, i);

                    Feature actualArg   = r.arguments.get(i);
                    Feature originalArg =   arguments.get(ai);
                    FeErrors.argumentTypeMismatchInRedefinition(this, originalArg,
                                                                r,    actualArg);
                  }
              }
          }

        if (  returnType != NoType.INSTANCE &&   returnType != ValueType.INSTANCE &&   returnType != RefType.INSTANCE ||
            r.returnType != NoType.INSTANCE && r.returnType != ValueType.INSTANCE && r.returnType != RefType.INSTANCE      )
          {
            Type t1 = handDownNonOpen(resultType(), r.outer());
            Type t2 = r.resultType();
            if ((t1.isChoice()
                 ? t1 != t2  // we (currently) do not tag the result in a redefined feature, see testRedefine
                 : !t1.isAssignableFrom(t2)) &&
                t2 != Types.resolved.t_void)
              {
                FeErrors.resultTypeMismatchInRedefinition(this, r);
              }
          }
      }

    if (returnType.isConstructorType())
      {
        var res = impl._code;
        var rt = res.type();
        if (!Types.resolved.t_unit.isAssignableFrom(rt))
          {
            FeErrors.constructorResultMustBeUnit(impl._code);
          }
      }
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
      (state_.atLeast(State.BOXED));

    state_ =
      (state_ == State.BOXED          ) ? State.CHECKING_TYPES1 :
      (state_ == State.RESOLVED_SUGAR2) ? State.CHECKING_TYPES2 : state_;

    if ((state_ == State.CHECKING_TYPES1) ||
        (state_ == State.CHECKING_TYPES2)    )
      {
        visit(new FeatureVisitor() {
            public void  action(Assign    a, Feature outer) { a.checkTypes();                }
            public Call  action(Call      c, Feature outer) { c.checkTypes(outer); return c; }
            public void  action(If        i, Feature outer) { i.checkTypes();                }
            public Expr  action(InitArray i, Feature outer) { i.checkTypes();      return i; }
          });
        checkTypes();

        switch (state_)
          {
          case CHECKING_TYPES1: state_ = State.CHECKED_TYPES1; res.scheduleForSyntacticSugar2Resolution(this); break;
          case CHECKING_TYPES2: state_ = State.CHECKED_TYPES2; /* end for front end! */                        break;
          }
      }

    if (POSTCONDITIONS) ensure
      (state_.atLeast(State.CHECKED_TYPES1));
  }


  /**
   * The result field declared automatically in case hasResultField().
   *
   * @return the result or null if this does not have a result field.
   */
  public Feature resultField()
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.LOADED));

    Feature result = resultField_;

    if (POSTCONDITIONS) ensure
      (hasResultField() == (result != null));
    return result;
  }


  /**
   * During type resolution, record that we found an assignment to
   * resultField().
   */
  public void foundAssignmentToResult()
  {
    if (PRECONDITIONS) require
      (state_ == State.RESOLVING_TYPES ||
       state_ == State.RESOLVED_TYPES);

    hasAssignmentsToResult_ = true;
  }


  /**
   * After type resolution, this checks if an assignment tot he result variable
   * has been found.
   */
  public boolean hasAssignmentsToResult()
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.RESOLVED_TYPES));

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
      (state_.atLeast(State.CHECKED_TYPES1));

    if (state_ == State.CHECKED_TYPES1)
      {
        state_ = State.RESOLVING_SUGAR2;

        visit(new FeatureVisitor() {
            public Stmnt action(Feature   f, Feature outer) { return new Nop(pos);                         }
            public Expr  action(Function  f, Feature outer) { return f.resolveSyntacticSugar2(res, outer); }
            public Expr  action(InitArray i, Feature outer) { return i.resolveSyntacticSugar2(res, outer); }
            public void  action(Impl      i, Feature outer) {        i.resolveSyntacticSugar2(res, outer); }
          });

        state_ = State.RESOLVED_SUGAR2;
        res.scheduleForCheckTypes2(this);
      }

    if (POSTCONDITIONS) ensure
      (state_.atLeast(State.RESOLVED_SUGAR2));
  }


  /**
   * Find an internally referenced feature within this based on its qualified
   * name.
   *
   * @param qname the qualified name of the feature relative to this.  If
   * this.isUniverse(), qname is the fully qualifed name.
   *
   * @return the found feature or null in case of an error.
   */
  public /* NYI: public only due to one hacky use in be.interpreter.Interpreter */ Feature get(String qname)
  {
    return get(null, qname, false);
  }


  /**
   * Find an internally referenced feature within this based on its qualified
   * name.
   *
   * @param qname the qualified name of the feature relative to this.  If
   * this.isUniverse(), qname is the fully qualifed name.
   *
   * @param argcount the number of arguments, -1 if not specified.
   *
   * @return the found feature or null in case of an error.
   */
  Feature get(String qname, int argcount)
  {
    return get(null, qname, false, argcount);
  }


  /**
   * Mark features given by their qualified name as used.  This is a convenience
   * method to mark features that cannot be detected as used automatically,
   * e.g., because they are used internally or within intrinsic features.
   *
   * @param qname the qualified name of the feature relative to this.  If
   * this.isUniverse(), qname is the fully qualifed name.
   *
   * @return the found feature or null in case of an error.
   */
  public Feature markUsedAndGet(Resolution res, String qname)
  {
    return get(res, qname, true);
  }


  /**
   * Mark features given by their qualified name as used. This is a convenience
   * method to mark features that cannot be detected as used automatically,
   * e.g., because they are used internally or within intrinsic features.
   *
   * @param qname the qualified name of the feature relative to this.  If
   * this.isUniverse(), qname is the fully qualifed name.
   *
   * @param markUsed true iff the features on the way should be marked as used.
   *
   * @return the found feature or null in case of an error.
   */
  Feature get(Resolution res, String qname, boolean markUsed)
  {
    return get(res, qname, markUsed, -1);
  }


  /**
   * Mark features given by their qualified name as used. This is a convenience
   * method to mark features that cannot be detected as used automatically,
   * e.g., because they are used internally or within intrinsic features.
   *
   * @param qname the qualified name of the feature relative to this.  If
   * this.isUniverse(), qname is the fully qualifed name.
   *
   * @param markUsed true iff the features on the way should be marked as used.
   *
   * @param argcount the number of arguments, -1 if not specified.
   *
   * @return the found feature or Types.f_ERROR in case of an error.
   */
  Feature get(Resolution res, String qname, boolean markUsed, int argcount)
  {
    Feature f = this;
    var nams = qname.split("\\.");
    boolean err = false;
    for (var nam : nams)
      {
        if (!err)
          {
            var set = (argcount >= 0
                       ? FeatureName.getAll(f.declaredFeatures(), nam, argcount)
                       : FeatureName.getAll(f.declaredFeatures(), nam         )).values();
            if (set.size() == 1)
              {
                for (var f2 : set)
                  {
                    if (markUsed)
                      {
                        f2.markUsed(res, SourcePosition.builtIn);
                      }
                    f = f2;
                  }
              }
            else
              {
                if (set.isEmpty())
                  {
                    FeErrors.internallyReferencedFeatureNotFound(pos, qname, f, nam);
                  }
                else
                  { // NYI: This might happen if the user adds additional features
                    // with different argCounts. qname should contain argCount to
                    // avoid this
                    FeErrors.internallyReferencedFeatureNotUnique(pos, qname + (argcount >= 0 ? " (" + Errors.argumentsString(argcount) : ""), set);
                  }
                err = true;
              }
          }
      }
    return err ? Types.f_ERROR : f;
  }


  /**
   * Find all features that are used, i.e., that are called or read. Fields that
   * are only written to but never read are considered to be unused.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   */
  void findUsedFeatures(Resolution res)
  {
    /* NYI: This should be part of the middle end, not part of the front end! */
    if (PRECONDITIONS) require
      (state_.atLeast(State.CHECKED_TYPES2));

    if (state_ == State.CHECKED_TYPES2)
      {
        state_ = State.FINDING_USED_FEATURES;

        if (outer_ != null)
          {
            outer_.markUsed(res, pos);
          }
        for (Feature a : arguments)
          {
            a.markUsed(res, pos);
            if (a.isOpenGenericField())
              {
                if (_selectOpen != null)
                  {
                    for (var s : _selectOpen)
                      {
                        s.markUsed(res, pos);
                      }
                  }
              }
          }
        for (Call p: inherits)
          {
            p.calledFeature().markUsed(res, p.pos);
          }
        resultType().findUsedFeatures(res, pos);
        if (choiceTag_ != null)
          {
            choiceTag_.markUsed(res, pos);
          }

        visit(new FeatureVisitor() {
            // it does not seem to be necessary to mark all features in types as used:
            // public Type  action(Type    t, Feature outer) { t.findUsedFeatures(res, pos); return t; }
            public Call  action(Call    c, Feature outer) { c.findUsedFeatures(res); return c; }
            public Stmnt action(Feature f, Feature outer) { markUsed(res, pos);      return f; } // NYI: this seems wrong ("f." missing) or unnecessary
            public void  action(Match   m, Feature outer) { m.findUsedFeatures(res);           }
            public void  action(Tag     t, Feature outer) { t._taggedType.findUsedFeatures(res, t.pos()); }
          });

        state_ = State.FOUND_USED_FEATURES;
        res.scheduleForFeatureIndexResolution(this);
      }

    if (POSTCONDITIONS) ensure
      (state_.atLeast(State.FOUND_USED_FEATURES));
  }


  /**
   * During FINDING_USED_FEATURES, this sets the flag that this feature is used.
   *
   * @param usedAt the position this feature was used at, for creating usefule
   * error messages
   */
  public void markUsed(Resolution res, SourcePosition usedAt)
  {
    markUsed(res, false, usedAt);
  }


  /**
   * During FINDING_USED_FEATURES, this sets the flag that this feature is used.
   *
   * @param dynamically true iff this feature is called dynamically, i.e., it
   * has to be part of the dynamic binding data.
   *
   * @param usedAt the position this feature was used at, for creating usefule
   * error messages
   */
  void markUsed(Resolution res, boolean dynamically, SourcePosition usedAt)
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.CHECKED_TYPES2));

    this.isCalledDynamically_ |= dynamically;
    if (!this.isUsed_)
      {
        this.isUsed_ = true;
        this.isUsedAt_ = usedAt;
        if (state_ != State.ERROR)
          {
            res.scheduleForFindUsedFeatures(this);
          }
        if (resultField_ != null)
          {
            resultField_.markUsed(res, usedAt);
          }
        if (resultType_ != null)
          {
            if (!resultType_.isGenericArgument())
              { // Since instances of choice types are never created explicity,
                // they will be marked as used if they are used as a result type
                // of a function or field.
                Feature f = resultType_.featureOfType();
                if (f.isChoice())
                  {
                    f.markUsed(res, usedAt);
                  }
              }
          }
        if (impl == Impl.INTRINSIC && outerRefOrNull() != null)
          {
            outerRefOrNull().markUsed(res, false, usedAt);
          }
        for (Feature f : redefinitions_)
          {
            f.markUsed(res, usedAt);
          }
        for (var a : arguments)
          {
            a.markUsed(res, usedAt);
          }
        if (isOpenGenericField())
          {
            for (var s :_selectOpen)
              {
                s.markUsed(res,  usedAt);
              }
          }
      }
  }

  // For debugging to print unused features exactly once
  //
  //  static Set<String> allUnused = new TreeSet<>();


  /**
   * Has this feature been found to be used?
   */
  public boolean isUsed()
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.CHECKED_TYPES2));

    if (!isUsed_)
      {
        String qn = qualifiedName();
        // if (!allUnused.contains(qn)) { System.out.println("UNUSED: "+qn); allUnused.add(qn); }
      }

    return isUsed_;
  }

  /**
   * Has this feature been found to be used?
   */
  public SourcePosition isUsedAt()
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.CHECKED_TYPES2));

    return isUsedAt_;
  }


  /**
   * Has this feature been found to be called dynamically?
   */
  public boolean isCalledDynamically()
  {
    return isCalledDynamically_;
  }


  /*
   * Feature index resolution: For all features except the universe, find a
   * unique index within the sets of features declared in it's outer feature
   * (i.e., all sibling will have unique indices).
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   */
  void resolveFeatureIndex(Resolution res)
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.FOUND_USED_FEATURES));

    if (state_ == State.FOUND_USED_FEATURES)
      {
        state_ = State.RESOLVING_FEATURE_INDEX;

        if (outer_ != null)
          {
            featureIndex = outer_.newFeatureIndex(this._featureName.baseName());
          }

        state_ = State.RESOLVED_FEATURE_INDEX;

        state_ = State.RESOLVED; // NYI: remove
      }

    if (POSTCONDITIONS) ensure
      (state_.atLeast(State.RESOLVED_FEATURE_INDEX));
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
      (!this.state_.atLeast(State.LOADED) || this.outer() == outer);

    // impl.initialValue is code executed by outer, not by this. So we visit it
    // here, while impl.code is visited when impl.visit is called with this as
    // outer argument.
    //
    if (impl._initialValue != null &&
        /* initial value has been replaced by explicit assignment during
         * RESOLVING_TYPES phase: */
        !outer.state().atLeast(State.RESOLVING_SUGAR1))
      {
        impl._initialValue = impl._initialValue.visit(v, outer);
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

    if (impl.kind_ == Impl.Kind.FieldDef    ||
        impl.kind_ == Impl.Kind.FieldActual    )
      {
        if ((returnType != NoType.INSTANCE))
          {
            Errors.error(pos,
                         "Field definition using := must not specify an explicit type",
                         "Definition of field: " + qualifiedName() + "\n" +
                         "Explicit type given: " + returnType + "\n" +
                         "Defining expression: " + impl._initialValue);
          }
      }
    if (impl.kind_ == Impl.Kind.RoutineDef)
      {
        if ((returnType != NoType.INSTANCE))
          {
            Errors.error(pos,
                         "Function definition using => must not specify an explicit type",
                         "Definition of function: " + qualifiedName() + "\n" +
                         "Explicit type given: " + returnType + "\n" +
                         "Defining expression: " + impl._code);
          }
      }
    if (impl._initialValue != null)
      {
        /* add assignment of initial value: */
        result = new Block
          (pos, new List<>
           (this,
            new Assign(res, pos, this, impl._initialValue, outer)
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


  public SortedMap<FeatureName, Feature> declaredFeatures()
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.LOADED));

    return declaredFeatures_;
  }


  public SortedMap<FeatureName, Feature> declaredOrInheritedFeatures()
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.RESOLVED_DECLARATIONS));

    return declaredOrInheritedFeatures_;
  }


  private void findDeclaredOrInheritedFeatures(Resolution res)
  {
    if (PRECONDITIONS) require
      (state_ == State.RESOLVING_DECLARATIONS);

    declaredOrInheritedFeatures_ = new TreeMap<>();
    findInheritedFeatures(res);
    res.innerFeaturesLoader.loadInnerFeatures(res, this);
    findDeclaredFeatures(res);
  }

  /**
   * Find all inherited features and add them to declaredOrInheritedFeatures_.
   * In case an existing feature was found, check if there is a conflict and if
   * so, report an error message (repeated inheritance).
   */
  private void findInheritedFeatures(Resolution res)
  {
    for (Call p : inherits)
      {
        Feature cf = p.calledFeature();
        check
          (Errors.count() > 0 || cf != null);

        if (cf != null)
          {
            cf._heirs.add(this);
            res.resolveDeclarations(cf);
            for (var fnf : cf.declaredOrInheritedFeatures().entrySet())
              {
                var fn = fnf.getKey();
                var f = fnf.getValue();
                check
                  (cf != this);

                var newfn = cf.handDown(f, fn, p, this);
                addInheritedFeature(p.pos, newfn, f);
              }
          }
      }
  }


  /**
   * Helper method for findInheritedFeatures and addToHeirs to add a feature
   * that this feature inherits.
   *
   * @param pos the source code position of the inherits call responsible for
   * the inheritance.
   *
   * @param fn the name of the feature, after possible renaming during inheritance
   *
   * @param f the feature to be added.
   */
  private void addInheritedFeature(SourcePosition pos, FeatureName fn, Feature f)
  {
    var existing = declaredOrInheritedFeatures_.get(fn);
    if (existing != null)
      {
        if (existing.redefinitions_.contains(f))
          { // f redefined existing, so we are fine
          }
        else if (f.redefinitions_.contains(existing))
          { // existing redefines f, so use existing
            f = existing;
          }
        else if (existing == f && f.generics != FormalGenerics.NONE ||
                 existing != f && declaredFeatures().get(fn) == null)
          { // NYI: Should be ok if existing or f is abstract.
            FeErrors.repeatedInheritanceCannotBeResolved(pos, this, fn, existing, f);
          }
      }
    declaredOrInheritedFeatures_.put(fn, f);
  }


  /**
   * Add all declared features to declaredOrInheritedFeatures_.  In case a
   * declared feature exists in declaredOrInheritedFeatures_ (because it was
   * inherited), check if the declared feature redefines the inherited
   * feature. Otherwise, report an error message.
   */
  private void findDeclaredFeatures(Resolution res)
  {
    for (var e : declaredFeatures().entrySet())
      {
        var fn = e.getKey();
        var f = e.getValue();
        var existing = declaredOrInheritedFeatures_.get(fn);
        if (existing == null)
          {
            if ((f.modifiers & Consts.MODIFIER_REDEFINE) != 0)
              {
                FeErrors.redefineModifierDoesNotRedefine(f);
              }
          }
        else if (existing.outer() == this)
          {
            // This cannot happen, this case was already handled in addDeclaredInnerFeature:
            check
              (false);
            FeErrors.duplicateFeatureDeclaration(f.pos, this, existing);
          }
        else if (existing.generics != FormalGenerics.NONE)
          {
            FeErrors.cannotRedefineGeneric(f.pos, this, existing);
          }
        else if ((f.modifiers & Consts.MODIFIER_REDEFINE) == 0 && existing.impl != Impl.ABSTRACT)
          {
            FeErrors.redefineModifierMissing(f.pos, this, existing);
          }
        else
          {
            existing.redefinitions_.add(f);
          }
        declaredOrInheritedFeatures_.put(fn, f);
        f.scheduleForResolution(res);
      }
  }


  public Feature findDeclaredOrInheritedFeature(FeatureName name)
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.RESOLVED_DECLARATIONS));

    return this.declaredOrInheritedFeatures_.get(name);
  }


  /**
   * Get all declared or inherited features with the given base name,
   * independent of the number of arguments or the id.
   *
   * @param name the name of the feature
   */
  SortedMap<FeatureName, Feature> findDeclaredOrInheritedFeatures(String name)
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.RESOLVED_DECLARATIONS));

    return FeatureName.getAll(this.declaredOrInheritedFeatures_, name);
  }

  /**
   * Get all declared or inherited features with the given base name and
   * argument count, independent of the id.
   *
   * @param name the name of the feature
   *
   * @param argCount the argument count
   */
  SortedMap<FeatureName, Feature> findDeclaredOrInheritedFeatures(String name, int argCount)
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.RESOLVED_DECLARATIONS));

    return FeatureName.getAll(this.declaredOrInheritedFeatures_, name, argCount);
  }

  static class FeatureAndOuter
  {
    Feature feature;
    Feature outer;
  }

  FeatureAndOuter findDeclaredInheritedOrOuterFeature(FeatureName fn)
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.RESOLVED_DECLARATIONS));

    FeatureAndOuter result = null;
    Feature outer;

    outer = this;
    do
      {
        Feature f = outer.findDeclaredOrInheritedFeature(fn);
        if (f != null)
          {
            result = new FeatureAndOuter();
            result.feature = f;
            result.outer = outer;
          }
        outer = outer.outer();
      }
    while ((result == null) && (outer != null));

    return result;
  }

  static class FeaturesAndOuter
  {
    SortedMap<FeatureName, Feature> features;
    Feature outer;

    /**
     * For an access (call to or assignment to field), create an expression to
     * get the outer instance that contains the accessed feature(s).
     *
     * @param pos source code position of the access
     *
     * @param res Resolution instance
     *
     * @param cur the feature that contains the access.
     */
    Expr target(SourcePosition pos, Resolution res, Feature cur)
    {
      var t = new This(pos, cur, outer);
      Expr result = t;
      if (cur.state() != Feature.State.RESOLVING_INHERITANCE)
        {
          result = t.resolveTypes(res, cur);
        }
      return result;
    }


    /**
     * Filter the features to find an exact match for name or a candidate.
     *
     * If one feature f matches exactly or there is exactly one for which
     * isCandidate.test(f) holds, return that candidate. Otherwise, return null
     * if no candidate was found, or create an error and return Types.f_ERROR if
     * several candidates were found.
     *
     * @param pos source position of the access, for error reporting.
     *
     * @param name the name to search for an exact match
     *
     * @param isCandidate predicate to decide if a feature is a candidate even
     * if its name is not an exact match.
     */
    Feature filter(SourcePosition pos, FeatureName name, java.util.function.Predicate<Feature> isCandidate)
    {
      var match = false;
      var found = new List<Feature>();
      for (var f : features.entrySet())
        {
          var ff = f.getValue();
          var fn = f.getKey();
          if (fn.equalsExceptId(name))  /* an exact match, so use it: */
            {
              check
                (Errors.count() > 0 || !match);
              found = new List<>(ff);
              match = true;
            }
          else if (!match && isCandidate.test(ff))
            { /* no exact match, but we have a candidate to check later: */
              found.add(ff);
            }
        }
      return switch (found.size())
        {
        case 0 -> null;
        case 1 -> found.get(0);
        default ->
        {
          FeErrors.ambiguousCallTargets(pos, name, found);
          yield Types.f_ERROR;
        }
        };
    }
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
  private Feature findFieldDefInScope(String name, Call call, Assign assign, Destructure destructure, Feature inner)
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
    for (var f : arguments)
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
                  c.calledFeature().isAnonymousInnerFeature() &&
                  c.calledFeature().featureName().baseName().startsWith("#anonymous") &&
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

    for (var c : contract.req)
      {
        c.cond.visit(fv, this);
      }

    for (var c : contract.ens)
      {
        c.cond.visit(fv, this);
      }

    for (Call p: inherits)
      {
        p.visit(fv, this);
      }

    // then iterate the statements making fields visible as they are declared
    // and checking which one is visible when we reach call:
    if (impl._code != null)
      {
        impl._code.visit(fv, this);
      }

    return curres[1];
  }


  /**
   * Is this feature an argument of its outer feature?
   */
  boolean isArgument()
  {
    if (outer_ != null)
      {
        for (var a : outer_.arguments)
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
  boolean isDeclaredInMainBlock()
  {
    if (outer_ != null)
      {
        var b = outer_.impl._code;
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
   * Find set of candidate features in an unqualified access (call or
   * assignment).  If several features match the name but have different
   * argument counts, return all of them.
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
   * @return in case we found features visible in the call's scope, the features
   * together with the outer feature where they were found.
   */
  FeaturesAndOuter findDeclaredInheritedOrOuterFeatures(String name, Call call, Assign assign, Destructure destructure)
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.RESOLVED_DECLARATIONS));

    FeaturesAndOuter result = new FeaturesAndOuter();
    Feature outer = this;
    Feature inner = null;
    do
      {
        var fs = assign != null ? outer.findDeclaredOrInheritedFeatures(name, 0)
                                : outer.findDeclaredOrInheritedFeatures(name);
        if (fs.size() >= 1)
          {
            List<FeatureName> fields = new List<>();
            for (var e : fs.entrySet())
              {
                var fn = e.getKey();
                var f = e.getValue();
                if (f.isField() && (f.outer()==null || f.outer().resultField_ != f))
                  {
                    fields.add(fn);
                  }
              }
            if (!fields.isEmpty())
              {
                var f = outer.findFieldDefInScope(name, call, assign, destructure, inner);
                fs = new TreeMap<>(fs);
                // if we found f in scope, remove all other entries, otherwise remove all entries within this since they are not in scope.
                for (var fn : fields)
                  {
                    var fi = fs.get(fn);
                    if (f != null || fi.outer() == this && !fi.isArtificialField())
                      {
                        fs.remove(fn);
                      }
                  }
                if (f != null)
                  {
                    fs.put(f.featureName(), f);
                  }
              }
          }
        result.features = fs;
        result.outer = outer;
        inner = outer;
        outer = outer.outer();
      }
    while ((result.features.isEmpty()) && (outer != null));

    return result;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return
      visibility+" "+
      Consts.modifierToString(modifiers)+
      returnType + " "+
      _featureName.baseName()+
      generics+
      (arguments.isEmpty() ? "" : "("+arguments+")")+
      (inherits.isEmpty() ? "" : " : "+inherits)+
      contract+
      impl.toString();
  }


  /**
   * resultTypeRaw returns the result type of this feature using the
   * formal generic argument.
   *
   * @return this feature's result type using the formal generics, null in
   * case the type is currently unknown (in particular, in case of a type
   * inference from a field declared later).
   */
  private Type resultTypeRaw()
  {
    Type result;

    check
      (state().atLeast(State.RESOLVING_TYPES));

    if (resultType_ != null)
      {
        result = resultType_;
      }
    else if (outer() != null && this == outer().resultField())
      {
        result = outer().resultTypeRaw();
      }
    else if (impl.kind_ == Impl.Kind.FieldDef ||
             impl.kind_ == Impl.Kind.FieldActual)
      {
        check
          (!state().atLeast(State.TYPES_INFERENCED));
        result = impl._initialValue.typeOrNull();
      }
    else if (impl.kind_ == Impl.Kind.RoutineDef)
      {
        check
          (!state().atLeast(State.TYPES_INFERENCED));
        result = impl._code.typeOrNull();
      }
    else if (returnType.isConstructorType())
      {
        result = thisType();
      }
    else if (returnType == NoType.INSTANCE)
      {
        result = Types.resolved.t_unit; // may be the result of intrinsic or abstract feature
      }
    else
      {
        result = returnType.functionReturnType();
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
  private Type resultTypeRaw(List<Type> actualGenerics)
  {
    check
      (state().atLeast(State.RESOLVING_TYPES));

    Type result = resultTypeRaw();
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
  Type resultTypeIfPresent(Resolution res, List<Type> generics)
  {
    if (!state_.atLeast(State.RESOLVING_TYPES))
      {
        res.resolveDeclarations(this);
        resolveTypes(res);
      }
    Type result = resultTypeRaw(generics);
    if (result != null)
      {
        result.findGenerics(outer());
      }
    return result;
  }


  /**
   * After type resolution, resultType returns the result type of this
   * feature using the formal generic argument.
   *
   * @return the result type, t_ERROR in case of an error. Never null.
   */
  public Type resultType()
  {
    if (PRECONDITIONS) require
      (Errors.count() > 0 || state_.atLeast(State.RESOLVED_TYPES));

    Type result = state_.atLeast(State.RESOLVED_TYPES) ? resultTypeRaw() : null;
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
  Type resultTypeForTypeInference(SourcePosition rpos, Resolution res, List<Type> generics)
  {
    Type result = resultTypeIfPresent(res, generics);
    if (result == null)
      {
        // NYI: It would be nice to output the whole cycle here as part of the detail message
        Errors.error(rpos,
                     "Illegal forward or cyclic type inference",
                     "The definition of a field using \":=\", or of a feature or function\n" +
                     "using \"=>\" must not create cyclic type dependencies.\n"+
                     "Referenced feature: " + qualifiedName() + " at " + pos.show());
        result = Types.t_ERROR;
      }
    return result;
  }


  /**
   * Check if this is a built in primitive.  For these, the type of an outer
   * reference for inner features is not a reference, but a copy of the value
   * itself since there are no inner features to modify the value.
   */
  public boolean isBuiltInPrimitive()
  {
    return
      (  outer_ != null)
      && outer_.outer_ == null
      && (   "i8"  .equals(_featureName.baseName())
          || "i16" .equals(_featureName.baseName())
          || "i32" .equals(_featureName.baseName())
          || "i64" .equals(_featureName.baseName())
          || "u8"  .equals(_featureName.baseName())
          || "u16" .equals(_featureName.baseName())
          || "u32" .equals(_featureName.baseName())
          || "u64" .equals(_featureName.baseName())
          || "f32" .equals(_featureName.baseName())
          || "f64" .equals(_featureName.baseName())
          || "bool".equals(_featureName.baseName()));
  }


  /**
   * thisType returns the type of this feature's frame object.  This can be
   * called even if !hasThisType() since thisClazz() is used also for abstract
   * or intrinsic feature to determine the resultClazz().
   *
   * @return this feature's frame object
   */
  public Type thisType()
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.FINDING_DECLARATIONS));

    Type result = thisType_;
    if (result == null)
      {
        result = this == Types.f_ERROR
          ? Types.t_ERROR
          : new Type(pos, _featureName.baseName(), generics.asActuals(), null, this, Type.RefOrVal.LikeUnderlyingFeature);
        thisType_ = result;
      }
    if (state_.atLeast(State.RESOLVED_TYPES))
      {
        result = Types.intern(result);
      }

    if (POSTCONDITIONS) ensure
      (result != null,
       Errors.count() > 0 || result.isRef() == isThisRef(),
       // does not hold if feature is declared repeatedly
       Errors.count() > 0 || result.featureOfType() == this,
       true || // this condition is very expensive to check and obviously true:
       !state_.atLeast(State.RESOLVED_TYPES) || result == Types.intern(result)
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
    return impl != Impl.INTRINSIC && impl != Impl.ABSTRACT
      && !isField();
  }


  /**
   * qualifiedName returns the qualified name of this feature
   *
   * @return the qualified name, e.g. "fuzion.std.out.println"
   */
  public String qualifiedName()
  {
    Feature o = this.outer_;
    return
      (o        == null) ? UNIVERSE_NAME :
      (o.outer_ == null) ? _featureName.baseName() : o.qualifiedName()+"."+_featureName.baseName();
  }


  public FeatureName featureName()
  {
    check(arguments.size() == _featureName.argCount());
    return _featureName;
  }

  /**
   * Compare this to other for sorting Feature
   */
  public int compareTo(Feature other)
  {
    int result;
    if (this == other)
      {
        result = 0;
      }
    else if ((this.outer_ == null) &&  (other.outer_ != null))
      {
        result = -1;
      }
    else if ((this.outer_ != null) &&  (other.outer_ == null))
      {
        result = +1;
      }
    else
      {
        result = (this.outer_ != null) ? this.outer_.compareTo(other.outer_)
                                       : 0;
        if (result == 0)
          {
            result = featureName().compareTo(other.featureName());
          }
      }
    return result;
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
   * allInnerAndInheritedFeatures returns a complete set of inner features, used
   * by Clazz.layout and Clazz.hasState.
   *
   * @return
   */
  public Collection<Feature> allInnerAndInheritedFeatures()
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.RESOLVED));

    TreeSet<Feature> result = new TreeSet();

    result.addAll(declaredFeatures_.values());
    for (Call p : inherits)
      {
        Feature cf = p.calledFeature();
        check
          (Errors.count() > 0 || cf != null);

        if (cf != null)
          {
            result.addAll(cf.allInnerAndInheritedFeatures());
          }
      }

    return result;
  }


  /**
   * featureIndex
   *
   * @return
   */
  public int featureIndex()
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.RESOLVED_FEATURE_INDEX));

    check
      (featureIndex != -1,
       outer() == null || featureIndex < outer().declaredFeatures().size());

    return featureIndex;
  }


  /**
   * Number of feature indices given away for inner features.
   */
  private int numFeatureIndices = 0;

  /**
   *
   */
  private String indices_for;

  /**
   * Obtain a new feature index for this.
   */
  public int newFeatureIndex(String name) {
    indices_for = indices_for + "\n"+numFeatureIndices+": "+name;
    if (Errors.count() == 0 && declaredOrInheritedFeatures().size() <= numFeatureIndices) {
      System.out.println(""+declaredOrInheritedFeatures().size()+" >= "+numFeatureIndices+" for >>"+this._featureName.baseName()+"<<");
      //      System.out.println("INNER: "+declaredOrInheritedFeatures());
      System.out.println(indices_for);
    }
    if (PRECONDITIONS) require
      (Errors.count() > 0 || declaredOrInheritedFeatures().size() > numFeatureIndices);

    int result;
    if (Errors.count() > 0 && numFeatureIndices >= declaredFeatures().size())
      {
        result = 0;
      }
    else
      {
        result = numFeatureIndices;
        numFeatureIndices = result + 1;
      }

    if (POSTCONDITIONS) ensure
      (result >= 0,
       result < numFeatureIndices,
       result < declaredFeatures().size(),
       numFeatureIndices <= declaredFeatures().size());

    return result;
  }


  /**
   * outerRefName
   *
   * @return
   */
  private String outerRefName()
  {
    if (PRECONDITIONS) require
      (outer_ != null);

    return "#^" + qualifiedName();
  }


  /**
   * Has the frame object of this feature a ref type?
   */
  public boolean isThisRef()
  {
    return returnType == RefType.INSTANCE;
  }


  /**
   * If outer is a value type, we can either store its address in the inner
   * feature's data, or we can copy the value if it is small enough and
   * immutable.
   *
   * @return true iff outerRef is the copy of an outer value type, false iff
   * otuerRef is the address of an outer value type or a reference to an outer
   * reference type.
   */
  public boolean isOuterRefCopyOfValue()
  {
    if (PRECONDITIONS) require
      (outer_ != null);

    // if outher is a small and immutable value type, we can copy it:
    return this.outer_.isBuiltInPrimitive();  // NYI: We might copy user defined small types as well
  }


  /**
   * If outer is a value type, we can either store its address in the inner
   * feature's data, or we can copy the value if it is small enough and
   * immutable.
   *
   * @return true iff outerRef is the address of an outer value type, false iff
   * otuerRef is the address of an outer value type or a reference to an outer
   * reference type.
   */
  public boolean isOuterRefAdrOfValue()
  {
    if (PRECONDITIONS) require
      (outer_ != null);

    return !this.outer_.isThisRef() && !isOuterRefCopyOfValue();
  }


  /**
   * Add implicit field to the outer feature of this.
   */
  private void addOuterRef()
  {
    if (PRECONDITIONS) require
      (this.outer_ != null,
       state_ == State.FINDING_DECLARATIONS);

    Feature o = this.outer_;
    if (impl._code != null || contract != null)
      {
        Type outerRefType = isOuterRefAdrOfValue() ? Types.t_ADDRESS
                                                   : o.thisType();
        outerRef_ = new Feature(pos,
                                Consts.VISIBILITY_PRIVATE,
                                outerRefType,
                                outerRefName(),
                                this);
      }
  }


  /**
   * outerRef returns the field of this feature that refers to the
   * outer field.
   */
  public Feature outerRef()
  {
    if (PRECONDITIONS) require
      (outer() != null,
       state_.atLeast(State.RESOLVED_DECLARATIONS) &&
       (!state_.atLeast(State.CHECKING_TYPES2) || outerRef_ != null));

    Feature result = outerRef_;

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * Check if this is an outer ref field.
   */
  public boolean isOuterRef()
  {
    return
      outer_ != null &&
      this == outer_.outerRef_;
  }

  /**
   * Check if this has an outerRef and return it if this is the case
   *
   * @return the outer ref if it exists, null otherwise.
   */
  public Feature outerRefOrNull()
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.RESOLVED_DECLARATIONS));

    return this.outer_ != null
      ? outerRef()
      : null;
  }


  /**
   * isField
   *
   * @return
   */
  public boolean isField()
  {
    boolean result = false;
    switch (impl.kind_)
      {
      case FieldInit  :
      case FieldDef   :
      case FieldActual:
      case Field      : result = true; break;
      default: break;
      }
    return result;
  }


  /**
   * depth
   *
   * @return
   */
  public int depth()
  {
    int result;
    Feature o = outer();
    result = (o == null)
      ? 0
      : o.depth()+1;
    return result;
  }


  /**
   * Obtain new unique ids for runtime clazz data stored in
   * Clazz.setRuntimeClazz/getRuntimeClazz.
   *
   * @param count the number of ids to reserve
   *
   * @return the first of the ids result..result+count-1 ids reserved.
   */
  public int getRuntimeClazzIds(int count)  // NYI: Used by dev.flang.be.interpreter, REMOVE!
  {
    if (PRECONDITIONS) require
      (state() == State.RESOLVED,
       runtimeClazzIdCount() <= Integer.MAX_VALUE - count);

    int result = runtimeClazzIdCount_;
    runtimeClazzIdCount_ = result + count;

    if (POSTCONDITIONS) ensure
      (result >= 0,
       result < runtimeClazzIdCount());

    return result;
  }

  /**
   * Obtain a new unique id for runtime clazz data stored in
   * Clazz.setRuntimeClazz/getRuntimeClazz.
   *
   * @return the id that was reserved.
   */
  public int getRuntimeClazzId()  // NYI: Used by dev.flang.be.interpreter, REMOVE!
  {
    if (PRECONDITIONS) require
      (state() == State.RESOLVED,
       runtimeClazzIdCount() < Integer.MAX_VALUE);

    int result = getRuntimeClazzIds(1);

    if (POSTCONDITIONS) ensure
      (result >= 0,
       result < runtimeClazzIdCount());

    return result;
  }

  /**
   * Total number of ids crated by getRuntimeClazzId[s].
   *
   * @return the id count.
   */
  public int runtimeClazzIdCount()  // NYI: Used by dev.flang.be.interpreter, REMOVE!
  {
    int result = runtimeClazzIdCount_;

    if (POSTCONDITIONS) ensure
      (result >= 0);

    return result;
  }


  /**
   * Check if this is equal to or inherits from parent
   *
   * @param parent a loaded feature
   *
   * @return true iff this is a heir of parent.
   */
  public boolean inheritsFrom(Feature parent)
  {
    if (PRECONDITIONS) require
      (state_.atLeast(State.LOADED),
       parent != null && parent.state().atLeast(State.LOADED));

    if (this == parent)
      {
        return true;
      }
    else
      {
        for (Call p : inherits)
          {
            if (p.calledFeature().inheritsFrom(parent))
              {
                return true;
              }
          }
      }
    return false;
  }


  /**
   * Is this a non-genenric feature.
   */
  boolean isNonGeneric()
  {
    if (PRECONDITIONS) require
      (outer() != null);

    return
      (generics == FormalGenerics.NONE);
  }


  /**
   * Are calls to this feature performed using dynamic binding?
   */
  boolean isDynamic()
  {
    if (PRECONDITIONS) require
      (this == Types.f_ERROR || outer() != null);

    return
      this != Types.f_ERROR &&
      isNonGeneric() &&
      !outer().isChoice();
  }


  /**
   * Is this a field of open generic type?
   */
  public boolean isOpenGenericField()
  {
    return isField() && resultType().isOpenGeneric();
  }


  /**
   * For fields of open generic type, this creates actual fields for the actual
   * generic argument.
   *
   * @param res Resolution instance use to resolve this for types.
   *
   * @param i the index of the actual generic argument
   *
   * @return the field that corresponds to the i-th actual generic argument.
   */
  public Feature select(Resolution res, int i)
  {
    if (PRECONDITIONS) require
      (isOpenGenericField(),
       i >= 0,
       state_.atLeast(State.RESOLVED_TYPES),
       !state_.atLeast(State.FINDING_USED_FEATURES));

    if (_selectOpen == null)
      {
        _selectOpen = new ArrayList<>();
      }
    int s = _selectOpen.size();
    while (s <= i)
      {
        Feature f = new Feature(pos, visibility, modifiers, resultType().generic.select(s), "#" + _featureName.baseName() + "." + s, contract);
        f.findDeclarations(outer());
        f.scheduleForResolution(res);
        _selectOpen.add(f);
        s = _selectOpen.size();
      }
    return _selectOpen.get(i);
  }


  /**
   * For fields of open generic type, this returns the actual fields for the
   * actual generic argument.
   *
   * @param i the index of the actual generic argument
   *
   * @return the field that corresponds to the i-th actual generic argument.
   */
  public Feature select(int i)
  {
    if (PRECONDITIONS) require
      (isOpenGenericField(),
       state_.atLeast(State.RESOLVED),
       i < selectSize());

    return _selectOpen.get(i);
  }


  /**
   * For fields of open generic type, this returns the number of actual fields
   * for the actual generic argument.
   *
   * @return the number of fields.
   */
  public int selectSize()
  {
    if (PRECONDITIONS) require
      (isOpenGenericField(),
       state_.atLeast(State.RESOLVED));

    return _selectOpen == null ? 0 : _selectOpen.size();
  }


  /**
   * Find formal generic argument of this feature with given name.
   *
   * @param name the name of a formal generic argument.
   *
   * @return null if name is not the name of a formal generic argument
   * of this. Otherwise, a reference to the formal generic argument.
   */
  public Generic getGeneric(String name)
  {
    Generic result = generics.get(name);

    if (POSTCONDITIONS) ensure
      ((result == null) || (result._name.equals(name) && (result.feature() == this)));
    // result == null ==> for all g in generics: !g.name.equals(name)

    return result;
  }

}

/* end of file */
