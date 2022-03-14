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
 * Source of class CNames
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;

import java.util.ArrayList;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;


/**
 * CNames provides methods to create and manage names of C identifiers
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class CNames extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Prefix for C functions created for Fuzion routines or intrinsics
   */
  private static final String C_FUNCTION_PREFIX = "fzC_";


  /**
   * Prefix for C functions created for Fuzion preconditions
   */
  private static final String C_PRECONDITION_PREFIX = "fzP_";


  /**
   * Prefix for types declared for clazz instances
   */
  private static final String TYPE_PREFIX = "fzT_";


  /**
   * C identifier of static singleton instance of the universe.
   */
  static final CIdent UNIVERSE = new CIdent("fzI_universe");


  /**
   * Maximum length of (external) function names in C.  Since name mangling
   * easily results in lengthy names, we have to be careful not to exeed this.
   */
  private static final int MAX_C99_IDENTIFIER_LENGTH = 31;


  /**
   * Value to be used during compiler development for expressions that are not
   * yet implemented.
   */
  static final CExpr CDUMMY = new CIdent("NYI_DUMMY_VALUE");


  /**
   * Name of local variable containing current instance
   */
  static final CIdent CURRENT = new CIdent("fzCur");


  /**
   * For a reference clazz' struct, this is the name of a struct element that
   * contains the fields using the corresponding value clazz' struct.
   */
  static final CIdent FIELDS_IN_REF_CLAZZ = new CIdent("fields");


  /**
   * Prefix for temporary local variables
   */
  private static final String TEMP_VAR_PREFIX = "fzM_";


  /**
   * Prefix for fields in an instance
   */
  private static final String FIELD_PREFIX = "fzF_";


  /**
   * Prefix for helper functions
   */
  private static final String HELPER_PREFIX = "fzH_";


  /**
   * Prefix for thread local env variable that stores the current oneway monad.
   */
  private static final String ENV_PREFIX = "fzEnv_";


  /**
   * C identifier of argument variable that refers to a clazz' outer instance.
   */
  static final CExpr OUTER = new CIdent("fzouter");


  /**
   * The name of the clazzId field in ref instances
   */
  static final CIdent CLAZZ_ID = new CIdent("clazzId");


  /**
   * The name of the tag field in choice clazzes such as bool.fz.
   */
  static final CIdent TAG_NAME = new CIdent("fzTag");


  /**
   * The name of the choice union's field name in choice clazzes
   */
  static final CIdent CHOICE_UNION_NAME = new CIdent("fzChoice");


  /**
   * The prefix of the name of the choice union's entries, will be concatenated
   * with the index i in _fuir.clazzChoice(cl, i).
   */
  static final String CHOICE_ENTRY_NAME = "v";


  /**
   * The prefix of the name of the choice union's reference entries.
   */
  static final CIdent CHOICE_REF_ENTRY_NAME = new CIdent("vref");


  /**
   * Name of helper function to clone a stack allocated instance on the heap.
   */
  static final CIdent HEAP_CLONE = new CIdent(HELPER_PREFIX + "heapClone");


  /**
   * C symbol "NULL"
   */
  static final CExpr NULL = new CIdent("NULL");


  /*----------------------------  variables  ----------------------------*/


  /**
   * The intermediate code we are compiling.
   */
  private final FUIR _fuir;


  /**
   * Counter for unique temp variables to hold function results
   */
  int _tempVarId = 0;


  /**
   * Mapping from clazz ids to C function names
   */
  private final CClazzNames _functionNames = new CClazzNames(C_FUNCTION_PREFIX);


  /**
   * Mapping from clazz ids to C function names
   */
  private final CClazzNames _preConditionNames = new CClazzNames(C_PRECONDITION_PREFIX);


  /**
   * Mapping from clazz ids to C struct names
   */
  private final CClazzNames _structNames = new CClazzNames(TYPE_PREFIX);


  /**
   * Generator and cache for C names created from clazzes.
   */
  private class CClazzNames
  {

    /**
     * The cache mapping clazz num to C names
     */
    private final ArrayList<String> _cache = new ArrayList<>();


    /**
     * prefix to be used for given class of names.
     */
    private final String _prefix;


    /**
     * Constructor for a set of names
     *
     * @param prefix to be used for given class of names.
     */
    CClazzNames(String prefix)
    {
      _prefix = prefix;
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
    private void clazzMangledName(int cl, StringBuilder sb)
    {
      var o = _fuir.clazzOuterClazz(cl);
      String sep = "";
      if (o != -1 &&
          _fuir.clazzOuterClazz(o) != -1)
        { // add o a prefix unless cl or o are universe
          clazzMangledName(o, sb);
          sep = "__";
        }
      sb.append(_fuir.clazzIsRef(cl) ? "_R" : sep);
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
          var p = _prefix;
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
              if (CHECKS) check
                (res.length() == MAX_C99_IDENTIFIER_LENGTH);
            }
          _cache.set(num, res);
        }

      return res;
    }
  }


  /**
   * C constants corresponding to Fuzion's true and false values.
   */
  final CExpr FZ_FALSE =  CExpr.compoundLiteral(_structNames._prefix + "bool", "0");
  final CExpr FZ_TRUE  =  CExpr.compoundLiteral(_structNames._prefix + "bool", "1");


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create instance of CNames
   */
  public CNames(FUIR fuir)
  {
    this._fuir = fuir;
  }


  /*-----------------------------  methods  -----------------------------*/


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
  private static String mangle(String s)
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
   * NYI: Documentation, just discard the sign?
   */
  private int clazzId2num(int cl)
  {
    return cl & 0xFFFffff; // NYI: give a name to this constant
  }


  /**
   * Name of the C struct of the given clazz.
   *
   * @param cl clazz id
   */
  String struct(int cl)
  {
    return _structNames.get(cl);
  }


  /**
   * Name of the C function of the given clazz.
   *
   * @param cl clazz id
   *
   * @param pre true iff we want to get the precondition, not the function
   * itself.
   */
  String function(int cl, boolean pre)
  {
    return (pre
            ? _preConditionNames
            : _functionNames    ).get(cl);
  }


  /**
   * Create C expression for clazz id converted to a number
   */
  CExpr clazzId(int cl)
  {
    return CExpr.int32const(clazzId2num(cl));
  }


  /**
   * Create a name for a new local temp variable.
   */
  CIdent newTemp()
  {
    return new CIdent(TEMP_VAR_PREFIX + (_tempVarId++));
  }


  /**
   * Get the name of a field
   *
   * @param field the field id
   */
  CIdent fieldName(int field)
  {
    var index = _fuir.fieldIndex(field);
    return new CIdent(FIELD_PREFIX + index + "_" + mangle(_fuir.clazzBaseName(field)));

  }

  /**
   * The name of the thread local env variable for the given onewayMonad type.
   *
   * @param cl clazz id for a onewayMonad type.
   */
  CIdent env(int cl)
  {
    return new CIdent(ENV_PREFIX + clazzId2num(cl));
  }

}

/* end of file */
