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
 * Source of class ClassFileConstants
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm.classfile;

import dev.flang.be.jvm.classfile.ClassFile.CPClass;
import dev.flang.be.jvm.classfile.VerificationTypeInfo.type;
import dev.flang.util.ANY;
import dev.flang.util.List;

import java.util.stream.Stream;


/**
 * ClassFileConstants provides constants used in a Java class file.
 *
 + Please refer to the Java Virtual Machine Specification for details,
 * latest version as time of writing available here
 *
 *   https://docs.oracle.com/javase/specs/jvms/se21/jvms21.pdf
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public interface ClassFileConstants
{


  /*----------------------------  constants  ----------------------------*/


  public static final byte[] MAGIC = new byte[] { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE };

  public static byte[] VERSION_JDK_1_0 = new byte[] { 0, 0, 0, 45 };
  public static byte[] VERSION_JDK_1_1 = new byte[] { 0, 3, 0, 45 };
  public static byte[] VERSION_JDK_1_2 = new byte[] { 0, 0, 0, 46 };
  public static byte[] VERSION_JDK_1_3 = new byte[] { 0, 0, 0, 47 };
  public static byte[] VERSION_JDK_1_4 = new byte[] { 0, 0, 0, 48 };
  public static byte[] VERSION_JDK_5   = new byte[] { 0, 0, 0, 49 };
  public static byte[] VERSION_JDK_6   = new byte[] { 0, 0, 0, 50 };
  public static byte[] VERSION_JDK_7   = new byte[] { 0, 0, 0, 51 };
  public static byte[] VERSION_JDK_8   = new byte[] { 0, 0, 0, 52 };   // LTS
  public static byte[] VERSION_JDK_9   = new byte[] { 0, 0, 0, 53 };
  public static byte[] VERSION_JDK_10  = new byte[] { 0, 0, 0, 54 };
  public static byte[] VERSION_JDK_11  = new byte[] { 0, 0, 0, 55 };   // LTS
  public static byte[] VERSION_JDK_12  = new byte[] { 0, 0, 0, 56 };
  public static byte[] VERSION_JDK_13  = new byte[] { 0, 0, 0, 57 };
  public static byte[] VERSION_JDK_14  = new byte[] { 0, 0, 0, 58 };
  public static byte[] VERSION_JDK_15  = new byte[] { 0, 0, 0, 59 };
  public static byte[] VERSION_JDK_16  = new byte[] { 0, 0, 0, 60 };
  public static byte[] VERSION_JDK_17  = new byte[] { 0, 0, 0, 61 };   // LTS
  public static byte[] VERSION_JDK_18  = new byte[] { 0, 0, 0, 62 };
  public static byte[] VERSION_JDK_19  = new byte[] { 0, 0, 0, 63 };
  public static byte[] VERSION_JDK_20  = new byte[] { 0, 0, 0, 64 };
  public static byte[] VERSION_JDK_21  = new byte[] { 0, 0, 0, 65 };   // LTS


  // public static byte[] DEFAULT_VERSION = VERSION_JDK_5;  // NYI: should be LTS version 17, using 5 only to avoid need for stack frame info entries
  public static byte[] DEFAULT_VERSION = VERSION_JDK_7;

  public enum CPoolTag
  {
    tag_utf8                ( 1),
    tag_unicode             ( 2),
    tag_integer             ( 3),
    tag_float               ( 4),
    tag_long                ( 5),
    tag_double              ( 6),
    tag_class               ( 7),
    tag_string              ( 8),
    tag_field_ref           ( 9),
    tag_method_ref          (10),
    tag_interface_method_ref(11),
    tag_name_and_type       (12),
    // unused 13
    // unused 14
    tag_method_handle       (15),  // since JDK_7
    tag_method_type         (16),  // since JDK_7
    tag_dynamic             (17),  // since JDK_11
    tag_invoke_dynamic      (18),  // since JDK_7
    tag_module              (19),  // since JDK_9
    tag_package             (20);  // since JDK_9

    final int _tag;

    CPoolTag(int t)
    {
      _tag = t;
    }
  };


  /**
   * JavaType provides an abstract of type sin the JVM and a basis for user
   * defined types that are useful for generating bytecode.
   */
  public static interface JavaType
  {
    Expr load(int index, ClassFile cf);
    Expr store(int index, ClassFile cf);
    Expr return0();
    Expr newArray();
    Expr xaload();
    Expr xastore();
    Expr pop();
    JavaType array();
    int stackSlots();

    /**
     * descriptor string, "Ljava/lang/Object;", "I", "[[B", etc.
     */
    String descriptor();

    /**
     * descriptor string if we know this is a reference: "java/lang/Object", "[I", etc.
     */
    String refDescriptor();

    /**
     * Descriptor if used as method argument. This is "" for void, otherwise same
     * as descriptor().
     */
    default String argDescriptor() { return descriptor(); }

    String className();

    // null for type void
    VerificationTypeInfo vti(ClassFile cf);
  }


  public enum PrimitiveType implements JavaType
  {
    type_void    ("V", "V", 0)
    {
      public Expr load(int index, ClassFile cf)
      {
        return Expr.UNIT;
      }
      public Expr store(int index, ClassFile cf)
      {
        return Expr.UNIT;
      }
      public Expr return0()
      {
        return Expr.RETURN;
      }
      public Expr newArray()
      { // void[] is just null
        return Expr.POP.andThen(Expr.ACONST_NULL);
      }
      public Expr xaload()
      {
        return Expr.POP.andThen(Expr.POP);
      }
      public Expr xastore()
      {
        return Expr.POP.andThen(Expr.POP);
      }
      public Expr pop()
      {
        return Expr.UNIT;
      }

      /**
       * Arguments of void type are just dropped, so argDescriptor is redefined to return
       * empty String.
       */
      public String argDescriptor() { return ""; }

      public String className() { return "void"; }
      public JavaType array()
      { // void[] type is java.lang.Object
        return JAVA_LANG_OBJECT;
      }
      public VerificationTypeInfo vti(ClassFile cf)
      {
        return null;
      }
    },
    type_int     ("I", "I", 1)
    {
      public Expr load(int index, ClassFile cf)
      {
        return Expr.iload(index);
      }
      public Expr store(int index, ClassFile cf)
      {
        return Expr.istore(index);
      }
      public Expr return0()
      {
        return Expr.IRETURN;
      }
      public Expr newArray()
      {
        return Expr.INEWARRAY;
      }
      public Expr xaload()
      {
        return Expr.IALOAD;
      }
      public Expr xastore()
      {
        return Expr.IASTORE;
      }
      public Expr pop()
      {
        return Expr.POP;
      }
      public String className() { return "int"; }
      public VerificationTypeInfo vti(ClassFile cf)
      {
        return VerificationTypeInfo.Integer;
      }
    },
    type_byte    ("B", "I", 1)
    {
      public Expr load(int index, ClassFile cf)
      {
        return Expr.iload(index);
      }
      public Expr store(int index, ClassFile cf)
      {
        return Expr.istore(index);
      }
      public Expr return0()
      {
        return Expr.IRETURN;
      }
      public Expr newArray()
      {
        return Expr.BNEWARRAY;
      }
      public Expr xaload()
      {
        return Expr.BALOAD;
      }
      public Expr xastore()
      {
        return Expr.BASTORE;
      }
      public Expr pop()
      {
        return Expr.POP;
      }
      public String className() { return "byte"; }
      public VerificationTypeInfo vti(ClassFile cf)
      {
        return VerificationTypeInfo.Integer;
      }
    },
    type_short   ("S", "I", 1)
    {
      public Expr load(int index, ClassFile cf)
      {
        return Expr.iload(index);
      }
      public Expr store(int index, ClassFile cf)
      {
        return Expr.istore(index);
      }
      public Expr return0()
      {
        return Expr.IRETURN;
      }
      public Expr newArray()
      {
        return Expr.SNEWARRAY;
      }
      public Expr xaload()
      {
        return Expr.SALOAD;
      }
      public Expr xastore()
      {
        return Expr.SASTORE;
      }
      public Expr pop()
      {
        return Expr.POP;
      }
      public String className() { return "short"; }
      public VerificationTypeInfo vti(ClassFile cf)
      {
        return VerificationTypeInfo.Integer;
      }
    },
    type_char    ("C", "I", 1)
    {
      public Expr load(int index, ClassFile cf)
      {
        return Expr.iload(index);
      }
      public Expr store(int index, ClassFile cf)
      {
        return Expr.istore(index);
      }
      public Expr return0()
      {
        return Expr.IRETURN;
      }
      public Expr newArray()
      {
        return Expr.CNEWARRAY;
      }
      public Expr xaload()
      {
        return Expr.CALOAD;
      }
      public Expr xastore()
      {
        return Expr.CASTORE;
      }
      public Expr pop()
      {
        return Expr.POP;
      }
      public String className() { return "char"; }
      public VerificationTypeInfo vti(ClassFile cf)
      {
        return VerificationTypeInfo.Integer;
      }
    },
    type_long    ("J", "J", 2)
    {
      public Expr load(int index, ClassFile cf)
      {
        return Expr.lload(index);
      }
      public Expr store(int index, ClassFile cf)
      {
        return Expr.lstore(index);
      }
      public Expr return0()
      {
        return Expr.LRETURN;
      }
      public Expr newArray()
      {
        return Expr.LNEWARRAY;
      }
      public Expr xaload()
      {
        return Expr.LALOAD;
      }
      public Expr xastore()
      {
        return Expr.LASTORE;
      }
      public Expr pop()
      {
        return Expr.POP2;
      }
      public String className() { return "long"; }
      public VerificationTypeInfo vti(ClassFile cf)
      {
        return VerificationTypeInfo.Long;
      }
    },
    type_float   ("F", "F", 1)
    {
      public Expr load(int index, ClassFile cf)
      {
        return Expr.fload(index);
      }
      public Expr store(int index, ClassFile cf)
      {
        return Expr.fstore(index);
      }
      public Expr return0()
      {
        return Expr.FRETURN;
      }
      public Expr newArray()
      {
        return Expr.FNEWARRAY;
      }
      public Expr xaload()
      {
        return Expr.FALOAD;
      }
      public Expr xastore()
      {
        return Expr.FASTORE;
      }
      public Expr pop()
      {
        return Expr.POP;
      }
      public String className() { return "float"; }
      public VerificationTypeInfo vti(ClassFile cf)
      {
        return VerificationTypeInfo.Float;
      }
    },
    type_double  ("D", "D", 2)
    {
      public Expr load(int index, ClassFile cf)
      {
        return Expr.dload(index);
      }
      public Expr store(int index, ClassFile cf)
      {
        return Expr.dstore(index);
      }
      public Expr return0()
      {
        return Expr.DRETURN;
      }
      public Expr newArray()
      {
        return Expr.DNEWARRAY;
      }
      public Expr xaload()
      {
        return Expr.DALOAD;
      }
      public Expr xastore()
      {
        return Expr.DASTORE;
      }
      public Expr pop()
      {
        return Expr.POP2;
      }
      public String className() { return "double"; }
      public VerificationTypeInfo vti(ClassFile cf)
      {
        return VerificationTypeInfo.Double;
      }
    },
    type_boolean ("Z", "I", 1)
    {
      public Expr load(int index, ClassFile cf)
      {
        return Expr.iload(index);
      }
      public Expr store(int index, ClassFile cf)
      {
        return Expr.istore(index);
      }
      public Expr return0()
      {
        return Expr.IRETURN;
      }
      public Expr newArray()
      {
        return Expr.ZNEWARRAY;
      }
      public Expr xaload()
      {
        return Expr.BALOAD;
      }
      public Expr xastore()
      {
        return Expr.BASTORE;
      }
      public Expr pop()
      {
        return Expr.POP;
      }
      public String className() { return "boolean"; }
      public VerificationTypeInfo vti(ClassFile cf)
      {
        return VerificationTypeInfo.Integer;
      }
    };


    final String _descriptor;
    final String _refDescriptor;
    final int _stackSlots;

    PrimitiveType(String desc, String refDesc, int stackSlots)
    {
      _descriptor = desc;
      _refDescriptor = refDesc;
      _stackSlots = stackSlots;
    }

    public int stackSlots()
    {
      return _stackSlots;
    }

    public String descriptor()
    {
      return _descriptor;
    }

    public String refDescriptor()
    {
      throw new Error("JavaType.refDescriptor only defined for ref type, not for " + this);
    }

    public Expr load(int index, ClassFile cf)
    {
      throw new Error("NYI: load for type " + this);
    }

    public Expr store(int index)
    {
      throw new Error("NYI: store for type " + this);
    }

    public Expr return0()
    {
      throw new Error("NYI: return for type " + this);
    }

    public Expr newArray()
    {
      throw new Error("NYI: newArray for type " + this);
    }

    public Expr xaload()
    {
      throw new Error("NYI: array load for type " + this);
    }

    public Expr xastore()
    {
      throw new Error("NYI: array load for type " + this);
    }

    public Expr pop()
    {
      throw new Error("NYI: pop for type " + this);
    }

    public String className()
    {
      throw new Error("NYI: className for type " + this);
    }

    public JavaType array()
    {
      return new ArrayType(this);
    }
  }

  /* a class or array */
  public static abstract class AType implements JavaType
  {
    final String _descriptor;
    AType(String descriptor)
    {
      _descriptor = descriptor;
    }

    public int stackSlots()
    {
      return 1;
    }

    public String descriptor()
    {
      return _descriptor;
    }

    public String refDescriptor()
    {
      return descriptor();
    }

    public Expr load(int index, ClassFile cf)
    {
      return Expr.aload(index, this, vti(cf));
    }

    public Expr store(int index, ClassFile cf)
    {
      return Expr.astore(index, vti(cf));
    }

    public Expr return0()
    {
      return Expr.ARETURN;
    }

    public Expr newArray()
    {
      return Expr.anewarray(this);
    }

    public Expr xaload()
    {
      return Expr.aaload(this);
    }

    public Expr xastore()
    {
      return Expr.AASTORE;
    }

    public Expr pop()
    {
      return Expr.POP;
    }

    public AType array()
    {
      return new ArrayType(this);
    }

    public String toString()
    {
      return "ClassType('" + _descriptor + "')";
    }
    public int cpIndex(ClassFile cf)
    {
      return cf.cpClass(this).index();
    }
    public VerificationTypeInfo vti(ClassFile cf)
    {
      return new VerificationTypeInfo(VerificationTypeInfo.type.Object, cpIndex(cf));
    }

  }

  public static class ClassType extends AType
  {
    final String _className;
    public ClassType(String className)
    {
      super("L" + className + ";");

      if (ANY.PRECONDITIONS) ANY.require
        (!className.startsWith("["));

      _className = className;
    }

    public String className()
    {
      return _className;
    }

    public String refDescriptor()
    {
      return className();
    }

    public boolean sameAs(ClassType other)
    {
      return _className.equals(other._className);
    }

  }


  static ClassType JAVA_LANG_CLASS  = new ClassType("java/lang/Class");
  static ClassType JAVA_LANG_OBJECT = new ClassType("java/lang/Object");
  static ClassType JAVA_LANG_STRING = new ClassType("java/lang/String");

  static ClassType NULL_TYPE = new ClassType("java/lang/Object");

  static class ArrayType extends AType
  {
    final JavaType _elementType;
    ArrayType(JavaType elementType)
    {
      super("[" + elementType.descriptor());

      _elementType = elementType;
    }

    public String className()
    {
      return _elementType.className() + "[]";
    }
  }


  static PrimitiveType primitiveType(String s)
  {
    return switch (s)
      {
        case "V" -> PrimitiveType.type_void;
        case "B" -> PrimitiveType.type_byte;
        case "S" -> PrimitiveType.type_short;
        case "C" -> PrimitiveType.type_char;
        case "I" -> PrimitiveType.type_int;
        case "J" -> PrimitiveType.type_long;
        case "F" -> PrimitiveType.type_float;
        case "D" -> PrimitiveType.type_double;
        case "Z" -> PrimitiveType.type_boolean;
        default ->
        {
          throw new Error("Unexpected type string `" + s +"`");
        }
      };
  }


  /**
   * Get the JavaType from a descriptor, e.g.,
   *
   *   typeFromDescriptor("(IJLjava/lang/String;)V",3)
   *
   * will return the type java.lang.String.
   *
   * @param s a descriptor string
   *
   * @param at a position in a descriptor string.
   *
   * @return the type starting as a JavaType instance.
   */
  static JavaType typeFromDescriptor(String s, int at)
  {
    var c = s.charAt(at);
    return switch (c)
      {
      case 'V', 'B', 'S', 'C', 'I', 'J', 'F', 'D', 'Z' -> primitiveType("" + c);
      case '[' -> typeFromDescriptor(s, at+1).array();
      case 'L' -> new ClassType(s.substring(at + 1, s.indexOf(";", at)));
      default -> throw new Error("Unexpected Java type starting with '" + c + "'");
      };
  }

  /**
   * Skip a type in a descriptor and get the index of the first char after the
   * descriptor, e.g.,
   *
   *   skipTypeInDescriptor("(IJLjava/lang/String;)V",3)
   *
   * will return the index of ")".
   *
   * @param s a descriptor string
   *
   * @param at a position in a descriptor string.
   *
   * @return the index in s after the type.
   */
  static int skipTypeInDescriptor(String s, int at)
  {
    var c = s.charAt(at);
    return switch (c)
      {
      case 'V', 'B', 'S', 'C', 'I', 'J', 'F', 'D', 'Z' -> at + 1;
      case '[' -> skipTypeInDescriptor(s, at+1);
      case 'L' -> s.indexOf(";", at) + 1;
      default -> throw new Error("Unexpected Java type starting with '" + c + "'");
      };
  }


  /**
   * Get the argument types used in a method signature descriptor as a stream, e.g.,
   *
   *   argTypesFromDescriptor("(IJLjava/lang/String;)V",3)
   *
   * Will produce
   *
   *   PrimitiveType.type_int
   *   PrimitiveType.type_long
   *   ClassType("java/lang/String")
   *
   * @param descriptor a method signature descriptor
   *
   * @return a stream of all the argument types
   */
  static Stream<JavaType> argTypesFromDescriptor(String descriptor)
  {
    if (ANY.PRECONDITIONS) ANY.require
      (descriptor.charAt(0) == '(',
       descriptor.indexOf(")") > 0);

    var l = new List<JavaType>();
    var i = 1;
    while (descriptor.charAt(i) != ')')
      {
        l.add(typeFromDescriptor(descriptor, i));
        i = skipTypeInDescriptor(descriptor, i);
      }
    return l.stream();
  }


  /**
   * This counts the number of slots for a call with the given descriptor.  This
   * is the sum of the slot count of all arguments in the descriptor, not
   * including the target value.
   *
   * @param a call descriptor, e.g., "(JDZLjava/lang/Object;II)F"
   *
   * @return the slot count, e.g., 8 for "(JDZLjava/lang/Object;II)F"
   * (==2+2+1+1+1+1).
   */
  static int slotCountForArgs(String descriptor)
  {
    return argTypesFromDescriptor(descriptor).mapToInt(x -> x.stackSlots()).sum();
  }


  public static int ACC_PUBLIC        = 0x0001;  // class,         field, method
  public static int ACC_PRIVATE       = 0x0002;  //                field, method
  public static int ACC_PROTECTED     = 0x0004;  //                field, method
  public static int ACC_STATIC        = 0x0008;  //                field, method
  public static int ACC_FINAL         = 0x0010;  // class,         field, method
  public static int ACC_SUPER         = 0x0020;  // class
  public static int ACC_SYNCHRONIZED  = 0x0020;  //                       method
  public static int ACC_OPEN          = 0x0020;  //        module
  public static int ACC_TRANSITIVE    = 0x0020;  //                              requires_flags
  public static int ACC_VOLATILE      = 0x0040;  //                field
  public static int ACC_BRIDGE        = 0x0040;  //                       method
  public static int ACC_STATIC_PHASE  = 0x0040;  //                              requires_flags
  public static int ACC_TRANSIENT     = 0x0080;  //                field
  public static int ACC_VARARGS       = 0x0080;  //                       method
  public static int ACC_NATIVE        = 0x0100;  //                       method
  public static int ACC_INTERFACE     = 0x0200;  // class
  public static int ACC_ABSTRACT      = 0x0400;  //                       method
  public static int ACC_STRICT        = 0x0800;  //                       method
  public static int ACC_SYNTHETIC     = 0x1000;  // class, module, field, method, requires_flags
  public static int ACC_ANNOTATION    = 0x2000;  // class
  public static int ACC_ENUM          = 0x4000;  // class,         field
  public static int ACC_MODULE        = 0x8000;  // class
  public static int ACC_MANDATED      = 0x8000;  //        module,                requires_flags


  public static int CLASS_ACCESS_FLAGS    = ACC_PUBLIC | ACC_FINAL | ACC_SUPER | ACC_INTERFACE | ACC_SYNTHETIC | ACC_ANNOTATION | ACC_ENUM | ACC_MODULE;
  public static int MODULE_ACCESS_FLAGS   = ACC_OPEN | ACC_SYNTHETIC | ACC_MANDATED;
  public static int FIELD_ACCESS_FLAGS    = ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED | ACC_STATIC | ACC_FINAL | ACC_VOLATILE | ACC_TRANSIENT | ACC_SYNTHETIC | ACC_ENUM;
  public static int METHOD_ACCESS_FLAGS   = ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED | ACC_STATIC | ACC_FINAL | ACC_SYNCHRONIZED | ACC_BRIDGE | ACC_VARARGS | ACC_NATIVE | ACC_ABSTRACT | ACC_STRICT | ACC_SYNTHETIC;
  public static int REQUIRES_ACCESS_FLAGS = ACC_TRANSITIVE  | ACC_STATIC_PHASE | ACC_SYNTHETIC | ACC_MANDATED;


  /* primitive types */
  public static byte T_BOOLEAN = 4;
  public static byte T_BYTE    = 8;
  public static byte T_CHAR    = 5;
  public static byte T_SHORT   = 9;
  public static byte T_INTEGER = 10;
  public static byte T_LONG    = 11;
  public static byte T_FLOAT   = 6;
  public static byte T_DOUBLE  = 7;


  /**
   * bytecodes:
   */
  public static final byte O_nop              = (byte) 0x00;
  public static final byte O_aconst_null      = (byte) 0x01;
  public static final byte O_iconst_m1        = (byte) 0x02;
  public static final byte O_iconst_0         = (byte) 0x03;
  public static final byte O_iconst_1         = (byte) 0x04;
  public static final byte O_iconst_2         = (byte) 0x05;
  public static final byte O_iconst_3         = (byte) 0x06;
  public static final byte O_iconst_4         = (byte) 0x07;
  public static final byte O_iconst_5         = (byte) 0x08;
  public static final byte O_lconst_0         = (byte) 0x09;
  public static final byte O_lconst_1         = (byte) 0x0a;
  public static final byte O_fconst_0         = (byte) 0x0b;
  public static final byte O_fconst_1         = (byte) 0x0c;
  public static final byte O_fconst_2         = (byte) 0x0d;
  public static final byte O_dconst_0         = (byte) 0x0e;
  public static final byte O_dconst_1         = (byte) 0x0f;
  public static final byte O_bipush           = (byte) 0x10;
  public static final byte O_sipush           = (byte) 0x11;
  public static final byte O_ldc              = (byte) 0x12;
  public static final byte O_ldc_w            = (byte) 0x13;
  public static final byte O_ldc2_w           = (byte) 0x14;
  public static final byte O_iload            = (byte) 0x15;
  public static final byte O_lload            = (byte) 0x16;
  public static final byte O_fload            = (byte) 0x17;
  public static final byte O_dload            = (byte) 0x18;
  public static final byte O_aload            = (byte) 0x19;
  public static final byte O_iload_0          = (byte) 0x1a;
  public static final byte O_iload_1          = (byte) 0x1b;
  public static final byte O_iload_2          = (byte) 0x1c;
  public static final byte O_iload_3          = (byte) 0x1d;
  public static final byte O_lload_0          = (byte) 0x1e;
  public static final byte O_lload_1          = (byte) 0x1f;
  public static final byte O_lload_2          = (byte) 0x20;
  public static final byte O_lload_3          = (byte) 0x21;
  public static final byte O_fload_0          = (byte) 0x22;
  public static final byte O_fload_1          = (byte) 0x23;
  public static final byte O_fload_2          = (byte) 0x24;
  public static final byte O_fload_3          = (byte) 0x25;
  public static final byte O_dload_0          = (byte) 0x26;
  public static final byte O_dload_1          = (byte) 0x27;
  public static final byte O_dload_2          = (byte) 0x28;
  public static final byte O_dload_3          = (byte) 0x29;
  public static final byte O_aload_0          = (byte) 0x2a;
  public static final byte O_aload_1          = (byte) 0x2b;
  public static final byte O_aload_2          = (byte) 0x2c;
  public static final byte O_aload_3          = (byte) 0x2d;
  public static final byte O_iaload           = (byte) 0x2e;
  public static final byte O_laload           = (byte) 0x2f;
  public static final byte O_faload           = (byte) 0x30;
  public static final byte O_daload           = (byte) 0x31;
  public static final byte O_aaload           = (byte) 0x32;
  public static final byte O_baload           = (byte) 0x33;
  public static final byte O_caload           = (byte) 0x34;
  public static final byte O_saload           = (byte) 0x35;
  public static final byte O_istore           = (byte) 0x36;
  public static final byte O_lstore           = (byte) 0x37;
  public static final byte O_fstore           = (byte) 0x38;
  public static final byte O_dstore           = (byte) 0x39;
  public static final byte O_astore           = (byte) 0x3a;
  public static final byte O_istore_0         = (byte) 0x3b;
  public static final byte O_istore_1         = (byte) 0x3c;
  public static final byte O_istore_2         = (byte) 0x3d;
  public static final byte O_istore_3         = (byte) 0x3e;
  public static final byte O_lstore_0         = (byte) 0x3f;
  public static final byte O_lstore_1         = (byte) 0x40;
  public static final byte O_lstore_2         = (byte) 0x41;
  public static final byte O_lstore_3         = (byte) 0x42;
  public static final byte O_fstore_0         = (byte) 0x43;
  public static final byte O_fstore_1         = (byte) 0x44;
  public static final byte O_fstore_2         = (byte) 0x45;
  public static final byte O_fstore_3         = (byte) 0x46;
  public static final byte O_dstore_0         = (byte) 0x47;
  public static final byte O_dstore_1         = (byte) 0x48;
  public static final byte O_dstore_2         = (byte) 0x49;
  public static final byte O_dstore_3         = (byte) 0x4a;
  public static final byte O_astore_0         = (byte) 0x4b;
  public static final byte O_astore_1         = (byte) 0x4c;
  public static final byte O_astore_2         = (byte) 0x4d;
  public static final byte O_astore_3         = (byte) 0x4e;
  public static final byte O_iastore          = (byte) 0x4f;
  public static final byte O_lastore          = (byte) 0x50;
  public static final byte O_fastore          = (byte) 0x51;
  public static final byte O_dastore          = (byte) 0x52;
  public static final byte O_aastore          = (byte) 0x53;
  public static final byte O_bastore          = (byte) 0x54;
  public static final byte O_castore          = (byte) 0x55;
  public static final byte O_sastore          = (byte) 0x56;
  public static final byte O_pop              = (byte) 0x57;
  public static final byte O_pop2             = (byte) 0x58;
  public static final byte O_dup              = (byte) 0x59;
  public static final byte O_dup_x1           = (byte) 0x5a;
  public static final byte O_dup_x2           = (byte) 0x5b;
  public static final byte O_dup2             = (byte) 0x5c;
  public static final byte O_dup2_x1          = (byte) 0x5d;
  public static final byte O_dup2_x2          = (byte) 0x5e;
  public static final byte O_swap             = (byte) 0x5f;
  public static final byte O_iadd             = (byte) 0x60;
  public static final byte O_ladd             = (byte) 0x61;
  public static final byte O_fadd             = (byte) 0x62;
  public static final byte O_dadd             = (byte) 0x63;
  public static final byte O_isub             = (byte) 0x64;
  public static final byte O_lsub             = (byte) 0x65;
  public static final byte O_fsub             = (byte) 0x66;
  public static final byte O_dsub             = (byte) 0x67;
  public static final byte O_imul             = (byte) 0x68;
  public static final byte O_lmul             = (byte) 0x69;
  public static final byte O_fmul             = (byte) 0x6a;
  public static final byte O_dmul             = (byte) 0x6b;
  public static final byte O_idiv             = (byte) 0x6c;
  public static final byte O_ldiv             = (byte) 0x6d;
  public static final byte O_fdiv             = (byte) 0x6e;
  public static final byte O_ddiv             = (byte) 0x6f;
  public static final byte O_irem             = (byte) 0x70;
  public static final byte O_lrem             = (byte) 0x71;
  public static final byte O_frem             = (byte) 0x72;
  public static final byte O_drem             = (byte) 0x73;
  public static final byte O_ineg             = (byte) 0x74;
  public static final byte O_lneg             = (byte) 0x75;
  public static final byte O_fneg             = (byte) 0x76;
  public static final byte O_dneg             = (byte) 0x77;
  public static final byte O_ishl             = (byte) 0x78;
  public static final byte O_lshl             = (byte) 0x79;
  public static final byte O_ishr             = (byte) 0x7a;
  public static final byte O_lshr             = (byte) 0x7b;
  public static final byte O_iushr            = (byte) 0x7c;
  public static final byte O_lushr            = (byte) 0x7d;
  public static final byte O_iand             = (byte) 0x7e;
  public static final byte O_land             = (byte) 0x7f;
  public static final byte O_ior              = (byte) 0x80;
  public static final byte O_lor              = (byte) 0x81;
  public static final byte O_ixor             = (byte) 0x82;
  public static final byte O_lxor             = (byte) 0x83;
  public static final byte O_iinc             = (byte) 0x84;
  public static final byte O_i2l              = (byte) 0x85;
  public static final byte O_i2f              = (byte) 0x86;
  public static final byte O_i2d              = (byte) 0x87;
  public static final byte O_l2i              = (byte) 0x88;
  public static final byte O_l2f              = (byte) 0x89;
  public static final byte O_l2d              = (byte) 0x8a;
  public static final byte O_f2i              = (byte) 0x8b;
  public static final byte O_f2l              = (byte) 0x8c;
  public static final byte O_f2d              = (byte) 0x8d;
  public static final byte O_d2i              = (byte) 0x8e;
  public static final byte O_d2l              = (byte) 0x8f;
  public static final byte O_d2f              = (byte) 0x90;
  public static final byte O_i2b              = (byte) 0x91;
  public static final byte O_i2c              = (byte) 0x92;
  public static final byte O_i2s              = (byte) 0x93;
  public static final byte O_lcmp             = (byte) 0x94;
  public static final byte O_fcmpl            = (byte) 0x95;
  public static final byte O_fcmpg            = (byte) 0x96;
  public static final byte O_dcmpl            = (byte) 0x97;
  public static final byte O_dcmpg            = (byte) 0x98;
  public static final byte O_ifeq             = (byte) 0x99;
  public static final byte O_ifne             = (byte) 0x9a;
  public static final byte O_iflt             = (byte) 0x9b;
  public static final byte O_ifge             = (byte) 0x9c;
  public static final byte O_ifgt             = (byte) 0x9d;
  public static final byte O_ifle             = (byte) 0x9e;
  public static final byte O_if_icmpeq        = (byte) 0x9f;
  public static final byte O_if_icmpne        = (byte) 0xa0;
  public static final byte O_if_icmplt        = (byte) 0xa1;
  public static final byte O_if_icmpge        = (byte) 0xa2;
  public static final byte O_if_icmpgt        = (byte) 0xa3;
  public static final byte O_if_icmple        = (byte) 0xa4;
  public static final byte O_if_acmpeq        = (byte) 0xa5;
  public static final byte O_if_acmpne        = (byte) 0xa6;
  public static final byte O_goto             = (byte) 0xa7;
  public static final byte O_jsr              = (byte) 0xa8;
  public static final byte O_ret              = (byte) 0xa9;
  public static final byte O_tableswitch      = (byte) 0xaa;
  public static final byte O_lookupswitch     = (byte) 0xab;
  public static final byte O_ireturn          = (byte) 0xac;
  public static final byte O_lreturn          = (byte) 0xad;
  public static final byte O_freturn          = (byte) 0xae;
  public static final byte O_dreturn          = (byte) 0xaf;
  public static final byte O_areturn          = (byte) 0xb0;
  public static final byte O_return           = (byte) 0xb1;
  public static final byte O_getstatic        = (byte) 0xb2;
  public static final byte O_putstatic        = (byte) 0xb3;
  public static final byte O_getfield         = (byte) 0xb4;
  public static final byte O_putfield         = (byte) 0xb5;
  public static final byte O_invokevirtual    = (byte) 0xb6;
  public static final byte O_invokespecial    = (byte) 0xb7;
  public static final byte O_invokestatic     = (byte) 0xb8;
  public static final byte O_invokeinterface  = (byte) 0xb9;
  public static final byte O_invokedynamic    = (byte) 0xba;
  public static final byte O_new              = (byte) 0xbb;
  public static final byte O_newarray         = (byte) 0xbc;
  public static final byte O_anewarray        = (byte) 0xbd;
  public static final byte O_arraylength      = (byte) 0xbe;
  public static final byte O_athrow           = (byte) 0xbf;
  public static final byte O_checkcast        = (byte) 0xc0;
  public static final byte O_instanceof       = (byte) 0xc1;
  public static final byte O_monitorenter     = (byte) 0xc2;
  public static final byte O_monitorexit      = (byte) 0xc3;
  public static final byte O_wide             = (byte) 0xc4;
  public static final byte O_multianewarray   = (byte) 0xc5;
  public static final byte O_ifnull           = (byte) 0xc6;
  public static final byte O_ifnonnull        = (byte) 0xc7;
  public static final byte O_goto_w           = (byte) 0xc8;
  public static final byte O_jsr_w            = (byte) 0xc9;
  public static final byte O_breakpoint       = (byte) 0xca;
  public static final byte O_impdep1          = (byte) 0xfe;
  public static final byte O_impdep2          = (byte) 0xff;


  /**
   * This is the maximum allowed length of the bytecode per method.
   */
  public static final int MAX_BYTECODE_LENGTH = 0xffff;


  /**
   * min / max offset in a branching bytecode instruction such as `ifeq`.
   */
  public static final int MIN_BRANCH_OFFSET = -0x8000;
  public static final int MAX_BRANCH_OFFSET =  0x7fff;



  /**
   * The invokeinterface bytecode requires the number of argument slots given in
   * a byte:
   */
  public static final int MAX_INVOKE_INTERFACE_SLOTS = 0xff;


  public static final int STACK_MAP_FRAME_FULL_FRAME = 255;


}

/* end of file */
