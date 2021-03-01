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

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Stack;
import java.util.TreeSet;

import dev.flang.ir.Backend;
import dev.flang.ir.BackendCallable;
import dev.flang.ir.Clazz;   // NYI: remove this dependency!
import dev.flang.ir.Clazzes; // NYI: remove this dependency!

import dev.flang.fuir.FUIR;

import dev.flang.util.Errors;
import dev.flang.util.FuzionOptions;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;


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
  private int _tempVarId = 0;


  /**
   * Set of clazz ids for all the clazzes whose structs have been declared
   * already.  Structs are declared recursively with structs of inner fields
   * declared before outer structs.  This set keeps track which structs have been
   * declared already to avoid duplicates.
   */
  private final TreeSet<Integer> _declaredStructs = new TreeSet<>();


  /**
   * Mapping from clazz ids to C function names
   */
  private final CClazzNames _functionNames = new CClazzNames()
    {
      String prefix(int cl)
      {
        return C_FUNCTION_PREFIX;
      }
    };


  /**
   * Mapping from clazz ids to C struct names
   */
  private final CClazzNames _structNames = new CClazzNames()
    {
      String prefix(int cl)
      {
        return _fuir.clazzIsRef(cl) ? REF_TYPE_PREFIX : VAL_TYPE_PREFIX;
      }
    };


  /**
   * Generator and cache for C names created from clazzes.
   */
  abstract class CClazzNames
  {

    /**
     * The cache mapping clazz num to C names
     */
    private final ArrayList<String> _cache = new ArrayList<>();


    /**
     * prefix to be used for given clazz
     *
     * @param cl a clazz id
     *
     * @return the prefix string to be used.
     */
    abstract String prefix(int cl);


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
    private void clazzMangledName(int cl, StringBuilder sb)
    {
      var o = _fuir.clazzOuterClazz(cl);
      if (o != -1 &&
          _fuir.clazzOuterClazz(o) != -1)
        { // add o a prefix unless cl or o are universe
          clazzMangledName(o, sb);
          sb.append(_fuir.clazzIsRef(cl) ? "_R" : "__");
        }
      else if (_fuir.clazzIsRef(cl))
        {
          sb.append("_R");
        }
      var n = _fuir.clazzArgCount(cl);
      if (n > 0)
        {
          sb.append(n);  // put arg count before the name since name never starts with a digit
        }
      sb.append(mangle(_fuir.clazzBaseName(cl)));
    }


    /**
     * Create unique mangled name for a clazz that can be used in C identifiers
     * (i.e., it starts with letter or '_' and contains only ASCII letters,
     * digits or '_'.
     *
     * @param cl id of a clazz
     *
     * @return the corresponding clazz
     */
    String get(int cl)
    {
      int num = clazzId2num(cl);
      _cache.ensureCapacity(num + 1);
      while (_cache.size() <= num)  // why is there no ArrayList.setSize?
        {
          _cache.add(null);
        }
      var res = _cache.get(num);
      if (res == null)
        {
          var p = prefix(cl);
          var sb = new StringBuilder(p);
          clazzMangledName(cl, sb);
          // NYI: there might be name conflicts due to different generic instances, so
          // we need to add the clazz id or the actual generics if this is the case:
          //
          //   sb.append("_").append(clazzId2num(cl)).append("_");
          res = sb.toString();

          if (res.length() > MAX_C99_IDENTIFIER_LENGTH)
            {
              var s = p + "_L" + num;
              res = s +
                res.substring(p.length(), p.length() + 10) + "__" +
                res.substring(res.length() - MAX_C99_IDENTIFIER_LENGTH + s.length() + 12);
              check
                (res.length() == MAX_C99_IDENTIFIER_LENGTH);
            }
          _cache.set(num, res);
        }

      return res;
    }
  }


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
   * @param dynamic true if this sets the static inner / outer clazz of a
   * dynamic call, false if this is a static call
   *
   * @param innerClazz the frame clazz of the called feature
   *
   * @param outerClazz the static clazz of the target instance of this call
   *
   * @return a beckend-specific object.
   */
  public BackendCallable callable(boolean dynamic,
                                  Clazz innerClazz,
                                  Clazz outerClazz)
  {
    return new BackendCallable()
      {
        public Clazz inner() { return innerClazz; }
        public Clazz outer() { return outerClazz; }
      };
  }


  /**
   * Create the C code from the intermediate code.
   */
  public void compile()
  {
    var cl = _fuir.mainClazzId();
    var name = _fuir.clazzBaseName(cl);
    var cname = name + ".c";
    System.out.println(" + " + cname);
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
    System.out.println(" * " + command.toString("", " ", ""));
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
       "\n" +
       (true ? "" :
        "void * " + DUMMY + "0;\n"+  // NYI: eventually remove this dummy
        "#define " + DUMMY + " (*" + DUMMY + "0)\n"));
    for (var c = _fuir.firstClazz(); c <= _fuir.lastClazz(); c++)
      {
        typesForClazz(c);
      }
    _c.println("");
    for (var c = _fuir.firstClazz(); c <= _fuir.lastClazz(); c++)
      {
        if (!_fuir.clazzIsVoidType(c))
          {
            structsForClazz(c);
          }
      }
    for (var c = _fuir.firstClazz(); c <= _fuir.lastClazz(); c++)
      {
        if (!_fuir.clazzIsVoidType(c))
          {
            compileClazz(c, CompilePhase.FORWARDS);
          }
      }
    for (var c = _fuir.firstClazz(); c <= _fuir.lastClazz(); c++)
      {
        if (!_fuir.clazzIsVoidType(c))
          {
            compileClazz(c, CompilePhase.IMPLEMENTATIONS);
          }
      }
    _c.println("int main(int argc, char **args) { " + _functionNames.get(_fuir.mainClazzId()) + "(); }");
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
        else if (',' == cp) { sb.append("k"); }
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
        else if ('(' == cp) { sb.append("C"); }
        else if (')' == cp) { sb.append("D"); }
        else if ('?' == cp) { sb.append("Q"); }
        else if ('$' == cp) { sb.append("S"); }
        else if ('%' == cp) { sb.append("P"); }
        else if ('°' == cp) { sb.append("O"); }
        else if ('#' == cp) { sb.append("H"); alphaMode = true; }
        // escapes that are used for other purposes:
        //
        // "__" for '.'
        // "_R" for ref
        // "_L" for long identifiers
        else
          {
            sb.append("U").append(Integer.toHexString(cp)).append("_");
          }
      }
    return sb.toString();
  }


  /**
   * Create a name for a new local temp variable.
   */
  String newTemp()
  {
    return TEMP_VAR_PREFIX + (_tempVarId++);
  }


  /**
   * Get the name of a field
   *
   * @param index the index of the field, needed to disambiguate fields
   * with equal name.
   *
   * @param field the field id
   */
  String fieldName(int index, int fieldClazz)
  {
    return FIELD_PREFIX + index + "_" + mangle(_fuir.clazzBaseName(fieldClazz));
  }


  /**
   * Get the name of a field
   *
   * @param index the index of the field, needed to disambiguate fields
   * with equal name.
   *
   * @param field the field id
   */
  String fieldName2(int index, int field)
  {
    return FIELD_PREFIX + index + "_" + mangle(_fuir.clazzBaseName(field));
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
    int index = _fuir.clazzFieldIndex(cl, field);
    return fieldName2(index, field);
  }


  /**
   * NYI: Documentation, just discard the sign?
   */
  int clazzId2num(int cl)
  {
    return cl & 0xFFFffff; // NYI: give a name to this constant
  }


  /**
   * The type of a value of the given clazz.
   */
  String clazzTypeName(int cl)
  {
    return _structNames.get(cl) + (_fuir.clazzIsRef(cl) ? "*" : "");
  }


  /**
   * Check if cl is passed as a value iff used as the type of an outer ref.
   *
   * NYI: special handling of outer refs should not be part of BE, should be moved to FUIR
   */
  boolean outerClazzPassedAsAdrOfValue(int cl)
  {
    return
      !_fuir.clazzIsRef(cl) &&
      !_fuir.clazzIsI32(cl) &&
      !_fuir.clazzIsI64(cl) &&
      !_fuir.clazzIsU32(cl) &&
      !_fuir.clazzIsU64(cl);
  }


  /**
   * The type of an outer field of the given clazz.
   *
   * NYI: special handling of outer refs should not be part of BE, should be moved to FUIR
   */
  String clazzTypeNameOuterField(int cl)
  {
    return clazzTypeName(cl) + (outerClazzPassedAsAdrOfValue(cl) ? "*" : "");
  }


  /**
   * Create declarations of the C types required for the given clazz.  Write
   * code to _c.
   *
   * @param cl a clazz id.
   */
  public void typesForClazz(int cl)
  {
    switch (_fuir.clazzKind(cl))
      {
      case Routine:
        {
          var name = _structNames.get(cl);
          // special handling of stdlib clazzes known to the compiler
          String type =
            _fuir.clazzIsI32(cl) ? "int32_t" :
            _fuir.clazzIsI64(cl) ? "int64_t" :
            _fuir.clazzIsU32(cl) ? "uint32_t" :
            _fuir.clazzIsU64(cl) ? "uint64_t" : "struct " + name;
          _c.print
                ("typedef " + type + " " + name + ";\n");
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
    switch (_fuir.clazzKind(cl))
      {
      case Routine:
        {
          if (!_declaredStructs.contains(cl))
            {
              _declaredStructs.add(cl);
              if (_fuir.clazzIsI32(cl) ||
                  _fuir.clazzIsI64(cl) ||
                  _fuir.clazzIsU32(cl) ||
                  _fuir.clazzIsU64(cl)    )
                { // special handling of stdlib clazzes known to the compiler
                }
              else
                {
                  // first, make sure structs used for inner fields are declared:
                  for (int i = 0; i < _fuir.clazzNumFields(cl); i++)
                    {
                      var cf = _fuir.clazzField(cl, i);
                      var rcl = _fuir.clazzResultClazz(cf);
                      if (!_fuir.clazzIsRef(rcl))
                        {
                          structsForClazz(rcl);
                        }
                    }

                  // next, declare the struct itself
                  _c.print
                    ("// for " + _fuir.clazzAsString(cl) + "\n" +
                     "struct " + _structNames.get(cl) + " {\n" +
                     (_fuir.clazzIsRef(cl) ? "  uint32_t clazzId;\n" : ""));
                  for (int i = 0; i < _fuir.clazzNumFields(cl); i++)
                    {
                      var cf = _fuir.clazzField(cl, i);
                      String type = _fuir.clazzFieldIsAdrOfValue(cf)
                        ? clazzTypeNameOuterField(_fuir.clazzOuterClazz(cl))
                        : clazzTypeName(_fuir.clazzResultClazz(cf));
                      _c.print(" " + type + " " + fieldName2(i, cf) + ";\n");
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
              if (false) // NYI: Check what AdrToValue is applied to empty stack and why it can be a NOP for the C backend
                {
                  var a = stack.pop();
                  var v = a;  /* a.deref(); --  NYI: AdrToValue is NOP for now since outer refs as values not supported in C backend yet */
                  stack.push(v);
                }
              break;
            }
          case Assign:
            {
              var field = _fuir.assignedField(cl, c, i);  // field we are assigning to
              if (field != -1)
                {
                  var outer = stack.pop();                // instance containing assigned field
                  var outercl = _fuir.assignOuterClazz(cl, c, i);  // static clazz of outer
                  var valuecl = _fuir.assignValueClazz(cl, c, i);  // static clazz of value
                  var fclazz = _fuir.clazzResultClazz(field);  // static clazz of assigned field
                  var fieldName = fieldNameInClazz(outercl, field);
                  var fieldAccess = ccodeAccessField(outercl, outer, fieldName);
                  if (_fuir.clazzIsBool(fclazz) &&
                      valuecl != -1 &&
                      fclazz != valuecl  // NYI: interpreter checks fclazz._type != staticTypeOfValue
                      )
                    { // bool is a basically a choice type, but we do not use the tag in the generated C code
                      check(_fuir.clazzIsTRUE (valuecl) ||
                            _fuir.clazzIsFALSE(valuecl));
                      var value = CExpr.uint32const(_fuir.clazzIsTRUE(valuecl) ? 1 : 0);
                      fieldAccess = fieldAccess.field(BOOL_TAG_NAME);
                      _c.printExpr(fieldAccess.assign(value)); _c.println(";  /* bool choice type */");
                    }
                  else if (_fuir.clazzIsChoice(fclazz) &&
                           fclazz != valuecl  // NYI: interpreter checks fclazz._type != staticTypeOfValue
                           )
                    {
                      if (!_fuir.clazzIsUnitType(valuecl))
                        {
                          var value = stack.pop();                // value assigned to field
                          _c.println("// NYI: Assign to choice field "+outer+"." + fieldName + " = "+value);
                        }
                      else
                        {
                          _c.println("// NYI: Assign to choice field "+outer+"." + fieldName + " = (void)");
                        }
                      _c.println("// flcazz: "+_fuir.clazzAsString(fclazz));
                      _c.println("// valuecl: "+_fuir.clazzAsString(valuecl));
                    }
                  else if (_fuir.clazzIsUnitType(fclazz))
                    {
                      _c.println("// valueluess assignment to " + fieldAccess.code());
                    }
                  else
                    {
                      var value = stack.pop();                // value assigned to field
                      if (_fuir.clazzIsRef(fclazz))
                        {
                          value = value.castTo(clazzTypeName(fclazz));
                        }
                      // _c.print("// Assign to "+_fuir.clazzAsString(fclazz)+" outercl "+_fuir.clazzAsString(outercl)+" valuecl "+_fuir.clazzAsString(valuecl));
                      _c.print(fieldAccess.assign(value));
                    }
                }
              break;
            }
          case Box:
            {
              var v = stack.pop();
              _c.println("// NYI: Box " + v.code());

              var vc = _fuir.boxValueClazz(cl, c, i);
              var rc = _fuir.boxResultClazz(cl, c, i);
              if (_fuir.clazzIsRef(vc))
                { // vc's type is a generic argument whose actual type does not need
                  // boxing
                }
              else
                {
                  var t = CExpr.ident(newTemp());
                  _c.println(clazzTypeName(rc) + " " + t.code() + " = malloc(sizeof("+_structNames.get(cl)+"));");
                  if (!_fuir.clazzIsUnitType(vc))
                    {
                      var val = stack.pop();
                      _c.println("// NYI: copy fields from "+val.code()+" over to "+t.code());
                    }
                  stack.push(t);
                }
              if (_fuir.clazzIsChoice(vc))
                {
                  _c.println("// NYI: choice boxing");
                  /* NYI choice boxing:

                check
                  (rc.isChoice());
                if (vc.isChoiceOfOnlyRefs())
                  {
                    throw new Error("NYI: Boxing choiceOfOnlyRefs, does this happen at all?");
                  }
                else
                  {
                    check
                      (!rc.isChoiceOfOnlyRefs());

                    var voff = vc.choiceValsOffset_;
                    var roff = rc.choiceValsOffset_;
                    var vsz = vc.choiceValsSize_;
                    check
                      (rc.choiceValsSize_ == vsz);
                    if (val instanceof LValue)
                      {
                        voff += ((LValue) val).offset;
                        val   = ((LValue) val).container;
                      }
                    if (val instanceof boolValue)
                      {
                        val.storeNonRef(new LValue(Clazzes.bool.get(), ri, roff), Clazzes.bool.get().size());
                      }
                    else
                      {
                        var vi = (Instance) val;
                        for (int i = 0; i<vsz; i++)
                          {
                            ri.refs   [roff+i] = vi.refs   [voff+i];
                            ri.nonrefs[roff+i] = vi.nonrefs[voff+i];
                          }
                      }
                  }
                  */
                }
              break;
            }
          case Call:
            {
              if (_fuir.callIsDynamic(cl, c, i))
                {
                  _c.println("// Dynamic call to " + _fuir.callDebugString(c, i));
                  var ccs = _fuir.callCalledClazzes(cl, c, i);
                  var ac = _fuir.callArgCount(c, i);
                  var tc = _fuir.callTargetClazz(cl, c, i);
                  var t = CExpr.ident(newTemp());
                  var ti = stack.size() - ac - 1;
                  var tt0 = clazzTypeName(tc);
                  _c.println(tt0 + " " + t.code() + ";");
                  _c.print(t.assign(stack.get(ti).castTo(tt0)));
                  stack.set(ti, t);
                  var id = t.deref().field("clazzId");
                  if (ccs.length == 1)
                    {
                      var tt = _fuir.clazzOuterClazz(ccs[0]);
                      _c.println("// Dynamic call to " + _fuir.callDebugString(c, i) + " with exactly one target");
                      _c.print(CExpr.call("assert",new List<>(CExpr.eq(id, CExpr.int32const(clazzId2num(tt)))))); // <-- perfect reason to make () optional
                      _c.print(call(cl, c, i, ccs[0], stack, tt));
                    }
                  else if (ccs.length == 0)
                    {
                      _c.println("fprintf(stderr,\"*** no possible call target found\\n\"); exit(1);");
                    }
                  else
                    {
                      boolean outerAdrOfValue = false;
                      CExpr res = null;
                      var rt = _fuir.clazzResultClazz(ccs[0]); // NYI: HACK: just use the first clazz ccs[0] as static clazz for now.
                      if (rt != -1 && !_fuir.clazzIsUnitType(rt) &&
                          (!_fuir.withinCode(c, i+1) || _fuir.codeAt(c, i+1) != FUIR.ExprKind.WipeStack))
                        {
                          res = CExpr.ident(newTemp());
                          var isOuterRef = _fuir.clazzIsOuterRef(ccs[0]); // NYI: HACK: just use the first clazz ccs[0] as static clazz for now.
                          _c.println((isOuterRef
                                      ? clazzTypeNameOuterField(rt)
                                      : clazzTypeName  (rt)) + " " + res.code() + ";");
                          outerAdrOfValue = isOuterRef && outerClazzPassedAsAdrOfValue(rt);
                        }
                      _c.println("switch (" + id.code() + ") {");
                      _c.indent();
                      for (var cc : ccs)
                        {
                          var stack2 = (Stack<CExpr>) stack.clone();
                          var tt = _fuir.clazzOuterClazz(cc);
                          _c.println("// Call target "+ _fuir.clazzAsString(cc) + ":");
                          _c.println("case " + CExpr.int32const(clazzId2num(tt)).code() + ": {");
                          _c.indent();
                          _c.print(call(cl, c, i, cc, stack2, tt));
                          if (res != null)
                            {
                              var rv = stack2.pop();
                              if (outerAdrOfValue)
                                {
                                  rv = rv.adrOf();

                                  // NYI: This cast should not be needed when
                                  // outer clazz handling is fixed, in
                                  // particular, when clazzes
                                  //
                                  //   ref stream<i32>.asString
                                  //   ref conststring.ref asStream.asString
                                  //
                                  // are the same, the second one should be replaced by the first.
                                  rv = rv.castTo(clazzTypeNameOuterField(rt));  // NYI remove, see above.
                                }
                              if (_fuir.clazzIsRef(rt))
                                {
                                  rv = rv.castTo(clazzTypeName(rt));
                                }
                              _c.print(res.assign(rv));
                            }
                          _c.print(CStmnt.BREAK);
                          _c.unindent();
                          _c.println("}");
                        }
                      _c.println("default: { fprintf(stderr,\"*** unhandled dynamic call target %d\\n\", " + id.code() + "); exit(1); }");
                      _c.unindent();
                      _c.println("}");
                      stack.setSize(stack.size() - ac); // stack.popn(ac)
                      if (res != null)
                        {
                          if (outerAdrOfValue)
                            {
                              res = res.deref();
                            }
                          stack.push(res);
                        }
                    }
                }
              else
                {
                  var cc = _fuir.callCalledClazz(cl, c, i);
                  _c.print(call(cl, c, i, cc, stack, -1));
                }
              break;
            }
          case Current:
            {
              if (_fuir.clazzIsRef(cl))
                {
                  stack.push(CURRENT);
                }
              else
                {
                  stack.push(CURRENT.deref());
                }
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
              var bytes = _fuir.strConst(c, i);
              stack.push(constString(bytes));
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
          case WipeStack:
            {
              // stack.clear();   NYI: this currently causes a compiler crash, but it seems to work fine if we do nothing.
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
   * Create code to create a constant string and assign it to a new temp
   * variable. Return an CExpr that reads this variable.
   */
  CExpr constString(byte[] bytes)
  {
    StringBuilder sb = new StringBuilder();
    for (var b : bytes)
      {
        sb.append("\\"+((b >> 6) & 7)+((b >> 3) & 7)+(b & 7));
        sb.append("...");
      }
    var tmp = newTemp();
    _c.println("fzTr__Rconststring *" + tmp + " = malloc(sizeof(fzTr__Rconststring));\n" +
               tmp + "->clazzId = " + clazzId2num(_fuir.clazz_conststring()) + ";\n" +
               tmp + "->fzF_1_data = (void *)\"" + sb + "\";\n" +
               tmp + "->fzF_3_length = " + bytes.length + ";\n");
    return CExpr.ident(tmp);
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
      case Routine  :
      case Intrinsic:
        {
          if (SHOW_STACK_ON_CALL) System.out.println("Before call to "+_fuir.clazzAsString(cc)+": "+stack);
          CExpr res = null;
          var call = CExpr.call(_functionNames.get(cc), args(cl, c, i, cc, stack, ac, castTarget));
          if (rt != -1 && !_fuir.clazzIsUnitType(rt))
            {
              var tmp = newTemp();
              res = CExpr.ident(tmp);
              result = CStmnt.seq(CStmnt.decl(clazzTypeName(rt), tmp),
                                  res.assign(call));
              stack.push(res);
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
          if (tc != -1 && !_fuir.clazzIsUnitType(tc))
            {
              var t = stack.pop();
              if (rt != -1 && !_fuir.clazzIsUnitType(rt))
                {
                  var field = fieldName(_fuir.callFieldOffset(tc, c, i), cc);
                  CExpr res = ccodeAccessField(tc, t, field);
                  if (_fuir.clazzIsOuterRef(cc) && outerClazzPassedAsAdrOfValue(rt))  // NYI: Better have a specific FUIR statement for this
                    {
                      res = res.deref();
                    }
                  stack.push(res);
                }
            }
          else
            {
              check(rt == -1 || _fuir.clazzIsUnitType(rt));
            }
          break;
        }
      case Abstract: throw new Error("This should not happen: Calling abstract '" + _fuir.clazzAsString(cc) + "'");
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
        if (_fuir.clazzIsUnitType(_fuir.clazzArgClazz(cc, argCount-1)))
          {
            result = args(cl, c, i, cc, stack, argCount-1, castTarget);
          }
        else
          {
            var a = stack.pop();
            result = args(cl, c, i, cc, stack, argCount-1, castTarget);
            var ac = _fuir.clazzArgClazz(cc, argCount-1);
            a = _fuir.clazzIsRef(ac) ? a.castTo(clazzTypeName(ac)) : a;
            result.add(a);
          }
      }
    else // NYI: special handling of outer refs should not be part of BE, should be moved to FUIR
      { // ref to outer instance, passed by reference
        var tc = _fuir.callTargetClazz(cl, c, i);
        if (tc == -1 || _fuir.clazzIsUnitType(tc))
          {
            result = new List<>();
          }
        else
          {
            var a = stack.pop();
            var targetAsValue = !outerClazzPassedAsAdrOfValue(tc);
            var a2 = targetAsValue    ? a  : a.adrOf();
            var a3 = castTarget == -1 ? a2 : a2.castTo(clazzTypeName(castTarget));
            result = new List<>(a3);
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
  private void cFunctionDecl(int cl)
  {
    var res = _fuir.clazzResultClazz(cl);
    _c.print(res == -1 || _fuir.clazzIsUnitType(res)
             ? "void "
             : clazzTypeName(res) + " ");
    _c.print(_functionNames.get(cl));
    _c.print("(");
    var oc = _fuir.clazzOuterClazz(cl);
    String comma = "";
    if (oc != -1 && !_fuir.clazzIsUnitType(oc))
      {
        var or = _fuir.clazzOuterRef(cl);
        String type =
          or == -1                         ? clazzTypeNameOuterField(oc) :
          _fuir.clazzFieldIsAdrOfValue(or) ? clazzTypeNameOuterField(_fuir.clazzOuterClazz(cl))
                                           : clazzTypeName(_fuir.clazzResultClazz(or));
        _c.print(type);
        _c.print(" fzouter");
        comma = ", ";
      }
    var ac = _fuir.clazzArgCount(cl);
    for (int i = 0; i < ac; i++)
      {
        _c.print(comma);
        var at = _fuir.clazzArgClazz(cl, i);
        if (at != -1 && !_fuir.clazzIsUnitType(at))
          {
            var t = clazzTypeName(at);
            _c.print(t + " arg" + i);
            comma = ", ";
          }
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
          switch (_fuir.clazzKind(cl))
            {
            case Routine:
            case Intrinsic:
              {
                cFunctionDecl(cl);
                _c.print(";\n");
                break;
              }
            case Abstract:
            case Field:
              break;
            default:
              break;
            }
          break;
        }
      case IMPLEMENTATIONS:
        {
          _tempVarId = 0;  // reset counter for unique temp variables for function results
          switch (_fuir.clazzKind(cl))
            {
            case Routine:
              {
                _c.print("\n// code for clazz "+_fuir.clazzAsString(cl)+":\n");
                cFunctionDecl(cl);
                _c.print(" {\n");
                _c.indent();
                _c.print("" + _structNames.get(cl) + " *" + CURRENT.code() + " = malloc(sizeof(" + _structNames.get(cl) + "));\n"+
                         (_fuir.clazzIsRef(cl) ? CURRENT.deref().field("clazzId").assign(CExpr.int32const(clazzId2num(cl))).code() + ";\n" : ""));

                var or = _fuir.clazzOuterRef(cl);
                if (or != -1)
                  {
                    var oc = _fuir.clazzOuterClazz(cl);
                    if (oc != -1 && !_fuir.clazzIsUnitType(oc))
                      {
                        var outer = CExpr.ident("fzouter");
                        _c.print(CURRENT.deref().field(fieldNameInClazz(cl, or)).assign(outer));
                      }
                  }

                var ac = _fuir.clazzArgCount(cl);
                for (int i = 0; i < ac; i++)
                  {
                    var af = _fuir.clazzArg(cl, i);
                    var at = _fuir.clazzArgClazz(cl, i);
                    if (at != -1 && !_fuir.clazzIsUnitType(at))
                      {
                        var target =
                          _fuir.clazzIsI32(cl) ||
                          _fuir.clazzIsI64(cl)||
                          _fuir.clazzIsU32(cl)||
                          _fuir.clazzIsU64(cl)
                          ? CURRENT.deref()
                          : CURRENT.deref().field(fieldNameInClazz(cl, af));
                        _c.print(target.assign(CExpr.ident("arg" + i)));
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
                if (res != -1 && !_fuir.clazzIsUnitType(res))
                  {
                    var rf = _fuir.clazzResultField(cl);
                    if (rf != -1)
                      {
                        _c.println("return " + CURRENT.deref().field(fieldNameInClazz(cl, rf)).code() + ";");
                      }
                    else
                      {
                        if (_fuir.clazzIsRef(cl))
                          {
                            _c.println("return " + CURRENT.code() + ";");
                          }
                        else
                          {
                            _c.println("return " + CURRENT.deref().code() + ";");
                          }
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
                var or = _fuir.clazzOuterRef(cl);
                var fzo = CExpr.ident("fzouter");
                var outer =
                  or == -1                                     ? CExpr.dummy("--no outer--") :
                  _fuir.clazzFieldIsAdrOfValue(or)             ? fzo.deref() :
                  _fuir.clazzIsRef(_fuir.clazzResultClazz(or)) ? fzo.deref().field("fzF_0_val")
                                                               : fzo;

                switch (_fuir.clazzIntrinsicName(cl))
                  {
                  case "exitForCompilerTest" : _c.print(" exit(arg0);\n"); break;
                  case "fuzion.std.out.write": _c.print(" char c = (char) arg0; fwrite(&c, 1, 1, stdout);\n"); break;
                  case "fuzion.std.out.flush": _c.print(" fflush(stdout);\n"); break;

                    /* NYI: The C standard does not guarentee wrap-around semantics for signed types, need
                     * to check if this is the case for the C compilers used for Fuzion.
                     */
                  case "i32.prefix -°"       :
                  case "i64.prefix -°"       : _c.print(outer.neg().ret()); break;
                  case "i32.infix -°"        :
                  case "i64.infix -°"        : _c.print(outer.sub(CExpr.ident("arg0")).ret()); break;
                  case "i32.infix +°"        :
                  case "i64.infix +°"        : _c.print(outer.add(CExpr.ident("arg0")).ret()); break;
                  case "i32.infix *°"        :
                  case "i64.infix *°"        : _c.print(outer.mul(CExpr.ident("arg0")).ret()); break;
                  case "i32.div"             :
                  case "i64.div"             : _c.print(outer.div(CExpr.ident("arg0")).ret()); break;
                  case "i32.mod"             :
                  case "i64.mod"             : _c.print(outer.mod(CExpr.ident("arg0")).ret()); break;

                  case "i32.infix =="        :
                  case "i64.infix =="        : _c.print(outer.eq(CExpr.ident("arg0")).cond(FZ_TRUE, FZ_FALSE).ret()); break;
                  case "i32.infix !="        :
                  case "i64.infix !="        : _c.print(outer.ne(CExpr.ident("arg0")).cond(FZ_TRUE, FZ_FALSE).ret()); break;
                  case "i32.infix >"         :
                  case "i64.infix >"         : _c.print(outer.gt(CExpr.ident("arg0")).cond(FZ_TRUE, FZ_FALSE).ret()); break;
                  case "i32.infix >="        :
                  case "i64.infix >="        : _c.print(outer.ge(CExpr.ident("arg0")).cond(FZ_TRUE, FZ_FALSE).ret()); break;
                  case "i32.infix <"         :
                  case "i64.infix <"         : _c.print(outer.lt(CExpr.ident("arg0")).cond(FZ_TRUE, FZ_FALSE).ret()); break;
                  case "i32.infix <="        :
                  case "i64.infix <="        : _c.print(outer.le(CExpr.ident("arg0")).cond(FZ_TRUE, FZ_FALSE).ret()); break;

                  case "u32.prefix -°"       :
                  case "u64.prefix -°"       : _c.print(outer.neg().ret()); break;
                  case "u32.infix -°"        :
                  case "u64.infix -°"        : _c.print(outer.sub(CExpr.ident("arg0")).ret()); break;
                  case "u32.infix +°"        :
                  case "u64.infix +°"        : _c.print(outer.add(CExpr.ident("arg0")).ret()); break;
                  case "u32.infix *°"        :
                  case "u64.infix *°"        : _c.print(outer.mul(CExpr.ident("arg0")).ret()); break;
                  case "u32.div"             :
                  case "u64.div"             : _c.print(outer.div(CExpr.ident("arg0")).ret()); break;
                  case "u32.mod"             :
                  case "u64.mod"             : _c.print(outer.mod(CExpr.ident("arg0")).ret()); break;

                  case "u32.infix =="        :
                  case "u64.infix =="        : _c.print(outer.eq(CExpr.ident("arg0")).cond(FZ_TRUE, FZ_FALSE).ret()); break;
                  case "u32.infix !="        :
                  case "u64.infix !="        : _c.print(outer.ne(CExpr.ident("arg0")).cond(FZ_TRUE, FZ_FALSE).ret()); break;
                  case "u32.infix >"         :
                  case "u64.infix >"         : _c.print(outer.gt(CExpr.ident("arg0")).cond(FZ_TRUE, FZ_FALSE).ret()); break;
                  case "u32.infix >="        :
                  case "u64.infix >="        : _c.print(outer.ge(CExpr.ident("arg0")).cond(FZ_TRUE, FZ_FALSE).ret()); break;
                  case "u32.infix <"         :
                  case "u64.infix <"         : _c.print(outer.lt(CExpr.ident("arg0")).cond(FZ_TRUE, FZ_FALSE).ret()); break;
                  case "u32.infix <="        :
                  case "u64.infix <="        : _c.print(outer.le(CExpr.ident("arg0")).cond(FZ_TRUE, FZ_FALSE).ret()); break;

                  case "i32.as_i64"          : _c.print(outer.castTo("fzT_1i64").ret()); break;
                  case "u32.as_i64"          : _c.print(outer.castTo("fzT_1i64").ret()); break;
                  case "i32.castTo_u32"      : _c.print(outer.castTo("fzT_1u32").ret()); break;
                  case "u32.castTo_i32"      : _c.print(outer.castTo("fzT_1i32").ret()); break;
                  case "i64.castTo_u64"      : _c.print(outer.castTo("fzT_1u64").ret()); break;
                  case "i64.low32bits"       : _c.print(outer.and(CExpr. int64const(0xffffFFFFL)).castTo("fzT_1u32").ret()); break;
                  case "u64.castTo_i64"      : _c.print(outer.castTo("fzT_1i64").ret()); break;
                  case "u64.low32bits"       : _c.print(outer.and(CExpr.uint64const(0xffffFFFFL)).castTo("fzT_1u32").ret()); break;

                  case "Object.asString"     :
                    {
                      var str = constString("NYI: Object.asString".getBytes(StandardCharsets.UTF_8));
                      _c.print(" return " + str.castTo("fzTr__Rstring*").code() + ";\n");
                      break;
                    }

                    // NYI: the following intrinsics are generic, they are currently hard-coded for i32 only:
                  case "Array.getData": _c.print(" return malloc(sizeof(fzT_1i32) * arg0);\n"); break;
                  case "Array.setel"  : _c.print(" ((fzT_1i32*) arg0) [arg1] = arg2;\n"); break;
                  case "Array.get"    : _c.print(" return ((fzT_1i32*) arg0) [arg1];\n"); break;

                  default:
                    var msg = "code for intrinsic " + _fuir.clazzIntrinsicName(cl) + " is missing";
                    Errors.warning(msg);
                    _c.print(" fprintf(stderr, \"*** error: NYI: "+ msg + "\\n\"); exit(1);\n");
                    break;
                  }
                _c.print("}\n");
              }
            case Abstract:
            case Field:
              break;
            default:
              _c.println("// NYI: code for "+_fuir.clazzKind(cl)+" "+ _functionNames.get(cl));
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
