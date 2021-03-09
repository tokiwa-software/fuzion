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
 * Source of class Intrinsics
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;

import java.nio.charset.StandardCharsets;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;


/**
 * Intrinsics provides the C implementation of Fuzion's intrinsic features.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
class Intrinsics extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /*-------------------------  static methods  --------------------------*/


  /*---------------------------  constructors  --------------------------*/


  /**
   * Constructor, creates an instance.
   */
  Intrinsics()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


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
      c._fuir.clazzFieldIsAdrOfValue(or)               ? c._outer_.deref() :
      c._fuir.clazzIsRef(c._fuir.clazzResultClazz(or)) ? c._outer_.deref().field(C.FIELDS_IN_REF_CLAZZ) //.field("fzF_0_val")
                                                       : c._outer_;

    switch (c._fuir.clazzIntrinsicName(cl))
      {
      case "exitForCompilerTest" : return CExpr.call("exit", new List<>(new CIdent("arg0")));
      case "fuzion.std.out.write": var cid = new CIdent("c");
                                   return CStmnt.seq(CStmnt.decl("char",cid),
                                                       cid.assign(new CIdent("arg0").castTo("char")),
                                                       CExpr.call("fwrite",
                                                                  new List<>(new CIdent("c").adrOf(),
                                                                             CExpr.int32const(1),
                                                                             CExpr.int32const(1),
                                                                             new CIdent("stdout"))));

      case "fuzion.std.out.flush": return CExpr.call("fflush", new List<>(new CIdent("stdout")));

        /* NYI: The C standard does not guarentee wrap-around semantics for signed types, need
         * to check if this is the case for the C compilers used for Fuzion.
         */
      case "i32.prefix -°"       :
      case "i64.prefix -°"       : return outer.neg().ret();
      case "i32.infix -°"        :
      case "i64.infix -°"        : return outer.sub(new CIdent("arg0")).ret();
      case "i32.infix +°"        :
      case "i64.infix +°"        : return outer.add(new CIdent("arg0")).ret();
      case "i32.infix *°"        :
      case "i64.infix *°"        : return outer.mul(new CIdent("arg0")).ret();
      case "i32.div"             :
      case "i64.div"             : return outer.div(new CIdent("arg0")).ret();
      case "i32.mod"             :
      case "i64.mod"             : return outer.mod(new CIdent("arg0")).ret();
      case "i32.infix <<"        :
      case "i64.infix <<"        : return outer.shl(new CIdent("arg0")).ret();
      case "i32.infix >>"        :
      case "i64.infix >>"        : return outer.shr(new CIdent("arg0")).ret();
      case "i32.infix &"         :
      case "i64.infix &"         : return outer.and(new CIdent("arg0")).ret();
      case "i32.infix |"         :
      case "i64.infix |"         : return outer.or (new CIdent("arg0")).ret();

      case "i32.infix =="        :
      case "i64.infix =="        : return outer.eq(new CIdent("arg0")).cond(c.FZ_TRUE, c.FZ_FALSE).ret();
      case "i32.infix !="        :
      case "i64.infix !="        : return outer.ne(new CIdent("arg0")).cond(c.FZ_TRUE, c.FZ_FALSE).ret();
      case "i32.infix >"         :
      case "i64.infix >"         : return outer.gt(new CIdent("arg0")).cond(c.FZ_TRUE, c.FZ_FALSE).ret();
      case "i32.infix >="        :
      case "i64.infix >="        : return outer.ge(new CIdent("arg0")).cond(c.FZ_TRUE, c.FZ_FALSE).ret();
      case "i32.infix <"         :
      case "i64.infix <"         : return outer.lt(new CIdent("arg0")).cond(c.FZ_TRUE, c.FZ_FALSE).ret();
      case "i32.infix <="        :
      case "i64.infix <="        : return outer.le(new CIdent("arg0")).cond(c.FZ_TRUE, c.FZ_FALSE).ret();

      case "u32.prefix -°"       :
      case "u64.prefix -°"       : return outer.neg().ret();
      case "u32.infix -°"        :
      case "u64.infix -°"        : return outer.sub(new CIdent("arg0")).ret();
      case "u32.infix +°"        :
      case "u64.infix +°"        : return outer.add(new CIdent("arg0")).ret();
      case "u32.infix *°"        :
      case "u64.infix *°"        : return outer.mul(new CIdent("arg0")).ret();
      case "u32.div"             :
      case "u64.div"             : return outer.div(new CIdent("arg0")).ret();
      case "u32.mod"             :
      case "u64.mod"             : return outer.mod(new CIdent("arg0")).ret();
      case "u32.infix <<"        :
      case "u64.infix <<"        : return outer.shl(new CIdent("arg0")).ret();
      case "u32.infix >>"        :
      case "u64.infix >>"        : return outer.shr(new CIdent("arg0")).ret();
      case "u32.infix &"         :
      case "u64.infix &"         : return outer.and(new CIdent("arg0")).ret();
      case "u32.infix |"         :
      case "u64.infix |"         : return outer.or (new CIdent("arg0")).ret();

      case "u32.infix =="        :
      case "u64.infix =="        : return outer.eq(new CIdent("arg0")).cond(c.FZ_TRUE, c.FZ_FALSE).ret();
      case "u32.infix !="        :
      case "u64.infix !="        : return outer.ne(new CIdent("arg0")).cond(c.FZ_TRUE, c.FZ_FALSE).ret();
      case "u32.infix >"         :
      case "u64.infix >"         : return outer.gt(new CIdent("arg0")).cond(c.FZ_TRUE, c.FZ_FALSE).ret();
      case "u32.infix >="        :
      case "u64.infix >="        : return outer.ge(new CIdent("arg0")).cond(c.FZ_TRUE, c.FZ_FALSE).ret();
      case "u32.infix <"         :
      case "u64.infix <"         : return outer.lt(new CIdent("arg0")).cond(c.FZ_TRUE, c.FZ_FALSE).ret();
      case "u32.infix <="        :
      case "u64.infix <="        : return outer.le(new CIdent("arg0")).cond(c.FZ_TRUE, c.FZ_FALSE).ret();

      case "i32.as_i64"          : return outer.castTo("fzT_1i64").ret();
      case "u32.as_i64"          : return outer.castTo("fzT_1i64").ret();
      case "i32.castTo_u32"      : return outer.castTo("fzT_1u32").ret();
      case "u32.castTo_i32"      : return outer.castTo("fzT_1i32").ret();
      case "i64.castTo_u64"      : return outer.castTo("fzT_1u64").ret();
      case "i64.low32bits"       : return outer.and(CExpr. int64const(0xffffFFFFL)).castTo("fzT_1u32").ret();
      case "u64.castTo_i64"      : return outer.castTo("fzT_1i64").ret();
      case "u64.low32bits"       : return outer.and(CExpr.uint64const(0xffffFFFFL)).castTo("fzT_1u32").ret();

      case "Object.asString"     :
        {
          return CStmnt.seq(c.constString("NYI: Object.asString".getBytes(StandardCharsets.UTF_8), "res"),
                            new CIdent("res").castTo("fzT__Rstring*").ret());

        }

        // NYI: the following intrinsics are generic, they are currently hard-coded for i32 only:
      case "Array.getData": return CExpr.call("malloc",
                                                new List<>(new CIdent("fzT_1i32").sizeOfType().mul(new CIdent("arg0")))).ret();
      case "Array.setel"  : return new CIdent("arg0").castTo("fzT_1i32*").index(new CIdent("arg1")).assign(new CIdent("arg2"));
      case "Array.get"    : return new CIdent("arg0").castTo("fzT_1i32*").index(new CIdent("arg1")).ret();

      default:
        var msg = "code for intrinsic " + c._fuir.clazzIntrinsicName(cl) + " is missing";
        Errors.warning(msg);
        return CStmnt.seq(CExpr.call("fprintf",
                                       new List<>(new CIdent("stderr"),
                                                  CExpr.string("*** error: NYI: "+ msg + "\\n"))),
                            CExpr.call("exit", new List<>(CExpr.int32const(1))));

      }
  }

}

/* end of file */
