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

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
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
  public SourcePosition declarationPos() { return _feature == null ? SourcePosition.notAvailable : _feature.pos(); }


  /**
   * Is this an explicit reference or value type?  Ref/Value to make this a
   * reference/value type independent of the type of the underlying feature
   * defining a ref type or not, false to keep the underlying feature's
   * ref/value status.
   */
  RefOrVal _refOrVal;


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
   * Cached result of isRef(). Even though this function looks harmless, it is
   * surprisingly performance critical.
   */
  Boolean _isRef;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Instantiate a new ResolvedNormalType and return its unique instance.
   *
   * @param t the original type
   *
   * @param g the actual generic arguments that replace t.generics (resolved)
   *
   * @param ug the actual generic arguments that replace t.generics (unresolved)
   *
   * @param o the actual outer type, or null, that replaces t.outer
   */
  public static ResolvedType create(AbstractType t, List<AbstractType> g, List<AbstractType> ug, AbstractType o)
  {
    if (PRECONDITIONS) require
      ( (t.generics() instanceof FormalGenerics.AsActuals   ) || t.generics().size() == g.size(),
       !(t.generics() instanceof FormalGenerics.AsActuals aa) || aa.sizeMatches(g),
        t == Types.t_ERROR || (t.outer() == null) == (o == null));

    return create(t, g, ug, o, false);
  }


  /**
   * Instantiate a new ResolvedNormalType and return its unique instance.
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
  public static ResolvedType create(AbstractType t, List<AbstractType> g, List<AbstractType> ug, AbstractType o, boolean fixOuterThisType)
  {
    if (PRECONDITIONS) require
      ( (t.generics() instanceof FormalGenerics.AsActuals   ) || t.generics().size() == g.size(),
       !(t.generics() instanceof FormalGenerics.AsActuals aa) || aa.sizeMatches(g),
        t == Types.t_ERROR || (t.outer() == null) == (o == null));

    return create(g, ug, o, t.feature(), refOrVal(t), fixOuterThisType);
  }


  /**
   * Instantiate a new ResolvedNormalType and return its unique instance.
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
   * @param refOrVal
   */
  public static ResolvedType create(List<AbstractType> g, List<AbstractType> ug, AbstractType o, AbstractFeature f, RefOrVal refOrVal)
  {
    return create(g, ug, o, f, refOrVal, true);
  }


  /**
   * Instantiate a new ResolvedNormalType and return its unique instance.
   *
   * @param g the actual generic arguments (resolved)
   *
   * @param ug the actual generic arguments (unresolved)
   *
   * @param o
   *
   * @param f if this type corresponds to a feature, then this is the
   * feature, else null.
   */
  public static ResolvedType create(List<AbstractType> g, List<AbstractType> ug, AbstractType o, AbstractFeature f)
  {
    return create(g, ug, o, f, RefOrVal.LikeUnderlyingFeature);
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
   * @param refOrVal true iff this type should be a ref type, otherwise it will be a
   * value type.
   */
  private ResolvedNormalType(List<AbstractType> g,
                            List<AbstractType> ug,
                            AbstractType o,
                            AbstractFeature f,
                            RefOrVal refOrVal,
                            boolean fixOuterThisType)
  {
    if (PRECONDITIONS) require
      (true // disabled for now since generics may be empty when resolving a type in a match case, actual generics will be inferred later.
       || Errors.any() || f == null || f.generics().sizeMatches(g == null ? UnresolvedType.NONE : g),
       Types.resolved == null
         || f.compareTo(Types.resolved.f_void) != 0
         || refOrVal == RefOrVal.LikeUnderlyingFeature);

    this._generics = ((g == null) || g.isEmpty()) ? UnresolvedType.NONE : g;
    this._generics.freeze();
    this._unresolvedGenerics = ((ug == null) || ug.isEmpty()) ? UnresolvedType.NONE : ug;

    if (fixOuterThisType && o instanceof ResolvedNormalType ot && ot.isThisType())
      {
        // NYI: CLEANUP: #737: Undo the asThisType() calls done in This.java for
        // outer types. Is it possible to not create asThisType() in This.java
        // in the first place?
        o = ResolvedNormalType.create(ot, RefOrVal.LikeUnderlyingFeature);
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
   * Instantiate a new ResolvedNormalType and return its unique instance.
   */
  public static ResolvedType create(List<AbstractType> g,
                                    List<AbstractType> ug,
                                    AbstractType o,
                                    AbstractFeature f,
                                    RefOrVal refOrVal,
                                    boolean fixOuterThisType)
  {
    if (f == Types.f_ERROR ||
        g.stream().anyMatch(x -> x == Types.t_ERROR))
      {
        return Types.t_ERROR;
      }
    else
      {
        return new ResolvedNormalType(g, ug, o, f, refOrVal, fixOuterThisType);
      }
  }


  /**
   * Create a ref or value type from a given value / ref type.
   *
   * @param original the original value type
   *
   * @param refOrVal must be UnresolvedType.RefOrVal.Boxed or UnresolvedType.RefOrVal.Val
   */
  private ResolvedNormalType(ResolvedNormalType original, RefOrVal refOrVal)
  {
    if (PRECONDITIONS) require
      (refOrVal != original._refOrVal,
       Types.resolved == null
         || !original.isVoid()
         || refOrVal == RefOrVal.LikeUnderlyingFeature
      );

    this._refOrVal          = refOrVal;
    this._generics          = original._generics;
    this._unresolvedGenerics = original._unresolvedGenerics;
    this._outer             = original._outer;
    this._feature           = original._feature;

    if (POSTCONDITIONS) ensure
      (feature().generics().sizeMatches(generics()));
  }


  /**
   * Instantiate a new ResolvedNormalType and return its unique instance.
   */
  public static ResolvedNormalType create(ResolvedNormalType original, RefOrVal refOrVal)
  {
    return new ResolvedNormalType(original, refOrVal);
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
  private ResolvedNormalType(ResolvedNormalType original, AbstractFeature originalOuterFeature)
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
   * Instantiate a new ResolvedNormalType and return its unique instance.
   */
  public static ResolvedNormalType create(ResolvedNormalType original, AbstractFeature originalOuterFeature)
  {
    return new ResolvedNormalType(original, originalOuterFeature);
  }

  /**
   * Constructor for artificial built-in types.
   */
  protected ResolvedNormalType()
  {
    this(UnresolvedType.NONE, UnresolvedType.NONE, null, null, RefOrVal.LikeUnderlyingFeature, true);
  }


  /*-------------------------  static methods  --------------------------*/


  /**
   * Helper to extract `RefOrVal` from given type.
   *
   * @param t a type, must not be generic argument.
   */
  private static RefOrVal refOrVal(AbstractType t)
  {
    return
      t instanceof ResolvedNormalType tt         ? tt._refOrVal                   :
      t.isRef() == t.feature().isRef() ? RefOrVal.LikeUnderlyingFeature :
      t.isRef()                                  ? RefOrVal.Boxed
                                                 : RefOrVal.Value;
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
        result = ResolvedNormalType.create(t.generics(), t.unresolvedGenerics(), o, t.feature(),
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
    AbstractType result = this;
    if (!isRef() && !isVoid() && this != Types.t_ERROR)
      {
        result = ResolvedNormalType.create(this, RefOrVal.Boxed);
      }
    return result;
  }


  /**
   * Create a Types.intern()ed this.type variant of this type.  Return this
   * in case it is a this.type or a choice variant already.
   */
  public AbstractType asThis()
  {
    AbstractType result = this;
    if (!isThisType() && !isChoice() && this != Types.t_ERROR && this != Types.t_ADDRESS)
      {
        result = ResolvedNormalType.create(this, RefOrVal.ThisType);
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
    AbstractType result = this;
    if (isRef() && this != Types.t_ERROR)
      {
        result = ResolvedNormalType.create(this, RefOrVal.Value);
      }
    return result;
  }


  /**
   * isRef
   */
  public boolean isRef()
  {
    var r = _isRef;
    if (r == null)
      {
        r = switch (this._refOrVal)
          {
          case Boxed                -> true;
          case Value                -> false;
          case LikeUnderlyingFeature-> feature().isRef();
          case ThisType             -> false;
          };
        this._isRef = r;
      }
    return r;
  }


  /**
   * isThisType
   */
  public boolean isThisType()
  {
    return this._refOrVal == RefOrVal.ThisType;
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
          + (_refOrVal == RefOrVal.Boxed && (_feature == null || !_feature.isRef()) ? "ref " :
             _refOrVal == RefOrVal.Value &&  _feature != null &&  _feature.isRef()  ? "value "
                                                                                      : ""       )
          + (_feature == null ? Errors.ERROR_STRING
                              : _feature.featureName().baseNameHuman());
      }
    else
      {
        result =
          _feature == null ? "<null-feature>" :
          ((_refOrVal == RefOrVal.Boxed && (_feature == null || !_feature.isRef()) ? "ref " :
            _refOrVal == RefOrVal.Value &&  _feature != null &&  _feature.isRef()  ? "value "
                                                                                    : ""       )
           + _feature.qualifiedName());
      }
    if (isThisType())
      {
        result = result + ".this";
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
   * For a resolved normal type, return the underlying feature.
   *
   * @return the underlying feature.
   *
   * @throws Error if this is not resolved or isGenericArgument().
   */
  public AbstractFeature feature()
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
    return _feature != null && _feature.isCotype();
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
   * @return a new type with same feature(), but using g2/o2 as generics
   * and outer type.
   */
  public AbstractType applyTypePars(List<AbstractType> g2, AbstractType o2)
  {
    return ResolvedNormalType.create(this, g2, unresolvedGenerics(), o2);
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
  AbstractType clone(AbstractFeature originalOuterFeature)
  {
    return this == Types.t_UNDEFINED ? this :
      new ResolvedNormalType(this, originalOuterFeature)
      {
        AbstractFeature originalOuterFeature(AbstractFeature currentOuter)
        {
          return originalOuterFeature;
        }
        ResolvedType _resolved = null;

        /**
         * This is a bit ugly, even though this type is a ResolvedType, the generics are not.
         */
        @Override
        AbstractType resolve(Resolution res, Context context)
        {
          if (_resolved == null)
            {
              _resolved = UnresolvedType.finishResolve(res, context, this, declarationPos(), feature(), _generics, unresolvedGenerics(), outer(), _refOrVal, false, false);
            }
          return _resolved;
        }
      };
  }


  /**
   * resolve this type. This is only needed for ast.Type, for fe.LibraryType
   * this is a NOP.
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this type is used
   */
  @Override
  AbstractType resolve(Resolution res, Context context)
  {
    // tricky: outers generics may not have been resolved yet.
    if (_outer != null)
      {
        _outer = _outer.resolve(res, context);
      }
    return this;
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
    var f = feature();
    if (s.add(f))
      {
        for (var g : generics())
          {
            g.usedFeatures(s);
          }
        if (isChoice())
          {
            for (var g : choiceGenerics(Context.NONE))
              {
                g.usedFeatures(s);
              }
          }
      }
  }

}

/* end of file */
