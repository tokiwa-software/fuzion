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
import java.util.TreeSet;

import dev.flang.ir.Backend;
import dev.flang.ir.BackendCallable;
import dev.flang.ir.Clazz;

import dev.flang.ir.Clazzes; // NYI: remove this dependency!

import dev.flang.fuir.FUIR;

import dev.flang.util.Errors;
import dev.flang.util.FuzionOptions;
import dev.flang.util.FuzionConstants;


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
   * Value to be used during compiler development for expressions that are not
   * yet implemented.
   */
  private static final String DUMMY = "NYI_DUMMY_VALUE";


  /**
   * Name of local variable containing current instance
   */
  private static final CExpr CURRENT = CExpr.ident("fzCur");


  /**
   * Prefix for types declared for clazz instances
   */
  private static final String REF_TYPE_PREFIX = "fzTr_";
  private static final String VAL_TYPE_PREFIX = "fzT_";


  /**
   * Prefix for C functions created for Fuzion routines or intrinsics
   */
  private static final String C_FUNCTION_PREFIX = "fzC_";


  /**
   * Prefix for temporary local variables
   */
  private static final String TEMP_VAR_PREFIX = "fzM_";


  /**
   * Prefix for fields in an instance
   */
  private static final String FIELD_PREFIX = "fzF_";


  /**
   * C constants corresponding to Fuzion's true and false values.
   */
  private static final CExpr FZ_FALSE =  CExpr.compoundLiteral(VAL_TYPE_PREFIX + "bool", "0");
  private static final CExpr FZ_TRUE  =  CExpr.compoundLiteral(VAL_TYPE_PREFIX + "bool", "1");


  /**
   * The name of the tag field in instances of bool.fz.
   */
  private static final String BOOL_TAG_NAME = "fzF_0_" + mangle(FuzionConstants.CHOICE_TAG_NAME);


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


  /**
   * Set of clazz ids for all the clazzes whose structs have been declared
   * already.  Structs are declared recursively with structs of inner fields
   * declared before outer structs.  This set keeps track which structs have been
   * declared already to avoid duplicates.
   */
  private final TreeSet<Integer> _declaredStructs = new TreeSet<>();


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create C code backend for given intermidiate code.
   *
   * @param fuir the intermeidate code.
   *
   * @param opt options to control compilation.
   */
  public C(FuzionOptions opt,
           FUIR fuir)
  {
    _fuir = fuir;
    Clazzes.findAllClasses(this, _fuir.main()); /* NYI: remove this, should be done within FUIR */
    Errors.showAndExit();
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
           "\n" +
           "void * " + DUMMY + "0;\n"+
           "#define " + DUMMY + " (*" + DUMMY + "0)\n");
        for (var c = _fuir.firstClazz(); c <= _fuir.lastClazz(); c++)
          {
            typesForClazz(c);
          }
        for (var c = _fuir.firstClazz(); c <= _fuir.lastClazz(); c++)
          {
            structsForClazz(c);
          }
        for (var c = _fuir.firstClazz(); c <= _fuir.lastClazz(); c++)
          {
            compileClazz(c, CompilePhase.FORWARDS);
          }
        for (var c = _fuir.firstClazz(); c <= _fuir.lastClazz(); c++)
          {
            compileClazz(c, CompilePhase.IMPLEMENTATIONS);
          }
        _c.println("int main(int argc, char **args) { " + clazzMangledName(cl) + "(NULL); }");
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
  static String mangle(String s)
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
   * Get the base name of the given feature and mangle it.  Do not include outer
   * feature's name.
   *
   * @parma f a feature id
   *
   * @return a mangled name such as "infix_wp" if f was "infix +".
   */
  String mangledFeatureBaseName(int f)
  {
    return mangle(_fuir.featureBaseName(f));
  }


  /**
   * Get the name of a field
   *
   * @param offset the slot offset of the field, needed to disambiguate fields
   * with equal name.
   *
   * @param field the field id
   */
  String fieldName(int offset, int field)
  {
    return FIELD_PREFIX + offset + "_" + mangledFeatureBaseName(field);
  }

  /**
   * Get the name of a field from an instances of a given clazz
   *
   * @param cl clazz id of the clazz that contains the field.
   *
   * @param field the field id
   */
  String fieldNameInClazz(int cl, int field)
  {
    int offset = _fuir.clazzFieldOffset(cl, field);
    return fieldName(offset, field);
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
    StringBuilder sb = new StringBuilder(_fuir.clazzIsRef(cl) ? REF_TYPE_PREFIX : VAL_TYPE_PREFIX);
    clazzMangledName(cl, sb);
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

  String clazzTypeNameRefOrVal(int cl)
  {
    return clazzTypeName(cl) + (_fuir.clazzIsRef(cl) ? "*" : "");
  }


  /**
   * Is the given clazz the stdlib clazz i32?
   */
  public boolean isI32(int cl)
  {
    return _fuir.clazzIsI32(cl);
  }


  /**
   * Create declarations of the C types required for the given clazz.  Write
   * code to _c.
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
          if (isI32(cl))  // special handling of stdlib clazzes known to the compiler
            {
              _c.print("typedef int32_t " + clazzTypeName(cl) + ";\n");
            }
          else
            {
              _c.print
                ("typedef struct " + clazzTypeName(cl) + " " + clazzTypeName(cl) + ";\n");
            }
          break;
        }
      default:
        break;
      }
  }


  /**
   * Create declarations of the C structs required for the given clazz.  Write
   * code to _c.
   *
   * @param cl a clazz id.
   */
  public void structsForClazz(int cl)
  {
    var f = _fuir.clazz2FeatureId(cl);
    switch (_fuir.featureKind(f))
      {
      case Routine:
        {
          if (!_declaredStructs.contains(cl))
            {
              _declaredStructs.add(cl);
              if (isI32(cl))  // special handling of stdlib clazzes known to the compiler
                {
                }
              else
                {
                  // first, make sure structs used for inner fields are declared:
                  for (int i = 0; i < _fuir.clazzSize(cl); i++)
                    {
                      var fcl = _fuir.clazzFieldSlotClazz(cl, i);
                      if (fcl != -1 /* no field at this slot, NYI: this should have been removed by FUIR */ &&
                          fcl != -2 /* void field,            NYI: this should have been removed by FUIR */ &&
                          fcl != -3 /* outer ref,             NYI: this should have been removed by FUIR */ &&
                          !_fuir.clazzIsRef(fcl))
                        {
                          structsForClazz(fcl);
                        }
                    }

                  // next, declare the struct itself
                  _c.print
                    ("// for " + _fuir.clazzAsString(cl) + "\n" +
                     "struct " + clazzTypeName(cl) + " {\n" +
                     (_fuir.clazzIsRef(cl) ? "  uint32_t clazzId;\n" : ""));
                  for (int i = 0; i < _fuir.clazzSize(cl); i++)
                    {
                      var fcl = _fuir.clazzFieldSlotClazz(cl, i);  // NYI: Slots are an interpreter artifact, should just iterate the fields instead
                      if (fcl == -1)
                        {
                          _c.print(" /* slot " + i + " not used */\n");
                        }
                      else if (fcl == -2) // NYI: ugly inline constant
                        {
                          _c.print(" /* slot " + i + " is VOID */\n");
                        }
                      else
                        {
                          var cf = _fuir.clazzField(cl, i);
                          String type;
                          if (_fuir.fieldIsOuterRef(cf))
                            {
                              var ocl = _fuir.clazzOuterClazz(cl);
                              type = clazzTypeName(ocl) + "*";
                            }
                          else
                            {
                              check
                                (fcl != -3); // NYI: ugly inline constant for ADDRESS type
                              type = clazzTypeNameRefOrVal(fcl);
                            }
                          _c.print(" " + type + " " + fieldName(i, cf) + ";\n");
                        }
                    }
                  _c.print
                    ("};\n\n");
                }
            }
        }
        break;
      default:
        break;
      }
  }


  /**
   * Create C code to pass given number of arguments plus one implicit target
   * argument from the stack to a called feature.  Write code to _c.
   *
   * @param stack the stack containing the C code of the args.
   *
   * @param argCount the number of arguments.
   */
  void passArgs(Stack<CExpr> stack, int argCount)
  {
    var a = stack.pop();
    if (argCount > 0)
      {
        passArgs(stack, argCount-1);
        _c.print(", ");
        _c.print(a);
      }
    else
      { // ref to outer instance, passed by reference
        _c.print(a.adrOf());
      }
  }


  /**
   * Create C code for code block c of clazz cl with given stack contents at
   * beginning of the block.  Write code to _c.
   *
   * @param cl clazz id
   *
   * @param stack the stack containing the current arguments waiting to be used
   *
   * @param c the code block to compile
   */
  void createCode(int cl, Stack<CExpr> stack, int c)
  {
    for (int i = 0; _fuir.withinCode(c, i); i++)
      {
        var s = _fuir.codeAt(c, i);
        switch (s)
          {
          case AdrToValue:
            { // dereference an outer reference
              var a = stack.pop();
              var v = a;  /* a.deref(); --  NYI: AdrToValue is NOP for now since outer refs as values not supported in C backend yet */
              stack.push(v);
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
                  var fieldName = fieldNameInClazz(outercl, field);
                  var fieldAccess = ccodeAccessField(outercl, outer, fieldName);
                  if (_fuir.clazzIsChoice(fclazz) &&
                      fclazz != valuecl &&  // NYI: interpreter checks fclazz._type != staticTypeOfValue
                      !_fuir.featureIsOuterRef(field) /* outerref might be an adr of a value type */
                      )
                    {
                      _c.println("// NYI: Assign to choice field "+outer+"."+_fuir.featureAsString(field)+" = "+value);
                    }
                  else if (_fuir.clazzIsRef(fclazz))
                    {
                      _c.print(fieldAccess.assign(value)); _c.println(";  /* ref type */");
                    }
                  else
                    {
                      _c.print(fieldAccess.assign(value)); _c.println(";  /* value type */");
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
              stack.push(CExpr.dummy("NYI: boxed"));
              _c.println("// NYI: Box " + v + "!");
              break;
            }
          case Call:
            {
              var ac = _fuir.callArgCount(c, i);
              if (_fuir.callIsDynamic(cl, c, i))
                {
                  _c.println("// NYI: Dynamic binding for call to " + _fuir.callDebugString(c, i) + " not supported");
                  var cc = _fuir.callCalledClazz(cl, c, i);
                  var cf = _fuir.clazz2FeatureId(cc);
                  switch (_fuir.featureKind(cf))
                    {
                    case Abstract :  _c.println("// NYI : dynamic call to abstract: " + _fuir.callDebugString(c, i));
                    case Routine  :
                    case Intrinsic:
                      {
                        if (SHOW_STACK_ON_CALL) System.out.println("Before call to "+_fuir.clazzAsString(cc)+": "+stack);
                        CExpr res = CExpr.ident(DUMMY); // NYI: no result, needed as a workaround for functions returning current instance
                        var rt = _fuir.callResultType(cl, c, i);
                        if (rt != -1)
                          {
                            var tmp = TEMP_VAR_PREFIX + (_resultId++);
                            res = CExpr.ident(tmp);
                            _c.print(clazzTypeName(rt) + " " + tmp + " = ");
                          }
                        String n = clazzMangledName(cc);
                        _c.print("" + n + "(");
                        passArgs(stack, ac);
                        _c.println(");");
                        stack.push(res);
                        if (SHOW_STACK_ON_CALL) System.out.println("After call to "+_fuir.clazzAsString(cc)+": "+stack);
                        break;
                      }
                    case Field:
                      {
                        var t = stack.pop();
                        var tc = _fuir.callTargetClazz(cl, c, i);
                        var field = fieldName(_fuir.callFieldOffset(tc, c, i), cf);
                        CExpr res = ccodeAccessField(tc, t, field);
                        if (_fuir.fieldIsOuterRef(cf))  // NYI: Better have a specific Statement for this
                          {
                            res = res.deref();
                          }
                        stack.push(res);
                        break;
                      }
                    default:       throw new Error("This should not happen: Unknown feature kind: " + _fuir.featureKind(cf));
                    }
                }
              else
                {
                  var cc = _fuir.callCalledClazz(cl, c, i);
                  var cf = _fuir.clazz2FeatureId(cc);
                  switch (_fuir.featureKind(cf))
                    {
                    case Routine  :
                    case Intrinsic:
                      {
                        if (SHOW_STACK_ON_CALL) System.out.println("Before call to "+_fuir.clazzAsString(cc)+": "+stack);
                        CExpr res = CExpr.ident(DUMMY); // NYI: no result, needed as a workaround for functions returning current instance
                        var rt = _fuir.callResultType(cl, c, i);
                        if (rt != -1)
                          {
                            var tmp = TEMP_VAR_PREFIX + (_resultId++);
                            res = CExpr.ident(tmp);
                            _c.print(clazzTypeName(rt) + " " + tmp + " = ");
                          }
                        String n = clazzMangledName(cc);
                        _c.print("" + n + "(");
                        passArgs(stack, ac);
                        _c.println(");");
                        stack.push(res);
                        if (SHOW_STACK_ON_CALL) System.out.println("After call to "+_fuir.clazzAsString(cc)+": "+stack);
                        break;
                      }
                    case Field:
                      {
                        var t = stack.pop();
                        var tc = _fuir.callTargetClazz(cl, c, i);
                        var field = fieldName(_fuir.callFieldOffset(tc, c, i), cf);
                        CExpr res = ccodeAccessField(tc, t, field);
                        if (_fuir.fieldIsOuterRef(cf))  // NYI: Better have a specific Statement for this
                          {
                            res = res.deref();
                          }
                        stack.push(res);
                        break;
                      }
                    case Abstract: throw new Error("This should not happen: Calling abstract '" + _fuir.clazzAsString(cc) + "'");
                    default:       throw new Error("This should not happen: Unknown feature kind: " + _fuir.featureKind(cf));
                    }
                }
              break;
            }
          case Current:
            {
              stack.push(CURRENT.deref());
              break;
            }
          case If:
            {
              var cond = stack.pop();
              var block     = _fuir.i32Const(c, i + 1);
              var elseBlock = _fuir.i32Const(c, i + 2);
              i = i + 2;
              _c.println("if (" + cond.field(BOOL_TAG_NAME).code() + " != 0) {");
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
          case i32Const: { var ic = _fuir.i32Const(c, i); stack.push(CExpr. int32const(ic)); break; }
          case u32Const: { var ic = _fuir.u32Const(c, i); stack.push(CExpr.uint32const(ic)); break; }
          case i64Const: { var ic = _fuir.i64Const(c, i); stack.push(CExpr. int64const(ic)); break; }
          case u64Const: { var ic = _fuir.u64Const(c, i); stack.push(CExpr.uint64const(ic)); break; }
          case strConst:
            {
              stack.push(CExpr.dummy("NYI: String const"));
              break;
            }
          case Match:
            {
              var v = stack.pop();
              _c.println("// NYI: match " + v + "!");
              stack.push(CExpr.dummy("NYI: match result"));
              break;
            }
          case Singleton:
            {
              _c.println("// NYI: singleton ");
              stack.push(CExpr.ident(DUMMY)); // NYI: Singleton result
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
   * Append mangled name of given feature to StringBuilder.  Prepend with outer
   * feature's mangled name.
   *
   * Ex. Feature "i32.prefix -"  will result in  "i32__prefix_wm"
   *
   * @param f a feature id
   *
   * @param sb a StringBuilder
   */
  public void clazzMangledName(int cl, StringBuilder sb)
  {
    var o = _fuir.clazzOuterClazz(cl);
    if (o != -1 &&
        _fuir.clazzOuterClazz(o) != -1)
      { // add o a prefix unless cl or o are universe
        clazzMangledName(o, sb);
        sb.append("__");
      }
    var f = _fuir.clazz2FeatureId(cl);
    sb.append(mangledFeatureBaseName(f));
  }


  /**
   * Create unique a mangled name for a clazz that can be used in C identifiers
   * (i.e., it starts with letter or '_' and contains only ASCII letters, digits
   * or '_'.
   *
   * @param cl id of a clazz
   */
  String clazzMangledName(int cl)
  {
    var sb = new StringBuilder(C_FUNCTION_PREFIX);
    clazzMangledName(cl, sb);
    String res = sb.toString();

    if (res.length() > MAX_C99_IDENTIFIER_LENGTH)
      {
        System.err.println("*** WARNING: Max C99 identifier length exceeded for '" + res + "'");
      }

    return res;
  }


  /**
   * Create code for the C function implemeting the routine corresponding to the
   * given clazz.  Write code to _c.
   *
   * @param cl id of clazz to compile
   *
   */
  private void cFunctionDecl(int cl)
  {
    var res = _fuir.clazzResultClazz(cl);
    _c.print(res == -1
             ? "void "
             : clazzTypeName(res) + " ");
    _c.print(clazzMangledName(cl));
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
        var at = _fuir.clazzArgClazz(cl, i);
        var t = at == -1 ? "slot_t" : clazzTypeNameRefOrVal(at);
        _c.print(t + " arg" + i);
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
            case Intrinsic:
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
                _c.print("" + clazzTypeName(cl) + " *" + CURRENT.code() + " = malloc(sizeof(" + clazzTypeName(cl) + "));\n"+
                         (_fuir.clazzIsRef(cl) ? CURRENT.deref().field("clazzId").assign(CExpr.int32const(clazzId2num(cl))).code() + ";\n" : ""));

                var or = _fuir.clazzOuterRef(cl);
                if (or != -1)
                  {
                    var outer = CExpr.ident("fzouter");
                    _c.print(CURRENT.deref().field(fieldNameInClazz(cl, or)).assign(outer).code() + ";\n");
                  }

                var ac = _fuir.clazzArgCount(cl);
                for (int i = 0; i < ac; i++)
                  {
                    var af = _fuir.clazzArg(cl, i);
                    if (af >= 0) // af < 0 for unused argument fields.
                      {
                        _c.print(CURRENT.deref().field(fieldNameInClazz(cl, af)).assign(CExpr.ident("arg" + i)).code() + ";\n");
                      }
                  }
                var c = _fuir.featureCode(f);
                var stack = new Stack<CExpr>();
                try
                  {
                    createCode(cl, stack, c);
                  }
                catch (RuntimeException | Error e)
                  {
                    _c.println("// *** compiler crash: " + e);
                    Errors.error("C backend compiler crash",
                                 "While creating code for " + _fuir.clazzAsString(cl) + "\n" +
                                 "Java Error: " + e);
                  }
                var res = _fuir.clazzResultClazz(cl);
                if (res != -1)
                  {
                    var rf = _fuir.featureResultField(f);
                    if (rf != -1)
                      {
                        _c.println("return " + CURRENT.deref().field(fieldNameInClazz(cl, rf)).code() + ";");
                      }
                    else
                      {
                        _c.println("return " + CURRENT.deref().code() + ";");
                      }
                  }
                _c.unindent();
                _c.println("}");
                break;
              }
            case Intrinsic:
              {
                _c.print("\n// code for intrinsic " + _fuir.clazzAsString(cl) + ":\n");
                cFunctionDecl(cl);
                _c.print(" {\n");
                switch (clazzMangledName(cl))
                  {
                  case C_FUNCTION_PREFIX + "exitForCompilerTest"    : _c.print(" exit(arg0);\n"); break;
                  case C_FUNCTION_PREFIX + "fuzion__std__out__write": _c.print(" char c = (char) arg0; fwrite(&c, 1, 1, stdout);\n"); break;
                  case C_FUNCTION_PREFIX + "i32__prefix_wmO"        : _c.print(" return - *fzouter;\n"); break;
                  case C_FUNCTION_PREFIX + "i32__infix_wmO"         : _c.print(" return *fzouter -  arg0;\n"); break;
                  case C_FUNCTION_PREFIX + "i32__infix_wpO"         : _c.print(" return *fzouter +  arg0;\n"); break;
                  case C_FUNCTION_PREFIX + "i32__infix_wg"          : _c.print(" return *fzouter >  arg0 ? " + FZ_TRUE.code() + " : " + FZ_FALSE.code() + ";\n"); break;
                  case C_FUNCTION_PREFIX + "i32__infix_wge"         : _c.print(" return *fzouter >= arg0 ? " + FZ_TRUE.code() + " : " + FZ_FALSE.code() + ";\n"); break;
                  case C_FUNCTION_PREFIX + "i32__infix_wl"          : _c.print(" return *fzouter <  arg0 ? " + FZ_TRUE.code() + " : " + FZ_FALSE.code() + ";\n"); break;
                  case C_FUNCTION_PREFIX + "i32__infix_wle"         : _c.print(" return *fzouter <= arg0 ? " + FZ_TRUE.code() + " : " + FZ_FALSE.code() + ";\n"); break;
                  default:                                            _c.print(" fprintf(stderr, \"*** error: NYI: code for intrinsic " + _fuir.clazzAsString(cl) + " missing!\\n\"); exit(1);\n"); break;
                  }
                _c.print("}\n");
              }
            case Field:
              break;
            default:
              _c.println("// NYI: code for "+_fuir.featureKind(f)+" "+ clazzMangledName(cl));
              break;
            }
          break;
        }
      }
  }


  /**
   * Create C code to access a field, dereferencing if needed.
   *
   * @param outercl the clazz id of the type of outer, used to tell if outer is ref or value
   *
   * @param outer C expression that result in the instance that contains the field
   *
   * @param fieldName C identifier that gives the name of the field
   */
  CExpr ccodeAccessField(int outercl, CExpr outer, String fieldName)
  {
    if (_fuir.clazzIsRef(outercl))
      {
        outer = outer.deref();
      }
    return outer.field(fieldName);
  }

}

/* end of file */
