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
import dev.flang.util.List;


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
   * Is this an explicit reference or value type?  Ref/Value to make this a
   * reference/value type independent of the type of the underlying feature
   * defining a ref type or not, false to keep the underlying feature's
   * ref/value status.
   */
  private final TypeKind _typeKind;


  /**
   * For a normal type, this is the list of actual type parameters given to the type.
   */
  final List<AbstractType> _generics;
  public final List<AbstractType> generics() { return _generics; }


  /**
   * For a normal type, this is the list of the unresolved version of actual
   * type parameters given to the type, as far as they are available. They are
   * not available, e.g., when the type was inferred or was loaded from a module
   * file.  The list might be shorter than generics().
   */
  private final List<AbstractType> _unresolvedGenerics;
  public final List<AbstractType> unresolvedGenerics() { return _unresolvedGenerics; }


  /**
   * The outer type, for the type p.q.r in the code
   */
  private AbstractType _outer;


  /**
   * The underlying feature this type was derived from.  _feature is a routine or a choice.
   */
  AbstractFeature _feature;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Instantiate a new ResolvedNormalType.
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

    return create(g, ug, o, t.feature(), t.kind());
  }


  /**
   * Instantiate a new ResolvedNormalType.
   *
   * @param g the actual generic arguments (resolved)
   *
   * @param o
   *
   * @param f if this type corresponds to a feature, then this is the
   * feature, else null.
   */
  public static ResolvedType create(List<AbstractType> g, AbstractType o, AbstractFeature f)
  {
    return create(g, Call.NO_GENERICS, o, f, f.defaultTypeKind());
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
   * @param typeKind true iff this type should be a ref type, otherwise it will be a
   * value type.
   */
  private ResolvedNormalType(List<AbstractType> g,
                            List<AbstractType> ug,
                            AbstractType o,
                            AbstractFeature f,
                            TypeKind typeKind)
  {
    if (PRECONDITIONS) require
      (Errors.any() || f == null || f.generics().sizeMatches(g == null ? UnresolvedType.NONE : g),
       typeKind == TypeKind.ValueType || typeKind == TypeKind.RefType
       /* NYI: Types.resolved == null
         || f.compareTo(Types.resolved.f_void) != 0*/);

    this._generics = g == null || g.isEmpty() ? UnresolvedType.NONE : g;
    this._generics.freeze();
    this._unresolvedGenerics = ((ug == null) || ug.isEmpty()) ? UnresolvedType.NONE : ug;

    // NYI: CLEANUP: surprising hack
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
    this._typeKind = typeKind;

    if (POSTCONDITIONS) ensure
      (_feature == null /* artificial built in type */
       || feature().generics().sizeMatches(generics())
       || generics().isEmpty() /* e.g. an incomplete type in a match case */);
  }

  /**
   * Instantiate a new ResolvedNormalType.
   */
  public static ResolvedType create(List<AbstractType> g,
                                    List<AbstractType> ug,
                                    AbstractType o,
                                    AbstractFeature f,
                                    TypeKind typeKind)
  {
    return f == Types.f_ERROR || g.contains(Types.t_ERROR)
      ? Types.t_ERROR
      : new ResolvedNormalType(g, ug, o, f, typeKind);
  }


  /**
   * Create a ref or value type from a given value / ref type.
   *
   * @param original the original value type
   *
   * @param typeKind must be TypeKind.RefType or TypeKind.ValueType
   */
  private ResolvedNormalType(ResolvedNormalType original, TypeKind typeKind)
  {
    if (PRECONDITIONS) require
      (kind() != original.kind(),
       Types.resolved == null
         || !original.isVoid(),
       typeKind == TypeKind.ValueType || typeKind == TypeKind.RefType
      );

    this._typeKind           = typeKind;
    this._generics           = original._generics;
    this._unresolvedGenerics = original._unresolvedGenerics;
    this._outer              = original._outer;
    this._feature            = original._feature;

    if (POSTCONDITIONS) ensure
      (feature().generics().sizeMatches(generics()));
  }


  /**
   * Instantiate a new ResolvedNormalType.
   */
  public static ResolvedNormalType create(ResolvedNormalType original, TypeKind typeKind)
  {
    return new ResolvedNormalType(original, typeKind);
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
    this._typeKind          = original._typeKind;
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
    this._outer              = (original._outer instanceof ResolvedNormalType ot)
      ? ot.clone(originalOuterFeature)
      : original._outer;
    this._feature            = original._feature;

    if (POSTCONDITIONS) ensure
      (feature().generics().sizeMatches(generics()));
  }


  /**
   * create resolved type for feature in universe and generics
   *
   * @param feature the feature that is defined in universe
   *
   * @param generics the generics of the type
   *
   */
  public static AbstractType create(AbstractFeature feature, List<AbstractType> generics)
  {
    if (PRECONDITIONS) require
      (feature.outer().isUniverse());

    return create(generics, null, feature);
  }


  /**
   * Constructor for artificial built-in types.
   */
  protected ResolvedNormalType()
  {
    this(UnresolvedType.NONE, UnresolvedType.NONE, null, null, TypeKind.ValueType);
  }


  /*-------------------------  static methods  --------------------------*/



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
      (t == Types.t_ERROR || (t.outer() == null) == (o == null));

    AbstractType result;
    if (t == Types.t_ERROR ||
        o == Types.t_ERROR   )
      {
        result = Types.t_ERROR;
      }
    else
      {
        result = ResolvedNormalType.create(t.generics(),
                                           t.unresolvedGenerics(),
                                           o,
                                           t.feature(),
                                           t.kind());
      }
    return result;
  }


  /*-----------------------------  methods  -----------------------------*/



  /**
   * The mode of the type: ThisType, RefType or ValueType.
   */
  @Override
  public TypeKind kind()
  {
    return _typeKind;
  }


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
   * For a resolved normal type, return the underlying feature.
   *
   * @return the underlying feature.
   *
   * @throws Error if this is not resolved or isGenericArgument().
   */
  @Override
  protected AbstractFeature backingFeature()
  {
    if (PRECONDITIONS) require
      (Errors.any() || _feature != null);

    return _feature != null
      ? _feature
      : Types.f_ERROR;
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
   * `this` as a value.
   *
   * Requires that at isNormalType().
   */
  @Override
  public AbstractType asValue()
  {
    if (PRECONDITIONS) require
      (isNormalType());

    return switch (kind())
      {
      case ValueType -> this;
      case RefType   -> create(generics(), Call.NO_GENERICS, outer(), feature(), TypeKind.ValueType);
      default        -> throw new Error("unexpected kind "+kind()+" for ResolvedNormalType");
    };
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
  @Override
  public AbstractType replaceGenericsAndOuter(List<AbstractType> g2, AbstractType o2)
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
    return (this == Types.t_UNDEFINED || this == Types.t_ERROR) ? this :
      new ResolvedNormalType(this, originalOuterFeature)
      {
        AbstractFeature originalOuterFeature(AbstractFeature currentOuter)
        {
          return originalOuterFeature;
        }
        AbstractType _resolved = null;

        /**
         * NYI: CLEANUP:
         * This is a bit ugly, even though this type is a ResolvedType, the generics are not.
         */
        @Override
        AbstractType resolve(Resolution res, Context context)
        {
          if (_resolved == null)
            {
              _resolved = UnresolvedType.finishResolve(res, context, this, declarationPos(), feature(), _generics, unresolvedGenerics(), outer(), kind(), false, false);
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
  @Override
  void usedFeatures(Set<AbstractFeature> s)
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
