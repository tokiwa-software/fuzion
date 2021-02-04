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
 * Tokiwa GmbH, Berlin
 *
 * Source of class Clazzes
 *
 *---------------------------------------------------------------------*/

package dev.flang.ir;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.ast.AdrToValue; // NYI: remove dependency!
import dev.flang.ast.Assign; // NYI: remove dependency!
import dev.flang.ast.Block; // NYI: remove dependency!
import dev.flang.ast.BoolConst; // NYI: remove dependency!
import dev.flang.ast.Box; // NYI: remove dependency!
import dev.flang.ast.Call; // NYI: remove dependency!
import dev.flang.ast.Case; // NYI: remove dependency!
import dev.flang.ast.Current; // NYI: remove dependency!
import dev.flang.ast.Expr; // NYI: remove dependency!
import dev.flang.ast.Feature; // NYI: remove dependency!
import dev.flang.ast.FunctionReturnType; // NYI: remove dependency!
import dev.flang.ast.If; // NYI: remove dependency!
import dev.flang.ast.Impl; // NYI: remove dependency!
import dev.flang.ast.IntConst; // NYI: remove dependency!
import dev.flang.ast.Match; // NYI: remove dependency!
import dev.flang.ast.Old; // NYI: remove dependency!
import dev.flang.ast.Singleton; // NYI: remove dependency!
import dev.flang.ast.StrConst; // NYI: remove dependency!
import dev.flang.ast.Type; // NYI: remove dependency!
import dev.flang.ast.Types; // NYI: remove dependency!

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionOptions;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Clazzes manages the actual clazzes used in the system during runtime.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
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
  private static final Map<Type, Clazz> _clazzesForTypes_ = new TreeMap<>();

  /**
   * All clazzes found so far that have not been analyzed yet for clazzes that
   * they require.
   */
  private static final LinkedList<Clazz> clazzesToBeVisited = new LinkedList<>();


  static interface TypF
  {
    Type get();
  }
  public static class OnDemandClazz
  {
    final TypF _t;
    final int _size;
    Clazz _clazz = null;
    Clazz _dummy = null;
    boolean _called = false;
    OnDemandClazz(TypF t, int size) { _t = t; _size = size; }
    OnDemandClazz(TypF t) { this(t, -1); }
    OnDemandClazz(TypF t, boolean called) { this(t, -1); _called = called; }
    OnDemandClazz() { this(null); }

    /**
     * Get this clazz only if it was created, by a call to get() or by direct
     * call to Clazzes.create():
     */
    public Clazz getIfCreated()
    {
      if (_t == null)
        {
          get();
        }
      else if (_clazz == null)
        {
          if (_dummy == null)
            {
              var oldClosed = closed;
              closed = false;
              _dummy = new Clazz(_t.get(), universe.get());
              closed = oldClosed;
            }
          _clazz = clazzes.get(_dummy);
        }
      return _clazz;
    }
    public Clazz get()
    {
      if (_clazz == null)
        {
          if (_t == null)
            {
              _clazz = create(Types.resolved.universe.thisType(), null);
            }
          else
            {
              _clazz = create(_t.get(), universe.get());
            }
          if (_called)
            {
              // called(_clazz);
            }
          if (_size > 0)
            {
              if (_clazz._size == 0)
                {
                  _clazz._size = _size;
                }
              check(_clazz._size == _size);
            }
        }
      return _clazz;
    }
  }

  /**
   * Handy preallocated classes to be used during execution:
   */
  public static final OnDemandClazz universe    = new OnDemandClazz(null, true);
  public static final OnDemandClazz bool        = new OnDemandClazz(() -> Types.resolved.t_bool             );
  public static final OnDemandClazz c_TRUE      = new OnDemandClazz(() -> Types.resolved.f_TRUE .thisType() );
  public static final OnDemandClazz c_FALSE     = new OnDemandClazz(() -> Types.resolved.f_FALSE.thisType() );
  public static final OnDemandClazz i32         = new OnDemandClazz(() -> Types.resolved.t_i32              );
  public static final OnDemandClazz u32         = new OnDemandClazz(() -> Types.resolved.t_u32              );
  public static final OnDemandClazz i64         = new OnDemandClazz(() -> Types.resolved.t_i64              );
  public static final OnDemandClazz u64         = new OnDemandClazz(() -> Types.resolved.t_u64              );
  public static final OnDemandClazz ref_i32     = new OnDemandClazz(() -> Types.resolved.t_ref_i32          );
  public static final OnDemandClazz ref_u32     = new OnDemandClazz(() -> Types.resolved.t_ref_u32          );
  public static final OnDemandClazz ref_i64     = new OnDemandClazz(() -> Types.resolved.t_ref_i64          );
  public static final OnDemandClazz ref_u64     = new OnDemandClazz(() -> Types.resolved.t_ref_u64          );
  public static final OnDemandClazz object      = new OnDemandClazz(() -> Types.resolved.t_object           );
  public static final OnDemandClazz string      = new OnDemandClazz(() -> Types.resolved.t_string           );
  public static final OnDemandClazz conststring = new OnDemandClazz(() -> Types.resolved.t_conststring      , true /* needed? */);
  public static final OnDemandClazz VOID        = new OnDemandClazz(() -> Types.t_VOID                      );
  public static final OnDemandClazz error       = new OnDemandClazz(() -> Types.t_ERROR                     );


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


  static Backend _backend_ = null;


  /**
   * Collection of actions to be performed during findClasses phase when it is
   * found that a feature is called dynamically: Then this features needs to be
   * added to the dynamic binding data of heir classes of f.outer).
   */
  private static TreeMap<Feature, List<Runnable>> _whenCalledDynamically_ = new TreeMap();


  /**
   * Set of features that are called dynamically. Populated during findClasses
   * phase.
   */
  private static TreeSet<Feature> _calledDynamically_ = new TreeSet();


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Initilize Clazzes with given Options.
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
  public static Clazz create(Type actualType, Clazz outer)
  {
    if (PRECONDITIONS) require
      (actualType == Types.intern(actualType),
       Errors.count() > 0 || !actualType.isGenericArgument(),
       Errors.count() > 0 || actualType.isFreeFromFormalGenerics());

    Clazz result = null;
    Clazz o = outer;
    while (o != null && result == null)
      {
        if (o._type == actualType && actualType != Types.t_ERROR)
          { // a recursive outer-relation
            result = o;  // is ok for a ref type, we can just return the original outer clazz
            if (!o.isRef() && o._type.featureOfType().impl != Impl.INTRINSIC)
              {  // but a recursive chain of value types is not permitted

                // NYI: recursive chain of value types should be detected during
                // types checking phase!
                StringBuilder chain = new StringBuilder();
                chain.append("1: "+actualType+" at "+actualType.pos.show()+"\n");
                int i = 2;
                Clazz c = outer;
                while (c._type != actualType)
                  {
                    chain.append(""+i+": "+c._type+" at "+c._type.pos.show()+"\n");
                    c = c._outer;
                    i++;
                  }
                chain.append(""+i+": "+c._type+" at "+c._type.pos.show()+"\n");
                Errors.error(actualType.pos,
                             "Recursive value type is not allowed",
                             "Value type " + actualType + " equals type of outer feature.\n"+
                             "The chain of outer types that lead to this recursion is:\n"+
                             chain);
              }
          }
        o = o._outer;
      }
    if (result == null)
      {
        // NYI: We currently create new clazzes for every different outer
        // context. This gives us plenty of opportunity to specialize the code,
        // but it might be overkill in some cases. We might rethink this and,
        // e.g. treat clazzes of inherited features with a referenc outer clazz
        // the same.
        var newcl =  new Clazz(actualType, outer);
        result = intern(newcl);
        if (result == newcl)
          {
            check
              (Errors.count() > 0 || result.feature().state().atLeast(Feature.State.RESOLVED));
            if (result.feature().state().atLeast(Feature.State.RESOLVED))
              {
                clazzesToBeVisited.add(result);
              }
            if (_options_.verbose(3))
              {
                System.out.println("GLOBAL CLAZZ: " + result);
                if (_options_.verbose(10))
                  {
                    Thread.dumpStack();
                  }
              }
          }
      }

    if (POSTCONDITIONS) ensure
      (actualType.compareToIgnoreOuter(result._type) == 0,
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
  public static void findAllClasses(Backend be, Clazz main)
  {
    _backend_ = be;
    var toLayout = new List<Clazz>();
    int clazzCount = 0;
    while (!clazzesToBeVisited.isEmpty())
      {
        clazzCount++;
        Clazz cl = clazzesToBeVisited.removeFirst();

        cl.findAllClasses();
        cl.findAllClassesWhenCalled();
        if (!cl.feature().isField())
          {
            toLayout.add(cl);
          }
      }
    check
      (clazzesToBeVisited.size() == 0);
    for (var cl : toLayout)
      {
        cl.layoutAndHandleCycle();
      }
    check
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
  static void whenCalledDynamically(Feature f,
                                    Runnable r)
  {
    if (_calledDynamically_.contains(f))
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
  static void calledDynamically(Feature f)
  {
    if (!_calledDynamically_.contains(f))
      {
        _calledDynamically_.add(f);
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
   * Has f been found to be caled dynamically?
   */
  static boolean isCalledDynamically(Feature f)
  {
    return _calledDynamically_.contains(f);
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
        Map<Feature, List<Clazz>> clazzesPerFeature = new TreeMap<>();
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
   * Find all static clazzes for this case and store them in outerClazz.
   */
  public static void findClazzes(Assign a, Clazz outerClazz)
  {
    if (PRECONDITIONS) require
      (a != null, outerClazz != null);

    check
      (Errors.count() > 0 || a.getOuter != null);

    if (a.getOuter != null)
      {
        if (a.tid_ < 0)
          {
            a.tid_ = outerClazz.feature().getRuntimeClazzIds(2);
          }

        Clazz sClazz = clazz(a.getOuter, outerClazz);
        outerClazz.setRuntimeClazz(a.tid_, sClazz);
        Clazz vc = clazz(a.value, outerClazz);

        // NYI: The following is needed only if assigneField.type.featureOfType.isChoice & !outerClazz.isChoiceOfOnlyRefs.
        outerClazz.setRuntimeClazz(a.tid_ + 1, vc);
      }
  }


  /**
   * Find all static clazzes for this Box and store them in outerClazz.
   */
  public static void findClazzes(Box b, Clazz outerClazz)
  {
    Clazz vc = clazz(b._value, outerClazz);
    Type t = b._type;
    if (t.isGenericArgument())
      {
        t = outerClazz.actualType(t);
        if (!t.isRef())
          {
            t = Types.intern(new Type(t, true));
          }
      }
    Clazz rc = outerClazz.actualClazz(t);
    Clazz ec = outerClazz.actualClazz(b._expectedType);
    if (b._valAndRefClazzId < 0)
      {
        b._valAndRefClazzId = outerClazz.feature().getRuntimeClazzIds(3);
      }
    outerClazz.setRuntimeClazz(b._valAndRefClazzId    , vc);
    outerClazz.setRuntimeClazz(b._valAndRefClazzId + 1, rc);
    outerClazz.setRuntimeClazz(b._valAndRefClazzId + 2, ec);
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
  public static void findClazzes(Call c, Clazz outerClazz)
  {
    if (PRECONDITIONS) require
      (Errors.count() > 0 || c.calledFeature_ != null && c.target != null);

    if (c.calledFeature_ == null  || c.target == null)
      {
        return;  // previous errors, give up
      }

    if (!c.isInheritanceCall_)
      {
        Clazz   tclazz = clazz(c.target, outerClazz);
        Feature cf     = c.calledFeature_;

        if (c.sid_ < 0)
          {
            c.sid_ = outerClazz.feature().getRuntimeClazzId();
          }
        BackendCallable ca = null;
        if (!c.isDynamic() || !tclazz.isRef())
          {
            var innerClazz = tclazz.lookup(cf, outerClazz.actualGenerics(c.generics), c.pos);
            ca = _backend_.callable(innerClazz, tclazz);
          }
        else
          {
            calledDynamically(cf);
          }
        outerClazz.setRuntimeData(c.sid_, ca);

        ArrayList<Type> argTypes = c.argTypes_;
        int i = 0;
        for (Type t : argTypes)
          {
            Type at = outerClazz.actualType(t);
            if (t != at)
              {
                if (argTypes == c.argTypes_)
                  {
                    argTypes = new ArrayList<>();
                    for (Type nt : c.argTypes_)
                      {
                        argTypes.add(nt);
                      }
                  }
                argTypes.set(i, at);
              }
            i++;
          }
        if (c.atid_ < 0)
          {
            c.atid_ = outerClazz.feature().getRuntimeClazzId();
          }
        outerClazz.setRuntimeData(c.atid_, argTypes);
      }
  }


  /**
   * Find all static clazzes for this case and store them in outerClazz.
   */
  public static void findClazzes(Case c, Clazz outerClazz)
  {
    // NYI: Check if this works for a case that is part of an inherits clause, do
    // we need to store in outerClazz.outer?
    if (c.runtimeClazzId_ < 0)
      {
        c.runtimeClazzId_ = outerClazz.feature().getRuntimeClazzIds(c.field != null
                                                                    ? 1
                                                                    : c.types.size());
      }
    int i = c.runtimeClazzId_;
    if (c.field != null)
      {
        outerClazz.setRuntimeClazz(i, outerClazz.actualClazz(c.field.resultType()));
      }
    else
      {
        for (Type caseType : c.types)
          {
            outerClazz.setRuntimeClazz(i, outerClazz.actualClazz(caseType));
            i++;
          }
      }
  }


  /**
   * Find all static clazzes for this case and store them in outerClazz.
   */
  public static void findClazzes(Match m, Clazz outerClazz)
  {
    if (m.runtimeClazzId_ < 0)
      {
        // NYI: Check if this works for a match that is part of a inhertis clause, do
        // we need to store in outerClazz.outer?
        m.runtimeClazzId_ = outerClazz.feature().getRuntimeClazzIds(1);
      }
    outerClazz.setRuntimeClazz(m.runtimeClazzId_, clazz(m.subject, outerClazz));
  }


  /**
   * Determine the outer clazz of an Expr.
   *
   * NYI: Temporary solution, will be replaced by dynamic calls.
   *
   * This is fairly inefficient compared to dynamic
   * binding.
   */
  public static Clazz clazz(Expr e, Clazz outerClazz)
  {
    Clazz result;
    if (e instanceof AdrToValue)
      {
        var a = (AdrToValue) e;
        result = clazz(a.adr_, outerClazz);
      }
    else if (e instanceof Block)
      {
        var b = (Block) e;
        Expr resExpr = b.resultExpression();
        result = resExpr != null ? clazz(resExpr, outerClazz)
                                 : VOID.get();
      }
    else if (e instanceof Box)
      {
        var b = (Box) e;
        result = outerClazz.actualClazz(b._type);
      }

    else if (e instanceof Call)
      {
        var c = (Call) e;
        Feature cf = c.calledFeature();
        var tclazz = clazz(c.target, outerClazz);
        if (cf == Types.f_ERROR)
          {
            result = error.get();
          }
        else if (cf.isOuterRef())
          {
            Feature f = tclazz.feature();
            if (f.outerRefOrNull() == cf)
              { // a "normal" outer ref for the outer clazz surrounding this instance
                result = tclazz._outer;
              }
            else
              {
                result = inheritedOuterRefClazz(c, f, tclazz, null);
              }
          }
        else
          {
            var frame = tclazz.lookup(cf, outerClazz.actualGenerics(c.generics), c.pos);
            if (cf.returnType.isConstructorType())
              {
                result = frame;
              }
            else
              {
                var t = cf.resultType();
                if (cf.returnType instanceof FunctionReturnType && !t.isGenericArgument())
                  {
                    /* NYI: This attempt to rebase to the outer type of the
                     * frame is not correct. Instead, we might have to look at
                     * the actual clazz of the outer reference the result type
                     * is based on and rebase against that clazz.
                     */
                    int td = t.featureOfType().depth() + 1;
                    int sd = ((FunctionReturnType) cf.returnType).depthInSource;
                    int baseLen = td - sd; // the length from universe to the innermost outer class in t that we rebase
                    if (baseLen > 0)
                      { // NYI: do not rebase, but directly create a class with a different outer clazz!
                        var base = frame._realOuter[baseLen-1];
                        t = t.rebase(base._type, sd);
                      }
                  }
                result = frame.actualClazz(t);
              }
          }
      }
    else if (e instanceof Current)
      {
        var c = (Current) e;
        result = outerClazz;
      }

    else if (e instanceof If)
      {
        var i = (If) e;
        result = outerClazz.actualClazz(i._type);
      }

    else if (e instanceof BoolConst)
      {
        var b = (BoolConst) e;
        result = bool.get();
      }

    else if (e instanceof IntConst)
      {
        var i = (IntConst) e;
        result = i32.get();
      }

    else if (e instanceof Match)
      {
        var m = (Match) e;
        result = outerClazz.actualClazz(m.type_);
      }

    else if (e instanceof Old)
      {
        var o = (Old) e;
        result = clazz(o.e, outerClazz);
      }

    else if (e instanceof Singleton)
      {
        var s = (Singleton) e;
        result = singletonClazz(s.singleton_);
      }

    else if (e instanceof StrConst)
      {
        var s = (StrConst) e;
        i32.get();
        object.get();
        result = conststring.get();
      }

    else
      {
        if (Errors.count() == 0)
          {
            throw new Error("" + e.getClass() + " should no longer exist at runtime");
          }
        return error.get(); // dummy class
      }

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * Recursive helper function for clazz to find the clazz for an outer ref from
   * an inherited feature.
   *
   * @param f the feature of the target of the inheritance call
   *
   * @param tclazz the target clazz of the inheritance call
   *
   * @param result must be null on the first call. This is used during recursive
   * traversal to check that all results are equal in case several results are
   * found.
   *
   * @return the static clazz of this call to an outer ref.
   */
  private static Clazz inheritedOuterRefClazz(Call thiz, Feature f, Clazz tclazz, Clazz result)
  {
    Feature cf = thiz.calledFeature();
    for (Call p : f.inherits)
      {
        if (p.calledFeature().outerRefOrNull() == cf)
          { // an inherited outer ref referring to the target of the inherits call
            Clazz found = clazz(p.target, tclazz);
            check
              (result == null || result == found);

            result = found;
          }
        result = inheritedOuterRefClazz(thiz, p.calledFeature(), tclazz, result);
      }
    return result;
  }


  /*----------------  methods to convert type to clazz  -----------------*/


  /**
   * clazz
   *
   * @return
   */
  public static Clazz clazz(Type thiz)
  {
    if (PRECONDITIONS) require
      (!thiz.isOpenGeneric(),
       Errors.count() > 0 || thiz.isFreeFromFormalGenerics());

    Clazz outerClazz;
    if (thiz.outer() != null)
      {
        outerClazz = clazz(thiz.outer());
      }
    else
      {
        outerClazz = null;
      }

    Type t = Types.intern(thiz);
    var result = _clazzesForTypes_.get(t);
    if (result == null)
      {
        result = create(t, outerClazz);
        _clazzesForTypes_.put(t, result);
      }

    if (POSTCONDITIONS) ensure
      (thiz.isRef() == result._type.isRef());

    return result;
  }


  /**
   * clazzWithSpecificOuter creates a clazz from this type with a specific outer
   * clazz that is inserted as the outer clazz for the outermost type that was
   * explicitly given in the source code.
   *
   * @param outerClazz the outer clazz
   *
   * @return
   */
  public static Clazz clazzWithSpecificOuter(Type thiz, Clazz outerClazz)
  {
    if (PRECONDITIONS) require
      (!thiz.isOpenGeneric(),
       thiz.isFreeFromFormalGenericsInSource(),
       outerClazz != null || thiz.featureOfType().outer() == null,
       thiz == Types.t_ERROR || !thiz.outerMostInSource() || outerClazz == null || outerClazz.feature().inheritsFrom(thiz.featureOfType().outer()));

    Clazz result;

    if (!thiz.outerMostInSource())
      {
        outerClazz = clazzWithSpecificOuter(thiz.outer(), outerClazz._outer);
      }

    Type t = Types.intern(thiz);
    result = create(t, outerClazz);

    return result;
  }


  /*-------------  methods for clazzes related to features  -------------*/


  /**
   * Obtain the static clazz of a singleton
   */
  public static Clazz singletonClazz(Feature thiz)
  {
    if (PRECONDITIONS) require
      (thiz.isSingleton());

    // NYI: caching of result!
    Clazz result = thiz.outer() == null ? universe.get()
      : singletonClazz(thiz.outer()).actualClazz(thiz.resultType());

    return result;
  }


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
   *   generic argument may contain only the formal genric arguments of
   *   a.b.c.f.  Consequently, the presence of generics in the parent feature
   *   does not add any new clazzes.
   *
   * The complete set of actual generics of a feature including all actual
   * generics of all outer features will be called the generic signature s of
   * a call.
   *
   * Note that a generic signature <V,W>,<X>,<Y,Z> cannot be flattened to
   * <V,W,X,Y,Z> since formal genric lists can be open, i.e, they do not have
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
   * Has this feature been found to be used within the given static clazz?
   */
  public static boolean isUsed(Feature thiz, Clazz staticClazz)
  {
    return thiz.state().atLeast(Feature.State.RESOLVED);
  }


}

/* end of file */
