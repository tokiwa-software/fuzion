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
 * Source of class DFA
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.fuir.FUIR;
import dev.flang.fuir.analysis.AbstractInterpreter;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionOptions;
import dev.flang.util.List;
import dev.flang.util.Pair;


/**
 * DFA creates a data flow analysis based on the FUIR representation of a Fuzion
 * application.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class DFA extends ANY
{


  /*----------------------------  interfaces  ---------------------------*/


  /**
   * Functional interface to crate intrinsics.
   */
  interface IntrinsicDFA
  {
    Value analyze(Call c);
  }


  /*-----------------------------  classes  -----------------------------*/


  /**
   * Dummy unit type as type parameter for AbstractInterpreter.ProcessStatement.
   */
  static class Unit
  {
  }


  /**
   * Statement processor used with AbstractInterpreter to perform DFA analysis
   */
  class Analyze extends AbstractInterpreter.ProcessStatement<Value,Unit>
  {


    /**
     * The Call we are analysing.
     */
    final Call _call;


    /**
     * Create processor for an abstract interpreter doing DFA analysis of the
     * given call's code.
     */
    Analyze(Call call)
    {
      _call = call;
    }



    /**
     * Join a List of RESULT from subsequent statements into a compound
     * statement.  For a code generator, this could, e.g., join statements "a :=
     * 3;" and "b(x);" into a block "{ a := 3; b(x); }".
     */
    public Unit sequence(List<Unit> l)
    {
      return _unit_;
    }


    /*
     * Produce the unit type value.  This is used as a placeholder
     * for the universe instance as well as for the instance 'unit'.
     */
    public Value unitValue()
    {
      return Value.UNIT;
    }


    /**
     * Called before each statement is processed. May be used to, e.g., produce
     * tracing code for debugging or a comment.
     */
    public Unit statementHeader(int cl, int c, int i)
    {
      if (_reportResults && _options.verbose(9))
        {
          System.out.println("DFA for "+_fuir.clazzAsString(cl)+"("+_fuir.clazzArgCount(cl)+" args) at "+c+"."+i+": "+_fuir.codeAtAsString(cl,c,i));
        }
      return _unit_;
    }


    /**
     * A comment, adds human readable information
     */
    public Unit comment(String s)
    {
      return _unit_;
    }


    /**
     * no operation, like comment, but without giving any comment.
     */
    public Unit nop()
    {
      return _unit_;
    }


    /**
     * Determine the address of a given value.  This is used on a call to an
     * inner feature to pass a reference to the outer value type instance.
     */
    public Pair<Value, Unit> adrOf(Value v)
    {
      return new Pair<>(v.adrOf(), _unit_);
    }


    /**
     * Perform an assignment val to field f in instance rt
     *
     * @param tc clazz id of the target instance
     *
     * @param f clazz id of the assigned field
     *
     * @param rt clazz is of the field type
     *
     * @param tvalue the target instance
     *
     * @param val the new value to be assigned to the field.
     *
     * @return resulting code of this assignment.
     */
    public Unit assignStatic(int tc, int f, int rt, Value tvalue, Value val)
    {
      tvalue.setField(DFA.this, f, val);
      return _unit_;
    }


    /**
     * Perform an assignment of avalue to a field in tvalue. The type of tvalue
     * might be dynamic (a refernce). See FUIR.access*().
     */
    public Unit assign(int cl, int c, int i, Value tvalue, Value avalue)
    {
      var res = access(cl, c, i, tvalue, new List<>(avalue));
      return _unit_;
    }


    /**
     * Perform a call of a feature with target instance tvalue with given
     * arguments.. The type of tvalue might be dynamic (a refernce). See
     * FUIR.access*().
     *
     * Result._v0 may be null to indicate that code generation should stop here
     * (due to an error or tail recursion optimization).
     */
    public Pair<Value, Unit> call(int cl, int c, int i, Value tvalue, List<Value> args)
    {
      var cc0 = _fuir.accessedClazz  (cl, c, i);
      var res = Value.UNIT;
      if (_fuir.clazzContract(cc0, FUIR.ContractKind.Pre, 0) != -1)
        {
          res = call0(cl, tvalue, args, c, i, cc0, true);
        }
      if (res != null && !_fuir.callPreconditionOnly(cl, c, i))
        {
          res = access(cl, c, i, tvalue, args);
        }
      return new Pair<>(res, _unit_);
    }


    /**
     * Analyze an access (call or write) a feature.
     *
     * @param cl clazz id
     *
     * @param c the code block to compile
     *
     * @param i index of the access statement, must be ExprKind.Assign or ExprKind.Call
     *
     * @param tvalue the target of this call, Value.UNIT if none.
     *
     * @param args the arguments of this call, or, in case of an assignment, a
     * list of one element containing value to be assigned.
     *
     * @return result value of the access
     */
    Value access(int cl, int c, int i, Value tvalue, List<Value> args)
    {
      var cc0 = _fuir.accessedClazz  (cl, c, i);
      var ccs = _fuir.accessedClazzes(cl, c, i);
      var found = new boolean[] { false };
      var resf = new Value[] { null };
      for (var ccii = 0; ccii < ccs.length; ccii += 2)
        {
          var cci = ccii;
          var tt = ccs[cci  ];
          var cc = ccs[cci+1];
          tvalue.forAll(t -> {
              check
                (t != Value.UNIT || AbstractInterpreter.clazzHasUniqueValue(_fuir, tt));
              if (t == Value.UNIT ||
                  t._clazz == tt ||
                  //!_fuir.accessIsDynamic(cl, c, i) && _fuir.correspondingFieldInValueInstance(tt) == t._clazz ||

                  // NYI: This is a little too much special handling, must simplify:
                  !_fuir.accessIsDynamic(cl, c, i) && _fuir.clazzIsRef(t._clazz) && !_fuir.clazzIsOuterRef(cc0))
                {
                  found[0] = true;
                  var r = access0(cl, c, i, t, args, cc);
                  if (r != null)
                    {
                      resf[0] = resf[0] == null ? r : resf[0].join(r);
                    }
                }
            });
        }
      if (!found[0])
        {
          // NYI: proper error reporting
          Errors.error("NYI: in "+_fuir.clazzAsString(cl)+" no targets for "+_fuir.codeAtAsString(cl, c, i)+" target "+tvalue);
        }
      return resf[0];
    }


    /**
     * Helper routine for access (above) to perform a static access (cal or write).
     */
    Value access0(int cl, int c, int i, Value tvalue, List<Value> args, int cc)
    {
      var cs = site(cl, c, i);
      cs._accesses.add(cc);
      var isCall = _fuir.codeAt(c, i) == FUIR.ExprKind.Call;
      Value r;
      if (isCall)
        {
          r = call0(cl, tvalue, args, c, i, cc, false);
        }
      else
        {
          if (!_fuir.clazzIsUnitType(_fuir.clazzResultClazz(cc)))
            {
              if (_reportResults && _options.verbose(9))
                {
                  System.out.println("DFA for "+_fuir.clazzAsString(cl)+"("+_fuir.clazzArgCount(cl)+" args) at "+c+"."+i+": "+_fuir.codeAtAsString(cl,c,i)+": " +
                                     tvalue + ".set("+_fuir.clazzAsString(cc)+") := " + args.get(0));
                }
              tvalue.setField(DFA.this, cc, args.get(0));
            }
          r = Value.UNIT;
        }
      return r;
    }


    /**
     * Helper for call to handle non-dynamic call to cc (or cc's precondition)
     *
     * @param cl clazz id of clazz containing the call
     *
     * @param stack the stack containing the current arguments waiting to be used
     *
     * @param c the code block to compile
     *
     * @param i the index of the call within c
     *
     * @param cc clazz that is called
     *
     * @param pre true to call the precondition of cl instead of cl.
     *
     * @return result values of the call
     */
    Value call0(int cl, Value tvalue, List<Value> args, int c, int i, int cc, boolean pre)
    {
      Value res = null;
      switch (pre ? FUIR.FeatureKind.Routine : _fuir.clazzKind(cc))
        {
        case Abstract :
          Errors.error("Call to abstract feature encountered.",
                       "Found call to  " + _fuir.clazzAsString(cc));
        case Routine  :
        case Intrinsic:
          {
            if (_fuir.clazzNeedsCode(cc))
              {
                var ca = newCall(cc, pre, tvalue, args, _call._env, _call);
                res = ca.result();
                if (_reportResults && _options.verbose(9))
                  {
                    System.out.println("DFA for "+_fuir.clazzAsString(cl)+"("+_fuir.clazzArgCount(cl)+" args) at "+c+"."+i+": "+_fuir.codeAtAsString(cl,c,i)+": " + ca);
                  }
              }
            break;
          }
        case Field:
          {
            var resa = new Value[] { null };
            tvalue.forAll(t ->
                          {
                            var r = t.readField(DFA.this, cc);
                            if (resa[0] == null)
                              {
                                resa[0] = r;
                              }
                            else
                              {
                                resa[0] = resa[0].join(r);
                              }
                          });
            res = resa[0];
            if (_reportResults && _options.verbose(9))
              {
                System.out.println("DFA for "+_fuir.clazzAsString(cl)+"("+_fuir.clazzArgCount(cl)+" args) at "+c+"."+i+": "+_fuir.codeAtAsString(cl,c,i)+": " +
                                   tvalue + ".get(" + _fuir.clazzAsString(cc) + ") => " + res);
              }
            break;
          }
        default:       throw new Error("This should not happen: Unknown feature kind: " + _fuir.clazzKind(cc));
        }
      return res;
    }


    /**
     * For a given value v of value type vc create a boxed ref value of type rc.
     */
    public Pair<Value, Unit> box(Value val, int vc, int rc)
    {
      var boxed = val.box(DFA.this, vc, rc, _call);
      return new Pair<>(boxed, _unit_);
    }


    /**
     * For a given reference value v create an unboxed value of type vc.
     */
    public Pair<Value, Unit> unbox(Value val, int orc)
    {
      var unboxed = val.unbox(orc);
      return new Pair<>(unboxed, _unit_);
    }


    /**
     * Get the current instance
     */
    public Pair<Value, Unit> current(int cl)
    {
      return new Pair<>(_call._instance, _unit_);
    }


    /**
     * Get the outer instance
     */
    public Pair<Value, Unit> outer(int cl)
    {
      return new Pair<>(_call._target, _unit_);
    }

    /**
     * Get the argument #i
     */
    public Value arg(int cl, int i)
    {
      return _call._args.get(i);
    }


    /**
     * Get a constant value of type constCl with given byte data d.
     */
    public Pair<Value, Unit> constData(int constCl, byte[] d)
    {
      var o = _unit_;
      var r = switch (_fuir.getSpecialId(constCl))
        {
        case c_bool -> d[0] == 1 ? _true : _false;
        case c_i8   ,
             c_i16  ,
             c_i32  ,
             c_i64  ,
             c_u8   ,
             c_u16  ,
             c_u32  ,
             c_u64  ,
             c_f32  ,
             c_f64  -> new NumericValue(DFA.this, constCl, ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN));
        case c_conststring -> newConstString(d, _call);
        default ->
        {
          Errors.error("Unsupported constant in DFA analysis.",
                       "DFA cannot handle constant of clazz '" + _fuir.clazzAsString(constCl) + "' ");
          yield null;
        }
        };
      return new Pair(r, o);
    }


    /**
     * Perform a match on value subv.
     */
    public Pair<Value, Unit> match(AbstractInterpreter ai, int cl, int c, int i, Value subv)
    {
      Value r = null; // result value null <=> does not return.  Will be set to Value.UNIT if returning case was found.
      for (var mc = 0; mc < _fuir.matchCaseCount(c, i); mc++)
        {
          // array to permit modification in lambda
          var takenA    = new boolean[] { false };
          var field = _fuir.matchCaseField(cl, c, i, mc);
          for (var t : _fuir.matchCaseTags(cl, c, i, mc))
            {
              subv.forAll(s -> {
                  if (s instanceof TaggedValue tv)
                    {
                      if (tv._tag == t)
                        {
                          var untagged = tv._original;
                          takenA[0] = true;
                          if (field != -1)
                            {
                              _call._instance.setField(DFA.this, field, untagged);
                            }
                        }
                    }
                  else
                    {
                      throw new Error("DFA encountered Unexpected value in match: " + s.getClass() + " '" + s + "' " +
                                      " for match of type " + _fuir.clazzAsString(_fuir.matchStaticSubject(cl, c, i)));
                    }
                });

            }
          var taken = takenA[0];
          if (_reportResults && _options.verbose(9))
            {
              System.out.println("DFA for "+_fuir.clazzAsString(cl)+"("+_fuir.clazzArgCount(cl)+" args) at "+c+"."+i+": "+_fuir.codeAtAsString(cl,c,i)+": "+subv+" case "+mc+": "+
                                 (taken ? "taken" : "not taken"));
            }

          if (taken)
            {
              var resv = ai.process(cl, _fuir.matchCaseCode(c, i, mc));
              if (resv._v0 != null)
                { // if at least one case returns (i.e., result is not null), this match returns.
                  r = Value.UNIT;
                }
            }
        }
      return new Pair(r, _unit_);
    }


    /**
     * Create a tagged value of type newcl from an untagged value for type valuecl.
     */
    public Pair<Value, Unit> tag(int cl, int valuecl, Value value, int newcl, int tagNum)
    {
      Value res = value.tag(_call._dfa, newcl, tagNum);
      return new Pair<>(res, _unit_);
    }


    /**
     * Access the effect of type ecl that is installed in the environemnt.
     */
    public Pair<Value, Unit> env(int ecl)
    {
      return new Pair<>(_call.getEffect(ecl), _unit_);
    }


    /**
     * Process a contract of kind ck of clazz cl that results in bool value cc
     * (i.e., the contract fails if !cc).
     */
    public Unit contract(int cl, FUIR.ContractKind ck, Value cc)
    {
      return _unit_;
      /*
      return Unit.iff(cc.field(_names.TAG_NAME).not(),
                        Unit.seq(Value.fprintfstderr("*** failed " + ck + " on call to '%s'\n",
                                                       Value.string(_fuir.clazzAsString(cl))),
                                   Value.exit(1)));
      */
    }

  }


  /*----------------------------  constants  ----------------------------*/


  /**
   * For debugging: dump stack when _chaned is set, for debugging when fix point
   * is not reached.
   */
  static boolean SHOW_STACK_ON_CHANGE = false;


  /**
   * singleton instance of Unit.
   */
  static Unit _unit_ = new Unit();


  /**
   * DFA's intrinsics.
   */
  static TreeMap<String, IntrinsicDFA> _intrinsics_ = new TreeMap<>();


  /*-------------------------  static methods  --------------------------*/


  /**
   * Helper method to add intrinsic to _intrinsics_.
   */
  private static void put(String n, IntrinsicDFA c)
  {
    _intrinsics_.put(n, c);
  }


  /**
   * Get the names of all intrinsics supported by this backend.
   */
  public static Set<String> supportedIntrinsics()
  {
    return _intrinsics_.keySet();
  }


  /*----------------------------  variables  ----------------------------*/


  /**
   * Options provided to the 'fz' command.
   */
  public final FuzionOptions _options;


  /**
   * The intermediate code we are analysing.
   */
  public final FUIR _fuir;


  /**
   * Special values of clazz 'bool' for 'true', 'false' and arbitrary bool
   * values.
   */
  final Value _true, _false, _bool;


  /**
   * Special value for universe.
   */
  final Value _universe;


  /**
   * Instances created during DFA analysis.
   */
  TreeMap<Value, Value> _instances = new TreeMap<>(Value.COMPARATOR);


  /**
   * Calls created during DFA analysis.
   */
  TreeMap<Call, Call> _calls = new TreeMap<>();


  /**
   * Sites created during DFA analysis.
   */
  TreeMap<Site, Site> _sites = new TreeMap<>();


  /**
   * Calls created during current sub-iteration of the DFA analysis.  These will
   * be analysed at the end of the current iteration since they most likely add
   * new information.
   */
  TreeSet<Call> _newCalls = new TreeSet<>();


  /**
   * Envs created during DFA analysis.
   */
  TreeMap<Env, Env> _envs = new TreeMap<>();


  /**
   * All fields that are ever written.  These will be needed even if they are
   * never read unless the assignments are actually removed (which is currently
   * not the case).
   */
  TreeSet<Integer> _writtenFields = new TreeSet<>();


  /**
   * All fields that are ever read.
   */
  TreeSet<Integer> _readFields = new TreeSet<>();


  /**
   * Map from type to corresponding default effects.
   *
   * NYI: this might need to be thread-local and not global!
   */
  TreeMap<Integer, Value> _defaultEffects = new TreeMap<>();


  /**
   * Flag to detect changes during current iteration of the fix-point algorithm.
   * If this remains false during one iteration we have reached a fix-point.
   */
  boolean _changed = false;
  String _changedSetBy;


  /**
   * Flag to control output of errors.  This is set to true after a fix point
   * has been reached to report errors that should disappear when fix point is
   * reached (like vars are initialized).
   */
  boolean _reportResults = false;


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create DFA for given intermediate code.
   *
   * @param options the options to specify values for intrinsics like 'debug',
   * 'safety'.
   *
   * @param fuir the intermediate code.
   */
  public DFA(FuzionOptions options, FUIR fuir)
  {
    _options = options;
    _fuir = fuir;
    var bool = fuir.clazz(FUIR.SpecialClazzes.c_bool);
    _true  = new TaggedValue(this, bool, Value.UNIT, 1);
    _false = new TaggedValue(this, bool, Value.UNIT, 0);
    _bool  = _true.join(_false);
    _universe = newInstance(_fuir.clazzUniverse(), null);
    Errors.showAndExit();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create a new Instance of FUIR using the information collected during this
   * DFA analyssis. In particular, Let 'clazzNeedsCode' return false for
   * routines that were found never to be caled.
   */
  public FUIR new_fuir()
  {
    dfa();
    var called = new TreeSet<Integer>();
    for (var c : _calls.values())
      {
        called.add(c._cc);
      }
    return new FUIR(_fuir)
      {
        public boolean clazzNeedsCode(int cl)
        {
          return super.clazzNeedsCode(cl) &&
            switch (_fuir.clazzKind(cl))
            {
            case Routine, Intrinsic -> called.contains(cl);
            case Field              ->
            {
              var fc = _fuir.correspondingFieldInValueInstance(cl);
              yield
                isBuiltInNumeric(_fuir.clazzOuterClazz(fc)) ||
                _readFields.contains(fc);
            }
            case Abstract           -> true;
            case Choice             -> true;
            };
        }
        public int[] accessedClazzes(int cl, int c, int ix)
        {
          var ccs = super.accessedClazzes(cl, c, ix);
          var cs = site(cl, c, ix);
          var nr = new int[ccs.length];
          int j = 0;
          for (var cci = 0; cci < ccs.length; cci += 2)
            {
              var tt = ccs[cci+0];
              var cc = ccs[cci+1];
              if (cs._accesses.contains(cc))
                {
                  nr[j++] = tt;
                  nr[j++] = cc;
                }
            }
          return java.util.Arrays.copyOfRange(nr, 0, j);
        }
    };
  }


  /**
   * Perform DFA analysis
   */
  public void dfa()
  {
    var callMain = newCall(_fuir.mainClazzId(),
                           false /* NYI: main's precondition is not analyzed */,
                           Value.UNIT,
                           new List<>(),
                           null /* env */,
                           () -> { System.out.println("program entry point"); return "  "; });
    findFixPoint();
    Errors.showAndExit();
  }


  /**
   * Iteratively perform data flow analysis until a fix point is reached.
   */
  void findFixPoint()
  {
    int cnt = 0;
    do
      {
        cnt++;
        _options.verbosePrintln("DFA iteration #"+cnt+": --------------------------------------------------" +
                                (!_options.verbose(2) ? "" : _calls.size()+","+_instances.size()+"; "+_changedSetBy));
        _changed = false;
        _changedSetBy = "*** change not set ***";
        iteration();
      }
    while (_changed && (true || cnt < 100) || false && (cnt < 50));
    if (_options.verbose(3))
      {
        _options.verbosePrintln(3, "DFA done:");
        _options.verbosePrintln(3, "Instances: " + _instances.values());
        _options.verbosePrintln(3, "Calls: ");
        for (var c : _calls.values())
          {
            _options.verbosePrintln(3, "  call: " + c);
          }
      }
    _reportResults = true;
    iteration();
  }


  /**
   * Perform one iteration of the analysis.
   */
  void iteration()
  {
    var vs = _calls.values();
    do
      {
        var s = vs.toArray(new Call[vs.size()]);
        _newCalls = new TreeSet();
        for (var c : s)
          {
            if (_reportResults && _options.verbose(4))
              {
                System.out.println(("----------------"+c+
                                    "----------------------------------------------------------------------------------------------------")
                                   .substring(0,100));
                c.showWhy();
              }
            analyze(c);
          }
        vs = _newCalls;
      }
    while (!vs.isEmpty());
  }


  /**
   * Analyze code for given call
   *
   * @param c the call
   */
  void analyze(Call c)
  {
    if (_fuir.clazzKind(c._cc) == FUIR.FeatureKind.Routine || c._pre)
      {
        var i = c._instance;
        check
          (c._args.size() == _fuir.clazzArgCount(c._cc));
        for (var a = 0; a < c._args.size(); a++)
          {
            var af = _fuir.clazzArg(c._cc, a);
            var aa = c._args.get(a);
            i.setField(this, af, aa);
          }

        // copy outer ref argument to outer ref field:
        var or = _fuir.clazzOuterRef(c._cc);
        if (or != -1)
          {
            i.setField(this, or, c._target);
          }

        var ai = new AbstractInterpreter(_fuir, new Analyze(c));
        var r = ai.process(c._cc, c._pre);
        if (r._v0 != null)
          {
            c.returns();
          }
      }
  }


  /**
   * Flag to detect and stop (endless) recursion within NYIintrinsiMissing.
   */
  static boolean _recursion_in_NYIintrinsicMissing = false;


  /**
   * Report that intrinsic 'cl' is missing and return Value.UNDEFINED.
   */
  static Value NYIintrinsicMissing(Call cl)
  {
    if (true || cl._dfa._reportResults)
      {
        var name = cl._dfa._fuir.clazzIntrinsicName(cl._cc);

        // NYI: Proper error handling.
        Errors.error("NYI: Support for intrinsic '" + name + "' missing");

        // cl.showWhy() may try to print result values that depend on
        // intrinsics, so we risk running into an endless recursion here:
        if (!_recursion_in_NYIintrinsicMissing)
          {
            _recursion_in_NYIintrinsicMissing = true;
            cl.showWhy();
            _recursion_in_NYIintrinsicMissing = false;
          }
      }
    return Value.UNDEFINED;
  }


  static
  {
    put("Type.name"                      , cl -> cl._dfa.newConstString(cl._dfa._fuir.clazzTypeName(cl._dfa._fuir.clazzOuterClazz(cl._cc)), cl) );
    put("safety"                         , cl -> cl._dfa._options.fuzionSafety() ? cl._dfa._true : cl._dfa._false );
    put("debug"                          , cl -> cl._dfa._options.fuzionDebug()  ? cl._dfa._true : cl._dfa._false );
    put("debugLevel"                     , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc), cl._dfa._options.fuzionDebugLevel()) );

    put("fuzion.std.args.count"          , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("fuzion.std.args.get"            , cl -> cl._dfa.newConstString(null, cl) );
    put("fuzion.std.exit"                , cl -> null );
    put("fuzion.std.out.write"           , cl -> Value.UNIT );
    put("fuzion.std.err.write"           , cl -> Value.UNIT );
    put("fuzion.std.fileio.read"         , cl -> cl._dfa._bool ); // NYI : manipulation of an array passed as argument needs to be tracked and recorded
    put("fuzion.std.fileio.get_file_size", cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("fuzion.std.fileio.write"        , cl -> cl._dfa._bool );
    put("fuzion.std.fileio.exists"       , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("fuzion.std.fileio.delete"       , cl -> cl._dfa._bool );
    put("fuzion.std.fileio.move"         , cl -> cl._dfa._bool );
    put("fuzion.std.fileio.create_dir"   , cl -> cl._dfa._bool );
    put("fuzion.std.out.flush"           , cl -> Value.UNIT );
    put("fuzion.std.err.flush"           , cl -> Value.UNIT );
    put("fuzion.stdin.nextByte"          , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );

    put("i8.prefix -°"                   , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i16.prefix -°"                  , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i32.prefix -°"                  , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i64.prefix -°"                  , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i8.infix -°"                    , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i16.infix -°"                   , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i32.infix -°"                   , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i64.infix -°"                   , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i8.infix +°"                    , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i16.infix +°"                   , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i32.infix +°"                   , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i64.infix +°"                   , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i8.infix *°"                    , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i16.infix *°"                   , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i32.infix *°"                   , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i64.infix *°"                   , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i8.div"                         , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i16.div"                        , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i32.div"                        , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i64.div"                        , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i8.mod"                         , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i16.mod"                        , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i32.mod"                        , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i64.mod"                        , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i8.infix <<"                    , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i16.infix <<"                   , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i32.infix <<"                   , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i64.infix <<"                   , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i8.infix >>"                    , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i16.infix >>"                   , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i32.infix >>"                   , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i64.infix >>"                   , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i8.infix &"                     , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i16.infix &"                    , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i32.infix &"                    , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i64.infix &"                    , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i8.infix |"                     , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i16.infix |"                    , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i32.infix |"                    , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i64.infix |"                    , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i8.infix ^"                     , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i16.infix ^"                    , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i32.infix ^"                    , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );
    put("i64.infix ^"                    , cl -> { return new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)); } );

    put("i8.infix =="                    , cl -> cl._dfa._bool );
    put("i16.infix =="                   , cl -> cl._dfa._bool );
    put("i32.infix =="                   , cl -> cl._dfa._bool );
    put("i64.infix =="                   , cl -> cl._dfa._bool );
    put("i8.infix !="                    , cl -> cl._dfa._bool );
    put("i16.infix !="                   , cl -> cl._dfa._bool );
    put("i32.infix !="                   , cl -> cl._dfa._bool );
    put("i64.infix !="                   , cl -> cl._dfa._bool );
    put("i8.infix >"                     , cl -> cl._dfa._bool );
    put("i16.infix >"                    , cl -> cl._dfa._bool );
    put("i32.infix >"                    , cl -> cl._dfa._bool );
    put("i64.infix >"                    , cl -> cl._dfa._bool );
    put("i8.infix >="                    , cl -> cl._dfa._bool );
    put("i16.infix >="                   , cl -> cl._dfa._bool );
    put("i32.infix >="                   , cl -> cl._dfa._bool );
    put("i64.infix >="                   , cl -> cl._dfa._bool );
    put("i8.infix <"                     , cl -> cl._dfa._bool );
    put("i16.infix <"                    , cl -> cl._dfa._bool );
    put("i32.infix <"                    , cl -> cl._dfa._bool );
    put("i64.infix <"                    , cl -> cl._dfa._bool );
    put("i8.infix <="                    , cl -> cl._dfa._bool );
    put("i16.infix <="                   , cl -> cl._dfa._bool );
    put("i32.infix <="                   , cl -> cl._dfa._bool );
    put("i64.infix <="                   , cl -> cl._dfa._bool );

    put("u8.prefix -°"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u16.prefix -°"                  , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u32.prefix -°"                  , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u64.prefix -°"                  , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u8.infix -°"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u16.infix -°"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u32.infix -°"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u64.infix -°"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u8.infix +°"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u16.infix +°"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u32.infix +°"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u64.infix +°"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u8.infix *°"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u16.infix *°"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u32.infix *°"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u64.infix *°"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u8.div"                         , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u16.div"                        , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u32.div"                        , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u64.div"                        , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u8.mod"                         , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u16.mod"                        , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u32.mod"                        , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u64.mod"                        , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u8.infix <<"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u16.infix <<"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u32.infix <<"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u64.infix <<"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u8.infix >>"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u16.infix >>"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u32.infix >>"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u64.infix >>"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u8.infix &"                     , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u16.infix &"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u32.infix &"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u64.infix &"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u8.infix |"                     , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u16.infix |"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u32.infix |"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u64.infix |"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u8.infix ^"                     , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u16.infix ^"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u32.infix ^"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u64.infix ^"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );

    put("u8.infix =="                    , cl -> cl._dfa._bool );
    put("u16.infix =="                   , cl -> cl._dfa._bool );
    put("u32.infix =="                   , cl -> cl._dfa._bool );
    put("u64.infix =="                   , cl -> cl._dfa._bool );
    put("u8.infix !="                    , cl -> cl._dfa._bool );
    put("u16.infix !="                   , cl -> cl._dfa._bool );
    put("u32.infix !="                   , cl -> cl._dfa._bool );
    put("u64.infix !="                   , cl -> cl._dfa._bool );
    put("u8.infix >"                     , cl -> cl._dfa._bool );
    put("u16.infix >"                    , cl -> cl._dfa._bool );
    put("u32.infix >"                    , cl -> cl._dfa._bool );
    put("u64.infix >"                    , cl -> cl._dfa._bool );
    put("u8.infix >="                    , cl -> cl._dfa._bool );
    put("u16.infix >="                   , cl -> cl._dfa._bool );
    put("u32.infix >="                   , cl -> cl._dfa._bool );
    put("u64.infix >="                   , cl -> cl._dfa._bool );
    put("u8.infix <"                     , cl -> cl._dfa._bool );
    put("u16.infix <"                    , cl -> cl._dfa._bool );
    put("u32.infix <"                    , cl -> cl._dfa._bool );
    put("u64.infix <"                    , cl -> cl._dfa._bool );
    put("u8.infix <="                    , cl -> cl._dfa._bool );
    put("u16.infix <="                   , cl -> cl._dfa._bool );
    put("u32.infix <="                   , cl -> cl._dfa._bool );
    put("u64.infix <="                   , cl -> cl._dfa._bool );

    put("i8.as_i32"                      , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("i16.as_i32"                     , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("i32.as_i64"                     , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("i32.as_f64"                     , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("i64.as_f64"                     , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u8.as_i32"                      , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u16.as_i32"                     , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u32.as_i64"                     , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u32.as_f64"                     , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u64.as_f64"                     , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("i8.castTo_u8"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("i16.castTo_u16"                 , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("i32.castTo_u32"                 , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("i64.castTo_u64"                 , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u8.castTo_i8"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u16.castTo_i16"                 , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u32.castTo_i32"                 , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u32.castTo_f32"                 , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u64.castTo_i64"                 , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u64.castTo_f64"                 , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u16.low8bits"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u32.low8bits"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u64.low8bits"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u32.low16bits"                  , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u64.low16bits"                  , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("u64.low32bits"                  , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );

    put("f32.prefix -"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64.prefix -"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32.infix +"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64.infix +"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32.infix -"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64.infix -"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32.infix *"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64.infix *"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32.infix /"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64.infix /"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32.infix %"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64.infix %"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32.infix **"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64.infix **"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32.infix =="                   , cl -> cl._dfa._bool );
    put("f64.infix =="                   , cl -> cl._dfa._bool );
    put("f32.infix !="                   , cl -> cl._dfa._bool );
    put("f64.infix !="                   , cl -> cl._dfa._bool );
    put("f32.infix <"                    , cl -> cl._dfa._bool );
    put("f64.infix <"                    , cl -> cl._dfa._bool );
    put("f32.infix <="                   , cl -> cl._dfa._bool );
    put("f64.infix <="                   , cl -> cl._dfa._bool );
    put("f32.infix >"                    , cl -> cl._dfa._bool );
    put("f64.infix >"                    , cl -> cl._dfa._bool );
    put("f32.infix >="                   , cl -> cl._dfa._bool );
    put("f64.infix >="                   , cl -> cl._dfa._bool );
    put("f32.as_f64"                     , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64.as_f32"                     , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64.as_i64_lax"                 , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32.castTo_u32"                 , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64.castTo_u64"                 , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32.asString"                   , cl -> cl._dfa.newConstString(null, cl) );
    put("f64.asString"                   , cl -> cl._dfa.newConstString(null, cl) );

    put("f32s.minExp"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.maxExp"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.minPositive"               , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.max"                       , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.epsilon"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.isNaN"                     , cl -> cl._dfa._bool );
    put("f64s.isNaN"                     , cl -> cl._dfa._bool );
    put("f64s.minExp"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64s.maxExp"                    , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64s.minPositive"               , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64s.max"                       , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64s.epsilon"                   , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.squareRoot"                , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64s.squareRoot"                , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.exp"                       , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64s.exp"                       , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.log"                       , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64s.log"                       , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.sin"                       , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64s.sin"                       , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.cos"                       , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64s.cos"                       , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.tan"                       , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64s.tan"                       , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.asin"                      , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64s.asin"                      , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.acos"                      , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64s.acos"                      , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.atan"                      , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64s.atan"                      , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.atan2"                     , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64s.atan2"                     , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.sinh"                      , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64s.sinh"                      , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.cosh"                      , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64s.cosh"                      , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f32s.tanh"                      , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("f64s.tanh"                      , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );

    put("Object.hashCode"                , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );
    put("Object.asString"                , cl -> cl._dfa.newConstString(null, cl) );
    put("fuzion.sys.array.alloc"         , cl -> { return new SysArray(cl._dfa, new byte[0]); } ); // NYI: get length from args
    put("fuzion.sys.array.setel"         , cl ->
        {
          var array = cl._args.get(0);
          var index = cl._args.get(1);
          var value = cl._args.get(2);
          if (array instanceof SysArray sa)
            {
              sa.setel(index, value);
              return Value.UNIT;
            }
          else
            {
              throw new Error("intrinsic fuzion.sys.array.setel: Expected class SysArray, found "+array.getClass()+" "+array);
            }
        });
    put("fuzion.sys.array.get"           , cl ->
        {
          var array = cl._args.get(0);
          var index = cl._args.get(1);
          if (array instanceof SysArray sa)
            {
              return sa.get(index);
            }
          else
            {
              throw new Error("intrinsic fuzion.sys.array.gel: Expected class SysArray, found "+array.getClass()+" "+array);
            }
        });
    put("fuzion.sys.env_vars.has0"       , cl -> cl._dfa._bool );
    put("fuzion.sys.env_vars.get0"       , cl -> cl._dfa.newConstString(null, cl) );
    put("fuzion.sys.thread.spawn0"       , cl ->
        {
          var oc = cl._dfa._fuir.clazzActualGeneric(cl._cc, 0);
          var call = cl._dfa._fuir.lookupCall(oc);

          if (CHECKS) check
            (cl._dfa._fuir.clazzNeedsCode(call));

          // NYI: spawn0 needs to set up an environment representing the new
          // thread and perform thread-related checks (race-detection. etc.)!
          var ncl = cl._dfa.newCall(call, false, cl._args.get(0), new List<>(), null /* new enviroment */, cl);
          return Value.UNIT;
        });
    put("fuzion.std.nano_sleep"          , cl -> Value.UNIT );
    put("fuzion.std.nano_time"           , cl -> new NumericValue(cl._dfa, cl._dfa._fuir.clazzResultClazz(cl._cc)) );

    put("effect.replace"                 , cl ->
        {
          var ecl = cl._dfa._fuir.effectType(cl._cc);
          var new_e = cl._target;
          cl.replaceEffect(ecl, new_e);
          return Value.UNIT;
        });
    put("effect.default"                 , cl ->
        {
          var ecl = cl._dfa._fuir.effectType(cl._cc);
          var oc = cl._dfa._fuir.clazzOuterClazz(cl._cc);
          var new_e = cl._target;
          var old_e = cl._dfa._defaultEffects.get(ecl);
          if (old_e != null)
            {
              new_e = old_e.join(new_e);
            }
          if (old_e == null || Value.compare(old_e, new_e) != 0)
            {
              cl._dfa._defaultEffects.put(ecl, new_e);
              if (!cl._dfa._changed)
                {
                  cl._dfa._changedSetBy = "effect.defaut called: "+cl._dfa._fuir.clazzAsString(cl._cc);
                }
              cl._dfa._changed = true;
            }
          return Value.UNIT;
        });
    put("effect.abortable"               , cl ->
        {
          var ecl = cl._dfa._fuir.effectType(cl._cc);
          var oc = cl._dfa._fuir.clazzActualGeneric(cl._cc, 0);
          var call = cl._dfa._fuir.lookupCall(oc);

          if (CHECKS) check
            (cl._dfa._fuir.clazzNeedsCode(call));

          var env = cl._env;
          var newEnv = cl._dfa.newEnv(cl, env, ecl, cl._target);
          var ncl = cl._dfa.newCall(call, false, cl._args.get(0), new List<>(), newEnv, cl);
          // NYI: result must be null if result of ncl is null (ncl does not return) and effect.abort is not called
          return Value.UNIT;
        });
    put("effect.abort"                   , cl ->
        {
          var ecl = cl._dfa._fuir.effectType(cl._cc);
          var new_e = cl._target;
          cl.replaceEffect(ecl, new_e);
          // NYI: we might have to do cl.returns() for 'cl' being the
          // corresponding call to 'effect.abortable' and make sure new_e is
          // used to create the value produced by the effect.
          return null;
        });
    put("effects.exists"                 , cl -> cl.getEffect(cl._dfa._fuir.clazzActualGeneric(cl._cc, 0)) != null
        ? cl._dfa._true
        : cl._dfa._bool  /* NYI: currently, this is never FALSE since a default effect might get installed turning this into TRUE
                          * should reconsider if handling of default effects changes
                          */
        );

    put("fuzion.java.JavaObject.isNull"  , cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.arrayGet"           , cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.arrayLength"        , cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.arrayToJavaObject0" , cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.boolToJavaObject"   , cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.callC0"             , cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.callS0"             , cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.callV0"             , cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.f32ToJavaObject"    , cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.f64ToJavaObject"    , cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.getField0"          , cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.getStaticField0"    , cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.i16ToJavaObject"    , cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.i32ToJavaObject"    , cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.i64ToJavaObject"    , cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.i8ToJavaObject"     , cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.javaStringToString" , cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.stringToJavaObject0", cl -> NYIintrinsicMissing(cl) );
    put("fuzion.java.u16ToJavaObject"    , cl -> NYIintrinsicMissing(cl) );
  }


  /**
   * Add given value to the set of default effect values for effect type ecl.
   */
  void replaceDefaultEffect(int ecl, Value e)
  {
    var oe = _defaultEffects.get(ecl);
    Value ne;
    if (oe == null)
      {
        if (false)
          {
            // NYI: Check why this can happen.
            throw new Error("replaceDefaultEffect called when there is no default effect!");
          }
        ne = oe;
      }
    else
      {
        ne = e.join(oe);
      }
    if (Value.compare(oe, ne) != 0)
      {
        _defaultEffects.put(ecl, ne);
        if (!_changed)
          {
            _changedSetBy = "effect.replace called: " + _fuir.clazzAsString(ecl);
          }
        _changed = true;
      }
  }



  /**
   * Check if given clazz is a built-in numeric clazz: i8..i64, u8..u64, f32 or f64.
   *
   * @param cl the clazz
   *
   * @return true iff cl is built-in numeric;
   */
  boolean isBuiltInNumeric(int cl)
  {
    return switch (_fuir.getSpecialId(cl))
      {
      case
        c_i8   ,
        c_i16  ,
        c_i32  ,
        c_i64  ,
        c_u8   ,
        c_u16  ,
        c_u32  ,
        c_u64  ,
        c_f32  ,
        c_f64  -> true;
      default -> false;
      };
  }


  /**
   * Create instance of given clazz.
   *
   * @param cl the clazz
   *
   * @param context for debugging: Reason that causes this instance to be part
   * of the analysis.
   */
  Value newInstance(int cl, Context context)
  {
    Value r;
    if (isBuiltInNumeric(cl))
      {
        r = new NumericValue(DFA.this, cl);
      }
    else
      {
        if (_fuir.clazzIsRef(cl))
          {
            var vc = _fuir.clazzAsValue(cl);
            r = newInstance(vc, context).box(this, vc, cl, context);
          }
        else
          {
            r = new Instance(this, cl, context);
          }
      }
    return cache(r);
  }


  /**
   * Check if value 'r' exists already. If so, return the existing
   * one. Otherwise, add 'r' to the set of existing values, set _changed since
   * the state has changed and return r.
   */
  Value cache(Value r)
  {
    var e = _instances.get(r);
    if (e == null)
      {
        _instances.put(r, r);
        e = r;
        if (SHOW_STACK_ON_CHANGE && !_changed) Thread.dumpStack();
        if (!_changed)
          {
            _changedSetBy = "DFA.newInstance for "+_fuir.clazzAsString(r._clazz);
          }
        _changed = true;
      }
    return e;
  }


  /**
   * Create constant string with given utf8 bytes.
   *
   * @param utf8Bytes the string contents or null if contents unknown
   *
   * @param context for debugging: Reason that causes this const string to be
   * part of the analysis.
   */
  Value newConstString(byte[] utf8Bytes, Context context)
  {
    var cs            = _fuir.clazz_conststring();
    var internalArray = _fuir.clazz_conststring_internalArray();
    var data          = _fuir.clazz_fuzionSysArray_u8_data();
    var length        = _fuir.clazz_fuzionSysArray_u8_length();
    var sysArray      = _fuir.clazzResultClazz(internalArray);
    var adata = utf8Bytes != null ? new SysArray(this, utf8Bytes)
                                  : new SysArray(this, new NumericValue(this, _fuir.clazz(FUIR.SpecialClazzes.c_u8)));
    var r = newInstance(cs, context);
    var a = newInstance(sysArray, context);
    a.setField(this,
               length,
                utf8Bytes != null ? new NumericValue(this, _fuir.clazzResultClazz(length), utf8Bytes.length)
                                  : new NumericValue(this, _fuir.clazzResultClazz(length)));
    a.setField(this, data  , adata);
    r.setField(this, internalArray, a);
    return r;
  }


  /**
   * Create call to given clazz with given target and args.
   *
   * @param cl the called clazz
   *
   * @param pre true iff precondition is called
   *
   * @param tvalue the target value on which cl is called
   *
   * @param args the arguments passed to the call
   *
   * @param env the environment at the call or null if none.
   *
   * @param context for debugging: Reason that causes this call to be part of
   * the analysis.
   *
   * @return cl a new or exsiting call to cl (or its precondition) with the
   * given target, args and environment.
   */
  Call newCall(int cl, boolean pre, Value tvalue, List<Value> args, Env env, Context context)
  {
    var r = new Call(this, cl, pre, tvalue, args, env, context);
    var e = _calls.get(r);
    if (e == null)
      {
        _newCalls.add(r);
        _calls.put(r,r);
        e = r;
        if (SHOW_STACK_ON_CHANGE && !_changed) { System.out.println("new call: "+r); Thread.dumpStack();}
        if (!_changed)
          {
            _changedSetBy = "DFA.newCall to "+e;
          }
        _changed = true;
      }
    return e;
  }


  /**
   * Create instance of 'Site' for given clazz, code block and index.
   */
  Site site(int cl, int c, int i)
    {
      var cs = new Site(cl, c, i);
      var res = _sites.get(cs);
      if (res == null)
        {
          _sites.put(cs, cs);
          res = cs;
        }
      return res;
    }


  /**
   * Create new Env for given existing env and effect type  and value pair.
   *
   * @param cl the current clazz that installs a new effect
   *
   * @param env the previous environemnt.
   *
   * @param ecl the effect types
   *
   * @param ev the effect value
   *
   * @return new or existing Env instance created from env by adding ecl/ev.
   */
  Env newEnv(Call cl, Env env, int ecl, Value ev)
  {
    var newEnv = new Env(cl, env, ecl, cl._target);
    var e = _envs.get(newEnv);
    if (e == null)
      {
        _envs.put(newEnv, newEnv);
        e = newEnv;
        if (SHOW_STACK_ON_CHANGE && !_changed) { System.out.println("new env: " + e); Thread.dumpStack();}
        if (!_changed)
          {
            _changedSetBy = "DFA.newEnv for " + e;
          }
        _changed = true;
      }
    return e;
  }

}

/* end of file */
