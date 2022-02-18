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
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * AbstractFeature is represents a Fuzion feature in the front end.  This
 * feature might either be part of the abstract syntax tree or part of a binary
 * module file.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractFeature extends ANY implements Comparable<AbstractFeature>, HasSourcePosition
{


  /*------------------------------  enums  ------------------------------*/


  /**
   * The basic types of features in Fuzion:
   */
  public enum Kind
  {
    Routine,
    Field,
    Intrinsic,
    Abstract,
    Choice;

    /**
     * get the Kind that corresponds to the given ordinal number.
     */
    public static Kind from(int ordinal)
    {
      if (CHECKS) check
        (values()[ordinal].ordinal() == ordinal);

      return values()[ordinal];
    }
  }


  /*----------------------------  variables  ----------------------------*/



  /**
   * NYI: to be removed: Temporary mapping from Feature to corresponding
   * libraryFeature (if it exists) and back to the ast.Feature.
   *
   * As long as the duality of ast.Feature/fe.LibraryFeature exists, a check for
   * feature equality should be done using sameAs.
   */
  public AbstractFeature _libraryFeature = null; // NYI: remove when USE_FUM is default
  public AbstractFeature libraryFeature() // NYI: remove
  {
    return _libraryFeature == null ? this : _libraryFeature;
  }


  /**
   * For a Feature that can be called and hasThisType() is true, this will be
   * set to the frame type during resolution.  This type uses the formal
   * generics as actual generics. For a generic feature, these must be replaced.
   */
  protected AbstractType _thisType = null;


  /**
   * Reserved fields to be used by dev.flang.air to find used features and to
   * mark features that are called dynamically.
   */
  public HasSourcePosition _usedAt;
  public boolean _calledDynamically;


  /**
   * Caching used in front end.
   */
  public Object _frontEndData;


  /*-----------------------------  methods  -----------------------------*/

  /**
   * All features that have been found to be directly redefined by this feature.
   * This does not include redefintions of redefinitions.  Four Features loaded
   * from source code, this set is collected during RESOLVING_DECLARATIONS.  For
   * LibraryFeature, this will be loaded from the library module file.
   */
  public abstract Set<AbstractFeature> redefines();


  /* pre-implemented convenience functions: */
  public boolean isRoutine() { return kind() == Kind.Routine; }
  public boolean isField() { return kind() == Kind.Field; }
  public boolean isAbstract() { return kind() == Kind.Abstract; }
  public boolean isIntrinsic() { return kind() == Kind.Intrinsic; }
  public boolean isChoice() { return kind() == Kind.Choice; }


  /**
   * What is this Feature's kind?
   *
   * @return Routine, Field, Intrinsic, Abstract or Choice.
   */
  public abstract Kind kind();


  /**
   * Is this an intrinsic feature that creates an instance of its result ref
   * type?
   */
  public abstract boolean isIntrinsicConstructor();


  /**
   * get a reference to the outermost feature.
   */
  public AbstractFeature universe()
  {
    if (PRECONDITIONS) require
      (state().atLeast(Feature.State.LOADED));

    AbstractFeature r = this;
    while (!r.isUniverse())
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
   * qualifiedName returns the qualified name of this feature
   *
   * @return the qualified name, e.g. "fuzion.std.out.println"
   */
  public String qualifiedName()
  {
    var n = featureName().baseName();
    return
      isUniverse()         ||
      outer() == null      ||
      outer().isUniverse()    ? n
                              : outer().qualifiedName() + "." + n;
  }


  /**
   * Obtain the effective name of this feature when actualGenerics are the
   * actual generics of its outer() feature.
   */
  public FeatureName effectiveName(List<AbstractType> actualGenerics)
  {
    var result = featureName();
    if (hasOpenGenericsArgList())
      {
        var argCount = arguments().size() + actualGenerics.size() - outer().generics().list.size();
        if (CHECKS) check
          (argCount >= 0);
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
  Feature.State state()
  {
    return Feature.State.RESOLVED;
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
      (state().atLeast(Feature.State.RESOLVING_TYPES));

    List<AbstractType> result;

    if (this == Types.f_ERROR)
      {
        result = null;
      }
    else if (this.compareTo(Types.resolved.f_choice) == 0)
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
              (Errors.count() > 0 || p.calledFeature() != null);

            if (p.calledFeature() == Types.resolved.f_choice)
              {
                if (lastP != null)
                  {
                    AstErrors.repeatedInheritanceOfChoice(p.pos(), lastP.pos());
                  }
                lastP = p;
                result = p.generics();
              }
          }
      }
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
                        t = af.returnType().functionReturnType();
                        if (!t.checkedForGeneric())
                          {
                            af.visit(Feature.findGenerics);
                          }
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
   * Find formal generic argument of this feature with given name.
   *
   * @param name the name of a formal generic argument.
   *
   * @return null if name is not the name of a formal generic argument
   * of this. Otherwise, a reference to the formal generic argument.
   */
  public Generic getGeneric(String name)
  {
    Generic result = generics().get(name);

    if (POSTCONDITIONS) ensure
      ((result == null) || (result._name.equals(name) && (result.feature() == this)));
    // result == null ==> for all g in generics: !g.name.equals(name)

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
      (state().atLeast(Feature.State.FINDING_DECLARATIONS));

    AbstractType result = _thisType;
    if (result == null)
      {
        result = this == Types.f_ERROR
          ? Types.t_ERROR
          : createThisType();
        _thisType = result;
      }
    if (state().atLeast(Feature.State.RESOLVED_TYPES))
      {
        result = Types.intern(result);
      }

    if (POSTCONDITIONS) ensure
      (result != null,
       Errors.count() > 0 || result.isRef() == isThisRef(),
       // does not hold if feature is declared repeatedly
       Errors.count() > 0 || result.featureOfType() == this,
       true || // this condition is very expensive to check and obviously true:
       !state().atLeast(Feature.State.RESOLVED_TYPES) || result == Types.intern(result)
       );

    return result;
  }


  /**
   * createThisType returns a new instance of the type of this feature's frame
   * object.  This can be called even if !hasThisType() since thisClazz() is
   * used also for abstract or intrinsic feature to determine the resultClazz().
   *
   * @return this feature's frame object
   */
  protected AbstractType createThisType()
  {
    if (PRECONDITIONS) require
      (state().atLeast(Feature.State.FINDING_DECLARATIONS));

    var result = new Type(pos(), featureName().baseName(), generics().asActuals(), null, this, Type.RefOrVal.LikeUnderlyingFeature);

    if (POSTCONDITIONS) ensure
      (result != null,
       Errors.count() > 0 || result.isRef() == isThisRef(),
       // does not hold if feature is declared repeatedly
       Errors.count() > 0 || result.featureOfType() == this,
       true || // this condition is very expensive to check and obviously true:
       !state().atLeast(Feature.State.RESOLVED_TYPES) || result == Types.intern(result)
       );

    return result;
  }


  /**
   * resultTypeRaw returns the result type of this feature using the
   * formal generic argument.
   *
   * @return this feature's result type using the formal generics, null in
   * case the type is currently unknown (in particular, in case of a type
   * inference from a field declared later).
   */
  AbstractType resultTypeRaw()
  {
    return resultType();
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
  AbstractType resultTypeRaw(List<AbstractType> actualGenerics)
  {
    if (CHECKS) check
      (state().atLeast(Feature.State.RESOLVING_TYPES));

    var result = resultTypeRaw();
    if (result != null)
      {
        result = result.actualType(this, actualGenerics);
      }

    return result;
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
    if (!state().atLeast(Feature.State.RESOLVING_TYPES))
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
        AstErrors.forwardTypeInference(rpos, this, pos());
        result = Types.t_ERROR;
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
   * otuerRef is the address of an outer value type or a reference to an outer
   * reference type.
   */
  public boolean isOuterRefCopyOfValue()
  {
    if (PRECONDITIONS) require
      (outer() != null);

    // if outher is a small and immutable value type, we can copy it:
    return this.outer().isBuiltInPrimitive();  // NYI: We might copy user defined small types as well
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
      (outer() != null);

    return !this.outer().isThisRef() && !isOuterRefCopyOfValue();
  }


  /**
   * Is this a routine that returns the current instance as its result?
   */
  public abstract boolean isConstructor();


  /**
   * Are calls to this feature performed using dynamic binding?
   */
  public boolean isDynamic()
  {
    if (PRECONDITIONS) require
      (this == Types.f_ERROR || outer() != null);

    return
      this != Types.f_ERROR &&
      generics() == FormalGenerics.NONE &&
      !outer().isChoice();
  }


  /**
   * Is this a constructor returning a reference result?
   */
  public abstract boolean isThisRef();


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
   * @param module the main SrcModule
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
  public FeatureName handDown(SrcModule module, AbstractFeature f, FeatureName fn, AbstractCall p, AbstractFeature heir)
  {
    if (PRECONDITIONS) require
      (module == null || module.declaredOrInheritedFeatures(this).get(fn) == f,
       this != heir);

    if (f.outer() == p.calledFeature()) // NYI: currently does not support inheriting open generic over several levels
      {
        // NYI: This might be incorrect in case p.generics() is inferred but not set yet.
        fn = f.effectiveName(p.generics());
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
   * @param a an array of types to be handed down
   *
   * @param heir a feature that inhertis from outer()
   *
   * @return the types from the argument array a has seen this within
   * heir. Their number might have changed due to open generics.
   */
  public AbstractType[] handDown(Resolution res, AbstractType[] a, AbstractFeature heir)  // NYI: This does not distinguish different inheritance chains yet
  {
    if (PRECONDITIONS) require
      (heir != null,
       state().atLeast(Feature.State.RESOLVING_TYPES));

    if (heir != Types.f_ERROR)
      {
        for (AbstractCall c : heir.findInheritanceChain(outer()))
          {
            for (int i = 0; i < a.length; i++)
              {
                var ti = a[i];
                if (ti.isOpenGeneric())
                  {
                    var frmlTs = ti.genericArgument().replaceOpen(c.generics());
                    a = Arrays.copyOf(a, a.length - 1 + frmlTs.size());
                    for (var tg : frmlTs)
                      {
                        if (CHECKS) check
                          (tg == Types.intern(tg));
                        a[i] = tg;
                        i++;
                      }
                    i = i - 1;
                  }
                else
                  {
                    FormalGenerics.resolve(res, c.generics(), heir);
                    ti = ti.actualType(c.calledFeature(), c.generics());
                    a[i] = Types.intern(ti);
                  }
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
   * @param heir a heir of this, might be equal to this.
   *
   * @return interned type that represents t seen as it is seen from heir.
   */
  public AbstractType handDownNonOpen(Resolution res, AbstractType t, AbstractFeature heir)
  {
    if (PRECONDITIONS) require
      (!t.isOpenGeneric(),
       heir != null,
       heir.state().atLeast(Feature.State.CHECKING_TYPES1));

    var a = handDown(res, new AbstractType[] { t }, heir);

    if (CHECKS) check
      (Errors.count() > 0 || a.length == 1);

    return a.length == 1 ? a[0] : Types.t_ERROR;
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
   * (wrt generic arguments) or renaminging/selection of the preferred
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
      (this == Types.f_ERROR || ancestor == Types.f_ERROR || Errors.count() > 0 || result != null);

    return result;
  }


  /**
   * Check if this is equal to or inherits from parent
   *
   * @param parent a loaded feature
   *
   * @return true iff this is a heir of parent.
   */
  public boolean inheritsFrom(AbstractFeature parent)
  {
    if (PRECONDITIONS) require
                         (state().atLeast(Feature.State.LOADED),
       parent != null && parent.state().atLeast(Feature.State.LOADED));

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
      // !isAbstract() &&      // NYI: check why abstract requires outer ref
      // !isIntrinsic() &&     // outer is require for backend code generator
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
    contract().visit(fv, this);
    if (isRoutine())
      {
        code().visit(fv, this);
      }
  }


  /**
   * Call v.action(s) on all statements s within this feature.
   *
   * @param v the action to be performed on the statements.
   */
  public void visitStatements(StatementVisitor v)
  {
    for (var c: inherits())
      {
        c.visitStatements(v);
      }
    contract().visitStatements(v);
    if (isRoutine())
      {
        code().visitStatements(v);
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
          (Errors.count() > 0 || frml.state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

        var frmlT = frml.resultType();
        if (CHECKS) check
          (frmlT == Types.intern(frmlT));

        result[argnum] = frmlT;
        argnum++;
      }

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  public abstract FeatureName featureName();
  public abstract FormalGenerics generics();
  public abstract List<AbstractCall> inherits();
  public abstract AbstractFeature outer();
  public abstract List<AbstractFeature> arguments();
  public abstract AbstractType resultType();
  public abstract AbstractFeature resultField();
  public abstract AbstractFeature outerRef();


  /**
   * Get inner feature with given name, ignoring the argument count.
   *
   * @param name the name of the feature within this.
   *
   * @return the found feature or null in case of an error.
   */
  public AbstractFeature get(SrcModule mod, String name)
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
  public AbstractFeature get(SrcModule mod, String name, int argcount)
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
        AstErrors.internallyReferencedFeatureNotFound(pos(), name, this, name);
      }
    else
      { // NYI: This might happen if the user adds additional features
        // with different argCounts. name should contain argCount to
        // avoid this
        AstErrors.internallyReferencedFeatureNotUnique(pos(), name + (argcount >= 0 ? " (" + Errors.argumentsString(argcount) : ""), set);
      }
    return result;
  }


  // following are used in IR/Clazzes middle end or later only:
  public abstract AbstractFeature choiceTag();

  // following are used in IR/Clazzes middle end or later only:
  public Impl.Kind implKind() { return Impl.Kind.Routine; /* NYI! */ }      // NYI: remove, used only in Clazz.java for some obscure case

  public Expr initialValue()   // NYI: remove, used only in Clazz.java for some obscure case
  {
    throw new Error("AbstractFeature.initialValue");
  }


  // following used in MIR or later
  public abstract Expr code();

  // in FUIR or later
  public abstract Contract contract();

}

/* end of file */
