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
import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.Stack;

import java.util.stream.Stream;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;


/**
 * C provides a C code backend converting FUIR data into C code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class C extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * C code generation phase for generating C functions for features.
   */
  private enum CompilePhase
  {
    TYPES           { void compile(C c, int cl) { c._c.print(c._types.types(cl));                    } }, // declare types
    STRUCTS         { void compile(C c, int cl) { c._types.structs(cl, c._c);                        } }, // generate struct declarations
    FORWARDS        { void compile(C c, int cl) { if (c._fuir.clazzIsCalled(cl)) { c.forwards(cl); } } }, // generate forward declarations only
    IMPLEMENTATIONS { void compile(C c, int cl) { if (c._fuir.clazzIsCalled(cl)) { c.code(cl);     } } }; // generate C functions

    /**
     * Perform this compilation phase on given clazz using given backend.
     *
     * @param c the backend
     *
     * @param cl the clazz.
     */
    abstract void compile(C c, int cl);
  }


  /**
   * Debugging output
   */
  private static final boolean SHOW_STACK_AFTER_STMNT = false;
  private static final boolean SHOW_STACK_ON_CALL = false;


  /*----------------------------  variables  ----------------------------*/


  /**
   * The intermediate code we are compiling.
   */
  final FUIR _fuir;


  /**
   * The options set for the compilation.
   */
  final COptions _options;


  /**
   * Writer to create the C code to.
   */
  CFile _c;


  /**
   * C identifier handling goes through _names:
   */
  final CNames _names;


  /**
   * C types handling goes through _types:
   */
  final CTypes _types;


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create C code backend for given intermidiate code.
   *
   * @param opt options to control compilation.
   *
   * @param fuir the intermeidate code.
   */
  public C(COptions opt,
           FUIR fuir)
  {
    _options = opt;
    _fuir = fuir;
    _names = new CNames(fuir);
    _types = new CTypes(_fuir, _names);
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
    if (_options.verbose(1))
      {
        System.out.println(" + " + cname);
      }
    try
      {
        _c = new CFile(cname);
        createCode();
      }
    catch (IOException io)
      {
        Errors.error("C backend I/O error",
                     "While creating code to '" + cname + "', received I/O error '" + io + "'");
      }
    finally
      {
        if (_c != null)
          {
            _c.close();
            _c = null;
          }
      }
    Errors.showAndExit();

    var command = new List<String>("clang", "-O3", "-o", name, cname);
    if (_options.verbose(1))
      {
        System.out.println(" * " + command.toString("", " ", ""));
      }
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
  private void createCode()
  {
    _c.println
      ("#include <stdlib.h>\n"+
       "#include <stdio.h>\n"+
       "#include <unistd.h>\n"+
       "#include <stdint.h>\n"+
       "#include <assert.h>\n"+
       "\n");

    Stream.of(CompilePhase.values()).forEachOrdered
      ((p) ->
       {
         for (var c = _fuir.firstClazz(); c <= _fuir.lastClazz(); c++)
           {
             p.compile(this, c);
           }
       });
    _c.println("int main(int argc, char **args) { " + _names.function(_fuir.mainClazzId(), false) + "(); }");
  }


  /**
   * Push the given value to the stack unless it is of unit or void type or the
   * clazz is -1
   *
   * @param stack the stack to push val to
   *
   * @param cl the clazz of val, may be -1
   *
   * @param the value to push
   */
  void push(Stack<CExpr> stack, int cl, CExpr val)
  {
    if (PRECONDITIONS) require
      (!_types.hasData(cl) || val != null);

    if (_types.hasData(cl))
      {
        stack.push(val);
      }
  }


  /**
   * Pop value from the stack unless it is of unit or void type or the
   * clazz is -1
   *
   * @param stack the stack to pop value from
   *
   * @param cl the clazz of value, may be -1
   *
   * @return the popped value or null if cl is -1 or unit type
   */
  CExpr pop(Stack<CExpr> stack, int cl)
  {
    return _types.hasData(cl) ? stack.pop()
                              : null;
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
    for (int i = 0; _fuir.withinCode(c, i); i = i + _fuir.codeSizeAt(c, i))
      {
        var s = _fuir.codeAt(c, i);
        _c.print(CStmnt.lineComment("Code for statement " + s));
        _c.print(createCode(cl, stack, c, i, s));
        if (SHOW_STACK_AFTER_STMNT) System.out.println("After " + s +" in "+_fuir.clazzAsString(cl)+": "+stack);
      }
  }


  /**
   * Create C code for one statement in a code block c of clazz cl with given
   * stack contents.  Write code to _c.
   *
   * @param cl clazz id
   *
   * @param stack the stack containing the current arguments waiting to be used
   *
   * @param c the code block to compile
   *
   * @param i the index within c
   *
   * @param s the FUIR.ExprKind to compile
   */
  CStmnt createCode(int cl, Stack<CExpr> stack, int c, int i, FUIR.ExprKind s)
  {
    CStmnt o = CStmnt.EMPTY;
    switch (s)
      {
      case AdrToValue:
        { // dereference an outer reference
          if (false) // NYI: Check what AdrToValue is applied to empty stack and why it can be a NOP for the C backend
            {
              var a = stack.pop();
              var v = a;  /* a.deref(); --  NYI: AdrToValue is NOP for now since outer refs as values not supported in C backend yet */
              stack.push(v);
            }
          break;
        }
      case Tag:
        {
          var valuecl = _fuir.tagValueClazz(cl, c, i);  // static clazz of value
          var value   = pop(stack, valuecl);            // value assigned to field
          var newcl   = _fuir.tagNewClazz  (cl, c, i);  // static clazz of assigned field
          int tagNum  = _fuir.clazzChoiceTag(newcl, valuecl);
          var res     = _names.newTemp();
          var tag     = res.field(_names.TAG_NAME);
          var uniyon  = res.field(_names.CHOICE_UNION_NAME);
          var entry   = uniyon.field(_fuir.clazzIsRef(valuecl) ||
                                     _fuir.clazzIsChoiceOfOnlyRefs(newcl) ? _names.CHOICE_REF_ENTRY_NAME
                                                                          : _names.CHOICE_ENTRY_NAME + tagNum);
          if (_fuir.clazzIsChoiceOfOnlyRefs(newcl) && !_fuir.clazzIsRef(valuecl))
            { // replace unit-type values by 0 or an odd value cast to ref Object
              check
                (value == null); // value must be a unit type
              value = CExpr.int32const(tagNum).castTo(_names.struct(_fuir.clazzObject()) + "*");
            }
          o = CStmnt.seq(CStmnt.lineComment("Tag a value to be of choice type " + _fuir.clazzAsString(newcl) + " static value type " + _fuir.clazzAsString(valuecl)),
                         CStmnt.decl(_types.clazz(newcl), res),
                         _fuir.clazzIsChoiceOfOnlyRefs(newcl) ? CStmnt.EMPTY : tag.assign(CExpr.int32const(tagNum)),
                         value == null
                         ? CStmnt.lineComment("valueluess assignment to " + entry.code())
                         : entry.assign(value));
          push(stack, newcl, res);
          break;
        }
      case Assign:
        {
          var field = _fuir.assignedField(cl, c, i);  // field we are assigning to
          if (field != -1)
            {
              var outercl = _fuir.assignOuterClazz(cl, c, i);  // static clazz of outer
              var fclazz  = _fuir.clazzResultClazz(field);     // static clazz of assigned field
              var outer   = pop(stack, outercl);               // instance containing assigned field
              var value   = pop(stack, fclazz);                // value assigned to field
              if (_fuir.clazzIsRef(fclazz))
                {
                  value = value.castTo(_types.clazz(fclazz));
                }
              // _c.print("// Assign to "+_fuir.clazzAsString(fclazz)+" outercl "+_fuir.clazzAsString(outercl));
              o = value == null
                ? CStmnt.lineComment("valueluess assignment to " + outer)
                : accessField(outercl, outer, field).assign(value);
            }
          break;
        }
      case Box:
        {
          var vc = _fuir.boxValueClazz(cl, c, i);
          var rc = _fuir.boxResultClazz(cl, c, i);
          if (_fuir.clazzIsRef(vc))
            { // vc's type is a generic argument whose actual type does not need
              // boxing
              o = CStmnt.lineComment("Box " + _fuir.clazzAsString(vc) + " is NOP, clazz is already a ref");
            }
          else
            {
              var val = pop(stack, vc);
              var t = _names.newTemp();
              o = CStmnt.seq(CStmnt.lineComment("Box " + _fuir.clazzAsString(vc)),
                             CStmnt.decl(_types.clazz(rc), t),
                             t.assign(CExpr.call("malloc", new List<>(new CIdent(_names.struct(rc)).sizeOfType()))),
                             t.deref().field("clazzId").assign(_names.clazzId(rc)),
                             val == null ? CStmnt.EMPTY
                             : t.deref().field(_names.FIELDS_IN_REF_CLAZZ).assign(val));
              push(stack, rc, t);
            }
          break;
        }
      case Call:
        {
          var cc0 = _fuir.callCalledClazz  (cl, c, i);
          var rt = _fuir.clazzResultClazz(cc0);
          if (_fuir.callIsDynamic(cl, c, i))
            {
              _c.println("// Dynamic call to " + _fuir.clazzAsString(cc0));
              var ccs = _fuir.callCalledClazzes(cl, c, i);
              var ac = _fuir.callArgCount(c, i);
              var tc = _fuir.callTargetClazz(cl, c, i);
              var t = _names.newTemp();
              var ti = stack.size() - ac - 1;
              var tt0 = _types.clazz(tc);
              _c.println(tt0 + " " + t.code()+ ";");
              _c.print(t.assign(stack.get(ti).castTo(tt0)));
              stack.set(ti, t);
              var id = t.deref().field("clazzId");
              CExpr res = null;
              if (_types.hasData(rt) &&
                  (!_fuir.withinCode(c, i+1) || _fuir.codeAt(c, i+1) != FUIR.ExprKind.WipeStack))
                {
                  res = _names.newTemp();
                  _c.println(_types.clazzField(cc0) + " " + res.code() + ";");
                }
              if (ccs.length == 0)
                {
                  _c.println("fprintf(stderr,\"*** %s:%d no targets for dynamic call to " + _fuir.clazzAsString(cc0) +
                             " within "+_fuir.clazzAsString(cl)+"\\n\", __FILE__, __LINE__); exit(1);");
                }
              else if (ccs.length > 2)
                {
                  _c.println("switch (" + id.code() + ") {");
                  _c.indent();
                }
              for (var cci = 0; cci < ccs.length; cci += 2)
                {
                  var tt = ccs[cci  ];
                  var cc = ccs[cci+1];
                  var stk = (Stack<CExpr>) stack.clone();
                  var co = call(cl, c, i, cc, stk, _fuir.clazzOuterClazz(cc));
                  var rv = pop(stk, rt);
                  if (rt != _fuir.clazzResultClazz(cc) && _fuir.clazzIsRef(rt)) // NYI: Check why result can be different
                    {
                      rv = rv.castTo(_types.clazz(rt));
                    }
                  var cr = res != null ? res.assign(rv) : CStmnt.EMPTY;
                  var bo = CStmnt.seq(CStmnt.lineComment("Call calls "+ _fuir.clazzAsString(cc) + " target: " + _fuir.clazzAsString(tt) + ":"),
                                      co, cr);
                  if (ccs.length > 2)
                    {
                      _c.println("case " + _names.clazzId(tt).code() + ": {");
                      _c.indent();
                      _c.print(CStmnt.seq(bo, CStmnt.BREAK));
                      _c.unindent();
                      _c.println("}");
                    }
                  else
                    {
                      _c.print(bo);
                    }
                }
              if (ccs.length > 2)
                {
                  _c.println("default: { fprintf(stderr,\"*** %s:%d unhandled dynamic call target %d in call to " + _fuir.clazzAsString(cc0) +
                             " within "+_fuir.clazzAsString(cl)+"\\n\", __FILE__, __LINE__, " + id.code() + "); exit(1); }");
                  _c.unindent();
                  _c.println("}");
                }
              args(cl, c, i, cc0, stack, _fuir.clazzArgCount(cc0), _fuir.clazzOuterClazz(cc0));
              if (res != null)
                {
                  push(stack, rt, res);
                }
            }
          else
            {
              o = call(cl, c, i, cc0, stack, -1);
              if (!_fuir.clazzIsCalled(cc0))
                {
                  o = CStmnt.lineComment("Feature not called: "+_fuir.clazzAsString(cc0));
                }
            }
          if (_fuir.clazzFieldIsAdrOfValue(cc0) && _types.hasData(rt))  // NYI: deref an outer ref to value type. Would be nice to have a separate statement for this
            {
              push(stack, rt, pop(stack, rt).deref());
            }
          break;
        }
      case Current:
        {
          push(stack, cl, current(cl));
          break;
        }
      case boolConst:
        {
          var bc = _fuir.boolConst(c, i);
          stack.push(bc ? _names.FZ_TRUE
                     : _names.FZ_FALSE);
          break;
        }
      case i32Const: { var ic = _fuir.i32Const(c, i); stack.push(CExpr. int32const(ic)); break; }
      case u32Const: { var ic = _fuir.u32Const(c, i); stack.push(CExpr.uint32const(ic)); break; }
      case i64Const: { var ic = _fuir.i64Const(c, i); stack.push(CExpr. int64const(ic)); break; }
      case u64Const: { var ic = _fuir.u64Const(c, i); stack.push(CExpr.uint64const(ic)); break; }
      case strConst:
        {
          var bytes = _fuir.strConst(c, i);
          var tmp = _names.newTemp();
          o = constString(bytes, tmp);
          stack.push(tmp);
          break;
        }
      case Match:
        {
          var subjClazz = _fuir.matchStaticSubject(cl, c, i);
          var sub = pop(stack, subjClazz);
          var uniyon = sub.field(_names.CHOICE_UNION_NAME);
          var stack2 = stack;
          var hasTag = !_fuir.clazzIsChoiceOfOnlyRefs(subjClazz);
          CIdent ref = _names.newTemp();
          if (!hasTag)
            {
              var entry = uniyon.field(_names.CHOICE_REF_ENTRY_NAME).castTo(_types.clazz(_fuir.clazzObject()));
              _c.print(CStmnt.seq(CStmnt.decl(_types.clazz(_fuir.clazzObject()), ref),
                                  ref.assign(entry)));
            }
          var tag = hasTag ? sub.field(_names.TAG_NAME)
                           : ref.castTo("int64_t");
          _c.println("switch ("+tag.code()+") {");
          _c.indent();
          var mcc = _fuir.matchCaseCount(c, i);
          for (var pass = 0; pass < (hasTag ? 1 : 2); pass++)
            { // pass 0: handle tags or unit type values like 'nil', pass 1: handle references
              int refCnt = 0;
              for (var mc = 0; mc < mcc; mc++)
                {
                  String caze = "";
                  var tags = _fuir.matchCaseTags(cl, c, i, mc);
                  for (var tagNum : tags)
                    {
                      var tc = _fuir.clazzChoice(subjClazz, tagNum);
                      if (!hasTag && !_fuir.clazzIsRef(tc) && pass == 0)
                        {
                          caze = caze + "case " + tagNum + ":\n";
                          check
                            (!_types.hasData(tc));
                        }
                      else if (!hasTag && _fuir.clazzIsRef(tc) && pass == 1)
                        {
                          refCnt++;
                          if (refCnt == 1)
                            {
                              caze = "default:\n";
                            }
                          else
                            {
                              _c.print(CExpr.call("assert", new List<>(CExpr.int32const(0))).comment("match of different refs not supported yet!"));
                            }
                        }
                      else if (hasTag)
                        {
                          caze = caze + "case " + tagNum + ":\n";
                        }
                    }
                  if (caze != "")
                    {
                      _c.print(caze);
                      _c.println("{");
                      _c.indent();
                      var field = _fuir.matchCaseField(cl, c, i, mc);
                      if (field != -1)
                        {
                          var fclazz = _fuir.clazzResultClazz(field);     // static clazz of assigned field
                          var f      = accessField(cl, current(cl), field);
                          if (!hasTag && pass == 1)
                            {
                              _c.print(f.assign(ref.castTo(_types.clazz(fclazz))));
                            }
                          else if (hasTag)
                            {
                              var entry  = _fuir.clazzIsRef(fclazz) ? uniyon.field(_names.CHOICE_REF_ENTRY_NAME).castTo(_types.clazz(fclazz))
                                                                    : uniyon.field(_names.CHOICE_ENTRY_NAME + tags[0]);
                              _c.print(!_types.hasData(fclazz) ? CStmnt.lineComment("valueluess assignment to " + f.code())
                                                               : f.assign(entry));
                            }
                        }
                      stack = (Stack<CExpr>) stack2.clone();
                      createCode(cl, stack, _fuir.i32Const(c, i + 1 + mc));
                      _c.println("break;");
                      _c.unindent();
                      _c.println("}");
                    }
                }
            }
          _c.unindent();
          _c.println("}");
          break;
        }
      case WipeStack:
        {
          stack.clear();
          break;
        }
      default:
        {
          Errors.fatal("C backend does not handle statments of type " + s);
        }
      }
    return o;
  }


  /**
   * Create code to create a constant string and assign it to a new temp
   * variable. Return an CExpr that reads this variable.
   */
  CStmnt constString(byte[] bytes, CIdent tmp)
  {
    StringBuilder sb = new StringBuilder();
    for (var bb : bytes)
      {
        var b = bb & 0xff;
        sb.append("\\"+((b >> 6) & 7)+((b >> 3) & 7)+(b & 7));
        sb.append("...");
      }
    return CStmnt.seq(CStmnt.decl("fzT__Rconststring *", tmp),
                      tmp.assign(CExpr.call("malloc", new List<>(new CIdent("fzT__Rconststring").sizeOfType()))),
                      tmp.deref().field("clazzId").assign(_names.clazzId(_fuir.clazz_conststring())),
                      tmp.deref().field(_names.FIELDS_IN_REF_CLAZZ).field("fzF_1_data").assign(CExpr.string(sb.toString()).castTo("void *")),
                      tmp.deref().field(_names.FIELDS_IN_REF_CLAZZ).field("fzF_3_length").assign(CExpr.int32const(bytes.length)));
  }


  /**
   * Create C code for a statically bound call.
   *
   * @param cl clazz id of the currently compiled clazz
   *
   * @param c the code block currently compiled
   *
   * @param i index in c of the current call
   *
   * @param cc clazz that is called
   *
   * @param stack the stack containing the current arguments waiting to be used
   *
   * @param castTarget if the type of the target instance of this call was
   * checked against a different type, the target type should be cast to this
   * clazz castTarget. -1 if no cast needed.
   *
   * @return the code to perform the call
   */
  CStmnt call(int cl, int c, int i, int cc, Stack<CExpr> stack, int castTarget)
  {
    CStmnt result = CStmnt.EMPTY;
    var ac = _fuir.callArgCount(c, i);
    var rt = _fuir.clazzResultClazz(cc);
    switch (_fuir.clazzKind(cc))
      {
      case Abstract :
        Errors.error("Call to abstract feature encountered.",
                     "While creating code for " + _fuir.clazzAsString(cl) + "\n" +
                     "Found call to  " + _fuir.clazzAsString(cc));
      case Routine  :
      case Intrinsic:
        {
          if (SHOW_STACK_ON_CALL) System.out.println("Before call to "+_fuir.clazzAsString(cc)+": "+stack);
          CExpr res = null;
          var call = CExpr.call(_names.function(cc, false), args(cl, c, i, cc, stack, ac, castTarget));
          if (_types.hasData(rt))
            {
              var tmp = _names.newTemp();
              res = tmp;
              result = CStmnt.seq(CStmnt.decl(_types.clazz(rt), tmp),
                                  res.assign(call));
              push(stack, rt, res);
            }
          else
            {
              result = call;
            }
          if (SHOW_STACK_ON_CALL) System.out.println("After call to "+_fuir.clazzAsString(cc)+": "+stack);
          break;
        }
      case Field:
        {
          var tc = _fuir.callTargetClazz(cl, c, i);
          var t = pop(stack, tc);
          check
            (t != null || !_types.hasData(rt) || tc == _fuir.clazzUniverse());
          var occ   = _fuir.clazzOuterClazz(cc);
          var vocc  = _fuir.clazzAsValue(occ);
          if (occ != tc &&
              !_fuir.clazzIsOuterRef(cc) /* NYI: tc for outer ref is wrong, avoid redundant casts */ )
            {
              t = t.castTo(_types.clazz(occ));  // t is a ref with different static type, so cast it to the actual type
            }
          CExpr res = (_types.isScalar(vocc) && _fuir.clazzIsRef(tc) ? t.deref().field("fields")      :
                       _types.isScalar(vocc)                         ? t                              :
                       _types.hasData(rt)                            ? accessField(tc, t, cc)         : null);
          if (_fuir.clazzIsRef(rt) && _fuir.clazzKind(_fuir.clazzAsValue(rt)) == FUIR.ClazzKind.Choice)
            { // NYI: This special handling should better be part of match, but staticSubjectClazz is never ref
              res = res.deref().field(_names.FIELDS_IN_REF_CLAZZ);
              rt = _fuir.clazzAsValue(rt);
            }
          push(stack, rt, res);
          break;
        }
      default:       throw new Error("This should not happen: Unknown feature kind: " + _fuir.clazzKind(cc));
      }
    return result;
  }


  /**
   * Create C code to pass given number of arguments plus one implicit target
   * argument from the stack to a called feature.
   *
   * @param cl clazz id of the currently compiled clazz
   *
   * @param c the code block currently compiled
   *
   * @param i index in c of the current call
   *
   * @param cc clazz that is called
   *
   * @param stack the stack containing the C code of the args.
   *
   * @param argCount the number of arguments.
   *
   * @return list of arguments to be passed to CExpr.call
   */
  List<CExpr> args(int cl, int c, int i, int cc, Stack<CExpr> stack, int argCount, int castTarget)
  {
    List<CExpr> result;
    if (argCount > 0)
      {
        var ac = _fuir.clazzArgClazz(cc, argCount-1);
        var a = pop(stack, ac);
        result = args(cl, c, i, cc, stack, argCount-1, castTarget);
        if (_types.hasData(ac))
          {
            a = _fuir.clazzIsRef(ac) ? a.castTo(_types.clazz(ac)) : a;
            result.add(a);
          }
      }
    else // NYI: special handling of outer refs should not be part of BE, should be moved to FUIR
      { // ref to outer instance, passed by reference
        result = new List<>();
        var tc = _fuir.callTargetClazz(cl, c, i);
        var or = _fuir.clazzOuterRef(cc);
        var a = pop(stack, tc);
        if (or != -1)
          {
            var a2 = _fuir.clazzFieldIsAdrOfValue(or) ? a.adrOf() : a;
            var a3 = castTarget == -1 ? a2 : a2.castTo(_types.clazz(castTarget));
            result.add(a3);
          }
      }
    return result;
  }


  /**
   * Create code for the C function implemeting the routine corresponding to the
   * given clazz.  Write code to _c.
   *
   * @param cl id of clazz to compile
   */
  private void cFunctionDecl(int cl, boolean pre)
  {
    var res = _fuir.clazzResultClazz(cl);
    _c.print(pre || !_types.hasData(res)
             ? "void "
             : _types.clazz(res) + " ");
    _c.print(_names.function(cl, pre));
    _c.print("(");
    String comma = "";
    var or = _fuir.clazzOuterRef(cl);
    if (or != -1)
      {
        _c.print(_types.clazzField(or));
        _c.print(" fzouter");
        comma = ", ";
      }
    var ac = _fuir.clazzArgCount(cl);
    for (int i = 0; i < ac; i++)
      {
        var at = _fuir.clazzArgClazz(cl, i);
        if (_types.hasData(at))
          {
            _c.print(comma);
            var t = _types.clazz(at);
            _c.print(t + " arg" + i);
            comma = ", ";
          }
      }
    _c.print(")");
  }


  /**
   * Create forward declarations for given clazz cl.
   *
   * @param cl id of clazz to compile
   */
  public void forwards(int cl)
  {
    switch (_fuir.clazzKind(cl))
      {
      case Routine:
      case Intrinsic:
        {
          cFunctionDecl(cl, false);
          _c.print(";\n");
        } /* fall through */
      case Field:
      case Abstract:
        {
          if (_fuir.clazzPre(cl, 0) != -1)
            {
              cFunctionDecl(cl, true);
              _c.print(";\n");
            }
          break;
        }
      }
  }


  /**
   * Create code for given clazz cl.
   *
   * @param cl id of clazz to compile
   */
  public void code(int cl)
  {
    _names._tempVarId = 0;  // reset counter for unique temp variables for function results
    switch (_fuir.clazzKind(cl))
      {
      case Routine:
      case Intrinsic:
        {
          _c.print("\n// code for clazz#"+_names.clazzId(cl).code()+" "+_fuir.clazzAsString(cl)+":\n");
          cFunctionDecl(cl, false);
          _c.print(" {\n");
          _c.indent();
          switch (_fuir.clazzKind(cl))
            {
            case Routine  : codeForRoutine(cl); break;
            case Intrinsic: _c.print(new Intrinsics().code(this, cl)); break;
            }
          _c.unindent();
          _c.println("}");
          break;
        }
      }
  }


  /**
   * Create code for given clazz cl of type Routine.
   *
   * @param cl id of clazz to generate code for
   */
  void codeForRoutine(int cl)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzKind(cl) == FUIR.ClazzKind.Routine);

    _c.print("" + _names.struct(cl) + " *" + _names.CURRENT.code() + " = malloc(sizeof(" + _names.struct(cl) + "));\n"+
             (_fuir.clazzIsRef(cl) ? _names.CURRENT.deref().field("clazzId").assign(_names.clazzId(cl)).code() + ";\n" : ""));

    var cur = _fuir.clazzIsRef(cl) ? _names.CURRENT.deref().field(_names.FIELDS_IN_REF_CLAZZ)
                                   : _names.CURRENT.deref();
    var vcl = _fuir.clazzAsValue(cl);
    var or = _fuir.clazzOuterRef(vcl);
    if (or != -1)
      {
        _c.print(cur.field(_names.fieldName(or)).assign(_names.OUTER));
      }

    var ac = _fuir.clazzArgCount(vcl);
    for (int i = 0; i < ac; i++)
      {
        var af = _fuir.clazzArg(vcl, i);
        var at = _fuir.clazzArgClazz(vcl, i);
        if (_types.hasData(at))
          {
            var target = _types.isScalar(vcl)
              ? cur
              : cur.field(_names.fieldName(af));
            _c.print(target.assign(new CIdent("arg" + i)));
          }
      }
    var c = _fuir.clazzCode(cl);
    var stack = new Stack<CExpr>();
    try
      {
        createCode(cl, stack, c);
      }
    catch (RuntimeException | Error e)
      {
        _c.println("// *** compiler crash: " + e);
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        Errors.error("C backend compiler crash",
                     "While creating code for " + _fuir.clazzAsString(cl) + "\n" +
                     "Java Error: " + sw);
      }
    var res = _fuir.clazzResultClazz(cl);
    if (_types.hasData(res))
      {
        var rf = _fuir.clazzResultField(cl);
        _c.print(rf != -1
                 ? current(cl).field(_names.fieldName(rf)).ret()  // a routine, return result field
                 : current(cl).ret()                              // a constructor, return current instance
                 );
      }
  }


  /**
   * Return the current instance of the currently compiled clazz cl. This is a C
   * pointer in case _fuir.clazzIsRef(cl), or the C struct corresponding to cl
   * otherwise.
   */
  CExpr current(int cl)
  {
    return _fuir.clazzIsRef(cl)
      ? _names.CURRENT
      : _names.CURRENT.deref();
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
  CExpr accessField(int outercl, CExpr outer, int field)
  {
    if (outercl == _fuir.clazzUniverse())
      {
        outer = _names.UNIVERSE;
      }
    if (_fuir.clazzIsRef(outercl))
      {
        outer = outer.deref().field(_names.FIELDS_IN_REF_CLAZZ);
      }
    return outer.field(_names.fieldName(field));
  }

}

/* end of file */
