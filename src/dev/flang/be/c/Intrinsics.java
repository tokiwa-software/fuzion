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
  static CIdent A3 = new CIdent("arg3");
  static CIdent A4 = new CIdent("arg4");
  static CIdent A5 = new CIdent("arg5");

  /**
   * Predefined identifier to access errno macro.
   */
  static CIdent errno = new CIdent("errno");

  /**
   * Wrap code into a pthread_mutex_lock/unlock using CNames.GLOBAL_LOCK.  This
   * ensured atomicity with respect to any other code that is locked.
   */
  static CStmnt locked(CExpr lock,
                       CStmnt code)
  {
    var res = new CIdent("res");
    return CStmnt.seq(CExpr.decl("int", res, CExpr.call("pthread_mutex_lock", new List<CExpr>(CNames.GLOBAL_LOCK.adrOf()))),
                      CExpr.call("assert", new List<>(CExpr.eq(res, new CIdent("0")))),
                      code,
                      CExpr.call("pthread_mutex_unlock", new List<CExpr>(CNames.GLOBAL_LOCK.adrOf())));
  }


  static TreeMap<String, IntrinsicCode> _intrinsics_ = new TreeMap<>();
  static
  {
    put("Type.name"            , (c,cl,outer,in) ->
        {
          var tmp = new CIdent("tmp");
          var str = c._fuir.clazzTypeName(c._fuir.clazzOuterClazz(cl));
          var rc  = c._fuir.clazzResultClazz(cl);
          return CStmnt.seq(c.constString(str, tmp),
                            tmp.castTo(c._types.clazz(rc)).ret());
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
                                    CExpr.call("__atomic_compare_exchange", new List<>(f.adrOf(), tmp.adrOf(), new_value.adrOf(), new CIdent("false"), new CIdent("__ATOMIC_SEQ_CST"), new CIdent("__ATOMIC_SEQ_CST"))),
                                    tmp.ret());
                }
              else
                {
                  eq = CExpr.eq(CExpr.call("memcmp", new List<>(tmp.adrOf(), expected.adrOf(), CExpr.sizeOfType(c._types.clazz(rc)))), new CIdent("0"));
                  code = CStmnt.seq(locked(CNames.GLOBAL_LOCK,
                                           CStmnt.seq(CExpr.decl(c._types.clazz(rc), tmp, f),
                                                      CStmnt.iff(eq,
                                                                 f.assign(new_value)))),
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
                                    CStmnt.iff(CExpr.call("__atomic_compare_exchange",
                                                          new List<>(
                                                            f.adrOf(),
                                                            tmp.adrOf(),
                                                            new_value.adrOf(),
                                                            new CIdent("false"),
                                                            new CIdent("__ATOMIC_SEQ_CST"),
                                                            new CIdent("__ATOMIC_SEQ_CST"))),
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

                  code = CStmnt.seq(CStmnt.decl("bool", res, new CIdent("false")),
                                    locked(CNames.GLOBAL_LOCK,
                                           CStmnt.seq(CExpr.decl(c._types.clazz(rc), tmp, f),
                                                      CStmnt.iff(eq,
                                                                 CStmnt.seq(
                                                                  f.assign(new_value),
                                                                  res.assign(new CIdent("true"))
                                                                  )))),
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
              code = CExpr.call("__atomic_thread_fence", new List<>(new CIdent("__ATOMIC_SEQ_CST")));
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
                CExpr.decl(c._types.clazz(rc), tmp),
                CExpr.call("__atomic_load", new List<>(f.adrOf(), tmp.adrOf(), new CIdent("__ATOMIC_SEQ_CST"))),
                tmp.ret());
            }
          else
            {
              var f = c.accessField(outer, ac, v);
              code = CStmnt.seq(CExpr.decl(c._types.clazz(rc), tmp),
                                locked(CNames.GLOBAL_LOCK, tmp.assign(f)),
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
              code = CExpr.call("__atomic_thread_fence", new List<>(new CIdent("__ATOMIC_SEQ_CST")));
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
              code = CExpr.call("__atomic_store", new List<>(f.adrOf(), new_value.adrOf(), new CIdent("__ATOMIC_SEQ_CST")));
            }
          else
            {
              var f = c.accessField(outer, ac, v);
              code = locked(CNames.GLOBAL_LOCK, f.assign(new_value));
            }
          return code;
        });

    put("concur.util.loadFence", (c,cl,outer,in) ->
        {
          return CExpr.call("__atomic_thread_fence", new List<>(new CIdent("__ATOMIC_SEQ_CST")));
        });

    put("concur.util.storeFence", (c,cl,outer,in) ->
        {
          return CExpr.call("__atomic_thread_fence", new List<>(new CIdent("__ATOMIC_SEQ_CST")));
        });

    put("safety"               , (c,cl,outer,in) -> (c._options.fuzionSafety() ? c._names.FZ_TRUE : c._names.FZ_FALSE).ret());
    put("debug"                , (c,cl,outer,in) -> (c._options.fuzionDebug()  ? c._names.FZ_TRUE : c._names.FZ_FALSE).ret());
    put("debug_level"          , (c,cl,outer,in) -> (CExpr.int32const(c._options.fuzionDebugLevel())).ret());
    put("fuzion.sys.args.count", (c,cl,outer,in) -> CNames.GLOBAL_ARGC.ret());
    put("fuzion.sys.args.get"  , (c,cl,outer,in) ->
        {
          var tmp = new CIdent("tmp");
          var str = CNames.GLOBAL_ARGV.index(A0);
          var rc = c._fuir.clazzResultClazz(cl);
          return CStmnt.seq(c.constString(str,CExpr.call("strlen",new List<>(str)), tmp),
                            tmp.castTo(c._types.clazz(rc)).ret());
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
        {
          var resultIdent = new CIdent("result");
          return CStmnt.seq(
            // try delete as a file first
            CExpr.decl("int", resultIdent, CExpr.call("unlink", new List<>(A0.castTo("char *")))),
            CExpr.iff(resultIdent.eq(new CIdent("0")), c._names.FZ_TRUE.ret()),
            // then try delete as a directory
            resultIdent.assign(CExpr.call("rmdir", new List<>(A0.castTo("char *")))),
            CExpr.iff(resultIdent.eq(new CIdent("0")), c._names.FZ_TRUE.ret()),
            c._names.FZ_FALSE.ret()
            );
        }
        );
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
        {
          var statIdent = new CIdent("statbuf");
          var metadata = new CIdent("metadata");
          return CStmnt.seq(
            CExpr.decl("struct stat", statIdent),
            CExpr.decl("fzT_1i64 *", metadata),
            metadata.assign(A1.castTo("fzT_1i64 *")),
            // write stats in metadata if stat was successful and return true
            CExpr.iff(
              CExpr.call("stat", new List<>(A0.castTo("char *"), statIdent.adrOf())).eq(CExpr.int8const(0)),
              CStmnt.seq(
                metadata.index(0).assign(statIdent.field(new CIdent("st_size"))),
                metadata.index(1).assign(statIdent.field(new CIdent("st_mtime"))),
                metadata.index(2).assign(CExpr.call("S_ISREG", new List<>(statIdent.field(new CIdent("st_mode"))))),
                metadata.index(3).assign(CExpr.call("S_ISDIR", new List<>(statIdent.field(new CIdent("st_mode"))))),
                c._names.FZ_TRUE.ret()
                )
              ),
            // return false if stat failed
            metadata.index(0).assign(errno),
            metadata.index(1).assign(CExpr.int64const(0)),
            metadata.index(2).assign(CExpr.int64const(0)),
            metadata.index(3).assign(CExpr.int64const(0)),
            c._names.FZ_FALSE.ret()
            );
        }
        );
    put("fuzion.sys.fileio.lstats"   , (c,cl,outer,in) -> // NYI : maybe will be merged with fileio.stats under the same intrinsic
        {
          var statIdent = new CIdent("statbuf");
          var metadata = new CIdent("metadata");
          return CStmnt.seq(
            CExpr.decl("struct stat", statIdent),
            CExpr.decl("fzT_1i64 *", metadata),
            metadata.assign(A1.castTo("fzT_1i64 *")),
            // write stats in metadata if lstat was successful and return true
            CExpr.iff(
              CExpr.call("lstat", new List<>(A0.castTo("char *"), statIdent.adrOf())).eq(CExpr.int8const(0)),
              CStmnt.seq(
                metadata.index(0).assign(statIdent.field(new CIdent("st_size"))),
                metadata.index(1).assign(statIdent.field(new CIdent("st_mtime"))),
                metadata.index(2).assign(CExpr.call("S_ISREG", new List<>(statIdent.field(new CIdent("st_mode"))))),
                metadata.index(3).assign(CExpr.call("S_ISDIR", new List<>(statIdent.field(new CIdent("st_mode"))))),
                c._names.FZ_TRUE.ret()
                )
              ),
            // return false if lstat failed
            metadata.index(0).assign(errno),
            metadata.index(1).assign(CExpr.int64const(0)),
            metadata.index(2).assign(CExpr.int64const(0)),
            metadata.index(3).assign(CExpr.int64const(0)),
            c._names.FZ_FALSE.ret()
            );
        }
        );
    put("fuzion.sys.fileio.open"   , (c,cl,outer,in) ->
        {
          var filePointer = new CIdent("fp");
          var openResults = new CIdent("open_results");
          return CStmnt.seq(
            CExpr.decl("FILE *", filePointer),
            CExpr.decl("fzT_1i64 *", openResults),
            openResults.assign(A1.castTo("fzT_1i64 *")),
            errno.assign(new CIdent("0")),
            CStmnt.suitch(
              A2,
              new List<>(
                CStmnt.caze(
                  new List<>(CExpr.int8const(0)),
                  CStmnt.seq(
                    // open file for reading, binary mode
                    filePointer.assign(CExpr.call("fopen", new List<>(A0.castTo("char *"), CExpr.string("rb")))),
                    CExpr.iff(CExpr.notEq(filePointer, new CIdent("NULL")),
                      CStmnt.seq(openResults.index(0).assign(filePointer.castTo("fzT_1i64")))),
                    CStmnt.BREAK
                    )
                  ),
                CStmnt.caze(
                  new List<>(CExpr.int8const(1)),
                  CStmnt.seq(
                    // open file read-, write-, binary-mode. creates new file if not exists.
                    // if file is not empty, any writes will overwrite the contents in the file.
                    filePointer.assign(CExpr.call("fopen", new List<>(A0.castTo("char *"), CExpr.string("a+b")))),
                    CExpr.call("fseek", new List<>(filePointer, CExpr.int32const(0), new CIdent("SEEK_SET"))),
                    CExpr.iff(CExpr.notEq(filePointer, new CIdent("NULL")),
                      CStmnt.seq(openResults.index(0).assign(filePointer.castTo("fzT_1i64")))),
                    CStmnt.BREAK
                    )
                  ),
                CStmnt.caze(
                  new List<>(CExpr.int8const(2)),
                  CStmnt.seq(
                    // open file read-, write-, binary-mode. creates new file if not exists.
                    // writes happen at end of file.
                    filePointer.assign(CExpr.call("fopen", new List<>(A0.castTo("char *"), CExpr.string("a+b")))),
                    CExpr.iff(CExpr.notEq(filePointer, new CIdent("NULL")),
                      CStmnt.seq(openResults.index(0).assign(filePointer.castTo("fzT_1i64")))),
                    CStmnt.BREAK
                    )
                  )
                ),
              CStmnt.seq(
                CExpr.fprintfstderr("*** Unsupported open flag. Please use: 0 for READ, 1 for WRITE, 2 for APPEND. ***\n"),
                CExpr.exit(1)
                )
              ),
            openResults.index(1).assign(errno.castTo("fzT_1i64"))
            );
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
            CStmnt.iff(CExpr.call("fseeko", new List<>(A0.castTo("FILE *"), A1.castTo("off_t"), new CIdent("SEEK_SET"))).eq(CExpr.int8const(0)),
            seekResults.index(0).assign(CExpr.call("ftello", new List<>(A0.castTo("FILE *"))).castTo("fzT_1i64"))),
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
            positionResults.index(0).assign(CExpr.call("ftello", new List<>(A0.castTo("FILE *"))).castTo("fzT_1i64")),
            positionResults.index(1).assign(errno.castTo("fzT_1i64"))
            );
        }
        );

    put("fuzion.sys.fileio.mmap"  , (c,cl,outer,in) -> CExpr.call("fzE_mmap", new List<CExpr>(
      A0.castTo("FILE * "),   // file
      A1.castTo("off_t"),     // offset
      A2.castTo("size_t"),    // size
      A3.castTo("int *")      // int[1] contains success=0 or error=-1
    )).ret());
    put("fuzion.sys.fileio.munmap", (c,cl,outer,in) -> CExpr.call("fzE_munmap", new List<CExpr>(
      A0.castTo("void *"),    // address
      A1.castTo("size_t")     // size
    )).ret());

    put("fuzion.sys.fileio.flush"      , (c,cl,outer,in) ->
      CExpr.call("fflush", new List<>(A0.castTo("FILE *"))).ret());
    put("fuzion.sys.stdin.stdin0"      , (c,cl,outer,in) ->
      new CIdent("stdin").castTo("fzT_1i64").ret());
    put("fuzion.sys.out.stdout"      , (c,cl,outer,in) ->
      new CIdent("stdout").castTo("fzT_1i64").ret());
    put("fuzion.sys.err.stderr"      , (c,cl,outer,in) ->
      new CIdent("stderr").castTo("fzT_1i64").ret());

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
    put("f32.type.is_NaN"      ,
        "f64.type.is_NaN"      , (c,cl,outer,in) -> CStmnt.seq(CStmnt.iff(CExpr.call("isnan", new List<>(A0)).ne(new CIdent("0")),
                                                                          c._names.FZ_TRUE.ret()
                                                                          ),
                                                               c._names.FZ_FALSE.ret()
                                                               ));
    put("f32.type.square_root" , (c,cl,outer,in) -> CExpr.call("sqrtf",  new List<>(A0)).ret());
    put("f64.type.square_root" , (c,cl,outer,in) -> CExpr.call("sqrt",   new List<>(A0)).ret());
    put("f32.type.exp"         , (c,cl,outer,in) -> CExpr.call("expf",   new List<>(A0)).ret());
    put("f64.type.exp"         , (c,cl,outer,in) -> CExpr.call("exp",    new List<>(A0)).ret());
    put("f32.type.log"         , (c,cl,outer,in) -> CExpr.call("logf",   new List<>(A0)).ret());
    put("f64.type.log"         , (c,cl,outer,in) -> CExpr.call("log",    new List<>(A0)).ret());
    put("f32.type.sin"         , (c,cl,outer,in) -> CExpr.call("sinf",   new List<>(A0)).ret());
    put("f64.type.sin"         , (c,cl,outer,in) -> CExpr.call("sin",    new List<>(A0)).ret());
    put("f32.type.cos"         , (c,cl,outer,in) -> CExpr.call("cosf",   new List<>(A0)).ret());
    put("f64.type.cos"         , (c,cl,outer,in) -> CExpr.call("cos",    new List<>(A0)).ret());
    put("f32.type.tan"         , (c,cl,outer,in) -> CExpr.call("tanf",   new List<>(A0)).ret());
    put("f64.type.tan"         , (c,cl,outer,in) -> CExpr.call("tan",    new List<>(A0)).ret());
    put("f32.type.asin"        , (c,cl,outer,in) -> CExpr.call("asinf", new List<>(A0)).ret());
    put("f64.type.asin"        , (c,cl,outer,in) -> CExpr.call("asin",  new List<>(A0)).ret());
    put("f32.type.acos"        , (c,cl,outer,in) -> CExpr.call("acosf", new List<>(A0)).ret());
    put("f64.type.acos"        , (c,cl,outer,in) -> CExpr.call("acos",  new List<>(A0)).ret());
    put("f32.type.atan"        , (c,cl,outer,in) -> CExpr.call("atanf", new List<>(A0)).ret());
    put("f64.type.atan"        , (c,cl,outer,in) -> CExpr.call("atan",  new List<>(A0)).ret());
    put("f32.type.sinh"        , (c,cl,outer,in) -> CExpr.call("sinhf",  new List<>(A0)).ret());
    put("f64.type.sinh"        , (c,cl,outer,in) -> CExpr.call("sinh",   new List<>(A0)).ret());
    put("f32.type.cosh"        , (c,cl,outer,in) -> CExpr.call("coshf",  new List<>(A0)).ret());
    put("f64.type.cosh"        , (c,cl,outer,in) -> CExpr.call("cosh",   new List<>(A0)).ret());
    put("f32.type.tanh"        , (c,cl,outer,in) -> CExpr.call("tanhf",  new List<>(A0)).ret());
    put("f64.type.tanh"        , (c,cl,outer,in) -> CExpr.call("tanh",   new List<>(A0)).ret());

    put("Any.as_string"        , (c,cl,outer,in) ->
        {
          var res = new CIdent("res");
          var clname = c._fuir.clazzAsString(c._fuir.clazzOuterClazz(cl));
          var instname = "instance[" + clname + "]";
          var instchars = instname.getBytes(StandardCharsets.UTF_8);
          var rc = c._fuir.clazzResultClazz(cl);
          return CStmnt.seq(c.constString(instchars, res),
                            res.castTo(c._types.clazz(rc)).ret());
        });

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
                                        CTypes.scalar(FUIR.SpecialClazzes.c_u64),
                                        last_id,
                                        CExpr.uint64const(0)),
                            CExpr.call("__atomic_add_fetch", new List<>(last_id.adrOf(), CExpr.uint64const(1), new CIdent("__ATOMIC_SEQ_CST"))).ret());
        });
     put("fuzion.sys.thread.spawn0", (c,cl,outer,in) ->
        {
          var oc = c._fuir.clazzActualGeneric(cl, 0);
          var call = c._fuir.lookupCall(oc);
          if (c._fuir.clazzNeedsCode(call))
            {
              var pt = new CIdent("pt");
              var res = new CIdent("res");
              var arg = new CIdent("arg");
              return CStmnt.seq(CExpr.decl("pthread_t *", pt),
                                CExpr.decl("int", res),
                                CExpr.decl("struct " + CNames.fzThreadStartRoutineArg.code() + "*", arg),

                                pt.assign(CExpr.call(c.malloc(), new List<>(CExpr.sizeOfType("pthread_t")))),
                                CExpr.iff(pt.eq(CNames.NULL),
                                          CStmnt.seq(CExpr.fprintfstderr("*** " + c.malloc() + "(%zu) failed\n", CExpr.sizeOfType("pthread_t")),
                                                     CExpr.call("exit", new List<>(CExpr.int32const(1))))),

                                arg.assign(CExpr.call(c.malloc(), new List<>(CExpr.sizeOfType("struct " + CNames.fzThreadStartRoutineArg.code())))),
                                CExpr.iff(arg.eq(CNames.NULL),
                                          CStmnt.seq(CExpr.fprintfstderr("*** " + c.malloc() + "(%zu) failed\n", CExpr.sizeOfType("struct " + CNames.fzThreadStartRoutineArg.code())),
                                                     CExpr.call("exit", new List<>(CExpr.int32const(1))))),

                                arg.deref().field(CNames.fzThreadStartRoutineArgFun).assign(CExpr.ident(c._names.function(call, false)).adrOf().castTo("void *")),
                                arg.deref().field(CNames.fzThreadStartRoutineArgArg).assign(A0.castTo("void *")),

                                res.assign(CExpr.call("pthread_create", new List<>(pt,
                                                                                   CNames.NULL,
                                                                                   CNames.fzThreadStartRoutine.adrOf(),
                                                                                   arg))),
                                CExpr.iff(res.ne(CExpr.int32const(0)),
                                          CStmnt.seq(CExpr.fprintfstderr("*** pthread_create failed with return code %d\n",res),
                                                     CExpr.call("exit", new List<>(CExpr.int32const(1))))),
                                pt.castTo("int64_t").ret());
              // NYI: free(pt)!
            }
          else
            {
              return CStmnt.EMPTY;
            }
        });
    put("fuzion.sys.thread.join0", (c,cl,outer,in) ->
    {
      return CStmnt.seq(
        CExpr.call("pthread_join", new List<>(A0.castTo("pthread_t *").deref(), CNames.NULL /* NYI handle return value */))
      );
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


    put("effect.replace"       ,
        "effect.default"       ,
        "effect.abortable"     ,
        "effect.abort"         , (c,cl,outer,in) ->
        {
          var ecl = c._fuir.effectType(cl);
          var ev  = CNames.fzThreadEffectsEnvironment.deref().field(c._names.env(ecl));
          var evi = CNames.fzThreadEffectsEnvironment.deref().field(c._names.envInstalled(ecl));
          var evj = CNames.fzThreadEffectsEnvironment.deref().field(c._names.envJmpBuf(ecl));
          var o   = CNames.OUTER;
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
    put("effect.type.is_installed"       , (c,cl,outer,in) ->
        {
          var ecl = c._fuir.clazzActualGeneric(cl, 0);
          var evi = CNames.fzThreadEffectsEnvironment.deref().field(c._names.envInstalled(ecl));
          return CStmnt.seq(CStmnt.iff(evi, c._names.FZ_TRUE.ret()), c._names.FZ_FALSE.ret());
        });

    IntrinsicCode noJava = (c,cl,outer,in) ->
      CStmnt.seq(CExpr.fprintfstderr("*** C backend support does not support Java interface (yet).\n"),
                 CExpr.exit(1));
    put("fuzion.java.Java_Object.is_null"   , noJava);
    put("fuzion.java.array_get"             , noJava);
    put("fuzion.java.array_length"          , noJava);
    put("fuzion.java.array_to_java_object0" , noJava);
    put("fuzion.java.bool_to_java_object"   , noJava);
    put("fuzion.java.call_c0"               , noJava);
    put("fuzion.java.call_s0"               , noJava);
    put("fuzion.java.call_v0"               , noJava);
    put("fuzion.java.f32_to_java_object"    , noJava);
    put("fuzion.java.f64_to_java_object"    , noJava);
    put("fuzion.java.get_field0"            , noJava);
    put("fuzion.java.get_static_field0"     , noJava);
    put("fuzion.java.i16_to_java_object"    , noJava);
    put("fuzion.java.i32_to_java_object"    , noJava);
    put("fuzion.java.i64_to_java_object"    , noJava);
    put("fuzion.java.i8_to_java_object"     , noJava);
    put("fuzion.java.java_string_to_string" , noJava);
    put("fuzion.java.string_to_java_object0", noJava);
    put("fuzion.java.u16_to_java_object"    , noJava);
  }


  /*----------------------------  variables  ----------------------------*/


  /*-------------------------  static methods  --------------------------*/


  private static void put(String n, IntrinsicCode c) { _intrinsics_.put(n, c); }
  private static void put(String n1, String n2, IntrinsicCode c) { put(n1, c); put(n2, c); }
  private static void put(String n1, String n2, String n3, IntrinsicCode c) { put(n1, c); put(n2, c); put(n3, c); }
  private static void put(String n1, String n2, String n3, String n4, IntrinsicCode c) { put(n1, c); put(n2, c); put(n3, c); put(n4, c); }


  /**
   * Get the names of all intrinsics supported by this backend.
   */
  public static Set<String> supportedIntrinsics()
  {
    return _intrinsics_.keySet();
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

    var in = c._fuir.clazzIntrinsicName(cl);
    var cg = _intrinsics_.get(in);
    var result = CStmnt.EMPTY;
    if (cg != null)
      {
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
            var msg = "code for intrinsic " + c._fuir.clazzIntrinsicName(cl) + " is missing";
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

}

/* end of file */
