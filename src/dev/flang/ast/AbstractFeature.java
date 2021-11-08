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
   * The basic types of features in Fuzion:
   */
  public enum Kind
  {
    Routine,
    Field,
    Intrinsic,
    Abstract,
    Choice
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

  public abstract boolean isUniverse();
  public abstract boolean isOuterRef();
  public abstract boolean isThisRef();
  abstract boolean isChoiceTag();
  abstract boolean isDynamic();
  abstract boolean isAnonymousInnerFeature();
  abstract boolean isIndexVarUpdatedByLoop();
  public abstract boolean isBuiltInPrimitive();
  public abstract boolean hasResult();
  public abstract FeatureName featureName();
  abstract FeatureName effectiveName(List<Type> actualGenerics);
  public abstract String qualifiedName();
  public abstract SourcePosition pos();
  public abstract ReturnType returnType();
  public abstract List<Type> choiceGenerics();
  public abstract FormalGenerics generics();
  public abstract Generic getGeneric(String name);
  public abstract List<Call> inherits();
  abstract boolean isLastArgType(Type t);
  public abstract AbstractFeature outer();
  public abstract Feature.State state();
  public abstract Type thisType();
  public abstract boolean hasOpenGenericsArgList();
  public abstract List<AbstractFeature> arguments();
  public abstract FeatureName handDown(Resolution res, AbstractFeature f, FeatureName fn, Call p, AbstractFeature heir);
  abstract Type[] handDown(Resolution res, Type[] a, AbstractFeature heir);
  public abstract AbstractFeature select(Resolution res, int i);
  abstract Type resultTypeIfPresent(Resolution res, List<Type> generics);
  abstract void whenResolvedTypes(Runnable r);
  abstract void resolveTypes(Resolution res);
  public abstract Type resultType();
  abstract Type resultTypeForTypeInference(SourcePosition rpos, Resolution res, List<Type> generics);
  boolean detectedCyclicInheritance() { return false; }
  abstract void checkNoClosureAccesses(Resolution res, SourcePosition errorPos);
  abstract void resolveInheritance(Resolution res);
  public abstract boolean inheritsFrom(AbstractFeature parent);
  abstract List<Call> tryFindInheritanceChain(AbstractFeature ancestor);
  public abstract List<Call> findInheritanceChain(AbstractFeature ancestor);
  public abstract AbstractFeature resultField();
  abstract void foundAssignmentToResult();
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

}

/* end of file */
