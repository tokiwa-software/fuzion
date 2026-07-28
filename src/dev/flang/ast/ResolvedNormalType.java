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
  final List<AbstractType> _typeArguments;
  public final List<AbstractType> typeArguments() { return _typeArguments; }


  /**
   * For a normal type, this is the list of the unresolved version of actual
   * type parameters given to the type, as far as they are available. They are
   * not available, e.g., when the type was inferred or was loaded from a module
   * file.  The list might be shorter than generics().
   */
  private final List<AbstractType> _unresolvedTypeArguments;
  public final List<AbstractType> unresolvedTypeArguments() { return _unresolvedTypeArguments; }


  /**
   * The outer type, for the type p.q.r in the code
   */
  private AbstractType _outer;


  /**
   * The underlying feature this type was derived from.  _feature is a routine or a choice.
   */
  protected AbstractFeature _feature;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Instantiate a new ResolvedNormalType.
   *
   * @param t the original type
   *
   * @param ta the type arguments that replace t.generics (resolved)
   *
   * @param uta the type arguments that replace t.generics (unresolved)
   *
   * @param o the actual outer type, or null, that replaces t.outer
   */
  public static ResolvedType create(AbstractType t, List<AbstractType> ta, List<AbstractType> uta, AbstractType o)
  {
    if (PRECONDITIONS) require
      ( t.feature().typeParameters().sizeMatches(ta),
        t == Types.t_ERROR || (t.outer() == null) == (o == null));

    return create(ta, uta, o, t.feature(), t.kind());
  }


  /**
   * Instantiate a new ResolvedNormalType.
   *
   * @param ta the type arguments (resolved)
   *
   * @param o
   *
   * @param f if this type corresponds to a feature, then this is the
   * feature, else null.
   */
  public static ResolvedType create(List<AbstractType> ta, AbstractType o, AbstractFeature f)
  {
    return create(ta, Call.NO_TYPE_ARGUMENTS, o, f, f.defaultTypeKind());
  }


  /**
   * Constructor
   *
   * @param ta the type arguments (resolved)
   *
   * @param uta the type arguments (unresolved)
   *
   * @param o
   *
   * @param f if this type corresponds to a feature, then this is the
   * feature, otherwise null.
   *
   * @param typeKind true iff this type should be a ref type, otherwise it will be a
   * value type.
   */
  protected ResolvedNormalType(List<AbstractType> ta,
                               List<AbstractType> uta,
                               AbstractType o,
                               AbstractFeature f,
                               TypeKind typeKind)
  {
    if (PRECONDITIONS) require
      (Errors.any() || f == null || f.typeParameters().sizeMatches(ta == null ? UnresolvedType.NONE : ta),
       typeKind == TypeKind.ValueType || typeKind == TypeKind.RefType);

    this._typeArguments = ta == null || ta.isEmpty() ? UnresolvedType.NONE : ta.freeze();
    this._unresolvedTypeArguments = ((uta == null) || uta.isEmpty()) ? UnresolvedType.NONE : uta;

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
       || feature().typeParameters().sizeMatches(typeArguments())
       || typeArguments().isEmpty() /* e.g. an incomplete type in a match case */,
        typeArguments().stream().allMatch(x -> !x.isCotypeType()),
        // the outer of a cotype must be a parametric type or a cotype
        o == null || o.backingFeature().isUniverse() || _feature == null || !_feature.isCotype() || o.isParametricType() || o.isCotypeType());
  }

  /**
   * Instantiate a new ResolvedNormalType.
   */
  public static ResolvedType create(List<AbstractType> ta,
                                    List<AbstractType> uta,
                                    AbstractType o,
                                    AbstractFeature f,
                                    TypeKind typeKind)
  {
    return f == Types.f_ERROR || ta.contains(Types.t_ERROR)
      ? Types.t_ERROR
      : new ResolvedNormalType(ta, uta, o, f, typeKind);
  }


  /**
   * Instantiate a new ResolvedNormalType.
   */
  public static ResolvedNormalType create(ResolvedNormalType original, TypeKind typeKind)
  {
    if (PRECONDITIONS) require
      (Types.resolved == null
         || !original.isVoid(),
       typeKind == TypeKind.ValueType || typeKind == TypeKind.RefType);
    return new ResolvedNormalType(original._typeArguments, original._unresolvedTypeArguments, original._outer, original._feature, original._typeKind);
  }


  /**
   * create resolved type for feature in universe and generics
   *
   * @param feature the feature that is defined in universe
   *
   * @param typeArguments the generics of the type
   *
   */
  public static AbstractType create(AbstractFeature feature, List<AbstractType> typeArguments)
  {
    if (PRECONDITIONS) require
      (feature.outer().isUniverse());

    return create(typeArguments, null, feature);
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
        result = ResolvedNormalType.create(t.typeArguments(),
                                           t.unresolvedTypeArguments(),
                                           o,
                                           t.feature(),
                                           t.kind());
      }
    return result;
  }


  /*-----------------------------  methods  -----------------------------*/



  /**
   * The mode of the type: ParametricType, ThisType, RefType or ValueType.
   */
  @Override
  public TypeKind kind()
  {
    return _typeKind;
  }


  /**
   * For a resolved normal type, return the underlying feature.
   *
   * @return the underlying feature.
   *
   * @throws Error if this is not resolved or isParametricType().
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
   * {@code this} as a value.
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
      case RefType   -> create(typeArguments(), Call.NO_TYPE_ARGUMENTS, outer(), feature(), TypeKind.ValueType);
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
  public AbstractType replaceTypeArgumentsAndOuter(List<AbstractType> g2, AbstractType o2)
  {
    return ResolvedNormalType.create(this, g2, unresolvedTypeArguments(), o2);
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
    var result = this;
    if (!isArtificialType())
      {
        var g = _typeArguments;
        if (!_typeArguments.isEmpty())
          {
            g = new List<>();
            for (var og : _typeArguments)
              {
                var gc = (og instanceof ResolvedNormalType gt)
                  ? gt.clone(originalOuterFeature)
                  : og;
                g.add(gc);
              }
            g.freeze();
          }
        var o = _outer instanceof ResolvedNormalType ot
          ? ot.clone(originalOuterFeature)
          : _outer;

        result = new ResolvedNormalType(g, _unresolvedTypeArguments, o, _feature, _typeKind)
          {
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
                  _resolved = UnresolvedType.finishResolve(res, context, this, declarationPos(), feature(), _typeArguments, unresolvedTypeArguments(), outer(), kind(), false, false);
                }
              return _resolved;
            }
          };
      }
    return result;

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
        for (var g : typeArguments())
          {
            g.usedFeatures(s);
          }
        if (isChoice())
          {
            for (var g : choiceTypes(Context.NONE))
              {
                g.usedFeatures(s);
              }
          }
      }
  }

}

/* end of file */
