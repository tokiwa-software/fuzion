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
 * Source of class UnresolvedType
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Optional;
import java.util.Set;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * UnresolvedType represents the abstract syntax tree of a Fuzion type parsed from source
 * code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class UnresolvedType extends AbstractType implements HasSourcePosition
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Pre-allocated empty type list. NOTE: There is a specific empty type List
   * {@code Call.NO_GENERICS} which is used to distinguish {@code a.b<>()} (using {@code UnresolvedType.NONE})
   * from {@code a.b()} (using {@code Call.NO_GENERICS}).
   */
  public static final List<AbstractType> NONE = new List<AbstractType>();
  static { NONE.freeze(); }


  /**
   * pre-allocated empty array of types
   */
  static final UnresolvedType[] NO_TYPES = new UnresolvedType[0];


  /*----------------------------  variables  ----------------------------*/


  /**
   * The sourcecode position of the use of this parsed type, used for error
   * messages.
   */
  public final HasSourcePosition _pos;


  /**
   * The sourcecode position of the use of this parsed type, used for error
   * messages.
   */
  public SourcePosition pos() { return _pos.pos(); }


  /**
   * The sourcecode position of the declaration point of this type, or, for
   * unresolved types, the source code position of its use.
   */
  public SourcePosition declarationPos() { return _pos.pos(); }


  /**
   * Is this an explicit reference or value type?  Ref/Value to make this a
   * reference/value type independent of the type of the underlying feature
   * defining a ref type or not, false to keep the underlying feature's
   * ref/value status.
   */
  final Optional<TypeMode> _typeMode;


  /**
   * the name of this type.  For a type {@code map<string,i32>.entry}, this is just
   * the base name {@code entry}. For a type parameter {@code A}, this is {@code A}. For an
   * artificial type, this is one of {@code Types.INTERNAL_NAMES} (e.g., {@code --ADDRESS--}).
   */
  protected String _name;
  protected String name()
  {
    return _name;
  }


  /**
   *
   */
  List<AbstractType> _generics;
  public final List<AbstractType> generics() { return _generics; }


  /**
   * The outer type, for the type p.q.r in the code
   *
   * a
   * {
   *   b
   *   {
   *     c
   *     {
   *       x p.q.r;
   *     }
   *   }
   *   p { ... }
   * }
   *
   * the _outer of "r" is "p.q", and the outer of "q" is "p".
   *
   * However, if p is declared in a, after type resolution, the outer type of
   * "p" is "a" or maybe an heir of "a".
   */
  private AbstractType _outer;


  /**
   * If set, resolution of this type should not check if the actual type
   * parameters are valid.  This is set for types used in a match case when the
   * actual type parameters are inferred from the subject type as in
   *
   *   x list i32 := [1,2,3].as_list
   *   match x
   *     c Cons => ...
   *     nil    => ...
   *
   *  where {@code Cons} stands for {@code Cons i32 (list i32)}.
   */
  boolean _ignoreActualTypePars = false;


  /**
   * Once this unresolved type was resolved into a ResolvedParametricType or
   * ResolvedNormalType, this will be set to the resolution result to avoid
   * repeated resolution.
   */
  AbstractType _resolved = null;

  /**
   * Was this Unresolved type followed by '...' when parsed
   */
  boolean _followedByDots = false;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param n
   *
   * @param g
   *
   * @param o
   */
  public UnresolvedType(HasSourcePosition pos, String n, List<AbstractType> g, AbstractType o)
  {
    this(pos, n, g, o, Optional.empty());
  }


  /**
   * Constructor to create a type from an existing type after formal generics
   * have been replaced in the generics arguments and in the outer type.
   *
   * @param t the original type
   *
   * @param g the actual generic arguments that replace t.generics
   *
   * @param o the actual outer type, or null, that replaces t.outer
   */
  public UnresolvedType(UnresolvedType t, List<AbstractType> g, AbstractType o)
  {
    this(t.pos(), t._name, g, o, t._typeMode);

    if (PRECONDITIONS) require
      (Errors.any() ||  (t.generics() instanceof FormalGenerics.AsActuals   ) || t.generics().size() == g.size(),
       Errors.any() || !(t.generics() instanceof FormalGenerics.AsActuals aa) || aa.sizeMatches(g),
       (t.outer() == null) == (o == null));
  }


  /**
   * Constructor
   *
   * @param n
   *
   * @param g the actual generic arguments
   *
   * @param o
   *
   * @param typeMode true iff this type should be a ref type, otherwise it will be a
   * value type.
   */
  public UnresolvedType(HasSourcePosition pos, String n, List<AbstractType> g, AbstractType o, Optional<TypeMode> typeMode)
  {
    if (PRECONDITIONS) require
      (pos != null,
       n.length() > 0);

    this._pos      = pos;
    this._name     = n;
    this._generics = ((g == null) || g.isEmpty()) ? NONE : g;
    this._generics.freeze();
    this._outer    = o;
    this._typeMode = typeMode;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Constructor for built-in types
   *
   * @param n the name, such as "int", "bool".
   */
  public static AbstractType type(Resolution res, String n, AbstractFeature universe)
  {
    if (PRECONDITIONS) require
      (n.length() > 0);

    return type(res, false, n, universe);
  }

  /**
   * Constructor for built-in types
   *
   * @param ref true iff we create a ref type
   *
   * @param n the name, such as "int", "bool".
   */
  public static AbstractType type(Resolution res, boolean ref, String n, AbstractFeature universe)
  {
    if (PRECONDITIONS) require
      (n.length() > 0);

    return new BuiltInType(ref, n).resolve(res, universe.context());
  }


  /**
   * Create a ref or value type from a given value / ref type.
   *
   * @param original the original value type
   *
   * @param typeMode must be TypeMode.Boxed or TypeMode.Val
   */
  public UnresolvedType(UnresolvedType original, TypeMode typeMode)
  {
    if (PRECONDITIONS) require
      (original._typeMode.isEmpty() || typeMode != original._typeMode.get());

    this._pos               = original._pos;
    this._typeMode          = Optional.of(typeMode);
    this._name              = original._name;
    this._generics          = original._generics;
    this._outer             = original._outer;
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
  UnresolvedType(UnresolvedType original, AbstractFeature originalOuterFeature)
  {
    this._pos               = original._pos;
    this._typeMode          = original._typeMode;
    this._name              = original._name;
    if (original._generics.isEmpty())
      {
        this._generics          = original._generics;
      }
    else
      {
        this._generics = new List<>();
        for (var g : original._generics)
          {
            var gc = (g instanceof UnresolvedType gt)
              ? gt.clone(originalOuterFeature)
              : g;
            this._generics.add(gc);
          }
        this._generics.freeze();
      }
    this._outer             = (original._outer instanceof UnresolvedType ot) ? ot.clone(originalOuterFeature) : original._outer;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Return this type as a simple qualifier.  This is null by default except for
   * types without generics and without {@code ref} modifier.
   */
  public List<ParsedName> asQualifier()
  {
    List<ParsedName> res = _outer instanceof UnresolvedType uo ? uo.asQualifier()
                                                               : new List<>();

    if (res == null ||
        !_generics.isEmpty() ||
        !_typeMode.isEmpty())
      {
        res = null;
      }
    else
      {
        res.add(new ParsedName(pos(), _name));
      }
    return res;
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
   * Create a reference variant of this type.  Return this
   * in case it is a reference already.
   */
  public AbstractType asRef()
  {
    // throw new Error("asRef not available for unresolved type");
    return this;
    /*
    AbstractType result = this;
    if (!isRef() && this != Types.t_ERROR)
      {
        result = ResolvedNormalType.create(this, TypeMode.Boxed);
      }
      return result;*/
  }


  /**
   * Create a this.type variant of this type.  Return this
   * in case it is a this.type or a choice variant already.
   */
  public AbstractType asThis()
  {
    //throw new Error("asThis not available for unresolved type");
    return this;
    /*
    AbstractType result = this;
    if (!isThisType() && !isChoice() && this != Types.t_ERROR)
      {
        result = ResolvedNormalType.create(this, TypeMode.ThisType);
      }

    if (POSTCONDITIONS) ensure
      (result == Types.t_ERROR || result.isThisType() || result.isChoice(),
       !(isThisType() || isChoice()) || result == this);

    return result;
    */
  }


  @Override
  public AbstractType asValue()
  {
    throw new Error("asValue not available for unresolved type");
  }


  /**
   * Call Constructor for a function type that returns a result
   *
   * @param returnType the result type.
   *
   * @param arguments the arguments list
   *
   * @return a UnresolvedType instance that represents this function
   */
  public static ParsedType funType(SourcePosition pos, AbstractType returnType, List<AbstractType> arguments)
  {
    if (PRECONDITIONS) require
      (returnType != null,
       arguments != null);

    // This is called during parsing, so Types.resolved.f_function is not set yet.
    return new ParsedType(pos,
                          arguments.size() == 1 ? Types.UNARY_NAME  :
                          arguments.size() == 2 ? Types.BINARY_NAME : Types.FUNCTION_NAME,
                          new List<AbstractType>(returnType, arguments),
                          null);
  }


  @Override
  public String toString(boolean humanReadable, AbstractFeature context)
  {
    return toString();
  }


  /**
   * Get a String representation of this Type.
   */
  @Override
  public String toString()
  {
    String result;

    if (Types.INTERNAL_NAMES.contains(_name))
      {
        result = _name;
      }
    else if (_outer != null)
      {
        String outer = _outer.toStringWrapped();
        result = ""
          + (outer == "" ||
             outer.equals(FuzionConstants.UNIVERSE_NAME) ? ""
                                                         : outer + ".")
          + (_typeMode.map(tm ->
              tm == TypeMode.RefType   ? "ref "  :
              tm == TypeMode.ValueType ? "value "
                                       : "").orElse(""))
          + _name;
      }
    else
      {
        result =
          (_typeMode.orElse(TypeMode.ValueType) == TypeMode.RefType ? "ref "
                                                                    : "")
          + _name;
      }
    if (_generics != NONE)
      {
        result = result + _generics
          .toString(" ", " ", "", (g) -> g.toStringWrapped());
      }
    return result;
  }


  /**
   * resolve this type, i.e., find or create the corresponding instance of
   * ResolvedType of this and all outer types and type arguments this depends on.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param context the source code context where this assignment is used
   */
  @Override
  AbstractType resolve(Resolution res, Context context)
  {
    return resolve(res, context, false);
  }


  /**
   * resolve this type, i.e., find or create the corresponding instance of
   * ResolvedType of this and all outer types and type arguments this depends on.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param context the outer feature this type is declared in. Lookup of
   * unqualified types will happen in this feature.
   *
   * @param tolerant behavior if resolution is not possible
   *                 if true return null, if false flag error
   */
  AbstractType resolve(Resolution res, Context context, boolean tolerant)
  {
    if (PRECONDITIONS) require
      (res != null,
       context != null);

    var outer = context.outerFeature();
    res.resolveDeclarations(outer);

    if (!tolerant && _resolved == null)
      {
        _resolved = resolveThisTypeInCotype(res, outer);
      }
    if (_resolved == null)
      {
        var of = outer;
        var o = _outer;
        var inCotype = false;
        if (!tolerant && (o != null && !o.isThisType()))
          {
            // workaround for not yet resolved universe: #4141
            if (!(o instanceof UnresolvedType ut && ut.name().equals(FuzionConstants.UNIVERSE_NAME)))
              {
                o = o.resolve(res, context);
                var ot2 = o.selfOrConstraint(res, context); // see tests/reg_issue1943 for examples
                of = ot2.feature();
              }
            else
              {
                o = null;
                of = res.universe;
              }
          }
        else if (tolerant && (o instanceof UnresolvedType ut))
          {
            o = ut.resolve(res, context, true);
            if (o == null || o == Types.t_ERROR)
              {
                return null;
              }
            var ot2 = o.selfOrConstraint(res, context); // see tests/reg_issue1943 for examples
            of = ot2.feature();
          }
        else
          {
            inCotype = of != originalOuterFeature(of);
          }

        var ot = outer();
        if (ot != null && ot.isGenericArgument())
          {
            if (tolerant) { return null; }
            else { AstErrors.formalGenericAsOuterType(pos(), this); }
          }

        var mayBeFreeType = mayBeFreeType() && outer.isValueArgument();

        var traverseOuter = ot == null && _name != FuzionConstants.COTYPE_THIS_TYPE;
        var fo = tolerant ? res._module.lookupType(pos(), of, _name, traverseOuter,
                                                   true /* ignore ambiguous */ ,
                                                   true /* ignore not found */)
                          : res._module.lookupType(pos(), of, _name, traverseOuter,
                                                   false                           /* ignore ambiguous */,
                                                   mayBeFreeType || inCotype       /* ignore not found */);
        if (fo == null || !fo._feature.isTypeParameter() && inCotype)
          { // if we are in a type feature, type lookup happens in the
            // original feature, except for type parameters that we just
            // checked in the type feature (of).
            of = originalOuterFeature(of);
            fo = tolerant ? res._module.lookupType(pos(), of, _name, traverseOuter,
                                                   true /* ignore ambiguous */ ,
                                                   true /* ignore not found */)
                          : res._module.lookupType(pos(), of, _name, traverseOuter,
                                                   false          /* ignore ambiguous */,
                                                   mayBeFreeType  /* ignore not found */);
          }
        if (!tolerant && _resolved == null)
          {
            if (fo == FeatureAndOuter.ERROR)
              {
                _resolved = Types.t_ERROR;
              }
            else if (fo == null)
              {
                _resolved = addAsFreeType(res, context);
              }
            else if (isFreeType())
              {
                AstErrors.freeTypeMustNotMaskExistingType(this, fo._feature);
                _resolved = Types.t_ERROR;
              }
            else
              {
                var f = fo._feature;
                var generics = generics();
                if (o == null && f.isTypeParameter())
                  {
                    if (!generics.isEmpty())
                      {
                        AstErrors.formalGenericWithGenericArgs(pos(), this, f);
                      }
                    var gt = f.asGenericType();
                    if (gt.isOpenGeneric() && !(outer instanceof Feature off && off.isLastArgType(this)))
                      {
                        AstErrors.illegalUseOfOpenFormalGeneric(pos(), gt.genericArgument());
                        _resolved = Types.t_ERROR;
                      }
                    else
                      {
                        _resolved = gt;
                      }
                  }
                else
                  {
                    if (o == null && !fo._outer.isUniverse())
                      {
                        o = fo._outer.thisType(fo.isNextInnerFixed());
                      }
                    _resolved = finishResolve(res, context, this, this, f, generics, generics(), o, _typeMode.orElse(f.defaultTypeMode()), _ignoreActualTypePars, tolerant);
                  }
              }
          }

        var outerfeat = context.outerFeature();

        if (tolerant && CHECKS) check
          (fo != FeatureAndOuter.ERROR);

        if (tolerant && fo != null)
          {
            var f = fo._feature;
            var generics = generics();
            if (o == null && f.isTypeParameter())
              {
                if (generics.isEmpty())
                  {
                    var gt = f.asGenericType();
                    if (!gt.isOpenGeneric() || (outerfeat instanceof Feature off && off.isLastArgType(this)))
                      {
                        _resolved = gt;
                      }
                  }
              }
            else
              {
                if (o == null && !fo._outer.isUniverse())
                  {
                    o = fo._outer.thisType(fo.isNextInnerFixed());
                  }
                _resolved = finishResolve(res, context, this, this, f, generics, null, o, _typeMode.orElse(f.defaultTypeMode()), _ignoreActualTypePars, tolerant);
              }
          }
      }

    if (_resolved != null && _resolved.isOpenGeneric() && !_followedByDots)
      {
        AstErrors.openGenericMissingDots(pos(), _resolved);
      }

    if (_resolved != null && !_resolved.isOpenGeneric() && _followedByDots)
      {
        AstErrors.dotsButNotOpenGeneric(pos(), _resolved);
      }

    return _resolved;
  }


  /**
   * Perform the last steps of resolve() for a normal type (not a type
   * parameter).
   *
   *  - if typeMode is ThisType, set generics to the formal generics used as
   *    actual.
   *
   *  - otherwise, resolve the formal generics and check that their number
   *    matches what is required
   *
   * Finally, create instance of ResolvedNormalType
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this type is used
   *
   * @param thiz the original, unresolved type. Used for error reporting.
   *
   * @param pos the position of this type, used for error reporting.
   *
   * @param f the features this type is built from
   *
   * @param generics the actual type parameters
   *
   * @param unresolvedGenerics the original, unresolved actual type
   * parameters. Used for error reporting to obtain the original source code
   * position.
   *
   * @param o the resolved outer type
   *
   * @param typeMode Select the type variant: value, boxed, thisType
   *
   * @param ignoreActualTypePars if true no errors will be reported in case the
   * number of actual type parameters does not match the formal type parameters.
   *
   * @param tolerant behavior if resolution is not possible
   *
   * @return an instance of ResolvedNormalType representing the given type.
   */
  static ResolvedType finishResolve(Resolution res,
                                    Context context,
                                    AbstractType thiz,
                                    HasSourcePosition pos,
                                    AbstractFeature f,
                                    List<AbstractType> generics,
                                    List<AbstractType> unresolvedGenerics,
                                    AbstractType o,
                                    TypeMode typeMode,
                                    boolean ignoreActualTypePars,
                                    boolean tolerant)
  {
    if (tolerant) { unresolvedGenerics = new List<>(); }

    if (!ignoreActualTypePars)
      {
        if (typeMode == TypeMode.ThisType && generics.isEmpty())
          {
            generics = f.generics().asActuals();
          }
        else
          {
            if (tolerant)
              {
                if (!(generics instanceof FormalGenerics.AsActuals))
                  {
                    generics = generics.map(t -> t instanceof UnresolvedType ut ? ut.resolve(res, context, true) : t);
                  }
                if (!f.generics().sizeMatches(generics) || generics.contains(null))
                  {
                    f = Types.f_ERROR;
                  }
              }
            else
              {
                generics = FormalGenerics.resolve(res, generics, context.outerFeature());
                if (!f.generics().errorIfSizeDoesNotMatch(generics,
                                                          pos.pos(),
                                                          "type",
                                                          "Type: " + thiz.toString(true) + "\n"))
                  {
                    f = Types.f_ERROR;
                  }
              }
          }
        generics.freeze();
      }

    return ResolvedNormalType.create(generics,
                                     unresolvedGenerics,
                                     o,
                                     f,
                                     typeMode,
                                     false);
  }


  /**
   * Called by Case.java for case entries that may infer actual type parameters
   * from the subjects.
   */
  void doIgnoreEmptyActualTypePars()
  {
    _ignoreActualTypePars = _generics.isEmpty();
  }


  /**
   * resolve 'abc.this.type' within a cotype. If this designates a
   * 'this.type' withing a cotype, then return the type parameter of the
   * corresponding outer type.
   *
   * Example: if this is
   *
   *   b.this.type
   *
   * within a cotype
   *
   *   a.type.b.type.c.d
   *
   * then we replace 'b.this.type' by the type parameter of a.b.type.
   *
   * @param res
   *
   * @param outerfeat the outer feature this type is declared in.
   *
   * @return null if no matching this type was found, the resolved type
   * otherwise.
   */
  private AbstractType resolveThisTypeInCotype(Resolution res, AbstractFeature outerfeat)
  {
    if (PRECONDITIONS) require
      (outerfeat != null,
       outerfeat != null && res.state(outerfeat).atLeast(State.RESOLVING_DECLARATIONS));

    AbstractType result = null;
    var o = outerfeat;
    while (isThisType() && o != null)
      {
        if (isMatchingCotype(o))
          {
            result = o.typeArguments().get(0).asGenericType();
            o = null;
          }
        else
          {
            o = o.outer();
          }
      }
    return result;
  }


  /**
   * Recursive helper for resolveThisType to check if outerfeat is a type
   * feature with the same name as this.
   *
   * @param outerfeat the outer feature that should be compared to this.
   */
  private boolean isMatchingCotype(AbstractFeature outerfeat)
  {
    return outerfeat.isCotype() &&
      (_name + "." + FuzionConstants.TYPE_NAME).equals(outerfeat.featureName().baseName()) &&
      (_outer == null                                   ||
       (_outer instanceof UnresolvedType ot                   &&
        !ot.isThisType()                            &&
        ot.isMatchingCotype(outerfeat.outer())   )    );
  }


  /**
   * isGenericArgument
   *
   * @return
   */
  public boolean isGenericArgument()
  {
    if (false)  // NYI: if findGenerics and resolve would be done in the same phase we could throw this error here:
      {
        throw new Error("isGenericArgument not known for unresolved type");
      }
    return false;
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
    throw new Error("feature not available for unresolved type");
  }


  /**
   * Is this the type of a type feature, e.g., the type of {@code (list i32).type}. Will return false for an instance of Type for which this is
   * still unknown since Type.resolve() was not called yet.
   *
   * This is redefined here since {@code feature} might still be null while this type
   * was not resolved yet.
   */
  @Override
  boolean isCotypeType()
  {
    return false;
  }


  /**
   * genericArgument gives the Generic instance of a type defined by a generic
   * argument.
   *
   * @return the Generic instance, never null.
   */
  public AbstractFeature genericArgument()
  {
    if (PRECONDITIONS) require
      (false);

    throw new Error();
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
   * May this unresolved type be a free type. This is the case for explicit free
   * types such as {@code X : Any}, and for all normal types like {@code XYZ} that are not
   * qualified by an outer type {@code outer.XYZ} and that do not have actual type
   * parameters {@code XYZ T1 T2} and that are not boxed.
   */
  public boolean mayBeFreeType()
  {
    return false;
  }


  /**
   * Is this type a free type?  Result is false for unresolved types where this
   * is not known yet.
   */
  public boolean isFreeType()
  {
    return false;
  }


  /**
   * For a type {@code XYZ} with mayBeFreeType() returning true, this gives the name
   * of the free type, which would be {@code "XYZ"} in this example.
   *
   * @return the name of the free type, which becomes the name of the type
   * parameter created for it.
   */
  public String freeTypeName()
  {
    throw new Error("freeTypeName cannot be called on " + getClass());
  }


  /**
   * For an unresolved type with mayBeFreeType() == true, this gives the
   * constraint to be used with that free type.
   */
  UnresolvedType freeTypeConstraint()
  {
    return new BuiltInType(FuzionConstants.ANY_NAME);
  }


  /**
   * Add this type as a free type to context.outerFeature().outer()
   */
  AbstractType addAsFreeType(Resolution res, Context context)
  {
    var outer = context.outerFeature();

    if (CHECKS) check
      (outer.isValueArgument());

    var tp = new Feature(pos(),
                         outer.visibility(),
                         0,
                         freeTypeConstraint().resolve(res, context),
                         _name,
                         Contract.EMPTY_CONTRACT,
                         Impl.TYPE_PARAMETER)
      {
        /**
         * Is this type a free type?
         */
        public boolean isFreeType() { return true; }
      };
    var g = outer.outer().addTypeParameter(res, tp);
    return g.asGenericType();
  }



  /**
   * traverse a type collecting all features this type uses.
   *
   * @param s the features that have already been found
   */
  protected void usedFeatures(Set<AbstractFeature> s)
  {
    throw new Error("must not be called on unresolved types.");
  }

  /**
   * Mark this type as being followed by '...' e.g. 'A...'
   */
  public void setFollowedByDots()
  {
    _followedByDots = true;
  }


  /**
   * The mode of the type: ThisType, RefType or ValueType.
   */
  @Override
  public TypeMode mode()
  {
    return _typeMode
      // NYI: UNDER DEVELOPMENT: always correct?
      .orElse(TypeMode.ValueType);
  }


}

/* end of file */
