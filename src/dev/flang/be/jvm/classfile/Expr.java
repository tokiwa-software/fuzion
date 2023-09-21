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
 * Source of class Expr
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm.classfile;

import dev.flang.util.Pair;


/**
 * Expr represents a calculated value in Java bytecode. The Bytecode of
 * An Expr is stored on the Java stack after evaluation.
 *
 * In addition to the bytecode data itself, an Expr has information about the
 * type of the value it produces and therefore can perform operations depending
 * on this type.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class Expr extends ByteCode
{


  /*-----------------------------  classes  -----------------------------*/


  /**
   * Expression for the unit value.
   */
  static class Unit extends Expr
  {
    public String toString() { return "UNIT"; }
    public JavaType type() { return PrimitiveType.type_void; }
    public byte[] byteCode(ClassFile cf) { return BC_EMPTY; }
  }


  /**
   * abstract class for expression to load a constant from a CPool entry. Will
   * be implemented by anonymous classes inheriting form this.
   */
  static abstract class LoadConst extends Expr
  {
    abstract ClassFile.CPEntry cpEntry(ClassFile cf);
    public byte[] byteCode(ClassFile cf)
    {
      return bc(O_ldc, cpEntry(cf));
    }
  }


  /**
   * Class for simple Expr instances with fixed type and bytecode.
   */
  static class Simple extends Expr
  {
    final String _str;
    final JavaType _type;
    final byte[] _bc;
    Simple(String str, JavaType type, byte[] bc)
    {
      this._str = str;
      this._type = type;
      this._bc = bc;
    }
    public String   toString()             { return _str;  }
    public JavaType type()                 { return _type; }
    public byte[]   byteCode(ClassFile cf) { return _bc;   }
  }


  /**
   * abstract parent class for loading a local variable
   */
  static abstract class Load extends Expr
  {
    public Expr drop()
    {
      return UNIT;
    }
  }

  /**
   * abstract parent class for storing a local variable
   */
  static abstract class Store extends Expr
  {
    public JavaType type()
    {
      return PrimitiveType.type_void;
    }
  }


  /*----------------------------  constants  ----------------------------*/


  /**
   * unit value
   */
  public static final Expr UNIT = new Unit();


  /**
   * simple bytecode expressions
   */
  public static final Expr RETURN      = new Simple("RETURN" , PrimitiveType.type_void, BC_RETURN);
  public static final Expr IRETURN     = new Simple("IRETURN", PrimitiveType.type_void, BC_IRETURN);
  public static final Expr LRETURN     = new Simple("LRETURN", PrimitiveType.type_void, BC_LRETURN);
  public static final Expr FRETURN     = new Simple("FRETURN", PrimitiveType.type_void, BC_FRETURN);
  public static final Expr DRETURN     = new Simple("DRETURN", PrimitiveType.type_void, BC_DRETURN);
  public static final Expr ARETURN     = new Simple("ARETURN", PrimitiveType.type_void, BC_ARETURN);

  public static final Expr NOP         = new Simple("NOP"    , PrimitiveType.type_void, BC_NOP    );
  public static final Expr POP         = new Simple("POP"    , PrimitiveType.type_void, BC_POP    );
  public static final Expr POP2        = new Simple("POP2"   , PrimitiveType.type_void, BC_POP2   );
  public static final Expr DUP         = new Simple("DUP"    , PrimitiveType.type_void, BC_DUP    );
  public static final Expr DUP_X1      = new Simple("DUP_X1" , PrimitiveType.type_void, BC_DUP_X1 );
  public static final Expr DUP_X2      = new Simple("DUP_x2" , PrimitiveType.type_void, BC_DUP_X2 );
  public static final Expr SWAP        = new Simple("SWAP"   , PrimitiveType.type_void, BC_SWAP   );

  public static final Expr THROW       = new Simple("THROW"  , PrimitiveType.type_void, BC_ATHROW);

  public static final Expr BALOAD      = new Simple("BALOAD" , PrimitiveType.type_byte,   BC_BALOAD);
  public static final Expr CALOAD      = new Simple("CALOAD" , PrimitiveType.type_char,   BC_CALOAD);
  public static final Expr SALOAD      = new Simple("SALOAD" , PrimitiveType.type_short,  BC_SALOAD);
  public static final Expr IALOAD      = new Simple("IALOAD" , PrimitiveType.type_int,    BC_IALOAD);
  public static final Expr LALOAD      = new Simple("LALOAD" , PrimitiveType.type_long,   BC_LALOAD);
  public static final Expr FALOAD      = new Simple("FALOAD" , PrimitiveType.type_float,  BC_FALOAD);
  public static final Expr DALOAD      = new Simple("DALOAD" , PrimitiveType.type_double, BC_DALOAD);

  public static final Expr ARRAYLENGTH = new Simple("ARRAYLENGTH", PrimitiveType.type_int, BC_ARRAYLENGTH);

  public static final Expr BASTORE     = new Simple("BASTORE", PrimitiveType.type_void, BC_BASTORE);
  public static final Expr CASTORE     = new Simple("CASTORE", PrimitiveType.type_void, BC_CASTORE);
  public static final Expr SASTORE     = new Simple("SASTORE", PrimitiveType.type_void, BC_SASTORE);
  public static final Expr IASTORE     = new Simple("IASTORE", PrimitiveType.type_void, BC_IASTORE);
  public static final Expr LASTORE     = new Simple("LASTORE", PrimitiveType.type_void, BC_LASTORE);
  public static final Expr FASTORE     = new Simple("FASTORE", PrimitiveType.type_void, BC_FASTORE);
  public static final Expr DASTORE     = new Simple("DASTORE", PrimitiveType.type_void, BC_DASTORE);
  public static final Expr AASTORE     = new Simple("AASTORE", PrimitiveType.type_void, BC_AASTORE);

  public static final Expr ZNEWARRAY   = new Simple("ZNEWARRAY", PrimitiveType.type_boolean.array(), BC_ZNEWARRAY);
  public static final Expr BNEWARRAY   = new Simple("BNEWARRAY", PrimitiveType.type_byte.array   (), BC_BNEWARRAY);
  public static final Expr CNEWARRAY   = new Simple("CNEWARRAY", PrimitiveType.type_char.array   (), BC_CNEWARRAY);
  public static final Expr SNEWARRAY   = new Simple("SNEWARRAY", PrimitiveType.type_short.array  (), BC_SNEWARRAY);
  public static final Expr INEWARRAY   = new Simple("INEWARRAY", PrimitiveType.type_int.array    (), BC_INEWARRAY);
  public static final Expr LNEWARRAY   = new Simple("LNEWARRAY", PrimitiveType.type_long.array   (), BC_LNEWARRAY);
  public static final Expr FNEWARRAY   = new Simple("FNEWARRAY", PrimitiveType.type_float.array  (), BC_FNEWARRAY);
  public static final Expr DNEWARRAY   = new Simple("DNEWARRAY", PrimitiveType.type_double.array (), BC_DNEWARRAY);

  public static final Expr ACONST_NULL = new Simple("ACONST_NULL", JAVA_LANG_OBJECT, BC_ACONST_NULL);

  public static final Expr MONITORENTER = new Simple("MONITORENTER", PrimitiveType.type_void, BC_MONITORENTER);
  public static final Expr MONITOREXIT = new Simple("MONITOREXIT", PrimitiveType.type_void, BC_MONITOREXIT);


  /**
   * Flag to enable (or suppress, if false) comments in the bytecode, see
   * comment(String).
   */
  public static boolean ENABLE_COMMENTS = false;


  /*-------------------------  static methods  --------------------------*/


  /**
   * Create a comment in the bytecode with the given msg.
   *
   * Java bytecode does not have a comment mechanism. Instead, if
   * ENABLE_COMMENTS is true, this will create a ldc bytecode that loads the msg
   * as a string constant followed by a pop bytecode to discard it.
   *
   * If ENABLED_COMMENTS is false, this will return UNIT.
   */
  public static Expr comment(String msg)
  {
    if (ENABLE_COMMENTS)
      {
        var s = stringconst(msg);
        return s.andThen(s.type().pop());
      }
    else
      {
        return UNIT;
      }
  }


  /**
   * create invokestatic bytecode to call given class, name and descr producing
   * given result type on the stack.
   */
  public static Expr invokeStatic(String cls, String name, String descr, JavaType rt)
  {
    return new Expr()
      {
        public String toString() { return "invokeStatic " + cls + "." + name; }
        public JavaType type() { return rt;  }
        public byte[] byteCode(ClassFile cf)
        {
          var c   = cf.cpUtf8(cls);
          var n   = cf.cpUtf8(name);
          var d   = cf.cpUtf8(descr);
          var cl  = cf.cpClass(c);
          var nat = cf.cpNameAndType(n, d);
          var m   = cf.cpMethod(cl, nat);
          return bc(O_invokestatic, m);
        };
    };
  }


  /**
   * create invokespecial bytecode to call given class, name and descr producing
   * void result type.
   */
  public static Expr invokeSpecial(String cls, String name, String descr)
  {
    return new Expr()
      {
        public String toString() { return "invokeSpecial " + cls + "." + name; }
        public JavaType type() { return PrimitiveType.type_void; }
        public byte[] byteCode(ClassFile cf)
        {
          var c   = cf.cpUtf8(cls);
          var n   = cf.cpUtf8(name);
          var d   = cf.cpUtf8(descr);
          var cl  = cf.cpClass(c);
          var nat = cf.cpNameAndType(n, d);
          var m   = cf.cpMethod(cl, nat);
          return bc(O_invokespecial, m);
        };
    };
  }


  /**
   * create invokespecial bytecode to call given class, name and descr producing
   * void result type.
   */
  public static Expr invokeSpecial(ClassFile.CPClass cl, String name, String descr)
  {
    return new Expr()
      {
        public String toString() { return "invokeSpecial " + cl + "." + name; }
        public JavaType type() { return PrimitiveType.type_void;  }
        public byte[] byteCode(ClassFile cf)
        {
          var n   = cf.cpUtf8(name);
          var d   = cf.cpUtf8(descr);
          var nat = cf.cpNameAndType(n, d);
          var m   = cf.cpMethod(cl, nat);
          return bc(O_invokespecial, m);
        };
    };
  }


  /**
   * create invokevirtual bytecode to call given class, name and descr producing
   * given result type on the stack.
   */
  public static Expr invokeVirtual(String cls, String name, String descr, JavaType rt)
  {
    return new Expr()
      {
        public String toString() { return "invokeVirtual " + cls + "." + name; }
        public JavaType type() { return rt;  }
        public byte[] byteCode(ClassFile cf)
        {
          var c   = cf.cpUtf8(cls);
          var n   = cf.cpUtf8(name);
          var d   = cf.cpUtf8(descr);
          var cl  = cf.cpClass(c);
          var nat = cf.cpNameAndType(n, d);
          var m   = cf.cpMethod(cl, nat);
          return bc(O_invokevirtual, m);
        };
    };
  }

  /**
   * create invokeinterface bytecode to call given class, name and descr producing
   * given result type on the stack.
   */
  public static Expr invokeInterface(String cls, String name, String descr, JavaType rt, int count)
  {
    if (PRECONDITIONS) check
      (count > 0 && count <= 0xff);

    return new Expr()
      {
        public String toString() { return "invokeInterface " + cls + "." + name; }
        public JavaType type() { return rt;  }
        public byte[] byteCode(ClassFile cf)
        {
          var c   = cf.cpUtf8(cls);
          var n   = cf.cpUtf8(name);
          var d   = cf.cpUtf8(descr);
          var cl  = cf.cpClass(c);
          var nat = cf.cpNameAndType(n, d);
          var m   = cf.cpInterfaceMethod(cl, nat);
          return bc(O_invokeinterface, m, (byte) count, (byte) 0);
        };
    };
  }


  /**
   * create getfield bytecode to load field described by cls, name and type.
   */
  public static Expr getfield(String cls, String name, JavaType type)
  {
    if (PRECONDITIONS) require
      (type != ClassFileConstants.PrimitiveType.type_void);

    return new Expr()
      {
        public String toString() { return "getfield " + cls + "." + name; }
        public JavaType type()
        {
          return type;
        }
        public byte[] byteCode(ClassFile cf)
        {
          return bc(O_getfield, cf.cpField(cf.cpClass(cls), cf.cpNameAndType(name, type.descriptor())));
        }
    };
  }


  /**
   * create putfield bytecode to write field described by cls, name and type.
   */
  public static Expr putfield(String cls, String name, JavaType type)
  {
    if (PRECONDITIONS) require
      (cls != null,
       name != null,
       type != null,
       type != ClassFileConstants.PrimitiveType.type_void);

    return new Expr()
      {
        public String toString() { return "putfield " + cls + "." + name; }
        public JavaType type() { return PrimitiveType.type_void;  }
        public byte[] byteCode(ClassFile cf)
        {
          return bc(O_putfield, cf.cpField(cf.cpClass(cls), cf.cpNameAndType(name, type.descriptor())));
        }
    };
  }


  /**
   * create getstatic bytecode to load static field described by cls, name and type.
   */
  public static Expr getstatic(String cls, String name, JavaType type)
  {
    if (PRECONDITIONS) require
      (cls != null,
       name != null,
       type != null,
       type != ClassFileConstants.PrimitiveType.type_void);

    return new Expr()
      {
        public String toString() { return "getstatic " + cls + "." + name; }
        public JavaType type()
        {
          return type;
        }
        public byte[] byteCode(ClassFile cf)
        {
          return bc(O_getstatic, cf.cpField(cf.cpClass(cls), cf.cpNameAndType(name, type.descriptor())));
        }
    };
  }


  /**
   * create putstatic bytecode to write field described by cls, name and type.
   */
  public static Expr putstatic(String cls, String name, JavaType type)
  {
    if (PRECONDITIONS) require
      (cls != null,
       name != null,
       type != null,
       type != ClassFileConstants.PrimitiveType.type_void);

    return new Expr()
      {
        public String toString() { return "putstatic " + cls + "." + name; }
        public JavaType type() { return PrimitiveType.type_void;  }
        public byte[] byteCode(ClassFile cf)
        {
          return bc(O_putstatic, cf.cpField(cf.cpClass(cls), cf.cpNameAndType(name, type.descriptor())));
        }
    };
  }


  /**
   * Load a 32-bit integer constant
   */
  public static Expr iconst(int c)
  {
    return new LoadConst()
      {
        public String toString() { return "iconst"; }
        public JavaType type()
        {
          return PrimitiveType.type_int;
        }
        ClassFile.CPEntry cpEntry(ClassFile cf) { return cf.cpInteger(c); }
        public byte[] byteCode(ClassFile cf)
        {
          return switch (c)
            {
            case -1 -> BC_ICONST_M1;
            case 0 -> BC_ICONST_0;
            case 1 -> BC_ICONST_1;
            case 2 -> BC_ICONST_2;
            case 3 -> BC_ICONST_3;
            case 4 -> BC_ICONST_4;
            case 5 -> BC_ICONST_5;
            default -> super.byteCode(cf);
            };
        }
    };
  }


  /**
   * Load a 64-bit integer constant
   */
  public static Expr lconst(long c)
  {
    return new LoadConst()
      {
        public String toString() { return "lconst"; }
        public JavaType type()
        {
          return PrimitiveType.type_long;
        }
        ClassFile.CPEntry cpEntry(ClassFile cf) { return cf.cpLong(c); }
        public byte[] byteCode(ClassFile cf)
        {
          return
            c == 0L ? BC_LCONST_0 :
            c == 1L ? BC_LCONST_1 : super.byteCode(cf);
        }
    };
  }


  /**
   * Load a 32-bit float constant
   */
  public static Expr fconst(int c)
  {
    return new LoadConst()
      {
        public String toString() { return "fconst"; }
        public JavaType type()
        {
          return PrimitiveType.type_float;
        }
        ClassFile.CPEntry cpEntry(ClassFile cf) { return cf.cpFloat(c); }
        public byte[] byteCode(ClassFile cf)
        {
          return
            Float.intBitsToFloat(c) == 0F ? BC_FCONST_0 :
            Float.intBitsToFloat(c) == 1F ? BC_FCONST_1 :
            Float.intBitsToFloat(c) == 2F ? BC_FCONST_2 : super.byteCode(cf);
        }
    };
  }


  /**
   * Load a 64-bit float constant
   */
  public static Expr dconst(long c)
  {
    return new LoadConst()
      {
        public String toString() { return "dconst"; }
        public JavaType type()
        {
          return PrimitiveType.type_double;
        }
        ClassFile.CPEntry cpEntry(ClassFile cf) { return cf.cpDouble(c); }
        public byte[] byteCode(ClassFile cf)
        {
          return
            Double.longBitsToDouble(c) == 0F ? BC_DCONST_0 :
            Double.longBitsToDouble(c) == 1F ? BC_DCONST_1 : super.byteCode(cf);
        }
    };
  }


  /**
   * Load a java.lang.String constant given by a Java string
   */
  public static Expr stringconst(String s)
  {
    return new LoadConst()
      {
        public String toString() { return "String constant '" + s + "'"; }
        public JavaType type()   { return JAVA_LANG_STRING;              }
        ClassFile.CPEntry cpEntry(ClassFile cf) { return cf.cpString(s); }
    };
  }

  /**
   * Load a java.lang.String constant given by utf8 encoded bytes
   */
  public static Expr stringconst(byte[] s)
  {
    return new LoadConst()
      {
        public String toString() { return "String constant";             }
        public JavaType type()   { return JAVA_LANG_STRING;              }
        ClassFile.CPEntry cpEntry(ClassFile cf) { return cf.cpString(s); }
    };
  }


  /**
   * Load a java.lang.Class constant given by ClassType
   */
  public static Expr classconst(ClassType t)
  {
    return new LoadConst()
      {
        public String toString() { return "class " + t;                 }
        public JavaType type()   { return JAVA_LANG_CLASS;              }
        ClassFile.CPEntry cpEntry(ClassFile cf) { return cf.cpClass(t); }
    };
  }

  /**
   * Load int local variable from slot at given index.
   */
  public static Expr iload(int index)
  {
    return new Load()
      {
        public String toString() { return "iload"; }
        public JavaType type()
        {
          return PrimitiveType.type_int;
        }
        public byte[] byteCode(ClassFile cf)
        {
          return switch (index)
            {
            case 0 -> BC_ILOAD_0;
            case 1 -> BC_ILOAD_1;
            case 2 -> BC_ILOAD_2;
            case 3 -> BC_ILOAD_3;
            default -> bc(O_iload, index);
            };
        }
    };
  }

  /**
   * Store int local variable into slot at given index.
   */
  public static Expr istore(int index)
  {
    return new Store()
      {
        public String toString() { return "istore"; }
        public byte[] byteCode(ClassFile cf)
        {
          return switch (index)
            {
            case 0 -> BC_ISTORE_0;
            case 1 -> BC_ISTORE_1;
            case 2 -> BC_ISTORE_2;
            case 3 -> BC_ISTORE_3;
            default -> bc(O_istore, index);
            };
        }
    };
  }


  /**
   * Load int local variable from slot at given index.
   */
  public static Expr lload(int index)
  {
    return new Load()
      {
        public String toString() { return "lload"; }
        public JavaType type()
        {
          return PrimitiveType.type_long;
        }
        public byte[] byteCode(ClassFile cf)
        {
          return switch (index)
            {
            case 0 -> BC_LLOAD_0;
            case 1 -> BC_LLOAD_1;
            case 2 -> BC_LLOAD_2;
            case 3 -> BC_LLOAD_3;
            default -> bc(O_lload, index);
            };
        }
    };
  }


  /**
   * Store long local variable into slot at given index.
   */
  public static Expr lstore(int index)
  {
    return new Store()
      {
        public String toString() { return "lstore"; }
        public byte[] byteCode(ClassFile cf)
        {
          return switch (index)
            {
            case 0 -> BC_LSTORE_0;
            case 1 -> BC_LSTORE_1;
            case 2 -> BC_LSTORE_2;
            case 3 -> BC_LSTORE_3;
            default -> bc(O_lstore, index);
            };
        }
    };
  }


  /**
   * Load float local variable from slot at given index.
   */
  public static Expr fload(int index)
  {
    return new Load()
      {
        public String toString() { return "fload"; }
        public JavaType type()
        {
          return PrimitiveType.type_float;
        }
        public byte[] byteCode(ClassFile cf)
        {
          return switch (index)
            {
            case 0 -> BC_FLOAD_0;
            case 1 -> BC_FLOAD_1;
            case 2 -> BC_FLOAD_2;
            case 3 -> BC_FLOAD_3;
            default -> bc(O_fload, index);
            };
        }
    };
  }


  /**
   * Store float local variable into slot at given index.
   */
  public static Expr fstore(int index)
  {
    return new Store()
      {
        public String toString() { return "fstore"; }
        public byte[] byteCode(ClassFile cf)
        {
          return switch (index)
            {
            case 0 -> BC_FSTORE_0;
            case 1 -> BC_FSTORE_1;
            case 2 -> BC_FSTORE_2;
            case 3 -> BC_FSTORE_3;
            default -> bc(O_fstore, index);
            };
        }
    };
  }


  /**
   * Load double local variable from slot at given index.
   */
  public static Expr dload(int index)
  {
    return new Load()
      {
        public String toString() { return "dload"; }
        public JavaType type()
        {
          return PrimitiveType.type_double;
        }
        public byte[] byteCode(ClassFile cf)
        {
          return switch (index)
            {
            case 0 -> BC_DLOAD_0;
            case 1 -> BC_DLOAD_1;
            case 2 -> BC_DLOAD_2;
            case 3 -> BC_DLOAD_3;
            default -> bc(O_dload, index);
            };
        }
    };
  }


  /**
   * Store double local variable into slot at given index.
   */
  public static Expr dstore(int index)
  {
    return new Store()
      {
        public String toString() { return "dstore"; }
        public byte[] byteCode(ClassFile cf)
        {
          return switch (index)
            {
            case 0 -> BC_DSTORE_0;
            case 1 -> BC_DSTORE_1;
            case 2 -> BC_DSTORE_2;
            case 3 -> BC_DSTORE_3;
            default -> bc(O_dstore, index);
            };
        }
    };
  }


  /**
   * Load ref local variable from slot at given index.
   */
  public static Expr aload(int n, JavaType type)
  {
    return new Load()
      {
        public String toString() { return "aload"; }
        public JavaType type()
        {
          return type;
        }
        public byte[] byteCode(ClassFile cf)
        {
          return switch (n)
            {
            case 0 -> BC_ALOAD_0;
            case 1 -> BC_ALOAD_1;
            case 2 -> BC_ALOAD_2;
            case 3 -> BC_ALOAD_3;
            default -> bc(O_aload, n);
            };
        }
    };
  }


  /**
   * Store ref local variable into slot at given index.
   */
  public static Expr astore(int n)
  {
    return new Store()
      {
        public String toString() { return "astore"; }
        public byte[] byteCode(ClassFile cf)
        {
          return switch (n)
            {
            case 0 -> BC_ASTORE_0;
            case 1 -> BC_ASTORE_1;
            case 2 -> BC_ASTORE_2;
            case 3 -> BC_ASTORE_3;
            default -> bc(O_astore, n);
            };
        }
    };
  }


  /**
   * Load ref from ref array
   */
  public static Expr aaload(JavaType type)
  {
    return new Expr()
      {
        public String toString() { return "aaload"; }
        public JavaType type()
        {
          return type;
        }
        public byte[] byteCode(ClassFile cf)
        {
          return BC_AALOAD;
        }
    };
  }


  /**
   * Create new instance of given class.
   */
  public static Expr new0(String className, JavaType type)
  {
    if (PRECONDITIONS) require
      (className != null);
    return new Expr()
      {
        public String toString() { return "new0"; }
        public JavaType type()
        {
          return type;
        }
        public byte[] byteCode(ClassFile cf)
        {
          return bc(O_new, cf.cpClass(className));
        }
    };
  }


  /**
   * Create new array instance of given element class.
   */
  public static Expr anewarray(JavaType type)
  {
    if (PRECONDITIONS) require
      (type != null,
       !(type instanceof PrimitiveType));

    return new Expr()
      {
        public String toString() { return "anewarray"; }
        public JavaType type()
        {
          return type.array();
        }
        public byte[] byteCode(ClassFile cf)
        {
          return bc(O_anewarray, cf.cpClass(type.descriptor2()));
        }
    };
  }


  public static Expr branch(byte bc, Expr pos, Expr neg)
  {
    if (PRECONDITIONS) require
      (bc == ClassFileConstants.O_ifeq      ||
       bc == ClassFileConstants.O_ifne      ||
       bc == ClassFileConstants.O_iflt      ||
       bc == ClassFileConstants.O_ifge      ||
       bc == ClassFileConstants.O_ifgt      ||
       bc == ClassFileConstants.O_ifle      ||
       bc == ClassFileConstants.O_if_icmpeq ||
       bc == ClassFileConstants.O_if_icmpne ||
       bc == ClassFileConstants.O_if_icmplt ||
       bc == ClassFileConstants.O_if_icmpge ||
       bc == ClassFileConstants.O_if_icmpgt ||
       bc == ClassFileConstants.O_if_icmple ||
       bc == ClassFileConstants.O_if_acmpeq ||
       bc == ClassFileConstants.O_if_acmpne ||
       bc == ClassFileConstants.O_ifnull    ||
       bc == ClassFileConstants.O_ifnonnull   );

    return new Expr()
      {
        public String toString() { return "branch"; }
        public JavaType type()
        {
          return PrimitiveType.type_void;
        }
        public byte[] byteCode(ClassFile cf)
        {
          var pb = pos.byteCode(cf);
          var nb = neg.byteCode(cf);
          return bc(bc, pb, nb);
        }
    };
  }


  public static Expr checkcast(JavaType type)
  {
    return new Expr()
      {
        public String toString() { return "checkcast"; }
        public JavaType type()
        {
          return type;
        }
        public byte[] byteCode(ClassFile cf)
        {
          return bc(O_checkcast, cf.cpClass(type.descriptor2()));
        }
    };
  }


  /**
   * Create an empty, endless loop. This can be used for unreachable code to
   * trick the classfile verifier not to report errors after a call to, e.g.,
   * Runtime.fatal.
   */
  public static Expr endless_loop()
  {
    return new Expr()
      {
        public String toString() { return "endless_loop"; }
        public JavaType type()
        {
          return ClassFileConstants.PrimitiveType.type_void;
        }
        public byte[] byteCode(ClassFile cf)
        {
          return bcsigned(O_goto, 0);
        }
    };
  }



  /*-----------------------------  methods  -----------------------------*/


  /**
   * The type of the top value on the stack after the bytecodes were executed.
   */
  public abstract JavaType type();


  /**
   * Create a sequence of two Expr: `this` followed by `s`.
   *
   * @param s another Expr that is to be execute after this.
   */
  public Expr andThen(Expr s)
  {
    if (this == UNIT)
      {
        return s;
      }
    else if (s == UNIT)
      {
        return this;
      }
    else
      {
        return new Expr()
          {
            public String toString() { return "...andThen" + s; }
            public JavaType type() { return s.type();  }
            public byte[] byteCode(ClassFile cf)
            {
              return bc(Expr.this.byteCode(cf), s.byteCode(cf));
            };
          };
      }
  }


  /**
   * Create a sequence of this Expr, followed by a statement and a value from a
   * Pair<> of value and statement.
   *
   * @param p a pair of value and statement, both encoded as expr. value may be
   * null to indicate the statements do not return.
   *
   * @param this connected with p._v1 and, if non-null, with p._v0.
   */
  public Expr andThen(Pair<Expr,Expr> p)
  {
    var c = p._v1;
    var v = p._v0 == null ? UNIT : p._v0;
    return andThen(c)
          .andThen(v);
  }


  /**
   * Record that this sequence creates a value of given type.  This is needed
   * when the value is not obvious from the last Expr in a sequence, e.g., when
   * the sequence allocates a new instance and then initializes it but leaves on
   * ref to the instance on the stack.
   *
   * NYI: Instead of setting the type explicitly, Expr could instead provide a
   * way to describe its effect on the stack (e.g., putfield removes 2 top
   * entries) such that type() could use this information to walk back in a
   * sequence to find the type of the top of the stack.
   *
   * @param t the type of the value that remains on top of the stack after
   * executing this.
   */
  public Expr is(JavaType t)
  {
    return new Expr()
      {
        public String toString() { return Expr.this.toString() + "[" + t + "]"; }
        public JavaType type() { return t;  }
        public byte[] byteCode(ClassFile cf)
        {
          return Expr.this.byteCode(cf);
        };
      };
  }


  /**
   * Create a new Expr to evaluate all the side-effects of this Expr, then throw
   * away the result.
   */
  public Expr drop()
  {
    return this.andThen(type().pop());
  }


  /**
   * create code to get a field from the instance calculated by this Expr.
   *
   * This also works for field with type == type_void, in this case the instance
   * is evaluated and dropped.
   *
   * @param cls the class to the get the field form
   *
   * @param name the name of the field
   *
   * @param type the type of the field.
   *
   * @return a new Expr that evaluates this and then reads the specified field
   * or drops the value if type is void.
   */
  public Expr getFieldOrUnit(String cls, String name, JavaType type)
  {
    return type == ClassFileConstants.PrimitiveType.type_void
      ? drop()
      : this.andThen(getfield(cls, name, type));
  }


}

/* end of file */
