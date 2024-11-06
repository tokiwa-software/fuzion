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
import dev.flang.util.List;


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
   * The intermediate code we are analyzing.
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


  /**
   * Newly found classes for which createCallGraph still must be called.
   */
  List<Integer> _newCalledClazzesToBeProcessed = new List<>();


  /*---------------------------  constructors  ---------------------------*/


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
    _newCalledClazzesToBeProcessed.add(cl);
    while (_newCalledClazzesToBeProcessed.size() > 0)
      {
        var ncl = _newCalledClazzesToBeProcessed.removeLast();
        createCallGraph(ncl);
      }
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
          case Routine  : createCallGraphForRoutine(cl); break;
          case Intrinsic: createCallGraphForIntrinsic(cl); break;
          }
      }
  }


  /**
   * Create call graph for given routine cl
   *
   * @param cl id of clazz to create call graph for
   */
  void createCallGraphForRoutine(int cl)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzKind(cl) == FUIR.FeatureKind.Routine);

    createCallGraphForBlock(cl, _fuir.clazzCode(cl));
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
    var in = _fuir.clazzOriginalName(cl);
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
            var msg = "code for intrinsic " + _fuir.clazzOriginalName(cl) + " is missing";
            Errors.warning(msg);
          }
      }
  }


  static
  {
    put("Type.name"                      , (cfg, cl) -> { } );

    put("concur.atomic.compare_and_swap0", (cfg, cl) -> { } );
    put("concur.atomic.compare_and_set0",  (cfg, cl) -> { } );
    put("concur.atomic.racy_accesses_supported", (cfg, cl) -> { } );
    put("concur.atomic.read0"            , (cfg, cl) -> { } );
    put("concur.atomic.write0"           , (cfg, cl) -> { } );
    put("concur.util.loadFence"          , (cfg, cl) -> { } );
    put("concur.util.storeFence"         , (cfg, cl) -> { } );

    put("safety"                         , (cfg, cl) -> { } );
    put("debug"                          , (cfg, cl) -> { } );
    put("debug_level"                    , (cfg, cl) -> { } );
    put("fuzion.sys.args.count"          , (cfg, cl) -> { } );
    put("fuzion.sys.args.get"            , (cfg, cl) -> { } );
    put("fuzion.std.exit"                , (cfg, cl) -> { } );
    put("fuzion.sys.fileio.read"         , (cfg, cl) -> { } );
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
    put("fuzion.sys.fileio.mmap"         , (cfg, cl) -> { } );
    put("fuzion.sys.fileio.munmap"       , (cfg, cl) -> { } );
    put("fuzion.sys.fileio.mapped_buffer_get", (cfg, cl) -> { } );
    put("fuzion.sys.fileio.mapped_buffer_set", (cfg, cl) -> { } );
    put("fuzion.sys.fileio.flush"        , (cfg, cl) -> { } );
    put("fuzion.sys.fatal_fault0"        , (cfg, cl) -> { } );
    put("fuzion.sys.stdin.stdin0"        , (cfg, cl) -> { } );
    put("fuzion.sys.out.stdout"          , (cfg, cl) -> { } );
    put("fuzion.sys.err.stderr"          , (cfg, cl) -> { } );
    put("fuzion.sys.fileio.open_dir"     , (cfg, cl) -> { } );
    put("fuzion.sys.fileio.read_dir"     , (cfg, cl) -> { } );
    put("fuzion.sys.fileio.read_dir_has_next", (cfg, cl) -> { } );
    put("fuzion.sys.fileio.close_dir"    , (cfg, cl) -> { } );

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

    put("i8.type.equality"               , (cfg, cl) -> { } );
    put("i16.type.equality"              , (cfg, cl) -> { } );
    put("i32.type.equality"              , (cfg, cl) -> { } );
    put("i64.type.equality"              , (cfg, cl) -> { } );
    put("i8.type.lteq"                   , (cfg, cl) -> { } );
    put("i16.type.lteq"                  , (cfg, cl) -> { } );
    put("i32.type.lteq"                  , (cfg, cl) -> { } );
    put("i64.type.lteq"                  , (cfg, cl) -> { } );

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

    put("u8.type.equality"               , (cfg, cl) -> { } );
    put("u16.type.equality"              , (cfg, cl) -> { } );
    put("u32.type.equality"              , (cfg, cl) -> { } );
    put("u64.type.equality"              , (cfg, cl) -> { } );
    put("u8.type.lteq"                   , (cfg, cl) -> { } );
    put("u16.type.lteq"                  , (cfg, cl) -> { } );
    put("u32.type.lteq"                  , (cfg, cl) -> { } );
    put("u64.type.lteq"                  , (cfg, cl) -> { } );

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
    put("i8.cast_to_u8"                  , (cfg, cl) -> { } );
    put("i16.cast_to_u16"                , (cfg, cl) -> { } );
    put("i32.cast_to_u32"                , (cfg, cl) -> { } );
    put("i64.cast_to_u64"                , (cfg, cl) -> { } );
    put("u8.cast_to_i8"                  , (cfg, cl) -> { } );
    put("u16.cast_to_i16"                , (cfg, cl) -> { } );
    put("u32.cast_to_i32"                , (cfg, cl) -> { } );
    put("u32.cast_to_f32"                , (cfg, cl) -> { } );
    put("u64.cast_to_i64"                , (cfg, cl) -> { } );
    put("u64.cast_to_f64"                , (cfg, cl) -> { } );
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
    put("f32.infix ="                    , (cfg, cl) -> { } );
    put("f64.infix ="                    , (cfg, cl) -> { } );
    put("f32.infix <="                   , (cfg, cl) -> { } );
    put("f64.infix <="                   , (cfg, cl) -> { } );
    put("f32.infix >="                   , (cfg, cl) -> { } );
    put("f64.infix >="                   , (cfg, cl) -> { } );
    put("f32.infix <"                    , (cfg, cl) -> { } );
    put("f64.infix <"                    , (cfg, cl) -> { } );
    put("f32.infix >"                    , (cfg, cl) -> { } );
    put("f64.infix >"                    , (cfg, cl) -> { } );
    put("f32.as_f64"                     , (cfg, cl) -> { } );
    put("f64.as_f32"                     , (cfg, cl) -> { } );
    put("f64.as_i64_lax"                 , (cfg, cl) -> { } );
    put("f32.cast_to_u32"                , (cfg, cl) -> { } );
    put("f64.cast_to_u64"                , (cfg, cl) -> { } );
    put("f32.is_NaN"                     , (cfg, cl) -> { } );
    put("f64.is_NaN"                     , (cfg, cl) -> { } );
    put("f32.square_root"                , (cfg, cl) -> { } );
    put("f64.square_root"                , (cfg, cl) -> { } );
    put("f32.exp"                        , (cfg, cl) -> { } );
    put("f64.exp"                        , (cfg, cl) -> { } );
    put("f32.log"                        , (cfg, cl) -> { } );
    put("f64.log"                        , (cfg, cl) -> { } );
    put("f32.sin"                        , (cfg, cl) -> { } );
    put("f64.sin"                        , (cfg, cl) -> { } );
    put("f32.cos"                        , (cfg, cl) -> { } );
    put("f64.cos"                        , (cfg, cl) -> { } );
    put("f32.tan"                        , (cfg, cl) -> { } );
    put("f64.tan"                        , (cfg, cl) -> { } );
    put("f32.asin"                       , (cfg, cl) -> { } );
    put("f64.asin"                       , (cfg, cl) -> { } );
    put("f32.acos"                       , (cfg, cl) -> { } );
    put("f64.acos"                       , (cfg, cl) -> { } );
    put("f32.atan"                       , (cfg, cl) -> { } );
    put("f64.atan"                       , (cfg, cl) -> { } );
    put("f32.sinh"                       , (cfg, cl) -> { } );
    put("f64.sinh"                       , (cfg, cl) -> { } );
    put("f32.cosh"                       , (cfg, cl) -> { } );
    put("f64.cosh"                       , (cfg, cl) -> { } );
    put("f32.tanh"                       , (cfg, cl) -> { } );
    put("f64.tanh"                       , (cfg, cl) -> { } );

    put("f32.type.min_exp"               , (cfg, cl) -> { } );
    put("f32.type.max_exp"               , (cfg, cl) -> { } );
    put("f32.type.min_positive"          , (cfg, cl) -> { } );
    put("f32.type.max"                   , (cfg, cl) -> { } );
    put("f32.type.epsilon"               , (cfg, cl) -> { } );
    put("f64.type.min_exp"               , (cfg, cl) -> { } );
    put("f64.type.max_exp"               , (cfg, cl) -> { } );
    put("f64.type.min_positive"          , (cfg, cl) -> { } );
    put("f64.type.max"                   , (cfg, cl) -> { } );
    put("f64.type.epsilon"               , (cfg, cl) -> { } );

    put("fuzion.sys.internal_array_init.alloc", (cfg, cl) -> { } );
    put("fuzion.sys.internal_array.setel", (cfg, cl) -> { } );
    put("fuzion.sys.internal_array.get"  , (cfg, cl) -> { } );
    put("fuzion.sys.internal_array.freeze"
                                         , (cfg, cl) -> { } );
    put("fuzion.sys.internal_array.ensure_not_frozen"
                                         , (cfg, cl) -> { } );
    put("fuzion.sys.env_vars.has0"       , (cfg, cl) -> { } );
    put("fuzion.sys.env_vars.get0"       , (cfg, cl) -> { } );
    put("fuzion.sys.env_vars.set0"       , (cfg, cl) -> { } );
    put("fuzion.sys.env_vars.unset0"     , (cfg, cl) -> { } );
    put("fuzion.sys.misc.unique_id"      , (cfg, cl) -> { } );
    put("fuzion.sys.thread.spawn0"       , (cfg, cl) -> { } );
    put("fuzion.sys.thread.join0"        , (cfg, cl) -> { } );

    put("fuzion.sys.net.bind0"           , (cfg, cl) -> { } );
    put("fuzion.sys.net.listen"          , (cfg, cl) -> { } );
    put("fuzion.sys.net.accept"          , (cfg, cl) -> { } );
    put("fuzion.sys.net.connect0"        , (cfg, cl) -> { } );
    put("fuzion.sys.net.get_peer_address", (cfg, cl) -> { } );
    put("fuzion.sys.net.get_peer_port"   , (cfg, cl) -> { } );
    put("fuzion.sys.net.read"            , (cfg, cl) -> { } );
    put("fuzion.sys.net.write"           , (cfg, cl) -> { } );
    put("fuzion.sys.net.close0"          , (cfg, cl) -> { } );
    put("fuzion.sys.net.set_blocking0"   , (cfg, cl) -> { } );

    put("fuzion.sys.process.create"      , (cfg, cl) -> { } );
    put("fuzion.sys.process.wait"        , (cfg, cl) -> { } );
    put("fuzion.sys.pipe.read"           , (cfg, cl) -> { } );
    put("fuzion.sys.pipe.write"          , (cfg, cl) -> { } );
    put("fuzion.sys.pipe.close"          , (cfg, cl) -> { } );

    put("fuzion.std.nano_sleep"          , (cfg, cl) -> { } );
    put("fuzion.std.nano_time"           , (cfg, cl) -> { } );
    put("fuzion.std.date_time"           , (cfg, cl) -> { } );

    put("effect.type.default0"              , (cfg, cl) -> { } );
    put("effect.type.instate0"              , (cfg, cl) ->
        {
          var oc  = cfg._fuir.clazzActualGeneric(cl, 1);
          var call = cfg._fuir.lookupCall(oc);
          if (cfg._fuir.clazzNeedsCode(call))
            {
              cfg.addToCallGraph(cl, call);
            }
        });
    put("effect.type.replace0"              , (cfg, cl) -> { } );
    put("effect.type.abort0"                , (cfg, cl) -> { } );
    put("effect.type.is_instated0"          , (cfg, cl) -> { } );
    put("fuzion.java.Java_Object.is_null0"  , (cfg, cl) -> { } );
    put("fuzion.java.array_get"             , (cfg, cl) -> { } );
    put("fuzion.java.array_length"          , (cfg, cl) -> { } );
    put("fuzion.java.array_to_java_object0" , (cfg, cl) -> { } );
    put("fuzion.java.bool_to_java_object"   , (cfg, cl) -> { } );
    put("fuzion.java.call_c0"               , (cfg, cl) -> { } );
    put("fuzion.java.call_s0"               , (cfg, cl) -> { } );
    put("fuzion.java.call_v0"               , (cfg, cl) -> { } );
    put("fuzion.java.cast0"                 , (cfg, cl) -> { } );
    put("fuzion.java.f32_to_java_object"    , (cfg, cl) -> { } );
    put("fuzion.java.f64_to_java_object"    , (cfg, cl) -> { } );
    put("fuzion.java.get_field0"            , (cfg, cl) -> { } );
    put("fuzion.java.get_static_field0"     , (cfg, cl) -> { } );
    put("fuzion.java.set_field0"            , (cfg, cl) -> { } );
    put("fuzion.java.set_static_field0"     , (cfg, cl) -> { } );
    put("fuzion.java.i16_to_java_object"    , (cfg, cl) -> { } );
    put("fuzion.java.i32_to_java_object"    , (cfg, cl) -> { } );
    put("fuzion.java.i64_to_java_object"    , (cfg, cl) -> { } );
    put("fuzion.java.i8_to_java_object"     , (cfg, cl) -> { } );
    put("fuzion.java.java_string_to_string" , (cfg, cl) -> { } );
    put("fuzion.java.string_to_java_object0", (cfg, cl) -> { } );
    put("fuzion.java.create_jvm"            , (cfg, cl) -> { } );
    put("fuzion.java.u16_to_java_object"    , (cfg, cl) -> { } );

    put("concur.sync.mtx_init"              , (cfg, cl) -> { } );
    put("concur.sync.mtx_lock"              , (cfg, cl) -> { } );
    put("concur.sync.mtx_trylock"           , (cfg, cl) -> { } );
    put("concur.sync.mtx_unlock"            , (cfg, cl) -> { } );
    put("concur.sync.mtx_destroy"           , (cfg, cl) -> { } );
    put("concur.sync.cnd_init"              , (cfg, cl) -> { } );
    put("concur.sync.cnd_signal"            , (cfg, cl) -> { } );
    put("concur.sync.cnd_broadcast"         , (cfg, cl) -> { } );
    put("concur.sync.cnd_wait"              , (cfg, cl) -> { } );
    put("concur.sync.cnd_destroy"           , (cfg, cl) -> { } );
  }


  /**
   * Create call graph for calls in code block c of clazz cl.
   *
   * @param cl clazz id
   *
   * @param s0 the site starting the block to analyze.
   */
  void createCallGraphForBlock(int cl, int s0)
  {
    for (var s = s0; /* NYI: !containsVoid(stack) &&*/ _fuir.withinCode(s); s = s + _fuir.codeSizeAt(s))
      {
        var e = _fuir.codeAt(s);
        createCallGraphForExpr(cl, s, e);
      }
  }


  /**
   * Create call graph for calls made by expression at site s of clazz cl.
   *
   * @param cl clazz id
   *
   * @param s site of expression
   *
   * @param e the FUIR.ExprKind of the expression to analyze
   */
  void createCallGraphForExpr(int cl, int s, FUIR.ExprKind e)
  {
    switch (e)
      {
      case Assign: break;
      case Box   : break;
      case Call:
        {
          access(cl, s);
          break;
        }
      case Comment: break;
      case Current: break;
      case Const  : break;
      case Match  :
        {
          for (var mc = 0; mc < _fuir.matchCaseCount(s); mc++)
            {
              createCallGraphForBlock(cl, _fuir.matchCaseCode(s, mc));
            }
          break;
        }
      case Tag: break;
      case Env:
        {
          var ecl = _fuir.envClazz(s);
          addEffect(cl, ecl);
          break;
        }
      case Pop: break;
      default:
        {
          Errors.fatal("Effects backend does not handle expressions of type " + s);
        }
      }
  }


  /**
   * Create call graph for access (call or write) of a feature.
   *
   * @param cl clazz id
   *
   * @param s site of the access, must be ExprKind.Assign or ExprKind.Call
   */
  void access(int cl, int s)
  {
    var cc0 = _fuir.accessedClazz(s);

    if (_fuir.accessIsDynamic(s))
      {
        var ccs = _fuir.accessedClazzes(s);
        for (var cci = 0; cci < ccs.length; cci += 2)
          {
            var tt = ccs[cci  ];
            var cc = ccs[cci+1];
            call(cl, cc);
          }
      }
    else if (_fuir.clazzNeedsCode(cc0))
      {
        call(cl, cc0);
      }
  }


  /**
   * Create call graph for call to a feature
   *
   * @param cl clazz id of the call
   *
   * @param cc clazz that is called
   */
  void call(int cl, int cc)
  {
    if (_fuir.clazzNeedsCode(cc))
      {
        addToCallGraph(cl, cc);
      }
  }


  /**
   * Add edge from cl to cc to call graph
   *
   * @param cl the caller clazz
   *
   * @param cc the callee clazz
   */
  void addToCallGraph(int cl, int cc)
  {
    if (!_callGraph.contains(cl, cc))
      {
        _callGraph.put(cl, cc);

        if (!_calledClazzes.contains(cc))
          {
            _calledClazzes.add(cc);
            _newCalledClazzesToBeProcessed.add(cc);
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
