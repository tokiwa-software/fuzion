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

import java.util.ArrayList;
import java.util.Stack;
import java.util.TreeSet;

import java.util.stream.Stream;

import dev.flang.ir.Backend;
import dev.flang.ir.BackendCallable;
import dev.flang.ir.Clazz;   // NYI: remove this dependency!
import dev.flang.ir.Clazzes; // NYI: remove this dependency!

import dev.flang.fuir.FUIR;

import dev.flang.util.Errors;
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
    TYPES           { void compile(C c, int cl) { c.types(cl);    } }, // declare types
    STRUCTS         { void compile(C c, int cl) { c.structs(cl);  } }, // generate struct declarations
    FORWARDS        { void compile(C c, int cl) { c.forwards(cl); } }, // generate forward declarations only
    IMPLEMENTATIONS { void compile(C c, int cl) { c.code(cl);     } }; // generate C functions

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
   * Maximum length of (external) function names in C.  Since name mangling
   * easily results in lengthy names, we have to be careful not to exeed this.
   */
  private static final int MAX_C99_IDENTIFIER_LENGTH = 31;


  /**
   * Value to be used during compiler development for expressions that are not
   * yet implemented.
   */
  private static final String DUMMY = "NYI_DUMMY_VALUE";
  private static final CExpr CDUMMY = CExpr.ident(DUMMY);


  /**
   * Name of local variable containing current instance
   */
  private static final CExpr CURRENT = CExpr.ident("fzCur");


  /**
   * Prefix for types declared for clazz instances
   */
  private static final String TYPE_PREFIX = "fzT_";


  /**
   * For a reference clazz' struct, this is the name of a struct element that
   * contains the fields using the corresponding value clazz' struct.
   */
  static final String FIELDS_IN_REF_CLAZZ = "fields";

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
  static final CExpr FZ_FALSE =  CExpr.compoundLiteral(TYPE_PREFIX + "bool", "0");
  static final CExpr FZ_TRUE  =  CExpr.compoundLiteral(TYPE_PREFIX + "bool", "1");


  /**
   * C identifier of argument variable that refers to a clazz' outer instance.
   */
  static final CExpr _outer_ = CExpr.ident("fzouter");


  /**
   * The name of the tag field in instances of bool.fz.
   */
  private static final String TAG_NAME = "fzTag";


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
        return TYPE_PREFIX;
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
   * @param opt options to control compilation.
   *
   * @param fuir the intermeidate code.
   */
  public C(COptions opt,
           FUIR fuir)
  {
    _options = opt;
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
             if (!_fuir.clazzIsVoidType(c))
               {
                 p.compile(this, c);
               }
           }
       });
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
  String fieldName(int index, int field)
  {
    return FIELD_PREFIX + index + "_" + mangle(_fuir.clazzBaseName(field));
  }


  /**
   * Get the name of a field from an instance of a given clazz
   *
   * @param cl clazz id of the clazz that contains the field.
   *
   * @param field the field id
   */
  String fieldNameInClazz(int cl, int field)
  {
    int index = _fuir.clazzFieldIndex(cl, field);
    return fieldName(index, field);
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
   * The type of a field.  This is the usually the same as clazzTypeName() of
   * the field's result clazz, except for outer refs for which
   * clazzFieldIsAdrOfValue, where it is a pointer to that type.
   */
  String clazzFieldType(int cf)
  {
    return clazzTypeName(_fuir.clazzResultClazz(cf)) +
      (_fuir.clazzFieldIsAdrOfValue(cf) ? "*" : "");
  }



  /**
   * Create declarations of the C types required for the given clazz.  Write
   * code to _c.
   *
   * @param cl a clazz id.
   */
  public void types(int cl)
  {
    switch (_fuir.clazzKind(cl))
      {
      case Choice:
      case Routine:
        {
          var name = _structNames.get(cl);
          // special handling of stdlib clazzes known to the compiler
          var stype = scalarType(cl);
          var type = stype != null ? stype : "struct " + name;
          _c.print
                ("typedef " + type + " " + name + ";\n");
          break;
        }
      default:
        break;
      }
  }


  /**
   * Does the given clazz specify a scalar type in the C code, i.e, standard
   * numeric types i32, u64, etc.
   */
  boolean isScalarType(int cl)
  {
    return scalarType(cl) != null;
  }


  /**
   * Check if the given clazz specifies a scalar type in the C code, i.e,
   * standard numeric types i32, u64, etc. If so, return that C type.
   *
   * @return the C scalar type corresponding to cl, null if cl is not scaler.
   */
  String scalarType(int cl)
  {
    return
      _fuir.clazzIsI32(cl) ? "int32_t" :
      _fuir.clazzIsI64(cl) ? "int64_t" :
      _fuir.clazzIsU32(cl) ? "uint32_t" :
      _fuir.clazzIsU64(cl) ? "uint64_t" : null;
  }


  /**
   * Create declarations of the C structs required for the given clazz.  Write
   * code to _c.
   *
   * @param cl a clazz id.
   */
  public void structs(int cl)
  {
    switch (_fuir.clazzKind(cl))
      {
      case Choice:
      case Routine:
        {
          if (!_declaredStructs.contains(cl))
            {
              _declaredStructs.add(cl);
              if (!isScalarType(cl)) // special handling of stdlib clazzes known to the compiler
                {
                  // first, make sure structs used for inner fields are declared:
                  for (int i = 0; i < _fuir.clazzNumFields(cl); i++)
                    {
                      var cf = _fuir.clazzField(cl, i);
                      var rcl = _fuir.clazzResultClazz(cf);
                      if (!_fuir.clazzIsRef(rcl))
                        {
                          structs(rcl);
                        }
                    }
                  if (_fuir.clazzIsRef(cl))
                    {
                      structs(_fuir.clazzAsValue(cl));
                    }

                  // next, declare the struct itself
                  _c.print
                    ("// for " + _fuir.clazzAsString(cl) + "\n" +
                     "struct " + _structNames.get(cl) + " {\n");
                  if (_fuir.clazzIsChoice(cl))
                    {
                      var ct = _fuir.clazzChoiceTag(cl);
                      if (ct != -1)
                        {
                          String type = clazzFieldType(ct);
                          _c.print(" " + type + " " + TAG_NAME + ";\n");
                        }
                    }
                  else if (_fuir.clazzIsRef(cl))
                    {
                      var vcl = _fuir.clazzAsValue(cl);
                      _c.print("  uint32_t clazzId;\n" +
                               "  " + clazzTypeName(vcl) + " " + FIELDS_IN_REF_CLAZZ + ";\n");
                    }
                  else
                    {
                      for (int i = 0; i < _fuir.clazzNumFields(cl); i++)
                        {
                          var cf = _fuir.clazzField(cl, i);
                          String type = clazzFieldType(cf);
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
   * Test is a given clazz is not -1 and stores data.
   *
   * @param cl the clazz of val, may be -1
   *
   * @return true if cl != -1 and not unit or void type.
   */
  boolean hasData(int cl)
  {
    return cl != -1 &&
      !_fuir.clazzIsUnitType(cl) &&
      !_fuir.clazzIsVoidType(cl);
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
      (hasData(cl) || val != null);

    if (hasData(cl))
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
    return hasData(cl) ? stack.pop()
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
    for (int i = 0; _fuir.withinCode(c, i); i++)
      {
        var s = _fuir.codeAt(c, i);
        _c.println("// Code for statement " + s);
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
          case Assign:
            {
              var field = _fuir.assignedField(cl, c, i);  // field we are assigning to
              if (field != -1)
                {
                  var outercl   = _fuir.assignOuterClazz(cl, c, i);  // static clazz of outer
                  var valuecl   = _fuir.assignValueClazz(cl, c, i);  // static clazz of value
                  var fclazz    = _fuir.clazzResultClazz(field);     // static clazz of assigned field
                  var voutercl  = _fuir.clazzAsValue(outercl);
                  var fieldName = fieldNameInClazz(voutercl, field);
                  var outer     = pop(stack, outercl);                 // instance containing assigned field
                  if (_fuir.clazzIsChoice(fclazz) &&
                      fclazz != valuecl  // NYI: interpreter checks fclazz._type != staticTypeOfValue
                      )
                    {
                      var value = pop(stack, valuecl);                // value assigned to field
                      if (_fuir.clazzIsBool(fclazz))
                        { // bool is basically a choice type, but we do not use the tag in the generated C code
                          check(_fuir.clazzIsTRUE (valuecl) ||
                                _fuir.clazzIsFALSE(valuecl),
                                value == null);
                          var bvalue = _fuir.clazzIsTRUE(valuecl) ? FZ_TRUE : FZ_FALSE;
                          o = ccodeAccessField(outercl, outer, fieldName).assign(bvalue);
                        }
                      else
                        {
                          o = CStmnt.seq
                            (CStmnt.comment("NYI: Assign to choice field "+outer+"." + fieldName + " = "+ (value == null ? "(void)" : value)),
                             CStmnt.comment("flcazz: "+_fuir.clazzAsString(fclazz)),
                             CStmnt.comment("valuecl: "+_fuir.clazzAsString(valuecl)),
                             CExpr.call("assert", new List<>(CExpr.int32const(0))).comment("choice field assignemnt"));
                        }
                    }
                  else
                    {
                      var value = pop(stack, fclazz);                // value assigned to field
                      if (_fuir.clazzIsRef(fclazz))
                        {
                          value = value.castTo(clazzTypeName(fclazz));
                        }
                      // _c.print("// Assign to "+_fuir.clazzAsString(fclazz)+" outercl "+_fuir.clazzAsString(outercl)+" valuecl "+_fuir.clazzAsString(valuecl));
                      o = value == null
                        ? CStmnt.comment("valueluess assignment to " + outer)
                        : ccodeAccessField(outercl, outer, fieldName).assign(value);
                    }
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
                  o = CStmnt.comment("Box " + _fuir.clazzAsString(vc) + " is NOP, clazz is already a ref");
                }
              else
                {
                  _c.println("// Box " + _fuir.clazzAsString(vc));
                  var t = CExpr.ident(newTemp());
                  _c.println(clazzTypeName(rc) + " " + t.code() + " = malloc(sizeof(" + _structNames.get(rc) + "));");
                  _c.print(t.deref().field("clazzId").assign(CExpr.int32const(clazzId2num(rc))));
                  if (_fuir.clazzIsChoice(vc))
                    {
                      _c.println("// NYI: choice boxing");
                      _c.print(CExpr.call("assert", new List<>(CExpr.int32const(0))).commnt("/* choice boxing */"));
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
                  else
                    {
                      var val = pop(stack, vc);
                      if (val != null)
                        {
                          _c.print(t.deref().field(FIELDS_IN_REF_CLAZZ).assign(val));
                        }
                    }
                  push(stack, rc, t);
                }
              break;
            }
          case Call:
            {
              if (_fuir.callIsDynamic(cl, c, i))
                {
                  var cc0 = _fuir.callCalledClazz  (cl, c, i);
                  _c.println("// Dynamic call to " + _fuir.clazzAsString(cc0));
                  var ccs = _fuir.callCalledClazzes(cl, c, i);
                  var ac = _fuir.callArgCount(c, i);
                  var tc = _fuir.callTargetClazz(cl, c, i);
                  var t = CExpr.ident(newTemp());
                  var ti = stack.size() - ac - 1;
                  var tt0 = clazzTypeName(tc);
                  _c.println(tt0 + " " + t.code()+ ";");
                  _c.print(t.assign(stack.get(ti).castTo(tt0)));
                  stack.set(ti, t);
                  var id = t.deref().field("clazzId");
                  if (ccs.length == 1)
                    {
                      var tt = _fuir.clazzOuterClazz(ccs[0]);
                      _c.println("// Dynamic call to " + _fuir.clazzAsString(cc0) + " with exactly one target");
                      _c.print(CExpr.call("assert",new List<>(CExpr.eq(id, CExpr.int32const(clazzId2num(tt)))))); // <-- perfect reason to make () optional
                      _c.print(call(cl, c, i, ccs[0], stack, tt));
                    }
                  else if (ccs.length == 0)
                    {
                      _c.println("fprintf(stderr,\"*** no possible call target found\\n\"); exit(1);");
                    }
                  else
                    {
                      CExpr res = null;
                      var rt = _fuir.clazzResultClazz(cc0);
                      if (hasData(rt) &&
                          (!_fuir.withinCode(c, i+1) || _fuir.codeAt(c, i+1) != FUIR.ExprKind.WipeStack))
                        {
                          res = CExpr.ident(newTemp());
                          _c.println(clazzTypeName(rt) + " " + res.code() + ";");
                        }
                      _c.println("switch (" + id.code() + ") {");
                      _c.indent();
                      var stack2 = stack;
                      for (var cc : ccs)
                        {
                          stack =  (Stack<CExpr>) stack2.clone();
                          var tt = _fuir.clazzOuterClazz(cc);
                          _c.println("// Call target "+ _fuir.clazzAsString(cc) + ":");
                          _c.println("case " + CExpr.int32const(clazzId2num(tt)).code() + ": {");
                          _c.indent();
                          _c.print(call(cl, c, i, cc, stack, tt));
                          var rt2 = _fuir.clazzResultClazz(cc); // NYI: Check why rt2 and rt can be different
                          if (hasData(rt2))
                            {
                              var rv = pop(stack, rt2);
                              if ((rt == rt2 || _fuir.clazzIsRef(rt) && _fuir.clazzIsRef(rt2)) && // NYI: Remove this conditions when ccs set no longer contains false entries
                                  rv != CDUMMY)
                                {
                                  if (res != null)
                                    {
                                      if (_fuir.clazzIsRef(rt))
                                        {
                                          rv = rv.castTo(clazzTypeName(rt));
                                        }
                                      _c.print(res.assign(rv));
                                    }
                                }
                            }
                          _c.print(CStmnt.BREAK);
                          _c.unindent();
                          _c.println("}");
                        }
                      _c.println("default: { fprintf(stderr,\"*** unhandled dynamic call target %d\\n\", " + id.code() + "); exit(1); }");
                      _c.unindent();
                      _c.println("}");
                      stack = stack2;
                      args(cl, c, i, cc0, stack, _fuir.clazzArgCount(cc0), _fuir.clazzOuterClazz(cc0));
                      if (res != null)
                        {
                          push(stack, rt, res);
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
              push(stack, cl, current(cl));
              break;
            }
          case If:
            {
              var cond = stack.pop();
              var block     = _fuir.i32Const(c, i + 1);
              var elseBlock = _fuir.i32Const(c, i + 2);
              var stack2 = stack;
              i = i + 2;
              _c.println("if (" + cond.field(TAG_NAME).code() + " != 0) {");
              _c.indent();
              stack = (Stack<CExpr>) stack2.clone();
              createCode(cl, stack, block);
              _c.unindent();
              _c.println("} else {");
              _c.indent();
              stack = stack2;
              createCode(cl, stack, elseBlock);
              _c.unindent();
              _c.println("}");
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
              var tmp = newTemp();
              o = constString(bytes, tmp);
              stack.push(CExpr.ident(tmp));
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
              o = CStmnt.comment("NYI: singleton");
              break;
            }
          case WipeStack:
            {
              stack.clear();
              break;
            }
          case NOP:
            {
              break;
            }
          default:
            {
              System.err.println("*** error: C backend does not handle statments of type " + s);
            }
          }
        _c.print(o);
        if (SHOW_STACK_AFTER_STMNT) System.out.println("After " + s +" in "+_fuir.clazzAsString(cl)+": "+stack);
      }
  }


  /**
   * Create code to create a constant string and assign it to a new temp
   * variable. Return an CExpr that reads this variable.
   */
  CStmnt constString(byte[] bytes, String tmp)
  {
    StringBuilder sb = new StringBuilder();
    for (var bb : bytes)
      {
        var b = bb & 0xff;
        sb.append("\\"+((b >> 6) & 7)+((b >> 3) & 7)+(b & 7));
        sb.append("...");
      }
    var t = CExpr.ident(tmp);
    return CStmnt.seq(CStmnt.decl("fzT__Rconststring *", tmp),
                      CExpr.ident(tmp).assign(CExpr.call("malloc", new List<>(CExpr.ident("fzT__Rconststring").sizeOfType()))),
                      t.deref().field("clazzId").assign(CExpr.int32const(clazzId2num(_fuir.clazz_conststring()))),
                      t.deref().field(FIELDS_IN_REF_CLAZZ).field("fzF_1_data").assign(CExpr.string(sb.toString()).castTo("void *")),
                      t.deref().field(FIELDS_IN_REF_CLAZZ).field("fzF_3_length").assign(CExpr.int32const(bytes.length)));
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
    if (ac != _fuir.clazzArgCount(cc)) // NYI: Remove this conditions when ccs set no longer contains false entries
      {
        _c.println("// Arg count does not match, expected "+ac+", called clazz "+_fuir.clazzAsString(cc)+" has "+_fuir.clazzArgCount(cc));
        push(stack, rt, CDUMMY);
      }
    else
    switch (_fuir.clazzKind(cc))
      {
      case Routine  :
      case Intrinsic:
        {
          if (SHOW_STACK_ON_CALL) System.out.println("Before call to "+_fuir.clazzAsString(cc)+": "+stack);
          CExpr res = null;
          var call = CExpr.call(_functionNames.get(cc), args(cl, c, i, cc, stack, ac, castTarget));
          if (hasData(rt))
            {
              var tmp = newTemp();
              res = CExpr.ident(tmp);
              result = CStmnt.seq(CStmnt.decl(clazzTypeName(rt), tmp),
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
            (t != null || !hasData(rt));
          if (hasData(rt))
            {
              var vtc = _fuir.clazzAsValue(tc);
              var field = fieldName(_fuir.callFieldOffset(vtc, c, i), cc);
              CExpr res = ccodeAccessField(tc, t, field);
              res = _fuir.clazzFieldIsAdrOfValue(cc) ? res.deref() : res;
              push(stack, rt, res);
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
        var ac = _fuir.clazzArgClazz(cc, argCount-1);
        var a = pop(stack, ac);
        result = args(cl, c, i, cc, stack, argCount-1, castTarget);
        if (hasData(ac))
          {
            a = _fuir.clazzIsRef(ac) ? a.castTo(clazzTypeName(ac)) : a;
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
            var a3 = castTarget == -1 ? a2 : a2.castTo(clazzTypeName(castTarget));
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
  private void cFunctionDecl(int cl)
  {
    var res = _fuir.clazzResultClazz(cl);
    _c.print(!hasData(res)
             ? "void "
             : clazzTypeName(res) + " ");
    _c.print(_functionNames.get(cl));
    _c.print("(");
    String comma = "";
    var or = _fuir.clazzOuterRef(cl);
    if (or != -1)
      {
        _c.print(clazzFieldType(or));
        _c.print(" fzouter");
        comma = ", ";
      }
    var ac = _fuir.clazzArgCount(cl);
    for (int i = 0; i < ac; i++)
      {
        var at = _fuir.clazzArgClazz(cl, i);
        if (hasData(at))
          {
            _c.print(comma);
            var t = clazzTypeName(at);
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
          cFunctionDecl(cl);
          _c.print(";\n");
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
    _tempVarId = 0;  // reset counter for unique temp variables for function results
    switch (_fuir.clazzKind(cl))
      {
      case Routine:
        {
          _c.print("\n// code for clazz "+_fuir.clazzAsString(cl)+":\n");
          cFunctionDecl(cl);
          _c.print(" {\n");
          _c.indent();
          codeForRoutine(cl);
          _c.unindent();
          _c.println("}");
          break;
        }
      case Intrinsic:
        {
          _c.print("\n// code for intrinsic " + _fuir.clazzAsString(cl) + ":\n");
          cFunctionDecl(cl);
          _c.print(" {\n");
          _c.indent();
          _c.print(new Intrinsics().code(this, cl));
          _c.unindent();
          _c.print("}\n");
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

    _c.print("" + _structNames.get(cl) + " *" + CURRENT.code() + " = malloc(sizeof(" + _structNames.get(cl) + "));\n"+
             (_fuir.clazzIsRef(cl) ? CURRENT.deref().field("clazzId").assign(CExpr.int32const(clazzId2num(cl))).code() + ";\n" : ""));

    var cur = _fuir.clazzIsRef(cl) ? CURRENT.deref().field(FIELDS_IN_REF_CLAZZ)
                                   : CURRENT.deref();
    var vcl = _fuir.clazzAsValue(cl);
    var or = _fuir.clazzOuterRef(vcl);
    if (or != -1)
      {
        _c.print(cur.field(fieldNameInClazz(vcl, or)).assign(_outer_));
      }

    var ac = _fuir.clazzArgCount(vcl);
    for (int i = 0; i < ac; i++)
      {
        var af = _fuir.clazzArg(vcl, i);
        var at = _fuir.clazzArgClazz(vcl, i);
        if (hasData(at))
          {
            var target = isScalarType(vcl)
              ? cur
              : cur.field(fieldNameInClazz(vcl, af));
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
    if (hasData(res))
      {
        var rf = _fuir.clazzResultField(cl);
        _c.print(rf != -1
                 ? current(cl).field(fieldNameInClazz(cl, rf)).ret()  // a routine, return result field
                 : current(cl).ret()                                  // a constructor, return current instance
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
      ? CURRENT
      : CURRENT.deref();
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
        outer = outer.deref().field(FIELDS_IN_REF_CLAZZ);
      }
    return outer.field(fieldName);
  }

}

/* end of file */
