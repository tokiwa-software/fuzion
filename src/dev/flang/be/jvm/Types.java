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

import dev.flang.util.ANY;
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


  /**
   * The intermediate code we are compiling.
   */
  private final FUIR _fuir;


  private final Names _names;


  final Choices _choices;

  private final TreeMap<Integer, ClassFile> _classFiles = new TreeMap<>();

  private final TreeMap<Integer, ClassFile> _interfaceFiles = new TreeMap<>();

  private boolean[] _boxed;

  JavaType UNIVERSE_TYPE;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create instance of Types
   */
  public Types(FUIR fuir, Names names)
  {
    this._fuir = fuir;
    this._names = names;
    this._choices = new Choices(fuir, names, this);
    this._boxed = new boolean[_fuir.lastClazz() - _fuir.firstClazz() + 1];
    for (var cl = _fuir.firstClazz(); cl <= _fuir.lastClazz(); cl++)
      {
        if (_fuir.clazzIsBoxed(cl))
          {
            _boxed[_fuir.clazzAsValue(cl) - _fuir.firstClazz()] = true;
          }
      }
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
        var cf = new ClassFile(cn, Names.ANY_CLASS);
        _classFiles.put(cl, cf);

        if (cl == _fuir.clazzUniverse())
          {
            cf.field(ACC_PUBLIC | ACC_STATIC,
                     Names.UNIVERSE_FIELD,
                     UNIVERSE_TYPE.descriptor(),
                     new List<>());
          }

        var sig = "()V";
        var cod = Expr.UNIT;
        if (_fuir.clazzIsBoxed(cl))
          {
            var vcl = _fuir.clazzAsValue(cl);
            if (CHECKS) check
              (!_fuir.clazzIsVoidType(vcl));   // would be strange to box `void`.

            var rt = javaType(cl);
            var vt = resultType(vcl);
            if (!_fuir.clazzIsUnitType(vcl))
              {
                cf.field(ACC_PUBLIC,
                         Names.BOXED_VALUE_FIELD_NAME,
                         vt.descriptor(),
                         new List<>());
                sig = "(" + vt.descriptor() + ")V";
                cod = rt.load(0)
                  .andThen(vt.load(1))
                  .andThen(Expr.putfield(cn, Names.BOXED_VALUE_FIELD_NAME, vt.descriptor()));
              }
            var bc_box = Expr.new0(cn, rt)
              .andThen(Expr.DUP)
              .andThen(vt.load(0))
              .andThen(Expr.invokeSpecial(cn, "<init>", sig))
              .andThen(rt.return0());
            var code_box = cf.codeAttribute(Names.BOX_METHOD_NAME + " in " + _fuir.clazzAsString(cl), bc_box, new List<>(), new List<>());
            cf.method(ACC_PUBLIC | ACC_STATIC,
                      Names.BOX_METHOD_NAME,
                      boxSignature(cl),
                      new List<>(code_box));
          }
        var bc_init = Expr.aload(0, javaType(cl))
          .andThen(Expr.invokeSpecial(cf._super,"<init>","()V"))
          .andThen(cod)
          .andThen(Expr.RETURN);
        var code_init = cf.codeAttribute("<init> in " + _fuir.clazzAsString(cl), bc_init, new List<>(), new List<>());

        cf.method(ACC_PUBLIC, "<init>", sig, new List<>(code_init));

        if (cl == _fuir.clazzUniverse())
          {
            var maincl = _fuir.mainClazzId();
            var bc_main =
              Expr.aload(0, JAVA_LANG_STRING.array())
              .andThen(Expr.putstatic(Names.RUNTIME_CLASS, Names.RUNTIME_ARGS, JAVA_LANG_STRING.array().descriptor()))
              .andThen(_fuir.hasPrecondition(maincl) ? invokeStatic(maincl, true) : Expr.UNIT)
              .andThen(invokeStatic(maincl, false)).drop()
              .andThen(Expr.RETURN);
            var code_main = cf.codeAttribute("main in " + _fuir.clazzAsString(cl), bc_main, new List<>(), new List<>());
            cf.method(ACC_STATIC | ACC_PUBLIC, "main", "([Ljava/lang/String;)V", new List<>(code_main));
          }
      }

    var ix = cl - _fuir.firstClazz();
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
    var arg = _fuir.clazzIsUnitType(vcl) ? ""
                                         : javaType(vcl).descriptor();
    return "(" + arg + ")" + javaType(cl).descriptor();
  }


  Expr invokeStatic(int cc, boolean preCalled)
  {
    var callingIntrinsic = !preCalled && _fuir.clazzKind(cc) == FUIR.FeatureKind.Intrinsic;
    var cls   = callingIntrinsic ? Names.RUNTIME_INTRINSICS_CLASS
                                 : _names.javaClass(cc);
    var fname = callingIntrinsic ? _names.function(cc, preCalled) :
                preCalled        ? Names.PRECONDITION_NAME
                                 : Names.ROUTINE_NAME;
    return Expr.invokeStatic(cls,
                             fname,
                             descriptor(cc, preCalled),
                             resultType(cc, preCalled));
  }



  boolean hasClassFile(int cl)
  {
    return _fuir.clazzIsBoxed(cl) ||
      switch (_fuir.clazzKind(cl))
      {
      case Choice    ->
          switch (_choices.kind(cl))
            {
            case voidlike, unitlike, boollike, intlike, nullable -> false;
            case refsAndUnits, general                           -> true;
            };
      case Routine   -> true; // NYI clazzNeedsCode(cl);
      case Intrinsic -> true; // NYI || _fuir.hasPrecondition(cl);
      default        -> false;
      };
  }


  int numUnitTypesInChoiceOfOnlyRefs(int cl)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzIsChoiceOfOnlyRefs(cl));

    int numUnitTypes = 0;
    for (var i = 0; i < _fuir.clazzNumChoices(cl); i++)
      {
        var tc = _fuir.clazzChoice(cl, i);
        if (!_fuir.clazzIsVoidType(tc) && !_fuir.clazzIsRef(tc))
          {
            if (CHECKS) check
              (_fuir.clazzIsUnitType(tc));
            numUnitTypes++;
          }
      }
    return numUnitTypes;
  }


  boolean clazzNeedsCode(int cl)
  {
    return _fuir.clazzNeedsCode(cl) ||
      cl == _fuir.clazz_Const_String() ||
      cl == _fuir.clazz_Const_String_internal_array() ||
      cl == _fuir.clazz_fuzionSysArray_u8() ||
      cl == _fuir.clazz_fuzionSysArray_u8_data() ||
      cl == _fuir.clazz_fuzionSysArray_u8_length() ||
      cl == _fuir.clazz_fuzionJavaObject() ||
      cl == _fuir.clazz_fuzionJavaObject_Ref();
  }


  boolean isChoiceOfOneRefAndOneUnitType(int cl)
  {
    var result = false;
    if (_fuir.clazzIsChoice(cl))
      {
        if (_fuir.clazzIsChoiceOfOnlyRefs(cl))
          {
            var nc = _fuir.clazzNumChoices(cl);
            var nu = numUnitTypesInChoiceOfOnlyRefs(cl);
            var nr = nc - nu; // num refs
            if (CHECKS) check
              (nr > 0);
            result = nu == 1 && nr == 1;
          }
      }
    return result;
  }


  /**
   * Create class interface declaration for given class.
   *
   * @param cl a clazz id.
   */
  private void makeInterface(int cl)
  {
    var i = new ClassFile(_names.javaInterface(cl), "java/lang/Object", true);
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
   * Do we need to declare a type for the given clazz? This is true for choice
   * and routine, but also for intrinsics if they have a pre-condition, since
   * the code for the pre-condition is generated and may access the instance
   * associated with cl.
   *
   * @param cl a clazz id.
   *
   * @return true iff a type and struct declaration is needed for cl.
   */
  private boolean needsTypeDeclaration(int cl)
  {
    return switch (_fuir.clazzKind(cl))
      {
      case Choice, Routine -> true; // !isScalar(cl); // special handling of stdlib clazzes known to the compiler
      case Intrinsic       -> true || _fuir.hasPrecondition(cl);
      default              -> false;
      };

  }


  /**
   * Does the given clazz specify a scalar type in the C code, i.e, standard
   * numeric types i32, u64, etc.
   */
  boolean isScalar(int cl)
  {
    var id = _fuir.getSpecialId(cl);
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
    var id = _fuir.getSpecialId(cl);
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
      case c_unit    -> PrimitiveType.type_void;
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
          else if (_fuir.clazzIsBoxed(cl))  // NYI: for a boxed choice, _fuir.clazzIsChoice(cl) is true, but should better be false
            {
              yield new ClassType(_names.javaClass(cl)); // NYI: caching!
            }
          else if (_fuir.clazzIsChoice(cl))
            {
              yield _choices.javaType(cl);
            }
          else
            {
              yield new ClassType(_names.javaClass(cl)); // NYI: caching!
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
   * Get the result type of a call to clazz cl or its precondition
   *
   * @param cl the called clazz
   *
   * @param pre true iff we call the precondition.
   */
  JavaType resultType(int cl, boolean pre)
  {
    var rt = _fuir.clazzResultClazz(cl);
    return pre ? PrimitiveType.type_void
               : resultType(rt);

  }


  /**
   * Get the signature descriptor string for calling cl or its precondition
   *
   * @param explicitOuter true if the target instance is required (for Java
   * dynamic binding) even if the called clazz does not need it.
   *
   * @param cl the called clazz
   *
   * @param pre true iff we call the precondition.
   */
  String descriptor(boolean explicitOuter, int cl, boolean pre)
  {
    var resultType = _fuir.clazzResultClazz(cl);
    var as = new StringBuilder();
    as.append("(");
    if (explicitOuter && hasOuterRef(cl))
      {
        var or = _fuir.clazzOuterRef(cl);
        var ot = _fuir.clazzResultClazz(or);
        as.append(resultType(ot).descriptor());
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
      .append(resultType(cl, pre).descriptor());

    return as.toString();
  }


  /**
   * Get the signature descriptor string for calling cl or its precondition
   *
   * @param cl the called clazz
   *
   * @param pre true iff we call the precondition.
   */
  String descriptor(int cl, boolean pre)
  {
    return descriptor(true /* NYI: this seems the wrong way around */, cl, pre);
  }

  String dynDescriptor(int cl, boolean pre)
  {
    return descriptor(false /* NYI: this seems the wrong way around */, cl, pre);
  }

  int dynDescriptorArgsCount(int cl, boolean pre)
  {
    int res = 1;
    for (var ai = 0; ai < _fuir.clazzArgCount(cl); ai++)
      {
        var at = _fuir.clazzArgClazz(cl, ai);
        var ft = resultType(at);
        res += ft.stackSlots();
      }
    return res;
  }


}

/* end of file */
