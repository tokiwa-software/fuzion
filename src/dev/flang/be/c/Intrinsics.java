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
 * Source of class Intrinsics
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;

import java.nio.charset.StandardCharsets;

import java.util.TreeMap;
import java.util.Set;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;


/**
 * Intrinsics provides the C implementation of Fuzion's intrinsic features.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Intrinsics extends ANY
{

  /*----------------------------  interfaces  ---------------------------*/


  interface IntrinsicCode
  {
    CStmnt get(C c, int cl, CExpr outer, String in);
  }

  /*----------------------------  constants  ----------------------------*/


  /**
   * Predefined identifiers to access args:
   */
  static CIdent A0 = new CIdent("arg0");
  static CIdent A1 = new CIdent("arg1");
  static CIdent A2 = new CIdent("arg2");


  static TreeMap<String, IntrinsicCode> _intrinsics_ = new TreeMap<>();
  static
  {
    put("safety", (c,cl,outer,in) -> (c._options.fuzionSafety() ? c._names.FZ_TRUE : c._names.FZ_FALSE).ret());
    put("safety"               , (c,cl,outer,in) -> (c._options.fuzionSafety() ? c._names.FZ_TRUE : c._names.FZ_FALSE).ret());
    put("debug"                , (c,cl,outer,in) -> (c._options.fuzionDebug()  ? c._names.FZ_TRUE : c._names.FZ_FALSE).ret());
    put("debugLevel"           , (c,cl,outer,in) -> (CExpr.int32const(c._options.fuzionDebugLevel())).ret());
    put("fuzion.std.args.count", (c,cl,outer,in) -> c._names.GLOBAL_ARGC.ret());
    put("fuzion.std.args.get"  , (c,cl,outer,in) ->
        {
          var tmp = new CIdent("tmp");
          var str = c._names.GLOBAL_ARGV.index(A0);
          var rc = c._fuir.clazzResultClazz(cl);
          return CStmnt.seq(c.constString(str,CExpr.call("strlen",new List<>(str)), tmp),
                            tmp.castTo(c._types.clazz(rc)).ret());
        });
    put("fuzion.std.exit"      , (c,cl,outer,in) -> CExpr.call("exit", new List<>(A0)));
    put("fuzion.std.out.write" ,
        "fuzion.std.err.write" , (c,cl,outer,in) ->
        {
          var cid = new CIdent("c");
          return CStmnt.seq(CStmnt.decl("char",cid),
                            cid.assign(A0.castTo("char")),
                            CExpr.call("fwrite",
                                       new List<>(cid.adrOf(),
                                                  CExpr.int32const(1),
                                                  CExpr.int32const(1),
                                                  outOrErr(in))));
        });
    put("fuzion.std.out.flush" ,
        "fuzion.std.err.flush" , (c,cl,outer,in) -> CExpr.call("fflush", new List<>(outOrErr(in))));
    put("fuzion.stdin.nextByte", (c,cl,outer,in) ->
        {
          var cIdent = new CIdent("c");
          return CStmnt.seq(CExpr.decl("char", cIdent, CExpr.call("getchar", new List<>())),
                            CExpr.iff(cIdent.eq(new CIdent("EOF")),CExpr.uint8const(0).ret()),
                            cIdent.ret()
                            );
        });

        /* NYI: The C standard does not guarentee wrap-around semantics for signed types, need
         * to check if this is the case for the C compilers used for Fuzion.
         */
    put("i8.prefix -°"         , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, CExpr.int8const (0), outer, '-', FUIR.SpecialClazzes.c_u8 , FUIR.SpecialClazzes.c_i8 ).ret());
    put("i16.prefix -°"        , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, CExpr.int16const(0), outer, '-', FUIR.SpecialClazzes.c_u16, FUIR.SpecialClazzes.c_i16).ret());
    put("i32.prefix -°"        , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, CExpr.int32const(0), outer, '-', FUIR.SpecialClazzes.c_u32, FUIR.SpecialClazzes.c_i32).ret());
    put("i64.prefix -°"        , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, CExpr.int64const(0), outer, '-', FUIR.SpecialClazzes.c_u64, FUIR.SpecialClazzes.c_i64).ret());
    put("i8.infix -°"          , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, outer, A0, '-', FUIR.SpecialClazzes.c_u8 , FUIR.SpecialClazzes.c_i8 ).ret());
    put("i16.infix -°"         , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, outer, A0, '-', FUIR.SpecialClazzes.c_u16, FUIR.SpecialClazzes.c_i16).ret());
    put("i32.infix -°"         , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, outer, A0, '-', FUIR.SpecialClazzes.c_u32, FUIR.SpecialClazzes.c_i32).ret());
    put("i64.infix -°"         , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, outer, A0, '-', FUIR.SpecialClazzes.c_u64, FUIR.SpecialClazzes.c_i64).ret());
    put("i8.infix +°"          , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, outer, A0, '+', FUIR.SpecialClazzes.c_u8 , FUIR.SpecialClazzes.c_i8 ).ret());
    put("i16.infix +°"         , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, outer, A0, '+', FUIR.SpecialClazzes.c_u16, FUIR.SpecialClazzes.c_i16).ret());
    put("i32.infix +°"         , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, outer, A0, '+', FUIR.SpecialClazzes.c_u32, FUIR.SpecialClazzes.c_i32).ret());
    put("i64.infix +°"         , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, outer, A0, '+', FUIR.SpecialClazzes.c_u64, FUIR.SpecialClazzes.c_i64).ret());
    put("i8.infix *°"          , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, outer, A0, '*', FUIR.SpecialClazzes.c_u8 , FUIR.SpecialClazzes.c_i8 ).ret());
    put("i16.infix *°"         , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, outer, A0, '*', FUIR.SpecialClazzes.c_u16, FUIR.SpecialClazzes.c_i16).ret());
    put("i32.infix *°"         , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, outer, A0, '*', FUIR.SpecialClazzes.c_u32, FUIR.SpecialClazzes.c_i32).ret());
    put("i64.infix *°"         , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, outer, A0, '*', FUIR.SpecialClazzes.c_u64, FUIR.SpecialClazzes.c_i64).ret());
    put("i8.div"               ,
        "i16.div"              ,
        "i32.div"              ,
        "i64.div"              , (c,cl,outer,in) -> outer.div(A0).ret());
    put("i8.mod"               ,
        "i16.mod"              ,
        "i32.mod"              ,
        "i64.mod"              , (c,cl,outer,in) -> outer.mod(A0).ret());
    put("i8.infix <<"          ,
        "i16.infix <<"         ,
        "i32.infix <<"         ,
        "i64.infix <<"         , (c,cl,outer,in) -> outer.shl(A0).ret());
    put("i8.infix >>"          ,
        "i16.infix >>"         ,
        "i32.infix >>"         ,
        "i64.infix >>"         , (c,cl,outer,in) -> outer.shr(A0).ret());
    put("i8.infix &"           ,
        "i16.infix &"          ,
        "i32.infix &"          ,
        "i64.infix &"          , (c,cl,outer,in) -> outer.and(A0).ret());
    put("i8.infix |"           ,
        "i16.infix |"          ,
        "i32.infix |"          ,
        "i64.infix |"          , (c,cl,outer,in) -> outer.or (A0).ret());
    put("i8.infix ^"           ,
        "i16.infix ^"          ,
        "i32.infix ^"          ,
        "i64.infix ^"          , (c,cl,outer,in) -> outer.xor(A0).ret());

    put("i8.infix =="          ,
        "i16.infix =="         ,
        "i32.infix =="         ,
        "i64.infix =="         , (c,cl,outer,in) -> outer.eq(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("i8.infix !="          ,
        "i16.infix !="         ,
        "i32.infix !="         ,
        "i64.infix !="         , (c,cl,outer,in) -> outer.ne(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("i8.infix >"           ,
        "i16.infix >"          ,
        "i32.infix >"          ,
        "i64.infix >"          , (c,cl,outer,in) -> outer.gt(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("i8.infix >="          ,
        "i16.infix >="         ,
        "i32.infix >="         ,
        "i64.infix >="         , (c,cl,outer,in) -> outer.ge(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("i8.infix <"           ,
        "i16.infix <"          ,
        "i32.infix <"          ,
        "i64.infix <"          , (c,cl,outer,in) -> outer.lt(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("i8.infix <="          ,
        "i16.infix <="         ,
        "i32.infix <="         ,
        "i64.infix <="         , (c,cl,outer,in) -> outer.le(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());

    put("u8.prefix -°"         ,
        "u16.prefix -°"        ,
        "u32.prefix -°"        ,
        "u64.prefix -°"        , (c,cl,outer,in) -> outer.neg().ret());
    put("u8.infix -°"          ,
        "u16.infix -°"         ,
        "u32.infix -°"         ,
        "u64.infix -°"         , (c,cl,outer,in) -> outer.sub(A0).ret());
    put("u8.infix +°"          ,
        "u16.infix +°"         ,
        "u32.infix +°"         ,
        "u64.infix +°"         , (c,cl,outer,in) -> outer.add(A0).ret());
    put("u8.infix *°"          ,
        "u16.infix *°"         ,
        "u32.infix *°"         ,
        "u64.infix *°"         , (c,cl,outer,in) -> outer.mul(A0).ret());
    put("u8.div"               ,
        "u16.div"              ,
        "u32.div"              ,
        "u64.div"              , (c,cl,outer,in) -> outer.div(A0).ret());
    put("u8.mod"               ,
        "u16.mod"              ,
        "u32.mod"              ,
        "u64.mod"              , (c,cl,outer,in) -> outer.mod(A0).ret());
    put("u8.infix <<"          ,
        "u16.infix <<"         ,
        "u32.infix <<"         ,
        "u64.infix <<"         , (c,cl,outer,in) -> outer.shl(A0).ret());
    put("u8.infix >>"          ,
        "u16.infix >>"         ,
        "u32.infix >>"         ,
        "u64.infix >>"         , (c,cl,outer,in) -> outer.shr(A0).ret());
    put("u8.infix &"           ,
        "u16.infix &"          ,
        "u32.infix &"          ,
        "u64.infix &"          , (c,cl,outer,in) -> outer.and(A0).ret());
    put("u8.infix |"           ,
        "u16.infix |"          ,
        "u32.infix |"          ,
        "u64.infix |"          , (c,cl,outer,in) -> outer.or (A0).ret());
    put("u8.infix ^"           ,
        "u16.infix ^"          ,
        "u32.infix ^"          ,
        "u64.infix ^"          , (c,cl,outer,in) -> outer.xor(A0).ret());

    put("u8.infix =="          ,
        "u16.infix =="         ,
        "u32.infix =="         ,
        "u64.infix =="         , (c,cl,outer,in) -> outer.eq(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("u8.infix !="          ,
        "u16.infix !="         ,
        "u32.infix !="         ,
        "u64.infix !="         , (c,cl,outer,in) -> outer.ne(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("u8.infix >"           ,
        "u16.infix >"          ,
        "u32.infix >"          ,
        "u64.infix >"          , (c,cl,outer,in) -> outer.gt(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("u8.infix >="          ,
        "u16.infix >="         ,
        "u32.infix >="         ,
        "u64.infix >="         , (c,cl,outer,in) -> outer.ge(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("u8.infix <"           ,
        "u16.infix <"          ,
        "u32.infix <"          ,
        "u64.infix <"          , (c,cl,outer,in) -> outer.lt(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("u8.infix <="          ,
        "u16.infix <="         ,
        "u32.infix <="         ,
        "u64.infix <="         , (c,cl,outer,in) -> outer.le(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());

    put("i8.as_i32"            , (c,cl,outer,in) -> outer.castTo("fzT_1i32").ret());
    put("i16.as_i32"           , (c,cl,outer,in) -> outer.castTo("fzT_1i32").ret());
    put("i32.as_i64"           , (c,cl,outer,in) -> outer.castTo("fzT_1i64").ret());
    put("u8.as_i32"            , (c,cl,outer,in) -> outer.castTo("fzT_1i32").ret());
    put("u16.as_i32"           , (c,cl,outer,in) -> outer.castTo("fzT_1i32").ret());
    put("u32.as_i64"           , (c,cl,outer,in) -> outer.castTo("fzT_1i64").ret());
    put("i8.castTo_u8"         , (c,cl,outer,in) -> outer.castTo("fzT_1u8").ret());
    put("i16.castTo_u16"       , (c,cl,outer,in) -> outer.castTo("fzT_1u16").ret());
    put("i32.castTo_u32"       , (c,cl,outer,in) -> outer.castTo("fzT_1u32").ret());
    put("i64.castTo_u64"       , (c,cl,outer,in) -> outer.castTo("fzT_1u64").ret());
    put("u8.castTo_i8"         , (c,cl,outer,in) -> outer.castTo("fzT_1i8").ret());
    put("u16.castTo_i16"       , (c,cl,outer,in) -> outer.castTo("fzT_1i16").ret());
    put("u32.castTo_i32"       , (c,cl,outer,in) -> outer.castTo("fzT_1i32").ret());
    put("u32.castTo_f32"       , (c,cl,outer,in) -> outer.adrOf().castTo("fzT_1f32*").deref().ret());
    put("u64.castTo_i64"       , (c,cl,outer,in) -> outer.castTo("fzT_1i64").ret());
    put("u64.castTo_f64"       , (c,cl,outer,in) -> outer.adrOf().castTo("fzT_1f64*").deref().ret());
    put("u16.low8bits"         , (c,cl,outer,in) -> outer.and(CExpr.uint16const(0xFF)).castTo("fzT_1u8").ret());
    put("u32.low8bits"         , (c,cl,outer,in) -> outer.and(CExpr.uint32const(0xFF)).castTo("fzT_1u8").ret());
    put("u64.low8bits"         , (c,cl,outer,in) -> outer.and(CExpr.uint64const(0xFFL)).castTo("fzT_1u8").ret());
    put("u32.low16bits"        , (c,cl,outer,in) -> outer.and(CExpr.uint32const(0xFFFF)).castTo("fzT_1u16").ret());
    put("u64.low16bits"        , (c,cl,outer,in) -> outer.and(CExpr.uint64const(0xFFFFL)).castTo("fzT_1u16").ret());
    put("u64.low32bits"        , (c,cl,outer,in) -> outer.and(CExpr.uint64const(0xffffFFFFL)).castTo("fzT_1u32").ret());
    put("i32.as_f64"           ,
        "i64.as_f64"           ,
        "u32.as_f64"           ,
        "u64.as_f64"           , (c,cl,outer,in) -> outer.castTo("fzT_1f64").ret());

    put("f32.prefix -"         ,
        "f64.prefix -"         , (c,cl,outer,in) -> outer.neg().ret());
    put("f32.infix +"          ,
        "f64.infix +"          , (c,cl,outer,in) -> outer.add(A0).ret());
    put("f32.infix -"          ,
        "f64.infix -"          , (c,cl,outer,in) -> outer.sub(A0).ret());
    put("f32.infix *"          ,
        "f64.infix *"          , (c,cl,outer,in) -> outer.mul(A0).ret());
    put("f32.infix /"          ,
        "f64.infix /"          , (c,cl,outer,in) -> outer.div(A0).ret());
    put("f32.infix %"          ,
        "f64.infix %"          , (c,cl,outer,in) -> CExpr.call("fmod", new List<>(outer, A0)).ret());
    put("f32.infix **"         ,
        "f64.infix **"         , (c,cl,outer,in) -> CExpr.call("pow", new List<>(outer, A0)).ret());
    put("f32.infix =="         ,
        "f64.infix =="         , (c,cl,outer,in) -> outer.eq(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("f32.infix !="         ,
        "f64.infix !="         , (c,cl,outer,in) -> outer.ne(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("f32.infix <"          ,
        "f64.infix <"          , (c,cl,outer,in) -> outer.lt(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("f32.infix <="         ,
        "f64.infix <="         , (c,cl,outer,in) -> outer.le(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("f32.infix >"          ,
        "f64.infix >"          , (c,cl,outer,in) -> outer.gt(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("f32.infix >="         ,
        "f64.infix >="         , (c,cl,outer,in) -> outer.ge(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("f32.as_f64"           , (c,cl,outer,in) -> outer.castTo("fzT_1f64").ret());
    put("f64.as_f32"           , (c,cl,outer,in) -> outer.castTo("fzT_1f32").ret());
    put("f64.as_i64_lax"       , (c,cl,outer,in) ->
        { // workaround for clang warning: "integer literal is too large to be represented in a signed integer type"
          var i64Min = (new CIdent("9223372036854775807")).neg().sub(new CIdent("1"));
          var i64Max =  new CIdent("9223372036854775807");
          return CStmnt.seq(
                            CExpr.iff(CExpr.call("isnan", new List<>(outer)).ne(new CIdent("0")), new CIdent("0").ret()),
                            CExpr.iff(outer.ge(i64Max.castTo("fzT_1f64")), i64Max.castTo("fzT_1i64").ret()),
                            CExpr.iff(outer.le(i64Min.castTo("fzT_1f64")), i64Min.castTo("fzT_1i64").ret()),
                            outer.castTo("fzT_1i64").ret()
                            );
        });
    put("f32.castTo_u32"       , (c,cl,outer,in) -> outer.adrOf().castTo("fzT_1u32*").deref().ret());
    put("f64.castTo_u64"       , (c,cl,outer,in) -> outer.adrOf().castTo("fzT_1u64*").deref().ret());
    put("f32.asString"         ,
        "f64.asString"         , (c,cl,outer,in) ->
        {
          var res = new CIdent("res");
          var rc = c._fuir.clazzResultClazz(cl);
          return CStmnt.seq(c.floatToConstString(outer, res),
                            res.castTo(c._types.clazz(rc)).ret());
        });

    /* The C standard library follows the convention that floating-point numbers x × 2exp have 0.5 ≤ x < 1,
     * while the IEEE 754 standard text uses the convention 1 ≤ x < 2.
     * This convention in C is not just used for DBL_MAX_EXP, but also for functions such as frexp.
     * source: https://github.com/rust-lang/rust/issues/88734
     */
    put("f32s.minExp"          , (c,cl,outer,in) -> CExpr.ident("FLT_MIN_EXP").sub(new CIdent("1")).ret());
    put("f32s.maxExp"          , (c,cl,outer,in) -> CExpr.ident("FLT_MAX_EXP").sub(new CIdent("1")).ret());
    put("f32s.minPositive"     , (c,cl,outer,in) -> CExpr.ident("FLT_MIN").ret());
    put("f32s.max"             , (c,cl,outer,in) -> CExpr.ident("FLT_MAX").ret());
    put("f32s.epsilon"         , (c,cl,outer,in) -> CExpr.ident("FLT_EPSILON").ret());
    put("f64s.minExp"          , (c,cl,outer,in) -> CExpr.ident("DBL_MIN_EXP").sub(new CIdent("1")).ret());
    put("f64s.maxExp"          , (c,cl,outer,in) -> CExpr.ident("DBL_MAX_EXP").sub(new CIdent("1")).ret());
    put("f64s.minPositive"     , (c,cl,outer,in) -> CExpr.ident("DBL_MIN").ret());
    put("f64s.max"             , (c,cl,outer,in) -> CExpr.ident("DBL_MAX").ret());
    put("f64s.epsilon"         , (c,cl,outer,in) -> CExpr.ident("DBL_EPSILON").ret());
    put("f32s.isNaN"           ,
        "f64s.isNaN"           , (c,cl,outer,in) -> CStmnt.seq(CStmnt.iff(CExpr.call("isnan", new List<>(A0)).ne(new CIdent("0")),
                                                                          c._names.FZ_TRUE.ret()
                                                                          ),
                                                               c._names.FZ_FALSE.ret()
                                                               ));
    put("f32s.squareRoot"      , (c,cl,outer,in) -> CExpr.call("sqrtf",  new List<>(A0)).ret());
    put("f64s.squareRoot"      , (c,cl,outer,in) -> CExpr.call("sqrt",   new List<>(A0)).ret());
    put("f32s.exp"             , (c,cl,outer,in) -> CExpr.call("expf",   new List<>(A0)).ret());
    put("f64s.exp"             , (c,cl,outer,in) -> CExpr.call("exp",    new List<>(A0)).ret());
    put("f32s.log"             , (c,cl,outer,in) -> CExpr.call("logf",   new List<>(A0)).ret());
    put("f64s.log"             , (c,cl,outer,in) -> CExpr.call("log",    new List<>(A0)).ret());
    put("f32s.sin"             , (c,cl,outer,in) -> CExpr.call("sinf",   new List<>(A0)).ret());
    put("f64s.sin"             , (c,cl,outer,in) -> CExpr.call("sin",    new List<>(A0)).ret());
    put("f32s.cos"             , (c,cl,outer,in) -> CExpr.call("cosf",   new List<>(A0)).ret());
    put("f64s.cos"             , (c,cl,outer,in) -> CExpr.call("cos",    new List<>(A0)).ret());
    put("f32s.tan"             , (c,cl,outer,in) -> CExpr.call("tanf",   new List<>(A0)).ret());
    put("f64s.tan"             , (c,cl,outer,in) -> CExpr.call("tan",    new List<>(A0)).ret());
    put("f32s.asin"            , (c,cl,outer,in) -> CExpr.call("asinf", new List<>(A0)).ret());
    put("f64s.asin"            , (c,cl,outer,in) -> CExpr.call("asin",  new List<>(A0)).ret());
    put("f32s.acos"            , (c,cl,outer,in) -> CExpr.call("acosf", new List<>(A0)).ret());
    put("f64s.acos"            , (c,cl,outer,in) -> CExpr.call("acos",  new List<>(A0)).ret());
    put("f32s.atan"            , (c,cl,outer,in) -> CExpr.call("atanf", new List<>(A0)).ret());
    put("f64s.atan"            , (c,cl,outer,in) -> CExpr.call("atan",  new List<>(A0)).ret());
    put("f32s.atan2"           , (c,cl,outer,in) -> CExpr.call("atan2f", new List<>(A0, A1)).ret());
    put("f64s.atan2"           , (c,cl,outer,in) -> CExpr.call("atan2",  new List<>(A0, A1)).ret());
    put("f32s.sinh"            , (c,cl,outer,in) -> CExpr.call("sinhf",  new List<>(A0)).ret());
    put("f64s.sinh"            , (c,cl,outer,in) -> CExpr.call("sinh",   new List<>(A0)).ret());
    put("f32s.cosh"            , (c,cl,outer,in) -> CExpr.call("coshf",  new List<>(A0)).ret());
    put("f64s.cosh"            , (c,cl,outer,in) -> CExpr.call("cosh",   new List<>(A0)).ret());
    put("f32s.tanh"            , (c,cl,outer,in) -> CExpr.call("tanhf",  new List<>(A0)).ret());
    put("f64s.tanh"            , (c,cl,outer,in) -> CExpr.call("tanh",   new List<>(A0)).ret());

    put("Object.hashCode"      , (c,cl,outer,in) ->
        {
          var or = c._fuir.clazzOuterRef(cl);
          var hc = c._fuir.clazzIsRef(c._fuir.clazzResultClazz(or))
            ? CNames.OUTER.castTo("char *").sub(new CIdent("NULL").castTo("char *")).castTo("int32_t") // NYI: This implementation of hashCode relies on non-compacting GC
            : CExpr.int32const(42);  // NYI: This implementation of hashCode is stupid
          return hc.ret();
        });
    put("Object.asString"      , (c,cl,outer,in) ->
        {
          var res = new CIdent("res");
          var clname = c._fuir.clazzAsString(c._fuir.clazzOuterClazz(cl));
          var instname = "instance[" + clname + "]";
          var instchars = instname.getBytes(StandardCharsets.UTF_8);
          var rc = c._fuir.clazzResultClazz(cl);
          return CStmnt.seq(c.constString(instchars, res),
                            res.castTo(c._types.clazz(rc)).ret());
        });

    put("fuzion.sys.array.alloc", (c,cl,outer,in) ->
        {
          var gc = c._fuir.clazzActualGeneric(cl, 0);
          return CExpr.call("malloc",
                            new List<>(CExpr.sizeOfType(c._types.clazz(gc)).mul(A0))).ret();
        });
    put("fuzion.sys.array.setel", (c,cl,outer,in) ->
        {
          var gc = c._fuir.clazzActualGeneric(cl, 0);
          return c._types.hasData(gc)
            ? A0.castTo(c._types.clazz(gc) + "*").index(A1).assign(A2)
            : CStmnt.EMPTY;
        });
    put("fuzion.sys.array.get" , (c,cl,outer,in) ->
        {
          var gc = c._fuir.clazzActualGeneric(cl, 0);
          return c._types.hasData(gc)
            ? A0.castTo(c._types.clazz(gc) + "*").index(A1).ret()
            : CStmnt.EMPTY;
        });
    put("fuzion.sys.env_vars.has0", (c,cl,outer,in) ->
        {
          return CStmnt.seq(CStmnt.iff(CExpr.call("getenv",new List<>(A0.castTo("char*"))).ne(CNames.NULL),
                                       c._names.FZ_TRUE.ret()),
                            c._names.FZ_FALSE.ret());
        });
    put("fuzion.sys.env_vars.get0", (c,cl,outer,in) ->
        {
          var tmp = new CIdent("tmp");
          var str = new CIdent("str");
          var rc = c._fuir.clazzResultClazz(cl);
          return CStmnt.seq(CStmnt.decl("char *", str),
                            str.assign(CExpr.call("getenv",new List<>(A0.castTo("char*")))),
                            c.constString(str, CExpr.call("strlen",new List<>(str)), tmp),
                            tmp.castTo(c._types.clazz(rc)).ret());
        });
    put("fuzion.sys.thread.spawn0", (c,cl,outer,in) ->
        {
          var oc = c._fuir.clazzActualGeneric(cl, 0);
          var call = c._fuir.lookupCall(oc);
          if (c._fuir.clazzNeedsCode(call))
            {
              var pt = new CIdent("pt");
              var res = new CIdent("res");
              return CStmnt.seq(CExpr.decl("pthread_t *", pt),
                                CExpr.decl("int", res),
                                pt.assign(CExpr.call("malloc", new List<>(CExpr.sizeOfType("pthread_t")))),
                                CExpr.iff(pt.eq(CNames.NULL),
                                          CStmnt.seq(CExpr.fprintfstderr("*** malloc(%lu) failed\n", CExpr.sizeOfType("pthread_t")),
                                                     CExpr.call("exit", new List<>(CExpr.int32const(1))))),
                                res.assign(CExpr.call("pthread_create", new List<>(pt,
                                                                                   CNames.NULL,
                                                                                   CExpr.ident(c._names.function(call, false)).adrOf().castTo("void *(*)(void *)"),
                                                                                   A0.castTo("void *")))),
                                CExpr.iff(res.ne(CExpr.int32const(0)),
                                          CStmnt.seq(CExpr.fprintfstderr("*** pthread_create failed with return code %d\n",res),
                                                     CExpr.call("exit", new List<>(CExpr.int32const(1))))));
              // NYI: free(pt)!
            }
          else
            {
              return CStmnt.EMPTY;
            }
        });
    put("fuzion.std.nano_time", (c,cl,outer,in) ->
        {
          var result = new CIdent("result");
          var onFailure = CStmnt.seq(CExpr.fprintfstderr("*** clock_gettime failed\n"),
                                     CExpr.call("exit", new List<>(new CIdent("1")){}));
          return CStmnt.seq(CStmnt.decl("struct timespec", result),
                            CExpr.iff(CExpr.call("clock_gettime",
                                                 new List<>(new CIdent("CLOCK_MONOTONIC"), result.adrOf()){}
                                                 ).ne(new CIdent("0")),
                                      onFailure
                                      ),
                            result.field(new CIdent("tv_sec"))
                            .mul(CExpr.uint64const(1_000_000_000))
                            .add(result.field(new CIdent("tv_nsec")))
                            .ret()
                            );
        });
    put("fuzion.std.nano_sleep", (c,cl,outer,in) ->
        {
          var req = new CIdent("req");
          var sec = A0.div(CExpr.int64const(1_000_000_000));
          var nsec = A0.sub(sec.mul(CExpr.int64const(1_000_000_000)));
          return CStmnt.seq(CStmnt.decl("struct timespec",req,CExpr.compoundLiteral("struct timespec",
                                                                                    sec.code() + "," +
                                                                                    nsec.code())),
                            /* NYI: while: */ CExpr.call("nanosleep",new List<>(req.adrOf(),req.adrOf())));
        });

    put("effect.replace"       ,
        "effect.default"       ,
        "effect.abortable"     ,
        "effect.abort"         , (c,cl,outer,in) ->
        {
          var ecl = effectType(c, cl);
          var ev  = c._names.env(ecl);
          var evi = c._names.envInstalled(ecl);
          var evj = c._names.envJmpBuf(ecl);
          var o   = c._names.OUTER;
          var e   = c._fuir.clazzIsRef(ecl) ? o : o.deref();
          return
            switch (in)
              {
              case "effect.replace"   ->                                  ev.assign(e)                            ;
              case "effect.default"   -> CStmnt.iff(evi.not(), CStmnt.seq(ev.assign(e), evi.assign(CIdent.TRUE )));
              case "effect.abortable" ->
                {
                  var oc = c._fuir.clazzActualGeneric(cl, 0);
                  var call = c._fuir.lookupCall(oc);
                  if (c._fuir.clazzNeedsCode(call))
                    {
                      var jmpbuf = new CIdent("jmpbuf");
                      var oldev  = new CIdent("old_ev");
                      var oldevi = new CIdent("old_evi");
                      var oldevj = new CIdent("old_evj");
                      yield CStmnt.seq(CStmnt.decl(c._types.clazz(ecl), oldev , ev ),
                                       CStmnt.decl("bool"             , oldevi, evi),
                                       CStmnt.decl("jmp_buf*"         , oldevj, evj),
                                       CStmnt.decl("jmp_buf", jmpbuf),
                                       ev.assign(e),
                                       evi.assign(CIdent.TRUE ),
                                       evj.assign(jmpbuf.adrOf()),
                                       CStmnt.iff(CExpr.call("setjmp",new List<>(jmpbuf)).eq(CExpr.int32const(0)),
                                                  CExpr.call(c._names.function(call, false), new List<>(A0))),
                                       /* NYI: this is a bit radical: we copy back the value from env to the outer instance, i.e.,
                                        * the outer instance is no longer immutable and we might run into difficulties if
                                        * the outer instance is used otherwise.
                                        *
                                        * It might be better to store the adr of a a value type effect in ev. Then we do not
                                        * have to copy anything back, but we would have to copy the value in case of effect.replace
                                        * and effect.default.
                                        */
                                       e.assign(ev),
                                       ev .assign(oldev ),
                                       evi.assign(oldevi),
                                       evj.assign(oldevj));
                    }
                  else
                    {
                      yield CStmnt.seq(CExpr.fprintfstderr("*** C backend no code for class '%s'\n",
                                                           CExpr.string(c._fuir.clazzAsString(call))),
                                       CExpr.call("exit", new List<>(CExpr.int32const(1))));
                    }
                }
              case "effect.abort"   ->
                CStmnt.seq(CStmnt.iff(evi, CExpr.call("longjmp",new List<>(evj.deref(), CExpr.int32const(1)))),
                           CExpr.fprintfstderr("*** C backend support for %s missing\n",
                                               CExpr.string(c._fuir.clazzIntrinsicName(cl))),
                           CExpr.exit(1));
              default -> throw new Error("unexpected intrinsic '" + in + "'.");
              };
        });
    put("effects.exists"       , (c,cl,outer,in) ->
        {
          var ecl = c._fuir.clazzActualGeneric(cl, 0);
          var evi = c._names.envInstalled(ecl);
          return CStmnt.seq(CStmnt.iff(evi, c._names.FZ_TRUE.ret()), c._names.FZ_FALSE.ret());
        });

    IntrinsicCode noJava = (c,cl,outer,in) ->
      CStmnt.seq(CExpr.fprintfstderr("*** C backend support does not support Java interface (yet).\n"),
                 CExpr.exit(1));
    put("fuzion.java.JavaObject.isNull"  , noJava);
    put("fuzion.java.arrayGet"           , noJava);
    put("fuzion.java.arrayLength"        , noJava);
    put("fuzion.java.arrayToJavaObject0" , noJava);
    put("fuzion.java.boolToJavaObject"   , noJava);
    put("fuzion.java.callC0"             , noJava);
    put("fuzion.java.callS0"             , noJava);
    put("fuzion.java.callV0"             , noJava);
    put("fuzion.java.f32ToJavaObject"    , noJava);
    put("fuzion.java.f64ToJavaObject"    , noJava);
    put("fuzion.java.getField0"          , noJava);
    put("fuzion.java.getStaticField0"    , noJava);
    put("fuzion.java.i16ToJavaObject"    , noJava);
    put("fuzion.java.i32ToJavaObject"    , noJava);
    put("fuzion.java.i64ToJavaObject"    , noJava);
    put("fuzion.java.i8ToJavaObject"     , noJava);
    put("fuzion.java.javaStringToString" , noJava);
    put("fuzion.java.stringToJavaObject0", noJava);
    put("fuzion.java.u16ToJavaObject"    , noJava);
  }


  /*----------------------------  variables  ----------------------------*/


  /*-------------------------  static methods  --------------------------*/


  private static void put(String n, IntrinsicCode c) { _intrinsics_.put(n, c); }
  private static void put(String n1, String n2, IntrinsicCode c) { put(n1, c); put(n2, c); }
  private static void put(String n1, String n2, String n3, IntrinsicCode c) { put(n1, c); put(n2, c); put(n3, c); }
  private static void put(String n1, String n2, String n3, String n4, IntrinsicCode c) { put(n1, c); put(n2, c); put(n3, c); put(n4, c); }


  /*---------------------------  constructors  --------------------------*/


  /**
   * Constructor, creates an instance.
   */
  Intrinsics()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Get the names of all intrinsics supported by this backend.
   */
  public static Set<String> supportedIntrinsics()
  {
    return _intrinsics_.keySet();
  }


  /**
   * Get the proper output file handle 'stdout' or 'stderr' depending on the
   * prefix of the intrinsic feature name in.
   *
   * @param in name of an intrinsic feature in fuzion.std.out or fuzion.std.err.
   *
   * @return CIdent of 'stdout' or 'stderr'
   */
  private static CIdent outOrErr(String in)
  {
    if      (in.startsWith("fuzion.std.out.")) { return new CIdent("stdout"); }
    else if (in.startsWith("fuzion.std.err.")) { return new CIdent("stderr"); }
    else                                       { throw new Error("outOrErr called on "+in); }
  }


  /**
   * Create code for intrinsic feature
   *
   * @param c the C backend
   *
   * @param cl the id of the intrinsic clazz
   */
  CStmnt code(C c, int cl)
  {
    var or = c._fuir.clazzOuterRef(cl);
    var outer =
      or == -1                                         ? null :
      c._fuir.clazzFieldIsAdrOfValue(or)               ? CNames.OUTER.deref() :
      c._fuir.clazzIsRef(c._fuir.clazzResultClazz(or)) ? CNames.OUTER.deref().field(CNames.FIELDS_IN_REF_CLAZZ)
                                                       : CNames.OUTER;

    var in = c._fuir.clazzIntrinsicName(cl);
    var cg = _intrinsics_.get(in);
    if (cg != null)
      {
        return cg.get(c, cl, outer, in);
      }
    else
      {
        var at = c._fuir.clazzTypeParameterActualType(cl);
        if (at >= 0)
          {
            // intrinsic is a type parameter, so create type instance:
            //
            // NYI: this does not work yet. Eventually. type instances should become
            // unit types so this should not be needed at all.
            var res = c._names.newTemp();
            CIdent tname = new CIdent("tname");
            var rc = c._fuir.clazzResultClazz(cl);
            return CStmnt.seq(c.declareAllocAndInitClazzId(rc, res),
                              c.constString(c._fuir.clazzAsStringNew(at).getBytes(StandardCharsets.UTF_8), tname),
                              res.deref().field(new CIdent("fields")).field(new CIdent("fzF_0_name")).assign(tname.castTo("void*")),
                              res.ret());
          }
        else
          {
            var msg = "code for intrinsic " + c._fuir.clazzIntrinsicName(cl) + " is missing";
            Errors.warning(msg);
            return CStmnt.seq(CExpr.call("fprintf",
                                         new List<>(new CIdent("stderr"),
                                                    CExpr.string("*** error: NYI: %s\n"),
                                                    CExpr.string(msg))),
                              CExpr.call("exit", new List<>(CExpr.int32const(1))));
          }
      }
  }


  /**
   * Is cl one of the instrinsics in effect that changes the effect in
   * the current environment?
   *
   * @param c the C backend
   *
   * @param cl the id of the intrinsic clazz
   *
   * @return true for effect.install and similar features.
   */
  static boolean isEffect(C c, int cl)
  {
    if (PRECONDITIONS) require
      (c._fuir.clazzKind(cl) == FUIR.FeatureKind.Intrinsic);

    return switch(c._fuir.clazzIntrinsicName(cl))
      {
      case "effect.replace",
           "effect.default",
           "effect.abortable",
           "effect.abort" -> true;
      default -> false;
      };
  }


  /**
   * For an intrinstic in effect that changes the effect in the
   * current environment, return the type of the environment.  This type is used
   * to distinguish different environments.
   *
   * @param c the C backend
   *
   * @param cl the id of the intrinsic clazz
   *
   * @return the type of the outer feature of cl
   */
  static int effectType(C c, int cl)
  {
    if (PRECONDITIONS) require
      (isEffect(c, cl));

    var or = c._fuir.clazzOuterRef(cl);
    return c._fuir.clazzResultClazz(or);
  }



  /**
   * Helper for signed wrapping arithmetic: Since C semantics are undefined for
   * an overflow for signed values, we cast signed values to their unsigned
   * counterparts for wrapping arithmetic and cast the result back to signed.
   *
   * @param c the C backend
   *
   * @param a the left expression
   *
   * @param b the right expression
   *
   * @param op an operator, one of '+', '-', '*'
   *
   * @param unsigned the unsigned type to cast to
   *
   * @param signed the signed type of a and b and the type the result has to be
   * casted to.
   */
  static CExpr castToUnsignedForArithmetic(C c, CExpr a, CExpr b, char op, FUIR.SpecialClazzes unsigned, FUIR.SpecialClazzes signed)
  {
    // C type
    var ut = c._types.scalar(unsigned);
    var st = c._types.scalar(signed  );

    // unsigned versions of a and b
    var au = a.castTo(ut);
    var bu = b.castTo(ut);

    // unsigned result
    var ru = switch (op)
      {
      case '+' -> au.add(bu);
      case '-' -> au.sub(bu);
      case '*' -> au.mul(bu);
      default -> throw new Error("unexpected arithmetic operator '" + op + "' for intrinsic.");
      };

    // signed result
    var rs = ru.castTo(st);

    return rs;
  }

}

/* end of file */
