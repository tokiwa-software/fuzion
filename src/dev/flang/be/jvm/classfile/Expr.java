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

import java.util.Stack;

import dev.flang.be.jvm.classfile.ClassFile.StackMapTable;
import dev.flang.util.Errors;
import dev.flang.util.List;
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

  /*
   * A goto expression.
   */
  static class GoTo extends Expr
  {
    private final Label to;
    private final Label from;

    private GoTo(Label to, Label from)
    {
      this.to = to;
      this.from = from;
    }

    public String toString()
    {
      return "goto " + to;
    }

    public JavaType type()
    {
      return ClassFileConstants.PrimitiveType.type_void;
    }

    public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
    {
      if (CHECKS) check
        (from._posFinal == -1 || to._posFinal == -1 || (to._posFinal <= from._posFinal == isJumpBackwards()));

      code(ba, O_goto, from, to);
    }

    @Override
    public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
    {
      // add two frames at `from` and `to`.
      smt.stackMapFrames.add(new StackMapFullFrame(smt, from._posFinal));
      smt.stackMapFrames.add(new StackMapFullFrame(smt, to._posFinal));

      // this is true if a goto is jumping to another goto
      if (smt.stacks.containsKey(from._posFinal))
        {
          smt.stacks.put(to._posFinal, smt.stacks.get(from._posFinal));
        }
      else
        {
          // save the current stack
          smt.stacks.put(from._posFinal,(Stack) stack.clone());
          smt.stacks.put(to._posFinal,  (Stack) stack.clone());
        }

      // save the current locals
      smt.locals.add(new Pair<>(from._posFinal , locals.clone()));
      smt.locals.add(new Pair<>(to._posFinal , locals.clone()));

      var positionNextByteCode = from._posFinal + 3;

      if (CHECKS) check
        (isJumpBackwards() || smt.stacks.containsKey(positionNextByteCode));

      if (!isJumpBackwards())
        {
          // restore locals and stack to
          // previously saved states.
          locals.clear();
          stack.clear();
          smt.stacks
            .get(positionNextByteCode)
            .forEach(vti -> stack.add(vti));
          smt
            .unifiedLocals(positionNextByteCode)
            .forEach(vti -> locals.add(vti));
      }
    }


  }


  /**
   * Expression for the unit value.
   */
  static class Unit extends Expr
  {
    public String toString() { return "UNIT"; }
    public JavaType type() { return PrimitiveType.type_void; }
    public void code(ClassFile.ByteCodeWriter ba, ClassFile cf) { ba.write(BC_EMPTY); }
  }


  /**
   * abstract class for expression to load a constant from a CPool entry. Will
   * be implemented by anonymous classes inheriting form this.
   */
  static abstract class LoadConst extends Expr
  {
    abstract ClassFile.CPEntry cpEntry(ClassFile cf);
    public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
    {
      code(ba, O_ldc, cpEntry(cf));
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
    public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
    {
      ba.write(_bc);
    }

    @Override
    public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
    {
      switch (_bc[0])
        {
        case O_return, O_ireturn, O_lreturn, O_freturn, O_dreturn, O_areturn, O_athrow :
          stack.clear();
          break;
        case O_nop :
          break;
        case O_pop :
          stack.pop();
          break;
        case O_pop2 :
          // this is used only by long and double,
          // hence need to pop stack only once.
          stack.pop();
          break;
        case O_dup :
          stack.push(stack.peek());
          break;
        case O_dup_x1 :
          {
            // value2, value1 → value1, value2, value1
            var val1 = stack.pop();
            var val2 = stack.pop();
            stack.push(val1);
            stack.push(val2);
            stack.push(val1);
            break;
          }
        case O_dup_x2 :
          {
            // value3, value2, value1 → value1, value3, value2, value1
            var val1 = stack.pop();
            var val2 = stack.pop();
            var val3 = stack.pop();
            stack.push(val1);
            stack.push(val3);
            stack.push(val2);
            stack.push(val1);
            break;
          }
        case O_swap :
          {
            // value2, value1 → value1, value2
            var val1 = stack.pop();
            var val2 = stack.pop();
            stack.push(val1);
            stack.push(val2);
            break;
          }
        case O_iadd, O_ladd, O_fadd, O_dadd, O_isub, O_lsub, O_fsub, O_dsub,
             O_imul, O_lmul, O_fmul, O_dmul, O_idiv, O_ldiv, O_fdiv, O_ddiv,
             O_irem, O_lrem, O_frem, O_drem,
             O_ishl, O_lshl, O_ishr, O_lshr, O_iushr, O_lushr,
             O_iand, O_land, O_ior, O_lor, O_ixor, O_lxor :
          {
            stack.pop();
            break;
          }
        case O_ineg, O_lneg, O_fneg, O_dneg :
          break;
        case O_baload :
          {
            stack.pop();
            stack.pop();
            stack.push(PrimitiveType.type_boolean.vti(smt.classFile()));
            break;
          }
        case O_caload :
          {
            stack.pop();
            stack.pop();
            stack.push(PrimitiveType.type_char.vti(smt.classFile()));
            break;
          }
        case O_saload :
          {
            stack.pop();
            stack.pop();
            stack.push(PrimitiveType.type_short.vti(smt.classFile()));
            break;
          }
        case O_iaload :
          {
            stack.pop();
            stack.pop();
            stack.push(PrimitiveType.type_int.vti(smt.classFile()));
            break;
          }
        case O_laload :
          {
            stack.pop();
            stack.pop();
            stack.push(PrimitiveType.type_long.vti(smt.classFile()));
            break;
          }
        case O_faload :
          {
            stack.pop();
            stack.pop();
            stack.push(PrimitiveType.type_float.vti(smt.classFile()));
            break;
          }
        case O_daload :
          {
            stack.pop();
            stack.pop();
            stack.push(PrimitiveType.type_double.vti(smt.classFile()));
            break;
          }
        case O_arraylength :
          {
            stack.pop();
            stack.push(PrimitiveType.type_int.vti(smt.classFile()));
            break;
          }
        case O_bastore, O_castore, O_sastore, O_iastore, O_lastore, O_fastore, O_dastore, O_aastore :
          {
            stack.pop();
            stack.pop();
            stack.pop();
            break;
          }
        case O_newarray :
          {
            stack.pop();
            stack.push(type().vti(smt.classFile()));
            break;
          }
        case O_aconst_null :
          {
            stack.push(VerificationType.Null);
            break;
          }
        case O_lcmp :
          {
            // value1, value2 → result
            stack.pop();
            stack.pop();
            stack.push(VerificationType.Integer);
            break;
          }
        case O_monitorenter, O_monitorexit :
          {
            stack.pop();
            break;
          }
        default:
          throw new UnsupportedOperationException("Unimplemented method");
        }
    }
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


    /*
     * The index and verification type of the local that
     * is stored by this store expression.
     */
    public abstract Pair<Integer, VerificationType> local();


    @Override
    public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
    {
      stack.pop();

      while (locals.size() <= (local()._v0 + (local()._v1.needsTwoSlots() ? 1 : 0)))
        {
          locals.add(VerificationType.Top);
        }
      locals.set(local()._v0, local()._v1);
      if (local()._v1.needsTwoSlots())
        {
          locals.set(local()._v0+1, local()._v1);
        }
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
  public static final Expr DUP2_X1     = new Simple("DUP2_x1", PrimitiveType.type_void, BC_DUP2_X1);
  public static final Expr DUP2_X2     = new Simple("DUP2_x2", PrimitiveType.type_void, BC_DUP2_X2);

  public static final Expr IADD        = new Simple("BC_IADD" , PrimitiveType.type_int   , BC_IADD    );
  public static final Expr LADD        = new Simple("BC_LADD" , PrimitiveType.type_long  , BC_LADD    );
  public static final Expr FADD        = new Simple("BC_FADD" , PrimitiveType.type_float , BC_FADD    );
  public static final Expr DADD        = new Simple("BC_DADD" , PrimitiveType.type_double, BC_DADD    );
  public static final Expr ISUB        = new Simple("BC_ISUB" , PrimitiveType.type_int   , BC_ISUB    );
  public static final Expr LSUB        = new Simple("BC_LSUB" , PrimitiveType.type_long  , BC_LSUB    );
  public static final Expr FSUB        = new Simple("BC_FSUB" , PrimitiveType.type_float , BC_FSUB    );
  public static final Expr DSUB        = new Simple("BC_DSUB" , PrimitiveType.type_double, BC_DSUB    );
  public static final Expr IMUL        = new Simple("BC_IMUL" , PrimitiveType.type_int   , BC_IMUL    );
  public static final Expr LMUL        = new Simple("BC_LMUL" , PrimitiveType.type_long  , BC_LMUL    );
  public static final Expr FMUL        = new Simple("BC_FMUL" , PrimitiveType.type_float , BC_FMUL    );
  public static final Expr DMUL        = new Simple("BC_DMUL" , PrimitiveType.type_double, BC_DMUL    );
  public static final Expr IDIV        = new Simple("BC_IDIV" , PrimitiveType.type_int   , BC_IDIV    );
  public static final Expr LDIV        = new Simple("BC_LDIV" , PrimitiveType.type_long  , BC_LDIV    );
  public static final Expr FDIV        = new Simple("BC_FDIV" , PrimitiveType.type_float , BC_FDIV    );
  public static final Expr DDIV        = new Simple("BC_DDIV" , PrimitiveType.type_double, BC_DDIV    );
  public static final Expr IREM        = new Simple("BC_IREM" , PrimitiveType.type_int   , BC_IREM    );
  public static final Expr LREM        = new Simple("BC_LREM" , PrimitiveType.type_long  , BC_LREM    );
  public static final Expr FREM        = new Simple("BC_FREM" , PrimitiveType.type_float , BC_FREM    );
  public static final Expr DREM        = new Simple("BC_DREM" , PrimitiveType.type_double, BC_DREM    );
  public static final Expr INEG        = new Simple("BC_INEG" , PrimitiveType.type_int   , BC_INEG    );
  public static final Expr LNEG        = new Simple("BC_LNEG" , PrimitiveType.type_long  , BC_LNEG    );
  public static final Expr FNEG        = new Simple("BC_FNEG" , PrimitiveType.type_float , BC_FNEG    );
  public static final Expr DNEG        = new Simple("BC_DNEG" , PrimitiveType.type_double, BC_DNEG    );
  public static final Expr ISHL        = new Simple("BC_ISHL" , PrimitiveType.type_int   , BC_ISHL    );
  public static final Expr LSHL        = new Simple("BC_LSHL" , PrimitiveType.type_long  , BC_LSHL    );
  public static final Expr ISHR        = new Simple("BC_ISHR" , PrimitiveType.type_int   , BC_ISHR    );
  public static final Expr LSHR        = new Simple("BC_LSHR" , PrimitiveType.type_long  , BC_LSHR    );
  public static final Expr IUSHR       = new Simple("BC_IUSHR", PrimitiveType.type_int   , BC_IUSHR   );
  public static final Expr LUSHR       = new Simple("BC_LUSHR", PrimitiveType.type_long  , BC_LUSHR   );
  public static final Expr IAND        = new Simple("BC_IAND" , PrimitiveType.type_int   , BC_IAND    );
  public static final Expr LAND        = new Simple("BC_LAND" , PrimitiveType.type_long  , BC_LAND    );
  public static final Expr IOR         = new Simple("BC_IOR"  , PrimitiveType.type_int   , BC_IOR     );
  public static final Expr LOR         = new Simple("BC_LOR"  , PrimitiveType.type_long  , BC_LOR     );
  public static final Expr IXOR        = new Simple("BC_IXOR" , PrimitiveType.type_int   , BC_IXOR    );
  public static final Expr LXOR        = new Simple("BC_LXOR" , PrimitiveType.type_long  , BC_LXOR    );

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

  public static final Expr ACONST_NULL = new Simple("ACONST_NULL", NULL_TYPE, BC_ACONST_NULL);

  public static final Expr LCMP        = new Simple("LCMP", PrimitiveType.type_int, BC_LCMP);

  public static final Expr MONITORENTER = new Simple("MONITORENTER", PrimitiveType.type_void, BC_MONITORENTER);
  public static final Expr MONITOREXIT = new Simple("MONITOREXIT", PrimitiveType.type_void, BC_MONITOREXIT);


  /**
   * Flag to enable (or suppress, if false) comments in the bytecode, see
   * comment(String) for how to use this and dev.flang.be.jvm.JVM.CODE_COMMENTS
   * for how to enable this.
   */
  public static boolean ENABLE_COMMENTS = false;


  /*-------------------------  static methods  --------------------------*/


  /**
   * Create a comment in the bytecode with the given msg.
   *
   * Java bytecode does not have a comment mechanism. Instead, if
   * ENABLE_COMMENTS is true, this will create an ldc bytecode that loads the
   * message as a string constant followed by a pop bytecode to discard it.
   *
   * If ENABLED_COMMENTS is false, this will return UNIT.
   *
   * @param msg a message to be shown in the comment
   *
   * @return Expr that is effectively a NOP but that shows msg when disassembled,
   * e.g., using javap.
   */
  public static Expr comment(String msg)
  {
    if (ENABLE_COMMENTS)
      {
        return commentAlways(msg);
      }
    else
      {
        return UNIT;
      }
  }


  /**
   * Create a comment in the bytecode with the given msg.  This should usually
   * not be called directly, but via comment().  Direct calls are useful,
   * however, for debugging.
   *
   * Java bytecode does not have a comment mechanism. Instead, this will create
   * an ldc bytecode that loads the message as a string constant followed by a pop
   * bytecode to discard it.
   *
   * @param msg a message to be shown in the comment
   *
   * @return Expr that is effectively a NOP but that shows msg when disassembled,
   * e.g., using javap.
   */
  public static Expr commentAlways(String msg)
  {
    var s = stringconst(msg);
    return s.andThen(s.type().pop());
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          var c   = cf.cpUtf8(cls);
          var n   = cf.cpUtf8(name);
          var d   = cf.cpUtf8(descr);
          var cl  = cf.cpClass(c);
          var nat = cf.cpNameAndType(n, d);
          var m   = cf.cpMethod(cl, nat);
          code(ba, O_invokestatic, m);
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          var argCount = ClassFileConstants.argCount(descr);
          for (int index = 0; index < argCount; index++)
            {
              stack.pop();
            }
          stack.push(rt.vti(smt.classFile()));
        }
    };
  }


  /**
   * create invokespecial bytecode to call given class, name and descr producing
   * void result type.
   */
  public static Expr invokeSpecial(String cls, String name, String descr)
  {
    if (PRECONDITIONS) require
      (descr.endsWith(")V"));

    return new Expr()
      {
        public String toString() { return "invokeSpecial " + cls + "." + name; }
        public JavaType type() { return PrimitiveType.type_void; }
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          var c   = cf.cpUtf8(cls);
          var n   = cf.cpUtf8(name);
          var d   = cf.cpUtf8(descr);
          var cl  = cf.cpClass(c);
          var nat = cf.cpNameAndType(n, d);
          var m   = cf.cpMethod(cl, nat);
          code(ba, O_invokespecial, m);
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          var argCount = ClassFileConstants.argCount(descr);
          stack.pop();
          for (int index = 0; index < argCount; index++)
            {
              stack.pop();
            }
        }
    };
  }


  /**
   * create invokespecial bytecode to call given class, name and descr producing
   * void result type.
   */
  public static Expr invokeSpecial(ClassFile.CPClass cl, String name, String descr)
  {
    if (PRECONDITIONS) require
      (descr.equals("()V"));

    return new Expr()
      {
        public String toString() { return "invokeSpecial " + cl + "." + name; }
        public JavaType type() { return PrimitiveType.type_void;  }
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          var n   = cf.cpUtf8(name);
          var d   = cf.cpUtf8(descr);
          var nat = cf.cpNameAndType(n, d);
          var m   = cf.cpMethod(cl, nat);
          code(ba, O_invokespecial, m);
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          stack.pop();
        }
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          var c   = cf.cpUtf8(cls);
          var n   = cf.cpUtf8(name);
          var d   = cf.cpUtf8(descr);
          var cl  = cf.cpClass(c);
          var nat = cf.cpNameAndType(n, d);
          var m   = cf.cpMethod(cl, nat);
          code(ba, O_invokevirtual, m);
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          var argCount = ClassFileConstants.argCount(descr);
          stack.pop();
          for (int index = 0; index < argCount; index++)
            {
              stack.pop();
            }
          stack.push(rt.vti(smt.classFile()));
        }
    };
  }

  /**
   * create invokeinterface bytecode to call given class, name and descr producing
   * given result type on the stack.
   *
   * @param cls the name of the interface class we are calling
   *
   * @param name the name of the interface method we are calling
   *
   * @param descr the descriptor if the interface method we are calling
   *
   * param rt the JavaType of the result of call
   *
   * @return Code to produce bytecode for the interface call.
   */
  public static Expr invokeInterface(String cls, String name, String descr, JavaType rt)
  {
    return new Expr()
      {
        public String toString() { return "invokeInterface " + cls + "." + name; }
        public JavaType type() { return rt;  }
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          var c   = cf.cpUtf8(cls);
          var n   = cf.cpUtf8(name);
          var d   = cf.cpUtf8(descr);
          var cl  = cf.cpClass(c);
          var nat = cf.cpNameAndType(n, d);
          var m   = cf.cpInterfaceMethod(cl, nat);
          var count = 1 + ClassFileConstants.slotCountForArgs(descr);
          if (count > ClassFileConstants.MAX_INVOKE_INTERFACE_SLOTS)
            {
              Errors.fatal("Too many argument slots required for call to " + cls + "." + name + descr + ": " + count +
                           ", maximum allowed is " + ClassFileConstants.MAX_INVOKE_INTERFACE_SLOTS);
            }
          code(ba, O_invokeinterface, m, (byte) count, (byte) 0);
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          var argCount = ClassFileConstants.argCount(descr);
          stack.pop();
          for (int index = 0; index < argCount; index++)
            {
              stack.pop();
            }
          stack.push(rt.vti(smt.classFile()));
        }
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          code(ba, O_getfield, cf.cpField(cf.cpClass(cls), cf.cpNameAndType(name, type.descriptor())));
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          // objectref
          stack.pop();
          // value
          stack.push(type.vti(smt.classFile()));
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          code(ba, O_putfield, cf.cpField(cf.cpClass(cls), cf.cpNameAndType(name, type.descriptor())));
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          // objectref
          stack.pop();
          // value
          stack.pop();
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          code(ba, O_getstatic, cf.cpField(cf.cpClass(cls), cf.cpNameAndType(name, type.descriptor())));
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          stack.push(type.vti(smt.classFile()));
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          code(ba, O_putstatic, cf.cpField(cf.cpClass(cls), cf.cpNameAndType(name, type.descriptor())));
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          // value
          stack.pop();
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          switch (c)
            {
            case -1 -> ba.write(BC_ICONST_M1);
            case  0 -> ba.write(BC_ICONST_0);
            case  1 -> ba.write(BC_ICONST_1);
            case  2 -> ba.write(BC_ICONST_2);
            case  3 -> ba.write(BC_ICONST_3);
            case  4 -> ba.write(BC_ICONST_4);
            case  5 -> ba.write(BC_ICONST_5);
            default -> super.code(ba, cf);
            };
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          stack.push(VerificationType.Integer);
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          if      (c == 0L) { ba.write(BC_LCONST_0); }
          else if (c == 1L) { ba.write(BC_LCONST_1); }
          else              { super.code(ba, cf);    }
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          stack.push(VerificationType.Long);
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          if      (Float.intBitsToFloat(c) == 0F) { ba.write(BC_FCONST_0); }
          else if (Float.intBitsToFloat(c) == 1F) { ba.write(BC_FCONST_1); }
          else if (Float.intBitsToFloat(c) == 2F) { ba.write(BC_FCONST_2); }
          else                                    { super.code(ba, cf);    }
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          stack.push(VerificationType.Float);
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          if      (Double.longBitsToDouble(c) == 0F) { ba.write(BC_DCONST_0); }
          else if (Double.longBitsToDouble(c) == 1F) { ba.write(BC_DCONST_1); }
          else                                       { super.code(ba, cf);    }
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          stack.push(VerificationType.Double);
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
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          stack.push(new VerificationType(type().className(), ()->cpEntry(smt.classFile()).index()));
        }
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
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          stack.push(new VerificationType(type().className(), ()->cpEntry(smt.classFile()).index()));
        }
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
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          stack.push(new VerificationType(t.className(), ()->cpEntry(smt.classFile()).index()));
        }
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          switch (index)
            {
            case 0 -> ba.write(BC_ILOAD_0);
            case 1 -> ba.write(BC_ILOAD_1);
            case 2 -> ba.write(BC_ILOAD_2);
            case 3 -> ba.write(BC_ILOAD_3);
            default -> code(ba, O_iload, index);
            };
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          stack.push(VerificationType.Integer);
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          switch (index)
            {
            case 0 -> ba.write(BC_ISTORE_0);
            case 1 -> ba.write(BC_ISTORE_1);
            case 2 -> ba.write(BC_ISTORE_2);
            case 3 -> ba.write(BC_ISTORE_3);
            default -> code(ba, O_istore, index);
            };
        }
        @Override
        public Pair<Integer, VerificationType> local()
        {
          return new Pair<>(index, VerificationType.Integer);
        }
    };
  }


  /**
   * Load long local variable from slot at given index.
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          switch (index)
            {
            case 0 -> ba.write(BC_LLOAD_0);
            case 1 -> ba.write(BC_LLOAD_1);
            case 2 -> ba.write(BC_LLOAD_2);
            case 3 -> ba.write(BC_LLOAD_3);
            default -> code(ba, O_lload, index);
            };
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          stack.push(VerificationType.Long);
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          switch (index)
            {
            case 0 -> ba.write(BC_LSTORE_0);
            case 1 -> ba.write(BC_LSTORE_1);
            case 2 -> ba.write(BC_LSTORE_2);
            case 3 -> ba.write(BC_LSTORE_3);
            default -> code(ba, O_lstore, index);
            };
        }
        @Override
        public Pair<Integer, VerificationType> local()
        {
          return new Pair<>(index, VerificationType.Long);
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          switch (index)
            {
            case 0 -> ba.write(BC_FLOAD_0);
            case 1 -> ba.write(BC_FLOAD_1);
            case 2 -> ba.write(BC_FLOAD_2);
            case 3 -> ba.write(BC_FLOAD_3);
            default -> code(ba, O_fload, index);
            };
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          stack.push(VerificationType.Float);
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          switch (index)
            {
            case 0 -> ba.write(BC_FSTORE_0);
            case 1 -> ba.write(BC_FSTORE_1);
            case 2 -> ba.write(BC_FSTORE_2);
            case 3 -> ba.write(BC_FSTORE_3);
            default -> code(ba, O_fstore, index);
            };
        }
        @Override
        public Pair<Integer, VerificationType> local()
        {
          return new Pair<>(index, VerificationType.Float);
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          switch (index)
            {
            case 0 -> ba.write(BC_DLOAD_0);
            case 1 -> ba.write(BC_DLOAD_1);
            case 2 -> ba.write(BC_DLOAD_2);
            case 3 -> ba.write(BC_DLOAD_3);
            default -> code(ba, O_dload, index);
            };
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          stack.push(VerificationType.Double);
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          switch (index)
            {
            case 0 -> ba.write(BC_DSTORE_0);
            case 1 -> ba.write(BC_DSTORE_1);
            case 2 -> ba.write(BC_DSTORE_2);
            case 3 -> ba.write(BC_DSTORE_3);
            default -> code(ba, O_dstore, index);
            };
        }
        @Override
        public Pair<Integer, VerificationType> local()
        {
          return new Pair<>(index, VerificationType.Double);
        }
    };
  }


  /**
   * Load ref local variable from slot at given index.
   */
  public static Expr aload(int n, JavaType type, ClassFile cf)
  {
    if (PRECONDITIONS) require
      (type instanceof AType || type.className().equals("void"));

    return aload(n, type, type.vti(cf));
  }

  /**
   * Load ref local variable from slot at given index.
   */
  public static Expr aload(int n, JavaType type, VerificationType vti)
  {
    return new Load()
      {
        public String toString() { return "aload " + n; }
        public JavaType type()
        {
          return type;
        }
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          switch (n)
            {
            case 0 -> ba.write(BC_ALOAD_0);
            case 1 -> ba.write(BC_ALOAD_1);
            case 2 -> ba.write(BC_ALOAD_2);
            case 3 -> ba.write(BC_ALOAD_3);
            default -> code(ba, O_aload, n);
            };
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          stack.push(vti);
        }
    };
  }


  /**
   * Store ref local variable into slot at given index.
   */
  public static Expr astore(int n, VerificationType vti)
  {
    return new Store()
      {
        public String toString() { return "astore " + n; }
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          switch (n)
            {
            case 0 -> ba.write(BC_ASTORE_0);
            case 1 -> ba.write(BC_ASTORE_1);
            case 2 -> ba.write(BC_ASTORE_2);
            case 3 -> ba.write(BC_ASTORE_3);
            default -> code(ba, O_astore, n);
            };
        }
        @Override
        public Pair<Integer, VerificationType> local()
        {
          // vti is null if we store a void-like type.
          return new Pair<Integer,VerificationType>(n, vti == null ? VerificationType.Top : vti);
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          ba.write(BC_AALOAD);
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          // arrayref, index → value
          stack.pop();
          stack.pop();
          stack.push(type.vti(smt.classFile()));
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          code(ba, O_new, cf.cpClass(className));
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          stack.push(new VerificationType(className, ()->smt.classFile().cpClass(className).index()));
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          code(ba, O_anewarray, cf.cpClass(type.refDescriptor()));
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          // count → arrayref
          stack.pop();
          stack.push(type.vti(smt.classFile()));
        }
      };
  }


  /**
   * Create conditional branch with one Expr executed if the condition holds
   * (`pos`) and one if it does not (`neg`).
   *
   * @param bc a condition bytecode O_if*
   *
   * @param pos code to be executed if the condition holds.
   *
   * @param neg code to be executed if the condition does not hold.
   */
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

    Label lStart = new Label();
    Label lEnd   = new Label();
    Label lPos   = new Label();

    if (pos != UNIT && neg != UNIT)
      {
        neg = neg.andThen(gotoLabel(lEnd));
        pos = lPos.andThen(pos);
      }

    // effectively final vars for use in inner class:
    var fpos = pos;
    var fneg = neg;

    return lStart.andThen
      (new Expr()
        {
          public String toString() { return "branch(" + fneg.toString() + "," + fpos.toString() + ")"; }
          public JavaType type()
          {
            return PrimitiveType.type_void;
          }
          public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
          {
            if (fpos == UNIT)
              {
                code(ba, bc, lStart, lEnd);
                fneg.code(ba, cf);
              }
            else if (fneg == UNIT)
              {
                var nbc = switch (bc)
                  {
                  case O_ifeq        -> O_ifne     ;
                  case O_ifne        -> O_ifeq     ;
                  case O_iflt        -> O_ifge     ;
                  case O_ifge        -> O_iflt     ;
                  case O_ifgt        -> O_ifle     ;
                  case O_ifle        -> O_ifgt     ;
                  case O_if_icmpeq   -> O_if_icmpne;
                  case O_if_icmpne   -> O_if_icmpeq;
                  case O_if_icmplt   -> O_if_icmpge;
                  case O_if_icmpge   -> O_if_icmplt;
                  case O_if_icmpgt   -> O_if_icmple;
                  case O_if_icmple   -> O_if_icmpgt;
                  case O_if_acmpeq   -> O_if_acmpne;
                  case O_if_acmpne   -> O_if_acmpeq;
                  case O_ifnull      -> O_ifnonnull;
                  case O_ifnonnull   -> O_ifnull   ;
                  default -> throw new Error("unexpected bc "+bc);
                  };
                code(ba, nbc, lStart, lEnd);
                fpos.code(ba, cf);
              }
            else
              {
                code(ba, bc, lStart, lPos);
                fneg.code(ba, cf);
                fpos.code(ba, cf);
              }
          }

          @Override
          public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
          {
            // save stack and locals at branch
            smt.stacks.put(lStart._posFinal, (Stack)stack.clone());
            smt.locals.add(new Pair<>(lStart._posFinal, locals.clone()));

            // Case 1: there is ONLY fpos OR fneg, jump is to lEnd
            // Case 2: we have fpos AND fneg, jump is to positive branch lPos
            var jumpPosition = fpos == UNIT || fneg == UNIT ? lEnd._posFinal : lPos._posFinal;

            // add frames at branch and jump position.
            smt.stackMapFrames.add(new StackMapFullFrame(smt, lStart._posFinal));
            smt.stackMapFrames.add(new StackMapFullFrame(smt, jumpPosition));

            // pop the subject of the comparison
            stack.pop();
            if (bc == O_if_icmpeq ||
              bc == O_if_icmpne ||
              bc == O_if_icmplt ||
              bc == O_if_icmpge ||
              bc == O_if_icmpgt ||
              bc == O_if_icmple ||
              bc == O_if_acmpeq ||
              bc == O_if_acmpne)
              {
                stack.pop();
              }

            // save stack and locals at jump position
            smt.stacks.put(jumpPosition, (Stack<VerificationType>)stack.clone());
            smt.locals.add(new Pair<>(jumpPosition, locals.clone()));

            var fposLocals = locals.clone();
            var fnegLocals = locals.clone();

            // assumption here is that fneg and fpos
            // affect the stack in the same way.
            var stackPositive = (Stack<VerificationType>)stack.clone();
            fneg.buildStackMapTable(smt, stack, fnegLocals);
            fpos.buildStackMapTable(smt, stackPositive , fposLocals);

            // update the state of the locals to the union of fneg and fPos.
            var unifiedLocals = VerificationType.union(fnegLocals, fposLocals);
            for (int index = 0; index < unifiedLocals.size() && index < locals.size(); index++)
              {
                locals.set(index, unifiedLocals.get(index));
              }

          }
        })
      .andThen(lEnd);
  }

  /**
   * Create conditional branch with one Expr executed if the condition
   * holds. I.e., this typically results in a branch using the negated condition
   * that jumps behind the code given as `pos`.
   *
   * @param bc a condition bytecode O_if*
   *
   * @param pos code to be executed if the condition holds.
   */
  public static Expr branch(byte bc, Expr pos)
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

    return branch(bc, pos, UNIT);
  }

  public static Expr checkcast(JavaType type)
  {
    return new Expr()
      {
        // NYI checkcasts where isRedundant=true should
        // not be present in the first place...
        // isRedundant is set to true if the checkcast
        // checks for the exact same type that is on the stack.
        private boolean isRedundant = false;

        public String toString() { return "checkcast"; }
        public JavaType type()
        {
          return type;
        }
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          // verifier complains about casts
          // that cast to itself. In this case
          // we replace checkcasts by NOPs.
          if (!isRedundant)
            {
              code(ba, O_checkcast, cf.cpClass(type.refDescriptor()));
            }
          else
            {
              NOP.code(ba, cf);
              NOP.code(ba, cf);
              NOP.code(ba, cf);
            }
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack,
          List<VerificationType> locals)
        {
          // NYI: cleanup remove once the
          // backend does not generate redundant
          // checkcasts anymore.
          isRedundant = type().vti(smt.classFile()).compareTo(stack.peek()) == 0;
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
    Label l = new Label();
    return new GoTo(l, l)
      {
        @Override
        protected boolean isJumpBackwards()
        {
          return true;
        }
      };
  }


  /**
   * A jump forward in the bytecode.
   * @param to the place where to jump to.
   */
  public static Expr gotoLabel(Label to)
  {
    Label from = new Label();
    return from.andThen(new GoTo(to, from));
  }


  /**
   * A jump backward in the bytecode.
   * @param to the place where to jump to.
   */
  public static Expr goBacktoLabel(Label to)
  {
    Label from = new Label();
    return from.andThen(new GoTo(to, from) {
      @Override
      protected boolean isJumpBackwards()
      {
        return true;
      }
    });
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
    if (PRECONDITIONS) require
      (s != null);

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
            public String toString() { return Expr.this + "->" + s; }
            public JavaType type() { return s.type(); }
            public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
            {
              Expr.this.code(ba, cf);
              if (!Expr.this.isJumpBackwards())
                {
                  s.code(ba, cf);
                }
            }
            @Override
            public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
            {
              Expr.this.buildStackMapTable(smt, stack, locals);
              if (!Expr.this.isJumpBackwards())
                {
                  s.buildStackMapTable(smt, stack, locals);
                }
            }
            @Override
            protected boolean isJumpBackwards()
            {
              return s.isJumpBackwards();
            }
          };
      }
  }


  /**
   * Is this expression denoting a jump backwards in the byte code?
   *
   * In case we encounter a jump backwards we stop writing
   * byte code. This is necessary since otherwise a stackmapframe
   * would be required at the bytecode following the jump.
   */
  protected boolean isJumpBackwards()
  {
    return false;
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
        public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
        {
          Expr.this.code(ba, cf);
        }
        @Override
        public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
        {
          Expr.this.buildStackMapTable(smt, stack, locals);
        }
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


  /**
   * This method is for traversing the code for
   * building a stackmap table.
   *
   * The stack and locals are updated by expressions during traversal of the code.
   *
   * When a goto or branch is encountered, stack map frames are added
   * to the stackmap tables and stack and locals are adjusted appropriately.
   *
   * @param smt the stackmap table we are building
   * @param stack the current state of the stack
   * @param locals the current state of the local variables
   */
  public void buildStackMapTable(StackMapTable smt, Stack<VerificationType> stack, List<VerificationType> locals)
  {

  }


}

/* end of file */
