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
 * Source of class ResolvedNormalType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * ResolvedNormalType represents normal type based on a constructor or choice
 * and a set of actual type parameters.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ResolvedNormalType extends ResolvedType
{

  /*----------------------------  variables  ----------------------------*/


  /**
   * The sourcecode position of the declaration point of this type, or, for
   * unresolved types, the source code position of its use.
   */
  public SourcePosition declarationPos() { return _feature.pos(); }


  /**
   * Is this an explicit reference or value type?  Ref/Value to make this a
   * reference/value type independent of the type of the underlying feature
   * defining a ref type or not, false to keep the underlying feature's
   * ref/value status.
   */
  UnresolvedType.RefOrVal _refOrVal;


  /**
   * the name of this type.  For a type 'map<string,i32>.entry', this is just
   * the base name 'entry'. For a type parameter 'A', this is 'A'. For an
   * artificial type, this is one of Types.INTERNAL_NAMES (e.g., '--ADDRESS--).
   */
  public String name()
  {
    return _feature.featureName().baseName();
  }


  /**
   * For a normal type, this is the list of actual type parameters given to the type.
   */
  List<AbstractType> _generics;
  public final List<AbstractType> generics() { return _generics; }


  /**
   * For a normal type, this is the list of the unresolved version of actual
   * type parameters given to the type, as far as they are available. They are
   * not available, e.g., when the type was inferred or was loaded from a module
   * file.  The list might be shorter than generics().
   */
  final List<AbstractType> _unresolvedGenerics;
  public final List<AbstractType> unresolvedGenerics() { return _unresolvedGenerics; }


  /**
   * The outer type, for the type p.q.r in the code
   */
  private AbstractType _outer;


  /**
   * The underlying feature this type was derived from.  _feature is a routine or a choice.
   */
  AbstractFeature _feature;


  /**
   * Cached result of calling Types.intern(this).
   */
  ResolvedNormalType _interned = null;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor to create a type from an existing type after formal generics
   * have been replaced in the generics arguments and in the outer type.
   *
   * @param t the original type
   *
   * @param g the actual generic arguments that replace t.generics (resolved)
   *
   * @param ug the actual generic arguments that replace t.generics (unresolved)
   *
   * @param o the actual outer type, or null, that replaces t.outer
   */
  public ResolvedNormalType(AbstractType t, List<AbstractType> g, List<AbstractType> ug, AbstractType o)
  {
    this(t, g, ug, o, false);

    if (PRECONDITIONS) require
      ( (t.generics() instanceof FormalGenerics.AsActuals   ) || t.generics().size() == g.size(),
       !(t.generics() instanceof FormalGenerics.AsActuals aa) || aa.sizeMatches(g),
        t == Types.t_ERROR || (t.outer() == null) == (o == null));
  }


  /**
   * Constructor to create a type from an existing type after formal generics
   * have been replaced in the generics arguments and in the outer type.
   *
   * @param t the original type
   *
   * @param g the actual generic arguments that replace t.generics (resolved)
   *
   * @param ug the actual generic arguments that replace t.generics (unresolved)
   *
   * @param o the actual outer type, or null, that replaces t.outer
   *
   * @param fixOuterThisType NYI: CLEANUP: #737, see below, unclear why this is needed.
   */
  public ResolvedNormalType(AbstractType t, List<AbstractType> g, List<AbstractType> ug, AbstractType o, boolean fixOuterThisType)
  {
    this(g,
         ug,
         o,
         t.featureOfType(),
         refOrVal(t),
         fixOuterThisType);

    if (PRECONDITIONS) require
      ( (t.generics() instanceof FormalGenerics.AsActuals   ) || t.generics().size() == g.size(),
       !(t.generics() instanceof FormalGenerics.AsActuals aa) || aa.sizeMatches(g),
        t == Types.t_ERROR || (t.outer() == null) == (o == null));
  }


  /**
   * Constructor
   *
   * @param g the actual generic arguments (resolved)
   *
   * @param ug the actual generic arguments (unresolved)
   *
   * @param o
   *
   * @param f if this type corresponds to a feature, then this is the
   * feature, else null.
   *
   * @param ref true iff this type should be a ref type, otherwise it will be a
   * value type.
   */
  public ResolvedNormalType(List<AbstractType> g, List<AbstractType> ug, AbstractType o, AbstractFeature f, UnresolvedType.RefOrVal refOrVal)
  {
    this(g, ug, o, f, refOrVal, true);
  }


  /**
   * Constructor
   *
   * @param g the actual generic arguments (resolved)
   *
   * @param ug the actual generic arguments (unresolved)
   *
   * @param o
   *
   * @param f if this type corresponds to a feature, then this is the
   * feature, otherwise null.
   *
   * @param ref true iff this type should be a ref type, otherwise it will be a
   * value type.
   */
  public ResolvedNormalType(List<AbstractType> g,
                            List<AbstractType> ug,
                            AbstractType o,
                            AbstractFeature f,
                            UnresolvedType.RefOrVal refOrVal,
                            boolean fixOuterThisType)
  {
    if (PRECONDITIONS) require
      (true // disabled for now since generics may be empty when resolving a type in a match case, actual generics will be inferred later.
       || Errors.any() || f == null || f.generics().sizeMatches(g == null ? UnresolvedType.NONE : g));

    this._generics = ((g == null) || g.isEmpty()) ? UnresolvedType.NONE : g;
    this._generics.freeze();
    this._unresolvedGenerics = ((ug == null) || ug.isEmpty()) ? UnresolvedType.NONE : ug;

    if (fixOuterThisType && o instanceof ResolvedNormalType ot && ot.isThisType())
      {
        // NYI: CLEANUP: #737: Undo the asThisType() calls done in This.java for
        // outer types. Is it possible to not create asThisType() in This.java
        // in the first place?
        o = new ResolvedNormalType(ot, UnresolvedType.RefOrVal.LikeUnderlyingFeature);
      }

    if (o == null && f != null)
      {
        var of = f.outer();
        if (of != null)
          {
            o = of.selfType();
          }
      }

    this._outer = o;
    this._feature = f;
    this._refOrVal = refOrVal;
  }


  /**
   * Create a ref or value type from a given value / ref type.
   *
   * @param original the original value type
   *
   * @param refOrVal must be UnresolvedType.RefOrVal.Boxed or UnresolvedType.RefOrVal.Val
   */
  public ResolvedNormalType(ResolvedNormalType original, UnresolvedType.RefOrVal refOrVal)
  {
    if (PRECONDITIONS) require
      (refOrVal != original._refOrVal);

    this._refOrVal          = refOrVal;
    this._generics          = original._generics;
    this._unresolvedGenerics = original._unresolvedGenerics;
    this._outer             = original._outer;
    this._feature           = original._feature;
  }


  /**
   * Create a clone of original that uses originalOuterFeature as context to
   * look up features the type is built from.
   *
   * @param original the original value type
   *
   * @param originalOuterFeature the original feature, which is not a type
   * feature.
   */
  ResolvedNormalType(ResolvedNormalType original, AbstractFeature originalOuterFeature)
  {
    this._refOrVal          = original._refOrVal;
    if (original._generics.isEmpty())
      {
        this._generics          = original._generics;
      }
    else
      {
        this._generics = new List<>();
        for (var g : original._generics)
          {
            var gc = (g instanceof ResolvedNormalType gt)
              ? gt.clone(originalOuterFeature)
              : g;
            this._generics.add(gc);
          }
        this._generics.freeze();
      }
    this._unresolvedGenerics = original._unresolvedGenerics;
    this._outer             = (original._outer instanceof ResolvedNormalType ot) ? ot.clone(originalOuterFeature) : original._outer;
    this._feature           = original._feature;
  }


  /**
   * Constructor for artificial built-in types.
   *
   * @param n the name
   */
  public ResolvedNormalType()
  {
    this(UnresolvedType.NONE, UnresolvedType.NONE, null, null, UnresolvedType.RefOrVal.LikeUnderlyingFeature);
  }


  /*-------------------------  static methods  --------------------------*/


  /**
   * Helper to extract `RefOrVal` from given type.
   *
   * @param t a type, must not be generic argument.
   */
  private static UnresolvedType.RefOrVal refOrVal(AbstractType t)
  {
    return
      t instanceof ResolvedNormalType tt         ? tt._refOrVal                   :
      t.isRef() == t.featureOfType().isThisRef() ? UnresolvedType.RefOrVal.LikeUnderlyingFeature :
      t.isRef()                                  ? UnresolvedType.RefOrVal.Boxed
                                                 : UnresolvedType.RefOrVal.Value;
  }


  /**
   * Constructor to create a type from an existing type after formal generics
   * have been replaced in the generics arguments and in the outer type.
   *
   * @param t the original type
   *
   * @param o the actual outer type, or null, that replaces t.outer
   */
  public static AbstractType newType(AbstractType t, AbstractType o)
  {
    if (PRECONDITIONS) require
      (t == Types.t_ERROR || t == Types.t_ADDRESS || (t.outer() == null) == (o == null));

    AbstractType result;
    if (t == Types.t_ERROR ||
        o == Types.t_ERROR   )
      {
        result = Types.t_ERROR;
      }
    else
      {
        result = new ResolvedNormalType(t.generics(), t.unresolvedGenerics(), o, t.featureOfType(),
                                        refOrVal(t),
                                        false);
      }
    return result;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * This method usually just returns currentOuter. Only for clone()d types that
   * are used in a different outer context, this permits to look up features the
   * type is based on in the original context.
   */
  AbstractFeature originalOuterFeature(AbstractFeature currentOuter)
  {
    return currentOuter;
  }


  /**
   * Create a Types.intern()ed reference variant of this type.  Return this
   * in case it is a reference already.
   */
  public AbstractType asRef()
  {
    if (PRECONDITIONS) require
      (this == Types.intern(this));

    AbstractType result = this;
    if (!isRef() && this != Types.t_ERROR)
      {
        result = Types.intern(new ResolvedNormalType(this, UnresolvedType.RefOrVal.Boxed));
      }
    return result;
  }


  /**
   * Create a Types.intern()ed this.type variant of this type.  Return this
   * in case it is a this.type or a choice variant already.
   */
  public AbstractType asThis()
  {
    if (PRECONDITIONS) require
      (this == Types.intern(this));

    AbstractType result = this;
    if (!isThisType() && !isChoice() && this != Types.t_ERROR && this != Types.t_ADDRESS)
      {
        result = Types.intern(new ResolvedNormalType(this, UnresolvedType.RefOrVal.ThisType));
      }

    if (POSTCONDITIONS) ensure
      (result == Types.t_ERROR || result == Types.t_ADDRESS || result.isThisType() || result.isChoice(),
       !(isThisType() || isChoice()) || result == this);

    return result;
  }


  /**
   * Create a Types.intern()ed value variant of this type.  Return this
   * in case it is a value already.
   */
  public AbstractType asValue()
  {
    if (PRECONDITIONS) require
      (this == Types.intern(this));

    AbstractType result = this;
    if (isRef() && this != Types.t_ERROR)
      {
        result = Types.intern(new ResolvedNormalType(this, UnresolvedType.RefOrVal.Value));
      }
    return result;
  }


  /**
   * isRef
   */
  public boolean isRef()
  {
    return switch (this._refOrVal)
      {
      case Boxed                -> true;
      case Value                -> false;
      case LikeUnderlyingFeature-> _feature.isThisRef();
      case ThisType             -> false;
      };
  }


  /**
   * isThisType
   */
  public boolean isThisType()
  {
    return this._refOrVal == UnresolvedType.RefOrVal.ThisType;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    String result;

    if (this == Types.t_ERROR)
      {
        result = Errors.ERROR_STRING;
      }
    else if (_outer != null)
      {
        String outer = _outer.toStringWrapped();
        result = ""
          + (outer == "" ||
             outer.equals(FuzionConstants.UNIVERSE_NAME) ? ""
                                                         : outer + ".")
          + (_refOrVal == UnresolvedType.RefOrVal.Boxed && (_feature == null || !_feature.isThisRef()) ? "ref " :
             _refOrVal == UnresolvedType.RefOrVal.Value &&  _feature != null &&  _feature.isThisRef()  ? "value "
                                                                                      : ""       )
          + (_feature == null ? Errors.ERROR_STRING
                              : _feature.featureName().baseName());
      }
    else
      {
        result =
          (_refOrVal == UnresolvedType.RefOrVal.Boxed && (_feature == null || !_feature.isThisRef()) ? "ref " :
           _refOrVal == UnresolvedType.RefOrVal.Value &&  _feature != null &&  _feature.isThisRef()  ? "value "
                                                                                    : ""       )
          + _feature.qualifiedName();
      }
    if (isThisType())
      {
        result = result + ".this.type";
      }
    if (_generics != UnresolvedType.NONE)
      {
        result = result + _generics
          .toString(" ", " ", "", (g) -> g.toStringWrapped());
      }
    return result;
  }


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outerfeat the feature surrounding this expression.
   */
  public AbstractType visit(FeatureVisitor v, AbstractFeature outerfeat)
  {
    return v.action(this, outerfeat);
  }


  /**
   * resolve this type
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param feat the outer feature this type is declared in, used
   * for resolution of generic parameters etc.
   */
  AbstractType resolve(Resolution res, AbstractFeature outerfeat)
  {
    if (PRECONDITIONS) require
      (outerfeat != null,
       outerfeat != null && outerfeat.state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

    // NYI: cleanup: Basically, resolution should no longer be needed here, but
    // be done on UnresolvedType. Need to check how we can move this to
    // Unresolvedtype.
    var result = resolveGenerics(declarationPos(), res, outerfeat);
    result = Types.intern(result);
    return result;
  }


  /**
   * For a normal type, resolve the actual type parameters.
   *
   * @param pos source code position of the unresolved types whose generics we
   * are resolving.
   *
   * @param res the resolution instance
   *
   * @param outerfeat the outer feature this type is declared in.
   */
  AbstractType resolveGenerics(HasSourcePosition pos, Resolution res, AbstractFeature outerfeat)
  {
    AbstractType result = this;
    if (isThisType() && _generics.isEmpty())
      {
        this._generics = _feature.generics().asActuals();
        this._generics.freeze();
      }
    this._generics = FormalGenerics.resolve(res, _generics, outerfeat);
    this._generics.freeze();
    if (CHECKS) check
      (Errors.any() || _feature != null);
    if (_feature != null &&
        !_feature.generics().errorIfSizeOrTypeDoesNotMatch(_generics,
                                                           pos.pos(),
                                                           "type",
                                                           "Type: " + toString() + "\n"))
      {
        result = Types.t_ERROR;
      }
    return result;
  }


  /**
   * For a resolved normal type, return the underlying feature.
   *
   * @return the underlying feature.
   *
   * @throws Error if this is not resolved or isGenericArgument().
   */
  public AbstractFeature featureOfType()
  {
    if (PRECONDITIONS) require
      (Errors.any() || _feature != null);

    return _feature != null
      ? _feature
      : Types.f_ERROR;
  }


  /**
   * Is this the type of a type feature, e.g., the type of `(list
   * i32).type`. Will return false for an instance of Type for which this is
   * still unknown since Type.resolve() was not called yet.
   *
   * This is redefined here since `feature` might still be null while this type
   * was not resolved yet.
   */
  boolean isTypeType()
  {
    return _feature != null && _feature.isTypeFeature();
  }


  /**
   * outer type, after type resolution. This provides the whole chain of types
   * until Types.resolved.universe.selfType(), while the _outer field ends with
   * the outermost type explicitly written in the source code.
   */
  public AbstractType outer()
  {
    return _outer;
  }


  /**
   * For a type that is not a type parameter, create a new variant using given
   * actual generics and outer type.
   *
   * @param g2 the new actual generics to be used
   *
   * @param o2 the new outer type to be used (which may also differ in its
   * actual generics).
   *
   * @return a new type with same featureOfType(), but using g2/o2 as generics
   * and outer type.
   */
  public AbstractType applyTypePars(List<AbstractType> g2, AbstractType o2)
  {
    return new ResolvedNormalType(this, g2, unresolvedGenerics(), o2);
  }


  /**
   * Create a clone of this Type that uses originalOuterFeature as context to
   * look up features the type is built from.  Generics will be looked up in the
   * current context.
   *
   * This is used for type features that use types from the original feature,
   * but needs to replace generics by the type feature's generics.
   *
   * @param originalOuterFeature the original feature, which is not a type
   * feature.
   */
  ResolvedNormalType clone(AbstractFeature originalOuterFeature)
  {
    return
      new ResolvedNormalType(this, originalOuterFeature)
      {
        AbstractFeature originalOuterFeature(AbstractFeature currentOuter)
        {
          return originalOuterFeature;
        }
      };
  }


  /**
   * traverse a resolved type collecting all features this type uses.
   *
   * @param s the features that have already been found
   */
  protected void usedFeatures(Set<AbstractFeature> s)
  {
    // NYI: "This currently does not touch the outer features.
    //       This means that for a type like (x T).y U the visibility of x and T will be ignored, which is probably wrong."
    var f = featureOfType();
    if (s.add(f))
      {
        for (var g : generics())
          {
            g.usedFeatures(s);
          }
        if (isChoice())
          {
            for (var g : choiceGenerics())
              {
                g.usedFeatures(s);
              }
          }
      }
  }

}

/* end of file */
