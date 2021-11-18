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

import java.util.Collection;

import dev.flang.util.ANY;
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
                        if (t instanceof Type tt && !tt.checkedForGeneric)
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


  public abstract FeatureName featureName();
  public abstract SourcePosition pos();
  public abstract List<AbstractType> choiceGenerics();
  public abstract FormalGenerics generics();
  public abstract Generic getGeneric(String name);
  public abstract List<Call> inherits();
  public abstract AbstractFeature outer();
  public abstract AbstractType thisType();
  public abstract List<AbstractFeature> arguments();
  public abstract FeatureName handDown(Resolution res, AbstractFeature f, FeatureName fn, Call p, AbstractFeature heir);
  public abstract AbstractType[] handDown(Resolution res, AbstractType[] a, AbstractFeature heir);
  public abstract AbstractType resultType();
  public abstract boolean inheritsFrom(AbstractFeature parent);
  public abstract List<Call> tryFindInheritanceChain(AbstractFeature ancestor);
  public abstract List<Call> findInheritanceChain(AbstractFeature ancestor);
  public abstract AbstractFeature resultField();
  public abstract Collection<AbstractFeature> allInnerAndInheritedFeatures(Resolution res);
  public abstract AbstractFeature outerRef();
  public abstract AbstractFeature get(Resolution res, String qname);
  public abstract AbstractType[] argTypes();

  // following are used in IR/Clazzes middle end or later only:
  public abstract AbstractFeature outerRefOrNull();
  public abstract void visit(FeatureVisitor v);
  public abstract boolean isOpenGenericField();
  public abstract int depth();
  public abstract Feature choiceTag();

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
