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
import dev.flang.util.Errors;
import dev.flang.util.List;


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
  static byte[] BC_POP         = new byte[] { O_pop                 };
  static byte[] BC_POP2        = new byte[] { O_pop2                };

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
   * Create byte[] for bytecode instruction followed by 2 unsigned bytes giving
   * the index of the given CPool entry.
   *
   * @param bc a bytecode operation that expects an unsigned index
   *
   * @param e the CPool entry whose index bc expects
   *
   * @return new byte[] { bx, hi-index, ho-index }
   */
  byte[] bc(byte bc, ClassFile.CPEntry e)
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

    return bc(bc, e.index());
  }


  /**
   * Create byte[] for bytecode instruction followed by a 1 or 2 bytes long
   * unsigned integer index.
   *
   * @param bc a bytecode operation that expects an unsigned integer index
   *
   * @param index the unsigned integer
   *
   * @return depending on bc and the value of index, either
   *
   *           new byte[] { bc, index }                          -- or --
   *           new byte[] { O_wide, bc, hi-index, ho-index }
   *           new byte[] { bc, hi-index, ho-index }
   */
  byte[] bc(byte bc, int index)
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

    return switch (bc)
      {
      case
        O_iload, O_istore,
        O_lload, O_lstore,
        O_fload, O_fstore,
        O_dload, O_dstore,
        O_aload, O_astore -> index <= 0xff ? new byte[] { bc, (byte) index }
                                           : new byte[] { O_wide,
                                                          bc,
                                                          (byte) (index >> 8),
                                                          (byte) index
                                                        };
      case
        O_ldc             -> index <= 0xff ? new byte[] { O_ldc, (byte) index }
                                           : new byte[] { O_ldc_w,
                                                          (byte) (index >> 8),
                                                          (byte) index
                                                        };
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
        O_ldc2_w          -> new byte[] { bc,
                                          (byte) (index >> 8),
                                          (byte)  index
                                        };
      default             -> throw new Error("bc(bc,index) unexpected bytecode "+Integer.toHexString(bc));
      };
  }


  /**
   * Create byte[] for bytecode instruction followed by a 2 bytes long
   * signed integer offset.
   *
   * @param bc a bytecode operation that expects an unsigned integer index
   *
   * @param offset the signed integer
   *
   * @return new byte[] { bc, hi-offset, ho-offset }
   */
  byte[] bcsigned(byte bc, int offset)
  {
    if (PRECONDITIONS) require
      (offset >= -0x8000 && offset < 0x8000,
       switch (bc)
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

    return new byte[]
      { bc,
        (byte) (offset >> 8),
        (byte)  offset
      };
  }


  /**
   * Create byte[] of O_invokeinterface
   *
   * @param bc O_invokeinterface.
   *
   * @param e InterfaceMethod CPool entry
   *
   * @param b1 arg count
   *
   * @param b2 0
   */
  byte[] bc(byte bc, ClassFile.CPEntry e, byte b1, byte b2)
  {
    if (PRECONDITIONS) require
      (bc == O_invokeinterface,
       (0xff & b1) > 0,
       b2 == 0);

    return bc(bc, e.index(), b1, b2);
  }

  /**
   * Create byte[] of O_invokeinterface
   *
   * @param bc O_invokeinterface.
   *
   * @param index of InterfaceMethod CPool entry
   *
   * @param b1 arg count
   *
   * @param b2 0
   */
  byte[] bc(byte bc, int index, byte b1, byte b2)
  {
    if (PRECONDITIONS) require
      (bc == O_invokeinterface,
       index >= 0 && index < 0x10000,
       (0xff & b1) > 0,
       b2 == 0);

    return new byte[]
      { bc,
        (byte) (index >> 8),
        (byte)  index,
                b1,
                b2
      };
  }


  /**
   * Create a new bc array by appending b to a.
   *
   * @param a a bytecode array
   *
   * @param b another bytecode array
   *
   * @return a new bytecode array
   */
  byte[] bc(byte[] a, byte[] b)
  {
    var res = new byte[a.length + b.length];
    System.arraycopy(a, 0, res, 0,        a.length);
    System.arraycopy(b, 0, res, a.length, b.length);

    return res;
  }


  /**
   * Create bytecode array for code `if (cc) pos else neg`.
   *
   * @param bc a conditional branch bytecode.
   *
   * @param pos bytecode to execute in case bc's condition is true
   *
   * @param neg bytecode to execute in case bc's condition is false
   */
  byte[] bc(byte bc, byte[] pos, byte[] neg)
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
             O_ifnonnull   -> true;
         default           -> false;
         });

    byte[] res;
    if (pos.length == 0)
      {
        res = bc(bc, neg);
      }
    else if (neg.length == 0)
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
          default -> throw new Error("unexpecgted bc "+bc);
          };
        res = bc(nbc, pos);
      }
    else
      {
        res = new byte[6 + neg.length + pos.length];
        var cc = bcsigned(bc    , 6 + neg.length);
        var gt = bcsigned(O_goto, 3 + pos.length);
        System.arraycopy(cc  , 0, res, 0             , 3);
        System.arraycopy(neg , 0, res, 3             , neg.length);
        System.arraycopy(gt  , 0, res, 3 + neg.length, 3);
        System.arraycopy(pos , 0, res, 6 + neg.length, pos.length);
      }
    return res;
  }


  /**
   * Create bytecode array for code `if (!cc) neg`.
   *
   * @param bc a conditional branch bytecode.
   *
   * @param neg bytecode to execute in case bc's condition is false
   */
  byte[] bc(byte bc, byte[] neg)
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
             O_ifnonnull   -> true;
         default           -> false;
         });

    var res = new byte[3 + neg.length];
    var cc = bcsigned(bc    , 3 + neg.length);
    System.arraycopy(cc  , 0, res, 0             , 3);
    System.arraycopy(neg , 0, res, 3             , neg.length);

    return res;
  }


  /**
   * The byte code for this ByteCode.
   *
   * NYI: This should better be a byteCodeCollector with an argument of type
   * Kaku to avoid exponential runtime when copying nested conditionals
   */
  public abstract byte[] byteCode(ClassFile cf);


  /**
   * NYI: determine the max stack use of the bytecodes.
   */
  public int max_stack()
  {
    // NYI: ByteCode.max_stack/max_locals not implemented yet, just using 10
    return 20;
  }

  /**
   * NYI: determine the max local index used by the bytecodes.
   */
  public int max_locals()
  {
    // NYI: ByteCode.max_stack/max_locals not implemented yet, just using 10
    return 10;
  }

}

/* end of file */
