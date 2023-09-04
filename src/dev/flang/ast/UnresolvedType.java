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
   * Call.NO_GENERICS which is used to distinguish "a.b<>()" (using UnresolvedType.NONE)
   * from "a.b()" (using Call.NO_GENERICS).
   */
  public static final List<AbstractType> NONE = new List<AbstractType>();
  static { NONE.freeze(); }


  /**
   * pre-allocated empty array of types
   */
  static final UnresolvedType[] NO_TYPES = new UnresolvedType[0];

  /**
   * Is this type explicitly a reference or a value type, or whatever the
   * underlying feature is?
   */
  public enum RefOrVal
  {
    Boxed,                  // this is boxed value type or an explicit reference type
    Value,                  // this is an explicit value type
    LikeUnderlyingFeature,  // this is ref or value as declared for the underlying feature
    ThisType,               // this is the type of featureOfType().this.type, i.e., it may be an heir type
  }


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
  RefOrVal _refOrVal;


  /**
   * the name of this type.  For a type 'map<string,i32>.entry', this is just
   * the base name 'entry'. For a type parameter 'A', this is 'A'. For an
   * artificial type, this is one of Types.INTERNAL_NAMES (e.g., '--ADDRESS--).
   */
  public String _name;
  public String name()
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
   * Once this unresolved type was resolved into a ResolvedParametricType or
   * ResolvedNormalType, this will be set to the resolution resuilt to avoid
   * repeated resolution.
   */
  AbstractType _resolved = null;


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
    this(pos, n,g,o,RefOrVal.LikeUnderlyingFeature);
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
    this(t.pos(), t._name, g, o, t._refOrVal, false);

    if (PRECONDITIONS) require
      (Errors.any() ||  (t.generics() instanceof FormalGenerics.AsActuals   ) || t.generics().size() == g.size(),
       Errors.any() || !(t.generics() instanceof FormalGenerics.AsActuals aa) || aa.sizeMatches(g),
        t == Types.t_ERROR || (t.outer() == null) == (o == null));
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
   * @param ref true iff this type should be a ref type, otherwise it will be a
   * value type.
   */
  public UnresolvedType(HasSourcePosition pos, String n, List<AbstractType> g, AbstractType o, RefOrVal refOrVal)
  {
    this(pos, n, g, o, refOrVal, true);
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
   * @param ref true iff this type should be a ref type, otherwise it will be a
   * value type.
   */
  public UnresolvedType(HasSourcePosition pos, String n, List<AbstractType> g, AbstractType o, RefOrVal refOrVal, boolean fixOuterThisType)
  {
    if (PRECONDITIONS) require
      (pos != null,
       n.length() > 0);

    this._pos      = pos;
    this._name     = n;
    this._generics = ((g == null) || g.isEmpty()) ? NONE : g;
    this._generics.freeze();
    this._outer    = o;
    this._refOrVal = refOrVal;
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

    return new BuiltInType(ref, n).resolve(res, universe);
  }


  /**
   * Create a ref or value type from a given value / ref type.
   *
   * @param original the original value type
   *
   * @param refOrVal must be RefOrVal.Boxed or RefOrVal.Val
   */
  public UnresolvedType(UnresolvedType original, RefOrVal refOrVal)
  {
    if (PRECONDITIONS) require
      (refOrVal != original._refOrVal);

    this._pos               = original._pos;
    this._refOrVal          = refOrVal;
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
    this._refOrVal          = original._refOrVal;
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

    // throw new Error("asRef not available for unresolved type");
    return this;
    /*
    AbstractType result = this;
    if (!isRef() && this != Types.t_ERROR)
      {
        result = Types.intern(new ResolvedNormalType(this, RefOrVal.Boxed));
      }
      return result;*/
  }


  /**
   * Create a Types.intern()ed this.type variant of this type.  Return this
   * in case it is a this.type or a choice variant already.
   */
  public AbstractType asThis()
  {
    if (PRECONDITIONS) require
      (this == Types.intern(this));

    //throw new Error("asThis not available for unresolved type");
    return this;
    /*
    AbstractType result = this;
    if (!isThisType() && !isChoice() && this != Types.t_ERROR && this != Types.t_ADDRESS)
      {
        result = Types.intern(new ResolvedNormalType(this, RefOrVal.ThisType));
      }

    if (POSTCONDITIONS) ensure
      (result == Types.t_ERROR || result == Types.t_ADDRESS || result.isThisType() || result.isChoice(),
       !(isThisType() || isChoice()) || result == this);

    return result;
    */
  }


  /**
   * Create a Types.intern()ed value variant of this type.  Return this
   * in case it is a value already.
   */
  public AbstractType asValue()
  {
    if (PRECONDITIONS) require
      (this == Types.intern(this));

    //throw new Error("asValue not available for unresolved type");
    return this;
    /*
    AbstractType result = this;
    if (isRef() && this != Types.t_ERROR)
      {
        result = Types.intern(new ResolvedNormalType(this, RefOrVal.Value));
      }
    return result;
    */
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
                          arguments.size() == 1 ? Types.UNARY_NAME : Types.FUNCTION_NAME,
                          new List<AbstractType>(returnType, arguments),
                          null);
  }


  /**
   * isRef
   */
  public boolean isRef()
  {
    throw new Error("isRef not known in unresolved type");
  }


  /**
   * isThisType
   */
  public boolean isThisType()
  {
    return this._refOrVal == RefOrVal.ThisType;
  }


  /**
   * setOuter
   *
   * @param t
   */
  void setOuter(UnresolvedType t)
  {
    if (this._outer == null)
      {
        this._outer = t;
      }
    else
      {
        this._outer.setOuter(t);
      }
  }


  /**
   * Get a String representation of this UnresolvedType.
   *
   * Note that this does not work for instances of UnresolvedType before they were
   * resolved.  Use toString() for creating strings early in the front end
   * phase.
   */
  public String asString()
  {
    return Types.INTERNAL_NAMES.contains(_name)
      ? toString()         // internal types like Types.t_UNDEFINED, t_ERROR, t_ADDRESS
      : super.asString();
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
    else if (Types.INTERNAL_NAMES.contains(_name))
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
          + (_refOrVal == RefOrVal.Boxed ? "ref "  :
             _refOrVal == RefOrVal.Value ? "value "
                                         : ""       )
          + _name;
      }
    else
      {
        result =
          (_refOrVal == RefOrVal.Boxed ? "ref "
                                       : ""       )
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
   * Find all the types used in this that refer to formal generic arguments of
   * this or any of this' outer classes.
   *
   * @param feat the root feature that contains this type.
   *
   * @return this type with all generic arguments that were found replaced by
   * instances of TypeParameter.
   */
  AbstractType findGenerics(AbstractFeature outerfeat)
  {
    // NYI   if (PRECONDITIONS) require
    //      (!outerfeat.state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

    AbstractType result = this;
    var ot = outer();
    if (ot != null)
      {
        if (ot.isGenericArgument())
          {
            AstErrors.formalGenericAsOuterType(pos(), this);
          }
      }
    else
      {
        var o = outerfeat;
        Generic generic;
        do
          {
            generic = o.getGeneric(_name);
            o = o.outer();
          }
        while (generic == null && o != null);

        if (generic != null)
          {
            result = generic.type();
            if (!_generics.isEmpty())
              {
                AstErrors.formalGenericWithGenericArgs(pos(), this, generic);
              }

            if (!(outerfeat instanceof Feature of && of.isLastArgType(this)))
              {
                result.ensureNotOpen(pos());
              }

            _resolved = result;
          }
      }
    if (result == this)
      {
        _generics = _generics.map(t -> t.findGenerics(outerfeat));
        _generics.freeze();
      }

    return result;
  }


  /**
   * resolve 'abc.this.type' within a type feature. If this designates a
   * 'this.type' withing a type feature, then return the type parameter of the
   * corresponding outer type.
   *
   * Example: if this is
   *
   *   b.this.type
   *
   * within a type feature
   *
   *   a.type.b.type.c.d
   *
   * then we replace 'b.this.type' by the type parameter of a.b.type.
   *
   * @param outerfeat the outer feature this type is declared in.
   */
  AbstractType resolveThisType(AbstractFeature outerfeat)
  {
    if (PRECONDITIONS) require
      (outerfeat != null,
       outerfeat != null && outerfeat.state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

    AbstractType result = this;
    var o = outerfeat;
    while (isThisType() && o != null)
      {
        if (isMatchingTypeFeature(o))
          {
            result = new Generic(o.typeArguments().get(0)).type();
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
  private boolean isMatchingTypeFeature(AbstractFeature outerfeat)
  {
    return outerfeat.isTypeFeature() &&
      (_name + "." + FuzionConstants.TYPE_NAME).equals(outerfeat.featureName().baseName()) &&
      (_outer == null                                   ||
       (_outer instanceof UnresolvedType ot                   &&
        !ot.isThisType()                            &&
        ot.isMatchingTypeFeature(outerfeat.outer())   )    );
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

    AbstractType result = resolveThisType(outerfeat);
    if (result == this)
      {
        result = findGenerics(outerfeat);
        if (!(outerfeat instanceof Feature of && of.isLastArgType(this)))
          {
            result.ensureNotOpen(pos());
          }
        if (result == this)
          {
            result = resolveFeature(res, outerfeat);
            result = result.resolveGenerics(this, res, outerfeat);
            result = Types.intern(result);
          }
      }
    return result;
  }


  /**
   * For a Type that is not a generic argument, resolve the feature of that
   * type.  Unlike Type.resolve(), this does not check the generic arguments, so
   * this can be used for type inferencing for the actual generics as in a match
   * case.
   *
   * @param feat the outer feature this type is declared in, used
   * for resolution of generic parameters etc.
   */
  AbstractType resolveFeature(Resolution res, AbstractFeature outerfeat)
  {
    if (PRECONDITIONS) require
      (outerfeat != null,
       outerfeat != null && outerfeat.state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

    findGenerics(outerfeat);
    if (_resolved == null)
      {
        AbstractType freeResult = null;
        var of = originalOuterFeature(outerfeat);
        var o = _outer;
        if (o != null && !isThisType() && !o.isThisType())
          {
            o = o.resolve(res, of);
            var ot = o.isGenericArgument() ? o.genericArgument().constraint(res) // see tests/reg_issue1943 for examples
                                           : o;
            of = ot.featureOfType();
          }
        AbstractFeature f = Types.f_ERROR;
        if (this instanceof QualThisType q)
          {
            // resolve the feature for a type `a.this.type`, so `a` has to be one of the outer features.
            f = This.getThisFeature(pos(), this, q._qual, of.isTypeFeature() ? of.typeFeatureOrigin() : of);
            var f0o = f.outer();
            o = f0o == null || f0o.isUniverse() ? null : f0o.thisType(false);
          }
        else
          {
            if (CHECKS) check
              (!isThisType());
            var mayBeFreeType = mayBeFreeType() && outerfeat.isValueArgument();
            var fo = res._module.lookupType(pos(), of, _name, o == null, mayBeFreeType);
            if (fo == null)
              {
                freeResult = addAsFreeType(res, outerfeat);
              }
            else if (isFreeType())
              {
                AstErrors.freeTypeMustNotMaskExistingType(this, fo._feature);
              }
            else
              {
                f = fo._feature;
                if (o == null && !fo._outer.isUniverse())
                  {
                    o = fo._outer.thisType(fo.isNextInnerFixed());
                  }
              }
          }
        _outer = o;

        _resolved =
          freeResult != null ? freeResult :
          f == Types.f_ERROR ? Types.t_ERROR
                             : new ResolvedNormalType(generics(),
                                                      unresolvedGenerics(),
                                                      o,
                                                      f,
                                                      _refOrVal,
                                                      false);
      }
    if (!(outerfeat instanceof Feature of && of.isLastArgType(this)))
      {
        _resolved.ensureNotOpen(pos());
      }
    return _resolved;
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
  public AbstractFeature featureOfType()
  {
    throw new Error("featureOfType not available for unresolved type");
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
    return false;
  }


  /**
   * genericArgument gives the Generic instance of a type defined by a generic
   * argument.
   *
   * @return the Generic instance, never null.
   */
  public Generic genericArgument()
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
   * types such as `X : Any`, and for all normal types like `XYZ` that are not
   * qualified by an outer type `outer.XYZ` and that do not have actual type
   * parameters `XYZ T1 T2` and that are not boxed.
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
   * For a type `XYZ` with mayBeFreeType() returning true, this gives the name
   * of the free type, which would be `"XYZ"` in this example.
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


  AbstractType addAsFreeType(Resolution res, AbstractFeature outerfeat)
  {
    if (PRECONDITIONS) require
      (outerfeat.isValueArgument());

    var tp = new Feature(pos(),
                         outerfeat.visibility(),
                         0,
                         freeTypeConstraint(),
                         _name,
                         Contract.EMPTY_CONTRACT,
                         Impl.TYPE_PARAMETER)
      {
        /**
         * Is this type a free type?
         */
        public boolean isFreeType() { return true; }
      };
    var g = outerfeat.outer().addTypeParameter(res, tp);
    return g.type();
  }



  /**
   * traverse a type collecting all features this type uses.
   *
   * @param s the features that have already been found
   */
  protected void usedFeatures(Set<AbstractFeature> s)
  {
    throw new RuntimeException("must not be called on unresolved types.");
  }


}

/* end of file */
