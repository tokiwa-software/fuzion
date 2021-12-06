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
import java.util.TreeSet;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * AbstractFeature is represents a Fuzion feature in the front end.  This
 * feature might either be part of the abstract syntax tree or part of a binary
 * module file.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractFeature extends ANY implements Comparable<AbstractFeature>
{

  /**
   * NYI: to be removed: Temporary mapping from Feature to corresponding
   * libraryFeature (if it exists) and back to the ast.Feature.
   *
   * As long as the duality of ast.Feature/fe.LibraryFeature exists, a check for
   * feature equality should be done using sameAs.
   */
  public AbstractFeature _libraryFeature = null;
  public AbstractFeature libraryFeature()
  {
    return _libraryFeature == null ? this : _libraryFeature;
  }
  public AbstractFeature astFeature() { return this; }
  public boolean sameAs(AbstractFeature other)
  {
    return astFeature() == other.astFeature();
  }


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
      check
        (values()[ordinal].ordinal() == ordinal);

      return values()[ordinal];
    }
  }

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
    return isUniverse() || outer().isUniverse() ? n
                                                : outer().qualifiedName() + "." + n;
  }


  /**
   * Obtain the effective name of this feature when actualGenerics are the
   * actual generics of its outer() feature.
   */
  public FeatureName effectiveName(List<AbstractType> actualGenerics)
  {
    if (PRECONDITIONS) require
      (outer().generics().sizeMatches(actualGenerics));

    var result = featureName();
    if (hasOpenGenericsArgList())
      {
        var argCount = arguments().size() + actualGenerics.size() - outer().generics().list.size();
        check
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
   * Check if this is an outer ref field.
   */
  public boolean isOuterRef()
  {
    return featureName().baseName().startsWith(FuzionConstants.OUTER_REF_PREFIX);
  }


  /**
   * Is this a tag field created for a choice-type?
   */
  boolean isChoiceTag()
  {
    return featureName().baseName().startsWith(FuzionConstants.CHOICE_TAG_NAME);
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
    else if (this == Types.resolved.f_choice)
      {
        result = generics().asActuals();
      }
    else
      {
        result = null;
        Call lastP = null;
        for (Call p: inherits())
          {
            check
              (Errors.count() > 0 || p.calledFeature() != null);

            if (p.calledFeature().sameAs(Types.resolved.f_choice))
              {
                if (lastP != null)
                  {
                    AstErrors.repeatedInheritanceOfChoice(p.pos, lastP.pos);
                  }
                lastP = p;
                result = p.generics;
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
      ((result == null) || (result._name.equals(name) && (result.feature().sameAs(this))));
    // result == null ==> for all g in generics: !g.name.equals(name)

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
    return resultType();
  }
  AbstractType resultTypeForTypeInference(SourcePosition rpos, Resolution res, List<AbstractType> generics)
  {
    return resultTypeIfPresent(res, generics);
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
  public FeatureName handDown(SrcModule module, AbstractFeature f, FeatureName fn, Call p, AbstractFeature heir)
  {
    if (PRECONDITIONS) require
      (module.declaredOrInheritedFeatures(this).get(fn).sameAs(f),
       this != heir);

    if (f.outer().sameAs(p.calledFeature())) // NYI: currently does not support inheriting open generic over several levels
      {
        fn = f.effectiveName(p.generics);
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
       state().atLeast(Feature.State.RESOLVED_TYPES));

    if (heir != Types.f_ERROR)
      {
        for (Call c : heir.findInheritanceChain(outer()))
          {
            for (int i = 0; i < a.length; i++)
              {
                var ti = a[i];
                if (ti.isOpenGeneric())
                  {
                    var frmlTs = ti.genericArgument().replaceOpen(c.generics);
                    a = Arrays.copyOf(a, a.length - 1 + frmlTs.size());
                    for (var tg : frmlTs)
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
                    FormalGenerics.resolve(res, c.generics, heir);
                    ti = ti.actualType(c.calledFeature(), c.generics);
                    a[i] = Types.intern(ti);
                  }
              }
          }
      }
    return a;
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
  public List<Call> tryFindInheritanceChain(AbstractFeature ancestor)
  {
    List<Call> result;
    if (this.sameAs(ancestor))
      {
        result = new List<Call>();
      }
    else
      {
        result = null;
        for (Call c : inherits())
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
  public List<Call> findInheritanceChain(AbstractFeature ancestor)
  {
    if (PRECONDITIONS) require
      (ancestor != null);

    List<Call> result = tryFindInheritanceChain(ancestor);

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

    if (this.sameAs(parent))
      {
        return true;
      }
    else
      {
        for (Call p : inherits())
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
    return !isField() && !isChoice() && !isUniverse() && (this != Types.f_ERROR);
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
    for (Call c: inherits())
      {
        var nc = c.visit(fv, (Feature) astFeature());
        check
          (c == nc); // NYI: This will fail when doing funny stuff like inherit from bool.infix &&, need to check and handle explicitly
      }
    if (contract() != null)
      {
        contract().visit(fv, (Feature) astFeature());
      }
    if (isRoutine())
      {
        code().visit(fv, (Feature) astFeature());
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
        check
          (Errors.count() > 0 || frml.state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

        var frmlT = frml.resultType();
        check(frmlT == Types.intern(frmlT));
        result[argnum] = frmlT;
        argnum++;
      }

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * allInnerAndInheritedFeatures returns a complete set of inner features, used
   * by Clazz.layout and Clazz.hasState.
   *
   * @return
   */
  public Collection<AbstractFeature> allInnerAndInheritedFeatures(SrcModule mod)
  {
    if (PRECONDITIONS) require
                         (state().atLeast(Feature.State.RESOLVED));

    TreeSet<AbstractFeature> result = new TreeSet<>();

    result.addAll(mod.declaredFeatures(this).values());
    for (Call p : inherits())
      {
        var cf = p.calledFeature();
        check
          (Errors.count() > 0 || cf != null);

        if (cf != null)
          {
            result.addAll(cf.allInnerAndInheritedFeatures(mod));
          }
      }

    return result;
  }


  public abstract FeatureName featureName();
  public abstract SourcePosition pos();
  public abstract FormalGenerics generics();
  public abstract List<Call> inherits();
  public abstract AbstractFeature outer();
  public abstract AbstractType thisType();
  public abstract List<AbstractFeature> arguments();
  public abstract AbstractType resultType();
  public abstract AbstractFeature resultField();
  public abstract AbstractFeature outerRef();
  public abstract AbstractFeature get(String name);

  // following are used in IR/Clazzes middle end or later only:
  public abstract AbstractFeature choiceTag();

  public abstract Impl.Kind implKind();  // NYI: remove, used only in Clazz.java for some obscure case
  public abstract Expr initialValue();   // NYI: remove, used only in Clazz.java for some obscure case

  // following used in MIR or later
  public abstract Expr code();

  // in FUIR or later
  public abstract Contract contract();


  /**
   * Compare this to other for sorting Feature
   */
  public int compareTo(AbstractFeature other)
  {
    int result;
    if (sameAs(other))
      {
        result = 0;
      }
    else if ((this.outer() == null) &&  (other.outer() != null))
      {
        result = -1;
      }
    else if ((this.outer() != null) &&  (other.outer() == null))
      {
        result = +1;
      }
    else
      {
        result = (this.outer() != null) ? this.outer().compareTo(other.outer())
                                       : 0;
        if (result == 0)
          {
            result = featureName().compareTo(other.featureName());
          }
      }
    return result;
  }


}

/* end of file */
