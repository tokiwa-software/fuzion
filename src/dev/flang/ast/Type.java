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
 * Source of class Type
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Set;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Type represents the abstract syntax tree of a Fuzion type parsed from source
 * code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Type extends AbstractType
{

  //  static int counter;  {counter++; if ((counter&(counter-1))==0) { System.out.println("######################"+counter+" "+this.getClass()); if(false)Thread.dumpStack(); } }

  /*----------------------------  constants  ----------------------------*/


  /**
   * Pre-allocated empty type list. NOTE: There is a specific empty type List
   * Call.NO_GENERICS which is used to distinguish "a.b<>()" (using Type.NONE)
   * from "a.b()" (using Call.NO_GENERICS).
   */
  public static final List<AbstractType> NONE = new List<AbstractType>();


  /**
   * pre-allocated empty array of types
   */
  static final Type[] NO_TYPES = new Type[0];

  /**
   * Is this type explicitly a reference or a value type, or whatever the
   * underlying feature is?
   */
  public enum RefOrVal
  {
    Ref,                    // this is an explicit reference type
    Value,                  // this is an explicit value type
    LikeUnderlyingFeature,  // this is ref or value as declared for the underlying feature
    ThisType,               // this is the type of featureOfType().this.type, i.e., it may be an heir type
  }


  /*----------------------------  variables  ----------------------------*/


  /**
   * The sourcecode position of this type, used for error messages.
   */
  public final HasSourcePosition pos;
  public SourcePosition pos() { return pos.pos(); }


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
  public final String name;
  public String name()
  {
    return name;
  }


  /**
   *
   */
  public final List<AbstractType> _generics;
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
   * Cached result of outer(). Note the difference to _outer: _outer is the
   * outer type shown in the source code, while outer()/outerCache_ is the
   * actual outer type taken from the type of the outer feature of this type's
   * feature.
   */
  AbstractType _outerCache;


  /**
   *
   */
  AbstractFeature feature;


  /**
   * In case this is the name of a generic argument in an outer feature, this
   * will be set to that generic argument during findGenerics.  Knowing the
   * generics is a pre-requisite to resolving types.
   */
  Generic generic;


  /**
   * For debugging only: Make sure the findGenerics was called before
   * replaceGeneric or resolve is called.
   */
  boolean checkedForGeneric = false;
  boolean checkedForGeneric() { return checkedForGeneric; }


  /**
   * Cached result of calling Types.intern(this).
   */
  Type _interned = null;


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
  public Type(HasSourcePosition pos, String n, List<AbstractType> g, AbstractType o)
  {
    this(pos, n,g,o,null, RefOrVal.LikeUnderlyingFeature);
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
  public Type(Type t, List<AbstractType> g, AbstractType o)
  {
    this((HasSourcePosition) t, t.name, g, o, t.feature, t._refOrVal);

    if (PRECONDITIONS) require
      (Errors.count() > 0 ||  (t.generics() instanceof FormalGenerics.AsActuals) || t.generics().size() == g.size(),
       Errors.count() > 0 || !(t.generics() instanceof FormalGenerics.AsActuals) || ((FormalGenerics.AsActuals)t.generics()).sizeMatches(g),
        t == Types.t_ERROR || (t.outer() == null) == (o == null));

    checkedForGeneric = t.checkedForGeneric();
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
  public Type(AbstractType t, List<AbstractType> g, AbstractType o)
  {
    this((HasSourcePosition) t, t.featureOfType().featureName().baseName(), g, o, t.featureOfType(),
         t.isRef() == t.featureOfType().isThisRef() ? RefOrVal.LikeUnderlyingFeature :
         t.isRef() ? RefOrVal.Ref
                   : RefOrVal.Value);

    if (PRECONDITIONS) require
      ( (t.generics() instanceof FormalGenerics.AsActuals) || t.generics().size() == g.size(),
       !(t.generics() instanceof FormalGenerics.AsActuals) || ((FormalGenerics.AsActuals)t.generics()).sizeMatches(g),
        t == Types.t_ERROR || (t.outer() == null) == (o == null));

    checkedForGeneric = t.checkedForGeneric();
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
   * @param f if this type corresponds to a feature, then this is the
   * feature, else null.
   *
   * @param ref true iff this type should be a ref type, otherwise it will be a
   * value type.
   */
  public Type(HasSourcePosition pos, String n, List<AbstractType> g, AbstractType o, AbstractFeature f, RefOrVal refOrVal)
  {
    if (PRECONDITIONS) require
      (pos != null,
       n.length() > 0);

    this.pos = pos;
    this.name  = n;
    this._generics = ((g == null) || g.isEmpty()) ? NONE : g;
    if (o instanceof Type ot && ot.isThisType())
      {
        // NYI: CLEANUP: #737: Undo the asThisType() calls done in This.java for
        // outer types. Is it possible to not create asThisType() in This.java
        // in the first place?
        o = new Type(ot, RefOrVal.LikeUnderlyingFeature);
      }
    this._outer = o;
    this.feature = f;
    this.generic = null;
    this._refOrVal = refOrVal;
    this.checkedForGeneric = f != null;
  }


  /**
   * Constructor for a direct use of a generic argument. For a feature
   *
   *   feat<A,B,c>
   *   {
   *     inner { ...outer... }
   *   }
   *
   * the type of the outer reference within inner is feat<A, B, C>.  This
   * constructor is used to create A, B, C in this case.
   *
   * @param g the formal generic this referes to
   */
  public Type(HasSourcePosition pos, Generic g)
  {
    if (PRECONDITIONS) require
      (g != null);

    this.pos = pos;
    this.name  = g.name();
    this._generics = NONE;
    this._outer = null;
    this.feature = null;
    this.generic = g;
    this._refOrVal = RefOrVal.LikeUnderlyingFeature;
    this.checkedForGeneric = true;
  }


  /**
   * Constructor for built-in types
   *
   * @param n the name, such as "int", "bool".
   */
  public Type(String n)
  {
    this(false, n);
  }


  /**
   * Constructor for built-in types
   *
   * @param ref true iff we create a ref type
   *
   * @param n the name, such as "int", "bool".
   */
  private Type(boolean ref, String n)
  {
    if (PRECONDITIONS) require
      (n.length() > 0);

    this.pos               = SourcePosition.builtIn;
    this.name              = n;
    this._generics         = NONE;
    this._outer            = null;
    this.feature           = null;
    this._refOrVal         = ref ? RefOrVal.Ref
                                 : RefOrVal.LikeUnderlyingFeature;
    this.checkedForGeneric = true;
  }

  /**
   * Constructor for built-in types
   *
   * @param n the name, such as "int", "bool".
   */
  public static Type type(Resolution res, String n, AbstractFeature universe)
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
  public static Type type(Resolution res, boolean ref, String n, AbstractFeature universe)
  {
    if (PRECONDITIONS) require
      (n.length() > 0);

    return new Type(ref, n).resolve(res, universe);
  }


  /**
   * Create a ref or value type from a given value / ref type.
   *
   * @param original the original value type
   *
   * @param refOrVal must be RefOrVal.Ref or RefOrVal.Val
   */
  public Type(Type original, RefOrVal refOrVal)
  {
    if (PRECONDITIONS) require
      (refOrVal != original._refOrVal);

    this.pos                = original.pos;
    this._refOrVal          = refOrVal;
    this.name               = original.name;
    this._generics          = original._generics;
    this._outer             = original._outer;
    this.feature            = original.feature;
    this.generic            = original.generic;
    this.checkedForGeneric  = original.checkedForGeneric;
  }


  /*-----------------------------  methods  -----------------------------*/


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
  public AbstractType actualType(List<AbstractType> g2, AbstractType o2)
  {
    if (PRECONDITIONS) require
      (!isGenericArgument());

    return new Type(this, g2, o2);
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
        result = Types.intern(new Type(this, RefOrVal.Ref));
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
      (this == Types.intern(this),
       !isGenericArgument());

    AbstractType result = this;
    if (!isThisType() && !isChoice() && this != Types.t_ERROR)
      {
        result = Types.intern(new Type(this, RefOrVal.ThisType));
      }

    if (POSTCONDITIONS) ensure
      (result == Types.t_ERROR || result.isThisType() || result.isChoice(),
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
        result = Types.intern(new Type(this, RefOrVal.Value));
      }
    return result;
  }


  /**
   * Call Constructor for a function type that returns a result
   *
   * @param returnType the result type.
   *
   * @param arguments the arguments list
   *
   * @return a Type instance that represents this function
   */
  public static Type funType(SourcePosition pos, AbstractType returnType, List<AbstractType> arguments)
  {
    if (PRECONDITIONS) require
      (returnType != null,
       arguments != null);

    // This is called during parsing, so Types.resolved.f_function is not set yet.
    return new Type(pos,
                    Types.FUNCTION_NAME,
                    new List<AbstractType>(returnType, arguments),
                    null);
  }


  /**
   * setRef is called by the parser when parsing a type of the form "ref
   * <simpletype>".
   */
  public void setRef()
  {
    if (PRECONDITIONS) require
      (this._refOrVal == RefOrVal.LikeUnderlyingFeature);

    this._refOrVal = RefOrVal.Ref;
  }


  /**
   * isRef
   */
  public boolean isRef()
  {
    return switch (this._refOrVal)
      {
      case Ref                  -> true;
      case Value                -> false;
      case LikeUnderlyingFeature-> ((feature != null) && feature.isThisRef());
      case ThisType             -> false;
      };
  }


  /**
   * isThisType
   */
  public boolean isThisType()
  {
    return switch (this._refOrVal)
      {
      case Ref, Value, LikeUnderlyingFeature -> false;
      case ThisType                          -> true;
      };
  }


  /**
   * setOuter
   *
   * @param t
   */
  void setOuter(Type t)
  {
    if (this._outer == null)
      {
        if (CHECKS) check
          (_interned == null);

        this._outer = t;
      }
    else
      {
        this._outer.setOuter(t);
      }
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    String result;

    if (Types.INTERNAL_NAMES.contains(name))
      {
        return name;
      }
    else if (generic != null)
      {
        result = generic.feature().qualifiedName() + "." + name + (this.isRef() ? " (boxed)" : "");
      }
    else if (_outer != null)
      {
        String outer = _outer.toString();
        result = ""
          + (outer == "" ||
             outer == FuzionConstants.UNIVERSE_NAME ? ""
                                                    : outer + ".")
          + (_refOrVal == RefOrVal.Ref   && (feature == null || !feature.isThisRef()) ? "ref "   :
             _refOrVal == RefOrVal.Value &&  feature != null &&  feature.isThisRef()  ? "value "
                                                                                      : ""       )
          + (feature == null ? name
             : feature.featureName().baseName());
      }
    else if (feature == null  || feature == Types.f_ERROR)
      {
        result = name;
      }
    else
      {
        result = feature.qualifiedName();
      }
    if (isThisType())
      {
        result = result + ".this.type";
      }
    if (_generics != NONE)
      {
        result = result + "<" + _generics + ">";
      }
    return result;
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   */
  public AbstractType visit(FeatureVisitor v, AbstractFeature outerfeat)
  {
    if ((feature == null) && (generic == null))
      {
        if (_outer != null)
          {
            var t = _outer.visit(v, outerfeat);
            if (t != _outer)
              {
                if (CHECKS) check
                  (_interned == null);

                _outer = _outer.visit(v, outerfeat);
              }
          }
      }
    if (!_generics.isEmpty() && !(_generics instanceof FormalGenerics.AsActuals))
      {
        var i = _generics.listIterator();
        while (i.hasNext())
          {
            var gt = i.next();
            var ng = (gt instanceof Type gtt ? gtt.visit(v, outerfeat) : gt);
            if (CHECKS) check
              (gt == ng || _interned == null);
            i.set(ng);
          }
      }
    return v.action(this, outerfeat);
  }


  /**
   * Find all the types used in this that refer to formal generic arguments of
   * this or any of this' outer classes.
   *
   * @param feat the root feature that contains this type.
   */
  void findGenerics(AbstractFeature outerfeat)
  {
    //    if (PRECONDITIONS) require
    //      (!outerfeat.state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

    if ((feature == null) && (generic == null))
      {
        if (outer() != null)
          {
            if (outer().isGenericArgument())
              {
                AstErrors.formalGenericAsOuterType(pos(), this);
              }
          }
        else
          {
            var o = outerfeat;
            do
              {
                generic = o.getGeneric(name);
                o = o.outer();
              }
            while (generic == null && o != null);

            if ((generic != null) && !_generics.isEmpty())
              {
                AstErrors.formalGenericWithGenericArgs(pos(), this, generic);
              }
          }
      }

    checkedForGeneric = true;

    if (POSTCONDITIONS) ensure
      (checkedForGeneric);
  }


  /**
   * resolve artificial types t_ERROR, etc.
   */
  public void resolveArtificialType(AbstractFeature feat)
  {
    if (PRECONDITIONS) require
      (feature == null,
       Types.INTERNAL_NAMES.contains(name));

    feature = feat;

    var interned = Types.intern(this);

    if (CHECKS) check
      (interned == this);
  }


  /**
   * resolve this type
   *
   * @param feat the outer feature that this type is declared in, used
   * for resolution of generic parameters etc.
   */
  Type resolve(Resolution res, AbstractFeature outerfeat)
  {
    if (PRECONDITIONS) require
      (outerfeat != null,
       outerfeat.state().atLeast(Feature.State.RESOLVED_DECLARATIONS),
       checkedForGeneric);

    if (!(outerfeat instanceof Feature of && of.isLastArgType(this)))
      {
        ensureNotOpen();
      }
    if (!isGenericArgument())
      {
        resolveFeature(res, outerfeat);
        if (feature == Types.f_ERROR)
          {
            return Types.t_ERROR;
          }
        FormalGenerics.resolve(res, _generics, outerfeat);
        if (!feature.generics().errorIfSizeOrTypeDoesNotMatch(_generics,
                                                              this,
                                                              "type",
                                                              "Type: " + toString() + "\n"))
          {
            return Types.t_ERROR;
          }
      }
    return (Type) Types.intern(this);
  }


  /**
   * For a Type that is not a generic argument, resolve the feature of that
   * type.  Unlike Type.resolve(), this does not check the generic arguments, so
   * this can be used for type inferencing for the actual generics as in a match
   * case.
   *
   * @param feat the outer feature that this type is declared in, used
   * for resolution of generic parameters etc.
   */
  void resolveFeature(Resolution res, AbstractFeature outerfeat)
  {
    if (PRECONDITIONS) require
      (outerfeat != null,
       outerfeat.state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

    if (!checkedForGeneric)
      {
        findGenerics(outerfeat);
      }
    if (!isGenericArgument())
      {
        var of = outerfeat;
        if (_outer != null)
          {
            _outer = _outer.resolve(res, outerfeat);
            var ot = _outer.isGenericArgument() ?_outer.genericArgument().constraint() : _outer;
            of = ot.featureOfType();
          }
        if (feature == null)
          {
            feature = res._module.lookupFeatureForType(pos(), name, of);
          }
      }
    if (POSTCONDITIONS) ensure
      (isGenericArgument() || feature != null);
  }


  /**
   * isGenericArgument
   *
   * @return
   */
  public boolean isGenericArgument()
  {
    if (PRECONDITIONS) require
      (checkedForGeneric);

    return generic != null;
  }


  /**
   * featureOfType
   *
   * @return
   */
  public AbstractFeature featureOfType()
  {
    if (PRECONDITIONS) require
      (Errors.count() > 0 || !isGenericArgument());

    var result = feature;

    if (result == null)
      {
        if (CHECKS) check
          (Errors.count() > 0);

        result = Types.f_ERROR;
      }

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
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
      (isGenericArgument());

    Generic result = generic;

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * outer type, after type resolution. This provides the whole chain of types
   * until Types.resolved.universe.thisType(), while the _outer field ends with
   * the outermost type explicitly written in the source code.
   */
  public AbstractType outer()
  {
    var result = _outerCache;
    if (result == null)
      {
        result = _outer;
        if (result == null)
          {
            if (feature != null && feature.state().atLeast(Feature.State.LOADED))
              {
                var of = feature.outer();
                if (of == null)
                  {
                    return null;
                  }
                else
                  {
                    result = of.thisType();
                  }
              }
            else if (generic != null)
              {
                if (CHECKS) check
                  (Errors.count() > 0);
              }
            if (result != null)
              {
                result = Types.intern(result);
                _outerCache = result;
              }
          }
      }
    return result;
  }


  /**
   * Check if this or any of its generic arguments is Types.t_ERROR.
   */
  public boolean containsError()
  {
    boolean result = false;
    if (this == Types.t_ERROR)
      {
        result = true;
      }
    else if (!_generics.isEmpty())
      {
        for (var t: _generics)
          {
            if (CHECKS) check
              (Errors.count() > 0 || t != null);
            result = result || t == null || t.containsError();
          }
      }

    ensure
      (!result || Errors.count() > 0);

    return result;
  }


  /**
   * Check if this or any of its generic arguments is Types.t_UNDEFINED.
   *
   * @param exceptFirstGenericArg if true, the first generic argument may be
   * Types.t_UNDEFINED.  This is used in a lambda 'x -> f x' of type
   * 'Function<R,X>' when 'R' is unknown and to be inferred.
   */
  public boolean containsUndefined(boolean exceptFirst)
  {
    boolean result = false;
    if (this == Types.t_UNDEFINED)
      {
        result = true;
      }
    else if (!_generics.isEmpty())
      {
        for (var t: _generics)
          {
            if (CHECKS) check
              (Errors.count() > 0 || t != null);
            result = result || !exceptFirst && t != null && t.containsUndefined(false);
            exceptFirst = false;
          }
      }

    return result;
  }

}

/* end of file */
