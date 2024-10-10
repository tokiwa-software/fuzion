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
 * Source of class Names
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm;

import java.util.ArrayList;
import java.util.TreeMap;

import dev.flang.be.jvm.classfile.ClassFileConstants;

import dev.flang.be.jvm.runtime.Any;
import dev.flang.be.jvm.runtime.AnyI;
import dev.flang.be.jvm.runtime.Main;
import dev.flang.be.jvm.runtime.Runtime;
import dev.flang.be.jvm.runtime.Intrinsics;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;


/**
 * Names provides methods to create and manage names of Java classes, methods,
 * fields, etc.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Names extends ANY implements ClassFileConstants
{


  /*----------------------------  constants  ----------------------------*/

  /**
   * Name of JVM backend's runtime's class Runtime
   */
  static final String RUNTIME_CLASS = Runtime.class.getName().replace(".","/");


  /**
   * Name of JVM backend's runtime's method Runtime.run
   */
  static final String RUNTIME_RUN = "run";


  /**
   * Name of JVM backend's runtime's interface Main
   */
  static final String MAIN_INTERFACE = Main.class.getName().replace(".","/");


  /**
   * Name of JVM backend's runtime's interface method Main.fz_run.
   */
  static final String MAIN_RUN = "fz_run";

  /**
   * Name of JVM backend's runtime's class that defines Intrinsics that are not inlined.
   */
  static final String RUNTIME_INTRINSICS_CLASS = Intrinsics.class.getName().replace(".","/");


  /**
   * Name of JVM backend's runtime's class Any and interface AnyI
   */
  static final String    ANY_CLASS  = Any.class.getName().replace(".","/");
  static final ClassType ANY_TYPE   = new ClassType(ANY_CLASS);
  static final String    ANY_DESCR  = ANY_TYPE.descriptor();
  static final String    ANYI_CLASS = AnyI.class.getName().replace(".","/");
  static final ClassType ANYI_TYPE  = new ClassType(ANYI_CLASS);
  static final String    ANYI_DESCR = ANYI_TYPE.descriptor();


  /**
   * Name of JVM backend's runtime's class Abort and field Abort._effect
   */
  static final String    ABORT_CLASS  = Runtime.Abort.class.getName().replace(".","/");
  static final ClassType ABORT_TYPE   = new ClassType(ABORT_CLASS);
  static final String    ABORT_EFFECT = "_effect";


  /**
   * Name and signature of Runtime._args_ field and Runtime.args_get method
   */
  static final String RUNTIME_ARGS = "_args_";
  static final String RUNTIME_ARGS_GET = "args_get";
  static final String RUNTIME_ARGS_GET_SIG = "(I)[B";


  /**
   * Name and signature of Runtime.internalArrayForConstString().
   */
  static final String RUNTIME_INTERNAL_ARRAY_FOR_CONST_STRING     = "internalArrayForConstString";
  static final String RUNTIME_INTERNAL_ARRAY_FOR_CONST_STRING_SIG = "(Ljava/lang/String;)[B";
  static final String RUNTIME_INTERNAL_ARRAY_FOR_ARRAY_8          = "constArray8FromString";
  static final String RUNTIME_INTERNAL_ARRAY_FOR_ARRAY_8_SIG      = "(Ljava/lang/String;I)[B";
  static final String RUNTIME_INTERNAL_ARRAY_FOR_ARRAY_I16        = "constArrayI16FromString";
  static final String RUNTIME_INTERNAL_ARRAY_FOR_ARRAY_I16_SIG    = "(Ljava/lang/String;)[S";
  static final String RUNTIME_INTERNAL_ARRAY_FOR_ARRAY_U16        = "constArrayU16FromString";
  static final String RUNTIME_INTERNAL_ARRAY_FOR_ARRAY_U16_SIG    = "(Ljava/lang/String;)[C";
  static final String RUNTIME_INTERNAL_ARRAY_FOR_ARRAY_32         = "constArray32FromString";
  static final String RUNTIME_INTERNAL_ARRAY_FOR_ARRAY_32_SIG     = "(Ljava/lang/String;)[I";
  static final String RUNTIME_INTERNAL_ARRAY_FOR_ARRAY_64         = "constArray64FromString";
  static final String RUNTIME_INTERNAL_ARRAY_FOR_ARRAY_64_SIG     = "(Ljava/lang/String;)[J";
  static final String RUNTIME_INTERNAL_ARRAY_FOR_ARRAY_F32        = "constArrayF32FromString";
  static final String RUNTIME_INTERNAL_ARRAY_FOR_ARRAY_F32_SIG    = "(Ljava/lang/String;)[F";
  static final String RUNTIME_INTERNAL_ARRAY_FOR_ARRAY_F64        = "constArrayF64FromString";
  static final String RUNTIME_INTERNAL_ARRAY_FOR_ARRAY_F64_SIG    = "(Ljava/lang/String;)[D";

  /**
   * Name and signature of Runtime.effect_get()
   */
  static final String RUNTIME_EFFECT_GET     = "effect_get";
  static final String RUNTIME_EFFECT_GET_SIG = "(I)" + ANYI_DESCR;


  /**
   * Name of Runtime.LOCK_FOR_ATOMIC
   */
  static final String RUNTIME_LOCK_FOR_ATOMIC   = "LOCK_FOR_ATOMIC";


  /**
   * Prefix for Java class names created for Fuzion routines or intrinsics
   */
  private static final String CLASS_PREFIX = "fzC_";
  private static final String INTERFACE_PREFIX = "fzI_";
  private static final String DYNAMIC_FUNCTION_PREFIX = "fzD_";

  static final String ROUTINE_NAME      = "fzRoutine";                // method with code for the routine
  static final String NAME_ID           = "_L";
  static
  {
    if (CHECKS) check
      (ROUTINE_NAME.equals(Runtime.ROUTINE_NAME),
       CLASS_PREFIX.equals(Runtime.CLASS_PREFIX),
       (CLASS_PREFIX + NAME_ID).equals(Runtime.CLASS_PREFIX_WITH_ID));
  }

  /**
   * Prefix for Java fields created for Fuzion fields
   */
  private static final String FIELD_PREFIX = "fzF_";

  static final String UNIVERSE_FIELD = "fzUniverse";


  public static final String BOXED_VALUE_FIELD_NAME = "fzFBoxed_value";
  public static final String BOX_METHOD_NAME = "fzBox";


  /**
   * Maximum length of Java class names to be created.
   */
  private static final int MAX_JAVA_NAME_LENGTH = 64;


  /**
   * The name of the tag field in choice clazzes such as bool.fz.
   */
  static final String TAG_NAME = "fzTag";

  /**
   * The name of the tag field function in choice clazzes of kind refsAndUnits.
   */
  static final String GET_TAG = "fzTag_";


  /**
   * The prefix of the name of the choice union's entries, will be concatenated
   * with the index i in _fuir.clazzChoice(cl, i).
   */
  static final String CHOICE_ENTRY_NAME = "fzV";


  /**
   * The prefix of the name of the choice union's reference entries.
   */
  static final String CHOICE_REF_ENTRY_NAME = "fzVref";

  /**
   * For choice of kine refsAndUnits, there are fields "fzU_1", "fzU_2", etc. for
   * each tag number for a unit type that contain preallocated references.
   */
  static final String CHOICE_UNIT_AS_REF_PREFIX = "fzU_";


  /**
   * Prefix for static fields in universe to hold pre-allocated constants
   */
  static final String PREALLOCATED_CONSTANT_PREFIX = "fzK_";


  /*----------------------------  variables  ----------------------------*/


  /**
   * The intermediate code we are compiling.
   */
  private final FUIR _fuir;


  /**
   * Mapping from clazz ids to Java class names
   */
  private final ClazzNames _classNames = new ClazzNames(CLASS_PREFIX, true);


  /**
   * Mapping from clazz ids to Java interface names
   */
  private final ClazzNames _interfaceNames = new ClazzNames(INTERFACE_PREFIX, true);


  /**
   * Mapping from field ids to Java field names
   */
  private final ClazzNames _fieldNames = new ClazzNames(FIELD_PREFIX, false)
    {
      String rawName(int field)
      {
        var index = _fuir.isJavaRef(field)
          ? ""
          : _fuir.fieldIndex(field) + "_";
        return _prefix + index + baseName(field);
      }
    };


  /**
   * Get the data for the resource Runtime.CLASS_NAME_TO_FUZION_CLAZZ_NAME that
   * holds a map from Java class names to human readable fuzion class names.
   *
   * The resource consists of lines separated by "\n" that consist of `java_name
   * + "\" + fuzion_clazz_name`.
   */
  String methodNameToFuzionClazzNames()
  {
    StringBuilder result = new StringBuilder();
    for (var n = 0; n < _classNames._cache.size(); n++)
      {
        var j = _classNames._cache.get(n);
        if (j != null)
          {
            var cl = _fuir.firstClazz() + n;
            result
              .append(j)
              .append("\t")
              .append(_fuir.clazzAsStringHuman(cl))
              .append("\n");
          }
      }
    return result.toString();
  }


  /**
   * Generator and cache for Java names created from clazzes.
   */
  private class ClazzNames
  {

    /**
     * The cache mapping clazz num to names
     */
    private final ArrayList<String> _cache = new ArrayList<>();


    /**
     * When creating unique names, this is the set of existing names that can no
     * longer be used.
     */
    private final TreeMap<String, Integer> _existing;


    /**
     * prefix to be used for given class of names.
     */
    final String _prefix;


    /**
     * Constructor for a set of names
     *
     * @param prefix to be used for given class of names.
     *
     * @param forceUniqueness true if these names are global and name clashes
     * must be avoided.
     */
    ClazzNames(String prefix, boolean forceUniqueness)
    {
      _prefix = prefix;
      _existing = forceUniqueness ? new TreeMap<>() : null;
    }


    /**
     * Append mangled name of given feature to StringBuilder.  Prepend with outer
     * feature's mangled name.
     *
     * Ex. Feature "i32.prefix -"  will result in  "i32__prefix_wm"
     *
     * @param sb a StringBuilder
     */
    private void clazzMangledName(int cl, StringBuilder sb)
    {
      var o = _fuir.clazzOuterClazz(cl);
      String sep = "";
      if (o != -1 &&
          _fuir.clazzOuterClazz(o) != -1)
        { // add a prefix unless cl or o are universe
          clazzMangledName(o, sb);
          sep = "__";
        }
      sb.append(_fuir.clazzIsBoxed(cl) ? "_B" : sep);
      var n = _fuir.clazzArgCount(cl);
      if (n > 0)
        {
          sb.append(n);  // put arg count before the name since name never starts with a digit
        }
      sb.append(baseName(cl));
    }


    /**
     * Create unique mangled name for a clazz that can be used in Java identifiers
     * (i.e., it starts with letter or '_' and contains only ASCII letters,
     * digits or '_'.
     *
     * @param cl id of a clazz
     *
     * @return the corresponding clazz
     */
    String get(int cl)
    {
      int num = _fuir.clazzId2num(cl);
      _cache.ensureCapacity(num + 1);
      while (_cache.size() <= num)  // why is there no ArrayList.setSize?
        {
          _cache.add(null);
        }
      var res = _cache.get(num);
      if (res == null)
        {
          res = rawName(cl);

          if (res.length() > MAX_JAVA_NAME_LENGTH)
            {
              var p = _prefix;
              var s = p + NAME_ID + num + "_";
              res = s +
                res.substring(p.length(), p.length() + 10) + "__" +
                res.substring(res.length() - MAX_JAVA_NAME_LENGTH + s.length() + 12);
              if (CHECKS) check
                (res.length() == MAX_JAVA_NAME_LENGTH);
            }

          var n = _existing == null ? 0 : _existing.getOrDefault(res, 0);
          if (n > 0)
            { // "abcdef" -> "abcdef_<n>" while keeping length <= MAX_JAVA_NAME_LENGTH and avoiding new conflicts:
              String nres;
              do
                {
                  var suffix = "_" + n;
                  var l = Math.min(res.length(), MAX_JAVA_NAME_LENGTH - suffix.length());
                  nres = res.substring(0, l) + suffix;
                  n = n + 1;
                  _existing.put(res, n);
                }
              while (_existing.containsKey(nres));
              res = nres;
            }
          _cache.set(num, res);
          if (_existing != null)
            {
              _existing.put(res, 1);
            }
        }

      return res;
    }

    /**
     * The 'raw' name not taking the maximum length into account and not
     * performing caching.
     *
     * @param cl id of a clazz
     */
    String rawName(int cl)
    {
      var p = _prefix;
      var sb = new StringBuilder(p);
      clazzMangledName(cl, sb);
      // NYI: UNDER DEVELOPMENT: there might be name conflicts due to different generic instances, so
      // we need to add the clazz id or the actual generics if this is the case:
      //
      // sb.append("_").append(_fuir.clazzId2num(cl)).append("_");
      return sb.toString();
    }
  }


  /**
   * Unique mapping from base names to mangled base names.
   */
  TreeMap<String,String> _simpleBaseNames = new TreeMap<>();


  /**
   * Map from PreallocatedConstants to field names if static field in
   * fzC_universe that hold these constant values.
   */
  TreeMap<PreallocatedConstant, String> _preallocatedConstantFieldNames = new TreeMap<>();


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create instance of Names
   */
  public Names(FUIR fuir)
  {
    this._fuir = fuir;

    // create all mangled base names
    var back = new TreeMap<String,String>();
    for (var cl = _fuir.firstClazz(); cl <= _fuir.lastClazz(); cl++)
      {
        var bn = _fuir.clazzBaseName(cl);
        if (!_simpleBaseNames.containsKey(bn))
          {
            var mbn = mangle(bn);
            var conflict = back.get(mbn);
            if (conflict != null)
              { // in case of conflict, prefix with "_C<cl>_", no mangled named would ever start with "_C", so there is no conflict.
                mbn = "_C" + cl + "_" + mbn;
              }
            back.put(mbn, bn);
            _simpleBaseNames.put(bn, mbn);
          }
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  private String baseName(int cl)
  {
    return _simpleBaseNames.get(_fuir.clazzBaseName(cl));
  }


  static String mangle(String s)
  {
    var maxd = 0;
    var d = 0;
    final int length = s.length();
    for (int cp, off = 0; off < length; off += Character.charCount(cp))
      {
        cp = s.codePointAt(off);
        if (cp == '(')
          {
            d++;
            maxd = Math.max(maxd, d);
          }
        else if (cp == ')')
          {
            d--;
          }
      }

    d = 0;
    StringBuilder sb = new StringBuilder();
    for (int cp, off = 0; off < length; off += Character.charCount(cp))
      {
        cp = s.codePointAt(off);

        var ok =
          (off == 0 && Character.isJavaIdentifierStart(cp) && (cp != '_') ||
           off >  0 && Character.isJavaIdentifierPart (cp)    );

        if (ok)
          {
            sb.appendCodePoint(cp);
          }
        else if ('_' == cp) { sb.append("_u"); }  // we use '_' at the beginning for special uses, so replace it by _u at beginning
        else if ('.' == cp) { sb.append(off == 0 ? "_u" : "_"); }
        else if (',' == cp) { sb.append(off == 0 ? "_u" : "_"); }
        else if (' ' == cp) { for (var i=d; i<=maxd; i++) { sb.append("_"); };  }
        else if ('+' == cp) { sb.append("plus"); }
        else if ('-' == cp) { sb.append("minus"); }
        else if ('*' == cp) { sb.append("times"); }
        else if ('/' == cp) { sb.append("divide"); }
        else if ('<' == cp) { sb.append("lt"); }
        else if ('>' == cp) { sb.append("gt"); }
        else if ('=' == cp) { sb.append("eq"); }
        else if ('!' == cp) { sb.append("not"); }
        else if ('^' == cp) { sb.append("caret"); }
        else if ('(' == cp) { d++; }
        else if (')' == cp) { d--; }
        else if ('?' == cp) { sb.append("question"); }
        else if (':' == cp) { sb.append("colon"); }
        else if ('&' == cp) { sb.append("AND"); }
        else if ('|' == cp) { sb.append("OR"); }
        else if ('$' == cp) { sb.append("DOLLAR"); }
        else if ('%' == cp) { sb.append("PERCENT"); }
        else if ('°' == cp) { sb.append("DEGREE"); }
        else if ('[' == cp) { }  // convert "index []" just into "index "
        else if (']' == cp) { }
        else if ('#' == cp &&
                 off < length &&
                 s.codePointAt(off + Character.charCount(cp)) == '^')
                            { sb.append("OUTER_"); off++; }  // join #^ used for outer refs
        else if ('#' == cp) { sb.append("INTERN_"); }
        else
          {
            sb.append("_U").append(Integer.toHexString(cp)).append("_");
          }
      }
    return sb.toString();
  }


  /**
   * Name of the Java class for the clazz.
   *
   * @param cl clazz id
   *
   * @return name to be used for Java class
   */
  String javaClass(int cl)
  {
    var res = _classNames.get(cl);
    return res;
  }


  /**
   * Name of the Java interface for the clazz.
   *
   * @param cl clazz id
   *
   * @return name to be used for Java class
   */
  String javaInterface(int cl)
  {
    return _interfaceNames.get(cl);
  }


  /**
   * Name of the Java function of the given clazz.
   *
   * @param cl clazz id
   *
   * @return the Java name
   */
  String function(int cl)
  {
    return _fuir.clazzKind(cl) != FUIR.FeatureKind.Intrinsic ? ROUTINE_NAME
                                                             : mangle(_fuir.clazzOriginalName(cl));
  }


  /**
   * Name of the dynamic function of the given clazz.  This function will be
   * declared in cl's outer clazz' interface.
   *
   * @param cl clazz id
   */
  String dynamicFunction(int cl)
  {
    return DYNAMIC_FUNCTION_PREFIX + _fuir.clazzId2num(cl) + "_" + baseName(cl);
  }


  /**
   * Get the name of a field
   *
   * @param field the field id
   */
  String field(int field)
  {
    return _fieldNames.get(field);
  }



  String choiceUnitAsRef(int tagNum)
  {
    return CHOICE_UNIT_AS_REF_PREFIX + tagNum;
  }


  String getTag(int choiceType)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzIsChoice(choiceType));

    return GET_TAG + _fuir.clazzId2num(choiceType);
  }


  /**
   * For a choice of general kind, get the field name that holds the value for
   * the given tag number.
   *
   * @param cc the choice clazz
   *
   * @param tagNum a tag.
   */
  String choiceEntryName(int cc, int tagNum)
  {
    if (PRECONDITIONS) check
      (// NYI: CLEANUP: _types not available here:  _types.choiceKind(cc) == Types.ChoiceImplementations.general,
       tagNum >= 0,
       tagNum < _fuir.clazzNumChoices(cc));

    var tc = _fuir.clazzChoice(cc, tagNum);
    return _fuir.clazzIsRef(tc) ? CHOICE_REF_ENTRY_NAME
                                : CHOICE_ENTRY_NAME + tagNum;
  }


  /**
   * Get the field name for a static field in fzC_universe to hold a constant
   * with given type and data.
   *
   * @param constCl the clazz of the type of the constant.
   *
   * @param data the constant value in serialized form
   *
   * @return the name of the (new or existing) static field to be declared to
   * hold this constant.
   */
  String preallocatedConstantField(int constCl, byte[] data)
  {
    var c = new PreallocatedConstant(constCl, data);
    var result = _preallocatedConstantFieldNames.get(c);
    if (result == null)
      {
        result = PREALLOCATED_CONSTANT_PREFIX + _preallocatedConstantFieldNames.size() + "_" + _classNames.get(constCl);
        _preallocatedConstantFieldNames.put(c, result);
      }
    return result;
  }

}

/* end of file */
