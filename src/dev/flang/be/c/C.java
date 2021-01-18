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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
   * Name of local variable containing current instance
   */
  private static final String CURRENT = "cur";


  /*----------------------------  variables  ----------------------------*/


  /**
   * The intermidiate code we are compiling.
   */
  private final FUIR _fuir;


  /**
   * Writer to create the C code to.
   */
  private PrintWriter cout;


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
        cout = new PrintWriter(Files.newBufferedWriter(Path.of(cname), StandardCharsets.UTF_8));
        cout.println("#include <stdlib.h>\n"+
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
                     "void fz_exitForCompilerTest(slot_t code) { exit(code.i32); }\n"+
                     "void fz_fusion__std__out__write(slot_t c) { char cc = (char) c.i32; fwrite(&cc, 1, 1, stdout); }\n");
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
        cout.println("int main(int argc, char **args) { " + featureMangledName(f) + "(); }\n");
      }
    catch (IOException io)
      { // NYI: proper error handling
        io.printStackTrace();
        return;
      }
    finally
      {
        if (cout != null)
          {
            cout.close();
            cout = null;
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
    var sb = new StringBuilder("fz_");
    featureMangledName(f, sb);
    return sb.toString();
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
    StringBuilder sb = new StringBuilder("fztype_");
    featureMangledName(_fuir.clazz2FeatureId(cl), sb);
    sb.append("_").append(clazzId2num(cl)).append("_");
    return sb.toString();
  }


  /**
   * Create declarations of the C types required for the given clazz.  Write
   * code to cout.
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
          cout.print("typedef struct {\n" +
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
   * Create C code to pass given number of argument from the stack to a called
   * feature.  Write code to cout.
   *
   * @param stack the stack containing the C code of the args.
   *
   * @param argCount the number of arguments.
   */
  void passArgs(Stack<String> stack, int argCount)
  {
    if (argCount > 0)
      {
        var a = stack.pop();
        passArgs(stack, argCount-1);
        if (argCount > 1)
          {
            cout.print(",");
          }
        cout.print(a);
      }
  }


  /**
   * Create C code for code block c of clazz cl with given stack contents at
   * beginning of the block.  Write code to cout.
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
                      cout.println("// NYI: Assign to choice field "+outer+"."+_fuir.featureAsString(field)+" = "+value);
                    }
                  else if (_fuir.clazzIsRef(fclazz))
                    {
                      cout.println("// NYI: Assign ref value "+outer+"."+_fuir.featureAsString(field)+" = "+value);
                    }
                  else
                    {
                      cout.println(" " + outer + "->fields[" + offset + "] = " + value + ";");
                    }
                }
              else
                {
                  cout.println("// NOP assignment to " + _fuir.featureAsString(field) + " of void type removed");
                }
              break;
            }
          case Box:
            {
              var v = stack.pop();
              stack.push("*** boxed("+v+") ***");
              cout.println("// NYI: Box " + v + "!");
              break;
            }
          case Call:
            {
              var cf = _fuir.callCalledFeature(c, i);
              var ac = _fuir.callArgCount(c, i);
              if (_fuir.callIsDynamic(c, i))
                {
                  cout.println("// NYI : dynamic call to feature: " + featureMangledName(cf) + " (");
                  passArgs(stack, ac);
                  cout.println(")");
                }
              else
                {
                  switch (_fuir.featureKind(cf))
                    {
                    case Routine  :
                    case Intrinsic:
                      {
                        var r = _fuir.featureResultField(cf);
                        String res;
                        if (r != -1)
                          {
                            res = "fzres_" + (_resultId++);
                            cout.print(" slot_t " + res + " = ");
                          }
                        else
                          {
                            res = "-- no result --";
                            cout.print(" ");
                          }
                        String n = featureMangledName(cf);
                        cout.print("" + n + "(");
                        passArgs(stack, ac);
                        cout.println(");");
                        stack.push(res);
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
                        cout.println("// NYI : Call abstract: " + featureMangledName(cf) + " (");
                        passArgs(stack, ac);
                        cout.println(");");
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
              cout.println("if ("+cond+") {");
              createCode(cl, stack, block);
              cout.println("} else {");
              // NYI: clone stack
              createCode(cl, stack, elseBlock);
              cout.println("}");
              // NYI: join stacks
              break;
            }
          case boolConst:
            {
              var bc = _fuir.boolConst(c, i);
              stack.push(bc ? "TRUE" : "FALSE");
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
              cout.println("// NYI: match " + v + "!");
              stack.push("*** match("+v+") result ***");
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
      }
  }


  /**
   * Create code for the C function implemeting the routine corresponding to the
   * given clazz.  Write code to cout.
   *
   * @param cl id of clazz to compile
   *
   */
  private void cFunctionDecl(int cl)
  {
    var f = _fuir.clazz2FeatureId(cl);
    var res = _fuir.featureResultField(f);
    cout.print(res == -1
               ? "void "
               : "slot_t ");
    cout.print(featureMangledName(f));
    cout.print("(");
    var ac = _fuir.clazzArgCount(cl);
    if (ac == 0)
      {
        cout.print("void");
      }
    else
      {
        for (int i = 0; i < ac; i++)
          {
            cout.print("slot_t arg" + i + (i < ac-1 ? "," : ""));
          }
      }
    cout.print(")");
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
                cout.print(";\n");
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
                cout.print("// code for clazz "+_fuir.clazzAsString(cl)+":\n");
                cFunctionDecl(cl);
                cout.print(" {\n"+
                           " " + clazzTypeName(cl) + " *" + CURRENT + " = malloc(sizeof(" + clazzTypeName(cl) + "));\n"+
                           (_fuir.clazzIsRef(cl) ? " " + CURRENT + ">clazzId = " + clazzId2num(cl) + ";\n" : ""));

                var ac = _fuir.clazzArgCount(cl);
                for (int i = 0; i < ac; i++)
                  {
                    var af = _fuir.clazzArg(cl, i);
                    if (af >= 0) // af < 0 for unused argument fields.
                      {
                        var offset = _fuir.clazzFieldOffset(cl, af);
                        cout.print(" cur->fields[" + offset + "] = arg" + i +";\n");
                      }
                  }
                var c = _fuir.featureCode(f);
                var stack = new Stack<String>();
                createCode(cl, stack, c);
                var res = _fuir.featureResultField(f);
                if (res != -1)
                  {
                    cout.println(" return cur->fields[" + _fuir.clazzFieldOffset(cl, res) + "];");
                  }
                cout.println("}");
                break;
              }
            case Field:
              break;
            default:
              cout.println("// NYI: code for "+_fuir.featureKind(f)+" "+ featureMangledName(f));
              break;
            }
          break;
        }
      }
  }

}

/* end of file */
