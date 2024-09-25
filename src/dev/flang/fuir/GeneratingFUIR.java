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
 * Source of class FUIR
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir;

import java.nio.ByteBuffer;

import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import dev.flang.air.FeatureAndActuals;

import dev.flang.ast.AbstractBlock;
import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Constant;
import dev.flang.ast.Context;
import dev.flang.ast.Expr;
import dev.flang.ast.FeatureName;
import dev.flang.ast.InlineArray;
import dev.flang.ast.ResolvedNormalType;
import dev.flang.ast.Types;

import dev.flang.fe.FrontEnd;
import dev.flang.fe.LibraryFeature;
import dev.flang.fe.LibraryModule;

import dev.flang.mir.MIR;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.IntArray;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;
import dev.flang.util.StringHelpers;
import dev.flang.util.YesNo;


/**
 * An implementation of FUIR that generates clazzes on demand from module files.
 * This is used to run DFA for monomorphization.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class GeneratingFUIR extends FUIR
{


  /*-----------------------------  classes  -----------------------------*/


  class Clazz
  {
    final int _outer;
    final int _id;
    final int _gix;
    final LibraryFeature _feature;
    final AbstractType _type;

    SpecialClazzes _s = SpecialClazzes.c_NOT_FOUND;
    boolean _needsCode;
    int _code; // site of the code block of this clazz
    int _outerRef = NO_CLAZZ;  // NYI: not initialized yet!
    boolean _isBoxed = false;  // NYI: not initialized yet!

    IntArray _inner = EMPTY_INT_ARRAY;

  /**
   * Actual inner clazzes when calling a dynamically bound feature on this.
   *
   * This maps a feature to a Clazz. Only for fields of open generic types, this
   * maps a feature to a Clazz[] that contains the actual fields.  The array
   * might be empty.
   */
  final Map<FeatureAndActuals, Object> _innerFromAir = new TreeMap<>();



    YesNo _isUnitType = YesNo.dontKnow;

    boolean _closed = false;

    Clazz(int outer,
          int id,
          int gix)
    {
      this(outer, id, gix, null);
    }
    Clazz(int outer,
          int id,
          int gix,
          AbstractType type)
    {
      _outer = outer;
      _id = id;
      _gix = gix;
      _needsCode = false;
      _code = NO_SITE;
      var m = _fe.module(_gix);
      var f = gix - m._globalBase;
      _feature = (LibraryFeature) m.libraryFeature(f);
      if (type == null)
        {
          type = _feature.thisType();
        }
      _type = type;
    }


    void addInner(int i)
    {
      if (PRECONDITIONS) require
        (!_closed);

      if (_inner == EMPTY_INT_ARRAY)
        {
          _inner = new IntArray();
        }
      _inner.add(i);
    }


    @Override
    public boolean equals(Object other)
    {
      return _gix == ((Clazz) other)._gix;  // NYI: outer and type parameters!
    }
    @Override
    public int hashCode()
    {
      return _gix;  // NYI: outer and type parameters!
    }


    boolean isUnitType()
    {
      _closed = true;

      if (_isUnitType != YesNo.dontKnow)
        {
          return _isUnitType == YesNo.yes;
        }

      // Tricky: To avoid endless recursion, we set _isUnitType to No. In case we
      // have a recursive type, isUnitType() will return false, so recursion will
      // stop and the result for the recursive type will be false.
      //
      // Object layout will later report an error for this case. (NYI: check this with a test!)
      var res = YesNo.no;
      _isUnitType = res;

      if (!_isBoxed &&
          !_feature.isThisRef() &&
          !_feature.isBuiltInPrimitive() &&
          !clazzIsVoidType(_id) &&
          !clazzIsChoice(_id))
        {
          res = YesNo.no;
          for(var ix = 0; ix < _inner.size(); ix++)
            {
              var i = _inner.get(ix);
              if (clazzKind(i) == FeatureKind.Field)
                {
                  var rc = clazzResultClazz(i);
                  res = clazzIsUnitType(rc) ? res : YesNo.no;
                }
            }
        }
      _isUnitType = res;
      return res == YesNo.yes;
    }


    int resultField()
    {
      var result = NO_CLAZZ;
      var rf = _feature.resultField();
      if (rf != null)
        {
          result = lookup(rf, _feature);
        }
      return result;
    }


    /**
     * Lookup the code to call the feature f from this clazz without type
     * parameters using dynamic binding if needed.
     *
     * This is not intended for use at runtime, but during analysis of static
     * types or to fill the virtual call table.
     *
     * @param f the feature that is called
     *
     * @return the inner clazz of the target in the call.
     *
    int lookup(AbstractFeature f)
    {
      if (PRECONDITIONS) require
        (f != null,
         clazzIsVoidType(_id));

      return lookup(f, _clazzes.isUsedAt(f));
    }
     */


    /**
     * Lookup the code to call the feature f from this clazz without type
     * parameters using dynamic binding if needed.
     *
     * This is not intended for use at runtime, but during analysis of static
     * types or to fill the virtual call table.
     *
     * @param f the feature that is called
     *
     * @param p if this lookup would result in the returned feature to be called,
     * p gives the position in the source code that causes this call.  p must be
     * null if the lookup does not cause a call, but it just done to determine
     * the type.
     *
     * @return the inner clazz of the target in the call.
     */
    int lookup(AbstractFeature f,
               HasSourcePosition p)
    {
      if (PRECONDITIONS) require
        (f != null,
         clazzIsVoidType(_id));

      return lookup(new dev.flang.air.FeatureAndActuals(f, dev.flang.ast.AbstractCall.NO_GENERICS), -1, p, false);
    }


    /**
     * Lookup the code to call given feature with actual type parameters, using
     * the position returned by _clazzes.isUsedAt(fa._f) as the call position.
     *
     * This is not intended for use at runtime, but during analysis of static
     * types or to fill the virtual call table.
     *
     * @param fa the feature and actual types that is called
     *
     * @return the inner clazz of the target in the call.
     *
    int lookup(dev.flang.air.FeatureAndActuals fa)
    {
      return lookup(fa, _clazzes.isUsedAt(fa._f));
    }
     */


    /**
     * Lookup the code to call given feature with actual type parameters, using
     * the given call position or null if not a call.
     *
     * This is not intended for use at runtime, but during analysis of static
     * types or to fill the virtual call table.
     *
     * @param fa the feature and actual types that is called
     *
     * @param p if this lookup would result in the returned feature to be called,
     * p gives the position in the source code that causes this call.  p must be
     * null if the lookup does not cause a call, but it just done to determine
     * the type.
     *
     * @return the inner clazz of the target in the call.
     */
    public int lookup(dev.flang.air.FeatureAndActuals fa, HasSourcePosition p)
    {
      return lookup(fa, -1, p, false);
    }


    /**
     * Lookup the code to call the feature f from this clazz using dynamic binding
     * if needed.
     *
     * This is not intended for use at runtime, but during analysis of static
     * types or to fill the virtual call table.
     *
     * @param fa the feature and actual generics that is called
     *
     * @param select in case f is a field of open generic type, this selects the
     * actual field.  -1 otherwise.
     *
     * @param p if this lookup would result in the returned feature to be called,
     * p gives the position in the source code that causes this call.  p must be
     * null if the lookup does not cause a call, but it just done to determine
     * the type.
     *
     * @param isInheritanceCall true iff this is a call in an inheritance clause.  In
     * this case, the result clazz will not be marked as instantiated since the
     * call will work on the instance of the inheriting clazz.
     *
     * @return the inner clazz of the target in the call.
     */
    int lookup(dev.flang.air.FeatureAndActuals fa,
               int select,
               HasSourcePosition p,
               boolean isInheritanceCall)
    {
      if (PRECONDITIONS) require
        (fa != null,
         !fa._f.isUniverse(),
         !clazzIsVoidType(_id));

      Clazz innerClazz = null;
      Clazz[] innerClazzes = null;
      var iCs = _innerFromAir.get(fa);
      if (select < 0)
        {
          if (CHECKS) check
            (Errors.any() || iCs == null || iCs instanceof Clazz);

          innerClazz =
            iCs == null              ? null :
            iCs instanceof Clazz iCC ? iCC
                                     : error();
        }
      else
        {
          if (CHECKS) check
            (Errors.any() || iCs == null || iCs instanceof Clazz[]);
          if (iCs == null || !(iCs instanceof Clazz[] iCA))
            {
              // NYI: innerClazzes = new Clazz[replaceOpenCount(fa._f)];
              _innerFromAir.put(fa, innerClazzes);
            }
          else
            {
              innerClazzes = iCA;
            }
          if (CHECKS) check
            (Errors.any() || select < innerClazzes.length);
          innerClazz = select < innerClazzes.length ? innerClazzes[select] : error();
        }
      if (innerClazz == null)
        {
          AbstractType t = null;
          var f = fa._f;
          if (f.isTypeParameter())
            { // type parameters do not get inherited, but replaced by the actual
              // type given in the inherits call:
              t = f.selfType();   // e.g., `(Types.get T).T`
              if (CHECKS)
                check(Errors.any() || fa._tp.isEmpty());  // there should not be an actual type parameters to a type parameter
            }
          else
            {
              var af = f; // NYI findRedefinition(f);
              /*
              if (CHECKS) check
                (Errors.any() || af != null || isEffectivelyAbstract(f));
              */
              if (f != Types.f_ERROR && (af != null/* NYI:  || !isEffectivelyAbstract(f)*/))
                {
                  var aaf = af != null ? af : f;
                  t = aaf.selfType().applyTypePars(aaf, fa._tp);
                }
            }
          if (t == null)
            {
              innerClazz = error();
            }
          else
            {
              t = _type.actualType(t, Context.NONE);  // e.g., `(Types.get (array f64)).T` -> `array f64`

/*
  We have the following possibilities when calling a feature `f` declared in do `on`
  actual outer clazz `oa`:

  inheritsCall: called normally or as a direct parent

     # f called normally
     r := oa.f

     # f called as parent
     g : oa.f is

  function/constructor: f may be a constructor or a function.

     on is
       # function f
       f result_type is
         stmnts

     on is
       # constructor f
       f is
         stmnts

  declared for value / declared for ref: `on` may be a `ref` instance

     # f declared for value
     on is
       f ... is
         stmnts
     # f declared for ref
     on ref is
       f ... is
         stmnts

  called on value / ref / boxed: `oa` is a value instance, a ref instance, a boxed value instance

     # f called on value:
     oa : on is ...
     t := oa
     r := t.f

     # f called on ref:
     oa ref : on is ...
     t := oa
     r := t.f

     # f called on boxed:
     oa : on is ...
     t ref oa := oa
     r := t.f

  So we have the following combinations

  * normal (non-inherits) call
    * function
      * declared for value
        * called on value
          - oa.f
        * called on ref
          - oa.f
        * called on boxed
          - unbox(oa).f   -- target is uncopied value type.
      * declared for ref
        * called on value
          - oa.f
        * called on ref
          - oa.f
        * called on boxed
          - unbox(oa).f   -- target is uncopied value type.
    * constructor
      * declared for value
        * called on value
          - oa.f
        * called on ref
          - (ref on).f
        * called on boxed
          - *** error ***
      * declared for ref
        * called on value
          - oa.f    result type is oa.f, incompatible to on.f
        * called on ref
          - oa.f    result type is on.f?
        * called on boxed
          - oa.f    result type is on.f?

  * inherits call
    * function -- not allowed
    * constructor
      - same as for normal (non-inherits) call

 */

              var outerUnboxed = _isBoxed && !f.isConstructor() ? asValue() : this;
              // innerClazz = _clazzes.create(t, select, outerUnboxed);
              var innerClazzId = newClazz(outerUnboxed._id, ((LibraryFeature)t.feature()).globalIndex());
              innerClazz = _clazzes.get(innerClazzId);
              if (select < 0)
                {
                  _innerFromAir.put(fa, innerClazz);
                  if (outerUnboxed != this)
                    {
                      outerUnboxed._innerFromAir.put(fa, innerClazz);
                    }
                }
              else
                {
                  // NYI: innerClazzes[select] = innerClazz;
                }
            }
        }
      if (p != null && !isInheritanceCall && innerClazz._type != Types.t_UNDEFINED)
        {
          //innerClazz.called(p);
          //innerClazz.instantiated(p);
        }

      /*
      if (POSTCONDITIONS) ensure
        (Errors.any() || fa._f.isTypeParameter() || findRedefinition(fa._f) == null || innerClazz._type != Types.t_ERROR,
         innerClazz != null);
      */
      return innerClazz._id;
    }

    Clazz asValue()
    {
      if (_isBoxed) throw new Error("Clazz.asValue");
      if (_feature.isThisRef()) throw new Error("Clazz.asValue2");
      return this;
    }



  /**
   * In given type t, replace occurrences of 'X.this.type' by the actual type
   * from this Clazz.
   *
   * @param t a type
   */
  AbstractType replaceThisType(AbstractType t)
  {
    t = replaceThisTypeForTypeFeature(t);
    if (t.isThisType())
      {
        t = findOuter(t.feature(), t.feature())._type;
      }
    return t.applyToGenericsAndOuter(g -> replaceThisType(g));
  }


  /**
   * Special handling for features whose outer features are type features: Any
   * references to x.this.type have to be replaced by the corresponding
   * original. See example from #1260:
   *
   *   t is
   *     h(B type) is
   *     i : h i is
   *     x := i.type
   *
   * Here, in the inherits call to `h i`, the type parameter is
   * `t.this.type.i`. So in the corresponding type feature has two
   *
   *   t.type.h.type t.i t.this.type.i
   *
   * the second type parameter for `B` has to get it's `this.type` types
   * replaced by the actual types given in the first type parameter
   */
  AbstractType replaceThisTypeForTypeFeature(AbstractType t)
  {
    if (_feature.isTypeFeature())
      {
        t = _type.generics().get(0).actualType(t, Context.NONE);
        var g = t.cotypeActualGenerics();
        var o = t.outer();
        if (o != null)
          {
            o = replaceThisTypeForTypeFeature(o);
          }
        t = ResolvedNormalType.create(t, g, g, o, true);
      }
    return t;
  }



  /**
   * Hand down a list of types along a given inheritance chain.
   *
   * @param tl the original list of types to be handed down
   *
   * @param inh the inheritance chain from the parent down to the child
   *
   * @return a new list of types as they are appear after inheritance. The
   * length might be different due to open type parameters being replaced by a
   * list of types.
   */
  public List<AbstractType> handDownThroughInheritsCalls(List<AbstractType> tl, List<AbstractCall> inh)
  {
    for (AbstractCall c : inh)
      {
        var f = c.calledFeature();
        var actualTypes = c.actualTypeParameters();
        tl = tl.flatMap(t -> t.isOpenGeneric()
                             ? t.genericArgument().replaceOpen(actualTypes)
                             : new List<>(t.applyTypePars(f, actualTypes)));
      }
    return tl;
  }


  /**
   * Helper for `handDown`: Change type `t`'s type parameters along the
   * inheritance chain `inh`.
   *
   * ex: in this code
   *
   *    a(T type) is
   *      x T => ...
   *    b(U type) : a Sequence U  is
   *    c(V type) : b option V is
   *
   * the result type `T` of `x` if used within `c` must be handed down via the inheritance chain
   *
   *    `a Sequence U'
   *    'b option B'
   *
   * so it will be replaced by `Sequence (option V)`.
   *
   * @param t the type to hand down
   *
   * @param select if t is an open generic parameter, this specifies the actual
   * argument to select.
   *
   * @param inh the inheritance call chain
   *
   * @return the type `t` as seen after inheritance
   */
  AbstractType handDownThroughInheritsCalls(AbstractType t, int select, List<AbstractCall> inh)
  {
    if (PRECONDITIONS) require
      (t != null,
       Errors.any() || !t.isOpenGeneric() || (select >= 0),
       inh != null);

    for (AbstractCall c : inh)
      {
        t = t.applyTypeParsLocally(c.calledFeature(),
                                   c.actualTypeParameters(), select);
      }
    return t;
  }


  /**
   * Hand down the given type along the given inheritance chain and along all
   * inheritance chains of outer clazzes such that it has the actual type
   * parameters in this clazz.
   *
   * @param t the original type
   *
   * @param select in case t is an open generic, the variant of the actual type
   * that is to be chosen.  -1 otherwise.
   *
   * @param inh the inheritance change that brought is here. This is usually an
   * empty list, only in case this is used in a (recursively) inlined inherits
   * call, then inh gives the sequence of inherits calls from bottom (child) to
   * top (parent).  E.g., in
   *
   *    sum(T type : numeric, a, b T) is
   *       res := a + b
   *
   *    sum_of_3_and_5 : sum i32 3 5 is
   *
   * the type `T` used in `res := a + b` gets replaced by `i32` when this code
   * is inlined to the constructor of `sum_of_3_and_5` via the inherits call
   * `sum i32 3 5`.
   *
   * @param pos a source code position, used to report errors.
   */
  Clazz handDown(AbstractType t, int select, List<AbstractCall> inh, HasSourcePosition pos)
  {
    if (PRECONDITIONS) require
      (t != null,
       Errors.any() || t != Types.t_ERROR,
       Errors.any() || (t.isOpenGeneric() == (select >= 0)),
       Errors.any() || inh != null,
       pos != null);

    var o = _feature;
    var t1 = inh == null ? t : handDownThroughInheritsCalls(t, select, inh);
    var oc = this;
    while (!o.isUniverse() && o != null && oc != null &&

           /* In case of type features, we can have the following loop

                oc: (((io.#type io).out.#type io.out).default_print_handler).println o: io.Print_Handler.println
                oc: ( (io.#type io).out.#type io.out).default_print_handler          o: io.Print_Handler
                oc:   (io.#type io).out.#type io.out                                 o: io
                oc:    io.#type io                                                   o: universe

              here, stop at (io.#type io).out.#type vs. io:
            */
           !(oc._feature.isTypeFeature() && !o.isTypeFeature())
           )
      {
        var f = oc._feature;
        var inh2 = f.tryFindInheritanceChain(o);
        if (CHECKS) check
          (Errors.any() || inh2 != null);
        if (inh2 != null)
          {
            t1 = handDownThroughInheritsCalls(t1, select, inh2);
            t1 = t1.applyTypeParsLocally(oc._type, select);
            if (inh2.size() > 0)
              {
                o = f;
              }
          }
        t1 = t1.replace_this_type_by_actual_outer(oc._type, Context.NONE);
        oc = oc.getOuter(o, pos);
        o = (LibraryFeature) o.outer();
      }

    var t2 = replaceThisType(t1);
    return type2clazz(t2);
  }


  /**
   * Convenience version of `handDown` with `select` set to `-1`.
   */
  Clazz handDown(AbstractType t, List<AbstractCall> inh, HasSourcePosition pos)
  {
    if (PRECONDITIONS) require
      (t != null,
       Errors.any() || t != Types.t_ERROR,
       pos != null,
       !t.isOpenGeneric());

    return handDown(t, -1, inh, pos);
  }




  /**
   * Determine the clazz of the result of calling this clazz, cache the result.
   *
   * @return the result clazz.
   */
  public Clazz resultClazz()
  {
    dev.flang.util.Debug.umprintln("NYI!");
    return error(); // NYI: _resultClazz;
  }


  /**
   * Find outer clazz of this corresponding to feature `o`.
   *
   * @param o the outer feature whose clazz we are searching for.
   *
   * @param pos a position for error messages.
   *
   * @return the outer clazz of this corresponding feature `o`.
   */
  Clazz findOuter(AbstractFeature o, HasSourcePosition pos)
  {
    /* starting with feature(), follow outer references
     * until we find o.
     */
    var res = this;
    var i = _feature;
    while (i != null && i != o
      && !(i.isThisRef() && i.inheritsFrom(o)) // see #1391 and #1628 for when this can be the case.
    )
      {
        res =  i.hasOuterRef() ? id2clazz(res.lookup(i.outerRef(), pos)).resultClazz()
                               : id2clazz(res._outer);
        i = (LibraryFeature) i.outer();
      }

    if (CHECKS) check
      (Errors.any() || i == o || i != null && i.isThisRef() && i.inheritsFrom(o));

    return i == null ? error() : res;
  }


  /**
   * For a direct parent p of this clazz's feature, find the outer clazz of the
   * parent. E.g., for `i32` that inherits from `num.wrap_around` the result of
   * `getOuter(num.wrap_around)` will be the result clazz of `num`, while
   * `getOuter(i32)` will be `universe`.
   *
   * @param p a feature that is either equal to this or a direct parent of x.
   *
   * @param pos source position for error reporting only.
   */
  Clazz getOuter(AbstractFeature p, HasSourcePosition pos)
  {
    var res =
      p.hasOuterRef()        ? /* we either inherit from p as in
                                *
                                *     x : a.b.c.p is ...
                                *
                                * or x = p.  So the outer of `x` with respect
                                * to `p` is `a.b.c`, which is the result type
                                * of `p`'s outer ref:
                                */
                               id2clazz(lookup(p.outerRef(), pos)).resultClazz() :
      p.isUniverse() ||
      p.outer().isUniverse() ? id2clazz(_universe)
                             : /* a field or choice, so there is no inherits
                                * call that could select a different outer:
                                 */
                               id2clazz(_outer);

    if (CHECKS) check
      (Errors.any() || res != null);

    return res;
  }



    String asString(boolean humanReadable)
    {
      String result;
      var o = _outer;
      String outer = o != NO_CLAZZ && (o != _universe) ? id2clazz(o).asStringWrapped(humanReadable) + "." : "";
      var f = _feature;
      var typeType = f.isTypeFeature();
      if (typeType)
        {
          f = (LibraryFeature) f.typeFeatureOrigin();
        }
      var fn = f.featureName();
      // for a feature that does not define a type itself, the name is not
      // unique due to overloading with different argument counts. So we add
      // the argument count to get a unique name.
      var fname = (humanReadable ? fn.baseNameHuman() : fn.baseName())
        +  (f.definesType() || fn.argCount() == 0 || fn.isInternal()
            ? ""
            : FuzionConstants.INTERNAL_NAME_PREFIX + fn.argCount());

      // NYI: would be good if postFeatures could be identified not be string comparison, but with something like
      // `f.isPostFeature()`. Note that this would need to be saved in .fum file as well!
      //
      if (fname.startsWith(FuzionConstants.POSTCONDITION_FEATURE_PREFIX))
        {
          fname = fname.substring(FuzionConstants.POSTCONDITION_FEATURE_PREFIX.length(),
                                  fname.lastIndexOf("_")) +
            ".postcondition";
        }

      result = outer
        + (_isBoxed ? "ref " : "" )
        + fname;
      if (typeType)
        {
          result = result + ".type";
        }
      /* NYI: generics
      var skip = typeType;
      for (var g : generics())
        {
          if (!skip) // skip first generic 'THIS#TYPE' for types of type features.
            {
              result = result + " " + g.asStringWrapped(humanReadable, context);
            }
          skip = false;
        }
      */
      return result;
    }

    String asStringWrapped(boolean humanReadable)
    {
      return StringHelpers.wrapInParentheses(asString(humanReadable));
    }

  }

    Clazz error()
    {
      if (PRECONDITIONS) require
        (Errors.any());

      return _clazzes.get(clazz(SpecialClazzes.c_void));  // NYI: UNDER DEVELOPMENT: have a dedicated clazz for this?
    }


  /*----------------  methods to convert type to clazz  -----------------*/


  /**
   * clazz
   *
   * @return
   */
  public Clazz type2clazz(AbstractType thiz)
  {
    if (PRECONDITIONS) require
      (Errors.any() || !thiz.dependsOnGenerics(),
       !thiz.isThisType());

    var result = _clazzesForTypes_.get(thiz);
    if (result == null)
      {
        Clazz outerClazz = thiz.outer() != null
          ? outerClazz = type2clazz(thiz.outer())
          : null;

        result = create(thiz, outerClazz);
        _clazzesForTypes_.put(thiz, result);
      }

    if (POSTCONDITIONS) ensure
      (Errors.any() || thiz.isRef() == result._type.isRef());

    return result;
  }


  /*----------------------------  constants  ----------------------------*/


  static final IntArray EMPTY_INT_ARRAY = new IntArray() {
      @Override
      public void add(int i)
      {
        throw new Error("Cannot add to EMPTY_INT_ARRAY");
      }
    };


  /*----------------------------  variables  ----------------------------*/


  private final FrontEnd _fe;

  private final HashMap<Clazz, Clazz> _clazzesHM;


  /**
   * For each site, this gives the clazz id of the clazz that contains the code at that site.
   */
  private final IntArray _siteClazzes;


  private final LibraryModule _mainModule;


  private final int _mainClazz;
  private final int _universe;


  private final List<Clazz> _clazzes;


  private final int[] _specialClazzes;

  private final Map<AbstractType, Clazz> _clazzesForTypes_ = new TreeMap<>();

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create FUIR from given Clazz instance.
   */
  public GeneratingFUIR(FrontEnd fe, MIR mir)
  {
    _fe = fe;
    _clazzesHM = new HashMap<Clazz, Clazz>();
    _siteClazzes = new IntArray();
    _mainModule = fe.mainModule();
    _clazzes = new List<>();
    _universe  = newClazz(NO_CLAZZ, ((LibraryFeature)mir.universe()).globalIndex());
    doesNeedCode(_universe);
    _mainClazz = newClazz((LibraryFeature) mir.main());
    doesNeedCode(_mainClazz);
    _specialClazzes = new int[SpecialClazzes.values().length];
    Arrays.fill(_specialClazzes, NO_CLAZZ);
  }



  /**
   * Clone this FUIR such that modifications can be made by optimizers.  A heir
   * of FUIR can use this to redefine methods.
   *
   * @param original the original FUIR instance that we are cloning.
   */
  public GeneratingFUIR(GeneratingFUIR original)
  {
    super(original);
    _fe = original._fe;
    _clazzesHM = original._clazzesHM;
    _siteClazzes = original._siteClazzes;
    _mainModule = original._mainModule;
    _mainClazz = original._mainClazz;
    _universe = original._universe;
    _clazzes = original._clazzes;
    _specialClazzes = original._specialClazzes;
  }


  /*-----------------------------  methods  -----------------------------*/



  private int newClazz(AbstractFeature f)
  {
    if (PRECONDITIONS) require
      (f != null,
       f.typeArguments().size() == 0);

    var of = f.outer();
    return of == null ? _universe
                      : newClazz(newClazz(of), f.globalIndex());
  }

  private int newClazz(int outer, int gix)
  {
    int result;
    var cl = new Clazz(outer, CLAZZ_BASE + _clazzes.size(), gix);
    var existing = _clazzesHM.get(cl);
    if (existing != null)
      {
        result = existing._id;
      }
    else
      {
        result = cl._id;
        _clazzes.add(cl);
        _clazzesHM.put(cl, cl);
        if (outer != NO_CLAZZ)
          {
            id2clazz(outer).addInner(result);
          }

        // NYI: OPTIMIZATION: Avoid creating all feature qualified names!
        var s = switch (cl._feature.qualifiedName())
          {
          case "i8"                -> SpecialClazzes.c_i8          ;
          case "i16"               -> SpecialClazzes.c_i16         ;
          case "i32"               -> SpecialClazzes.c_i32         ;
          case "i64"               -> SpecialClazzes.c_i64         ;
          case "u8"                -> SpecialClazzes.c_u8          ;
          case "u16"               -> SpecialClazzes.c_u16         ;
          case "u32"               -> SpecialClazzes.c_u32         ;
          case "u64"               -> SpecialClazzes.c_u64         ;
          case "f32"               -> SpecialClazzes.c_f32         ;
          case "f64"               -> SpecialClazzes.c_f64         ;
          case "bool"              -> SpecialClazzes.c_bool        ;
          case "TRUE"              -> SpecialClazzes.c_TRUE        ;
          case "FALSE"             -> SpecialClazzes.c_FALSE       ;
          case "Const_String"      -> SpecialClazzes.c_Const_String;
          case "String"            -> SpecialClazzes.c_String      ;
          case "fuzion.sys.Pointer"-> SpecialClazzes.c_sys_ptr     ; // NYI: does not work, must handle outer correctly
          case "unit"              -> SpecialClazzes.c_unit        ;
          default                  -> SpecialClazzes.c_NOT_FOUND   ;
          };
        if (s != SpecialClazzes.c_NOT_FOUND)
          {
            _specialClazzes[s.ordinal()] = result;
          }
        cl._s = s;
        System.out.println("NEW CLAZZ: "+cl.asString(true));
      }
    return result;
  }





  /**
   * Create a clazz for the given actual type and the given outer clazz.
   * Clazzes created are recorded to be handed by findAllClasses.
   *
   * @param actualType the type of the clazz, must be free from generics
   *
   * @param outer the runtime clazz of the outer feature of
   * actualType.feature.
   *
   * @return the existing or newly created Clazz that represents actualType
   * within outer.
   */
  Clazz create(AbstractType actualType, Clazz outer)
  {
    return create(actualType, -1, outer);
  }


  /**
   * Create a clazz for the given actual type and the given outer clazz.
   * Clazzes created are recorded to be handed by findAllClasses.
   *
   * @param actualType the type of the clazz, must be free from generics
   *
   * @param select in case actualType is a field with open generic result, this
   * chooses the actual field from outer's actual generics. -1 otherwise.
   *
   * @param outer the runtime clazz of the outer feature of
   * actualType.feature.
   *
   * @return the existing or newly created Clazz that represents actualType
   * within outer. undefined.getIfCreated() in case the created clazz cannot
   * exist (due to precondition `T : x` where type parameter `T` is not
   * constraintAssignableFrom `x`.
   */
  Clazz create(AbstractType actualType, int select, Clazz outer)
  {
    if (PRECONDITIONS) require
      (Errors.any() || !actualType.dependsOnGenericsExceptTHIS_TYPE(),
       Errors.any() || !actualType.containsThisType(),
       Errors.any() || outer == null || outer._type != Types.t_UNDEFINED,
       outer != null || actualType.feature().outer() == null,
       Errors.any() || actualType == Types.t_ERROR || outer == null ||
        outer._feature.inheritsFrom(actualType.feature().outer()) || (outer._feature.isTypeFeature() /* NYI: REMOVE: workaround for #3160 */));

    return id2clazz(newClazz(actualType.feature()));
    /*

    Clazz o = outer;
    var ao = actualType.feature().outer();
    while (o != null)
      {
        if (actualType.isRef() && ao != null && ao.inheritsFrom(o.feature()) && !outer.isRef())
          {
            outer = o;  // short-circuit outer relation if suitable outer was found
          }

        if (o._type.compareTo(actualType) == 0 &&
            // example where the following logic is relevant:
            // `((Unary i32 i32).compose i32).#fun`
            // here `compose i32` is not a constructor but a normal routine.
            // `compose i32` does not define a type. Thus it will not lead
            // to a recursive value type.
            actualType.feature().definesType() &&
            actualType != Types.t_ERROR &&
            // a recursive outer-relation

            // This is a little ugly: we do not want outer to be a value
            // type in the source code (see tests/inheritance_negative for
            // reasons why), but we are fine if outer is an 'artificial'
            // value type that is created by Clazz.asValue(), since these
            // will never be instantiated at runtime but are here only for
            // the convenience of the backend.
            //
            // So instead of testing !o.isRef() we use
            // !o._type.feature().isThisRef().
            !o._type.feature().isThisRef() &&
            !o._type.feature().isIntrinsic())
          {  // but a recursive chain of value types is not permitted

            // NYI: recursive chain of value types should be detected during
            // types checking phase!
            StringBuilder chain = new StringBuilder();
            chain.append("1: "+actualType+" at "+actualType.declarationPos().show()+"\n");
            int i = 2;
            Clazz c = outer;
            while (c._type.compareTo(actualType) != 0)
              {
                chain.append(""+i+": "+c._type+" at "+c._type.declarationPos().show()+"\n");
                c = c._outer;
                i++;
              }
            chain.append(""+i+": "+c._type+" at "+c._type.declarationPos().show()+"\n");
            Errors.error(actualType.declarationPos(),
                         "Recursive value type is not allowed",
                         "Value type " + actualType + " equals type of outer feature.\n"+
                         "The chain of outer types that lead to this recursion is:\n"+
                         chain + "\n" +
                         "To solve this, you could add a 'ref' after the arguments list at "+o._type.feature().pos().show());
          }
        o = o._outer;
      }

    // NYI: We currently create new clazzes for every different outer
    // context. This gives us plenty of opportunity to specialize the code,
    // but it might be overkill in some cases. We might rethink this and,
    // e.g. treat clazzes of inherited features with a reference outer clazz
    // the same.

    Clazz result = null, newcl = null;

    // find preconditions `T : x` that prevent creation of instances of this clazz.
    //
    // NYI: UNDER DEVELOPMENT: This is very manual code to extract this info
    // from the code created for the preFeature. This is done automatically by
    // DFA, so this code will disappear once DFA and AIR phases are merged.
    //
    var pF = actualType.feature().preFeature();
    if (pF != null)
      {
        var pFcode = pF.code();
        var ass0 = pFcode instanceof AbstractBlock b ? b._expressions.get(0) : pFcode;
        if (ass0 instanceof AbstractAssign ass)
          {
            var e0 = ass._value;
            var e1 = e0 instanceof AbstractBlock ab ? ab._expressions.get(0) :
                     e0 instanceof AbstractCall ac  ? ac.target() :
                     e0;
            if (e1 instanceof AbstractBlock ab &&
                ab._expressions.get(0) instanceof AbstractMatch m &&
                m.subject() instanceof AbstractCall sc &&
                sc.calledFeature() == Types.resolved.f_Type_infix_colon)
              {
                var pFc = outer.lookup(pF);
                if (clazzesToBeVisited.contains(pFc))
                  {
                    clazzesToBeVisited.remove(pFc);
                    pFc.findAllClasses();
                  }
                var args = pFc.actualClazzes(sc, null);
                if (CHECKS)
                  check(args[0].feature() == Types.resolved.f_Type_infix_colon_true  ||
                        args[0].feature() == Types.resolved.f_Type_infix_colon_false   );
                if (args[0].feature() == Types.resolved.f_Type_infix_colon_false)
                  {
                    result = undefined.get();
                  }
              }
          }
      }

    if (result == null)
      {
        newcl = new Clazz(actualType, select, outer, this);
        result = newcl;
        if (actualType != Types.t_UNDEFINED)
          {
            result = intern(newcl);
          }
      }

    if (result == newcl)
      {
        if (CHECKS) check
          (Errors.any() || result.feature().state().atLeast(State.RESOLVED));
        if (result.feature().state().atLeast(State.RESOLVED))
          {
            clazzesToBeVisited.add(result);
          }
        result.registerAsHeir();
        if (_options_.verbose(5))
          {
            _options_.verbosePrintln(5, "GLOBAL CLAZZ: " + result);
            if (_options_.verbose(10))
              {
                Thread.dumpStack();
              }
          }
        result.dependencies();
      }

    if (POSTCONDITIONS) ensure
      (Errors.any() || actualType == Types.t_ADDRESS || actualType.compareToIgnoreOuter(result._type) == 0 || true,
       outer == result._outer || true // NYI: Check why this sometimes does not hold
    );

    return result;
*/
  }



  private Clazz id2clazz(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    return _clazzes.get(cl - CLAZZ_BASE);
  }


  private static List<AbstractCall> NO_INH = new List<>();
  static { NO_INH.freeze(); }


  /**
   * Determine the result clazz of an Expr.
   *
   * @param inh the inheritance chain that brought the code here (in case it is
   * an inlined inherits call).
   */
  private Clazz clazz(Expr e, Clazz outerClazz, List<AbstractCall> inh)
  {
    Clazz result;
    if (e instanceof AbstractBlock b)
      {
        Expr resExpr = b.resultExpression();
        result = resExpr != null ? clazz(resExpr, outerClazz, inh)
                                 : id2clazz(clazz(SpecialClazzes.c_unit));
      }
    /*
    else if (e instanceof Box b)
      {
        result = outerClazz.handDown(b.type(), inh, e);
      }

    else if (e instanceof AbstractCall c)
      {
        var tclazz = clazz(c.target(), outerClazz, inh);
        if (!tclazz.isVoidOrUndefined())
          {
            var at = outerClazz.handDownThroughInheritsCalls(c.actualTypeParameters(), inh);
            var inner = tclazz.lookup(new FeatureAndActuals(c.calledFeature(),
                                                            outerClazz.actualGenerics(at)),
                                      c.select(),
                                      c,
                                      false);
            result = inner.resultClazz();
          }
        else
          {
            result = tclazz;
          }
      }
    else if (e instanceof AbstractCurrent c)
      {
        result = outerClazz;
      }

    else if (e instanceof AbstractMatch m)
      {
        result = outerClazz.handDown(m.type(), inh, e);
      }

    else if (e instanceof Universe)
      {
        result = universe.get();
      }
    */
    else if (e instanceof Constant c)
      {
        result = outerClazz.handDown(c.typeOfConstant(), inh, e);
      }
    /*
    else if (e instanceof Tag tg)
      {
        result = outerClazz.handDown(tg._taggedType, inh, e);
      }

    else if (e instanceof InlineArray ia)
      {
        result = outerClazz.handDown(ia.type(), inh, e);
      }

    else if (e instanceof Env v)
      {
        result = outerClazz.handDown(v.type(), inh, e);
      }
    */
    else
      {
        if (!Errors.any())
          {
            throw new Error("" + e + " "+ e.getClass() + " should no longer exist at runtime");
          }

        result = error();
      }

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /*------------------------  accessing classes  ------------------------*/


  /**
   * The clazz ids form a contiguous range of integers. This method gives the
   * smallest clazz id.  Together with `lastClazz`, this permits iteration.
   *
   * @return a valid clazz id such that for all clazz ids id: result <= id.
   */
  @Override
  public int firstClazz()
  {
    return CLAZZ_BASE;
  }


  /**
   * The clazz ids form a contiguous range of integers. This method gives the
   * largest clazz id.  Together with `firstClazz`, this permits iteration.
   *
   * @return a valid clazz id such that for all clazz ids id: result >= id.
   */
  @Override
  public int lastClazz()
  {
    return CLAZZ_BASE + _clazzes.size() - 1;
  }


  /**
   * id of the main clazz.
   *
   * @return a valid clazz id
   */
  @Override
  public int mainClazzId()
  {
    return _mainClazz;
  }


  /**
   * Return the kind of this clazz ( Routine, Field, Intrinsic, Abstract, ...)
   */
  @Override
  public FeatureKind clazzKind(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    var c = id2clazz(cl);
    var m = _fe.module(c._gix);
    return switch (m.featureKindEnum(c._gix - m._globalBase))
      {
      case Routine           -> FeatureKind.Routine;
      case Field             -> FeatureKind.Field;
      case TypeParameter,
           OpenTypeParameter -> FeatureKind.Intrinsic; // NYI: strange
      case Intrinsic         -> FeatureKind.Intrinsic;
      case Abstract          -> FeatureKind.Abstract;
      case Choice            -> FeatureKind.Choice;
      case Native            -> FeatureKind.Native;
      };
  }


  /**
   * Return the base name of this clazz, i.e., the name excluding the outer
   * clazz' name and excluding the actual type parameters
   *
   * @return String like `"Set"` if `cl` corresponds to `container.Set u32`.
   */
  @Override
  public String clazzBaseName(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    var c = id2clazz(cl);
    var m = _fe.module(c._gix);
    var ix = c._gix - m._globalBase;
    var bytes = m.featureName(ix);
    var ac = m.featureArgCount(ix);
    var id = m.featureId(ix);
    String result;
    if (bytes.length == 0)
      {
        result = FeatureName.get(ix, ac, id).baseName();
      }
    else
      {
        result = new String(bytes, StandardCharsets.UTF_8);
      }
    return result;
  }



  /**
   * Get the clazz of the result of calling a clazz
   *
   * @param cl a clazz id, must not be Choice
   *
   * @return clazz id of cl's result
   */
  @Override
  public int clazzResultClazz(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    dev.flang.util.Debug.umprintln("NYI!");
    if (true) return cl;
    throw new Error("NYI");
  }


  /**
   * The original qualified name of the feature this clazz was
   * created from, ignoring any inheritance into new clazzes.
   *
   * @param cl a clazz
   *
   * @return its original name, e.g. 'Array.getel' instead of
   * 'Const_String.getel'
   */
  @Override
  public String clazzOriginalName(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    throw new Error("NYI");
  }


  /**
   * String representation of clazz, for creation of unique type names.
   *
   * @param cl a clazz id.
   */
  @Override
  public String clazzAsString(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    var c = id2clazz(cl);
    return c.asString(false);
  }


  /**
   * human readable String representation of clazz, for stack traces and debugging.
   *
   * @param cl a clazz id.
   */
  @Override
  public String clazzAsStringHuman(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    var c = id2clazz(cl);
    return c.asString(true);
  }


  /**
   * Get a String representation of a given clazz including a list of arguments
   * and the result type. For debugging only, names might be ambiguous.
   *
   * @param cl a clazz id.
   */
  @Override
  public String clazzAsStringWithArgsAndResult(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    throw new Error("NYI");
  }


  /**
   * Get the outer clazz of the given clazz.
   *
   * @param cl a clazz id
   *
   * @return clazz id of cl's outer clazz, -1 if cl is universe or a value-less
   * type.
   */
  @Override
  public int clazzOuterClazz(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    var c = id2clazz(cl);
    return c._outer;
  }


  /*------------------------  accessing fields  ------------------------*/


  /**
   * Number of value fields in clazz `cl`, including argument value fields,
   * inherited fields, artificial fields like outer refs.
   *
   * @param cl a clazz id
   *
   * @return number of value fields in `cl`
   */
  @Override
  public int clazzNumFields(int cl)
  {
    throw new Error("NYI");
  }


  /**
   * Return the field #i in the given clazz
   *
   * @param cl a clazz id
   *
   * @param i the field number
   *
   * @return the clazz id of the field
   */
  @Override
  public int clazzField(int cl, int i)
  {
    throw new Error("NYI");
  }


  /**
   * Is the given field clazz a reference to an outer feature?
   *
   * @param cl a clazz id of kind Field
   *
   * @return true for automatically generated references to outer instance
   */
  @Override
  public boolean clazzIsOuterRef(int cl)
  {
    throw new Error("NYI");
  }


  /**
   * Check if field does not store the value directly, but a pointer to the value.
   *
   * @param fcl a clazz id of the field
   *
   * @return true iff the field is an outer ref field that holds an address of
   * an outer value, false for normal fields our outer ref fields that store the
   * outer ref or value directly.
   */
  @Override
  public boolean clazzFieldIsAdrOfValue(int fcl)
  {
    throw new Error("NYI");
  }


  /**
   * NYI: CLEANUP: Remove? This seems to be used only for naming fields, maybe we could use clazzId2num(field) instead?
   */
  @Override
  public int fieldIndex(int field)
  {
    throw new Error("NYI");
  }


  /*------------------------  accessing choices  -----------------------*/


  /**
   * is the given clazz a choice clazz
   *
   * @param cl a clazz id
   */
  @Override
  public boolean clazzIsChoice(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    var c = id2clazz(cl);
    return switch (c._feature.kind())
      {
      case Choice -> true;
      default -> false;
      };
  }


  /**
   * For a choice type, the number of entries to choose from.
   *
   * @param cl a clazz id
   *
   * @return -1 if cl is not a choice clazz, the number of choice entries
   * otherwise.  May be 0 for the void choice.
   */
  @Override
  public int clazzNumChoices(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    var c = id2clazz(cl);
    return c._feature.choiceGenerics().size();
  }


  /**
   * Return the choice #i in the given choice clazz
   *
   * @param cl a clazz id
   *
   * @param i the choice number
   *
   * @return the clazz id of the choice type, or void clazz if the clazz is
   * never instantiated and hence does not need to be taken care for.
   */
  @Override
  public int clazzChoice(int cl, int i)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    dev.flang.util.Debug.umprintln("NYI!");
    return clazz(SpecialClazzes.c_void);
  }


  /**
   * Is this a choice type with some elements of ref type?
   *
   * @param cl a clazz id
   *
   * @return true iff cl is a choice with at least one ref element
   */
  @Override
  public boolean clazzIsChoiceWithRefs(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    throw new Error("NYI");
  }


  /**
   * Is this a choice type with all elements of ref type?
   *
   * @param cl a clazz id
   *
   * @return true iff cl is a choice with only ref or unit/void elements
   */
  @Override
  public boolean clazzIsChoiceOfOnlyRefs(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    throw new Error("NYI");
  }



  /*------------------------  inheritance  -----------------------*/


  /**
   * Get all heirs of given clazz that are instantiated.
   *
   * @param cl a clazz id
   *
   * @return an array of the clazz id's of all heirs for cl that are
   * instantiated, including cl itself, provided that cl is instantiated.
   */
  @Override
  public int[] clazzInstantiatedHeirs(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    throw new Error("NYI");
  }


  /*-------------------------  routines  -------------------------*/


  /**
   * Get the number of arguments required for a call to this clazz.
   *
   * @param cl clazz id
   *
   * @return number of arguments expected by cl, 0 if none or if clazz cl can
   * not be called (is a choice type)
   */
  @Override
  public int clazzArgCount(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    var c = id2clazz(cl);
    var m = _fe.module(c._gix);
    return m.featureArgCount(c._gix - m._globalBase);   // NYI: open generics?
  }


  /**
   * Get the clazz id of the type of the given argument of clazz cl
   *
   * @param cl clazz id
   *
   * @param arg argument number 0, 1, .. clazzArgCount(cl)-1
   *
   * @return clazz id of the argument or -1 if no such feature exists (the
   * argument is unused).
   */
  @Override
  public int clazzArgClazz(int cl, int arg)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    throw new Error("NYI");
  }


  /**
   * Get the clazz id of the given argument of clazz cl
   *
   * @param cl clazz id
   *
   * @param arg argument number 0, 1, .. clazzArgCount(cl)-1
   *
   * @return clazz id of the argument or -1 if no such argument exists (the
   * argument is unused).
   */
  @Override
  public int clazzArg(int cl, int arg)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    throw new Error("NYI");
  }


  /**
   * Get the id of the result field of a given clazz.
   *
   * @param cl a clazz id
   *
   * @return id of cl's result field or NO_CLAZZ if f has no result field (NYI: or a
   * result field that contains no data)
   */
  @Override
  public int clazzResultField(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    var c = id2clazz(cl);
    return c.resultField();
  }


  /**
   * If a clazz's instance contains an outer ref field, return this field.
   *
   * @param cl a clazz id
   *
   * @return clazz id of cl's outer ref field or -1 if no such field exists.
   */
  @Override
  public int clazzOuterRef(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    var c = id2clazz(cl);
    return c._outerRef;
  }


  /**
   * Get access to the code of a clazz of kind Routine
   *
   * @param cl a clazz id
   *
   * @return a site id referring to cl's code
   */
  @Override
  public int clazzCode(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       clazzNeedsCode(cl));

    var c = id2clazz(cl);
    var result = c._code;
    if (result == NO_SITE)
      {
        var m = _fe.module(c._gix);
        var f = c._gix - m._globalBase;
        var code = m.featureCodePos(f);
        result = addCode(cl, c, m, code);
        c._code = result;
      }
    return result;
  }


  int addCode(int cl, Clazz c, LibraryModule m, int codePos)
  {
    var code = new List<Object>();
    if (!clazzIsVoidType(cl))
      {
        /*
    for (var p: ff.inherits())
      {
        var pf = p.calledFeature();
        var of = pf.outerRef();
        var or = (of == null) ? null : (Clazz) cc._inner.get(new FeatureAndActuals(of, new List<>()));  // NYI: ugly cast
        var needsOuterRef = (or != null && !or.resultClazz().isUnitType());
        toStack(code, p.target(), !needsOuterRef //* dump result if not needed //);
        if (needsOuterRef)
          {
            code.add(ExprKind.Current);
            code.add(or);  // field clazz means assignment to field
          }
        if (CHECKS) check
          (p.actuals().size() == p.calledFeature().valueArguments().size());
        var argFields = cc._parentCallArgFields.get(p.globalIndex());
        for (var i = 0; i < p.actuals().size(); i++)
          {
            var a = p.actuals().get(i);
            toStack(code, a);
            code.add(ExprKind.Current);
            // Field clazz means assign value to that field
            code.add(argFields[i]);
          }
        addCode(cc, code, p.calledFeature());
      }
  */
    toStack(code, c._feature.code());
      }
    var result = addCode(code);
    while (_siteClazzes.size() < _allCode.size())
      {
        _siteClazzes.add(cl);
      }
    // NYI:     recordClazzForSitesOfRecentlyAddedCode(cl);
    /* NYI:
    var result = _nextSite;
    _nextSite += m.codeSize(code) + 1;
    for (var i = result; i < _nextSite; i++)
      {
        _siteClazzes.add(cl);
      }
    */
    System.out.println("Code added for "+c.asString(true)+" "+(_allCode.size() - (result - SITE_BASE)));
    return result;
  }


  /**
   * Does the backend need to generate code for this clazz since it might be
   * called at runtime.  This is true for all features that are called directly
   * or dynamically in a 'normal' call, i.e., not in an inheritance call.
   *
   * An inheritance call is inlined since it works on a different instance, the
   * instance of the heir class.  Consequently, a clazz resulting from an
   * inheritance call does not need code for itself.
   */
  @Override
  public boolean clazzNeedsCode(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    var c = id2clazz(cl);
    return c._needsCode;
  }


  void doesNeedCode(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    var c = id2clazz(cl);
    c._needsCode = true;
  }


  /*-----------------------  constructors  -----------------------*/


  /**
   * Check if the given clazz is a constructor, i.e., a routine returning
   * its instance as a result?
   *
   * @param cl a clazz id
   *
   * @return true if the clazz is a constructor, false otherwise
   */
  @Override
  public boolean isConstructor(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    var c = id2clazz(cl);
    return switch (c._feature.kind())
      {
      case Routine -> c._feature.isConstructor();
      default -> false;
      };
  }


  /**
   * Is the given clazz a ref clazz?
   *
   * @parm cl a constructor clazz id
   *
   * @return true for non-value-type clazzes
   */
  @Override
  public boolean clazzIsRef(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    var c = id2clazz(cl);
    return c._isBoxed || c._feature.isThisRef();
  }


  /**
   * Is the given clazz a ref clazz that contains a boxed value type?
   *
   * @return true for boxed value-type clazz
   */
  @Override
  public boolean clazzIsBoxed(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    var c = id2clazz(cl);
    return c._isBoxed;
  }


  /**
   * For a reference clazz, obtain the corresponding value clazz.
   *
   * @param cl a clazz id
   *
   * @return clazz id of corresponding value clazz.
   */
  @Override
  public int clazzAsValue(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    throw new Error("NYI");
  }


  /*--------------------------  cotypes  -------------------------*/


  /**
   * For a clazz that represents a Fuzion type such as 'i32.type', return the
   * corresponding name of the type such as 'i32'.  This value is returned by
   * intrinsic `Type.name`.
   *
   * @param cl a clazz id of a cotype
   *
   * @return the name of the type represented by instances of cl, using UTF8 encoding.
   */
  @Override
  public byte[] clazzTypeName(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    throw new Error("NYI");
  }


  /**
   * If cl is a type parameter, return the type parameter's actual type.
   *
   * @param cl a clazz id
   *
   * @return if cl is a type parameter, clazz id of cl's actual type or -1 if cl
   * is not a type parameter.
   */
  @Override
  public int clazzTypeParameterActualType(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    throw new Error("NYI");
  }


  /*----------------------  special clazzes  ---------------------*/


  /**
   * Obtain SpecialClazz from a given clazz.
   *
   * @param cl a clazz id
   *
   * @return the corresponding SpecialClazz or c_NOT_FOUND if cl is not a
   * special clazz.
   */
  @Override
  public SpecialClazzes getSpecialClazz(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    var c = id2clazz(cl);
    return c._s;
  }


  /**
   * Check if a clazz is the special clazz c.
   *
   * @param cl a clazz id
   *
   * @param c one of the constants SpecialClazzes.c_i8,...
   *
   * @return true iff cl is the specified special clazz c
   */
  @Override
  public boolean clazzIs(int cl, SpecialClazzes c)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    return id2clazz(cl)._s == c;
  }


  /**
   * Get the id of the given special clazz.
   *
   * @param s the special clazz we are looking for
   */
  @Override
  public int clazz(SpecialClazzes s)
  {
    if (PRECONDITIONS) require
      (s != SpecialClazzes.c_NOT_FOUND);

    var result = _specialClazzes[s.ordinal()];
    if (result == NO_CLAZZ)
      {
        if (s == SpecialClazzes.c_universe)
          {
            result = _universe;
          }
        else
          {
            var o = clazz(s._outer);
            var oc = _clazzes.get(o - CLAZZ_BASE);
            var of = oc._feature;
            var f = of.get(of._libModule, s._name, s._argCount);
            result = newClazz(o, f.globalIndex());
          }
      }
    return result;
  }


  /**
   * Get the id of clazz Any.
   *
   * @return clazz id of clazz Any
   */
  @Override
  public int clazzAny()
  {
    throw new Error("NYI");
  }


  /**
   * Get the id of clazz universe.
   *
   * @return clazz id of clazz universe
   */
  @Override
  public int clazzUniverse()
  {
    return _universe;
  }


  /**
   * Get the id of clazz Const_String
   *
   * @return the id of Const_String or -1 if that clazz was not created.
   */
  @Override
  public int clazz_Const_String()
  {
    dev.flang.util.Debug.umprintln("NYI!");
    if (true) return -1;
    throw new Error("NYI");
  }


  /**
   * Get the id of clazz Const_String.utf8_data
   *
   * @return the id of Const_String.utf8_data or -1 if that clazz was not created.
   */
  @Override
  public int clazz_Const_String_utf8_data()
  {
    dev.flang.util.Debug.umprintln("NYI!");
    if (true) return -1;
    throw new Error("NYI");
  }


  /**
   * Get the id of clazz Const_String.array
   *
   * @return the id of Const_String.array or -1 if that clazz was not created.
   */
  @Override
  public int clazz_array_u8()
  {
    dev.flang.util.Debug.umprintln("NYI!");
    if (true) return -1;
    throw new Error("NYI");
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>
   *
   * @return the id of fuzion.sys.array<u8> or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8()
  {
    dev.flang.util.Debug.umprintln("NYI!");
    if (true) return -1;
    throw new Error("NYI");
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>.data
   *
   * @return the id of fuzion.sys.array<u8>.data or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8_data()
  {
    dev.flang.util.Debug.umprintln("NYI!");
    if (true) return -1;
    throw new Error("NYI");
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>.length
   *
   * @return the id of fuzion.sys.array<u8>.length or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8_length()
  {
    dev.flang.util.Debug.umprintln("NYI!");
    if (true) return -1;
    throw new Error("NYI");
  }


  /**
   * Get the id of clazz fuzion.java.Java_Object
   *
   * @return the id of fuzion.java.Java_Object or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionJavaObject()
  {
    dev.flang.util.Debug.umprintln("NYI!");
    if (true) return -1;
    throw new Error("NYI");
  }


  /**
   * Get the id of clazz fuzion.java.Java_Object.Java_Ref
   *
   * @return the id of fuzion.java.Java_Object.Java_Ref or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionJavaObject_Ref()
  {
    dev.flang.util.Debug.umprintln("NYI!");
    if (true) return -1;
    throw new Error("NYI");
  }


  /**
   * Get the id of clazz error
   *
   * @return the id of error or -1 if that clazz was not created.
   */
  @Override
  public int clazz_error()
  {
    dev.flang.util.Debug.umprintln("NYI!");
    if (true) return -1;
    throw new Error("NYI");
  }


  /**
   * On `cl` lookup field `Java_Ref`
   *
   * @param cl Java_Object or inheriting from Java_Object
   *
   */
  @Override
  public int lookupJavaRef(int cl)
  {
    throw new Error("NYI");
  }


  /**
   * For a clazz that is an heir of 'Function', find the corresponding inner
   * clazz for 'call'.  This is used for code generation of intrinsic
   * 'abortable' that has to create code to call 'call'.
   *
   * @param cl index of a clazz that is an heir of 'Function'.
   *
   * @return the index of the requested `Function.call` feature's clazz.
   */
  @Override
  public int lookupCall(int cl)
  {
    throw new Error("NYI");
  }


  /**
   * For a clazz that is an heir of 'effect', find the corresponding inner
   * clazz for 'finally'.  This is used for code generation of intrinsic
   * 'instate0' that has to create code to call 'effect.finally'.
   *
   * @param cl index of a clazz that is an heir of 'effect'.
   *
   * @return the index of the requested `effect.finally` feature's clazz.
   */
  @Override
  public int lookup_static_finally(int cl)
  {
    throw new Error("NYI");
  }


  /**
   * For a clazz of concur.atomic, lookup the inner clazz of the value field.
   *
   * @param cl index of a clazz representing cl's value field
   *
   * @return the index of the requested `concur.atomic.value` field's clazz.
   */
  @Override
  public int lookupAtomicValue(int cl)
  {
    throw new Error("NYI");
  }


  /**
   * For a clazz of array, lookup the inner clazz of the internal_array field.
   *
   * @param cl index of a clazz `array T` for some type parameter `T`
   *
   * @return the index of the requested `array.internal_array` field's clazz.
   */
  @Override
  public int lookup_array_internal_array(int cl)
  {
    throw new Error("NYI");
  }


  /**
   * For a clazz of fuzion.sys.internal_array, lookup the inner clazz of the
   * data field.
   *
   * @param cl index of a clazz `fuzion.sys.internal_array T` for some type parameter `T`
   *
   * @return the index of the requested `fuzion.sys.internal_array.data` field's clazz.
   */
  @Override
  public int lookup_fuzion_sys_internal_array_data(int cl)
  {
    throw new Error("NYI");
  }


  /**
   * For a clazz of fuzion.sys.internal_array, lookup the inner clazz of the
   * length field.
   *
   * @param cl index of a clazz `fuzion.sys.internal_array T` for some type parameter `T`
   *
   * @return the index of the requested `fuzion.sys.internal_array.length` field's clazz.
   */
  @Override
  public int lookup_fuzion_sys_internal_array_length(int cl)
  {
    throw new Error("NYI");
  }


  /**
   * For a clazz of error, lookup the inner clazz of the msg field.
   *
   * @param cl index of a clazz `error`
   *
   * @return the index of the requested `error.msg` field's clazz.
   */
  @Override
  public int lookup_error_msg(int cl)
  {
    throw new Error("NYI");
  }


  /*---------------------------  types  --------------------------*/


  /**
   * Is there just one single value of this class, so this type is essentially a
   * C/Java `void` type?
   *
   * NOTE: This is false for Fuzion's `void` type!
   */
  @Override
  public boolean clazzIsUnitType(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    return id2clazz(cl).isUnitType();
  }


  /**
   * Is this a void type, i.e., values of this clazz do not exist.
   */
  @Override
  public boolean clazzIsVoidType(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    return cl == clazz(SpecialClazzes.c_void);
  }


  /**
   * Test is a given clazz is not -1 and stores data.
   *
   * @param cl the clazz defining a type, may be -1
   *
   * @return true if cl != -1 and not unit or void type.
   */
  @Override
  public boolean hasData(int cl)
  {
    throw new Error("NYI");
  }


  /*----------------------  type parameters  ---------------------*/


  /**
   * Get the id of an actual generic parameter of a given clazz.
   *
   * @param cl a clazz id
   *
   * @param gix indec of the generic parameter
   *
   * @return id of cl's actual generic parameter #gix
   */
  @Override
  public int clazzActualGeneric(int cl, int gix)
  {
    throw new Error("NYI");
  }


  /*---------------------  analysis results  ---------------------*/


  /**
   * Determine the lifetime of the instance of a call to clazz cl.
   *
   * @param cl a clazz id of any kind
   *
   * @return A conservative estimate of the lifespan of cl's instance.
   * Undefined if a call to cl does not create an instance, Call if it is
   * guaranteed that the instance is inaccessible after the call returned.
   */
  @Override
  public LifeTime lifeTime(int cl)
  {
    throw new Error("NYI");
  }


  /*--------------------------  accessing code  -------------------------*/


  /*
  @Override
  public boolean withinCode(int s)
  {
    return (s != NO_SITE) && false;
  }
  */

  /**
   * Get the clazz id at the given site
   *
   * @param s a site, may be !withinCode(s), i.e., this may be used on
   * `clazzCode(cl)` if the code is empty.
   *
   * @return the clazz id that code at site s belongs to.
   */
  @Override
  public int clazzAt(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size());

    return _siteClazzes.get(s - SITE_BASE);
  }


  /**
   * Create a String representation of a site for debugging purposes:
   *
   * @param s a site or NO_SITE
   *
   * @return a String describing site
   */
  @Override
  public String siteAsString(int s)
  {
    throw new Error("NYI");
  }


  /**
   * Get the expr at the given site
   *
   * @param s site
   */
  @Override
  public ExprKind codeAt(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       withinCode(s));

    ExprKind result;
    var e = getExpr(s);
    if (e instanceof Clazz    )  /* Clazz represents the field we assign a value to */
      {
        result = ExprKind.Assign;
      }
    else
      {
        result = exprKind(e);
      }
    if (result == null)
      {
        Errors.fatal(sitePos(s),
                     "Expr `" + e.getClass() + "` not supported in FUIR.codeAt", "Expression class: " + e.getClass());
        result = ExprKind.Current; // keep javac from complaining.
      }
    return result;
  }


  /**
   * For an instruction of type ExprKind.Tag at site s, return the type of the
   * original value that will be tagged.
   *
   * @param s a code site for an Env instruction.
   *
   * @return the original type, i.e., for `o option i32 := 42`, this is `i32`.
   */
  @Override
  public int tagValueClazz(int s)
  {
    throw new Error("NYI");
  }


  /**
   * For an instruction of type ExprKind.Tag at site s, return the type of the
   * original value that will be tagged.
   *
   * @param s a code site for an Env instruction.
   *
   * @return the new choice type, i.e., for `o option i32 := 42`, this is
   * `option i32`.
   */
  @Override
  public int tagNewClazz(int s)
  {
    throw new Error("NYI");
  }


  /**
   * For an instruction of type ExprKind.Tag at site s, return the number of the
   * choice. This will be the same number as the tag number used in a match.
   *
   * @param s a code site for an Env instruction.
   *
   * @return the tag number, i.e., for `o choice a b i32 c d := 42`, this is
   * `2`.
   */
  @Override
  public int tagTagNum(int s)
  {
    throw new Error("NYI");
  }


  /**
   * For an instruction of type ExprKind.Env at site s, return the type of the
   * env value.
   *
   * @param s a code site for an Env instruction.
   */
  @Override
  public int envClazz(int s)
  {
    throw new Error("NYI");
  }


  /**
   * For an instruction of type ExprKind.Box at site s, return the original type
   * of the value that is to be boxed.
   *
   * @param s a code site for a Box instruction.
   *
   * @return the original type of the value to be boxed.
   */
  @Override
  public int boxValueClazz(int s)
  {
    throw new Error("NYI");
  }


  /**
   * For an instruction of type ExprKind.Box at site s, return the new reference
   * type of the value that is to be boxed.
   *
   * @param s a code site for a Box instruction.
   *
   * @return the new reference type of the value to be boxed.
   */
  @Override
  public int boxResultClazz(int s)
  {
    throw new Error("NYI");
  }


  /**
   * Get the code for a comment expression.  This is used for debugging.
   *
   * @param s site of the comment
   */
  @Override
  public String comment(int s)
  {
    throw new Error("NYI");
  }


  /**
   * Get the inner clazz for a non dynamic access or the static clazz of a dynamic
   * access.
   *
   * @param s site of the access
   *
   * @return the clazz that has to be accessed or -1 if the access is an
   * assignment to a field that is unused, so the assignment is not needed.
   */
  @Override
  public int accessedClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       withinCode(s),
       codeAt(s) == ExprKind.Call   ||
       codeAt(s) == ExprKind.Assign    );

    var cl = clazzAt(s);
    var outerClazz = id2clazz(cl);
    var e = getExpr(s);

    Clazz innerClazz = null;
    /*
      (e instanceof AbstractCall   call) ? outerClazz.actualClazzes(call, null)[0] :
      (e instanceof AbstractAssign a   ) ? outerClazz.actualClazzes(a   , null)[1] :
      (e instanceof Clazz          fld ) ? fld :
      (Clazz) (Object) new Object() { { if (true) throw new Error("accessedClazz found unexpected Expr " + (e == null ? e : e.getClass()) + "."); } } /* Java is ugly... //;
*/
    return innerClazz == null ? -1 : innerClazz._id;
  }


  /**
   * Get the type of an assigned value. This returns the type even if the
   * assigned field has been removed and accessedClazz() returns -1.
   *
   * @param s site of the assignment
   *
   * @return the type of the assigned value.
   */
  @Override
  public int assignedType(int s)
  {
    dev.flang.util.Debug.umprintln("NYI!");
    return clazz(SpecialClazzes.c_i32);
  }


  /**
   * Get the possible inner clazzes for a call or assignment to a field
   *
   * @param s site of the access
   *
   * @return an array with an even number of element pairs with accessed target
   * clazzes at even indices followed by the corresponding inner clazz of the
   * feature to be accessed for this target.
   */
  @Override
  public int[] accessedClazzes(int s)
  {
    throw new Error("NYI");
  }


  /**
   * Is an access to a feature (assignment, call) dynamic?
   *
   * @param s site of the access
   *
   * @return true iff the assignment or call requires dynamic binding depending
   * on the actual target type.
   */
  @Override
  public boolean accessIsDynamic(int s)
  {
    throw new Error("NYI");
  }


  /**
   * Get the target (outer) clazz of a feature access
   *
   * @param cl index of clazz containing the access
   *
   * @param s site of the access
   *
   * @return index of the static outer clazz of the accessed feature.
   */
  @Override
  public int accessTargetClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       withinCode(s),
       codeAt(s) == ExprKind.Assign ||
       codeAt(s) == ExprKind.Call  );

    dev.flang.util.Debug.umprintln("NYI!");
    return _universe;
  }


  /**
   * For an intermediate command of type ExprKind.Const, return its clazz.
   *
   * Currently, the clazz is one of bool, i8, i16, i32, i64, u8, u16, u32, u64,
   * f32, f64, or Const_String. This will be extended by value instances without
   * refs, choice instances with tag, arrays, etc.
   *
   * @param cl index of clazz containing the constant
   *
   * @param s site of the constant
   */
  @Override
  public int constClazz(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       withinCode(s),
       codeAt(s) == ExprKind.Const);

    var cl = clazzAt(s);
    var cc = id2clazz(cl);
    var outerClazz = cc;
    var ac = (Constant) getExpr(s);
    var clazz = switch (ac.origin())
      {
      case Constant     c ->
      {
        var p = c.pos();
        var const_clazz = clazz(c, outerClazz, NO_INH);
        //        outerClazz.saveActualClazzes(c, outer, new Clazz[] {const_clazz});
        //        const_clazz.instantiated(p);
        if (const_clazz._feature == Types.resolved.f_array)
          { // add clazzes touched by constant creation:
            //
            //   array.internal_array
            //   fuzion.sys.internal_array
            //   fuzion.sys.internal_array.data
            //   fuzion.sys.Pointer
            //
            throw new Error("NYI: constClazz for array!");
            /*
            var array          = const_clazz;
            var internal_array = array.lookup(Types.resolved.f_array_internal_array);
            var sys_array      = internal_array.resultClazz();
            var data           = sys_array.lookup(Types.resolved.f_fuzion_sys_array_data);
            array.instantiated(p);
            sys_array.instantiated(p);
            data.resultClazz().instantiated(p);
            */
          }
        yield const_clazz;
      }

      case AbstractCall c -> null;
      case InlineArray  ia -> null;
      default -> throw new Error("constClazz origin of unknown class " + ac.origin().getClass());
      };

    //    var acl = cc.actualClazzes(ac.origin(), null);
    // origin might be Constant, AbstractCall or InlineArray.  In all
    // cases, the clazz of the result is the first actual clazz:
    // var clazz = acl[0];
    return clazz._id;
  }


  /**
   * For an intermediate command of type ExprKind.Const, return the constant
   * data using little endian encoding, i.e, 0x12345678 -> { 0x78, 0x56, 0x34, 0x12 }.
   */
  @Override
  public byte[] constData(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       withinCode(s),
       codeAt(s) == ExprKind.Const);

    var ic = getExpr(s);
    return ((Constant) ic).data();
  }


  /**
   * For a match expression, get the static clazz of the subject.
   *
   * @param s site of the match
   *
   * @return clazz id of type of the subject
   */
  @Override
  public int matchStaticSubject(int s)
  {
    throw new Error("NYI");
  }


  /**
   * For a match expression, get the field of a given case
   *
   * @param s site of the match
   *
   * @paramc cix index of the case in the match
   *
   * @return clazz id of field the value in this case is assigned to, -1 if this
   * case does not have a field or the field is unused.
   */
  @Override
  public int matchCaseField(int s, int cix)
  {
    throw new Error("NYI");
  }


  /**
   * For a given tag return the index of the corresponding case.
   *
   * @param s site of the match
   *
   * @param tag e.g. 0,1,2,...
   *
   * @return the index of the case for tag `tag`
   */
  @Override
  public int matchCaseIndex(int s, int tag)
  {
    throw new Error("NYI");
  }


  /**
   * For a match expression, get the tags matched by a given case
   *
   * @param s site of the match
   *
   * @paramc cix index of the case in the match
   *
   * @return array of tag numbers this case matches
   */
  @Override
  public int[] matchCaseTags(int s, int cix)
  {
    throw new Error("NYI");
  }


  /**
   * For a match expression, get the code associated with a given case
   *
   * @param s site of the match
   *
   * @paramc cix index of the case in the match
   *
   * @return code block for the case
   */
  @Override
  public int matchCaseCode(int s, int cix)
  {
    throw new Error("NYI");
  }


  /**
   * @return If the expression has only been found to result in void.
   */
  @Override
  public boolean alwaysResultsInVoid(int s)
  {
    return false;
  }


  /**
   * Get the source code position of an expr at the given index if it is available.
   *
   * @param s site of an expression
   *
   * @return the source code position or null if not available.
   */
  @Override
  public SourcePosition sitePos(int s)
  {
    throw new Error("NYI");
  }


  /*-----------------  convenience methods for effects  -----------------*/


  /**
   * Is cl one of the intrinsics in effect that changes the effect in
   * the current environment?
   *
   * @param cl the id of the intrinsic clazz
   *
   * @return true for effect.install and similar features.
   */
  @Override
  public boolean isEffectIntrinsic(int cl)
  {
    throw new Error("NYI");
  }


  /**
   * For an intrinsic in effect that changes the effect in the
   * current environment, return the type of the environment.  This type is used
   * to distinguish different environments.
   *
   * @param cl the id of the intrinsic clazz
   *
   * @return the type of the outer feature of cl
   */
  @Override
  public int effectTypeFromInstrinsic(int cl)
  {
    throw new Error("NYI");
  }


  /*------------------------------  arrays  -----------------------------*/


  /**
   * the clazz of the elements of the array
   *
   * @param constCl, e.g. `array (tuple i32 codepoint)`
   *
   * @return e.g. `tuple i32 codepoint`
   */
  @Override
  public int inlineArrayElementClazz(int constCl)
  {
    throw new Error("NYI");
  }


  /**
   * Is `constCl` an array?
   */
  @Override
  public boolean clazzIsArray(int constCl)
  {
    throw new Error("NYI");
  }


  /*----------------------------  constants  ----------------------------*/


  /**
   * Extract bytes from `bb` that should be used when deserializing for `cl`.
   *
   * @param cl the constants clazz
   *
   * @param bb the bytes to be used when deserializing this constant.
   *           May be more than necessary for variable length constants
   *           like strings, arrays, etc.
   */
  @Override
  public byte[] deseralizeConst(int cl, ByteBuffer bb)
  {
    throw new Error("NYI");
  }


  /*----------------------  accessing source code  ----------------------*/


  /**
   * Get the source file the clazz originates from.
   *
   * e.g. /fuzion/tests/hello/HelloWorld.fz, $FUZION/lib/panic.fz
   */
  @Override
  public String clazzSrcFile(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl <= CLAZZ_END);

    var c = id2clazz(cl);
    return c._feature.pos()._sourceFile._fileName.toString();
  }


  /**
   * Get the position where the clazz is declared
   * in the source code.
   */
  @Override
  public SourcePosition declarationPos(int cl)
  {
    throw new Error("NYI");
  }


  /*---------------------------------------------------------------------
   *
   * handling of abstract missing errors.
   *
   * NYI: This still uses AirErrors.abstractFeatureNotImplemented, which should
   * eventually be moved to DFA or somewhere else when DFA is joined with AIR
   * phase.
   */


  /**
   * If a called to an abstract feature was found, the DFA will use this to
   * record the missing implementation of an abstract features.
   *
   * Later, this will be reported as an error via `reportAbstractMissing()`.
   *
   * @param cl clazz is of the clazz that is missing an implementation of an
   * abstract features.
   *
   * @param f the inner clazz that is called and that is missing an implementation
   *
   * @param instantiationPos if known, the site where `cl` was instantiated,
   * `NO_SITE` if unknown.
   */
  @Override
  public void recordAbstractMissing(int cl, int f, int instantiationSite, String context)
  {
    throw new Error("NYI");
  }


  /**
   * In case any errors were recorded via `recordAbstractMissing` this will
   * create the corresponding error messages.  The errors reported will be
   * cumulative, i.e., if a clazz is missing several implementations of abstract
   * features, there will be only one error for that clazz.
   */
  @Override
  public void reportAbstractMissing()
  {
    if (false) throw new Error("NYI");
  }


}

/* end of file */
