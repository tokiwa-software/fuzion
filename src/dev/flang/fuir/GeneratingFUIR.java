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
import java.nio.ByteOrder;

import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.air.AirErrors;
import dev.flang.air.FeatureAndActuals;

import dev.flang.ast.AbstractAssign;
import dev.flang.ast.AbstractBlock;
import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractCurrent;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractMatch;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Box;
import dev.flang.ast.Constant;
import dev.flang.ast.Context;
import dev.flang.ast.Env;
import dev.flang.ast.Expr;
import dev.flang.ast.FeatureName;
import dev.flang.ast.InlineArray;
import dev.flang.ast.NumLiteral;
import dev.flang.ast.ResolvedNormalType;
import dev.flang.ast.Tag;
import dev.flang.ast.Types;
import dev.flang.ast.Universe;

import dev.flang.fe.FrontEnd;
import dev.flang.fe.LibraryFeature;
import dev.flang.fe.LibraryModule;

import dev.flang.mir.MIR;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.FuzionOptions;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.IntArray;
import dev.flang.util.IntMap;
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


  class Clazz implements Comparable<Clazz>
  {
    final int _id;

    final int _outer;
    Clazz outer() { return _outer == NO_CLAZZ ? null : id2clazz(_outer); }

    int gix() { return _feature.globalIndex(); }
    final LibraryFeature _feature;
    LibraryFeature feature() { return _feature; }
    static final Clazz[] NO_CLAZZES = new Clazz[0];
    static final int[] NO_CLAZZ_IDS = new int[0];
    Clazz[] _actualTypeParameters = NO_CLAZZES;
    Clazz[] actualTypeParameters() { return _actualTypeParameters; }
    final AbstractType _type;
    /* final */ int _select = -1;

  /**
   * Cached result of choiceGenerics(), only used if isChoice() and
   * !isChoiceOfOnlyRefs().
   */
  public List<Clazz> _choiceGenerics;


  /**
   * Flag that is set while the layout of objects of this clazz is determined.
   * This is used to detect recursive clazzes that contain value type fields of
   * the same type as the clazz itself.
   */
  LayoutStatus _layouting = LayoutStatus.Before;


    SpecialClazzes _s = SpecialClazzes.c_NOT_FOUND;
    boolean _needsCode;
    int _code; // site of the code block of this clazz
    Clazz _outerRef = null;
    boolean isBoxed() { return isRef() && !_feature.isThisRef(); }
    Clazz[] _fields = null;
    Clazz[] _argumentFields = null;

    Clazz _resultClazz = null;



  /**
   * Will instances of this class be created?
   */
    private boolean _isInstantiated = true; // NYI: false;

  /**
   * Is this a normalized outer clazz? If so, there might be calls on this as an
   * outer clazz even if it is not instantiated.
   */
  public boolean _isNormalized = false;


  /**
   * Enum to record that we have checked the layout of this clazz and to detect
   * recursive value fields during layout.
   */
  enum LayoutStatus
  {
    Before,
    During,
    After,
  }




  /**
   * isRef
   */
  public boolean isRef()
  {
    return _type.isRef();
  }



    /**
     * Set of all heirs of this clazz.
     */
    Set<Clazz> _heirs = null;


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


  /**
   * Cached result of parents(), null before first call to parents().
   */
  private Set<Clazz> _parents = null;


    boolean _closed = false;

    Clazz _asValue;

    Clazz(Clazz outer,
          AbstractType type,
          int id)
    {
      if (PRECONDITIONS) require
                           (!type.dependsOnGenerics() || true /* NYI: UNDER DEVELOPMENT: Why? */,
         !type.containsThisType());

      outer = normalizeOuter(type, outer);
      this._type = outer != null
        ? ResolvedNormalType.newType(type, outer._type)
        : type;

      _outer = outer == null ? NO_CLAZZ : outer._id;
      _id = id;
      _feature = (LibraryFeature) type.feature();
      _needsCode = false;
      _code = NO_SITE;
    }

    /**
     * Additional initialization code that has to be run after this Clazz was added to clazzesHT.
     */
    void init()
    {
      _choiceGenerics = determineChoiceGenerics();
      var vas = feature().valueArguments();
      if (vas.size() == 0 || isBoxed())
        {
          _argumentFields = NO_CLAZZES;
        }
      else
        {
          _argumentFields = determineArgumentFields();
        }

      var gs = _type.generics();
      if (!gs.isEmpty())
        {
          _actualTypeParameters = new Clazz[gs.size()];
          for (int i = 0; i < gs.size(); i++)
            {
              var gi = gs.get(i);
              if (gi.isThisType())
                {
                  // Only calls to type_as_value may have generic parameters gi with
                  // gi.isThisType().  Calls to type_as_value will essentially become
                  // NOPs anyway. Here we replace the this.types by their
                  // underlying type to avoid problems creating clazzes form
                  // this.types.
                  if (CHECKS) check
                    (Errors.any() || feature() == Types.resolved.f_type_as_value);

                  gi = gi.feature().isThisRef() ? gi.asRef() : gi.asValue();
                }
              _actualTypeParameters[i] = type2clazz(gi);
            }
        }

      inspectCode(new List<>(), _feature);
    }

    Clazz outerRef()
    {
      var res = _outerRef;
      if (res == null)
        {
          var or = _feature.outerRef();
          if (or != null)
            {
              res = id2clazz(lookup(new FeatureAndActuals(or, new List<>()), -1, _feature, false, false));
              _outerRef = res;
            }
        }
      return res;
    }


  /**
   * Determine the argument fields of this routine.
   *
   * @return the argument fields array or NO_CLAZZES if this is not a routine.
   */
  private Clazz[] determineArgumentFields()
  {
    return actualFields(feature().valueArguments());
  }


    void addInner(Clazz i)
    {
      if (PRECONDITIONS) require
        (!_closed);

      if (_inner == EMPTY_INT_ARRAY)
        {
          _inner = new IntArray();
        }
      _inner.add(i._id);
    }



  /**
   * Check if the given feature has an outer ref that is used.
   *
   * If an outer ref is used to access the outer instance, we must not normalize
   * because we will need the exact type of the outer instance to specialize code
   * or to access features that only exist in the specific version
   *
   * @param f the feature to check if it has an outer ref
   */
  private boolean hasUsedOuterRef(AbstractFeature f)
  {
    var or = f.outerRef();

    return !f.isConstructor()  // do not specialize a constructor
      && or != null;
  }



  /**
   * Normalize an outer clazz for a given type. For a reference clazz that
   * inherits from f, this will return the corresponding clazz derived from
   * f. The idea is that, e.g., we do not need to distinguish Const_String.length
   * from (array u8).length.
   *
   * @param t the type of the newly created clazz
   *
   * @param outer the outer clazz that should be normalized for the newly
   * created clazz
   *
   * @return the normalized version of outer.
   */
  private Clazz normalizeOuter(AbstractType t, Clazz outer)
  {
    var f = t.feature();
    if (outer != null && !hasUsedOuterRef(f) && !f.isField() && t != Types.t_ERROR)
      {
        outer = outer.normalize(t.feature().outer());
      }
    return outer;
  }


  /**
   * Normalize a reference clazz to the given feature.  For a reference clazz
   * that inherits from f, this will return the corresponding clazz derived
   * from f. The idea is that, e.g., we do not need to distinguish Const_String.length
   * from (array u8).length.
   *
   * @param f the feature we want to normalize to (array in the example above).
   *
   * @return the normalized clazz.
   */
  private Clazz normalize(AbstractFeature f)
  {
    if (// an outer clazz of value type is not normalized (except for
        // universe, which was done already).
        !isRef() ||

        // optimization: if feature() is already f, there is nothing to
        // normalize anymore
        feature() == f ||

        // if an outer ref is used (i.e., state is resolved) to access the
        // outer instance, we must not normalize because we will need the
        // exact type of the outer instance to specialize code or to access
        // features that only exist in the specific version
        hasUsedOuterRef(feature())
        )
      {
        return this;
      }
    else
      {
        var t = this._type.actualType(f.selfType(), Context.NONE).asRef();
        return normalize2(t);
      }
  }
  private Clazz normalize2(AbstractType t)
  {
    var f = t.feature();
    if (f.isUniverse())
      {
        return id2clazz(_universe);
      }
    else
      {
        var normalized = newClazz(normalize2(f.outer().selfType()), t);
        normalized._isNormalized = true;
        return normalized;
      }
  }



  /**
   * Make sure this clazz is added to the set of heirs for all of its parents.
   */
  void registerAsHeir()
  {
    registerAsHeir(this);
  }

  /**
   * private helper for registerAsHeir().  Make sure this clazz is added to the
   * set of heirs of parent and all of parent's parents.
   */
  private void registerAsHeir(Clazz parent)
  {
    parent.heirs().add(this);
    for (var p: parents())
      {
        if (!p.heirs().contains(this))
          {
            registerAsHeir(p);
          }
      }
  }


  /**
   * Set of heirs of this clazz, including this itself.  This is defined for
   * clazzes with isRef() only.
   *
   * This set is initially empty, it will be filled by `registerAsHeir()`
   * which is called for every new Clazz created via _clazzes.create().
   *
   * @return the heirs including this.
   */
  public Set<Clazz> heirs()
  {
    if (_heirs == null)
      {
        _heirs = new HashSet<>();
      }
    return _heirs;
  }


  /**
   * Set of direct parent clazzes this inherits from.
   */
  private Set<Clazz> directParents()
  {
    var result = new HashSet<Clazz>();
    result.add(this);
    for (var p: feature().inherits())
      {
        var pt = p.type();
        var t1 = isRef() && !pt.isVoid() ? pt.asRef() : pt.asValue();
        var t2 = _type.actualType(t1, Context.NONE);
        var pc = newClazz(t2);
        if (CHECKS) check
          (Errors.any() || pc.isVoidType() || isRef() == pc.isRef());
        result.add(pc);
      }
    return result;
  }


  /**
   * Set of parents of this clazz, including this itself.
   *
   * @return the heirs including this.
   */
  public Set<Clazz> parents()
  {
    var result = _parents;
    if (result == null)
      {
        result = new HashSet<Clazz>();
        result.add(this);
        for (var p : directParents())
          {
            if (!result.contains(p))
              {
                for (var pp : p.parents())
                  {
                    if (isRef() && !pp.isVoidType())
                      {
                        pp = pp.asRef();
                      }
                    result.add(pp);
                  }
              }
          }
        _parents = result;
      }
    return result;
  }


  boolean needsCode() { return _needsCode && (!_lookupDone || !_feature.isField() || !resultClazz().isUnitType()); }

    void doesNeedCode()
    {
      if (!_needsCode)
        {
          _needsCode = true;
          var r = resultField();
          if (r != NO_CLAZZ)
            { // NYI: UNDER DEVELOPMENT: This is require for tests/javaBase. Check why this is needed only there and not otherwise!
              id2clazz(r).doesNeedCode();
            }
        }
    }




  /**
   * Helper routine for compareTo: compare the outer classes.  If outer are refs
   * for both clazzes, they can be considered the same as long as their outer
   * classes (recursively) are the same. If they are values, they need to be
   * exactly equal.
   */
  private int compareOuter(Clazz other)
  {
    var to = this .outer();
    var oo = other.outer();
    int result = 0;
    if (to != oo)
      {
        result =
          to == null ? -1 :
          oo == null ? +1 : 0;
        if (result == 0)
          {
            if (to.isRef() && oo.isRef())
              { // NYI: If outer is normalized for refs as described in the
                // constructor, there should be no need for special handling of
                // ref types here.
                result = to._type.compareToIgnoreOuter(oo._type);
                if (result == 0)
                  {
                    result = to.compareOuter(oo);
                  }
              }
            else
              {
                result =
                  !to.isRef() && !oo.isRef() ? to.compareTo(oo) :
                  to.isRef() ? +1
                             : -1;
              }
          }
      }
    return result;
  }


  /**
   * Compare this to other for creating unique clazzes.
   */
  public int compareTo(Clazz other)
  {
    if (PRECONDITIONS) require
      (other != null,
       this .getClass() == Clazz.class,
       other.getClass() == Clazz.class);

    var result = compareToIgnoreOuter(other);
    if (result == 0)
      {
        result = compareOuter(other);
      }
    return result;
  }


  /**
   * Compare this to other ignoring outer.
   */
  public int compareToIgnoreOuter(Clazz other)
  {
    var result =
      this._select < other._select ? -1 :
      this._select > other._select ? +1 : this._type.compareToIgnoreOuter(other._type);
    return result;
  }


    @Override
    public boolean equals(Object other)
    {
      return compareTo((Clazz)other)==0;
    }
    @Override
    public int hashCode()
    {
      return (_type.isRef() ? 0x777377 : 0) ^ gix();  // NYI: outer and type parameters!
    }



  /**
   * Convert the given generics to the actual generics of this class.
   *
   * @param generics a list of generic arguments that might itself consist of
   * formal generics
   *
   * @return The list of actual generics after replacing the generics of this
   * class or its outer classes.
   */
  public List<AbstractType> actualGenerics(List<AbstractType> generics)
  {
    var result = this._type.replaceGenerics(generics);

    // Replace any `a.this.type` actual generics by the actual outer clazz:
    result = result.map(t->replaceThisType(t));

    if (_outer != NO_CLAZZ)
      {
        result = outer().actualGenerics(result);
      }
    return result;
  }


    boolean isUnitType()
    {
      if (_isUnitType != YesNo.dontKnow)
        {
          return _isUnitType == YesNo.yes;
        }

      var res = YesNo.no;
      if (_s == SpecialClazzes.c_unit)
        {
          res = YesNo.yes;
        }
      else if ( _lookupDone && (!isRef()                       &&
                                !_feature.isBuiltInPrimitive() &&
                                !clazzIsVoidType(_id)          &&
                                !clazzIsChoice(_id)              ))
        {
          // Tricky: To avoid endless recursion, we set _isUnitType to No. In case we
          // have a recursive type, isUnitType() will return false, so recursion will
          // stop and the result for the recursive type will be false.
          //
          // Object layout will later report an error for this case. (NYI: check this with a test!)
          _isUnitType = YesNo.no;
          res = YesNo.yes;
          for(var ix = 0; ix < _inner.size(); ix++)
            {
              var i = _inner.get(ix);
              if (clazzKind(i) == FeatureKind.Field)
                {
                  var rc = clazzResultClazz(i);
                  res = clazzIsUnitType(rc) ? res : YesNo.no;
                }
            }
          _isUnitType = YesNo.dontKnow;
        }
      if (_lookupDone)
        {
          _isUnitType = res;
        }
      return res == YesNo.yes;
    }


  /**
   * isVoidType checks if this is void.  This is not true for user defined void
   * types, i.e., any product type, e.g.,
   *
   *    absurd (i i32, v void) is {}
   *
   * that has a field of type void is effectively a void type. This call will,
   * however, return false for user defined void types.
   */
  public boolean isVoidType()
  {
    return this._s == SpecialClazzes.c_void;
  }


  /**
   * isVoidType checks if this is void (@see isVoidType) or undefined, which is
   * used for clazzes that cannot be created due to failing type constraints in
   * preconditions `pre T : x`.
   */
  public boolean isVoidOrUndefined()
  {
    return isVoidType() /* NYI:  ||
                            this == _clazzes.undefined.getIfCreated() */;
  }


  /**
   * Layout this clazz. In case a cyclic nesting of value fields is detected,
   * report an error.
   */
  void layoutAndHandleCycle()
  {
    var cycle = layout();
    if (cycle != null && Errors.count() <= AirErrors.count())
      {
        StringBuilder cycleString = new StringBuilder();
        var tp = _type.declarationPos();
        for (SourcePosition p : cycle)
          {
            if (!p.equals(tp))
              {
                cycleString.append(p.show()).append("\n");
              }
          }
        AirErrors.error(tp,
                        "Cyclic field nesting is not permitted",
                        "Cyclic value field nesting would result in infinitely large objects.\n" +
                        "Cycle of nesting found during clazz layout:\n" +
                        cycleString + "\n" +
                        "To solve this, you could change one or several of the fields involved to a reference type by adding 'ref' before the type.");
      }
  }



  /**
   * layout this clazz.  This does not really do the layout, but it checks that
   * the layout is possible and there are no recursively nested value types.
   *
   * @return null in case of success, a list of source code positions that shows
   * the recursively nested value types otherwise.
   */
  private TreeSet<SourcePosition> layout()
  {
    TreeSet<SourcePosition> result = null;
    switch (_layouting)
      {
      case During:
        result = new TreeSet<>();
        result.add(this.feature().pos());
        break;
      case Before:
        {
          _layouting = LayoutStatus.During;
          if (isChoice())
            {
              for (Clazz c : choiceGenerics())
                {
                  if (result == null && !c.isRef())
                    {
                      result = c.layout();
                      if (result != null)
                        {
                          result.add(c.feature().pos());
                        }
                    }
                }
            }
          for (var fc : fields())
            {
              if (result == null && !fc.feature().isOuterRef())
                {
                  result = layoutFieldType(fc);
                }
            }
          _layouting = LayoutStatus.After;
        }
      case After: break;
      }
    return result;
  }


  /**
   * Helper for layout() to layout type of given field.
   *
   * @param field to be added to this.
   */
  private TreeSet<SourcePosition> layoutFieldType(Clazz field)
  {
    TreeSet<SourcePosition> result = null;
    var fieldClazz = field.resultClazz();
    if (!fieldClazz.isRef() &&
        !fieldClazz.feature().isBuiltInPrimitive() &&
        !fieldClazz.isVoidType())
      {
        result = fieldClazz.layout();
        if (result != null)
          {
            result.add(field.feature().pos());
          }
      }
    return result;
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
     */
    int lookup(AbstractFeature f)
    {
      if (PRECONDITIONS) require
        (f != null,
         !clazzIsVoidType(_id));

      return lookup(f, f.pos() /* NYI: _clazzes.isUsedAt(f) */);
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
      return lookup(f, p, true);
    }
    int lookup(AbstractFeature f,
               HasSourcePosition p,
               boolean needed)
    {
      if (PRECONDITIONS) require
        (f != null,
         !clazzIsVoidType(_id));

      return lookup(new dev.flang.air.FeatureAndActuals(f, dev.flang.ast.AbstractCall.NO_GENERICS), -1, p, false, needed);
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
      return lookup(fa, -1, p, false, true);
    }


    Clazz lookup(AbstractCall c, List<AbstractType> typePars, boolean needsCode)
    {
      return isVoidOrUndefined()
        ? this
        : id2clazz(lookup(new FeatureAndActuals(c.calledFeature(),
                                                typePars),
                          c.select(),
                          c,
                          c.isInheritanceCall(),
                          needsCode));
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
               boolean isInheritanceCall,
               boolean needed)
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
              innerClazzes = new Clazz[replaceOpenCount(fa._f)];
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
              var af = findRedefinition(f);
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

              var outerUnboxed = isBoxed() && !f.isConstructor() ? asValue() : this;
              innerClazz = newClazz(outerUnboxed, t);
              innerClazz._select = select; // NYI: pass through newClazz to constructor for Clazz
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
                  innerClazzes[select] = innerClazz;
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
      if (!_lookupDone && needed)
        {
          innerClazz.doesNeedCode();
        }
      return innerClazz._id;
    }


  /**
   * find redefinition of a given feature in this clazz. NYI: This will have to
   * take the whole inheritance chain into account including the parent view that is
   * being filled with live:
   */
  private AbstractFeature findRedefinition(AbstractFeature f)
  {
    if (PRECONDITIONS) require
      (// type parameters never get redefined, they are effectively fixed.
       // However, type features get replaced by actual type parameters in the
       // inheritance call. Instead of searching for the redefinition, the type
       // should be replaced by the actual type.
       !f.isTypeParameter());

    var fn = f.featureName();
    var tf = feature();
    if (/* tf != Types.f_ERROR && */ f != Types.f_ERROR && tf != Types.resolved.f_void)
      {
        var chain = tf.findInheritanceChain(f.outer());
        if (CHECKS) check
          (chain != null || Errors.any());
        if (chain != null)
          {
            for (var p: chain)
              {
                fn = f.outer().handDown(null, f, fn, p, feature());  // NYI: need to update f/f.outer() to support several levels of inheritance correctly!
              }
          }
      }

    // first look in the feature itself
    AbstractFeature result = _mainModule.lookupFeature(feature(), fn, f);

    if (!result.redefinesFull().contains(f) && result != f)
      {
        // feature with same name, but not a redefinition
        result = null;
      }

    // the inherited feature might not be
    // visible to the inheriting feature
    var chain = tf.findInheritanceChain(f.outer());
    if (result == null && chain != null)
      {
        for (var p: chain)
          {
            result = _mainModule.lookupFeature(p.calledFeature(), fn, f);
            if (!result.redefinesFull().contains(f) && result != f)
              {
                // feature with same name, but not a redefinition
                result = null;
              }
            if (result != null)
              {
                break;
              }
          }
      }

    if (POSTCONDITIONS) ensure
      (result != null || Errors.any());

    return result;
  }

    Clazz asValue()
    {
      if (_asValue == null)
        {
          _asValue = isRef() && _type != Types.t_ADDRESS
            ? newClazz(outer(), _type.asValue())
            : this;
        }
      if (CHECKS) check
        (!_asValue.isRef());
      return _asValue;
    }


  /**
   * In case this is a Clazz of value type, create the corresponding reference clazz.
   */
  public Clazz asRef()
  {
    return isRef()
      ? this
      : newClazz(outer(), _type.asRef());
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
   * Recursive helper function for to find the clazz for an outer ref from
   * an inherited feature.
   *
   * @param cf the feature corresponding to the outer reference
   *
   * @param f the feature of the target of the inheritance call
   *
   * @param result must be null on the first call. This is used during recursive
   * traversal to check that all results are equal in case several results are
   * found.
   *
   * @return the static clazz of this call to an outer ref cf.
   */
  private Clazz inheritedOuterRefClazz(Clazz outer, Expr target, AbstractFeature cf, AbstractFeature f, Clazz result)
  {
    if (PRECONDITIONS) require
      ((outer != null) != (target != null));

    if (f.outerRef() == cf)
      { // a "normal" outer ref for the outer clazz surrounding this instance or
        // (if in recursion) an inherited outer ref referring to the target of
        // the inherits call
        if (outer == null)
          {
            outer = clazz(target, this, new List<>());
          }
        if (CHECKS) check
          (result == null || result == outer);

        result = outer;
      }
    else
      {
        for (var p : f.inherits())
          {
            result = inheritedOuterRefClazz(null, p.target(), cf, p.calledFeature(), result);
          }
      }
    return result;
  }



  /**
   * Determine the clazz of the result of calling this clazz, cache the result.
   *
   * @return the result clazz.
   */
  public Clazz resultClazz()
  {
    var result = _resultClazz;
    if (result == null)
      {
        var f = _feature;
        var o  = _outer != NO_CLAZZ ? id2clazz(_outer) : null;
        var of = _outer != NO_CLAZZ ? o._feature       : null;

        if (f.isConstructor())
          {
            result = this;
          }
        else if (f.isOuterRef())
          {
            result = o.inheritedOuterRefClazz(o.outer(), null, f, o._feature, null);
          }
        else if (f.isTypeParameter())
          {
            result = typeParameterActualType().typeClazz();
          }
        else if (f  == Types.resolved.f_type_as_value                     ||
                 of == Types.resolved.f_type_as_value && f == of.resultField()   )
          {
            var ag = (f == Types.resolved.f_type_as_value ? this : o).actualTypeParameters();
            result = ag[0].typeClazz();
          }
        else
          {
            var ft = f.resultType();
            result = handDown(ft, _select, new List<>(), _feature);
            if (result._feature.isTypeFeature())
              {
                var ac = handDown(result._type.generics().get(0), new List<>(), _feature);
                result = ac.typeClazz();
              }
          }
        _resultClazz = result;
      }
    return result;
  }


  /**
   * For a type clazz such as 'i32.type' return its name, such as 'i32'.
   */
  public String typeName()
  {
    if (PRECONDITIONS) require
      (feature().isTypeFeature());

    return _type.generics().get(0).asString(true);
  }


  /**
   * For a type parameter, return the clazz of the actual type.
   *
   * Example:
   *
   * For `(Types.get (array f64)).T` this results in `array f64`.
   */
  public Clazz typeParameterActualType()
  {
    if (PRECONDITIONS) require
      (feature().isTypeParameter());

    var f = feature();
    var o = outer();
    var inh = o.feature().tryFindInheritanceChain(f.outer());
    if (inh != null && inh.size() > 0)
      { // type parameter was inherited, so get value from parameter of inherits call:
        var call = inh.get(0);
        if (CHECKS) check
          (call.calledFeature() == f.outer());

        /* NYI:

        // NYI: CLEANUP: Ugly special handling, might be good to remove this: if
        // inherited by a ref type, actual clazzes are added to the ref
        // type. This is required, e.g., to run `tests/nom`.
        //
        // Smallest known example to reproduce a crash if `_outer.asRef()` case
        // is removed here:
        //
        //   _ := "A".starts_with "#"
        //   a => _ option (Sequence codepoint) := nil
        //        _ option (Sequence codepoint) := ["A"]
        //   c => a
        //   _ := c
        //
        var oc = _outer        .hasActualClazzes(call, null)
             || !_outer.asRef().hasActualClazzes(call, null) ? _outer
                                                             : _outer.asRef();
        */
        var oc = outer();
        var tclazz  = clazz(call.target(), oc, inh);
        var typePars = actualGenerics(call.actualTypeParameters());
        check(call.isInheritanceCall());
        o = tclazz.lookup(call, typePars, false);
      }
    var ix = f.typeParameterIndex();
    var oag = o.actualTypeParameters();
    return inh == null || ix < 0 || ix >= oag.length ? error()
                                                     : oag[ix];
  }


  /**
   * For a clazz a.b.c the corresponding type clazz a.b.c.type, which is,
   * actually, '((a.type a).b.type b).c.type c'.
   */
  Clazz typeClazz()
  {
    if (PRECONDITIONS)
      require(Errors.any() || !_type.isGenericArgument());

    if (_typeClazz == null)
      {
        if (_type.isGenericArgument())
          {
            _typeClazz = error();
          }
        else
          {
            var tt = _type.typeType();
            var ty = Types.resolved.f_Type.selfType();
            _typeClazz = _type.containsError()  ? error() :
                         feature().isUniverse() ? this    :
                         tt.compareTo(ty) == 0  ? create(ty, universe())
                                                : create(tt, outer().typeClazz()    );
          }
      }
    return _typeClazz;
  }


  /**
   * cached result of typeClazz()
   */
  private Clazz _typeClazz = null;

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
                               : res.outer();
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
                               id2clazz(lookup(p.outerRef(), pos, false)).resultClazz() :
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
        + ( isRef() && !feature().isThisRef() ? "ref "   : "" )
        + (!isRef() &&  feature().isThisRef() ? "value " : "" )
        + fname;
      if (typeType)
        {
          result = result + ".type";
        }

      var skip = typeType;
      for (var g : actualTypeParameters())
        {
          if (!skip) // skip first generic 'THIS#TYPE' for types of type features.
            {
              result = result + " " + g.asStringWrapped(humanReadable);
            }
          skip = false;
        }

      return result;
    }

    String asStringWrapped(boolean humanReadable)
    {
      return StringHelpers.wrapInParentheses(asString(humanReadable));
    }




  /**
   * call _clazzes.findClazzes for all the expressions in f, including all the
   * expressions of the parents of f.
   *
   * @param inh an empty list when inspecting code of `f` for `f`, a list of
   * inheritance calls when inspecting `f1` that inherits from `f`. The head of
   * this list is the inheritance call from `f1` to `f`, the tail is the
   * inheritance calls in case we are inspecting `f2` which inherits from `f1`,
   * etc.
   *
   * @þaram f the feature whose code should be inspected to find clazzes
   */
  private void inspectCode(List<AbstractCall> inh, AbstractFeature f)
  {
    for (var c: f.inherits())
      {
        AbstractFeature cf = c.calledFeature();
        var n = c.actuals().size();
        var argFields = new Clazz[n];
        for (var i = 0; i < n; i++)
          {
            if (i >= cf.valueArguments().size())
              {
                if (CHECKS) check
                  (Errors.any());
              }
            else
              {
                var cfa = cf.valueArguments().get(i);
                // argFields[i] = lookup(cfa, _clazzes.isUsedAt(f));
              }
          }
        //        _parentCallArgFields.put(c.globalIndex(), argFields);

        if (CHECKS) check
          (Errors.any() || cf != null);

        if (cf != null)
          {
            var inh1 = new List<AbstractCall>();
            inh1.add(c);
            inh1.addAll(inh);
            inspectCode(inh1, cf);
          }
      }
  }




  /**
   * Is this a choice-type, i.e., does it directly inherit from choice?
   */
  public boolean isChoice()
  {
    return feature().isChoice();
  }



  /**
   * Is this a choice-type whose actual generics include ref?  If so, a field for
   * all the refs will be needed.
   */
  public boolean isChoiceWithRefs()
  {
    boolean hasRefs = false;

    if (_choiceGenerics != null)
      {
        for (Clazz c : _choiceGenerics)
          {
            hasRefs = hasRefs || c.isRef();
          }
      }

    return hasRefs;
  }


  /**
   * Is this a choice-type whose actual generics are all refs or stateless
   * values? If so, no tag will be added, but ChoiceIdAsRef can be used.
   *
   * In case this is a choice of stateless value without any references, the
   * result will be false since in this case, it is better to use the an integer
   * stored in the tag.
   */
  public boolean isChoiceOfOnlyRefs()
  {
    boolean hasNonRefsWithState = false;

    if (_choiceGenerics != null)
      {
        for (Clazz c : _choiceGenerics)
          {
            hasNonRefsWithState = hasNonRefsWithState || (!c.isRef() && !c.isUnitType() && !c.isVoidType());
          }
      }

    return isChoiceWithRefs() && !hasNonRefsWithState;
  }


  /**
   * Obtain the actual classes of a choice.
   *
   * @return the actual clazzes of this choice clazz, in the order they appear
   * as actual generics.
   */
  private List<Clazz> determineChoiceGenerics()
  {
    List<Clazz> result;

    if (isChoice())
      {
        result = new List<>();
        for (var t : actualGenerics(feature().choiceGenerics()))
          {
            result.add(newClazz(t));
          }
      }
    else
      {
        result = null;
      }

    return result;
  }


  /**
   * Obtain the actual classes of a choice.
   *
   * @return the actual clazzes of this choice clazz, in the order they appear
   * as actual generics.
   */
  public List<Clazz> choiceGenerics()
  {
    if (PRECONDITIONS) require
      (isChoice());

    return _choiceGenerics;
  }



  /**
   * Mark clazz cl and all its outers as instantiated.
   * In case it is a choice, mark all its
   * choice generics as instantiated as well.
   *
   * @param cl the clazz we want to mark as instantiated
   *
   * @param at where the instantiation is taking place
   */
  private void markInstantiated(Clazz cl, HasSourcePosition at)
  {
    cl.instantiated(at);
    if (cl.isChoice())
      {
        // e.g. `java.call_c0` may return `outcome x`
        for (var cg : cl.choiceGenerics())
          {
            markInstantiated(cg, at);
          }
      }
    else
      {
        var o = cl.outer();
        while (o != null)
          {
            o.instantiated(at);
            o = o.outer();
          }
      }
  }


  /**
   * Mark this as instantiated at given source code position.
   *
   * @param at gives the position in the source code that causes this instantiation.
   */
  void instantiated(HasSourcePosition at)
  {
    if (PRECONDITIONS) require
      (at != null);

    if (!_isInstantiated && !isVoidType())
      {
        _isInstantiated = true;
      }
  }


  /**
   * Check of _outer is instantiated.
   */
  private boolean isOuterInstantiated()
  {
    var o = outer();
    return o == null ||

      // NYI: Once Clazz.normalize() is implemented better, a clazz C has
      // to be considered instantiated if there is any clazz D that
      // normalize() would replace by C if it occurs as an outer clazz.
      o._s == SpecialClazzes.c_Any    ||

      o._isNormalized ||

      o.isInstantiated();
  }


  /**
   * Flag to detect endless recursion between isInstantiated() and
   * isRefWithInstantiatedHeirs(). This may happen in a clazz that inherits from
   * its outer clazz.
   */
  private int _checkingInstantiatedHeirs = 0;


  /**
   * Helper for isInstantiated to check if outer clazz this is a ref and there
   * are heir clazzes of this that are refs and that are instantiated.
   *
   * @return true iff this is a ref and there exists an heir of this that is
   * instantiated.
   */
  public boolean hasInstantiatedHeirs()
  {
    var result = false;
    for (var h : heirs())
      {
        h._checkingInstantiatedHeirs++;
        result = result
          || h != this && h.isInstantiated();
        h._checkingInstantiatedHeirs--;
      }
    return result;
  }


  /**
   * Is this clazz instantiated?  This tests this._isInstantiated and,
   * recursively, _outer.isInstantiated().
   */
  public boolean isInstantiated()
  {
    return _isInstantiated
      && (_checkingInstantiatedHeirs > 0
          || (isOuterInstantiated()
              || isChoice()
              || outer().isRef() && outer().hasInstantiatedHeirs()));
  }


  /**
   * For an open generic type ft find the actual type parameters within this
   * clazz.  The resulting list could be empty.
   *
   * @param ft the type that is an open generic
   *
   * @param fouter the outer feature where ft is used. This might be an heir of
   * _outer.feature() in case ft is the result type of an inherited feature.
   */
  List<AbstractType> replaceOpen(AbstractType ft, AbstractFeature fouter)
  {
    if (PRECONDITIONS) require
      (Errors.any() || ft.isOpenGeneric());

    List<AbstractType> types;
    var inh = outer() == null ? null : outer().feature().tryFindInheritanceChain(fouter.outer());
    if (inh != null &&
        inh.size() > 0)
      {
        var typesa = new AbstractType[] { ft };
        typesa = fouter.handDown(null, typesa, outer().feature());
        types = new List<AbstractType>();
        for (var t : typesa)
          {
            types.add(t);
          }
      }
    else if (ft.isOpenGeneric() && feature().generics() == ft.genericArgument().formalGenerics())
      {
        types = ft.genericArgument().replaceOpen(_type.generics());
      }
    else if (outer() != null)
      {
        types = outer().replaceOpen(ft, fouter);
      }
    else
      {
        if (CHECKS) check
          (Errors.any());
        types = new List<>();
      }
    return types;
  }


  /**
   * For a feature with an open generic result type, find the number of actual
   * instances existing in this clazz.
   *
   * @param a an inner feature of this of open generic type.
   */
  public int replaceOpenCount(AbstractFeature a)
  {
    if (PRECONDITIONS) require
      (Errors.any() || a != Types.f_ERROR || a.resultType().isOpenGeneric());

    return a == Types.f_ERROR ? 0 : replaceOpen(a.resultType(), a.outer()).size();
  }


  /**
   * From a set of inner features of this clazz, extract used fields and create
   * the corresponding clazzes for these fields.
   *
   * Fields with open generic result type will be replaced by 0 or more clazzes
   * depending on the number of actual type parameters the open generic is
   * replaced with.
   *
   * @param feats a collection of features the fields will be extracted from.
   *
   * @return NO_CLAZZES in case there are no fields remaining, an array of
   * fields otherwise.
   */
  Clazz[] actualFields()
  {
    var fields = new List<Clazz>();
    for(var ix = 0; ix < _inner.size(); ix++)
      {
        var field = id2clazz(_inner.get(ix));
        if (!isVoidOrUndefined() &&
            field.feature().isField()
            // NYI: needed?  field == findRedefinition(field) && // NYI: proper field redefinition handling missing, see tests/redef_args/*
            )
          {
            fields.add(field);
          }
      }
    var result = fields.size() == 0 ? NO_CLAZZES : fields.toArray(new Clazz[fields.size()]);
    return result;
  }



  /**
   * From a set of inner features of this clazz, extract used fields and create
   * the corresponding clazzes for these fields.
   *
   * Fields with open generic result type will be replaced by 0 or more clazzes
   * depending on the number of actual type parameters the open generic is
   * replaced with.
   *
   * @param feats a collection of features the fields will be extracted from.
   *
   * @return NO_CLAZZES in case there are no fields remaining, an array of
   * fields otherwise.
   */
  Clazz[] actualFields(Collection<AbstractFeature> feats)
  {
    var fields = new List<Clazz>();
    for (var field: feats)
      {
        if (!this.isVoidOrUndefined() &&
            field.isField()
            // NYI: needed?  field == findRedefinition(field) && // NYI: proper field redefinition handling missing, see tests/redef_args/*
            // (true || _clazzes.isUsed(field))
            )
          {
            if (field.isOpenGenericField())
              {
                var n = replaceOpenCount(field);
                for (var i = 0; i < n; i++)
                  {
                    fields.add(id2clazz(lookup(new FeatureAndActuals(field), i, SourcePosition.builtIn, false, true)));
                  }
              }
            else
              {
                fields.add(id2clazz(lookup(field)));
              }
          }
      }
    return fields.size() == 0 ? NO_CLAZZES : fields.toArray(new Clazz[fields.size()]);
  }



  /**
   * Set of fields in this clazz, including inherited and artificially added fields.
   *
   * @return the set of fields, NO_CLAZZES if none. Never null.
   */
  Clazz[] fields()
  {
    if (_fields == null)
      {
        _fields =
          /* NYI: maybe do this only for clazzKind(_id) == FeatureKind.Routine?

          clazzIsChoice(_id)              ||
          // note that intrinsics may have fields that are used in the intrinsic's pre-condition!
          false && isRef() // NYI: would be good to add isRef() here and create _fields only for value types, does not work with C backend yet
          ? NO_CLAZZES
          :
          */
          actualFields();
      }
    return isRef() ? NO_CLAZZES : _fields;   // NYI: CLEANUP: Remove the difference between _fields and fileds() wrt isRef()!
  }


  /**
   * Get the argument fields of this routine
   *
   * @return the argument fields.
   */
  public Clazz[] argumentFields()
  {
    if (PRECONDITIONS) require
      (switch (clazzKind(_id))
               {
                 case Routine,
                      Intrinsic,
                      Abstract,
                      Field,
                      Native -> true;
                 case Choice -> false;
               });

    return _argumentFields;
  }


  /**
   * For a field, determine its index in _outer.fields().
   *
   * @return index of this in fields()
   */
  public int fieldIndex()
  {
    if (PRECONDITIONS) require
      (feature().isField());

    var ignore = outer().fields();
    int i = 0;
    for (var f : outer()._fields)
      {
        if (f == this)
          {
            return i;
          }
        i++;
      }
    throw new Error("Clazz.fieldIndex() did not find field " + this + " in " + outer());
  }



    @Override
    public String toString()
    {
      return clazzAsString(_id);
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
  Clazz type2clazz(AbstractType thiz)
  {
    if (PRECONDITIONS) require
      (Errors.any() || !thiz.dependsOnGenerics(),
       !thiz.isThisType());

    var result = _clazzesForTypes.get(thiz);
    if (result == null)
      {
        var ot = thiz.outer();
        var oc = ot != null ? type2clazz(ot) : null;
        result = newClazz(oc, thiz);
        _clazzesForTypes.put(thiz, result);
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


  /*----------------------------  constants  ----------------------------*/


  /**
   * property- or env-var-controlled flag to enable debug output whenever a
   * new clazz is created.
   *
   * To enable this, use fz with
   *
   *   dev_flang_fuir_GeneratingFUIR_SHOW_NEW_CLAZZES=true
   */
  static final boolean SHOW_NEW_CLAZZES = FuzionOptions.boolPropertyOrEnv("dev.flang.fuir.GeneratingFUIR.SHOW_NEW_CLAZZES");



  /*----------------------------  variables  ----------------------------*/


  private final FrontEnd _fe;

  private final TreeMap<Clazz, Clazz> _clazzesTM;
  private final HashMap<Clazz, Clazz> _clazzesHM;


  /**
   * For each site, this gives the clazz id of the clazz that contains the code at that site.
   */
  private final IntArray _siteClazzes;
  private final IntMap<Clazz> _accessedClazz;
  private final IntMap<Clazz> _accessedTarget;
  final IntMap<int[]> _accessedClazzes;


  private final LibraryModule _mainModule;


  private final int _mainClazz;
  private final int _universe;
  private Clazz universe() { return id2clazz(_universe); }


  private final List<Clazz> _clazzes;

  private final List<List<AbstractCall>> _inh;


  private final Clazz[] _specialClazzes;

  private final Map<AbstractType, Clazz> _clazzesForTypes;

  private boolean _lookupDone;

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create FUIR from given Clazz instance.
   */
  public GeneratingFUIR(FrontEnd fe, MIR mir)
  {
    _fe = fe;
    _lookupDone = false;
    _clazzesTM = new TreeMap<Clazz, Clazz>();
    _clazzesHM = new HashMap<Clazz, Clazz>();
    _siteClazzes = new IntArray();
    _accessedClazz = new IntMap<>();
    _accessedClazzes = new IntMap<>();
    _accessedTarget = new IntMap<>();
    _mainModule = fe.mainModule();
    _clazzes = new List<>();
    _specialClazzes = new Clazz[SpecialClazzes.values().length];
    _universe  = newClazz(null, mir.universe().selfType())._id;
    doesNeedCode(_universe);
    _mainClazz = newClazz(mir.main().selfType())._id;
    doesNeedCode(_mainClazz);
    _inh = new List<>();
    _clazzesForTypes = new TreeMap<>();
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
    original._lookupDone = true;
    _lookupDone = true;
    _clazzesTM = original._clazzesTM;
    _clazzesHM = original._clazzesHM;
    _siteClazzes = original._siteClazzes;
    _accessedClazz = original._accessedClazz;
    _accessedClazzes = original._accessedClazzes;
    _accessedTarget = original._accessedTarget;
    _mainModule = original._mainModule;
    _mainClazz = original._mainClazz;
    _universe = original._universe;
    _clazzes = original._clazzes;
    _specialClazzes = original._specialClazzes;
    _inh = original._inh;
    _clazzesForTypes = original._clazzesForTypes;
  }


  /*-----------------------------  methods  -----------------------------*/



  private Clazz newClazz(AbstractType t)
  {
    var o = t.outer();
    return newClazz(o == null ? null : newClazz(o), t);
  }
  private Clazz newClazz(Clazz outerR, AbstractType actualType)
  {
    Clazz result;

    var outer = outerR;
    Clazz o = outerR;
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
                c = c.outer();
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
        o = o.outer();
      }



    var t = actualType;

    var cl = new Clazz(outerR, t, CLAZZ_BASE + _clazzes.size());
    // var existing = _clazzesTM.get(cl);
    var existing = _clazzesHM.get(cl);
    if (existing != null)
      {
        result = existing;
      }
    else
      {
        result = cl;
        _clazzes.add(cl);
        _clazzesTM.put(cl, cl);
        _clazzesHM.put(cl, cl);

        if (outerR != null)
          {
            outerR.addInner(result);
          }

        var s = SpecialClazzes.c_NOT_FOUND;
        if (cl.isRef() == cl._feature.isThisRef())  // not an boxed or explicit value clazz
          {
            // NYI: OPTIMIZATION: Avoid creating all feature qualified names!
            s = switch (cl._feature.qualifiedName())
              {
              case "Any"               -> SpecialClazzes.c_Any         ;
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
              case "unit"              -> SpecialClazzes.c_unit        ;
              case "void"              -> SpecialClazzes.c_void        ;
              case "bool"              -> SpecialClazzes.c_bool        ;
              case "TRUE"              -> SpecialClazzes.c_TRUE        ;
              case "FALSE"             -> SpecialClazzes.c_FALSE       ;
              case "Const_String"      -> SpecialClazzes.c_Const_String;
              case "String"            -> SpecialClazzes.c_String      ;
              case "error"             -> SpecialClazzes.c_error       ;
              case "fuzion"            -> SpecialClazzes.c_fuzion      ;
              case "fuzion.sys"        -> SpecialClazzes.c_fuzion_sys  ;
              case "fuzion.sys.Pointer"-> SpecialClazzes.c_sys_ptr     ;
              default                  -> SpecialClazzes.c_NOT_FOUND   ;
              };
            if (s != SpecialClazzes.c_NOT_FOUND)
              {
                _specialClazzes[s.ordinal()] = result;
              }
          }
        cl._s = s;
        if (SHOW_NEW_CLAZZES) System.out.println("NEW CLAZZ "+cl);
        cl.init();

        if (cl.feature().isField() && outerR.isRef() && !outerR.isBoxed())
          { // NYI: CLEANUP: Duplicate clazzes for fields in corresponding value
            // instance.  This is currently needed for the C backend only since
            // that backend creates ref clazzes by embedding the underlying
            // value instance in the ref clazz' struct:
            var ignore = newClazz(outerR.asValue(), actualType);
          }

        result.registerAsHeir();
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

    var cl = newClazz(outer, actualType);
    return cl;
  }



  private Clazz id2clazz(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return _clazzes.get(cl - CLAZZ_BASE);
  }
  private Clazz clazz(int cl)
  {
    if (PRECONDITIONS) require
      (cl == NO_CLAZZ || cl >= CLAZZ_BASE,
       cl == NO_CLAZZ || cl < CLAZZ_BASE + _clazzes.size());

    return cl == NO_CLAZZ ? null : _clazzes.get(cl - CLAZZ_BASE);
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
            var typePars = outerClazz.actualGenerics(at);
            result = tclazz.lookup(c, typePars, false).resultClazz();
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
        result = id2clazz(_universe);
      }

    else if (e instanceof Constant c)
      {
        result = outerClazz.handDown(c.typeOfConstant(), inh, e);
      }

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
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return switch (c._feature.kind())
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
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    var res = c._feature.featureName().baseName();
    res = res + c._type.generics()
      .toString(" ", " ", "", t -> t.asStringWrapped(false));
    return res;
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
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).resultClazz()._id;
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
       cl < CLAZZ_BASE + _clazzes.size());

    var cc = id2clazz(cl);
    return cc.feature().qualifiedName();
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
      (cl == NO_CLAZZ || cl >= CLAZZ_BASE,
       cl == NO_CLAZZ || cl < CLAZZ_BASE + _clazzes.size());

    return cl == NO_CLAZZ
      ? "-- no clazz --"
      : id2clazz(cl).asString(false);
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
       cl < CLAZZ_BASE + _clazzes.size());

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
       cl < CLAZZ_BASE + _clazzes.size());

    var sb = new StringBuilder();
    sb.append(clazzAsString(cl))
      .append("(");
    var o = clazzOuterClazz(cl);
    if (o != -1)
      {
        sb.append("outer ")
          .append(clazzAsString(o));
      }
    for (var i = 0; i < clazzArgCount(cl); i++)
      {
        var ai = clazzArg(cl,i);
        sb.append(o != -1 || i > 0 ? ", " : "")
          .append(clazzBaseName(ai))
          .append(" ")
          .append(clazzAsString(clazzResultClazz(ai)));
      }
    sb.append(") ")
      .append(clazzAsString(clazzResultClazz(cl)));
    return sb.toString();
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
       cl < CLAZZ_BASE + _clazzes.size());

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
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).fields().length;
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
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size(),
       0 <= i,
       i < clazzNumFields(cl));

    return id2clazz(cl).fields()[i]._id;
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
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).feature().isOuterRef();
  }


  /**
   * Check if field does not store the value directly, but a pointer to the value.
   *
   * @param field a clazz id, not necessarily a field
   *
   * @return true iff the field is an outer ref field that holds an address of
   * an outer value, false for normal fields our outer ref fields that store the
   * outer ref or value directly.
   */
  @Override
  public boolean clazzFieldIsAdrOfValue(int field)
  {
    if (PRECONDITIONS) require
      (field >= CLAZZ_BASE,
       field < CLAZZ_BASE + _clazzes.size());

    var fc = id2clazz(field);
    var f = fc.feature();
    return f.isOuterRef() &&
      !fc.resultClazz().isRef() &&
      !fc.resultClazz().isUnitType() &&
      !fc.resultClazz().feature().isBuiltInPrimitive();
  }


  /**
   * NYI: CLEANUP: Remove? This seems to be used only for naming fields, maybe we could use clazzId2num(field) instead?
   */
  @Override
  public int fieldIndex(int field)
  {
    if (PRECONDITIONS) require
      (field >= CLAZZ_BASE,
       field < CLAZZ_BASE + _clazzes.size(),
       clazzKind(field) == FeatureKind.Field);

    return id2clazz(field).fieldIndex();
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
       cl < CLAZZ_BASE + _clazzes.size());

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
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return switch (c._feature.kind())
      {
      case Choice -> c.choiceGenerics().size();
      default     -> -1;
      };
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
       cl < CLAZZ_BASE + _clazzes.size(),
       i >= 0 && i < clazzNumChoices(cl));

    var cc = id2clazz(cl);
    var cg = cc.choiceGenerics().get(i);
    var res = cg.isRef()          ||
              cg.isInstantiated()    ? cg
                                     : id2clazz(clazz(SpecialClazzes.c_void));
    return res._id;
  }


  /**
   * Is this a choice type with some elements of ref type?
   *
   * NYI: CLEANUP: Used by C and interpreter backends only. Remove?
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
       cl < CLAZZ_BASE + _clazzes.size());

    var cc = id2clazz(cl);
    return cc.isChoiceWithRefs();
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
       cl < CLAZZ_BASE + _clazzes.size());

    var cc = id2clazz(cl);
    return cc.isChoiceOfOnlyRefs();
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
       cl < CLAZZ_BASE + _clazzes.size());

    if (!clazzIsRef(cl))
      {
        // NYI: this is sometimes (e.g. in tests/inheritance) called for non-ref
        // clazzes. Check what this is needed for, seems not to make not so much
        // sense.
      }

    var c = id2clazz(cl);
    var result = new List<Clazz>();
    for (var h : c.heirs())
      {
        if (h.isInstantiated())
          {
            result.add(h);
          }
      }
    var res = new int[result.size()];
    for (var i = 0; i < result.size(); i++)
      {
        res[i] = result.get(i)._id;
        if (CHECKS) check
          (res[i] != -1);
      }
    return res;
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
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return
      switch (clazzKind(c._id))
        {
        case Routine,
             Intrinsic,
             Abstract,
             Native -> c.argumentFields().length;
        case Field,
             Choice -> 0;
        };
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
       cl < CLAZZ_BASE + _clazzes.size(),
       arg >= 0,
       arg < clazzArgCount(cl));

    var c = id2clazz(cl);
    var rc = c.argumentFields()[arg].resultClazz();
    return rc._id;
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
       cl < CLAZZ_BASE + _clazzes.size(),
       arg >= 0,
       arg < clazzArgCount(cl));

    var c = id2clazz(cl);
    var af = c.argumentFields()[arg];
    return af._id;
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
       cl < CLAZZ_BASE + _clazzes.size());

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
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    var or = c.outerRef();
    return or == null || c.outer().isUnitType() ? NO_CLAZZ : or._id;
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
       cl < CLAZZ_BASE + _clazzes.size(),
       Errors.any() ||
       !_lookupDone ||
       clazzNeedsCode(cl) ||
       cl == clazz_Const_String() ||
       cl == clazz_Const_String_utf8_data() ||
       cl == clazz_array_u8() ||
       cl == clazz_fuzionSysArray_u8() ||
       cl == clazz_fuzionSysArray_u8_data() ||
       cl == clazz_fuzionSysArray_u8_length() ||
       cl == clazz_fuzionJavaObject() ||
       cl == clazz_fuzionJavaObject_Ref()
       );

    var c = id2clazz(cl);
    var result = c._code;
    if (result == NO_SITE)
      {
        if (!_lookupDone)
          {
            c.doesNeedCode();
          }
        result = addCodeX(cl, c);
        c._code = result;
      }
    return result;
  }


  int addCodeX(int cl, Clazz c)
  {
    var code = new List<Object>();
    var inhe = new List<List<AbstractCall>>();
    addCodeI(cl, c, code, inhe, c.feature(), NO_INH);
    check
      (code.size() == inhe.size(),
       _allCode.size() == _inh.size());
    var result = addCode(code);
    _inh.addAll(inhe);
    _inh.add(null);
    check
      (_allCode.size() == _inh.size());
    while (_siteClazzes.size() < _allCode.size())
      {
        _siteClazzes.add(cl);
      }
    check
      (_allCode.size() == _siteClazzes.size());
    // NYI:     recordClazzForSitesOfRecentlyAddedCode(cl);
    /* NYI:
    var result = _nextSite;
    _nextSite += m.codeSize(code) + 1;
    for (var i = result; i < _nextSite; i++)
      {
        _siteClazzes.add(cl);
      }
    */
    //System.out.println("Code added for "+c.asString(true)+" "+(_allCode.size() - (result - SITE_BASE)));
    return result;
  }

  void addCodeI(int cl, Clazz c, List<Object> code, List<List<AbstractCall>> inhe, LibraryFeature ff, List<AbstractCall> inh)
  {
    if (!clazzIsVoidType(cl))
      {
        //System.out.println("add code "+c);
        //var ff = c.feature();
        for (var p: ff.inherits())
          {
            var pf = (LibraryFeature) p.calledFeature();
            var of = pf.outerRef();
            // var or = (of == null) ? null : (Clazz) cc._inner.get(new FeatureAndActuals(of, new List<>()));  // NYI: ugly cast
            Clazz or = (of == null) ? null : id2clazz(c.lookup(new FeatureAndActuals(of, new List<>()), p));
            var needsOuterRef = (or != null && (!or.resultClazz().isUnitType()));
            //System.out.println("++++++++++++++++++ needsOuterRef "+c+" is "+needsOuterRef+" pf "+pf.qualifiedName()+" of "+(of == null ? "null" : of.qualifiedName()) + " pf: "+pf.qualifiedName() + " ff is "+ff.qualifiedName());
            toStack(code, p.target(), !needsOuterRef /* dump result if not needed */);
            while (inhe.size() < code.size()) { inhe.add(inh); }
            while (_inh.size() < _allCode.size()) { _inh.add(inh); }
            if (needsOuterRef)
              {
                code.add(ExprKind.Current);
                code.add(or);  // field clazz means assignment to field
              }
            if (CHECKS) check
              (p.actuals().size() == p.calledFeature().valueArguments().size());

            AbstractFeature cf = pf;
            var n = p.actuals().size();
            var argFields = new Clazz[n];
            for (var i = 0; i < n; i++)
              {
                if (i >= cf.valueArguments().size())
                  {
                    if (CHECKS) check
                      (Errors.any());
                  }
                else
                  {
                    var cfa = cf.valueArguments().get(i);
                    argFields[i] = id2clazz(c.lookup(cfa, p/* NYI: , _clazzes.isUsedAt(f) */));
                  }
              }
            //_parentCallArgFields.put(c.globalIndex(), argFields);
            // var argFields = c._parentCallArgFields.get(p.globalIndex());
            for (var i = 0; i < p.actuals().size(); i++)
              {
                var a = p.actuals().get(i);
                toStack(code, a);
                while (inhe.size() < code.size()) { inhe.add(inh); }
                while (_inh.size() < _allCode.size()) { _inh.add(inh); }
                code.add(ExprKind.Current);
                // Field clazz means assign value to that field
                code.add(argFields[i]);
              }

            var inh1 = new List<AbstractCall>();
            inh1.add(p);
            inh1.addAll(inh);
            addCodeI(cl, c, code, inhe, pf, inh1);
          }
        toStack(code, ff.code());
        while (inhe.size() < code.size()) { inhe.add(inh); }
        while (_inh.size() < _allCode.size()) { _inh.add(inh); }
      }
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
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return c.needsCode();
  }


  void doesNeedCode(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    c.doesNeedCode();
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
       cl < CLAZZ_BASE + _clazzes.size());

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
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return c.isRef();
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
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return c.isRef() && !c.feature().isThisRef();
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
       cl < CLAZZ_BASE + _clazzes.size());

    var cc = id2clazz(cl);
    var vcc = id2clazz(cl).asValue();
    if (vcc.isRef())
      {
        throw new Error("vcc.isRef in clazzAsValue for "+clazzAsString(cl)+" is "+vcc);
      }
    var vc0 = id2clazz(cl).asValue()._id;
    var vc = vcc._id;
    if (clazzIsRef(vc))
      {
        throw new Error("clazzAsValue for "+clazzAsString(cl)+" is "+clazzAsString(vc)+" "+cl+" "+vc+"="+vc0+" "+System.identityHashCode(cc)+" "+System.identityHashCode(vcc));
      }
    return vc;
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
       cl < CLAZZ_BASE + _clazzes.size());

    var c = id2clazz(cl);
    return c.typeName().getBytes(StandardCharsets.UTF_8);
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
       cl < CLAZZ_BASE + _clazzes.size());

    var cc = id2clazz(cl);
    return cc.feature().isTypeParameter() ? cc.typeParameterActualType()._id
                                          : NO_CLAZZ;
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
       cl < CLAZZ_BASE + _clazzes.size());

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
       cl < CLAZZ_BASE + _clazzes.size());

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
    if (result == null)
      {
        if (s == SpecialClazzes.c_universe)
          {
            result = id2clazz(_universe);
          }
        else
          {
            var o = clazz(s._outer);
            var oc = id2clazz(o);
            var of = oc._feature;
            var f = (LibraryFeature) of.get(of._libModule, s._name, s._argCount);
            result = newClazz(oc, f.selfType());
            if (CHECKS) check
              (f.isThisRef() == result.isRef());
          }
        _specialClazzes[s.ordinal()] = result;
      }
    return result._id;
  }


  /**
   * Get the id of clazz Any.
   *
   * @return clazz id of clazz Any
   */
  @Override
  public int clazzAny()
  {
    return clazz(SpecialClazzes.c_Const_String);
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
    return clazz(SpecialClazzes.c_Const_String);
  }


  /**
   * Get the id of clazz Const_String.utf8_data
   *
   * @return the id of Const_String.utf8_data or -1 if that clazz was not created.
   */
  @Override
  public int clazz_Const_String_utf8_data()
  {
    return clazz(SpecialClazzes.c_CS_utf8_data);
  }


  /**
   * Get the id of clazz `array u8`
   *
   * @return the id of Const_String.array or -1 if that clazz was not created.
   */
  @Override
  public int clazz_array_u8()
  {
    var utf8_data = clazz_Const_String_utf8_data();
    return clazzResultClazz(utf8_data);
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>
   *
   * @return the id of fuzion.sys.array<u8> or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8()
  {
    var a8 = clazz_array_u8();
    var ia = lookup_array_internal_array(a8);
    var res = clazzResultClazz(ia);
    return res;
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>.data
   *
   * @return the id of fuzion.sys.array<u8>.data or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8_data()
  {
    var sa8 = clazz_fuzionSysArray_u8();
    return lookup_fuzion_sys_internal_array_data(sa8);
  }


  /**
   * Get the id of clazz fuzion.sys.array<u8>.length
   *
   * @return the id of fuzion.sys.array<u8>.length or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8_length()
  {
    var sa8 = clazz_fuzionSysArray_u8();
    return lookup_fuzion_sys_internal_array_length(sa8);
  }


  /**
   * Get the id of clazz fuzion.java.Java_Object
   *
   * @return the id of fuzion.java.Java_Object or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionJavaObject()
  {
    return newClazz(Types.resolved.f_fuzion_Java_Object.selfType())._id;
  }


  /**
   * Get the id of clazz fuzion.java.Java_Object.Java_Ref
   *
   * @return the id of fuzion.java.Java_Object.Java_Ref or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionJavaObject_Ref()
  {
    return newClazz(Types.resolved.f_fuzion_Java_Object_Ref.selfType())._id;
  }


  /**
   * Get the id of clazz error
   *
   * @return the id of error or -1 if that clazz was not created.
   */
  @Override
  public int clazz_error()
  {
    return clazz(SpecialClazzes.c_error);
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
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).lookup(Types.resolved.f_fuzion_Java_Object_Ref);
  }


  /**
   * Check if the given clazz is a --possibly inherited--
   * `fuzion.java.Java_Object.Java_Ref` field.
   *
   * NYI: CLEANUP: #3927: Remove once #3927 is fixed.
   *
   * @param cl a clazz id that should be checked, must not be NO_CLAZZ.
   */
  @Override
  public boolean isJavaRef(int cl)
  {
    var f = id2clazz(cl)._feature;
    return isJavaRef(f);
  }


  /**
   * Helper for isJavaRef to check is this is or redefineds
   * `fuzion.java.Java_Object.Java_Ref` field.
   *
   * NYI: CLEANUP: #3927: Remove once #3927 is fixed.
   */
  boolean isJavaRef(AbstractFeature f)
  {
    var result = f == Types.resolved.f_fuzion_Java_Object_Ref;
    if (!result)
      {
        for (var rf : f.redefines())
          {
            result = result || isJavaRef(rf);
          }
      }
    return result;
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
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).lookup(Types.resolved.f_Function_call);
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
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).lookup(Types.resolved.f_effect_static_finally);
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
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).lookup(Types.resolved.f_concur_atomic_v);
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
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size(),
       id2clazz(cl).feature() == Types.resolved.f_array);

    return id2clazz(cl).lookup(Types.resolved.f_array_internal_array);
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
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size(),
       id2clazz(cl).feature() == Types.resolved.f_fuzion_sys_array);

    return id2clazz(cl).lookup(Types.resolved.f_fuzion_sys_array_data);
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
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size(),
       id2clazz(cl).feature() == Types.resolved.f_fuzion_sys_array);

    return id2clazz(cl).lookup(Types.resolved.f_fuzion_sys_array_length);
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
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl).lookup(Types.resolved.f_error_msg);
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
       cl < CLAZZ_BASE + _clazzes.size());

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
       cl < CLAZZ_BASE + _clazzes.size());

    return id2clazz(cl)._s == SpecialClazzes.c_void;
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
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return
      !clazzIsUnitType(cl) &&
      !clazzIsVoidType(cl) &&
      cl != clazzUniverse();
  }


  /**
   * Add entries of type ExprKind created from the given expression (and its
   * nested expressions) to list l. pop the result in case dumpResult==true.
   *
   * @param l list of ExprKind that should be extended by s's expressions
   *
   * @param e a expression.
   *
   * @param dumpResult flag indicating that we are not interested in the result.
   */
  @Override
  protected void toStack(List<Object> l, Expr e, boolean dumpResult)
  {
    if ((e instanceof AbstractCall ||
         e instanceof InlineArray    ) && isConst(e))
      {
        if (!dumpResult)
          {
            l.add(e.asCompileTimeConstant());
          }
      }
    else
      {
        super.toStack(l, e, dumpResult);
      }
  }


  /**
   * Is this a compile-time constant?
   *
   * @param o an Object from the IR-Stack.
   *
   * @return true iff `o` is an Expr and can be turned into a compile-time constant.
   */
  private boolean isConst(Object o)
  {
    if (PRECONDITIONS) require
      (o instanceof Expr || o instanceof ExprKind);

    return o instanceof InlineArray iai && isConst(iai)
        || o instanceof Constant
        || o instanceof AbstractCall ac && isConst(ac)
        || o instanceof AbstractBlock ab && ab._expressions.size() == 1 && isConst(ab.resultExpression());
  }


  /**
   * Can this array be turned into a compile-time constant?
   */
  private boolean isConst(InlineArray ia)
  {
    return
      !ia.type().dependsOnGenerics() &&
      !ia.type().containsThisType() &&
      // some backends have special handling for array void.
      !ia.elementType().isVoid() &&
      ia._elements
        .stream()
        .allMatch(el -> {
          var s = new List<>();
          super.toStack(s, el);
          return s
            .stream()
            .allMatch(x -> isConst(x));
        });
  }


  /**
   * Can this call be turned into a constant?
   *
   * @param ac the call to be analyzed.
   *
   * @return true iff the call is suitable to be turned into
   * a compile time constant.
   */
  private boolean isConst(AbstractCall ac)
  {
    var result =
      !ac.isInheritanceCall() &&
      ac.calledFeature().isConstructor() &&
      // contains no fields
      ac.calledFeature().code().containsOnlyDeclarations() &&
      // we are calling a value type feature
      !ac.calledFeature().selfType().isRef() &&
      // only features without args and no fields may be inherited
      ac.calledFeature().inherits().stream().allMatch(c -> c.calledFeature().arguments().isEmpty() && c.calledFeature().code().containsOnlyDeclarations()) &&
      // no unit   // NYI we could allow units that does not contain declarations
      ac.actuals().size() > 0 &&
      ac.actuals().stream().allMatch(x -> isConst(x));

    if (result)
      {
        var s = new List<>();
        super.toStack(s, ac, false);
        result = s
          .stream()
          .allMatch(x -> x == ac || isConst(x));
      }
    return result;
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
    var cc = id2clazz(cl);
    return cc.actualTypeParameters()[gix]._id;
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
    String res;
    if (s == NO_SITE)
      {
        res = "** NO_SITE **";
      }
    else if (s >= SITE_BASE && (s - SITE_BASE < _allCode.size()))
      {
        var cl = clazzAt(s);
        var p = sitePos(s);
        res = clazzAsString(cl) + "(" + clazzArgCount(cl) + " args)" + (p == null ? "" : " at " + sitePos(s).show());
      }
    else
      {
        res = "ILLEGAL site " + s;
      }
    return res;

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
       s < SITE_BASE + _allCode.size(),
       withinCode(s));

    ExprKind result;
    var e = getExpr(s);
    if (e instanceof Clazz    )  /* Clazz represents the field we assign a value to */
      {
        result = ExprKind.Assign;
        // System.out.println("###################### codeAt is "+result+" to "+e);
      }
    else
      {
        result = exprKind(e);
        // System.out.println("###################### codeAt is "+result+" at "+(e instanceof HasSourcePosition he ? he.pos().show() : e));
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
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Tag);

    var cl = clazzAt(s);
    var outerClazz = clazz(cl);
    var t = (Tag) getExpr(s);
    Clazz vc = clazz(t._value, outerClazz, _inh.get(s - SITE_BASE));
    return vc._id;
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
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Tag);

    var cl = clazzAt(s);
    var outerClazz = clazz(cl);
    var t = (Tag) getExpr(s);
    var tc = outerClazz.handDown(t._taggedType, _inh.get(s - SITE_BASE), t);
    tc.instantiated(t);
    return tc._id;
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
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Tag);

    var t = (Tag) getExpr(s);
    return t.tagNum();
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
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       withinCode(s),
       codeAt(s) == ExprKind.Env);

    var cl = clazzAt(s);
    var outerClazz = clazz(cl);
    var v = (Env) getExpr(s);
    Clazz vcl = clazz(v, outerClazz, _inh.get(s - SITE_BASE));
    return vcl == null ? -1 : vcl._id;
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
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Box);

    var cl = clazzAt(s);
    var outerClazz = id2clazz(cl);
    var b = (Box) getExpr(s);
    Clazz vc = clazz(b._value, outerClazz, _inh.get(s - SITE_BASE));
    Clazz rc = vc;
    return vc._id;
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
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Box);

    var cl = clazzAt(s);
    var outerClazz = id2clazz(cl);
    var b = (Box) getExpr(s);
    Clazz vc = clazz(b._value, outerClazz, _inh.get(s - SITE_BASE));
    Clazz rc = outerClazz.handDown(b.type(), -1, _inh.get(s - SITE_BASE), b);
    if (rc.isRef() &&
        outerClazz.feature() != Types.resolved.f_type_as_value) // NYI: ugly special case
      {
        rc = vc.asRef();
      }
    else
      {
        rc = vc;
      }
    return rc._id;
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


  /*
   * Is vc.asRef assignable to ec?
   */
  private boolean asRefAssignable(Clazz ec, Clazz vc)
  {
    return asRefDirectlyAssignable(ec, vc) || asRefAssignableToChoice(ec, vc);
  }


  /*
   * Is vc.asRef directly assignable to ec, i.e. without the need for tagging?
   */
  private boolean asRefDirectlyAssignable(Clazz ec, Clazz vc)
  {
    return ec.isRef() && ec._type.isAssignableFrom(vc.asRef()._type, Context.NONE);
  }


  /*
   * Is ec a choice and vc.asRef assignable to ec?
   */
  private boolean asRefAssignableToChoice(Clazz ec, Clazz vc)
  {
    return ec._type.isChoice() &&
      !ec._type.isAssignableFrom(vc._type, Context.NONE) &&
      ec._type.isAssignableFrom(vc._type.asRef(), Context.NONE);
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
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Call   ||
       codeAt(s) == ExprKind.Assign    );

    var res = _accessedClazz.get(s);
    if (res == null)
      {
        res = accessedClazz(s, null);
        if (res != null)
          {
            _accessedClazz.put(s, res);
          }
        // _accessedClazz = res; -- NYI: need Map from s to res
      }
    return res == null ? NO_CLAZZ : res._id;
  }
  //int _accessedClazz = NO_CLAZZ;

  private Clazz accessedClazz(int s, Clazz tclazz)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Call   ||
       codeAt(s) == ExprKind.Assign    );

    var cl = clazzAt(s);
    var outerClazz = id2clazz(cl);
    var e = getExpr(s);

    Clazz innerClazz = switch (e)
      {
      case AbstractCall   call -> calledInner(call, outerClazz, tclazz, _inh.get(s - SITE_BASE));
      case AbstractAssign a    -> assignedField(outerClazz, tclazz, a, _inh.get(s - SITE_BASE));
      case Clazz          fld  -> fld;
      default -> (Clazz) (Object) new Object() { { if (true) throw new Error("accessedClazz found unexpected Expr " + (e == null ? e : e.getClass()) + "."); } }; /* Java is ugly... */
      };
    return innerClazz == null ? null : innerClazz;
  }


  public Clazz calledTarget(AbstractCall c, Clazz outerClazz, List<AbstractCall> inh)
  {
    if (PRECONDITIONS) require
      (Errors.any() || c.calledFeature() != null && c.target() != null);

    if (c.calledFeature() == null  || c.target() == null)
      {
        return error();  // previous errors, give up
      }

    return clazz(c.target(), outerClazz, inh);
  }


  public Clazz calledInner(AbstractCall c, Clazz outerClazz, Clazz explicitTarget, List<AbstractCall> inh)
  {
    if (PRECONDITIONS) require
      (Errors.any() || c.calledFeature() != null && c.target() != null);

    var outer = outerClazz._feature;
    if (c.calledFeature() == null  || c.target() == null)
      {
        return error();  // previous errors, give up
      }

    var tclazz = explicitTarget == null
      ? calledTarget(c, outerClazz, inh)
      : explicitTarget;

    Clazz innerClazz = null;
    var cf      = c.calledFeature();
    var callToOuterRef = c.target().isCallToOuterRef();
    var dynamic = c.isDynamic() && (tclazz.isRef() || callToOuterRef);
    var needsCode = !dynamic || explicitTarget != null;
    /*
    if (callToOuterRef)
      {
        tclazz._isCalledAsOuter = true;
      }
    */
    var typePars = outerClazz.actualGenerics(c.actualTypeParameters());
    if (!tclazz.isVoidOrUndefined())
      {
        /*
        if (dynamic)
          {
            calledDynamically(cf, typePars);
          }
        */

        innerClazz        = id2clazz(tclazz.lookup(new FeatureAndActuals(cf, typePars), c.select(), c, c.isInheritanceCall(), needsCode));
          {
            if (c.calledFeature() == Types.resolved.f_Type_infix_colon)
              {
                var T = innerClazz.actualTypeParameters()[0];
                cf = T._type.constraintAssignableFrom(Context.NONE, tclazz._type.generics().get(0))
                  ? Types.resolved.f_Type_infix_colon_true
                  : Types.resolved.f_Type_infix_colon_false;
                innerClazz = id2clazz(tclazz.lookup(new FeatureAndActuals(cf, typePars), -1, c, c.isInheritanceCall(), needsCode));
              }
          }

      }
    return innerClazz == null ? error() : innerClazz;
  }



  private Clazz assignedField(Clazz outerClazz, Clazz tclazz, AbstractAssign a, List<AbstractCall> inh)
  {
    if (tclazz == null)
      {
        tclazz = clazz(a._target, outerClazz, inh);
        // target clazz of an assignment is always the value counterpart, even
        // if `tclazz` is a `ref` that itself is never instantiated.
        //
        // NYI: CLEANUP: We might reconsider this and permit `ref` here the same
        // way that `ref` can be the target to a call that reads a
        // field. Currently, back-ends (JVM) rely on this being a value, though.
        tclazz = tclazz.asValue();
      }
    var fc = id2clazz(tclazz.lookup(a._assignedField, a, false));
    if (fc.resultClazz().isUnitType())
      {
        fc = null;
      }
    return fc;
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
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Assign    );

    var cl = clazzAt(s);
    var outerClazz = id2clazz(cl);
    var e = getExpr(s);
    var field = switch (e)
      {
      case AbstractAssign a   ->
      {
        Clazz sClazz = clazz(a._target, outerClazz, _inh.get(s - SITE_BASE));
        var vc = sClazz.asValue();
        var fc = id2clazz(vc.lookup(a._assignedField, a, false));
        yield fc;
      }



      case Clazz          fld -> fld;
      default ->
        (Clazz) (Object) new Object() { { if (true) throw new Error("assignedType found unexpected Expr " + (e == null ? e : e.getClass()) + "."); } } /* Java is ugly... */;
      };

    return field.resultClazz()._id;
  }


  private void addToAccessedClazzes(int s, int tclazz, int innerClazz)
  {
    var a = _accessedClazzes.get(s);
    if (a == null)
      {
        _accessedClazzes.put(s, new int[] { tclazz, innerClazz});
      }
    else
      {
        var found = false;
        for (var i=0; i < a.length && !found; i+=2)
          {
            if (a[i] == tclazz)
              {
                if (CHECKS) check
                  (a[i+1] == innerClazz);
                found = true;
              }
          }
        if (!found)
          {
            var n = new int[a.length+2];
            System.arraycopy(a, 0, n, 0, a.length);
            n[a.length  ] = tclazz;
            n[a.length+1] = innerClazz;
            _accessedClazzes.put(s, n);
          }
      }
  }


  /**
   * Get the possible inner clazzes for a dynamic call or assignment to a field
   *
   * @param s site of the access
   *
   * @return an array with an even number of element pairs with accessed target
   * clazzes at even indices followed by the corresponding inner clazz of the
   * feature to be accessed for this target.
   */
  private int[] accessedClazzesDynamic(int s)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Call   ||
       codeAt(s) == ExprKind.Assign    ,
       accessIsDynamic(s));

    var result = _accessedClazzes.get(s);
    if (result == null)
      {
        result = Clazz.NO_CLAZZ_IDS;
      }
    return result;
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
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Call   ||
       codeAt(s) == ExprKind.Assign    );

    int[] result;
    if (accessIsDynamic(s))
      {
        result = accessedClazzesDynamic(s);
      }
    else
      {
        var innerClazz = accessedClazz(s);
        var tt = clazzOuterClazz(innerClazz);
        result = clazzNeedsCode(innerClazz) ? new int[] { tt, innerClazz }
                                            : new int[0];
      }
    return result;
  }


  /**
   * Get the possible inner clazz for a call or assignment to a field with given
   * target clazz.
   *
   * This is used to feed information back from static analysis tools like DFA
   * to the GeneratingFUIR such that the given target will be added to the
   * targets / inner clazzes tuples returned by accesedClazzes.
   *
   * @param s site of the access
   *
   * @param tclazz the target clazz of the acces.
   *
   * @return the accessed inner clazz or NO_CLAZZ in case that does not exist,
   * i.e., an abstract feature is missing.
   */
  @Override
  public int lookup(int s, int tclazz)
  {
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Call   ||
       codeAt(s) == ExprKind.Assign    ,
       tclazz >= CLAZZ_BASE &&
       tclazz < CLAZZ_BASE  + _clazzes.size());

    int innerClazz;
    if (accessIsDynamic(s))
      {
        var inner = accessedClazz(s, id2clazz(tclazz));
        innerClazz = inner == null ? NO_CLAZZ : inner._id;
        if (inner != null)
          {
            addToAccessedClazzes(s, tclazz, innerClazz);
          }
        /*
        innerClazz = NO_CLAZZ;
        var ccs = accessedClazzes(s);
        //System.out.println("tclazz "+clazzAsString(tclazz)+" count "+ccs.length);
        if (CHECKS) check
          (ccs.length % 2 == 0);
        for (var i = 0; i < ccs.length; i += 2)
          {
            var tt = ccs[i+0];
            var cc = ccs[i+1];
            if (tt == tclazz)
              {
                innerClazz = cc;
              }
            //  System.out.println("tclazz "+clazzAsString(tclazz)+" vs tt "+clazzAsString(tt));
          }
        if (CHECKS) check
          (innerClazz != NO_CLAZZ);
        */
      }
    else
      {
        innerClazz = accessedClazz(s);
        if (CHECKS) check
          (tclazz == clazzOuterClazz(innerClazz));
        //    System.out.println("static : tclazz "+clazzAsString(tclazz)+" vs inner "+clazzAsString(innerClazz)+" "+clazzKind(innerClazz)+" from "+id2clazz(innerClazz).feature().pos().show());
      }
    if (innerClazz != NO_CLAZZ)
      {
        innerClazz = switch (clazzKind(innerClazz))
          {
          case Routine, Intrinsic, Native, Field -> innerClazz;
          case Abstract, Choice -> NO_CLAZZ;
          };
      }
    if (innerClazz != NO_CLAZZ && codeAt(s) == ExprKind.Call)
      {
        doesNeedCode(innerClazz);
      }

    // System.out.println("LOOKUP for "+clazzAsString(tclazz)+" is "+clazzAsString(innerClazz)+" at "+sitePos(s).show());
    return innerClazz;
  }


  /**
   * Inform the FUIR instance that lookup for new clazzes is finished.  This
   * means that clazzIsUnitType will be able to produce correct results since no
   * more features will be added.
   */
  @Override
  public void lookupDone()
  {
    if (CHECKS) check
      (!_lookupDone);

    _lookupDone = true;

    // NYI: layout phase creates new clazzes, which is why we cannot iterate like this. Need to check why and remove this!
    //
    // for(var c : _clazzes)
    for (var i = 0; i < _clazzes.size(); i++)
      {
        var c = _clazzes.get(i);
        c.layoutAndHandleCycle();
      }
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
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Assign ||
       codeAt(s) == ExprKind.Call  );

    var cl = clazzAt(s);
    var outerClazz = id2clazz(cl);
    var e = getExpr(s);
    var res = switch (e)
      {
      case AbstractAssign ass  -> id2clazz(accessTargetClazz(s)).isRef();
      case Clazz          arg  -> outerClazz.isRef() && !arg.feature().isOuterRef(); // assignment to arg field in inherits call (dynamic if outerClazz is ref)
                                                                                    // or to outer ref field (not dynamic)
      case AbstractCall   call -> id2clazz(accessTargetClazz(s)).isRef();
      default -> new Object() { { if (true) throw new Error("accessIsDynamic found unexpected Expr " + (e == null ? e : e.getClass()) + "."); } } == null /* Java is ugly... */;
      };
    return res;
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
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Assign ||
       codeAt(s) == ExprKind.Call  );

    var tclazz = _accessedTarget.get(s);
    if (tclazz == null)
      {
        var cl = clazzAt(s);
        var outerClazz = id2clazz(cl);
        var e = getExpr(s);
        tclazz = switch (e)
          {
          case AbstractAssign ass  -> clazz(ass._target, outerClazz, _inh.get(s - SITE_BASE)); // NYI: This should be the same as assignedField._outer
          case Clazz          arg  -> outerClazz; // assignment to arg field in inherits call, so outer clazz is current instance
          case AbstractCall   call -> calledTarget(call, outerClazz, _inh.get(s - SITE_BASE));
          default ->
          (Clazz) (Object) new Object() { { if (true) throw new Error("accessTargetClazz found unexpected Expr " + (e == null ? e : e.getClass()) + "."); } } /* Java is ugly... */;
          };
        _accessedTarget.put(s, tclazz);
      }

    return tclazz._id;
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
       s < SITE_BASE + _allCode.size(),
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
        var const_clazz = clazz(c, outerClazz, _inh.get(s - SITE_BASE));
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

      case AbstractCall c -> calledInner(c, outerClazz, null, _inh.get(s - SITE_BASE));
      case InlineArray  ia -> outerClazz.handDown(ia.type(),  _inh.get(s - SITE_BASE), ia);
      default -> throw new Error("constClazz origin of unknown class " + ac.origin().getClass());
      };

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
       s < SITE_BASE + _allCode.size(),
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
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Match);

    var cl = clazzAt(s);
    var cc = id2clazz(cl);
    var outerClazz = cc;
    var m = (AbstractMatch) getExpr(s);
    return clazz(m.subject(), outerClazz, _inh.get(s - SITE_BASE))._id;
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
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Match);

    var cl = clazzAt(s);
    var cc = id2clazz(cl);
    var outerClazz = cc;
    var m = (AbstractMatch) getExpr(s);
    var mc = m.cases().get(cix);
    var f = mc.field();
    var result = NO_CLAZZ;
    if (f != null)
      {
        // NYI: Check if this works for a case that is part of an inherits clause, do
        // we need to store in outerClazz.outer?
        result = outerClazz.lookup(f);
      }
    return result;
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
    var result = -1;
    for (var j = 0; result < 0 && j <  matchCaseCount(s); j++)
      {
        var mct = matchCaseTags(s, j);
        if (Arrays.stream(mct).anyMatch(t -> t == tag))
          {
            result = j;
          }
      }
    if (CHECKS) check
      (result != -1);
    return result;
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
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Match);

    var m = (AbstractMatch) getExpr(s);
    var mc = m.cases().get(cix);
    var ts = mc.types();
    var f = mc.field();
    int nt = f != null ? 1 : ts.size();
    var resultL = new List<Integer>();
    int tag = 0;
    for (var cg : m.subject().type().choiceGenerics(Context.NONE /* NYI: CLEANUP: Context should no longer be needed during FUIR */))
      {
        for (int tix = 0; tix < nt; tix++)
          {
            var t = f != null ? f.resultType() : ts.get(tix);
            if (t.isDirectlyAssignableFrom(cg, Context.NONE /* NYI: CLEANUP: Context should no longer be needed during FUIR */))
              {
                resultL.add(tag);
              }
          }
        tag++;
      }
    var result = new int[resultL.size()];
    for (int i = 0; i < result.length; i++)
      {
        result[i] = resultL.get(i);
      }

    if(POSTCONDITIONS) ensure
      (result.length > 0);

    return result;
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
    if (PRECONDITIONS) require
      (s >= SITE_BASE,
       s < SITE_BASE + _allCode.size(),
       withinCode(s),
       codeAt(s) == ExprKind.Match);

    var me = getExpr(s);
    var e = getExpr(s + 1 + cix);

    if (me instanceof AbstractMatch m &&
        m.subject() instanceof AbstractCall sc)
      {
        var c = m.cases().get(cix);
        var cf = sc.calledFeature();
        if (cf == Types.resolved.f_Type_infix_colon_true  ||
            cf == Types.resolved.f_Type_infix_colon_false ||
            cf == Types.resolved.f_Type_infix_colon          )
          {
            var outer = id2clazz(clazzAt(s));
            var innerClazz = calledInner(sc, outer, null, _inh.get(s - SITE_BASE));
            var tclazz = innerClazz.outer();
            var T = innerClazz.actualTypeParameters()[0];
            var pos = cf == Types.resolved.f_Type_infix_colon_true ||
              cf == Types.resolved.f_Type_infix_colon  &&
              T._type.constraintAssignableFrom(Context.NONE /* NYI: CLEANUP: Context should no longer be needed during FUIR */, tclazz._type.generics().get(0));
            var tf = pos ? Types.resolved.f_TRUE : Types.resolved.f_FALSE;
            if (!c.types().stream().anyMatch(x->x.compareTo(tf.selfType())==0))
              {
                return NO_SITE;
              }
          }
      }

    return ((NumLiteral) e).intValue().intValueExact();
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
    if (PRECONDITIONS) require
      (s == NO_SITE || s >= SITE_BASE,
       s == NO_SITE || withinCode(s));

    SourcePosition result = SourcePosition.notAvailable;
    if (s != NO_SITE)
      {
        var e = getExpr(s);
        result = (e instanceof Expr expr) ? expr.pos() :
                 (e instanceof Clazz z)   ? z._type.declarationPos()  /* implicit assignment to argument field */
                                          : null;
      }
    return result;
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
    if (PRECONDITIONS) require
      (cl != NO_CLAZZ);

    return
      (clazzKind(cl) == FeatureKind.Intrinsic) &&
      switch(clazzOriginalName(cl))
      {
      case "effect.type.abort0"  ,
           "effect.type.default0",
           "effect.type.instate0",
           "effect.type.is_instated0",
           "effect.type.replace0" -> true;
      default -> false;
      };
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
    if (PRECONDITIONS) require
      (isEffectIntrinsic(cl));

    return clazzActualGeneric(clazzOuterClazz(cl), 0);
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
    if (PRECONDITIONS) require
      (clazzIsArray(constCl));

    var result = type2clazz(id2clazz(constCl)._type.generics().get(0))._id;

    if (POSTCONDITIONS) ensure
      (result >= 0);

    return result;
  }


  /**
   * Is `cl` an array?
   */
  @Override
  public boolean clazzIsArray(int cl)
  {
    if (PRECONDITIONS) require
      (cl >= CLAZZ_BASE,
       cl < CLAZZ_BASE + _clazzes.size());

    return clazz(cl)._feature == Types.resolved.f_array;
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
  private ByteBuffer deserializeClazz(int cl, ByteBuffer bb)
  {
    return switch (getSpecialClazz(cl))
      {
      case c_Const_String, c_String :
        var len = bb.duplicate().order(ByteOrder.LITTLE_ENDIAN).getInt();
        yield bb.slice(bb.position(), 4+len);
      case c_bool :
        yield bb.slice(bb.position(), 1);
      case c_i8, c_i16, c_i32, c_i64, c_u8, c_u16, c_u32, c_u64, c_f32, c_f64 :
        var bytes = bb.duplicate().order(ByteOrder.LITTLE_ENDIAN).getInt();
        yield bb.slice(bb.position(), 4+bytes);
      default:
        yield this.clazzIsArray(cl)
          ? deserializeArray(this.inlineArrayElementClazz(cl), bb)
          : deserializeValueConst(cl, bb);
      };
  }


  /**
   * bytes used when serializing call that results in this type.
   */
  private ByteBuffer deserializeValueConst(int cl, ByteBuffer bb)
  {
    var args = clazzArgCount(cl);
    var bbb = bb.duplicate().order(ByteOrder.LITTLE_ENDIAN);
    var argBytes = 0;
    for (int i = 0; i < args; i++)
      {
        var rt = clazzArgClazz(cl, i);
        argBytes += deseralizeConst(rt, bbb).length;
      }
    return bb.slice(bb.position(), argBytes);
  }


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
    var elBytes = deserializeClazz(cl, bb.duplicate()).order(ByteOrder.LITTLE_ENDIAN);
    bb.position(bb.position()+elBytes.remaining());
    var b = new byte[elBytes.remaining()];
    elBytes.get(b);
    return b;
  }



  /**
   * Extract bytes from `bb` that should be used when deserializing this inline array.
   *
   * @param elementClazz the elements clazz
   *
   * @elementCount the count of elements in this array.
   *
   * @param bb the bytes to be used when deserializing this constant.
   *           May be more than necessary for variable length constants
   *           like strings, arrays, etc.
   */
  private ByteBuffer deserializeArray(int elementClazz, ByteBuffer bb)
  {
    var bbb = bb.duplicate().order(ByteOrder.LITTLE_ENDIAN);
    var elCount = bbb.getInt();
    var elBytes = 0;
    for (int i = 0; i < elCount; i++)
      {
        elBytes += deseralizeConst(elementClazz, bbb).length;
      }
    return bb.slice(bb.position(), 4+elBytes);
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
       cl < CLAZZ_BASE + _clazzes.size());

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
   * tuple of clazz, called abstract features and location where the clazz was
   * instantiated.
   */
  record AbsMissing(Clazz clazz,
                    TreeMap<AbstractFeature, String> called,
                    SourcePosition instantiationPos,
                    String context)
  {
  };


  /**
   * Set of missing implementations of abstract features
   */
  TreeMap<Clazz, AbsMissing> _abstractMissing = new TreeMap<>((a,b)->Integer.compare(a._id,b._id));


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
  public void recordAbstractMissing(int cl, int f, int instantiationSite, String context, int callSite)
  {
    // we might have an assignment to a field that was removed:
    if (codeAt(callSite) == FUIR.ExprKind.Call &&
        // if there is no instantiation (while cotypes are implicitly instantiated), no need to report
        (instantiationSite != NO_SITE || id2clazz(f)._feature.outer().isTypeFeature()))
      {
        var cc = id2clazz(cl);
        var cf = id2clazz(f);
        var r = _abstractMissing.computeIfAbsent(cc, ccc -> new AbsMissing(ccc, new TreeMap<>(), sitePos(instantiationSite), context));
        r.called.put(cf.feature(), sitePos(callSite).show());
        if (CHECKS) check
          (cf.feature().isAbstract() ||
           (cf.feature().modifiers() & FuzionConstants.MODIFIER_FIXED) != 0);
      }
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
    _abstractMissing.values()
      .stream()
      .forEach(r -> AirErrors.abstractFeatureNotImplemented(r.clazz.feature(),
                                                            r.called,
                                                            r.instantiationPos,
                                                            r.context));
  }


}

/* end of file */
