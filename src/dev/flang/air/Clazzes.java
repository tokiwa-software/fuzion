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
 * Source of class Clazzes
 *
 *---------------------------------------------------------------------*/

package dev.flang.air;

import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.ast.AbstractAssign; // NYI: remove dependency!
import dev.flang.ast.AbstractBlock; // NYI: remove dependency!
import dev.flang.ast.AbstractCall; // NYI: remove dependency!
import dev.flang.ast.AbstractCase; // NYI: remove dependency!
import dev.flang.ast.AbstractConstant; // NYI: remove dependency!
import dev.flang.ast.AbstractCurrent; // NYI: remove dependency!
import dev.flang.ast.AbstractFeature; // NYI: remove dependency!
import dev.flang.ast.AbstractMatch; // NYI: remove dependency!
import dev.flang.ast.AbstractType; // NYI: remove dependency!
import dev.flang.ast.Box; // NYI: remove dependency!
import dev.flang.ast.Env; // NYI: remove dependency!
import dev.flang.ast.Expr; // NYI: remove dependency!
import dev.flang.ast.If; // NYI: remove dependency!
import dev.flang.ast.InlineArray; // NYI: remove dependency!
import dev.flang.ast.State; // NYI: remove dependency!
import dev.flang.ast.Tag; // NYI: remove dependency!
import dev.flang.ast.Types; // NYI: remove dependency!
import dev.flang.ast.Universe; // NYI: remove dependency!

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionOptions;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Clazzes manages the actual clazzes used in the system during runtime.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Clazzes extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * All clazzes found in the system.
   *
   * NYI: One of these maps is probably redundant!
   */
  private static final Map<Clazz, Clazz> clazzes = new TreeMap<>();
  private static final Map<AbstractType, Clazz> _clazzesForTypes_ = new TreeMap<>();


  /**
   * All clazzes found so far that have not been analyzed yet for clazzes that
   * they require.
   */
  private static final LinkedList<Clazz> clazzesToBeVisited = new LinkedList<>();


  static interface TypF
  {
    AbstractType get();
  }
  public static class OnDemandClazz
  {
    final TypF _t;
    Clazz _clazz = null;
    OnDemandClazz(TypF t) { _t = t; }
    OnDemandClazz() { this(null); }

    /**
     * Get this clazz only if it was created, by a call to get() or by direct
     * call to Clazzes.create():
     */
    public Clazz getIfCreated()
    {
      if (_clazz == null && _clazzesForTypes_.containsKey(_t.get()))
        {
          _clazz = clazz(_t.get());
        }
      return _clazz;
    }
    public Clazz get()
    {
      if (_clazz == null)
        {
          _clazz = clazz(_t.get());
        }
      return _clazz;
    }
    public void clear()
    {
      _clazz = null;
    }
  }

  /**
   * Handy preallocated classes to be used during execution:
   */
  public static final OnDemandClazz universe    = new OnDemandClazz(() -> Types.resolved.universe.selfType());
  public static final OnDemandClazz c_void      = new OnDemandClazz(() -> Types.resolved.t_void             );
  public static final OnDemandClazz bool        = new OnDemandClazz(() -> Types.resolved.t_bool             );
  public static final OnDemandClazz c_TRUE      = new OnDemandClazz(() -> Types.resolved.f_TRUE .selfType() );
  public static final OnDemandClazz c_FALSE     = new OnDemandClazz(() -> Types.resolved.f_FALSE.selfType() );
  public static final OnDemandClazz i8          = new OnDemandClazz(() -> Types.resolved.t_i8               );
  public static final OnDemandClazz i16         = new OnDemandClazz(() -> Types.resolved.t_i16              );
  public static final OnDemandClazz i32         = new OnDemandClazz(() -> Types.resolved.t_i32              );
  public static final OnDemandClazz i64         = new OnDemandClazz(() -> Types.resolved.t_i64              );
  public static final OnDemandClazz u8          = new OnDemandClazz(() -> Types.resolved.t_u8               );
  public static final OnDemandClazz u16         = new OnDemandClazz(() -> Types.resolved.t_u16              );
  public static final OnDemandClazz u32         = new OnDemandClazz(() -> Types.resolved.t_u32              );
  public static final OnDemandClazz u64         = new OnDemandClazz(() -> Types.resolved.t_u64              );
  public static final OnDemandClazz f32         = new OnDemandClazz(() -> Types.resolved.t_f32              );
  public static final OnDemandClazz f64         = new OnDemandClazz(() -> Types.resolved.t_f64              );
  public static final OnDemandClazz ref_i8      = new OnDemandClazz(() -> Types.resolved.t_i8 .asRef()      );
  public static final OnDemandClazz ref_i16     = new OnDemandClazz(() -> Types.resolved.t_i16.asRef()      );
  public static final OnDemandClazz ref_i32     = new OnDemandClazz(() -> Types.resolved.t_i32.asRef()      );
  public static final OnDemandClazz ref_i64     = new OnDemandClazz(() -> Types.resolved.t_i64.asRef()      );
  public static final OnDemandClazz ref_u8      = new OnDemandClazz(() -> Types.resolved.t_u8 .asRef()      );
  public static final OnDemandClazz ref_u16     = new OnDemandClazz(() -> Types.resolved.t_u16.asRef()      );
  public static final OnDemandClazz ref_u32     = new OnDemandClazz(() -> Types.resolved.t_u32.asRef()      );
  public static final OnDemandClazz ref_u64     = new OnDemandClazz(() -> Types.resolved.t_u64.asRef()      );
  public static final OnDemandClazz ref_f32     = new OnDemandClazz(() -> Types.resolved.t_f32.asRef()      );
  public static final OnDemandClazz ref_f64     = new OnDemandClazz(() -> Types.resolved.t_f64.asRef()      );
  public static final OnDemandClazz any         = new OnDemandClazz(() -> Types.resolved.t_any              );
  public static final OnDemandClazz Const_String= new OnDemandClazz(() -> Types.resolved.t_Const_String     );
  public static final OnDemandClazz c_unit      = new OnDemandClazz(() -> Types.resolved.t_unit             );
  public static final OnDemandClazz array_i8    = new OnDemandClazz(() -> Types.resolved.t_array_i8         );
  public static final OnDemandClazz array_i16   = new OnDemandClazz(() -> Types.resolved.t_array_i16        );
  public static final OnDemandClazz array_i32   = new OnDemandClazz(() -> Types.resolved.t_array_i32        );
  public static final OnDemandClazz array_i64   = new OnDemandClazz(() -> Types.resolved.t_array_i64        );
  public static final OnDemandClazz array_u8    = new OnDemandClazz(() -> Types.resolved.t_array_u8         );
  public static final OnDemandClazz array_u16   = new OnDemandClazz(() -> Types.resolved.t_array_u16        );
  public static final OnDemandClazz array_u32   = new OnDemandClazz(() -> Types.resolved.t_array_u32        );
  public static final OnDemandClazz array_u64   = new OnDemandClazz(() -> Types.resolved.t_array_u64        );
  public static final OnDemandClazz array_f32   = new OnDemandClazz(() -> Types.resolved.t_array_f32        );
  public static final OnDemandClazz array_f64   = new OnDemandClazz(() -> Types.resolved.t_array_f64        );
  public static final OnDemandClazz error       = new OnDemandClazz(() -> Types.t_ERROR                     )
    {
      public Clazz get()
      {
        if (CHECKS) check
          (Errors.any());
        return super.get();
      }
    };
  public static Clazz constStringInternalArray;  // field Const_String.internal_array
  public static Clazz fuzionJavaObject;          // clazz representing a Java Object in Fuzion
  public static Clazz fuzionJavaObject_Ref;      // field fuzion.java.Java_Object.Java_Ref
  public static Clazz fuzionSysPtr;              // internal pointer type
  public static Clazz fuzionSysArray_u8;         // result clazz of Const_String.internal_array
  public static Clazz fuzionSysArray_u8_data;    // field fuzion.sys.array<u8>.data
  public static Clazz fuzionSysArray_u8_length;  // field fuzion.sys.array<u8>.length


  /**
   * NYI: This will eventually be part of a Fuzion IR Config class.
   */
  public static FuzionOptions _options_;


  /*----------------------------  variables  ----------------------------*/


  /**
   * Flag that is set during runtime execution to make sure there are no classes
   * created accidentally during runtime.
   */
  static boolean closed = false;


  /**
   * Collection of actions to be performed during findClasses phase when it is
   * found that a feature is called dynamically: Then this features needs to be
   * added to the dynamic binding data of heir classes of f.outer).
   */
  private static TreeMap<AbstractFeature, List<Runnable>> _whenCalledDynamically_ = new TreeMap<>();
  static TreeMap<Clazz, List<Runnable>> _whenCalled_ = new TreeMap<>();


  /**
   * Set of features that are called dynamically. Populated during findClasses
   * phase.
   */
  private static TreeSet<FeatureAndActuals> _calledDynamically_ = new TreeSet<>();


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Initialize Clazzes with given Options.
   */
  public static void init(FuzionOptions options)
  {
    _options_ = options;
    universe.get();
  }


  /**
   * Find the unique instance of a clazz.
   *
   * @param c a Clazz
   *
   * @return in case a class equal to c was interned before, returns that
   * existing clazz, otherwise returns c.
   */
  public static Clazz intern(Clazz c)
  {
    if (PRECONDITIONS) require
      (Errors.any() || c._type != Types.t_ERROR);

    Clazz existing = clazzes.get(c);
    if (existing == null)
      {
        clazzes.put(c, c);
        existing = c;
      }

    return existing;
  }


  /**
   * Create a clazz for the given actual type and the given outer clazz.
   * Clazzes created are recorded to be handed by findAllClasses.
   *
   * @param actualType the type of the clazz, must be free from generics
   *
   * @param clazz the runtime clazz of the outer feature of
   * actualType.featureOfType.
   *
   * @return the existing or newly created Clazz that represents actualType
   * within outer.
   */
  public static Clazz create(AbstractType actualType, Clazz outer)
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
   * @param clazz the runtime clazz of the outer feature of
   * actualType.featureOfType.
   *
   * @return the existing or newly created Clazz that represents actualType
   * within outer.
   */
  public static Clazz create(AbstractType actualType, int select, Clazz outer)
  {
    if (PRECONDITIONS) require
      (Errors.any() || !actualType.dependsOnGenerics(),
       Errors.any() || !actualType.containsThisType());

    Clazz o = outer;
    var ao = actualType.featureOfType().outer();
    while (o != null)
      {
        if (actualType.isRef() && ao != null && ao.inheritsFrom(o.feature()) && !outer.isRef())
          {
            outer = o;  // short-circuit outer relation if suitable outer was found
          }

        if (o._type.compareTo(actualType) == 0 && actualType != Types.t_ERROR &&
            // a recursive outer-relation

            // This is a little ugly: we do not want outer to be a value
            // type in the source code (see tests/inheritance_negative for
            // reasons why), but we are fine if outer is an 'artificial'
            // value type that is created by Clazz.asValue(), since these
            // will never be instantiated at runtime but are here only for
            // the convenience of the backend.
            //
            // So instead of testing !o.isRef() we use
            // !o._type.featureOfType().isThisRef().
            o._type.featureOfType().isConstructor() &&
            !o._type.featureOfType().isThisRef() &&
            !o._type.featureOfType().isIntrinsic())
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
                         "To solve this, you could add a 'ref' after the arguments list at "+o._type.featureOfType().pos().show());
          }
        o = o._outer;
      }

    // NYI: We currently create new clazzes for every different outer
    // context. This gives us plenty of opportunity to specialize the code,
    // but it might be overkill in some cases. We might rethink this and,
    // e.g. treat clazzes of inherited features with a reference outer clazz
    // the same.

    var newcl = new Clazz(actualType, select, outer);
    var result = intern(newcl);
    if (result == newcl)
      {
        if (CHECKS) check
          (Errors.any() || result.feature().state().atLeast(State.RESOLVED));
        if (result.feature().state().atLeast(State.RESOLVED))
          {
            clazzesToBeVisited.add(result);
          }
        result.registerAsHeir();
        if (_options_.verbose(3))
          {
            _options_.verbosePrintln(3, "GLOBAL CLAZZ: " + result);
            if (_options_.verbose(10))
              {
                Thread.dumpStack();
              }
          }
        result.dependencies();
      }

    if (POSTCONDITIONS) ensure
      (Errors.any() || actualType == Types.t_ADDRESS || actualType.compareToIgnoreOuter(result._type) == 0 || true,
       outer == result._outer || true /* NYI: Check why this sometimes does not hold */);

    return result;
  }


  /**
   * As long as there are clazzes that were created via create(), call
   * findAllClasses on that clazz and layout the class.
   *
   * Once this returns, all runtime classes required during execution have been
   * created.
   */
  public static void findAllClasses(Clazz main)
  {
    var toLayout = new LinkedList<Clazz>();

    // make sure internally referenced clazzes do exist:
    any.get();
    var c_universe = universe.get();
    c_universe.called(SourcePosition.builtIn);
    c_universe.instantiated(SourcePosition.builtIn);
    create(Types.t_ADDRESS, c_universe);

    // mark internally referenced clazzes as called or instantiated:
    if (CHECKS) check
      (Errors.any() || main != null);
    if (main != null)
      {
        main.called(SourcePosition.builtIn);
        main.instantiated(SourcePosition.builtIn);
      }
    constStringInternalArray = Const_String.get().lookup(Types.resolved.f_array_internal_array, SourcePosition.builtIn);
    fuzionSysArray_u8 = constStringInternalArray.resultClazz();
    fuzionSysArray_u8_data   = fuzionSysArray_u8.lookup(Types.resolved.f_fuzion_sys_array_data  , SourcePosition.builtIn);
    fuzionSysArray_u8_length = fuzionSysArray_u8.lookup(Types.resolved.f_fuzion_sys_array_length, SourcePosition.builtIn);
    fuzionSysPtr = fuzionSysArray_u8_data.resultClazz();
    var fuzion = universe.get().lookup(Types.resolved.f_fuzion, SourcePosition.builtIn);
    var fuzionJava = fuzion.lookup(Types.resolved.f_fuzion_java, SourcePosition.builtIn);
    fuzionJavaObject = fuzionJava.lookup(Types.resolved.f_fuzion_java_object, SourcePosition.builtIn);
    fuzionJavaObject_Ref = fuzionJavaObject.lookup(Types.resolved.f_fuzion_java_object_ref, SourcePosition.builtIn);

    while (!clazzesToBeVisited.isEmpty())
      {
        Clazz cl = clazzesToBeVisited.removeFirst();

        cl.findAllClasses();
        if (!cl.feature().isField())
          {
            toLayout.add(cl);
          }

        while (clazzesToBeVisited.isEmpty() && !toLayout.isEmpty())
          {
            toLayout.removeFirst().layoutAndHandleCycle();
            /* NYI: There are very few fields for which layout() causes the
             * creation of new clazzes. Examples are some inherited outer refs
             * and i32.val in case there is a user defined feature inheriting
             * from i32.  We might want to make sure that these are also
             * found before the layout phase.
             */
            if (!clazzesToBeVisited.isEmpty() && _options_.verbose(1))
              {
                Errors.warning("New clazz created during layout phase: "+clazzesToBeVisited.get(0));
              }
          }
      }
    if (CHECKS) check
      (clazzesToBeVisited.size() == 0);
    closed = true;
    for (var cl : clazzes.keySet())
      {
        cl.check();
      }
  }


  /**
   * When it is detected that f is called dynamically, execute r.run().
   */
  static void whenCalledDynamically(AbstractFeature f,
                                    Runnable r)
  {
    if (isCalledDynamically(f))
      {
        r.run();
      }
    else
      {
        var l = _whenCalledDynamically_.get(f);
        if (l == null)
          {
            l = new List<Runnable>(r);
            _whenCalledDynamically_.put(f, l);
          }
        else
          {
            l.add(r);
          }
      }
  }


  /**
   * Remember that f is called dynamically.  In case f was not known to be
   * called dynamically, execute all the runnables registered for f by
   * whenCalledDynamically.
   */
  static void calledDynamically(AbstractFeature f, List<AbstractType> tp)
  {
    if (PRECONDITIONS) require
      (Errors.any() || isUsed(f) || true /* NYI: clazzes are created for type features's type parameters without being called,
                                                * see tests/reg_issue1236 for an example. We might treat clazzes that are only used
                                                * in types differently.
                                                */);

    var ft = new FeatureAndActuals(f, tp, false);
    var added = _calledDynamically_.add(ft);
    if (added)
      {
        var l = _whenCalledDynamically_.remove(f);
        if (l != null)
          {
            for (var r : l)
              {
                r.run();
              }
          }
      }

    if (POSTCONDITIONS) ensure
      (isCalledDynamically(f));
  }


  /**
   * Has f been found to be called dynamically?
   */
  static boolean isCalledDynamically(AbstractFeature f)
  {
    return !calledDynamicallyWithTypePars(f).isEmpty();
  }

  /**
   * Has f been found to be called dynamically?
   */
  static Set<FeatureAndActuals> calledDynamicallyWithTypePars(AbstractFeature f)
  {
    var fmin = new FeatureAndActuals(f, false);
    var fmax = new FeatureAndActuals(f, true);
    return _calledDynamically_.subSet(fmin, fmax);
  }


  /**
   * Print statistics on clazzes defined per feature, for verbose output.
   */
  public static void showStatistics()
  {
    if (_options_.verbose(1))
      {
        int fields = 0;
        int routines = 0;
        int clazzesForFields = 0;
        Map<AbstractFeature, List<Clazz>> clazzesPerFeature = new TreeMap<>();
        for (var cl : clazzes.keySet())
          {
            var f = cl.feature();
            var l = clazzesPerFeature.get(f);
            if (l == null)
              {
                l = new List<>();
              }
            l.add(cl);
            clazzesPerFeature.put(f, l);
            if (f.isField())
              {
                clazzesForFields++;
                if (l.size() == 1)
                  {
                    fields++;
                  }
              }
            else
              {
                if (l.size() == 1)
                  {
                    routines++;
                  }
              }
          }
        if (_options_.verbose(2))
          {
            for (var e : clazzesPerFeature.entrySet())
              {
                var f = e.getKey();
                String fn = (f.isField() ? "field " : "routine ") + f.qualifiedName();
                System.out.println(""+e.getValue().size()+" classes for " + fn);
                if (_options_.verbose(5))
                  {
                    int i = 0;
                    for (var c : e.getValue() )
                      {
                        i++;
                        System.out.println(""+i+"/"+e.getValue().size()+" classes for " + fn + ": " + c);
                      }
                  }
              }
          }
        System.out.println("Found "+Types.num()+" types and "+Clazzes.num()+" clazzes (" +
                           clazzesForFields + " for " + fields+ " fields, " +
                           (clazzes.size()-clazzesForFields) + " for " + routines + " routines).");
      }
  }


  /**
   * Obtain a set of all clazzes.
   */
  public static Set<Clazz> all()
  {
    return clazzes.keySet();
  }


  /**
   * Return the total number of unique runtime clazzes stored globally.
   */
  public static int num()
  {
    return clazzes.size();
  }


  /*-----------------  methods to find runtime Clazzes  -----------------*/


  /**
   * # if ids created by getRuntimeClazzId[s].
   *
   * NYI! This is static to create unique ids. It is sufficient to have unique ids for sets of clazzes used by the same expression.
   */
  private static int _runtimeClazzIdCount = 0;  // NYI: Used by dev.flang.be.interpreter, REMOVE!


  /**
   * Obtain new unique ids for runtime clazz data stored in
   * Clazz.setRuntimeClazz/getRuntimeClazz.
   *
   * @param count the number of ids to reserve
   *
   * @return the first of the ids result..result+count-1 ids reserved.
   */
  static int getRuntimeClazzIds(int count)
  {
    if (PRECONDITIONS) require
      (runtimeClazzIdCount() <= Integer.MAX_VALUE - count,
       count >= 0);

    int result = _runtimeClazzIdCount;
    _runtimeClazzIdCount = result + count;

    if (POSTCONDITIONS) ensure
      (result >= 0,
       result + count <= runtimeClazzIdCount());

    return result;
  }


  /**
   * Obtain a new unique id for runtime clazz data stored in
   * Clazz.setRuntimeClazz/getRuntimeClazz.
   *
   * @return the id that was reserved.
   */
  private static int getRuntimeClazzId()
  {
    if (PRECONDITIONS) require
      (runtimeClazzIdCount() < Integer.MAX_VALUE);

    int result = getRuntimeClazzIds(1);

    if (POSTCONDITIONS) ensure
      (result >= 0,
       result < runtimeClazzIdCount());

    return result;
  }

  /**
   * Total number of ids crated by getRuntimeClazzId[s].
   *
   * @return the id count.
   */
  static int runtimeClazzIdCount()
  {
    int result = _runtimeClazzIdCount;

    if (POSTCONDITIONS) ensure
      (result >= 0);

    return result;
  }


  /**
   * Find all static clazzes for this case and store them in outerClazz.
   */
  public static void findClazzes(AbstractAssign a, Clazz outerClazz)
  {
    if (PRECONDITIONS) require
      (a != null, outerClazz != null);

    if (CHECKS) check
      (Errors.any() || a._target != null);

    if (a._target != null)
      {
        if (a._tid < 0)
          {
            a._tid = getRuntimeClazzIds(3);
          }

        Clazz sClazz = clazz(a._target, outerClazz);
        outerClazz.setRuntimeClazz(a._tid, sClazz);
        var vc = sClazz.asValue();
        var fc = vc.lookup(a._assignedField, a);
        propagateExpectedClazz(a._value, fc.resultClazz(), outerClazz);
        if (isUsed(a._assignedField))
          {
            outerClazz.setRuntimeClazz(a._tid + 1, fc);
          }
        outerClazz.setRuntimeClazz(a._tid + 2, fc.resultClazz());
      }
  }


  /**
   * propagate the expected clazz of an expression.  This is used to find the
   * result type of Box() expressions that are a NOP if the expected type is a
   * value type or the boxed type is already a ref type, while it performs
   * boxing if a value type is used as a ref.
   *
   * @param e the expression we are propagating the expected clazz into
   *
   * @param ec the expected result clazz of e
   *
   * @param outerClazz the current clazz
   */
  static void propagateExpectedClazz(Expr e, Clazz ec, Clazz outerClazz)
  {
    if (e instanceof Box b)
      {
        Clazz vc = clazz(b._value, outerClazz);
        Clazz rc = vc;
        if (ec.isRef() ||
            (ec._type.isChoice() &&
             !ec._type.isAssignableFrom(vc._type) &&
             ec._type.isAssignableFrom(vc._type.asRef())))
          {
            rc = vc.asRef();
            if (CHECKS) check
              (Errors.any() || ec._type.isAssignableFrom(rc._type));
          }
        if (b._valAndRefClazzId < 0)
          {
            b._valAndRefClazzId = getRuntimeClazzIds(2);
          }
        outerClazz.setRuntimeClazz(b._valAndRefClazzId    , vc);
        outerClazz.setRuntimeClazz(b._valAndRefClazzId + 1, rc);
        if (vc != rc)
          {
            rc.instantiated(b);
          }
        else
          {
            propagateExpectedClazz(b._value, ec, outerClazz);
          }
      }
    else if (e instanceof AbstractBlock b)
      {
        var s = b._expressions;
        if (!s.isEmpty())
          {
            propagateExpectedClazz(s.getLast(), ec, outerClazz);
          }
      }
    else if (e instanceof Tag t)
      {
        propagateExpectedClazz(t._value, ec, outerClazz);
      }
  }


  /**
   * Find the mapping from all calls to actual frame clazzes
   *
   * In an inheritance clause of the form
   *
   *   o<p,q>
   *   {
   *     a<x,y> : b<x,p>.c<y,q>;
   *
   *     d<x,y> { e<z> { } };
   *     d<i32,p>.e<bool>;
   *   }
   *
   * for the call b<x,p>.c<y,q>, the outerClazz is a<x,y>, while the frame for
   * b<x,p>.c<y,q> will be created with outerClazz.outer, i.e., o<p,q>.
   *
   * In contrast, for the call to e in d<i32,p>.e<bool>, outerClazz is d<x,y>
   * and will be used both as frame clazz for d<i32,p> and as the context for
   * call to e<z>.
   */
  public static void findClazzes(AbstractCall c, Clazz outerClazz)
  {
    if (PRECONDITIONS) require
      (Errors.any() || c.calledFeature() != null && c.target() != null);

    if (c.calledFeature() == null  || c.target() == null)
      {
        return;  // previous errors, give up
      }

    var tclazz  = clazz(c.target(), outerClazz);
    var cf      = c.calledFeature();
    var callToOuterRef = c.target().isCallToOuterRef();
    boolean dynamic = c.isDynamic() && (tclazz.isRef() || callToOuterRef);
    if (callToOuterRef)
      {
        tclazz._isCalledAsOuter = true;
      }
    var typePars = outerClazz.actualGenerics(c.actualTypeParameters());
    if (tclazz != c_void.get())
      {
        if (dynamic)
          {
            calledDynamically(cf, typePars);
          }

        var innerClazz        = tclazz.lookup(new FeatureAndActuals(cf, typePars, false), c.select(), c, c.isInheritanceCall());
        var preconditionClazz = tclazz.lookup(new FeatureAndActuals(cf, typePars, true ), c.select(), c, c.isInheritanceCall());
        if (c._sid < 0)
          {
            c._sid = getRuntimeClazzIds(3);
          }
        // A bit hacky..., this is used for calls that
        // are turned into constants in FUIR.
        c.innerClazz = innerClazz;
        outerClazz.setRuntimeData(c._sid + 0, innerClazz       );
        outerClazz.setRuntimeData(c._sid + 1, tclazz           );
        outerClazz.setRuntimeData(c._sid + 2, preconditionClazz);
        var afs = innerClazz.argumentFields();
        var i = 0;
        for (var a : c.actuals())
          {
            if (i < afs.length)  // NYI: check boxing for open generic argument lists
              {
                propagateExpectedClazz(a, afs[i].resultClazz(), outerClazz);
              }
            i++;
          }

        var f = innerClazz.feature();
        if (f.kind() == AbstractFeature.Kind.TypeParameter)
          {
            var tpc = innerClazz.resultClazz();
            do
              {
                addUsedFeature(tpc.feature(), c.pos());
                tpc.instantiated(c.pos());
                tpc = tpc._outer;
              }
            while (tpc != null && !tpc.feature().isUniverse());
          }
      }
  }


  /**
   * Find actual clazzes used by a constant expression
   *
   * @param c the constant
   *
   * @param outerClazz the surrounding clazz
   */
  public static void findClazzes(AbstractConstant c, Clazz outerClazz)
  {
    if (PRECONDITIONS) require
      (c != null, outerClazz != null);

    var p = c.pos();
    var const_clazz = clazz(c, outerClazz);
    c.runtimeClazz = const_clazz;
    const_clazz.instantiated(p);
    if (const_clazz.feature() == Types.resolved.f_array)
      { // add clazzes touched by constant creation:
        //
        //   array.internal_array
        //   fuzion.sys.internal_array
        //   fuzion.sys.internal_array.data
        //   fuzion.sys.Pointer
        //
        var array          = const_clazz;
        var internal_array = array.lookup(Types.resolved.f_array_internal_array);
        var sys_array      = internal_array.resultClazz();
        var data           = sys_array.lookup(Types.resolved.f_fuzion_sys_array_data);
        array.instantiated(p);
        sys_array.instantiated(p);
        data.resultClazz().instantiated(p);
      }
  }


  /**
   * Find all static clazzes for this case and store them in outerClazz.
   */
  public static void findClazzes(If i, Clazz outerClazz)
  {
    if (i._runtimeClazzId < 0)
      {
        i._runtimeClazzId = getRuntimeClazzIds(1);
      }
    outerClazz.setRuntimeClazz(i._runtimeClazzId, clazz(i.cond, outerClazz));
  }


  /**
   * Find all static clazzes for this case and store them in outerClazz.
   */
  public static void findClazzes(AbstractCase c, Clazz outerClazz)
  {
    // NYI: Check if this works for a case that is part of an inherits clause, do
    // we need to store in outerClazz.outer?
    var f = c.field();
    var t = c.types();
    if (c._runtimeClazzId < 0)
      {
        c._runtimeClazzId = getRuntimeClazzIds(f != null ? 1 :
                                               t != null ? t.size()
                                                         : 0);
      }
    int i = c._runtimeClazzId;
    if (f != null)
      {
        var fOrFc = isUsed(f)
          ? outerClazz.lookup(f)
          : outerClazz.actualClazz(f.resultType());
        outerClazz.setRuntimeClazz(i, fOrFc);
      }
    else if (t != null)
      {
        for (var caseType : t)
          {
            outerClazz.setRuntimeClazz(i, outerClazz.actualClazz(caseType));
            i++;
          }
      }
  }


  /**
   * Find all static clazzes for this case and store them in outerClazz.
   */
  public static void findClazzes(AbstractMatch m, Clazz outerClazz)
  {
    if (m._runtimeClazzId < 0)
      {
        // NYI: Check if this works for a match that is part of a inherits clause, do
        // we need to store in outerClazz.outer?
        m._runtimeClazzId = getRuntimeClazzIds(1);
      }
    var subjClazz = clazz(m.subject(), outerClazz);
    outerClazz.setRuntimeClazz(m._runtimeClazzId, subjClazz);
  }


  /**
   * Find all static clazzes for this Tag and store them in outerClazz.
   */
  public static void findClazzes(Tag t, Clazz outerClazz)
  {
    Clazz vc = clazz(t._value, outerClazz);
    Clazz tc = outerClazz.actualClazz(t._taggedType);
    if (t._valAndTaggedClazzId < 0)
      {
        t._valAndTaggedClazzId = getRuntimeClazzIds(2);
      }
    outerClazz.setRuntimeClazz(t._valAndTaggedClazzId    , vc);
    outerClazz.setRuntimeClazz(t._valAndTaggedClazzId + 1, tc);
    tc.instantiated(t);
  }


  /**
   * Find all static clazzes for this Tag and store them in outerClazz.
   */
  public static void findClazzes(InlineArray i, Clazz outerClazz)
  {
    Clazz ac = clazz(i, outerClazz);
    if (i._arrayClazzId < 0)
      {
        i._arrayClazzId = getRuntimeClazzIds(2);
      }
    Clazz sa = ac.lookup(Types.resolved.f_array_internal_array, i).resultClazz();
    sa.instantiated(i);
    outerClazz.setRuntimeClazz(i._arrayClazzId    , ac);
    outerClazz.setRuntimeClazz(i._arrayClazzId + 1, sa);
    ac.instantiated(i);
    var ec = outerClazz.actualClazz(i.elementType());
    for (var e : i._elements)
      {
        propagateExpectedClazz(e, ec, outerClazz);
      }
  }


  /**
   * Find all static clazzes for this Env and store them in outerClazz.
   */
  public static void findClazzes(Env v, Clazz outerClazz)
  {
    Clazz ac = clazz(v, outerClazz);
    if (v._clazzId < 0)
      {
        v._clazzId = getRuntimeClazzId();
      }
    outerClazz.setRuntimeClazz(v._clazzId, ac);
  }


  /**
   * Determine the result clazz of an Expr.
   *
   * NYI: Temporary solution, will be replaced by dynamic calls.
   *
   * This is fairly inefficient compared to dynamic
   * binding.
   */
  public static Clazz clazz(Expr e, Clazz outerClazz)
  {
    Clazz result;
    if (e instanceof AbstractBlock b)
      {
        Expr resExpr = b.resultExpression();
        result = resExpr != null ? clazz(resExpr, outerClazz)
                                 : c_unit.get();
      }
    else if (e instanceof Box b)
      {
        result = outerClazz.actualClazz(b.type());
      }

    else if (e instanceof AbstractCall c)
      {
        var tclazz = clazz(c.target(), outerClazz);
        if (tclazz != c_void.get())
          {
            var inner = tclazz.lookup(new FeatureAndActuals(c.calledFeature(),
                                                            outerClazz.actualGenerics(c.actualTypeParameters()),
                                                            false),
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

    else if (e instanceof If i)
      {
        result = outerClazz.actualClazz(i.type());
      }

    else if (e instanceof AbstractMatch m)
      {
        result = outerClazz.actualClazz(m.type());
      }

    else if (e instanceof Universe)
      {
        result = universe.get();
      }

    else if (e instanceof AbstractConstant c)
      {
        result = outerClazz.actualClazz(c.typeOfConstant());
      }

    else if (e instanceof Tag t)
      {
        result = outerClazz.actualClazz(t._taggedType);
      }

    else if (e instanceof InlineArray ia)
      {
        result = outerClazz.actualClazz(ia.type());
      }

    else if (e instanceof Env v)
      {
        result = outerClazz.actualClazz(v.type());
      }

    else
      {
        if (!Errors.any())
          {
            throw new Error("" + e.getClass() + " should no longer exist at runtime");
          }

        result = error.get(); // dummy class
      }

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /*----------------  methods to convert type to clazz  -----------------*/


  /**
   * clazz
   *
   * @return
   */
  public static Clazz clazz(AbstractType thiz)
  {
    if (PRECONDITIONS) require
      (Errors.any() || !thiz.dependsOnGenerics(),
       !thiz.isThisType());

    var result = _clazzesForTypes_.get(thiz);
    if (result == null)
      {
        Clazz outerClazz = thiz.outer() != null
          ? outerClazz = clazz(thiz.outer())
          : null;

        result = create(thiz, outerClazz);
        _clazzesForTypes_.put(thiz, result);
      }

    if (POSTCONDITIONS) ensure
      (Errors.any() || thiz.isRef() == result._type.isRef());

    return result;
  }


  /**
   * clazzWithSpecificOuter creates a clazz from this type with a specific outer
   * clazz that is inserted as the outer clazz for the outermost type that was
   * explicitly given in the source code.
   *
   * @param thiz the type of the clazz, must be free from generics
   *
   * @param select in case thiz is a field with open generic result, this
   * chooses the actual field from outer's actual generics. -1 otherwise.
   *
   * @param outerClazz the outer clazz
   *
   * @return the corresponding Clazz.
   */
  public static Clazz clazzWithSpecificOuter(AbstractType thiz, int select, Clazz outerClazz)
  {
    if (PRECONDITIONS) require
      (Errors.any() || !thiz.dependsOnGenerics(),
       outerClazz != null || thiz.featureOfType().outer() == null,
       Errors.any() || thiz == Types.t_ERROR || outerClazz == null || outerClazz.feature().inheritsFrom(thiz.featureOfType().outer()));

    var result = create(thiz, select, outerClazz);

    return result;
  }


  /*-------------  methods for clazzes related to features  -------------*/


  /**
   * NYI: recycle this comment whose method has disappeared.
   *
   * thisClazz returns the clazz of this feature's frame object.  This can be
   * called even if !hasThisType() since thisClazz() is used also for abstract
   * or intrinsic feature to determine the resultClazz().
   *
   * Depending on the generics of this and its outer features, we consider the
   * following cases:
   *
   * a.b.c.f
   *
   *   A feature with no generic arguments and no generic outer features,
   *   there is exactly one clazz for a.b.c.f's frame that can be used for all
   *   calls to f. outerClazz is not really needed in this case.
   *
   * a.b.c.f<A,B>
   *
   *   A feature with generic arguments and no generic outer features has
   *   exactly one clazz for each set of actual generic arguments <X,Y> used
   *   at any call site.
   *
   * a.b<A,B>.c<C>.d
   *
   *   A feature with no generic arguments but generic outer features has
   *   exactly one clazz for each set of actual generic arguments <X,Y>,<Z>
   *   for its outer features used at any call site.
   *
   * a.b<A,B>.c<C>.f<D,E>
   *
   *   A feature with generic arguments and generic outer features has one
   *   clazz for each complete set of actual generic arguments <V,W>,<X>,<Y,Z>
   *   used at any call site.
   *
   * a.b.c.f : g<x,y>.h<z>
   *
   *   For a feature f that inherits from a generic feature g.h, the inherits
   *   clause specifies actual generic arguments to g and g.h and these actual
   *   generic argument may contain only the formal generic arguments of
   *   a.b.c.f.  Consequently, the presence of generics in the parent feature
   *   does not add any new clazzes.
   *
   * The complete set of actual generics of a feature including all actual
   * generics of all outer features will be called the generic signature s of
   * a call.
   *
   * Note that a generic signature <V,W>,<X>,<Y,Z> cannot be flattened to
   * <V,W,X,Y,Z> since formal generic lists can be open, i.e, they do not have
   * a fixed length.
   *
   * So, essentially, we need one clazz for each (f,s) where f is a feature
   * and s is any generic signatures used in calls.
   *
   * Since every call is performed in the code of a feature that is executed
   * for an actual clazz (caller), we need a mapping
   *
   *  caller x call -> callee
   *
   * that gives the actual class to be called.
   *
   * Special thought is required for calls in an inherits clause of a feature:
   * Since calls to parent features operate on the same data, so they should
   * be performed using the same clazz. I.e., the mapping caller x call ->
   * callee also has to include all calls performed in any parent features.
   *
   * @param thiz the feature whose clazz we create
   *
   * @param actualGenerics the actual generics arguments
   *
   * @param outerClazz the clazz of this.outer(), null for universe
   *
   * @return this feature's frame clazz
   */


  /**
   * Has this feature been found to be used?
   */
  public static boolean isUsed(AbstractFeature thiz)
  {
    return thiz._usedAt != null;
  }


  /**
   * Has this feature been found to be used?
   */
  public static HasSourcePosition isUsedAt(AbstractFeature thiz)
  {
    return thiz._usedAt;
  }


  /**
   * Add f to the set of used features, record at as the position of the first
   * use.
   */
  public static void addUsedFeature(AbstractFeature f, HasSourcePosition at)
  {
    f._usedAt = at;
  }

  /**
   * reset all statically held data
   * and set closed to false again
   */
  public static void reset()
  {
    clazzes.clear();
    _clazzesForTypes_.clear();
    clazzesToBeVisited.clear();
    universe.clear();
    c_void.clear();
    bool.clear();
    c_TRUE.clear();
    c_FALSE.clear();
    i8.clear();
    i16.clear();
    i32.clear();
    i64.clear();
    u8.clear();
    u16.clear();
    u32.clear();
    u64.clear();
    f32.clear();
    f64.clear();
    ref_i8.clear();
    ref_i16.clear();
    ref_i32.clear();
    ref_i64.clear();
    ref_u8.clear();
    ref_u16.clear();
    ref_u32.clear();
    ref_u64.clear();
    ref_f32.clear();
    ref_f64.clear();
    any.clear();
    Const_String.clear();
    c_unit.clear();
    error.clear();
    constStringInternalArray = null;
    fuzionSysArray_u8 = null;
    fuzionSysArray_u8_data = null;
    fuzionSysArray_u8_length = null;
    fuzionJavaObject = null;
    fuzionJavaObject_Ref = null;
    closed = false;
    _whenCalledDynamically_.clear();
    _whenCalled_.clear();
    _calledDynamically_.clear();
  }


}

/* end of file */
