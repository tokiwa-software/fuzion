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
 * Source of class Clazz
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir;

import static dev.flang.util.FuzionConstants.NO_SELECT;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractType;
import dev.flang.ast.AstErrors;
import dev.flang.ast.Expr;
import dev.flang.ast.ResolvedNormalType;
import dev.flang.ast.Types;

import dev.flang.fe.LibraryFeature;

import dev.flang.ir.IR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.List;
import dev.flang.util.StringHelpers;
import dev.flang.util.YesNo;


/**
 * Clazz represents a runtime type, i.e, a Type with actual generic arguments.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class Clazz extends ANY implements Comparable<Clazz>
{


  /*-----------------------------  statics  -----------------------------*/


  //  static int counter;  {counter++; if ((counter&(counter-1))==0) say("######################"+counter+" "+this.getClass()); }
  // { if ((counter&(counter-1))==0) Thread.dumpStack(); }


  /**
   * Empty array as a result for fields() if there are no fields.
   */
  private static final Clazz[] NO_CLAZZES = new Clazz[0];


  /*-----------------------------  classes  -----------------------------*/


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


  /*----------------------------  variables  ----------------------------*/


  /**
   * The runtime type for this clazz
   */
  final AbstractType _type;


  /**
   * If this clazz represents a field of an open generic type, then _select
   * chooses the actual generic parameter to be used as the type of this field.
   * Otherwise, _select is NO_SELECT.
   */
  final int _select;


  /**
   * The outer clazz
   */
  final Clazz _outer;


  /**
   * Cached result of choiceGenerics(), only used if isChoice() and
   * !isChoiceOfOnlyRefs().
   */
  private List<Clazz> _choiceGenerics;


  /**
   * Flag that is set while the layout of objects of this clazz is determined.
   * This is used to detect recursive clazzes that contain value type fields of
   * the same type as the clazz itself.
   */
  LayoutStatus _layouting = LayoutStatus.Before;


  /**
   * The argument fields of this routine.
   */
  Clazz[] _argumentFields;


  /**
   * Cached actual type parameters of this clazz
   */
  Clazz[] _actualTypeParameters = NO_CLAZZES;


  /**
   * If this clazz contains a direct outer ref field, this is the direct outer
   * ref. null otherwise.
   *
   * This is initialized after Clazz creation by dependencies().
   */
  Clazz _outerRef;


  /**
   * Cached looked up result field of this clazz
   */
  Clazz _resultField;


  /**
   * Fields in instances of this clazz. Set during layout phase.
   */
  Clazz[] _fields;


  /**
   * For a clazz with isRef()==true, this will be set to a value version of this
   * clazz.
   */
  private Clazz _asValue;


  /**
   * The type of the result of calling thiz clazz.
   *
   * This is initialized after Clazz creation by dependencies().
   */
  Clazz _resultClazz = null;



  /**
   * Will instances of this class be created?
   */
  private boolean _isInstantiatedChoice = true; // NYI: false;

  /**
   * Is this a normalized outer clazz? If so, there might be calls on this as an
   * outer clazz even if it is not instantiated.
   */
  private boolean _isNormalized = false;


  /**
   * Set of all heirs of this clazz.
   */
  Set<Clazz> _heirs = null;


  List<Clazz> _inner = new List<>();



  /**
   * Actual inner clazzes when calling a dynamically bound feature on this.
   *
   * This maps a feature to a Clazz. Only for fields of open generic types, this
   * maps a feature to a Clazz[] that contains the actual fields.  The array
   * might be empty.
   */
  final Map<FeatureAndActuals, Object> _innerFromFuir = new TreeMap<>();


  /**
   * Cached result of isUnitType().
   */
  YesNo _isUnitType = YesNo.dontKnow;


  /**
   * Cached result of parents(), null before first call to parents().
   */
  private Set<Clazz> _parents = null;



  /**
   * Interface to FUIR instance used to with this Clazz.
   */
  final GeneratingFUIR _fuir;


  /**
   * Integer id of this Clazz used in FUIR instance.
   */
  int _id = IR.NO_CLAZZ;


  /**
   * Special clazz id to quickly check if this is a given special clazz.
   */
  SpecialClazzes _specialClazzId = SpecialClazzes.c_NOT_FOUND;


  /**
   * Does this clazz need code, i.e., for a routine: Is this ever called?  For a
   * choice: Is this ever used? For a field: is it ever read?
   */
  boolean _needsCode;


  /**
   * For a routine with _needsCode: Site of the code block of this clazz
   */
  int _code;



  /**
   * Cached result values of {@code toString(boolean)}
   */
  String _toStringHuman, _toString;


  /**
   * If this creates a type that depends on a {@code this} type of an outer ref clazz,
   * this will be set to code calling AstErrors.illegalOuterRefTypeInCall. If
   * this clazz is the result clazz in a call, this error will be produced.
   *
   * See #4273 and rests/reg_issue4273 for example code that needs this.
   */
  Consumer<AbstractCall> _showErrorIfCallResult_ = null;

  /**
   * Used when lookupDone=true but looking up
   * not yet existing clazz.
   */
  private static final Clazz NO_CLAZZ = new Clazz()
    {
      @Override void doesNeedCode() { }
    };


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a Clazz
   *
   * @fuir FUIR instance used to lookup reference clazzes
   *
   * @param outer the outer clazz, will be normalized before it is used.
   *
   * @param type the actual type this clazz is built on. The actual type must
   * not be a generic argument.
   *
   * @param select in case actualType refers to a field whose result type is an
   * open generic parameter, select specifies the actual generic to be used.
   */
  Clazz(GeneratingFUIR fuir,
        Clazz outer,
        AbstractType type,
        int select)
  {
    if (PRECONDITIONS) require
      (!type.dependsOnGenericsNoOuter(),
       !type.containsThisType(),
       // NYI: UNDER DEVELOPMENT: currently not possible because of type_as_value and `Type.infix :`
       //  !type.feature().isTypeParameter(),
       type.feature().resultType().isOpenGeneric() == (select >= 0),
       type != Types.t_ERROR,
       // outer clazzes of fields must be values
       outer == null || outer.isValue() || !type.feature().isField());

    _fuir = fuir;
    outer = normalizeOuter(type, outer);
    this._type = outer != null
      ? ResolvedNormalType.newType(type, outer._type)
      : type;

    _outer = outer;
    _select = select;
    _needsCode = false;
    _code = IR.NO_SITE;
  }


  /*
   * constructor for NO_CLAZZ
   */
  private Clazz()
  {
    _fuir = null;
    _outer = null;
    _select = -5555555;
    _type = null;
  }


  /**
   * Additional initialization code that has to be run after this Clazz was
   * added to FUIRI._clazzes for recursive clazz lookup.
   */
  void init(int id)
  {
    _id = id;
    _choiceGenerics = determineChoiceGenerics();
    var vas = feature().valueArguments();
    if (vas.size() == 0 || isBoxed())
      {
        _argumentFields = NO_CLAZZES;
      }
    else
      {
        _argumentFields = actualFields(feature().valueArguments());
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

                gi = gi.feature().isRef() ? gi.asRef() : gi.asValue();
                }
            _actualTypeParameters[i] = _fuir.type2clazz(gi);
          }
      }

    // NYI: UNDER DEVELOPMENT: we might want to create the result clazz early
    // to avoid adding clazzes one lookupDone is set:
    //
    // var ignore = resultClazz();
  }



  void addInner(Clazz i)
  {
    if (PRECONDITIONS) require
      (!_fuir._lookupDone,
       i.clazzKind() != IR.FeatureKind.Field || isValue());

    _inner.add(i);
  }


  /**
   * Check if the given feature requires specialization for the exact outer
   * clazz.
   *
   * If an outer ref is used to access the outer instance, we must not normalize
   * because we will need the exact type of the outer instance to specialize
   * code or to access features that only exist in the specific version
   *
   * @param f the feature to check if it has an outer ref
   */
  private boolean needsSpecialization(AbstractFeature f)
  {
    var or = f.outerRef();

    return !f.isConstructor()  // do not specialize a constructor
      && or != null;
  }


  /**
   * Normalize an outer clazz for a given type. For a reference clazz that
   * inherits from f, this will return the corresponding clazz derived from
   * f. The idea is that, e.g., we do not need to distinguish const_string.length
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
    if (outer != null && !needsSpecialization(f) && !f.isField() && t != Types.t_ERROR)
      {
        outer = outer.normalize(t.feature().outer());
      }
    return outer;
  }


  /**
   * Normalize a reference clazz to the given feature.  For a reference clazz
   * that inherits from f, this will return the corresponding clazz derived
   * from f. The idea is that, e.g., we do not need to distinguish const_string.length
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
        isValue() ||

        // optimization: if feature() is already f, there is nothing to
        // normalize anymore
        feature() == f ||

        needsSpecialization(feature())
        )
      {
        return this;
      }
    else
      {
        var t = this._type.actualType(f.selfType()).asRef();
        return normalize2(t);
      }
  }
  private Clazz normalize2(AbstractType t)
  {
    var f = t.feature();
    if (f.isUniverse())
      {
        return _fuir.universe();
      }
    else
      {
        var normalized = _fuir.newClazz(normalize2(f.outer().selfType()), t, FuzionConstants.NO_SELECT);
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
   * This set is initially empty, it will be filled by {@code registerAsHeir()}
   * which is called for every new Clazz created via _clazzes.create().
   *
   * @return the heirs including this.
   */
  Set<Clazz> heirs()
  {
    if (_heirs == null)
      {
        _heirs = new TreeSet<>();
      }
    return _heirs;
  }


  /**
   * Set of direct parent clazzes this inherits from.
   */
  private Set<Clazz> directParents()
  {
    var result = new TreeSet<Clazz>();
    result.add(this);
    for (var p: feature().inherits())
      {
        var pt = p.type();
        var t1 = isRef() && !pt.isVoid() ? pt.asRef() : pt.asValue();
        var t2 = _type.actualType(t1);
        var pc = _fuir.newClazz(t2);
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
  private Set<Clazz> parents()
  {
    var result = _parents;
    if (result == null)
      {
        result = new TreeSet<Clazz>();
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


  /**
   * Does this clazz need code, i.e., for a routine: Is this ever called?  For a
   * choice: Is this ever used? For a field: is it ever read?
   */
  boolean needsCode()
  {
    return _needsCode && (!_fuir._lookupDone || !feature().isField() || !resultClazz().isUnitType());
  }


  /**
   * Record that this clazz needs code, or, in case of a field, is read at some point.
   */
  void doesNeedCode()
  {
    if (!_needsCode && !_fuir._lookupDone)
      {
        _needsCode = true;
        var r = resultField();
        if (r != null)
          { // NYI: UNDER DEVELOPMENT: This is require for tests/javaBase. Check why this is needed only there and not otherwise!
            r.doesNeedCode();
          }
      }
  }


  /**
   * In given type t, replace occurrences of 'X.this.type' by the actual type
   * from this Clazz.
   *
   * @param t a type
   */
  AbstractType replaceThisType(AbstractType t)
  {
    t = replaceThisTypeForCotype(t);
    if (t.isThisType())
      {
        t = findOuter(t)._type;
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
   * Here, in the inherits call to {@code h i}, the type parameter is
   * {@code t.this.type.i}. So in the corresponding type feature has two
   *
   *   t.type.h.type t.i t.this.type.i
   *
   * the second type parameter for {@code B} has to get it's {@code this.type} types
   * replaced by the actual types given in the first type parameter
   */
  AbstractType replaceThisTypeForCotype(AbstractType t)
  {
    if (feature().isCotype())
      {
        t = _type.generics().get(0).actualType(t);
        var g = t.cotypeActualGenerics();
        var o = t.outer();
        if (o != null)
          {
            o = replaceThisTypeForCotype(o);
          }
        t = ResolvedNormalType.create(t, g, g, o, true);
      }
    return t;
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
  List<AbstractType> actualGenerics(List<AbstractType> generics)
  {
    var result = this._type.replaceGenerics(generics);

    // Replace any {@code a.this.type} actual generics by the actual outer clazz:
    result = result.map(t->replaceThisType(t));

    if (_outer != null)
      {
        result = _outer.actualGenerics(result);
      }
    return result;
  }


  /**
   * Return the kind of this clazz ( Routine, Field, Intrinsic, Abstract, ...)
   */
  IR.FeatureKind clazzKind()
  {
    return switch (feature().kind())
      {
      case Routine           -> IR.FeatureKind.Routine;
      case Field             -> IR.FeatureKind.Field;
      case TypeParameter     -> IR.FeatureKind.Intrinsic; // NYI: strange, currently needed for type_as_value arg
      case Intrinsic         -> IR.FeatureKind.Intrinsic;
      case Abstract          -> IR.FeatureKind.Abstract;
      case Choice            -> IR.FeatureKind.Choice;
      case Native            -> IR.FeatureKind.Native;
      default                -> throw new Error("clazzKind: " + feature().kind());
      };
  }


  /**
   * The feature underlying this clazz.
   */
  LibraryFeature feature()
  {
    return (LibraryFeature) _type.feature();
  }


  /**
   * Is this clazz denoting a reference type?
   */
  boolean isRef()
  {
    return switch (_type.mode())
      {
      case RefType -> true;
      case ValueType -> false;
      case ThisType -> throw new Error("unexpected this type");
      };
  }


  /**
   * Is this clazz denoting a value type?
   */
  boolean isValue()
  {
    return !isRef();
  }


  /**
   * isBoxed is true iff this is a ref value but the underlying feature is a value feature.
   */
  boolean isBoxed() { return isRef() && !feature().isRef(); }


  /**
   * isUnitType checks if there exists only one single value in instances of
   * this clazz, so this value does not need to be stored.
   */
  boolean isUnitType()
  {
    if (_isUnitType != YesNo.dontKnow)
      {
        return _isUnitType == YesNo.yes;
      }

    var res = YesNo.no;
    if (_specialClazzId == SpecialClazzes.c_unit)
      {
        res = YesNo.yes;
      }
    else if ( _fuir._lookupDone && (isValue()                       &&
                                    !feature().isBuiltInPrimitive() &&
                                    !isVoidType()                   &&
                                    !isChoice()                       ))
      {
        // Tricky: To avoid endless recursion, we set _isUnitType to No. In case we
        // have a recursive type, isUnitType() will return false, so recursion will
        // stop and the result for the recursive type will be false.
        //
        // Object layout will later report an error for this case. (NYI: check this with a test!)
        _isUnitType = YesNo.no;

        res = YesNo.yes;

        // NOTE: We cannot use {@code for (var i : _inner)} since {@code resultClazz} may
        // add inner clazzes even if lookupDone() is set.
        for (var ix = 0; ix < _inner.size(); ix++)
          {
            var i = _inner.get(ix);
            res =
              i.clazzKind() != IR.FeatureKind.Field ||
              i.resultClazz().isUnitType()             ? res
                                                       : YesNo.no;
          }
        _isUnitType = YesNo.dontKnow;
      }
    if (_fuir._lookupDone)
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
  boolean isVoidType()
  {
    return this._specialClazzId == SpecialClazzes.c_void;
  }


  /**
   * Layout this clazz. In case a cyclic nesting of value fields is detected,
   * report an error.
   */
  void layoutAndHandleCycle()
  {
    var cycle = layout();
    if (cycle != null && Errors.count() <= FuirErrors.count())
      {
        StringBuilder cycleString = new StringBuilder();
        for (var s: cycle.reversed())
          {
            cycleString.append(s).append("\n");
          }
        FuirErrors.error(_type.declarationPos(),
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
  private List<String> layout()
  {
    List<String> result = null;
    switch (_layouting)
      {
      case During:
        result = new List<>();
        result.add("Requires layout of "+ Errors.sqn(this.toString()) + ": " + this.feature().pos().show());
        break;
      case Before:
        {
          _layouting = LayoutStatus.During;
          if (isChoice())
            {
              for (Clazz c : choiceGenerics())
                {
                  if (result == null && c.isValue())
                    {
                      result = c.layout();
                      if (result != null)
                        {
                          result.add("Choice variant " + Errors.sqn(c.toString()) + ": " + c.feature().pos().show());
                        }
                    }
                }
            }
          if (isValue())
            {
              for (var fc : fields())
                {
                  if (result == null && !fc.feature().isOuterRef())
                    {
                      result = layoutFieldType(fc);
                      if (result != null)
                        {
                          result.add("Layout " + Errors.sqn(this.toString()) + ": " + fc.feature().pos().show());
                        }
                    }
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
  private List<String> layoutFieldType(Clazz field)
  {
    List<String> result = null;
    var fieldClazz = field.resultClazz();
    if (fieldClazz.isValue() &&
        !fieldClazz.feature().isBuiltInPrimitive() &&
        !fieldClazz.isVoidType())
      {
        result = fieldClazz.layout();
        if (result != null)
          {
            result.add("Field: " + Errors.sqn(field.toString()) + " of type " + Errors.sqn(fieldClazz.toString())+": " + field.feature().pos().show());
          }
      }
    return result;
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
    if (f != Types.f_ERROR && tf != Types.resolved.f_void)
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
    AbstractFeature result = _fuir._mainModule.lookupFeature(feature(), fn, f);

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
            result = _fuir._mainModule.lookupFeature(p.calledFeature(), fn, f);
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
  Clazz lookup(AbstractFeature f)
  {
    if (PRECONDITIONS) require
      (f != null,
       !isVoidType());

    return lookup(new FeatureAndActuals(f, AbstractCall.NO_GENERICS), FuzionConstants.NO_SELECT, false);
  }


  /**
   * Convenience function that calls {@code lookup} followed {@code doesNeedCod()} on the
   * result.
   */
  Clazz lookupNeeded(AbstractFeature f)
  {
    var innerClazz = lookup(f);
    innerClazz.doesNeedCode();
    return innerClazz;
  }


  /**
   * Lookup the code to perform the given static call. The result is _not_
   * marked as needed (since it might not be needed if the call is dynamic or
   * inlined).
   *
   * @param c the call whose target is to be looked up
   *
   * @param typePars the actual type parameters in the call.
   *
   * @return the inner clazz of the target in the call.
   */
  Clazz lookupCall(AbstractCall c, List<AbstractType> typePars)
  {
    return isVoidType()
      ? this
      // NYI: HACK for test compile_time_type_casts
      : !feature().inheritsFrom(c.calledFeature().outer())
      ? this
      : lookup(new FeatureAndActuals(c.calledFeature(),
                                     typePars),
               c.select(),
               c.isInheritanceCall());
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
   * actual field. NO_SELECT otherwise.
   *
   * @param isInheritanceCall true iff this is a call in an inheritance clause.  In
   * this case, the result clazz will not be marked as instantiated since the
   * call will work on the instance of the inheriting clazz.
   *
   * @return the inner clazz of the target in the call.
   */
  Clazz lookup(FeatureAndActuals fa,
               int select,
               boolean isInheritanceCall)
  {
    if (PRECONDITIONS) require
      (fa != null,
       !fa._f.isUniverse(),
       !isVoidType());

    Clazz innerClazz = null;
    Clazz[] innerClazzes = null;
    var iCs = _innerFromFuir.get(fa);
    if (select < 0)
      {
        if (CHECKS) check
          (Errors.any() || iCs == null || iCs instanceof Clazz);

        innerClazz =
          iCs == null              ? null :
          iCs instanceof Clazz iCC ? iCC
                                   : _fuir.error();
      }
    else
      {
        if (CHECKS) check
          (Errors.any() || iCs == null || iCs instanceof Clazz[]);
        if (iCs == null || !(iCs instanceof Clazz[] iCA))
          {
            innerClazzes = new Clazz[replaceOpenCount(fa._f)];
            _innerFromFuir.put(fa, innerClazzes);
          }
        else
          {
            innerClazzes = iCA;
          }
        if (CHECKS) check
          (Errors.any() || select < innerClazzes.length);
        innerClazz = select < innerClazzes.length ? innerClazzes[select] : _fuir.error();
      }
    if (innerClazz == null && _fuir._lookupDone)
      {
        return NO_CLAZZ;
      }
    if (innerClazz == null)
      {
        AbstractType t = null;
        var f = fa._f;
        if (f.isTypeParameter())
          { // type parameters do not get inherited, but replaced by the actual
            // type given in the inherits call:
            t = f.selfType();   // e.g., {@code (Types.get T).T}
            if (CHECKS)
              check(Errors.any() || fa._tp.isEmpty());  // there should not be an actual type parameters to a type parameter
          }
        else if (f != Types.f_ERROR)
          {
            var af = findRedefinition(f);
            if (CHECKS) check
              (Errors.any() || af != null);
            if (af != null)
              {
                t = af.selfType().applyTypePars(af, fa._tp);
              }
          }
        if (t == null)
          {
            innerClazz = _fuir.error();
          }
        else
          {
            t = _type.actualType(t);  // e.g., {@code (Types.get (array f64)).T} -> {@code array f64}

/*
  We have the following possibilities when calling a feature {@code f} declared in do {@code on}
  actual outer clazz {@code oa}:

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

  declared for value / declared for ref: {@code on} may be a {@code ref} instance

     # f declared for value
     on is
       f ... is
         stmnts
     # f declared for ref
     on ref is
       f ... is
         stmnts

  called on value / ref / boxed: {@code oa} is a value instance, a ref instance, a boxed value instance

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
            innerClazz = _fuir.newClazz(outerUnboxed, t, select);
            if (CHECKS) check
              (innerClazz._select == select);
            if (select < 0)
              {
                _innerFromFuir.put(fa, innerClazz);
                if (outerUnboxed != this)
                  {
                    outerUnboxed._innerFromFuir.put(fa, innerClazz);
                  }
              }
            else
              {
                innerClazzes[select] = innerClazz;
              }
          }
      }

    if (POSTCONDITIONS) ensure
      (Errors.any() || fa._f.isTypeParameter() || findRedefinition(fa._f) == null || innerClazz._type != Types.t_ERROR,
      innerClazz != null);

    return innerClazz;
  }


  /**
   * Create String from this clazz.
   *
   * @param humanReadable true to create a string optimized to be readable by
   * humans but possibly not unique. false for a unique String representing
   * this clazz to be used by compilers.
   */
  String toString(boolean humanReadable)
  {
    String result = humanReadable ? _toStringHuman : _toString;
    if (result == null)
      {
        var o = _outer;
        String outer = o != null && !o.feature().isUniverse() ? StringHelpers.wrapInParentheses(o.toString(humanReadable)) + "." : "";
        var f = feature();
        var typeType = f.isCotype();
        if (typeType)
          {
            f = (LibraryFeature) f.cotypeOrigin();
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
        // {@code f.isPostFeature()}. Note that this would need to be saved in .fum file as well!
        //
        if (fname.startsWith(FuzionConstants.POSTCONDITION_FEATURE_PREFIX))
          {
            fname = fname.substring(FuzionConstants.POSTCONDITION_FEATURE_PREFIX.length(),
                                    fname.lastIndexOf("_")) +
              ".postcondition";
          }

        result = outer
          + ( isRef()   && !feature().isRef() ? "ref "   : "" )
          + ( isValue() &&  feature().isRef() ? "value " : "" )
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
                result = result + " " + StringHelpers.wrapInParentheses(g.toString(humanReadable));
              }
            skip = false;
          }
        if (humanReadable)
          {
            _toStringHuman = result;
          }
        else
          {
            _toString = result;
          }
      }
    return result;
  }


  @Override
  public String toString()
  {
    return toString(false); // maybe better true, i.e., human readable
  }



  /**
   * If this clazz contains a direct outer ref field, this is the direct outer
   * ref. null otherwise.
   */
  Clazz outerRef()
  {
    var res = _outerRef;
    if (res == null && !_fuir._lookupDone)
      {
        var or = feature().outerRef();
        if (!isBoxed() && or != null)
          {
            res = lookup(or);
          }
        else
          {
            res = this;
          }
        _outerRef = res;
      }
    return res == this ? null : res;
  }


  /**
   * Get the result field of this routine if it exists.
   *
   * @return the result field or null.
   */
  Clazz resultField()
  {
    if (_resultField == null && !_fuir._lookupDone)
      {
        var rf = feature().resultField();
        if (rf != null)
          {
            _resultField = lookupNeeded(rf);
          }
      }
    return _resultField;
  }


  /**
   * Get the argument fields of this routine
   *
   * @return the argument fields.
   */
  Clazz[] argumentFields()
  {
    if (PRECONDITIONS) require
      (switch (clazzKind())
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
   * Helper routine for compareTo: compare the outer classes.  If outer are refs
   * for both clazzes, they can be considered the same as long as their outer
   * classes (recursively) are the same. If they are values, they need to be
   * exactly equal.
   */
  private int compareOuter(Clazz other)
  {
    var to = this ._outer;
    var oo = other._outer;
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
                  to.isValue() && oo.isValue() ? to.compareTo(oo) :
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
  @Override
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
  private int compareToIgnoreOuter(Clazz other)
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
    return (_type.isRef() ? 0x777377 : 0) ^ feature().globalIndex();  // NYI: outer and type parameters!
  }


  /**
   * The actual type parameters of this clazz. E.g. for {@code list i32} this returns
   * {@code [ i32 ]}.
   */
  Clazz[] actualTypeParameters()
  {
    return _actualTypeParameters;
  }


  /**
   * Is this a choice-type, i.e., does it directly inherit from choice?
   */
  private boolean isChoice()
  {
    return feature().isChoice();
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
            result.add(_fuir.newClazz(t));
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
  List<Clazz> choiceGenerics()
  {
    if (PRECONDITIONS) require
      (isChoice());

    return _choiceGenerics;
  }



  /**
   * Mark this as instantiated at given source code position.
   *
   * @param at gives the position in the source code that causes this instantiation.
   */
  void instantiatedChoice(HasSourcePosition at)
  {
    if (PRECONDITIONS) require
      (at != null,
       isChoice());

    if (!_isInstantiatedChoice && !isVoidType())
      {
        _isInstantiatedChoice = true;
      }
  }


  /**
   * Check of _outer is instantiated.
   *
   * NYI: UNDER DEVELOPMENT: Check if this can be replaced by just {@code true}
   */
  private boolean isOuterInstantiated()
  {
    var o = _outer;
    return o == null ||

      // NYI: Once Clazz.normalize() is implemented better, a clazz C has
      // to be considered instantiated if there is any clazz D that
      // normalize() would replace by C if it occurs as an outer clazz.
      o._specialClazzId == SpecialClazzes.c_Any    ||

      o._isNormalized ||

      o.isInstantiatedChoice();
  }


  /**
   * Flag to detect endless recursion between isInstantiatedChoice() and
   * isRefWithInstantiatedHeirs(). This may happen in a clazz that inherits from
   * its outer clazz.
   */
  private int _checkingInstantiatedHeirs = 0;


  /**
   * Helper for isInstantiatedChoice to check if there are heir clazzes of this
   * that are instantiated.
   *
   * @return true iff this is a ref and there exists an heir of this that is
   * instantiated.
   */
  private boolean hasInstantiatedChoiceHeirs()
  {
    var result = false;
    for (var h : heirs())
      {
        h._checkingInstantiatedHeirs++;
        result = result
          || h != this && h.isInstantiatedChoice();
        h._checkingInstantiatedHeirs--;
      }
    return result;
  }


  /**
   * Is this clazz instantiated?  This tests this._isInstantiatedChoice and,
   * recursively, _outer.isInstantiated().
   */
  boolean isInstantiatedChoice()
  {
    return _isInstantiatedChoice
      && (_checkingInstantiatedHeirs > 0
          || (isOuterInstantiated()
              || isChoice()
              || _outer.isRef() && _outer.hasInstantiatedChoiceHeirs()));
  }


  /**
   * In case this is a Clazz of value type, create the corresponding reference clazz.
   */
  Clazz asRef()
  {
    return isRef()
      ? this
      : _fuir.newClazz(_outer, _type.asRef(), _select);
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
            outer = _fuir.clazz(target, this, new List<>());
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
  Clazz resultClazz()
  {
    var result = _resultClazz;
    if (result == null)
      {
        var f = feature();
        var o  = _outer;
        var of = o != null ? o.feature() : null;

        if (f.isConstructor())
          {
            result = this;
          }
        else if (f.isOuterRef())
          {
            result = o.inheritedOuterRefClazz(o._outer, null, f, o.feature(), null);
          }
        else if (f.isTypeParameter())
          {
            result = typeParameterActualType().typeClazz();
          }
        else if (f  == Types.resolved.f_type_as_value                          ||
                 of == Types.resolved.f_type_as_value && f == of.resultField()   )
          {
            var ag = (f == Types.resolved.f_type_as_value ? this : o).actualTypeParameters();
            result = ag[0].typeClazz();
          }
        // NYI: UNDER DEVELOPMENT: special handling for #2262
        else if (isBoxed())
          {
            result = _asValue;
          }
        else
          {
            var ft = f.resultType();
            result = handDown(ft, _select);
          }
        _resultClazz = result;
      }
    return result;
  }


  /**
   * For a type clazz such as 'i32.type' return its name, such as 'i32'.
   */
  String typeName()
  {
    if (PRECONDITIONS) require
      (feature().isCotype());

    return _type.generics().get(0).toString(true);
  }


  /**
   * For a type parameter, return the clazz of the actual type.
   *
   * Example:
   *
   * For {@code (Types.get (array f64)).T} this results in {@code array f64}.
   */
  Clazz typeParameterActualType()
  {
    if (PRECONDITIONS) require
      (feature().isTypeParameter());

    var f = feature();
    var o = _outer;
    var inh = o.feature().tryFindInheritanceChain(f.outer());
    if (inh != null && inh.size() > 0)
      { // type parameter was inherited, so get value from parameter of inherits call:
        var call = inh.get(0);
        if (CHECKS) check
          (call.calledFeature() == f.outer());

        var oc = _outer;
        var tclazz  = _fuir.clazz(call.target(), oc, inh);
        var typePars = actualGenerics(call.actualTypeParameters());
        check(call.isInheritanceCall());
        o = tclazz.lookupCall(call, typePars);
      }
    var ix = f.typeParameterIndex();
    var oag = o.actualTypeParameters();
    return inh == null || ix < 0 || ix >= oag.length ? _fuir.error()
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
            _typeClazz = _fuir.error();
          }
        else
          {
            var tt = _type.typeType();
            var ty = Types.resolved.f_Type.selfType();
            _typeClazz = _type.containsError()  ? _fuir.error() :
                         feature().isUniverse() ? this    :
                         tt.compareTo(ty) == 0  ? _fuir.newClazz(_fuir.universe()  , ty, FuzionConstants.NO_SELECT)
                                                : _fuir.newClazz(_outer.typeClazz(), tt, FuzionConstants.NO_SELECT);
          }
      }
    return _typeClazz;
  }


  /**
   * cached result of typeClazz()
   */
  private Clazz _typeClazz = null;


  /**
   * Find outer clazz of this corresponding to this-type {@code o}.
   *
   * @param o the this-type whose clazz we are searching for.
   *
   * @return the outer clazz of this corresponding this-type {@code o}.
   */
  // NYI: UNDER DEVELOPMENT: logic too complicated and likely subtly wrong.
  private Clazz findOuter(AbstractType o)
  {
    if (PRECONDITIONS) require
      (o.isThisType());

    /* starting with feature(), follow outer references
     * until we find o.
     */
    var of = handDown(o, NO_SELECT, (t1,t2)->{}).feature();
    var res = this;
    var i = feature();
    while (i != null && i != of)
      {
        res = res.outerRef() != null
          ? res.outerRef().resultClazz()
          : res._outer;

        i = res == null
          ? null
          : (LibraryFeature) res.feature();
      }

    // NYI: BUG: handDown does not handle nestedinheritance correctly, test/covariance
    if (i == null)
      {
        res = this;
        i = feature();
        while (i != null && i != of)
          {
            res = i.hasOuterRef()
              ? res.lookup(i.outerRef()).resultClazz()
              : res._outer;
            i = (LibraryFeature) i.outer();
          }
      }

    if (CHECKS) check
      (Errors.any() || i == of);

    return i == null ? _fuir.error() : res;
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
  List<AbstractType> handDownThroughInheritsCalls(List<AbstractType> tl, List<AbstractCall> inh)
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
   * Helper for {@code handDown}: Change type {@code t}'s type parameters along the
   * inheritance chain {@code inh}.
   *
   * <pre>{@code
   *  ex: in this code
   *
   *    a(T type) is
   *      x T => ...
   *    b(U type) : a Sequence U  is
   *    c(V type) : b option V is
   * }</pre>
   *
   * the result type {@code T} of {@code x} if used within {@code c} must be handed down via the inheritance chain
   *
   * <pre>{@code
   *    a Sequence U
   *    b option B
   * }</pre>
   *
   * so it will be replaced by {@code Sequence (option V)}.
   *
   * @param t the type to hand down
   *
   * @param select if t is an open generic parameter, this specifies the actual
   * argument to select.
   *
   * @param inh the inheritance call chain
   *
   * @return the type {@code t} as seen after inheritance
   */
  private AbstractType handDownThroughInheritsCalls(AbstractType t, int select, List<AbstractCall> inh)
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
   * that is to be chosen. NO_SELECT otherwise.
   *
   */
  private Clazz handDown(AbstractType t, int select)
  {
    // error handling for replacing {@code .this} types of {@code ref} types in a call result, see #4273
    var err = new List<Consumer<AbstractCall>>();
    var ft = t; // final variant of t to be used in lambda
    BiConsumer<AbstractType, AbstractType> foundRef = (from,to) ->
      { err.add((c)->AstErrors.illegalOuterRefTypeInCall(c, false, feature(), ft, from, to)); };

    t = handDown(t, select, foundRef);

    var res = _fuir.type2clazz(t);
    if (res.feature().isCotype())
      {
        var ac = handDown(res._type.generics().get(0));
        res = ac.typeClazz();
      }
    if (err.size() > 0)
      {
        res._showErrorIfCallResult_ = err.get(0);
      }
    return res;
  }


  /**
   * Hand down the given type along the given inheritance chain and along all
   * inheritance chains of outer clazzes such that it has the actual type
   * parameters in this clazz.
   *
   * @param t the original type
   *
   * @param select in case t is an open generic, the variant of the actual type
   * that is to be chosen. NO_SELECT otherwise.
   *
   */
  private AbstractType handDown(AbstractType t, int select, BiConsumer<AbstractType, AbstractType> foundRef)
  {
    if (PRECONDITIONS) require
      (t != null,
       Errors.any() || t != Types.t_ERROR,
       Errors.any() || (t.isOpenGeneric() == (select >= 0)));

    for (var i = 0; i<2; i++) // NYI: UNDER DEVELOPMENT: get rid for second iteration!
      {
        // iterate using {@code child} and {@code parent} over outer clazzes starting at
        // {@code this} where {@code child} is the current outer clazz and {@code parent} is the
        // parent feature the previous inner clazz' feature was inherited from.
        var child = this;
        AbstractFeature parent = feature();
        while (child != null)
          {
            var childf = child.feature();
            if (i == 0)
              {
                // find outer that inherits this clazz, e.g.
                //
                //   Any.me =>
                //     res := Any.this
                //     res
                //   x : Any is
                //
                // here, for {@code x.me.res} inherited from {@code Any.me.res}, the
                // inheritance is two features out when {@code x} ({@code childf}) inherits
                // form {@code Any} ({@code parent}).
                t = t.replace_inherited_this_type(parent, childf, foundRef);
                var inh = childf.tryFindInheritanceChain(parent);
                if (CHECKS) check
                  (Errors.any() || inh != null);
                if (inh != null)
                  {
                    t = handDownThroughInheritsCalls(t, select, inh);
                  }
                t = t.applyTypeParsLocally(child._type, select);
                // NYI: BUG: we should not need
                // replace_this_type_by_actual_outer and replace_this_type_by_actual_outer2
                t = t.replace_this_type_by_actual_outer(child._type, foundRef);
              }
            else
              {
                // NYI: UNDER DEVELOPMENT: This currently cannot be done during
                // the first pass of the loop, need to check why (most likely it
                // performs something that is in conflict with the call to
                // {@code t.replace_inherited_this_type(parentf, childf, foundRef)} a few lines
                // above.
                t = t.replace_this_type_by_actual_outer2(child._type,
                                                         foundRef);
              }
            // NYI: UNDER DEVELOPMENT: Where is the different to just using _outer?
            child = childf.hasOuterRef() ? child.lookup(childf.outerRef()).resultClazz()
                                         : child._outer;
            parent = childf.outer();
          }
        if (CHECKS) check
          (Errors.any() || (child == null) == (parent == null));
      }
    return t;
  }


  /**
   * Convenience version of {@code handDown} with {@code select} set to {@code NO_SELECT}.
   */
  private Clazz handDown(AbstractType t)
  {
    if (PRECONDITIONS) require
      (t != null,
       Errors.any() || t != Types.t_ERROR,
       !t.isOpenGeneric());

    return handDown(t, FuzionConstants.NO_SELECT);
  }


  /**
   * Convenience version of {@code handDown} with {@code select} set to {@code NO_SELECT}. Used for
   * inlined code in inheritance for code inherited via the given inh chain.
   */
  Clazz handDown(AbstractType t, List<AbstractCall> inh)
  {
    if (PRECONDITIONS) require
      (t != null,
       Errors.any() || t != Types.t_ERROR,
       !t.isOpenGeneric(),
       inh != null);

    var t1 = handDownThroughInheritsCalls(t, FuzionConstants.NO_SELECT, inh);
    return handDown(t1);
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
    var inh = _outer == null ? null : _outer.feature().tryFindInheritanceChain(fouter.outer());
    if (inh != null &&
        inh.size() > 0)
      {
        var typesa = new AbstractType[] { ft };
        typesa = fouter.handDown(null, typesa, _outer.feature());
        types = new List<AbstractType>();
        for (var t : typesa)
          {
            types.add(t);
          }
      }
    else if (ft.isOpenGeneric() && feature().generics() == ft.genericArgument().outer().generics())
      {
        types = ft.genericArgument().replaceOpen(_type.generics());
      }
    else if (_outer != null)
      {
        types = _outer.replaceOpen(ft, fouter);
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
  private int replaceOpenCount(AbstractFeature a)
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
  private Clazz[] actualFields(Collection<AbstractFeature> feats)
  {
    var fields = new List<Clazz>();
    for (var field: feats)
      {
        if (!isVoidType() && field.isField())
          {
            if (field.isOpenGenericField())
              {
                var n = replaceOpenCount(field);
                for (var i = 0; i < n; i++)
                  {
                    fields.add(lookup(new FeatureAndActuals(field), i, false));
                  }
              }
            else
              {
                fields.add(lookup(field));
              }
          }
      }
    return fields.size() == 0 ? NO_CLAZZES
                              : fields.toArray(new Clazz[fields.size()]);
  }


  /**
   * Set of fields in this clazz, including inherited and artificially added fields.
   *
   * @return the set of fields, NO_CLAZZES if none. Never null.
   */
  Clazz[] fields()
  {
    if (PRECONDITIONS) require
      (isValue());

    if (_fields == null)
      {
        var fields = new List<Clazz>();
        for (var fieldc: _inner)
          {
            var field = fieldc.feature();
            if (!isVoidType() && field.isField())
              {
                fields.add(fieldc);
              }
          }
        _fields = fields.isEmpty() ? NO_CLAZZES
                                   : fields.toArray(new Clazz[fields.size()]);
      }
    return _fields;
  }


  /**
   * For a clazz with isRef()==true, return a value version of this clazz.
   * Returns this if it is already a value or ADDRESS.
   */
  Clazz asValue()
  {
    if (_asValue == null)
      {
        _asValue = isRef() && _type != Types.t_ADDRESS
          ? _fuir.newClazz(_outer, _type.asValue(), _select)
          : this;
      }

    if (CHECKS) check
      (_asValue.isValue());

    return _asValue;
  }

}

/* end of file */
