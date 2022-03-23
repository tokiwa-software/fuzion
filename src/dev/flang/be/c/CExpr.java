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
 * Source of class CExpr
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;

import dev.flang.util.ANY;
import dev.flang.util.List;

import java.nio.charset.StandardCharsets;

/**
 * CExpr provides infrastructure to generate C code expressions
 *
 * The idea here is to have a compiler that works backwards: Instead of parsing
 * source code into an AST, we create an AST for C and then create source code
 * from this.
 *
 * For C expressions, this greatly simplifies the handling of operator
 * precedence: Creating expressions as strings directly typically means you have
 * to put parentheses around every sub-expression. Using an AST, we can instead
 * create the minimum number of parentheses, resulting in cleaner C code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
abstract class CExpr extends CStmnt
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Dummy value to be used for unit type values.  This cannot be used to create
   * code since C does not have a unit type.
   */
  static CExpr UNIT = new CExpr()
    {
      void code(CString sb) { sb.append("/* UNIT VALUE */");  }
      int precedence() { return 0; }
    };


  /*----------------------------  producers  ----------------------------*/


  /**
   * Create a C expression from dummy text.  This will most likely not compile,
   * but the dummy text can be used to show what needs to be done to make this
   * compile.
   *
   * @return the resulting expression
   */
  static CExpr dummy(String text)
  {
    return new CExpr()
      {
        void code(CString sb) { sb.append("/* ").append(text).append(" */"); }
        int precedence() { return 0; }
      };
  }


  /**
   * Create a C expression from a plain identifier
   *
   * @return the resulting expression
   */
  static CExpr ident(String name)
  {
    if (PRECONDITIONS) require
      (isAlphanumeric(name));

    return new CIdent(name);
  }


  /**
   * Create a C expression from a int8_t constant
   *
   * @return the resulting expression
   */
  static CExpr int8const(int value)
  {
    return new CExpr()
      {
        void code(CString sb) { sb.append("((int8_t) ").append(value).append(")"); }
        int precedence() { return 0; }
    };
  }


  /**
   * Create a C expression from a int16_t constant
   *
   * @return the resulting expression
   */
  static CExpr int16const(int value)
  {
    return new CExpr()
      {
        void code(CString sb) { sb.append("((int16_t) ").append(value).append(")"); }
        int precedence() { return 0; }
    };
  }


  /**
   * Create a C expression from a int32_t constant
   *
   * @return the resulting expression
   */
  static CExpr int32const(int value)
  {
    return new CExpr()
      {
        void code(CString sb) { sb.append(value); }
        int precedence() { return 0; }
    };
  }


  /**
   * Create a C expression from a int64_t constant
   *
   * @return the resulting expression
   */
  static CExpr int64const(long value)
  {
    return new CExpr()
      {
        void code(CString sb)
        {
          if (value == Long.MIN_VALUE)
            {
              // workaround to avoid clang warning 'integer literal is too large
              // to be represented in a signed integer type, interpreting as
              // unsigned [-Wimplicitly-unsigned-literal]'
              sb.append("((int64_t)").append(value).append("ULL)");
            }
          else
            {
              sb.append(value).append("LL");
            }
        }
        int precedence() { return 0; }
    };
  }


  /**
   * Create a C expression from a uint16_t constant
   *
   * @return the resulting expression
   */
  static CExpr uint8const(int value)
  {
    return new CExpr()
      {
        void code(CString sb) { sb.append("((uint8_t)").append(value & 0xff).append('U').append(")"); }
        int precedence() { return 0; }
      };
  }


  /**
   * Create a C expression from a uint16_t constant
   *
   * @return the resulting expression
   */
  static CExpr uint16const(int value)
  {
    return new CExpr()
      {
        void code(CString sb) { sb.append("((uint16_t)").append(value & 0xffff).append('U').append(")"); }
        int precedence() { return 0; }
      };
  }


  /**
   * Create a C expression from a uint32_t constant
   *
   * @return the resulting expression
   */
  static CExpr uint32const(int value)
  {
    return new CExpr()
      {
        void code(CString sb) { sb.append(value & 0xFFFFffff).append('U'); }
        int precedence() { return 0; }
      };
  }


  /**
   * Create a C expression from a uint642_t constant
   *
   * @return the resulting expression
   */
  static CExpr uint64const(long value)
  {
    return new CExpr()
      {
        void code(CString sb)
        {
          if (value >= 0)
            {
              sb.append(value);
            }
          else
            {
              sb.append("0x");
              sb.append(Long.toHexString(value));
            }
          sb.append("ULL");
        }
        int precedence() { return 0; }
      };
  }


  /**
   * Create a C expression from a f32 constant
   *
   * @return the resulting expression
   */
  public static CExpr f32const(float value)
  {
    return new CExpr()
    {
      void code(CString sb)
      {
        sb.append(String.valueOf(value));
      }
      int precedence() { return 0; }
    };
  }

  /**
   * Create a C expression from a f64 constant
   *
   * @return the resulting expression
   */
  public static CExpr f64const(double value)
  {
    return new CExpr()
    {
      void code(CString sb)
      {
        sb.append(String.valueOf(value));
      }
      int precedence() { return 0; }
    };
  }


  /**
   * Create a C expression for a C string
   *
   * @param s the C string, containing one byte per char.
   *
   * @return the resulting expression
   */
  static CExpr string(String s)
  {
    return string(s.getBytes(StandardCharsets.UTF_8));
  }


  /**
   * Create a C expression for a C string
   *
   * @param bytes the UTF8-encoded byts of the string
   *
   * @return the resulting expression
   */
  static CExpr string(byte[] bytes)
  {
    return new CExpr()
      {
        void code(CString sb)
        {
          sb.append("\"");
          for (var b : bytes)
            {
              var c = (char) (b & 0xff);
              if (c <  ' '  ||
                  c == '"'  ||
                  c == '\'' ||
                  c == '\\' ||
                  c >  '~'     )
                {
                  var i = (int) c;
                  sb.append("\\"+((i >> 6) & 7)+((i >> 3) & 7)+(i & 7));
                }
              else
                {
                  sb.append(c);
                }
            }
          sb.append("\"");
        }
        int precedence() { return 0; }
      };
  }


  /**
   * Compound initializer such as (myStruct){ .fieldA = 3 }
   *
   * @return the resulting expression
   */
  static CExpr compoundLiteral(String type, String initializerList)
  {
    return new CExpr()
      {
        int precedence() { return 1; }
        void code(CString sb) { sb.append('(').append(type).append("){").append(initializerList).append("}"); }
      };
  }


  /**
   * Helper routine for eq/notEq to create eq-expr
   *
   * @param lhs left hand side of resulting expression
   *
   * @param op the operator, should be "==" or "!="
   *
   * @param rhs rightt hand side of resulting expression
   */
  private static CExpr eq(CExpr lhs, String op, CExpr rhs)
  {
    return new CExpr()
      {
        int precedence() { return 8; }
        void code(CString sb)
        {
          lhs.code(sb, precedence());
          sb.append(op);
          rhs.code(sb, precedence());
        }
    };
  }


  /**
   * eq-expr comparing lhs and rhs using "=="
   *
   * @param lhs left hand side of resulting expression
   *
   * @param rhs rightt hand side of resulting expression
   */
  static CExpr eq(CExpr lhs, CExpr rhs)
  {
    return eq(lhs, "==", rhs);
  }


  /**
   * eq-expr comparing lhs and rhs using "!="
   *
   * @param lhs left hand side of resulting expression
   *
   * @param rhs rightt hand side of resulting expression
   */
  static CExpr notEq(CExpr lhs, CExpr rhs)
  {
    return eq(lhs, "!=", rhs);
  }


  /**
   * Call such as name(arg,arg,arg,...)"
   *
   * @return the resulting expression
   */
  static CExpr call(String name, List<CExpr> args)
  {
    return new CExpr()
      {
        int precedence() { return 1; }
        void code(CString sb)
        {
          sb.append(name).append("(");
          String comma = "";
          for (var a : args)
            {
              sb.append(comma);
              a.code(sb);
              comma = ",";
            }
          sb.append(")");
        }
    };
  }

  /**
   * Create C code 'fprintf(stderr,msg, args'
   */
  static CExpr fprintfstderr(String msg,
                             CExpr... args)
  {
    var l = new List<CExpr>(CIdent.STDERR,
                            string(msg));
    for (var a : args)
      {
        l.add(a);
      }

    return call("fprintf", l);
  }


  /**
   * Create C code 'exit(rc)'
   */
  static CExpr exit(int rc)
  {
    return call("exit", new List<>(int32const(rc)));
  }


  /*-------------------------  static methods  --------------------------*/


  /**
   * Check if given expression is alphanumeric, i.e., a C identifier or integer constant.
   *
   * @param expr a C expression
   *
   * @return true iff a only contains A..Za..z0..9
   */
  static boolean isAlphanumeric(String expr)
  {
    boolean alphaNumeric = true;
    for (int i=0; i < expr.length(); i++)
      {
        var c = expr.charAt(i);
        alphaNumeric = alphaNumeric && ('A' <= c && c <= 'Z' ||
                                        'a' <= c && c <= 'z' ||
                                        '0' <= c && c <= '9' ||
                                        c == '_');
      }
    return alphaNumeric;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * What is the lowest (numerically highest) precedence in this expression.
   *
   * See Chapter 6.5 in http://www.open-std.org/jtc1/sc22/wg14/www/docs/n2596.pdf for detail:
   *
   *  0 for prim-expr: identifier, constant, string-literal, '(' expression ')' and generic-selection
   *
   *  1 for postfix-expr: prim-expr | postfix-expr ('[' expr ']'| '(' arglist ')'| '.'| '->'| '++'| '--'
   *           | '(' type-name ')' '{' initializer-list [','] '}'
   *
   *  2 for unary-expr: postfix-expr | ({'++'|'--'|'&'|'*'|'+'|'-'|'~'|'!'} unary-expr)
   *           | 'sizeof' unary-expression | 'sizeof' '(' type-name ')' | '_Alignof' '(' type-name ')'
   *
   *  3 for cast-expr: {'(' type-name ')'} unary-expr
   *
   *  4 for mult-expr: cast-expr | mult-expr ('*' | '/' | '%') cast-expr
   *
   *  5 for add-expr: mult-expr | add-expr ('+' | '-') cast-expr
   *
   *  6 for shift-expr: add-expr | shift-expr ('<<' | '>>') add-expr
   *
   *  7 for rela-expr: shift-expr | rela-expr ('<' | '>' | '<=' | '>=') shift-expr
   *
   *  8 for eq-expr: rela-expr | eq-expr ('==' | '!=') rela-expr
   *
   *  9 for AND-expr: eq-expr | AND-expr '&' eq-expr
   *
   * 10 for XOR-expr: AND-expr | XOR-expr '^' AND-expr
   *
   * 11 for OR-expr: XOR-expr | OR-expr '|' XOR-expr
   *
   * 12 for logicAND-expr: OR-expr | logicAND-expr '&&' OR-expr
   *
   * 13 for logicOR-expr: logicAND-expr | logicOR-expr '||' logicAND-expr
   *
   * 14 for cond-expr: logicOR-expr | logicOR-expr '?' expr : cond-expr
   *
   * 15 for ass-expr; cond.expr | unary-expr ('='|'*='|'/='|'%='|'+='|'-='|'<<='|'>>='|'&='|'^='|'|=') ass-expr
   *
   * 16 expr: ass-exp | expr ',' ass-expr
   *
   * A good reference is from https://en.cppreference.com/w/c/language/operator_precedence,
   * only difference is that this source does not define a level for cast-expr, so levels >= 3 are off by one.
   *
   * @return the precedence used in this CExpr.
   */
  abstract int precedence();


  /**
   * Create C code for this CExpr such that it can be used with an operator with
   * given precedence.  If needed, the code will be wrapped with '(' and ')'.
   *
   * @param sb will be used to append the code to
   *
   * @param precedence the precedence of an operator that will be applied to the
   * result.
   */
  private void code(CString sb, int precedence)
  {
    if (precedence < precedence())
      {
        sb.append('(');
        code(sb);
        sb.append(')');
      }
    else
      {
        code(sb);
      }
  }


  /**
   * Create CExpr that corresponds to reading named field from struct given in this
   *
   * @param name the name of a field
   *
   * @return the resulting expression to read this.name
   */
  CExpr field(CIdent name)
  {
    CExpr inner = this;
    return new CExpr()
      {
        int precedence() { return 1; }
        void code(CString sb) { inner.code(sb, precedence()); sb.append("."); name.code(sb); }
    };
  }


  /**
   * Create CExpr that corresponds to indexing an array given in this
   *
   * @param ix the index
   *
   * @return the resulting expression to read this[ix]
   */
  CExpr index(CExpr ix)
  {
    CExpr inner = this;
    return new CExpr()
      {
        int precedence() { return 1; }
        void code(CString sb) { inner.code(sb, precedence()); sb.append("["); ix.code(sb); sb.append("]"); }
    };
  }


  /**
   * Helper clazz for unary-expr with precedence 2
   */
  private static class Unary extends CExpr
  {
    CExpr _inner;
    String _op;

    Unary(CExpr inner, String op)
      {
        _inner = inner;
        _op = op;
      }

    int precedence()
    {
      return 2;
    }

    void code(CString sb)
    {
      sb.append(_op);
      _inner.code(sb, precedence());
    }
  }

  /**
   * Create CExpr that corresponds to C unary operator '&' with precedence 2
   * applied to this.
   *
   * @return the resulting expression
   */
  CExpr adrOf()
  {
    return new Unary(this, "&")
      {
        // redefine deref since inner.adrOf().deref() == inner
        CExpr deref() { return _inner; }
    };
  }


  /**
   * Create CExpr that corresponds to C unary operator '*' with precedence 2
   * applied to this.
   *
   * @return the resulting expression
   */
  CExpr deref()
  {
    return new Unary(this, "*")
      {
        // redefine adrOf since inner.deref().adrOf() == inner
        CExpr adrOf() { return _inner; }

        // Redefine field to generate inner->field and not (*inner).field.
        CExpr field(CIdent name)
        {
          return new CExpr()
            {
              int precedence() { return 1; }
              void code(CString sb) { _inner.code(sb, precedence()); sb.append("->"); name.code(sb); }
          };
        }
    };
  }


  /**
   * Create CExpr that corresponds to C unary operator '-' with precedence 2
   *
   * @return the resulting expression to negate this
   */
  CExpr neg()
  {
    return new Unary(this, "-")
      {
        // redefine neg since inner.neg().neg() == inner
        CExpr  neg() { return _inner; }
    };
  }


  /**
   * Create CExpr that corresponds to C unary operator '!' with precedence 2
   *
   * @return the resulting expression to negate this
   */
  CExpr not()
  {
    return new Unary(this, "!")
      {
        // redefine not since inner.not().not() == inner, at least for 0/1
        CExpr not() { return _inner; }
    };
  }


  /**
   * Create CExpr that corresponds to C unary operator 'sizeof' with precedence 2
   * applied to this expression.
   *
   * @return the resulting expression
   */
  CExpr sizeOfExpr()
  {
    return new Unary(this, "sizeof ");
  }


  /**
   * Create CExpr that corresponds to C unary operator 'sizeof' with precedence 2
   * applied to ctype.
   *
   * @param ctype the c type
   *
   * @return the resulting expression
   */
  static CExpr sizeOfType(String ctype)
  {
    return new Unary(null /* ignored */, "sizeof")
      {
        void code(CString sb)
        {
          sb.append(_op).append("(").append(ctype).append(")");
        }
    };
  }


  /**
   * Create CExpr that corresponds to C unary operator '+' with precedence 2
   * applied to this.
   *
   * @return the resulting expression
   */
  CExpr plus()
  {
    return this; // to my knowledge, this is always the identity
  }


  /**
   * Create CExpr that corresponds to C cast '(type)expr'
   *
   * @param value the value to be assigned to this.
   *
   * @return the resulting expression
   */
  CExpr castTo(String type)
  {
    CExpr inner = this;
    return new CExpr()
      {
        int precedence() { return 3; }
        void code(CString sb) { sb.append("(").append(type).append(")"); inner.code(sb, precedence()); }
    };
  }


  /**
   * Helper clazz for binary-expr
   */
  private class Binary extends CExpr
  {
    CExpr _left, _right;
    String _op;
    int _prec;

    Binary(CExpr left, String op, CExpr right, int prec)
    {
      _left = left;
      _right = right;
      _op = op;
      _prec = prec;
    }

    int precedence()
    {
      return _prec;
    }

    void code(CString sb)
    {
      _left.code(sb, precedence());
      sb.append(_op);
      _right.code(sb, precedence());
    }
  }


  /**
   * Create CExpr that corresponds to C binary operators '*', '/', '%', '+',
   * '-', '<<', '>>', '<', '<=', '>', '>=', '==', '!=', '&', '^', '|', '&&',
   * '||' with precedence 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 applied to this and
   * right.
   *
   * @return the resulting expression
   */
  CExpr mul(CExpr right) { return new Binary(this, "*" , right,  4); }
  CExpr div(CExpr right) { return new Binary(this, "/" , right,  4); }
  CExpr mod(CExpr right) { return new Binary(this, "%" , right,  4); }
  CExpr add(CExpr right) { return new Binary(this, "+" , right,  5); }
  CExpr sub(CExpr right) { return new Binary(this, "-" , right,  5); }
  CExpr shl(CExpr right) { return new Binary(this, "<<", right,  6); }
  CExpr shr(CExpr right) { return new Binary(this, ">>", right,  6); }
  CExpr lt (CExpr right) { return new Binary(this, "<" , right,  7); }
  CExpr le (CExpr right) { return new Binary(this, "<=", right,  7); }
  CExpr gt (CExpr right) { return new Binary(this, ">" , right,  7); }
  CExpr ge (CExpr right) { return new Binary(this, ">=", right,  7); }
  CExpr eq (CExpr right) { return new Binary(this, "==", right,  8); }
  CExpr ne (CExpr right) { return new Binary(this, "!=", right,  8); }
  CExpr and(CExpr right) { return new Binary(this, "&" , right,  9); }
  CExpr xor(CExpr right) { return new Binary(this, "^" , right, 10); }
  CExpr or (CExpr right) { return new Binary(this, "|" , right, 11); }
  CExpr AND(CExpr right) { return new Binary(this, "&&", right, 12); }
  CExpr OR (CExpr right) { return new Binary(this, "||", right, 13); }

  /**
   * Create CExpr for ternary operator ? : with precedence 14.
   *
   * @param t result in case this is true
   *
   * @param f result in case this is false
   */
  CExpr cond(CExpr t, CExpr f)
  {
    CExpr cc = this;
    return new CExpr()
      {
        int precedence()
        {
          return 14;
        }

        void code(CString sb)
        {
          cc.code(sb, precedence());
          sb.append('?');
          t.code(sb, precedence());
          sb.append(':');
          f.code(sb, precedence());
        }
     };
  }


  /**
   * Create CExpr that corresponds to C assignment using '=' of value to this.
   *
   * @param value the value to be assigned to this.
   *
   * @return the resulting expression
   */
  CExpr assign(CExpr value)
  {
    CExpr inner = this;
    return new CExpr()
      {
        int precedence() { return 15; }
        void code(CString sb) { inner.code(sb, precedence()); sb.append(" = "); value.code(sb, precedence()); }
    };
  }


  /**
   * Create CStmnt to return this expression.
   */
  CStmnt ret()
  {
    return new CStmnt()
      {
        void code(CString sb)
        {
          sb.append("return ");
          CExpr.this.code(sb);
        }
      };
  }


  /**
   * Add a comment surrounded by '/'+'*' and '*'+'/' after this expression.
   */
  CExpr comment(String s)
  {
    CExpr inner = this;
    return new CExpr()
      {
        int precedence() { return inner.precedence(); }
        void code(CString sb)
        {
          inner.code(sb);
          sb.append("/* ").append(s).append(" */");
        }
        boolean isLocalVar()
        {
          return inner.isLocalVar();
        }
      };
  }


  /**
   * Is this a local variable?
   */
  boolean isLocalVar()
  {
    return false;
  }


  /**
   * Create String for debugging-output.
   */
  public String toString()
  {
    return "C-Expression '" + code() + "'";
  }

}

/* end of file */
