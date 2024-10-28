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
 * Source of class Types
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm;

import dev.flang.fuir.FUIR;


import dev.flang.be.jvm.classfile.ClassFile;
import dev.flang.be.jvm.classfile.ClassFileConstants;
import dev.flang.be.jvm.classfile.Expr;
import dev.flang.be.jvm.classfile.VerificationType;

import dev.flang.util.ANY;
import dev.flang.util.FuzionOptions;
import dev.flang.util.List;

import java.util.TreeMap;


/**
 * Types provides methods to handle types
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Types extends ANY implements ClassFileConstants
{


  /*----------------------------  variables  ----------------------------*/


  private final FuzionOptions _opt;


  /**
   * The intermediate code we are compiling.
   */
  private final FUIR _fuir;


  private final Names _names;


  final Choices _choices;

  private final TreeMap<Integer, ClassFile> _classFiles = new TreeMap<>();

  private final TreeMap<Integer, ClassFile> _interfaceFiles = new TreeMap<>();

  JavaType UNIVERSE_TYPE;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create instance of Types
   */
  public Types(JVMOptions opt, FUIR fuir, Names names)
  {
    this._opt = opt;
    this._fuir = fuir;
    this._names = names;
    this._choices = new Choices(fuir, names, this);
  }


  /*-----------------------------  methods  -----------------------------*/



  /**
   * Find the order in which the clazzes have to be declared to avoid C compiler
   * from complaining, i.e., all struct and union elements before the
   * surrounding structures.
   *
   * @return A list of all clazzes in the order they should be declared.
   */
  List<Integer> inOrder()
  {
    var result = new List<Integer>();
    for (var cl = _fuir.firstClazz(); cl <= _fuir.lastClazz(); cl++)
      {
        result.add(cl);
      }
    return result;
  }


  /**
   * Create class declaration if required for the given clazz.
   *
   * @param cl a clazz id.
   */
  void createClassFile(int cl)
  {
    if (hasClassFile(cl))
      {
        var cn = _names.javaClass(cl);
        var cf = new ClassFile(_opt, cn, Names.ANY_CLASS, _fuir.clazzSrcFile(cl));
        _classFiles.put(cl, cf);

        if (cl == _fuir.clazzUniverse())
          {
            cf.field(ACC_PUBLIC | ACC_STATIC,
                     Names.UNIVERSE_FIELD,
                     UNIVERSE_TYPE.descriptor(),
                     new List<>());
          }

        var sig = "()V";
        var initLocals = new List<VerificationType>(VerificationType.UninitializedThis);
        var cod = Expr.UNIT;
        if (_fuir.clazzIsBoxed(cl))
          {
            var vcl = _fuir.clazzAsValue(cl);
            if (CHECKS) check
              (!_fuir.clazzIsVoidType(vcl));   // would be strange to box `void`.

            var rt = javaType(cl);
            var vt = resultType(vcl);
            if (vt != PrimitiveType.type_void)
              {
                cf.field(ACC_PUBLIC,
                         Names.BOXED_VALUE_FIELD_NAME,
                         vt.descriptor(),
                         new List<>());
                sig = "(" + vt.argDescriptor() + ")V";
                initLocals = addToLocals(initLocals, vt);
                cod = rt.load(0)
                  .andThen(vt.load(1))
                  .andThen(Expr.putfield(cn, Names.BOXED_VALUE_FIELD_NAME, vt));
              }
            var bc_box = Expr.new0(cn, rt)
              .andThen(Expr.DUP)
              .andThen(vt.load(0))
              .andThen(Expr.invokeSpecial(cn, "<init>", sig))
              .andThen(rt.return0());
            var code_box = cf.codeAttribute(Names.BOX_METHOD_NAME + " in " + _fuir.clazzAsString(cl), bc_box, new List<>(), ClassFile.StackMapTable.empty(cf, addToLocals(new List<>(), vt), bc_box));
            cf.method(ACC_PUBLIC | ACC_STATIC,
                      Names.BOX_METHOD_NAME,
                      boxSignature(cl),
                      new List<>(code_box));
          }
        var bc_init = Expr.aload(0, javaType(cl), VerificationType.UninitializedThis)
          .andThen(Expr.invokeSpecial(cf._super,"<init>","()V"))
          .andThen(cod)
          .andThen(Expr.RETURN);
        var code_init = cf.codeAttribute("<init> in " + _fuir.clazzAsString(cl), bc_init, new List<>(), ClassFile.StackMapTable.empty(cf, initLocals, bc_init));

        cf.method(ACC_PUBLIC, "<init>", sig, new List<>(code_init));

        if (cl == _fuir.clazzUniverse())
          {
            cf.addImplements(Names.MAIN_INTERFACE);
            var maincl = _fuir.mainClazzId();
            var bc_run =
              Expr.UNIT
              .andThen(invokeStatic(maincl, -1)).drop()
              .andThen(Expr.RETURN);
            var code_run = cf.codeAttribute(Names.MAIN_RUN + " in " + _fuir.clazzAsString(cl), bc_run, new List<>(), ClassFile.StackMapTable.empty(cf, new List<>(VerificationType.UninitializedThis), bc_run));
            cf.method(ACC_PUBLIC, Names.MAIN_RUN, "()V", new List<>(code_run));

            var bc_main =
              Expr.aload(0, JAVA_LANG_STRING.array())
              .andThen(Expr.putstatic(Names.RUNTIME_CLASS, Names.RUNTIME_ARGS, JAVA_LANG_STRING.array()))
              .andThen(Expr.new0(cn, javaType(cl)))
              .andThen(Expr.DUP)
              .andThen(Expr.invokeSpecial(cn, "<init>", "()V"))
              .andThen(Expr.invokeStatic(Names.RUNTIME_CLASS, Names.RUNTIME_RUN, "(" + new ClassType(Names.MAIN_INTERFACE).argDescriptor() + ")V", PrimitiveType.type_void))
              .andThen(Expr.RETURN);
            var code_main = cf.codeAttribute("main in " + _fuir.clazzAsString(cl), bc_main, new List<>(), ClassFile.StackMapTable.empty(cf, new List<>(JAVA_LANG_STRING.array().vti()), bc_main));
            cf.method(ACC_STATIC | ACC_PUBLIC, "main", "([Ljava/lang/String;)V", new List<>(code_main));
          }
      }
  }


  /**
   * Create the Java type descriptor for cl's box method.
   *
   * @param cl a boxed clazz
   *
   * @return A string like "(I)LfzC__Ri32;", the signature of the method to box
   * _fuir.clazzAsValue(cl) into cl.
   */
  String boxSignature(int cl)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzIsBoxed(cl));

    var vcl = _fuir.clazzAsValue(cl);
    var arg = javaType(vcl).argDescriptor();
    return "(" + arg + ")" + javaType(cl).descriptor();
  }


  Expr invokeStatic(int cc, int line)
  {
    var callingIntrinsic = _fuir.clazzKind(cc) == FUIR.FeatureKind.Intrinsic;
    var cls   = callingIntrinsic ? Names.RUNTIME_INTRINSICS_CLASS
                                 : _names.javaClass(cc);
    var fname = callingIntrinsic ? _names.function(cc)
                                 : Names.ROUTINE_NAME;
    return Expr.invokeStatic(cls,
                             fname,
                             descriptor(cc),
                             resultType(_fuir.clazzResultClazz(cc)),
                             line);
  }


  boolean hasClassFile(int cl)
  {
    return _fuir.clazzIsBoxed(cl) ||
      switch (_fuir.clazzKind(cl))
      {
      case Abstract  -> false;
      case Choice    ->
          switch (_choices.kind(cl))
            {
            case voidlike, unitlike, boollike, intlike, nullable -> false;
            case refsAndUnits, general                           -> true;
            };
      case Routine   -> true; // NYI: UNDER DEVELOPMENT: clazzNeedsCode(cl);
      case Intrinsic -> true;
      default        -> false;
      };
  }


  boolean clazzNeedsCode(int cl)
  {
    return _fuir.clazzNeedsCode(cl) ||
      cl == _fuir.clazz_Const_String() ||
      cl == _fuir.clazz_Const_String_utf8_data() ||
      cl == _fuir.clazz_array_u8() ||
      cl == _fuir.clazz_fuzionSysArray_u8() ||
      cl == _fuir.clazz_fuzionSysArray_u8_data() ||
      cl == _fuir.clazz_fuzionSysArray_u8_length();
  }


  /**
   * Create class interface declaration for given class.
   *
   * @param cl a clazz id.
   */
  private void makeInterface(int cl)
  {
    var i = new ClassFile(_opt, _names.javaInterface(cl), "java/lang/Object", true, _fuir.clazzSrcFile(cl));
    _interfaceFiles.put(cl, i);
    if (!_fuir.clazzIsChoice(cl))
      {
        var hs = _fuir.clazzInstantiatedHeirs(cl);
        for (var h : hs)
          {
            var c = classFile(h);
            if (c != null)
              {
                c.addImplements(i._name);
              }
          }
      }
  }


  /**
   * Does this clazz have instantiated heirs other than cl itself?
   */
  boolean hasRealHeirs(int cl)
  {
    var hs = _fuir.clazzInstantiatedHeirs(cl);
    return hs.length > 1 || hs.length == 1 && hs[0] != cl;
  }


  /**
   * Get class file if required for the given clazz.
   *
   * @param cl a clazz id.
   *
   * @return the corresponding class file.
   */
  ClassFile classFile(int cl)
  {
    return _classFiles.get(cl);
  }


  /**
   * check if an interface class file was generated for the given clazz.
   *
   * @param cl a clazz id.
   *
   * @return true if an interface was created for cl.
   */
  boolean hasInterfaceFile(int cl)
  {
    return _interfaceFiles.get(cl) != null;
  }



  /**
   * Get interface class file if required for the given clazz, create one on demand if
   * this requirement was only detected now.
   *
   * @param cl a clazz id.
   *
   * @return the corresponding interface class file, null if not needed.
   */
  ClassFile interfaceFile(int cl)
  {
    var result = _interfaceFiles.get(cl);
    if (result == null)
      {
        makeInterface(cl);
        result = _interfaceFiles.get(cl);
      }
    if (POSTCONDITIONS) ensure
      (result != null);
    return result;
  }


  /**
   * Does the given clazz specify a scalar type in the C code, i.e, standard
   * numeric types i32, u64, etc.
   */
  boolean isScalar(int cl)
  {
    var id = _fuir.getSpecialClazz(cl);
    return switch (id)
      {
      case
        c_i8  , c_i16 , c_i32 ,
        c_i64 , c_u8  , c_u16 ,
        c_u32 , c_u64 , c_f32 ,
        c_f64                   -> true;
      default                   -> false;
      };
  }


  /**
   * Get the Java name of the given clazz type: "I" for i32, "LfzC_featureName;"
   * for a non-scalar.
   *
   * @return the Java name for type cl.
   */
  JavaType javaType(int cl)
  {
    var id = _fuir.getSpecialClazz(cl);
    return switch (id)
      {
      case c_bool    -> PrimitiveType.type_boolean;
      case c_i8      -> PrimitiveType.type_byte;
      case c_i16     -> PrimitiveType.type_short;
      case c_i32     -> PrimitiveType.type_int;
      case c_i64     -> PrimitiveType.type_long;
      case c_u8      -> PrimitiveType.type_byte;
      case c_u16     -> PrimitiveType.type_char;
      case c_u32     -> PrimitiveType.type_int;
      case c_u64     -> PrimitiveType.type_long;
      case c_f32     -> PrimitiveType.type_float;
      case c_f64     -> PrimitiveType.type_double;
      case c_sys_ptr -> JAVA_LANG_OBJECT;
      default        ->
        {
          if (cl == _fuir.clazzUniverse()                        ||
              !_fuir.clazzIsRef(cl) && _fuir.clazzIsUnitType(cl) ||
              _fuir.clazzIsVoidType(cl)  // Java's void is not really the same, but Java does not have a real 'void' type.
              )
            {
              yield PrimitiveType.type_void;
            }
          else if (_fuir.clazzIsBoxed(cl))  // NYI: CLEANUP: for a boxed choice, _fuir.clazzIsChoice(cl) is true, but should better be false
            {
              yield new ClassType(_names.javaClass(cl)); // NYI: OPTIMIZATION: caching!
            }
          else if (_fuir.clazzIsChoice(cl))
            {
              yield _choices.javaType(cl);
            }
          else
            {
              yield new ClassType(_names.javaClass(cl)); // NYI: OPTIMIZATION: caching!
            }
        }
      };
  }


  /**
   * Get the JavaType for the given clazz type if used as a result of a feature,
   * a field or array element to store data.  In these cases, ref types are
   * replaced by the corresponding interface to permit multiple inheritance.
   *
   * @return the Java name for type cl when used for a field.
   */
  JavaType resultType(int cl)
  {
    if (_fuir.clazzIsRef(cl) && hasRealHeirs(cl))
      {
        return interfaceFile(cl).classType();
      }
    else
      {
        return javaType(cl);
      }
  }


  boolean hasOuterRef(int cl)
  {
    var or = _fuir.clazzOuterRef(cl);
    return or != -1 && !_fuir.clazzIsUnitType(_fuir.clazzResultClazz(or));
  }


  /**
   * Get the signature descriptor string for calling cl
   *
   * @param explicitOuter true if the target instance is required (for Java
   * dynamic binding) even if the called clazz does not need it.
   *
   * @param cl the called clazz
   */
  String descriptor(boolean explicitOuter, int cl)
  {
    var as = new StringBuilder();
    as.append("(");
    if (explicitOuter && hasOuterRef(cl))
      {
        var or = _fuir.clazzOuterRef(cl);
        var ot = _fuir.clazzResultClazz(or);
        var at = resultType(ot);
        if (at != PrimitiveType.type_void)
          {
            as.append(at.descriptor());
          }
      }
    for (var ai = 0; ai < _fuir.clazzArgCount(cl); ai++)
      {
        var at = _fuir.clazzArgClazz(cl, ai);
        var ft = resultType(at);
        if (ft != PrimitiveType.type_void)
          {
            as.append(ft.descriptor());
          }
      }
    as.append(")")
      .append(resultType(_fuir.clazzResultClazz(cl)).descriptor());

    return as.toString();
  }


  /**
   * Get the signature descriptor string for calling cl
   *
   * @param cl the called clazz
   */
  String descriptor(int cl)
  {
    return descriptor(true /* NYI: CLEANUP: this seems the wrong way around */, cl);
  }

  String dynDescriptor(int cl)
  {
    return descriptor(false /* NYI: CLEANUP: this seems the wrong way around */, cl);
  }


  /**
   * Add `jt` to the list of locals.
   * If `jt` is javaVoid-like it is not added.
   * longs and doubles are added twice.
   *
   * @param locals
   * @param jt
   * @return
   */
  public static List<VerificationType> addToLocals(List<VerificationType> locals, JavaType jt)
  {
    if (jt != PrimitiveType.type_void)
      {
        var vti = jt.vti();
        if (vti.needsTwoSlots())
          {
            locals.addAll(vti, vti);
          }
        else
          {
            locals.add(vti);
          }
      }
    return locals;
  }

}

/* end of file */
