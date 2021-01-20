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
 * Source of class C
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;

import java.io.IOException;

import java.util.Stack;

import dev.flang.ir.Backend;
import dev.flang.ir.BackendCallable;
import dev.flang.ir.Clazz;

import dev.flang.fuir.FUIR;

import dev.flang.util.FusionOptions;


/**
 * C provides a C code backend converting FUIR data into C code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class C extends Backend
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * C code generation phase for generating C functions for features.
   */
  private enum CompilePhase
  {
    FORWARDS,         // generate forward declarations only
    IMPLEMENTATIONS,  // generate C functions
  }


  /**
   * Maximum length of (external) function names in C.  Since name mangling
   * easily results in lengthy names, we have to be careful not to exeed this.
   */
  private static final int MAX_C99_IDENTIFIER_LENGTH = 31;


  /**
   * Name of local variable containing current instance
   */
  private static final String CURRENT = "cur";


  /**
   * Prefix for types declared for clazz instances
   */
  private static final String TYPE_PREFIX = "fzT_";


  /**
   * Prefix for C functions created for Fuzion routines or intrinsics
   */
  private static final String C_FUNCTION_PREFIX = "fzC_";


  /**
   * Prefix for temporary local variables
   */
  private static final String TEMP_VAR_PREFIX = "fzM_";


  /**
   * C constants corresponding to Fuzion's true and false values.
   */
  private static final String FZ_FALSE =  "((slot_t) { .i32 = 0 })";
  private static final String FZ_TRUE  =  "((slot_t) { .i32 = 1 })";


  /**
   * Debugging output
   */
  private static final boolean SHOW_STACK_AFTER_STMNT = false;
  private static final boolean SHOW_STACK_ON_CALL = false;


  /*----------------------------  variables  ----------------------------*/


  /**
   * The intermidiate code we are compiling.
   */
  private final FUIR _fuir;


  /**
   * Writer to create the C code to.
   */
  private CFile _c;


  /**
   * Counter for unique temp variables to hold function results
   */
  private int _resultId = 0;


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create C code backend for given intermidiate code.
   *
   * @param fuir the intermeidate code.
   *
   * @param opt options to control compilation.
   */
  public C(FusionOptions opt,
           FUIR fuir)
  {
    _fuir = fuir;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Obtain backend information required for dynamic binding lookup to perform a
   * call.
   *
   * @param innerClazz the frame clazz of the called feature
   *
   * @param outerClazz the static clazz of the target instance of this call
   *
   * @return a beckend-specific object.
   */
  public BackendCallable callable(Clazz innerClazz,
                                  Clazz outerClazz)
  {
    return null;
  }


  /**
   * Create the C code from the intermediate code.
   */
  public void compile()
  {
    var cl = _fuir.mainClazzId();
    var f = _fuir.clazz2FeatureId(cl);
    var name = _fuir.featureBaseName(f);
    var cname = name + ".c";
    System.out.println(" + " + cname);
    try
      {
        _c = new CFile(cname);
        _c.println
          ("#include <stdlib.h>\n"+
           "#include <stdio.h>\n"+
           "#include <unistd.h>\n"+
           "#include <stdint.h>\n"+
           "typedef union slot_u * slot_r;\n"+
           "typedef union slot_u {\n"+
           " slot_r ref;\n"+
           " int32_t i32;\n"+
           " uint32_t u32;\n"+
           " int64_t i64;\n"+
           " uint64_t u64;\n"+
           "} slot_t;\n"+
           "slot_t fzC_exitForCompilerTest    (void /* fztype__mm_universe_mm_0_   */ *cur, slot_t code) { exit(code.i32); return ((slot_t) { }); }\n" +
           "slot_t fzC_fusion__std__out__write(void /* fztype_fusion__std__out_17_ */ *cur, slot_t c) { char cc = (char) c.i32; fwrite(&cc, 1, 1, stdout); return ((slot_t) { }); }\n" +
           "slot_t fzC_i32__prefix_wmO(slot_t cur          ) { return ((slot_t) { .i32 = - cur.i32       }); }\n" +
           "slot_t fzC_i32__infix_wmO (slot_t cur, slot_t i) { return ((slot_t) { .i32 = cur.i32 - i.i32 }); }\n" +
           "slot_t fzC_i32__infix_wpO (slot_t cur, slot_t i) { return ((slot_t) { .i32 = cur.i32 + i.i32 }); }\n" +
           "slot_t fzC_i32__infix_wg  (slot_t cur, slot_t i) { return cur.i32 > i.i32 ? " + FZ_TRUE + " : " + FZ_FALSE + "; }\n" +
           "slot_t fzC_i32__infix_wl  (slot_t cur, slot_t i) { return cur.i32 < i.i32 ? " + FZ_TRUE + " : " + FZ_FALSE + "; }\n");
        for (var c = _fuir.firstClazz(); c <= _fuir.lastClazz(); c++)
          {
            typesForClazz(c);
          }
        for (var c = _fuir.firstClazz(); c <= _fuir.lastClazz(); c++)
          {
            compileClazz(c, CompilePhase.FORWARDS);
          }
        for (var c = _fuir.firstClazz(); c <= _fuir.lastClazz(); c++)
          {
            compileClazz(c, CompilePhase.IMPLEMENTATIONS);
          }
        _c.println("int main(int argc, char **args) { " + featureMangledName(f) + "(NULL); }");
      }
    catch (IOException io)
      { // NYI: proper error handling
        io.printStackTrace();
        return;
      }
    finally
      {
        if (_c != null)
          {
            _c.close();
            _c = null;
          }
      }
  }


  /**
   * Mangle an arbitrary unicode string into a legal C identifier.
   *
   * NYI: This might produce a string longer than a legal C identifier name,
   * which seems to be 31 according to
   * https://stackoverflow.com/questions/2352209/max-identifier-length
   *
   * @param s an arbitrary string
   *
   * @return a string usable as C function name, i.e., starting with a letter or
   * '_' followed by letters, digits or '_'.
   */
  String mangle(String s)
  {
    StringBuilder sb = new StringBuilder();
    final int length = s.length();
    boolean alphaMode = true;
    for (int cp, off = 0; off < length; off += Character.charCount(cp))
      {
        cp = s.codePointAt(off);

        boolean alpha = ('a' <= cp && cp <= 'z' ||
                         'A' <= cp && cp <= 'Z' ||
                         '0' <= cp && cp <= '9' && off > 0);
        if (alphaMode != alpha)
          {
            sb.append("_");
            alphaMode = alpha;
          }
        if (alpha)
          {
            sb.append((char) cp);
          }
        else if ('.' == cp) { sb.append("o"); }
        else if (' ' == cp) { sb.append("w"); }
        else if ('_' == cp) { sb.append("u"); }
        else if ('+' == cp) { sb.append("p"); }
        else if ('-' == cp) { sb.append("m"); }
        else if ('*' == cp) { sb.append("t"); }
        else if ('/' == cp) { sb.append("d"); }
        else if ('<' == cp) { sb.append("l"); }
        else if ('>' == cp) { sb.append("g"); }
        else if ('=' == cp) { sb.append("e"); }
        else if ('!' == cp) { sb.append("n"); }
        else if ('^' == cp) { sb.append("c"); }
        else if ('(' == cp) { sb.append("L"); }
        else if (')' == cp) { sb.append("R"); }
        else if ('?' == cp) { sb.append("Q"); }
        else if ('$' == cp) { sb.append("D"); }
        else if ('%' == cp) { sb.append("P"); }
        else if ('°' == cp) { sb.append("O"); }
        else
          {
            sb.append("U").append(Integer.toHexString(cp)).append("_");
          }
      }
    return sb.toString();
  }


  /**
   * Append mangled name of given feature to StringBuilder.
   *
   * @param f a feature id
   *
   * @param sb a StringBuilder
   */
  public void featureMangledName(int f, StringBuilder sb)
  {
    if (!_fuir.featureIsUniverse(f) &&
        !_fuir.featureIsUniverse(_fuir.featureOuter(f)))
      {
        featureMangledName(_fuir.featureOuter(f), sb);
        sb.append("__");
      }
    sb.append(mangle(_fuir.featureBaseName(f)));
  }


  /**
   * Create mangled name of a given feature.
   *
   * @param f a feature id.
   *
   * @return a corresponding valid C function name
   */
  public String featureMangledName(int f)
  {
    var sb = new StringBuilder(C_FUNCTION_PREFIX);
    featureMangledName(f, sb);
    String res = sb.toString();

    if (res.length() > MAX_C99_IDENTIFIER_LENGTH)
      {
        System.err.println("*** WARNING: Max C99 identifier length exceeded for '" + res + "'");
      }

    return res;
  }

  /**
   * NYI: Documentation, just discard the sign?
   */
  int clazzId2num(int cl)
  {
    return cl & 0xFFFffff; // NYI: give a name to this constant
  }

  /**
   * Create a C identifier for the type of an instance of the given clazz.
   *
   * @param cl a clazz id.
   *
   * @return corresponding C type name
   */
  String clazzTypeName(int cl)
  {
    StringBuilder sb = new StringBuilder(TYPE_PREFIX);
    featureMangledName(_fuir.clazz2FeatureId(cl), sb);
    // NYI: there might be name conflicts due to different generic instances, so
    // we need to add the clazz id or the actual generics if this is the case:
    //
    //   sb.append("_").append(clazzId2num(cl)).append("_");

    String res = sb.toString();

    if (res.length() > MAX_C99_IDENTIFIER_LENGTH)
      {
        System.err.println("*** WARNING: Max C99 identifier length exceeded for '" + res + "'");
      }

    return res;
  }


  /**
   * Create declarations of the C types required for the given clazz.  Write
   * code to _cout.
   *
   * @param cl a clazz id.
   */
  public void typesForClazz(int cl)
  {
    var f = _fuir.clazz2FeatureId(cl);
    switch (_fuir.featureKind(f))
      {
      case Routine:
        {
          _c.print
            ("typedef struct {\n" +
             (_fuir.clazzIsRef(cl) ? "  uint32_t clazzId;\n" : "") +
             "  slot_t fields["+_fuir.clazzSize(cl)+"];\n"+
             "} " + clazzTypeName(cl) + ";\n");
          break;
        }
      default:
        break;
      }
  }


  /**
   * Create C code to pass given number of arguments plus one implicit target
   * argument from the stack to a called feature.  Write code to _cout.
   *
   * @param stack the stack containing the C code of the args.
   *
   * @param argCount the number of arguments.
   */
  void passArgs(Stack<String> stack, int argCount)
  {
    var a = stack.pop();
    if (argCount > 0)
      {
        passArgs(stack, argCount-1);
        _c.print(", ");
      }
    _c.print(a);
  }


  /**
   * Create C code for code block c of clazz cl with given stack contents at
   * beginning of the block.  Write code to _cout.
   *
   * @param cl clazz id
   *
   * @param stack the stack containing the current arguments waiting to be used
   *
   * @param c the code block to compile
   */
  void createCode(int cl, Stack<String> stack, int c)
  {
    for (int i = 0; _fuir.withinCode(c, i); i++)
      {
        var s = _fuir.codeAt(c, i);
        // System.out.println("Code at "+i+": " + s);
        switch (s)
          {
          case AdrToValue:
            {
              // NYI: can this be a NOP?
              break;
            }
          case Assign:
            {
              var field = _fuir.assignedField(c, i);  // field we are assigning to
              var outer = stack.pop();                // instance containing assigned field
              var value = stack.pop();                // value assigned to field
              var outercl = _fuir.assignOuterClazz(cl, c, i);  // static clazz of outer
              var valuecl = _fuir.assignValueClazz(cl, c, i);  // static clazz of value
              if (valuecl != -1) // void value. NYI: better remove the Assign altogether from the IR in this case
                {
                  var fclazz = _fuir.assignClazzForField(outercl, field);  // static clazz of assigned field
                  String offset;
                  if (_fuir.clazzIsRef(outercl))
                    {
                      offset = "/* NYI: dynamic binding needed */";
                    }
                  else
                    {
                      offset = Integer.toString(_fuir.clazzFieldOffset(outercl, field));
                    }
                  if (_fuir.clazzIsChoice(fclazz) &&
                      fclazz != valuecl &&  // NYI: interpreter checks fclazz._type != staticTypeOfValue
                      !_fuir.featureIsOuterRef(field) /* outerref might be an adr of a value type */
                      )
                    {
                      _c.println("// NYI: Assign to choice field "+outer+"."+_fuir.featureAsString(field)+" = "+value);
                    }
                  else if (_fuir.clazzIsRef(fclazz))
                    {
                      _c.println("// NYI: Assign ref value "+outer+"."+_fuir.featureAsString(field)+" = "+value);
                    }
                  else
                    {
                      _c.println("" + outer + "->fields[" + offset + "] = " + value + ";");
                    }
                }
              else
                {
                  _c.println("// NOP assignment to " + _fuir.featureAsString(field) + " of void type removed");
                }
              break;
            }
          case Box:
            {
              var v = stack.pop();
              stack.push("*** boxed("+v+") ***");
              _c.println("// NYI: Box " + v + "!");
              break;
            }
          case Call:
            {
              var cf = _fuir.callCalledFeature(c, i);
              var ac = _fuir.callArgCount(c, i);
              if (_fuir.callIsDynamic(c, i))
                {
                  _c.println("// NYI : dynamic call to feature: " + featureMangledName(cf) + " (");
                  passArgs(stack, ac);
                  _c.println(")");
                }
              else
                {
                  switch (_fuir.featureKind(cf))
                    {
                    case Routine  :
                    case Intrinsic:
                      {
                        if (SHOW_STACK_ON_CALL) System.out.println("Befor call to "+_fuir.featureAsString(cf)+": "+stack);
                        var r = _fuir.featureResultField(cf);
                        String res;
                        if (r != -1 ||
                            _fuir.featureKind(cf) == FUIR.FeatureKind.Intrinsic)
                          {
                            res = TEMP_VAR_PREFIX + (_resultId++);
                            _c.print("slot_t " + res + " = ");
                          }
                        else
                          {
                            res = "(/*-- no result --*/ NULL)";
                          }
                        String n = featureMangledName(cf);
                        _c.print("" + n + "(");
                        passArgs(stack, ac);
                        _c.println(");");
                        stack.push(res);
                        if (SHOW_STACK_ON_CALL) System.out.println("After call to "+_fuir.featureAsString(cf)+": "+stack);
                        break;
                      }
                    case Field    :
                      {
                        var t = stack.pop();
                        var tc = _fuir.callTargetClazz(cl, c, i);
                        stack.push(t + "->fields[" + _fuir.callFieldOffset(tc, c, i) + "]");
                        break;
                      }
                    case Abstract :
                      {
                        _c.print("// NYI : Call abstract: " + featureMangledName(cf) + " (");
                        passArgs(stack, ac);
                        _c.println(");");
                        stack.push("/* NYI : Abstract result */ NULL");
                        break;
                      }
                    }
                }
              break;
            }
          case Current:
            {
              stack.push(CURRENT);
              break;
            }
          case If:
            {
              var cond = stack.pop();
              var block     = _fuir.i32Const(c, i + 1);
              var elseBlock = _fuir.i32Const(c, i + 2);
              i = i + 2;
              _c.println("if (" + cond + ".i32 != 0) {");
              _c.indent();
              createCode(cl, stack, block);
              _c.unindent();
              _c.println("} else {");
              // NYI: clone stack
              _c.indent();
              createCode(cl, stack, elseBlock);
              _c.unindent();
              _c.println("}");
              // NYI: join stacks
              break;
            }
          case boolConst:
            {
              var bc = _fuir.boolConst(c, i);
              stack.push(bc ? FZ_TRUE : FZ_FALSE);
              break;
            }
          case i32Const: { var ic = _fuir.i32Const(c, i); stack.push("((slot_t) { .i32 = " + ic + "})"); break; }
          case u32Const: { var ic = _fuir.u32Const(c, i); stack.push("((slot_t) { .u32 = " + ic + "})"); break; }
          case i64Const: { var ic = _fuir.i64Const(c, i); stack.push("((slot_t) { .i64 = " + ic + "})"); break; }
          case u64Const: { var ic = _fuir.u64Const(c, i); stack.push("((slot_t) { .u64 = " + ic + "})"); break; }
          case strConst:
            {
              stack.push("(/* NYI: String const */ \"\")");
              break;
            }
          case Match:
            {
              var v = stack.pop();
              _c.println("// NYI: match " + v + "!");
              stack.push("*** match("+v+") result ***");
              break;
            }
          case Singleton:
            {
              stack.push("(/*-- NYI: Singleton result --*/ NULL)");
              break;
            }
          case NOP:
          case Unknown: // NYI: remove
            {
              break;
            }
          default:
            {
              System.err.println("*** error: C backend does not handle statments of type " + s);
            }
          }
        if (SHOW_STACK_AFTER_STMNT) System.out.println("After " + s +" in "+_fuir.clazzAsString(cl)+": "+stack);
      }
  }


  /**
   * Create code for the C function implemeting the routine corresponding to the
   * given clazz.  Write code to _cout.
   *
   * @param cl id of clazz to compile
   *
   */
  private void cFunctionDecl(int cl)
  {
    var f = _fuir.clazz2FeatureId(cl);
    var res = _fuir.featureResultField(f);
    _c.print(res == -1
            ? "void "
            : "slot_t ");
    _c.print(featureMangledName(f));
    _c.print("(");
    var oc = _fuir.clazzOuterClazz(cl);
    String comma = "";
    if (oc != -1)
      {
        _c.print(clazzTypeName(oc));
        _c.print(" *fzouter");
        comma = ", ";
      }
    var ac = _fuir.clazzArgCount(cl);
    for (int i = 0; i < ac; i++)
      {
        _c.print(comma);
        _c.print("slot_t arg" + i);
        comma = ", ";
      }
    _c.print(")");
  }


  /**
   * Create code for given clazz cl in given code generation phase
   *
   * @param cl id of clazz to compile
   *
   * @param phase specifies what code to generate (forward declarations or
   * function declarations)
   */
  public void compileClazz(int cl, CompilePhase phase)
  {
    switch (phase)
      {
      case FORWARDS:
        {
          var f = _fuir.clazz2FeatureId(cl);
          switch (_fuir.featureKind(f))
            {
            case Routine:
              {
                cFunctionDecl(cl);
                _c.print(";\n");
                break;
              }
            case Field:
              break;
            default:
              break;
            }
          break;
        }
      case IMPLEMENTATIONS:
        {
          _resultId = 0;  // reset counter for unique temp variables for function results
          var f = _fuir.clazz2FeatureId(cl);
          switch (_fuir.featureKind(f))
            {
            case Routine:
              {
                _c.print("\n// code for clazz "+_fuir.clazzAsString(cl)+":\n");
                cFunctionDecl(cl);
                _c.print(" {\n");
                _c.indent();
                _c.print("" + clazzTypeName(cl) + " *" + CURRENT + " = malloc(sizeof(" + clazzTypeName(cl) + "));\n"+
                        (_fuir.clazzIsRef(cl) ? CURRENT + "->clazzId = " + clazzId2num(cl) + ";\n" : ""));

                var ac = _fuir.clazzArgCount(cl);
                for (int i = 0; i < ac; i++)
                  {
                    var af = _fuir.clazzArg(cl, i);
                    if (af >= 0) // af < 0 for unused argument fields.
                      {
                        var offset = _fuir.clazzFieldOffset(cl, af);
                        _c.print("cur->fields[" + offset + "] = arg" + i +";\n");
                      }
                  }
                var c = _fuir.featureCode(f);
                var stack = new Stack<String>();
                createCode(cl, stack, c);
                var res = _fuir.featureResultField(f);
                if (res != -1)
                  {
                    _c.println("return cur->fields[" + _fuir.clazzFieldOffset(cl, res) + "];");
                  }
                _c.unindent();
                _c.println("}");
                break;
              }
            case Field:
              break;
            default:
              _c.println("// NYI: code for "+_fuir.featureKind(f)+" "+ featureMangledName(f));
              break;
            }
          break;
        }
      }
  }

}

/* end of file */
