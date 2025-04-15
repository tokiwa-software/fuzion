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

import static dev.flang.ir.IR.NO_CLAZZ;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import dev.flang.fuir.FUIR;
import dev.flang.fuir.SpecialClazzes;
import dev.flang.fuir.analysis.AbstractInterpreter;
import dev.flang.fuir.analysis.TailCall;
import dev.flang.ir.IR.FeatureKind;
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
   * Expression processor used with AbstractInterpreter to generate C code.
   */
  class CodeGen extends AbstractInterpreter.ProcessExpression<CExpr,CStmnt>
  {


    /**
     * Join a List of RESULT from subsequent statements into a compound
     * statement.  For a code generator, this could, e.g., join statements "a :=
     * 3;" and "b(x);" into a block "{ a := 3; b(x); }".
     */
    @Override
    public CStmnt sequence(List<CStmnt> l)
    {
      return CStmnt.seq(l);
    }


    /*
     * Produce the unit type value.  This is used as a placeholder
     * for the universe instance as well as for the instance 'unit'.
     */
    @Override
    public CExpr unitValue()
    {
      return CExpr.UNIT;
    }


    /**
     * Called before each expression is processed. May be used to, e.g., produce
     * tracing code for debugging or a comment.
     */
    @Override
    public CStmnt expressionHeader(int s)
    {
      return comment(String.format("%4d: %s", s, _fuir.codeAtAsString(s)));
    }


    /**
     * A comment, adds human readable information
     */
    @Override
    public CStmnt comment(String s)
    {
      return CStmnt.lineComment(s);
    }


    /**
     * no operation, like comment, but without giving any comment.
     */
    @Override
    public CStmnt nop()
    {
      return CStmnt.EMPTY;
    }


    /**
     * Create code to assign value to a given field w/o dynamic binding.
     *
     * @param s site of the expression causing this assignment
     *
     * @param tc clazz id of the target instance
     *
     * @param f clazz id of the assigned field
     *
     * @param tvalue the target instance
     *
     * @param val the new value to be assigned to the field.
     *
     * @return statement to perform the given access
     */
    @Override
    public CStmnt assignStatic(int s, int tc, int f, CExpr tvalue, CExpr val)
    {
      return assignField(tvalue, tc, tc, f, val, _fuir.clazzResultClazz(f));
    }


    /**
     * Perform an assignment of a value to a field in tvalue. The type of tvalue
     * might be dynamic (a reference). See FUIR.access*().
     *
     * @param s site of the assignment
     *
     * @param tvalue the target instance
     *
     * @param avalue the new value to be assigned to the field.
     */
    @Override
    public CStmnt assign(int s, CExpr tvalue, CExpr avalue)
    {
      return access(s, tvalue, new List<>(avalue)).v1();
    }


    /**
     * Perform a call of a feature with target instance tvalue with given
     * arguments.  The type of tvalue might be dynamic (a reference). See
     * FUIR.access*().
     *
     * Result.v0() may be null to indicate that code generation should stop here
     * (due to an error or tail recursion optimization).
     */
    @Override
    public Pair<CExpr, CStmnt> call(int s, CExpr tvalue, List<CExpr> args)
    {
      var r = access(s, tvalue, args);
      return new Pair<>(r.v0(), CStmnt.seq(new List<>(r.v1())));
    }


    /**
     * For a given value v of value type vc create a boxed ref value of type rc.
     */
    @Override
    public Pair<CExpr, CStmnt> box(int s, CExpr val, int vc, int rc)
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
    @Override
    public Pair<CExpr, CStmnt> current(int s)
    {
      return new Pair<>(C.this.current(s), CStmnt.EMPTY);
    }


    /**
     * Get the outer instance the given clazz is called on.
     */
    @Override
    public Pair<CExpr, CStmnt> outer(int s)
    {
      var cl = _fuir.clazzAt(s);
      CExpr result = CNames.OUTER;
      if (_fuir.clazzFieldIsAdrOfValue(_fuir.clazzOuterRef(cl)))
        {
          result = result.deref();
        }
      return new Pair<>(result, CStmnt.EMPTY);
    }


    /**
     * Get the argument #i
     */
    @Override
    public CExpr arg(int s, int i) { return CIdent.arg(i); }


    /**
     * Get a constant value of type constCl with given byte data d.
     */
    @Override
    public Pair<CExpr, CStmnt> constData(int s, int constCl, byte[] d)
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
      return switch (_fuir.getSpecialClazz(constCl))
        {
          case c_bool -> new Pair<>(primitiveExpression(SpecialClazzes.c_bool, ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_i8   -> new Pair<>(primitiveExpression(SpecialClazzes.c_i8,   ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_i16  -> new Pair<>(primitiveExpression(SpecialClazzes.c_i16,  ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_i32  -> new Pair<>(primitiveExpression(SpecialClazzes.c_i32,  ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_i64  -> new Pair<>(primitiveExpression(SpecialClazzes.c_i64,  ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_u8   -> new Pair<>(primitiveExpression(SpecialClazzes.c_u8,   ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_u16  -> new Pair<>(primitiveExpression(SpecialClazzes.c_u16,  ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_u32  -> new Pair<>(primitiveExpression(SpecialClazzes.c_u32,  ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_u64  -> new Pair<>(primitiveExpression(SpecialClazzes.c_u64,  ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_f32  -> new Pair<>(primitiveExpression(SpecialClazzes.c_f32,  ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_f64  -> new Pair<>(primitiveExpression(SpecialClazzes.c_f64,  ByteBuffer.wrap(d).position(4).order(ByteOrder.LITTLE_ENDIAN)),CStmnt.EMPTY);
          case c_String -> new Pair<>(boxedConstString(Arrays.copyOfRange(d, 4, ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).getInt() + 4)),CStmnt.EMPTY);
          default     -> {
            if (CHECKS)
              check(!_fuir.clazzIsRef(constCl)); // NYI currently no refs

            var result = _fuir.clazzIsArray(constCl)
              ? constArray(constCl, d)
              : constValue(constCl, d);

            // NYI without this heap clone tests ternary and unary are failing.
            yield onHeap
              ? new Pair<>(CExpr
                              .call(CNames.HEAP_CLONE._name, new List<>(result.adrOf(), result.sizeOfExpr()))
                              .castTo(_types.clazz(constCl) + " *")
                              .deref(),
                             CStmnt.EMPTY)
              : new Pair<>(result, CStmnt.EMPTY);
          }
        };
    }


    /**
     * create a value constant via means of compound literals.
     *
     * @param constCl, e.g. {@code codepoint 65}
     *
     * @param d the serialized data for initializing the code
     */
    private CExpr constValue(int constCl, byte[] d)
    {
      var sb = new StringBuilder();
      var argCount = _fuir.clazzArgCount(constCl);
      var l = new List<CStmnt>();

      var bb = ByteBuffer.wrap(d);
      for (int i = 0; i < argCount; i++)
        {
          var arg = _fuir.clazzArg(constCl, i);
          var fr = _fuir.clazzArgClazz(constCl, i);
          var bytes = _fuir.deserializeConst(fr, bb);
          sb.append("." + _names.fieldName(arg).code());
          sb.append(" = ");
          var cd = constData(_fuir.clazzResultClazz(arg), bytes, false);
          l.add(cd.v1());
          sb.append(cd.v0().code());
          if (i + 1 != argCount)
            {
              sb.append(",");
            }
        }

      return CExpr.compoundLiteral(_types.clazz(constCl), sb.toString());
    }


    /**
     * create a constant fuzion array
     *
     * @param constCl, e.g. {@code array (codepoint u32)}
     *
     * @param d the serialized data
     */
    private CExpr constArray(int constCl, byte[] d)
    {
      var elementType      = _fuir.inlineArrayElementClazz(constCl);
      var c_internal_array = _fuir.lookup_array_internal_array(constCl);
      var c_sys_array      = _fuir.clazzResultClazz(c_internal_array);
      var c_data           = _fuir.lookup_fuzion_sys_internal_array_data(c_sys_array);
      var c_length         = _fuir.lookup_fuzion_sys_internal_array_length(c_sys_array);
      var internal_array   = _names.fieldName(c_internal_array);
      var data             = _names.fieldName(c_data);
      var length           = _names.fieldName(c_length);

      var bb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
      var elCount = bb.getInt();

      var sb = new StringBuilder();
      // empty initializer is only allowed since C23, e.g. reg_issue2478 fails without this
      if (!_fuir.clazzIsUnitType(elementType))
        {
          sb.append("." + data.code());
          sb.append(" = ");
          sb.append(arrayInit(d, elementType).code() + ",");
        }
      sb.append("." + length.code());
      sb.append(" = ");
      sb.append(CExpr.int32const(elCount).code());
      var ia = CExpr.compoundLiteral(_types.clazz(c_sys_array), sb.toString());

      sb = new StringBuilder();
      sb.append("." + internal_array.code());
      sb.append(" = ");
      sb.append(ia.code());

      return CExpr.compoundLiteral(_types.clazz(constCl), sb.toString());
    }


    /**
     * create a c array with the given bytes as input.
     *
     * @param d the data of the array
     *
     * @param elementType i8, f32, etc.
     */
    public CExpr arrayInit(byte[] d, int elementType)
    {
      var result = new CExpr() {
        int precedence()
        {
          return 0;
        }

        void code(CString sb)
        {
          sb.append("(" + _types.clazz(elementType) + "[]){");

          var bb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
          var elCount = bb.getInt();

          // empty initializer is only allowed since C23
          if (elCount == 0 && !_fuir.clazzIsUnitType(elementType))
            {
              sb.append("0");
            }

          for (int idx = 0; idx < elCount; idx++)
            {
              var b = _fuir.deserializeConst(elementType, bb);

              constData(elementType, b, false)
                .v0()
                .code(sb);

              if (idx+1 < elCount)
                {
                  sb.append(",");
                }
            }
          sb.append("}");
        }
      };
      // since in C, an array is a pointer we have to heap clone this array.
      return CExpr.call(CNames.HEAP_CLONE._name, new List<>(result, result.sizeOfExpr()));
    }


    /**
     * Perform a match on value subv.
     */
    @Override
    public CStmnt match(int s, AbstractInterpreter<CExpr, CStmnt> ai, CExpr sub)
    {
      var subjClazz = _fuir.matchStaticSubject(s);
      var uniyon    = sub.field(CNames.CHOICE_UNION_NAME);
      var hasTag    = !_fuir.clazzIsChoiceOfOnlyRefs(subjClazz);
      var refEntry  = uniyon.field(CNames.CHOICE_REF_ENTRY_NAME);
      var ref       = hasTag ? refEntry                   : _names.newTemp();
      var getRef    = hasTag ? CStmnt.EMPTY               : CStmnt.decl(_types.clazz(_fuir.clazzAny()), (CIdent) ref, refEntry);
      var tag       = hasTag ? sub.field(CNames.TAG_NAME) : ref.castTo("int64_t");
      var tcases    = new List<CStmnt>(); // cases depending on tag value or ref cast to int64
      var rcases    = new List<CStmnt>(); // cases depending on clazzId of ref type
      CStmnt tdefault = null;
      for (var mc = 0; mc < _fuir.matchCaseCount(s); mc++)
        {
          var ctags = new List<CExpr>();
          var rtags = new List<CExpr>();
          var tags = _fuir.matchCaseTags(s, mc);
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
          if (tags.length > 0)
             {
               var sl = new List<CStmnt>();
               var field = _fuir.matchCaseField(s, mc);
               if (field != -1)
                 {
                   var fclazz = _fuir.clazzResultClazz(field);     // static clazz of assigned field
                   var cl     = _fuir.clazzAt(s);
                   var f      = field(cl, C.this.current(s), field);
                   var entry  = _fuir.clazzIsRef(fclazz) ? ref.castTo(_types.clazz(fclazz)) :
                                _fuir.hasData(fclazz)   ? uniyon.field(new CIdent(CNames.CHOICE_ENTRY_NAME + tags[0]))
                                                         : CExpr.UNIT;
                   sl.add(C.this.assign(f, entry, fclazz));
                 }
               sl.add(ai.processCode(_fuir.matchCaseCode(s, mc)).v1());
               sl.add(CStmnt.BREAK);
               var cazecode = CStmnt.seq(sl);
               tcases.add(CStmnt.caze(ctags, cazecode));  // tricky: this a NOP if ctags.isEmpty
               if (!rtags.isEmpty()) // we need default clause to handle refs without a tag
                 {
                   rcases.add(CStmnt.caze(rtags, cazecode));
                   tdefault = cazecode;
                 }
             }
        }
      if (rcases.size() >= 2)
        { // more than two reference cases: we have to create separate switch of clazzIds for refs
          var id = refEntry.deref().field(CNames.CLAZZ_ID);
          var notFound = reportErrorInCode0("unexpected reference type %d found in match", id);
          tdefault = CStmnt.suitch(id, rcases, notFound);
        }
      return CStmnt.seq(getRef, CStmnt.suitch(tag, tcases, tdefault));
    }


    /**
     * Create a tagged value of type newcl from an untagged value for type valuecl.
     */
    @Override
    public Pair<CExpr, CStmnt> tag(int s, CExpr value, int newcl, int tagNum)
    {
      var valuecl = _fuir.clazzChoice(newcl, tagNum);
      var res     = _names.newTemp();
      var tag     = res.field(CNames.TAG_NAME);
      var uniyon  = res.field(CNames.CHOICE_UNION_NAME);
      var entry   = uniyon.field(choiceEntryName(valuecl, newcl, tagNum));
      if (_fuir.clazzIsUnitType(valuecl) && _fuir.clazzIsChoiceOfOnlyRefs(newcl))
        {// replace unit-type values by 0, 1, 2, 3,... cast to ref Object
          if (CHECKS) check
            (value == CExpr.UNIT);
          if (tagNum >= CConstants.PAGE_SIZE)
            {
              Errors.error("Number of tags for choice type exceeds page size.",
                           "While creating code for '" + _fuir.siteAsString(s) + "'\n" +
                           "Found in choice type '" + _fuir.clazzAsString(newcl)+ "'\n");
            }
          value = CExpr.int32const(tagNum);
          valuecl = _fuir.clazzAny();
        }
      if (_fuir.clazzIsRef(valuecl))
        {
          value = value.castTo(_types.clazz(_fuir.clazzAny()));
        }
      var o = CStmnt.seq(CStmnt.lineComment("Tag a value to be of choice type " + _fuir.clazzAsString(newcl) +
                                            " static value type " + _fuir.clazzAsString(valuecl)),
                         CStmnt.decl(_types.clazz(newcl), res),
                         _fuir.clazzIsChoiceOfOnlyRefs(newcl) ? CStmnt.EMPTY : tag.assign(CExpr.int32const(tagNum)),
                         C.this.assign(entry, value, valuecl));

      return new Pair<>(res, o);
    }


    /**
     * Generate code to terminate the execution immediately.
     *
     * @param msg a message explaining the illegal state
     */
    @Override
    public CStmnt reportErrorInCode(String msg)
    {
      return reportErrorInCode0("%s", CExpr.string(msg));
    }

  }


  /*----------------------------  constants  ----------------------------*/


  /*
   * If you want the c-backend to link the JVM,
   * set this environment variable to e.g.:
   * JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
   */
  static final String JAVA_HOME = System.getenv("JAVA_HOME");


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


  /**
   * Sorted array of clazzes of all effects that are ever instated, replaced, or
   * aborted. Will be created during CompilePhase.STRUCTS.
   */
  int[] _effectClazzes;


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
    var cl = _fuir.mainClazz();
    var name = _options._binaryName != null ? _options._binaryName : _fuir.clazzBaseName(cl);
    var cf = new CFile(name, _options._keepGeneratedCode, false);
    var hf = new CFile(name, _options._keepGeneratedCode, true);
    _options.verbosePrintln(" + " + cf.fileName());
    try
      {
        createCode(cf, hf, _options);
      }
    catch (IOException io)
      {
        Errors.error("C backend I/O error",
                     "While writing code to '" + cf.fileName() + "', received I/O error '" + io + "'");
      }
    finally
      {
        cf.close();
        hf.close();
      }
    Errors.showAndExit();

    var command = buildCommand(name, cf);

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
   * @param name the name of the produced binary
   *
   * @param cf the generated code
   *
   * @return list of cmd and args to build the c code.
   */
  private List<String> buildCommand(String name, CFile cf)
  {
    var clangVersion = getClangVersion();
    // NYI should be clangVersion == expectedClangVersion but workflows etc. must be updated first
    if (_options._cCompiler == null && clangVersion < expectedClangVersion)
      {
        Errors.warning(clangVersion == -1
          ? "Could not determine clang version."
          : "Expected clang version " + expectedClangVersion + " or higher. Found version " + clangVersion + ".");
      }

    var cCompiler = _options._cCompiler != null ? _options._cCompiler : "clang";
    var cTarget = _options._cTarget != null ? Optional.of(_options._cTarget) : getClangDefaultTarget();
    var command = new List<String>(cCompiler);

    if (cTarget.isPresent())
      {
        command.add("--target=" + cTarget.get());
      }

    /*
     * "Generate code to catch integer overflow errors.
     *  Signed integer overflow is undefined in C. With this flag,
     *  extra code is generated to detect this and abort when it happens."
     * source: man clang
     */
    command.add("-ftrapv");

    // NYI: UNDER DEVELOPMENT: enable this once we have gotten rid of implicit conversions
    // command.add("-Wconversion");

    if(_options._cFlags != null)
      {
        command.addAll(_options._cFlags.split(" "));
      }
    else
      {
        command.addAll(
          "-Wall",
          "-Werror",
          // some suggestions taken from: https://github.com/mcinglis/c-style
          "-Wextra",
          "-Wpedantic",
          "-Wformat=2",
          "-Wno-unused-parameter",
          "-Wno-unused-but-set-parameter", // needed for #1777
          "-Wshadow",
          "-Wwrite-strings",
          "-Wold-style-definition",
          "-Wredundant-decls",
          "-Wnested-externs",
          "-Wmissing-include-dirs",
          // NYI: UNDER DEVELOPMENT:
          "-Wno-strict-prototypes",
          // NYI: UNDER DEVELOPMENT:
          "-Wno-gnu-empty-initializer",
          // NYI: UNDER DEVELOPMENT:
          "-Wno-zero-length-array",
          "-Wno-trigraphs",
          "-Wno-gnu-empty-struct",
          "-Wno-unused-variable",
          "-Wno-unused-label",
          "-Wno-unused-function",
          // used when casting jobject to e.g. u16
          "-Wno-pointer-to-int-cast",
          // clang >= 19:
          // clang: error: no such include directory: 'C:/Program Files/OpenJDK/jdk-21.0.2/include/linux' [-Werror,-Wmissing-include-dirs]
          // clang: error: no such include directory: 'C:/Program Files/OpenJDK/jdk-21.0.2/include/darwin' [-Werror,-Wmissing-include-dirs]
          "-Wno-missing-include-dirs",
          // allow infinite recursion
          "-Wno-infinite-recursion",
          // NYI: UNDER DEVELOPMENT: (test mod_sqlite, `char **` and `fzT_fuzion__sys_RPointer *` are incompatible)
          "-Wno-incompatible-function-pointer-types"
          );

        if (_options._cCompiler == null && clangVersion >= 13)
          {
            command.addAll("-Wno-unused-but-set-variable");
          }

        command.addAll("-O3");
      }

    if(_options._useBoehmGC)
      {
        command.addAll("-DGC_THREADS", "-DGC_PTHREADS", "-DPTW32_STATIC_LIB", "-DGC_WIN32_PTHREADS");
      }


    if (usesThreads())
      {
        command.addAll("-DFUZION_ENABLE_THREADS");
      }

    // disable trigraphs:
    // "Trigraphs are not popular and many compilers implement them incorrectly. Portable code should not rely on trigraphs being either converted or ignored."
    // source: https://gcc.gnu.org/onlinedocs/cpp/Initial-processing.html
    command.add("-fno-trigraphs");

    // add frame pointers
    // https://fedoraproject.org/wiki/Changes/fno-omit-frame-pointer
    // https://lobste.rs/s/avrfxz/ubuntu_24_04_lts_will_enable_frame
    command.addAll("-fno-omit-frame-pointer", "-mno-omit-leaf-frame-pointer");

    if (linkLibMath())
      {
        command.add("-lm");
      }

      // NYI on windows link nothing
    if (usesThreads())
      {
        command.add("-lpthread");
      }

    command.addAll("-std=c11", "-o", name);

    // add the c-files
    command.addAll(_options.pathOf("include/shared.c"));
    // NYI: should select includes based on cTarget
    if (isWindows())
      {
        command.addAll(_options.pathOf("include/win.c"));
      }
    else
      {
        command.addAll(_options.pathOf("include/posix.c"));
      }
    if (linkJVM())
      {
        command.addAll(_options.pathOf("include/fz_jni.c"));
      }

    command.addAll(cf.fileName());

    if (linkJVM())
      {
        command.addAll(
          "-I" + JAVA_HOME + "/include",
          "-I" + JAVA_HOME + "/include/linux",
          "-I" + JAVA_HOME + "/include/win32",
          "-I" + JAVA_HOME + "/include/darwin",
          "-L" + JAVA_HOME + "/lib/server");

        if (!isWindows())
          {
            command.add("-ljvm");
          }
      }

    if (isWindows())
      {
        command.addAll("-lMswsock", "-lAdvApi32", "-lWs2_32");

        if (_options._useBoehmGC)
          {
            command.addAll(
              System.getenv("FUZION_CLANG_INSTALLED_DIR") == null
                ? "C:\\tools\\msys64\\ucrt64\\bin\\libgc-1.dll"
                : System.getenv("FUZION_CLANG_INSTALLED_DIR") + "\\libgc-1.dll"
            );
          }

        if (linkJVM())
          {
            command.addAll(JAVA_HOME + "\\bin\\server\\jvm.dll");
          }
      }

    if(_options._useBoehmGC)
      {
        command.addAll("-lgc");
      }

    if (_options._cLink != null)
      {
        var libraries = Arrays
          .stream(_options._cLink.split(" "))
          .map(x -> "-l" + x)
          .iterator();
        command.addAll(libraries);
      }

    return command;
  }


  /*
   * Are threads used?
   */
  private boolean usesThreads()
  {
    return Stream.of("fuzion.sys.thread.spawn0",
                     "fuzion.sys.thread.join0",
                     "concur.atomic.compare_and_swap0",
                     "concur.atomic.compare_and_set0",
                     "concur.atomic.racy_accesses_supported",
                     "concur.atomic.read0",
                     "concur.atomic.write0")
      .anyMatch(_intrinsics._usedIntrinsics::contains);
  }


  /*
   * Do we have to link libmath?
   */
  private boolean linkLibMath()
  {
    return Stream.of("f32.prefix -",
                     "f32.infix +",
                     "f32.infix -",
                     "f32.infix *",
                     "f32.infix /",
                     "f32.infix %",
                     "f32.infix **",
                     "f32.type.equal",
                     "f32.type.lower_than_or_equal",
                     "f64.type.equal",
                     "f64.type.lower_than_or_equal",
                     "f32.as_f64",
                     "f64.as_f32",
                     "f64.as_i64_lax",
                     "f32.cast_to_u32",
                     "f64.cast_to_u64",
                     "f32.is_NaN",
                     "f64.is_NaN",
                     "f32.square_root",
                     "f64.square_root",
                     "f32.exp",
                     "f64.exp",
                     "f32.log",
                     "f64.log",
                     "f32.sin",
                     "f64.sin",
                     "f32.cos",
                     "f64.cos",
                     "f32.tan",
                     "f64.tan",
                     "f32.asin",
                     "f64.asin",
                     "f32.acos",
                     "f64.acos",
                     "f32.atan",
                     "f64.atan",
                     "f32.sinh",
                     "f64.sinh",
                     "f32.cosh",
                     "f64.cosh",
                     "f32.tanh",
                     "f64.tanh",
                     "f32.type.min_exp",
                     "f32.type.max_exp",
                     "f32.type.min_positive",
                     "f32.type.max",
                     "f32.type.epsilon",
                     "f64.type.min_exp",
                     "f64.type.max_exp",
                     "f64.type.min_positive",
                     "f64.type.max",
                     "f64.type.epsilon")
      .anyMatch(_intrinsics._usedIntrinsics::contains);
  }


  /**
   * If $JAVA_HOME is set and java intrinsics are used,
   * we link the JVM.
   */
  private boolean linkJVM()
  {
    return JAVA_HOME != null
      && Stream.of("fuzion.java.Java_Object.is_null0",
                    "fuzion.java.array_get",
                    "fuzion.java.array_length",
                    "fuzion.java.array_to_java_object0",
                    "fuzion.java.get_field0",
                    "fuzion.java.set_field0",
                    "fuzion.java.get_static_field0",
                    "fuzion.java.set_static_field0",
                    "fuzion.java.call_c0",
                    "fuzion.java.call_s0",
                    "fuzion.java.call_v0",
                    "fuzion.java.primitive_to_java_object",
                    "fuzion.java.java_string_to_string",
                    "fuzion.java.string_to_java_object0",
                    "fuzion.java.fuzion.java.create_jvm")
      .anyMatch(_intrinsics._usedIntrinsics::contains);
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
   * @return The default clang target or optional.empty().
   */
  private Optional<String> getClangDefaultTarget()
  {
    try
      {
        var p = new ProcessBuilder().command(Arrays.asList("clang", "-print-target-triple"))
          .start();
        p.waitFor();
        return new String(p
          .getInputStream()
          .readAllBytes())
            .lines()
            .findFirst();
      }
    catch (Exception e)
      {
        return Optional.empty();
      }
  }


  /**
   * After the CFile has been opened and stored in _c, this methods generates
   * the code into this file.
   * @throws IOException
   */
  private void createCode(CFile cf, CFile hf, COptions _options) throws IOException
  {
    printHeaderFileHeader(hf);
    printCodeFileHeader(cf, hf, _options);

    var ordered = _types.inOrder();

    Stream.of(CompilePhase.values()).forEachOrdered
      ((p) ->
       {
        Consumer<CStmnt> printStmnt =
          switch(p)
            {
              case TYPES -> (stmnt)->hf.print(stmnt);
              case STRUCTS -> (stmnt)->hf.print(stmnt);
              case FORWARDS -> (stmnt)->hf.print(stmnt);
              case IMPLEMENTATIONS -> (stmnt)->cf.print(stmnt);
            };
        Consumer<String> printStr =
          switch(p)
            {
              case TYPES -> (str)->hf.print(str);
              case STRUCTS -> (str)->hf.print(str);
              case FORWARDS -> (str)->hf.print(str);
              case IMPLEMENTATIONS -> (str)->cf.print(str);
            };
         for (var c : ordered)
           {
            printStmnt.accept(p.compile(this, c));
           }
         printStr.accept("\n");

         // thread local effect environments
         if (p == CompilePhase.STRUCTS)
           {
             _effectClazzes = ordered
               .stream()
               .filter(_fuir::isEffectIntrinsic)
               .mapToInt(cl -> _fuir.effectTypeFromIntrinsic(cl))
               .sorted()
               .distinct()
               .toArray();

             var effectsData = new List<CStmnt>
               (IntStream.of(_effectClazzes)
                .mapToObj(cl -> Stream.of(CStmnt.decl(_types.clazz(cl), _names.env(cl)),
                                          CStmnt.decl("bool", _names.envInstalled(cl))
                                          )
                          )
                .flatMap(x -> x)
                .iterator());
             effectsData.add(CStmnt.decl("jmp_buf*", _names.envJmpBuf()));

             printStmnt.accept(
               CStmnt.seq(
                 CStmnt.struct(CNames.fzThreadEffectsEnvironment.code(), effectsData),
                 CStmnt.decl("_Thread_local", "struct " + CNames.fzThreadEffectsEnvironment.code() + "*", CNames.fzThreadEffectsEnvironment)
               )
             );
           }
       });

    cf.print(threadStartRoutine(true));

    cf.println("int main(int argc, char **argv) { ");

    cf.print(initializeEffectsEnvironment());

    var cl = _fuir.mainClazz();

    cf.print(CStmnt.seq(CNames.GLOBAL_ARGC.assign(new CIdent("argc")),
                        CNames.GLOBAL_ARGV.assign(new CIdent("argv")),
                        CExpr.call(_names.function(cl), new List<>())
                        ));

    if (linkJVM())
      {
        cf.println("fzE_destroy_jvm();");
      }

    cf.println("}");
  }


  /*
   * print header in .h file
   */
  private void printHeaderFileHeader(CFile hf)
  {
    hf.print("#include <stdint.h>\n");
    hf.print("#include <stdbool.h>\n"); /* for bool fzEnvInstalled */
    hf.print("#include <setjmp.h>\n"); /* for jmp_buf */

    hf.print
      (CStmnt.decl("int", CNames.GLOBAL_ARGC));
    hf.print
      (CStmnt.decl("char **", CNames.GLOBAL_ARGV));

    // declaration of struct that is meant to passed to
    // the thread start routine
    hf.print(CStmnt.struct(CNames.fzThreadStartRoutineArg.code(), new List<>(
      CStmnt.decl("void *", CNames.fzThreadStartRoutineArgFun),
      CStmnt.decl("void *", CNames.fzThreadStartRoutineArgArg)
    )));
    // declaration of the thread start routine
    hf.print(threadStartRoutine(false));
  }


  /*
   * print header in .c file
   */
  private void printCodeFileHeader(CFile cf, CFile hf, COptions _options)
  {
    if (_options._useBoehmGC)
      {
                 // we need to include winsock2.h before windows.h
        cf.print("#define GC_DONT_INCLUDE_WINDOWS_H\n" +
                 "#include <gc.h>\n");
      }

    // --- C-11 ---
    cf.print(
       "#include <stdlib.h>\n"+
       "#include <stdio.h>\n"+
       "#include <stdbool.h>\n"+
       "#include <stdint.h>\n"+
       "#include <string.h>\n"+
       "#include <math.h>\n"+
       "#include <float.h>\n"+
       "#include <assert.h>\n"+
       "#include <time.h>\n"+
       "#include <setjmp.h>\n"+
       "#include <errno.h>\n"+
       "#include <stdatomic.h>\n");

    if (linkJVM())
      {
        cf.println("#include <jni.h>");
      }

    var fzH = _options.pathOf("include/fz.h");
    cf.println("#include \"" + fzH + "\"");
    cf.println("#include \"" + _options.pathOf("include/fz_jni.h") + "\"");
    cf.println("#include \"" + hf.fileName() + "\"");

    if (_options._cLink != null)
      {
        Arrays
          .stream(_options._cInclude.split(" "))
          .forEach(x -> cf.println("#include <" + x + ">"));
      }

    var o = new CIdent("of");
    var s = new CIdent("sz");
    var r = new CIdent("r");

    // NYI: UNDER DEVELOPMENT: use libffi instead of storing the outer
    // reference in a thread local variable?
    cf.println("_Thread_local void * fzW_native_outer = NULL;");

    cf.print
      (CStmnt.lineComment("helper to clone a (stack) instance to the heap"));
    cf.print
      (CStmnt.functionDecl("void *",
                           CNames.HEAP_CLONE,
                           new List<>("void *", "size_t"),
                           new List<>(o, s),
                           CStmnt.seq(new List<>(CStmnt.decl(null, "void *", r, CExpr.call(malloc(), new List<>(s))),
                                                 CExpr.call("fzE_memcpy", new List<>(r, o, s)),
                                                 r.ret()))));
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
      CExpr.call("fzE_mem_zero", new List<>(tmp.adrOf(), CExpr.sizeOfType("struct " + CNames.fzThreadEffectsEnvironment.code()))),
      CNames.fzThreadEffectsEnvironment.assign(tmp.adrOf()),
      CStmnt.seq(
        new List<CStmnt>(
          _types.inOrder()
            .stream()
            .filter(cl -> _fuir.clazzNeedsCode(cl) && _fuir.isEffectIntrinsic(cl))
            .mapToInt(cl -> _fuir.effectTypeFromIntrinsic(cl))
            .distinct()
            .<CStmnt>mapToObj(ecl -> CNames.fzThreadEffectsEnvironment.deref().field(_names.envInstalled(ecl)).assign(new CIdent("false")))
            .iterator()))
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
  CStmnt reportErrorInCode0(String msg, CExpr... args)
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
   * @param s site of the access expression, must be ExprKind.Assign or ExprKind.Call
   *
   * @param tvalue the target of this call, CExpr.UNIT if none.
   *
   * @param args the arguments of this call, or, in case of an assignment, a
   * list of one element containing value to be assigned.
   *
   * @return pair of expression containing result value and statement to perform
   * the given access
   */
  Pair<CExpr, CStmnt> access(int s, CExpr tvalue, List<CExpr> args)
  {
    CExpr res = CExpr.UNIT;
    var isCall = _fuir.codeAt(s) == FUIR.ExprKind.Call;
    var cc0 = _fuir.accessedClazz  (s);
    var tc = _fuir.accessTargetClazz(s);
    var rt = _fuir.clazzResultClazz(cc0); // only needed if isCall
    var ol = new List<CStmnt>();
    var ccs = _fuir.accessedClazzes(s);
    if (ccs.length == 0)
      {
        if (isCall && (_fuir.hasData(rt) || _fuir.clazzIsVoidType(rt)))
          {
            ol.add(reportErrorInCode0("no targets for access of `%s` within %s",
                                      CExpr.string(_fuir.clazzAsString(cc0)),
                                      CExpr.string(_fuir.siteAsString(s))));
            res = null;
          }
        else
          {
            ol.add(CStmnt.lineComment("access to " + _fuir.codeAtAsString(s) + " eliminated"));
          }
      }
    else
      {
        if (_fuir.hasData(tc) && _fuir.accessIsDynamic(s) && ccs.length > 2)
          {
            ol.add(CStmnt.lineComment("Dynamic access of " + _fuir.clazzAsString(cc0)));
            var tvar = _names.newTemp();
            var tt0 = _types.clazz(tc);
            ol.add(CStmnt.decl(tt0, tvar, tvalue));
            tvalue = tvar;
          }

        // see: #1835 why we need this. Without this the calls result
        // is correctly detected to escape, heap cloned but then dereferenced
        // and put onto the stack which defeats the purpose of the heap clone.
        var callsResultEscapes = isCall
          && _fuir.hasData(rt)
          && ccs.length > 2
          && (_fuir.doesResultEscape(s) && !_fuir.clazzIsRef(_fuir.clazzResultClazz(cc0))
            // see: #2072 why we need this. Without this we would copy the field.
            // But we just want a reference to the field.
            || _fuir.clazzKind(cc0) == FeatureKind.Field && !_fuir.clazzFieldIsAdrOfValue(cc0)
            );

        if (isCall && _fuir.hasData(rt) && ccs.length > 2)
          {
            var resvar = _names.newTemp();
            res = resvar;
            ol.add(CStmnt.decl(_types.clazzField(cc0) + (callsResultEscapes ? "*" : ""), resvar));
          }
        var cazes = new List<CStmnt>();
        CStmnt acc = CStmnt.EMPTY;
        for (var cci = 0; cci < ccs.length; cci += 2)
          {
            var tt = ccs[cci  ];                   // target clazz we match against
            var cc = ccs[cci+1];                   // called clazz in case of match
            var cco = _fuir.clazzOuterClazz(cc);   // outer clazz of called clazz, usually equal to tt unless tt is boxed value type
            var rti = _fuir.clazzResultClazz(cc);
            if (isCall)
              {
                var tv = tt != tc ? tvalue.castTo(_types.clazz(tt)) : tvalue;
                var ut = _fuir.clazzIsBoxed(tt) && !_fuir.clazzIsRef(cco) ? cco : tt;
                tv = unbox(tt, cc, tv);
                var calpair = call(s, tv, args, ut, cc);
                var rv  = calpair.v0();
                acc = calpair.v1();
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
                                     assign(res, callsResultEscapes ? rv.adrOf() : rv, rt));
                  }
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
            var id = tvalue.deref().field(CNames.CLAZZ_ID);
            acc = CStmnt.suitch(id, cazes,
                                reportErrorInCode0("unhandled dynamic target %d in access of `%s` within %s",
                                                   id,
                                                   CExpr.string(_fuir.clazzAsString(cc0)),
                                                   CExpr.string(_fuir.siteAsString(s))));
          }
        ol.add(acc);
        res = _fuir.alwaysResultsInVoid(s)
          ? null
          : callsResultEscapes || isCall && _fuir.hasData(rt) && _fuir.clazzFieldIsAdrOfValue(cc0)  // NYI: deref an outer ref to value type. Would be nice to have a separate expression for this
            ? res.deref()
            : res;
      }

    return new Pair<>(res, CStmnt.seq(ol));
  }


  /**
   * Unbox tv if needed.
   *
   * @param tt the target type
   * @param cc the called clazz
   * @param tv the target value which may be boxed
   * @return
   */
  private CExpr unbox(int tt, int cc, CExpr tv)
  {
    var cco = _fuir.clazzOuterClazz(cc); // outer clazz of called clazz, usually equal to tt unless tt is boxed value type
    return _fuir.clazzIsBoxed(tt) && !_fuir.clazzIsRef(cco)
      ? fields(tv, tt)
      : tv;
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
   * @param sc the special clazz we we are generating the CExpr for.
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
   * Create CExpr to create a (boxed) constant string.
   *
   * @param bytes the serialized bytes of the UTF-8 string.
   *
   * Example code:
   * {@code (fzT__RConst_u_String){.clazzId = 282, .fields = (fzT_Const_u_String){.fzF_0_internal_u_array = (fzT__L3393fuzion__sy__array_w_u8){.fzF_0_data = (void *)"failed to encode code point ",.fzF_1_length = 28}}}}
   */
  CExpr boxedConstString(byte[] bytes)
  {
    return boxedConstString(CExpr.string(bytes), CExpr.int32const(bytes.length));
  }


  /**
   * returns a CExpr that creates a (boxed) const_string from a java string.
   *
   * @param str the string.
   */
  CExpr boxedConstString(String str)
  {
    return boxedConstString(str.getBytes(StandardCharsets.UTF_8));
  }


  /**
   * Create CExpr to create a (boxed) constant string.
   *
   * @param str CExpr the creates a c string.
   *
   * @param len CExpr that returns the size_t of the string
   *
   * Example code:
   * {@code (fzT__Rconst_u_string){.clazzId = 282, .fields = (fzT_const_u_string){.fzF_0_internal_u_array = (fzT__L3393fuzion__sy__array_w_u8){.fzF_0_data = (void *)"failed to encode code point ",.fzF_1_length = 28}}}}
   */
  CExpr boxedConstString(CExpr str, CExpr len)
  {
    var data           = _names.fieldName(_fuir.clazz_fuzionSysArray_u8_data());
    var length         = _names.fieldName(_fuir.clazz_fuzionSysArray_u8_length());
    var internal_array = _names.fieldName(_fuir.lookup_array_internal_array(_fuir.clazz_array_u8()));
    var utf8_data      = _names.fieldName(_fuir.clazz_const_string_utf8_data());

    var sysArray = CExpr.compoundLiteral(
        _types.clazz(_fuir.clazzResultClazz(_fuir.clazz_fuzionSysArray_u8())),
        "." + data.code() + " = " + str.castTo("void *").code() +  "," +
          "." + length.code() + " = " + len.code());

    var array = CExpr.compoundLiteral(
        _types.clazz(_fuir.clazz_array_u8()),
        "." + internal_array.code() + " = " + sysArray.code());

    var constStr = CExpr
      .compoundLiteral(
        _types.clazz(_fuir.clazz_const_string()),
        "." + utf8_data.code() + " = " + array.code());

    var refConstStr = _fuir.clazz_ref_const_string();
    var res = CExpr
      .compoundLiteral(
        _names.struct(refConstStr),
        "." + CNames.CLAZZ_ID.code() + " = " + _names.clazzId(refConstStr).code() + ", " +
          "." + CNames.FIELDS_IN_REF_CLAZZ.code() + " = " + constStr.code());

    return heapClone(res, _fuir.clazz(SpecialClazzes.c_String));
  }


  /**
   * Create code to assign value to a field
   *
   * @param tc the static target clazz
   *
   * @param tt the actual target clazz in case the assignment is dynamic
   *
   * @param f the field
   */
  CStmnt assignField(CExpr tvalue, int tc, int tt, int f, CExpr value, int rt)
  {
    if (_fuir.clazzFieldIsAdrOfValue(f))
      {
        value = value.adrOf();
      }
    if (_fuir.clazzIsRef(tt) && tc != tt)
      {
        tvalue = tvalue.castTo(_types.clazz(tt));
      }
    var af = accessField(tvalue, tc, f);
    if (_fuir.clazzIsRef(rt))
      {
        value = value.castTo(_types.clazz(rt));
      }
    return _types.fieldExists(f) ? assign(af, value, rt)
                                 : CStmnt.lineComment("assignment to unused field " + clazzInQuotes(f));
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
   * @param s site of the call
   *
   * @param tvalue
   *
   * @param args
   *
   * @param cc clazz that is called
   *
   * @return the code to perform the call
   */
  Pair<CExpr, CStmnt> call(int s, CExpr tvalue, List<CExpr> args, int tt, int cc)
  {
    CStmnt result = CStmnt.EMPTY;
    var resultValue = CExpr.UNIT;
    var rt = _fuir.clazzResultClazz(cc);
    switch (_fuir.clazzKind(cc))
      {
      case Abstract :
        Errors.error("Call to abstract feature encountered.",
                     "Found call to  " + _fuir.clazzAsString(cc));
        break;
      case Routine  :
      case Intrinsic:
      case Native   :
        {
          if (_fuir.clazzNeedsCode(cc))
            {
              var a = args(tvalue, args, cc, _fuir.clazzArgCount(cc));
              var cl = _fuir.clazzAt(s);

              if (cc == cl &&  // calling myself
                  _tailCall.callIsTailCall(cl, s)
                )
                { // then we can do tail recursion optimization!
                  var tc = _fuir.clazzOuterClazz(cc);
                  result = tailRecursion(cl, s, tc, a);
                  resultValue = null;
                }
              else
                {
                  var call = CExpr.call(_names.function(cc), a);
                  result = call;
                  CExpr res = _fuir.clazzIsVoidType(rt) ? null : CExpr.UNIT;
                  if (_fuir.hasData(rt))
                    {
                      var tmp = _names.newTemp();
                      res = tmp;
                      var heapClone = CStmnt.EMPTY;
                      if (_fuir.doesResultEscape(s))
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
          break;
        }
      case Field:
        {
          resultValue = accessField(tvalue, tt, cc);
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
   * @param s site of the call
   *
   * @param tc the target clazz (type of outer) in this call
   *
   * @param a list of actual arguments to the tail recursive call.
   */
  CStmnt tailRecursion(int cl, int s, int tc, List<CExpr> a)
  {
    var l = new List<CStmnt>();
    if (_fuir.hasData(tc) && !_tailCall.firstArgIsOuter(s))
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
        var af = _fuir.clazzArg     (vcl, ai);
        var at = _fuir.clazzArgClazz(vcl, ai);
        if (_fuir.hasData(at))
          {
            if (_fuir.clazzNeedsCode(af))
              {
                l.add(assign(CIdent.arg(ai), a.get(aii), at));
              }
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
   * @param body the code of the function, or null for a forward declaration.
   *
   * @return the C code
   */
  private CStmnt cFunctionDecl(int cl, CStmnt body)
  {
    var res = _fuir.clazzResultClazz(cl);
    var resultType = _types.resultClazz(res);
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
    return CStmnt.functionDecl(resultType, new CIdent(_names.function(cl)), argts, argns, body);
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
          case Native   : l.add(cFunctionDecl(cl, null));
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
    var res = CStmnt.EMPTY;
    if (_fuir.clazzNeedsCode(cl))
      {
        var decl = switch (_fuir.clazzKind(cl))
          {
          case Routine   -> cFunctionDecl(cl, codeForRoutine(cl));
          case Intrinsic -> cFunctionDecl(cl, _intrinsics.code(this, cl));
          case Native    -> CStmnt.seq(functionWrapperForNative(cl),
                                       cFunctionDecl(cl, codeForNative(cl)));
          default -> null;
          };
        if (decl != null)
          {
            res = CStmnt.seq
              (CStmnt.lineComment("code for clazz#"+_names.clazzId(cl).code()+" "+_fuir.clazzAsString(cl)+":"),
               decl);
          }
      }
    return res;
  }


  /**
   * Create code for given clazz cl.
   *
   * @param cl id of clazz to generate code for
   */
  CStmnt codeForRoutine(int cl)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzKind(cl) == FUIR.FeatureKind.Routine);

    _names._tempVarId = 0;  // reset counter for unique temp variables for function results
    var l = new List<CStmnt>();
    l.add(_ai.processClazz(cl).v1());
    var res = _fuir.clazzResultClazz(cl);
    if (_fuir.hasData(res))
      {
        l.add(_fuir.isConstructor(cl)
                ? current(_fuir.clazzCode(cl)).ret()                                                      // a constructor, return current instance
                : current(_fuir.clazzCode(cl)).field(_names.fieldName(_fuir.clazzResultField(cl))).ret()  // a routine, return result field
              );
      }
    var allocCurrent = switch (_fuir.lifeTime(cl))
      {
      case Call      -> CStmnt.seq(
          CStmnt.lineComment("cur does not escape, alloc on stack"),
          CStmnt.decl(_names.struct(cl), CNames.CURRENT),
          // this fixes "variable 'fzCur' is uninitialized when used here" in e.g. reg_issue1188
          CExpr.call("fzE_mem_zero", new List<>(CNames.CURRENT.adrOf(), CNames.CURRENT.sizeOfExpr())));
      case Unknown   -> CStmnt.seq(CStmnt.lineComment("cur may escape, so use malloc"      ), declareAllocAndInitClazzId(cl, CNames.CURRENT));
      case Undefined -> CExpr.dummy("undefined life time");
      };
    return CStmnt.seq(allocCurrent,
                      CStmnt.seq(l)).label("start");
  }


  /*
   * Generates code for a wrapper function in native
   * functions that take callback arguments.
   * Example:
   *
   *     fzT_1i32 fzW_268437765(fzT_fuzion__sys_RPointer* arg0, fzT_1i32 arg1, fzT_fuzion__sys_RPointer* arg2, fzT_fuzion__sys_RPointer* arg3)
   *     {
   *       return fzC__L23151sqlite_u___b2__4call(fzW_native_outer,arg0,arg1,arg2,arg3);
   *     }
   *
   * The function pointer is then passed to the native function
   *
   *     fzT_1i32 fzC__L23095sqlite3_u___H2_o_cb2(fzT_fuzion__sys_RPointer* arg0, fzT_fuzion__sys_RPointer* arg1, fzT__L23051sqlite_u___uery__cb2 arg2, fzT_fuzion__sys_RPointer* arg3, fzT_fuzion__sys_RPointer* arg4)
   *     {
   *       fzW_native_outer = (void *)&arg2;
   *       return sqlite3_exec((void *)arg0,(void *)arg1,&fzW_268437765,(void *)arg3,(void *)arg4);
   *     }
   */
  private CStmnt functionWrapperForNative(int cl)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzKind(cl) == FUIR.FeatureKind.Native);

    var l = new List<CStmnt>();

    for (var i = 0; i < _fuir.clazzArgCount(cl); i++)
      {
        if (isLambdaWithOuterRef(_fuir.clazzArgClazz(cl, i)))
          {
            var call = _fuir.lookupCall(_fuir.clazzArgClazz(cl, i));
            var rc = _fuir.clazzResultClazz(call);
            l.add(
              new CStmnt() {
                @Override
                void code(CString sb)
                {
                  sb.append(_types.resultClazz(rc));
                  sb.append(" ");
                  CIdent.funWrapper(cl).code(sb);
                  sb.append("(");
                  var args = new List<CExpr>(new CIdent("fzW_native_outer"));
                  var argCountWrapper = _fuir.clazzArgCount(call);
                  for (int j = 0; j < argCountWrapper; j++)
                    {
                      sb.append(_types.clazz(_fuir.clazzArgClazz(call, j)));
                      sb.append(" ");
                      sb.append("arg" + j);
                      if (argCountWrapper-1 != j)
                        {
                          sb.append(", ");
                        }
                      args.add(new CIdent("arg" + j));
                    }
                  sb.append(")\n{\n");
                  CExpr
                    .iff(
                      new CIdent("fzW_native_outer").eq(CNames.NULL),
                      reportErrorInCode0("Misuse of native callback detected, outer reference is NULL.")
                    )
                    .code(sb.indent());
                  var c = CExpr.call(_names.function(call), args);
                  if (_fuir.hasData(rc))
                    {
                      c.ret().code(sb.indent());
                    }
                  else
                    {
                      c.code(sb.indent());
                    }
                  sb.append(";\n}\n");
                }
                @Override boolean needsSemi() { return false; }
              });
          }
      }

    return CStmnt.seq(l);
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

    /*
      fzW_native_outer = &arg3;
      int wrapper(void *, int, void * void *)
      {
        return fzC_...(fzW_native_outer, arg1, arg2, arg3, arg4);
      }
    */

    var res = new List<CStmnt>();

    checkNumCallsMaxOne(cl);

    for (var i = 0; i < _fuir.clazzArgCount(cl); i++)
      {
        if (isLambdaWithOuterRef(_fuir.clazzArgClazz(cl, i)))
          {
            res.add(
              new CIdent("fzW_native_outer").assign(CIdent.arg(i).adrOf().castTo("void *"))
            );
          }
      }

    for (var i = 0; i < _fuir.clazzArgCount(cl); i++)
      {
        var at = _fuir.clazzArgClazz(cl, i);
        var c = _fuir.lookupCall(at);
        var arg = c != NO_CLAZZ
          // 1. pass as function pointer
          ? (_fuir.clazzOuterRef(c) != NO_CLAZZ
              // 1.1 needs outer ref
              ? CIdent.funWrapper(cl).adrOf()
              // 1.2 does not need outer ref
              : new CIdent(_names.function(c)).adrOf())
          : _fuir.clazzIsRef(at)
          // 2. pass as ref
          ? CIdent.arg(i).castTo("void *")
          // 3. pass as value
          : (_fuir.clazzIsArray(at)
              // 3.1 array, we need to get field internal_array.data
              ? getFieldInternalArrayData(i, at)
              // 3.2 plain value
              : _fuir.getSpecialClazz(at) != SpecialClazzes.c_NOT_FOUND
                ? CIdent.arg(i)
                : CIdent.arg(i).adrOf().castTo(_fuir.clazzNativeName(at) + " *").deref()
              );
        args.add(arg);
      }

    var rc = _fuir.clazzResultClazz(cl);
    var call = CExpr.call(_fuir.clazzNativeName(cl), args);

    var tmp = _names.newTemp();
    var resultsInUnit = _fuir.clazzIsUnitType(rc);
    var isNativeValue = _fuir.getSpecialClazz(rc) == SpecialClazzes.c_NOT_FOUND && !_fuir.clazzIsRef(rc);

    if (!resultsInUnit)
      {
        res.add(CExpr.decl(isNativeValue ? _fuir.clazzNativeName(rc) : _types.clazz(rc), tmp));
      }

    switch (_fuir.getSpecialClazz(rc))
      {
        case
          c_i8, c_i16, c_i32, c_i64, c_u8,
          c_u16, c_u32, c_u64, c_f32, c_f64: { res.add(tmp.assign(call)); break; }
        case c_String:
          {
            var str = new CIdent("str");
            res.add(CStmnt.seq(
              CExpr.decl("char*", str, call),
              tmp.assign(boxedConstString(str, CExpr.call("strlen", new List<>(str))))
                ));
            break;
          }
        case c_bool: { res.add(tmp.assign(call.cond(_names.FZ_TRUE, _names.FZ_FALSE))); break; }
        default:
          {
            var x = _fuir.clazzIsRef(rc)
              ? call.castTo("void *")
              : call;
            res.add(resultsInUnit ? x : tmp.assign(x));
            break;
          }
      };

    for (var i = 0; i < _fuir.clazzArgCount(cl); i++)
      {
        if (isLambdaWithOuterRef(_fuir.clazzArgClazz(cl, i)))
          {
            res.add(new CIdent("fzW_native_outer").assign(CNames.NULL));
          }
      }

    if (!resultsInUnit)
      {
        res.add(isNativeValue
                  ? tmp.adrOf().castTo(_types.clazz(rc) + " *").deref().ret()
                  : tmp.ret());
      }

    return CStmnt.seq(res);
  }


  private CExpr getFieldInternalArrayData(int i, int at)
  {
    var ia = _fuir.lookup_array_internal_array(at);

    return CIdent
      .arg(i)
      .field(_names.fieldName(ia))
      .field(_names.fieldName(_fuir.lookup_fuzion_sys_internal_array_data(_fuir.clazzResultClazz(ia))))
      .castTo("void *");
  }


  /**
   * Is cl a lambda - inheriting from Function -
   * and {@code call} needs and outer reference
   * passed in?
   */
  private boolean isLambdaWithOuterRef(int cl)
  {
    var c = _fuir.lookupCall(cl);
    return c != NO_CLAZZ && _fuir.clazzOuterRef(c) != NO_CLAZZ;
  }


  /**
   * NYI: UNDER DEVELOPMENT: remove this implementation restriction
   */
  @Deprecated
  private void checkNumCallsMaxOne(int cl)
  {
    var numCalls = 0;
    for (var i = 0; i < _fuir.clazzArgCount(cl); i++)
      {
        if (_fuir.lookupCall(_fuir.clazzArgClazz(cl, i)) != NO_CLAZZ)
          {
            numCalls++;
          }
      }
    if (numCalls > 1)
      {
        Errors.fatal("Implementation restriction: maximum number of callbacks in native is currently one.");
      }
  }


  CExpr heapClone(CExpr valueExpr, int rc)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzIsRef(rc));

    return CExpr
      .call(CNames.HEAP_CLONE._name, new List<>(valueExpr.adrOf(), valueExpr.sizeOfExpr()))
      .castTo(_types.clazz(rc));
  }


  /**
   * Return the current instance of the currently compiled clazz cl. This is a C
   * pointer in case _fuir.clazzIsRef(cl), or the C struct corresponding to cl
   * otherwise.
   *
   * @param s id of clazz we are generating code for
   */
  CExpr current(int s)
  {
    var cl = _fuir.clazzAt(s);
    var res1 = CNames.CURRENT;
    var res2 = _fuir.clazzIsRef(cl) ? res1 : res1.deref();
    var res3 =  _fuir.lifeTime(cl).maySurviveCall() ? res2 : res2.adrOf();
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
   * @param refOrVal C expression to access an instance
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


  /**
   * Create and return a {@code Java_Object} from result of {@code expr}.
   *
   * @param cl the type we are returning
   * @param expr the expr producing the result (fzE_jvm_result or jvalue)
   * @param complexResult is the result of {@code expr} {@code fzE_jvm_result} or {@code jvalue}
   * @return
   */
  public CStmnt returnJavaObject(int cl, CExpr expr, boolean complexResult)
  {
    var jv = complexResult
                           ? expr
                             .field(CNames.CHOICE_UNION_NAME)
                             .field(new CIdent("v0"))
                           : expr;

    /*
      * typedef union jvalue {
      *     jboolean z;
      *     jbyte    b;
      *     jchar    c;
      *     jshort   s;
      *     jint     i;
      *     jlong    j;
      *     jfloat   f;
      *     jdouble  d;
      *     jobject  l;
      * } jvalue;
      *
      */

    switch (_fuir.getSpecialClazz(cl))
      {
      case c_i8 :
        return jv.field(new CIdent("b")).castTo(_types.scalar(cl)).ret();
      case c_i16 :
        return jv.field(new CIdent("s")).castTo(_types.scalar(cl)).ret();
      case c_i32 :
        return jv.field(new CIdent("i")).castTo(_types.scalar(cl)).ret();
      case c_i64 :
        return jv.field(new CIdent("j")).castTo(_types.scalar(cl)).ret();
      case c_u16 :
        return jv.field(new CIdent("c")).castTo(_types.scalar(cl)).ret();
      case c_f32 :
        return jv.field(new CIdent("f")).castTo(_types.scalar(cl)).ret();
      case c_f64 :
        return jv.field(new CIdent("d")).castTo(_types.scalar(cl)).ret();
      case c_bool :
        return jv.field(new CIdent("z")).cond(_names.FZ_TRUE, _names.FZ_FALSE).ret();
      case c_NOT_FOUND :

        var tmp = _names.newTemp();

        var sideEffect =  CStmnt.decl(complexResult ? "fzE_jvm_result" : "jvalue", tmp, expr);

        var innerCl =  _fuir.clazzIsChoice(cl) ? _fuir.clazzChoice(cl, 0) : cl;

        var val = javaValue2Fuzion(complexResult, tmp, innerCl);

        var result =  _fuir.clazzIsChoice(cl)
          ? complexResult
          ? CExpr.iff(
              tmp.field(CNames.TAG_NAME).eq(CExpr.int32const(0)),
                // normal result
                returnOutcome(innerCl, val, cl, 0),
                // exception
                returnOutcome(
                  _fuir.clazzChoice(cl, 1),
                    jStringToError(
                      tmp
                        .field(CNames.CHOICE_UNION_NAME)
                        .field(new CIdent("v1"))
                    ),
                  cl,
                  1))
          : returnOutcome(innerCl, val, cl, 0)
          : val.ret();

        return CExpr.seq(sideEffect, result);
      case c_unit :
        return expr;
      case c_String :
      case c_false_ :
      case c_true_ :
      case c_Array :
      case c_u32 :
      case c_u64 :
      case c_u8 :
      default:
        throw new Error("misuse of Java intrinsic?" + _fuir.clazzAsString(cl));
      }
  }


  /**
   * @param complexResult are we dealing with a result that may contain an exception
   * @param tmp the name of the variable containing the result
   * @param cl the clazz of the result
   * @return
   */
  private CExpr javaValue2Fuzion(boolean complexResult, CLocal tmp, int cl)
  {
    var successResult = (complexResult ? tmp.field(CNames.CHOICE_UNION_NAME).field(new CIdent("v0")) : tmp);
    return switch (_fuir.getSpecialClazz(cl))
      {
        case c_i8 -> successResult.field(new CIdent("b")).castTo(_types.scalar(cl));
        case c_i16 -> successResult.field(new CIdent("s")).castTo(_types.scalar(cl));
        case c_i32 -> successResult.field(new CIdent("i")).castTo(_types.scalar(cl));
        case c_i64 -> successResult.field(new CIdent("j")).castTo(_types.scalar(cl));
        case c_u16 -> successResult.field(new CIdent("c")).castTo(_types.scalar(cl));
        case c_f32 -> successResult.field(new CIdent("f")).castTo(_types.scalar(cl));
        case c_f64 -> successResult.field(new CIdent("d")).castTo(_types.scalar(cl));
        case c_bool -> successResult.field(new CIdent("z")).cond(_names.FZ_TRUE, _names.FZ_FALSE);
        case c_unit -> successResult;
        case c_NOT_FOUND -> asJava_Object(cl, successResult);
        default -> throw new Error("error in implementation.");
      };
  }


  /**
   * wrap successResult in the appropriate Java_Object
   *
   * @param cl
   * @param successResult
   * @return
   */
  private CExpr asJava_Object(int cl, CExpr successResult)
  {
    var rc = _fuir.clazzAsValue(cl);
    var obj = CExpr
      .compoundLiteral(
        _types.clazz(rc),
        "." + _names.fieldName(_fuir.lookupJavaRef(cl)).code() + " = "
          + successResult
            .field(new CIdent("l"))
            .castTo("void *" /* J_Value */)
            .code());

    var val = CExpr
      .compoundLiteral(
        _names.struct(cl),
        "." + CNames.CLAZZ_ID.code() + " = " + _names.clazzId(cl).code() + ", " +
          "." + CNames.FIELDS_IN_REF_CLAZZ.code() + " = " + obj.code());

    val = CExpr.call(CNames.HEAP_CLONE._name, new List<>(val.adrOf(), val.sizeOfExpr()));
    return val;
  }


  /**
   * @param field the jstring
   *
   * @return a c expression that creates a fuzion const string.
   */
  private CExpr jStringToError(CExpr field)
  {
    return error(boxedConstString(
        CExpr.call("fzE_java_string_to_utf8_bytes", new List<>(field)),
        CExpr.call("strlen", new List<>(
            CExpr.call("fzE_java_string_to_utf8_bytes",
            new List<>(field))))
      ));
  }


  /**
   * create code for instantiating a
   * fuzion error from a constString
   *
   * @param str
   * @return
   */
  public CExpr error(CExpr str)
  {
    return CExpr.compoundLiteral(
      _names.struct(_fuir.clazz_error()),
      "." + _names.fieldName(_fuir.clazzArg(_fuir.clazz_error(), 0)).code() + " = " +
        str.code()
      );
  }


  /**
   * The choice entries name. v0, v1, ..., vref
   *
   * @param valuecl
   * @param choiceCl
   * @param tagNum
   * @return
   */
  private CIdent choiceEntryName(int valuecl, int choiceCl, int tagNum)
  {
    return _fuir.clazzIsRef(valuecl) ||
      _fuir.clazzIsChoiceOfOnlyRefs(choiceCl)
                                              ? CNames.CHOICE_REF_ENTRY_NAME
                                              : new CIdent(CNames.CHOICE_ENTRY_NAME + tagNum);
  }


  /**
   * return a tagged value of type newcl from an untagged value for type valuecl.
   */
  public CStmnt returnOutcome(int valuecl, CExpr value, int choiceCl, int tagNum)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzIsChoice(choiceCl),
        !_fuir.clazzIsChoiceOfOnlyRefs(choiceCl),
        _fuir.clazzChoiceCount(choiceCl) == 2);

    return _fuir.clazzIsUnitType(valuecl)
      ? CExpr.compoundLiteral(
                      _types.clazz(choiceCl),
                      "." + CNames.TAG_NAME.code() + " = " + CExpr.int32const(0).code())
             .ret()
      : CExpr.compoundLiteral(_types.clazz(choiceCl),
                "." + CNames.TAG_NAME.code() + " = " + CExpr.int32const(tagNum).code() + ", " +
                  "." + CNames.CHOICE_UNION_NAME.code() + " = { ." + choiceEntryName(valuecl, choiceCl, tagNum).code() + " = "
                  + (_fuir.clazzIsRef(valuecl) ? value.castTo(_types.clazz(_fuir.clazzAny())): value).code() + " }")
             .ret();
  }


  /**
   * For debugging output
   *
   * @return "{@code <clazz c>}".
   */
  private String clazzInQuotes(int c)
  {
    return "`" + _fuir.clazzAsString(c) + "`";
  }


}

/* end of file */
