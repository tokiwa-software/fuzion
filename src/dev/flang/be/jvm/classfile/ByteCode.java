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
 * Source of class ByteCode
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm.classfile;

import dev.flang.util.ANY;


/**
 * ByteCode represents Java bytecode
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
abstract class ByteCode extends ANY implements ClassFileConstants
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  /**
   * Frequently used byte codes wrapped into byte[]:
   */
  static byte[] BC_EMPTY       = new byte[] {                       };
  static byte[] BC_RETURN      = new byte[] { O_return              };
  static byte[] BC_ICONST_M1   = new byte[] { O_iconst_m1           };
  static byte[] BC_ICONST_0    = new byte[] { O_iconst_0            };
  static byte[] BC_ICONST_1    = new byte[] { O_iconst_1            };
  static byte[] BC_ICONST_2    = new byte[] { O_iconst_2            };
  static byte[] BC_ICONST_3    = new byte[] { O_iconst_3            };
  static byte[] BC_ICONST_4    = new byte[] { O_iconst_4            };
  static byte[] BC_ICONST_5    = new byte[] { O_iconst_5            };
  static byte[] BC_FCONST_0    = new byte[] { O_fconst_0            };
  static byte[] BC_FCONST_1    = new byte[] { O_fconst_1            };
  static byte[] BC_FCONST_2    = new byte[] { O_fconst_2            };
  static byte[] BC_LCONST_0    = new byte[] { O_lconst_0            };
  static byte[] BC_LCONST_1    = new byte[] { O_lconst_1            };
  static byte[] BC_DCONST_0    = new byte[] { O_dconst_0            };
  static byte[] BC_DCONST_1    = new byte[] { O_dconst_1            };

  static byte[] BC_ALOAD_0     = new byte[] { O_aload_0             };
  static byte[] BC_ALOAD_1     = new byte[] { O_aload_1             };
  static byte[] BC_ALOAD_2     = new byte[] { O_aload_2             };
  static byte[] BC_ALOAD_3     = new byte[] { O_aload_3             };
  static byte[] BC_ASTORE_0    = new byte[] { O_astore_0            };
  static byte[] BC_ASTORE_1    = new byte[] { O_astore_1            };
  static byte[] BC_ASTORE_2    = new byte[] { O_astore_2            };
  static byte[] BC_ASTORE_3    = new byte[] { O_astore_3            };
  static byte[] BC_ARETURN     = new byte[] { O_areturn             };

  static byte[] BC_ILOAD_0     = new byte[] { O_iload_0             };
  static byte[] BC_ILOAD_1     = new byte[] { O_iload_1             };
  static byte[] BC_ILOAD_2     = new byte[] { O_iload_2             };
  static byte[] BC_ILOAD_3     = new byte[] { O_iload_3             };
  static byte[] BC_ISTORE_0    = new byte[] { O_istore_0            };
  static byte[] BC_ISTORE_1    = new byte[] { O_istore_1            };
  static byte[] BC_ISTORE_2    = new byte[] { O_istore_2            };
  static byte[] BC_ISTORE_3    = new byte[] { O_istore_3            };
  static byte[] BC_IRETURN     = new byte[] { O_ireturn             };

  static byte[] BC_LLOAD_0     = new byte[] { O_lload_0             };
  static byte[] BC_LLOAD_1     = new byte[] { O_lload_1             };
  static byte[] BC_LLOAD_2     = new byte[] { O_lload_2             };
  static byte[] BC_LLOAD_3     = new byte[] { O_lload_3             };
  static byte[] BC_LSTORE_0    = new byte[] { O_lstore_0            };
  static byte[] BC_LSTORE_1    = new byte[] { O_lstore_1            };
  static byte[] BC_LSTORE_2    = new byte[] { O_lstore_2            };
  static byte[] BC_LSTORE_3    = new byte[] { O_lstore_3            };
  static byte[] BC_LRETURN     = new byte[] { O_lreturn             };

  static byte[] BC_FLOAD_0     = new byte[] { O_fload_0             };
  static byte[] BC_FLOAD_1     = new byte[] { O_fload_1             };
  static byte[] BC_FLOAD_2     = new byte[] { O_fload_2             };
  static byte[] BC_FLOAD_3     = new byte[] { O_fload_3             };
  static byte[] BC_FSTORE_0    = new byte[] { O_fstore_0            };
  static byte[] BC_FSTORE_1    = new byte[] { O_fstore_1            };
  static byte[] BC_FSTORE_2    = new byte[] { O_fstore_2            };
  static byte[] BC_FSTORE_3    = new byte[] { O_fstore_3            };
  static byte[] BC_FRETURN     = new byte[] { O_freturn             };

  static byte[] BC_DLOAD_0     = new byte[] { O_dload_0             };
  static byte[] BC_DLOAD_1     = new byte[] { O_dload_1             };
  static byte[] BC_DLOAD_2     = new byte[] { O_dload_2             };
  static byte[] BC_DLOAD_3     = new byte[] { O_dload_3             };
  static byte[] BC_DSTORE_0    = new byte[] { O_dstore_0            };
  static byte[] BC_DSTORE_1    = new byte[] { O_dstore_1            };
  static byte[] BC_DSTORE_2    = new byte[] { O_dstore_2            };
  static byte[] BC_DSTORE_3    = new byte[] { O_dstore_3            };
  static byte[] BC_DRETURN     = new byte[] { O_dreturn             };

  static byte[] BC_NOP         = new byte[] { O_nop                 };

  static byte[] BC_DUP         = new byte[] { O_dup                 };
  static byte[] BC_DUP_X1      = new byte[] { O_dup_x1              };
  static byte[] BC_DUP_X2      = new byte[] { O_dup_x2              };
  static byte[] BC_SWAP        = new byte[] { O_swap                };
  static byte[] BC_DUP2_X1     = new byte[] { O_dup2_x1             };
  static byte[] BC_DUP2_X2     = new byte[] { O_dup2_x2             };
  static byte[] BC_POP         = new byte[] { O_pop                 };
  static byte[] BC_POP2        = new byte[] { O_pop2                };

  static byte[] BC_IADD        = new byte[] { O_iadd                };
  static byte[] BC_LADD        = new byte[] { O_ladd                };
  static byte[] BC_FADD        = new byte[] { O_fadd                };
  static byte[] BC_DADD        = new byte[] { O_dadd                };
  static byte[] BC_ISUB        = new byte[] { O_isub                };
  static byte[] BC_LSUB        = new byte[] { O_lsub                };
  static byte[] BC_FSUB        = new byte[] { O_fsub                };
  static byte[] BC_DSUB        = new byte[] { O_dsub                };
  static byte[] BC_IMUL        = new byte[] { O_imul                };
  static byte[] BC_LMUL        = new byte[] { O_lmul                };
  static byte[] BC_FMUL        = new byte[] { O_fmul                };
  static byte[] BC_DMUL        = new byte[] { O_dmul                };
  static byte[] BC_IDIV        = new byte[] { O_idiv                };
  static byte[] BC_LDIV        = new byte[] { O_ldiv                };
  static byte[] BC_FDIV        = new byte[] { O_fdiv                };
  static byte[] BC_DDIV        = new byte[] { O_ddiv                };
  static byte[] BC_IREM        = new byte[] { O_irem                };
  static byte[] BC_LREM        = new byte[] { O_lrem                };
  static byte[] BC_FREM        = new byte[] { O_frem                };
  static byte[] BC_DREM        = new byte[] { O_drem                };
  static byte[] BC_INEG        = new byte[] { O_ineg                };
  static byte[] BC_LNEG        = new byte[] { O_lneg                };
  static byte[] BC_FNEG        = new byte[] { O_fneg                };
  static byte[] BC_DNEG        = new byte[] { O_dneg                };
  static byte[] BC_ISHL        = new byte[] { O_ishl                };
  static byte[] BC_LSHL        = new byte[] { O_lshl                };
  static byte[] BC_ISHR        = new byte[] { O_ishr                };
  static byte[] BC_LSHR        = new byte[] { O_lshr                };
  static byte[] BC_IUSHR       = new byte[] { O_iushr               };
  static byte[] BC_LUSHR       = new byte[] { O_lushr               };
  static byte[] BC_IAND        = new byte[] { O_iand                };
  static byte[] BC_LAND        = new byte[] { O_land                };
  static byte[] BC_IOR         = new byte[] { O_ior                 };
  static byte[] BC_LOR         = new byte[] { O_lor                 };
  static byte[] BC_IXOR        = new byte[] { O_ixor                };
  static byte[] BC_LXOR        = new byte[] { O_lxor                };

  static byte[] BC_ATHROW      = new byte[] { O_athrow              };

  static byte[] BC_ARRAYLENGTH = new byte[] { O_arraylength         };

  static byte[] BC_BALOAD      = new byte[] { O_baload              };
  static byte[] BC_CALOAD      = new byte[] { O_caload              };
  static byte[] BC_SALOAD      = new byte[] { O_saload              };
  static byte[] BC_IALOAD      = new byte[] { O_iaload              };
  static byte[] BC_LALOAD      = new byte[] { O_laload              };
  static byte[] BC_FALOAD      = new byte[] { O_faload              };
  static byte[] BC_DALOAD      = new byte[] { O_daload              };
  static byte[] BC_AALOAD      = new byte[] { O_aaload              };

  static byte[] BC_BASTORE     = new byte[] { O_bastore             };
  static byte[] BC_CASTORE     = new byte[] { O_castore             };
  static byte[] BC_SASTORE     = new byte[] { O_sastore             };
  static byte[] BC_IASTORE     = new byte[] { O_iastore             };
  static byte[] BC_LASTORE     = new byte[] { O_lastore             };
  static byte[] BC_FASTORE     = new byte[] { O_fastore             };
  static byte[] BC_DASTORE     = new byte[] { O_dastore             };
  static byte[] BC_AASTORE     = new byte[] { O_aastore             };

  static byte[] BC_ZNEWARRAY   = new byte[] { O_newarray, T_BOOLEAN };
  static byte[] BC_BNEWARRAY   = new byte[] { O_newarray, T_BYTE    };
  static byte[] BC_CNEWARRAY   = new byte[] { O_newarray, T_CHAR    };
  static byte[] BC_SNEWARRAY   = new byte[] { O_newarray, T_SHORT   };
  static byte[] BC_INEWARRAY   = new byte[] { O_newarray, T_INTEGER };
  static byte[] BC_LNEWARRAY   = new byte[] { O_newarray, T_LONG    };
  static byte[] BC_FNEWARRAY   = new byte[] { O_newarray, T_FLOAT   };
  static byte[] BC_DNEWARRAY   = new byte[] { O_newarray, T_DOUBLE  };

  static byte[] BC_ACONST_NULL = new byte[] { O_aconst_null         };

  static byte[] BC_LCMP        = new byte[] { O_lcmp                };

  static byte[] BC_MONITORENTER = new byte[] { O_monitorenter       };
  static byte[] BC_MONITOREXIT = new byte[] { O_monitorexit         };


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor, does not do much.
   */
  public ByteCode()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Write bytecode instruction bc followed by 2 unsigned bytes giving the index
   * of the given CPool entry.
   *
   * @param bw target to write bytecodes to.
   *
   * @param bc a bytecode operation that expects an unsigned index
   *
   * @param e the CPool entry whose index bc expects
   *
   * @return new byte[] { bx, hi-index, ho-index }
   */
  void code(ClassFile.ByteCodeWriter ba, byte bc, ClassFile.CPEntry e)
  {
    if (PRECONDITIONS) require
      (e != null,
       switch (bc)
         {
         case
           O_invokespecial,
           O_invokestatic,
           O_invokevirtual,
           O_getfield,
           O_putfield,
           O_getstatic,
           O_putstatic,
           O_new,
           O_anewarray,
           O_checkcast,
           O_instanceof,
           O_ldc           -> true;
         default           -> false;
         });

    if (e.slots() == 2)
      {
        if (CHECKS) check
          (bc == O_ldc);

        bc = O_ldc2_w;
      }

    code(ba, bc, e.index());
  }


  /**
   * Write bytecode instruction bc followed by a 1 or 2 bytes long
   * unsigned integer index.
   *
   * Depending on bc and the value of index, the data written is
   *
   *           new byte[] { bc, index }                          -- or --
   *           new byte[] { O_wide, bc, hi-index, ho-index }     -- or --
   *           new byte[] { bc, hi-index, ho-index }
   *
   * @param bw target to write bytecodes to.
   *
   * @param bc a bytecode operation that expects an unsigned integer index
   *
   * @param index the unsigned integer
   *
   */
  void code(ClassFile.ByteCodeWriter ba, byte bc, int index)
  {
    if (PRECONDITIONS) require
      (index >= 0 && index < 0x10000,
       switch (bc)
         {
         case
           O_iload, O_istore,
           O_lload, O_lstore,
           O_fload, O_fstore,
           O_dload, O_dstore,
           O_aload, O_astore,
           O_invokespecial,
           O_invokestatic,
           O_invokevirtual,
           O_getfield,
           O_putfield,
           O_getstatic,
           O_putstatic,
           O_new,
           O_anewarray,
           O_checkcast,
           O_instanceof,
           O_ldc,
           O_ldc2_w        -> true;
         default           -> false;
         });

    switch (bc)
      {
      case
        O_iload, O_istore,
        O_lload, O_lstore,
        O_fload, O_fstore,
        O_dload, O_dstore,
        O_aload, O_astore -> {
                               if (index <= 0xff)
                                 {
                                   ba.write(bc);
                                   ba.writeU1(index);
                                 }
                               else
                                 {
                                   ba.write(O_wide);
                                   ba.write(bc);
                                   ba.writeU2(index);
                                 }
                              }

      case
        O_ldc             -> {
                               if (index <= 0xff)
                                 {
                                   ba.write(O_ldc);
                                   ba.writeU1(index);
                                 }
                               else
                                 {
                                   ba.write(O_ldc_w);
                                   ba.writeU2(index);
                                 }
                              }
      case
        O_invokespecial,
        O_invokestatic,
        O_invokevirtual,
        O_getfield,
        O_putfield,
        O_getstatic,
        O_putstatic,
        O_new,
        O_anewarray,
        O_checkcast,
        O_instanceof,
        O_ldc2_w           -> {
                                ba.write(bc);
                                ba.writeU2(index);
                              }
      default              -> throw new Error("bc(bc,index) unexpected bytecode "+Integer.toHexString(bc));
      };
  }


  /**
   * Write bytecode instruction bc followed by a 2 bytes long
   * signed integer offset.
   *
   * @param bw target to write bytecodes to.
   *
   * @param bc a bytecode operation that expects an unsigned integer index
   *
   * @param offset the signed integer
   *
   * @return new byte[] { bc, hi-offset, ho-offset }
   */
  void code(ClassFile.ByteCodeWriter bw, byte bc, Label from, Label to)
  {
    if (PRECONDITIONS) require
      (switch (bc)
         { case
             O_ifeq        ,
             O_ifne        ,
             O_iflt        ,
             O_ifge        ,
             O_ifgt        ,
             O_ifle        ,
             O_if_icmpeq   ,
             O_if_icmpne   ,
             O_if_icmplt   ,
             O_if_icmpge   ,
             O_if_icmpgt   ,
             O_if_icmple   ,
             O_if_acmpeq   ,
             O_if_acmpne   ,
             O_ifnull      ,
             O_ifnonnull   ,
             O_goto        -> true;
         default           -> false;
         });

    // NYI: UNDER_DEVELOPMENT: Support for goto_w
    int offset = bw.offset(from, to);
    bw.write(bc);
    bw.write((byte) (offset >> 8));
    bw.write((byte)  offset      );
  }


  /**
   * Create byte[] of O_invokeinterface
   *
   * @param bw target to write bytecodes to.
   *
   * @param bc O_invokeinterface.
   *
   * @param e InterfaceMethod CPool entry
   *
   * @param b1 arg count
   *
   * @param b2 0
   */
  void code(ClassFile.ByteCodeWriter bw, byte bc, ClassFile.CPEntry e, byte b1, byte b2)
  {
    if (PRECONDITIONS) require
      (bc == O_invokeinterface,
       (0xff & b1) > 0,
       b2 == 0);

    code(bw, bc, e.index(), b1, b2);
  }

  /**
   * Create byte[] of O_invokeinterface
   *
   * @param bw target to write bytecodes to.
   *
   * @param bc O_invokeinterface.
   *
   * @param index of InterfaceMethod CPool entry
   *
   * @param b1 arg count
   *
   * @param b2 0
   */
  void code(ClassFile.ByteCodeWriter bw, byte bc, int index, byte b1, byte b2)
  {
    bw.write(bc);
    bw.writeU2(index);
    bw.write(b1);
    bw.write(b2);
  }


  /**
   * Create the byte code for this ByteCode.
   *
   * This will run several passes with different instances of ByteCodeWrite
   * where first an ByteCodeSizeEstimate is used to get an upper bound for the
   * bytecode size, then ByteCodeFixLabels is used to find all branch targets
   * and fix the layout, and finally ByteCodeWrite is used to write the
   * bytecodes to a file.
   *
   * @param bw the writer to write the bytecode to.
   *
   * @param cf the class file we are generating, used for cpool indices.
   */
  public abstract void code(ClassFile.ByteCodeWriter bw, ClassFile cf);


}

/* end of file */
