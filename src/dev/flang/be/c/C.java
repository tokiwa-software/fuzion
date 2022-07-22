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
 * Source of class C
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.util.stream.Stream;

import dev.flang.fuir.FUIR;

import dev.flang.fuir.analysis.AbstractInterpreter;
import dev.flang.fuir.analysis.dfa.DFA;
import dev.flang.fuir.analysis.Escape;
import dev.flang.fuir.analysis.TailCall;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.Pair;


/**
 * C provides a C code backend converting FUIR data into C code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class C extends ANY
{

  /*-----------------------------  classes  -----------------------------*/


  /**
   * Statement processor used with AbstractInterpreter to generate C code.
   */
  class CodeGen extends AbstractInterpreter.ProcessStatement<CExpr,CStmnt>
  {


    /**
     * Join a List of RESULT from subsequent statements into a compound
     * statement.  For a code generator, this could, e.g., join statements "a :=
     * 3;" and "b(x);" into a block "{ a := 3; b(x); }".
     */
    public CStmnt sequence(List<CStmnt> l)
    {
      return CStmnt.seq(l);
    }


    /*
     * Produce the unit type value.  This is used as a placeholder
     * for the universe instance as well as for the instance 'unit'.
     */
    public CExpr unitValue()
    {
      return CExpr.UNIT;
    }


    /**
     * Called before each statement is processed. May be used to, e.g., produce
     * tracing code for debugging or a comment.
     */
    public CStmnt statementHeader(int cl, int c, int i)
    {
      return comment(String.format("%4d: %s", i, _fuir.codeAtAsString(cl, c, i)));
    }


    /**
     * A comment, adds human readable information
     */
    public CStmnt comment(String s)
    {
      return CStmnt.lineComment(s);
    }


    /**
     * no operation, like comment, but without giving any comment.
     */
    public CStmnt nop()
    {
      return CStmnt.EMPTY;
    }


    /**
     * Determine the address of a given value.  This is used on a call to an
     * inner feature to pass a reference to the outer value type instance.
     */
    public Pair<CExpr, CStmnt> adrOf(CExpr v)
    {
      return new Pair<>(v.adrOf(), CStmnt.EMPTY);
    }


    /**
     * Create code to assign value to a given field w/o dynamic binding.
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
     * @return statement to perform the given access
     */
    public CStmnt assignStatic(int tc, int f, int rt, CExpr tvalue, CExpr val)
    {
      return assignField(tvalue, tc, tc, f, val, rt);
    }


    /**
     * Perform an assignment of avalue to a field in tvalue. The type of tvalue
     * might be dynamic (a refernce). See FUIR.acess*().
     */
    public CStmnt assign(int cl, int c, int i, CExpr tvalue, CExpr avalue)
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
    public Pair<CExpr, CStmnt> call(int cl, int c, int i, CExpr tvalue, List<CExpr> args)
    {
      var cc0 = _fuir.accessedClazz  (cl, c, i);
      var ol = new List<CStmnt>();
      if (_fuir.clazzContract(cc0, FUIR.ContractKind.Pre, 0) != -1)
        {
          var callpair = C.this.call(cl, tvalue, args, c, i, cc0, true);
          ol.add(callpair._v1);
        }
      var res = CExpr.UNIT;
      if (!_fuir.callPreconditionOnly(cl, c, i))
        {
          var r = access(cl, c, i, tvalue, args);
          ol.add(r._v1);
          res = r._v0;
        }
      return new Pair<>(res, CStmnt.seq(ol));
    }


    /**
     * For a given value v of value type vc create a boxed ref value of type rc.
     */
    public Pair<CExpr, CStmnt> box(CExpr val, int vc, int rc)
    {
      var t = _names.newTemp();
      var o = CStmnt.seq(CStmnt.lineComment("Box " + _fuir.clazzAsString(vc)),
                         declareAllocAndInitClazzId(rc, t),
                         C.this.assign(fields(t, rc), val, vc));
      return new Pair<>(t, o);
    }


    /**
     * For a given reference value v create an unboxed value of type vc.
     */
    public Pair<CExpr, CStmnt> unbox(CExpr val, int orc)
    {
      return new Pair<>(fields(val, orc), CStmnt.EMPTY);
    }


    /**
     * Get the current instance
     */
    public Pair<CExpr, CStmnt> current(int cl)
    {
      return new Pair<>(C.this.current(cl), CStmnt.EMPTY);
    }


    /**
     * Get the outer instance the given clazz is called on.
     */
    public Pair<CExpr, CStmnt> outer(int cl)
    {
      return new Pair<>(CNames.OUTER, CStmnt.EMPTY);
    }


    /**
     * Get the argument #i
     */
    public CExpr arg(int cl, int i) { return CIdent.arg(i); }


    /**
     * Get a constant value of type constCl with given byte data d.
     */
    public Pair<CExpr, CStmnt> constData(int constCl, byte[] d)
    {
      var o = CStmnt.EMPTY;
      var r = switch (_fuir.getSpecialId(constCl))
        {
        case c_bool -> d[0] == 1 ? _names.FZ_TRUE : _names.FZ_FALSE;
        case c_i8   -> CExpr. int8const( ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).get     ());
        case c_i16  -> CExpr. int16const(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getShort());
        case c_i32  -> CExpr. int32const(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getInt  ());
        case c_i64  -> CExpr. int64const(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getLong ());
        case c_u8   -> CExpr.uint8const (ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).get     () & 0xff);
        case c_u16  -> CExpr.uint16const(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getChar ());
        case c_u32  -> CExpr.uint32const(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getInt  ());
        case c_u64  -> CExpr.uint64const(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getLong ());
        case c_f32  -> CExpr.   f32const(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getFloat());
        case c_f64  -> CExpr.   f64const(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getDouble());
        case c_conststring ->
        {
          var tmp = _names.newTemp();
          o = constString(d, tmp);
          yield tmp;
        }
        default ->
        {
          Errors.error("Unsupported constant in C backend.",
                       "Backend cannot handle constant of clazz '" + _fuir.clazzAsString(constCl) + "' ");
          yield CExpr.dummy(_fuir.clazzAsString(constCl));
        }
        };
      return new Pair<>(r, o);
    }


    /**
     * Perform a match on value subv.
     */
    public Pair<CExpr, CStmnt> match(AbstractInterpreter<CExpr, CStmnt> ai, int cl, int c, int i, CExpr subv)
    {
      var subjClazz = _fuir.matchStaticSubject(cl, c, i);
      var sub       = fields(subv, subjClazz);
      var uniyon    = sub.field(_names.CHOICE_UNION_NAME);
      var hasTag    = !_fuir.clazzIsChoiceOfOnlyRefs(subjClazz);
      var refEntry  = uniyon.field(_names.CHOICE_REF_ENTRY_NAME);
      var ref       = hasTag ? refEntry                   : _names.newTemp();
      var getRef    = hasTag ? CStmnt.EMPTY               : CStmnt.decl(_types.clazz(_fuir.clazzObject()), (CIdent) ref, refEntry);
      var tag       = hasTag ? sub.field(_names.TAG_NAME) : ref.castTo("int64_t");
      var tcases    = new List<CStmnt>(); // cases depending on tag value or ref cast to int64
      var rcases    = new List<CStmnt>(); // cases depending on clazzId of ref type
      CStmnt tdefault = null;
      for (var mc = 0; mc < _fuir.matchCaseCount(c, i); mc++)
        {
          var ctags = new List<CExpr>();
          var rtags = new List<CExpr>();
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
                      ctags.add(CExpr.int32const(tagNum).comment(_fuir.clazzAsString(tc)));
                      if (CHECKS) check
                        (hasTag || !_types.hasData(tc));
                    }
                }
            }
          var sl = new List<CStmnt>();
          var field = _fuir.matchCaseField(cl, c, i, mc);
          if (field != -1)
            {
              var fclazz = _fuir.clazzResultClazz(field);     // static clazz of assigned field
              var f      = field(cl, C.this.current(cl), field);
              var entry  = _fuir.clazzIsRef(fclazz) ? ref.castTo(_types.clazz(fclazz)) :
                           _types.hasData(fclazz)   ? uniyon.field(new CIdent(_names.CHOICE_ENTRY_NAME + tags[0]))
                                                    : CExpr.UNIT;
              sl.add(C.this.assign(f, entry, fclazz));
            }
          sl.add(ai.process(cl, _fuir.matchCaseCode(c, i, mc))._v1);
          sl.add(CStmnt.BREAK);
          var cazecode = CStmnt.seq(sl);
          tcases.add(CStmnt.caze(ctags, cazecode));  // tricky: this a NOP if ctags.isEmpty
          if (!rtags.isEmpty()) // we need default clause to handle refs without a tag
            {
              rcases.add(CStmnt.caze(rtags, cazecode));
              tdefault = cazecode;
            }
        }
      if (rcases.size() >= 2)
        { // more than two reference cases: we have to create separate switch of clazzIds for refs
          var id = refEntry.deref().field(_names.CLAZZ_ID);
          var notFound = reportErrorInCode("unexpected reference type %d found in match", id);
          tdefault = CStmnt.suitch(id, rcases, notFound);
        }
      return new Pair(CExpr.UNIT, CStmnt.seq(getRef, CStmnt.suitch(tag, tcases, tdefault)));
    }


    /**
     * Create a tagged value of type newcl from an untagged value for type valuecl.
     */
    public Pair<CExpr, CStmnt> tag(int cl, int valuecl, CExpr value, int newcl, int tagNum)
    {
      var res     = _names.newTemp();
      var tag     = res.field(_names.TAG_NAME);
      var uniyon  = res.field(_names.CHOICE_UNION_NAME);
      var entry   = uniyon.field(_fuir.clazzIsRef(valuecl) ||
                                 _fuir.clazzIsChoiceOfOnlyRefs(newcl) ? _names.CHOICE_REF_ENTRY_NAME
                                                                      : new CIdent(_names.CHOICE_ENTRY_NAME + tagNum));
      if (_fuir.clazzIsUnitType(valuecl) && _fuir.clazzIsChoiceOfOnlyRefs(newcl))
        {// replace unit-type values by 0, 1, 2, 3,... cast to ref Object
          if (CHECKS) check
            (value == CExpr.UNIT);
          if (tagNum >= CConstants.PAGE_SIZE)
            {
              Errors.error("Number of tags for choice type exceeds page size.",
                           "While creating code for '" + _fuir.clazzAsString(cl) + "'\n" +
                           "Found in choice type '" + _fuir.clazzAsString(newcl)+ "'\n");
            }
          value = CExpr.int32const(tagNum);
          valuecl = _fuir.clazzObject();
        }
      if (_fuir.clazzIsRef(valuecl))
        {
          value = value.castTo(_types.clazz(_fuir.clazzObject()));
        }
      var o = CStmnt.seq(CStmnt.lineComment("Tag a value to be of choice type " + _fuir.clazzAsString(newcl) +
                                            " static value type " + _fuir.clazzAsString(valuecl)),
                         CStmnt.decl(_types.clazz(newcl), res),
                         _fuir.clazzIsChoiceOfOnlyRefs(newcl) ? CStmnt.EMPTY : tag.assign(CExpr.int32const(tagNum)),
                         C.this.assign(entry, value, valuecl));

      return new Pair<>(res, o);
    }


    /**
     * Access the effect of type ecl that is installed in the environemnt.
     */
    public Pair<CExpr, CStmnt> env(int ecl)
    {
      var res = _names.fzThreadEffectsEnvironment.deref().field(_names.env(ecl));
      var evi = _names.fzThreadEffectsEnvironment.deref().field(_names.envInstalled(ecl));
      var o = CStmnt.iff(evi.not(),
                         CStmnt.seq(CExpr.fprintfstderr("*** effect %s not present in current environment\n",
                                                        CExpr.string(_fuir.clazzAsString(ecl))),
                                    CExpr.exit(1)));
      return new Pair<>(res, o);
    }


    /**
     * Process a contract of kind ck of clazz cl that results in bool value cc
     * (i.e., the contract fails if !cc).
     */
    public CStmnt contract(int cl, FUIR.ContractKind ck, CExpr cc)
    {
      return CStmnt.iff(cc.field(_names.TAG_NAME).not(),
                        CStmnt.seq(CExpr.fprintfstderr("*** failed " + ck + " on call to '%s'\n",
                                                       CExpr.string(_fuir.clazzAsString(cl))),
                                   CExpr.exit(1)));
    }

  }


  /*----------------------------  constants  ----------------------------*/


  /**
   * C code generation phase for generating C functions for features.
   */
  private enum CompilePhase
  {
    TYPES           { CStmnt compile(C c, int cl) { return c._types.types(cl);   } }, // declare types
    STRUCTS         { CStmnt compile(C c, int cl) { return c._types.structs(cl); } }, // generate struct declarations
    FORWARDS        { CStmnt compile(C c, int cl) { return c.forwards(cl);       } }, // generate forward declarations only
    IMPLEMENTATIONS { CStmnt compile(C c, int cl) { return c.code(cl);           } }; // generate C functions

    /**
     * Perform this compilation phase on given clazz using given backend.
     *
     * @param c the backend
     *
     * @param cl the clazz.
     */
    abstract CStmnt compile(C c, int cl);
  }


  /*----------------------------  variables  ----------------------------*/


  /**
   * The intermediate code we are compiling.
   */
  final FUIR _fuir;


  /**
   * The tail call analysis.
   */
  final TailCall _tailCall;


  /**
   * The escape analysis.
   */
  final Escape _escape;


  /**
   * Abstract interpreter framework used to walk through the code.
   */
  final AbstractInterpreter<CExpr, CStmnt> _ai;


  /**
   * The options set for the compilation.
   */
  final COptions _options;


  /**
   * C identifier handling goes through _names:
   */
  final CNames _names;


  /**
   * C types handling goes through _types:
   */
  final CTypes _types;


  /**
   * C intrinsics
   */
  final Intrinsics _intrinsics;


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create C code backend for given intermediate code.
   *
   * @param opt options to control compilation.
   *
   * @param fuir the intermediate code.
   */
  public C(COptions opt,
           FUIR fuir)
  {
    _options = opt;
    _fuir = opt._Xdfa ?  new DFA(opt, fuir).new_fuir() : fuir;
    _tailCall = new TailCall(fuir);
    _escape = new Escape(fuir);
    _ai = new AbstractInterpreter(_fuir, new CodeGen());

    _names = new CNames(fuir);
    _types = new CTypes(_fuir, _names);
    _intrinsics = new Intrinsics();
    Errors.showAndExit();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create the C code from the intermediate code.
   */
  public void compile()
  {
    var cl = _fuir.mainClazzId();
    var name = _options._binaryName != null ? _options._binaryName : _fuir.clazzBaseName(cl);
    var cname = name + ".c";
    _options.verbosePrintln(" + " + cname);
    try
      {
        var cf = new CFile(cname);
        try
          {
            createCode(cf);
          }
        finally
          {
            cf.close();
          }
      }
    catch (IOException io)
      {
        Errors.error("C backend I/O error",
                     "While creating code to '" + cname + "', received I/O error '" + io + "'");
      }
    Errors.showAndExit();

    var command = new List<String>("clang", "-O3");
    if(_options._useBoehmGC)
      {
        command.addAll("-lgc");
      }
    // NYI link libmath, libpthread only when needed
    command.addAll("-lm", "-lpthread", "-o", name, cname);

    _options.verbosePrintln(" * " + command.toString("", " ", ""));;
    try
      {
        var p = new ProcessBuilder().inheritIO().command(command).start();
        p.waitFor();
        if (p.exitValue() != 0)
          {
            Errors.error("C backend: C compiler failed",
                         "C compiler call '" + command.toString("", " ", "") + "' failed with exit code '" + p.exitValue() + "'");
          }
      }
    catch (IOException | InterruptedException io)
      {
        Errors.error("C backend I/O error when running C Compiler",
                     "C compiler call '" + command.toString("", " ", "") + "'  received '" + io + "'");
      }
    Errors.showAndExit();
  }


  /**
   * After the CFile has been opened and stored in _c, this methods generates
   * the code into this file.
   */
  private void createCode(CFile cf)
  {
    cf.print
      ((_options._useBoehmGC ? "#include <gc.h>\n" : "")+
       "#include <stdlib.h>\n"+
       "#include <stdio.h>\n"+
       "#include <unistd.h>\n"+
       "#include <stdbool.h>\n"+
       "#include <stdint.h>\n"+
       "#include <string.h>\n"+
       "#include <math.h>\n"+
       "#include <float.h>\n"+
       "#include <assert.h>\n"+
       "#include <time.h>\n"+
       "#include <setjmp.h>\n"+
       "#include <pthread.h>\n"+
       "\n");
    cf.print
      (CStmnt.decl("int", _names.GLOBAL_ARGC));
    cf.print
      (CStmnt.decl("char **", _names.GLOBAL_ARGV));
    var o = new CIdent("of");
    var s = new CIdent("sz");
    var r = new CIdent("r");
    cf.print
      (CStmnt.lineComment("helper to clone a (stack) instance to the heap"));
    cf.print
      (CStmnt.functionDecl("void *",
                           CNames.HEAP_CLONE,
                           new List<>("void *", "size_t"),
                           new List<>(o, s),
                           CStmnt.seq(new List<>(CStmnt.decl(null, "void *", r, CExpr.call(malloc(), new List<>(s))),
                                                 CExpr.call("memcpy", new List<>(r, o, s)),
                                                 r.ret()))));
    var ordered = _types.inOrder();


    // declaration of struct that is meant to passed to
    // the thread start routine
    cf.print(CStmnt.struct(CNames.fzThreadStartRoutineArg.code(), new List<>(
      CStmnt.decl("void *", CNames.fzThreadStartRoutineArgFun),
      CStmnt.decl("void *", CNames.fzThreadStartRoutineArgArg)
    )));
    // declaration of the thread start routine
    cf.print(threadStartRoutine(false));


    Stream.of(CompilePhase.values()).forEachOrdered
      ((p) ->
       {
         for (var c : ordered)
           {
             cf.print(p.compile(this, c));
           }
         cf.println("");

         // thread local effect environments
         if (p == CompilePhase.STRUCTS)
           {
             cf.print(
               CStmnt.seq(
                 CStmnt.struct(CNames.fzThreadEffectsEnvironment.code(),
                   new List<CStmnt>(
                     ordered
                       .stream()
                       .filter(cl -> _fuir.clazzNeedsCode(cl) &&
                               _fuir.clazzKind(cl) == FUIR.FeatureKind.Intrinsic &&
                               _fuir.isEffect(cl))
                       .mapToInt(cl -> _fuir.effectType(cl))
                       .distinct()
                       .mapToObj(cl -> Stream.of(
                                         CStmnt.decl(_types.clazz(cl), _names.env(cl)),
                                         CStmnt.decl("bool", _names.envInstalled(cl)),
                                         CStmnt.decl("jmp_buf*", _names.envJmpBuf(cl))
                                       )
                       )
                       .flatMap(x -> x)
                       .iterator())),
                 CStmnt.decl("__thread", "struct " + CNames.fzThreadEffectsEnvironment.code() + "*", CNames.fzThreadEffectsEnvironment)
               )
             );
           }
       });

    cf.print(threadStartRoutine(true));

    cf.println("int main(int argc, char **argv) { ");

    if (_options._useBoehmGC)
      {
        cf.println("GC_INIT(); /* Optional on Linux/X86 */");
      }

    cf.print(initializeEffectsEnvironment());

    cf.print(CStmnt.seq(_names.GLOBAL_ARGC.assign(new CIdent("argc")),
                        _names.GLOBAL_ARGV.assign(new CIdent("argv")),
                        CExpr.call(_names.function(_fuir.mainClazzId(), false), new List<>())));
    cf.println("}");
  }


  /**
   * initializes the effects environment
   * then runs the actual code passed to the thread
   * @param includeBody
   * @return
   */
  private CStmnt threadStartRoutine(boolean includeBody)
  {
    var tmp = new CIdent("tmp1");
    var body = CStmnt.seq(
      initializeEffectsEnvironment(),
      CExpr.decl("struct " + CNames.fzThreadStartRoutineArg.code() + "*", tmp),
      tmp.assign(CIdent.arg(0)),
      CExpr.call("((void *(*)(void *))" + tmp.code() + "->"+ CNames.fzThreadStartRoutineArgFun.code() + ")", new List<>(tmp.deref().field(CNames.fzThreadStartRoutineArgArg))).ret()
    );
    return CStmnt.functionDecl("static void *", CNames.fzThreadStartRoutine, new List<>("void *"), new List<>(CIdent.arg(0)), includeBody ? body : null);
  }


  /**
   * zeros the struct that holds the effects environment
   * @return
   */
  private CStmnt initializeEffectsEnvironment()
  {
    var tmp = new CIdent("tmp0");
    return CStmnt.seq(
      CStmnt.decl("struct " + CNames.fzThreadEffectsEnvironment.code(), tmp),
      CExpr.call("memset", new List<>(tmp.adrOf(), CExpr.int32const(0), CExpr.sizeOfType("struct " + CNames.fzThreadEffectsEnvironment.code()))),
      CNames.fzThreadEffectsEnvironment.assign(tmp.adrOf())
    );
  }


  /**
   * In case of an unexpected situation such as code that should be unreachable,
   * this should be used to print a corresponding error and exit(1).
   *
   * @param msg the message to be shown, may include %-escapes for additional args
   *
   * @param args the additional args to be fprintf-ed into msg.
   *
   * @return the C statement to report the error and exit(1).
   */
  CStmnt reportErrorInCode(String msg, CExpr... args)
  {
    var msg2 = "*** %s:%d: " + msg + "\n";
    var args2 = new List<CExpr>(CIdent.FILE, CIdent.LINE);
    for (var a : args)
      {
        args2.add(a);
      }
    return CStmnt.seq(CExpr.fprintfstderr(msg2, args2.toArray(new CExpr[args2.size()])),
                      CExpr.exit(1));
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
   * @param tvalue the target of this call, CExpr.UNIT if none.
   *
   * @param args the arguments of this call, or, in case of an assignment, a
   * list of one element containing value to be assigned.
   *
   * @return pair of expression containing result value and statement to perform
   * the given access
   */
  Pair<CExpr, CStmnt> access(int cl, int c, int i, CExpr tvalue, List<CExpr> args)
  {
    CStmnt result;
    CExpr res = CExpr.UNIT;
    var isCall = _fuir.codeAt(c, i) == FUIR.ExprKind.Call;
    var cc0 = _fuir.accessedClazz  (cl, c, i);
    var tc = _fuir.accessTargetClazz(cl, c, i);
    var rt = _fuir.clazzResultClazz(cc0); // only needed if isCall

    if (_fuir.accessIsDynamic(cl, c, i))
      {
        var ol = new List<CStmnt>(CStmnt.lineComment("Dynamic access of " + _fuir.clazzAsString(cc0)));
        var ccs = _fuir.accessedClazzes(cl, c, i);
        if (CHECKS) check
          (_types.hasData(tc)); // target in dynamic call cannot be unit type
        var tvar = _names.newTemp();
        var tt0 = _types.clazz(tc);
        ol.add(CStmnt.decl(tt0, tvar, tvalue.castTo(tt0)));
        tvalue = tvar;
        if (isCall && _types.hasData(rt))
          {
            var resvar = _names.newTemp();
            res = resvar;
            ol.add(CStmnt.decl(_types.clazzField(cc0), resvar));
          }
        if (ccs.length == 0)
          {
            ol.add(reportErrorInCode("no targets for dynamic access of %s within %s",
                                     CExpr.string(_fuir.clazzAsString(cc0)),
                                     CExpr.string(_fuir.clazzAsString(cl ))));
          }
        var cazes = new List<CStmnt>();
        CStmnt acc = CStmnt.EMPTY;
        for (var cci = 0; cci < ccs.length; cci += 2)
          {
            var tt = ccs[cci  ];
            var cc = ccs[cci+1];
            var rti = _fuir.clazzResultClazz(cc);
            if (isCall)
              {
                var calpair = call(cl, tvalue, args, c, i, cc, false);
                var rv  = calpair._v0;
                var cal = calpair._v1;
                var as = CStmnt.EMPTY;
                if (rv != null && res != CExpr.UNIT)
                  {
                    if (rt != rti && _fuir.clazzIsRef(rt)) // NYI: Check why result can be different
                      {
                        rv = rv.castTo(_types.clazz(rt));
                      }
                    as = assign(res, rv, rt);
                  }
                acc = CStmnt.seq(CStmnt.lineComment("Call calls "+ _fuir.clazzAsString(cc) + " target: " + _fuir.clazzAsString(tt) + ":"),
                                 cal,
                                 as);
              }
            else
              {
                acc = assignField(tvalue, tc, tt, cc, args.get(0), rti);
              }
            cazes.add(CStmnt.caze(new List<>(_names.clazzId(tt)),
                                  CStmnt.seq(acc, CStmnt.BREAK)));
          }
        if (ccs.length > 2)
          {
            var id = tvar.deref().field(_names.CLAZZ_ID);
            acc = CStmnt.suitch(id, cazes,
                                reportErrorInCode("unhandled dynamic target %d in access of %s within %s",
                                                  id,
                                                  CExpr.string(_fuir.clazzAsString(cc0)),
                                                  CExpr.string(_fuir.clazzAsString(cl ))));
          }
        ol.add(acc);
        result = CStmnt.seq(ol);
      }
    else if (_fuir.clazzNeedsCode(cc0))
      {
        if (isCall)
          {
            var callpair = call(cl, tvalue, args, c, i, cc0, false);
            result = callpair._v1;
            res = callpair._v0;
          }
        else
          {
            result = assignField(tvalue, tc, tc, cc0, args.get(0), rt);
          }
      }
    else
      {
        result = reportErrorInCode("no code generated for static access to %s within %s",
                                   CExpr.string(_fuir.clazzAsString(cc0)),
                                   CExpr.string(_fuir.clazzAsString(cl )));
        res = null;
      }
    if (res != null && isCall)
      {
        res = _fuir.clazzIsVoidType(rt) ? null :
          _types.hasData(rt) && _fuir.clazzFieldIsAdrOfValue(cc0) ? res.deref() : res; // NYI: deref an outer ref to value type. Would be nice to have a separate statement for this
      }
    return new Pair(res, result);
  }


  /**
   * Create code to assign a value of given type to a field. In case value is of
   * unit type, this will produce no code, i.e., any possible side-effect of
   * target and value will be lost.
   */
  CStmnt assign(CExpr target, CExpr value, int type)
  {
    if (PRECONDITIONS) require
      (!_types.hasData(type) || (value != CExpr.UNIT));

    return _types.hasData(type)
      ? target.assign(value)
      : CStmnt.lineComment("unit type assignment to " + target.code());
  }


  /**
   * Create code to create a constant string and assign it to a new temp
   * variable.
   */
  CStmnt constString(byte[] bytes, CIdent tmp)
  {
    return constString(CExpr.string(bytes),
                       CExpr.int32const(bytes.length),
                       tmp);
  }


  /**
   * Create code to declare local var 'tmp', malloc an instance of clazz 'cl',
   * assign it to 'tmp' and, for a ref clazz, init the CLAZZ_ID field.
   */
  CStmnt declareAllocAndInitClazzId(int cl, CIdent tmp)
  {
    var t = _names.struct(cl);
    return CStmnt.seq(CStmnt.decl(t + "*", tmp),
                      tmp.assign(CExpr.call(malloc(), new List<>(CExpr.sizeOfType(t)))),
                      _fuir.clazzIsRef(cl) ? tmp.deref().field(_names.CLAZZ_ID).assign(_names.clazzId(cl)) : CStmnt.EMPTY);
  }


  /**
   * Create code to create a constant string and assign it to a new temp
   * variable.
   *
   * @param bytes C code resulting in char* to bytes of this string
   *
   * @param len length of this string, in bytes
   *
   * @param tmp local vad the new string should be assigned to
   */
  CStmnt constString(CExpr bytes, CExpr len, CIdent tmp)
  {
    var cs            = _fuir.clazz_conststring();
    var internalArray = _names.fieldName(_fuir.clazz_conststring_internalArray());
    var data          = _names.fieldName(_fuir.clazz_fuzionSysArray_u8_data());
    var length        = _names.fieldName(_fuir.clazz_fuzionSysArray_u8_length());
    var sysArray = fields(tmp, cs).field(internalArray);
    return CStmnt.seq(declareAllocAndInitClazzId(cs, tmp),
                      sysArray.field(data  ).assign(bytes.castTo("void *")),
                      sysArray.field(length).assign(len));
  }


  // NYI this conversion should be done in Fuzion
  CStmnt floatToConstString(CExpr expr, CIdent tmp)
  {
    // NYI how much do we need?
    var bufferSize = 50;
    var res = new CIdent("float_as_string_result");
    var usedChars = new CIdent("used_chars");
    var malloc = CExpr.call(malloc(),
      new List<>(CExpr.sizeOfType("char").mul(CExpr.int32const(bufferSize))));
    var sprintf = CExpr.call("sprintf", new List<>(res, CExpr.string("%.21g"), expr));

    return CStmnt.seq(CStmnt.decl("char*", res, malloc),
                      CStmnt.decl("int", usedChars, sprintf),
                      res.assign(CExpr.call(realloc(), new List<>(res, usedChars))),
                      constString(res, usedChars, tmp));
  }


  /**
   * Create code to assign value to a field
   *
   * @param stack the stack containing the value and the target instance
   *
   * @param tc the static target clazz
   *
   * @param tt the actual target clazz in case the assignment is dynamic
   *
   * @param f the field
   */
  CStmnt assignField(CExpr tvalue, int tc, int tt, int f, CExpr value, int rt)
  {
    if (_fuir.clazzIsRef(tc) && tc != tt)
      {
        tvalue = tvalue.castTo(_types.clazz(tt));
      }
    var af = accessField(tvalue, tt, f);
    if (_fuir.clazzIsRef(rt))
      {
        value = value.castTo(_types.clazz(rt));
      }
    return assign(af, value, rt);
  }


  /**
   * Create code to access a field
   *
   * @param t the target instance containing the field
   *
   * @param tc the static target clazz
   *
   * @param f the field
   *
   * @return the code to access field f, null if type is 'void', CExpr.UNIT if
   * type is 'unit'.
   */
  CExpr accessField(CExpr t, int tc, int f)
  {
    var rt = _fuir.clazzResultClazz(f);
    if (CHECKS) check
      (t != null || !_types.hasData(rt) || tc == _fuir.clazzUniverse());
    var occ   = _fuir.clazzOuterClazz(f);
    var vocc  = _fuir.clazzAsValue(occ);
    if (occ != tc && _fuir.clazzIsRef(occ))
      {
        t = t.castTo(_types.clazz(occ));  // t is a ref with different static type, so cast it to the actual type
      }
    return (_types.isScalar(vocc)     ? fields(t, tc)         :
            _fuir.clazzIsVoidType(rt) ? null :
            _types.hasData(rt)        ? field(tc, t, f) : CExpr.UNIT);
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
  Pair<CExpr, CStmnt> call(int cl, CExpr tvalue, List<CExpr> args, int c, int i, int cc, boolean pre)
  {
    var tc = _fuir.accessTargetClazz(cl, c, i);
    CStmnt result = CStmnt.EMPTY;
    var resultValue = CExpr.UNIT;
    var rt = _fuir.clazzResultClazz(cc);
    switch (pre ? FUIR.FeatureKind.Routine : _fuir.clazzKind(cc))
      {
      case Abstract :
        Errors.error("Call to abstract feature encountered.",
                     "Found call to  " + _fuir.clazzAsString(cc));
      case Routine  :
      case Intrinsic:
        {
          var a = args(tc, tvalue, args, cc, _fuir.clazzArgCount(cc));
          if (_fuir.clazzNeedsCode(cc))
            {
              if (!pre                               &&  // not calling pre-condition
                  cc == cl                           &&  // calling myself
                  _tailCall.callIsTailCall(cl, c, i) &&  // as a tail call
                  !_escape.doesCurEscape(cl)             // and current instance did not escape
                  )
                { // then we can do tail recursion optimization!
                  result = tailRecursion(cl, c, i, tc, a);
                  resultValue = null;
                }
              else
                {
                  var call = CExpr.call(_names.function(cc, pre), a);
                  result = call;
                  if (!pre)
                    {
                      CExpr res = _fuir.clazzIsVoidType(rt) ? null : CExpr.UNIT;
                      if (_types.hasData(rt))
                        {
                          var tmp = _names.newTemp();
                          res = tmp;
                          result = CStmnt.seq(CStmnt.decl(_types.clazz(rt), tmp),
                                              res.assign(call));
                        }
                      resultValue = res;
                    }
                }
            }
          break;
        }
      case Field:
        {
          resultValue = accessField(tvalue, tc, cc);
          break;
        }
      default:       throw new Error("This should not happen: Unknown feature kind: " + _fuir.clazzKind(cc));
      }
    return new Pair<>(resultValue, result);
  }


  /**
   * Create code for a tail recursive call to the current clazz.
   *
   * @param cl clazz id of clazz containing the call
   *
   * @param c the code block to compile
   *
   * @param i the index of the call within c
   *
   * @param tc the target clazz (type of outer) in this call
   *
   * @param a list of actual arguments to the tail recursive call.
   */
  CStmnt tailRecursion(int cl, int c, int i, int tc, List<CExpr> a)
  {
    var cur = _fuir.clazzIsRef(cl) ? fields(_names.CURRENT, cl)
                                   : _names.CURRENT.deref();

    var l = new List<CStmnt>();
    if (_types.hasData(tc) && !_tailCall.firstArgIsOuter(cl, c, i))
      {
        l.add(CStmnt.lineComment("tail recursion with changed target"));
        l.add(assign(CNames.OUTER, a.get(0), tc));
      }
    else
      {
        l.add(CStmnt.lineComment("tail recursion on same target"));
      }
    var vcl = _fuir.clazzAsValue(cl);
    var ac = _fuir.clazzArgCount(vcl);
    var aii = _types.hasData(tc) ? 1 : 0;
    for (int ai = 0; ai < ac; ai++)
      {
        var at = _fuir.clazzArgClazz(vcl, ai);
        if (_types.hasData(at))
          {
            var target = _types.isScalar(vcl)
              ? cur
              : cur.field(_names.fieldName(_fuir.clazzArg(vcl, ai)));
            l.add(assign(CIdent.arg(ai), a.get(aii), at));
                          aii = aii + 1;
          }
      }
    l.add(CStmnt.gowto("start"));
    return CStmnt.seq(l);
  }


  /**
   * Create C code to pass given number of arguments plus one implicit target
   * argument from the stack to a called feature.
   *
   * @param tc clazz id of the outer clazz of the called clazz, -1 to skip the
   * target argument
   *
   * @param cc clazz that is called
   *
   * @param stack the stack containing the C code of the args.
   *
   * @param argCount the number of arguments.
   *
   * @return list of arguments to be passed to CExpr.call
   */
  List<CExpr> args(int tc, CExpr tvalue, List<CExpr> args, int cc, int argCount)
  {
    if (argCount > 0)
      {
        var ac = _fuir.clazzArgClazz(cc, argCount-1);
        var a = args.get(argCount-1);
        var result = args(tc, tvalue, args, cc, argCount-1);
        if (_types.hasData(ac))
          {
            a = _fuir.clazzIsRef(ac) ? a.castTo(_types.clazz(ac)) : a;
            result.add(a);
          }
        return result;
      }
    else if (tc != -1)
      { // ref to outer instance, passed by reference
        var a = tc == _fuir.clazzUniverse() ? _names.UNIVERSE : tvalue;
        var or = _fuir.clazzOuterRef(cc);   // NYI: special handling of outer refs should not be part of BE, should be moved to FUIR
        if (or != -1)
          {
            var rc = _fuir.clazzResultClazz(or);
            var a2 =_fuir.clazzFieldIsAdrOfValue(or) ? a.adrOf() : a;
            var esc = _fuir.clazzOuterRefEscapes(cc);
            var a3 = esc && a.isLocalVar() ? CExpr.call(CNames.HEAP_CLONE._name, new List<>(a2, a.sizeOfExpr()))
                                           : a2;
            var a4 = esc || tc != rc ? a3.castTo(_types.clazzField(or)) : a3;
            return new List<>(a4);
          }
      }
    return new List<>();
  }


  /**
   * Create code for the C function implemeting the routine corresponding to the
   * given clazz.
   *
   * @param cl id of clazz to compile
   *
   * @param pre true to create the precondition function, not the function itself.
   *
   * @param body the code of the function, or null for a forward declaration.
   *
   * @return the C code
   */
  private CStmnt cFunctionDecl(int cl, boolean pre, CStmnt body)
  {
    var res = _fuir.clazzResultClazz(cl);
    var resultType = pre || !_types.hasData(res)
      ? "void"
      : _types.clazz(res);
    var argts = new List<String>();
    var argns = new List<CIdent>();
    var or = _fuir.clazzOuterRef(cl);
    if (or != -1)
      {
        argts.add(_types.clazzField(or));
        argns.add(CNames.OUTER);
      }
    var ac = _fuir.clazzArgCount(cl);
    for (int i = 0; i < ac; i++)
      {
        var at = _fuir.clazzArgClazz(cl, i);
        if (_types.hasData(at))
          {
            argts.add(_types.clazz(at));
            argns.add(CIdent.arg(i));
          }
      }
    return CStmnt.functionDecl(resultType, new CIdent(_names.function(cl, pre)), argts, argns, body);
  }


  /**
   * Create forward declarations for given clazz cl.
   *
   * @param cl id of clazz to compile
   *
   * @return C statements with the forward declarations required for cl.
   */
  public CStmnt forwards(int cl)
  {
    var l = new List<CStmnt>();
    if (_fuir.clazzNeedsCode(cl))
      {
        switch (_fuir.clazzKind(cl))
          {
          case Routine  :
          case Intrinsic: l.add(cFunctionDecl(cl, false, null));
          }
        if (_fuir.clazzContract(cl, FUIR.ContractKind.Pre, 0) != -1)
          {
            l.add(cFunctionDecl(cl, true, null));
          }
      }
    return CStmnt.seq(l);
  }


  /**
   * Create code for given clazz cl.
   *
   * @param cl id of clazz to compile
   *
   * @return C statements with the forward declarations required for cl.
   */
  public CStmnt code(int cl)
  {
    var l = new List<CStmnt>();
    if (_fuir.clazzNeedsCode(cl))
      {
        var ck = _fuir.clazzKind(cl);
        switch (ck)
          {
          case Routine:
          case Intrinsic:
            {
              l.add(CStmnt.lineComment("code for clazz#"+_names.clazzId(cl).code()+" "+_fuir.clazzAsString(cl)+":"));
              var o = ck == FUIR.FeatureKind.Routine ? codeForRoutine(cl, false)
                                                     : _intrinsics.code(this, cl);
              l.add(cFunctionDecl(cl, false, o));
            }
          }
        if (_fuir.clazzContract(cl, FUIR.ContractKind.Pre, 0) != -1)
          {
            l.add(CStmnt.lineComment("code for clazz#"+_names.clazzId(cl).code()+" precondition of "+_fuir.clazzAsString(cl)+":"));
            l.add(cFunctionDecl(cl, true, codeForRoutine(cl, true)));
          }
      }
    return CStmnt.seq(l);
  }


  /**
   * Create code for given clazz cl.
   *
   * @param cl id of clazz to generate code for
   *
   * @param pre true to create code for cl's precondition, false to create code
   * for cl itself.
   */
  CStmnt codeForRoutine(int cl, boolean pre)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzKind(cl) == FUIR.FeatureKind.Routine || pre);

    _names._tempVarId = 0;  // reset counter for unique temp variables for function results
    var cur = _fuir.clazzIsRef(cl) ? fields(_names.CURRENT, cl)
                                   : _names.CURRENT.deref();
    var l = new List<CStmnt>();
    l.add(_ai.process(cl, pre)._v1);
    var res = _fuir.clazzResultClazz(cl);
    if (!pre && _types.hasData(res))
      {
        var rf = _fuir.clazzResultField(cl);
        l.add(rf != -1 ? current(cl).field(_names.fieldName(rf)).ret()  // a routine, return result field
                       : current(cl).ret()                              // a constructor, return current instance
              );
      }
    return CStmnt.seq(CStmnt.lineComment(pre                       ? "for precondition only, need to check if it may escape" :
                                         _escape.doesCurEscape(cl) ? "instance may escape, so we need malloc here"
                                                                   : "instance does not escape, put it on stack"),
                      _escape.doesCurEscape(cl) ? declareAllocAndInitClazzId(cl, _names.CURRENT)
                                                : CStmnt.decl(_names.struct(cl), _names.CURRENT),
                      CStmnt.seq(l).label("start"));
  }


  /**
   * Return the current instance of the currently compiled clazz cl. This is a C
   * pointer in case _fuir.clazzIsRef(cl), or the C struct corresponding to cl
   * otherwise.
   */
  CExpr current(int cl)
  {
    var res1 = _names.CURRENT;
    var res2 = _fuir.clazzIsRef(cl)      ? res1 : res1.deref();
    var res3 = _escape.doesCurEscape(cl) ? res2 : res2.adrOf();
    return !_types.hasData(cl) ? CExpr.UNIT : res3;
  }


  /**
   * Create C code to access a field, dereferencing if needed.
   *
   * @param outercl the clazz id of the type of outer, used to tell if outer is ref or value
   *
   * @param outer C expression that result in the instance that contains the field
   *
   * @param field the field id of the accessed field
   */
  CExpr field(int outercl, CExpr outer, int field)
  {
    if (outercl == _fuir.clazzUniverse())
      {
        outer = _names.UNIVERSE;
      }
    return fields(outer, outercl).field(_names.fieldName(field));
  }

  /**
   * For an instance value refOrVal get the struct that contains its fields.
   *
   * @param refOrValue C expression to access an instance
   *
   * @param type the type of the instance, may be a ref or value type
   *
   * @return C expression of the struct that contains a field. In case type is a
   * references, refOrValue will be dereferenced and the fiields member will be
   * accessed.
   */
  CExpr fields(CExpr refOrVal, int type)
  {
    return _fuir.clazzIsRef(type) ? refOrVal.deref().field(_names.FIELDS_IN_REF_CLAZZ)
                                  : refOrVal;
  }

  /**
   * @return the name of malloc function that is used
   */
  String malloc()
  {
    return _options._useBoehmGC ? "GC_MALLOC" : "malloc";
  }


  /**
   * @return the name of realloc function that is used
   */
  String realloc()
  {
    return _options._useBoehmGC ? "GC_REALLOC" : "realloc";
  }

}

/* end of file */
