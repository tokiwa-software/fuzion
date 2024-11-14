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
 * Source of class AbstractFeature
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;
import dev.flang.util.StringHelpers;


/**
 * AbstractFeature represents a Fuzion feature in the front end.  This feature
 * might either be part of the abstract syntax tree or part of a binary module
 * file.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractFeature extends Expr implements Comparable<AbstractFeature>
{


  /*------------------------------  enums  ------------------------------*/


  /**
   * The basic types of features in Fuzion:
   */
  public enum Kind
  {
    Routine,
    Field,
    TypeParameter,
    OpenTypeParameter,
    Intrinsic,
    Abstract,
    Choice,
    Native;

    /**
     * get the Kind that corresponds to the given ordinal number.
     */
    public static Kind from(int ordinal)
    {
      if (PRECONDITIONS) require
        (0 <= ordinal,
         ordinal < values().length);

      if (CHECKS) check
        (values()[ordinal].ordinal() == ordinal);

      return values()[ordinal];
    }
  }


  /*----------------------------  constants  ----------------------------*/


  /**
   * empty list of AbstractFeature
   */
  public static List<AbstractFeature> _NO_FEATURES_ = new List<>();
  static { _NO_FEATURES_.freeze(); }


  /*------------------------  static variables  -------------------------*/


  /**
   * Counter for assigning unique names to cotype() results. This is
   * currently used only for non-constructors since they do not create a type
   * name.
   *
   * NYI (see #285): This might create name clashes since equal ids might be
   * assigned to type features in different modules.
   */
  static int _cotypeId_ = 0;


  /*----------------------------  variables  ----------------------------*/


  /**
   * For a Feature that can be called and hasThisType() is true, this will be
   * set to the abstract type referring to the instance, i.e., the actual value
   * by be _selfType or the _selfType of any heir feature of this or the self
   * type of this after it was inherited by any different outer type.
   *
   * For a feature a.b.c, _thisType is a.this.type.b.this.type.c.this.type.
   */
  private AbstractType _thisType = null;


  /**
   * For a Feature that can be called and hasThisType() is true, this will be
   * set to the abstract type referring to the instance.  Unlike _thisType, this
   * does not permit this to be replaced by an inherited feature, but any outer
   * feature might be.
   *
   * For a feature a.b.c, _thisTypeFixed is a.this.type.b.this.type.c.
   */
  private AbstractType _thisTypeFixed = null;


  /**
   * cached result of valueArguments();
   */
  private List<AbstractFeature> _valueArguments = null;


  /**
   * cached result of typeArguments();
   */
  private List<AbstractFeature> _typeArguments = null;


  /**
   * cached result of cotype()
   */
  private AbstractFeature _cotype = null;


  /**
   * Cached result of generic().
   */
  private Generic _generic;


  /**
   * For a Feature that can be called and hasThisType() is true, this will be
   * set to concrete the frame type during resolution.  This type uses the formal
   * generics as actual generics. For a generic feature, these must be replaced.
   *
   * For a feature a.b.c, _selfType is a.b.c.
   */
  protected AbstractType _selfType = null;


  /**
   * The formal generic arguments of this feature, cached result of generics()
   */
  protected FormalGenerics _generics;


  /**
   * For a type feature, this specifies the base feature the type feature was
   * created for.
   */
  public AbstractFeature _cotypeOrigin;


  /**
   * Flag used in dev.flang.fe.SourceModule to avoid endless recursion when
   * loading inner features from source directories.
   *
   * NYI: CLEANUP: Remove when #462 is fixed.
   */
  public boolean _loadedInner = false;


  /**
   * Reserved fields to be used by dev.flang.air to find used features and to
   * mark features that are called dynamically.
   */
  public HasSourcePosition _usedAt;


  /**
   * Caching used in front end.
   */
  public Object _frontEndData;


  /**
   * Cached result of context();
   */
  private Context _contextCache;


  /*----------------------------  abstract methods  ----------------------------*/



  /**
   * All features that have been found to be directly redefined by this feature.
   * This does not include redefinitions of redefinitions.  For Features loaded
   * from source code, this set is collected during RESOLVING_DECLARATIONS.  For
   * LibraryFeature, this will be loaded from the library module file.
   */
  public abstract Set<AbstractFeature> redefines();


  /**
   * What is this Feature's kind?
   *
   * @return Routine, Field, Intrinsic, Abstract or Choice.
   */
  public abstract Kind kind();


  /**
   * Is this a routine that returns the current instance as its result?
   */
  public abstract boolean isConstructor();


  /**
   * Is this a constructor returning a reference result?
   */
  public abstract boolean isRef();


  /**
   * Visibility of this feature
   */
  public abstract Visi visibility();


  /**
   * the modifiers of this feature
   */
  public abstract int modifiers();


  /**
   * The feature name of this feature.
   */
  public abstract FeatureName featureName();


  /**
   * The inherits calls of this feature.
   * Almost never empty since almost every feature inherits from `Any`.
   */
  public abstract List<AbstractCall> inherits();


  /**
   * The outer of this feature. For universe this returns null.
   */
  public abstract AbstractFeature outer();


  /**
   * All arguments of this feature. This includes type arguments.
   */
  public abstract List<AbstractFeature> arguments();


  /**
   * resultType returns the result type of this feature using.
   *
   * @return the result type, t_ERROR in case of an error.  Never
   * null. Types.t_UNDEFINED in case type inference for this type is cyclic and
   * hence impossible.
   */
  public abstract AbstractType resultType();


  /**
   * The source code position of this feature declaration's result type, null if
   * not available.
   */
  public abstract SourcePosition resultTypePos();


  /**
   * The result field declared automatically in case hasResultField().
   *
   * @return the result or null if this does not have a result field.
   */
  public abstract AbstractFeature resultField();


  /**
   * The outer ref field field in case hasOuterRef().
   *
   * @return the outer ref or null if this does not have an outer ref.
   */
  public abstract AbstractFeature outerRef();


  /**
   * The implementation of this feature.
   *
   * requires isRoutine() == true
   */
  public abstract Expr code();


  /**
   * The contract of this feature.
   */
  public abstract Contract contract();


  /**
   * If this feature has a pre condition or redefines a feature from which it
   * inherits a pre condition, this gives the feature that implements the pre
   * condition check.
   *
   * The preFeature has the same outer feature as the original feature and the
   * same arguments.
   *
   * @return this feature's precondition feature or null if none is needed.
   */
  public abstract AbstractFeature preFeature();


  /**
   * If this feature has a pre condition or redefines a feature from which it
   * inherits a pre condition, this gives the feature that implements the pre
   * condition check resulting in a boolean instead of a fault.
   *
   * The preBoolFeature has the same outer feature as the original feature and
   * the same arguments.
   *
   * @return this feature's precondition bool feature or null if none is needed.
   */
  public abstract AbstractFeature preBoolFeature();


  /**
   * If this feature has a pre condition or redefines a feature from which it
   * inherits a pre condition, this gives the feature that combines a call to
   * preFeature() and a call to this feature.  This is used at call sites as a
   * replacement to a call to this to implement precondition checking.
   *
   * Note that dynamic binding is done by the preAndCallFeature, i.e., on a call
   * `a.f` where `a` is of a ref type `A` containing a reference to an instance
   * of `B`, calling the preAndCallFeature of `a.f` results in checking the
   * precondition of `A.f` and then calling `B.f`, i.e., the precondition that
   * is checked is that of the static type, not the (possibly relaxed)
   * precondition of the dynamic actual type.
   *
   * The preBoolFeature has the same outer feature as the original feature and
   * the same arguments.
   *
   * @return this feature's precondition bool feature or null if none is needed.
   */
  public abstract AbstractFeature preAndCallFeature();


  /**
   * If this feature has a post condition or redefines a feature from which it
   * inherits a post condition, this gives the feature that implements the post
   * condition check.  The postFeature has tha same outer feature as the
   * original feature and the same arguments except for an additional `result`
   * argument in case the feature has a non-unit result.
   */
  public abstract AbstractFeature postFeature();



  /*-----------------------------  methods  -----------------------------*/



  /* pre-implemented convenience functions: */
  public boolean isRoutine() { return kind() == Kind.Routine; }
  public boolean isField() { return kind() == Kind.Field; }
  public boolean isAbstract() { return kind() == Kind.Abstract; }
  public boolean isIntrinsic() { return kind() == Kind.Intrinsic; }
  public boolean isNative() { return kind() == Kind.Native; }
  public boolean isChoice() { return kind() == Kind.Choice; }
  public boolean isTypeParameter() { return switch (kind()) { case TypeParameter, OpenTypeParameter -> true; default -> false; }; }
  public boolean isOpenTypeParameter() { return kind() == Kind.OpenTypeParameter; }

  /**
   * Does this feature has an arrow "=>" in it's signature, i.e. is a function or an intrinsic
   * @return true if the signature contains an arrow "=>"
   */
  public boolean signatureWithArrow()
  {
    return (isRoutine() && !isConstructor()) || isIntrinsic() || isAbstract() || isNative();
  }


  /**
   * Is this base-lib's choice-feature?
   */
  boolean isBaseChoice()
  {
    return this == Types.resolved.f_choice;
  }


  /**
   * get a reference to the outermost feature.
   */
  private AbstractFeature universe()
  {
    if (PRECONDITIONS) require
      (state().atLeast(State.LOADED));

    AbstractFeature r = this;
    while (!r.isUniverse() && r != Types.f_ERROR)
      {
        r = r.outer();
      }
    return r;
  }


  /**
   * is this the outermost feature?
   */
  public boolean isUniverse()
  {
    return false;
  }


  /**
   * returns the qualified name of this feature, relative to feature context, without any special handling for type features.
   * If context is null the full qualified name to universe is returned.
   *
   * @param context the feature to which the name should be relative to, universe if null
   * @return the qualified name, e.g. "fuzion.std.out.println" or "abc.#type.def.#type.THIS#TYPE"
   */
  private String qualifiedName0(AbstractFeature context)
  {
    var n = featureName().baseNameHuman();
    return
      !state().atLeast(State.FINDING_DECLARATIONS) ||
      isUniverse()                                 ||
      outer() == null                              ||
      outer().isUniverse()                         ||
      (context != null && outer().equals(context))     ? n
                                                       : outer().qualifiedName() + "." + n;
  }


  /**
   * qualifiedName returns the qualified name of this feature
   *
   * @return the qualified name, e.g. "fuzion.std.out.println" or "abc.def.this.type" or "abc.def.type".
   */
  public String qualifiedName()
  {
    return qualifiedName(null);
  }

  /**
   * qualifiedName returns the qualified name of this feature, relative to feature context (if context is not null)
   *
   * @param context the feature to which the name should be relative to, universe if null
   * @return the qualified name, e.g. "fuzion.std.out.println" or "abc.def.this.type" or "abc.def.type".
   */
  public String qualifiedName(AbstractFeature context)
  {
    var tfo = state().atLeast(State.FINDING_DECLARATIONS) && outer() != null && outer().isCotype() ? outer().cotypeOrigin() : null;
    return
      /* special type parameter used for this.type in type features */
      isCoTypesThisType() ? (tfo != null ? tfo.qualifiedName(context) : "null") + ".this.type" :

      /* cotype: use original name and add ".type": */
      isCotype()             &&
      cotypeOrigin() != null                 ? cotypeOrigin().qualifiedName(context) + ".type"

      /* a normal feature name */
                                                  : qualifiedName0(context);
  }


  /**
   * Obtain the effective name of this feature when actualGenerics are the
   * actual generics of its outer() feature.
   */
  private FeatureName effectiveName(Resolution res, List<AbstractType> actualGenerics)
  {
    var result = featureName();
    if (hasOpenGenericsArgList(res))
      {
        var argCount = arguments().size() + actualGenerics.size() - outer().generics().list.size();
        if (CHECKS) check
          (Errors.any() || argCount >= 0);
        if (argCount < 0)
          {
            argCount = 0;
          }
        result =  FeatureName.get(result.baseName(),
                                  argCount);
      }
    return result;
  }


  /**
   * Return the state of this feature.
   *
   * This is only relevant for ast.Feature to document the resolution state.
   *
   * NYI: Remove, replace by Resolution.state(Feature).
   */
  public State state()
  {
    return State.RESOLVED;
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
    r.run();
  }


  /**
   * Check if this is an outer ref field.
   */
  public boolean isOuterRef()
  {
    var o = outer();
    return o != null && o.outerRef() == this;
  }


  /**
   * Is this a choice feature, i.e., does it directly inherit from choice? If
   * so, return the actual generic parameters passed to the choice.
   *
   * @return null if this is not a choice feature, the actual generic
   * parameters, i.e, the actual choice types, otherwise.
   */
  public List<AbstractType> choiceGenerics()
  {
    if (PRECONDITIONS) require
      (state().atLeast(State.RESOLVED_DECLARATIONS));

    List<AbstractType> result;

    if (this == Types.f_ERROR)
      {
        result = null;
      }
    else if (isBaseChoice())
      {
        result = generics().asActuals();
      }
    else
      {
        result = null;
        AbstractCall lastP = null;
        for (var p: inherits())
          {
            if (CHECKS) check
              (Errors.any() || p.calledFeature() != null);

            if (p.calledFeature().isChoice())
              {
                if (lastP != null)
                  {
                    AstErrors.repeatedInheritanceOfChoice(p.pos(), lastP.pos());
                  }
                lastP = p;
                result = p.calledFeature().isBaseChoice()
                  ? p.actualTypeParameters()
                  : p.calledFeature().choiceGenerics();
                // we need to do a hand down to get the actual choice generics
                if (!p.calledFeature().isBaseChoice())
                  {
                    var arr = new AbstractType[result.size()];
                    result.toArray(arr);
                    var inh = this.findInheritanceChain(p.calledFeature());
                    result = new List<>(AbstractFeature.handDownInheritance(null, inh, arr, this));
                  }
              }
          }
      }
    return result;
  }


  /**
   * Check if this features argument list contains arguments of open generic
   * type. If this is the case, then the argCount of the feature name may change
   * when inherited.
   *
   * @return true iff arg list has open generic arg.
   */
  public boolean hasOpenGenericsArgList()
  {
    return hasOpenGenericsArgList(null);
  }


  /**
   * Check if this features argument list contains arguments of open generic
   * type. If this is the case, then the argCount of the feature name may change
   * when inherited.
   *
   * @param res resolution used before type resolution is done to resolve
   * argument types. May be null after type resolution.
   *
   * @return true iff arg list has open generic arg.
   */
  boolean hasOpenGenericsArgList(Resolution res)
  {
    boolean result = false;
    AbstractFeature o = this;
    while (o != null && !result)
      {
        for (var g : o.generics().list)
          {
            if (g.isOpen())
              {
                for (AbstractFeature a : arguments())
                  {
                    AbstractType t;
                    if (a instanceof Feature af)
                      {
                        if (res != null)
                          {
                            af.visit(res.resolveTypesOnly(af));
                          }
                        t = af.returnType().functionReturnType();
                      }
                    else
                      {
                        t = a.resultType();
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
   * Get instance of Generic that corresponds to this type parameter.
   *
   * NYI: Since there is a 1-to-1 correspondent between type parameter features
   * and Generic we could remove Generic completely.
   *
   * @return null if name is not the name of a formal generic argument
   * of this. Otherwise, a reference to the formal generic argument.
   */
  public Generic asGeneric()
  {
    if (PRECONDITIONS) require
      (isTypeParameter());

    if (_generic == null)
      {
        _generic = new Generic(this);
      }
    return _generic;
  }


  /**
   * For a type parameter, this gives the ResolvedParametricType instance
   * corresponding to this type parameter.
   */
  public AbstractType asGenericType()
  {
    if (PRECONDITIONS) require
      (isTypeParameter());

    return asGeneric().type();
  }


  /**
   * selfType returns the type of this feature's frame object.  This can be
   * called even if !hasThisType() since thisClazz() is used also for abstract
   * or intrinsic feature to determine the resultClazz().
   *
   * @return this feature's frame object
   */
  public AbstractType selfType()
  {
    if (PRECONDITIONS) require
      (state().atLeast(State.FINDING_DECLARATIONS));

    AbstractType result = _selfType;
    if (result == null)
      {
        result = this == Types.f_ERROR
          ? Types.t_ERROR
          : createThisType();
        _selfType = result;
      }

    if (POSTCONDITIONS) ensure
      (result != null,
       Errors.any() || result.isRef() == isRef(),
       // does not hold if feature is declared repeatedly
       Errors.any() || result.feature() == this);

    return result;
  }


  /**
   * Create '.this.type' for this feature.
   */
  AbstractType thisType()
  {
    return thisType(false);
  }


  /**
   * Create '.this.type' for this feature.
   *
   * @param innerFixed true iff the directly next inner feature for which this
   * is created is fixed.  In this case. the type is exactly selfType(), and not
   * a placeholder for any possible child's type.
   */
  public AbstractType thisType(boolean innerFixed)
  {
    AbstractType result = innerFixed ? _thisTypeFixed : _thisType;

    if (result == null)
      {
        result = selfType();
        var of = outer();
        if (!isUniverse() && of != null && !of.isUniverse())
          {
            result = ResolvedNormalType.newType(result, of.thisType());
          }
        if (innerFixed)
          {
            _thisTypeFixed = result;
          }
        else
          {
            result = result.asThis();
            _thisType = result;
          }
      }
    return result;
  }


  /**
   * Is this a cotype?
   */
  public boolean isCotype()
  {
    return _cotypeOrigin != null;
  }


  /**
   * Is this a type feature?
   */
  public boolean isTypeFeature()
  {
    // NYI: BUG: wrongly returns false for features that a cotype inherits from Type but which are implemented in Any i.e. its outer feature is Any
    return outer() != null && (outer().isCotype() || outer().compareTo(Types.resolved.f_Type) == 0);
  }


  /**
   * Is this the 'THIS_TYPE' type parameter in a cotype?
   */
  public boolean isCoTypesThisType()
  {
    return outer() != null
      && outer().isCotype()
      && outer().typeArguments().get(0) == this;
  }


  /**
   * For a type feature, create the inheritance call for a parent type feature.
   *
   * @param p the source position
   *
   * @param typeParameters the type parameters passed to the call
   *
   * @param res Resolution instance used to resolve types in this call.
   *
   * @param that the original feature that is used to lookup types.
   *
   * @return instance of Call to be used for the parent call in cotype().
   */
  Call typeCall(SourcePosition p, List<AbstractType> typeParameters, Resolution res, AbstractFeature that)
  {
    var o = outer();
    var oc = o == null || o.isUniverse()
      ? new Universe()
      : outer().typeCall(p, new List<>(outer().selfType()), res, that);
    var tf = cotype(res);
    var typeParameters2 = new List<AbstractType>();
    for (var tp : typeParameters)
      {
        var tpa = that.rebaseTypeForCotype(tp);
        typeParameters2.add(typeParameters2.size() == 0 ? tp : tpa);
      }
    return new Call(p,
                    oc,
                    typeParameters2,
                    Expr.NO_EXPRS,
                    tf,
                    tf.selfType());
  }


  /**
   * For a feature 'a', the the type of 'a.this.type' when used within 'a.type',
   * i.e., within 'a's type feature.  The difference between selfType() and
   * selfTypeInCoType() is that the type parameters in the former are the
   * type parameters of 'a', while in the latter they are the type parameter of
   * 'a.this' (who use the same name)..
   */
  public AbstractType selfTypeInCoType()
  {
    var t0 = selfType();
    var tl = new List<AbstractType>();
    boolean first = true;
    for (var ta : cotype().typeArguments())
      {
        if (!first)
          {
            tl.add(ta.asGenericType());
          }
        first = false;
      }
    return t0.applyTypePars(this, tl);
  }


  /**
   * For a given type t that was declared in the context of a non-type feature
   * 'this', rebase this type to be used in 'this.cotype()'.  This means
   * that generics used in t that are generics from 'this' have to be replaced
   * by generics from 'this.cotype()'. Furthermore, resolution of
   * non-generic types used in 't' has to be performed relative to 'this' even
   * when the outer feature is 'this.cotype()'.
   *
   * @param t the type to be moved to the type feature.
   */
  AbstractType rebaseTypeForCotype(AbstractType t)
  {
    var tl = new List<AbstractType>();
    for (var ta0 : typeArguments())
      {
        var ta = new ParsedType(pos(), ta0.featureName().baseName(), UnresolvedType.NONE, null);
        tl.add(ta);
      }
    t = t.applyTypePars(this, tl);
    t = t.clone(this);
    return t;
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
  public AbstractFeature cotype(Resolution res)
  {
    if (PRECONDITIONS) require
      (res != null,
       Errors.any() || !isUniverse(),
       res.state(this).atLeast(State.FINDING_DECLARATIONS),
       !isCotype());

    if (_cotype == null)
      {
        if (hasCotype())
          {
            _cotype = cotype();
          }
        else if (isUniverse())
          {
            if (CHECKS) check
              (Errors.any());
            _cotype = Types.f_ERROR;
          }
        else
          {
            var name = featureName().baseName() + ".";
            if (!isConstructor() && !isChoice())
              {
                name = name + "_" + (_cotypeId_++);
              }
            name = name + FuzionConstants.TYPE_NAME;

            var p = pos();
            var inh = cotypeInherits(res);
            var typeArg = new Feature(p,
                                      Visi.PRIV,
                                      0,
                                      selfType(),
                                      FuzionConstants.COTYPE_THIS_TYPE,
                                      Contract.EMPTY_CONTRACT,
                                      Impl.TYPE_PARAMETER)
              {
                @Override
                public boolean isCoTypesThisType()
                {
                  return true;
                }
              };
            var typeArgs = new List<AbstractFeature>(typeArg);
            for (var t : typeArguments())
              {
                var i = t.isOpenTypeParameter() ? Impl.TYPE_PARAMETER_OPEN
                                                : Impl.TYPE_PARAMETER;
                var constraint0 = t instanceof Feature tf ? tf._returnType.functionReturnType() : t.resultType();
                var constraint = rebaseTypeForCotype(constraint0);
                var ta = new Feature(p, t.visibility(), t.modifiers() & FuzionConstants.MODIFIER_REDEFINE, constraint, t.featureName().baseName(),
                                     Contract.EMPTY_CONTRACT,
                                     i);
                typeArgs.add(ta);
              }

            if (inh.isEmpty() && !Errors.any())
              { // let `Any.type` inherit from `Type`
                if (CHECKS) check
                  (this instanceof Feature && featureName().baseName().equals("Any"));
                inh.add(new Call(pos(), "Type"));
              }
            existingOrNewCotype(res, name, typeArgs, inh);
          }
      }
    return _cotype;
  }


  /**
   * Helper method for cotype(res) to create the list of inherits calls of
   * this' type feature.
   *
   * @param res Resolution instance used to resolve this for types.
   */
  private List<AbstractCall> cotypeInherits(Resolution res)
  {
    if (PRECONDITIONS) require
      (state().atLeast(State.RESOLVED_INHERITANCE));

    return inherits()
      .stream()
      .filter(pc -> pc.calledFeature() != Types.f_ERROR)
      .map(pc -> pc.typeCall(pos(), res, this))
      .collect(List.collector());
  }


  /**
   * Helper method for cotype to create a new feature with given name and
   * inherits clause iff no such feature exists in outer().cotype().
   *
   * The new type feature will be stored in _cotype.
   *
   * @param res Resolution instance used to resolve this for types.
   *
   * @param name the name of the type feature to be created
   *
   * @param typeArgs arguments of the type feature.
   * NYI: OPTIMIZATION: typeArgs should be determined within this method and
   * only when needed.
   *
   * @param inh the inheritance clause of the new type feature.
   */
  private void existingOrNewCotype(Resolution res, String name, List<AbstractFeature> typeArgs, List<AbstractCall> inh)
  {
    if (PRECONDITIONS) require
      (!isUniverse());

    var outerType = outer().isUniverse()    ? universe() :
                    outer().isCotype() ? outer()
                                            : outer().cotype(res);

    _cotype = res
      ._module
      .declaredOrInheritedFeatures(outerType,
                                   FeatureName.get(name, typeArgs.size()))
      .getFirstOrNull();

    if (_cotype == null)
      {
        var p = pos();
        var cotype = new Feature(p, visibility().typeVisibility(), 0, NoType.INSTANCE, new List<>(name), typeArgs,
                                      inh,
                                      Contract.EMPTY_CONTRACT,
                                      new Impl(p, new Block(new List<>()), Impl.Kind.Routine));

        // we need to set _cotype early to avoid endless recursion during
        // res._module.addCotype for `Any.type`:
        _cotype = cotype;

        cotype._cotypeOrigin = this;
        res._module.addCotype(outerType, cotype);
      }
  }


  /**
   * For a type feature, this specifies the base feature the type feature was
   * created for.
   */
  public AbstractFeature cotypeOrigin()
  {
    if (CHECKS) check
      (isCotype());

    return _cotypeOrigin;
  }



  /**
   * Check if a cotype exists already, either because this feature was
   * loaded from a library .fum file that includes a cotype, or because one
   * was created explicitly using cotype(res).
   */
  public boolean hasCotype()
  {
    return _cotype != null || existingCotype() != null || this == Types.f_ERROR;
  }


  /**
   * Return existing cotype.
   */
  public AbstractFeature cotype()
  {
    if (PRECONDITIONS) require
      (isCotype() || hasCotype());

    if (_cotype == null)
      {
        _cotype = this == Types.f_ERROR ? this :
                       isCotype()       ? Types.resolved.f_Type
                                             : existingCotype();
      }
    var result = _cotype;

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * If we have an existing type feature (stored in a .fum library file), return that
   * type feature. return null otherwise.
   */
  protected AbstractFeature existingCotype()
  {
    return null;
  }


  /**
   * createThisType returns a new instance of the type of this feature's frame
   * object.  This can be called even if !hasThisType() since thisClazz() is
   * used also for abstract or intrinsic features to determine the resultClazz().
   *
   * @return this feature's frame object
   */
  protected AbstractType createThisType()
  {
    if (PRECONDITIONS) require
      (state().atLeast(State.FINDING_DECLARATIONS));

    var o = isUniverse() || outer().isUniverse() ? null : outer().selfType().asThis();
    var g = generics().asActuals();
    var result = ResolvedNormalType.create(g, g, o, this);

    if (POSTCONDITIONS) ensure
      (result != null,
       Errors.any() || result.isRef() == isRef(),
       // does not hold if feature is declared repeatedly
       Errors.any() || result.feature() == this,
       result.feature().generics().sizeMatches(result.generics()));

    return result;
  }


  /**
   * resultTypeIfPresent returns the result type of this feature using the
   * formal generic argument.
   *
   * @return this feature's result type, null in case the type is currently
   * unknown since the type inference is incomplete.
   */
  AbstractType resultTypeIfPresent(Resolution res)
  {
    return resultTypeIfPresentUrgent(res, false);
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
  AbstractType resultTypeIfPresentUrgent(Resolution res, boolean urgent)
  {
    return resultType();
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
    // NYI: Check if there is any chance a library feature used in a choice makes illegal closure accesses
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
    r.run();
  }


  /**
   * Check if this is a built in primitive.  For these, the type of an outer
   * reference for inner features is not a reference, but a copy of the value
   * itself since there are no inner features to modify the value.
   */
  public boolean isBuiltInPrimitive()
  {
    return !isUniverse()
      && outer() != null
      && outer().isUniverse()
      && (   "i8"  .equals(featureName().baseName())
          || "i16" .equals(featureName().baseName())
          || "i32" .equals(featureName().baseName())
          || "i64" .equals(featureName().baseName())
          || "u8"  .equals(featureName().baseName())
          || "u16" .equals(featureName().baseName())
          || "u32" .equals(featureName().baseName())
          || "u64" .equals(featureName().baseName())
          || "f32" .equals(featureName().baseName())
          || "f64" .equals(featureName().baseName())
          || "bool".equals(featureName().baseName()));
  }


  /**
   * If outer is a value type, we can either store its address in the inner
   * feature's data, or we can copy the value if it is small enough and
   * immutable.
   *
   * @return true iff outerRef is the copy of an outer value type, false iff
   * outerRef is the address of an outer value type or a reference to an outer
   * reference type.
   */
  private boolean isOuterRefCopyOfValue()
  {
    if (PRECONDITIONS) require
      (outer() != null);

    // if outer is a small and immutable value type, we can copy it:
    return this.outer().isBuiltInPrimitive();  // NYI: We might copy user defined small types as well
  }


  /**
   * If outer is a value type, we can either store its address in the inner
   * feature's data, or we can copy the value if it is small enough and
   * immutable.
   *
   * @return true iff outerRef is the address of an outer value type, false iff
   * outerRef is the address of an outer value type or a reference to an outer
   * reference type.
   */
  public boolean isOuterRefAdrOfValue()
  {
    if (PRECONDITIONS) require
      (outer() != null);

    return !this.outer().isRef() && !isOuterRefCopyOfValue();
  }


  /**
   * Does this feature define a type?
   *
   * This is the case for constructors and choice features.
   *
   * Type features and any features declared within type features do not declare
   * types.  Allowing this would open up Pandora's box of having instances of
   * the f.type.type, f.type.type.type, f.type.type.type.type, ...
   */
  public boolean definesType()
  {
    return (isConstructor() || isChoice())
      && !isUniverse()
      && !isCotype();
  }


  /**
   * true iff this feature as a result field. This is the case if the returnType
   * is not a constructortype (self, value, single) and this is not a field.
   *
   * @return true iff this has a result field.
   */
  public boolean hasResultField()
  {
    return isRoutine() && !isConstructor();
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
   * @param res in case this is called during the front end phase, this is the
   * resolution instance, null otherwise.
   *
   * @param f a feature that is declared in or inherited by this feature
   *
   * @param fn a feature name within this feature
   *
   * @param p an inheritance call in heir inheriting from this
   *
   * @param heir the heir that contains the inheritance call p
   *
   * @return the new feature name as seen within heir.
   */
  public FeatureName handDown(Resolution res, AbstractFeature f, FeatureName fn, AbstractCall p, AbstractFeature heir)
  {
    if (PRECONDITIONS) require
      (this != heir);

    if (f.outer() == p.calledFeature())
      {
        // NYI: This might be incorrect in case p.actualTypeParameters() is inferred but not set yet.
        fn = f.effectiveName(res, p.actualTypeParameters());
      }

    return fn;
  }


  /**
   * Determine the actual types of an array of types in this feature after it
   * was inherited by heir. The types may change on the way due to formal
   * generics being replaced by actual generic arguments on the way.
   *
   * Due to open generics, even the number of types may change through
   * inheritance.
   *
   * @param res resolution instance, required only when run in front end phase,
   * null otherwise.
   *
   * @param a an array of types to be handed down
   *
   * @param heir a feature that inherits from outer()
   *
   * @return the types from the argument array a has seen this within
   * heir. Their number might have changed due to open generics.
   */
  public AbstractType[] handDown(Resolution res, AbstractType[] a, AbstractFeature heir)  // NYI: This does not distinguish different inheritance chains yet
  {
    if (PRECONDITIONS) require
      (heir != null,
       state().atLeast(State.RESOLVING_TYPES));

    if (heir != Types.f_ERROR)
      {
        var inh = heir.findInheritanceChain(outer());
        if (CHECKS) check
          (inh != null);

        if (inh != null)
          {
            a = AbstractFeature.handDownInheritance(res, inh, a, heir);
          }
      }
    return a;
  }


  /**
   * Helper for handDown() to hand down an array of types along a given inheritance chain.
   *
   * @param res the resolution instance
   *
   * @param inh the inheritance chain from the parent down to the child
   *
   * @param a the original array of types that is to be handed down
   *
   * @param heir the feature that inherits the types
   *
   * @return a new array of types as they are visible in heir. The length might
   * be different due to open type parameters being replaced by a list of types.
   */
  private static AbstractType[] handDownInheritance(Resolution res, List<AbstractCall> inh, AbstractType[] a, AbstractFeature heir)
  {
    for (AbstractCall c : inh)
      {
        for (int i = 0; i < a.length; i++)
          {
            var ti = a[i];
            if (ti.isOpenGeneric())
              {
                var frmlTs = ti.genericArgument().replaceOpen(c.actualTypeParameters());
                a = Arrays.copyOf(a, a.length - 1 + frmlTs.size());
                for (var tg : frmlTs)
                  {
                    a[i] = tg;
                    i++;
                  }
                i = i - 1;
              }
            else
              {
                var actualTypes = c.actualTypeParameters();
                actualTypes = FormalGenerics.resolve(res, actualTypes, heir);
                ti = ti.applyTypePars(c.calledFeature(), actualTypes);
                a[i] = ti;
              }
          }
      }
    return a;
  }


  /**
   * Get the actual type from a type used in this feature after it was inherited
   * by heir.  During inheritance, formal generics may be replaced by actual
   * generics.
   *
   * @param t a type used in this feature, must not be an open generic type
   * (which can be replaced by several types during inheritance).
   *
   * @param heir an heir of this, might be equal to this.
   *
   * @return interned type that represents t seen as it is seen from heir.
   */
  public AbstractType handDownNonOpen(Resolution res, AbstractType t, AbstractFeature heir)
  {
    if (PRECONDITIONS) require
      (!t.isOpenGeneric(),
       heir != null,
       res == null || res.state(heir).atLeast(State.CHECKING_TYPES));

    var a = handDown(res, new AbstractType[] { t }, heir);

    if (CHECKS) check
      (Errors.any() || a.length == 1);

    return a.length == 1 ? a[0] : Types.t_ERROR;
  }


  /**
   * Find the chain of inheritance calls from this to its parent ancestor.
   *
   * NYI: Repeated inheritance handling is still missing, there might be several
   * different inheritance chains, need to check if they lead to the same result
   * (wrt generic arguments) or renaming/selection of the preferred
   * implementation.
   *
   * @param ancestor the ancestor feature this inherits from
   *
   * @return The inheritance chain from the inheritance call to ancestor at the
   * first index down to the last inheritance call within this.  Empty list in
   * case this == ancestor, null in case this does not inherit from ancestor.
   */
  public List<AbstractCall> tryFindInheritanceChain(AbstractFeature ancestor)
  {
    List<AbstractCall> result;
    if (this == ancestor)
      {
        result = new List<>();
      }
    else
      {
        result = null;
        for (var c : inherits())
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
   * (wrt generic arguments) or renaming/selection of the preferred
   * implementation.
   *
   * @param ancestor the ancestor feature this inherits from
   *
   * @return The inheritance chain from the inheritance call to ancestor at the
   * first index down to the last inheritance call within this.  Empty list in
   * case this == ancestor, never null.
   */
  public List<AbstractCall> findInheritanceChain(AbstractFeature ancestor)
  {
    if (PRECONDITIONS) require
      (ancestor != null);

    List<AbstractCall> result = tryFindInheritanceChain(ancestor);

    if (POSTCONDITIONS) ensure
      (this == Types.f_ERROR || ancestor == Types.f_ERROR || Errors.any() || result != null);

    return result;
  }


  /**
   * Check if this is equal to or inherits from parent
   *
   * @param parent a loaded feature
   *
   * @return true iff this is an heir of parent.
   */
  public boolean inheritsFrom(AbstractFeature parent)
  {
    if (PRECONDITIONS) require
                         (state().atLeast(State.LOADED),
       parent != null && parent.state().atLeast(State.LOADED));

    if (this == parent)
      {
        return true;
      }
    else
      {
        for (var p : inherits())
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
   * Does this Feature have an outer ref field, i.e., is outerRef() != null?
   */
  public boolean hasOuterRef()
  {
    return
      (this != Types.f_ERROR) &&

      // tricky: abstract features may have contracts
      // that use outer references.
      // !isAbstract() &&

      // outer is required for backend code generator
      // !isIntrinsic() &&

      !isField() &&
      !isChoice() &&
      !isUniverse() &&
      !outer().isUniverse();
  }


  /**
   * Is this a field of open generic type?
   */
  public boolean isOpenGenericField()
  {
    return isField() && resultType().isOpenGeneric();
  }


  public void visitCode(FeatureVisitor fv)
  {
    for (var c: inherits())
      {
        var nc = c.visit(fv, this);
        if (CHECKS) check
          (c == nc); // NYI: This will fail when doing funny stuff like inherit from bool.infix &&, need to check and handle explicitly
      }
    if (isRoutine())
      {
        code().visit(fv, this);
      }
  }


  /**
   * Call v.action(s) on all expressions s within this feature.
   *
   * @param v the action to be performed on the expressions.
   */
  public void visitExpressions(ExpressionVisitor v)
  {
    for (var c: inherits())
      {
        c.visitExpressions(v);
      }
    if (isRoutine())
      {
        code().visitExpressions(v);
      }
  }


  /**
   * Determine the formal argument types of this feature.
   *
   * @return a new array containing this feature's formal argument types.
   */
  public AbstractType[] argTypes()
  {
    int argnum = 0;
    var result = new AbstractType[arguments().size()];
    for (var frml : arguments())
      {
        if (CHECKS) check
          (Errors.any() || frml.state().atLeast(State.RESOLVED_DECLARATIONS));

        var frmlT = frml.resultType();

        result[argnum] = frmlT;
        argnum++;
      }

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * Is this feature marked with the `fixed` modifier. If so, this feature is
   * not inherited, i.e., we know that at runtime, the outer feature's type is
   * outer().selfType() and not a heir of outer().  However, outer().outer()
   * could might be a heir.
   */
  public boolean isFixed()
  {
    return (modifiers() & FuzionConstants.MODIFIER_FIXED) != 0 || isTypeParameter();
  }


  /**
   * Get inner feature with given name, ignoring the argument count.
   *
   * @param name the name of the feature within this.
   *
   * @return the found feature or null in case of an error.
   */
  public AbstractFeature get(AbstractModule mod, String name)
  {
    return get(mod, name, -1);
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
  public AbstractFeature get(AbstractModule mod, String name, int argcount)
  {
    AbstractFeature result = Types.f_ERROR;
    var d = mod.declaredFeatures(this);
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
        check
          (this != Types.f_ERROR || Errors.any());
        if (this != Types.f_ERROR)
          {
            AstErrors.internallyReferencedFeatureNotFound(pos(), name, this, name);
          }
      }
    else
      { // NYI: This might happen if the user adds additional features
        // with different argCounts. name should contain argCount to
        // avoid this
        AstErrors.internallyReferencedFeatureNotUnique(pos(), name + (argcount >= 0 ? " (" + StringHelpers.argumentsString(argcount) : ""), set);
      }
    return result;
  }


  /**
   * List of arguments that are values, i.e., not type parameters or effects.
   */
  public List<AbstractFeature> valueArguments()
  {
    if (_valueArguments == null)
      {
        var args = arguments();
        if (args.stream().anyMatch(a -> a.isTypeParameter()))
          {
            _valueArguments = new List<>();
            _valueArguments.addAll(args.stream().filter(a -> !a.isTypeParameter()).toList());
          }
        else
          {
            _valueArguments = args;
          }
      }
    return _valueArguments;
  }


  /**
   * Is this feature an argument of its outer feature, but not a type argument?
   */
  boolean isValueArgument()
  {
    var result = false;
    var o = outer();
    if (o != null)
      {
        for (var va : o.valueArguments())
          {
            result = result || va == this;
          }
      }
    return result;
  }


  /**
   * List of arguments that are types, i.e., not type parameters or effects.
   */
  public List<AbstractFeature> typeArguments()
  {
    if (_typeArguments == null)
      {
        var args = arguments();
        if (args.stream().anyMatch(a -> a.isTypeParameter()))
          {
            _typeArguments = new List<>();
            _typeArguments.addAll(args.stream().filter(a -> a.isTypeParameter()).toList());
          }
        else if (args.stream().anyMatch(a -> !a.isTypeParameter()))
          {
            _typeArguments = new List<>();
          }
        else
          {
            _typeArguments = args;
          }
      }
    return _typeArguments;
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

    throw new Error("addTypeArgument only possible for Feature, not for "+getClass());
  }


  /**
   * The formal generic arguments of this feature
   */
  public FormalGenerics generics()
  {
    if (_generics == null)
      {
        // Recreate FormalGenerics from typeParameters
        // NYI: Remove, FormalGenerics should use AbstractFeature.typeArguments() instead of its own list of Generics.
        if (typeArguments().isEmpty())
          {
            _generics = FormalGenerics.NONE;
          }
        else
          {
            var l = new List<Generic>();
            for (var a0 : typeArguments())
              {
                l.add(a0.asGeneric());
              }
            _generics = new FormalGenerics(l);
          }
      }
    return _generics;
  }


  /**
   * Return the index of this type parameter within the type arguments of its
   * outer feature.
   *
   * @return the index such that formalGenerics.get(result)) this
   */
  public int typeParameterIndex()
  {
    if (PRECONDITIONS) require
      (isTypeParameter());

    var result = 0;
    for (var tp : outer().typeArguments())
      {
        if (tp == this)
          {
            return result;
          }
        result++;
      }
    throw new Error("AbstractFeature.typeParameterIndex() failed for " + this);
  }


  /**
   * Some Expressions do not produce a result, e.g., a Block that is empty or
   * whose last expression is not an expression that produces a result.
   */
  public boolean producesResult()
  {
    return false;
  }


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this or an alternative Expr if the action performed during the
   * visit replaces this by the alternative.
   */
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    throw new Error("not meant to be used...");
  }


  /**
   * Is this feature an argument of its outer feature?
   */
  public boolean isArgument()
  {
    if (outer() != null)
      {
        for (var a : outer().arguments())
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
   * Is this feature a field that is not an argument?
   */
  public boolean isNonArgumentField()
  {
    return isField() && !isArgument();
  }


  /**
   * In contrast to redefines() this does not just contain direct redefines()
   * but also redefinitions of redefinitions of arbitrary depth.
   */
  public Set<AbstractFeature> redefinesFull()
  {
    return redefines()
      .stream()
      .flatMap(x -> Stream.concat(Stream.of(x), x.redefinesFull().stream()))
      .collect(Collectors.toSet());
  }


  /**
   * The Context associated with this feature without any context from
   * statements like `if T : x then`.  This provides a way to get this as an
   * outer feature via `result.outerFeature()'.
   *
   * @return the context for this feature.
   */
  Context context()
  {
    var result = _contextCache;
    if (result == null)
      {
        result = Context.forFeature(this);
        _contextCache = result;
      }

    if (POSTCONDITIONS) ensure
      (result != null,
       result.outerFeature() == this);

    return result;
  }

  /**
   * this feature as a human readable string
   */
  public String toString()
  {
    return visibility() + " " +
      FuzionConstants.modifierToString(modifiers()) +
      (isCotype() ? "type." : "") +
      featureName().baseNameHuman() +
      (arguments().isEmpty() ? "" : "("+arguments()+")") + " " +
      (state().atLeast(State.RESOLVED_TYPES) ? resultType() : "***not yet known***") + " " +
      (inherits().isEmpty() ? "" : ": " + inherits() + " ") +
      ((contract() == Contract.EMPTY_CONTRACT) ? "" : "🤝 ")
       +  "is " + kind();

  }


}

/* end of file */
