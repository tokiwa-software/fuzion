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


  private enum CompilePhase
  {
    FORWARDS,
    IMPLEMENTATIONS
  }

  /**
   * Name of local variable containing current instance
   */
  private static final String CURRENT = "cur";


  /*----------------------------  variables  ----------------------------*/


  private final FUIR _fuir;


  private PrintWriter cout;

  /*---------------------------  consructors  ---------------------------*/


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
                     "void fz_exitForCompilerTest(int code) { exit(code); }\n"+
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


  public String featureMangledName(int f)
  {
    var sb = new StringBuilder("fz_");
    featureMangledName(f, sb);
    return sb.toString();
  }

  int clazzId2num(int cl)
  {
    return cl & 0xFFFffff;
  }

  String clazzTypeName(int cl)
  {
    StringBuilder sb = new StringBuilder("fztype_");
    featureMangledName(_fuir.clazz2FeatureId(cl), sb);
    sb.append("_").append(clazzId2num(cl)).append("_");
    return sb.toString();
  }


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
              var field = "NYI: _fuir.assignedField(c, i);";
              var outer = stack.pop();
              var value = stack.pop();
              cout.println("// NYI: Assign "+outer+"."+field+" = "+value);
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
                        String n = featureMangledName(cf);
                        cout.println("" + n + "(");
                        passArgs(stack, ac);
                        cout.println(");");
                        stack.push("(/*NYI: Call result of " + n + " */ 0)");
                        break;
                      }
                    case Field    :
                      {
                        var t = stack.pop();
                        var tc = _fuir.callTargetClazz(cl, c, i);
                        stack.push(t + "[" + _fuir.callFieldOffset(tc, c, i) + "]");
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
          case i32Const:
            {
              var ic = _fuir.i32Const(c, i);
              stack.push("((int32_t)" + ic + ")");
              break;
            }
          case u32Const:
            {
              var ic = _fuir.u32Const(c, i);
              stack.push("((uint32_t)" + ic + ")");
              break;
            }
          case i64Const:
            {
              var ic = _fuir.i64Const(c, i);
              stack.push("((int64_t)" + ic + ")");
              break;
            }
          case u64Const:
            {
              var ic = _fuir.u64Const(c, i);
              stack.push("((uint64_t)" + ic + ")");
              break;
            }
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
                cout.print("void " + featureMangledName(f) + "();\n");
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
          var f = _fuir.clazz2FeatureId(cl);
          switch (_fuir.featureKind(f))
            {
            case Routine:
              {
                cout.print("void " + featureMangledName(f) + "() {\n"+
                           " " + clazzTypeName(cl) + " *" + CURRENT + " = malloc(sizeof(" + clazzTypeName(cl) + "));\n"+
                           (_fuir.clazzIsRef(cl) ? " " + CURRENT + ">clazzId = " + clazzId2num(cl) + ";\n" : ""));
                var c = _fuir.featureCode(f);
                var stack = new Stack<String>();
                createCode(cl, stack, c);
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
