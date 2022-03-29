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

import java.util.TreeSet;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.Graph;
import dev.flang.util.MapToN;


/**
 * CFG creates a control-flow-graph based on the FUIR representation of a
 * Fuzion application.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class CFG extends ANY
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/



  /*----------------------------  variables  ----------------------------*/


  /**
   * The intermediate code we are compiling.
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
        if (_fuir.clazzContract(cl, FUIR.ContractKind.Pre, 0) != -1)
          {
            createCallGraphForRoutine(cl, true);
          }
      }
  }


  /**
   * Create code for given clazz cl.
   *
   * @param cl id of clazz to generate code for
   *
   * @param pre true to create code for cl's precondition, false to create code
   * for cl itself.
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
   * Create code for given intrinsic clazz cl.
   *
   * @param cl id of clazz to generate code for
   *
   */
  void createCallGraphForIntrinsic(int cl)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzKind(cl) == FUIR.FeatureKind.Intrinsic);
    var in = _fuir.clazzIntrinsicName(cl);
    switch (in)
      {
      case "safety"              : return;
      case "debug"               : return;
      case "debugLevel"          : return;
      case "fuzion.std.args.count": return;
      case "fuzion.std.args.get" : return;
      case "fuzion.std.exit"     : return;
      case "fuzion.std.out.write": return;
      case "fuzion.std.err.write": return;
      case "fuzion.std.out.flush": return;
      case "fuzion.std.err.flush": return;
      case "i8.prefix -°"        : return;
      case "i16.prefix -°"       : return;
      case "i32.prefix -°"       : return;
      case "i64.prefix -°"       : return;
      case "i8.infix -°"         : return;
      case "i16.infix -°"        : return;
      case "i32.infix -°"        : return;
      case "i64.infix -°"        : return;
      case "i8.infix +°"         : return;
      case "i16.infix +°"        : return;
      case "i32.infix +°"        : return;
      case "i64.infix +°"        : return;
      case "i8.infix *°"         : return;
      case "i16.infix *°"        : return;
      case "i32.infix *°"        : return;
      case "i64.infix *°"        : return;
      case "i8.div"              : return;
      case "i16.div"             : return;
      case "i32.div"             : return;
      case "i64.div"             : return;
      case "i8.mod"              : return;
      case "i16.mod"             : return;
      case "i32.mod"             : return;
      case "i64.mod"             : return;
      case "i8.infix <<"         : return;
      case "i16.infix <<"        : return;
      case "i32.infix <<"        : return;
      case "i64.infix <<"        : return;
      case "i8.infix >>"         : return;
      case "i16.infix >>"        : return;
      case "i32.infix >>"        : return;
      case "i64.infix >>"        : return;
      case "i8.infix &"          : return;
      case "i16.infix &"         : return;
      case "i32.infix &"         : return;
      case "i64.infix &"         : return;
      case "i8.infix |"          : return;
      case "i16.infix |"         : return;
      case "i32.infix |"         : return;
      case "i64.infix |"         : return;
      case "i8.infix ^"          : return;
      case "i16.infix ^"         : return;
      case "i32.infix ^"         : return;
      case "i64.infix ^"         : return;

      case "i8.infix =="         : return;
      case "i16.infix =="        : return;
      case "i32.infix =="        : return;
      case "i64.infix =="        : return;
      case "i8.infix !="         : return;
      case "i16.infix !="        : return;
      case "i32.infix !="        : return;
      case "i64.infix !="        : return;
      case "i8.infix >"          : return;
      case "i16.infix >"         : return;
      case "i32.infix >"         : return;
      case "i64.infix >"         : return;
      case "i8.infix >="         : return;
      case "i16.infix >="        : return;
      case "i32.infix >="        : return;
      case "i64.infix >="        : return;
      case "i8.infix <"          : return;
      case "i16.infix <"         : return;
      case "i32.infix <"         : return;
      case "i64.infix <"         : return;
      case "i8.infix <="         : return;
      case "i16.infix <="        : return;
      case "i32.infix <="        : return;
      case "i64.infix <="        : return;

      case "u8.prefix -°"        : return;
      case "u16.prefix -°"       : return;
      case "u32.prefix -°"       : return;
      case "u64.prefix -°"       : return;
      case "u8.infix -°"         : return;
      case "u16.infix -°"        : return;
      case "u32.infix -°"        : return;
      case "u64.infix -°"        : return;
      case "u8.infix +°"         : return;
      case "u16.infix +°"        : return;
      case "u32.infix +°"        : return;
      case "u64.infix +°"        : return;
      case "u8.infix *°"         : return;
      case "u16.infix *°"        : return;
      case "u32.infix *°"        : return;
      case "u64.infix *°"        : return;
      case "u8.div"              : return;
      case "u16.div"             : return;
      case "u32.div"             : return;
      case "u64.div"             : return;
      case "u8.mod"              : return;
      case "u16.mod"             : return;
      case "u32.mod"             : return;
      case "u64.mod"             : return;
      case "u8.infix <<"         : return;
      case "u16.infix <<"        : return;
      case "u32.infix <<"        : return;
      case "u64.infix <<"        : return;
      case "u8.infix >>"         : return;
      case "u16.infix >>"        : return;
      case "u32.infix >>"        : return;
      case "u64.infix >>"        : return;
      case "u8.infix &"          : return;
      case "u16.infix &"         : return;
      case "u32.infix &"         : return;
      case "u64.infix &"         : return;
      case "u8.infix |"          : return;
      case "u16.infix |"         : return;
      case "u32.infix |"         : return;
      case "u64.infix |"         : return;
      case "u8.infix ^"          : return;
      case "u16.infix ^"         : return;
      case "u32.infix ^"         : return;
      case "u64.infix ^"         : return;

      case "u8.infix =="         : return;
      case "u16.infix =="        : return;
      case "u32.infix =="        : return;
      case "u64.infix =="        : return;
      case "u8.infix !="         : return;
      case "u16.infix !="        : return;
      case "u32.infix !="        : return;
      case "u64.infix !="        : return;
      case "u8.infix >"          : return;
      case "u16.infix >"         : return;
      case "u32.infix >"         : return;
      case "u64.infix >"         : return;
      case "u8.infix >="         : return;
      case "u16.infix >="        : return;
      case "u32.infix >="        : return;
      case "u64.infix >="        : return;
      case "u8.infix <"          : return;
      case "u16.infix <"         : return;
      case "u32.infix <"         : return;
      case "u64.infix <"         : return;
      case "u8.infix <="         : return;
      case "u16.infix <="        : return;
      case "u32.infix <="        : return;
      case "u64.infix <="        : return;

      case "i8.as_i32"           : return;
      case "i16.as_i32"          : return;
      case "i32.as_i64"          : return;
      case "u8.as_i32"           : return;
      case "u16.as_i32"          : return;
      case "u32.as_i64"          : return;
      case "i8.castTo_u8"        : return;
      case "i16.castTo_u16"      : return;
      case "i32.castTo_u32"      : return;
      case "i64.castTo_u64"      : return;
      case "u8.castTo_i8"        : return;
      case "u16.castTo_i16"      : return;
      case "u32.castTo_i32"      : return;
      case "u32.castTo_f32"      : return;
      case "u64.castTo_i64"      : return;
      case "u64.castTo_f64"      : return;
      case "u16.low8bits"        : return;
      case "u32.low8bits"        : return;
      case "u64.low8bits"        : return;
      case "u32.low16bits"       : return;
      case "u64.low16bits"       : return;
      case "u64.low32bits"       : return;

      case "f32.prefix -"        : return;
      case "f64.prefix -"        : return;
      case "f32.infix +"         : return;
      case "f64.infix +"         : return;
      case "f32.infix -"         : return;
      case "f64.infix -"         : return;
      case "f32.infix *"         : return;
      case "f64.infix *"         : return;
      case "f32.infix /"         : return;
      case "f64.infix /"         : return;
      case "f32.infix %"         : return;
      case "f64.infix %"         : return;
      case "f32.infix **"        : return;
      case "f64.infix **"        : return;
      case "f32.infix =="        : return;
      case "f64.infix =="        : return;
      case "f32.infix !="        : return;
      case "f64.infix !="        : return;
      case "f32.infix <"         : return;
      case "f64.infix <"         : return;
      case "f32.infix <="        : return;
      case "f64.infix <="        : return;
      case "f32.infix >"         : return;
      case "f64.infix >"         : return;
      case "f32.infix >="        : return;
      case "f64.infix >="        : return;
      case "f32.castTo_u32"      : return;
      case "f64.castTo_u64"      : return;
      case "f32.asString"        : return;
      case "f64.asString"        : return;

      case "f32s.minExp"         : return;
      case "f32s.maxExp"         : return;
      case "f32s.minPositive"    : return;
      case "f32s.max"            : return;
      case "f32s.epsilon"        : return;
      case "f64s.minExp"         : return;
      case "f64s.maxExp"         : return;
      case "f64s.minPositive"    : return;
      case "f64s.max"            : return;
      case "f64s.epsilon"        : return;
      case "f32s.squareRoot"     : return;
      case "f64s.squareRoot"     : return;
      case "f32s.exp"            : return;
      case "f64s.exp"            : return;
      case "f32s.log"            : return;
      case "f64s.log"            : return;
      case "f32s.sin"            : return;
      case "f64s.sin"            : return;
      case "f32s.cos"            : return;
      case "f64s.cos"            : return;
      case "f32s.tan"            : return;
      case "f64s.tan"            : return;
      case "f32s.asin"           : return;
      case "f64s.asin"           : return;
      case "f32s.acos"           : return;
      case "f64s.acos"           : return;
      case "f32s.atan"           : return;
      case "f64s.atan"           : return;
      case "f32s.sinh"           : return;
      case "f64s.sinh"           : return;
      case "f32s.cosh"           : return;
      case "f64s.cosh"           : return;
      case "f32s.tanh"           : return;
      case "f64s.tanh"           : return;

      case "Object.hashCode"     : return;
      case "Object.asString"     : return;
      case "sys.array.alloc"     : return;
      case "sys.array.setel"     : return;
      case "sys.array.get"       : return;
      case "fuzion.std.nano_time": return;

      case "effect.replace"      : return;
      case "effect.default"      : return;
      case "effect.abortable"    :
        var oc = _fuir.clazzActualGeneric(cl, 0);
        var call = _fuir.lookupCall(oc);
        if (_fuir.clazzNeedsCode(call))
          {
            addToCallGraph(cl, call, false);
          }
      case "effect.abort"        : return;
      case "effects.exists"      : return;
      default:
        var msg = "code for intrinsic " + _fuir.clazzIntrinsicName(cl) + " is missing";
        Errors.warning(msg);
      }
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
      case Outer  : break;
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
