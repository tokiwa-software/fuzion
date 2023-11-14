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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.stream.Stream;

import dev.flang.fuir.FUIR;
import dev.flang.fuir.FUIR.SpecialClazzes;
import dev.flang.fuir.analysis.AbstractInterpreter;
import dev.flang.fuir.analysis.dfa.DFA;
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
     * @param cl the clazz we are compiling
     *
     * @param pre true iff we are compiling the precondition
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
    public CStmnt assignStatic(int cl, boolean pre, int tc, int f, int rt, CExpr tvalue, CExpr val)
    {
      return assignField(tvalue, tc, tc, f, val, rt);
    }


    /**
     * Perform an assignment of a value to a field in tvalue. The type of tvalue
     * might be dynamic (a reference). See FUIR.access*().
     *
     * @param cl id of clazz we are interpreting
     *
     * @param pre true iff interpreting cl's precondition, false for cl itself.
     *
     * @param c current code block
     *
     * @param i index of call in current code block
     *
     * @param tvalue the target instance
     *
     * @param avalue the new value to be assigned to the field.
     */
    public CStmnt assign(int cl, boolean pre, int c, int i, CExpr tvalue, CExpr avalue)
    {
      return access(cl, pre, c, i, tvalue, new List<>(avalue))._v1;
    }


    /**
     * Perform a call of a feature with target instance tvalue with given
     * arguments.  The type of tvalue might be dynamic (a reference). See
     * FUIR.access*().
     *
     * Result._v0 may be null to indicate that code generation should stop here
     * (due to an error or tail recursion optimization).
     */
    public Pair<CExpr, CStmnt> call(int cl, boolean pre, int c, int i, CExpr tvalue, List<CExpr> args)
    {
      var ccP = _fuir.accessedPreconditionClazz(cl, c, i);
      var cc0 = _fuir.accessedClazz            (cl, c, i);
      var ol = new List<CStmnt>();
      if (ccP != -1)
        {
          var callpair = C.this.call(cl, pre, tvalue, args, c, i, ccP, true);
          ol.add(callpair._v1);
        }
      var res = CExpr.UNIT;
      if (!_fuir.callPreconditionOnly(cl, c, i))
        {
          var r = access(cl, pre, c, i, tvalue, args);
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
     * Get the current instance
     */
    public Pair<CExpr, CStmnt> current(int cl, boolean pre)
    {
      return new Pair<>(C.this.current(cl, pre), CStmnt.EMPTY);
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
      return constData(constCl, d, true);
    }


    /**
     * Get a constant value of type constCl with given byte data d.
     *
     * @param constCl the clazz of the const we are creating
     *
     * @param d the serialized data to use for creating the constant
     *
     * @param onHeap should this constant be cloned to heap?
     *               Constants initialized by means of compound literals
     *               need to be allocated on heap because we may use the address
     *               of the constant and thus the constant may outlive the function
     *               it was created in.
     * @return
     */
    private Pair<CExpr, CStmnt> constData(int constCl, byte[] d, boolean onHeap /* NYI init "(larger)" constants only once, globally. */)
    {
      return switch (_fuir.getSpecialId(constCl))
        {
          case c_bool -> new Pair<>(primitiveExpression(SpecialClazzes.c_bool, ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_i8   -> new Pair<>(primitiveExpression(SpecialClazzes.c_i8,   ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_i16  -> new Pair<>(primitiveExpression(SpecialClazzes.c_i16,  ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_i32  -> new Pair<>(primitiveExpression(SpecialClazzes.c_i32,  ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_i64  -> new Pair<>(primitiveExpression(SpecialClazzes.c_i64,  ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_u8   -> new Pair<>(primitiveExpression(SpecialClazzes.c_u8,   ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_u16  -> new Pair<>(primitiveExpression(SpecialClazzes.c_u16,  ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_u32  -> new Pair<>(primitiveExpression(SpecialClazzes.c_u32,  ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_u64  -> new Pair<>(primitiveExpression(SpecialClazzes.c_u64,  ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_f32  -> new Pair<>(primitiveExpression(SpecialClazzes.c_f32,  ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_f64  -> new Pair<>(primitiveExpression(SpecialClazzes.c_f64,  ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_array_i8  -> constArray(constCl, SpecialClazzes.c_i8 , d);
          case c_array_i16 -> constArray(constCl, SpecialClazzes.c_i16, d);
          case c_array_i32 -> constArray(constCl, SpecialClazzes.c_i32, d);
          case c_array_i64 -> constArray(constCl, SpecialClazzes.c_i64, d);
          case c_array_u8  -> constArray(constCl, SpecialClazzes.c_u8 , d);
          case c_array_u16 -> constArray(constCl, SpecialClazzes.c_u16, d);
          case c_array_u32 -> constArray(constCl, SpecialClazzes.c_u32, d);
          case c_array_u64 -> constArray(constCl, SpecialClazzes.c_u64, d);
          case c_array_f32 -> constArray(constCl, SpecialClazzes.c_f32, d);
          case c_array_f64 -> constArray(constCl, SpecialClazzes.c_f64, d);
          case c_Const_String ->
          {
            yield new Pair<>(constString(d, onHeap), CStmnt.EMPTY);
          }
          default ->
          {
            var sb = new StringBuilder();
            var offset = 0;
            var argCount = _fuir.clazzArgCount(constCl);
            var l = new List<CStmnt>();

            for (int i = 0; i < argCount; i++)
              {
                var arg = _fuir.clazzArg(constCl, i);
                int bytes = _fuir.clazzArgFieldBytes(constCl, i);
                sb.append("." + _names.fieldName(arg).code());
                sb.append(" = ");
                var cd = constData(_fuir.clazzResultClazz(arg), Arrays.copyOfRange(d, offset, offset + bytes), false);
                l.add(cd._v1);
                sb.append(cd._v0.code());
                if (i + 1 != argCount)
                  {
                    sb.append(",");
                  }
                offset += bytes;
              }

            var cl = CExpr.compoundLiteral(_types.clazz(constCl), sb.toString());
            yield onHeap
              ? new Pair<>(CExpr.call(CNames.HEAP_CLONE._name, new List<>(cl.adrOf(), cl.sizeOfExpr())).castTo(_types.clazz(constCl) + "*").deref(), CStmnt.seq(l))
              : new Pair<>(cl, CStmnt.seq(l));
          }
        };
    }


    /**
     * Perform a match on value subv.
     */
    public Pair<CExpr, CStmnt> match(AbstractInterpreter<CExpr, CStmnt> ai, int cl, boolean pre, int c, int i, CExpr sub)
    {
      var subjClazz = _fuir.matchStaticSubject(cl, c, i);
      var uniyon    = sub.field(CNames.CHOICE_UNION_NAME);
      var hasTag    = !_fuir.clazzIsChoiceOfOnlyRefs(subjClazz);
      var refEntry  = uniyon.field(CNames.CHOICE_REF_ENTRY_NAME);
      var ref       = hasTag ? refEntry                   : _names.newTemp();
      var getRef    = hasTag ? CStmnt.EMPTY               : CStmnt.decl(_types.clazz(_fuir.clazzObject()), (CIdent) ref, refEntry);
      var tag       = hasTag ? sub.field(CNames.TAG_NAME) : ref.castTo("int64_t");
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
              if (!hasTag && _fuir.clazzIsRef(tc))  // do we need to check the clazzId of a ref?
                {
                  for (var h : _fuir.clazzInstantiatedHeirs(tc))
                    {
                      rtags.add(_names.clazzId(h).comment(_fuir.clazzAsString(h)));
                    }
                }
              else if (!_fuir.clazzIsVoidType(tc))
                {
                  ctags.add(CExpr.int32const(tagNum).comment(_fuir.clazzAsString(tc)));
                  if (CHECKS) check
                    (hasTag || !_fuir.hasData(tc));
                }
            }
          var sl = new List<CStmnt>();
          var field = _fuir.matchCaseField(cl, c, i, mc);
          if (field != -1)
            {
              var fclazz = _fuir.clazzResultClazz(field);     // static clazz of assigned field
              var f      = field(cl, C.this.current(cl, pre), field);
              var entry  = _fuir.clazzIsRef(fclazz) ? ref.castTo(_types.clazz(fclazz)) :
                           _fuir.hasData(fclazz)   ? uniyon.field(new CIdent(CNames.CHOICE_ENTRY_NAME + tags[0]))
                                                    : CExpr.UNIT;
              sl.add(C.this.assign(f, entry, fclazz));
            }
          sl.add(ai.process(cl, pre, _fuir.matchCaseCode(c, i, mc))._v1);
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
          var id = refEntry.deref().field(CNames.CLAZZ_ID);
          var notFound = reportErrorInCode("unexpected reference type %d found in match", id);
          tdefault = CStmnt.suitch(id, rcases, notFound);
        }
      return new Pair<>(CExpr.UNIT, CStmnt.seq(getRef, CStmnt.suitch(tag, tcases, tdefault)));
    }


    /**
     * Create a tagged value of type newcl from an untagged value for type valuecl.
     */
    public Pair<CExpr, CStmnt> tag(int cl, int valuecl, CExpr value, int newcl, int tagNum)
    {
      var res     = _names.newTemp();
      var tag     = res.field(CNames.TAG_NAME);
      var uniyon  = res.field(CNames.CHOICE_UNION_NAME);
      var entry   = uniyon.field(_fuir.clazzIsRef(valuecl) ||
                                 _fuir.clazzIsChoiceOfOnlyRefs(newcl) ? CNames.CHOICE_REF_ENTRY_NAME
                                                                      : new CIdent(CNames.CHOICE_ENTRY_NAME + tagNum));
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
     * Access the effect of type ecl that is installed in the environment.
     */
    public Pair<CExpr, CStmnt> env(int ecl)
    {
      var res = CNames.fzThreadEffectsEnvironment.deref().field(_names.env(ecl));
      var evi = CNames.fzThreadEffectsEnvironment.deref().field(_names.envInstalled(ecl));
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
      return CStmnt.iff(cc.field(CNames.TAG_NAME).not(),
                        CStmnt.seq(CExpr.fprintfstderr("*** failed " + ck + " on call to '%s'\n",
                                                       CExpr.string(_fuir.clazzAsString(cl))),
                                   CExpr.exit(1)));
    }

  }


  /*----------------------------  constants  ----------------------------*/


  /**
   * env var to enable debug output for tail call optimization:
   */
  static private final boolean FUZION_DEBUG_TAIL_CALL = "true".equals(System.getenv("FUZION_DEBUG_TAIL_CALL"));


  private static final int expectedClangVersion = 11;


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


  /*---------------------------  constructors  ---------------------------*/


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
    fuir = opt._Xdfa ?  new DFA(opt, fuir).new_fuir() : fuir;
    _fuir = fuir;
    _tailCall = new TailCall(fuir);
    _ai = new AbstractInterpreter<>(fuir, new CodeGen());

    _names = new CNames(fuir);
    _types = new CTypes(fuir, _names);
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
            createCode(cf, _options);
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

    var clangVersion = getClangVersion();
    // NYI should be clangVersion == expectedClangVersion but workflows etc. must be updated first
    if (_options._cCompiler == null && clangVersion < expectedClangVersion)
      {
        Errors.warning(clangVersion == -1
          ? "Could not determine clang version."
          : "Expected clang version " + expectedClangVersion + " or higher. Found version " + clangVersion + ".");
      }

    var cCompiler = _options._cCompiler != null ? _options._cCompiler : "clang";
    var command = new List<String>(cCompiler);
    if(_options._cFlags != null)
      {
        command.addAll(_options._cFlags.split(" "));
      }
    else
      {
        command.addAll(
          "-Wall",
          "-Werror",
          "-Wno-trigraphs",
          "-Wno-gnu-empty-struct",
          "-Wno-unused-variable",
          "-Wno-unused-label",
          "-Wno-unused-function",
          // allow infinite recursion
          "-Wno-infinite-recursion");

        if (_options._cCompiler == null && clangVersion >= 13)
          {
            command.addAll("-Wno-unused-but-set-variable");
          }

        command.addAll("-O3");
      }
    if(_options._useBoehmGC)
      {
        command.addAll("-lgc");
      }

    // disable trigraphs:
    // "Trigraphs are not popular and many compilers implement them incorrectly. Portable code should not rely on trigraphs being either converted or ignored."
    // source: https://gcc.gnu.org/onlinedocs/cpp/Initial-processing.html
    command.add("-fno-trigraphs");

    // NYI link libmath, libpthread only when needed
    command.addAll("-lm", "-lpthread", "-std=c11", "-o", name, cname);

    if (isWindows())
      {
        command.addAll("-lMswsock", "-lAdvApi32", "-lWs2_32");
      }

    _options.verbosePrintln(" * " + command.toString("", " ", ""));
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
   * @return The currently installed clang version or -1 on error.
   */
  private int getClangVersion()
  {
    try
      {
        var p = new ProcessBuilder().command(Arrays.asList("clang", "--version"))
        .start();
        p.waitFor();

        var clangVersion = new String(p
                                .getInputStream()
                                .readAllBytes())
                              .lines()
                              .findFirst()
                              .orElse("");

        return Integer.parseInt(clangVersion.replaceFirst(".*?(\\d+).*", "$1"));
      }
    catch (IOException | InterruptedException | NumberFormatException e)
      {
        return -1;
      }
  }


  /**
   * After the CFile has been opened and stored in _c, this methods generates
   * the code into this file.
   * @throws IOException
   */
  private void createCode(CFile cf, COptions _options) throws IOException
  {
    cf.print(
       "#define _POSIX_C_SOURCE 200809L\n" +
       (_options._useBoehmGC ? "#define GC_THREADS\n#include <gc.h>\n" : "")+
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
       "#include <errno.h>\n"+
       "#include <sys/stat.h>\n"+
       // defines _O_BINARY
       "#include <fcntl.h>\n");

    var fzH = _options.fuzionHome().resolve("include/fz.h").normalize().toAbsolutePath();
    cf.println("#include \"" + fzH.toString() + "\"\n");

    cf.print
      (CStmnt.decl("int", CNames.GLOBAL_ARGC));
    cf.print
      (CStmnt.decl("char **", CNames.GLOBAL_ARGV));
    cf.print
      (CStmnt.decl("pthread_mutex_t", CNames.GLOBAL_LOCK));

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
                 CStmnt.decl("_Thread_local", "struct " + CNames.fzThreadEffectsEnvironment.code() + "*", CNames.fzThreadEffectsEnvironment)
               )
             );
           }
       });

    cf.print(threadStartRoutine(true));

    cf.println("int main(int argc, char **argv) { ");

    // If we don't do the following stdout/err might be opened in text mode on windows.
    // This would lead to automatic insertions of carriage returns.
    cf.println("#if _WIN32");
    cf.println(" _setmode( _fileno( stdout ), _O_BINARY ); // reopen stdout in binary mode");
    cf.println(" _setmode( _fileno( stderr ), _O_BINARY ); // reopen stderr in binary mode");
    cf.println("#endif");

    cf.println(" {\n" +
               "  pthread_mutexattr_t attr;\n" +
               "  memset(&" + CNames.GLOBAL_LOCK.code() + ", 0, sizeof(" + CNames.GLOBAL_LOCK.code() + "));\n" +
               "  bool res = pthread_mutexattr_init(&attr) == 0 &&\n" +
               "  #if _WIN32\n" +
               "  // NYI #1646 setprotocol returns EINVAL on windows. \n" +
               "  #else\n" +
               "             pthread_mutexattr_setprotocol(&attr, PTHREAD_PRIO_INHERIT) == 0 &&\n" +
               "  #endif\n" +
               "             pthread_mutex_init(&" + CNames.GLOBAL_LOCK.code() + ", &attr) == 0;\n" +
               "  assert(res);\n" +
               " }\n");

    if (_options._useBoehmGC)
      {
        cf.println("GC_INIT(); /* Optional on Linux/X86 */");
      }

    cf.print(initializeEffectsEnvironment());

    var cl = _fuir.mainClazzId();

    cf.print(CStmnt.seq(CNames.GLOBAL_ARGC.assign(new CIdent("argc")),
                        CNames.GLOBAL_ARGV.assign(new CIdent("argv")),
                        _fuir.hasPrecondition(cl) ? CExpr.call(_names.function(cl, true), new List<>()) : CStmnt.EMPTY,
                        CExpr.call(_names.function(cl, false), new List<>())
                        ));
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
  Pair<CExpr, CStmnt> access(int cl, boolean pre, int c, int i, CExpr tvalue, List<CExpr> args)
  {
    CExpr res = CExpr.UNIT;
    var isCall = _fuir.codeAt(c, i) == FUIR.ExprKind.Call;
    var cc0 = _fuir.accessedClazz  (cl, c, i);
    var tc = _fuir.accessTargetClazz(cl, c, i);
    var rt = _fuir.clazzResultClazz(cc0); // only needed if isCall
    var ol = new List<CStmnt>();
    var ccs = _fuir.accessedClazzes(cl, c, i);
    if (ccs.length == 0)
      {
        if (isCall && (_fuir.hasData(rt) || _fuir.clazzIsVoidType(rt)))
          {
            ol.add(reportErrorInCode("no targets for access of %s within %s",
                                     CExpr.string(_fuir.clazzAsString(cc0)),
                                     CExpr.string(_fuir.clazzAsString(cl ))));
            res = null;
          }
        else
          {
            ol.add(CStmnt.lineComment("access to " + _fuir.codeAtAsString(cl, c, i) + " eliminated"));
          }
      }
    else
      {
        if (_fuir.hasData(tc) && _fuir.accessIsDynamic(cl, c, i) && ccs.length > 2)
          {
            ol.add(CStmnt.lineComment("Dynamic access of " + _fuir.clazzAsString(cc0)));
            var tvar = _names.newTemp();
            var tt0 = _types.clazz(tc);
            ol.add(CStmnt.decl(tt0, tvar, tvalue));
            tvalue = tvar;
          }
        if (isCall && _fuir.hasData(rt) && ccs.length > 2)
          {
            var resvar = _names.newTemp();
            res = resvar;
            ol.add(CStmnt.decl(_types.clazzField(cc0), resvar));
          }
        var cazes = new List<CStmnt>();
        CStmnt acc = CStmnt.EMPTY;
        for (var cci = 0; cci < ccs.length; cci += 2)
          {
            var tt = ccs[cci  ];                   // target clazz we match against
            var cc = ccs[cci+1];                   // called clazz in case of match
            var cco = _fuir.clazzOuterClazz(cc);   // outer clazz of called clazz, usually equal to tt unless tt is boxed value type
            var rti = _fuir.clazzResultClazz(cc);
            var tv = tt != tc ? tvalue.castTo(_types.clazz(tt)) : tvalue;
            if (_fuir.clazzIsBoxed(tt) && !_fuir.clazzIsRef(cco))
              { // in case we access the value in a boxed target, unbox it first:
                tv = fields(tv, tt);
              }
            if (isCall)
              {
                var calpair = call(cl, pre, tv, args, c, i, cc, false);
                var rv  = calpair._v0;
                acc = calpair._v1;
                if (ccs.length == 2)
                  {
                    res = rv;
                  }
                else if (_fuir.hasData(rt) && rv != null)
                  {
                    if (rt != rti && _fuir.clazzIsRef(rt)) // NYI: Check why result can be different
                      {
                        rv = rv.castTo(_types.clazz(rt));
                      }
                    acc = CStmnt.seq(CStmnt.lineComment("Call calls "+ _fuir.clazzAsString(cc) + " target: " + _fuir.clazzAsString(tt) + ":"),
                                     acc,
                                     assign(res, rv, rt));
                  }
              }
            else
              {
                acc = assignField(tv, tc, cco, cc, args.get(0), rti);
              }
            cazes.add(CStmnt.caze(new List<>(_names.clazzId(tt)),
                                  CStmnt.seq(acc, CStmnt.BREAK)));
          }
        if (ccs.length > 2)
          {
            var id = tvalue.deref().field(CNames.CLAZZ_ID);
            acc = CStmnt.suitch(id, cazes,
                                reportErrorInCode("unhandled dynamic target %d in access of %s within %s",
                                                  id,
                                                  CExpr.string(_fuir.clazzAsString(cc0)),
                                                  CExpr.string(_fuir.clazzAsString(cl ))));
          }
        ol.add(acc);
        res = isCall ?
          (_fuir.clazzIsVoidType(rt) ? null :
           _fuir.hasData(rt) && _fuir.clazzFieldIsAdrOfValue(cc0) ? res.deref() // NYI: deref an outer ref to value type. Would be nice to have a separate statement for this
                                                                   : res)
           :  res;
      }

    return new Pair<>(res, CStmnt.seq(ol));
  }


  /**
   * Create code to assign a value of given type to a field. In case value is of
   * unit type, this will produce no code, i.e., any possible side-effect of
   * target and value will be lost.
   */
  CStmnt assign(CExpr target, CExpr value, int type)
  {
    if (PRECONDITIONS) require
      (!_fuir.hasData(type) || (value != CExpr.UNIT));

    return _fuir.hasData(type)
      ? target.assign(value)
      : CStmnt.lineComment("unit type assignment to " + target.code());
  }


  /**
   * produce CExpr for given special clazz sc and byte buffer bbLE.
   *
   * @param sc the spezial clazz we we are generating the CExpr for.
   *
   * @param bbLE byte buffer (little endian)
   *
   * @return C expression that creates corresponding constant value.
   */
  public CExpr primitiveExpression(SpecialClazzes sc, ByteBuffer bbLE)
  {
    return switch (sc)
      {
      case c_bool -> bbLE.get(0) == 1 ? _names.FZ_TRUE: _names.FZ_FALSE;
      case c_u8   -> CExpr.uint8const (bbLE.get() & 0xff);
      case c_u16  -> CExpr.uint16const(bbLE.getChar());
      case c_u32  -> CExpr.uint32const(bbLE.getInt());
      case c_u64  -> CExpr.uint64const(bbLE.getLong());
      case c_i8   -> CExpr.int8const  (bbLE.get());
      case c_i16  -> CExpr.int16const (bbLE.getShort());
      case c_i32  -> CExpr.int32const (bbLE.getInt());
      case c_i64  -> CExpr.int64const (bbLE.getLong());
      case c_f32  -> CExpr.f32const   (bbLE.getFloat());
      case c_f64  -> CExpr.f64const   (bbLE.getDouble());
      default -> throw new Error(sc.name() + " is not a supported primitive.");
      };
  }


  /**
   * How many bytes are needed to encode specialClazz sc?
   *
   * @param sc a special clazz id.
   *
   * @return 1, 2, 4 or 8 => meaning 8bits, 16bits, 32bits, 64bits
   */
  public int bytesOfConst(SpecialClazzes sc)
  {
    return switch (sc)
      {
      case c_bool -> 1;
      case c_u8   -> 1;
      case c_u16  -> 2;
      case c_u32  -> 4;
      case c_u64  -> 8;
      case c_i8   -> 1;
      case c_i16  -> 2;
      case c_i32  -> 4;
      case c_i64  -> 8;
      case c_f32  -> 4;
      case c_f64  -> 8;
      default -> throw new Error(sc.name() + " is not a supported primitive.");
      };
  }


  /**
   * produce an expression to create an array
   * on the heap from the given data
   *
   * @param constCl, e.g. array
   * @param d
   * @param bytesPerField
   * @return
   */
  public Pair<CExpr, CStmnt> constArray(int constCl, SpecialClazzes elementType, byte[] d)
  {
    var bytesPerField    = bytesOfConst(elementType);
    var tmp              = _names.newTemp();
    var tmpR             = _names.newTemp();
    var c_internal_array = _fuir.lookup_array_internal_array(constCl);
    var c_sys_array      = _fuir.clazzResultClazz(c_internal_array);
    var c_data           = _fuir.lookup_fuzion_sys_internal_array_data(c_sys_array);
    var c_length         = _fuir.lookup_fuzion_sys_internal_array_length(c_sys_array);
    var internal_array   = _names.fieldName(c_internal_array);
    var data             = _names.fieldName(c_data);
    var length           = _names.fieldName(c_length);
    var sysArray         = fields(tmp, constCl).field(internal_array);
    var type             = _types.clazz(constCl);
    var typeR            = type + "*";
    var stmnts = CStmnt.seq(CStmnt.decl(type, tmp),
                           CStmnt.decl(typeR, tmpR),
                           sysArray.field(data).assign(CExpr.call(CNames.HEAP_CLONE._name, // NYI cast to void* should suffice but does
                                                                                           // not work yet for e.g. floats: say [f32 -17.3, f32 1.2]
                                                                  new List<>(arrayInit(d, elementType),
                                                                             CExpr.int32const(d.length)))),
                           sysArray.field(length).assign(CExpr.int32const(d.length / bytesPerField)),
                           tmpR.assign(CExpr.call(CNames.HEAP_CLONE._name,
                                                  new List<>(tmp.adrOf(),
                                                             tmp.sizeOfExpr())).castTo(typeR)));
    return new Pair<>(tmpR.deref(),
                      stmnts);
  }


  /**
   * create an array with the given bytes as input.
   *
   * @param d the data of the array
   *
   * @param elementType i8, f32, etc.
   */
  public CExpr arrayInit(byte[] d, SpecialClazzes elementType)
  {
    var bytesPerField = bytesOfConst(elementType);
    return new CExpr() {
      int precedence()
      {
        return 0;
      }

      void code(CString sb)
      {
        sb.append("(" + CTypes.scalar(elementType) + "[]){");
        for(int i = 0; i < d.length; i = i + bytesPerField)
          {
            primitiveExpression(elementType, ByteBuffer.wrap(d, i, bytesPerField).order(ByteOrder.LITTLE_ENDIAN))
              .code(sb);
            if (i + bytesPerField != d.length)
              {
                sb.append(", ");
              }
          }
        sb.append("}");
      }
    };
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
                      _fuir.clazzIsRef(cl) ? tmp.deref().field(CNames.CLAZZ_ID).assign(_names.clazzId(cl)) : CStmnt.EMPTY);
  }


  /**
   * Create CExpr to create a constant string.
   *
   * @param bytes the serialized bytes of the UTF-8 string.
   *
   * @param onHeap should the string be allocated on the heap?
   *
   * Example code:
   * `(fzT__RConst_u_String){.clazzId = 282, .fields = (fzT_Const_u_String){.fzF_0_internal_u_array = (fzT__L3393fuzion__sy__rray_w_u8){.fzF_0_data = (void *)"failed to encode code point ",.fzF_1_length = 28}}}`
   */
  CExpr constString(byte[] bytes, boolean onHeap)
  {
    return constString(CExpr.string(bytes), CExpr.int32const(bytes.length), onHeap);
  }


  /**
   * Create CExpr to create a constant string.
   *
   * @param str CExpr the creates a c string.
   *
   * @param len CExpr that returns the size_t of the string
   *
   * @param onHeap should the string be allocated on the heap?
   *
   * Example code:
   * `(fzT__RConst_u_String){.clazzId = 282, .fields = (fzT_Const_u_String){.fzF_0_internal_u_array = (fzT__L3393fuzion__sy__rray_w_u8){.fzF_0_data = (void *)"failed to encode code point ",.fzF_1_length = 28}}}`
   */
  CExpr constString(CExpr str, CExpr len, boolean onHeap)
  {
    var data          = _names.fieldName(_fuir.clazz_fuzionSysArray_u8_data());
    var length        = _names.fieldName(_fuir.clazz_fuzionSysArray_u8_length());

    var sysArray = CExpr.compoundLiteral(
        _types.clazz(_fuir.clazzResultClazz(_fuir.clazz_Const_String_internal_array())),
        "." + data.code() + " = " + str.castTo("void *").code() +  "," +
          "." + length.code() + " = " + len.code());

    var internal_array = _names.fieldName(_fuir.clazz_Const_String_internal_array());

    var constStr = CExpr
      .compoundLiteral(
        _types.clazz(_fuir.clazzAsValue(_fuir.clazz_Const_String())),
        "." + internal_array.code() + " = " + sysArray.code());

    var result = CExpr
      .compoundLiteral(
        _names.struct(_fuir.clazz_Const_String()),
        "." + CNames.CLAZZ_ID.code() + " = " + _names.clazzId(_fuir.clazz_Const_String()).code() + ", " +
          "." + CNames.FIELDS_IN_REF_CLAZZ.code() + " = " + constStr.code());

    return onHeap
      ? CExpr.call(CNames.HEAP_CLONE._name, new List<>(result.adrOf(), result.sizeOfExpr()))
      : result.adrOf();
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
    if (_fuir.clazzIsRef(tt) && tc != tt)
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
      (t != null || !_fuir.hasData(rt) || tc == _fuir.clazzUniverse());
    var occ   = _fuir.clazzOuterClazz(f);
    var vocc  = _fuir.clazzAsValue(occ);
    return (_types.isScalar(vocc)     ? fields(t, tc)         :
            _fuir.clazzIsVoidType(rt) ? null :
            _fuir.hasData(rt)         ? field(tc, t, f) : CExpr.UNIT);
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
  Pair<CExpr, CStmnt> call(int cl, boolean pre, CExpr tvalue, List<CExpr> args, int c, int i, int cc, boolean preCalled)
  {
    var tc = _fuir.clazzOuterClazz(cc);
    CStmnt result = CStmnt.EMPTY;
    var resultValue = CExpr.UNIT;
    var rt = _fuir.clazzResultClazz(cc);
    switch (preCalled ? FUIR.FeatureKind.Routine : _fuir.clazzKind(cc))
      {
      case Abstract :
        Errors.error("Call to abstract feature encountered.",
                     "Found call to  " + _fuir.clazzAsString(cc));
      case Routine  :
      case Intrinsic:
      case Native   :
        {
          var a = args(tvalue, args, cc, _fuir.clazzArgCount(cc));
          if (_fuir.clazzNeedsCode(cc))
            {

              if (FUZION_DEBUG_TAIL_CALL                                 &&
                  !preCalled                                             &&  // not calling pre-condition
                  cc == cl                                               &&  // calling myself
                  _tailCall.callIsTailCall(cl, c, i)                     &&  // as a tail call
                  _fuir.lifeTime(cl, pre).ordinal() >
                  FUIR.LifeTime.Call.ordinal()                               // and current instance did not escape
                )
                {
                  System.out.println("Escapes, no tail call opt possible: " + _fuir.clazzAsStringNew(cl) + ", lifetime: " + _fuir.lifeTime(cl, pre).name());
                }

              if (!preCalled                                             &&  // not calling pre-condition
                  cc == cl                                               &&  // calling myself
                  _tailCall.callIsTailCall(cl, c, i)                     &&  // as a tail call
                  _fuir.lifeTime(cl, pre).ordinal() <=
                  FUIR.LifeTime.Call.ordinal()                               // and current instance did not escape
                )
                { // then we can do tail recursion optimization!
                  result = tailRecursion(cl, c, i, tc, a);
                  resultValue = null;
                }
              else
                {
                  var call = CExpr.call(_names.function(cc, preCalled), a);
                  result = call;
                  if (!preCalled)
                    {
                      CExpr res = _fuir.clazzIsVoidType(rt) ? null : CExpr.UNIT;
                      if (_fuir.hasData(rt))
                        {
                          var tmp = _names.newTemp();
                          res = tmp;
                          var heapClone = CStmnt.EMPTY;
                          if (_fuir.doesResultEscape(cl, c, i))
                            {
                              var tmp2 = _names.newTemp();
                              heapClone = CStmnt.seq(CStmnt.decl(_types.clazz(rt)+"*", tmp2),
                                                     tmp2.assign(CExpr.call(CNames.HEAP_CLONE._name, new List<>(res.adrOf(), res.sizeOfExpr())).castTo(_types.clazz(rt)+"*")));
                              res = tmp2.deref();
                            }
                          result = CStmnt.seq(CStmnt.decl(_types.clazz(rt), tmp),
                                              tmp.assign(call),
                                              heapClone);
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
    var l = new List<CStmnt>();
    if (_fuir.hasData(tc) && !_tailCall.firstArgIsOuter(cl, c, i))
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
    var aii = _fuir.hasData(tc) ? 1 : 0;
    for (int ai = 0; ai < ac; ai++)
      {
        var at = _fuir.clazzArgClazz(vcl, ai);
        if (_fuir.hasData(at))
          {
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
   * @param cc clazz that is called
   *
   * @param stack the stack containing the C code of the args.
   *
   * @param argCount the number of arguments.
   *
   * @return list of arguments to be passed to CExpr.call
   */
  List<CExpr> args(CExpr tvalue, List<CExpr> args, int cc, int argCount)
  {
    List<CExpr> result;
    if (argCount > 0)
      {
        var ac = _fuir.clazzArgClazz(cc, argCount-1);
        var a = args.get(argCount-1);
        result = args(tvalue, args, cc, argCount-1);
        if (_fuir.hasData(ac))
          {
            a = _fuir.clazzIsRef(ac) ? a.castTo(_types.clazz(ac)) : a;
            result.add(a);
          }
      }
    else
      {
        var oc = _fuir.clazzOuterClazz(cc);
        var or = _fuir.clazzOuterRef(cc);
        result = new List<>();
        if (or != -1 && _fuir.hasData(oc))
          {
            result.add(_fuir.clazzIsRef(oc)             ? tvalue        .castTo(_types.clazzField(_fuir.clazzOuterRef(cc))) :
                       /* NYI: special handling in backend should be
                        * replaced by AdrOf in FUIR code: */
                       _fuir.clazzFieldIsAdrOfValue(or) ? tvalue.adrOf().castTo(_types.clazzField(_fuir.clazzOuterRef(cc)))
                                                        : tvalue);
          }
      }
    return result;
  }


  /**
   * Create code for the C function implementing the routine corresponding to the
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
    var resultType = pre || !_fuir.hasData(res)
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
        if (_fuir.hasData(at))
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
          case Intrinsic:
          case Native   : l.add(cFunctionDecl(cl, false, null));
          }
        if (_fuir.hasPrecondition(cl))
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
          case Native:
            {
              l.add(CStmnt.lineComment("code for clazz#"+_names.clazzId(cl).code()+" "+_fuir.clazzAsString(cl)+":"));
              var o = ck == FUIR.FeatureKind.Routine ? codeForRoutine(cl, false) :
                      ck == FUIR.FeatureKind.Native  ? codeForNative(cl)
                                                     : _intrinsics.code(this, cl);
              l.add(cFunctionDecl(cl, false, o));
            }
          }
        if (_fuir.hasPrecondition(cl))
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
    var l = new List<CStmnt>();
    l.add(_ai.process(cl, pre)._v1);
    var res = _fuir.clazzResultClazz(cl);
    if (!pre && _fuir.hasData(res))
      {
        var rf = _fuir.clazzResultField(cl);
        l.add(rf != -1 ? current(cl, pre).field(_names.fieldName(rf)).ret()  // a routine, return result field
                       : current(cl, pre).ret()                              // a constructor, return current instance
              );
      }
    var allocCurrent = switch (_fuir.lifeTime(cl, pre))
      {
      case Call      -> CStmnt.seq(CStmnt.lineComment("cur does not escape, alloc on stack"), CStmnt.decl(_names.struct(cl), CNames.CURRENT));
      case Unknown   -> CStmnt.seq(CStmnt.lineComment("cur may escape, so use malloc"      ), declareAllocAndInitClazzId(cl, CNames.CURRENT));
      case Undefined -> CExpr.dummy("undefined life time");
      };
    return CStmnt.seq(allocCurrent,
                      CStmnt.seq(l).label("start"));
  }


  /**
   * Create code for a given native clazz cl.
   *
   * @param cl id of native clazz to generate code for
   */
  CStmnt codeForNative(int cl)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzKind(cl) == FUIR.FeatureKind.Native);

    var args = new List<CExpr>();

    for (var i = 0; i < _fuir.clazzArgCount(cl); i++)
      {
        var ai = new CIdent("arg" + i);
        var ac = _fuir.clazzArgClazz(cl, i);

        switch (_fuir.getSpecialId(ac))
          {
            case c_u8, c_u16, c_u32, c_u64,
                 c_i8, c_i16, c_i32, c_i64,
                 c_f32, c_f64              -> args.add(ai);
            case c_sys_ptr                 -> args.add(ai.castTo("void*"));
            default                        -> {}
          };
      }

    var rc = _fuir.clazzResultClazz(cl);
    return switch (_fuir.getSpecialId(rc))
      {
        case c_Const_String -> {
          var str = new CIdent("str");
          yield CStmnt.seq(
            CExpr.decl("char*", str, CExpr.call(_fuir.clazzBaseName(cl), args)),
            constString(str, CExpr.call("strlen", new List<>(str)), true)
              .castTo(_types.clazz(rc))
              .ret());
        }
        default -> CStmnt.seq(CExpr.call(_fuir.clazzBaseName(cl), args).ret());
      };
  }


  /**
   * Return the current instance of the currently compiled clazz cl. This is a C
   * pointer in case _fuir.clazzIsRef(cl), or the C struct corresponding to cl
   * otherwise.
   *
   * @param cl id of clazz we are generating code for
   *
   * @param pre true iff generating code for cl's precondition, false for cl itself.
   */
  CExpr current(int cl, boolean pre)
  {
    var res1 = CNames.CURRENT;
    var res2 = _fuir.clazzIsRef(cl) ? res1 : res1.deref();
    var res3 =  _fuir.lifeTime(cl, pre).ordinal() <= FUIR.LifeTime.Call.ordinal() ? res2.adrOf() : res2;
    return !_fuir.hasData(cl) ? CExpr.UNIT : res3;
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
        outer = CNames.UNIVERSE;
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
   * references, refOrValue will be dereferenced and the fields member will be
   * accessed.
   */
  CExpr fields(CExpr refOrVal, int type)
  {
    return _fuir.clazzIsRef(type) ? refOrVal.deref().field(CNames.FIELDS_IN_REF_CLAZZ)
                                  : refOrVal;
  }

  /**
   * @return the name of malloc function that is used
   */
  String malloc()
  {
    return "fzE_malloc_safe";
  }


  /**
   * Is the compiler running on windows?
   * @return
   */
  boolean isWindows()
  {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }

}

/* end of file */
