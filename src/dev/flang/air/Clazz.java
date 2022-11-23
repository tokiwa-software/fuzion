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

package dev.flang.air;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.ast.AbstractAssign; // NYI: remove dependency!
import dev.flang.ast.AbstractCall; // NYI: remove dependency!
import dev.flang.ast.AbstractCase; // NYI: remove dependency!
import dev.flang.ast.AbstractFeature; // NYI: remove dependency!
import dev.flang.ast.AbstractMatch; // NYI: remove dependency!
import dev.flang.ast.AbstractType; // NYI: remove dependency!
import dev.flang.ast.Call; // NYI: remove dependency!
import dev.flang.ast.Env; // NYI: remove dependency!
import dev.flang.ast.Expr; // NYI: remove dependency!
import dev.flang.ast.Feature; // NYI: remove dependency!
import dev.flang.ast.If; // NYI: remove dependency!
import dev.flang.ast.Impl; // NYI: remove dependency!
import dev.flang.ast.InlineArray; // NYI: remove dependency!
import dev.flang.ast.SrcModule; // NYI: remove dependency!
import dev.flang.ast.StatementVisitor; // NYI: remove dependency!
import dev.flang.ast.Stmnt; // NYI: remove dependency!
import dev.flang.ast.Tag; // NYI: remove dependency!
import dev.flang.ast.Types; // NYI: remove dependency!
import dev.flang.ast.Unbox; // NYI: remove dependency!

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;
import dev.flang.util.YesNo;


/**
 * Clazz represents a runtime type, i.e, a Type with actual generic arguments.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Clazz extends ANY implements Comparable<Clazz>
{


  /*-----------------------------  statics  -----------------------------*/


  //  static int counter;  {counter++; if ((counter&(counter-1))==0) System.out.println("######################"+counter+" "+this.getClass()); }
  // { if ((counter&(counter-1))==0) Thread.dumpStack(); }


  /**
   * Empty array as a result for fields() if there are no fields.
   */
  static final Clazz[] NO_CLAZZES = new Clazz[0];


  public static SrcModule _module;


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
   *
   */
  public final AbstractType _type;


  /**
   * If this clazz represents a field of an open generic type, then _select
   * chooses the actual generic parameter to be used as the type of this field.
   * Otherwise, _select is -1.
   */
  public final int _select;


  /**
   *
   */
  public final Clazz _outer;


  public final Map<AbstractFeature, Clazz> _clazzForField = new TreeMap<>();


  /**
   * Clazzes required during runtime. These are indexed by
   * Clazzes.getRuntimeClazzId and used to quickly find the actual class
   * depending on the actual generic parameters given in this class or its super
   * classes.
   */
  ArrayList<Object> _runtimeClazzes = new ArrayList<>();


  /**
   * Cached result of choiceGenerics(), only used if isChoice() and
   * !isChoiceOfOnlyRefs().
   */
  public ArrayList<Clazz> _choiceGenerics;


  /**
   * Flag that is set while the layout of objects of this clazz is determined.
   * This is used to detect recursive clazzes that contain value type fields of
   * the same type as the clazz itself.
   */
  LayoutStatus _layouting = LayoutStatus.Before;


  /**
   * Will instances of this class be created?
   */
  private boolean _isInstantiated = false;


  /**
   * Is this a normalized outer clazz? If so, there might be calls on this as an
   * outer clazz even if it is not instantiated.
   */
  public boolean _isNormalized = false;


  /**
   * Is this clazz ever called?  Usually, this is the same as isInstantiated_,
   * except for instances created by intrinsics: These are created even for
   * clazzes that are not called.
   */
  public boolean _isCalled = false;


  /**
   * Has this been found to be the static target type of a call to an outer
   * clazz.
   */
  boolean _isCalledAsOuter = false;


  /**
   * If instances of this class are created, this gives a source code position
   * that does create such an instance.  To be used in error messages.
   */
  HasSourcePosition _instantiationPos = null;


  /**
   * In case abstract methods are called on this, this lists the abstract
   * methods that have been found to do so.
   */
  TreeSet<AbstractFeature> _abstractCalled = null;


  /**
   * Set of all heirs of this clazz.
   */
  TreeSet<Clazz> _heirs = null;


  /**
   * Actual inner clazzes when calling a dynamically bound feature on this.
   *
   * This maps a feature to a Clazz. Only for fields of open generic types, this
   * maps a feature to a Clazz[] that contains the actual fields.  The array
   * might be empty.
   */
  public final Map<AbstractFeature, Object> _inner = new TreeMap<>();


  /**
   * The dynamic binding implementation used for this clazz. null if !isRef().
   */
  public Object _dynamicBinding;


  /**
   * The type of the result of calling thiz clazz.
   *
   * This is initialized after Clazz creation by dependencies().
   */
  Clazz _resultClazz;


  /**
   * The result field of this routine if it exists.
   *
   * This is initialized after Clazz creation by dependencies().
   */
  Clazz _resultField;


  /**
   * The argument fields of this routine.
   *
   * This is initialized after Clazz creation by dependencies().
   */
  Clazz[] _argumentFields;


  /**
   * The actual generics of this clazz.
   *
   * This is initialized after Clazz creation by dependencies().
   */
  Clazz[] _actualGenerics;


  /**
   * If this clazz contains a direct outer ref field, this is the direct outer
   * ref. null otherwise.
   *
   * This is initialized after Clazz creation by dependencies().
   */
  Clazz _outerRef;


  /**
   * If this clazz is a choice clazz that requires a tag, this is the tag.  null
   * otherwise.
   *
   * This is initialized after Clazz creation by dependencies().
   */
  Clazz _choiceTag;


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
   * Any data the backend might wish to store with an instance of Clazz.
   */
  public Object _backendData;


  /**
   * Cached result of isUnitType().
   */
  private YesNo _isUnitType = YesNo.dontKnow;


  /**
   * This gives the id this clazz is mapped to in FUIR.
   *
   * NYI: Remove once FUIR is based on a .fuir file and not on Clazz instances
   * and the AST.
   */
  public int _idInFUIR = -1;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param actualType the actual type this clazz is build on. The actual type
   * must not be a generic argument.
   *
   * @param select in case actualType refers to a field whose result type is an
   * open generic parameter, select specifies the actual generic to be used.
   *
   * @param outer
   */
  public Clazz(AbstractType actualType, int select, Clazz outer)
  {
    if (PRECONDITIONS) require
      (!Clazzes.closed,
       Errors.count() > 0 || !actualType.dependsOnGenerics(),
       Errors.count() > 0 || actualType.featureOfType().outer() == null || outer.feature().inheritsFrom(actualType.featureOfType().outer()),
       Errors.count() > 0 || actualType.featureOfType().outer() != null || outer == null,
       Errors.count() > 0 || (actualType != Types.t_ERROR     &&
                              actualType != Types.t_UNDEFINED   ),
       outer == null || outer._type != Types.t_ADDRESS);

    if (actualType == Types.t_UNDEFINED)
      {
        actualType = Types.t_ERROR;
      }

    this._type = actualType;
    this._select = select;
    /* NYI: Handling of outer in Clazz is not done properly yet. There are two
     * basic cases:
     *
     * 1. outer is a value type
     *
     *    in this case, we specialize all inner clazzes for every single value
     *    type outer clazz.
     *
     * 2. outer is a reference type
     *
     *    in this case, we do not specialize inner clazzes, but can normalize
     *    the outer clazz using the outer feature of the inner clazz.  This
     *    means, say we have a feature 'pop() T' within a ref clazz 'stack<T>'.
     *    There exists a ref clazz 'intStack' that inherits from 'stack<i32>'.
     *    The clazz for 'intStack.pop' then should be 'stack<i32>.pop', this
     *    clazz can be shared with all other sub-clazzes of 'stack<i32>', but
     *    not with sub-clazzes with different actual generics.
     */
    this._outer = normalizeOuter(actualType, outer);

    this._dynamicBinding = null;

    if(POSTCONDITIONS) ensure
      (!hasCycles());
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Is there any outers that share the same feature?
   */
  private boolean hasCycles()
  {
    return selfAndOuters().count() != selfAndOuters().map(x -> x.feature()).collect(Collectors.toSet()).size();
  }

  /**
   * Returns itself and all outer clazzes
   * @return
   */
  public Stream<Clazz> selfAndOuters()
  {
    return selfAndOuters(this);
  }

  /**
   * Returns clazz and all outer clazzes of clazz
   * @return
   */
  private Stream<Clazz> selfAndOuters(Clazz clazz)
  {
    if (clazz == null)
      {
        return Stream.empty();
      }
    return Stream.concat(Stream.of(clazz), selfAndOuters(clazz._outer));
  }


  /**
   * Create all the clazzes that this clazz depends on such as result type,
   * inner fields, etc.
   */
  void dependencies()
  {
    _choiceGenerics = determineChoiceGenerics();
    _argumentFields = determineArgumentFields();
    _actualGenerics = determineActualGenerics();
    _resultField = determineResultField();
    _resultClazz = determineResultClazz();
    _outerRef = determineOuterRef();
    _choiceTag = determineChoiceTag();
    _asValue = determineAsValue();
  }


  /**
   * Check if this clazz has an outer ref that is used.
   *
   * if an outer ref is used (i.e., state is resolved) to access the outer
   * instance, we must not normalize because we will need the exact type of the
   * outer instance to specialize code or to access features that only exist in
   * the specific version
   */
  private boolean hasUsedOuterRef()
  {
    var or = feature().outerRef();
    return !feature().isConstructor()  // do not specialize a constructor
      && or != null && (!(or instanceof Feature orf) || orf.state().atLeast(Feature.State.RESOLVED));
  }


  /**
   * Normalize an outer clazz for a given type. For a reference clazz that
   * inherits from f, this will return the corresponding clazz derived from
   * f. The idea is that, e.g., we do not need to distinguish conststring.length
   * from array<u8>.length.
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
    if (outer != null && !hasUsedOuterRef() && !feature().isField() && t != Types.t_ERROR)
      {
        outer = outer.normalize(t.featureOfType().outer());
      }
    return outer;
  }


  /**
   * Normalize a reference clazz to the given feature.  For a reference clazz
   * that inherits from f, this will return the corresponding clazz derived
   * from f. The idea is that, e.g., we do not need to distinguish conststring.length
   * from array<u8>.length.
   *
   * @param f the feature we want to normalize to (array in the example above).
   *
   * @return the normalized clazz.
   */
  private Clazz normalize(AbstractFeature f)
  {
    if (// an outer clazz of value type is not normalized (except for
        // univers, which was done already).
        !isRef() ||

        // optimization: if feature() is already f, there is nothing to
        // normalize anymore
        feature() == f ||

        // if an outer ref is used (i.e., state is resolved) to access the
        // outer instance, we must not normalize because we will need the
        // exact type of the outer instance to specialize code or to access
        // features that only exist in the specific version
        hasUsedOuterRef()
        )
      {
        return this;
      }
    else
      {
        var t = actualType(f.thisType()).asRef();
        return normalize2(t);
      }
  }
  private Clazz normalize2(AbstractType t)
  {
    var f = t.featureOfType();
    if (f.isUniverse())
      {
        return Clazzes.universe.get();
      }
    else
      {
        var normalized = Clazzes.create(t, normalize2(f.outer().thisType()));
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
   * @return the heirs including this.
   */
  public Set<Clazz> heirs()
  {
    if (_heirs == null)
      {
        _heirs = new TreeSet<>();
        _heirs.add(this);
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
        var pc = actualClazz(isRef() ? pt.asRef() : pt.asValue());
        if (CHECKS) check
          (Errors.count() > 0 || isRef() == pc.isRef());
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
    var result = new TreeSet<Clazz>();
    result.add(this);
    for (var p : directParents())
      {
        if (!result.contains(p))
          {
            result.addAll(p.parents());
          }
      }
    return result;
  }


  /**
   * Convert a given type to the actual type within this class. An
   * actual type does not refer to any formal generic arguments.
   *
   * @param t the original type
   */
  public AbstractType actualType(AbstractType t)
  {
    if (PRECONDITIONS) require
      (t != null,
       Errors.count() > 0 || !t.isOpenGeneric());

    return actualType(t, -1);
  }


  /**
   * Convert a given type to the actual type within this class. An
   * actual type does not refer to any formal generic arguments.
   *
   * @param t the original type
   *
   * @param select specifies the actual type parameter in case
   * t.isOpenGeneric().
   */
  public AbstractType actualType(AbstractType t, int select)
  {
    if (PRECONDITIONS) require
      (t != null,
       Errors.count() > 0 || ((select >= 0) == t.isOpenGeneric()));

    if (t.isOpenGeneric())
      {
        var types = replaceOpen(t, feature());
        if (CHECKS) check
          (Errors.count() > 0 || select >= 0 && select < types.size());
        t = 0 <= select && select < types.size() ? types.get(select) : Types.t_ERROR;
      }

    t = this._type.actualType(t);
    if (this._outer != null)
      {
        t = this._outer.actualType(t);
      }
    return Types.intern(t);
  }


  /**
   * Convert a given type to the actual runtime clazz within this class. The
   * formal generics arguments will first be replaced via actualType(t), and the
   * Clazz will be created from the result.
   *
   * @param t the original type
   */
  public Clazz actualClazz(AbstractType t)
  {
    if (PRECONDITIONS) require
      (t != null,
       Errors.count() > 0 || !t.isOpenGeneric());

    return Clazzes.clazz(actualType(t, -1));
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
    generics = this._type.replaceGenerics(generics);
    if (this._outer != null)
      {
        generics = this._outer.actualGenerics(generics);
      }
    return generics;
  }


  /**
   * The feature underlying this clazz.
   */
  public AbstractFeature feature()
  {
    return this._type.featureOfType();
  }


  /**
   * isRef
   */
  public boolean isRef()
  {
    return this._type.isRef();
  }


  /**
   * isUnitType checks if there exists only one single value in instances of
   * this clazz, so this value does not need to be stored.
   */
  public boolean isUnitType()
  {
    if (_isUnitType != YesNo.dontKnow)
      {
        return _isUnitType == YesNo.yes;
      }
    // Tricky: To avoid endless recursion, we set _isUnitType to No. In case we
    // have a recursive type, isUnitType() will return false, so recursion will
    // stop and the result for the recursive type will be false.
    //
    // Object layout will later report an error for this case.
    _isUnitType = YesNo.no;

    if (isRef() || feature().isBuiltInPrimitive() || isVoidType())
      {
        return false;
      }
    if (isChoice())
      {
        if (choiceTag() != null)
          {
            return false;
          }
        for (var cg : choiceGenerics())
          {
            if (!cg.isUnitType())
              {
                return false;
              }
          }
      }
    else
      {
        for (var f : fields())
          {
            var rc = f.resultClazz();
            if (rc == null || !rc.isUnitType())
              {
                return false;
              }
          }
      }
    _isUnitType = YesNo.yes;
    return true;
  }


  /**
   * isVoidType checks if this is void.  This is not true for user defined void
   * types, i.e., any product type, e.g.,
   *
   *    absurd (i i32, v void) is {}
   *
   * that has a field of type void is effectively a void type. This call will,
   * however, return false for ushc user defined void types.
   */
  public boolean isVoidType()
  {
    return this == Clazzes.c_void.get();
  }


  /**
   * Layout this clazz. In case a cyclic nesting of value fields is detected,
   * report an error.
   */
  void layoutAndHandleCycle()
  {
    var cycle = layout();
    if (cycle != null && Errors.count() <= AirErrors.count)
      {
        StringBuilder cycleString = new StringBuilder();
        var tp = _type.pos();
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

    createDynamicBinding();
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


  /**
   * Check if this clazz is a value type that is used as an outer instance that
   * requires dynamic binding when called.
   */
  public boolean isDynamicOuterRef()
  {
    if (!isRef())
      {
        if (isUsedAsDynamicOuterRef())
          {
            return true;
          }
        for (var p : parents())
          {
            if (p != this && p.isUsedAsDynamicOuterRef())
              {
                return true;
              }
          }
      }
    return false;
  }


  /**
   * Is this clazz the static clazz of a target of a call to a dynamic outer
   * ref.  This is slightly different to isDynamicOuterRef() which is also true
   * for all heirs.
   */
  public boolean isUsedAsDynamicOuterRef()
  {
    return this._isCalledAsOuter && hasInstantiatedHeirs();
  }


  /**
   * Create dynamic binding data for this clazz in case it is a ref.
   */
  private void createDynamicBinding()
  {
    if (this._type != Types.t_ADDRESS &&
        (isUsedAsDynamicOuterRef() || isRef()))
      {
        // NYI: This should be removed, but this still finds some clazzes that findAllClasses() missed. Need to check why.
        for (AbstractFeature f: _module.allInnerAndInheritedFeatures(feature()))
          {
            lookupIfInstantiated(f);
          }
      }
  }


  /**
   * Check if f might be called dynamically on an instance of this and if so,
   * look up the actual feature that is called at mark it as used.
   */
  private void lookupIfInstantiated(AbstractFeature f)
  {
    if (PRECONDITIONS) require
      (this._type != Types.t_ADDRESS);

    if (Clazzes.isCalledDynamically(f) &&
        (isRef() || isDynamicOuterRef()) &&
        isInstantiated())
      {
        var innerClazz = lookup(f, Call.NO_GENERICS, Clazzes.isUsedAt(f));
      }
  }


  /**
   * find redefinition of a given feature in this clazz. NYI: This will have to
   * take the whole inheritance chain into account including the parent view that is
   * being filled with live:
   */
  private AbstractFeature findRedefinition(AbstractFeature f)
  {
    var fn = f.featureName();
    var tf = feature();
    if (tf != Types.f_ERROR && f != Types.f_ERROR && tf != Types.resolved.f_void)
      {
        var chain = tf.findInheritanceChain(f.outer());
        if (CHECKS) check
          (chain != null || Errors.count() > 0);
        if (chain != null)
          {
            for (var p: chain)
              {
                fn = f.outer().handDown(_module, f, fn, p, feature());  // NYI: need to update f/f.outer() to support several levels of inheritance correctly!
              }
          }
      }
    return _module.lookupFeature(feature(), fn);
  }


  /**
   * Lookup the code to call the feature f from this clazz using dynamic binding
   * if needed.
   *
   * This is not intended for use at runtime, but during analysis of static
   * types or to fill the virtual call table.
   *
   * @param f the feature that is called
   *
   * @param actualGenerics the actual generics provided in the call,
   * Call.NO_GENERICS if none.
   *
   * @param p if this lookup would result in the returned feature to be called,
   * p gives the position in the source code that causes this call.  p must be
   * null if the lookup does not causes a call, but it just done to determine
   * the type.
   *
   * @return the inner clazz of the target in the call.
   */
  public /* NYI: make package private */ Clazz lookup(AbstractFeature f,
                      List<AbstractType> actualGenerics,
                      HasSourcePosition p)
  {
    if (PRECONDITIONS) require
      (f != null,
       !this.isVoidType());

    return lookup(f, -1, actualGenerics, p, false);
  }


  /**
   * Lookup the code to call the feature f from this clazz using dynamic binding
   * if needed.
   *
   * This is not intended for use at runtime, but during analysis of static
   * types or to fill the virtual call table.
   *
   * @param f the feature that is called
   *
   * @param select in case f is a field of open generic type, this selects the
   * actual field.  -1 otherwise.
   *
   * @param actualGenerics the actual generics provided in the call,
   * Call.NO_GENERICS if none.
   *
   * @param p if this lookup would result in the returned feature to be called,
   * p gives the position in the source code that causes this call.  p must be
   * null if the lookup does not causes a call, but it just done to determine
   * the type.
   *
   * @param isInstantiated true iff this is a call in an inheritance clause.  In
   * this case, the result clazz will not be marked as instantiated since the
   * call will work on the instance of the inheriting clazz.
   *
   * @return the inner clazz of the target in the call.
   */
  Clazz lookup(AbstractFeature f,
               int select,
               List<AbstractType> actualGenerics,
               HasSourcePosition p,
               boolean isInheritanceCall)
  {
    if (PRECONDITIONS) require
      (f != null,
       !this.isVoidType());

    Clazz innerClazz = null;
    Clazz[] innerClazzes = null;
    if (actualGenerics.isEmpty())
      {
        if (select < 0)
          {
            var iC = _inner.get(f);
            if (CHECKS) check
              (Errors.count() > 0 || iC == null || iC instanceof Clazz);

            innerClazz =
              iC == null              ? null :
              iC instanceof Clazz iCC ? iCC
                                      : Clazzes.error.get();
          }
        else
          {
            var iCs = _inner.get(f);
            if (CHECKS) check
              (Errors.count() > 0 || iCs == null || iCs instanceof Clazz[]);
            if (iCs == null || !(iCs instanceof Clazz[] iCA))
              {
                innerClazzes = new Clazz[replaceOpenCount(f)];
                _inner.put(f, innerClazzes);
              }
            else
              {
                innerClazzes = iCA;
              }
            if (CHECKS) check
              (Errors.count() > 0 || select < innerClazzes.length);
            innerClazz = select < innerClazzes.length ? innerClazzes[select] : Clazzes.error.get();
          }
      }
    if (innerClazz == null)
      {
        AbstractFeature af = findRedefinition(f);
        if (CHECKS) check
          (Errors.count() > 0 || af != null);

        if (f == Types.f_ERROR || af == null)
          {
            innerClazz = Clazzes.error.get();
          }
        else
          {
            if (af.kind() == AbstractFeature.Kind.Abstract)
              {
                if (_abstractCalled == null)
                  {
                    _abstractCalled = new TreeSet<>();
                  }
                _abstractCalled.add(af);
              }

            AbstractType t = af.thisType().actualType(af, actualGenerics);
            t = actualType(t);
            innerClazz = Clazzes.clazzWithSpecificOuter(t, select, this);
            if (actualGenerics.isEmpty())
              {
                if (select < 0)
                  {
                    _inner.put(f, innerClazz);
                  }
                else
                  {
                    innerClazzes[select] = innerClazz;
                  }
                if (f.isField())
                  {
                    clazzForFieldX(f, select);
                  }
              }
            if (CHECKS) check
              (innerClazz._type == Types.t_ERROR || innerClazz._type.featureOfType() == af);
          }
      }
    if (innerClazz != null && p != null && !isInheritanceCall)
      {
        innerClazz.called(p);
        if (!isInheritanceCall)
          {
            innerClazz.instantiated(p);
          }
      }

    if (POSTCONDITIONS) ensure
      (Errors.count() > 0 || findRedefinition(f) == null || innerClazz._type != Types.t_ERROR,
       innerClazz != null);

    return innerClazz;
  }


  /**
   * Get the runtime clazz of a field in this clazz.
   *
   * NYI: try to remove, used only in interpreter
   *
   * @param field a field
   *
   * @param select in case field has an open generic type, this selects the
   * actual field. -1 otherwise.
   */
  public Clazz clazzForFieldX(AbstractFeature field, int select)
  {
    if (CHECKS) check
      (Errors.count() > 0 || field.isField(),
       Errors.count() > 0 || feature().inheritsFrom(field.outer()));

    var result = _clazzForField.get(field);
    if (result == null)
      {
        result =
          field.isOuterRef() &&
          field.outer().isOuterRefAdrOfValue() ? actualClazz(Types.t_ADDRESS)
                                               : lookup(field, select, Call.NO_GENERICS, Clazzes.isUsedAt(field), false).resultClazz();
        if (select < 0)
          {
            _clazzForField.put(field, result);
          }
      }
    return result;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return
      (this._type == Types.t_ERROR   ||
       this._type == Types.t_ADDRESS ||
       this._outer == null              )
      ? this._type.toString() // error, address or universe
      : (""
         + ((this._outer == Clazzes.universe.get())
            ? ""
            : this._outer.toString() + ".")
         + (this.isRef()
            ? "ref "
            : ""
            )
         + feature().featureName().baseName() + (this._type.generics().isEmpty()
                                                 ? ""
                                                 : "<" + this._type.generics() + ">"));
  }


  /**
   * toString
   *
   * @return
   */
  public String toString2()
  {
    return "CLAZZ:" + this._type + (this._outer != null ? " in " + this._outer : "");
  }


  /**
   * Check if a value of clazz other can be assigned to a field of this clazz.
   *
   * @other the value to be assigned to a field of type this
   *
   * @return true iff other can be assigned to a field of type this.
   */
  @Deprecated(forRemoval = true) // NYI only isDirectlyAssignableFrom should be used after AST
  public boolean isAssignableFrom(Clazz other)
  {
    return this._type.isAssignableFrom(other._type);
  }


  /**
   * Check if a value of clazz other can be assigned to a field of this clazz
   * without the need for tagging.
   *
   * @other the value to be assigned to a field of type this
   *
   * @return true iff other can be assigned to a field of type this.
   */
  public boolean isDirectlyAssignableFrom(Clazz other)
  {
    return this._type.isDirectlyAssignableFrom(other._type);
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
       other.getClass() == Clazz.class,
       this ._type == Types.intern(this ._type),
       other._type == Types.intern(other._type));

    var result =
      this._select < other._select ? -1 :
      this._select > other._select ? +1 : this._type.compareToIgnoreOuter(other._type);
    if (result == 0)
      {
        result = compareOuter(other);
      }
    return result;
  }


  /**
   * visit all the code in f, including inherited features, by fc.
   */
  private void inspectCode(StatementVisitor fc, AbstractFeature f)
  {
    f.visitStatements(fc);
    for (var c: f.inherits())
      {
        AbstractFeature cf = c.calledFeature();
        var n = c.actuals().size();
        for (var i = 0; i < n; i++)
          {
            var a = c.actuals().get(i);
            if (i >= cf.valueArguments().size())
              {
                if (CHECKS) check
                  (Errors.count() > 0);
              }
            else
              {
                var cfa = cf.valueArguments().get(i);
                var ccc = lookup(cfa, Call.NO_GENERICS, Clazzes.isUsedAt(f));
                if (c._parentCallArgFieldIds < 0)
                  {
                    c._parentCallArgFieldIds = Clazzes.getRuntimeClazzIds(n);
                  }
                Clazz.this.setRuntimeData(c._parentCallArgFieldIds+i, ccc);
              }
          }

        if (CHECKS) check
          (Errors.count() > 0 || cf != null);

        if (cf != null)
          {
            inspectCode(fc, cf);
          }
      }
  }


  /**
   * Find all inner clazzes of this that are referenced when this is executed
   */
  void findAllClasses()
  {
    if (this._type != Types.t_ADDRESS)
      {
        var f = feature();
        inspectCode(new StatementVisitor()
          {
            public void action (Stmnt s)
            {
              if      (s instanceof Unbox          u) { Clazzes.findClazzes(u, Clazz.this); }
              else if (s instanceof AbstractAssign a) { Clazzes.findClazzes(a, Clazz.this); }
              else if (s instanceof AbstractCall   c) { Clazzes.findClazzes(c, Clazz.this); }
              else if (s instanceof If             i) { Clazzes.findClazzes(i, Clazz.this); }
              else if (s instanceof InlineArray    i) { Clazzes.findClazzes(i, Clazz.this); }
              else if (s instanceof Env            b) { Clazzes.findClazzes(b, Clazz.this); }
              else if (s instanceof AbstractMatch  m) { Clazzes.findClazzes(m, Clazz.this); }
              else if (s instanceof Tag            t) { Clazzes.findClazzes(t, Clazz.this); }
            }
            public void action(AbstractCase c)
            {
              Clazzes.findClazzes(c, Clazz.this);
            }
          },
          f);

        for (AbstractFeature ff: _module.allInnerAndInheritedFeatures(f))
          {
            Clazzes.whenCalledDynamically(ff, () -> lookupIfInstantiated(ff));
          }
      }
  }


  /**
   * During findClazzes, store data for a given id.
   *
   * @param id the id obtained via AbstractFeature.getRuntimeClazzId()
   *
   * @param data the data to be stored for this id.
   */
  public void setRuntimeData(int id, Object data)
  {
    if (PRECONDITIONS) require
      (id >= 0,
       id < Clazzes.runtimeClazzIdCount());

    int cnt = Clazzes.runtimeClazzIdCount();
    this._runtimeClazzes.ensureCapacity(cnt);
    while (this._runtimeClazzes.size() < cnt)
      {
        this._runtimeClazzes.add(null);
      }
    this._runtimeClazzes.set(id, data);

    if (POSTCONDITIONS) ensure
      (getRuntimeData(id) == data);
  }


  /**
   * During execution, retrieve the data stored for given id.
   *
   * @param id the id used in setRuntimeData
   *
   * @return the data stored for this id.
   */
  public Object getRuntimeData(int id)
  {
    if (PRECONDITIONS) require
      (id < Clazzes.runtimeClazzIdCount(),
       id >= 0);

    return this._runtimeClazzes.get(id);
  }


  /**
   * During findClazzes, store a clazz for a given id.
   *
   * @param id the id obtained via Clazzes.getRuntimeClazzId()
   *
   * @param cl the clazz to be stored for this id.
   */
  public void setRuntimeClazz(int id, Clazz cl)
  {
    if (PRECONDITIONS) require
      (id >= 0,
       id < Clazzes.runtimeClazzIdCount());

    setRuntimeData(id, cl);

    if (POSTCONDITIONS) ensure
      (getRuntimeClazz(id) == cl);
  }


  /**
   * During execution, retrieve the clazz stored for given id.
   *
   * @param id the id used in setRuntimeClazz
   *
   * @return the clazz stored for this id.
   */
  public Clazz getRuntimeClazz(int id)
  {
    if (PRECONDITIONS) require
      (id < Clazzes.runtimeClazzIdCount());

    return (Clazz) getRuntimeData(id);
  }


  /**
   * Is this a choice-type, i.e., does it directly inherit from choice?
   */
  public boolean isChoice()
  {
    return feature().isChoice();
  }


  /**
   * Is this a routine-type, i.e., a function returning a result or a constructor?
   */
  public boolean isRoutine()
  {
    return feature().isRoutine();
  }


  /**
   * Is this a choice-type whose actual generics inlude ref?  If so, a field for
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
  private ArrayList<Clazz> determineChoiceGenerics()
  {
    ArrayList<Clazz> result;

    if (isChoice())
      {
        result = new ArrayList<>();
        for (var t : actualGenerics(feature().choiceGenerics()))
          {
            result.add(actualClazz(t));
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
  public ArrayList<Clazz> choiceGenerics()
  {
    if (PRECONDITIONS) require
      (isChoice());

    return _choiceGenerics;
  }


  /**
   * Determine the index of the generic argument of this choice type that
   * matches the given static type.
   */
  public int getChoiceTag(AbstractType staticTypeOfValue)
  {
    if (PRECONDITIONS) require
      (isChoice(),
       !staticTypeOfValue.dependsOnGenerics(),
       staticTypeOfValue == Types.intern(staticTypeOfValue));

    int result = -1;
    int index = 0;
    for (Clazz g : _choiceGenerics)
      {
        if (g._type.isDirectlyAssignableFrom(staticTypeOfValue))
          {
            if (CHECKS) check
              (result < 0);
            result = index;
          }
        index++;
      }
    if (CHECKS) check
      (result >= 0);

    return result;
  }

  /**
   * For a choice clazz, get the clazz that corresponds to the generic
   * argument to choice at index id (0..n-1).
   *
   * @param id the index of the paramenter
   */
  public Clazz getChoiceClazz(int id)
  {
    if (PRECONDITIONS) require
      (isChoice(),
       id >= 0,
       id <  _choiceGenerics.size());

    return _choiceGenerics.get(id);
  }


  /**
   * Mark this as called at given source code position.
   *
   * @param at gives the position in the source code that causes this instantiation.  p can be
   * null, which means that this should not be marked as called.
   */
  void called(HasSourcePosition at)
  {
    if (PRECONDITIONS) require
      (Errors.count() > 0 || !isChoice());

    if (at != null &&
        (_outer == null || !_outer.isVoidType()) &&
        !_isCalled)
      {
        _isCalled = true;

        if (isCalled())
          {
            var l = Clazzes._whenCalled_.remove(this);
            if (l != null)
              {
                for (var r : l)
                  {
                    r.run();
                  }
              }
            if (feature().isIntrinsic())
              {
                intrinsicCalled(at);
              }
          }
      }
  }


  /**
   * Find clazzes required by intrinsic method that has been found be to called.
   *
   * This includes results of intrinsic contructors are any specifc clazzes
   * required for specific instrinsics, e.g., clazzes called from the intrinsic.
   *
   * @param at position that the intrinsic has been found to be called.
   */
  void intrinsicCalled(HasSourcePosition at)
  {
    if (PRECONDITIONS) require
      (feature().isIntrinsic(),
       isCalled());

    // value instances returned from intrinsics are automatically
    // recorded to be instantiated, refs only if intrinsic is marked as
    // 'intrinsic_constructor'.
    var rc = resultClazz();
    if (rc.isChoice())
      {
        if (feature().isIntrinsicConstructor())
          {
            for (var cg : rc.choiceGenerics())
              {
                cg.instantiated(at);
              }
          }
      }
    else if (!rc.isRef() || feature().isIntrinsicConstructor())
      {
        rc.instantiated(at);
      }

    switch (feature().qualifiedName())
      {
      case "effect.abortable":
        argumentFields()[0].resultClazz().lookup(Types.resolved.f_function_call, Call.NO_GENERICS, at);
        break;
      case "fuzion.sys.thread.spawn0":
        argumentFields()[0].resultClazz().lookup(Types.resolved.f_function_call, Call.NO_GENERICS, at);
        break;
      default: break;
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
        _instantiationPos = at;
      }
  }


  /**
   * Is this clazz called?  This tests this._isCalled and isInstantiated() and !isAbsurd().
   */
  public boolean isCalled()
  {
    return _isCalled && isOuterInstantiated() && !feature().isAbstract() &&
      (_argumentFields == null || /* this may happen when creating deterḿining isUnitType() on cyclic value type, will cause an error during layout() */
       !isAbsurd());
  }

  /**
   * Is this clazz absured, i.e., does it have any arguments of type void?
   */
  public boolean isAbsurd()
  {
    if (PRECONDITIONS) require
      (_argumentFields != null);

    if (false)  // streams version is significantly slower
      {
        return Arrays.stream(argumentFields())
                     .anyMatch(a -> a.resultClazz().isVoidType());
      }
    else  // array iteration version is fast:
      {
        for (var a : argumentFields())
          {
            if (a.resultClazz().isVoidType())
              {
                return true;
              }
          }
      }
    return false;
  }


  /**
   * Check of _outer is instantiated.
   */
  private boolean isOuterInstantiated()
  {
    return _outer == null ||

      // NYI: Once Clazz.normalize() is implemented better, a clazz C has
      // to be considered instantiated if there is any clazz D that
      // normalize() would replace by C if it occurs as an outer clazz.
      _outer == Clazzes.object.getIfCreated() ||
      _outer == Clazzes.string.getIfCreated() ||

      _outer._isNormalized ||

      _outer.isInstantiated();
  }


  /**
   * Flag to detetect endless recursion between isInstantiated() and
   * isRefWithInstantiatedHeirs(). This may happen in a clazz that inherits from
   * its outer clazz.
   */
  private int _checkingInstantiatedHeirs = 0;


  /**
   * Helper for isInstantiated to check if outer clazz this is a ref and there
   * are heir clazzes of this that are refs and that are instantiated.
   *
   * @return true iff this is a ref and there exists a heir of this that is
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
    return this == Clazzes.fuzionSysArray_u8 ||
      this == Clazzes.conststring.get() ||
      _checkingInstantiatedHeirs>0 || (isOuterInstantiated() || isChoice() || _outer.isRef() && _outer.hasInstantiatedHeirs()) && _isInstantiated;
  }


  /**
   * Check if this and all its (potentially normalized) outer clazzes are instantiated.
   */
  public boolean allOutersInstantiated()
  {
    return isInstantiated() && (_outer == null || _outer.allOutersInstantiated2());
  }


  /**
   * Helper for allOutersInstantiated to check outers if they are either
   * instantiated directly or are refs that have instantiated heirs.
   */
  private boolean allOutersInstantiated2()
  {
    return (isInstantiated() || isRef() && hasInstantiatedHeirs()) && (_outer == null || _outer.allOutersInstantiated2());
  }


  /**
   * Perform checks on classes such as that an instantiated clazz is not the
   * target of any calls to abstract methods that are not implemented by this
   * clazz.
   */
  public void check()
  {
    if (isInstantiated() && _abstractCalled != null)
      {
        AirErrors.abstractFeatureNotImplemented(feature(), _abstractCalled, _instantiationPos);
      }
  }


  /**
   * In case this is a Clazz of value type, create the corresponding reference clazz.
   */
  public Clazz asRef()
  {
    return isRef()
      ? this
      : Clazzes.create(_type.asRef(), _outer);
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
            outer = Clazzes.clazz(target, this);
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
    return _resultClazz;
  }


  /**
   * Determine the nesting level of a given feature f. The nesting level is 0
   * for the universe, 1 for any feature declared in the universe, and
   * depth(f.outer())+1 for all the rest.
   */
  private int depth(AbstractFeature f)
  {
    if (CHECKS) check
      (Errors.count() > 0 || f.isUniverse() || f.outer() != null);

    return f.isUniverse() || (f.outer() == null)
      ? 0
      : depth(f.outer()) + 1;
  }


  /**
   * For a type clazz such as 'i32.type', this will set the type this clazz
   * represents.
   *
   * NYI: This is currently set in Clazzes.findClasses() when processing
   * TypeParameters.  It would be nicer (less error prone etc.) to have this
   * information available directly when this instance of Clazz is created.
   *
   * Maybe if we added a type parameter to feature 'Type' or to all instances
   * inheriting from 'Type', we could have this information available directly.
   */
  AbstractType _typeType = null;


  /**
   * For a type clazz such as 'i32.type' return its name, such as 'i32'.
   */
  public String typeName()
  {
    if (isRef()) // the type was boxed, so get the name from the original value type
      {
        return asValue().typeName();
      }
    else if (_typeType == null)
      {
        Errors.error("*** internal error: type name is not set for '" + this + "'");
        return "** UNDEF **";
      }
    else
      {
        return _typeType.asString();
      }
  }


  /**
   * For a type parameter, return the actual type.
   */
  public Clazz typeParameterActualType()
  {
    if (PRECONDITIONS) require
      (feature().isTypeParameter());

    var f = feature();

    if (_outer.feature() != f.outer())
      {
        if (Errors.count() == 0)
          {
            throw new Error("NYI: cannot find actual generic for '"+f.qualifiedName()+"' in heir outer clazz '" + _outer + "'.");
          }
        else
          {
            return Clazzes.error.get();
          }
      }
    return _outer.actualGenerics()[f.typeParameterIndex()];
  }


  /**
   * For a clazz a.b.c the corresponding type clazz a.b.c.type, which is,
   * actually, a.type.b.type.c.type.
   */
  Clazz typeClazz()
  {
    var cf = _type.featureOfType();
    if (cf.isUniverse())
      {
        return this;
      }
    else
      {
        var tf = cf.typeFeature();
        return Clazzes.create(tf.thisType(), _outer.typeClazz());
      }
  }


  /**
   * Determine the clazz of the result of calling this clazz.
   *
   * @return the result clazz.
   */
  private Clazz determineResultClazz()
  {
    var f = feature();
    var of = _outer != null ? _outer.feature() : null;

    if (f.isConstructor())
      {
        return this;
      }
    else if (f.isOuterRef())
      {
        return _outer.inheritedOuterRefClazz(_outer._outer, null, f, _outer.feature(), null);
      }
    else if (f.isTypeParameter())
      {
        return typeParameterActualType().typeClazz();
      }
    else if (f  == Types.resolved.f_Types_get ||
             of == Types.resolved.f_Types_get && f == of.resultField())
      // NYI (see #282): Would be nice if this would not need special handlng but would
      // work in general for any feature with type parameters that returns one
      // of this type parameters as its result using '=>'.
      {
        var ag = (f == Types.resolved.f_Types_get ? this : _outer).actualGenerics();
        return ag[0].typeClazz();
      }
    else
      {
        var ft = f.resultType();
        var t = _outer.actualType(ft, _select);
        if (!t.dependsOnGenerics())
          {
            /* We have this situation:

               a is
                 b is
                   c is
                     f t.u.v.w.x.y.z
                 t is
                   u is
                     v is
                       w is
                         x is
                           y is
                             z is

                p is
                  q is
                    r : a is

                p.q.r.b.c.f

               so f.depth is 4 (a.b.c.f),
               t.featureOfType().depth() is 8 (a.t.u.v.w.x.y.z),
               inner.depth is 6 (p.q.r.b.c.f) and
               depthInSource is 7 (t.u.v.w.x.y.z). We have to
               go back 3 (6-4+1) levels in inner, i.e,. p.q.r.b.c.f -> p.q.r.*,
               and 7 levels in t (a.t.u.v.w.x.y.z -> *.t.u.v.w.x.y.z) to rebase t
               to become p.q.r.t.u.v.w.x.y.z.

               f:                       a.b.c.f
               t:                       a.t,u.v.w.x.y.z
               inner:                   p.q.r.b.c.f
               depthInSource              t.u.v.w.x.y.z
               back 3:                  p.q.r.*
               depthInSource part of t: *.t.u.v.w.x.y.z
               plugged together:        p.q.r.t.u.v.w.x.y.z

             */
            /* NYI: This implementation currently ignores depthInSource that could be determined via
               ((dev.flang.ast.FunctionReturnType) f.returnType).depthInSource (more complicated when
               type inference is used). We need proper tests for this and implement it for
               depthInSource > 1.
             */
            int goBack = depth(f) - depth(t.featureOfType()) + 1;
            var innerBase = this;
            while (goBack > 0 &&
                   innerBase._outer != null // NYI: this sometimes overflows if chain of outer's is shorter then f's outers.  This whole code is broken and needs to be rewritten!
                   )
              {
                innerBase = innerBase._outer;
                goBack--;
              }
            if (t.featureOfType().outer() == null || innerBase.feature().inheritsFrom(t.featureOfType().outer()))
              {
                var res = innerBase == null || t == Types.t_UNDEFINED || t == Types.t_ERROR
                  ? Clazzes.create(t, null)
                  : innerBase.lookup(t.featureOfType(), t.generics(), null);
                if (t.isRef())
                  {
                    res = res.asRef();
                  }
                return res;
              }
            else
              {
                // NYI: This branch should never be taken when rebasing above is implemented correctly.
                if (f.implKind() == Impl.Kind.FieldDef)
                  {
                    return Clazzes.clazz(f.initialValue(), this._outer);
                  }
                else if (f.implKind() == Impl.Kind.RoutineDef)
                  {
                    /* NYI: Do we need special handling for inferred routine result as well?
                     *
                     *   return Clazzes.clazz(f.initialValue(), this._outer);
                     */
                  }
                return actualClazz(t);
              }
          }
        else
          {
            return actualClazz(t);
          }
      }
  }


  /**
   * Get the result field of this routine if it exists.
   *
   * @return the result field or null.
   */
  public Clazz resultField()
  {
    return _resultField;
  }


  /**
   * Determine the result field of this routine if it exists.
   *
   * @return the result field or null.
   */
  private Clazz determineResultField()
  {
    var f = feature();
    var r = f.resultField();
    return r == null
      ? null
      : lookup(r, Call.NO_GENERICS, Clazzes.isUsedAt(r));
  }


  /**
   * Get the argument fields of this routine
   *
   * @return the argument fields.
   */
  public Clazz[] argumentFields()
  {
    if (PRECONDITIONS) require
      (_argumentFields != null);

    return _argumentFields;
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


  /**
   * Get the actual generic arguments of this clazz
   *
   * @return the actual generics
   */
  public Clazz[] actualGenerics()
  {
    return _actualGenerics;
  }


  /**
   * Determine the actual generic arguments of this clazz
   *
   * @return the actual generic argument
   */
  private Clazz[] determineActualGenerics()
  {
    var result = NO_CLAZZES;
    var gs = _type.generics();
    if (!gs.isEmpty())
      {
        result = new Clazz[gs.size()];
        for (int i = 0; i < gs.size(); i++)
          {
            result[i] = actualClazz(gs.get(i));
          }
      }
    return result;
  }


  /**
   * If this clazz contains a direct outer ref field, this is the direct outer
   * ref. null otherwise.
   */
  public Clazz outerRef()
  {
    return _outerRef;
  }


  /**
   * Determine the clazz of this clazz' direct outer ref field if it exists.
   *
   * @return the direct outer ref field, null if none.
   */
  private Clazz determineOuterRef()
  {
    Clazz result = null;
    var f = feature();
    switch (f.kind())
      {
      case Intrinsic  :
      case Routine    :
        {
          var or = f.outerRef();
          if (or != null && Clazzes.isUsedAtAll(or))
            {
              result = lookup(or, Call.NO_GENERICS, Clazzes.isUsedAt(or));
            }
          break;
        }
      }
    return result;
  }


  /**
   * If this clazz is a choice clazz that requires a tag, this will return the
   * tag.  null otherwise.
   */
  public Clazz choiceTag()
  {
    return _choiceTag;
  }


  /**
   * If this is a choice clazz that requires a tag, determine the clazz of this
   * tag field.
   *
   * @return the tag field, or null if this is not a choice or this is a choice
   * that does not need a tag.
   */
  private Clazz determineChoiceTag()
  {
    Clazz result = null;
    if (isChoice() && !isChoiceOfOnlyRefs())
      {
        var f = feature();
        result = lookup(f.choiceTag(), Call.NO_GENERICS, f);
      }
    return result;
  }


  /**
   * For an open generic type ft find the actual type parameters within this
   * clazz.  The resulting list could be empty.
   *
   * @param ft the type that is an open generic
   *
   * @param fouter the outer feature where ft is used. This might be a heir of
   * _outer.feature() in case ft is the result type of an inherited feature.
   */
  List<AbstractType> replaceOpen(AbstractType ft, AbstractFeature fouter)
  {
    if (PRECONDITIONS) require
      (Errors.count() > 0 || ft.isOpenGeneric());

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
    else if (ft.isOpenGeneric() && feature().generics() == ft.genericArgument().formalGenerics())
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
          (Errors.count() > 0);
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
      (Errors.count() > 0 || a != Types.f_ERROR || a.resultType().isOpenGeneric());

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
  Clazz[] actualFields(Collection<AbstractFeature> feats)
  {
    var fields = new List<Clazz>();
    for (var field: feats)
      {
        if (!this.isVoidType() &&
            field.isField() &&
            field == findRedefinition(field) && // NYI: proper field redefinition handling missing, see tests/redef_args/*
            Clazzes.isUsed(field, this))
          {
            if (field.isOpenGenericField())
              {
                var n = replaceOpenCount(field);
                for (var i = 0; i < n; i++)
                  {
                    fields.add(lookup(field, i, Call.NO_GENERICS, Clazzes.isUsedAt(field), false));
                  }
              }
            else
              {
                fields.add(lookup(field, Call.NO_GENERICS, Clazzes.isUsedAt(field)));
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
  public Clazz[] fields()
  {
    if (_fields == null)
      {
        _fields =
          isChoice()              ||
          // note that intrinsics may have fields that are used in the intrinsic's pre-condition!
          false && isRef() /* NYI: would be good to add isRef() here and create _fields only for value types, does not work with C backend yet */
          ? NO_CLAZZES
          : actualFields(_module.allInnerAndInheritedFeatures(feature()));
      }
    return isRef() ? NO_CLAZZES : _fields;
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

    int i = 0;
    for (var f : _outer._fields)
      {
        if (f == this)
          {
            return i;
          }
        i++;
      }
    throw new Error("Clazz.fieldIndex() did not find field " + this + " in " + _outer);
  }


  /**
   * For a clazz with isRef()==true, determine a value version of this clazz.
   * Returns this if it is already a value or ADDRESS.
   */
  private Clazz determineAsValue()
  {
    return isRef() && _type != Types.t_ADDRESS
      ? Clazzes.create(_type.asValue(), _outer)
      : this;
  }


  /**
   * For a clazz with isRef()==true, return a value version of this clazz.
   * Returns this if it is already a value or ADDRESS.
   */
  public Clazz asValue()
  {
    return _asValue;
  }


  /**
   * For a given field fc whose outer instance is a value type, find the same
   * field in the corresponding outer ref type.
   *
   * @param fc a field in a value instance
   */
  public Clazz correspondingFieldInRefInstance(Clazz fc)
  {
    var oc = fc._outer.asRef();
    var g = new List<AbstractType>();
    for (var ag : fc.actualGenerics())
      {
        g.add(ag._type);
      }
    return oc.lookup(fc.feature(), fc._select, g, Clazzes.isUsedAt(fc.feature()), false);
  }


  /**
   * For a given field fc whose outer instance is a ref type, find the same
   * field in the corresponding outer value type.
   *
   * @param fc a field in a ref instance
   */
  public Clazz correspondingFieldInValueInstance(Clazz fc)
  {
    var oc = fc._outer.asValue();
    var g = new List<AbstractType>();
    for (var ag : fc.actualGenerics())
      {
        g.add(ag._type);
      }
    return oc.lookup(fc.feature(), fc._select, g, Clazzes.isUsedAt(fc.feature()), false);
  }

}

/* end of file */
