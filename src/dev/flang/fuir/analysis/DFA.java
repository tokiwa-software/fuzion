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

import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.Graph;
import dev.flang.util.MapToN;


/**
 * DFA creates a data flow analysis based on the FUIR representation of a Fuzion
 * application.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class DFA extends ANY
{


  /*----------------------------  interfaces  ---------------------------*/


  interface IntrinsicDFA
  {
    void analyze(DFA dfa, int cl);
  }


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  static TreeMap<String, IntrinsicDFA> _intrinsics_ = new TreeMap<>();


  /*-------------------------  static methods  --------------------------*/


  private static void put(String n, IntrinsicDFA c) { _intrinsics_.put(n, c); }


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
  TreeSet<Instance> _instances = new TreeSet<>();



  /**
   * Flag to detect changes during current iteration of the fix-point algorithm.
   * If this remains false during one iteration we have reached a fix-point.
   */
  boolean _changed = false;


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
    _instances.add(new Instance(_fuir.mainClazzId()));
    findFixPoint();
    Errors.showAndExit();
  }


  /**
   * Iteratively perform data flow analysis until a fix point is reached.
   */
  void findFixPoint()
  {
    do
      {
        _changed = false;
        iteration();
      }
    while (_changed);
  }


  /**
   * Perform one iteration of the analysis.
   */
  void iteration()
  {
    var s = _instances.toArray(new Instance[_instances.size()]);
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
        analyzeBlock(cl, _fuir.clazzCode(cl));
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
   * Analyze code block c of clazz cl.
   *
   * @param cl clazz id
   *
   * @param c the code block to analyze.
   */
  void analyzeBlock(int cl, int c)
  {
    for (int i = 0; /* NYI: !containsVoid(stack) &&*/ _fuir.withinCode(c, i); i = i + _fuir.codeSizeAt(c, i))
      {
        var s = _fuir.codeAt(c, i);
        analyzeStmnt(cl, c, i, s);
      }
  }


  /**
   * Analyze statemnt s at index i in code block c of clazz cl.
   *
   * @param cl clazz id
   *
   * @param c the code block to analyze
   *
   * @param i the index within c
   *
   * @param s the FUIR.ExprKind of the statement to analyze
   */
  void analyzeStmnt(int cl, int c, int i, FUIR.ExprKind s)
  {
    switch (s)
      {
      case AdrOf : break;
      case Assign: break;
      case Box   : break;
      case Unbox : break;
      case Call:
        {
          var cc0 = _fuir.accessedClazz  (cl, c, i);
          if (_fuir.clazzContract(cc0, FUIR.ContractKind.Pre, 0) != -1)
            {
             call(cl, cc0, true);
            }
          if (!_fuir.callPreconditionOnly(cl, c, i))
            {
              access(cl, c, i);
            }
          break;
        }
      case Comment: break;
      case Current: break;
      case Const  : break;
      case Match  :
        {
          for (var mc = 0; mc < _fuir.matchCaseCount(c, i); mc++)
            {
              analyzeBlock(cl, _fuir.matchCaseCode(c, i, mc));
            }
          break;
        }
      case Tag: break;
      case Env:
        {
          var ecl = _fuir.envClazz(cl, c, i);
          addEffect(cl, ecl);
          break;
        }
      case Dup: break;
      case Pop: break;
      default:
        {
          Errors.fatal("Effects backend does not handle statments of type " + s);
        }
      }
  }


  /**
   * Create call graph for access (call or write) of a feature.
   *
   * @param cl clazz id
   *
   * @param c the code block to compile
   *
   * @param i index of the access statement, must be ExprKind.Assign or ExprKind.Call
   */
  void access(int cl, int c, int i)
  {
    var cc0 = _fuir.accessedClazz  (cl, c, i);

    if (_fuir.accessIsDynamic(cl, c, i))
      {
        var ccs = _fuir.accessedClazzes(cl, c, i);
        for (var cci = 0; cci < ccs.length; cci += 2)
          {
            var tt = ccs[cci  ];
            var cc = ccs[cci+1];
            call(cl, cc, false);
          }
      }
    else if (_fuir.clazzNeedsCode(cc0))
      {
        call(cl, cc0, false);
      }
  }


  /**
   * Add call
   *
   * @param cl clazz id of the call
   *
   * @param cc clazz that is called
   *
   * @param pre true to call the precondition of cl instead of cl.
   */
  void call(int cl, int cc, boolean pre)
  {
    // NYI: DFA
  }



  /**
   * Add connection from cl to ecl in _effects
   *
   * @param cl a clazz
   *
   * @param ecl an effect that is required by cl
   */
  public void addEffect(int cl, int ecl)
  {
    // NYI: DFA
  }


}

/* end of file */
