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
 * Source of class CFG
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.cfg;

import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.Graph;


/**
 * CFG creates a control-flow-graph based on the FUIR representation of a
 * Fuzion application.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class CFG extends ANY
{


  /*----------------------------  interfaces  ---------------------------*/


  interface Intrinsic
  {
    void createCallGraph(CFG cfg, int cl);
  }


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  static TreeMap<String, Intrinsic> _intrinsics_ = new TreeMap<>();


  /*-------------------------  static methods  --------------------------*/


  private static void put(String n, Intrinsic c) { _intrinsics_.put(n, c); }


  /**
   * Get the names of all intrinsics supported by this backend.
   */
  public static Set<String> supportedIntrinsics()
  {
    return _intrinsics_.keySet();
  }


  /*----------------------------  constants  ----------------------------*/



  /*----------------------------  variables  ----------------------------*/


  /**
   * The intermediate code we are analysing.
   */
  public final FUIR _fuir;


  /**
   * Map from clazz cl to set of clazzes that are called by cl
   */
  public Graph<Integer> _callGraph = new Graph<>();


  /**
   * All clazzes that are called
   */
  TreeSet<Integer> _calledClazzes = new TreeSet<>();


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create CFG for given intermediate code.
   *
   * @param fuir the intermediate code.
   */
  public CFG(FUIR fuir)
  {
    _fuir = fuir;
    Errors.showAndExit();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create call graph
   */
  public void createCallGraph()
  {
    var cl = _fuir.mainClazzId();
    createCallGraph(cl);
    Errors.showAndExit();
  }


  /**
   * Create call graph for given clazz cl.
   *
   * @param cl id of clazz to compile
   */
  void createCallGraph(int cl)
  {
    if (_fuir.clazzNeedsCode(cl))
      {
        var ck = _fuir.clazzKind(cl);
        switch (ck)
          {
          case Routine  : createCallGraphForRoutine(cl, false); break;
          case Intrinsic: createCallGraphForIntrinsic(cl); break;
          }
        if (_fuir.hasPrecondition(cl))
          {
            createCallGraphForRoutine(cl, true);
          }
      }
  }


  /**
   * Create call graph for given routine cl
   *
   * @param cl id of clazz to create call graph for
   *
   * @param pre true to creating call graph for cl's precondition, false for cl
   * itself.
   */
  void createCallGraphForRoutine(int cl, boolean pre)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzKind(cl) == FUIR.FeatureKind.Routine || pre);

    if (pre)
      {
        // NYI: preOrPostCondition(cl, FUIR.ContractKind.Pre);
      }
    else
      {
        createCallGraphForBlock(cl, _fuir.clazzCode(cl));
      }
  }


  /**
   * Create call graph for given intrinsic clazz cl.
   *
   * @param cl id of clazz to create call graph for
   *
   */
  void createCallGraphForIntrinsic(int cl)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzKind(cl) == FUIR.FeatureKind.Intrinsic);
    var in = _fuir.clazzIntrinsicName(cl);
    var c = _intrinsics_.get(in);
    if (c != null)
      {
        c.createCallGraph(this, cl);
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
    put("Type.name"                      , (cfg, cl) -> { } );
    put("safety"                         , (cfg, cl) -> { } );
    put("debug"                          , (cfg, cl) -> { } );
    put("debugLevel"                     , (cfg, cl) -> { } );
    put("fuzion.sys.args.count"          , (cfg, cl) -> { } );
    put("fuzion.sys.args.get"            , (cfg, cl) -> { } );
    put("fuzion.std.exit"                , (cfg, cl) -> { } );
    put("fuzion.sys.out.write"           , (cfg, cl) -> { } );
    put("fuzion.sys.err.write"           , (cfg, cl) -> { } );
    put("fuzion.sys.fileio.read"         , (cfg, cl) -> { } );
    put("fuzion.sys.fileio.get_file_size", (cfg, cl) -> { } );
    put("fuzion.sys.fileio.write"        , (cfg, cl) -> { } );
    put("fuzion.sys.fileio.delete"       , (cfg, cl) -> { } );
    put("fuzion.sys.fileio.move"         , (cfg, cl) -> { } );
    put("fuzion.sys.fileio.create_dir"   , (cfg, cl) -> { } );
    put("fuzion.sys.fileio.stats"        , (cfg, cl) -> { } );
    put("fuzion.sys.fileio.lstats"       , (cfg, cl) -> { } );
    put("fuzion.sys.fileio.open"         , (cfg, cl) -> { } );
    put("fuzion.sys.fileio.close"        , (cfg, cl) -> { } );
    put("fuzion.sys.fileio.seek"         , (cfg, cl) -> { } );
    put("fuzion.sys.fileio.file_position", (cfg, cl) -> { } );
    put("fuzion.sys.out.flush"           , (cfg, cl) -> { } );
    put("fuzion.sys.err.flush"           , (cfg, cl) -> { } );
    put("fuzion.sys.stdin.next_byte"     , (cfg, cl) -> { } );
    put("i8.prefix -°"                   , (cfg, cl) -> { } );
    put("i16.prefix -°"                  , (cfg, cl) -> { } );
    put("i32.prefix -°"                  , (cfg, cl) -> { } );
    put("i64.prefix -°"                  , (cfg, cl) -> { } );
    put("i8.infix -°"                    , (cfg, cl) -> { } );
    put("i16.infix -°"                   , (cfg, cl) -> { } );
    put("i32.infix -°"                   , (cfg, cl) -> { } );
    put("i64.infix -°"                   , (cfg, cl) -> { } );
    put("i8.infix +°"                    , (cfg, cl) -> { } );
    put("i16.infix +°"                   , (cfg, cl) -> { } );
    put("i32.infix +°"                   , (cfg, cl) -> { } );
    put("i64.infix +°"                   , (cfg, cl) -> { } );
    put("i8.infix *°"                    , (cfg, cl) -> { } );
    put("i16.infix *°"                   , (cfg, cl) -> { } );
    put("i32.infix *°"                   , (cfg, cl) -> { } );
    put("i64.infix *°"                   , (cfg, cl) -> { } );
    put("i8.div"                         , (cfg, cl) -> { } );
    put("i16.div"                        , (cfg, cl) -> { } );
    put("i32.div"                        , (cfg, cl) -> { } );
    put("i64.div"                        , (cfg, cl) -> { } );
    put("i8.mod"                         , (cfg, cl) -> { } );
    put("i16.mod"                        , (cfg, cl) -> { } );
    put("i32.mod"                        , (cfg, cl) -> { } );
    put("i64.mod"                        , (cfg, cl) -> { } );
    put("i8.infix <<"                    , (cfg, cl) -> { } );
    put("i16.infix <<"                   , (cfg, cl) -> { } );
    put("i32.infix <<"                   , (cfg, cl) -> { } );
    put("i64.infix <<"                   , (cfg, cl) -> { } );
    put("i8.infix >>"                    , (cfg, cl) -> { } );
    put("i16.infix >>"                   , (cfg, cl) -> { } );
    put("i32.infix >>"                   , (cfg, cl) -> { } );
    put("i64.infix >>"                   , (cfg, cl) -> { } );
    put("i8.infix &"                     , (cfg, cl) -> { } );
    put("i16.infix &"                    , (cfg, cl) -> { } );
    put("i32.infix &"                    , (cfg, cl) -> { } );
    put("i64.infix &"                    , (cfg, cl) -> { } );
    put("i8.infix |"                     , (cfg, cl) -> { } );
    put("i16.infix |"                    , (cfg, cl) -> { } );
    put("i32.infix |"                    , (cfg, cl) -> { } );
    put("i64.infix |"                    , (cfg, cl) -> { } );
    put("i8.infix ^"                     , (cfg, cl) -> { } );
    put("i16.infix ^"                    , (cfg, cl) -> { } );
    put("i32.infix ^"                    , (cfg, cl) -> { } );
    put("i64.infix ^"                    , (cfg, cl) -> { } );

    put("i8.infix =="                    , (cfg, cl) -> { } );
    put("i16.infix =="                   , (cfg, cl) -> { } );
    put("i32.infix =="                   , (cfg, cl) -> { } );
    put("i64.infix =="                   , (cfg, cl) -> { } );
    put("i8.type.equality"               , (cfg, cl) -> { } );
    put("i16.type.equality"              , (cfg, cl) -> { } );
    put("i32.type.equality"              , (cfg, cl) -> { } );
    put("i64.type.equality"              , (cfg, cl) -> { } );
    put("i8.infix !="                    , (cfg, cl) -> { } );
    put("i16.infix !="                   , (cfg, cl) -> { } );
    put("i32.infix !="                   , (cfg, cl) -> { } );
    put("i64.infix !="                   , (cfg, cl) -> { } );
    put("i8.infix >"                     , (cfg, cl) -> { } );
    put("i16.infix >"                    , (cfg, cl) -> { } );
    put("i32.infix >"                    , (cfg, cl) -> { } );
    put("i64.infix >"                    , (cfg, cl) -> { } );
    put("i8.infix >="                    , (cfg, cl) -> { } );
    put("i16.infix >="                   , (cfg, cl) -> { } );
    put("i32.infix >="                   , (cfg, cl) -> { } );
    put("i64.infix >="                   , (cfg, cl) -> { } );
    put("i8.infix <"                     , (cfg, cl) -> { } );
    put("i16.infix <"                    , (cfg, cl) -> { } );
    put("i32.infix <"                    , (cfg, cl) -> { } );
    put("i64.infix <"                    , (cfg, cl) -> { } );
    put("i8.infix <="                    , (cfg, cl) -> { } );
    put("i16.infix <="                   , (cfg, cl) -> { } );
    put("i32.infix <="                   , (cfg, cl) -> { } );
    put("i64.infix <="                   , (cfg, cl) -> { } );

    put("u8.prefix -°"                   , (cfg, cl) -> { } );
    put("u16.prefix -°"                  , (cfg, cl) -> { } );
    put("u32.prefix -°"                  , (cfg, cl) -> { } );
    put("u64.prefix -°"                  , (cfg, cl) -> { } );
    put("u8.infix -°"                    , (cfg, cl) -> { } );
    put("u16.infix -°"                   , (cfg, cl) -> { } );
    put("u32.infix -°"                   , (cfg, cl) -> { } );
    put("u64.infix -°"                   , (cfg, cl) -> { } );
    put("u8.infix +°"                    , (cfg, cl) -> { } );
    put("u16.infix +°"                   , (cfg, cl) -> { } );
    put("u32.infix +°"                   , (cfg, cl) -> { } );
    put("u64.infix +°"                   , (cfg, cl) -> { } );
    put("u8.infix *°"                    , (cfg, cl) -> { } );
    put("u16.infix *°"                   , (cfg, cl) -> { } );
    put("u32.infix *°"                   , (cfg, cl) -> { } );
    put("u64.infix *°"                   , (cfg, cl) -> { } );
    put("u8.div"                         , (cfg, cl) -> { } );
    put("u16.div"                        , (cfg, cl) -> { } );
    put("u32.div"                        , (cfg, cl) -> { } );
    put("u64.div"                        , (cfg, cl) -> { } );
    put("u8.mod"                         , (cfg, cl) -> { } );
    put("u16.mod"                        , (cfg, cl) -> { } );
    put("u32.mod"                        , (cfg, cl) -> { } );
    put("u64.mod"                        , (cfg, cl) -> { } );
    put("u8.infix <<"                    , (cfg, cl) -> { } );
    put("u16.infix <<"                   , (cfg, cl) -> { } );
    put("u32.infix <<"                   , (cfg, cl) -> { } );
    put("u64.infix <<"                   , (cfg, cl) -> { } );
    put("u8.infix >>"                    , (cfg, cl) -> { } );
    put("u16.infix >>"                   , (cfg, cl) -> { } );
    put("u32.infix >>"                   , (cfg, cl) -> { } );
    put("u64.infix >>"                   , (cfg, cl) -> { } );
    put("u8.infix &"                     , (cfg, cl) -> { } );
    put("u16.infix &"                    , (cfg, cl) -> { } );
    put("u32.infix &"                    , (cfg, cl) -> { } );
    put("u64.infix &"                    , (cfg, cl) -> { } );
    put("u8.infix |"                     , (cfg, cl) -> { } );
    put("u16.infix |"                    , (cfg, cl) -> { } );
    put("u32.infix |"                    , (cfg, cl) -> { } );
    put("u64.infix |"                    , (cfg, cl) -> { } );
    put("u8.infix ^"                     , (cfg, cl) -> { } );
    put("u16.infix ^"                    , (cfg, cl) -> { } );
    put("u32.infix ^"                    , (cfg, cl) -> { } );
    put("u64.infix ^"                    , (cfg, cl) -> { } );

    put("u8.infix =="                    , (cfg, cl) -> { } );
    put("u16.infix =="                   , (cfg, cl) -> { } );
    put("u32.infix =="                   , (cfg, cl) -> { } );
    put("u64.infix =="                   , (cfg, cl) -> { } );
    put("u8.type.equality"               , (cfg, cl) -> { } );
    put("u16.type.equality"              , (cfg, cl) -> { } );
    put("u32.type.equality"              , (cfg, cl) -> { } );
    put("u64.type.equality"              , (cfg, cl) -> { } );
    put("u8.infix !="                    , (cfg, cl) -> { } );
    put("u16.infix !="                   , (cfg, cl) -> { } );
    put("u32.infix !="                   , (cfg, cl) -> { } );
    put("u64.infix !="                   , (cfg, cl) -> { } );
    put("u8.infix >"                     , (cfg, cl) -> { } );
    put("u16.infix >"                    , (cfg, cl) -> { } );
    put("u32.infix >"                    , (cfg, cl) -> { } );
    put("u64.infix >"                    , (cfg, cl) -> { } );
    put("u8.infix >="                    , (cfg, cl) -> { } );
    put("u16.infix >="                   , (cfg, cl) -> { } );
    put("u32.infix >="                   , (cfg, cl) -> { } );
    put("u64.infix >="                   , (cfg, cl) -> { } );
    put("u8.infix <"                     , (cfg, cl) -> { } );
    put("u16.infix <"                    , (cfg, cl) -> { } );
    put("u32.infix <"                    , (cfg, cl) -> { } );
    put("u64.infix <"                    , (cfg, cl) -> { } );
    put("u8.infix <="                    , (cfg, cl) -> { } );
    put("u16.infix <="                   , (cfg, cl) -> { } );
    put("u32.infix <="                   , (cfg, cl) -> { } );
    put("u64.infix <="                   , (cfg, cl) -> { } );

    put("i8.as_i32"                      , (cfg, cl) -> { } );
    put("i16.as_i32"                     , (cfg, cl) -> { } );
    put("i32.as_i64"                     , (cfg, cl) -> { } );
    put("i32.as_f64"                     , (cfg, cl) -> { } );
    put("i64.as_f64"                     , (cfg, cl) -> { } );
    put("u8.as_i32"                      , (cfg, cl) -> { } );
    put("u16.as_i32"                     , (cfg, cl) -> { } );
    put("u32.as_i64"                     , (cfg, cl) -> { } );
    put("u32.as_f64"                     , (cfg, cl) -> { } );
    put("u64.as_f64"                     , (cfg, cl) -> { } );
    put("i8.castTo_u8"                   , (cfg, cl) -> { } );
    put("i16.castTo_u16"                 , (cfg, cl) -> { } );
    put("i32.castTo_u32"                 , (cfg, cl) -> { } );
    put("i64.castTo_u64"                 , (cfg, cl) -> { } );
    put("u8.castTo_i8"                   , (cfg, cl) -> { } );
    put("u16.castTo_i16"                 , (cfg, cl) -> { } );
    put("u32.castTo_i32"                 , (cfg, cl) -> { } );
    put("u32.castTo_f32"                 , (cfg, cl) -> { } );
    put("u64.castTo_i64"                 , (cfg, cl) -> { } );
    put("u64.castTo_f64"                 , (cfg, cl) -> { } );
    put("u16.low8bits"                   , (cfg, cl) -> { } );
    put("u32.low8bits"                   , (cfg, cl) -> { } );
    put("u64.low8bits"                   , (cfg, cl) -> { } );
    put("u32.low16bits"                  , (cfg, cl) -> { } );
    put("u64.low16bits"                  , (cfg, cl) -> { } );
    put("u64.low32bits"                  , (cfg, cl) -> { } );

    put("f32.prefix -"                   , (cfg, cl) -> { } );
    put("f64.prefix -"                   , (cfg, cl) -> { } );
    put("f32.infix +"                    , (cfg, cl) -> { } );
    put("f64.infix +"                    , (cfg, cl) -> { } );
    put("f32.infix -"                    , (cfg, cl) -> { } );
    put("f64.infix -"                    , (cfg, cl) -> { } );
    put("f32.infix *"                    , (cfg, cl) -> { } );
    put("f64.infix *"                    , (cfg, cl) -> { } );
    put("f32.infix /"                    , (cfg, cl) -> { } );
    put("f64.infix /"                    , (cfg, cl) -> { } );
    put("f32.infix %"                    , (cfg, cl) -> { } );
    put("f64.infix %"                    , (cfg, cl) -> { } );
    put("f32.infix **"                   , (cfg, cl) -> { } );
    put("f64.infix **"                   , (cfg, cl) -> { } );
    put("f32.infix =="                   , (cfg, cl) -> { } );
    put("f64.infix =="                   , (cfg, cl) -> { } );
    put("f32.type.equality"              , (cfg, cl) -> { } );
    put("f64.type.equality"              , (cfg, cl) -> { } );
    put("f32.infix !="                   , (cfg, cl) -> { } );
    put("f64.infix !="                   , (cfg, cl) -> { } );
    put("f32.infix <"                    , (cfg, cl) -> { } );
    put("f64.infix <"                    , (cfg, cl) -> { } );
    put("f32.infix <="                   , (cfg, cl) -> { } );
    put("f64.infix <="                   , (cfg, cl) -> { } );
    put("f32.infix >"                    , (cfg, cl) -> { } );
    put("f64.infix >"                    , (cfg, cl) -> { } );
    put("f32.infix >="                   , (cfg, cl) -> { } );
    put("f64.infix >="                   , (cfg, cl) -> { } );
    put("f32.as_f64"                     , (cfg, cl) -> { } );
    put("f64.as_f32"                     , (cfg, cl) -> { } );
    put("f64.as_i64_lax"                 , (cfg, cl) -> { } );
    put("f32.castTo_u32"                 , (cfg, cl) -> { } );
    put("f64.castTo_u64"                 , (cfg, cl) -> { } );
    put("f32.asString"                   , (cfg, cl) -> { } );
    put("f64.asString"                   , (cfg, cl) -> { } );

    put("f32s.minExp"                    , (cfg, cl) -> { } );
    put("f32s.maxExp"                    , (cfg, cl) -> { } );
    put("f32s.minPositive"               , (cfg, cl) -> { } );
    put("f32s.max"                       , (cfg, cl) -> { } );
    put("f32s.epsilon"                   , (cfg, cl) -> { } );
    put("f32s.isNaN"                     , (cfg, cl) -> { } );
    put("f64s.isNaN"                     , (cfg, cl) -> { } );
    put("f64s.minExp"                    , (cfg, cl) -> { } );
    put("f64s.maxExp"                    , (cfg, cl) -> { } );
    put("f64s.minPositive"               , (cfg, cl) -> { } );
    put("f64s.max"                       , (cfg, cl) -> { } );
    put("f64s.epsilon"                   , (cfg, cl) -> { } );
    put("f32s.squareRoot"                , (cfg, cl) -> { } );
    put("f64s.squareRoot"                , (cfg, cl) -> { } );
    put("f32s.exp"                       , (cfg, cl) -> { } );
    put("f64s.exp"                       , (cfg, cl) -> { } );
    put("f32s.log"                       , (cfg, cl) -> { } );
    put("f64s.log"                       , (cfg, cl) -> { } );
    put("f32s.sin"                       , (cfg, cl) -> { } );
    put("f64s.sin"                       , (cfg, cl) -> { } );
    put("f32s.cos"                       , (cfg, cl) -> { } );
    put("f64s.cos"                       , (cfg, cl) -> { } );
    put("f32s.tan"                       , (cfg, cl) -> { } );
    put("f64s.tan"                       , (cfg, cl) -> { } );
    put("f32s.asin"                      , (cfg, cl) -> { } );
    put("f64s.asin"                      , (cfg, cl) -> { } );
    put("f32s.acos"                      , (cfg, cl) -> { } );
    put("f64s.acos"                      , (cfg, cl) -> { } );
    put("f32s.atan"                      , (cfg, cl) -> { } );
    put("f64s.atan"                      , (cfg, cl) -> { } );
    put("f32s.atan2"                     , (cfg, cl) -> { } );
    put("f64s.atan2"                     , (cfg, cl) -> { } );
    put("f32s.sinh"                      , (cfg, cl) -> { } );
    put("f64s.sinh"                      , (cfg, cl) -> { } );
    put("f32s.cosh"                      , (cfg, cl) -> { } );
    put("f64s.cosh"                      , (cfg, cl) -> { } );
    put("f32s.tanh"                      , (cfg, cl) -> { } );
    put("f64s.tanh"                      , (cfg, cl) -> { } );

    put("Any.hashCode"                   , (cfg, cl) -> { } );
    put("Any.asString"                   , (cfg, cl) -> { } );
    put("fuzion.sys.internal_array.alloc", (cfg, cl) -> { } );
    put("fuzion.sys.internal_array.setel", (cfg, cl) -> { } );
    put("fuzion.sys.internal_array.get"  , (cfg, cl) -> { } );
    put("fuzion.sys.env_vars.has0"       , (cfg, cl) -> { } );
    put("fuzion.sys.env_vars.get0"       , (cfg, cl) -> { } );
    put("fuzion.sys.env_vars.set0"       , (cfg, cl) -> { } );
    put("fuzion.sys.env_vars.unset0"     , (cfg, cl) -> { } );
    put("fuzion.sys.thread.spawn0"       , (cfg, cl) -> { } );
    put("fuzion.std.nano_sleep"          , (cfg, cl) -> { } );
    put("fuzion.std.nano_time"           , (cfg, cl) -> { } );

    put("effect.replace"                 , (cfg, cl) -> { } );
    put("effect.default"                 , (cfg, cl) -> { } );
    put("effect.abortable"               , (cfg, cl) ->
        {
          var oc = cfg._fuir.clazzActualGeneric(cl, 0);
          var call = cfg._fuir.lookupCall(oc);
          if (cfg._fuir.clazzNeedsCode(call))
            {
              cfg.addToCallGraph(cl, call, false);
            }
        });
    put("effect.abort"                   , (cfg, cl) -> { } );
    put("effects.exists"                 , (cfg, cl) -> { } );
    put("fuzion.java.JavaObject.isNull"  , (cfg, cl) -> { } );
    put("fuzion.java.arrayGet"           , (cfg, cl) -> { } );
    put("fuzion.java.arrayLength"        , (cfg, cl) -> { } );
    put("fuzion.java.arrayToJavaObject0" , (cfg, cl) -> { } );
    put("fuzion.java.boolToJavaObject"   , (cfg, cl) -> { } );
    put("fuzion.java.callC0"             , (cfg, cl) -> { } );
    put("fuzion.java.callS0"             , (cfg, cl) -> { } );
    put("fuzion.java.callV0"             , (cfg, cl) -> { } );
    put("fuzion.java.f32ToJavaObject"    , (cfg, cl) -> { } );
    put("fuzion.java.f64ToJavaObject"    , (cfg, cl) -> { } );
    put("fuzion.java.getField0"          , (cfg, cl) -> { } );
    put("fuzion.java.getStaticField0"    , (cfg, cl) -> { } );
    put("fuzion.java.i16ToJavaObject"    , (cfg, cl) -> { } );
    put("fuzion.java.i32ToJavaObject"    , (cfg, cl) -> { } );
    put("fuzion.java.i64ToJavaObject"    , (cfg, cl) -> { } );
    put("fuzion.java.i8ToJavaObject"     , (cfg, cl) -> { } );
    put("fuzion.java.javaStringToString" , (cfg, cl) -> { } );
    put("fuzion.java.stringToJavaObject0", (cfg, cl) -> { } );
    put("fuzion.java.u16ToJavaObject"    , (cfg, cl) -> { } );
  }


  /**
   * Create call graph for calls in code block c of clazz cl.
   *
   * @param cl clazz id
   *
   * @param c the code block to analyze.
   */
  void createCallGraphForBlock(int cl, int c)
  {
    for (int i = 0; /* NYI: !containsVoid(stack) &&*/ _fuir.withinCode(c, i); i = i + _fuir.codeSizeAt(c, i))
      {
        var s = _fuir.codeAt(c, i);
        createCallGraphForStmnt(cl, c, i, s);
      }
  }


  /**
   * Create call graph for calls made by statemnt s at index i in code block c
   * of clazz cl.
   *
   * @param cl clazz id
   *
   * @param c the code block to analyze
   *
   * @param i the index within c
   *
   * @param s the FUIR.ExprKind of the statement to analyze
   */
  void createCallGraphForStmnt(int cl, int c, int i, FUIR.ExprKind s)
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
          if (_fuir.hasPrecondition(cc0))
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
              createCallGraphForBlock(cl, _fuir.matchCaseCode(c, i, mc));
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
   * Create call graph for call to a feature
   *
   * @param cl clazz id of the call
   *
   * @param cc clazz that is called
   *
   * @param pre true to call the precondition of cl instead of cl.
   */
  void call(int cl, int cc, boolean pre)
  {
    if (_fuir.clazzNeedsCode(cc))
      {
        addToCallGraph(cl, cc, pre);
      }
  }


  /**
   * Add edge from cl to cc to call graph
   *
   * @param cl the caller clazz
   *
   * @param cc the callee clazz
   *
   * @param pre true iff cc's precondition is called, not cc itself.
   */
  void addToCallGraph(int cl, int cc, boolean pre)
  {
    if (pre)
      {
        // NYI:
      }
    else
      {
        if (!_callGraph.contains(cl, cc))
          {
            _callGraph.put(cl, cc);

            if (!_calledClazzes.contains(cc))
              {
                _calledClazzes.add(cc);
                createCallGraph(cc);
              }
          }
      }
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
  }


}

/* end of file */
