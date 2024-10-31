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

import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.fuir.FUIR;
import dev.flang.fuir.FUIR.SpecialClazzes;
import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.Version;


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
  static CIdent A3 = new CIdent("arg3");
  static CIdent A4 = new CIdent("arg4");
  static CIdent A5 = new CIdent("arg5");
  static CIdent A6 = new CIdent("arg6");
  static CIdent A7 = new CIdent("arg7");

  /**
   * Predefined identifier to access errno macro.
   */
  static CIdent errno = new CIdent("errno");

  /**
   * Wrap code into a mutex_lock/unlock.  This
   * ensured atomicity with respect to any other code that is locked.
   */
  static CStmnt locked(CStmnt code)
  {
    return CStmnt.seq(CExpr.call("fzE_lock", new List<>()),
                      code,
                      CExpr.call("fzE_unlock", new List<>()));
  }


  static TreeMap<String, IntrinsicCode> _intrinsics_ = new TreeMap<>();
  static
  {
    put("Type.name"            , (c,cl,outer,in) ->
        {
          var rc = c._fuir.clazzResultClazz(cl);
          return c.heapClone(
              c.constString( c._fuir.clazzTypeName(c._fuir.clazzOuterClazz(cl))),
              rc
            )
            .ret();
        });

    put("concur.atomic.compare_and_swap0",  (c,cl,outer,in) ->
        {
          var ac = c._fuir.clazzOuterClazz(cl);
          var v = c._fuir.lookupAtomicValue(ac);
          var rc  = c._fuir.clazzResultClazz(v);
          var expected  = A0;
          var new_value = A1;
          var tmp = new CIdent("tmp");
          var code = CStmnt.EMPTY;
          if (!c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_unit))
            {
              var f = c.accessField(outer, ac, v);
              CExpr eq;
              if (c._fuir.clazzIsRef(rc) ||
                  c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i8  ) ||
                  c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i16 ) ||
                  c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i32 ) ||
                  c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i64 ) ||
                  c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u8  ) ||
                  c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u16 ) ||
                  c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u32 ) ||
                  c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u64 ))
                {
                  code = CStmnt.seq(CExpr.decl(c._types.clazz(rc), tmp, expected),
                                    CExpr.call("atomic_compare_exchange_strong_explicit", new List<>(
                                      f.adrOf().castTo(c._types.atomicType(rc)+"*"),
                                      tmp.adrOf().castTo("void *" /* the underlying type e.g. `uintptr_t *`, `uint_least64_t` */),
                                      new_value.adrOf().castTo(c._types.atomicType(rc)+"*").deref(),
                                      new CIdent("memory_order_seq_cst"),
                                      new CIdent("memory_order_seq_cst"))),
                                    tmp.ret());
                }
              else
                {
                  var res = c._names.newTemp();
                  code = CStmnt.seq(locked(CStmnt.seq(CExpr.decl(c._types.clazz(rc), tmp, f),
                                                      CStmnt.seq(res.decl("bool", res),
                                                                 compareValues(c, tmp, expected, rc, res),
                                                                 CStmnt.iff(res,
                                                                            f.assign(new_value))))),
                                    tmp.ret());
                }
            }
          return code;
        });

    put("concur.atomic.compare_and_set0",  (c,cl,outer,in) ->
        {
          var ac = c._fuir.clazzOuterClazz(cl);
          var v = c._fuir.lookupAtomicValue(ac);
          var rc  = c._fuir.clazzResultClazz(v);
          var expected  = A0;
          var new_value = A1;
          var tmp = new CIdent("tmp");
          var res = new CIdent("set_successful");
          var code = CStmnt.EMPTY;
          if (!c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_unit))
            {
              var f = c.accessField(outer, ac, v);
              CExpr eq;
              if (c._fuir.clazzIsRef(rc) ||
                  c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i8  ) ||
                  c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i16 ) ||
                  c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i32 ) ||
                  c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i64 ) ||
                  c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u8  ) ||
                  c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u16 ) ||
                  c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u32 ) ||
                  c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u64 ))
                {
                  code = CStmnt.seq(CExpr.decl(c._types.clazz(rc), tmp, expected),
                                    CStmnt.iff(CExpr.call("atomic_compare_exchange_strong_explicit",
                                                          new List<>(
                                                            f.adrOf().castTo(c._types.atomicType(rc)+"*"),
                                                            tmp.adrOf().castTo("void *" /* the underlying type e.g. `uintptr_t *`, `uint_least64_t` */),
                                                            new_value.adrOf().castTo(c._types.atomicType(rc)+"*").deref(),
                                                            new CIdent("memory_order_seq_cst"),
                                                            new CIdent("memory_order_seq_cst"))),
                                      c._names.FZ_TRUE.ret()),
                                    c._names.FZ_FALSE.ret());
                }
              else
                {
                  if (c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_f32) ||
                      c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_f64))
                    {
                      eq = CExpr.eq(tmp, expected);
                    }
                  else
                    {
                      eq = CExpr.eq(CExpr.call("memcmp", new List<>(tmp.adrOf(), expected.adrOf(), CExpr.sizeOfType(c._types.clazz(rc)))), new CIdent("0"));
                    }

                  code = CStmnt.seq(CStmnt.decl("bool", res),
                                    locked(CStmnt.seq(CExpr.decl(c._types.clazz(rc), tmp, f),
                                                      compareValues(c, tmp, expected, rc, res),
                                                      CStmnt.iff(res,
                                                                 f.assign(new_value)
                                                                 ))),
                                    CStmnt.seq(CStmnt.iff(res, c._names.FZ_TRUE.ret()), c._names.FZ_FALSE.ret()));
                }
            }
          return code;
        });

    put("concur.atomic.racy_accesses_supported",  (c,cl,outer,in) ->
        {
          var v = c._fuir.lookupAtomicValue(c._fuir.clazzOuterClazz(cl));
          var rc  = c._fuir.clazzResultClazz(v);
          var r =
            c._fuir.clazzIsRef(rc) ||
            c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i8  ) ||
            c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i16 ) ||
            c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i32 ) ||
            c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i64 ) ||
            c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u8  ) ||
            c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u16 ) ||
            c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u32 ) ||
            c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u64 ) ||
            c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_f32 ) ||
            c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_f64 ) ||
            c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_bool) ||
            c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_unit);
          return (r ? c._names.FZ_TRUE : c._names.FZ_FALSE).ret();
        });

    put("concur.atomic.read0",  (c,cl,outer,in) ->
        {
          var ac = c._fuir.clazzOuterClazz(cl);
          var v = c._fuir.lookupAtomicValue(ac);
          var rc  = c._fuir.clazzResultClazz(v);
          var tmp = new CIdent("tmp");
          CStmnt code;
          if (c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_unit))
            {
              code = CExpr.call("atomic_thread_fence", new List<>(new CIdent("memory_order_seq_cst")));
            }
          else if (c._fuir.clazzIsRef(rc) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i8  ) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i16 ) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i32 ) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i64 ) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u8  ) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u16 ) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u32 ) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u64 ) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_bool))
            {
              var f = c.accessField(outer, ac, v);
              code = CStmnt.seq(
                CExpr.decl(c._types.atomicType(rc), tmp),
                tmp.assign(CExpr.call("atomic_load_explicit", new List<>(f.adrOf().castTo(c._types.atomicType(rc)+"*"), new CIdent("memory_order_seq_cst")))),
                tmp.adrOf()
                  .castTo(c._types.clazz(rc) + "*")
                  .deref()
                  .ret()
              );
            }
          else
            {
              var f = c.accessField(outer, ac, v);
              code = CStmnt.seq(CExpr.decl(c._types.clazz(rc), tmp),
                                locked(tmp.assign(f)),
                                tmp.ret());
            }
          return code;
        });

    put("concur.atomic.write0", (c,cl,outer,in) ->
        {
          var ac = c._fuir.clazzOuterClazz(cl);
          var v = c._fuir.lookupAtomicValue(ac);
          var rc  = c._fuir.clazzResultClazz(v);
          var new_value = A0;
          var code = CStmnt.EMPTY;
          if (c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_unit))
            {
              code = CExpr.call("atomic_thread_fence", new List<>(new CIdent("memory_order_seq_cst")));
            }
          else if (c._fuir.clazzIsRef(rc) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i8  ) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i16 ) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i32 ) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_i64 ) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u8  ) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u16 ) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u32 ) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_u64 ) ||
                   c._fuir.clazzIs(rc, FUIR.SpecialClazzes.c_bool))
            {
              var f = c.accessField(outer, ac, v);
              code = CExpr.call("atomic_store_explicit", new List<>(f.adrOf().castTo(c._types.atomicType(rc)+"*"), new_value.adrOf().castTo(c._types.atomicType(rc)+"*").deref(), new CIdent("memory_order_seq_cst")));
            }
          else
            {
              var f = c.accessField(outer, ac, v);
              code = locked(f.assign(new_value));
            }
          return code;
        });

    put("concur.util.loadFence", (c,cl,outer,in) ->
        {
          return CExpr.call("atomic_thread_fence", new List<>(new CIdent("memory_order_seq_cst")));
        });

    put("concur.util.storeFence", (c,cl,outer,in) ->
        {
          return CExpr.call("atomic_thread_fence", new List<>(new CIdent("memory_order_seq_cst")));
        });

    put("safety"               , (c,cl,outer,in) -> (c._options.fuzionSafety() ? c._names.FZ_TRUE : c._names.FZ_FALSE).ret());
    put("debug"                , (c,cl,outer,in) -> (c._options.fuzionDebug()  ? c._names.FZ_TRUE : c._names.FZ_FALSE).ret());
    put("debug_level"          , (c,cl,outer,in) -> (CExpr.int32const(c._options.fuzionDebugLevel())).ret());
    put("fuzion.sys.args.count", (c,cl,outer,in) -> CNames.GLOBAL_ARGC.ret());
    put("fuzion.sys.args.get"  , (c,cl,outer,in) ->
        {
          var str = CNames.GLOBAL_ARGV.index(A0);
          var rc = c._fuir.clazzResultClazz(cl);
          return c.heapClone(
            c.constString(str, CExpr.call("strlen",new List<>(str))),
            rc
          ).ret();
        });
    put("fuzion.std.exit"      , (c,cl,outer,in) -> CExpr.call("exit", new List<>(A0)));
    put("fuzion.sys.fileio.read"         , (c,cl,outer,in) ->
        {
          var result = new CIdent("result");
          var zero = new CIdent("0");
          return CStmnt.seq(
            CExpr.decl("int", result, CExpr.call("fread", new List<>(A1, CExpr.int8const(1), A2, A0.castTo("FILE *")))),
            CExpr.iff(CExpr.notEq(CExpr.call("ferror", new List<>(A0.castTo("FILE *"))), zero),
              CExpr.int32const(-2).ret()),
            CExpr.iff(result.eq(zero).and(CExpr.notEq(CExpr.call("feof", new List<>(A0.castTo("FILE *"))), zero)),
              CExpr.int32const(-1).ret()),
            result.castTo("fzT_1i32").ret()
          );
        });
    put("fuzion.sys.fileio.write"        , (c,cl,outer,in) ->
        {
          return CStmnt.seq(
            CExpr.call("fwrite",
                            new List<>(
                              A1.castTo("void *"),      // the data
                              CExpr.sizeOfType("char"), //
                              A2,                       // how many bytes to write
                              A0.castTo("FILE *")       // the file descriptor
                            )),
            CExpr.iff(CExpr.notEq(CExpr.call("ferror", new List<>(A0.castTo("FILE *"))), new CIdent("0")),
              CExpr.int32const(-1).ret()),
            CExpr.int32const(0).ret()
          );
        });
    put("fuzion.sys.fileio.delete"       ,  (c,cl,outer,in) ->
      CExpr.iff(
        CExpr.call("fzE_rm", new List<>(A0.castTo("char *")))
          .eq(new CIdent("0")),
            c._names.FZ_TRUE.ret(),
            c._names.FZ_FALSE.ret()));
    put("fuzion.sys.fileio.move"         , (c,cl,outer,in) ->
        {
          var resultIdent = new CIdent("result");
          return CStmnt.seq(
            CExpr.decl("int", resultIdent, CExpr.call("rename", new List<>(A0.castTo("char *"), A1.castTo("char *")))),
            // Testing if rename was successful
            CExpr.iff(resultIdent.eq(CExpr.int8const(0)), c._names.FZ_TRUE.ret()),
            c._names.FZ_FALSE.ret()
            );
        }
        );
    put("fuzion.sys.fileio.create_dir"   , (c,cl,outer,in) ->
        {
          var resultIdent = new CIdent("result");
          return CStmnt.seq(
            CExpr.decl("int", resultIdent, CExpr.call("fzE_mkdir", new List<>(A0.castTo("char *")))),
            CExpr.iff(resultIdent.eq(new CIdent("0")), c._names.FZ_TRUE.ret()),
            c._names.FZ_FALSE.ret());
        }
        );
    put("fuzion.sys.fileio.stats"   , (c,cl,outer,in) ->
      CExpr.iff(
        CExpr.call("fzE_stat", new List<>(A0.castTo("const char *"), A1.castTo("int64_t *")))
          .eq(CExpr.int8const(0)),
            c._names.FZ_TRUE.ret(),
            c._names.FZ_FALSE.ret()));
    put("fuzion.sys.fileio.lstats"   , (c,cl,outer,in) ->
      CExpr.iff(
        CExpr.call("fzE_lstat", new List<>(A0.castTo("const char *"), A1.castTo("int64_t *")))
          .eq(CExpr.int8const(0)),
            c._names.FZ_TRUE.ret(),
            c._names.FZ_FALSE.ret()));
    put("fuzion.sys.fileio.open"   , (c,cl,outer,in) ->
        {
          return CExpr.call("fzE_file_open", new List<>(
              A0.castTo("char *"),
              A1.castTo("int64_t *"),
              A2.castTo("int8_t")));
        }
        );
    put("fuzion.sys.fileio.close"   , (c,cl,outer,in) ->
        {
          return CStmnt.seq(
            errno.assign(new CIdent("0")),
            CStmnt.iff(CExpr.call("fclose", new List<>(A0.castTo("FILE *"))).eq(CExpr.int8const(0)), CExpr.int8const(0).ret()),
            errno.castTo("fzT_1i8").ret()
            );
        }
        );
    put("fuzion.sys.fileio.seek"   , (c,cl,outer,in) ->
        {
          var seekResults = new CIdent("seek_results");
          return CStmnt.seq(
            errno.assign(new CIdent("0")),
            CExpr.decl("fzT_1i64 *", seekResults, A2.castTo("fzT_1i64 *")),
            CStmnt.iff(CExpr.call("fseek", new List<>(A0.castTo("FILE *"), A1.castTo("long"), new CIdent("SEEK_SET"))).eq(CExpr.int8const(0)),
            seekResults.index(0).assign(CExpr.call("ftell", new List<>(A0.castTo("FILE *"))).castTo("fzT_1i64"))),
            seekResults.index(1).assign(errno.castTo("fzT_1i64"))
            );
        }
        );
    put("fuzion.sys.fileio.file_position"   , (c,cl,outer,in) ->
        {
          var positionResults = new CIdent("position_results");
          return CStmnt.seq(
            errno.assign(new CIdent("0")),
            CExpr.decl("fzT_1i64 *", positionResults, A1.castTo("fzT_1i64 *")),
            positionResults.index(0).assign(CExpr.call("ftell", new List<>(A0.castTo("FILE *"))).castTo("fzT_1i64")),
            positionResults.index(1).assign(errno.castTo("fzT_1i64"))
            );
        }
        );

    put("fuzion.sys.fileio.mmap"  , (c,cl,outer,in) -> CExpr.call("fzE_mmap", new List<CExpr>(
      A0.castTo("FILE * "),   // file
      A1.castTo("uint64_t"),  // offset
      A2.castTo("size_t"),    // size
      A3.castTo("int *")      // int[1] contains success=0 or error=-1
    )).ret());
    put("fuzion.sys.fileio.munmap", (c,cl,outer,in) -> CExpr.call("fzE_munmap", new List<CExpr>(
      A0.castTo("void *"),    // address
      A1.castTo("size_t")     // size
    )).ret());
    put("fuzion.sys.fileio.mapped_buffer_get", (c,cl,outer,in) -> A0.castTo("int8_t*").index(A1).ret());
    put("fuzion.sys.fileio.mapped_buffer_set", (c,cl,outer,in) -> A0.castTo("int8_t*").index(A1).assign(A2.castTo("int8_t")));
    put("fuzion.sys.fileio.open_dir", (c,cl,outer,in) -> CExpr.call("fzE_opendir", new List<CExpr>(
      A0.castTo("char *"),
      A1.castTo("int64_t *")
    )));
    put("fuzion.sys.fileio.read_dir", (c,cl,outer,in) ->
      {
        var d_name = new CIdent("d_name");
        var rc = c._fuir.clazzResultClazz(cl);
        return CStmnt.seq(
          CStmnt.decl("char *", d_name, CExpr.call("fzE_readdir", new List<>(A0.castTo("intptr_t *")))),
          CStmnt.iff(d_name.eq(new CIdent("NULL")), CStmnt.seq(
            c.heapClone(c.constString("error in read_dir encountered NULL pointer"), rc).ret())),
          c.heapClone(c.constString(d_name, CExpr.call("strlen", new List<>(d_name)).castTo("int")), rc).ret()
        );
      });
    put("fuzion.sys.fileio.read_dir_has_next", (c,cl,outer,in) -> {
      return CStmnt.iff(CExpr.call("fzE_read_dir_has_next", new List<>(A0.castTo("intptr_t *"))), c._names.FZ_FALSE.ret(),
        c._names.FZ_TRUE.ret());
    });
    put("fuzion.sys.fileio.close_dir", (c,cl,outer,in) -> CExpr.call("fzE_closedir", new List<>(A0.castTo("intptr_t *"))).ret());

    put("fuzion.sys.fileio.flush"      , (c,cl,outer,in) ->
      CExpr.call("fflush", new List<>(A0.castTo("FILE *"))).ret());

    put("fuzion.sys.fatal_fault0"      , (c,cl,outer,in) ->
        CStmnt.seq(CExpr.fprintfstderr("*** failed %s: `%s`\n", new CExpr[] {A0.castTo("char *"),
                                                                             A1.castTo("char *")}),
                   CExpr.exit(1)));

    put("fuzion.sys.stdin.stdin0"      , (c,cl,outer,in) ->
      new CIdent("stdin").castTo("fzT_1i64").ret());
    put("fuzion.sys.out.stdout"      , (c,cl,outer,in) ->
      new CIdent("stdout").castTo("fzT_1i64").ret());
    put("fuzion.sys.err.stderr"      , (c,cl,outer,in) ->
      new CIdent("stderr").castTo("fzT_1i64").ret());

    put("fuzion.sys.process.create", (c,cl,outer,in) ->
      CExpr.call("fzE_process_create", new List<>(
        // args
        A0.castTo("char **"),
        A1.castTo("size_t"),
        // env
        A2.castTo("char **"),
        A3.castTo("size_t"),
        // result
        A4.castTo("int64_t *"),
        // args as space separated string
        A5.castTo("char *"),
        // env vars as NULL separated string
        A6.castTo("char *")
        )).ret());

    put("fuzion.sys.process.wait", (c,cl,outer,in) ->
      CExpr.call("fzE_process_wait", new List<>(A0.castTo("int64_t"))).ret());

    put("fuzion.sys.pipe.read", (c,cl,outer,in) ->
      CExpr.call("fzE_pipe_read", new List<>(
        A0.castTo("int64_t") /* descriptor/handle */,
        A1.castTo("char *")  /* buffer */,
        A2.castTo("size_t")  /* buffer size */)).ret());

    put("fuzion.sys.pipe.write", (c,cl,outer,in) ->
      CExpr.call("fzE_pipe_write", new List<>(
        A0.castTo("int64_t") /* descriptor/handle */,
        A1.castTo("char *")  /* buffer */,
        A2.castTo("size_t")  /* buffer size */)).ret());

    put("fuzion.sys.pipe.close", (c,cl,outer,in) ->
      CExpr.call("fzE_pipe_close", new List<>(
        A0.castTo("int64_t") /* descriptor/handle */)).ret());

        /* NYI: The C standard does not guarantee wrap-around semantics for signed types, need
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
    /**
     * This is intentionally cast to u32 and not u16.
     * Read why here:
     * https://stackoverflow.com/questions/24371868/why-must-a-short-be-converted-to-an-int-before-arithmetic-operations-in-c-and-c
     * https://github.com/llvm/llvm-project/issues/25954
     * https://stackoverflow.com/questions/23994293/inconsistent-behaviour-of-implicit-conversion-between-unsigned-and-bigger-signed
     */
    put("i16.infix *°"         , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, outer, A0, '*', FUIR.SpecialClazzes.c_u32, FUIR.SpecialClazzes.c_i16).ret());
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

    put("i8.type.equality"     ,
        "i16.type.equality"    ,
        "i32.type.equality"    ,
        "i64.type.equality"    , (c,cl,outer,in) -> A0.eq(A1).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("i8.type.lteq"         ,
        "i16.type.lteq"        ,
        "i32.type.lteq"        ,
        "i64.type.lteq"        , (c,cl,outer,in) -> A0.le(A1).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());

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
        "u32.infix *°"         ,
        "u64.infix *°"         , (c,cl,outer,in) -> outer.mul(A0).ret());
    /**
     * This is intentionally cast to u32 and not u16.
     * Read why here:
     * https://stackoverflow.com/questions/24371868/why-must-a-short-be-converted-to-an-int-before-arithmetic-operations-in-c-and-c
     * https://github.com/llvm/llvm-project/issues/25954
     * https://stackoverflow.com/questions/23994293/inconsistent-behaviour-of-implicit-conversion-between-unsigned-and-bigger-signed
     */
    put("u16.infix *°"         , (c,cl,outer,in) -> castToUnsignedForArithmetic(c, outer, A0, '*', FUIR.SpecialClazzes.c_u32, FUIR.SpecialClazzes.c_i16).ret());
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

    put("u8.type.equality"     ,
        "u16.type.equality"    ,
        "u32.type.equality"    ,
        "u64.type.equality"    , (c,cl,outer,in) -> A0.eq(A1).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("u8.type.lteq"         ,
        "u16.type.lteq"        ,
        "u32.type.lteq"        ,
        "u64.type.lteq"        , (c,cl,outer,in) -> A0.le(A1).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());

    put("i8.as_i32"            , (c,cl,outer,in) -> outer.castTo("fzT_1i32").ret());
    put("i16.as_i32"           , (c,cl,outer,in) -> outer.castTo("fzT_1i32").ret());
    put("i32.as_i64"           , (c,cl,outer,in) -> outer.castTo("fzT_1i64").ret());
    put("u8.as_i32"            , (c,cl,outer,in) -> outer.castTo("fzT_1i32").ret());
    put("u16.as_i32"           , (c,cl,outer,in) -> outer.castTo("fzT_1i32").ret());
    put("u32.as_i64"           , (c,cl,outer,in) -> outer.castTo("fzT_1i64").ret());
    put("i8.cast_to_u8"        , (c,cl,outer,in) -> outer.castTo("fzT_1u8").ret());
    put("i16.cast_to_u16"      , (c,cl,outer,in) -> outer.castTo("fzT_1u16").ret());
    put("i32.cast_to_u32"      , (c,cl,outer,in) -> outer.castTo("fzT_1u32").ret());
    put("i64.cast_to_u64"      , (c,cl,outer,in) -> outer.castTo("fzT_1u64").ret());
    put("u8.cast_to_i8"        , (c,cl,outer,in) -> outer.castTo("fzT_1i8").ret());
    put("u16.cast_to_i16"      , (c,cl,outer,in) -> outer.castTo("fzT_1i16").ret());
    put("u32.cast_to_i32"      , (c,cl,outer,in) -> outer.castTo("fzT_1i32").ret());
    put("u32.cast_to_f32"      , (c,cl,outer,in) -> outer.adrOf().castTo("fzT_1f32*").deref().ret());
    put("u64.cast_to_i64"      , (c,cl,outer,in) -> outer.castTo("fzT_1i64").ret());
    put("u64.cast_to_f64"      , (c,cl,outer,in) -> outer.adrOf().castTo("fzT_1f64*").deref().ret());
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
    put("f32.infix ="          ,
        "f64.infix ="          , (c,cl,outer,in) -> outer.eq(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("f32.infix <="         ,
        "f64.infix <="         , (c,cl,outer,in) -> outer.le(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("f32.infix >="         ,
        "f64.infix >="         , (c,cl,outer,in) -> outer.ge(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("f32.infix <"          ,
        "f64.infix <"          , (c,cl,outer,in) -> outer.lt(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
    put("f32.infix >"          ,
        "f64.infix >"          , (c,cl,outer,in) -> outer.gt(A0).cond(c._names.FZ_TRUE, c._names.FZ_FALSE).ret());
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
    put("f32.cast_to_u32"      , (c,cl,outer,in) -> outer.adrOf().castTo("fzT_1u32*").deref().ret());
    put("f64.cast_to_u64"      , (c,cl,outer,in) -> outer.adrOf().castTo("fzT_1u64*").deref().ret());

    /* The C standard library follows the convention that floating-point numbers x × 2exp have 0.5 ≤ x < 1,
     * while the IEEE 754 standard text uses the convention 1 ≤ x < 2.
     * This convention in C is not just used for DBL_MAX_EXP, but also for functions such as frexp.
     * source: https://github.com/rust-lang/rust/issues/88734
     */
    put("f32.type.min_exp"     , (c,cl,outer,in) -> CExpr.ident("FLT_MIN_EXP").sub(new CIdent("1")).ret());
    put("f32.type.max_exp"     , (c,cl,outer,in) -> CExpr.ident("FLT_MAX_EXP").sub(new CIdent("1")).ret());
    put("f32.type.min_positive", (c,cl,outer,in) -> CExpr.ident("FLT_MIN").ret());
    put("f32.type.max"         , (c,cl,outer,in) -> CExpr.ident("FLT_MAX").ret());
    put("f32.type.epsilon"     , (c,cl,outer,in) -> CExpr.ident("FLT_EPSILON").ret());
    put("f64.type.min_exp"     , (c,cl,outer,in) -> CExpr.ident("DBL_MIN_EXP").sub(new CIdent("1")).ret());
    put("f64.type.max_exp"     , (c,cl,outer,in) -> CExpr.ident("DBL_MAX_EXP").sub(new CIdent("1")).ret());
    put("f64.type.min_positive", (c,cl,outer,in) -> CExpr.ident("DBL_MIN").ret());
    put("f64.type.max"         , (c,cl,outer,in) -> CExpr.ident("DBL_MAX").ret());
    put("f64.type.epsilon"     , (c,cl,outer,in) -> CExpr.ident("DBL_EPSILON").ret());
    put("f32.is_NaN"           ,
        "f64.is_NaN"           , (c,cl,outer,in) -> CStmnt.seq(CStmnt.iff(CExpr.call("isnan", new List<>(outer)).ne(new CIdent("0")),
                                                                          c._names.FZ_TRUE.ret()
                                                                          ),
                                                               c._names.FZ_FALSE.ret()
                                                               ));
    put("f32.square_root"      , (c,cl,outer,in) -> CExpr.call("sqrtf", new List<>(outer)).ret());
    put("f64.square_root"      , (c,cl,outer,in) -> CExpr.call("sqrt",  new List<>(outer)).ret());
    put("f32.exp"              , (c,cl,outer,in) -> CExpr.call("expf",  new List<>(outer)).ret());
    put("f64.exp"              , (c,cl,outer,in) -> CExpr.call("exp",   new List<>(outer)).ret());
    put("f32.log"              , (c,cl,outer,in) -> CExpr.call("logf",  new List<>(outer)).ret());
    put("f64.log"              , (c,cl,outer,in) -> CExpr.call("log",   new List<>(outer)).ret());
    put("f32.sin"              , (c,cl,outer,in) -> CExpr.call("sinf",  new List<>(outer)).ret());
    put("f64.sin"              , (c,cl,outer,in) -> CExpr.call("sin",   new List<>(outer)).ret());
    put("f32.cos"              , (c,cl,outer,in) -> CExpr.call("cosf",  new List<>(outer)).ret());
    put("f64.cos"              , (c,cl,outer,in) -> CExpr.call("cos",   new List<>(outer)).ret());
    put("f32.tan"              , (c,cl,outer,in) -> CExpr.call("tanf",  new List<>(outer)).ret());
    put("f64.tan"              , (c,cl,outer,in) -> CExpr.call("tan",   new List<>(outer)).ret());
    put("f32.asin"             , (c,cl,outer,in) -> CExpr.call("asinf", new List<>(outer)).ret());
    put("f64.asin"             , (c,cl,outer,in) -> CExpr.call("asin",  new List<>(outer)).ret());
    put("f32.acos"             , (c,cl,outer,in) -> CExpr.call("acosf", new List<>(outer)).ret());
    put("f64.acos"             , (c,cl,outer,in) -> CExpr.call("acos",  new List<>(outer)).ret());
    put("f32.atan"             , (c,cl,outer,in) -> CExpr.call("atanf", new List<>(outer)).ret());
    put("f64.atan"             , (c,cl,outer,in) -> CExpr.call("atan",  new List<>(outer)).ret());
    put("f32.sinh"             , (c,cl,outer,in) -> CExpr.call("sinhf", new List<>(outer)).ret());
    put("f64.sinh"             , (c,cl,outer,in) -> CExpr.call("sinh",  new List<>(outer)).ret());
    put("f32.cosh"             , (c,cl,outer,in) -> CExpr.call("coshf", new List<>(outer)).ret());
    put("f64.cosh"             , (c,cl,outer,in) -> CExpr.call("cosh",  new List<>(outer)).ret());
    put("f32.tanh"             , (c,cl,outer,in) -> CExpr.call("tanhf", new List<>(outer)).ret());
    put("f64.tanh"             , (c,cl,outer,in) -> CExpr.call("tanh",  new List<>(outer)).ret());

    put("fuzion.sys.internal_array_init.alloc", (c,cl,outer,in) ->
        {
          var gc = c._fuir.clazzActualGeneric(cl, 0);
          return CExpr.call(c.malloc(),
                            new List<>(CExpr.sizeOfType(c._types.clazz(gc)).mul(A0))).ret();
        });
    put("fuzion.sys.internal_array.setel", (c,cl,outer,in) ->
        {
          var gc = c._fuir.clazzActualGeneric(cl, 0);
          return c._fuir.hasData(gc)
            ? A0.castTo(c._types.clazz(gc) + "*").index(A1).assign(A2)
            : CStmnt.EMPTY;
        });
    put("fuzion.sys.internal_array.get", (c,cl,outer,in) ->
        {
          var gc = c._fuir.clazzActualGeneric(cl, 0);
          return c._fuir.hasData(gc)
            ? A0.castTo(c._types.clazz(gc) + "*").index(A1).ret()
            : CStmnt.EMPTY;
        });
    put("fuzion.sys.internal_array.freeze", (c,cl,outer,in) ->
        {
          return CStmnt.EMPTY;
        });
    put("fuzion.sys.internal_array.ensure_not_frozen", (c,cl,outer,in) ->
        {
          return CStmnt.EMPTY;
        });
    put("fuzion.sys.env_vars.has0", (c,cl,outer,in) ->
        {
          return CStmnt.seq(CStmnt.iff(CExpr.call("getenv",new List<>(A0.castTo("char*"))).ne(CNames.NULL),
                                       c._names.FZ_TRUE.ret()),
                            c._names.FZ_FALSE.ret());
        });
    put("fuzion.sys.env_vars.get0", (c,cl,outer,in) ->
        {
          var str = new CIdent("str");
          var rc = c._fuir.clazzResultClazz(cl);
          return CStmnt.seq(CStmnt.decl("char *", str),
                            str.assign(CExpr.call("getenv",new List<>(A0.castTo("char*")))),
                            c.heapClone(c.constString(str, CExpr.call("strlen",new List<>(str))), rc).ret());
        });
    put("fuzion.sys.env_vars.set0", (c,cl,outer,in) ->
        {
          return CStmnt.seq(CStmnt.iff(CExpr.call("fzE_setenv",new List<>(A0.castTo("char*") /* name */,
                                                                      A1.castTo("char*") /* value */,
                                                                      CExpr.int32const(1) /* overwrite */))
                                            .eq(CExpr.int32const(0)),
                                       c._names.FZ_TRUE.ret()),
                            c._names.FZ_FALSE.ret());
        });
     put("fuzion.sys.env_vars.unset0", (c,cl,outer,in) ->
        {
          return CStmnt.seq(CStmnt.iff(CExpr.call("fzE_unsetenv",new List<>(A0.castTo("char*") /* name */))
                                            .eq(CExpr.int32const(0)),
                                       c._names.FZ_TRUE.ret()),
                            c._names.FZ_FALSE.ret());
        });
     put("fuzion.sys.misc.unique_id",(c,cl,outer,in) ->
        {
          var last_id = new CIdent("last_id");
          return CStmnt.seq(CStmnt.decl("static",
                                        "atomic_uint_least64_t",
                                        last_id,
                                        CExpr.uint64const(0)),
                            last_id.incr().ret());
        });
     put("fuzion.sys.thread.spawn0", (c,cl,outer,in) ->
        {
          var oc = c._fuir.clazzActualGeneric(cl, 0);
          var call = c._fuir.lookupCall(oc);
          if (c._fuir.clazzNeedsCode(call))
            {
              var arg = new CIdent("arg");
              return CStmnt.seq(
                                CExpr.decl("struct " + CNames.fzThreadStartRoutineArg.code() + "*", arg),

                                arg.assign(CExpr.call(c.malloc(), new List<>(CExpr.sizeOfType("struct " + CNames.fzThreadStartRoutineArg.code())))),

                                arg.deref().field(CNames.fzThreadStartRoutineArgFun).assign(CExpr.ident(c._names.function(call)).adrOf().castTo("void *")),
                                arg.deref().field(CNames.fzThreadStartRoutineArgArg).assign(A0.castTo("void *")),

                                CExpr.call("fzE_thread_create", new List<>(CNames.fzThreadStartRoutine.adrOf(), arg)).ret());
            }
          else
            {
              return CStmnt.EMPTY;
            }
        });
    put("fuzion.sys.thread.join0", (c,cl,outer,in) ->
    {
      return CExpr.call("fzE_thread_join", new List<>(A0));
    });
    put("fuzion.std.nano_time", (c,cl,outer,in) -> CExpr.call("fzE_nanotime", new List<>()).ret());
    put("fuzion.std.nano_sleep", (c,cl,outer,in) -> CExpr.call("fzE_nanosleep", new List<>(A0)));


    put("fuzion.std.date_time", (c,cl,outer,in) ->
      {
        var rawTime = new CIdent("rawtime");
        var ptm = new CIdent("ptm");

        return CStmnt.seq(
            CStmnt.decl("time_t", rawTime),
            CExpr.call("time", new List<>(rawTime.adrOf())),
            CStmnt.decl("struct tm *", ptm, CExpr.call("gmtime", new List<>(rawTime.adrOf()))),
            A0.castTo("fzT_1i32 *").index(0).assign(ptm.deref().field(new CIdent("tm_year")).add(CExpr.int32const(1900))),
            A0.castTo("fzT_1i32 *").index(1).assign(ptm.deref().field(new CIdent("tm_yday")).add(CExpr.int32const(1))),
            A0.castTo("fzT_1i32 *").index(2).assign(ptm.deref().field(new CIdent("tm_hour"))),
            A0.castTo("fzT_1i32 *").index(3).assign(ptm.deref().field(new CIdent("tm_min"))),
            A0.castTo("fzT_1i32 *").index(4).assign(ptm.deref().field(new CIdent("tm_sec"))),
            A0.castTo("fzT_1i32 *").index(5).assign(CExpr.int32const(0)));
      });


    put("fuzion.sys.net.bind0",    (c,cl,outer,in) ->
      CExpr.call("fzE_bind", new List<CExpr>(
        A0.castTo("int"),       // family
        A1.castTo("int"),       // socktype
        A2.castTo("int"),       // protocol
        A3.castTo("char *"),    // host
        A4.castTo("char *"),    // port
        A5.castTo("int64_t *")  // result
    )).ret());

    put("fuzion.sys.net.listen",  (c,cl,outer,in) -> CExpr.call("fzE_listen", new List<CExpr>(
      A0.castTo("int"), // socket descriptor
      A1.castTo("int")  // size of backlog
    )).ret());

    put("fuzion.sys.net.accept",  (c,cl,outer,in) -> assignNetErrorOnError(c, CExpr.call("fzE_accept", new List<CExpr>(
      A0.castTo("int") // socket descriptor
    )), A1));

    put("fuzion.sys.net.connect0",    (c,cl,outer,in) ->
    CExpr.call("fzE_connect", new List<CExpr>(
      A0.castTo("int"),       // family
      A1.castTo("int"),       // socktype
      A2.castTo("int"),       // protocol
      A3.castTo("char *"),    // host
      A4.castTo("char *"),    // port
      A5.castTo("int64_t *")  // result (err or descriptor)
    )).ret());

    put("fuzion.sys.net.get_peer_address", (c,cl,outer,in) ->
      CExpr.call("fzE_get_peer_address", new List<>(A0.castTo("int"), A1.castTo("void *"))).castTo("fzT_1i32").ret()
    );

    put("fuzion.sys.net.get_peer_port", (c,cl,outer,in) ->
      CExpr.call("fzE_get_peer_port", new List<>(A0.castTo("int"))).castTo("fzT_1u16").ret()
    );

    put("fuzion.sys.net.read", (c,cl,outer,in) -> assignNetErrorOnError(c, CExpr.call("fzE_read", new List<CExpr>(
      A0.castTo("int"),    // socket descriptor
      A1.castTo("void *"), // buffer
      A2.castTo("size_t")  // buffer length
    )), A3));

    put("fuzion.sys.net.write", (c,cl,outer,in) -> CExpr.call("fzE_write", new List<CExpr>(
      A0.castTo("int"),    // socket descriptor
      A1.castTo("void *"), // buffer
      A2.castTo("size_t")  // buffer length
    )).ret());

    put("fuzion.sys.net.close0", (c,cl,outer,in) -> CExpr.call("fzE_close", new List<CExpr>(
      A0.castTo("int") // socket descriptor
    )).ret());

    put("fuzion.sys.net.set_blocking0", (c,cl,outer,in) -> CExpr.call("fzE_set_blocking", new List<CExpr>(
      A0.castTo("int"), // socket descriptor
      A1.castTo("int")  // blocking
    )).ret());


    put("effect.type.abort0"     ,
        "effect.type.default0"   ,
        "effect.type.instate0"   ,
        "effect.type.is_instated0",
        "effect.type.replace0"   , (c,cl,outer,in) ->
        {
          var ecl = c._fuir.effectTypeFromInstrinsic(cl);
          var eid = c._fuir.clazzId2num(ecl) + 1; // must be != 0 since setjmp uses 0 for the normal return case, so we add `1`:
          var ev  = CNames.fzThreadEffectsEnvironment.deref().field(c._names.env(ecl));           // installed effect value
          var evi = CNames.fzThreadEffectsEnvironment.deref().field(c._names.envInstalled(ecl));  // isInstalled flag
          var evj = CNames.fzThreadEffectsEnvironment.deref().field(c._names.envJmpBuf());        // current jump buffer
          var e   = A0;
          var effect_is_unit_type = c._fuir.clazzIsUnitType(ecl);
          return
            switch (in)
              {
              case "effect.type.abort0"        ->
                CStmnt.seq(CStmnt.iff(evi, CExpr.call("longjmp",new List<>(evj.deref(), CExpr.int32const(eid)))),
                           CExpr.fprintfstderr("*** abort called for effect `%s` that is not instated!\n",
                                               CExpr.string(c._fuir.clazzAsString(ecl))),
                           CExpr.exit(1));
              case "effect.type.default0"     -> CStmnt.iff(evi.not(), CStmnt.seq(effect_is_unit_type ? CExpr.UNIT : ev.assign(e),
                                                                                  evi.assign(CIdent.TRUE )));
              case "effect.type.instate0"     ->
                {
                  var call     = c._fuir.lookupCall(c._fuir.clazzActualGeneric(cl, 0));
                  var call_def = c._fuir.lookupCall(c._fuir.clazzActualGeneric(cl, 1));
                  var finallie = c._fuir.lookup_static_finally(ecl);
                  if (c._fuir.clazzNeedsCode(call))
                    {
                      var jmpbuf = new CIdent("jmpbuf");
                      var oldev  = new CIdent("old_ev");
                      var oldevi = new CIdent("old_evi");
                      var oldevj = new CIdent("old_evj");
                      var cureff  = new CIdent("cur_eff");
                      var cureff_as_target = c._fuir.clazzIsRef(ecl) ? cureff : cureff.adrOf();
                      var setjmp_result = new CIdent("setjmp_res");

                      var pass_on = CStmnt.iff(setjmp_result.ne(CExpr.int32const(0)), CExpr.call("longjmp",new List<>(evj.deref(), setjmp_result)));

                      yield CStmnt.seq(// copy previously instated effect values:
                                       effect_is_unit_type ? CExpr.UNIT : CStmnt.decl(c._types.clazz(ecl), oldev , ev ),
                                       CStmnt.decl("bool"             , oldevi, evi),
                                       CStmnt.decl("jmp_buf*"         , oldevj, evj),

                                       // declare jumpbuf related local vars
                                       CStmnt.decl("jmp_buf", jmpbuf),
                                       CStmnt.decl("int", setjmp_result),

                                       // instate effect
                                       effect_is_unit_type ? CExpr.UNIT : ev.assign(e),
                                       evi.assign(CIdent.TRUE ),
                                       evj.assign(jmpbuf.adrOf()),
                                       setjmp_result.assign(CExpr.call("setjmp",new List<>(jmpbuf))),

                                       // setjmp returns 0 originally, so we run the code in `call`:
                                       CStmnt.iff(setjmp_result.eq(CExpr.int32const(0)),
                                                  CStmnt.seq(CExpr.call(c._names.function(call), new List<>(A1.adrOf())))),

                                       // remove the instated effect and call finally:
                                       effect_is_unit_type ? CExpr.UNIT : CStmnt.decl(c._types.clazz(ecl), cureff , ev ),
                                       effect_is_unit_type ? CExpr.UNIT : ev .assign(oldev ),
                                       evi.assign(oldevi),
                                       evj.assign(oldevj),
                                       CExpr.call(c._names.function(finallie),
                                                  effect_is_unit_type
                                                  ? new List<>()
                                                  : new List<>(cureff_as_target)),

                                       c._fuir.clazzNeedsCode(call_def)
                                       ? // if setjmp returned with an abort for this effect (==eid), run `call_def`
                                         CStmnt.iff(setjmp_result.eq(CExpr.int32const(eid)),
                                                  CExpr.call(c._names.function(call_def),
                                                             effect_is_unit_type
                                                             ? new List<>(A2.adrOf())
                                                             : new List<>(A2.adrOf(), cureff)
                                                             ),
                                                  // else, if setjmp returned with an abort for a different effect, propagate it further
                                                  pass_on
                                                  )
                                       : pass_on  // in case call_def was detected not to be called by DFA we can pass on the abort directly
                                       );
                    }
                  else
                    {
                      yield CStmnt.seq(CExpr.fprintfstderr("*** C backend no code for class '%s'\n",
                                                           CExpr.string(c._fuir.clazzAsString(call))),
                                       CExpr.call("exit", new List<>(CExpr.int32const(1))));
                    }
                }
              case "effect.type.is_instated0" -> CStmnt.seq(CStmnt.iff(evi, c._names.FZ_TRUE.ret()), c._names.FZ_FALSE.ret());
              case "effect.type.replace0"     -> c._fuir.clazzIsUnitType(ecl) ? CExpr.UNIT : ev.assign(e);
              default -> throw new Error("unexpected intrinsic '" + in + "'.");
              };
        });

    var noJava = CStmnt.seq(
                 CExpr.fprintfstderr("*** Set environment variable JAVA_HOME when compiling to be able to use intrinsics fuzion.java.*.\n"),
                 CExpr.fprintfstderr("*** Example: JAVA_HOME=/usr/lib/jvm/java-" + Version.JAVA_VERSION + "-openjdk-amd64 fz -c file.fz\n"),
                 CExpr.exit(1));
    put("fuzion.java.Java_Object.is_null0", (c, cl, outer, in) -> C.JAVA_HOME == null
                                                                                       ? noJava
                                                                                       : CExpr.call(
                                                                                         "fzE_java_object_is_null",
                                                                                         new List<CExpr>(outer.field(
                                                                                           c._names.fieldName(c._fuir
                                                                                             .clazz_fuzionJavaObject_Ref()))
                                                                                           .castTo("jobject")))
                                                                                         .cond(c._names.FZ_TRUE,
                                                                                           c._names.FZ_FALSE)
                                                                                         .ret());
    put("fuzion.java.array_get"             , (c, cl, outer, in) -> {
      if (C.JAVA_HOME == null)
        {
          return noJava;
        }
      else
        {
          return c.returnJavaObject(c._fuir.clazzResultClazz(cl), CExpr
            .call("fzE_array_get",
              new List<CExpr>(
                c.javaRefField(A0).castTo("jarray"),
                A1,
                A2.castTo("char *"))),
            false);
        }
    });
    put("fuzion.java.array_length"          , (c,cl,outer,in) -> C.JAVA_HOME == null ? noJava : CExpr.call("fzE_array_length", new List<>(c.javaRefField(A0).castTo("jarray"))).ret());
    put("fuzion.java.array_to_java_object0", (c, cl, outer, in) -> {
      if (C.JAVA_HOME == null)
        {
          return noJava;
        }
      else
        {
          var internalArray = c._fuir.clazzArgClazz(cl, 0);
          var data   = c._fuir.lookup_fuzion_sys_internal_array_data  (internalArray);
          var length = c._fuir.lookup_fuzion_sys_internal_array_length(internalArray);
          var elementType = c._fuir.clazzActualGeneric(c._fuir.clazzResultClazz(cl), 0);
          var elements = c._names.newTemp();
          return CStmnt
            .seq(
              c._fuir.getSpecialClazz(elementType) == SpecialClazzes.c_NOT_FOUND
                                                                              ? c.extractJValues(elements, A0)
                                                                              : CStmnt.EMPTY,
              c.returnJavaObject(c._fuir.clazzResultClazz(cl), CExpr
                .call("fzE_array_to_java_object0",
                  new List<CExpr>(
                    A0.field(c._names.fieldName(length)),
                    c._fuir.getSpecialClazz(
                      elementType) == SpecialClazzes.c_NOT_FOUND
                                                                 ? elements
                                                                 : A0.field(c._names
                                                                   .fieldName(data))
                                                                   .castTo("jvalue *"),
                    CExpr.string(javaSignature(c._fuir, elementType)))), false));
        }
    });
    put("fuzion.java.get_field0",
      (c, cl, outer, in) ->
        C.JAVA_HOME == null
          ? noJava
          : c.returnJavaObject(c._fuir.clazzResultClazz(cl), CExpr
              .call("fzE_get_field0",
                new List<>(c.javaRefField(A0).castTo("jobject"),
                  c.javaRefField(A1).castTo("jstring"),
                  A2.castTo("char *"))),
            false));
    put("fuzion.java.get_static_field0",
      (c, cl, outer, in) ->
        C.JAVA_HOME == null
          ? noJava
          : c.returnJavaObject(c._fuir.clazzResultClazz(cl), CExpr
              .call("fzE_get_static_field0",
                new List<>(c.javaRefField(A0).castTo("jstring"),
                  c.javaRefField(A1).castTo("jstring"),
                  A2.castTo("char *"))),
              false));
    put("fuzion.java.set_field0",
      (c, cl, outer, in) ->
        C.JAVA_HOME == null
          ? noJava
          : CExpr
              .call("fzE_set_field0",
                new List<>(c.javaRefField(A0).castTo("jobject"),
                  c.javaRefField(A1).castTo("jstring"),
                  c.javaRefField(A2).castTo("jvalue"),
                  A3.castTo("char *"))));
    put("fuzion.java.set_static_field0",
      (c, cl, outer, in) ->
        C.JAVA_HOME == null
          ? noJava
          : CExpr
              .call("fzE_set_static_field0",
                new List<>(c.javaRefField(A0).castTo("jstring"),
                  c.javaRefField(A1).castTo("jstring"),
                  c.javaRefField(A2).castTo("jvalue"),
                  A3.castTo("char *"))));
    put("fuzion.java.call_c0", (c, cl, outer, in) -> {
      if (C.JAVA_HOME == null)
        {
          return noJava;
        }
      else
        {
          var internalArray = c._fuir.clazzArgClazz(cl, 2);
          var data          = c._fuir.lookup_fuzion_sys_internal_array_data(internalArray);
          return CStmnt
            .seq(c.returnJavaObject(c._fuir.clazzResultClazz(cl),
              CExpr
                .call("fzE_call_c0",
                  new List<CExpr>(
                    c.javaRefField(A0).castTo("jstring"),
                    c.javaRefField(A1).castTo("jstring"),
                    A2.field(c._names.fieldName(data)).castTo("jvalue *"))), true));
        }
    });
    put("fuzion.java.cast0", (c, cl, outer, in) -> {
      if (C.JAVA_HOME == null)
        {
          return noJava;
        }
      else
        {
          return c.returnJavaObject(c._fuir.clazzResultClazz(cl), CExpr.compoundLiteral("jvalue", ".l="+A0.castTo("jobject").code()), false);
        }
    });
    put("fuzion.java.call_s0", (c, cl, outer, in) -> {
      if (C.JAVA_HOME == null)
        {
          return noJava;
        }
      else
        {
          var internalArray = c._fuir.clazzArgClazz(cl, 3);
          var data          = c._fuir.lookup_fuzion_sys_internal_array_data(internalArray);
          return CStmnt
            .seq(
              // NYI methods where result clazz is e.g. unit, f64 etc. that does
              // not inherit Java_Object or Java_String
              c.returnJavaObject(c._fuir.clazzResultClazz(cl), CExpr
                .call("fzE_call_s0",
                  new List<CExpr>(
                    c.javaRefField(A0).castTo("jstring"),
                    c.javaRefField(A1).castTo("jstring"),
                    c.javaRefField(A2).castTo("jstring"),
                    A3.field(c._names.fieldName(data)).castTo("jvalue *"))), true));
        }
    });
    put("fuzion.java.call_v0", (c, cl, outer, in) -> {
      var internalArray = c._fuir.clazzArgClazz(cl, 4);
      var data          = c._fuir.lookup_fuzion_sys_internal_array_data(internalArray);
      if (C.JAVA_HOME == null)
        {
          return noJava;
        }
      else
        {
          return CStmnt
            .seq(
              c.returnJavaObject(c._fuir.clazzResultClazz(cl), CExpr
                .call("fzE_call_v0",
                  new List<CExpr>(
                    c.javaRefField(A0).castTo("jstring"),
                    c.javaRefField(A1).castTo("jstring"),
                    c.javaRefField(A2).castTo("jstring"),
                    A3.castTo("jobject"),
                    A4.field(c._names.fieldName(data)).castTo("jvalue *"))), true));
        }
    });
    put("fuzion.java.bool_to_java_object",
      (c, cl, outer, in) -> C.JAVA_HOME == null
                                                  ? noJava
                                                  : c
                                                    .returnJavaObject(c._fuir.clazz_fuzionJavaObject(),
                                                      CExpr.call("fzE_bool_to_java_object", new List<CExpr>(A0.field(CNames.TAG_NAME))), false));
    put("fuzion.java.f32_to_java_object",
      (c, cl, outer, in) -> C.JAVA_HOME == null
                                                  ? noJava
                                                  : c
                                                    .returnJavaObject(c._fuir.clazz_fuzionJavaObject(),
                                                      CExpr.call("fzE_f32_to_java_object", new List<CExpr>(A0)), false));
    put("fuzion.java.f64_to_java_object",
      (c, cl, outer, in) -> C.JAVA_HOME == null
                                                  ? noJava
                                                  : c
                                                    .returnJavaObject(c._fuir.clazz_fuzionJavaObject(),
                                                      CExpr.call("fzE_f64_to_java_object", new List<CExpr>(A0)), false));
    put("fuzion.java.i8_to_java_object",
      (c, cl, outer, in) -> C.JAVA_HOME == null
                                                  ? noJava
                                                  : c
                                                    .returnJavaObject(c._fuir.clazz_fuzionJavaObject(),
                                                      CExpr.call("fzE_i8_to_java_object", new List<CExpr>(A0)), false));
    put("fuzion.java.i16_to_java_object",
      (c, cl, outer, in) -> C.JAVA_HOME == null
                                                  ? noJava
                                                  : c
                                                    .returnJavaObject(c._fuir.clazz_fuzionJavaObject(),
                                                      CExpr.call("fzE_i16_to_java_object", new List<CExpr>(A0)), false));
    put("fuzion.java.i32_to_java_object",
      (c, cl, outer, in) -> C.JAVA_HOME == null
                                                  ? noJava
                                                  : c
                                                    .returnJavaObject(c._fuir.clazz_fuzionJavaObject(),
                                                      CExpr.call("fzE_i32_to_java_object", new List<CExpr>(A0)), false));
    put("fuzion.java.i64_to_java_object",
      (c, cl, outer, in) -> C.JAVA_HOME == null
                                                  ? noJava
                                                  : c
                                                    .returnJavaObject(c._fuir.clazz_fuzionJavaObject(),
                                                      CExpr.call("fzE_i64_to_java_object", new List<CExpr>(A0)), false));
    put("fuzion.java.u16_to_java_object",
      (c, cl, outer, in) -> C.JAVA_HOME == null
                                                  ? noJava
                                                  : c
                                                    .returnJavaObject(c._fuir.clazz_fuzionJavaObject(),
                                                      CExpr.call("fzE_u16_to_java_object", new List<CExpr>(A0)), false));
    put("fuzion.java.java_string_to_string" , (c,cl,outer,in) ->
        {
          if (C.JAVA_HOME == null)
            {
              return noJava;
            }
          else
            {
              var tmp = new CIdent("tmp");
              var rc = c._fuir.clazzResultClazz(cl);
              return CStmnt.seq(CStmnt.decl("const char *", tmp),
                         tmp.assign(CExpr.call("fzE_java_string_to_utf8_bytes", new List<CExpr>(A0.castTo("jstring")))),
                         c.heapClone(c.constString(tmp, CExpr.call("strlen",new List<>(tmp))), c._fuir.clazz_Const_String())
                          .castTo(c._types.clazz(rc))
                          .ret());
            }
        });
      put("fuzion.java.string_to_java_object0", (c,cl,outer,in) -> {
          var internalArray = c._fuir.clazzArgClazz(cl, 0);
          var data          = c._fuir.lookup_fuzion_sys_internal_array_data  (internalArray);
          var length        = c._fuir.lookup_fuzion_sys_internal_array_length(internalArray);
          return C.JAVA_HOME == null
            ? noJava
            : c.returnJavaObject(c._fuir.clazz_fuzionJavaObject(), CExpr
                .call("fzE_string_to_java_object", new List<CExpr>(
                  A0.field(c._names.fieldName(data)),
                  A0.field(c._names.fieldName(length))
                  )), false);
        });


    put("fuzion.java.create_jvm", (c,cl,outer,in) -> {
      return  C.JAVA_HOME == null
        ? noJava
        : CExpr.call("fzE_create_jvm", new List<>(A0.castTo("char *")));
    });
    // NYI: UNDER DEVELOPMENT: put("fuzion.java.destroy_jvm", (c,cl,outer,in) -> {});

    put("concur.sync.mtx_init",      (c,cl,outer,in) ->
      {
        var tmp = new CIdent("tmp");
        var rc = c._fuir.clazzResultClazz(cl);
        return CStmnt.seq(
          CStmnt.decl("void *", tmp, CExpr.call("fzE_mtx_init", new List<>())),
          CStmnt.iff(tmp.eq(CNames.NULL),
            c.returnOutcome(c._fuir.clazz_error(), c.error(c.constString("An error occurred initializing the mutex.")), rc, 1),
            c.returnOutcome(c._fuir.clazz(SpecialClazzes.c_sys_ptr), tmp, rc , 0)
          )
        );
      }
    );
    put("concur.sync.mtx_lock",      (c,cl,outer,in) -> CStmnt.iff(CExpr.call("fzE_mtx_lock",      new List<>(A0)).eq(new CIdent("0")), c._names.FZ_TRUE.ret(), c._names.FZ_FALSE.ret()));
    put("concur.sync.mtx_trylock",   (c,cl,outer,in) -> CStmnt.iff(CExpr.call("fzE_mtx_trylock",   new List<>(A0)).eq(new CIdent("0")), c._names.FZ_TRUE.ret(), c._names.FZ_FALSE.ret()));
    put("concur.sync.mtx_unlock",    (c,cl,outer,in) -> CStmnt.iff(CExpr.call("fzE_mtx_unlock",    new List<>(A0)).eq(new CIdent("0")), c._names.FZ_TRUE.ret(), c._names.FZ_FALSE.ret()));
    put("concur.sync.mtx_destroy",   (c,cl,outer,in) -> CExpr.call("fzE_mtx_destroy",   new List<>(A0)));
    put("concur.sync.cnd_init",      (c,cl,outer,in) ->
      {
        var tmp = new CIdent("tmp");
        var rc = c._fuir.clazzResultClazz(cl);
        return CStmnt.seq(
          CStmnt.decl("void *", tmp, CExpr.call("fzE_cnd_init",      new List<>())),
          CStmnt.iff(tmp.eq(CNames.NULL),
            c.returnOutcome(c._fuir.clazz_error(), c.error(c.constString("An error occurred initializing the condition variable.")), rc, 1),
            c.returnOutcome(c._fuir.clazz(SpecialClazzes.c_sys_ptr), tmp, rc , 0)
          )
        );
      }
    );
    put("concur.sync.cnd_signal",    (c,cl,outer,in) -> CStmnt.iff(CExpr.call("fzE_cnd_signal",    new List<>(A0)).eq(new CIdent("0")), c._names.FZ_TRUE.ret(), c._names.FZ_FALSE.ret()));
    put("concur.sync.cnd_broadcast", (c,cl,outer,in) -> CStmnt.iff(CExpr.call("fzE_cnd_broadcast", new List<>(A0)).eq(new CIdent("0")), c._names.FZ_TRUE.ret(), c._names.FZ_FALSE.ret()));
    put("concur.sync.cnd_wait",      (c,cl,outer,in) -> CStmnt.iff(CExpr.call("fzE_cnd_wait",      new List<>(A0, A1)).eq(new CIdent("0")), c._names.FZ_TRUE.ret(), c._names.FZ_FALSE.ret()));
    put("concur.sync.cnd_destroy",   (c,cl,outer,in) -> CExpr.call("fzE_cnd_destroy",   new List<>(A0)));
  }


  /*----------------------------  variables  ----------------------------*/


  TreeSet<String> _usedIntrinsics = new TreeSet<>();


  /*-------------------------  static methods  --------------------------*/


  private static void put(String n, IntrinsicCode c) { _intrinsics_.put(n, c); }
  private static void put(String n1, String n2, IntrinsicCode c) { put(n1, c); put(n2, c); }
  private static void put(String n1, String n2, String n3, IntrinsicCode c) { put(n1, c); put(n2, c); put(n3, c); }
  private static void put(String n1, String n2, String n3, String n4, IntrinsicCode c) { put(n1, c); put(n2, c); put(n3, c); put(n4, c); }
  private static void put(String n1, String n2, String n3, String n4, String n5, IntrinsicCode c) { put(n1, c); put(n2, c); put(n3, c); put(n4, c); put(n5, c); }


  /**
   * Get the names of all intrinsics supported by this backend.
   */
  public static Set<String> supportedIntrinsics()
  {
    return _intrinsics_.keySet();
  }


  /**
   * get the java signature for a given primitive element type.
   */
  private static String javaSignature(FUIR fuir, int elementType)
  {
    switch (fuir.getSpecialClazz(elementType))
      {
      case c_bool :
        return "Z";
      case c_f32 :
        return "F";
      case c_f64 :
        return "D";
      case c_i16 :
        return "S";
      case c_i32 :
        return "I";
      case c_i64 :
        return "J";
      case c_i8 :
        return "B";
      case c_u16 :
        return "C";
      default:
        return "NOT_A_PRIMITIVE";
      }
  }


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
      c._fuir.clazzFieldIsAdrOfValue(or)               ? CNames.OUTER.deref() :
      c._fuir.clazzIsRef(c._fuir.clazzResultClazz(or)) ? CNames.OUTER.deref().field(CNames.FIELDS_IN_REF_CLAZZ)
                                                       : CNames.OUTER;

    var in = c._fuir.clazzOriginalName(cl);
    var cg = _intrinsics_.get(in);
    var result = CStmnt.EMPTY;
    if (cg != null)
      {
        _usedIntrinsics.add(in);
        result = cg.get(c, cl, outer, in);
      }
    else
      {
        var at = c._fuir.clazzTypeParameterActualType(cl);
        if (at >= 0)
          {
            // intrinsic is a type parameter, type instances are unit types, so nothing to be done:
            result = CStmnt.EMPTY;
          }
        else
          {
            var msg = "code for intrinsic " + c._fuir.clazzOriginalName(cl) + " is missing";
            Errors.warning(msg);
            result = CStmnt.seq(CExpr.call("fprintf",
                                           new List<>(new CIdent("stderr"),
                                                      CExpr.string("*** error: NYI: %s\n"),
                                                      CExpr.string(msg))),
                                CExpr.call("exit", new List<>(CExpr.int32const(1))));
          }
      }

    return result;
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
    var ut = CTypes.scalar(unsigned);
    var st = CTypes.scalar(signed  );

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


  /**
   * if result of expr is -1 return false and assign
   * the result fzE_net_error to res[0]
   * else return true and assign the result of expr to res[0]
   * @param c
   * @param expr
   * @param res
   * @return
   */
  static CStmnt assignNetErrorOnError(C c,CExpr expr, CIdent res)
  {
    var expr_res = new CIdent("expr_res");
    return CStmnt.seq(
      CExpr.decl("int", expr_res),
      expr_res.assign(expr),
      // error
      CExpr.iff(CExpr.eq(expr_res, CExpr.int32const(-1)),
        CStmnt.seq(
          res
            .castTo("fzT_1i32 *")
            .index(CExpr.int32const(0))
            .assign(CExpr.call("fzE_net_error", new List<>())),
          c._names.FZ_FALSE.ret()
        )
      ),
      // success
      CStmnt.seq(
        res
          .castTo("fzT_1i32 *")
          .index(CExpr.int32const(0))
          .assign(expr_res),
        c._names.FZ_TRUE.ret()
      ));
  }


  /**
   * Create code for field-by-field comparison of two value or choice type values.
   *
   * @param c the C backend
   *
   * @param value1 the first value to compare
   *
   * @param value2 the second value to compare
   *
   * @param rt the Fuzion type of the value
   *
   * @param tmp local variable to type `bool` to be set to `true` iff `value1`
   * equals `value2`, and to `false` otherwise.
   *
   * @return code to perform the comparison
   */
  static CStmnt compareValues(C c, CExpr value1, CExpr value2, int rt, CIdent tmp)
  {
    if (PRECONDITIONS) require
      (value1 != null,
       value2 != null);

    CStmnt result;

    if (c._fuir.clazzIsVoidType(rt))
      {
        result = c.reportErrorInCode0("Unexpected comparison of void values. This is a bug in the compiler.");
      }
    else if (c._fuir.clazzIsUnitType(rt))
      { // unit-type values are always equal:
        result = tmp.assign(new CIdent("true"));
      }
    else if (c._fuir.clazzIsRef(rt) ||
             c._fuir.clazzIs(rt, FUIR.SpecialClazzes.c_i8  ) ||
             c._fuir.clazzIs(rt, FUIR.SpecialClazzes.c_i16 ) ||
             c._fuir.clazzIs(rt, FUIR.SpecialClazzes.c_i32 ) ||
             c._fuir.clazzIs(rt, FUIR.SpecialClazzes.c_i64 ) ||
             c._fuir.clazzIs(rt, FUIR.SpecialClazzes.c_u8  ) ||
             c._fuir.clazzIs(rt, FUIR.SpecialClazzes.c_u16 ) ||
             c._fuir.clazzIs(rt, FUIR.SpecialClazzes.c_u32 ) ||
             c._fuir.clazzIs(rt, FUIR.SpecialClazzes.c_u64 )    )
      {
        result = tmp.assign(CExpr.eq(value1, value2));
      }
    else if (c._fuir.clazzIs(rt, FUIR.SpecialClazzes.c_f32))
      {
        result = tmp.assign(CExpr.call("fzE_bitwise_compare_float", new List<>(value1, value2)));
      }
    else if (c._fuir.clazzIs(rt, FUIR.SpecialClazzes.c_f64))
      {
        result = tmp.assign(CExpr.call("fzE_bitwise_compare_float", new List<>(value1, value2)));
      }
    else if (c._fuir.clazzIsChoiceOfOnlyRefs(rt))
      {
        var union1 = value1.field(CNames.CHOICE_UNION_NAME);
        var union2 = value2.field(CNames.CHOICE_UNION_NAME);
        var fld = CNames.CHOICE_REF_ENTRY_NAME;
        var entry1  = union1.field(fld);
        var entry2  = union2.field(fld);
        result = tmp.assign(CExpr.eq(entry1, entry2));
      }
    else if (c._fuir.clazzIsChoice(rt))
      {
        var union1 = value1.field(CNames.CHOICE_UNION_NAME);
        var union2 = value2.field(CNames.CHOICE_UNION_NAME);
        var cazes = new List<CStmnt>();
        for (int i = 0; i < c._fuir.clazzNumChoices(rt); i++)
          {
            var tc = c._fuir.clazzChoice(rt, i);
            var fld = c._fuir.clazzIsRef(tc) ? CNames.CHOICE_REF_ENTRY_NAME
                                             : new CIdent(CNames.CHOICE_ENTRY_NAME + i);
            var entry1  = union1.field(fld);
            var entry2  = union2.field(fld);
            var cmp = compareValues(c, entry1, entry2, tc, tmp);
            cazes.add(CStmnt.caze(new List<>(CExpr.int32const(i)),
                                  CStmnt.seq(cmp, CStmnt.BREAK)));
          }
        result = CStmnt.iff(CExpr.eq(value1.field(CNames.TAG_NAME),
                                     value2.field(CNames.TAG_NAME)),
                            CStmnt.suitch(value1.field(CNames.TAG_NAME),
                                          cazes,
                                          null),
                            tmp.assign(new CIdent("false")));
      }
    else // not a choice, so a 'normal' product type
      {
        result = tmp.assign(new CIdent("true"));
        for (var i = 0; i < c._fuir.clazzNumFields(rt); i++)
          {
            var fi = c._fuir.clazzField(rt, i);
            if (c._types.fieldExists(fi))
              {
                var rti = c._fuir.clazzResultClazz(fi);
                var f1 = value1.field(c._names.fieldName(fi));
                var f2 = value2.field(c._names.fieldName(fi));
                result = CStmnt.seq(result,
                                    CStmnt.iff(tmp,
                                               compareValues(c, f1, f2, rti, tmp)));
              }
          }
      }
    return result;
  }

}

/* end of file */
