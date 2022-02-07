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

import java.util.Stack;

import java.util.stream.Stream;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;


/**
 * C provides a C code backend converting FUIR data into C code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class C extends ANY
{


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
   * C identifier handling goes through _names:
   */
  final CNames _names;


  /**
   * C types handling goes through _types:
   */
  final CTypes _types;


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

    var command = new List<String>("clang", "-O3", "-o", name, cname);
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
      ("#include <stdlib.h>\n"+
       "#include <stdio.h>\n"+
       "#include <unistd.h>\n"+
       "#include <stdint.h>\n"+
       "#include <assert.h>\n"+
       "\n");

    var ordered = _types.inOrder();
    Stream.of(CompilePhase.values()).forEachOrdered
      ((p) ->
       {
         for (var c : ordered)
           {
             cf.print(p.compile(this, c));
           }
         cf.println("");
       });
    cf.println("int main(int argc, char **args) { " + _names.function(_fuir.mainClazzId(), false) + "(); }");
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
      (!_types.hasData(cl)                               || val != null && val != CExpr.UNIT,
        _types.hasData(cl) ||  _fuir.clazzIsVoidType(cl) ||                val == CExpr.UNIT,
                              !_fuir.clazzIsVoidType(cl) || val == null,
       !containsVoid(stack));

    if (_types.hasData(cl) || _fuir.clazzIsVoidType(cl))
      {
        stack.push(val);
      }

    if (POSTCONDITIONS) ensure
      (!_types.hasData(cl) || stack.get(stack.size()-1) == val,
       !_fuir.clazzIsVoidType(cl) || containsVoid(stack));
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
    if (PRECONDITIONS) require
      (!_types.hasData(cl) || stack.size() > 0,
       !containsVoid(stack));

    return _types.hasData(cl) ? stack.pop() : CExpr.UNIT;
  }


  /**
   * Check if the given stack contains a void value.  If so, code generation has
   * to stop immediately.
   */
  boolean containsVoid(Stack<CExpr> stack)
  {
    return stack.size() > 0 && stack.get(stack.size()-1) == null;
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
   *
   * @return the statements created for c's code
   */
  CStmnt createCode(int cl, Stack<CExpr> stack, int c)
  {
    var l = new List<CStmnt>();
    for (int i = 0; !containsVoid(stack) && _fuir.withinCode(c, i); i = i + _fuir.codeSizeAt(c, i))
      {
        var s = _fuir.codeAt(c, i);
        l.add(CStmnt.lineComment("Code for statement " + s));
        l.add(createCode(cl, stack, c, i, s));
        if (SHOW_STACK_AFTER_STMNT) System.out.println("After " + s +" in "+_fuir.clazzAsString(cl)+": "+stack);
      }
    return CStmnt.seq(l);
  }


  /**
   * Create code to access (call or write) a feature.
   *
   * @param cl clazz id
   *
   * @param stack the stack containing the target and arguments for the access
   *
   * @param c the code block to compile
   *
   * @param i index of the access statement, must be ExprKind.Assign or ExprKind.Call
   *
   * @return statement to perform the given access
   */
  CStmnt access(int cl, Stack<CExpr> stack, int c, int i)
  {
    CStmnt result;
    CExpr res = null;
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
        var stackWithArgs = (Stack<CExpr>) stack.clone();
        args(-1, cc0, stack, _fuir.clazzArgCount(cc0));  // pop all args except target
        var target = pop(stack, tc);
        var tvar = _names.newTemp();
        var tt0 = _types.clazz(tc);
        ol.add(CStmnt.decl(tt0, tvar, target.castTo(tt0)));
        stackWithArgs.set(stack.size(), tvar);
        if (isCall && _types.hasData(rt) && (!_fuir.withinCode(c, i+1) || _fuir.codeAt(c, i+1) != FUIR.ExprKind.Pop))
          {
            var resvar = _names.newTemp();
            res = resvar;
            ol.add(CStmnt.decl(_types.clazzField(cc0), resvar));
          }
        if (ccs.length == 0)
          {
            ol.add(CStmnt.seq(CExpr.fprintfstderr("*** %s:%d no targets for dynamic access of %s within %s\n",
                                                  CIdent.FILE,
                                                  CIdent.LINE,
                                                  CExpr.string(_fuir.clazzAsString(cc0)),
                                                  CExpr.string(_fuir.clazzAsString(cl ))),
                              CExpr.exit(1)));
          }
        var cazes = new List<CStmnt>();
        CStmnt acc = CStmnt.EMPTY;
        for (var cci = 0; cci < ccs.length; cci += 2)
          {
            var tt = ccs[cci  ];
            var cc = ccs[cci+1];
            var stk = (Stack<CExpr>) stackWithArgs.clone();
            if (isCall)
              {
                var cal = call(tc, cc, stk, false);
                var as = CStmnt.EMPTY;
                if (!containsVoid(stk))
                  {
                    var rti = _fuir.clazzResultClazz(cc);
                    var rv = pop(stk, rti);
                    if (rv != null && res != null)
                      {
                        if (rt != rti && _fuir.clazzIsRef(rt)) // NYI: Check why result can be different
                          {
                            rv = rv.castTo(_types.clazz(rt));
                          }
                        as = assign(res, rv, rt);
                      }
                  }
                acc = CStmnt.seq(CStmnt.lineComment("Call calls "+ _fuir.clazzAsString(cc) + " target: " + _fuir.clazzAsString(tt) + ":"),
                                 cal,
                                 as);
              }
            else
              {
                acc = assignField(stk, tc, tt, cc);
              }
            cazes.add(CStmnt.caze(new List<>(_names.clazzId(tt)),
                                  CStmnt.seq(acc, CStmnt.BREAK)));
          }
        if (ccs.length > 2)
          {
            var id = tvar.deref().field(_names.CLAZZ_ID);
            acc = CStmnt.suitch(id, cazes,
                                CStmnt.seq(CExpr.fprintfstderr("*** %s:%d unhandled dynamic target %d in access of %s within %s\n",
                                                               CIdent.FILE, CIdent.LINE,
                                                               id,
                                                               CExpr.string(_fuir.clazzAsString(cc0)),
                                                               CExpr.string(_fuir.clazzAsString(cl ))),
                                           CExpr.exit(1)));
          }
        ol.add(acc);
        result = CStmnt.seq(ol);
      }
    else if (_fuir.clazzNeedsCode(cc0))
      {
        if (isCall)
          {
            result = call(tc, cc0, stack, false);
            res = containsVoid(stack) ? null : pop(stack, rt);
          }
        else
          {
            result = assignField(stack, tc, tc, cc0);
          }
      }
    else
      {
        result = CStmnt.seq(CExpr.fprintfstderr("*** %s:%d no code generated for static access to %s within %s\n",
                                                CIdent.FILE,
                                                CIdent.LINE,
                                                CExpr.string(_fuir.clazzAsString(cc0)),
                                                CExpr.string(_fuir.clazzAsString(cl ))),
                            CExpr.exit(1));
        stack.push(null);  // push void, i.e., stop code generation here
      }
    if (isCall && (res != null || _fuir.clazzIsVoidType(rt) && !containsVoid(stack)))
      {
        var rres = _types.hasData(rt) && _fuir.clazzFieldIsAdrOfValue(cc0) ? res.deref() : res; // NYI: deref an outer ref to value type. Would be nice to have a separate statement for this
        push(stack, rt, rres);
      }
    return result;
  }


  /**
   * Create C code for one statement in a code block c of clazz cl with given
   * stack contents.
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
   *
   * @return the C code
   */
  CStmnt createCode(int cl, Stack<CExpr> stack, int c, int i, FUIR.ExprKind s)
  {
    CStmnt o = CStmnt.EMPTY;
    switch (s)
      {
      case AdrOf:
        {
          stack.push(stack.pop().adrOf());
          break;
        }
      case Assign:
        {
          if (_fuir.accessedClazz(cl, c, i) != -1)  // field we are assigning to may be unused, i.e., -1
            {
              o = access(cl, stack, c, i);
            }
          break;
        }
      case Box:
        {
          var vc = _fuir.boxValueClazz(cl, c, i);
          var rc = _fuir.boxResultClazz(cl, c, i);
          if (_fuir.clazzIsRef(vc) || !_fuir.clazzIsRef(rc))
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
                             t.assign(CExpr.call("malloc", new List<>(CExpr.sizeOfType(_names.struct(rc))))),
                             t.deref().field(_names.CLAZZ_ID).assign(_names.clazzId(rc)),
                             assign(fields(t, rc), val, vc));
              push(stack, rc, t);
            }
          break;
        }
      case Unbox:
        {
          var orc = _fuir.unboxOuterRefClazz(cl, c, i);
          var vc = _fuir.unboxResultClazz(cl, c, i);
          if (_fuir.clazzIsRef(orc) && !_fuir.clazzIsRef(vc))
            {
              var refval = pop(stack, orc);
              push(stack, vc, fields(refval, orc));
            }
          break;
        }
      case Call:
        {
          var tc = _fuir.accessTargetClazz(cl, c, i);
          var cc0 = _fuir.accessedClazz  (cl, c, i);
          var ol = new List<CStmnt>();
          if (_fuir.clazzContract(cc0, FUIR.ContractKind.Pre, 0) != -1)
            {
              ol.add(call(tc, cc0, (Stack<CExpr>) stack.clone(), true));
            }
          if (!_fuir.callPreconditionOnly(cl, c, i))
            {
              ol.add(access(cl, stack, c, i));
            }
          o = CStmnt.seq(ol);
          break;
        }
      case Comment:
        {
          o = CStmnt.lineComment(_fuir.comment(cl, c, i));
          break;
        }
      case Current:
        {
          push(stack, cl, current(cl));
          break;
        }
      case Outer:
        {
          push(stack, cl, _names.OUTER);
          break;
        }
      case Const:
        {
          var constCl = _fuir.constClazz(c, i);
          var d = _fuir.constData(c, i);
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
          push(stack, constCl, r);
          break;
        }
      case Match:
        {
          var subjClazz = _fuir.matchStaticSubject(cl, c, i);
          var sub       = fields(pop(stack, subjClazz), subjClazz);
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
                  var f      = field(cl, current(cl), field);
                  var entry  = _fuir.clazzIsRef(fclazz) ? ref.castTo(_types.clazz(fclazz)) :
                               _types.hasData(fclazz)   ? uniyon.field(new CIdent(_names.CHOICE_ENTRY_NAME + tags[0]))
                                                        : CExpr.UNIT;
                  sl.add(assign(f, entry, fclazz));
                }
              sl.add(createCode(cl, (Stack<CExpr>) stack.clone(), _fuir.matchCaseCode(c, i, mc)));
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
              var notFound = CStmnt.seq(CExpr.fprintfstderr("*** %s:%d Unexpected reference type %d found in match\n", CIdent.FILE, CIdent.LINE, id),
                                        CExpr.exit(1));
              tdefault = CStmnt.suitch(id, rcases, notFound);
            }
          o = CStmnt.seq(getRef, CStmnt.suitch(tag, tcases, tdefault));
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
          o = CStmnt.seq(CStmnt.lineComment("Tag a value to be of choice type " + _fuir.clazzAsString(newcl) + " static value type " + _fuir.clazzAsString(valuecl)),
                         CStmnt.decl(_types.clazz(newcl), res),
                         _fuir.clazzIsChoiceOfOnlyRefs(newcl) ? CStmnt.EMPTY : tag.assign(CExpr.int32const(tagNum)),
                         assign(entry, value, valuecl));
          push(stack, newcl, res);
          break;
        }
      case Dup:
        {
          var v = stack.pop();
          stack.push(v);
          stack.push(v);
          break;
        }
      case Pop:
        { // Handled within Call
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
   * Create code to assign a value of given type to a field. In case value is of
   * unit type, this will produce no code, i.e., any possible side-effect of
   * target and value will be lost.
   */
  CStmnt assign(CExpr target, CExpr value, int type)
  {
    if (PRECONDITIONS) require
      (_types.hasData(type) == (value != CExpr.UNIT));

    return _types.hasData(type)
      ? target.assign(value)
      : CStmnt.lineComment("unit type assignment to " + target.code());
  }


  /**
   * Create code to create a constant string and assign it to a new temp
   * variable. Return an CExpr that reads this variable.
   */
  CStmnt constString(byte[] bytes, CIdent tmp)
  {
    var internalArray = _names.fieldName(_fuir.clazz_conststring_internalArray());
    var data          = _names.fieldName(_fuir.clazz_sysArray_u8_data());
    var length        = _names.fieldName(_fuir.clazz_sysArray_u8_length());
    var sysArray = fields(tmp, _fuir.clazz_conststring()).field(internalArray);
    return CStmnt.seq(CStmnt.decl("fzT__R1conststring *", tmp),
                      tmp.assign(CExpr.call("malloc", new List<>(CExpr.sizeOfType("fzT__R1conststring")))),
                      tmp.deref().field(_names.CLAZZ_ID).assign(_names.clazzId(_fuir.clazz_conststring())),
                      sysArray.field(data  ).assign(CExpr.string(bytes).castTo("void *")),
                      sysArray.field(length).assign(CExpr.int32const(bytes.length)));
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
  CStmnt assignField(Stack<CExpr> stack, int tc, int tt, int f)
  {
    var af = accessField(stack, tc, f);
    var rt = _fuir.clazzResultClazz(f);
    var value = pop(stack, rt);
    if (_fuir.clazzIsRef(rt))
      {
        value = value.castTo(_types.clazz(rt));
      }
    return assign(af, value, rt);
  }


  /**
   * Create code to access a field
   *
   * @param stack the stack containing the value and the target instance
   *
   * @param tc the static target clazz
   *
   * @param f the field
   *
   * @return the code to access field f, null if type is 'void', CExpr.UNIT if
   * type is 'unit'.
   */
  CExpr accessField(Stack<CExpr> stack, int tc, int f)
  {
    var rt = _fuir.clazzResultClazz(f);
    var t = pop(stack, tc);
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
   * @param tc clazz id of the outer clazz of the called clazz
   *
   * @param cc clazz that is called
   *
   * @param stack the stack containing the current arguments waiting to be used
   *
   * @param pre true to call the precondition of cl instead of cl.
   *
   * @return the code to perform the call
   */
  CStmnt call(int tc, int cc, Stack<CExpr> stack, boolean pre)
  {
    CStmnt result = CStmnt.EMPTY;
    var rt = _fuir.clazzResultClazz(cc);
    switch (pre ? FUIR.FeatureKind.Routine : _fuir.clazzKind(cc))
      {
      case Abstract :
        Errors.error("Call to abstract feature encountered.",
                     "Found call to  " + _fuir.clazzAsString(cc));
      case Routine  :
      case Intrinsic:
        {
          if (SHOW_STACK_ON_CALL) System.out.println("Before call to "+_fuir.clazzAsString(cc)+": "+stack);
          var a = args(tc, cc, stack, _fuir.clazzArgCount(cc));
          if (_fuir.clazzNeedsCode(cc))
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
                  push(stack, rt, res);
                }
            }
          if (SHOW_STACK_ON_CALL) System.out.println("After call to "+_fuir.clazzAsString(cc)+": "+stack);
          break;
        }
      case Field:
        {
          push(stack, rt, accessField(stack, tc, cc));
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
  List<CExpr> args(int tc, int cc, Stack<CExpr> stack, int argCount)
  {
    if (argCount > 0)
      {
        var ac = _fuir.clazzArgClazz(cc, argCount-1);
        var a = pop(stack, ac);
        var result = args(tc, cc, stack, argCount-1);
        if (_types.hasData(ac))
          {
            a = _fuir.clazzIsRef(ac) ? a.castTo(_types.clazz(ac)) : a;
            result.add(a);
          }
        return result;
      }
    else if (tc != -1)
      { // ref to outer instance, passed by reference
        var or = _fuir.clazzOuterRef(cc);   // NYI: special handling of outer refs should not be part of BE, should be moved to FUIR
        var a = tc == _fuir.clazzUniverse() ? _names.UNIVERSE : pop(stack, tc);
        if (or != -1)
          {
            var a2 = _fuir.clazzFieldIsAdrOfValue(or) ? a.adrOf() : a;
            var rc = _fuir.clazzResultClazz(or);
            var a3 = tc != rc ? a2.castTo(_types.clazzField(or)) : a2;
            return new List<>(a3);
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
    var args = new List<String>();
    var or = _fuir.clazzOuterRef(cl);
    if (or != -1)
      {
        args.add(_types.clazzField(or));
        args.add("fzouter");
      }
    var ac = _fuir.clazzArgCount(cl);
    for (int i = 0; i < ac; i++)
      {
        var at = _fuir.clazzArgClazz(cl, i);
        if (_types.hasData(at))
          {
            args.add(_types.clazz(at));
            args.add("arg" + i);
          }
      }
    return CStmnt.functionDecl(resultType, new CIdent(_names.function(cl, pre)), args, body);
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
                                                     : new Intrinsics().code(this, cl);
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
    var l = new List<CStmnt>();
    var t = _names.struct(cl);
    l.add(CStmnt.decl(t + "*", _names.CURRENT, CExpr.call("malloc", new List<>(CExpr.sizeOfType(t)))));
    l.add(_fuir.clazzIsRef(cl) ? _names.CURRENT.deref().field(_names.CLAZZ_ID).assign(_names.clazzId(cl)) : CStmnt.EMPTY);

    var cur = _fuir.clazzIsRef(cl) ? fields(_names.CURRENT, cl)
                                   : _names.CURRENT.deref();
    var vcl = _fuir.clazzAsValue(cl);
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
            l.add(assign(target, new CIdent("arg" + i), at));
          }
      }
    if (pre)
      {
        l.add(preOrPostCondition(cl, FUIR.ContractKind.Pre));
      }
    else
      {
        l.add(createCode(cl, new Stack<CExpr>(), _fuir.clazzCode(cl)));
        l.add(preOrPostCondition(cl, FUIR.ContractKind.Post));
      }
    var res = _fuir.clazzResultClazz(cl);
    if (!pre && _types.hasData(res))
      {
        var rf = _fuir.clazzResultField(cl);
        l.add(rf != -1 ? current(cl).field(_names.fieldName(rf)).ret()  // a routine, return result field
                       : current(cl).ret()                              // a constructor, return current instance
              );
      }
    return CStmnt.seq(l);
  }


  /**
   * Create C statements to execute the pre- or postcondition of the given
   * clazz.
   *
   * @param cl clazz id
   *
   * @param pre true for pre-condition, false for post-condition.
   *
   * @return the C code
   */
  CStmnt preOrPostCondition(int cl, FUIR.ContractKind ck)
  {
    var l = new List<CStmnt>();
    var stack = new Stack<CExpr>();
    for (int p, i = 0;
         !containsVoid(stack) && (p = _fuir.clazzContract(cl, ck, i)) != -1;
         i++)
      {
        l.add(createCode(cl, stack, p));
        if (!containsVoid(stack))
          {
            var cc = stack.pop();
            l.add(CStmnt.iff(cc.field(_names.TAG_NAME).not(),
                             CStmnt.seq(CExpr.fprintfstderr("*** failed " + ck + " on call to '%s'\n",
                                                            CExpr.string(_fuir.clazzAsString(cl))),
                                        CExpr.exit(1))));
          }
      }
    return CStmnt.seq(l);
  }


  /**
   * Return the current instance of the currently compiled clazz cl. This is a C
   * pointer in case _fuir.clazzIsRef(cl), or the C struct corresponding to cl
   * otherwise.
   */
  CExpr current(int cl)
  {
    return
      !_types.hasData(cl) ? CExpr.UNIT :
      _fuir.clazzIsRef(cl) ? _names.CURRENT
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

}

/* end of file */
