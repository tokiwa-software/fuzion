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

package dev.flang.fuir.analysis;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.Graph;
import dev.flang.util.List;
import dev.flang.util.MapToN;
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
    void analyze(DFA dfa, int cl);
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
     * The instance we are analysing.
     */
    final Instance _current;


    /**
     * Create processor for an abstract interpreter doing DFA analysis of the
     * given instance's cod.
     */
    Analyze(Instance current)
    {
      _current = current;
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
     * Perform an assignment of avalue to a field in tvalue. The type of tvalue
     * might be dynamic (a refernce). See FUIR.acess*().
     */
    public Unit assign(int cl, int c, int i, Value tvalue, Value avalue)
    {
      return access(cl, c, i, tvalue, new List<>(avalue))._v1;
    }


    /**
     * Perform a call of a feature with target instance tvalue with given
     * arguments.. The type of tvalue might be dynamic (a refernce). See
     * FUIR.acess*().
     *
     * Result._v0 may be null to indicate that code generation should stop here
     * (due to an error or tail recursion optimization).
     */
    public Pair<Value, Unit> call(int cl, int c, int i, Value tvalue, List<Value> args)
    {
      var cc0 = _fuir.accessedClazz  (cl, c, i);
      if (_fuir.clazzContract(cc0, FUIR.ContractKind.Pre, 0) != -1)
        {
          System.err.println("NYI: DFA.call Precondition for "+_fuir.codeAtAsString(cl, c, i));
          // var callpair = C.this.call(cl, tvalue, args, c, i, cc0, true);
        }
      var res = Value.UNIT;
      if (!_fuir.callPreconditionOnly(cl, c, i))
        {
          var r = access(cl, c, i, tvalue, args);
          res = r._v0;
        }
      return new Pair<>(res, _unit_);
    }


    /**
     * Create code to access (call or write) a feature.
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
     * @return statement to perform the given access
     */
    Pair<Value, Unit> access(int cl, int c, int i, Value tvalue, List<Value> args)
    {
      var result = _unit_;
      var res = Value.UNIT;
      var isCall = _fuir.codeAt(c, i) == FUIR.ExprKind.Call;
      var cc0 = _fuir.accessedClazz  (cl, c, i);
      var tc = _fuir.accessTargetClazz(cl, c, i);
      var rt = _fuir.clazzResultClazz(cc0); // only needed if isCall

      if (_fuir.accessIsDynamic(cl, c, i))
        {
          var ccs = _fuir.accessedClazzes(cl, c, i);
          if (ccs.length == 0)
            {
              // NYI: proper error reporting
              System.out.println("NYI: DFA: no targets for "+_fuir.codeAtAsString(cl, c, i));
              /* NYI:
            ol.add(reportErrorInCode("no targets for dynamic access of %s within %s",
                                     CExpr.string(_fuir.clazzAsString(cc0)),
                                     CExpr.string(_fuir.clazzAsString(cl ))));
              */
            }
          boolean found = false;
          for (var cci = 0; cci < ccs.length; cci += 2)
            {
              var tt = ccs[cci  ];
              var cc = ccs[cci+1];
              if (tvalue instanceof Instance tvaluei &&
                  tvaluei._clazz == tt)
                {
                  found = true;
                  var rti = _fuir.clazzResultClazz(cc);
                  if (isCall)
                    {
                      var calpair = call(cl, tvalue, args, c, i, cc, false);
                      res = calpair._v0;
                      result = calpair._v1;
                    }
                  else
                    {
                      tvalue.setField(cc, args.get(0));
                    }
                }
            }
          if (!found)
            {
              // NYI: proper error reporting
              System.out.println("NYI: no targets for call to "+_fuir.codeAtAsString(cl, c, i)+" target "+tvalue);
            }
        }
      else if (_fuir.clazzNeedsCode(cc0))
        {
          if (isCall)
            {
              var callpair = call(cl, tvalue, args, c, i, cc0, false);
              res = callpair._v0;
              result = callpair._v1;
            }
          else
            {
              tvalue.setField(cc0, args.get(0));
            }
        }
      else
        {
          System.out.println("NYI: DFA call to nowhere for "+_fuir.codeAtAsString(cl, c, i));
          /* NYI: proper error reporting
        result = reportErrorInCode("no code generated for static access to %s within %s",
                                   CExpr.string(_fuir.clazzAsString(cc0)),
                                   CExpr.string(_fuir.clazzAsString(cl )));
          */
          res = null;
        }
      /* NYI:
      if (res != null && isCall)
        {
          res = _fuir.clazzIsVoidType(rt) ? null :
            _types.hasData(rt) && _fuir.clazzFieldIsAdrOfValue(cc0) ? res.deref() : res; // NYI: deref an outer ref to value type. Would be nice to have a separate statement for this
            } */
    return new Pair(res, result);
  }


  /**
   * Create C code for a statically bound call.
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
   * @return the code to perform the call
   */
    Pair<Value, Unit> call(int cl, Value tvalue, List<Value> args, int c, int i, int cc, boolean pre)
    {
      var result = _unit_;
      var resultValue = Value.UNIT;
      var tc = _fuir.accessTargetClazz(cl, c, i);
      var rt = _fuir.clazzResultClazz(cc);
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
                var ca = newCall(cc, pre, tvalue, args);
                resultValue = ca.result();
              }
            break;
          }
        case Field:
          {
            resultValue = tvalue.readField(tc, cc);
            break;
          }
        default:       throw new Error("This should not happen: Unknown feature kind: " + _fuir.clazzKind(cc));
        }
      return new Pair<>(resultValue, result);
    }


    /**
     * For a given value v of value type vc create a boxed ref value of type rc.
     */
    public Pair<Value, Unit> box(Value val, int vc, int rc)
    {
      System.err.println("NYI: DFA.box");
      return new Pair<>(Value.UNIT, _unit_);
    }


    /**
     * For a given reference value v create an unboxed value of type vc.
     */
    public Pair<Value, Unit> unbox(Value val, int orc)
    {
      System.err.println("NYI: DFA.unbox");
      return new Pair<>(Value.UNIT, _unit_);
    }


    /**
     * Get the current instance
     */
    public Pair<Value, Unit> current(int cl)
    {
      return new Pair<>(_current, _unit_);
    }


    /**
     * Get a constant value of type constCl with given byte data d.
     */
    public Pair<Value, Unit> constData(int constCl, byte[] d)
    {
      var o = _unit_;
      var r = switch (_fuir.getSpecialId(constCl))
        {
        case c_bool -> d[0] == 1 ? Value.TRUE : Value.FALSE;
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
        case c_conststring -> newInstance(constCl);
        default ->
        {
          Errors.error("Unsupported constant in DFA analysis.",
                       "DFA cannot handle constant of clazz '" + _fuir.clazzAsString(constCl) + "' ");
          yield Value.UNIT;
        }
        };
      return new Pair(r, o);
    }


    /**
     * Perform a match on value subv.
     */
    public Unit match(int cl, int c, int i, Value subv)
    {
      System.err.println("NYI: DFA.match for "+_fuir.codeAtAsString(cl, c, i));
      return _unit_;
      /*
      var subjClazz = _fuir.matchStaticSubject(cl, c, i);
      var sub       = fields(subv, subjClazz);
      var uniyon    = sub.field(_names.CHOICE_UNION_NAME);
      var hasTag    = !_fuir.clazzIsChoiceOfOnlyRefs(subjClazz);
      var refEntry  = uniyon.field(_names.CHOICE_REF_ENTRY_NAME);
      var ref       = hasTag ? refEntry                   : _names.newTemp();
      var getRef    = hasTag ? _unit_               : Unit.decl(_types.clazz(_fuir.clazzObject()), (CIdent) ref, refEntry);
      var tag       = hasTag ? sub.field(_names.TAG_NAME) : ref.castTo("int64_t");
      var tcases    = new List<Unit>(); // cases depending on tag value or ref cast to int64
      var rcases    = new List<Unit>(); // cases depending on clazzId of ref type
      Unit tdefault = null;
      for (var mc = 0; mc < _fuir.matchCaseCount(c, i); mc++)
        {
          var ctags = new List<Value>();
          var rtags = new List<Value>();
          var tags = _fuir.matchCaseTags(cl, c, i, mc);
          for (var tagNum : tags)
            {
              var tc = _fuir.clazzChoice(subjClazz, tagNum);
              if (tc != -1)
                {
                  if (!hasTag && _fuir.clazzIsRef(tc))  // do we need to check the clazzId of a ref?
                    {
                      for (var h : _fuir.clazzInstantiatedHeirs(tc))
                        {
                          rtags.add(_names.clazzId(h).comment(_fuir.clazzAsString(h)));
                        }
                    }
                  else
                    {
                      ctags.add(Value.int32const(tagNum).comment(_fuir.clazzAsString(tc)));
                      if (CHECKS) check
                        (hasTag || !_types.hasData(tc));
                    }
                }
            }
          var sl = new List<Unit>();
          var field = _fuir.matchCaseField(cl, c, i, mc);
          if (field != -1)
            {
              var fclazz = _fuir.clazzResultClazz(field);     // static clazz of assigned field
              var f      = field(cl, C.this.current(cl), field);
              var entry  = _fuir.clazzIsRef(fclazz) ? ref.castTo(_types.clazz(fclazz)) :
                           _types.hasData(fclazz)   ? uniyon.field(new CIdent(_names.CHOICE_ENTRY_NAME + tags[0]))
                                                    : Value.UNIT;
              sl.add(C.this.assign(f, entry, fclazz));
            }
          sl.add(_ai.process(cl, _fuir.matchCaseCode(c, i, mc)));
          sl.add(Unit.BREAK);
          var cazecode = Unit.seq(sl);
          tcases.add(Unit.caze(ctags, cazecode));  // tricky: this a NOP if ctags.isEmpty
          if (!rtags.isEmpty()) // we need default clause to handle refs without a tag
            {
              rcases.add(Unit.caze(rtags, cazecode));
              tdefault = cazecode;
            }
        }
      if (rcases.size() >= 2)
        { // more than two reference cases: we have to create separate switch of clazzIds for refs
          var id = refEntry.deref().field(_names.CLAZZ_ID);
          var notFound = reportErrorInCode("unexpected reference type %d found in match", id);
          tdefault = Unit.suitch(id, rcases, notFound);
        }
      return Unit.seq(getRef, Unit.suitch(tag, tcases, tdefault));
      */
    }


    /**
     * Create a tagged value of type newcl from an untagged value for type valuecl.
     */
    public Pair<Value, Unit> tag(int cl, int valuecl, Value value, int newcl, int tagNum)
    {
      System.err.println("NYI: DFA.tag");
      return new Pair<>(Value.UNIT, _unit_);
      /*
      var res     = _names.newTemp();
      var tag     = res.field(_names.TAG_NAME);
      var uniyon  = res.field(_names.CHOICE_UNION_NAME);
      var entry   = uniyon.field(_fuir.clazzIsRef(valuecl) ||
                                 _fuir.clazzIsChoiceOfOnlyRefs(newcl) ? _names.CHOICE_REF_ENTRY_NAME
                                                                      : new CIdent(_names.CHOICE_ENTRY_NAME + tagNum));
      if (_fuir.clazzIsUnitType(valuecl) && _fuir.clazzIsChoiceOfOnlyRefs(newcl))
        {// replace unit-type values by 0, 1, 2, 3,... cast to ref Object
          if (CHECKS) check
            (value == Value.UNIT);
          if (tagNum >= CConstants.PAGE_SIZE)
            {
              Errors.error("Number of tags for choice type exceeds page size.",
                           "While creating code for '" + _fuir.clazzAsString(cl) + "'\n" +
                           "Found in choice type '" + _fuir.clazzAsString(newcl)+ "'\n");
            }
          value = Value.int32const(tagNum);
          valuecl = _fuir.clazzObject();
        }
      if (_fuir.clazzIsRef(valuecl))
        {
          value = value.castTo(_types.clazz(_fuir.clazzObject()));
        }
      var o = Unit.seq(Unit.lineComment("Tag a value to be of choice type " + _fuir.clazzAsString(newcl) +
                                            " static value type " + _fuir.clazzAsString(valuecl)),
                         Unit.decl(_types.clazz(newcl), res),
                         _fuir.clazzIsChoiceOfOnlyRefs(newcl) ? _unit_ : tag.assign(Value.int32const(tagNum)),
                         C.this.assign(entry, value, valuecl));

      return new Pair<Value, Unit>(res, o);
      */
    }


    /**
     * Access the effect of type ecl that is installed in the environemnt.
     */
    public Pair<Value, Unit> env(int ecl)
    {
      System.err.println("NYI: DFA.env");
      return new Pair<>(Value.UNIT, _unit_);
      /*
      var res = _names.env(ecl);
      var evi = _names.envInstalled(ecl);
      var o = Unit.iff(evi.not(),
                         Unit.seq(Value.fprintfstderr("*** effect %s not present in current environment\n",
                                                        Value.string(_fuir.clazzAsString(ecl))),
                                    Value.exit(1)));
      return new Pair<Value, Unit>(res, o);
      */
    }


    /**
     * Process a contract of kind ck of clazz cl that results in bool value cc
     * (i.e., the contract fails if !cc).
     */
    public Unit contract(int cl, FUIR.ContractKind ck, Value cc)
    {
      System.err.println("NYI: DFA.contract");
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
   * The intermediate code we are analysing.
   */
  public final FUIR _fuir;


  /**
   * Instances created during DFA analysis.
   */
  TreeMap<Instance, Instance> _instances = new TreeMap<>();


  /**
   * Calls created during DFA analysis.
   */
  TreeMap<Call, Call> _calls = new TreeMap<>();


  /**
   * Flag to detect changes during current iteration of the fix-point algorithm.
   * If this remains false during one iteration we have reached a fix-point.
   */
  boolean _changed = false;


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
   * @param fuir the intermediate code.
   */
  public DFA(FUIR fuir)
  {
    _fuir = fuir;
    Errors.showAndExit();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Perform DFA analysis
   */
  public void dfa()
  {
    var main = newInstance(_fuir.mainClazzId());
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
        System.out.println("DFA iteration #"+cnt+": --------------------------------------------------");
        _changed = false;
        iteration();
      }
    while (_changed && cnt < 10);
    System.out.println("DFA done:");
    System.out.println("Instances: " + _instances.values());
    System.out.println("Calls: " + _calls.values());
    _reportResults = true;
    iteration();
  }


  /**
   * Perform one iteration of the analysis.
   */
  void iteration()
  {
    var s = _instances.values().toArray(new Instance[_instances.size()]);
    for (var i : s)
      {
        analyze(i);
      }
  }


  /**
   * analyze call to one instance
   */
  void analyze(Instance i)
  {
    var cl = i._clazz;
    var ck = _fuir.clazzKind(cl);
    switch (ck)
      {
      case Routine  : analyzeRoutine(i, false); break;
      case Intrinsic: analyzeIntrinsic(cl); break;
      }
    if (_fuir.clazzContract(cl, FUIR.ContractKind.Pre, 0) != -1)
      {
        analyzeRoutine(i, true);
      }
  }


  /**
   * Analyze code for given routine cl
   *
   * @param i the instance we are analyzing.
   *
   * @param pre true to analyze cl's precondition, false for cl itself.
   */
  void analyzeRoutine(Instance i, boolean pre)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzKind(i._clazz) == FUIR.FeatureKind.Routine || pre);

    var cl = i._clazz;
    if (pre)
      {
        // NYI: preOrPostCondition(cl, FUIR.ContractKind.Pre);
      }
    else
      {
        var ai = new AbstractInterpreter(_fuir, new Analyze(i));
        ai.process(cl, _fuir.clazzCode(cl));
      }
  }


  /**
   * Analyze given intrinsic cl
   *
   * @param cl id of clazz to analyze
   *
   */
  void analyzeIntrinsic(int cl)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzKind(cl) == FUIR.FeatureKind.Intrinsic);
    var in = _fuir.clazzIntrinsicName(cl);
    var c = _intrinsics_.get(in);
    if (c != null)
      {
        c.analyze(this, cl);
      }
    else
      {
        var at = _fuir.clazzTypeParameterActualType(cl);
        if (at >= 0)
          {
            // intrinsic is a type parameter
          }
        else
          {
            var msg = "code for intrinsic " + _fuir.clazzIntrinsicName(cl) + " is missing";
            Errors.warning(msg);
          }
      }
  }


  static
  {
    put("safety"                         , (dfa, cl) -> { } );
    put("debug"                          , (dfa, cl) -> { } );
    put("debugLevel"                     , (dfa, cl) -> { } );
    put("fuzion.std.args.count"          , (dfa, cl) -> { } );
    put("fuzion.std.args.get"            , (dfa, cl) -> { } );
    put("fuzion.std.exit"                , (dfa, cl) -> { } );
    put("fuzion.std.out.write"           , (dfa, cl) -> { } );
    put("fuzion.std.err.write"           , (dfa, cl) -> { } );
    put("fuzion.std.out.flush"           , (dfa, cl) -> { } );
    put("fuzion.std.err.flush"           , (dfa, cl) -> { } );
    put("fuzion.stdin.nextByte"          , (dfa, cl) -> { } );
    put("i8.prefix -°"                   , (dfa, cl) -> { } );
    put("i16.prefix -°"                  , (dfa, cl) -> { } );
    put("i32.prefix -°"                  , (dfa, cl) -> { } );
    put("i64.prefix -°"                  , (dfa, cl) -> { } );
    put("i8.infix -°"                    , (dfa, cl) -> { } );
    put("i16.infix -°"                   , (dfa, cl) -> { } );
    put("i32.infix -°"                   , (dfa, cl) -> { } );
    put("i64.infix -°"                   , (dfa, cl) -> { } );
    put("i8.infix +°"                    , (dfa, cl) -> { } );
    put("i16.infix +°"                   , (dfa, cl) -> { } );
    put("i32.infix +°"                   , (dfa, cl) -> { } );
    put("i64.infix +°"                   , (dfa, cl) -> { } );
    put("i8.infix *°"                    , (dfa, cl) -> { } );
    put("i16.infix *°"                   , (dfa, cl) -> { } );
    put("i32.infix *°"                   , (dfa, cl) -> { } );
    put("i64.infix *°"                   , (dfa, cl) -> { } );
    put("i8.div"                         , (dfa, cl) -> { } );
    put("i16.div"                        , (dfa, cl) -> { } );
    put("i32.div"                        , (dfa, cl) -> { } );
    put("i64.div"                        , (dfa, cl) -> { } );
    put("i8.mod"                         , (dfa, cl) -> { } );
    put("i16.mod"                        , (dfa, cl) -> { } );
    put("i32.mod"                        , (dfa, cl) -> { } );
    put("i64.mod"                        , (dfa, cl) -> { } );
    put("i8.infix <<"                    , (dfa, cl) -> { } );
    put("i16.infix <<"                   , (dfa, cl) -> { } );
    put("i32.infix <<"                   , (dfa, cl) -> { } );
    put("i64.infix <<"                   , (dfa, cl) -> { } );
    put("i8.infix >>"                    , (dfa, cl) -> { } );
    put("i16.infix >>"                   , (dfa, cl) -> { } );
    put("i32.infix >>"                   , (dfa, cl) -> { } );
    put("i64.infix >>"                   , (dfa, cl) -> { } );
    put("i8.infix &"                     , (dfa, cl) -> { } );
    put("i16.infix &"                    , (dfa, cl) -> { } );
    put("i32.infix &"                    , (dfa, cl) -> { } );
    put("i64.infix &"                    , (dfa, cl) -> { } );
    put("i8.infix |"                     , (dfa, cl) -> { } );
    put("i16.infix |"                    , (dfa, cl) -> { } );
    put("i32.infix |"                    , (dfa, cl) -> { } );
    put("i64.infix |"                    , (dfa, cl) -> { } );
    put("i8.infix ^"                     , (dfa, cl) -> { } );
    put("i16.infix ^"                    , (dfa, cl) -> { } );
    put("i32.infix ^"                    , (dfa, cl) -> { } );
    put("i64.infix ^"                    , (dfa, cl) -> { } );

    put("i8.infix =="                    , (dfa, cl) -> { } );
    put("i16.infix =="                   , (dfa, cl) -> { } );
    put("i32.infix =="                   , (dfa, cl) -> { } );
    put("i64.infix =="                   , (dfa, cl) -> { } );
    put("i8.infix !="                    , (dfa, cl) -> { } );
    put("i16.infix !="                   , (dfa, cl) -> { } );
    put("i32.infix !="                   , (dfa, cl) -> { } );
    put("i64.infix !="                   , (dfa, cl) -> { } );
    put("i8.infix >"                     , (dfa, cl) -> { } );
    put("i16.infix >"                    , (dfa, cl) -> { } );
    put("i32.infix >"                    , (dfa, cl) -> { } );
    put("i64.infix >"                    , (dfa, cl) -> { } );
    put("i8.infix >="                    , (dfa, cl) -> { } );
    put("i16.infix >="                   , (dfa, cl) -> { } );
    put("i32.infix >="                   , (dfa, cl) -> { } );
    put("i64.infix >="                   , (dfa, cl) -> { } );
    put("i8.infix <"                     , (dfa, cl) -> { } );
    put("i16.infix <"                    , (dfa, cl) -> { } );
    put("i32.infix <"                    , (dfa, cl) -> { } );
    put("i64.infix <"                    , (dfa, cl) -> { } );
    put("i8.infix <="                    , (dfa, cl) -> { } );
    put("i16.infix <="                   , (dfa, cl) -> { } );
    put("i32.infix <="                   , (dfa, cl) -> { } );
    put("i64.infix <="                   , (dfa, cl) -> { } );

    put("u8.prefix -°"                   , (dfa, cl) -> { } );
    put("u16.prefix -°"                  , (dfa, cl) -> { } );
    put("u32.prefix -°"                  , (dfa, cl) -> { } );
    put("u64.prefix -°"                  , (dfa, cl) -> { } );
    put("u8.infix -°"                    , (dfa, cl) -> { } );
    put("u16.infix -°"                   , (dfa, cl) -> { } );
    put("u32.infix -°"                   , (dfa, cl) -> { } );
    put("u64.infix -°"                   , (dfa, cl) -> { } );
    put("u8.infix +°"                    , (dfa, cl) -> { } );
    put("u16.infix +°"                   , (dfa, cl) -> { } );
    put("u32.infix +°"                   , (dfa, cl) -> { } );
    put("u64.infix +°"                   , (dfa, cl) -> { } );
    put("u8.infix *°"                    , (dfa, cl) -> { } );
    put("u16.infix *°"                   , (dfa, cl) -> { } );
    put("u32.infix *°"                   , (dfa, cl) -> { } );
    put("u64.infix *°"                   , (dfa, cl) -> { } );
    put("u8.div"                         , (dfa, cl) -> { } );
    put("u16.div"                        , (dfa, cl) -> { } );
    put("u32.div"                        , (dfa, cl) -> { } );
    put("u64.div"                        , (dfa, cl) -> { } );
    put("u8.mod"                         , (dfa, cl) -> { } );
    put("u16.mod"                        , (dfa, cl) -> { } );
    put("u32.mod"                        , (dfa, cl) -> { } );
    put("u64.mod"                        , (dfa, cl) -> { } );
    put("u8.infix <<"                    , (dfa, cl) -> { } );
    put("u16.infix <<"                   , (dfa, cl) -> { } );
    put("u32.infix <<"                   , (dfa, cl) -> { } );
    put("u64.infix <<"                   , (dfa, cl) -> { } );
    put("u8.infix >>"                    , (dfa, cl) -> { } );
    put("u16.infix >>"                   , (dfa, cl) -> { } );
    put("u32.infix >>"                   , (dfa, cl) -> { } );
    put("u64.infix >>"                   , (dfa, cl) -> { } );
    put("u8.infix &"                     , (dfa, cl) -> { } );
    put("u16.infix &"                    , (dfa, cl) -> { } );
    put("u32.infix &"                    , (dfa, cl) -> { } );
    put("u64.infix &"                    , (dfa, cl) -> { } );
    put("u8.infix |"                     , (dfa, cl) -> { } );
    put("u16.infix |"                    , (dfa, cl) -> { } );
    put("u32.infix |"                    , (dfa, cl) -> { } );
    put("u64.infix |"                    , (dfa, cl) -> { } );
    put("u8.infix ^"                     , (dfa, cl) -> { } );
    put("u16.infix ^"                    , (dfa, cl) -> { } );
    put("u32.infix ^"                    , (dfa, cl) -> { } );
    put("u64.infix ^"                    , (dfa, cl) -> { } );

    put("u8.infix =="                    , (dfa, cl) -> { } );
    put("u16.infix =="                   , (dfa, cl) -> { } );
    put("u32.infix =="                   , (dfa, cl) -> { } );
    put("u64.infix =="                   , (dfa, cl) -> { } );
    put("u8.infix !="                    , (dfa, cl) -> { } );
    put("u16.infix !="                   , (dfa, cl) -> { } );
    put("u32.infix !="                   , (dfa, cl) -> { } );
    put("u64.infix !="                   , (dfa, cl) -> { } );
    put("u8.infix >"                     , (dfa, cl) -> { } );
    put("u16.infix >"                    , (dfa, cl) -> { } );
    put("u32.infix >"                    , (dfa, cl) -> { } );
    put("u64.infix >"                    , (dfa, cl) -> { } );
    put("u8.infix >="                    , (dfa, cl) -> { } );
    put("u16.infix >="                   , (dfa, cl) -> { } );
    put("u32.infix >="                   , (dfa, cl) -> { } );
    put("u64.infix >="                   , (dfa, cl) -> { } );
    put("u8.infix <"                     , (dfa, cl) -> { } );
    put("u16.infix <"                    , (dfa, cl) -> { } );
    put("u32.infix <"                    , (dfa, cl) -> { } );
    put("u64.infix <"                    , (dfa, cl) -> { } );
    put("u8.infix <="                    , (dfa, cl) -> { } );
    put("u16.infix <="                   , (dfa, cl) -> { } );
    put("u32.infix <="                   , (dfa, cl) -> { } );
    put("u64.infix <="                   , (dfa, cl) -> { } );

    put("i8.as_i32"                      , (dfa, cl) -> { } );
    put("i16.as_i32"                     , (dfa, cl) -> { } );
    put("i32.as_i64"                     , (dfa, cl) -> { } );
    put("i32.as_f64"                     , (dfa, cl) -> { } );
    put("i64.as_f64"                     , (dfa, cl) -> { } );
    put("u8.as_i32"                      , (dfa, cl) -> { } );
    put("u16.as_i32"                     , (dfa, cl) -> { } );
    put("u32.as_i64"                     , (dfa, cl) -> { } );
    put("u32.as_f64"                     , (dfa, cl) -> { } );
    put("u64.as_f64"                     , (dfa, cl) -> { } );
    put("i8.castTo_u8"                   , (dfa, cl) -> { } );
    put("i16.castTo_u16"                 , (dfa, cl) -> { } );
    put("i32.castTo_u32"                 , (dfa, cl) -> { } );
    put("i64.castTo_u64"                 , (dfa, cl) -> { } );
    put("u8.castTo_i8"                   , (dfa, cl) -> { } );
    put("u16.castTo_i16"                 , (dfa, cl) -> { } );
    put("u32.castTo_i32"                 , (dfa, cl) -> { } );
    put("u32.castTo_f32"                 , (dfa, cl) -> { } );
    put("u64.castTo_i64"                 , (dfa, cl) -> { } );
    put("u64.castTo_f64"                 , (dfa, cl) -> { } );
    put("u16.low8bits"                   , (dfa, cl) -> { } );
    put("u32.low8bits"                   , (dfa, cl) -> { } );
    put("u64.low8bits"                   , (dfa, cl) -> { } );
    put("u32.low16bits"                  , (dfa, cl) -> { } );
    put("u64.low16bits"                  , (dfa, cl) -> { } );
    put("u64.low32bits"                  , (dfa, cl) -> { } );

    put("f32.prefix -"                   , (dfa, cl) -> { } );
    put("f64.prefix -"                   , (dfa, cl) -> { } );
    put("f32.infix +"                    , (dfa, cl) -> { } );
    put("f64.infix +"                    , (dfa, cl) -> { } );
    put("f32.infix -"                    , (dfa, cl) -> { } );
    put("f64.infix -"                    , (dfa, cl) -> { } );
    put("f32.infix *"                    , (dfa, cl) -> { } );
    put("f64.infix *"                    , (dfa, cl) -> { } );
    put("f32.infix /"                    , (dfa, cl) -> { } );
    put("f64.infix /"                    , (dfa, cl) -> { } );
    put("f32.infix %"                    , (dfa, cl) -> { } );
    put("f64.infix %"                    , (dfa, cl) -> { } );
    put("f32.infix **"                   , (dfa, cl) -> { } );
    put("f64.infix **"                   , (dfa, cl) -> { } );
    put("f32.infix =="                   , (dfa, cl) -> { } );
    put("f64.infix =="                   , (dfa, cl) -> { } );
    put("f32.infix !="                   , (dfa, cl) -> { } );
    put("f64.infix !="                   , (dfa, cl) -> { } );
    put("f32.infix <"                    , (dfa, cl) -> { } );
    put("f64.infix <"                    , (dfa, cl) -> { } );
    put("f32.infix <="                   , (dfa, cl) -> { } );
    put("f64.infix <="                   , (dfa, cl) -> { } );
    put("f32.infix >"                    , (dfa, cl) -> { } );
    put("f64.infix >"                    , (dfa, cl) -> { } );
    put("f32.infix >="                   , (dfa, cl) -> { } );
    put("f64.infix >="                   , (dfa, cl) -> { } );
    put("f32.as_f64"                     , (dfa, cl) -> { } );
    put("f64.as_f32"                     , (dfa, cl) -> { } );
    put("f64.as_i64_lax"                 , (dfa, cl) -> { } );
    put("f32.castTo_u32"                 , (dfa, cl) -> { } );
    put("f64.castTo_u64"                 , (dfa, cl) -> { } );
    put("f32.asString"                   , (dfa, cl) -> { } );
    put("f64.asString"                   , (dfa, cl) -> { } );

    put("f32s.minExp"                    , (dfa, cl) -> { } );
    put("f32s.maxExp"                    , (dfa, cl) -> { } );
    put("f32s.minPositive"               , (dfa, cl) -> { } );
    put("f32s.max"                       , (dfa, cl) -> { } );
    put("f32s.epsilon"                   , (dfa, cl) -> { } );
    put("f32s.isNaN"                     , (dfa, cl) -> { } );
    put("f64s.isNaN"                     , (dfa, cl) -> { } );
    put("f64s.minExp"                    , (dfa, cl) -> { } );
    put("f64s.maxExp"                    , (dfa, cl) -> { } );
    put("f64s.minPositive"               , (dfa, cl) -> { } );
    put("f64s.max"                       , (dfa, cl) -> { } );
    put("f64s.epsilon"                   , (dfa, cl) -> { } );
    put("f32s.squareRoot"                , (dfa, cl) -> { } );
    put("f64s.squareRoot"                , (dfa, cl) -> { } );
    put("f32s.exp"                       , (dfa, cl) -> { } );
    put("f64s.exp"                       , (dfa, cl) -> { } );
    put("f32s.log"                       , (dfa, cl) -> { } );
    put("f64s.log"                       , (dfa, cl) -> { } );
    put("f32s.sin"                       , (dfa, cl) -> { } );
    put("f64s.sin"                       , (dfa, cl) -> { } );
    put("f32s.cos"                       , (dfa, cl) -> { } );
    put("f64s.cos"                       , (dfa, cl) -> { } );
    put("f32s.tan"                       , (dfa, cl) -> { } );
    put("f64s.tan"                       , (dfa, cl) -> { } );
    put("f32s.asin"                      , (dfa, cl) -> { } );
    put("f64s.asin"                      , (dfa, cl) -> { } );
    put("f32s.acos"                      , (dfa, cl) -> { } );
    put("f64s.acos"                      , (dfa, cl) -> { } );
    put("f32s.atan"                      , (dfa, cl) -> { } );
    put("f64s.atan"                      , (dfa, cl) -> { } );
    put("f32s.atan2"                     , (dfa, cl) -> { } );
    put("f64s.atan2"                     , (dfa, cl) -> { } );
    put("f32s.sinh"                      , (dfa, cl) -> { } );
    put("f64s.sinh"                      , (dfa, cl) -> { } );
    put("f32s.cosh"                      , (dfa, cl) -> { } );
    put("f64s.cosh"                      , (dfa, cl) -> { } );
    put("f32s.tanh"                      , (dfa, cl) -> { } );
    put("f64s.tanh"                      , (dfa, cl) -> { } );

    put("Object.hashCode"                , (dfa, cl) -> { } );
    put("Object.asString"                , (dfa, cl) -> { } );
    put("fuzion.sys.array.alloc"         , (dfa, cl) -> { } );
    put("fuzion.sys.array.setel"         , (dfa, cl) -> { } );
    put("fuzion.sys.array.get"           , (dfa, cl) -> { } );
    put("fuzion.sys.env_vars.has0"       , (dfa, cl) -> { } );
    put("fuzion.sys.env_vars.get0"       , (dfa, cl) -> { } );
    put("fuzion.sys.thread.spawn0"       , (dfa, cl) -> { } );
    put("fuzion.std.nano_sleep"          , (dfa, cl) -> { } );
    put("fuzion.std.nano_time"           , (dfa, cl) -> { } );

    put("effect.replace"                 , (dfa, cl) -> { } );
    put("effect.default"                 , (dfa, cl) -> { } );
    put("effect.abortable"               , (dfa, cl) ->
        {
          var oc = dfa._fuir.clazzActualGeneric(cl, 0);
          var call = dfa._fuir.lookupCall(oc);
          System.err.println("**** DFA handling for effect.abortable missing");
        });
    put("effect.abort"                   , (dfa, cl) -> { } );
    put("effects.exists"                 , (dfa, cl) -> { } );
    put("fuzion.java.JavaObject.isNull"  , (dfa, cl) -> { } );
    put("fuzion.java.arrayGet"           , (dfa, cl) -> { } );
    put("fuzion.java.arrayLength"        , (dfa, cl) -> { } );
    put("fuzion.java.arrayToJavaObject0" , (dfa, cl) -> { } );
    put("fuzion.java.boolToJavaObject"   , (dfa, cl) -> { } );
    put("fuzion.java.callC0"             , (dfa, cl) -> { } );
    put("fuzion.java.callS0"             , (dfa, cl) -> { } );
    put("fuzion.java.callV0"             , (dfa, cl) -> { } );
    put("fuzion.java.f32ToJavaObject"    , (dfa, cl) -> { } );
    put("fuzion.java.f64ToJavaObject"    , (dfa, cl) -> { } );
    put("fuzion.java.getField0"          , (dfa, cl) -> { } );
    put("fuzion.java.getStaticField0"    , (dfa, cl) -> { } );
    put("fuzion.java.i16ToJavaObject"    , (dfa, cl) -> { } );
    put("fuzion.java.i32ToJavaObject"    , (dfa, cl) -> { } );
    put("fuzion.java.i64ToJavaObject"    , (dfa, cl) -> { } );
    put("fuzion.java.i8ToJavaObject"     , (dfa, cl) -> { } );
    put("fuzion.java.javaStringToString" , (dfa, cl) -> { } );
    put("fuzion.java.stringToJavaObject0", (dfa, cl) -> { } );
    put("fuzion.java.u16ToJavaObject"    , (dfa, cl) -> { } );
  }


   /**
    * Create instance of given clazz.
    */
   Instance newInstance(int cl)
   {
    var r = new Instance(this, cl);
    var e = _instances.get(r);
    if (e == null)
      {
        _instances.put(r, r);
        e = r;
        if (SHOW_STACK_ON_CHANGE && !_changed) Thread.dumpStack();
        _changed = true;
      }
    return e;
  }


  /**
   * Create call to given clazz with given target and args.
   */
  Call newCall(int cl, boolean pre, Value tvalue, List<Value> args)
  {
    // NYI: tvalue ignored!
    var r = new Call(this, cl, pre, args);
    var e = _calls.get(r);
    if (e == null)
      {
        _calls.put(r,r);
        e = r;
        if (SHOW_STACK_ON_CHANGE && !_changed) { System.out.println("new call: "+r); Thread.dumpStack();}
        _changed = true;
      }
    return e;
  }


}

/* end of file */
