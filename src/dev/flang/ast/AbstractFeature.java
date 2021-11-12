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
 * a module file.
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
  public FeatureName effectiveName(List<Type> actualGenerics)
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
  public Feature.State state()
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
   * Check if this is a built in primitive.  For these, the type of an outer
   * reference for inner features is not a reference, but a copy of the value
   * itself since there are no inner features to modify the value.
   */
  public boolean isBuiltInPrimitive()
  {
    return !isUniverse()
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
   * Is this a routine that returns the current instance as its result?
   */
  public abstract boolean isConstructor();

  public abstract boolean isThisRef();
  public abstract boolean isDynamic();
  public abstract boolean isAnonymousInnerFeature();
  public abstract boolean hasResult();
  public abstract FeatureName featureName();
  public abstract SourcePosition pos();
  public abstract ReturnType returnType();
  public abstract List<Type> choiceGenerics();
  public abstract FormalGenerics generics();
  public abstract Generic getGeneric(String name);
  public abstract List<Call> inherits();
  public abstract boolean isLastArgType(Type t);
  public abstract AbstractFeature outer();
  public abstract Type thisType();
  public abstract boolean hasOpenGenericsArgList();
  public abstract List<AbstractFeature> arguments();
  public abstract FeatureName handDown(Resolution res, AbstractFeature f, FeatureName fn, Call p, AbstractFeature heir);
  public abstract Type[] handDown(Resolution res, Type[] a, AbstractFeature heir);
  public abstract AbstractFeature select(Resolution res, int i);
  protected abstract Type resultTypeIfPresent(Resolution res, List<Type> generics);
  public abstract Type resultType();
  public abstract void checkNoClosureAccesses(Resolution res, SourcePosition errorPos);
  public abstract boolean inheritsFrom(AbstractFeature parent);
  public abstract List<Call> tryFindInheritanceChain(AbstractFeature ancestor);
  public abstract List<Call> findInheritanceChain(AbstractFeature ancestor);
  public abstract AbstractFeature resultField();
  public abstract Collection<AbstractFeature> allInnerAndInheritedFeatures(Resolution res);
  public abstract AbstractFeature outerRef();
  public abstract boolean isOuterRefAdrOfValue();
  public abstract AbstractFeature get(Resolution res, String qname);
  public abstract Type[] argTypes();

  // following are used in IR/Clazzes middle end or later only:
  public abstract boolean isOuterRefCopyOfValue();
  public abstract AbstractFeature outerRefOrNull();
  public abstract void visit(FeatureVisitor v);
  public abstract boolean isOpenGenericField();
  public abstract int depth();
  public abstract int selectSize();
  public abstract Feature select(int i);
  public abstract Feature choiceTag();

  public abstract Impl.Kind implKind();  // NYI: remove, used only in Clazz.java for some obscure case
  public abstract Expr initialValue();   // NYI: remove, used only in Clazz.java for some obscure case

  // following used in MIR or later
  public abstract Expr code();

  // in FE or later
  public abstract boolean isArtificialField();

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
